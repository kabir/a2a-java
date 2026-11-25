package org.a2aproject.sdk.extras.taskstore.database.jpa;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.annotation.Priority;

import org.a2aproject.sdk.server.tasks.WebhookDispatchClaim;
import org.a2aproject.sdk.server.tasks.WebhookDispatchLaneState;
import org.a2aproject.sdk.server.tasks.WebhookDispatchLanePosition;
import org.a2aproject.sdk.server.tasks.WebhookDispatchLaneStore;
import org.a2aproject.sdk.server.tasks.WebhookDispatchNoopClaim;

/** Shared-database webhook gate. Each method delegates to a short transaction independent of task mutation. */
@Alternative
@Priority(50)
@ApplicationScoped
public class JpaWebhookDispatchGate implements WebhookDispatchLaneStore {

    private final JpaWebhookDispatchGateTransactions transactions;

    @Inject
    public JpaWebhookDispatchGate(JpaWebhookDispatchGateTransactions transactions) {
        this.transactions = transactions;
    }

    @Override
    public boolean isSharedStorage() {
        return true;
    }

    @Override
    public boolean initializeTaskLane(String taskId, long writerEpoch, long nextGeneration) {
        return transactions.initializeTaskLane(taskId, writerEpoch, nextGeneration);
    }

    @Override
    public Optional<WebhookDispatchLaneState> readLane(String taskId) {
        return transactions.readLane(taskId);
    }

    @Override
    public Optional<WebhookDispatchLanePosition> readPosition(String taskId) {
        return transactions.readPosition(taskId);
    }

    @Override
    public Optional<WebhookDispatchClaim> claim(String taskId, long writerEpoch,
            long firstGeneration, long lastGeneration) {
        return transactions.claim(taskId, writerEpoch, firstGeneration, lastGeneration);
    }

    @Override
    public boolean complete(WebhookDispatchClaim claim) {
        return transactions.complete(claim);
    }

    @Override
    public boolean retireUnclaimedGap(String taskId, long writerEpoch,
            long expectedNextGeneration, long throughGeneration) {
        return transactions.retireUnclaimedGap(taskId, writerEpoch, expectedNextGeneration, throughGeneration);
    }

    @Override
    public Optional<WebhookDispatchNoopClaim> claimNoop(String taskId, long writerEpoch,
            long expectedNextGeneration) {
        return transactions.claimNoop(taskId, writerEpoch, expectedNextGeneration);
    }

    @Override
    public boolean releaseNoop(WebhookDispatchNoopClaim claim) {
        return transactions.releaseNoop(claim);
    }

    @Override
    public boolean completeWithLoss(WebhookDispatchClaim claim, String reason) {
        return transactions.completeWithLoss(claim, reason);
    }

    @Override
    public boolean recordAdmissionLoss(String taskId, long writerEpoch, long firstGeneration,
            long lastGeneration, String reason) {
        return transactions.recordAdmissionLoss(taskId, writerEpoch, firstGeneration, lastGeneration, reason);
    }

    @Override
    public boolean rebaseUnclaimedLane(String taskId, long expectedWriterEpoch, long newWriterEpoch) {
        return transactions.rebaseUnclaimedLane(taskId, expectedWriterEpoch, newWriterEpoch);
    }

    @Override
    public boolean recoverQuiescedClaim(WebhookDispatchClaim abandonedClaim, long newWriterEpoch) {
        return transactions.recoverQuiescedClaim(abandonedClaim, newWriterEpoch);
    }

    @Override
    public boolean recoverQuiescedNoop(WebhookDispatchNoopClaim abandonedClaim, long newWriterEpoch) {
        return transactions.recoverQuiescedNoop(abandonedClaim, newWriterEpoch);
    }
}
