package org.a2aproject.sdk.transport.grpc.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.a2aproject.sdk.grpc.GetTaskRequest;
import org.a2aproject.sdk.grpc.SendMessageRequest;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.grpc.context.GrpcContextKeys;
import org.junit.jupiter.api.Test;

class GrpcVersionRegistrationTest {
    @Test
    void newSharedServiceRejectsMissingEmptyAndBlankMetadataEvenWhen03IsRegisteredSeparately() {
        GrpcHandler handler = handler(registration(Set.of("0.3", "1.0", "1.1")));
        for (String version : new String[] {null, "", "  "}) {
            RecordingObserver<org.a2aproject.sdk.grpc.Task> observer = new RecordingObserver<>();
            Context context = Context.current().withValue(GrpcContextKeys.VERSION_HEADER_KEY, version);
            context.run(() -> handler.getTask(GetTaskRequest.newBuilder().setId("missing").build(), observer));
            assertNotNull(observer.error);
            assertEquals(Status.Code.UNIMPLEMENTED, Status.fromThrowable(observer.error).getCode());
        }
    }

    @Test
    void capabilityFailureBeforeDispatchUsesRequested11Policy() {
        GrpcHandler handler = handler(registration(Set.of("1.0", "1.1")));
        for (String version : List.of("1.0", "1.1")) {
            RecordingObserver<org.a2aproject.sdk.grpc.StreamResponse> observer = new RecordingObserver<>();
            Context context = Context.current().withValue(GrpcContextKeys.VERSION_HEADER_KEY, version);
            context.run(() -> handler.sendStreamingMessage(SendMessageRequest.getDefaultInstance(), observer));
            assertNotNull(observer.error);
            assertEquals(version.equals("1.1") ? Status.Code.FAILED_PRECONDITION : Status.Code.UNIMPLEMENTED,
                    Status.fromThrowable(observer.error).getCode());
        }
    }

    @Test
    void explicitRegistrationSelectsVersionBeforeCheckingCapabilities() throws Exception {
        GrpcHandler handler = handler(registration(Set.of("1.0")));
        RecordingObserver<org.a2aproject.sdk.grpc.StreamResponse> observer = new RecordingObserver<>();
        Context.current().withValue(GrpcContextKeys.VERSION_HEADER_KEY, "1.1").run(() ->
                handler.sendStreamingMessage(SendMessageRequest.getDefaultInstance(), observer));
        assertNotNull(observer.error);
        com.google.rpc.Status status = StatusProto.fromThrowable(observer.error);
        assertNotNull(status);
        assertEquals("VERSION_NOT_SUPPORTED", status.getDetails(0)
                .unpack(com.google.rpc.ErrorInfo.class).getReason());
    }

    @Test
    void extendedCardAlsoSelectsProvisionedVersionBeforeCapability() throws Exception {
        for (String version : new String[] {null, "", "  ", "1.1"}) {
            GrpcHandler handler = handler(registration(Set.of("0.3", "1.0")));
            RecordingObserver<org.a2aproject.sdk.grpc.AgentCard> observer = new RecordingObserver<>();
            Context.current().withValue(GrpcContextKeys.VERSION_HEADER_KEY, version).run(() ->
                    handler.getExtendedAgentCard(org.a2aproject.sdk.grpc.GetExtendedAgentCardRequest
                            .getDefaultInstance(), observer));
            assertNotNull(observer.error);
            com.google.rpc.Status status = StatusProto.fromThrowable(observer.error);
            assertNotNull(status);
            assertEquals("VERSION_NOT_SUPPORTED", status.getDetails(0)
                    .unpack(com.google.rpc.ErrorInfo.class).getReason());
        }
    }

    private static InterfaceRegistration registration(Set<String> versions) {
        return new InterfaceRegistration(TransportProtocol.GRPC, "localhost:9999", null, versions, true);
    }

    private static GrpcHandler handler(InterfaceRegistration registration) {
        AgentCard card = AgentCard.builder().name("test").description("test").version("1")
                .supportedInterfaces(registration.supportedInterfaces())
                .capabilities(AgentCapabilities.builder().streaming(false).build())
                .defaultInputModes(List.of("text/plain")).defaultOutputModes(List.of("text/plain"))
                .skills(List.of()).build();
        RequestHandler requestHandler = mock(RequestHandler.class);
        return new GrpcHandler() {
            @Override protected RequestHandler getRequestHandler() { return requestHandler; }
            @Override protected AgentCard getAgentCard() { return card; }
            @Override protected AgentCard getExtendedAgentCard() { return card; }
            @Override protected CallContextFactory getCallContextFactory() { return null; }
            @Override protected Executor getExecutor() { return Runnable::run; }
            @Override protected InterfaceRegistration getInterfaceRegistration(String tenant) { return registration; }
        };
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private Throwable error;
        @Override public void onNext(T value) { }
        @Override public void onError(Throwable error) { this.error = error; }
        @Override public void onCompleted() { }
    }
}
