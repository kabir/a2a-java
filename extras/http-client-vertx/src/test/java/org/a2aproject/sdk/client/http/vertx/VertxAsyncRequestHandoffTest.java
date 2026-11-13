package org.a2aproject.sdk.client.http.vertx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VertxAsyncRequestHandoffTest {

    @Test
    void cancellationDuringRequestHandoffResetsTheActiveRequestExactlyOnce() throws Exception {
        VertxA2AHttpClient.AsyncRequestHandoff handoff = new VertxA2AHttpClient.AsyncRequestHandoff();
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch finishSend = new CountDownLatch(1);
        CountDownLatch cancellationStarted = new CountDownLatch(1);
        AtomicInteger resetCount = new AtomicInteger();
        AtomicInteger sendCount = new AtomicInteger();
        Thread requestCallback = new Thread(() -> handoff.onRequestReady(
                resetCount::incrementAndGet,
                () -> {
                    sendCount.incrementAndGet();
                    sendStarted.countDown();
                    await(finishSend);
                }));
        Thread cancellation = new Thread(() -> {
            cancellationStarted.countDown();
            handoff.cancel();
        });

        requestCallback.start();
        assertTrue(sendStarted.await(5, TimeUnit.SECONDS));
        cancellation.start();
        assertTrue(cancellationStarted.await(5, TimeUnit.SECONDS));
        assertTrue(waitUntilBlocked(cancellation), "cancellation should wait for the in-progress request handoff");
        finishSend.countDown();
        requestCallback.join(TimeUnit.SECONDS.toMillis(5));
        cancellation.join(TimeUnit.SECONDS.toMillis(5));

        assertFalse(requestCallback.isAlive());
        assertFalse(cancellation.isAlive());
        handoff.cancel();
        assertEquals(1, sendCount.get());
        assertEquals(1, resetCount.get());
    }

    @Test
    void requestArrivingAfterCancellationIsResetWithoutBeingSent() {
        VertxA2AHttpClient.AsyncRequestHandoff handoff = new VertxA2AHttpClient.AsyncRequestHandoff();
        AtomicInteger resetCount = new AtomicInteger();
        AtomicInteger sendCount = new AtomicInteger();

        handoff.cancel();
        handoff.onRequestReady(resetCount::incrementAndGet, sendCount::incrementAndGet);
        handoff.cancel();

        assertEquals(0, sendCount.get());
        assertEquals(1, resetCount.get());
    }

    private static boolean waitUntilBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.BLOCKED) {
                return true;
            }
            Thread.yield();
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the test handoff");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the test handoff", e);
        }
    }
}
