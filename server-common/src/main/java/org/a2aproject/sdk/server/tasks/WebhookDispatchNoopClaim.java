package org.a2aproject.sdk.server.tasks;

/** Conditional zero-width ownership for a verified delivery-only legacy summary. */
public record WebhookDispatchNoopClaim(String taskId, long writerEpoch, String holderToken,
        long expectedNextGeneration) {
}
