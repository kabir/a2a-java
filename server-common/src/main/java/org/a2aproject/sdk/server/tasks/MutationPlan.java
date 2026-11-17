package org.a2aproject.sdk.server.tasks;

import java.util.List;

import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.util.Assert;

/** A side-effect-free proposal for one task's final state and ordered emitted events. */
public record MutationPlan(Task task, List<StreamingEventKind> events, boolean changed) {

    public MutationPlan {
        Assert.checkNotNullParam("task", task);
        Assert.checkNotNullParam("events", events);
        events = List.copyOf(events);
        if (!changed && !events.isEmpty()) {
            throw new IllegalArgumentException("An unchanged plan cannot contain events");
        }
    }
}
