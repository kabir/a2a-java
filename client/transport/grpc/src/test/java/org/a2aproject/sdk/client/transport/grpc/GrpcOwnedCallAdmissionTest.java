package org.a2aproject.sdk.client.transport.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientStreamTracer;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerStreamTracer;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.GetTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Direct calls cannot bypass an owned handle's private pin or install executable hooks. */
@Timeout(20)
class GrpcOwnedCallAdmissionTest {
    @Test
    void invalidPinsCredentialsAndTracersRejectBeforeCallbacksOrServerStreamsForBothVersions() throws Exception {
        AtomicInteger streams = new AtomicInteger();
        AtomicInteger credentialCallbacks = new AtomicInteger();
        AtomicInteger tracerCallbacks = new AtomicInteger();
        CallCredentials credentials = new CallCredentials() {
            @Override
            public void applyRequestMetadata(RequestInfo info, Executor executor, MetadataApplier applier) {
                credentialCallbacks.incrementAndGet();
                applier.apply(new Metadata());
            }
        };
        ClientStreamTracer.Factory tracer = new ClientStreamTracer.Factory() {
            @Override
            public ClientStreamTracer newClientStreamTracer(ClientStreamTracer.StreamInfo info, Metadata headers) {
                tracerCallbacks.incrementAndGet();
                return new ClientStreamTracer() { };
            }
        };
        Server peer = NettyServerBuilder.forPort(0).directExecutor()
                .addStreamTracerFactory(new ServerStreamTracer.Factory() {
                    @Override
                    public ServerStreamTracer newServerStreamTracer(String method, Metadata headers) {
                        streams.incrementAndGet();
                        return new ServerStreamTracer() { };
                    }
                }).addService(new A2AServiceGrpc.A2AServiceImplBase() {
                    @Override
                    public void getTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest request,
                            StreamObserver<org.a2aproject.sdk.grpc.TaskPushNotificationConfig> response) {
                        response.onNext(org.a2aproject.sdk.grpc.TaskPushNotificationConfig.newBuilder()
                                .setTaskId(request.getTaskId()).setId("config").setUrl("https://callback.test").build());
                        response.onCompleted();
                    }
                }).build().start();
        try {
            String endpoint = "http://127.0.0.1:" + peer.getPort();
            GrpcTransportConfig config = new GrpcTransportConfigBuilder()
                    .managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()).build();
            for (String version : List.of("1.0", "1.1")) {
                AgentInterface descriptor = new AgentInterface("GRPC", endpoint, null, version);
                AgentCard card = AgentCard.builder().name("admission").description("admission").version("1")
                        .supportedInterfaces(List.of(descriptor)).capabilities(AgentCapabilities.builder().pushNotifications(true).build())
                        .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
                Channel handle = config.getChannelFactory().apply(endpoint);
                GrpcTransport owner = GrpcTransport.forInterface(handle, card, descriptor, null);
                try {
                    int before = streams.get();
                    assertRejected(handle, CallOptions.DEFAULT, "selected-version pin");
                    assertRejected(handle, CallOptions.DEFAULT.withOption(OwnedGrpcChannel.VERSION_OPTION,
                            version.equals("1.0") ? "1.1" : "1.0"), "selected-version pin");
                    CallOptions pinned = CallOptions.DEFAULT.withOption(OwnedGrpcChannel.VERSION_OPTION, version);
                    assertRejected(handle, pinned.withCallCredentials(credentials), "CallCredentials");
                    assertRejected(handle, pinned.withStreamTracerFactory(tracer), "tracers");
                    assertEquals(0, credentialCallbacks.get());
                    assertEquals(0, tracerCallbacks.get());
                    assertEquals(before, streams.get());
                    // A valid call still succeeds on this same owner after every admission failure.
                    assertEquals("task", owner.getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams("task"), null).taskId());
                    assertEquals(before + 1, streams.get());
                } finally {
                    owner.close();
                }
            }
        } finally {
            peer.shutdownNow();
            assertTrue(peer.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void assertRejected(Channel handle, CallOptions options, String reason) {
        A2AClientException failure = assertThrows(A2AClientException.class,
                () -> handle.newCall(A2AServiceGrpc.getGetTaskPushNotificationConfigMethod(), options));
        assertTrue(failure.getMessage().contains(reason), failure.getMessage());
    }
}
