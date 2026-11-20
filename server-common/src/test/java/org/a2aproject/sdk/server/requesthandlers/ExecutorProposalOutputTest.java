package org.a2aproject.sdk.server.requesthandlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.events.EventQueueUtil;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.MainEventBusProcessorCallback;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.ProposalResult;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskMutationAuthority;
import org.a2aproject.sdk.server.tasks.TaskMutationPlanner;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class ExecutorProposalOutputTest {

    @Test
    void terminalProposalBatchProjectsOneSummaryForOldVersionAndCanonicalEventsForOnePointOne() throws Exception {
        RunResult oldVersion = runStream("1.0", false, true, false);
        assertEquals(List.of(Task.class), eventTypes(oldVersion.output()));
        assertEquals(TaskState.TASK_STATE_COMPLETED,
                assertInstanceOf(Task.class, oldVersion.output().get(0)).status().state());

        RunResult currentVersion = runStream("1.1", false, true, false);
        assertEquals(List.of(TaskMessageUpdateEvent.class, TaskArtifactUpdateEvent.class,
                TaskStatusUpdateEvent.class), eventTypes(currentVersion.output()));
    }

    @Test
    void terminalLegacySummaryCompletesAfterSingleSubscriberDemand() throws Exception {
        for (boolean existingTask : List.of(false, true)) {
            RunResult result = runStream("1.0", existingTask, true, false, 1, false,
                    Message.Role.ROLE_AGENT, new InMemoryTaskStore());

            assertTrue(result.completedAfterInitialDemand());
            assertEquals(List.of(Task.class), eventTypes(result.output()));
        }
    }

    @Test
    void nonterminalLegacySummaryDoesNotUseDemandForFollowingVisibleEvent() throws Exception {
        RunResult result = runStream("1.0", true, false, false, 1, true,
                Message.Role.ROLE_AGENT, new InMemoryTaskStore());

        assertFalse(result.completedAfterInitialDemand());
        assertEquals(List.of(Task.class, TaskStatusUpdateEvent.class), eventTypes(result.output()));
    }

    @Test
    void existingTerminalProposalBatchProjectsOnceAndKeepsCanonicalOrderForOnePointOne() throws Exception {
        RunResult oldVersion = runStream("1.0", true, true, false);
        assertEquals(List.of(Task.class), eventTypes(oldVersion.output()));

        RunResult currentVersion = runStream("1.1", true, true, false);
        assertEquals(List.of(TaskArtifactUpdateEvent.class, TaskStatusUpdateEvent.class),
                eventTypes(currentVersion.output()));
    }

    @Test
    void artifactOnlyProposalProjectsOneSummaryForOldVersionAndOneDeltaForOnePointOne() throws Exception {
        RunResult oldVersion = runStream("1.0", true, false, false);
        assertEquals(List.of(Task.class), eventTypes(oldVersion.output()));

        RunResult currentVersion = runStream("1.1", true, false, false);
        assertEquals(1, currentVersion.output().stream().filter(TaskArtifactUpdateEvent.class::isInstance).count());
        assertTrue(currentVersion.output().stream().allMatch(event ->
                event instanceof TaskStatusUpdateEvent || event instanceof TaskArtifactUpdateEvent));
    }

    @Test
    void proposalAfterDeliveredDirectMessageRejectsWithoutMaterializingTask() throws Exception {
        for (String version : List.of("1.0", "1.1")) {
            for (Message.Role role : List.of(Message.Role.ROLE_AGENT, Message.Role.ROLE_USER)) {
                RunResult result = runStream(version, false, true, true, Long.MAX_VALUE, false,
                        role, new InMemoryTaskStore());
                assertEquals(List.of(Message.class), eventTypes(result.output()));
                assertInstanceOf(ProposalResult.Rejected.class, result.proposalResult());
                assertNull(result.store().readSnapshot(result.taskId()));
            }
        }
    }

    @Test
    void directMessageIsDeliveredWhenTheAtomicSnapshotReadWouldFail() throws Exception {
        FailFirstSnapshotReadStore store = new FailFirstSnapshotReadStore();

        RunResult result = runStream("1.0", false, false, true, Long.MAX_VALUE, false,
                Message.Role.ROLE_AGENT, store);

        assertEquals(List.of(Message.class), eventTypes(result.output()));
        assertNull(store.get(result.taskId()));
        assertFalse(store.readFailed().get(), "the direct response path must not need an atomic snapshot read");
    }

    @Test
    void immediateUnaryResultUsesProposalSnapshotDespiteLaterUnrelatedWrite() throws Exception {
        AtomicReference<TaskSnapshot> laterSnapshot = new AtomicReference<>();
        AtomicReference<ProposalResult.Committed> proposalResult = new AtomicReference<>();
        AtomicBoolean injectLaterWrite = new AtomicBoolean();
        InMemoryTaskStore store = new InMemoryTaskStore();
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queues);
        processor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void admitPushNotifications(org.a2aproject.sdk.server.events.CommittedTaskBatch batch,
                    Runnable invokeSender, java.util.function.Consumer<Throwable> reportFailure) {
                if (injectLaterWrite.compareAndSet(false, true)) {
                    laterSnapshot.set(new TaskMutationAuthority(store).mutate(batch.taskId(),
                            current -> TaskMutationPlanner.planStatus(current,
                                    TaskStatusUpdateEvent.builder().taskId(batch.taskId()).contextId("ctx")
                                            .status(new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED)).build()))
                            .snapshot());
                }
                invokeSender.run();
            }

            @Override
            public void onEventProcessed(String taskId, org.a2aproject.sdk.spec.Event event) {
            }

            @Override
            public void onTaskFinalized(String taskId) {
            }
        });
        ExecutorService workers = Executors.newCachedThreadPool();
        CountDownLatch agentDone = new CountDownLatch(1);
        DefaultRequestHandler handler = DefaultRequestHandler.builder().agentExecutor(new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                try {
                    Task proposed = emitter.taskBuilder().status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                            .artifacts(List.of(artifact("first"))).build();
                    proposalResult.set((ProposalResult.Committed) emitter.submitTask(
                            new org.a2aproject.sdk.server.tasks.TaskProposal("unary-proposal", null, proposed))
                            .toCompletableFuture().get(4, TimeUnit.SECONDS));
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                } finally {
                    agentDone.countDown();
                }
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
            }
        }).taskStore(store).queueManager(queues).mainEventBusProcessor(processor)
                .executor(workers).eventConsumerExecutor(workers).authorizationRequired(false).build();
        try {
            processor.start();
            Message request = request("unary-opening", null);
            MessageSendConfiguration configuration = MessageSendConfiguration.builder().returnImmediately(true).build();
            EventKind result = handler.onMessageSend(new MessageSendParams(request, configuration, null),
                    new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of(), "1.1"));

            Task response = assertInstanceOf(Task.class, result);
            assertTrue(agentDone.await(5, TimeUnit.SECONDS));
            assertNotNull(proposalResult.get());
            assertNotNull(laterSnapshot.get(), "the controlled later write must have run");
            assertTrue(laterSnapshot.get().task().generation() > proposalResult.get().snapshot().task().generation());
            assertEquals(proposalResult.get().snapshot().task(), response);
            assertEquals(TaskState.TASK_STATE_WORKING, response.status().state());
        } finally {
            EventQueueUtil.stop(processor);
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static RunResult runStream(String version, boolean existingTask, boolean terminal,
            boolean sendDirectFirst) throws Exception {
        return runStream(version, existingTask, terminal, sendDirectFirst, Long.MAX_VALUE, false,
                Message.Role.ROLE_AGENT, new InMemoryTaskStore());
    }

    private static RunResult runStream(String version, boolean existingTask, boolean terminal,
            boolean sendDirectFirst, long initialDemand, boolean emitFollowupFinal,
            Message.Role directRole, InMemoryTaskStore store) throws Exception {
        String taskId = "proposal-output-task";
        if (existingTask) {
            store.save(Task.builder().id(taskId).contextId("ctx")
                    .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build(), false);
        }
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queues);
        ExecutorService workers = Executors.newCachedThreadPool();
        List<StreamingEventKind> output = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        CountDownLatch firstItem = new CountDownLatch(1);
        CountDownLatch followupProcessed = new CountDownLatch(1);
        CountDownLatch agentDone = new CountDownLatch(1);
        CountDownLatch directDelivered = new CountDownLatch(1);
        AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();
        AtomicReference<String> invocationTaskId = new AtomicReference<>();
        AtomicReference<ProposalResult> proposalResult = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        processor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void onEventProcessed(String processedTaskId, org.a2aproject.sdk.spec.Event event) {
                if (taskId.equals(processedTaskId) && emitFollowupFinal
                        && event instanceof TaskStatusUpdateEvent status
                        && status.status().state() == TaskState.TASK_STATE_COMPLETED) {
                    followupProcessed.countDown();
                }
            }

            @Override
            public void onTaskFinalized(String finalizedTaskId) {
            }
        });
        DefaultRequestHandler handler = DefaultRequestHandler.builder().agentExecutor(new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                invocationTaskId.set(context.getTaskId());
                try {
                    if (sendDirectFirst) {
                        emitter.emitEvent(Message.builder().messageId("direct-response").role(directRole)
                                .parts(List.of(new TextPart("direct answer"))).build());
                        if (!directDelivered.await(3, TimeUnit.SECONDS)) {
                            throw new AssertionError("direct response was not delivered");
                        }
                    }
                    Task.Builder builder = context.getTask() == null
                            ? emitter.taskBuilder() : Task.builder(context.getTask());
                    Task proposed = builder.status(new TaskStatus(terminal ? TaskState.TASK_STATE_COMPLETED
                                    : TaskState.TASK_STATE_WORKING))
                            .artifacts(List.of(artifact("proposal-artifact"))).build();
                    proposalResult.set(emitter.submitTask(new org.a2aproject.sdk.server.tasks.TaskProposal(
                            "proposal-output", context.getTaskSnapshot(), proposed))
                            .toCompletableFuture().get(4, TimeUnit.SECONDS));
                    if (emitFollowupFinal) {
                        emitter.emitEvent(TaskStatusUpdateEvent.builder().taskId(context.getTaskId())
                                .contextId(context.getContextId())
                                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build());
                    }
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    agentDone.countDown();
                }
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
            }
        }).taskStore(store).queueManager(queues).mainEventBusProcessor(processor)
                .executor(workers).eventConsumerExecutor(workers).authorizationRequired(false).build();
        try {
            processor.start();
            Message request = request("opening-request", existingTask ? taskId : null);
            Flow.Publisher<StreamingEventKind> publisher = handler.onMessageSendStream(
                    new MessageSendParams(request, null, null),
                    new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of(), version));
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscriptionRef.set(subscription);
                    subscription.request(initialDemand);
                }

                @Override
                public void onNext(StreamingEventKind item) {
                    output.add(item);
                    firstItem.countDown();
                    if (item instanceof Message message && "direct-response".equals(message.messageId())) {
                        directDelivered.countDown();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    failure.set(throwable);
                    done.countDown();
                }

                @Override
                public void onComplete() {
                    done.countDown();
                }
            });
            if (emitFollowupFinal) {
                assertTrue(agentDone.await(5, TimeUnit.SECONDS));
                assertTrue(followupProcessed.await(5, TimeUnit.SECONDS));
                assertTrue(firstItem.await(5, TimeUnit.SECONDS));
                assertEquals(1, output.size(), "a visible event must not exceed the first unit of demand");
            }
            boolean completedAfterInitialDemand = done.await(1500, TimeUnit.MILLISECONDS);
            if (!completedAfterInitialDemand) {
                subscriptionRef.get().request(1);
            }
            assertTrue(done.await(5, TimeUnit.SECONDS), "stream should terminate once the pending event is requested");
            assertNull(failure.get());
            return new RunResult(output, proposalResult.get(), store,
                    invocationTaskId.get() == null ? taskId : invocationTaskId.get(), completedAfterInitialDemand);
        } finally {
            EventQueueUtil.stop(processor);
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static Message request(String id, String taskId) {
        return Message.builder().messageId(id).taskId(taskId).contextId("ctx").role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("request"))).build();
    }

    private static Artifact artifact(String id) {
        return Artifact.builder().artifactId(id).parts(List.of(new TextPart("output"))).build();
    }

    private static List<Class<?>> eventTypes(List<StreamingEventKind> events) {
        return events.stream().map(Object::getClass).toList();
    }

    private record RunResult(List<StreamingEventKind> output, ProposalResult proposalResult,
            InMemoryTaskStore store, String taskId, boolean completedAfterInitialDemand) {
    }

    private static final class FailFirstSnapshotReadStore extends InMemoryTaskStore {
        private final AtomicBoolean readFailed = new AtomicBoolean();

        @Override
        public @org.jspecify.annotations.Nullable TaskSnapshot readSnapshot(String taskId) {
            readFailed.set(true);
            throw new IllegalStateException("injected read failure");
        }

        AtomicBoolean readFailed() {
            return readFailed;
        }
    }
}
