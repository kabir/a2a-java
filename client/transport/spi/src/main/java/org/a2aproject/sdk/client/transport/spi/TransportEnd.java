package org.a2aproject.sdk.client.transport.spi;

import org.jspecify.annotations.Nullable;

/** The single terminal outcome of an observation. */
public record TransportEnd(Kind kind, @Nullable Throwable cause) {
    public enum Kind {
        COMPLETED,
        CANCELLED,
        DEADLINE,
        FAILED
    }

    public TransportEnd {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
    }

    public static TransportEnd completed() {
        return new TransportEnd(Kind.COMPLETED, null);
    }

    public static TransportEnd cancelled() {
        return new TransportEnd(Kind.CANCELLED, null);
    }

    public static TransportEnd deadline(Throwable cause) {
        return new TransportEnd(Kind.DEADLINE, cause);
    }

    public static TransportEnd failed(Throwable cause) {
        return new TransportEnd(Kind.FAILED, cause);
    }
}
