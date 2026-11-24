package org.a2aproject.sdk.extras.queuemanager.replicated.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.a2aproject.sdk.extras.common.events.TaskFinalizedEvent;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.server.events.EventQueue;
import org.a2aproject.sdk.server.events.EventQueueClosedException;
import org.a2aproject.sdk.server.events.EventQueueItem;
import org.a2aproject.sdk.server.events.EventQueueUtil;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.MainEventBusProcessorCallback;
import org.a2aproject.sdk.server.events.QueueClosedEvent;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskMutation;
import org.a2aproject.sdk.server.tasks.TaskRevision;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Committed replication: envelope provenance, ordering, dedup, terminal forwarding and no second mutation. */
class CommittedReplicationTest {

    private static final long EPOCH = 7L;
    private static final String TASK = "committed-task";

    private MainEventBus mainEventBus;
    private MainEventBusProcessor processor;
    private RecordingStrategy strategy;
    private ReplicatedQueueManager manager;
    private CountingStore store;
    private final List<String> webhookEvents = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        mainEventBus = new MainEventBus();
        store = new CountingStore();
        strategy = new RecordingStrategy();
        manager = new ReplicatedQueueManager(strategy, new ActiveProvider(), mainEventBus,
                new org.a2aproject.sdk.server.events.DefaultTaskStreamLifecycleHook(), EPOCH);
        PushNotificationSender recording = new PushNotificationSender() {
            @Override
            public boolean sendNotification(StreamingEventKind event, Task task) {
                webhookEvents.add(taskIdOf(event));
                return true;
            }
        };
        processor = new MainEventBusProcessor(mainEventBus, store, recording, manager);
        processor.setPushNotificationExecutor(Runnable::run);
        EventQueueUtil.start(processor);
    }

    @AfterEach
    void tearDown() {
        processor.setCallback(null);
        EventQueueUtil.stop(processor);
    }

    private static String taskIdOf(StreamingEventKind event) {
        if (event instanceof TaskStatusUpdateEvent update) {
            return update.taskId();
        }
        if (event instanceof Task task) {
            return task.id();
        }
        return event.getClass().getSimpleName();
    }

    private static TaskStatusUpdateEvent status(String taskId, TaskState state, long generation) {
        return TaskStatusUpdateEvent.builder().taskId(taskId).contextId("ctx")
                .status(new TaskStatus(state)).generation(generation).build();
    }

    private static CommittedTaskBatch batch(String taskId, long revision, TaskState finalState, long... generations) {
        List<StreamingEventKind> events = new ArrayList<>();
        for (long generation : generations) {
            TaskState state = generation == generations[generations.length - 1] ? finalState : TaskState.TASK_STATE_WORKING;
            events.add(status(taskId, state, generation));
        }
        long last = generations[generations.length - 1];
        Task task = Task.builder().id(taskId).contextId("ctx").status(new TaskStatus(finalState))
                .generation(last).build();
        return new CommittedTaskBatch(new TaskSnapshot(task, new TaskRevision(revision)), events);
    }

    private ReplicatedEventQueueItem remote(CommittedTaskBatch batch) {
        return ReplicatedEventQueueItem.committed(batch, EPOCH, "other-node");
    }

    /** Collects whatever arrives within a short quiet period (used to assert that nothing more is delivered). */
    private static List<Long> drainQuiet(EventQueue queue) throws Exception {
        List<Long> seen = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(600);
        while (System.nanoTime() < deadline) {
            EventQueueItem item;
            try {
                item = queue.dequeueEventItem(100);
            } catch (EventQueueClosedException closed) {
                break;
            }
            if (item != null && item.getEvent() instanceof TaskStatusUpdateEvent update) {
                seen.add(update.generation());
            }
        }
        return seen;
    }

    private static List<Long> drainGenerations(EventQueue queue, int expected) throws Exception {
        List<Long> seen = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (seen.size() < expected && System.nanoTime() < deadline) {
            EventQueueItem item;
            try {
                item = queue.dequeueEventItem(100);
            } catch (EventQueueClosedException closed) {
                break;
            }
            if (item != null && item.getEvent() instanceof TaskStatusUpdateEvent update) {
                seen.add(update.generation());
            }
        }
        return seen;
    }

    @Test
    void committedEnvelopeSerializesWithOpaqueBatchAndRoundTripsIdentity() throws Exception {
        CommittedTaskBatch original = batch(TASK, 11, TaskState.TASK_STATE_WORKING, 3, 4, 5);
        ReplicatedEventQueueItem sent = ReplicatedEventQueueItem.committed(original, EPOCH, "node-a");

        ReplicatedEventQueueItem received = JsonUtil.fromJson(JsonUtil.toJson(sent), ReplicatedEventQueueItem.class);

        assertTrue(received.isCommittedBatch());
        assertEquals(EPOCH, received.getWriterEpoch());
        assertEquals("node-a", received.getOriginId());
        CommittedTaskBatch rebuilt = received.committedBatch();
        assertNotNull(rebuilt);
        assertEquals(original.identity(), rebuilt.identity());
        assertEquals(List.of(3L, 4L, 5L), rebuilt.generationPositions());
        assertEquals(11, rebuilt.snapshot().revision().value());
    }

    @Test
    void nonStatusBatchEventsSurviveTheEnvelopeRoundTripWithTheirTypes() throws Exception {
        Task snapshot = Task.builder().id(TASK).contextId("ctx").status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .generation(5).build();
        List<StreamingEventKind> events = List.of(
                TaskArtifactUpdateEvent.builder().taskId(TASK).contextId("ctx")
                        .artifact(Artifact.builder().artifactId("a1").parts(List.of(new org.a2aproject.sdk.spec.TextPart("x")))
                                .build())
                        .generation(3).build(),
                new TaskMessageUpdateEvent(TASK, "ctx", Message.builder().role(Message.Role.ROLE_AGENT)
                        .parts(List.of(new org.a2aproject.sdk.spec.TextPart("hi"))).build(), null, 4),
                status(TASK, TaskState.TASK_STATE_WORKING, 5));
        CommittedTaskBatch original = new CommittedTaskBatch(new TaskSnapshot(snapshot, new TaskRevision(9)), events);

        ReplicatedEventQueueItem received = JsonUtil.fromJson(
                JsonUtil.toJson(ReplicatedEventQueueItem.committed(original, EPOCH, "node-a")),
                ReplicatedEventQueueItem.class);
        CommittedTaskBatch rebuilt = received.committedBatch();

        assertNotNull(rebuilt);
        // Gson normalizes absent part metadata to {}, so compare the canonical wire form, not record equality.
        assertEquals(JsonUtil.toJson(ReplicatedEventQueueItem.committed(original, EPOCH, "node-a")),
                JsonUtil.toJson(ReplicatedEventQueueItem.committed(rebuilt, EPOCH, "node-a")));
        assertEquals(original.identity(), rebuilt.identity());
        assertEquals(List.of(3L, 4L, 5L), rebuilt.generationPositions());
        assertInstanceOf(TaskArtifactUpdateEvent.class, rebuilt.events().get(0));
        assertInstanceOf(TaskMessageUpdateEvent.class, rebuilt.events().get(1));
        assertInstanceOf(TaskStatusUpdateEvent.class, rebuilt.events().get(2));
    }

    @Test
    void terminalBatchArrivingAfterItsPillIsDroppedAsStale() throws Exception {
        manager.onReplicatedEvent(ReplicatedEventQueueItem.stampedClosed("late-final", EPOCH, "other-node", 2L));
        EventQueue afterPill = manager.get("late-final");

        manager.onReplicatedEvent(remote(batch("late-final", 2, TaskState.TASK_STATE_COMPLETED, 2)));

        assertSame(afterPill, manager.get("late-final"), "the late terminal batch must not create or replace a queue");
        assertEquals(0, store.mutationsFor("late-final"));
        assertEquals(0, store.savesFor("late-final"));
    }

    @Test
    void sequencerDropsATerminalBatchBehindAnImmediatelyRunPillWithoutDeliveringIt() {
        CommittedBatchSequencer sequencer = new CommittedBatchSequencer();
        List<String> events = new ArrayList<>();
        sequencer.runAfterGeneration("p", 2, () -> events.add("pill"));
        assertEquals(List.of("pill"), events);

        assertEquals(CommittedBatchSequencer.Outcome.STALE,
                sequencer.offer(batch("p", 2, TaskState.TASK_STATE_COMPLETED, 2), () -> events.add("late-batch")));
        assertEquals(List.of("pill"), events, "the late terminal batch is never delivered");
    }

    @Test
    void aFailedDeliveryFollowedByReadyBatchesStillDeliversThemInOrder() {
        CommittedBatchSequencer sequencer = new CommittedBatchSequencer();
        List<Long> delivered = new ArrayList<>();
        sequencer.offer(batch("f", 1, TaskState.TASK_STATE_WORKING, 1), () -> delivered.add(1L));
        sequencer.offer(batch("f", 3, TaskState.TASK_STATE_WORKING, 3), () -> delivered.add(3L));
        sequencer.offer(batch("f", 4, TaskState.TASK_STATE_WORKING, 4), () -> delivered.add(4L));

        assertThrows(IllegalStateException.class, () -> sequencer.offer(
                batch("f", 2, TaskState.TASK_STATE_WORKING, 2), () -> {
                    delivered.add(2L);
                    throw new IllegalStateException("first fails");
                }));

        assertEquals(List.of(1L, 2L, 3L, 4L), delivered);
        assertEquals(0, sequencer.pendingCount("f"));
    }

    @Test
    void aFailedDeliveryDoesNotWedgeLaterBufferedBatchesOrStrandTheDeferredPill() {
        CommittedBatchSequencer sequencer = new CommittedBatchSequencer();
        List<Long> delivered = new ArrayList<>();
        List<String> events = new ArrayList<>();
        assertEquals(CommittedBatchSequencer.Outcome.DELIVERED,
                sequencer.offer(batch("w", 1, TaskState.TASK_STATE_WORKING, 1), () -> delivered.add(1L)));
        assertEquals(CommittedBatchSequencer.Outcome.BUFFERED,
                sequencer.offer(batch("w", 3, TaskState.TASK_STATE_COMPLETED, 3), () -> {
                    delivered.add(3L);
                    throw new IllegalStateException("terminal delivery fails");
                }));
        sequencer.runAfterGeneration("w", 3, () -> events.add("pill"));
        assertEquals(1, sequencer.pendingCount("w"));

        assertThrows(IllegalStateException.class,
                () -> sequencer.offer(batch("w", 2, TaskState.TASK_STATE_WORKING, 2), () -> delivered.add(2L)));

        assertEquals(List.of(1L, 2L, 3L), delivered, "later ready batches are still delivered in order");
        assertEquals(List.of("pill"), events, "the ordered pill still runs after a failing terminal delivery");
        assertEquals(0, sequencer.pendingCount("w"));
    }

    @Test
    void legacyRecordHasNoEpochAndNoBatch() throws Exception {
        ReplicatedEventQueueItem legacy = JsonUtil.fromJson(JsonUtil.toJson(
                new ReplicatedEventQueueItem(TASK, status(TASK, TaskState.TASK_STATE_WORKING, 0))),
                ReplicatedEventQueueItem.class);
        assertNull(legacy.getWriterEpoch());
        assertFalse(legacy.isCommittedBatch());
        assertNull(legacy.committedBatch());
    }

    @Test
    void incompleteOrNonConsecutiveEnvelopesAreRejectedByValidation() throws Exception {
        String json = JsonUtil.toJson(remote(batch(TASK, 2, TaskState.TASK_STATE_WORKING, 1, 2)));
        // Drop the snapshot's revision: the envelope is no longer complete.
        ReplicatedEventQueueItem missing = JsonUtil.fromJson(
                json.replaceFirst(",?\"batchRevision\":\\d+", ""), ReplicatedEventQueueItem.class);
        assertThrows(IllegalArgumentException.class, missing::committedBatch);

        CommittedTaskBatch gapped = batchWithGap();
        ReplicatedEventQueueItem gap = JsonUtil.fromJson(JsonUtil.toJson(
                ReplicatedEventQueueItem.committed(gapped, EPOCH, "x")), ReplicatedEventQueueItem.class);
        assertThrows(IllegalArgumentException.class, gap::committedBatch);
    }

    private static CommittedTaskBatch batchWithGap() {
        Task task = Task.builder().id(TASK).contextId("ctx").status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .generation(5).build();
        return new CommittedTaskBatch(new TaskSnapshot(task, new TaskRevision(1)),
                List.of(status(TASK, TaskState.TASK_STATE_WORKING, 3), status(TASK, TaskState.TASK_STATE_WORKING, 5)));
    }

    @Test
    void remoteBatchIsVisibleToLocalObserversWithoutAnyStoreWriteOrWebhook() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);
        CommittedTaskBatch b = batch(TASK, 3, TaskState.TASK_STATE_WORKING, 1, 2);

        manager.onReplicatedEvent(remote(b));

        assertEquals(List.of(1L, 2L), drainGenerations(queue, 2));
        // Positive control (same wait, same executor): a LOCALLY committed batch does reach the webhook sender.
        String localTask = "local-control";
        EventQueue localQueue = manager.createOrTap(localTask);
        store.commit(localTask, localQueue);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!webhookEvents.contains(localTask) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(webhookEvents.contains(localTask), "control: a local committed batch must dispatch a webhook");
        assertFalse(webhookEvents.contains(TASK), "remote copies never dispatch webhooks");
        assertEquals(0, store.mutationsFor(TASK), "a committed copy must never mutate the shared store");
        assertEquals(0, store.savesFor(TASK), "a committed copy must never save to the shared store");
        assertTrue(strategy.committed.stream().noneMatch(i -> TASK.equals(i.getTaskId())),
                "a received copy is never re-replicated");
        assertTrue(strategy.legacy.isEmpty());
    }

    @Test
    void laterBatchFirstMakesTheEarlierBatchStaleAndDroppedNotDeliveredOutOfOrder() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);
        manager.onReplicatedEvent(remote(batch(TASK, 5, TaskState.TASK_STATE_WORKING, 5, 6)));
        manager.onReplicatedEvent(remote(batch(TASK, 3, TaskState.TASK_STATE_WORKING, 3, 4)));

        // Documented limitation: the first batch seen is the starting point; observers never see generations go back.
        assertEquals(List.of(5L, 6L), drainGenerations(queue, 2));
        assertEquals(List.of(), drainQuiet(queue));
    }

    @Test
    void poisonPillBeforeAnyBatchClosesImmediatelyAndNeverMutatesTheStore() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);

        manager.onReplicatedEvent(ReplicatedEventQueueItem.stampedClosed(TASK, EPOCH, "other-node", 2L));

        assertTrue(queueClosedObserved(queue));
        assertEquals(0, store.mutationsFor(TASK));
        assertEquals(0, store.savesFor(TASK));
    }

    @Test
    void remoteNonTerminalBatchForAnInactiveTaskIsSkippedButATerminalOneIsDelivered() throws Exception {
        TaskStateProvider inactive = new TaskStateProvider() {
            @Override
            public boolean isTaskActive(String taskId) {
                return false;
            }

            @Override
            public boolean isTaskFinalized(String taskId) {
                return true;
            }
        };
        ReplicatedQueueManager receiver = new ReplicatedQueueManager(new RecordingStrategy(), inactive, mainEventBus,
                new org.a2aproject.sdk.server.events.DefaultTaskStreamLifecycleHook(), EPOCH);

        EventQueue expired = receiver.createOrTap("expired");
        EventQueue expiredFinal = receiver.createOrTap("expired-final");

        receiver.onReplicatedEvent(remote(batch("expired", 1, TaskState.TASK_STATE_WORKING, 1)));
        receiver.onReplicatedEvent(remote(batch("expired-final", 1, TaskState.TASK_STATE_COMPLETED, 1)));

        assertEquals(List.of(), drainQuiet(expired), "a non-terminal batch for an expired task is skipped");
        assertEquals(List.of(1L), drainGenerations(expiredFinal, 1), "a terminal batch carries the final state");
    }

    @Test
    void outOfOrderBatchesAreBufferedAndDeliveredInOrderOnce() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);
        manager.onReplicatedEvent(remote(batch(TASK, 1, TaskState.TASK_STATE_WORKING, 1)));
        CommittedTaskBatch second = batch(TASK, 2, TaskState.TASK_STATE_WORKING, 2, 3);
        CommittedTaskBatch third = batch(TASK, 3, TaskState.TASK_STATE_WORKING, 4);

        manager.onReplicatedEvent(remote(third));    // arrives early: buffered
        manager.onReplicatedEvent(remote(second));   // releases both
        manager.onReplicatedEvent(remote(third));    // duplicate of a delivered batch

        assertEquals(List.of(1L, 2L, 3L, 4L), drainGenerations(queue, 4));
        assertEquals(List.of(), drainQuiet(queue), "no further generation may appear");
    }

    @Test
    void duplicateIsDroppedAndConflictingPayloadIsRejected() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);
        CommittedTaskBatch first = batch(TASK, 1, TaskState.TASK_STATE_WORKING, 1);
        manager.onReplicatedEvent(remote(first));
        manager.onReplicatedEvent(remote(first));
        // Same range, different content: different revision means a different payload.
        manager.onReplicatedEvent(remote(batch(TASK, 9, TaskState.TASK_STATE_WORKING, 1)));
        // Overlapping a delivered range differently.
        manager.onReplicatedEvent(remote(batch(TASK, 10, TaskState.TASK_STATE_WORKING, 1, 2)));

        assertEquals(List.of(1L), drainQuiet(queue));
    }

    @Test
    void conflictingBufferedBatchIsRejected() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);
        manager.onReplicatedEvent(remote(batch(TASK, 1, TaskState.TASK_STATE_WORKING, 1)));
        manager.onReplicatedEvent(remote(batch(TASK, 5, TaskState.TASK_STATE_WORKING, 3)));
        manager.onReplicatedEvent(remote(batch(TASK, 6, TaskState.TASK_STATE_WORKING, 3)));  // conflicts with buffered
        manager.onReplicatedEvent(remote(batch(TASK, 2, TaskState.TASK_STATE_WORKING, 2)));

        assertEquals(List.of(1L, 2L, 3L), drainGenerations(queue, 3));
    }

    @Test
    void delayedUnversionedAndWrongEpochRecordsAreRejectedAfterMigration() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);

        manager.onReplicatedEvent(new ReplicatedEventQueueItem(TASK, status(TASK, TaskState.TASK_STATE_WORKING, 0)));
        manager.onReplicatedEvent(ReplicatedEventQueueItem.committed(
                batch(TASK, 1, TaskState.TASK_STATE_WORKING, 1), EPOCH + 1, "other"));
        manager.onReplicatedEvent(new ReplicatedEventQueueItem(TASK, new QueueClosedEvent(TASK)));

        assertEquals(List.of(), drainQuiet(queue));
        assertFalse(queueClosedObserved(queue));
    }

    private static boolean queueClosedObserved(EventQueue queue) throws Exception {
        try {
            EventQueueItem item = queue.dequeueEventItem(100);
            return item != null && item.getEvent() instanceof QueueClosedEvent;
        } catch (EventQueueClosedException closed) {
            return true;
        }
    }

    @Test
    void ownBroadcastEchoedByTheBrokerIsDropped() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);
        String own = originIdOf(manager);
        manager.onReplicatedEvent(ReplicatedEventQueueItem.committed(
                batch(TASK, 1, TaskState.TASK_STATE_WORKING, 1), EPOCH, own));
        assertEquals(List.of(), drainQuiet(queue));
    }

    private static String originIdOf(ReplicatedQueueManager manager) throws Exception {
        var field = ReplicatedQueueManager.class.getDeclaredField("originId");
        field.setAccessible(true);
        return (String) field.get(manager);
    }

    @Test
    void committedEnvelopeIsRejectedByALegacyOnlyNode() throws Exception {
        ReplicatedQueueManager legacy = new ReplicatedQueueManager(new RecordingStrategy(), new ActiveProvider(),
                mainEventBus);
        EventQueue queue = legacy.createOrTap("legacy-task");
        legacy.onReplicatedEvent(remote(batch("legacy-task", 1, TaskState.TASK_STATE_WORKING, 1)));
        assertEquals(List.of(), drainQuiet(queue));
    }

    @Test
    void legacyOnlyProfileStillReplicatesPerEventAndFinalizesWithStatusThenPoisonPill() {
        RecordingStrategy legacyStrategy = new RecordingStrategy();
        ReplicatedQueueManager legacy = new ReplicatedQueueManager(legacyStrategy, new ActiveProvider(), mainEventBus);
        Task done = Task.builder().id("lg").contextId("ctx").status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();

        legacy.onTaskFinalized(new TaskFinalizedEvent("lg", done));

        assertEquals(2, legacyStrategy.legacy.size());
        assertInstanceOf(TaskStatusUpdateEvent.class, legacyStrategy.legacy.get(0));
        assertInstanceOf(QueueClosedEvent.class, legacyStrategy.legacy.get(1));
        assertTrue(legacyStrategy.committed.isEmpty());
    }

    @Test
    void finalizationForwardsTheOriginalStampedTerminalBatchThenAnOrderedPoisonPill() {
        CommittedTaskBatch terminal = batch(TASK, 8, TaskState.TASK_STATE_COMPLETED, 4, 5);
        Task finalTask = terminal.snapshot().task();

        manager.onTaskFinalized(new TaskFinalizedEvent(TASK, finalTask, terminal));

        assertEquals(1, strategy.committed.size(), "exactly the original batch, no synthesized status generation");
        ReplicatedEventQueueItem forwarded = strategy.committed.get(0);
        assertEquals(terminal.identity(), forwarded.committedBatch().identity());
        assertEquals(List.of(4L, 5L), forwarded.committedBatch().generationPositions());
        assertEquals(EPOCH, forwarded.getWriterEpoch());
        assertEquals(2, strategy.ordered.size());
        assertTrue(strategy.ordered.get(1).isClosedEvent());
        assertEquals(5L, strategy.ordered.get(1).getClosedAfterGeneration());
        assertTrue(strategy.legacy.isEmpty(), "no second status event is synthesized");
    }

    @Test
    void duplicateFinalizationForwardsTheSameIdentityWhichReceiversDeduplicate() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);
        CommittedTaskBatch terminal = batch(TASK, 8, TaskState.TASK_STATE_COMPLETED, 1);
        manager.onTaskFinalized(new TaskFinalizedEvent(TASK, terminal.snapshot().task(), terminal));
        manager.onTaskFinalized(new TaskFinalizedEvent(TASK, terminal.snapshot().task(), terminal));
        assertEquals(strategy.committed.get(0).committedBatch().identity(),
                strategy.committed.get(1).committedBatch().identity());

        // A remote node receives both copies and shows one.
        ReplicatedQueueManager receiver = new ReplicatedQueueManager(new RecordingStrategy(), new ActiveProvider(),
                mainEventBus, new org.a2aproject.sdk.server.events.DefaultTaskStreamLifecycleHook(), EPOCH);
        EventQueue remoteQueue = receiver.createOrTap(TASK);
        strategy.committed.forEach(receiver::onReplicatedEvent);
        assertEquals(List.of(1L), drainGenerations(remoteQueue, 1));
        assertEquals(List.of(), drainQuiet(remoteQueue), "the duplicate finalization is shown once");
    }

    @Test
    void reversedTerminalBatchAndPoisonPillStillCloseOnlyAfterTheTerminalBatch() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);
        CommittedTaskBatch first = batch(TASK, 1, TaskState.TASK_STATE_WORKING, 1);
        CommittedTaskBatch terminal = batch(TASK, 2, TaskState.TASK_STATE_COMPLETED, 2);
        manager.onReplicatedEvent(remote(first));
        // Pill arrives before the terminal batch.
        manager.onReplicatedEvent(ReplicatedEventQueueItem.stampedClosed(TASK, EPOCH, "other-node", 2L));
        assertEquals(List.of(1L), drainGenerations(queue, 1));
        assertFalse(queueClosedObserved(queue), "the poison pill must wait for the terminal batch");

        manager.onReplicatedEvent(remote(terminal));

        assertEquals(List.of(2L), drainGenerations(queue, 1));
        assertTrue(queueClosedObserved(queue), "after the terminal batch the pill closes the queue");
    }

    @Test
    void localCommittedBatchesReplicateOncePerBatchInOrderAndRemoteCopiesAreNotReplicated() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);
        CountDownLatch done = new CountDownLatch(2);
        processor.setCallback(new MainEventBusProcessorCallback() {
            @Override
            public void onEventProcessed(String taskId, Event event) {
                done.countDown();
            }

            @Override
            public void onTaskFinalized(String taskId) {
            }
        });
        // Bootstrap plus one committed batch through the real authority and bus path.
        store.commit(TASK, queue);

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(1, strategy.committed.size(), "one replication call per committed batch");
        assertEquals(EPOCH, strategy.committed.get(0).getWriterEpoch());
        assertTrue(strategy.legacy.isEmpty(), "no unstamped per-event record under an epoch");
        assertEquals(2, store.mutations.get(), "both local events were committed by the authority");
        // The opening SUBMITTED status is the bootstrap (no event); WORKING is the first committed generation.
        assertEquals(List.of(1L), strategy.committed.get(0).committedBatch().generationPositions(),
                "the replicated batch carries the canonical committed generation");
    }

    @Test
    void stampedBareEventIsRejectedUnderAnEpochSoItCannotReachTheSharedStoreAgain() throws Exception {
        EventQueue queue = manager.createOrTap(TASK);
        ReplicatedEventQueueItem bare = new ReplicatedEventQueueItem(TASK, status(TASK, TaskState.TASK_STATE_WORKING, 0));
        JsonUtil.fromJson(JsonUtil.toJson(bare), ReplicatedEventQueueItem.class);
        var stamped = JsonUtil.fromJson(JsonUtil.toJson(bare).replaceFirst("\\{", "{\"writerEpoch\":7,\"originId\":\"o\","),
                ReplicatedEventQueueItem.class);
        assertEquals(EPOCH, stamped.getWriterEpoch());

        manager.onReplicatedEvent(stamped);

        assertEquals(List.of(), drainQuiet(queue));
        assertEquals(0, store.saves.get());
        assertEquals(0, store.mutations.get());
    }

    @Test
    void finalizationWithoutACommittedBatchUnderAnEpochSendsOnlyAStampedPoisonPill() {
        Task done = Task.builder().id("nb").contextId("ctx").status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();

        manager.onTaskFinalized(new TaskFinalizedEvent("nb", done));

        assertTrue(strategy.legacy.isEmpty(), "no synthesized status generation");
        assertEquals(1, strategy.ordered.size());
        assertTrue(strategy.ordered.get(0).isClosedEvent());
        assertEquals(EPOCH, strategy.ordered.get(0).getWriterEpoch());
    }

    @Test
    void configuredEpochOnLocalMemoryTaskStoreIsRejected() {
        var provider = (org.a2aproject.sdk.server.config.A2AConfigProvider) new org.a2aproject.sdk.server.config.A2AConfigProvider() {
            @Override
            public String getValue(String name) {
                return "7";
            }

            @Override
            public java.util.Optional<String> getOptionalValue(String name) {
                return java.util.Optional.of("7");
            }
        };
        assertThrows(IllegalStateException.class, () -> new ReplicatedQueueManager(strategy, new InMemoryTaskStore(),
                mainEventBus, new org.a2aproject.sdk.server.events.DefaultTaskStreamLifecycleHook(), provider));
    }

    private static final class ActiveProvider implements TaskStateProvider {
        @Override
        public boolean isTaskActive(String taskId) {
            return true;
        }

        @Override
        public boolean isTaskFinalized(String taskId) {
            return false;
        }
    }

    private static final class RecordingStrategy implements ReplicationStrategy {
        final List<Event> legacy = new CopyOnWriteArrayList<>();
        final List<ReplicatedEventQueueItem> committed = new CopyOnWriteArrayList<>();
        final List<ReplicatedEventQueueItem> ordered = new CopyOnWriteArrayList<>();

        @Override
        public void send(String taskId, Event event) {
            legacy.add(event);
        }

        @Override
        public void send(ReplicatedEventQueueItem item) {
            ordered.add(item);
            if (item.isCommittedBatch()) {
                committed.add(item);
            }
        }
    }

    /** InMemoryTaskStore that counts writes and can commit batches through the authority for the bus. */
    private static final class CountingStore extends InMemoryTaskStore {
        final java.util.concurrent.atomic.AtomicInteger mutations = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger saves = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> mutationsByTask =
                new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> savesByTask =
                new java.util.concurrent.ConcurrentHashMap<>();

        int mutationsFor(String taskId) {
            var count = mutationsByTask.get(taskId);
            return count == null ? 0 : count.get();
        }

        int savesFor(String taskId) {
            var count = savesByTask.get(taskId);
            return count == null ? 0 : count.get();
        }

        @Override
        public void save(Task task, boolean isReplicated) {
            saves.incrementAndGet();
            savesByTask.computeIfAbsent(task.id(), k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
            super.save(task, isReplicated);
        }

        @Override
        public CommittedMutation mutate(String taskId, TaskMutation mutation,
                org.a2aproject.sdk.server.tasks.TaskCommitSlotCallback callback) {
            mutations.incrementAndGet();
            mutationsByTask.computeIfAbsent(taskId, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
            return super.mutate(taskId, mutation, callback);
        }

        void commit(String taskId, EventQueue queue) {
            queue.enqueueEvent(TaskStatusUpdateEvent.builder().taskId(taskId).contextId("ctx")
                    .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build());
            queue.enqueueEvent(status(taskId, TaskState.TASK_STATE_WORKING, 0));
        }
    }
}
