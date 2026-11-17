package org.a2aproject.sdk.server.tasks;

import org.jspecify.annotations.Nullable;

/**
 * Additive companion for stores that can atomically plan and commit a task mutation.
 *
 * <p>Legacy-only {@link TaskStore} implementations remain valid and are not adapted through
 * separate {@code get} and {@code save} calls. Runtime mutation callers should enter through
 * {@link TaskMutationAuthority}.</p>
 */
public interface AtomicTaskStore extends TaskStore {

    /** Returns one immutable authoritative task/revision pair, or {@code null} when absent. */
    @Nullable TaskSnapshot readSnapshot(String taskId);

    /** Atomically plans and commits without reserving a publication position. */
    CommittedMutation mutate(String taskId, TaskMutation mutation);

    /**
     * Plans and commits inside one per-task atomic boundary and reserves the local publication
     * position in that same boundary.
     *
     * <p>A planner or slot-callback {@link RuntimeException} must leave state unchanged and be
     * reported as {@link TaskMutationNotCommittedException}. A fatal {@link Error} is propagated
     * directly; any reserved slot is cancelled before propagation. If the storage outcome cannot
     * be established, the implementation must report
     * {@link TaskMutationOutcomeUnknownException}; it must not claim rollback. Cancelling a slot
     * suppresses publication but does not prove rollback. A changed result contains the original
     * ordered events and exactly one final task snapshot for the whole batch. An unchanged plan
     * preserves both protocol generation and storage revision and returns no events.</p>
     */
    CommittedMutation mutate(String taskId, TaskMutation mutation, TaskCommitSlotCallback slotCallback);
}
