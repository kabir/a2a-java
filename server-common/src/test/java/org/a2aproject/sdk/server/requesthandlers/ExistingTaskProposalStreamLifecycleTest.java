package org.a2aproject.sdk.server.requesthandlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExistingTaskProposalStreamLifecycleTest {
    @ParameterizedTest
    @ValueSource(strings = {"1.0", "1.1"})
    void terminalProposalRemainsOnOriginStreamUntilOrderedDelivery(String protocolVersion) throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task initial = Task.builder().id("task").contextId("ctx")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build();
        store.save(initial, false);
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queues);
        ExecutorService workers = Executors.newCachedThreadPool();
        CountDownLatch processorBlocked = new CountDownLatch(1);
        CountDownLatch releaseProcessor = new CountDownLatch(1);
        CountDownLatch proposalSubmitted = new CountDownLatch(1);
        CountDownLatch terminalProcessed = new CountDownLatch(1);
        CountDownLatch streamCompleted = new CountDownLatch(1);
        List<StreamingEventKind> output = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();

        DefaultRequestHandler handler = DefaultRequestHandler.builder()
                .agentExecutor(new AgentExecutor() {
                    @Override
                    public void execute(RequestContext context, AgentEmitter emitter) {
                        emitter.emitEvent(Task.builder(context.getTask())
                                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build());
                        proposalSubmitted.countDown();
                    }

                    @Override
                    public void cancel(RequestContext context, AgentEmitter emitter) {
                    }
                })
                .taskStore(store)
                .queueManager(queues)
                .mainEventBusProcessor(processor)
                .executor(workers)
                .eventConsumerExecutor(workers)
                .authorizationRequired(false)
                .build();

        processor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void onEventProcessed(String taskId, Event event) {
                if ("blocker".equals(taskId)) {
                    processorBlocked.countDown();
                    await(releaseProcessor);
                } else if ("task".equals(taskId)) {
                    terminalProcessed.countDown();
                }
            }

            @Override
            public void onTaskFinalized(String taskId) {
            }
        });
        try {
            processor.start();
            queues.createOrTap("blocker").enqueueEvent(TaskStatusUpdateEvent.builder()
                    .taskId("blocker").contextId("ctx")
                    .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build());
            assertTrue(processorBlocked.await(2, TimeUnit.SECONDS), "preceding event should pause bus processing");

            Message message = Message.builder().messageId("request").taskId("task").contextId("ctx")
                    .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("complete"))).build();
            handler.onMessageSendStream(new MessageSendParams(message, null, null),
                    new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of(), protocolVersion))
                    .subscribe(new Flow.Subscriber<>() {
                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            subscription.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(StreamingEventKind event) {
                            output.add(event);
                        }

                        @Override
                        public void onError(Throwable failure) {
                            streamFailure.set(failure);
                            streamCompleted.countDown();
                        }

                        @Override
                        public void onComplete() {
                            streamCompleted.countDown();
                        }
                    });

            assertTrue(proposalSubmitted.await(2, TimeUnit.SECONDS), "executor should submit terminal Task proposal");
            assertFalse(streamCompleted.await(3, TimeUnit.SECONDS),
                    "agent completion must not close the stream while its admitted proposal waits in the bus");

            releaseProcessor.countDown();
            assertTrue(terminalProcessed.await(5, TimeUnit.SECONDS), "terminal status should commit and publish");
            assertTrue(streamCompleted.await(5, TimeUnit.SECONDS), "stream should complete after terminal delivery");
            assertNull(streamFailure.get());
            assertEquals(1, output.size());
            if ("1.0".equals(protocolVersion)) {
                Task summary = assertInstanceOf(Task.class, output.get(0));
                assertEquals(TaskState.TASK_STATE_COMPLETED, summary.status().state());
            } else {
                TaskStatusUpdateEvent status = assertInstanceOf(TaskStatusUpdateEvent.class, output.get(0));
                assertEquals(TaskState.TASK_STATE_COMPLETED, status.status().state());
            }
        } finally {
            releaseProcessor.countDown();
            EventQueueUtil.stop(processor);
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void stalledProposalExpiresItsOriginWaitWithoutCancellingTheCommit() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task initial = Task.builder().id("task").contextId("ctx")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build();
        store.save(initial, false);
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queues);
        ExecutorService workers = Executors.newCachedThreadPool();
        CountDownLatch processorBlocked = new CountDownLatch(1);
        CountDownLatch releaseProcessor = new CountDownLatch(1);
        CountDownLatch proposalSubmitted = new CountDownLatch(1);
        CountDownLatch terminalProcessed = new CountDownLatch(1);
        CountDownLatch streamCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();
        List<StreamingEventKind> output = new CopyOnWriteArrayList<>();

        DefaultRequestHandler handler = DefaultRequestHandler.builder()
                .agentExecutor(new AgentExecutor() {
                    @Override
                    public void execute(RequestContext context, AgentEmitter emitter) {
                        emitter.emitEvent(Task.builder(context.getTask())
                                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build());
                        proposalSubmitted.countDown();
                    }

                    @Override
                    public void cancel(RequestContext context, AgentEmitter emitter) {
                    }
                })
                .taskStore(store)
                .queueManager(queues)
                .mainEventBusProcessor(processor)
                .executor(workers)
                .eventConsumerExecutor(workers)
                .authorizationRequired(false)
                .build();

        processor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void onEventProcessed(String taskId, Event event) {
                if ("blocker".equals(taskId)) {
                    processorBlocked.countDown();
                    await(releaseProcessor);
                } else if ("task".equals(taskId)) {
                    terminalProcessed.countDown();
                }
            }

            @Override
            public void onTaskFinalized(String taskId) {
            }
        });

        try {
            processor.start();
            queues.createOrTap("blocker").enqueueEvent(TaskStatusUpdateEvent.builder()
                    .taskId("blocker").contextId("ctx")
                    .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build());
            assertTrue(processorBlocked.await(2, TimeUnit.SECONDS));

            Message message = Message.builder().messageId("request").taskId("task").contextId("ctx")
                    .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("complete"))).build();
            handler.onMessageSendStream(new MessageSendParams(message, null, null),
                    new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of(), "1.0"))
                    .subscribe(new Flow.Subscriber<>() {
                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            subscription.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(StreamingEventKind event) {
                            output.add(event);
                        }

                        @Override
                        public void onError(Throwable failure) {
                            streamFailure.set(failure);
                            streamCompleted.countDown();
                        }

                        @Override
                        public void onComplete() {
                            streamCompleted.countDown();
                        }
                    });

            assertTrue(proposalSubmitted.await(2, TimeUnit.SECONDS));
            assertTrue(streamCompleted.await(7, TimeUnit.SECONDS),
                    "a stalled proposal must not keep the origin stream open indefinitely");
            assertNull(streamFailure.get());
            assertTrue(output.isEmpty(), "the stalled processor has not delivered the proposal yet");
            assertEquals(TaskState.TASK_STATE_SUBMITTED, store.readSnapshot("task").task().status().state());

            releaseProcessor.countDown();
            assertTrue(terminalProcessed.await(5, TimeUnit.SECONDS));
            assertEquals(TaskState.TASK_STATE_COMPLETED, store.readSnapshot("task").task().status().state(),
                    "expiring the stream wait does not cancel the still-pending ordered commit");
        } finally {
            releaseProcessor.countDown();
            EventQueueUtil.stop(processor);
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void olderProposalTimeoutDoesNotExpireANewerOrdinaryTerminalEvent() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task initial = Task.builder().id("task").contextId("ctx")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build();
        store.save(initial, false);
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queues);
        ExecutorService workers = Executors.newCachedThreadPool();
        CountDownLatch processorBlocked = new CountDownLatch(1);
        CountDownLatch releaseProcessor = new CountDownLatch(1);
        CountDownLatch proposalSubmitted = new CountDownLatch(1);
        CountDownLatch submitOrdinaryFinal = new CountDownLatch(1);
        CountDownLatch ordinaryFinalQueued = new CountDownLatch(1);
        CountDownLatch terminalProcessed = new CountDownLatch(1);
        CountDownLatch streamCompleted = new CountDownLatch(1);
        AtomicLong proposalSubmittedAt = new AtomicLong();
        AtomicLong ordinaryFinalQueuedAt = new AtomicLong();
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();
        List<StreamingEventKind> output = new CopyOnWriteArrayList<>();

        DefaultRequestHandler handler = DefaultRequestHandler.builder()
                .agentExecutor(new AgentExecutor() {
                    @Override
                    public void execute(RequestContext context, AgentEmitter emitter) {
                        emitter.emitEvent(context.getTask());
                        proposalSubmittedAt.set(System.nanoTime());
                        proposalSubmitted.countDown();
                        try {
                            if (!submitOrdinaryFinal.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("timed out waiting to submit ordinary terminal event");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError("interrupted waiting to submit ordinary terminal event", e);
                        }
                        emitter.emitEvent(TaskStatusUpdateEvent.builder()
                                .taskId("task").contextId("ctx")
                                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build());
                        ordinaryFinalQueuedAt.set(System.nanoTime());
                        ordinaryFinalQueued.countDown();
                    }

                    @Override
                    public void cancel(RequestContext context, AgentEmitter emitter) {
                    }
                })
                .taskStore(store)
                .queueManager(queues)
                .mainEventBusProcessor(processor)
                .executor(workers)
                .eventConsumerExecutor(workers)
                .authorizationRequired(false)
                .build();

        processor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void onEventProcessed(String taskId, Event event) {
                if ("blocker".equals(taskId)) {
                    processorBlocked.countDown();
                    await(releaseProcessor);
                } else if ("task".equals(taskId) && event instanceof TaskStatusUpdateEvent status
                        && status.status().state() == TaskState.TASK_STATE_COMPLETED) {
                    terminalProcessed.countDown();
                }
            }

            @Override
            public void onTaskFinalized(String taskId) {
            }
        });

        try {
            processor.start();
            queues.createOrTap("blocker").enqueueEvent(TaskStatusUpdateEvent.builder()
                    .taskId("blocker").contextId("ctx")
                    .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build());
            assertTrue(processorBlocked.await(2, TimeUnit.SECONDS));

            Message message = Message.builder().messageId("request").taskId("task").contextId("ctx")
                    .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("finish"))).build();
            handler.onMessageSendStream(new MessageSendParams(message, null, null),
                    new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of(), "1.1"))
                    .subscribe(new Flow.Subscriber<>() {
                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            subscription.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(StreamingEventKind event) {
                            output.add(event);
                        }

                        @Override
                        public void onError(Throwable failure) {
                            streamFailure.set(failure);
                            streamCompleted.countDown();
                        }

                        @Override
                        public void onComplete() {
                            streamCompleted.countDown();
                        }
                    });

            assertTrue(proposalSubmitted.await(2, TimeUnit.SECONDS));
            Thread.sleep(2_600);
            submitOrdinaryFinal.countDown();
            assertTrue(ordinaryFinalQueued.await(2, TimeUnit.SECONDS));

            long releaseAt = proposalSubmittedAt.get() + TimeUnit.MILLISECONDS.toNanos(4_200);
            long untilRelease = releaseAt - System.nanoTime();
            if (untilRelease > 0) {
                TimeUnit.NANOSECONDS.sleep(untilRelease);
            }
            long terminalWaitMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ordinaryFinalQueuedAt.get());
            assertTrue(terminalWaitMillis < TimeUnit.SECONDS.toMillis(3),
                    "the ordinary final event is still within its own delivery timeout");
            assertFalse(streamCompleted.await(100, TimeUnit.MILLISECONDS),
                    "the older proposal timeout must not close the stream awaiting a newer final event");

            releaseProcessor.countDown();
            assertTrue(terminalProcessed.await(5, TimeUnit.SECONDS));
            assertTrue(streamCompleted.await(5, TimeUnit.SECONDS));
            assertNull(streamFailure.get());
            assertEquals(1, output.size());
            TaskStatusUpdateEvent status = assertInstanceOf(TaskStatusUpdateEvent.class, output.get(0));
            assertEquals(TaskState.TASK_STATE_COMPLETED, status.status().state());
        } finally {
            submitOrdinaryFinal.countDown();
            releaseProcessor.countDown();
            EventQueueUtil.stop(processor);
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for proposal stream test gate");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for proposal stream test gate", e);
        }
    }
}
