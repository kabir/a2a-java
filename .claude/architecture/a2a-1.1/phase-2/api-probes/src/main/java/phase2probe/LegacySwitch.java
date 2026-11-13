package phase2probe;

import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.UpdateEvent;

public final class LegacySwitch {
    private LegacySwitch() {
    }

    public static String update(UpdateEvent event) {
        return switch (event) {
            case TaskStatusUpdateEvent status -> status.kind();
            case TaskArtifactUpdateEvent artifact -> artifact.kind();
        };
    }

    public static String streaming(StreamingEventKind event) {
        return switch (event) {
            case Task task -> task.kind();
            case org.a2aproject.sdk.spec.Message message -> message.kind();
            case TaskStatusUpdateEvent status -> status.kind();
            case TaskArtifactUpdateEvent artifact -> artifact.kind();
        };
    }
}
