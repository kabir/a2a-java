package org.a2aproject.sdk.server.version;

import org.a2aproject.sdk.spec.TransportProtocol;
import org.jspecify.annotations.Nullable;

/**
 * Registration lookup scoped by the application to the configured route/server endpoint.
 * A null result disables that binding/tenant; absence of this optional resolver preserves legacy entry points.
 * Applications serving distinct endpoints must use a resolver scoped to each endpoint, never a global version union.
 */
@FunctionalInterface
public interface InterfaceRegistrationResolver {
    @Nullable InterfaceRegistration resolve(TransportProtocol binding, @Nullable String tenant);
}
