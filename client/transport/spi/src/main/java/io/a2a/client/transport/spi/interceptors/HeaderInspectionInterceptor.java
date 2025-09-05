package io.a2a.client.transport.spi.interceptors;

import io.a2a.spec.AgentCard;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A simple interceptor that captures streaming response headers for inspection.
 * This is useful for debugging and monitoring HTTP responses in streaming calls.
 *
 * Example usage:
 * <pre>
 * HeaderInspectionInterceptor headerInterceptor = new HeaderInspectionInterceptor();
 *
 * JSONRPCTransportConfig config = new JSONRPCTransportConfigBuilder()
 *     .addInterceptor(headerInterceptor)
 *     .build();
 *
 * Client client = Client.builder(agentCard)
 *     .withTransport(JSONRPCTransport.class, config)
 *     .build();
 *
 * // After making a streaming call:
 * Map&lt;String, List&lt;String&gt;&gt; headers = headerInterceptor.getLastStreamingHeaders();
 * headerInterceptor.printLastStreamingHeaders();
 * </pre>
 */
public class HeaderInspectionInterceptor extends ClientCallInterceptor {

    private final AtomicReference<Map<String, List<String>>> lastStreamingHeaders = new AtomicReference<>();
    private final AtomicReference<String> lastMethodName = new AtomicReference<>();
    private final boolean logHeaders;

    /**
     * Creates a header inspection interceptor with logging enabled.
     */
    public HeaderInspectionInterceptor() {
        this(true);
    }

    /**
     * Creates a header inspection interceptor.
     * @param logHeaders whether to automatically log headers when received
     */
    public HeaderInspectionInterceptor(boolean logHeaders) {
        this.logHeaders = logHeaders;
    }

    @Override
    public PayloadAndHeaders intercept(String methodName, Object payload, Map<String, String> headers,
                                      AgentCard agentCard, ClientCallContext clientCallContext) {
        // Just pass through - we don't modify requests
        return new PayloadAndHeaders(payload, headers);
    }

    @Override
    public void onStreamingResponseHeaders(String methodName, Map<String, List<String>> responseHeaders,
                                          AgentCard agentCard, ClientCallContext clientCallContext) {
        // Capture the headers
        lastStreamingHeaders.set(responseHeaders);
        lastMethodName.set(methodName);

        if (logHeaders) {
            printHeaders(methodName, responseHeaders);
        }
    }

    /**
     * Gets the headers from the last streaming response.
     * @return the headers map, or null if no streaming response has been received yet
     */
    public Map<String, List<String>> getLastStreamingHeaders() {
        return lastStreamingHeaders.get();
    }

    /**
     * Gets the method name from the last streaming call.
     * @return the method name, or null if no streaming response has been received yet
     */
    public String getLastMethodName() {
        return lastMethodName.get();
    }

    /**
     * Prints the last streaming response headers to System.out.
     */
    public void printLastStreamingHeaders() {
        Map<String, List<String>> headers = lastStreamingHeaders.get();
        String methodName = lastMethodName.get();

        if (headers != null && methodName != null) {
            printHeaders(methodName, headers);
        } else {
            System.out.println("No streaming response headers captured yet.");
        }
    }

    private void printHeaders(String methodName, Map<String, List<String>> headers) {
        System.out.println("=== Streaming Response Headers for " + methodName + " ===");
        headers.forEach((name, values) -> {
            if (values.size() == 1) {
                System.out.println(name + ": " + values.get(0));
            } else {
                System.out.println(name + ": " + String.join(", ", values));
            }
        });
        System.out.println("=" + "=".repeat(methodName.length() + 40) + "=");
    }
}
