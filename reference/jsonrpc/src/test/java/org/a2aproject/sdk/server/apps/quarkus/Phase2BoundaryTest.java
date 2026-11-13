package org.a2aproject.sdk.server.apps.quarkus;

import static org.a2aproject.sdk.spec.A2AMethods.SEND_MESSAGE_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SEND_STREAMING_MESSAGE_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SUBSCRIBE_TO_TASK_METHOD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.inject.Instance;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendInputRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendInputStreamingRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.server.AgentCardCacheMetadata;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Phase2BoundaryTest {
    private A2AServerRoutes routes;
    private JSONRPCHandler handler;
    private Instance<CallContextFactory> contextFactories;
    private Instance<?> registrations;
    private RoutingContext routingContext;
    private HttpServerRequest request;
    private HttpServerResponse response;

    @BeforeEach
    void setUp() throws Exception {
        routes = new A2AServerRoutes();
        handler = mock(JSONRPCHandler.class);
        contextFactories = mock(Instance.class);
        registrations = mock(Instance.class);
        routingContext = mock(RoutingContext.class);
        request = mock(HttpServerRequest.class);
        response = mock(HttpServerResponse.class);
        RequestBody body = mock(RequestBody.class);
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        when(routingContext.request()).thenReturn(request);
        when(routingContext.response()).thenReturn(response);
        when(routingContext.body()).thenReturn(body);
        when(routingContext.user()).thenReturn(null);
        when(routingContext.normalizedPath()).thenReturn("/");
        when(request.headers()).thenReturn(headers);
        when(response.setStatusCode(any(Integer.class))).thenReturn(response);
        when(response.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(response);
        when(response.end(any(String.class))).thenReturn(null);
        when(contextFactories.isUnsatisfied()).thenReturn(false);
        when(contextFactories.get()).thenReturn(rc -> new ServerCallContext(UnauthenticatedUser.INSTANCE,
                new HashMap<>(), Set.of(), "1.1"));
        when(registrations.isUnsatisfied()).thenReturn(true);
        setField("jsonRpcHandler", handler);
        setField("callContextFactory", contextFactories);
        setField("interfaceRegistrationResolvers", registrations);
        setField("cacheMetadata", mock(AgentCardCacheMetadata.class));
    }

    @Test
    void selected11SendRoutesThroughCompanionWrapper() {
        String body = """
                {"jsonrpc":"2.0","id":"r1","method":"%s","params":{"message":{"messageId":"m1","contextId":"c1","role":"ROLE_USER","parts":[{"text":"hello"}]},"configuration":{"ifGenerationMatch":"4"}}}
                """.formatted(SEND_MESSAGE_METHOD);
        Message result = Message.builder().role(Message.Role.ROLE_AGENT).parts(new TextPart("ok")).build();
        when(handler.onSendInput(any(SendInputRequest.class), any(ServerCallContext.class)))
                .thenReturn(new SendMessageResponse("r1", result));

        routes.invokeJSONRPCHandler(body, routingContext);

        var captor = org.mockito.ArgumentCaptor.forClass(SendInputRequest.class);
        verify(handler).onSendInput(captor.capture(), any(ServerCallContext.class));
        assertEquals(4L, captor.getValue().getParams().configuration().ifGenerationMatch());
        verify(handler, never()).onMessageSend(any(SendMessageRequest.class), any(ServerCallContext.class));
    }

    @Test
    void explicit10GuardIsRejectedBeforeLegacyDispatch() throws Exception {
        when(contextFactories.get()).thenReturn(rc -> new ServerCallContext(UnauthenticatedUser.INSTANCE,
                new HashMap<>(), Set.of(), "1.0"));
        String body = """
                {"jsonrpc":"2.0","id":"r1","method":"%s","params":{"message":{"messageId":"m1","contextId":"c1","role":"ROLE_USER","parts":[{"text":"hello"}]},"configuration":{"ifGenerationMatch":"4"}}}
                """.formatted(SEND_MESSAGE_METHOD);

        routes.invokeJSONRPCHandler(body, routingContext);

        verify(handler, never()).onMessageSend(any(SendMessageRequest.class), any(ServerCallContext.class));
        verify(handler, never()).onSendInput(any(SendInputRequest.class), any(ServerCallContext.class));
        var error = org.a2aproject.sdk.jsonrpc.common.json.JsonUtil.fromJson(
                responseBody(), org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse.class);
        assertInstanceOf(UnsupportedOperationError.class, error.getError());
    }

    @Test
    void selected11StreamingArtifactsAndGuardsAreRejectedBeforeRouteAuthorization() throws Exception {
        String artifactBody = """
                {"jsonrpc":"2.0","id":"r1","method":"%s","params":{"artifactUpdates":[{"taskId":"task-1","contextId":"context-1","artifact":{"artifactId":"input-1","parts":[{"text":"payload"}]}}]}}
                """.formatted(SEND_STREAMING_MESSAGE_METHOD);
        String guardBody = """
                {"jsonrpc":"2.0","id":"r2","method":"%s","params":{"message":{"messageId":"m1","taskId":"task-1","contextId":"c1","role":"ROLE_USER","parts":[{"text":"hello"}]},"configuration":{"ifGenerationMatch":"0"}}}
                """.formatted(SEND_STREAMING_MESSAGE_METHOD);

        routes.invokeJSONRPCHandler(artifactBody, routingContext);
        routes.invokeJSONRPCHandler(guardBody, routingContext);

        verify(handler, never()).authorizeTaskAccess(any(), any(ServerCallContext.class), any());
        verify(handler, never()).onSendInputStream(any(SendInputStreamingRequest.class), any(ServerCallContext.class));
        org.mockito.ArgumentCaptor<String> bodies = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(response, times(2)).end(bodies.capture());
        for (String body : bodies.getAllValues()) {
            var error = org.a2aproject.sdk.jsonrpc.common.json.JsonUtil.fromJson(
                    body, org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse.class);
            assertInstanceOf(UnsupportedOperationError.class, error.getError());
        }
    }

    @Test
    void explicit10SubscriptionCursorIncludingZeroIsRejectedBeforeRouteAuthorization() throws Exception {
        when(contextFactories.get()).thenReturn(rc -> new ServerCallContext(UnauthenticatedUser.INSTANCE,
                new HashMap<>(), Set.of(), "1.0"));
        String body = """
                {"jsonrpc":"2.0","id":"s1","method":"%s","params":{"id":"task-1","lastSeenGeneration":0}}
                """.formatted(SUBSCRIBE_TO_TASK_METHOD);

        routes.invokeJSONRPCHandler(body, routingContext);

        verify(handler, never()).authorizeTaskAccess(any(), any(ServerCallContext.class), any());
        verify(handler, never()).onSubscribeToTask(any(), any(ServerCallContext.class));
        var error = org.a2aproject.sdk.jsonrpc.common.json.JsonUtil.fromJson(
                responseBody(), org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse.class);
        assertInstanceOf(UnsupportedOperationError.class, error.getError());
    }

    private String responseBody() throws Exception {
        // Captured from Vert.x response.end(String) without coupling the route to a test serializer.
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(response).end(captor.capture());
        return captor.getValue();
    }

    private void setField(String name, Object value) throws Exception {
        Field field = A2AServerRoutes.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(routes, value);
    }
}
