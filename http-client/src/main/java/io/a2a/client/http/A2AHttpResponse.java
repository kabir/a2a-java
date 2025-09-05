package io.a2a.client.http;

import java.util.List;
import java.util.Map;

public interface A2AHttpResponse {
    int status();

    boolean success();

    String body();
    
    /**
     * Returns the HTTP response headers.
     * @return a map of header names to their values (headers can have multiple values)
     */
    Map<String, List<String>> headers();
}
