package org.a2aproject.sdk.server.tasks;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.events.EventQueue;
import org.a2aproject.sdk.spec.Task;
import org.jspecify.annotations.Nullable;

/** Invocation-local provenance and asynchronous result sink for executor Task proposals. */
public final class ExecutionProposalContext {
    private final String taskId;
    private final String invocationContextId;
    private final @Nullable TaskSnapshot invocationStartSnapshot;
    private final ExecutorSubmissionService submissionService;
    private final CopyOnWriteArrayList<Consumer<ProposalResult>> resultListeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<@Nullable ProposalResult> lastResult = new AtomicReference<>();

    public ExecutionProposalContext(RequestContext requestContext, ExecutorSubmissionService submissionService) {
        this.taskId = requestContext.getTaskId();
        this.invocationContextId = requestContext.getContextId();
        this.invocationStartSnapshot = requestContext.getTaskSnapshot();
        this.submissionService = submissionService;
        Task invocationTask = requestContext.getTask();
        if (invocationTask != null && (!taskId.equals(invocationTask.id())
                || !invocationContextId.equals(invocationTask.contextId()))) {
            throw new IllegalArgumentException("RequestContext Task identity does not match its invocation IDs");
        }
        if (invocationStartSnapshot != null
                && (invocationTask == null || !invocationStartSnapshot.task().equals(invocationTask))) {
            throw new IllegalArgumentException("RequestContext Task and atomic snapshot must be the same value");
        }
    }

    public CompletionStage<ProposalResult> submitTask(TaskProposal proposal, EventQueue queue) {
        validateProposal(proposal);
        return submissionService.submitTask(proposal, queue, this::recordOutcome);
    }

    /** Returns a matching authoritative Task/revision pair without changing any captured bases. */
    public TaskSnapshot refreshTask() {
        TaskSnapshot refreshed = submissionService.refreshTask(taskId);
        if (!taskId.equals(refreshed.task().id())
                || !invocationContextId.equals(refreshed.task().contextId())) {
            throw new IllegalStateException("refreshed Task does not match this executor invocation");
        }
        return refreshed;
    }

    /** The latest asynchronously observed final result in this executor invocation. */
    public @Nullable ProposalResult lastResult() {
        return lastResult.get();
    }

    /** Registers an asynchronous sink for retained void-emitter outcomes. */
    public void addResultListener(Consumer<ProposalResult> listener) {
        resultListeners.add(java.util.Objects.requireNonNull(listener, "listener"));
    }

    /** Local completion lookup; entries share the submission service's bounded retention. */
    public Optional<CompletionStage<ProposalResult>> completion(String correlationId) {
        return submissionService.completion(correlationId);
    }

    void submitLegacyTask(Task task, EventQueue queue) {
        submitTask(TaskProposal.create(invocationStartSnapshot, task), queue);
    }

    private void validateProposal(TaskProposal proposal) {
        java.util.Objects.requireNonNull(proposal, "proposal");
        if (!taskId.equals(proposal.proposedTask().id())
                || !invocationContextId.equals(proposal.proposedTask().contextId())
                || (proposal.baseSnapshot() != null
                        && (!taskId.equals(proposal.baseSnapshot().task().id())
                                || !invocationContextId.equals(proposal.baseSnapshot().task().contextId())))) {
            throw new IllegalArgumentException("proposal identity does not match this executor invocation");
        }
    }

    private void recordOutcome(ProposalResult result, boolean finalResult,
            Supplier<@Nullable ProposalResult> finalResultSupplier) {
        ProposalResult currentFinalResult = finalResultSupplier.get();
        if (!finalResult && currentFinalResult != null) {
            return;
        }
        lastResult.set(result);
        currentFinalResult = finalResultSupplier.get();
        if (!finalResult && currentFinalResult != null) {
            lastResult.set(currentFinalResult);
            return;
        }
        for (Consumer<ProposalResult> listener : resultListeners) {
            if (!finalResult) {
                currentFinalResult = finalResultSupplier.get();
                if (currentFinalResult != null) {
                    lastResult.set(currentFinalResult);
                    break;
                }
            }
            try {
                listener.accept(result);
            } catch (RuntimeException ignored) {
                // One local observer must not suppress delivery to the remaining observers.
            }
        }
    }

}
