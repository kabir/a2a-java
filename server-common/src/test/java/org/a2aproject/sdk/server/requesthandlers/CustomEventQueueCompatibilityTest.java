package org.a2aproject.sdk.server.requesthandlers;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.server.events.EventConsumer;
import org.a2aproject.sdk.server.events.EventQueue;
import org.a2aproject.sdk.server.events.EventQueueClosedException;
import org.a2aproject.sdk.server.events.EventQueueItem;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

class CustomEventQueueCompatibilityTest {

    @Test
    void publicFinalEventHooksStillDriveTheConsumerTimeout() throws Exception {
        LegacyHookQueue queue = new LegacyHookQueue();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        EventConsumer consumer = new EventConsumer(queue, executor);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();

        try {
            consumer.consumeAll().subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(EventQueueItem item) {
                }

                @Override
                public void onError(Throwable failure) {
                    streamFailure.set(failure);
                    completed.countDown();
                }

                @Override
                public void onComplete() {
                    completed.countDown();
                }
            });

            assertTrue(queue.finalEventCleared.await(5, TimeUnit.SECONDS),
                    "the consumer must time out through the existing public queue hooks");
        } finally {
            consumer.cancel();
            assertTrue(completed.await(2, TimeUnit.SECONDS), "the consumer should stop after cancellation");
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertNull(streamFailure.get());
    }

    /** Models an out-of-package implementation that only knows the established public hooks. */
    private static final class LegacyHookQueue extends EventQueue {
        private final AtomicBoolean awaitingFinalEvent = new AtomicBoolean(true);
        private final CountDownLatch finalEventCleared = new CountDownLatch(1);

        @Override
        public void awaitQueuePollerStart() {
        }

        @Override
        public void signalQueuePollerStarted() {
        }

        @Override
        public void enqueueItem(EventQueueItem item) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EventQueue tap() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable EventQueueItem dequeueEventItem(int waitMilliSeconds) throws EventQueueClosedException {
            try {
                Thread.sleep(waitMilliSeconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isAwaitingFinalEvent() {
            return awaitingFinalEvent.get();
        }

        @Override
        public void clearAwaitingFinalEvent() {
            awaitingFinalEvent.set(false);
            finalEventCleared.countDown();
        }

        @Override
        public void close() {
            doClose();
        }

        @Override
        public void close(boolean immediate) {
            doClose(immediate);
        }

        @Override
        public void close(boolean immediate, boolean notifyParent) {
            doClose(immediate);
        }
    }
}
