package io.a2a.transport.grpc.handler;

import io.a2a.server.TransportMetadata;
import io.a2a.spec.TransportProtocol;

public class GrpcTestTransportMetadata implements TransportMetadata {
    @Override
    public String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }

}
