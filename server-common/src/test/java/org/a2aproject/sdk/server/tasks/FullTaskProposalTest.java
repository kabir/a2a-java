package org.a2aproject.sdk.server.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
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
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FullTaskProposalTest {

    private static final String TASK_ID = "full-proposal-task";
    private static final String CONTEXT_ID = "full-proposal-context";

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
    void matchingBaseNormalizesStatusArtifactsAndMetadataWithoutReplacingCanonicalHistory() throws Exception {
        TaskSnapshot base = existingSnapshot();
        Task proposed = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED,
                        agentMessage("proposed-status", "need input"), null))
                .history(List.of(agentMessage("forged-history", "ignored")))
                .timeline(List.of(new TimelineEntry(900, new TimelineEntry.MessageValue(
                        agentMessage("forged-timeline", "ignored")))))
                .artifacts(List.of(
                        base.task().artifacts().get(0),
                        Artifact.builder(base.task().artifacts().get(1)).parts(List.of(new TextPart("updated")))
                                .startGeneration(800).endGeneration(900).build(),
                        agentArtifact("new-agent", "new output", 700, 700)))
                .metadata(Map.of("replace", "new", "added", "agent"))
                .generation(999)
                .build();

        MutationPlan plan = TaskMutationPlanner.planTaskProposal(base, proposed);

        assertTrue(plan.changed());
        assertEquals(List.of(5L, 6L, 7L), plan.events().stream().map(FullTaskProposalTest::generation).toList());
        assertInstanceOf(TaskStatusUpdateEvent.class, plan.events().get(0));
        assertInstanceOf(TaskArtifactUpdateEvent.class, plan.events().get(1));
        assertInstanceOf(TaskArtifactUpdateEvent.class, plan.events().get(2));
        assertEquals(List.of(userMessage(), agentMessage("old-status", "working")), plan.task().history());
        assertEquals(base.task().timeline().size() + 1, plan.task().timeline().size());
        assertEquals(base.task().timeline(), plan.task().timeline().subList(0, base.task().timeline().size()));
        assertEquals(Map.of("preserved", "yes", "replace", "new", "added", "agent"), plan.task().metadata());
        assertEquals(4, plan.task().artifacts().size());
        assertEquals(Message.Role.ROLE_USER, plan.task().artifacts().get(0).role());
        assertEquals(List.of(new TextPart("updated")), plan.task().artifacts().get(1).parts());
        assertEquals(6, plan.task().artifacts().get(1).startGeneration());
        assertEquals(6, plan.task().artifacts().get(1).endGeneration());
        assertEquals("retained", plan.task().artifacts().get(2).artifactId());
        assertEquals(7, plan.task().artifacts().get(3).startGeneration());
        assertEquals(7, plan.task().artifacts().get(3).endGeneration());
        assertEquals(7, plan.task().generation());
    }

    @Test
    void terminalProposalCommitsFinalArtifactsBeforeTerminalStatusAndRejectsInvalidBatchAtomically() {
        TaskSnapshot base = existingSnapshot();
        Task terminal = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of(agentArtifact("final", "last bytes", 900, 901)))
                .generation(999).build();

        MutationPlan plan = TaskMutationPlanner.planTaskProposal(base, terminal);

        assertEquals(2, plan.events().size());
        assertInstanceOf(TaskArtifactUpdateEvent.class, plan.events().get(0));
        TaskStatusUpdateEvent status = assertInstanceOf(TaskStatusUpdateEvent.class, plan.events().get(1));
        assertEquals(TaskState.TASK_STATE_COMPLETED, status.status().state());
        assertEquals(List.of(5L, 6L), plan.events().stream().map(FullTaskProposalTest::generation).toList());

        Artifact forgedUserArtifact = Artifact.builder().artifactId("user-collision")
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("forged"))).build();
        Task invalid = Task.builder(terminal).status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of(agentArtifact("would-be-partial", "must not commit", 0, 0), forgedUserArtifact))
                .build();
        TaskSnapshot before = base;
        org.junit.jupiter.api.Assertions.assertThrows(InvalidParamsError.class, () ->
                new TaskMutationAuthority(store).mutate(TASK_ID,
                        current -> TaskMutationPlanner.planTaskProposal(current, invalid)));
        assertEquals(before, store.readSnapshot(TASK_ID));
    }

    @Test
    void metadataOnlyProposalAddsOneSyntheticStatusObservationWithoutDuplicatingItsMessage() throws Exception {
        TaskSnapshot stored = existingSnapshot();
        TaskSnapshot base = new TaskSnapshot(Task.builder(stored.task())
                .history(List.of(userMessage(), agentMessage("old-status", "working"))).build(), stored.revision());
        Task proposed = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(base.task().status()).metadata(Map.of("added", true)).generation(700).build();

        MutationPlan plan = TaskMutationPlanner.planTaskProposal(base, proposed);

        assertTrue(plan.changed());
        assertEquals(1, plan.events().size());
        TaskStatusUpdateEvent status = assertInstanceOf(TaskStatusUpdateEvent.class, plan.events().get(0));
        assertTrue(plan.events().stream().noneMatch(TaskMessageUpdateEvent.class::isInstance));
        assertEquals(5, status.generation());
        assertEquals("old-status", Objects.requireNonNull(status.status().message()).messageId());
        assertEquals(Map.of("added", true), status.metadata());
        assertEquals(base.task().history(), plan.task().history());
        assertEquals(base.task().status().message(), plan.task().status().message());
        assertEquals(1, plan.task().history().stream()
                .filter(message -> "old-status".equals(message.messageId())).count());
        assertEquals(base.task().timeline().size() + 1, plan.task().timeline().size());
        assertEquals(5, plan.task().timeline().get(2).generation());
        assertEquals(Map.of("preserved", "yes", "replace", "old", "added", true), plan.task().metadata());
    }

    @Test
    void initialProposalCommitsOpeningInputOnceAndDoesNotInventLegacyTimelineEntries() throws Exception {
        Message opening = Message.builder().messageId("opening-id").role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("hello"))).build();
        RequestContext context = new RequestContext.Builder()
                .setParams(MessageSendParams.builder().message(opening).build())
                .setTaskId(TASK_ID).setContextId(CONTEXT_ID).build();
        Message canonicalOpening = Objects.requireNonNull(context.getMessage());
        Message legacy = agentMessage("legacy-history", "earlier legacy output");
        Task proposed = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .history(List.of(legacy, canonicalOpening, canonicalOpening))
                .timeline(List.of(new TimelineEntry(400, new TimelineEntry.MessageValue(legacy))))
                .artifacts(List.of(agentArtifact("initial-agent", "answer", 80, 90)))
                .generation(900).build();
        EventQueue main = mainQueue();
        EventQueue producer = main.tap();
        EventQueue observer = main.tap();
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), context, Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertNull(store.readSnapshot(TASK_ID), "opening input must remain request-local before materialization");

        ProposalResult result = service.submitTask(new TaskProposal("initial-proposal", null, proposed), producer)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);

        ProposalResult.Committed committed = assertInstanceOf(ProposalResult.Committed.class, result);
        TaskSnapshot snapshot = committed.snapshot();
        assertEquals(List.of(legacy, canonicalOpening), snapshot.task().history());
        assertEquals(3, snapshot.task().generation());
        assertEquals(List.of(1L, 2L, 3L), committed.events().stream()
                .map(FullTaskProposalTest::generation).toList());
        assertInstanceOf(TaskMessageUpdateEvent.class, committed.events().get(0));
        assertInstanceOf(TaskStatusUpdateEvent.class, committed.events().get(1));
        assertInstanceOf(TaskArtifactUpdateEvent.class, committed.events().get(2));
        assertEquals(List.of(1L, 2L), snapshot.task().timeline().stream()
                .map(TimelineEntry::generation).toList());
        assertFalse(snapshot.task().timeline().stream().anyMatch(entry -> entry.entry()
                instanceof TimelineEntry.MessageValue value && "legacy-history".equals(value.message().messageId())));
        assertEquals(1, snapshot.task().history().stream()
                .filter(message -> "opening-id".equals(message.messageId())).count());
        assertEquals(3, snapshot.task().artifacts().get(0).startGeneration());
        assertEquals(snapshot, store.readSnapshot(TASK_ID));
        org.a2aproject.sdk.server.events.EventQueueItem messageItem = observer.dequeueEventItem(1_000);
        org.a2aproject.sdk.server.events.EventQueueItem statusItem = observer.dequeueEventItem(1_000);
        org.a2aproject.sdk.server.events.EventQueueItem artifactItem = observer.dequeueEventItem(1_000);
        assertInstanceOf(TaskMessageUpdateEvent.class, messageItem.getEvent());
        assertInstanceOf(TaskStatusUpdateEvent.class, statusItem.getEvent());
        assertInstanceOf(TaskArtifactUpdateEvent.class, artifactItem.getEvent());
        assertEquals(snapshot, messageItem.committedSnapshot());
        assertEquals(snapshot, statusItem.committedSnapshot());
        assertEquals(snapshot, artifactItem.committedSnapshot());
        assertNull(observer.dequeueEventItem(0));
    }

    @Test
    void openingMessageNestedValuesAreCapturedBeforeSubmissionAndStayImmutableAfterCommit() throws Exception {
        List<String> nested = new ArrayList<>(List.of("captured"));
        List<String> requestNested = new ArrayList<>(List.of("request-captured"));
        Message opening = Message.builder().messageId("mutable-opening").role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("hello"))).metadata(Map.of("nested", nested)).build();
        Map<String, Object> requestMetadata = Map.of("request-nested", requestNested);
        RequestContext context = new RequestContext.Builder()
                .setParams(MessageSendParams.builder().message(opening).metadata(requestMetadata).build())
                .setTaskId(TASK_ID).setContextId(CONTEXT_ID).build();
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), context, Duration.ofSeconds(2), Duration.ofSeconds(2));
        EventQueue main = mainQueue();
        EventQueue producer = main.tap();
        EventQueue observer = main.tap();
        Task proposed = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build();

        nested.add("before-submit");
        requestNested.add("before-submit");
        ProposalResult.Committed committed = assertInstanceOf(ProposalResult.Committed.class,
                service.submitTask(new TaskProposal("immutable-opening", null, proposed), producer)
                        .toCompletableFuture().get(2, TimeUnit.SECONDS));
        org.a2aproject.sdk.server.events.EventQueueItem item = observer.dequeueEventItem(1_000);
        nested.add("after-commit");
        requestNested.add("after-commit");

        Message stored = committed.snapshot().task().history().get(0);
        Message published = assertInstanceOf(TaskMessageUpdateEvent.class, item.getEvent()).message();
        assertEquals(Map.of("nested", List.of("captured")), stored.metadata());
        assertEquals(stored.metadata(), published.metadata());
        assertEquals(Map.of("request-nested", List.of("request-captured")),
                assertInstanceOf(TaskMessageUpdateEvent.class, item.getEvent()).metadata());
        assertEquals(committed.snapshot(), item.committedSnapshot());
        assertEquals(committed.snapshot(), store.readSnapshot(TASK_ID));
        assertInstanceOf(TaskStatusUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());
        assertNull(observer.dequeueEventItem(0));
    }

    @Test
    void competingInitialProposalsReturnOneCommitAndOneConflictWithoutLosingInput() throws Exception {
        Message firstOpening = Message.builder().messageId("first-opening").role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("first"))).build();
        Message secondOpening = Message.builder().messageId("second-opening").role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("second"))).build();
        RequestContext firstContext = openingContext(firstOpening);
        RequestContext secondContext = openingContext(secondOpening);
        ExecutorSubmissionService firstService = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), firstContext, Duration.ofSeconds(2), Duration.ofSeconds(2));
        ExecutorSubmissionService secondService = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), secondContext, Duration.ofSeconds(2), Duration.ofSeconds(2));
        EventQueue main = mainQueue();
        EventQueue firstProducer = main.tap();
        EventQueue secondProducer = main.tap();
        EventQueue observer = main.tap();
        Task proposed = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build();

        java.util.concurrent.CompletionStage<ProposalResult> first = firstService.submitTask(
                new TaskProposal("initial-winner", null, proposed), firstProducer);
        java.util.concurrent.CompletionStage<ProposalResult> second = secondService.submitTask(
                new TaskProposal("initial-loser", null, proposed), secondProducer);
        ProposalResult firstResult = first.toCompletableFuture().get(2, TimeUnit.SECONDS);
        ProposalResult secondResult = second.toCompletableFuture().get(2, TimeUnit.SECONDS);

        ProposalResult.Committed winner = assertInstanceOf(ProposalResult.Committed.class, firstResult);
        ProposalResult.Rejected loser = assertInstanceOf(ProposalResult.Rejected.class, secondResult);
        assertEquals(ProposalResult.Rejected.Kind.CONFLICT, loser.kind());
        assertEquals(winner.snapshot().revision(), loser.currentRevision());
        assertEquals(List.of(Objects.requireNonNull(firstContext.getMessage())), winner.snapshot().task().history());
        assertEquals(winner.snapshot(), store.readSnapshot(TASK_ID));
        assertInstanceOf(TaskMessageUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());
        assertInstanceOf(TaskStatusUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());
        assertNull(observer.dequeueEventItem(0), "the losing opening input must not be published");

        TaskSnapshot refreshed = secondService.refreshTask(TASK_ID);
        Task rebuilt = Task.builder(refreshed.task()).status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();
        assertInstanceOf(ProposalResult.Committed.class, secondService.submitTask(
                new TaskProposal("initial-loser-rebuilt", refreshed, rebuilt), secondProducer)
                .toCompletableFuture().get(2, TimeUnit.SECONDS));
        assertInstanceOf(TaskStatusUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());
        assertTrue(Objects.requireNonNull(store.readSnapshot(TASK_ID)).task().status().state().isFinal());
    }

    @Test
    void unchangedProposalDoesNotAllocateMutationRevisionOrPublishCanonicalEvents() throws Exception {
        TaskSnapshot base = existingSnapshot();
        RequestContext context = existingContext(base);
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), context, Duration.ofSeconds(2), Duration.ofSeconds(2));
        EventQueue main = mainQueue();
        EventQueue producer = main.tap();
        EventQueue observer = main.tap();
        Task proposed = Task.builder(base.task()).generation(999)
                .timeline(List.of(new TimelineEntry(900, new TimelineEntry.StatusValue(base.task().status())))).build();

        ProposalResult.Committed result = assertInstanceOf(ProposalResult.Committed.class,
                service.submitTask(new TaskProposal("unchanged-proposal", base, proposed), producer)
                        .toCompletableFuture().get(2, TimeUnit.SECONDS));

        assertFalse(result.changed());
        assertTrue(result.events().isEmpty());
        assertEquals(base, result.snapshot());
        assertEquals(base, store.readSnapshot(TASK_ID));
        assertNull(observer.dequeueEventItem(0), "unchanged proposals have no canonical publication batch");
    }

    @Test
    void staleProposalAfterExecutorDeltaRejectsLocallyThenRefreshAllowsRebuild() throws Exception {
        TaskSnapshot base = existingSnapshot();
        RequestContext context = existingContext(base);
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), context, Duration.ofSeconds(2), Duration.ofSeconds(2));
        EventQueue main = mainQueue();
        EventQueue producer = main.tap();
        EventQueue observer = main.tap();
        TaskStatusUpdateEvent delta = TaskStatusUpdateEvent.builder().taskId(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build();

        producer.enqueueEvent(delta, 1, TimeUnit.SECONDS);
        TaskSnapshot afterDelta = awaitAdvance(base);
        assertInstanceOf(TaskStatusUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());

        Task stale = Task.builder(base.task()).status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();
        ProposalResult staleResult = service.submitTask(new TaskProposal("stale-after-delta", base, stale), producer)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);

        ProposalResult.Rejected rejected = assertInstanceOf(ProposalResult.Rejected.class, staleResult);
        assertEquals(ProposalResult.Rejected.Kind.CONFLICT, rejected.kind());
        assertEquals(afterDelta.revision(), rejected.currentRevision());
        assertEquals(afterDelta, store.readSnapshot(TASK_ID));
        assertNull(observer.dequeueEventItem(0), "local proposal rejection must not be broadcast");

        TaskSnapshot refreshed = service.refreshTask(TASK_ID);
        Task rebuilt = Task.builder(refreshed.task()).status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();
        ProposalResult rebuiltResult = service.submitTask(
                new TaskProposal("refreshed-rebuild", refreshed, rebuilt), producer)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertInstanceOf(ProposalResult.Committed.class, rebuiltResult);
        assertInstanceOf(TaskStatusUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());
        assertTrue(Objects.requireNonNull(store.readSnapshot(TASK_ID)).task().status().state().isFinal());
    }

    @Test
    void staleExternalInputCannotBeRebasedAndObserverStillReceivesLaterDelta() throws Exception {
        TaskSnapshot base = existingSnapshot();
        RequestContext context = existingContext(base);
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), context, Duration.ofSeconds(2), Duration.ofSeconds(2));
        EventQueue main = mainQueue();
        EventQueue producer = main.tap();
        EventQueue observer = main.tap();

        Message externalInput = Message.builder().messageId("external-input").taskId(TASK_ID).contextId(CONTEXT_ID)
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("new client input"))).build();
        CommittedMutation external = new TaskMutationAuthority(store).mutate(TASK_ID,
                current -> TaskMutationPlanner.planInput(current,
                        new PendingTaskInput(externalInput, List.of(), null, null,
                                new PendingTaskInput.Admission(TASK_ID, CONTEXT_ID, null, true, false)),
                        MutationOrigin.USER));
        TaskSnapshot afterInput = external.snapshot();
        Task stale = Task.builder(base.task()).status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();

        ProposalResult result = service.submitTask(new TaskProposal("stale-after-input", base, stale), producer)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);

        ProposalResult.Rejected rejected = assertInstanceOf(ProposalResult.Rejected.class, result);
        assertEquals(ProposalResult.Rejected.Kind.CONFLICT, rejected.kind());
        assertEquals(afterInput, store.readSnapshot(TASK_ID));
        producer.enqueueEvent(TaskStatusUpdateEvent.builder().taskId(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build(), 1, TimeUnit.SECONDS);
        assertInstanceOf(TaskStatusUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());
    }

    private TaskSnapshot existingSnapshot() {
        Message user = userMessage();
        Message oldStatus = agentMessage("old-status", "working");
        Task task = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, oldStatus, null))
                .history(List.of(user))
                .timeline(List.of(
                        new TimelineEntry(1, new TimelineEntry.MessageValue(user)),
                        new TimelineEntry(4, new TimelineEntry.StatusValue(
                                new TaskStatus(TaskState.TASK_STATE_WORKING, oldStatus, null)))))
                .artifacts(List.of(
                        Artifact.builder().artifactId("user-artifact").role(Message.Role.ROLE_USER)
                                .parts(List.of(new TextPart("user data"))).startGeneration(2).endGeneration(3).build(),
                        agentArtifact("agent-artifact", "original", 2, 4),
                        agentArtifact("retained", "not supplied", 3, 4)))
                .metadata(Map.of("preserved", "yes", "replace", "old"))
                .generation(4).build();
        store.save(task, false);
        return Objects.requireNonNull(store.readSnapshot(TASK_ID));
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
        throw new AssertionError("event-bus delta did not commit before the deadline");
    }

    private EventQueue mainQueue() {
        return EventQueueUtil.getEventQueueBuilder(bus).taskId(TASK_ID).taskStateProvider(store)
                .mainEventBus(bus).build();
    }

    private static RequestContext existingContext(TaskSnapshot snapshot) {
        return new RequestContext.Builder().setTaskId(TASK_ID).setContextId(CONTEXT_ID)
                .setTask(snapshot.task()).setTaskSnapshot(snapshot).build();
    }

    private RequestContext openingContext(Message message) {
        return new RequestContext.Builder().setParams(MessageSendParams.builder().message(message).build())
                .setTaskId(TASK_ID).setContextId(CONTEXT_ID).build();
    }

    private static Message userMessage() {
        return Message.builder().messageId("user-input").taskId(TASK_ID).contextId(CONTEXT_ID)
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("accepted input"))).build();
    }

    private static Message agentMessage(String id, String text) {
        return Message.builder().messageId(id).taskId(TASK_ID).contextId(CONTEXT_ID)
                .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart(text))).build();
    }

    private static Artifact agentArtifact(String id, String text, long start, long end) {
        return Artifact.builder().artifactId(id).role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart(text))).startGeneration(start).endGeneration(end).build();
    }

    private static long generation(StreamingEventKind event) {
        if (event instanceof TaskStatusUpdateEvent status) {
            return status.generation();
        }
        if (event instanceof TaskArtifactUpdateEvent artifact) {
            return artifact.generation();
        }
        if (event instanceof TaskMessageUpdateEvent message) {
            return message.generation();
        }
        throw new AssertionError("unexpected committed event " + event.getClass().getSimpleName());
    }
}
