package io.a2a.extras.queuemanager.replicated.tests;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.a2a.extras.queuemanager.replicated.core.ReplicatedEvent;
import io.a2a.util.Utils;
import io.quarkus.arc.profile.IfBuildProfile;

/**
 * Test consumer for Kafka replicated events using reactive messaging.
 * Uses a separate incoming channel to avoid interfering with the main application consumer.
 */
@IfBuildProfile("test")
@ApplicationScoped
public class TestKafkaEventConsumer {

    private final ConcurrentLinkedQueue<ReplicatedEvent> receivedEvents = new ConcurrentLinkedQueue<>();
    private volatile CountDownLatch eventLatch;

    @Incoming("test-replicated-events-in")
    public void onTestReplicatedEvent(String jsonMessage) {
        try {
            ReplicatedEvent event = Utils.OBJECT_MAPPER.readValue(jsonMessage, ReplicatedEvent.class);
            receivedEvents.offer(event);

            // Signal any waiting threads
            if (eventLatch != null) {
                eventLatch.countDown();
            }
        } catch (Exception e) {
            // Log error but don't fail the message processing
            System.err.println("Failed to process test Kafka message: " + e.getMessage());
        }
    }

    /**
     * Wait for an event matching the given task ID.
     * @param taskId the task ID to wait for
     * @param timeoutSeconds maximum time to wait
     * @return the matching ReplicatedEvent, or null if timeout
     */
    public ReplicatedEvent waitForEvent(String taskId, int timeoutSeconds) throws InterruptedException {
        // Check if we already have the event
        ReplicatedEvent existing = findEventByTaskId(taskId);
        if (existing != null) {
            return existing;
        }

        // Set up latch to wait for new events
        eventLatch = new CountDownLatch(1);

        try {
            // Wait for new events
            boolean received = eventLatch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (received) {
                // Check again for the event
                return findEventByTaskId(taskId);
            }
            return null;
        } finally {
            eventLatch = null;
        }
    }

    /**
     * Wait for an event matching the given task ID and containing specific content.
     * @param taskId the task ID to wait for
     * @param contentMatch a string that must be present in the event
     * @param timeoutSeconds maximum time to wait
     * @return the matching ReplicatedEvent, or null if timeout
     */
    public ReplicatedEvent waitForEventWithContent(String taskId, String contentMatch, int timeoutSeconds) throws InterruptedException {
        // Check if we already have the event
        ReplicatedEvent existing = findEventByTaskIdWithContent(taskId, contentMatch);
        if (existing != null) {
            return existing;
        }

        // Set up latch to wait for new events
        eventLatch = new CountDownLatch(1);

        try {
            // Wait for new events
            boolean received = eventLatch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (received) {
                // Check again for the event
                return findEventByTaskIdWithContent(taskId, contentMatch);
            }
            return null;
        } finally {
            eventLatch = null;
        }
    }

    /**
     * Find an event by task ID in the received events.
     */
    private ReplicatedEvent findEventByTaskId(String taskId) {
        return receivedEvents.stream()
                .filter(event -> taskId.equals(event.getTaskId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Find an event by task ID and content match in the received events.
     */
    private ReplicatedEvent findEventByTaskIdWithContent(String taskId, String contentMatch) {
        return receivedEvents.stream()
                .filter(event -> taskId.equals(event.getTaskId()) &&
                        event.getEvent().toString().contains(contentMatch))
                .findFirst()
                .orElse(null);
    }

    /**
     * Clear all received events (useful for test cleanup).
     */
    public void clear() {
        receivedEvents.clear();
    }

    /**
     * Get count of received events.
     */
    public int getEventCount() {
        return receivedEvents.size();
    }
}