package org.a2aproject.sdk.tests.multiversion.jsonrpc;

/** The Phase 3 JSON-RPC profile for a server whose effective agent card does not advertise client-written artifacts. */
public class Phase3JSONRPCNoUserArtifactsProfile extends Phase3JSONRPCTestProfile {

    @Override
    protected boolean userArtifacts() {
        return false;
    }
}
