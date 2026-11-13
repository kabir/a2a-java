package org.a2aproject.sdk.server.version;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;

import org.a2aproject.sdk.server.util.async.ProjectingPublisher;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Pure projections from canonical 1.1 values to the selected older protocol view. */
public final class VersionedOutputProjector {

    private VersionedOutputProjector() {
    }

    public static Task projectTask(Task task, ServerVersionPolicy policy) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(policy, "policy");
        if (policy.supportsPhase2Output()) {
            return task;
        }
        List<Artifact> artifacts = task.artifacts().stream()
                .filter(VersionedOutputProjector::isAgentArtifact)
                .map(VersionedOutputProjector::withoutArtifactProvenance)
                .toList();
        return new Task(task.id(), task.contextId(), task.status(), artifacts, task.history(), task.metadata(), 0, null);
    }

    public static @Nullable StreamingEventKind projectEvent(StreamingEventKind event, ServerVersionPolicy policy) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(policy, "policy");
        if (policy.supportsPhase2Output()) {
            return event;
        }
        if (event instanceof Task task) {
            return projectTask(task, policy);
        }
        if (event instanceof TaskMessageUpdateEvent) {
            return null;
        }
        if (event instanceof TaskArtifactUpdateEvent artifactUpdate) {
            if (!isAgentArtifact(artifactUpdate.artifact())) {
                return null;
            }
            return new TaskArtifactUpdateEvent(artifactUpdate.taskId(),
                    withoutArtifactProvenance(artifactUpdate.artifact()), artifactUpdate.contextId(),
                    artifactUpdate.append(), artifactUpdate.lastChunk(), artifactUpdate.metadata(), 0);
        }
        if (event instanceof TaskStatusUpdateEvent statusUpdate) {
            return new TaskStatusUpdateEvent(statusUpdate.taskId(), statusUpdate.status(), statusUpdate.contextId(),
                    statusUpdate.metadata(), 0);
        }
        return event;
    }

    @NullMarked
    public static Flow.Publisher<StreamingEventKind> projectStream(
            Flow.Publisher<StreamingEventKind> publisher, ServerVersionPolicy policy) {
        return ProjectingPublisher.mapNotNullWithNullableProjector(publisher, event -> projectEvent(event, policy));
    }

    public static TaskPushNotificationConfig projectConfig(TaskPushNotificationConfig config,
            ServerVersionPolicy policy) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(policy, "policy");
        if (policy.supportsPhase2Output()) {
            return config;
        }
        return new TaskPushNotificationConfig(config.id(), config.taskId(), config.url(), config.token(),
                config.authentication(), config.tenant(), null);
    }

    private static boolean isAgentArtifact(Artifact artifact) {
        return artifact.role() == null || artifact.role() == Message.Role.ROLE_AGENT;
    }

    private static Artifact withoutArtifactProvenance(Artifact artifact) {
        return new Artifact(artifact.artifactId(), artifact.name(), artifact.description(), artifact.parts(),
                artifact.metadata(), artifact.extensions(), 0, 0, null);
    }
}
