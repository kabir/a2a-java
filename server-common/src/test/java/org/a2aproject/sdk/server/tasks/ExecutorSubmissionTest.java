package org.a2aproject.sdk.server.tasks;

import static org.a2aproject.sdk.jsonrpc.common.json.JsonUtil.fromJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.events.EventQueue;
import org.a2aproject.sdk.server.events.EventQueueItem;
import org.a2aproject.sdk.server.events.EventQueueUtil;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.MainEventBusProcessorCallback;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutorSubmissionTest {
    private static final String TASK_JSON = """
            {"id":"proposal-task","contextId":"proposal-context",
             "status":{"state":"TASK_STATE_WORKING"}}
            """;

    private MainEventBusProcessor processor;
    private MainEventBus bus;
    private InMemoryTaskStore store;

    @BeforeEach
    void startProcessor() {
        store = new InMemoryTaskStore();
        bus = new MainEventBus();
        InMemoryQueueManager queueManager = new InMemoryQueueManager(store, bus);
        processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queueManager);
        EventQueueUtil.start(processor);
    }

    @AfterEach
    void stopProcessor() {
        EventQueueUtil.stop(processor);
    }

    @Test
    void admittedProposalCompletesOnlyAfterDelayedAuthorityAndDoesNotFanOut() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        CountDownLatch enteredAuthority = new CountDownLatch(1);
        CountDownLatch releaseAuthority = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        calls.incrementAndGet();
                        enteredAuthority.countDown();
                        await(releaseAuthority);
                        return new ProposalResult.Committed(proposal.correlationId(),
                                new TaskSnapshot(proposal.proposedTask(), new TaskRevision(2)), List.of(), false);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(2), Duration.ofSeconds(2));
        EventQueue main = EventQueueUtil.getEventQueueBuilder(bus)
                .taskId(task.id())
                .mainEventBus(bus)
                .build();
        EventQueue producer = main.tap();
        EventQueue unrelatedObserver = main.tap();
        TaskProposal proposal = new TaskProposal("proposal-1", base, task);

        var result = service.submitTask(proposal, producer);

        assertTrue(enteredAuthority.await(2, TimeUnit.SECONDS));
        assertFalse(result.toCompletableFuture().isDone(), "admission must not imply commitment");
        releaseAuthority.countDown();
        ProposalResult observed = result.toCompletableFuture().get(2, TimeUnit.SECONDS);

        assertEquals("proposal-1", observed.correlationId());
        assertTrue(observed instanceof ProposalResult.Committed);
        assertEquals(1, calls.get(), "one admitted proposal is processed once");
        assertNull(unrelatedObserver.dequeueEventItem(0), "executor-local result must not fan out");
    }

    @Test
    void rawLocalTaskAdmissionWithoutProposalProvenanceRejectsBeforeEnqueue() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        store.save(task, true);
        EventQueue main = EventQueueUtil.getEventQueueBuilder(bus)
                .taskId(task.id())
                .queueSize(1)
                .taskStateProvider(store)
                .mainEventBus(bus)
                .build();
        EventQueue producer = main.tap();
        EventQueue observer = main.tap();

        assertThrows(IllegalArgumentException.class, () -> main.enqueueEvent(task));
        TaskSnapshot fakeBase = new TaskSnapshot(task, new TaskRevision(1));
        assertThrows(IllegalArgumentException.class,
                () -> main.enqueueItem(new ForgedTaskItem(task,
                        new TaskProposal("forged-provenance", fakeBase, task), null)));
        assertThrows(IllegalArgumentException.class,
                () -> main.enqueueItem(new ForgedTaskItem(task, null, fakeBase)));
        assertEquals(0, main.size(), "a raw Task must be rejected before queue admission");

        TaskStatusUpdateEvent progress = TaskStatusUpdateEvent.builder()
                .taskId(task.id()).contextId(task.contextId())
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build();
        producer.enqueueEvent(progress, 1, TimeUnit.SECONDS);
        TaskStatusUpdateEvent delivered = assertInstanceOf(TaskStatusUpdateEvent.class,
                observer.dequeueEventItem(2_000).getEvent());
        assertEquals(progress.taskId(), delivered.taskId());
        assertEquals(progress.status(), delivered.status());
    }

    @Test
    void submissionRejectsQueueBoundToAnotherTaskBeforeQueueAdmission() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        AtomicInteger authorityCalls = new AtomicInteger();
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        authorityCalls.incrementAndGet();
                        return new ProposalResult.Committed(proposal.correlationId(), base, List.of(), false);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(1), Duration.ofSeconds(1));
        EventQueue wrongQueue = createMainQueue("another-task");

        ProposalResult result = service.submitTask(new TaskProposal("wrong-queue", base, task), wrongQueue)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertEquals(ProposalResult.Rejected.Kind.VALIDATION,
                assertInstanceOf(ProposalResult.Rejected.class, result).kind());
        assertEquals(0, wrongQueue.size());
        assertEquals(0, authorityCalls.get());
    }

    @Test
    void invocationIdentityAndOpeningTenantMustMatchProposalScope() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        ExecutorSubmissionService.ProposalAuthority authority = new ExecutorSubmissionService.ProposalAuthority() {
            @Override
            public ProposalResult process(TaskProposal proposal) {
                throw new AssertionError("invalid proposal scope must not reach authority");
            }

            @Override
            public TaskSnapshot refreshTask(String taskId) {
                return base;
            }
        };
        RequestContext invocation = requestContext(task, base);
        ExecutionProposalContext context = new ExecutionProposalContext(invocation,
                new ExecutorSubmissionService(authority, invocation, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        AgentEmitter emitter = new AgentEmitter(invocation, createMainQueue(task.id()).tap(), context);
        Task wrongContext = Task.builder(task).contextId("another-context").build();
        assertThrows(IllegalArgumentException.class,
                () -> emitter.submitTask(new TaskProposal("wrong-context", base, wrongContext)));

        org.a2aproject.sdk.spec.Message openingMessage = org.a2aproject.sdk.spec.Message.builder()
                .messageId("opening-message").role(org.a2aproject.sdk.spec.Message.Role.ROLE_USER)
                .parts(new org.a2aproject.sdk.spec.TextPart("open")).build();
        RequestContext mismatchedTenant = new RequestContext.Builder()
                .setParams(org.a2aproject.sdk.spec.MessageSendParams.builder().message(openingMessage)
                        .tenant("request-tenant").build())
                .setTaskId(task.id()).setContextId(task.contextId()).setTenant("invocation-tenant").build();
        ExecutorSubmissionService openingService = new ExecutorSubmissionService(authority, mismatchedTenant,
                Duration.ofSeconds(1), Duration.ofSeconds(1));

        ProposalResult result = openingService.submitTask(new TaskProposal("wrong-tenant-opening", null, task),
                createMainQueue(task.id()).tap()).toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertEquals(ProposalResult.Failed.CommitCertainty.KNOWN_NOT_COMMITTED,
                assertInstanceOf(ProposalResult.Failed.class, result).certainty());
    }

    @Test
    void genericQueueFailureAfterForwardingIsIndeterminateAndRetainsTheRealResult() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        EventQueue forwardingQueue = createMainQueue(task.id()).tap();
        EventQueue queueThatForwardsThenThrows = mock(EventQueue.class);
        when(queueThatForwardsThenThrows.isForTask(anyString())).thenReturn(true);
        doAnswer(invocation -> {
            forwardingQueue.enqueueItem(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2));
            throw new IllegalStateException("queue failed after forwarding");
        }).when(queueThatForwardsThenThrows).enqueueItem(any(), anyLong(), any());
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        return new ProposalResult.Committed(proposal.correlationId(),
                                new TaskSnapshot(proposal.proposedTask(), new TaskRevision(2)), List.of(), true);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(1), Duration.ofSeconds(1));

        var submitted = service.submitTask(
                new TaskProposal("forward-then-throw", base, task), queueThatForwardsThenThrows);
        submitted.toCompletableFuture().cancel(true);
        ProposalResult dispatchResult = submitted.toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertEquals(ProposalResult.Failed.CommitCertainty.INDETERMINATE,
                assertInstanceOf(ProposalResult.Failed.class, dispatchResult).certainty());
        ProposalResult eventual = service.completion("forward-then-throw").orElseThrow()
                .toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertInstanceOf(ProposalResult.Committed.class, eventual);
    }

    @Test
    void sameCorrelationCannotReplaceTheRegisteredProposalAndCapabilityProcessesOnce() throws Exception {
        Task firstTask = fromJson(TASK_JSON, Task.class);
        Task otherTask = Task.builder(firstTask).metadata(Map.of("different", true)).build();
        TaskSnapshot base = new TaskSnapshot(firstTask, new TaskRevision(1));
        EventQueue queue = mock(EventQueue.class);
        when(queue.isForTask(anyString())).thenReturn(true);
        AtomicReference<EventQueueItem> admitted = new AtomicReference<>();
        doAnswer(invocation -> {
            admitted.set(invocation.getArgument(0));
            return null;
        }).when(queue).enqueueItem(any(), anyLong(), any());
        AtomicInteger authorityCalls = new AtomicInteger();
        AtomicReference<TaskProposal> processed = new AtomicReference<>();
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        authorityCalls.incrementAndGet();
                        processed.set(proposal);
                        return new ProposalResult.Committed(proposal.correlationId(),
                                new TaskSnapshot(proposal.proposedTask(), new TaskRevision(2)), List.of(), true);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(1), Duration.ofSeconds(1));
        var first = service.submitTask(new TaskProposal("same-id", base, firstTask), queue);

        assertThrows(IllegalArgumentException.class,
                () -> service.submitTask(new TaskProposal("same-id", base, otherTask), queue));
        ExecutorSubmissionService.ProposalAdmission capability =
                assertInstanceOf(ExecutorSubmissionService.ProposalAdmission.class, admitted.get());
        capability.process();
        capability.process();

        assertInstanceOf(ProposalResult.Committed.class,
                first.toCompletableFuture().get(1, TimeUnit.SECONDS));
        assertEquals(firstTask, processed.get().proposedTask());
        assertEquals(1, authorityCalls.get());
    }

    @Test
    void productionProposalCannotCommitUntilProcessedByItsOrderedBus() throws Exception {
        Task baseTask = fromJson(TASK_JSON, Task.class);
        store.save(baseTask, false);
        TaskSnapshot base = store.readSnapshot(baseTask.id());
        Task completed = Task.builder(baseTask).status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();
        EventQueue bypassQueue = mock(EventQueue.class);
        when(bypassQueue.isForTask(anyString())).thenReturn(true);
        AtomicReference<EventQueueItem> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            EventQueueItem item = invocation.getArgument(0);
            captured.set(item);
            assertInstanceOf(ExecutorSubmissionService.ProposalAdmission.class, item).process();
            return null;
        }).when(bypassQueue).enqueueItem(any(), anyLong(), any());

        ExecutorSubmissionService service = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), Duration.ofSeconds(5), Duration.ofSeconds(5));
        CompletionStage<ProposalResult> completion = service.submitTask(
                new TaskProposal("ordered-only", base, completed), bypassQueue);
        assertEquals(base, store.readSnapshot(baseTask.id()),
                "calling the public admission method outside MainEventBus must not commit");
        assertFalse(completion.toCompletableFuture().isDone(),
                "a local process call is not an admitted proposal result");

        EventQueue main = EventQueueUtil.getEventQueueBuilder(bus)
                .taskId(baseTask.id()).taskStateProvider(store).mainEventBus(bus).build();
        EventQueue observer = main.tap();
        main.enqueueItem(captured.get());

        ProposalResult result = completion.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(assertInstanceOf(ProposalResult.Committed.class, result).changed());
        TaskStatusUpdateEvent published = assertInstanceOf(TaskStatusUpdateEvent.class,
                observer.dequeueEventItem(5_000).getEvent());
        assertEquals(TaskState.TASK_STATE_COMPLETED, published.status().state());
        // The legacy-saved generation-0 base is baselined to 1 once, so the committed mutation is generation 2.
        assertEquals(2, store.readSnapshot(baseTask.id()).task().generation());
        observer.close(true);
        main.close(true);
    }

    @Test
    void productionAuthorityReturnedByFactoryCannotCommitDirectly() throws Exception {
        Task baseTask = fromJson(TASK_JSON, Task.class);
        store.save(baseTask, false);
        TaskSnapshot base = store.readSnapshot(baseTask.id());
        Task completed = Task.builder(baseTask).status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();

        assertThrows(IllegalStateException.class, () -> ExecutorSubmissionService.forTaskStore(store)
                .process(new TaskProposal("direct-authority", base, completed)));

        assertEquals(base, store.readSnapshot(baseTask.id()),
                "the public factory authority must not mutate outside ordered admission");
    }

    @Test
    void failedDuplicateEnqueueCannotCancelTheAlreadyAcceptedProposal() throws Exception {
        Task baseTask = fromJson(TASK_JSON, Task.class);
        store.save(baseTask, false);
        TaskSnapshot base = store.readSnapshot(baseTask.id());
        Task completed = Task.builder(baseTask).status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();
        MainEventBus parkedBus = new MainEventBus();
        EventQueue main = EventQueueUtil.getEventQueueBuilder(parkedBus)
                .taskId(baseTask.id()).queueSize(1).taskStateProvider(store).mainEventBus(parkedBus).build();
        EventQueue origin = main.tap();
        EventQueue observer = main.tap();
        EventQueue duplicateForwarder = mock(EventQueue.class);
        when(duplicateForwarder.isForTask(anyString())).thenReturn(true);
        AtomicBoolean duplicateRejected = new AtomicBoolean();
        doAnswer(invocation -> {
            EventQueueItem item = invocation.getArgument(0);
            origin.enqueueItem(item, 1, TimeUnit.SECONDS);
            try {
                origin.enqueueItem(item, 0, TimeUnit.SECONDS);
            } catch (IllegalStateException expected) {
                duplicateRejected.set(true);
                throw expected;
            }
            return null;
        }).when(duplicateForwarder).enqueueItem(any(), anyLong(), any());

        ExecutorSubmissionService service = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), Duration.ofSeconds(5), Duration.ofSeconds(5));
        MainEventBusProcessor parkedProcessor = new MainEventBusProcessor(parkedBus, store,
                PushNotificationSender.NOOP, new InMemoryQueueManager(store, parkedBus));
        try {
            CompletionStage<ProposalResult> observed = service.submitTask(
                    new TaskProposal("duplicate-enqueue", base, completed), duplicateForwarder);
            assertTrue(duplicateRejected.get());
            assertEquals(1, parkedBus.size(), "only the first admission reaches the bus");
            ProposalResult dispatch = observed.toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(ProposalResult.Failed.CommitCertainty.INDETERMINATE,
                    assertInstanceOf(ProposalResult.Failed.class, dispatch).certainty());
            assertEquals(base, store.readSnapshot(baseTask.id()),
                    "the accepted proposal is still queued while its processor is parked");

            EventQueueUtil.start(parkedProcessor);
            ProposalResult result = service.completion("duplicate-enqueue").orElseThrow()
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(assertInstanceOf(ProposalResult.Committed.class, result).changed());
            TaskStatusUpdateEvent published = assertInstanceOf(TaskStatusUpdateEvent.class,
                    observer.dequeueEventItem(5_000).getEvent());
            assertEquals(TaskState.TASK_STATE_COMPLETED, published.status().state());
            // The legacy-saved generation-0 base is baselined to 1 once, so the committed mutation is generation 2.
            assertEquals(2, store.readSnapshot(baseTask.id()).task().generation());
            assertNull(observer.dequeueEventItem(0), "the original accepted proposal publishes exactly once");
        } finally {
            EventQueueUtil.stop(parkedProcessor);
            main.close(true);
        }
    }

    @Test
    void exposedFutureViewsCannotRewriteTheRetainedAuthorityResult() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        entered.countDown();
                        await(release);
                        return new ProposalResult.Committed(proposal.correlationId(),
                                new TaskSnapshot(proposal.proposedTask(), new TaskRevision(2)), List.of(), true);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(1), Duration.ofSeconds(2));

        var returnedStage = service.submitTask(new TaskProposal("protected-future", base, task),
                createMainQueue(task.id()).tap());
        CompletableFuture<ProposalResult> returnedView = returnedStage.toCompletableFuture();
        CompletableFuture<ProposalResult> lookupView = service.completion("protected-future").orElseThrow()
                .toCompletableFuture();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        returnedView.cancel(true);
        lookupView.complete(new ProposalResult.Rejected("protected-future",
                ProposalResult.Rejected.Kind.VALIDATION, "forged local completion", null));
        release.countDown();

        ProposalResult retained = service.completion("protected-future").orElseThrow()
                .toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertInstanceOf(ProposalResult.Committed.class, retained);
    }

    @Test
    void completionAndObservedCallbacksCanAwaitEachOtherWithoutBlockingDispatch() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        EventQueue queue = mock(EventQueue.class);
        when(queue.isForTask(anyString())).thenReturn(true);
        AtomicReference<EventQueueItem> admitted = new AtomicReference<>();
        doAnswer(invocation -> {
            admitted.set(invocation.getArgument(0));
            return null;
        }).when(queue).enqueueItem(any(), anyLong(), any());
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        return new ProposalResult.Committed(proposal.correlationId(),
                                new TaskSnapshot(proposal.proposedTask(), new TaskRevision(2)), List.of(), false);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(1), Duration.ofSeconds(1));
        var observed = service.submitTask(new TaskProposal("cross-channel", base, task), queue)
                .toCompletableFuture();
        var completion = service.completion("cross-channel").orElseThrow().toCompletableFuture();
        CountDownLatch completionCallbackEntered = new CountDownLatch(1);
        CountDownLatch observedCallbackEntered = new CountDownLatch(1);
        CompletableFuture<Void> completionCallback = completion.thenAccept(result -> {
            completionCallbackEntered.countDown();
            await(observedCallbackEntered);
        });
        CompletableFuture<Void> observedCallback = observed.thenAccept(result -> {
            observedCallbackEntered.countDown();
            await(completionCallbackEntered);
        });

        assertInstanceOf(ExecutorSubmissionService.ProposalAdmission.class, admitted.get()).process();

        completionCallback.get(3, TimeUnit.SECONDS);
        observedCallback.get(3, TimeUnit.SECONDS);
        assertInstanceOf(ProposalResult.Committed.class, completion.get(1, TimeUnit.SECONDS));
        assertInstanceOf(ProposalResult.Committed.class, observed.get(1, TimeUnit.SECONDS));
    }

    @Test
    void boundedLookupKeepsPendingAndCallbackDelayedEvidenceUntilControlledExpiry() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        EventQueue queue = mock(EventQueue.class);
        when(queue.isForTask(anyString())).thenReturn(true);
        AtomicReference<EventQueueItem> admitted = new AtomicReference<>();
        doAnswer(invocation -> {
            admitted.set(invocation.getArgument(0));
            return null;
        }).when(queue).enqueueItem(any(), anyLong(), any());
        AtomicLong clock = new AtomicLong(10_000);
        CountDownLatch sinkEntered = new CountDownLatch(1);
        CountDownLatch releaseSink = new CountDownLatch(1);
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        return new ProposalResult.Committed(proposal.correlationId(),
                                new TaskSnapshot(proposal.proposedTask(), new TaskRevision(2)), List.of(), true);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, null, Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ofNanos(10), clock::get);

        var first = service.submitTask(new TaskProposal("bounded", base, task), queue,
                (result, finalResult, finalResultSupplier) -> {
            sinkEntered.countDown();
            await(releaseSink);
        });
        ProposalResult capacityWhilePending = service.submitTask(
                new TaskProposal("while-pending", base, task), queue).toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertEquals(ProposalResult.Failed.CommitCertainty.KNOWN_NOT_COMMITTED,
                assertInstanceOf(ProposalResult.Failed.class, capacityWhilePending).certainty());
        assertFalse(service.completion("bounded").orElseThrow().toCompletableFuture().isDone());

        assertInstanceOf(ExecutorSubmissionService.ProposalAdmission.class, admitted.get()).process();
        assertTrue(sinkEntered.await(1, TimeUnit.SECONDS));
        assertInstanceOf(ProposalResult.Committed.class,
                service.completion("bounded").orElseThrow().toCompletableFuture().get(1, TimeUnit.SECONDS),
                "authority evidence is available before user sink dispatch completes");
        ProposalResult capacityWhileCallbackPending = service.submitTask(
                new TaskProposal("while-callback-pending", base, task), queue)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertInstanceOf(ProposalResult.Failed.class, capacityWhileCallbackPending);

        releaseSink.countDown();
        clock.addAndGet(11);
        var reusedAfterExpiry = service.submitTask(new TaskProposal("bounded", base, task), queue);
        assertInstanceOf(ExecutorSubmissionService.ProposalAdmission.class, admitted.get()).process();
        assertInstanceOf(ProposalResult.Committed.class,
                reusedAfterExpiry.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void completedLookupExpiresForNegativeAndZeroMonotonicClockOrigins() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        for (long origin : List.of(-1_000L, 0L)) {
            AtomicLong clock = new AtomicLong(origin);
            EventQueue queue = mock(EventQueue.class);
            when(queue.isForTask(anyString())).thenReturn(true);
            AtomicReference<EventQueueItem> admitted = new AtomicReference<>();
            doAnswer(invocation -> {
                admitted.set(invocation.getArgument(0));
                return null;
            }).when(queue).enqueueItem(any(), anyLong(), any());
            ExecutorSubmissionService service = new ExecutorSubmissionService(
                    new ExecutorSubmissionService.ProposalAuthority() {
                        @Override
                        public ProposalResult process(TaskProposal proposal) {
                            return new ProposalResult.Committed(proposal.correlationId(),
                                    new TaskSnapshot(proposal.proposedTask(), new TaskRevision(2)), List.of(), true);
                        }

                        @Override
                        public TaskSnapshot refreshTask(String taskId) {
                            return base;
                        }
                    }, null, Duration.ofSeconds(1), Duration.ofSeconds(1), 1,
                    Duration.ofNanos(10), clock::get);

            var first = service.submitTask(new TaskProposal("clock-origin", base, task), queue);
            assertInstanceOf(ExecutorSubmissionService.ProposalAdmission.class, admitted.get()).process();
            assertInstanceOf(ProposalResult.Committed.class, first.toCompletableFuture().get(1, TimeUnit.SECONDS));
            clock.addAndGet(11);

            assertTrue(service.completion("clock-origin").isEmpty(),
                    "a completed result expires based on elapsed monotonic time, including at origin " + origin);
            var reused = service.submitTask(new TaskProposal("clock-origin", base, task), queue);
            assertInstanceOf(ExecutorSubmissionService.ProposalAdmission.class, admitted.get()).process();
            assertInstanceOf(ProposalResult.Committed.class, reused.toCompletableFuture().get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void lookupDuringCompletionDoesNotEvictTheNewlyPublishedResult() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        EventQueue queue = mock(EventQueue.class);
        when(queue.isForTask(anyString())).thenReturn(true);
        AtomicReference<EventQueueItem> admitted = new AtomicReference<>();
        doAnswer(invocation -> {
            admitted.set(invocation.getArgument(0));
            return null;
        }).when(queue).enqueueItem(any(), anyLong(), any());
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        CountDownLatch pruneStarted = new CountDownLatch(1);
        CountDownLatch releasePrune = new CountDownLatch(1);
        AtomicBoolean pausePrune = new AtomicBoolean();
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        return new ProposalResult.Committed(proposal.correlationId(),
                                new TaskSnapshot(proposal.proposedTask(), new TaskRevision(2)), List.of(), true);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, null, Duration.ofSeconds(1), Duration.ofSeconds(1), 1,
                Duration.ofNanos(10), clock::get, () -> {
                    if (pausePrune.compareAndSet(true, false)) {
                        pruneStarted.countDown();
                        await(releasePrune);
                    }
                });

        service.submitTask(new TaskProposal("prune-race", base, task), queue);
        var admission = assertInstanceOf(ExecutorSubmissionService.ProposalAdmission.class, admitted.get());
        pausePrune.set(true);
        FutureTask<java.util.Optional<CompletionStage<ProposalResult>>> lookup = new FutureTask<>(
                () -> service.completion("prune-race"));
        Thread lookupThread = new Thread(lookup, "proposal-completion-prune-test");
        lookupThread.setDaemon(true);
        lookupThread.start();
        try {
            assertTrue(pruneStarted.await(1, TimeUnit.SECONDS));
            admission.process();
        } finally {
            releasePrune.countDown();
        }

        java.util.Optional<CompletionStage<ProposalResult>> retained = lookup.get(1, TimeUnit.SECONDS);
        assertTrue(retained.isPresent(), "completion evidence published during pruning must retain its full window");
        assertInstanceOf(ProposalResult.Committed.class,
                retained.orElseThrow().toCompletableFuture().get(1, TimeUnit.SECONDS));
        assertEquals(1_000_000_000L, clock.get(), "the completion race must not rely on clock advancement");
    }

    @Test
    void proposalAndSnapshotCopyNestedMetadataByValue() throws Exception {
        Map<String, Object> nested = new HashMap<>();
        List<Object> values = new ArrayList<>(List.of("before"));
        nested.put("values", values);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("nested", nested);
        Task original = Task.builder(fromJson(TASK_JSON, Task.class)).metadata(metadata).build();

        TaskSnapshot snapshot = new TaskSnapshot(original, new TaskRevision(1));
        TaskProposal proposal = new TaskProposal("immutable-values", snapshot, original);
        values.set(0, "after");
        nested.put("other", true);
        metadata.put("later", true);

        Map<?, ?> copiedMetadata = (Map<?, ?>) proposal.proposedTask().metadata().get("nested");
        assertEquals(List.of("before"), copiedMetadata.get("values"));
        assertFalse(copiedMetadata.containsKey("other"));
        assertFalse(proposal.proposedTask().metadata().containsKey("later"));
        assertEquals(List.of("before"), ((Map<?, ?>) snapshot.task().metadata().get("nested")).get("values"));
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) copiedMetadata.get("values")).add("mutate"));
    }

    @Test
    void snapshotAndProposalCopyTopLevelAndTimelineStatusMessagesByValue() throws Exception {
        Task baseTask = fromJson(TASK_JSON, Task.class);
        List<Object> topLevelValues = new ArrayList<>(List.of("status-original"));
        Message topLevelMessage = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new org.a2aproject.sdk.spec.TextPart("top-level status")))
                .metadata(Map.of("nested", Map.of("values", topLevelValues)))
                .build();
        Task withTopLevelStatus = Task.builder(baseTask)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, topLevelMessage, null))
                .build();
        TaskSnapshot snapshot = new TaskSnapshot(withTopLevelStatus, new TaskRevision(1));

        List<Object> timelineValues = new ArrayList<>(List.of("timeline-original"));
        Message timelineMessage = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new org.a2aproject.sdk.spec.TextPart("timeline status")))
                .metadata(Map.of("nested", Map.of("values", timelineValues)))
                .build();
        Task proposed = Task.builder(baseTask)
                .timeline(List.of(new TimelineEntry(2,
                        new TimelineEntry.StatusValue(new TaskStatus(TaskState.TASK_STATE_WORKING,
                                timelineMessage, null)))))
                .build();
        TaskProposal proposal = new TaskProposal("status-message-copy", snapshot, proposed);

        topLevelValues.set(0, "mutated-after-snapshot");
        timelineValues.set(0, "mutated-after-proposal");

        TaskStatus copiedTopLevelStatus = snapshot.task().status();
        assertEquals(List.of("status-original"),
                statusMessageValues(java.util.Objects.requireNonNull(copiedTopLevelStatus.message())));
        TimelineEntry.StatusValue timelineEntry = assertInstanceOf(TimelineEntry.StatusValue.class,
                proposal.proposedTask().timeline().get(0).entry());
        assertEquals(List.of("timeline-original"),
                statusMessageValues(java.util.Objects.requireNonNull(timelineEntry.status().message())));
    }

    @Test
    void rejectedProposalIsOriginLocalAndLeavesTaskAndObserversUsable() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        store.save(task, true);
        TaskSnapshot base = store.readSnapshot(task.id());
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        return new ProposalResult.Rejected(proposal.correlationId(),
                                ProposalResult.Rejected.Kind.CONFLICT, "stale proposal", base.revision());
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(2), Duration.ofSeconds(2));
        AtomicInteger replicationCalls = new AtomicInteger();
        EventQueue main = EventQueueUtil.getEventQueueBuilder(bus)
                .taskId(task.id())
                .mainEventBus(bus)
                .hook(item -> replicationCalls.incrementAndGet())
                .build();
        EventQueue producer = main.tap();
        EventQueue observer = main.tap();

        ProposalResult result = service.submitTask(new TaskProposal("reject-1", base, task), producer)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);

        assertInstanceOf(ProposalResult.Rejected.class, result);
        assertEquals(TaskState.TASK_STATE_WORKING, store.get(task.id()).status().state());
        assertNull(producer.dequeueEventItem(0), "rejection belongs to the result channel");
        assertNull(observer.dequeueEventItem(0), "rejection must not be broadcast");
        assertEquals(0, replicationCalls.get(), "a rejected proposal must not replicate");

        TaskStatusUpdateEvent progress = TaskStatusUpdateEvent.builder()
                .taskId(task.id())
                .contextId(task.contextId())
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .build();
        producer.enqueueEvent(progress);
        assertInstanceOf(TaskStatusUpdateEvent.class,
                observer.dequeueEventItem(2_000).getEvent(), "the observer remains usable after rejection");
        assertEquals(2, store.get(task.id()).generation(), "legacy-saved base baselined to 1, then one committed event");
        assertEquals(1, replicationCalls.get(), "a later committed event still reaches replication");
    }

    @Test
    void failuresDistinguishKnownNonCommitFromIndeterminateOutcome() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        EventQueue producer = createMainQueue(task.id()).tap();

        ExecutorSubmissionService knownService = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        throw new TaskMutationNotCommittedException(task.id(), "write rejected");
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(2), Duration.ofSeconds(2));
        ProposalResult known = knownService.submitTask(new TaskProposal("known", base, task), producer)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertEquals(ProposalResult.Failed.CommitCertainty.KNOWN_NOT_COMMITTED,
                assertInstanceOf(ProposalResult.Failed.class, known).certainty());

        ExecutorSubmissionService unknownService = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        throw new TaskMutationOutcomeUnknownException(task.id(), "storage outcome unknown");
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(2), Duration.ofSeconds(2));
        ProposalResult unknown = unknownService.submitTask(new TaskProposal("unknown", base, task), producer)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertEquals(ProposalResult.Failed.CommitCertainty.INDETERMINATE,
                assertInstanceOf(ProposalResult.Failed.class, unknown).certainty());
    }

    @Test
    void observationTimeoutRetainsCorrelationAndLateCompletion() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        CountDownLatch enteredAuthority = new CountDownLatch(1);
        CountDownLatch releaseAuthority = new CountDownLatch(1);
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        enteredAuthority.countDown();
                        await(releaseAuthority);
                        return new ProposalResult.Committed(proposal.correlationId(),
                                new TaskSnapshot(task, new TaskRevision(2)), List.of(), false);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(2), Duration.ofMillis(25));

        RequestContext requestContext = requestContext(task, base);
        AgentEmitter emitter = new AgentEmitter(requestContext, createMainQueue(task.id()).tap(),
                new ExecutionProposalContext(requestContext, service));
        CompletableFuture<ProposalResult> observed = emitter.submitTask(
                new TaskProposal("late-result", base, task)).toCompletableFuture();
        assertTrue(enteredAuthority.await(2, TimeUnit.SECONDS));
        ProposalResult timeout = observed.get(2, TimeUnit.SECONDS);
        assertEquals(ProposalResult.Failed.CommitCertainty.INDETERMINATE,
                assertInstanceOf(ProposalResult.Failed.class, timeout).certainty());
        CompletableFuture<ProposalResult> eventual = emitter.proposalCompletion("late-result")
                .orElseThrow().toCompletableFuture();
        assertFalse(eventual.isDone(), "observation timeout must not discard or complete the underlying result");

        releaseAuthority.countDown();
        ProposalResult committed = eventual.get(2, TimeUnit.SECONDS);
        assertInstanceOf(ProposalResult.Committed.class, committed);
        assertEquals("late-result", committed.correlationId());
    }

    @Test
    void blockedClientContinuationsCannotStarveOtherObservationDeadlines() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        EventQueue queue = mock(EventQueue.class);
        when(queue.isForTask(anyString())).thenReturn(true);
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        throw new AssertionError("the test queue retains admitted items");
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(1), Duration.ofMillis(250));
        CountDownLatch fourCallbacksEntered = new CountDownLatch(4);
        CountDownLatch releaseCallbacks = new CountDownLatch(1);
        try {
            for (int i = 0; i < 4; i++) {
                service.submitTask(new TaskProposal("blocked-continuation-" + i, base, task), queue)
                        .thenRun(() -> {
                            fourCallbacksEntered.countDown();
                            await(releaseCallbacks);
                        });
            }
            assertTrue(fourCallbacksEntered.await(2, TimeUnit.SECONDS));

            ProposalResult fifth = service.submitTask(
                    new TaskProposal("deadline-behind-blocked-continuations", base, task), queue)
                    .toCompletableFuture().get(750, TimeUnit.MILLISECONDS);
            assertEquals(ProposalResult.Failed.CommitCertainty.INDETERMINATE,
                    assertInstanceOf(ProposalResult.Failed.class, fifth).certainty());
        } finally {
            releaseCallbacks.countDown();
        }
    }

    @Test
    void authoritativeTimeoutFailureReachesLegacyVoidSink() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        java.util.concurrent.TimeoutException authorityTimeout =
                new java.util.concurrent.TimeoutException("authority storage outcome is uncertain");
        RequestContext context = requestContext(task, base);
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        return new ProposalResult.Failed(proposal.correlationId(), "authority timeout",
                                authorityTimeout, ProposalResult.Failed.CommitCertainty.INDETERMINATE);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, context, Duration.ofSeconds(1), Duration.ofSeconds(1));
        ExecutionProposalContext execution = new ExecutionProposalContext(context, service);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<ProposalResult> outcome = new AtomicReference<>();
        AgentEmitter emitter = new AgentEmitter(context, createMainQueue(task.id()).tap(), execution);
        emitter.addProposalResultListener(result -> {
            outcome.set(result);
            delivered.countDown();
        });

        emitter.addTask(task);

        assertTrue(delivered.await(1, TimeUnit.SECONDS));
        ProposalResult.Failed failed = assertInstanceOf(ProposalResult.Failed.class, outcome.get());
        assertEquals(authorityTimeout, failed.cause());
        assertEquals(failed, execution.lastResult());
    }

    @Test
    void delayedTimeoutListenerCannotOverwriteLateAuthorityOutcome() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        CountDownLatch enteredAuthority = new CountDownLatch(1);
        CountDownLatch releaseAuthority = new CountDownLatch(1);
        CountDownLatch timeoutListenerEntered = new CountDownLatch(1);
        CountDownLatch releaseTimeoutListener = new CountDownLatch(1);
        CountDownLatch committedListenerDelivered = new CountDownLatch(1);
        CountDownLatch staleTimeoutDelivered = new CountDownLatch(1);
        List<ProposalResult> listenerResults = new CopyOnWriteArrayList<>();
        RequestContext requestContext = requestContext(task, base);
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        enteredAuthority.countDown();
                        await(releaseAuthority);
                        return new ProposalResult.Committed(proposal.correlationId(),
                                new TaskSnapshot(task, new TaskRevision(2)), List.of(), true);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, requestContext, Duration.ofSeconds(1), Duration.ofMillis(30));
        ExecutionProposalContext context = new ExecutionProposalContext(requestContext, service);
        context.addResultListener(result -> {
            listenerResults.add(result);
            if (result instanceof ProposalResult.Failed failed
                    && failed.cause() instanceof java.util.concurrent.TimeoutException) {
                timeoutListenerEntered.countDown();
                await(releaseTimeoutListener);
            } else if (result instanceof ProposalResult.Committed) {
                committedListenerDelivered.countDown();
            }
        });
        context.addResultListener(result -> {
            if (result instanceof ProposalResult.Failed failed
                    && failed.cause() instanceof java.util.concurrent.TimeoutException) {
                staleTimeoutDelivered.countDown();
            }
        });
        AgentEmitter emitter = new AgentEmitter(requestContext, createMainQueue(task.id()).tap(), context);
        var observed = emitter.submitTask(new TaskProposal("timeout-order", base, task));
        assertTrue(enteredAuthority.await(1, TimeUnit.SECONDS));
        assertTrue(timeoutListenerEntered.await(1, TimeUnit.SECONDS));

        releaseAuthority.countDown();
        assertInstanceOf(ProposalResult.Committed.class, emitter.proposalCompletion("timeout-order")
                .orElseThrow().toCompletableFuture().get(1, TimeUnit.SECONDS));
        assertFalse(committedListenerDelivered.await(100, TimeUnit.MILLISECONDS),
                "the same submission must not invoke a final listener concurrently with its timeout listener");
        releaseTimeoutListener.countDown();
        assertTrue(committedListenerDelivered.await(1, TimeUnit.SECONDS));
        assertFalse(staleTimeoutDelivered.await(100, TimeUnit.MILLISECONDS),
                "a later listener must not receive an observation timeout after authority committed");
        assertTrue(listenerResults.size() >= 2);
        assertInstanceOf(ProposalResult.Failed.class, listenerResults.get(0));
        assertInstanceOf(ProposalResult.Committed.class, listenerResults.get(listenerResults.size() - 1));
        assertInstanceOf(ProposalResult.Committed.class, context.lastResult());
        assertInstanceOf(ProposalResult.Failed.class, observed.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void retainedVoidEmitterKeepsInvocationBaseAndExplicitRefreshCanRebuild() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot invocationBase = new TaskSnapshot(task, new TaskRevision(1));
        AtomicReference<TaskSnapshot> current = new AtomicReference<>(invocationBase);
        AtomicInteger calls = new AtomicInteger();
        List<TaskRevision> observedBases = new CopyOnWriteArrayList<>();
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        observedBases.add(proposal.baseSnapshot().revision());
                        if (calls.getAndIncrement() == 0) {
                            TaskSnapshot committed = new TaskSnapshot(proposal.proposedTask(), new TaskRevision(2));
                            current.set(committed);
                            return new ProposalResult.Committed(proposal.correlationId(), committed, List.of(), true);
                        }
                        if (!proposal.baseSnapshot().revision().equals(current.get().revision())) {
                            return new ProposalResult.Rejected(proposal.correlationId(),
                                    ProposalResult.Rejected.Kind.CONFLICT, "invocation base is stale",
                                    current.get().revision());
                        }
                        return new ProposalResult.Committed(proposal.correlationId(), current.get(), List.of(), false);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return current.get();
                    }
                }, requestContext(task, invocationBase), Duration.ofSeconds(2), Duration.ofSeconds(2));
        RequestContext requestContext = requestContext(task, invocationBase);
        ExecutionProposalContext execution = new ExecutionProposalContext(requestContext, service);
        EventQueue producer = createMainQueue(task.id()).tap();
        AgentEmitter emitter = new AgentEmitter(requestContext, producer, execution);
        CountDownLatch voidResults = new CountDownLatch(2);
        List<ProposalResult> voidOutcomes = new CopyOnWriteArrayList<>();
        List<String> callbackThreads = new CopyOnWriteArrayList<>();
        emitter.addProposalResultListener(result -> {
            voidOutcomes.add(result);
            callbackThreads.add(Thread.currentThread().getName());
            voidResults.countDown();
        });

        emitter.addTask(task);
        emitter.emitEvent(task);
        assertTrue(voidResults.await(2, TimeUnit.SECONDS));
        assertEquals(2, voidOutcomes.size());
        assertTrue(voidOutcomes.stream().anyMatch(ProposalResult.Committed.class::isInstance));
        assertTrue(voidOutcomes.stream().anyMatch(ProposalResult.Rejected.class::isInstance));
        assertEquals(List.of(new TaskRevision(1), new TaskRevision(1)), observedBases,
                "acknowledged legacy emissions must not advance retained proposal provenance");
        assertTrue(callbackThreads.stream().noneMatch("MainEventBusProcessor"::equals));

        TaskSnapshot refreshed = emitter.refreshTask();
        ProposalResult explicit = emitter.submitTask(new TaskProposal("rebuilt", refreshed, task))
                .toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertInstanceOf(ProposalResult.Committed.class, explicit);
        assertEquals(new TaskRevision(2), refreshed.revision());
    }

    @Test
    void rejectionCallbackCanRefreshRebuildAndSubmitWithoutBlockingProcessor() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot oldBase = new TaskSnapshot(task, new TaskRevision(1));
        TaskSnapshot current = new TaskSnapshot(task, new TaskRevision(2));
        CountDownLatch enteredFirst = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        if (calls.getAndIncrement() == 0) {
                            enteredFirst.countDown();
                            await(releaseFirst);
                            return new ProposalResult.Rejected(proposal.correlationId(),
                                    ProposalResult.Rejected.Kind.CONFLICT, "stale", current.revision());
                        }
                        return new ProposalResult.Committed(proposal.correlationId(), current, List.of(), false);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return current;
                    }
                }, Duration.ofSeconds(2), Duration.ofSeconds(2));
        EventQueue producer = createMainQueue(task.id()).tap();
        CompletableFuture<ProposalResult> first = service.submitTask(
                new TaskProposal("callback-first", oldBase, task), producer).toCompletableFuture();
        assertTrue(enteredFirst.await(2, TimeUnit.SECONDS));
        CompletableFuture<ProposalResult> retry = first.thenCompose(result -> {
            assertInstanceOf(ProposalResult.Rejected.class, result);
            TaskSnapshot refreshed = service.refreshTask(task.id());
            return service.submitTask(new TaskProposal("callback-rebuilt", refreshed, task), producer)
                    .toCompletableFuture();
        });

        releaseFirst.countDown();
        assertInstanceOf(ProposalResult.Committed.class, retry.get(2, TimeUnit.SECONDS));
        assertEquals(2, calls.get());
    }

    @Test
    void missingOpeningContextMakesNullBaseAKnownNonCommit() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        AtomicInteger calls = new AtomicInteger();
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        calls.incrementAndGet();
                        return new ProposalResult.Committed(proposal.correlationId(),
                                new TaskSnapshot(task, new TaskRevision(1)), List.of(), false);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return new TaskSnapshot(task, new TaskRevision(1));
                    }
                }, Duration.ofSeconds(1), Duration.ofSeconds(1));

        ProposalResult result = service.submitTask(new TaskProposal("no-opening", null, task),
                createMainQueue(task.id()).tap()).toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertEquals(ProposalResult.Failed.CommitCertainty.KNOWN_NOT_COMMITTED,
                assertInstanceOf(ProposalResult.Failed.class, result).certainty());
        assertEquals(0, calls.get(), "a null base without retained opening input must never be admitted");
    }

    @Test
    void productionAuthorityRejectsBaseStaledByExternalMutation() throws Exception {
        Task initial = fromJson(TASK_JSON, Task.class);
        store.save(initial, true);
        TaskSnapshot invocationBase = java.util.Objects.requireNonNull(store.readSnapshot(initial.id()));
        Task externalMutation = Task.builder(initial).metadata(java.util.Map.of("external", true)).build();
        store.save(externalMutation, true);
        TaskSnapshot externallyCommitted = java.util.Objects.requireNonNull(store.readSnapshot(initial.id()));
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), requestContext(initial, invocationBase),
                Duration.ofSeconds(2), Duration.ofSeconds(2));
        EventQueue producer = createMainQueue(initial.id()).tap();
        Task proposed = Task.builder(initial).metadata(java.util.Map.of("agent", true)).build();

        ProposalResult result = service.submitTask(
                new TaskProposal("external-stale", invocationBase, proposed), producer)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);

        ProposalResult.Rejected rejected = assertInstanceOf(ProposalResult.Rejected.class, result);
        assertEquals(ProposalResult.Rejected.Kind.CONFLICT, rejected.kind());
        assertEquals(externallyCommitted.revision(), rejected.currentRevision());
        // Reading the snapshot baselined the legacy-saved external mutation (generation 0 -> 1) before the rejection.
        assertEquals(externallyCommitted.task(), store.get(initial.id()), "rejection preserves the external mutation");
    }

    @Test
    void productionAuthorityRejectsDifferentTaskValueWithReusedRevision() throws Exception {
        Task initial = fromJson(TASK_JSON, Task.class);
        store.save(initial, true);
        TaskSnapshot current = java.util.Objects.requireNonNull(store.readSnapshot(initial.id()));
        TaskSnapshot forgedBase = new TaskSnapshot(
                Task.builder(initial).metadata(Map.of("forged", true)).build(), current.revision());
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), Duration.ofSeconds(1), Duration.ofSeconds(1));

        ProposalResult result = service.submitTask(new TaskProposal("wrong-value-same-revision", forgedBase, initial),
                createMainQueue(initial.id()).tap()).toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertEquals(ProposalResult.Rejected.Kind.CONFLICT,
                assertInstanceOf(ProposalResult.Rejected.class, result).kind());
        assertEquals(current, store.readSnapshot(initial.id()));
    }

    @Test
    void unprovenTypedAdmissionFailureIsIndeterminate() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        TaskSnapshot base = new TaskSnapshot(task, new TaskRevision(1));
        EventQueue queue = mock(EventQueue.class);
        when(queue.isForTask(anyString())).thenReturn(true);
        AtomicLong suppliedTimeout = new AtomicLong();
        AtomicInteger authorityCalls = new AtomicInteger();
        doAnswer(invocation -> {
            suppliedTimeout.set(invocation.getArgument(1));
            throw new EventQueue.EnqueueTimeoutException();
        }).when(queue).enqueueItem(any(), anyLong(), any());
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        authorityCalls.incrementAndGet();
                        return new ProposalResult.Committed(proposal.correlationId(), base, List.of(), false);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofMillis(37), Duration.ofMillis(200));

        ProposalResult result = service.submitTask(new TaskProposal("admission-timeout", base, task), queue)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);

        ProposalResult.Failed failed = assertInstanceOf(ProposalResult.Failed.class, result);
        assertEquals(ProposalResult.Failed.CommitCertainty.INDETERMINATE, failed.certainty());
        assertEquals(37, suppliedTimeout.get(), "the configured finite admission deadline reaches the queue");
        assertEquals(0, authorityCalls.get(), "a rejected admission never reaches the authority");
    }

    @Test
    void typedTimeoutAndInterruptionAfterForwardingRetainTheEventualAuthorityResult() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        store.save(task, true);
        TaskSnapshot base = java.util.Objects.requireNonNull(store.readSnapshot(task.id()));
        CountDownLatch processorBlocked = new CountDownLatch(1);
        CountDownLatch releaseProcessor = new CountDownLatch(1);
        processor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void onEventProcessed(String taskId, Event event) {
                if (event instanceof TaskStatusUpdateEvent) {
                    processorBlocked.countDown();
                    await(releaseProcessor);
                }
            }

            @Override
            public void onTaskFinalized(String taskId) {
            }
        });
        EventQueue producer = createMainQueue(task.id()).tap();
        producer.enqueueEvent(TaskStatusUpdateEvent.builder()
                .taskId(task.id()).contextId(task.contextId())
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build());
        assertTrue(processorBlocked.await(1, TimeUnit.SECONDS));

        AtomicInteger authorityWrites = new AtomicInteger();
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        authorityWrites.incrementAndGet();
                        return new ProposalResult.Committed(proposal.correlationId(),
                                new TaskSnapshot(proposal.proposedTask(), new TaskRevision(2)), List.of(), true);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofSeconds(1), Duration.ofSeconds(2));
        RequestContext requestContext = requestContext(task, base);
        ExecutionProposalContext execution = new ExecutionProposalContext(requestContext, service);
        CopyOnWriteArrayList<ProposalResult> outcomes = new CopyOnWriteArrayList<>();
        CountDownLatch twoCommits = new CountDownLatch(2);
        CountDownLatch twoNotices = new CountDownLatch(2);
        execution.addResultListener(result -> {
            outcomes.add(result);
            if (result instanceof ProposalResult.Committed) {
                twoCommits.countDown();
            } else {
                twoNotices.countDown();
            }
        });

        RuntimeException[] forwardingFailures = {
                new EventQueue.EnqueueTimeoutException(),
                new EventQueue.EnqueueInterruptedException(new InterruptedException("forwarded then interrupted"))
        };
        try {
            for (int i = 0; i < forwardingFailures.length; i++) {
                RuntimeException failure = forwardingFailures[i];
                EventQueue adapter = mock(EventQueue.class);
                when(adapter.isForTask(anyString())).thenReturn(true);
                doAnswer(invocation -> {
                    producer.enqueueItem(invocation.getArgument(0), invocation.getArgument(1),
                            invocation.getArgument(2));
                    throw failure;
                }).when(adapter).enqueueItem(any(), anyLong(), any());

                String correlationId = "forwarded-typed-" + i;
                CompletionStage<ProposalResult> observed = execution.submitTask(
                        new TaskProposal(correlationId, base, task), adapter);
                ProposalResult.Failed dispatchNotice = assertInstanceOf(ProposalResult.Failed.class,
                        observed.toCompletableFuture().get(1, TimeUnit.SECONDS));
                assertEquals(ProposalResult.Failed.CommitCertainty.INDETERMINATE, dispatchNotice.certainty());
                assertFalse(execution.completion(correlationId).orElseThrow().toCompletableFuture().isDone(),
                        "ambiguous forwarding must retain an unresolved authority result");
            }

            assertTrue(twoNotices.await(1, TimeUnit.SECONDS));
        } finally {
            releaseProcessor.countDown();
        }

        assertTrue(twoCommits.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < forwardingFailures.length; i++) {
            ProposalResult eventual = execution.completion("forwarded-typed-" + i).orElseThrow()
                    .toCompletableFuture().get(1, TimeUnit.SECONDS);
            assertInstanceOf(ProposalResult.Committed.class, eventual);
        }
        assertEquals(2, authorityWrites.get());
    }

    @Test
    void aFullRealQueueReportsKnownNonAdmissionAndReleasesThePermit() throws Exception {
        Task task = fromJson(TASK_JSON, Task.class);
        store.save(task, true);
        CountDownLatch firstEventEnteredProcessor = new CountDownLatch(1);
        CountDownLatch releaseProcessor = new CountDownLatch(1);
        processor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void onEventProcessed(String taskId, Event event) {
                if (event instanceof TaskStatusUpdateEvent) {
                    firstEventEnteredProcessor.countDown();
                    await(releaseProcessor);
                }
            }

            @Override
            public void onTaskFinalized(String taskId) {
            }
        });
        EventQueue main = EventQueueUtil.getEventQueueBuilder(bus)
                .taskId(task.id()).queueSize(1).taskStateProvider(store).mainEventBus(bus).build();
        EventQueue producer = main.tap();
        EventQueue observer = main.tap();
        TaskStatusUpdateEvent progress = TaskStatusUpdateEvent.builder()
                .taskId(task.id()).contextId(task.contextId())
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build();
        producer.enqueueEvent(progress);
        assertTrue(firstEventEnteredProcessor.await(1, TimeUnit.SECONDS));

        AtomicInteger authorityCalls = new AtomicInteger();
        TaskSnapshot base = java.util.Objects.requireNonNull(store.readSnapshot(task.id()));
        ExecutorSubmissionService service = new ExecutorSubmissionService(
                new ExecutorSubmissionService.ProposalAuthority() {
                    @Override
                    public ProposalResult process(TaskProposal proposal) {
                        authorityCalls.incrementAndGet();
                        return new ProposalResult.Committed(proposal.correlationId(), base, List.of(), false);
                    }

                    @Override
                    public TaskSnapshot refreshTask(String taskId) {
                        return base;
                    }
                }, Duration.ofMillis(30), Duration.ofSeconds(1));
        ProposalResult admission = service.submitTask(new TaskProposal("real-full-queue", base, task), producer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertEquals(ProposalResult.Failed.CommitCertainty.KNOWN_NOT_COMMITTED,
                assertInstanceOf(ProposalResult.Failed.class, admission).certainty());
        assertEquals(0, authorityCalls.get());
        releaseProcessor.countDown();
        assertInstanceOf(TaskStatusUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent());
        producer.enqueueEvent(progress, 1, TimeUnit.SECONDS);
        assertInstanceOf(TaskStatusUpdateEvent.class, observer.dequeueEventItem(1_000).getEvent(),
                "a real admission timeout must not leak the queue permit");
    }

    private EventQueue createMainQueue(String taskId) {
        return EventQueueUtil.getEventQueueBuilder(bus).taskId(taskId).mainEventBus(bus).build();
    }

    private record ForgedTaskItem(Task task, @Nullable TaskProposal proposal,
            @Nullable TaskSnapshot snapshot) implements EventQueueItem {
        @Override
        public Task getEvent() {
            return task;
        }

        @Override
        public boolean isReplicated() {
            return false;
        }

        @Override
        public @Nullable TaskProposal taskProposal() {
            return proposal;
        }

        @Override
        public @Nullable TaskSnapshot committedSnapshot() {
            return snapshot;
        }
    }

    private static RequestContext requestContext(Task task, TaskSnapshot snapshot) throws Exception {
        return new RequestContext.Builder()
                .setTaskId(task.id())
                .setContextId(task.contextId())
                .setTaskSnapshot(snapshot)
                .build();
    }

    private static List<?> statusMessageValues(Message message) {
        Map<?, ?> metadata = java.util.Objects.requireNonNull(message.metadata());
        Map<?, ?> nested = (Map<?, ?>) metadata.get("nested");
        return (List<?>) nested.get("values");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("authority release timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
