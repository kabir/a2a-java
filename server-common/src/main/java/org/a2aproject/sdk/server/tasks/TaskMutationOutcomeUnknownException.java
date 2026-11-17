package org.a2aproject.sdk.server.tasks;

/**
 * Indicates that storage could not establish whether a mutation committed.
 *
 * <p>Callers must not treat this outcome as a rollback or automatically retry the mutation.</p>
 */
public class TaskMutationOutcomeUnknownException extends TaskStoreException {

    public TaskMutationOutcomeUnknownException(String taskId, String message) {
        super(taskId, message);
    }

    public TaskMutationOutcomeUnknownException(String taskId, String message, Throwable cause) {
        super(taskId, message, cause);
    }
}
