package org.a2aproject.sdk.grpc.utils;

import java.util.List;

/** Evidence retained when a versioned decoder skips future content. */
public record DecodeNotes(List<Long> skippedTimelineGenerations, boolean unknownSkipped) {

    public static final DecodeNotes EMPTY = new DecodeNotes(List.of(), false);

    public DecodeNotes {
        skippedTimelineGenerations = List.copyOf(skippedTimelineGenerations);
    }
}
