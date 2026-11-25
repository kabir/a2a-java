package org.a2aproject.sdk.extras.taskstore.database.jpa;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import org.a2aproject.sdk.extras.common.events.TaskFinalizedEvent;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.MutationPlan;
import org.a2aproject.sdk.server.tasks.TaskCommitSlot;
import org.a2aproject.sdk.server.tasks.TaskCommitSlotCallback;
import org.a2aproject.sdk.server.tasks.TaskMutation;
import org.a2aproject.sdk.server.tasks.TaskMutationNotCommittedException;
import org.a2aproject.sdk.server.tasks.TaskRevision;
import org.a2aproject.sdk.server.tasks.TaskSerializationException;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.Task;
import org.jspecify.annotations.Nullable;

/**
 * Transaction boundaries for atomic task mutations. Invoke only through the CDI proxy.
 *
 * <p>{@link #commit} runs in its own transaction: when it returns normally the database transaction
 * has <em>completed its commit</em>, so the caller may then make a reserved publication slot
 * publishable. Any exception it throws other than {@link TaskMutationNotCommittedException} may have
 * been raised by the commit itself, so the storage outcome is unknown. The row is locked
 * ({@code PESSIMISTIC_WRITE}) before planning, so guarded updates serialize; creation uniqueness is
 * enforced by the primary key and surfaces as a definite non-commit for the loser.</p>
 *
 * <p>{@link #joined} instead participates in the caller's transaction (for a push configuration store
 * sharing the persistence unit); the caller owns the commit and no publication slot is involved.</p>
 */
@ApplicationScoped
public class JpaTaskMutationTransactions {

    @PersistenceContext(unitName = "a2a-java")
    EntityManager em;

    @Inject
    Event<TaskFinalizedEvent> taskFinalizedEvent;

    @Inject
    JpaTaskRevisionAllocator revisions;

    @Inject
    JpaTaskMigration migration;

    @Inject
    JpaWebhookDispatchGateTransactions webhookDispatchLanes;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public @Nullable TaskSnapshot read(String taskId) {
        JpaTask row = em.find(JpaTask.class, taskId);
        if (row == null) {
            return null;
        }
        Long epoch = migration.storedEpoch();
        JpaWebhookDispatchLane lane = em.find(JpaWebhookDispatchLane.class, taskId);
        boolean laneNeedsEpoch = epoch != null && (lane == null
                || (lane.getHolderToken() == null && lane.getWriterEpoch() != epoch));
        if (epoch != null && (row.getMigrationMarker() == null || laneNeedsEpoch)) {
            // Active epoch: baseline legacy rows and initialize/rebase missing unclaimed lane metadata under
            // the task row lock. A claimed lane is never reinitialized by this path.
            migration.checkWritable();
            em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
            migration.baseline(row, epoch, revisions);
            em.flush();
        } else if (row.getRevision() == null) {
            // Row written before revisions existed: assign one exactly once under a lock.
            em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
            if (row.getRevision() == null) {
                row.setRevision(revisions.newRevision());
                em.flush();
            }
        }
        return snapshot(row);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public CommittedMutation commit(String taskId, TaskMutation mutation, TaskCommitSlotCallback slotCallback,
            AtomicReference<TaskCommitSlot.@Nullable Reservation> reservation) {
        return apply(taskId, mutation, slotCallback, reservation);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public CommittedMutation joined(String taskId, TaskMutation mutation) {
        return apply(taskId, mutation, TaskCommitSlotCallback.NONE, null);
    }

    private CommittedMutation apply(String taskId, TaskMutation mutation, TaskCommitSlotCallback slotCallback,
            @Nullable AtomicReference<TaskCommitSlot.@Nullable Reservation> reservationHolder) {
        try {
            Long epoch = migration.checkWritable();
            JpaTask row = em.find(JpaTask.class, taskId);
            long createRevision = 0;
            if (row != null) {
                // Reload under the row lock so the plan never sees state read before the lock was granted. A joined
                // caller transaction may hold unflushed changes to this row, which must reach the database first.
                em.flush();
                em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
            } else {
                // Never lock an absent row: InnoDB would take gap locks and racing creators would deadlock.
                // Creators serialize on the revision counter row (held until commit) and then re-read, so a
                // creator that lost the race plans against the winner's committed row like any guarded update.
                createRevision = revisions.newRevision();
                row = em.find(JpaTask.class, taskId, LockModeType.PESSIMISTIC_WRITE);
            }
            if (row != null && epoch != null) {
                migration.baseline(row, epoch, revisions);
            }
            TaskSnapshot current = row == null ? null : snapshotAssigningRevision(row);
            if (current != null) {
                // If an older single-node deployment has no lane yet, initialize from the locked,
                // accepted snapshot's next position, never from the incoming proposal's generation.
                webhookDispatchLanes.initializeOrRebaseUnclaimedLaneInCurrentTransaction(taskId,
                        epoch == null ? 0 : epoch, Math.max(1, current.task().generation() + 1));
            }
            MutationPlan plan;
            try {
                plan = java.util.Objects.requireNonNull(mutation.plan(current), "plan");
            } catch (RuntimeException failure) {
                throw new TaskMutationNotCommittedException(taskId, "Task mutation planning failed", failure);
            }
            if (!plan.changed()) {
                if (current == null) {
                    throw new TaskMutationNotCommittedException(taskId,
                            "An absent task cannot accept an unchanged mutation");
                }
                return new CommittedMutation(current, List.of(), false, epoch == null ? 0L : epoch);
            }
            if (!taskId.equals(plan.task().id())) {
                throw new TaskMutationNotCommittedException(taskId,
                        "Mutation plan task ID does not match the requested task ID");
            }

            long revision = row == null ? createRevision : revisions.successor(row.getRevision());
            TaskSnapshot next = new TaskSnapshot(plan.task(), new TaskRevision(revision));
            CommittedMutation committed = new CommittedMutation(next, plan.events(), true, epoch == null ? 0L : epoch);
            if (reservationHolder != null && !plan.events().isEmpty()) {
                reserve(taskId, next, committed, slotCallback, reservationHolder);
            }

            if (row == null) {
                // Generation 1 is the first canonical webhook position for a new task. Persisting this
                // metadata in the same transaction prevents first-arriving batches from choosing a baseline.
                webhookDispatchLanes.initializeTaskLaneInCurrentTransaction(taskId, epoch == null ? 0 : epoch, 1);
            }

            persist(taskId, row, plan.task(), revision, epoch);
            if (plan.task().status().state().isFinal()) {
                // Delivered to AFTER_SUCCESS observers only once this transaction has committed.
                // The original stamped terminal batch travels with it so replication forwards it unchanged.
                Object terminalBatch = plan.events().isEmpty()
                        ? null : new CommittedTaskBatch(next, plan.events());
                taskFinalizedEvent.fire(new TaskFinalizedEvent(taskId, plan.task(), terminalBatch,
                        mutation.isTaskProposal(), epoch == null ? 0L : epoch));
            }
            return committed;
        } catch (TaskMutationNotCommittedException failure) {
            throw failure;
        } catch (JpaTaskMigration.StaleWriterEpochException failure) {
            throw new TaskMutationNotCommittedException(taskId, "Writer epoch is not the active storage epoch", failure);
        } catch (TaskSerializationException failure) {
            // A corrupt stored row is read before anything is written: a definite non-commit.
            throw new TaskMutationNotCommittedException(taskId, "Stored task could not be read", failure);
        } catch (PersistenceException failure) {
            // Constraint violations (lost creation race) and lock failures surface here, inside the
            // transaction, so the whole mutation is rolled back.
            throw new TaskMutationNotCommittedException(taskId, "Task mutation was rejected by storage", failure);
        }
    }

    private void reserve(String taskId, TaskSnapshot next, CommittedMutation committed,
            TaskCommitSlotCallback slotCallback, AtomicReference<TaskCommitSlot.@Nullable Reservation> holder) {
        TaskCommitSlot.Reservation reservation = TaskCommitSlot.reserve(taskId, next.revision());
        holder.set(reservation);
        try {
            slotCallback.reserve(reservation.slot(), committed);
        } catch (RuntimeException failure) {
            reservation.cancel();
            throw new TaskMutationNotCommittedException(taskId,
                    "Task publication slot reservation failed", failure);
        } catch (Error failure) {
            reservation.cancel();
            throw failure;
        }
    }

    private void persist(String taskId, @Nullable JpaTask row, Task task, long revision, @Nullable Long epoch) {
        try {
            if (row == null) {
                JpaTask created = JpaTask.createFromTask(task);
                created.setRevision(revision);
                JpaTaskMigration.stamp(created, task, epoch);
                em.persist(created);
            } else {
                row.setTask(task);
                row.setRevision(revision);
                JpaTaskMigration.stamp(row, task, epoch);
            }
            em.flush();
        } catch (JsonProcessingException e) {
            throw new TaskMutationNotCommittedException(taskId, "Failed to serialize task for persistence",
                    new TaskSerializationException(taskId, "Failed to serialize task for persistence", e));
        }
    }

    private TaskSnapshot snapshotAssigningRevision(JpaTask row) {
        if (row.getRevision() == null) {
            row.setRevision(revisions.newRevision());
        }
        return snapshot(row);
    }

    private static TaskSnapshot snapshot(JpaTask row) {
        try {
            Task task = row.getTask();
            JpaTaskMigration.requireCanonicalGeneration(row, task);
            return new TaskSnapshot(task, new TaskRevision(row.getRevision()));
        } catch (JsonProcessingException e) {
            throw new TaskSerializationException(row.getId(), "Failed to deserialize task from database", e);
        }
    }
}
