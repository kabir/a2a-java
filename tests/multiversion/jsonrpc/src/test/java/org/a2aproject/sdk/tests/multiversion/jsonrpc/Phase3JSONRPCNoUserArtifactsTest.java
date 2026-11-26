package org.a2aproject.sdk.tests.multiversion.jsonrpc;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.http.vertx.VertxA2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.server.apps.common.Phase3CapabilityScenarios;
import org.a2aproject.sdk.server.apps.common.TestUtilsBean;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.jspecify.annotations.Nullable;

/** P3-T17 capability scenarios against a JSON-RPC server whose effective card has no client-artifact capability. */
@QuarkusTest
@TestProfile(Phase3JSONRPCNoUserArtifactsProfile.class)
public class Phase3JSONRPCNoUserArtifactsTest extends Phase3CapabilityScenarios {

    @Inject
    TestUtilsBean testUtils;

    @Inject
    Vertx vertx;

    @Override
    protected String transportProtocol() {
        return TransportProtocol.JSONRPC.asString();
    }

    @Override
    protected String transportUrl() {
        return "http://localhost:8081";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(JSONRPCTransport.class,
                new JSONRPCTransportConfigBuilder().httpClient(new VertxA2AHttpClient(vertx)));
    }

    @Override
    protected @Nullable Task storedTask(String taskId) {
        return testUtils.getTask(taskId);
    }

    @Override
    protected void deleteStoredTask(String taskId) {
        testUtils.deleteTask(taskId);
    }
}
