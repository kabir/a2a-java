package org.a2aproject.sdk.tests.multiversion.grpc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import jakarta.enterprise.context.ApplicationScoped;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.quarkus.grpc.GlobalInterceptor;

/** Captures actual server-ingress metadata, independently of client configuration. */
@ApplicationScoped
@GlobalInterceptor
public class ProtocolVersionCapture implements ServerInterceptor {
    private static final Metadata.Key<String> VERSION = Metadata.Key.of("a2a-version", Metadata.ASCII_STRING_MARSHALLER);
    private final ConcurrentLinkedQueue<String> protocolVersions = new ConcurrentLinkedQueue<>();

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
            Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        Iterable<String> versions = headers.getAll(VERSION);
        if (versions == null) {
            protocolVersions.add("<missing>");
        } else {
            versions.forEach(protocolVersions::add);
        }
        return next.startCall(call, headers);
    }

    public List<String> drainProtocolVersions() {
        List<String> versions = new ArrayList<>();
        String version;
        while ((version = protocolVersions.poll()) != null) {
            versions.add(version);
        }
        return versions;
    }
}
