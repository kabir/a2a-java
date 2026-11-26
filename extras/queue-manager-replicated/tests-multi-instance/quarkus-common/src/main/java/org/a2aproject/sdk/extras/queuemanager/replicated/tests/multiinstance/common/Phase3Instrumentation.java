package org.a2aproject.sdk.extras.queuemanager.replicated.tests.multiinstance.common;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only internal barriers and an ordered trace for the Phase 3 authority tests. Barriers are armed and released
 * from the test through the node's admin routes; production code paths are never changed, only the fixture's own
 * decorators (replication strategy, lane store, HTTP client, agent executor) call into this class.
 */
public final class Phase3Instrumentation {

    /** Named barrier points. */
    public static final String PUBLISH = "publish";
    public static final String WEBHOOK_HTTP = "webhook-http";
    public static final String EXECUTOR = "executor";
    public static final String LANE_CLAIM = "lane-claim";

    private static final long MAX_HOLD_SECONDS = 180;

    private static final AtomicLong SEQ = new AtomicLong();
    private static final ConcurrentLinkedQueue<String> TRACE = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<String, Barrier> BARRIERS = new ConcurrentHashMap<>();

    private Phase3Instrumentation() {
    }

    private static final class Barrier {
        final String match;
        final CountDownLatch released = new CountDownLatch(1);
        final AtomicInteger waiting = new AtomicInteger();
        final AtomicInteger arrived = new AtomicInteger();

        Barrier(String match) {
            this.match = match;
        }
    }

    /** Appends one trace record (kind plus free-form detail) with a node-local monotonic sequence. */
    public static void trace(String kind, String detail) {
        String line = "{\"seq\":" + SEQ.incrementAndGet() + ",\"nanos\":" + System.nanoTime() + ",\"millis\":"
                + System.currentTimeMillis() + ",\"kind\":\"" + escape(kind) + "\",\"detail\":\"" + escape(detail) + "\"}";
        TRACE.add(line);
    }

    public static List<String> traceLines() {
        return List.copyOf(TRACE);
    }

    /** Arms a barrier; calls whose key contains {@code match} (empty matches everything) block until released. */
    public static void arm(String name, String match) {
        BARRIERS.put(name, new Barrier(match == null ? "" : match));
        trace("barrier-armed", name + " match=" + match);
    }

    /** Releases and disarms the barrier. */
    public static void release(String name) {
        Barrier barrier = BARRIERS.remove(name);
        if (barrier != null) {
            barrier.released.countDown();
            trace("barrier-released", name);
        }
    }

    public static int waiting(String name) {
        Barrier barrier = BARRIERS.get(name);
        return barrier == null ? 0 : barrier.waiting.get();
    }

    public static int arrived(String name) {
        Barrier barrier = BARRIERS.get(name);
        return barrier == null ? 0 : barrier.arrived.get();
    }

    /** Blocks the caller while the named barrier is armed and the key matches. */
    public static void pass(String name, String key) {
        Barrier barrier = BARRIERS.get(name);
        if (barrier == null || !key.contains(barrier.match)) {
            return;
        }
        barrier.arrived.incrementAndGet();
        barrier.waiting.incrementAndGet();
        trace("barrier-waiting", name + " " + key);
        try {
            if (!barrier.released.await(MAX_HOLD_SECONDS, TimeUnit.SECONDS)) {
                trace("barrier-timeout", name + " " + key);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            barrier.waiting.decrementAndGet();
            trace("barrier-passed", name + " " + key);
        }
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c < 0x20 ? ' ' : c);
            }
        }
        return out.toString();
    }
}
