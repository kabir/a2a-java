package org.a2aproject.sdk.tests.multiversion.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonParser;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.compat03.client.ClientBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.Client_v0_3;
import org.a2aproject.sdk.compat03.client.MessageEvent_v0_3;
import org.a2aproject.sdk.compat03.client.config.ClientConfig_v0_3;
import org.a2aproject.sdk.compat03.client.transport.rest.RestTransportConfigBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.rest.RestTransport_v0_3;
import org.a2aproject.sdk.compat03.grpc.utils.ProtoJsonUtils_v0_3;
import org.a2aproject.sdk.compat03.grpc.utils.ProtoUtils_v0_3;
import org.a2aproject.sdk.compat03.spec.A2AClientException_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentCapabilities_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentInterface_v0_3;
import org.a2aproject.sdk.compat03.spec.MessageSendParams_v0_3;
import org.a2aproject.sdk.compat03.spec.Message_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskQueryParams_v0_3;
import org.a2aproject.sdk.compat03.spec.TextPart_v0_3;

/** Uses the unchanged public 0.3 SDK with its native configured endpoint and wire shape. */
final class Public03ClientSupport {
    private Public03ClientSupport() {
    }

    static void assertGet(String endpoint, String taskId) throws Exception {
        try (Handle handle = create(endpoint)) {
            assertEquals(taskId, handle.client().getTask(new TaskQueryParams_v0_3(taskId), null).id());
        }
    }

    static void assertSend(String endpoint) throws Exception {
        try (Handle handle = create(endpoint)) {
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Message_v0_3> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Message_v0_3 request = message();
            handle.client().sendMessage(request, List.of((event, card) -> {
                if (event instanceof MessageEvent_v0_3 messageEvent) {
                    response.set(messageEvent.getMessage());
                    completed.countDown();
                }
            }), error -> {
                failure.set(error);
                completed.countDown();
            }, null);
            assertTrue(completed.await(10, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertNotNull(response.get());
            assertEquals(request.messageId(), response.get().messageId());
            assertEquals("test message", ((TextPart_v0_3) response.get().parts().get(0)).text());
        }
    }

    static void assertGetRejected(String endpoint, String taskId) throws Exception {
        try (Handle handle = create(endpoint)) {
            assertLegacyGenericRejection(assertThrows(A2AClientException_v0_3.class,
                    () -> handle.client().getTask(new TaskQueryParams_v0_3(taskId), null)));
            assertNativeRawVersionRejection(HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/v1/tasks/" + taskId)).GET().build());
        }
    }

    static void assertSendRejected(String endpoint) throws Exception {
        try (Handle handle = create(endpoint)) {
            assertLegacyGenericRejection(assertThrows(A2AClientException_v0_3.class,
                    () -> handle.client().sendMessage(message(), null)));
            assertNativeRawVersionRejection(HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/v1/message:send")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(nativeSendBody())).build());
        }
    }

    private static void assertLegacyGenericRejection(A2AClientException_v0_3 error) {
        // The frozen 0.3 mapper has no version error type and cannot read the dispatcher envelope.
        assertEquals(A2AClientException_v0_3.class, error.getClass());
        assertEquals("", error.getMessage());
        assertNull(error.getCause());
    }

    static String nativeSendBody() throws Exception {
        return ProtoJsonUtils_v0_3.toJson(JsonFormat.printer(),
                ProtoUtils_v0_3.ToProto.sendMessageRequest(new MessageSendParams_v0_3(message(), null, null)));
    }

    static void assertNativeRawVersionRejection(HttpRequest request) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode(), response.body());
        var error = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("error");
        assertEquals(-32009, error.get("code").getAsInt(), response.body());
        assertTrue(error.get("message").getAsString().contains("0.3"), response.body());
    }

    private static Message_v0_3 message() {
        return new Message_v0_3.Builder().messageId("public03-message").role(Message_v0_3.Role.AGENT)
                .parts(new TextPart_v0_3("test message")).build();
    }

    private static Handle create(String endpoint) throws Exception {
        AgentCard_v0_3 card = new AgentCard_v0_3.Builder().name("test-card").description("A test agent card")
                .url(endpoint).version("1.0").protocolVersion("0.3").preferredTransport("HTTP+JSON")
                .capabilities(new AgentCapabilities_v0_3.Builder().streaming(true).pushNotifications(true).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of())
                .additionalInterfaces(List.of(new AgentInterface_v0_3("HTTP+JSON", endpoint))).build();
        ClientBuilder_v0_3 builder = Client_v0_3.builder(card)
                .clientConfig(new ClientConfig_v0_3.Builder().setStreaming(false).build());
        builder.withTransport(RestTransport_v0_3.class, new RestTransportConfigBuilder_v0_3());
        return new Handle(builder.build(), () -> { });
    }

    private record Handle(Client_v0_3 client, Runnable cleanup) implements AutoCloseable {
        @Override
        public void close() {
            try {
                client.close();
            } finally {
                cleanup.run();
            }
        }
    }
}
