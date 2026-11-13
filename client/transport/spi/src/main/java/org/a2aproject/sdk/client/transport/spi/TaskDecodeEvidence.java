package org.a2aproject.sdk.client.transport.spi;

import java.util.List;

import org.a2aproject.sdk.util.Assert;

/** Immutable evidence about timeline entries skipped while decoding one task. */
public record TaskDecodeEvidence(String taskId, List<Long> skippedTimelineGenerations,
        boolean unknownSkipped) {

    public TaskDecodeEvidence {
        Assert.checkNotNullParam("taskId", taskId);
        skippedTimelineGenerations = List.copyOf(
                Assert.checkNotNullParam("skippedTimelineGenerations", skippedTimelineGenerations));
    }
}
