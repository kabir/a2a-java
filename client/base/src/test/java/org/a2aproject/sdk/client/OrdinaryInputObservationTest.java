package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportCall;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamError;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class OrdinaryInputObservationTest {

    private static final String TASK = "task";
    private static final String CTX = "ctx";

    private static Task snapshot(long generation) {
        return Task.builder().id(TASK).contextId(CTX).status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .generation(generation).history(List.of()).artifacts(List.of()).timeline(List.of()).build();
    }

    private static TaskStatusUpdateEvent status(long generation) {
        return new TaskStatusUpdateEvent(TASK, new TaskStatus(TaskState.TASK_STATE_WORKING), CTX, null, generation);
    }

    private static Message message(String text) {
        return Message.builder().role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart(text))).build();
    }

    private static TransportFrame frame(StreamingEventKind event) {
        return new TransportFrame(event, false, List.of(), null, 1);
    }

    private static TransportObservation source(AtomicInteger detached) {
        return TransportObservation.create(SpiCallOptions.defaults(), detached::incrementAndGet);
    }

    private static OrdinaryInputObservation observe(TransportObservation source) {
        return OrdinaryInputObservation.stream(source, new ClientTaskReducer(), new GenerationReliability());
    }

    /** Subscriber that records signals and requests an initial demand. */
    private static final class Collector implements Flow.Subscriber<ObservationUpdate> {
        final List<ObservationUpdate> updates = new ArrayList<>();
        final AtomicInteger completions = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final long initialDemand;
        Flow.Subscription subscription;

        Collector(long initialDemand) {
            this.initialDemand = initialDemand;
        }

        @Override
        public synchronized void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (initialDemand > 0) {
                subscription.request(initialDemand);
            }
        }

        @Override
        public synchronized void onNext(ObservationUpdate item) {
            updates.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            error.set(throwable);
            errors.incrementAndGet();
        }

        @Override
        public void onComplete() {
            completions.incrementAndGet();
        }
    }

    private static ObservationEnd end(TaskObservation observation) throws Exception {
        return observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void streamingObservationIsReadyDeliversReducedUpdatesAndCompletesOnce() throws Exception {
        AtomicInteger detached = new AtomicInteger();
        TransportObservation source = source(detached);
        source.markReady();
        source.publish(frame(snapshot(1)));
        source.publish(frame(status(2)));
        source.complete();
        OrdinaryInputObservation observation = observe(source);
        Collector collector = new Collector(Long.MAX_VALUE);

        observation.updates().subscribe(collector);

        assertTrue(observation.ready().toCompletableFuture().isDone());
        assertEquals(2, collector.updates.size());
        assertEquals(1L, collector.updates.get(0).view().throughGeneration());
        assertEquals(2L, collector.updates.get(1).view().throughGeneration());
        assertEquals(1, collector.completions.get());
        assertEquals(0, collector.errors.get());
        ObservationEnd end = end(observation);
        assertEquals(ObservationEnd.Kind.COMPLETED, end.kind());
        assertEquals(2L, end.finalView().throughGeneration());
        assertEquals(2L, observation.currentView().throughGeneration());
        assertEquals(0, detached.get(), "normal completion does not detach through cancel");
    }

    @Test
    void unknownFrameProducesViewOnlyUpdateThatInvalidatesTheView() throws Exception {
        TransportObservation source = source(new AtomicInteger());
        source.markReady();
        source.publish(frame(snapshot(1)));
        source.publish(new TransportFrame(null, true, List.of(), null, 1));
        source.complete();
        OrdinaryInputObservation observation = observe(source);
        Collector collector = new Collector(Long.MAX_VALUE);

        observation.updates().subscribe(collector);

        assertEquals(2, collector.updates.size());
        assertEquals(ViewSync.SYNCHRONIZED, collector.updates.get(0).view().sync());
        assertNull(collector.updates.get(1).event());
        assertEquals(ViewSync.INCOMPLETE, collector.updates.get(1).view().sync());
        assertNull(OrdinaryInputObservation.toClientEvent(collector.updates.get(1)),
                "a view-only update never becomes a callback event");
    }

    @Test
    void cancelDetachesOnceCompletesCancelledAndFailsAnUnreadyObservation() throws Exception {
        AtomicInteger detached = new AtomicInteger();
        TransportObservation source = source(detached);
        OrdinaryInputObservation observation = observe(source);
        Collector collector = new Collector(Long.MAX_VALUE);
        observation.updates().subscribe(collector);

        observation.cancel();
        observation.cancel();
        observation.close();

        assertEquals(ObservationEnd.Kind.CANCELLED, end(observation).kind());
        assertEquals(1, detached.get());
        assertEquals(1, collector.completions.get());
        assertEquals(0, collector.errors.get());
        ExecutionException unready = assertThrows(ExecutionException.class,
                () -> observation.ready().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertInstanceOf(CancellationException.class, unready.getCause());
    }

    @Test
    void cancelFailAndCompleteRacesRecordOneTerminalAndOneDownstreamSignal() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            for (int round = 0; round < 100; round++) {
                AtomicInteger detached = new AtomicInteger();
                TransportObservation source = source(detached);
                source.markReady();
                OrdinaryInputObservation observation = observe(source);
                Collector collector = new Collector(Long.MAX_VALUE);
                observation.updates().subscribe(collector);
                CountDownLatch start = new CountDownLatch(1);
                List<CompletableFuture<?>> tasks = List.of(
                        CompletableFuture.runAsync(() -> {
                            await(start);
                            observation.cancel();
                        }, executor),
                        CompletableFuture.runAsync(() -> {
                            await(start);
                            source.fail(new IllegalStateException("boom"));
                        }, executor),
                        CompletableFuture.runAsync(() -> {
                            await(start);
                            source.complete();
                        }, executor));
                start.countDown();
                for (CompletableFuture<?> task : tasks) {
                    task.get(5, TimeUnit.SECONDS);
                }

                ObservationEnd end = end(observation);
                assertEquals(1, collector.completions.get() + collector.errors.get(), "exactly one downstream signal");
                assertEquals(end.kind() == ObservationEnd.Kind.FAILED ? 1 : 0, collector.errors.get());
                assertTrue(detached.get() <= 1);
                assertSame(end, end(observation));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void laterCancelNeverOverwritesAnAlreadyRecordedCompletionOrDropsQueuedUpdates() throws Exception {
        AtomicInteger detached = new AtomicInteger();
        TransportObservation source = source(detached);
        source.markReady();
        source.publish(frame(snapshot(1)));
        source.complete();
        OrdinaryInputObservation observation = observe(source);
        Collector collector = new Collector(0);
        observation.updates().subscribe(collector);
        assertEquals(ObservationEnd.Kind.COMPLETED, end(observation).kind());

        observation.cancel();
        source.fail(new IllegalStateException("late"));
        collector.subscription.request(1);

        assertEquals(ObservationEnd.Kind.COMPLETED, end(observation).kind());
        assertEquals(1, collector.updates.size(), "the queued update survives a late cancel");
        assertEquals(1, collector.completions.get());
        assertEquals(0, collector.errors.get());
        assertEquals(0, detached.get(), "a late cancel after the terminal does not detach again");
    }

    @Test
    void secondSubscriberIsRejectedAndNonPositiveDemandFailsTheObservation() throws Exception {
        TransportObservation source = source(new AtomicInteger());
        source.markReady();
        OrdinaryInputObservation observation = observe(source);
        Collector first = new Collector(1);
        Collector second = new Collector(1);
        observation.updates().subscribe(first);
        observation.updates().subscribe(second);

        assertInstanceOf(IllegalStateException.class, second.error.get());
        assertEquals(0, first.errors.get());

        first.subscription.request(0);

        ObservationEnd end = end(observation);
        assertEquals(ObservationEnd.Kind.FAILED, end.kind());
        assertInstanceOf(IllegalArgumentException.class, end.cause());
        assertEquals(1, first.errors.get());
    }

    @Test
    void demandIsHonouredOneUpdateAtATime() throws Exception {
        TransportObservation source = source(new AtomicInteger());
        source.markReady();
        source.publish(frame(snapshot(1)));
        source.publish(frame(status(2)));
        source.publish(frame(status(3)));
        source.complete();
        OrdinaryInputObservation observation = observe(source);
        Collector collector = new Collector(1);

        observation.updates().subscribe(collector);
        assertEquals(1, collector.updates.size());
        assertEquals(0, collector.completions.get());
        collector.subscription.request(1);
        assertEquals(2, collector.updates.size());
        collector.subscription.request(5);

        assertEquals(3, collector.updates.size());
        assertEquals(1, collector.completions.get());
    }

    @Test
    void localQueueAndUpstreamBuffersAreBoundedAndOverflowFailsTheObservation() throws Exception {
        AtomicInteger detached = new AtomicInteger();
        TransportObservation source = TransportObservation.create(
                new SpiCallOptions(Instant.now().plusSeconds(60), 4, SpiCallOptions.DEFAULT_MAX_BUFFERED_BYTES),
                detached::incrementAndGet);
        source.markReady();
        OrdinaryInputObservation observation = observe(source);

        // no downstream demand: 256 reduced updates queue locally, then 4 frames stay in the transport buffer
        int accepted = 0;
        for (int i = 0; i < 300; i++) {
            if (!source.publish(frame(message("m" + i)))) {
                break;
            }
            accepted++;
        }

        assertEquals(SpiCallOptions.DEFAULT_MAX_BUFFERED_FRAMES + 4, accepted);
        ObservationEnd end = end(observation);
        assertEquals(ObservationEnd.Kind.FAILED, end.kind());
        assertInstanceOf(IllegalStateException.class, end.cause());
        assertEquals(1, detached.get());
    }

    @Test
    void streamErrorFrameFailsTheObservationAndDetaches() throws Exception {
        AtomicInteger detached = new AtomicInteger();
        TransportObservation source = source(detached);
        source.markReady();
        OrdinaryInputObservation observation = observe(source);
        Collector collector = new Collector(Long.MAX_VALUE);
        observation.updates().subscribe(collector);

        source.publish(new TransportFrame(null, false, List.of(),
                new StreamError(TASK, "TASK_NOT_FOUND", "gone", null, null), 1));

        ObservationEnd end = end(observation);
        assertEquals(ObservationEnd.Kind.FAILED, end.kind());
        assertTrue(end.cause().getMessage().contains("TASK_NOT_FOUND"));
        assertEquals(1, collector.errors.get());
        assertEquals(1, detached.get());
    }

    @Test
    void unaryTaskResultIsOneUpdateThenCompleted() throws Exception {
        TransportCall<TransportResult<EventKind>> call = TransportCall.create(() -> { });
        Task task = snapshot(4);
        call.complete(new TransportResult<>(task, List.of(new TaskDecodeEvidence(TASK, List.of(), false))));
        OrdinaryInputObservation observation = OrdinaryInputObservation.unary(call, new ClientTaskReducer(),
                new GenerationReliability());
        Collector collector = new Collector(Long.MAX_VALUE);

        observation.updates().subscribe(collector);

        assertTrue(observation.ready().toCompletableFuture().isDone());
        assertEquals(1, collector.updates.size());
        assertSame(task, collector.updates.get(0).event());
        assertEquals(4L, collector.updates.get(0).view().throughGeneration());
        assertEquals(1, collector.completions.get());
        assertEquals(ObservationEnd.Kind.COMPLETED, end(observation).kind());
    }

    @Test
    void unaryDirectMessageHasNoTaskBaselineAndNoFabricatedTask() throws Exception {
        TransportCall<TransportResult<EventKind>> call = TransportCall.create(() -> { });
        Message reply = message("reply");
        call.complete(new TransportResult<>(reply, List.of()));
        OrdinaryInputObservation observation = OrdinaryInputObservation.unary(call, new ClientTaskReducer(),
                new GenerationReliability());
        Collector collector = new Collector(Long.MAX_VALUE);

        observation.updates().subscribe(collector);

        assertEquals(1, collector.updates.size());
        assertSame(reply, collector.updates.get(0).event());
        assertNull(collector.updates.get(0).view().task());
        assertEquals(ViewSync.NO_BASELINE, collector.updates.get(0).view().sync());
        assertNull(end(observation).finalView().task());
    }

    @Test
    void unaryFailureFailsObservationAndReadiness() throws Exception {
        TransportCall<TransportResult<EventKind>> call = TransportCall.create(() -> { });
        OrdinaryInputObservation observation = OrdinaryInputObservation.unary(call, new ClientTaskReducer(),
                new GenerationReliability());
        Collector collector = new Collector(Long.MAX_VALUE);
        observation.updates().subscribe(collector);
        IllegalStateException failure = new IllegalStateException("peer failed");

        call.fail(failure);

        ObservationEnd end = end(observation);
        assertEquals(ObservationEnd.Kind.FAILED, end.kind());
        assertSame(failure, end.cause());
        assertSame(failure, collector.error.get());
        assertTrue(observation.ready().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void unaryCancelCancelsTheCallOnceAndNeverTheTask() throws Exception {
        AtomicInteger cancelled = new AtomicInteger();
        TransportCall<TransportResult<EventKind>> call = TransportCall.create(cancelled::incrementAndGet);
        OrdinaryInputObservation observation = OrdinaryInputObservation.unary(call, new ClientTaskReducer(),
                new GenerationReliability());

        observation.cancel();
        observation.cancel();
        call.complete(new TransportResult<>(snapshot(3), List.of(new TaskDecodeEvidence(TASK, List.of(), false))));

        assertEquals(ObservationEnd.Kind.CANCELLED, end(observation).kind());
        assertEquals(1, cancelled.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
