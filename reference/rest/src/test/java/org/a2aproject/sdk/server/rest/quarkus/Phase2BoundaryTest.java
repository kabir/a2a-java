package org.a2aproject.sdk.server.rest.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.inject.Instance;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Phase2BoundaryTest {
    private A2AServerRoutes routes;
    private RestHandler handler;
    private RoutingContext routingContext;
    private HttpServerRequest request;
    private HttpServerResponse response;
    private MultiMap params;

    @BeforeEach
    void setUp() throws Exception {
        routes = new A2AServerRoutes();
        handler = mock(RestHandler.class);
        routingContext = mock(RoutingContext.class);
        request = mock(HttpServerRequest.class);
        response = mock(HttpServerResponse.class);
        params = MultiMap.caseInsensitiveMultiMap();
        when(routingContext.request()).thenReturn(request);
        when(routingContext.response()).thenReturn(response);
        when(routingContext.user()).thenReturn(null);
        when(routingContext.body()).thenReturn(mock(RequestBody.class));
        when(routingContext.pathParam("taskId")).thenReturn("task-1");
        when(request.params()).thenReturn(params);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(response.setStatusCode(any(Integer.class))).thenReturn(response);
        when(response.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(response);
        when(response.end()).thenReturn(Future.succeededFuture());
        when(response.end(any(String.class))).thenReturn(Future.succeededFuture());
        @SuppressWarnings("unchecked") Instance<CallContextFactory> contextFactory = mock(Instance.class);
        @SuppressWarnings("unchecked") Instance<?> registrations = mock(Instance.class);
        when(contextFactory.isUnsatisfied()).thenReturn(false);
        when(contextFactory.get()).thenReturn(rc -> new ServerCallContext(UnauthenticatedUser.INSTANCE,
                new HashMap<>(), Set.of(), "1.1"));
        when(registrations.isUnsatisfied()).thenReturn(true);
        setField("jsonRestHandler", handler);
        setField("callContextFactory", contextFactory);
        setField("interfaceRegistrationResolvers", registrations);
    }

    @Test
    void getAndListQueryOptionsReachRestHandlerOverloads() {
        RestHandler.HTTPRestResponse result = mock(RestHandler.HTTPRestResponse.class);
        when(result.getStatusCode()).thenReturn(200);
        when(result.getContentType()).thenReturn("application/json");
        when(result.getBody()).thenReturn("{}");
        when(handler.getTask(any(ServerCallContext.class), anyString(), eq("task-1"), eq("3"), eq("7"), eq("11")))
                .thenReturn(result);
        params.add("historyLength", "3").add("timelineLength", "7").add("currentGeneration", "11");

        routes.getTask(routingContext);

        verify(handler).getTask(any(ServerCallContext.class), anyString(), eq("task-1"), eq("3"), eq("7"), eq("11"));

        params.clear().add("timelineLength", "9");
        when(handler.listTasks(any(ServerCallContext.class), anyString(), any(), any(), any(), any(), any(), any(), any(), eq("9")))
                .thenReturn(result);
        routes.listTasks(routingContext);
        verify(handler).listTasks(any(ServerCallContext.class), anyString(), any(), any(), any(), any(), any(), any(), any(), eq("9"));
    }

    @Test
    void subscribeCursorReachesRestHandlerOverload() {
        RestHandler.HTTPRestResponse result = mock(RestHandler.HTTPRestResponse.class);
        when(result.getStatusCode()).thenReturn(200);
        when(result.getContentType()).thenReturn("application/json");
        when(result.getBody()).thenReturn("{}");
        when(handler.subscribeToTask(any(ServerCallContext.class), anyString(), eq("task-1"), eq("13")))
                .thenReturn(result);
        params.add("lastSeenGeneration", "13");

        routes.subscribeToTask(routingContext);

        verify(handler).subscribeToTask(any(ServerCallContext.class), anyString(), eq("task-1"), eq("13"));
    }

    private void setField(String name, Object value) throws Exception {
        Field field = A2AServerRoutes.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(routes, value);
    }
}
