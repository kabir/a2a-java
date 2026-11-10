package org.a2aproject.sdk.compat03.server.grpc.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.compat03.conversion.Convert_v0_3_To10RequestHandler;
import org.a2aproject.sdk.compat03.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.compat03.grpc.GetTaskRequest;
import org.a2aproject.sdk.compat03.grpc.Task;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.transport.grpc.handler.CallContextFactory_v0_3;
import org.a2aproject.sdk.compat03.transport.grpc.handler.GrpcHandler_v0_3;
import org.a2aproject.sdk.server.FixedInstance;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.server.version.InterfaceRegistrationResolver;
import org.a2aproject.sdk.server.version.ServerVersionPolicy;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GrpcRegisteredContextPropagationTest {
    @Test
    void admittedRegistrationBindingAndOriginal03ReachConvertedCoreForEveryBoundaryDefault() {
        InterfaceRegistration registration = registration();
        for (String requested : new String[] {null, "", "  ", "0.3"}) {
            for (CallContextFactory_v0_3 factory : new CallContextFactory_v0_3[] {null, factory("0.3")}) {
                Invocation result = invoke(registration, requested, factory);
                assertEquals(1, result.coreCalls.get());
                ServerCallContext context = result.coreContext.get();
                assertNotNull(context);
                assertSame(registration, context.getInterfaceRegistration());
                assertEquals(TransportProtocol.GRPC, context.getState().get(ServerCallContext.TRANSPORT_KEY));
                assertEquals("0.3", context.getRequestedProtocolVersion());
                assertEquals(ServerVersionPolicy.V0_3, context.getVersionPolicy());
                assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(result.observer.error).getCode());
                if (factory != null) {
                    assertEquals("preserved", context.getState().get("custom-state"));
                }
            }
        }
    }

    @Test
    void admittedRegistrationRejectsConflictingFactoryBeforeConvertedCoreExecution() {
        for (String version : new String[] {"1.0", "1.1"}) {
            Invocation result = invoke(registration(), "0.3", factory(version));
            assertRejectedBeforeCore(result);
        }
    }

    @Test
    void admittedRegistrationRejectsConflictingFactoryBindingBeforeConvertedCoreExecution() {
        CallContextFactory_v0_3 factory = new CallContextFactory_v0_3() {
            @Override
            public <V> ServerCallContext create(StreamObserver<V> observer) {
                ServerCallContext context = new ServerCallContext(UnauthenticatedUser.INSTANCE,
                        Map.of(ServerCallContext.TRANSPORT_KEY, TransportProtocol.HTTP_JSON), Set.of(), "0.3");
                context.attachInterfaceRegistration(new InterfaceRegistration(TransportProtocol.HTTP_JSON,
                        "https://other.test", null, Set.of("0.3"), false));
                return context;
            }
        };
        assertRejectedBeforeCore(invoke(registration(), "0.3", factory));
    }

    @Test
    void absentResolverPreservesHistoricalCustomFactoryContext() {
        Invocation result = invoke(null, "0.3", factory("1.1"));
        assertEquals(1, result.coreCalls.get());
        ServerCallContext context = result.coreContext.get();
        assertNotNull(context);
        assertEquals("1.1", context.getRequestedProtocolVersion());
        assertEquals("preserved", context.getState().get("custom-state"));
        assertNull(context.getInterfaceRegistration());
        assertNull(context.getState().get(ServerCallContext.TRANSPORT_KEY));
    }

    private static void assertRejectedBeforeCore(Invocation result) {
        assertEquals(0, result.coreCalls.get());
        assertNull(result.coreContext.get());
        assertNotNull(result.observer.error);
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(result.observer.error).getCode());
    }

    private static InterfaceRegistration registration() {
        return new InterfaceRegistration(TransportProtocol.GRPC, "localhost:9999", null,
                Set.of("0.3", "1.0", "1.1"), true);
    }

    private static CallContextFactory_v0_3 factory(String version) {
        return new CallContextFactory_v0_3() {
            @Override
            public <V> ServerCallContext create(StreamObserver<V> observer) {
                return new ServerCallContext(UnauthenticatedUser.INSTANCE,
                        Map.of("custom-state", "preserved"), Set.of(), version);
            }
        };
    }

    private static Invocation invoke(@Nullable InterfaceRegistration registration, @Nullable String version,
            @Nullable CallContextFactory_v0_3 factory) {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<ServerCallContext> context = new AtomicReference<>();
        RequestHandler core = (RequestHandler) Proxy.newProxyInstance(RequestHandler.class.getClassLoader(),
                new Class<?>[] {RequestHandler.class}, (proxy, method, arguments) -> {
                    if (!method.getName().equals("onGetTask")) {
                        throw new AssertionError("Unexpected core operation: " + method.getName());
                    }
                    calls.incrementAndGet();
                    context.set((ServerCallContext) arguments[1]);
                    throw new TaskNotFoundError();
                });
        GrpcHandler_v0_3 handler = new GrpcHandler_v0_3() {
            {
                setRequestHandler(new Convert_v0_3_To10RequestHandler(core));
            }
            @Override protected AgentCard_v0_3 getAgentCard() { throw new AssertionError("Unused card"); }
            @Override protected CallContextFactory_v0_3 getCallContextFactory() { return factory; }
            @Override protected Executor getExecutor() { return Runnable::run; }
        };
        A2AExtensionsInterceptor_v0_3 interceptor = new A2AExtensionsInterceptor_v0_3();
        if (registration != null) {
            InterfaceRegistrationResolver resolver = (binding, tenant) -> registration;
            interceptor.interfaceRegistrationResolvers = new FixedInstance<>(resolver);
        }
        RecordingObserver observer = new RecordingObserver();
        GetTaskRequest request = GetTaskRequest.newBuilder().setName("tasks/probe").build();
        ServerCallHandler<GetTaskRequest, Task> next = (call, metadata) -> new ServerCall.Listener<>() {
            @Override public void onHalfClose() { handler.getTask(request, observer); }
        };
        Metadata metadata = new Metadata();
        if (version != null) {
            metadata.put(Metadata.Key.of(A2AHeaders.A2A_VERSION, Metadata.ASCII_STRING_MARSHALLER), version);
        }
        ServerCall.Listener<GetTaskRequest> listener = interceptor.interceptCall(new RecordingCall(), metadata, next);
        listener.onHalfClose();
        return new Invocation(calls, context, observer);
    }

    private record Invocation(AtomicInteger coreCalls, AtomicReference<ServerCallContext> coreContext,
            RecordingObserver observer) { }

    private static final class RecordingObserver implements StreamObserver<Task> {
        private Throwable error;
        @Override public void onNext(Task task) { }
        @Override public void onCompleted() { }
        @Override public void onError(Throwable error) { this.error = error; }
    }

    private static final class RecordingCall extends ServerCall<GetTaskRequest, Task> {
        @Override public void request(int count) { }
        @Override public void sendHeaders(Metadata metadata) { }
        @Override public void sendMessage(Task task) { }
        @Override public void close(Status status, Metadata trailers) { }
        @Override public boolean isCancelled() { return false; }
        @Override public MethodDescriptor<GetTaskRequest, Task> getMethodDescriptor() {
            return A2AServiceGrpc.getGetTaskMethod();
        }
    }
}
