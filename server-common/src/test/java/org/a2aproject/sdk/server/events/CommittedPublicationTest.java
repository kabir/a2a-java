package org.a2aproject.sdk.server.events;

import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_COMPLETED;
import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_FAILED;
import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_AUTH_REQUIRED;
import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_INPUT_REQUIRED;
import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_UNSPECIFIED;
import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.MutationPlan;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.ResultAggregator;
import org.a2aproject.sdk.server.tasks.TaskCommitSlot;
import org.a2aproject.sdk.server.tasks.TaskCommitSlotCallback;
import org.a2aproject.sdk.server.tasks.TaskManager;
import org.a2aproject.sdk.server.tasks.TaskMutation;
import org.a2aproject.sdk.server.tasks.TaskMutationAuthority;
import org.a2aproject.sdk.server.tasks.TaskMutationPlanner;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.a2aproject.sdk.spec.TextPart;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class CommittedPublicationTest {

    private static final String TASK_ID = "publication-task";
    private static final String CONTEXT_ID = "publication-context";
    private static final String OTHER_CONTEXT_ID = "other-context";

    @Test
    void staleFullTaskIsRejectedWithoutReplacingCanonicalStateOrFailingOtherObservers() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Artifact userArtifact = Artifact.builder()
                .artifactId("user-input")
                .parts(List.of(new TextPart("retained")))
                .role(Message.Role.ROLE_USER)
                .startGeneration(5)
                .endGeneration(7)
                .build();
        Task authoritative = Task.builder(task(TASK_STATE_WORKING, 7))
                .artifacts(List.of(userArtifact))
                .history(List.of())
                .build();
        store.save(authoritative, false);
        TaskSnapshot before = Objects.requireNonNull(store.readSnapshot(TASK_ID));
        List<Event> pushes = new CopyOnWriteArrayList<>();
        List<Event> replications = new CopyOnWriteArrayList<>();

        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushes.add(event);
            return true;
        }, item -> replications.add(item.getEvent()))) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            Task stale = Task.builder()
                    .id(TASK_ID)
                    .contextId(OTHER_CONTEXT_ID)
                    .status(new TaskStatus(TASK_STATE_WORKING))
                    .history(List.of())
                    .artifacts(List.of())
                    .generation(0)
                    .build();

            liveBus.producer.enqueueEvent(stale);
            EventQueueItem rejection = liveBus.producer.dequeueEventItem(5000);

            assertTrue(rejection.getEvent() instanceof A2AError,
                    "the proposing request should receive a typed rejection");
            assertEquals(before, store.readSnapshot(TASK_ID),
                    "a stale full Task must not replace the authoritative snapshot");
            assertTrue(observer.events.isEmpty(), "unrelated observers must not receive the rejected Task");
            assertNull(observer.failure, "the rejected proposal must not fail another observer");
            assertTrue(pushes.isEmpty(), "rejected state must not enter push delivery");
            assertTrue(replications.isEmpty(), "rejected state must not enter replication");

            liveBus.producer.enqueueEvent(status(TASK_STATE_INPUT_REQUIRED, 0));
            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS),
                    "the observer must remain active for a later valid mutation");
            assertNull(observer.failure);
            assertEquals(List.of(8L), observer.events.stream()
                    .map(event -> ((TaskStatusUpdateEvent) event).generation()).toList());
            TaskSnapshot after = Objects.requireNonNull(store.readSnapshot(TASK_ID));
            assertEquals(CONTEXT_ID, after.task().contextId());
            assertEquals(List.of(userArtifact), after.task().artifacts());
            assertEquals(8, after.task().generation());
            assertEquals(List.of(8L), replications.stream()
                    .map(event -> ((TaskStatusUpdateEvent) event).generation()).toList());
        }
    }

    @Test
    void executorErrorPublishesCanonicalFailureAndPreservesOriginError() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 7), false);
        AtomicReference<CommittedTaskBatch> admitted = new AtomicReference<>();
        CountDownLatch admissionCalled = new CountDownLatch(1);
        AtomicReference<StreamingEventKind> pushedEvent = new AtomicReference<>();
        AtomicReference<Task> pushedSnapshot = new AtomicReference<>();
        CountDownLatch pushCalled = new CountDownLatch(1);
        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushedEvent.set(event);
            pushedSnapshot.set(snapshot);
            pushCalled.countDown();
            return true;
        }, item -> {
        })) {
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void beforePushNotification(CommittedTaskBatch batch) {
                    admitted.set(batch);
                    admissionCalled.countDown();
                }

                @Override
                public void onEventProcessed(String taskId, Event event) {
                }

                @Override
                public void onTaskFinalized(String taskId) {
                }
            });
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            InternalError requestError = new InternalError("executor failed");

            liveBus.producer.enqueueEvent(requestError);
            EventQueueItem originResult = liveBus.producer.dequeueEventItem(5000);

            assertEquals(requestError, originResult.getEvent(),
                    "the request origin must retain the executor's typed error");
            assertTrue(admissionCalled.await(5, TimeUnit.SECONDS),
                    "the committed FAILED generation must pass webhook admission");
            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS),
                    "other observers must receive the canonical FAILED status");
            assertTrue(pushCalled.await(5, TimeUnit.SECONDS));
            CommittedTaskBatch batch = Objects.requireNonNull(admitted.get());
            assertEquals(List.of(8L), batch.generationPositions());
            assertEquals(8, batch.snapshot().task().generation());
            assertInstanceOf(TaskStatusUpdateEvent.class, pushedEvent.get());
            assertEquals(8, ((TaskStatusUpdateEvent) pushedEvent.get()).generation());
            assertEquals(batch.snapshot().task(), pushedSnapshot.get());
            assertEquals(List.of(TASK_STATE_FAILED), observer.events.stream()
                    .map(event -> ((TaskStatusUpdateEvent) event).status().state()).toList());
            assertNull(observer.failure);
            assertEquals(TASK_STATE_FAILED, Objects.requireNonNull(store.readSnapshot(TASK_ID))
                    .task().status().state());
        }
    }

    @Test
    void executorErrorWithoutTaskOrForTerminalTaskDoesNotReserveAPosition() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        AtomicReference<TaskCommitSlot> reserved = new AtomicReference<>();
        TaskManager withoutTask = new TaskManager(TASK_ID, null, store, null);

        TaskManager.CommittedProcessingResult missingTask = withoutTask.processCommitted(
                new InternalError("executor failed"), (slot, mutation) -> reserved.set(slot));

        assertTrue(missingTask.isFinal());
        assertNull(missingTask.mutation());
        assertNull(reserved.get(), "an error without a task context must not allocate a publication slot");
        assertNull(store.readSnapshot(TASK_ID));

        Task terminal = task(TASK_STATE_COMPLETED, 9);
        store.save(terminal, false);
        TaskSnapshot before = Objects.requireNonNull(store.readSnapshot(TASK_ID));
        TaskManager alreadyTerminal = new TaskManager(TASK_ID, null, store, null);

        TaskManager.CommittedProcessingResult unchanged = alreadyTerminal.processCommitted(
                new InternalError("late executor failure"), (slot, mutation) -> reserved.set(slot));

        assertTrue(unchanged.isFinal());
        assertNotNull(unchanged.mutation());
        assertFalse(unchanged.mutation().changed());
        assertTrue(unchanged.mutation().events().isEmpty());
        assertEquals(before, unchanged.mutation().snapshot());
        assertNull(reserved.get(), "an already-terminal task must not reserve a publication slot");
        assertEquals(before, store.readSnapshot(TASK_ID));
    }

    @Test
    void plannerRejectionIsScopedToOriginAndDoesNotStrandFinalExpectations() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 7), false);

        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> true, item -> {
        })) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            TaskStatusUpdateEvent wrongContextFinal = TaskStatusUpdateEvent.builder()
                    .taskId(TASK_ID)
                    .contextId(OTHER_CONTEXT_ID)
                    .status(new TaskStatus(TASK_STATE_COMPLETED))
                    .build();
            TaskSnapshot before = Objects.requireNonNull(store.readSnapshot(TASK_ID));

            liveBus.producer.enqueueEvent(wrongContextFinal);
            EventQueueItem rejection = liveBus.producer.dequeueEventItem(5000);

            assertTrue(rejection.getEvent() instanceof A2AError,
                    "the origin should receive the planner's typed rejection");
            assertEquals(before, store.readSnapshot(TASK_ID));
            assertFalse(liveBus.observer.isAwaitingFinalEvent(),
                    "rejected terminal proposals must settle submitted final expectations");
            assertNull(observer.failure, "unrelated observation must stay open");

            liveBus.producer.enqueueEvent(status(TASK_STATE_INPUT_REQUIRED, 0));
            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS),
                    "the active observer should receive the next valid status");
            assertNull(observer.failure);
            assertEquals(TASK_STATE_INPUT_REQUIRED,
                    ((TaskStatusUpdateEvent) observer.events.get(0)).status().state());
            assertEquals(8, Objects.requireNonNull(store.readSnapshot(TASK_ID)).task().generation());
        }
    }

    @Test
    void rawTaskMessageUpdateIsRejectedAtTheLiveCommittedBoundary() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 7), false);
        List<Event> pushes = new CopyOnWriteArrayList<>();
        List<Event> replications = new CopyOnWriteArrayList<>();

        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushes.add(event);
            return true;
        }, item -> replications.add(item.getEvent()))) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            TaskMessageUpdateEvent raw = new TaskMessageUpdateEvent(TASK_ID, CONTEXT_ID,
                    Message.builder().messageId("raw").role(Message.Role.ROLE_AGENT)
                            .parts(new TextPart("raw message update")).build(), null, 99);
            TaskSnapshot before = Objects.requireNonNull(store.readSnapshot(TASK_ID));

            liveBus.producer.enqueueEvent(raw);
            EventQueueItem rejection = liveBus.producer.dequeueEventItem(5000);

            assertInstanceOf(A2AError.class, rejection.getEvent());
            assertEquals(before, store.readSnapshot(TASK_ID));
            assertTrue(observer.events.isEmpty());
            assertNull(observer.failure);
            assertTrue(pushes.isEmpty());
            assertTrue(replications.isEmpty());

            liveBus.producer.enqueueEvent(status(TASK_STATE_INPUT_REQUIRED, 0));
            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            assertNull(observer.failure);
            assertEquals(List.of(8L), observer.events.stream()
                    .map(event -> ((TaskStatusUpdateEvent) event).generation()).toList());
        }
    }

    @Test
    void liveProcessorKeepsEverySinkInvisibleUntilAtomicCommitThenUsesMatchingSnapshot() throws Exception {
        GateableTaskStore store = new GateableTaskStore();
        store.save(task(TASK_STATE_WORKING, 7), false);
        store.armCommitGate();
        List<Event> pushes = new CopyOnWriteArrayList<>();
        AtomicReference<Task> pushSnapshot = new AtomicReference<>();
        List<Event> replications = new CopyOnWriteArrayList<>();
        List<Task> replicationSnapshots = new CopyOnWriteArrayList<>();
        CountDownLatch admission = new CountDownLatch(1);
        AtomicReference<CommittedTaskBatch> admittedBatch = new AtomicReference<>();

        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushes.add(event);
            pushSnapshot.set(snapshot);
            return true;
        }, item -> {
            replications.add(item.getEvent());
            replicationSnapshots.add(Objects.requireNonNull(item.committedSnapshot()).task());
        })) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void beforePushNotification(CommittedTaskBatch batch) {
                    admittedBatch.set(batch);
                    admission.countDown();
                }

                @Override
                public void onEventProcessed(String taskId, Event event) {
                }

                @Override
                public void onTaskFinalized(String taskId) {
                }
            });
            liveBus.producer.enqueueEvent(status(TASK_STATE_INPUT_REQUIRED, 0));

            assertTrue(store.commitGateEntered.await(5, TimeUnit.SECONDS));
            assertEquals(7, Objects.requireNonNull(store.readSnapshot(TASK_ID)).task().generation());
            assertTrue(observer.events.isEmpty(), "child visibility must wait for commit");
            assertNull(observer.failure);
            assertTrue(pushes.isEmpty(), "push visibility must wait for commit");
            assertTrue(replications.isEmpty(), "replication visibility must wait for commit");
            assertEquals(1, admission.getCount());

            store.releaseCommitGate();
            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            assertTrue(admission.await(5, TimeUnit.SECONDS));
            TaskStatusUpdateEvent canonical = (TaskStatusUpdateEvent) observer.events.get(0);
            CommittedTaskBatch batch = Objects.requireNonNull(admittedBatch.get());
            assertEquals(8, canonical.generation());
            assertEquals(List.of(8L), batch.generationPositions());
            assertEquals(batch.snapshot().task(), pushSnapshot.get());
            assertEquals(batch.snapshot().task(), store.readSnapshot(TASK_ID).task());
            assertEquals(List.of(canonical), pushes);
            assertEquals(List.of(canonical), replications);
            assertEquals(List.of(batch.snapshot().task()), observer.snapshots);
            assertEquals(List.of(batch.snapshot().task()), replicationSnapshots);
        } finally {
            store.releaseCommitGate();
        }
    }

    @Test
    void liveProcessorDeliversOriginalMultiGenerationBatchBeforeTerminalCallbacks() throws Exception {
        ExpandingTaskStore store = new ExpandingTaskStore();
        store.save(task(TASK_STATE_WORKING, 7), false);
        store.expandNextStatusMutation();
        List<Long> pushedPositions = new CopyOnWriteArrayList<>();
        List<Task> pushedSnapshots = new CopyOnWriteArrayList<>();
        List<String> callbacks = new CopyOnWriteArrayList<>();
        AtomicReference<CommittedTaskBatch> admitted = new AtomicReference<>();
        CountDownLatch finalized = new CountDownLatch(1);

        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushedPositions.add(generation(event));
            pushedSnapshots.add(snapshot);
            return true;
        }, item -> {
            throw new AssertionError("terminal committed batches are not eligible for legacy replication");
        })) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store, 2);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void beforePushNotification(CommittedTaskBatch batch) {
                    admitted.set(batch);
                }

                @Override
                public void onEventProcessed(String taskId, Event event) {
                    callbacks.add("processed-" + generation((StreamingEventKind) event));
                }

                @Override
                public void onTaskFinalized(String taskId) {
                    callbacks.add("finalized");
                    finalized.countDown();
                }
            });
            liveBus.producer.enqueueEvent(status(TASK_STATE_COMPLETED, 0));

            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            assertTrue(observer.completed.await(5, TimeUnit.SECONDS));
            assertTrue(finalized.await(5, TimeUnit.SECONDS));
            assertNull(observer.failure);
            assertEquals(List.of(8L, 9L), observer.events.stream()
                    .map(event -> ((TaskStatusUpdateEvent) event).generation()).toList());
            CommittedTaskBatch batch = Objects.requireNonNull(admitted.get());
            assertEquals(List.of(8L, 9L), batch.generationPositions());
            assertEquals(9, batch.snapshot().task().generation());
            assertEquals(List.of(8L, 9L), pushedPositions);
            assertEquals(List.of(batch.snapshot().task(), batch.snapshot().task()), pushedSnapshots);
            assertEquals(List.of(batch.snapshot().task(), batch.snapshot().task()), observer.snapshots);
            assertEquals(List.of("processed-8", "processed-9", "finalized"), callbacks);
        }
    }

    @Test
    void throwingEligibleReplicationHookIsReportedAfterCommitAndDoesNotAbortSubscribers() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 7), false);
        CountDownLatch postCommitFailure = new CountDownLatch(1);
        List<Event> pushes = new CopyOnWriteArrayList<>();

        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushes.add(event);
            return true;
        }, item -> {
            throw new IllegalStateException("replication unavailable");
        })) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void onPostCommitFailure(String taskId, CommittedTaskBatch batch, Throwable failure) {
                    assertEquals(List.of(8L), batch.generationPositions());
                    postCommitFailure.countDown();
                }

                @Override
                public void onEventProcessed(String taskId, Event event) {
                }

                @Override
                public void onTaskFinalized(String taskId) {
                }
            });
            liveBus.producer.enqueueEvent(status(TASK_STATE_INPUT_REQUIRED, 0));

            assertTrue(postCommitFailure.await(5, TimeUnit.SECONDS));
            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            assertNull(observer.failure);
            assertEquals(8, ((TaskStatusUpdateEvent) observer.events.get(0)).generation());
            assertEquals(8, ((TaskStatusUpdateEvent) pushes.get(0)).generation());
        }
    }

    @Test
    void deferredWebhookAdmissionDoesNotBlockTerminalSubscriberDeliveryOrFinalization() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 7), false);
        AtomicReference<Runnable> senderInvocation = new AtomicReference<>();
        AtomicReference<CommittedTaskBatch> admitted = new AtomicReference<>();
        CountDownLatch admission = new CountDownLatch(1);
        CountDownLatch finalized = new CountDownLatch(1);
        List<Event> pushes = new CopyOnWriteArrayList<>();

        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushes.add(event);
            return true;
        }, null)) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void admitPushNotifications(CommittedTaskBatch batch, Runnable invokeSender) {
                    admitted.set(batch);
                    senderInvocation.set(invokeSender);
                    admission.countDown();
                }

                @Override
                public void onEventProcessed(String taskId, Event event) {
                }

                @Override
                public void onTaskFinalized(String taskId) {
                    finalized.countDown();
                }
            });
            liveBus.producer.enqueueEvent(status(TASK_STATE_COMPLETED, 0));

            assertTrue(admission.await(5, TimeUnit.SECONDS));
            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            assertTrue(finalized.await(5, TimeUnit.SECONDS));
            assertTrue(pushes.isEmpty(), "the sender remains deferred until admission invokes it");
            TaskStatusUpdateEvent delivered = (TaskStatusUpdateEvent) observer.events.get(0);
            CommittedTaskBatch batch = Objects.requireNonNull(admitted.get());
            assertEquals(List.of(8L), batch.generationPositions());
            assertEquals(8, batch.snapshot().task().generation());

            Objects.requireNonNull(senderInvocation.get()).run();
            assertEquals(List.of(delivered), pushes);
        }
    }

    @Test
    void failedPostCommitAdmissionReportsFailureWithoutReplacingCommittedEventOrFinalization() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 7), false);
        CountDownLatch postCommitFailure = new CountDownLatch(1);
        CountDownLatch finalized = new CountDownLatch(1);
        AtomicReference<Consumer<Throwable>> reportAdmissionFailure = new AtomicReference<>();
        List<Event> pushes = new CopyOnWriteArrayList<>();

        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushes.add(event);
            return true;
        }, null)) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void admitPushNotifications(CommittedTaskBatch batch, Runnable invokeSender,
                        Consumer<Throwable> reportFailure) {
                    assertEquals(List.of(8L), batch.generationPositions());
                    assertEquals(8, batch.snapshot().task().generation());
                    reportAdmissionFailure.set(reportFailure);
                }

                @Override
                public void onPostCommitFailure(String taskId, CommittedTaskBatch batch, Throwable failure) {
                    assertEquals(List.of(8L), batch.generationPositions());
                    postCommitFailure.countDown();
                }

                @Override
                public void onEventProcessed(String taskId, Event event) {
                }

                @Override
                public void onTaskFinalized(String taskId) {
                    finalized.countDown();
                }
            });
            liveBus.producer.enqueueEvent(status(TASK_STATE_COMPLETED, 0));

            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            assertTrue(finalized.await(5, TimeUnit.SECONDS));
            assertInstanceOf(TaskStatusUpdateEvent.class, observer.events.get(0));
            assertNull(observer.failure);
            assertTrue(pushes.isEmpty());
            assertEquals(TASK_STATE_COMPLETED, store.readSnapshot(TASK_ID).task().status().state());

            Objects.requireNonNull(reportAdmissionFailure.get())
                    .accept(new IllegalStateException("deferred webhook admission failed"));
            assertTrue(postCommitFailure.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void blockingPostCommitFailureReporterCannotHoldSubscriberDeliveryOrFinalization() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 7), false);
        CountDownLatch reportEntered = new CountDownLatch(1);
        CountDownLatch releaseReport = new CountDownLatch(1);
        CountDownLatch finalized = new CountDownLatch(1);
        try (LiveBus liveBus = new LiveBus(store, PushNotificationSender.NOOP, null)) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void admitPushNotifications(CommittedTaskBatch batch, Runnable invokeSender,
                        Consumer<Throwable> reportFailure) {
                    reportFailure.accept(new IllegalStateException("admission failed"));
                }

                @Override
                public void onPostCommitFailure(String taskId, CommittedTaskBatch batch, Throwable failure) {
                    reportEntered.countDown();
                    try {
                        if (!releaseReport.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to release the failure reporter");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }

                @Override public void onEventProcessed(String taskId, Event event) { }
                @Override public void onTaskFinalized(String taskId) { finalized.countDown(); }
            });
            liveBus.producer.enqueueEvent(status(TASK_STATE_COMPLETED, 0));

            try {
                assertTrue(reportEntered.await(5, TimeUnit.SECONDS));
                assertTrue(observer.completed.await(5, TimeUnit.SECONDS),
                        "post-commit error reporting must not hold child delivery");
                assertTrue(finalized.await(5, TimeUnit.SECONDS),
                        "post-commit error reporting must not hold finalization");
            } finally {
                releaseReport.countDown();
            }
        }
    }

    @Test
    void failedTerminalChildDeliveryReportsPostCommitFailureAndReleasesQueueState() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 7), false);
        MainEventBus eventBus = new MainEventBus();
        FailingDistributionMainQueue mainQueue = new FailingDistributionMainQueue(store, eventBus);
        EventQueue producer = mainQueue.tap();
        EventQueue observer = mainQueue.tap();
        MainEventBusProcessor processor = new MainEventBusProcessor(eventBus, store, PushNotificationSender.NOOP,
                new InMemoryQueueManager(store, eventBus));
        processor.setPushNotificationExecutor(Runnable::run);
        CountDownLatch deliveryFailure = new CountDownLatch(1);
        CountDownLatch finalized = new CountDownLatch(1);
        CountDownLatch controlProcessed = new CountDownLatch(1);
        processor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void onPostCommitFailure(String taskId, CommittedTaskBatch batch, Throwable failure) {
                assertEquals(List.of(8L), batch.generationPositions());
                deliveryFailure.countDown();
            }

            @Override
            public void onEventProcessed(String taskId, Event event) {
                if (event instanceof Message) {
                    controlProcessed.countDown();
                }
            }

            @Override
            public void onTaskFinalized(String taskId) {
                finalized.countDown();
            }
        });
        processor.start();

        try {
            producer.enqueueEvent(status(TASK_STATE_COMPLETED, 0));
            assertTrue(deliveryFailure.await(5, TimeUnit.SECONDS));
            assertTrue(finalized.await(5, TimeUnit.SECONDS));
            assertEquals(TASK_STATE_COMPLETED, store.readSnapshot(TASK_ID).task().status().state());
            assertEquals(0, observer.size(), "a failed child delivery must not be replaced by a storage error");
            assertFalse(observer.isAwaitingFinalEvent(), "failed terminal delivery must clear the child's final wait");
            Message control = Message.builder().messageId("after-delivery-failure")
                    .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("still processing"))).build();
            producer.enqueueEvent(control, 5, TimeUnit.SECONDS);
            assertTrue(controlProcessed.await(5, TimeUnit.SECONDS),
                    "a later event must still pass through the processor after a committed delivery failure");
            assertEquals(control, observer.dequeueEventItem(5000).getEvent());
        } finally {
            processor.stop();
            mainQueue.close(true);
        }
    }

    @Test
    void canonicalOpeningMessageOccupiesGenerationOneBeforeStatusOrArtifact() throws Exception {
        InMemoryTaskStore statusStore = new InMemoryTaskStore();
        Message opening = Message.builder().messageId("opening-status").role(Message.Role.ROLE_USER)
                .parts(new TextPart("question")).build();
        TaskManager statusManager = new TaskManager(TASK_ID, CONTEXT_ID, statusStore, opening);
        TaskManager.CommittedProcessingResult statusResult = statusManager.processCommitted(
                TaskStatusUpdateEvent.builder().taskId(TASK_ID).contextId(CONTEXT_ID)
                        .status(new TaskStatus(TASK_STATE_WORKING)).build(), TaskCommitSlotCallback.NONE);

        assertEquals(List.of(1L, 2L), statusResult.mutation().events().stream()
                .map(event -> ((StreamingEventKind) event) instanceof TaskMessageUpdateEvent messageEvent
                        ? messageEvent.generation() : ((TaskStatusUpdateEvent) event).generation())
                .toList());
        assertEquals(2, statusResult.mutation().snapshot().task().generation());
        Message canonicalOpening = statusResult.mutation().snapshot().task().history().get(0);
        assertEquals("opening-status", canonicalOpening.messageId());
        assertEquals(TASK_ID, canonicalOpening.taskId());
        assertEquals(CONTEXT_ID, canonicalOpening.contextId());

        InMemoryTaskStore artifactStore = new InMemoryTaskStore();
        Message artifactOpening = Message.builder().messageId("opening-artifact").role(Message.Role.ROLE_USER)
                .parts(new TextPart("question")).build();
        TaskArtifactUpdateEvent artifact = TaskArtifactUpdateEvent.builder().taskId(TASK_ID)
                .contextId(CONTEXT_ID).artifact(Artifact.builder().artifactId("output")
                        .parts(List.of(new TextPart("answer"))).build()).append(false).build();
        TaskManager artifactManager = new TaskManager(TASK_ID, CONTEXT_ID, artifactStore, artifactOpening);
        TaskManager.CommittedProcessingResult artifactResult = artifactManager.processCommitted(
                artifact, TaskCommitSlotCallback.NONE);

        assertEquals(1, ((TaskMessageUpdateEvent) artifactResult.mutation().events().get(0)).generation());
        assertEquals(2, ((org.a2aproject.sdk.spec.TaskArtifactUpdateEvent) artifactResult.mutation().events().get(1))
                .generation());
        assertEquals(2, artifactResult.mutation().snapshot().task().generation());
    }

    @Test
    void atomicUnspecifiedIsFinalForObserverQueueReplicationAndCleanup() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 7), false);
        List<Event> replication = new CopyOnWriteArrayList<>();
        List<Event> pushes = new CopyOnWriteArrayList<>();
        AtomicReference<Task> pushSnapshot = new AtomicReference<>();
        CountDownLatch finalized = new CountDownLatch(1);

        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushes.add(event);
            pushSnapshot.set(snapshot);
            return true;
        }, item -> replication.add(item.getEvent()))) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void onEventProcessed(String taskId, Event event) {
                }

                @Override
                public void onTaskFinalized(String taskId) {
                    finalized.countDown();
                }
            });
            liveBus.producer.enqueueEvent(status(TASK_STATE_UNSPECIFIED, 0));

            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            assertTrue(observer.completed.await(5, TimeUnit.SECONDS),
                    "canonical unspecified finality must close the local consumer");
            assertTrue(finalized.await(5, TimeUnit.SECONDS));
            assertNull(observer.failure);
            TaskStatusUpdateEvent event = (TaskStatusUpdateEvent) observer.events.get(0);
            assertEquals(8, event.generation());
            assertEquals(List.of(event), pushes);
            assertEquals(List.of(event), replication,
                    "legacy replication must retain the enum's non-final UNSPECIFIED behavior");
            assertEquals(store.readSnapshot(TASK_ID).task(), pushSnapshot.get());
            assertTrue(store.isTaskFinalized(TASK_ID));
            assertFalse(store.isTaskActive(TASK_ID));
        }
    }

    @Test
    void interimInitialDeltaWithoutOpeningProvenanceDoesNotPublishCanonicalBatch() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        AtomicReference<CommittedTaskBatch> admission = new AtomicReference<>();
        List<Event> replicated = new CopyOnWriteArrayList<>();
        List<StreamingEventKind> pushed = new CopyOnWriteArrayList<>();

        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushed.add(event);
            return true;
        }, item -> replicated.add(item.getEvent()))) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void beforePushNotification(CommittedTaskBatch batch) {
                    admission.set(batch);
                }

                @Override
                public void onEventProcessed(String taskId, Event event) {
                }

                @Override
                public void onTaskFinalized(String taskId) {
                }
            });
            liveBus.producer.enqueueEvent(status(TASK_STATE_WORKING, 77));

            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            assertNull(observer.failure);
            assertNull(admission.get(), "unknown opening provenance must stay outside canonical batching");
            TaskStatusUpdateEvent delivered = (TaskStatusUpdateEvent) observer.events.get(0);
            assertEquals(77, delivered.generation(), "the interim legacy event keeps its incoming form");
            assertEquals(List.of(delivered), pushed);
            assertEquals(List.of(delivered), replicated);
            assertEquals(0, store.get(TASK_ID).generation(),
                    "interim legacy creation must not invent a canonical watermark");
        }
    }

    @Test
    void absentReadRacingCommittedCreationUsesTheCanonicalNextGeneration() throws Exception {
        MutationGateTaskStore store = new MutationGateTaskStore();
        store.armNextMutationGate();
        List<Long> observed = new CopyOnWriteArrayList<>();
        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> true, null)) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            liveBus.producer.enqueueEvent(status(TASK_STATE_WORKING, 0));
            assertTrue(store.mutationEntered.await(5, TimeUnit.SECONDS), "processor must reach atomic mutation");

            store.save(task(TASK_STATE_WORKING, 7), false);
            store.releaseMutation.countDown();

            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            assertNull(observer.failure);
            observer.events.forEach(event -> observed.add(((TaskStatusUpdateEvent) event).generation()));
            assertEquals(List.of(8L), observed);
            assertEquals(8, store.get(TASK_ID).generation());
            assertEquals(List.of(store.get(TASK_ID)), observer.snapshots,
                    "a raced existing task must publish its exact committed snapshot");
        } finally {
            store.releaseMutation.countDown();
        }
    }

    @Test
    void deferredWebhookBatchesKeepTheirAdmissionOrder() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 1), false);
        List<Long> pushed = new CopyOnWriteArrayList<>();
        List<Long> pushSnapshots = new CopyOnWriteArrayList<>();
        List<Long> replicated = new CopyOnWriteArrayList<>();
        AtomicReference<Runnable> deferred = new AtomicReference<>();
        CountDownLatch firstDeferred = new CountDownLatch(1);
        CountDownLatch bothPushed = new CountDownLatch(2);
        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushed.add(((TaskStatusUpdateEvent) event).generation());
            pushSnapshots.add(Objects.requireNonNull(snapshot).generation());
            bothPushed.countDown();
            return true;
        }, item -> replicated.add(((TaskStatusUpdateEvent) item.getEvent()).generation()))) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store, 2);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void admitPushNotifications(CommittedTaskBatch batch, Runnable invokeSender,
                        Consumer<Throwable> reportFailure) {
                    if (batch.generationPositions().equals(List.of(2L))) {
                        deferred.set(invokeSender);
                        firstDeferred.countDown();
                    } else {
                        invokeSender.run();
                    }
                }

                @Override public void onEventProcessed(String taskId, Event event) { }
                @Override public void onTaskFinalized(String taskId) { }
            });
            liveBus.producer.enqueueEvent(status(TASK_STATE_WORKING, 0));
            assertTrue(firstDeferred.await(5, TimeUnit.SECONDS));
            liveBus.producer.enqueueEvent(status(TASK_STATE_INPUT_REQUIRED, 0));
            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS),
                    "child delivery must continue while generation 2 webhook admission is deferred");
            deferred.get().run();
            assertTrue(bothPushed.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(2L, 3L), pushed);
            assertEquals(List.of(2L, 3L), pushSnapshots,
                    "each sender invocation must receive its matching committed snapshot");
            assertEquals(List.of(2L, 3L), replicated,
                    "eligible local replication hooks must receive canonical event positions");
            assertEquals(List.of(2L, 3L), observer.events.stream()
                    .map(event -> ((TaskStatusUpdateEvent) event).generation()).toList());
            assertEquals(List.of(2L, 3L), observer.snapshots.stream().map(Task::generation).toList());
        }
    }

    @Test
    void failedDeferredWebhookAdmissionReleasesTheNextBatch() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 1), false);
        List<Long> pushed = new CopyOnWriteArrayList<>();
        AtomicReference<Runnable> failAdmission = new AtomicReference<>();
        CountDownLatch firstDeferred = new CountDownLatch(1);
        CountDownLatch successorPushed = new CountDownLatch(1);
        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushed.add(((TaskStatusUpdateEvent) event).generation());
            successorPushed.countDown();
            return true;
        }, null)) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store, 2);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void admitPushNotifications(CommittedTaskBatch batch, Runnable invokeSender,
                        Consumer<Throwable> reportFailure) {
                    if (batch.generationPositions().equals(List.of(2L))) {
                        failAdmission.set(() -> reportFailure.accept(
                                new IllegalStateException("webhook predecessor failed")));
                        firstDeferred.countDown();
                    } else {
                        invokeSender.run();
                    }
                }

                @Override public void onEventProcessed(String taskId, Event event) { }
                @Override public void onTaskFinalized(String taskId) { }
            });
            liveBus.producer.enqueueEvent(status(TASK_STATE_WORKING, 0));
            assertTrue(firstDeferred.await(5, TimeUnit.SECONDS));
            liveBus.producer.enqueueEvent(status(TASK_STATE_COMPLETED, 0));
            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            failAdmission.get().run();
            assertTrue(successorPushed.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(3L), pushed);
        }
    }

    @Test
    void firstFullTaskWithUnsupportedNestedAuthorityIsRejectedBeforeReservation() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task malformed = Task.builder(task(TASK_STATE_WORKING, 50))
                .artifacts(List.of(Artifact.builder().artifactId("user-input")
                        .parts(List.of(new TextPart("user"))).role(Message.Role.ROLE_USER)
                        .startGeneration(25).endGeneration(50).build()))
                .timeline(List.of(new TimelineEntry(50, new TimelineEntry.StatusValue(new TaskStatus(TASK_STATE_WORKING)))))
                .build();
        AtomicBoolean reserved = new AtomicBoolean();
        assertThrows(InvalidParamsError.class, () -> new TaskManager(TASK_ID, CONTEXT_ID, store, null)
                .processCommitted(malformed, (slot, mutation) -> reserved.set(true)));
        assertNull(store.get(TASK_ID));
        assertFalse(reserved.get());
    }

    @Test
    void initialFullTaskNormalizesLegacyUnstampedArtifactOnLiveBus() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Artifact legacyArtifact = new Artifact("legacy-output", null, null,
                List.of(new TextPart("result")), null, null);
        Task initial = Task.builder()
                .id(TASK_ID)
                .contextId(CONTEXT_ID)
                .status(new TaskStatus(TASK_STATE_COMPLETED))
                .history(List.of())
                .artifacts(List.of(legacyArtifact))
                .build();
        AtomicReference<CommittedTaskBatch> admitted = new AtomicReference<>();
        CountDownLatch admission = new CountDownLatch(1);

        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> true, null)) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override public void beforePushNotification(CommittedTaskBatch batch) {
                    admitted.set(batch);
                    admission.countDown();
                }
                @Override public void onEventProcessed(String taskId, Event event) { }
                @Override public void onTaskFinalized(String taskId) { }
            });
            liveBus.producer.enqueueEvent(initial);

            assertTrue(admission.await(5, TimeUnit.SECONDS));
            assertTrue(observer.completed.await(5, TimeUnit.SECONDS));
            Artifact stored = store.get(TASK_ID).artifacts().get(0);
            assertEquals(1, stored.startGeneration());
            assertEquals(1, stored.endGeneration());
            assertEquals(Message.Role.ROLE_AGENT, stored.role());
            assertEquals(List.of(stored), admitted.get().snapshot().task().artifacts());
            Task delivered = assertInstanceOf(Task.class, observer.events.get(0));
            assertEquals(List.of(stored), delivered.artifacts());
        }
    }

    @Test
    void initialFullTaskRejectsDuplicateGenerationOneTimelineEntries() throws Exception {
        Task duplicateTimeline = Task.builder(task(TASK_STATE_WORKING, 1))
                .timeline(List.of(
                        new TimelineEntry(1, new TimelineEntry.StatusValue(new TaskStatus(TASK_STATE_WORKING))),
                        new TimelineEntry(1, new TimelineEntry.StatusValue(new TaskStatus(TASK_STATE_WORKING)))))
                .build();

        assertRejectedInitialTaskOnLiveBus(duplicateTimeline);
    }

    @Test
    void initialFullTaskRejectsExplicitUnspecifiedArtifactOrigin() throws Exception {
        Artifact unspecifiedOrigin = Artifact.builder()
                .artifactId("unspecified-origin")
                .parts(List.of(new TextPart("result")))
                .role(Message.Role.ROLE_UNSPECIFIED)
                .startGeneration(1)
                .endGeneration(1)
                .build();
        Task initial = Task.builder(task(TASK_STATE_WORKING, 1))
                .artifacts(List.of(unspecifiedOrigin))
                .build();

        assertRejectedInitialTaskOnLiveBus(initial);
    }

    @Test
    void cancellationAggregatorReturnsLocallyFinalUnspecifiedCompatibilityResult() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        ExecutorService consumerExecutor = Executors.newCachedThreadPool();
        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> true, null)) {
            ResultAggregator aggregator = new ResultAggregator(
                    new TaskManager(TASK_ID, CONTEXT_ID, store, null), null,
                    consumerExecutor, consumerExecutor, TimeUnit.SECONDS.toNanos(2));
            CompletableFuture<ResultAggregator.EventTypeAndInterrupt> cancellationResult =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return aggregator.consumeUntilFinalOrAuthRequired(
                                    new EventConsumer(liveBus.observer, consumerExecutor), 2, TimeUnit.SECONDS);
                        } catch (A2AError failure) {
                            throw new CompletionException(failure);
                        }
                    }, consumerExecutor);
            liveBus.producer.enqueueEvent(status(TASK_STATE_UNSPECIFIED, 0));

            ResultAggregator.EventTypeAndInterrupt result = cancellationResult.get(5, TimeUnit.SECONDS);
            Task task = assertInstanceOf(Task.class, result.eventType());
            assertEquals(TASK_ID, task.id());
            assertEquals(TASK_STATE_UNSPECIFIED, task.status().state());
            assertNull(result.committedSnapshot(),
                    "the provenance-free compatibility result remains outside canonical metadata");
        } finally {
            consumerExecutor.shutdownNow();
        }
    }

    @Test
    void failedExecutorMutationDoesNotRecreateTaskDeletedAfterObservation() throws Exception {
        DeleteBeforeMutationTaskStore store = new DeleteBeforeMutationTaskStore();
        store.save(task(TASK_STATE_WORKING, 4), false);
        store.deleteBeforeNextMutation();
        TaskManager manager = new TaskManager(TASK_ID, null, store, null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<TaskManager.CommittedProcessingResult> result = CompletableFuture.supplyAsync(() -> {
                try {
                    return manager.processCommitted(new InternalError("executor failed"), TaskCommitSlotCallback.NONE);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            TaskManager.CommittedProcessingResult outcome = result.get(5, TimeUnit.SECONDS);
            assertNull(outcome.mutation());
            assertNull(store.get(TASK_ID), "a stale failure proposal must not recreate a deleted task");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void firstAtomicUnspecifiedDeltaClosesLocalQueueUsingItsCommittedResult() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        CountDownLatch finalized = new CountDownLatch(1);
        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> true, null)) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override public void onEventProcessed(String taskId, Event event) { }
                @Override public void onTaskFinalized(String taskId) { finalized.countDown(); }
            });
            liveBus.producer.enqueueEvent(status(TASK_STATE_UNSPECIFIED, 0));
            assertTrue(observer.completed.await(5, TimeUnit.SECONDS),
                    "the interim atomic result must close consumer final accounting consistently");
            assertTrue(finalized.await(5, TimeUnit.SECONDS));
            assertEquals(TASK_STATE_UNSPECIFIED, store.get(TASK_ID).status().state());
            assertTrue(observer.snapshots.isEmpty(),
                    "the provenance-free compatibility event must not be labeled a canonical snapshot batch");
            assertFalse(store.isTaskActive(TASK_ID));
        }
    }

    @Test
    void legacyUnspecifiedRemainsProgressAndLegacyArtifactDoesNotRefinalizeCallback() throws Exception {
        LegacyOnlyTaskStore legacy = new LegacyOnlyTaskStore();
        legacy.save(task(TASK_STATE_WORKING, 7), false);
        List<Event> replications = new CopyOnWriteArrayList<>();
        AtomicReference<Integer> finalCallbacks = new AtomicReference<>(0);

        try (LiveBus liveBus = new LiveBus(legacy, legacy, (event, snapshot) -> true,
                item -> replications.add(item.getEvent()))) {
            RecordingSubscriber observer = liveBus.subscribeObserver(legacy, 2);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void onEventProcessed(String taskId, Event event) {
                }

                @Override
                public void onTaskFinalized(String taskId) {
                    finalCallbacks.updateAndGet(count -> count + 1);
                }
            });
            TaskStatusUpdateEvent unspecified = status(TASK_STATE_UNSPECIFIED, 0);
            TaskStatusUpdateEvent resumed = status(TASK_STATE_WORKING, 0);
            liveBus.producer.enqueueEvent(unspecified);
            liveBus.producer.enqueueEvent(resumed);

            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(TASK_STATE_UNSPECIFIED, TASK_STATE_WORKING), observer.events.stream()
                    .map(event -> ((TaskStatusUpdateEvent) event).status().state()).toList());
            assertEquals(List.of(unspecified, resumed), replications);
            assertEquals(0, finalCallbacks.get());
        }

        LegacyOnlyTaskStore terminalLegacy = new LegacyOnlyTaskStore();
        terminalLegacy.save(task(TASK_STATE_COMPLETED, 7), false);
        AtomicReference<Integer> terminalCallbacks = new AtomicReference<>(0);
        try (LiveBus liveBus = new LiveBus(terminalLegacy, terminalLegacy, (event, snapshot) -> true, null)) {
            RecordingSubscriber observer = liveBus.subscribeObserver(terminalLegacy);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override
                public void onEventProcessed(String taskId, Event event) {
                }

                @Override
                public void onTaskFinalized(String taskId) {
                    terminalCallbacks.updateAndGet(count -> count + 1);
                }
            });
            org.a2aproject.sdk.spec.TaskArtifactUpdateEvent artifact =
                    org.a2aproject.sdk.spec.TaskArtifactUpdateEvent.builder().taskId(TASK_ID)
                            .contextId(CONTEXT_ID).artifact(Artifact.builder().artifactId("late")
                                    .parts(List.of(new TextPart("late artifact"))).build()).append(false).build();
            liveBus.producer.enqueueEvent(artifact);
            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS));
            assertEquals(0, terminalCallbacks.get());
        }
    }

    @Test
    void uncommittedReservationCannotReachPublicationSpies() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 1), false);
        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        OrderedTaskPublisher publisher = new OrderedTaskPublisher();
        List<Long> subscriberGenerations = new ArrayList<>();
        List<Long> pushGenerations = new ArrayList<>();
        List<Long> replicationGenerations = new ArrayList<>();
        CountDownLatch reservationCreated = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        AtomicReference<TaskCommitSlot> slot = new AtomicReference<>();
        AtomicReference<CommittedTaskBatch> batch = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            CompletableFuture<Void> commit = CompletableFuture.runAsync(() -> mutate(authority, TASK_STATE_COMPLETED,
                    (reserved, mutation) -> {
                        publisher.reserve(reserved);
                        slot.set(reserved);
                        batch.set(batch(mutation));
                        reservationCreated.countDown();
                        await(allowCommit);
                    }), executor);

            assertTrue(reservationCreated.await(5, TimeUnit.SECONDS), "store should reserve before replacement");
            assertTrue(!slot.get().isPublishable(), "reservation must stay private until replacement completes");
            assertThrows(IllegalStateException.class, () -> publisher.publish(slot.get(), batch.get(), committed ->
                    deliver(committed, subscriberGenerations, pushGenerations, replicationGenerations)));
            assertTrue(subscriberGenerations.isEmpty());
            assertTrue(pushGenerations.isEmpty());
            assertTrue(replicationGenerations.isEmpty());

            allowCommit.countDown();
            commit.get(5, TimeUnit.SECONDS);
            publisher.publish(slot.get(), batch.get(), committed ->
                    deliver(committed, subscriberGenerations, pushGenerations, replicationGenerations))
                    .toCompletableFuture().join();
            assertEquals(List.of(2L), subscriberGenerations);
            assertEquals(subscriberGenerations, pushGenerations);
            assertEquals(subscriberGenerations, replicationGenerations);
        } finally {
            allowCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void publicationWaitsForCommitAndPreservesReservationOrder() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 1), false);
        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        OrderedTaskPublisher publisher = new OrderedTaskPublisher();
        List<Long> subscriberGenerations = new ArrayList<>();
        List<Long> pushGenerations = new ArrayList<>();
        List<Long> replicationGenerations = new ArrayList<>();
        CountDownLatch firstCommitted = new CountDownLatch(1);
        CountDownLatch allowFirstPublication = new CountDownLatch(1);
        CountDownLatch secondPublicationQueued = new CountDownLatch(1);
        AtomicReference<CommittedTaskBatch> firstBatch = new AtomicReference<>();
        AtomicReference<TaskCommitSlot> firstSlot = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<Void> first = CompletableFuture.runAsync(() -> {
                CommittedMutation committed = mutate(authority, TASK_STATE_WORKING, (slot, mutation) -> {
                    publisher.reserve(slot);
                    firstSlot.set(slot);
                    firstBatch.set(batch(mutation));
                });
                assertNotNull(committed);
                firstCommitted.countDown();
                await(allowFirstPublication);
                publisher.publish(firstSlot.get(), firstBatch.get(), batch -> deliver(batch,
                        subscriberGenerations, pushGenerations, replicationGenerations)).toCompletableFuture().join();
            }, executor);

            assertTrue(firstCommitted.await(5, TimeUnit.SECONDS), "first mutation should commit");
            assertTrue(subscriberGenerations.isEmpty());
            assertTrue(pushGenerations.isEmpty());
            assertTrue(replicationGenerations.isEmpty());

            CompletableFuture<Void> second = CompletableFuture.runAsync(() -> {
                AtomicReference<TaskCommitSlot> slot = new AtomicReference<>();
                AtomicReference<CommittedTaskBatch> committedBatch = new AtomicReference<>();
                CommittedMutation committed = mutate(authority, TASK_STATE_COMPLETED, (reserved, mutation) -> {
                    publisher.reserve(reserved);
                    slot.set(reserved);
                    committedBatch.set(batch(mutation));
                });
                assertNotNull(committed);
                CompletableFuture<Void> delivery = publisher.publish(slot.get(), committedBatch.get(), batch ->
                        deliver(batch, subscriberGenerations, pushGenerations, replicationGenerations))
                        .toCompletableFuture();
                secondPublicationQueued.countDown();
                delivery.join();
            }, executor);

            assertTrue(secondPublicationQueued.await(5, TimeUnit.SECONDS), "second batch should be queued for publication");
            assertTrue(subscriberGenerations.isEmpty(), "the second writer must not overtake the first reservation");
            assertTrue(pushGenerations.isEmpty());
            assertTrue(replicationGenerations.isEmpty());

            allowFirstPublication.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertEquals(List.of(2L, 3L), subscriberGenerations);
            assertEquals(subscriberGenerations, pushGenerations);
            assertEquals(subscriberGenerations, replicationGenerations);
        } finally {
            allowFirstPublication.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedReservationCanBeRemovedWithoutBlockingLaterCommit() {
        OrderedTaskPublisher publisher = new OrderedTaskPublisher();
        TaskCommitSlot.Reservation failed = TaskCommitSlot.reserve(TASK_ID, new org.a2aproject.sdk.server.tasks.TaskRevision(1));
        publisher.reserve(failed.slot());
        failed.cancel();
        publisher.discard(failed.slot());

        TaskCommitSlot.Reservation next = TaskCommitSlot.reserve(TASK_ID, new org.a2aproject.sdk.server.tasks.TaskRevision(2));
        publisher.reserve(next.slot());
        next.markCommitted();
        TaskSnapshot snapshot = new TaskSnapshot(task(TASK_STATE_WORKING, 2), new org.a2aproject.sdk.server.tasks.TaskRevision(2));
        TaskStatusUpdateEvent event = status(TASK_STATE_WORKING, 2);
        CommittedTaskBatch batch = new CommittedTaskBatch(snapshot, List.of(event));
        List<Long> delivered = new ArrayList<>();

        publisher.publish(next.slot(), batch, committedBatch -> delivered.addAll(committedBatch.generationPositions()))
                .toCompletableFuture().join();

        assertEquals(List.of(2L), delivered);
    }

    @Test
    void batchRetainsOrderedEventGenerationsAndOneFinalSnapshot() {
        TaskSnapshot finalSnapshot = new TaskSnapshot(task(TASK_STATE_COMPLETED, 3),
                new org.a2aproject.sdk.server.tasks.TaskRevision(3));
        CommittedTaskBatch batch = new CommittedTaskBatch(finalSnapshot,
                List.of(status(TASK_STATE_WORKING, 2), status(TASK_STATE_COMPLETED, 3)));

        assertEquals(finalSnapshot, batch.snapshot());
        assertEquals(List.of(2L, 3L), batch.generationPositions());
        assertEquals(2, batch.events().size());
    }

    @Test
    void taskManagerPlansFromCurrentStoreSnapshotInsteadOfItsCachedTask() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(task(TASK_STATE_WORKING, 1), false);
        TaskManager manager = new TaskManager(TASK_ID, CONTEXT_ID, store, null);
        assertEquals(1, Objects.requireNonNull(manager.getTask()).generation());

        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        mutate(authority, org.a2aproject.sdk.spec.TaskState.TASK_STATE_INPUT_REQUIRED,
                org.a2aproject.sdk.server.tasks.TaskCommitSlotCallback.NONE);
        TaskManager.CommittedProcessingResult result = manager.processCommitted(
                status(TASK_STATE_WORKING, 0), (slot, mutation) -> {
                });

        assertNotNull(result.mutation());
        assertEquals(3, result.mutation().snapshot().task().generation());
        assertEquals(List.of(3L), result.mutation().events().stream()
                .map(event -> ((TaskStatusUpdateEvent) event).generation()).toList());
    }

    @Test
    void typedPlannerRejectionDoesNotReserveOrMutateTheTask() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task terminalTask = task(TASK_STATE_COMPLETED, 7);
        store.save(terminalTask, false);
        TaskManager manager = new TaskManager(TASK_ID, CONTEXT_ID, store, null);
        AtomicReference<TaskCommitSlot> reserved = new AtomicReference<>();

        assertThrows(InvalidParamsError.class, () -> manager.processCommitted(
                status(TASK_STATE_WORKING, 0), (slot, mutation) -> reserved.set(slot)));

        assertEquals(terminalTask, Objects.requireNonNull(store.readSnapshot(TASK_ID)).task());
        assertTrue(reserved.get() == null, "planner rejection must happen before reservation");
    }

    @Test
    void unspecifiedTaskStateUsesTheAcceptedTerminalAssessment() {
        TaskSnapshot snapshot = new TaskSnapshot(
                task(org.a2aproject.sdk.spec.TaskState.TASK_STATE_UNSPECIFIED, 4),
                new org.a2aproject.sdk.server.tasks.TaskRevision(4));
        CommittedTaskBatch batch = new CommittedTaskBatch(snapshot,
                List.of(status(org.a2aproject.sdk.spec.TaskState.TASK_STATE_UNSPECIFIED, 4)));

        assertTrue(batch.isFinal());
    }

    @Test
    void terminalCallbacksAndStreamClosureFollowEveryBatchEvent() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        MainEventBus eventBus = new MainEventBus();
        InMemoryQueueManager queueManager = new InMemoryQueueManager(store, eventBus);
        List<String> order = new ArrayList<>();
        TaskStreamLifecycleHook lifecycleHook = new TaskStreamLifecycleHook() {
            @Override
            public void onSubscribe(String taskId, StreamCloseHandle handle) {
            }

            @Override
            public void onUnsubscribe(String taskId, StreamCloseHandle handle) {
            }

            @Override
            public void onEvent(String taskId, org.a2aproject.sdk.spec.Event event, StreamCloseHandle handle) {
                order.add("lifecycle-" + generation((StreamingEventKind) event));
                if (generation((StreamingEventKind) event) == 3) {
                    handle.closeStreams();
                }
            }
        };
        EventQueue.MainQueue mainQueue = (EventQueue.MainQueue) EventQueue.builder(eventBus)
                .taskId(TASK_ID)
                .taskStateProvider(store)
                .streamLifecycleHook(lifecycleHook)
                .build();
        EventQueue child = mainQueue.tap();
        PushNotificationSender sender = (event, snapshot) -> {
            order.add("push-" + generation(event));
            return true;
        };
        MainEventBusProcessor processor = new MainEventBusProcessor(eventBus, store, sender, queueManager);
        processor.setPushNotificationExecutor(Runnable::run);
        processor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void beforePushNotification(CommittedTaskBatch batch) {
                assertEquals(List.of(2L, 3L), batch.generationPositions());
                assertEquals(3, batch.snapshot().task().generation());
                order.add("admission");
            }

            @Override
            public void onEventProcessed(String taskId, org.a2aproject.sdk.spec.Event event) {
                order.add("processed-" + generation((StreamingEventKind) event));
            }

            @Override
            public void onTaskFinalized(String taskId) {
                assertEquals(2, child.size(), "all committed events must be queued before terminal callback");
                order.add("finalized");
            }
        });

        CommittedTaskBatch batch = new CommittedTaskBatch(
                new TaskSnapshot(task(TASK_STATE_COMPLETED, 3), new org.a2aproject.sdk.server.tasks.TaskRevision(5)),
                List.of(status(TASK_STATE_WORKING, 2), status(TASK_STATE_COMPLETED, 3)));
        processor.deliverCommittedBatch(mainQueue, batch, false);

        assertEquals(List.of("admission", "push-2", "push-3", "lifecycle-2", "lifecycle-3",
                "processed-2", "processed-3", "finalized"), order);
        assertEquals(2, child.size());
        child.close(true);
        mainQueue.close(true);
    }

    private static final class LiveBus implements AutoCloseable {

        private final EventQueue.MainQueue mainQueue;
        private final EventQueue producer;
        private final EventQueue observer;
        private final ExecutorService consumerExecutor = Executors.newCachedThreadPool();
        private final MainEventBusProcessor processor;

        private LiveBus(InMemoryTaskStore store, PushNotificationSender pushSender, EventEnqueueHook replicationHook) {
            this(store, store, pushSender, replicationHook);
        }

        private LiveBus(TaskStore store, TaskStateProvider stateProvider,
                PushNotificationSender pushSender, EventEnqueueHook replicationHook) {
            MainEventBus eventBus = new MainEventBus();
            EventQueue.EventQueueBuilder builder = EventQueue.builder(eventBus)
                    .taskId(TASK_ID)
                    .taskStateProvider(stateProvider);
            if (replicationHook != null) {
                builder.hook(replicationHook);
            }
            mainQueue = (EventQueue.MainQueue) builder.build();
            producer = mainQueue.tap();
            observer = mainQueue.tap();
            processor = new MainEventBusProcessor(eventBus, store, pushSender,
                    new InMemoryQueueManager(stateProvider, eventBus));
            processor.setPushNotificationExecutor(Runnable::run);
            processor.start();
        }

        private RecordingSubscriber subscribeObserver(TaskStore store) {
            return subscribeObserver(store, 1);
        }

        private RecordingSubscriber subscribeObserver(TaskStore store, int expectedEvents) {
            RecordingSubscriber subscriber = new RecordingSubscriber(expectedEvents);
            ResultAggregator aggregator = new ResultAggregator(
                    new TaskManager(TASK_ID, CONTEXT_ID, store, null), null,
                    consumerExecutor, consumerExecutor, TimeUnit.SECONDS.toNanos(2));
            aggregator.consumeAndEmit(new EventConsumer(observer, consumerExecutor)).subscribe(subscriber);
            return subscriber;
        }

        @Override
        public void close() {
            processor.stop();
            mainQueue.close(true);
            consumerExecutor.shutdownNow();
            try {
                assertTrue(consumerExecutor.awaitTermination(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while closing live-bus fixture", e);
            }
        }
    }

    private static final class GateableTaskStore extends InMemoryTaskStore {
        private volatile CountDownLatch commitGateEntered;
        private volatile CountDownLatch releaseCommitGate;

        private void armCommitGate() {
            commitGateEntered = new CountDownLatch(1);
            releaseCommitGate = new CountDownLatch(1);
        }

        private void releaseCommitGate() {
            CountDownLatch release = releaseCommitGate;
            if (release != null) {
                release.countDown();
            }
        }

        @Override
        public CommittedMutation mutate(String taskId, TaskMutation mutation, TaskCommitSlotCallback slotCallback) {
            CountDownLatch entered = commitGateEntered;
            CountDownLatch release = releaseCommitGate;
            if (entered == null || release == null) {
                return super.mutate(taskId, mutation, slotCallback);
            }
            try {
                return super.mutate(taskId, mutation, (slot, committed) -> {
                    slotCallback.reserve(slot, committed);
                    entered.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to release the test commit gate");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while holding test commit gate", e);
                    }
                });
            } finally {
                commitGateEntered = null;
                releaseCommitGate = null;
            }
        }
    }

    private static final class MutationGateTaskStore extends InMemoryTaskStore {
        private final AtomicBoolean armed = new AtomicBoolean();
        private volatile CountDownLatch mutationEntered = new CountDownLatch(0);
        private volatile CountDownLatch releaseMutation = new CountDownLatch(0);

        private void armNextMutationGate() {
            mutationEntered = new CountDownLatch(1);
            releaseMutation = new CountDownLatch(1);
            armed.set(true);
        }

        @Override
        public CommittedMutation mutate(String taskId, TaskMutation mutation, TaskCommitSlotCallback slotCallback) {
            if (armed.compareAndSet(true, false)) {
                mutationEntered.countDown();
                try {
                    if (!releaseMutation.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting at the test mutation gate");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted at the test mutation gate", e);
                }
            }
            return super.mutate(taskId, mutation, slotCallback);
        }
    }

    private static final class DeleteBeforeMutationTaskStore extends InMemoryTaskStore {
        private final AtomicBoolean deleteOnNextMutation = new AtomicBoolean();

        private void deleteBeforeNextMutation() {
            deleteOnNextMutation.set(true);
        }

        @Override
        public CommittedMutation mutate(String taskId, TaskMutation mutation, TaskCommitSlotCallback slotCallback) {
            if (deleteOnNextMutation.compareAndSet(true, false)) {
                delete(taskId);
            }
            return super.mutate(taskId, mutation, slotCallback);
        }
    }

    private static final class ExpandingTaskStore extends InMemoryTaskStore {
        private final AtomicBoolean expandNextMutation = new AtomicBoolean();

        private void expandNextStatusMutation() {
            expandNextMutation.set(true);
        }

        @Override
        public CommittedMutation mutate(String taskId, TaskMutation mutation, TaskCommitSlotCallback slotCallback) {
            if (!expandNextMutation.compareAndSet(true, false)) {
                return super.mutate(taskId, mutation, slotCallback);
            }
            TaskMutation expanded = current -> {
                MutationPlan original = mutation.plan(current);
                if (current == null) {
                    return original;
                }
                long firstGeneration = current.task().generation() + 1;
                long finalGeneration = firstGeneration + 1;
                TaskStatusUpdateEvent progress = TaskStatusUpdateEvent.builder()
                        .taskId(taskId).contextId(current.task().contextId())
                        .status(new TaskStatus(TASK_STATE_WORKING)).generation(firstGeneration).build();
                TaskStatusUpdateEvent terminal = TaskStatusUpdateEvent.builder()
                        .taskId(taskId).contextId(current.task().contextId())
                        .status(new TaskStatus(TASK_STATE_COMPLETED)).generation(finalGeneration).build();
                List<TimelineEntry> timeline = current.task().timeline() == null
                        ? new ArrayList<>() : new ArrayList<>(current.task().timeline());
                timeline.add(new TimelineEntry(firstGeneration,
                        new TimelineEntry.StatusValue(progress.status())));
                timeline.add(new TimelineEntry(finalGeneration,
                        new TimelineEntry.StatusValue(terminal.status())));
                Task finalTask = Task.builder(current.task()).status(terminal.status()).timeline(timeline)
                        .generation(finalGeneration).build();
                return new MutationPlan(finalTask, List.of(progress, terminal), true);
            };
            return super.mutate(taskId, expanded, slotCallback);
        }
    }

    private static final class FailingDistributionMainQueue extends EventQueue.MainQueue {
        private final AtomicBoolean failNextDistribution = new AtomicBoolean(true);

        private FailingDistributionMainQueue(InMemoryTaskStore store, MainEventBus eventBus) {
            super(1, null, TASK_ID, List.of(), store, eventBus, null);
        }

        @Override
        void distributeToChildren(EventQueueItem item) {
            if (failNextDistribution.compareAndSet(true, false)) {
                throw new IllegalStateException("controlled post-commit child delivery failure");
            }
            super.distributeToChildren(item);
        }
    }

    private static final class LegacyOnlyTaskStore implements TaskStore, TaskStateProvider {
        private final InMemoryTaskStore delegate = new InMemoryTaskStore();

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
        public ListTasksResult list(ListTasksParams params, @Nullable ServerCallContext context) {
            return delegate.list(params, context);
        }

        @Override
        public boolean isTaskActive(String taskId) {
            Task task = delegate.get(taskId);
            return task != null && (task.status() == null || task.status().state() == null
                    || !task.status().state().isFinal());
        }

        @Override
        public boolean isTaskFinalized(String taskId) {
            Task task = delegate.get(taskId);
            return task != null && task.status() != null && task.status().state() != null
                    && task.status().state().isFinal();
        }
    }

    private static final class RecordingSubscriber implements Flow.Subscriber<EventQueueItem> {

        private final List<Event> events = new CopyOnWriteArrayList<>();
        private final List<Task> snapshots = new CopyOnWriteArrayList<>();
        private final CountDownLatch eventReceived;
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile Throwable failure;

        private RecordingSubscriber() {
            this(1);
        }

        private RecordingSubscriber(int expectedEvents) {
            eventReceived = new CountDownLatch(expectedEvents);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(EventQueueItem item) {
            events.add(item.getEvent());
            if (item.committedSnapshot() != null) {
                snapshots.add(item.committedSnapshot().task());
            }
            eventReceived.countDown();
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
        }

        @Override
        public void onComplete() {
            completed.countDown();
        }
    }

    private static CommittedMutation mutate(TaskMutationAuthority authority,
            org.a2aproject.sdk.spec.TaskState state,
            org.a2aproject.sdk.server.tasks.TaskCommitSlotCallback callback) {
        TaskMutationAuthority mutationAuthority = authority;
        return mutationAuthority.mutate(TASK_ID, current -> {
            TaskStatusUpdateEvent proposed = TaskStatusUpdateEvent.builder()
                    .taskId(TASK_ID)
                    .contextId(CONTEXT_ID)
                    .status(new TaskStatus(state))
                    .build();
            MutationPlan plan = TaskMutationPlanner.planStatus(current, proposed);
            return plan;
        }, callback);
    }

    private static CommittedTaskBatch batch(CommittedMutation mutation) {
        return new CommittedTaskBatch(mutation.snapshot(), mutation.events());
    }

    private static void deliver(CommittedTaskBatch batch, List<Long> subscribers,
            List<Long> pushes, List<Long> replication) {
        List<Long> positions = batch.generationPositions();
        subscribers.addAll(positions);
        pushes.addAll(positions);
        replication.addAll(positions);
    }

    private static Task task(org.a2aproject.sdk.spec.TaskState state, long generation) {
        return Task.builder()
                .id(TASK_ID)
                .contextId(CONTEXT_ID)
                .status(new TaskStatus(state))
                .generation(generation)
                .build();
    }

    private static void assertRejectedInitialTaskOnLiveBus(Task rejected) throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        List<Event> pushes = new CopyOnWriteArrayList<>();
        List<Event> replications = new CopyOnWriteArrayList<>();
        TaskStatusUpdateEvent barrier = status(TASK_STATE_WORKING, 0);
        CountDownLatch barrierProcessed = new CountDownLatch(1);
        try (LiveBus liveBus = new LiveBus(store, (event, snapshot) -> {
            pushes.add(event);
            return true;
        }, item -> replications.add(item.getEvent()))) {
            RecordingSubscriber observer = liveBus.subscribeObserver(store, 1);
            liveBus.processor.setCallback(new MainEventBusProcessorCallback() {
                @Override public void onEventProcessed(String taskId, Event event) {
                    if (event instanceof TaskStatusUpdateEvent update
                            && update.status().state() == TASK_STATE_WORKING) {
                        barrierProcessed.countDown();
                    }
                }
                @Override public void onTaskFinalized(String taskId) { }
            });
            liveBus.producer.enqueueEvent(rejected);
            liveBus.producer.enqueueEvent(barrier);

            assertTrue(barrierProcessed.await(5, TimeUnit.SECONDS));
            assertTrue(observer.eventReceived.await(5, TimeUnit.SECONDS),
                    "the subscriber must record the valid follow-up event before assertions");
            assertNull(observer.failure, "the observer must not fail while consuming the valid barrier");
            assertBarrierOnly(observer.events,
                    "the rejected initial Task must not be broadcast to child queues");
            assertBarrierOnly(pushes, "the rejected initial Task must not reach push delivery");
            assertBarrierOnly(replications, "the rejected initial Task must not reach the replication hook");
            assertEquals(0, store.get(TASK_ID).generation(),
                    "only the legitimate follow-up status should create the Task");
        }
    }

    private static void assertBarrierOnly(List<Event> events, String message) {
        assertEquals(1, events.size(), message);
        TaskStatusUpdateEvent observed = assertInstanceOf(TaskStatusUpdateEvent.class, events.get(0), message);
        assertEquals(0, observed.generation(), message);
        assertEquals(TASK_STATE_WORKING, observed.status().state(), message);
    }

    private static TaskStatusUpdateEvent status(org.a2aproject.sdk.spec.TaskState state, long generation) {
        return TaskStatusUpdateEvent.builder()
                .taskId(TASK_ID)
                .contextId(CONTEXT_ID)
                .status(new TaskStatus(state))
                .generation(generation)
                .build();
    }

    private static long generation(StreamingEventKind event) {
        if (event instanceof Task task) {
            return task.generation();
        }
        if (event instanceof TaskStatusUpdateEvent status) {
            return status.generation();
        }
        throw new IllegalArgumentException("Unexpected event type " + event.getClass().getName());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }
}
