package io.a2a.transport.jsonrpc.handler;

import io.a2a.server.TransportMetadata;
import io.a2a.spec.TransportProtocol;

public class JSONRPCTestTransportMetadata implements TransportMetadata {
    @Override
    public TransportProtocol getTransportProtocol() {
        return TransportProtocol.JSONRPC;
    }

}
