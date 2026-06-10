package org.a2aproject.sdk.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utilities for creating case-insensitive, immutable snapshots of HTTP header maps.
 */
public final class HttpHeaderUtils {

    private HttpHeaderUtils() {
    }

    /**
     * Creates an unmodifiable, case-insensitive copy of the given header map.
     *
     * <p>Null keys and null value lists are silently skipped (e.g. the {@code null}
     * status-line key from {@link java.net.HttpURLConnection}).
     * Individual value lists are defensively copied via {@link List#copyOf(java.util.Collection)}.
     *
     * @param headers the source header map
     * @return an unmodifiable, case-insensitive map
     */
    public static Map<String, List<String>> copyOfCaseInsensitive(Map<String, List<String>> headers) {
        TreeMap<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(copy);
    }
}
