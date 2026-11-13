package org.a2aproject.sdk.grpc.utils;

import org.a2aproject.sdk.spec.EventKind;
import org.jspecify.annotations.Nullable;

/** A unary send result; task evidence is null for a direct Message result. */
public record DecodedEvent(EventKind value, @Nullable DecodedTask task) {
}
