import java.nio.file.Files;
import java.nio.file.Path;

import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;

/** Writes original-runtime streaming JSON bodies; does not use candidate classes. */
public final class FrozenEventHttpProbe {

    private static final Path FIXTURES = Path.of(
            ".claude/architecture/a2a-1.1/phase-0/fixtures");

    private FrozenEventHttpProbe() {}

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        write(output, "event-artifact_update", TaskArtifactUpdateEvent.class);
        write(output, "event-status_update", TaskStatusUpdateEvent.class);
        write(output, "event-status_update_event_final", TaskStatusUpdateEvent.class);
        write(output, "event-task", Task.class);
    }

    private static void write(Path output, String id, Class<?> type) throws Exception {
        String input = Files.readString(FIXTURES.resolve("inputs/1.0/" + id + ".json"));
        StreamingEventKind event = (StreamingEventKind) JsonUtil.fromJson(input, type);
        Files.writeString(output.resolve(id + ".json"), JsonUtil.toJsonStreamingEvent(event));
    }
}
