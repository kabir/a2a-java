package org.a2aproject.sdk.server.tasks;

import java.util.Optional;

/** Additional lane operations; the three canonical gate signatures stay on {@link WebhookDispatchGate}. */
public interface WebhookDispatchLaneStore extends WebhookDispatchGate {

    /** Whether this gate coordinates writers through shared persistent storage. */
    default boolean isSharedStorage() {
        return false;
    }

    /** Initializes a newly-created task lane once; an existing cursor or claim is never reset. */
    boolean initializeTaskLane(String taskId, long writerEpoch, long nextGeneration);

    /** Reads the lane cursor/owner without changing it. */
    Optional<WebhookDispatchLaneState> readLane(String taskId);

    /** Reads only the lane cursor/owner without materializing loss diagnostics. */
    default Optional<WebhookDispatchLanePosition> readPosition(String taskId) {
        return readLane(taskId).map(lane -> new WebhookDispatchLanePosition(lane.taskId(), lane.writerEpoch(),
                lane.nextGeneration(), lane.holderToken()));
    }

    /** Atomically acquires the lane without consuming a canonical generation. */
    Optional<WebhookDispatchNoopClaim> claimNoop(String taskId, long writerEpoch,
            long expectedNextGeneration);

    /** Releases exact zero-width ownership without advancing the canonical cursor. */
    boolean releaseNoop(WebhookDispatchNoopClaim claim);

    /** Completes a settled claim and records an explicit uncertain/deadline loss for its range. */
    boolean completeWithLoss(WebhookDispatchClaim claim, String reason);

    /** Records a known dropped canonical admission without advancing the cursor or overlapping a holder. */
    boolean recordAdmissionLoss(String taskId, long writerEpoch, long firstGeneration,
            long lastGeneration, String reason);

    /** Moves an unclaimed lane to a new fenced epoch while preserving its accepted cursor. */
    boolean rebaseUnclaimedLane(String taskId, long expectedWriterEpoch, long newWriterEpoch);

    /** Explicitly retires a claimed range after the old outbound worker is proven quiescent. */
    boolean recoverQuiescedClaim(WebhookDispatchClaim abandonedClaim, long newWriterEpoch);

    /** Explicitly releases an abandoned zero-width no-op claim after worker quiescence, without moving the cursor. */
    boolean recoverQuiescedNoop(WebhookDispatchNoopClaim abandonedClaim, long newWriterEpoch);
}
