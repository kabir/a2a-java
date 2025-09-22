package io.a2a.server.events;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.a2a.spec.Artifact;
import io.a2a.spec.Event;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskNotFoundError;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.TextPart;
import io.a2a.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EventQueueTest {

    private EventQueue eventQueue;

    private static final String MINIMAL_TASK = """
            {
                "id": "123",
                "contextId": "session-xyz",
                "status": {"state": "submitted"},
                "kind": "task"
            }
            """;

    private static final String MESSAGE_PAYLOAD = """
            {
                "role": "agent",
                "parts": [{"kind": "text", "text": "test message"}],
                "messageId": "111",
                "kind": "message"
            }
            """;


    @BeforeEach
    public void init() {
        eventQueue = EventQueue.create();

    }

    @Test
    public void testEnqueueAndDequeueEvent() throws Exception {
        Event event = Utils.unmarshalFrom(MESSAGE_PAYLOAD, Message.TYPE_REFERENCE);
        eventQueue.enqueueEvent(event);
        Event dequeuedEvent = eventQueue.dequeueEvent(200);
        assertSame(event, dequeuedEvent);
    }

    @Test
    public void testDequeueEventNoWait() throws Exception {
        Event event = Utils.unmarshalFrom(MINIMAL_TASK, Task.TYPE_REFERENCE);
        eventQueue.enqueueEvent(event);
        Event dequeuedEvent = eventQueue.dequeueEvent(-1);
        assertSame(event, dequeuedEvent);
    }

    @Test
    public void testDequeueEventEmptyQueueNoWait() throws Exception {
        Event dequeuedEvent = eventQueue.dequeueEvent(-1);
        assertNull(dequeuedEvent);
    }

    @Test
    public void testDequeueEventWait() throws Exception {
        Event event = new TaskStatusUpdateEvent.Builder()
                .taskId("task-123")
                .contextId("session-xyz")
                .status(new TaskStatus(TaskState.WORKING))
                .isFinal(true)
                .build();

        eventQueue.enqueueEvent(event);
        Event dequeuedEvent = eventQueue.dequeueEvent(1000);
        assertSame(event, dequeuedEvent);
    }

    @Test
    public void testTaskDone() throws Exception {
        Event event = new TaskArtifactUpdateEvent.Builder()
                .taskId("task-123")
                .contextId("session-xyz")
                .artifact(new Artifact.Builder()
                        .artifactId("11")
                        .parts(new TextPart("text"))
                        .build())
                .build();
        eventQueue.enqueueEvent(event);
        Event dequeuedEvent = eventQueue.dequeueEvent(1000);
        assertSame(event, dequeuedEvent);
        eventQueue.taskDone();
    }

    @Test
    public void testEnqueueDifferentEventTypes() throws Exception {
        List<Event> events = List.of(
                new TaskNotFoundError(),
                new JSONRPCError(111, "rpc error", null));

        for (Event event : events) {
            eventQueue.enqueueEvent(event);
            Event dequeuedEvent = eventQueue.dequeueEvent(100);
            assertSame(event, dequeuedEvent);
        }
    }

    /**
     * Test close behavior sets flag and handles graceful close.
     * Backported from Python test: test_close_sets_flag_and_handles_internal_queue_old_python
     */
    @Test
    public void testCloseGracefulSetsFlag() throws Exception {
        Event event = Utils.unmarshalFrom(MINIMAL_TASK, Task.TYPE_REFERENCE);
        eventQueue.enqueueEvent(event);

        eventQueue.close(false); // Graceful close
        assertTrue(eventQueue.isClosed());
    }

    /**
     * Test immediate close behavior.
     * Backported from Python test behavior
     */
    @Test
    public void testCloseImmediateClearsQueue() throws Exception {
        Event event = Utils.unmarshalFrom(MINIMAL_TASK, Task.TYPE_REFERENCE);
        eventQueue.enqueueEvent(event);

        eventQueue.close(true); // Immediate close
        assertTrue(eventQueue.isClosed());

        // After immediate close, queue should be cleared
        // Attempting to dequeue should return null or throw exception
        try {
            Event dequeuedEvent = eventQueue.dequeueEvent(-1);
            // If we get here, the event should be null (queue was cleared)
            assertNull(dequeuedEvent);
        } catch (EventQueueClosedException e) {
            // This is also acceptable - queue is closed
        }
    }

    /**
     * Test that close is idempotent.
     * Backported from Python test: test_close_idempotent
     */
    @Test
    public void testCloseIdempotent() throws Exception {
        eventQueue.close();
        assertTrue(eventQueue.isClosed());

        // Calling close again should not cause issues
        eventQueue.close();
        assertTrue(eventQueue.isClosed());

        // Test with immediate close as well
        EventQueue eventQueue2 = EventQueue.create();
        eventQueue2.close(true);
        assertTrue(eventQueue2.isClosed());

        eventQueue2.close(true);
        assertTrue(eventQueue2.isClosed());
    }

    /**
     * Test that child queues are closed when parent closes.
     */
    @Test
    public void testCloseChildQueues() throws Exception {
        EventQueue childQueue = eventQueue.tap();
        assertTrue(childQueue != null);

        eventQueue.close();
        assertTrue(eventQueue.isClosed());
        assertTrue(childQueue.isClosed());
    }
}
