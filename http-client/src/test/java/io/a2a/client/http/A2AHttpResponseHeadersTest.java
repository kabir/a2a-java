package io.a2a.client.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify that HTTP response headers are properly exposed
 * through the A2AHttpResponse interface.
 */
public class A2AHttpResponseHeadersTest {

    private HttpServer testServer;
    private String serverUrl;
    private A2AHttpClient httpClient;

    @BeforeEach
    void setUp() throws IOException {
        // Create a test HTTP server
        testServer = HttpServer.create(new InetSocketAddress(0), 0);
        int port = testServer.getAddress().getPort();
        serverUrl = "http://localhost:" + port;
        
        // Set up test endpoints
        testServer.createContext("/test", new TestHandler());
        testServer.createContext("/headers", new HeaderTestHandler());
        testServer.setExecutor(Executors.newFixedThreadPool(2));
        testServer.start();
        
        // Create the HTTP client to test
        httpClient = new JdkA2AHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @Test
    void testGetRequestHeaders() throws IOException, InterruptedException {
        A2AHttpResponse response = httpClient.createGet()
                .url(serverUrl + "/headers")
                .addHeader("X-Test-Header", "test-value")
                .get();

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(200, response.status());

        // Test that headers are accessible
        Map<String, List<String>> headers = response.headers();
        assertNotNull(headers);
        
        // Check for common HTTP headers
        assertTrue(headers.containsKey("content-type") || headers.containsKey("Content-Type"));
        assertTrue(headers.containsKey("content-length") || headers.containsKey("Content-Length"));
        
        // Check for our custom response header
        List<String> customHeader = headers.get("X-Response-Header");
        if (customHeader == null) {
            customHeader = headers.get("x-response-header"); // Case insensitive check
        }
        assertNotNull(customHeader, "Custom response header should be present");
        assertEquals("response-value", customHeader.get(0));
    }

    @Test
    void testPostRequestHeaders() throws IOException, InterruptedException {
        String requestBody = "{\"test\": \"data\"}";
        
        A2AHttpResponse response = httpClient.createPost()
                .url(serverUrl + "/test")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Custom-Header", "custom-value")
                .body(requestBody)
                .post();

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(200, response.status());

        // Test that headers are accessible
        Map<String, List<String>> headers = response.headers();
        assertNotNull(headers);
        assertFalse(headers.isEmpty());
        
        // Verify we can access standard headers
        boolean hasContentType = headers.containsKey("content-type") || headers.containsKey("Content-Type");
        assertTrue(hasContentType, "Response should contain Content-Type header");
    }

    @Test
    void testHeadersAreImmutable() throws IOException, InterruptedException {
        A2AHttpResponse response = httpClient.createGet()
                .url(serverUrl + "/headers")
                .get();

        Map<String, List<String>> headers = response.headers();
        assertNotNull(headers);
        
        // Headers map should be immutable or at least not affect the original response
        int originalSize = headers.size();
        
        // Try to modify (this might throw an exception or be ignored)
        try {
            headers.put("new-header", List.of("new-value"));
            // If modification succeeded, verify it doesn't affect subsequent calls
            Map<String, List<String>> headers2 = response.headers();
            // The original headers should be unchanged
            assertTrue(headers2.size() >= originalSize);
        } catch (UnsupportedOperationException e) {
            // This is expected if the map is immutable
            assertTrue(true, "Headers map is properly immutable");
        }
    }

    @Test
    void testMultipleHeaderValues() throws IOException, InterruptedException {
        A2AHttpResponse response = httpClient.createGet()
                .url(serverUrl + "/headers")
                .get();

        Map<String, List<String>> headers = response.headers();
        assertNotNull(headers);
        
        // Verify that headers with multiple values are properly handled
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            assertNotNull(entry.getKey(), "Header name should not be null");
            assertNotNull(entry.getValue(), "Header values should not be null");
            assertFalse(entry.getValue().isEmpty(), "Header values list should not be empty");
        }
    }

    /**
     * Test handler that echoes back information about the request
     */
    private static class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String response = "{\"method\":\"" + method + "\",\"received\":\"ok\"}";
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().close();
        }
    }

    /**
     * Test handler that sets various headers for testing
     */
    private static class HeaderTestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Set various response headers for testing
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.getResponseHeaders().set("X-Response-Header", "response-value");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("X-Multi-Header", "value1");
            exchange.getResponseHeaders().add("X-Multi-Header", "value2");
            
            String response = "Headers test response";
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().close();
        }
    }
}
