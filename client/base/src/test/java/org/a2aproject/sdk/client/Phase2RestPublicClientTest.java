package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.VerificationTimes.exactly;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.util.JsonFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfig;
import org.a2aproject.sdk.client.transport.rest.RestTransportProvider;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;

class Phase2RestPublicClientTest {

    private ClientAndServer server;

    @BeforeEach
    void setUp() {
        server = new ClientAndServer(4001);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void explicitAndDefaultOneOnePublicGetAndListSkipUnknownTimelineEntries() throws Exception {
        JsonObject taskA = taskJson("task-A", 7, 9);
        addUnknown(taskA, 8);
        JsonObject taskB = taskJson("task-B", 17, 19);
        addUnknown(taskB, 18);
        JsonObject list = new JsonObject();
        JsonArray tasks = new JsonArray();
        tasks.add(taskA);
        tasks.add(taskB);
        list.add("tasks", tasks);
        list.addProperty("totalSize", 12);
        list.addProperty("pageSize", 2);
        list.addProperty("nextPageToken", "page-2");
        respond("/tasks/task-A", taskA);
        respond("/tasks", list);

        Client explicit = client(card("1.1"), "1.1", false);
        Client defaultClient = client(card("1.1"), null, false);
        assertEquals("1.1", explicit.getProtocolVersion());
        assertEquals("1.1", defaultClient.getProtocolVersion());
        assertEquals(Map.of("1.0", java.util.Set.of(ClientFeature.ORDINARY),
                        "1.1", java.util.Set.of(ClientFeature.ORDINARY, ClientFeature.GUARDED_INPUT,
                                ClientFeature.ARTIFACT_INPUT, ClientFeature.UNKNOWN_VARIANT_SKIP)),
                new RestTransportProvider().capabilities());
        assertEquals(java.util.Set.of(ClientFeature.ORDINARY, ClientFeature.GUARDED_INPUT,
                        ClientFeature.ARTIFACT_INPUT, ClientFeature.UNKNOWN_VARIANT_SKIP), capabilities(explicit));
        assertEquals(List.of(7L, 9L), generations(explicit.getTask(new TaskQueryParams("task-A"), null)));
        var listed = defaultClient.listTasks(ListTasksParams.builder().pageSize(2).build(), null);
        assertEquals(List.of("task-A", "task-B"), listed.tasks().stream().map(Task::id).toList());
        assertEquals(List.of(17L, 19L), generations(listed.tasks().get(1)));
        assertEquals(12, listed.totalSize());
        assertEquals(2, listed.pageSize());
        assertEquals("page-2", listed.nextPageToken());
        server.verify(request().withMethod("GET").withPath("/tasks/task-A"), exactly(1));
        server.verify(request().withMethod("GET").withPath("/tasks"), exactly(1));
        explicit.close();
        defaultClient.close();
    }

    @Test
    void explicitOneZeroPreflightRejectsGuardBeforeAnyHttpRequest() throws Exception {
        Client client = client(card("1.0"), "1.0", false);
        SendInputParams guarded = SendInputParams.builder().message(message("task-v10", 1))
                .configuration(MessageSendConfiguration.builder().ifGenerationMatch(1L).build()).build();
        assertThrows(org.a2aproject.sdk.spec.A2AClientException.class, () -> client.sendInput(guarded));
        assertEquals("1.0", client.getProtocolVersion());
        assertEquals(java.util.Set.of(ClientFeature.ORDINARY), capabilities(client));
        server.verify(request(), exactly(0));
        client.close();
    }

    @Test
    void publicUnaryInputDispatchesTaskAndDirectMessageAndEncodesGuardArtifactTenantAndVersion() throws Exception {
        JsonObject taskResponse = new JsonObject();
        taskResponse.add("task", taskJson("task-unary", 1, 2));
        server.when(request().withMethod("POST").withPath("/tenant-a/message:send"))
                .respond(response().withStatusCode(200).withBody(taskResponse.toString()));
        ClientCallInterceptor interceptor = new ClientCallInterceptor() {
            @Override
            public PayloadAndHeaders intercept(String method, @Nullable Object payload, Map<String, String> headers,
                    @Nullable AgentCard agentCard, @Nullable ClientCallContext context) {
                return new PayloadAndHeaders(payload, Map.of("X-Phase2", "public"));
            }
        };
        Client client = client(card("1.1"), "1.1", false, List.of(interceptor));
        TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent("task-unary", new Artifact(
                "artifact-in", "name", "description", List.of(new TextPart("payload")), Map.of(), List.of()),
                "context-task-unary", true, false, Map.of("origin", "client"), 5);
        SendInputParams input = SendInputParams.builder().message(message("task-unary", 4))
                .artifactUpdates(List.of(artifactUpdate))
                .configuration(MessageSendConfiguration.builder().ifGenerationMatch(4L).timelineLength(3).build())
                .metadata(Map.of("trace", "public-trace")).tenant("tenant-a").build();
        List<ClientEvent> events = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        client.sendInput(input, List.of((event, agentCard) -> events.add(event)), error -> {
            failure.set(error);
            done.countDown();
        }, null);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());
        assertInstanceOf(TaskEvent.class, events.get(0));
        var recorded = server.retrieveRecordedRequests(request().withMethod("POST").withPath("/tenant-a/message:send"));
        assertEquals(1, recorded.length);
        JsonObject wire = JsonParser.parseString(recorded[0].getBodyAsString()).getAsJsonObject();
        assertEquals("task-unary-m4", wire.getAsJsonObject("message").get("messageId").getAsString());
        assertEquals("4", wire.getAsJsonObject("configuration").get("ifGenerationMatch").getAsString());
        assertEquals(3, wire.getAsJsonObject("configuration").get("timelineLength").getAsInt());
        assertEquals("artifact-in", wire.getAsJsonArray("artifactUpdates").get(0).getAsJsonObject()
                .getAsJsonObject("artifact").get("artifactId").getAsString());
        assertEquals("public-trace", wire.getAsJsonObject("metadata").get("trace").getAsString());
        server.verify(request().withMethod("POST").withPath("/tenant-a/message:send")
                .withHeader("A2A-Version", "1.1").withHeader("X-Phase2", "public"), exactly(1));

        server.clear(request().withMethod("POST").withPath("/tenant-a/message:send"));
        JsonObject direct = new JsonObject();
        direct.add("message", JsonParser.parseString(JsonUtil.toJson(message("", 6))));
        server.when(request().withMethod("POST").withPath("/message:send"))
                .respond(response().withStatusCode(200).withBody(direct.toString()));
        CountDownLatch messageDone = new CountDownLatch(1);
        client.sendInput(SendInputParams.builder().message(message("", 6)).build(), List.of((event, agentCard) -> {
            events.add(event);
        }), error -> {
            failure.set(error);
            messageDone.countDown();
        }, null);
        assertTrue(messageDone.await(5, TimeUnit.SECONDS));
        assertInstanceOf(MessageEvent.class, events.get(1));
        client.close();
    }

    @Test
    void publicHttp409IsTypedAndNotRetriedAndMalformedKnownTaskCallsOneError() throws Exception {
        String mismatch = "{\"error\":{\"code\":409,\"message\":\"generation mismatch\",\"details\":["
                + "{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\","
                + "\"reason\":\"TASK_GENERATION_MISMATCH\",\"metadata\":{\"taskId\":\"task-guarded\"}}]}}";
        server.when(request().withMethod("POST").withPath("/message:send"))
                .respond(response().withStatusCode(409).withBody(mismatch));
        Client client = client(card("1.1"), "1.1", false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger errors = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        client.sendInput(SendInputParams.builder().message(message("task-guarded", 1))
                .configuration(MessageSendConfiguration.builder().ifGenerationMatch(1L).build()).build(), List.of(),
                error -> {
                    failure.set(error);
                    errors.incrementAndGet();
                    done.countDown();
                }, null);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(1, errors.get());
        assertInstanceOf(TaskGenerationMismatchError.class, failure.get().getCause());
        server.verify(request().withMethod("POST").withPath("/message:send"), exactly(1));

        server.clear(request().withMethod("POST").withPath("/message:send"));
        JsonObject malformed = taskJson("task-bad", 7, 9);
        JsonObject badEntry = new JsonObject();
        badEntry.addProperty("generation", "8");
        badEntry.add("message", new JsonObject());
        malformed.getAsJsonArray("timeline").set(1, badEntry);
        JsonObject body = new JsonObject();
        body.add("task", malformed);
        server.when(request().withMethod("POST").withPath("/message:send"))
                .respond(response().withStatusCode(200).withBody(body.toString()));
        errors.set(0);
        failure.set(null);
        CountDownLatch malformedDone = new CountDownLatch(1);
        client.sendInput(SendInputParams.builder().message(message("task-bad", 7)).build(), List.of(), error -> {
            failure.set(error);
            errors.incrementAndGet();
            malformedDone.countDown();
        }, null);
        assertTrue(malformedDone.await(5, TimeUnit.SECONDS));
        assertEquals(1, errors.get());
        assertTrue(failure.get() != null);
        server.verify(request().withMethod("POST").withPath("/message:send"), exactly(1));
        client.close();
    }

    @Test
    void publicRestStreamKeepsBaselineTypedMessageFutureSkipStatusArtifactAndSingleTerminal() throws Exception {
        Task baselineTask = new Task("task-stream", "context-task-stream",
                new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(), List.of(message("task-stream", 0)),
                Map.of("base", "kept"), 1, List.of(new TimelineEntry(1,
                        new TimelineEntry.MessageValue(message("task-stream", 1)))));
        org.a2aproject.sdk.spec.TaskMessageUpdateEvent messageUpdate = new org.a2aproject.sdk.spec.TaskMessageUpdateEvent(
                "task-stream", "context-task-stream", message("task-stream", 2), Map.of("message", "kept"), 2);
        TaskStatusUpdateEvent statusUpdate = new TaskStatusUpdateEvent("task-stream",
                new TaskStatus(TaskState.TASK_STATE_WORKING), "context-task-stream", Map.of("status", "kept"), 3);
        TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent("task-stream",
                new Artifact("artifact-out", "out", "result", List.of(new TextPart("output")),
                        Map.of("artifact", "kept"), List.of()), "context-task-stream", false, true,
                Map.of("event", "kept"), 4);
        String baseline = JsonFormat.printer().omittingInsignificantWhitespace().print(
                org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto.taskOrMessageStream(baselineTask));
        String messageFrame = JsonFormat.printer().omittingInsignificantWhitespace().print(
                org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto.taskOrMessageStream(messageUpdate));
        String statusFrame = JsonFormat.printer().omittingInsignificantWhitespace().print(
                org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto.taskOrMessageStream(statusUpdate));
        String artifactFrame = JsonFormat.printer().omittingInsignificantWhitespace().print(
                org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto.taskOrMessageStream(artifactUpdate));
        String terminalFrame = JsonFormat.printer().omittingInsignificantWhitespace().print(
                org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto.taskOrMessageStream(new TaskStatusUpdateEvent(
                        "task-stream", new TaskStatus(TaskState.TASK_STATE_COMPLETED), "context-task-stream",
                        Map.of("terminal", "kept"), 5)));
        server.when(request().withMethod("POST").withPath("/tenant-stream/message:stream"))
                .respond(response().withStatusCode(200).withHeader("Content-Type", "text/event-stream")
                        .withBody(sse(baseline, messageFrame, "{\"futureVariant\":{}}", statusFrame,
                                artifactFrame, terminalFrame)));
        Client client = client(card("1.1"), "1.1", true);
        List<ClientEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger terminals = new AtomicInteger();
        AtomicReference<Throwable> terminal = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        client.sendInput(SendInputParams.builder().message(message("task-stream", 1))
                .configuration(MessageSendConfiguration.builder().ifGenerationMatch(1L).timelineLength(2).build())
                .tenant("tenant-stream").build(), List.of((event, agentCard) -> events.add(event)), error -> {
                    terminal.set(error);
                    terminals.incrementAndGet();
                    done.countDown();
                }, null);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNull(terminal.get());
        assertEquals(1, terminals.get());
        assertEquals(5, events.size());
        assertInstanceOf(TaskEvent.class, events.get(0));
        var typedMessage = assertInstanceOf(TaskUpdateEvent.class, events.get(1));
        assertInstanceOf(TaskMessageUpdateEvent.class, typedMessage.getUpdateEvent());
        assertEquals("kept", typedMessage.getTask().metadata().get("base"));
        assertEquals("task-stream-m2", ((TaskMessageUpdateEvent) typedMessage.getUpdateEvent()).message().messageId());
        var statusEvent = assertInstanceOf(TaskUpdateEvent.class, events.get(2));
        assertEquals("kept", statusEvent.getTask().metadata().get("status"));
        var artifactEvent = assertInstanceOf(TaskUpdateEvent.class, events.get(3));
        assertEquals("artifact-out", ((TaskArtifactUpdateEvent) artifactEvent.getUpdateEvent()).artifact().artifactId());
        var terminalEvent = assertInstanceOf(TaskUpdateEvent.class, events.get(4));
        assertEquals(TaskState.TASK_STATE_COMPLETED,
                ((TaskStatusUpdateEvent) terminalEvent.getUpdateEvent()).status().state());
        assertEquals("kept", terminalEvent.getTask().metadata().get("base"));
        server.verify(request().withMethod("POST").withPath("/tenant-stream/message:stream")
                .withHeader("A2A-Version", "1.1"), exactly(1));
        client.close();
    }

    @Test
    void closingOnePublicClientKeepsSharedInjectedRestHttpClientAndOtherStreamAlive() throws Exception {
        try (HeldOpenRestPeer peer = new HeldOpenRestPeer()) {
            var shared = new JdkA2AHttpClient();
            AgentCard card = card("1.1", peer.baseUrl());
            Client first = client(card, "1.1", true, shared);
            Client second = client(card, "1.1", true, shared);
            AtomicReference<Throwable> firstFailure = new AtomicReference<>();
            AtomicReference<Throwable> secondFailure = new AtomicReference<>();
            AtomicInteger firstTerminals = new AtomicInteger();
            AtomicInteger secondTerminals = new AtomicInteger();
            AtomicInteger secondEvents = new AtomicInteger();
            CountDownLatch firstDone = new CountDownLatch(1);
            CountDownLatch secondDone = new CountDownLatch(1);
            first.sendInput(SendInputParams.builder().message(message("", 0)).tenant("one").build(), List.of(),
                    error -> {
                        firstFailure.set(error);
                        firstTerminals.incrementAndGet();
                        firstDone.countDown();
                    }, null);
            second.sendInput(SendInputParams.builder().message(message("", 0)).tenant("two").build(),
                    List.of((event, agentCard) -> secondEvents.incrementAndGet()), error -> {
                        secondFailure.set(error);
                        secondTerminals.incrementAndGet();
                        secondDone.countDown();
                    }, null);
            assertTrue(peer.session("/one/message:stream").opened.await(5, TimeUnit.SECONDS));
            assertTrue(peer.session("/two/message:stream").opened.await(5, TimeUnit.SECONDS));
            first.close();
            assertTrue(peer.session("/one/message:stream").closed.await(5, TimeUnit.SECONDS));
            assertEquals(1, peer.session("/two/message:stream").closed.getCount());
            peer.session("/two/message:stream").release.countDown();
            assertTrue(secondDone.await(5, TimeUnit.SECONDS));
            assertTrue(firstDone.await(5, TimeUnit.SECONDS));
            assertEquals(1, firstTerminals.get());
            assertInstanceOf(CancellationException.class, firstFailure.get());
            assertEquals(1, secondTerminals.get());
            assertNull(secondFailure.get());
            assertEquals(1, secondEvents.get());
            second.close();
        }
    }

    @Test
    void publicRestCompletionErrorAndCancelRaceDuringInFlightEventCallback() throws Exception {
        Task firstTask = new Task("task-race", "context-task-race",
                new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(), List.of(), Map.of(), 1,
                List.of(new TimelineEntry(1, new TimelineEntry.MessageValue(message("task-race", 1)))));
        String firstFrame = JsonFormat.printer().omittingInsignificantWhitespace().print(
                org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto.taskOrMessageStream(firstTask));
        Task afterTerminalTask = new Task("task-after-terminal", "context-task-after-terminal",
                new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(), List.of(), Map.of(), 2,
                List.of(new TimelineEntry(2,
                        new TimelineEntry.MessageValue(message("task-after-terminal", 2)))));
        String afterTerminalFrame = JsonFormat.printer().omittingInsignificantWhitespace().print(
                org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto.taskOrMessageStream(afterTerminalTask));

        try (RestRacingSsePeer peer = new RestRacingSsePeer(firstFrame, afterTerminalFrame)) {
            Client client = client(card("1.1", peer.baseUrl()), "1.1", true);
            assertEquals("1.1", client.getProtocolVersion());
            AtomicInteger eventCallbackStarts = new AtomicInteger();
            AtomicInteger terminalCallbackStarts = new AtomicInteger();
            CountDownLatch callbackEntered = new CountDownLatch(1);
            CountDownLatch releaseCallback = new CountDownLatch(1);
            CountDownLatch terminalCallback = new CountDownLatch(1);

            client.sendInput(SendInputParams.builder().message(message("", 0)).build(),
                    List.of((event, agentCard) -> {
                        eventCallbackStarts.incrementAndGet();
                        callbackEntered.countDown();
                        try {
                            if (!releaseCallback.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("event callback was not released");
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        }
                    }), failure -> {
                        terminalCallbackStarts.incrementAndGet();
                        terminalCallback.countDown();
                    }, null);

            assertTrue(peer.requestReceived.await(5, TimeUnit.SECONDS));
            assertTrue(peer.firstEventWritten.await(5, TimeUnit.SECONDS));
            assertTrue(callbackEntered.await(5, TimeUnit.SECONDS));
            assertEquals("POST", peer.requestMethod);
            assertEquals("/message:stream", peer.requestPath);
            assertEquals("1.1", peer.requestVersion);
            assertTrue(JsonParser.parseString(peer.requestBody).getAsJsonObject().has("message"),
                    "REST streaming sends a message body instead of a JSON-RPC envelope");

            CountDownLatch raceStart = new CountDownLatch(1);
            ExecutorService racers = Executors.newFixedThreadPool(2);
            try {
                var peerTerminals = racers.submit(() -> {
                    raceStart.await();
                    peer.releaseTerminalTriggers.countDown();
                    return null;
                });
                var clientCancel = racers.submit(() -> {
                    raceStart.await();
                    client.close();
                    return null;
                });
                raceStart.countDown();
                peerTerminals.get(5, TimeUnit.SECONDS);
                clientCancel.get(5, TimeUnit.SECONDS);
                assertTrue(peer.terminalSequenceAttempted.await(5, TimeUnit.SECONDS));
                assertTrue(peer.responseClosed.await(5, TimeUnit.SECONDS));
            } finally {
                releaseCallback.countDown();
                racers.shutdownNow();
            }

            assertTrue(terminalCallback.await(5, TimeUnit.SECONDS));
            assertEquals(1, terminalCallbackStarts.get(), "public completion callback must start at most once");
            assertEquals(1, eventCallbackStarts.get(), "no event callback may start after a terminal trigger");
        }
    }

    @Test
    void publicClientCloseCancelsHeldOpenAllUnknownRestStream() throws Exception {
        try (HeldOpenRestPeer peer = new HeldOpenRestPeer()) {
            Client client = client(card("1.1", peer.baseUrl()), "1.1", true);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicInteger terminals = new AtomicInteger();
            CountDownLatch done = new CountDownLatch(1);
            client.sendInput(SendInputParams.builder().message(message("", 0)).build(), List.of(), error -> {
                failure.set(error);
                terminals.incrementAndGet();
                done.countDown();
            }, null);
            assertTrue(peer.session("/message:stream").opened.await(5, TimeUnit.SECONDS));
            client.close();
            assertTrue(peer.session("/message:stream").closed.await(5, TimeUnit.SECONDS));
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(1, terminals.get());
            assertInstanceOf(CancellationException.class, failure.get());
        }
    }

    private void respond(String path, JsonObject body) {
        server.when(request().withMethod("GET").withPath(path))
                .respond(response().withStatusCode(200).withBody(body.toString()));
    }

    private static Client client(AgentCard card, String version, boolean streaming) {
        return client(card, version, streaming, List.of());
    }

    private static Client client(AgentCard card, String version, boolean streaming,
            List<ClientCallInterceptor> interceptors) {
        return client(card, version, streaming, new JdkA2AHttpClient(), interceptors);
    }

    private static Client client(AgentCard card, String version, boolean streaming,
            org.a2aproject.sdk.client.http.A2AHttpClient httpClient) {
        return client(card, version, streaming, httpClient, List.of());
    }

    private static Client client(AgentCard card, String version, boolean streaming,
            org.a2aproject.sdk.client.http.A2AHttpClient httpClient, List<ClientCallInterceptor> interceptors) {
        ClientConfig.Builder configBuilder = new ClientConfig.Builder().setStreaming(streaming);
        if (version != null) {
            configBuilder.setProtocolVersion(version);
        }
        RestTransportConfig transportConfig = new RestTransportConfig(httpClient);
        transportConfig.setInterceptors(interceptors);
        ClientBuilder builder = Client.builder(card).withTransport(RestTransport.class, transportConfig);
        if (version != null) {
            builder.clientConfig(configBuilder.build());
        }
        return builder.build();
    }

    private static java.util.Set<ClientFeature> capabilities(Client client) throws Exception {
        var field = Client.class.getDeclaredField("clientTransport");
        field.setAccessible(true);
        return ((VersionedClientTransport) field.get(client)).capabilities();
    }

    private static String sse(String... data) {
        return java.util.Arrays.stream(data).map(value -> "data: " + value + "\n\n")
                .collect(java.util.stream.Collectors.joining());
    }

    private static AgentCard card(String version) {
        return card(version, "http://localhost:4001");
    }

    private static AgentCard card(String version, String endpoint) {
        return AgentCard.builder().name("REST public test").description("test").version("1")
                .supportedInterfaces(List.of(new AgentInterface("HTTP+JSON", endpoint, null, version)))
                .capabilities(AgentCapabilities.builder().streaming(true).userArtifacts(true).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
    }

    private static JsonObject taskJson(String id, long first, long last) throws Exception {
        Task task = new Task(id, "context-" + id, new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(), List.of(),
                Map.of(), last, List.of(new TimelineEntry(first, new TimelineEntry.MessageValue(message(id, first))),
                        new TimelineEntry(last, new TimelineEntry.MessageValue(message(id, last)))));
        return JsonParser.parseString(JsonUtil.toJson(task)).getAsJsonObject();
    }

    private static Message message(String id, long generation) {
        return Message.builder().messageId(id + "-m" + generation).taskId(id).contextId("context-" + id)
                .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("generation-" + generation))).build();
    }

    private static List<Long> generations(Task task) {
        return task.timeline().stream().map(TimelineEntry::generation).toList();
    }

    private static void addUnknown(JsonObject task, long generation) {
        JsonArray old = task.getAsJsonArray("timeline");
        JsonArray timeline = new JsonArray();
        timeline.add(old.get(0));
        JsonObject future = new JsonObject();
        future.addProperty("generation", Long.toString(generation));
        future.add("futureKind", new JsonObject());
        timeline.add(future);
        timeline.add(old.get(1));
        task.add("timeline", timeline);
    }

    private static final class HeldOpenRestPeer implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
        private final java.util.Map<String, HeldOpenSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();

        HeldOpenRestPeer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        HeldOpenSession session(String path) {
            return sessions.computeIfAbsent(path, ignored -> new HeldOpenSession());
        }

        private void handle(HttpExchange exchange) {
            String path = exchange.getRequestURI().getPath();
            HeldOpenSession session = session(path);
            try {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                OutputStream output = exchange.getResponseBody();
                output.write("data: {\"futureKind\":{}}\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                session.opened.countDown();
                while (!session.release.await(50, TimeUnit.MILLISECONDS)) {
                    output.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                if (path.startsWith("/two/")) {
                    Task task = new Task("task-two", "context-two", new TaskStatus(TaskState.TASK_STATE_WORKING),
                            List.of(), List.of(), Map.of(), 0, List.of());
                    String frame = JsonFormat.printer().omittingInsignificantWhitespace().print(
                            org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto.taskOrMessageStream(task));
                    output.write(sse(frame).getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
            } catch (IOException ignored) {
                // Client.close cancels the request and closes this response stream.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                session.closed.countDown();
                exchange.close();
            }
        }

        @Override
        public void close() {
            sessions.values().forEach(session -> session.release.countDown());
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class RestRacingSsePeer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final String firstFrame;
        private final String afterTerminalFrame;
        private final CountDownLatch requestReceived = new CountDownLatch(1);
        private final CountDownLatch firstEventWritten = new CountDownLatch(1);
        private final CountDownLatch releaseTerminalTriggers = new CountDownLatch(1);
        private final CountDownLatch terminalSequenceAttempted = new CountDownLatch(1);
        private final CountDownLatch responseClosed = new CountDownLatch(1);
        private volatile String requestMethod;
        private volatile String requestPath;
        private volatile String requestVersion;
        private volatile String requestBody;

        RestRacingSsePeer(String firstFrame, String afterTerminalFrame) throws IOException {
            this.firstFrame = firstFrame;
            this.afterTerminalFrame = afterTerminalFrame;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) {
            requestMethod = exchange.getRequestMethod();
            requestPath = exchange.getRequestURI().getPath();
            requestVersion = exchange.getRequestHeaders().getFirst("A2A-Version");
            try {
                requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requestReceived.countDown();
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(sse(firstFrame).getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    firstEventWritten.countDown();
                    releaseTerminalTriggers.await(5, TimeUnit.SECONDS);
                    try {
                        output.write("data: {\"task\":\n\n".getBytes(StandardCharsets.UTF_8));
                        output.write(sse(afterTerminalFrame).getBytes(StandardCharsets.UTF_8));
                        output.flush();
                    } finally {
                        terminalSequenceAttempted.countDown();
                    }
                }
            } catch (IOException ignored) {
                // Client.close can cancel the response while the peer writes competing terminal data.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                requestReceived.countDown();
                terminalSequenceAttempted.countDown();
                responseClosed.countDown();
                exchange.close();
            }
        }

        @Override
        public void close() {
            releaseTerminalTriggers.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class HeldOpenSession {
        private final CountDownLatch opened = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
    }
}
