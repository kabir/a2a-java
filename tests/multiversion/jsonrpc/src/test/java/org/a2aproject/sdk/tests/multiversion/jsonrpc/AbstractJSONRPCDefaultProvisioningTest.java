package org.a2aproject.sdk.tests.multiversion.jsonrpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


import com.google.gson.JsonParser;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.server.apps.common.AbstractDefaultEndpointProvisioningTest;
import org.jspecify.annotations.Nullable;

/** Native 0.3 paths share the configured endpoint with exact-version 1.x clients. */
public abstract class AbstractJSONRPCDefaultProvisioningTest extends AbstractDefaultEndpointProvisioningTest {
    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(JSONRPCTransport.class,
                new JSONRPCTransportConfigBuilder().httpClient(new JdkA2AHttpClient()));
    }

    @Override
    protected void exercisePublic03(String endpoint, String taskId) throws Exception {
        Public03ClientSupport.assertGet(endpoint, taskId);
        Public03ClientSupport.assertSend(endpoint);
    }

    @Override
    protected void assertRawVersion(String endpoint, String taskId, @Nullable String version,
            boolean accepted) throws Exception {
        String method = version == null || version.isBlank() || version.equals("0.3") ? "tasks/get" : "GetTask";
        String body = "{\"jsonrpc\":\"2.0\",\"id\":\"default-subset\",\"method\":\"" + method
                + "\",\"params\":{\"id\":\"" + taskId + "\"}}";
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create(endpoint + "/"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (version != null) {
            request.header("A2A-Version", version);
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        var payload = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(200, response.statusCode(), response.body());
        if (accepted) {
            assertFalse(payload.has("error"), response.body());
            assertEquals(taskId, payload.getAsJsonObject("result").get("id").getAsString());
        } else {
            assertEquals(-32009, payload.getAsJsonObject("error").get("code").getAsInt(), response.body());
        }
    }
}
