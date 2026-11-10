package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.ClientTransportWrapper;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.spec.A2AClientException;
import org.junit.jupiter.api.Test;

class ClientTransportPolicyTest {
    private static final Set<ClientFeature> ORDINARY = Set.of(ClientFeature.ORDINARY);
    private final ClientTransportConfig<?> config = new JSONRPCTransportConfigBuilder().build();

    private static ClientTransport transport(String version, Set<ClientFeature> features, AtomicInteger closes) {
        return (ClientTransport) Proxy.newProxyInstance(ClientTransport.class.getClassLoader(),
                version.isEmpty() ? new Class<?>[] {ClientTransport.class} : new Class<?>[] {VersionedClientTransport.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "protocolVersion" -> version;
                    case "capabilities" -> features;
                    case "close" -> { closes.incrementAndGet(); yield null; }
                    default -> null;
                });
    }

    private static ClientTransportWrapper wrapper(ClientTransport result) {
        return new ClientTransportWrapper() {
            public ClientTransport wrap(ClientTransport delegate, ClientTransportConfig<?> config) {
                return result;
            }
        };
    }

    @Test
    void identityWrapperPreservesSelectedVersionAndFeatures() throws A2AClientException {
        ClientTransport transport = transport("1.1", ORDINARY, new AtomicInteger());
        VersionedClientTransport result = ClientTransportPolicy.wrap(transport, config, "1.1", ORDINARY,
                List.of(wrapper(transport)));
        assertEquals("1.1", result.protocolVersion());
        assertEquals(ORDINARY, result.capabilities());
    }

    @Test
    void plainWrapperRemovesOneOneSupportAndClosesOwnedChain() {
        AtomicInteger closes = new AtomicInteger();
        ClientTransport base = transport("1.1", ORDINARY, new AtomicInteger());
        ClientTransport plain = transport("", ORDINARY, closes);
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(base, config, "1.1", ORDINARY,
                List.of(wrapper(plain))));
        assertEquals(1, closes.get());
    }

    @Test
    void wrongActualVersionRejectsImplicitConstructorDelegate() {
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(
                transport("1.1", ORDINARY, new AtomicInteger()), config, "1.0", ORDINARY, List.of()));
    }

    @Test
    void missingProviderOrWrapperCapabilityFailsConstruction() {
        ClientTransport base = transport("1.1", ORDINARY, new AtomicInteger());
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(base, config, "1.1", Set.of(), List.of()));
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(base, config, "1.1", ORDINARY,
                List.of(wrapper(transport("1.1", Set.of(), new AtomicInteger())))));
    }

    @Test
    void configuredWrapperExceptionFailsOneOneButRetainsHistoricalOneZeroBehavior() throws A2AClientException {
        ClientTransportWrapper throwing = new ClientTransportWrapper() {
            public ClientTransport wrap(ClientTransport delegate, ClientTransportConfig<?> config) {
                throw new IllegalStateException("configured wrapper failure");
            }
        };
        AtomicInteger closes = new AtomicInteger();
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(
                transport("1.1", ORDINARY, closes), config, "1.1", ORDINARY, List.of(throwing)));
        assertEquals(1, closes.get());
        VersionedClientTransport legacy = ClientTransportPolicy.wrap(
                transport("", ORDINARY, new AtomicInteger()), config, "1.0", ORDINARY, List.of(throwing));
        assertEquals("1.0", legacy.protocolVersion());
        assertEquals(ORDINARY, legacy.capabilities());
    }
    @Test
    void malformedActualDeclarationClosesTransportAndNamesComponent() {
        AtomicInteger closes = new AtomicInteger();
        ClientTransport malformed = (ClientTransport) Proxy.newProxyInstance(ClientTransport.class.getClassLoader(),
                new Class<?>[] {VersionedClientTransport.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "protocolVersion" -> "1.1";
                    case "capabilities" -> null;
                    case "close" -> { closes.incrementAndGet(); yield null; }
                    default -> null;
                });
        A2AClientException failure = assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(
                malformed, config, "1.1", ORDINARY, List.of()));
        assertEquals(1, closes.get());
        assertTrue(failure.getMessage().contains(malformed.getClass().getName()));
    }

    @Test
    void everyRetainedDecoratorReceivesCallsAndCannotReinstateRemovedSupport() throws A2AClientException {
        AtomicInteger baseCloses = new AtomicInteger();
        AtomicInteger decoratedCloses = new AtomicInteger();
        ClientTransport base = transport("1.1", ORDINARY, baseCloses);
        ClientTransportWrapper retaining = new ClientTransportWrapper() {
            public ClientTransport wrap(ClientTransport delegate, ClientTransportConfig<?> config) {
                return (ClientTransport) Proxy.newProxyInstance(ClientTransport.class.getClassLoader(),
                        new Class<?>[] {VersionedClientTransport.class}, (proxy, method, args) -> {
                            if (method.getName().equals("close")) {
                                decoratedCloses.incrementAndGet();
                            }
                            return method.invoke(delegate, args);
                        });
            }
        };
        VersionedClientTransport retained = ClientTransportPolicy.wrap(base, config, "1.1", ORDINARY,
                List.of(retaining, retaining));
        retained.close();
        assertEquals(1, baseCloses.get());
        assertEquals(2, decoratedCloses.get());

        AtomicInteger escalationCalls = new AtomicInteger();
        ClientTransportWrapper escalating = new ClientTransportWrapper() {
            public ClientTransport wrap(ClientTransport delegate, ClientTransportConfig<?> config) {
                escalationCalls.incrementAndGet();
                return transport("1.1", ORDINARY, new AtomicInteger());
            }
        };
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(base, config, "1.1", ORDINARY,
                List.of(wrapper(transport("1.1", Set.of(), new AtomicInteger())), escalating)));
        assertEquals(0, escalationCalls.get());
    }

}
