package io.a2a.extras.queuemanager.replicated.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.a2a.spec.Event;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.StreamingEventKind;
import io.a2a.util.Utils;

public class ReplicatedEvent {
    private String taskId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private StreamingEventKind event;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JSONRPCError error;

    // Default constructor for JSON deserialization
    public ReplicatedEvent() {
    }

    // Constructor for creating from A2A StreamingEventKind objects
    public ReplicatedEvent(String taskId, StreamingEventKind event) {
        this.taskId = taskId;
        this.event = event;
        this.error = null;
    }

    // Constructor for creating from A2A JSONRPCError objects
    public ReplicatedEvent(String taskId, JSONRPCError error) {
        this.taskId = taskId;
        this.event = null;
        this.error = error;
    }

    // Backward compatibility constructor for generic Event objects
    public ReplicatedEvent(String taskId, Event event) {
        this.taskId = taskId;
        if (event instanceof StreamingEventKind streamingEvent) {
            this.event = streamingEvent;
            this.error = null;
        } else if (event instanceof JSONRPCError jsonRpcError) {
            this.event = null;
            this.error = jsonRpcError;
        } else {
            throw new IllegalArgumentException("Event must be either StreamingEventKind or JSONRPCError, got: " + event.getClass());
        }
    }


    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public StreamingEventKind getEvent() {
        return event;
    }

    public void setEvent(StreamingEventKind event) {
        this.event = event;
        this.error = null; // Clear error when setting event
    }

    public JSONRPCError getError() {
        return error;
    }

    public void setError(JSONRPCError error) {
        this.error = error;
        this.event = null; // Clear event when setting error
    }

    /**
     * Get the contained event as the generic Event interface for backward compatibility.
     * @return the event (either StreamingEventKind or JSONRPCError) or null if neither is set
     */
    @JsonIgnore
    public Event getEventAsGeneric() {
        if (event != null) {
            return event;
        }
        return error;
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
     * @return true if it contains a JSONRPCError
     */
    public boolean hasError() {
        return error != null;
    }

    @Override
    public String toString() {
        return "ReplicatedEvent{" +
                "taskId='" + taskId + '\'' +
                ", event=" + event +
                ", error=" + error +
                '}';
    }
}