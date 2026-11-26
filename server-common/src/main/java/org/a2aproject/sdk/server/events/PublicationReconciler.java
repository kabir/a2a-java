package org.a2aproject.sdk.server.events;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Duration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.tasks.TaskManager;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects a lost or stranded local publication for observers that are attached to a task, within a finite bound.
 *
 * <p>A task observer is one {@code ChildQueue}. {@link #attach(EventQueue)} installs a delivery gate on it
 * <em>before</em> the caller reads its authoritative snapshot at generation {@code G}. Events published while the
 * snapshot is read are held, those at or below {@code G} are discarded as covered, and only generations above
 * {@code G} are delivered, in order and without gaps. A late arrival at or below the delivered watermark is stale and
 * is discarded; a missing generation holds later ones back. Generations are counted on arrival at the observer, before
 * any older-protocol filtering, so a generation hidden from an older view never creates a false gap.</p>
 *
 * <p>A streaming send has no snapshot to replace such covered events, so {@link Attachment#completeWithoutSnapshot}
 * fails its observation instead of discarding them silently. This holds whether a covered event reaches the observer
 * before or after the attach is completed, because the two cases cannot be told apart from an event that was
 * committed before the tap: either way the stream never carried it. A redelivered generation above {@code G} is
 * still ignored, and nothing is added to a stream that has already delivered its final event.</p>
 *
 * <p>Gap detection cannot rely on another event arriving: a terminal mutation may commit and lose its publication with
 * no later mutation. Every {@link Limits#reconciliationInterval() interval} the reconciler therefore reads the
 * authoritative head generation once per active task (coalesced across that task's observers, each read bounded by
 * {@link Limits#authorityReadDeadline()}). An observer whose watermark is below the head and has not advanced for
 * {@link Limits#reorderGapDeadline()} is <em>stranded</em>. A held generation that is not filled within that deadline
 * is a reorder gap. Authority-read failure or timeout, and a buffer above {@link Limits#maxBufferedEvents()} events or
 * {@link Limits#maxBufferedBytes()} bytes (held plus delivered-but-unconsumed, so a consumer that stops demanding is
 * included), end only the affected observation.</p>
 *
 * <p>An affected observation is closed through the existing child-queue failure path: its consumer receives an
 * {@link InternalError} and the child closes. This means "retry or resynchronize with GetTask is needed"; it adds no
 * protocol error code, retryability guarantee, replay range, cursor or snapshot replacement. Recovery never mutates the
 * task, fails the task, sends a webhook or replicates anything, and other observers of the task are unaffected.</p>
 *
 * <h2>Bound</h2>
 * <p>With the defaults (5s interval, 5s read deadline, 10s reorder-gap deadline) a lost terminal publication is first
 * seen by the next scheduled read, at most {@code interval + read deadline} (about 10s) after the loss, and the
 * observation is closed by the first read at least {@code reorder-gap deadline} (10s) after that sighting, at most
 * another {@code interval + read deadline}. The worst case is therefore about {@code 2 x (interval + read deadline) +
 * gap deadline} (30s) plus scheduler delay; a read that never returns is abandoned at the next tick after its
 * deadline. This is an operational bound for a healthy scheduler, not an instantaneous repair and not a delivery
 * guarantee.</p>
 *
 * <p>The reconciler is opt-in and idle until an observation is attached. It starts its scheduler with the first
 * observation and releases it with the last one, so it holds no thread or timer for finished tasks.</p>
 */
public final class PublicationReconciler implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationReconciler.class);
    private static final int MAX_CONCURRENT_AUTHORITY_READS = 4;
    private static final int MAX_QUEUED_AUTHORITY_READS = 1024;
    private static final long NONE = Long.MIN_VALUE;

    /**
     * Finite recovery bounds. The values are SDK policy, not protocol requirements.
     *
     * @param reconciliationInterval delay between authority comparisons while a task has observers
     * @param authorityReadDeadline maximum wait for one authoritative head read
     * @param reorderGapDeadline how long a missing generation may stand before its observation is closed
     * @param maxBufferedEvents maximum events held or delivered but not yet consumed, per observation
     * @param maxBufferedBytes maximum encoded bytes of those events, per observation
     */
    public record Limits(Duration reconciliationInterval, Duration authorityReadDeadline,
            Duration reorderGapDeadline, int maxBufferedEvents, long maxBufferedBytes) {

        /** 5s interval, 5s read deadline, 10s reorder-gap deadline, 256 events and 4 MiB per observation. */
        public static final Limits DEFAULTS = new Limits(Duration.ofSeconds(5), Duration.ofSeconds(5),
                Duration.ofSeconds(10), 256, 4L * 1024 * 1024);

        public Limits {
            Assert.checkNotNullParam("reconciliationInterval", reconciliationInterval);
            Assert.checkNotNullParam("authorityReadDeadline", authorityReadDeadline);
            Assert.checkNotNullParam("reorderGapDeadline", reorderGapDeadline);
            if (reconciliationInterval.isNegative() || reconciliationInterval.isZero()
                    || authorityReadDeadline.isNegative() || authorityReadDeadline.isZero()
                    || reorderGapDeadline.isNegative() || reorderGapDeadline.isZero()
                    || maxBufferedEvents <= 0 || maxBufferedBytes <= 0) {
                throw new IllegalArgumentException("Every publication recovery bound must be positive");
            }
        }
    }

    /** Reads the authoritative head generation of a task without blocking the reconciler's scheduler. */
    @FunctionalInterface
    public interface AuthorityHead {

        /** Completes with the committed generation, or empty when the task does not exist. */
        CompletionStage<OptionalLong> read(String taskId);
    }

    /** An observation that has been installed on a child queue and awaits its authoritative snapshot. */
    public interface Attachment {

        /**
         * Completes the attach with the generation {@code G} of the snapshot read after {@link #attach}, which the
         * caller hands to the observer as its starting state. Held events at or below {@code G} are discarded as
         * covered by that snapshot; later ones are delivered in generation order.
         */
        void complete(long snapshotGeneration);

        /**
         * Completes the attach with the generation {@code G} read after {@link #attach} when the observer is
         * <em>not</em> given that state, so {@code G} only sets the delivery watermark. A concurrent committed event
         * at or below {@code G} that reached the observer after the tap would be discarded unseen with nothing to
         * replace it, so the observation is closed instead through the resynchronization failure of this class
         * ({@link InternalError}: call GetTask), also when such an event arrives after this call. Events above
         * {@code G} are delivered in generation order.
         */
        void completeWithoutSnapshot(long snapshotGeneration);

        /** Releases the observation and closes its queue, as when the first snapshot rejects the subscription. */
        void detach();
    }

    enum FailureReason {
        STRANDED_WATERMARK("the authoritative task is ahead of this stream and no publication arrived"),
        REORDER_GAP_DEADLINE("a missing generation did not arrive in time"),
        AUTHORITY_READ_FAILED("the authoritative task could not be read"),
        AUTHORITY_READ_DEADLINE("the authoritative read did not finish in time"),
        BUFFER_OVERFLOW("the stream buffer limit was exceeded"),
        COVERED_WITHOUT_SNAPSHOT("a concurrent publication was committed while the stream started and cannot be replaced");

        private final String description;

        FailureReason(String description) {
            this.description = description;
        }
    }

    private final Limits limits;
    private final AuthorityHead authorityHead;
    private final LongSupplier nanoClock;
    private final @Nullable ScheduledExecutorService externalScheduler;
    private final ToLongFunction<Event> sizer;
    private final Object registryLock = new Object();
    private final Map<String, TaskWatch> watches = new HashMap<>();
    private @Nullable ScheduledFuture<?> schedule;
    private @Nullable ScheduledThreadPoolExecutor ownedScheduler;
    private boolean closed;

    /** Creates a reconciler that reads heads through {@code authorityHead} on its own scheduler thread. */
    public PublicationReconciler(Limits limits, AuthorityHead authorityHead) {
        this(limits, authorityHead, System::nanoTime, null, PublicationReconciler::encodedSize);
    }

    PublicationReconciler(Limits limits, AuthorityHead authorityHead, LongSupplier nanoClock,
            @Nullable ScheduledExecutorService scheduler, ToLongFunction<Event> sizer) {
        this.limits = Assert.checkNotNullParam("limits", limits);
        this.authorityHead = Assert.checkNotNullParam("authorityHead", authorityHead);
        this.nanoClock = Assert.checkNotNullParam("nanoClock", nanoClock);
        this.externalScheduler = scheduler;
        this.sizer = Assert.checkNotNullParam("sizer", sizer);
    }

    /** Creates a reconciler with the default bounds that reads the head from {@code store}. */
    public static PublicationReconciler forTaskStore(TaskStore store) {
        return forTaskStore(store, Limits.DEFAULTS);
    }

    /**
     * Creates a reconciler that reads the head from {@code store} on a small bounded reader pool. A legacy-only
     * store has no canonical generation, so its observers are never judged stranded.
     */
    public static PublicationReconciler forTaskStore(TaskStore store, Limits limits) {
        return new PublicationReconciler(limits, new StoreAuthorityHead(Assert.checkNotNullParam("store", store)));
    }

    /**
     * Installs a delivery gate on a task observer's child queue. Call it after tapping the queue and <em>before</em>
     * reading the authoritative snapshot, then call {@link Attachment#complete(long)} with that snapshot's generation.
     *
     * @throws IllegalArgumentException if {@code queue} is not a child queue obtained from a task's main queue
     * @throws IllegalStateException if this reconciler is closed
     */
    public Attachment attach(EventQueue queue) {
        Assert.checkNotNullParam("queue", queue);
        if (!(queue instanceof EventQueue.ChildQueue child)) {
            throw new IllegalArgumentException("Only a child queue of a task's main queue can be observed");
        }
        Observation observation = new Observation(this, child);
        register(observation);
        child.installObservation(observation);
        return observation;
    }

    @Override
    public void close() {
        synchronized (registryLock) {
            closed = true;
            releaseScheduleLocked();
            watches.clear();
        }
        if (authorityHead instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception failure) {
                LOGGER.warn("Failed to close the publication authority reader", failure);
            }
        }
    }

    int activeTaskCount() {
        synchronized (registryLock) {
            return watches.size();
        }
    }

    int activeObservationCount() {
        synchronized (registryLock) {
            return watches.values().stream().mapToInt(watch -> watch.observations.size()).sum();
        }
    }

    boolean isScheduled() {
        synchronized (registryLock) {
            return schedule != null;
        }
    }

    /** Canonical generation of an item that is a position of a committed batch, or {@code 0}. */
    static long committedGeneration(EventQueueItem item) {
        return item instanceof LocalEventQueueItem local ? local.batchGeneration() : 0;
    }

    private void register(Observation observation) {
        synchronized (registryLock) {
            if (closed) {
                throw new IllegalStateException("The publication reconciler is closed");
            }
            TaskWatch watch = watches.computeIfAbsent(observation.taskId, TaskWatch::new);
            watch.observations.add(observation);
            try {
                ensureScheduledLocked();
            } catch (RuntimeException failure) {
                // A rejected schedule must not leave an observation that nothing will ever unregister.
                watch.observations.remove(observation);
                if (watch.observations.isEmpty()) {
                    watches.remove(observation.taskId);
                }
                throw failure;
            }
        }
    }

    private void unregister(Observation observation) {
        @Nullable CompletableFuture<OptionalLong> abandoned = null;
        synchronized (registryLock) {
            TaskWatch watch = watches.get(observation.taskId);
            if (watch == null) {
                return;
            }
            watch.observations.remove(observation);
            if (watch.observations.isEmpty()) {
                watches.remove(observation.taskId);
                abandoned = watch.abandonRead();
            }
            if (watches.isEmpty()) {
                releaseScheduleLocked();
            }
        }
        if (abandoned != null) {
            abandoned.cancel(true);
        }
    }

    private void ensureScheduledLocked() {
        if (schedule != null) {
            return;
        }
        ScheduledExecutorService scheduler = externalScheduler;
        ScheduledThreadPoolExecutor owned = null;
        if (scheduler == null) {
            owned = new ScheduledThreadPoolExecutor(1, daemonThreads("a2a-publication-reconciler"));
            owned.setRemoveOnCancelPolicy(true);
            scheduler = owned;
        }
        long interval = limits.reconciliationInterval().toNanos();
        try {
            schedule = scheduler.scheduleWithFixedDelay(this::tickGuarded, interval, interval, TimeUnit.NANOSECONDS);
        } catch (RuntimeException failure) {
            if (owned != null) {
                owned.shutdownNow();
            }
            throw failure;
        }
        ownedScheduler = owned;
    }

    private void releaseScheduleLocked() {
        ScheduledFuture<?> current = schedule;
        schedule = null;
        if (current != null) {
            current.cancel(false);
        }
        ScheduledThreadPoolExecutor owned = ownedScheduler;
        ownedScheduler = null;
        if (owned != null) {
            owned.shutdown();
        }
    }

    private void tickGuarded() {
        try {
            tick();
        } catch (Throwable failure) {
            // A failed tick must not cancel the periodic task and silently end recovery.
            LOGGER.error("Publication reconciliation tick failed", failure);
        }
    }

    /** One reconciliation pass; scheduled every interval and driven directly by tests with an injected clock. */
    void tick() {
        long now = nanoClock.getAsLong();
        List<TaskWatch> current;
        synchronized (registryLock) {
            current = List.copyOf(watches.values());
        }
        for (TaskWatch watch : current) {
            reconcile(watch, now);
        }
    }

    private void reconcile(TaskWatch watch, long now) {
        // A reorder gap needs no authority read: it ages on the clock alone.
        for (Observation observation : watch.observations) {
            FailureReason reason = observation.check(now, OptionalLong.empty());
            if (reason != null) {
                failQuietly(observation, reason);
            }
        }
        if (watch.observations.isEmpty()) {
            return;
        }
        CompletableFuture<OptionalLong> expired = null;
        synchronized (watch) {
            if (watch.inFlight != null) {
                if (now - watch.readStarted < limits.authorityReadDeadline().toNanos()) {
                    return;
                }
                expired = watch.inFlight;
                watch.inFlight = null;
            }
        }
        if (expired != null) {
            expired.cancel(true);
            failAll(watch, FailureReason.AUTHORITY_READ_DEADLINE);
            return;
        }
        CompletableFuture<OptionalLong> read;
        try {
            read = authorityHead.read(watch.taskId).toCompletableFuture();
        } catch (RuntimeException failure) {
            LOGGER.warn("Publication authority read for task {} failed to start", watch.taskId, failure);
            failAll(watch, FailureReason.AUTHORITY_READ_FAILED);
            return;
        }
        synchronized (watch) {
            watch.inFlight = read;
            watch.readStarted = now;
        }
        read.whenComplete((head, failure) -> onRead(watch, read, head, failure));
    }

    private void onRead(TaskWatch watch, CompletableFuture<OptionalLong> read, @Nullable OptionalLong head,
            @Nullable Throwable failure) {
        synchronized (watch) {
            if (watch.inFlight != read) {
                // Late completion of a read already abandoned at its deadline or with its last observer.
                return;
            }
            watch.inFlight = null;
        }
        if (failure != null || head == null) {
            LOGGER.warn("Publication authority read for task {} failed", watch.taskId, failure);
            failAll(watch, FailureReason.AUTHORITY_READ_FAILED);
            return;
        }
        long now = nanoClock.getAsLong();
        for (Observation observation : watch.observations) {
            FailureReason reason = observation.check(now, head);
            if (reason != null) {
                failQuietly(observation, reason);
            }
        }
    }

    private static void failAll(TaskWatch watch, FailureReason reason) {
        for (Observation observation : watch.observations) {
            failQuietly(observation, reason);
        }
    }

    /** One observation whose close throws must not stop the others of its task from being closed. */
    private static void failQuietly(Observation observation, FailureReason reason) {
        try {
            observation.fail(reason);
        } catch (RuntimeException failure) {
            LOGGER.warn("Closing the observation of task {} failed", observation.taskId, failure);
        }
    }

    private static long encodedSize(Event event) {
        try {
            return JsonUtil.toJson(event).getBytes(UTF_8).length;
        } catch (Exception encodingFailure) {
            return event.toString().getBytes(UTF_8).length;
        }
    }

    private static ThreadFactory daemonThreads(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Coalesces the authority read of one task across all of its observers. */
    private static final class TaskWatch {

        private final String taskId;
        private final Set<Observation> observations = new CopyOnWriteArraySet<>();
        private @Nullable CompletableFuture<OptionalLong> inFlight;
        private long readStarted;

        private TaskWatch(String taskId) {
            this.taskId = taskId;
        }

        private synchronized @Nullable CompletableFuture<OptionalLong> abandonRead() {
            CompletableFuture<OptionalLong> read = inFlight;
            inFlight = null;
            return read;
        }
    }

    /**
     * Reads the head generation on a bounded, idle-releasing reader pool. Reads beyond the thread count wait in a
     * bounded queue; a read that waits past its deadline is abandoned like any other slow read, and its queued work is
     * skipped.
     */
    private static final class StoreAuthorityHead implements AuthorityHead, AutoCloseable {

        private final TaskStore store;
        private final ExecutorService readers;

        private StoreAuthorityHead(TaskStore store) {
            this.store = store;
            ThreadPoolExecutor pool = new ThreadPoolExecutor(MAX_CONCURRENT_AUTHORITY_READS,
                    MAX_CONCURRENT_AUTHORITY_READS, 5, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(MAX_QUEUED_AUTHORITY_READS),
                    daemonThreads("a2a-publication-authority-read"));
            pool.allowCoreThreadTimeOut(true);
            this.readers = pool;
        }

        @Override
        public CompletionStage<OptionalLong> read(String taskId) {
            CompletableFuture<OptionalLong> result = new CompletableFuture<>();
            try {
                readers.execute(() -> {
                    if (result.isDone()) {
                        return;
                    }
                    try {
                        Task task = TaskManager.readAuthoritativeTask(store, taskId);
                        result.complete(task == null ? OptionalLong.empty() : OptionalLong.of(task.generation()));
                    } catch (Throwable failure) {
                        result.completeExceptionally(failure);
                    }
                });
            } catch (RejectedExecutionException saturated) {
                result.completeExceptionally(saturated);
            }
            return result;
        }

        @Override
        public void close() {
            readers.shutdownNow();
        }
    }

    /**
     * Delivery accounting and ordering gate for one observer. Only committed-batch positions are gated; errors,
     * control events and direct messages pass straight through, as they do for an unobserved child.
     *
     * <p>Lock order: this observation's lock may take the child's monitor (delivery), and the child's monitor may
     * reach only {@link #onChildClosed()}, which takes no observation lock.</p>
     */
    static final class Observation implements Attachment {

        private final PublicationReconciler owner;
        private final EventQueue.ChildQueue child;
        private final String taskId;
        private final AtomicBoolean childClosed = new AtomicBoolean();
        private final Object lock = new Object();
        private final TreeMap<Long, Held> held = new TreeMap<>();
        private final Map<EventQueueItem, Long> unconsumed = new IdentityHashMap<>();
        private boolean attached;
        private boolean ended;
        private boolean terminalSeen;
        private long watermark;
        /** Highest generation the observer was never given a snapshot for, or {@link #NONE} when it has a snapshot. */
        private long unseenThrough = NONE;
        private int bufferedEvents;
        private long bufferedBytes;
        private long gapSince = NONE;
        private long suspectSince = NONE;
        private long suspectWatermark;
        private @Nullable FailureReason failure;

        private Observation(PublicationReconciler owner, EventQueue.ChildQueue child) {
            this.owner = owner;
            this.child = child;
            this.taskId = child.taskId();
        }

        /** Delivery watermark: the highest generation delivered to the consumer queue or covered by the snapshot. */
        long watermark() {
            synchronized (lock) {
                return watermark;
            }
        }

        @Nullable FailureReason failureReason() {
            synchronized (lock) {
                return failure;
            }
        }

        int bufferedEvents() {
            synchronized (lock) {
                return bufferedEvents;
            }
        }

        long bufferedBytes() {
            synchronized (lock) {
                return bufferedBytes;
            }
        }

        /** Called on the thread that distributes an item to this child. */
        void admit(EventQueueItem item) {
            long generation = committedGeneration(item);
            if (generation <= 0) {
                child.offerToQueue(item);
                return;
            }
            long bytes = owner.sizer.applyAsLong(item.getEvent());
            @Nullable FailureReason overflow = null;
            boolean terminal = false;
            synchronized (lock) {
                if (ended || childClosed.get()) {
                    return;
                }
                if (attached && generation <= watermark) {
                    // Stale or duplicate: a later generation has already been accounted, so it is never re-delivered.
                    // Only a generation at or below the starting one was never accounted, and an observer without a
                    // snapshot would lose it, unless its stream has already ended with the final state.
                    if (generation > unseenThrough || terminalSeen) {
                        return;
                    }
                    overflow = FailureReason.COVERED_WITHOUT_SNAPSHOT;
                } else if (held.containsKey(generation)) {
                    // A redelivered held generation grows nothing, so it must not trip the buffer limit.
                    return;
                } else if (!tryChargeLocked(bytes)) {
                    overflow = FailureReason.BUFFER_OVERFLOW;
                } else {
                    held.put(generation, new Held(item, bytes));
                    if (attached) {
                        terminal = drainLocked();
                    }
                }
            }
            if (overflow != null) {
                fail(overflow);
            } else if (terminal) {
                owner.unregister(this);
            }
        }

        /** Called on the thread that consumes this child's queue. */
        void consumed(EventQueueItem item) {
            synchronized (lock) {
                Long bytes = unconsumed.remove(item);
                if (bytes != null) {
                    uncharge(bytes);
                }
            }
        }

        @Override
        public void complete(long snapshotGeneration) {
            complete(snapshotGeneration, true);
        }

        @Override
        public void completeWithoutSnapshot(long snapshotGeneration) {
            complete(snapshotGeneration, false);
        }

        private void complete(long snapshotGeneration, boolean snapshotReplacesCovered) {
            if (snapshotGeneration < 0) {
                throw new IllegalArgumentException("A snapshot generation must not be negative");
            }
            List<EventQueueItem> beforeInstall = child.takeQueuedCommittedItems();
            @Nullable FailureReason overflow = null;
            boolean terminal = false;
            synchronized (lock) {
                if (ended) {
                    return;
                }
                if (attached) {
                    throw new IllegalStateException("The observation is already attached");
                }
                if (!snapshotReplacesCovered && (!held.isEmpty() && held.firstKey() <= snapshotGeneration
                        || beforeInstall.stream().anyMatch(item -> committedGeneration(item) <= snapshotGeneration))) {
                    // The observer was never given the snapshot that covers this event, so dropping it would lose it.
                    overflow = FailureReason.COVERED_WITHOUT_SNAPSHOT;
                    beforeInstall = List.of();
                }
                for (EventQueueItem item : beforeInstall) {
                    long generation = committedGeneration(item);
                    if (generation <= snapshotGeneration || held.containsKey(generation)) {
                        continue;
                    }
                    long bytes = owner.sizer.applyAsLong(item.getEvent());
                    if (!tryChargeLocked(bytes)) {
                        overflow = FailureReason.BUFFER_OVERFLOW;
                        break;
                    }
                    held.put(generation, new Held(item, bytes));
                }
                if (overflow == null) {
                    attached = true;
                    watermark = snapshotGeneration;
                    if (!snapshotReplacesCovered) {
                        unseenThrough = snapshotGeneration;
                    }
                    suspectWatermark = snapshotGeneration;
                    while (!held.isEmpty() && held.firstKey() <= snapshotGeneration) {
                        uncharge(held.pollFirstEntry().getValue().bytes);
                    }
                    gapSince = NONE;
                    terminal = drainLocked();
                }
            }
            if (overflow != null) {
                fail(overflow);
            } else if (terminal) {
                owner.unregister(this);
            }
        }

        @Override
        public void detach() {
            child.close(true);
        }

        /** The child closed, whether by its consumer, a failure or the main queue: stop reconciling it. */
        void onChildClosed() {
            if (childClosed.compareAndSet(false, true)) {
                owner.unregister(this);
            }
        }

        /**
         * Judges this observation at {@code now}. {@code head} is empty when no authority read is being applied.
         *
         * @return the reason to close this observation, or {@code null} when it is healthy or already finished
         */
        @Nullable FailureReason check(long now, OptionalLong head) {
            long gapDeadline = owner.limits.reorderGapDeadline().toNanos();
            synchronized (lock) {
                if (ended || childClosed.get() || !attached || terminalSeen) {
                    return null;
                }
                if (!held.isEmpty() && gapSince != NONE && now - gapSince >= gapDeadline) {
                    return FailureReason.REORDER_GAP_DEADLINE;
                }
                if (head.isEmpty()) {
                    return null;
                }
                if (head.getAsLong() <= watermark || !held.isEmpty()) {
                    suspectSince = NONE;
                    return null;
                }
                if (suspectSince == NONE || suspectWatermark != watermark) {
                    suspectSince = now;
                    suspectWatermark = watermark;
                    return null;
                }
                return now - suspectSince >= gapDeadline ? FailureReason.STRANDED_WATERMARK : null;
            }
        }

        /** Closes only this observation through the child's failure path; the task and other observers are untouched. */
        void fail(FailureReason reason) {
            long atWatermark;
            synchronized (lock) {
                if (ended) {
                    return;
                }
                ended = true;
                failure = reason;
                atWatermark = watermark;
                held.clear();
                unconsumed.clear();
                bufferedEvents = 0;
                bufferedBytes = 0;
            }
            owner.unregister(this);
            LOGGER.warn("Closing the observation of task {} at generation {} without a task change: {}", taskId,
                    atWatermark, reason.description);
            child.terminateObservation(new InternalError("Task publication could not be confirmed ("
                    + reason.description + "). The stream was closed; call GetTask to resynchronize, then "
                    + "resubscribe if the task is not finished."), reason == FailureReason.BUFFER_OVERFLOW);
        }

        /** Releases every held item that is next in generation order. Must hold {@code lock}. */
        private boolean drainLocked() {
            boolean progressed = false;
            boolean terminal = false;
            while (!held.isEmpty() && held.firstKey() == watermark + 1) {
                Held next = held.pollFirstEntry().getValue();
                watermark = watermark + 1;
                unconsumed.put(next.item, next.bytes);
                child.offerToQueue(next.item);
                progressed = true;
                if (next.item.isFinalForLocalConsumption() || next.item.isCanonicalFinal()) {
                    terminalSeen = true;
                    terminal = true;
                }
            }
            if (held.isEmpty()) {
                gapSince = NONE;
            } else if (progressed || gapSince == NONE) {
                gapSince = owner.nanoClock.getAsLong();
            }
            if (progressed) {
                suspectSince = NONE;
            }
            return terminal;
        }

        private boolean tryChargeLocked(long bytes) {
            if (bufferedEvents + 1 > owner.limits.maxBufferedEvents()
                    || bufferedBytes + bytes > owner.limits.maxBufferedBytes()) {
                return false;
            }
            bufferedEvents++;
            bufferedBytes += bytes;
            return true;
        }

        private void uncharge(long bytes) {
            bufferedEvents--;
            bufferedBytes -= bytes;
        }
    }

    private record Held(EventQueueItem item, long bytes) {
    }
}
