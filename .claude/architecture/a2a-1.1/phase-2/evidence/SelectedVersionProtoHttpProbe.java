import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TimelineEntry;

/** Writes selected-1.0 protobuf HTTP response bodies for comparison to the frozen runtime. */
public final class SelectedVersionProtoHttpProbe {

    private static final Path FIXTURES = Path.of(
            ".claude/architecture/a2a-1.1/phase-0/fixtures");
    private static final ServerVersionPolicy V1_0 = ServerVersionPolicy.V1_0;

    private SelectedVersionProtoHttpProbe() {}

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        compareRestTask(output);
        compareJsonRpcTaskResponse(output, "rpc-CANCEL_TASK_TEST_RESPONSE", false);
        compareJsonRpcTaskResponse(output, "rpc-GET_TASK_TEST_RESPONSE", false);
        compareJsonRpcTaskResponse(output, "rpc-SEND_MESSAGE_TEST_RESPONSE", true);
    }

    private static void compareRestTask(Path output) throws Exception {
        Task task = readTask("rest-task");
        Task projected = VersionedOutputProjector.projectTask(withPhase2TaskFields(task), V1_0);
        String body = ProtoJsonUtils.toJsonForVersion(JsonFormat.printer().alwaysPrintFieldsWithNoPresence(),
                ProtoUtils.ToProto.task(projected), "1.0");
        Files.writeString(output.resolve("rest-task.json"), body);
    }

    private static void compareJsonRpcTaskResponse(Path output, String id, boolean taskOrMessage) throws Exception {
        JsonObject input = JsonParser.parseString(Files.readString(
                FIXTURES.resolve("inputs/1.0/" + id + ".json"))).getAsJsonObject();
        JsonObject result = input.getAsJsonObject("result");
        if (taskOrMessage) {
            result = result.getAsJsonObject("task");
        }
        Task projected = VersionedOutputProjector.projectTask(
                withPhase2TaskFields(JsonUtil.fromJson(result.toString(), Task.class)), V1_0);
        com.google.protobuf.MessageOrBuilder proto = taskOrMessage
                ? ProtoUtils.ToProto.taskOrMessage(projected)
                : ProtoUtils.ToProto.task(projected);
        String body = JSONRPCUtils.toJsonRPCResultResponse(input.get("id").getAsString(), proto, "1.0");
        Files.writeString(output.resolve(id + ".json"), body);
    }

    private static Task readTask(String id) throws Exception {
        return JsonUtil.fromJson(Files.readString(FIXTURES.resolve("inputs/1.0/" + id + ".json")), Task.class);
    }

    private static Task withPhase2TaskFields(Task task) {
        List<Artifact> artifacts = new ArrayList<>(task.artifacts().size());
        for (Artifact artifact : task.artifacts()) {
            artifacts.add(new Artifact(artifact.artifactId(), artifact.name(), artifact.description(),
                    artifact.parts(), artifact.metadata(), artifact.extensions(), 11, 19,
                    org.a2aproject.sdk.spec.Message.Role.ROLE_AGENT));
        }
        return new Task(task.id(), task.contextId(), task.status(), artifacts, task.history(), task.metadata(), 19,
                List.of(new TimelineEntry(18, new TimelineEntry.StatusValue(task.status()))));
    }
}
