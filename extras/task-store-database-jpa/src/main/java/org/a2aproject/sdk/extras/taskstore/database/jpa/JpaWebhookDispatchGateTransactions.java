package org.a2aproject.sdk.extras.taskstore.database.jpa;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import org.a2aproject.sdk.server.tasks.WebhookDispatchClaim;
import org.a2aproject.sdk.server.tasks.WebhookDispatchLaneState;
import org.a2aproject.sdk.server.tasks.WebhookDispatchLanePosition;
import org.a2aproject.sdk.server.tasks.WebhookDispatchLoss;
import org.a2aproject.sdk.server.tasks.WebhookDispatchNoopClaim;

/** Short, independent lane transactions. Never hold a task row lock or this transaction across HTTP. */
@ApplicationScoped
public class JpaWebhookDispatchGateTransactions {

    private static final int MAX_HISTORICAL_LOSS_RECORDS = 256;

    @PersistenceContext(unitName = "a2a-java")
    EntityManager em;

    @jakarta.inject.Inject
    JpaTaskMigration migration;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean initializeTaskLane(String taskId, long writerEpoch, long nextGeneration) {
        if (nextGeneration < 1) {
            throw new IllegalArgumentException("nextGeneration must be positive");
        }
        if (!validStorageEpoch(writerEpoch)) {
            return false;
        }
        if (em.find(JpaWebhookDispatchLane.class, taskId) != null) {
            return false;
        }
        em.persist(new JpaWebhookDispatchLane(taskId, writerEpoch, nextGeneration));
        em.flush();
        return true;
    }

    /** Called by the task mutation transaction so lane creation commits with its first canonical task row. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void initializeTaskLaneInCurrentTransaction(String taskId, long writerEpoch, long nextGeneration) {
        if (nextGeneration < 1 || !validStorageEpoch(writerEpoch)) {
            throw new IllegalStateException("Webhook lane initialization does not match the active storage epoch");
        }
        if (em.find(JpaWebhookDispatchLane.class, taskId) == null) {
            em.persist(new JpaWebhookDispatchLane(taskId, writerEpoch, nextGeneration));
        }
    }

    /** Creates a migrated lane from accepted task generation without resetting an existing cursor or owner. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void initializeOrRebaseUnclaimedLaneInCurrentTransaction(String taskId, long writerEpoch,
            long nextGeneration) {
        if (nextGeneration < 1 || !validStorageEpoch(writerEpoch)) {
            throw new IllegalStateException("Webhook lane migration does not match the active storage epoch");
        }
        JpaWebhookDispatchLane lane = em.find(JpaWebhookDispatchLane.class, taskId, LockModeType.PESSIMISTIC_WRITE);
        if (lane == null) {
            em.persist(new JpaWebhookDispatchLane(taskId, writerEpoch, nextGeneration));
            return;
        }
        if (lane.getHolderToken() == null && writerEpoch >= lane.getWriterEpoch()) {
            // Migration advances the fencing epoch but keeps next_generation exactly as previously accepted.
            lane.setWriterEpoch(writerEpoch);
            drainRecordedLosses(lane);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean hasLane(String taskId) {
        return em.find(JpaWebhookDispatchLane.class, taskId) != null;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<WebhookDispatchLaneState> readLane(String taskId) {
        JpaWebhookDispatchLane lane = em.find(JpaWebhookDispatchLane.class, taskId);
        if (lane == null) {
            return Optional.empty();
        }
        List<JpaWebhookDispatchLoss> historicalLosses = em.createQuery(
                "SELECT loss FROM JpaWebhookDispatchLoss loss WHERE loss.taskId = :taskId "
                        + "AND loss.lastGeneration < :next ORDER BY loss.recordedAt DESC, loss.lossId DESC",
                JpaWebhookDispatchLoss.class)
                .setParameter("taskId", taskId)
                .setParameter("next", lane.getNextGeneration())
                .setMaxResults(MAX_HISTORICAL_LOSS_RECORDS)
                .getResultList();
        List<JpaWebhookDispatchLoss> actionableLosses = em.createQuery(
                "SELECT loss FROM JpaWebhookDispatchLoss loss WHERE loss.taskId = :taskId "
                        + "AND loss.lastGeneration >= :next ORDER BY loss.firstGeneration, loss.lastGeneration",
                JpaWebhookDispatchLoss.class)
                .setParameter("taskId", taskId)
                .setParameter("next", lane.getNextGeneration())
                .getResultList();
        List<WebhookDispatchLoss> losses = new ArrayList<>(historicalLosses.size() + actionableLosses.size());
        for (int index = historicalLosses.size() - 1; index >= 0; index--) {
            JpaWebhookDispatchLoss loss = historicalLosses.get(index);
            losses.add(new WebhookDispatchLoss(loss.getFirstGeneration(), loss.getLastGeneration(),
                    loss.getRecordedAt(), loss.getReason()));
        }
        for (JpaWebhookDispatchLoss loss : actionableLosses) {
            losses.add(new WebhookDispatchLoss(loss.getFirstGeneration(), loss.getLastGeneration(),
                    loss.getRecordedAt(), loss.getReason()));
        }
        return Optional.of(new WebhookDispatchLaneState(taskId, lane.getWriterEpoch(), lane.getNextGeneration(),
                lane.getHolderToken(), lane.getClaimedFirstGeneration(), lane.getClaimedLastGeneration(), losses));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<WebhookDispatchLanePosition> readPosition(String taskId) {
        JpaWebhookDispatchLane lane = em.find(JpaWebhookDispatchLane.class, taskId);
        if (lane == null) {
            return Optional.empty();
        }
        return Optional.of(new WebhookDispatchLanePosition(taskId, lane.getWriterEpoch(),
                lane.getNextGeneration(), lane.getHolderToken()));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<WebhookDispatchClaim> claim(String taskId, long writerEpoch,
            long firstGeneration, long lastGeneration) {
        if (firstGeneration < 1 || lastGeneration < firstGeneration || !validStorageEpoch(writerEpoch)) {
            return Optional.empty();
        }
        JpaWebhookDispatchLane lane = em.find(JpaWebhookDispatchLane.class, taskId, LockModeType.PESSIMISTIC_WRITE);
        if (lane == null) {
            return Optional.empty();
        }
        drainRecordedLosses(lane);
        if (lane.getWriterEpoch() != writerEpoch || lane.getNextGeneration() != firstGeneration
                || lane.getHolderToken() != null) {
            return Optional.empty();
        }
        String holder = UUID.randomUUID().toString();
        lane.setHolderToken(holder);
        lane.setClaimedFirstGeneration(firstGeneration);
        lane.setClaimedLastGeneration(lastGeneration);
        return Optional.of(new WebhookDispatchClaim(taskId, writerEpoch, holder, firstGeneration, lastGeneration));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean complete(WebhookDispatchClaim claim) {
        if (!validStorageEpoch(claim.writerEpoch())) {
            return false;
        }
        int updated = em.createQuery("UPDATE JpaWebhookDispatchLane lane SET lane.nextGeneration = :next, "
                + "lane.holderToken = NULL, lane.claimedFirstGeneration = NULL, lane.claimedLastGeneration = NULL "
                + "WHERE lane.taskId = :taskId AND lane.writerEpoch = :epoch AND lane.nextGeneration = :first "
                + "AND lane.holderToken = :holder AND lane.claimedFirstGeneration = :first "
                + "AND lane.claimedLastGeneration = :last")
                .setParameter("next", claim.lastGeneration() + 1)
                .setParameter("taskId", claim.taskId())
                .setParameter("epoch", claim.writerEpoch())
                .setParameter("first", claim.firstGeneration())
                .setParameter("last", claim.lastGeneration())
                .setParameter("holder", claim.holderToken())
                .executeUpdate();
        if (updated != 1) {
            return false;
        }
        JpaWebhookDispatchLane lane = lockedLane(claim.taskId());
        drainRecordedLosses(lane);
        return true;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean retireUnclaimedGap(String taskId, long writerEpoch,
            long expectedNextGeneration, long throughGeneration) {
        if (expectedNextGeneration < 1 || throughGeneration < expectedNextGeneration) {
            return false;
        }
        if (!validStorageEpoch(writerEpoch)) {
            return false;
        }
        int updated = em.createQuery("UPDATE JpaWebhookDispatchLane lane SET lane.nextGeneration = :next "
                + "WHERE lane.taskId = :taskId AND lane.writerEpoch = :epoch "
                + "AND lane.nextGeneration = :expected AND lane.holderToken IS NULL")
                .setParameter("next", throughGeneration + 1)
                .setParameter("taskId", taskId)
                .setParameter("epoch", writerEpoch)
                .setParameter("expected", expectedNextGeneration)
                .executeUpdate();
        if (updated != 1) {
            return false;
        }
        JpaWebhookDispatchLane lane = lockedLane(taskId);
        persistLoss(new JpaWebhookDispatchLoss(UUID.randomUUID().toString(), taskId,
                expectedNextGeneration, throughGeneration, Instant.now(), "unclaimed webhook range retired"), lane);
        drainRecordedLosses(lane);
        return true;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean recordAdmissionLoss(String taskId, long writerEpoch, long firstGeneration,
            long lastGeneration, String reason) {
        if (firstGeneration < 1 || lastGeneration < firstGeneration || !validStorageEpoch(writerEpoch)) {
            return false;
        }
        JpaWebhookDispatchLane lane = em.find(JpaWebhookDispatchLane.class, taskId, LockModeType.PESSIMISTIC_WRITE);
        if (lane == null || lane.getWriterEpoch() > writerEpoch) {
            return false;
        }
        Long holderFirst = lane.getClaimedFirstGeneration();
        Long holderLast = lane.getClaimedLastGeneration();
        if (lane.getHolderToken() != null && holderFirst != null && holderLast != null
                && firstGeneration <= holderLast && holderFirst <= lastGeneration) {
            return false;
        }
        if (lane.getHolderToken() == null && firstGeneration <= lane.getNextGeneration()
                && lastGeneration >= lane.getNextGeneration()) {
            lane.setNextGeneration(lastGeneration + 1);
        }
        persistLoss(new JpaWebhookDispatchLoss(UUID.randomUUID().toString(), taskId,
                firstGeneration, lastGeneration, Instant.now(), reason), lane);
        drainRecordedLosses(lane);
        return true;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<WebhookDispatchNoopClaim> claimNoop(String taskId, long writerEpoch,
            long expectedNextGeneration) {
        if (expectedNextGeneration < 1 || !validStorageEpoch(writerEpoch)) {
            return Optional.empty();
        }
        JpaWebhookDispatchLane lane = em.find(JpaWebhookDispatchLane.class, taskId, LockModeType.PESSIMISTIC_WRITE);
        if (lane == null) {
            return Optional.empty();
        }
        drainRecordedLosses(lane);
        if (lane.getWriterEpoch() != writerEpoch || lane.getNextGeneration() != expectedNextGeneration
                || lane.getHolderToken() != null) {
            return Optional.empty();
        }
        String holder = UUID.randomUUID().toString();
        lane.setHolderToken(holder);
        lane.setClaimedFirstGeneration(expectedNextGeneration);
        lane.setClaimedLastGeneration(expectedNextGeneration - 1);
        return Optional.of(new WebhookDispatchNoopClaim(taskId, writerEpoch, holder, expectedNextGeneration));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean releaseNoop(WebhookDispatchNoopClaim claim) {
        if (!validStorageEpoch(claim.writerEpoch())) {
            return false;
        }
        int updated = em.createQuery("UPDATE JpaWebhookDispatchLane lane SET lane.holderToken = NULL, "
                + "lane.claimedFirstGeneration = NULL, lane.claimedLastGeneration = NULL "
                + "WHERE lane.taskId = :taskId AND lane.writerEpoch = :epoch "
                + "AND lane.nextGeneration = :expected AND lane.holderToken = :holder "
                + "AND lane.claimedFirstGeneration = :expected AND lane.claimedLastGeneration = :last")
                .setParameter("taskId", claim.taskId())
                .setParameter("epoch", claim.writerEpoch())
                .setParameter("expected", claim.expectedNextGeneration())
                .setParameter("last", claim.expectedNextGeneration() - 1)
                .setParameter("holder", claim.holderToken())
                .executeUpdate();
        if (updated != 1) {
            return false;
        }
        drainRecordedLosses(lockedLane(claim.taskId()));
        return true;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean completeWithLoss(WebhookDispatchClaim claim, String reason) {
        if (!validStorageEpoch(claim.writerEpoch())) {
            return false;
        }
        int updated = em.createQuery("UPDATE JpaWebhookDispatchLane lane SET lane.nextGeneration = :next, "
                + "lane.holderToken = NULL, lane.claimedFirstGeneration = NULL, lane.claimedLastGeneration = NULL "
                + "WHERE lane.taskId = :taskId AND lane.writerEpoch = :epoch AND lane.nextGeneration = :first "
                + "AND lane.holderToken = :holder AND lane.claimedFirstGeneration = :first "
                + "AND lane.claimedLastGeneration = :last")
                .setParameter("next", claim.lastGeneration() + 1)
                .setParameter("taskId", claim.taskId())
                .setParameter("epoch", claim.writerEpoch())
                .setParameter("first", claim.firstGeneration())
                .setParameter("last", claim.lastGeneration())
                .setParameter("holder", claim.holderToken())
                .executeUpdate();
        if (updated != 1) {
            return false;
        }
        JpaWebhookDispatchLane lane = lockedLane(claim.taskId());
        persistLoss(new JpaWebhookDispatchLoss(UUID.randomUUID().toString(), claim.taskId(),
                claim.firstGeneration(), claim.lastGeneration(), Instant.now(), reason), lane);
        drainRecordedLosses(lane);
        return true;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean rebaseUnclaimedLane(String taskId, long expectedWriterEpoch, long newWriterEpoch) {
        if (newWriterEpoch < expectedWriterEpoch || !validStorageEpoch(newWriterEpoch)) {
            return false;
        }
        int updated = em.createQuery("UPDATE JpaWebhookDispatchLane lane SET lane.writerEpoch = :newEpoch "
                + "WHERE lane.taskId = :taskId AND lane.writerEpoch = :expectedEpoch AND lane.holderToken IS NULL")
                .setParameter("taskId", taskId)
                .setParameter("expectedEpoch", expectedWriterEpoch)
                .setParameter("newEpoch", newWriterEpoch)
                .executeUpdate();
        if (updated != 1) {
            return false;
        }
        drainRecordedLosses(lockedLane(taskId));
        return true;
    }

    /** Caller must verify old outbound workers/processes are quiescent before invoking this explicit recovery. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean recoverQuiescedClaim(WebhookDispatchClaim abandonedClaim, long newWriterEpoch) {
        if (newWriterEpoch < abandonedClaim.writerEpoch() || !validStorageEpoch(newWriterEpoch)) {
            return false;
        }
        int updated = em.createQuery("UPDATE JpaWebhookDispatchLane lane SET lane.writerEpoch = :newEpoch, "
                + "lane.nextGeneration = :next, lane.holderToken = NULL, "
                + "lane.claimedFirstGeneration = NULL, lane.claimedLastGeneration = NULL "
                + "WHERE lane.taskId = :taskId AND lane.writerEpoch = :oldEpoch "
                + "AND lane.nextGeneration = :first AND lane.holderToken = :holder "
                + "AND lane.claimedFirstGeneration = :first AND lane.claimedLastGeneration = :last")
                .setParameter("newEpoch", newWriterEpoch)
                .setParameter("next", abandonedClaim.lastGeneration() + 1)
                .setParameter("taskId", abandonedClaim.taskId())
                .setParameter("oldEpoch", abandonedClaim.writerEpoch())
                .setParameter("first", abandonedClaim.firstGeneration())
                .setParameter("last", abandonedClaim.lastGeneration())
                .setParameter("holder", abandonedClaim.holderToken())
                .executeUpdate();
        if (updated != 1) {
            return false;
        }
        JpaWebhookDispatchLane lane = lockedLane(abandonedClaim.taskId());
        persistLoss(new JpaWebhookDispatchLoss(UUID.randomUUID().toString(), abandonedClaim.taskId(),
                abandonedClaim.firstGeneration(), abandonedClaim.lastGeneration(), Instant.now(),
                "claimed webhook range retired after worker quiescence"), lane);
        drainRecordedLosses(lane);
        return true;
    }

    /** Caller must prove the old worker is quiescent; exact zero-width ownership is cleared without cursor advance. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean recoverQuiescedNoop(WebhookDispatchNoopClaim abandonedClaim, long newWriterEpoch) {
        if (newWriterEpoch < abandonedClaim.writerEpoch() || !validStorageEpoch(newWriterEpoch)) {
            return false;
        }
        long expected = abandonedClaim.expectedNextGeneration();
        int updated = em.createQuery("UPDATE JpaWebhookDispatchLane lane SET lane.writerEpoch = :newEpoch, "
                + "lane.holderToken = NULL, lane.claimedFirstGeneration = NULL, lane.claimedLastGeneration = NULL "
                + "WHERE lane.taskId = :taskId AND lane.writerEpoch = :oldEpoch "
                + "AND lane.nextGeneration = :expected AND lane.holderToken = :holder "
                + "AND lane.claimedFirstGeneration = :expected AND lane.claimedLastGeneration = :last")
                .setParameter("newEpoch", newWriterEpoch)
                .setParameter("taskId", abandonedClaim.taskId())
                .setParameter("oldEpoch", abandonedClaim.writerEpoch())
                .setParameter("expected", expected)
                .setParameter("last", expected - 1)
                .setParameter("holder", abandonedClaim.holderToken())
                .executeUpdate();
        if (updated != 1) {
            return false;
        }
        drainRecordedLosses(lockedLane(abandonedClaim.taskId()));
        return true;
    }

    private boolean validStorageEpoch(long requestedEpoch) {
        Long stored = migration.storedEpoch();
        return stored == null ? requestedEpoch == 0 : stored.longValue() == requestedEpoch;
    }

    private JpaWebhookDispatchLane lockedLane(String taskId) {
        JpaWebhookDispatchLane lane = em.find(JpaWebhookDispatchLane.class, taskId, LockModeType.PESSIMISTIC_WRITE);
        if (lane == null) {
            throw new IllegalStateException("Webhook lane disappeared during a locked transition: " + taskId);
        }
        em.refresh(lane);
        return lane;
    }

    private void drainRecordedLosses(JpaWebhookDispatchLane lane) {
        if (lane.getHolderToken() != null) {
            return;
        }
        while (true) {
            List<JpaWebhookDispatchLoss> covering = em.createQuery(
                    "SELECT loss FROM JpaWebhookDispatchLoss loss WHERE loss.taskId = :taskId "
                            + "AND loss.firstGeneration <= :next AND loss.lastGeneration >= :next "
                            + "ORDER BY loss.lastGeneration DESC, loss.recordedAt DESC", JpaWebhookDispatchLoss.class)
                    .setParameter("taskId", lane.getTaskId())
                    .setParameter("next", lane.getNextGeneration())
                    .setMaxResults(1)
                    .getResultList();
            if (covering.isEmpty()) {
                pruneHistoricalLosses(lane);
                return;
            }
            lane.setNextGeneration(covering.get(0).getLastGeneration() + 1);
        }
    }

    private void pruneHistoricalLosses(JpaWebhookDispatchLane lane) {
        Long count = em.createQuery("SELECT COUNT(loss) FROM JpaWebhookDispatchLoss loss "
                + "WHERE loss.taskId = :taskId AND loss.lastGeneration < :next", Long.class)
                .setParameter("taskId", lane.getTaskId())
                .setParameter("next", lane.getNextGeneration())
                .getSingleResult();
        long excess = count - MAX_HISTORICAL_LOSS_RECORDS;
        if (excess <= 0) {
            return;
        }
        List<JpaWebhookDispatchLoss> historical = em.createQuery(
                "SELECT loss FROM JpaWebhookDispatchLoss loss WHERE loss.taskId = :taskId "
                        + "AND loss.lastGeneration < :next ORDER BY loss.recordedAt ASC, loss.lossId ASC",
                JpaWebhookDispatchLoss.class)
                .setParameter("taskId", lane.getTaskId())
                .setParameter("next", lane.getNextGeneration())
                .setMaxResults(Math.toIntExact(excess))
                .getResultList();
        historical.forEach(em::remove);
    }

    private void persistLoss(JpaWebhookDispatchLoss loss, JpaWebhookDispatchLane lane) {
        boolean actionable = loss.getLastGeneration() >= lane.getNextGeneration();
        if (!actionable) {
            long historicalCount = em.createQuery("SELECT COUNT(loss) FROM JpaWebhookDispatchLoss loss "
                    + "WHERE loss.taskId = :taskId AND loss.lastGeneration < :next", Long.class)
                    .setParameter("taskId", loss.getTaskId())
                    .setParameter("next", lane.getNextGeneration())
                    .getSingleResult();
            if (historicalCount >= MAX_HISTORICAL_LOSS_RECORDS) {
                List<JpaWebhookDispatchLoss> historical = em.createQuery(
                        "SELECT loss FROM JpaWebhookDispatchLoss loss WHERE loss.taskId = :taskId "
                                + "AND loss.lastGeneration < :next ORDER BY loss.recordedAt ASC, loss.lossId ASC",
                        JpaWebhookDispatchLoss.class)
                        .setParameter("taskId", loss.getTaskId())
                        .setParameter("next", lane.getNextGeneration())
                        .setMaxResults(1)
                        .getResultList();
                if (historical.isEmpty()) {
                    throw new IllegalStateException("Historical webhook loss count changed while locked for task "
                            + loss.getTaskId());
                }
                em.remove(historical.get(0));
            }
        }
        em.persist(loss);
    }
}
