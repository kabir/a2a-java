package org.a2aproject.sdk.server.tasks;

import org.jspecify.annotations.Nullable;

/**
 * Plans a change from the store's current authoritative snapshot.
 *
 * <p>Implementations must be side-effect-free. Stores may execute this planner while holding an
 * atomic per-task boundary and must not retry it as a storage retry mechanism. Executor and other
 * user callbacks do not belong here.</p>
 */
@FunctionalInterface
public interface TaskMutation {

    MutationPlan plan(@Nullable TaskSnapshot current);

    /** True only when this mutation is an immutable executor Task proposal. */
    default boolean isTaskProposal() {
        return false;
    }
}
