package org.a2aproject.sdk.client;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * A cancellable, single-subscriber observation of one ordinary interaction with an explicit {@link TaskView}.
 * <p>
 * Signals to the subscriber are serialized and bounded by Flow demand. {@link #completion()} resolves normally
 * exactly once with an {@link ObservationEnd}, even without a subscriber. {@link #currentView()} is an immutable
 * local snapshot, never a network read; it survives completion but does not stay current. Cancelling or closing
 * only detaches this observer; it never cancels the remote task.
 */
public interface TaskObservation extends AutoCloseable {

    /** The one-subscriber stream of updates; a second subscriber is rejected locally. */
    Flow.Publisher<ObservationUpdate> updates();

    /** Completes when the transport request is established, or exceptionally if it fails or ends first. */
    CompletionStage<Void> ready();

    /** Completes exactly once with the terminal outcome. */
    CompletionStage<ObservationEnd> completion();

    /** The latest immutable local view. */
    TaskView currentView();

    /** Detaches this observer once; the outcome is {@link ObservationEnd.Kind#CANCELLED} if nothing ended first. */
    void cancel();

    /** Idempotent local cancellation. */
    @Override
    void close();
}
