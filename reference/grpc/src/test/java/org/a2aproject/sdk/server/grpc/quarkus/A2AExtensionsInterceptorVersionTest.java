package org.a2aproject.sdk.server.grpc.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.GetTaskRequest;
import org.a2aproject.sdk.grpc.Task;
import org.a2aproject.sdk.transport.grpc.context.GrpcContextKeys;
import org.junit.jupiter.api.Test;

class A2AExtensionsInterceptorVersionTest {
    private static final Metadata.Key<String> VERSION = Metadata.Key.of("a2a-version", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void conflictingDuplicatesRejectBeforeDispatchForEitherHeaderOrder() {
        for (List<String> versions : List.of(List.of("1.0", "1.1"), List.of("1.1", "1.0"))) {
            RecordingCall call = new RecordingCall();
            AtomicBoolean invoked = new AtomicBoolean();
            new A2AExtensionsInterceptor().interceptCall(call, metadata(versions), (ignoredCall, headers) -> {
                invoked.set(true);
                return new ServerCall.Listener<>() { };
            });
            assertFalse(invoked.get());
            assertNotNull(call.status);
            assertEquals(Status.Code.FAILED_PRECONDITION, call.status.getCode());
        }
    }

    @Test
    void identicalDuplicatesSelectExactlyOneVersion() {
        for (String version : List.of("1.0", "1.1")) {
            RecordingCall call = new RecordingCall();
            List<String> received = new ArrayList<>();
            new A2AExtensionsInterceptor().interceptCall(call, metadata(List.of(version, version)), (ignoredCall, headers) -> {
                assertEquals(version, GrpcContextKeys.VERSION_HEADER_KEY.get());
                headers.getAll(VERSION).forEach(received::add);
                return new ServerCall.Listener<>() { };
            });
            assertNull(call.status);
            assertEquals(List.of(version), received);
        }
    }

    private static Metadata metadata(List<String> versions) {
        Metadata result = new Metadata();
        versions.forEach(version -> result.put(VERSION, version));
        return result;
    }

    private static final class RecordingCall extends ServerCall<GetTaskRequest, Task> {
        private Status status;
        @Override public void request(int count) { }
        @Override public void sendHeaders(Metadata metadata) { }
        @Override public void sendMessage(Task response) { }
        @Override public void close(Status status, Metadata trailers) { this.status = status; }
        @Override public boolean isCancelled() { return false; }
        @Override public MethodDescriptor<GetTaskRequest, Task> getMethodDescriptor() {
            return A2AServiceGrpc.getGetTaskMethod();
        }
    }
}
