package org.a2aproject.sdk.client.transport.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.GetTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.GetExtendedAgentCardParams;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TextPart;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class GrpcTransportTest {

    private static final String TASK_ID = "task-1";
    private static final String CALLBACK_URL = "https://example.com/callback";
    private static final AgentCard TEMPLATE_CARD = AgentCard.builder()
            .name("Test Agent")
            .description("Agent for gRPC transport tests")
            .version("1.0.0")
            .supportedInterfaces(List.of(new AgentInterface("GRPC", "localhost", null, "1.1"),
                    new AgentInterface("GRPC", "localhost", "default-tenant", "1.1")))
            .capabilities(AgentCapabilities.builder().pushNotifications(true).build())
            .defaultInputModes(List.of("text"))
            .defaultOutputModes(List.of("text"))
            .skills(List.of())
            .build();

    private final AtomicReference<GetTaskPushNotificationConfigRequest> receivedRequest = new AtomicReference<>();
    private final AtomicReference<Metadata> receivedMetadata = new AtomicReference<>();
    private Server server;
    private AgentCard CARD;
    private final List<GrpcTransport> providerTransports = new ArrayList<>();
    private final List<OwnedGrpcChannel> channels = new ArrayList<>();
    private CountDownLatch requestArrived = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws Exception {
        server = NettyServerBuilder.forPort(0)
                .directExecutor()
                .addService(ServerInterceptors.intercept(new A2AServiceGrpc.A2AServiceImplBase() {
                    @Override
                    public void getTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest request,
                            StreamObserver<org.a2aproject.sdk.grpc.TaskPushNotificationConfig> responseObserver) {
                        receivedRequest.set(request);
                        responseObserver.onNext(org.a2aproject.sdk.grpc.TaskPushNotificationConfig.newBuilder()
                                .setTaskId(request.getTaskId())
                                .setId(request.getId().isEmpty() ? request.getTaskId() : request.getId())
                                .setUrl(CALLBACK_URL)
                                .build());
                        responseObserver.onCompleted();
                    }
                }, new ServerInterceptor() {
                    @Override
                    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                            Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                        receivedMetadata.set(headers);
                        requestArrived.countDown();
                        return next.startCall(call, headers);
                    }
                }))
                .build()
                .start();
        CARD = AgentCard.builder(TEMPLATE_CARD).supportedInterfaces(List.of(
                new AgentInterface("GRPC", endpoint(), null, "1.1"),
                new AgentInterface("GRPC", endpoint(), "default-tenant", "1.1"))).build();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        providerTransports.forEach(GrpcTransport::close);
        channels.forEach(OwnedGrpcChannel::close);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void testGetPushNotificationConfigWithOmittedId() throws Exception {
        assertGetPushNotificationConfig(new GetTaskPushNotificationConfigParams(TASK_ID), null, "", "");
    }

    @Test
    void testGetPushNotificationConfigWithOmittedIdViaBuilder() throws Exception {
        assertGetPushNotificationConfig(GetTaskPushNotificationConfigParams.builder().taskId(TASK_ID).build(),
                null, "", "");
    }

    @Test
    void testGetPushNotificationConfigWithEmptyId() throws Exception {
        assertGetPushNotificationConfig(new GetTaskPushNotificationConfigParams(TASK_ID, ""), null, "", "");
    }

    @Test
    void testGetPushNotificationConfigWithExplicitIdAndDefaultTenant() throws Exception {
        assertGetPushNotificationConfig(new GetTaskPushNotificationConfigParams(TASK_ID, "config-1"),
                "default-tenant", "config-1", "default-tenant");
    }

    @Test
    void testGetPushNotificationConfigWithOmittedIdAndRequestTenant() throws Exception {
        assertGetPushNotificationConfig(new GetTaskPushNotificationConfigParams(TASK_ID, null, "request-tenant"),
                "default-tenant", "", "request-tenant");
    }

    @Test
    void defaultConstructorEmitsOneOne() {
        new GrpcTransport(channel(), CARD).getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams(TASK_ID), null);
        assertEquals(List.of("1.1"), receivedVersions());
    }

    @Test
    void duplicateIdenticalContextVersionIsNormalized() {
        ClientCallContext context = new ClientCallContext(Map.of(), Map.of("A2A-Version", "1.1", "a2a-version", "1.1"));
        new GrpcTransport(channel(), CARD).getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams(TASK_ID), context);
        assertEquals(List.of("1.1"), receivedVersions());
    }

    @Test
    void conflictingContextVersionFailsBeforeChannelCall() {
        ClientCallContext context = new ClientCallContext(Map.of(), Map.of("a2a-version", "1.0"));
        GrpcTransport transport = new GrpcTransport(channel(), CARD);
        assertThrows(A2AClientException.class, () -> transport
                .getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams(TASK_ID), context));
        assertNull(receivedRequest.get());
    }

    @Test
    void providerForwardsExplicitOneOneDescriptorVersion() {
        GrpcTransport transport = track(new GrpcTransportProvider().create(
                new GrpcTransportConfigBuilder().managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()).build(), CARD,
                new AgentInterface("GRPC", endpoint(), "selected-tenant", "1.1")));
        transport.getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams(TASK_ID), null);
        assertEquals(List.of("1.1"), receivedVersions());
        assertEquals("selected-tenant", receivedRequest.get().getTenant());
    }

    @Test
    void separateVersionsRemainIsolatedAcrossOwnedChannels() {
        GrpcTransportConfig config = new GrpcTransportConfigBuilder().managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()).build();
        GrpcTransportProvider provider = new GrpcTransportProvider();
        GrpcTransport legacy = track(provider.create(config, CARD, new AgentInterface("GRPC", endpoint(), null, "1.0")));
        GrpcTransport current = track(provider.create(config, CARD, new AgentInterface("GRPC", endpoint(), null, "1.1")));
        for (GrpcTransport transport : List.of(legacy, current, legacy, current)) {
            transport.getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams(TASK_ID), null);
            assertEquals(List.of(transport == legacy ? "1.0" : "1.1"), receivedVersions());
        }
    }

    @Test
    void selectedVersionReachesEveryUnaryAndStreamingOperation() {
        GrpcTransport transport = new GrpcTransport(channel(), CARD);
        for (Consumer<ClientCallContext> operation : operations(transport)) {
            receivedMetadata.set(null);
            requestArrived = new CountDownLatch(1);
            try {
                operation.accept(null);
            } catch (A2AClientException expectedUnimplementedOperation) {
                // This focused server implements only getTaskPushNotificationConfig.
            }
            assertEquals(List.of("1.1"), receivedVersions());
        }
    }

    @Test
    void contextConflictFailsBeforeEveryUnaryAndStreamingOperation() {
        ClientCallContext context = new ClientCallContext(Map.of(), Map.of("A2A-Version", "1.1", "a2a-version", "1.0"));
        for (Consumer<ClientCallContext> operation : operations(new GrpcTransport(channel(), CARD))) {
            assertThrows(A2AClientException.class, () -> operation.accept(context));
        }
        assertNull(receivedMetadata.get());
    }

    @Test
    void interceptorConflictFailsBeforeEveryUnaryAndStreamingOperation() {
        ClientCallInterceptor interceptor = new ClientCallInterceptor() {
            @Override
            public PayloadAndHeaders intercept(String method, Object payload, Map<String, String> headers,
                    AgentCard card, ClientCallContext context) {
                return new PayloadAndHeaders(payload, Map.of("A2A-Version", "1.1", "a2a-version", "1.0"));
            }
        };
        GrpcTransport transport = new GrpcTransport(channel(), CARD, "", List.of(interceptor));
        for (Consumer<ClientCallContext> operation : operations(transport)) {
            assertThrows(A2AClientException.class, () -> operation.accept(null));
        }
        assertNull(receivedMetadata.get());
    }

    @Test
    void defaultCardConstructorRejectsOneZeroOnlyCard() {
        AgentCard legacy = cardWithInterfaces(List.of(new AgentInterface("GRPC", endpoint(), null, "1.0")));
        assertThrows(A2AClientException.class, () -> new GrpcTransport(channel(), legacy));
        assertNull(receivedMetadata.get());
    }

    @Test
    void defaultCardConstructorRejectsWrongBinding() {
        AgentCard wrongBinding = cardWithInterfaces(List.of(new AgentInterface("JSONRPC", endpoint(), null, "1.1")));
        assertThrows(A2AClientException.class, () -> new GrpcTransport(channel(), wrongBinding));
        assertNull(receivedMetadata.get());
    }

    @Test
    void cardOrderCannotChangeSelectedVersionBindingOrDescriptorTenant() {
        AgentInterface legacy = new AgentInterface("GRPC", endpoint(), "legacy-tenant", "1.0");
        AgentInterface current = new AgentInterface("GRPC", endpoint(), "selected-tenant", "1.1");
        AgentInterface wrongBinding = new AgentInterface("JSONRPC", endpoint(), "wrong-tenant", "1.1");
        for (List<AgentInterface> interfaces : List.of(List.of(legacy, wrongBinding, current), List.of(current, wrongBinding, legacy))) {
            new GrpcTransport(channel(), cardWithInterfaces(interfaces))
                    .getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams(TASK_ID), null);
            assertEquals(List.of("1.1"), receivedVersions());
            assertEquals("selected-tenant", receivedRequest.get().getTenant());
        }
    }

    @Test
    void explicitTenantConstructorRejectsUnavailableTenantAndVersion() {
        assertThrows(A2AClientException.class, () -> new GrpcTransport(channel(), CARD, "missing-tenant", null));
        assertThrows(A2AClientException.class, () -> new GrpcTransport(channel(), CARD, "default-tenant", null, "1.0"));
    }

    @Test
    void explicitVersionConstructorSelectsExactDescriptorAndTenant() {
        AgentCard legacy = cardWithInterfaces(List.of(new AgentInterface("GRPC", endpoint(), "legacy-tenant", "1.0")));
        new GrpcTransport(channel(), legacy, "1.0")
                .getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams(TASK_ID), null);
        assertEquals(List.of("1.0"), receivedVersions());
        assertEquals("legacy-tenant", receivedRequest.get().getTenant());
    }

    private AgentCard cardWithInterfaces(List<AgentInterface> interfaces) {
        return AgentCard.builder(CARD).supportedInterfaces(interfaces).build();
    }

    private String endpoint() {
        return "localhost:" + server.getPort();
    }

    private Channel channel() {
        OwnedGrpcChannel channel = (OwnedGrpcChannel) new GrpcTransportConfigBuilder()
                .managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()).build()
                .getChannelFactory().apply(endpoint());
        channels.add(channel);
        return channel;
    }

    private GrpcTransport track(GrpcTransport transport) {
        providerTransports.add(transport);
        return transport;
    }

    private List<String> receivedVersions() {
        try {
            assertTrue(requestArrived.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
        Metadata metadata = receivedMetadata.get();
        assertNotNull(metadata);
        Iterable<String> values = metadata.getAll(Metadata.Key.of("a2a-version", Metadata.ASCII_STRING_MARSHALLER));
        List<String> result = new ArrayList<>();
        if (values != null) {
            values.forEach(result::add);
        }
        return result;
    }

    private void assertGetPushNotificationConfig(GetTaskPushNotificationConfigParams params,
            @Nullable String defaultTenant, String expectedId, String expectedTenant) throws Exception {
        AgentCard legacyCard = cardWithInterfaces(List.of(new AgentInterface("GRPC", endpoint(), defaultTenant, "1.0")));
        GrpcTransport transport = new GrpcTransport(channel(), legacyCard, defaultTenant, null, "1.0");
        TaskPushNotificationConfig result = transport.getTaskPushNotificationConfiguration(params, null);
        assertEquals(List.of("1.0"), receivedVersions());

        GetTaskPushNotificationConfigRequest request = receivedRequest.get();
        assertNotNull(request);
        assertEquals(TASK_ID, request.getTaskId());
        assertEquals(expectedId, request.getId());
        assertEquals(expectedTenant, request.getTenant());
        assertEquals(expectedId.isEmpty() ? TASK_ID : expectedId, result.id());
        assertEquals(TASK_ID, result.taskId());
        assertEquals(CALLBACK_URL, result.url());
    }
    private static List<Consumer<ClientCallContext>> operations(ClientTransport transport) {
        MessageSendParams message = MessageSendParams.builder().message(Message.builder()
                .messageId("message").role(Message.Role.ROLE_USER).parts(List.of(new TextPart("hello"))).build()).build();
        return List.of(
                context -> transport.sendMessage(message, context),
                context -> transport.sendMessageStreaming(message, event -> { }, error -> { }, context),
                context -> transport.getTask(new TaskQueryParams("task"), context),
                context -> transport.cancelTask(new CancelTaskParams("task"), context),
                context -> transport.listTasks(new ListTasksParams(), context),
                context -> transport.createTaskPushNotificationConfiguration(TaskPushNotificationConfig.builder()
                        .taskId("task").url("https://callback.example").build(), context),
                context -> transport.getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams("task"), context),
                context -> transport.listTaskPushNotificationConfigurations(new ListTaskPushNotificationConfigsParams("task"), context),
                context -> transport.deleteTaskPushNotificationConfigurations(new DeleteTaskPushNotificationConfigParams("task", "config"), context),
                context -> transport.subscribeToTask(new TaskIdParams("task"), event -> { }, error -> { }, context),
                context -> transport.getExtendedAgentCard(new GetExtendedAgentCardParams(null), context));
    }
}
