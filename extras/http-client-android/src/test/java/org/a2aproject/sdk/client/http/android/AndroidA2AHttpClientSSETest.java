package org.a2aproject.sdk.client.http.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpClientConfiguration;
import org.a2aproject.sdk.client.http.AbstractA2AHttpClientSSETest;
import org.a2aproject.sdk.client.http.SSEParserConfig;
import org.a2aproject.sdk.client.http.ServerSentEvent;
import org.junit.jupiter.api.Test;

public class AndroidA2AHttpClientSSETest extends AbstractA2AHttpClientSSETest {

    @Test
    public void asyncGetResponsePreservesNonSuccessStatusAndBody() throws Exception {
        HttpServer peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        peer.createContext("/async-get", exchange -> {
            byte[] body = "failure body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        peer.start();
        try {
            A2AHttpClient.AsyncGetCall call = createClient().createGet()
                    .url("http://127.0.0.1:" + peer.getAddress().getPort() + "/async-get")
                    .getAsyncResponse();
            var response = call.response().get(5, TimeUnit.SECONDS);

            assertEquals(500, response.status());
            assertFalse(response.success());
            assertEquals("failure body", response.body());
        } finally {
            peer.stop(0);
        }
    }

    @Test
    public void cancellableGetSsePreservesNonSuccessBodyWithoutSseDecoding() throws Exception {
        HttpServer peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String errorBody = "{\"error\":{\"code\":409,\"message\":\"generation mismatch\"}}";
        peer.createContext("/non-2xx-get-sse", exchange -> {
            byte[] body = errorBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(409, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        peer.start();
        try {
            AtomicReference<ServerSentEvent> message = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicInteger completed = new AtomicInteger();
            A2AHttpClient.AsyncSSECall call = createClient().createGet()
                    .url("http://127.0.0.1:" + peer.getAddress().getPort() + "/non-2xx-get-sse")
                    .getAsyncSSECallStatusAware(message::set, failure::set, completed::incrementAndGet);
            call.completion().get(5, TimeUnit.SECONDS);

            assertNull(message.get());
            org.a2aproject.sdk.spec.A2AClientHTTPError httpError = assertInstanceOf(
                    org.a2aproject.sdk.spec.A2AClientHTTPError.class, failure.get().getCause());
            assertEquals(409, httpError.getCode());
            assertEquals(errorBody, httpError.getResponseBody());
            assertEquals(0, completed.get());
        } finally {
            peer.stop(0);
        }
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
        HttpServer peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch peerObservedClose = new CountDownLatch(1);
        AtomicInteger bytesSent = new AtomicInteger();
        peer.setExecutor(executor);
        peer.createContext("/oversized-error", exchange -> writeOversizedErrorBody(exchange, post,
                peerObservedClose, bytesSent));
        peer.start();
        try {
            AtomicInteger events = new AtomicInteger();
            AtomicInteger errors = new AtomicInteger();
            AtomicInteger completed = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            String url = "http://127.0.0.1:" + peer.getAddress().getPort() + "/oversized-error";
            A2AHttpClient.AsyncSSECall call = post
                    ? createClient().createPost().url(url).body("{}")
                            .postAsyncSSECallStatusAware(event -> events.incrementAndGet(), error -> {
                                errors.incrementAndGet();
                                failure.set(error);
                            }, completed::incrementAndGet)
                    : createClient().createGet().url(url)
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

    private static void writeOversizedErrorBody(
            HttpExchange exchange, boolean post, CountDownLatch peerObservedClose, AtomicInteger bytesSent) {
        try {
            if (post) {
                exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            }
            exchange.getResponseHeaders().add("X-Error-Test", "oversized");
            exchange.sendResponseHeaders(500, 2 * 1024 * 1024);
            byte[] chunk = "x".repeat(4096).getBytes(StandardCharsets.US_ASCII);
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
    }

    @Test
    public void cancellingAsyncGetDisconnectsHeldOpenPeer() throws Exception {
        HttpServer peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch responseStarted = new CountDownLatch(1);
        CountDownLatch peerObservedClose = new CountDownLatch(1);
        peer.setExecutor(executor);
        peer.createContext("/held-open-get", exchange -> streamGetUntilClosed(exchange, responseStarted,
                peerObservedClose));
        peer.start();
        try {
            A2AHttpClient.AsyncGetCall call = createClient().createGet()
                    .url("http://127.0.0.1:" + peer.getAddress().getPort() + "/held-open-get")
                    .getAsyncResponse();
            assertTrue(responseStarted.await(5, TimeUnit.SECONDS), "peer should start the held-open GET response");

            call.cancel();

            assertTrue(call.response().isCancelled());
            assertTrue(peerObservedClose.await(5, TimeUnit.SECONDS), "disconnect should close the held-open GET read");
        } finally {
            peer.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    public void cancellableSseDisconnectsHeldOpenPeer() throws Exception {
        HttpServer peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch peerObservedClose = new CountDownLatch(1);
        peer.setExecutor(executor);
        peer.createContext("/held-open", exchange -> streamUntilClosed(exchange, requestReceived, peerObservedClose));
        peer.start();
        try {
            A2AHttpClient.AsyncSSECall call = createClient().createPost()
                    .url("http://127.0.0.1:" + peer.getAddress().getPort() + "/held-open")
                    .body("request-body")
                    .postAsyncSSECall(event -> { }, failure -> { }, () -> { });

            assertTrue(requestReceived.await(5, TimeUnit.SECONDS), "peer should receive the complete SSE POST");
            call.cancel();

            assertTrue(call.completion().isCancelled(), "SSE completion should be cancelled");
            assertTrue(peerObservedClose.await(5, TimeUnit.SECONDS), "disconnect should close the held-open peer read");
        } finally {
            peer.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    public void cancellableSseRejectsHttp500JsonRpcResultBeforeMessageConsumer() throws Exception {
        HttpServer peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        peer.setExecutor(executor);
        peer.createContext("/non-2xx-sse", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] body = "{\"jsonrpc\":\"2.0\",\"id\":\"request-1\",\"result\":{\"task\":{\"id\":\"task-1\",\"contextId\":\"context-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        peer.start();
        try {
            AtomicReference<ServerSentEvent> message = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicInteger completed = new AtomicInteger();
            A2AHttpClient.AsyncSSECall call = createClient().createPost()
                    .url("http://127.0.0.1:" + peer.getAddress().getPort() + "/non-2xx-sse")
                    .body("{}")
                    .postAsyncSSECallStatusAware(message::set, failure::set, completed::incrementAndGet);
            call.completion().get(5, TimeUnit.SECONDS);

            assertNull(message.get(), "a valid JSON-RPC result in a 500 response must not reach the decoder");
            assertTrue(failure.get() != null);
            assertInstanceOf(org.a2aproject.sdk.spec.A2AClientHTTPError.class, failure.get().getCause());
            assertEquals(0, completed.get());
        } finally {
            peer.stop(0);
            executor.shutdownNow();
        }
    }

    private static void streamUntilClosed(HttpExchange exchange, CountDownLatch requestReceived,
            CountDownLatch peerObservedClose) {
        try {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream output = exchange.getResponseBody();
            output.write("data: keep-open\n\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
            requestReceived.countDown();
            while (true) {
                output.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                Thread.sleep(50);
            }
        } catch (IOException closed) {
            peerObservedClose.countDown();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    @Override
    protected A2AHttpClient createClient() {
        return new AndroidA2AHttpClient();
    }

    @Override
    protected A2AHttpClient createClient(SSEParserConfig sseParserConfig) {
        return new AndroidA2AHttpClientProvider().create(A2AHttpClientConfiguration.builder()
                .sseParserConfig(sseParserConfig)
                .build());
    }

    private static void streamGetUntilClosed(HttpExchange exchange, CountDownLatch responseStarted,
            CountDownLatch peerObservedClose) {
        try {
            exchange.sendResponseHeaders(200, 0);
            OutputStream output = exchange.getResponseBody();
            output.write("held-open".getBytes(StandardCharsets.UTF_8));
            output.flush();
            responseStarted.countDown();
            while (true) {
                output.write("keep-alive".getBytes(StandardCharsets.UTF_8));
                output.flush();
                Thread.sleep(50);
            }
        } catch (IOException closed) {
            peerObservedClose.countDown();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }
}
