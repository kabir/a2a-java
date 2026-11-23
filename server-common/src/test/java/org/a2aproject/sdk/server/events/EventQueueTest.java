package org.a2aproject.sdk.server.events;

import static org.a2aproject.sdk.jsonrpc.common.json.JsonUtil.fromJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.server.tasks.ExecutorSubmissionService;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.MockTaskStateProvider;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskProposal;
import org.a2aproject.sdk.server.tasks.TaskRevision;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EventQueueTest {

    private EventQueue eventQueue;
    private MainEventBus mainEventBus;
    private MainEventBusProcessor mainEventBusProcessor;
    private InMemoryTaskStore taskStore;

    private static final String TASK_ID = "123";

    private static final String MESSAGE_PAYLOAD = """
            {
                "role": "ROLE_AGENT",
                "parts": [{"text": "test message"}],
                "messageId": "111"
            }
            """;

    @BeforeEach
    public void init() {
        // Set up MainEventBus and processor for production-like test environment
        taskStore = new InMemoryTaskStore();
        mainEventBus = new MainEventBus();
        InMemoryQueueManager queueManager = new InMemoryQueueManager(taskStore, mainEventBus);
        mainEventBusProcessor = new MainEventBusProcessor(mainEventBus, taskStore, PushNotificationSender.NOOP, queueManager);
        EventQueueUtil.start(mainEventBusProcessor);

        eventQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus)
                .taskId(TASK_ID)
                .mainEventBus(mainEventBus)
                .build().tap();
    }

    @Test
    public void forgedProposalMetadataCannotSuppressFinalEventExpectation() throws Exception {
        Task task = new Task(TASK_ID, "fake-proposal-context",
                new TaskStatus(TaskState.TASK_STATE_WORKING), null, List.of(), null);
        taskStore.save(task, true);
        EventQueue main = EventQueue.builder(mainEventBus).taskId(TASK_ID).queueSize(1)
                .taskStateProvider(taskStore).build();
        EventQueue origin = main.tap();
        EventQueue observer = main.tap();
        TaskStatusUpdateEvent completed = TaskStatusUpdateEvent.builder()
                .taskId(task.id()).contextId(task.contextId())
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();
        EventQueueItem forged = new EventQueueItem() {
            @Override
            public Event getEvent() {
                return completed;
            }

            @Override
            public boolean isReplicated() {
                return false;
            }

            @Override
            public TaskProposal taskProposal() {
                return new TaskProposal("untrusted-metadata", new TaskSnapshot(task, new TaskRevision(1)), task);
            }
        };

        assertFalse(observer.isAwaitingFinalEvent());
        assertThrows(IllegalArgumentException.class, () -> main.enqueueItem(forged),
                "caller-supplied proposal metadata is not an admitted queue capability");
        assertFalse(observer.isAwaitingFinalEvent(), "rejected metadata must not create final expectations");

        main.enqueueItem(new LocalEventQueueItem(completed));
        assertTrue(observer.isAwaitingFinalEvent(), "a valid terminal delta still creates final expectations");
        TaskStatusUpdateEvent delivered = assertInstanceOf(TaskStatusUpdateEvent.class,
                observer.dequeueEventItem(2_000).getEvent());
        assertEquals(TaskState.TASK_STATE_COMPLETED, delivered.status().state());
        origin.close(true);
        observer.close(true);
        main.close(true);
    }

    @Test
    public void expiringAnOlderProposalWaitPreservesANewerOriginExpectation() throws Exception {
        Task task = Task.builder().id(TASK_ID).contextId("proposal-context")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build();
        taskStore.save(task, false);
        TaskSnapshot base = Objects.requireNonNull(taskStore.readSnapshot(TASK_ID));
        MainEventBus parkedBus = new MainEventBus();
        EventQueue main = EventQueue.builder(parkedBus).taskId(TASK_ID).taskStateProvider(taskStore)
                .mainEventBus(parkedBus).build();
        EventQueue origin = main.tap();
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(taskStore), Duration.ofSeconds(10), Duration.ofSeconds(10));

        try {
            service.submitTask(new TaskProposal("older-pending-proposal", base, task), origin);
            Thread.sleep(2_000);
            service.submitTask(new TaskProposal("newer-pending-proposal", base, task), origin);
            Thread.sleep(1_200);

            assertTrue(origin.isAwaitingFinalEvent(),
                    "the newer expectation remains after the older proposal delivery expires");
            assertFalse(origin.closeIfNotAwaitingFinalEvent(),
                    "the newer expectation still protects the origin from graceful close");

            Thread.sleep(2_000);
            assertFalse(origin.isAwaitingFinalEvent(), "the newer proposal also expires at its own deadline");
            assertTrue(origin.closeIfNotAwaitingFinalEvent());
        } finally {
            main.close(true);
        }
    }

    @AfterEach
    public void cleanup() {
        if (mainEventBusProcessor != null) {
            mainEventBusProcessor.setCallback(null);  // Clear any test callbacks
            EventQueueUtil.stop(mainEventBusProcessor);
        }
    }

    /**
     * Helper to create a queue with MainEventBus configured (for tests that need event distribution).
     */
    private EventQueue createQueueWithEventBus(String taskId) {
        return createQueueWithEventBus(taskId, null);
    }

    private EventQueue createQueueWithEventBus(String taskId, @Nullable TaskStreamLifecycleHook hook) {
        return EventQueueUtil.getEventQueueBuilder(mainEventBus)
                .taskId(taskId)
                .streamLifecycleHook(hook)
                .build();
    }

    /**
     * Helper to wait for MainEventBusProcessor to process an event.
     * Replaces polling patterns with deterministic callback-based waiting.
     *
     * @param action the action that triggers event processing
     * @throws InterruptedException if waiting is interrupted
     * @throws AssertionError if processing doesn't complete within timeout
     */
    private void waitForEventProcessing(Runnable action) throws InterruptedException {
        CountDownLatch processingLatch = new CountDownLatch(1);
        mainEventBusProcessor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void onEventProcessed(String taskId, Event event) {
                processingLatch.countDown();
            }

            @Override
            public void onTaskFinalized(String taskId) {
                // Not needed for basic event processing wait
            }
        });

        try {
            action.run();
            assertTrue(processingLatch.await(5, TimeUnit.SECONDS),
                    "MainEventBusProcessor should have processed the event within timeout");
        } finally {
            mainEventBusProcessor.setCallback(null);
        }
    }

    @Test
    public void testConstructorDefaultQueueSize() {
        EventQueue queue = EventQueueUtil.getEventQueueBuilder(mainEventBus).build();
        assertEquals(EventQueue.DEFAULT_QUEUE_SIZE, queue.getQueueSize());
    }

    @Test
    public void testConstructorCustomQueueSize() {
        int customSize = 500;
        EventQueue queue = EventQueueUtil.getEventQueueBuilder(mainEventBus).queueSize(customSize).build();
        assertEquals(customSize, queue.getQueueSize());
    }

    @Test
    public void testConstructorInvalidQueueSize() {
        // Test zero queue size
        assertThrows(IllegalArgumentException.class, () -> EventQueueUtil.getEventQueueBuilder(mainEventBus).queueSize(0).build());

        // Test negative queue size
        assertThrows(IllegalArgumentException.class, () -> EventQueueUtil.getEventQueueBuilder(mainEventBus).queueSize(-10).build());
    }

    @Test
    public void testTapCreatesChildQueue() {
        EventQueue parentQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus).build();
        EventQueue childQueue = parentQueue.tap();

        assertNotNull(childQueue);
        assertNotSame(parentQueue, childQueue);
        assertEquals(EventQueue.DEFAULT_QUEUE_SIZE, childQueue.getQueueSize());
    }

    @Test
    public void testTapOnChildQueueThrowsException() {
        EventQueue parentQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus).build();
        EventQueue childQueue = parentQueue.tap();

        assertThrows(IllegalStateException.class, () -> childQueue.tap());
    }

    @Test
    public void testEnqueueEventPropagagesToChildren() throws Exception {
        EventQueue mainQueue = createQueueWithEventBus(TASK_ID);
        EventQueue childQueue1 = mainQueue.tap();
        EventQueue childQueue2 = mainQueue.tap();

        Event event = fromJson(MESSAGE_PAYLOAD, Message.class);
        mainQueue.enqueueEvent(event);

        // Event should be available in all child queues
        // Note: MainEventBusProcessor runs async, so we use dequeueEventItem with timeout
        Event child1Event = childQueue1.dequeueEventItem(5000).getEvent();
        Event child2Event = childQueue2.dequeueEventItem(5000).getEvent();

        assertSame(event, child1Event);
        assertSame(event, child2Event);
    }

    @Test
    public void testMultipleChildQueuesReceiveEvents() throws Exception {
        EventQueue mainQueue = createQueueWithEventBus(TASK_ID);
        EventQueue childQueue1 = mainQueue.tap();
        EventQueue childQueue2 = mainQueue.tap();
        EventQueue childQueue3 = mainQueue.tap();

        Event event1 = fromJson(MESSAGE_PAYLOAD, Message.class);
        Event event2 = fromJson(MESSAGE_PAYLOAD, Message.class);

        mainQueue.enqueueEvent(event1);
        mainQueue.enqueueEvent(event2);

        // All child queues should receive both events
        // Note: Use timeout for async processing
        assertSame(event1, childQueue1.dequeueEventItem(5000).getEvent());
        assertSame(event2, childQueue1.dequeueEventItem(5000).getEvent());

        assertSame(event1, childQueue2.dequeueEventItem(5000).getEvent());
        assertSame(event2, childQueue2.dequeueEventItem(5000).getEvent());

        assertSame(event1, childQueue3.dequeueEventItem(5000).getEvent());
        assertSame(event2, childQueue3.dequeueEventItem(5000).getEvent());
    }

    @Test
    public void testChildQueueDequeueIndependently() throws Exception {
        EventQueue mainQueue = createQueueWithEventBus(TASK_ID);
        EventQueue childQueue1 = mainQueue.tap();
        EventQueue childQueue2 = mainQueue.tap();
        EventQueue childQueue3 = mainQueue.tap();

        Event event = fromJson(MESSAGE_PAYLOAD, Message.class);
        mainQueue.enqueueEvent(event);

        // Dequeue from child1 first (use timeout for async processing)
        Event child1Event = childQueue1.dequeueEventItem(5000).getEvent();
        assertSame(event, child1Event);

        // child2 should still have the event available
        Event child2Event = childQueue2.dequeueEventItem(5000).getEvent();
        assertSame(event, child2Event);

        // child3 should still have the event available
        Event child3Event = childQueue3.dequeueEventItem(5000).getEvent();
        assertSame(event, child3Event);
    }


    @Test
    public void testCloseImmediatePropagationToChildren() throws Exception {
        EventQueue parentQueue = createQueueWithEventBus(TASK_ID);
        EventQueue childQueue = parentQueue.tap();

        // Add events to both parent and child
        Event event = fromJson(MESSAGE_PAYLOAD, Message.class);
        parentQueue.enqueueEvent(event);

        assertFalse(childQueue.isClosed());
        try {
            assertNotNull(childQueue.dequeueEventItem(5000)); // Child has the event (use timeout)
        } catch (EventQueueClosedException e) {
            // This is fine if queue closed before dequeue
        }

        // Add event again for immediate close test
        parentQueue.enqueueEvent(event);

        // Close with immediate=true
        parentQueue.close(true);

        assertTrue(parentQueue.isClosed());
        assertTrue(childQueue.isClosed());

        // Child queue should be cleared due to immediate close
        // Child queue should be cleared and closed, so dequeueing should throw
        assertThrows(EventQueueClosedException.class, () -> childQueue.dequeueEventItem(-1));
    }

    @Test
    public void testEnqueueEventWhenClosed() throws Exception {
        EventQueue mainQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus)
                .taskId(TASK_ID)
                .build();
        EventQueue childQueue = mainQueue.tap();
        Event event = fromJson(MESSAGE_PAYLOAD, Message.class);

        childQueue.close(); // Close the child queue first (removes from children list)
        assertTrue(childQueue.isClosed());

        // Create a new child queue BEFORE enqueuing (ensures it's in children list for distribution)
        EventQueue newChildQueue = mainQueue.tap();

        // MainQueue accepts events even when closed (for replication support)
        // This ensures late-arriving replicated events can be enqueued to closed queues
        // Note: MainEventBusProcessor runs asynchronously, so we use dequeueEventItem with timeout
        mainQueue.enqueueEvent(event);

        // New child queue should receive the event (old closed child was removed from children list)
        EventQueueItem item = newChildQueue.dequeueEventItem(5000);
        assertNotNull(item);
        Event dequeuedEvent = item.getEvent();
        assertSame(event, dequeuedEvent);

        // Now new child queue is closed and empty, should throw exception
        newChildQueue.close();
        assertThrows(EventQueueClosedException.class, () -> newChildQueue.dequeueEventItem(-1));
    }

    @Test
    public void testDequeueEventWhenClosedAndEmpty() throws Exception {
        EventQueue queue = EventQueueUtil.getEventQueueBuilder(mainEventBus).build().tap();
        queue.close();
        assertTrue(queue.isClosed());

        // Dequeue from closed empty queue should throw exception
        assertThrows(EventQueueClosedException.class, () -> queue.dequeueEventItem(-1));
    }

    @Test
    public void testDequeueEventWhenClosedButHasEvents() throws Exception {
        EventQueue mainQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus)
                .taskId(TASK_ID)
                .build();
        EventQueue childQueue = mainQueue.tap();
        Event event = fromJson(MESSAGE_PAYLOAD, Message.class);

        // Use callback to wait for event processing instead of polling
        waitForEventProcessing(() -> mainQueue.enqueueEvent(event));

        // At this point, event has been processed and distributed to childQueue
        childQueue.close(); // Graceful close - events should remain
        assertTrue(childQueue.isClosed());

        // Should still be able to dequeue existing events from closed queue
        EventQueueItem item = childQueue.dequeueEventItem(5000);
        assertNotNull(item);
        Event dequeuedEvent = item.getEvent();
        assertSame(event, dequeuedEvent);

        // Now queue is closed and empty, should throw exception
        assertThrows(EventQueueClosedException.class, () -> childQueue.dequeueEventItem(-1));
    }

    @Test
    public void testEnqueueAndDequeueEvent() throws Exception {
        Event event = fromJson(MESSAGE_PAYLOAD, Message.class);
        eventQueue.enqueueEvent(event);
        Event dequeuedEvent = eventQueue.dequeueEventItem(200).getEvent();
        assertSame(event, dequeuedEvent);
    }

    @Test
    public void testDequeueEventNoWait() throws Exception {
        Event event = fromJson(MESSAGE_PAYLOAD, Message.class);
        eventQueue.enqueueEvent(event);
        EventQueueItem item = eventQueue.dequeueEventItem(5000);
        assertNotNull(item);
        Event dequeuedEvent = item.getEvent();
        assertSame(event, dequeuedEvent);
    }

    @Test
    public void testDequeueEventEmptyQueueNoWait() throws Exception {
        EventQueueItem item = eventQueue.dequeueEventItem(-1);
        assertNull(item);
    }

    @Test
    public void testDequeueEventWait() throws Exception {
        Event event = TaskStatusUpdateEvent.builder()
                .taskId(TASK_ID)
                .contextId("session-xyz")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .build();

        eventQueue.enqueueEvent(event);
        Event dequeuedEvent = eventQueue.dequeueEventItem(1000).getEvent();
        assertSame(event, dequeuedEvent);
    }

    @Test
    public void testTaskDone() throws Exception {
        Event event = TaskArtifactUpdateEvent.builder()
                .taskId(TASK_ID)
                .contextId("session-xyz")
                .artifact(Artifact.builder()
                        .artifactId("11")
                        .parts(new TextPart("text"))
                        .build())
                .build();
        eventQueue.enqueueEvent(event);
        Event dequeuedEvent = eventQueue.dequeueEventItem(1000).getEvent();
        assertSame(event, dequeuedEvent);
        eventQueue.taskDone();
    }

    @Test
    public void testEnqueueDifferentEventTypes() throws Exception {
        List<Event> events = List.of(
                new TaskNotFoundError(),
                new A2AError(111, "rpc error", null));

        for (Event event : events) {
            eventQueue.enqueueEvent(event);
            Event dequeuedEvent = eventQueue.dequeueEventItem(100).getEvent();
            assertSame(event, dequeuedEvent);
        }
    }

    /**
     * Test close behavior sets flag and handles graceful close.
     * Backported from Python test: test_close_sets_flag_and_handles_internal_queue_old_python
     */
    @Test
    public void testCloseGracefulSetsFlag() throws Exception {
        Event event = fromJson(MESSAGE_PAYLOAD, Message.class);
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
        Event event = fromJson(MESSAGE_PAYLOAD, Message.class);
        eventQueue.enqueueEvent(event);

        eventQueue.close(true); // Immediate close
        assertTrue(eventQueue.isClosed());

        // After immediate close, queue should be cleared
        // Attempting to dequeue should return null or throw exception
        try {
            EventQueueItem item = eventQueue.dequeueEventItem(-1);
            // If we get here, the item should be null (queue was cleared)
            assertNull(item);
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
        EventQueue eventQueue2 = EventQueueUtil.getEventQueueBuilder(mainEventBus).build();
        eventQueue2.close(true);
        assertTrue(eventQueue2.isClosed());

        eventQueue2.close(true);
        assertTrue(eventQueue2.isClosed());
    }

    /**
     * Test that child queues are NOT automatically closed when parent closes gracefully.
     * Children must close themselves, which then notifies parent via reference counting.
     */
    @Test
    public void testCloseChildQueues() throws Exception {
        EventQueue mainQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus).build();
        EventQueue childQueue = mainQueue.tap();
        assertTrue(childQueue != null);

        // Graceful close - parent closes but children remain open
        mainQueue.close();
        assertTrue(mainQueue.isClosed());
        assertFalse(childQueue.isClosed());  // Child NOT closed on graceful parent close

        // Immediate close - parent force-closes all children
        EventQueue mainQueue2 = EventQueueUtil.getEventQueueBuilder(mainEventBus).build();
        EventQueue childQueue2 = mainQueue2.tap();
        mainQueue2.close(true);  // immediate=true
        assertTrue(mainQueue2.isClosed());
        assertTrue(childQueue2.isClosed());  // Child IS closed on immediate parent close
    }

    /**
     * Test reference counting: MainQueue stays open while children are active,
     * closes automatically when last child closes.
     */
    @Test
    public void testMainQueueReferenceCountingStaysOpenWithActiveChildren() throws Exception {
        EventQueue mainQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus).build();
        EventQueue child1 = mainQueue.tap();
        EventQueue child2 = mainQueue.tap();

        // Close child1
        child1.close();

        // MainQueue should still be open (child2 active)
        assertFalse(mainQueue.isClosed());
        assertTrue(child1.isClosed());
        assertFalse(child2.isClosed());

        // Close child2
        child2.close();

        // Now MainQueue should auto-close (no children left)
        assertTrue(mainQueue.isClosed());
        assertTrue(child2.isClosed());
    }

    /**
     * Helper to create a queue with a TaskStateProvider that keeps MainQueue open
     * when all children close (task is never finalized).
     */
    private EventQueue createNonFinalizedQueueWithEventBus(String taskId) {
        return EventQueueUtil.getEventQueueBuilder(mainEventBus)
                .taskId(taskId)
                .taskStateProvider(new MockTaskStateProvider())
                .build();
    }

    @Test
    public void testCloseChildrenGracefullyClosesAllChildren() throws InterruptedException {
        EventQueue mainQueue = createNonFinalizedQueueWithEventBus("close-children-test");

        EventQueue child1 = mainQueue.tap();
        EventQueue child2 = mainQueue.tap();
        assertFalse(child1.isClosed());
        assertFalse(child2.isClosed());

        ((EventQueue.MainQueue) mainQueue).closeChildren();

        assertTrue(child1.isClosed());
        assertTrue(child2.isClosed());

        assertFalse(mainQueue.isClosed());
    }

    @Test
    public void testCloseChildrenAllowsNewSubscriptions() throws InterruptedException {
        EventQueue mainQueue = createNonFinalizedQueueWithEventBus("close-children-resubscribe-test");

        EventQueue child1 = mainQueue.tap();
        ((EventQueue.MainQueue) mainQueue).closeChildren();
        assertTrue(child1.isClosed());

        EventQueue child2 = mainQueue.tap();
        assertFalse(child2.isClosed());
        assertFalse(mainQueue.isClosed());
    }

    @Test
    public void testStreamCloseHandleCloseStreams() throws InterruptedException {
        EventQueue mainQueue = createNonFinalizedQueueWithEventBus("handle-close-test");

        EventQueue child1 = mainQueue.tap();
        EventQueue child2 = mainQueue.tap();

        StreamCloseHandle handle = ((EventQueue.MainQueue) mainQueue).getStreamCloseHandle();
        assertEquals(2, handle.getActiveSubscriberCount());

        handle.closeStreams();

        assertTrue(child1.isClosed());
        assertTrue(child2.isClosed());
        assertFalse(mainQueue.isClosed());
        assertEquals(0, handle.getActiveSubscriberCount());
    }

    @Test
    public void testStreamLifecycleHookOnEventCalledAfterDistribution() throws InterruptedException {
        List<Event> receivedEvents = new ArrayList<>();
        CountDownLatch hookLatch = new CountDownLatch(1);
        TaskStreamLifecycleHook hook = TestStreamLifecycleHook.onEvent((taskId, event, handle) -> {
            receivedEvents.add(event);
            hookLatch.countDown();
        });

        EventQueue mainQueue = createQueueWithEventBus("hook-event-test", hook);
        EventQueue child = mainQueue.tap();

        TaskStatusUpdateEvent statusEvent = TaskStatusUpdateEvent.builder()
                .taskId("hook-event-test")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, null))
                .build();

        mainQueue.enqueueEvent(statusEvent);

        assertTrue(hookLatch.await(5, TimeUnit.SECONDS),
                "TaskStreamLifecycleHook.onEvent should have been called");

        assertEquals(1, receivedEvents.size());
        assertSame(statusEvent, receivedEvents.get(0));
    }

    @Test
    public void testCloseStreamsFromWithinOnEventDoesNotCauseConcurrentModification() throws InterruptedException {
        CountDownLatch hookLatch = new CountDownLatch(1);
        AtomicReference<Throwable> caughtException = new AtomicReference<>();
        TaskStreamLifecycleHook hook = TestStreamLifecycleHook.onEvent((taskId, event, handle) -> {
            try {
                handle.closeStreams();
            } catch (Throwable t) {
                caughtException.set(t);
            } finally {
                hookLatch.countDown();
            }
        });

        EventQueue mainQueue = createQueueWithEventBus("close-in-hook-test", hook);
        mainQueue.tap();
        mainQueue.tap();

        TaskStatusUpdateEvent statusEvent = TaskStatusUpdateEvent.builder()
                .taskId("close-in-hook-test")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, null))
                .build();

        mainQueue.enqueueEvent(statusEvent);

        assertTrue(hookLatch.await(5, TimeUnit.SECONDS),
                "onEvent should have been called");
        assertNull(caughtException.get(),
                "closeStreams() from within onEvent should not throw");
    }

    @Test
    public void testCloseStreamsFromOnUnsubscribeIsNoOp() {
        AtomicInteger unsubscribeCount = new AtomicInteger(0);
        AtomicReference<Throwable> caughtException = new AtomicReference<>();
        TaskStreamLifecycleHook hook = TestStreamLifecycleHook.onUnsubscribe((taskId, handle) -> {
            unsubscribeCount.incrementAndGet();
            try {
                handle.closeStreams();
            } catch (Throwable t) {
                caughtException.set(t);
            }
        });

        EventQueue mainQueue = createQueueWithEventBus("reentrant-close-test", hook);
        mainQueue.tap();
        mainQueue.tap();
        mainQueue.tap();

        ((EventQueue.MainQueue) mainQueue).closeChildren();

        assertNull(caughtException.get(),
                "Reentrant closeStreams() from onUnsubscribe should not throw");
        assertEquals(3, unsubscribeCount.get(),
                "All three children should have triggered onUnsubscribe exactly once");
        assertEquals(0, ((EventQueue.MainQueue) mainQueue).getActiveChildCount(),
                "All children should be closed");
    }

    @Test
    public void testTapReturnsClosedQueueWhenHookCallsCloseStreams() {
        TaskStreamLifecycleHook hook = TestStreamLifecycleHook.onSubscribe((taskId, handle) -> handle.closeStreams());

        EventQueue mainQueue = createQueueWithEventBus("tap-close-test", hook);
        EventQueue child = mainQueue.tap();

        assertTrue(child.isClosed(),
                "ChildQueue returned by tap() should be closed when hook calls closeStreams() during onSubscribe");
        assertThrows(EventQueueClosedException.class,
                () -> child.dequeueEventItem(0),
                "Dequeuing from a closed ChildQueue should throw EventQueueClosedException");
    }

    @Test
    public void testOnEventNotCalledWithZeroSubscribers() throws InterruptedException {
        AtomicBoolean onEventCalled = new AtomicBoolean(false);
        TaskStreamLifecycleHook hook = TestStreamLifecycleHook.onEvent((taskId, event, handle) -> onEventCalled.set(true));

        EventQueue mainQueue = createQueueWithEventBus("no-subscribers-test", hook);

        TaskStatusUpdateEvent statusEvent = TaskStatusUpdateEvent.builder()
                .taskId("no-subscribers-test")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, null))
                .build();

        CountDownLatch processingLatch = new CountDownLatch(1);
        mainEventBusProcessor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void onEventProcessed(String taskId, Event event) {
                processingLatch.countDown();
            }

            @Override
            public void onTaskFinalized(String taskId) {
            }
        });

        try {
            mainQueue.enqueueEvent(statusEvent);
            assertTrue(processingLatch.await(5, TimeUnit.SECONDS),
                    "Event should have been processed");
        } finally {
            mainEventBusProcessor.setCallback(null);
        }

        assertFalse(onEventCalled.get(),
                "onEvent should not be called when there are zero subscribers");
    }

    @Test
    public void testChildQueueIsBoundedByParentQueueSize() throws Exception {
        int customSize = 5;
        EventQueue mainQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus)
                .queueSize(customSize)
                .build();
        EventQueue childQueue = mainQueue.tap();

        Field queueField = EventQueue.ChildQueue.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        BlockingQueue<?> childDeque = (BlockingQueue<?>) queueField.get(childQueue);

        // The child queue must be bounded by the parent's configured capacity:
        // an unbounded deque would let a slow subscriber grow memory without limit.
        assertEquals(customSize, childDeque.remainingCapacity());
    }

    private EventQueue.MainQueue directBranchMain(String taskId) {
        return (EventQueue.MainQueue) EventQueue.builder(mainEventBus).taskId(taskId)
                .taskStateProvider(taskStore).mainEventBus(mainEventBus).build();
    }

    @Test
    public void directBranchChildClosingWithNoTaskClosesTheMainQueue() {
        EventQueue.MainQueue main = directBranchMain("direct-only");
        EventQueue.ChildQueue child = (EventQueue.ChildQueue) main.tap();
        child.markDirectMessageResponseSelected();
        child.close();
        assertTrue(main.isClosed(), "a direct Message with no task leaves nothing to keep the queue for");
        assertTrue(main.isClosedForDirectBranch());
    }

    @Test
    public void directBranchChildClosingKeepsTheMainQueueWhileAnotherChildRemains() {
        EventQueue.MainQueue main = directBranchMain("direct-with-sibling");
        EventQueue.ChildQueue direct = (EventQueue.ChildQueue) main.tap();
        EventQueue.ChildQueue sibling = (EventQueue.ChildQueue) main.tap();
        direct.markDirectMessageResponseSelected();
        direct.close();
        assertFalse(main.isClosed(), "a remaining observer keeps the queue open");
        assertFalse(main.isClosedForDirectBranch());
        sibling.close(true);
    }

    @Test
    public void directBranchChildClosingKeepsTheMainQueueOfAnActiveTask() {
        taskStore.save(Task.builder().id("active-task").contextId("ctx")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build(), false);
        EventQueue.MainQueue main = directBranchMain("active-task");
        EventQueue.ChildQueue child = (EventQueue.ChildQueue) main.tap();
        child.markDirectMessageResponseSelected();
        child.close();
        assertFalse(main.isClosed(), "an existing non-final task keeps its queue for resubscription");
        assertFalse(main.isClosedForDirectBranch());
    }

    @Test
    public void nonDirectChildClosingWithNoTaskKeepsTheMainQueueOpen() {
        EventQueue.MainQueue main = directBranchMain("fire-and-forget");
        EventQueue.ChildQueue child = (EventQueue.ChildQueue) main.tap();
        child.close();
        assertFalse(main.isClosed(), "only a selected direct Message response may close an unmaterialized queue");
    }

    @Test
    public void testSemaphorePermitReleasedWhenSubmitFails() {
        MainEventBus failingBus = mock(MainEventBus.class);
        doThrow(new RuntimeException("submit failed"))
                .when(failingBus).submit(anyString(), any(), any(), any());

        EventQueue mainQueue = EventQueueUtil.getEventQueueBuilder(failingBus)
                .taskId(TASK_ID)
                .queueSize(2)
                .build();
        assertEquals(0, mainQueue.size(), "No permits should be in use before enqueue");

        assertThrows(RuntimeException.class,
                () -> mainQueue.enqueueEvent(fromJson(MESSAGE_PAYLOAD, Message.class)));

        // If the permit leaked, size() would be 1 (one permit held forever). It must be
        // released when submit() fails because MainEventBusProcessor never saw the event
        // and therefore never called releaseSemaphore().
        assertEquals(0, mainQueue.size(), "Semaphore permit must be released on submit failure");
    }
}
