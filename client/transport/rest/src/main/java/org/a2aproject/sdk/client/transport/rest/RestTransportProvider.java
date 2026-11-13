package org.a2aproject.sdk.client.transport.rest;

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

public class RestTransportProvider implements ClientTransportProvider<RestTransport, RestTransportConfig> {

    @Override
    public Map<String, Set<ClientFeature>> capabilities() {
        return Map.of("1.0", Set.of(ClientFeature.ORDINARY), "1.1", Set.of(ClientFeature.ORDINARY,
                ClientFeature.GUARDED_INPUT, ClientFeature.ARTIFACT_INPUT, ClientFeature.UNKNOWN_VARIANT_SKIP));
    }

    @Override
    public String getTransportProtocol() {
        return TransportProtocol.HTTP_JSON.asString();
    }

    @Override
    public RestTransport create(RestTransportConfig clientTransportConfig, AgentCard agentCard, AgentInterface agentInterface) throws A2AClientException {
        RestTransportConfig transportConfig = clientTransportConfig;
         if (transportConfig == null) {
            transportConfig = new RestTransportConfig(A2AHttpClientFactory.create(A2AHttpClientConfiguration.DEFAULT));
        }
        return new RestTransport(transportConfig.getHttpClient(), agentCard, agentInterface,
                transportConfig.getInterceptors(), transportConfig.isFollowRedirects());
    }

    @Override
    public Class<RestTransport> getTransportProtocolClass() {
        return RestTransport.class;
    }
}
