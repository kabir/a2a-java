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

    /**
     * Declares where this store's atomic mutation authority lives, so configuration can be validated before
     * A2A 1.1 is exposed. The default is {@link TaskAuthorityCapabilities#UNDECLARED}: such a store keeps working
     * for legacy-only registrations but cannot back 1.1. A declaration is the store's own assertion; it does not
     * fence old writers and is not evidence that any cluster profile was verified.
     */
    default TaskAuthorityCapabilities authorityCapabilities() {
        return TaskAuthorityCapabilities.UNDECLARED;
    }

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
     * preserves both protocol generation and storage revision and returns no events. A changed
     * plan with no events is reserved for an atomic compatibility bootstrap whose legacy event
     * provenance cannot be represented as a canonical batch; it commits without a publication slot.</p>
     */
    CommittedMutation mutate(String taskId, TaskMutation mutation, TaskCommitSlotCallback slotCallback);
}
