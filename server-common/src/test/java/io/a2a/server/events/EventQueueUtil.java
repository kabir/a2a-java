package io.a2a.server.events;

public class EventQueueUtil {
    // Since EventQueue.builder() is package protected, add a method to expose it
    public static EventQueue.EventQueueBuilder getEventQueueBuilder() {
        return EventQueue.builder();
    }
}
