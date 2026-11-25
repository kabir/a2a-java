package org.a2aproject.sdk.server.tasks;

import java.util.Optional;

/** Atomic shared-store compare-and-set operations for ordered webhook delivery. */
public interface WebhookDispatchGate {

    Optional<WebhookDispatchClaim> claim(String taskId, long writerEpoch,
            long firstGeneration, long lastGeneration);

    boolean complete(WebhookDispatchClaim claim);

    boolean retireUnclaimedGap(String taskId, long writerEpoch,
            long expectedNextGeneration, long throughGeneration);
}
