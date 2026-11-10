package org.a2aproject.sdk.client.transport.jsonrpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.GetExtendedAgentCardParams;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProtocolVersionTest {

    private final A2AHttpClient http = mock(A2AHttpClient.class);
    private final List<Map.Entry<String, String>> emittedHeaders = new ArrayList<>();
    private int exchanges;
    private String emittedUrl;

    @BeforeEach
    void setUp() throws Exception {
        A2AHttpClient.PostBuilder post = mock(A2AHttpClient.PostBuilder.class, RETURNS_SELF);
        A2AHttpClient.GetBuilder get = mock(A2AHttpClient.GetBuilder.class, RETURNS_SELF);
        A2AHttpClient.DeleteBuilder delete = mock(A2AHttpClient.DeleteBuilder.class, RETURNS_SELF);
        doAnswer(invocation -> {
            emittedUrl = invocation.getArgument(0);
            return post;
        }).when(post).url(anyString());
        when(http.createPost()).thenReturn(post);
        when(http.createGet()).thenReturn(get);
        when(http.createDelete()).thenReturn(delete);
        for (A2AHttpClient.Builder<?> builder : List.of(post, get, delete)) {
            doAnswer(invocation -> {
                emittedHeaders.add(Map.entry(invocation.getArgument(0), invocation.getArgument(1)));
                return builder;
            }).when(builder).addHeader(anyString(), anyString());
        }
        when(post.post()).thenAnswer(invocation -> failExchange());
        when(get.get()).thenAnswer(invocation -> failExchange());
        when(delete.delete()).thenAnswer(invocation -> failExchange());
        when(post.postAsyncSSE(any(), any(), any())).thenAnswer(invocation -> failExchange());
        when(get.getAsyncSSE(any(), any(), any())).thenAnswer(invocation -> failExchange());
    }

    private Object failExchange() throws IOException {
        exchanges++;
        throw new IOException("test exchange reached");
    }

    @Test
    void selectedDescriptorVersionReachesEveryUnaryAndStreamingOperation() {
        ClientTransport transport = transport("1.1", null);
        for (Consumer<ClientCallContext> operation : operations(transport)) {
            emittedHeaders.clear();
            assertThrows(A2AClientException.class, () -> operation.accept(null));
            assertEquals(List.of("1.1"), versionHeaders());
        }
        assertEquals(11, exchanges);
    }

    @Test
    void explicitOneZeroDescriptorRemainsOneZero() {
        assertThrows(A2AClientException.class, () -> transport("1.0", null).listTasks(new ListTasksParams(), null));
        assertEquals(List.of("1.0"), versionHeaders());
    }

    @Test
    void identicalCaseInsensitiveVersionHeadersCollapseToOne() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("A2A-Version", "1.1");
        headers.put("a2a-version", "1.1");
        ClientCallContext context = new ClientCallContext(Map.of(), headers);
        assertThrows(A2AClientException.class, () -> transport("1.1", null).listTasks(new ListTasksParams(), context));
        assertEquals(List.of("1.1"), versionHeaders());
        assertEquals(1, exchanges);
    }

    @Test
    void conflictingContextVersionFailsBeforeExchangeForEveryOperation() {
        ClientCallContext context = new ClientCallContext(Map.of(), Map.of("a2a-VERSION", "1.0"));
        for (Consumer<ClientCallContext> operation : operations(transport("1.1", null))) {
            assertThrows(A2AClientException.class, () -> operation.accept(context));
        }
        assertEquals(0, exchanges);
    }

    @Test
    void interceptorConflictFailsBeforeExchange() {
        ClientCallInterceptor interceptor = new ClientCallInterceptor() {
            @Override
            public PayloadAndHeaders intercept(String method, Object payload, Map<String, String> headers,
                    AgentCard card, ClientCallContext context) {
                return new PayloadAndHeaders(payload, Map.of("A2A-Version", "1.1", "a2a-version", "1.0"));
            }
        };
        assertThrows(A2AClientException.class,
                () -> transport("1.1", List.of(interceptor)).listTasks(new ListTasksParams(), null));
        assertEquals(0, exchanges);
    }

    @Test
    void defaultCardConstructorRejectsOneZeroOnlyCard() {
        AgentCard legacy = card(List.of(new AgentInterface("JSONRPC", "http://localhost", null, "1.0")));
        assertThrows(A2AClientException.class, () -> new JSONRPCTransport(legacy));
    }

    @Test
    void defaultCardConstructorRejectsWrongBinding() {
        AgentCard wrongBinding = card(List.of(new AgentInterface("HTTP+JSON", "http://localhost", null, "1.1")));
        assertThrows(A2AClientException.class, () -> new JSONRPCTransport(wrongBinding));
    }

    @Test
    void explicitDescriptorRejectsUnsupportedVersion() {
        assertThrows(IllegalArgumentException.class, () -> transport("0.3", null));
    }

    @Test
    void selectedDescriptorTenantReachesEveryOperationEndpoint() {
        for (String version : List.of("1.0", "1.1")) {
            AgentInterface descriptor = new AgentInterface("JSONRPC", "http://localhost", "selected-tenant", version);
            ClientTransport transport = new JSONRPCTransport(http, card(List.of(descriptor)), descriptor, null);
            for (Consumer<ClientCallContext> operation : operations(transport)) {
                assertThrows(A2AClientException.class, () -> operation.accept(null));
                assertEquals("http://localhost/selected-tenant", emittedUrl);
            }
        }
    }

    @Test
    void explicitRequestTenantKeepsItsEndpointScope() {
        AgentInterface descriptor = new AgentInterface("JSONRPC", "http://localhost", "selected-tenant", "1.1");
        ClientTransport transport = new JSONRPCTransport(http, card(List.of(descriptor)), descriptor, null);
        assertThrows(A2AClientException.class, () -> transport.listTasks(ListTasksParams.builder().tenant("request-tenant").build(), null));
        assertEquals("http://localhost/request-tenant", emittedUrl);
    }

    @Test
    void unscopedOneZeroDescriptorKeepsOriginalUrlWithRequestTenant() {
        AgentInterface descriptor = new AgentInterface("JSONRPC", "http://localhost/", null, "1.0");
        ClientTransport transport = new JSONRPCTransport(http, card(List.of(descriptor)), descriptor, null);
        assertThrows(A2AClientException.class, () -> transport.listTasks(ListTasksParams.builder().tenant("request-tenant").build(), null));
        assertEquals("http://localhost/", emittedUrl);
        assertEquals(List.of("1.0"), versionHeaders());
    }

    private List<String> versionHeaders() {
        return emittedHeaders.stream().filter(entry -> "A2A-Version".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).toList();
    }

    private ClientTransport transport(String version, List<ClientCallInterceptor> interceptors) {
        AgentInterface descriptor = new AgentInterface("JSONRPC", "http://localhost", null, version);
        return new JSONRPCTransport(http, card(List.of(descriptor)), descriptor, interceptors);
    }

    private static AgentCard card(List<AgentInterface> interfaces) {
        return AgentCard.builder().name("test").description("test").version("1")
                .supportedInterfaces(interfaces).capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
    }

    private static List<Consumer<ClientCallContext>> operations(ClientTransport transport) {
        MessageSendParams message = MessageSendParams.builder().message(Message.builder()
                .messageId("message").role(Message.Role.ROLE_USER).parts(List.of(new TextPart("hello"))).build()).build();
        return List.of(
                context -> transport.sendMessage(message, context),
                context -> transport.sendMessageStreaming(message, event -> { }, error -> { }, context),
                context -> transport.getTask(new TaskQueryParams("task"), context),
                context -> transport.cancelTask(new CancelTaskParams("task"), context),
                context -> transport.listTasks(new ListTasksParams(), context),
                context -> transport.createTaskPushNotificationConfiguration(TaskPushNotificationConfig.builder()
                        .taskId("task").url("https://callback.example").build(), context),
                context -> transport.getTaskPushNotificationConfiguration(new GetTaskPushNotificationConfigParams("task"), context),
                context -> transport.listTaskPushNotificationConfigurations(new ListTaskPushNotificationConfigsParams("task"), context),
                context -> transport.deleteTaskPushNotificationConfigurations(new DeleteTaskPushNotificationConfigParams("task", "config"), context),
                context -> transport.subscribeToTask(new TaskIdParams("task"), event -> { }, error -> { }, context),
                context -> transport.getExtendedAgentCard(new GetExtendedAgentCardParams(null), context));
    }
}
