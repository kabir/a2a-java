package org.a2aproject.sdk.client;

/**
 * Synchronization of a {@link TaskView} with authoritative task state.
 * <p>
 * {@link #SYNCHRONIZED} is an as-of guarantee for the fields represented by the selected-version projection
 * through {@link TaskView#throughGeneration()}. It does not prove the server's current head, complete history
 * or future protocol understanding.
 */
public enum ViewSync {
    /** No authoritative snapshot has been observed; any assembled task is explicitly partial. */
    NO_BASELINE,
    /** The represented fields are reconciled through the view's generation. */
    SYNCHRONIZED,
    /** A baseline exists but a gap, uninterpretable payload or invalid generation signal was observed. */
    INCOMPLETE
}
