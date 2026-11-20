package org.a2aproject.sdk.server.events;

import org.a2aproject.sdk.server.tasks.TaskProposal;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * Represents a locally-generated event in the queue.
 * <p>
 * Local events are those enqueued directly by the agent executor on this node,
 * as opposed to events received via replication from other nodes.
 * </p>
 */
class LocalEventQueueItem implements EventQueueItem {

    private final Event event;
    private final @Nullable TaskSnapshot committedSnapshot;
    private final boolean canonicalFinal;
    private final boolean finalForLocalConsumption;
    private final @Nullable Task resultTaskSnapshot;
    private final boolean legacyTaskSummary;

    LocalEventQueueItem(Event event) {
        this(event, null, false);
    }

    LocalEventQueueItem(Event event, @Nullable TaskSnapshot committedSnapshot, boolean canonicalFinal) {
        this(event, committedSnapshot, canonicalFinal,
                committedSnapshot == null ? null : committedSnapshot.task());
    }

    LocalEventQueueItem(Event event, @Nullable TaskSnapshot committedSnapshot, boolean canonicalFinal,
            @Nullable Task resultTaskSnapshot) {
        this(event, committedSnapshot, canonicalFinal, resultTaskSnapshot, canonicalFinal);
    }

    LocalEventQueueItem(Event event, @Nullable TaskSnapshot committedSnapshot, boolean canonicalFinal,
            @Nullable Task resultTaskSnapshot, boolean finalForLocalConsumption) {
        this(event, committedSnapshot, canonicalFinal, resultTaskSnapshot, finalForLocalConsumption, false);
    }

    LocalEventQueueItem(Event event, @Nullable TaskSnapshot committedSnapshot, boolean canonicalFinal,
            @Nullable Task resultTaskSnapshot, boolean finalForLocalConsumption, boolean legacyTaskSummary) {
        Assert.checkNotNullParam("event", event);
        this.event = event;
        this.committedSnapshot = committedSnapshot;
        this.canonicalFinal = canonicalFinal;
        this.finalForLocalConsumption = finalForLocalConsumption;
        this.resultTaskSnapshot = resultTaskSnapshot;
        this.legacyTaskSummary = legacyTaskSummary;
    }

    @Override
    public Event getEvent() {
        return event;
    }

    @Override
    public boolean isReplicated() {
        return false;
    }

    @Override
    public @Nullable TaskProposal taskProposal() {
        return null;
    }

    @Override
    public @Nullable TaskSnapshot committedSnapshot() {
        return committedSnapshot;
    }

    @Override
    public boolean isCanonicalFinal() {
        return canonicalFinal;
    }

    @Override
    public boolean isFinalForLocalConsumption() {
        return finalForLocalConsumption;
    }

    @Override
    public @Nullable Task resultTaskSnapshot() {
        return resultTaskSnapshot;
    }

    /** Whether older selected versions receive this committed Task snapshot in place of the event. */
    @Override
    public boolean legacyTaskSummary() {
        return legacyTaskSummary;
    }
}
