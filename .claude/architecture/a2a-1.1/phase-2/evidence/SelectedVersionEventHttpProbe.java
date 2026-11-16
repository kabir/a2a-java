import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

/** Writes selected-1.0 streaming JSON bodies for comparison with the frozen runtime. */
public final class SelectedVersionEventHttpProbe {

    private static final Path FIXTURES = Path.of(
            ".claude/architecture/a2a-1.1/phase-0/fixtures");

    private SelectedVersionEventHttpProbe() {}

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
        StreamingEventKind projected = VersionedOutputProjector.projectEvent(addPhase2Fields(event),
                ServerVersionPolicy.V1_0);
        Files.writeString(output.resolve(id + ".json"), JsonUtil.toJsonStreamingEvent(projected, "1.0"));
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
}
