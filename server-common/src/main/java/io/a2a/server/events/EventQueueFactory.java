package io.a2a.server.events;

public interface EventQueueFactory {
    EventQueue.EventQueueBuilder builder();
}