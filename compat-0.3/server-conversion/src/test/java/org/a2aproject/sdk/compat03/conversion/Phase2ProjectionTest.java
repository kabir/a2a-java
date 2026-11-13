package org.a2aproject.sdk.compat03.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.client.http.ServerSentEvent;
import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationPayloadFormatter;
import org.a2aproject.sdk.server.tasks.PushNotificationUrlValidator;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class Phase2ProjectionTest {

    @Test
    void actualV03WebhookBodyMatchesUnchangedHistoricalTaskFixture() throws Exception {
        InMemoryPushNotificationConfigStore configStore = new InMemoryPushNotificationConfigStore();
        TaskPushNotificationConfig config = new TaskPushNotificationConfig("legacy", "task-123",
                "https://example.invalid/webhook", null, null, null);
        configStore.setInfo(config, "0.3");
        CapturingHttpClient httpClient = new CapturingHttpClient();
        BasePushNotificationSender sender = new BasePushNotificationSender(configStore, httpClient,
                List.of(new PushNotificationPayloadFormatter_v0_3()), PushNotificationUrlValidator.ALLOW_ALL);

        Task canonical = Task.builder()
                .id("task-123")
                .contextId("context-456")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null,
                        OffsetDateTime.parse("2026-01-01T00:00:00Z")))
                .artifacts(List.of())
                .history(List.of())
                .generation(41)
                .timeline(List.of(new org.a2aproject.sdk.spec.TimelineEntry(41,
                        new org.a2aproject.sdk.spec.TimelineEntry.MessageValue(Message.builder()
                                .messageId("ignored")
                                .role(Message.Role.ROLE_AGENT)
                                .parts(new TextPart("timeline"))
                                .build()))))
                .build();

        assertTrue(sender.sendNotification(canonical, canonical));
        assertEquals(1, httpClient.bodies.size());
        assertEquals(JsonParser.parseString(Files.readString(historicalFixture())),
                JsonParser.parseString(httpClient.bodies.get(0)));
    }

    @Test
    void v03SuppressedUpdatesDoNotInvokeFormatterOrSendHttp() {
        InMemoryPushNotificationConfigStore configStore = new InMemoryPushNotificationConfigStore();
        configStore.setInfo(new TaskPushNotificationConfig("legacy", "task-123",
                "https://example.invalid/webhook", null, null, null), "0.3");
        CapturingHttpClient httpClient = new CapturingHttpClient();
        AtomicInteger formatterCalls = new AtomicInteger();
        PushNotificationPayloadFormatter formatter = new PushNotificationPayloadFormatter() {
            @Override public String targetVersion() { return "0.3"; }
            @Override public String formatPayload(org.a2aproject.sdk.spec.StreamingEventKind event,
                    @org.jspecify.annotations.Nullable Task snapshot) {
                formatterCalls.incrementAndGet();
                return "unexpected";
            }
        };
        BasePushNotificationSender sender = new BasePushNotificationSender(configStore, httpClient,
                List.of(formatter), PushNotificationUrlValidator.ALLOW_ALL);
        Task snapshot = Task.builder().id("task-123").contextId("context-456")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build();
        Message update = Message.builder().messageId("m1").role(Message.Role.ROLE_AGENT)
                .parts(new TextPart("update")).build();
        TaskMessageUpdateEvent messageUpdate = new TaskMessageUpdateEvent("task-123", "context-456", update, null, 5);
        TaskArtifactUpdateEvent userArtifactUpdate = new TaskArtifactUpdateEvent("task-123",
                new Artifact("user-artifact", null, null, List.of(new TextPart("private")), null, null,
                        1, 5, Message.Role.ROLE_USER), "context-456", false, true, null, 5);

        assertTrue(sender.sendNotification(messageUpdate, snapshot));
        assertTrue(sender.sendNotification(userArtifactUpdate, snapshot));
        assertEquals(0, formatterCalls.get());
        assertTrue(httpClient.bodies.isEmpty());
    }

    private static Path historicalFixture() {
        Path directory = Path.of(System.getProperty("user.dir"));
        while (directory.getParent() != null && !Files.exists(directory.resolve(".claude"))) {
            directory = directory.getParent();
        }
        return directory.resolve(".claude/architecture/a2a-1.1/phase-0/fixtures/expected/0.3/event-task.json");
    }

    private static final class CapturingHttpClient implements A2AHttpClient {
        private final java.util.ArrayList<String> bodies = new java.util.ArrayList<>();

        @Override public GetBuilder createGet() { return null; }
        @Override public DeleteBuilder createDelete() { return null; }
        @Override public PostBuilder createPost() {
            return new PostBuilder() {
                private String body;
                @Override public PostBuilder body(String value) { body = value; return this; }
                @Override public A2AHttpResponse post() {
                    bodies.add(body);
                    return new A2AHttpResponse() {
                        @Override public int status() { return 200; }
                        @Override public boolean success() { return true; }
                        @Override public String body() { return ""; }
                    };
                }
                @Override public CompletableFuture<Void> postAsyncSSE(Consumer<ServerSentEvent> messageConsumer,
                        Consumer<Throwable> errorConsumer, Runnable completeRunnable) {
                    return CompletableFuture.completedFuture(null);
                }
                @Override public PostBuilder url(String value) { return this; }
                @Override public PostBuilder addHeader(String name, String value) { return this; }
                @Override public PostBuilder addHeaders(Map<String, String> headers) { return this; }
            };
        }
    }
}
