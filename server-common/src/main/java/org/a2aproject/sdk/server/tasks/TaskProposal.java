package org.a2aproject.sdk.server.tasks;

import java.util.UUID;

import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/** An executor's full-Task proposal and the immutable authority snapshot it was built from. */
public record TaskProposal(String correlationId, @Nullable TaskSnapshot baseSnapshot, Task proposedTask) {

    public TaskProposal {
        Assert.checkNotNullParam("correlationId", correlationId);
        Assert.checkNotNullParam("proposedTask", proposedTask);
        proposedTask = ImmutableTaskCopy.copy(proposedTask);
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
    }

    /** Creates a proposal with a fresh local correlation identifier. */
    public static TaskProposal create(@Nullable TaskSnapshot baseSnapshot, Task proposedTask) {
        return new TaskProposal(UUID.randomUUID().toString(), baseSnapshot, proposedTask);
    }
}
