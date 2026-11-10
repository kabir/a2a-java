package org.a2aproject.sdk.extras.opentelemetry.client.propagation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
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
    void unconfiguredWrapperRetainsOriginalTransport() {
        ClientTransport delegate = mock(ClientTransport.class);
        assertSame(delegate, new OpenTelemetryClientPropagatorTransportWrapper().wrap(delegate, new ClientTransportConfig<>() { }));
    }
}
