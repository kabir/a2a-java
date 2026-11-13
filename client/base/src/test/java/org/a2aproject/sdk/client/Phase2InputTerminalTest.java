package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.A2AClientHTTPError;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class Phase2InputTerminalTest {
    @Test
    void publicInputCallbackReceivesHttpStatusFailureOnceWithoutEvents() throws Exception {
        A2AClientHTTPError httpError = new A2AClientHTTPError(500, "HTTP status 500", null, Map.of());
        TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
        observation.markReady();
        observation.fail(new A2AClientException("Input stream failed", httpError));
        AtomicInteger closeCount = new AtomicInteger();
        VersionedClientTransport transport = (VersionedClientTransport) Proxy.newProxyInstance(
                VersionedClientTransport.class.getClassLoader(), new Class<?>[] {VersionedClientTransport.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "protocolVersion" -> "1.1";
                    case "capabilities" -> Set.of(ClientFeature.ORDINARY);
                    case "streamInput" -> observation;
                    case "close" -> { closeCount.incrementAndGet(); yield null; }
                    default -> null;
                });
        AgentCard card = AgentCard.builder().name("status-test").description("status-test").version("1.1")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text/plain")).defaultOutputModes(List.of("text/plain"))
                .skills(List.of()).supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost", null, "1.1")))
                .build();
        Client client = new Client(card, new ClientConfig.Builder().setProtocolVersion("1.1").setStreaming(true).build(),
                transport, List.of(), null);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        AtomicInteger events = new AtomicInteger();
        CountDownLatch terminal = new CountDownLatch(1);

        client.sendInput(SendInputParams.builder().message(Message.builder()
                        .messageId("message-1").role(Message.Role.ROLE_USER).parts(List.of(new TextPart("input"))).build())
                .build(), List.of((event, agentCard) -> events.incrementAndGet()), failure -> {
                    failures.add(failure);
                    terminal.countDown();
                }, null);

        assertTrue(terminal.await(5, TimeUnit.SECONDS));
        assertEquals(0, events.get());
        assertEquals(1, failures.size());
        assertInstanceOf(A2AClientHTTPError.class, failures.get(0).getCause());
        client.close();
        assertEquals(1, closeCount.get());
    }
}
