package org.a2aproject.sdk.client.transport.rest;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpClientConfiguration;
import org.a2aproject.sdk.client.http.A2AHttpClientFactory;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.client.http.ServerSentEvent;
import org.a2aproject.sdk.client.transport.rest.sse.SSEEventListener;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientProtocolVersion;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportCall;
import org.a2aproject.sdk.client.transport.spi.TransportEnd;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.grpc.utils.DecodedEvent;
import org.a2aproject.sdk.grpc.utils.DecodedTask;
import org.a2aproject.sdk.grpc.utils.DecodedTaskList;
import org.a2aproject.sdk.grpc.utils.ProtoJsonUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.grpc.utils.StreamDecoder;
import org.a2aproject.sdk.grpc.utils.StreamDecodeResult;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.A2AClientError;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.A2AClientHTTPError;
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
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.util.Utils;
import org.jspecify.annotations.Nullable;

public class RestTransport implements VersionedClientTransport {

    private static final Logger LOGGER = Logger.getLogger(RestTransport.class.getName());
    private final String protocolVersion;
    private final A2AHttpClient httpClient;
    private final AgentInterface agentInterface;
    private @Nullable final List<ClientCallInterceptor> interceptors;
    private final AgentCard agentCard;
    private final boolean followRedirects;
    private final Set<TransportObservation> activeObservations = ConcurrentHashMap.newKeySet();

    /** Selects an advertised REST interface with protocol 1.1. */
    public RestTransport(AgentCard agentCard) {
        this(agentCard, ClientProtocolVersion.DEFAULT);
    }

    public RestTransport(AgentCard agentCard, String protocolVersion) {
        this(null, agentCard, ClientProtocolVersion.selectInterface(agentCard, "HTTP+JSON", protocolVersion), null);
    }

    public RestTransport(@Nullable A2AHttpClient httpClient, AgentCard agentCard,
            AgentInterface agentInterface, @Nullable List<ClientCallInterceptor> interceptors) {
        this(httpClient, agentCard, agentInterface, interceptors, false);
    }

    public RestTransport(@Nullable A2AHttpClient httpClient, AgentCard agentCard,
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
            return Set.of(ClientFeature.ORDINARY, ClientFeature.GUARDED_INPUT, ClientFeature.ARTIFACT_INPUT,
                    ClientFeature.UNKNOWN_VARIANT_SKIP);
        }
        return Set.of(ClientFeature.ORDINARY);
    }

    @Override
    public EventKind sendMessage(MessageSendParams messageSendParams, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("messageSendParams", messageSendParams);
        org.a2aproject.sdk.grpc.SendMessageRequest.Builder builder = org.a2aproject.sdk.grpc.SendMessageRequest.newBuilder(ProtoUtils.ToProto.sendMessageRequest(messageSendParams));
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SEND_MESSAGE_METHOD, builder, agentCard, context);
        try {
            String httpResponseBody = sendPostRequest(Utils.buildBaseUrl(agentInterface, messageSendParams.tenant()) + "/message:send", payloadAndHeaders);
            org.a2aproject.sdk.grpc.SendMessageResponse.Builder responseBuilder = org.a2aproject.sdk.grpc.SendMessageResponse.newBuilder();
            JsonFormat.parser().merge(httpResponseBody, responseBuilder);
            if (responseBuilder.hasMessage()) {
                return ProtoUtils.FromProto.message(responseBuilder.getMessage());
            }
            if (responseBuilder.hasTask()) {
                return ProtoUtils.FromProto.task(responseBuilder.getTask());
            }
            throw new A2AClientException("Failed to send message, wrong response:" + httpResponseBody);
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to send message: " + e, e);
        }
    }

    @Override
    public TransportCall<TransportResult<EventKind>> sendInput(SendInputParams request,
            SpiCallOptions options, @Nullable ClientCallContext context) {
        checkNotNullParam("request", request);
        checkNotNullParam("options", options);
        if (!"1.1".equals(protocolVersion)) {
            throw new UnsupportedOperationException("Decoded unary send requires protocol version 1.1");
        }

        AtomicReference<A2AHttpClient.AsyncPostCall> requestCall = new AtomicReference<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        TransportCall<TransportResult<EventKind>> call = TransportCall.create(() -> {
            cancellationRequested.set(true);
            A2AHttpClient.AsyncPostCall current = requestCall.get();
            if (current != null) {
                current.cancel();
            }
        });
        try {
            PayloadAndHeaders payloadAndHeaders = applyInterceptors(SEND_MESSAGE_METHOD,
                    ProtoUtils.ToProto.sendInputRequest(request), agentCard, context);
            String url = Utils.buildBaseUrl(agentInterface, request.tenant()) + "/message:send";
            A2AHttpClient.AsyncPostCall current = createPostBuilder(url, payloadAndHeaders, false)
                    .postAsyncResponse();
            requestCall.set(current);
            if (cancellationRequested.get()) {
                current.cancel();
            }
            current.response().whenComplete((response, failure) -> {
                if (failure != null) {
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    call.fail(cause instanceof A2AClientException ? cause
                            : new A2AClientException("Failed to send input: " + cause, cause));
                } else if (response == null) {
                    call.fail(new A2AClientException("Failed to send input: empty response"));
                } else if (!response.success()) {
                    call.fail(RestErrorMapper.mapRestError(response));
                } else {
                    try {
                        DecodedEvent decoded = StreamDecoder.decodeSendResponseJson(response.body(), protocolVersion);
                        List<TaskDecodeEvidence> evidence = decoded.task() == null ? List.of()
                                : List.of(evidence(decoded.task()));
                        call.complete(new TransportResult<>(decoded.value(), evidence));
                    } catch (RuntimeException decodeFailure) {
                        call.fail(new A2AClientException(
                                "Failed to decode unary send response: " + decodeFailure, decodeFailure));
                    }
                }
            });
        } catch (IOException | InterruptedException | JsonProcessingException failure) {
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
                activeObservations.remove(current);
            }
        });
        observationReference.set(observation);
        activeObservations.add(observation);

        try {
            PayloadAndHeaders payloadAndHeaders = applyInterceptors(SEND_STREAMING_MESSAGE_METHOD,
                    ProtoUtils.ToProto.sendInputRequest(request), agentCard, context);
            SSEEventListener listener = new SSEEventListener(protocolVersion, observation);
            CompletableFuture<Void> cancellableFuture = cancellableSseFuture(requestCall);
            A2AHttpClient.AsyncSSECall call = createPostBuilder(
                    Utils.buildBaseUrl(agentInterface, request.tenant()) + "/message:stream", payloadAndHeaders, false)
                    .postAsyncSSECallStatusAware(event -> listener.onMessage(event, cancellableFuture),
                            failure -> listener.onError(mapSseFailure(failure), cancellableFuture), listener::onComplete);
            requestCall.set(call);
            if (cancellationRequested.get() || observation.completion().toCompletableFuture().isDone()) {
                call.cancel();
            }
            call.completion().whenComplete((ignored, failure) -> {
                activeObservations.remove(observation);
                if (failure != null) {
                    listener.onError(mapSseFailure(failure), cancellableFuture);
                } else {
                    listener.onComplete();
                }
            });
        } catch (IOException | InterruptedException | JsonProcessingException | RuntimeException failure) {
            activeObservations.remove(observation);
            observation.fail(new A2AClientException("Failed to start streaming input: " + failure, failure));
        }
        return observation;
    }

    private static CompletableFuture<Void> cancellableSseFuture(
            AtomicReference<A2AHttpClient.AsyncSSECall> requestCall) {
        return new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                A2AHttpClient.AsyncSSECall call = requestCall.get();
                if (call != null) {
                    call.cancel();
                }
                return super.cancel(mayInterruptIfRunning);
            }
        };
    }

    private static Throwable mapSseFailure(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause.getCause() instanceof A2AClientHTTPError httpError && httpError.getResponseBody() != null) {
            return RestErrorMapper.mapRestError(httpError.getResponseBody(), httpError.getCode(),
                    httpError.getResponseHeaders());
        }
        return cause;
    }

    @Override
    public void sendMessageStreaming(MessageSendParams messageSendParams, Consumer<StreamingEventKind> eventConsumer, Consumer<Throwable> errorConsumer, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", messageSendParams);
        checkNotNullParam("eventConsumer", eventConsumer);
        checkNotNullParam("messageSendParams", messageSendParams);
        org.a2aproject.sdk.grpc.SendMessageRequest.Builder builder = org.a2aproject.sdk.grpc.SendMessageRequest.newBuilder(ProtoUtils.ToProto.sendMessageRequest(messageSendParams));
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SEND_STREAMING_MESSAGE_METHOD, builder, agentCard, context);
        AtomicReference<CompletableFuture<Void>> ref = new AtomicReference<>();
        SSEEventListener sseEventListener = new SSEEventListener(eventConsumer, errorConsumer);
        try {
            A2AHttpClient.PostBuilder postBuilder = createPostBuilder(
                    Utils.buildBaseUrl(agentInterface, messageSendParams.tenant()) + "/message:stream",
                    payloadAndHeaders,
                    false);
            ref.set(postBuilder.postAsyncSSE(
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
    public Task getTask(TaskQueryParams taskQueryParams, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("taskQueryParams", taskQueryParams);
        org.a2aproject.sdk.grpc.GetTaskRequest.Builder builder = org.a2aproject.sdk.grpc.GetTaskRequest.newBuilder();
        builder.setId(taskQueryParams.id());
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_TASK_METHOD, builder, agentCard, context);
        try {
            StringBuilder url = new StringBuilder(Utils.buildBaseUrl(agentInterface, taskQueryParams.tenant()));
            if (taskQueryParams.historyLength() != null && taskQueryParams.historyLength() > 0) {
                url.append(String.format("/tasks/%s?historyLength=%d", taskQueryParams.id(), taskQueryParams.historyLength()));
            } else {
                url.append(String.format("/tasks/%1s", taskQueryParams.id()));
            }
            A2AHttpClient.GetBuilder getBuilder = httpClient.createGet().url(url.toString());
            addStandardHeaders(getBuilder, payloadAndHeaders);
            A2AHttpResponse response = getBuilder.get();
            if (!response.success()) {
                throw RestErrorMapper.mapRestError(response);
            }
            String httpResponseBody = response.body();
            org.a2aproject.sdk.grpc.Task.Builder responseBuilder = org.a2aproject.sdk.grpc.Task.newBuilder();
            JsonFormat.parser().merge(httpResponseBody, responseBuilder);
            return ProtoUtils.FromProto.task(responseBuilder);
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new A2AClientException("Failed to get task: " + e, e);
        }
    }

    @Override
    public TransportResult<Task> getTaskDecoded(TaskQueryParams request, @Nullable ClientCallContext context)
            throws A2AClientException {
        checkNotNullParam("request", request);
        requireDecodedOneOneRead();
        requireSupportedTaskQuery(request);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_TASK_METHOD,
                ProtoUtils.ToProto.getTaskRequest(request), agentCard, context);
        try {
            A2AHttpClient.GetBuilder getBuilder = httpClient.createGet().url(buildDecodedGetTaskUrl(request));
            addStandardHeaders(getBuilder, payloadAndHeaders);
            A2AHttpResponse response = getBuilder.get();
            if (!response.success()) {
                throw RestErrorMapper.mapRestError(response);
            }
            DecodedTask decoded = StreamDecoder.decodeTaskJson(response.body(), protocolVersion);
            return new TransportResult<>(decoded.task(), List.of(evidence(decoded)));
        } catch (A2AClientException failure) {
            throw failure;
        } catch (IOException | InterruptedException failure) {
            throw new A2AClientException("Failed to get decoded task: " + failure, failure);
        } catch (RuntimeException failure) {
            throw new A2AClientException("Failed to decode task response: " + failure, failure);
        }
    }

    @Override
    public TransportCall<TransportResult<Task>> getTaskAsync(TaskQueryParams request, SpiCallOptions options,
            @Nullable ClientCallContext context) {
        checkNotNullParam("request", request);
        checkNotNullParam("options", options);
        requireDecodedOneOneRead();
        requireSupportedTaskQuery(request);

        AtomicReference<A2AHttpClient.AsyncGetCall> requestCall = new AtomicReference<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        TransportCall<TransportResult<Task>> call = TransportCall.create(() -> {
            cancellationRequested.set(true);
            A2AHttpClient.AsyncGetCall current = requestCall.get();
            if (current != null) {
                current.cancel();
            }
        });
        try {
            PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_TASK_METHOD,
                    ProtoUtils.ToProto.getTaskRequest(request), agentCard, context);
            A2AHttpClient.GetBuilder getBuilder = httpClient.createGet().url(buildDecodedGetTaskUrl(request));
            addStandardHeaders(getBuilder, payloadAndHeaders);
            A2AHttpClient.AsyncGetCall current = getBuilder.getAsyncResponse();
            requestCall.set(current);
            if (cancellationRequested.get()) {
                current.cancel();
            }
            current.response().whenComplete((response, failure) -> {
                if (failure != null) {
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    call.fail(new A2AClientException("Failed to get decoded task: " + cause, cause));
                } else if (response == null) {
                    call.fail(new A2AClientException("Failed to get decoded task: empty response"));
                } else if (!response.success()) {
                    call.fail(RestErrorMapper.mapRestError(response));
                } else {
                    try {
                        DecodedTask decoded = StreamDecoder.decodeTaskJson(response.body(), protocolVersion);
                        call.complete(new TransportResult<>(decoded.task(), List.of(evidence(decoded))));
                    } catch (RuntimeException decodeFailure) {
                        call.fail(new A2AClientException(
                                "Failed to decode async task response: " + decodeFailure, decodeFailure));
                    }
                }
            });
        } catch (IOException | InterruptedException failure) {
            call.fail(new A2AClientException("Failed to start async task request: " + failure, failure));
        } catch (RuntimeException failure) {
            call.fail(new A2AClientException("Failed to start async task request: " + failure, failure));
        }
        return call;
    }

    @Override
    public Task cancelTask(CancelTaskParams taskIdParams, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("taskIdParams", taskIdParams);
        org.a2aproject.sdk.grpc.CancelTaskRequest.Builder builder = org.a2aproject.sdk.grpc.CancelTaskRequest.newBuilder(ProtoUtils.ToProto.cancelTaskRequest(taskIdParams));
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(CANCEL_TASK_METHOD, builder, agentCard, context);
        try {
            String httpResponseBody = sendPostRequest(Utils.buildBaseUrl(agentInterface, taskIdParams.tenant()) + String.format("/tasks/%1s:cancel", taskIdParams.id()), payloadAndHeaders);
            org.a2aproject.sdk.grpc.Task.Builder responseBuilder = org.a2aproject.sdk.grpc.Task.newBuilder();
            JsonFormat.parser().merge(httpResponseBody, responseBuilder);
            return ProtoUtils.FromProto.task(responseBuilder);
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to cancel task: " + e, e);
        }
    }

    @Override
    public ListTasksResult listTasks(ListTasksParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        org.a2aproject.sdk.grpc.ListTasksRequest.Builder builder = org.a2aproject.sdk.grpc.ListTasksRequest.newBuilder();
        if (request.contextId() != null) {
            builder.setContextId(request.contextId());
        }
        if (request.status() != null) {
            builder.setStatus(ProtoUtils.ToProto.taskState(request.status()));
        }
        if (request.pageSize() != null) {
            builder.setPageSize(request.pageSize());
        }
        if (request.pageToken() != null) {
            builder.setPageToken(request.pageToken());
        }
        if (request.historyLength() != null) {
            builder.setHistoryLength(request.historyLength());
        }
        if (request.tenant() != null) {
            builder.setTenant(request.tenant());
        }
        if (request.includeArtifacts() != null && request.includeArtifacts()) {
            builder.setIncludeArtifacts(true);
        }
        if (request.statusTimestampAfter() != null) {
            builder.setStatusTimestampAfter(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(request.statusTimestampAfter().getEpochSecond())
                    .setNanos(request.statusTimestampAfter().getNano())
                    .build());
        }

        PayloadAndHeaders payloadAndHeaders = applyInterceptors(LIST_TASK_METHOD, builder, agentCard, context);

        try {
            // Build query string
            StringBuilder urlBuilder = new StringBuilder(Utils.buildBaseUrl(agentInterface, request.tenant()));
            urlBuilder.append("/tasks");
            String queryParams = buildListTasksQueryString(request, false);
            if (!queryParams.isEmpty()) {
                urlBuilder.append("?").append(queryParams);
            }

            A2AHttpClient.GetBuilder getBuilder = httpClient.createGet().url(urlBuilder.toString());
            addStandardHeaders(getBuilder, payloadAndHeaders);
            A2AHttpResponse response = getBuilder.get();
            if (!response.success()) {
                throw RestErrorMapper.mapRestError(response);
            }
            String httpResponseBody = response.body();
            org.a2aproject.sdk.grpc.ListTasksResponse.Builder responseBuilder = org.a2aproject.sdk.grpc.ListTasksResponse.newBuilder();
            JsonFormat.parser().merge(httpResponseBody, responseBuilder);

            return new ListTasksResult(
                    responseBuilder.getTasksList().stream()
                            .map(ProtoUtils.FromProto::task)
                            .toList(),
                    responseBuilder.getTotalSize(),
                    responseBuilder.getTasksCount(),
                    responseBuilder.getNextPageToken().isEmpty() ? null : responseBuilder.getNextPageToken()
            );
        } catch (IOException | InterruptedException e) {
            throw new A2AClientException("Failed to list tasks: " + e, e);
        }
    }

    @Override
    public TransportResult<ListTasksResult> listTasksDecoded(ListTasksParams request,
            @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        requireDecodedOneOneRead();
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(LIST_TASK_METHOD,
                ProtoUtils.ToProto.listTasksParams(request), agentCard, context);
        try {
            StringBuilder urlBuilder = new StringBuilder(Utils.buildBaseUrl(agentInterface, request.tenant()));
            urlBuilder.append("/tasks");
            String queryParams = buildListTasksQueryString(request, true);
            if (!queryParams.isEmpty()) {
                urlBuilder.append("?").append(queryParams);
            }
            A2AHttpClient.GetBuilder getBuilder = httpClient.createGet().url(urlBuilder.toString());
            addStandardHeaders(getBuilder, payloadAndHeaders);
            A2AHttpResponse response = getBuilder.get();
            if (!response.success()) {
                throw RestErrorMapper.mapRestError(response);
            }
            DecodedTaskList decoded = StreamDecoder.decodeTaskListJson(response.body(), protocolVersion);
            List<TaskDecodeEvidence> evidence = decoded.tasks().stream().map(RestTransport::evidence).toList();
            return new TransportResult<>(decoded.value(), evidence);
        } catch (A2AClientException failure) {
            throw failure;
        } catch (IOException | InterruptedException failure) {
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

    private static void requireSupportedTaskQuery(TaskQueryParams request) {
        if (request.currentGeneration() != null) {
            throw new UnsupportedOperationException(
                    "REST task reads do not support the currentGeneration query parameter");
        }
    }

    private String buildDecodedGetTaskUrl(TaskQueryParams request) {
        StringBuilder url = new StringBuilder(Utils.buildBaseUrl(agentInterface, request.tenant()));
        url.append(String.format("/tasks/%1s", request.id()));
        java.util.List<String> queryParts = new java.util.ArrayList<>();
        if (request.historyLength() != null) {
            queryParts.add("historyLength=" + request.historyLength());
        }
        if (request.timelineLength() != null) {
            queryParts.add("timelineLength=" + request.timelineLength());
        }
        if (!queryParts.isEmpty()) {
            url.append("?").append(String.join("&", queryParts));
        }
        return url.toString();
    }

    private static TaskDecodeEvidence evidence(DecodedTask decoded) {
        return new TaskDecodeEvidence(decoded.task().id(), decoded.notes().skippedTimelineGenerations(),
                decoded.notes().unknownSkipped());
    }

    
    private String buildListTasksQueryString(ListTasksParams request, boolean includeTimelineLength) {
        java.util.List<String> queryParts = new java.util.ArrayList<>();
        if (request.contextId() != null) {
            queryParts.add("contextId=" + URLEncoder.encode(request.contextId(), StandardCharsets.UTF_8));
        }
        if (request.status() != null) {
            queryParts.add("status=" + URLEncoder.encode(request.status().toString(), StandardCharsets.UTF_8));
        }
        if (request.pageSize() != null) {
            queryParts.add("pageSize=" + request.pageSize());
        }
        if (request.pageToken() != null) {
            queryParts.add("pageToken=" + URLEncoder.encode(request.pageToken(), StandardCharsets.UTF_8));
        }
        if (request.historyLength() != null) {
            queryParts.add("historyLength=" + request.historyLength());
        }
        if (includeTimelineLength && request.timelineLength() != null) {
            queryParts.add("timelineLength=" + request.timelineLength());
        }
        if (request.statusTimestampAfter() != null) {
            queryParts.add("statusTimestampAfter=" + URLEncoder.encode(
                    request.statusTimestampAfter().toString(), StandardCharsets.UTF_8));
        }
        if (request.includeArtifacts() != null && request.includeArtifacts()) {
            queryParts.add("includeArtifacts=true");
        }
        return String.join("&", queryParts);
    }

    private String buildListPushNotificationConfigsQueryString(ListTaskPushNotificationConfigsParams request) {
        java.util.List<String> queryParts = new java.util.ArrayList<>();
        if (request.pageSize() > 0) {
            queryParts.add("pageSize=" + request.pageSize());
        }
        if (request.pageToken() != null && !request.pageToken().isEmpty()) {
            queryParts.add("pageToken=" + URLEncoder.encode(request.pageToken(), StandardCharsets.UTF_8));
        }
        return String.join("&", queryParts);
    }

    @Override
    public TaskPushNotificationConfig createTaskPushNotificationConfiguration(TaskPushNotificationConfig request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        org.a2aproject.sdk.grpc.TaskPushNotificationConfig.Builder builder
                = ProtoUtils.ToProto.taskPushNotificationConfig(request).toBuilder();
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD, builder, agentCard, context);
        try {
            String httpResponseBody = sendPostRequest(Utils.buildBaseUrl(agentInterface, request.tenant()) + String.format("/tasks/%1s/pushNotificationConfigs", request.taskId()), payloadAndHeaders);
            org.a2aproject.sdk.grpc.TaskPushNotificationConfig.Builder responseBuilder = org.a2aproject.sdk.grpc.TaskPushNotificationConfig.newBuilder();
            JsonFormat.parser().merge(httpResponseBody, responseBuilder);
            return ProtoUtils.FromProto.taskPushNotificationConfig(responseBuilder);
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to set task push notification config: " + e, e);
        }
    }

    @Override
    public TaskPushNotificationConfig getTaskPushNotificationConfiguration(GetTaskPushNotificationConfigParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        org.a2aproject.sdk.grpc.GetTaskPushNotificationConfigRequest.Builder builder
                = org.a2aproject.sdk.grpc.GetTaskPushNotificationConfigRequest.newBuilder();
        StringBuilder url = new StringBuilder(Utils.buildBaseUrl(agentInterface, request.tenant()));
        String configId = request.id();
        if (configId != null && !configId.isEmpty()) {
            builder.setId(configId).setTaskId(request.taskId());
            url.append(String.format("/tasks/%1s/pushNotificationConfigs/%2s", request.taskId(), configId));
        } else {
            // Use trailing slash to distinguish GET from LIST
            builder.setTaskId(request.taskId());
            url.append(String.format("/tasks/%1s/pushNotificationConfigs/", request.taskId()));
        }
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD, builder,
                agentCard, context);
        try {
            A2AHttpClient.GetBuilder getBuilder = httpClient.createGet().url(url.toString());
            addStandardHeaders(getBuilder, payloadAndHeaders);
            A2AHttpResponse response = getBuilder.get();
            if (!response.success()) {
                throw RestErrorMapper.mapRestError(response);
            }
            String httpResponseBody = response.body();
            org.a2aproject.sdk.grpc.TaskPushNotificationConfig.Builder responseBuilder = org.a2aproject.sdk.grpc.TaskPushNotificationConfig.newBuilder();
            JsonFormat.parser().merge(httpResponseBody, responseBuilder);
            return ProtoUtils.FromProto.taskPushNotificationConfig(responseBuilder);
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new A2AClientException("Failed to get push notifications: " + e, e);
        }
    }

    @Override
    public ListTaskPushNotificationConfigsResult listTaskPushNotificationConfigurations(ListTaskPushNotificationConfigsParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        org.a2aproject.sdk.grpc.ListTaskPushNotificationConfigsRequest.Builder builder
                = org.a2aproject.sdk.grpc.ListTaskPushNotificationConfigsRequest.newBuilder();
        builder.setTaskId(request.id());
        if (request.pageSize() > 0) {
            builder.setPageSize(request.pageSize());
        }
        if (request.pageToken() != null && !request.pageToken().isEmpty()) {
            builder.setPageToken(request.pageToken());
        }
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(LIST_TASK_PUSH_NOTIFICATION_CONFIG_METHOD, builder,
                agentCard, context);
        try {
            StringBuilder urlBuilder = new StringBuilder(Utils.buildBaseUrl(agentInterface, request.tenant()));
            urlBuilder.append(String.format("/tasks/%1s/pushNotificationConfigs", request.id()));
            String queryString = buildListPushNotificationConfigsQueryString(request);
            if (!queryString.isEmpty()) {
                urlBuilder.append("?").append(queryString);
            }
            A2AHttpClient.GetBuilder getBuilder = httpClient.createGet().url(urlBuilder.toString());
            addStandardHeaders(getBuilder, payloadAndHeaders);
            A2AHttpResponse response = getBuilder.get();
            if (!response.success()) {
                throw RestErrorMapper.mapRestError(response);
            }
            String httpResponseBody = response.body();
            org.a2aproject.sdk.grpc.ListTaskPushNotificationConfigsResponse.Builder responseBuilder = org.a2aproject.sdk.grpc.ListTaskPushNotificationConfigsResponse.newBuilder();
            JsonFormat.parser().merge(httpResponseBody, responseBuilder);
            return ProtoUtils.FromProto.listTaskPushNotificationConfigsResult(responseBuilder);
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new A2AClientException("Failed to list push notifications: " + e, e);
        }
    }

    @Override
    public void deleteTaskPushNotificationConfigurations(DeleteTaskPushNotificationConfigParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        org.a2aproject.sdk.grpc.DeleteTaskPushNotificationConfigRequestOrBuilder builder = org.a2aproject.sdk.grpc.DeleteTaskPushNotificationConfigRequest.newBuilder();
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(DELETE_TASK_PUSH_NOTIFICATION_CONFIG_METHOD, builder,
                agentCard, context);
        try {
            String url = Utils.buildBaseUrl(agentInterface, request.tenant()) + String.format("/tasks/%1s/pushNotificationConfigs/%2s", request.taskId(), request.id());
            A2AHttpClient.DeleteBuilder deleteBuilder = httpClient.createDelete().url(url);
            addStandardHeaders(deleteBuilder, payloadAndHeaders);
            A2AHttpResponse response = deleteBuilder.delete();
            if (!response.success()) {
                throw RestErrorMapper.mapRestError(response);
            }
        } catch (A2AClientException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new A2AClientException("Failed to delete push notification config: " + e, e);
        }
    }

    @Override
    public void subscribeToTask(TaskIdParams request, Consumer<StreamingEventKind> eventConsumer,
            Consumer<Throwable> errorConsumer, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        if ("1.1".equals(protocolVersion)) {
            subscribeToTaskOneOne(request, eventConsumer, errorConsumer, context);
            return;
        }
        org.a2aproject.sdk.grpc.SubscribeToTaskRequest.Builder builder = org.a2aproject.sdk.grpc.SubscribeToTaskRequest.newBuilder();
        builder.setId(request.id());
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SUBSCRIBE_TO_TASK_METHOD, builder,
                agentCard, context);
        AtomicReference<CompletableFuture<Void>> ref = new AtomicReference<>();
        SSEEventListener sseEventListener = new SSEEventListener(eventConsumer, errorConsumer);
        try {
            String url = Utils.buildBaseUrl(agentInterface, request.tenant()) + String.format("/tasks/%1s:subscribe", request.id());
            Consumer<ServerSentEvent> messageConsumer = event -> sseEventListener.onMessage(event, ref.get());
            Consumer<Throwable> failureConsumer = throwable -> sseEventListener.onError(throwable, ref.get());
            A2AHttpClient.PostBuilder postBuilder = createPostBuilder(url, payloadAndHeaders, false);
            ref.set(postBuilder.postAsyncSSE(messageConsumer, failureConsumer, sseEventListener::onComplete));
        } catch (IOException e) {
            throw new A2AClientException("Failed to send streaming message request: " + e, e);
        } catch (InterruptedException e) {
            throw new A2AClientException("Send streaming message request timed out: " + e, e);
        } catch (JsonProcessingException e) {
            throw new A2AClientException("Failed to process JSON for streaming message request: " + e, e);
        }
    }

    private void subscribeToTaskOneOne(TaskIdParams request, Consumer<StreamingEventKind> eventConsumer,
            Consumer<Throwable> errorConsumer, @Nullable ClientCallContext context) throws A2AClientException {
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SUBSCRIBE_TO_TASK_METHOD,
                ProtoUtils.ToProto.subscribeToTaskRequest(request), agentCard, context);
        String url = Utils.buildBaseUrl(agentInterface, request.tenant()) + "/tasks/" + request.id() + ":subscribe";
        AtomicReference<A2AHttpClient.AsyncSSECall> requestCall = new AtomicReference<>();
        AtomicReference<TransportObservation> observationReference = new AtomicReference<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> {
            cancellationRequested.set(true);
            A2AHttpClient.AsyncSSECall current = requestCall.get();
            if (current != null) {
                current.cancel();
            }
            TransportObservation active = observationReference.get();
            if (active != null) {
                activeObservations.remove(active);
            }
        });
        observationReference.set(observation);
        activeObservations.add(observation);

        try {
            SSEEventListener listener = new SSEEventListener(protocolVersion, observation);
            CompletableFuture<Void> cancellableFuture = cancellableSseFuture(requestCall);
            A2AHttpClient.GetBuilder getBuilder = httpClient.createGet().url(url);
            addStandardHeaders(getBuilder, payloadAndHeaders);
            A2AHttpClient.AsyncSSECall call = getBuilder.getAsyncSSECallStatusAware(
                    event -> listener.onMessage(event, cancellableFuture),
                    failure -> listener.onError(mapSseFailure(failure), cancellableFuture), listener::onComplete);
            requestCall.set(call);
            if (cancellationRequested.get() || observation.completion().toCompletableFuture().isDone()) {
                call.cancel();
            }
            observation.frames().subscribe(new Flow.Subscriber<>() {
                private Flow.@Nullable Subscription subscription;
                private final AtomicBoolean terminalCallbackDelivered = new AtomicBoolean();

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(TransportFrame frame) {
                    if (frame.event() != null) {
                        eventConsumer.accept(frame.event());
                    } else if (frame.streamError() != null
                            && terminalCallbackDelivered.compareAndSet(false, true)) {
                        String message = frame.streamError().message() == null
                                ? "Task subscription rejected" : frame.streamError().message();
                        errorConsumer.accept(new A2AClientException(message));
                        if (subscription != null) {
                            subscription.cancel();
                        }
                    }
                }

                @Override
                public void onError(Throwable failure) {
                    if (terminalCallbackDelivered.compareAndSet(false, true)) {
                        errorConsumer.accept(failure);
                    }
                }

                @Override
                public void onComplete() {
                    if (observation.completion().toCompletableFuture().join().kind() == TransportEnd.Kind.COMPLETED
                            && terminalCallbackDelivered.compareAndSet(false, true)) {
                        errorConsumer.accept(null);
                    }
                }
            });
            call.completion().whenComplete((ignored, failure) -> {
                activeObservations.remove(observation);
                if (failure != null) {
                    listener.onError(mapSseFailure(failure), cancellableFuture);
                } else {
                    listener.onComplete();
                }
            });
        } catch (IOException | InterruptedException | RuntimeException failure) {
            activeObservations.remove(observation);
            observation.fail(new A2AClientException("Failed to subscribe to task: " + failure, failure));
        }
    }

    @Override
    public AgentCard getExtendedAgentCard(GetExtendedAgentCardParams params, @Nullable ClientCallContext context) throws A2AClientException {
        try {
            PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_EXTENDED_AGENT_CARD_METHOD, null, agentCard, context);
            String url = Utils.buildBaseUrl(agentInterface, params.tenant()) + "/extendedAgentCard";
            A2AHttpClient.GetBuilder getBuilder = httpClient.createGet().url(url);
            addStandardHeaders(getBuilder, payloadAndHeaders);
            A2AHttpResponse response = getBuilder.get();
            if (!response.success()) {
                throw RestErrorMapper.mapRestError(response);
            }
            String httpResponseBody = response.body();
            return JsonUtil.fromJson(httpResponseBody, AgentCard.class);
        } catch (IOException | InterruptedException | JsonProcessingException e) {
            throw new A2AClientException("Failed to get authenticated extended agent card: " + e, e);
        } catch (A2AClientError e) {
            throw new A2AClientException("Failed to get agent card: " + e, e);
        }
    }

    @Override
    public void close() {
        for (TransportObservation observation : activeObservations) {
            observation.cancel();
        }
    }

    private PayloadAndHeaders applyInterceptors(String methodName, @Nullable MessageOrBuilder payload,
            AgentCard agentCard, @Nullable ClientCallContext clientCallContext) {
        PayloadAndHeaders payloadAndHeaders = new PayloadAndHeaders(payload, ClientProtocolVersion.normalizeHeaders(getHttpHeaders(clientCallContext), protocolVersion));
        if (interceptors != null && !interceptors.isEmpty()) {
            for (ClientCallInterceptor interceptor : interceptors) {
                payloadAndHeaders = interceptor.intercept(methodName, payloadAndHeaders.getPayload(),
                        payloadAndHeaders.getHeaders(), agentCard, clientCallContext);
                payloadAndHeaders = new PayloadAndHeaders(payloadAndHeaders.getPayload(),
                        ClientProtocolVersion.normalizeHeaders(payloadAndHeaders.getHeaders(), protocolVersion));
            }
        }
        return payloadAndHeaders;
    }

    private String sendPostRequest(String url, PayloadAndHeaders payloadAndHeaders) throws IOException, InterruptedException, JsonProcessingException {
        MessageOrBuilder payload = (MessageOrBuilder) payloadAndHeaders.getPayload();
        String body = payload != null ? ProtoJsonUtils.toJson(JsonFormat.printer(), payload) : "";
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(body);
        }
        A2AHttpClient.PostBuilder builder = buildPost(url, payloadAndHeaders, body, followRedirects);
        A2AHttpResponse response = builder.post();
        if (!response.success()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Error on POST processing " + body);
            }
            throw RestErrorMapper.mapRestError(response);
        }
        return response.body();
    }

    private A2AHttpClient.PostBuilder createPostBuilder(
            String url, PayloadAndHeaders payloadAndHeaders, boolean followRedirects)
            throws JsonProcessingException, InvalidProtocolBufferException {
        String body = ProtoJsonUtils.toJson(JsonFormat.printer(), (MessageOrBuilder) payloadAndHeaders.getPayload());
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(body);
        }
        return buildPost(url, payloadAndHeaders, body, followRedirects);
    }

    private A2AHttpClient.PostBuilder buildPost(
            String url, PayloadAndHeaders payloadAndHeaders, String body, boolean followRedirects)
            throws JsonProcessingException {
        A2AHttpClient.PostBuilder postBuilder = httpClient.createPost()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .followRedirects(followRedirects)
                .body(body);

        if (payloadAndHeaders.getHeaders() != null) {
            for (Map.Entry<String, String> entry : payloadAndHeaders.getHeaders().entrySet()) {
                postBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return postBuilder;
    }

    private static <T extends A2AHttpClient.Builder<T>> void addStandardHeaders(T builder, PayloadAndHeaders payloadAndHeaders) {
        if (payloadAndHeaders.getHeaders() != null) {
            for (Map.Entry<String, String> entry : payloadAndHeaders.getHeaders().entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    private Map<String, String> getHttpHeaders(@Nullable ClientCallContext context) {
        return context != null ? context.getHeaders() : Map.of();
    }
}
