package org.a2aproject.sdk.server.tasks;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.spec.Task;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded volatile admission in front of the shared per-task webhook cursor.
 *
 * <p>The gate is claimed before invoking a sender. A claim is completed only when the sender stage settles;
 * cancelling the returned observation future never releases it. Missing positions can be retired only through
 * the gate's conditional, loss-recording operation.</p>
 */
@ApplicationScoped
public class OrderedWebhookDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderedWebhookDispatcher.class);
    private static final long ATTEMPT_DEADLINE_NANOS = Duration.ofSeconds(30).toNanos();
    private static final long GAP_DEADLINE_NANOS = Duration.ofSeconds(10).toNanos();
    private static final long RETRY_DELAY_MILLIS = 100;
    private static final int MAX_PENDING_EVENTS = 256;
    private static final int MAX_PENDING_RECORDS = 256;
    private static final int MAX_ACTIVE_TASKS = 1024;
    private static final long MAX_PENDING_BYTES = 4L * 1024 * 1024;
    private static final long DROP_DIAGNOSTIC_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();

    private static final class Pending {
        long first;
        long last;
        final long sequence;
        final long writerEpoch;
        final boolean taskProposal;
        long enqueuedAt;
        long bytes;
        int eventCount;
        @Nullable CommittedTaskBatch batch;
        final @Nullable Task legacySummary;
        boolean dropped;
        final Admission admission;
        final CompletableFuture<Boolean> result = new CompletableFuture<>();

        Pending(long first, long last, long sequence, long writerEpoch, boolean taskProposal, long enqueuedAt,
                long bytes, int eventCount, @Nullable CommittedTaskBatch batch, @Nullable Task legacySummary, boolean dropped,
                Admission admission) {
            this.first = first;
            this.last = last;
            this.sequence = sequence;
            this.writerEpoch = writerEpoch;
            this.taskProposal = taskProposal;
            this.enqueuedAt = enqueuedAt;
            this.bytes = bytes;
            this.eventCount = eventCount;
            this.batch = batch;
            this.legacySummary = legacySummary;
            this.dropped = dropped;
            this.admission = admission;
        }

        boolean isNoop() {
            return legacySummary != null;
        }
    }

    /** Captures the decision time on the completing thread, before the lane scheduler observes the token. */
    private static final class Admission {
        final CompletableFuture<Boolean> decision;
        private final LongSupplier nanoTime;
        volatile long decidedAt = Long.MIN_VALUE;
        volatile boolean locallyRejected;

        Admission(CompletableFuture<Boolean> decision, LongSupplier nanoTime) {
            this.decision = decision;
            this.nanoTime = nanoTime;
            decision.whenComplete((ignored, failure) -> captureDecisionTime());
        }

        synchronized void captureDecisionTime() {
            if (decidedAt == Long.MIN_VALUE) {
                decidedAt = nanoTime.getAsLong();
            }
        }

        boolean awaitingDecisionObservation() {
            if (locallyRejected || decision.isCompletedExceptionally() || !decision.isDone()) {
                return !locallyRejected && !decision.isCompletedExceptionally();
            }
            return Boolean.TRUE.equals(decision.getNow(false)) && decidedAt == Long.MIN_VALUE;
        }

        void expireLocally() {
            locallyRejected = true;
            decision.complete(false);
        }

        boolean admitted(long enqueuedAt, long deadlineNanos) {
            if (locallyRejected || decision.isCompletedExceptionally()
                    || !Boolean.TRUE.equals(decision.getNow(false))) {
                return false;
            }
            long observedAt = decidedAt;
            if (observedAt == Long.MIN_VALUE || observedAt - enqueuedAt >= deadlineNanos) {
                locallyRejected = true;
                return false;
            }
            return true;
        }
    }

    private static final class TaskQueue {
        final List<Pending> pending = new ArrayList<>();
        final List<Pending> overflow = new ArrayList<>();
        @Nullable Pending activeOverflow;
        int pendingEvents;
        long pendingBytes;
        boolean running;
        boolean scheduled;
    }

    private final WebhookDispatchLaneStore gate;
    private final @Nullable OrderedWebhookSender sender;
    private final LongSupplier nanoTime;
    private final long attemptDeadlineNanos;
    private final long gapDeadlineNanos;
    private final int maxPendingEvents;
    private final long maxPendingBytes;
    private final int maxPendingRecords;
    private final int maxActiveTasks;
    private final ConcurrentMap<String, TaskQueue> queues = new ConcurrentHashMap<>();
    private final ConcurrentMap<CommittedTaskBatch.Identity, CompletableFuture<Boolean>> activeBatches =
            new ConcurrentHashMap<>();
    private final Object completedBatchLock = new Object();
    private final LinkedHashMap<CommittedTaskBatch.Identity, Boolean> completedBatches = new LinkedHashMap<>(
            256, 0.75f, true);
    private final AtomicInteger activeQueues = new AtomicInteger();
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicLong lastDropDiagnosticNanos = new AtomicLong(Long.MIN_VALUE);
    private final ScheduledThreadPoolExecutor scheduler;

    /** No-arg constructor for CDI proxies. */
    @SuppressWarnings("NullAway")
    protected OrderedWebhookDispatcher() {
        this.gate = null;
        this.sender = null;
        this.nanoTime = System::nanoTime;
        this.attemptDeadlineNanos = ATTEMPT_DEADLINE_NANOS;
        this.gapDeadlineNanos = GAP_DEADLINE_NANOS;
        this.maxPendingEvents = MAX_PENDING_EVENTS;
        this.maxPendingBytes = MAX_PENDING_BYTES;
        this.maxPendingRecords = MAX_PENDING_RECORDS;
        this.maxActiveTasks = MAX_ACTIVE_TASKS;
        this.scheduler = newScheduler();
    }

    @Inject
    public OrderedWebhookDispatcher(WebhookDispatchLaneStore gate, PushNotificationSender pushSender) {
        this(gate, orderedSenderOrNull(pushSender),
                System::nanoTime, ATTEMPT_DEADLINE_NANOS, GAP_DEADLINE_NANOS,
                MAX_PENDING_EVENTS, MAX_PENDING_BYTES, MAX_PENDING_RECORDS, MAX_ACTIVE_TASKS);
        if (gate.isSharedStorage() && sender == null) {
            throw new IllegalStateException("Shared ordered webhook dispatch requires a sender adapter that "
                    + "reports completion only after its outbound work has settled or become quiescent");
        }
    }

    private static @Nullable OrderedWebhookSender orderedSenderOrNull(PushNotificationSender pushSender) {
        try {
            return pushSender.supportsOrderedDispatch() ? pushSender : null;
        } catch (RuntimeException | Error capabilityFailure) {
            LOGGER.error("Could not inspect ordered webhook sender capability", capabilityFailure);
            return null;
        }
    }

    OrderedWebhookDispatcher(WebhookDispatchLaneStore gate, @Nullable OrderedWebhookSender sender, LongSupplier nanoTime,
            long attemptDeadlineNanos, long gapDeadlineNanos, int maxPendingEvents, long maxPendingBytes,
            int maxPendingRecords) {
        this(gate, sender, nanoTime, attemptDeadlineNanos, gapDeadlineNanos, maxPendingEvents,
                maxPendingBytes, maxPendingRecords, MAX_ACTIVE_TASKS);
    }

    OrderedWebhookDispatcher(WebhookDispatchLaneStore gate, @Nullable OrderedWebhookSender sender, LongSupplier nanoTime,
            long attemptDeadlineNanos, long gapDeadlineNanos, int maxPendingEvents, long maxPendingBytes,
            int maxPendingRecords, int maxActiveTasks) {
        this.gate = gate;
        this.sender = sender;
        this.nanoTime = nanoTime;
        this.attemptDeadlineNanos = attemptDeadlineNanos;
        this.gapDeadlineNanos = gapDeadlineNanos;
        this.maxPendingEvents = maxPendingEvents;
        this.maxPendingBytes = maxPendingBytes;
        this.maxPendingRecords = maxPendingRecords;
        this.maxActiveTasks = maxActiveTasks;
        this.scheduler = newScheduler();
    }

    /** Whether the configured sender supplied the ordered, quiescent adapter required by this dispatcher. */
    public boolean supportsOrderedDispatch() {
        return sender != null;
    }

    /** The wiring facts relevant to authority-topology validation. */
    public TaskAuthorityCapabilities.WebhookDispatch topology() {
        return new TaskAuthorityCapabilities.WebhookDispatch(true, sender != null, gate.isSharedStorage());
    }

    /** Records a dropped known batch without moving the durable cursor or retaining its payload. */
    public boolean recordAdmissionLoss(CommittedTaskBatch batch, @Nullable Long writerEpoch, String reason) {
        List<Long> positions = batch.generationPositions();
        long first = positions.get(0);
        long last = positions.get(positions.size() - 1);
        if (first < 1 || last < first || !isContiguous(positions)) {
            return false;
        }
        Long originatingEpoch = writerEpoch;
        if (originatingEpoch == null) {
            if (gate.isSharedStorage()) {
                LOGGER.warn("Cannot record dropped shared webhook batch {} without its originating writer epoch",
                        batch.identity());
                return false;
            }
            WebhookDispatchLanePosition lane = gate.readPosition(batch.taskId()).orElse(null);
            if (lane == null) {
                return false;
            }
            originatingEpoch = lane.writerEpoch();
        }
        return gate.recordAdmissionLoss(batch.taskId(), originatingEpoch, first, last, reason);
    }

    /** Admit every canonical event position in this committed batch. */
    public CompletionStage<Boolean> dispatch(CommittedTaskBatch batch) {
        return dispatch(batch, false, null, CompletableFuture.completedFuture(true));
    }

    /** Reserves bounded payload capacity before callback admission; the future is only a release token. */
    public CompletionStage<Boolean> dispatch(CommittedTaskBatch batch, CompletableFuture<Boolean> admission) {
        return dispatch(batch, false, null, admission);
    }

    /** Reserves one committed batch using trusted local provenance, without changing its wire representation. */
    public CompletionStage<Boolean> dispatch(CommittedTaskBatch batch, boolean taskProposal,
            @Nullable Long writerEpoch, CompletableFuture<Boolean> admission) {
        CommittedTaskBatch.Identity identity = batch.identity();
        CompletableFuture<Boolean> active = activeBatches.get(identity);
        if (active != null) {
            return active;
        }
        synchronized (completedBatchLock) {
            Boolean completed = completedBatches.get(identity);
            if (completed != null) {
                return CompletableFuture.completedFuture(completed);
            }
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        active = activeBatches.putIfAbsent(identity, result);
        if (active != null) {
            return active;
        }
        List<Long> positions = batch.generationPositions();
        long first = positions.get(0);
        long last = positions.get(positions.size() - 1);
        if (first < 1 || last < first || !isContiguous(positions)) {
            result.completeExceptionally(new IllegalArgumentException(
                    "Ordered webhook batches require contiguous canonical generations"));
            rememberCompletedBatch(identity, false);
            activeBatches.remove(identity, result);
            return result;
        }
        String encoded;
        try {
            encoded = JsonUtil.toJson(batch);
        } catch (Exception encodingFailure) {
            encoded = batch.toString();
        }
        long bytes = encoded.getBytes(StandardCharsets.UTF_8).length;
        boolean dropPayload = positions.size() > maxPendingEvents || bytes > maxPendingBytes;
        if (writerEpoch == null && gate.isSharedStorage()) {
            result.completeExceptionally(new IllegalStateException(
                    "Shared webhook dispatch requires the originating writer epoch"));
            activeBatches.remove(identity, result);
            return result;
        }
        Long originatingEpoch = writerEpoch;
        if (originatingEpoch == null) {
            try {
                WebhookDispatchLanePosition lane = gate.readPosition(batch.taskId()).orElse(null);
                if (lane == null) {
                    throw new IllegalStateException("Webhook lane for task " + batch.taskId()
                            + " was not initialized with task creation or migration");
                }
                originatingEpoch = lane.writerEpoch();
            } catch (RuntimeException gateFailure) {
                result.completeExceptionally(gateFailure);
                activeBatches.remove(identity, result);
                rememberCompletedBatch(identity, false);
                return result;
            }
        }
        Admission admissionDecision = new Admission(admission, nanoTime);
        CompletionStage<Boolean> admitted;
        try {
            admitted = enqueue(batch.taskId(), new Pending(first, last, sequence.getAndIncrement(), originatingEpoch,
                    taskProposal, nanoTime.getAsLong(), dropPayload ? 0 : bytes, dropPayload ? 0 : positions.size(),
                    dropPayload ? null : batch, null, dropPayload, admissionDecision));
        } catch (RuntimeException gateFailure) {
            result.completeExceptionally(gateFailure);
            activeBatches.remove(identity, result);
            rememberCompletedBatch(identity, false);
            return result;
        }
        admitted.whenComplete((sent, failure) -> {
            if (failure == null) {
                result.complete(sent);
            } else {
                result.completeExceptionally(failure);
            }
            activeBatches.remove(identity, result);
            rememberCompletedBatch(identity, failure == null && Boolean.TRUE.equals(sent));
        });
        return result;
    }

    private void rememberCompletedBatch(CommittedTaskBatch.Identity identity, boolean result) {
        synchronized (completedBatchLock) {
            completedBatches.put(identity, result);
            while (completedBatches.size() > 2048) {
                Map.Entry<CommittedTaskBatch.Identity, Boolean> oldest = completedBatches.entrySet().iterator().next();
                completedBatches.remove(oldest.getKey());
            }
        }
    }

    /**
     * Admit a delivery-only older-protocol Task summary at the zero-width boundary after its observed generation.
     * The lane cursor is not advanced by this operation.
     */
    public CompletionStage<Boolean> dispatchLegacySummary(Task snapshot) {
        return dispatchLegacySummary(snapshot, null);
    }

    /** Admit a zero-width summary while retaining the epoch of the mutation that produced its snapshot. */
    public CompletionStage<Boolean> dispatchLegacySummary(Task snapshot, @Nullable Long writerEpoch) {
        long capturedEpoch;
        if (writerEpoch == null) {
            if (gate.isSharedStorage()) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Shared webhook dispatch requires the originating writer epoch"));
            }
            WebhookDispatchLanePosition lane = gate.readPosition(snapshot.id()).orElse(null);
            if (lane == null) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Webhook lane for task " + snapshot.id()
                                + " was not initialized with task creation or migration"));
            }
            capturedEpoch = lane.writerEpoch();
        } else {
            capturedEpoch = writerEpoch;
        }
        long expectedNext = Math.max(1, snapshot.generation() + 1);
        String encoded;
        try {
            encoded = JsonUtil.toJson(snapshot);
        } catch (Exception encodingFailure) {
            encoded = snapshot.toString();
        }
        long bytes = encoded.getBytes(StandardCharsets.UTF_8).length;
        return enqueue(snapshot.id(), new Pending(expectedNext, expectedNext - 1, sequence.getAndIncrement(),
                capturedEpoch, false, nanoTime.getAsLong(), bytes, 1, null, snapshot, false,
                new Admission(CompletableFuture.completedFuture(true), nanoTime)));
    }

    private CompletionStage<Boolean> enqueue(String taskId, Pending item) {
        if (sender == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "The configured PushNotificationSender does not implement OrderedWebhookSender"));
        }
        while (true) {
            TaskQueue taskQueue = queues.get(taskId);
            if (taskQueue == null) {
                if (activeQueues.incrementAndGet() > maxActiveTasks) {
                    activeQueues.decrementAndGet();
                    recordAdmissionLoss(item, taskId, "active task queue capacity exhausted before admission");
                    item.result.complete(false);
                    return CompletableFuture.completedFuture(false);
                }
                TaskQueue created = new TaskQueue();
                if (queues.putIfAbsent(taskId, created) == null) {
                    taskQueue = created;
                } else {
                    activeQueues.decrementAndGet();
                    continue;
                }
            }
            boolean sparseCapacityExhausted = false;
            synchronized (taskQueue) {
                if (queues.get(taskId) != taskQueue) {
                    continue;
                }
                Pending mergeableOverflow = null;
                for (Pending marker : taskQueue.overflow) {
                    if (canMerge(taskQueue, marker, item)) {
                        mergeableOverflow = marker;
                        break;
                    }
                }
                if (mergeableOverflow != null && !item.isNoop()) {
                    mergeOverflow(mergeableOverflow, item);
                    item.result.complete(false);
                    schedule(taskId, taskQueue, 0);
                    return item.result;
                }
                if (taskQueue.pending.size() >= maxPendingRecords) {
                    if (item.isNoop()) {
                        item.result.complete(false);
                        return item.result;
                    }
                    // Keep one compact loss marker outside the bounded payload-record list so saturation itself
                    // is represented even when an earlier claimed range prevents immediate retirement.
                    if (taskQueue.overflow.size() < maxPendingRecords) {
                        taskQueue.overflow.add(overflowMarker(item));
                        taskQueue.overflow.sort(PENDING_ORDER);
                    } else {
                        sparseCapacityExhausted = true;
                    }
                    if (!sparseCapacityExhausted) {
                        item.result.complete(false);
                        schedule(taskId, taskQueue, 0);
                        return item.result;
                    }
                } else {
                    boolean overflow = item.dropped || taskQueue.pendingEvents + item.eventCount > maxPendingEvents
                            || taskQueue.pendingBytes + item.bytes > maxPendingBytes;
                    Pending admitted = overflow && !item.dropped && !item.isNoop()
                            ? new Pending(item.first, item.last, item.sequence, item.writerEpoch, item.taskProposal,
                                    item.enqueuedAt, 0, 0, null, null, true, item.admission)
                            : item;
                    if (overflow && item.isNoop()) {
                        item.result.complete(false);
                        return item.result;
                    }
                    if (!admitted.dropped) {
                        taskQueue.pendingEvents += admitted.eventCount;
                        taskQueue.pendingBytes += admitted.bytes;
                    }
                    if (admitted != item) {
                        CompletableFuture<Boolean> itemResult = item.result;
                        admitted.result.whenComplete((sent, failure) -> {
                            if (failure == null) {
                                itemResult.complete(sent);
                            } else {
                                itemResult.completeExceptionally(failure);
                            }
                        });
                    }
                    taskQueue.pending.add(admitted);
                    taskQueue.pending.sort(PENDING_ORDER);
                }
            }
            if (sparseCapacityExhausted) {
                recordAdmissionLoss(item, taskId, "sparse loss marker capacity exhausted");
                item.result.complete(false);
                return item.result;
            }
            schedule(taskId, taskQueue, 0);
            TaskQueue admittedQueue = taskQueue;
            // The bridge holds only the admission token, never the Pending or its batch payload.
            Admission admissionObservation = item.admission;
            CompletableFuture<Boolean> admissionToken = admissionObservation.decision;
            admissionToken.whenComplete((admitted, failure) -> {
                admissionObservation.captureDecisionTime();
                schedule(taskId, admittedQueue, 0);
            });
            return item.result;
        }
    }

    private void recordAdmissionLoss(Pending item, String taskId, String reason) {
        if (item.isNoop()) {
            logDrop(taskId, item.first, item.last, reason);
            return;
        }
        try {
            if (!gate.recordAdmissionLoss(taskId, item.writerEpoch, item.first, item.last, reason)) {
                LOGGER.warn("Could not record dropped webhook admission for task {} generations {}..{}",
                        taskId, item.first, item.last);
            }
        } catch (RuntimeException | Error gateFailure) {
            LOGGER.error("Could not record dropped webhook admission for task {} generations {}..{}",
                    taskId, item.first, item.last, gateFailure);
        }
        logDrop(taskId, item.first, item.last, reason);
    }

    private static final Comparator<Pending> PENDING_ORDER = Comparator.comparingLong((Pending pending) -> pending.first)
            .thenComparingLong(pending -> pending.sequence);

    private void schedule(String taskId, TaskQueue queue, long delayMillis) {
        synchronized (queue) {
            if (queue.running || queue.scheduled) {
                return;
            }
            queue.scheduled = true;
        }
        try {
            scheduler.schedule(() -> {
                synchronized (queue) {
                    queue.scheduled = false;
                    if (queue.running) {
                        return;
                    }
                    queue.running = true;
                }
                try {
                    processNext(taskId, queue);
                } catch (RuntimeException | Error gateFailure) {
                    LOGGER.error("Ordered webhook gate failed for task {}; clearing local queue", taskId,
                            gateFailure);
                    failQueue(taskId, queue, gateFailure);
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException rejected) {
            synchronized (queue) {
                queue.scheduled = false;
            }
            LOGGER.error("Ordered webhook dispatcher rejected task {} admission work", taskId, rejected);
            failQueue(taskId, queue, rejected);
        }
    }

    private void processNext(String taskId, TaskQueue queue) {
        Pending next;
        synchronized (queue) {
            Pending firstPending = queue.pending.stream().min(PENDING_ORDER).orElse(null);
            Pending firstOverflow = queue.overflow.stream().min(PENDING_ORDER).orElse(null);
            if (firstOverflow != null && (firstPending == null
                    || PENDING_ORDER.compare(firstOverflow, firstPending) < 0)) {
                queue.activeOverflow = firstOverflow;
                queue.overflow.remove(firstOverflow);
                next = firstOverflow;
            } else if (firstPending != null) {
                next = firstPending;
            } else {
                queue.running = false;
                if (queues.remove(taskId, queue)) {
                    activeQueues.decrementAndGet();
                }
                return;
            }
        }
        if (next.admission.awaitingDecisionObservation()) {
            if (nanoTime.getAsLong() - next.enqueuedAt >= gapDeadlineNanos) {
                next.admission.expireLocally();
            }
            if (next.admission.awaitingDecisionObservation()) {
                waitForAdmission(taskId, queue, next);
                return;
            }
        }
        boolean admitted = next.admission.admitted(next.enqueuedAt, gapDeadlineNanos);
        if (!admitted && !next.dropped) {
            if (next.isNoop()) {
                finish(taskId, queue, next, false);
                return;
            }
            synchronized (queue) {
                queue.pendingEvents -= next.eventCount;
                queue.pendingBytes -= next.bytes;
                next.eventCount = 0;
                next.bytes = 0;
                next.batch = null;
                next.dropped = true;
            }
            scheduleAgain(taskId, queue, 0, queue.activeOverflow == next ? next : null);
            return;
        }
        WebhookDispatchLanePosition lane = gate.readPosition(taskId).orElse(null);
        if (lane == null) {
            finish(taskId, queue, next, false);
            return;
        }
        if (lane.writerEpoch() > next.writerEpoch) {
            logDrop(taskId, next.first, next.last, "queued batch belongs to a stale writer epoch");
            finish(taskId, queue, next, false);
            return;
        }
        if (lane.writerEpoch() < next.writerEpoch) {
            if (lane.holderToken() != null) {
                scheduleAgain(taskId, queue, RETRY_DELAY_MILLIS, queue.activeOverflow == next ? next : null);
                return;
            }
            if (gate.rebaseUnclaimedLane(taskId, lane.writerEpoch(), next.writerEpoch)) {
                scheduleAgain(taskId, queue, 0, queue.activeOverflow == next ? next : null);
            } else {
                scheduleAgain(taskId, queue, RETRY_DELAY_MILLIS, queue.activeOverflow == next ? next : null);
            }
            return;
        }

        if (next.isNoop()) {
            long expectedNext = next.first;
            if (lane.nextGeneration() > expectedNext) {
                finish(taskId, queue, next, false);
                return;
            }
            if (lane.nextGeneration() < expectedNext) {
                if (nanoTime.getAsLong() - next.enqueuedAt >= gapDeadlineNanos) {
                    logDrop(taskId, next.first, next.last, "orphan legacy summary expired without canonical input");
                    finish(taskId, queue, next, false);
                    return;
                }
                scheduleAgain(taskId, queue, RETRY_DELAY_MILLIS);
                return;
            }
            OrderedWebhookSender orderedSender = sender;
            Task legacySummary = next.legacySummary;
            if (orderedSender == null || legacySummary == null) {
                finish(taskId, queue, next, false);
                return;
            }
            var claim = gate.claimNoop(taskId, next.writerEpoch, expectedNext);
            if (claim.isEmpty()) {
                if (gate.readPosition(taskId).map(state -> state.holderToken() != null).orElse(false)) {
                    scheduleAgain(taskId, queue, RETRY_DELAY_MILLIS);
                } else {
                    finish(taskId, queue, next, false);
                }
                return;
            }
            long deadline = nanoTime.getAsLong() + attemptDeadlineNanos;
            CompletionStage<Boolean> dispatch;
            try {
                dispatch = orderedSender.sendOrderedLegacyTaskSnapshot(legacySummary, deadline, nanoTime,
                        java.util.function.UnaryOperator.identity());
            } catch (RuntimeException failure) {
                dispatch = CompletableFuture.failedFuture(failure);
            }
            dispatch.whenComplete((sent, failure) -> {
                try {
                    boolean settled = gate.releaseNoop(claim.get());
                    finish(taskId, queue, next, settled && failure == null && Boolean.TRUE.equals(sent));
            } catch (RuntimeException | Error gateFailure) {
                    LOGGER.error("Could not release zero-width webhook claim for task {}; retaining durable state",
                            taskId, gateFailure);
                    finish(taskId, queue, next, false);
                }
            });
            return;
        }

        if (next.first < lane.nextGeneration()) {
            if (next.dropped && next.last >= lane.nextGeneration()) {
                next.first = lane.nextGeneration();
            } else {
                finish(taskId, queue, next, false);
                return;
            }
        }
        if (next.first > lane.nextGeneration()) {
            if (lane.holderToken() != null) {
                scheduleAgain(taskId, queue, RETRY_DELAY_MILLIS, next);
                return;
            }
            long elapsed = nanoTime.getAsLong() - next.enqueuedAt;
            if (elapsed < gapDeadlineNanos) {
                long remainingMillis = Math.max(1,
                        TimeUnit.NANOSECONDS.toMillis(gapDeadlineNanos - elapsed));
                scheduleAgain(taskId, queue, Math.min(RETRY_DELAY_MILLIS, remainingMillis), next);
                return;
            }
            boolean retired = gate.retireUnclaimedGap(taskId, next.writerEpoch,
                    lane.nextGeneration(), next.first - 1);
            if (retired) {
                logDrop(taskId, lane.nextGeneration(), next.first - 1, "unclaimed generation gap");
            }
            scheduleAgain(taskId, queue, retired ? 0 : RETRY_DELAY_MILLIS, next);
            return;
        }

        if (next.dropped) {
            boolean retired = gate.retireUnclaimedGap(taskId, next.writerEpoch, next.first, next.last);
            if (retired) {
                logDrop(taskId, next.first, next.last, "pending webhook capacity exceeded");
                finish(taskId, queue, next, false);
            } else {
                scheduleAgain(taskId, queue, RETRY_DELAY_MILLIS, next);
            }
            return;
        }

        OrderedWebhookSender orderedSender = sender;
        CommittedTaskBatch committedBatch = next.batch;
        if (orderedSender == null || committedBatch == null) {
            finish(taskId, queue, next, false);
            return;
        }
        var claim = gate.claim(taskId, next.writerEpoch, next.first, next.last);
        if (claim.isEmpty()) {
            WebhookDispatchLanePosition refreshed = gate.readPosition(taskId).orElse(null);
            if (refreshed != null && refreshed.holderToken() != null) {
                scheduleAgain(taskId, queue, RETRY_DELAY_MILLIS);
            } else {
                finish(taskId, queue, next, false);
            }
            return;
        }
        long deadline = nanoTime.getAsLong() + attemptDeadlineNanos;
        CompletionStage<Boolean> dispatch;
        try {
            dispatch = orderedSender.sendOrderedBatch(committedBatch, next.taskProposal, deadline, nanoTime,
                    java.util.function.UnaryOperator.identity());
        } catch (RuntimeException failure) {
            dispatch = CompletableFuture.failedFuture(failure);
        }
        dispatch.whenComplete((sent, failure) -> {
            try {
                boolean expired = nanoTime.getAsLong() >= deadline;
                boolean completed;
                if (failure != null || !Boolean.TRUE.equals(sent) || expired) {
                    String reason = expired ? "ordered webhook attempt deadline elapsed; delivery may be partial"
                            : "ordered webhook sender reported a settled unsuccessful attempt";
                    completed = gate.completeWithLoss(claim.get(), reason);
                    if (completed) {
                        logDrop(taskId, next.first, next.last, reason);
                    }
                } else {
                    completed = gate.complete(claim.get());
                }
                finish(taskId, queue, next,
                        completed && failure == null && Boolean.TRUE.equals(sent) && !expired);
            } catch (RuntimeException | Error gateFailure) {
                LOGGER.error("Could not settle webhook claim for task {}; retaining durable ownership",
                        taskId, gateFailure);
                finish(taskId, queue, next, false);
            }
        });
    }

    private void scheduleAgain(String taskId, TaskQueue queue, long delayMillis) {
        scheduleAgain(taskId, queue, delayMillis, null);
    }

    private void waitForAdmission(String taskId, TaskQueue queue, Pending pending) {
        synchronized (queue) {
            if (queue.activeOverflow == pending) {
                queue.activeOverflow = null;
                queue.overflow.add(pending);
                queue.overflow.sort(PENDING_ORDER);
            }
            queue.running = false;
        }
        // Recheck after clearing running: admission may have completed between the first probe and this point.
        long elapsed = nanoTime.getAsLong() - pending.enqueuedAt;
        if (elapsed >= gapDeadlineNanos) {
            pending.admission.expireLocally();
        }
        long waitMillis = pending.admission.awaitingDecisionObservation()
                ? Math.min(RETRY_DELAY_MILLIS, Math.max(1, TimeUnit.NANOSECONDS.toMillis(gapDeadlineNanos - elapsed)))
                : 0;
        schedule(taskId, queue, waitMillis);
    }

    private void scheduleAgain(String taskId, TaskQueue queue, long delayMillis, @Nullable Pending requeueOverflow) {
        synchronized (queue) {
            if (requeueOverflow != null && queue.activeOverflow == requeueOverflow) {
                queue.activeOverflow = null;
                Pending mergeable = queue.overflow.stream()
                        .filter(marker -> canMerge(queue, marker, requeueOverflow)).findFirst().orElse(null);
                if (mergeable == null) {
                    queue.overflow.add(requeueOverflow);
                    queue.overflow.sort(PENDING_ORDER);
                } else {
                    mergeOverflow(mergeable, requeueOverflow);
                }
            }
            queue.running = false;
        }
        schedule(taskId, queue, delayMillis);
    }

    private void finish(String taskId, TaskQueue queue, Pending item, boolean result) {
        synchronized (queue) {
            if (queue.activeOverflow == item) {
                queue.activeOverflow = null;
            } else {
                queue.overflow.remove(item);
            }
            if (!queue.pending.isEmpty() && queue.pending.get(0) == item) {
                queue.pending.remove(0);
            } else if (queue.pending.contains(item)) {
                queue.pending.remove(item);
            }
            if (!item.dropped) {
                queue.pendingEvents -= item.eventCount;
                queue.pendingBytes -= item.bytes;
            }
            queue.running = false;
        }
        item.result.complete(result);
        schedule(taskId, queue, 0);
    }

    private void failQueue(String taskId, TaskQueue queue, Throwable failure) {
        List<Pending> pending;
        synchronized (queue) {
            if (queues.remove(taskId, queue)) {
                activeQueues.decrementAndGet();
            }
            List<Pending> failed = new ArrayList<>(queue.pending);
            failed.addAll(queue.overflow);
            queue.overflow.clear();
            if (queue.activeOverflow != null) {
                failed.add(queue.activeOverflow);
                queue.activeOverflow = null;
            }
            pending = List.copyOf(failed);
            queue.pending.clear();
            queue.running = false;
        }
        pending.forEach(item -> item.result.completeExceptionally(failure));
    }

    private static boolean isContiguous(List<Long> positions) {
        for (int index = 1; index < positions.size(); index++) {
            if (positions.get(index) != positions.get(index - 1) + 1) {
                return false;
            }
        }
        return true;
    }

    private static Pending overflowMarker(Pending item) {
        return new Pending(item.first, item.last, item.sequence, item.writerEpoch, item.taskProposal,
                item.enqueuedAt, 0, 0, null, null, true, item.admission);
    }

    private static boolean canMerge(TaskQueue queue, Pending left, Pending right) {
        long first = Math.min(left.first, right.first);
        long last = Math.max(left.last, right.last);
        if (left.writerEpoch != right.writerEpoch || left.isNoop() || right.isNoop()
                || right.first > left.last + 1 || left.first > right.last + 1) {
            return false;
        }
        for (Pending accepted : queue.pending) {
            if (!accepted.dropped && !accepted.isNoop()
                    && first <= accepted.last && accepted.first <= last) {
                return false;
            }
        }
        return true;
    }

    private static void mergeOverflow(Pending overflow, Pending item) {
        overflow.first = Math.min(overflow.first, item.first);
        overflow.last = Math.max(overflow.last, item.last);
        overflow.enqueuedAt = Math.min(overflow.enqueuedAt, item.enqueuedAt);
    }

    private void logDrop(String taskId, long first, long last, String reason) {
        long now = nanoTime.getAsLong();
        while (true) {
            long previous = lastDropDiagnosticNanos.get();
            if (previous != Long.MIN_VALUE && now - previous < DROP_DIAGNOSTIC_INTERVAL_NANOS) {
                return;
            }
            if (lastDropDiagnosticNanos.compareAndSet(previous, now)) {
                LOGGER.warn("Ordered webhook range {}..{} for task {} was retired or dropped: {}",
                        first, last, taskId, reason);
                return;
            }
        }
    }

    private static ScheduledThreadPoolExecutor newScheduler() {
        AtomicInteger threadIds = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "a2a-webhook-lane-" + threadIds.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2, factory);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    @PreDestroy
    public void close() {
        // Do not interrupt active HTTP work: a claimed lane remains owned until the worker is demonstrably done.
        scheduler.shutdown();
    }
}
