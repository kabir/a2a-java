package org.a2aproject.sdk.tests.multiversion.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.google.gson.JsonParser;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.a2aproject.sdk.server.apps.common.PairedVersionTestProfile;
import org.a2aproject.sdk.server.apps.common.TestUtilsBean;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.junit.jupiter.api.Test;

/** The native /v1 prefix is not a 0.3 tenant. */
@QuarkusTest
@TestProfile(Native03RestDefaultScopeTest.Default03Enabled.class)
public class Native03RestDefaultScopeTest {
    public static class Default03Enabled extends PairedVersionTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("test.a2a.experimental", "true", "test.a2a.versions", "0.3,1.0,1.1",
                    "test.a2a.v1-versions", "1.0");
        }
    }

    @Inject
    TestUtilsBean testUtils;

    @Test
    public void testPublic03GetUsesDefaultEndpoint() throws Exception {
        Task task = Task.builder().id("native03-default-task").contextId("native03-default-context")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build();
        testUtils.saveTask(task);
        try {
            Public03ClientSupport.assertGet("http://localhost:8081", task.id());
        } finally {
            testUtils.deleteTask(task.id());
        }
    }

    @Test
    public void testExplicit1xV1PrefixUsesTenantRegistration() throws Exception {
        Task task = Task.builder().id("native03-v1-tenant-task").contextId("native03-v1-tenant-context")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build();
        testUtils.saveTask(task);
        try {
            HttpResponse<String> accepted = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8081/v1/tasks/" + task.id()))
                    .header("A2A-Version", "1.0").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, accepted.statusCode(), accepted.body());
            assertEquals(task.id(), JsonParser.parseString(accepted.body()).getAsJsonObject().get("id").getAsString());
            HttpResponse<String> rejected = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8081/v1/tasks/" + task.id()))
                    .header("A2A-Version", "1.1").GET().build(), HttpResponse.BodyHandlers.ofString());
            RestCardProvisioningWithout03Test.assertVersionRejection(rejected);
        } finally {
            testUtils.deleteTask(task.id());
        }
    }

    @Test
    public void testFabricated03TenantAliasesAreRejected() throws Exception {
        Public03ClientSupport.assertNativeRawVersionRejection(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/phase1-6/tasks/alias-task")).GET().build());
        Public03ClientSupport.assertNativeRawVersionRejection(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/phase1-6/message:send"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Public03ClientSupport.nativeSendBody())).build());
    }

    @Test
    public void testPublic03SendUsesDefaultEndpoint() throws Exception {
        Public03ClientSupport.assertSend("http://localhost:8081");
    }
}
