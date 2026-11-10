package org.a2aproject.sdk.client.transport.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.Attributes;
import io.grpc.Channel;
import io.grpc.ClientStreamTracer;
import io.grpc.ConnectivityState;
import io.grpc.InternalConfigurator;
import io.grpc.InternalConfiguratorRegistry;
import io.grpc.LoadBalancer;
import io.grpc.LoadBalancerProvider;
import io.grpc.LoadBalancerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerStreamTracer;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.MetadataUtils;
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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/** Adversarial public global providers, including the final LB tracer slot. */
@Timeout(30)
@Isolated("Mutates global gRPC providers and ProxySelector")
class GrpcGlobalProviderIsolationTest {
    private static final Metadata.Key<String> VERSION = Metadata.Key.of("a2a-version", Metadata.ASCII_STRING_MARSHALLER);
    private static final Message MESSAGE = Message.builder().messageId("provider-test").role(Message.Role.ROLE_USER)
            .parts(new TextPart("hello")).build();

    @Test
    void attackControlReallyAppendsAfterTheApplicationHeaderOnRawNetty() throws Exception {
        HostileBalancer provider = new HostileBalancer("1.0");
        LoadBalancerRegistry registry = LoadBalancerRegistry.getDefaultRegistry();
        registry.register(provider);
        try (Peer peer = new Peer()) {
            ManagedChannel raw = NettyChannelBuilder.forTarget(peer.authority()).usePlaintext().build();
            try {
                Metadata metadata = new Metadata();
                metadata.put(VERSION, "1.1");
                A2AServiceGrpc.newBlockingV2Stub(raw).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                        .getTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest.newBuilder().setTaskId("task").build());
                assertEquals(List.of(List.of("1.1", "1.0")), peer.versions);
                assertTrue(provider.callbacks.get() > 0);
            } finally {
                raw.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        } finally {
            registry.deregister(provider);
        }
    }

    @Test
    void globalProxySelectorCannotRedirectOwnedChannels() throws Exception {
        ProxySelector previous = ProxySelector.getDefault();
        AtomicInteger callbacks = new AtomicInteger();
        ProxySelector.setDefault(new ProxySelector() {
            @Override public List<Proxy> select(URI uri) {
                callbacks.incrementAndGet();
                return List.of(Proxy.NO_PROXY);
            }
            @Override public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
        });
        try (Peer peer = new Peer()) {
            for (String version : List.of("1.0", "1.1")) {
                GrpcTransport transport = GrpcTransport.createManaged(options(), card(peer.endpoint()), version);
                try {
                    unary(transport);
                    stream(transport);
                } finally {
                    transport.close();
                }
            }
            assertEquals(0, callbacks.get());
            assertEquals(4, peer.streams.get());
        } finally {
            ProxySelector.setDefault(previous);
        }
    }

    @Test
    void globalConfiguratorIsRejectedBeforeItsCallbackInAnIsolatedJvm(@TempDir Path directory) throws Exception {
        Path output = directory.resolve("configurator.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                ConfiguratorProbe.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Configurator probe exceeded its finite deadline");
            assertEquals(0, process.exitValue(), Files.readString(output));
            assertTrue(Files.readString(output).contains("REJECTED_NO_CALLBACK"));
        } finally {
            process.destroyForcibly();
            assertTrue(process.waitFor(2, TimeUnit.SECONDS));
        }
    }

    /** A fresh JVM is required because the gRPC global configurator registry is initialized once. */
    public static final class ConfiguratorProbe {
        public static void main(String[] args) {
            AtomicInteger callbacks = new AtomicInteger();
            InternalConfiguratorRegistry.setConfigurators(List.of(new InternalConfigurator() {
                @Override public void configureChannelBuilder(ManagedChannelBuilder<?> builder) {
                    callbacks.incrementAndGet();
                }
            }));
            for (String version : List.of("1.0", "1.1")) {
                try {
                    GrpcTransport.createManaged(options(), card("http://127.0.0.1:1"), version);
                    throw new AssertionError("Global configurator must reject construction");
                } catch (A2AClientException expected) {
                    if (!expected.getMessage().contains("configurators")) {
                        throw new AssertionError("Wrong rejection", expected);
                    }
                }
            }
            if (callbacks.get() != 0) {
                throw new AssertionError("An untrusted configurator was invoked");
            }
            System.out.println("REJECTED_NO_CALLBACK");
        }
    }

    @Test
    void hostileBalancerBeforeConstructionIsRejectedBeforeCallbackOrServerStream() throws Exception {
        LoadBalancerRegistry registry = LoadBalancerRegistry.getDefaultRegistry();
        try (Peer peer = new Peer()) {
            for (String selected : List.of("1.0", "1.1")) {
                for (String injected : List.of("1.0", "1.1")) {
                    HostileBalancer provider = new HostileBalancer(injected);
                    registry.register(provider);
                    try {
                        A2AClientException failure = assertThrows(A2AClientException.class,
                                () -> GrpcTransport.createManaged(options(), card(peer.endpoint()), selected));
                        assertTrue(failure.getMessage().contains("load-balancer"));
                        assertEquals(0, provider.callbacks.get());
                    } finally {
                        registry.deregister(provider);
                    }
                }
            }
            assertEquals(0, peer.streams.get());
        }
    }

    @Test
    void hostileGlobalDnsResolverIsNeverInvoked() throws Exception {
        HostileResolver resolver = new HostileResolver();
        NameResolverRegistry registry = NameResolverRegistry.getDefaultRegistry();
        registry.register(resolver);
        try (Peer peer = new Peer()) {
            for (String version : List.of("1.0", "1.1")) {
                GrpcTransport transport = GrpcTransport.createManaged(options(), card(peer.endpoint()), version);
                try {
                    unary(transport);
                    stream(transport);
                } finally {
                    transport.close();
                }
            }
            assertEquals(0, resolver.callbacks.get());
            assertEquals(List.of(List.of("1.0"), List.of("1.0"), List.of("1.1"), List.of("1.1")), peer.versions);
        } finally {
            registry.deregister(resolver);
        }
    }

    @Test
    void replacementAfterBuildCannotAffectColdWarmOrRecreatedLoadBalancers() throws Exception {
        LoadBalancerRegistry registry = LoadBalancerRegistry.getDefaultRegistry();
        try (Peer peer = new Peer()) {
            for (String version : List.of("1.0", "1.1")) {
                Channel handle = new GrpcTransportConfigBuilder().managedChannel(options()).build().getChannelFactory().apply(peer.endpoint());
                GrpcTransport transport = GrpcTransport.forInterface(handle, card(peer.endpoint()), descriptor(peer.endpoint(), version), null);
                HostileBalancer provider = new HostileBalancer("1.0".equals(version) ? "1.1" : "1.0");
                registry.register(provider);
                try {
                    unary(transport);
                    stream(transport);
                    // Exercise recreation through the real private managed backend; no production test hook.
                    Field field = OwnedGrpcChannel.class.getDeclaredField("delegate");
                    field.setAccessible(true);
                    ((ManagedChannel) field.get(handle)).enterIdle();
                    unary(transport);
                    stream(transport);
                    assertEquals(0, provider.callbacks.get());
                } finally {
                    registry.deregister(provider);
                    transport.close();
                }
            }
            assertEquals(List.of(List.of("1.0"), List.of("1.0"), List.of("1.0"), List.of("1.0"),
                    List.of("1.1"), List.of("1.1"), List.of("1.1"), List.of("1.1")), peer.versions);
        }
    }

    @Test
    void concurrentRegistrationCannotSlipBetweenIdentityCheckAndProviderCapture() throws Exception {
        LoadBalancerRegistry registry = LoadBalancerRegistry.getDefaultRegistry();
        HostileBalancer provider = new HostileBalancer("1.0");
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Future<?> mutation = worker.submit(() -> {
            started.countDown();
            while (running.get()) {
                registry.register(provider);
                registry.deregister(provider);
                Thread.yield();
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        try (Peer peer = new Peer()) {
            for (int i = 0; i < 20; i++) {
                String version = i % 2 == 0 ? "1.0" : "1.1";
                GrpcTransport transport;
                try {
                    transport = GrpcTransport.createManaged(options(), card(peer.endpoint()), version);
                } catch (A2AClientException rejected) {
                    assertTrue(rejected.getMessage().contains("load-balancer"));
                    rejectedCount.incrementAndGet();
                    continue;
                }
                try {
                    accepted.incrementAndGet();
                    int before = peer.versions.size();
                    unary(transport);
                    assertEquals(List.of(version), peer.versions.get(before));
                } finally {
                    transport.close();
                }
            }
            assertEquals(0, provider.callbacks.get());
            assertEquals(20, accepted.get() + rejectedCount.get());
            System.out.println("Provider capture stress: accepted=" + accepted.get() + ", rejected=" + rejectedCount.get());
        } finally {
            running.set(false);
            mutation.get(5, TimeUnit.SECONDS);
            worker.shutdownNow();
            registry.deregister(provider);
        }
    }

    private static GrpcManagedChannelOptions options() {
        return GrpcManagedChannelOptions.builder().usePlaintext().build();
    }

    private static AgentInterface descriptor(String endpoint, String version) {
        return new AgentInterface("GRPC", endpoint, null, version);
    }

    private static AgentCard card(String endpoint) {
        return AgentCard.builder().name("provider").description("provider").version("1")
                .supportedInterfaces(List.of(descriptor(endpoint, "1.0"), descriptor(endpoint, "1.1")))
                .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(true).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
    }

    private static void unary(GrpcTransport transport) {
        assertEquals("task", transport.getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams("task"), null).taskId());
    }

    private static void stream(GrpcTransport transport) throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger events = new AtomicInteger();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        transport.sendMessageStreaming(new MessageSendParams(MESSAGE, null, null), event -> {
            events.incrementAndGet(); completed.countDown();
        }, error -> { if (error != null) { errors.add(error); } completed.countDown(); }, null);
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(), errors);
        assertEquals(1, events.get());
    }

    private static final class HostileBalancer extends LoadBalancerProvider {
        private final AtomicInteger callbacks = new AtomicInteger();
        private final String injected;
        private HostileBalancer(String injected) { this.injected = injected; }
        @Override public boolean isAvailable() { return true; }
        @Override public int getPriority() { return 100; }
        @Override public String getPolicyName() { return "pick_first"; }
        @Override public LoadBalancer newLoadBalancer(LoadBalancer.Helper helper) {
            callbacks.incrementAndGet();
            return new LoadBalancer() {
                private @Nullable Subchannel subchannel;
                @Override public Status acceptResolvedAddresses(ResolvedAddresses addresses) {
                    Subchannel selected = helper.createSubchannel(CreateSubchannelArgs.newBuilder()
                            .setAddresses(addresses.getAddresses()).setAttributes(Attributes.EMPTY).build());
                    subchannel = selected;
                    selected.start(state -> {
                        if (state.getState() == ConnectivityState.READY) {
                            helper.updateBalancingState(ConnectivityState.READY, new SubchannelPicker() {
                                @Override public PickResult pickSubchannel(PickSubchannelArgs args) {
                                    return PickResult.withSubchannel(selected, new ClientStreamTracer.Factory() {
                                        @Override public ClientStreamTracer newClientStreamTracer(ClientStreamTracer.StreamInfo info, Metadata headers) {
                                            return new ClientStreamTracer() {
                                                @Override public void streamCreated(Attributes attributes, Metadata actualHeaders) {
                                                    actualHeaders.put(VERSION, injected);
                                                }
                                            };
                                        }
                                    });
                                }
                            });
                        }
                    });
                    selected.requestConnection();
                    return Status.OK;
                }
                @Override public void handleNameResolutionError(Status error) {
                    helper.updateBalancingState(ConnectivityState.TRANSIENT_FAILURE, new SubchannelPicker() {
                        @Override public PickResult pickSubchannel(PickSubchannelArgs args) { return PickResult.withError(error); }
                    });
                }
                @Override public void shutdown() {
                    if (subchannel != null) { subchannel.shutdown(); }
                }
            };
        }
    }

    private static final class HostileResolver extends NameResolverProvider {
        private final AtomicInteger callbacks = new AtomicInteger();
        @Override public boolean isAvailable() { return true; }
        @Override public int priority() { return 100; }
        @Override public String getDefaultScheme() { return "dns"; }
        @Override public NameResolver newNameResolver(URI target, NameResolver.Args args) {
            callbacks.incrementAndGet();
            throw new AssertionError("Global resolver must not receive the selected endpoint");
        }
    }

    private static final class Peer implements AutoCloseable {
        private final List<List<String>> versions = new CopyOnWriteArrayList<>();
        private final AtomicInteger streams = new AtomicInteger();
        private final Server server;
        private Peer() throws Exception {
            server = NettyServerBuilder.forPort(0).directExecutor()
                    .addStreamTracerFactory(new ServerStreamTracer.Factory() {
                        @Override public ServerStreamTracer newServerStreamTracer(String method, Metadata headers) {
                            streams.incrementAndGet(); return new ServerStreamTracer() { };
                        }
                    }).addService(ServerInterceptors.intercept(new A2AServiceGrpc.A2AServiceImplBase() {
                        @Override public void getTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest request,
                                StreamObserver<org.a2aproject.sdk.grpc.TaskPushNotificationConfig> response) {
                            response.onNext(org.a2aproject.sdk.grpc.TaskPushNotificationConfig.newBuilder()
                                    .setTaskId(request.getTaskId()).setId("config").setUrl("https://callback.test").build());
                            response.onCompleted();
                        }
                        @Override public void sendStreamingMessage(SendMessageRequest request, StreamObserver<StreamResponse> response) {
                            response.onNext(StreamResponse.newBuilder().setMessage(ToProto.message(MESSAGE)).build());
                            response.onCompleted();
                        }
                    }, new ServerInterceptor() {
                        @Override public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                            List<String> actual = new ArrayList<>();
                            Iterable<String> values = headers.getAll(VERSION);
                            assertNotNull(values); values.forEach(actual::add);
                            versions.add(List.copyOf(actual));
                            return next.startCall(call, headers);
                        }
                    })).build().start();
        }
        private String authority() { return "127.0.0.1:" + server.getPort(); }
        private String endpoint() { return "http://" + authority(); }
        @Override public void close() throws InterruptedException { server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS); }
    }
}
