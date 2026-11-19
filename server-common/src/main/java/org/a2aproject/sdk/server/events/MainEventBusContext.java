package org.a2aproject.sdk.server.events;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

record MainEventBusContext(String taskId, EventQueue.MainQueue eventQueue, EventQueueItem eventQueueItem,
        EventQueue.@Nullable ChildQueue origin) {
    MainEventBusContext {
        Objects.requireNonNull(taskId, "taskId cannot be null");
        Objects.requireNonNull(eventQueue, "eventQueue cannot be null");
        Objects.requireNonNull(eventQueueItem, "eventQueueItem cannot be null");
    }

    MainEventBusContext(String taskId, EventQueue.MainQueue eventQueue, EventQueueItem eventQueueItem) {
        this(taskId, eventQueue, eventQueueItem, null);
    }
}
