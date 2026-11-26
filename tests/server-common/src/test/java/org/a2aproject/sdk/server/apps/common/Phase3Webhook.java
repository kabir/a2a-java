package org.a2aproject.sdk.server.apps.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;

/**
 * A loopback webhook receiver. Notifications leave the server through its real HTTP client and arrive here, so a
 * scenario observes exactly what a client-registered webhook would receive, in the 1.1 wire shape.
 */
public final class Phase3Webhook {

    /** Every streaming event delivered to the receiver since the JVM started, in arrival order. */
    public static final List<StreamingEventKind> RECEIVED = new CopyOnWriteArrayList<>();

    private static HttpServer server;

    private Phase3Webhook() {
    }

    /** Starts the receiver on first use and returns its URL. */
    public static synchronized String url() {
        if (server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.createContext("/hook", exchange -> {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    RECEIVED.add(JsonUtil.fromJson(body, StreamingEventKind.class));
                } catch (JsonProcessingException | RuntimeException e) {
                    System.err.println("Phase3Webhook could not parse a notification: " + e);
                }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
        }
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/hook";
    }

    /** Every event (task, status or artifact update) delivered for {@code taskId}. */
    public static List<StreamingEventKind> eventsFor(String taskId) {
        return RECEIVED.stream().filter(event -> event instanceof Task task && task.id().equals(taskId)
                || event instanceof org.a2aproject.sdk.spec.TaskStatusUpdateEvent status && status.taskId().equals(taskId)
                || event instanceof org.a2aproject.sdk.spec.TaskArtifactUpdateEvent artifact
                        && artifact.taskId().equals(taskId)).toList();
    }

    /** The tasks delivered for {@code taskId}. */
    public static List<Task> tasks(String taskId) {
        return RECEIVED.stream().filter(Task.class::isInstance).map(Task.class::cast)
                .filter(task -> task.id().equals(taskId)).toList();
    }
}
