package org.a2aproject.sdk.server.tasks;

import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.util.Assert;

/**
 * Read-only view of a local ordered-publication position.
 *
 * <p>An atomic store implementation creates a {@link Reservation} while reserving its ordered
 * publication position inside the task's atomic boundary. It gives this view to the reservation
 * callback and retains the reservation handle privately. The callback can observe state but cannot
 * transition the slot. A successful cancellation suppresses publication; cancellation after an
 * unknown storage outcome does not establish that storage rolled back.</p>
 *
 * <p>The reservation callback may retain this view, but must not publish, perform network I/O, or
 * call executor or user code. State transitions are one-way: reserved to publishable, or reserved
 * to cancelled. A terminal state cannot be changed.</p>
 */
public final class TaskCommitSlot {

    private enum State {
        RESERVED,
        PUBLISHABLE,
        CANCELLED
    }

    private final String taskId;
    private final TaskRevision revision;
    private final AtomicReference<State> state = new AtomicReference<>(State.RESERVED);

    private TaskCommitSlot(String taskId, TaskRevision revision) {
        this.taskId = taskId;
        this.revision = revision;
    }

    /**
     * Creates a reservation handle for an atomic store implementation to retain privately while it
     * performs a task replacement.
     */
    public static Reservation reserve(String taskId, TaskRevision revision) {
        return new Reservation(new TaskCommitSlot(
                Assert.checkNotNullParam("taskId", taskId),
                Assert.checkNotNullParam("revision", revision)));
    }

    public String taskId() {
        return taskId;
    }

    public TaskRevision revision() {
        return revision;
    }

    /** Returns whether the corresponding storage commit completed definitively. */
    public boolean isPublishable() {
        return state.get() == State.PUBLISHABLE;
    }

    /**
     * Returns whether local publication has been suppressed. This does not indicate whether storage
     * committed, particularly after an unknown storage outcome.
     */
    public boolean isCancelled() {
        return state.get() == State.CANCELLED;
    }

    /** Store-owned lifecycle handle; do not pass this handle to the reservation callback. */
    public static final class Reservation {

        private final TaskCommitSlot slot;

        private Reservation(TaskCommitSlot slot) {
            this.slot = slot;
        }

        /** Returns the read-only view to retain or pass to the reservation callback. */
        public TaskCommitSlot slot() {
            return slot;
        }

        /**
         * Makes the slot publishable after the associated task replacement is definitively
         * committed.
         *
         * @throws IllegalStateException if this slot is already cancelled or committed
         */
        public void markCommitted() {
            if (!slot.state.compareAndSet(State.RESERVED, State.PUBLISHABLE)) {
                throw new IllegalStateException("Commit slot is no longer reserved");
            }
        }

        /**
         * Cancels a still-reserved publication slot. This suppresses local publication but does
         * not prove that storage rolled back. Calls after either terminal state leave that state
         * unchanged.
         */
        public void cancel() {
            slot.state.compareAndSet(State.RESERVED, State.CANCELLED);
        }
    }
}
