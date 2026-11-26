package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * P3-T17 public {@link Client} matrix against the real {@link JSONRPCTransport} and a controlled JSON-RPC peer.
 * <p>
 * The peer is the only controlled component: every assertion crosses the public client API, the real transport and
 * the real HTTP/SSE decoding. Ingress is counted at the peer so automatic retry or rebase would be visible.
 */
class Phase3JSONRPCPublicClientTest {

    private static final String TASK = "task-p3";
    private static final String CTX = "ctx-p3";

    private enum Route {
        SEND_UNARY, SEND_STREAMING, SUBSCRIBE_NO_CURSOR;

        boolean streaming() {
            return this != SEND_UNARY;
        }
    }

    private enum Cause {
        ZERO, EQUAL
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void zeroAndEqualGenerationsFromEveryEntryPointDisableGuardedInputBeforeIo(Route route) throws Exception {
        for (Cause cause : Cause.values()) {
            try (Peer peer = new Peer(requestBody -> route.streaming()
                    ? Reply.sse(taintFrames(cause, requestBody))
                    : Reply.json(taintUnary(cause, requestBody)))) {
                Client client = client(peer.baseUrl(), "1.1", route.streaming());
                List<ClientEvent> events = new CopyOnWriteArrayList<>();
                int repeat = !route.streaming() && cause == Cause.EQUAL ? 2 : 1;
                for (int i = 0; i < repeat; i++) {
                    runEntry(client, route, events);
                }
                int before = peer.requests.size();
                assertTrue(before >= 1, route + "/" + cause + " must have reached the peer");

                // the guard is refused through every public input API, each time before any I/O
                SendInputParams guarded = guarded(1L);
                A2AClientException rejected = assertThrows(A2AClientException.class,
                        () -> client.sendInput(guarded, List.of(), null, null));
                assertTrue(rejected.getMessage().contains("not reliable"), rejected.getMessage());
                A2AClientException observed = assertThrows(A2AClientException.class,
                        () -> client.sendInputObserved(guarded, null));
                assertTrue(observed.getMessage().contains("not reliable"), observed.getMessage());
                MessageSendParams legacy = MessageSendParams.builder().message(guarded.message())
                        .configuration(guarded.configuration()).build();
                A2AClientException viaSendMessage = assertThrows(A2AClientException.class,
                        () -> client.sendMessage(legacy, List.of(), error -> { }, null));
                assertTrue(viaSendMessage.getMessage().contains("not reliable"), viaSendMessage.getMessage());
                assertEquals(before, peer.requests.size(), route + "/" + cause + ": guard must stop before I/O");

                // The guard is never dropped or rewritten: unguarded input is still allowed and counts as one request.
                client.sendInput(SendInputParams.builder().message(userMessage("unguarded")).build(), List.of(),
                        error -> { }, null);
                assertTrue(peer.awaitRequests(before + 1), route + "/" + cause + ": unguarded input must be sent");
                client.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void strictlyIncreasingGenerationsKeepTheGuardUsable(Route route) throws Exception {
        try (Peer peer = new Peer(requestBody -> route.streaming()
                ? Reply.sse(increasingFrames(requestBody))
                : Reply.json(unaryResult(requestBody, task(3))))) {
            Client client = client(peer.baseUrl(), "1.1", route.streaming());
            runEntry(client, route, new CopyOnWriteArrayList<>());
            int before = peer.requests.size();

            client.sendInput(guarded(3L), List.of(), error -> { }, null);

            assertTrue(peer.awaitRequests(before + 1));
            JsonObject sent = peer.requests.get(before);
            assertEquals("3", sent.getAsJsonObject("params").getAsJsonObject("configuration")
                    .get("ifGenerationMatch").getAsString());
            client.close();
        }
    }

    @ParameterizedTest
    @EnumSource(value = Route.class, names = {"SEND_STREAMING", "SUBSCRIBE_NO_CURSOR"})
    void streamingSendAndNoCursorObserveReduceTheSameStampsTimelineAndMessageCounts(Route route) throws Exception {
        Message first = agentMessage("m1");
        Message second = agentMessage("m2");
        try (Peer peer = new Peer(requestBody -> Reply.sse(frames(requestBody,
                eventEnvelope("task", task(1)),
                eventEnvelope("messageUpdate", new TaskMessageUpdateEvent(TASK, CTX, first, null, 2)),
                // metadata-only status: no message, so the message occurrence count must stay 1
                eventEnvelope("statusUpdate", new TaskStatusUpdateEvent(TASK,
                        new TaskStatus(TaskState.TASK_STATE_WORKING), CTX, Map.of("k", "v"), 3)),
                eventEnvelope("messageUpdate", new TaskMessageUpdateEvent(TASK, CTX, second, null, 4)))))) {
            Client client = client(peer.baseUrl(), "1.1", true);
            List<ClientEvent> events = new CopyOnWriteArrayList<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            runEntry(client, route, events, failure);

            assertEquals(null, failure.get());
            Task last = lastTask(events);
            assertEquals(4, last.generation());
            assertEquals(List.of(2L, 3L, 4L), last.timeline().stream().map(TimelineEntry::generation).toList());
            assertEquals(List.of("m1", "m2"), last.history().stream().map(Message::messageId).toList());
            assertEquals(1, peer.requests.size(), "exactly one ingress, no automatic retry or rebase");
            if (route == Route.SUBSCRIBE_NO_CURSOR) {
                JsonObject request = peer.requests.get(0);
                assertEquals("SubscribeToTask", request.get("method").getAsString());
                assertFalse(request.getAsJsonObject("params").has("cursor"));
                assertFalse(request.getAsJsonObject("params").has("currentGeneration"));
                assertEquals("1.1", peer.versions.get(0));
            }
            client.close();
        }
    }

    @Test
    void unknownStreamVariantRetainsUncertaintyAndCancellationDetachesOnlyTheObserver() throws Exception {
        try (Peer peer = new Peer(requestBody -> Reply.sse(frames(requestBody,
                eventEnvelope("task", task(1)),
                "{\"futureVariant\":{}}",
                eventEnvelope("messageUpdate", new TaskMessageUpdateEvent(TASK, CTX, agentMessage("m3"), null, 3)))))) {
            Client client = client(peer.baseUrl(), "1.1", true);
            TaskObservation observation = client.sendInputObserved(
                    SendInputParams.builder().message(userMessage("observed")).build(), null);
            ObservationEnd end = observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(ObservationEnd.Kind.COMPLETED, end.kind());
            TaskView view = end.finalView();
            assertEquals(ViewSync.INCOMPLETE, view.sync(), "unknown variant must keep the view uncertain: " + view);
            assertEquals(1L, view.throughGeneration(), "the safely interpreted generation must not pass the unknown frame: " + view);
            assertEquals(1, peer.requests.size());

            observation.cancel();
            assertEquals(ObservationEnd.Kind.COMPLETED, observation.completion().toCompletableFuture().get().kind(),
                    "cancel after the outcome never changes it");
            assertEquals(1, peer.requests.size(), "cancelling never sends a task cancel");
            client.close();
        }
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void malformedKnownPayloadsFailThroughEveryPublicEntryPointAttributedToTheMalformedValue(Route route)
            throws Exception {
        // A complete frame whose generation is not an int64: the one defect, attributed by the decoder to its value.
        String malformed = statusUpdateJson("not-a-number");
        String wellFormed = statusUpdateJson("2");
        try (Peer peer = new Peer(requestBody -> route.streaming()
                ? Reply.sse(frames(requestBody, eventEnvelope("task", task(1)), malformed))
                : Reply.json(unaryResultJson(requestBody, taskJsonWithGeneration("not-a-number"))))) {
            Client client = client(peer.baseUrl(), "1.1", route.streaming());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            runEntry(client, route, new CopyOnWriteArrayList<>(), failure);
            assertTrue(failure.get() != null, route + ": malformed known payload must fail");
            // The decoder reports known-content corruption as such and names the offending int64 value; it cannot
            // name the JSON field, so the well-formed control below is what pins the cause to the generation.
            String cause = chain(failure.get());
            assertTrue(cause.contains("Malformed known protocol content") && cause.contains("not-a-number"),
                    route + ": the failure must be the malformed-known-content error naming the bad value: " + cause);
            assertEquals(1, peer.requests.size());
            client.close();
        }
        // control: the same frame with a valid generation is accepted, so the failure above is the generation's
        try (Peer peer = new Peer(requestBody -> route.streaming()
                ? Reply.sse(frames(requestBody, eventEnvelope("task", task(1)), wellFormed))
                : Reply.json(unaryResultJson(requestBody, taskJsonWithGeneration("2"))))) {
            Client client = client(peer.baseUrl(), "1.1", route.streaming());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            runEntry(client, route, new CopyOnWriteArrayList<>(), failure);
            assertEquals(null, failure.get(), route + ": the well-formed control must succeed");
            client.close();
        }
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void errorEnvelopesStayTypedThroughEveryPublicEntryPoint(Route route) throws Exception {
        try (Peer peer = new Peer(requestBody -> route.streaming()
                ? Reply.sse(errorFrame(requestBody)) : Reply.json(errorEnvelope(requestBody)))) {
            Client client = client(peer.baseUrl(), "1.1", route.streaming());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            runEntry(client, route, new CopyOnWriteArrayList<>(), failure);
            Throwable cause = failure.get();
            assertTrue(cause != null, route + ": an error envelope must fail the call");
            assertTrue(chainContains(cause, TaskNotFoundError.class), route + ": error envelope must stay typed: " + chain(cause));
            assertEquals(1, peer.requests.size());
            client.close();
        }
    }

    @Test
    void unknownTimelineVariantInASnapshotKeepsTheViewUncertainAndNeverFabricatesAGap() throws Exception {
        JsonObject snapshot = toJson(task(3));
        JsonArray timeline = new JsonArray();
        JsonObject known = new JsonObject();
        known.addProperty("generation", 3);
        known.add("message", toJsonObject(agentMessage("m-known")));
        JsonObject unknown = new JsonObject();
        unknown.addProperty("generation", 2);
        unknown.add("futureTimelineKind", new JsonObject());
        timeline.add(unknown);
        timeline.add(known);
        snapshot.add("timeline", timeline);
        try (Peer peer = new Peer(requestBody -> Reply.sse(frames(requestBody, eventEnvelope("task", task(1)),
                "{\"task\":" + snapshot + "}")))) {
            Client client = client(peer.baseUrl(), "1.1", true);
            TaskObservation observation = client.sendInputObserved(
                    SendInputParams.builder().message(userMessage("observed")).build(), null);
            ObservationEnd end = observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(ObservationEnd.Kind.COMPLETED, end.kind());
            TaskView view = end.finalView();
            assertEquals(ViewSync.INCOMPLETE, view.sync(), "an uninterpreted timeline entry keeps the view uncertain: " + view);
            assertTrue(view.timeline().flags().contains(HistoryCoverage.Flag.UNKNOWN_SKIPPED), String.valueOf(view));
            assertFalse(view.timeline().flags().contains(HistoryCoverage.Flag.NOT_ASSERTED),
                    "the skipped entry's generation is known, so no gap is fabricated: " + view);
            assertEquals(1, peer.requests.size());
            client.close();
        }
    }

    @Test
    void cancellingALiveUncertainStreamDetachesOnlyTheObserverAndSendsNoTaskCancel() throws Exception {
        try (Peer peer = new Peer(requestBody -> Reply.heldOpenSse(frames(requestBody,
                eventEnvelope("task", task(1)), "{\"futureVariant\":{}}")))) {
            Client client = client(peer.baseUrl(), "1.1", true);
            TaskObservation observation = client.sendInputObserved(
                    SendInputParams.builder().message(userMessage("live")).build(), null);
            for (int i = 0; i < 100 && observation.currentView().sync() != ViewSync.INCOMPLETE; i++) {
                Thread.sleep(50);
            }
            assertEquals(ViewSync.INCOMPLETE, observation.currentView().sync(), "the unknown frame arrived live");
            assertFalse(observation.completion().toCompletableFuture().isDone(), "the stream is still open");

            observation.cancel();

            ObservationEnd end = observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(ObservationEnd.Kind.CANCELLED, end.kind());
            assertEquals(ViewSync.INCOMPLETE, end.finalView().sync(), "cancellation keeps the retained uncertainty");
            assertEquals(1L, end.finalView().throughGeneration());
            Thread.sleep(300);
            assertEquals(1, peer.requests.size(), "cancelling the observer never sends a task cancel or any request");
            assertEquals("SendStreamingMessage", peer.requests.get(0).get("method").getAsString());
            client.close();
        }
    }

    @Test
    void selected10AdvancedInputsAreRejectedLocallyAndObserveStaysUnavailable() throws Exception {
        try (Peer peer = new Peer(requestBody -> Reply.json(unaryResult(requestBody, task(1))))) {
            Client client = client(peer.baseUrl(), "1.0", false);
            assertThrows(A2AClientException.class, () -> client.sendInput(guarded(1L), List.of(), null, null));
            assertThrows(A2AClientException.class, () -> client.sendInput(SendInputParams.builder()
                    .message(userMessage("timeline"))
                    .configuration(MessageSendConfiguration.builder().timelineLength(2).build()).build(),
                    List.of(), null, null));
            assertThrows(A2AClientException.class, () -> client.sendInputObserved(
                    SendInputParams.builder().message(userMessage("observed")).build(), null));
            assertEquals(0, peer.requests.size(), "selected-1.0 advanced input never reaches the peer");
            client.close();
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private static void runEntry(Client client, Route route, List<ClientEvent> events) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runEntry(client, route, events, failure);
        if (failure.get() instanceof A2AClientException synchronous) {
            throw synchronous;
        }
    }

    private static void runEntry(Client client, Route route, List<ClientEvent> events,
            AtomicReference<Throwable> failure) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<java.util.function.BiConsumer<ClientEvent, AgentCard>> consumers =
                List.of((event, card) -> events.add(event));
        java.util.function.Consumer<Throwable> errors = error -> {
            if (error != null && !(error instanceof java.util.concurrent.CancellationException)) {
                failure.compareAndSet(null, error);
            }
            done.countDown();
        };
        try {
            if (route == Route.SUBSCRIBE_NO_CURSOR) {
                client.subscribeToTask(new TaskIdParams(TASK), consumers, errors, null);
            } else {
                client.sendMessage(userMessage("send"), consumers, errors, null);
            }
        } catch (A2AClientException synchronous) {
            // a unary send reports its decode or server error by throwing instead of through the error callback
            failure.compareAndSet(null, synchronous);
            return;
        }
        // A finite SSE reply ends the stream cleanly without an error callback: wait for quiescence.
        awaitQuiescent(events, done);
    }

    private static void awaitQuiescent(List<ClientEvent> events, CountDownLatch done) throws InterruptedException {
        done.await(1, TimeUnit.SECONDS);
        int size;
        do {
            size = events.size();
            Thread.sleep(100);
        } while (size != events.size());
    }

    private static Task lastTask(List<ClientEvent> events) {
        Task last = null;
        for (ClientEvent event : events) {
            if (event instanceof TaskEvent taskEvent) {
                last = taskEvent.getTask();
            } else if (event instanceof TaskUpdateEvent update) {
                last = update.getTask();
            }
        }
        assertTrue(last != null, "a task view must have been delivered: " + events);
        return last;
    }

    private static SendInputParams guarded(long generation) {
        return SendInputParams.builder().message(Message.builder().messageId("guarded-" + generation)
                        .taskId(TASK).contextId(CTX).role(Message.Role.ROLE_USER).parts(List.of(new TextPart("go")))
                        .build())
                .configuration(MessageSendConfiguration.builder().ifGenerationMatch(generation).build()).build();
    }

    private static Message userMessage(String id) {
        return Message.builder().messageId(id).taskId(TASK).contextId(CTX).role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(id))).build();
    }

    private static Message agentMessage(String id) {
        return Message.builder().messageId(id).taskId(TASK).contextId(CTX).role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart(id))).build();
    }

    private static Task task(long generation) {
        return new Task(TASK, CTX, new TaskStatus(TaskState.TASK_STATE_WORKING), List.<Artifact>of(),
                List.<Message>of(), Map.of(), generation, List.<TimelineEntry>of());
    }

    private static String taintUnary(Cause cause, JsonObject requestBody) {
        return unaryResult(requestBody, task(cause == Cause.ZERO ? 0 : 3));
    }

    private static String taintFrames(Cause cause, JsonObject requestBody) {
        if (cause == Cause.ZERO) {
            return frames(requestBody, eventEnvelope("task", task(0)));
        }
        return frames(requestBody, eventEnvelope("task", task(3)),
                eventEnvelope("statusUpdate", new TaskStatusUpdateEvent(TASK,
                        new TaskStatus(TaskState.TASK_STATE_WORKING), CTX, null, 4)),
                eventEnvelope("statusUpdate", new TaskStatusUpdateEvent(TASK,
                        new TaskStatus(TaskState.TASK_STATE_WORKING), CTX, null, 4)));
    }

    private static String increasingFrames(JsonObject requestBody) {
        return frames(requestBody, eventEnvelope("task", task(1)),
                eventEnvelope("statusUpdate", new TaskStatusUpdateEvent(TASK,
                        new TaskStatus(TaskState.TASK_STATE_WORKING), CTX, null, 2)),
                eventEnvelope("messageUpdate", new TaskMessageUpdateEvent(TASK, CTX, agentMessage("inc"), null, 3)));
    }

    private static String unaryResult(JsonObject requestBody, StreamingEventKind event) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        envelope.add("id", requestBody.get("id").deepCopy());
        JsonObject result = new JsonObject();
        result.add("task", toJson(event));
        envelope.add("result", result);
        return envelope.toString();
    }

    /** One decoded JSON-RPC result object holding exactly one stream variant. */
    private static String eventEnvelope(String variant, StreamingEventKind event) {
        return "{\"" + variant + "\":" + json(event) + "}";
    }

    private static JsonObject toJson(StreamingEventKind event) {
        return JsonParser.parseString(json(event)).getAsJsonObject();
    }

    private static String json(StreamingEventKind event) {
        try {
            return JsonUtil.toJson(event);
        } catch (org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String frames(JsonObject requestBody, String... results) {
        StringBuilder body = new StringBuilder();
        for (String result : results) {
            JsonObject envelope = new JsonObject();
            envelope.addProperty("jsonrpc", "2.0");
            envelope.add("id", requestBody.get("id").deepCopy());
            envelope.add("result", JsonParser.parseString(result));
            body.append("data: ").append(envelope).append("\n\n");
        }
        return body.toString();
    }

    private static String statusUpdateJson(String generation) {
        return "{\"statusUpdate\":{\"taskId\":\"" + TASK + "\",\"contextId\":\"" + CTX
                + "\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},\"generation\":\"" + generation + "\"}}";
    }

    private static JsonObject taskJsonWithGeneration(String generation) {
        JsonObject task = toJson(task(1));
        task.addProperty("generation", generation);
        return task;
    }

    private static String unaryResultJson(JsonObject requestBody, JsonObject task) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        envelope.add("id", requestBody.get("id").deepCopy());
        JsonObject result = new JsonObject();
        result.add("task", task);
        envelope.add("result", result);
        return envelope.toString();
    }

    private static JsonObject toJsonObject(Message message) {
        return JsonParser.parseString(json(message)).getAsJsonObject();
    }

    private static String chain(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            text.append(cause.getClass().getSimpleName()).append(':').append(cause.getMessage()).append(" <- ");
        }
        return text.toString();
    }

    private static boolean chainContains(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static String errorEnvelope(JsonObject requestBody) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        envelope.add("id", requestBody.get("id").deepCopy());
        JsonObject error = new JsonObject();
        error.addProperty("code", -32001);
        error.addProperty("message", "task not found");
        envelope.add("error", error);
        return envelope.toString();
    }

    private static String errorFrame(JsonObject requestBody) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        envelope.add("id", requestBody.get("id").deepCopy());
        JsonObject error = new JsonObject();
        error.addProperty("code", -32001);
        error.addProperty("message", "task not found");
        envelope.add("error", error);
        return "data: " + envelope + "\n\n";
    }

    private static Client client(String url, String version, boolean streaming) throws Exception {
        AgentCard card = AgentCard.builder().name("test").description("test").version("1")
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", url, null, version)))
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
        return Client.builder(card)
                .clientConfig(new ClientConfig.Builder().setProtocolVersion(version).setStreaming(streaming).build())
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig(new JdkA2AHttpClient()))
                .build();
    }

    private record Reply(boolean sse, String body, boolean hold) {
        static Reply sse(String body) {
            return new Reply(true, body, false);
        }

        /** An event stream that delivers its frames and then stays open until the peer is closed. */
        static Reply heldOpenSse(String body) {
            return new Reply(true, body, true);
        }

        static Reply json(String body) {
            return new Reply(false, body, false);
        }
    }

    private static final class Peer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final Function<JsonObject, Reply> replies;
        private final CountDownLatch released = new CountDownLatch(1);
        final List<JsonObject> requests = new CopyOnWriteArrayList<>();
        final List<String> versions = new CopyOnWriteArrayList<>();

        Peer(Function<JsonObject, Reply> replies) throws IOException {
            this.replies = replies;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        boolean awaitRequests(int count) throws InterruptedException {
            for (int i = 0; i < 100 && requests.size() < count; i++) {
                Thread.sleep(50);
            }
            return requests.size() >= count;
        }

        private void handle(HttpExchange exchange) {
            try (exchange) {
                JsonObject requestBody = JsonParser.parseString(
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                versions.add(String.valueOf(exchange.getRequestHeaders().getFirst("A2A-Version")));
                requests.add(requestBody);
                Reply reply = replies.apply(requestBody);
                byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", reply.sse() ? "text/event-stream" : "application/json");
                exchange.sendResponseHeaders(200, reply.hold() ? 0 : body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                    if (reply.hold()) {
                        output.flush();
                        released.await(30, TimeUnit.SECONDS);
                    }
                }
            } catch (IOException ignored) {
                // A client detaching its read ends the controlled request.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            released.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
