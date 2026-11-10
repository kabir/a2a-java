package org.a2aproject.sdk.client.transport.grpc;

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

import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientProtocolVersion;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.A2AServiceGrpc.A2AServiceBlockingV2Stub;
import org.a2aproject.sdk.grpc.A2AServiceGrpc.A2AServiceStub;
import org.a2aproject.sdk.grpc.GetExtendedAgentCardRequest;
import org.a2aproject.sdk.grpc.utils.ProtoUtils.FromProto;
import org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.A2AClientException;
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
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.util.Utils;
import org.jspecify.annotations.Nullable;

public class GrpcTransport implements VersionedClientTransport {

    private final String protocolVersion;
    private final OwnedGrpcChannel ownedChannel;
    private final A2AServiceBlockingV2Stub blockingStub;
    private final A2AServiceStub asyncStub;
    private final @Nullable List<ClientCallInterceptor> interceptors;
    private final AgentCard agentCard;
    private final String agentTenant;

    /** Creates a transport using protocol 1.1. Use the version overload to select 1.0. */
    public GrpcTransport(Channel channel, AgentCard agentCard) {
        this(channel, agentCard, ClientProtocolVersion.DEFAULT);
    }

    public GrpcTransport(Channel channel, AgentCard agentCard, String protocolVersion) {
        this(channel, ClientProtocolVersion.selectInterface(agentCard, "GRPC", protocolVersion), agentCard, null);
    }

    public GrpcTransport(Channel channel, AgentCard agentCard, @Nullable String agentTenant, @Nullable List<ClientCallInterceptor> interceptors) {
        this(channel, agentCard, agentTenant, interceptors, ClientProtocolVersion.DEFAULT);
    }

    public GrpcTransport(Channel channel, AgentCard agentCard, @Nullable String agentTenant,
            @Nullable List<ClientCallInterceptor> interceptors, String protocolVersion) {
        this(channel, selectTenantInterface(agentCard, agentTenant, protocolVersion), agentCard, interceptors);
    }

    /** Creates a transport using the supplied descriptor without selecting another card interface. */
    public static GrpcTransport forInterface(Channel channel, AgentCard agentCard, AgentInterface agentInterface,
            @Nullable List<ClientCallInterceptor> interceptors) {
        return new GrpcTransport(channel, agentInterface, agentCard, interceptors);
    }

    /** Creates a dedicated SDK-owned channel using protocol 1.1. */
    public static GrpcTransport createManaged(GrpcManagedChannelOptions options, AgentCard agentCard) {
        return createManaged(options, agentCard, ClientProtocolVersion.DEFAULT);
    }

    /** Creates a dedicated SDK-owned channel for the exact selected card interface. */
    public static GrpcTransport createManaged(GrpcManagedChannelOptions options, AgentCard agentCard, String protocolVersion) {
        checkNotNullParam("options", options);
        AgentInterface selected = ClientProtocolVersion.selectInterface(agentCard, "GRPC", protocolVersion);
        Utils.validateTenant(selected.tenant());
        Channel channel = OwnedGrpcChannel.factory(options).apply(selected.url());
        return new GrpcTransport(channel, selected, agentCard, null);
    }

    private GrpcTransport(Channel channel, AgentInterface agentInterface, AgentCard agentCard,
            @Nullable List<ClientCallInterceptor> interceptors) {
        this.protocolVersion = ClientProtocolVersion.validate(agentInterface.protocolVersion());
        checkNotNullParam("channel", channel);
        checkNotNullParam("agentCard", agentCard);
        Utils.validateTenant(agentInterface.tenant());
        this.ownedChannel = OwnedGrpcChannel.claim(channel, agentInterface.url(), protocolVersion);
        this.asyncStub = A2AServiceGrpc.newStub(ownedChannel).withOption(OwnedGrpcChannel.VERSION_OPTION, protocolVersion);
        this.blockingStub = A2AServiceGrpc.newBlockingV2Stub(ownedChannel).withOption(OwnedGrpcChannel.VERSION_OPTION, protocolVersion);
        this.agentCard = agentCard;
        this.interceptors = interceptors;
        Utils.validateTenant(agentInterface.tenant());
        this.agentTenant = normalizeTenant(agentInterface.tenant());
    }

    private static AgentInterface selectTenantInterface(AgentCard card, @Nullable String tenant, String protocolVersion) {
        ClientProtocolVersion.validate(protocolVersion);
        Utils.validateTenant(tenant);
        String selectedTenant = normalizeTenant(tenant);
        return card.supportedInterfaces().stream()
                .filter(iface -> "GRPC".equals(iface.protocolBinding()) && protocolVersion.equals(iface.protocolVersion())
                        && selectedTenant.equals(normalizeTenant(iface.tenant())))
                .findFirst().orElseThrow(() -> new A2AClientException(
                        "No compatible GRPC interface for protocol version " + protocolVersion + " and tenant " + selectedTenant));
    }

    private static String normalizeTenant(@Nullable String tenant) {
        return tenant == null || tenant.isBlank() ? "" : tenant;
    }

    @Override
    public String protocolVersion() {
        return protocolVersion;
    }

    @Override
    public Set<ClientFeature> capabilities() {
        return Set.of(ClientFeature.ORDINARY);
    }

    /**
     * Resolves the tenant to use, preferring the request tenant over the agent default.
     *
     * @param requestTenant the tenant from the request, may be null or blank
     * @return the tenant to use (request tenant if provided, otherwise agent default)
     */
    private String resolveTenant(@Nullable String requestTenant) {
        return (requestTenant == null || requestTenant.isBlank()) ? agentTenant : requestTenant;
    }

    @Override
    public EventKind sendMessage(MessageSendParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        MessageSendParams tenantRequest = createRequestWithTenant(request);

        org.a2aproject.sdk.grpc.SendMessageRequest sendMessageRequest = createGrpcSendMessageRequest(tenantRequest, context);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SEND_MESSAGE_METHOD, sendMessageRequest,
                agentCard, context);

        try {
            A2AServiceBlockingV2Stub stubWithMetadata = createBlockingStubWithMetadata(context, payloadAndHeaders);
            org.a2aproject.sdk.grpc.SendMessageResponse response = stubWithMetadata.sendMessage(sendMessageRequest);
            if (response.hasMessage()) {
                return FromProto.message(response.getMessage());
            } else if (response.hasTask()) {
                return FromProto.task(response.getTask());
            } else {
                throw new A2AClientException("Server response did not contain a message or task");
            }
        } catch (StatusRuntimeException | StatusException e) {
            throw GrpcErrorMapper.mapGrpcError(e, "Failed to send message: ");
        }
    }

    @Override
    public void sendMessageStreaming(MessageSendParams request, Consumer<StreamingEventKind> eventConsumer,
            Consumer<Throwable> errorConsumer, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        checkNotNullParam("eventConsumer", eventConsumer);
        MessageSendParams tenantRequest = createRequestWithTenant(request);

        org.a2aproject.sdk.grpc.SendMessageRequest grpcRequest = createGrpcSendMessageRequest(tenantRequest, context);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SEND_STREAMING_MESSAGE_METHOD,
                grpcRequest, agentCard, context);
        StreamObserver<org.a2aproject.sdk.grpc.StreamResponse> streamObserver = new EventStreamObserver(eventConsumer, errorConsumer);

        try {
            A2AServiceStub stubWithMetadata = createAsyncStubWithMetadata(context, payloadAndHeaders);
            stubWithMetadata.sendStreamingMessage(grpcRequest, streamObserver);
        } catch (StatusRuntimeException e) {
            throw GrpcErrorMapper.mapGrpcError(e, "Failed to send streaming message request: ");
        }
    }

    @Override
    public Task getTask(TaskQueryParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        org.a2aproject.sdk.grpc.GetTaskRequest.Builder requestBuilder = org.a2aproject.sdk.grpc.GetTaskRequest.newBuilder();
        requestBuilder.setId(request.id());
        if (request.historyLength() != null) {
            requestBuilder.setHistoryLength(request.historyLength());
        }
        requestBuilder.setTenant(resolveTenant(request.tenant()));
        org.a2aproject.sdk.grpc.GetTaskRequest getTaskRequest = requestBuilder.build();
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_TASK_METHOD, getTaskRequest,
                agentCard, context);

        try {
            A2AServiceBlockingV2Stub stubWithMetadata = createBlockingStubWithMetadata(context, payloadAndHeaders);
            return FromProto.task(stubWithMetadata.getTask(getTaskRequest));
        } catch (StatusRuntimeException | StatusException e) {
            throw GrpcErrorMapper.mapGrpcError(e, "Failed to get task: ");
        }
    }

    @Override
    public Task cancelTask(CancelTaskParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);

        org.a2aproject.sdk.grpc.CancelTaskRequest cancelTaskRequest = org.a2aproject.sdk.grpc.CancelTaskRequest.newBuilder()
                .setId(request.id())
                .setTenant(resolveTenant(request.tenant()))
                .build();
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(CANCEL_TASK_METHOD, cancelTaskRequest, agentCard, context);

        try {
            A2AServiceBlockingV2Stub stubWithMetadata = createBlockingStubWithMetadata(context, payloadAndHeaders);
            return FromProto.task(stubWithMetadata.cancelTask(cancelTaskRequest));
        } catch (StatusRuntimeException | StatusException e) {
            throw GrpcErrorMapper.mapGrpcError(e, "Failed to cancel task: ");
        }
    }

    @Override
    public ListTasksResult listTasks(ListTasksParams request, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);

        org.a2aproject.sdk.grpc.ListTasksRequest.Builder requestBuilder = org.a2aproject.sdk.grpc.ListTasksRequest.newBuilder();
        if (request.contextId() != null) {
            requestBuilder.setContextId(request.contextId());
        }
        if (request.status() != null) {
            requestBuilder.setStatus(ToProto.taskState(request.status()));
        }
        if (request.pageSize() != null) {
            requestBuilder.setPageSize(request.pageSize());
        }
        if (request.pageToken() != null) {
            requestBuilder.setPageToken(request.pageToken());
        }
        if (request.historyLength() != null) {
            requestBuilder.setHistoryLength(request.historyLength());
        }
        if (request.statusTimestampAfter() != null) {
            requestBuilder.setStatusTimestampAfter(
                    com.google.protobuf.Timestamp.newBuilder()
                            .setSeconds(request.statusTimestampAfter().getEpochSecond())
                            .setNanos(request.statusTimestampAfter().getNano())
                            .build());
        }
        if (request.includeArtifacts() != null) {
            requestBuilder.setIncludeArtifacts(request.includeArtifacts());
        }
        requestBuilder.setTenant(resolveTenant(request.tenant()));
        org.a2aproject.sdk.grpc.ListTasksRequest listTasksRequest = requestBuilder.build();
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(LIST_TASK_METHOD, listTasksRequest, agentCard, context);

        try {
            A2AServiceBlockingV2Stub stubWithMetadata = createBlockingStubWithMetadata(context, payloadAndHeaders);
            org.a2aproject.sdk.grpc.ListTasksResponse grpcResponse = stubWithMetadata.listTasks(listTasksRequest);

            return new ListTasksResult(
                    grpcResponse.getTasksList().stream()
                            .map(FromProto::task)
                            .collect(Collectors.toList()),
                    grpcResponse.getTotalSize(),
                    grpcResponse.getTasksCount(),
                    grpcResponse.getNextPageToken().isEmpty() ? null : grpcResponse.getNextPageToken()
            );
        } catch (StatusRuntimeException | StatusException e) {
            throw GrpcErrorMapper.mapGrpcError(e, "Failed to list tasks: ");
        }
    }

    @Override
    public TaskPushNotificationConfig createTaskPushNotificationConfiguration(TaskPushNotificationConfig request,
            @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);

        org.a2aproject.sdk.grpc.TaskPushNotificationConfig grpcRequest = ToProto.taskPushNotificationConfig(request);
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD, grpcRequest, agentCard, context);

        try {
            A2AServiceBlockingV2Stub stubWithMetadata = createBlockingStubWithMetadata(context, payloadAndHeaders);
            return FromProto.taskPushNotificationConfig(stubWithMetadata.createTaskPushNotificationConfig(grpcRequest));
        } catch (StatusRuntimeException | StatusException e) {
            throw GrpcErrorMapper.mapGrpcError(e, "Failed to create task push notification config: ");
        }
    }

    @Override
    public TaskPushNotificationConfig getTaskPushNotificationConfiguration(GetTaskPushNotificationConfigParams request,
            @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        checkNotNullParam("taskId", request.taskId());
        String configId = request.id();

        org.a2aproject.sdk.grpc.GetTaskPushNotificationConfigRequest grpcRequest = org.a2aproject.sdk.grpc.GetTaskPushNotificationConfigRequest.newBuilder()
                .setTaskId(request.taskId())
                .setTenant(resolveTenant(request.tenant()))
                .setId(configId == null ? "" : configId)
                .build();
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD, grpcRequest, agentCard, context);

        try {
            A2AServiceBlockingV2Stub stubWithMetadata = createBlockingStubWithMetadata(context, payloadAndHeaders);
            return FromProto.taskPushNotificationConfig(stubWithMetadata.getTaskPushNotificationConfig(grpcRequest));
        } catch (StatusRuntimeException | StatusException e) {
            throw GrpcErrorMapper.mapGrpcError(e, "Failed to get task push notification config: ");
        }
    }

    @Override
    public ListTaskPushNotificationConfigsResult listTaskPushNotificationConfigurations(
            ListTaskPushNotificationConfigsParams request,
            @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);

        org.a2aproject.sdk.grpc.ListTaskPushNotificationConfigsRequest grpcRequest = org.a2aproject.sdk.grpc.ListTaskPushNotificationConfigsRequest.newBuilder()
                .setTaskId(request.id())
                .setTenant(resolveTenant(request.tenant()))
                .setPageSize(request.pageSize())
                .setPageToken(request.pageToken())
                .build();
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(LIST_TASK_PUSH_NOTIFICATION_CONFIG_METHOD,
                grpcRequest, agentCard, context);

        try {
            A2AServiceBlockingV2Stub stubWithMetadata = createBlockingStubWithMetadata(context, payloadAndHeaders);
            org.a2aproject.sdk.grpc.ListTaskPushNotificationConfigsResponse grpcResponse = stubWithMetadata.listTaskPushNotificationConfigs(grpcRequest);
            return FromProto.listTaskPushNotificationConfigsResult(grpcResponse);
        } catch (StatusRuntimeException | StatusException e) {
            throw GrpcErrorMapper.mapGrpcError(e, "Failed to list task push notification configs: ");
        }
    }

    @Override
    public void deleteTaskPushNotificationConfigurations(DeleteTaskPushNotificationConfigParams request,
            @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);

        org.a2aproject.sdk.grpc.DeleteTaskPushNotificationConfigRequest grpcRequest = org.a2aproject.sdk.grpc.DeleteTaskPushNotificationConfigRequest.newBuilder()
                .setTaskId(request.taskId())
                .setId(request.id())
                .setTenant(resolveTenant(request.tenant()))
                .build();
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(DELETE_TASK_PUSH_NOTIFICATION_CONFIG_METHOD, grpcRequest, agentCard, context);

        try {
            A2AServiceBlockingV2Stub stubWithMetadata = createBlockingStubWithMetadata(context, payloadAndHeaders);
            stubWithMetadata.deleteTaskPushNotificationConfig(grpcRequest);
        } catch (StatusRuntimeException | StatusException e) {
            throw GrpcErrorMapper.mapGrpcError(e, "Failed to delete task push notification config: ");
        }
    }

    @Override
    public void subscribeToTask(TaskIdParams request, Consumer<StreamingEventKind> eventConsumer,
            Consumer<Throwable> errorConsumer, @Nullable ClientCallContext context) throws A2AClientException {
        checkNotNullParam("request", request);
        checkNotNullParam("eventConsumer", eventConsumer);

        org.a2aproject.sdk.grpc.SubscribeToTaskRequest grpcRequest = org.a2aproject.sdk.grpc.SubscribeToTaskRequest.newBuilder()
                .setTenant(resolveTenant(request.tenant()))
                .setId(request.id())
                .build();
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(SUBSCRIBE_TO_TASK_METHOD, grpcRequest, agentCard, context);

        StreamObserver<org.a2aproject.sdk.grpc.StreamResponse> streamObserver = new EventStreamObserver(eventConsumer, errorConsumer);

        try {
            A2AServiceStub stubWithMetadata = createAsyncStubWithMetadata(context, payloadAndHeaders);
            stubWithMetadata.subscribeToTask(grpcRequest, streamObserver);
        } catch (StatusRuntimeException e) {
            throw GrpcErrorMapper.mapGrpcError(e, "Failed to subscribe to task: ");
        }
    }

    /**
     * Ensure tenant is set, using agent default if not provided in request
     *
     * @param request the initial request.
     * @return the updated request with the tenant set.
     */
    private MessageSendParams createRequestWithTenant(MessageSendParams request) {
        return MessageSendParams.builder()
                .configuration(request.configuration())
                .message(request.message())
                .metadata(request.metadata())
                .tenant(resolveTenant(request.tenant()))
                .build();
    }

    @Override
    public AgentCard getExtendedAgentCard(GetExtendedAgentCardParams params, @Nullable ClientCallContext context) throws A2AClientException {
        GetExtendedAgentCardRequest request = GetExtendedAgentCardRequest.newBuilder()
                .setTenant(resolveTenant(params.tenant()))
                .build();
        PayloadAndHeaders payloadAndHeaders = applyInterceptors(GET_EXTENDED_AGENT_CARD_METHOD, request, agentCard, context);

        try {
            A2AServiceBlockingV2Stub stubWithMetadata = createBlockingStubWithMetadata(context, payloadAndHeaders);
            org.a2aproject.sdk.grpc.AgentCard response = stubWithMetadata.getExtendedAgentCard(request);

            return FromProto.agentCard(response);
        } catch (StatusRuntimeException | StatusException e) {
            throw GrpcErrorMapper.mapGrpcError(e, "Failed to get extended agent card: ");
        }
    }

    @Override
    public void close() {
        ownedChannel.close();
    }

    private org.a2aproject.sdk.grpc.SendMessageRequest createGrpcSendMessageRequest(MessageSendParams messageSendParams, @Nullable ClientCallContext context) {
        return ToProto.sendMessageRequest(messageSendParams);
    }

    /**
     * Creates gRPC metadata from ClientCallContext headers.
     * Extracts headers like a2a-extensions and sets them as gRPC metadata.
     * The headers are lower-cased (compared to the HTTP headers).
     * @param context the client call context containing headers, may be null
     * @param payloadAndHeaders the payload and headers wrapper, may be null
     * @return the gRPC metadata
     */
    private Metadata createGrpcMetadata(@Nullable ClientCallContext context, @Nullable PayloadAndHeaders payloadAndHeaders) {
        Map<String, String> headers = payloadAndHeaders == null
                ? ClientProtocolVersion.normalizeHeaders(context == null ? Map.of() : context.getHeaders(), protocolVersion)
                : ClientProtocolVersion.normalizeHeaders(payloadAndHeaders.getHeaders(), protocolVersion);
        Metadata metadata = new Metadata();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getValue() != null) {
                metadata.put(Metadata.Key.of(header.getKey().toLowerCase(Locale.ROOT), Metadata.ASCII_STRING_MARSHALLER), header.getValue());
            }
        }
        return metadata;
    }

    /**
     * Creates a blocking stub with metadata attached from the ClientCallContext.
     *
     * @param context the client call context
     * @param payloadAndHeaders the payloadAndHeaders after applying any interceptors
     * @return blocking stub with metadata interceptor
     */
    private A2AServiceBlockingV2Stub createBlockingStubWithMetadata(@Nullable ClientCallContext context,
            PayloadAndHeaders payloadAndHeaders) {
        Metadata metadata = createGrpcMetadata(context, payloadAndHeaders);
        return blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    /**
     * Creates an async stub with metadata attached from the ClientCallContext.
     *
     * @param context the client call context
     * @param payloadAndHeaders the payloadAndHeaders after applying any interceptors
     * @return async stub with metadata interceptor
     */
    private A2AServiceStub createAsyncStubWithMetadata(@Nullable ClientCallContext context,
            PayloadAndHeaders payloadAndHeaders) {
        Metadata metadata = createGrpcMetadata(context, payloadAndHeaders);
        return asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    private PayloadAndHeaders applyInterceptors(String methodName, Object payload,
            AgentCard agentCard, @Nullable ClientCallContext clientCallContext) {
        PayloadAndHeaders payloadAndHeaders = new PayloadAndHeaders(payload,
                ClientProtocolVersion.normalizeHeaders(clientCallContext != null ? clientCallContext.getHeaders() : Map.of(), protocolVersion));
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

}
