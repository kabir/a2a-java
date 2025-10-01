package io.a2a.server.events;

public interface QueueManager {
    void add(String taskId, EventQueue queue);

    EventQueue get(String taskId);

    EventQueue tap(String taskId);

    /**
     * @deprecated since 0.3.0, for removal in 0.4.0. Queue cleanup now handled automatically
     * via reference counting when all child queues close. This method is no longer needed.
     */
    @Deprecated(since = "0.3.0", forRemoval = true)
    void close(String taskId);

    EventQueue createOrTap(String taskId);

    void awaitQueuePollerStart(EventQueue eventQueue) throws InterruptedException;

    default EventQueue.EventQueueBuilder getEventQueueBuilder(String taskId) {
        return EventQueue.builder();
    }
}
