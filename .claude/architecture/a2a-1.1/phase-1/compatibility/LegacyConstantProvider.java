package compatibility;

import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.client.transport.spi.ClientTransportProvider;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;

/** Representative unchanged provider compiled only against the pinned pre-Phase1 jars. */
public class LegacyConstantProvider implements ClientTransportProvider<JSONRPCTransport, JSONRPCTransportConfig> {
    public static int creates;

    public JSONRPCTransport create(JSONRPCTransportConfig config, AgentCard card, AgentInterface selected) {
        creates++;
        return new JSONRPCTransport(config.getHttpClient(), card,
                new AgentInterface("JSONRPC", selected.url(), selected.tenant(), AgentInterface.CURRENT_PROTOCOL_VERSION),
                config.getInterceptors());
    }

    public String getTransportProtocol() {
        return "LEGACY-CONSTANT";
    }

    public Class<JSONRPCTransport> getTransportProtocolClass() {
        return JSONRPCTransport.class;
    }
}
