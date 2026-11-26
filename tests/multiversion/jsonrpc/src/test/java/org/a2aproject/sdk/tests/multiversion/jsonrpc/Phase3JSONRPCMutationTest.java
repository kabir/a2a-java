package org.a2aproject.sdk.tests.multiversion.jsonrpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.http.vertx.VertxA2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.compat03.conversion.Convert_v0_3_To10RequestHandler;
import org.a2aproject.sdk.compat03.spec.Artifact_v0_3;
import org.a2aproject.sdk.compat03.spec.Message_v0_3;
import org.a2aproject.sdk.compat03.spec.StreamingEventKind_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskArtifactUpdateEvent_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskIdParams_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskStatusUpdateEvent_v0_3;
import org.a2aproject.sdk.compat03.spec.Task_v0_3;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.apps.common.Phase3MutationScenarios;
import org.a2aproject.sdk.server.apps.common.Phase3OldObserver;
import org.a2aproject.sdk.server.apps.common.TestUtilsBean;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.version.VersionedOutputProjector;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.jspecify.annotations.Nullable;

/** P3-T17 JSON-RPC production slice: the shared scenarios against the real reference server. */
@QuarkusTest
@TestProfile(Phase3JSONRPCTestProfile.class)
public class Phase3JSONRPCMutationTest extends Phase3MutationScenarios {

    @Inject
    A2ATestRoutes ingress;

    @Inject
    TestUtilsBean testUtils;

    @Inject
    Vertx vertx;

    @Inject
    RequestHandler requestHandler;

    @Inject
    Convert_v0_3_To10RequestHandler convert03;

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
    protected List<String> drainIngress() {
        return ingress.drainProtocolVersions();
    }

    @Override
    protected void commitWithoutPublication(Task task) {
        testUtils.saveTask(task);
    }

    @Override
    protected @Nullable Task storedTask(String taskId) {
        return testUtils.getTask(taskId);
    }

    @Override
    protected void deleteStoredTask(String taskId) {
        testUtils.deleteTask(taskId);
    }

    @Override
    protected int childQueueCount(String taskId) {
        return testUtils.getChildQueueCount(taskId);
    }

    @Override
    protected int pushConfigCount(String taskId) {
        return testUtils.pushConfigCount(taskId);
    }

    @Override
    protected void send03Message(String taskId, String contextId, String text) throws Exception {
        Public03ClientSupport.sendToTask(transportUrl(), taskId, contextId, text);
    }

    /**
     * The reference HTTP edge subscribes with unbounded demand, so a finite demand is applied to the very publisher
     * the edge subscribes to: the request handler's subscription stream through the same version projection the
     * JSON-RPC handler applies for 1.0 ({@code JSONRPCHandler.onSubscribeToTask}), and the 0.3 conversion handler
     * the 0.3 JSON-RPC handler delegates to ({@code JSONRPCHandler_v0_3.onResubscribeToTask}).
     */
    @Override
    protected Phase3OldObserver<?> observeUnderFiniteDemand(String version, String taskId) throws Exception {
        if (version.equals("1.0")) {
            Phase3OldObserver<StreamingEventKind> observer = new Phase3OldObserver<>(Phase3JSONRPCMutationTest::frame10);
            ServerCallContext context = context("1.0");
            VersionedOutputProjector.projectStream(requestHandler.onSubscribeToTask(new TaskIdParams(taskId), context),
                    context.getVersionPolicy()).subscribe(observer);
            return observer;
        }
        Phase3OldObserver<StreamingEventKind_v0_3> observer = new Phase3OldObserver<>(Phase3JSONRPCMutationTest::frame03);
        convert03.onResubscribeToTask(new TaskIdParams_v0_3(taskId), context("0.3")).subscribe(observer);
        return observer;
    }

    private static ServerCallContext context(String version) {
        HashMap<String, Object> state = new HashMap<>();
        state.put(ServerCallContext.TRANSPORT_KEY, TransportProtocol.JSONRPC);
        return new ServerCallContext(UnauthenticatedUser.INSTANCE, state, Set.of(), version);
    }

    private static Phase3OldObserver.Frame frame10(StreamingEventKind event) {
        if (event instanceof Task task) {
            return new Phase3OldObserver.Frame("task", task.generation(), null, state10(task.status().state()),
                    task.artifacts().stream().map(Artifact::artifactId).toList());
        }
        if (event instanceof TaskStatusUpdateEvent status) {
            return new Phase3OldObserver.Frame("status", status.generation(), null, state10(status.status().state()),
                    List.of());
        }
        if (event instanceof TaskArtifactUpdateEvent artifact) {
            return new Phase3OldObserver.Frame("artifact", artifact.generation(), artifact.artifact().artifactId(), null,
                    List.of());
        }
        if (event instanceof TaskMessageUpdateEvent message) {
            return new Phase3OldObserver.Frame("message", message.generation(), null, null, List.of());
        }
        return new Phase3OldObserver.Frame("other:" + event.getClass().getSimpleName(), 0, null, null, List.of());
    }

    private static @Nullable String state10(org.a2aproject.sdk.spec.TaskState state) {
        return state == org.a2aproject.sdk.spec.TaskState.TASK_STATE_COMPLETED ? "completed" : state.name();
    }

    private static Phase3OldObserver.Frame frame03(StreamingEventKind_v0_3 event) {
        if (event instanceof Task_v0_3 task) {
            return new Phase3OldObserver.Frame("task", 0, null, task.status().state().asString(),
                    task.artifacts().stream().map(Artifact_v0_3::artifactId).toList());
        }
        if (event instanceof TaskStatusUpdateEvent_v0_3 status) {
            return new Phase3OldObserver.Frame("status", 0, null, status.status().state().asString(), List.of());
        }
        if (event instanceof TaskArtifactUpdateEvent_v0_3 artifact) {
            return new Phase3OldObserver.Frame("artifact", 0, artifact.artifact().artifactId(), null, List.of());
        }
        if (event instanceof Message_v0_3) {
            return new Phase3OldObserver.Frame("message", 0, null, null, List.of());
        }
        return new Phase3OldObserver.Frame("other:" + event.getClass().getSimpleName(), 0, null, null, List.of());
    }

    @Override
    protected Phase3OldObserver.TaskRead read03Task(String taskId) throws Exception {
        Task_v0_3 task = Public03ClientSupport.getTask(transportUrl(), taskId);
        return new Phase3OldObserver.TaskRead(task.status().state().asString(), task.status().state().isFinal(),
                task.artifacts().stream().map(Artifact_v0_3::artifactId).toList(),
                task.history().stream().map(Message_v0_3::messageId).toList());
    }

    @Override
    protected void assertRawMalformedFinalArtifactRejected(String taskId, String contextId, String otherTaskId)
            throws Exception {
        for (String finalElement : List.of(
                "{\"taskId\":\"%1$s\",\"contextId\":\"%2$s\"}",
                "{\"taskId\":\"%1$s\",\"contextId\":\"%2$s\",\"artifact\":{\"artifactId\":\"\",\"parts\":[{\"text\":\"x\"}]}}",
                "{\"taskId\":\"%3$s\",\"contextId\":\"%2$s\",\"artifact\":{\"artifactId\":\"raw-other\","
                        + "\"parts\":[{\"text\":\"x\"}]}}")) {
            String body = """
                    {"jsonrpc":"2.0","id":"raw-malformed","method":"SendMessage","params":{
                      "message":{"messageId":"raw-malformed-msg","taskId":"%1$s","contextId":"%2$s","role":"ROLE_USER",
                        "parts":[{"text":"phase3:noop"}]},
                      "artifactUpdates":[
                        {"taskId":"%1$s","contextId":"%2$s","artifact":{"artifactId":"raw-ok","parts":[{"text":"ok"}]}},
                        %4$s]}}
                    """.formatted(taskId, contextId, otherTaskId, finalElement.formatted(taskId, contextId, otherTaskId));
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(transportUrl() + "/"))
                    .header("Content-Type", "application/json").header("A2A-Version", "1.1")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
            assertTrue(payload.has("error"), "a malformed final artifact element must be rejected: " + response.body());
            assertEquals(-32602, payload.getAsJsonObject("error").get("code").getAsInt(), response.body());
            assertEquals("raw-malformed", payload.get("id").getAsString(), "the rejection echoes the request id");
        }
    }
}
