package org.a2aproject.sdk.server.apps.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.properties.IfBuildProperty;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.multitenancy.AgentCardRouter;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.server.version.InterfaceRegistrationResolver;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.jspecify.annotations.Nullable;

/** Derives each tenant card from the same registration used by dispatch. */
@ApplicationScoped
@IfBuildProperty(name = "test.a2a.experimental", stringValue = "true")
public class PairedVersionAgentCardRouter implements AgentCardRouter {
    @Inject
    @PublicAgentCard
    AgentCard defaultCard;

    @Inject
    InterfaceRegistrationResolver registrations;

    @Override
    public @Nullable AgentCard resolvePublicCard(@Nullable String tenant) {
        TransportProtocol binding = TransportProtocol.fromString(defaultCard.supportedInterfaces().get(0).protocolBinding());
        InterfaceRegistration registration = registrations.resolve(binding, tenant);
        return registration == null ? null
                : AgentCard.builder(defaultCard).supportedInterfaces(registration.supportedInterfaces()).build();
    }

    @Override
    public @Nullable AgentCard resolveExtendedCard(@Nullable String tenant) {
        return resolvePublicCard(tenant);
    }
}
