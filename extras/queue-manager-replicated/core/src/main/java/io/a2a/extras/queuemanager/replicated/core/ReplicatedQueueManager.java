package io.a2a.extras.queuemanager.replicated.core;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.a2a.server.events.EventEnqueueHook;
import io.a2a.server.events.EventQueue;
import io.a2a.server.events.EventQueueFactory;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.events.QueueManager;
import io.a2a.spec.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Alternative
@Priority(50)
public class ReplicatedQueueManager implements QueueManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplicatedQueueManager.class);

    private final InMemoryQueueManager delegate;
    private final ThreadLocal<Boolean> isHandlingReplicatedEvent = new ThreadLocal<>();

    @Inject
    private ReplicationStrategy replicationStrategy;

    public ReplicatedQueueManager() {
        this.delegate = new InMemoryQueueManager(new ReplicatingEventQueueFactory());
    }

    // For testing
    public ReplicatedQueueManager(ReplicationStrategy replicationStrategy) {
        this.delegate = new InMemoryQueueManager(new ReplicatingEventQueueFactory());
        this.replicationStrategy = replicationStrategy;
    }

    @Override
    public void add(String taskId, EventQueue queue) {
        delegate.add(taskId, queue);
    }

    @Override
    public EventQueue get(String taskId) {
        return delegate.get(taskId);
    }

    @Override
    public EventQueue tap(String taskId) {
        return delegate.tap(taskId);
    }

    @Override
    public void close(String taskId) {
        delegate.close(taskId);
    }

    @Override
    public EventQueue createOrTap(String taskId) {
        EventQueue queue = delegate.createOrTap(taskId);
        return queue;
    }

    @Override
    public void awaitQueuePollerStart(EventQueue eventQueue) throws InterruptedException {
        delegate.awaitQueuePollerStart(eventQueue);
    }

    public void onReplicatedEvent(@Observes ReplicatedEvent replicatedEvent) {
        LOGGER.debug("onReplicatedEvent called for task: {}", replicatedEvent.getTaskId());
        isHandlingReplicatedEvent.set(true);
        try {
            EventQueue queue = delegate.get(replicatedEvent.getTaskId());
            LOGGER.debug("Existing queue for task {}: {}", replicatedEvent.getTaskId(), queue);

            if (queue == null) {
                // If no queue exists, create or tap one to handle the replicated event
                // This can happen when events arrive after the original queue has been closed
                LOGGER.debug("No existing queue, calling createOrTap for task: {}", replicatedEvent.getTaskId());
                queue = delegate.createOrTap(replicatedEvent.getTaskId());
                LOGGER.debug("createOrTap returned queue: {}", queue);
            }

            if (queue != null) {
                // Use the backward compatibility method to get the event as the generic Event interface
                Event event = replicatedEvent.getEventAsGeneric();
                LOGGER.debug("Enqueueing event to queue for task {}: event={}, queue={}",
                    replicatedEvent.getTaskId(), event != null ? event.getClass().getSimpleName() : "null", queue);
                if (event != null) {
                    queue.enqueueEvent(event);
                    LOGGER.debug("Successfully enqueued event for task: {}", replicatedEvent.getTaskId());
                }
            } else {
                LOGGER.warn("No queue available for task: {}, event will be dropped", replicatedEvent.getTaskId());
            }
        } finally {
            isHandlingReplicatedEvent.remove();
        }
    }

    @Override
    public EventQueue.EventQueueBuilder getEventQueueBuilder(String taskId) {
        return QueueManager.super.getEventQueueBuilder(taskId)
                .hook(new ReplicationHook(taskId));
    }

    private class ReplicatingEventQueueFactory implements EventQueueFactory {
        @Override
        public EventQueue.EventQueueBuilder builder(String taskId) {
            // Use the taskId parameter directly instead of ThreadLocal
            return delegate.getEventQueueBuilder(taskId).hook(new ReplicationHook(taskId));
        }
    }

    private class ReplicationHook implements EventEnqueueHook {
        private final String taskId;

        public ReplicationHook(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public void onEnqueue(Event event) {
            // Only replicate if this isn't already a replicated event being processed
            if (isHandlingReplicatedEvent.get() != Boolean.TRUE) {
                if (replicationStrategy != null && taskId != null) {
                    replicationStrategy.send(taskId, event);
                }
            }
        }
    }
}