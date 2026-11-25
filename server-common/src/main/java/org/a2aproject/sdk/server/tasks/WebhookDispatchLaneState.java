package org.a2aproject.sdk.server.tasks;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** Read-only cursor and owner snapshot used by the local best-effort dispatcher. */
public record WebhookDispatchLaneState(String taskId, long writerEpoch, long nextGeneration,
        @Nullable String holderToken, @Nullable Long claimedFirstGeneration,
        @Nullable Long claimedLastGeneration, List<WebhookDispatchLoss> losses) {
    public WebhookDispatchLaneState {
        losses = List.copyOf(losses);
    }
}
