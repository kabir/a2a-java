package org.a2aproject.sdk.tests.multiversion.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;

import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.http.vertx.VertxA2AHttpClient;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.server.apps.common.AbstractPairedVersionServerTest;
import org.a2aproject.sdk.server.apps.common.PairedVersionTestProfile;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(PairedVersionTestProfile.class)
public class MultiVersionRestTest extends AbstractPairedVersionServerTest {

    @Inject
    A2ATestRoutes protocolVersionCapture;

    @Override
    protected List<String> drainProtocolVersions() {
        return protocolVersionCapture.drainProtocolVersions();
    }

    @Inject
    Vertx vertx;

    public MultiVersionRestTest() {
        super(8081);
    }

    @Test
    public void testPublic03GetAgainstRegisteredSameServer() throws Exception {
        saveTaskInTaskStore(MINIMAL_TASK);
        try {
            Public03ClientSupport.assertGet(getTransportUrl(), MINIMAL_TASK.id());
        } finally {
            deleteTaskInTaskStore(MINIMAL_TASK.id());
        }
    }

    @Test
    public void testPublic03SendAgainstRegisteredSameServer() throws Exception {
        Public03ClientSupport.assertSend(getTransportUrl());
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.HTTP_JSON.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "http://localhost:8081";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(RestTransport.class,
                new RestTransportConfigBuilder()
                        .httpClient(new VertxA2AHttpClient(vertx)));
    }
    @Override
    protected void assertRawProvisionedVersion(String tenant, @Nullable String version, boolean accepted) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(getTransportUrl() + "/" + tenant + "/tasks/" + MINIMAL_TASK.id())).GET();
        if (version != null) {
            request.header("A2A-Version", version);
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        var payload = JsonParser.parseString(response.body()).getAsJsonObject();
        if (accepted) {
            assertEquals(200, response.statusCode(), response.body());
            assertFalse(payload.has("error"), response.body());
            assertEquals(MINIMAL_TASK.id(), payload.get("id").getAsString());
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

    @Test
    public void testHistoricalCardRouteDoesNotServe03For11() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create(getTransportUrl() + "/v1/card"))
                .header("A2A-Version", "1.1").GET().build(), HttpResponse.BodyHandlers.ofString());
        RestCardProvisioningWithout03Test.assertVersionRejection(response);
    }

    @Test
    public void testUnsupportedVersionBeforeMalformedHistory() throws Exception {
        HttpResponse<String> response = historyResponse("2.0", "historyLength=bad");
        assertEquals(400, response.statusCode());
        var payload = JsonParser.parseString(response.body()).getAsJsonObject();
        String message = payload.getAsJsonObject("error").remove("message").getAsString();
        assertTrue(message.contains("Protocol version '2.0' is not provisioned"), message);
        assertEquals(JsonParser.parseString("""
                {"error":{"code":-32009}}
                """), payload);
    }

    @Test
    public void testSelectedVersionHistoryValidation() throws Exception {
        HttpResponse<String> response = historyResponse("1.0", "historyLength=bad");
        assertEquals(422, response.statusCode());
        assertEquals(JsonParser.parseString("""
                {"error":{"code":422,"status":"INVALID_ARGUMENT","message":"bad historyLength","details":[{
                "@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"INVALID_PARAMS",
                "domain":"a2a-protocol.org","metadata":{}}]}}
                """), JsonParser.parseString(response.body()));
    }
    private HttpResponse<String> historyResponse(String header, String query) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/v1/tasks/missing-history-task?" + query)).GET();
        if (header != null) {
            request.header("A2A-Version", header);
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElseThrow());
        return response;
    }

}
