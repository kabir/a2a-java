package org.a2aproject.sdk.server.multiversion.rest;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.google.gson.JsonObject;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.a2aproject.sdk.compat03.server.rest.quarkus.A2AServerRoutes_v0_3;
import org.a2aproject.sdk.server.common.quarkus.VersionRouter;
import org.a2aproject.sdk.server.common.quarkus.VertxSecurityHelper;
import org.a2aproject.sdk.server.rest.quarkus.A2AServerRoutes;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.server.version.InterfaceRegistrationResolver;
import org.a2aproject.sdk.server.version.ServerVersionPolicy;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.A2AErrorCodes;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.spec.VersionNotSupportedError;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.jspecify.annotations.Nullable;

@Singleton
public class MultiVersionRestRoutes {

    @Inject
    A2AServerRoutes v10Routes;

    @Inject
    RestHandler restHandler;

    @Inject
    Instance<A2AServerRoutes_v0_3> v03Routes;

    @Inject
    Instance<InterfaceRegistrationResolver> interfaceRegistrationResolvers;

    @Inject
    VertxSecurityHelper vertxSecurityHelper;

    void setupRoutes(@Observes @Priority(5) Router router) {
        // POST /v1/message:send
        router.postWithRegex("^\\/(?<tenant>[^\\/]+\\/)?message:send$")
            .order(-1)
            .handler(BodyHandler.create())
            .blockingHandler(versionDispatch(false,
                MultiVersionRestRoutes::bridgeTenant,
                (body, ctx) -> v10Routes.sendMessage(body, ctx),
                (body, ctx) -> v03Routes.get().sendMessage(body, ctx)), false);

        // POST /v1/message:stream (deferred CDI context destruction)
        router.postWithRegex("^\\/(?<tenant>[^\\/]+\\/)?message:stream$")
            .order(-1)
            .handler(BodyHandler.create())
            .blockingHandler(versionDispatch(true,
                MultiVersionRestRoutes::bridgeTenant,
                (body, ctx) -> v10Routes.sendMessageStreaming(body, ctx),
                (body, ctx) -> v03Routes.get().sendMessageStreaming(body, ctx)), false);

        // GET /v1/tasks/{taskId}
        router.getWithRegex("^\\/(?<tenant>[^\\/]+\\/)?tasks\\/(?<taskId>[^:^/]+)$")
            .order(-1)
            .blockingHandler(versionDispatchNoBody(false,
                ctx -> { bridgeTenant(ctx); bridgeTaskId(ctx); },
                ctx -> v10Routes.getTask(ctx),
                ctx -> v03Routes.get().getTask(ctx)), false);

        // POST /v1/tasks/{taskId}:cancel
        router.postWithRegex("^\\/(?<tenant>[^\\/]+\\/)?tasks\\/(?<taskId>[^/]+):cancel$")
            .order(-1)
            .handler(BodyHandler.create())
            .blockingHandler(versionDispatch(false,
                ctx -> { bridgeTenant(ctx); bridgeTaskId(ctx); },
                (body, ctx) -> v10Routes.cancelTask(body, ctx),
                (body, ctx) -> v03Routes.get().cancelTask(ctx)), false);

        // GET is only admitted by the shared handler for selected protocol 1.1.
        router.getWithRegex("^\\/(?<tenant>[^\\/]+\\/)?tasks\\/(?<taskId>[^/]+):subscribe$")
            .order(-1)
            .blockingHandler(versionDispatchNoBody(true,
                ctx -> { bridgeTenant(ctx); bridgeTaskId(ctx); },
                ctx -> v10Routes.subscribeToTask(ctx),
                ctx -> ctx.response().setStatusCode(405).putHeader("Allow", "POST").end()), false);

        // POST /v1/tasks/{taskId}:subscribe (deferred CDI context destruction)
        router.postWithRegex("^\\/(?<tenant>[^\\/]+\\/)?tasks\\/(?<taskId>[^/]+):subscribe$")
            .order(-1)
            .blockingHandler(versionDispatchNoBody(true,
                ctx -> { bridgeTenant(ctx); bridgeTaskId(ctx); },
                ctx -> v10Routes.subscribeToTask(ctx),
                ctx -> v03Routes.get().resubscribeTask(ctx)), false);

        // POST /v1/tasks/{taskId}/pushNotificationConfigs
        router.postWithRegex("^\\/(?<tenant>[^\\/]+\\/)?tasks\\/(?<taskId>[^/]+)\\/pushNotificationConfigs$")
            .order(-1)
            .handler(BodyHandler.create())
            .blockingHandler(versionDispatch(false,
                ctx -> { bridgeTenant(ctx); bridgeTaskId(ctx); },
                (body, ctx) -> v10Routes.createTaskPushNotificationConfiguration(body, ctx),
                (body, ctx) -> v03Routes.get().setTaskPushNotificationConfiguration(body, ctx)), false);

        // GET /v1/tasks/{taskId}/pushNotificationConfigs/{configId}
        router.getWithRegex("^\\/(?<tenant>[^\\/]+\\/)?tasks\\/(?<taskId>[^/]+)\\/pushNotificationConfigs\\/(?<configId>[^\\/]+)")
            .order(-1)
            .blockingHandler(versionDispatchNoBody(false,
                ctx -> { bridgeTenant(ctx); bridgeTaskId(ctx); },
                ctx -> v10Routes.getTaskPushNotificationConfiguration(ctx),
                ctx -> v03Routes.get().getTaskPushNotificationConfiguration(ctx)), false);

        // GET /v1/tasks/{taskId}/pushNotificationConfigs
        router.getWithRegex("^\\/(?<tenant>[^\\/]+\\/)?tasks\\/(?<taskId>[^/]+)\\/pushNotificationConfigs\\/?$")
            .order(-1)
            .blockingHandler(versionDispatchNoBody(false,
                ctx -> { bridgeTenant(ctx); bridgeTaskId(ctx); },
                ctx -> v10Routes.listTaskPushNotificationConfigurations(ctx),
                ctx -> v03Routes.get().listTaskPushNotificationConfigurations(ctx)), false);

        // DELETE /v1/tasks/{taskId}/pushNotificationConfigs/{configId}
        router.deleteWithRegex("^\\/(?<tenant>[^\\/]+\\/)?tasks\\/(?<taskId>[^/]+)\\/pushNotificationConfigs\\/(?<configId>[^/]+)")
            .order(-1)
            .blockingHandler(versionDispatchNoBody(false,
                ctx -> { bridgeTenant(ctx); bridgeTaskId(ctx); },
                ctx -> v10Routes.deleteTaskPushNotificationConfiguration(ctx),
                ctx -> v03Routes.get().deleteTaskPushNotificationConfiguration(ctx)), false);

        // GET /v1/card — v0.3 only (v1.0 uses /{tenant}/extendedAgentCard)
        router.get("/v1/card")
            .order(-1)
            .produces("application/json")
            .blockingHandler(ctx -> {
                try {
                    vertxSecurityHelper.runInRequestContext(ctx, () -> {
                        // The historical /v1 prefix names the 0.3 route, not a tenant.
                        InterfaceRegistration registration = VersionRouter.registration(
                                interfaceRegistrationResolvers, TransportProtocol.HTTP_JSON, null);
                        if (registration != null) {
                            registration.requireAvailableVersions(v03Routes.isResolvable()
                                    ? Set.of("0.3", "1.0", "1.1") : Set.of("1.0", "1.1"));
                            ServerVersionPolicy selected = registration.select(VersionRouter.resolveVersion(ctx));
                            if (selected != ServerVersionPolicy.V0_3) {
                                throw new VersionNotSupportedError(null,
                                        "The historical card endpoint only provides the 0.3 contract", null);
                            }
                            ctx.put(VersionRouter.REGISTRATION_KEY, registration);
                        }
                        v03Routes.get().getAuthenticatedExtendedCard(ctx);
                    });
                } catch (UnauthorizedException | ForbiddenException e) {
                    vertxSecurityHelper.handleAuthError(ctx, e);
                } catch (A2AError e) {
                    sendA2AErrorResponse(ctx, e);
                } catch (Exception e) {
                    VertxSecurityHelper.handleGenericError(ctx);
                }
            }, false);
    }

    private static void bridgeTenant(RoutingContext ctx) {
        String tenant = ctx.pathParam("tenant");
        ctx.pathParams().put("tenant", tenant == null ? "" : tenant.replace("/", ""));
    }

    private static void bridgeTaskId(RoutingContext ctx) {
        String taskId = ctx.pathParam("taskId");
        if (taskId != null) {
            ctx.pathParams().put("id", taskId);
            ctx.pathParams().put("param0", taskId);
        }
    }

    /**
     * @param deferContextDestruction if true, defers CDI request context destruction until the SSE response completes
     */
    private io.vertx.core.Handler<RoutingContext> versionDispatch(
            boolean deferContextDestruction,
            Consumer<RoutingContext> paramBridger,
            BiConsumer<String, RoutingContext> v10Handler,
            BiConsumer<String, RoutingContext> v03Handler) {
        return ctx -> {
            try {
                Runnable task = () -> {
                    paramBridger.accept(ctx);
                    String tenant = ctx.pathParam("tenant");
                    if (interfaceRegistrationResolvers.isUnsatisfied() && !"v1".equals(tenant)) {
                        ctx.next();
                        return;
                    }
                    String version = VersionRouter.resolveVersion(ctx);
                    InterfaceRegistration registration = selectRegistration(ctx, version);
                    String body = ctx.body().asString();
                    if (body == null) {
                        body = "";
                    }
                    if (VersionRouter.isV10(version) || (registration != null && VersionRouter.VERSION_1_1.equals(version))) {
                        v10Handler.accept(body, ctx);
                    } else if (VersionRouter.isV03(version)) {
                        v03Handler.accept(body, ctx);
                    } else {
                        throw new VersionNotSupportedError(
                                null,
                                "Protocol version '" + version + "' is not supported. Supported versions: [1.0, 0.3]",
                                null);
                    }
                };
                runInContext(ctx, task, deferContextDestruction);
            } catch (UnauthorizedException | ForbiddenException e) {
                vertxSecurityHelper.handleAuthError(ctx, e);
            } catch (A2AError e) {
                sendA2AErrorResponse(ctx, e);
            } catch (Exception e) {
                VertxSecurityHelper.handleGenericError(ctx);
            }
        };
    }

    /**
     * @param deferContextDestruction if true, defers CDI request context destruction until the SSE response completes
     */
    private io.vertx.core.Handler<RoutingContext> versionDispatchNoBody(
            boolean deferContextDestruction,
            Consumer<RoutingContext> paramBridger,
            Consumer<RoutingContext> v10Handler,
            Consumer<RoutingContext> v03Handler) {
        return ctx -> {
            try {
                Runnable task = () -> {
                    paramBridger.accept(ctx);
                    String tenant = ctx.pathParam("tenant");
                    if (interfaceRegistrationResolvers.isUnsatisfied() && !"v1".equals(tenant)) {
                        ctx.next();
                        return;
                    }
                    String version = VersionRouter.resolveVersion(ctx);
                    InterfaceRegistration registration = selectRegistration(ctx, version);
                    if (VersionRouter.isV10(version) || (registration != null && VersionRouter.VERSION_1_1.equals(version))) {
                        v10Handler.accept(ctx);
                    } else if (VersionRouter.isV03(version)) {
                        v03Handler.accept(ctx);
                    } else {
                        throw new VersionNotSupportedError(
                                null,
                                "Protocol version '" + version + "' is not supported. Supported versions: [1.0, 0.3]",
                                null);
                    }
                };
                runInContext(ctx, task, deferContextDestruction);
            } catch (UnauthorizedException | ForbiddenException e) {
                vertxSecurityHelper.handleAuthError(ctx, e);
            } catch (A2AError e) {
                sendA2AErrorResponse(ctx, e);
            } catch (Exception e) {
                VertxSecurityHelper.handleGenericError(ctx);
            }
        };
    }

    private @Nullable InterfaceRegistration selectRegistration(RoutingContext ctx, String version) {
        String tenant = ctx.pathParam("tenant");
        if (!interfaceRegistrationResolvers.isUnsatisfied() && VersionRouter.isV03(version)) {
            if (!"v1".equals(tenant)) {
                throw new VersionNotSupportedError(null,
                        "Protocol 0.3 only provides the historical default endpoint paths", null);
            }
            // Native 0.3 /v1 paths have no tenant; explicit 1.x still treats v1 as its tenant.
            tenant = null;
            ctx.pathParams().put("tenant", "");
        }
        InterfaceRegistration registration = VersionRouter.registration(
                interfaceRegistrationResolvers, TransportProtocol.HTTP_JSON, tenant);
        if (registration != null) {
            registration.requireAvailableVersions(v03Routes.isResolvable()
                    ? Set.of("0.3", "1.0", "1.1") : Set.of("1.0", "1.1"));
            registration.select(version);
            ctx.put(VersionRouter.REGISTRATION_KEY, registration);
        }
        return registration;
    }

    private void runInContext(RoutingContext ctx, Runnable task, boolean deferContextDestruction) {
        if (deferContextDestruction) {
            vertxSecurityHelper.runInRequestContextDeferred(ctx, task);
        } else {
            vertxSecurityHelper.runInRequestContext(ctx, task);
        }
    }

    private void sendA2AErrorResponse(RoutingContext ctx, A2AError error) {
        if (VersionRouter.VERSION_1_1.equals(VersionRouter.resolveVersion(ctx))) {
            RestHandler.HTTPRestResponse response = restHandler.createErrorResponse(error, ServerVersionPolicy.V1_1);
            ctx.response().setStatusCode(response.getStatusCode())
                    .putHeader(CONTENT_TYPE, response.getContentType()).end(response.getBody());
            return;
        }
        A2AErrorCodes errorCode = A2AErrorCodes.fromCode(error.getCode());
        int httpStatus = errorCode != null
                ? ServerVersionPolicy.forRequest(VersionRouter.resolveVersion(ctx)).httpStatus(error.getCode()) : 400;

        JsonObject errorObj = new JsonObject();
        errorObj.addProperty("code", error.getCode());
        errorObj.addProperty("message", error.getMessage());

        JsonObject response = new JsonObject();
        response.add("error", errorObj);

        ctx.response()
            .setStatusCode(httpStatus)
            .putHeader(CONTENT_TYPE, "application/json")
            .end(response.toString());
    }
}
