package io.a2a.client.transport.jsonrpc;

import io.a2a.client.transport.spi.interceptors.HeaderInspectionInterceptor;
import io.a2a.client.transport.spi.interceptors.ClientCallInterceptor;
import io.a2a.client.transport.spi.interceptors.ClientCallContext;
import io.a2a.client.transport.spi.interceptors.PayloadAndHeaders;
import io.a2a.spec.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify that the interceptor system is working correctly.
 */
public class SimpleHeaderInterceptorTest {

    @Test
    void testInterceptorCreation() {
        HeaderInspectionInterceptor interceptor = new HeaderInspectionInterceptor(false);
        assertNotNull(interceptor);
        assertNull(interceptor.getLastStreamingHeaders());
        assertNull(interceptor.getLastMethodName());
    }

    @Test
    void testInterceptorCallbacks() {
        AtomicBoolean interceptCalled = new AtomicBoolean(false);
        AtomicBoolean headersCalled = new AtomicBoolean(false);

        ClientCallInterceptor interceptor = new ClientCallInterceptor() {
            @Override
            public PayloadAndHeaders intercept(String methodName, Object payload, Map<String, String> headers,
                                              AgentCard agentCard, ClientCallContext clientCallContext) {
                interceptCalled.set(true);
                return new PayloadAndHeaders(payload, headers);
            }

            @Override
            public void onStreamingResponseHeaders(String methodName, Map<String, List<String>> responseHeaders,
                                                  AgentCard agentCard, ClientCallContext clientCallContext) {
                headersCalled.set(true);
            }
        };

        // Test intercept method
        PayloadAndHeaders result = interceptor.intercept("test", "payload", Map.of("header", "value"), null, null);
        assertTrue(interceptCalled.get());
        assertEquals("payload", result.getPayload());

        // Test streaming headers method
        interceptor.onStreamingResponseHeaders("test", Map.of("Content-Type", List.of("text/event-stream")), null, null);
        assertTrue(headersCalled.get());
    }

    @Test
    void testHeaderInspectionInterceptor() {
        HeaderInspectionInterceptor interceptor = new HeaderInspectionInterceptor(false);

        // Initially no headers
        assertNull(interceptor.getLastStreamingHeaders());
        assertNull(interceptor.getLastMethodName());

        // Simulate receiving headers
        Map<String, List<String>> testHeaders = Map.of(
            "Content-Type", List.of("text/event-stream"),
            "X-Test", List.of("test-value")
        );

        interceptor.onStreamingResponseHeaders("message/stream", testHeaders, null, null);

        // Verify headers were captured
        assertEquals(testHeaders, interceptor.getLastStreamingHeaders());
        assertEquals("message/stream", interceptor.getLastMethodName());

        // Test header access
        Map<String, List<String>> captured = interceptor.getLastStreamingHeaders();
        assertEquals(List.of("text/event-stream"), captured.get("Content-Type"));
        assertEquals(List.of("test-value"), captured.get("X-Test"));
    }
}
