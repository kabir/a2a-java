package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.junit.jupiter.api.Test;

class ClientBuilderVersionTest {
    static AgentCard card(AgentInterface... interfaces) {
        return AgentCard.builder().name("version-test").description("exact selection")
                .version("test").capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text"))
                .skills(List.of()).supportedInterfaces(List.of(interfaces)).build();
    }

    @Test
    void defaultFiltersVersionBeforeBindingPreference() throws A2AClientException {
        AgentInterface old = new AgentInterface("JSONRPC", "http://old", null, "1.0");
        AgentInterface selected = new AgentInterface("JSONRPC", "http://new", null, "1.1");
        assertEquals(selected, Client.builder(card(old, selected)).findBestClientTransport());
    }

    @Test
    void defaultNeverFallsBackToOneZero() {
        assertThrows(A2AClientException.class, () -> Client.builder(card(
                new AgentInterface("JSONRPC", "http://old", null, "1.0"))).findBestClientTransport());
    }
    @Test
    void explicitSelectionFiltersEndpointAndTenantBeforePreference() throws A2AClientException {
        AgentInterface selected = new AgentInterface("JSONRPC", "http://chosen", "chosen", "1.0");
        ClientBuilder builder = Client.builder(card(
                new AgentInterface("JSONRPC", "http://other", "other", "1.0"),
                new AgentInterface("JSONRPC", "http://chosen", "chosen", "1.1"), selected))
                .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.0")
                        .setEndpoint("http://chosen").setTenant("chosen").build());
        assertEquals(selected, builder.findBestClientTransport());
    }

    @Test
    void configFreezesSelectedVersionAndPreservesLegacyConstant() {
        ClientConfig.Builder builder = new ClientConfig.Builder();
        ClientConfig first = builder.build();
        ClientConfig second = builder.setProtocolVersion("1.0").build();
        assertEquals("1.1", first.getProtocolVersion());
        assertEquals("1.0", second.getProtocolVersion());
        assertEquals("1.0", AgentInterface.CURRENT_PROTOCOL_VERSION);
        assertThrows(IllegalArgumentException.class, () -> builder.setProtocolVersion("0.3").build());
    }
}
