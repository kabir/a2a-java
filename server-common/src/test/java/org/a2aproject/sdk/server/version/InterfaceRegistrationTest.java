package org.a2aproject.sdk.server.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.spec.VersionNotSupportedError;
import org.junit.jupiter.api.Test;

class InterfaceRegistrationTest {
    @Test
    void requiresExplicitNonemptyKnownVersionsAndExperimental11() {
        assertThrows(IllegalArgumentException.class, () -> registration(Set.of(), true));
        assertThrows(IllegalArgumentException.class, () -> registration(Set.of("1.2"), true));
        assertThrows(IllegalArgumentException.class, () -> registration(Set.of("1.1"), false));
    }

    @Test
    void protocol03RegistrationCannotAdvertiseTenantSemanticsForAnyBinding() {
        for (TransportProtocol binding : TransportProtocol.values()) {
            for (Set<String> enabled : List.of(Set.of("0.3"), Set.of("0.3", "1.0"),
                    Set.of("0.3", "1.1"), Set.of("0.3", "1.0", "1.1"))) {
                for (String tenant : List.of("tenant-a", "/tenant-a/")) {
                    assertThrows(IllegalArgumentException.class, () -> new InterfaceRegistration(
                            binding, "https://example.test", tenant, enabled, true));
                }
            }
        }
    }

    @Test
    void protocol03RegistrationAllowsNormalizedDefaultEndpointOnly() {
        for (TransportProtocol binding : TransportProtocol.values()) {
            for (String tenant : new String[] {null, "", "   ", "/"}) {
                InterfaceRegistration registration = new InterfaceRegistration(binding,
                        "https://example.test", tenant, Set.of("0.3", "1.0", "1.1"), true);
                assertEquals(null, registration.tenant());
                assertEquals("0.3", registration.select(null).protocolVersion());
                for (AgentInterface descriptor : registration.supportedInterfaces()) {
                    assertEquals(null, descriptor.tenant());
                }
            }
        }
    }

    @Test
    void everyNonemptySubsetDispatchesOnlyEnabledExactVersionsAndBuildsMatchingDescriptors() {
        List<String> versions = List.of("0.3", "1.0", "1.1");
        for (int mask = 1; mask < 8; mask++) {
            Set<String> enabled = new HashSet<>();
            for (int bit = 0; bit < 3; bit++) {
                if ((mask & (1 << bit)) != 0) {
                    enabled.add(versions.get(bit));
                }
            }
            InterfaceRegistration registration = registration(enabled, true);
            assertEquals(enabled, new HashSet<>(registration.supportedInterfaces().stream()
                    .map(AgentInterface::protocolVersion).toList()));
            for (String version : versions) {
                if (enabled.contains(version)) {
                    assertEquals(version, registration.select(version).protocolVersion());
                } else {
                    assertThrows(VersionNotSupportedError.class, () -> registration.select(version));
                }
            }
            assertThrows(VersionNotSupportedError.class, () -> registration.select("1.0.1"));
            assertThrows(VersionNotSupportedError.class, () -> registration.select("2.0"));
        }
    }

    @Test
    void missingEmptyAndBlankResolveTo03AndRejectWhenDisabled() {
        InterfaceRegistration legacy = registration(Set.of("0.3"), true);
        InterfaceRegistration modern = registration(Set.of("1.1"), true);
        for (String request : new String[] {null, "", "   "}) {
            assertEquals("0.3", legacy.select(request).protocolVersion());
            assertThrows(VersionNotSupportedError.class, () -> modern.select(request));
        }
    }

    @Test
    void optional03MustBeAvailableExplicitly() {
        InterfaceRegistration registration = registration(Set.of("0.3", "1.1"), true);
        assertThrows(IllegalStateException.class,
                () -> registration.requireAvailableVersions(Set.of("1.0", "1.1")));
        registration.requireAvailableVersions(Set.of("0.3", "1.0", "1.1"));
    }

    @Test
    void registrationCopiesCallerSet() {
        Set<String> versions = new HashSet<>(Set.of("1.0"));
        InterfaceRegistration registration = registration(versions, false);
        versions.add("1.1");
        assertThrows(VersionNotSupportedError.class, () -> registration.select("1.1"));
    }

    @Test
    void validationCannotBorrowVersionFromAnotherBindingEndpointOrTenant() {
        InterfaceRegistration registration = registration(Set.of("1.1"), true);
        ServerCallContext context = new ServerCallContext(UnauthenticatedUser.INSTANCE,
                Map.of(ServerCallContext.TRANSPORT_KEY, TransportProtocol.JSONRPC), Set.of(), "1.1");
        context.attachInterfaceRegistration(registration);
        for (AgentInterface wrong : List.of(
                new AgentInterface("GRPC", "https://example.test/tenant-a", "tenant-a", "1.1"),
                new AgentInterface("JSONRPC", "https://example.test/other", "tenant-a", "1.1"),
                new AgentInterface("JSONRPC", "https://example.test/tenant-a", "tenant-b", "1.1"))) {
            AgentCard card = card(List.of(wrong));
            assertThrows(VersionNotSupportedError.class,
                    () -> A2AVersionValidator.validateProtocolVersion(card, context));
        }
        A2AVersionValidator.validateProtocolVersion(card(registration.supportedInterfaces()), context);
        assertEquals("1.1", context.getVersionPolicy().protocolVersion());
        assertThrows(IllegalStateException.class,
                () -> context.attachInterfaceRegistration(registration(Set.of("1.0"), false)));
    }

    private static InterfaceRegistration registration(Set<String> versions, boolean experimental) {
        boolean defaultEndpoint = versions.contains("0.3");
        return new InterfaceRegistration(TransportProtocol.JSONRPC,
                defaultEndpoint ? "https://example.test" : "https://example.test/tenant-a",
                defaultEndpoint ? null : "tenant-a", versions, experimental);
    }

    private static AgentCard card(List<AgentInterface> interfaces) {
        return AgentCard.builder().name("test").description("test").version("1")
                .supportedInterfaces(interfaces).capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text/plain")).defaultOutputModes(List.of("text/plain"))
                .skills(List.of()).build();
    }
}
