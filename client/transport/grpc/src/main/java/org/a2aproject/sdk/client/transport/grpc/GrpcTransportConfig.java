package org.a2aproject.sdk.client.transport.grpc;

import java.util.function.Function;

import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.util.Assert;
import io.grpc.Channel;

/**
 * Configuration for SDK-owned gRPC channels. Use
 * {@link GrpcTransportConfigBuilder#managedChannel(GrpcManagedChannelOptions)}.
 * Each transport owns a dedicated channel and closes it within its configured timeout.
 * TLS with system trust is the default; plaintext requires explicit selection.
 * <p>
 * The legacy factory constructor and getter retain their descriptors. Arbitrary factories
 * are rejected before invocation for both protocol 1.0 and 1.1. SDK-issued factory handles
 * are bound to the selected endpoint and may be claimed by only one transport.
 * <pre>{@code
 * GrpcTransportConfig config = new GrpcTransportConfigBuilder()
 *     .managedChannel(GrpcManagedChannelOptions.builder().build())
 *     .build();
 * }</pre>
 */
public class GrpcTransportConfig extends ClientTransportConfig<GrpcTransport> {

    private final Function<String, Channel> channelFactory;

    /**
     * Retains the legacy factory descriptor. Arbitrary factories are rejected by the provider.
     * <p>
     * Consider using {@link GrpcTransportConfigBuilder} instead for a more fluent API.
     *
     * @param channelFactory function to create gRPC channels from agent URLs (must not be null)
     * @throws IllegalArgumentException if channelFactory is null
     */
    public GrpcTransportConfig(Function<String, Channel> channelFactory) {
        Assert.checkNotNullParam("channelFactory", channelFactory);
        this.channelFactory = channelFactory;
    }

    /**
     * Get the configured channel factory.
     *
     * @return the channel factory function
     */
    public Function<String, Channel> getChannelFactory() {
        return this.channelFactory;
    }
}
