package org.a2aproject.sdk.client.transport.spi;

import java.time.Duration;
import java.time.Instant;

import org.a2aproject.sdk.util.Assert;

/** Local observation limits; these values are never encoded as wire-level timeouts. */
public record SpiCallOptions(Instant openingDeadline, int maxBufferedFrames, long maxBufferedBytes) {
    public static final int DEFAULT_MAX_BUFFERED_FRAMES = 256;
    public static final long DEFAULT_MAX_BUFFERED_BYTES = 4L * 1024 * 1024;
    public static final Duration DEFAULT_OPENING_TIMEOUT = Duration.ofSeconds(30);

    public SpiCallOptions {
        Assert.checkNotNullParam("openingDeadline", openingDeadline);
        if (maxBufferedFrames < 1) {
            throw new IllegalArgumentException("maxBufferedFrames must be positive");
        }
        if (maxBufferedBytes < 1) {
            throw new IllegalArgumentException("maxBufferedBytes must be positive");
        }
    }

    /** Creates defaults with an opening deadline 30 seconds from now. */
    public static SpiCallOptions defaults() {
        return new SpiCallOptions(Instant.now().plus(DEFAULT_OPENING_TIMEOUT),
                DEFAULT_MAX_BUFFERED_FRAMES, DEFAULT_MAX_BUFFERED_BYTES);
    }
}
