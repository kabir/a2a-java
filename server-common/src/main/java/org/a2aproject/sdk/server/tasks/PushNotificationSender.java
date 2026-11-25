package org.a2aproject.sdk.server.tasks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.jspecify.annotations.Nullable;

/**
 * Interface for delivering push notifications containing task state updates to external systems.
 * <p>
 * Push notifications enable asynchronous, out-of-band communication of task progress to
 * configured webhook URLs or messaging systems. This allows clients to receive updates
 * without maintaining persistent connections or polling.
 * </p>
 *
 * <h2>Invocation Context</h2>
 * Called by {@link org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler} after:
 * <ul>
 *   <li>Task events are persisted to {@link TaskStore}</li>
 *   <li>Events are returned/streamed to the requesting client</li>
 *   <li>For streaming: after each event emission to the client</li>
 *   <li>For blocking: after the initial response is returned</li>
 * </ul>
 * <p>
 * Push notifications are always sent AFTER the task state is persisted and the client
 * has received the event, ensuring consistency.
 * </p>
 *
 * <h2>Default Implementation</h2>
 * {@link BasePushNotificationSender} provides HTTP webhook delivery:
 * <ul>
 *   <li>Retrieves webhook URLs from {@link PushNotificationConfigStore}</li>
 *   <li>Formats payloads according to the protocol version stored with each configuration
 *       (v1.0 StreamResponse by default; version-specific formatters via
 *       {@link PushNotificationPayloadFormatter} SPI)</li>
 *   <li>Sends HTTP POST requests with the formatted JSON payload</li>
 *   <li>Logs errors but doesn't fail the request</li>
 * </ul>
 *
 * <h2>Alternative Implementations</h2>
 * Custom implementations can deliver notifications via:
 * <ul>
 *   <li>Kafka topics for event streaming</li>
 *   <li>AWS SNS/SQS for cloud messaging</li>
 *   <li>WebSockets for real-time browser updates</li>
 *   <li>Custom messaging protocols</li>
 * </ul>
 *
 * <h2>CDI Extension Pattern</h2>
 * <pre>{@code
 * @ApplicationScoped
 * @Alternative
 * @Priority(100)
 * public class KafkaPushNotificationSender implements PushNotificationSender {
 *     @Inject
 *     KafkaProducer<String, StreamingEventKind> producer;
 *
 *     @Override
 *     public boolean sendNotification(StreamingEventKind event, Task taskSnapshot) {
 *         String taskId = extractTaskId(event);
 *         producer.send("task-updates", taskId, event);
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * <h2>Error Handling</h2>
 * Implementations should handle errors gracefully:
 * <ul>
 *   <li>Return {@code false} on <b>delivery failures</b> (network errors, HTTP
 *       errors, unreachable endpoints) rather than throwing exceptions —
 *       notifications are best-effort</li>
 *   <li>Throw {@link IllegalArgumentException} or {@link IllegalStateException}
 *       for <b>precondition violations</b> (e.g. an event without a task ID, or
 *       an unsupported event type) — these indicate caller bugs, not delivery
 *       problems</li>
 *   <li>Don't block on network I/O - execute asynchronously if needed</li>
 *   <li>Circuit breaker patterns for repeatedly failing endpoints</li>
 * </ul>
 * Exceptions thrown from this method will not fail the client request — callers
 * catch and log them as errors.
 *
 * <h2>Thread Safety</h2>
 * May be called from multiple threads concurrently for different tasks.
 * Implementations must be thread-safe.
 *
 * @see PushNotificationConfigStore
 * @see BasePushNotificationSender
 * @see org.a2aproject.sdk.spec.TaskPushNotificationConfig
 */
public interface PushNotificationSender extends OrderedWebhookSender {

    /**
     * A no-op sender that always reports success. Useful in tests and
     * configurations where push notifications are not needed.
     */
    PushNotificationSender NOOP = (event, taskSnapshot) -> true;

    /** Compatibility implementations opt out of the additive ordered adapter by default. */
    @Override
    default boolean supportsOrderedDispatch() {
        return false;
    }

    @Override
    default CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, long deadlineNanos,
            LongSupplier nanoTime) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "This PushNotificationSender does not implement ordered dispatch"));
    }

    @Override
    default CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, long deadlineNanos,
            LongSupplier nanoTime, UnaryOperator<Runnable> taskContext) {
        return sendOrderedBatch(batch, deadlineNanos, nanoTime);
    }

    @Override
    default CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, boolean taskProposal,
            long deadlineNanos, LongSupplier nanoTime, UnaryOperator<Runnable> taskContext) {
        return sendOrderedBatch(batch, deadlineNanos, nanoTime, taskContext);
    }

    @Override
    default CompletionStage<Boolean> sendOrderedLegacyTaskSnapshot(Task snapshot, long deadlineNanos,
            LongSupplier nanoTime) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "This PushNotificationSender does not implement ordered dispatch"));
    }

    @Override
    default CompletionStage<Boolean> sendOrderedLegacyTaskSnapshot(Task snapshot, long deadlineNanos,
            LongSupplier nanoTime, UnaryOperator<Runnable> taskContext) {
        return sendOrderedLegacyTaskSnapshot(snapshot, deadlineNanos, nanoTime);
    }

    /**
     * Sends a push notification containing a streaming event.
     * <p>
     * Called after the event has been persisted to {@link TaskStore}. The payload format
     * depends on the protocol version used to register the push notification configuration.
     * <ul>
     *   <li><b>v1.0 (default):</b> The event is wrapped in a StreamResponse format (per A2A spec section 4.3.3)
     *       with the appropriate oneof field set (task, message, statusUpdate, or artifactUpdate).</li>
     *   <li><b>v0.3:</b> The payload is a v0.3 {@code Task} JSON object, using the provided {@code taskSnapshot}.
     *       {@code Message} events are skipped.</li>
     * </ul>
     * <p>
     * Retrieve push notification URLs or messaging configurations from
     * {@link PushNotificationConfigStore} using the task ID extracted from the event.
     * </p>
     * Supported event types:
     * <ul>
     *   <li>{@link Task}</li>
     *   <li>{@link org.a2aproject.sdk.spec.Message}</li>
     *   <li>{@link org.a2aproject.sdk.spec.TaskStatusUpdateEvent}</li>
     *   <li>{@link org.a2aproject.sdk.spec.TaskArtifactUpdateEvent}</li>
     * </ul>
     * <p>
     * <b>Error Handling:</b> Return {@code false} on delivery failures (network
     * errors, HTTP errors). Throwing is reserved for precondition violations such
     * as a missing task ID or an unsupported event type — these signal caller bugs.
     * Callers catch all exceptions so neither case fails the client request.
     * </p>
     *
     * @param event the streaming event to send.
     * @param taskSnapshot the current state of the task after the event has been applied.
     *                     Used by formatters for older protocol versions that require
     *                     the full task state in notifications.
     * @return {@code true} if all notifications were delivered successfully,
     *         {@code false} if one or more deliveries failed
     * @throws IllegalArgumentException if the event does not contain a task ID
     * @throws IllegalStateException if the event type is not recognized
     */
    boolean sendNotification(StreamingEventKind event, @Nullable Task taskSnapshot);

    /**
     * Delivers a status committed from a full-Task proposal. Version-aware senders may choose a
     * Task summary for older configs and the canonical status event for 1.1 configs. Existing
     * senders retain the canonical status fallback.
     */
    default boolean sendTaskProposalStatusNotification(TaskStatusUpdateEvent event, Task taskSnapshot) {
        return sendNotification(event, taskSnapshot);
    }

    /**
     * Delivers a committed Task proposal as one output choice per configured protocol version.
     * Version-aware senders should override this method to send one Task summary to older
     * configurations and every canonical event to 1.1 configurations. The default routes a
     * proposal containing a status through the existing status method. Other proposals forward
     * canonical events through the existing notification method; version-aware SDK senders
     * project the final Task summary for older configurations from the scoped batch context.
     */
    default boolean sendTaskProposalBatchNotification(CommittedTaskBatch batch) {
        Task taskSnapshot = batch.snapshot().task();
        Task dispatchSnapshot = ImmutableTaskCopy.copy(taskSnapshot);
        TaskStatusUpdateEvent statusSummary = batch.events().stream()
                .filter(TaskStatusUpdateEvent.class::isInstance)
                .map(TaskStatusUpdateEvent.class::cast)
                .findFirst().orElse(null);
        if (statusSummary != null) {
            return TaskProposalDispatchContext.withBatchExcept(batch, statusSummary, dispatchSnapshot, () -> {
                boolean delivered = true;
                for (StreamingEventKind event : batch.events()) {
                    boolean sent = event instanceof TaskStatusUpdateEvent status
                            ? sendTaskProposalStatusNotification(status, dispatchSnapshot)
                            : sendProposalEvent(event, dispatchSnapshot);
                    delivered &= sent;
                }
                return delivered;
            });
        }
        return TaskProposalDispatchContext.withBatch(batch, dispatchSnapshot, () -> {
            boolean delivered = true;
            for (StreamingEventKind event : batch.events()) {
                delivered &= sendProposalEvent(event, dispatchSnapshot);
            }
            return delivered;
        });
    }

    private boolean sendProposalEvent(StreamingEventKind event, Task taskSnapshot) {
        if (event instanceof TaskStatusUpdateEvent status) {
            return sendTaskProposalStatusNotification(status, taskSnapshot);
        }
        if (event instanceof TaskArtifactUpdateEvent artifactUpdate) {
            return TaskProposalDispatchContext.withArtifactForwarding(artifactUpdate, taskSnapshot,
                    () -> sendNotification(event, taskSnapshot));
        }
        return sendNotification(event, taskSnapshot);
    }

    /**
     * Delivers a verified active-task no-op snapshot to older-version configs. A sender without
     * version-aware dispatch has no canonical mutation to report and therefore skips this delivery.
     */
    default boolean sendLegacyTaskSnapshot(Task taskSnapshot) {
        return true;
    }
}
