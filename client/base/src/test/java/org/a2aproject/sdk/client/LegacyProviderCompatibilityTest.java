package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientTransportProvider;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.verify.VerificationTimes;

/** Runs unchanged class files compiled against the pinned pre-Phase1 SDK, never current classes. */
class LegacyProviderCompatibilityTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void withProvider(String name, ProviderCheck check) throws Exception {
        String override = System.getProperty("legacy.provider.jar");
        URL binary = override == null
                ? LegacyProviderCompatibilityTest.class.getResource("/legacy-providers/legacy-providers.jar")
                : Path.of(override).toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {binary}, ClientBuilder.class.getClassLoader())) {
            ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try {
                Class<?> type = loader.loadClass("compatibility." + name);
                ClientTransportProvider provider = (ClientTransportProvider) type.getConstructor().newInstance();
                Field registryField = ClientBuilder.class.getDeclaredField("transportProviderRegistry");
                Field mappingField = ClientBuilder.class.getDeclaredField("transportProtocolMapping");
                registryField.setAccessible(true);
                mappingField.setAccessible(true);
                Map<String, ClientTransportProvider> registry = (Map) registryField.get(null);
                Map<Class<?>, String> mapping = (Map) mappingField.get(null);
                String binding = provider.getTransportProtocol();
                String previous = mapping.put(JSONRPCTransport.class, binding);
                registry.put(binding, provider);
                try {
                    assertEquals(Map.of("1.0", Set.of(ClientFeature.ORDINARY)), provider.capabilities());
                    check.run(binding, type);
                } finally {
                    registry.remove(binding);
                    mapping.put(JSONRPCTransport.class, previous);
                }
            } finally {
                Thread.currentThread().setContextClassLoader(previousLoader);
            }
        }
    }

    @FunctionalInterface
    private interface ProviderCheck {
        void run(String binding, Class<?> fixture) throws Exception;
    }

    @Test
    void unchangedConstantProviderLinksAndEmitsOneZeroThroughPublicClient() throws Exception {
        withProvider("LegacyConstantProvider", (binding, fixture) -> {
            ClientAndServer server = ClientAndServer.startClientAndServer(0);
            try {
                String url = "http://localhost:" + server.getLocalPort();
                String body;
                try (var input = getClass().getResourceAsStream("/legacy-providers/get-task-response.json")) {
                    body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                server.when(request().withMethod("POST").withPath("/")
                        .withHeader("A2A-Version", "1.0")).respond(response().withBody(body));
                try (Client client = Client.builder(ClientBuilderVersionTest.card(
                        new AgentInterface(binding, url, null, "1.0")))
                        .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.0").build())
                        .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder()).build()) {
                    assertEquals("1.0", client.getProtocolVersion());
                    assertEquals("de38c76d-d54c-436c-8b9f-4c2703648d64", client.getTask(new TaskQueryParams("task")).id());
                }
                server.verify(request().withMethod("POST").withHeader("A2A-Version", "1.0"), VerificationTimes.exactly(1));
                assertEquals(1, fixture.getField("creates").getInt(null));
            } finally {
                server.stop();
            }
        });
    }

    @Test
    void legacyProviderRejectsOneOneBeforeCreate() throws Exception {
        withProvider("LegacyConstantProvider", (binding, fixture) -> {
            assertThrows(A2AClientException.class, () -> Client.builder(ClientBuilderVersionTest.card(
                    new AgentInterface(binding, "http://localhost", null, "1.1")))
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder()).build());
            assertEquals(0, fixture.getField("creates").getInt(null));
        });
    }

    @Test
    void unchangedImplicitConstructorDelegateFailsItsReportedVersionMismatch() throws Exception {
        withProvider("LegacyImplicitProvider", (binding, fixture) -> {
            A2AClientException failure = assertThrows(A2AClientException.class, () -> Client.builder(ClientBuilderVersionTest.card(
                    new AgentInterface(binding, "http://localhost", null, "1.0"),
                    new AgentInterface("JSONRPC", "http://localhost", null, "1.1")))
                    .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.0").build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder()).build());
            assertTrue(failure.getMessage().contains("expected 1.0"));
            assertEquals(1, fixture.getField("creates").getInt(null));
        });
    }

    @Test
    void unchangedBinaryWrapperRemovesOneOneAndThrowingWrapperNeverBypassesIt() throws Exception {
        URL binary = getClass().getResource("/legacy-providers/legacy-providers.jar");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {binary}, ClientBuilder.class.getClassLoader())) {
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try {
                A2AClientException removed = assertThrows(A2AClientException.class, () -> Client.builder(
                        ClientBuilderVersionTest.card(new AgentInterface("JSONRPC", "http://localhost", null, "1.1")))
                        .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder()).build());
                assertTrue(removed.getMessage().contains("compatibility.LegacyWrapper"));
                loader.loadClass("compatibility.LegacyWrapper").getField("fail").setBoolean(null, true);
                A2AClientException thrown = assertThrows(A2AClientException.class, () -> Client.builder(
                        ClientBuilderVersionTest.card(new AgentInterface("JSONRPC", "http://localhost", null, "1.1")))
                        .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder()).build());
                assertTrue(thrown.getMessage().contains("compatibility.LegacyWrapper"));
                try (Client legacy = Client.builder(ClientBuilderVersionTest.card(
                        new AgentInterface("JSONRPC", "http://localhost", null, "1.0")))
                        .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.0").build())
                        .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder()).build()) {
                    assertEquals("1.0", legacy.getProtocolVersion());
                }
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        }
    }
}
