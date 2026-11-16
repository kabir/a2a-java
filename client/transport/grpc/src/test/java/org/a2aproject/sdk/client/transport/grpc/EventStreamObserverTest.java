package org.a2aproject.sdk.client.transport.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import io.grpc.stub.ClientCallStreamObserver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TransportEnd;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.grpc.StreamResponse;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class EventStreamObserverTest {

    @Test
    void onCompleted_callsErrorHandlerWithNull() {
        List<StreamingEventKind> events = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        Consumer<StreamingEventKind> eventHandler = events::add;
        Consumer<Throwable> errorHandler = errors::add;
        EventStreamObserver observer = new EventStreamObserver(eventHandler, errorHandler);

        observer.onCompleted();

        assertEquals(1, errors.size(), "errorHandler should have been called once");
        assertNull(errors.get(0), "errorHandler should be called with null to signal clean completion");
        assertEquals(0, events.size(), "eventHandler should not be called");
    }

    @Test
    void expiredDeadlineBeforeObserverInstallationCancelsLateCallAndTerminatesOnce() throws Exception {
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        TransportObservation observation = TransportObservation.create(
                new SpiCallOptions(Instant.now().minusSeconds(1), 2, 1024),
                () -> cancellationRequested.set(true));
        assertEquals(TransportEnd.Kind.DEADLINE,
                observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS).kind());

        AtomicInteger terminals = new AtomicInteger();
        observation.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(TransportFrame frame) {
                throw new AssertionError("No frame should be delivered after the opening deadline");
            }

            @Override
            public void onError(Throwable failure) {
                terminals.incrementAndGet();
            }

            @Override
            public void onComplete() {
                terminals.incrementAndGet();
            }
        });

        AtomicInteger cancellations = new AtomicInteger();
        EventStreamObserver observer = new EventStreamObserver("1.1", observation, cancellationRequested);
        observer.beforeStart(new ClientCallStreamObserver<>() {
            @Override
            public void cancel(String message, Throwable cause) {
                cancellations.incrementAndGet();
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setOnReadyHandler(Runnable handler) { }

            @Override
            public void disableAutoInboundFlowControl() { }

            @Override
            public void disableAutoRequestWithInitial(int request) { }

            @Override
            public void request(int count) { }

            @Override
            public void setMessageCompression(boolean enabled) { }

            @Override
            public void onNext(Object value) { }

            @Override
            public void onError(Throwable failure) { }

            @Override
            public void onCompleted() { }
        });

        assertTrue(cancellationRequested.get());
        assertEquals(1, cancellations.get(), "a late gRPC call must be cancelled before it can read");
        assertEquals(1, terminals.get(), "the deadline must produce exactly one terminal signal");
    }

    @Test
    void onCompleted_withNullErrorHandler_doesNotThrow() {
        Consumer<StreamingEventKind> eventHandler = e -> {};
        EventStreamObserver observer = new EventStreamObserver(eventHandler, null);

        observer.onCompleted();
    }

    @Test
    void versionedObserverSkipsFutureVariantsAndEmitsTypedMessageUpdates() {
        TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
        EventStreamObserver observer = new EventStreamObserver("1.1", observation);
        List<TransportFrame> frames = new ArrayList<>();
        observation.frames().subscribe(collecting(frames));

        observer.onNext(StreamResponse.newBuilder().setUnknownFields(UnknownFieldSet.newBuilder()
                .addField(100, UnknownFieldSet.Field.newBuilder()
                        .addLengthDelimited(ByteString.copyFromUtf8("future"))
                        .build())
                .build()).build());
        Message message = Message.builder().messageId("message-1").taskId("task-1").contextId("context-1")
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("hello"))).build();
        TaskMessageUpdateEvent update = new TaskMessageUpdateEvent("task-1", "context-1", message, null, 9);
        observer.onNext(ProtoUtils.ToProto.streamResponse(update));
        observer.onCompleted();

        assertEquals(2, frames.size());
        assertTrue(frames.get(0).unknownSkipped());
        assertNull(frames.get(0).event());
        assertInstanceOf(TaskMessageUpdateEvent.class, frames.get(1).event());
        TaskMessageUpdateEvent received = (TaskMessageUpdateEvent) frames.get(1).event();
        assertEquals(update.taskId(), received.taskId());
        assertEquals(update.contextId(), received.contextId());
        assertEquals(update.generation(), received.generation());
        assertEquals(update.message().messageId(), received.message().messageId());
        assertEquals(TransportEnd.Kind.COMPLETED, observation.completion().toCompletableFuture().join().kind());
    }

    @Test
    void versionedObserverKeepsLocalRejectionSeparateAndFailsMalformedKnownPayload() {
        TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
        EventStreamObserver observer = new EventStreamObserver("1.1", observation);
        List<TransportFrame> frames = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        observation.frames().subscribe(collecting(frames, failures));

        observer.onNext(StreamResponse.newBuilder().setError(org.a2aproject.sdk.grpc.StreamError.newBuilder()
                .setCode("INVALID_INPUT").setMessage("rejected")).build());
        assertTrue(frames.get(0).streamError() != null);
        assertFalse(observation.completion().toCompletableFuture().isDone());

        observer.onNext(StreamResponse.newBuilder().setStatusUpdate(
                org.a2aproject.sdk.grpc.TaskStatusUpdateEvent.getDefaultInstance()).build());
        assertEquals(1, failures.size());
        assertInstanceOf(org.a2aproject.sdk.spec.A2AClientException.class, failures.get(0));
        assertEquals(TransportEnd.Kind.FAILED, observation.completion().toCompletableFuture().join().kind());
    }

    private static Flow.Subscriber<TransportFrame> collecting(List<TransportFrame> frames) {
        return collecting(frames, new ArrayList<>());
    }

    private static Flow.Subscriber<TransportFrame> collecting(List<TransportFrame> frames,
            List<Throwable> failures) {
        return new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(TransportFrame frame) {
                frames.add(frame);
            }

            @Override
            public void onError(Throwable failure) {
                failures.add(failure);
            }

            @Override
            public void onComplete() {
            }
        };
    }
}
