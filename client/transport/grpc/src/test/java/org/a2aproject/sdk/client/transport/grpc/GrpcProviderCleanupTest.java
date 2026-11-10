package org.a2aproject.sdk.client.transport.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.grpc.Channel;
import io.grpc.InternalChannelz;
import io.grpc.InternalInstrumented;
import io.grpc.ManagedChannel;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Observe real SDK channel allocation without exposing a production testing hook. */
@Timeout(20)
class GrpcProviderCleanupTest {
    @Test
    void sdkFactoryHandlesAreObservableUntilClosed() throws Exception {
        String target = target();
        Channel handle = config().getChannelFactory().apply("http://" + target);
        try {
            assertEquals(1, channelsFor(target).size());
        } finally {
            ((OwnedGrpcChannel) handle).close();
        }
        assertTrue(channelsFor(target).isEmpty());
    }

    @Test
    void unsupportedVersionDoesNotLeaveAnAllocatedChannel() throws Exception {
        assertRejectedWithoutChannel("2.0", null, false);
    }

    @Test
    void malformedTenantDoesNotLeaveAnAllocatedChannelForEitherVersion() throws Exception {
        for (String version : List.of("1.0", "1.1")) {
            assertRejectedWithoutChannel(version, "invalid/tenant", false);
        }
    }

    @Test
    void missingCardDoesNotLeaveAnAllocatedChannelForEitherVersion() throws Exception {
        for (String version : List.of("1.0", "1.1")) {
            assertRejectedWithoutChannel(version, null, true);
        }
    }

    @Test
    void failureAfterAllocationClosesFreshChannelAndPreservesCause() throws Exception {
        String target = target();
        AgentInterface descriptor = new AgentInterface("GRPC", "http://" + target, null, "1.1");
        IllegalStateException failure = new IllegalStateException("interceptor configuration unavailable");
        GrpcTransportConfig failingConfig = new GrpcTransportConfig(config().getChannelFactory()) {
            @Override
            public List<ClientCallInterceptor> getInterceptors() {
                throw failure;
            }
        };
        try {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> new GrpcTransportProvider().create(failingConfig, card(descriptor), descriptor)));
            assertTrue(channelsFor(target).isEmpty(), "Construction failure must close the freshly allocated channel");
        } finally {
            for (ManagedChannel channel : channelsFor(target)) {
                channel.shutdownNow();
                assertTrue(channel.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    private static void assertRejectedWithoutChannel(String version, @Nullable String tenant, boolean missingCard) throws Exception {
        String target = target();
        AgentInterface descriptor = new AgentInterface("GRPC", "http://" + target, tenant, version);
        AgentCard card = card(descriptor);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> new GrpcTransportProvider().create(config(), missingCard ? null : card, descriptor));
            assertTrue(channelsFor(target).isEmpty(), "Rejected provider creation must not retain an SDK-managed channel");
        } finally {
            // RED runs must release the real resources leaked by the unfixed provider.
            for (ManagedChannel channel : channelsFor(target)) {
                channel.shutdownNow();
                assertTrue(channel.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    private static List<ManagedChannel> channelsFor(String target) throws Exception {
        List<ManagedChannel> matching = new ArrayList<>();
        long fromId = 0;
        while (true) {
            InternalChannelz.RootChannelList page = InternalChannelz.instance().getRootChannels(fromId, 100);
            for (InternalInstrumented<InternalChannelz.ChannelStats> channel : page.channels) {
                if (channel.getStats().get(5, TimeUnit.SECONDS).target.contains(target)) {
                    matching.add((ManagedChannel) channel);
                }
                fromId = InternalChannelz.id(channel) + 1;
            }
            if (page.end) {
                return matching;
            }
        }
    }

    private static String target() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "127.0.0.1:" + socket.getLocalPort();
        }
    }

    private static GrpcTransportConfig config() {
        return new GrpcTransportConfigBuilder().managedChannel(GrpcManagedChannelOptions.builder()
                .usePlaintext().shutdownTimeout(Duration.ofSeconds(2)).build()).build();
    }

    private static AgentCard card(AgentInterface descriptor) {
        return AgentCard.builder().name("cleanup").description("cleanup").version("1")
                .supportedInterfaces(List.of(descriptor)).capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
    }
}
