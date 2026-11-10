package org.a2aproject.sdk.transport.rest.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonParser;
import org.a2aproject.sdk.server.AgentCardCacheMetadata;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

class RestVersionPolicyTest {
    @Test
    void cancelErrorResponseAndBodyUseTheImmutableRequestVersion() {
        InterfaceRegistration registration = new InterfaceRegistration(TransportProtocol.HTTP_JSON,
                "http://localhost:9999", null, Set.of("1.0", "1.1"), true);
        AgentCard card = AgentCard.builder().name("test").description("test").version("1")
                .supportedInterfaces(registration.supportedInterfaces())
                .capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text/plain")).defaultOutputModes(List.of("text/plain"))
                .skills(List.of()).build();
        RequestHandler requestHandler = mock(RequestHandler.class);
        when(requestHandler.onCancelTask(any(), any())).thenThrow(new TaskNotCancelableError());
        RestHandler handler = new RestHandler(card, mock(AgentCardCacheMetadata.class), requestHandler, Runnable::run);
        for (String version : List.of("1.0", "1.1")) {
            ServerCallContext context = new ServerCallContext(UnauthenticatedUser.INSTANCE,
                    Map.of(ServerCallContext.TRANSPORT_KEY, TransportProtocol.HTTP_JSON), Set.of(), version);
            context.attachInterfaceRegistration(registration);
            RestHandler.HTTPRestResponse response = handler.cancelTask(context, "", "{}", "task");
            int expected = version.equals("1.1") ? 400 : 409;
            assertEquals(expected, response.getStatusCode());
            assertEquals(expected, JsonParser.parseString(response.getBody()).getAsJsonObject()
                    .getAsJsonObject("error").get("code").getAsInt());
        }
    }
}
