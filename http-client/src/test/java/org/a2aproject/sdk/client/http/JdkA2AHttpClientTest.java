package org.a2aproject.sdk.client.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;

public class JdkA2AHttpClientTest {

    private ClientAndServer server;
    private ClientAndServer collector;

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (collector != null) {
            collector.stop();
        }
    }

    @Test
    public void testDefaultConstructorCreatesUsableClient() throws Exception {
        server = ClientAndServer.startClientAndServer(0);
        server.when(request().withMethod("GET").withPath("/default"))
                .respond(response().withStatusCode(200).withBody("ok"));

        JdkA2AHttpClient client = new JdkA2AHttpClient();

        A2AHttpResponse response = client.createGet()
                .url("http://localhost:" + server.getLocalPort() + "/default")
                .get();

        assertEquals(200, response.status());
        assertEquals("ok", response.body());
    }

    @Test
    public void postAsyncResponsePreservesNonSuccessStatusAndBody() throws Exception {
        server = ClientAndServer.startClientAndServer(0);
        server.when(request().withMethod("POST").withPath("/async"))
                .respond(response().withStatusCode(500).withBody("failure body"));

        A2AHttpClient.AsyncPostCall call = new JdkA2AHttpClient().createPost()
                .url("http://localhost:" + server.getLocalPort() + "/async")
                .body("{}")
                .postAsyncResponse();
        A2AHttpResponse result = call.response().get(5, TimeUnit.SECONDS);

        assertEquals(500, result.status());
        assertFalse(result.success());
        assertEquals("failure body", result.body());
    }

    @Test
    public void getAsyncResponsePreservesNonSuccessStatusAndBody() throws Exception {
        server = ClientAndServer.startClientAndServer(0);
        server.when(request().withMethod("GET").withPath("/async-get"))
                .respond(response().withStatusCode(500).withBody("failure body"));

        A2AHttpClient.AsyncGetCall call = new JdkA2AHttpClient().createGet()
                .url("http://localhost:" + server.getLocalPort() + "/async-get")
                .getAsyncResponse();
        A2AHttpResponse result = call.response().get(5, TimeUnit.SECONDS);

        assertEquals(500, result.status());
        assertFalse(result.success());
        assertEquals("failure body", result.body());
    }

    @Test
    public void cancellingAsyncGetClosesHeldOpenPeerConnection() throws Exception {
        try (ServerSocket listener = new ServerSocket(0)) {
            CountDownLatch requestRead = new CountDownLatch(1);
            CountDownLatch peerObservedClose = new CountDownLatch(1);
            AtomicReference<Throwable> peerFailure = new AtomicReference<>();
            Thread peer = new Thread(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    InputStream input = socket.getInputStream();
                    int matched = 0;
                    int value;
                    while ((value = input.read()) >= 0) {
                        matched = switch (matched) {
                            case 0 -> value == '\r' ? 1 : 0;
                            case 1 -> value == '\n' ? 2 : value == '\r' ? 1 : 0;
                            case 2 -> value == '\r' ? 3 : 0;
                            case 3 -> value == '\n' ? 4 : 0;
                            default -> matched;
                        };
                        if (matched == 4) break;
                    }
                    requestRead.countDown();
                    if (input.read() == -1) peerObservedClose.countDown();
                } catch (Throwable failure) {
                    peerFailure.set(failure);
                    requestRead.countDown();
                }
            }, "held-open-get-peer");
            peer.setDaemon(true);
            peer.start();

            A2AHttpClient.AsyncGetCall call = new JdkA2AHttpClient().createGet()
                    .url("http://localhost:" + listener.getLocalPort() + "/held-open-get")
                    .getAsyncResponse();
            assertTrue(requestRead.await(5, TimeUnit.SECONDS), "peer did not receive the GET request");
            assertNull(peerFailure.get());

            call.cancel();

            assertTrue(call.response().isCancelled());
            assertTrue(peerObservedClose.await(5, TimeUnit.SECONDS), "cancel left the held-open GET active");
            assertNull(peerFailure.get());
        }
    }

    @Test
    public void cancellingAsyncResponseClosesHeldOpenPeerConnection() throws Exception {
        try (ServerSocket listener = new ServerSocket(0)) {
            CountDownLatch requestRead = new CountDownLatch(1);
            CountDownLatch peerObservedClose = new CountDownLatch(1);
            AtomicReference<Throwable> peerFailure = new AtomicReference<>();
            Thread peer = new Thread(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    InputStream input = socket.getInputStream();
                    ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
                    int matched = 0;
                    int value;
                    while ((value = input.read()) >= 0) {
                        headerBytes.write(value);
                        matched = switch (matched) {
                            case 0 -> value == '\r' ? 1 : 0;
                            case 1 -> value == '\n' ? 2 : value == '\r' ? 1 : 0;
                            case 2 -> value == '\r' ? 3 : 0;
                            case 3 -> value == '\n' ? 4 : 0;
                            default -> matched;
                        };
                        if (matched == 4) break;
                    }
                    String headers = headerBytes.toString(java.nio.charset.StandardCharsets.US_ASCII);
                    int contentLength = 0;
                    for (String line : headers.split("\\r\\n")) {
                        if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        }
                    }
                    for (int i = 0; i < contentLength; i++) {
                        if (input.read() < 0) throw new IOException("request body ended early");
                    }
                    requestRead.countDown();
                    if (input.read() == -1) peerObservedClose.countDown();
                } catch (Throwable failure) {
                    peerFailure.set(failure);
                    requestRead.countDown();
                }
            }, "held-open-http-peer");
            peer.setDaemon(true);
            peer.start();

            A2AHttpClient.AsyncPostCall call = new JdkA2AHttpClient().createPost()
                    .url("http://localhost:" + listener.getLocalPort() + "/held-open")
                    .body("{}")
                    .postAsyncResponse();
            assertTrue(requestRead.await(5, TimeUnit.SECONDS), "peer did not receive the complete request");
            assertNull(peerFailure.get());

            call.cancel();

            assertTrue(call.response().isCancelled());
            assertTrue(peerObservedClose.await(5, TimeUnit.SECONDS), "cancel left the held-open peer connection active");
            assertNull(peerFailure.get());
        }
    }

    @Test
    public void cancellableSseRejectsHttp500JsonRpcResultBeforeMessageConsumer() throws Exception {
        server = ClientAndServer.startClientAndServer(0);
        String validResult = "{\"jsonrpc\":\"2.0\",\"id\":\"request-1\",\"result\":{\"task\":{\"id\":\"task-1\",\"contextId\":\"context-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}}";
        server.when(request().withMethod("POST").withPath("/non-2xx-sse"))
                .respond(response().withStatusCode(500).withHeader("Content-Type", "application/json")
                        .withBody(validResult));
        AtomicReference<ServerSentEvent> message = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger completed = new AtomicInteger();

        A2AHttpClient.AsyncSSECall call = new JdkA2AHttpClient().createPost()
                .url("http://localhost:" + server.getLocalPort() + "/non-2xx-sse")
                .body("{}")
                .postAsyncSSECallStatusAware(message::set, failure::set, completed::incrementAndGet);
        call.completion().get(5, TimeUnit.SECONDS);

        assertNull(message.get(), "a valid JSON-RPC result in a 500 response must not reach the decoder");
        assertNotNull(failure.get());
        org.a2aproject.sdk.spec.A2AClientHTTPError httpError = assertInstanceOf(
                org.a2aproject.sdk.spec.A2AClientHTTPError.class, failure.get().getCause());
        assertEquals(validResult, httpError.getResponseBody());
        assertEquals(0, completed.get());
    }

    @Test
    public void cancellableGetSsePreservesNonSuccessBodyWithoutSseDecoding() throws Exception {
        server = ClientAndServer.startClientAndServer(0);
        String errorBody = "{\"error\":{\"code\":409,\"message\":\"generation mismatch\"}}";
        server.when(request().withMethod("GET").withPath("/non-2xx-get-sse"))
                .respond(response().withStatusCode(409).withHeader("Content-Type", "application/json")
                        .withBody(errorBody));
        AtomicReference<ServerSentEvent> message = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger completed = new AtomicInteger();

        A2AHttpClient.AsyncSSECall call = new JdkA2AHttpClient().createGet()
                .url("http://localhost:" + server.getLocalPort() + "/non-2xx-get-sse")
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
        HttpServer peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch peerObservedClose = new CountDownLatch(1);
        AtomicInteger bytesSent = new AtomicInteger();
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
                    ? new JdkA2AHttpClient().createPost().url(url).body("{}")
                            .postAsyncSSECallStatusAware(event -> events.incrementAndGet(), error -> {
                                errors.incrementAndGet();
                                failure.set(error);
                            }, completed::incrementAndGet)
                    : new JdkA2AHttpClient().createGet().url(url)
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
            assertNotNull(failure.get());
            assertTrue(failure.get().getMessage().contains("exceeds"));
            org.a2aproject.sdk.spec.A2AClientHTTPError httpError = assertInstanceOf(
                    org.a2aproject.sdk.spec.A2AClientHTTPError.class, failure.get().getCause());
            assertEquals(500, httpError.getCode());
            assertNull(httpError.getResponseBody());
            assertEquals("oversized", httpError.getResponseHeaders().get("X-Error-Test").get(0));
        } finally {
            peer.stop(0);
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
    }

    @Test
    public void cancellingAsyncSseClosesHeldOpenPeerConnection() throws Exception {
        try (ServerSocket listener = new ServerSocket(0)) {
            CountDownLatch requestRead = new CountDownLatch(1);
            CountDownLatch peerObservedClose = new CountDownLatch(1);
            AtomicReference<Throwable> peerFailure = new AtomicReference<>();
            Thread peer = new Thread(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    InputStream input = socket.getInputStream();
                    ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
                    int matched = 0;
                    int value;
                    while ((value = input.read()) >= 0) {
                        headerBytes.write(value);
                        matched = switch (matched) {
                            case 0 -> value == '\r' ? 1 : 0;
                            case 1 -> value == '\n' ? 2 : value == '\r' ? 1 : 0;
                            case 2 -> value == '\r' ? 3 : 0;
                            case 3 -> value == '\n' ? 4 : 0;
                            default -> matched;
                        };
                        if (matched == 4) break;
                    }
                    String headers = headerBytes.toString(java.nio.charset.StandardCharsets.US_ASCII);
                    int contentLength = 0;
                    for (String line : headers.split("\\r\\n")) {
                        if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        }
                    }
                    for (int i = 0; i < contentLength; i++) {
                        if (input.read() < 0) throw new IOException("request body ended early");
                    }
                    requestRead.countDown();
                    if (input.read() == -1) peerObservedClose.countDown();
                } catch (Throwable failure) {
                    peerFailure.set(failure);
                    requestRead.countDown();
                }
            }, "held-open-sse-peer");
            peer.setDaemon(true);
            peer.start();

            A2AHttpClient.AsyncSSECall call = new JdkA2AHttpClient().createPost()
                    .url("http://localhost:" + listener.getLocalPort() + "/held-open-sse")
                    .body("{}")
                    .postAsyncSSECall(event -> { }, failure -> { }, () -> { });
            assertTrue(requestRead.await(5, TimeUnit.SECONDS), "peer did not receive the complete SSE request");
            assertNull(peerFailure.get());

            call.cancel();

            assertTrue(call.completion().isCancelled());
            assertTrue(peerObservedClose.await(5, TimeUnit.SECONDS), "cancel left the held-open SSE read active");
            assertNull(peerFailure.get());
        }
    }

    @Test
    public void testCustomHostHeaderRejectedByJdkHttpClient() {
        // Documents a JDK-wide restriction (not specific to this SDK): java.net.http.HttpClient
        // rejects an explicit "Host" header unless the JVM is started with
        // -Djdk.httpclient.allowRestrictedHeaders=host. Callers who need to send a custom Host
        // header should use that JVM property or a non-JDK A2AHttpClient implementation, e.g.
        // VertxA2AHttpClient from the a2a-java-sdk-http-client-vertx extra.
        //
        // Skip if the JVM running this test already allows a restricted "Host" header: in that
        // (documented, supported) configuration the JDK client legitimately accepts it instead.
        assumeFalse(jdkAllowsRestrictedHostHeader(), "JVM allows a restricted Host header "
                + "via jdk.httpclient.allowRestrictedHeaders; the JDK client no longer rejects it");

        JdkA2AHttpClient client = new JdkA2AHttpClient();

        assertThrows(IllegalArgumentException.class, () -> client.createGet()
                .url("http://localhost:1/unused")
                .addHeader("Host", "custom.example.com")
                .get());
    }

    private static boolean jdkAllowsRestrictedHostHeader() {
        String allowed = System.getProperty("jdk.httpclient.allowRestrictedHeaders", "");
        return Arrays.stream(allowed.split(","))
                .map(String::trim)
                .anyMatch("host"::equalsIgnoreCase);
    }

    @Test
    public void testConstructorUsesProvidedHttpClient() throws Exception {
        server = ClientAndServer.startClientAndServer(0);
        server.when(request().withMethod("GET").withPath("/custom"))
                .respond(response().withStatusCode(200).withBody("ok"));

        TrackingProxySelector proxySelector = new TrackingProxySelector();
        HttpClient providedClient = HttpClient.newBuilder()
                .proxy(proxySelector)
                .build();

        JdkA2AHttpClient client = new JdkA2AHttpClient(providedClient);

        A2AHttpResponse response = client.createGet()
                .url("http://localhost:" + server.getLocalPort() + "/custom")
                .get();

        assertEquals(200, response.status());
        assertEquals("ok", response.body());
        assertEquals(1, proxySelector.selectCount.get(),
                "Provided HttpClient should be used for request execution");
    }

    @Test
    public void testConstructorRejectsNullHttpClient() {
        assertThrows(IllegalArgumentException.class, () -> new JdkA2AHttpClient((java.net.http.HttpClient) null), "foo");
    }

    @Test
    public void testPostFollowRedirectsFalseDoesNotFollowRedirect() throws Exception {
        server = ClientAndServer.startClientAndServer(0);
        int port = server.getLocalPort();
        server.when(request().withMethod("POST").withPath("/redirect"))
                .respond(response()
                        .withStatusCode(302)
                        .withHeader("Location", "http://localhost:" + port + "/target"));
        server.when(request().withMethod("POST").withPath("/target"))
                .respond(response().withStatusCode(200).withBody("redirected"));

        JdkA2AHttpClient client = new JdkA2AHttpClient();

        A2AHttpResponse response = client.createPost()
                .url("http://localhost:" + port + "/redirect")
                .body("{}")
                .followRedirects(false)
                .post();

        assertEquals(302, response.status(),
                "With followRedirects(false), the 302 should be returned as-is");
    }

    @Test
    public void testPostWithoutRedirectsPreservesSuppliedCookieHandler() throws Exception {
        server = ClientAndServer.startClientAndServer(0);
        int port = server.getLocalPort();
        server.when(request().withMethod("POST").withPath("/post"))
                .respond(response().withStatusCode(200));

        CookieManager cookieManager = new CookieManager();
        URI uri = URI.create("http://localhost:" + port + "/post");
        cookieManager.put(uri, Map.of("Set-Cookie", List.of("session=secret; Path=/")));
        HttpClient suppliedClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        A2AHttpResponse result = new JdkA2AHttpClient(suppliedClient).createPost()
                .url(uri.toString())
                .body("{}")
                .followRedirects(false)
                .post();

        assertEquals(200, result.status());
        org.mockserver.model.HttpRequest[] requests = server.retrieveRecordedRequests(
                request().withMethod("POST").withPath("/post"));
        assertEquals(1, requests.length);
        assertEquals("session=secret", requests[0].getFirstHeader("Cookie"));
    }

    @Test
    public void testCrossOriginRedirectDoesNotUseSuppliedCookieHandler() throws Exception {
        server = ClientAndServer.startClientAndServer(0);
        collector = ClientAndServer.startClientAndServer(0);
        int redirectPort = server.getLocalPort();
        server.when(request().withMethod("POST").withPath("/redirect"))
                .respond(response()
                        .withStatusCode(302)
                        .withHeader("Location", "http://127.0.0.1:" + collector.getLocalPort() + "/target"));
        collector.when(request().withMethod("GET").withPath("/target"))
                .respond(response().withStatusCode(200).withBody("redirected"));

        CookieManager cookieManager = new CookieManager();
        cookieManager.put(
                URI.create("http://127.0.0.1:" + redirectPort + "/redirect"),
                Map.of("Set-Cookie", List.of("session=secret; Path=/")));
        URI targetUri = URI.create("http://127.0.0.1:" + collector.getLocalPort() + "/target");
        assertEquals(List.of("session=secret"), cookieManager.get(targetUri, Map.of()).get("Cookie"),
                "The supplied CookieHandler must be capable of injecting the cross-port cookie");
        HttpClient suppliedClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        A2AHttpResponse result = new JdkA2AHttpClient(suppliedClient).createPost()
                .url("http://127.0.0.1:" + redirectPort + "/redirect")
                .body("{}")
                .followRedirects(true)
                .post();

        assertEquals(200, result.status());
        org.mockserver.model.HttpRequest[] requests = collector.retrieveRecordedRequests(
                request().withMethod("GET").withPath("/target"));
        assertEquals(1, requests.length);
        String cookie = requests[0].getFirstHeader("Cookie");
        assertTrue(cookie == null || cookie.isEmpty(),
                "Cross-origin redirect must not allow a supplied CookieHandler to inject cookies");
    }

    @Test
    public void testCrossOriginRedirectPreservesSuppliedProxySelector() throws Exception {
        server = ClientAndServer.startClientAndServer(0);
        collector = ClientAndServer.startClientAndServer(0);
        int redirectPort = server.getLocalPort();
        server.when(request().withMethod("POST").withPath("/redirect"))
                .respond(response()
                        .withStatusCode(302)
                        .withHeader("Location", "http://127.0.0.1:" + collector.getLocalPort() + "/target"));
        collector.when(request().withMethod("GET").withPath("/target"))
                .respond(response().withStatusCode(200).withBody("redirected"));

        TrackingProxySelector proxySelector = new TrackingProxySelector();
        HttpClient suppliedClient = HttpClient.newBuilder()
                .proxy(proxySelector)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        A2AHttpResponse result = new JdkA2AHttpClient(suppliedClient).createPost()
                .url("http://127.0.0.1:" + redirectPort + "/redirect")
                .body("{}")
                .followRedirects(true)
                .post();

        assertEquals(200, result.status());
        assertEquals(2, proxySelector.selectCount.get(),
                "Supplied ProxySelector must be used for both redirect hops");
    }

    @Test
    public void testDefaultClientDoesNotFollowRedirects() throws Exception {
        server = ClientAndServer.startClientAndServer(0);

        server.when(request().withMethod("GET").withPath("/redirect"))
                .respond(response()
                        .withStatusCode(302)
                        .withHeader("Location", "http://localhost:" + server.getLocalPort() + "/target"));

        server.when(request().withMethod("GET").withPath("/target"))
                .respond(response().withStatusCode(200).withBody("redirected"));

        JdkA2AHttpClient client = new JdkA2AHttpClient();

        A2AHttpResponse response = client.createGet()
                .url("http://localhost:" + server.getLocalPort() + "/redirect")
                .get();

        assertEquals(302, response.status());
        assertFalse(response.success());
        String expectedLocation = "http://localhost:" + server.getLocalPort() + "/target";
        assertEquals(expectedLocation, response.headers().firstValue("Location"));
    }

    @Test
    public void testGetNeverFollowsNativeRedirectsEvenWithRedirectFollowingClient() throws Exception {
        server = ClientAndServer.startClientAndServer(0);

        server.when(request().withMethod("GET").withPath("/redirect"))
                .respond(response()
                        .withStatusCode(302)
                        .withHeader("Location", "http://localhost:" + server.getLocalPort() + "/target"));

        server.when(request().withMethod("GET").withPath("/target"))
                .respond(response().withStatusCode(200).withBody("redirected"));

        HttpClient customClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkA2AHttpClient client = new JdkA2AHttpClient(customClient);

        A2AHttpResponse response = client.createGet()
                .url("http://localhost:" + server.getLocalPort() + "/redirect")
                .get();

        assertEquals(302, response.status(),
                "GET must not follow native redirects; the SDK always uses a no-redirect client");
    }

    @Test
    public void testCancellationExceptionViaSubscriberOnErrorIsNotPropagated() throws Exception {
        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        CountDownLatch errorPathReached = new CountDownLatch(1);

        HttpClient fakeClient = new StubHttpClient() {
            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                    HttpRequest request, HttpResponse.BodyHandler<T> handler) {
                HttpResponse.BodySubscriber<T> bodySubscriber =
                        handler.apply(new FakeResponseInfo(200, "text/plain"));
                bodySubscriber.onSubscribe(new NoOpSubscription());
                bodySubscriber.onError(new CancellationException());
                errorPathReached.countDown();
                return new CompletableFuture<>(); // never completes
            }
        };

        new JdkA2AHttpClient(fakeClient)
                .createGet()
                .url("http://example.com/sse")
                .getAsyncSSE(event -> {}, e -> capturedError.set(e), () -> completed.set(true));

        assertTrue(errorPathReached.await(5, TimeUnit.SECONDS));
        assertNull(capturedError.get(), "CancellationException should not reach the error consumer");
        assertFalse(completed.get(), "Complete handler must not be called after cancellation");
    }

    @Test
    public void testCancellationExceptionViaFutureFailureIsNotPropagated() throws Exception {
        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        HttpClient fakeClient = new StubHttpClient() {
            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                    HttpRequest request, HttpResponse.BodyHandler<T> handler) {
                CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
                future.completeExceptionally(new CancellationException());
                return future;
            }
        };

        CompletableFuture<Void> result = new JdkA2AHttpClient(fakeClient)
                .createGet()
                .url("http://example.com/sse")
                .getAsyncSSE(event -> {}, e -> capturedError.set(e), () -> completed.set(true));

        result.get(5, TimeUnit.SECONDS);
        assertNull(capturedError.get(), "CancellationException should not reach the error consumer");
        assertFalse(completed.get(), "Complete handler must not be called after cancellation");
    }

    @Test
    public void testRealErrorsAreStillPropagatedToErrorConsumer() throws Exception {
        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        CountDownLatch errorLatch = new CountDownLatch(1);
        IOException expectedError = new IOException("connection refused");

        HttpClient fakeClient = new StubHttpClient() {
            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                    HttpRequest request, HttpResponse.BodyHandler<T> handler) {
                CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
                future.completeExceptionally(expectedError);
                return future;
            }
        };

        new JdkA2AHttpClient(fakeClient)
                .createGet()
                .url("http://example.com/sse")
                .getAsyncSSE(event -> {}, e -> { capturedError.set(e); errorLatch.countDown(); }, () -> {});

        assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
        assertNotNull(capturedError.get());
        assertEquals(expectedError, capturedError.get());
    }

    private abstract static class StubHttpClient extends HttpClient {
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public HttpClient.Redirect followRedirects() { return HttpClient.Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> HttpResponse<T> send(HttpRequest r, HttpResponse.BodyHandler<T> h) { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) { return sendAsync(r, h); }
        @Override public WebSocket.Builder newWebSocketBuilder() { throw new UnsupportedOperationException(); }
    }

    private static final class FakeResponseInfo implements HttpResponse.ResponseInfo {
        private final int statusCode;
        private final HttpHeaders headers;

        FakeResponseInfo(int statusCode, String contentType) {
            this.statusCode = statusCode;
            this.headers = HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (k, v) -> true);
        }

        @Override public int statusCode() { return statusCode; }
        @Override public HttpHeaders headers() { return headers; }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private static final class NoOpSubscription implements Flow.Subscription {
        @Override public void request(long n) {}
        @Override public void cancel() {}
    }

    private static final class TrackingProxySelector extends ProxySelector {
        private final AtomicInteger selectCount = new AtomicInteger();

        @Override
        public List<Proxy> select(URI uri) {
            selectCount.incrementAndGet();
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            throw new AssertionError("Proxy connection should not fail in this test", ioe);
        }
    }
}
