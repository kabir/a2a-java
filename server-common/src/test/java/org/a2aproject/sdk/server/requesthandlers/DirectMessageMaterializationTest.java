package org.a2aproject.sdk.server.requesthandlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.TaskCommitSlotCallback;
import org.a2aproject.sdk.server.tasks.TaskMutation;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A no-task direct Message creates no task, input, artifact or webhook-configuration effect. */
class DirectMessageMaterializationTest {

    private OrdinaryInputTestSupport fixture;

    @AfterEach
    void stop() {
        if (fixture != null) {
            fixture.stop();
        }
    }

    private static final AgentExecutor DIRECT = new AgentExecutor() {
        @Override
        public void execute(RequestContext context, AgentEmitter emitter) {
            emitter.sendMessage("direct reply");
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) {
        }
    };

    private static MessageSendConfiguration webhookConfig(boolean returnImmediately, Long guard) {
        return MessageSendConfiguration.builder().returnImmediately(returnImmediately)
                .taskPushNotificationConfig(TaskPushNotificationConfig.builder().url("http://hook.example/").build())
                .ifGenerationMatch(guard).build();
    }

    private static SendInputParams withArtifacts(Message message, MessageSendConfiguration config) {
        TaskArtifactUpdateEvent artifact = TaskArtifactUpdateEvent.builder().taskId("").contextId("")
                .artifact(Artifact.builder().artifactId("a1").parts(new TextPart("data")).build()).build();
        return SendInputParams.builder().message(message).artifactUpdates(List.of(artifact))
                .configuration(config).build();
    }

    private void assertNothingPersisted() throws Exception {
        TimeUnit.MILLISECONDS.sleep(300);
        assertTrue(fixture.taskStore.list(
                org.a2aproject.sdk.spec.ListTasksParams.builder().build(), null).tasks().isEmpty(),
                "a direct Message must not leave any task");
        assertTrue(fixture.configWrites.isEmpty(), "no webhook configuration may be written: " + fixture.configWrites);
        assertTrue(fixture.configDeletes.isEmpty(), "no provisional configuration to clean up");
        assertTrue(fixture.configStore.getInfo(new org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams(
                "any-task")).configs().isEmpty(), "no task-bound webhook configuration may exist");
    }

    @Test
    void unaryDirectMessageWithPendingArtifactsAndWebhookCreatesNothing() throws Exception {
        fixture = new OrdinaryInputTestSupport(DIRECT);
        EventKind result = fixture.handler.onSendInput(
                withArtifacts(OrdinaryInputTestSupport.userMessage("m1", "hi"), webhookConfig(false, null)),
                OrdinaryInputTestSupport.context(true));
        assertInstanceOf(Message.class, result);
        assertNothingPersisted();
    }

    @Test
    void unaryDirectMessageWithReturnImmediatelyDoesNotManufactureATask() throws Exception {
        fixture = new OrdinaryInputTestSupport(DIRECT);
        EventKind result = fixture.handler.onSendInput(
                withArtifacts(OrdinaryInputTestSupport.userMessage("m1", "hi"), webhookConfig(true, null)),
                OrdinaryInputTestSupport.context(true));
        assertInstanceOf(Message.class, result, "returnImmediately must not force a phantom task");
        assertNothingPersisted();
    }

    @Test
    void streamingDirectMessageCreatesNothingAndEmitsOnlyTheMessage() throws Exception {
        fixture = new OrdinaryInputTestSupport(DIRECT);
        List<StreamingEventKind> events = OrdinaryInputTestSupport.drain(fixture.handler.onSendInputStream(
                withArtifacts(OrdinaryInputTestSupport.userMessage("m1", "hi"), webhookConfig(false, null)),
                OrdinaryInputTestSupport.context(true)));
        assertEquals(1, events.size());
        assertInstanceOf(Message.class, events.get(0));
        assertNothingPersisted();
    }

    @Test
    void legacyMessageOnlyDirectResponseCreatesNothing() throws Exception {
        fixture = new OrdinaryInputTestSupport(DIRECT);
        EventKind result = fixture.handler.onMessageSend(
                new MessageSendParams(OrdinaryInputTestSupport.userMessage("m1", "hi"), webhookConfig(false, null), null),
                OrdinaryInputTestSupport.context(false));
        assertInstanceOf(Message.class, result);
        assertNothingPersisted();
    }

    /** Counts task mutations so a split opening-then-output commit would be visible. */
    private static final class CountingStore extends InMemoryTaskStore {
        final AtomicInteger mutations = new AtomicInteger();

        @Override
        public CommittedMutation mutate(String taskId, TaskMutation mutation, TaskCommitSlotCallback callback) {
            mutations.incrementAndGet();
            return super.mutate(taskId, mutation, callback);
        }
    }

    private static AgentExecutor capturing(AtomicReference<String> taskIdSink,
            java.util.function.Consumer<AgentEmitter> body) {
        return new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                taskIdSink.set(context.getTaskId());
                body.accept(emitter);
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
            }
        };
    }

    @Test
    void lateTaskOutputAfterADirectMessageIsRejectedAndLeavesNothing() throws Exception {
        AtomicReference<String> taskId = new AtomicReference<>();
        fixture = new OrdinaryInputTestSupport(capturing(taskId, emitter -> {
            emitter.sendMessage("direct reply");
            try {
                emitter.startWork();
            } catch (RuntimeException expectedRejection) {
                // the late task-bearing output is rejected for this invocation
            }
        }));
        List<StreamingEventKind> events = OrdinaryInputTestSupport.drain(fixture.handler.onSendInputStream(
                withArtifacts(OrdinaryInputTestSupport.userMessage("m1", "hi"), webhookConfig(false, null)),
                OrdinaryInputTestSupport.context(true)));
        assertTrue(events.stream().noneMatch(Task.class::isInstance), "no task may be revealed: " + events);
        assertNothingPersisted();
        assertTrue(fixture.pushed.stream().noneMatch(Task.class::isInstance), "no webhook push for a direct branch");
    }

    @Test
    void directBranchClosesItsQueuesEvenWithReturnImmediately() throws Exception {
        AtomicReference<String> taskId = new AtomicReference<>();
        fixture = new OrdinaryInputTestSupport(capturing(taskId, emitter -> emitter.sendMessage("direct reply")));
        EventKind result = fixture.handler.onSendInput(
                withArtifacts(OrdinaryInputTestSupport.userMessage("m1", "hi"), webhookConfig(true, null)),
                OrdinaryInputTestSupport.context(true));
        assertInstanceOf(Message.class, result);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (fixture.queues.get(taskId.get()) != null && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertEquals(null, fixture.queues.get(taskId.get()), "the direct branch must not leak its queue");
        assertNothingPersisted();
    }

    private void assertSingleMaterializingMutation(CountingStore store, List<StreamingEventKind> events,
            int expectedMutations) {
        assertEquals(expectedMutations, store.mutations.get(),
                "opening batch + first output must be ONE mutation, not a separate input commit");
        String taskId = events.stream().filter(TaskMessageUpdateEvent.class::isInstance)
                .map(e -> ((TaskMessageUpdateEvent) e).taskId()).findFirst().orElseThrow();
        Task stored = store.get(taskId);
        assertEquals(1, stored.history().stream().filter(m -> "m1".equals(m.messageId())).count(),
                "the committed task covers the opening message once");
        assertEquals(1, stored.artifacts().stream().filter(a -> "a1".equals(a.artifactId())).count(),
                "the committed task covers the pending artifact once");
        List<Long> generations = events.stream().map(OrdinaryInputTestSupport::generationOf).toList();
        assertEquals(generations.stream().sorted().toList(), generations, "batch events keep ascending generations");
        assertEquals(1L, generations.get(0), "the opening message is the first committed generation");
        assertEquals(1, events.stream().filter(e -> e instanceof TaskMessageUpdateEvent update
                && "m1".equals(update.message().messageId())).count(), "opening message event is not duplicated");
        assertEquals(1, events.stream().filter(e -> e instanceof TaskArtifactUpdateEvent update
                && "a1".equals(update.artifact().artifactId())).count(), "pending artifact event is not duplicated");
    }

    @Test
    void statusFirstOutputMaterializesOpeningBatchInOneMutation() throws Exception {
        CountingStore store = new CountingStore();
        fixture = new OrdinaryInputTestSupport(capturing(new AtomicReference<>(), emitter -> {
            emitter.startWork();
            emitter.complete();
        }), new InMemoryPushNotificationConfigStore(), store);
        List<StreamingEventKind> events = OrdinaryInputTestSupport.drain(fixture.handler.onSendInputStream(
                withArtifacts(OrdinaryInputTestSupport.userMessage("m1", "hi"), webhookConfig(false, null)),
                OrdinaryInputTestSupport.context(true)));
        assertSingleMaterializingMutation(store, events, 2);
    }

    @Test
    void artifactFirstOutputMaterializesOpeningBatchInOneMutation() throws Exception {
        CountingStore store = new CountingStore();
        fixture = new OrdinaryInputTestSupport(capturing(new AtomicReference<>(), emitter -> {
            emitter.addArtifact(List.of(new TextPart("agent data")), "agent-artifact", null, null);
            emitter.complete();
        }), new InMemoryPushNotificationConfigStore(), store);
        List<StreamingEventKind> events = OrdinaryInputTestSupport.drain(fixture.handler.onSendInputStream(
                withArtifacts(OrdinaryInputTestSupport.userMessage("m1", "hi"), webhookConfig(false, null)),
                OrdinaryInputTestSupport.context(true)));
        assertSingleMaterializingMutation(store, events, 2);
    }

    @Test
    void unaryTaskCreationRevealsTheCommittedSnapshotWithTheOpeningBatchOnce() throws Exception {
        CountingStore store = new CountingStore();
        fixture = new OrdinaryInputTestSupport(capturing(new AtomicReference<>(), emitter -> {
            emitter.startWork();
            emitter.complete();
        }), new InMemoryPushNotificationConfigStore(), store);
        EventKind result = fixture.handler.onSendInput(
                withArtifacts(OrdinaryInputTestSupport.userMessage("m1", "hi"), webhookConfig(false, null)),
                OrdinaryInputTestSupport.context(true));
        Task task = assertInstanceOf(Task.class, result);
        assertEquals(1, task.history().stream().filter(m -> "m1".equals(m.messageId())).count());
        assertEquals(1, task.artifacts().stream().filter(a -> "a1".equals(a.artifactId())).count());
        assertEquals(TaskState.TASK_STATE_COMPLETED, task.status().state());
    }

    @Test
    void taskCreatingOutputMaterializesOpeningMessageArtifactAndConfigTogether() throws Exception {
        fixture = new OrdinaryInputTestSupport(new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                emitter.startWork();
                emitter.complete();
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
            }
        });
        List<StreamingEventKind> events = OrdinaryInputTestSupport.drain(fixture.handler.onSendInputStream(
                withArtifacts(OrdinaryInputTestSupport.userMessage("m1", "hi"), webhookConfig(false, null)),
                OrdinaryInputTestSupport.context(true)));
        List<Long> generations = events.stream().map(OrdinaryInputTestSupport::generationOf)
                .filter(g -> g > 0).toList();
        assertTrue(events.stream().anyMatch(TaskMessageUpdateEvent.class::isInstance),
                "the opening message must be committed with the task: " + events);
        assertTrue(events.stream().anyMatch(TaskArtifactUpdateEvent.class::isInstance),
                "the pending artifact must be committed with the task: " + events);
        assertEquals(generations.stream().sorted().toList(), generations, "generations stay ordered");
        assertEquals(1, fixture.configWrites.size(), "exactly one enrollment at materialization");
        assertTrue(fixture.configDeletes.isEmpty(), "committed enrollment is never compensated");
        String taskId = fixture.taskStore.list(org.a2aproject.sdk.spec.ListTasksParams.builder().build(), null)
                .tasks().get(0).id();
        Task stored = fixture.taskStore.get(taskId);
        assertEquals(TaskState.TASK_STATE_COMPLETED, stored.status().state());
        assertEquals(1, stored.artifacts().size());
        assertTrue(stored.history().stream().anyMatch(m -> "m1".equals(m.messageId())));
    }
}
