package io.a2a.server.events;

import io.a2a.spec.Event;

public interface EventEnqueueHook {
    void onEnqueue(Event event);
}