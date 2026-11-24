package org.a2aproject.sdk.extras.taskstore.database.jpa;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.tasks.TaskPersistenceException;
import org.a2aproject.sdk.server.tasks.TaskSerializationException;
import org.a2aproject.sdk.spec.Task;
import org.jspecify.annotations.Nullable;

/**
 * Persisted baseline migration and writer-epoch transition for shared task storage.
 *
 * <p><b>Deployment precondition.</b> {@link #activate(long)} must only be called after all old writers
 * were drained and their database identities denied write privileges (for example with {@code REVOKE});
 * nothing here can detect an old binary. Until a writer epoch is activated the store is in legacy mode:
 * rows are neither stamped nor baselined. Once activated, every write by this store requires its configured
 * node epoch ({@value #NODE_EPOCH_PROPERTY}) to equal the stored epoch and stamps the row's
 * {@code writer_epoch} and {@code migration_marker} (outside the Task JSON), and a legacy row (no marker)
 * is baselined exactly once, under its row lock, at its first atomic read or mutation: generation 0
 * becomes 1 and history, artifacts and their absent roles are preserved; no earlier timeline is invented.
 * A marked row without a canonical generation is corrupt and is never re-initialized.</p>
 */
@ApplicationScoped
public class JpaTaskMigration {

    /** Per-task marker value written once a row has been baselined or written under an active epoch. */
    public static final String BASELINE_MARKER = "a2a-1.1-baseline";

    /** Writer epoch of this node; must equal the stored epoch for writes once storage is activated. */
    public static final String NODE_EPOCH_PROPERTY = "a2a.task-store.writer-epoch";

    private static final int BULK_BATCH = 100;

    /** A write was refused because this node's epoch does not match the activated storage epoch. */
    public static final class StaleWriterEpochException extends TaskPersistenceException {
        StaleWriterEpochException(@Nullable Long nodeEpoch, long storedEpoch) {
            super((nodeEpoch == null ? "No writer epoch is configured" : "Writer epoch " + nodeEpoch)
                    + " (" + NODE_EPOCH_PROPERTY + ") does not match the active storage epoch " + storedEpoch);
        }
    }

    @PersistenceContext(unitName = "a2a-java")
    EntityManager em;

    @Inject
    A2AConfigProvider configProvider;

    @Inject
    JpaTaskMutationTransactions mutationTransactions;

    @Inject
    JpaTaskMigration self;

    /** Epoch of this node from configuration, or {@code null} when none is configured (no default exists). */
    public @Nullable Long nodeEpoch() {
        return configProvider.getOptionalValue(NODE_EPOCH_PROPERTY).map(Long::valueOf).orElse(null);
    }

    /** Returns the activated storage epoch, or {@code null} in legacy mode. Runs in the caller's transaction. */
    public @Nullable Long storedEpoch() {
        List<Long> rows = em.createQuery("SELECT e.epoch FROM JpaWriterEpoch e WHERE e.id = :id", Long.class)
                .setParameter("id", JpaWriterEpoch.SINGLETON_ID).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Returns the epoch to stamp on writes, or {@code null} in legacy mode. Throws
     * {@link StaleWriterEpochException} when this node's epoch is not the active one. Nothing is written.
     */
    public @Nullable Long checkWritable() {
        Long stored = storedEpoch();
        if (stored == null) {
            return null;
        }
        Long node = nodeEpoch();
        if (node == null || node.longValue() != stored.longValue()) {
            throw new StaleWriterEpochException(node, stored);
        }
        return stored;
    }

    /**
     * Activates (or raises) the storage writer epoch. Epochs never decrease. Call it only after old writers
     * are fenced at the database. Concurrent writers by this store hold no lock on the epoch row, so an
     * in-flight write may complete just after activation; the database fence, not this call, excludes old
     * writers.
     */
    public long activate(long epoch) {
        if (epoch <= 0) {
            throw new IllegalArgumentException("epoch must be greater than zero");
        }
        try {
            return self.install(epoch);
        } catch (RuntimeException concurrentInstall) {
            if (concurrentInstall instanceof IllegalStateException) {
                throw concurrentInstall;
            }
            // Another activator may have created the row first; the retry only updates it.
            try {
                return self.install(epoch);
            } catch (RuntimeException second) {
                second.addSuppressed(concurrentInstall);
                throw second;
            }
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public long install(long epoch) {
        @Nullable JpaWriterEpoch row =
                em.find(JpaWriterEpoch.class, JpaWriterEpoch.SINGLETON_ID, LockModeType.PESSIMISTIC_WRITE);
        if (row == null) {
            em.persist(new JpaWriterEpoch(JpaWriterEpoch.SINGLETON_ID, epoch));
        } else if (epoch < row.getEpoch()) {
            throw new IllegalStateException("Writer epoch " + epoch + " is older than the active epoch " + row.getEpoch());
        } else {
            row.setEpoch(epoch);
        }
        em.flush();
        return epoch;
    }

    /**
     * Baselines every task row that has no migration marker, one transaction per task. Requires an activated
     * epoch. Returns the number of rows examined in this run.
     */
    public int migrateAll() {
        int examined = 0;
        String after = "";
        while (true) {
            List<String> ids = self.unmigratedIds(after);
            if (ids.isEmpty()) {
                return examined;
            }
            for (String id : ids) {
                mutationTransactions.read(id);
                examined++;
            }
            after = ids.get(ids.size() - 1);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<String> unmigratedIds(String after) {
        if (storedEpoch() == null) {
            throw new IllegalStateException("Activate a writer epoch before migrating tasks");
        }
        return em.createQuery("SELECT t.id FROM JpaTask t WHERE t.migrationMarker IS NULL AND t.id > :after "
                + "ORDER BY t.id", String.class).setParameter("after", after).setMaxResults(BULK_BATCH).getResultList();
    }

    /**
     * Under the row lock and an active epoch: stamps the row and, for a legacy row, establishes the
     * generation-1 baseline once. Revision changes only when the stored Task changed (or none existed).
     */
    public void baseline(JpaTask row, long epoch, JpaTaskRevisionAllocator revisions) {
        Task task = parse(row);
        if (row.getMigrationMarker() != null) {
            requireCanonicalGeneration(row, task);
            return;
        }
        boolean changed = task.generation() == 0;
        if (changed) {
            try {
                row.setTask(Task.builder(task).generation(1).build());
            } catch (JsonProcessingException e) {
                throw new TaskSerializationException(row.getId(), "Failed to serialize baselined task", e);
            }
        }
        row.setMigrationMarker(BASELINE_MARKER);
        row.setWriterEpoch(epoch);
        Long revision = row.getRevision();
        if (revision == null) {
            row.setRevision(revisions.newRevision());
        } else if (changed) {
            row.setRevision(revisions.successor(revision));
        }
    }

    /** A marked row must carry a canonical generation; this fails closed instead of resetting it. */
    static void requireCanonicalGeneration(JpaTask row, Task task) {
        if (row.getMigrationMarker() != null && task.generation() <= 0) {
            throw new TaskSerializationException(row.getId(),
                    "Migrated task row has no canonical generation and is corrupt");
        }
    }

    /**
     * Stamps a written row under an active epoch (no-op in legacy mode). The marker is written only for a
     * canonical generation, so a still-uncanonical legacy-form task is baselined at its next access instead
     * of being marked corrupt.
     */
    static void stamp(JpaTask row, Task task, @Nullable Long epoch) {
        if (epoch == null) {
            return;
        }
        row.setWriterEpoch(epoch);
        if (task.generation() >= 1) {
            row.setMigrationMarker(BASELINE_MARKER);
        }
    }

    private static Task parse(JpaTask row) {
        try {
            return row.getTask();
        } catch (JsonProcessingException e) {
            throw new TaskSerializationException(row.getId(), "Failed to deserialize task from database", e);
        }
    }
}
