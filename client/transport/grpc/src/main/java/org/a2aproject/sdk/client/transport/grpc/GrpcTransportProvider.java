package org.a2aproject.sdk.client.transport.grpc;

import static org.a2aproject.sdk.util.Assert.checkNotNullParam;

import io.grpc.Channel;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientProtocolVersion;
import org.a2aproject.sdk.client.transport.spi.ClientTransportProvider;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.spec.util.Utils;

/**
 * Provider for gRPC transport implementation.
 */
public class GrpcTransportProvider implements ClientTransportProvider<GrpcTransport, GrpcTransportConfig> {

    @Override
    public Map<String, Set<ClientFeature>> capabilities() {
        return Map.of("1.0", Set.of(ClientFeature.ORDINARY), "1.1", Set.of(ClientFeature.ORDINARY));
    }

    @Override
    public GrpcTransport create(GrpcTransportConfig grpcTransportConfig, AgentCard agentCard, AgentInterface agentInterface) throws A2AClientException {
        Function<String, Channel> channelFactory = grpcTransportConfig.getChannelFactory();
        if (!OwnedGrpcChannel.isOwnedFactory(channelFactory)) {
            throw new A2AClientException("Opaque gRPC channel factories are unsupported; migrate to GrpcTransportConfigBuilder.managedChannel");
        }
        checkNotNullParam("agentCard", agentCard);
        checkNotNullParam("agentInterface", agentInterface);
        ClientProtocolVersion.validate(agentInterface.protocolVersion());
        Utils.validateTenant(agentInterface.tenant());
        Channel channel = channelFactory.apply(agentInterface.url());
        if (channel == null) {
            throw new A2AClientException("Missing required GrpcTransportConfig");
        }
        try {
            return GrpcTransport.forInterface(channel, agentCard, agentInterface, grpcTransportConfig.getInterceptors());
        } catch (RuntimeException | Error failure) {
            // The captured SDK factory always returns a fresh handle, never another transport's owner.
            try {
                ((OwnedGrpcChannel) channel).close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }

    @Override
    public Class<GrpcTransport> getTransportProtocolClass() {
        return GrpcTransport.class;
    }
}
