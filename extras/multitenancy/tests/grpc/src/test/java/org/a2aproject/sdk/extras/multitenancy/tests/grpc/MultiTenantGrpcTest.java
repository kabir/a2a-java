package org.a2aproject.sdk.extras.multitenancy.tests.grpc;

import io.quarkus.test.junit.QuarkusTest;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcManagedChannelOptions;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.extras.multitenancy.tests.AbstractMultiTenantServerTest;
import org.a2aproject.sdk.spec.TransportProtocol;

@QuarkusTest
public class MultiTenantGrpcTest extends AbstractMultiTenantServerTest {

    public MultiTenantGrpcTest() {
        super(TEST_PORT);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "localhost:" + TEST_PORT;
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder().managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()));
    }

    // gRPC-only deployments do not serve the well-known public-card endpoints.
    @Override
    public void publicCardWithoutTenantReturnsDefault() {
        // no-op: not served by gRPC
    }

    @Override
    public void publicCardWithAcmeTenant() {
        // no-op: not served by gRPC
    }

    @Override
    public void publicCardWithBetaTenant() {
        // no-op: not served by gRPC
    }

    @Override
    public void publicCardUnknownTenantReturns404() {
        // no-op: not served by gRPC
    }
}
