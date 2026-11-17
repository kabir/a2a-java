package org.a2aproject.sdk.server.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.server.tasks.external.ExternalAtomicTaskStoreFixture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AtomicTaskStoreContractTest {

    @Test
    void onlyOneWriterCanAcceptTheSameRevisionAndGeneration() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        store.save(task("guarded", 7), false);
        TaskSnapshot base = requireSnapshot(store, "guarded");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService writers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = writers.submit(() -> guardedWrite(authority, base, ready, start));
            Future<Boolean> second = writers.submit(() -> guardedWrite(authority, base, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int accepted = (first.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, accepted);
            TaskSnapshot result = requireSnapshot(store, "guarded");
            assertEquals(8, result.task().generation());
            assertTrue(result.revision().value() > base.revision().value());
        } finally {
            writers.shutdownNow();
        }
    }

    @Test
    void competingCreationHasOneWinnerAndRejectedCreationDoesNotReplaceIt() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService writers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = writers.submit(() -> createIfAbsent(store, "new-task", ready, start));
            Future<Boolean> second = writers.submit(() -> createIfAbsent(store, "new-task", ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(1, (first.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS) ? 1 : 0));
            TaskSnapshot created = requireSnapshot(store, "new-task");
            assertEquals(1, created.task().generation());
        } finally {
            writers.shutdownNow();
        }
    }

    @Test
    void planningFailureIsCalledOnceAndLeavesTaskAndRevisionUnchanged() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        store.save(task("rollback", 3), false);
        TaskSnapshot before = requireSnapshot(store, "rollback");
        AtomicInteger calls = new AtomicInteger();

        assertThrows(TaskMutationNotCommittedException.class, () -> authority.mutate("rollback", current -> {
            calls.incrementAndGet();
            throw new IllegalStateException("reject plan");
        }));

        assertEquals(1, calls.get());
        assertEquals(before, store.readSnapshot("rollback"));
    }

    @Test
    void snapshotAndCommittedBatchUseTheSameFinalTaskAndRevisionPair() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task("batch", 1), false);
        TaskSnapshot before = requireSnapshot(store, "batch");

        CountDownLatch planning = new CountDownLatch(1);
        CountDownLatch continuePlan = new CountDownLatch(1);
        AtomicReference<TaskCommitSlot> reservedSlot = new AtomicReference<>();
        AtomicReference<CommittedMutation> reservedCandidate = new AtomicReference<>();
        AtomicReference<Boolean> publishableDuringReservation = new AtomicReference<>();
        List<StreamingEventKind> plannedEvents = List.of(statusEvent("batch", 2), statusEvent("batch", 3));
        ExecutorService writer = Executors.newSingleThreadExecutor();
        try {
            Future<CommittedMutation> committedFuture = writer.submit(() -> store.mutate("batch", current -> {
                assertEquals(before, current);
                planning.countDown();
                await(continuePlan);
                return new MutationPlan(task("batch", 3), plannedEvents, true);
            }, (slot, candidate) -> {
                reservedSlot.set(slot);
                reservedCandidate.set(candidate);
                publishableDuringReservation.set(slot.isPublishable());
            }));

            assertTrue(planning.await(5, TimeUnit.SECONDS));
            assertEquals(before, store.readSnapshot("batch"), "the old immutable pair remains visible while planning");
            continuePlan.countDown();

            CommittedMutation committed = committedFuture.get(5, TimeUnit.SECONDS);
            assertEquals(committed, Objects.requireNonNull(reservedCandidate.get()));
            assertFalse(Objects.requireNonNull(publishableDuringReservation.get()));
            assertTrue(Objects.requireNonNull(reservedSlot.get()).isPublishable());
            assertSame(plannedEvents.get(0), committed.events().get(0));
            assertEquals(List.of(2L, 3L), committed.events().stream()
                    .map(event -> ((TaskStatusUpdateEvent) event).generation())
                    .toList());
            assertEquals(3, committed.snapshot().task().generation());
            assertSame(committed.snapshot().task(), requireSnapshot(store, "batch").task());
            assertTrue(committed.snapshot().revision().value() > before.revision().value());
            assertNotEquals(committed.snapshot().revision().value(), committed.snapshot().task().generation());
        } finally {
            continuePlan.countDown();
            writer.shutdownNow();
        }
    }

    @Test
    void unrelatedTaskCanCommitWhileOneTaskPlannerIsBlocked() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task("task-a", 1), false);
        store.save(task("task-b", 1), false);
        CountDownLatch taskAPlanning = new CountDownLatch(1);
        CountDownLatch releaseTaskA = new CountDownLatch(1);
        ExecutorService writers = Executors.newFixedThreadPool(2);
        try {
            Future<CommittedMutation> taskA = writers.submit(() -> store.mutate("task-a", current -> {
                taskAPlanning.countDown();
                await(releaseTaskA);
                return nextGeneration(current);
            }));
            assertTrue(taskAPlanning.await(5, TimeUnit.SECONDS));

            Future<CommittedMutation> taskB = writers.submit(() -> store.mutate("task-b", this::nextGeneration));
            assertEquals(2, taskB.get(5, TimeUnit.SECONDS).snapshot().task().generation());
            releaseTaskA.countDown();
            assertEquals(2, taskA.get(5, TimeUnit.SECONDS).snapshot().task().generation());
        } finally {
            releaseTaskA.countDown();
            writers.shutdownNow();
        }
    }

    @Test
    void unchangedPlanPreservesRevisionAndSkipsSlotReservation() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task("noop", 4), false);
        TaskSnapshot before = requireSnapshot(store, "noop");
        AtomicInteger reservations = new AtomicInteger();

        CommittedMutation result = store.mutate("noop", current -> new MutationPlan(
                current.task(), List.of(), false), (slot, candidate) -> reservations.incrementAndGet());

        assertEquals(before, result.snapshot());
        assertFalse(result.changed());
        assertTrue(result.events().isEmpty());
        assertEquals(0, reservations.get());
    }

    @Test
    void throwingSlotReservationCancelsSlotAndCannotExposeUncommittedBatch() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task("slot", 1), false);
        TaskSnapshot before = requireSnapshot(store, "slot");
        AtomicReference<TaskCommitSlot> reserved = new AtomicReference<>();

        assertThrows(TaskMutationNotCommittedException.class, () -> store.mutate("slot", current -> nextGeneration(current),
                (slot, candidate) -> {
                    reserved.set(slot);
                    throw new IllegalStateException("reservation rejected");
                }));

        TaskCommitSlot slot = Objects.requireNonNull(reserved.get());
        assertTrue(slot.isCancelled());
        assertFalse(slot.isPublishable());
        assertEquals(before, store.readSnapshot("slot"));
    }

    @Test
    void reservationCallbackCannotMakeRetainedSlotPublishableBeforeThrowing() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task("slot-premature", 1), false);
        TaskSnapshot before = requireSnapshot(store, "slot-premature");
        AtomicReference<TaskCommitSlot> reserved = new AtomicReference<>();

        assertThrows(TaskMutationNotCommittedException.class, () -> store.mutate("slot-premature",
                current -> nextGeneration(current), (slot, candidate) -> {
                    reserved.set(slot);
                    try {
                        slot.getClass().getMethod("markCommitted").invoke(slot);
                    } catch (NoSuchMethodException expected) {
                        throw new IllegalStateException("reservation callback cannot release a slot", expected);
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                    throw new IllegalStateException("reservation callback failed after release");
                }));

        TaskCommitSlot slot = Objects.requireNonNull(reserved.get());
        assertTrue(slot.isCancelled());
        assertFalse(slot.isPublishable());
        assertEquals(before, store.readSnapshot("slot-premature"));
    }

    @Test
    void legacyOnlyTaskStoreDoesNotNeedToImplementAtomicCompanion() {
        TaskStore legacyStore = new LegacyOnlyTaskStore();
        legacyStore.save(task("legacy", 0), false);
        assertNull(legacyStore.get("legacy"));
    }

    @Test
    void externalAtomicStoreCanReserveCommitAndCancelSlots() {
        ExternalAtomicTaskStoreFixture store = new ExternalAtomicTaskStoreFixture();
        store.save(task("external", 1), false);
        AtomicReference<TaskCommitSlot> retained = new AtomicReference<>();
        AtomicBoolean publishableDuringReservation = new AtomicBoolean(true);

        CommittedMutation committed = store.mutate("external", current -> new MutationPlan(
                task("external", 2), List.of(statusEvent("external", 2)), true), (slot, candidate) -> {
                    retained.set(slot);
                    publishableDuringReservation.set(slot.isPublishable());
                });

        TaskCommitSlot committedSlot = Objects.requireNonNull(retained.get());
        assertFalse(publishableDuringReservation.get());
        assertTrue(committedSlot.isPublishable());
        assertEquals(committed.snapshot().revision(), committedSlot.revision());
        TaskCommitSlot.Reservation committedReservation = store.reserveSlot("external-committed",
                new TaskRevision(49));
        store.commitSlot(committedReservation);
        assertTrue(committedReservation.slot().isPublishable());
        store.cancelSlot(committedReservation);
        assertTrue(committedReservation.slot().isPublishable(),
                "a committed slot cannot transition back to cancelled");

        TaskCommitSlot.Reservation cancelledReservation = store.reserveSlot("external", new TaskRevision(50));
        store.cancelSlot(cancelledReservation);
        TaskCommitSlot cancelledSlot = cancelledReservation.slot();
        assertTrue(cancelledSlot.isCancelled());
        assertFalse(cancelledSlot.isPublishable());
        assertThrows(IllegalStateException.class, () -> store.commitSlot(cancelledReservation));
    }

    @Test
    void storageFailureBeforeReplacementLeavesReservedSlotCancelledAndOutcomeUnknown() {
        RuntimeException injectedFailure = new IllegalStateException("before replacement");
        InMemoryTaskStore store = new InMemoryTaskStore(null, (point, taskId, proposed) -> {
            if (point == InMemoryTaskStore.CommitFaultPoint.BEFORE_REPLACEMENT) {
                throw injectedFailure;
            }
        });
        store.save(task("fault-before", 1), false);
        TaskSnapshot before = requireSnapshot(store, "fault-before");
        AtomicReference<TaskCommitSlot> retained = new AtomicReference<>();

        TaskMutationOutcomeUnknownException outcome = assertThrows(TaskMutationOutcomeUnknownException.class,
                () -> store.mutate("fault-before", this::nextGeneration, (slot, candidate) -> retained.set(slot)));

        TaskCommitSlot slot = Objects.requireNonNull(retained.get());
        assertSame(injectedFailure, outcome.getCause());
        assertTrue(outcome.getMessage().contains("unknown"));
        assertFalse(outcome.getMessage().toLowerCase().contains("rollback"));
        assertFalse(outcome.getMessage().toLowerCase().contains("retry"));
        assertTrue(slot.isCancelled());
        assertFalse(slot.isPublishable());
        assertEquals(before, requireSnapshot(store, "fault-before"));
    }

    @Test
    void storageFailureAfterReplacementLeavesOutcomeUnknownAndSlotUnpublishable() {
        RuntimeException injectedFailure = new IllegalStateException("after replacement");
        InMemoryTaskStore store = new InMemoryTaskStore(null, (point, taskId, proposed) -> {
            if (point == InMemoryTaskStore.CommitFaultPoint.AFTER_REPLACEMENT) {
                throw injectedFailure;
            }
        });
        store.save(task("fault-after", 1), false);
        AtomicReference<TaskCommitSlot> retained = new AtomicReference<>();

        TaskMutationOutcomeUnknownException outcome = assertThrows(TaskMutationOutcomeUnknownException.class,
                () -> store.mutate("fault-after", this::nextGeneration, (slot, candidate) -> retained.set(slot)));

        TaskCommitSlot slot = Objects.requireNonNull(retained.get());
        assertSame(injectedFailure, outcome.getCause());
        assertTrue(outcome.getMessage().contains("unknown"));
        assertFalse(outcome.getMessage().toLowerCase().contains("rollback"));
        assertFalse(outcome.getMessage().toLowerCase().contains("retry"));
        assertTrue(slot.isCancelled());
        assertFalse(slot.isPublishable());
        assertEquals(2, requireSnapshot(store, "fault-after").task().generation(),
                "the injected failure happens after replacement, while the caller still receives an unknown outcome");
    }

    @Test
    void fatalErrorDuringReservationPropagatesDirectlyAndCancelsSlot() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task("fatal-slot", 1), false);
        TaskSnapshot before = requireSnapshot(store, "fatal-slot");
        AtomicReference<TaskCommitSlot> retained = new AtomicReference<>();
        AssertionError fatal = new AssertionError("fatal reservation error");

        AssertionError propagated = assertThrows(AssertionError.class,
                () -> store.mutate("fatal-slot", this::nextGeneration, (slot, candidate) -> {
                    retained.set(slot);
                    throw fatal;
                }));

        assertSame(fatal, propagated);
        TaskCommitSlot slot = Objects.requireNonNull(retained.get());
        assertTrue(slot.isCancelled());
        assertFalse(slot.isPublishable());
        assertEquals(before, requireSnapshot(store, "fatal-slot"));
    }

    @Test
    void legacySaveAndAtomicMutationExposeWholeExpectedTaskRevisionPairs() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task firstTask = task("coherent", 1);
        store.save(firstTask, false);
        TaskSnapshot first = requireSnapshot(store, "coherent");
        assertEquals(new TaskSnapshot(firstTask, new TaskRevision(1)), first);

        CountDownLatch planning = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch legacySaveStarted = new CountDownLatch(1);
        Task secondTask = task("coherent", 2);
        Task thirdTask = task("coherent", 8);
        ExecutorService writers = Executors.newFixedThreadPool(2);
        try {
            Future<CommittedMutation> mutation = writers.submit(() -> store.mutate("coherent", current -> {
                assertEquals(first, current);
                planning.countDown();
                await(release);
                return new MutationPlan(secondTask, List.of(statusEvent("coherent", 2)), true);
            }));
            assertTrue(planning.await(5, TimeUnit.SECONDS));
            assertEquals(first, requireSnapshot(store, "coherent"));

            Future<TaskSnapshot> legacySave = writers.submit(() -> {
                legacySaveStarted.countDown();
                store.save(thirdTask, false);
                return requireSnapshot(store, "coherent");
            });
            assertTrue(legacySaveStarted.await(5, TimeUnit.SECONDS));
            release.countDown();

            CommittedMutation second = mutation.get(5, TimeUnit.SECONDS);
            assertEquals(new TaskSnapshot(secondTask, new TaskRevision(2)), second.snapshot());
            TaskSnapshot third = legacySave.get(5, TimeUnit.SECONDS);
            assertEquals(new TaskSnapshot(thirdTask, new TaskRevision(3)), third);
            assertEquals(third, requireSnapshot(store, "coherent"));
        } finally {
            release.countDown();
            writers.shutdownNow();
        }
    }

    @Test
    void deleteAndRecreateDoesNotReuseStorageRevision() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task("recreated", 1), false);
        TaskRevision firstRevision = requireSnapshot(store, "recreated").revision();
        CommittedMutation changed = store.mutate("recreated", this::nextGeneration);
        TaskRevision lastRevision = changed.snapshot().revision();

        store.delete("recreated");
        assertNull(store.readSnapshot("recreated"));
        CommittedMutation recreated = store.mutate("recreated", current -> {
            assertNull(current);
            return new MutationPlan(task("recreated", 1), List.of(statusEvent("recreated", 1)), true);
        });

        assertTrue(lastRevision.value() > firstRevision.value());
        assertTrue(recreated.snapshot().revision().value() > lastRevision.value());
        assertSame(recreated.snapshot().task(), requireSnapshot(store, "recreated").task());
    }

    private static boolean guardedWrite(TaskMutationAuthority authority, TaskSnapshot base,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            authority.mutate(base.task().id(), current -> {
                if (!base.equals(current)) {
                    throw new IllegalStateException("stale revision");
                }
                return new MutationPlan(task(base.task().id(), base.task().generation() + 1),
                        List.of(statusEvent(base.task().id(), base.task().generation() + 1)), true);
            });
            return true;
        } catch (TaskMutationNotCommittedException rejected) {
            return false;
        }
    }

    private static boolean createIfAbsent(InMemoryTaskStore store, String id,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            store.mutate(id, current -> {
                if (current != null) {
                    throw new IllegalStateException("task already exists");
                }
                return new MutationPlan(task(id, 1), List.of(statusEvent(id, 1)), true);
            });
            return true;
        } catch (TaskMutationNotCommittedException rejected) {
            return false;
        }
    }

    private MutationPlan nextGeneration(@Nullable TaskSnapshot current) {
        TaskSnapshot snapshot = Objects.requireNonNull(current);
        long generation = snapshot.task().generation() + 1;
        return new MutationPlan(task(snapshot.task().id(), generation),
                List.of(statusEvent(snapshot.task().id(), generation)), true);
    }

    private static TaskSnapshot requireSnapshot(AtomicTaskStore store, String taskId) {
        return Objects.requireNonNull(store.readSnapshot(taskId));
    }

    private static Task task(String id, long generation) {
        return Task.builder()
                .id(id)
                .contextId("context-" + id)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .generation(generation)
                .build();
    }

    private static TaskStatusUpdateEvent statusEvent(String id, long generation) {
        return new TaskStatusUpdateEvent(id, new TaskStatus(TaskState.TASK_STATE_WORKING), "context-" + id, null,
                generation);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for latch", e);
        }
    }

    private static final class LegacyOnlyTaskStore implements TaskStore {
        @Override
        public void save(Task task, boolean isReplicated) {
        }

        @Override
        public @Nullable Task get(String taskId) {
            return null;
        }

        @Override
        public void delete(String taskId) {
        }

        @Override
        public ListTasksResult list(ListTasksParams params, @Nullable ServerCallContext context) {
            return new ListTasksResult(List.of());
        }
    }
}
