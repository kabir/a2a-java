package org.a2aproject.sdk.client;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TransportCall;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.UpdateEvent;
import org.jspecify.annotations.Nullable;

/**
 * Internal {@link TaskObservation} over one ordinary 1.1 interaction: a decoded unary result, a streaming input
 * observation, or a no-cursor subscription. Every frame is reduced through {@link ClientTaskReducer} and the
 * client's {@link GenerationReliability} before it is exposed.
 * <p>
 * Upstream demand is one frame at a time and only while the local queue has room, so a slow subscriber is bounded
 * by the transport's own finite buffers. The terminal outcome is recorded exactly once across cancel, failure and
 * normal completion races. Cancelling detaches only the local read; it never requests task cancellation.
 */
final class OrdinaryInputObservation implements TaskObservation {

    private static final int MAX_QUEUED_UPDATES = SpiCallOptions.DEFAULT_MAX_BUFFERED_FRAMES;

    private final ClientTaskReducer reducer;
    private final GenerationReliability reliability;
    private final Runnable detach;
    private final Object lock = new Object();
    private final Queue<ObservationUpdate> queue = new ArrayDeque<>();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final CompletableFuture<ObservationEnd> completion = new CompletableFuture<>();
    private final Flow.Publisher<ObservationUpdate> publisher = this::subscribeDownstream;

    private Flow.@Nullable Subscriber<? super ObservationUpdate> subscriber;
    private Flow.@Nullable Subscription upstream;
    private boolean subscribed;
    private boolean draining;
    private boolean terminalSignalled;
    private boolean wantMore;
    private long demand;
    private @Nullable ObservationEnd end;
    private @Nullable Throwable terminalFailure;

    private OrdinaryInputObservation(ClientTaskReducer reducer, GenerationReliability reliability, Runnable detach) {
        this.reducer = reducer;
        this.reliability = reliability;
        this.detach = detach;
    }

    /** Observes a streaming transport observation, taking one frame at a time. */
    static OrdinaryInputObservation stream(TransportObservation source, ClientTaskReducer reducer,
            GenerationReliability reliability) {
        OrdinaryInputObservation observation = new OrdinaryInputObservation(reducer, reliability, source::cancel);
        source.ready().whenComplete((ignored, failure) -> {
            if (failure == null) {
                observation.ready.complete(null);
            } else {
                observation.ready.completeExceptionally(unwrap(failure));
            }
        });
        source.frames().subscribe(observation.new UpstreamSubscriber(source));
        return observation;
    }

    /** Observes one decoded unary result as a single update followed by completion. */
    static OrdinaryInputObservation unary(TransportCall<TransportResult<EventKind>> call,
            ClientTaskReducer reducer, GenerationReliability reliability) {
        OrdinaryInputObservation observation = new OrdinaryInputObservation(reducer, reliability, call::cancel);
        call.result().whenComplete((result, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                observation.terminate(cause instanceof CancellationException
                        ? ObservationEnd.Kind.CANCELLED : ObservationEnd.Kind.FAILED, cause);
                return;
            }
            try {
                EventKind value = result.value();
                if (!(value instanceof StreamingEventKind event)) {
                    throw new A2AClientException("Unsupported input response event: " + value.getClass().getName());
                }
                TaskView view = reliability.reduceUnary(reducer, result);
                observation.ready.complete(null);
                observation.enqueue(new ObservationUpdate(event, view));
                observation.terminate(ObservationEnd.Kind.COMPLETED, null);
            } catch (Throwable decodeFailure) {
                observation.terminate(ObservationEnd.Kind.FAILED, decodeFailure);
            }
        });
        return observation;
    }

    @Override
    public Flow.Publisher<ObservationUpdate> updates() {
        return publisher;
    }

    @Override
    public CompletionStage<Void> ready() {
        return ready.minimalCompletionStage();
    }

    @Override
    public CompletionStage<ObservationEnd> completion() {
        return completion.minimalCompletionStage();
    }

    @Override
    public TaskView currentView() {
        return reducer.view();
    }

    @Override
    public void cancel() {
        if (terminate(ObservationEnd.Kind.CANCELLED, null)) {
            detach.run();
        }
    }

    @Override
    public void close() {
        cancel();
    }

    /** Maps a reduced update to the established callback event shapes; view-only updates map to nothing. */
    static @Nullable ClientEvent toClientEvent(ObservationUpdate update) {
        StreamingEventKind event = update.event();
        if (event == null) {
            return null;
        }
        return toClientEvent(event, update.view());
    }

    static ClientEvent toClientEvent(StreamingEventKind event, TaskView view) {
        if (event instanceof Message message) {
            return new MessageEvent(message);
        } else if (event instanceof Task task) {
            return new TaskEvent(task);
        }
        UpdateEvent update = (UpdateEvent) event;
        Task task = view.task();
        return new TaskUpdateEvent(task != null ? task : skeleton(event), update);
    }

    private static Task skeleton(StreamingEventKind event) {
        String taskId;
        String contextId;
        if (event instanceof TaskStatusUpdateEvent status) {
            taskId = status.taskId();
            contextId = status.contextId();
        } else if (event instanceof TaskArtifactUpdateEvent artifact) {
            taskId = artifact.taskId();
            contextId = artifact.contextId();
        } else {
            TaskMessageUpdateEvent message = (TaskMessageUpdateEvent) event;
            taskId = message.taskId();
            contextId = message.contextId();
        }
        return Task.builder().id(taskId).contextId(contextId)
                .status(new TaskStatus(TaskState.TASK_STATE_UNSPECIFIED)).build();
    }

    private boolean terminate(ObservationEnd.Kind kind, @Nullable Throwable cause) {
        ObservationEnd terminal;
        synchronized (lock) {
            if (end != null) {
                return false;
            }
            terminal = new ObservationEnd(kind, cause, reducer.view());
            end = terminal;
            terminalFailure = kind == ObservationEnd.Kind.FAILED || kind == ObservationEnd.Kind.DEADLINE ? cause : null;
            if (kind != ObservationEnd.Kind.COMPLETED) {
                queue.clear();
            }
        }
        if (!ready.isDone()) {
            ready.completeExceptionally(cause != null ? cause
                    : new CancellationException("Observation ended before it was ready"));
        }
        completion.complete(terminal);
        drain();
        return true;
    }

    private void fail(Throwable failure) {
        if (terminate(ObservationEnd.Kind.FAILED, failure)) {
            detach.run();
        }
    }

    private void enqueue(ObservationUpdate update) {
        synchronized (lock) {
            if (end != null) {
                return;
            }
            queue.add(update);
        }
        drain();
    }

    private void requestMoreIfRoom() {
        Flow.Subscription source;
        synchronized (lock) {
            source = upstream;
            if (source == null || !wantMore || end != null || queue.size() >= MAX_QUEUED_UPDATES) {
                return;
            }
            wantMore = false;
        }
        source.request(1);
    }

    private void subscribeDownstream(Flow.Subscriber<? super ObservationUpdate> next) {
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
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
            next.onError(new IllegalStateException("TaskObservation supports one subscriber"));
            return;
        }
        next.onSubscribe(new DownstreamSubscription());
        drain();
    }

    private void drain() {
        synchronized (lock) {
            if (draining || subscriber == null) {
                return;
            }
            draining = true;
        }
        while (true) {
            Flow.Subscriber<? super ObservationUpdate> target;
            @Nullable ObservationUpdate update = null;
            boolean complete = false;
            @Nullable Throwable failure = null;
            synchronized (lock) {
                target = subscriber;
                if (target == null) {
                    draining = false;
                    return;
                }
                if (demand > 0 && !queue.isEmpty()) {
                    update = queue.remove();
                    demand--;
                } else if (queue.isEmpty() && end != null && !terminalSignalled) {
                    terminalSignalled = true;
                    failure = terminalFailure;
                    complete = failure == null;
                } else {
                    draining = false;
                    return;
                }
            }
            try {
                if (update != null) {
                    requestMoreIfRoom();
                    target.onNext(update);
                } else {
                    synchronized (lock) {
                        subscriber = null;
                        draining = false;
                    }
                    if (complete) {
                        target.onComplete();
                    } else {
                        target.onError(failure != null ? failure : new IllegalStateException("Observation failed"));
                    }
                    return;
                }
            } catch (RuntimeException subscriberFailure) {
                synchronized (lock) {
                    subscriber = null;
                    draining = false;
                }
                fail(subscriberFailure);
                return;
            }
        }
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private final class DownstreamSubscription implements Flow.Subscription {
        private boolean cancelled;

        @Override
        public void request(long n) {
            if (n <= 0) {
                fail(new IllegalArgumentException("Flow demand must be positive"));
                return;
            }
            synchronized (lock) {
                if (cancelled) {
                    return;
                }
                demand = demand > Long.MAX_VALUE - n ? Long.MAX_VALUE : demand + n;
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
            OrdinaryInputObservation.this.cancel();
        }
    }

    private final class UpstreamSubscriber implements Flow.Subscriber<TransportFrame> {
        private final TransportObservation source;

        private UpstreamSubscriber(TransportObservation source) {
            this.source = source;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            synchronized (lock) {
                upstream = subscription;
                wantMore = true;
            }
            requestMoreIfRoom();
        }

        @Override
        public void onNext(TransportFrame frame) {
            if (frame.streamError() != null) {
                var rejection = frame.streamError();
                fail(new A2AClientException("Input stream rejected (" + rejection.code() + "): "
                        + (rejection.message() == null ? "" : rejection.message())));
                return;
            }
            try {
                TaskView view = reliability.reduceFrame(reducer, frame);
                enqueue(new ObservationUpdate(frame.event(), view));
            } catch (Throwable reduceFailure) {
                fail(reduceFailure);
                return;
            }
            synchronized (lock) {
                wantMore = true;
            }
            requestMoreIfRoom();
        }

        @Override
        public void onError(Throwable failure) {
            finishFromSource(failure);
        }

        @Override
        public void onComplete() {
            finishFromSource(null);
        }

        private void finishFromSource(@Nullable Throwable signalled) {
            source.completion().whenComplete((sourceEnd, failure) -> {
                if (failure != null) {
                    terminate(ObservationEnd.Kind.FAILED, unwrap(failure));
                } else if (sourceEnd == null) {
                    terminate(ObservationEnd.Kind.FAILED, signalled != null ? signalled
                            : new A2AClientException("Input stream completed without a terminal result"));
                } else {
                    switch (sourceEnd.kind()) {
                        case COMPLETED -> terminate(ObservationEnd.Kind.COMPLETED, null);
                        case CANCELLED -> terminate(ObservationEnd.Kind.CANCELLED, null);
                        case DEADLINE -> terminate(ObservationEnd.Kind.DEADLINE, causeOf(sourceEnd.cause(), signalled,
                                "Observation deadline exceeded"));
                        case FAILED -> terminate(ObservationEnd.Kind.FAILED, causeOf(sourceEnd.cause(), signalled,
                                "Observation failed"));
                    }
                }
            });
        }

        private Throwable causeOf(@Nullable Throwable cause, @Nullable Throwable signalled, String fallback) {
            return cause != null ? cause : signalled != null ? signalled : new A2AClientException(fallback);
        }
    }
}
