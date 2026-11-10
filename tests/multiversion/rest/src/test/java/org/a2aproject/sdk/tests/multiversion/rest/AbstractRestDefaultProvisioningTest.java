package org.a2aproject.sdk.tests.multiversion.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.StreamSupport;

import com.google.gson.JsonParser;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.server.apps.common.AbstractDefaultEndpointProvisioningTest;
import org.jspecify.annotations.Nullable;

/** Native 0.3 paths share the configured endpoint with exact-version 1.x clients. */
public abstract class AbstractRestDefaultProvisioningTest extends AbstractDefaultEndpointProvisioningTest {
    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(RestTransport.class,
                new RestTransportConfigBuilder().httpClient(new JdkA2AHttpClient()));
    }

    @Override
    protected void exercisePublic03(String endpoint, String taskId) throws Exception {
        Public03ClientSupport.assertGet(endpoint, taskId);
        Public03ClientSupport.assertSend(endpoint);
    }

    @Override
    protected void assertRawVersion(String endpoint, String taskId, @Nullable String version,
            boolean accepted) throws Exception {
        boolean historical = version == null || version.isBlank() || version.equals("0.3");
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + (historical ? "/v1" : "") + "/tasks/" + taskId)).GET();
        if (version != null) {
            request.header("A2A-Version", version);
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        var payload = JsonParser.parseString(response.body()).getAsJsonObject();
        if (accepted) {
            assertEquals(200, response.statusCode(), response.body());
            assertFalse(payload.has("error"), response.body());
            assertEquals(taskId, payload.get("id").getAsString());
        } else {
            assertEquals(400, response.statusCode(), response.body());
            var error = payload.getAsJsonObject("error");
            if ("1.1".equals(version)) {
                assertEquals(400, error.get("code").getAsInt(), response.body());
                var details = error.getAsJsonArray("details");
                assertTrue(details != null && StreamSupport.stream(details.spliterator(), false)
                        .map(detail -> detail.getAsJsonObject())
                        .anyMatch(detail -> detail.has("reason")
                                && detail.get("reason").getAsString().equals("VERSION_NOT_SUPPORTED")), response.body());
            } else {
                assertEquals(-32009, error.get("code").getAsInt(), response.body());
            }
        }
    }
}
