package org.a2aproject.sdk.server.rest.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.a2aproject.sdk.server.FixedInstance;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.common.quarkus.VertxSecurityHelper;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.server.version.InterfaceRegistrationResolver;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.a2aproject.sdk.transport.rest.handler.RestHandler.HTTPRestResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestSubscriptionRouteTest {
    private final AtomicInteger coreCalls = new AtomicInteger();
    private Vertx vertx;
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        VertxSecurityHelper security = mock(VertxSecurityHelper.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(security).runInRequestContextDeferred(any(), any());
        RestHandler handler = mock(RestHandler.class);
        when(handler.subscribeToTask(any(), anyString(), anyString())).thenAnswer(invocation -> {
            coreCalls.incrementAndGet();
            ServerCallContext context = invocation.getArgument(0);
            return new HTTPRestResponse(200, "text/event-stream", "data: admitted-" + context.getRequestedProtocolVersion() + "\n\n");
        });
        InterfaceRegistration registration = new InterfaceRegistration(TransportProtocol.HTTP_JSON,
                "http://localhost", null, Set.of("1.0", "1.1"), true);
        InterfaceRegistrationResolver resolver = (binding, tenant) -> registration;
        A2AServerRoutes routes = new A2AServerRoutes();
        setField(routes, "jsonRestHandler", handler);
        setField(routes, "callContextFactory", FixedInstance.empty());
        setField(routes, "interfaceRegistrationResolvers", new FixedInstance<>(resolver));
        setField(routes, "vertxSecurityHelper", security);
        routes.setupRouter(router);
        server = vertx.createHttpServer().requestHandler(router).listen(0).toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void stopServer() throws Exception {
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void rawOneOneGetSubscriptionReachesSelectedHandler() throws Exception {
        HttpResponse<String> response = request("GET", "1.1", "/tasks/task:subscribe");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("text/event-stream", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("data: admitted-1.1\n\n", response.body());
        assertEquals(1, coreCalls.get());
    }

    @Test
    void rawOneZeroPostSubscriptionRetainsLegacyVerb() throws Exception {
        HttpResponse<String> response = request("POST", "1.0", "/tasks/task:subscribe");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("data: admitted-1.0\n\n", response.body());
        assertEquals(1, coreCalls.get());
    }

    @Test
    void oneOnePostSubscriptionCannotExecuteHandler() throws Exception {
        HttpResponse<String> response = request("POST", "1.1", "/tasks/task:subscribe");
        assertEquals(405, response.statusCode(), response.body());
        assertEquals("GET", response.headers().firstValue("Allow").orElseThrow());
        assertEquals(0, coreCalls.get());
    }

    @Test
    void oneZeroGetSubscriptionCannotExecuteHandler() throws Exception {
        HttpResponse<String> response = request("GET", "1.0", "/tasks/task:subscribe");
        assertEquals(405, response.statusCode(), response.body());
        assertEquals("POST", response.headers().firstValue("Allow").orElseThrow());
        assertEquals(0, coreCalls.get());
    }

    private HttpResponse<String> request(String method, String version, String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + server.actualPort() + path)).timeout(Duration.ofSeconds(5))
                .header("A2A-Version", version).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
