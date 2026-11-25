package org.a2aproject.sdk.server.tasks;

import org.jspecify.annotations.Nullable;

/** Lightweight lane cursor and owner snapshot for dispatcher polling. */
public record WebhookDispatchLanePosition(String taskId, long writerEpoch, long nextGeneration,
        @Nullable String holderToken) {
}
