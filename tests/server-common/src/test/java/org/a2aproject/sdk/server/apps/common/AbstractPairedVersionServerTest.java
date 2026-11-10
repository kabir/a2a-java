package org.a2aproject.sdk.server.apps.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.VersionNotSupportedError;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Runs public clients with different immutable versions against one server instance. */
public abstract class AbstractPairedVersionServerTest extends AbstractA2AServerTest {

    private @Nullable Client pairedStreamingClient;
    private @Nullable Client pairedUnaryClient;

    protected AbstractPairedVersionServerTest(int serverPort) {
        super(serverPort);
    }

    protected abstract List<String> drainProtocolVersions();

    protected abstract void assertRawProvisionedVersion(String tenant, @Nullable String version,
            boolean accepted) throws Exception;

    @Override
    protected Client getClient() throws A2AClientException {
        return pairedStreamingClient == null ? super.getClient() : pairedStreamingClient;
    }

    @Override
    protected Client getNonStreamingClient() throws A2AClientException {
        return pairedUnaryClient == null ? super.getNonStreamingClient() : pairedUnaryClient;
    }

    @Test
    public void testPublicClientExplicit10() throws Exception {
        exercisePublicClient("1.0", "1.0");
    }

    @Test
    public void testPublicClientExplicit11() throws Exception {
        exercisePublicClient("1.1", "1.1");
    }

    @Test
    public void testPublicClientDefault11() throws Exception {
        exercisePublicClient(null, "1.1");
    }

    private void exercisePublicClient(@Nullable String configuredVersion, String expectedVersion) throws Exception {
        try (Client streaming = createPairedClient(configuredVersion, true, interfaces(false));
                Client unary = createPairedClient(configuredVersion, false, interfaces(false))) {
            pairedStreamingClient = streaming;
            pairedUnaryClient = unary;
            try {
                assertRequestVersion(expectedVersion, this::testSendMessageNewMessageSuccess);
                assertRequestVersion(expectedVersion, this::testRequestScopedBeanAvailableOnAgentExecutorThread);
                assertRequestVersion(expectedVersion, this::testGetTaskSuccess);
                assertRequestVersion(expectedVersion, this::testListTasksSuccess);
                assertRequestVersion(expectedVersion, this::testCancelTaskSuccess);
                assertRequestVersion(expectedVersion, this::testGetPushNotificationSuccess);
                assertRequestVersion(expectedVersion, this::testListPushNotificationConfigsWithConfigId);
                assertRequestVersion(expectedVersion, this::testDeletePushNotificationConfigWithValidConfigId);
                assertRequestVersion(expectedVersion, this::testGetExtendedAgentCard);
                assertRequestVersion(expectedVersion, this::testSendMessageStreamNewMessageSuccess);
                assertRequestVersion(expectedVersion, this::testSubscribeExistingTaskSuccess);
            } finally {
                pairedStreamingClient = null;
                pairedUnaryClient = null;
            }
        }
    }

    @Test
    public void testPublicClientCardOrderDoesNotChangeVersion() throws Exception {
        saveTaskInTaskStore(MINIMAL_TASK);
        try {
            for (boolean reverse : List.of(false, true)) {
                for (String version : List.of("1.0", "1.1")) {
                    try (Client client = createPairedClient(version, false, interfaces(reverse))) {
                        assertRequestVersion(version, () -> assertEquals(MINIMAL_TASK.id(),
                                client.getTask(new TaskQueryParams(MINIMAL_TASK.id())).id()));
                    }
                }
            }
        } finally {
            deleteTaskInTaskStore(MINIMAL_TASK.id());
        }
    }

    @Test
    public void testPublicClientRejectsUnavailableVersionBeforeSending() {
        drainProtocolVersions();
        assertThrows(A2AClientException.class, () -> createPairedClient("1.1", false,
                List.of(new AgentInterface(getTransportProtocol(), getTransportUrl(), null, "1.0"))));
        assertTrue(drainProtocolVersions().isEmpty());
    }

    @Test
    public void testPublicClientSelectsEndpointAndTenantBeforeCardOrder() throws Exception {
        saveTaskInTaskStore(MINIMAL_TASK);
        String tenant = PairedVersionRegistrationResolver.SUBSET_TENANT_PREFIX + "6";
        String wrongEndpoint = getTransportProtocol().equals("GRPC") ? "localhost:1" : "http://localhost:1";
        try {
            for (String version : List.of("1.0", "1.1")) {
                AgentInterface selected = new AgentInterface(getTransportProtocol(), getTransportUrl(), tenant, version);
                AgentInterface otherEndpoint = new AgentInterface(getTransportProtocol(), wrongEndpoint, tenant, version);
                AgentInterface otherTenant = new AgentInterface(getTransportProtocol(), getTransportUrl(), "phase1-2", version);
                for (List<AgentInterface> order : List.of(List.of(otherEndpoint, otherTenant, selected),
                        List.of(otherTenant, selected, otherEndpoint), List.of(selected, otherEndpoint, otherTenant))) {
                    try (Client client = createPairedClient(version, false, order, getTransportUrl(), tenant)) {
                        assertRequestVersion(version, () -> assertEquals(MINIMAL_TASK.id(),
                                client.getTask(new TaskQueryParams(MINIMAL_TASK.id())).id()));
                    }
                }
            }
        } finally {
            deleteTaskInTaskStore(MINIMAL_TASK.id());
        }
    }

    @Test
    public void testEvery1xTenantSubsetAndMissingVersion() throws Exception {
        saveTaskInTaskStore(MINIMAL_TASK);
        try {
            for (int mask : List.of(2, 4, 6)) {
                String tenant = PairedVersionRegistrationResolver.SUBSET_TENANT_PREFIX + mask;
                assertRawProvisionedVersion(tenant, "1.0", (mask & 2) != 0);
                assertRawProvisionedVersion(tenant, "1.1", (mask & 4) != 0);
                assertRawProvisionedVersion(tenant, "0.3", false);
                for (String absentVersion : new String[] {null, "", " "}) {
                    assertRawProvisionedVersion(tenant, absentVersion, false);
                }
                assertRawProvisionedVersion(tenant, "2.0", false);
            }
        } finally {
            deleteTaskInTaskStore(MINIMAL_TASK.id());
        }
    }

    @Test
    public void testPublicClientsUseTenantRegistration() throws Exception {
        saveTaskInTaskStore(MINIMAL_TASK);
        try {
            for (int mask : List.of(2, 4, 6)) {
                String tenant = PairedVersionRegistrationResolver.SUBSET_TENANT_PREFIX + mask;
                for (String version : List.of("1.0", "1.1")) {
                    int versionBit = version.equals("1.0") ? 2 : 4;
                    if ((mask & versionBit) != 0) {
                        AgentInterface descriptor = new AgentInterface(getTransportProtocol(), getTransportUrl(), tenant, version);
                        try (Client client = createPairedClient(version, false, List.of(descriptor))) {
                            assertRequestVersion(version, () -> assertEquals(MINIMAL_TASK.id(),
                                    client.getTask(new TaskQueryParams(MINIMAL_TASK.id())).id()));
                        }
                    }
                }
            }
        } finally {
            deleteTaskInTaskStore(MINIMAL_TASK.id());
        }
    }

    @Test
    public void testStaleCardDisabledVersionReachesServerAndIsRejected() throws Exception {
        AgentInterface stale = new AgentInterface(getTransportProtocol(), getTransportUrl(),
                PairedVersionRegistrationResolver.SUBSET_TENANT_PREFIX + "2", "1.1");
        try (Client client = createPairedClient("1.1", false, List.of(stale))) {
            assertRequestVersion("1.1", () -> {
                A2AClientException error = assertThrows(A2AClientException.class,
                        () -> client.getTask(new TaskQueryParams("stale-card-task")));
                assertTrue(hasVersionNotSupportedCause(error), () -> "Expected server version rejection: " + error);
            });
        }
    }

    private boolean hasVersionNotSupportedCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof VersionNotSupportedError) {
                return true;
            }
        }
        return false;
    }

    protected Client createPairedClient(@Nullable String version, boolean streaming,
            List<AgentInterface> interfaces) throws A2AClientException {
        return createPairedClient(version, streaming, interfaces, null, null);
    }

    private Client createPairedClient(@Nullable String version, boolean streaming,
            List<AgentInterface> interfaces, @Nullable String endpoint, @Nullable String tenant) throws A2AClientException {
        AgentCard card = AgentCard.builder()
                .name("test-card")
                .description("A test agent card")
                .version("1.0")
                .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(true)
                        .extendedAgentCard(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(interfaces)
                .build();
        ClientConfig.Builder config = new ClientConfig.Builder().setStreaming(streaming).setEndpoint(endpoint).setTenant(tenant);
        if (version != null) {
            config.setProtocolVersion(version);
        }
        ClientBuilder builder = Client.builder(card).clientConfig(config.build());
        configureTransport(builder);
        return builder.build();
    }

    private List<AgentInterface> interfaces(boolean reverse) {
        AgentInterface v10 = new AgentInterface(getTransportProtocol(), getTransportUrl(), null, "1.0");
        AgentInterface v11 = new AgentInterface(getTransportProtocol(), getTransportUrl(), null, "1.1");
        return reverse ? List.of(v11, v10) : List.of(v10, v11);
    }

    private void assertRequestVersion(String expectedVersion, CheckedOperation operation) throws Exception {
        drainProtocolVersions();
        operation.run();
        List<String> versions = drainProtocolVersions();
        assertFalse(versions.isEmpty(), "The public SDK operation must reach the server");
        assertTrue(versions.stream().allMatch(expectedVersion::equals),
                () -> "Expected exactly version " + expectedVersion + " at ingress, received " + versions);
    }

    @FunctionalInterface
    private interface CheckedOperation {
        void run() throws Exception;
    }
}
