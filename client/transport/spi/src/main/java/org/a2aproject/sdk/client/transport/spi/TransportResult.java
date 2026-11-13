package org.a2aproject.sdk.client.transport.spi;

import java.util.List;

import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.util.Assert;

/** A decoded value and ordered per-task decode evidence. */
public record TransportResult<T>(T value, List<TaskDecodeEvidence> taskEvidence) {

    public TransportResult {
        Assert.checkNotNullParam("value", value);
        taskEvidence = List.copyOf(Assert.checkNotNullParam("taskEvidence", taskEvidence));
        if (value instanceof Task task) {
            if (taskEvidence.size() != 1) {
                throw new IllegalArgumentException("A task result requires exactly one decode evidence item");
            }
            requireEvidence(task, taskEvidence.get(0));
        } else if (value instanceof ListTasksResult result) {
            if (result.tasks().size() != taskEvidence.size()) {
                throw new IllegalArgumentException("A list result requires one decode evidence item per task");
            }
            for (int index = 0; index < result.tasks().size(); index++) {
                requireEvidence(result.tasks().get(index), taskEvidence.get(index));
            }
        } else if (value instanceof Message && !taskEvidence.isEmpty()) {
            throw new IllegalArgumentException("A direct Message result must not have task decode evidence");
        }
    }

    private static void requireEvidence(Task task, TaskDecodeEvidence evidence) {
        if (!task.id().equals(evidence.taskId())) {
            throw new IllegalArgumentException("Task decode evidence must match the returned task in order");
        }
    }
}
