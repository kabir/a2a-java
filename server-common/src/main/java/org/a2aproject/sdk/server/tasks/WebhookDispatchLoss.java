package org.a2aproject.sdk.server.tasks;

import java.time.Instant;

/** Operational record that volatile webhook work for a canonical range was discarded. */
public record WebhookDispatchLoss(long firstGeneration, long lastGeneration, Instant recordedAt, String reason) {
}
