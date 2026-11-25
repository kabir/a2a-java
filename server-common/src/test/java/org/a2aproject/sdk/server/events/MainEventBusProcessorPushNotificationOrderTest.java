package org.a2aproject.sdk.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.InMemoryWebhookDispatchGate;
import org.a2aproject.sdk.server.tasks.OrderedWebhookDispatcher;
import org.a2aproject.sdk.server.tasks.OrderedWebhookSender;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskMutation;
import org.a2aproject.sdk.server.tasks.MutationPlan;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for https://github.com/a2aproject/a2a-java/issues/775: push notifications
 * for the same task used to be submitted independently to the shared {@code ForkJoinPool},
 * so the client could observe them out of order relative to how the agent produced them.
 */
public class MainEventBusProcessorPushNotificationOrderTest {

    private static final String CONTEXT_ID = "test-context";

    private MainEventBus mainEventBus;
    private MainEventBusProcessor mainEventBusProcessor;
    private InMemoryTaskStore taskStore;
    private InMemoryQueueManager queueManager;

    @BeforeEach
    public void setUp() {
        taskStore = new InMemoryTaskStore();
        mainEventBus = new MainEventBus();
        queueManager = new InMemoryQueueManager(null, mainEventBus);
    }

    @AfterEach
    public void tearDown() {
        if (mainEventBusProcessor != null) {
            EventQueueUtil.stop(mainEventBusProcessor);
        }
    }

    @Test
    public void testEventsForSameTaskDeliveredInOrder() throws InterruptedException {
        String taskId = "order-task";
        int eventCount = 20;
        List<Integer> deliveryOrder = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(eventCount);

        // The Nth push sleeps longer the SMALLER N is, so an unordered executor would very
        // likely deliver a later, faster push before an earlier, slower one -- exactly the
        // interleaving #775 reported. A correctly-ordered chain delivers 0, 1, 2, ... regardless.
        PushNotificationSender sender = (event, snapshot) -> {
            TaskStatusUpdateEvent statusEvent = (TaskStatusUpdateEvent) event;
            int index = extractSequence(statusEvent);
            try {
                Thread.sleep(Math.max(0, (eventCount - index) * 2L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            deliveryOrder.add(index);
            latch.countDown();
            return true;
        };

        mainEventBusProcessor = new MainEventBusProcessor(mainEventBus, taskStore, sender, queueManager);
        EventQueueUtil.start(mainEventBusProcessor);
        taskStore.save(Task.builder().id(taskId).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build(), false);
        EventQueue eventQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus)
                .taskId(taskId)
                .mainEventBus(mainEventBus)
                .build().tap();

        for (int i = 0; i < eventCount; i++) {
            eventQueue.enqueueEvent(statusEventWithSequence(taskId, i));
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All push notifications should complete within timeout");
        assertEquals(eventCount, deliveryOrder.size());
        for (int i = 0; i < eventCount; i++) {
            assertEquals(i, deliveryOrder.get(i),
                    "Push notification " + i + " should be delivered in produced order, got " + deliveryOrder);
        }
    }

    @Test
    public void committedOriginWithoutLocalQueueStillEntersOrderedWebhookLane() throws Exception {
        String taskId = "origin-without-queue";
        Task initial = Task.builder().id(taskId).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).generation(1).build();
        taskStore.save(initial, false);
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane(taskId, 0, 2);
        CountDownLatch dispatched = new CountDownLatch(1);
        AtomicInteger firstGeneration = new AtomicInteger();
        PushNotificationSender sender = new PushNotificationSender() {
            @Override
            public boolean sendNotification(org.a2aproject.sdk.spec.StreamingEventKind event, Task snapshot) {
                return true;
            }
        };
        OrderedWebhookSender orderedSender = new OrderedWebhookSender() {
            @Override
            public boolean supportsOrderedDispatch() {
                return true;
            }

            @Override
            public java.util.concurrent.CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch,
                    long deadlineNanos, LongSupplier nanoTime) {
                firstGeneration.set((int) batch.identity().firstGeneration());
                dispatched.countDown();
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public java.util.concurrent.CompletionStage<Boolean> sendOrderedLegacyTaskSnapshot(Task snapshot,
                    long deadlineNanos, LongSupplier nanoTime) {
                return CompletableFuture.completedFuture(true);
            }
        };
        PushNotificationSender combinedSender = new PushNotificationSender() {
            @Override
            public boolean sendNotification(org.a2aproject.sdk.spec.StreamingEventKind event, Task snapshot) {
                return sender.sendNotification(event, snapshot);
            }
        };
        // Use an adapter object with both compatibility and ordered surfaces.
        PushNotificationSender pushSender = new CombinedOrderedPushSender(combinedSender, orderedSender);
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, pushSender);
        try {
            mainEventBusProcessor = new MainEventBusProcessor(mainEventBus, taskStore, pushSender,
                    queueManager, dispatcher);
            TaskMutation mutation = current -> {
                Task next = Task.builder(current.task()).generation(2).build();
                TaskStatusUpdateEvent event = new TaskStatusUpdateEvent(taskId,
                        new TaskStatus(TaskState.TASK_STATE_WORKING), CONTEXT_ID, null, 2);
                return new MutationPlan(next, List.of(event), true);
            };

            mainEventBusProcessor.commitAndPublish(taskId, mutation);

            assertTrue(dispatched.await(5, TimeUnit.SECONDS));
            assertEquals(2, firstGeneration.get());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (gate.readLane(taskId).orElseThrow().nextGeneration() != 3 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(3, gate.readLane(taskId).orElseThrow().nextGeneration());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    public void legacySenderRemainsUsableWhenTheLocalDispatcherHasNoOrderedAdapter() throws Exception {
        String taskId = "legacy-origin-without-queue";
        Task initial = Task.builder().id(taskId).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).generation(1).build();
        taskStore.save(initial, false);
        taskStore.readSnapshot(taskId);
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane(taskId, 0, 2);
        CountDownLatch dispatched = new CountDownLatch(1);
        AtomicInteger sentGeneration = new AtomicInteger();
        PushNotificationSender legacySender = (event, snapshot) -> {
            if (event instanceof TaskStatusUpdateEvent status) {
                sentGeneration.set((int) status.generation());
            }
            dispatched.countDown();
            return true;
        };
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, legacySender);
        try {
            mainEventBusProcessor = new MainEventBusProcessor(mainEventBus, taskStore, legacySender,
                    queueManager, dispatcher);
            TaskMutation mutation = current -> {
                Task next = Task.builder(current.task()).generation(2).build();
                TaskStatusUpdateEvent event = new TaskStatusUpdateEvent(taskId,
                        new TaskStatus(TaskState.TASK_STATE_WORKING), CONTEXT_ID, null, 2);
                return new MutationPlan(next, List.of(event), true);
            };

            mainEventBusProcessor.commitAndPublish(taskId, mutation);

            assertTrue(dispatched.await(5, TimeUnit.SECONDS));
            assertEquals(2, sentGeneration.get());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    public void capabilityInspectionFailureDoesNotFallBackToLegacyHttpSender() throws Exception {
        String taskId = "capability-inspection-failure";
        taskStore.save(Task.builder().id(taskId).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).generation(1).build(), false);
        AtomicInteger legacyAttempts = new AtomicInteger();
        CountDownLatch postCommitFailure = new CountDownLatch(1);
        PushNotificationSender legacySender = (event, snapshot) -> {
            legacyAttempts.incrementAndGet();
            return true;
        };
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(
                new InMemoryWebhookDispatchGate(), legacySender) {
            @Override
            public boolean supportsOrderedDispatch() {
                throw new IllegalStateException("lazy sender initialization failed");
            }
        };
        try {
            mainEventBusProcessor = new MainEventBusProcessor(mainEventBus, taskStore, legacySender,
                    queueManager, dispatcher);
            mainEventBusProcessor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void onEventProcessed(String id, org.a2aproject.sdk.spec.Event event) {
                }

                @Override
                public void onTaskFinalized(String id) {
                }

                @Override
                public void onPostCommitFailure(String id, CommittedTaskBatch batch, Throwable failure) {
                    postCommitFailure.countDown();
                }
            });
            mainEventBusProcessor.commitAndPublish(taskId, current -> {
                TaskStatus terminal = new TaskStatus(TaskState.TASK_STATE_COMPLETED);
                Task next = Task.builder(current.task()).status(terminal).generation(2).build();
                return new MutationPlan(next,
                        List.of(new TaskStatusUpdateEvent(taskId, terminal, CONTEXT_ID, null, 2)), true);
            });

            assertTrue(postCommitFailure.await(5, TimeUnit.SECONDS));
            assertFalse(legacyAttempts.get() > 0, "capability failure must not run the legacy HTTP sender");
        } finally {
            dispatcher.close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void terminalBatchRejectedAtAdmissionCapacityCannotReenterAfterCapacityFrees() throws Exception {
        String taskId = "capacity-rejected-terminal";
        taskStore.save(Task.builder().id(taskId).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).generation(1).build(), false);
        AtomicInteger senderAttempts = new AtomicInteger();
        PushNotificationSender sender = (event, snapshot) -> {
            senderAttempts.incrementAndGet();
            return true;
        };
        mainEventBusProcessor = new MainEventBusProcessor(mainEventBus, taskStore, sender, queueManager);
        java.lang.reflect.Field activeField = MainEventBusProcessor.class.getDeclaredField("activePushAdmissions");
        activeField.setAccessible(true);
        Map<CommittedTaskBatch.Identity, CompletableFuture<Boolean>> active =
                (Map<CommittedTaskBatch.Identity, CompletableFuture<Boolean>>) activeField.get(mainEventBusProcessor);
        for (int index = 0; index < 2048; index++) {
            active.put(new CommittedTaskBatch.Identity("filler-" + index, 1, 1, 1), new CompletableFuture<>());
        }

        org.a2aproject.sdk.server.tasks.CommittedMutation committed = mainEventBusProcessor.commitAndPublish(taskId,
                current -> {
                    TaskStatus terminal = new TaskStatus(TaskState.TASK_STATE_COMPLETED);
                    Task next = Task.builder(current.task()).status(terminal).generation(2).build();
                    return new MutationPlan(next,
                            List.of(new TaskStatusUpdateEvent(taskId, terminal, CONTEXT_ID, null, 2)), true);
                });
        CommittedTaskBatch batch = new CommittedTaskBatch(committed.snapshot(), committed.events());
        active.clear();

        assertFalse(mainEventBusProcessor.admitFinalizedCommittedBatch(batch, false, committed.writerEpoch())
                .toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(0, senderAttempts.get(), "a terminal identity rejected at capacity stays closed");
    }

    private static final class CombinedOrderedPushSender implements PushNotificationSender, OrderedWebhookSender {
        private final PushNotificationSender compatibility;
        private final OrderedWebhookSender ordered;

        CombinedOrderedPushSender(PushNotificationSender compatibility, OrderedWebhookSender ordered) {
            this.compatibility = compatibility;
            this.ordered = ordered;
        }

        @Override
        public boolean sendNotification(org.a2aproject.sdk.spec.StreamingEventKind event, Task snapshot) {
            return compatibility.sendNotification(event, snapshot);
        }

        @Override
        public boolean supportsOrderedDispatch() {
            return ordered.supportsOrderedDispatch();
        }

        @Override
        public java.util.concurrent.CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch,
                long deadlineNanos, LongSupplier nanoTime) {
            return ordered.sendOrderedBatch(batch, deadlineNanos, nanoTime);
        }

        @Override
        public java.util.concurrent.CompletionStage<Boolean> sendOrderedLegacyTaskSnapshot(Task snapshot,
                long deadlineNanos, LongSupplier nanoTime) {
            return ordered.sendOrderedLegacyTaskSnapshot(snapshot, deadlineNanos, nanoTime);
        }
    }

    @Test
    public void testDifferentTasksAreIndependent() throws InterruptedException {
        String slowTaskId = "slow-task";
        String fastTaskId = "fast-task";
        CountDownLatch slowLatch = new CountDownLatch(1);
        CountDownLatch fastLatch = new CountDownLatch(1);
        AtomicInteger fastDeliveredWhileSlowBlocked = new AtomicInteger(-1);

        PushNotificationSender sender = (event, snapshot) -> {
            TaskStatusUpdateEvent statusEvent = (TaskStatusUpdateEvent) event;
            if (statusEvent.taskId().equals(slowTaskId)) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                slowLatch.countDown();
            } else {
                fastDeliveredWhileSlowBlocked.set(slowLatch.getCount() > 0 ? 1 : 0);
                fastLatch.countDown();
            }
            return true;
        };

        mainEventBusProcessor = new MainEventBusProcessor(mainEventBus, taskStore, sender, queueManager);
        EventQueueUtil.start(mainEventBusProcessor);

        EventQueue slowQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus)
                .taskId(slowTaskId).mainEventBus(mainEventBus).build().tap();
        EventQueue fastQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus)
                .taskId(fastTaskId).mainEventBus(mainEventBus).build().tap();

        taskStore.save(Task.builder().id(slowTaskId).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build(), false);
        taskStore.save(Task.builder().id(fastTaskId).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build(), false);
        slowQueue.enqueueEvent(statusEventWithSequence(slowTaskId, 0));

        fastQueue.enqueueEvent(statusEventWithSequence(fastTaskId, 0));

        assertTrue(fastLatch.await(1, TimeUnit.SECONDS),
                "The fast task's push must not wait for the slow task's push");
        assertEquals(1, fastDeliveredWhileSlowBlocked.get(),
                "The fast task should have been delivered while the slow task was still blocked");

        assertTrue(slowLatch.await(5, TimeUnit.SECONDS), "The slow task's push should eventually complete");
    }

    @Test
    public void testSynchronousExecutorDoesNotLeakChainEntries() throws InterruptedException {
        // One entry per DISTINCT task leaks, not repeated pushes for the same one --
        // a single task's own map slot just gets overwritten each time. Only several
        // different task IDs reveal growth.
        //
        // Each task produces TWO push notifications: one for the initial Task event and one for the
        // StatusUpdate event (both implement StreamingEventKind). With a synchronous executor,
        // pushTask runs inside compute()'s lambda -- before the entry is stored in the map -- so
        // the latch fires before the entry is even visible. To avoid a TOCTOU race between the
        // while-loop exit (count==0 transiently) and the assertEquals re-reading count, we:
        //   1. Set the latch to cover ALL push notifications (taskCount * 2).
        //   2. Always sleep before the first count check so the last cleanup has time to run.
        //   3. Assert on the captured count, not a fresh read.
        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);
        PushNotificationSender sender = (event, snapshot) -> {
            latch.countDown();
            return true;
        };

        mainEventBusProcessor = new MainEventBusProcessor(mainEventBus, taskStore, sender, queueManager);
        mainEventBusProcessor.setPushNotificationExecutor(Runnable::run);
        EventQueueUtil.start(mainEventBusProcessor);

        for (int i = 0; i < taskCount; i++) {
            String taskId = "sync-task-" + i;
            EventQueue eventQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus)
                    .taskId(taskId)
                    .mainEventBus(mainEventBus)
                    .build().tap();
            taskStore.save(Task.builder()
                    .id(taskId)
                    .contextId(CONTEXT_ID)
                    .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                    .build(), false);
            eventQueue.enqueueEvent(statusEventWithSequence(taskId, 0));
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All push notifications should complete");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        int count;
        do {
            // Always sleep before checking: with the sync executor, pushTask fires inside
            // compute()'s lambda (before the entry is stored), so the latch may reach 0
            // slightly before the final whenComplete removal runs. A brief sleep lets the
            // processor thread finish that last cleanup step.
            Thread.sleep(10);
            count = mainEventBusProcessor.pushNotificationChainCount();
        } while (count != 0 && System.nanoTime() < deadline);
        assertEquals(0, count,
                "A completed task's chain entry must not be left in the map");
    }

    @Test
    public void testDeliveryFailureDoesNotStopSubsequentNotifications() throws InterruptedException {
        // When sendNotification() returns false, the processor must continue delivering
        // subsequent events rather than stopping or throwing.
        String taskId = "failure-resilience-task";
        int eventCount = 5;
        CountDownLatch latch = new CountDownLatch(eventCount);
        AtomicInteger attemptCount = new AtomicInteger(0);

        PushNotificationSender sender = (event, snapshot) -> {
            int attempt = attemptCount.incrementAndGet();
            latch.countDown();
            // Alternate: fail on even-indexed attempts to prove the chain continues.
            return attempt % 2 != 0;
        };

        mainEventBusProcessor = new MainEventBusProcessor(mainEventBus, taskStore, sender, queueManager);
        EventQueueUtil.start(mainEventBusProcessor);
        EventQueue eventQueue = EventQueueUtil.getEventQueueBuilder(mainEventBus)
                .taskId(taskId)
                .mainEventBus(mainEventBus)
                .build().tap();

        taskStore.save(Task.builder()
                .id(taskId)
                .contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                .build(), false);
        for (int i = 0; i < eventCount; i++) {
            eventQueue.enqueueEvent(statusEventWithSequence(taskId, i));
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "All notifications must be attempted even when some deliveries return false");
        assertEquals(eventCount, attemptCount.get(),
                "Every event must be passed to the sender regardless of prior delivery failures");
    }

    private TaskStatusUpdateEvent statusEventWithSequence(String taskId, int sequence) {
        return new TaskStatusUpdateEvent(taskId, new TaskStatus(TaskState.TASK_STATE_WORKING, null, null),
                CONTEXT_ID, java.util.Map.of("sequence", sequence));
    }

    private int extractSequence(TaskStatusUpdateEvent event) {
        return ((Number) event.metadata().get("sequence")).intValue();
    }
}
