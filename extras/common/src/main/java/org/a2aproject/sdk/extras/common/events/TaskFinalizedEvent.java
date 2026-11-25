package org.a2aproject.sdk.extras.common.events;

import org.jspecify.annotations.Nullable;

/**
 * CDI event fired when a task reaches a final state and is successfully persisted to the database.
 * This event is fired AFTER the database transaction commits, making it safe for downstream
 * components to assume the task is durably stored.
 *
 * <p>Used by the replicated queue manager to send the final task state before the poison pill,
 * ensuring correct event ordering across instances and eliminating race conditions.
 *
 * <p>A store that committed the terminal mutation through the atomic authority also supplies the
 * original stamped committed batch as an opaque value. This module cannot name the server type, so
 * consumers cast it. When it is absent (legacy {@code save} paths) consumers keep the legacy behavior.
 */
public class TaskFinalizedEvent {
    private final String taskId;
    private final Object task;  // Task type from org.a2aproject.sdk.spec - using Object to avoid dependency
    private final Object committedBatch;  // CommittedTaskBatch from server-common, or null - opaque to avoid dependency
    private final boolean taskProposal;
    private final @Nullable Long writerEpoch;

    public TaskFinalizedEvent(String taskId, Object task) {
        this(taskId, task, null, false, null);
    }

    public TaskFinalizedEvent(String taskId, Object task, Object committedBatch) {
        this(taskId, task, committedBatch, false, null);
    }

    /** Local post-commit metadata; it is never added to the replicated batch envelope. */
    public TaskFinalizedEvent(String taskId, Object task, Object committedBatch, boolean taskProposal,
            @Nullable Long writerEpoch) {
        this.taskId = taskId;
        this.task = task;
        this.committedBatch = committedBatch;
        this.taskProposal = taskProposal;
        this.writerEpoch = writerEpoch;
    }

    public String getTaskId() {
        return taskId;
    }

    public Object getTask() {
        return task;
    }

    /**
     * Returns the original committed terminal batch (a server-common {@code CommittedTaskBatch}) that produced
     * this final state, or {@code null} when the store has no committed batch for it.
     */
    public Object getCommittedBatch() {
        return committedBatch;
    }

    public boolean isTaskProposal() {
        return taskProposal;
    }

    public @Nullable Long getWriterEpoch() {
        return writerEpoch;
    }

    @Override
    public String toString() {
        return "TaskFinalizedEvent{taskId='" + taskId + "', task=" + task
                + ", committedBatch=" + (committedBatch != null) + "}";
    }
}
