package org.a2aproject.sdk.server.requesthandlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.TaskCommitSlotCallback;
import org.a2aproject.sdk.server.tasks.TaskMutation;
import org.a2aproject.sdk.server.tasks.TaskMutationNotCommittedException;
import org.a2aproject.sdk.server.tasks.TaskEnrollmentCoordinator;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Existing-task ordinary input: guarded atomic commit, enrollment-before-input, partial-effect reporting. */
class OrdinaryInputMutationTest {

    private static final AgentExecutor IDLE = new AgentExecutor() {
        @Override
        public void execute(RequestContext context, AgentEmitter emitter) {
            emitter.complete();
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) {
        }
    };

    private OrdinaryInputTestSupport fixture;

    @AfterEach
    void stop() {
        if (fixture != null) {
            fixture.stop();
        }
    }

    private void seedWorkingTask(String id) {
        fixture.taskStore.save(Task.builder().id(id).contextId("ctx")
                .status(new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED)).history(List.of())
                .artifacts(List.of()).timeline(List.of()).generation(1).build(), false);
    }

    private static TaskPushNotificationConfig hook() {
        return TaskPushNotificationConfig.builder().url("http://hook.example/").build();
    }

    private static SendInputParams input(String taskId, MessageSendConfiguration config) {
        return SendInputParams.builder()
                .message(OrdinaryInputTestSupport.userMessage("m-" + System.nanoTime(), "more", taskId, "ctx"))
                .configuration(config).build();
    }

    @Test
    void guardedInputCommitsOnceWithAdvancedGenerationAndReturnsCommittedView() throws Exception {
        fixture = new OrdinaryInputTestSupport(IDLE);
        seedWorkingTask("t1");
        EventKind result = fixture.handler.onSendInput(input("t1",
                MessageSendConfiguration.builder().returnImmediately(true).ifGenerationMatch(1L).build()),
                OrdinaryInputTestSupport.context(false));
        // The returned view is the committed input snapshot; the IDLE executor may advance the stored task later.
        Task returned = assertInstanceOf(Task.class, result);
        assertEquals(1, returned.history().stream().filter(m -> m.messageId().startsWith("m-")).count());
        List<org.a2aproject.sdk.spec.TimelineEntry> inputEntries = returned.timeline().stream()
                .filter(t -> t.entry() instanceof org.a2aproject.sdk.spec.TimelineEntry.MessageValue).toList();
        assertEquals(1, inputEntries.size(), "the input is recorded once");
        assertEquals(2, inputEntries.get(0).generation(),
                "the guarded input advanced the seeded generation 1 by exactly one");
    }

    @Test
    void staleGuardRejectsBeforeAnyInputButEnrollmentStaysVisibleAndIsReported() {
        fixture = new OrdinaryInputTestSupport(IDLE);
        seedWorkingTask("t1");
        A2AError error = assertThrows(TaskGenerationMismatchError.class, () -> fixture.handler.onSendInput(input("t1",
                MessageSendConfiguration.builder().ifGenerationMatch(99L).taskPushNotificationConfig(hook()).build()),
                OrdinaryInputTestSupport.context(false)));
        Task stored = fixture.taskStore.get("t1");
        assertEquals(1, stored.generation(), "rejected input must not advance the generation");
        assertTrue(stored.history().isEmpty(), "rejected input must not be recorded");
        assertEquals(1, fixture.configWrites.size(), "enrollment preceded the input commit");
        assertTrue(fixture.configDeletes.isEmpty(), "the visible enrollment is never deleted or restored");
        assertEquals("t1", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_TASK_ID));
        assertEquals(Boolean.TRUE, error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_CONFIG_ENROLLED));
        assertEquals("not-committed", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_INPUT_OUTCOME));
    }

    private static final String CONCURRENT_URL = "http://concurrent.example/";

    /** A concurrent config update that lands after enrollment but before the input mutation. */
    private static final class ConcurrentUpdatingStore extends InMemoryTaskStore {
        private final InMemoryPushNotificationConfigStore configs;
        private final AtomicBoolean armed = new AtomicBoolean(true);
        private final RuntimeException failure;

        ConcurrentUpdatingStore(InMemoryPushNotificationConfigStore configs, RuntimeException failure) {
            this.configs = configs;
            this.failure = failure;
        }

        @Override
        public CommittedMutation mutate(String taskId, TaskMutation mutation, TaskCommitSlotCallback callback) {
            if (armed.compareAndSet(true, false)) {
                String enrolledId = configs.getInfo(
                        new org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams(taskId))
                        .configs().get(0).id();
                configs.setInfo(TaskPushNotificationConfig.builder().taskId(taskId).id(enrolledId)
                        .url(CONCURRENT_URL).build());
                if (failure != null) {
                    throw failure;
                }
            }
            return super.mutate(taskId, mutation, callback);
        }
    }

    private static String survivingUrl(InMemoryPushNotificationConfigStore configs) {
        return configs.getInfo(new org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams("t1"))
                .configs().get(0).url();
    }

    @Test
    void concurrentConfigUpdateBetweenEnrollmentAndGuardRejectionSurvives() {
        InMemoryPushNotificationConfigStore configs = new InMemoryPushNotificationConfigStore();
        // armed with no failure: the update lands mid-request, then the stale guard rejects.
        fixture = new OrdinaryInputTestSupport(IDLE, configs, new ConcurrentUpdatingStore(configs, null));
        seedWorkingTask("t1");
        A2AError error = assertThrows(TaskGenerationMismatchError.class, () -> fixture.handler.onSendInput(input("t1",
                MessageSendConfiguration.builder().ifGenerationMatch(99L).taskPushNotificationConfig(hook()).build()),
                OrdinaryInputTestSupport.context(false)));
        assertEquals(CONCURRENT_URL, survivingUrl(configs),
                "failure handling must not revert or delete a concurrent update");
        assertTrue(fixture.configDeletes.isEmpty(), "no delete or restore after a partial effect");
        assertEquals("t1", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_TASK_ID));
        assertEquals(1, fixture.taskStore.get("t1").generation(), "rejected input stays uncommitted");
    }

    @Test
    void commitFailureAfterEnrollmentReportsPartialEffectAndConcurrentUpdateSurvives() {
        InMemoryPushNotificationConfigStore configs = new InMemoryPushNotificationConfigStore();
        fixture = new OrdinaryInputTestSupport(IDLE, configs, new ConcurrentUpdatingStore(configs,
                new TaskMutationNotCommittedException("t1", "injected commit failure")));
        seedWorkingTask("t1");
        A2AError error = assertThrows(A2AError.class, () -> fixture.handler.onSendInput(input("t1",
                MessageSendConfiguration.builder().taskPushNotificationConfig(hook()).build()),
                OrdinaryInputTestSupport.context(false)));
        assertEquals(1, fixture.taskStore.get("t1").generation());
        assertEquals(1, fixture.configWrites.size());
        assertTrue(fixture.configDeletes.isEmpty(), "existing-task enrollment is never routed to orphan cleanup");
        assertEquals(CONCURRENT_URL, survivingUrl(configs), "a concurrent update must survive commit failure");
        assertEquals("t1", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_TASK_ID));
        assertEquals(Boolean.TRUE, error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_CONFIG_ENROLLED));
        assertEquals("not-committed", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_INPUT_OUTCOME));
        assertTrue(error.getDetails().containsKey(TaskEnrollmentCoordinator.DETAIL_CONFIG_ID));
    }

    @Test
    void streamingCommitFailureAfterEnrollmentReportsTheSamePartialEffectAsUnary() {
        InMemoryPushNotificationConfigStore configs = new InMemoryPushNotificationConfigStore();
        fixture = new OrdinaryInputTestSupport(IDLE, configs, new ConcurrentUpdatingStore(configs,
                new TaskMutationNotCommittedException("t1", "injected commit failure")));
        seedWorkingTask("t1");
        A2AError error = assertThrows(A2AError.class, () -> fixture.handler.onSendInputStream(input("t1",
                MessageSendConfiguration.builder().taskPushNotificationConfig(hook()).build()),
                OrdinaryInputTestSupport.context(false)));
        assertEquals("not-committed", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_INPUT_OUTCOME));
        assertEquals(CONCURRENT_URL, survivingUrl(configs));
        assertTrue(fixture.configDeletes.isEmpty());
    }

    @Test
    void indeterminateCommitOutcomeIsReportedAsUnknownNotNotCommitted() {
        InMemoryPushNotificationConfigStore configs = new InMemoryPushNotificationConfigStore();
        fixture = new OrdinaryInputTestSupport(IDLE, configs, new ConcurrentUpdatingStore(configs,
                new org.a2aproject.sdk.server.tasks.TaskMutationOutcomeUnknownException("t1", "lost connection")));
        seedWorkingTask("t1");
        A2AError error = assertThrows(A2AError.class, () -> fixture.handler.onSendInput(input("t1",
                MessageSendConfiguration.builder().taskPushNotificationConfig(hook()).build()),
                OrdinaryInputTestSupport.context(false)));
        assertEquals("unknown", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_INPUT_OUTCOME));
        assertTrue(fixture.configDeletes.isEmpty());
    }

    @Test
    void streamingInputReportsTheSamePartialEffectAsUnary() {
        fixture = new OrdinaryInputTestSupport(IDLE);
        seedWorkingTask("t1");
        A2AError error = assertThrows(A2AError.class, () -> fixture.handler.onSendInputStream(input("t1",
                MessageSendConfiguration.builder().ifGenerationMatch(99L).taskPushNotificationConfig(hook()).build()),
                OrdinaryInputTestSupport.context(false)));
        assertEquals(Boolean.TRUE, error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_CONFIG_ENROLLED));
        assertEquals(1, fixture.taskStore.get("t1").generation());
        assertTrue(fixture.configDeletes.isEmpty());
    }

    @Test
    void artifactOnlyInputOnExistingTaskCommitsWithoutInvokingTheExecutor() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        fixture = new OrdinaryInputTestSupport(new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                invoked.set(true);
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
            }
        });
        seedWorkingTask("t1");
        TaskArtifactUpdateEvent artifact = TaskArtifactUpdateEvent.builder().taskId("t1").contextId("ctx")
                .artifact(Artifact.builder().artifactId("a1").parts(new TextPart("d")).build()).build();
        EventKind result = fixture.handler.onSendInput(SendInputParams.builder()
                .artifactUpdates(List.of(artifact)).build(), OrdinaryInputTestSupport.context(true));
        Task stored = fixture.taskStore.get("t1");
        assertEquals(1, stored.artifacts().size());
        assertEquals(org.a2aproject.sdk.spec.Message.Role.ROLE_USER, stored.artifacts().get(0).role());
        assertFalse(invoked.get(), "artifact-only input is a mutation, not an executor invocation");
        assertInstanceOf(Task.class, result);
    }

    @Test
    void userArtifactsAreRejectedWhenTheEffectiveCardDoesNotEnableThem() {
        fixture = new OrdinaryInputTestSupport(IDLE);
        seedWorkingTask("t1");
        TaskArtifactUpdateEvent artifact = TaskArtifactUpdateEvent.builder().taskId("t1").contextId("ctx")
                .artifact(Artifact.builder().artifactId("a1").parts(new TextPart("d")).build()).build();
        assertThrows(InvalidParamsError.class, () -> fixture.handler.onSendInput(SendInputParams.builder()
                .artifactUpdates(List.of(artifact)).build(), OrdinaryInputTestSupport.context(false)));
        assertEquals(1, fixture.taskStore.get("t1").generation());
        assertTrue(fixture.taskStore.get("t1").artifacts().isEmpty());
    }

    @Test
    void newTaskEnrollmentFailureLeavesNoTaskRecordAndNoExecutorEffect() {
        InMemoryPushNotificationConfigStore failingConfigs = new InMemoryPushNotificationConfigStore() {
            @Override
            public TaskPushNotificationConfig setInfo(TaskPushNotificationConfig config) {
                throw new IllegalStateException("config store unavailable");
            }
        };
        fixture = new OrdinaryInputTestSupport(new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                emitter.startWork();
                emitter.complete();
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
            }
        }, failingConfigs);
        List<StreamingEventKind> events = null;
        Exception surfaced = null;
        try {
            events = OrdinaryInputTestSupport.drain(fixture.handler.onSendInputStream(SendInputParams.builder()
                    .message(OrdinaryInputTestSupport.userMessage("m1", "hi"))
                    .configuration(MessageSendConfiguration.builder().taskPushNotificationConfig(hook()).build())
                    .build(), OrdinaryInputTestSupport.context(false)));
        } catch (Exception expected) {
            surfaced = expected;
        }
        assertTrue(surfaced != null || events.stream().noneMatch(Task.class::isInstance),
                "enrollment failure must surface as an error, never as a created Task: " + events);
        assertTrue(fixture.taskStore.list(org.a2aproject.sdk.spec.ListTasksParams.builder().build(), null)
                .tasks().isEmpty(), "enrollment failure precedes any task mutation");
        assertTrue(fixture.configDeletes.isEmpty(), "nothing was enrolled, so nothing is compensated");
    }

    @Test
    void firstTaskProposalWhoseEnrollmentFailsIsKnownNotCommittedAndLeavesNoTask() throws Exception {
        InMemoryPushNotificationConfigStore failingConfigs = new InMemoryPushNotificationConfigStore() {
            @Override
            public TaskPushNotificationConfig setInfo(TaskPushNotificationConfig config) {
                throw new IllegalStateException("config store unavailable");
            }
        };
        java.util.concurrent.atomic.AtomicReference<org.a2aproject.sdk.server.tasks.ProposalResult> outcome =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch delivered = new java.util.concurrent.CountDownLatch(1);
        fixture = new OrdinaryInputTestSupport(new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                emitter.addProposalResultListener(result -> {
                    outcome.set(result);
                    delivered.countDown();
                });
                emitter.addTask(Task.builder().id(context.getTaskId()).contextId(context.getContextId())
                        .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).history(List.of())
                        .artifacts(List.of()).timeline(List.of()).generation(0).build());
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
            }
        }, failingConfigs);
        try {
            OrdinaryInputTestSupport.drain(fixture.handler.onSendInputStream(SendInputParams.builder()
                    .message(OrdinaryInputTestSupport.userMessage("m1", "hi"))
                    .configuration(MessageSendConfiguration.builder().taskPushNotificationConfig(hook()).build())
                    .build(), OrdinaryInputTestSupport.context(false)));
        } catch (Exception expected) {
            // the stream may surface the rejected proposal as an error
        }
        assertTrue(delivered.await(10, java.util.concurrent.TimeUnit.SECONDS), "the proposal result is delivered");
        org.a2aproject.sdk.server.tasks.ProposalResult.Failed failed = assertInstanceOf(
                org.a2aproject.sdk.server.tasks.ProposalResult.Failed.class, outcome.get());
        assertEquals(org.a2aproject.sdk.server.tasks.ProposalResult.Failed.CommitCertainty.KNOWN_NOT_COMMITTED,
                failed.certainty(), "enrollment precedes the mutation, so nothing could have committed");
        assertTrue(fixture.taskStore.list(org.a2aproject.sdk.spec.ListTasksParams.builder().build(), null)
                .tasks().isEmpty());
    }

    @Test
    void newTaskWebhookStillEnrollsOnAStoreThatIsNotAtomic() throws Exception {
        fixture = new OrdinaryInputTestSupport(IDLE_WORKING);
        // Rebuild the handler over a legacy-only wrapper that hides AtomicTaskStore.
        org.a2aproject.sdk.server.tasks.TaskStore legacy = new LegacyOnlyStore(fixture.taskStore);
        DefaultRequestHandler legacyHandler = DefaultRequestHandler.builder()
                .agentExecutor(IDLE_WORKING)
                .taskStore(legacy)
                .queueManager(fixture.queues)
                .pushConfigStore(fixture.configStore)
                .pushNotificationsEnabled(true)
                .mainEventBusProcessor(fixture.processor)
                .executor(fixture.workers)
                .eventConsumerExecutor(fixture.workers)
                .authorizationRequired(false)
                .build();
        OrdinaryInputTestSupport.drain(legacyHandler.onSendInputStream(SendInputParams.builder()
                .message(OrdinaryInputTestSupport.userMessage("m1", "hi"))
                .configuration(MessageSendConfiguration.builder().taskPushNotificationConfig(hook()).build())
                .build(), OrdinaryInputTestSupport.context(false)));
        assertEquals(1, fixture.configWrites.size(), "legacy stores keep their original enrollment path");
    }

    private static final AgentExecutor IDLE_WORKING = new AgentExecutor() {
        @Override
        public void execute(RequestContext context, AgentEmitter emitter) {
            emitter.startWork();
            emitter.complete();
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) {
        }
    };

    /** Exposes only the legacy {@link org.a2aproject.sdk.server.tasks.TaskStore} contract. */
    private static final class LegacyOnlyStore implements org.a2aproject.sdk.server.tasks.TaskStore,
            org.a2aproject.sdk.server.tasks.TaskStateProvider {
        private final InMemoryTaskStore delegate;

        LegacyOnlyStore(InMemoryTaskStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public void save(Task task, boolean isReplicated) {
            delegate.save(task, isReplicated);
        }

        @Override
        public Task get(String taskId) {
            return delegate.get(taskId);
        }

        @Override
        public void delete(String taskId) {
            delegate.delete(taskId);
        }

        @Override
        public org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult list(org.a2aproject.sdk.spec.ListTasksParams params,
                org.a2aproject.sdk.server.ServerCallContext context) {
            return delegate.list(params, context);
        }

        @Override
        public boolean isTaskActive(String taskId) {
            return delegate.isTaskActive(taskId);
        }

        @Override
        public boolean isTaskFinalized(String taskId) {
            return delegate.isTaskFinalized(taskId);
        }
    }

    @Test
    void publicationFailureAfterAnExistingTaskCommitIsReportedAsAnUnknownPartialEffect() throws Exception {
        fixture = new OrdinaryInputTestSupport(IDLE);
        seedWorkingTask("t1");
        // A live queue makes the commit publish to observers; fail that ordered publication after the commit.
        fixture.queues.createOrTap("t1");
        org.a2aproject.sdk.server.events.OrderedTaskPublisher failing = org.mockito.Mockito.spy(
                new org.a2aproject.sdk.server.events.OrderedTaskPublisher());
        org.mockito.Mockito.doReturn(java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalStateException("publication failed"))).when(failing).publish(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        java.lang.reflect.Field publisher = org.a2aproject.sdk.server.events.MainEventBusProcessor.class
                .getDeclaredField("orderedTaskPublisher");
        publisher.setAccessible(true);
        publisher.set(fixture.processor, failing);

        A2AError error = assertThrows(A2AError.class, () -> fixture.handler.onSendInput(input("t1",
                MessageSendConfiguration.builder().taskPushNotificationConfig(hook()).build()),
                OrdinaryInputTestSupport.context(false)));

        assertEquals(2L, fixture.taskStore.get("t1").generation(), "the input did commit before publication failed");
        assertEquals("unknown", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_INPUT_OUTCOME));
        assertEquals(Boolean.TRUE, error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_CONFIG_ENROLLED));
        assertTrue(fixture.configDeletes.isEmpty(), "committed input and enrollment are never compensated");
    }
}
