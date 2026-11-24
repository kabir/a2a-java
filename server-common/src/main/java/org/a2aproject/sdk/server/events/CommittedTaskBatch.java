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

    /**
     * Stable identity of one committed batch. Webhook dispatch and replication of the same batch use it, so a
     * batch seen on the event bus and again on the finalization path is recognizably the same unit.
     *
     * @param taskId the task ID
     * @param firstGeneration generation of the first event
     * @param lastGeneration generation of the last event
     * @param revision store revision assigned to the batch's final snapshot
     */
    public record Identity(String taskId, long firstGeneration, long lastGeneration, long revision) {
    }

    /** Returns this batch's stable identity. */
    public Identity identity() {
        List<Long> positions = generationPositions();
        return new Identity(taskId(), positions.get(0), positions.get(positions.size() - 1),
                snapshot.revision().value());
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
