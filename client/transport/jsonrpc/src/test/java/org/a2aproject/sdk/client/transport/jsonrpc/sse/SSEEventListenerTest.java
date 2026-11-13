package org.a2aproject.sdk.client.transport.jsonrpc.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import org.a2aproject.sdk.client.http.ServerSentEvent;
import org.a2aproject.sdk.client.transport.jsonrpc.JsonStreamingMessages;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportEnd;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

public class SSEEventListenerTest {

    @Test
    public void testOnEventWithTaskResult() throws Exception {
        // Set up event handler
        AtomicReference<StreamingEventKind> receivedEvent = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> receivedEvent.set(event),
                error -> {}
        );

        // Parse the task event JSON
        String eventData = JsonStreamingMessages.STREAMING_TASK_EVENT.substring(
                JsonStreamingMessages.STREAMING_TASK_EVENT.indexOf("{"));
        
        // Call the onEvent method directly
        listener.onMessage(new ServerSentEvent(eventData), null);

        // Verify the event was processed correctly
        assertNotNull(receivedEvent.get());
        assertTrue(receivedEvent.get() instanceof Task);
        Task task = (Task) receivedEvent.get();
        assertEquals("task-123", task.id());
        assertEquals("context-456", task.contextId());
        assertEquals(TaskState.TASK_STATE_WORKING, task.status().state());
    }

    @Test
    public void testOnEventWithMessageResult() throws Exception {
        // Set up event handler
        AtomicReference<StreamingEventKind> receivedEvent = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> receivedEvent.set(event),
                error -> {}
        );

        // Parse the message event JSON
        String eventData = JsonStreamingMessages.STREAMING_MESSAGE_EVENT.substring(
                JsonStreamingMessages.STREAMING_MESSAGE_EVENT.indexOf("{"));
        
        // Call onEvent method
        listener.onMessage(new ServerSentEvent(eventData), null);

        // Verify the event was processed correctly
        assertNotNull(receivedEvent.get());
        assertTrue(receivedEvent.get() instanceof Message);
        Message message = (Message) receivedEvent.get();
        assertEquals(Message.Role.ROLE_AGENT, message.role());
        assertEquals("msg-123", message.messageId());
        assertEquals("context-456", message.contextId());
        assertEquals(1, message.parts().size());
        assertTrue(message.parts().get(0) instanceof TextPart);
        assertEquals("Hello, world!", ((TextPart) message.parts().get(0)).text());
    }

    @Test
    public void testOnEventWithTaskStatusUpdateEventEvent() throws Exception {
        // Set up event handler
        AtomicReference<StreamingEventKind> receivedEvent = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> receivedEvent.set(event),
                error -> {}
        );

        // Parse the message event JSON
        String eventData = JsonStreamingMessages.STREAMING_STATUS_UPDATE_EVENT.substring(
                JsonStreamingMessages.STREAMING_STATUS_UPDATE_EVENT.indexOf("{"));

        // Call onEvent method
        listener.onMessage(new ServerSentEvent(eventData), null);

        // Verify the event was processed correctly
        assertNotNull(receivedEvent.get());
        assertTrue(receivedEvent.get() instanceof TaskStatusUpdateEvent);
        TaskStatusUpdateEvent taskStatusUpdateEvent = (TaskStatusUpdateEvent) receivedEvent.get();
        assertEquals("1", taskStatusUpdateEvent.taskId());
        assertEquals("2", taskStatusUpdateEvent.contextId());
        assertFalse(taskStatusUpdateEvent.isFinal());
        assertEquals(TaskState.TASK_STATE_SUBMITTED, taskStatusUpdateEvent.status().state());
    }

    @Test
    public void testOnEventWithTaskArtifactUpdateEventEvent() throws Exception {
        // Set up event handler
        AtomicReference<StreamingEventKind> receivedEvent = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> receivedEvent.set(event),
                error -> {}
        );

        // Parse the message event JSON
        String eventData = JsonStreamingMessages.STREAMING_ARTIFACT_UPDATE_EVENT.substring(
                JsonStreamingMessages.STREAMING_ARTIFACT_UPDATE_EVENT.indexOf("{"));

        // Call onEvent method
        listener.onMessage(new ServerSentEvent(eventData), null);

        // Verify the event was processed correctly
        assertNotNull(receivedEvent.get());
        assertTrue(receivedEvent.get() instanceof TaskArtifactUpdateEvent);

        TaskArtifactUpdateEvent taskArtifactUpdateEvent = (TaskArtifactUpdateEvent) receivedEvent.get();
        assertEquals("1", taskArtifactUpdateEvent.taskId());
        assertEquals("2", taskArtifactUpdateEvent.contextId());
        assertFalse(taskArtifactUpdateEvent.append());
        assertTrue(taskArtifactUpdateEvent.lastChunk());
        Artifact artifact = taskArtifactUpdateEvent.artifact();
        assertEquals("artifact-1", artifact.artifactId());
        assertEquals(1, artifact.parts().size());
        assertTrue(artifact.parts().get(0) instanceof TextPart);
        assertEquals("Why did the chicken cross the road? To get to the other side!", ((TextPart) artifact.parts().get(0)).text());
    }

    @Test
    public void testOnEventWithError() throws Exception {
        // Set up event handler
        AtomicReference<Throwable> receivedError = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> {},
                error -> receivedError.set(error)
        );

        // Parse the error event JSON
        String eventData = JsonStreamingMessages.STREAMING_ERROR_EVENT.substring(
                JsonStreamingMessages.STREAMING_ERROR_EVENT.indexOf("{"));
        
        // Call onEvent method
        listener.onMessage(new ServerSentEvent(eventData), null);

        // Verify the error was processed correctly
        assertNotNull(receivedError.get());
        assertInstanceOf(A2AError.class, receivedError.get());
        A2AError jsonrpcError = (A2AError) receivedError.get();
        assertEquals(-32602, jsonrpcError.getCode());
        assertEquals("Invalid parameters", jsonrpcError.getMessage());
        assertEquals("Missing required field", jsonrpcError.getDetails().get("info"));
    }

    @Test
    public void testOnFailure() {
        AtomicBoolean failureHandlerCalled = new AtomicBoolean(false);
        SSEEventListener listener = new SSEEventListener(
                event -> {},
                error -> failureHandlerCalled.set(true)
        );

        // Simulate a failure
        CancelCapturingFuture future = new CancelCapturingFuture();
        listener.onError(new RuntimeException("Test exception"), future);

        // Verify the failure handler was called
        assertTrue(failureHandlerCalled.get());
        // Verify it got cancelled
        assertTrue(future.cancelHandlerCalled);
    }

    @Test
    public void versionedReaderKeepsKnownEventsAndMarksFutureVariantsWithoutInventingEvents() {
        TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
        observation.markReady();
        List<TransportFrame> frames = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        observation.frames().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(TransportFrame frame) { frames.add(frame); }
            @Override public void onError(Throwable throwable) { failure.set(throwable); }
            @Override public void onComplete() { }
        });
        SSEEventListener listener = new SSEEventListener("1.1", "1", observation);

        listener.onMessage(event("task", """
                {"id":"task-1","contextId":"context-1","status":{"state":"TASK_STATE_WORKING"}}
                """), null);
        listener.onMessage(event("messageUpdate", """
                {"taskId":"task-1","contextId":"context-1","message":{"role":"ROLE_AGENT","messageId":"m1","contextId":"context-1","parts":[{"text":"update"}]},"generation":"2"}
                """), null);
        listener.onMessage(event("futureEvent", "{\"value\":\"ignored\"}"), null);
        listener.onMessage(event("statusUpdate", """
                {"taskId":"task-1","contextId":"context-1","status":{"state":"TASK_STATE_SUBMITTED"}}
                """), null);
        listener.onMessage(event("artifactUpdate", """
                {"taskId":"task-1","contextId":"context-1","artifact":{"artifactId":"a1","parts":[{"text":"artifact"}]},"append":false,"lastChunk":false}
                """), null);
        listener.onComplete();

        assertNull(failure.get());
        assertEquals(5, frames.size());
        assertInstanceOf(Task.class, frames.get(0).event());
        assertInstanceOf(org.a2aproject.sdk.spec.TaskMessageUpdateEvent.class, frames.get(1).event());
        assertNull(frames.get(2).event());
        assertTrue(frames.get(2).unknownSkipped());
        assertTrue(frames.get(3).event() instanceof TaskStatusUpdateEvent);
        assertTrue(frames.get(4).event() instanceof TaskArtifactUpdateEvent);
    }

    @Test
    public void versionedReaderRejectsMalformedKnownPayloadAndValidatesJsonRpcEnvelope() {
        TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
        observation.markReady();
        AtomicInteger errorCount = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        observation.frames().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(TransportFrame frame) { }
            @Override public void onError(Throwable throwable) { errorCount.incrementAndGet(); failure.set(throwable); }
            @Override public void onComplete() { }
        });
        SSEEventListener listener = new SSEEventListener("1.1", "1", observation);

        listener.onMessage(new ServerSentEvent("""
                {"jsonrpc":"2.0","id":"1","result":{"messageUpdate":{"taskId":"task-1","contextId":"context-1"}}}
                """), null);
        listener.onComplete();

        assertEquals(1, errorCount.get());
        assertNotNull(failure.get());
    }

    @Test
    public void versionedReaderRejectsMissingResponseIdAndJsonRpcErrorEnvelopes() {
        for (String envelope : List.of(
                "{\"jsonrpc\":\"2.0\",\"result\":{\"futureEvent\":{}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"result\":{\"futureEvent\":{}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32000,\"message\":\"rejected\"}}")) {
            TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
            AtomicInteger errorCount = new AtomicInteger();
            observation.frames().subscribe(new Flow.Subscriber<>() {
                @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                @Override public void onNext(TransportFrame frame) { }
                @Override public void onError(Throwable throwable) { errorCount.incrementAndGet(); }
                @Override public void onComplete() { }
            });
            SSEEventListener listener = new SSEEventListener("1.1", "1", observation);

            listener.onMessage(new ServerSentEvent(envelope), null);

            assertEquals(TransportEnd.Kind.FAILED, observation.completion().toCompletableFuture().join().kind());
            assertEquals(1, errorCount.get());
        }
    }

    @Test
    public void selectedReaderCorrelatesIdsBeforeKnownFramesLocalRejectionsAndRpcErrors() {
        String knownResult = "\"result\":{\"task\":{\"id\":\"task-1\",\"contextId\":\"context-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}";
        String localRejection = "\"result\":{\"error\":{\"code\":\"GENERATION_MISMATCH\",\"message\":\"stale\"}}";
        String rpcError = "\"error\":{\"code\":-32010,\"message\":\"generation mismatch\"}";
        List<String> payloads = List.of(knownResult, localRejection, rpcError);
        List<String> invalidIdEnvelopes = List.of(
                "\"jsonrpc\":\"2.0\",\"id\":\"another-request\",",
                "\"jsonrpc\":\"2.0\",",
                "\"jsonrpc\":\"2.0\",\"id\":null,",
                "\"jsonrpc\":\"2.0\",\"id\":{},",
                "\"jsonrpc\":\"2.0\",\"id\":true,",
                "\"jsonrpc\":\"2.0\",\"id\":[],",
                "\"jsonrpc\":\"2.0\",\"id\":17,");
        String matchingTask = "{\"jsonrpc\":\"2.0\",\"id\":\"expected-request\",\"result\":{\"task\":{\"id\":\"task-after\",\"contextId\":\"context-after\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}}";

        for (String payload : payloads) {
            for (String invalidId : invalidIdEnvelopes) {
                TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
                observation.markReady();
                List<TransportFrame> frames = new ArrayList<>();
                AtomicInteger errors = new AtomicInteger();
                AtomicInteger completions = new AtomicInteger();
                observation.frames().subscribe(new Flow.Subscriber<>() {
                    @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                    @Override public void onNext(TransportFrame frame) { frames.add(frame); }
                    @Override public void onError(Throwable throwable) { errors.incrementAndGet(); }
                    @Override public void onComplete() { completions.incrementAndGet(); }
                });
                SSEEventListener listener = new SSEEventListener("1.1", "expected-request", observation);
                listener.onMessage(new ServerSentEvent("{" + invalidId + payload + "}"), null);
                listener.onMessage(new ServerSentEvent(matchingTask), null);
                listener.onComplete();

                assertEquals(TransportEnd.Kind.FAILED, observation.completion().toCompletableFuture().join().kind());
                assertTrue(frames.isEmpty(), "invalid response ID must prevent every frame category");
                assertEquals(1, errors.get(), "invalid response ID must signal one terminal error");
                assertEquals(0, completions.get());
            }
        }
    }

    @Test
    public void selectedReaderPreservesMatchingStreamErrorAndJsonRpcGenerationMismatch() {
        TransportObservation rejectionObservation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
        rejectionObservation.markReady();
        List<TransportFrame> frames = new ArrayList<>();
        rejectionObservation.frames().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(TransportFrame frame) { frames.add(frame); }
            @Override public void onError(Throwable throwable) { }
            @Override public void onComplete() { }
        });
        SSEEventListener rejectionListener = new SSEEventListener("1.1", "expected-request", rejectionObservation);
        rejectionListener.onMessage(new ServerSentEvent("""
                {"jsonrpc":"2.0","id":"expected-request","result":{"error":{"code":"GENERATION_MISMATCH","message":"stale"}}}
                """), null);
        rejectionListener.onComplete();
        assertEquals(1, frames.size());
        assertNotNull(frames.get(0).streamError());
        assertEquals(TransportEnd.Kind.COMPLETED, rejectionObservation.completion().toCompletableFuture().join().kind());

        TransportObservation errorObservation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
        AtomicReference<Throwable> terminalError = new AtomicReference<>();
        errorObservation.frames().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(TransportFrame frame) { }
            @Override public void onError(Throwable throwable) { terminalError.set(throwable); }
            @Override public void onComplete() { }
        });
        SSEEventListener errorListener = new SSEEventListener("1.1", "expected-request", errorObservation);
        errorListener.onMessage(new ServerSentEvent("""
                {"jsonrpc":"2.0","id":"expected-request","error":{"code":-32010,"message":"generation mismatch"}}
                """), null);

        assertInstanceOf(org.a2aproject.sdk.spec.TaskGenerationMismatchError.class, terminalError.get());
        assertEquals(TransportEnd.Kind.FAILED, errorObservation.completion().toCompletableFuture().join().kind());
    }

    private static ServerSentEvent event(String variant, String payload) {
        return new ServerSentEvent("""
                {"jsonrpc":"2.0","id":"1","result":{"%s":%s}}
                """.formatted(variant, payload.trim()));
    }

    @Test
    public void testFinalTaskStatusUpdateEventCancels() {
        TaskStatus completedStatus = new TaskStatus(TaskState.TASK_STATE_COMPLETED);
        TaskStatusUpdateEvent tsue = new TaskStatusUpdateEvent(
                "1234",
                completedStatus,
                "xyz",
                null
        );

        // Set up event handler
        AtomicReference<StreamingEventKind> receivedEvent = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> receivedEvent.set(event),
                error -> {}
        );

        // Parse the message event JSON
        String eventData = JsonStreamingMessages.STREAMING_STATUS_UPDATE_EVENT_FINAL.substring(
                JsonStreamingMessages.STREAMING_STATUS_UPDATE_EVENT_FINAL.indexOf("{"));

        // Call onMessage with a cancellable future
        CancelCapturingFuture future = new CancelCapturingFuture();
        listener.onMessage(new ServerSentEvent(eventData), future);

        // Verify the event was received and processed
        assertNotNull(receivedEvent.get());
        assertTrue(receivedEvent.get() instanceof TaskStatusUpdateEvent);
        TaskStatusUpdateEvent received = (TaskStatusUpdateEvent) receivedEvent.get();
        assertTrue(received.isFinal());

        // Verify the future was cancelled (auto-close on final event)
        assertTrue(future.cancelHandlerCalled);
    }

    @Test
    public void testOnEventWithFinalTaskStatusUpdateEventEventCancels() throws Exception {
        // Set up event handler
        AtomicReference<StreamingEventKind> receivedEvent = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> receivedEvent.set(event),
                error -> {}
        );

        // Parse the message event JSON
        String eventData = JsonStreamingMessages.STREAMING_STATUS_UPDATE_EVENT_FINAL.substring(
                JsonStreamingMessages.STREAMING_STATUS_UPDATE_EVENT_FINAL.indexOf("{"));

        // Call onEvent method
        CancelCapturingFuture future = new CancelCapturingFuture();
        listener.onMessage(new ServerSentEvent(eventData), future);

        // Verify the event was processed correctly
        assertNotNull(receivedEvent.get());
        assertTrue(receivedEvent.get() instanceof TaskStatusUpdateEvent);
        TaskStatusUpdateEvent taskStatusUpdateEvent = (TaskStatusUpdateEvent) receivedEvent.get();
        assertEquals("1", taskStatusUpdateEvent.taskId());
        assertEquals("2", taskStatusUpdateEvent.contextId());
        assertTrue(taskStatusUpdateEvent.isFinal());
        assertEquals(TaskState.TASK_STATE_COMPLETED, taskStatusUpdateEvent.status().state());

        assertTrue(future.cancelHandlerCalled);
    }



    @Test
    public void testOnCompleteThenOnErrorDeliversSingleTerminalCallback() {
        AtomicInteger terminalCount = new AtomicInteger(0);
        AtomicReference<Throwable> lastArg = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> {},
                error -> { terminalCount.incrementAndGet(); lastArg.set(error); });

        listener.onComplete();
        listener.onError(new RuntimeException("late error"), new CancelCapturingFuture());

        assertEquals(1, terminalCount.get());
        assertNull(lastArg.get());
    }

    @Test
    public void testOnErrorThenOnCompleteDeliversSingleTerminalCallback() {
        AtomicInteger terminalCount = new AtomicInteger(0);
        AtomicReference<Throwable> lastArg = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> {},
                error -> { terminalCount.incrementAndGet(); lastArg.set(error); });

        RuntimeException boom = new RuntimeException("first error");
        listener.onError(boom, new CancelCapturingFuture());
        listener.onComplete();

        assertEquals(1, terminalCount.get());
        assertSame(boom, lastArg.get());
    }

    @Test
    public void testRepeatedOnCompleteDeliversSingleTerminalCallback() {
        AtomicInteger terminalCount = new AtomicInteger(0);
        AtomicReference<Throwable> lastArg = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> {},
                error -> { terminalCount.incrementAndGet(); lastArg.set(error); });

        listener.onComplete();
        listener.onComplete();
        listener.onComplete();

        assertEquals(1, terminalCount.get());
    }

    @Test
    public void testConcurrentTerminalSignalsDeliverExactlyOneCallback() throws Exception {
        AtomicInteger terminalCount = new AtomicInteger(0);
        AtomicReference<Throwable> lastArg = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> {},
                error -> { terminalCount.incrementAndGet(); lastArg.set(error); });

        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            final boolean complete = (i % 2 == 0);
            pool.submit(() -> {
                try {
                    start.await();
                    if (complete) {
                        listener.onComplete();
                    } else {
                        listener.onError(new RuntimeException("race"), new CancelCapturingFuture());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(1, terminalCount.get());
    }

    @Test
    public void testFinalEventThenOnCompleteDeliversSingleTerminalCallback() throws Exception {
        AtomicInteger terminalCount = new AtomicInteger(0);
        SSEEventListener listener = new SSEEventListener(
                event -> {},
                error -> terminalCount.incrementAndGet());

        String eventData = JsonStreamingMessages.STREAMING_STATUS_UPDATE_EVENT_FINAL.substring(
                JsonStreamingMessages.STREAMING_STATUS_UPDATE_EVENT_FINAL.indexOf("{"));
        CancelCapturingFuture future = new CancelCapturingFuture();
        listener.onMessage(new ServerSentEvent(eventData), future);
        listener.onComplete();

        assertTrue(future.cancelHandlerCalled);
        assertEquals(1, terminalCount.get());
    }

    @Test
    public void testFinalEventThenOnErrorDeliversNormalCompletionOnly() {
        AtomicInteger terminalCount = new AtomicInteger(0);
        AtomicReference<Throwable> terminalError = new AtomicReference<>();
        SSEEventListener listener = new SSEEventListener(
                event -> {},
                error -> {
                    terminalCount.incrementAndGet();
                    terminalError.set(error);
                });

        String eventData = JsonStreamingMessages.STREAMING_STATUS_UPDATE_EVENT_FINAL.substring(
                JsonStreamingMessages.STREAMING_STATUS_UPDATE_EVENT_FINAL.indexOf("{"));
        CancelCapturingFuture future = new CancelCapturingFuture();
        listener.onMessage(new ServerSentEvent(eventData), future);
        listener.onError(new RuntimeException("cancelled after final event"), future);

        assertTrue(future.cancelHandlerCalled);
        assertEquals(1, terminalCount.get());
        assertNull(terminalError.get());
    }

    private static class CancelCapturingFuture implements Future<Void> {
        private boolean cancelHandlerCalled;

        public CancelCapturingFuture() {
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelHandlerCalled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Void get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }
    }
}
