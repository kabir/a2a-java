package org.a2aproject.sdk.tests.multiversion.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.a2aproject.sdk.server.apps.common.PairedVersionTestProfile;
import org.junit.jupiter.api.Test;

/** The historical card route must obey actual default-endpoint provisioning. */
@QuarkusTest
@TestProfile(RestCardProvisioningWithout03Test.Without03Profile.class)
public class RestCardProvisioningWithout03Test {
    public static class Without03Profile extends PairedVersionTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // /v1/card has a fixed historical prefix and must use the default registration.
            // A distinct tenant named v1 has its own 1.x-only registration.
            return Map.of("test.a2a.experimental", "true", "test.a2a.versions", "1.0,1.1",
                    "test.a2a.v1-versions", "1.0");
        }
    }

    @Test
    public void testHistoricalCardRouteRejectsDisabled03() throws Exception {
        for (String version : new String[] {"0.3", null, "", " "}) {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8081/v1/card")).GET();
            if (version != null) {
                request.header("A2A-Version", version);
            }
            assertVersionRejection(HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString()));
        }
    }

    @Test
    public void testPublicCardOmitsDisabled03() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/.well-known/agent-card.json")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        var interfaces = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("supportedInterfaces");
        Set<String> versions = StreamSupport.stream(interfaces.spliterator(), false)
                .map(entry -> entry.getAsJsonObject().get("protocolVersion").getAsString()).collect(Collectors.toSet());
        assertEquals(Set.of("1.0", "1.1"), versions);
    }

    static void assertVersionRejection(HttpResponse<String> response) {
        assertEquals(400, response.statusCode(), response.body());
        var payload = JsonParser.parseString(response.body()).getAsJsonObject();
        assertFalse(payload.has("supportedInterfaces"), response.body());
        var error = payload.getAsJsonObject("error");
        assertNotNull(error, response.body());
        boolean versionReason = error.has("details") && StreamSupport.stream(error.getAsJsonArray("details").spliterator(), false)
                .map(detail -> detail.getAsJsonObject())
                .anyMatch(detail -> detail.has("reason") && detail.get("reason").getAsString().equals("VERSION_NOT_SUPPORTED"));
        assertTrue(error.get("code").getAsInt() == -32009 || versionReason, response.body());
    }
}
