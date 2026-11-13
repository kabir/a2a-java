package org.a2aproject.sdk.server.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class VersionedOutputProjectorTest {

    @Test
    void legacyTaskViewKeepsAgentAndUnspecifiedArtifactsButDropsUserAndNewState() {
        Artifact agent = artifact("agent", Message.Role.ROLE_AGENT, 4, 7);
        Artifact unspecified = artifact("unspecified", null, 0, 0);
        Artifact user = artifact("user", Message.Role.ROLE_USER, 2, 3);
        Task canonical = new Task("task", "context", new TaskStatus(TaskState.TASK_STATE_WORKING),
                List.of(agent, unspecified, user), List.of(new Message(Message.Role.ROLE_USER,
                List.of(new TextPart("history")), "history-id", "context", "task", null, null, null)),
                Map.of("generation", 9), 12, List.of());

        Task projected = VersionedOutputProjector.projectTask(canonical, ServerVersionPolicy.V1_0);

        assertEquals(List.of("agent", "unspecified"), projected.artifacts().stream().map(Artifact::artifactId).toList());
        assertEquals(0, projected.generation());
        assertNull(projected.timeline());
        assertEquals(canonical.history(), projected.history());
        assertEquals(Map.of("generation", 9), projected.metadata());
        assertEquals(4, canonical.artifacts().get(0).startGeneration());
        assertEquals(12, canonical.generation());
        assertEquals(List.of(agent, unspecified, user), canonical.artifacts());
    }

    @Test
    void onePointOneProjectionRetainsCanonicalObjectsAndUserUpdates() {
        Artifact userArtifact = artifact("user", Message.Role.ROLE_USER, 1, 2);
        Task canonical = new Task("task", "context", new TaskStatus(TaskState.TASK_STATE_WORKING),
                List.of(userArtifact), null, null, 8, List.of());
        TaskArtifactUpdateEvent update = new TaskArtifactUpdateEvent("task", userArtifact, "context", false, true,
                Map.of(), 8);
        TaskMessageUpdateEvent messageUpdate = new TaskMessageUpdateEvent("task", "context",
                new Message(Message.Role.ROLE_USER, List.of(new TextPart("new")), "new-id", "context", "task", null, null, null), null, 9);

        assertSame(canonical, VersionedOutputProjector.projectTask(canonical, ServerVersionPolicy.V1_1));
        assertSame(update, VersionedOutputProjector.projectEvent(update, ServerVersionPolicy.V1_1));
        assertSame(messageUpdate, VersionedOutputProjector.projectEvent(messageUpdate, ServerVersionPolicy.V1_1));
    }

    @Test
    void olderProjectionDropsMessageAndUserArtifactUpdatesAndClearsConfigVersion() {
        Artifact userArtifact = artifact("user", Message.Role.ROLE_USER, 1, 2);
        TaskArtifactUpdateEvent update = new TaskArtifactUpdateEvent("task", userArtifact, "context", false, true,
                null, 5);
        TaskMessageUpdateEvent messageUpdate = new TaskMessageUpdateEvent("task", "context",
                new Message(Message.Role.ROLE_AGENT, List.of(new TextPart("update")), "update-id", "context", "task", null, null, null), null, 6);
        TaskPushNotificationConfig config = new TaskPushNotificationConfig("cfg", "task", "https://example.test",
                null, null, null, "1.1");

        assertNull(VersionedOutputProjector.projectEvent(update, ServerVersionPolicy.V1_0));
        assertNull(VersionedOutputProjector.projectEvent(messageUpdate, ServerVersionPolicy.V0_3));
        assertNull(VersionedOutputProjector.projectConfig(config, ServerVersionPolicy.V1_0).protocolVersion());
        assertEquals("1.1", config.protocolVersion());
    }

    private static Artifact artifact(String id, Message.Role role, long start, long end) {
        return new Artifact(id, null, null, List.of(new TextPart(id)), Map.of(), List.of(), start, end, role);
    }
}
