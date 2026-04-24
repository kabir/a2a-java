package org.a2aproject.sdk.server.apps.quarkus.rest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class JsonRpcPocEndpointTest {

    private static final Gson GSON = new Gson();

    @Test
    void testEndpointExists() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"jsonrpc\":\"2.0\",\"method\":\"testNonStreaming\",\"id\":\"test-1\"}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
    }

    @Test
    void testNonStreamingMethod() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"jsonrpc\":\"2.0\",\"method\":\"testNonStreaming\",\"id\":\"test-123\"}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("jsonrpc", equalTo("2.0"))
            .body("id", equalTo("test-123"))
            .body("result.message", notNullValue())
            .body("result.timestamp", notNullValue());
    }

    @Test
    void testStreamingMethod() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8081/"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"jsonrpc\":\"2.0\",\"method\":\"testStreaming\",\"id\":\"stream-123\"}"))
            .build();

        List<Long> eventTimestamps = new CopyOnWriteArrayList<>();
        List<String> eventData = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        CompletableFuture<Void> future = client.sendAsync(request,
            HttpResponse.BodyHandlers.ofLines())
            .thenAccept(response -> {
                assertEquals(200, response.statusCode(),
                    "Expected HTTP 200 status");
                assertEquals("text/event-stream", response.headers().firstValue("content-type").orElse(""),
                    "Expected text/event-stream content type");

                response.body().forEach(line -> {
                    if (line.startsWith("data: ")) {
                        eventTimestamps.add(System.currentTimeMillis());
                        eventData.add(line.substring(6));
                        latch.countDown();
                    }
                });
            });

        // Wait for at least 3 events (with timeout)
        assertTrue(latch.await(5, TimeUnit.SECONDS),
            "Expected 3 events within 5 seconds");

        // Verify 3 events received
        assertEquals(3, eventData.size(),
            "Expected exactly 3 events");

        // Verify timing (events ~500ms apart)
        for (int i = 1; i < eventTimestamps.size(); i++) {
            long interval = eventTimestamps.get(i) - eventTimestamps.get(i - 1);
            assertTrue(interval >= 300 && interval <= 700,
                "Event " + i + " arrived " + interval + "ms after previous (expected ~500ms)");
        }

        // Verify JSON-RPC structure of each event
        for (String data : eventData) {
            JsonObject eventObj = GSON.fromJson(data, JsonObject.class);
            assertEquals("2.0", eventObj.get("jsonrpc").getAsString(),
                "Expected JSON-RPC 2.0");
            assertEquals("stream-123", eventObj.get("id").getAsString(),
                "Expected matching request ID");
            assertTrue(eventObj.has("result"),
                "Expected result field");

            JsonObject result = eventObj.getAsJsonObject("result");
            assertTrue(result.has("event"),
                "Expected event field in result");
            assertTrue(result.has("timestamp"),
                "Expected timestamp field in result");
        }

        // Ensure future completes
        future.get(1, TimeUnit.SECONDS);
    }

    @Test
    void testInvalidJson() {
        given()
            .contentType(ContentType.JSON)
            .body("{invalid json}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("jsonrpc", equalTo("2.0"))
            .body("error.code", equalTo(-32700))
            .body("error.message", containsString("Parse error"));
    }

    @Test
    void testUnknownMethod() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"jsonrpc\":\"2.0\",\"method\":\"unknownMethod\",\"id\":\"test-1\"}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("jsonrpc", equalTo("2.0"))
            .body("id", equalTo("test-1"))
            .body("error.code", equalTo(-32601))
            .body("error.message", containsString("Method not found"));
    }

    @Test
    void testMissingMethod() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"jsonrpc\":\"2.0\",\"id\":\"test-1\"}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("error.code", equalTo(-32600))
            .body("error.message", containsString("Invalid Request"));
    }

    @Test
    void testGsonSerialization() {
        // Send request with special Gson-friendly structure
        String response = given()
            .contentType(ContentType.JSON)
            .body("{\"jsonrpc\":\"2.0\",\"method\":\"testNonStreaming\",\"id\":\"gson-test\"}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .extract()
            .asString();

        // Parse response and verify structure (Gson-specific behavior)
        JsonObject parsed = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(parsed.has("jsonrpc"));
        assertTrue(parsed.has("id"));
        assertTrue(parsed.has("result"));

        // Verify the response can be parsed by Gson
        assertEquals("2.0", parsed.get("jsonrpc").getAsString());
        assertEquals("gson-test", parsed.get("id").getAsString());
    }
}
