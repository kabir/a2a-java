package org.a2aproject.sdk.server.tasks;

import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * The intended runtime entry point for authoritative task mutations.
 *
 * <p>This foundation is additive. Existing handlers are not wired through this authority until
 * the later Phase 3 orchestration task.</p>
 */
public final class TaskMutationAuthority {

    private final AtomicTaskStore store;

    public TaskMutationAuthority(AtomicTaskStore store) {
        this.store = Assert.checkNotNullParam("store", store);
    }

    @Nullable
    public TaskSnapshot readSnapshot(String taskId) {
        return store.readSnapshot(taskId);
    }

    public CommittedMutation mutate(String taskId, TaskMutation mutation) {
        return store.mutate(taskId, mutation);
    }

    public CommittedMutation mutate(String taskId, TaskMutation mutation,
            TaskCommitSlotCallback slotCallback) {
        return store.mutate(taskId, mutation, slotCallback);
    }
}
