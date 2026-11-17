package org.a2aproject.sdk.server.tasks;

/**
 * Reserves the local publication position for a proposed committed batch.
 *
 * <p>The callback runs inside the store's atomic per-task boundary and receives only a read-only
 * slot view, so it cannot make the reservation publishable. It must do only bounded local
 * reservation work and must not fan out events, perform network I/O, or invoke executor/user
 * callbacks. If it throws a {@link RuntimeException}, the task is not changed, the store cancels
 * the slot, and the store reports a definite non-commit. If it throws an {@link Error}, the store
 * cancels the slot and propagates the error directly.</p>
 */
@FunctionalInterface
public interface TaskCommitSlotCallback {

    TaskCommitSlotCallback NONE = (slot, mutation) -> {
    };

    void reserve(TaskCommitSlot slot, CommittedMutation mutation);
}
