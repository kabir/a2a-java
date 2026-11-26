package org.a2aproject.sdk.extras.queuemanager.replicated.tests.multiinstance.common;


import java.util.Collections;
import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Shared AgentCard factory for multi-instance replication tests.
 */
public final class MultiInstanceReplicationAgentCards {

    private MultiInstanceReplicationAgentCards() {
    }

    /**
     * The legacy profile advertises the default interface only. A node running the Phase 3 authority profile (a
     * writer epoch is configured) additionally advertises the exact 1.1 interface; the 1.1 request gate still
     * validates the topology before serving it.
     */
    private static List<AgentInterface> interfaces(String url) {
        AgentInterface legacy = new AgentInterface(TransportProtocol.JSONRPC.asString(), url);
        boolean authorityProfile = ConfigProvider.getConfig().getOptionalValue("a2a.task-store.writer-epoch",
                String.class).isPresent();
        if (!authorityProfile) {
            return List.of(legacy);
        }
        return List.of(legacy, new AgentInterface(TransportProtocol.JSONRPC.asString(), url, null, "1.1"));
    }

    /**
     * Creates an AgentCard for a test instance.
     *
     * @param instanceNumber the instance number (e.g., 1, 2)
     * @param port the port number (e.g., 8081, 8082)
     * @return the configured AgentCard
     */
    public static AgentCard createAgentCard(int instanceNumber, int port) {
        String url = "http://localhost:" + port;

        return AgentCard.builder()
                .name("Multi-Instance Test Agent " + instanceNumber)
                .description("Test agent for multi-instance replication testing - Instance " + instanceNumber)
                .supportedInterfaces(interfaces(url))
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(true)
                        .build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.singletonList(AgentSkill.builder()
                                .id("replication_test")
                                .name("Replication Test")
                                .description("Fire-and-forget agent for testing replication")
                                .tags(Collections.singletonList("test"))
                                .build()))
                .build();
    }
}
