package org.a2aproject.sdk.server.apps.quarkus.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
public class JsonRpcPocEndpoint {

    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.LENIENT).create();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleRequest(String body) {
        try {
            JsonObject jsonRequest = JsonParser.parseString(body).getAsJsonObject();
            Object id = jsonRequest.has("id") ? jsonRequest.get("id").getAsString() : null;
            String method = jsonRequest.has("method") ? jsonRequest.get("method").getAsString() : null;

            if ("testNonStreaming".equals(method)) {
                return handleNonStreaming(id);
            } else {
                return sendErrorResponse(id, -32601, "Method not found: " + method);
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            return sendErrorResponse(null, -32700, "Parse error: " + e.getMessage());
        } catch (Exception e) {
            return sendErrorResponse(null, -32603, "Internal error: " + e.getMessage());
        }
    }

    private Response handleNonStreaming(Object id) {
        JsonObject result = new JsonObject();
        result.addProperty("message", "Non-streaming response");
        result.addProperty("timestamp", System.currentTimeMillis());

        JsonRpcResponse response = new JsonRpcResponse(id, result);
        String jsonResponse = GSON.toJson(response);

        return Response.ok(jsonResponse, MediaType.APPLICATION_JSON).build();
    }

    private Response sendErrorResponse(Object id, int code, String message) {
        JsonRpcErrorResponse errorResponse = new JsonRpcErrorResponse(id, code, message);
        String jsonError = GSON.toJson(errorResponse);

        return Response.ok(jsonError, MediaType.APPLICATION_JSON).build();
    }
}
