package org.a2aproject.sdk.client.transport.jsonrpc;

import static org.a2aproject.sdk.spec.A2AMethods.CANCEL_TASK_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.DELETE_TASK_PUSH_NOTIFICATION_CONFIG_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.GET_EXTENDED_AGENT_CARD_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.GET_TASK_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.LIST_TASK_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.LIST_TASK_PUSH_NOTIFICATION_CONFIG_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SEND_MESSAGE_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SEND_STREAMING_MESSAGE_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SUBSCRIBE_TO_TASK_METHOD;
import static org.a2aproject.sdk.util.Assert.checkNotNullParam;

import com.google.protobuf.MessageOrBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpClientConfiguration;
import org.a2aproject.sdk.client.http.A2AHttpClientFactory;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.client.transport.jsonrpc.sse.SSEEventListener;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientProtocolVersion;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TransportCall;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.grpc.utils.DecodedEvent;
import org.a2aproject.sdk.grpc.utils.DecodedTask;
import org.a2aproject.sdk.grpc.utils.DecodedTaskList;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetExtendedAgentCardResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.spec.A2AClientError;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.A2AClientHTTPError;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetExtendedAgentCardParams;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.util.Utils;
import org.jspecify.annotations.Nullable;

public class JSONRPCTransport implements VersionedClientTransport {

    private final String protocolVersion;
    private final A2AHttpClient httpClient;
    private final AgentInterface agentInterface;
    private final @Nullable List<ClientCallInterceptor> interceptors;
    private final @Nullable AgentCard agentCard;
    private final boolean followRedirects;
    private final Set<TransportObservation> activeInputObservations = ConcurrentHashMap.newKeySet();

    /** Creates a transport using protocol 1.1. Use the version overload to select 1.0. */
    public JSONRPCTransport(String agentUrl) {
        this(agentUrl, ClientProtocolVersion.DEFAULT);
    }

    public JSONRPCTransport(String agentUrl, String protocolVersion) {
        this(null, null, new AgentInterface("JSONRPC", agentUrl, null, ClientProtocolVersion.validate(protocolVersion)), null);
    }

    /** Selects an advertised JSON-RPC interface with protocol 1.1. */
    public JSONRPCTransport(AgentCard agentCard) {
        this(agentCard, ClientProtocolVersion.DEFAULT);
    }

    public JSONRPCTransport(AgentCard agentCard, String protocolVersion) {
        this(null, agentCard, ClientProtocolVersion.selectInterface(agentCard, "JSONRPC", protocolVersion), null);
    }

    public JSONRPCTransport(@Nullable A2AHttpClient httpClient, @Nullable AgentCard agentCard,
                            AgentInterface agentInterface, @Nullable List<ClientCallInterceptor> interceptors) {
        this(httpClient, agentCard, agentInterface, interceptors, false);
    }

    public JSONRPCTransport(@Nullable A2AHttpClient httpClient, @Nullable AgentCard agentCard,
                            AgentInterface agentInterface, @Nullable List<ClientCallInterceptor> interceptors,
                            boolean followRedirects) {
        this.protocolVersion = ClientProtocolVersion.validate(agentInterface.protocolVersion());
        this.httpClient = httpClient == null ? A2AHttpClientFactory.create(A2AHttpClientConfiguration.DEFAULT) : httpClient;
        this.agentCard = agentCard;
        this.agentInterface = agentInterface;
        this.interceptors = interceptors;
        this.followRedirects = followRedirects;
    }

    @Override
    public String protocolVersion() {
        return protocolVersion;
    }

    @Override
    public Set<ClientFeature> capabilities() {
        if ("1.1".equals(protocolVersion)) {
            return Set.of(ClientFeature.ORDINARY, ClientFeature.GUARDED_INPUT,
                    ClientFeature.ARTIFACT_INPUT, ClientFeature.UNKNOWN_VARIANT_SKIP);
        }
        return Set.of(ClientFeature.ORDINARY);
    }

    @Override
    public EventKind sendMessage(MessageSendParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SEND_MESSAGE_METHOD, ProtoUtils.ToProto.sendMessageRequest(request),
                agentCard, context);

        try {
            String httpResponseBody = sendPostRequest(requestUrl(request.tenant()), payloadAndHeaders, SEND_MESSAGE_METHOD);
            SendMessageResponse response = unmarshalResponse(httpResponseBody, SEND_MESSAGE_METHOD);
            return response.getResult();
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to send message: " + e, e);
        }
    }

    @Override
    public void sendMessageStreaming(MessageSendParams request, Consumer<StreamingEventKind> eventConsumer,
                                     @Nullable Consumer<Throwable> errorConsumer, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        checkNotNullParam("eventConsumer", eventConsumer);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SEND_STREAMING_MESSAGE_METHOD,
                ProtoUtils.ToProto.sendMessageRequest(request), agentCard, context);

        final AtomicReference<CompletableFuture<Void>> ref = new AtomicReference<>();
        SSEEventListener sseEventListener = new SSEEventListener(eventConsumer, errorConsumer);

        try {
            A2AHttpClient.PostBuilder builder = createPostBuilder(
                    requestUrl(request.tenant()), payloadAndHeaders, SEND_STREAMING_MESSAGE_METHOD, false);
            ref.set(builder.postAsyncSSE(
                    event -> sseEventListener.onMessage(event, ref.get()),
                    throwable -> sseEventListener.onError(throwable, ref.get()),
                    () -> {
                        // Signal normal stream completion to error handler (null error means success)
                        sseEventListener.onComplete();
                    }));
        } catch (IOException e) {
            throw new A2AClientException("Failed to send streaming message request: " + e, e);
        } catch (InterruptedException e) {
            throw new A2AClientException("Send streaming message request timed out: " + e, e);
        } catch (JsonProcessingException e) {
            throw new A2AClientException("Failed to process JSON for streaming message request: " + e, e);
        }
    }

    @Override
    public Task getTask(TaskQueryParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_TASK_METHOD, ProtoUtils.ToProto.getTaskRequest(request),
                agentCard, context);

        try {
            String httpResponseBody = sendPostRequest(requestUrl(request.tenant()), payloadAndHeaders, GET_TASK_METHOD);
            GetTaskResponse response = unmarshalResponse(httpResponseBody, GET_TASK_METHOD);
            return response.getResult();
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to get task: " + e, e);
        }
    }

    @Override
    public TransportCall<TransportResult<Task>> getTaskAsync(TaskQueryParams request, SpiCallOptions options,
            @Nullable ClientCallContext context) {
        checkNotNullParam("request", request);
        checkNotNullParam("options", options);
        requireDecodedOneOneRead();
        if (!"1.1".equals(protocolVersion)) {
            throw new UnsupportedOperationException("Decoded asynchronous task reads require protocol version 1.1");
        }
        AtomicReference<A2AHttpClient.AsyncPostCall> requestFuture = new AtomicReference<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        TransportCall<TransportResult<Task>> call = TransportCall.create(() -> {
            cancellationRequested.set(true);
            A2AHttpClient.AsyncPostCall future = requestFuture.get();
            if (future != null) future.cancel();
        });
        try {
            PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_TASK_METHOD,
                    ProtoUtils.ToProto.getTaskRequest(request), agentCard, context);
            String requestId = UUID.randomUUID().toString();
            A2AHttpClient.AsyncPostCall future = createPostBuilder(requestUrl(request.tenant()), payloadAndHeaders,
                    GET_TASK_METHOD, followRedirects, requestId).postAsyncResponse();
            requestFuture.set(future);
            if (cancellationRequested.get()) future.cancel();
            future.response().whenComplete((response, failure) -> {
                if (failure != null) {
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    call.fail(cause instanceof A2AClientException ? cause
                            : new A2AClientException("Failed to get decoded task: " + cause, cause));
                } else if (response == null) {
                    call.fail(new A2AClientException("Failed to get decoded task: empty response"));
                } else if (!response.success()) {
                    int status = response.status();
                    String message = "Request failed with HTTP " + status;
                    call.fail(new A2AClientException(message,
                            new A2AClientHTTPError(status, message, response.body(), response.headers().toMap())));
                } else {
                    try {
                        DecodedTask decoded = JSONRPCUtils.decodeTaskResponse(response.body(), protocolVersion, requestId);
                        call.complete(new TransportResult<>(decoded.task(), List.of(evidence(decoded))));
                    } catch (JsonProcessingException | RuntimeException decodeFailure) {
                        call.fail(new A2AClientException(
                                "Failed to decode async task response: " + decodeFailure, decodeFailure));
                    }
                }
            });
        } catch (JsonProcessingException | IOException | InterruptedException failure) {
            call.fail(new A2AClientException("Failed to start async task request: " + failure, failure));
        } catch (RuntimeException failure) {
            call.fail(new A2AClientException("Failed to start async task request: " + failure, failure));
        }
        return call;
    }

    @Override
    public TransportCall<TransportResult<EventKind>> sendInput(SendInputParams request, SpiCallOptions options,
            @Nullable ClientCallContext context) {
        checkNotNullParam("request", request);
        checkNotNullParam("options", options);
        if (!"1.1".equals(protocolVersion)) {
            throw new UnsupportedOperationException("Decoded unary send requires protocol version 1.1");
        }
        AtomicReference<A2AHttpClient.AsyncPostCall> requestFuture = new AtomicReference<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        TransportCall<TransportResult<EventKind>> call = TransportCall.create(() -> {
            cancellationRequested.set(true);
            A2AHttpClient.AsyncPostCall future = requestFuture.get();
            if (future != null) future.cancel();
        });
        try {
            PayloadAndHeaders payloadAndHeaders = applyInterceptors(SEND_MESSAGE_METHOD,
                    ProtoUtils.ToProto.sendInputRequest(request), agentCard, context);
            String requestId = UUID.randomUUID().toString();
            A2AHttpClient.AsyncPostCall future = createPostBuilder(requestUrl(request.tenant()), payloadAndHeaders,
                    SEND_MESSAGE_METHOD, followRedirects, requestId).postAsyncResponse();
            requestFuture.set(future);
            if (cancellationRequested.get()) future.cancel();
            future.response().whenComplete((response, failure) -> {
                if (failure != null) {
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    call.fail(cause instanceof A2AClientException ? cause
                            : new A2AClientException("Failed to send input: " + cause, cause));
                } else if (response == null) {
                    call.fail(new A2AClientException("Failed to send input: empty response"));
                } else if (!response.success()) {
                    int status = response.status();
                    String message = "Request failed with HTTP " + status;
                    call.fail(new A2AClientException(message,
                            new A2AClientHTTPError(status, message, response.body(), response.headers().toMap())));
                } else {
                    try {
                        DecodedEvent decoded = JSONRPCUtils.decodeSendResponse(response.body(), protocolVersion,
                                requestId);
                        List<TaskDecodeEvidence> evidence = decoded.task() == null ? List.of()
                                : List.of(evidence(decoded.task()));
                        call.complete(new TransportResult<>(decoded.value(), evidence));
                    } catch (TaskGenerationMismatchError mismatch) {
                        call.fail(mismatch);
                    } catch (JsonProcessingException | RuntimeException decodeFailure) {
                        call.fail(new A2AClientException(
                                "Failed to decode unary send response: " + decodeFailure, decodeFailure));
                    }
                }
            });
        } catch (JsonProcessingException | IOException | InterruptedException failure) {
            call.fail(new A2AClientException("Failed to start unary send request: " + failure, failure));
        } catch (RuntimeException failure) {
            call.fail(new A2AClientException("Failed to start unary send request: " + failure, failure));
        }
        return call;
    }

    @Override
    public TransportObservation streamInput(SendInputParams request, SpiCallOptions options,
            @Nullable ClientCallContext context) {
        checkNotNullParam("request", request);
        checkNotNullParam("options", options);
        if (!"1.1".equals(protocolVersion)) {
            throw new UnsupportedOperationException("Streaming input requires protocol version 1.1");
        }

        AtomicReference<A2AHttpClient.AsyncSSECall> requestCall = new AtomicReference<>();
        AtomicReference<TransportObservation> observationReference = new AtomicReference<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        TransportObservation observation = TransportObservation.create(options, () -> {
            cancellationRequested.set(true);
            A2AHttpClient.AsyncSSECall call = requestCall.get();
            if (call != null) {
                call.cancel();
            }
            TransportObservation current = observationReference.get();
            if (current != null) {
                activeInputObservations.remove(current);
            }
        });
        observationReference.set(observation);
        activeInputObservations.add(observation);

        try {
            PayloadAndHeaders payloadAndHeaders = applyInterceptors(SEND_STREAMING_MESSAGE_METHOD,
                    ProtoUtils.ToProto.sendInputRequest(request), agentCard, context);
            String requestId = UUID.randomUUID().toString();
            SSEEventListener listener = new SSEEventListener(protocolVersion, requestId, observation);
            CompletableFuture<Void> cancellableFuture = new CompletableFuture<>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    A2AHttpClient.AsyncSSECall call = requestCall.get();
                    if (call != null) {
                        call.cancel();
                    }
                    return super.cancel(mayInterruptIfRunning);
                }
            };
            A2AHttpClient.AsyncSSECall call = createPostBuilder(requestUrl(request.tenant()), payloadAndHeaders,
                    SEND_STREAMING_MESSAGE_METHOD, false, requestId).postAsyncSSECallStatusAware(
                            event -> listener.onMessage(event, cancellableFuture),
                            failure -> listener.onError(failure, cancellableFuture),
                            listener::onComplete);
            requestCall.set(call);
            if (cancellationRequested.get() || observation.completion().toCompletableFuture().isDone()) {
                call.cancel();
            }
            call.completion().whenComplete((ignored, failure) -> {
                activeInputObservations.remove(observation);
                if (failure != null) {
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    listener.onError(cause, cancellableFuture);
                } else {
                    listener.onComplete();
                }
            });
        } catch (IOException | InterruptedException | JsonProcessingException | RuntimeException failure) {
            activeInputObservations.remove(observation);
            observation.fail(new A2AClientException("Failed to start streaming input: " + failure, failure));
        }
        return observation;
    }

    @Override
    public TransportResult<Task> getTaskDecoded(TaskQueryParams request, @Nullable ClientCallContext context)
            throws A2AClientException {
        checkNotNullParam("request", request);
        requireDecodedOneOneRead();
        if (!"1.1".equals(protocolVersion)) {
            throw new UnsupportedOperationException("Decoded task reads require protocol version 1.1");
        }
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_TASK_METHOD, ProtoUtils.ToProto.getTaskRequest(request),
                agentCard, context);
        try {
            String requestId = UUID.randomUUID().toString();
            String body = sendPostRequest(requestUrl(request.tenant()), payloadAndHeaders, GET_TASK_METHOD, requestId);
            DecodedTask decoded = JSONRPCUtils.decodeTaskResponse(body, protocolVersion, requestId);
            return new TransportResult<>(decoded.task(), List.of(evidence(decoded)));
        } catch (A2AClientException failure) {
            throw failure;
        } catch (IOException | InterruptedException | JsonProcessingException failure) {
            throw new A2AClientException("Failed to get decoded task: " + failure, failure);
        } catch (RuntimeException failure) {
            throw new A2AClientException("Failed to decode task response: " + failure, failure);
        }
    }

    @Override
    public TransportResult<ListTasksResult> listTasksDecoded(ListTasksParams request,
            @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        requireDecodedOneOneRead();
        if (!"1.1".equals(protocolVersion)) {
            throw new UnsupportedOperationException("Decoded task reads require protocol version 1.1");
        }
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(LIST_TASK_METHOD, ProtoUtils.ToProto.listTasksParams(request),
                agentCard, context);
        try {
            String requestId = UUID.randomUUID().toString();
            String body = sendPostRequest(requestUrl(request.tenant()), payloadAndHeaders, LIST_TASK_METHOD, requestId);
            DecodedTaskList decoded = JSONRPCUtils.decodeListTasksResponse(body, protocolVersion, requestId);
            List<TaskDecodeEvidence> evidence = decoded.tasks().stream().map(JSONRPCTransport::evidence).toList();
            return new TransportResult<>(decoded.value(), evidence);
        } catch (A2AClientException failure) {
            throw failure;
        } catch (IOException | InterruptedException | JsonProcessingException failure) {
            throw new A2AClientException("Failed to list decoded tasks: " + failure, failure);
        } catch (RuntimeException failure) {
            throw new A2AClientException("Failed to decode task list response: " + failure, failure);
        }
    }

    private void requireDecodedOneOneRead() {
        if (!"1.1".equals(protocolVersion)) {
            throw new UnsupportedOperationException("Decoded task reads require protocol version 1.1");
        }
    }

    private static TaskDecodeEvidence evidence(DecodedTask decoded) {
        return new TaskDecodeEvidence(decoded.task().id(), decoded.notes().skippedTimelineGenerations(),
                decoded.notes().unknownSkipped());
    }

    @Override
    public Task cancelTask(CancelTaskParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(CANCEL_TASK_METHOD, ProtoUtils.ToProto.cancelTaskRequest(request),
                agentCard, context);

        try {
            String httpResponseBody = sendPostRequest(requestUrl(request.tenant()), payloadAndHeaders, CANCEL_TASK_METHOD);
            CancelTaskResponse response = unmarshalResponse(httpResponseBody, CANCEL_TASK_METHOD);
            return response.getResult();
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to cancel task: " + e, e);
        }
    }

    @Override
    public ListTasksResult listTasks(ListTasksParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(LIST_TASK_METHOD, ProtoUtils.ToProto.listTasksParams(request),
                agentCard, context);
        try {
            String httpResponseBody = sendPostRequest(requestUrl(request.tenant()), payloadAndHeaders, LIST_TASK_METHOD);
            ListTasksResponse response = unmarshalResponse(httpResponseBody, LIST_TASK_METHOD);
            return response.getResult();
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to list tasks: " + e, e);
        }
    }

    @Override
    public TaskPushNotificationConfig createTaskPushNotificationConfiguration(TaskPushNotificationConfig request,
                                                                              @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD,
                ProtoUtils.ToProto.createTaskPushNotificationConfigRequest(request), agentCard, context);

        try {
            String httpResponseBody = sendPostRequest(requestUrl(request.tenant()), payloadAndHeaders, SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
            CreateTaskPushNotificationConfigResponse response = unmarshalResponse(httpResponseBody, SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
            return response.getResult();
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to set task push notification config: " + e, e);
        }
    }

    @Override
    public TaskPushNotificationConfig getTaskPushNotificationConfiguration(GetTaskPushNotificationConfigParams request,
                                                                           @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD,
                ProtoUtils.ToProto.getTaskPushNotificationConfigRequest(request), agentCard, context);

        try {
            String httpResponseBody = sendPostRequest(requestUrl(request.tenant()), payloadAndHeaders, GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
            GetTaskPushNotificationConfigResponse response = unmarshalResponse(httpResponseBody, GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
            return response.getResult();
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to get task push notification config: " + e, e);
        }
    }

    @Override
    public ListTaskPushNotificationConfigsResult listTaskPushNotificationConfigurations(
            ListTaskPushNotificationConfigsParams request,
            @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(LIST_TASK_PUSH_NOTIFICATION_CONFIG_METHOD,
                ProtoUtils.ToProto.listTaskPushNotificationConfigsRequest(request), agentCard, context);

        try {
            String httpResponseBody = sendPostRequest(requestUrl(request.tenant()), payloadAndHeaders, LIST_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
            ListTaskPushNotificationConfigsResponse response = unmarshalResponse(httpResponseBody, LIST_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
            return response.getResult();
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to list task push notification configs: " + e, e);
        }
    }

    @Override
    public void deleteTaskPushNotificationConfigurations(DeleteTaskPushNotificationConfigParams request,
                                                         @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(DELETE_TASK_PUSH_NOTIFICATION_CONFIG_METHOD,
                ProtoUtils.ToProto.deleteTaskPushNotificationConfigRequest(request), agentCard, context);

        try {
            String httpResponseBody = sendPostRequest(requestUrl(request.tenant()), payloadAndHeaders, DELETE_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
            DeleteTaskPushNotificationConfigResponse response = unmarshalResponse(httpResponseBody, DELETE_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
            // Response validated (no error), but no result to return
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to delete task push notification configs: " + e, e);
        }
    }

    @Override
    public void subscribeToTask(TaskIdParams request, Consumer<StreamingEventKind> eventConsumer,
                            Consumer<Throwable> errorConsumer, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        checkNotNullParam("eventConsumer", eventConsumer);
        checkNotNullParam("errorConsumer", errorConsumer);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SUBSCRIBE_TO_TASK_METHOD, ProtoUtils.ToProto.subscribeToTaskRequest(request), agentCard, context);

        AtomicReference<CompletableFuture<Void>> ref = new AtomicReference<>();
        SSEEventListener sseEventListener = new SSEEventListener(eventConsumer, errorConsumer);

        try {
            A2AHttpClient.PostBuilder builder = createPostBuilder(
                    requestUrl(request.tenant()), payloadAndHeaders, SUBSCRIBE_TO_TASK_METHOD, false);
            ref.set(builder.postAsyncSSE(
                    event -> sseEventListener.onMessage(event, ref.get()),
                    throwable -> sseEventListener.onError(throwable, ref.get()),
                    () -> {
                        // Signal normal stream completion to error handler (null error means success)
                        sseEventListener.onComplete();
                    }));
        } catch (IOException e) {
            throw new A2AClientException("Failed to send task resubscription request: " + e, e);
        } catch (InterruptedException e) {
            throw new A2AClientException("Task resubscription request timed out: " + e, e);
        } catch (JsonProcessingException e) {
            throw new A2AClientException("Failed to process JSON for task resubscription request: " + e, e);
        }
    }

    @Override
    public AgentCard getExtendedAgentCard(GetExtendedAgentCardParams params, @Nullable ClientCallContext context) throws A2AClientException {
        try {
            PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_EXTENDED_AGENT_CARD_METHOD,
                    ProtoUtils.ToProto.extendedAgentCard(params), agentCard, context);

            try {
                String httpResponseBody = sendPostRequest(requestUrl(params.tenant()), payloadAndHeaders, GET_EXTENDED_AGENT_CARD_METHOD);
                GetExtendedAgentCardResponse response = unmarshalResponse(httpResponseBody, GET_EXTENDED_AGENT_CARD_METHOD);
                return response.getResult();
            } catch (IOException | InterruptedException | JsonProcessingException e) {
                throw new A2AClientException("Failed to get authenticated extended agent card: " + e, e);
            }
        } catch(A2AClientError e){
            throw new A2AClientException("Failed to get agent card: " + e, e);
        }
    }

    @Override
    public void close() {
        for (TransportObservation observation : activeInputObservations) {
            observation.cancel();
        }
    }

    private PayloadAndHeaders applyInterceptors(String methodName, @Nullable Object payload,
                                                @Nullable AgentCard agentCard, @Nullable ClientCallContext clientCallContext) {
        PayloadAndHeaders payloadAndHeaders = new PayloadAndHeaders(payload, ClientProtocolVersion.normalizeHeaders(getHttpHeaders(clientCallContext), protocolVersion));
        if (interceptors != null && ! interceptors.isEmpty()) {
            for (ClientCallInterceptor interceptor : interceptors) {
                payloadAndHeaders = interceptor.intercept(methodName, payloadAndHeaders.getPayload(),
                        payloadAndHeaders.getHeaders(), agentCard, clientCallContext);
                payloadAndHeaders = new PayloadAndHeaders(payloadAndHeaders.getPayload(),
                        ClientProtocolVersion.normalizeHeaders(payloadAndHeaders.getHeaders(), protocolVersion));
            }
        }
        return payloadAndHeaders;
    }

    private String sendPostRequest(String url, PayloadAndHeaders payloadAndHeaders, String method) throws IOException, InterruptedException, JsonProcessingException {
        A2AHttpClient.PostBuilder builder = createPostBuilder(url, payloadAndHeaders, method, followRedirects);
        return sendPostRequest(builder);
    }

    private String sendPostRequest(String url, PayloadAndHeaders payloadAndHeaders, String method, String requestId)
            throws IOException, InterruptedException, JsonProcessingException {
        A2AHttpClient.PostBuilder builder = createPostBuilder(url, payloadAndHeaders, method, followRedirects, requestId);
        return sendPostRequest(builder);
    }

    private String sendPostRequest(A2AHttpClient.PostBuilder builder)
            throws IOException, InterruptedException, JsonProcessingException {
        A2AHttpResponse response = builder.post();
        if (!response.success()) {
            int status = response.status();
            String message = "Request failed with HTTP " + status;
            throw new A2AClientException(message, new A2AClientHTTPError(status, message, response.body(), response.headers().toMap()));
        }
        return response.body();
    }

    private A2AHttpClient.PostBuilder createPostBuilder(
            String url, PayloadAndHeaders payloadAndHeaders, String method, boolean followRedirects)
            throws JsonProcessingException {
        return createPostBuilder(url, payloadAndHeaders, method, followRedirects, UUID.randomUUID().toString());
    }

    private A2AHttpClient.PostBuilder createPostBuilder(
            String url, PayloadAndHeaders payloadAndHeaders, String method, boolean followRedirects, String requestId)
            throws JsonProcessingException {
        A2AHttpClient.PostBuilder postBuilder = httpClient.createPost()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .followRedirects(followRedirects)
                .body(JSONRPCUtils.toJsonRPCRequest(requestId, method, (MessageOrBuilder) payloadAndHeaders.getPayload()));

        if (payloadAndHeaders.getHeaders() != null) {
            for (Map.Entry<String, String> entry : payloadAndHeaders.getHeaders().entrySet()) {
                postBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        return postBuilder;
    }

    /**
     * Unmarshals a JSON-RPC response string into a type-safe response object.
     * <p>
     * This method parses the JSON-RPC response body and returns the appropriate
     * response type based on the method parameter. If the response contains an error,
     * an A2AClientException is thrown.
     *
     * @param <T> the expected response type, must extend JSONRPCResponse
     * @param response the JSON-RPC response body as a string
     * @param method the method name used to determine the response type
     * @return the parsed response object of type T
     * @throws A2AClientException if the response contains an error or parsing fails
     * @throws JsonProcessingException if the JSON cannot be processed
     */
    @SuppressWarnings("unchecked")
    private <T extends A2AResponse<?>> T unmarshalResponse(String response, String method)
            throws A2AClientException, JsonProcessingException {
        A2AResponse<?> value = JSONRPCUtils.parseResponseBody(response, method);
        A2AError error = value.getError();
        if (error != null) {
            throw new A2AClientException(error.getMessage() + (!error.getDetails().isEmpty() ? ": " + error.getDetails() : ""), error);
        }
        // Safe cast: JSONRPCUtils.parseResponseBody returns the correct concrete type based on method
        return (T) value;
    }

    private String requestUrl(@Nullable String requestTenant) {
        if ("1.0".equals(protocolVersion) && (agentInterface.tenant() == null || agentInterface.tenant().isBlank())) {
            return agentInterface.url();
        }
        return Utils.buildBaseUrl(agentInterface, requestTenant);
    }

    private Map<String, String> getHttpHeaders(@Nullable ClientCallContext context) {
        return context != null ? context.getHeaders() : Map.of();
    }
}
