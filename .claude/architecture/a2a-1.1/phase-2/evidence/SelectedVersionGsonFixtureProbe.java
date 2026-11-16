import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.server.version.ServerVersionPolicy;
import org.a2aproject.sdk.server.version.VersionedOutputProjector;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TimelineEntry;

/** Compares explicit 1.0 projections with historical Gson .json expectations. */
public final class SelectedVersionGsonFixtureProbe {

    private static final Path FIXTURES = Path.of(
            ".claude/architecture/a2a-1.1/phase-0/fixtures");
    private static final ServerVersionPolicy V1_0 = ServerVersionPolicy.V1_0;
    private static int comparisons;
    private static final List<String> failures = new ArrayList<>();

    private SelectedVersionGsonFixtureProbe() {}

    public static void main(String[] args) throws Exception {
        compareEvent("event-artifact_update", TaskArtifactUpdateEvent.class);
        compareEvent("event-status_update", TaskStatusUpdateEvent.class);
        compareEvent("event-status_update_event_final", TaskStatusUpdateEvent.class);
        compareEvent("event-task", Task.class);
        compareTask("jsonrpc-task");
        compareTask("rest-task");
        compareJsonRpcResponse("rpc-CANCEL_TASK_TEST_RESPONSE");
        compareJsonRpcResponse("rpc-GET_TASK_TEST_RESPONSE");
        compareJsonRpcResponse("rpc-SEND_MESSAGE_TEST_RESPONSE");
        System.out.println("Gson fixture comparisons: " + (comparisons - failures.size())
                + "/" + comparisons + " passed");
        if (!failures.isEmpty()) {
            throw new AssertionError("Gson fixture mismatches: " + failures);
        }
    }

    private static void compareEvent(String id, Class<?> type) throws Exception {
        String input = Files.readString(FIXTURES.resolve("inputs/1.0/" + id + ".json"));
        StreamingEventKind event = (StreamingEventKind) JsonUtil.fromJson(input, type);
        StreamingEventKind projected = VersionedOutputProjector.projectEvent(addPhase2Fields(event), V1_0);
        compare(id, JsonParser.parseString(JsonUtil.toJson(projected)));
    }

    private static void compareTask(String id) throws Exception {
        String input = Files.readString(FIXTURES.resolve("inputs/1.0/" + id + ".json"));
        Task task = withPhase2TaskFields(JsonUtil.fromJson(input, Task.class));
        Task projected = VersionedOutputProjector.projectTask(task, V1_0);
        compare(id, JsonParser.parseString(JsonUtil.toJson(projected)));
    }

    private static void compareJsonRpcResponse(String id) throws Exception {
        String input = Files.readString(FIXTURES.resolve("inputs/1.0/" + id + ".json"));
        Object output;
        if (id.startsWith("rpc-CANCEL")) {
            CancelTaskResponse response = JsonUtil.fromJson(input, CancelTaskResponse.class);
            Task task = VersionedOutputProjector.projectTask(
                    withPhase2TaskFields(response.getResult()), V1_0);
            output = new CancelTaskResponse(response.getId(), task);
        } else if (id.startsWith("rpc-GET")) {
            GetTaskResponse response = JsonUtil.fromJson(input, GetTaskResponse.class);
            Task task = VersionedOutputProjector.projectTask(
                    withPhase2TaskFields(response.getResult()), V1_0);
            output = new GetTaskResponse(response.getId(), task);
        } else {
            SendMessageResponse response = JsonUtil.fromJson(input, SendMessageResponse.class);
            if (!(response.getResult() instanceof Task task)) {
                throw new IllegalArgumentException("Expected Task send-message result for " + id);
            }
            Task projected = VersionedOutputProjector.projectTask(withPhase2TaskFields(task), V1_0);
            output = new SendMessageResponse(response.getId(), projected);
        }
        compare(id, JsonParser.parseString(JsonUtil.toJson(output)));
    }

    private static StreamingEventKind addPhase2Fields(StreamingEventKind event) {
        if (event instanceof Task task) {
            return withPhase2TaskFields(task);
        }
        if (event instanceof TaskStatusUpdateEvent status) {
            return new TaskStatusUpdateEvent(status.taskId(), status.status(), status.contextId(),
                    status.metadata(), 19);
        }
        if (event instanceof TaskArtifactUpdateEvent update) {
            return new TaskArtifactUpdateEvent(update.taskId(), withPhase2ArtifactFields(update.artifact()),
                    update.contextId(), update.append(), update.lastChunk(), update.metadata(), 19);
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

    private static void compare(String id, JsonElement actual) throws Exception {
        JsonElement expected = JsonParser.parseString(Files.readString(
                FIXTURES.resolve("expected/1.0/" + id + ".json")));
        boolean matches = expected.equals(actual);
        System.out.println((matches ? "PASS " : "FAIL ") + id + " SHA256 " + sha256(actual.toString()));
        if (!matches) {
            failures.add(id);
            System.out.println("  expected: " + expected + "\n  actual:   " + actual);
        }
        comparisons++;
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }
}
