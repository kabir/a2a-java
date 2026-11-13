package org.a2aproject.sdk.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class Phase2ModelTest {

    private static final TaskStatus STATUS = new TaskStatus(TaskState.TASK_STATE_WORKING);

    @Test
    void legacyConstructorsKeepTheirDefaultsAndMessageAccessorNonNull() {
        Task task = new Task("task", "context", STATUS, null, null, null);
        Artifact artifact = new Artifact("artifact", null, null, List.of(new TextPart("data")), null, null);
        TaskStatusUpdateEvent status = new TaskStatusUpdateEvent("task", STATUS, "context", null);
        TaskArtifactUpdateEvent artifactUpdate = new TaskArtifactUpdateEvent(
                "task", artifact, "context", null, null, null);
        Message message = message(null, null);
        MessageSendParams send = new MessageSendParams(message, null, null);

        assertEquals(0, task.generation());
        assertNull(task.timeline());
        assertEquals(0, artifact.startGeneration());
        assertEquals(0, artifact.endGeneration());
        assertNull(artifact.role());
        assertEquals(0, status.generation());
        assertEquals(0, artifactUpdate.generation());
        assertNull(new MessageSendConfiguration(null, null, null, false).ifGenerationMatch());
        assertNull(new MessageSendConfiguration(null, null, null, false).timelineLength());
        assertNull(new TaskQueryParams("task", 0, "tenant").currentGeneration());
        assertNull(new TaskQueryParams("task", 0, "tenant").timelineLength());
        assertNull(new ListTasksParams(null, null, null, null, null, null, null, null).timelineLength());
        assertFalse(new AgentCapabilities(false, false, false, null).userArtifacts());
        TaskPushNotificationConfig pushConfig = new TaskPushNotificationConfig(
                "id", "task", "https://example.test", null, null, null, "1.1");
        assertNull(new TaskPushNotificationConfig("id", "task", "https://example.test", null, null, null)
                .protocolVersion());
        assertEquals(pushConfig, TaskPushNotificationConfig.builder(pushConfig).build());
        assertEquals(message, send.message());
        assertEquals(1, send.message().parts().size());
    }

    @Test
    void copyBuildersRetainGenerationTimelineAndOrigin() {
        List<TimelineEntry> timeline = new ArrayList<>();
        timeline.add(new TimelineEntry(7, new TimelineEntry.MessageValue(message(null, null))));
        Task task = Task.builder()
                .id("task")
                .contextId("context")
                .status(STATUS)
                .generation(7)
                .timeline(timeline)
                .build();
        Artifact artifact = Artifact.builder()
                .artifactId("artifact")
                .parts(List.of(new TextPart("data")))
                .startGeneration(6)
                .endGeneration(7)
                .role(Message.Role.ROLE_USER)
                .build();
        TaskStatusUpdateEvent status = TaskStatusUpdateEvent.builder()
                .taskId("task").status(STATUS).contextId("context").generation(8).build();
        TaskArtifactUpdateEvent artifactUpdate = TaskArtifactUpdateEvent.builder()
                .taskId("task").artifact(artifact).contextId("context").generation(9).build();

        Task taskCopy = Task.builder(task).build();
        Artifact artifactCopy = Artifact.builder(artifact).build();
        TaskStatusUpdateEvent statusCopy = TaskStatusUpdateEvent.builder(status).build();
        TaskArtifactUpdateEvent artifactUpdateCopy = TaskArtifactUpdateEvent.builder(artifactUpdate).build();

        assertEquals(7, taskCopy.generation());
        assertEquals(timeline, taskCopy.timeline());
        assertEquals(6, artifactCopy.startGeneration());
        assertEquals(7, artifactCopy.endGeneration());
        assertEquals(Message.Role.ROLE_USER, artifactCopy.role());
        assertEquals(8, statusCopy.generation());
        assertEquals(9, artifactUpdateCopy.generation());

        timeline.add(new TimelineEntry(8, new TimelineEntry.StatusValue(STATUS)));
        assertEquals(1, task.timeline().size());
        assertThrows(UnsupportedOperationException.class,
                () -> task.timeline().add(new TimelineEntry(9, new TimelineEntry.StatusValue(STATUS))));
    }

    @Test
    void zeroAndAbsentGuardsAndLengthsRemainDistinct() {
        MessageSendConfiguration absent = MessageSendConfiguration.builder().build();
        MessageSendConfiguration zero = MessageSendConfiguration.builder()
                .ifGenerationMatch(0L).timelineLength(0).build();
        TaskQueryParams queryAbsent = TaskQueryParams.builder().id("task").build();
        TaskQueryParams queryZero = TaskQueryParams.builder()
                .id("task").currentGeneration(0L).timelineLength(0).build();
        ListTasksParams listAbsent = ListTasksParams.builder().build();
        ListTasksParams listZero = ListTasksParams.builder().timelineLength(0).build();

        assertNull(absent.ifGenerationMatch());
        assertNull(absent.timelineLength());
        assertEquals(0L, zero.ifGenerationMatch());
        assertEquals(0, zero.timelineLength());
        assertNull(queryAbsent.currentGeneration());
        assertNull(queryAbsent.timelineLength());
        assertEquals(0L, queryZero.currentGeneration());
        assertEquals(0, queryZero.timelineLength());
        assertNull(listAbsent.timelineLength());
        assertEquals(0, listZero.timelineLength());

        assertThrows(IllegalArgumentException.class,
                () -> MessageSendConfiguration.builder().timelineLength(-1));
        assertThrows(IllegalArgumentException.class,
                () -> TaskQueryParams.builder().id("task").timelineLength(-1));
        assertThrows(InvalidParamsError.class,
                () -> ListTasksParams.builder().timelineLength(-1).build());
    }

    @Test
    void companionModelsDefensivelyCopyCollections() {
        List<TaskArtifactUpdateEvent> updates = new ArrayList<>(List.of(update("task", "context", 4)));
        Map<String, Object> inputMetadata = new HashMap<>(Map.of("source", "client"));
        SendInputParams input = new SendInputParams(
                message("task", "context"), updates, null, inputMetadata, null);

        updates.clear();
        inputMetadata.put("later", "mutation");

        assertEquals(1, input.artifactUpdates().size());
        assertEquals(Map.of("source", "client"), input.metadata());
        assertThrows(UnsupportedOperationException.class, () -> input.artifactUpdates().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> Objects.requireNonNull(input.metadata()).put("later", "mutation"));

        Map<String, Object> eventMetadata = new HashMap<>(Map.of("source", "agent"));
        TaskMessageUpdateEvent event = new TaskMessageUpdateEvent(
                "task", "context", message("task", "context"), eventMetadata, 5);
        eventMetadata.clear();
        assertEquals(Map.of("source", "agent"), event.metadata());
        assertThrows(UnsupportedOperationException.class,
                () -> Objects.requireNonNull(event.metadata()).clear());

        Map<String, Object> errorMetadata = new HashMap<>(Map.of("code", "conflict"));
        StreamError error = new StreamError("task", "ABORTED", "mismatch", null, errorMetadata);
        errorMetadata.clear();
        assertEquals(Map.of("code", "conflict"), error.metadata());
        assertThrows(UnsupportedOperationException.class,
                () -> Objects.requireNonNull(error.metadata()).clear());
    }

    @Test
    void sendInputSupportsMessageAndExistingTaskArtifactShapes() {
        Message opening = message(null, null);
        Message existingTaskMessage = message("task", "context");
        TaskArtifactUpdateEvent matchingUpdate = update("task", "context", 42);

        SendInputParams messageOnly = new SendInputParams(opening, List.of(), null, null, null);
        SendInputParams openingWithProvisionalUpdate = new SendInputParams(
                opening, List.of(update("", "", 43)), null, null, null);
        SendInputParams combined = new SendInputParams(
                existingTaskMessage, List.of(matchingUpdate), null, Map.of("request", "value"), null);
        SendInputParams artifactOnly = new SendInputParams(null, List.of(matchingUpdate), null, null, "tenant");
        SendInputParams artifactOnlyWithUntrustedStamp = new SendInputParams(
                null, List.of(update("task", "context", Long.MIN_VALUE)), null, null, null);

        assertEquals(opening, messageOnly.requireMessageOnly().message());
        assertEquals("", openingWithProvisionalUpdate.artifactUpdates().get(0).taskId());
        assertEquals(1, combined.artifactUpdates().size());
        assertNull(artifactOnly.message());
        assertEquals("task", artifactOnly.artifactUpdates().get(0).taskId());
        assertEquals(42, artifactOnly.artifactUpdates().get(0).generation());
        assertEquals(Long.MIN_VALUE, artifactOnlyWithUntrustedStamp.artifactUpdates().get(0).generation());
        assertEquals("tenant", artifactOnly.tenant());
        assertEquals(opening, SendInputParams.from(new MessageSendParams(opening, null, null)).message());
        assertThrows(IllegalArgumentException.class, artifactOnly::requireMessageOnly);
    }

    @Test
    void sendInputRejectsEmptyAndConflictingArtifactIdentity() {
        TaskArtifactUpdateEvent matching = update("task", "context", 0);

        assertThrows(IllegalArgumentException.class,
                () -> new SendInputParams(null, List.of(), null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SendInputParams(message("task-a", "context"), List.of(update("task-b", "context", 0)),
                        null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SendInputParams(message("task", "context-a"), List.of(update("task", "context-b", 0)),
                        null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SendInputParams(null, List.of(matching, update("other-task", "context", 0)), null, null,
                        null));
        assertThrows(IllegalArgumentException.class,
                () -> new SendInputParams(message(null, null),
                        List.of(update("task-a", "context-a", 0), update("task-b", "context-b", 0)),
                        null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SendInputParams(message(null, null),
                        List.of(update("task", "context-a", 0), update("task", "context-b", 0)),
                        null, null, null));
    }

    @Test
    void companionEventsKeepTheirDistinctKindsAndErrorsStayOutsideTaskEvents() {
        Message message = message("task", "context");
        TaskMessageUpdateEvent update = new TaskMessageUpdateEvent(
                "task", "context", message, Map.of("source", "client"), 11);
        StreamError error = new StreamError("task", "ABORTED", "generation mismatch", "ref-1", Map.of());
        SendInputParams send = new SendInputParams(message, List.of(), null, null, null);
        ObserveTaskParams observe = new ObserveTaskParams("task", 0L, "tenant");

        assertEquals("messageUpdate", update.kind());
        assertEquals(11, update.generation());
        assertInstanceOf(StreamingEventKind.class, update);
        assertInstanceOf(UpdateEvent.class, update);
        assertFalse((Object) error instanceof StreamingEventKind);
        assertEquals(0L, observe.lastSeenGeneration());
        assertInstanceOf(StreamRequest.Send.class, new StreamRequest(null, new StreamRequest.Send(send)).payload());
        assertInstanceOf(StreamRequest.Subscribe.class,
                new StreamRequest("tenant", new StreamRequest.Subscribe(observe)).payload());
    }

    private static Message message(String taskId, String contextId) {
        return new Message(Message.Role.ROLE_USER, List.of(new TextPart("input")), "message-id", contextId, taskId,
                null, null, null);
    }

    private static TaskArtifactUpdateEvent update(String taskId, String contextId, long generation) {
        Artifact artifact = new Artifact("artifact", null, null, List.of(new TextPart("data")), null, null);
        return new TaskArtifactUpdateEvent(taskId, artifact, contextId, null, null, null, generation);
    }
}
