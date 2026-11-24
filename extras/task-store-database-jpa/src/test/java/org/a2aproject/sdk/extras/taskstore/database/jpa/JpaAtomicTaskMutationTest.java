package org.a2aproject.sdk.extras.taskstore.database.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.MutationPlan;
import org.a2aproject.sdk.server.tasks.TaskCommitSlot;
import org.a2aproject.sdk.server.tasks.TaskMutationAuthority;
import org.a2aproject.sdk.server.tasks.TaskMutationNotCommittedException;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JpaAtomicTaskMutationTest {

    @Inject
    JpaDatabaseTaskStore store;

    @Inject
    jakarta.persistence.EntityManager em;

    private final java.util.concurrent.atomic.AtomicInteger sawExisting = new java.util.concurrent.atomic.AtomicInteger();

    static String id() {
        return "atomic-" + UUID.randomUUID();
    }

    static Task task(String id, long generation, TaskState state) {
        return Task.builder().id(id).contextId("ctx-" + id).status(new TaskStatus(state))
                .generation(generation).build();
    }

    static TaskStatusUpdateEvent event(String id, long generation, TaskState state) {
        return new TaskStatusUpdateEvent(id, new TaskStatus(state), "ctx-" + id, null, generation);
    }

    static MutationPlan plan(String id, long generation) {
        return new MutationPlan(task(id, generation, TaskState.TASK_STATE_WORKING),
                List.<StreamingEventKind>of(event(id, generation, TaskState.TASK_STATE_WORKING)), true);
    }

    @Test
    void createThenUpdateReturnsCommittedPairsAndSnapshotMatches() {
        String id = id();
        CommittedMutation created = store.mutate(id, current -> {
            assertNull(current);
            return plan(id, 1);
        });
        assertTrue(created.changed());
        assertEquals(created.snapshot(), store.readSnapshot(id));
        assertEquals(1, created.snapshot().task().generation());

        CommittedMutation updated = store.mutate(id, current -> {
            assertEquals(created.snapshot(), current);
            return plan(id, 2);
        });
        assertTrue(updated.snapshot().revision().value() > created.snapshot().revision().value());
        assertEquals(updated.snapshot(), store.readSnapshot(id));
        assertEquals(2, store.get(id).generation());
    }

    @Test
    void unchangedPlanPreservesRevisionAndSkipsSlot() {
        String id = id();
        TaskSnapshot first = store.mutate(id, current -> plan(id, 1)).snapshot();
        AtomicReference<TaskCommitSlot> slot = new AtomicReference<>();
        CommittedMutation unchanged = store.mutate(id,
                current -> new MutationPlan(current.task(), List.of(), false), (s, m) -> slot.set(s));
        assertFalse(unchanged.changed());
        assertEquals(first, unchanged.snapshot());
        assertEquals(first, store.readSnapshot(id));
        assertNull(slot.get());
    }

    @Test
    void plannerFailureRollsBackAndLeavesRevisionUnchanged() {
        String id = id();
        TaskSnapshot first = store.mutate(id, current -> plan(id, 1)).snapshot();
        assertThrows(TaskMutationNotCommittedException.class, () -> store.mutate(id, current -> {
            throw new IllegalStateException("reject");
        }));
        assertEquals(first, store.readSnapshot(id));
        String absent = id();
        assertThrows(TaskMutationNotCommittedException.class, () -> store.mutate(absent, current -> {
            throw new IllegalStateException("reject");
        }));
        assertNull(store.readSnapshot(absent));
    }

    @Test
    void slotIsPublishableOnlyAfterCommitAndCancelledWhenReservationFails() {
        String id = id();
        AtomicReference<TaskCommitSlot> slot = new AtomicReference<>();
        AtomicReference<Boolean> publishableInside = new AtomicReference<>();
        store.mutate(id, current -> plan(id, 1), (s, m) -> {
            slot.set(s);
            publishableInside.set(s.isPublishable());
            // Not yet committed: the row is invisible to other transactions.
            assertNull(store.readSnapshot(id));
        });
        assertEquals(Boolean.FALSE, publishableInside.get());
        assertTrue(slot.get().isPublishable());
        assertNotNull(store.readSnapshot(id));

        String failing = id();
        AtomicReference<TaskCommitSlot> failedSlot = new AtomicReference<>();
        assertThrows(TaskMutationNotCommittedException.class, () -> store.mutate(failing,
                current -> plan(failing, 1), (s, m) -> {
                    failedSlot.set(s);
                    throw new IllegalStateException("reservation");
                }));
        assertTrue(failedSlot.get().isCancelled());
        assertFalse(failedSlot.get().isPublishable());
        assertNull(store.readSnapshot(failing));
    }

    @Test
    void competingGuardedWritersHaveExactlyOneWinner() throws Exception {
        String id = id();
        TaskSnapshot base = store.mutate(id, current -> plan(id, 1)).snapshot();
        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService writers = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(
                    writers.submit(() -> guarded(authority, id, base, ready, start)),
                    writers.submit(() -> guarded(authority, id, base, ready, start)));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) {
                accepted += result.get(30, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertEquals(1, accepted);
            assertEquals(2, store.get(id).generation());
        } finally {
            writers.shutdownNow();
        }
    }

    private static boolean guarded(TaskMutationAuthority authority, String id, TaskSnapshot base,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            authority.mutate(id, current -> {
                if (!base.equals(current)) {
                    throw new IllegalStateException("stale");
                }
                return plan(id, 2);
            });
            return true;
        } catch (TaskMutationNotCommittedException rejected) {
            return false;
        }
    }

    @Test
    void competingCreationHasOneWinnerAndLoserDoesNotReplaceIt() throws Exception {
        String id = id();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService writers = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(
                    writers.submit(() -> create(id, ready, start)), writers.submit(() -> create(id, ready, start)));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int winners = 0;
            for (Future<Boolean> result : results) {
                winners += result.get(30, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertEquals(1, winners);
            // The loser is serialized behind the winner and is planned against its committed row, so it is
            // rejected by its own planner exactly like any stale guarded update (not by a late PK violation).
            assertEquals(1, sawExisting.get());
            assertEquals(1, store.get(id).generation());
        } finally {
            writers.shutdownNow();
        }
    }

    private boolean create(String id, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            store.mutate(id, current -> {
                if (current != null) {
                    sawExisting.incrementAndGet();
                    throw new IllegalStateException("exists");
                }
                return plan(id, 1);
            });
            return true;
        } catch (TaskMutationNotCommittedException rejected) {
            return false;
        }
    }

    @Test
    void deleteAndRecreateNeverReusesARevision() {
        String id = id();
        TaskSnapshot first = store.mutate(id, current -> plan(id, 1)).snapshot();
        TaskSnapshot last = store.mutate(id, current -> plan(id, 2)).snapshot();
        store.delete(id);
        assertNull(store.readSnapshot(id));
        TaskSnapshot recreated = store.mutate(id, current -> plan(id, 1)).snapshot();
        assertTrue(recreated.revision().value() > last.revision().value());
        assertTrue(last.revision().value() > first.revision().value());
    }

    @Test
    void legacySaveRowGetsOneStableRevisionOnFirstSnapshotRead() {
        String id = id();
        store.save(task(id, 3, TaskState.TASK_STATE_WORKING), false);
        TaskSnapshot first = store.readSnapshot(id);
        assertNotNull(first);
        assertEquals(first, store.readSnapshot(id));
        CommittedMutation next = store.mutate(id, current -> plan(id, 4));
        assertTrue(next.snapshot().revision().value() > first.revision().value());
    }

    @Test
    void listAppliesIndependentHistoryAndTimelineWindows() {
        String id = id();
        Message m1 = Message.builder().role(Message.Role.ROLE_USER).messageId("m1")
                .parts(List.of(new TextPart("one"))).build();
        Message m2 = Message.builder().role(Message.Role.ROLE_USER).messageId("m2")
                .parts(List.of(new TextPart("two"))).build();
        List<TimelineEntry> timeline = List.of(
                new TimelineEntry(1, new TimelineEntry.MessageValue(m1)),
                new TimelineEntry(2, new TimelineEntry.MessageValue(m2)),
                new TimelineEntry(3, new TimelineEntry.StatusValue(new TaskStatus(TaskState.TASK_STATE_WORKING))));
        store.save(Task.builder().id(id).contextId("ctx-" + id).status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .history(List.of(m1, m2)).timeline(timeline).build(), false);
        ListTasksResult result = store.list(ListTasksParams.builder().contextId("ctx-" + id).historyLength(1)
                .timelineLength(2).tenant("t").build(), null);
        assertEquals(1, result.tasks().size());
        assertEquals(List.of("m2"), result.tasks().get(0).history().stream().map(Message::messageId).toList());
        assertEquals(List.of(2L, 3L), result.tasks().get(0).timeline().stream().map(TimelineEntry::generation).toList());
        // Independent windows: zero timeline with full history, and omitted timeline leaves it as stored.
        Task zero = store.list(ListTasksParams.builder().contextId("ctx-" + id).historyLength(2)
                .timelineLength(0).tenant("t").build(), null).tasks().get(0);
        assertEquals(2, zero.history().size());
        assertTrue(zero.timeline().isEmpty());
        Task omitted = store.list(ListTasksParams.builder().contextId("ctx-" + id).historyLength(1)
                .tenant("t").build(), null).tasks().get(0);
        assertEquals(List.of(1L, 2L, 3L), omitted.timeline().stream().map(TimelineEntry::generation).toList());
    }
    @Test
    void legacySavePreservesRevisionEpochAndMigrationMarker() {
        String id = id();
        store.mutate(id, current -> plan(id, 1));
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> em.createQuery(
                "UPDATE JpaTask t SET t.writerEpoch = 7, t.migrationMarker = 'm' WHERE t.id = :id")
                .setParameter("id", id).executeUpdate());
        TaskSnapshot before = store.readSnapshot(id);
        store.save(task(id, 2, TaskState.TASK_STATE_WORKING), false);
        TaskSnapshot after = store.readSnapshot(id);
        assertTrue(after.revision().value() > before.revision().value());
        JpaTask row = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            em.clear();
            return em.find(JpaTask.class, id);
        });
        assertEquals(7L, row.getWriterEpoch());
        assertEquals("m", row.getMigrationMarker());
    }

    @Test
    void exhaustedRevisionBlockMovesToANewBlockAndStaysMonotonic() {
        String id = id();
        long first = store.mutate(id, current -> plan(id, 1)).snapshot().revision().value();
        long full = (first & ~JpaTaskRevisionAllocator.SEQUENCE_MASK) + JpaTaskRevisionAllocator.SEQUENCE_MASK;
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> em.createQuery(
                "UPDATE JpaTask t SET t.revision = :r WHERE t.id = :id").setParameter("r", full)
                .setParameter("id", id).executeUpdate());
        long next = store.mutate(id, current -> plan(id, 2)).snapshot().revision().value();
        assertTrue(next > full);
        assertEquals(1, next & JpaTaskRevisionAllocator.SEQUENCE_MASK);
    }

    @Test
    void commitTimeFailureIsUnknownAndCancelsSlotAndErrorsPropagate() throws Exception {
        AtomicReference<TaskCommitSlot.Reservation> lastReservation = new AtomicReference<>();
        JpaTaskMutationTransactions failing = org.mockito.Mockito.mock(JpaTaskMutationTransactions.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            AtomicReference<TaskCommitSlot.Reservation> holder = invocation.getArgument(3);
            TaskCommitSlot.Reservation reservation = TaskCommitSlot.reserve("t",
                    new org.a2aproject.sdk.server.tasks.TaskRevision(1));
            lastReservation.set(reservation);
            holder.set(reservation);
            throw new jakarta.persistence.PersistenceException("commit failed");
        }).when(failing).commit(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        JpaDatabaseTaskStore unit = new JpaDatabaseTaskStore();
        java.lang.reflect.Field field = JpaDatabaseTaskStore.class.getDeclaredField("mutationTransactions");
        field.setAccessible(true);
        field.set(unit, failing);

        org.a2aproject.sdk.server.tasks.TaskMutationOutcomeUnknownException unknown = assertThrows(
                org.a2aproject.sdk.server.tasks.TaskMutationOutcomeUnknownException.class,
                () -> unit.mutate("t", current -> plan("t", 1)));
        assertNotNull(unknown.getCause());
        // The slot reservation made before the failure must be cancelled, never publishable.
        assertNotNull(lastReservation.get());
        assertTrue(lastReservation.get().slot().isCancelled());
        assertFalse(lastReservation.get().slot().isPublishable());

        org.mockito.Mockito.doAnswer(invocation -> {
            AtomicReference<TaskCommitSlot.Reservation> holder = invocation.getArgument(3);
            holder.set(TaskCommitSlot.reserve("t", new org.a2aproject.sdk.server.tasks.TaskRevision(1)));
            throw new AssertionError("fatal");
        }).when(failing).commit(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertThrows(AssertionError.class, () -> unit.mutate("t", current -> plan("t", 1)));
    }

    @Test
    void errorFromSlotCallbackPropagatesAndLeavesNoRowAndCancelledSlot() {
        String id = id();
        AtomicReference<TaskCommitSlot> slot = new AtomicReference<>();
        assertThrows(AssertionError.class, () -> store.mutate(id, current -> plan(id, 1), (s, m) -> {
            slot.set(s);
            throw new AssertionError("fatal");
        }));
        assertTrue(slot.get().isCancelled());
        assertNull(store.readSnapshot(id));
    }
}
