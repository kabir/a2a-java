package org.a2aproject.sdk.extras.taskstore.database.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.InMemoryWebhookDispatchGate;
import org.a2aproject.sdk.server.tasks.OrderedWebhookDispatcher;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskAuthorityCapabilities;
import org.a2aproject.sdk.server.tasks.TaskAuthorityConfiguration;
import org.a2aproject.sdk.server.tasks.TaskMutationNotCommittedException;
import org.a2aproject.sdk.server.tasks.TaskPersistenceException;
import org.a2aproject.sdk.server.tasks.TaskSerializationException;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Baseline migration and the cooperative writer-epoch check on H2. A dedicated profile gives this class
 * a fresh application so an activated epoch never leaks into other test classes. Storage-enforced denial
 * of old writers is proven against PostgreSQL in {@link JpaWriterFencePostgresTest}.
 */
@QuarkusTest
@TestProfile(JpaTaskMigrationTest.NodeEpochTwo.class)
class JpaTaskMigrationTest {

    public static final class NodeEpochTwo implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(JpaTaskMigration.NODE_EPOCH_PROPERTY, "2");
        }
    }

    @Inject
    JpaDatabaseTaskStore store;

    @Inject
    JpaTaskMigration migration;

    @Inject
    JpaWebhookDispatchGate gate;

    @Inject
    EntityManager em;

    @BeforeEach
    void clean() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM JpaWebhookDispatchLoss").executeUpdate();
            em.createQuery("DELETE FROM JpaWebhookDispatchLane").executeUpdate();
            em.createQuery("DELETE FROM JpaTask").executeUpdate();
            em.createQuery("DELETE FROM JpaWriterEpoch").executeUpdate();
        });
    }

    private static String id() {
        return JpaAtomicTaskMutationTest.id();
    }

    /** A legacy task: generation 0, history, an artifact with an absent role, and no timeline. */
    static Task legacyTask(String id) {
        Message message = Message.builder().role(Message.Role.ROLE_USER).messageId("m-" + id)
                .parts(List.of(new TextPart("hello"))).build();
        Artifact artifact = Artifact.builder().artifactId("a-" + id).parts(List.of(new TextPart("data"))).build();
        return Task.builder().id(id).contextId("ctx-" + id).status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .history(List.of(message)).artifacts(List.of(artifact)).build();
    }

    private JpaTask row(String id) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.clear();
            return em.find(JpaTask.class, id);
        });
    }

    private Long storedEpoch() {
        return QuarkusTransaction.requiringNew().call(() -> migration.storedEpoch());
    }

    @Test
    void legacyModeNeitherStampsNorBaselines() {
        String id = id();
        store.save(legacyTask(id), false);
        TaskSnapshot snapshot = store.readSnapshot(id);
        assertEquals(0, snapshot.task().generation());
        JpaTask row = row(id);
        assertNull(row.getMigrationMarker());
        assertNull(row.getWriterEpoch());
        assertNull(storedEpoch());
    }

    @Test
    void legacyRowIsBaselinedOnceAndPreservesHistoryArtifactsAndAbsentRole() {
        String id = id();
        store.save(legacyTask(id), false);
        // What is stored, exactly as it reads back before any migration (Gson normalizes empty metadata).
        Task legacy = store.get(id);
        migration.activate(2);

        TaskSnapshot first = store.readSnapshot(id);
        assertNotNull(first);
        Task task = first.task();
        assertEquals(1, task.generation());
        assertEquals(legacy.history(), task.history());
        assertEquals(1, task.artifacts().size());
        assertEquals(legacy.artifacts().get(0).parts(), task.artifacts().get(0).parts());
        assertNull(task.artifacts().get(0).role(), "absent artifact role must stay absent, not invented");
        assertTrue(task.timeline() == null || task.timeline().isEmpty(), "no past timeline is invented");

        JpaTask row = row(id);
        assertEquals(JpaTaskMigration.BASELINE_MARKER, row.getMigrationMarker());
        assertEquals(2L, row.getWriterEpoch().longValue());

        // Stable: repeated reads and a fresh persistence context see the same revision and generation.
        assertEquals(first, store.readSnapshot(id));
        assertEquals(first, store.readSnapshot(id));
        assertEquals(first.revision().value(), row(id).getRevision().longValue());
    }

    @Test
    void baselineIsAtomicWithTheFirstMutation() {
        String id = id();
        store.save(legacyTask(id), false);
        migration.activate(2);
        // A guarded mutation planned against the baseline sees generation 1, never 0.
        CommittedMutation committed = store.mutate(id, current -> {
            assertEquals(1, current.task().generation());
            return JpaAtomicTaskMutationTest.plan(id, 2);
        });
        assertEquals(2, committed.snapshot().task().generation());
        assertEquals(JpaTaskMigration.BASELINE_MARKER, row(id).getMigrationMarker());
    }

    @Test
    void existingPositiveGenerationIsKept() {
        String id = id();
        store.save(JpaAtomicTaskMutationTest.task(id, 5, TaskState.TASK_STATE_WORKING), false);
        migration.activate(2);
        assertEquals(5, store.readSnapshot(id).task().generation());
        assertEquals(JpaTaskMigration.BASELINE_MARKER, row(id).getMigrationMarker());
    }

    @Test
    void migratedRowMissingGenerationIsCorruptAndNeverReinitialized() {
        String id = id();
        store.save(legacyTask(id), false);
        QuarkusTransaction.requiringNew().run(() -> em.createQuery(
                "UPDATE JpaTask t SET t.migrationMarker = :m WHERE t.id = :id")
                .setParameter("m", JpaTaskMigration.BASELINE_MARKER).setParameter("id", id).executeUpdate());
        migration.activate(2);
        String before = row(id).getTaskJson();

        assertThrows(TaskSerializationException.class, () -> store.readSnapshot(id));
        TaskMutationNotCommittedException rejected = assertThrows(TaskMutationNotCommittedException.class,
                () -> store.mutate(id, current -> JpaAtomicTaskMutationTest.plan(id, 2)));
        assertTrue(rejected.getCause() instanceof TaskSerializationException, String.valueOf(rejected.getCause()));
        assertEquals(before, row(id).getTaskJson(), "a corrupt migrated row is not rewritten");
        assertThrows(TaskSerializationException.class,
                () -> store.save(JpaAtomicTaskMutationTest.task(id, 1, TaskState.TASK_STATE_WORKING), false));
        assertEquals(before, row(id).getTaskJson());
    }

    @Test
    void newWritesAreStampedWithEpochAndMarker() {
        migration.activate(2);
        String id = id();
        store.mutate(id, current -> JpaAtomicTaskMutationTest.plan(id, 1));
        JpaTask created = row(id);
        assertEquals(2L, created.getWriterEpoch().longValue());
        assertEquals(JpaTaskMigration.BASELINE_MARKER, created.getMigrationMarker());

        String legacySaved = id();
        store.save(JpaAtomicTaskMutationTest.task(legacySaved, 1, TaskState.TASK_STATE_WORKING), false);
        assertEquals(2L, row(legacySaved).getWriterEpoch().longValue());
        assertEquals(JpaTaskMigration.BASELINE_MARKER, row(legacySaved).getMigrationMarker());
    }

    @Test
    void staleNodeEpochCannotWriteOrDelete() {
        String id = id();
        store.mutate(id, current -> JpaAtomicTaskMutationTest.plan(id, 1));
        String legacy = id();
        store.save(legacyTask(legacy), false);
        migration.activate(3); // storage epoch moves past this node's configured epoch 2
        String before = row(id).getTaskJson();

        assertThrows(TaskMutationNotCommittedException.class,
                () -> store.mutate(id, current -> JpaAtomicTaskMutationTest.plan(id, 2)));
        assertThrows(TaskPersistenceException.class,
                () -> store.save(JpaAtomicTaskMutationTest.task(id, 9, TaskState.TASK_STATE_WORKING), false));
        assertThrows(TaskPersistenceException.class, () -> store.delete(id));
        // Reading an unmarked row would baseline it, which is a write: refused for a stale epoch.
        assertThrows(TaskPersistenceException.class, () -> store.readSnapshot(legacy));
        assertNull(row(legacy).getMigrationMarker());
        assertEquals(before, row(id).getTaskJson());
        assertNotNull(row(id));
    }

    @Test
    void epochNeverDecreases() {
        migration.activate(4);
        assertThrows(IllegalStateException.class, () -> migration.activate(3));
        assertEquals(4L, storedEpoch().longValue());
        assertEquals(4L, migration.activate(4));
        assertThrows(IllegalArgumentException.class, () -> migration.activate(0));
    }

    @Test
    void migrateAllBaselinesEveryLegacyRowOnceAndIsIdempotent() {
        String a = id();
        String b = id();
        store.save(legacyTask(a), false);
        store.save(legacyTask(b), false);
        assertThrows(IllegalStateException.class, () -> migration.migrateAll(), "needs an active epoch");
        migration.activate(2);
        assertEquals(2, migration.migrateAll());
        long revisionA = row(a).getRevision();
        assertEquals(1, store.readSnapshot(a).task().generation());
        assertEquals(1, store.readSnapshot(b).task().generation());
        assertEquals(0, migration.migrateAll());
        assertEquals(revisionA, row(a).getRevision().longValue());
    }

    @Test
    void migrationInitializesMarkedT09RowsWithoutResettingTheirAcceptedGeneration() {
        String id = id();
        store.save(JpaAtomicTaskMutationTest.task(id, 5, TaskState.TASK_STATE_WORKING), false);
        QuarkusTransaction.requiringNew().run(() -> em.createQuery(
                "UPDATE JpaTask t SET t.migrationMarker = :marker, t.writerEpoch = :epoch WHERE t.id = :id")
                .setParameter("marker", JpaTaskMigration.BASELINE_MARKER)
                .setParameter("epoch", 2L)
                .setParameter("id", id)
                .executeUpdate());
        migration.activate(2);

        assertEquals(1, migration.migrateAll(), "marked tasks without T11 lane metadata still need migration");
        assertEquals(6, QuarkusTransaction.requiringNew().call(() -> em.createQuery(
                "SELECT lane.nextGeneration FROM JpaWebhookDispatchLane lane WHERE lane.taskId = :id", Long.class)
                .setParameter("id", id).getSingleResult()));
        assertEquals(0, migration.migrateAll());
    }

    @Test
    void authorityCapabilitiesReportUnfencedThenUnmigratedThenReady() {
        String id = id();
        store.save(legacyTask(id), false);

        TaskAuthorityCapabilities legacy = store.authorityCapabilities();
        assertEquals(TaskAuthorityCapabilities.Scope.SHARED_STORAGE, legacy.scope());
        assertNull(legacy.storageEpoch(), "no epoch is active: unfenced");
        assertFalse(legacy.migrationComplete());
        assertEquals(2L, legacy.nodeEpoch().longValue());

        migration.activate(2);
        TaskAuthorityCapabilities unmigrated = store.authorityCapabilities();
        assertEquals(2L, unmigrated.storageEpoch().longValue());
        assertFalse(unmigrated.migrationComplete(), "a legacy row is not baselined yet");

        migration.migrateAll();
        TaskAuthorityCapabilities ready = store.authorityCapabilities();
        assertTrue(ready.migrationComplete());
        assertEquals(List.of(), org.a2aproject.sdk.server.tasks.TaskAuthorityConfiguration.of(
                new org.a2aproject.sdk.server.tasks.TaskAuthorityConfiguration.Topology(true, ready,
                        TaskAuthorityCapabilities.QueueReplication.NONE,
                        new TaskAuthorityCapabilities.WebhookDispatch(true, true, true), "jpa")).violations());
    }

    @Test
    void authorityCapabilitiesExposeAStorageEpochThatDiffersFromTheNode() {
        migration.activate(5);
        TaskAuthorityCapabilities capabilities = store.authorityCapabilities();
        assertEquals(5L, capabilities.storageEpoch().longValue());
        assertEquals(2L, capabilities.nodeEpoch().longValue());
        assertFalse(org.a2aproject.sdk.server.tasks.TaskAuthorityConfiguration.of(
                new org.a2aproject.sdk.server.tasks.TaskAuthorityConfiguration.Topology(true, capabilities,
                        TaskAuthorityCapabilities.QueueReplication.NONE,
                        new TaskAuthorityCapabilities.WebhookDispatch(true, true, true), "jpa"))
                .violations().isEmpty());
    }

    @Test
    void savingALowerGenerationOverAMigratedTaskIsRefused() {
        String id = id();
        store.mutate(id, current -> JpaAtomicTaskMutationTest.plan(id, 1));
        migration.activate(2);
        store.mutate(id, current -> JpaAtomicTaskMutationTest.plan(id, 2));
        TaskSnapshot before = store.readSnapshot(id);
        assertThrows(TaskPersistenceException.class,
                () -> store.save(JpaAtomicTaskMutationTest.task(id, 0, TaskState.TASK_STATE_WORKING), false));
        assertEquals(before, store.readSnapshot(id));
        assertNotEquals(0, store.readSnapshot(id).task().generation());
    }

    /** A sender that reports the ordered, quiescent adapter the shared dispatcher requires. */
    private static final class QuiescentSender implements PushNotificationSender {
        @Override
        public boolean sendNotification(StreamingEventKind event, Task taskSnapshot) {
            return true;
        }

        @Override
        public boolean supportsOrderedDispatch() {
            return true;
        }
    }

    @Test
    void liveSharedJpaCompositionPassesOnlyWithTheSharedGateDispatcher() {
        migration.activate(2);
        migration.migrateAll();
        MainEventBus bus = new MainEventBus();
        QueueManager queues = new InMemoryQueueManager(store, bus);

        MainEventBusProcessor withoutDispatcher = new MainEventBusProcessor(bus, store, new QuiescentSender(), queues);
        List<String> noDispatcher = TaskAuthorityConfiguration.of(store, queues,
                withoutDispatcher::webhookDispatchTopology).violations();
        assertEquals(1, noDispatcher.size(), noDispatcher.toString());
        assertTrue(noDispatcher.get(0).contains("dispatcher"), noDispatcher.get(0));

        OrderedWebhookDispatcher localGate = new OrderedWebhookDispatcher(new InMemoryWebhookDispatchGate(),
                new QuiescentSender());
        try {
            MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, new QuiescentSender(), queues,
                    localGate);
            List<String> violations = TaskAuthorityConfiguration.of(store, queues, processor::webhookDispatchTopology)
                    .violations();
            assertEquals(1, violations.size(), violations.toString());
            assertTrue(violations.get(0).contains("gate"), violations.get(0));
        } finally {
            localGate.close();
        }

        OrderedWebhookDispatcher sharedGate = new OrderedWebhookDispatcher(gate, new QuiescentSender());
        try {
            MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, new QuiescentSender(), queues,
                    sharedGate);
            assertEquals(List.of(), TaskAuthorityConfiguration.of(store, queues, processor::webhookDispatchTopology)
                    .violations());
        } finally {
            sharedGate.close();
        }
    }
}
