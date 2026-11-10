package compatibility;

import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.client.transport.spi.ClientTransportProvider;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;

/** Representative unchanged provider compiled only against the pinned pre-Phase1 jars. */
public class LegacyImplicitProvider implements ClientTransportProvider<JSONRPCTransport, JSONRPCTransportConfig> {
    public static int creates;

    public JSONRPCTransport create(JSONRPCTransportConfig config, AgentCard card, AgentInterface selected) {
        creates++;
        return new JSONRPCTransport(card);
    }

    public String getTransportProtocol() {
        return "LEGACY-IMPLICIT";
    }

    public Class<JSONRPCTransport> getTransportProtocolClass() {
        return JSONRPCTransport.class;
    }
}
