package org.a2aproject.sdk.client.http.vertx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.common.A2AErrorMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.verify.VerificationTimes;

public class VertxA2AHttpClientIntegrationTest {

    private ClientAndServer mockServer;
    private VertxA2AHttpClient client;

    @BeforeEach
    public void setup() {
        mockServer = ClientAndServer.startClientAndServer(0);  // Use random port
        client = new VertxA2AHttpClient();
    }

    @AfterEach
    public void teardown() {
        if (client != null) {
            client.close();
        }
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    private String getBaseUrl() {
        return "http://localhost:" + mockServer.getPort();
    }

    @Test
    public void testGetRequestSuccess() throws Exception {
        mockServer
                .when(request().withMethod("GET").withPath("/test"))
                .respond(response().withStatusCode(200).withBody("success"));

        A2AHttpResponse response = client.createGet()
                .url(getBaseUrl() + "/test")
                .get();

        assertEquals(200, response.status());
        assertTrue(response.success());
        assertEquals("success", response.body());
    }

    @Test
    public void testAsyncGetResponsePreservesNonSuccessStatusAndBody() throws Exception {
        mockServer.when(request().withMethod("GET").withPath("/async-get"))
                .respond(response().withStatusCode(500).withBody("failure body"));

        A2AHttpClient.AsyncGetCall call = client.createGet()
                .url(getBaseUrl() + "/async-get")
                .getAsyncResponse();
        A2AHttpResponse result = call.response().get(5, TimeUnit.SECONDS);

        assertEquals(500, result.status());
        assertFalse(result.success());
        assertEquals("failure body", result.body());
    }

    @Test
    public void testCancellingAsyncGetClosesHeldOpenConnection() throws Exception {
        Vertx serverVertx = Vertx.vertx();
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch connectionClosed = new CountDownLatch(1);
        HttpServer server = serverVertx.createHttpServer().requestHandler(request -> {
            request.connection().closeHandler(ignored -> connectionClosed.countDown());
            request.response().setChunked(true).write("held-open");
            requestReceived.countDown();
        });
        try {
            server.listen(0).toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            A2AHttpClient.AsyncGetCall call = client.createGet()
                    .url("http://localhost:" + server.actualPort() + "/held-open-get")
                    .getAsyncResponse();

            assertTrue(requestReceived.await(5, TimeUnit.SECONDS), "peer should receive the GET");
            call.cancel();
            call.cancel();

            assertTrue(call.response().isCancelled(), "cancellation should cancel the response stage");
            assertTrue(connectionClosed.await(5, TimeUnit.SECONDS), "reset should close the held-open GET");
        } finally {
            server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            serverVertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testCancellingAsyncPostClosesHeldOpenConnection() throws Exception {
        Vertx serverVertx = Vertx.vertx();
        CountDownLatch requestBodyReceived = new CountDownLatch(1);
        CountDownLatch connectionClosed = new CountDownLatch(1);
        HttpServer server = serverVertx.createHttpServer().requestHandler(request -> {
            request.connection().closeHandler(ignored -> connectionClosed.countDown());
            request.bodyHandler(ignored -> requestBodyReceived.countDown());
        });
        try {
            server.listen(0).toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            A2AHttpClient.AsyncPostCall call = client.createPost()
                    .url("http://localhost:" + server.actualPort() + "/held-open")
                    .body("request-body")
                    .postAsyncResponse();

            assertTrue(requestBodyReceived.await(5, TimeUnit.SECONDS), "peer should receive the complete POST");
            call.cancel();
            call.cancel();

            assertTrue(call.response().isCancelled(), "cancellation should cancel the response stage");
            assertTrue(connectionClosed.await(5, TimeUnit.SECONDS), "reset should close the held-open peer connection");
        } finally {
            server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            serverVertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testCancellingAsyncSseClosesHeldOpenConnection() throws Exception {
        Vertx serverVertx = Vertx.vertx();
        CountDownLatch requestBodyReceived = new CountDownLatch(1);
        CountDownLatch connectionClosed = new CountDownLatch(1);
        HttpServer server = serverVertx.createHttpServer().requestHandler(request -> {
            request.connection().closeHandler(ignored -> connectionClosed.countDown());
            request.response().putHeader("Content-Type", "text/event-stream").setChunked(true)
                    .write("data: {\"id\":\"keep-open\"}\n\n");
            request.bodyHandler(ignored -> requestBodyReceived.countDown());
        });
        try {
            server.listen(0).toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            A2AHttpClient.AsyncSSECall call = client.createPost()
                    .url("http://localhost:" + server.actualPort() + "/held-open-sse")
                    .body("request-body")
                    .postAsyncSSECall(event -> { }, failure -> { }, () -> { });

            assertTrue(requestBodyReceived.await(5, TimeUnit.SECONDS), "peer should receive the complete SSE POST");
            call.cancel();
            call.cancel();

            assertTrue(call.completion().isCancelled(), "cancellation should stop the completion stage");
            assertTrue(connectionClosed.await(5, TimeUnit.SECONDS), "request reset should close the SSE connection");
        } finally {
            server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            serverVertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testCancellableSseRejectsHttp500JsonRpcResultBeforeMessageConsumer() throws Exception {
        String validResult = "{\"jsonrpc\":\"2.0\",\"id\":\"request-1\",\"result\":{\"task\":{\"id\":\"task-1\",\"contextId\":\"context-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}}";
        mockServer.when(request().withMethod("POST").withPath("/non-2xx-sse"))
                .respond(response().withStatusCode(500).withHeader("Content-Type", "application/json")
                        .withBody(validResult));
        AtomicReference<org.a2aproject.sdk.client.http.ServerSentEvent> message = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger completed = new AtomicInteger();

        A2AHttpClient.AsyncSSECall call = client.createPost()
                .url(getBaseUrl() + "/non-2xx-sse")
                .body("{}")
                .postAsyncSSECallStatusAware(message::set, failure::set, completed::incrementAndGet);
        call.completion().get(5, TimeUnit.SECONDS);

        assertNull(message.get(), "a valid JSON-RPC result in a 500 response must not reach the decoder");
        assertTrue(failure.get() != null);
        assertInstanceOf(org.a2aproject.sdk.spec.A2AClientHTTPError.class, failure.get().getCause());
        assertEquals(0, completed.get());
    }

    @Test
    public void testCancellableGetSsePreservesNonSuccessBodyWithoutSseDecoding() throws Exception {
        String errorBody = "{\"error\":{\"code\":409,\"message\":\"generation mismatch\"}}";
        mockServer.when(request().withMethod("GET").withPath("/non-2xx-get-sse"))
                .respond(response().withStatusCode(409).withHeader("Content-Type", "application/json")
                        .withBody(errorBody));
        AtomicReference<org.a2aproject.sdk.client.http.ServerSentEvent> message = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger completed = new AtomicInteger();

        A2AHttpClient.AsyncSSECall call = client.createGet()
                .url(getBaseUrl() + "/non-2xx-get-sse")
                .getAsyncSSECallStatusAware(message::set, failure::set, completed::incrementAndGet);
        call.completion().get(5, TimeUnit.SECONDS);

        assertNull(message.get());
        org.a2aproject.sdk.spec.A2AClientHTTPError httpError = assertInstanceOf(
                org.a2aproject.sdk.spec.A2AClientHTTPError.class, failure.get().getCause());
        assertEquals(409, httpError.getCode());
        assertEquals(errorBody, httpError.getResponseBody());
        assertEquals(0, completed.get());
    }

    @Test
    public void statusAwareGetSseCancelsOversizedErrorBodyBeforeEndOfBody() throws Exception {
        assertOversizedStatusAwareErrorBody(false);
    }

    @Test
    public void statusAwarePostSseCancelsOversizedErrorBodyBeforeEndOfBody() throws Exception {
        assertOversizedStatusAwareErrorBody(true);
    }

    private void assertOversizedStatusAwareErrorBody(boolean post) throws Exception {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
        com.sun.net.httpserver.HttpServer peer = com.sun.net.httpserver.HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch peerObservedClose = new CountDownLatch(1);
        AtomicInteger bytesSent = new AtomicInteger();
        peer.setExecutor(executor);
        peer.createContext("/oversized-error", exchange -> {
            try {
                if (post) {
                    exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
                }
                exchange.getResponseHeaders().add("X-Error-Test", "oversized");
                exchange.sendResponseHeaders(500, 2 * 1024 * 1024);
                byte[] chunk = "x".repeat(4096).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                try (OutputStream output = exchange.getResponseBody()) {
                    while (bytesSent.get() < 2 * 1024 * 1024) {
                        output.write(chunk);
                        output.flush();
                        bytesSent.addAndGet(chunk.length);
                        Thread.sleep(10);
                    }
                }
            } catch (IOException closedByClient) {
                peerObservedClose.countDown();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        peer.start();
        try {
            AtomicInteger events = new AtomicInteger();
            AtomicInteger errors = new AtomicInteger();
            AtomicInteger completed = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            String url = "http://127.0.0.1:" + peer.getAddress().getPort() + "/oversized-error";
            A2AHttpClient.AsyncSSECall call = post
                    ? client.createPost().url(url).body("{}")
                            .postAsyncSSECallStatusAware(event -> events.incrementAndGet(), error -> {
                                errors.incrementAndGet();
                                failure.set(error);
                            }, completed::incrementAndGet)
                    : client.createGet().url(url)
                            .getAsyncSSECallStatusAware(event -> events.incrementAndGet(), error -> {
                                errors.incrementAndGet();
                                failure.set(error);
                            }, completed::incrementAndGet);

            call.completion().get(5, TimeUnit.SECONDS);

            assertTrue(peerObservedClose.await(2, TimeUnit.SECONDS), "oversized error body read was not cancelled");
            assertTrue(bytesSent.get() < 2 * 1024 * 1024, "peer completed the oversized body instead of observing cancellation");
            assertEquals(0, events.get());
            assertEquals(1, errors.get());
            assertEquals(0, completed.get());
            assertTrue(failure.get().getMessage().contains("exceeds"));
            org.a2aproject.sdk.spec.A2AClientHTTPError httpError = assertInstanceOf(
                    org.a2aproject.sdk.spec.A2AClientHTTPError.class, failure.get().getCause());
            assertEquals(500, httpError.getCode());
            assertNull(httpError.getResponseBody());
            assertEquals("oversized", httpError.getResponseHeaders().get("X-Error-Test").get(0));
        } finally {
            peer.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    public void testPostRequestSuccess() throws Exception {
        mockServer
                .when(request()
                        .withMethod("POST")
                        .withPath("/test")
                        .withBody("{\"key\":\"value\"}"))
                .respond(response().withStatusCode(201).withBody("created"));

        A2AHttpResponse response = client.createPost()
                .url(getBaseUrl() + "/test")
                .body("{\"key\":\"value\"}")
                .post();

        assertEquals(201, response.status());
        assertTrue(response.success());
        assertEquals("created", response.body());
    }

    @Test
    public void testDeleteRequestSuccess() throws Exception {
        mockServer
                .when(request().withMethod("DELETE").withPath("/test"))
                .respond(response().withStatusCode(204));

        A2AHttpResponse response = client.createDelete()
                .url(getBaseUrl() + "/test")
                .delete();

        assertEquals(204, response.status());
        assertTrue(response.success());
    }

    @Test
    public void testCustomHostHeaderOverride() throws Exception {
        // Unlike the JDK-based client, VertxA2AHttpClient does not build on
        // java.net.http.HttpClient and so is not subject to its restricted-header
        // check: callers can override "Host" while still connecting to the URL's
        // own host:port (e.g. to reach an instance directly while presenting the
        // hostname a load balancer would normally supply).
        mockServer
                .when(request().withMethod("GET").withPath("/test"))
                .respond(response().withStatusCode(200).withBody("ok"));

        A2AHttpResponse response = client.createGet()
                .url(getBaseUrl() + "/test")
                .addHeader("Host", "custom-logical-host.example.com")
                .get();

        assertEquals(200, response.status());
        mockServer.verify(
                request().withPath("/test").withHeader("Host", "custom-logical-host.example.com"),
                VerificationTimes.once());
    }

    @Test
    public void test401AuthenticationErrorOnGet() throws Exception {
        mockServer
                .when(request().withMethod("GET").withPath("/test"))
                .respond(response().withStatusCode(401));

        Exception exception = assertThrows(java.io.IOException.class, () -> {
            client.createGet()
                    .url(getBaseUrl() + "/test")
                    .get();
        });

        assertEquals(A2AErrorMessages.AUTHENTICATION_FAILED, exception.getMessage());
    }

    @Test
    public void test403AuthorizationErrorOnGet() throws Exception {
        mockServer
                .when(request().withMethod("GET").withPath("/test"))
                .respond(response().withStatusCode(403));

        Exception exception = assertThrows(java.io.IOException.class, () -> {
            client.createGet()
                    .url(getBaseUrl() + "/test")
                    .get();
        });

        assertEquals(A2AErrorMessages.AUTHORIZATION_FAILED, exception.getMessage());
    }

    @Test
    public void test401AuthenticationErrorOnPost() throws Exception {
        mockServer
                .when(request().withMethod("POST").withPath("/test"))
                .respond(response().withStatusCode(401));

        Exception exception = assertThrows(java.io.IOException.class, () -> {
            client.createPost()
                    .url(getBaseUrl() + "/test")
                    .body("{}")
                    .post();
        });

        assertEquals(A2AErrorMessages.AUTHENTICATION_FAILED, exception.getMessage());
    }

    @Test
    public void test403AuthorizationErrorOnPost() throws Exception {
        mockServer
                .when(request().withMethod("POST").withPath("/test"))
                .respond(response().withStatusCode(403));

        Exception exception = assertThrows(java.io.IOException.class, () -> {
            client.createPost()
                    .url(getBaseUrl() + "/test")
                    .body("{}")
                    .post();
        });

        assertEquals(A2AErrorMessages.AUTHORIZATION_FAILED, exception.getMessage());
    }

    @Test
    public void test401AuthenticationErrorOnDelete() throws Exception {
        mockServer
                .when(request().withMethod("DELETE").withPath("/test"))
                .respond(response().withStatusCode(401));

        Exception exception = assertThrows(java.io.IOException.class, () -> {
            client.createDelete()
                    .url(getBaseUrl() + "/test")
                    .delete();
        });

        assertEquals(A2AErrorMessages.AUTHENTICATION_FAILED, exception.getMessage());
    }

    @Test
    public void testHeaderPropagation() throws Exception {
        mockServer
                .when(request()
                        .withMethod("GET")
                        .withPath("/test")
                        .withHeader("Authorization", "Bearer token")
                        .withHeader("X-Custom-Header", "custom-value"))
                .respond(response().withStatusCode(200).withBody("ok"));

        A2AHttpResponse response = client.createGet()
                .url(getBaseUrl() + "/test")
                .addHeader("Authorization", "Bearer token")
                .addHeader("X-Custom-Header", "custom-value")
                .get();

        assertEquals(200, response.status());
        assertEquals("ok", response.body());
    }

    @Test
    public void testNonSuccessStatusCode() throws Exception {
        mockServer
                .when(request().withMethod("GET").withPath("/test"))
                .respond(response().withStatusCode(500).withBody("Internal Server Error"));

        A2AHttpResponse response = client.createGet()
                .url(getBaseUrl() + "/test")
                .get();

        assertEquals(500, response.status());
        assertFalse(response.success());
        assertEquals("Internal Server Error", response.body());
    }

    @Test
    public void test404NotFound() throws Exception {
        mockServer
                .when(request().withMethod("GET").withPath("/test"))
                .respond(response().withStatusCode(404).withBody("Not Found"));

        A2AHttpResponse response = client.createGet()
                .url(getBaseUrl() + "/test")
                .get();

        assertEquals(404, response.status());
        assertFalse(response.success());
        assertEquals("Not Found", response.body());
    }
}
