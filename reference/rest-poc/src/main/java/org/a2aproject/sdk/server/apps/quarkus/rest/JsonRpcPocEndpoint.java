package org.a2aproject.sdk.server.apps.quarkus.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
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
    public void handleRequest(String body, @Context RoutingContext rc) {
        rc.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end("{}");
    }
}
