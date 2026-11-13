package org.a2aproject.sdk.server.tasks;

import static org.a2aproject.sdk.client.http.A2AHttpClient.APPLICATION_JSON;
import static org.a2aproject.sdk.client.http.A2AHttpClient.CONTENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.enterprise.inject.Instance;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.client.http.ServerSentEvent;
import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.AuthenticationInfo;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.a2aproject.sdk.spec.TextPart;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PushNotificationSenderTest {

    @Test
    void actualTaskWebhookBodyMatchesHistoricalFixtureForFallbackAndExplicitOnePointZero() throws Exception {
        String taskId = "task-123";
        Task task = historicalTaskWithNewState();
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/fallback", "fallback", null));
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v10", "v10", null), "1.0");

        sender.sendNotification(task, task);

        JsonObject expected = historicalFixture("1.0", "event-task.json");
        assertEquals(2, testHttpClient.rawBodies.size());
        for (String body : testHttpClient.rawBodies) {
            assertWrapperMatchesFixture(body, "task", expected);
        }
    }

    @Test
    void actualLegacyStatusArtifactAndMessageWebhookBodiesMatchHistoricalFixtures() throws Exception {
        TaskStatusUpdateEvent status = new TaskStatusUpdateEvent("1",
                new TaskStatus(TaskState.TASK_STATE_SUBMITTED, null,
                        OffsetDateTime.parse("2026-01-01T00:00:00Z")), "2", null, 88);
        sendEventForVersion(status, "1.0", "statusUpdate", "event-status_update.json");

        Artifact artifact = new Artifact("artifact-1", null, null,
                List.of(new TextPart("Why did the chicken cross the road? To get to the other side!")),
                null, null, 3, 9, Message.Role.ROLE_AGENT);
        TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent("1", artifact, "2", false, true,
                null, 99);
        sendEventForVersion(artifactUpdate, "1.0", "artifactUpdate", "event-artifact_update.json");

        Message message = Message.builder().role(Message.Role.ROLE_AGENT)
                .parts(new TextPart("Hello, world!")).messageId("msg-123").contextId("context-456").build();
        sendEventForVersion(message, "1.0", "message", "event-message.json");
    }

    @Test
    void oldDeliverySuppressesMessageAndUserArtifactUpdatesBeforeSendingHttp() {
        String taskId = "task-hidden";
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/hidden", "cfg-hidden", null));
        Message updateMessage = Message.builder().role(Message.Role.ROLE_AGENT)
                .parts(new TextPart("update")).messageId("m").build();
        TaskMessageUpdateEvent messageUpdate = new TaskMessageUpdateEvent(taskId, "context", updateMessage, null, 4);
        Artifact userArtifact = new Artifact("user", null, null, List.of(new TextPart("private")), null, null,
                1, 4, Message.Role.ROLE_USER);
        TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent(taskId, userArtifact, "context",
                false, true, null, 4);

        assertTrue(sender.sendNotification(messageUpdate, null));
        assertTrue(sender.sendNotification(artifactUpdate, null));
        assertTrue(testHttpClient.rawBodies.isEmpty());
    }

    @Test
    void onePointOneWebhookRetainsGenerationAndTimeline() throws Exception {
        String taskId = "task-123";
        Task task = historicalTaskWithNewState();
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v11", "v11", null), "1.1");

        sender.sendNotification(task, task);

        JsonObject taskBody = JsonParser.parseString(testHttpClient.rawBodies.get(0)).getAsJsonObject()
                .getAsJsonObject("task");
        assertEquals(17, taskBody.get("generation").getAsInt());
        assertTrue(taskBody.has("timeline"));
    }

    @Test
    void actualWebhookBodiesOmitZeroStampsWithoutRemovingSameNamedContentKeys() {
        String taskId = "task-zero";
        Map<String, Object> content = Map.of("generation", "g", "timeline", "t",
                "startGeneration", "s", "endGeneration", "e", "role", "r");
        Artifact artifact = new Artifact("artifact-zero", null, null,
                List.of(new DataPart(content, content)), content, null, 0, 0, Message.Role.ROLE_AGENT);
        TaskStatus status = new TaskStatus(TaskState.TASK_STATE_WORKING);
        Task task = new Task(taskId, "context-zero", status, List.of(artifact), List.of(), content, 0, null);

        JsonObject legacyTask = JsonParser.parseString(actualBodyForVersion(taskId, task, "1.0"))
                .getAsJsonObject().getAsJsonObject("task");
        assertFalse(legacyTask.has("generation"));
        assertFalse(legacyTask.has("timeline"));
        JsonObject legacyArtifact = legacyTask.getAsJsonArray("artifacts").get(0).getAsJsonObject();
        assertFalse(legacyArtifact.has("startGeneration"));
        assertFalse(legacyArtifact.has("endGeneration"));
        assertFalse(legacyArtifact.has("role"));
        JsonObject part = legacyArtifact.getAsJsonArray("parts").get(0).getAsJsonObject();
        for (String key : content.keySet()) {
            String expected = content.get(key).toString();
            assertEquals(expected, legacyTask.getAsJsonObject("metadata").get(key).getAsString());
            assertEquals(expected, legacyArtifact.getAsJsonObject("metadata").get(key).getAsString());
            assertEquals(expected, part.getAsJsonObject("data").get(key).getAsString());
            assertEquals(expected, part.getAsJsonObject("metadata").get(key).getAsString());
        }

        JsonObject currentTask = JsonParser.parseString(actualBodyForVersion(taskId, task, "1.1"))
                .getAsJsonObject().getAsJsonObject("task");
        assertEquals(0, currentTask.get("generation").getAsInt());
        JsonObject currentArtifact = currentTask.getAsJsonArray("artifacts").get(0).getAsJsonObject();
        assertEquals(0, currentArtifact.get("startGeneration").getAsInt());
        assertEquals(0, currentArtifact.get("endGeneration").getAsInt());
        assertTrue(currentArtifact.has("role"));

        TaskStatusUpdateEvent statusUpdate = new TaskStatusUpdateEvent(taskId, status, "context-zero", content, 0);
        JsonObject legacyStatus = JsonParser.parseString(actualBodyForVersion(taskId, statusUpdate, "1.0"))
                .getAsJsonObject().getAsJsonObject("statusUpdate");
        assertFalse(legacyStatus.has("generation"));
        assertEquals("g", legacyStatus.getAsJsonObject("metadata").get("generation").getAsString());
        JsonObject currentStatus = JsonParser.parseString(actualBodyForVersion(taskId, statusUpdate, "1.1"))
                .getAsJsonObject().getAsJsonObject("statusUpdate");
        assertEquals(0, currentStatus.get("generation").getAsInt());

        TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent(taskId, artifact, "context-zero",
                false, true, content, 0);
        JsonObject legacyUpdate = JsonParser.parseString(actualBodyForVersion(taskId, artifactUpdate, "1.0"))
                .getAsJsonObject().getAsJsonObject("artifactUpdate");
        assertFalse(legacyUpdate.has("generation"));
        JsonObject legacyUpdatedArtifact = legacyUpdate.getAsJsonObject("artifact");
        assertFalse(legacyUpdatedArtifact.has("startGeneration"));
        assertFalse(legacyUpdatedArtifact.has("endGeneration"));
        assertFalse(legacyUpdatedArtifact.has("role"));
        assertEquals("g", legacyUpdate.getAsJsonObject("metadata").get("generation").getAsString());
        assertEquals("g", legacyUpdatedArtifact.getAsJsonArray("parts").get(0).getAsJsonObject()
                .getAsJsonObject("data").get("generation").getAsString());
        JsonObject currentUpdate = JsonParser.parseString(actualBodyForVersion(taskId, artifactUpdate, "1.1"))
                .getAsJsonObject().getAsJsonObject("artifactUpdate");
        assertEquals(0, currentUpdate.get("generation").getAsInt());
        assertEquals(0, currentUpdate.getAsJsonObject("artifact").get("startGeneration").getAsInt());
        assertEquals(0, currentUpdate.getAsJsonObject("artifact").get("endGeneration").getAsInt());
    }

    private String actualBodyForVersion(String taskId, StreamingEventKind event, String version) {
        InMemoryPushNotificationConfigStore store = new InMemoryPushNotificationConfigStore();
        TestHttpClient client = new TestHttpClient();
        store.setInfo(createSamplePushConfig(taskId, "http://notify.me/" + version, "cfg", null), version);
        BasePushNotificationSender isolatedSender = new BasePushNotificationSender(store, client,
                PushNotificationUrlValidator.ALLOW_ALL);
        assertTrue(isolatedSender.sendNotification(event, null));
        assertEquals(1, client.rawBodies.size());
        return client.rawBodies.get(0);
    }

    private void sendEventForVersion(StreamingEventKind event, String version, String wrapper, String fixture)
            throws Exception {
        String taskId;
        if (event instanceof TaskStatusUpdateEvent value) {
            taskId = value.taskId();
        } else if (event instanceof TaskArtifactUpdateEvent value) {
            taskId = value.taskId();
        } else if (event instanceof Message) {
            taskId = "task-123";
        } else {
            throw new IllegalArgumentException("unexpected test event " + event);
        }
        InMemoryPushNotificationConfigStore isolatedStore = new InMemoryPushNotificationConfigStore();
        TestHttpClient isolatedClient = new TestHttpClient();
        isolatedStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/" + wrapper,
                "cfg-" + wrapper, null), version);
        BasePushNotificationSender isolatedSender = new BasePushNotificationSender(isolatedStore, isolatedClient,
                PushNotificationUrlValidator.ALLOW_ALL);

        Task snapshot = event instanceof Message
                ? new Task(taskId, "context-456", new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(), List.of(), null)
                : null;
        isolatedSender.sendNotification(event, snapshot);

        assertEquals(1, isolatedClient.rawBodies.size());
        assertWrapperMatchesFixture(isolatedClient.rawBodies.get(0), wrapper, historicalFixture("1.0", fixture));
    }

    private static Task historicalTaskWithNewState() {
        return new Task("task-123", "context-456",
                new TaskStatus(TaskState.TASK_STATE_WORKING, null,
                        OffsetDateTime.parse("2026-01-01T00:00:00Z")), List.of(), List.of(), null, 17,
                List.of(new TimelineEntry(17, new TimelineEntry.StatusValue(
                        new TaskStatus(TaskState.TASK_STATE_WORKING, null,
                                OffsetDateTime.parse("2026-01-01T00:00:00Z"))))));
    }

    private static JsonObject historicalFixture(String version, String name) throws IOException {
        Path directory = Path.of(System.getProperty("user.dir"));
        if (!Files.exists(directory.resolve(".claude"))) {
            directory = directory.getParent();
        }
        Path fixture = directory.resolve(".claude/architecture/a2a-1.1/phase-0/fixtures/expected")
                .resolve(version).resolve(name);
        return JsonParser.parseString(Files.readString(fixture)).getAsJsonObject();
    }

    private static void assertWrapperMatchesFixture(String body, String wrapper, JsonObject expected) {
        JsonObject actual = JsonParser.parseString(body).getAsJsonObject();
        assertEquals(java.util.Set.of(wrapper), actual.keySet());
        assertEquals(expected, actual.getAsJsonObject(wrapper));
    }

    private TestHttpClient testHttpClient;
    private InMemoryPushNotificationConfigStore configStore;
    private BasePushNotificationSender sender;

    /**
     * Simple test implementation of A2AHttpClient that captures HTTP calls for verification.
     * Now captures StreamingEventKind events wrapped in StreamResponse format.
     */
    private static class TestHttpClient implements A2AHttpClient {
        final List<StreamingEventKind> events = Collections.synchronizedList(new ArrayList<>());
        final List<String> urls = Collections.synchronizedList(new ArrayList<>());
        final List<Map<String, String>> headers = Collections.synchronizedList(new ArrayList<>());
        final List<String> rawBodies = Collections.synchronizedList(new ArrayList<>());
        volatile CountDownLatch latch;
        volatile boolean shouldThrowException = false;
        volatile int responseStatusCode = 200;

        @Override
        public GetBuilder createGet() {
            return null;
        }

        @Override
        public PostBuilder createPost() {
            return new TestPostBuilder();
        }

        @Override
        public DeleteBuilder createDelete() {
            return null;
        }

        class TestPostBuilder implements A2AHttpClient.PostBuilder {
            private volatile String body;
            private volatile String url;
            private final Map<String, String> requestHeaders = new java.util.HashMap<>();

            @Override
            public PostBuilder body(String body) {
                this.body = body;
                return this;
            }

            @Override
            public A2AHttpResponse post() throws IOException, InterruptedException {
                if (shouldThrowException) {
                    throw new IOException("Simulated network error");
                }

                try {
                    // Store raw body for verification
                    rawBodies.add(body);

                    // Parse StreamResponse format to extract the event
                    // The body contains a wrapper with one of: task, message, statusUpdate, artifactUpdate
                    StreamingEventKind event = JsonUtil.fromJson(body, StreamingEventKind.class);
                    events.add(event);
                    urls.add(url);
                    headers.add(new java.util.HashMap<>(requestHeaders));

                    int status = responseStatusCode;
                    return new A2AHttpResponse() {
                        @Override
                        public int status() {
                            return status;
                        }

                        @Override
                        public boolean success() {
                            return status >= 200 && status < 300;
                        }

                        @Override
                        public String body() {
                            return "";
                        }
                    };
                } catch (JsonProcessingException e) {
                    throw new IOException("Failed to parse StreamingEventKind JSON", e);
                } finally {
                    if (latch != null) {
                        latch.countDown();
                    }
                }
            }

            @Override
            public CompletableFuture<Void> postAsyncSSE(Consumer<ServerSentEvent> messageConsumer, Consumer<Throwable> errorConsumer, Runnable completeRunnable) throws IOException, InterruptedException {
                return null;
            }

            @Override
            public PostBuilder url(String url) {
                this.url = url;
                return this;
            }

            @Override
            public PostBuilder addHeader(String name, String value) {
                requestHeaders.put(name, value);
                return this;
            }

            @Override
            public PostBuilder addHeaders(Map<String, String> headers) {
                requestHeaders.putAll(headers);
                return this;
            }
        }
    }

    @BeforeEach
    public void setUp() {
        testHttpClient = new TestHttpClient();
        configStore = new InMemoryPushNotificationConfigStore();
        sender = new BasePushNotificationSender(configStore, testHttpClient,
                PushNotificationUrlValidator.ALLOW_ALL);
    }

    private void testSendNotificationWithInvalidToken(String token, String testName) throws InterruptedException {
        String taskId = testName;
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
        TaskPushNotificationConfig config = createSamplePushConfig(taskId, "http://notify.me/here", "cfg1", token);

        // Set up the configuration in the store
        configStore.setInfo(config);
        
        // Set up latch to wait for async completion
        testHttpClient.latch = new CountDownLatch(1);

        sender.sendNotification(taskData, null);

        // Wait for the async operation to complete
        assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS), "HTTP call should complete within 5 seconds");

        // Verify the task was sent via HTTP wrapped in StreamResponse format
        assertEquals(1, testHttpClient.events.size());
        StreamingEventKind sentEvent = testHttpClient.events.get(0);
        assertTrue(sentEvent instanceof Task, "Event should be a Task");
        Task sentTask = (Task) sentEvent;
        assertEquals(taskData.id(), sentTask.id());

        // Verify that no authentication header was sent (invalid token should not add header)
        assertEquals(1, testHttpClient.headers.size());
        Map<String, String> sentHeaders = testHttpClient.headers.get(0);
        assertEquals(1, sentHeaders.size());
        assertFalse(sentHeaders.containsKey(A2AHeaders.X_A2A_NOTIFICATION_TOKEN),
                "X-A2A-Notification-Token header should not be sent when token is invalid");
        // Content-Type header should always be present
        assertTrue(sentHeaders.containsKey(CONTENT_TYPE));
        assertEquals(APPLICATION_JSON, sentHeaders.get(CONTENT_TYPE));
    }

    private Task createSampleTask(String taskId, TaskState state) {
        return Task.builder()
                .id(taskId)
                .contextId("ctx456")
                .status(new TaskStatus(state))
                .build();
    }

    private TaskPushNotificationConfig createSamplePushConfig(String taskId, String url, String configId, String token) {
        TaskPushNotificationConfig.Builder builder = TaskPushNotificationConfig.builder()
                .url(url)
                .id(configId)
                .taskId(taskId);
        if (token != null) {
            builder.token(token);
        }
        return builder.build();
    }

    @Test
    public void testSendNotificationSuccess() throws InterruptedException {
        String taskId = "task_send_success";
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
        TaskPushNotificationConfig config = createSamplePushConfig(taskId,"http://notify.me/here", "cfg1", null);

        // Set up the configuration in the store
        configStore.setInfo(config);

        // Set up latch to wait for async completion
        testHttpClient.latch = new CountDownLatch(1);

        sender.sendNotification(taskData, null);

        // Wait for the async operation to complete
        assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS), "HTTP call should complete within 5 seconds");

        // Verify the task was sent via HTTP wrapped in StreamResponse format
        assertEquals(1, testHttpClient.events.size());
        StreamingEventKind sentEvent = testHttpClient.events.get(0);
        assertTrue(sentEvent instanceof Task, "Event should be a Task");
        Task sentTask = (Task) sentEvent;
        assertEquals(taskData.id(), sentTask.id());
        assertEquals(taskData.contextId(), sentTask.contextId());
        assertEquals(taskData.status().state(), sentTask.status().state());

        // Verify StreamResponse wrapper is present in raw body
        String rawBody = testHttpClient.rawBodies.get(0);
        assertTrue(rawBody.contains("\"task\""), "Raw body should contain 'task' discriminator for StreamResponse");
    }

    @Test
    public void testSendNotificationWithTokenSuccess() throws InterruptedException {
        String taskId = "task_send_with_token";
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
        TaskPushNotificationConfig config = createSamplePushConfig(taskId,"http://notify.me/here", "cfg1", "unique_token");

        // Set up the configuration in the store
        configStore.setInfo(config);

        // Set up latch to wait for async completion
        testHttpClient.latch = new CountDownLatch(1);

        sender.sendNotification(taskData, null);

        // Wait for the async operation to complete
        assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS), "HTTP call should complete within 5 seconds");

        // Verify the task was sent via HTTP wrapped in StreamResponse format
        assertEquals(1, testHttpClient.events.size());
        StreamingEventKind sentEvent = testHttpClient.events.get(0);
        assertTrue(sentEvent instanceof Task, "Event should be a Task");
        Task sentTask = (Task) sentEvent;
        assertEquals(taskData.id(), sentTask.id());

        // Verify that the X-A2A-Notification-Token header is sent with the correct token
        assertEquals(1, testHttpClient.headers.size());
        Map<String, String> sentHeaders = testHttpClient.headers.get(0);
        assertEquals(2, sentHeaders.size());
        assertTrue(sentHeaders.containsKey(A2AHeaders.X_A2A_NOTIFICATION_TOKEN));
        assertEquals(config.token(), sentHeaders.get(A2AHeaders.X_A2A_NOTIFICATION_TOKEN));
        // Content-Type header should always be present
        assertTrue(sentHeaders.containsKey(CONTENT_TYPE));
        assertEquals(APPLICATION_JSON, sentHeaders.get(CONTENT_TYPE));

    }

    @Test
    public void testSendNotificationNoConfig() {
        String taskId = "task_send_no_config";
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);

        // Don't set any configuration in the store
        sender.sendNotification(taskData, null);

        // Verify no HTTP calls were made
        assertEquals(0, testHttpClient.events.size());
    }

    @Test
    public void testSendNotificationWithEmptyToken() throws InterruptedException {
        testSendNotificationWithInvalidToken("", "task_send_empty_token");
    }

    @Test
    public void testSendNotificationWithBlankToken() throws InterruptedException {
        testSendNotificationWithInvalidToken("   ", "task_send_blank_token");
    }

    @Test
    public void testSendNotificationMultipleConfigs() throws InterruptedException {
        String taskId = "task_multiple_configs";
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
        TaskPushNotificationConfig config1 = createSamplePushConfig(taskId,"http://notify.me/cfg1", "cfg1", null);
        TaskPushNotificationConfig config2 = createSamplePushConfig(taskId,"http://notify.me/cfg2", "cfg2", null);

        // Set up multiple configurations in the store
        configStore.setInfo(config1);
        configStore.setInfo(config2);

        // Set up latch to wait for async completion (2 calls expected)
        testHttpClient.latch = new CountDownLatch(2);

        sender.sendNotification(taskData, null);

        // Wait for the async operations to complete
        assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS), "HTTP calls should complete within 5 seconds");

        // Verify both events were sent via HTTP wrapped in StreamResponse format
        assertEquals(2, testHttpClient.events.size());
        assertEquals(2, testHttpClient.urls.size());
        assertTrue(testHttpClient.urls.containsAll(java.util.List.of("http://notify.me/cfg1", "http://notify.me/cfg2")));

        // Both events should be identical (same event sent to different endpoints)
        for (StreamingEventKind sentEvent : testHttpClient.events) {
            assertTrue(sentEvent instanceof Task, "Event should be a Task");
            Task sentTask = (Task) sentEvent;
            assertEquals(taskData.id(), sentTask.id());
            assertEquals(taskData.contextId(), sentTask.contextId());
            assertEquals(taskData.status().state(), sentTask.status().state());
        }
    }

    @Test
    public void testSendNotificationHttpError() {
        String taskId = "task_send_http_err";
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
        TaskPushNotificationConfig config = createSamplePushConfig(taskId,"http://notify.me/http_error", "cfg1", null);

        // Set up the configuration in the store
        configStore.setInfo(config);

        // Configure the test client to throw an exception
        testHttpClient.shouldThrowException = true;

        // This should not throw an exception - errors should be handled gracefully
        sender.sendNotification(taskData, null);

        // Verify no events were successfully processed due to the error
        assertEquals(0, testHttpClient.events.size());
    }

    @Test
    public void testSendNotificationMessage() throws InterruptedException {
        String taskId = "task_send_message";
        Message message = Message.builder()
                .taskId(taskId)
                .role(Message.Role.ROLE_AGENT)
                .parts(new TextPart("Hello from agent"))
                .build();
        TaskPushNotificationConfig config = createSamplePushConfig(taskId,"http://notify.me/here", "cfg1", null);

        // Set up the configuration in the store
        configStore.setInfo(config);

        // Set up latch to wait for async completion
        testHttpClient.latch = new CountDownLatch(1);

        sender.sendNotification(message, null);

        // Wait for the async operation to complete
        assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS), "HTTP call should complete within 5 seconds");

        // Verify the message was sent via HTTP wrapped in StreamResponse format
        assertEquals(1, testHttpClient.events.size());
        StreamingEventKind sentEvent = testHttpClient.events.get(0);
        assertTrue(sentEvent instanceof Message, "Event should be a Message");
        Message sentMessage = (Message) sentEvent;
        assertEquals(taskId, sentMessage.taskId());

        // Verify StreamResponse wrapper with 'message' discriminator
        String rawBody = testHttpClient.rawBodies.get(0);
        assertTrue(rawBody.contains("\"message\""), "Raw body should contain 'message' discriminator for StreamResponse");
    }

    @Test
    public void testSendNotificationTaskStatusUpdate() throws InterruptedException {
        String taskId = "task_send_status_update";
        TaskStatusUpdateEvent statusUpdate = TaskStatusUpdateEvent.builder()
                .taskId(taskId)
                .contextId("ctx456")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .build();
        TaskPushNotificationConfig config = createSamplePushConfig(taskId,"http://notify.me/here", "cfg1", null);

        // Set up the configuration in the store
        configStore.setInfo(config);

        // Set up latch to wait for async completion
        testHttpClient.latch = new CountDownLatch(1);

        sender.sendNotification(statusUpdate, null);

        // Wait for the async operation to complete
        assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS), "HTTP call should complete within 5 seconds");

        // Verify the status update was sent via HTTP wrapped in StreamResponse format
        assertEquals(1, testHttpClient.events.size());
        StreamingEventKind sentEvent = testHttpClient.events.get(0);
        assertTrue(sentEvent instanceof TaskStatusUpdateEvent, "Event should be a TaskStatusUpdateEvent");
        TaskStatusUpdateEvent sentUpdate = (TaskStatusUpdateEvent) sentEvent;
        assertEquals(taskId, sentUpdate.taskId());
        assertEquals(TaskState.TASK_STATE_WORKING, sentUpdate.status().state());

        // Verify StreamResponse wrapper with 'statusUpdate' discriminator
        String rawBody = testHttpClient.rawBodies.get(0);
        assertTrue(rawBody.contains("\"statusUpdate\""), "Raw body should contain 'statusUpdate' discriminator for StreamResponse");
    }

    @Test
    public void testSendNotificationTaskArtifactUpdate() throws InterruptedException {
        String taskId = "task_send_artifact_update";
        Artifact artifact = Artifact.builder()
                .artifactId("artifact-1")
                .name("test-artifact")
                .parts(Collections.singletonList(new TextPart("Artifact chunk")))
                .build();
        TaskArtifactUpdateEvent artifactUpdate = TaskArtifactUpdateEvent.builder()
                .taskId(taskId)
                .contextId("ctx456")
                .artifact(artifact)
                .build();
        TaskPushNotificationConfig config = createSamplePushConfig(taskId,"http://notify.me/here", "cfg1", null);

        // Set up the configuration in the store
        configStore.setInfo(config);

        // Set up latch to wait for async completion
        testHttpClient.latch = new CountDownLatch(1);

        sender.sendNotification(artifactUpdate, null);

        // Wait for the async operation to complete
        assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS), "HTTP call should complete within 5 seconds");

        // Verify the artifact update was sent via HTTP wrapped in StreamResponse format
        assertEquals(1, testHttpClient.events.size());
        StreamingEventKind sentEvent = testHttpClient.events.get(0);
        assertTrue(sentEvent instanceof TaskArtifactUpdateEvent, "Event should be a TaskArtifactUpdateEvent");
        TaskArtifactUpdateEvent sentUpdate = (TaskArtifactUpdateEvent) sentEvent;
        assertEquals(taskId, sentUpdate.taskId());

        // Verify StreamResponse wrapper with 'artifactUpdate' discriminator
        String rawBody = testHttpClient.rawBodies.get(0);
        assertTrue(rawBody.contains("\"artifactUpdate\""), "Raw body should contain 'artifactUpdate' discriminator for StreamResponse");
    }

    @Test
    public void testSendNotificationUsesFormatterForVersionedConfig() throws InterruptedException {
        String taskId = "task_formatter_test";
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);

        TaskPushNotificationConfig config = TaskPushNotificationConfig.builder()
                .url("http://notify.me/here")
                .id("cfg1")
                .taskId(taskId)
                .build();
        configStore.setInfo(config, "0.3");

        PushNotificationPayloadFormatter formatter = new PushNotificationPayloadFormatter() {
            @Override
            public String targetVersion() { return "0.3"; }

            @Override
            public @Nullable String formatPayload(StreamingEventKind event, @Nullable Task snapshot) {
                return "{\"id\":\"" + taskId + "\",\"kind\":\"task\",\"formatted\":true}";
            }
        };

        BasePushNotificationSender formatterSender = new BasePushNotificationSender(
                configStore, testHttpClient, List.of(formatter), PushNotificationUrlValidator.ALLOW_ALL);
        testHttpClient.latch = new CountDownLatch(1);

        formatterSender.sendNotification(taskData, taskData);

        assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, testHttpClient.rawBodies.size());
        assertTrue(testHttpClient.rawBodies.get(0).contains("\"formatted\":true"));
    }

    @Test
    public void testSendNotificationSkipsWhenFormatterReturnsNull() throws InterruptedException {
        String taskId = "task_formatter_skip";
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);

        TaskPushNotificationConfig config = TaskPushNotificationConfig.builder()
                .url("http://notify.me/here")
                .id("cfg1")
                .taskId(taskId)
                .build();
        configStore.setInfo(config, "0.3");

        PushNotificationPayloadFormatter formatter = new PushNotificationPayloadFormatter() {
            @Override
            public String targetVersion() { return "0.3"; }

            @Override
            public @Nullable String formatPayload(StreamingEventKind event, @Nullable Task snapshot) {
                return null;
            }
        };

        BasePushNotificationSender formatterSender = new BasePushNotificationSender(
                configStore, testHttpClient, List.of(formatter), PushNotificationUrlValidator.ALLOW_ALL);

        formatterSender.sendNotification(taskData, taskData);

        assertTrue(testHttpClient.rawBodies.isEmpty());
    }

    @Test
    public void testSendNotificationRejectsCrlfInToken() {
        String taskId = "task_send_crlf_token";
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
        TaskPushNotificationConfig config = createSamplePushConfig(taskId, "http://notify.me/here", "cfg-crlf-token",
                "token\r\nX-Injected: 1");
        configStore.setInfo(config);

        // No latch needed: sendNotification() calls dispatchResult.get(), which blocks until
        // all CompletableFuture dispatches complete, including the CRLF rejection path.
        sender.sendNotification(taskData, null);

        assertTrue(testHttpClient.events.isEmpty(), "Notification with CRLF token must not be dispatched");
        assertTrue(testHttpClient.headers.isEmpty(), "No headers should have been sent");
        assertTrue(testHttpClient.rawBodies.isEmpty(), "No body should have been sent");
    }

    @Test
    public void testSendNotificationWithAuthHeader() throws InterruptedException {
        String taskId = "task_send_auth";
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
        TaskPushNotificationConfig config = TaskPushNotificationConfig.builder()
                .url("http://notify.me/here")
                .id("cfg-auth")
                .taskId(taskId)
                .authentication(new AuthenticationInfo("Bearer", "token123"))
                .build();
        configStore.setInfo(config);

        testHttpClient.latch = new CountDownLatch(1);
        sender.sendNotification(taskData, null);

        assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS), "HTTP call should complete within 5 seconds");
        assertEquals(1, testHttpClient.events.size());
        assertEquals(1, testHttpClient.headers.size());
        Map<String, String> sentHeaders = testHttpClient.headers.get(0);
        assertEquals("Bearer token123", sentHeaders.get("Authorization"));
    }

    static Stream<Arguments> crlfAuthVectors() {
        return Stream.of(
                Arguments.of("CRLF in credentials", "Bearer", "token\r\nX-Injected: 1"),
                Arguments.of("LF in scheme", "Bearer\nX-Injected: 1", "token"),
                Arguments.of("bare CR in credentials", "Bearer", "token\rX-Injected: 1"),
                Arguments.of("bare CR in scheme", "Bearer\rX-Injected: 1", "token"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("crlfAuthVectors")
    public void testSendNotificationRejectsCrlfInAuthFields(String description, String scheme, String credentials) {
        String taskId = "task_send_crlf_auth";
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
        TaskPushNotificationConfig config = TaskPushNotificationConfig.builder()
                .url("http://notify.me/here")
                .id("cfg-crlf")
                .taskId(taskId)
                .authentication(new AuthenticationInfo(scheme, credentials))
                .build();
        configStore.setInfo(config);

        sender.sendNotification(taskData, null);

        assertTrue(testHttpClient.events.isEmpty(), "Notification with " + description + " must not be dispatched");
        assertTrue(testHttpClient.headers.isEmpty(), "No headers should have been sent");
        assertTrue(testHttpClient.rawBodies.isEmpty(), "No body should have been sent");
    }

    static Stream<Arguments> nonSuccessHttpStatuses() {
        return Stream.of(
                Arguments.of("3xx redirect", 301),
                Arguments.of("4xx client error", 404),
                Arguments.of("5xx server error", 500));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonSuccessHttpStatuses")
    public void testSendNotificationReturnsFalseOnNon2xxResponse(String description, int statusCode) {
        String taskId = "task_non2xx_" + statusCode;
        Task taskData = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
        TaskPushNotificationConfig config = createSamplePushConfig(taskId, "http://notify.me/here", "cfg1", null);
        configStore.setInfo(config);

        testHttpClient.responseStatusCode = statusCode;

        boolean result = sender.sendNotification(taskData, null);

        assertFalse(result, description + " (HTTP " + statusCode + ") must cause sendNotification to return false");
    }

    @Nested
    class ExecutorResolution {

        @SuppressWarnings("unchecked")
        private Instance<Executor> mockInstance(boolean resolvable, Executor executor) {
            Instance<Executor> instance = mock(Instance.class);
            when(instance.isResolvable()).thenReturn(resolvable);
            if (resolvable) {
                when(instance.get()).thenReturn(executor);
            }
            return instance;
        }

        @SuppressWarnings("unchecked")
        private BasePushNotificationSender createWithExecutors(Instance<Executor> instrumented,
                                                                Instance<Executor> named) {
            Instance<PushNotificationPayloadFormatter> formatters = mock(Instance.class);
            when(formatters.iterator()).thenReturn(Collections.emptyIterator());

            Instance<A2AHttpClient> httpClient = mock(Instance.class);
            when(httpClient.isResolvable()).thenReturn(false);

            Instance<A2AHttpClient> propagatingClient = mock(Instance.class);
            when(propagatingClient.isResolvable()).thenReturn(false);

            return new BasePushNotificationSender(
                    new InMemoryPushNotificationConfigStore(),
                    formatters,
                    PushNotificationUrlValidator.ALLOW_ALL,
                    httpClient,
                    propagatingClient,
                    instrumented,
                    named);
        }

        private TestHttpClient prepareDispatch(BasePushNotificationSender sender, String taskId) {
            InMemoryPushNotificationConfigStore store = new InMemoryPushNotificationConfigStore();
            store.setInfo(createSamplePushConfig(taskId, "http://notify.me/here", "cfg1", null));
            TestHttpClient http = new TestHttpClient();
            http.latch = new CountDownLatch(1);
            try {
                java.lang.reflect.Field storeField = BasePushNotificationSender.class.getDeclaredField("configStore");
                storeField.setAccessible(true);
                storeField.set(sender, store);
                java.lang.reflect.Field clientField = BasePushNotificationSender.class.getDeclaredField("httpClient");
                clientField.setAccessible(true);
                clientField.set(sender, http);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            return http;
        }

        @Test
        void instrumentedExecutorIsPreferredOverNamed() throws InterruptedException {
            AtomicInteger instrumentedCount = new AtomicInteger();
            AtomicInteger namedCount = new AtomicInteger();

            Executor instrumentedExec = command -> {
                instrumentedCount.incrementAndGet();
                command.run();
            };
            Executor namedExec = command -> {
                namedCount.incrementAndGet();
                command.run();
            };

            BasePushNotificationSender s = createWithExecutors(
                    mockInstance(true, instrumentedExec),
                    mockInstance(true, namedExec));

            String taskId = "exec-resolution-instrumented";
            TestHttpClient httpClient = prepareDispatch(s, taskId);

            Task task = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
            s.sendNotification(task, null);

            assertTrue(httpClient.latch.await(5, TimeUnit.SECONDS));
            assertTrue(instrumentedCount.get() > 0,
                    "@Instrumented executor must be used when available");
            assertEquals(0, namedCount.get(),
                    "@Named executor must NOT be used when @Instrumented is available");
        }

        @Test
        void namedExecutorUsedWhenInstrumentedAbsent() throws InterruptedException {
            AtomicInteger namedCount = new AtomicInteger();

            Executor namedExec = command -> {
                namedCount.incrementAndGet();
                command.run();
            };

            BasePushNotificationSender s = createWithExecutors(
                    mockInstance(false, null),
                    mockInstance(true, namedExec));

            String taskId = "exec-resolution-named";
            TestHttpClient httpClient = prepareDispatch(s, taskId);

            Task task = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
            s.sendNotification(task, null);

            assertTrue(httpClient.latch.await(5, TimeUnit.SECONDS));
            assertTrue(namedCount.get() > 0,
                    "@Named executor must be used when @Instrumented is absent");
        }

        @Test
        void commonPoolUsedWhenBothAbsent() throws InterruptedException {
            BasePushNotificationSender s = createWithExecutors(
                    mockInstance(false, null),
                    mockInstance(false, null));

            String taskId = "exec-resolution-fallback";
            TestHttpClient httpClient = prepareDispatch(s, taskId);

            Task task = createSampleTask(taskId, TaskState.TASK_STATE_COMPLETED);
            s.sendNotification(task, null);

            assertTrue(httpClient.latch.await(5, TimeUnit.SECONDS),
                    "Notification must still be dispatched via the common pool fallback");
        }
    }
}
