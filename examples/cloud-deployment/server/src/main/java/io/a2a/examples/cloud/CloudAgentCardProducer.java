package io.a2a.examples.cloud;

import io.a2a.server.PublicAgentCard;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.List;

/**
 * Producer for the cloud deployment example agent card.
 */
@ApplicationScoped
public class CloudAgentCardProducer {

    @ConfigProperty(name = "agent.url", defaultValue = "http://localhost:8080")
    String agentUrl;

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        return new AgentCard.Builder()
                .name("Cloud Deployment Demo Agent")
                .description("Demonstrates A2A agent deployment in Kubernetes with multi-pod replication")
                .url(agentUrl)
                .version("1.0.0")
                .capabilities(new AgentCapabilities.Builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .stateTransitionHistory(false)
                        .build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.singletonList(
                        new AgentSkill.Builder()
                                .id("echo_with_pod_info")
                                .name("Echo with Pod Information")
                                .description("Echoes messages back with information about which pod processed the request")
                                .tags(List.of("demo", "cloud", "kubernetes"))
                                .examples(List.of(
                                        "Hello",
                                        "Process this message",
                                        "Test replication"
                                ))
                                .build()
                ))
                .protocolVersion("0.3.0")
                .build();
    }
}
