package org.a2aproject.sdk.spec;

import java.util.Map;

import org.a2aproject.sdk.spec.util.CollectionCopies;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/** An incremental message update with its authoritative task generation. */
public record TaskMessageUpdateEvent(String taskId, String contextId, Message message,
        @Nullable Map<String, Object> metadata, long generation)
        implements StreamingEventKind, UpdateEvent, EventKind {

    public static final String STREAMING_EVENT_ID = "messageUpdate";

    public TaskMessageUpdateEvent {
        Assert.checkNotNullParam("taskId", taskId);
        Assert.checkNotNullParam("contextId", contextId);
        Assert.checkNotNullParam("message", message);
        metadata = CollectionCopies.unmodifiableNullableShallowMap(metadata);
    }

    @Override
    public String kind() {
        return STREAMING_EVENT_ID;
    }
}
