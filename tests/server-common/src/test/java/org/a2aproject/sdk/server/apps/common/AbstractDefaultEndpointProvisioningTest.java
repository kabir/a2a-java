package org.a2aproject.sdk.server.apps.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Boots one actual default-endpoint subset; 0.3 is never represented by a tenant alias. */
public abstract class AbstractDefaultEndpointProvisioningTest {
    @Inject
    TestUtilsBean testUtils;

    @Inject
    @PublicAgentCard
    AgentCard card;

    @Inject
    @ConfigProperty(name = "test.a2a.versions")
    Set<String> versions;

    protected abstract void configureTransport(ClientBuilder builder);

    protected abstract void exercisePublic03(String endpoint, String taskId) throws Exception;

    protected abstract void assertRawVersion(String endpoint, String taskId, @Nullable String version,
            boolean accepted) throws Exception;

    @Test
    public void testDefaultEndpointProvisioning() throws Exception {
        assertEquals(versions, card.supportedInterfaces().stream().map(iface -> iface.protocolVersion()).collect(Collectors.toSet()));
        String endpoint = card.supportedInterfaces().get(0).url();
        Task task = Task.builder().id("default-provisioning-task").contextId("default-provisioning-context")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build();
        testUtils.saveTask(task);
        try {
            if (versions.contains("0.3")) {
                exercisePublic03(endpoint, task.id());
            }
            for (String version : new String[] {"1.0", "1.1"}) {
                if (versions.contains(version)) {
                    ClientBuilder builder = Client.builder(card).clientConfig(new ClientConfig.Builder()
                            .setProtocolVersion(version).setStreaming(false).build());
                    configureTransport(builder);
                    try (Client client = builder.build()) {
                        assertEquals(task.id(), client.getTask(new TaskQueryParams(task.id())).id());
                    }
                }
            }
            for (String version : new String[] {"0.3", "1.0", "1.1", "2.0"}) {
                assertRawVersion(endpoint, task.id(), version, versions.contains(version));
            }
            for (String version : new String[] {null, "", " "}) {
                assertRawVersion(endpoint, task.id(), version, versions.contains("0.3"));
            }
        } finally {
            testUtils.deleteTask(task.id());
        }
    }
}
