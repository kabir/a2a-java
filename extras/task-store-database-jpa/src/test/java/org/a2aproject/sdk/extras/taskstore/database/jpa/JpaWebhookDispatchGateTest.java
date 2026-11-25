package org.a2aproject.sdk.extras.taskstore.database.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.a2aproject.sdk.server.tasks.WebhookDispatchClaim;
import org.a2aproject.sdk.server.tasks.WebhookDispatchLaneState;
import org.a2aproject.sdk.server.tasks.WebhookDispatchNoopClaim;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/** Cross-instance gate contract exercised against a disposable PostgreSQL database. */
@QuarkusTest
@TestProfile(JpaWebhookDispatchPostgresProfile.class)
@DisabledIfSystemProperty(named = "skipDockerTests", matches = "true")
class JpaWebhookDispatchGateTest {

    @Inject
    JpaWebhookDispatchGateTransactions transactions;

    @Inject
    JpaTaskMigration migration;

    @Test
    void distinctGateInstancesCannotClaimOverlappingOrOutOfOrderRanges() throws Exception {
        String taskId = "webhook-gate-" + UUID.randomUUID();
        migration.activate(9);
        JpaWebhookDispatchGate firstInstance = new JpaWebhookDispatchGate(transactions);
        JpaWebhookDispatchGate secondInstance = new JpaWebhookDispatchGate(transactions);
        assertTrue(transactions.initializeTaskLane(taskId, 9, 1));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<java.util.Optional<WebhookDispatchClaim>> first = workers.submit(() -> {
                start.await();
                return firstInstance.claim(taskId, 9, 1, 2);
            });
            Future<java.util.Optional<WebhookDispatchClaim>> second = workers.submit(() -> {
                start.await();
                return secondInstance.claim(taskId, 9, 1, 1);
            });
            start.countDown();
            List<WebhookDispatchClaim> claims = new ArrayList<>();
            first.get().ifPresent(claims::add);
            second.get().ifPresent(claims::add);
            assertEquals(1, claims.size(), "only one database transaction may own the exact next range");

            WebhookDispatchClaim winner = claims.get(0);
            assertTrue(firstInstance.complete(winner));
            assertFalse(secondInstance.complete(winner));
            assertTrue(secondInstance.claim(taskId, 9, 1, 2).isEmpty(), "completed generations cannot replay");
            assertTrue(firstInstance.claim(taskId, 9, 4, 4).isEmpty(), "a later range cannot skip the cursor");
            long nextBeforeGap = winner.lastGeneration() + 1;
            assertTrue(firstInstance.retireUnclaimedGap(taskId, 9, nextBeforeGap, 3));

            WebhookDispatchClaim next = secondInstance.claim(taskId, 9, 4, 4).orElseThrow();
            WebhookDispatchLaneState state = transactions.readLane(taskId).orElseThrow();
            assertEquals(4, state.nextGeneration());
            assertTrue(secondInstance.complete(next));

            WebhookDispatchClaim abandoned = firstInstance.claim(taskId, 9, 5, 5).orElseThrow();
            migration.activate(10);
            assertFalse(firstInstance.complete(abandoned), "the old epoch cannot release after fencing");
            assertTrue(secondInstance.recoverQuiescedClaim(abandoned, 10),
                    "explicit recovery requires the exact claim and active replacement epoch");
            WebhookDispatchLaneState recovered = transactions.readLane(taskId).orElseThrow();
            assertEquals(10, recovered.writerEpoch());
            assertEquals(6, recovered.nextGeneration());
            assertEquals(2, recovered.losses().size());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void quiescedNoopRecoveryAcrossEpochsKeepsTheSameCursorAndRequiresExactOwnership() {
        String taskId = "webhook-noop-recovery-" + UUID.randomUUID();
        Long storedEpoch = migration.storedEpoch();
        long epoch = storedEpoch == null ? 9 : storedEpoch + 1;
        long replacementEpoch = epoch + 1;
        migration.activate(epoch);
        JpaWebhookDispatchGate gate = new JpaWebhookDispatchGate(transactions);
        assertTrue(transactions.initializeTaskLane(taskId, epoch, 12));
        WebhookDispatchNoopClaim claim = gate.claimNoop(taskId, epoch, 12).orElseThrow();

        migration.activate(replacementEpoch);
        assertFalse(gate.releaseNoop(claim), "ordinary release must reject a stale node epoch");
        assertFalse(gate.recoverQuiescedNoop(new WebhookDispatchNoopClaim(
                taskId, epoch, "wrong-token", 12), replacementEpoch));
        assertTrue(gate.recoverQuiescedNoop(claim, replacementEpoch));

        WebhookDispatchLaneState recovered = transactions.readLane(taskId).orElseThrow();
        assertEquals(replacementEpoch, recovered.writerEpoch());
        assertEquals(12, recovered.nextGeneration(), "zero-width recovery never advances canonical positions");
        assertNull(recovered.holderToken());
        assertTrue(gate.claimNoop(taskId, replacementEpoch, 12).isPresent());
    }

    @Test
    void admissionLossJournalConsumesExactNextAndFutureRangesAfterHeldClaimCompletes() {
        String taskId = "webhook-loss-journal-" + UUID.randomUUID();
        long epoch = migration.storedEpoch() == null ? 9 : migration.storedEpoch() + 1;
        migration.activate(epoch);
        JpaWebhookDispatchGate gate = new JpaWebhookDispatchGate(transactions);
        assertTrue(transactions.initializeTaskLane(taskId, epoch, 1));

        assertTrue(gate.recordAdmissionLoss(taskId, epoch, 1, 1, "exact-next loss"));
        assertEquals(2, gate.readLane(taskId).orElseThrow().nextGeneration());
        WebhookDispatchClaim held = gate.claim(taskId, epoch, 2, 2).orElseThrow();
        assertTrue(gate.recordAdmissionLoss(taskId, epoch, 3, 3, "future loss behind held generation"));
        assertEquals(2, gate.readLane(taskId).orElseThrow().nextGeneration());
        assertTrue(gate.complete(held));

        WebhookDispatchLaneState settled = gate.readLane(taskId).orElseThrow();
        assertEquals(4, settled.nextGeneration());
        assertEquals(List.of(1L, 3L), settled.losses().stream()
                .map(org.a2aproject.sdk.server.tasks.WebhookDispatchLoss::firstGeneration).toList());
    }

    @Test
    void newerEpochAdmissionLossWaitsBehindOldHolderAndDrainsOnExplicitRecovery() {
        String taskId = "webhook-new-epoch-loss-" + UUID.randomUUID();
        long oldEpoch = migration.storedEpoch() == null ? 9 : migration.storedEpoch() + 1;
        long newEpoch = oldEpoch + 1;
        migration.activate(oldEpoch);
        JpaWebhookDispatchGate gate = new JpaWebhookDispatchGate(transactions);
        assertTrue(transactions.initializeTaskLane(taskId, oldEpoch, 1));
        WebhookDispatchClaim oldHolder = gate.claim(taskId, oldEpoch, 1, 1).orElseThrow();

        migration.activate(newEpoch);
        assertTrue(gate.recordAdmissionLoss(taskId, newEpoch, 2, 2, "new epoch admission capacity loss"));
        assertEquals(1, gate.readLane(taskId).orElseThrow().nextGeneration(),
                "the new epoch loss cannot clear or pass the old holder");
        assertTrue(gate.recoverQuiescedClaim(oldHolder, newEpoch));

        WebhookDispatchLaneState recovered = gate.readLane(taskId).orElseThrow();
        assertEquals(newEpoch, recovered.writerEpoch());
        assertEquals(3, recovered.nextGeneration(), "the loss drains only after exact old-holder recovery");
        assertTrue(gate.claim(taskId, oldEpoch, 3, 3).isEmpty(), "old-stamped work cannot claim after rebase");
    }

    @Test
    void fullActionableJournalPreservesFutureRangesAcrossHistoricalSettlementAndRecovery() {
        String taskId = "webhook-full-actionable-journal-" + UUID.randomUUID();
        long epoch = migration.storedEpoch() == null ? 9 : migration.storedEpoch() + 1;
        long replacementEpoch = epoch + 1;
        migration.activate(epoch);
        JpaWebhookDispatchGate gate = new JpaWebhookDispatchGate(transactions);
        assertTrue(transactions.initializeTaskLane(taskId, epoch, 1));
        for (int index = 0; index < 257; index++) {
            long generation = 1000 + index * 2L;
            assertTrue(gate.recordAdmissionLoss(taskId, epoch, generation, generation, "future actionable loss"));
        }

        assertTrue(gate.recordAdmissionLoss(taskId, epoch, 1, 1, "exact-next loss"));
        assertEquals(2, gate.readLane(taskId).orElseThrow().nextGeneration());
        WebhookDispatchClaim settledWithLoss = gate.claim(taskId, epoch, 2, 2).orElseThrow();
        assertTrue(gate.completeWithLoss(settledWithLoss, "settled unsuccessfully"));
        WebhookDispatchClaim abandoned = gate.claim(taskId, epoch, 3, 3).orElseThrow();
        migration.activate(replacementEpoch);
        assertTrue(gate.recoverQuiescedClaim(abandoned, replacementEpoch));
        assertTrue(gate.retireUnclaimedGap(taskId, replacementEpoch, 4, 5));

        WebhookDispatchLaneState state = gate.readLane(taskId).orElseThrow();
        assertEquals(6, state.nextGeneration());
        assertEquals(257, state.losses().stream().filter(loss -> loss.firstGeneration() >= 1000).count(),
                "historical records must not prune actionable future loss ranges");
        assertEquals(261, state.losses().size());
        assertTrue(gate.claim(taskId, replacementEpoch, 1000, 1000).isEmpty(),
                "sparse future loss ranges cannot skip unrecorded generations");
        assertEquals(6, gate.readLane(taskId).orElseThrow().nextGeneration());
    }

    @Test
    void cursorAdvancementPrunesHistoricalRowsAndKeepsNewestDiagnostics() {
        String taskId = "webhook-historical-retention-" + UUID.randomUUID();
        long epoch = migration.storedEpoch() == null ? 9 : migration.storedEpoch() + 1;
        migration.activate(epoch);
        JpaWebhookDispatchGate gate = new JpaWebhookDispatchGate(transactions);
        assertTrue(transactions.initializeTaskLane(taskId, epoch, 1));
        for (int generation = 1; generation <= 300; generation++) {
            assertTrue(gate.recordAdmissionLoss(taskId, epoch, generation, generation, "known admission loss"));
        }

        WebhookDispatchLaneState state = gate.readLane(taskId).orElseThrow();
        assertEquals(301, state.nextGeneration());
        assertEquals(256, state.losses().size());
        assertEquals(45, state.losses().get(0).firstGeneration());
        assertEquals(300, state.losses().get(255).lastGeneration());
    }
}
