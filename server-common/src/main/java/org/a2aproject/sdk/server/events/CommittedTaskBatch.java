package org.a2aproject.sdk.server.events;

import java.util.List;

import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.util.Assert;

/**
 * One authoritative task snapshot and the original ordered events committed with it.
 *
 * <p>The snapshot describes the end of the batch. Earlier events retain their own generation
 * positions and are not expanded into fabricated intermediate snapshots.</p>
 */
public record CommittedTaskBatch(TaskSnapshot snapshot, List<StreamingEventKind> events) {

    public CommittedTaskBatch {
        Assert.checkNotNullParam("snapshot", snapshot);
        Assert.checkNotNullParam("events", events);
        events = List.copyOf(events);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("A committed task batch must contain events");
        }
        for (StreamingEventKind event : events) {
            if (!snapshot.task().id().equals(taskIdOf(event))) {
                throw new IllegalArgumentException("Committed event task ID must match the final snapshot");
            }
        }
    }

    /** Returns the task ID shared by the batch snapshot and its task events. */
    public String taskId() {
        return snapshot.task().id();
    }

    /**
     * Returns each task-event generation in event order. Direct {@code Message} responses do not
     * belong in committed task batches and therefore have no position here.
     */
    public List<Long> generationPositions() {
        return events.stream().map(CommittedTaskBatch::generationOf).toList();
    }

    /** Uses the T02 mutation-finality rule without changing the shared TaskState enum. */
    public boolean isFinal() {
        TaskState state = snapshot.task().status().state();
        return state == TaskState.TASK_STATE_UNSPECIFIED || state.isFinal();
    }

    private static long generationOf(StreamingEventKind event) {
        if (event instanceof Task task) {
            return task.generation();
        }
        if (event instanceof TaskMessageUpdateEvent update) {
            return update.generation();
        }
        if (event instanceof TaskStatusUpdateEvent update) {
            return update.generation();
        }
        if (event instanceof TaskArtifactUpdateEvent update) {
            return update.generation();
        }
        throw new IllegalArgumentException("Committed task events must carry a task generation: "
                + event.getClass().getName());
    }

    private static String taskIdOf(StreamingEventKind event) {
        if (event instanceof Task task) {
            return task.id();
        }
        if (event instanceof TaskMessageUpdateEvent update) {
            return update.taskId();
        }
        if (event instanceof TaskStatusUpdateEvent update) {
            return update.taskId();
        }
        if (event instanceof TaskArtifactUpdateEvent update) {
            return update.taskId();
        }
        throw new IllegalArgumentException("Committed task events must identify a task: "
                + event.getClass().getName());
    }
}
