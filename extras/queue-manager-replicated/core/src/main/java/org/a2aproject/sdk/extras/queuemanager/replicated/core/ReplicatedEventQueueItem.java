package org.a2aproject.sdk.extras.queuemanager.replicated.core;

import java.util.List;

import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.server.events.EventQueueItem;
import org.a2aproject.sdk.server.tasks.TaskRevision;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.jspecify.annotations.Nullable;

public class ReplicatedEventQueueItem implements EventQueueItem {
    private String taskId;

    private StreamingEventKind event;

    private A2AError error;

    private boolean closedEvent;

    /** Writer epoch of the sending node, or null for an unversioned (legacy) record. */
    private @Nullable Long writerEpoch;

    /** Random identity of the sending manager, so a node can drop its own broadcast. */
    private @Nullable String originId;

    /** Committed batch payload; all three are set together, or none (not a committed envelope). */
    private @Nullable List<StreamingEventKind> batchEvents;

    private @Nullable Task batchTask;

    private @Nullable Long batchRevision;

    /** For a stamped poison pill: generation of the terminal batch it must follow, or null for no ordering. */
    private @Nullable Long closedAfterGeneration;

    private transient @Nullable CommittedTaskBatch cachedBatch;

    // Default constructor for JSON deserialization
    public ReplicatedEventQueueItem() {
    }

    // Constructor for creating from A2A StreamingEventKind objects
    public ReplicatedEventQueueItem(String taskId, StreamingEventKind event) {
        this.taskId = taskId;
        this.event = event;
        this.error = null;
    }

    // Constructor for creating from A2A A2AError objects
    public ReplicatedEventQueueItem(String taskId, A2AError error) {
        this.taskId = taskId;
        this.event = null;
        this.error = error;
    }

    // Backward compatibility constructor for generic Event objects
    public ReplicatedEventQueueItem(String taskId, Event event) {
        this.taskId = taskId;
        if (event instanceof org.a2aproject.sdk.server.events.QueueClosedEvent) {
            this.event = null;
            this.error = null;
            this.closedEvent = true;
        } else if (event instanceof StreamingEventKind streamingEvent) {
            this.event = streamingEvent;
            this.error = null;
            this.closedEvent = false;
        } else if (event instanceof A2AError jsonRpcError) {
            this.event = null;
            this.error = jsonRpcError;
            this.closedEvent = false;
        } else {
            throw new IllegalArgumentException("Event must be StreamingEventKind, A2AError, or QueueClosedEvent, got: " + event.getClass());
        }
    }


    /**
     * Creates the internal committed envelope for one batch. It carries the original ordered events and the
     * final snapshot with its store revision; it is not a proposal and a receiver never persists it.
     *
     * @param batch the committed batch
     * @param writerEpoch the sender's migrated writer epoch
     * @param originId identity of the sending manager
     * @return the envelope
     */
    public static ReplicatedEventQueueItem committed(CommittedTaskBatch batch, long writerEpoch, String originId) {
        ReplicatedEventQueueItem item = new ReplicatedEventQueueItem();
        item.taskId = batch.taskId();
        item.writerEpoch = writerEpoch;
        item.originId = originId;
        item.batchEvents = batch.events();
        item.batchTask = batch.snapshot().task();
        item.batchRevision = batch.snapshot().revision().value();
        item.cachedBatch = batch;
        return item;
    }

    /**
     * Creates a stamped poison pill that receivers deliver only after the terminal batch ending at
     * {@code afterGeneration} (or immediately when it is null).
     */
    public static ReplicatedEventQueueItem stampedClosed(String taskId, long writerEpoch, String originId,
            @Nullable Long afterGeneration) {
        ReplicatedEventQueueItem item = new ReplicatedEventQueueItem();
        item.taskId = taskId;
        item.closedEvent = true;
        item.writerEpoch = writerEpoch;
        item.originId = originId;
        item.closedAfterGeneration = afterGeneration;
        return item;
    }

    public @Nullable Long getClosedAfterGeneration() {
        return closedAfterGeneration;
    }

    public @Nullable Long getWriterEpoch() {
        return writerEpoch;
    }

    public @Nullable String getOriginId() {
        return originId;
    }

    /** True when this record carries a committed batch rather than a single legacy event. */
    public boolean isCommittedBatch() {
        return batchEvents != null || batchTask != null || batchRevision != null;
    }

    /**
     * Rebuilds and validates the committed batch.
     *
     * @throws IllegalArgumentException if the envelope is incomplete or not a canonical consecutive range
     */
    @Override
    public @Nullable CommittedTaskBatch committedBatch() {
        if (!isCommittedBatch()) {
            return null;
        }
        CommittedTaskBatch cached = cachedBatch;
        if (cached != null) {
            return cached;
        }
        if (batchEvents == null || batchTask == null || batchRevision == null || taskId == null
                || event != null || error != null || closedEvent) {
            throw new IllegalArgumentException("Incomplete committed replication envelope");
        }
        CommittedTaskBatch batch = new CommittedTaskBatch(
                new TaskSnapshot(batchTask, new TaskRevision(batchRevision)), batchEvents);
        if (!taskId.equals(batch.taskId())) {
            throw new IllegalArgumentException("Envelope task ID does not match its batch");
        }
        List<Long> positions = batch.generationPositions();
        for (int i = 0; i < positions.size(); i++) {
            long position = positions.get(i);
            if (position < 1 || (i > 0 && position != positions.get(i - 1) + 1)) {
                throw new IllegalArgumentException("Committed batch generations are not a consecutive range");
            }
        }
        if (positions.get(positions.size() - 1) != batch.snapshot().task().generation()) {
            throw new IllegalArgumentException("Committed batch does not end at its snapshot generation");
        }
        cachedBatch = batch;
        return batch;
    }

    @Override
    public boolean isCanonicalFinal() {
        CommittedTaskBatch batch = committedBatch();
        return batch != null && batch.isFinal();
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * Get the StreamingEventKind event field (for JSON serialization).
     * @return the StreamingEventKind event or null
     */
    public StreamingEventKind getStreamingEvent() {
        return event;
    }

    public void setEvent(StreamingEventKind event) {
        this.event = event;
        this.error = null; // Clear error when setting event
    }

    /**
     * Get the A2AError field (for JSON serialization).
     * @return the A2AError or null
     */
    public A2AError getErrorObject() {
        return error;
    }

    public void setError(A2AError error) {
        this.error = error;
        this.event = null; // Clear event when setting error
    }

    /**
     * Get the contained event as the generic Event interface (implements EventQueueItem).
     * This is the method required by the EventQueueItem interface.
     * @return the event (StreamingEventKind, A2AError, or QueueClosedEvent) or null if none is set
     */
    @Override
    public Event getEvent() {
        if (closedEvent) {
            return new org.a2aproject.sdk.server.events.QueueClosedEvent(taskId);
        }
        if (event != null) {
            return event;
        }
        List<StreamingEventKind> events = batchEvents;
        if (events != null && !events.isEmpty()) {
            return events.get(events.size() - 1);
        }
        return error;
    }

    /**
     * Indicates this is a replicated event (implements EventQueueItem).
     * @return always true for replicated events
     */
    @Override
    public boolean isReplicated() {
        return true;
    }

    /**
     * Check if this ReplicatedEvent contains an event (vs an error).
     * @return true if it contains a StreamingEventKind event
     */
    public boolean hasEvent() {
        return event != null;
    }

    /**
     * Check if this ReplicatedEvent contains an error.
     * @return true if it contains a A2AError
     */
    public boolean hasError() {
        return error != null;
    }

    /**
     * Check if this is a QueueClosedEvent (poison pill).
     * For JSON serialization.
     * @return true if this is a queue closed event
     */
    public boolean isClosedEvent() {
        return closedEvent;
    }

    /**
     * Set the closed event flag (for JSON deserialization).
     * @param closedEvent true if this is a queue closed event
     */
    public void setClosedEvent(boolean closedEvent) {
        this.closedEvent = closedEvent;
        if (closedEvent) {
            this.event = null;
            this.error = null;
        }
    }

    /**
     * Check if this event is a Task event.
     * Task events should always be processed even for inactive tasks,
     * as they carry the final task state.
     * @return true if this is a Task event
     */
    public boolean isTaskEvent() {
        return event instanceof org.a2aproject.sdk.spec.Task;
    }

    @Override
    public String toString() {
        return "ReplicatedEventQueueItem{" +
                "taskId='" + taskId + '\'' +
                ", event=" + event +
                ", error=" + error +
                ", closedEvent=" + closedEvent +
                ", writerEpoch=" + writerEpoch +
                ", committedBatch=" + isCommittedBatch() +
                '}';
    }
}