package org.a2aproject.sdk.extras.opentelemetry.client.propagation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportCall;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.ObserveTaskParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class VersionedWrapperTest {

    @Test
    void configuredWrapperRetainsImmutableDelegateVersionAndFeatures() {
        VersionedClientTransport delegate = mock(VersionedClientTransport.class);
        Set<ClientFeature> features = EnumSet.of(ClientFeature.ORDINARY);
        when(delegate.protocolVersion()).thenReturn("1.1");
        when(delegate.capabilities()).thenReturn(features);
        ClientTransportConfig<ClientTransport> config = new ClientTransportConfig<>() { };
        config.setParameters(Map.of(OpenTelemetryClientPropagatorTransportWrapper.OTEL_OPEN_TELEMETRY_KEY, OpenTelemetry.noop()));

        ClientTransport wrapped = new OpenTelemetryClientPropagatorTransportWrapper().wrap(delegate, config);
        VersionedClientTransport profile = assertInstanceOf(VersionedClientTransport.class, wrapped);
        when(delegate.protocolVersion()).thenReturn("1.0");
        features.clear();

        assertEquals("1.1", profile.protocolVersion());
        assertEquals(Set.of(ClientFeature.ORDINARY), profile.capabilities());
        assertThrows(UnsupportedOperationException.class, () -> profile.capabilities().clear());
    }

    @Test
    void plainLegacyDelegateRetainsOrdinaryOneZeroProfile() {
        ClientTransportConfig<ClientTransport> config = new ClientTransportConfig<>() { };
        config.setParameters(Map.of(OpenTelemetryClientPropagatorTransportWrapper.OTEL_OPEN_TELEMETRY_KEY, OpenTelemetry.noop()));
        ClientTransport wrapped = new OpenTelemetryClientPropagatorTransportWrapper().wrap(mock(ClientTransport.class), config);
        VersionedClientTransport profile = assertInstanceOf(VersionedClientTransport.class, wrapped);
        assertEquals("1.0", profile.protocolVersion());
        assertEquals(Set.of(ClientFeature.ORDINARY), profile.capabilities());
    }

    @Test
    void versionedOrdinaryCallStillInjectsTraceContextAndPreservesCallerContext() {
        VersionedClientTransport delegate = mock(VersionedClientTransport.class);
        when(delegate.protocolVersion()).thenReturn("1.1");
        when(delegate.capabilities()).thenReturn(Set.of(ClientFeature.ORDINARY));
        TaskQueryParams request = new TaskQueryParams("task");
        Task expected = new Task("task", "context", new TaskStatus(TaskState.TASK_STATE_WORKING), null, null, null);
        AtomicReference<ClientCallContext> received = new AtomicReference<>();
        when(delegate.getTask(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    received.set(invocation.getArgument(1));
                    return expected;
                });
        OpenTelemetry telemetry = OpenTelemetry.propagating(ContextPropagators.create(W3CTraceContextPropagator.getInstance()));
        OpenTelemetryClientPropagatorTransport transport = new OpenTelemetryClientPropagatorTransport(delegate, telemetry);
        ClientCallContext caller = new ClientCallContext(Map.of("scope", "tenant"), Map.of("A2A-Version", "1.1"));
        SpanContext span = SpanContext.create("0123456789abcdef0123456789abcdef", "0123456789abcdef",
                TraceFlags.getSampled(), TraceState.getDefault());
        try (Scope ignored = Span.wrap(span).makeCurrent()) {
            assertSame(expected, transport.getTask(request, caller));
        }
        assertEquals("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", received.get().getHeaders().get("traceparent"));
        assertEquals("1.1", received.get().getHeaders().get("A2A-Version"));
        assertEquals(Map.of("scope", "tenant"), received.get().getState());
        assertEquals(Map.of("A2A-Version", "1.1"), caller.getHeaders());
    }

    @Test
    void observeAndSendInputPassSkippedEvidenceThroughTheWrapperUnchanged() {
        VersionedClientTransport delegate = mock(VersionedClientTransport.class);
        when(delegate.protocolVersion()).thenReturn("1.1");
        when(delegate.capabilities()).thenReturn(Set.of(ClientFeature.ORDINARY));
        TaskStatusUpdateEvent update = new TaskStatusUpdateEvent("task",
                new TaskStatus(TaskState.TASK_STATE_WORKING), "ctx", null, 3);
        TransportFrame skipped = new TransportFrame(update, true, List.of(7L), null, 1);
        TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
        observation.markReady();
        observation.publish(skipped);
        ObserveTaskParams observe = new ObserveTaskParams("task", null, null);
        when(delegate.observe(eq(observe), any(), any())).thenReturn(observation);
        Task task = new Task("task", "ctx", new TaskStatus(TaskState.TASK_STATE_WORKING), null, null, null);
        TransportResult<EventKind> envelope = new TransportResult<>(task,
                List.of(new TaskDecodeEvidence("task", List.of(7L), true)));
        TransportCall<TransportResult<EventKind>> call = TransportCall.create(() -> { });
        call.complete(envelope);
        SendInputParams input = SendInputParams.builder().message(Message.builder().role(Message.Role.ROLE_USER)
                .parts(new TextPart("go")).build()).build();
        when(delegate.sendInput(eq(input), any(), any())).thenReturn(call);
        VersionedClientTransport wrapped = new OpenTelemetryClientPropagatorTransport(delegate, OpenTelemetry.noop());

        TransportObservation observed = wrapped.observe(observe, SpiCallOptions.defaults(), null);
        List<TransportFrame> frames = new ArrayList<>();
        observed.frames().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(TransportFrame frame) {
                frames.add(frame);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        TransportResult<EventKind> result = wrapped.sendInput(input, SpiCallOptions.defaults(), null)
                .result().toCompletableFuture().join();

        assertEquals(1, frames.size());
        assertSame(skipped, frames.get(0));
        assertEquals(List.of(7L), frames.get(0).skippedTimelineGenerations());
        assertSame(envelope, result);
        assertEquals(List.of(7L), result.taskEvidence().get(0).skippedTimelineGenerations());
    }

    @Test
    void streamInputPassesSkippedEvidenceThroughTheWrapperUnchanged() {
        VersionedClientTransport delegate = mock(VersionedClientTransport.class);
        when(delegate.protocolVersion()).thenReturn("1.1");
        when(delegate.capabilities()).thenReturn(Set.of(ClientFeature.ORDINARY));
        TaskStatusUpdateEvent update = new TaskStatusUpdateEvent("task",
                new TaskStatus(TaskState.TASK_STATE_WORKING), "ctx", null, 3);
        TransportFrame skipped = new TransportFrame(update, true, List.of(9L), null, 1);
        TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
        observation.markReady();
        observation.publish(skipped);
        SendInputParams input = SendInputParams.builder().message(Message.builder().role(Message.Role.ROLE_USER)
                .parts(new TextPart("go")).build()).build();
        when(delegate.streamInput(eq(input), any(), any())).thenReturn(observation);
        VersionedClientTransport wrapped = new OpenTelemetryClientPropagatorTransport(delegate, OpenTelemetry.noop());

        TransportObservation streamed = wrapped.streamInput(input, SpiCallOptions.defaults(), null);
        List<TransportFrame> frames = new ArrayList<>();
        streamed.frames().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(TransportFrame frame) {
                frames.add(frame);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        assertEquals(1, frames.size());
        assertSame(skipped, frames.get(0));
        assertEquals(List.of(9L), frames.get(0).skippedTimelineGenerations());
    }

    @Test
    void unconfiguredWrapperRetainsOriginalTransport() {
        ClientTransport delegate = mock(ClientTransport.class);
        assertSame(delegate, new OpenTelemetryClientPropagatorTransportWrapper().wrap(delegate, new ClientTransportConfig<>() { }));
    }
}
