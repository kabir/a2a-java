package org.a2aproject.sdk.server.events;

/**
 * Hook interface for event queue enqueue operations.
 * Implementations can be notified when items are enqueued to the event queue,
 * allowing for custom behavior such as event replication or logging.
 */
public interface EventEnqueueHook {
    /**
     * Called when an item is enqueued to the event queue.
     *
     * @param item the event queue item being enqueued
     */
    void onEnqueue(EventQueueItem item);

    /**
     * Whether this hook wants each locally committed non-terminal batch once through
     * {@link #onCommittedBatch(CommittedTaskBatch)} instead of one {@link #onEnqueue(EventQueueItem)} per event.
     *
     * @return true to receive whole committed batches; the default is false (per-event delivery)
     */
    default boolean handlesCommittedBatches() {
        return false;
    }

    /**
     * Called once per locally committed non-terminal batch when {@link #handlesCommittedBatches()} is true.
     *
     * @param batch the committed batch, with its original ordered events and final snapshot
     */
    default void onCommittedBatch(CommittedTaskBatch batch) {
    }
}
