package org.a2aproject.sdk.client.transport.grpc;

import java.util.function.Function;

import org.a2aproject.sdk.client.transport.spi.ClientTransportConfigBuilder;
import org.a2aproject.sdk.util.Assert;
import io.grpc.Channel;
import org.jspecify.annotations.Nullable;

/**
 * Builds gRPC configuration with declarative SDK-owned channel settings.
 * Select {@link #managedChannel(GrpcManagedChannelOptions)}; SDK client interceptors
 * remain supported and their metadata is checked against the immutable protocol version.
 * Arbitrary gRPC interceptors, credentials, channel sharing and mutable builders are unsupported.
 * The legacy raw factory API remains source/binary compatible but its use fails before
 * factory invocation when creating a transport for either protocol 1.0 or 1.1.
 */
public class GrpcTransportConfigBuilder extends ClientTransportConfigBuilder<GrpcTransportConfig, GrpcTransportConfigBuilder> {

    private @Nullable Function<String, Channel> channelFactory;
    private @Nullable GrpcManagedChannelOptions managedOptions;

    /** Selects SDK-owned channels with immutable settings and bounded transport-owned shutdown. */
    public GrpcTransportConfigBuilder managedChannel(GrpcManagedChannelOptions options) {
        Assert.checkNotNullParam("options", options);
        this.managedOptions = options;
        return this;
    }

    /**
     * Retains the legacy factory setter for Java source and binary compatibility.
     * Arbitrary functions are rejected before invocation for both protocol versions;
     * select {@link #managedChannel(GrpcManagedChannelOptions)} for new configurations.
     * An SDK-issued factory obtained from a managed configuration remains admissible.
     *
     * @param channelFactory legacy channel factory (must not be null)
     * @return this builder
     * @throws IllegalArgumentException if channelFactory is null
     */
    public GrpcTransportConfigBuilder channelFactory(Function<String, Channel> channelFactory) {
        Assert.checkNotNullParam("channelFactory", channelFactory);

        this.channelFactory = channelFactory;

        return this;
    }

    /**
     * Build the gRPC transport configuration.
     * <p>
     * Select managed channel settings. Legacy raw factory configurations may still be built,
     * but are rejected before factory invocation by the transport provider.
     *
     * @return the configured gRPC transport configuration
     * @throws IllegalStateException if the channel factory was not set
     */
    @Override
    public GrpcTransportConfig build() {
        if (managedOptions != null) {
            if (channelFactory != null) {
                throw new IllegalStateException("Choose managedChannel or channelFactory, not both");
            }
            GrpcTransportConfig config = new GrpcTransportConfig(OwnedGrpcChannel.factory(managedOptions));
            config.setInterceptors(interceptors);
            return config;
        }
        if (channelFactory == null) {
            throw new IllegalStateException("channelFactory must be set");
        }
        GrpcTransportConfig config = new GrpcTransportConfig(channelFactory);
        config.setInterceptors(interceptors);
        return config;
    }
}
