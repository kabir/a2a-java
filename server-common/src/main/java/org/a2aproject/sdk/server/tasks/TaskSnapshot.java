package org.a2aproject.sdk.server.tasks;

import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.util.Assert;

/** An immutable task value and the storage revision assigned to that exact value. */
public record TaskSnapshot(Task task, TaskRevision revision) {

    public TaskSnapshot {
        Assert.checkNotNullParam("task", task);
        Assert.checkNotNullParam("revision", revision);
        task = ImmutableTaskCopy.copy(task);
    }
}
