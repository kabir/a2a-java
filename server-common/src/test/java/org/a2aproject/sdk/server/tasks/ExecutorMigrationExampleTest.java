package org.a2aproject.sdk.server.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.events.EventQueue;
import org.a2aproject.sdk.server.events.EventQueueUtil;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Executable migration examples for custom executors that used to replace Tasks wholesale. */
class ExecutorMigrationExampleTest {

    private static final String TASK_ID = "migration-task";
    private static final String CONTEXT_ID = "migration-context";

    private InMemoryTaskStore store;
    private MainEventBus bus;
    private MainEventBusProcessor processor;

    @BeforeEach
    void startProcessor() {
        store = new InMemoryTaskStore();
        bus = new MainEventBus();
        processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP,
                new InMemoryQueueManager(store, bus));
        EventQueueUtil.start(processor);
    }

    @AfterEach
    void stopProcessor() {
        EventQueueUtil.stop(processor);
    }

    @Test
    void customExecutorCanCreateThenSubmitAMatchingBaseProposal() throws Exception {
        Message opening = Message.builder().messageId("opening").role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("hello"))).build();
        RequestContext context = new RequestContext.Builder()
                .setParams(MessageSendParams.builder().message(opening).build())
                .setTaskId(TASK_ID).setContextId(CONTEXT_ID).build();
        EventQueue main = mainQueue();
        EventQueue producer = main.tap();
        EventQueue observer = main.tap();
        ExecutionProposalContext proposalContext = proposalContext(context);
        AgentEmitter emitter = new AgentEmitter(context, producer, proposalContext);

        // Initial creation: submit the full output with a null base. The request-local opening
        // message is committed with this Task only if this proposal materializes it.
        Message working = Message.builder().messageId("working-status").taskId(TASK_ID).contextId(CONTEXT_ID)
                .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("working"))).build();
        Task initial = emitter.taskBuilder().status(new TaskStatus(TaskState.TASK_STATE_WORKING, working, null))
                .artifacts(List.of(Artifact.builder().artifactId("answer").parts(List.of(new TextPart("first"))).build()))
                .build();
        ProposalResult.Committed created = assertInstanceOf(ProposalResult.Committed.class,
                emitter.submitTask(new TaskProposal("migration-create", null, initial))
                        .toCompletableFuture().get(2, TimeUnit.SECONDS));
        assertEquals(1, created.events().stream().filter(TaskMessageUpdateEvent.class::isInstance).count());
        assertEquals(3, created.snapshot().task().generation());
        assertInstanceOf(TaskMessageUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());
        assertInstanceOf(TaskStatusUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());
        assertInstanceOf(TaskArtifactUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());

        // Matching-base update: always build from the Task/revision pair returned by refreshTask.
        TaskSnapshot refreshed = emitter.refreshTask();
        Task update = Task.builder(refreshed.task()).metadata(Map.of("executor", "custom"))
                .generation(900).build();
        ProposalResult.Committed changed = assertInstanceOf(ProposalResult.Committed.class,
                emitter.submitTask(new TaskProposal("migration-update", refreshed, update))
                        .toCompletableFuture().get(2, TimeUnit.SECONDS));
        assertTrue(changed.changed());
        assertEquals(Map.of("executor", "custom"), changed.snapshot().task().metadata());
        TaskStatusUpdateEvent syntheticStatus = assertInstanceOf(TaskStatusUpdateEvent.class,
                observer.dequeueEventItem(1_000).getEvent());
        assertEquals(4, syntheticStatus.generation());
        assertEquals("working-status", Objects.requireNonNull(syntheticStatus.status().message()).messageId());
    }

    @Test
    void customExecutorRefreshesAfterConflictOrUsesDeltaEmission() throws Exception {
        Task initial = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .artifacts(List.of(Artifact.builder().artifactId("existing-agent-output")
                        .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("old")))
                        .startGeneration(1).endGeneration(1).build()))
                .generation(1).build();
        store.save(initial, false);
        TaskSnapshot invocationBase = Objects.requireNonNull(store.readSnapshot(TASK_ID));
        RequestContext context = new RequestContext.Builder().setTaskId(TASK_ID).setContextId(CONTEXT_ID)
                .setTask(initial).setTaskSnapshot(invocationBase).build();
        EventQueue main = mainQueue();
        EventQueue producer = main.tap();
        EventQueue observer = main.tap();
        AgentEmitter emitter = new AgentEmitter(context, producer, proposalContext(context));

        // A previously captured whole-Task proposal never acquires a newer base. Prefer a delta
        // for incremental output, or refresh and rebuild the complete proposal explicitly.
        Task stale = Task.builder(invocationBase.task()).metadata(Map.of("stale", true)).build();
        emitter.updateStatus(TaskState.TASK_STATE_WORKING, null);
        TaskSnapshot afterDelta = awaitAdvance(invocationBase);
        assertInstanceOf(TaskStatusUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());
        ProposalResult.Rejected conflict = assertInstanceOf(ProposalResult.Rejected.class,
                emitter.submitTask(new TaskProposal("migration-stale", invocationBase, stale))
                        .toCompletableFuture().get(2, TimeUnit.SECONDS));
        assertEquals(ProposalResult.Rejected.Kind.CONFLICT, conflict.kind());
        assertEquals(afterDelta, store.readSnapshot(TASK_ID));

        TaskSnapshot fresh = emitter.refreshTask();
        Task rebuilt = Task.builder(fresh.task()).metadata(Map.of("refreshed", true)).build();
        assertInstanceOf(ProposalResult.Committed.class,
                emitter.submitTask(new TaskProposal("migration-rebuild", fresh, rebuilt))
                        .toCompletableFuture().get(2, TimeUnit.SECONDS));
        TaskStatusUpdateEvent metadataStatus = assertInstanceOf(TaskStatusUpdateEvent.class,
                observer.dequeueEventItem(1_000).getEvent());
        assertEquals(1, Objects.requireNonNull(store.readSnapshot(TASK_ID)).task().artifacts().size());
        TaskSnapshot afterMetadata = Objects.requireNonNull(store.readSnapshot(TASK_ID));

        // Delta path remains available for the next artifact update and does not require a
        // reconstructed Task snapshot.
        emitter.addArtifact(List.of(new TextPart("new output")), "new-agent-output", "new output", null);
        TaskSnapshot afterArtifact = awaitAdvance(afterMetadata);
        TaskArtifactUpdateEvent artifact = assertInstanceOf(TaskArtifactUpdateEvent.class,
                observer.dequeueEventItem(1_000).getEvent());
        assertEquals(afterDelta.task().generation() + 1, metadataStatus.generation());
        assertEquals(afterArtifact.task().generation(), artifact.generation());
        assertEquals(Message.Role.ROLE_AGENT,
                Objects.requireNonNull(store.readSnapshot(TASK_ID)).task().artifacts().get(1).role());
    }

    private ExecutionProposalContext proposalContext(RequestContext context) {
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), context, Duration.ofSeconds(2), Duration.ofSeconds(2));
        return new ExecutionProposalContext(context, service);
    }

    private EventQueue mainQueue() {
        return EventQueueUtil.getEventQueueBuilder(bus).taskId(TASK_ID).taskStateProvider(store)
                .mainEventBus(bus).build();
    }

    private TaskSnapshot awaitAdvance(TaskSnapshot base) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        TaskSnapshot current;
        do {
            current = Objects.requireNonNull(store.readSnapshot(TASK_ID));
            if (!current.revision().equals(base.revision())) {
                return current;
            }
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("executor delta did not commit before the deadline");
    }
}
