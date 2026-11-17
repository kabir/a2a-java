package org.a2aproject.sdk.server.tasks;

import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskNotFoundError;
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
        try {
            return store.mutate(taskId, plannerErrors(mutation));
        } catch (PlannerRejection rejection) {
            throw rejection.protocolError;
        } catch (TaskMutationNotCommittedException failure) {
            rethrowPlannerError(failure);
            throw failure;
        }
    }

    public CommittedMutation mutate(String taskId, TaskMutation mutation,
            TaskCommitSlotCallback slotCallback) {
        try {
            return store.mutate(taskId, plannerErrors(mutation), slotCallback);
        } catch (PlannerRejection rejection) {
            throw rejection.protocolError;
        } catch (TaskMutationNotCommittedException failure) {
            rethrowPlannerError(failure);
            throw failure;
        }
    }

    private static TaskMutation plannerErrors(TaskMutation mutation) {
        return current -> {
            try {
                return mutation.plan(current);
            } catch (TaskGenerationMismatchError | InvalidParamsError | TaskNotFoundError failure) {
                throw new PlannerRejection(failure);
            }
        };
    }

    private static void rethrowPlannerError(TaskMutationNotCommittedException failure) {
        Throwable cause = failure.getCause();
        while (cause != null && cause != failure) {
            if (cause instanceof PlannerRejection rejection) {
                throw rejection.protocolError;
            }
            cause = cause.getCause();
        }
    }

    private static final class PlannerRejection extends RuntimeException {

        private final A2AError protocolError;

        private PlannerRejection(A2AError protocolError) {
            super(protocolError);
            this.protocolError = protocolError;
        }
    }
}
