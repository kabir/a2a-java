package org.a2aproject.sdk.client.transport.spi;

import java.util.Set;

/** Additive companion for transports with an immutable, exact protocol selection. */
public interface VersionedClientTransport extends ClientTransport {
    String protocolVersion();

    /** Implemented capabilities for this instance's selected version. */
    Set<ClientFeature> capabilities();
}
