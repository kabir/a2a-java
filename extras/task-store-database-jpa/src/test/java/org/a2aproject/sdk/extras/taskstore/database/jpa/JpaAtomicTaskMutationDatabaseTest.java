package org.a2aproject.sdk.extras.taskstore.database.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.a2aproject.sdk.server.tasks.TaskMutationNotCommittedException;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.junit.jupiter.api.Test;

/**
 * Runs the atomic mutation path on a real engine started on a fresh generated schema, including the
 * first-ever creation that initializes the revision counter, and racing creators/writers.
 */
abstract class JpaAtomicTaskMutationDatabaseTest {

    @Inject
    JpaDatabaseTaskStore store;

    @Test
    void firstCreationOnFreshSchemaAndConcurrentCreatorsAndWriters() throws Exception {
        String first = JpaAtomicTaskMutationTest.id();
        TaskSnapshot created = store.mutate(first, current -> JpaAtomicTaskMutationTest.plan(first, 1)).snapshot();
        assertEquals(created, store.readSnapshot(first));

        // Eight creators race for one ID: exactly one wins, every loser is rejected by its own planner.
        String contested = JpaAtomicTaskMutationTest.id();
        AtomicInteger sawExisting = new AtomicInteger();
        int winners = race(8, () -> {
            try {
                store.mutate(contested, current -> {
                    if (current != null) {
                        sawExisting.incrementAndGet();
                        throw new IllegalStateException("exists");
                    }
                    return JpaAtomicTaskMutationTest.plan(contested, 1);
                });
                return true;
            } catch (TaskMutationNotCommittedException rejected) {
                return false;
            }
        });
        assertEquals(1, winners);
        assertEquals(7, sawExisting.get());

        // Eight writers share one base snapshot: exactly one guarded write is accepted.
        TaskSnapshot base = store.readSnapshot(contested);
        assertNotNull(base);
        int accepted = race(8, () -> {
            try {
                store.mutate(contested, current -> {
                    if (!base.equals(current)) {
                        throw new IllegalStateException("stale");
                    }
                    return JpaAtomicTaskMutationTest.plan(contested, 2);
                });
                return true;
            } catch (TaskMutationNotCommittedException rejected) {
                return false;
            }
        });
        assertEquals(1, accepted);
        assertTrue(store.readSnapshot(contested).revision().value() > base.revision().value());
    }

    @Test
    void legacySaveCreatorsRacingAtomicCreatorsNeitherDeadlockNorLoseRows() throws Exception {
        for (int round = 0; round < 5; round++) {
            String id = JpaAtomicTaskMutationTest.id();
            AtomicInteger counter = new AtomicInteger();
            int done = race(8, () -> {
                if (counter.getAndIncrement() % 2 == 0) {
                    store.save(JpaAtomicTaskMutationTest.task(id, 1,
                            org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING), false);
                } else {
                    try {
                        store.mutate(id, current -> {
                            if (current != null) {
                                throw new IllegalStateException("exists");
                            }
                            return JpaAtomicTaskMutationTest.plan(id, 1);
                        });
                    } catch (TaskMutationNotCommittedException lostRace) {
                        // The planner rejected it behind another creator: expected.
                    }
                }
                return true;
            });
            assertEquals(8, done);
            assertNotNull(store.readSnapshot(id));
        }
    }

    private static int race(int threads, java.util.concurrent.Callable<Boolean> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                }));
            }
            assertTrue(ready.await(20, TimeUnit.SECONDS));
            start.countDown();
            int ok = 0;
            for (Future<Boolean> result : results) {
                ok += result.get(60, TimeUnit.SECONDS) ? 1 : 0;
            }
            return ok;
        } finally {
            pool.shutdownNow();
        }
    }
}
