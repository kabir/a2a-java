package org.a2aproject.sdk.server.tasks;

import org.a2aproject.sdk.util.Assert;

/** Store-local ownership of an exact contiguous range of committed task generations. */
public record WebhookDispatchClaim(String taskId, long writerEpoch, String holderToken,
        long firstGeneration, long lastGeneration) {

    public WebhookDispatchClaim {
        taskId = Assert.checkNotNullParam("taskId", taskId);
        holderToken = Assert.checkNotNullParam("holderToken", holderToken);
        if (holderToken.isBlank()) {
            throw new IllegalArgumentException("holderToken must not be blank");
        }
        if (firstGeneration < 1 || lastGeneration < firstGeneration) {
            throw new IllegalArgumentException("The claimed generation range must be positive and ordered");
        }
    }
}
