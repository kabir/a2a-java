package org.a2aproject.sdk.extras.queuemanager.replicated.tests.multiinstance.common;

import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.a2aproject.sdk.extras.taskstore.database.jpa.JpaWebhookDispatchGate;
import org.a2aproject.sdk.server.tasks.WebhookDispatchClaim;
import org.a2aproject.sdk.server.tasks.WebhookDispatchLanePosition;
import org.a2aproject.sdk.server.tasks.WebhookDispatchLaneState;
import org.a2aproject.sdk.server.tasks.WebhookDispatchLaneStore;
import org.a2aproject.sdk.server.tasks.WebhookDispatchNoopClaim;

/**
 * Test decorator around the real shared-database lane store: every claim/complete/loss/recovery transition made by
 * this node is traced with its result, independently of whatever the webhook receiver later does. The decorator
 * declares the delegate's shared-storage nature, so the T12 topology gate evaluates the real store.
 */
@ApplicationScoped
@Alternative
@Priority(100)
public class Phase3LaneStore implements WebhookDispatchLaneStore {

    @Inject
    JpaWebhookDispatchGate delegate;

    @Override
    public boolean isSharedStorage() {
        return delegate.isSharedStorage();
    }

    @Override
    public boolean initializeTaskLane(String taskId, long writerEpoch, long nextGeneration) {
        boolean result = delegate.initializeTaskLane(taskId, writerEpoch, nextGeneration);
        Phase3Instrumentation.trace("lane-init", taskId + " epoch=" + writerEpoch + " next=" + nextGeneration
                + " -> " + result);
        return result;
    }

    @Override
    public Optional<WebhookDispatchLaneState> readLane(String taskId) {
        return delegate.readLane(taskId);
    }

    @Override
    public Optional<WebhookDispatchLanePosition> readPosition(String taskId) {
        return delegate.readPosition(taskId);
    }

    @Override
    public Optional<WebhookDispatchClaim> claim(String taskId, long writerEpoch, long firstGeneration,
            long lastGeneration) {
        Phase3Instrumentation.pass(Phase3Instrumentation.LANE_CLAIM, taskId + " " + firstGeneration + "-" + lastGeneration);
        Optional<WebhookDispatchClaim> result = delegate.claim(taskId, writerEpoch, firstGeneration, lastGeneration);
        if (result.isPresent()) {
            Phase3Instrumentation.trace("lane-claim", taskId + " " + firstGeneration + "-" + lastGeneration
                    + " epoch=" + writerEpoch + " holder=" + result.get().holderToken());
        } else {
            Phase3Instrumentation.trace("lane-claim-refused", taskId + " " + firstGeneration + "-" + lastGeneration
                    + " epoch=" + writerEpoch);
        }
        return result;
    }

    @Override
    public boolean complete(WebhookDispatchClaim claim) {
        boolean result = delegate.complete(claim);
        Phase3Instrumentation.trace("lane-complete", claim.taskId() + " " + claim.firstGeneration() + "-"
                + claim.lastGeneration() + " holder=" + claim.holderToken() + " -> " + result);
        return result;
    }

    @Override
    public boolean retireUnclaimedGap(String taskId, long writerEpoch, long expectedNextGeneration,
            long throughGeneration) {
        boolean result = delegate.retireUnclaimedGap(taskId, writerEpoch, expectedNextGeneration, throughGeneration);
        Phase3Instrumentation.trace("lane-retire-gap", taskId + " " + expectedNextGeneration + "-" + throughGeneration
                + " -> " + result);
        return result;
    }

    @Override
    public Optional<WebhookDispatchNoopClaim> claimNoop(String taskId, long writerEpoch, long expectedNextGeneration) {
        Optional<WebhookDispatchNoopClaim> result = delegate.claimNoop(taskId, writerEpoch, expectedNextGeneration);
        Phase3Instrumentation.trace("lane-claim-noop", taskId + " next=" + expectedNextGeneration + " -> "
                + result.isPresent());
        return result;
    }

    @Override
    public boolean releaseNoop(WebhookDispatchNoopClaim claim) {
        boolean result = delegate.releaseNoop(claim);
        Phase3Instrumentation.trace("lane-release-noop", claim.taskId() + " -> " + result);
        return result;
    }

    @Override
    public boolean completeWithLoss(WebhookDispatchClaim claim, String reason) {
        boolean result = delegate.completeWithLoss(claim, reason);
        Phase3Instrumentation.trace("lane-complete-loss", claim.taskId() + " " + claim.firstGeneration() + "-"
                + claim.lastGeneration() + " reason=" + reason + " -> " + result);
        return result;
    }

    @Override
    public boolean recordAdmissionLoss(String taskId, long writerEpoch, long firstGeneration, long lastGeneration,
            String reason) {
        boolean result = delegate.recordAdmissionLoss(taskId, writerEpoch, firstGeneration, lastGeneration, reason);
        Phase3Instrumentation.trace("lane-admission-loss", taskId + " " + firstGeneration + "-" + lastGeneration
                + " reason=" + reason + " -> " + result);
        return result;
    }

    @Override
    public boolean rebaseUnclaimedLane(String taskId, long expectedWriterEpoch, long newWriterEpoch) {
        boolean result = delegate.rebaseUnclaimedLane(taskId, expectedWriterEpoch, newWriterEpoch);
        Phase3Instrumentation.trace("lane-rebase", taskId + " " + expectedWriterEpoch + "->" + newWriterEpoch
                + " -> " + result);
        return result;
    }

    @Override
    public boolean recoverQuiescedClaim(WebhookDispatchClaim abandonedClaim, long newWriterEpoch) {
        boolean result = delegate.recoverQuiescedClaim(abandonedClaim, newWriterEpoch);
        Phase3Instrumentation.trace("lane-recover-claim", abandonedClaim.taskId() + " "
                + abandonedClaim.firstGeneration() + "-" + abandonedClaim.lastGeneration() + " holder="
                + abandonedClaim.holderToken() + " newEpoch=" + newWriterEpoch + " -> " + result);
        return result;
    }

    @Override
    public boolean recoverQuiescedNoop(WebhookDispatchNoopClaim abandonedClaim, long newWriterEpoch) {
        boolean result = delegate.recoverQuiescedNoop(abandonedClaim, newWriterEpoch);
        Phase3Instrumentation.trace("lane-recover-noop", abandonedClaim.taskId() + " -> " + result);
        return result;
    }
}
