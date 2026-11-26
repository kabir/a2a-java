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

    /**
     * Called once after a batch passed to {@link #onCommittedBatch(CommittedTaskBatch)} has been handed to every
     * local child queue, so a hook can order work from other nodes behind this node's own publication.
     *
     * @param batch the batch that was passed to {@link #onCommittedBatch(CommittedTaskBatch)}
     */
    default void onCommittedBatchPublished(CommittedTaskBatch batch) {
    }
}
