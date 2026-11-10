package org.a2aproject.sdk.client.transport.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerStreamTracer;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.GetTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.grpc.SendMessageRequest;
import org.a2aproject.sdk.grpc.StreamResponse;
import org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Actual HTTP/2 metadata and admission proof for SDK-owned channels. */
@Timeout(20)
class GrpcOwnedChannelTest {
    private static final Metadata.Key<String> VERSION = Metadata.Key.of("a2a-version", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> AUTH = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Message MESSAGE = Message.builder().messageId("owned-message")
            .role(Message.Role.ROLE_USER).parts(new TextPart("hello")).build();

    @Test
    void bothVersionsReachNettyPeerWithOnePinnedHeaderForUnaryAndStreaming() throws Exception {
        try (Peer peer = new Peer()) {
            for (String version : List.of("1.0", "1.1")) {
                GrpcTransport transport = GrpcTransport.createManaged(options(Map.of()), card(peer.endpoint()), version);
                try {
                    unary(transport, null);
                    stream(transport);
                } finally {
                    transport.close();
                }
            }
            assertEquals(List.of(List.of("1.0"), List.of("1.0"), List.of("1.1"), List.of("1.1")), peer.versions);
        }
    }

    @Test
    void defaultManagedTransportUsesOneOne() throws Exception {
        try (Peer peer = new Peer()) {
            GrpcTransport transport = GrpcTransport.createManaged(options(Map.of()), card(peer.endpoint()));
            try {
                assertEquals("1.1", transport.protocolVersion());
                unary(transport, null);
                assertEquals(List.of(List.of("1.1")), peer.versions);
            } finally {
                transport.close();
            }
        }
    }

    @Test
    void sdkAuthenticationConflictsRejectWithoutRpcForBothVersions() throws Exception {
        try (Peer peer = new Peer()) {
            for (String version : List.of("1.0", "1.1")) {
                String other = "1.0".equals(version) ? "1.1" : "1.0";
                assertThrows(A2AClientException.class, () -> {
                    GrpcTransport transport = GrpcTransport.createManaged(options(Map.of("A2A-Version", version,
                            "a2a-version", other)), card(peer.endpoint()), version);
                    try {
                        unary(transport, null);
                    } finally {
                        transport.close();
                    }
                });
            }
            assertEquals(List.of(), peer.versions);
            assertEquals(0, peer.streams.get());
        }
    }

    @Test
    void authenticationConflictsRejectStreamingWithoutRpcForBothVersions() throws Exception {
        try (Peer peer = new Peer()) {
            for (String version : List.of("1.0", "1.1")) {
                String other = "1.0".equals(version) ? "1.1" : "1.0";
                GrpcTransport transport = GrpcTransport.createManaged(options(Map.of("A2A-Version", other)), card(peer.endpoint()), version);
                try {
                    CountDownLatch result = new CountDownLatch(1);
                    AtomicReference<Throwable> failure = new AtomicReference<>();
                    AtomicInteger events = new AtomicInteger();
                    try {
                        transport.sendMessageStreaming(new MessageSendParams(MESSAGE, null, null), event -> {
                            events.incrementAndGet();
                            result.countDown();
                        }, error -> {
                            failure.set(error);
                            result.countDown();
                        }, null);
                    } catch (A2AClientException error) {
                        failure.set(error);
                        result.countDown();
                    }
                    assertTrue(result.await(5, TimeUnit.SECONDS));
                    assertNotNull(failure.get());
                    assertEquals(0, events.get());
                } finally {
                    transport.close();
                }
            }
            assertEquals(List.of(), peer.versions);
            assertEquals(0, peer.streams.get());
        }
    }

    @Test
    void identicalAuthenticationAndContextHeadersCollapseOnWireForBothVersions() throws Exception {
        try (Peer peer = new Peer()) {
            for (String version : List.of("1.0", "1.1")) {
                GrpcTransport transport = GrpcTransport.createManaged(options(Map.of("A2A-Version", version,
                        "a2a-version", version, "Authorization", "Bearer test")), card(peer.endpoint()), version);
                try {
                    unary(transport, new ClientCallContext(Map.of(), Map.of("A2A-Version", version, "a2a-version", version)));
                } finally {
                    transport.close();
                }
            }
            assertEquals(List.of(List.of("1.0"), List.of("1.1")), peer.versions);
            assertEquals(List.of("Bearer test", "Bearer test"), peer.authorization);
        }
    }

    @Test
    void conflictingContextRejectsWithoutRpcForBothVersions() throws Exception {
        try (Peer peer = new Peer()) {
            for (String version : List.of("1.0", "1.1")) {
                GrpcTransport transport = GrpcTransport.createManaged(options(Map.of()), card(peer.endpoint()), version);
                try {
                    String other = "1.0".equals(version) ? "1.1" : "1.0";
                    assertThrows(A2AClientException.class, () -> unary(transport,
                            new ClientCallContext(Map.of(), Map.of("A2A-Version", version, "a2a-version", other))));
                } finally {
                    transport.close();
                }
            }
            assertEquals(List.of(), peer.versions);
            assertEquals(0, peer.streams.get());
        }
    }

    @Test
    void managedConfigRetainsNonNullFactoryAndProviderPath() throws Exception {
        try (Peer peer = new Peer()) {
            GrpcTransportConfig config = new GrpcTransportConfigBuilder().managedChannel(options(Map.of())).build();
            assertNotNull(config.getChannelFactory());
            GrpcTransport transport = new GrpcTransportProvider().create(config, card(peer.endpoint()), descriptor(peer.endpoint(), "1.1"));
            try {
                unary(transport, null);
                assertEquals(List.of(List.of("1.1")), peer.versions);
            } finally {
                transport.close();
            }
        }
    }

    @Test
    void endpointMismatchDoesNotConsumeCorrectHandle() throws Exception {
        try (Peer peer = new Peer()) {
            Channel handle = new GrpcTransportConfigBuilder().managedChannel(options(Map.of())).build().getChannelFactory().apply(peer.endpoint());
            assertThrows(A2AClientException.class, () -> GrpcTransport.forInterface(handle, card(peer.endpoint()),
                    descriptor(peer.endpoint() + "/other", "1.1"), null));
            GrpcTransport owner = GrpcTransport.forInterface(handle, card(peer.endpoint()), descriptor(peer.endpoint(), "1.1"), null);
            try {
                unary(owner, null);
                assertEquals(List.of(List.of("1.1")), peer.versions);
            } finally {
                owner.close();
            }
        }
    }

    @Test
    void secondOwnerRejectionDoesNotCloseExistingOwner() throws Exception {
        try (Peer peer = new Peer()) {
            Channel handle = new GrpcTransportConfigBuilder().managedChannel(options(Map.of())).build().getChannelFactory().apply(peer.endpoint());
            GrpcTransport owner = GrpcTransport.forInterface(handle, card(peer.endpoint()), descriptor(peer.endpoint(), "1.0"), null);
            try {
                assertThrows(A2AClientException.class, () -> GrpcTransport.forInterface(handle, card(peer.endpoint()),
                        descriptor(peer.endpoint(), "1.1"), null));
                unary(owner, null);
                stream(owner);
                assertEquals(List.of(List.of("1.0"), List.of("1.0")), peer.versions);
            } finally {
                owner.close();
            }
        }
    }

    @Test
    void concurrentClaimsProduceOneOwner() throws Exception {
        try (Peer peer = new Peer()) {
            Channel handle = new GrpcTransportConfigBuilder().managedChannel(options(Map.of())).build().getChannelFactory().apply(peer.endpoint());
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<GrpcTransport>> claims = new ArrayList<>();
            try {
                for (String version : List.of("1.0", "1.1")) {
                    claims.add(executor.submit(() -> {
                        start.await();
                        try {
                            return GrpcTransport.forInterface(handle, card(peer.endpoint()), descriptor(peer.endpoint(), version), null);
                        } catch (A2AClientException rejected) {
                            return null;
                        }
                    }));
                }
                start.countDown();
                List<GrpcTransport> owners = new ArrayList<>();
                for (Future<GrpcTransport> claim : claims) {
                    GrpcTransport owner = claim.get(5, TimeUnit.SECONDS);
                    if (owner != null) {
                        owners.add(owner);
                    }
                }
                try {
                    assertEquals(1, owners.size());
                    unary(owners.get(0), null);
                    assertEquals(List.of(List.of(owners.get(0).protocolVersion())), peer.versions);
                } finally {
                    owners.forEach(GrpcTransport::close);
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void independentHandlesFromOneConfigKeepVersionsAndCloseSeparately() throws Exception {
        try (Peer peer = new Peer()) {
            GrpcTransportConfig config = new GrpcTransportConfigBuilder().managedChannel(options(Map.of())).build();
            GrpcTransport one = new GrpcTransportProvider().create(config, card(peer.endpoint()), descriptor(peer.endpoint(), "1.0"));
            GrpcTransport two = new GrpcTransportProvider().create(config, card(peer.endpoint()), descriptor(peer.endpoint(), "1.1"));
            try {
                unary(one, null);
                unary(two, null);
                stream(one);
                stream(two);
                one.close();
                unary(two, null);
                assertEquals(List.of(List.of("1.0"), List.of("1.1"), List.of("1.0"), List.of("1.1"), List.of("1.1")), peer.versions);
            } finally {
                one.close();
                two.close();
            }
        }
    }

    @Test
    void closedHandleRejectsReattachmentAndCalls() throws Exception {
        try (Peer peer = new Peer()) {
            Channel handle = new GrpcTransportConfigBuilder().managedChannel(options(Map.of())).build().getChannelFactory().apply(peer.endpoint());
            GrpcTransport owner = GrpcTransport.forInterface(handle, card(peer.endpoint()), descriptor(peer.endpoint(), "1.1"), null);
            owner.close();
            owner.close();
            assertThrows(A2AClientException.class, () -> GrpcTransport.forInterface(handle, card(peer.endpoint()), descriptor(peer.endpoint(), "1.1"), null));
            assertThrows(A2AClientException.class, () -> unary(owner, null));
            assertEquals(List.of(), peer.versions);
            assertEquals(0, peer.streams.get());
        }
    }

    private static GrpcManagedChannelOptions options(Map<String, String> auth) {
        return GrpcManagedChannelOptions.builder().usePlaintext().authentication(auth).shutdownTimeout(Duration.ofSeconds(2)).build();
    }

    private static void unary(GrpcTransport transport, ClientCallContext context) {
        assertEquals("task", transport.getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams("task"), context).taskId());
    }

    private static void stream(GrpcTransport transport) throws InterruptedException {
        CountDownLatch event = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger events = new AtomicInteger();
        transport.sendMessageStreaming(new MessageSendParams(MESSAGE, null, null), value -> {
            events.incrementAndGet();
            event.countDown();
        }, error -> {
            failure.set(error);
            event.countDown();
        }, null);
        assertTrue(event.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());
        assertEquals(1, events.get());
    }

    private static AgentInterface descriptor(String endpoint, String version) {
        return new AgentInterface("GRPC", endpoint, null, version);
    }

    private static AgentCard card(String endpoint) {
        return AgentCard.builder().name("owned").description("owned").version("1")
                .supportedInterfaces(List.of(descriptor(endpoint, "1.0"), descriptor(endpoint, "1.1")))
                .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(true).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
    }

    private static final class Peer implements AutoCloseable {
        private final List<List<String>> versions = new CopyOnWriteArrayList<>();
        private final AtomicInteger streams = new AtomicInteger();
        private final List<String> authorization = new CopyOnWriteArrayList<>();
        private final Server server;

        private Peer() throws Exception {
            server = NettyServerBuilder.forPort(0).directExecutor()
                    .addStreamTracerFactory(new ServerStreamTracer.Factory() {
                        @Override
                        public ServerStreamTracer newServerStreamTracer(String method, Metadata headers) {
                            streams.incrementAndGet();
                            return new ServerStreamTracer() { };
                        }
                    })
                    .addService(ServerInterceptors.intercept(new A2AServiceGrpc.A2AServiceImplBase() {
                        @Override
                        public void getTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest request,
                                StreamObserver<org.a2aproject.sdk.grpc.TaskPushNotificationConfig> response) {
                            response.onNext(org.a2aproject.sdk.grpc.TaskPushNotificationConfig.newBuilder().setTaskId(request.getTaskId())
                                    .setId("config").setUrl("https://callback.test").build());
                            response.onCompleted();
                        }

                        @Override
                        public void sendStreamingMessage(SendMessageRequest request, StreamObserver<StreamResponse> response) {
                            response.onNext(StreamResponse.newBuilder().setMessage(ToProto.message(MESSAGE)).build());
                            response.onCompleted();
                        }
                    }, new ServerInterceptor() {
                        @Override
                        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                            List<String> actual = new ArrayList<>();
                            Iterable<String> values = headers.getAll(VERSION);
                            if (values != null) {
                                values.forEach(actual::add);
                            }
                            versions.add(List.copyOf(actual));
                            String auth = headers.get(AUTH);
                            if (auth != null) {
                                authorization.add(auth);
                            }
                            return next.startCall(call, headers);
                        }
                    })).build().start();
        }

        private String endpoint() {
            return "http://127.0.0.1:" + server.getPort();
        }

        @Override
        public void close() throws InterruptedException {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
