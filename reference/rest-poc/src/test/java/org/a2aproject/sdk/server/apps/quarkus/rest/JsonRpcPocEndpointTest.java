package org.a2aproject.sdk.server.apps.quarkus.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class JsonRpcPocEndpointTest {

    @Test
    void testEndpointExists() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
        .when()
            .post("/")
        .then()
            .statusCode(200);
    }
}
