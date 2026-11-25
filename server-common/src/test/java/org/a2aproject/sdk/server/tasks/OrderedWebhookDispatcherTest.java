package org.a2aproject.sdk.server.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.junit.jupiter.api.Test;

class OrderedWebhookDispatcherTest {

    @Test
    void sharedGateClaimsOnlyTheExactNextRangeAndRequiresMatchingCompletion() {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        assertTrue(gate.initializeTaskLane("task-1", 7, 4));

        WebhookDispatchClaim claim = gate.claim("task-1", 7, 4, 5).orElseThrow();
        assertTrue(gate.claim("task-1", 7, 4, 4).isEmpty());
        assertTrue(gate.claim("task-1", 8, 4, 5).isEmpty());
        assertFalse(gate.complete(new WebhookDispatchClaim("task-1", 7, "stale", 4, 5)));
        assertEquals(4, gate.readLane("task-1").orElseThrow().nextGeneration());

        assertTrue(gate.complete(claim));
        assertEquals(6, gate.readLane("task-1").orElseThrow().nextGeneration());
        assertFalse(gate.complete(claim));
        assertTrue(gate.claim("task-1", 7, 4, 5).isEmpty());
    }

    @Test
    void sharedProfileRejectsSenderThatCannotProveQuiescentCompletion() {
        InMemoryWebhookDispatchGate sharedGate = new InMemoryWebhookDispatchGate() {
            @Override
            public boolean isSharedStorage() {
                return true;
            }
        };

        assertThrows(IllegalStateException.class,
                () -> new OrderedWebhookDispatcher(sharedGate, (event, snapshot) -> true));
    }

    @Test
    void gapRetirementIsConditionalAndRecordsTheLostCanonicalRange() {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("task-2", 3, 2);
        WebhookDispatchClaim owner = gate.claim("task-2", 3, 2, 2).orElseThrow();

        assertFalse(gate.retireUnclaimedGap("task-2", 3, 2, 4));
        assertTrue(gate.complete(owner));
        assertFalse(gate.retireUnclaimedGap("task-2", 3, 2, 4));
        assertTrue(gate.retireUnclaimedGap("task-2", 3, 3, 4));

        WebhookDispatchLaneState state = gate.readLane("task-2").orElseThrow();
        assertEquals(5, state.nextGeneration());
        assertEquals(1, state.losses().size());
        assertEquals(3, state.losses().get(0).firstGeneration());
        assertEquals(4, state.losses().get(0).lastGeneration());
    }

    @Test
    void recordedAdmissionLossesAdvanceOnlyWhenTheyCoverTheUnclaimedCursor() {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("admission-loss-journal", 4, 1);

        assertTrue(gate.recordAdmissionLoss("admission-loss-journal", 4, 1, 1, "exact-next loss"));
        assertEquals(2, gate.readLane("admission-loss-journal").orElseThrow().nextGeneration());
        assertTrue(gate.recordAdmissionLoss("admission-loss-journal", 4, 3, 3, "future loss"));
        assertEquals(2, gate.readLane("admission-loss-journal").orElseThrow().nextGeneration(),
                "a loss cannot skip unrecorded generation 2");

        WebhookDispatchClaim generationTwo = gate.claim("admission-loss-journal", 4, 2, 2).orElseThrow();
        assertEquals(2, gate.readLane("admission-loss-journal").orElseThrow().nextGeneration());
        assertTrue(gate.complete(generationTwo));

        WebhookDispatchLaneState state = gate.readLane("admission-loss-journal").orElseThrow();
        assertEquals(4, state.nextGeneration(), "the recorded future loss drains after generation 2 settles");
        assertEquals(List.of(1L, 3L), state.losses().stream().map(WebhookDispatchLoss::firstGeneration).toList());
    }

    @Test
    void fullActionableLossJournalDoesNotBlockExactLossSettlementRecoveryOrGapRetirement() {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("full-loss-journal", 8, 1);
        for (int index = 0; index < 257; index++) {
            long generation = 1000 + index * 2L;
            assertTrue(gate.recordAdmissionLoss("full-loss-journal", 8, generation, generation,
                    "future actionable loss"));
        }

        assertTrue(gate.recordAdmissionLoss("full-loss-journal", 8, 1, 1, "exact-next loss"));
        assertEquals(2, gate.readLane("full-loss-journal").orElseThrow().nextGeneration());
        WebhookDispatchClaim completedWithLoss = gate.claim("full-loss-journal", 8, 2, 2).orElseThrow();
        assertTrue(gate.completeWithLoss(completedWithLoss, "settled unsuccessfully"));
        WebhookDispatchClaim abandoned = gate.claim("full-loss-journal", 8, 3, 3).orElseThrow();
        assertTrue(gate.recoverQuiescedClaim(abandoned, 9));
        assertTrue(gate.retireUnclaimedGap("full-loss-journal", 9, 4, 5));

        WebhookDispatchLaneState state = gate.readLane("full-loss-journal").orElseThrow();
        assertEquals(6, state.nextGeneration());
        assertEquals(257, state.losses().stream().filter(loss -> loss.firstGeneration() >= 1000).count(),
                "historical settlement records must never evict actionable future losses");
        assertEquals(261, state.losses().size());
        assertTrue(gate.claim("full-loss-journal", 9, 1000, 1000).isEmpty(),
                "sparse future loss ranges cannot skip unrecorded generations");
        assertEquals(6, gate.readLane("full-loss-journal").orElseThrow().nextGeneration());
    }

    @Test
    void cursorAdvancementPrunesHistoricalLossesAndKeepsNewestDiagnostics() {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("historical-loss-retention", 3, 1);
        for (int generation = 1; generation <= 300; generation++) {
            assertTrue(gate.recordAdmissionLoss("historical-loss-retention", 3, generation, generation,
                    "known admission loss"));
        }

        WebhookDispatchLaneState state = gate.readLane("historical-loss-retention").orElseThrow();
        assertEquals(301, state.nextGeneration());
        assertEquals(256, state.losses().size());
        assertEquals(45, state.losses().get(0).firstGeneration());
        assertEquals(300, state.losses().get(255).lastGeneration());
    }

    @Test
    void historicalPruningPreservesActionableLossInterleavedBeforeNewerHistory() {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("interleaved-loss-retention", 3, 1);
        WebhookDispatchClaim held = gate.claim("interleaved-loss-retention", 3, 1, 1).orElseThrow();
        assertTrue(gate.recordAdmissionLoss("interleaved-loss-retention", 3, 2, 2, "known loss"));
        assertTrue(gate.recordAdmissionLoss("interleaved-loss-retention", 3, 3, 3, "known loss"));
        assertTrue(gate.recordAdmissionLoss("interleaved-loss-retention", 3, 1000, 1000, "future loss"));
        for (int generation = 4; generation <= 259; generation++) {
            assertTrue(gate.recordAdmissionLoss("interleaved-loss-retention", 3, generation, generation,
                    "known loss"));
        }

        assertTrue(gate.complete(held));
        WebhookDispatchLaneState state = gate.readLane("interleaved-loss-retention").orElseThrow();
        assertEquals(260, state.nextGeneration());
        assertEquals(257, state.losses().size());
        assertTrue(state.losses().stream().anyMatch(loss -> loss.firstGeneration() == 1000),
                "actionable range must survive pruning");
        List<WebhookDispatchLoss> historical = state.losses().stream()
                .filter(loss -> loss.lastGeneration() < state.nextGeneration()).toList();
        assertEquals(256, historical.size());
        assertEquals(4, historical.get(0).firstGeneration(), "newest historical diagnostics remain retained");
        assertEquals(259, historical.get(255).lastGeneration());
    }

    @Test
    void verifiedLegacySummaryUsesZeroWidthLaneOwnershipWithoutAdvancingCursor() {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("task-3", 11, 8);

        WebhookDispatchNoopClaim claim = gate.claimNoop("task-3", 11, 8).orElseThrow();
        assertTrue(gate.claim("task-3", 11, 8, 8).isEmpty());
        assertTrue(gate.claimNoop("task-3", 11, 8).isEmpty());
        assertFalse(gate.releaseNoop(new WebhookDispatchNoopClaim("task-3", 11, "stale", 8)));
        assertTrue(gate.releaseNoop(claim));
        assertEquals(8, gate.readLane("task-3").orElseThrow().nextGeneration());

        assertTrue(gate.claimNoop("task-3", 11, 8).isPresent());
        assertFalse(gate.claimNoop("task-3", 11, 7).isPresent());
        assertNotNull(gate.readLane("task-3").orElseThrow().holderToken());
    }

    @Test
    void laterGenerationCannotStartUntilEarlierSenderStageSettlesEvenWhenObserverCancels() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("ordered-task", 0, 1);
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256);
        try {
            CompletableFuture<Boolean> first = dispatcher.dispatch(batch("ordered-task", 1, 1))
                    .toCompletableFuture();
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> second = dispatcher.dispatch(batch("ordered-task", 2, 2))
                    .toCompletableFuture();

            first.cancel(false);
            assertFalse(sender.started(2).await(200, TimeUnit.MILLISECONDS),
                    "cancelling an observer cannot release a claim while the sender stage is running");
            assertEquals(1, gate.readLane("ordered-task").orElseThrow().nextGeneration());

            sender.complete(1);
            assertTrue(sender.started(2).await(5, TimeUnit.SECONDS));
            sender.complete(2);
            assertTrue(second.get(5, TimeUnit.SECONDS));
            assertEquals(3, gate.readLane("ordered-task").orElseThrow().nextGeneration());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void noOpAtNextCursorRunsBeforeLaterCanonicalBatchAtSameBoundary() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("same-boundary-order", 0, 1);
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256);
        try {
            CompletableFuture<Boolean> first = dispatcher.dispatch(batch("same-boundary-order", 1, 1))
                    .toCompletableFuture();
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            Task summary = Task.builder().id("same-boundary-order").contextId("ctx")
                    .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).generation(1).build();
            CompletableFuture<Boolean> noOp = dispatcher.dispatchLegacySummary(summary, 0L).toCompletableFuture();
            CompletableFuture<Boolean> second = dispatcher.dispatch(batch("same-boundary-order", 2, 2))
                    .toCompletableFuture();

            sender.complete(1);
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertTrue(sender.started(2).await(5, TimeUnit.SECONDS));
            sender.complete(2);
            assertTrue(noOp.get(5, TimeUnit.SECONDS));
            assertEquals(List.of("event:1", "noop:1", "event:2"), sender.dispatchOrder);
            assertTrue(second.get(5, TimeUnit.SECONDS));
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void lateAdmissionDecisionExpiresEvenWhenObservedAfterEarlierClaimSettles() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("late-admission", 0, 1);
        GatedSender sender = new GatedSender();
        AtomicLong ticker = new AtomicLong(1);
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                ticker::get, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256);
        try {
            CompletableFuture<Boolean> first = dispatcher.dispatch(batch("late-admission", 1, 1))
                    .toCompletableFuture();
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> admission = new CompletableFuture<>();
            CompletableFuture<Boolean> late = dispatcher.dispatch(batch("late-admission", 2, 2), admission)
                    .toCompletableFuture();

            ticker.set(1 + TimeUnit.SECONDS.toNanos(10));
            admission.complete(true);
            sender.complete(1);

            assertFalse(late.get(5, TimeUnit.SECONDS));
            assertEquals(3, gate.readLane("late-admission").orElseThrow().nextGeneration());
            assertEquals(List.of(2L), gate.readLane("late-admission").orElseThrow().losses().stream()
                    .map(WebhookDispatchLoss::firstGeneration).toList());
            assertEquals(List.of("event:1"), sender.dispatchOrder);
            assertTrue(first.get(5, TimeUnit.SECONDS));
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void timelyAdmissionDecisionRemainsValidWhileQueuedBehindEarlierClaim() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("timely-admission", 0, 1);
        GatedSender sender = new GatedSender();
        AtomicLong ticker = new AtomicLong(1);
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                ticker::get, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256);
        try {
            CompletableFuture<Boolean> first = dispatcher.dispatch(batch("timely-admission", 1, 1))
                    .toCompletableFuture();
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> admission = new CompletableFuture<>();
            CompletableFuture<Boolean> second = dispatcher.dispatch(batch("timely-admission", 2, 2), admission)
                    .toCompletableFuture();

            ticker.set(1 + TimeUnit.SECONDS.toNanos(9));
            admission.complete(true);
            sender.complete(1);

            assertTrue(sender.started(2).await(5, TimeUnit.SECONDS));
            sender.complete(2);
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertTrue(second.get(5, TimeUnit.SECONDS));
            assertEquals(List.of("event:1", "event:2"), sender.dispatchOrder);
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void blockedCompletionObserverCannotLetUnstampedLateAdmissionThrough() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("blocked-admission-observer", 0, 1);
        GatedSender sender = new GatedSender();
        AtomicLong ticker = new AtomicLong(1);
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                ticker::get, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        Thread completingThread = null;
        try {
            CompletableFuture<Boolean> first = dispatcher.dispatch(batch("blocked-admission-observer", 1, 1))
                    .toCompletableFuture();
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> admission = new CompletableFuture<>();
            CompletableFuture<Boolean> late = dispatcher.dispatch(
                    batch("blocked-admission-observer", 2, 2), admission).toCompletableFuture();
            admission.whenComplete((value, failure) -> {
                callbackEntered.countDown();
                try {
                    releaseCallback.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            });

            ticker.set(1 + TimeUnit.SECONDS.toNanos(11));
            completingThread = new Thread(() -> admission.complete(true), "blocked-admission-completer");
            completingThread.start();
            assertTrue(callbackEntered.await(5, TimeUnit.SECONDS));
            sender.complete(1);

            assertFalse(late.get(5, TimeUnit.SECONDS));
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertEquals(List.of("event:1"), sender.dispatchOrder);
            WebhookDispatchLaneState state = gate.readLane("blocked-admission-observer").orElseThrow();
            assertEquals(3, state.nextGeneration());
            assertEquals(List.of(2L), state.losses().stream().map(WebhookDispatchLoss::firstGeneration).toList());
        } finally {
            releaseCallback.countDown();
            sender.complete(2);
            if (completingThread != null) {
                completingThread.join(5000);
            }
            dispatcher.close();
        }
    }

    @Test
    void reservedAdmissionBoundsDeferredPayloadAndDuplicateTerminalIdentityUsesOneDispatch() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("reserved-task", 0, 1);
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256);
        try {
            CommittedTaskBatch terminal = terminalBatch("reserved-task", 1, 1, 0);
            CompletableFuture<Boolean> admission = new CompletableFuture<>();
            CompletionStage<Boolean> first = dispatcher.dispatch(terminal, admission);
            CompletionStage<Boolean> duplicate = dispatcher.dispatch(terminal);

            assertFalse(sender.started(1).await(100, TimeUnit.MILLISECONDS),
                    "reservation retains bounded input without starting HTTP before callback admission");
            admission.complete(true);
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            sender.complete(1);

            assertTrue(first.toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertTrue(duplicate.toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(2, gate.readLane("reserved-task").orElseThrow().nextGeneration());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void capacityBridgeDoesNotRetainTheDroppedCommittedBatch() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("payload-bridge-task", 0, 1);
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 1,
                4L * 1024 * 1024, 256);
        try {
            CompletableFuture<Boolean> first = dispatcher.dispatch(batch("payload-bridge-task", 1, 1), false,
                    0L, CompletableFuture.completedFuture(true)).toCompletableFuture();
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> deferredAdmission = new CompletableFuture<>();
            WeakReference<CommittedTaskBatch> droppedBatch = enqueuePayloadOnlyDroppedBatch(dispatcher,
                    deferredAdmission);

            assertFalse(first.isDone());
            for (int attempt = 0; attempt < 30 && droppedBatch.get() != null; attempt++) {
                System.gc();
                Thread.sleep(50);
            }
            assertNull(droppedBatch.get(), "the admission bridge must keep only the completion token");
            deferredAdmission.complete(false);
            sender.complete(1);
            assertTrue(first.get(5, TimeUnit.SECONDS));
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void queuedBatchCannotAdoptRebasedEpoch() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("rebased-task", 9, 1);
        WebhookDispatchClaim earlier = gate.claim("rebased-task", 9, 1, 1).orElseThrow();
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256);
        try {
            CompletableFuture<Boolean> admission = new CompletableFuture<>();
            CompletableFuture<Boolean> queued = dispatcher.dispatch(batch("rebased-task", 2, 2), false, 9L, admission)
                    .toCompletableFuture();
            assertTrue(gate.complete(earlier));
            assertTrue(gate.rebaseUnclaimedLane("rebased-task", 9, 10));
            admission.complete(true);

            assertFalse(queued.get(5, TimeUnit.SECONDS));
            assertFalse(sender.started(2).await(100, TimeUnit.MILLISECONDS));
            WebhookDispatchLaneState state = gate.readLane("rebased-task").orElseThrow();
            assertEquals(10, state.writerEpoch());
            assertEquals(2, state.nextGeneration());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void successorFromNewEpochWaitsForOldClaimThenRebasesWithoutAdoptingOldWork() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("future-epoch-task", 9, 1);
        WebhookDispatchClaim earlier = gate.claim("future-epoch-task", 9, 1, 1).orElseThrow();
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256);
        try {
            CompletableFuture<Boolean> successor = dispatcher.dispatch(batch("future-epoch-task", 2, 2), false,
                    10L, CompletableFuture.completedFuture(true)).toCompletableFuture();
            assertFalse(sender.started(2).await(200, TimeUnit.MILLISECONDS));
            assertEquals(9, gate.readLane("future-epoch-task").orElseThrow().writerEpoch());

            assertTrue(gate.complete(earlier));
            assertTrue(sender.started(2).await(5, TimeUnit.SECONDS));
            sender.complete(2);
            assertTrue(successor.get(5, TimeUnit.SECONDS));
            WebhookDispatchLaneState state = gate.readLane("future-epoch-task").orElseThrow();
            assertEquals(10, state.writerEpoch());
            assertEquals(3, state.nextGeneration());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void sharedDispatchRequiresCapturedOriginEpoch() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate() {
            @Override
            public boolean isSharedStorage() {
                return true;
            }
        };
        gate.initializeTaskLane("missing-origin-epoch", 9, 1);
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256);
        try {
            CommittedTaskBatch batch = batch("missing-origin-epoch", 1, 1);
            assertThrows(CompletionException.class, () -> dispatcher.dispatch(batch).toCompletableFuture().join());
            assertFalse(sender.started(1).await(100, TimeUnit.MILLISECONDS));
            CompletableFuture<Boolean> valid = dispatcher.dispatch(batch, false, 9L,
                    CompletableFuture.completedFuture(true)).toCompletableFuture();
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            sender.complete(1);
            assertTrue(valid.get(5, TimeUnit.SECONDS));
            assertEquals(2, gate.readLane("missing-origin-epoch").orElseThrow().nextGeneration());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void queuedLegacySummaryCannotAdoptRebasedEpoch() throws Exception {
        CountDownLatch claimingNoop = new CountDownLatch(1);
        CountDownLatch continueNoopClaim = new CountDownLatch(1);
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate() {
            @Override
            public java.util.Optional<WebhookDispatchNoopClaim> claimNoop(String taskId, long writerEpoch,
                    long expectedNextGeneration) {
                claimingNoop.countDown();
                try {
                    assertTrue(continueNoopClaim.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return super.claimNoop(taskId, writerEpoch, expectedNextGeneration);
            }
        };
        gate.initializeTaskLane("rebased-summary-task", 9, 1);
        WebhookDispatchClaim earlier = gate.claim("rebased-summary-task", 9, 1, 1).orElseThrow();
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256);
        try {
            Task summary = Task.builder().id("rebased-summary-task").contextId("ctx")
                    .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).generation(0).build();
            CompletableFuture<Boolean> queued = dispatcher.dispatchLegacySummary(summary, 9L).toCompletableFuture();
            assertTrue(claimingNoop.await(5, TimeUnit.SECONDS));
            assertTrue(gate.complete(earlier));
            assertTrue(gate.rebaseUnclaimedLane("rebased-summary-task", 9, 10));
            continueNoopClaim.countDown();

            assertFalse(queued.get(5, TimeUnit.SECONDS));
            assertFalse(sender.started(1).await(100, TimeUnit.MILLISECONDS));
            WebhookDispatchLaneState state = gate.readLane("rebased-summary-task").orElseThrow();
            assertEquals(10, state.writerEpoch());
            assertEquals(2, state.nextGeneration());
            assertNull(state.holderToken());
        } finally {
            continueNoopClaim.countDown();
            dispatcher.close();
        }
    }

    @Test
    void sparseOverflowMarkerDoesNotSwallowDuplicateRangeBelowCursor() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("sparse-overflow-task", 0, 1);
        GatedSender sender = new GatedSender();
        AtomicLong ticker = new AtomicLong(1);
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender, ticker::get,
                TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 1,
                4L * 1024 * 1024, 1);
        try {
            CompletableFuture<Boolean> held = dispatcher.dispatch(batch("sparse-overflow-task", 1, 1))
                    .toCompletableFuture();
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> droppedThree = dispatcher.dispatch(batch("sparse-overflow-task", 3, 3))
                    .toCompletableFuture();
            CompletableFuture<Boolean> duplicateOne = dispatcher.dispatch(batch("sparse-overflow-task", 1, 2))
                    .toCompletableFuture();
            assertFalse(duplicateOne.get(5, TimeUnit.SECONDS));

            ticker.set(TimeUnit.SECONDS.toNanos(11));
            sender.complete(1);
            assertTrue(held.get(5, TimeUnit.SECONDS));
            assertFalse(droppedThree.get(5, TimeUnit.SECONDS));
            WebhookDispatchLaneState state = awaitNext(gate, "sparse-overflow-task", 4);
            assertEquals(2, state.losses().size());
            assertEquals(2, state.losses().get(0).firstGeneration());
            assertEquals(2, state.losses().get(0).lastGeneration());
            assertEquals(3, state.losses().get(1).firstGeneration());
            assertEquals(3, state.losses().get(1).lastGeneration());
            assertEquals(1, sender.started(3).getCount(), "dropped payloads must not reach the sender");
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void overflowSuffixAndMixedEpochLossRemainSeparateAfterCursorAdvances() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("mixed-overflow-epochs", 0, 1);
        GatedSender sender = new GatedSender();
        AtomicLong ticker = new AtomicLong(1);
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender, ticker::get,
                TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 1);
        try {
            CompletableFuture<Boolean> held = dispatcher.dispatch(batch("mixed-overflow-epochs", 1, 1), false,
                    0L, CompletableFuture.completedFuture(true)).toCompletableFuture();
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> epochZeroDrop = dispatcher.dispatch(batch("mixed-overflow-epochs", 3, 3),
                    false, 0L, CompletableFuture.completedFuture(true)).toCompletableFuture();
            CompletableFuture<Boolean> epochOneDrop = dispatcher.dispatch(batch("mixed-overflow-epochs", 4, 4),
                    false, 1L, CompletableFuture.completedFuture(true)).toCompletableFuture();
            assertFalse(epochZeroDrop.get(5, TimeUnit.SECONDS));
            assertFalse(epochOneDrop.get(5, TimeUnit.SECONDS));

            ticker.set(TimeUnit.SECONDS.toNanos(11));
            sender.complete(1);
            assertTrue(held.get(5, TimeUnit.SECONDS));
            WebhookDispatchLaneState state = awaitNext(gate, "mixed-overflow-epochs", 4);
            assertEquals(0, state.writerEpoch(), "an overflow marker must retain its original epoch");
            assertEquals(3, state.losses().size());
            assertTrue(state.losses().stream().anyMatch(loss -> loss.firstGeneration() == 2
                    && loss.lastGeneration() == 2));
            assertTrue(state.losses().stream().anyMatch(loss -> loss.firstGeneration() == 3
                    && loss.lastGeneration() == 3));
            assertTrue(state.losses().stream().anyMatch(loss -> loss.firstGeneration() == 4
                    && loss.lastGeneration() == 4));
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void orphanLegacySummaryExpiresWithoutMovingCanonicalCursor() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("orphan-summary-task", 0, 1);
        GatedSender sender = new GatedSender();
        AtomicLong ticker = new AtomicLong(1);
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender, ticker::get,
                TimeUnit.SECONDS.toNanos(30), 10, 256, 4L * 1024 * 1024, 256);
        try {
            Task orphan = Task.builder().id("orphan-summary-task").contextId("ctx")
                    .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).generation(3).build();
            CompletableFuture<Boolean> summary = dispatcher.dispatchLegacySummary(orphan).toCompletableFuture();
            ticker.set(11);

            assertFalse(summary.get(5, TimeUnit.SECONDS));
            assertEquals(1, gate.readLane("orphan-summary-task").orElseThrow().nextGeneration());
            assertNull(gate.readLane("orphan-summary-task").orElseThrow().holderToken());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void noOpAheadOfLaterOverflowWaitsWithPositiveDelayInsteadOfBusyPolling() throws Exception {
        AtomicLong reads = new AtomicLong();
        InMemoryWebhookDispatchGate delegate = new InMemoryWebhookDispatchGate();
        delegate.initializeTaskLane("noop-before-overflow", 0, 1);
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate() {
            @Override
            public java.util.Optional<WebhookDispatchLanePosition> readPosition(String taskId) {
                reads.incrementAndGet();
                return delegate.readPosition(taskId);
            }

            @Override
            public java.util.Optional<WebhookDispatchLaneState> readLane(String taskId) {
                return delegate.readLane(taskId);
            }

            @Override
            public boolean recordAdmissionLoss(String taskId, long epoch, long first, long last, String reason) {
                return delegate.recordAdmissionLoss(taskId, epoch, first, last, reason);
            }

            @Override
            public boolean retireUnclaimedGap(String taskId, long epoch, long expected, long through) {
                return delegate.retireUnclaimedGap(taskId, epoch, expected, through);
            }
        };
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender, System::nanoTime,
                TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256, 4L * 1024 * 1024, 1);
        try {
            Task orphan = Task.builder().id("noop-before-overflow").contextId("ctx")
                    .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).generation(3).build();
            CompletableFuture<Boolean> summary = dispatcher.dispatchLegacySummary(orphan).toCompletableFuture();
            CompletableFuture<Boolean> laterOverflow = dispatcher.dispatch(batch("noop-before-overflow", 5, 5))
                    .toCompletableFuture();

            Thread.sleep(350);
            assertFalse(summary.isDone());
            assertEquals(1, delegate.readLane("noop-before-overflow").orElseThrow().nextGeneration());
            assertTrue(reads.get() <= 8, "orphan summary must wait on the bounded positive retry delay");

            // Let the orphan expire; later overflow is then handled by the existing 10-second gap rule.
            assertFalse(laterOverflow.get(5, TimeUnit.SECONDS));
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void activeTaskQueueCapRecordsAndConsumesExactNextLoss() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("active-cap-task", 0, 1);
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256, 0);
        try {
            assertFalse(dispatcher.dispatch(batch("active-cap-task", 1, 1), false, 0L,
                    CompletableFuture.completedFuture(true)).toCompletableFuture().get(5, TimeUnit.SECONDS));
            WebhookDispatchLaneState state = gate.readLane("active-cap-task").orElseThrow();
            assertEquals(2, state.nextGeneration(), "the cursor advances only because this exact range was recorded lost");
            assertEquals(1, state.losses().size());
            assertEquals(1, state.losses().get(0).firstGeneration());
            assertEquals(1, state.losses().get(0).lastGeneration());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void deferredAdmissionExpiresFromOriginalEnqueueAndRecordsCanonicalLoss() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("expired-admission-task", 0, 1);
        GatedSender sender = new GatedSender();
        AtomicLong ticker = new AtomicLong(1);
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender, ticker::get,
                10, 10, 256, 4L * 1024 * 1024, 256);
        try {
            CompletableFuture<Boolean> admission = new CompletableFuture<>();
            CompletableFuture<Boolean> result = dispatcher.dispatch(batch("expired-admission-task", 1, 1), false,
                    0L, admission).toCompletableFuture();
            Thread.sleep(50);
            ticker.set(11);
            assertFalse(result.get(5, TimeUnit.SECONDS));
            assertEquals(1, sender.started(1).getCount());
            WebhookDispatchLaneState state = gate.readLane("expired-admission-task").orElseThrow();
            assertEquals(2, state.nextGeneration());
            assertEquals(1, state.losses().size());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void gateReadFailureClearsLocalQueueAndDoesNotPreventLaterAdmission() throws Exception {
        AtomicLong reads = new AtomicLong();
        InMemoryWebhookDispatchGate delegate = new InMemoryWebhookDispatchGate();
        delegate.initializeTaskLane("gate-read-failure-task", 0, 1);
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate() {
            @Override
            public java.util.Optional<WebhookDispatchLanePosition> readPosition(String taskId) {
                if (reads.incrementAndGet() == 1) {
                    throw new IllegalStateException("injected lane read failure");
                }
                return delegate.readPosition(taskId);
            }

            @Override
            public java.util.Optional<WebhookDispatchLaneState> readLane(String taskId) {
                return delegate.readLane(taskId);
            }

            @Override
            public java.util.Optional<WebhookDispatchClaim> claim(String taskId, long epoch, long first, long last) {
                return delegate.claim(taskId, epoch, first, last);
            }

            @Override
            public boolean complete(WebhookDispatchClaim claim) {
                return delegate.complete(claim);
            }

            @Override
            public boolean retireUnclaimedGap(String taskId, long epoch, long expected, long through) {
                return delegate.retireUnclaimedGap(taskId, epoch, expected, through);
            }

            @Override
            public java.util.Optional<WebhookDispatchNoopClaim> claimNoop(String taskId, long epoch, long expected) {
                return delegate.claimNoop(taskId, epoch, expected);
            }

            @Override
            public boolean releaseNoop(WebhookDispatchNoopClaim claim) {
                return delegate.releaseNoop(claim);
            }

            @Override
            public boolean completeWithLoss(WebhookDispatchClaim claim, String reason) {
                return delegate.completeWithLoss(claim, reason);
            }
        };
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 256);
        try {
            java.util.concurrent.atomic.AtomicReference<CompletableFuture<Boolean>> readmitted =
                    new java.util.concurrent.atomic.AtomicReference<>();
            CountDownLatch readmissionPublished = new CountDownLatch(1);
            CompletableFuture<Boolean> failed = dispatcher.dispatch(batch("gate-read-failure-task", 1, 1), false,
                    0L, CompletableFuture.completedFuture(true))
                    .toCompletableFuture();
            failed.whenComplete((ignored, failure) -> {
                readmitted.set(dispatcher.dispatch(batch("gate-read-failure-task", 1, 2), false, 0L,
                        CompletableFuture.completedFuture(true)).toCompletableFuture());
                readmissionPublished.countDown();
            });
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> failed.get(5, TimeUnit.SECONDS));

            assertTrue(readmissionPublished.await(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> later = readmitted.get();
            assertNotNull(later, "failure settlement should allow a concurrent admission into a fresh queue");
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            sender.complete(1);
            assertTrue(later.get(5, TimeUnit.SECONDS));
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void legacySummaryBytesCountAgainstThePerTaskBudget() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("summary-budget-task", 0, 1);
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256, 1, 256);
        try {
            Task snapshot = Task.builder().id("summary-budget-task").contextId("ctx")
                    .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).generation(0).build();
            assertFalse(dispatcher.dispatchLegacySummary(snapshot).toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(1, gate.readLane("summary-budget-task").orElseThrow().nextGeneration());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void expiredAttemptKeepsOwnershipUntilWorkerSettlesThenRecordsUncertainLoss() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("deadline-task", 0, 1);
        GatedSender sender = new GatedSender();
        AtomicLong ticker = new AtomicLong(1);
        LongSupplier nanoTime = ticker::get;
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender, nanoTime,
                10, 5, 256, 4L * 1024 * 1024, 256);
        try {
            CompletionStage<Boolean> attempt = dispatcher.dispatch(batch("deadline-task", 1, 1));
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));
            ticker.set(12);
            WebhookDispatchLaneState claimed = gate.readLane("deadline-task").orElseThrow();
            assertNotNull(claimed.holderToken(), "deadline observation alone cannot release active work");
            assertEquals(1, claimed.nextGeneration());

            sender.complete(1);
            assertFalse(attempt.toCompletableFuture().get(5, TimeUnit.SECONDS));
            WebhookDispatchLaneState settled = gate.readLane("deadline-task").orElseThrow();
            assertEquals(2, settled.nextGeneration());
            assertEquals(1, settled.losses().size());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void recordCapacityOverflowKeepsOneLossMarkerUntilTheUnclaimedRangeCanBeRetired() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("overflow-task", 0, 1);
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 256,
                4L * 1024 * 1024, 1);
        try {
            CompletableFuture<Boolean> first = dispatcher.dispatch(batch("overflow-task", 1, 1))
                    .toCompletableFuture();
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));

            CompletableFuture<Boolean> overflow = dispatcher.dispatch(batch("overflow-task", 2, 2))
                    .toCompletableFuture();
            assertFalse(overflow.get(5, TimeUnit.SECONDS));
            sender.complete(1);
            assertTrue(first.get(5, TimeUnit.SECONDS));

            WebhookDispatchLaneState state = awaitNext(gate, "overflow-task", 3);
            assertEquals(1, state.losses().size());
            assertEquals(2, state.losses().get(0).firstGeneration());
            assertEquals(2, state.losses().get(0).lastGeneration());
            assertEquals(1, sender.started(2).getCount(), "overflowed payload must not reach the sender");
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void eventCapacityDropIsReportedAndRetiredAfterAnEarlierClaimSettles() throws Exception {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("event-overflow-task", 0, 1);
        GatedSender sender = new GatedSender();
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(gate, sender,
                System::nanoTime, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(10), 1,
                4L * 1024 * 1024, 256);
        try {
            CompletableFuture<Boolean> first = dispatcher.dispatch(batch("event-overflow-task", 1, 1))
                    .toCompletableFuture();
            assertTrue(sender.started(1).await(5, TimeUnit.SECONDS));

            CompletableFuture<Boolean> dropped = dispatcher.dispatch(batch("event-overflow-task", 2, 2))
                    .toCompletableFuture();
            sender.complete(1);
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertFalse(dropped.get(5, TimeUnit.SECONDS), "a capacity drop must settle its observer");

            WebhookDispatchLaneState state = awaitNext(gate, "event-overflow-task", 3);
            assertEquals(1, state.losses().size());
            assertEquals(2, state.losses().get(0).firstGeneration());
            assertEquals(2, state.losses().get(0).lastGeneration());
            assertEquals(1, sender.started(2).getCount(), "dropped payload must not reach the sender");
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void abandonedClaimRecoveryRequiresExactClaimAndNewEpoch() {
        InMemoryWebhookDispatchGate gate = new InMemoryWebhookDispatchGate();
        gate.initializeTaskLane("recovery-task", 4, 6);
        WebhookDispatchClaim claim = gate.claim("recovery-task", 4, 6, 7).orElseThrow();

        assertFalse(gate.recoverQuiescedClaim(
                new WebhookDispatchClaim("recovery-task", 4, "stale", 6, 7), 5));
        assertTrue(gate.recoverQuiescedClaim(claim, 5));
        WebhookDispatchLaneState state = gate.readLane("recovery-task").orElseThrow();
        assertEquals(5, state.writerEpoch());
        assertEquals(8, state.nextGeneration());
        assertEquals(1, state.losses().size());
        assertTrue(state.holderToken() == null);
    }

    private static CommittedTaskBatch batch(String taskId, long generation, long revision) {
        TaskStatus status = new TaskStatus(TaskState.TASK_STATE_WORKING);
        Task task = Task.builder().id(taskId).contextId("ctx").status(status).generation(generation).build();
        TaskStatusUpdateEvent event = new TaskStatusUpdateEvent(taskId, status, "ctx", null, generation);
        return new CommittedTaskBatch(new TaskSnapshot(task, new TaskRevision(revision)), List.of(event));
    }

    private static WeakReference<CommittedTaskBatch> enqueuePayloadOnlyDroppedBatch(
            OrderedWebhookDispatcher dispatcher, CompletableFuture<Boolean> admission) {
        CommittedTaskBatch batch = batch("payload-bridge-task", 2, 2);
        WeakReference<CommittedTaskBatch> reference = new WeakReference<>(batch);
        dispatcher.dispatch(batch, false, 0L, admission);
        return reference;
    }

    private static CommittedTaskBatch terminalBatch(String taskId, long generation, long revision, long epoch) {
        TaskStatus status = new TaskStatus(TaskState.TASK_STATE_COMPLETED);
        Task task = Task.builder().id(taskId).contextId("ctx").status(status).generation(generation).build();
        TaskStatusUpdateEvent event = new TaskStatusUpdateEvent(taskId, status, "ctx", null, generation);
        return new CommittedTaskBatch(new TaskSnapshot(task, new TaskRevision(revision)), List.of(event));
    }

    private static WebhookDispatchLaneState awaitNext(InMemoryWebhookDispatchGate gate, String taskId, long next)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            WebhookDispatchLaneState state = gate.readLane(taskId).orElseThrow();
            if (state.nextGeneration() == next) {
                return state;
            }
            Thread.sleep(10);
        }
        return gate.readLane(taskId).orElseThrow();
    }

    private static final class GatedSender implements OrderedWebhookSender {
        private final Map<Long, CompletableFuture<Boolean>> results = new ConcurrentHashMap<>();
        private final Map<Long, CountDownLatch> starts = new ConcurrentHashMap<>();
        private final List<String> dispatchOrder = new CopyOnWriteArrayList<>();

        @Override
        public boolean supportsOrderedDispatch() {
            return true;
        }

        @Override
        public CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, long deadlineNanos,
                LongSupplier nanoTime) {
            long generation = batch.identity().firstGeneration();
            dispatchOrder.add("event:" + generation);
            started(generation).countDown();
            return results.computeIfAbsent(generation, ignored -> new CompletableFuture<>());
        }

        @Override
        public CompletionStage<Boolean> sendOrderedLegacyTaskSnapshot(Task snapshot, long deadlineNanos,
                LongSupplier nanoTime) {
            dispatchOrder.add("noop:" + snapshot.generation());
            return CompletableFuture.completedFuture(true);
        }

        CountDownLatch started(long generation) {
            return starts.computeIfAbsent(generation, ignored -> new CountDownLatch(1));
        }

        void complete(long generation) {
            results.computeIfAbsent(generation, ignored -> new CompletableFuture<>()).complete(true);
        }
    }
}
