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

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.events.EventQueue;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.Task;
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
    private final @Nullable PendingTaskInput openingTaskInput;
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
        this.openingTaskInput = openingTaskInput(openingContext);
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
        private final AtomicReference<EventQueue.QueueAdmissionToken> queueAdmissionToken = new AtomicReference<>();

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

        /** Whether this is a null-base proposal that attempts initial materialization. */
        public boolean isInitialProposal() {
            return submission.proposal.baseSnapshot() == null;
        }

        /** Whether the initiating request uses a protocol that consumes full Task summaries. */
        public boolean hasLegacyTaskSummaryRequest() {
            if (owner.openingContext == null) {
                return false;
            }
            @Nullable ServerCallContext callContext = owner.openingContext.getCallContext();
            return callContext != null && !callContext.getVersionPolicy().supportsPhase2Output();
        }

        /** Claims this admission for one exact attempt by its owning MainQueue. */
        public boolean claimQueueAdmission(EventQueue.QueueAdmissionToken token, EventQueue queue) {
            requireQueueToken(token, queue);
            return queueAdmissionToken.compareAndSet(null, token);
        }

        /** Internal queue handshake: called after capacity is acquired, before bus submission. */
        public void markQueueAdmissionAccepted(EventQueue.QueueAdmissionToken token, EventQueue queue) {
            requireQueueToken(token, queue);
            requireClaimedQueueToken(token);
            queueAdmission.compareAndSet(QueueAdmissionState.UNDECIDED, QueueAdmissionState.ACCEPTED);
        }

        /** Internal queue handshake: rolls back only the exact claimed attempt that failed. */
        public void markQueueAdmissionRejected(EventQueue.QueueAdmissionToken token, EventQueue queue) {
            requireQueueToken(token, queue);
            if (queueAdmissionToken.get() != token) {
                return;
            }
            queueAdmission.compareAndSet(QueueAdmissionState.UNDECIDED, QueueAdmissionState.REJECTED);
            queueAdmission.compareAndSet(QueueAdmissionState.ACCEPTED, QueueAdmissionState.REJECTED);
        }

        private void requireClaimedQueueToken(EventQueue.QueueAdmissionToken token) {
            if (queueAdmissionToken.get() != token) {
                throw new IllegalStateException("queue admission token does not own this proposal attempt");
            }
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

        private boolean markProcessing(boolean requiresQueueAdmission) {
            while (true) {
                QueueAdmissionState state = queueAdmission.get();
                if (state == QueueAdmissionState.REJECTED_FINAL || state == QueueAdmissionState.PROCESSING) {
                    return false;
                }
                if (requiresQueueAdmission && state != QueueAdmissionState.ACCEPTED) {
                    return false;
                }
                if (queueAdmission.compareAndSet(state, QueueAdmissionState.PROCESSING)) {
                    return true;
                }
            }
        }

        public @Nullable ProposalExecution process(TaskCommitSlotCallback slotCallback) {
            return owner.processAdmitted(this, null, slotCallback);
        }

        /** Production processing requires an unforgeable capability issued by the owning MainQueue. */
        public @Nullable ProposalExecution process(EventQueue.ProposalProcessingCapability capability,
                TaskCommitSlotCallback slotCallback) {
            return owner.processAdmitted(this, capability, slotCallback, false);
        }

        /** Production path can reject an initial proposal after this invocation selected a direct response. */
        public @Nullable ProposalExecution process(EventQueue.ProposalProcessingCapability capability,
                boolean directResponseAlreadySelected, TaskCommitSlotCallback slotCallback) {
            return owner.processAdmitted(this, capability, slotCallback, directResponseAlreadySelected);
        }

        /** Compatibility entry point for callers that do not own the processor's publisher. */
        public void process() {
            owner.processAdmitted(this, null, TaskCommitSlotCallback.NONE);
        }
    }

    /** Internal production outcome; its constructor cannot be used by injected authorities. */
    public static final class ProposalExecution {
        private final ProposalResult result;
        private final @Nullable CommittedMutation mutation;

        private ProposalExecution(ProposalResult result, @Nullable CommittedMutation mutation) {
            this.result = result;
            this.mutation = mutation;
        }

        public ProposalResult result() {
            return result;
        }

        public @Nullable CommittedMutation mutation() {
            return mutation;
        }
    }

    private interface OrderedProposalAuthority extends ProposalAuthority {
        @Override
        default ProposalResult process(TaskProposal proposal) {
            throw new IllegalStateException(
                    "production TaskStore proposals must be processed through ordered queue admission");
        }

        ProposalExecution processWithPublication(TaskProposal proposal, TaskCommitSlotCallback slotCallback);

        default ProposalExecution processWithPublication(TaskProposal proposal,
                @Nullable PendingTaskInput openingInput, TaskCommitSlotCallback slotCallback) {
            return processWithPublication(proposal, slotCallback);
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
    private @Nullable ProposalExecution processAdmitted(ProposalAdmission admission,
            EventQueue.@Nullable ProposalProcessingCapability capability, TaskCommitSlotCallback slotCallback) {
        return processAdmitted(admission, capability, slotCallback, false);
    }

    private @Nullable ProposalExecution processAdmitted(ProposalAdmission admission,
            EventQueue.@Nullable ProposalProcessingCapability capability, TaskCommitSlotCallback slotCallback,
            boolean directResponseAlreadySelected) {
        Submission submission = admission.submission;
        boolean orderedProduction = authority instanceof OrderedProposalAuthority;
        if (admission.owner != this || submissions.get(submission.proposal.correlationId()) != submission
                || (orderedProduction && (capability == null || !capability.claim(admission)))
                || !admission.markProcessing(orderedProduction) || !admission.claimed.compareAndSet(false, true)) {
            return null;
        }
        TaskProposal proposal = submission.proposal;
        ProposalExecution execution;
        try {
            if (directResponseAlreadySelected && openingTaskInput != null && proposal.baseSnapshot() == null) {
                execution = new ProposalExecution(new ProposalResult.Rejected(proposal.correlationId(),
                        ProposalResult.Rejected.Kind.VALIDATION,
                        "this invocation already selected a direct Message response", null), null);
            } else {
                execution = authority instanceof OrderedProposalAuthority orderedAuthority
                        ? orderedAuthority.processWithPublication(proposal, openingTaskInput, slotCallback)
                        : new ProposalExecution(authority.process(proposal), null);
            }
            ProposalResult result = execution.result();
            if (!proposal.correlationId().equals(result.correlationId())) {
                throw new IllegalStateException("proposal authority returned a mismatched correlation ID");
            }
            if (result instanceof ProposalResult.Committed committed
                    && (!proposal.proposedTask().id().equals(committed.snapshot().task().id())
                            || !proposal.proposedTask().contextId().equals(committed.snapshot().task().contextId()))) {
                throw new IllegalStateException("proposal authority returned a mismatched Task snapshot");
            }
        } catch (TaskMutationNotCommittedException e) {
            execution = new ProposalExecution(failed(proposal, String.valueOf(e.getMessage()), e,
                    ProposalResult.Failed.CommitCertainty.KNOWN_NOT_COMMITTED), null);
        } catch (Throwable failure) {
            execution = new ProposalExecution(failed(proposal, String.valueOf(failure.getMessage()), failure,
                    ProposalResult.Failed.CommitCertainty.INDETERMINATE), null);
        }
        complete(submission, execution.result());
        return execution;
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

    private static @Nullable PendingTaskInput openingTaskInput(@Nullable RequestContext context) {
        if (context == null || context.getTask() != null || context.getTaskSnapshot() != null) {
            return null;
        }
        org.a2aproject.sdk.spec.Message message = context.getMessage();
        if (message == null) {
            return null;
        }
        return new PendingTaskInput(message, List.of(), context.getConfiguration(), context.getMetadata(),
                new PendingTaskInput.Admission(context.getTaskId(), context.getContextId(), context.getTenant(),
                        true, false));
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

    /** Creates the atomic authority for full-Task proposals on the ordered queue path. */
    public static ProposalAuthority forTaskStore(org.a2aproject.sdk.server.tasks.TaskStore taskStore) {
        return new OrderedProposalAuthority() {
            @Override
            public ProposalExecution processWithPublication(TaskProposal proposal,
                    TaskCommitSlotCallback slotCallback) {
                return processWithPublication(proposal, null, slotCallback);
            }

            @Override
            public ProposalExecution processWithPublication(TaskProposal proposal,
                    @Nullable PendingTaskInput openingInput, TaskCommitSlotCallback slotCallback) {
                if (!(taskStore instanceof AtomicTaskStore atomicStore)) {
                    throw new TaskMutationNotCommittedException(proposal.proposedTask().id(),
                            "full Task proposal requires an AtomicTaskStore; legacy stores are unsupported");
                }
                AtomicReference<@Nullable TaskSnapshot> observed = new AtomicReference<>();
                AtomicReference<Optional<ProposalResult.Rejected>> rejection =
                        new AtomicReference<>(Optional.empty());
                try {
                    CommittedMutation mutation = new TaskMutationAuthority(atomicStore).mutate(
                            proposal.proposedTask().id(), current -> {
                                observed.set(current);
                                Task proposedTask = proposal.proposedTask();
                                if (current == null && proposal.baseSnapshot() != null) {
                                    throw rejectProposal(proposal, rejection, ProposalResult.Rejected.Kind.CONFLICT,
                                            "Task was removed after the proposal base was captured", null);
                                }
                                if (current != null && (!current.task().id().equals(proposedTask.id())
                                        || !current.task().contextId().equals(proposedTask.contextId()))) {
                                    throw rejectProposal(proposal, rejection, ProposalResult.Rejected.Kind.VALIDATION,
                                            "Task proposal identity does not match the authoritative task",
                                            current.revision());
                                }
                                if (current == null) {
                                    if (openingInput == null) {
                                        throw rejectProposal(proposal, rejection,
                                                ProposalResult.Rejected.Kind.VALIDATION,
                                                "initial materialization requires retained opening request input", null);
                                    }
                                    return TaskMutationPlanner.planTaskProposal(null, proposedTask, openingInput);
                                }
                                TaskSnapshot base = proposal.baseSnapshot();
                                if (base == null) {
                                    if (openingInput != null) {
                                        throw rejectProposal(proposal, rejection, ProposalResult.Rejected.Kind.CONFLICT,
                                                "Task was materialized after the initial proposal was captured",
                                                current.revision());
                                    }
                                    throw rejectProposal(proposal, rejection, ProposalResult.Rejected.Kind.VALIDATION,
                                            "an existing Task proposal requires its immutable base snapshot",
                                            current.revision());
                                }
                                if (!base.equals(current)) {
                                    throw rejectProposal(proposal, rejection, ProposalResult.Rejected.Kind.CONFLICT,
                                            "Task changed since the proposal base was captured", current.revision());
                                }
                                return TaskMutationPlanner.planTaskProposal(current, proposedTask);
                            }, slotCallback);
                    ProposalResult.Committed result = new ProposalResult.Committed(proposal.correlationId(),
                            mutation.snapshot(), mutation.events(), mutation.changed());
                    return new ProposalExecution(result, mutation);
                } catch (A2AError failure) {
                    Optional<ProposalResult.Rejected> typedRejection = java.util.Objects.requireNonNull(rejection.get());
                    if (typedRejection.isPresent()) {
                        return new ProposalExecution(typedRejection.get(), null);
                    }
                    TaskSnapshot current = observed.get();
                    ProposalResult.Rejected rejected = new ProposalResult.Rejected(proposal.correlationId(),
                            ProposalResult.Rejected.Kind.VALIDATION, String.valueOf(failure.getMessage()),
                            current == null ? null : current.revision());
                    return new ProposalExecution(rejected, null);
                }
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

            private InvalidParamsError rejectProposal(TaskProposal proposal,
                    AtomicReference<Optional<ProposalResult.Rejected>> rejection,
                    ProposalResult.Rejected.Kind kind, String reason, @Nullable TaskRevision revision) {
                rejection.set(Optional.of(new ProposalResult.Rejected(proposal.correlationId(), kind, reason, revision)));
                return new InvalidParamsError(reason);
            }
        };
    }
}
