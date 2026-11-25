package org.a2aproject.sdk.extras.taskstore.database.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.a2aproject.sdk.extras.common.events.TaskFinalizedEvent;
import org.a2aproject.sdk.server.tasks.MutationPlan;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.TaskEnrollmentCoordinator;
import org.a2aproject.sdk.server.tasks.TaskMutationAuthority;
import org.a2aproject.sdk.server.tasks.TaskMutationNotCommittedException;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JpaMaterializationEnrollmentTest {

    @ApplicationScoped
    public static class FinalizationObserver {
        final ConcurrentMap<String, Boolean> configVisibleAtFinalization = new ConcurrentHashMap<>();
        final ConcurrentMap<String, Boolean> taskVisibleAtFinalization = new ConcurrentHashMap<>();
        final ConcurrentMap<String, Object> committedBatches = new ConcurrentHashMap<>();
        final ConcurrentMap<String, Boolean> taskProposalProvenance = new ConcurrentHashMap<>();
        final ConcurrentMap<String, Long> writerEpochs = new ConcurrentHashMap<>();
        final ConcurrentMap<String, java.util.concurrent.atomic.AtomicInteger> notifications = new ConcurrentHashMap<>();
        @Inject
        PushNotificationConfigStore configs;
        @Inject
        JpaDatabaseTaskStore tasks;

        int notifications(String taskId) {
            var count = notifications.get(taskId);
            return count == null ? 0 : count.get();
        }

        Boolean configVisible(String taskId) {
            return configVisibleAtFinalization.get(taskId);
        }

        Object committedBatch(String taskId) {
            return committedBatches.get(taskId);
        }

        Boolean taskVisible(String taskId) {
            return taskVisibleAtFinalization.get(taskId);
        }

        Boolean taskProposal(String taskId) {
            return taskProposalProvenance.get(taskId);
        }

        Long writerEpoch(String taskId) {
            return writerEpochs.get(taskId);
        }

        void on(@Observes(during = TransactionPhase.AFTER_SUCCESS) TaskFinalizedEvent event) {
            String taskId = event.getTaskId();
            taskProposalProvenance.put(taskId, event.isTaskProposal());
            if (event.getWriterEpoch() != null) {
                writerEpochs.put(taskId, event.getWriterEpoch());
            }
            configVisibleAtFinalization.put(taskId, !configs.getInfo(
                    new ListTaskPushNotificationConfigsParams(taskId, 10, "", "")).configs().isEmpty());
            taskVisibleAtFinalization.put(taskId, tasks.get(taskId) != null);
            if (event.getCommittedBatch() != null) {
                committedBatches.put(taskId, event.getCommittedBatch());
            }
            notifications.computeIfAbsent(taskId, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
        }
    }

    @Inject
    JpaDatabaseTaskStore store;
    @Inject
    PushNotificationConfigStore configs;
    @Inject
    FinalizationObserver observer;

    private static TaskPushNotificationConfig config(String taskId, String configId, String url) {
        return TaskPushNotificationConfig.builder().id(configId).taskId(taskId).url(url).build();
    }

    private List<TaskPushNotificationConfig> configsOf(String taskId) {
        return configs.getInfo(new ListTaskPushNotificationConfigsParams(taskId, 10, "", "")).configs();
    }

    private static MutationPlan create(String id, TaskState state) {
        return new MutationPlan(JpaAtomicTaskMutationTest.task(id, 1, state),
                List.<StreamingEventKind>of(JpaAtomicTaskMutationTest.event(id, 1, state)), true);
    }

    @Test
    void sharedTransactionCommitsTaskAndConfigTogether() {
        String id = JpaAtomicTaskMutationTest.id();
        QuarkusTransaction.requiringNew().run(() -> {
            configs.setInfo(config(id, "c1", "https://example.test/hook"));
            store.mutateInTransaction(id, current -> create(id, TaskState.TASK_STATE_WORKING));
        });
        assertNotNull(store.readSnapshot(id));
        assertEquals(1, configsOf(id).size());
    }

    @Test
    void terminalProposalProvenanceStaysInTheLocalFinalizationEnvelope() {
        String ordinaryId = JpaAtomicTaskMutationTest.id();
        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        authority.mutate(ordinaryId, current -> create(ordinaryId, TaskState.TASK_STATE_COMPLETED));
        assertEquals(1, observer.notifications(ordinaryId));
        assertEquals(Boolean.FALSE, observer.taskProposal(ordinaryId));

        String proposalId = JpaAtomicTaskMutationTest.id();
        authority.mutateTaskProposal(proposalId,
                current -> create(proposalId, TaskState.TASK_STATE_COMPLETED),
                org.a2aproject.sdk.server.tasks.TaskCommitSlotCallback.NONE);
        assertEquals(Boolean.TRUE, observer.taskProposal(proposalId));
        assertNotNull(observer.committedBatch(proposalId));
        assertNotNull(observer.writerEpoch(proposalId));
        assertEquals(2, org.a2aproject.sdk.server.events.CommittedTaskBatch.class.getRecordComponents().length,
                "proposal and epoch metadata must stay outside the replicated committed batch");
    }

    @Test
    void sharedTransactionRollsBackBothWhenTheMutationFails() {
        String id = JpaAtomicTaskMutationTest.id();
        assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            configs.setInfo(config(id, "c1", "https://example.test/hook"));
            store.mutateInTransaction(id, current -> {
                throw new IllegalStateException("guard rejected");
            });
        }));
        assertNull(store.readSnapshot(id));
        assertTrue(configsOf(id).isEmpty());
    }

    @Test
    void immediatelyTerminalCreationHasConfigAndTaskVisibleBeforeFinalizationObserver() {
        String id = JpaAtomicTaskMutationTest.id();
        QuarkusTransaction.requiringNew().run(() -> {
            configs.setInfo(config(id, "c1", "https://example.test/hook"));
            store.mutateInTransaction(id, current -> create(id, TaskState.TASK_STATE_COMPLETED));
        });
        assertEquals(Boolean.TRUE, observer.configVisible(id));
        assertEquals(Boolean.TRUE, observer.taskVisible(id));
    }

    @Test
    void finalizationCarriesTheOriginalCommittedTerminalBatchWithItsStoreRevision() {
        String id = JpaAtomicTaskMutationTest.id();
        org.a2aproject.sdk.server.tasks.CommittedMutation committed =
                store.mutate(id, current -> create(id, TaskState.TASK_STATE_COMPLETED));

        Object batch = observer.committedBatch(id);
        assertNotNull(batch, "the terminal batch must travel with the finalization event");
        org.a2aproject.sdk.server.events.CommittedTaskBatch terminal =
                (org.a2aproject.sdk.server.events.CommittedTaskBatch) batch;
        assertEquals(committed.events(), terminal.events(), "the original stamped events, not a synthesized status");
        assertEquals(committed.snapshot().revision(), terminal.snapshot().revision());
        assertEquals(1, observer.notifications(id));
    }

    @Test
    void rolledBackFinalTaskNeverNotifiesFinalizationObserver() {
        String id = JpaAtomicTaskMutationTest.id();
        assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            store.mutateInTransaction(id, current -> create(id, TaskState.TASK_STATE_COMPLETED));
            throw new IllegalStateException("rollback after planning");
        }));
        assertNull(observer.taskVisible(id));
        assertNull(store.readSnapshot(id));
    }

    @Test
    void independentEnrollmentSurvivesGuardFailureAndConcurrentConfigUpdate() {
        String id = JpaAtomicTaskMutationTest.id();
        store.mutate(id, current -> create(id, TaskState.TASK_STATE_INPUT_REQUIRED));
        TaskEnrollmentCoordinator coordinator = new TaskEnrollmentCoordinator(configs, Clock.systemUTC(),
                Duration.ofMinutes(1));
        TaskEnrollmentCoordinator.Enrollment enrollment = coordinator.enrollExistingTask(
                config(id, "c1", "https://example.test/first"), id, null);

        // A concurrent writer replaces the configuration before the guarded input fails.
        configs.setInfo(config(id, "c1", "https://example.test/replacement"));
        TaskMutationNotCommittedException failure = assertThrows(TaskMutationNotCommittedException.class,
                () -> store.mutate(id, current -> {
                    throw new IllegalStateException("guard rejected");
                }));

        A2AError error = enrollment.partialEffectError(failure, false);
        assertEquals(id, error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_TASK_ID));
        assertEquals("c1", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_CONFIG_ID));
        assertEquals("not-committed", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_INPUT_OUTCOME));
        assertFalse(enrollment.taskNotCommitted());
        List<TaskPushNotificationConfig> remaining = configsOf(id);
        assertEquals(1, remaining.size());
        assertEquals("https://example.test/replacement", remaining.get(0).url());
        assertEquals(1, store.get(id).generation());
    }

    @Test
    void finalTaskThroughAtomicMutateNotifiesExactlyOnceAfterCommitAndRollbackNotifiesNever() {
        String id = JpaAtomicTaskMutationTest.id();
        store.mutate(id, current -> create(id, TaskState.TASK_STATE_COMPLETED));
        assertEquals(1, observer.notifications(id));
        assertEquals(Boolean.TRUE, observer.taskVisible(id));

        String rejected = JpaAtomicTaskMutationTest.id();
        assertThrows(TaskMutationNotCommittedException.class, () -> store.mutate(rejected, current -> {
            throw new IllegalStateException("planner");
        }));
        assertEquals(0, observer.notifications(rejected));
    }
}
