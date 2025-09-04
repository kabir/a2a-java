package io.a2a.server.grpc.quarkus;

import io.a2a.server.TransportMetadata;
import io.a2a.spec.TransportProtocol;

public class QuarkusGrpcTransportMetadata implements TransportMetadata {
    @Override
    public String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }
}
