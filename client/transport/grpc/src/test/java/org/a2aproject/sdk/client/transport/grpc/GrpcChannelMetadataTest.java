package org.a2aproject.sdk.client.transport.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientStreamTracer;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerStreamTracer;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
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

/** SDK-owned channels validate authentication metadata before server ingress. */
@Timeout(10)
class GrpcChannelMetadataTest {
    private static final Metadata.Key<String> VERSION = Metadata.Key.of("a2a-version", Metadata.ASCII_STRING_MARSHALLER);
    private static final Message MESSAGE = Message.builder().messageId("channel-metadata-message")
            .role(Message.Role.ROLE_USER).parts(new TextPart("test")).build();
    private static final AgentCard CARD = AgentCard.builder().name("test").description("test").version("1")
            .supportedInterfaces(List.of(new AgentInterface("GRPC", "localhost", null, "1.0"),
                    new AgentInterface("GRPC", "localhost", null, "1.1")))
            .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(true).build())
            .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();

    @Test
    void conflictingAuthenticationVersionRejectsUnaryBeforeServerIngress() throws Exception {
        for (String selected : List.of("1.0", "1.1")) {
            try (Fixture fixture = fixture(selected.equals("1.0") ? "1.1" : "1.0")) {
                GrpcTransport transport = new GrpcTransport(fixture.channel(), fixture.card(), selected);
                assertThrows(A2AClientException.class, () -> transport.getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams("task"), null));
                assertEquals(0, fixture.ingress().get());
                assertEquals(0, fixture.streams().get());
            }
        }
    }

    @Test
    void identicalAuthenticationVersionsNormalizeBeforeUnaryServerIngress() throws Exception {
        for (String selected : List.of("1.0", "1.1")) {
            try (Fixture fixture = fixture(selected, selected)) {
                new GrpcTransport(fixture.channel(), fixture.card(), selected)
                        .getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams("task"), null);
                assertEquals(1, fixture.ingress().get());
                assertEquals(1, fixture.streams().get());
                assertEquals(List.of(selected), fixture.versions().get());
            }
        }
    }

    @Test
    void conflictingAuthenticationVersionRejectsStreamingBeforeServerIngress() throws Exception {
        for (String selected : List.of("1.0", "1.1")) {
            try (Fixture fixture = fixture(selected.equals("1.0") ? "1.1" : "1.0")) {
                CountDownLatch result = new CountDownLatch(1);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                AtomicInteger events = new AtomicInteger();
                GrpcTransport transport = new GrpcTransport(fixture.channel(), fixture.card(), selected);
                try {
                    transport.sendMessageStreaming(
                            new MessageSendParams(MESSAGE, null, null), event -> {
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
                assertEquals(0, fixture.ingress().get());
                assertEquals(0, fixture.streams().get());
            }
        }
    }

    @Test
    void identicalAuthenticationVersionsNormalizeBeforeStreamingServerIngress() throws Exception {
        for (String selected : List.of("1.0", "1.1")) {
            try (Fixture fixture = fixture(selected, selected)) {
                CountDownLatch result = new CountDownLatch(1);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                new GrpcTransport(fixture.channel(), fixture.card(), selected).sendMessageStreaming(
                        new MessageSendParams(MESSAGE, null, null), event -> result.countDown(), error -> {
                            failure.set(error);
                            result.countDown();
                        }, null);
                assertTrue(result.await(5, TimeUnit.SECONDS));
                assertNull(failure.get());
                assertEquals(1, fixture.ingress().get());
                assertEquals(1, fixture.streams().get());
                assertEquals(List.of(selected), fixture.versions().get());
            }
        }
    }

    @Test
    void opaqueLateTracerChannelIsRejectedAtAdmissionBeforeServerIngress() throws Exception {
        ClientInterceptor lateTracer = new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                    CallOptions options, Channel next) {
                return next.newCall(method, options.withStreamTracerFactory(new ClientStreamTracer.Factory() {
                    @Override
                    public ClientStreamTracer newClientStreamTracer(ClientStreamTracer.StreamInfo info, Metadata headers) {
                        headers.put(VERSION, "1.0");
                        return new ClientStreamTracer() { };
                    }
                }));
            }
        };
        try (Fixture fixture = fixture()) {
            ManagedChannel opaque = NettyChannelBuilder.forTarget(fixture.endpoint()).usePlaintext()
                    .intercept(lateTracer).build();
            try {
                A2AClientException error = assertThrows(A2AClientException.class,
                        () -> new GrpcTransport(opaque, fixture.card(), "1.1"));
                assertTrue(error.getMessage().contains("Opaque gRPC channels"), error.getMessage());
            } finally {
                opaque.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
            assertEquals(0, fixture.streams().get());
            assertEquals(0, fixture.ingress().get());
        }
    }

    private static Fixture fixture(String... injectedVersions) throws Exception {
        Map<String, String> authentication = switch (injectedVersions.length) {
            case 0 -> Map.of();
            case 1 -> Map.of("a2a-version", injectedVersions[0]);
            case 2 -> Map.of("a2a-version", injectedVersions[0], "A2A-Version", injectedVersions[1]);
            default -> throw new IllegalArgumentException("At most two case variants are required");
        };
        AtomicInteger ingress = new AtomicInteger();
        AtomicInteger streams = new AtomicInteger();
        AtomicReference<List<String>> versions = new AtomicReference<>();
        Server server = NettyServerBuilder.forPort(0).directExecutor()
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
                        response.onNext(org.a2aproject.sdk.grpc.TaskPushNotificationConfig.newBuilder()
                                .setTaskId(request.getTaskId()).setId("config").setUrl("https://callback.test").build());
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
                        ingress.incrementAndGet();
                        List<String> captured = new ArrayList<>();
                        Iterable<String> received = headers.getAll(VERSION);
                        if (received != null) {
                            received.forEach(captured::add);
                        }
                        versions.set(List.copyOf(captured));
                        return next.startCall(call, headers);
                    }
                })).build().start();
        OwnedGrpcChannel channel = (OwnedGrpcChannel) new GrpcTransportConfigBuilder().managedChannel(
                GrpcManagedChannelOptions.builder().usePlaintext().authentication(authentication).build())
                .build().getChannelFactory().apply("localhost:" + server.getPort());
        return new Fixture(server, channel, ingress, streams, versions);
    }

    private record Fixture(Server server, OwnedGrpcChannel channel, AtomicInteger ingress, AtomicInteger streams,
            AtomicReference<List<String>> versions) implements AutoCloseable {
        String endpoint() {
            return "localhost:" + server.getPort();
        }

        AgentCard card() {
            return AgentCard.builder(CARD).supportedInterfaces(List.of(
                    new AgentInterface("GRPC", endpoint(), null, "1.0"),
                    new AgentInterface("GRPC", endpoint(), null, "1.1"))).build();
        }

        @Override
        public void close() throws InterruptedException {
            channel.close();
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
