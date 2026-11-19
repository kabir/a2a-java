package org.a2aproject.sdk.server.tasks;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.events.EventQueue;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Admits executor proposals and retains their correlated in-process completion. */
public final class ExecutorSubmissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorSubmissionService.class);
    public static final Duration DEFAULT_ADMISSION_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_RESULT_OBSERVATION_TIMEOUT = Duration.ofSeconds(30);
    static final int MAX_RETAINED_SUBMISSIONS = 1024;
    static final Duration RESULT_RETENTION = Duration.ofMinutes(5);

    private static final Executor CALLBACK_DISPATCHER = Executors.newCachedThreadPool(daemonThreadFactory());
    private static final ScheduledExecutorService TIMEOUTS = Executors.newScheduledThreadPool(4,
            daemonThreadFactory());

    private final ProposalAuthority authority;
    private final Duration admissionTimeout;
    private final Duration observationTimeout;
    private final @Nullable RequestContext openingContext;
    private final int maxRetainedSubmissions;
    private final long resultRetentionNanos;
    private final LongSupplier nanoClock;
    private final Runnable afterPruneCompletionSnapshot;
    private final ConcurrentHashMap<String, Submission> submissions = new ConcurrentHashMap<>();

    public ExecutorSubmissionService(ProposalAuthority authority) {
        this(authority, DEFAULT_ADMISSION_TIMEOUT, DEFAULT_RESULT_OBSERVATION_TIMEOUT);
    }

    public ExecutorSubmissionService(ProposalAuthority authority, Duration admissionTimeout,
            Duration observationTimeout) {
        this(authority, null, admissionTimeout, observationTimeout);
    }

    public ExecutorSubmissionService(ProposalAuthority authority,
            @Nullable RequestContext openingContext,
            Duration admissionTimeout, Duration observationTimeout) {
        this(authority, openingContext, admissionTimeout, observationTimeout,
                MAX_RETAINED_SUBMISSIONS, RESULT_RETENTION, System::nanoTime);
    }

    ExecutorSubmissionService(ProposalAuthority authority,
            @Nullable RequestContext openingContext,
            Duration admissionTimeout, Duration observationTimeout,
            int maxRetainedSubmissions, Duration resultRetention, LongSupplier nanoClock) {
        this(authority, openingContext, admissionTimeout, observationTimeout,
                maxRetainedSubmissions, resultRetention, nanoClock, () -> {});
    }

    ExecutorSubmissionService(ProposalAuthority authority,
            @Nullable RequestContext openingContext,
            Duration admissionTimeout, Duration observationTimeout,
            int maxRetainedSubmissions, Duration resultRetention, LongSupplier nanoClock,
            Runnable afterPruneCompletionSnapshot) {
        this.authority = java.util.Objects.requireNonNull(authority, "authority");
        this.openingContext = openingContext;
        this.admissionTimeout = positive(admissionTimeout, "admissionTimeout");
        this.observationTimeout = positive(observationTimeout, "observationTimeout");
        if (maxRetainedSubmissions <= 0) {
            throw new IllegalArgumentException("maxRetainedSubmissions must be positive");
        }
        this.maxRetainedSubmissions = maxRetainedSubmissions;
        java.util.Objects.requireNonNull(resultRetention, "resultRetention");
        if (resultRetention.isZero() || resultRetention.isNegative()) {
            throw new IllegalArgumentException("resultRetention must be positive");
        }
        this.resultRetentionNanos = resultRetention.toNanos();
        this.nanoClock = java.util.Objects.requireNonNull(nanoClock, "nanoClock");
        this.afterPruneCompletionSnapshot = java.util.Objects.requireNonNull(afterPruneCompletionSnapshot,
                "afterPruneCompletionSnapshot");
    }

    /**
     * Admits a proposal to the local event pipeline. The returned stage observes the result
     * deadline; {@link #completion(String)} retains the underlying completion if observation times
     * out.
     */
    public CompletionStage<ProposalResult> submitTask(TaskProposal proposal, EventQueue queue) {
        return submitTask(proposal, queue, (ignored, finalResult, finalResultSupplier) -> {});
    }

    CompletionStage<ProposalResult> submitTask(TaskProposal proposal, EventQueue queue,
            ProposalResultSink resultSink) {
        java.util.Objects.requireNonNull(proposal, "proposal");
        java.util.Objects.requireNonNull(queue, "queue");
        pruneCompleted();
        Submission submission = new Submission(proposal, resultSink);
        if (!registerSubmission(submission)) {
            ProposalResult.Failed capacityFailure = new ProposalResult.Failed(proposal.correlationId(),
                    "local proposal completion capacity is full", new IllegalStateException("submission capacity"),
                    ProposalResult.Failed.CommitCertainty.KNOWN_NOT_COMMITTED);
            CompletableFuture<ProposalResult> rejected = new CompletableFuture<>();
            CALLBACK_DISPATCHER.execute(() -> {
                rejected.complete(capacityFailure);
                notifySink(proposal.correlationId(), resultSink, capacityFailure, true, () -> capacityFailure);
            });
            return rejected.minimalCompletionStage();
        }

        Submission registered = submission;
        ProposalAdmission admission = new ProposalAdmission(this, registered);

        if (!matchesInvocation(proposal)) {
            complete(proposal.correlationId(), new ProposalResult.Rejected(proposal.correlationId(),
                    ProposalResult.Rejected.Kind.VALIDATION,
                    "proposal task ID does not match its invocation or base snapshot", null));
            return submission.observed.minimalCompletionStage();
        }

        if (proposal.baseSnapshot() == null && !hasRetainedOpeningContext(proposal)) {
            complete(proposal.correlationId(), new ProposalResult.Failed(proposal.correlationId(),
                    "a proposal without a base requires its retained opening request context",
                    new IllegalArgumentException("missing opening context"),
                    ProposalResult.Failed.CommitCertainty.KNOWN_NOT_COMMITTED));
            return submission.observed.minimalCompletionStage();
        }

        if (!queue.isForTask(proposal.proposedTask().id())) {
            complete(registered, new ProposalResult.Rejected(proposal.correlationId(),
                    ProposalResult.Rejected.Kind.VALIDATION,
                    "submission queue is bound to a different task", null));
            return submission.observed.minimalCompletionStage();
        }

        try {
            queue.enqueueItem(admission, admissionTimeout.toMillis(), MILLISECONDS);
        } catch (RuntimeException dispatchFailure) {
            handleQueueDispatchFailure(registered, admission, dispatchFailure);
            return submission.observed.minimalCompletionStage();
        }
        scheduleObservationTimeout(registered);
        return submission.observed.minimalCompletionStage();
    }

    private void scheduleObservationTimeout(Submission submission) {
        ScheduledFuture<?> timeoutTask = TIMEOUTS.schedule(() -> {
            ProposalResult timeoutResult = new ProposalResult.Failed(submission.proposal.correlationId(),
                    "proposal result was not observed before the configured deadline",
                    new TimeoutException("proposal result observation timed out"),
                    ProposalResult.Failed.CommitCertainty.INDETERMINATE);
            if (submission.authorityCompletion.get() == null
                    && submission.observationOutcome.compareAndSet(null, timeoutResult)) {
                CALLBACK_DISPATCHER.execute(() -> submission.observed.complete(timeoutResult));
                submission.enqueueSink(() -> notifySink(submission.proposal.correlationId(), submission,
                        timeoutResult, false));
            }
        }, observationTimeout.toMillis(), MILLISECONDS);
        submission.observationTimeout = timeoutTask;
        if (submission.authorityCompletion.get() != null) {
            timeoutTask.cancel(false);
        }
    }

    /** Refreshes an authoritative Task and its storage revision as one pair. */
    public TaskSnapshot refreshTask(String taskId) {
        return authority.refreshTask(taskId);
    }

    private boolean hasRetainedOpeningContext(TaskProposal proposal) {
        if (openingContext == null || openingContext.getTask() != null || openingContext.getMessage() == null
                || !openingContext.isTenantConsistentWithParams()
                || !openingContext.getTaskId().equals(proposal.proposedTask().id())
                || !openingContext.getContextId().equals(proposal.proposedTask().contextId())) {
            return false;
        }
        org.a2aproject.sdk.spec.Message message = openingContext.getMessage();
        return (message.taskId() == null || message.taskId().equals(proposal.proposedTask().id()))
                && (message.contextId() == null || message.contextId().equals(proposal.proposedTask().contextId()));
    }

    private boolean matchesInvocation(TaskProposal proposal) {
        org.a2aproject.sdk.spec.Task task = proposal.proposedTask();
        TaskSnapshot base = proposal.baseSnapshot();
        if (openingContext != null) {
            org.a2aproject.sdk.spec.Task invocationTask = openingContext.getTask();
            TaskSnapshot invocationSnapshot = openingContext.getTaskSnapshot();
            if (!openingContext.getTaskId().equals(task.id())
                    || !openingContext.getContextId().equals(task.contextId())
                    || (invocationTask != null && (!openingContext.getTaskId().equals(invocationTask.id())
                            || !openingContext.getContextId().equals(invocationTask.contextId())))
                    || (invocationSnapshot != null && (invocationTask == null
                            || !invocationSnapshot.task().equals(invocationTask)))) {
                return false;
            }
        }
        return base == null || (base.task().id().equals(task.id())
                && base.task().contextId().equals(task.contextId()));
    }

    /** Returns the retained eventual completion, or empty after its bounded local lifetime. */
    public Optional<java.util.concurrent.CompletionStage<ProposalResult>> completion(String correlationId) {
        pruneCompleted();
        Submission submission = submissions.get(correlationId);
        if (submission == null) {
            return Optional.empty();
        }
        AuthorityCompletion evidence = submission.authorityCompletion.get();
        return Optional.of((evidence == null ? submission.completion
                : CompletableFuture.completedFuture(evidence.result()))
                .minimalCompletionStage());
    }

    /** A queue capability created only after its exact proposal submission is registered. */
    public static final class ProposalAdmission implements org.a2aproject.sdk.server.events.EventQueueItem {
        private final ExecutorSubmissionService owner;
        private final Submission submission;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final AtomicReference<QueueAdmissionState> queueAdmission =
                new AtomicReference<>(QueueAdmissionState.UNDECIDED);

        private ProposalAdmission(ExecutorSubmissionService owner, Submission submission) {
            this.owner = owner;
            this.submission = submission;
        }

        @Override
        public org.a2aproject.sdk.spec.Event getEvent() {
            return submission.proposal.proposedTask();
        }

        @Override
        public boolean isReplicated() {
            return false;
        }

        @Override
        public @Nullable TaskProposal taskProposal() {
            return submission.proposal;
        }

        /** Internal queue handshake: called only after the owning MainQueue submits the item. */
        public void markQueueAdmissionAccepted(EventQueue.QueueAdmissionToken token, EventQueue queue) {
            requireQueueToken(token, queue);
            queueAdmission.updateAndGet(state -> state == QueueAdmissionState.PROCESSING
                    || state == QueueAdmissionState.REJECTED_FINAL
                    ? state : QueueAdmissionState.ACCEPTED);
        }

        /** Internal queue handshake: called only when capacity acquisition fails before submit. */
        public void markQueueAdmissionRejected(EventQueue.QueueAdmissionToken token, EventQueue queue) {
            requireQueueToken(token, queue);
            queueAdmission.compareAndSet(QueueAdmissionState.UNDECIDED, QueueAdmissionState.REJECTED);
        }

        private void requireQueueToken(EventQueue.QueueAdmissionToken token, EventQueue queue) {
            if (!token.authorizes(queue, this)) {
                throw new IllegalArgumentException("queue admission proof does not match this capability");
            }
        }

        public boolean isKnownNotAdmitted() {
            QueueAdmissionState state = queueAdmission.get();
            if (state == QueueAdmissionState.REJECTED_FINAL) {
                return true;
            }
            return state == QueueAdmissionState.REJECTED
                    && queueAdmission.compareAndSet(QueueAdmissionState.REJECTED,
                            QueueAdmissionState.REJECTED_FINAL);
        }

        private boolean markProcessing() {
            while (true) {
                QueueAdmissionState state = queueAdmission.get();
                if (state == QueueAdmissionState.REJECTED_FINAL || state == QueueAdmissionState.PROCESSING) {
                    return false;
                }
                if (queueAdmission.compareAndSet(state, QueueAdmissionState.PROCESSING)) {
                    return true;
                }
            }
        }

        public void process() {
            owner.processAdmitted(this);
        }
    }

    private enum QueueAdmissionState {
        UNDECIDED,
        ACCEPTED,
        REJECTED,
        REJECTED_FINAL,
        PROCESSING
    }

    /** Called only once by the event processor for the registered capability. */
    private void processAdmitted(ProposalAdmission admission) {
        Submission submission = admission.submission;
        if (admission.owner != this || submissions.get(submission.proposal.correlationId()) != submission
                || !admission.markProcessing() || !admission.claimed.compareAndSet(false, true)) {
            return;
        }
        TaskProposal proposal = submission.proposal;
        ProposalResult result;
        try {
            result = authority.process(proposal);
            if (!proposal.correlationId().equals(result.correlationId())) {
                throw new IllegalStateException("proposal authority returned a mismatched correlation ID");
            }
            if (result instanceof ProposalResult.Committed committed
                    && (!proposal.proposedTask().id().equals(committed.snapshot().task().id())
                            || !proposal.proposedTask().contextId().equals(committed.snapshot().task().contextId()))) {
                throw new IllegalStateException("proposal authority returned a mismatched Task snapshot");
            }
        } catch (TaskMutationNotCommittedException e) {
            result = failed(proposal, String.valueOf(e.getMessage()), e,
                    ProposalResult.Failed.CommitCertainty.KNOWN_NOT_COMMITTED);
        } catch (Throwable failure) {
            result = failed(proposal, String.valueOf(failure.getMessage()), failure,
                    ProposalResult.Failed.CommitCertainty.INDETERMINATE);
        }
        complete(submission, result);
    }

    private void complete(String correlationId, ProposalResult result) {
        Submission submission = submissions.get(correlationId);
        if (submission != null) {
            complete(submission, result);
        }
    }

    private void complete(Submission submission, ProposalResult result) {
        if (!submission.finished.compareAndSet(false, true)) {
            return;
        }
        submission.authorityCompletion.set(new AuthorityCompletion(result, nanoClock.getAsLong()));
        submission.observationOutcome.compareAndSet(null, result);
        if (submission.observationTimeout != null) {
            submission.observationTimeout.cancel(false);
        }
        CALLBACK_DISPATCHER.execute(() -> submission.completion.complete(result));
        ProposalResult observerResult = java.util.Objects.requireNonNull(submission.observationOutcome.get());
        CALLBACK_DISPATCHER.execute(() -> submission.observed.complete(observerResult));
        submission.enqueueSink(() -> notifySink(submission.proposal.correlationId(), submission, result, true));
    }

    private void completeObservedOnly(Submission submission, ProposalResult result) {
        if (submission.observationOutcome.compareAndSet(null, result)) {
            CALLBACK_DISPATCHER.execute(() -> submission.observed.complete(result));
            submission.enqueueSink(() -> notifySink(submission.proposal.correlationId(), submission, result, false));
        }
    }

    private void notifySink(String correlationId, Submission submission, ProposalResult result,
            boolean finalResult) {
        if (!finalResult && submission.authorityCompletion.get() != null) {
            return;
        }
        notifySink(correlationId, submission.resultSink, result, finalResult, submission::authoritativeResult);
    }

    private void notifySink(String correlationId, ProposalResultSink sink, ProposalResult result,
            boolean finalResult, Supplier<@Nullable ProposalResult> finalResultSupplier) {
        try {
            sink.accept(result, finalResult, finalResultSupplier);
        } catch (RuntimeException sinkFailure) {
            LOGGER.warn("Executor proposal result sink failed for correlation {}", correlationId, sinkFailure);
        }
    }

    private void handleQueueDispatchFailure(Submission submission, ProposalAdmission admission,
            RuntimeException failure) {
        if (admission.isKnownNotAdmitted()) {
            complete(submission, failed(submission.proposal, failure,
                    ProposalResult.Failed.CommitCertainty.KNOWN_NOT_COMMITTED));
            return;
        }
        // A queue adapter can throw after forwarding the capability. Without an explicit
        // pre-admission rejection from the owning queue, only an indeterminate notice is safe.
        completeObservedOnly(submission, failed(submission.proposal, failure,
                ProposalResult.Failed.CommitCertainty.INDETERMINATE));
        scheduleObservationTimeout(submission);
    }

    private ProposalResult.Failed failed(TaskProposal proposal, Throwable failure,
            ProposalResult.Failed.CommitCertainty certainty) {
        return failed(proposal, String.valueOf(failure.getMessage()), failure, certainty);
    }

    private ProposalResult.Failed failed(TaskProposal proposal, String reason, Throwable failure,
            ProposalResult.Failed.CommitCertainty certainty) {
        return new ProposalResult.Failed(proposal.correlationId(), reason, failure, certainty);
    }

    private synchronized boolean registerSubmission(Submission submission) {
        String correlationId = submission.proposal.correlationId();
        if (submissions.containsKey(correlationId)) {
            throw new IllegalArgumentException("duplicate proposal correlation ID: " + correlationId);
        }
        if (submissions.size() >= maxRetainedSubmissions) {
            return false;
        }
        submissions.put(correlationId, submission);
        return true;
    }

    private void pruneCompleted() {
        submissions.forEach((id, submission) -> {
            AuthorityCompletion evidence = submission.authorityCompletion.get();
            afterPruneCompletionSnapshot.run();
            if (evidence != null
                    && nanoClock.getAsLong() - evidence.completedAtNanos() >= resultRetentionNanos) {
                submissions.remove(id, submission);
            }
        });
    }

    private static Duration positive(Duration value, String name) {
        java.util.Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative() || value.toMillis() <= 0) {
            throw new IllegalArgumentException(name + " must be positive and at least one millisecond");
        }
        return value;
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "a2a-proposal-result-timeouts");
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class Submission {
        private final TaskProposal proposal;
        private final CompletableFuture<ProposalResult> completion = new CompletableFuture<>();
        private final CompletableFuture<ProposalResult> observed = new CompletableFuture<>();
        private final AtomicReference<@Nullable ProposalResult> observationOutcome = new AtomicReference<>();
        private final ProposalResultSink resultSink;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicReference<@Nullable AuthorityCompletion> authorityCompletion = new AtomicReference<>();
        private volatile @Nullable ScheduledFuture<?> observationTimeout;
        private CompletableFuture<Void> sinkTail = CompletableFuture.completedFuture(null);

        private Submission(TaskProposal proposal, ProposalResultSink resultSink) {
            this.proposal = proposal;
            this.resultSink = resultSink;
        }

        private @Nullable ProposalResult authoritativeResult() {
            AuthorityCompletion evidence = authorityCompletion.get();
            return evidence == null ? null : evidence.result();
        }

        private synchronized void enqueueSink(Runnable notification) {
            sinkTail = sinkTail.handle((ignored, failure) -> null)
                    .thenRunAsync(notification, CALLBACK_DISPATCHER);
        }
    }

    private record AuthorityCompletion(ProposalResult result, long completedAtNanos) {}

    @FunctionalInterface
    interface ProposalResultSink {
        void accept(ProposalResult result, boolean finalResult,
                Supplier<@Nullable ProposalResult> finalResultSupplier);
    }

    public interface ProposalAuthority {
        ProposalResult process(TaskProposal proposal) throws Exception;

        TaskSnapshot refreshTask(String taskId);
    }

    /** Conservative authority until T05 supplies full Task normalization. */
    public static ProposalAuthority forTaskStore(org.a2aproject.sdk.server.tasks.TaskStore taskStore) {
        return new ProposalAuthority() {
            @Override
            public ProposalResult process(TaskProposal proposal) {
                if (!(taskStore instanceof AtomicTaskStore atomicStore)) {
                    throw new TaskMutationNotCommittedException(proposal.proposedTask().id(),
                            "full Task proposal requires an AtomicTaskStore; legacy stores are unsupported");
                }
                TaskSnapshot current = atomicStore.readSnapshot(proposal.proposedTask().id());
                if (current == null) {
                    return new ProposalResult.Rejected(proposal.correlationId(),
                            ProposalResult.Rejected.Kind.VALIDATION,
                            "initial materialization requires the T05 normalization path", null);
                }
                if (proposal.baseSnapshot() == null) {
                    return new ProposalResult.Rejected(proposal.correlationId(),
                            ProposalResult.Rejected.Kind.VALIDATION,
                            "an existing Task proposal requires its immutable base snapshot",
                            current.revision());
                }
                if (!proposal.baseSnapshot().equals(current)) {
                    return new ProposalResult.Rejected(proposal.correlationId(),
                            ProposalResult.Rejected.Kind.CONFLICT,
                            "Task changed since the proposal base was captured", current.revision());
                }
                if (proposal.proposedTask().equals(current.task())) {
                    return new ProposalResult.Committed(proposal.correlationId(), current, List.of(), false);
                }
                return new ProposalResult.Rejected(proposal.correlationId(),
                        ProposalResult.Rejected.Kind.VALIDATION,
                        "changed full Task normalization is deferred to P3-T05", current.revision());
            }

            @Override
            public TaskSnapshot refreshTask(String taskId) {
                if (!(taskStore instanceof AtomicTaskStore atomicStore)) {
                    throw new UnsupportedOperationException(
                            "refreshTask requires an AtomicTaskStore; legacy stores cannot provide a revision pair");
                }
                TaskSnapshot snapshot = atomicStore.readSnapshot(taskId);
                if (snapshot == null) {
                    throw new IllegalStateException("no authoritative Task exists for " + taskId);
                }
                return snapshot;
            }
        };
    }
}
