package org.a2aproject.sdk.client.transport.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Real HTTP/2 TLS, cancellation, and owner shutdown using ordinary JVM trust configuration. */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
@Timeout(30)
class GrpcOwnedLifecycleTest {
    private static final Metadata.Key<String> VERSION = Metadata.Key.of("a2a-version", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> AUTH = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String TOKEN = "Bearer owned-lifecycle-test";
    private static final Message MESSAGE = Message.builder().messageId("owned-lifecycle")
            .role(Message.Role.ROLE_USER).parts(new TextPart("hello")).build();

    @Test
    void defaultTlsUsesSystemTrustForUnaryAndStreamingWithBothVersions() throws Exception {
        try (SystemTrust trust = SystemTrust.trustFixture(); Peer peer = new Peer(false)) {
            for (String version : List.of("1.0", "1.1")) {
                GrpcTransport transport = managed(peer, version, Duration.ofSeconds(2));
                try {
                    unary(transport, peer);
                    ClientStream stream = new ClientStream();
                    stream.start(transport);
                    assertTrue(stream.terminal.await(5, TimeUnit.SECONDS));
                    assertEquals(1, stream.events.get());
                    assertEquals(1, stream.terminals.get());
                    assertNull(stream.failure.get());
                } finally {
                    transport.close();
                }
            }
            assertRequests(peer, List.of("1.0", "1.0", "1.1", "1.1"));
            assertEquals(4, peer.serviceCalls.get());
        }
    }

    @Test
    void untrustedTlsPeerFailsHandshakeBeforeRpcForBothVersions() throws Exception {
        try (SystemTrust trust = SystemTrust.systemDefaults(); Peer peer = new Peer(false)) {
            for (String version : List.of("1.0", "1.1")) {
                GrpcTransport transport = managed(peer, version, Duration.ofSeconds(2));
                try {
                    A2AClientException failure = assertThrows(A2AClientException.class, () -> unary(transport, peer));
                    assertTrue(hasHandshakeFailure(failure), "An untrusted certificate must fail the TLS handshake");
                    ClientStream stream = new ClientStream();
                    stream.start(transport);
                    assertTrue(stream.terminal.await(5, TimeUnit.SECONDS));
                    assertNotNull(stream.failure.get());
                    assertTrue(hasHandshakeFailure(stream.failure.get()));
                    assertEquals(0, stream.events.get());
                    assertEquals(1, stream.terminals.get());
                } finally {
                    transport.close();
                }
            }
            assertEquals(List.of(), peer.observations);
            assertEquals(0, peer.serviceCalls.get());
        }
    }

    @Test
    void callerCancellationReachesServerAndLeavesOwnerUsableForBothVersions() throws Exception {
        try (SystemTrust trust = SystemTrust.trustFixture()) {
            for (String version : List.of("1.0", "1.1")) {
                try (Peer peer = new Peer(true); Context.CancellableContext caller = Context.current().withCancellation()) {
                    GrpcTransport transport = managed(peer, version, Duration.ofSeconds(2));
                    try {
                        ClientStream stream = new ClientStream();
                        caller.run(() -> stream.start(transport));
                        assertTrue(peer.streamStarted.await(5, TimeUnit.SECONDS));
                        assertTrue(stream.firstEvent.await(5, TimeUnit.SECONDS));
                        assertEquals(0, stream.terminals.get());
                        assertTrue(caller.cancel(new CancellationException("caller cancelled owned stream")));
                        assertTrue(stream.terminal.await(5, TimeUnit.SECONDS));
                        assertTrue(peer.streamCancelled.await(5, TimeUnit.SECONDS));
                        assertNotNull(stream.failure.get());
                        assertEquals(Status.Code.CANCELLED, Status.fromThrowable(stream.failure.get()).getCode());
                        assertEquals(1, stream.terminals.get());
                        assertEquals(1, peer.cancellations.get());
                        unary(transport, peer);
                        assertRequests(peer, List.of(version, version));
                        assertEquals(2, peer.serviceCalls.get());
                    } finally {
                        transport.close();
                    }
                }
            }
        }
    }

    @Test
    void closeCancelsActiveStreamWithinBoundAndIsIdempotentForBothVersions() throws Exception {
        Duration shutdownTimeout = Duration.ofMillis(500);
        try (SystemTrust trust = SystemTrust.trustFixture()) {
            for (String version : List.of("1.0", "1.1")) {
                try (Peer peer = new Peer(true)) {
                    GrpcTransport transport = managed(peer, version, shutdownTimeout);
                    try {
                        ClientStream stream = new ClientStream();
                        stream.start(transport);
                        assertTrue(peer.streamStarted.await(5, TimeUnit.SECONDS));
                        assertTrue(stream.firstEvent.await(5, TimeUnit.SECONDS));
                        assertEquals(0, stream.terminals.get());
                        long started = System.nanoTime();
                        transport.close();
                        assertTrue(System.nanoTime() - started <= shutdownTimeout.plusSeconds(1).toNanos(),
                                "Close must honor the configured timeout with a one-second scheduling margin");
                        assertTrue(stream.terminal.await(5, TimeUnit.SECONDS));
                        assertTrue(peer.streamCancelled.await(5, TimeUnit.SECONDS));
                        assertNotNull(stream.failure.get());
                        assertEquals(1, stream.terminals.get());
                        assertEquals(1, peer.cancellations.get());
                        started = System.nanoTime();
                        transport.close();
                        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(1));
                        assertThrows(A2AClientException.class, () -> unary(transport, peer));
                        assertRequests(peer, List.of(version));
                        assertEquals(1, peer.serviceCalls.get());
                    } finally {
                        transport.close();
                    }
                }
            }
        }
    }

    private static GrpcTransport managed(Peer peer, String version, Duration timeout) {
        // Omitting useTransportSecurity exercises the default TLS setting.
        GrpcManagedChannelOptions options = GrpcManagedChannelOptions.builder()
                .authentication(Map.of("Authorization", TOKEN)).shutdownTimeout(timeout).build();
        return GrpcTransport.createManaged(options, card(peer.endpoint()), version);
    }

    private static void unary(GrpcTransport transport, Peer peer) throws Exception {
        try (Context.CancellableContext deadline = Context.current().withDeadlineAfter(5, TimeUnit.SECONDS, peer.deadlines)) {
            assertEquals("task", deadline.call(() -> transport.getTaskPushNotificationConfiguration(
                    new GetTaskPushNotificationConfigParams("task"), null)).taskId());
        }
    }

    private static boolean hasHandshakeFailure(@Nullable Throwable failure) {
        while (failure != null) {
            if (failure instanceof SSLHandshakeException) {
                return true;
            }
            failure = failure.getCause();
        }
        return false;
    }

    private static void assertRequests(Peer peer, List<String> versions) {
        assertEquals(versions.size(), peer.observations.size());
        for (int index = 0; index < versions.size(); index++) {
            Observation observation = peer.observations.get(index);
            assertEquals(List.of(versions.get(index)), observation.versions());
            assertEquals(TOKEN, observation.authorization());
            assertTrue(observation.tls(), "Successful Netty HTTP/2 RPC must have a TLS session");
        }
    }

    private static AgentCard card(String endpoint) {
        return AgentCard.builder().name("lifecycle").description("lifecycle").version("1")
                .supportedInterfaces(List.of(new AgentInterface("GRPC", endpoint, null, "1.0"),
                        new AgentInterface("GRPC", endpoint, null, "1.1")))
                .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(true).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
    }

    private static InputStream fixture(String name) {
        return Objects.requireNonNull(GrpcOwnedLifecycleTest.class.getResourceAsStream("/owned-tls/" + name));
    }

    private record Observation(List<String> versions, @Nullable String authorization, boolean tls) { }

    private static final class ClientStream {
        private final CountDownLatch firstEvent = new CountDownLatch(1);
        private final CountDownLatch terminal = new CountDownLatch(1);
        private final AtomicInteger events = new AtomicInteger();
        private final AtomicInteger terminals = new AtomicInteger();
        private final AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();

        private void start(GrpcTransport transport) {
            transport.sendMessageStreaming(new MessageSendParams(MESSAGE, null, null), event -> {
                events.incrementAndGet();
                firstEvent.countDown();
            }, error -> {
                failure.set(error);
                terminals.incrementAndGet();
                terminal.countDown();
            }, null);
        }
    }

    private static final class Peer implements AutoCloseable {
        private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
        private final List<Observation> observations = new CopyOnWriteArrayList<>();
        private final AtomicInteger serviceCalls = new AtomicInteger();
        private final AtomicInteger cancellations = new AtomicInteger();
        private final CountDownLatch streamStarted = new CountDownLatch(1);
        private final CountDownLatch streamCancelled = new CountDownLatch(1);
        private final Server server;

        private Peer(boolean holdStream) throws Exception {
            try (InputStream certificate = fixture("localhost-cert.pem"); InputStream key = fixture("localhost-key.pem")) {
                server = NettyServerBuilder.forPort(0).directExecutor()
                        .sslContext(GrpcSslContexts.forServer(certificate, key).build())
                        .addService(ServerInterceptors.intercept(new A2AServiceGrpc.A2AServiceImplBase() {
                            @Override
                            public void getTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest request,
                                    StreamObserver<org.a2aproject.sdk.grpc.TaskPushNotificationConfig> response) {
                                serviceCalls.incrementAndGet();
                                response.onNext(org.a2aproject.sdk.grpc.TaskPushNotificationConfig.newBuilder()
                                        .setTaskId("task").setId("config").setUrl("https://callback.test").build());
                                response.onCompleted();
                            }

                            @Override
                            public void sendStreamingMessage(SendMessageRequest request, StreamObserver<StreamResponse> response) {
                                serviceCalls.incrementAndGet();
                                ServerCallStreamObserver<StreamResponse> stream = (ServerCallStreamObserver<StreamResponse>) response;
                                stream.setOnCancelHandler(() -> {
                                    cancellations.incrementAndGet();
                                    streamCancelled.countDown();
                                });
                                stream.onNext(StreamResponse.newBuilder().setMessage(ToProto.message(MESSAGE)).build());
                                streamStarted.countDown();
                                if (!holdStream) {
                                    stream.onCompleted();
                                }
                            }
                        }, new ServerInterceptor() {
                            @Override
                            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                    Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                                List<String> versions = new ArrayList<>();
                                Iterable<String> supplied = headers.getAll(VERSION);
                                if (supplied != null) {
                                    supplied.forEach(versions::add);
                                }
                                SSLSession session = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
                                observations.add(new Observation(List.copyOf(versions), headers.get(AUTH),
                                        session != null && session.getProtocol().startsWith("TLS")));
                                return next.startCall(call, headers);
                            }
                        })).build().start();
            }
        }

        private String endpoint() {
            return "https://localhost:" + server.getPort();
        }

        @Override
        public void close() throws InterruptedException {
            try {
                server.shutdownNow();
                assertTrue(server.awaitTermination(5, TimeUnit.SECONDS));
            } finally {
                deadlines.shutdownNow();
                assertTrue(deadlines.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    /** Install an ordinary JVM PKCS12 truststore; do not bypass certificate or hostname checks. */
    private static final class SystemTrust implements AutoCloseable {
        private static final List<String> PROPERTIES = List.of("javax.net.ssl.trustStore", "javax.net.ssl.trustStoreType",
                "javax.net.ssl.trustStorePassword");
        private final Map<String, @Nullable String> previous = new HashMap<>();
        private final @Nullable Path trustStore;

        private SystemTrust(boolean trustFixture) throws Exception {
            for (String property : PROPERTIES) {
                previous.put(property, System.getProperty(property));
            }
            if (trustFixture) {
                KeyStore store = KeyStore.getInstance("PKCS12");
                store.load(null, null);
                try (InputStream input = fixture("localhost-cert.pem")) {
                    X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
                    certificate.checkValidity();
                    store.setCertificateEntry("owned-localhost-peer", certificate);
                }
                trustStore = Files.createTempFile("a2a-owned-tls-", ".p12");
                try (OutputStream output = Files.newOutputStream(trustStore)) {
                    store.store(output, "owned-test".toCharArray());
                }
                System.setProperty("javax.net.ssl.trustStore", trustStore.toString());
                System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
                System.setProperty("javax.net.ssl.trustStorePassword", "owned-test");
            } else {
                trustStore = null;
                PROPERTIES.forEach(System::clearProperty);
            }
        }

        private static SystemTrust trustFixture() throws Exception {
            return new SystemTrust(true);
        }

        private static SystemTrust systemDefaults() throws Exception {
            return new SystemTrust(false);
        }

        @Override
        public void close() throws Exception {
            previous.forEach((property, value) -> {
                if (value == null) {
                    System.clearProperty(property);
                } else {
                    System.setProperty(property, value);
                }
            });
            if (trustStore != null) {
                Files.deleteIfExists(trustStore);
            }
        }
    }
}
