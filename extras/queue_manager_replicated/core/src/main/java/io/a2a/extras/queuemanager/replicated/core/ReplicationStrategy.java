package io.a2a.extras.queuemanager.replicated.core;

import io.a2a.spec.Event;

public interface ReplicationStrategy {
    void send(String taskId, Event event);
}