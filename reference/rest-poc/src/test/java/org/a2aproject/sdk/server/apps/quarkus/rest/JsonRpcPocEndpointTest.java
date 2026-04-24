package org.a2aproject.sdk.server.apps.quarkus.rest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

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
    void testStreamingMethod() {
        Response response = given()
            .contentType(ContentType.JSON)
            .body("{\"jsonrpc\":\"2.0\",\"method\":\"testStreaming\",\"id\":\"stream-123\"}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .contentType("text/event-stream")
            .extract()
            .response();

        // Parse SSE format and verify at least 3 events
        String body = response.getBody().asString();
        String[] lines = body.split("\n");

        int eventCount = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("data: ")) {
                eventCount++;
                String jsonData = line.substring(6); // Remove "data: " prefix

                // Verify JSON-RPC structure
                JsonObject eventObj = GSON.fromJson(jsonData, JsonObject.class);
                assertEquals("2.0", eventObj.get("jsonrpc").getAsString());
                assertEquals("stream-123", eventObj.get("id").getAsString());
                assertTrue(eventObj.has("result"));

                JsonObject result = eventObj.getAsJsonObject("result");
                assertTrue(result.has("event"));
                assertTrue(result.has("timestamp"));
            }
        }

        assertTrue(eventCount >= 3, "Expected at least 3 events, got " + eventCount);
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
            .body("error.code", equalTo(-32601))
            .body("error.message", containsString("Method not found"));
    }
}
