package org.a2aproject.sdk.client.transport.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

/** A bounded, serialized, single-subscriber stream of transport frames. */
public final class TransportObservation implements Flow.Publisher<TransportFrame> {
    private static final ScheduledExecutorService DEADLINE_TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "a2a-transport-observation-deadline");
        thread.setDaemon(true);
        return thread;
    });

    private final Object lock = new Object();
    private final SpiCallOptions options;
    private final Runnable cancelAction;
    private final AtomicBoolean readDetached = new AtomicBoolean();
    private final Queue<TransportFrame> pending = new ArrayDeque<>();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final CompletableFuture<TransportEnd> completion = new CompletableFuture<>();
    private final ScheduledFuture<?> openingTimer;
    private Flow.@Nullable Subscriber<? super TransportFrame> subscriber;
    private long requested;
    private long pendingBytes;
    private @Nullable TransportEnd end;
    private @Nullable Throwable subscriberFailure;
    private boolean subscribed;
    private boolean draining;
    private boolean terminalDelivered;

    private TransportObservation(SpiCallOptions options, Runnable cancelAction) {
        this.options = Objects.requireNonNull(options, "options");
        this.cancelAction = Objects.requireNonNull(cancelAction, "cancelAction");
        long delayMillis;
        try {
            delayMillis = Math.max(0, Duration.between(Instant.now(), options.openingDeadline()).toMillis());
        } catch (ArithmeticException overflow) {
            delayMillis = Long.MAX_VALUE;
        }
        this.openingTimer = DEADLINE_TIMER.schedule(this::expireOpening, delayMillis, TimeUnit.MILLISECONDS);
    }

    /** Creates a pending observation. Cancellation detaches the underlying read only. */
    public static TransportObservation create(SpiCallOptions options, Runnable cancelAction) {
        return new TransportObservation(options, cancelAction);
    }

    /** Returns this one-subscriber publisher. */
    public Flow.Publisher<TransportFrame> frames() {
        return this;
    }

    /** Completes when the transport has opened, or exceptionally if opening fails. */
    public CompletionStage<Void> ready() {
        return ready.minimalCompletionStage();
    }

    /** Completes exactly once with the observation's terminal outcome. */
    public CompletionStage<TransportEnd> completion() {
        return completion.minimalCompletionStage();
    }

    /** Marks the connection open. */
    public boolean markReady() {
        synchronized (lock) {
            if (end != null || ready.isDone()) {
                return false;
            }
            ready.complete(null);
        }
        openingTimer.cancel(false);
        return true;
    }

    /** Adds a frame unless the finite local buffer would be exceeded. */
    public boolean publish(TransportFrame frame) {
        Objects.requireNonNull(frame, "frame");
        boolean notReady;
        boolean overflow = false;
        synchronized (lock) {
            if (end != null) {
                return false;
            }
            notReady = !ready.isDone();
            if (!notReady) {
                overflow = pending.size() >= options.maxBufferedFrames()
                        || frame.encodedSizeBytes() > options.maxBufferedBytes() - pendingBytes;
                if (!overflow) {
                    pending.add(frame);
                    pendingBytes += frame.encodedSizeBytes();
                }
            }
        }
        if (notReady) {
            fail(new IllegalStateException("Cannot publish before the observation is ready"));
            return false;
        }
        if (overflow) {
            if (fail(new IllegalStateException("Observation buffer limit exceeded"))) {
                detachReadOnce();
            }
            return false;
        }
        drain();
        return true;
    }

    /** Completes the observation once. */
    public boolean complete() {
        return finish(TransportEnd.completed());
    }

    /** Fails the observation once. */
    public boolean fail(Throwable failure) {
        return finish(TransportEnd.failed(Objects.requireNonNull(failure, "failure")));
    }

    /** Cancels the local read and completes the observation once. */
    public void cancel() {
        if (finish(TransportEnd.cancelled())) {
            detachReadOnce();
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super TransportFrame> next) {
        Objects.requireNonNull(next, "subscriber");
        boolean reject;
        synchronized (lock) {
            reject = subscribed;
            if (!reject) {
                subscribed = true;
                subscriber = next;
            }
        }
        if (reject) {
            next.onSubscribe(new Flow.Subscription() {
                public void request(long n) { }
                public void cancel() { }
            });
            next.onError(new IllegalStateException("TransportObservation supports one subscriber"));
            return;
        }
        next.onSubscribe(new ObservationSubscription());
        drain();
    }

    private boolean finish(TransportEnd terminal) {
        boolean accepted;
        synchronized (lock) {
            accepted = end == null;
            if (accepted) {
                end = terminal;
                if (terminal.kind() == TransportEnd.Kind.CANCELLED
                        || terminal.kind() == TransportEnd.Kind.FAILED
                        || terminal.kind() == TransportEnd.Kind.DEADLINE) {
                    pending.clear();
                    pendingBytes = 0;
                }
                if (!ready.isDone()) {
                    Throwable openingFailure = terminal.cause() != null ? terminal.cause()
                            : new IllegalStateException("Observation ended before opening");
                    ready.completeExceptionally(openingFailure);
                }
            }
        }
        if (accepted) {
            openingTimer.cancel(false);
            completion.complete(terminal);
            drain();
        }
        return accepted;
    }

    private void expireOpening() {
        TimeoutException timeout = new TimeoutException("Transport observation opening deadline exceeded");
        if (finish(TransportEnd.deadline(timeout))) {
            detachReadOnce();
        }
    }

    private void detachReadOnce() {
        if (readDetached.compareAndSet(false, true)) {
            cancelAction.run();
        }
    }

    private void drain() {
        synchronized (lock) {
            if (draining || subscriber == null) {
                return;
            }
            draining = true;
        }
        while (true) {
            Flow.Subscriber<? super TransportFrame> target;
            @Nullable TransportFrame frame = null;
            @Nullable TransportEnd terminal = null;
            @Nullable Throwable protocolFailure = null;
            synchronized (lock) {
                target = subscriber;
                if (target == null) {
                    draining = false;
                    return;
                }
                if (subscriberFailure != null) {
                    protocolFailure = subscriberFailure;
                    subscriberFailure = null;
                } else if (requested > 0 && !pending.isEmpty()) {
                    frame = pending.remove();
                    pendingBytes -= frame.encodedSizeBytes();
                    requested--;
                } else if (pending.isEmpty() && end != null && !terminalDelivered) {
                    terminal = end;
                    terminalDelivered = true;
                } else {
                    draining = false;
                    return;
                }
            }
            try {
                if (protocolFailure != null) {
                    target.onError(protocolFailure);
                    synchronized (lock) {
                        subscriber = null;
                        draining = false;
                    }
                    return;
                } else if (frame != null) {
                    target.onNext(frame);
                } else if (terminal != null) {
                    if (terminal.kind() == TransportEnd.Kind.FAILED || terminal.kind() == TransportEnd.Kind.DEADLINE) {
                        target.onError(terminal.cause() != null ? terminal.cause()
                                : new IllegalStateException("Transport observation failed"));
                    } else {
                        target.onComplete();
                    }
                    synchronized (lock) {
                        subscriber = null;
                        draining = false;
                    }
                    return;
                }
            } catch (RuntimeException subscriberFailure) {
                synchronized (lock) {
                    subscriber = null;
                    draining = false;
                }
                cancel();
                return;
            }
        }
    }

    private final class ObservationSubscription implements Flow.Subscription {
        private boolean cancelled;

        @Override
        public void request(long n) {
            if (n <= 0) {
                synchronized (lock) {
                    if (cancelled) {
                        return;
                    }
                    cancelled = true;
                    subscriberFailure = new IllegalArgumentException("Flow demand must be positive");
                }
                TransportObservation.this.cancel();
                drain();
                return;
            }
            synchronized (lock) {
                if (cancelled) {
                    return;
                }
                requested = requested > Long.MAX_VALUE - n ? Long.MAX_VALUE : requested + n;
            }
            drain();
        }

        @Override
        public void cancel() {
            synchronized (lock) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                subscriber = null;
            }
            TransportObservation.this.cancel();
        }
    }
}
