package org.a2aproject.sdk.jsonrpc.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

/**
 * Tests for StreamingEventKind serialization and deserialization.
 * <p>
 * Verifies that StreamingEventKind types (Task, Message, TaskStatusUpdateEvent, TaskArtifactUpdateEvent)
 * serialize using wrapper member names (e.g., {"task": {...}}) and do not contain "kind" fields.
 */
class StreamingEventKindSerializationTest {

    @Test
    void legacyVersionedSerializationOmitsOnlyTaskSchemaFields() throws Exception {
        Artifact artifact = new Artifact("a", null, null,
                List.of(new DataPart(Map.of("startGeneration", 71, "role", "payload"))),
                Map.of("startGeneration", 72, "endGeneration", 73, "role", "metadata"), List.of(),
                4, 9, Message.Role.ROLE_AGENT);
        Task task = new Task("t", "c", new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(artifact), null,
                Map.of("generation", 74, "timeline", "metadata"), 10, List.of());

        com.google.gson.JsonObject result = JsonParser.parseString(
                JsonUtil.toJsonStreamingEvent(task, "1.0")).getAsJsonObject().getAsJsonObject("task");

        assertFalse(result.has("generation"));
        assertFalse(result.has("timeline"));
        assertFalse(result.getAsJsonArray("artifacts").get(0).getAsJsonObject().has("startGeneration"));
        assertFalse(result.getAsJsonArray("artifacts").get(0).getAsJsonObject().has("endGeneration"));
        assertFalse(result.getAsJsonArray("artifacts").get(0).getAsJsonObject().has("role"));
        assertEquals(74, result.getAsJsonObject("metadata").get("generation").getAsInt());
        assertEquals(72, result.getAsJsonArray("artifacts").get(0).getAsJsonObject()
                .getAsJsonObject("metadata").get("startGeneration").getAsInt());
        assertEquals(71, result.getAsJsonArray("artifacts").get(0).getAsJsonObject()
                .getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonObject("data")
                .get("startGeneration").getAsInt());
    }

    @Test
    void onePointOneSerializationRetainsNewFieldsAndLegacyRejectsMessageUpdates() throws Exception {
        Task task = new Task("t", "c", new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(), null,
                null, 0, List.of());
        com.google.gson.JsonObject serialized = JsonParser.parseString(
                JsonUtil.toJsonStreamingEvent(task, "1.1")).getAsJsonObject().getAsJsonObject("task");

        assertEquals(0, serialized.get("generation").getAsInt());
        assertTrue(serialized.has("timeline"));
        TaskMessageUpdateEvent update = new TaskMessageUpdateEvent("t", "c",
                Message.builder().role(Message.Role.ROLE_AGENT).parts(new org.a2aproject.sdk.spec.TextPart("x"))
                        .messageId("m").build(), null, 0);
        org.junit.jupiter.api.Assertions.assertThrows(JsonProcessingException.class,
                () -> JsonUtil.toJsonStreamingEvent(update, "1.0"));
    }

    @Test
    void testTaskSerialization() throws JsonProcessingException {
        // Create a Task
        Task task = Task.builder()
                .id("task-123")
                .contextId("context-456")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                .build();

        // Serialize as StreamingEventKind
        String json = JsonUtil.toJsonStreamingEvent(task);

        // Verify JSON contains task wrapper, not "kind" field
        assertNotNull(json);
        assertTrue(json.contains("\"task\""));
        assertTrue(json.contains("\"id\":\"task-123\""));
        assertTrue(json.contains("\"state\":\"TASK_STATE_SUBMITTED\""));
        assertFalse(json.contains("\"kind\""));

        // Deserialize back to StreamingEventKind
        StreamingEventKind deserialized = JsonUtil.fromJson(json, StreamingEventKind.class);

        // Verify it's a Task
        assertInstanceOf(Task.class, deserialized);
        Task deserializedTask = (Task) deserialized;
        assertEquals(task.id(), deserializedTask.id());
        assertEquals(task.contextId(), deserializedTask.contextId());
        assertEquals(task.status().state(), deserializedTask.status().state());
    }

    @Test
    void testMessageSerialization() throws JsonProcessingException {
        // Create a Message
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Hello, agent!")))
                .taskId("task-789")
                .messageId("msg-123")
                .contextId("context-456")
                .build();

        // Serialize as StreamingEventKind
        String json = JsonUtil.toJsonStreamingEvent(message);

        // Verify JSON contains message wrapper, not "kind" field
        assertNotNull(json);
        assertTrue(json.contains("\"message\""));
        assertTrue(json.contains("\"taskId\":\"task-789\""));
        assertTrue(json.contains("\"role\":\"ROLE_USER\""));
        assertTrue(json.contains("Hello, agent!"));
        assertFalse(json.contains("\"kind\""));

        // Deserialize back to StreamingEventKind
        StreamingEventKind deserialized = JsonUtil.fromJson(json, StreamingEventKind.class);

        // Verify it's a Message
        assertInstanceOf(Message.class, deserialized);
        Message deserializedMessage = (Message) deserialized;
        assertEquals(message.taskId(), deserializedMessage.taskId());
        assertEquals(message.role(), deserializedMessage.role());
        assertEquals(message.parts().size(), deserializedMessage.parts().size());
    }

    @Test
    void testTaskStatusUpdateEventSerialization() throws JsonProcessingException {
        // Create a TaskStatusUpdateEvent
        TaskStatusUpdateEvent statusEvent = TaskStatusUpdateEvent.builder()
                .taskId("task-abc")
                .contextId("context-def")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .build();

        // Serialize as StreamingEventKind
        String json = JsonUtil.toJsonStreamingEvent(statusEvent);

        // Verify JSON contains statusUpdate wrapper, not "kind" field
        assertNotNull(json);
        assertTrue(json.contains("\"statusUpdate\""));
        assertTrue(json.contains("\"taskId\":\"task-abc\""));
        assertTrue(json.contains("\"state\":\"TASK_STATE_WORKING\""));
        assertFalse(json.contains("\"final\""));
        assertFalse(json.contains("\"kind\""));

        // Deserialize back to StreamingEventKind
        StreamingEventKind deserialized = JsonUtil.fromJson(json, StreamingEventKind.class);

        // Verify it's a TaskStatusUpdateEvent
        assertInstanceOf(TaskStatusUpdateEvent.class, deserialized);
        TaskStatusUpdateEvent deserializedEvent = (TaskStatusUpdateEvent) deserialized;
        assertEquals(statusEvent.taskId(), deserializedEvent.taskId());
        assertEquals(statusEvent.status().state(), deserializedEvent.status().state());
        assertEquals(statusEvent.isFinal(), deserializedEvent.isFinal());
    }

    @Test
    void testTaskArtifactUpdateEventSerialization() throws JsonProcessingException {
        // Create a TaskArtifactUpdateEvent
        Artifact artifact = Artifact.builder()
                .artifactId("artifact-xyz")
                .name("Test Artifact")
                .parts(List.of(new TextPart("Artifact content")))
                .build();

        TaskArtifactUpdateEvent artifactEvent = TaskArtifactUpdateEvent.builder()
                .taskId("task-123")
                .contextId("context-456")
                .artifact(artifact)
                .build();

        // Serialize as StreamingEventKind
        String json = JsonUtil.toJsonStreamingEvent(artifactEvent);

        // Verify JSON contains artifactUpdate wrapper, not "kind" field
        assertNotNull(json);
        assertTrue(json.contains("\"artifactUpdate\""));
        assertTrue(json.contains("\"taskId\":\"task-123\""));
        assertTrue(json.contains("\"artifactId\":\"artifact-xyz\""));
        assertTrue(json.contains("Artifact content"));
        assertFalse(json.contains("\"kind\""));

        // Deserialize back to StreamingEventKind
        StreamingEventKind deserialized = JsonUtil.fromJson(json, StreamingEventKind.class);

        // Verify it's a TaskArtifactUpdateEvent
        assertInstanceOf(TaskArtifactUpdateEvent.class, deserialized);
        TaskArtifactUpdateEvent deserializedEvent = (TaskArtifactUpdateEvent) deserialized;
        assertEquals(artifactEvent.taskId(), deserializedEvent.taskId());
        assertEquals(artifactEvent.artifact().artifactId(), deserializedEvent.artifact().artifactId());
    }

    @Test
    void testUnwrappedTaskDeserialization() throws JsonProcessingException {
        // Test that unwrapped Task format (direct deserialization) still works
        String json = """
            {
              "id": "task-unwrapped",
              "contextId": "context-999",
              "status": {
                "state": "TASK_STATE_COMPLETED"
              }
            }
            """;

        // Deserialize as StreamingEventKind
        StreamingEventKind deserialized = JsonUtil.fromJson(json, StreamingEventKind.class);

        // Should successfully deserialize as Task
        assertInstanceOf(Task.class, deserialized);
        Task task = (Task) deserialized;
        assertEquals("task-unwrapped", task.id());
        assertEquals("context-999", task.contextId());
        assertEquals(TaskState.TASK_STATE_COMPLETED, task.status().state());
    }

    @Test
    void testUnwrappedMessageDeserialization() throws JsonProcessingException {
        // Test that unwrapped Message format (direct deserialization) still works
        String json = """
            {
              "role": "ROLE_AGENT",
              "parts": [
                {
                  "text": "Unwrapped message"
                }
              ],
              "messageId": "msg-unwrapped",
              "taskId": "task-999"
            }
            """;

        // Deserialize as StreamingEventKind
        StreamingEventKind deserialized = JsonUtil.fromJson(json, StreamingEventKind.class);

        // Should successfully deserialize as Message
        assertInstanceOf(Message.class, deserialized);
        Message message = (Message) deserialized;
        assertEquals("msg-unwrapped", message.messageId());
        assertEquals("task-999", message.taskId());
        assertEquals(Message.Role.ROLE_AGENT, message.role());
    }

    @Test
    void testUnwrappedTaskStatusUpdateEventDeserialization() throws JsonProcessingException {
        // Test that unwrapped TaskStatusUpdateEvent format still works
        String json = """
            {
              "taskId": "task-status-unwrapped",
              "contextId": "context-999",
              "status": {
                "state": "TASK_STATE_WORKING"
              }
            }
            """;

        // Deserialize as StreamingEventKind
        StreamingEventKind deserialized = JsonUtil.fromJson(json, StreamingEventKind.class);

        // Should successfully deserialize as TaskStatusUpdateEvent
        assertInstanceOf(TaskStatusUpdateEvent.class, deserialized);
        TaskStatusUpdateEvent event = (TaskStatusUpdateEvent) deserialized;
        assertEquals("task-status-unwrapped", event.taskId());
        assertEquals(TaskState.TASK_STATE_WORKING, event.status().state());
        assertFalse(event.isFinal());
    }

    @Test
    void testUnwrappedTaskArtifactUpdateEventDeserialization() throws JsonProcessingException {
        // Test that unwrapped TaskArtifactUpdateEvent format still works
        String json = """
            {
              "taskId": "task-artifact-unwrapped",
              "contextId": "context-999",
              "artifact": {
                "artifactId": "artifact-unwrapped",
                "parts": [
                  {
                    "text": "Unwrapped artifact"
                  }
                ]
              }
            }
            """;

        // Deserialize as StreamingEventKind
        StreamingEventKind deserialized = JsonUtil.fromJson(json, StreamingEventKind.class);

        // Should successfully deserialize as TaskArtifactUpdateEvent
        assertInstanceOf(TaskArtifactUpdateEvent.class, deserialized);
        TaskArtifactUpdateEvent event = (TaskArtifactUpdateEvent) deserialized;
        assertEquals("task-artifact-unwrapped", event.taskId());
        assertEquals("artifact-unwrapped", event.artifact().artifactId());
    }
}
