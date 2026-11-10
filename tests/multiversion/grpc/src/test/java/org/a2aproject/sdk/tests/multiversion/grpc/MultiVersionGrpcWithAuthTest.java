package org.a2aproject.sdk.tests.multiversion.grpc;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcManagedChannelOptions;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.auth.AuthInterceptor;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerWithAuthTest;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(AuthTestProfile.class)
public class MultiVersionGrpcWithAuthTest extends AbstractA2AServerWithAuthTest {

    @Inject
    @PublicAgentCard
    AgentCard agentCard;

    public MultiVersionGrpcWithAuthTest() {
        super(8081);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "localhost:8081";
    }

    @Override
    protected void configureTransportWithAuth(ClientBuilder builder) {
        AuthInterceptor authInterceptor = new AuthInterceptor(
                (schemeName, context) -> BASIC_AUTH_SCHEME_NAME.equals(schemeName) ? getEncodedCredentials() : null
        );

        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder()
                .managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build())
                .addInterceptor(authInterceptor));
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder().managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()));
    }

    @Override
    protected AgentCard fetchAgentCardFromServer() {
        return agentCard;
    }

    @Test
    @Override
    @Disabled
    public void testGetAgentCardIsPublic() {
        // Skip - gRPC doesn't have /.well-known/agent-card.json endpoint
    }

    @Test
    @Override
    @Disabled
    public void testBasicAuthWorksViaHttp() {
        // Skip - HTTP-specific test
    }
}
