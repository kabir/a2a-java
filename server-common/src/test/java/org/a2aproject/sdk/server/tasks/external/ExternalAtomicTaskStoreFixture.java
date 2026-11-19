package org.a2aproject.sdk.server.tasks.external;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.tasks.AtomicTaskStore;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.MutationPlan;
import org.a2aproject.sdk.server.tasks.TaskCommitSlot;
import org.a2aproject.sdk.server.tasks.TaskCommitSlotCallback;
import org.a2aproject.sdk.server.tasks.TaskMutation;
import org.a2aproject.sdk.server.tasks.TaskMutationNotCommittedException;
import org.a2aproject.sdk.server.tasks.TaskRevision;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Task;
import org.jspecify.annotations.Nullable;

/** Compile/runtime probe for an AtomicTaskStore implementation outside its production package. */
public final class ExternalAtomicTaskStoreFixture implements AtomicTaskStore {

    private final Map<String, TaskSnapshot> snapshots = new HashMap<>();
    private long revision;

    @Override
    public synchronized @Nullable TaskSnapshot readSnapshot(String taskId) {
        return snapshots.get(taskId);
    }

    @Override
    public CommittedMutation mutate(String taskId, TaskMutation mutation) {
        return mutate(taskId, mutation, TaskCommitSlotCallback.NONE);
    }

    @Override
    public synchronized CommittedMutation mutate(String taskId, TaskMutation mutation,
            TaskCommitSlotCallback slotCallback) {
        TaskSnapshot current = snapshots.get(taskId);
        MutationPlan plan = mutation.plan(current);
        if (!plan.changed()) {
            return new CommittedMutation(Objects.requireNonNull(current), List.of(), false);
        }

        TaskRevision nextRevision = new TaskRevision(++revision);
        TaskSnapshot next = new TaskSnapshot(plan.task(), nextRevision);
        CommittedMutation committed = new CommittedMutation(next, plan.events(), true);
        if (plan.events().isEmpty()) {
            snapshots.put(taskId, next);
            return committed;
        }
        TaskCommitSlot.Reservation reservation = reserveSlot(taskId, nextRevision);
        TaskCommitSlot slot = reservation.slot();
        try {
            slotCallback.reserve(slot, committed);
        } catch (RuntimeException failure) {
            cancelSlot(reservation);
            throw new TaskMutationNotCommittedException(taskId, "External fixture reservation failed", failure);
        }
        snapshots.put(taskId, next);
        commitSlot(reservation);
        return committed;
    }

    @Override
    public synchronized void save(Task task, boolean isReplicated) {
        snapshots.put(task.id(), new TaskSnapshot(task, new TaskRevision(++revision)));
    }

    @Override
    public synchronized @Nullable Task get(String taskId) {
        TaskSnapshot snapshot = snapshots.get(taskId);
        return snapshot == null ? null : snapshot.task();
    }

    @Override
    public synchronized void delete(String taskId) {
        snapshots.remove(taskId);
    }

    @Override
    public ListTasksResult list(ListTasksParams params, @Nullable ServerCallContext context) {
        return new ListTasksResult(List.of());
    }

    public TaskCommitSlot.Reservation reserveSlot(String taskId, TaskRevision revision) {
        return TaskCommitSlot.reserve(taskId, revision);
    }

    public void commitSlot(TaskCommitSlot.Reservation reservation) {
        reservation.markCommitted();
    }

    public void cancelSlot(TaskCommitSlot.Reservation reservation) {
        reservation.cancel();
    }
}
