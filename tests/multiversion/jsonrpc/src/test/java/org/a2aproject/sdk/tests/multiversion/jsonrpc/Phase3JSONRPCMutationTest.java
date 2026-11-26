package org.a2aproject.sdk.tests.multiversion.jsonrpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import jakarta.inject.Inject;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.http.vertx.VertxA2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.server.apps.common.Phase3MutationScenarios;
import org.a2aproject.sdk.server.apps.common.Phase3TestProfile;
import org.a2aproject.sdk.server.apps.common.TestUtilsBean;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.jspecify.annotations.Nullable;

/** P3-T17 JSON-RPC production slice: the shared scenarios against the real reference server. */
@QuarkusTest
@TestProfile(Phase3TestProfile.class)
public class Phase3JSONRPCMutationTest extends Phase3MutationScenarios {

    @Inject
    A2ATestRoutes ingress;

    @Inject
    TestUtilsBean testUtils;

    @Inject
    Vertx vertx;

    @Override
    protected String transportProtocol() {
        return TransportProtocol.JSONRPC.asString();
    }

    @Override
    protected String transportUrl() {
        return "http://localhost:8081";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(JSONRPCTransport.class,
                new JSONRPCTransportConfigBuilder().httpClient(new VertxA2AHttpClient(vertx)));
    }

    @Override
    protected List<String> drainIngress() {
        return ingress.drainProtocolVersions();
    }

    @Override
    protected void commitWithoutPublication(Task task) {
        testUtils.saveTask(task);
    }

    @Override
    protected @Nullable Task storedTask(String taskId) {
        return testUtils.getTask(taskId);
    }

    @Override
    protected void deleteStoredTask(String taskId) {
        testUtils.deleteTask(taskId);
    }

    @Override
    protected int childQueueCount(String taskId) {
        return testUtils.getChildQueueCount(taskId);
    }

    @Override
    protected int pushConfigCount(String taskId) {
        return testUtils.pushConfigCount(taskId);
    }

    @Override
    protected void send03Message(String taskId, String contextId, String text) throws Exception {
        Public03ClientSupport.sendToTask(transportUrl(), taskId, contextId, text);
    }

    @Override
    protected void assertRawMalformedFinalArtifactRejected(String taskId, String contextId, String otherTaskId)
            throws Exception {
        for (String finalElement : List.of(
                "{\"taskId\":\"%1$s\",\"contextId\":\"%2$s\"}",
                "{\"taskId\":\"%1$s\",\"contextId\":\"%2$s\",\"artifact\":{\"artifactId\":\"\",\"parts\":[{\"text\":\"x\"}]}}",
                "{\"taskId\":\"%3$s\",\"contextId\":\"%2$s\",\"artifact\":{\"artifactId\":\"raw-other\","
                        + "\"parts\":[{\"text\":\"x\"}]}}")) {
            String body = """
                    {"jsonrpc":"2.0","id":"raw-malformed","method":"SendMessage","params":{
                      "message":{"messageId":"raw-malformed-msg","taskId":"%1$s","contextId":"%2$s","role":"ROLE_USER",
                        "parts":[{"text":"phase3:noop"}]},
                      "artifactUpdates":[
                        {"taskId":"%1$s","contextId":"%2$s","artifact":{"artifactId":"raw-ok","parts":[{"text":"ok"}]}},
                        %4$s]}}
                    """.formatted(taskId, contextId, otherTaskId, finalElement.formatted(taskId, contextId, otherTaskId));
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(transportUrl() + "/"))
                    .header("Content-Type", "application/json").header("A2A-Version", "1.1")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
            assertTrue(payload.has("error"), "a malformed final artifact element must be rejected: " + response.body());
            assertEquals(-32602, payload.getAsJsonObject("error").get("code").getAsInt(), response.body());
        }
    }
}
