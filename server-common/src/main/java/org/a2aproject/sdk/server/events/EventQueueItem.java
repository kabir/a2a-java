package org.a2aproject.sdk.server.events;

import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.Task;
import org.jspecify.annotations.Nullable;

/**
 * Represents an item that can be enqueued/dequeued in an EventQueue.
 * <p>
 * This abstraction allows the queue to track whether events originated locally
 * or were received from replication, enabling proper handling of the replication loop
 * prevention mechanism.
 * </p>
 */
public interface EventQueueItem {

    /**
     * Gets the event contained in this queue item.
     *
     * @return the event
     */
    Event getEvent();

    /**
     * Checks if this item represents a replicated event.
     * <p>
     * Replicated events are those received from other nodes in the cluster via
     * the replication mechanism (e.g., Kafka). Local events are those enqueued
     * directly by the local agent execution.
     * </p>
     * <p>
     * This distinction is important to prevent replication loops - replicated events
     * should not be re-replicated to other nodes.
     * </p>
     *
     * @return true if this event was replicated from another node, false if it's a local event
     */
    boolean isReplicated();

    /** Final store snapshot attached to a canonical committed event, when available. */
    default @Nullable TaskSnapshot committedSnapshot() {
        return null;
    }

    /** True when this item is the last event of a canonical terminal committed batch. */
    default boolean isCanonicalFinal() {
        return false;
    }

    /** Whether local consumption and queue cleanup should treat this item as terminal. */
    default boolean isFinalForLocalConsumption() {
        return isCanonicalFinal();
    }

    /** Matching task result persisted for a legacy-compatible local event, when available. */
    default @Nullable Task resultTaskSnapshot() {
        return committedSnapshot() == null ? null : committedSnapshot().task();
    }
}
