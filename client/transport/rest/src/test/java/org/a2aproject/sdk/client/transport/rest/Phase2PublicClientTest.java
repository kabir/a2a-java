package org.a2aproject.sdk.client.transport.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TransportEnd;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.A2AClientHTTPError;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
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
    void wrappedOneOneSyncReadsRetainIndependentUnknownTimelineEvidence() throws Exception {
        JsonObject taskA = taskJson("task-A", 7, 9);
        addUnknown(taskA, 8);
        JsonObject taskB = taskJson("task-B", 17, 19);
        addUnknown(taskB, 18);
        respond("/tasks/task-A", taskA);
        respond("/tasks", listResult(List.of(taskA, taskB)));

        VersionedClientTransport selected = transport("1.1");
        var decodedGet = selected.getTaskDecoded(new TaskQueryParams("task-A", null, null, null, 0), null);
        var decodedList = selected.listTasksDecoded(ListTasksParams.builder().pageSize(2).timelineLength(0).build(), null);

        assertEquals(List.of(7L, 9L), generations(decodedGet.value()));
        assertEquals(List.of(8L), decodedGet.taskEvidence().get(0).skippedTimelineGenerations());
        assertTrue(decodedGet.taskEvidence().get(0).unknownSkipped());
        assertEquals(List.of("task-A", "task-B"), decodedList.value().tasks().stream().map(Task::id).toList());
        assertEquals(List.of(8L), decodedList.taskEvidence().get(0).skippedTimelineGenerations());
        assertEquals(List.of(18L), decodedList.taskEvidence().get(1).skippedTimelineGenerations());
        assertTrue(decodedList.taskEvidence().get(0).unknownSkipped());
        assertTrue(decodedList.taskEvidence().get(1).unknownSkipped());
        assertEquals(12, decodedList.value().totalSize());
        assertEquals(2, decodedList.value().pageSize());
        assertEquals("page-2", decodedList.value().nextPageToken());
        server.verify(request().withMethod("GET").withPath("/tasks/task-A")
                .withQueryStringParameter("timelineLength", "0"), exactly(1));
        server.verify(request().withMethod("GET").withPath("/tasks")
                .withQueryStringParameter("timelineLength", "0"), exactly(1));

    }

    @Test
    void wrappedOneOneAsyncGetRetainsUnknownTimelineEvidence() throws Exception {
        JsonObject taskA = taskJson("task-A", 7, 9);
        addUnknown(taskA, 8);
        respond("/tasks/task-A", taskA);

        VersionedClientTransport selected = transport("1.1");
        var asyncGet = selected.getTaskAsync(new TaskQueryParams("task-A", null, null, null, 0),
                new SpiCallOptions(Instant.now().plusSeconds(5), 16, 4096), null);
        var asyncResult = asyncGet.result().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals("task-A", asyncResult.value().id());
        assertEquals(List.of(8L), asyncResult.taskEvidence().get(0).skippedTimelineGenerations());
        assertTrue(asyncResult.taskEvidence().get(0).unknownSkipped());
        server.verify(request().withMethod("GET").withPath("/tasks/task-A")
                .withQueryStringParameter("timelineLength", "0"), exactly(1));
    }

    @Test
    void wrappedOneOneAsyncGetPreservesRestHttpErrorMapping() throws Exception {
        String errorBody = "{\"message\":\"missing task\"}";
        server.when(request().withMethod("GET").withPath("/tasks/task-missing"))
                .respond(response().withStatusCode(404).withBody(errorBody));

        var call = transport("1.1").getTaskAsync(new TaskQueryParams("task-missing"),
                SpiCallOptions.defaults(), null);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> call.result().toCompletableFuture().get(5, TimeUnit.SECONDS));
        A2AClientException clientFailure = assertInstanceOf(A2AClientException.class, failure.getCause());
        A2AClientHTTPError httpError = assertInstanceOf(A2AClientHTTPError.class, clientFailure.getCause());
        assertEquals(404, httpError.getCode());
        assertEquals(errorBody, httpError.getResponseBody());
    }

    @Test
    void asyncGetPreservesTenantAndInterceptorHeaders() throws Exception {
        JsonObject task = taskJson("task-A", 7, 9);
        addUnknown(task, 8);
        server.when(request().withMethod("GET").withPath("/tenant/tasks/task-A")
                        .withQueryStringParameter("timelineLength", "0")
                        .withHeader("X-Async-Get", "intercepted"))
                .respond(response().withStatusCode(200).withBody(task.toString()));
        ClientCallInterceptor interceptor = new ClientCallInterceptor() {
            @Override
            public PayloadAndHeaders intercept(String methodName, @Nullable Object payload, Map<String, String> headers,
                    @Nullable AgentCard agentCard, @Nullable ClientCallContext context) {
                return new PayloadAndHeaders(payload, Map.of("X-Async-Get", "intercepted"));
            }
        };

        var call = transport(new JdkA2AHttpClient(), "1.1", List.of(interceptor))
                .getTaskAsync(new TaskQueryParams("task-A", null, "tenant", null, 0),
                        SpiCallOptions.defaults(), null);
        assertEquals("task-A", call.result().toCompletableFuture().get(5, TimeUnit.SECONDS).value().id());
        server.verify(request().withMethod("GET").withPath("/tenant/tasks/task-A")
                .withQueryStringParameter("timelineLength", "0")
                .withHeader("X-Async-Get", "intercepted"), exactly(1));
    }

    @Test
    void cancellingAsyncGetCancelsUnderlyingHttpRequest() throws Exception {
        A2AHttpClient httpClient = mock(A2AHttpClient.class);
        A2AHttpClient.GetBuilder getBuilder = mock(A2AHttpClient.GetBuilder.class, RETURNS_SELF);
        A2AHttpClient.AsyncGetCall requestCall = mock(A2AHttpClient.AsyncGetCall.class);
        when(httpClient.createGet()).thenReturn(getBuilder);
        when(getBuilder.getAsyncResponse()).thenReturn(requestCall);
        when(requestCall.response()).thenReturn(new CompletableFuture<>());

        var call = transport(httpClient, "1.1").getTaskAsync(new TaskQueryParams("task-A"),
                SpiCallOptions.defaults(), null);
        call.cancel();

        verify(requestCall).cancel();
        assertTrue(call.result().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void asynchronousGetRejectsOneZeroBeforeCreatingHttpRequest() throws Exception {
        A2AHttpClient httpClient = mock(A2AHttpClient.class);

        assertThrows(UnsupportedOperationException.class,
                () -> transport(httpClient, "1.0").getTaskAsync(new TaskQueryParams("task-legacy"),
                        SpiCallOptions.defaults(), null));

        verify(httpClient, never()).createGet();
    }

    @Test
    void selectedOneOneTaskReadsRejectUnsupportedCurrentGenerationBeforeIo() throws Exception {
        TaskQueryParams withCursor = new TaskQueryParams("task-cursor", null, null, 4L, null);
        VersionedClientTransport selected = transport("1.1");

        assertThrows(UnsupportedOperationException.class, () -> selected.getTaskDecoded(withCursor, null));
        assertThrows(UnsupportedOperationException.class,
                () -> selected.getTaskAsync(withCursor, SpiCallOptions.defaults(), null));

        server.verify(request().withMethod("GET").withPath("/tasks/task-cursor"), exactly(0));
    }

    @Test
    void malformedKnownTimelinePayloadFailsInsteadOfBecomingSkippedEvidence() throws Exception {
        JsonObject malformedTask = taskJson("task-malformed", 7, 9);
        JsonObject malformedKnown = new JsonObject();
        malformedKnown.addProperty("generation", "8");
        malformedKnown.add("message", new JsonObject());
        malformedTask.getAsJsonArray("timeline").set(1, malformedKnown);
        respond("/tasks/task-malformed", malformedTask);

        A2AClientException failure = assertThrows(A2AClientException.class,
                () -> transport("1.1").getTaskDecoded(new TaskQueryParams("task-malformed"), null));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    @Test
    void explicitlySelectedOneZeroKeepsLegacyGetPath() throws Exception {
        JsonObject legacyTask = new JsonObject();
        legacyTask.addProperty("id", "task-legacy");
        legacyTask.addProperty("contextId", "context-task-legacy");
        legacyTask.add("status", JsonParser.parseString("{\"state\":\"TASK_STATE_WORKING\"}"));
        respond("/tasks/task-legacy", legacyTask);

        assertEquals("task-legacy", transport("1.0").getTask(
                new TaskQueryParams("task-legacy", null, null, null, 0), null).id());
        server.verify(request().withMethod("GET").withPath("/tasks/task-legacy")
                .withQueryStringParameter("timelineLength", "0"), exactly(0));
    }

    @Test
    void unarySendRetainsTaskTimelineEvidenceAndAllInputFields() throws Exception {
        JsonObject task = taskJson("task-send", 37, 39);
        addUnknown(task, 38);
        JsonObject responseBody = new JsonObject();
        responseBody.add("task", task);
        server.when(request().withMethod("POST").withPath("/tenant-send/message:send")
                        .withHeader("A2A-Version", "1.1")
                        .withHeader("X-Context", "from-context")
                        .withHeader("X-Intercepted", "yes"))
                .respond(response().withStatusCode(200).withBody(responseBody.toString()));
        ClientCallInterceptor interceptor = new ClientCallInterceptor() {
            @Override
            public PayloadAndHeaders intercept(String method, @Nullable Object payload, Map<String, String> headers,
                    @Nullable AgentCard card, @Nullable ClientCallContext context) {
                assertEquals("SendMessage", method);
                Map<String, String> result = new java.util.LinkedHashMap<>(headers);
                result.put("X-Intercepted", "yes");
                return new PayloadAndHeaders(payload, result);
            }
        };
        TaskArtifactUpdateEvent update = new TaskArtifactUpdateEvent("task-send",
                new Artifact("artifact-send", "name", "description", List.of(new TextPart("artifact")),
                        Map.of("artifact-meta", "kept"), List.of()),
                "context-send", true, false, Map.of("source", "client"), 88);
        SendInputParams input = SendInputParams.builder()
                .message(Message.builder().messageId("task-send-m36").taskId("task-send")
                        .contextId("context-send").role(Message.Role.ROLE_USER)
                        .parts(List.of(new TextPart("generation-36"))).build())
                .artifactUpdates(List.of(update))
                .configuration(MessageSendConfiguration.builder().acceptedOutputModes(List.of("text/plain"))
                        .historyLength(2).returnImmediately(true).ifGenerationMatch(39L).timelineLength(4).build())
                .metadata(Map.of("trace", "send-trace"))
                .tenant("tenant-send")
                .build();

        var call = transport(new JdkA2AHttpClient(), "1.1", List.of(interceptor)).sendInput(input,
                SpiCallOptions.defaults(), new ClientCallContext(Map.of(), Map.of("X-Context", "from-context")));
        var result = call.result().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertInstanceOf(Task.class, result.value());
        assertEquals("task-send", ((Task) result.value()).id());
        assertEquals(List.of(38L), result.taskEvidence().get(0).skippedTimelineGenerations());
        assertTrue(result.taskEvidence().get(0).unknownSkipped());
        var recorded = server.retrieveRecordedRequests(request().withMethod("POST")
                .withPath("/tenant-send/message:send"));
        assertEquals(1, recorded.length);
        JsonObject body = JsonParser.parseString(recorded[0].getBodyAsString()).getAsJsonObject();
        assertEquals("task-send-m36", body.getAsJsonObject("message").get("messageId").getAsString());
        assertEquals("tenant-send", body.get("tenant").getAsString());
        assertEquals("send-trace", body.getAsJsonObject("metadata").get("trace").getAsString());
        JsonObject configuration = body.getAsJsonObject("configuration");
        assertEquals("39", configuration.get("ifGenerationMatch").getAsString());
        assertEquals(4, configuration.get("timelineLength").getAsInt());
        assertEquals(2, configuration.get("historyLength").getAsInt());
        assertTrue(configuration.get("returnImmediately").getAsBoolean());
        assertEquals("text/plain", configuration.getAsJsonArray("acceptedOutputModes").get(0).getAsString());
        JsonObject artifactUpdate = body.getAsJsonArray("artifactUpdates").get(0).getAsJsonObject();
        assertEquals("task-send", artifactUpdate.get("taskId").getAsString());
        assertEquals("context-send", artifactUpdate.get("contextId").getAsString());
        assertTrue(artifactUpdate.get("append").getAsBoolean());
        assertFalse(artifactUpdate.has("lastChunk"));
        assertEquals("client", artifactUpdate.getAsJsonObject("metadata").get("source").getAsString());
        assertEquals("artifact-send", artifactUpdate.getAsJsonObject("artifact").get("artifactId").getAsString());
        assertEquals("kept", artifactUpdate.getAsJsonObject("artifact").getAsJsonObject("metadata")
                .get("artifact-meta").getAsString());
        server.verify(request().withMethod("POST").withPath("/tenant-send/message:send")
                .withHeader("A2A-Version", "1.1").withHeader("X-Context", "from-context")
                .withHeader("X-Intercepted", "yes"), exactly(1));
    }

    @Test
    void unarySendDirectMessageHasNoTaskEvidence() throws Exception {
        JsonObject responseBody = new JsonObject();
        responseBody.add("message", JsonParser.parseString(JsonUtil.toJson(message("", 0))));
        server.when(request().withMethod("POST").withPath("/message:send"))
                .respond(response().withStatusCode(200).withBody(responseBody.toString()));

        var result = transport("1.1").sendInput(SendInputParams.builder().message(message("", 0)).build(),
                SpiCallOptions.defaults(), null).result().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertInstanceOf(Message.class, result.value());
        assertTrue(result.taskEvidence().isEmpty());
        server.verify(request().withMethod("POST").withPath("/message:send"), exactly(1));
    }

    @Test
    void unarySendMapsHttp409GenerationMismatchWithoutRetry() throws Exception {
        String errorBody = "{\"error\":{\"code\":409,\"message\":\"generation mismatch\",\"details\":["
                + "{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\","
                + "\"reason\":\"TASK_GENERATION_MISMATCH\",\"metadata\":{\"taskId\":\"task-send\"}}]}}";
        server.when(request().withMethod("POST").withPath("/message:send"))
                .respond(response().withStatusCode(409).withBody(errorBody));

        var call = transport("1.1").sendInput(SendInputParams.builder().message(message("", 0)).build(),
                SpiCallOptions.defaults(), null);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> call.result().toCompletableFuture().get(5, TimeUnit.SECONDS));
        A2AClientException clientFailure = assertInstanceOf(A2AClientException.class, failure.getCause());
        assertInstanceOf(TaskGenerationMismatchError.class, clientFailure.getCause());
        server.verify(request().withMethod("POST").withPath("/message:send"), exactly(1));
    }

    @Test
    void unarySendRejectsMalformedKnownTimelineEntry() throws Exception {
        JsonObject malformedTask = taskJson("task-malformed-send", 7, 9);
        JsonObject malformedKnown = new JsonObject();
        malformedKnown.addProperty("generation", "8");
        malformedKnown.add("message", new JsonObject());
        malformedTask.getAsJsonArray("timeline").set(1, malformedKnown);
        JsonObject responseBody = new JsonObject();
        responseBody.add("task", malformedTask);
        server.when(request().withMethod("POST").withPath("/message:send"))
                .respond(response().withStatusCode(200).withBody(responseBody.toString()));

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> transport("1.1").sendInput(SendInputParams.builder().message(message("", 0)).build(),
                        SpiCallOptions.defaults(), null).result().toCompletableFuture().get(5, TimeUnit.SECONDS));
        A2AClientException clientFailure = assertInstanceOf(A2AClientException.class, failure.getCause());
        assertInstanceOf(IllegalArgumentException.class, clientFailure.getCause());
    }

    @Test
    void cancellingUnarySendCancelsUnderlyingHttpRequest() throws Exception {
        A2AHttpClient httpClient = mock(A2AHttpClient.class);
        A2AHttpClient.PostBuilder postBuilder = mock(A2AHttpClient.PostBuilder.class, RETURNS_SELF);
        A2AHttpClient.AsyncPostCall requestCall = mock(A2AHttpClient.AsyncPostCall.class);
        when(httpClient.createPost()).thenReturn(postBuilder);
        when(postBuilder.postAsyncResponse()).thenReturn(requestCall);
        when(requestCall.response()).thenReturn(new CompletableFuture<>());

        var call = transport(httpClient, "1.1").sendInput(
                SendInputParams.builder().message(message("", 0)).build(), SpiCallOptions.defaults(), null);
        call.cancel();

        verify(requestCall).cancel();
        assertTrue(call.result().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void unarySendRejectsOneZeroBeforeCreatingHttpRequest() throws Exception {
        A2AHttpClient httpClient = mock(A2AHttpClient.class);

        assertThrows(UnsupportedOperationException.class,
                () -> transport(httpClient, "1.0").sendInput(
                        SendInputParams.builder().message(message("", 0)).build(), SpiCallOptions.defaults(), null));

        verify(httpClient, never()).createPost();
    }

    @Test
    void oneOneStreamInputRetainsIgnoredFutureFrameThenKnownMessageUnderFiniteDemand() throws Exception {
        String unknown = "{\"futureEvent\":{\"token\":1}}";
        String known = JsonFormat.printer().omittingInsignificantWhitespace()
                .print(org.a2aproject.sdk.grpc.StreamResponse.newBuilder()
                .setMessage(org.a2aproject.sdk.grpc.mapper.MessageMapper.INSTANCE.toProto(message("stream-task", 4)))
                .build());
        respondSse("POST", "/message:stream", sse(unknown, known));

        TransportObservation observation = transport("1.1").streamInput(
                SendInputParams.builder().message(message("stream-task", 4)).build(),
                SpiCallOptions.defaults(), null);
        BlockingFrameSubscriber subscriber = new BlockingFrameSubscriber();
        observation.frames().subscribe(subscriber);

        TransportFrame future = subscriber.next();
        assertTrue(future.unknownSkipped());
        assertEquals(null, future.event());
        assertEquals(null, subscriber.poll());

        subscriber.request(1);
        TransportFrame event = subscriber.next();
        assertInstanceOf(Message.class, event.event());
        assertEquals("stream-task-m4", ((Message) event.event()).messageId());
        assertEquals(TransportEnd.Kind.COMPLETED, observation.completion().toCompletableFuture()
                .get(5, TimeUnit.SECONDS).kind());
        server.verify(request().withMethod("POST").withPath("/message:stream"), exactly(1));
    }

    @Test
    void oneOneStreamInputFailsMalformedKnownFrameAndKeepsStreamErrorAsLocalRejection() throws Exception {
        String malformed = "{\"message\":{\"messageId\":\"m\"}}";
        respondSse("POST", "/message:stream", sse(malformed));
        TransportObservation malformedObservation = transport("1.1").streamInput(
                SendInputParams.builder().message(message("stream-task", 4)).build(),
                SpiCallOptions.defaults(), null);
        BlockingFrameSubscriber malformedSubscriber = new BlockingFrameSubscriber();
        malformedObservation.frames().subscribe(malformedSubscriber);
        assertInstanceOf(IllegalArgumentException.class, malformedSubscriber.failure());
        assertEquals(TransportEnd.Kind.FAILED, malformedObservation.completion().toCompletableFuture()
                .get(5, TimeUnit.SECONDS).kind());

        server.clear(request().withMethod("POST").withPath("/message:stream"));
        String rejection = JsonFormat.printer().omittingInsignificantWhitespace()
                .print(org.a2aproject.sdk.grpc.StreamResponse.newBuilder()
                .setError(org.a2aproject.sdk.grpc.StreamError.newBuilder().setCode("ABORTED")
                        .setMessage("local rejection"))
                .build());
        respondSse("POST", "/message:stream", sse(rejection));
        TransportObservation rejectionObservation = transport("1.1").streamInput(
                SendInputParams.builder().message(message("stream-task", 4)).build(),
                SpiCallOptions.defaults(), null);
        BlockingFrameSubscriber rejectionSubscriber = new BlockingFrameSubscriber();
        rejectionObservation.frames().subscribe(rejectionSubscriber);
        TransportFrame frame = rejectionSubscriber.next();
        assertEquals("ABORTED", frame.streamError().code());
        assertEquals("local rejection", frame.streamError().message());
        assertEquals(TransportEnd.Kind.COMPLETED, rejectionObservation.completion().toCompletableFuture()
                .get(5, TimeUnit.SECONDS).kind());
    }

    @Test
    void closingRestTransportCancelsHeldOpenAllUnknownStreamWithoutOwningInjectedClient() throws Exception {
        A2AHttpClient httpClient = mock(A2AHttpClient.class);
        A2AHttpClient.PostBuilder postBuilder = mock(A2AHttpClient.PostBuilder.class, RETURNS_SELF);
        A2AHttpClient.AsyncSSECall requestCall = mock(A2AHttpClient.AsyncSSECall.class);
        AtomicReference<java.util.function.Consumer<org.a2aproject.sdk.client.http.ServerSentEvent>> messages =
                new AtomicReference<>();
        when(httpClient.createPost()).thenReturn(postBuilder);
        when(postBuilder.postAsyncSSECallStatusAware(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                    messages.set(invocation.getArgument(0));
                    return requestCall;
                });
        when(requestCall.completion()).thenReturn(new CompletableFuture<>());

        RestTransport rest = (RestTransport) transport(httpClient, "1.1");
        TransportObservation observation = rest.streamInput(
                SendInputParams.builder().message(message("stream-task", 4)).build(),
                SpiCallOptions.defaults(), null);
        BlockingFrameSubscriber subscriber = new BlockingFrameSubscriber();
        observation.frames().subscribe(subscriber);
        messages.get().accept(new org.a2aproject.sdk.client.http.ServerSentEvent("{\"futureEvent\":{}}"));
        assertTrue(subscriber.next().unknownSkipped());
        assertFalse(observation.completion().toCompletableFuture().isDone(),
                "an all-unknown SSE connection must stay open until cancellation");
        rest.close();

        assertEquals(TransportEnd.Kind.CANCELLED,
                observation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS).kind());
        verify(requestCall).cancel();
        verify(httpClient, never()).createDelete();
    }

    @Test
    void closingOneRestTransportLeavesAnotherSharingTheInjectedHttpClientRunning() throws Exception {
        A2AHttpClient httpClient = mock(A2AHttpClient.class);
        A2AHttpClient.PostBuilder firstBuilder = mock(A2AHttpClient.PostBuilder.class, RETURNS_SELF);
        A2AHttpClient.PostBuilder secondBuilder = mock(A2AHttpClient.PostBuilder.class, RETURNS_SELF);
        A2AHttpClient.AsyncSSECall firstCall = mock(A2AHttpClient.AsyncSSECall.class);
        A2AHttpClient.AsyncSSECall secondCall = mock(A2AHttpClient.AsyncSSECall.class);
        when(httpClient.createPost()).thenReturn(firstBuilder, secondBuilder);
        when(firstBuilder.postAsyncSSECallStatusAware(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(firstCall);
        when(secondBuilder.postAsyncSSECallStatusAware(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(secondCall);
        when(firstCall.completion()).thenReturn(new CompletableFuture<>());
        when(secondCall.completion()).thenReturn(new CompletableFuture<>());

        RestTransport first = (RestTransport) transport(httpClient, "1.1");
        RestTransport second = (RestTransport) transport(httpClient, "1.1");
        TransportObservation firstObservation = first.streamInput(
                SendInputParams.builder().message(message("task-one", 0)).build(), SpiCallOptions.defaults(), null);
        TransportObservation secondObservation = second.streamInput(
                SendInputParams.builder().message(message("task-two", 0)).build(), SpiCallOptions.defaults(), null);

        first.close();

        verify(firstCall).cancel();
        verify(secondCall, never()).cancel();
        assertFalse(secondObservation.completion().toCompletableFuture().isDone());
        second.close();
        assertEquals(TransportEnd.Kind.CANCELLED,
                firstObservation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS).kind());
        assertEquals(TransportEnd.Kind.CANCELLED,
                secondObservation.completion().toCompletableFuture().get(5, TimeUnit.SECONDS).kind());
        verify(secondCall).cancel();
    }

    @Test
    void oneOneSubscribeUsesGetAndVersionedFramesWhileOneZeroKeepsPostListener() throws Exception {
        String unknown = "{\"futureEvent\":{\"token\":1}}";
        String known = JsonFormat.printer().omittingInsignificantWhitespace()
                .print(org.a2aproject.sdk.grpc.StreamResponse.newBuilder()
                .setMessage(org.a2aproject.sdk.grpc.mapper.MessageMapper.INSTANCE.toProto(message("task-sub", 0)))
                .build());
        respondSse("GET", "/tasks/task-sub:subscribe", sse(unknown, known));
        CountDownLatch received = new CountDownLatch(1);
        List<StreamingEventKind> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        transport("1.1").subscribeToTask(new TaskIdParams("task-sub"), event -> {
            events.add(event);
            received.countDown();
        }, failure::set, null);
        assertTrue(received.await(5, TimeUnit.SECONDS), "subscription failure: " + failure.get());
        assertEquals(1, events.size());
        assertInstanceOf(Message.class, events.get(0));
        assertEquals(null, failure.get());
        server.verify(request().withMethod("GET").withPath("/tasks/task-sub:subscribe"), exactly(1));

        respondSse("POST", "/tasks/task-legacy:subscribe", sse(known));
        CountDownLatch legacyComplete = new CountDownLatch(1);
        transport("1.0").subscribeToTask(new TaskIdParams("task-legacy"), event -> { },
                error -> legacyComplete.countDown(), null);
        assertTrue(legacyComplete.await(5, TimeUnit.SECONDS));
        server.verify(request().withMethod("POST").withPath("/tasks/task-legacy:subscribe"), exactly(1));
    }

    @Test
    void oneOneSubscribeMapsHttp409BodyToTypedGenerationMismatchWithoutSseDecoding() throws Exception {
        String errorBody = "{\"error\":{\"code\":409,\"message\":\"generation mismatch\",\"details\":["
                + "{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\","
                + "\"reason\":\"TASK_GENERATION_MISMATCH\",\"metadata\":{\"taskId\":\"task-sub\"}}]}}";
        server.when(request().withMethod("GET").withPath("/tasks/task-sub:subscribe"))
                .respond(response().withStatusCode(409).withBody(errorBody));
        CountDownLatch failed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        transport("1.1").subscribeToTask(new TaskIdParams("task-sub"), event -> {
            throw new AssertionError("non-2xx body must not be decoded as an SSE event");
        }, error -> {
            failure.set(error);
            failed.countDown();
        }, null);

        assertTrue(failed.await(5, TimeUnit.SECONDS));
        A2AClientException clientFailure = assertInstanceOf(A2AClientException.class, failure.get());
        assertInstanceOf(TaskGenerationMismatchError.class, clientFailure.getCause());
        server.verify(request().withMethod("GET").withPath("/tasks/task-sub:subscribe"), exactly(1));
    }

    @Test
    void closingOneOneCallbackSubscriptionDoesNotReportSuccessfulCompletion() throws Exception {
        A2AHttpClient httpClient = mock(A2AHttpClient.class);
        A2AHttpClient.GetBuilder getBuilder = mock(A2AHttpClient.GetBuilder.class, RETURNS_SELF);
        A2AHttpClient.AsyncSSECall requestCall = mock(A2AHttpClient.AsyncSSECall.class);
        when(httpClient.createGet()).thenReturn(getBuilder);
        when(getBuilder.getAsyncSSECallStatusAware(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(requestCall);
        when(requestCall.completion()).thenReturn(new CompletableFuture<>());
        AtomicInteger terminalCallbacks = new AtomicInteger();
        RestTransport rest = (RestTransport) transport(httpClient, "1.1");

        rest.subscribeToTask(new TaskIdParams("task-sub"), event -> { }, error -> terminalCallbacks.incrementAndGet(), null);
        rest.close();

        verify(requestCall).cancel();
        assertEquals(0, terminalCallbacks.get());
    }

    private void respond(String path, JsonObject body) {
        server.when(request().withMethod("GET").withPath(path))
                .respond(response().withStatusCode(200).withBody(body.toString()));
    }

    private void respondSse(String method, String path, String body) {
        server.when(request().withMethod(method).withPath(path))
                .respond(response().withStatusCode(200).withHeader("Content-Type", "text/event-stream")
                        .withBody(body));
    }

    private static String sse(String... data) {
        return java.util.Arrays.stream(data).map(value -> "data: " + value + "\n\n")
                .collect(java.util.stream.Collectors.joining());
    }

    private static VersionedClientTransport transport(String version) {
        return transport(new JdkA2AHttpClient(), version);
    }

    private static VersionedClientTransport transport(A2AHttpClient httpClient, String version) {
        return transport(httpClient, version, null);
    }

    private static VersionedClientTransport transport(A2AHttpClient httpClient, String version,
            List<ClientCallInterceptor> interceptors) {
        AgentInterface descriptor = new AgentInterface("HTTP+JSON", "http://localhost:4001", null, version);
        AgentCard card = AgentCard.builder().name("test").description("test").version("1")
                .supportedInterfaces(List.of(descriptor))
                .capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
        return new RestTransport(httpClient, card, descriptor, interceptors);
    }

    private static JsonObject taskJson(String id, long first, long last) throws Exception {
        Task task = new Task(id, "context-" + id, new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(), List.of(),
                Map.of(), last, List.of(
                        new TimelineEntry(first, new TimelineEntry.MessageValue(message(id, first))),
                        new TimelineEntry(last, new TimelineEntry.MessageValue(message(id, last)))));
        return JsonParser.parseString(JsonUtil.toJson(task)).getAsJsonObject();
    }

    private static Message message(String id, long generation) {
        return Message.builder().messageId(id + "-m" + generation).taskId(id).contextId("context-" + id)
                .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("generation-" + generation))).build();
    }

    private static JsonObject listResult(List<JsonObject> taskValues) {
        JsonObject result = new JsonObject();
        JsonArray tasks = new JsonArray();
        taskValues.forEach(tasks::add);
        result.add("tasks", tasks);
        result.addProperty("totalSize", 12);
        result.addProperty("pageSize", 2);
        result.addProperty("nextPageToken", "page-2");
        return result;
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

    private static List<Long> generations(Task task) {
        return task.timeline().stream().map(TimelineEntry::generation).toList();
    }

    private static final class BlockingFrameSubscriber implements Flow.Subscriber<TransportFrame> {
        private final java.util.concurrent.BlockingQueue<TransportFrame> frames =
                new java.util.concurrent.LinkedBlockingQueue<>();
        private final CountDownLatch terminal = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription.set(subscription);
            subscription.request(1);
        }

        @Override
        public void onNext(TransportFrame frame) {
            frames.add(frame);
        }

        @Override
        public void onError(Throwable throwable) {
            failure.set(throwable);
            terminal.countDown();
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }

        private TransportFrame next() throws InterruptedException {
            TransportFrame frame = frames.poll(5, TimeUnit.SECONDS);
            assertTrue(frame != null, "expected a transport frame; observation failure: " + failure.get());
            return frame;
        }

        private TransportFrame poll() {
            return frames.poll();
        }

        private void request(long count) {
            subscription.get().request(count);
        }

        private Throwable failure() throws InterruptedException {
            assertTrue(terminal.await(5, TimeUnit.SECONDS), "observation did not terminate");
            return failure.get();
        }
    }
}
