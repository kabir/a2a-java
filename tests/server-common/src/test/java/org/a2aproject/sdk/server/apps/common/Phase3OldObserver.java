package org.a2aproject.sdk.server.apps.common;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * A reactive-streams subscriber of an older-version (1.0 or 0.3) observation that requests exactly one item at a
 * time. A stream that drops items the old version must not see only makes progress if the filtering publisher
 * replenishes the downstream demand for every dropped item, so this subscriber is the finite-demand oracle.
 * Frames are exposed through a version-neutral shape so the same scenario assertions serve both versions.
 *
 * @param <T> the version-specific stream item
 */
public final class Phase3OldObserver<T> implements Flow.Subscriber<T>, AutoCloseable {

    /** Version-neutral view of one delivered older-version frame. */
    public record Frame(String kind, long generation, @Nullable String artifactId, @Nullable String state,
            List<String> taskArtifactIds) {
    }

    /** Version-neutral view of an older-version task read. */
    public record TaskRead(String state, boolean finalState, List<String> artifactIds, List<String> historyMessageIds) {
    }

    private final Function<T, Frame> mapper;
    private final List<Frame> frames = new CopyOnWriteArrayList<>();
    private final AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final CountDownLatch completed = new CountDownLatch(1);
    private volatile Flow.@Nullable Subscription subscription;

    public Phase3OldObserver(Function<T, Frame> mapper) {
        this.mapper = mapper;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        requests.incrementAndGet();
        subscription.request(1);
    }

    @Override
    public void onNext(T item) {
        frames.add(mapper.apply(item));
        requests.incrementAndGet();
        Flow.Subscription current = subscription;
        if (current != null) {
            current.request(1);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        failure.compareAndSet(null, throwable);
        completed.countDown();
    }

    @Override
    public void onComplete() {
        completed.countDown();
    }

    public List<Frame> frames() {
        return List.copyOf(frames);
    }

    public @Nullable Throwable failure() {
        return failure.get();
    }

    /** Number of single-item demand signals issued downstream: one per delivered frame plus the first. */
    public int demandSignals() {
        return requests.get();
    }

    public boolean awaitCompleted(int seconds) throws InterruptedException {
        return completed.await(seconds, TimeUnit.SECONDS);
    }

    public boolean awaitFrames(int count, int seconds) throws InterruptedException {
        for (int i = 0; i < seconds * 20 && frames.size() < count; i++) {
            Thread.sleep(50);
        }
        return frames.size() >= count;
    }

    @Override
    public void close() {
        Flow.Subscription current = subscription;
        if (current != null) {
            current.cancel();
        }
    }
}
