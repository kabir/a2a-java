package org.a2aproject.sdk.extras.queuemanager.replicated.core;

import org.a2aproject.sdk.spec.Event;

public interface ReplicationStrategy {
    void send(String taskId, Event event);

    /**
     * Sends a fully formed internal envelope (a committed batch, or a record stamped with the sender's writer
     * epoch). Strategies that predate committed replication do not implement it; the queue manager only calls it
     * when a writer epoch is configured.
     *
     * @param item the envelope to send
     */
    default void send(ReplicatedEventQueueItem item) {
        throw new UnsupportedOperationException(
                "This ReplicationStrategy does not support committed replication envelopes");
    }

    /**
     * Whether {@link #send(ReplicatedEventQueueItem)} is implemented. A strategy that overrides it must also
     * return {@code true} here, otherwise A2A 1.1 topology validation rejects it at configuration time instead of
     * the first send failing under a writer epoch.
     */
    default boolean supportsCommittedReplication() {
        return false;
    }
}
