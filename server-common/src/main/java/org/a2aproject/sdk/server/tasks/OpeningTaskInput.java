package org.a2aproject.sdk.server.tasks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * Request-local opening input of an ordinary send that may create a new task, with the optional
 * webhook enrollment to perform at materialization.
 *
 * <p>Holding this value persists nothing. The first task-bearing executor output consumes it:
 * enrollment runs immediately before the atomic task mutation, and the mutation applies the pending
 * input once. A direct Message response never consumes it, so no task, input, artifact or
 * configuration effect exists for that branch.</p>
 */
public final class OpeningTaskInput {

    private final PendingTaskInput input;
    private final @Nullable Supplier<TaskEnrollmentCoordinator.Enrollment> enrollment;
    private final AtomicBoolean materialized = new AtomicBoolean();

    public OpeningTaskInput(PendingTaskInput input,
            @Nullable Supplier<TaskEnrollmentCoordinator.Enrollment> enrollment) {
        this.input = Assert.checkNotNullParam("input", input);
        this.enrollment = enrollment;
    }

    public PendingTaskInput input() {
        return input;
    }

    /** Enrolls the request's webhook configuration for the not-yet-created task, if one was supplied. */
    public TaskEnrollmentCoordinator.@Nullable Enrollment enroll() {
        return enrollment == null ? null : enrollment.get();
    }

    public boolean isMaterialized() {
        return materialized.get();
    }

    /** Records that the task has committed, so no later output re-applies this input. */
    public void markMaterialized() {
        materialized.set(true);
    }
}
