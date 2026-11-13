package org.a2aproject.sdk.client.http;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * HTTP client interface for making HTTP requests to A2A agents.
 *
 * <p>Provides a fluent builder API for constructing and executing HTTP requests
 * with support for GET, POST, and DELETE methods. Includes support for both
 * synchronous requests and asynchronous Server-Sent Events (SSE) streaming.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * A2AHttpClient client = A2AHttpClientFactory.create();
 *
 * // Synchronous GET request
 * A2AHttpResponse response = client.createGet()
 *     .url("http://localhost:9999/api/endpoint")
 *     .addHeader("Authorization", "Bearer token")
 *     .get();
 *
 * // Synchronous POST request
 * A2AHttpResponse response = client.createPost()
 *     .url("http://localhost:9999/message:send")
 *     .body("{\"message\": \"Hello\"}")
 *     .post();
 *
 * // Asynchronous SSE streaming
 * CompletableFuture<Void> future = client.createPost()
 *     .url("http://localhost:9999/message:stream")
 *     .body(jsonBody)
 *     .postAsyncSSE(
 *         event -> System.out.println("Event: " + event.data()),
 *         error -> System.err.println("Error: " + error),
 *         () -> System.out.println("Stream complete")
 *     );
 * }</pre>
 *
 * @see A2AHttpClientFactory
 * @see A2AHttpResponse
 */
public interface A2AHttpClient {

    /** HTTP Content-Type header name. */
    String CONTENT_TYPE = "Content-Type";
    /** JSON content type value. */
    String APPLICATION_JSON = "application/json";
    /** HTTP Accept header name. */
    String ACCEPT = "Accept";
    /** SSE event stream content type. */
    String EVENT_STREAM = "text/event-stream";

    /**
     * Creates a builder for GET requests.
     *
     * @return a new GetBuilder instance
     */
    GetBuilder createGet();

    /**
     * Creates a builder for POST requests.
     *
     * @return a new PostBuilder instance
     */
    PostBuilder createPost();

    /**
     * Creates a builder for DELETE requests.
     *
     * @return a new DeleteBuilder instance
     */
    DeleteBuilder createDelete();

    /**
     * Base builder interface for HTTP requests.
     *
     * @param <T> the concrete builder type for method chaining
     */
    interface Builder<T extends Builder<T>> {
        /**
         * Sets the target URL for the request.
         *
         * @param s the URL string
         * @return this builder for chaining
         */
        T url(String s);

        /**
         * Adds multiple HTTP headers to the request.
         *
         * @param headers map of header names to values
         * @return this builder for chaining
         */
        T addHeaders(Map<String, String> headers);

        /**
         * Adds a single HTTP header to the request.
         *
         * @param name the header name
         * @param value the header value
         * @return this builder for chaining
         */
        T addHeader(String name, String value);
    }

    /**
     * Builder for HTTP GET requests.
     *
     * <p>Supports both synchronous requests and asynchronous Server-Sent Events (SSE) streaming.
     */
    interface GetBuilder extends Builder<GetBuilder> {
        /**
         * Controls whether the HTTP client follows at most one redirect for this synchronous
         * GET request. When set to {@code true}, the SDK issues a follow-up GET to the
         * {@code Location} URL with headers filtered by {@link RedirectHeaderPolicy} to
         * prevent credential leakage. {@code getAsyncSSE} is never redirected regardless of
         * this flag.
         *
         * <p><strong>Note for implementors:</strong> The default implementation is a no-op.
         * Third-party {@link A2AHttpClient} implementations MUST override this method to
         * honour the setting.
         *
         * @param follow {@code true} to follow one redirect hop, {@code false} to disable (default)
         * @return this builder for chaining
         */
        default GetBuilder followRedirects(boolean follow) {
            return this;
        }

        /**
         * Executes a synchronous GET request.
         *
         * @return the HTTP response
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the operation is interrupted
         */
        A2AHttpResponse get() throws IOException, InterruptedException;

        /** Starts an asynchronous GET and returns its complete status-aware response. */
        default AsyncGetCall getAsyncResponse() throws IOException, InterruptedException {
            throw new UnsupportedOperationException("Status-aware asynchronous GET is not supported");
        }

        /**
         * Executes an asynchronous GET request expecting Server-Sent Events (SSE).
         *
         * <p>The request will stream SSE messages asynchronously, invoking the provided
         * consumers for each event, error, or completion.
         *
         * @param messageConsumer callback for each SSE message received
         * @param errorConsumer callback for errors during streaming
         * @param completeRunnable callback when the stream completes normally
         * @return a CompletableFuture that completes when streaming ends
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the operation is interrupted
         */
        CompletableFuture<Void> getAsyncSSE(
                Consumer<ServerSentEvent> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) throws IOException, InterruptedException;

        /** Starts a cancellable GET SSE request and rejects non-2xx responses before SSE decoding. */
        default AsyncSSECall getAsyncSSECallStatusAware(
                Consumer<ServerSentEvent> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) throws IOException, InterruptedException {
            throw new UnsupportedOperationException("Status-aware asynchronous GET SSE is not supported");
        }
    }

    /**
     * Builder for HTTP POST requests.
     *
     * <p>Supports both synchronous requests and asynchronous Server-Sent Events (SSE) streaming.
     */
    interface PostBuilder extends Builder<PostBuilder> {
        /**
         * Sets the request body content.
         *
         * @param body the request body string (typically JSON)
         * @return this builder for chaining
         */
        PostBuilder body(String body);

        /**
         * Controls whether the HTTP client follows redirects for this synchronous POST request.
         * When set to {@code false}, redirect responses (3xx) are returned as-is
         * instead of being followed automatically.
         *
         * <p>Security-sensitive code (e.g. push notification delivery) should disable
         * redirects to prevent SSRF bypass via open redirectors.
         *
         * <p><strong>Note for implementors:</strong> The default implementation is a no-op
         * that silently ignores the {@code follow} argument. Third-party {@link A2AHttpClient}
         * implementations MUST override this method to honour the setting; otherwise
         * {@code followRedirects(false)} calls have no effect and redirects will still
         * be followed, bypassing SSRF protection.
         *
         * @param follow {@code true} to follow redirects, {@code false} to disable (default)
         * @return this builder for chaining
         */
        default PostBuilder followRedirects(boolean follow) {
            return this;
        }

        /**
         * Executes a synchronous POST request.
         *
         * @return the HTTP response
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the operation is interrupted
         */
        A2AHttpResponse post() throws IOException, InterruptedException;

        /**
         * Executes an asynchronous POST request expecting Server-Sent Events (SSE).
         * Redirect following is not supported for streaming requests; redirect responses are
         * delivered to the configured callbacks as ordinary non-SSE responses.
         *
         * <p>The request will stream SSE messages asynchronously, invoking the provided
         * consumers for each event, error, or completion.
         *
         * @param messageConsumer callback for each SSE message received
         * @param errorConsumer callback for errors during streaming
         * @param completeRunnable callback when the stream completes normally
         * @return a CompletableFuture that completes when streaming ends
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the operation is interrupted
         */
        CompletableFuture<Void> postAsyncSSE(
                Consumer<ServerSentEvent> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) throws IOException, InterruptedException;

        /** Starts an SSE POST with an explicit handle that must cancel the underlying read. */
        default AsyncSSECall postAsyncSSECall(
                Consumer<ServerSentEvent> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) throws IOException, InterruptedException {
            throw new UnsupportedOperationException("Cancellable asynchronous SSE is not supported");
        }

        /** Starts a cancellable SSE POST that rejects every non-2xx HTTP status before body decoding. */
        default AsyncSSECall postAsyncSSECallStatusAware(
                Consumer<ServerSentEvent> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) throws IOException, InterruptedException {
            throw new UnsupportedOperationException("Status-aware asynchronous SSE is not supported");
        }


        /**
         * Executes an asynchronous POST and returns the complete HTTP response. The returned
         * handle exposes an explicit cancellation operation that must detach the underlying
         * request, rather than only cancelling a dependent completion stage.
         *
         * <p>Implementations that cannot provide status-aware cancellation may keep source
         * compatibility by inheriting this unsupported default. Existing SSE methods retain
         * their callback and completion behavior.
         *
         * @return a handle for the response and underlying request cancellation
         */
        default AsyncPostCall postAsyncResponse() throws IOException, InterruptedException {
            throw new UnsupportedOperationException("Status-aware asynchronous POST is not supported");
        }
    }

    /** A status-aware asynchronous GET response with explicit underlying-request cancellation. */
    interface AsyncGetCall {
        /** Returns the response result, including non-2xx status and body. */
        CompletableFuture<A2AHttpResponse> response();

        /** Detaches the underlying HTTP request and cancels the response result. */
        void cancel();
    }

    /** A status-aware asynchronous POST response with explicit underlying-request cancellation. */
    interface AsyncPostCall {
        /** Returns the response result. */
        CompletableFuture<A2AHttpResponse> response();

        /** Detaches the underlying HTTP request and cancels the response result. */
        void cancel();
    }

    /** A streaming SSE POST and cancellation of its underlying HTTP request. */
    interface AsyncSSECall {
        /** Completes when streaming ends. */
        CompletableFuture<Void> completion();

        /** Detaches the underlying streaming HTTP request. */
        void cancel();
    }

    /**
     * Builder for HTTP DELETE requests.
     */
    interface DeleteBuilder extends Builder<DeleteBuilder> {
        /**
         * Executes a synchronous DELETE request.
         *
         * @return the HTTP response
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the operation is interrupted
         */
        A2AHttpResponse delete() throws IOException, InterruptedException;
    }
}
