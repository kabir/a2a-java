package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.ClientTransportWrapper;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.spec.A2AClientException;
import org.junit.jupiter.api.Test;

class ClientTransportPolicyTest {
    private static final Set<ClientFeature> ORDINARY = Set.of(ClientFeature.ORDINARY);
    private final ClientTransportConfig<?> config = new JSONRPCTransportConfigBuilder().build();

    private static ClientTransport transport(String version, Set<ClientFeature> features, AtomicInteger closes) {
        return (ClientTransport) Proxy.newProxyInstance(ClientTransport.class.getClassLoader(),
                version.isEmpty() ? new Class<?>[] {ClientTransport.class} : new Class<?>[] {VersionedClientTransport.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "protocolVersion" -> version;
                    case "capabilities" -> features;
                    case "close" -> { closes.incrementAndGet(); yield null; }
                    default -> null;
                });
    }

    private static ClientTransportWrapper wrapper(ClientTransport result) {
        return new ClientTransportWrapper() {
            public ClientTransport wrap(ClientTransport delegate, ClientTransportConfig<?> config) {
                return result;
            }
        };
    }

    @Test
    void identityWrapperPreservesSelectedVersionAndFeatures() throws A2AClientException {
        ClientTransport transport = transport("1.1", ORDINARY, new AtomicInteger());
        VersionedClientTransport result = ClientTransportPolicy.wrap(transport, config, "1.1", ORDINARY,
                List.of(wrapper(transport)));
        assertEquals("1.1", result.protocolVersion());
        assertEquals(ORDINARY, result.capabilities());
    }

    @Test
    void plainWrapperRemovesOneOneSupportAndClosesOwnedChain() {
        AtomicInteger closes = new AtomicInteger();
        ClientTransport base = transport("1.1", ORDINARY, new AtomicInteger());
        ClientTransport plain = transport("", ORDINARY, closes);
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(base, config, "1.1", ORDINARY,
                List.of(wrapper(plain))));
        assertEquals(1, closes.get());
    }

    @Test
    void wrongActualVersionRejectsImplicitConstructorDelegate() {
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(
                transport("1.1", ORDINARY, new AtomicInteger()), config, "1.0", ORDINARY, List.of()));
    }

    @Test
    void missingProviderOrWrapperCapabilityFailsConstruction() {
        ClientTransport base = transport("1.1", ORDINARY, new AtomicInteger());
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(base, config, "1.1", Set.of(), List.of()));
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(base, config, "1.1", ORDINARY,
                List.of(wrapper(transport("1.1", Set.of(), new AtomicInteger())))));
    }

    @Test
    void configuredWrapperExceptionFailsOneOneButRetainsHistoricalOneZeroBehavior() throws A2AClientException {
        ClientTransportWrapper throwing = new ClientTransportWrapper() {
            public ClientTransport wrap(ClientTransport delegate, ClientTransportConfig<?> config) {
                throw new IllegalStateException("configured wrapper failure");
            }
        };
        AtomicInteger closes = new AtomicInteger();
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(
                transport("1.1", ORDINARY, closes), config, "1.1", ORDINARY, List.of(throwing)));
        assertEquals(1, closes.get());
        VersionedClientTransport legacy = ClientTransportPolicy.wrap(
                transport("", ORDINARY, new AtomicInteger()), config, "1.0", ORDINARY, List.of(throwing));
        assertEquals("1.0", legacy.protocolVersion());
        assertEquals(ORDINARY, legacy.capabilities());
    }
    @Test
    void malformedActualDeclarationClosesTransportAndNamesComponent() {
        AtomicInteger closes = new AtomicInteger();
        ClientTransport malformed = (ClientTransport) Proxy.newProxyInstance(ClientTransport.class.getClassLoader(),
                new Class<?>[] {VersionedClientTransport.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "protocolVersion" -> "1.1";
                    case "capabilities" -> null;
                    case "close" -> { closes.incrementAndGet(); yield null; }
                    default -> null;
                });
        A2AClientException failure = assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(
                malformed, config, "1.1", ORDINARY, List.of()));
        assertEquals(1, closes.get());
        assertTrue(failure.getMessage().contains(malformed.getClass().getName()));
    }

    @Test
    void everyRetainedDecoratorReceivesCallsAndCannotReinstateRemovedSupport() throws A2AClientException {
        AtomicInteger baseCloses = new AtomicInteger();
        AtomicInteger decoratedCloses = new AtomicInteger();
        ClientTransport base = transport("1.1", ORDINARY, baseCloses);
        ClientTransportWrapper retaining = new ClientTransportWrapper() {
            public ClientTransport wrap(ClientTransport delegate, ClientTransportConfig<?> config) {
                return (ClientTransport) Proxy.newProxyInstance(ClientTransport.class.getClassLoader(),
                        new Class<?>[] {VersionedClientTransport.class}, (proxy, method, args) -> {
                            if (method.getName().equals("close")) {
                                decoratedCloses.incrementAndGet();
                            }
                            return method.invoke(delegate, args);
                        });
            }
        };
        VersionedClientTransport retained = ClientTransportPolicy.wrap(base, config, "1.1", ORDINARY,
                List.of(retaining, retaining));
        retained.close();
        assertEquals(1, baseCloses.get());
        assertEquals(2, decoratedCloses.get());

        AtomicInteger escalationCalls = new AtomicInteger();
        ClientTransportWrapper escalating = new ClientTransportWrapper() {
            public ClientTransport wrap(ClientTransport delegate, ClientTransportConfig<?> config) {
                escalationCalls.incrementAndGet();
                return transport("1.1", ORDINARY, new AtomicInteger());
            }
        };
        assertThrows(A2AClientException.class, () -> ClientTransportPolicy.wrap(base, config, "1.1", ORDINARY,
                List.of(wrapper(transport("1.1", Set.of(), new AtomicInteger())), escalating)));
        assertEquals(0, escalationCalls.get());

        Set<ClientFeature> wrapperClaims = Set.of(ClientFeature.ORDINARY, ClientFeature.UNKNOWN_VARIANT_SKIP);
        VersionedClientTransport intersected = ClientTransportPolicy.wrap(
                transport("1.1", ORDINARY, new AtomicInteger()), config, "1.1", ORDINARY,
                List.of(wrapper(transport("1.1", wrapperClaims, new AtomicInteger()))));
        assertEquals(ORDINARY, intersected.capabilities());
    }


    @Test
    void selectedTransportForwardsNewCallsAndRetainsResultEnvelopeThroughIdentityWrapper() throws A2AClientException {
        org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence evidence =
                new org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence("task", List.of(4L), true);
        org.a2aproject.sdk.spec.Task eventTask = new org.a2aproject.sdk.spec.Task("task", "context",
                new org.a2aproject.sdk.spec.TaskStatus(org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING),
                List.of(), List.of(), java.util.Map.of());
        org.a2aproject.sdk.client.transport.spi.TransportResult<org.a2aproject.sdk.spec.EventKind> envelope =
                new org.a2aproject.sdk.client.transport.spi.TransportResult<>(eventTask, List.of(evidence));
        AtomicReference<Object[]> call = new AtomicReference<>();
        org.a2aproject.sdk.client.transport.spi.TransportCall<org.a2aproject.sdk.client.transport.spi.TransportResult<org.a2aproject.sdk.spec.EventKind>> operation =
                org.a2aproject.sdk.client.transport.spi.TransportCall.create(() -> { });
        operation.complete(envelope);
        org.a2aproject.sdk.spec.Task task = new org.a2aproject.sdk.spec.Task("task", "context",
                new org.a2aproject.sdk.spec.TaskStatus(org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING),
                List.of(), List.of(), java.util.Map.of());
        var taskEnvelope = new org.a2aproject.sdk.client.transport.spi.TransportResult<>(task, List.of(evidence));
        var listEnvelope = new org.a2aproject.sdk.client.transport.spi.TransportResult<>(
                new org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult(List.of(task)), List.of(evidence));
        var getOperation = org.a2aproject.sdk.client.transport.spi.TransportCall.<org.a2aproject.sdk.client.transport.spi.TransportResult<org.a2aproject.sdk.spec.Task>>create(() -> { });
        getOperation.complete(taskEnvelope);
        var observation = org.a2aproject.sdk.client.transport.spi.TransportObservation.create(
                org.a2aproject.sdk.client.transport.spi.SpiCallOptions.defaults(), () -> { });
        Set<ClientFeature> features = Set.of(ClientFeature.ORDINARY, ClientFeature.UNKNOWN_VARIANT_SKIP);
        ClientTransport delegate = (ClientTransport) Proxy.newProxyInstance(ClientTransport.class.getClassLoader(),
                new Class<?>[] {VersionedClientTransport.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "protocolVersion" -> "1.1";
                    case "capabilities" -> features;
                    case "sendInput", "getTaskAsync", "getTaskDecoded", "listTasksDecoded", "streamInput", "observe" -> {
                        call.set(args);
                        yield switch (method.getName()) {
                            case "sendInput" -> operation;
                            case "getTaskAsync" -> getOperation;
                            case "getTaskDecoded" -> taskEnvelope;
                            case "listTasksDecoded" -> listEnvelope;
                            default -> observation;
                        };
                    }
                    default -> null;
                });

        VersionedClientTransport selected = ClientTransportPolicy.wrap(delegate, config, "1.1", features,
                List.of(wrapper(delegate)));
        org.a2aproject.sdk.spec.SendInputParams params = org.a2aproject.sdk.spec.SendInputParams.builder()
                .message(org.a2aproject.sdk.spec.Message.builder().role(org.a2aproject.sdk.spec.Message.Role.ROLE_USER)
                        .parts(List.of(new org.a2aproject.sdk.spec.TextPart("hello"))).build())
                .build();
        org.a2aproject.sdk.client.transport.spi.SpiCallOptions options =
                new org.a2aproject.sdk.client.transport.spi.SpiCallOptions(java.time.Instant.now().plusSeconds(10), 2, 1024);
        var forwarded = selected.sendInput(params, options, null);
        assertEquals(operation, forwarded);
        assertEquals(params, call.get()[0]);
        assertEquals(options, call.get()[1]);
        assertEquals(envelope, forwarded.result().toCompletableFuture().join());
        assertEquals(envelope.taskEvidence(), forwarded.result().toCompletableFuture().join().taskEvidence());

        var taskQuery = new org.a2aproject.sdk.spec.TaskQueryParams("task");
        var listQuery = org.a2aproject.sdk.spec.ListTasksParams.builder().build();
        var taskCall = selected.getTaskAsync(taskQuery, options, null);
        assertSame(getOperation, taskCall);
        assertSame(taskEnvelope, taskCall.result().toCompletableFuture().join());
        assertSame(taskEnvelope, selected.getTaskDecoded(taskQuery, null));
        assertSame(listEnvelope, selected.listTasksDecoded(listQuery, null));
        assertSame(observation, selected.streamInput(params, options, null));
        assertSame(observation, selected.observe(new org.a2aproject.sdk.spec.ObserveTaskParams("task", 4L, null),
                options, null));
    }


    @Test
    void publicOneOneGetAndListUnwrapDecodedResultsWhenCapabilityIsSelected() throws A2AClientException {
        org.a2aproject.sdk.spec.Task task = new org.a2aproject.sdk.spec.Task("task", "context",
                new org.a2aproject.sdk.spec.TaskStatus(org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING),
                List.of(), List.of(), java.util.Map.of());
        var list = new org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult(List.of(task));
        var evidence = new org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence("task", List.of(), false);
        var getEnvelope = new org.a2aproject.sdk.client.transport.spi.TransportResult<>(task, List.of(evidence));
        var listEnvelope = new org.a2aproject.sdk.client.transport.spi.TransportResult<>(list, List.of(evidence));
        AtomicInteger decodedGets = new AtomicInteger();
        AtomicInteger decodedLists = new AtomicInteger();
        AtomicInteger legacyReads = new AtomicInteger();
        Set<ClientFeature> features = Set.of(ClientFeature.ORDINARY, ClientFeature.UNKNOWN_VARIANT_SKIP);
        ClientTransport delegate = (ClientTransport) Proxy.newProxyInstance(ClientTransport.class.getClassLoader(),
                new Class<?>[] {VersionedClientTransport.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "protocolVersion" -> "1.1";
                    case "capabilities" -> features;
                    case "getTaskDecoded" -> { decodedGets.incrementAndGet(); yield getEnvelope; }
                    case "listTasksDecoded" -> { decodedLists.incrementAndGet(); yield listEnvelope; }
                    case "getTask", "listTasks" -> { legacyReads.incrementAndGet(); yield null; }
                    default -> null;
                });
        VersionedClientTransport selected = ClientTransportPolicy.wrap(delegate, config, "1.1", features, List.of());
        Client client = new Client(ClientBuilderVersionTest.card(
                new org.a2aproject.sdk.spec.AgentInterface("JSONRPC", "http://localhost", null, "1.1")),
                new org.a2aproject.sdk.client.config.ClientConfig.Builder().setProtocolVersion("1.1").build(),
                selected, List.of(), null);

        assertSame(task, client.getTask(new org.a2aproject.sdk.spec.TaskQueryParams("task")));
        assertSame(list, client.listTasks(org.a2aproject.sdk.spec.ListTasksParams.builder().build()));
        assertEquals(1, decodedGets.get());
        assertEquals(1, decodedLists.get());
        assertEquals(0, legacyReads.get());
    }

}
