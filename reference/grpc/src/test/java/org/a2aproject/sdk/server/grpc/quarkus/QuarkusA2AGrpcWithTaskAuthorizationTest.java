package org.a2aproject.sdk.server.grpc.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcManagedChannelOptions;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.auth.AuthInterceptor;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerWithTaskAuthorizationTest;
import org.a2aproject.sdk.server.apps.common.TaskAuthorizationTestProfile;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.TransportProtocol;

@QuarkusTest
@TestProfile(TaskAuthorizationTestProfile.class)
public class QuarkusA2AGrpcWithTaskAuthorizationTest extends AbstractA2AServerWithTaskAuthorizationTest {

    @Inject
    @PublicAgentCard
    AgentCard agentCard;

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "localhost:8081";
    }

    @Override
    protected AgentCard fetchAgentCardFromServer() {
        return agentCard;
    }

    @Override
    protected void configureTransportWithCredentials(ClientBuilder builder, String username, String password) {
        AuthInterceptor authInterceptor = new AuthInterceptor(
                (schemeName, context) -> BASIC_AUTH_SCHEME_NAME.equals(schemeName)
                        ? getEncodedCredentials(username, password) : null);
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder()
                .managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build())
                .addInterceptor(authInterceptor));
    }

}
