import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonParser;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.version.ServerVersionPolicy;
import org.a2aproject.sdk.server.version.VersionedOutputProjector;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TimelineEntry;

/** Checks protobuf payload fixtures using WireProbe's exact printer and bytes paths. */
public final class SelectedVersionProtoFixtureProbe {

    private static final Path FIXTURES = Path.of(
            ".claude/architecture/a2a-1.1/phase-0/fixtures");
    private static int comparisons;
    private static final List<String> failures = new ArrayList<>();

    private SelectedVersionProtoFixtureProbe() {}

    public static void main(String[] args) throws Exception {
        compareTask("jsonrpc-task");
        compareTask("rest-task");
        System.out.println("Protobuf fixture comparisons: " + (comparisons - failures.size())
                + "/" + comparisons + " passed (JSON and binary checked separately)");
        if (!failures.isEmpty()) {
            throw new AssertionError("Protobuf fixture mismatches: " + failures);
        }
    }

    private static void compareTask(String id) throws Exception {
        Task task = JsonUtil.fromJson(Files.readString(FIXTURES.resolve("inputs/1.0/" + id + ".json")), Task.class);
        Task projected = VersionedOutputProjector.projectTask(withPhase2TaskFields(task),
                ServerVersionPolicy.V1_0);
        Message proto = ProtoUtils.ToProto.task(projected);

        String actualJson = JsonFormat.printer().print(proto);
        String expectedJson = Files.readString(FIXTURES.resolve("expected/1.0/" + id + ".proto.json"));
        boolean jsonMatches = JsonParser.parseString(expectedJson).equals(JsonParser.parseString(actualJson));
        report(id + ".proto.json", jsonMatches, actualJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] actualBytes = proto.toByteArray();
        byte[] expectedBytes = Files.readAllBytes(FIXTURES.resolve("expected/1.0/" + id + ".pb"));
        report(id + ".pb", java.util.Arrays.equals(expectedBytes, actualBytes), actualBytes);
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

    private static void report(String id, boolean matches, byte[] actual) throws Exception {
        comparisons++;
        System.out.println((matches ? "PASS " : "FAIL ") + id + " SHA256 " + sha256(actual));
        if (!matches) {
            failures.add(id);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
