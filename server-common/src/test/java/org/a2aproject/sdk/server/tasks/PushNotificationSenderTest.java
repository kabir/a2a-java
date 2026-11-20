package org.a2aproject.sdk.server.tasks;

import static org.a2aproject.sdk.client.http.A2AHttpClient.APPLICATION_JSON;
import static org.a2aproject.sdk.client.http.A2AHttpClient.CONTENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.server.events.EventQueue;
import org.a2aproject.sdk.server.events.EventQueueUtil;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.MainEventBusProcessorCallback;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.AuthenticationInfo;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Event;
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
    void versionUnawareSenderFallsBackToCanonicalProposalStatusAndSkipsNoOpSnapshot() {
        List<StreamingEventKind> delivered = new ArrayList<>();
        PushNotificationSender versionUnaware = (event, snapshot) -> {
            delivered.add(event);
            return true;
        };
        Task task = createSampleTask("fallback-task", TaskState.TASK_STATE_WORKING);
        TaskStatusUpdateEvent status = new TaskStatusUpdateEvent(task.id(),
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED), task.contextId(), null, 1);

        assertTrue(versionUnaware.sendTaskProposalStatusNotification(status, task));
        assertTrue(versionUnaware.sendLegacyTaskSnapshot(task));

        assertEquals(List.of(status), delivered,
                "legacy senders keep canonical changed-status fallback and invent no no-op mutation");
    }

    @Test
    void batchAwareSenderSelectsOneLegacySummaryAndAllCanonicalEvents() throws InterruptedException {
        String taskId = "batch-aware-webhook";
        Task task = createSampleTask(taskId, TaskState.TASK_STATE_INPUT_REQUIRED);
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v10", "v10", null), "1.0");
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v11", "v11", null), "1.1");
        TaskArtifactUpdateEvent artifact = new TaskArtifactUpdateEvent(taskId,
                Artifact.builder().artifactId("answer").parts(List.of(new TextPart("bytes"))).build(),
                task.contextId(), false, true, null, 1);
        TaskStatusUpdateEvent status = new TaskStatusUpdateEvent(taskId,
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED), task.contextId(), null, 2);
        CommittedTaskBatch batch = new CommittedTaskBatch(
                new TaskSnapshot(task, new TaskRevision(1)), List.of(artifact, status));
        testHttpClient.latch = new CountDownLatch(3);

        assertTrue(sender.sendTaskProposalBatchNotification(batch));
        assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));

        List<StreamingEventKind> legacyEvents = testHttpClient.notifications.stream()
                .filter(notification -> notification.url().endsWith("/v10"))
                .map(CapturedNotification::event).toList();
        List<StreamingEventKind> currentEvents = testHttpClient.notifications.stream()
                .filter(notification -> notification.url().endsWith("/v11"))
                .map(CapturedNotification::event).toList();
        assertEquals(List.of(Task.class), legacyEvents.stream().map(Object::getClass).toList());
        assertEquals(List.of(TaskArtifactUpdateEvent.class, TaskStatusUpdateEvent.class),
                currentEvents.stream().map(Object::getClass).toList());
        TaskArtifactUpdateEvent deliveredArtifact = assertInstanceOf(TaskArtifactUpdateEvent.class,
                currentEvents.get(0));
        assertEquals("bytes", assertInstanceOf(TextPart.class, deliveredArtifact.artifact().parts().get(0)).text());
        assertEquals(2, assertInstanceOf(TaskStatusUpdateEvent.class, currentEvents.get(1)).generation());
    }

    @Test
    void statusOnlyProposalUsesStatusHookWhenWrapperInheritsNoOpSnapshotHook() throws Exception {
        String taskId = "status-only-inherited-noop";
        Task initial = createSampleTask(taskId, TaskState.TASK_STATE_WORKING);
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v10", "v10", null), "1.0");
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v11", "v11", null), "1.1");
        ExecutorService forwardingWorker = Executors.newSingleThreadExecutor();
        PushNotificationSender delegatingSender = new PushNotificationSender() {
            @Override
            public boolean sendNotification(StreamingEventKind event, @Nullable Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendNotification(event, snapshot));
            }

            @Override
            public boolean sendTaskProposalStatusNotification(TaskStatusUpdateEvent event, Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendTaskProposalStatusNotification(event, snapshot));
            }
        };
        TaskStatusUpdateEvent status = new TaskStatusUpdateEvent(taskId,
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED), initial.contextId(), null, 1);
        CommittedTaskBatch batch = statusOnlyBatch(initial, status);
        testHttpClient.latch = new CountDownLatch(1);

        try {
            assertTrue(delegatingSender.sendLegacyTaskSnapshot(initial));
            assertTrue(testHttpClient.notifications.isEmpty(), "the inherited no-op hook keeps no-op delivery disabled");

            assertTrue(delegatingSender.sendTaskProposalBatchNotification(batch));
            assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));

            List<StreamingEventKind> legacyEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v10"))
                    .map(CapturedNotification::event).toList();
            List<StreamingEventKind> currentEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v11"))
                    .map(CapturedNotification::event).toList();
            assertEquals(List.of(Task.class), legacyEvents.stream().map(Object::getClass).toList());
            assertEquals(List.of(status), currentEvents);
        } finally {
            forwardingWorker.shutdownNow();
        }
    }

    @Test
    void statusOnlyProposalWithCopiedStatusKeepsOneLegacySummary() throws Exception {
        String taskId = "status-only-copied-event";
        Task initial = createSampleTask(taskId, TaskState.TASK_STATE_WORKING);
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v10", "v10", null), "1.0");
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v11", "v11", null), "1.1");
        ExecutorService forwardingWorker = Executors.newSingleThreadExecutor();
        PushNotificationSender delegatingSender = new PushNotificationSender() {
            @Override
            public boolean sendNotification(StreamingEventKind event, @Nullable Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendNotification(event, snapshot));
            }

            @Override
            public boolean sendTaskProposalStatusNotification(TaskStatusUpdateEvent event, Task snapshot) {
                TaskStatusUpdateEvent copy = TaskStatusUpdateEvent.builder(event).build();
                return awaitWorker(forwardingWorker, () -> sender.sendTaskProposalStatusNotification(copy, snapshot));
            }

            @Override
            public boolean sendLegacyTaskSnapshot(Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendLegacyTaskSnapshot(snapshot));
            }
        };
        TaskStatusUpdateEvent status = new TaskStatusUpdateEvent(taskId,
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED), initial.contextId(), null, 1);
        CommittedTaskBatch batch = statusOnlyBatch(initial, status);
        testHttpClient.latch = new CountDownLatch(3);

        try {
            assertTrue(delegatingSender.sendLegacyTaskSnapshot(initial));
            assertTrue(delegatingSender.sendTaskProposalBatchNotification(batch));
            assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));

            List<StreamingEventKind> legacyEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v10"))
                    .map(CapturedNotification::event).toList();
            List<StreamingEventKind> currentEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v11"))
                    .map(CapturedNotification::event).toList();
            assertEquals(List.of(Task.class, Task.class), legacyEvents.stream().map(Object::getClass).toList(),
                    "the active no-op and changed status each select exactly one legacy Task summary");
            assertEquals(List.of(status), currentEvents);
        } finally {
            forwardingWorker.shutdownNow();
        }
    }

    @Test
    void statusAndArtifactProposalUsesStatusHookWhenWrapperInheritsNoOpSnapshotHook() throws Exception {
        String taskId = "mixed-inherited-noop";
        Task initial = createSampleTask(taskId, TaskState.TASK_STATE_WORKING);
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v10", "v10", null), "1.0");
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v11", "v11", null), "1.1");
        ExecutorService forwardingWorker = Executors.newSingleThreadExecutor();
        PushNotificationSender delegatingSender = new PushNotificationSender() {
            @Override
            public boolean sendNotification(StreamingEventKind event, @Nullable Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendNotification(event, snapshot));
            }

            @Override
            public boolean sendTaskProposalStatusNotification(TaskStatusUpdateEvent event, Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendTaskProposalStatusNotification(event, snapshot));
            }
        };
        TaskStatusUpdateEvent status = new TaskStatusUpdateEvent(taskId,
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED), initial.contextId(), null, 1);
        Artifact artifact = Artifact.builder().artifactId("mixed-output")
                .parts(List.of(new TextPart("proposal output"))).build();
        CommittedTaskBatch batch = statusAndArtifactBatch(initial, status, artifact);
        testHttpClient.latch = new CountDownLatch(2);

        try {
            assertTrue(delegatingSender.sendLegacyTaskSnapshot(initial));
            assertTrue(testHttpClient.notifications.isEmpty(), "the inherited no-op hook keeps no-op delivery disabled");

            assertTrue(delegatingSender.sendTaskProposalBatchNotification(batch));
            assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));

            List<StreamingEventKind> legacyEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v10"))
                    .map(CapturedNotification::event).toList();
            List<StreamingEventKind> currentEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v11"))
                    .map(CapturedNotification::event).toList();
            assertEquals(List.of(Task.class), legacyEvents.stream().map(Object::getClass).toList());
            Task summary = assertInstanceOf(Task.class, legacyEvents.get(0));
            assertProposalArtifact(summary, artifact.artifactId());
            assertEquals(2, currentEvents.size());
            assertInstanceOf(TaskStatusUpdateEvent.class, currentEvents.get(0));
            assertInstanceOf(TaskArtifactUpdateEvent.class, currentEvents.get(1));
        } finally {
            forwardingWorker.shutdownNow();
        }
    }

    @Test
    void statusAndArtifactProposalWithCopiedStatusKeepsOneLegacySummary() throws Exception {
        String taskId = "mixed-copied-status";
        Task initial = createSampleTask(taskId, TaskState.TASK_STATE_WORKING);
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v10", "v10", null), "1.0");
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v11", "v11", null), "1.1");
        ExecutorService forwardingWorker = Executors.newSingleThreadExecutor();
        PushNotificationSender delegatingSender = new PushNotificationSender() {
            @Override
            public boolean sendNotification(StreamingEventKind event, @Nullable Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendNotification(event, snapshot));
            }

            @Override
            public boolean sendTaskProposalStatusNotification(TaskStatusUpdateEvent event, Task snapshot) {
                TaskStatusUpdateEvent copy = TaskStatusUpdateEvent.builder(event).build();
                return awaitWorker(forwardingWorker, () -> sender.sendTaskProposalStatusNotification(copy, snapshot));
            }

            @Override
            public boolean sendLegacyTaskSnapshot(Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendLegacyTaskSnapshot(snapshot));
            }
        };
        TaskStatusUpdateEvent status = new TaskStatusUpdateEvent(taskId,
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED), initial.contextId(), null, 1);
        Artifact artifact = Artifact.builder().artifactId("mixed-output")
                .parts(List.of(new TextPart("proposal output"))).build();
        CommittedTaskBatch batch = statusAndArtifactBatch(initial, status, artifact);
        testHttpClient.latch = new CountDownLatch(4);

        try {
            assertTrue(delegatingSender.sendLegacyTaskSnapshot(initial));
            assertTrue(delegatingSender.sendTaskProposalBatchNotification(batch));
            assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));

            List<StreamingEventKind> legacyEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v10"))
                    .map(CapturedNotification::event).toList();
            List<StreamingEventKind> currentEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v11"))
                    .map(CapturedNotification::event).toList();
            assertEquals(List.of(Task.class, Task.class), legacyEvents.stream().map(Object::getClass).toList(),
                    "the active no-op and changed proposal each select exactly one legacy Task summary");
            assertTrue(assertInstanceOf(Task.class, legacyEvents.get(0)).artifacts().isEmpty());
            assertProposalArtifact(assertInstanceOf(Task.class, legacyEvents.get(1)), artifact.artifactId());
            assertEquals(2, currentEvents.size());
            assertInstanceOf(TaskStatusUpdateEvent.class, currentEvents.get(0));
            assertInstanceOf(TaskArtifactUpdateEvent.class, currentEvents.get(1));
        } finally {
            forwardingWorker.shutdownNow();
        }
    }

    @Test
    void artifactOnlyProposalUsesTaskSummaryWhenWrapperInheritsNoOpSnapshotHook() throws Exception {
        String taskId = "artifact-only-inherited-noop";
        Task initial = createSampleTask(taskId, TaskState.TASK_STATE_WORKING);
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v10", "v10", null), "1.0");
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v11", "v11", null), "1.1");
        ExecutorService forwardingWorker = Executors.newSingleThreadExecutor();
        PushNotificationSender delegatingSender = new PushNotificationSender() {
            @Override
            public boolean sendNotification(StreamingEventKind event, @Nullable Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendNotification(event, snapshot));
            }
        };
        Artifact artifact = Artifact.builder().artifactId("answer")
                .parts(List.of(new TextPart("proposal output"))).build();
        TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent(taskId, artifact,
                initial.contextId(), false, true, null, 1);
        Task proposed = Task.builder(initial).artifacts(List.of(artifact)).generation(1).build();
        CommittedTaskBatch batch = new CommittedTaskBatch(
                new TaskSnapshot(proposed, new TaskRevision(1)), List.of(artifactUpdate));
        testHttpClient.latch = new CountDownLatch(2);

        try {
            assertTrue(delegatingSender.sendLegacyTaskSnapshot(initial));
            assertTrue(testHttpClient.notifications.isEmpty(), "the inherited no-op hook keeps no-op delivery disabled");

            assertTrue(delegatingSender.sendTaskProposalBatchNotification(batch));
            assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));

            List<StreamingEventKind> legacyEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v10"))
                    .map(CapturedNotification::event).toList();
            List<StreamingEventKind> currentEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v11"))
                    .map(CapturedNotification::event).toList();
            assertEquals(List.of(Task.class), legacyEvents.stream().map(Object::getClass).toList());
            assertProposalArtifact(assertInstanceOf(Task.class, legacyEvents.get(0)), "answer");
            assertEquals(1, currentEvents.size());
            assertEquals("answer", assertInstanceOf(TaskArtifactUpdateEvent.class, currentEvents.get(0))
                    .artifact().artifactId());
        } finally {
            forwardingWorker.shutdownNow();
        }
    }

    @Test
    void copiedArtifactProposalUsesOneTaskSummaryAndLeavesEqualOrdinaryArtifactVisible() throws Exception {
        String taskId = "artifact-copy-mixed";
        Task initial = createSampleTask(taskId, TaskState.TASK_STATE_WORKING);
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v10", "v10", null), "1.0");
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v11", "v11", null), "1.1");
        ExecutorService forwardingWorker = Executors.newSingleThreadExecutor();
        PushNotificationSender delegatingSender = new PushNotificationSender() {
            @Override
            public boolean sendNotification(StreamingEventKind event, @Nullable Task snapshot) {
                StreamingEventKind forwarded = event instanceof TaskArtifactUpdateEvent artifactUpdate
                        ? TaskArtifactUpdateEvent.builder(artifactUpdate).build()
                        : event;
                return awaitWorker(forwardingWorker, () -> sender.sendNotification(forwarded, snapshot));
            }

            @Override
            public boolean sendTaskProposalStatusNotification(TaskStatusUpdateEvent event, Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendTaskProposalStatusNotification(event, snapshot));
            }

            @Override
            public boolean sendLegacyTaskSnapshot(Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendLegacyTaskSnapshot(snapshot));
            }
        };
        TaskStatusUpdateEvent status = new TaskStatusUpdateEvent(taskId,
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED), initial.contextId(), null, 1);
        Artifact artifact = Artifact.builder().artifactId("answer")
                .parts(List.of(new TextPart("proposal output"))).build();
        CommittedTaskBatch batch = statusAndArtifactBatch(initial, status, artifact);
        TaskArtifactUpdateEvent proposalArtifact = assertInstanceOf(TaskArtifactUpdateEvent.class,
                batch.events().get(1));
        TaskArtifactUpdateEvent equalOrdinaryArtifact = TaskArtifactUpdateEvent.builder(proposalArtifact).build();
        testHttpClient.latch = new CountDownLatch(6);

        try {
            assertTrue(delegatingSender.sendLegacyTaskSnapshot(initial));
            assertTrue(delegatingSender.sendTaskProposalBatchNotification(batch));
            assertTrue(delegatingSender.sendNotification(equalOrdinaryArtifact, batch.snapshot().task()));
            assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));

            List<StreamingEventKind> legacyEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v10"))
                    .map(CapturedNotification::event).toList();
            List<StreamingEventKind> currentEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v11"))
                    .map(CapturedNotification::event).toList();
            assertEquals(List.of(Task.class, Task.class, TaskArtifactUpdateEvent.class),
                    legacyEvents.stream().map(Object::getClass).toList());
            assertTrue(assertInstanceOf(Task.class, legacyEvents.get(0)).artifacts().isEmpty());
            assertProposalArtifact(assertInstanceOf(Task.class, legacyEvents.get(1)), "answer");
            assertEquals(3, currentEvents.size());
            assertInstanceOf(TaskStatusUpdateEvent.class, currentEvents.get(0));
            assertInstanceOf(TaskArtifactUpdateEvent.class, currentEvents.get(1));
            assertEquals("answer", assertInstanceOf(TaskArtifactUpdateEvent.class, currentEvents.get(2))
                    .artifact().artifactId());
        } finally {
            forwardingWorker.shutdownNow();
        }
    }

    @Test
    void versionUnawareSenderReceivesOnlyCanonicalArtifactForArtifactOnlyProposal() {
        String taskId = "artifact-only-version-unaware";
        Task initial = createSampleTask(taskId, TaskState.TASK_STATE_WORKING);
        Artifact artifact = Artifact.builder().artifactId("answer")
                .parts(List.of(new TextPart("proposal output"))).build();
        TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent(taskId, artifact,
                initial.contextId(), false, true, null, 1);
        Task proposed = Task.builder(initial).artifacts(List.of(artifact)).generation(1).build();
        CommittedTaskBatch batch = new CommittedTaskBatch(
                new TaskSnapshot(proposed, new TaskRevision(1)), List.of(artifactUpdate));
        List<StreamingEventKind> received = new ArrayList<>();
        PushNotificationSender versionUnawareSender = (event, snapshot) -> {
            received.add(event);
            return true;
        };

        assertTrue(versionUnawareSender.sendTaskProposalBatchNotification(batch));

        assertEquals(List.of(artifactUpdate), received);
    }

    @Test
    void equalOrdinaryArtifactDuringProposalForwardingStillReachesBothVersions() throws Exception {
        String taskId = "artifact-copy-concurrent-ordinary";
        Task initial = createSampleTask(taskId, TaskState.TASK_STATE_WORKING);
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v10", "v10", null), "1.0");
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v11", "v11", null), "1.1");
        ExecutorService forwardingWorker = Executors.newSingleThreadExecutor();
        CountDownLatch workerReadyToForward = new CountDownLatch(1);
        CountDownLatch releaseForwardingWorker = new CountDownLatch(1);
        PushNotificationSender delegatingSender = new PushNotificationSender() {
            @Override
            public boolean sendNotification(StreamingEventKind event, @Nullable Task snapshot) {
                if (event instanceof TaskArtifactUpdateEvent artifactUpdate) {
                    TaskArtifactUpdateEvent forwarded = TaskArtifactUpdateEvent.builder(artifactUpdate).build();
                    return awaitWorker(forwardingWorker, () -> {
                        workerReadyToForward.countDown();
                        try {
                            assertTrue(releaseForwardingWorker.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError("worker forwarding was interrupted", e);
                        }
                        return sender.sendNotification(forwarded, snapshot);
                    });
                }
                return awaitWorker(forwardingWorker, () -> sender.sendNotification(event, snapshot));
            }
        };
        Artifact artifact = Artifact.builder().artifactId("answer")
                .parts(List.of(new TextPart("proposal output"))).build();
        TaskArtifactUpdateEvent proposalArtifact = new TaskArtifactUpdateEvent(taskId, artifact,
                initial.contextId(), false, true, null, 1);
        Task proposed = Task.builder(initial).artifacts(List.of(artifact)).generation(1).build();
        CommittedTaskBatch batch = new CommittedTaskBatch(
                new TaskSnapshot(proposed, new TaskRevision(1)), List.of(proposalArtifact));
        TaskArtifactUpdateEvent equalOrdinaryArtifact = TaskArtifactUpdateEvent.builder(proposalArtifact).build();
        testHttpClient.latch = new CountDownLatch(2);

        try {
            CompletableFuture<Boolean> proposalDispatch = CompletableFuture.supplyAsync(
                    () -> delegatingSender.sendTaskProposalBatchNotification(batch));
            assertTrue(workerReadyToForward.await(5, TimeUnit.SECONDS));

            assertTrue(sender.sendNotification(proposalArtifact, batch.snapshot().task()));
            assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));
            List<StreamingEventKind> legacyDuringPause = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v10"))
                    .map(CapturedNotification::event).toList();
            List<StreamingEventKind> currentDuringPause = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v11"))
                    .map(CapturedNotification::event).toList();
            assertEquals(List.of(TaskArtifactUpdateEvent.class),
                    legacyDuringPause.stream().map(Object::getClass).toList());
            assertEquals(List.of(TaskArtifactUpdateEvent.class),
                    currentDuringPause.stream().map(Object::getClass).toList());
            assertEquals("answer", assertInstanceOf(TaskArtifactUpdateEvent.class, currentDuringPause.get(0))
                    .artifact().artifactId());

            testHttpClient.latch = new CountDownLatch(2);
            assertTrue(sender.sendNotification(equalOrdinaryArtifact, batch.snapshot().task()));
            assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));
            testHttpClient.latch = new CountDownLatch(2);
            releaseForwardingWorker.countDown();

            assertTrue(proposalDispatch.get(5, TimeUnit.SECONDS));
            assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));
            List<StreamingEventKind> legacyEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v10"))
                    .map(CapturedNotification::event).toList();
            List<StreamingEventKind> currentEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v11"))
                    .map(CapturedNotification::event).toList();
            assertEquals(1, legacyEvents.stream().filter(Task.class::isInstance).count());
            assertEquals(2, legacyEvents.stream().filter(TaskArtifactUpdateEvent.class::isInstance).count());
            assertEquals(3, currentEvents.stream().filter(TaskArtifactUpdateEvent.class::isInstance).count());
        } finally {
            releaseForwardingWorker.countDown();
            forwardingWorker.shutdownNow();
        }
    }

    @Test
    void artifactOnlyProposalProjectsOneLegacySummaryToEachDelegatedSender() throws Exception {
        String taskId = "artifact-only-multi-delegate";
        Task initial = createSampleTask(taskId, TaskState.TASK_STATE_WORKING);
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/primary-v10", "primary-v10", null),
                "1.0");
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/primary-v11", "primary-v11", null),
                "1.1");
        InMemoryPushNotificationConfigStore secondaryStore = new InMemoryPushNotificationConfigStore();
        secondaryStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/secondary-v10", "secondary-v10",
                null), "1.0");
        secondaryStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/secondary-v11", "secondary-v11",
                null), "1.1");
        TestHttpClient secondaryClient = new TestHttpClient();
        BasePushNotificationSender secondarySender = new BasePushNotificationSender(secondaryStore, secondaryClient,
                PushNotificationUrlValidator.ALLOW_ALL);
        PushNotificationSender delegatingSender = (event, snapshot) -> {
            boolean primaryDelivered = sender.sendNotification(event, snapshot);
            boolean secondaryDelivered = secondarySender.sendNotification(event, snapshot);
            return primaryDelivered && secondaryDelivered;
        };
        Artifact artifact = Artifact.builder().artifactId("answer")
                .parts(List.of(new TextPart("proposal output"))).build();
        TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent(taskId, artifact,
                initial.contextId(), false, true, null, 1);
        Task proposed = Task.builder(initial).artifacts(List.of(artifact)).generation(1).build();
        CommittedTaskBatch batch = new CommittedTaskBatch(
                new TaskSnapshot(proposed, new TaskRevision(1)), List.of(artifactUpdate));
        testHttpClient.latch = new CountDownLatch(2);
        secondaryClient.latch = new CountDownLatch(2);

        assertTrue(delegatingSender.sendTaskProposalBatchNotification(batch));
        assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS));
        assertTrue(secondaryClient.latch.await(5, TimeUnit.SECONDS));

        List<StreamingEventKind> primaryLegacy = testHttpClient.notifications.stream()
                .filter(notification -> notification.url().endsWith("/primary-v10"))
                .map(CapturedNotification::event).toList();
        List<StreamingEventKind> primaryCurrent = testHttpClient.notifications.stream()
                .filter(notification -> notification.url().endsWith("/primary-v11"))
                .map(CapturedNotification::event).toList();
        List<StreamingEventKind> secondaryLegacy = secondaryClient.notifications.stream()
                .filter(notification -> notification.url().endsWith("/secondary-v10"))
                .map(CapturedNotification::event).toList();
        List<StreamingEventKind> secondaryCurrent = secondaryClient.notifications.stream()
                .filter(notification -> notification.url().endsWith("/secondary-v11"))
                .map(CapturedNotification::event).toList();
        assertEquals(List.of(Task.class), primaryLegacy.stream().map(Object::getClass).toList());
        assertProposalArtifact(assertInstanceOf(Task.class, primaryLegacy.get(0)), "answer");
        assertEquals(List.of(TaskArtifactUpdateEvent.class), primaryCurrent.stream().map(Object::getClass).toList());
        assertEquals(List.of(Task.class), secondaryLegacy.stream().map(Object::getClass).toList());
        assertProposalArtifact(assertInstanceOf(Task.class, secondaryLegacy.get(0)), "answer");
        assertEquals(List.of(TaskArtifactUpdateEvent.class),
                secondaryCurrent.stream().map(Object::getClass).toList());
    }

    @Test
    void proposalWebhookVersionSelectionSurvivesDelegatingSenderAndDeferredAdmission() throws Exception {
        String taskId = "proposal-webhook-task";
        Task initial = createSampleTask(taskId, TaskState.TASK_STATE_WORKING);
        InMemoryTaskStore taskStore = new InMemoryTaskStore();
        taskStore.save(initial, false);
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v10", "v10", null), "1.0");
        configStore.setInfo(createSamplePushConfig(taskId, "http://notify.me/v11", "v11", null), "1.1");

        ExecutorService forwardingWorker = Executors.newSingleThreadExecutor();
        PushNotificationSender delegatingSender = new PushNotificationSender() {
            @Override
            public boolean sendNotification(StreamingEventKind event, @Nullable Task snapshot) {
                StreamingEventKind forwardedEvent = event instanceof TaskArtifactUpdateEvent artifactUpdate
                        ? TaskArtifactUpdateEvent.builder(artifactUpdate).build()
                        : event;
                Task forwardedSnapshot = snapshot == null ? null : Task.builder(snapshot).build();
                return awaitWorker(forwardingWorker, () -> sender.sendNotification(forwardedEvent, forwardedSnapshot));
            }

            @Override
            public boolean sendTaskProposalStatusNotification(TaskStatusUpdateEvent event, Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendTaskProposalStatusNotification(event, snapshot));
            }

            @Override
            public boolean sendLegacyTaskSnapshot(Task snapshot) {
                return awaitWorker(forwardingWorker, () -> sender.sendLegacyTaskSnapshot(snapshot));
            }
        };
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queueManager = new InMemoryQueueManager(taskStore, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, taskStore, delegatingSender, queueManager);
        processor.setPushNotificationExecutor(Runnable::run);
        EventQueue mainQueue = EventQueueUtil.getEventQueueBuilder(bus)
                .taskId(taskId).taskStateProvider(taskStore).build();
        EventQueue producer = mainQueue.tap();

        CountDownLatch earlierAdmission = new CountDownLatch(1);
        CountDownLatch laterDeltaProcessed = new CountDownLatch(1);
        testHttpClient.latch = new CountDownLatch(12);
        AtomicReference<Runnable> releaseEarlierPush = new AtomicReference<>();
        AtomicBoolean deferred = new AtomicBoolean();
        processor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void admitPushNotifications(org.a2aproject.sdk.server.events.CommittedTaskBatch batch,
                    Runnable invokeSender, java.util.function.Consumer<Throwable> reportFailure) {
                if (deferred.compareAndSet(false, true)) {
                    releaseEarlierPush.set(invokeSender);
                    earlierAdmission.countDown();
                    return;
                }
                invokeSender.run();
            }

            @Override
            public void onEventProcessed(String processedTaskId, Event event) {
                if (taskId.equals(processedTaskId) && event instanceof TaskStatusUpdateEvent status
                        && status.status().state() == TaskState.TASK_STATE_COMPLETED) {
                    laterDeltaProcessed.countDown();
                }
            }

            @Override
            public void onTaskFinalized(String finalizedTaskId) {
            }
        });

        try {
            processor.start();
            producer.enqueueEvent(new TaskStatusUpdateEvent(taskId,
                    new TaskStatus(TaskState.TASK_STATE_WORKING, null,
                            OffsetDateTime.parse("2026-01-01T00:00:00Z")), "ctx456", Map.of("order", "earlier")));
            assertTrue(earlierAdmission.await(5, TimeUnit.SECONDS));

            ExecutorSubmissionService service = new ExecutorSubmissionService(
                    ExecutorSubmissionService.forTaskStore(taskStore),
                    java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(5));
            TaskSnapshot noOpBase = java.util.Objects.requireNonNull(taskStore.readSnapshot(taskId));
            ProposalResult noOp = service.submitTask(
                    new TaskProposal("webhook-noop", noOpBase, noOpBase.task()), producer)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertFalse(((ProposalResult.Committed) noOp).changed());

            TaskSnapshot changedBase = java.util.Objects.requireNonNull(taskStore.readSnapshot(taskId));
            Task changed = Task.builder(changedBase.task())
                    .status(new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED,
                            null, OffsetDateTime.parse("2026-01-02T00:00:00Z")))
                    .artifacts(List.of(Artifact.builder().artifactId("proposal-artifact")
                            .parts(List.of(new TextPart("proposal output"))).build()))
                    .build();
            ProposalResult changedResult = service.submitTask(
                    new TaskProposal("webhook-change", changedBase, changed), producer)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(((ProposalResult.Committed) changedResult).changed());

            TaskSnapshot artifactBase = java.util.Objects.requireNonNull(taskStore.readSnapshot(taskId));
            Task artifactOnly = Task.builder(artifactBase.task()).artifacts(List.of(
                    artifactBase.task().artifacts().get(0),
                    Artifact.builder().artifactId("proposal-artifact-two")
                            .parts(List.of(new TextPart("second output"))).build())).build();
            ProposalResult artifactResult = service.submitTask(
                    new TaskProposal("webhook-artifact-only", artifactBase, artifactOnly), producer)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(((ProposalResult.Committed) artifactResult).changed());

            TaskSnapshot statusBase = java.util.Objects.requireNonNull(taskStore.readSnapshot(taskId));
            Task statusOnly = Task.builder(statusBase.task())
                    .status(new TaskStatus(TaskState.TASK_STATE_WORKING,
                            null, OffsetDateTime.parse("2026-01-03T00:00:00Z")))
                    .build();
            ProposalResult statusResult = service.submitTask(
                    new TaskProposal("webhook-status-only", statusBase, statusOnly), producer)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(((ProposalResult.Committed) statusResult).changed());

            producer.enqueueEvent(new TaskStatusUpdateEvent(taskId,
                    new TaskStatus(TaskState.TASK_STATE_COMPLETED, null,
                            OffsetDateTime.parse("2026-01-04T00:00:00Z")), "ctx456", Map.of("order", "later")));
            assertTrue(laterDeltaProcessed.await(5, TimeUnit.SECONDS));
            assertTrue(testHttpClient.events.isEmpty(), "all later webhook work must remain behind deferred admission");

            Runnable invokeEarlier = releaseEarlierPush.get();
            invokeEarlier.run();
            assertTrue(testHttpClient.latch.await(5, TimeUnit.SECONDS), "all versioned webhooks should be delivered");

            List<StreamingEventKind> legacyEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v10"))
                    .map(CapturedNotification::event).toList();
            List<StreamingEventKind> currentEvents = testHttpClient.notifications.stream()
                    .filter(notification -> notification.url().endsWith("/v11"))
                    .map(CapturedNotification::event).toList();
            assertEquals(List.of(TaskStatusUpdateEvent.class, Task.class, Task.class, Task.class, Task.class,
                    TaskStatusUpdateEvent.class), legacyEvents.stream().map(Object::getClass).toList(),
                    "older-version outputs contain one summary per no-op/proposal and no covered deltas");
            assertEquals(6, currentEvents.size(), "1.1 retains canonical proposal events and skips the no-op");
            assertInstanceOf(TaskStatusUpdateEvent.class, currentEvents.get(0));
            assertInstanceOf(TaskStatusUpdateEvent.class, currentEvents.get(1));
            assertInstanceOf(TaskArtifactUpdateEvent.class, currentEvents.get(2));
            assertInstanceOf(TaskArtifactUpdateEvent.class, currentEvents.get(3));
            assertInstanceOf(TaskStatusUpdateEvent.class, currentEvents.get(4));
            assertInstanceOf(TaskStatusUpdateEvent.class, currentEvents.get(5));
        } finally {
            if (releaseEarlierPush.get() != null) {
                releaseEarlierPush.get().run();
            }
            EventQueueUtil.stop(processor);
            mainQueue.close(true);
            forwardingWorker.shutdownNow();
        }
    }

    private static <T> T awaitWorker(Executor worker, Supplier<T> operation) {
        try {
            return CompletableFuture.supplyAsync(operation, worker).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("worker forwarding was interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AssertionError("worker forwarding failed", e);
        }
    }

    private static CommittedTaskBatch statusOnlyBatch(Task initial, TaskStatusUpdateEvent status) {
        Task proposed = Task.builder(initial).status(status.status()).generation(status.generation()).build();
        return new CommittedTaskBatch(new TaskSnapshot(proposed, new TaskRevision(1)), List.of(status));
    }

    private static CommittedTaskBatch statusAndArtifactBatch(Task initial, TaskStatusUpdateEvent status,
            Artifact artifact) {
        TaskArtifactUpdateEvent artifactEvent = new TaskArtifactUpdateEvent(initial.id(), artifact,
                initial.contextId(), false, true, null, 2);
        Task proposed = Task.builder(initial).status(status.status()).artifacts(List.of(artifact))
                .generation(artifactEvent.generation()).build();
        return new CommittedTaskBatch(new TaskSnapshot(proposed, new TaskRevision(2)),
                List.of(status, artifactEvent));
    }

    private static void assertProposalArtifact(Task summary, String expectedArtifactId) {
        assertEquals(1, summary.artifacts().size());
        Artifact artifact = summary.artifacts().get(0);
        assertEquals(expectedArtifactId, artifact.artifactId());
        assertEquals("proposal output", assertInstanceOf(TextPart.class, artifact.parts().get(0)).text());
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
        final List<CapturedNotification> notifications = Collections.synchronizedList(new ArrayList<>());
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
                    notifications.add(new CapturedNotification(url, event));
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

    private record CapturedNotification(String url, StreamingEventKind event) {
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
