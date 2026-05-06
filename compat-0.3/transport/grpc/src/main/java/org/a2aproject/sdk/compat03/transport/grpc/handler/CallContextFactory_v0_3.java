package org.a2aproject.sdk.compat03.transport.grpc.handler;

import org.a2aproject.sdk.server.ServerCallContext;
import io.grpc.stub.StreamObserver;

/**
 * Factory interface for creating ServerCallContext from gRPC StreamObserver.
 * Implementations can provide custom context creation logic.
 *
 * <p>Implementations MUST pass {@code "0.3"} as the protocol version when constructing
 * {@link ServerCallContext} so that push notification payloads are formatted correctly.</p>
 */
public interface CallContextFactory_v0_3 {
    <V> ServerCallContext create(StreamObserver<V> responseObserver);
}
