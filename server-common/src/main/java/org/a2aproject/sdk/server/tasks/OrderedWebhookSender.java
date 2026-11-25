package org.a2aproject.sdk.server.tasks;

import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.spec.Task;

/** Additive sender capability whose stage settles only after its owned dispatch work has stopped. */
public interface OrderedWebhookSender {

    /** False when the wrapped adapter cannot establish that no outbound work remains after stage completion. */
    boolean supportsOrderedDispatch();

    CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, long deadlineNanos,
            LongSupplier nanoTime);

    /** Optional execution-context propagation hook for adapters wrapping the ordered sender. */
    default CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, long deadlineNanos,
            LongSupplier nanoTime, UnaryOperator<Runnable> taskContext) {
        return sendOrderedBatch(batch, deadlineNanos, nanoTime);
    }

    /** Adds immutable local proposal provenance without changing the replicated batch representation. */
    default CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, boolean taskProposal,
            long deadlineNanos, LongSupplier nanoTime, UnaryOperator<Runnable> taskContext) {
        return sendOrderedBatch(batch, deadlineNanos, nanoTime, taskContext);
    }

    CompletionStage<Boolean> sendOrderedLegacyTaskSnapshot(Task snapshot, long deadlineNanos,
            LongSupplier nanoTime);

    /** Optional execution-context propagation hook for adapters wrapping the ordered sender. */
    default CompletionStage<Boolean> sendOrderedLegacyTaskSnapshot(Task snapshot, long deadlineNanos,
            LongSupplier nanoTime, UnaryOperator<Runnable> taskContext) {
        return sendOrderedLegacyTaskSnapshot(snapshot, deadlineNanos, nanoTime);
    }
}
