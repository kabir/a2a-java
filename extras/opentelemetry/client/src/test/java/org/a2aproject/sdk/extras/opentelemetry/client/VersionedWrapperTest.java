package org.a2aproject.sdk.extras.opentelemetry.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.junit.jupiter.api.Test;

class VersionedWrapperTest {

    @Test
    void configuredWrapperRetainsImmutableDelegateVersionAndFeatures() {
        VersionedClientTransport delegate = mock(VersionedClientTransport.class);
        Set<ClientFeature> features = EnumSet.of(ClientFeature.ORDINARY);
        when(delegate.protocolVersion()).thenReturn("1.1");
        when(delegate.capabilities()).thenReturn(features);
        ClientTransportConfig<ClientTransport> config = new ClientTransportConfig<>() { };
        config.setParameters(Map.of(OpenTelemetryClientTransportWrapper.OTEL_TRACER_KEY, OpenTelemetry.noop().getTracer("test")));

        ClientTransport wrapped = new OpenTelemetryClientTransportWrapper().wrap(delegate, config);
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
        config.setParameters(Map.of(OpenTelemetryClientTransportWrapper.OTEL_TRACER_KEY, OpenTelemetry.noop().getTracer("test")));
        ClientTransport wrapped = new OpenTelemetryClientTransportWrapper().wrap(mock(ClientTransport.class), config);
        VersionedClientTransport profile = assertInstanceOf(VersionedClientTransport.class, wrapped);
        assertEquals("1.0", profile.protocolVersion());
        assertEquals(Set.of(ClientFeature.ORDINARY), profile.capabilities());
    }

    @Test
    void unconfiguredWrapperRetainsOriginalTransport() {
        ClientTransport delegate = mock(ClientTransport.class);
        assertSame(delegate, new OpenTelemetryClientTransportWrapper().wrap(delegate, new ClientTransportConfig<>() { }));
    }
}
