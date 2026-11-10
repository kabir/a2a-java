package org.a2aproject.sdk.server.version;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.a2aproject.sdk.server.AgentCardResolver;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.spec.VersionNotSupportedError;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * Explicit exposure at one configured binding, endpoint and tenant. Use the same registration
 * to construct card descriptors and dispatch. Incomplete 1.1 is available only with experimental=true.
 */
public record InterfaceRegistration(TransportProtocol binding, String endpoint, @Nullable String tenant,
        Set<String> versions, boolean experimental) {
    public InterfaceRegistration {
        Assert.checkNotNullParam("binding", binding);
        Assert.checkNotNullParam("endpoint", endpoint);
        Assert.checkNotNullParam("versions", versions);
        if (endpoint.isBlank() || versions.isEmpty()) {
            throw new IllegalArgumentException("An endpoint and an explicit nonempty version set are required");
        }
        versions = Set.copyOf(versions);
        if (!Set.of("0.3", "1.0", "1.1").containsAll(versions)) {
            throw new IllegalArgumentException("Unsupported registered versions: " + versions);
        }
        if (versions.contains("1.1") && !experimental) {
            throw new IllegalArgumentException("Incomplete A2A 1.1 requires an explicit experimental registration");
        }
        tenant = AgentCardResolver.normalizeTenantForResolution(tenant);
        if (tenant != null && versions.contains("0.3")) {
            throw new IllegalArgumentException("Protocol 0.3 does not support tenant-qualified interfaces");
        }
    }

    public List<AgentInterface> supportedInterfaces() {
        return versions.stream().sorted()
                .map(version -> new AgentInterface(binding.asString(), endpoint, tenant, version)).toList();
    }

    public ServerVersionPolicy select(@Nullable String requested) {
        String version = ServerVersionPolicy.resolveVersion(requested);
        if (!versions.contains(version)) {
            throw new VersionNotSupportedError(null,
                    "Protocol version '" + version + "' is not provisioned. Supported versions: " + versions, null);
        }
        return ServerVersionPolicy.forRequest(version);
    }

    /** Fail configuration when enabled adapters are unavailable, without depending on optional modules. */
    public void requireAvailableVersions(Set<String> available) {
        if (!available.containsAll(versions)) {
            throw new IllegalStateException("Registered versions " + versions + " require unavailable adapters; available: " + available);
        }
    }

    public boolean matches(AgentInterface descriptor) {
        return binding.asString().equals(descriptor.protocolBinding()) && endpoint.equals(descriptor.url())
                && Objects.equals(tenant, AgentCardResolver.normalizeTenantForResolution(descriptor.tenant()));
    }
}
