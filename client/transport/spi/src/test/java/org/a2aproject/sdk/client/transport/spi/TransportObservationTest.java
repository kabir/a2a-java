package org.a2aproject.sdk.client.transport.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.spec.StreamError;
import org.junit.jupiter.api.Test;

class TransportObservationTest {
    @Test
    void versionedDefaultsRejectNewOperationsWithoutCallingTransport() {
        VersionedClientTransport transport = (VersionedClientTransport) Proxy.newProxyInstance(
                VersionedClientTransport.class.getClassLoader(), new Class<?>[] {VersionedClientTransport.class},
                (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    return switch (method.getName()) {
                        case "protocolVersion" -> "1.1";
                        case "capabilities" -> java.util.Set.of(ClientFeature.ORDINARY);
                        default -> null;
                    };
                });
        var params = org.a2aproject.sdk.spec.TaskQueryParams.builder().id("task").build();
        var listParams = org.a2aproject.sdk.spec.ListTasksParams.builder().build();
        var input = org.a2aproject.sdk.spec.SendInputParams.builder()
                .message(org.a2aproject.sdk.spec.Message.builder().role(org.a2aproject.sdk.spec.Message.Role.ROLE_USER)
                        .parts(List.of(new org.a2aproject.sdk.spec.TextPart("hello"))).build())
                .build();
        var options = new SpiCallOptions(Instant.now().plusSeconds(10), 2, 1024);
        var observationParams = new org.a2aproject.sdk.spec.ObserveTaskParams("task", null, null);
        assertThrows(UnsupportedOperationException.class, () -> transport.sendInput(input, options, null));
        assertThrows(UnsupportedOperationException.class, () -> transport.streamInput(input, options, null));
        assertThrows(UnsupportedOperationException.class, () -> transport.observe(observationParams, options, null));
        assertThrows(UnsupportedOperationException.class, () -> transport.getTaskAsync(params, options, null));
        assertThrows(UnsupportedOperationException.class, () -> transport.getTaskDecoded(params, null));
        assertThrows(UnsupportedOperationException.class, () -> transport.listTasksDecoded(listParams, null));
    }

    @Test
    void callsCompleteOrCancelOnlyOnce() {
        AtomicInteger cancellations = new AtomicInteger();
        TransportCall<String> cancelled = TransportCall.create(cancellations::incrementAndGet);
        cancelled.cancel();
        cancelled.cancel();
        assertEquals(1, cancellations.get());
        CompletionException cancellation = assertThrows(CompletionException.class,
                () -> cancelled.result().toCompletableFuture().join());
        assertInstanceOf(CancellationException.class, cancellation.getCause());

        TransportCall<String> completed = TransportCall.create(() -> { });
        assertTrue(completed.complete("ok"));
        assertFalse(completed.complete("again"));
        assertFalse(completed.fail(new IllegalStateException("late")));
        completed.cancel();
        assertEquals("ok", completed.result().toCompletableFuture().join());
    }

    @Test
    void observationSerializesDemandAndAllowsOnlyOneSubscriber() {
        TransportObservation observation = TransportObservation.create(
                new SpiCallOptions(Instant.now().plusSeconds(10), 2, 1024), () -> { });
        List<TransportFrame> received = new ArrayList<>();
        AtomicReference<Throwable> secondSubscriberFailure = new AtomicReference<>();
        observation.frames().subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            public void onSubscribe(Flow.Subscription value) {
                subscription = value;
                value.request(2);
            }
            public void onNext(TransportFrame frame) { received.add(frame); }
            public void onError(Throwable failure) { throw new AssertionError(failure); }
            public void onComplete() { }
        });
        observation.frames().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(1); }
            public void onNext(TransportFrame frame) { }
            public void onError(Throwable failure) { secondSubscriberFailure.set(failure); }
            public void onComplete() { }
        });
        TransportFrame frame = new TransportFrame(null, true, List.of(3L), null, 4);
        observation.markReady();
        assertTrue(observation.publish(frame));
        assertTrue(observation.publish(frame));
        assertEquals(List.of(frame, frame), received);
        assertInstanceOf(IllegalStateException.class, secondSubscriberFailure.get());
        assertTrue(observation.complete());
        assertEquals(TransportEnd.Kind.COMPLETED, observation.completion().toCompletableFuture().join().kind());
        assertFalse(observation.fail(new IllegalStateException("late")));
    }

    @Test
    void failedObservationDiscardsBufferedFramesAndSignalsErrorWithoutDemand() {
        TransportObservation observation = TransportObservation.create(
                new SpiCallOptions(Instant.now().plusSeconds(10), 2, 16), () -> { });
        AtomicInteger received = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        AtomicReference<Throwable> signalledFailure = new AtomicReference<>();
        observation.frames().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription value) { subscription.set(value); }
            public void onNext(TransportFrame frame) { received.incrementAndGet(); }
            public void onError(Throwable failure) {
                errors.incrementAndGet();
                signalledFailure.set(failure);
            }
            public void onComplete() { completed.incrementAndGet(); }
        });

        observation.markReady();
        assertTrue(observation.publish(new TransportFrame(null, true, List.of(), null, 4)));
        IllegalStateException failure = new IllegalStateException("read failed");
        assertTrue(observation.fail(failure));

        assertEquals(TransportEnd.Kind.FAILED, observation.completion().toCompletableFuture().join().kind());
        assertEquals(0, received.get());
        assertSame(failure, signalledFailure.get());
        assertEquals(1, errors.get());
        assertEquals(0, completed.get());
        assertFalse(observation.fail(new IllegalStateException("late failure")));
        subscription.get().request(1);
        assertEquals(0, received.get());
        assertEquals(1, errors.get());
        assertSame(failure, signalledFailure.get());
    }

    @Test
    void observationBoundsBufferedFramesAndCancellationDetachesOnce() {
        AtomicInteger overflowCancellations = new AtomicInteger();
        TransportObservation observation = TransportObservation.create(
                new SpiCallOptions(Instant.now().plusSeconds(10), 1, 8), overflowCancellations::incrementAndGet);
        observation.markReady();
        TransportFrame frame = new TransportFrame(null, true, List.of(), null, 4);
        assertTrue(observation.publish(frame));
        assertFalse(observation.publish(frame));
        TransportEnd end = observation.completion().toCompletableFuture().join();
        assertEquals(TransportEnd.Kind.FAILED, end.kind());
        assertInstanceOf(IllegalStateException.class, end.cause());
        assertEquals(1, overflowCancellations.get());
        observation.cancel();
        assertEquals(end, observation.completion().toCompletableFuture().join());
        assertEquals(1, overflowCancellations.get());

        AtomicInteger cancellations = new AtomicInteger();
        TransportObservation cancelled = TransportObservation.create(
                new SpiCallOptions(Instant.now().plusSeconds(10), 1, 8), cancellations::incrementAndGet);
        cancelled.cancel();
        cancelled.cancel();
        assertEquals(TransportEnd.Kind.CANCELLED, cancelled.completion().toCompletableFuture().join().kind());
        assertEquals(1, cancellations.get());
    }

    @Test
    void immutableEvidenceAndFramesDefensivelyCopyNotes() {
        List<Long> skipped = new ArrayList<>(List.of(1L, 2L));
        TaskDecodeEvidence evidence = new TaskDecodeEvidence("task", skipped, true);
        TransportResult<String> result = new TransportResult<>("value", List.of(evidence));
        skipped.clear();
        assertEquals(List.of(1L, 2L), result.taskEvidence().get(0).skippedTimelineGenerations());
        assertThrows(UnsupportedOperationException.class,
                () -> result.taskEvidence().add(evidence));
        org.a2aproject.sdk.spec.Task task = new org.a2aproject.sdk.spec.Task("task", "context",
                new org.a2aproject.sdk.spec.TaskStatus(org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING),
                List.of(), List.of(), java.util.Map.of());
        assertThrows(IllegalArgumentException.class, () -> new TransportResult<>(task, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TransportResult<>(task,
                List.of(new TaskDecodeEvidence("other", List.of(), false))));
        assertThrows(IllegalArgumentException.class, () -> new TransportResult<>(
                org.a2aproject.sdk.spec.Message.builder().role(org.a2aproject.sdk.spec.Message.Role.ROLE_USER)
                        .parts(List.of(new org.a2aproject.sdk.spec.TextPart("direct"))).build(), List.of(evidence)));
        new TransportResult<>(org.a2aproject.sdk.spec.Message.builder()
                .role(org.a2aproject.sdk.spec.Message.Role.ROLE_USER)
                .parts(List.of(new org.a2aproject.sdk.spec.TextPart("direct"))).build(), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> new TransportFrame(null, false, List.of(), null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TransportFrame(null, true, List.of(), new StreamError("task", "local", null, null, null), 0));
    }

    @Test
    void expiredOpeningDeadlineDetachesAndSignalsErrorWithoutDemand() throws Exception {
        AtomicInteger cancellations = new AtomicInteger();
        CountDownLatch detached = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        AtomicReference<Throwable> signalledFailure = new AtomicReference<>();
        TransportObservation observation = TransportObservation.create(
                new SpiCallOptions(Instant.now().minusSeconds(1), 1, 8), () -> {
                    cancellations.incrementAndGet();
                    detached.countDown();
                });
        observation.frames().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription subscription) { }
            public void onNext(TransportFrame frame) { received.incrementAndGet(); }
            public void onError(Throwable failure) { signalledFailure.set(failure); }
            public void onComplete() { completed.incrementAndGet(); }
        });

        TransportEnd end = observation.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertEquals(TransportEnd.Kind.DEADLINE, end.kind());
        assertInstanceOf(java.util.concurrent.TimeoutException.class, end.cause());
        assertSame(end.cause(), signalledFailure.get());
        assertEquals(0, received.get());
        assertEquals(0, completed.get());
        assertTrue(detached.await(2, TimeUnit.SECONDS));
        assertEquals(1, cancellations.get());
        assertThrows(CompletionException.class, () -> observation.ready().toCompletableFuture().join());
    }

    @Test
    void deadlineAndExplicitCancelRaceDetachesOnlyOnceAndKeepsDeadlineOutcome() throws Exception {
        AtomicInteger cancellations = new AtomicInteger();
        CountDownLatch detachStarted = new CountDownLatch(1);
        CountDownLatch allowDetachToFinish = new CountDownLatch(1);
        TransportObservation observation = TransportObservation.create(
                new SpiCallOptions(Instant.now().plusMillis(50), 1, 8), () -> {
                    cancellations.incrementAndGet();
                    detachStarted.countDown();
                    try {
                        if (!allowDetachToFinish.await(2, TimeUnit.SECONDS)) {
                            throw new AssertionError("test did not release the detach action");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                });

        assertTrue(detachStarted.await(2, TimeUnit.SECONDS));
        observation.cancel();
        TransportEnd end = observation.completion().toCompletableFuture().join();
        allowDetachToFinish.countDown();
        assertEquals(TransportEnd.Kind.DEADLINE, end.kind());
        assertEquals(end, observation.completion().toCompletableFuture().join());
        assertEquals(1, cancellations.get());
    }

    @Test
    void invalidDemandErrorDoesNotInterleaveWithAnOnNextSignal() {
        TransportObservation observation = TransportObservation.create(
                new SpiCallOptions(Instant.now().plusSeconds(10), 1, 8), () -> { });
        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger insideOnNext = new AtomicInteger();
        observation.frames().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription value) {
                subscription.set(value);
                value.request(1);
            }
            public void onNext(TransportFrame frame) {
                insideOnNext.incrementAndGet();
                subscription.get().request(0);
                assertEquals(0, failure.get() == null ? 0 : 1);
                insideOnNext.decrementAndGet();
            }
            public void onError(Throwable error) {
                assertEquals(0, insideOnNext.get());
                failure.set(error);
            }
            public void onComplete() { }
        });
        observation.markReady();
        observation.publish(new TransportFrame(null, true, List.of(), null, 1));
        assertInstanceOf(IllegalArgumentException.class, failure.get());
    }

    @Test
    void completionErrorAndCancelRaceNeverStartsCallbacksAfterTerminal() throws Exception {
        TransportObservation observation = TransportObservation.create(
                new SpiCallOptions(Instant.now().plusSeconds(10), 4, 1024), () -> { });
        List<String> callbacks = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger terminalCount = new AtomicInteger();
        AtomicInteger callbacksAfterTerminal = new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean terminalStarted = new java.util.concurrent.atomic.AtomicBoolean();
        CountDownLatch nextStarted = new CountDownLatch(1);
        CountDownLatch releaseNext = new CountDownLatch(1);
        CountDownLatch terminalDelivered = new CountDownLatch(1);
        observation.frames().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(TransportFrame frame) {
                if (terminalStarted.get()) {
                    callbacksAfterTerminal.incrementAndGet();
                }
                callbacks.add("next-start");
                nextStarted.countDown();
                try {
                    assertTrue(releaseNext.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                callbacks.add("next-end");
            }
            @Override public void onError(Throwable failure) {
                terminalStarted.set(true);
                terminalCount.incrementAndGet();
                callbacks.add("error");
                terminalDelivered.countDown();
            }
            @Override public void onComplete() {
                terminalStarted.set(true);
                terminalCount.incrementAndGet();
                callbacks.add("complete");
                terminalDelivered.countDown();
            }
        });
        observation.markReady();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            var delivery = executor.submit(() -> observation.publish(new TransportFrame(null, true, List.of(), null, 4)));
            assertTrue(nextStarted.await(5, TimeUnit.SECONDS));
            CountDownLatch start = new CountDownLatch(1);
            var completion = executor.submit(() -> {
                await(start);
                observation.complete();
            });
            var error = executor.submit(() -> {
                await(start);
                observation.fail(new IllegalStateException("racing failure"));
            });
            var cancel = executor.submit(() -> {
                await(start);
                observation.cancel();
            });
            start.countDown();
            completion.get(5, TimeUnit.SECONDS);
            error.get(5, TimeUnit.SECONDS);
            cancel.get(5, TimeUnit.SECONDS);
            releaseNext.countDown();
            delivery.get(5, TimeUnit.SECONDS);

            assertTrue(terminalDelivered.await(5, TimeUnit.SECONDS));
            assertEquals(1, terminalCount.get());
            assertEquals(0, callbacksAfterTerminal.get());
            assertEquals("next-start", callbacks.get(0));
            assertEquals("next-end", callbacks.get(1));
            assertTrue(List.of("complete", "error").contains(callbacks.get(2)));
        } finally {
            releaseNext.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("terminal race did not start");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

}
