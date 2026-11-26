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

    /**
     * Called, only when {@link #handlesCommittedBatches()} is true, while a request-thread mutation of the task is
     * still inside the store's atomic boundary, that is before its result can be visible to another node. It lets
     * a hook order batches from other nodes behind a local batch that is committed but not yet handed to
     * {@link #onCommittedBatch(CommittedTaskBatch)}. Every call is followed by exactly one
     * {@link #onCommittedBatchReleased(String)}, whether the batch was published, never produced, or failed.
     *
     * <p>Mutations processed on the event-bus processor thread do not use this pair: a batch from another node is
     * itself processed on that thread, after the local batch.</p>
     *
     * @param taskId the task whose mutation is being committed
     */
    default void onCommittedBatchReserved(String taskId) {
    }

    /**
     * Ends the hold started by {@link #onCommittedBatchReserved(String)}, after the local publication finished or was
     * abandoned.
     *
     * @param taskId the task passed to the matching {@link #onCommittedBatchReserved(String)}
     */
    default void onCommittedBatchReleased(String taskId) {
    }
}
