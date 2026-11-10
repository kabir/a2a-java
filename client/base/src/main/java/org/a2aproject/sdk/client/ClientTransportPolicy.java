package org.a2aproject.sdk.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.ClientTransportWrapper;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.spec.A2AClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Construction-time validation of the complete provider/decorator chain. */
final class ClientTransportPolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientTransportPolicy.class);

    private ClientTransportPolicy() {
    }

    static VersionedClientTransport wrap(ClientTransport transport, ClientTransportConfig<?> config, String version,
            Set<ClientFeature> providerFeatures, List<ClientTransportWrapper> wrappers) throws A2AClientException {
        ClientTransport wrapped = transport;
        try {
            Set<ClientFeature> features = intersect(transport, version, providerFeatures, transport.getClass().getName());
            for (ClientTransportWrapper wrapper : wrappers) {
                ClientTransport next;
                try {
                    next = wrapper.wrap(wrapped, config);
                } catch (Exception failure) {
                    if ("1.1".equals(version)) {
                        throw new A2AClientException("Failed configured wrapper " + wrapper.getClass().getName(), failure);
                    }
                    LOGGER.warn("Failed to apply transport wrapper {}: {}", wrapper.getClass().getName(), failure.getMessage(), failure);
                    continue;
                }
                if (next == null) {
                    throw new A2AClientException("Wrapper returned null: " + wrapper.getClass().getName());
                }
                wrapped = next;
                features = intersect(next, version, features, wrapper.getClass().getName());
            }
            return new SelectedClientTransport(wrapped, version, features);
        } catch (A2AClientException failure) {
            try {
                wrapped.close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static Set<ClientFeature> intersect(ClientTransport transport, String version, Set<ClientFeature> allowed,
            String component) throws A2AClientException {
        try {
            Set<ClientFeature> actual;
            if (transport instanceof VersionedClientTransport selected) {
                String actualVersion = selected.protocolVersion();
                if (!version.equals(actualVersion)) {
                    throw new A2AClientException(component + " selected " + actualVersion + ", expected " + version);
                }
                actual = Set.copyOf(selected.capabilities());
            } else {
                if (!"1.0".equals(version)) {
                    throw new A2AClientException(component + " removes selected protocol version " + version);
                }
                actual = Set.of(ClientFeature.ORDINARY);
            }
            Set<ClientFeature> retained = new HashSet<>(allowed);
            retained.retainAll(actual);
            if (!retained.contains(ClientFeature.ORDINARY)) {
                throw new A2AClientException(component + " removes required ORDINARY capability for " + version);
            }
            return Set.copyOf(retained);
        } catch (A2AClientException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new A2AClientException("Invalid capability declaration from " + component, failure);
        }
    }
}
