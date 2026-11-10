package org.a2aproject.sdk.server.common.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.server.FixedInstance;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.server.version.InterfaceRegistrationResolver;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

class VersionRouterTest {

    @Test
    void missingHeaderDefaultsTo03() {
        RoutingContext rc = mockRc(null, null);
        assertEquals(VersionRouter.VERSION_0_3, VersionRouter.resolveVersion(rc));
    }

    @Test
    void emptyHeaderDefaultsTo03() {
        RoutingContext rc = mockRc("", null);
        assertEquals(VersionRouter.VERSION_0_3, VersionRouter.resolveVersion(rc));
    }

    @Test
    void explicitV10() {
        RoutingContext rc = mockRc("1.0", null);
        assertEquals(VersionRouter.VERSION_1_0, VersionRouter.resolveVersion(rc));
    }

    @Test
    void explicitV03() {
        RoutingContext rc = mockRc("0.3", null);
        assertEquals(VersionRouter.VERSION_0_3, VersionRouter.resolveVersion(rc));
    }

    @Test
    void queryParamFallback() {
        RoutingContext rc = mockRc(null, "1.0");
        assertEquals(VersionRouter.VERSION_1_0, VersionRouter.resolveVersion(rc));
    }

    @Test
    void headerTakesPrecedenceOverQueryParam() {
        RoutingContext rc = mockRc("0.3", "1.0");
        assertEquals(VersionRouter.VERSION_0_3, VersionRouter.resolveVersion(rc));
    }

    @Test
    void unsupportedVersionPassedThrough() {
        RoutingContext rc = mockRc("2.0", null);
        assertEquals("2.0", VersionRouter.resolveVersion(rc));
    }

    @Test
    void enabling03WithoutItsOptionalAdapterFailsConfiguration() {
        InterfaceRegistration registration = new InterfaceRegistration(TransportProtocol.JSONRPC,
                "https://example.test", null, Set.of("0.3", "1.1"), true);
        InterfaceRegistrationResolver resolver = (binding, tenant) -> registration;
        ServerCallContext context = new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of(), "1.1");
        assertThrows(IllegalStateException.class, () -> VersionRouter.attachRegistration(context,
                new FixedInstance<>(resolver), TransportProtocol.JSONRPC, null));
    }

    @Test
    void explicit11QueryAndWhitespacePreserveHeaderPrecedence() {
        assertEquals("1.1", VersionRouter.resolveVersion(mockRc("  ", " 1.1 ")));
        assertEquals("1.0", VersionRouter.resolveVersion(mockRc(" 1.0 ", "1.1")));
        assertEquals("0.3", VersionRouter.resolveVersion(mockRc("  ", "  ")));
    }

    @Test
    void defaultActivationRejectsMissingOptional03AdapterBeforeServingRequests() {
        InterfaceRegistration registration = new InterfaceRegistration(TransportProtocol.JSONRPC,
                "https://example.test", null, Set.of("0.3", "1.1"), true);
        InterfaceRegistrationResolver resolver = (binding, tenant) -> registration;
        assertThrows(IllegalStateException.class, () -> VersionRouter.validateDefaultRegistration(
                new FixedInstance<>(resolver), TransportProtocol.JSONRPC));
    }

    private RoutingContext mockRc(String headerValue, String queryParamValue) {
        RoutingContext rc = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(rc.request()).thenReturn(request);
        when(request.getHeader(A2AHeaders.A2A_VERSION)).thenReturn(headerValue);
        when(request.getParam(A2AHeaders.A2A_VERSION)).thenReturn(queryParamValue);
        return rc;
    }
}
