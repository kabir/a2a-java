package org.a2aproject.sdk.server.apps.common;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.properties.IfBuildProperty;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.server.version.InterfaceRegistrationResolver;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;

/** Endpoint-scoped registrations used only by the experimental paired-client profile. */
@ApplicationScoped
@IfBuildProperty(name = "test.a2a.experimental", stringValue = "true")
public class PairedVersionRegistrationResolver implements InterfaceRegistrationResolver {
    public static final String SUBSET_TENANT_PREFIX = "phase1-";

    @Inject
    @ConfigProperty(name = "test.a2a.versions", defaultValue = "0.3,1.0,1.1")
    String configuredVersions;

    @Inject
    @ConfigProperty(name = "test.a2a.v1-versions", defaultValue = "inherit")
    String v1Versions;

    @Override
    public @Nullable InterfaceRegistration resolve(TransportProtocol binding, @Nullable String tenant) {
        String selectedVersions = "v1".equals(tenant) && !v1Versions.equals("inherit") ? v1Versions : configuredVersions;
        Set<String> versions = new LinkedHashSet<>(Arrays.asList(selectedVersions.split(",")));
        if (tenant != null && tenant.startsWith(SUBSET_TENANT_PREFIX)) {
            int mask;
            try {
                mask = Integer.parseInt(tenant.substring(SUBSET_TENANT_PREFIX.length()));
            } catch (NumberFormatException ignored) {
                return null;
            }
            if (mask < 1 || mask > 7) {
                return null;
            }
            versions.clear();
            if ((mask & 1) != 0) {
                versions.add("0.3");
            }
            if ((mask & 2) != 0) {
                versions.add("1.0");
            }
            if ((mask & 4) != 0) {
                versions.add("1.1");
            }
        }
        // Native 0.3 has no tenant selector or tenant-qualified descriptor.
        if (tenant != null && !tenant.isBlank()) {
            versions.remove("0.3");
            if (versions.isEmpty()) {
                return null;
            }
        }
        String port = System.getProperty("test.agent.card.port", "8081");
        String endpoint = binding == TransportProtocol.GRPC ? "localhost:" + port : "http://localhost:" + port;
        return new InterfaceRegistration(binding, endpoint, tenant, versions, true);
    }
}
