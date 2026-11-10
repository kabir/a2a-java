package org.a2aproject.sdk.client.transport.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.junit.jupiter.api.Test;

class GrpcOpaqueChannelAdmissionTest {
    @Test
    void directOpaqueChannelsAreRejectedBeforeNewCallForBothVersions() {
        for (String version : List.of("1.0", "1.1")) {
            AtomicInteger calls = new AtomicInteger();
            Channel opaque = new Channel() {
                @Override
                public String authority() {
                    return "localhost";
                }

                @Override
                public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method,
                        CallOptions options) {
                    calls.incrementAndGet();
                    throw new AssertionError("Opaque channel must not be invoked");
                }
            };
            assertThrows(A2AClientException.class, () -> new GrpcTransport(opaque, card(version), version));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void rawFactoriesAreRejectedBeforeFactoryInvocationForBothVersions() {
        for (String version : List.of("1.0", "1.1")) {
            AtomicInteger factories = new AtomicInteger();
            GrpcTransportConfig config = new GrpcTransportConfigBuilder().channelFactory(url -> {
                factories.incrementAndGet();
                throw new AssertionError("Raw factory must not be invoked");
            }).build();
            assertThrows(A2AClientException.class, () -> new GrpcTransportProvider().create(config,
                    card(version), new AgentInterface("GRPC", "http://localhost:9999", null, version)));
            assertEquals(0, factories.get());
        }
    }

    private static AgentCard card(String version) {
        return AgentCard.builder().name("strict admission").description("strict admission").version("1")
                .supportedInterfaces(List.of(new AgentInterface("GRPC", "http://localhost:9999", null, version)))
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
    }
}
