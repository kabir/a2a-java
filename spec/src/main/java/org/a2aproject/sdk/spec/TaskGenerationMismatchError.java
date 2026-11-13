package org.a2aproject.sdk.spec;

import static org.a2aproject.sdk.spec.util.Utils.defaultIfNull;

import java.util.Map;

import org.jspecify.annotations.Nullable;

/** A protocol error indicating that a task no longer has the requested generation. */
public final class TaskGenerationMismatchError extends A2AProtocolError {

    public TaskGenerationMismatchError() {
        this(null, null, null);
    }

    public TaskGenerationMismatchError(@Nullable Integer code, @Nullable String message,
            @Nullable Map<String, Object> details) {
        super(defaultIfNull(code, A2AErrorCodes.TASK_GENERATION_MISMATCH.code()),
                defaultIfNull(message, "Task generation does not match"), details);
    }
}
