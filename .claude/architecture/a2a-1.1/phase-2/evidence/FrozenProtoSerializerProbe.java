import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoJsonUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.Task;

/** Read-only check of the original Phase 1 protobuf JSON response formatting. */
public final class FrozenProtoSerializerProbe {

    private static final Path FIXTURES = Path.of(
            ".claude/architecture/a2a-1.1/phase-0/fixtures");

    private FrozenProtoSerializerProbe() {
    }

    public static void main(String[] args) throws Exception {
        comparePlainTask("jsonrpc-task", true);
        comparePlainTask("rest-task", false);
        compareJsonRpcTaskResponse("rpc-CANCEL_TASK_TEST_RESPONSE");
        compareJsonRpcTaskResponse("rpc-GET_TASK_TEST_RESPONSE");
        compareJsonRpcSendResponse();
    }

    private static void comparePlainTask(String id, boolean jsonRpc) throws Exception {
        Task task = JsonUtil.fromJson(Files.readString(FIXTURES.resolve("inputs/1.0/" + id + ".json")),
                Task.class);
        JsonElement expected = expected(id);
        JsonElement actual;
        if (jsonRpc) {
            actual = JsonParser.parseString(JSONRPCUtils.toJsonRPCResultResponse(
                    "frozen-probe", ProtoUtils.ToProto.task(task))).getAsJsonObject().get("result");
        } else {
            actual = JsonParser.parseString(ProtoJsonUtils.toJson(JsonFormat.printer()
                    .alwaysPrintFieldsWithNoPresence(), ProtoUtils.ToProto.task(task)));
        }
        report(id, expected.equals(actual), actual);
    }

    private static void compareJsonRpcTaskResponse(String id) throws Exception {
        JsonObject input = JsonParser.parseString(Files.readString(
                FIXTURES.resolve("inputs/1.0/" + id + ".json"))).getAsJsonObject();
        Task task = JsonUtil.fromJson(input.get("result").toString(), Task.class);
        String actual = JSONRPCUtils.toJsonRPCResultResponse(input.get("id").getAsString(),
                ProtoUtils.ToProto.task(task));
        report(id, expected(id).equals(JsonParser.parseString(actual)), JsonParser.parseString(actual));
    }

    private static void compareJsonRpcSendResponse() throws Exception {
        String id = "rpc-SEND_MESSAGE_TEST_RESPONSE";
        JsonObject input = JsonParser.parseString(Files.readString(
                FIXTURES.resolve("inputs/1.0/" + id + ".json"))).getAsJsonObject();
        Task task = JsonUtil.fromJson(input.getAsJsonObject("result").get("task").toString(), Task.class);
        String actual = JSONRPCUtils.toJsonRPCResultResponse(input.get("id").getAsString(),
                ProtoUtils.ToProto.taskOrMessage(task));
        report(id, expected(id).equals(JsonParser.parseString(actual)), JsonParser.parseString(actual));
    }

    private static JsonElement expected(String id) throws Exception {
        return JsonParser.parseString(Files.readString(FIXTURES.resolve("expected/1.0/" + id + ".json")));
    }

    private static void report(String id, boolean matched, JsonElement actual) {
        System.out.println((matched ? "MATCH " : "DIFF  ") + id);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(actual.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("SHA256 " + id + " " + HexFormat.of().formatHex(digest));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        if (!matched) {
            System.out.println("  frozen selected response: " + actual);
        }
    }
}
