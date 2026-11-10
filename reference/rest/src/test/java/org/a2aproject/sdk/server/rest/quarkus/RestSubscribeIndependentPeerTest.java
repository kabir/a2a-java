package org.a2aproject.sdk.server.rest.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.junit.jupiter.api.Test;

class RestSubscribeIndependentPeerTest {
    @Test
    void publicOneOneClientSubscribesToGetOnlyIndependentPeer() throws Exception {
        assertSubscription("1.1", "GET");
    }

    @Test
    void publicOneZeroClientRetainsPostOnlyIndependentPeer() throws Exception {
        assertSubscription("1.0", "POST");
    }

    private static void assertSubscription(String version, String requiredMethod) throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<List<String>> versions = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicInteger requests = new AtomicInteger();
        HttpServer peer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        peer.createContext("/", exchange -> {
            requests.incrementAndGet();
            method.set(exchange.getRequestMethod());
            versions.set(exchange.getRequestHeaders().get("A2A-Version"));
            path.set(exchange.getRequestURI().getPath());
            if (!requiredMethod.equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Allow", requiredMethod);
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            byte[] response = ("data: {\"task\":{\"id\":\"independent-task\",\"contextId\":\"context\","
                    + "\"status\":{\"state\":\"TASK_STATE_COMPLETED\"}}}\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        peer.start();
        try {
            String endpoint = "http://localhost:" + peer.getAddress().getPort();
            AgentCard card = AgentCard.builder().name("independent peer").description("GET-only RC peer")
                    .version("test").supportedInterfaces(List.of(new AgentInterface("HTTP+JSON", endpoint, "peer-tenant", version)))
                    .capabilities(AgentCapabilities.builder().streaming(true).build())
                    .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
            AtomicReference<ClientEvent> received = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch signal = new CountDownLatch(1);
            try (Client client = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().setProtocolVersion(version).build())
                    .withTransport(RestTransport.class, new RestTransportConfigBuilder().httpClient(new JdkA2AHttpClient()))
                    .build()) {
                client.subscribeToTask(new TaskIdParams("independent-task"), List.of((event, ignored) -> {
                    received.set(event);
                    signal.countDown();
                }), error -> {
                    if (error != null) {
                        failure.set(error);
                    }
                    signal.countDown();
                }, null);
                assertTrue(signal.await(5, TimeUnit.SECONDS), "subscription did not finish");
                assertEquals(requiredMethod, method.get());
                assertEquals(List.of(version), versions.get());
                assertEquals("/peer-tenant/tasks/independent-task:subscribe", path.get());
                assertEquals(1, requests.get(), "must not retry using another verb");
                assertNull(failure.get());
                assertNotNull(received.get());
            }
        } finally {
            peer.stop(0);
        }
    }
}
