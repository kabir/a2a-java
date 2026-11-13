package org.a2aproject.sdk.client.transport.spi;

import java.util.List;

import org.a2aproject.sdk.spec.StreamError;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.jspecify.annotations.Nullable;

/** One known event, skipped-variant note, or local stream rejection. */
public record TransportFrame(@Nullable StreamingEventKind event, boolean unknownSkipped,
        List<Long> skippedTimelineGenerations, @Nullable StreamError streamError, long encodedSizeBytes) {

    public TransportFrame {
        if (skippedTimelineGenerations == null) {
            throw new IllegalArgumentException("skippedTimelineGenerations must not be null");
        }
        skippedTimelineGenerations = List.copyOf(skippedTimelineGenerations);
        if (encodedSizeBytes < 0) {
            throw new IllegalArgumentException("encodedSizeBytes must be non-negative");
        }
        if (streamError != null) {
            if (event != null || unknownSkipped || !skippedTimelineGenerations.isEmpty()) {
                throw new IllegalArgumentException("streamError cannot accompany an event or skipped-variant note");
            }
        } else if (event == null && !unknownSkipped) {
            throw new IllegalArgumentException("frame must contain an event, skipped-variant note, or stream error");
        }
    }
}
