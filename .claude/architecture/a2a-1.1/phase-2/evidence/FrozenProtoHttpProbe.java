import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoJsonUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.Task;

/** Writes original-runtime protobuf response bodies without comparing them to Gson fixtures. */
public final class FrozenProtoHttpProbe {

    private static final Path FIXTURES = Path.of(
            ".claude/architecture/a2a-1.1/phase-0/fixtures");

    private FrozenProtoHttpProbe() {}

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        writeRestTask(output);
        writeJsonRpcTaskResponse(output, "rpc-CANCEL_TASK_TEST_RESPONSE", false);
        writeJsonRpcTaskResponse(output, "rpc-GET_TASK_TEST_RESPONSE", false);
        writeJsonRpcTaskResponse(output, "rpc-SEND_MESSAGE_TEST_RESPONSE", true);
    }

    private static void writeRestTask(Path output) throws Exception {
        Task task = readTask("rest-task");
        String body = ProtoJsonUtils.toJson(JsonFormat.printer().alwaysPrintFieldsWithNoPresence(),
                ProtoUtils.ToProto.task(task));
        Files.writeString(output.resolve("rest-task.json"), body);
    }

    private static void writeJsonRpcTaskResponse(Path output, String id, boolean taskOrMessage) throws Exception {
        JsonObject input = JsonParser.parseString(Files.readString(
                FIXTURES.resolve("inputs/1.0/" + id + ".json"))).getAsJsonObject();
        JsonObject result = input.getAsJsonObject("result");
        if (taskOrMessage) {
            result = result.getAsJsonObject("task");
        }
        Task task = JsonUtil.fromJson(result.toString(), Task.class);
        com.google.protobuf.MessageOrBuilder proto = taskOrMessage
                ? ProtoUtils.ToProto.taskOrMessage(task)
                : ProtoUtils.ToProto.task(task);
        String body = JSONRPCUtils.toJsonRPCResultResponse(input.get("id").getAsString(), proto);
        Files.writeString(output.resolve(id + ".json"), body);
    }

    private static Task readTask(String id) throws Exception {
        return JsonUtil.fromJson(Files.readString(FIXTURES.resolve("inputs/1.0/" + id + ".json")), Task.class);
    }
}
