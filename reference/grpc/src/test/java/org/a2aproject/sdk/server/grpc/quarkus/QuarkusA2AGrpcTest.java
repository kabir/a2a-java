package org.a2aproject.sdk.server.grpc.quarkus;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcManagedChannelOptions;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerTest;
import org.a2aproject.sdk.spec.TransportProtocol;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuarkusA2AGrpcTest extends AbstractA2AServerTest {

    public QuarkusA2AGrpcTest() {
        super(8081); // HTTP server port for utility endpoints
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        // gRPC server runs on port 8081, which is the same port as the main web server.
        // This is from the setting on the
        return "localhost:8081";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder().managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()));
    }

    @Override
    public void testAgentCardHeaders() {
        // Skip - gRPC doesn't use HTTP caching headers for Agent Card
        // The A2A spec section 8.6 caching requirements apply only to HTTP endpoints
    }
}