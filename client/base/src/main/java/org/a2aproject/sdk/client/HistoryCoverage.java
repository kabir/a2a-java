package org.a2aproject.sdk.client;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * Client-side view metadata describing what a history or timeline window covers.
 * <p>
 * Coverage is not a protocol assertion. There is deliberately no flag claiming full history: a returned window
 * never proves lifetime coverage, and an unknown returned range stays {@code null}.
 *
 * @param requestedLimit the requested limit (zero is a real limit), or {@code null} when the default/unknown
 *                       window applied
 * @param returnedRange the known generation range of returned entries, or {@code null} when unknown
 * @param flags the limitations that apply
 */
public record HistoryCoverage(@Nullable Integer requestedLimit, @Nullable Range returnedRange, Set<Flag> flags) {

    /** Limitations of a window. */
    public enum Flag {
        /** Only a returned window is known; older or unreturned entries may exist. */
        WINDOW_ONLY,
        /** Migration provenance says pre-upgrade entries are missing. */
        LEGACY_GAPS,
        /** Entries or payloads were skipped because they were not understood. */
        UNKNOWN_SKIPPED,
        /** Nothing is asserted, for example for an observed suffix without a baseline. */
        NOT_ASSERTED
    }

    /** An inclusive known generation range. */
    public record Range(long first, long last) {
        public Range {
            if (first > last) {
                throw new IllegalArgumentException("first must not exceed last");
            }
        }
    }

    public HistoryCoverage {
        Assert.checkNotNullParam("flags", flags);
        if (requestedLimit != null && requestedLimit < 0) {
            throw new IllegalArgumentException("requestedLimit must not be negative");
        }
        flags = flags.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(flags));
    }
}
