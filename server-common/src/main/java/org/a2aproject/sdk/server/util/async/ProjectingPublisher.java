package org.a2aproject.sdk.server.util.async;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/** A backpressure-preserving publisher that drops projected {@code null} values. */
public final class ProjectingPublisher {

    private ProjectingPublisher() {
    }

    public static <T, R> Flow.Publisher<R> mapNotNull(Flow.Publisher<T> source,
            Function<T, @Nullable R> projector) {
        return mapNotNullWithNullableProjector(source, projector::apply);
    }

    @FunctionalInterface
    public interface NullableProjector<T, R> {
        @Nullable R apply(T value);
    }

    public static <T, R> Flow.Publisher<R> mapNotNullWithNullableProjector(Flow.Publisher<T> source,
            NullableProjector<T, R> projector) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(projector, "projector");
        return downstream -> {
            ProjectingSubscriber<T, R> bridge = new ProjectingSubscriber<>(downstream, projector);
            try {
                source.subscribe(bridge);
            } catch (Throwable error) {
                bridge.onError(error);
            }
        };
    }

    private static final class ProjectingSubscriber<T, R> implements Flow.Subscriber<T>, Flow.Subscription {
        private final Flow.Subscriber<? super R> downstream;
        private final NullableProjector<T, R> projector;
        private final ConcurrentLinkedQueue<Signal<T>> signals = new ConcurrentLinkedQueue<>();
        private final AtomicInteger work = new AtomicInteger();
        private final AtomicLong demand = new AtomicLong();
        private final AtomicBoolean upstreamCancelled = new AtomicBoolean();
        private final AtomicBoolean downstreamSubscribed = new AtomicBoolean();
        private final Object stateLock = new Object();
        private final AtomicReference<Flow.@Nullable Subscription> upstream = new AtomicReference<>();
        private boolean upstreamOutstanding;
        private boolean cancelled;
        private boolean terminated;

        private ProjectingSubscriber(Flow.Subscriber<? super R> downstream,
                NullableProjector<T, R> projector) {
            this.downstream = downstream;
            this.projector = projector;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription, "subscription");
            if (!upstream.compareAndSet(null, subscription)) {
                subscription.cancel();
                return;
            }
            signalSubscription();
            drain();
        }

        @Override
        public void onNext(T item) {
            if (item == null) {
                onError(new NullPointerException("Flow publishers may not emit null"));
                return;
            }
            signals.offer(new Next<>(item));
            drain();
        }

        @Override
        public void onError(Throwable throwable) {
            if (downstreamSubscribed.compareAndSet(false, true)) {
                try {
                    downstream.onSubscribe(this);
                } catch (Throwable ignored) {
                    cancel();
                    return;
                }
            }
            signals.offer(new Failure<>(Objects.requireNonNull(throwable, "throwable")));
            drain();
        }

        @Override
        public void onComplete() {
            signals.offer(new Complete<>());
            drain();
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                signals.offer(new Failure<>(new IllegalArgumentException("request amount must be positive")));
                cancelUpstream();
                drain();
                return;
            }
            addDemand(n);
            drain();
        }

        @Override
        public void cancel() {
            synchronized (stateLock) {
                if (cancelled || terminated) {
                    return;
                }
                cancelled = true;
                signals.clear();
            }
            cancelUpstream();
        }

        private void signalSubscription() {
            if (!downstreamSubscribed.compareAndSet(false, true)) {
                return;
            }
            try {
                downstream.onSubscribe(this);
            } catch (Throwable error) {
                cancel();
            }
        }

        private void drain() {
            if (work.getAndIncrement() != 0) {
                return;
            }
            int missed = 1;
            do {
                while (true) {
                    Signal<T> signal = signals.poll();
                    if (signal != null) {
                        if (!handle(signal)) {
                            break;
                        }
                        continue;
                    }
                    Flow.Subscription subscription = null;
                    synchronized (stateLock) {
                        if (cancelled || terminated) {
                            break;
                        }
                        if (!upstreamOutstanding && demand.get() > 0) {
                            subscription = upstream.get();
                            if (subscription != null) {
                                upstreamOutstanding = true;
                            }
                        }
                    }
                    if (subscription == null) {
                        break;
                    }
                    // WIP serialization makes synchronous onNext/request cycles iterative.
                    subscription.request(1);
                }
                missed = work.addAndGet(-missed);
            } while (missed != 0);
        }

        private boolean handle(Signal<T> signal) {
            synchronized (stateLock) {
                if (cancelled || terminated) {
                    return true;
                }
            }
            if (signal instanceof Next<T> next) {
                synchronized (stateLock) {
                    upstreamOutstanding = false;
                }
                final @Nullable R projected;
                try {
                    projected = projector.apply(next.value());
                } catch (Throwable error) {
                    fail(error);
                    return false;
                }
                if (projected != null) {
                    if (!takeDemand()) {
                        fail(new IllegalStateException("upstream produced without downstream demand"));
                        return false;
                    }
                    try {
                        downstream.onNext(projected);
                    } catch (Throwable error) {
                        cancel();
                        return false;
                    }
                }
                return true;
            }
            if (signal instanceof Failure<T> failure) {
                synchronized (stateLock) {
                    terminated = true;
                }
                cancelUpstream();
                downstream.onError(failure.error());
                return false;
            }
            synchronized (stateLock) {
                terminated = true;
            }
            downstream.onComplete();
            return false;
        }

        private void fail(Throwable error) {
            synchronized (stateLock) {
                if (cancelled || terminated) {
                    return;
                }
                terminated = true;
            }
            cancelUpstream();
            downstream.onError(error);
        }

        private boolean takeDemand() {
            while (true) {
                long current = demand.get();
                if (current == 0) {
                    return false;
                }
                if (current == Long.MAX_VALUE || demand.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        private void addDemand(long n) {
            while (true) {
                long current = demand.get();
                long updated = current + n;
                if (updated < 0) {
                    updated = Long.MAX_VALUE;
                }
                if (demand.compareAndSet(current, updated)) {
                    return;
                }
            }
        }

        private void cancelUpstream() {
            Flow.Subscription subscription = upstream.get();
            if (subscription != null && upstreamCancelled.compareAndSet(false, true)) {
                subscription.cancel();
            }
        }
    }

    private interface Signal<T> {
    }

    private record Next<T>(T value) implements Signal<T> {
    }

    private record Failure<T>(Throwable error) implements Signal<T> {
    }

    private record Complete<T>() implements Signal<T> {
    }
}
