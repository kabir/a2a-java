package org.a2aproject.sdk.server.tasks;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

/** Process-local gate for the single-process in-memory task-store profile. */
@ApplicationScoped
public class InMemoryWebhookDispatchGate implements WebhookDispatchLaneStore {

    private static final int MAX_HISTORICAL_LOSSES = 256;

    private static final class Lane {
        long epoch;
        long next;
        @Nullable String holder;
        @Nullable Long first;
        @Nullable Long last;
        final List<WebhookDispatchLoss> losses = new ArrayList<>();

        Lane(long epoch, long next) {
            this.epoch = epoch;
            this.next = next;
        }
    }

    private final ConcurrentMap<String, Lane> lanes = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryWebhookDispatchGate() {
        this(Clock.systemUTC());
    }

    InMemoryWebhookDispatchGate(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean initializeTaskLane(String taskId, long writerEpoch, long nextGeneration) {
        if (nextGeneration < 1) {
            throw new IllegalArgumentException("nextGeneration must be positive");
        }
        return lanes.putIfAbsent(taskId, new Lane(writerEpoch, nextGeneration)) == null;
    }

    @Override
    public Optional<WebhookDispatchLaneState> readLane(String taskId) {
        Lane lane = lanes.get(taskId);
        if (lane == null) {
            return Optional.empty();
        }
        synchronized (lane) {
            return Optional.of(snapshot(taskId, lane));
        }
    }

    @Override
    public Optional<WebhookDispatchLanePosition> readPosition(String taskId) {
        Lane lane = lanes.get(taskId);
        if (lane == null) {
            return Optional.empty();
        }
        synchronized (lane) {
            return Optional.of(new WebhookDispatchLanePosition(taskId, lane.epoch, lane.next, lane.holder));
        }
    }

    @Override
    public Optional<WebhookDispatchClaim> claim(String taskId, long writerEpoch,
            long firstGeneration, long lastGeneration) {
        if (firstGeneration < 1 || lastGeneration < firstGeneration) {
            return Optional.empty();
        }
        Lane lane = lanes.get(taskId);
        if (lane == null) {
            return Optional.empty();
        }
        synchronized (lane) {
            drainRecordedLosses(lane);
            if (lane.epoch != writerEpoch || lane.holder != null || lane.next != firstGeneration) {
                return Optional.empty();
            }
            lane.holder = UUID.randomUUID().toString();
            lane.first = firstGeneration;
            lane.last = lastGeneration;
            return Optional.of(new WebhookDispatchClaim(taskId, writerEpoch, lane.holder,
                    firstGeneration, lastGeneration));
        }
    }

    @Override
    public boolean complete(WebhookDispatchClaim claim) {
        Lane lane = lanes.get(claim.taskId());
        if (lane == null) {
            return false;
        }
        synchronized (lane) {
            if (!matches(lane, claim)) {
                return false;
            }
            lane.next = claim.lastGeneration() + 1;
            clearOwner(lane);
            drainRecordedLosses(lane);
            return true;
        }
    }

    @Override
    public boolean retireUnclaimedGap(String taskId, long writerEpoch,
            long expectedNextGeneration, long throughGeneration) {
        Lane lane = lanes.get(taskId);
        if (lane == null || throughGeneration < expectedNextGeneration) {
            return false;
        }
        synchronized (lane) {
            if (lane.epoch != writerEpoch || lane.holder != null || lane.next != expectedNextGeneration) {
                return false;
            }
            lane.next = throughGeneration + 1;
            recordLoss(lane, new WebhookDispatchLoss(expectedNextGeneration, throughGeneration,
                    Instant.now(clock), "unclaimed webhook range retired"));
            drainRecordedLosses(lane);
            return true;
        }
    }

    @Override
    public Optional<WebhookDispatchNoopClaim> claimNoop(String taskId, long writerEpoch,
            long expectedNextGeneration) {
        Lane lane = lanes.get(taskId);
        if (lane == null) {
            return Optional.empty();
        }
        synchronized (lane) {
            drainRecordedLosses(lane);
            if (lane.epoch != writerEpoch || lane.holder != null || lane.next != expectedNextGeneration) {
                return Optional.empty();
            }
            lane.holder = UUID.randomUUID().toString();
            lane.first = expectedNextGeneration;
            lane.last = expectedNextGeneration - 1;
            return Optional.of(new WebhookDispatchNoopClaim(taskId, writerEpoch, lane.holder,
                    expectedNextGeneration));
        }
    }

    @Override
    public boolean releaseNoop(WebhookDispatchNoopClaim claim) {
        Lane lane = lanes.get(claim.taskId());
        if (lane == null) {
            return false;
        }
        synchronized (lane) {
            if (lane.epoch != claim.writerEpoch() || lane.next != claim.expectedNextGeneration()
                    || !claim.holderToken().equals(lane.holder)
                    || !Long.valueOf(claim.expectedNextGeneration()).equals(lane.first)
                    || !Long.valueOf(claim.expectedNextGeneration() - 1).equals(lane.last)) {
                return false;
            }
            clearOwner(lane);
            drainRecordedLosses(lane);
            return true;
        }
    }

    @Override
    public boolean completeWithLoss(WebhookDispatchClaim claim, String reason) {
        Lane lane = lanes.get(claim.taskId());
        if (lane == null) {
            return false;
        }
        synchronized (lane) {
            if (!matches(lane, claim)) {
                return false;
            }
            lane.next = claim.lastGeneration() + 1;
            clearOwner(lane);
            recordLoss(lane, new WebhookDispatchLoss(claim.firstGeneration(), claim.lastGeneration(),
                    Instant.now(clock), reason));
            drainRecordedLosses(lane);
            return true;
        }
    }

    @Override
    public boolean recordAdmissionLoss(String taskId, long writerEpoch, long firstGeneration,
            long lastGeneration, String reason) {
        if (firstGeneration < 1 || lastGeneration < firstGeneration) {
            return false;
        }
        Lane lane = lanes.get(taskId);
        if (lane == null) {
            return false;
        }
        synchronized (lane) {
            if (lane.epoch > writerEpoch) {
                return false;
            }
            if (lane.holder != null && lane.first != null && lane.last != null
                    && firstGeneration <= lane.last && lane.first <= lastGeneration) {
                return false;
            }
            drainRecordedLosses(lane);
            boolean immediatelyConsumable = lane.holder == null
                    && firstGeneration <= lane.next && lastGeneration >= lane.next;
            if (immediatelyConsumable) {
                lane.next = lastGeneration + 1;
            }
            if (!recordLoss(lane, new WebhookDispatchLoss(firstGeneration, lastGeneration,
                    Instant.now(clock), reason))) {
                return false;
            }
            drainRecordedLosses(lane);
            return true;
        }
    }

    @Override
    public boolean rebaseUnclaimedLane(String taskId, long expectedWriterEpoch, long newWriterEpoch) {
        Lane lane = lanes.get(taskId);
        if (lane == null) {
            return false;
        }
        synchronized (lane) {
            if (lane.epoch != expectedWriterEpoch || lane.holder != null || newWriterEpoch < expectedWriterEpoch) {
                return false;
            }
            lane.epoch = newWriterEpoch;
            drainRecordedLosses(lane);
            return true;
        }
    }

    @Override
    public boolean recoverQuiescedClaim(WebhookDispatchClaim abandonedClaim, long newWriterEpoch) {
        Lane lane = lanes.get(abandonedClaim.taskId());
        if (lane == null) {
            return false;
        }
        synchronized (lane) {
            if (!matches(lane, abandonedClaim) || newWriterEpoch < lane.epoch) {
                return false;
            }
            lane.next = abandonedClaim.lastGeneration() + 1;
            lane.epoch = newWriterEpoch;
            clearOwner(lane);
            recordLoss(lane, new WebhookDispatchLoss(abandonedClaim.firstGeneration(),
                    abandonedClaim.lastGeneration(), Instant.now(clock),
                    "claimed webhook range retired after worker quiescence"));
            drainRecordedLosses(lane);
            return true;
        }
    }

    @Override
    public boolean recoverQuiescedNoop(WebhookDispatchNoopClaim abandonedClaim, long newWriterEpoch) {
        Lane lane = lanes.get(abandonedClaim.taskId());
        if (lane == null) {
            return false;
        }
        synchronized (lane) {
            long expected = abandonedClaim.expectedNextGeneration();
            if (lane.epoch != abandonedClaim.writerEpoch() || lane.next != expected
                    || !abandonedClaim.holderToken().equals(lane.holder)
                    || !Long.valueOf(expected).equals(lane.first)
                    || !Long.valueOf(expected - 1).equals(lane.last) || newWriterEpoch < lane.epoch) {
                return false;
            }
            lane.epoch = newWriterEpoch;
            clearOwner(lane);
            drainRecordedLosses(lane);
            return true;
        }
    }

    private static boolean matches(Lane lane, WebhookDispatchClaim claim) {
        return lane.epoch == claim.writerEpoch() && lane.next == claim.firstGeneration()
                && claim.holderToken().equals(lane.holder)
                && Long.valueOf(claim.firstGeneration()).equals(lane.first)
                && Long.valueOf(claim.lastGeneration()).equals(lane.last);
    }

    private static void clearOwner(Lane lane) {
        lane.holder = null;
        lane.first = null;
        lane.last = null;
    }

    private static boolean recordLoss(Lane lane, WebhookDispatchLoss loss) {
        boolean actionable = loss.lastGeneration() >= lane.next;
        if (!actionable) {
            List<Integer> historical = new ArrayList<>();
            for (int index = 0; index < lane.losses.size(); index++) {
                if (lane.losses.get(index).lastGeneration() < lane.next) {
                    historical.add(index);
                }
            }
            if (historical.size() >= MAX_HISTORICAL_LOSSES) {
                lane.losses.remove((int) historical.get(0));
            }
        }
        lane.losses.add(loss);
        return true;
    }

    private static void drainRecordedLosses(Lane lane) {
        if (lane.holder != null) {
            return;
        }
        while (true) {
            WebhookDispatchLoss covering = null;
            for (WebhookDispatchLoss loss : lane.losses) {
                if (loss.firstGeneration() <= lane.next && loss.lastGeneration() >= lane.next
                        && (covering == null || loss.lastGeneration() > covering.lastGeneration())) {
                    covering = loss;
                }
            }
            if (covering == null) {
                pruneHistoricalLosses(lane);
                return;
            }
            lane.next = covering.lastGeneration() + 1;
        }
    }

    private static void pruneHistoricalLosses(Lane lane) {
        List<Integer> historical = new ArrayList<>();
        for (int index = 0; index < lane.losses.size(); index++) {
            if (lane.losses.get(index).lastGeneration() < lane.next) {
                historical.add(index);
            }
        }
        int excess = historical.size() - MAX_HISTORICAL_LOSSES;
        for (int index = excess - 1; index >= 0; index--) {
            lane.losses.remove((int) historical.get(index));
        }
    }

    private static WebhookDispatchLaneState snapshot(String taskId, Lane lane) {
        return new WebhookDispatchLaneState(taskId, lane.epoch, lane.next, lane.holder,
                lane.first, lane.last, lane.losses);
    }
}
