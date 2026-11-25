package org.a2aproject.sdk.server.common.quarkus;

import java.util.Objects;

import jakarta.enterprise.inject.Instance;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.vertx.ext.web.RoutingContext;
import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.server.AgentCardResolver;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.tasks.TaskAuthorityConfiguration;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.server.version.InterfaceRegistrationResolver;
import org.a2aproject.sdk.server.version.ServerVersionPolicy;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.spec.VersionNotSupportedError;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VersionRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionRouter.class);

    public static final String REGISTRATION_KEY = VersionRouter.class.getName() + ".registration";

    public static final String VERSION_1_1 = "1.1";
    public static final String VERSION_1_0 = "1.0";
    public static final String VERSION_0_3 = "0.3";

    private VersionRouter() {
    }

    /**
     * Resolves the protocol version from the request.
     *
     * @return {@link #VERSION_1_0}, {@link #VERSION_0_3}, or the raw version string
     *         if it matches neither (caller should reject as unsupported)
     */
    public static String resolveVersion(RoutingContext rc) {
        String version = rc.request().getHeader(A2AHeaders.A2A_VERSION);
        if (version == null || version.isBlank()) {
            version = rc.request().getParam(A2AHeaders.A2A_VERSION);
        }

        if (version == null || version.isBlank()) {
            return VERSION_0_3;
        }

        String trimmed = version.trim();
        if (trimmed.equals(VERSION_1_0)) {
            return VERSION_1_0;
        }
        if (trimmed.equals(VERSION_0_3)) {
            return VERSION_0_3;
        }
        return trimmed;
    }

    /** Retain a valid 1.1 JSON-RPC envelope identifier even when dispatch rejects the version. */
    public static @Nullable Object requestIdForError(RoutingContext context) {
        if (!VERSION_1_1.equals(resolveVersion(context))) {
            return null;
        }
        try {
            String body = context.body().asString();
            if (body == null) {
                return null;
            }
            JsonElement envelope = JsonParser.parseString(body);
            if (!envelope.isJsonObject()) {
                return null;
            }
            JsonElement id = envelope.getAsJsonObject().get("id");
            if (id == null || !id.isJsonPrimitive()) {
                return null;
            }
            JsonPrimitive primitive = id.getAsJsonPrimitive();
            if (primitive.isString()) {
                return primitive.getAsString();
            }
            return primitive.isNumber() ? primitive.getAsNumber() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isV1(String resolvedVersion) {
        return VERSION_1_0.equals(resolvedVersion) || VERSION_1_1.equals(resolvedVersion);
    }

    /** The resolver belongs to the configured route endpoint, rather than a process-wide version union. */
    public static @Nullable InterfaceRegistration registration(
            @Nullable Instance<InterfaceRegistrationResolver> resolvers, TransportProtocol binding, @Nullable String tenant) {
        if (resolvers == null || resolvers.isUnsatisfied()) {
            return null;
        }
        InterfaceRegistration registration = resolvers.get().resolve(binding, tenant);
        if (registration == null || registration.binding() != binding
                || !Objects.equals(registration.tenant(), AgentCardResolver.normalizeTenantForResolution(tenant))) {
            throw new VersionNotSupportedError(null, "No provisioned interface for this binding and tenant", null);
        }
        return registration;
    }

    public static void attachRegistration(ServerCallContext context,
            @Nullable Instance<InterfaceRegistrationResolver> resolvers, TransportProtocol binding, @Nullable String tenant) {
        attachRegistration(context, resolvers, binding, tenant, context.getRequestedProtocolVersion());
    }

    public static void attachRegistration(ServerCallContext context,
            @Nullable Instance<InterfaceRegistrationResolver> resolvers, TransportProtocol binding,
            @Nullable String tenant, @Nullable String inboundVersion) {
        attachRegistration(context, resolvers, null, binding, tenant, inboundVersion);
    }

    /**
     * Attaches the registration and rejects a request for 1.1 when the node's task authority topology cannot back it.
     * A request that selects 1.1 without any registration is checked too; a legacy card must not bypass the gate.
     */
    public static void attachRegistration(ServerCallContext context,
            @Nullable Instance<InterfaceRegistrationResolver> resolvers,
            @Nullable Instance<TaskAuthorityConfiguration> authority, TransportProtocol binding,
            @Nullable String tenant, @Nullable String inboundVersion) {
        InterfaceRegistration registration = registration(resolvers, binding, tenant);
        if (registration != null) {
            validateContextVersion(context, inboundVersion);
            requireAvailableAdapters(registration);
            requireTaskAuthority(registration, authority);
            context.attachInterfaceRegistration(registration);
        } else {
            requireTaskAuthorityForVersion(inboundVersion, authority);
        }
    }

    /** Explicit registrations preserve the version selected at the ingress boundary. */
    public static void validateContextVersion(ServerCallContext context, @Nullable String inboundVersion) {
        if (!ServerVersionPolicy.resolveVersion(inboundVersion)
                .equals(ServerVersionPolicy.resolveVersion(context.getRequestedProtocolVersion()))) {
            throw new InvalidParamsError("Call context cannot override the requested protocol version");
        }
    }

    /** Validate fixed default exposure at activation; dynamic tenants are also checked per request. */
    public static void validateDefaultRegistration(@Nullable Instance<InterfaceRegistrationResolver> resolvers,
            TransportProtocol binding) {
        validateDefaultRegistration(resolvers, null, binding);
    }

    /** As above, and a default registration that includes 1.1 fails activation unless the task authority backs it. */
    public static void validateDefaultRegistration(@Nullable Instance<InterfaceRegistrationResolver> resolvers,
            @Nullable Instance<TaskAuthorityConfiguration> authority, TransportProtocol binding) {
        if (resolvers == null || resolvers.isUnsatisfied()) {
            return;
        }
        InterfaceRegistration registration = resolvers.get().resolve(binding, null);
        if (registration != null) {
            if (registration.binding() != binding || registration.tenant() != null) {
                throw new IllegalStateException("Default interface registration has the wrong binding or tenant");
            }
            requireAvailableAdapters(registration);
            requireTaskAuthority(registration, authority);
        }
    }

    /**
     * Rejects a registration that includes 1.1 when the task authority topology cannot back it. A null
     * {@code authority} is a manually constructed route without CDI; an unsatisfied one fails closed. Nothing is
     * narrowed: a rejected configuration is reported, never served as 0.3/1.0 instead.
     */
    public static void requireTaskAuthority(@Nullable InterfaceRegistration registration,
            @Nullable Instance<TaskAuthorityConfiguration> authority) {
        if (registration != null && registration.versions().contains(VERSION_1_1)) {
            requireTaskAuthority(authority);
        }
    }

    /**
     * Gates a request that selects 1.1 although no registration applies: a legacy card must not bypass the check.
     * Other versions and a null {@code authority} (manually constructed route) are left alone.
     */
    public static void requireTaskAuthorityForVersion(@Nullable String inboundVersion,
            @Nullable Instance<TaskAuthorityConfiguration> authority) {
        if (VERSION_1_1.equals(ServerVersionPolicy.resolveVersion(inboundVersion))) {
            requireTaskAuthority(authority);
        }
    }

    /**
     * Gates agent-card exposure: a card for a binding and tenant whose registration includes 1.1 is advertised only
     * when the task authority backs it. Unlike request handling, a missing registration is not an error here; the
     * card keeps its historical behaviour and only a registration that includes 1.1 is checked.
     */
    public static void requireTaskAuthorityForCard(@Nullable Instance<InterfaceRegistrationResolver> resolvers,
            @Nullable Instance<TaskAuthorityConfiguration> authority, TransportProtocol binding,
            @Nullable String tenant) {
        if (resolvers == null || resolvers.isUnsatisfied()) {
            return;
        }
        InterfaceRegistration registration = resolvers.get().resolve(binding, tenant);
        if (registration != null && registration.binding() == binding
                && Objects.equals(registration.tenant(), AgentCardResolver.normalizeTenantForResolution(tenant))) {
            requireTaskAuthority(registration, authority);
        }
    }

    private static void requireTaskAuthority(@Nullable Instance<TaskAuthorityConfiguration> authority) {
        if (authority == null) {
            return;
        }
        if (authority.isUnsatisfied()) {
            throw new IllegalStateException("A2A 1.1 requires a TaskAuthorityConfiguration bean to validate the "
                    + "task authority topology");
        }
        try {
            authority.get().requireVersion11();
        } catch (RuntimeException e) {
            // The routes translate this into a generic server error; keep the violations in the server log.
            LOGGER.error("Refusing to serve A2A 1.1: {}", e.getMessage());
            throw e;
        }
    }

    /** Checks optional adapter availability without adding a dependency to shared 1.x artifacts. */
    public static void requireAvailableAdapters(InterfaceRegistration registration) {
        if (!registration.versions().contains(VERSION_0_3)) {
            return;
        }
        String adapter = switch (registration.binding()) {
            case JSONRPC -> "org.a2aproject.sdk.server.multiversion.jsonrpc.MultiVersionJSONRPCRoutes";
            case HTTP_JSON -> "org.a2aproject.sdk.server.multiversion.rest.MultiVersionRestRoutes";
            case GRPC -> "org.a2aproject.sdk.compat03.server.grpc.quarkus.QuarkusGrpcHandler_v0_3";
        };
        try {
            Class.forName(adapter, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException | LinkageError ex) {
            throw new IllegalStateException("Protocol 0.3 was explicitly enabled but its optional "
                    + registration.binding() + " adapter module is unavailable", ex);
        }
    }

    public static boolean isV10(String resolvedVersion) {
        return VERSION_1_0.equals(resolvedVersion);
    }

    public static boolean isV03(String resolvedVersion) {
        return VERSION_0_3.equals(resolvedVersion);
    }
}
