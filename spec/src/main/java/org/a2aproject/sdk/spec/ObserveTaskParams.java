package org.a2aproject.sdk.spec;

import org.a2aproject.sdk.spec.util.Utils;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/** Task subscription parameters with an optional last-seen generation cursor. */
public record ObserveTaskParams(String id, @Nullable Long lastSeenGeneration, @Nullable String tenant) {

    public ObserveTaskParams {
        Assert.checkNotNullParam("id", id);
        Utils.validateTenant(tenant);
    }
}
