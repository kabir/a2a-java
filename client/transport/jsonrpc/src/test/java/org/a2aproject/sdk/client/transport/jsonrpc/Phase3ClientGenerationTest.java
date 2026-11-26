package org.a2aproject.sdk.client.transport.jsonrpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TransportEnd;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.ObserveTaskParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Controlled JSON-RPC peer evidence for the no-cursor {@code observe} companion added in P3-T17, including peers a
 * correct server must never emulate (generation zero, equal consecutive generations). The peer is not a substitute for
 * the real server path; see {@code Phase3JSONRPCMutationTest}. The transport reports frames faithfully; the reducer and
 * the Client-owned reliability registry (client/base) are what judge and taint them.
 */
class Phase3ClientGenerationTest {

    private HttpServer server;
    private volatile Function<JsonObject, String> sseReply = requestBody -> "";
    private final List<JsonObject> requests = new CopyOnWriteArrayList<>();
    private volatile Headers lastHeaders;
    private volatile String lastPath;
    private volatile CountDownLatch heldOpen;
    private final CountDownLatch released = new CountDownLatch(1);
    private final CountDownLatch peerSawDisconnect = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        released.countDown();
        server.stop(0);
    }

    @Test
    void observeWithoutCursorSendsSubscribeToTaskAndDeliversKnownFramesOverOneStream() throws Exception {
        String taskEvent = eventEnvelope("task", json(task("task-observe", "context-observe", 1)));
        String statusEvent = eventEnvelope("statusUpdate", status("task-observe", "context-observe"));
        sseReply = requestBody -> sseBody(requestBody, taskEvent, statusEvent);

        JSONRPCTransport selected = new JSONRPCTransport(serverUrl(), "1.1");
        List<TransportFrame> frames = new CopyOnWriteArrayList<>();
        TransportObservation observation = selected.observe(new ObserveTaskParams("task-observe", null, null),
                SpiCallOptions.defaults(), null);
        CountDownLatch completed = collect(observation, frames);

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals(TransportEnd.Kind.COMPLETED,
                observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS).kind());
        assertEquals(2, frames.size());
        assertInstanceOf(Task.class, frames.get(0).event());
        assertInstanceOf(TaskStatusUpdateEvent.class, frames.get(1).event());
        assertEquals(1, requests.size(), "one observation is exactly one request");
        JsonObject sent = requests.get(0);
        assertEquals("SubscribeToTask", sent.get("method").getAsString());
        assertEquals("task-observe", sent.getAsJsonObject("params").get("id").getAsString());
        assertFalse(sent.getAsJsonObject("params").has("lastSeenGeneration"),
                "A no-cursor observation must not send a generation cursor");
        assertEquals("1.1", lastHeaders.getFirst("A2A-Version"));
        selected.close();
    }

    @Test
    void observeUsesTheTenantPathAndKeepsAnUnknownFrameAsSkippedEvidenceBetweenKnownFrames() throws Exception {
        String taskEvent = eventEnvelope("task", json(task("task-tenant", "context-tenant", 1)));
        String unknown = "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{\"futureEvent\":{\"payload\":true}}}";
        String statusEvent = eventEnvelope("statusUpdate", status("task-tenant", "context-tenant"));
        sseReply = requestBody -> sseBody(requestBody, taskEvent, unknown, statusEvent);

        JSONRPCTransport selected = new JSONRPCTransport(serverUrl(), "1.1");
        List<TransportFrame> frames = new CopyOnWriteArrayList<>();
        TransportObservation observation = selected.observe(new ObserveTaskParams("task-tenant", null, "tenant-a"),
                SpiCallOptions.defaults(), null);
        assertTrue(collect(observation, frames).await(5, TimeUnit.SECONDS));

        assertEquals("/tenant-a", lastPath);
        assertEquals(3, frames.size());
        assertInstanceOf(Task.class, frames.get(0).event());
        assertTrue(frames.get(1).unknownSkipped());
        assertEquals(null, frames.get(1).event(), "an unknown variant is skipped evidence, never a fabricated event");
        assertInstanceOf(TaskStatusUpdateEvent.class, frames.get(2).event());
        selected.close();
    }

    @Test
    void invalidGenerationPeersAreReportedFaithfullyForTheReducerToTaint() throws Exception {
        // A correct server never sends generation zero or equal consecutive generations; the transport must not
        // normalise them away, because the Client-owned reliability registry needs to see them.
        String zeroGeneration = eventEnvelope("task", json(task("task-bad", "context-bad", 0)));
        String equalOne = eventEnvelope("messageUpdate", messageUpdate("task-bad", "context-bad", 2, "m2"));
        String equalTwo = eventEnvelope("messageUpdate", messageUpdate("task-bad", "context-bad", 2, "m3"));
        sseReply = requestBody -> sseBody(requestBody, zeroGeneration, equalOne, equalTwo);

        JSONRPCTransport selected = new JSONRPCTransport(serverUrl(), "1.1");
        List<TransportFrame> frames = new CopyOnWriteArrayList<>();
        TransportObservation observation = selected.observe(new ObserveTaskParams("task-bad", null, null),
                SpiCallOptions.defaults(), null);
        assertTrue(collect(observation, frames).await(5, TimeUnit.SECONDS));

        assertEquals(3, frames.size());
        assertEquals(0L, ((Task) frames.get(0).event()).generation());
        var first = assertInstanceOf(org.a2aproject.sdk.spec.TaskMessageUpdateEvent.class, frames.get(1).event());
        var second = assertInstanceOf(org.a2aproject.sdk.spec.TaskMessageUpdateEvent.class, frames.get(2).event());
        assertEquals(first.generation(), second.generation());
        selected.close();
    }

    @Test
    void streamErrorEnvelopeFailsTheObservationWithItsTypedError() throws Exception {
        sseReply = requestBody -> {
            JsonObject error = new JsonObject();
            error.addProperty("code", -32001);
            error.addProperty("message", "Task not found");
            JsonObject envelope = new JsonObject();
            envelope.addProperty("jsonrpc", "2.0");
            envelope.add("id", requestBody.get("id").deepCopy());
            envelope.add("error", error);
            return "data: " + envelope + "\n\n";
        };

        JSONRPCTransport selected = new JSONRPCTransport(serverUrl(), "1.1");
        TransportObservation observation = selected.observe(new ObserveTaskParams("missing", null, null),
                SpiCallOptions.defaults(), null);
        List<TransportFrame> frames = new CopyOnWriteArrayList<>();
        assertTrue(collect(observation, frames).await(5, TimeUnit.SECONDS));

        TransportEnd end = observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(TransportEnd.Kind.FAILED, end.kind());
        assertTrue(hasCause(end.cause(), TaskNotFoundError.class), () -> "typed error expected: " + end.cause());
        assertTrue(frames.isEmpty());
        selected.close();
    }

    @Test
    void cancellingAHeldOpenObservationClosesItWithoutAnotherRequest() throws Exception {
        heldOpen = new CountDownLatch(1);
        JSONRPCTransport selected = new JSONRPCTransport(serverUrl(), "1.1");
        TransportObservation observation = selected.observe(new ObserveTaskParams("task-held", null, null),
                SpiCallOptions.defaults(), null);
        List<TransportFrame> frames = new CopyOnWriteArrayList<>();
        collect(observation, frames);
        assertTrue(heldOpen.await(5, TimeUnit.SECONDS), "the peer must have opened the stream");

        observation.cancel();

        TransportEnd end = observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(TransportEnd.Kind.CANCELLED, end.kind());
        assertTrue(peerSawDisconnect.await(5, TimeUnit.SECONDS), "cancelling must detach the HTTP read");
        assertEquals(1, requests.size(), "cancellation must not resend or open a second stream");
        selected.close();
    }

    @Test
    void observeWithCursorIsRejectedBeforeAnyRequest() throws Exception {
        JSONRPCTransport selected = new JSONRPCTransport(serverUrl(), "1.1");
        assertThrows(UnsupportedOperationException.class, () -> selected.observe(
                new ObserveTaskParams("task-cursor", 4L, null), SpiCallOptions.defaults(), null));
        assertThrows(UnsupportedOperationException.class, () -> selected.observe(
                new ObserveTaskParams("task-cursor", 0L, null), SpiCallOptions.defaults(), null));
        assertEquals(0, requests.size());
        selected.close();
    }

    @Test
    void selectedOneZeroObserveIsUnsupportedBeforeAnyRequestAndKeepsTheCallbackSubscription() throws Exception {
        JSONRPCTransport selected = new JSONRPCTransport(serverUrl(), "1.0");
        assertThrows(UnsupportedOperationException.class, () -> selected.observe(
                new ObserveTaskParams("task-legacy", null, null), SpiCallOptions.defaults(), null));
        assertEquals(0, requests.size());
        selected.close();
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static CountDownLatch collect(TransportObservation observation, List<TransportFrame> frames) {
        CountDownLatch completed = new CountDownLatch(1);
        observation.frames().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(TransportFrame frame) { frames.add(frame); }
            @Override public void onError(Throwable failure) { completed.countDown(); }
            @Override public void onComplete() { completed.countDown(); }
        });
        return completed;
    }

    private String serverUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        JsonObject body = JsonParser.parseString(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        requests.add(body.deepCopy());
        lastHeaders = exchange.getRequestHeaders();
        lastPath = exchange.getRequestURI().getPath();
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        CountDownLatch held = heldOpen;
        if (held != null) {
            exchange.sendResponseHeaders(200, 0);
            OutputStream output = exchange.getResponseBody();
            try {
                output.write(": open\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                held.countDown();
                while (!released.await(50, TimeUnit.MILLISECONDS)) {
                    output.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
            } catch (IOException disconnected) {
                peerSawDisconnect.countDown();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
            return;
        }
        byte[] reply = sseReply.apply(body).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, reply.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(reply);
        }
    }

    private static String sseBody(JsonObject requestBody, String... envelopes) {
        StringBuilder body = new StringBuilder();
        for (String envelope : envelopes) {
            JsonObject parsed = JsonParser.parseString(envelope).getAsJsonObject();
            parsed.add("id", requestBody.get("id").deepCopy());
            body.append("data: ").append(parsed).append("\n\n");
        }
        return body.toString();
    }

    private static String eventEnvelope(String variant, JsonObject payload) {
        JsonObject result = new JsonObject();
        result.add(variant, payload);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        envelope.addProperty("id", "response-1");
        envelope.add("result", result);
        return envelope.toString();
    }

    private static JsonObject json(Task task) throws org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException {
        return JsonParser.parseString(JsonUtil.toJson(task)).getAsJsonObject();
    }

    private static JsonObject status(String taskId, String contextId) {
        JsonObject update = new JsonObject();
        update.addProperty("taskId", taskId);
        update.addProperty("contextId", contextId);
        update.add("status", JsonParser.parseString("{\"state\":\"TASK_STATE_WORKING\"}"));
        return update;
    }

    private static JsonObject messageUpdate(String taskId, String contextId, long generation, String messageId)
            throws org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException {
        JsonObject update = new JsonObject();
        update.addProperty("taskId", taskId);
        update.addProperty("contextId", contextId);
        update.add("message", JsonParser.parseString(JsonUtil.toJson(message(taskId, contextId, messageId))));
        update.addProperty("generation", Long.toString(generation));
        return update;
    }

    private static Message message(String taskId, String contextId, String messageId) {
        return Message.builder().messageId(messageId).taskId(taskId).contextId(contextId)
                .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("payload-" + messageId))).build();
    }

    private static Task task(String id, String contextId, long generation) {
        List<TimelineEntry> timeline = generation == 0 ? List.of()
                : List.of(new TimelineEntry(generation,
                        new TimelineEntry.MessageValue(message(id, contextId, id + "-m" + generation))));
        return new Task(id, contextId, new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(), List.of(), Map.of(),
                generation, timeline);
    }
}
