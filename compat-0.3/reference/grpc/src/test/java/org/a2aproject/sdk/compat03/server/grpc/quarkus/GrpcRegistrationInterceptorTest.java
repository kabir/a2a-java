package org.a2aproject.sdk.compat03.server.grpc.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.compat03.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.compat03.grpc.GetTaskRequest;
import org.a2aproject.sdk.compat03.grpc.Task;
import org.a2aproject.sdk.server.FixedInstance;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.server.version.InterfaceRegistrationResolver;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

class GrpcRegistrationInterceptorTest {
    @Test
    void explicitRegistrationGatesTheOldServiceAndPreservesMissingVersionDefault() {
        for (Set<String> enabled : List.of(Set.of("1.1"), Set.of("0.3", "1.1"))) {
            for (String version : new String[] {null, "", "  ", "0.3", "1.0", "1.1"}) {
                InterfaceRegistration registration = new InterfaceRegistration(TransportProtocol.GRPC,
                        "localhost:9999", null, enabled, true);
                A2AExtensionsInterceptor_v0_3 interceptor = new A2AExtensionsInterceptor_v0_3();
                InterfaceRegistrationResolver resolver = (binding, tenant) -> registration;
                interceptor.interfaceRegistrationResolvers = new FixedInstance<>(resolver);
                RecordingCall call = new RecordingCall();
                boolean[] started = {false};
                ServerCallHandler<GetTaskRequest, Task> next = (ignoredCall, ignoredMetadata) -> {
                    started[0] = true;
                    return new ServerCall.Listener<>() {};
                };
                Metadata metadata = new Metadata();
                if (version != null) {
                    metadata.put(Metadata.Key.of(A2AHeaders.A2A_VERSION, Metadata.ASCII_STRING_MARSHALLER), version);
                }
                interceptor.interceptCall(call, metadata, next);
                boolean accepted = enabled.contains("0.3")
                        && (version == null || version.isBlank() || version.equals("0.3"));
                assertEquals(accepted, started[0]);
                if (accepted) {
                    assertNull(call.status);
                } else {
                    assertNotNull(call.status);
                    assertEquals(Status.Code.UNIMPLEMENTED, call.status.getCode());
                }
            }
        }
    }

    private static final class RecordingCall extends ServerCall<GetTaskRequest, Task> {
        private Status status;
        @Override public void request(int count) { }
        @Override public void sendHeaders(Metadata metadata) { }
        @Override public void sendMessage(Task task) { }
        @Override public void close(Status status, Metadata trailers) { this.status = status; }
        @Override public boolean isCancelled() { return false; }
        @Override public MethodDescriptor<GetTaskRequest, Task> getMethodDescriptor() {
            return A2AServiceGrpc.getGetTaskMethod();
        }
    }
}
