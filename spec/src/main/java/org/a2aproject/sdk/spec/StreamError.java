package org.a2aproject.sdk.spec;

import java.util.Map;

import org.a2aproject.sdk.spec.util.CollectionCopies;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/** Wire-level error detail kept separate from task event and failure types. */
public record StreamError(@Nullable String taskId, String code, @Nullable String message,
        @Nullable String referenceId, @Nullable Map<String, Object> metadata) {

    public StreamError {
        Assert.checkNotNullParam("code", code);
        metadata = CollectionCopies.unmodifiableNullableShallowMap(metadata);
    }
}
