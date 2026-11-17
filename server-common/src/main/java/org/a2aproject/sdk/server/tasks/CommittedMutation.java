package org.a2aproject.sdk.server.tasks;

import java.util.List;

import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.util.Assert;

/**
 * The final task snapshot and original ordered events from one committed batch.
 *
 * <p>Events in a multi-generation batch retain their individual generations. The snapshot is the
 * one final state for the whole batch; it is not an intermediate view for each event.</p>
 */
public record CommittedMutation(TaskSnapshot snapshot, List<StreamingEventKind> events, boolean changed) {

    public CommittedMutation {
        Assert.checkNotNullParam("snapshot", snapshot);
        Assert.checkNotNullParam("events", events);
        events = List.copyOf(events);
        if (!changed && !events.isEmpty()) {
            throw new IllegalArgumentException("An unchanged mutation cannot contain events");
        }
    }
}
