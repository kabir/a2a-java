package org.a2aproject.sdk.extras.opentelemetry.client.propagation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportCall;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.ObserveTaskParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class Phase2PropagationTest {

    @Test
    void phase2UnaryCallsInjectTraceContextAndPreserveAuthAndDecodeEvidence() {
        VersionedClientTransport delegate = mock(VersionedClientTransport.class);
        when(delegate.protocolVersion()).thenReturn("1.1");
        OpenTelemetry telemetry = OpenTelemetry.propagating(
                ContextPropagators.create(W3CTraceContextPropagator.getInstance()));
        OpenTelemetryClientPropagatorTransport transport = new OpenTelemetryClientPropagatorTransport(delegate, telemetry);
        ClientCallContext caller = new ClientCallContext(Map.of("scope", "tenant:acme"),
                Map.of("Authorization", "Bearer secret", "A2A-Version", "1.1"));
        SpiCallOptions options = new SpiCallOptions(Instant.now().plusSeconds(5), 8, 2048);
        SendInputParams input = new SendInputParams(
                Message.builder().messageId("input").role(Message.Role.ROLE_USER)
                        .parts(List.of(new TextPart("hello"))).build(),
                List.of(), null, null, "acme");
        TaskQueryParams query = new TaskQueryParams("task", null, "acme");
        AtomicReference<ClientCallContext> sendContext = new AtomicReference<>();
        AtomicReference<ClientCallContext> streamContext = new AtomicReference<>();
        AtomicReference<ClientCallContext> observeContext = new AtomicReference<>();
        AtomicReference<ClientCallContext> asyncGetContext = new AtomicReference<>();
        AtomicReference<ClientCallContext> getContext = new AtomicReference<>();
        TransportCall<TransportResult<EventKind>> delegateCall = TransportCall.create(() -> { });
        when(delegate.sendInput(eq(input), eq(options), any(ClientCallContext.class))).thenAnswer(invocation -> {
            sendContext.set(invocation.getArgument(2));
            return delegateCall;
        });
        TransportObservation streamObservation = TransportObservation.create(options, () -> { });
        TransportObservation taskObservation = TransportObservation.create(options, () -> { });
        ObserveTaskParams observeParams = new ObserveTaskParams("task", 2L, "acme");
        when(delegate.streamInput(eq(input), eq(options), any(ClientCallContext.class))).thenAnswer(invocation -> {
            streamContext.set(invocation.getArgument(2));
            return streamObservation;
        });
        when(delegate.observe(eq(observeParams), eq(options), any(ClientCallContext.class))).thenAnswer(invocation -> {
            observeContext.set(invocation.getArgument(2));
            return taskObservation;
        });
        Task task = new Task("task", "context", new TaskStatus(TaskState.TASK_STATE_WORKING), null, null, null);
        TransportResult<Task> taskEnvelope = new TransportResult<>(task,
                List.of(new TaskDecodeEvidence("task", List.of(2L, 5L), true)));
        TransportCall<TransportResult<Task>> delegateGetCall = TransportCall.create(() -> { });
        when(delegate.getTaskAsync(eq(query), eq(options), any(ClientCallContext.class))).thenAnswer(invocation -> {
            asyncGetContext.set(invocation.getArgument(2));
            return delegateGetCall;
        });
        when(delegate.getTaskDecoded(eq(query), any(ClientCallContext.class))).thenAnswer(invocation -> {
            getContext.set(invocation.getArgument(1));
            return taskEnvelope;
        });
        ListTasksParams listParams = new ListTasksParams();
        TransportResult<ListTasksResult> listEnvelope = new TransportResult<>(new ListTasksResult(List.of()), List.of());
        AtomicReference<ClientCallContext> listContext = new AtomicReference<>();
        when(delegate.listTasksDecoded(eq(listParams), any(ClientCallContext.class))).thenAnswer(invocation -> {
            listContext.set(invocation.getArgument(1));
            return listEnvelope;
        });

        SpanContext parent = SpanContext.create("0123456789abcdef0123456789abcdef", "0123456789abcdef",
                TraceFlags.getSampled(), TraceState.getDefault());
        String expectedTraceparent = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
        TransportCall<TransportResult<EventKind>> wrappedCall;
        TransportObservation wrappedStream;
        TransportObservation wrappedObservation;
        TransportCall<TransportResult<Task>> wrappedGetCall;
        TransportResult<Task> returnedTaskEnvelope;
        TransportResult<ListTasksResult> returnedListEnvelope;
        try (Scope ignored = Span.wrap(parent).makeCurrent()) {
            wrappedCall = transport.sendInput(input, options, caller);
            wrappedStream = transport.streamInput(input, options, caller);
            wrappedObservation = transport.observe(observeParams, options, caller);
            wrappedGetCall = transport.getTaskAsync(query, options, caller);
            returnedTaskEnvelope = transport.getTaskDecoded(query, caller);
            returnedListEnvelope = transport.listTasksDecoded(listParams, caller);
        }

        TransportResult<EventKind> sendEnvelope = new TransportResult<>(
                Message.builder().messageId("response").role(Message.Role.ROLE_AGENT)
                        .parts(List.of(new TextPart("done"))).build(), List.of());
        delegateCall.complete(sendEnvelope);
        delegateGetCall.complete(taskEnvelope);
        assertSame(sendEnvelope, wrappedCall.result().toCompletableFuture().join());
        assertSame(taskEnvelope, wrappedGetCall.result().toCompletableFuture().join());
        assertSame(taskEnvelope, returnedTaskEnvelope);
        assertSame(listEnvelope, returnedListEnvelope);
        assertCallContext(sendContext.get(), caller, expectedTraceparent);
        assertCallContext(streamContext.get(), caller, expectedTraceparent);
        assertCallContext(observeContext.get(), caller, expectedTraceparent);
        assertCallContext(asyncGetContext.get(), caller, expectedTraceparent);
        assertCallContext(getContext.get(), caller, expectedTraceparent);
        assertCallContext(listContext.get(), caller, expectedTraceparent);
        assertSame(streamObservation, wrappedStream);
        assertSame(taskObservation, wrappedObservation);
        wrappedStream.cancel();
        wrappedObservation.cancel();
        assertEquals("Bearer secret", caller.getHeaders().get("Authorization"));
        assertNull(caller.getHeaders().get("traceparent"));
    }

    @Test
    void phase2DecodedListPreservesItsEvidenceEnvelope() {
        VersionedClientTransport delegate = mock(VersionedClientTransport.class);
        when(delegate.protocolVersion()).thenReturn("1.1");
        OpenTelemetryClientPropagatorTransport transport = new OpenTelemetryClientPropagatorTransport(
                delegate, OpenTelemetry.noop());
        ListTasksResult value = new ListTasksResult(List.of());
        TransportResult<ListTasksResult> envelope = new TransportResult<>(value, List.of());
        when(delegate.listTasksDecoded(any(), any())).thenReturn(envelope);

        assertSame(envelope, transport.listTasksDecoded(new org.a2aproject.sdk.spec.ListTasksParams(), null));
    }

    private static void assertCallContext(ClientCallContext forwarded, ClientCallContext caller, String traceparent) {
        assertEquals(caller.getState(), forwarded.getState());
        assertEquals("Bearer secret", forwarded.getHeaders().get("Authorization"));
        assertEquals("1.1", forwarded.getHeaders().get("A2A-Version"));
        assertEquals(traceparent, forwarded.getHeaders().get("traceparent"));
    }
}
