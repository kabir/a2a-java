package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
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
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;

class Phase2PublicClientTest {

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
    void publicGetAndListSkipUnknownTimelineEntriesAndWrapperRetainsPerTaskEvidence() throws Exception {
        JsonObject taskA = taskJson("task-A", 7, 9);
        addUnknown(taskA, 8);
        JsonObject taskB = taskJson("task-B", 17, 19);
        addUnknown(taskB, 18);
        String getResponse = envelope(taskA);
        JsonObject result = new JsonObject();
        JsonArray tasks = new JsonArray();
        tasks.add(taskA);
        tasks.add(taskB);
        result.add("tasks", tasks);
        result.addProperty("totalSize", 12);
        result.addProperty("pageSize", 2);
        result.addProperty("nextPageToken", "page-2");
        String listResponse = envelope(result);
        try (RespondingJsonRpcPeer peer = new RespondingJsonRpcPeer(requestBody ->
                "GetTask".equals(requestBody.get("method").getAsString())
                        ? echoEnvelope(getResponse, requestBody)
                        : echoEnvelope(listResponse, requestBody))) {
            AgentCard card = AgentCard.builder().name("test").description("test").version("1")
                    .supportedInterfaces(List.of(new AgentInterface("JSONRPC", peer.baseUrl(), null, "1.1")))
                    .capabilities(AgentCapabilities.builder().build()).defaultInputModes(List.of("text"))
                    .defaultOutputModes(List.of("text")).skills(List.of()).build();
            Client explicit = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.1").build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig(new JdkA2AHttpClient()))
                    .build();
            Client defaultClient = Client.builder(card)
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig(new JdkA2AHttpClient()))
                    .build();
            Field transportField = Client.class.getDeclaredField("clientTransport");
            transportField.setAccessible(true);
            VersionedClientTransport selected = (VersionedClientTransport) transportField.get(explicit);
            VersionedClientTransport defaultSelected = (VersionedClientTransport) transportField.get(defaultClient);

            Set<org.a2aproject.sdk.client.transport.spi.ClientFeature> oneOneFeatures = Set.of(
                    org.a2aproject.sdk.client.transport.spi.ClientFeature.ORDINARY,
                    org.a2aproject.sdk.client.transport.spi.ClientFeature.GUARDED_INPUT,
                    org.a2aproject.sdk.client.transport.spi.ClientFeature.ARTIFACT_INPUT,
                    org.a2aproject.sdk.client.transport.spi.ClientFeature.UNKNOWN_VARIANT_SKIP);
            assertEquals("1.1", selected.protocolVersion());
            assertEquals("1.1", defaultSelected.protocolVersion());
            assertEquals(oneOneFeatures, selected.capabilities());
            assertEquals(oneOneFeatures, defaultSelected.capabilities());
            Task publicGet = explicit.getTask(new TaskQueryParams("task-A"), null);
            var publicList = defaultClient.listTasks(ListTasksParams.builder().pageSize(2).build(), null);

            assertEquals("task-A", publicGet.id());
            assertEquals(List.of(7L, 9L), publicGet.timeline().stream().map(TimelineEntry::generation).toList());
            assertEquals(List.of("task-A", "task-B"), publicList.tasks().stream().map(Task::id).toList());
            assertEquals(List.of(7L, 9L), publicList.tasks().get(0).timeline().stream()
                    .map(TimelineEntry::generation).toList());
            assertEquals(List.of(17L, 19L), publicList.tasks().get(1).timeline().stream()
                    .map(TimelineEntry::generation).toList());
            assertEquals(12, publicList.totalSize());
            assertEquals(2, publicList.pageSize());
            assertEquals("page-2", publicList.nextPageToken());

            var decodedGet = selected.getTaskDecoded(new TaskQueryParams("task-A"), null);
            var decoded = selected.listTasksDecoded(ListTasksParams.builder().pageSize(2).build(), null);
            assertEquals(List.of("task-A", "task-B"), decoded.value().tasks().stream().map(Task::id).toList());
            assertEquals(List.of(7L, 9L), decodedGet.value().timeline().stream()
                    .map(TimelineEntry::generation).toList());
            assertEquals(List.of(8L), decodedGet.taskEvidence().get(0).skippedTimelineGenerations());
            assertTrue(decodedGet.taskEvidence().get(0).unknownSkipped());
            assertEquals(List.of("task-A", "task-B"), decoded.taskEvidence().stream().map(e -> e.taskId()).toList());
            assertEquals(List.of(8L), decoded.taskEvidence().get(0).skippedTimelineGenerations());
            assertEquals(List.of(18L), decoded.taskEvidence().get(1).skippedTimelineGenerations());
            assertTrue(decoded.taskEvidence().stream().allMatch(e -> e.unknownSkipped()));
            explicit.close();
            defaultClient.close();
        }
    }

    @Test
    void explicitOneZeroPublicGetAndListKeepTheOrdinaryReadPath() throws Exception {
        JsonObject taskA = legacyTaskJson("task-legacy-A");
        JsonObject listResult = new JsonObject();
        JsonArray tasks = new JsonArray();
        tasks.add(taskA);
        tasks.add(legacyTaskJson("task-legacy-B"));
        listResult.add("tasks", tasks);
        listResult.addProperty("totalSize", 2);
        listResult.addProperty("pageSize", 2);
        listResult.addProperty("nextPageToken", "legacy-page-2");
        try (RespondingJsonRpcPeer peer = new RespondingJsonRpcPeer(requestBody ->
                echoEnvelope("GetTask".equals(requestBody.get("method").getAsString())
                        ? envelope(taskA) : envelope(listResult), requestBody))) {
            AgentCard card = AgentCard.builder().name("test").description("test").version("1")
                    .supportedInterfaces(List.of(new AgentInterface("JSONRPC", peer.baseUrl(), null, "1.0"),
                            new AgentInterface("JSONRPC", peer.baseUrl(), null, "1.1")))
                    .capabilities(AgentCapabilities.builder().build()).defaultInputModes(List.of("text"))
                    .defaultOutputModes(List.of("text")).skills(List.of()).build();
            Client client = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.0").build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig(new JdkA2AHttpClient()))
                    .build();
            Field transportField = Client.class.getDeclaredField("clientTransport");
            transportField.setAccessible(true);
            VersionedClientTransport selected = (VersionedClientTransport) transportField.get(client);

            assertEquals("1.0", selected.protocolVersion());
            assertEquals(Set.of(org.a2aproject.sdk.client.transport.spi.ClientFeature.ORDINARY),
                    selected.capabilities());
            assertEquals("task-legacy-A", client.getTask(new TaskQueryParams("task-legacy-A"), null).id());
            var listed = client.listTasks(ListTasksParams.builder().pageSize(2).build(), null);
            assertEquals(List.of("task-legacy-A", "task-legacy-B"), listed.tasks().stream().map(Task::id).toList());
            assertEquals(2, listed.pageSize());
            assertEquals("legacy-page-2", listed.nextPageToken());
            assertEquals(2, peer.requests.get());
            client.close();
        }
    }

    @Test
    void malformedKnownTimelineEntryFailsPublicGetInsteadOfBeingSkipped() throws Exception {
        JsonObject malformedTask = taskJson("task-malformed", 7, 9);
        JsonObject malformedKnown = new JsonObject();
        malformedKnown.addProperty("generation", "8");
        malformedKnown.add("message", new JsonObject());
        malformedTask.getAsJsonArray("timeline").set(1, malformedKnown);
        try (RespondingJsonRpcPeer peer = new RespondingJsonRpcPeer(requestBody ->
                echoEnvelope(envelope(malformedTask), requestBody))) {
            AgentCard card = publicCard(peer.baseUrl());
            Client client = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.1").build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig(new JdkA2AHttpClient()))
                    .build();

            org.junit.jupiter.api.Assertions.assertThrows(org.a2aproject.sdk.spec.A2AClientException.class,
                    () -> client.getTask(new TaskQueryParams("task-malformed"), null));
            client.close();
        }
    }

    @Test
    void publicClientStreamsTypedMessageAndArtifactUpdatesAndSkipsFutureVariant() throws Exception {
        Message priorMessage = message("task-public", 0);
        Message firstTimelineMessage = message("task-public", 1);
        Message secondTimelineMessage = message("task-public", 2);
        Task taskSnapshot = new Task("task-public", "context-task-public",
                new TaskStatus(TaskState.TASK_STATE_WORKING),
                List.of(new Artifact("artifact-baseline", "baseline", "prior artifact",
                        List.of(new TextPart("before")), Map.of(), List.of())),
                List.of(priorMessage), Map.of("baseline", "retained"), 2,
                List.of(new TimelineEntry(1, new TimelineEntry.MessageValue(firstTimelineMessage)),
                        new TimelineEntry(2, new TimelineEntry.MessageValue(secondTimelineMessage))));
        JsonObject task = JsonParser.parseString(JsonUtil.toJson(taskSnapshot)).getAsJsonObject();
        TaskMessageUpdateEvent messageUpdate = new TaskMessageUpdateEvent("task-public", "context-task-public",
                message("task-public", 3), Map.of("messageMeta", "retained"), 3);
        TaskStatusUpdateEvent statusUpdate = new TaskStatusUpdateEvent("task-public",
                new TaskStatus(TaskState.TASK_STATE_WORKING), "context-task-public",
                Map.of("statusMeta", "retained"), 4);
        TaskArtifactUpdateEvent responseArtifactUpdate = new TaskArtifactUpdateEvent("task-public",
                new Artifact("artifact-response", "response", "response artifact",
                        List.of(new TextPart("output")), Map.of("artifactMeta", "retained"), List.of()),
                "context-task-public", false, false, Map.of("eventMeta", "retained"), 5);
        JsonObject messageUpdateJson = JsonParser.parseString(JsonUtil.toJson(messageUpdate)).getAsJsonObject();
        JsonObject statusUpdateJson = JsonParser.parseString(JsonUtil.toJson(statusUpdate)).getAsJsonObject();
        JsonObject responseArtifactUpdateJson = JsonParser.parseString(JsonUtil.toJson(responseArtifactUpdate))
                .getAsJsonObject();
        AtomicReference<JsonObject> sentRequest = new AtomicReference<>();
        try (RespondingSsePeer peer = new RespondingSsePeer(requestBody -> {
            sentRequest.set(requestBody.deepCopy());
            String requestId = requestBody.get("id").getAsString();
            return publicSseBody(
                    publicEventEnvelope("task", task, requestId),
                    publicEventEnvelope("messageUpdate", messageUpdateJson, requestId),
                    publicResponseEnvelope(JsonParser.parseString("{\"futureVariant\":{}}").getAsJsonObject(),
                            requestId),
                    publicEventEnvelope("statusUpdate", statusUpdateJson, requestId),
                    publicEventEnvelope("artifactUpdate", responseArtifactUpdateJson, requestId));
        })) {
            Client client = publicClient(publicCard(peer.baseUrl(), true), new JdkA2AHttpClient());
            List<ClientEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Throwable> terminal = new AtomicReference<>();
            AtomicBoolean terminalCallbackStarted = new AtomicBoolean();
            AtomicInteger terminalCallbackStarts = new AtomicInteger();
            AtomicInteger eventCallbackStarts = new AtomicInteger();

            TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent("task-public",
                    new Artifact("artifact-public", "name", "description", List.of(new TextPart("input")),
                            Map.of("origin", "user"), List.of()),
                    "context-task-public", true, false, Map.of(), 4);
            SendInputParams params = SendInputParams.builder()
                    .message(message("task-public", 1))
                    .artifactUpdates(List.of(artifactUpdate))
                    .configuration(MessageSendConfiguration.builder().ifGenerationMatch(1L).timelineLength(3).build())
                    .tenant("tenant-public")
                    .build();
            client.sendInput(params,
                    List.of((event, agentCard) -> {
                        eventCallbackStarts.incrementAndGet();
                        assertFalse(terminalCallbackStarted.get(), "event callback must not start after terminal callback");
                        events.add(event);
                    }), failure -> {
                        terminalCallbackStarts.incrementAndGet();
                        terminalCallbackStarted.set(true);
                        terminal.set(failure);
                        completed.countDown();
                    }, null);

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertNull(terminal.get());
            assertEquals(4, events.size());
            assertEquals(4, eventCallbackStarts.get());
            assertEquals(1, terminalCallbackStarts.get());
            assertTrue(terminalCallbackStarted.get());
            assertEquals(List.of(TaskEvent.class, TaskUpdateEvent.class, TaskUpdateEvent.class, TaskUpdateEvent.class),
                    events.stream().map(Object::getClass).toList());
            assertInstanceOf(TaskEvent.class, events.get(0));
            TaskUpdateEvent messagePublicEvent = assertInstanceOf(TaskUpdateEvent.class, events.get(1));
            TaskMessageUpdateEvent receivedMessageUpdate = assertInstanceOf(TaskMessageUpdateEvent.class,
                    messagePublicEvent.getUpdateEvent());
            assertSame(receivedMessageUpdate, messagePublicEvent.getUpdateEvent());
            assertEquals("task-public", receivedMessageUpdate.taskId());
            assertEquals("context-task-public", receivedMessageUpdate.contextId());
            assertEquals("task-public-m3", receivedMessageUpdate.message().messageId());
            assertEquals("task-public", receivedMessageUpdate.message().taskId());
            assertEquals("context-task-public", receivedMessageUpdate.message().contextId());
            assertEquals("generation-3", ((TextPart) receivedMessageUpdate.message().parts().get(0)).text());
            assertEquals(Map.of("messageMeta", "retained"), receivedMessageUpdate.metadata());
            assertEquals(3, receivedMessageUpdate.generation());

            Task taskAfterMessageUpdate = messagePublicEvent.getTask();
            assertEquals(2, taskAfterMessageUpdate.generation());
            assertEquals(List.of(1L, 2L), taskAfterMessageUpdate.timeline().stream()
                    .map(TimelineEntry::generation).toList());
            assertEquals(List.of("artifact-baseline"), taskAfterMessageUpdate.artifacts().stream()
                    .map(Artifact::artifactId).toList());
            assertEquals("retained", taskAfterMessageUpdate.metadata().get("baseline"));
            assertEquals("retained", taskAfterMessageUpdate.metadata().get("messageMeta"));
            assertEquals(List.of("task-public-m0", "task-public-m3"), taskAfterMessageUpdate.history().stream()
                    .map(Message::messageId).toList());

            TaskUpdateEvent statusPublicEvent = assertInstanceOf(TaskUpdateEvent.class, events.get(2));
            TaskStatusUpdateEvent receivedStatusUpdate = assertInstanceOf(TaskStatusUpdateEvent.class,
                    statusPublicEvent.getUpdateEvent());
            assertEquals(TaskState.TASK_STATE_WORKING, receivedStatusUpdate.status().state());
            assertEquals(Map.of("statusMeta", "retained"), receivedStatusUpdate.metadata());
            assertEquals(4, receivedStatusUpdate.generation());
            assertEquals("retained", statusPublicEvent.getTask().metadata().get("baseline"));
            assertEquals("retained", statusPublicEvent.getTask().metadata().get("messageMeta"));
            assertEquals("retained", statusPublicEvent.getTask().metadata().get("statusMeta"));

            TaskUpdateEvent artifactPublicEvent = assertInstanceOf(TaskUpdateEvent.class, events.get(3));
            TaskArtifactUpdateEvent receivedArtifactUpdate = assertInstanceOf(TaskArtifactUpdateEvent.class,
                    artifactPublicEvent.getUpdateEvent());
            assertEquals("artifact-response", receivedArtifactUpdate.artifact().artifactId());
            assertEquals("response", receivedArtifactUpdate.artifact().name());
            assertEquals("response artifact", receivedArtifactUpdate.artifact().description());
            assertEquals(Map.of("artifactMeta", "retained"), receivedArtifactUpdate.artifact().metadata());
            assertEquals(List.of("output"), receivedArtifactUpdate.artifact().parts().stream()
                    .map(part -> ((TextPart) part).text()).toList());
            assertEquals(Map.of("eventMeta", "retained"), receivedArtifactUpdate.metadata());
            assertEquals(5, receivedArtifactUpdate.generation());
            assertEquals(List.of("artifact-baseline", "artifact-response"), artifactPublicEvent.getTask().artifacts()
                    .stream().map(Artifact::artifactId).toList());
            assertEquals(2, artifactPublicEvent.getTask().generation());
            assertEquals(List.of(1L, 2L), artifactPublicEvent.getTask().timeline().stream()
                    .map(TimelineEntry::generation).toList());
            assertEquals(List.of("task-public-m0", "task-public-m3"), artifactPublicEvent.getTask().history().stream()
                    .map(Message::messageId).toList());
            JsonObject sentParams = sentRequest.get().getAsJsonObject("params");
            assertEquals("SendStreamingMessage", sentRequest.get().get("method").getAsString());
            assertEquals("tenant-public", sentParams.get("tenant").getAsString());
            assertEquals("1", sentParams.getAsJsonObject("configuration").get("ifGenerationMatch").getAsString());
            assertEquals(3, sentParams.getAsJsonObject("configuration").get("timelineLength").getAsInt());
            assertEquals("task-public", sentParams.getAsJsonArray("artifactUpdates").get(0)
                    .getAsJsonObject().get("taskId").getAsString());
            assertEquals("artifact-public", sentParams.getAsJsonArray("artifactUpdates").get(0)
                    .getAsJsonObject().getAsJsonObject("artifact").get("artifactId").getAsString());
            client.close();
        }
    }

    @Test
    void publicUnaryInputDeliversTaskAndDirectMessageAndRetainsArtifactAndGuardFields() throws Exception {
        AtomicInteger responseIndex = new AtomicInteger();
        AtomicReference<JsonObject> taskRequest = new AtomicReference<>();
        try (RespondingJsonRpcPeer peer = new RespondingJsonRpcPeer(requestBody -> {
            if (responseIndex.getAndIncrement() == 0) {
                taskRequest.set(requestBody.deepCopy());
                JsonObject result = new JsonObject();
                try {
                    result.add("task", taskJson("task-unary", 1, 2));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return echoEnvelope(envelope(result), requestBody);
            }
            JsonObject result = new JsonObject();
            try {
                result.add("message", JsonParser.parseString(JsonUtil.toJson(message("", 3))));
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            return echoEnvelope(envelope(result), requestBody);
        })) {
            AgentCard card = publicCard(peer.baseUrl(), true);
            Client client = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.1").setStreaming(false).build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig(new JdkA2AHttpClient()))
                    .build();
            List<ClientEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
            CountDownLatch taskTerminal = new CountDownLatch(1);
            AtomicReference<Throwable> taskFailure = new AtomicReference<>();
            TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent("task-unary",
                    new Artifact("artifact-unary", "name", "description", List.of(new TextPart("input")),
                            Map.of(), List.of()),
                    "context-task-unary", false, true, Map.of(), 5);
            client.sendInput(SendInputParams.builder().message(message("task-unary", 2))
                    .artifactUpdates(List.of(artifactUpdate))
                    .configuration(MessageSendConfiguration.builder().ifGenerationMatch(2L).timelineLength(6).build())
                    .build(), List.of((event, agentCard) -> events.add(event)), failure -> {
                        taskFailure.set(failure);
                        taskTerminal.countDown();
                    }, null);

            assertTrue(taskTerminal.await(5, TimeUnit.SECONDS));
            assertNull(taskFailure.get());
            assertInstanceOf(TaskEvent.class, events.get(0));
            JsonObject requestParams = taskRequest.get().getAsJsonObject("params");
            assertEquals("2", requestParams.getAsJsonObject("configuration").get("ifGenerationMatch").getAsString());
            assertEquals(6, requestParams.getAsJsonObject("configuration").get("timelineLength").getAsInt());
            assertEquals("artifact-unary", requestParams.getAsJsonArray("artifactUpdates").get(0)
                    .getAsJsonObject().getAsJsonObject("artifact").get("artifactId").getAsString());

            CountDownLatch messageTerminal = new CountDownLatch(1);
            AtomicReference<Throwable> messageFailure = new AtomicReference<>();
            client.sendInput(SendInputParams.builder().message(message("", 3)).build(),
                    List.of((event, agentCard) -> events.add(event)), failure -> {
                        messageFailure.set(failure);
                        messageTerminal.countDown();
                    }, null);

            assertTrue(messageTerminal.await(5, TimeUnit.SECONDS));
            assertNull(messageFailure.get());
            assertEquals(2, events.size());
            assertInstanceOf(MessageEvent.class, events.get(1));
            client.close();
        }
    }

    @Test
    void publicUnaryAndStreamingInputsKeepTypedGenerationMismatchWithoutRetry() throws Exception {
        String mismatch = "{\"jsonrpc\":\"2.0\",\"id\":\"placeholder\",\"error\":{\"code\":-32010,\"message\":\"generation mismatch\"}}";
        try (RespondingJsonRpcPeer unaryPeer = new RespondingJsonRpcPeer(requestBody -> echoEnvelope(mismatch, requestBody))) {
            Client unary = Client.builder(publicCard(unaryPeer.baseUrl()))
                    .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.1").setStreaming(false).build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig(new JdkA2AHttpClient()))
                    .build();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch completed = new CountDownLatch(1);
            unary.sendInput(SendInputParams.builder().message(message("", 1))
                    .configuration(MessageSendConfiguration.builder().ifGenerationMatch(1L).build()).build(),
                    List.of(), error -> {
                        failure.set(error);
                        completed.countDown();
                    }, null);

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertInstanceOf(org.a2aproject.sdk.spec.TaskGenerationMismatchError.class, failure.get());
            assertEquals(1, unaryPeer.requests.get());
            unary.close();
        }

        AtomicInteger streamingRequests = new AtomicInteger();
        try (RespondingSsePeer streamingPeer = new RespondingSsePeer(requestBody -> {
            streamingRequests.incrementAndGet();
            JsonObject errorEnvelope = new JsonObject();
            errorEnvelope.addProperty("jsonrpc", "2.0");
            errorEnvelope.add("id", requestBody.get("id").deepCopy());
            JsonObject error = new JsonObject();
            error.addProperty("code", -32010);
            error.addProperty("message", "generation mismatch");
            errorEnvelope.add("error", error);
            return publicSseBody(errorEnvelope.toString());
        })) {
            Client streaming = publicClient(publicCard(streamingPeer.baseUrl()), new JdkA2AHttpClient());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch completed = new CountDownLatch(1);
            streaming.sendInput(SendInputParams.builder().message(message("", 1)).build(), List.of(), error -> {
                failure.set(error);
                completed.countDown();
            }, null);

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertInstanceOf(org.a2aproject.sdk.spec.TaskGenerationMismatchError.class, failure.get());
            assertEquals(1, streamingRequests.get());
            streaming.close();
        }
    }

    @Test
    void publicInputPreflightRejectsArtifactsWithoutCardFeatureAndV10GuardsBeforeIo() throws Exception {
        try (RespondingJsonRpcPeer peer = new RespondingJsonRpcPeer(requestBody -> envelope(new JsonObject()))) {
            AgentCard card = AgentCard.builder().name("test").description("test").version("1")
                    .supportedInterfaces(List.of(new AgentInterface("JSONRPC", peer.baseUrl(), null, "1.0"),
                            new AgentInterface("JSONRPC", peer.baseUrl(), null, "1.1")))
                    .capabilities(AgentCapabilities.builder().streaming(false).build())
                    .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
            Client v11 = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.1").setStreaming(false).build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig(new JdkA2AHttpClient()))
                    .build();
            TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent("task", new Artifact("artifact",
                    null, null, List.of(new TextPart("input")), Map.of(), List.of()), "context", false, false,
                    Map.of(), 1);
            org.junit.jupiter.api.Assertions.assertThrows(org.a2aproject.sdk.spec.A2AClientException.class,
                    () -> v11.sendInput(SendInputParams.builder().artifactUpdates(List.of(artifactUpdate)).build(),
                            List.of(), null, null));
            v11.close();

            Client v10 = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.0").setStreaming(false).build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig(new JdkA2AHttpClient()))
                    .build();
            org.junit.jupiter.api.Assertions.assertThrows(org.a2aproject.sdk.spec.A2AClientException.class,
                    () -> v10.sendInput(SendInputParams.builder().message(message("", 1))
                            .configuration(MessageSendConfiguration.builder().ifGenerationMatch(0L).build()).build(),
                            List.of(), null, null));
            assertEquals(0, peer.requests.get());
            v10.close();
        }
    }

    @Test
    void streamErrorBecomesOneRequestLocalPublicFailure() throws Exception {
        JsonObject rejection = new JsonObject();
        rejection.addProperty("code", "GENERATION_MISMATCH");
        rejection.addProperty("message", "stale input");
        JsonObject result = new JsonObject();
        result.add("error", rejection);
        try (RespondingSsePeer peer = new RespondingSsePeer(requestBody -> publicSseBody(
                publicResponseEnvelope(result, requestBody.get("id").getAsString())))) {
            Client client = publicClient(publicCard(peer.baseUrl()), new JdkA2AHttpClient());
            List<ClientEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
            AtomicInteger errorCount = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch completed = new CountDownLatch(1);

            client.sendInput(SendInputParams.builder().message(message("", 0)).build(),
                    List.of((event, agentCard) -> events.add(event)), error -> {
                        failure.set(error);
                        errorCount.incrementAndGet();
                        completed.countDown();
                    }, null);

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertEquals(1, errorCount.get());
            assertInstanceOf(org.a2aproject.sdk.spec.A2AClientException.class, failure.get());
            assertTrue(failure.get().getMessage().contains("GENERATION_MISMATCH"));
            assertTrue(events.isEmpty(), "local rejection must not be delivered as a task event");
            client.close();
        }
    }

    @Test
    void malformedKnownStreamEventReportsExactlyOnePublicError() throws Exception {
        JsonObject malformedTask = taskJson("task-malformed", 7, 9);
        JsonObject malformedKnown = new JsonObject();
        malformedKnown.addProperty("generation", "8");
        malformedKnown.add("message", new JsonObject());
        malformedTask.getAsJsonArray("timeline").set(1, malformedKnown);
        JsonObject taskEvent = new JsonObject();
        taskEvent.add("task", malformedTask);
        JsonObject laterTaskEvent = new JsonObject();
        laterTaskEvent.add("task", taskJson("task-late", 10, 11));
        try (RespondingSsePeer peer = new RespondingSsePeer(requestBody -> {
            String requestId = requestBody.get("id").getAsString();
            return publicSseBody(publicResponseEnvelope(taskEvent, requestId),
                    publicResponseEnvelope(laterTaskEvent, requestId));
        })) {
            Client client = publicClient(publicCard(peer.baseUrl()), new JdkA2AHttpClient());
            List<ClientEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
            AtomicInteger errorCount = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch completed = new CountDownLatch(1);

            client.sendInput(SendInputParams.builder().message(message("", 0)).build(),
                    List.of((event, agentCard) -> events.add(event)), error -> {
                        failure.set(error);
                        errorCount.incrementAndGet();
                        completed.countDown();
                    }, null);

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertEquals(1, errorCount.get());
            assertTrue(events.isEmpty(), "malformed known data and later frames must not reach public callbacks");
            assertTrue(failure.get() != null);
            client.close();
        }
    }

    @Test
    void closingOneOfTwoPublicClientsLeavesSharedInjectedHttpClientOperational() throws Exception {
        try (HeldOpenSsePeer peer = new HeldOpenSsePeer()) {
            A2AHttpClient sharedHttpClient = new JdkA2AHttpClient();
            AgentCard card = publicCard(peer.baseUrl());
            Client first = publicClient(card, sharedHttpClient);
            Client second = publicClient(card, sharedHttpClient);
            List<ClientEvent> secondEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
            AtomicInteger firstTerminals = new AtomicInteger();
            AtomicInteger secondTerminals = new AtomicInteger();
            AtomicReference<Throwable> firstFailure = new AtomicReference<>();
            AtomicReference<Throwable> secondFailure = new AtomicReference<>();
            CountDownLatch firstCompleted = new CountDownLatch(1);
            CountDownLatch secondCompleted = new CountDownLatch(1);

            first.sendInput(SendInputParams.builder().message(message("", 0)).tenant("one").build(), List.of(),
                    failure -> {
                        firstFailure.set(failure);
                        firstTerminals.incrementAndGet();
                        firstCompleted.countDown();
                    }, null);
            second.sendInput(SendInputParams.builder().message(message("", 0)).tenant("two").build(),
                    List.of((event, agentCard) -> secondEvents.add(event)), failure -> {
                        secondFailure.set(failure);
                        secondTerminals.incrementAndGet();
                        secondCompleted.countDown();
                    }, null);

            assertTrue(peer.session("/one").opened.await(5, TimeUnit.SECONDS));
            assertTrue(peer.session("/two").opened.await(5, TimeUnit.SECONDS));
            first.close();
            assertTrue(peer.session("/one").closed.await(5, TimeUnit.SECONDS));
            assertEquals(1, peer.session("/two").closed.getCount(),
                    "closing one public client must leave the other SSE read open");

            peer.session("/two").release.countDown();
            assertTrue(secondCompleted.await(5, TimeUnit.SECONDS));
            assertTrue(firstCompleted.await(5, TimeUnit.SECONDS));
            assertEquals(1, firstTerminals.get());
            assertInstanceOf(CancellationException.class, firstFailure.get());
            assertEquals(1, secondTerminals.get());
            assertNull(secondFailure.get());
            assertEquals(1, secondEvents.size());
            assertInstanceOf(TaskEvent.class, secondEvents.get(0));
            second.close();
        }
    }

    @Test
    void closingPublicClientDetachesAllUnknownHeldOpenInputStream() throws Exception {
        try (HeldOpenSsePeer peer = new HeldOpenSsePeer()) {
            AgentCard card = AgentCard.builder().name("test").description("test").version("1")
                    .supportedInterfaces(List.of(new AgentInterface("JSONRPC", peer.baseUrl(), null, "1.1")))
                    .capabilities(AgentCapabilities.builder().streaming(true).build()).defaultInputModes(List.of("text"))
                    .defaultOutputModes(List.of("text")).skills(List.of()).build();
            Client client = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.1").setStreaming(true).build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                    .build();
            List<ClientEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
            CountDownLatch completed = new CountDownLatch(1);
            AtomicInteger terminalCount = new AtomicInteger();
            AtomicReference<Throwable> terminal = new AtomicReference<>();
            client.sendInput(SendInputParams.builder().message(message("", 0)).build(),
                    List.of((event, agentCard) -> events.add(event)), failure -> {
                        terminal.set(failure);
                        terminalCount.incrementAndGet();
                        completed.countDown();
                    }, null);

            assertTrue(peer.session("/").opened.await(5, TimeUnit.SECONDS));
            client.close();

            assertTrue(peer.session("/").closed.await(5, TimeUnit.SECONDS),
                    "Client.close must detach the held-open HTTP read");
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertEquals(1, terminalCount.get());
            assertInstanceOf(CancellationException.class, terminal.get());
            assertTrue(events.isEmpty(), "unknown future variants must not become public events");
        }
    }

    @Test
    void publicJsonRpcCompletionErrorAndCancelRaceDuringInFlightEventCallback() throws Exception {
        try (RacingSsePeer peer = new RacingSsePeer()) {
            Client client = publicClient(publicCard(peer.baseUrl()), new JdkA2AHttpClient());
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

            assertTrue(peer.firstEventWritten.await(5, TimeUnit.SECONDS));
            assertTrue(callbackEntered.await(5, TimeUnit.SECONDS));
            CountDownLatch raceStart = new CountDownLatch(1);
            ExecutorService racers = Executors.newFixedThreadPool(2);
            try {
                var peerTerminal = racers.submit(() -> {
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
                peerTerminal.get(5, TimeUnit.SECONDS);
                clientCancel.get(5, TimeUnit.SECONDS);
                assertTrue(peer.malformedEventWritten.await(5, TimeUnit.SECONDS));
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

    private static JsonObject taskJson(String id, long first, long last) throws Exception {
        Task task = new Task(id, "context-" + id, new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(), List.of(),
                Map.of(), last, List.of(
                        new TimelineEntry(first, new TimelineEntry.MessageValue(message(id, first))),
                        new TimelineEntry(last, new TimelineEntry.MessageValue(message(id, last)))));
        return JsonParser.parseString(JsonUtil.toJson(task)).getAsJsonObject();
    }

    private static JsonObject legacyTaskJson(String id) {
        JsonObject task = new JsonObject();
        task.addProperty("id", id);
        task.addProperty("contextId", "context-" + id);
        task.add("status", JsonParser.parseString("{\"state\":\"TASK_STATE_WORKING\"}"));
        return task;
    }

    private static AgentCard publicCard(String url) {
        return publicCard(url, false);
    }

    private static AgentCard publicCard(String url, boolean userArtifacts) {
        return AgentCard.builder().name("test").description("test").version("1")
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", url, null, "1.1")))
                .capabilities(AgentCapabilities.builder().streaming(true).userArtifacts(userArtifacts).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text")).skills(List.of()).build();
    }

    private static Client publicClient(AgentCard card, A2AHttpClient httpClient) {
        return Client.builder(card)
                .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.1").setStreaming(true).build())
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig(httpClient))
                .build();
    }

    private static Message message(String id, long generation) {
        return Message.builder().messageId(id + "-m" + generation).taskId(id).contextId("context-" + id)
                .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("generation-" + generation))).build();
    }

    private static void addUnknown(JsonObject task, long generation) {
        JsonArray old = task.getAsJsonArray("timeline");
        JsonArray timeline = new JsonArray();
        timeline.add(old.get(0));
        JsonObject unknown = new JsonObject();
        unknown.addProperty("generation", Long.toString(generation));
        unknown.add("futureKind", new JsonObject());
        timeline.add(unknown);
        timeline.add(old.get(1));
        task.add("timeline", timeline);
    }

    private static String envelope(JsonObject result) {
        return envelope(result, "response-1");
    }

    private static String envelope(JsonObject result, String id) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        envelope.addProperty("id", id);
        envelope.add("result", result);
        return envelope.toString();
    }

    private static String echoEnvelope(String response, JsonObject requestBody) {
        JsonObject envelope = JsonParser.parseString(response).getAsJsonObject();
        envelope.add("id", requestBody.get("id").deepCopy());
        return envelope.toString();
    }

    private static String publicEventEnvelope(String variant, JsonObject payload) {
        JsonObject result = new JsonObject();
        result.add(variant, payload);
        return publicResponseEnvelope(result);
    }

    private static String publicEventEnvelope(String variant, JsonObject payload, String id) {
        JsonObject result = new JsonObject();
        result.add(variant, payload);
        return publicResponseEnvelope(result, id);
    }

    private static String publicResponseEnvelope(JsonObject result) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        envelope.addProperty("id", "response-1");
        envelope.add("result", result);
        return envelope.toString();
    }

    private static String publicResponseEnvelope(JsonObject result, String id) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        envelope.addProperty("id", id);
        envelope.add("result", result);
        return envelope.toString();
    }

    private static String publicSseBody(String... envelopes) {
        return java.util.Arrays.stream(envelopes).map(envelope -> "data: " + envelope + "\n\n")
                .collect(java.util.stream.Collectors.joining());
    }

    private static final class RespondingJsonRpcPeer implements AutoCloseable {
        private final HttpServer server;
        private final java.util.function.Function<JsonObject, String> responseFactory;
        private final AtomicInteger requests = new AtomicInteger();

        RespondingJsonRpcPeer(java.util.function.Function<JsonObject, String> responseFactory) throws IOException {
            this.responseFactory = responseFactory;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) {
            try (exchange) {
                JsonObject requestBody = JsonParser.parseString(
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                requests.incrementAndGet();
                byte[] body = responseFactory.apply(requestBody).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            } catch (IOException ignored) {
                // A closing client ends the controlled request.
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class RespondingSsePeer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final java.util.function.Function<JsonObject, String> responseFactory;

        RespondingSsePeer(java.util.function.Function<JsonObject, String> responseFactory) throws IOException {
            this.responseFactory = responseFactory;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) {
            try (exchange) {
                JsonObject requestBody = JsonParser.parseString(
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                byte[] body = responseFactory.apply(requestBody).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            } catch (IOException ignored) {
                // A rejected observation may cancel the HTTP read before all peer bytes are consumed.
            }
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class HeldOpenSsePeer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final Map<String, HeldOpenSession> sessions = new ConcurrentHashMap<>();

        HeldOpenSsePeer() throws IOException {
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
                JsonObject requestBody = JsonParser.parseString(
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                String requestId = requestBody.get("id").getAsString();
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                OutputStream output = exchange.getResponseBody();
                JsonObject future = JsonParser.parseString("{\"futureVariant\":{}}").getAsJsonObject();
                output.write(publicSseBody(publicResponseEnvelope(future, requestId)).getBytes(StandardCharsets.UTF_8));
                output.flush();
                session.opened.countDown();
                while (!session.release.await(50, TimeUnit.MILLISECONDS)) {
                    output.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                if ("/two".equals(path)) {
                    JsonObject taskEvent = new JsonObject();
                    taskEvent.add("task", JsonParser.parseString(
                            "{\"id\":\"task-two\",\"contextId\":\"context-two\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}"));
                    output.write(publicSseBody(publicResponseEnvelope(taskEvent, requestId))
                            .getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
            } catch (IOException ignored) {
                // A client closing its owned stream ends this held-open response.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                session.closed.countDown();
                exchange.close();
            }
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class RacingSsePeer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final CountDownLatch firstEventWritten = new CountDownLatch(1);
        private final CountDownLatch releaseTerminalTriggers = new CountDownLatch(1);
        private final CountDownLatch malformedEventWritten = new CountDownLatch(1);
        private final CountDownLatch responseClosed = new CountDownLatch(1);

        RacingSsePeer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) {
            try {
                JsonObject requestBody = JsonParser.parseString(
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                String requestId = requestBody.get("id").getAsString();
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(publicSseBody(publicEventEnvelope("task", taskJson("task-race", 1, 2), requestId))
                            .getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    firstEventWritten.countDown();
                    releaseTerminalTriggers.await(5, TimeUnit.SECONDS);

                    JsonObject malformedTask = taskJson("task-race", 1, 3);
                    JsonObject malformedKnown = new JsonObject();
                    malformedKnown.addProperty("generation", "2");
                    malformedKnown.add("message", new JsonObject());
                    malformedTask.getAsJsonArray("timeline").set(1, malformedKnown);
                    output.write(publicSseBody(
                            publicEventEnvelope("task", malformedTask, requestId),
                            publicEventEnvelope("task", taskJson("task-after-terminal", 4, 5), requestId))
                            .getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    malformedEventWritten.countDown();
                }
            } catch (IOException ignored) {
                // The client may cancel before the peer finishes sending the competing terminal triggers.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            } finally {
                malformedEventWritten.countDown();
                responseClosed.countDown();
                exchange.close();
            }
        }

        @Override
        public void close() {
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
