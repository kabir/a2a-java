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

            if (method == null) {
                sendErrorResponse(rc, id, -32600, "Invalid Request: missing method");
                return Uni.createFrom().voidItem();
            }

            if ("testNonStreaming".equals(method)) {
                handleNonStreaming(rc, id);
                return Uni.createFrom().voidItem();
            } else if ("testStreaming".equals(method)) {
                // For streaming, return a Uni that completes when streaming is done
                return handleStreaming(rc, id);
            } else {
                sendErrorResponse(rc, id, -32601, "Method not found: " + method);
                return Uni.createFrom().voidItem();
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            sendErrorResponse(rc, null, -32700, "Parse error: " + e.getMessage());
            return Uni.createFrom().voidItem();
        } catch (Exception e) {
            sendErrorResponse(rc, null, -32603, "Internal error: " + e.getMessage());
            return Uni.createFrom().voidItem();
        }
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

    /**
     * Handle streaming JSON-RPC method.
     * Returns SSE stream with events arriving asynchronously.
     *
     * <p>This demonstrates:
     * <ul>
     *   <li>Setting SSE headers immediately</li>
     *   <li>Returning control to caller (method exits)</li>
     *   <li>Events arriving asynchronously via Vert.x timers</li>
     * </ul>
     */
    private Uni<Void> handleStreaming(RoutingContext rc, Object id) {
        io.vertx.core.http.HttpServerResponse response = rc.response();

        // Set SSE headers immediately
        response.putHeader("Content-Type", "text/event-stream");
        response.putHeader("Cache-Control", "no-cache");
        response.putHeader("X-Accel-Buffering", "no");
        response.setChunked(true);

        // Send initial comment to establish connection
        response.write(": SSE stream started\n\n");

        // Create a Uni that will complete when streaming is done
        return Uni.createFrom().emitter(emitter -> {
            // Schedule async event generation (3 events, 500ms apart)
            final int totalEvents = 3;
            final long[] eventCounter = {0};

            io.vertx.core.Vertx vertx = rc.vertx();

            long timerId = vertx.setPeriodic(500, timerId2 -> {
                long eventNum = eventCounter[0]++;

                // Create event result
                JsonObject result = new JsonObject();
                result.addProperty("event", eventNum + 1);
                result.addProperty("timestamp", System.currentTimeMillis());

                // Create JSON-RPC response for this event
                JsonRpcResponse eventResponse = new JsonRpcResponse(id, result);
                String jsonEvent = GSON.toJson(eventResponse);

                // Format as SSE
                String sseEvent = "id: " + eventNum + "\n" +
                                 "data: " + jsonEvent + "\n\n";

                // Write event
                response.write(sseEvent);

                // If this was the last event, close stream
                if (eventNum + 1 >= totalEvents) {
                    vertx.cancelTimer(timerId2);
                    response.end();
                    emitter.complete(null);
                }
            });

            // Handle client disconnect - cancel timer
            response.closeHandler(v -> {
                vertx.cancelTimer(timerId);
                emitter.complete(null);
            });
        });
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
