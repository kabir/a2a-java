package org.a2aproject.sdk.jsonrpc.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.Test;

class Phase2ModelSerializationTest {

    private static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-01-02T03:04:05Z");

    @Test
    void taskTimelineAndArtifactGenerationRoundTripWithTypedPayloadWrappers() throws JsonProcessingException {
        Message message = message();
        TaskStatus status = new TaskStatus(TaskState.TASK_STATE_WORKING, null, TIMESTAMP);
        Artifact artifact = Artifact.builder()
                .artifactId("artifact")
                .parts(List.of(new TextPart("output", Map.of(), null)))
                .startGeneration(4)
                .endGeneration(5)
                .role(Message.Role.ROLE_AGENT)
                .build();
        Task task = Task.builder()
                .id("task")
                .contextId("context")
                .status(status)
                .artifacts(List.of(artifact))
                .generation(5)
                .timeline(List.of(
                        new TimelineEntry(4, new TimelineEntry.MessageValue(message)),
                        new TimelineEntry(5, new TimelineEntry.StatusValue(status))))
                .metadata(Map.of("generation", 17, "timeline", "metadata"))
                .build();

        JsonObject serialized = JsonParser.parseString(JsonUtil.toJson(task)).getAsJsonObject();
        assertEquals(5, serialized.get("generation").getAsLong());
        JsonObject firstEntry = serialized.getAsJsonArray("timeline").get(0).getAsJsonObject();
        assertEquals(4, firstEntry.get("generation").getAsLong());
        assertTrue(firstEntry.has("message"));
        JsonObject secondEntry = serialized.getAsJsonArray("timeline").get(1).getAsJsonObject();
        assertTrue(secondEntry.has("status"));
        JsonObject serializedArtifact = serialized.getAsJsonArray("artifacts").get(0).getAsJsonObject();
        assertEquals(4, serializedArtifact.get("startGeneration").getAsLong());
        assertEquals(5, serializedArtifact.get("endGeneration").getAsLong());
        assertEquals("ROLE_AGENT", serializedArtifact.get("role").getAsString());

        Task decoded = JsonUtil.fromJson(serialized.toString(), Task.class);
        assertEquals(task.generation(), decoded.generation());
        assertEquals(task.timeline(), decoded.timeline());
        assertEquals(task.artifacts(), decoded.artifacts());
        assertEquals(17L, decoded.metadata().get("generation"));
        assertEquals("metadata", decoded.metadata().get("timeline"));
    }

    @Test
    void messageUpdateUsesMessageUpdateWrapperAndStatusGenerationRoundTrips() throws JsonProcessingException {
        Message message = message();
        TaskMessageUpdateEvent messageUpdate = new TaskMessageUpdateEvent(
                "task", "context", message, Map.of("source", "agent"), 12);
        String wrappedMessage = JsonUtil.toJsonStreamingEvent(messageUpdate);
        JsonObject messageWrapper = JsonParser.parseString(wrappedMessage).getAsJsonObject();

        assertTrue(messageWrapper.has("messageUpdate"));
        assertEquals(12, messageWrapper.getAsJsonObject("messageUpdate").get("generation").getAsLong());
        TaskMessageUpdateEvent decodedMessage = assertInstanceOf(
                TaskMessageUpdateEvent.class,
                JsonUtil.fromJson(wrappedMessage, StreamingEventKind.class));
        assertEquals(messageUpdate, decodedMessage);

        TaskStatusUpdateEvent statusUpdate = new TaskStatusUpdateEvent(
                "task", new TaskStatus(TaskState.TASK_STATE_WORKING, null, TIMESTAMP), "context", null, 13);
        String wrappedStatus = JsonUtil.toJsonStreamingEvent(statusUpdate);
        JsonObject statusWrapper = JsonParser.parseString(wrappedStatus).getAsJsonObject();
        assertEquals(13, statusWrapper.getAsJsonObject("statusUpdate").get("generation").getAsLong());
        TaskStatusUpdateEvent decodedStatus = assertInstanceOf(
                TaskStatusUpdateEvent.class,
                JsonUtil.fromJson(wrappedStatus, StreamingEventKind.class));
        assertEquals(statusUpdate, decodedStatus);
        assertEquals(statusUpdate, JsonUtil.fromJson(JsonUtil.toJson(statusUpdate), StreamingEventKind.class));
    }

    @Test
    void unwrappedMessageUpdatePreservesItsEventIdentityAndGeneration() throws JsonProcessingException {
        TaskMessageUpdateEvent update = new TaskMessageUpdateEvent(
                "task", "context", message(), Map.of("source", "agent"), 12);

        TaskMessageUpdateEvent decoded = assertInstanceOf(
                TaskMessageUpdateEvent.class,
                JsonUtil.fromJson(JsonUtil.toJson(update), StreamingEventKind.class));

        assertEquals(update, decoded);
    }

    @Test
    void unknownTimelineKindsAreSkippedButEmptyEntriesFail() throws JsonProcessingException {
        Task task = Task.builder()
                .id("task")
                .contextId("context")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, TIMESTAMP))
                .generation(3)
                .build();
        JsonObject valid = JsonParser.parseString(JsonUtil.toJson(task)).getAsJsonObject();
        JsonObject knownMessageEntry = new JsonObject();
        knownMessageEntry.addProperty("generation", 2);
        knownMessageEntry.add("message", JsonParser.parseString(JsonUtil.toJson(message())));
        JsonObject unknownEntry = new JsonObject();
        unknownEntry.addProperty("generation", 3);
        unknownEntry.add("futureKind", new JsonObject());
        JsonArray timeline = new JsonArray();
        timeline.add(knownMessageEntry);
        timeline.add(unknownEntry);
        valid.add("timeline", timeline);

        Task decoded = JsonUtil.fromJson(valid.toString(), Task.class);

        assertEquals(List.of(new TimelineEntry(2, new TimelineEntry.MessageValue(message()))), decoded.timeline());

        JsonObject emptyKnownEntry = valid.deepCopy();
        emptyKnownEntry.add("timeline", JsonParser.parseString("[{\"generation\":3}]").getAsJsonArray());
        assertThrows(JsonProcessingException.class,
                () -> JsonUtil.fromJson(emptyKnownEntry.toString(), Task.class));
    }

    @Test
    void timelineEntryNeedsAnObjectPayloadToQualifyAsAnUnknownKind() throws JsonProcessingException {
        JsonObject task = JsonParser.parseString(JsonUtil.toJson(Task.builder()
                .id("task")
                .contextId("context")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, TIMESTAMP))
                .generation(3)
                .build())).getAsJsonObject();

        for (String malformedEntry : List.of(
                "{\"generation\":3,\"futureScalar\":true}",
                "{\"generation\":3,\"futureKind\":null}",
                "{\"generation\":3,\"futureKind\":[]}")) {
            JsonObject malformedTask = task.deepCopy();
            malformedTask.add("timeline", JsonParser.parseString("[" + malformedEntry + "]").getAsJsonArray());
            assertThrows(JsonProcessingException.class,
                    () -> JsonUtil.fromJson(malformedTask.toString(), Task.class), malformedEntry);
        }
    }

    @Test
    void timelineGenerationMustBeAnExactInt64() throws JsonProcessingException {
        JsonObject task = JsonParser.parseString(JsonUtil.toJson(Task.builder()
                .id("task")
                .contextId("context")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, TIMESTAMP))
                .generation(3)
                .build())).getAsJsonObject();
        for (String generation : List.of("1.5", "9223372036854775808")) {
            JsonObject malformed = task.deepCopy();
            malformed.add("timeline", JsonParser.parseString("[{\"generation\":" + generation
                    + ",\"message\":" + JsonUtil.toJson(message()) + "}]").getAsJsonArray());
            assertThrows(JsonProcessingException.class,
                    () -> JsonUtil.fromJson(malformed.toString(), Task.class));
        }
    }

    @Test
    void configurationVersionAndZeroValuesRoundTrip() throws JsonProcessingException {
        TaskPushNotificationConfig pushConfig = new TaskPushNotificationConfig(
                "config", "task", "https://example.test/hook", null, null, "tenant", "1.1");
        MessageSendConfiguration configuration = new MessageSendConfiguration(
                List.of("text"), 3, pushConfig, false, 0L, 0);
        MessageSendConfiguration decoded = JsonUtil.fromJson(
                JsonUtil.toJson(configuration), MessageSendConfiguration.class);

        assertEquals(configuration, decoded);
        assertEquals(0L, decoded.ifGenerationMatch());
        assertEquals(0, decoded.timelineLength());
    }

    @Test
    void unknownScalarPropertiesAreIgnoredWithoutDroppingMetadataKeys() throws JsonProcessingException {
        Task task = Task.builder()
                .id("task")
                .contextId("context")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, TIMESTAMP))
                .generation(0)
                .timeline(List.of())
                .metadata(Map.of("generation", "metadata-value", "timeline", "data-value"))
                .build();
        JsonObject json = JsonParser.parseString(JsonUtil.toJson(task)).getAsJsonObject();
        json.addProperty("futureScalar", "ignored");

        Task decoded = JsonUtil.fromJson(json.toString(), Task.class);

        assertEquals(0, decoded.generation());
        assertEquals(List.of(), decoded.timeline());
        assertEquals("metadata-value", decoded.metadata().get("generation"));
        assertEquals("data-value", decoded.metadata().get("timeline"));
    }

    @Test
    void existingTaskAndStatusEventWrappersRemainReadable() throws JsonProcessingException {
        Task task = Task.builder()
                .id("task")
                .contextId("context")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, TIMESTAMP))
                .build();
        String wrapped = JsonUtil.toJsonStreamingEvent(task);
        assertTrue(JsonParser.parseString(wrapped).getAsJsonObject().has("task"));
        assertEquals(task, JsonUtil.fromJson(wrapped, StreamingEventKind.class));
        assertEquals(task, JsonUtil.fromJson(JsonUtil.toJson(task), StreamingEventKind.class));
    }

    private static Message message() {
        return new Message(Message.Role.ROLE_USER, List.of(new TextPart("input", Map.of(), null)), "message-id", "context", "task",
                null, null, null);
    }
}
