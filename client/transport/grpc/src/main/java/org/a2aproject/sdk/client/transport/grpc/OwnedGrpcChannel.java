package org.a2aproject.sdk.client.transport.grpc;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientStreamTracer;
import io.grpc.ForwardingClientCall;
import io.grpc.InternalConfiguratorRegistry;
import io.grpc.LoadBalancerProvider;
import io.grpc.LoadBalancerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.Status;
import io.grpc.Uri;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import io.grpc.netty.InternalNettyChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import org.a2aproject.sdk.client.transport.spi.ClientProtocolVersion;
import org.a2aproject.sdk.spec.A2AClientException;
import org.jspecify.annotations.Nullable;

/** SDK-issued, single-owner channel; no user-programmable routing or tracing callbacks. */
final class OwnedGrpcChannel extends Channel implements AutoCloseable {
    static final CallOptions.Key<String> VERSION_OPTION = CallOptions.Key.create("a2a-sdk-owned-version");
    private static final Metadata.Key<String> VERSION = Metadata.Key.of("a2a-version", Metadata.ASCII_STRING_MARSHALLER);
    private final ManagedChannel delegate;
    private final String endpoint;
    private final String expectedAuthority;
    private final GrpcManagedChannelOptions options;
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile @Nullable String selectedVersion;

    @SuppressWarnings("deprecation") // Pinned gRPC exposes private resolver registries through this API.
    private OwnedGrpcChannel(String endpoint, GrpcManagedChannelOptions options) {
        this.endpoint = endpoint;
        this.options = options;
        this.expectedAuthority = authorityFor(endpoint);
        rejectGlobalConfigurators();
        NettyChannelBuilder builder = NettyChannelBuilder.forTarget("dns:///" + expectedAuthority)
                .nameResolverFactory(new SdkDnsResolverProvider())
                .proxyDetector(address -> null)
                .disableServiceConfigLookUp();
        // Pinned gRPC 1.81.0: prevent classpath-discovered Census interceptors from appending tracers.
        InternalNettyChannelBuilder.setStatsEnabled(builder, false);
        InternalNettyChannelBuilder.setTracingEnabled(builder, false);
        if (options.plaintext()) {
            builder.usePlaintext();
        } else {
            builder.useTransportSecurity();
        }
        LoadBalancerRegistry registry = LoadBalancerRegistry.getDefaultRegistry();
        synchronized (registry) {
            // gRPC 1.81.0 synchronizes register/deregister/getProvider on this monitor and
            // captures the default provider in a final field synchronously during build().
            // Exact final-class identity excludes same-name providers and subclass callbacks.
            LoadBalancerProvider provider = registry.getProvider("pick_first");
            if (provider == null || provider.getClass() != PickFirstLoadBalancerProvider.class) {
                throw new A2AClientException("Custom gRPC pick_first load-balancer providers are unsupported by SDK-owned channels");
            }
            delegate = builder.build();
        }
        if (!InternalConfiguratorRegistry.getConfigurators().isEmpty()) {
            delegate.shutdownNow();
            throw new A2AClientException("Global gRPC configurators are unsupported by SDK-owned channels");
        }
    }

    /** Stock DNS with a private registry, no proxy callback, and no dynamic policy parser. */
    private static final class SdkDnsResolverProvider extends NameResolverProvider {
        private final DnsNameResolverProvider delegate = new DnsNameResolverProvider();
        private static final NameResolver.ServiceConfigParser NO_SERVICE_CONFIG = new NameResolver.ServiceConfigParser() {
            @Override
            public NameResolver.ConfigOrError parseServiceConfig(Map<String, ?> ignored) {
                return NameResolver.ConfigOrError.fromError(Status.UNAVAILABLE.withDescription("DNS service configs are disabled"));
            }
        };

        private static NameResolver.Args isolatedArgs(NameResolver.Args args) {
            return args.toBuilder().setServiceConfigParser(NO_SERVICE_CONFIG).build();
        }

        @Override
        public @Nullable NameResolver newNameResolver(URI target, NameResolver.Args args) {
            return delegate.newNameResolver(target, isolatedArgs(args));
        }

        @Override
        public @Nullable NameResolver newNameResolver(Uri target, NameResolver.Args args) {
            return delegate.newNameResolver(target, isolatedArgs(args));
        }

        @Override
        public String getDefaultScheme() {
            return "dns";
        }

        @Override
        protected boolean isAvailable() {
            return true;
        }

        @Override
        protected int priority() {
            return 5;
        }

        @Override
        public Collection<Class<? extends SocketAddress>> getProducedSocketAddressTypes() {
            return List.of(InetSocketAddress.class);
        }
    }

    private static void rejectGlobalConfigurators() {
        if (!InternalConfiguratorRegistry.getConfigurators().isEmpty()) {
            throw new A2AClientException("Global gRPC configurators are unsupported by SDK-owned channels");
        }
    }

    static Function<String, Channel> factory(GrpcManagedChannelOptions options) {
        return new Factory(options);
    }

    static boolean isOwnedFactory(Function<String, Channel> factory) {
        return factory instanceof Factory;
    }

    static OwnedGrpcChannel claim(Channel channel, String endpoint, String version) {
        if (!(channel instanceof OwnedGrpcChannel owned)) {
            throw new A2AClientException("Opaque gRPC channels are unsupported for protocol " + version
                    + "; migrate to GrpcTransportConfigBuilder.managedChannel or GrpcTransport.createManaged");
        }
        // Scope validation precedes claiming: a failed candidate must not consume another owner's handle.
        if (!owned.endpoint.equals(endpoint) || !owned.expectedAuthority.equals(owned.delegate.authority())) {
            throw new A2AClientException("SDK-owned gRPC channel endpoint/authority does not match the selected descriptor");
        }
        if (owned.closed.get() || !owned.claimed.compareAndSet(false, true)) {
            throw new A2AClientException("SDK-owned gRPC channel is closed or already claimed; sharing is unsupported");
        }
        owned.selectedVersion = version;
        return owned;
    }

    private static String authorityFor(String endpoint) {
        try {
            URI uri = URI.create(endpoint.contains("://") ? endpoint : "https://" + endpoint);
            String scheme = uri.getScheme();
            String authority = uri.getRawAuthority();
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null || authority == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))) {
                throw new A2AClientException("SDK-managed gRPC endpoints require a host and optional port: " + endpoint);
            }
            return authority;
        } catch (IllegalArgumentException error) {
            throw new A2AClientException("Invalid SDK-managed gRPC endpoint: " + endpoint, error);
        }
    }

    @Override
    public String authority() {
        return expectedAuthority;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
        String version = selectedVersion;
        if (closed.get() || version == null || !version.equals(callOptions.getOption(VERSION_OPTION))) {
            throw new A2AClientException("SDK-owned gRPC call requires its immutable selected-version pin and an open owner");
        }
        if (!callOptions.getStreamTracerFactories().isEmpty() || callOptions.getCredentials() != null) {
            throw new A2AClientException("Untrusted gRPC tracers and CallCredentials are unsupported; use SDK authentication metadata");
        }
        ClientStreamTracer.Factory guard = new ClientStreamTracer.Factory() {
            @Override
            public ClientStreamTracer newClientStreamTracer(ClientStreamTracer.StreamInfo info, Metadata headers) {
                normalize(headers, version);
                return new ClientStreamTracer() {
                    @Override
                    public void streamCreated(Attributes attributes, Metadata finalHeaders) {
                        // Only audited backend callbacks can follow this SDK-owned assertion.
                        normalize(finalHeaders, version);
                    }
                };
            }
        };
        ClientCall<ReqT, RespT> call = delegate.newCall(method, callOptions.withStreamTracerFactory(guard));
        return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {
            @Override
            public void start(Listener<RespT> listener, Metadata headers) {
                Metadata copied = new Metadata();
                copied.merge(headers);
                Map<String, String> authentication = ClientProtocolVersion.normalizeHeaders(options.authenticationHeaders(), version);
                for (Map.Entry<String, String> entry : authentication.entrySet()) {
                    copied.put(Metadata.Key.of(entry.getKey().toLowerCase(Locale.ROOT), Metadata.ASCII_STRING_MARSHALLER), entry.getValue());
                }
                normalize(copied, version);
                try {
                    super.start(listener, copied);
                } catch (RuntimeException | Error failure) {
                    try {
                        call.cancel("A2A protocol metadata validation failed", failure);
                    } catch (RuntimeException | Error cancellationFailure) {
                        failure.addSuppressed(cancellationFailure);
                    }
                    throw failure;
                }
            }
        };
    }

    private static void normalize(Metadata metadata, String version) {
        Iterable<String> supplied = metadata.getAll(VERSION);
        if (supplied != null) {
            for (String value : supplied) {
                if (!version.equals(value)) {
                    throw new A2AClientException("gRPC metadata conflicts with selected protocol version " + version + ": " + value);
                }
            }
        }
        metadata.discardAll(VERSION);
        metadata.put(VERSION, version);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Duration timeout = options.shutdownTimeout();
        long deadline = System.nanoTime() + timeout.toNanos();
        delegate.shutdown();
        try {
            if (!delegate.awaitTermination(timeout.toNanos() / 2, TimeUnit.NANOSECONDS)) {
                delegate.shutdownNow();
                delegate.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            }
        } catch (InterruptedException interrupted) {
            delegate.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class Factory implements Function<String, Channel> {
        private final GrpcManagedChannelOptions options;

        private Factory(GrpcManagedChannelOptions options) {
            this.options = options;
        }

        @Override
        public Channel apply(String endpoint) {
            return new OwnedGrpcChannel(endpoint, options);
        }
    }
}
