package org.a2aproject.sdk.extras.multitenancy.tests.grpc;

import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.extras.multitenancy.tests.AbstractMultiTenantServerTest;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.AfterAll;

@QuarkusTest
public class MultiTenantGrpcTest extends AbstractMultiTenantServerTest {

    private static ManagedChannel channel;

    public MultiTenantGrpcTest() {
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
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder().channelFactory(target -> {
            channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            return channel;
        }));
    }

    @AfterAll
    public static void closeChannel() {
        if (channel != null) {
            channel.shutdownNow();
            try {
                channel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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
