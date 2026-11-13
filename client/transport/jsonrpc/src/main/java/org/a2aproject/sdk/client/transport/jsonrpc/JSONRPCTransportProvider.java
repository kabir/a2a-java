package org.a2aproject.sdk.client.transport.jsonrpc;

import java.util.Map;
import java.util.Set;
import org.a2aproject.sdk.client.http.A2AHttpClientConfiguration;
import org.a2aproject.sdk.client.http.A2AHttpClientFactory;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientTransportProvider;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.jspecify.annotations.Nullable;

public class JSONRPCTransportProvider implements ClientTransportProvider<JSONRPCTransport, JSONRPCTransportConfig> {

    private static final Set<ClientFeature> ONE_ONE_CAPABILITIES = Set.of(ClientFeature.ORDINARY,
            ClientFeature.GUARDED_INPUT, ClientFeature.ARTIFACT_INPUT, ClientFeature.UNKNOWN_VARIANT_SKIP);

    @Override
    public Map<String, Set<ClientFeature>> capabilities() {
        return Map.of("1.0", Set.of(ClientFeature.ORDINARY), "1.1", ONE_ONE_CAPABILITIES);
    }

    @Override
    public JSONRPCTransport create(@Nullable JSONRPCTransportConfig clientTransportConfig, AgentCard agentCard, AgentInterface agentInterface) throws A2AClientException {
        JSONRPCTransportConfig currentClientTransportConfig = clientTransportConfig;
        if (currentClientTransportConfig == null) {
            currentClientTransportConfig = new JSONRPCTransportConfig(
                    A2AHttpClientFactory.create(A2AHttpClientConfiguration.DEFAULT));
        }
        return new JSONRPCTransport(currentClientTransportConfig.getHttpClient(), agentCard, agentInterface,
                currentClientTransportConfig.getInterceptors(), currentClientTransportConfig.isFollowRedirects());
    }

    @Override
    public String getTransportProtocol() {
        return TransportProtocol.JSONRPC.asString();
    }

    @Override
    public Class<JSONRPCTransport> getTransportProtocolClass() {
        return JSONRPCTransport.class;
    }
}
