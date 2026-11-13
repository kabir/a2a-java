package org.a2aproject.sdk.client.transport.jsonrpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TransportCall;
import org.a2aproject.sdk.client.transport.spi.TransportEnd;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Delay;

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
    void selectedOneOneGetAndListKeepOrderedPerTaskDecodeEvidence() throws Exception {
        Task taskA = task("task-A", "context-A", 7, 9);
        String getResponse = envelope(taskA);
        Task taskB = task("task-B", "context-B", 17, 19);
        JsonObject listResult = new JsonObject();
        JsonArray tasks = new JsonArray();
        tasks.add(taskJson(taskA));
        tasks.add(taskJson(taskB));
        addUnknownTimelineEntry(tasks.get(0).getAsJsonObject(), 8);
        addUnknownTimelineEntry(tasks.get(1).getAsJsonObject(), 18);
        listResult.add("tasks", tasks);
        listResult.addProperty("totalSize", 12);
        listResult.addProperty("pageSize", 2);
        listResult.addProperty("nextPageToken", "page-2");
        String listResponse = responseEnvelope(listResult);

        try (JsonRpcTestPeer peer = new JsonRpcTestPeer(requestBody -> echoResponse(
                "GetTask".equals(requestBody.get("method").getAsString()) ? getResponse : listResponse,
                requestBody))) {
            VersionedClientTransport selected = new JSONRPCTransport(peer.baseUrl(), "1.1");
            assertTrue(selected.capabilities().contains(ClientFeature.UNKNOWN_VARIANT_SKIP));
            var decodedGet = selected.getTaskDecoded(new TaskQueryParams("task-A"), null);

            assertEquals("task-A", decodedGet.value().id());
            assertEquals(List.of(7L, 9L), decodedGet.value().timeline().stream().map(TimelineEntry::generation).toList());
            assertEquals(1, decodedGet.taskEvidence().size());
            assertEquals("task-A", decodedGet.taskEvidence().get(0).taskId());
            assertEquals(List.of(8L), decodedGet.taskEvidence().get(0).skippedTimelineGenerations());
            assertTrue(decodedGet.taskEvidence().get(0).unknownSkipped());

            var decodedList = selected.listTasksDecoded(ListTasksParams.builder().pageSize(2).build(), null);

            ListTasksResult result = decodedList.value();
            assertEquals(List.of("task-A", "task-B"), result.tasks().stream().map(Task::id).toList());
            assertEquals(12, result.totalSize());
            assertEquals(2, result.pageSize());
            assertEquals("page-2", result.nextPageToken());
            assertEquals(List.of(7L, 9L), result.tasks().get(0).timeline().stream().map(TimelineEntry::generation).toList());
            assertEquals(List.of(17L, 19L), result.tasks().get(1).timeline().stream().map(TimelineEntry::generation).toList());
            assertEquals(List.of("task-A", "task-B"), decodedList.taskEvidence().stream().map(e -> e.taskId()).toList());
            assertEquals(List.of(8L), decodedList.taskEvidence().get(0).skippedTimelineGenerations());
            assertEquals(List.of(18L), decodedList.taskEvidence().get(1).skippedTimelineGenerations());
            assertTrue(decodedList.taskEvidence().get(0).unknownSkipped());
            assertTrue(decodedList.taskEvidence().get(1).unknownSkipped());
            assertFalse(decodedList.taskEvidence().get(0).taskId().equals(decodedList.taskEvidence().get(1).taskId()));
            assertEquals(2, peer.requestIds().size());
            assertFalse(peer.requestIds().get(0).equals(peer.requestIds().get(1)));
        }
    }

    @Test
    void providerAndTransportAdvertiseOnlyImplementedFeaturesForEachVersion() {
        Set<ClientFeature> oneZero = Set.of(ClientFeature.ORDINARY);
        Set<ClientFeature> oneOne = Set.of(ClientFeature.ORDINARY, ClientFeature.GUARDED_INPUT,
                ClientFeature.ARTIFACT_INPUT, ClientFeature.UNKNOWN_VARIANT_SKIP);
        JSONRPCTransportProvider provider = new JSONRPCTransportProvider();

        assertEquals(oneZero, provider.capabilities().get("1.0"));
        assertEquals(oneOne, provider.capabilities().get("1.1"));
        assertEquals(oneZero, new JSONRPCTransport("http://localhost:4001", "1.0").capabilities());
        assertEquals(oneOne, new JSONRPCTransport("http://localhost:4001", "1.1").capabilities());
    }

    @Test
    void asyncGetPreservesUnknownTimelineEvidenceAndHandlesFailureAndCancellation() throws Exception {
        Task task = task("task-async", "context-async", 27, 29);
        String successResponse = envelope(task);
        try (JsonRpcTestPeer peer = new JsonRpcTestPeer(requestBody -> echoResponse(successResponse, requestBody))) {
            JSONRPCTransport selected = new JSONRPCTransport(peer.baseUrl(), "1.1");
            TransportCall<org.a2aproject.sdk.client.transport.spi.TransportResult<Task>> call = selected.getTaskAsync(
                    new TaskQueryParams("task-async"),
                    new SpiCallOptions(Instant.now().plusSeconds(5), 16, 4096), null);
            var decoded = call.result().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals("task-async", decoded.value().id());
            assertEquals(List.of(28L), decoded.taskEvidence().get(0).skippedTimelineGenerations());
            assertTrue(decoded.taskEvidence().get(0).unknownSkipped());
        }

        JSONRPCTransport selected = new JSONRPCTransport("http://localhost:4001", "1.1");
        server.reset();
        server.when(request().withMethod("POST").withPath("/"))
                .respond(response().withStatusCode(500).withBody(envelope(task)));
        TransportCall<org.a2aproject.sdk.client.transport.spi.TransportResult<Task>> httpFailure = selected.getTaskAsync(
                new TaskQueryParams("task-async"), SpiCallOptions.defaults(), null);
        CompletionException httpFailureException = assertThrows(CompletionException.class,
                () -> httpFailure.result().toCompletableFuture().join());
        assertInstanceOf(org.a2aproject.sdk.spec.A2AClientException.class, httpFailureException.getCause());
        assertInstanceOf(org.a2aproject.sdk.spec.A2AClientHTTPError.class,
                httpFailureException.getCause().getCause());

        server.reset();
        JsonObject malformed = taskJson(task);
        JsonObject malformedKnown = new JsonObject();
        malformedKnown.addProperty("generation", "28");
        malformedKnown.add("message", new JsonObject());
        malformed.getAsJsonArray("timeline").set(1, malformedKnown);
        String malformedResponse = responseEnvelope(malformed);
        try (JsonRpcTestPeer peer = new JsonRpcTestPeer(requestBody -> echoResponse(malformedResponse, requestBody))) {
            selected = new JSONRPCTransport(peer.baseUrl(), "1.1");
            TransportCall<org.a2aproject.sdk.client.transport.spi.TransportResult<Task>> failed = selected.getTaskAsync(
                    new TaskQueryParams("task-async"), SpiCallOptions.defaults(), null);
            CompletionException failure = assertThrows(CompletionException.class,
                    () -> failed.result().toCompletableFuture().join());
            assertInstanceOf(org.a2aproject.sdk.spec.A2AClientException.class, failure.getCause());
        }

        selected = new JSONRPCTransport("http://localhost:4001", "1.1");
        server.reset();
        server.when(request().withMethod("POST").withPath("/"))
                .respond(response().withStatusCode(200).withBody(envelope(task))
                        .withDelay(new Delay(TimeUnit.SECONDS, 3)));
        TransportCall<org.a2aproject.sdk.client.transport.spi.TransportResult<Task>> cancelled = selected.getTaskAsync(
                new TaskQueryParams("task-async"), SpiCallOptions.defaults(), null);
        cancelled.cancel();
        CompletionException cancellation = assertThrows(CompletionException.class,
                () -> cancelled.result().toCompletableFuture().join());
        assertInstanceOf(CancellationException.class, cancellation.getCause());
    }

    @Test
    void unarySendPreservesTaskEvidenceAndAllOneOneInputFields() throws Exception {
        Task task = task("task-send", "context-send", 37, 39);
        String sendResponse = sendTaskEnvelope(task);
        ClientCallInterceptor interceptor = new ClientCallInterceptor() {
            @Override
            public PayloadAndHeaders intercept(String method, Object payload, Map<String, String> headers,
                    org.a2aproject.sdk.spec.AgentCard card, ClientCallContext context) {
                assertEquals("SendMessage", method);
                assertEquals("from-interceptor", context.getHeaders().get("X-Context"));
                assertEquals("1.1", headers.get("A2A-Version"));
                Map<String, String> interceptedHeaders = new java.util.LinkedHashMap<>(headers);
                interceptedHeaders.put("X-Intercepted", "yes");
                return new PayloadAndHeaders(payload, interceptedHeaders);
            }
        };
        TaskArtifactUpdateEvent update = new TaskArtifactUpdateEvent("task-send",
                new Artifact("artifact-send", "name", "description", List.of(new TextPart("artifact")),
                        Map.of("artifact-meta", "kept"), List.of()),
                "context-send", true, false, Map.of("source", "client"), 88);
        SendInputParams input = SendInputParams.builder()
                .message(message("task-send", "context-send", 36))
                .artifactUpdates(List.of(update))
                .configuration(MessageSendConfiguration.builder().acceptedOutputModes(List.of("text/plain"))
                        .historyLength(2).returnImmediately(true).ifGenerationMatch(39L).timelineLength(4).build())
                .metadata(Map.of("trace", "send-trace"))
                .tenant("tenant-send")
                .build();

        try (JsonRpcTestPeer peer = new JsonRpcTestPeer(requestBody -> echoResponse(sendResponse, requestBody))) {
            JSONRPCTransport selected = new JSONRPCTransport(null, null,
                    new org.a2aproject.sdk.spec.AgentInterface("JSONRPC", peer.baseUrl(), null, "1.1"),
                    List.of(interceptor));
            var result = selected.sendInput(input, SpiCallOptions.defaults(),
                    new ClientCallContext(Map.of(), Map.of("X-Context", "from-interceptor")))
                    .result().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertInstanceOf(Task.class, result.value());
            assertEquals("task-send", ((Task) result.value()).id());
            assertEquals(1, result.taskEvidence().size());
            assertEquals("task-send", result.taskEvidence().get(0).taskId());
            assertEquals(List.of(38L), result.taskEvidence().get(0).skippedTimelineGenerations());
            assertTrue(result.taskEvidence().get(0).unknownSkipped());

            assertEquals(1, peer.requestCount());
            assertEquals("1.1", peer.requestHeader("A2A-Version"));
            assertEquals("from-interceptor", peer.requestHeader("X-Context"));
            assertEquals("yes", peer.requestHeader("X-Intercepted"));
            JsonObject requestBody = peer.lastRequestBody();
            assertTrue(requestBody.get("id").isJsonPrimitive());
            assertTrue(requestBody.get("id").getAsJsonPrimitive().isString());
            assertEquals("SendMessage", requestBody.get("method").getAsString());
            JsonObject params = requestBody.getAsJsonObject("params");
            assertEquals("task-send-m36", params.getAsJsonObject("message").get("messageId").getAsString());
            assertEquals("tenant-send", params.get("tenant").getAsString());
            assertEquals("send-trace", params.getAsJsonObject("metadata").get("trace").getAsString());
            JsonObject configuration = params.getAsJsonObject("configuration");
            assertEquals("39", configuration.get("ifGenerationMatch").getAsString());
            assertEquals(4, configuration.get("timelineLength").getAsInt());
            assertEquals(2, configuration.get("historyLength").getAsInt());
            assertTrue(configuration.get("returnImmediately").getAsBoolean());
            assertEquals("text/plain", configuration.getAsJsonArray("acceptedOutputModes").get(0).getAsString());
            JsonObject artifactUpdate = params.getAsJsonArray("artifactUpdates").get(0).getAsJsonObject();
            assertEquals("task-send", artifactUpdate.get("taskId").getAsString());
            assertEquals("context-send", artifactUpdate.get("contextId").getAsString());
            assertTrue(artifactUpdate.get("append").getAsBoolean());
            assertFalse(artifactUpdate.has("lastChunk"));
            assertEquals("client", artifactUpdate.getAsJsonObject("metadata").get("source").getAsString());
            JsonObject artifact = artifactUpdate.getAsJsonObject("artifact");
            assertEquals("artifact-send", artifact.get("artifactId").getAsString());
            assertEquals("name", artifact.get("name").getAsString());
            assertEquals("description", artifact.get("description").getAsString());
            assertEquals("kept", artifact.getAsJsonObject("metadata").get("artifact-meta").getAsString());
            assertFalse(artifactUpdate.has("generation"));
        }
    }

    @Test
    void unarySendDirectMessageHasNoTaskEvidence() throws Exception {
        JsonObject result = new JsonObject();
        result.add("message", JsonParser.parseString(JsonUtil.toJson(message("", "", 0))));
        String responseBody = responseEnvelope(result);
        try (JsonRpcTestPeer peer = new JsonRpcTestPeer(requestBody -> echoResponse(responseBody, requestBody))) {
            JSONRPCTransport selected = new JSONRPCTransport(peer.baseUrl(), "1.1");

            var response = selected.sendInput(SendInputParams.builder().message(message("", "", 0)).build(),
                    SpiCallOptions.defaults(), null).result().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertInstanceOf(Message.class, response.value());
            assertTrue(response.taskEvidence().isEmpty());
            assertEquals(1, peer.requestCount());
        }
    }

    @Test
    void unarySendMapsGenerationMismatchWithoutRetry() throws IOException {
        String mismatch = "{\"jsonrpc\":\"2.0\",\"id\":\"response-1\",\"error\":{\"code\":-32010,\"message\":\"generation mismatch\"}}";
        try (JsonRpcTestPeer peer = new JsonRpcTestPeer(requestBody -> echoResponse(mismatch, requestBody))) {
            JSONRPCTransport selected = new JSONRPCTransport(peer.baseUrl(), "1.1");

            var call = selected.sendInput(SendInputParams.builder().message(message("", "", 0)).build(),
                    SpiCallOptions.defaults(), null);
            CompletionException failure = assertThrows(CompletionException.class,
                    () -> call.result().toCompletableFuture().join());

            assertInstanceOf(TaskGenerationMismatchError.class, failure.getCause());
            assertEquals(1, peer.requestCount());
        }
    }

    @Test
    void unarySendRejectsHttpAndMalformedResponses() throws Exception {
        JSONRPCTransport selected = new JSONRPCTransport("http://localhost:4001", "1.1");
        SendInputParams input = SendInputParams.builder().message(message("", "", 0)).build();
        Task task = task("task-send", "context-send", 47, 49);
        server.when(request().withMethod("POST").withPath("/"))
                .respond(response().withStatusCode(500).withBody(sendTaskEnvelope(task)));

        CompletionException httpFailure = assertThrows(CompletionException.class,
                () -> selected.sendInput(input, SpiCallOptions.defaults(), null).result().toCompletableFuture().join());
        assertInstanceOf(org.a2aproject.sdk.spec.A2AClientException.class, httpFailure.getCause());
        assertInstanceOf(org.a2aproject.sdk.spec.A2AClientHTTPError.class, httpFailure.getCause().getCause());

        JsonObject malformedTask = taskJson(task);
        addUnknownTimelineEntry(malformedTask, 48);
        JsonObject malformedKnown = new JsonObject();
        malformedKnown.addProperty("generation", "48");
        malformedKnown.add("message", new JsonObject());
        malformedTask.getAsJsonArray("timeline").set(1, malformedKnown);
        JsonObject malformed = new JsonObject();
        malformed.add("task", malformedTask);
        String malformedResponse = responseEnvelope(malformed);
        try (JsonRpcTestPeer peer = new JsonRpcTestPeer(requestBody -> echoResponse(malformedResponse, requestBody))) {
            JSONRPCTransport malformedTransport = new JSONRPCTransport(peer.baseUrl(), "1.1");
            CompletionException malformedFailure = assertThrows(CompletionException.class,
                    () -> malformedTransport.sendInput(input, SpiCallOptions.defaults(), null).result().toCompletableFuture().join());
            assertInstanceOf(org.a2aproject.sdk.spec.A2AClientException.class, malformedFailure.getCause());
            assertEquals(1, peer.requestCount());
        }
    }

    @Test
    void unarySendCanBeCancelledAndDoesNotEnableNewV10Operation() {
        SendInputParams input = SendInputParams.builder().message(message("", "", 0)).build();
        JSONRPCTransport selected = new JSONRPCTransport("http://localhost:4001", "1.1");
        server.when(request().withMethod("POST").withPath("/"))
                .respond(response().withStatusCode(200).withBody(responseEnvelope(new JsonObject()))
                        .withDelay(new Delay(TimeUnit.SECONDS, 3)));

        TransportCall<org.a2aproject.sdk.client.transport.spi.TransportResult<org.a2aproject.sdk.spec.EventKind>> call =
                selected.sendInput(input, SpiCallOptions.defaults(), null);
        call.cancel();
        CompletionException cancellation = assertThrows(CompletionException.class,
                () -> call.result().toCompletableFuture().join());
        assertInstanceOf(CancellationException.class, cancellation.getCause());

        server.reset();
        JSONRPCTransport legacy = new JSONRPCTransport("http://localhost:4001", "1.0");
        assertThrows(UnsupportedOperationException.class,
                () -> legacy.sendInput(input, SpiCallOptions.defaults(), null));
        assertEquals(0, server.retrieveRecordedRequests(request().withMethod("POST")).length);
    }

    @Test
    void malformedKnownTimelineEntryFailsInsteadOfBecomingSkippedEvidence() throws Exception {
        JsonObject malformedTask = taskJson(task("task-A", "context-A", 7, 9));
        JsonArray timeline = malformedTask.getAsJsonArray("timeline");
        JsonObject malformedKnown = new JsonObject();
        malformedKnown.addProperty("generation", "8");
        malformedKnown.add("message", new JsonObject());
        timeline.set(1, malformedKnown);
        String malformedResponse = responseEnvelope(malformedTask);
        try (JsonRpcTestPeer peer = new JsonRpcTestPeer(requestBody -> echoResponse(malformedResponse, requestBody))) {
            VersionedClientTransport selected = new JSONRPCTransport(peer.baseUrl(), "1.1");
            assertThrows(org.a2aproject.sdk.spec.A2AClientException.class,
                    () -> selected.getTaskDecoded(new TaskQueryParams("task-A"), null));
        }
    }

    @Test
    void selectedOneOneUnaryGetRejectsResponseForAnotherRequestId() throws Exception {
        Task task = task("task-A", "context-A", 7, 9);
        String response = envelope(task);
        try (JsonRpcTestPeer peer = new JsonRpcTestPeer(ignoredRequest -> response)) {
            JSONRPCTransport selected = new JSONRPCTransport(peer.baseUrl(), "1.1");

            assertThrows(org.a2aproject.sdk.spec.A2AClientException.class,
                    () -> selected.getTaskDecoded(new TaskQueryParams("task-A"), null));
        }
    }

    @Test
    void selectedOneOneUnaryOperationsRejectMissingNullAndWrongTypeIds() throws Exception {
        Task task = task("task-id-check", "context-id-check", 1, 2);
        String getResponse = responseEnvelope(taskJson(task));
        JsonObject listResult = new JsonObject();
        JsonArray taskArray = new JsonArray();
        taskArray.add(taskJson(task));
        listResult.add("tasks", taskArray);
        listResult.addProperty("totalSize", 1);
        listResult.addProperty("pageSize", 1);
        String listResponse = responseEnvelope(listResult);
        String sendResponse = sendTaskEnvelope(task);

        for (UnaryOperation operation : UnaryOperation.values()) {
            String response = switch (operation) {
                case GET, ASYNC_GET -> getResponse;
                case LIST -> listResponse;
                case SEND -> sendResponse;
            };
            for (ResponseIdMode mode : List.of(ResponseIdMode.WRONG, ResponseIdMode.MISSING,
                    ResponseIdMode.NULL, ResponseIdMode.OBJECT, ResponseIdMode.BOOLEAN, ResponseIdMode.ARRAY)) {
                try (JsonRpcTestPeer peer = new JsonRpcTestPeer(requestBody -> responseWithId(response, requestBody, mode))) {
                    JSONRPCTransport selected = new JSONRPCTransport(peer.baseUrl(), "1.1");
                    switch (operation) {
                        case GET -> assertThrows(org.a2aproject.sdk.spec.A2AClientException.class,
                                () -> selected.getTaskDecoded(new TaskQueryParams("task-id-check"), null),
                                operation + " should reject " + mode + " id");
                        case LIST -> assertThrows(org.a2aproject.sdk.spec.A2AClientException.class,
                                () -> selected.listTasksDecoded(ListTasksParams.builder().pageSize(1).build(), null),
                                operation + " should reject " + mode + " id");
                        case ASYNC_GET -> {
                            var call = selected.getTaskAsync(new TaskQueryParams("task-id-check"),
                                    SpiCallOptions.defaults(), null);
                            CompletionException failure = assertThrows(CompletionException.class,
                                    () -> call.result().toCompletableFuture().join());
                            assertInstanceOf(org.a2aproject.sdk.spec.A2AClientException.class, failure.getCause());
                        }
                        case SEND -> {
                            var call = selected.sendInput(
                                    SendInputParams.builder().message(message("task-id-check", "context-id-check", 2)).build(),
                                    SpiCallOptions.defaults(), null);
                            CompletionException failure = assertThrows(CompletionException.class,
                                    () -> call.result().toCompletableFuture().join());
                            assertInstanceOf(org.a2aproject.sdk.spec.A2AClientException.class, failure.getCause());
                        }
                    }
                    assertEquals(1, peer.requestCount(), operation + " should issue exactly one request");
                }
            }
        }
    }

    @Test
    void unarySendCorrelatesErrorEnvelopeBeforeMappingTypedError() throws IOException {
        String mismatch = "{\"jsonrpc\":\"2.0\",\"id\":\"response-1\",\"error\":{\"code\":-32010,\"message\":\"generation mismatch\"}}";
        try (JsonRpcTestPeer peer = new JsonRpcTestPeer(requestBody -> responseWithId(mismatch, requestBody,
                ResponseIdMode.WRONG))) {
            JSONRPCTransport selected = new JSONRPCTransport(peer.baseUrl(), "1.1");
            var call = selected.sendInput(SendInputParams.builder().message(message("", "", 0)).build(),
                    SpiCallOptions.defaults(), null);
            CompletionException failure = assertThrows(CompletionException.class,
                    () -> call.result().toCompletableFuture().join());

            assertInstanceOf(org.a2aproject.sdk.spec.A2AClientException.class, failure.getCause());
            assertFalse(failure.getCause() instanceof TaskGenerationMismatchError);
            assertEquals(1, peer.requestCount());
        }
    }

    @Test
    void streamingInputDeliversKnownEventsAndKeepsFutureVariantsAsRawFrames() throws Exception {
        JsonObject taskPayload = taskJson(task("task-stream", "context-stream", 1, 1));
        JsonObject messageUpdate = new JsonObject();
        messageUpdate.addProperty("taskId", "task-stream");
        messageUpdate.addProperty("contextId", "context-stream");
        messageUpdate.add("message", JsonParser.parseString(JsonUtil.toJson(message("task-stream", "context-stream", 2))));
        messageUpdate.addProperty("generation", "2");
        JsonObject statusUpdate = new JsonObject();
        statusUpdate.addProperty("taskId", "task-stream");
        statusUpdate.addProperty("contextId", "context-stream");
        statusUpdate.add("status", JsonParser.parseString("{\"state\":\"TASK_STATE_SUBMITTED\"}"));
        JsonObject artifactUpdate = new JsonObject();
        artifactUpdate.addProperty("taskId", "task-stream");
        artifactUpdate.addProperty("contextId", "context-stream");
        artifactUpdate.addProperty("append", false);
        artifactUpdate.addProperty("lastChunk", false);
        artifactUpdate.add("artifact", JsonParser.parseString(
                "{\"artifactId\":\"stream-artifact\",\"parts\":[{\"text\":\"chunk\"}]}"));
        try (JsonRpcSseTestPeer peer = new JsonRpcSseTestPeer(requestBody -> new SseReply(200, "text/event-stream",
                sseBodyWithMatchingId(requestBody,
                        eventEnvelope("task", taskPayload),
                        eventEnvelope("messageUpdate", messageUpdate),
                        responseEnvelope(JsonParser.parseString("{\"futureEvent\":{\"payload\":true}}")
                                .getAsJsonObject()),
                        eventEnvelope("statusUpdate", statusUpdate),
                        eventEnvelope("artifactUpdate", artifactUpdate))))) {
            ClientCallInterceptor interceptor = new ClientCallInterceptor() {
                @Override
                public PayloadAndHeaders intercept(String method, Object payload, Map<String, String> headers,
                        org.a2aproject.sdk.spec.AgentCard card, ClientCallContext context) {
                    assertEquals("SendStreamingMessage", method);
                    Map<String, String> intercepted = new java.util.LinkedHashMap<>(headers);
                    intercepted.put("X-Intercepted", "stream");
                    return new PayloadAndHeaders(payload, intercepted);
                }
            };
            JSONRPCTransport selected = new JSONRPCTransport(null, null,
                    new AgentInterface("JSONRPC", peer.baseUrl(), null, "1.1"), List.of(interceptor));
            var observation = selected.streamInput(SendInputParams.builder().message(message("", "", 0))
                    .tenant("tenant-stream").build(), SpiCallOptions.defaults(),
                    new ClientCallContext(Map.of(), Map.of("X-Context", "stream-context")));
            List<TransportFrame> frames = new java.util.concurrent.CopyOnWriteArrayList<>();
            CountDownLatch completed = new CountDownLatch(1);
            observation.frames().subscribe(new Flow.Subscriber<>() {
                @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                @Override public void onNext(TransportFrame frame) { frames.add(frame); }
                @Override public void onError(Throwable failure) { completed.countDown(); }
                @Override public void onComplete() { completed.countDown(); }
            });

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertEquals(5, frames.size());
            assertInstanceOf(Task.class, frames.get(0).event());
            assertInstanceOf(org.a2aproject.sdk.spec.TaskMessageUpdateEvent.class, frames.get(1).event());
            assertTrue(frames.get(2).unknownSkipped());
            assertEquals(null, frames.get(2).event());
            assertInstanceOf(org.a2aproject.sdk.spec.TaskStatusUpdateEvent.class, frames.get(3).event());
            assertInstanceOf(TaskArtifactUpdateEvent.class, frames.get(4).event());
            JsonObject sent = peer.lastRequestBody();
            assertEquals("/tenant-stream", peer.lastPath());
            assertEquals("1.1", peer.requestHeader("A2A-Version"));
            assertEquals("stream-context", peer.requestHeader("X-Context"));
            assertEquals("stream", peer.requestHeader("X-Intercepted"));
            assertEquals("SendStreamingMessage", sent.get("method").getAsString());
            selected.close();
        }
    }

    @Test
    void finiteRawDemandDeliversIgnoredVariantAndThenTheNextKnownFrame() throws Exception {
        JsonObject knownTask = taskJson(task("task-demand", "context-demand", 1, 2));
        try (JsonRpcSseTestPeer peer = new JsonRpcSseTestPeer(requestBody -> {
            String requestId = requestBody.get("id").getAsString();
            String ignored = responseEnvelope(JsonParser.parseString("{\"futureEvent\":{}}").getAsJsonObject(),
                    requestId);
            String known = eventEnvelope("task", knownTask, requestId);
            return new SseReply(200, "text/event-stream", sseBody(ignored, known));
        })) {
            JSONRPCTransport selected = new JSONRPCTransport(peer.baseUrl(), "1.1");
            TransportObservation observation = selected.streamInput(input(""), SpiCallOptions.defaults(), null);
            List<TransportFrame> frames = new java.util.concurrent.CopyOnWriteArrayList<>();
            AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
            CountDownLatch firstFrame = new CountDownLatch(1);
            CountDownLatch secondFrame = new CountDownLatch(1);
            CountDownLatch terminal = new CountDownLatch(1);
            AtomicInteger errors = new AtomicInteger();
            observation.frames().subscribe(new Flow.Subscriber<>() {
                @Override public void onSubscribe(Flow.Subscription value) {
                    subscription.set(value);
                    value.request(1);
                }
                @Override public void onNext(TransportFrame frame) {
                    frames.add(frame);
                    (frames.size() == 1 ? firstFrame : secondFrame).countDown();
                }
                @Override public void onError(Throwable failure) {
                    errors.incrementAndGet();
                    terminal.countDown();
                }
                @Override public void onComplete() { terminal.countDown(); }
            });

            assertTrue(firstFrame.await(5, TimeUnit.SECONDS));
            assertTrue(frames.get(0).unknownSkipped());
            assertEquals(null, frames.get(0).event());
            assertFalse(secondFrame.await(100, TimeUnit.MILLISECONDS),
                    "the second raw frame must wait for another unit of downstream demand");
            subscription.get().request(1);
            assertTrue(secondFrame.await(5, TimeUnit.SECONDS),
                    "a skipped future variant must not prevent the next requested known frame");
            assertTrue(terminal.await(5, TimeUnit.SECONDS));
            assertEquals(TransportEnd.Kind.COMPLETED,
                    observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS).kind());
            assertEquals(2, frames.size());
            assertInstanceOf(Task.class, frames.get(1).event());
            assertEquals(0, errors.get());
            selected.close();
        }
    }

    @Test
    void mismatchedSelectedSseResponseIdFailsBeforePublishingFramesOrReadingLaterEvents() throws Exception {
        String wrongId = "unrelated-request";
        String first = eventEnvelope("task", taskJson(task("task-mismatch", "context-mismatch", 1, 1)), wrongId);
        String later = eventEnvelope("task", taskJson(task("task-later", "context-later", 2, 2)), wrongId);
        server.when(request().withMethod("POST").withPath("/"))
                .respond(response().withStatusCode(200).withHeader("Content-Type", "text/event-stream")
                        .withBody(sseBody(first, later)));
        JSONRPCTransport selected = new JSONRPCTransport("http://localhost:4001", "1.1");
        TransportObservation observation = selected.streamInput(input(""), SpiCallOptions.defaults(), null);
        List<TransportFrame> frames = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch terminal = new CountDownLatch(1);
        observation.frames().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(TransportFrame frame) { frames.add(frame); }
            @Override public void onError(Throwable failure) { errors.incrementAndGet(); terminal.countDown(); }
            @Override public void onComplete() { terminal.countDown(); }
        });

        TransportEnd end = observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(terminal.await(5, TimeUnit.SECONDS));
        var recorded = server.retrieveRecordedRequests(request().withMethod("POST").withPath("/"));
        String requestId = JsonParser.parseString(recorded[0].getBodyAsString()).getAsJsonObject()
                .get("id").getAsString();
        assertFalse(wrongId.equals(requestId));
        assertEquals(TransportEnd.Kind.FAILED, end.kind());
        assertTrue(frames.isEmpty(), "a mismatched response id must not publish this or later frames");
        assertEquals(1, errors.get(), "a mismatched response id must signal exactly one terminal error");
        selected.close();
    }

    @Test
    void nonSuccessHttpStatusFailsRawObservationBeforeJsonRpcResultDecode() throws Exception {
        JsonObject taskResult = new JsonObject();
        taskResult.add("task", taskJson(task("task-http-error", "context-http-error", 1, 1)));
        try (JsonRpcSseTestPeer peer = new JsonRpcSseTestPeer(requestBody -> new SseReply(500, "application/json",
                responseWithId(responseEnvelope(taskResult), requestBody, ResponseIdMode.MATCHING)))) {
            JSONRPCTransport selected = new JSONRPCTransport(peer.baseUrl(), "1.1");
            TransportObservation observation = selected.streamInput(input(""), SpiCallOptions.defaults(), null);
            List<TransportFrame> frames = new java.util.concurrent.CopyOnWriteArrayList<>();
            CountDownLatch terminal = new CountDownLatch(1);
            observation.frames().subscribe(new Flow.Subscriber<>() {
                @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                @Override public void onNext(TransportFrame frame) { frames.add(frame); }
                @Override public void onError(Throwable failure) { terminal.countDown(); }
                @Override public void onComplete() { terminal.countDown(); }
            });

            TransportEnd end = observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(terminal.await(5, TimeUnit.SECONDS));
            assertEquals(TransportEnd.Kind.FAILED, end.kind());
            assertTrue(frames.isEmpty(), "a non-success response must not publish its JSON-RPC result");
            assertInstanceOf(org.a2aproject.sdk.spec.A2AClientHTTPError.class, end.cause().getCause());
            selected.close();
        }
    }

    @Test
    void successfulHttpJsonRpcErrorEnvelopeRemainsTypedForRawObservation() throws Exception {
        String errorResponse = "{\"jsonrpc\":\"2.0\",\"id\":\"response-1\",\"error\":{\"code\":-32010,\"message\":\"generation mismatch\"}}";
        try (JsonRpcSseTestPeer peer = new JsonRpcSseTestPeer(requestBody -> new SseReply(200, "application/json",
                responseWithId(errorResponse, requestBody, ResponseIdMode.MATCHING)))) {
            JSONRPCTransport selected = new JSONRPCTransport(peer.baseUrl(), "1.1");
            TransportObservation observation = selected.streamInput(input(""), SpiCallOptions.defaults(), null);

            TransportEnd end = observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(TransportEnd.Kind.FAILED, end.kind());
            assertInstanceOf(TaskGenerationMismatchError.class, end.cause());
            selected.close();
        }
    }

    @Test
    void mismatchedSseErrorEnvelopeFailsBeforeTypedGenerationMismatchMapping() throws Exception {
        String errorResponse = "{\"jsonrpc\":\"2.0\",\"id\":\"unrelated-request\",\"error\":{\"code\":-32010,\"message\":\"generation mismatch\"}}";
        server.when(request().withMethod("POST").withPath("/"))
                .respond(response().withStatusCode(200).withHeader("Content-Type", "application/json").withBody(errorResponse));
        JSONRPCTransport selected = new JSONRPCTransport("http://localhost:4001", "1.1");
        TransportObservation observation = selected.streamInput(input(""), SpiCallOptions.defaults(), null);

        TransportEnd end = observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
        var recorded = server.retrieveRecordedRequests(request().withMethod("POST").withPath("/"));
        String requestId = JsonParser.parseString(recorded[0].getBodyAsString()).getAsJsonObject()
                .get("id").getAsString();
        assertFalse("unrelated-request".equals(requestId));
        assertEquals(TransportEnd.Kind.FAILED, end.kind());
        assertFalse(end.cause() instanceof TaskGenerationMismatchError,
                "a JSON-RPC error with a mismatched id must fail correlation before typed mapping");
        selected.close();
    }

    @Test
    void closingOneTransportDetachesOnlyItsAllUnknownHeldOpenStream() throws Exception {
        try (HeldOpenSsePeer peer = new HeldOpenSsePeer()) {
            JSONRPCTransport first = new JSONRPCTransport(new JdkA2AHttpClient(), null,
                    new AgentInterface("JSONRPC", peer.baseUrl(), null, "1.1"), null);
            JSONRPCTransport second = new JSONRPCTransport(new JdkA2AHttpClient(), null,
                    new AgentInterface("JSONRPC", peer.baseUrl(), null, "1.1"), null);
            TransportObservation firstObservation = first.streamInput(input("one"), SpiCallOptions.defaults(), null);
            TransportObservation secondObservation = second.streamInput(input("two"), SpiCallOptions.defaults(), null);
            List<TransportFrame> firstFrames = new java.util.concurrent.CopyOnWriteArrayList<>();
            List<TransportFrame> secondFrames = new java.util.concurrent.CopyOnWriteArrayList<>();
            CountDownLatch firstFrameReceived = new CountDownLatch(1);
            subscribe(firstObservation, firstFrames, firstFrameReceived);
            subscribe(secondObservation, secondFrames, new CountDownLatch(1));

            assertTrue(peer.session("/one").opened.await(5, TimeUnit.SECONDS));
            assertTrue(peer.session("/two").opened.await(5, TimeUnit.SECONDS));
            assertTrue(firstFrameReceived.await(5, TimeUnit.SECONDS));
            first.close();
            assertTrue(peer.session("/one").closed.await(5, TimeUnit.SECONDS),
                    "closing the first transport must detach its held-open SSE read");
            assertEquals(1, peer.session("/two").closed.getCount(),
                    "closing one transport must leave the other HTTP read open");
            assertEquals(1, firstFrames.size());
            assertTrue(firstFrames.get(0).unknownSkipped());
            assertEquals(null, firstFrames.get(0).event());

            peer.session("/two").release.countDown();
            TransportEnd secondEnd = secondObservation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(TransportEnd.Kind.COMPLETED, secondEnd.kind());
            assertEquals(2, secondFrames.size());
            assertTrue(secondFrames.get(0).unknownSkipped());
            assertInstanceOf(Task.class, secondFrames.get(1).event());
            second.close();
        }
    }

    private static SendInputParams input(String tenant) {
        return SendInputParams.builder().message(message("", "", 0)).tenant(tenant).build();
    }

    private static void subscribe(TransportObservation observation, List<TransportFrame> frames,
            CountDownLatch firstFrameReceived) {
        observation.frames().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(TransportFrame frame) {
                frames.add(frame);
                firstFrameReceived.countDown();
            }
            @Override public void onError(Throwable failure) { }
            @Override public void onComplete() { }
        });
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
                String unknown = responseEnvelope(JsonParser.parseString(
                        "{\"futureEvent\":{\"payload\":true}}").getAsJsonObject(), requestId);
                output.write(sseBody(unknown).getBytes(StandardCharsets.UTF_8));
                output.flush();
                session.opened.countDown();
                while (!session.release.await(50, TimeUnit.MILLISECONDS)) {
                    output.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                if ("/two".equals(path)) {
                    JsonObject taskResult = new JsonObject();
                    taskResult.add("task", JsonParser.parseString(
                            "{\"id\":\"task-two\",\"contextId\":\"context-two\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}"));
                    output.write(sseBody(responseEnvelope(taskResult, requestId)).getBytes(StandardCharsets.UTF_8));
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

    private static final class JsonRpcSseTestPeer implements AutoCloseable {
        private final HttpServer server;
        private final Function<JsonObject, SseReply> responseFactory;
        private volatile JsonObject lastRequestBody;
        private volatile com.sun.net.httpserver.Headers lastHeaders;
        private volatile String lastPath;

        JsonRpcSseTestPeer(Function<JsonObject, SseReply> responseFactory) throws IOException {
            this.responseFactory = responseFactory;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        JsonObject lastRequestBody() {
            return java.util.Objects.requireNonNull(lastRequestBody).deepCopy();
        }

        String requestHeader(String name) {
            return lastHeaders == null ? null : lastHeaders.getFirst(name);
        }

        String lastPath() {
            return lastPath;
        }

        private void handle(HttpExchange exchange) throws IOException {
            JsonObject requestBody = JsonParser.parseString(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            lastRequestBody = requestBody.deepCopy();
            lastHeaders = exchange.getRequestHeaders();
            lastPath = exchange.getRequestURI().getPath();
            SseReply reply = responseFactory.apply(requestBody.deepCopy());
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", reply.contentType());
            exchange.sendResponseHeaders(reply.status(), body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private record SseReply(int status, String contentType, String body) {
    }

    private static final class JsonRpcTestPeer implements AutoCloseable {
        private final HttpServer server;
        private final Function<JsonObject, String> responseFactory;
        private final List<String> requestIds = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile JsonObject lastRequestBody;
        private volatile com.sun.net.httpserver.Headers lastHeaders;
        private volatile int requestCount;

        JsonRpcTestPeer(Function<JsonObject, String> responseFactory) throws IOException {
            this.responseFactory = responseFactory;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        int requestCount() {
            return requestCount;
        }

        String requestHeader(String name) {
            return lastHeaders == null ? null : lastHeaders.getFirst(name);
        }

        JsonObject lastRequestBody() {
            return java.util.Objects.requireNonNull(lastRequestBody).deepCopy();
        }

        List<String> requestIds() {
            return List.copyOf(requestIds);
        }

        private void handle(HttpExchange exchange) throws IOException {
            JsonObject requestBody = JsonParser.parseString(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            requestIds.add(requestBody.get("id").getAsString());
            lastRequestBody = requestBody.deepCopy();
            lastHeaders = exchange.getRequestHeaders();
            requestCount++;
            byte[] body = responseFactory.apply(requestBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class HeldOpenSession {
        private final CountDownLatch opened = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
    }

    private static String eventEnvelope(String variant, JsonObject payload) {
        JsonObject result = new JsonObject();
        result.add(variant, payload);
        return responseEnvelope(result);
    }

    private static String eventEnvelope(String variant, JsonObject payload, String id) {
        JsonObject result = new JsonObject();
        result.add(variant, payload);
        return responseEnvelope(result, id);
    }

    private static String sseBodyWithMatchingId(JsonObject requestBody, String... envelopes) {
        return sseBody(java.util.Arrays.stream(envelopes)
                .map(envelope -> responseWithId(envelope, requestBody, ResponseIdMode.MATCHING))
                .toArray(String[]::new));
    }

    private static String sseBody(String... envelopes) {
        return java.util.Arrays.stream(envelopes).map(envelope -> "data: " + envelope + "\n\n")
                .collect(java.util.stream.Collectors.joining());
    }

    private static Task task(String id, String contextId, long firstGeneration, long lastGeneration) {
        return new Task(id, contextId, new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(), List.of(), Map.of(),
                lastGeneration, List.of(
                        new TimelineEntry(firstGeneration, new TimelineEntry.MessageValue(message(id, contextId, firstGeneration))),
                        new TimelineEntry(lastGeneration, new TimelineEntry.MessageValue(message(id, contextId, lastGeneration)))));
    }

    private static Message message(String taskId, String contextId, long generation) {
        return Message.builder().messageId(taskId + "-m" + generation).taskId(taskId).contextId(contextId)
                .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("generation-" + generation))).build();
    }

    private static JsonObject taskJson(Task task) throws org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException {
        return JsonParser.parseString(JsonUtil.toJson(task)).getAsJsonObject();
    }

    private static void addUnknownTimelineEntry(JsonObject task, long generation) {
        JsonObject unknown = new JsonObject();
        unknown.addProperty("generation", Long.toString(generation));
        unknown.add("futureKind", new JsonObject());
        JsonArray timeline = task.getAsJsonArray("timeline");
        JsonArray updated = new JsonArray();
        updated.add(timeline.get(0));
        updated.add(unknown);
        updated.add(timeline.get(1));
        task.add("timeline", updated);
    }

    private static String envelope(Task task) throws org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException {
        JsonObject taskJson = taskJson(task);
        addUnknownTimelineEntry(taskJson, task.timeline().get(0).generation() + 1);
        return responseEnvelope(taskJson);
    }

    private static String sendTaskEnvelope(Task task)
            throws org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException {
        JsonObject result = new JsonObject();
        JsonObject taskJson = taskJson(task);
        addUnknownTimelineEntry(taskJson, task.timeline().get(0).generation() + 1);
        result.add("task", taskJson);
        return responseEnvelope(result);
    }

    private static String responseEnvelope(JsonObject result) {
        return responseEnvelope(result, "response-1");
    }

    private static String responseEnvelope(JsonObject result, String id) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        envelope.addProperty("id", id);
        envelope.add("result", result);
        return envelope.toString();
    }

    private static String echoResponse(String response, JsonObject requestBody) {
        return responseWithId(response, requestBody, ResponseIdMode.MATCHING);
    }

    private static String responseWithId(String response, JsonObject requestBody, ResponseIdMode mode) {
        JsonObject envelope = JsonParser.parseString(response).getAsJsonObject();
        switch (mode) {
            case MATCHING -> envelope.add("id", requestBody.get("id").deepCopy());
            case WRONG -> envelope.addProperty("id", "another-request");
            case MISSING -> envelope.remove("id");
            case NULL -> envelope.add("id", com.google.gson.JsonNull.INSTANCE);
            case OBJECT -> envelope.add("id", new JsonObject());
            case BOOLEAN -> envelope.addProperty("id", true);
            case ARRAY -> envelope.add("id", new JsonArray());
        }
        return envelope.toString();
    }

    private enum UnaryOperation {
        GET,
        LIST,
        ASYNC_GET,
        SEND
    }

    private enum ResponseIdMode {
        MATCHING,
        WRONG,
        MISSING,
        NULL,
        OBJECT,
        BOOLEAN,
        ARRAY
    }
}
