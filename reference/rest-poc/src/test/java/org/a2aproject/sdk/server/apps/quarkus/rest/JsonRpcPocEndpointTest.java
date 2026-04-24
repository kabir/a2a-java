package org.a2aproject.sdk.server.apps.quarkus.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class JsonRpcPocEndpointTest {

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
}
