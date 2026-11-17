package org.a2aproject.sdk.server.tasks;

/** Indicates that a mutation definitely did not change stored task state. */
public class TaskMutationNotCommittedException extends TaskStoreException {

    public TaskMutationNotCommittedException(String taskId, String message) {
        super(taskId, message);
    }

    public TaskMutationNotCommittedException(String taskId, String message, Throwable cause) {
        super(taskId, message, cause);
    }
}
