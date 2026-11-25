package org.a2aproject.sdk.extras.opentelemetry.server;

import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.ERROR_TYPE;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.EXTRACT_RESPONSE_SYS_PROPERTY;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.GENAI_CONTEXT_ID;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.GENAI_OPERATION_NAME;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.GENAI_RESPONSE;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.GENAI_TASK_ID;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.PUSH_NOTIFICATION_DELIVERY_FAILED;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.PUSH_NOTIFICATION_EVENT_KIND;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.jspecify.annotations.Nullable;

/**
 * OpenTelemetry CDI Decorator for {@link PushNotificationSender}.
 * <p>
 * Creates a {@code CLIENT} span named {@code SendPushNotification} for every push
 * notification delivery attempt, capturing the task ID, context ID, and event kind
 * as span attributes. The span status reflects the actual delivery outcome:
 * {@code OK} when all notifications succeed, {@code ERROR} when one or more
 * deliveries fail or an exception is thrown.
 * <p>
 * This decorator is registered in the module's {@code META-INF/beans.xml} and is
 * therefore active automatically when this module is on the classpath — no additional
 * configuration is needed.
 */
@Decorator
@Priority(100)
public abstract class OpenTelemetryPushNotificationSenderDecorator
        implements PushNotificationSender {

    static final String SEND_PUSH_NOTIFICATION = "SendPushNotification";

    @Inject
    @Delegate
    @Any
    private PushNotificationSender delegate;

    @Inject
    private Tracer tracer;

    private final boolean extractResponse;

    /**
     * Default constructor for CDI. Reads {@code EXTRACT_RESPONSE_SYS_PROPERTY} once
     * at bean initialisation so the value is not re-evaluated on every notification.
     */
    public OpenTelemetryPushNotificationSenderDecorator() {
        this.extractResponse = Boolean.getBoolean(EXTRACT_RESPONSE_SYS_PROPERTY);
    }

    /**
     * Constructor for unit testing.
     *
     * @param delegate the delegate push notification sender
     * @param tracer the tracer to use
     * @param extractResponse whether to capture the full event payload as a span attribute
     */
    OpenTelemetryPushNotificationSenderDecorator(PushNotificationSender delegate, Tracer tracer,
                                                  boolean extractResponse) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.extractResponse = extractResponse;
    }

    @Override
    public boolean sendNotification(StreamingEventKind event, @Nullable Task taskSnapshot) {
        return traceDelivery(event, taskSnapshot, () -> delegate.sendNotification(event, taskSnapshot));
    }

    @Override
    public boolean sendTaskProposalStatusNotification(TaskStatusUpdateEvent event, Task taskSnapshot) {
        return traceDelivery(event, taskSnapshot,
                () -> delegate.sendTaskProposalStatusNotification(event, taskSnapshot));
    }

    @Override
    public boolean sendTaskProposalBatchNotification(CommittedTaskBatch batch) {
        StreamingEventKind firstEvent = batch.events().get(0);
        Task taskSnapshot = batch.snapshot().task();
        return traceDelivery(firstEvent, taskSnapshot,
                () -> delegate.sendTaskProposalBatchNotification(batch));
    }

    @Override
    public boolean sendLegacyTaskSnapshot(Task taskSnapshot) {
        return traceDelivery(taskSnapshot, taskSnapshot, () -> delegate.sendLegacyTaskSnapshot(taskSnapshot));
    }

    @Override
    public boolean supportsOrderedDispatch() {
        return delegate.supportsOrderedDispatch();
    }

    @Override
    public CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, long deadlineNanos,
            LongSupplier nanoTime) {
        return sendOrderedBatch(batch, false, deadlineNanos, nanoTime,
                java.util.function.UnaryOperator.identity());
    }

    @Override
    public CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, boolean taskProposal,
            long deadlineNanos, LongSupplier nanoTime,
            java.util.function.UnaryOperator<Runnable> taskContext) {
        if (!delegate.supportsOrderedDispatch()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "The wrapped push sender does not support quiescent ordered dispatch"));
        }
        return traceOrdered(batch.events().get(0), batch.snapshot().task(), () -> {
            Context context = Context.current();
            return delegate.sendOrderedBatch(batch, taskProposal, deadlineNanos, nanoTime,
                    runnable -> taskContext.apply(context.wrap(runnable)));
        });
    }

    @Override
    public CompletionStage<Boolean> sendOrderedLegacyTaskSnapshot(Task snapshot, long deadlineNanos,
            LongSupplier nanoTime) {
        return sendOrderedLegacyTaskSnapshot(snapshot, deadlineNanos, nanoTime,
                java.util.function.UnaryOperator.identity());
    }

    @Override
    public CompletionStage<Boolean> sendOrderedLegacyTaskSnapshot(Task snapshot, long deadlineNanos,
            LongSupplier nanoTime, java.util.function.UnaryOperator<Runnable> taskContext) {
        if (!delegate.supportsOrderedDispatch()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "The wrapped push sender does not support quiescent ordered dispatch"));
        }
        return traceOrdered(snapshot, snapshot, () -> {
            Context context = Context.current();
            return delegate.sendOrderedLegacyTaskSnapshot(snapshot, deadlineNanos, nanoTime,
                    runnable -> taskContext.apply(context.wrap(runnable)));
        });
    }

    private CompletionStage<Boolean> traceOrdered(StreamingEventKind event, @Nullable Task taskSnapshot,
            java.util.function.Supplier<CompletionStage<Boolean>> delivery) {
        Span span = newSpan(event, taskSnapshot);
        CompletionStage<Boolean> result;
        try (Scope scope = span.makeCurrent()) {
            result = delivery.get();
        } catch (RuntimeException | Error failure) {
            markFailure(span, failure);
            span.end();
            throw failure;
        }
        return result.whenComplete((sent, failure) -> {
            try (Scope scope = span.makeCurrent()) {
                if (failure != null || !Boolean.TRUE.equals(sent)) {
                    span.setAttribute(ERROR_TYPE, PUSH_NOTIFICATION_DELIVERY_FAILED);
                    span.setStatus(StatusCode.ERROR, "Ordered webhook dispatch failed or was uncertain");
                } else {
                    span.setStatus(StatusCode.OK);
                }
            } finally {
                span.end();
            }
        });
    }

    private Span newSpan(StreamingEventKind event, @Nullable Task taskSnapshot) {
        var spanBuilder = tracer.spanBuilder(SEND_PUSH_NOTIFICATION)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(GENAI_OPERATION_NAME, SEND_PUSH_NOTIFICATION)
                .setAttribute(PUSH_NOTIFICATION_EVENT_KIND, event.getClass().getSimpleName());
        EventIds ids = extractIds(event, taskSnapshot);
        if (ids.taskId != null) {
            spanBuilder.setAttribute(GENAI_TASK_ID, ids.taskId);
        }
        if (ids.contextId != null) {
            spanBuilder.setAttribute(GENAI_CONTEXT_ID, ids.contextId);
        }
        if (extractResponse) {
            spanBuilder.setAttribute(GENAI_RESPONSE, event.toString());
        }
        return spanBuilder.startSpan();
    }

    private static void markFailure(Span span, Throwable failure) {
        String description = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getName();
        span.setAttribute(ERROR_TYPE, description);
        span.setStatus(StatusCode.ERROR, description);
    }

    private boolean traceDelivery(StreamingEventKind event, @Nullable Task taskSnapshot,
            BooleanSupplier delivery) {
        var spanBuilder = tracer.spanBuilder(SEND_PUSH_NOTIFICATION)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(GENAI_OPERATION_NAME, SEND_PUSH_NOTIFICATION)
                .setAttribute(PUSH_NOTIFICATION_EVENT_KIND, event.getClass().getSimpleName());

        EventIds ids = extractIds(event, taskSnapshot);
        if (ids.taskId != null) {
            spanBuilder.setAttribute(GENAI_TASK_ID, ids.taskId);
        }
        if (ids.contextId != null) {
            spanBuilder.setAttribute(GENAI_CONTEXT_ID, ids.contextId);
        }
        if (extractResponse) {
            spanBuilder.setAttribute(GENAI_RESPONSE, event.toString());
        }

        Span span = spanBuilder.startSpan();
        try (Scope scope = span.makeCurrent()) {
            boolean result = delivery.getAsBoolean();
            if (result) {
                span.setStatus(StatusCode.OK);
            } else {
                span.setAttribute(ERROR_TYPE, PUSH_NOTIFICATION_DELIVERY_FAILED);
                span.setStatus(StatusCode.ERROR, "One or more push notification deliveries failed");
            }
            return result;
        } catch (Error err) {
            String errorDescription = err.getMessage() != null ? err.getMessage() : err.getClass().getName();
            span.setAttribute(ERROR_TYPE, errorDescription);
            span.setStatus(StatusCode.ERROR, errorDescription);
            throw err;
        } catch (Exception ex) {
            String errorDescription = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName();
            span.setAttribute(ERROR_TYPE, errorDescription);
            span.setStatus(StatusCode.ERROR, errorDescription);
            throw ex;
        } finally {
            span.end();
        }
    }

    private record EventIds(@Nullable String taskId, @Nullable String contextId) {
    }

    private static EventIds extractIds(StreamingEventKind event, @Nullable Task taskSnapshot) {
        if (taskSnapshot != null) {
            return new EventIds(taskSnapshot.id(), taskSnapshot.contextId());
        }
        if (event instanceof Task task) {
            return new EventIds(task.id(), task.contextId());
        }
        if (event instanceof Message message) {
            return new EventIds(message.taskId(), message.contextId());
        }
        if (event instanceof TaskStatusUpdateEvent statusUpdate) {
            return new EventIds(statusUpdate.taskId(), statusUpdate.contextId());
        }
        if (event instanceof TaskArtifactUpdateEvent artifactUpdate) {
            return new EventIds(artifactUpdate.taskId(), artifactUpdate.contextId());
        }
        return new EventIds(null, null);
    }

}
