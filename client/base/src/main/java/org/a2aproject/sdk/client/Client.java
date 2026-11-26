package org.a2aproject.sdk.client;

import static org.a2aproject.sdk.util.Assert.checkNotNullParam;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TransportCall;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.A2AClientError;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.A2AClientInvalidStateError;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetExtendedAgentCardParams;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.ObserveTaskParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A client for communicating with A2A agents using the Agent2Agent Protocol.
 * <p>
 * The Client class provides the primary API for sending messages to agents, managing tasks,
 * configuring push notifications, and subscribing to task updates. It abstracts the underlying
 * transport protocol (JSON-RPC, gRPC, REST) and provides a consistent interface for all
 * agent interactions.
 * <p>
 * <b>Key capabilities:</b>
 * <ul>
 *   <li><b>Message exchange:</b> Send messages to agents and receive responses via event consumers</li>
 *   <li><b>Task management:</b> Query, list, and cancel tasks</li>
 *   <li><b>Streaming support:</b> Real-time event streaming when both client and server support it</li>
 *   <li><b>Push notifications:</b> Configure webhooks for task state changes</li>
 *   <li><b>Resubscription:</b> Resume receiving events for ongoing tasks after disconnection</li>
 * </ul>
 * <p>
 * <b>Resource management:</b> Client implements {@link AutoCloseable} and should be used with
 * try-with-resources to ensure proper cleanup:
 * <pre>{@code
 * AgentCard card = A2A.getAgentCard("http://localhost:9999");
 *
 * try (Client client = Client.builder(card)
 *         .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
 *         .addConsumer((event, agentCard) -> {
 *             if (event instanceof MessageEvent me) {
 *                 System.out.println("Response: " + me.getMessage().parts());
 *             }
 *         })
 *         .build()) {
 *
 *     // Send messages - client automatically closed when done
 *     client.sendMessage(A2A.toUserMessage("Tell me a joke"));
 * }
 * }</pre>
 * <p>
 * <b>Manual resource management:</b> If not using try-with-resources, call {@link #close()}
 * explicitly when done:
 * <pre>{@code
 * Client client = Client.builder(card)
 *     .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
 *     .addConsumer((event, agentCard) -> {
 *         // Handle events
 *     })
 *     .build();
 *
 * try {
 *     client.sendMessage(A2A.toUserMessage("Tell me a joke"));
 * } finally {
 *     client.close();  // Always close to release resources
 * }
 * }</pre>
 * <p>
 * <b>Event consumption model:</b> Responses from the agent are delivered as {@link ClientEvent}
 * instances to the registered consumers:
 * <ul>
 *   <li>{@link MessageEvent} - contains agent response messages with content parts</li>
 *   <li>{@link TaskEvent} - contains complete task state (typically final state)</li>
 *   <li>{@link TaskUpdateEvent} - contains incremental task updates (status or artifact changes)</li>
 * </ul>
 * <p>
 * <b>Streaming vs blocking:</b> The client supports two communication modes:
 * <ul>
 *   <li><b>Blocking:</b> {@link #sendMessage} blocks until the agent completes the task</li>
 *   <li><b>Streaming:</b> {@link #sendMessage} returns immediately, events delivered asynchronously
 *       to consumers as the agent processes the request</li>
 * </ul>
 * The mode is determined by {@link ClientConfig#isStreaming()} AND {@link org.a2aproject.sdk.spec.AgentCapabilities#streaming()}.
 * Both must be {@code true} for streaming mode; otherwise blocking mode is used.
 * <p>
 * <b>Task lifecycle example:</b>
 * <pre>{@code
 * client.addConsumer((event, card) -> {
 *     if (event instanceof TaskUpdateEvent tue) {
 *         TaskState state = tue.getTask().status().state();
 *         switch (state) {
 *             case SUBMITTED -> System.out.println("Task created");
 *             case WORKING -> System.out.println("Agent is processing...");
 *             case COMPLETED -> System.out.println("Task finished");
 *             case FAILED -> System.err.println("Task failed: " +
 *                 tue.getTask().status().message());
 *         }
 *         
 *         // Check for new artifacts
 *         if (tue.getUpdateEvent() instanceof TaskArtifactUpdateEvent update) {
 *             Artifact artifact = update.artifact();
 *             System.out.println("New content: " + artifact.parts());
 *         }
 *     }
 * });
 * }</pre>
 * <p>
 * <b>Push notifications:</b> Configure webhooks to receive task updates:
 * <pre>{@code
 * // Configure push notifications for a task
 * TaskPushNotificationConfig pushConfig = TaskPushNotificationConfig.builder()
 *     .id("config-1")
 *     .url("https://my-app.com/webhooks/task-updates")
 *     .authentication(new AuthenticationInfo("Bearer", "my-token"))
 *     .build();
 *
 * // Send message with push notifications
 * client.sendMessage(
 *     A2A.toUserMessage("Process this data"),
 *     pushConfig,
 *     null,  // metadata
 *     null   // context
 * );
 * }</pre>
 * <p>
 * <b>Resubscription after disconnection:</b>
 * <pre>{@code
 * // Original request
 * client.sendMessage(A2A.toUserMessage("Long-running task"));
 * // ... client disconnects ...
 *
 * // Later, reconnect and resume receiving events
 * String taskId = "task-123";  // From original request
 * client.subscribeToTask(
 *     new TaskIdParams(taskId),
 *     List.of((event, card) -> {
 *         // Process events from where we left off
 *     }),
 *     null,  // error handler
 *     null   // context
 * );
 * }</pre>
 * <p>
 * <b>Thread safety:</b> Client instances are thread-safe and can be used concurrently from
 * multiple threads. Event consumers must also be thread-safe as they may be invoked concurrently
 * for different tasks.
 * <p>
 * <b>Resource management:</b> Clients hold resources (HTTP connections, gRPC channels, etc.)
 * and should be closed when no longer needed:
 * <pre>{@code
 * try (Client client = Client.builder(card)...build()) {
 *     client.sendMessage(...);
 * } // Automatically closed
 * }</pre>
 *
 * @see ClientBuilder
 * @see ClientEvent
 * @see MessageEvent
 * @see TaskEvent
 * @see TaskUpdateEvent
 * @see org.a2aproject.sdk.A2A
 */
public class Client extends AbstractClient {

    private final ClientConfig clientConfig;
    private final ClientTransport clientTransport;
    private final GenerationReliability reliability = new GenerationReliability();
    private AgentCard agentCard;

    /**
     * Package-private constructor used by {@link ClientBuilder#build()}.
     *
     * @param agentCard the agent card for the target agent
     * @param clientConfig the client configuration
     * @param clientTransport the transport protocol implementation
     * @param consumers the event consumers
     * @param streamingErrorHandler the error handler for streaming scenarios
     */
    Client(AgentCard agentCard, ClientConfig clientConfig, ClientTransport clientTransport,
                  List<BiConsumer<ClientEvent, AgentCard>> consumers, @Nullable Consumer<Throwable> streamingErrorHandler) {
        super(consumers, streamingErrorHandler);
        checkNotNullParam("agentCard", agentCard);

        this.agentCard = agentCard;
        this.clientConfig = clientConfig;
        this.clientTransport = clientTransport;
    }

    /**
     * Create a new builder for constructing a client instance.
     * <p>
     * This is the primary entry point for creating clients. The builder provides a fluent
     * API for configuring transports, event consumers, and client behavior.
     * <p>
     * Example:
     * <pre>{@code
     * AgentCard card = A2A.getAgentCard("http://localhost:9999");
     * Client client = Client.builder(card)
     *     .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
     *     .addConsumer((event, agentCard) -> processEvent(event))
     *     .build();
     * }</pre>
     *
     * @param agentCard the agent card describing the agent to communicate with
     * @return a new builder instance
     * @see ClientBuilder
     */
    public static ClientBuilder builder(AgentCard agentCard) {
        return new ClientBuilder(agentCard);
    }

    /** The version fixed at construction; discovery refresh never changes it. */
    public String getProtocolVersion() {
        return clientConfig.getProtocolVersion();
    }

    @Override
    public void sendMessage(@NonNull Message request,
                            @NonNull List<BiConsumer<ClientEvent, AgentCard>> consumers,
                            @Nullable Consumer<Throwable> streamingErrorHandler,
                            @Nullable ClientCallContext context) throws A2AClientException {
        MessageSendConfiguration messageSendConfiguration = createMessageSendConfiguration(clientConfig.getTaskPushNotificationConfig());

        MessageSendParams messageSendParams = MessageSendParams.builder()
                .message(request)
                .configuration(messageSendConfiguration)
                .metadata(clientConfig.getMetadata())
                .build();
        sendMessage(messageSendParams, consumers, streamingErrorHandler, context);
    }

    /**
     * Send a message to the agent.
     * <p>
     * This is the primary method for communicating with an agent. The behavior depends on
     * whether streaming is enabled:
     * <ul>
     *   <li><b>Streaming mode:</b> Returns immediately, events delivered asynchronously to consumers</li>
     *   <li><b>Blocking mode:</b> Blocks until the agent completes the task, then invokes consumers</li>
     * </ul>
     * Streaming mode is active when both {@link ClientConfig#isStreaming()} AND
     * {@link org.a2aproject.sdk.spec.AgentCapabilities#streaming()} are {@code true}.
     * <p>
     * <b>Simple example:</b>
     * <pre>{@code
     * Message userMessage = A2A.toUserMessage("What's the weather?");
     * client.sendMessage(userMessage, null, null, null);
     * // Events delivered to consumers registered during client construction
     * }</pre>
     * <p>
     * <b>With push notifications:</b>
     * <pre>{@code
     * TaskPushNotificationConfig pushConfig = TaskPushNotificationConfig.builder()
     *     .id("config-1")
     *     .url("https://my-app.com/webhook")
     *     .authentication(new AuthenticationInfo("Bearer", "token"))
     *     .build();
     * client.sendMessage(userMessage, pushConfig, null, null);
     * }</pre>
     * <p>
     * <b>With metadata:</b>
     * <pre>{@code
     * Map<String, Object> metadata = Map.of(
     *     "userId", "user-123",
     *     "sessionId", "session-456"
     * );
     * client.sendMessage(userMessage, null, metadata, null);
     * }</pre>
     *
     * @param request the message to send (required)
     * @param pushNotificationConfiguration webhook configuration for task updates (optional)
     * @param metadata custom metadata to attach to the request (optional)
     * @param context custom call context for request interceptors (optional)
     * @throws A2AClientException if the message cannot be sent or if the agent returns an error
     * @see #sendMessage(Message, List, Consumer, ClientCallContext)
     * @see TaskPushNotificationConfig
     */
    @Override
    public void sendMessage(@NonNull Message request,
                            @Nullable TaskPushNotificationConfig pushNotificationConfiguration,
                            @Nullable Map<String, Object> metadata,
                            @Nullable ClientCallContext context) throws A2AClientException {
        MessageSendConfiguration messageSendConfiguration = createMessageSendConfiguration(pushNotificationConfiguration);
        MessageSendParams messageSendParams = MessageSendParams.builder()
                .message(request)
                .configuration(messageSendConfiguration)
                .metadata(metadata)
                .build();

        sendMessage(messageSendParams, consumers, streamingErrorHandler, context);
    }

    @Override
    public void sendMessage(@NonNull MessageSendParams messageSendParams,
                            @NonNull List<BiConsumer<ClientEvent, AgentCard>> consumers,
                            @Nullable Consumer<Throwable> streamingErrorHandler,
                            @Nullable ClientCallContext context) throws A2AClientException {
        SendInputParams input = SendInputParams.from(messageSendParams);
        ClientInputPolicy.validateSend(clientConfig.getProtocolVersion(), input, agentCard, reliability);
        if (isSelectedOneOne()) {
            sendSelectedMessage(messageSendParams, input, consumers, streamingErrorHandler, context);
            return;
        }
        if (! clientConfig.isStreaming() || ! agentCard.capabilities().streaming()) {
            EventKind eventKind = clientTransport.sendMessage(messageSendParams, context);
            ClientEvent clientEvent;
            if (eventKind instanceof Task task) {
                clientEvent = new TaskEvent(task);
            } else {
                // must be a message
                clientEvent = new MessageEvent((Message) eventKind);
            }
            consume(clientEvent, agentCard, consumers);
        } else {
            ClientTaskManager tracker = new ClientTaskManager();
            Consumer<Throwable> overriddenErrorHandler = getOverriddenErrorHandler(streamingErrorHandler);
            Consumer<StreamingEventKind> eventHandler = event -> {
                try {
                    ClientEvent clientEvent = getClientEvent(event, tracker);
                    consume(clientEvent, agentCard, consumers);
                } catch (A2AClientError e) {
                    overriddenErrorHandler.accept(e);
                }
            };
            clientTransport.sendMessageStreaming(messageSendParams, eventHandler, overriddenErrorHandler, context);
        }
    }


    /**
     * Sends message or artifact input using the selected protocol and delivers known response events
     * to the supplied consumers. The error consumer receives exactly one terminal error or {@code null}
     * for completed 1.1 asynchronous calls and streams.
     */
    public void sendInput(@NonNull SendInputParams params,
            @NonNull List<BiConsumer<ClientEvent, AgentCard>> eventConsumers,
            @Nullable Consumer<Throwable> errorConsumer,
            @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("params", params);
        checkNotNullParam("eventConsumers", eventConsumers);
        ClientInputPolicy.validateSend(clientConfig.getProtocolVersion(), params, agentCard, reliability);

        if ("1.0".equals(clientConfig.getProtocolVersion())) {
            sendLegacyInput(params.requireMessageOnly(), eventConsumers, errorConsumer, context);
            return;
        }

        if (!(clientTransport instanceof VersionedClientTransport versioned)) {
            throw new A2AClientException("Selected transport does not support companion input");
        }
        if (clientConfig.isStreaming() && agentCard.capabilities().streaming()) {
            TransportObservation observation;
            try {
                observation = versioned.streamInput(params, SpiCallOptions.defaults(), context);
            } catch (UnsupportedOperationException unsupported) {
                throw new A2AClientException("Selected transport does not support streaming input", unsupported);
            }
            consumeObservation(OrdinaryInputObservation.stream(observation, newReducer(params), reliability),
                    eventConsumers, failure -> notifyInputCompletion(errorConsumer, failure));
            return;
        }

        final TransportCall<TransportResult<EventKind>> call;
        try {
            call = versioned.sendInput(params, SpiCallOptions.defaults(), context);
        } catch (UnsupportedOperationException unsupported) {
            throw new A2AClientException("Selected transport does not support input", unsupported);
        }
        AtomicBoolean terminal = new AtomicBoolean();
        ClientTaskReducer reducer = newReducer(params);
        call.result().whenComplete((result, failure) -> {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            if (failure != null) {
                notifyInputCompletion(errorConsumer, unwrapCompletionFailure(failure));
                return;
            }
            Throwable dispatchFailure = null;
            try {
                dispatchUnaryResult(result, reducer, eventConsumers);
            } catch (Throwable failureDuringDispatch) {
                dispatchFailure = failureDuringDispatch;
            }
            notifyInputCompletion(errorConsumer, dispatchFailure);
        });
    }

    /** Sends input using the consumers and error handler configured on this client. */
    public void sendInput(@NonNull SendInputParams params, @Nullable ClientCallContext context)
            throws A2AClientException {
        sendInput(params, consumers, streamingErrorHandler, context);
    }

    /** Sends input using the consumers configured on this client. */
    public void sendInput(@NonNull SendInputParams params) throws A2AClientException {
        sendInput(params, consumers, streamingErrorHandler, null);
    }

    private void sendLegacyInput(MessageSendParams request,
            List<BiConsumer<ClientEvent, AgentCard>> eventConsumers,
            @Nullable Consumer<Throwable> errorConsumer, @Nullable ClientCallContext context)
            throws A2AClientException {
        if (!clientConfig.isStreaming() || !agentCard.capabilities().streaming()) {
            dispatchInputResult(clientTransport.sendMessage(request, context), eventConsumers);
            notifyInputCompletion(errorConsumer, null);
            return;
        }
        ClientTaskManager tracker = new ClientTaskManager();
        AtomicBoolean terminal = new AtomicBoolean();
        Consumer<Throwable> terminalError = error -> {
            if (terminal.compareAndSet(false, true)) {
                notifyInputCompletion(errorConsumer, error);
            }
        };
        clientTransport.sendMessageStreaming(request, event -> {
            try {
                ClientEvent clientEvent = getClientEvent(event, tracker);
                consume(clientEvent, agentCard, eventConsumers);
            } catch (A2AClientError failure) {
                terminalError.accept(failure);
            }
        }, terminalError, context);
    }

    /**
     * Sends ordinary input and returns an observation of the reduced task state instead of callbacks.
     * <p>
     * Requires selected protocol version 1.1 and a transport implementing the decoded input companions. A unary
     * result yields one update then completion; a streaming result yields one update per decoded frame, with
     * unknown frames surfacing as view-only updates. This is not a task-subscription, replay or live-input
     * API: the request is never resent or rebased on mismatch, timeout or uncertainty, and cancelling the
     * observation only detaches the local read; it never cancels the task.
     *
     * @param params the input to send; an explicit generation guard is rejected locally, before any I/O, for a
     *               task whose observed generations this client found unreliable
     * @param context custom call context for request interceptors (optional)
     * @return the observation of this request
     * @throws A2AClientException if the request is rejected locally or the transport lacks the companion
     */
    public TaskObservation sendInputObserved(@NonNull SendInputParams params, @Nullable ClientCallContext context)
            throws A2AClientException {
        checkNotNullParam("params", params);
        ClientInputPolicy.validateSend(clientConfig.getProtocolVersion(), params, agentCard, reliability);
        if (!isSelectedOneOne()) {
            throw new A2AClientException("Observed input requires protocol version 1.1");
        }
        if (!(clientTransport instanceof VersionedClientTransport versioned)) {
            throw new A2AClientException("Selected transport does not support companion input");
        }
        ClientTaskReducer reducer = newReducer(params);
        if (clientConfig.isStreaming() && agentCard.capabilities().streaming()) {
            try {
                return OrdinaryInputObservation.stream(
                        versioned.streamInput(params, SpiCallOptions.defaults(), context), reducer, reliability);
            } catch (UnsupportedOperationException unsupported) {
                throw new A2AClientException("Selected transport does not support streaming input", unsupported);
            }
        }
        try {
            return OrdinaryInputObservation.unary(
                    versioned.sendInput(params, SpiCallOptions.defaults(), context), reducer, reliability);
        } catch (UnsupportedOperationException unsupported) {
            throw new A2AClientException("Selected transport does not support input", unsupported);
        }
    }

    private boolean isSelectedOneOne() {
        return "1.1".equals(clientConfig.getProtocolVersion());
    }

    private static ClientTaskReducer newReducer(SendInputParams params) {
        MessageSendConfiguration configuration = params.configuration();
        return configuration == null ? new ClientTaskReducer()
                : new ClientTaskReducer(configuration.historyLength(), configuration.timelineLength());
    }

    /**
     * Selected-1.1 {@code sendMessage}: uses the decoded input companions and the shared reducer. Only an
     * optional companion that is unsupported before dispatch falls back to the established transport method,
     * still reducing every event but marking skipped-variant evidence unavailable.
     */
    private void sendSelectedMessage(MessageSendParams legacy, SendInputParams input,
            List<BiConsumer<ClientEvent, AgentCard>> eventConsumers, @Nullable Consumer<Throwable> errorHandler,
            @Nullable ClientCallContext context) throws A2AClientException {
        @Nullable VersionedClientTransport versioned = clientTransport instanceof VersionedClientTransport selected
                ? selected : null;
        ClientTaskReducer reducer = newReducer(input);
        Consumer<Throwable> handler = getOverriddenErrorHandler(errorHandler);
        if (clientConfig.isStreaming() && agentCard.capabilities().streaming()) {
            @Nullable TransportObservation observation = null;
            if (versioned != null) {
                try {
                    observation = versioned.streamInput(input, SpiCallOptions.defaults(), context);
                } catch (UnsupportedOperationException unsupported) {
                    // Optional companion unsupported before dispatch: use the established streaming method.
                }
            }
            if (observation != null) {
                consumeObservation(OrdinaryInputObservation.stream(observation, reducer, reliability),
                        eventConsumers, failure -> reportFailure(handler, failure));
                return;
            }
            clientTransport.sendMessageStreaming(legacy,
                    event -> dispatchReduced(event, reducer, eventConsumers, handler), handler, context);
            return;
        }
        @Nullable TransportCall<TransportResult<EventKind>> call = null;
        if (versioned != null) {
            try {
                call = versioned.sendInput(input, SpiCallOptions.defaults(), context);
            } catch (UnsupportedOperationException unsupported) {
                // Optional companion unsupported before dispatch: use the established unary method.
            }
        }
        if (call != null) {
            dispatchUnaryResult(awaitUnary(call), reducer, eventConsumers);
            return;
        }
        EventKind result = clientTransport.sendMessage(legacy, context);
        if (!(result instanceof StreamingEventKind event)) {
            throw new A2AClientException("Unsupported send response event: " + result.getClass().getName());
        }
        TaskView view = reliability.reduceUnaryWithoutEvidence(reducer, result);
        consume(OrdinaryInputObservation.toClientEvent(event, view), agentCard, eventConsumers);
    }

    /** Waits for a decoded unary result on the caller's thread, preserving blocking sendMessage semantics. */
    private static TransportResult<EventKind> awaitUnary(TransportCall<TransportResult<EventKind>> call)
            throws A2AClientException {
        try {
            return call.result().toCompletableFuture().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            call.cancel();
            throw new A2AClientException("Interrupted while waiting for the send result", interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
            if (cause instanceof A2AClientException clientFailure) {
                throw clientFailure;
            } else if (cause instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            } else if (cause instanceof Error error) {
                throw error;
            }
            throw new A2AClientException("Failed to send message: " + cause, cause);
        }
    }

    private void dispatchUnaryResult(TransportResult<EventKind> result, ClientTaskReducer reducer,
            List<BiConsumer<ClientEvent, AgentCard>> eventConsumers) throws A2AClientException {
        EventKind value = result.value();
        if (!(value instanceof StreamingEventKind event)) {
            throw new A2AClientException("Unsupported input response event: " + value.getClass().getName());
        }
        TaskView view = reliability.reduceUnary(reducer, result);
        consume(OrdinaryInputObservation.toClientEvent(event, view), agentCard, eventConsumers);
    }

    private void dispatchReduced(StreamingEventKind event, ClientTaskReducer reducer,
            List<BiConsumer<ClientEvent, AgentCard>> eventConsumers, Consumer<Throwable> errorHandler) {
        try {
            TaskView view = reliability.reduceEventWithoutEvidence(reducer, event);
            consume(OrdinaryInputObservation.toClientEvent(event, view), agentCard, eventConsumers);
        } catch (A2AClientError failure) {
            errorHandler.accept(failure);
        }
    }

    /** Streaming sendMessage/subscribe report failures only; normal completion and cancellation are silent. */
    private static void reportFailure(Consumer<Throwable> handler, @Nullable Throwable failure) {
        if (failure != null && !(failure instanceof CancellationException)) {
            handler.accept(failure);
        }
    }

    /**
     * Delivers an observation's known events through the established callback shapes. View-only updates never
     * produce a callback and a partial view never becomes a synthetic complete {@link TaskEvent}. The terminal
     * sink receives exactly one outcome: {@code null} for clean completion, a {@link CancellationException} for
     * cancellation, or the failure.
     */
    private void consumeObservation(OrdinaryInputObservation observation,
            List<BiConsumer<ClientEvent, AgentCard>> eventConsumers, Consumer<Throwable> terminalSink) {
        AtomicBoolean terminal = new AtomicBoolean();
        Consumer<Throwable> finish = failure -> {
            if (terminal.compareAndSet(false, true)) {
                terminalSink.accept(failure);
            }
        };
        observation.updates().subscribe(new Flow.Subscriber<>() {
            private Flow.@Nullable Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription next) {
                subscription = next;
                next.request(1);
            }

            @Override
            public void onNext(ObservationUpdate update) {
                ClientEvent event = OrdinaryInputObservation.toClientEvent(update);
                if (event != null) {
                    try {
                        consume(event, agentCard, eventConsumers);
                    } catch (Throwable dispatchFailure) {
                        finish.accept(dispatchFailure);
                        observation.cancel();
                        return;
                    }
                }
                Flow.Subscription current = subscription;
                if (current != null) {
                    current.request(1);
                }
            }

            @Override
            public void onError(Throwable failure) {
                finish.accept(failure);
            }

            @Override
            public void onComplete() {
                observation.completion().whenComplete((end, failure) -> {
                    if (failure != null) {
                        finish.accept(unwrapCompletionFailure(failure));
                    } else if (end.kind() == ObservationEnd.Kind.CANCELLED) {
                        finish.accept(new CancellationException("Input stream cancelled"));
                    } else if (end.kind() == ObservationEnd.Kind.COMPLETED) {
                        finish.accept(null);
                    } else {
                        finish.accept(end.cause() != null ? end.cause()
                                : new A2AClientException("Input stream ended: " + end.kind()));
                    }
                });
            }
        });
    }


    private void dispatchInputResult(EventKind result,
            List<BiConsumer<ClientEvent, AgentCard>> eventConsumers) throws A2AClientException {
        ClientEvent event;
        if (result instanceof Task task) {
            event = new TaskEvent(task);
        } else if (result instanceof Message message) {
            event = new MessageEvent(message);
        } else {
            throw new A2AClientException("Unsupported input response event: " + result.getClass().getName());
        }
        consume(event, agentCard, eventConsumers);
    }

    private void notifyInputCompletion(@Nullable Consumer<Throwable> errorConsumer, @Nullable Throwable failure) {
        if (errorConsumer != null) {
            errorConsumer.accept(failure);
        } else if (failure != null) {
            getOverriddenErrorHandler(null).accept(failure);
        }
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    /**
     * Retrieve a specific task by ID.
     * <p>
     * This method queries the agent for the current state of a task. It's useful for:
     * <ul>
     *   <li>Checking the status of a task after disconnection</li>
     *   <li>Retrieving task results without subscribing to events</li>
     *   <li>Polling for task completion (when streaming is not available)</li>
     * </ul>
     * <p>
     * Example:
     * <pre>{@code
     * Task task = client.getTask(new TaskQueryParams("task-123"));
     * if (task.status().state() == TaskState.COMPLETED) {
     *     Artifact result = task.artifact();
     *     System.out.println("Result: " + result.parts());
     * } else if (task.status().state() == TaskState.FAILED) {
     *     System.err.println("Task failed: " + task.status().message());
     * }
     * }</pre>
     *
     * @param request the task query parameters containing the task ID
     * @param context custom call context for request interceptors (optional)
     * @return the current task state
     * @throws A2AClientException if the task is not found or if a communication error occurs
     * @see TaskQueryParams
     * @see Task
     */
    @Override
    public Task getTask(TaskQueryParams request, @Nullable ClientCallContext context) throws A2AClientException {
        ClientInputPolicy.validateTaskQuery(clientConfig.getProtocolVersion(), request);
        if (supportsDecodedReads()) {
            return ((VersionedClientTransport) clientTransport).getTaskDecoded(request, context).value();
        }
        return clientTransport.getTask(request, context);
    }

    /**
     * List tasks for the current session or context.
     * <p>
     * This method retrieves multiple tasks based on filter criteria. Useful for:
     * <ul>
     *   <li>Viewing all tasks in a session/context</li>
     *   <li>Finding tasks by state (e.g., all failed tasks)</li>
     *   <li>Paginating through large task lists</li>
     * </ul>
     * <p>
     * Example:
     * <pre>{@code
     * // List all tasks for a context
     * ListTasksParams params = new ListTasksParams(
     *     "session-123",  // contextId
     *     null,           // state filter (null = all states)
     *     10,             // limit
     *     null            // offset
     * );
     * ListTasksResult result = client.listTasks(params);
     * for (Task task : result.tasks()) {
     *     System.out.println(task.id() + ": " + task.status().state());
     * }
     * }</pre>
     *
     * @param request the list parameters with optional filters
     * @param context custom call context for request interceptors (optional)
     * @return the list of tasks matching the criteria
     * @throws A2AClientException if a communication error occurs
     * @see ListTasksParams
     * @see ListTasksResult
     */
    @Override
    public ListTasksResult listTasks(ListTasksParams request, @Nullable ClientCallContext context) throws A2AClientException {
        ClientInputPolicy.validateListTasks(clientConfig.getProtocolVersion(), request);
        if (supportsDecodedReads()) {
            return ((VersionedClientTransport) clientTransport).listTasksDecoded(request, context).value();
        }
        return clientTransport.listTasks(request, context);
    }

    private boolean supportsDecodedReads() {
        return clientTransport instanceof VersionedClientTransport versioned
                && "1.1".equals(versioned.protocolVersion())
                && versioned.capabilities().contains(ClientFeature.UNKNOWN_VARIANT_SKIP);
    }

    /**
     * Request cancellation of a task.
     * <p>
     * This method sends a cancellation request to the agent for the specified task. The agent
     * may or may not honor the request depending on its implementation and the task's current state.
     * <p>
     * <b>Important notes:</b>
     * <ul>
     *   <li>Cancellation is a request, not a guarantee - agents may decline or be unable to cancel</li>
     *   <li>Some agents don't support cancellation and will return {@link org.a2aproject.sdk.spec.UnsupportedOperationError}</li>
     *   <li>Tasks in final states (COMPLETED, FAILED, CANCELED) cannot be canceled</li>
     *   <li>The returned task will have state CANCELED if the cancellation succeeded</li>
     * </ul>
     * <p>
     * Example:
     * <pre>{@code
     * try {
     *     Task canceledTask = client.cancelTask(new TaskIdParams("task-123"));
     *     if (canceledTask.status().state() == TaskState.CANCELED) {
     *         System.out.println("Task successfully canceled");
     *     }
     * } catch (A2AClientException e) {
     *     if (e.getCause() instanceof UnsupportedOperationError) {
     *         System.err.println("Agent does not support cancellation");
     *     } else if (e.getCause() instanceof TaskNotFoundError) {
     *         System.err.println("Task not found");
     *     }
     * }
     * }</pre>
     *
     * @param request the task ID to cancel
     * @param context custom call context for request interceptors (optional)
     * @return the task with CANCELED status if successful
     * @throws A2AClientException if the task cannot be canceled or if a communication error occurs
     * @see TaskIdParams
     * @see org.a2aproject.sdk.spec.UnsupportedOperationError
     * @see org.a2aproject.sdk.spec.TaskNotFoundError
     */
    @Override
    public Task cancelTask(CancelTaskParams request, @Nullable ClientCallContext context) throws A2AClientException {
        return clientTransport.cancelTask(request, context);
    }

    /**
     * Configure push notifications for a task.
     * <p>
     * Push notifications allow your application to receive task updates via webhook instead
     * of maintaining an active connection. When configured, the agent will POST events to
     * the specified URL as the task progresses.
     * <p>
     * Example:
     * <pre>{@code
     * TaskPushNotificationConfig config = TaskPushNotificationConfig.builder()
     *     .id("config-1")
     *     .taskId("task-123")
     *     .url("https://my-app.com/webhooks/task-updates")
     *     .authentication(new AuthenticationInfo("Bearer", "my-webhook-secret"))
     *     .build();
     * client.createTaskPushNotificationConfiguration(config);
     * }</pre>
     *
     * @param request the push notification configuration for the task
     * @param context custom call context for request interceptors (optional)
     * @return the stored configuration (may include server-assigned IDs)
     * @throws A2AClientException if the configuration cannot be set
     * @see TaskPushNotificationConfig
     */
    @Override
    public TaskPushNotificationConfig createTaskPushNotificationConfiguration(
            TaskPushNotificationConfig request, @Nullable ClientCallContext context) throws A2AClientException {
        ClientInputPolicy.validatePushNotificationConfig(request);
        return clientTransport.createTaskPushNotificationConfiguration(request, context);
    }

    /**
     * Retrieve the push notification configuration for a task.
     * <p>
     * Example:
     * <pre>{@code
     * GetTaskPushNotificationConfigParams params =
     *     new GetTaskPushNotificationConfigParams("task-123");
     * TaskPushNotificationConfig config =
     *     client.getTaskPushNotificationConfiguration(params);
     * System.out.println("Webhook URL: " +
     *     config.url());
     * }</pre>
     *
     * @param request the parameters specifying which task's configuration to retrieve
     * @param context custom call context for request interceptors (optional)
     * @return the push notification configuration for the task
     * @throws A2AClientException if the configuration cannot be retrieved
     * @see GetTaskPushNotificationConfigParams
     */
    @Override
    public TaskPushNotificationConfig getTaskPushNotificationConfiguration(
            GetTaskPushNotificationConfigParams request, @Nullable ClientCallContext context) throws A2AClientException {
        return clientTransport.getTaskPushNotificationConfiguration(request, context);
    }

    /**
     * List all push notification configurations, optionally filtered by task or context.
     * <p>
     * Example:
     * <pre>{@code
     * // List all configurations for a context
     * ListTaskPushNotificationConfigsParams params =
     *     new ListTaskPushNotificationConfigsParams("session-123", null, 10, null);
     * ListTaskPushNotificationConfigsResult result =
     *     client.listTaskPushNotificationConfigurations(params);
     * for (TaskPushNotificationConfig config : result.configurations()) {
     *     System.out.println("Task " + config.taskId() + " -> " +
     *         config.url());
     * }
     * }</pre>
     *
     * @param request the list parameters with optional filters
     * @param context custom call context for request interceptors (optional)
     * @return the list of push notification configurations
     * @throws A2AClientException if the configurations cannot be retrieved
     * @see ListTaskPushNotificationConfigsParams
     */
    @Override
    public ListTaskPushNotificationConfigsResult listTaskPushNotificationConfigurations(
            ListTaskPushNotificationConfigsParams request, @Nullable ClientCallContext context) throws A2AClientException {
        return clientTransport.listTaskPushNotificationConfigurations(request, context);
    }

    /**
     * Delete push notification configurations.
     * <p>
     * This method removes push notification configurations for the specified tasks or context.
     * After deletion, the agent will stop sending webhook notifications for those tasks.
     * <p>
     * Example:
     * <pre>{@code
     * // Delete configuration for a specific task
     * DeleteTaskPushNotificationConfigParams params =
     *     new DeleteTaskPushNotificationConfigParams(
     *         null,           // contextId (null = not filtering by context)
     *         List.of("task-123", "task-456")  // specific task IDs
     *     );
     * client.deleteTaskPushNotificationConfigurations(params);
     * }</pre>
     *
     * @param request the delete parameters specifying which configurations to remove
     * @param context custom call context for request interceptors (optional)
     * @throws A2AClientException if the configurations cannot be deleted
     * @see DeleteTaskPushNotificationConfigParams
     */
    @Override
    public void deleteTaskPushNotificationConfigurations(
            DeleteTaskPushNotificationConfigParams request, @Nullable ClientCallContext context) throws A2AClientException {
        clientTransport.deleteTaskPushNotificationConfigurations(request, context);
    }

    /**
     * Subscribe to an existing task to receive remaining events.
     * <p>
     * This method is useful when a client disconnects during a long-running task and wants to
     * resume receiving events without starting a new task. The agent will deliver any events
     * that occurred since the original subscription.
     * <p>
     * <b>Requirements:</b>
     * <ul>
     *   <li>Both {@link ClientConfig#isStreaming()} and {@link org.a2aproject.sdk.spec.AgentCapabilities#streaming()}
     *       must be {@code true}</li>
     *   <li>The task must still exist and not be in a final state (or the agent must support
     *       historical event replay)</li>
     * </ul>
     * <p>
     * Example:
     * <pre>{@code
     * // Original request (client1)
     * client1.sendMessage(A2A.toUserMessage("Analyze this dataset"));
     * String taskId = ...; // Save task ID from TaskEvent
     * // ... client1 disconnects ...
     *
     * // Later, reconnect (client2)
     * client2.subscribeToTask(
     *     new TaskIdParams(taskId),
     *     List.of((event, card) -> {
     *         if (event instanceof TaskUpdateEvent tue) {
     *             System.out.println("Resumed - status: " +
     *                 tue.getTask().status().state());
     *         }
     *     }),
     *     throwable -> System.err.println("Subscribe error: " + throwable),
     *     null
     * );
     * }</pre>
     *
     * @param request the task ID to subscribe to
     * @param consumers the event consumers for processing events (required)
     * @param streamingErrorHandler error handler for streaming errors (optional)
     * @param context custom call context for request interceptors (optional)
     * @throws A2AClientException if subscription is not supported or if the task cannot be found
     */
    @Override
    public void subscribeToTask(@NonNull TaskIdParams request,
                            @NonNull List<BiConsumer<ClientEvent, AgentCard>> consumers,
                            @Nullable Consumer<Throwable> streamingErrorHandler,
                            @Nullable ClientCallContext context) throws A2AClientException {
        if (! clientConfig.isStreaming() || ! agentCard.capabilities().streaming()) {
            throw new A2AClientException("Client and/or server does not support resubscription");
        }
        if (isSelectedOneOne()) {
            subscribeSelected(request, consumers, streamingErrorHandler, context);
            return;
        }

        ClientTaskManager tracker = new ClientTaskManager();
        Consumer<Throwable> overriddenErrorHandler = getOverriddenErrorHandler(streamingErrorHandler);
        Consumer<StreamingEventKind> eventHandler = event -> {
            try {
                ClientEvent clientEvent = getClientEvent(event, tracker);
                consume(clientEvent, agentCard, consumers);
            } catch (A2AClientError e) {
                overriddenErrorHandler.accept(e);
            }
        };
        clientTransport.subscribeToTask(request, eventHandler, overriddenErrorHandler, context);
    }

    /**
     * Selected-1.1 no-cursor subscription. The versioned {@code observe} companion is used through the
     * configured wrappers so skipped-variant evidence reaches the reducer. Only an {@code observe} that is
     * unsupported before dispatch falls back to the established callback subscription, whose events are still
     * reduced but whose skipped-variant evidence is unavailable (coverage not asserted, view incomplete).
     */
    private void subscribeSelected(TaskIdParams request, List<BiConsumer<ClientEvent, AgentCard>> eventConsumers,
            @Nullable Consumer<Throwable> errorHandler, @Nullable ClientCallContext context)
            throws A2AClientException {
        Consumer<Throwable> handler = getOverriddenErrorHandler(errorHandler);
        ClientTaskReducer reducer = new ClientTaskReducer();
        if (clientTransport instanceof VersionedClientTransport versioned) {
            ObserveTaskParams observe = new ObserveTaskParams(request.id(), null, request.tenant());
            ClientInputPolicy.validateObserve(observe);
            @Nullable TransportObservation observation = null;
            try {
                observation = versioned.observe(observe, SpiCallOptions.defaults(), context);
            } catch (UnsupportedOperationException unsupported) {
                // Optional observe unsupported before dispatch: use the established callback subscription.
            }
            if (observation != null) {
                consumeObservation(OrdinaryInputObservation.stream(observation, reducer, reliability),
                        eventConsumers, failure -> reportFailure(handler, failure));
                return;
            }
        }
        clientTransport.subscribeToTask(request,
                event -> dispatchReduced(event, reducer, eventConsumers, handler), handler, context);
    }

    /**
     * Retrieve the agent's extended agent card.
     * <p>
     * This method fetches the extended agent card from the agent (if the extendedAgentCard capability is supported).
     * The card may have changed since
     * client construction (e.g., new skills added, capabilities updated). The client's internal
     * reference is updated to the newly retrieved card.
     * <p>
     * Example:
     * <pre>{@code
     * AgentCard updatedCard = client.getExtendedAgentCard();
     * System.out.println("Agent version: " + updatedCard.version());
     * System.out.println("Skills: " + updatedCard.skills().size());
     * }</pre>
     *
     * @param tenant Optional tenant
     * @param context custom call context for request interceptors (optional)
     * @return the agent's extended agent card
     * @throws A2AClientException if the extended agent card cannot be retrieved
     * @see AgentCard
     */
    @Override
    public AgentCard getExtendedAgentCard(@Nullable String tenant, @Nullable ClientCallContext context) throws A2AClientException {
        GetExtendedAgentCardParams params = new GetExtendedAgentCardParams(tenant);
        agentCard = clientTransport.getExtendedAgentCard(params, context);
        return agentCard;
    }

    /**
     * Close this client and release all associated resources.
     * <p>
     * This method closes the underlying transport (HTTP connections, gRPC channels, etc.)
     * and releases any other resources held by the client. After calling this method, the
     * client instance should not be used further.
     * <p>
     * <b>Important:</b> Always close clients when done to avoid resource leaks:
     * <pre>{@code
     * Client client = Client.builder(card)...build();
     * try {
     *     client.sendMessage(...);
     * } finally {
     *     client.close();
     * }
     * // Or use try-with-resources if Client implements AutoCloseable
     * }</pre>
     */
    @Override
    public void close() {
        clientTransport.close();
    }

    private ClientEvent getClientEvent(StreamingEventKind event, ClientTaskManager taskManager) throws A2AClientError {
        if (event instanceof Message message) {
            return new MessageEvent(message);
        } else if (event instanceof Task task) {
            taskManager.saveTaskEvent(task);
            return new TaskEvent(taskManager.getCurrentTask());
        } else if (event instanceof TaskStatusUpdateEvent updateEvent) {
            taskManager.saveTaskEvent(updateEvent);
            return new TaskUpdateEvent(taskManager.getCurrentTask(), updateEvent);
        } else if (event instanceof TaskArtifactUpdateEvent updateEvent) {
            taskManager.saveTaskEvent(updateEvent);
            return new TaskUpdateEvent(taskManager.getCurrentTask(), updateEvent);
        } else if (event instanceof TaskMessageUpdateEvent updateEvent) {
            taskManager.saveTaskEvent(updateEvent);
            return new TaskUpdateEvent(taskManager.getCurrentTask(), updateEvent);
        } else {
            throw new A2AClientInvalidStateError("Invalid client event");
        }
    }

    private MessageSendConfiguration createMessageSendConfiguration(@Nullable TaskPushNotificationConfig taskPushNotificationConfig) {
        return MessageSendConfiguration.builder()
                .acceptedOutputModes(clientConfig.getAcceptedOutputModes())
                .returnImmediately(clientConfig.isPolling())
                .historyLength(clientConfig.getHistoryLength())
                .taskPushNotificationConfig(taskPushNotificationConfig)
                .build();
    }

    private @NonNull Consumer<Throwable> getOverriddenErrorHandler(@Nullable Consumer<Throwable> errorHandler) {
        return e -> {
            if (errorHandler != null) {
                errorHandler.accept(e);
            } else {
                if (getStreamingErrorHandler() != null) {
                    getStreamingErrorHandler().accept(e);
                }
            }
        };
    }

    private void consume(ClientEvent clientEvent, AgentCard agentCard, @NonNull List<BiConsumer<ClientEvent, AgentCard>> consumers) {
        for (BiConsumer<ClientEvent, AgentCard> consumer : consumers) {
            consumer.accept(clientEvent, agentCard);
        }
    }
}
