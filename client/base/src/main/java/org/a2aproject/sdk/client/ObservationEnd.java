package org.a2aproject.sdk.client;

import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * The single terminal outcome of a {@link TaskObservation}.
 * <p>
 * The final view is the last local as-of view; it is not a claim about the server's current state. No retry or
 * resend is implied by any outcome.
 *
 * @param kind how the observation ended
 * @param cause the failure or deadline cause, otherwise {@code null}
 * @param finalView the last local view when the observation ended
 */
public record ObservationEnd(Kind kind, @Nullable Throwable cause, TaskView finalView) {

    /** Terminal outcomes. */
    public enum Kind {
        /** The source ended cleanly. */
        COMPLETED,
        /** The local observer cancelled or closed; no task cancellation is requested. */
        CANCELLED,
        /** A finite local deadline expired. */
        DEADLINE,
        /** Opening, decoding, dispatch or the transport failed. */
        FAILED
    }

    public ObservationEnd {
        Assert.checkNotNullParam("kind", kind);
        Assert.checkNotNullParam("finalView", finalView);
    }
}
