package org.a2aproject.sdk.client;

import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * One observation step: the known raw event, if any, and the immutable view after reducing it.
 * <p>
 * A {@code null} event is a view-only update, for example an unknown stream payload that was skipped. It
 * communicates invalidation without inventing a protocol event.
 *
 * @param event the known event that was reduced, or {@code null} for a view-only update
 * @param view the immutable view after this step
 */
public record ObservationUpdate(@Nullable StreamingEventKind event, TaskView view) {

    public ObservationUpdate {
        Assert.checkNotNullParam("view", view);
    }
}
