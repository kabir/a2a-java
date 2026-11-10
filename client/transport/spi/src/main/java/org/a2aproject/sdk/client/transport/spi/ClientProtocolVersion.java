package org.a2aproject.sdk.client.transport.spi;

import java.util.LinkedHashMap;
import java.util.Map;

import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;

/** Pure client selection and request-header policy, shared by built-in bindings. */
public final class ClientProtocolVersion {
    public static final String DEFAULT = "1.1";
    private static final String VERSION_HEADER = "A2A-Version";

    private ClientProtocolVersion() {
    }

    public static String validate(String version) {
        if (!"1.0".equals(version) && !"1.1".equals(version)) {
            throw new IllegalArgumentException("Shared clients require exact protocol version 1.0 or 1.1: " + version);
        }
        return version;
    }

    /** Select an exact descriptor before applying any binding preference. */
    public static AgentInterface selectInterface(AgentCard card, String binding, String version)
            throws A2AClientException {
        validate(version);
        return card.supportedInterfaces().stream()
                .filter(iface -> binding.equals(iface.protocolBinding()) && version.equals(iface.protocolVersion()))
                .findFirst().orElseThrow(() -> new A2AClientException(
                        "No compatible transport found for " + binding + " at protocol version " + version));
    }

    /** Reject every conflicting case-insensitive override and emit one pinned value. */
    public static Map<String, String> normalizeHeaders(Map<String, String> headers, String version)
            throws A2AClientException {
        validate(version);
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (VERSION_HEADER.equalsIgnoreCase(header.getKey())) {
                if (!version.equals(header.getValue())) {
                    throw new A2AClientException("Conflicting A2A-Version override for selected version " + version);
                }
            } else {
                normalized.put(header.getKey(), header.getValue());
            }
        }
        normalized.put(VERSION_HEADER, version);
        return normalized;
    }
}
