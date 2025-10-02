package io.a2a.server.events;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InMemoryQueueManager implements QueueManager {
    private final ConcurrentMap<String, EventQueue> queues = new ConcurrentHashMap<>();
    private final EventQueueFactory factory;

    public InMemoryQueueManager() {
        this.factory = new DefaultEventQueueFactory();
    }

    public InMemoryQueueManager(EventQueueFactory factory) {
        this.factory = factory;
    }

    @Override
    public void add(String taskId, EventQueue queue) {
        EventQueue existing = queues.putIfAbsent(taskId, queue);
        if (existing != null) {
            throw new TaskQueueExistsException();
        }
    }

    @Override
    public EventQueue get(String taskId) {
        return queues.get(taskId);
    }

    @Override
    public EventQueue tap(String taskId) {
        EventQueue queue = queues.get(taskId);
        return queue == null ? null : queue.tap();
    }

    @Override
    public void close(String taskId) {
        EventQueue existing = queues.remove(taskId);
        if (existing == null) {
            throw new NoTaskQueueException();
        }
    }

    @Override
    public EventQueue createOrTap(String taskId) {
        EventQueue existing = queues.get(taskId);

        // Lazy cleanup: remove closed queues from map
        if (existing != null && existing.isClosed()) {
            queues.remove(taskId);
            existing = null;
        }

        EventQueue newQueue = null;
        if (existing == null) {
            // Use builder pattern for cleaner queue creation
            // Use the new taskId-aware builder method if available
            newQueue = factory.builder(taskId).build();
            // Make sure an existing queue has not been added in the meantime
            existing = queues.putIfAbsent(taskId, newQueue);
        }

        EventQueue main = existing == null ? newQueue : existing;
        return main.tap();  // Always return ChildQueue
    }

    @Override
    public void awaitQueuePollerStart(EventQueue eventQueue) throws InterruptedException {
        eventQueue.awaitQueuePollerStart();
    }

    private static class DefaultEventQueueFactory implements EventQueueFactory {
        @Override
        public EventQueue.EventQueueBuilder builder(String taskId) {
            // Default implementation doesn't need task-specific configuration
            return EventQueue.builder();
        }
    }
}
