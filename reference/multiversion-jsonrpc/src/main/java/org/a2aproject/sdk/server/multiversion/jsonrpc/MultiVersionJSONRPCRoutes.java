package org.a2aproject.sdk.server.multiversion.jsonrpc;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import java.util.Set;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.a2aproject.sdk.compat03.server.apps.quarkus.A2AServerRoutes_v0_3;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.server.apps.quarkus.A2AServerRoutes;
import org.a2aproject.sdk.server.common.quarkus.VersionRouter;
import org.a2aproject.sdk.server.common.quarkus.VertxSecurityHelper;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.server.version.InterfaceRegistrationResolver;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.spec.VersionNotSupportedError;

@Singleton
public class MultiVersionJSONRPCRoutes {

    @Inject
    A2AServerRoutes v10Routes;

    @Inject
    Instance<A2AServerRoutes_v0_3> v03Routes;

    @Inject
    Instance<InterfaceRegistrationResolver> interfaceRegistrationResolvers;

    @Inject
    VertxSecurityHelper vertxSecurityHelper;

    void setupRoutes(@Observes Router router) {
        router.postWithRegex("^\\/(?<tenant>[^\\/]*)\\/?$")
            .order(-1)
            .consumes("application/json")
            .handler(BodyHandler.create())
            .blockingHandler(ctx -> {
                try {
                    // JSON-RPC multiplexes streaming and non-streaming methods over a single
                    // endpoint, so we always use deferred CDI context destruction — matching
                    // the single-version A2AServerRoutes behavior.
                    vertxSecurityHelper.runInRequestContextDeferred(ctx, () -> {
                        String tenant = ctx.pathParam("tenant");
                        if (interfaceRegistrationResolvers.isUnsatisfied() && tenant != null && !tenant.isEmpty()) {
                            ctx.next();
                            return;
                        }
                        String version = VersionRouter.resolveVersion(ctx);
                        InterfaceRegistration registration = VersionRouter.registration(
                                interfaceRegistrationResolvers, TransportProtocol.JSONRPC, tenant);
                        if (registration != null) {
                            registration.requireAvailableVersions(v03Routes.isResolvable()
                                    ? Set.of("0.3", "1.0", "1.1") : Set.of("1.0", "1.1"));
                            registration.select(version);
                            ctx.put(VersionRouter.REGISTRATION_KEY, registration);
                        }
                        String body = ctx.body().asString();

                        if (VersionRouter.isV10(version) || (registration != null && VersionRouter.VERSION_1_1.equals(version))) {
                            v10Routes.invokeJSONRPCHandler(body, ctx);
                        } else if (VersionRouter.isV03(version)) {
                            v03Routes.get().invokeJSONRPCHandler(body, ctx);
                        } else {
                            throw new VersionNotSupportedError(
                                null,
                                "Protocol version '" + version + "' is not supported. Supported versions: [1.0, 0.3]",
                                null);
                        }
                    });
                } catch (UnauthorizedException | ForbiddenException e) {
                    vertxSecurityHelper.handleAuthError(ctx, e);
                } catch (A2AError e) {
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader(CONTENT_TYPE, "application/json")
                        .end(JSONRPCUtils.toJsonRPCErrorResponse(VersionRouter.requestIdForError(ctx), e));
                } catch (Exception e) {
                    VertxSecurityHelper.handleGenericError(ctx);
                }
            }, false);
    }
}
