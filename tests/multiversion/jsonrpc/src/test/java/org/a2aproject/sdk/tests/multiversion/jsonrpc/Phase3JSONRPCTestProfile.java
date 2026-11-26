package org.a2aproject.sdk.tests.multiversion.jsonrpc;

import java.util.List;

import org.a2aproject.sdk.server.apps.common.Phase3TestProfile;

/**
 * The shared Phase 3 profile for the JSON-RPC multiversion module. A profile replaces the module's own
 * {@code quarkus.arc.selected-alternatives}, so the 0.3 test HTTP client the module selects is listed again.
 */
public class Phase3JSONRPCTestProfile extends Phase3TestProfile {

    @Override
    protected List<String> extraAlternatives() {
        return List.of("org.a2aproject.sdk.compat03.conversion.TestHttpClient_v0_3");
    }
}
