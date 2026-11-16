import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoJsonUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.version.ServerVersionPolicy;
import org.a2aproject.sdk.server.version.VersionedOutputProjector;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TimelineEntry;

/**
 * Historical diagnostic retained for provenance. Its protobuf response outputs
 * were incorrectly compared with Gson .json expectations; see the corrected
 * serializer-specific probes beside this source.
 */
public final class SelectedVersionFixtureProbe {

    private static final Path FIXTURES = Path.of(
            ".claude/architecture/a2a-1.1/phase-0/fixtures");
    private static final ServerVersionPolicy V1_0 = ServerVersionPolicy.V1_0;
    private static int comparisons;
    private static final List<String> failures = new ArrayList<>();

    private SelectedVersionFixtureProbe() {
    }

    public static void main(String[] args) throws Exception {
        compareEvent("event-artifact_update", TaskArtifactUpdateEvent.class, "artifactUpdate");
        compareEvent("event-status_update", TaskStatusUpdateEvent.class, "statusUpdate");
        compareEvent("event-status_update_event_final", TaskStatusUpdateEvent.class, "statusUpdate");
        compareEvent("event-task", Task.class, "task");

        comparePlainTask("jsonrpc-task", true);
        comparePlainTask("rest-task", false);

        compareJsonRpcResponse("rpc-CANCEL_TASK_TEST_RESPONSE", false);
        compareJsonRpcResponse("rpc-GET_TASK_TEST_RESPONSE", false);
        compareJsonRpcResponse("rpc-SEND_MESSAGE_TEST_RESPONSE", true);

        if (comparisons != 9) {
            throw new AssertionError("Expected 9 comparisons, got " + comparisons);
        }
        System.out.println("Matched " + (comparisons - failures.size()) + "/" + comparisons
                + " unchanged 1.0 expectations through selected-version production encoders");
        if (!failures.isEmpty()) {
            throw new AssertionError("Selected 1.0 encoder mismatches: " + failures);
        }
    }

    private static void compareEvent(String id, Class<?> eventType, String wrapper) throws Exception {
        String input = Files.readString(FIXTURES.resolve("inputs/1.0/" + id + ".json"));
        StreamingEventKind event = (StreamingEventKind) JsonUtil.fromJson(input, eventType);
        StreamingEventKind withNewFields = addPhase2EventFields(event);
        StreamingEventKind projected = VersionedOutputProjector.projectEvent(withNewFields, V1_0);
        JsonObject encoded = JsonParser.parseString(JsonUtil.toJsonStreamingEvent(projected, "1.0"))
                .getAsJsonObject();
        assertMatches(id, encoded.get(wrapper));
    }

    private static void comparePlainTask(String id, boolean jsonRpc) throws Exception {
        String input = Files.readString(FIXTURES.resolve("inputs/1.0/" + id + ".json"));
        Task task = withPhase2TaskFields(JsonUtil.fromJson(input, Task.class));
        Task projected = VersionedOutputProjector.projectTask(task, V1_0);
        JsonElement actual;
        if (jsonRpc) {
            JsonObject response = JsonParser.parseString(JSONRPCUtils.toJsonRPCResultResponse("t7",
                    ProtoUtils.ToProto.task(projected), "1.0")).getAsJsonObject();
            actual = response.get("result");
        } else {
            String json = ProtoJsonUtils.toJsonForVersion(JsonFormat.printer()
                    .alwaysPrintFieldsWithNoPresence().omittingInsignificantWhitespace(),
                    ProtoUtils.ToProto.task(projected), "1.0");
            actual = JsonParser.parseString(json);
        }
        assertMatches(id, actual);
    }

    private static void compareJsonRpcResponse(String id, boolean taskOrMessage) throws Exception {
        JsonObject input = JsonParser.parseString(Files.readString(
                FIXTURES.resolve("inputs/1.0/" + id + ".json"))).getAsJsonObject();
        JsonElement result = input.getAsJsonObject("result");
        if (taskOrMessage) {
            result = result.getAsJsonObject().get("task");
        }
        Task task = withPhase2TaskFields(JsonUtil.fromJson(result.toString(), Task.class));
        Task projected = VersionedOutputProjector.projectTask(task, V1_0);
        Object requestId = input.get("id").getAsString();
        com.google.protobuf.MessageOrBuilder proto = taskOrMessage
                ? ProtoUtils.ToProto.taskOrMessage(projected)
                : ProtoUtils.ToProto.task(projected);
        String response = JSONRPCUtils.toJsonRPCResultResponse(
                requestId, proto, "1.0");
        assertMatches(id, JsonParser.parseString(response));
    }

    private static StreamingEventKind addPhase2EventFields(StreamingEventKind event) {
        if (event instanceof Task task) {
            return withPhase2TaskFields(task);
        }
        if (event instanceof TaskStatusUpdateEvent status) {
            return new TaskStatusUpdateEvent(status.taskId(), status.status(), status.contextId(),
                    status.metadata(), 19);
        }
        if (event instanceof TaskArtifactUpdateEvent update) {
            Artifact artifact = withPhase2ArtifactFields(update.artifact());
            return new TaskArtifactUpdateEvent(update.taskId(), artifact, update.contextId(), update.append(),
                    update.lastChunk(), update.metadata(), 19);
        }
        throw new IllegalArgumentException("Unexpected fixture event: " + event.getClass().getName());
    }

    private static Task withPhase2TaskFields(Task task) {
        List<Artifact> artifacts = new ArrayList<>(task.artifacts().size());
        for (Artifact artifact : task.artifacts()) {
            artifacts.add(withPhase2ArtifactFields(artifact));
        }
        return new Task(task.id(), task.contextId(), task.status(), artifacts, task.history(), task.metadata(), 19,
                List.of(new TimelineEntry(18, new TimelineEntry.StatusValue(task.status()))));
    }

    private static Artifact withPhase2ArtifactFields(Artifact artifact) {
        return new Artifact(artifact.artifactId(), artifact.name(), artifact.description(), artifact.parts(),
                artifact.metadata(), artifact.extensions(), 11, 19, Message.Role.ROLE_AGENT);
    }

    private static void assertMatches(String id, JsonElement actual) throws Exception {
        JsonElement expected = JsonParser.parseString(Files.readString(
                FIXTURES.resolve("expected/1.0/" + id + ".json")));
        if (!expected.equals(actual)) {
            failures.add(id);
            System.out.println("FAIL " + id + "\n  expected: " + expected + "\n  actual:   " + actual);
        } else {
            System.out.println("PASS " + id);
        }
        comparisons++;
        System.out.println("SHA256 " + id + " " + sha256(actual.toString()));
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }
}
