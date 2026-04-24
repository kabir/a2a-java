package org.a2aproject.sdk.server.apps.quarkus.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class JsonRpcPocEndpoint {

    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.LENIENT).create();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Void> handleRequest(String body, @Context RoutingContext rc) {
        try {
            JsonObject jsonRequest = JsonParser.parseString(body).getAsJsonObject();
            Object id = jsonRequest.has("id") ? jsonRequest.get("id").getAsString() : null;
            String method = jsonRequest.has("method") ? jsonRequest.get("method").getAsString() : null;

            if ("testNonStreaming".equals(method)) {
                handleNonStreaming(rc, id);
            } else {
                sendErrorResponse(rc, id, -32601, "Method not found: " + method);
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            sendErrorResponse(rc, null, -32700, "Parse error: " + e.getMessage());
        } catch (Exception e) {
            sendErrorResponse(rc, null, -32603, "Internal error: " + e.getMessage());
        }
        return Uni.createFrom().voidItem();
    }

    private void handleNonStreaming(RoutingContext rc, Object id) {
        JsonObject result = new JsonObject();
        result.addProperty("message", "Non-streaming response");
        result.addProperty("timestamp", System.currentTimeMillis());

        JsonRpcResponse response = new JsonRpcResponse(id, result);
        String jsonResponse = GSON.toJson(response);

        rc.response()
            .setStatusCode(200)
            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .end(jsonResponse);
    }

    private void sendErrorResponse(RoutingContext rc, Object id, int code, String message) {
        JsonRpcErrorResponse errorResponse = new JsonRpcErrorResponse(id, code, message);
        String jsonError = GSON.toJson(errorResponse);

        rc.response()
            .setStatusCode(200)
            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .end(jsonError);
    }
}
