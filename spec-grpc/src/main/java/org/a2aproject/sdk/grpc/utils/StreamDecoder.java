package org.a2aproject.sdk.grpc.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.grpc.mapper.StreamResponseMapper;
import org.a2aproject.sdk.grpc.mapper.TaskMapper;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamError;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;

/** Version-aware protobuf and JSON decoding for 1.0 and the pinned 1.1 RC. */
public final class StreamDecoder {

    private static final String V1_0 = "1.0";
    private static final String V1_1 = "1.1";
    private static final Map<String, String> STREAM_VARIANTS = Map.of(
            "task", "task",
            "message", "message",
            "statusUpdate", "statusUpdate",
            "status_update", "statusUpdate",
            "artifactUpdate", "artifactUpdate",
            "artifact_update", "artifactUpdate",
            "messageUpdate", "messageUpdate",
            "message_update", "messageUpdate",
            "error", "error");

    private StreamDecoder() {
    }

    public static StreamDecodeResult decode(org.a2aproject.sdk.grpc.StreamResponse response, String protocolVersion) {
        try {
            requireVersion(protocolVersion);
            if (V1_0.equals(protocolVersion)) {
                return new StreamDecodeResult.Known(decodeLegacyEvent(response), DecodeNotes.EMPTY);
            }
            return switch (response.getPayloadCase()) {
                case TASK -> {
                    DecodedTask decoded = decodeTask(response.getTask(), protocolVersion);
                    yield new StreamDecodeResult.Known(decoded.task(), decoded.notes());
                }
                case MESSAGE -> {
                    validateMessage(response.getMessage());
                    yield new StreamDecodeResult.Known(ProtoUtils.FromProto.message(response.getMessage()), DecodeNotes.EMPTY);
                }
                case STATUS_UPDATE -> {
                    validateStatusUpdate(response.getStatusUpdate());
                    yield new StreamDecodeResult.Known(
                            ProtoUtils.FromProto.taskStatusUpdateEvent(response.getStatusUpdate()), DecodeNotes.EMPTY);
                }
                case ARTIFACT_UPDATE -> {
                    validateArtifactUpdate(response.getArtifactUpdate());
                    yield new StreamDecodeResult.Known(
                            ProtoUtils.FromProto.taskArtifactUpdateEvent(response.getArtifactUpdate()), DecodeNotes.EMPTY);
                }
                case MESSAGE_UPDATE -> {
                    org.a2aproject.sdk.grpc.TaskMessageUpdateEvent update = response.getMessageUpdate();
                    validateTaskAndContext(update.getTaskId(), update.getContextId());
                    if (!update.hasMessage()) {
                        throw new IllegalArgumentException("Message update content is required");
                    }
                    validateMessage(update.getMessage());
                    yield new StreamDecodeResult.Known(
                            org.a2aproject.sdk.grpc.mapper.TaskMessageUpdateEventMapper.INSTANCE.fromProto(update),
                            DecodeNotes.EMPTY);
                }
                case ERROR -> {
                    StreamError error = ProtoUtils.FromProto.streamError(response.getError());
                    if (error.code() == null || error.code().isBlank()) {
                        throw new IllegalArgumentException("StreamError code is required");
                    }
                    yield new StreamDecodeResult.LocalRejection(error);
                }
                case PAYLOAD_NOT_SET -> {
                    if (!response.getUnknownFields().asMap().isEmpty()) {
                        yield new StreamDecodeResult.Ignored(new DecodeNotes(List.of(), true));
                    }
                    throw new IllegalArgumentException("StreamResponse has no known payload or unknown-field evidence");
                }
            };
        } catch (RuntimeException ex) {
            return new StreamDecodeResult.Malformed(ex);
        }
    }

    public static StreamDecodeResult decodeJson(String payload, String protocolVersion) {
        try {
            requireVersion(protocolVersion);
            JsonElement element = JsonParser.parseString(payload);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("StreamResponse JSON must be an object");
            }
            JsonObject object = element.getAsJsonObject();
            if (V1_0.equals(protocolVersion)) {
                rejectV1_0StreamFields(object);
                org.a2aproject.sdk.grpc.StreamResponse.Builder builder = org.a2aproject.sdk.grpc.StreamResponse.newBuilder();
                mergeJson(object, builder);
                return decode(builder.build(), protocolVersion);
            }

            JsonObject known = new JsonObject();
            List<String> unknown = new ArrayList<>();
            for (Map.Entry<String, JsonElement> field : object.entrySet()) {
                String variant = STREAM_VARIANTS.get(field.getKey());
                if (variant == null) {
                    unknown.add(field.getKey());
                } else if (known.has(variant)) {
                    throw new IllegalArgumentException("StreamResponse contains duplicate aliases for payload " + variant);
                } else {
                    known.add(variant, field.getValue());
                }
            }
            if (known.size() > 1) {
                throw new IllegalArgumentException("StreamResponse contains multiple known payload variants");
            }
            if (known.isEmpty()) {
                if (unknown.size() != 1 || !object.get(unknown.get(0)).isJsonObject()) {
                    throw new IllegalArgumentException("Unknown StreamResponse variant must be a single object payload");
                }
                return new StreamDecodeResult.Ignored(new DecodeNotes(List.of(), true));
            }
            for (String name : unknown) {
                if (!object.get(name).isJsonObject()) {
                    throw new IllegalArgumentException("Unknown StreamResponse sibling must be an object payload");
                }
            }
            String selected = known.keySet().iterator().next();
            if ("task".equals(selected)) {
                DecodedTask task = decodeTaskJson(known.get(selected).toString(), protocolVersion);
                return new StreamDecodeResult.Known(task.task(), new DecodeNotes(
                        task.notes().skippedTimelineGenerations(), task.notes().unknownSkipped() || !unknown.isEmpty()));
            }
            JsonObject normalized = new JsonObject();
            normalized.add(selected, known.get(selected));
            org.a2aproject.sdk.grpc.StreamResponse.Builder builder = org.a2aproject.sdk.grpc.StreamResponse.newBuilder();
            mergeJson(normalized, builder);
            StreamDecodeResult decoded = decode(builder.build(), protocolVersion);
            if (decoded instanceof StreamDecodeResult.Known result && !unknown.isEmpty()) {
                return new StreamDecodeResult.Known(result.event(), new DecodeNotes(
                        result.notes().skippedTimelineGenerations(), true));
            }
            return decoded;
        } catch (RuntimeException ex) {
            return new StreamDecodeResult.Malformed(ex);
        }
    }

    public static DecodedTask decodeTask(org.a2aproject.sdk.grpc.Task response, String protocolVersion) {
        requireVersion(protocolVersion);
        if (V1_0.equals(protocolVersion)) {
            org.a2aproject.sdk.grpc.Task.Builder legacy = response.toBuilder().setGeneration(0).clearTimeline().clearArtifacts();
            response.getArtifactsList().forEach(artifact -> legacy.addArtifacts(stripArtifactV1_1Fields(artifact)));
            return new DecodedTask(ProtoUtils.FromProto.task(legacy.build()), DecodeNotes.EMPTY);
        }
        validateTask(response);
        List<Long> skipped = new ArrayList<>();
        org.a2aproject.sdk.grpc.Task.Builder filtered = response.toBuilder().clearTimeline();
        for (org.a2aproject.sdk.grpc.TimelineEntry entry : response.getTimelineList()) {
            if (entry.getEntryCase() == org.a2aproject.sdk.grpc.TimelineEntry.EntryCase.ENTRY_NOT_SET) {
                if (entry.getUnknownFields().asMap().isEmpty()) {
                    throw new IllegalArgumentException("Timeline entry has no known payload or unknown-field evidence");
                }
                skipped.add(entry.getGeneration());
                continue;
            }
            validateTimelineEntry(entry);
            filtered.addTimeline(entry);
        }
        return new DecodedTask(TaskMapper.INSTANCE.fromProto(filtered.build()),
                new DecodeNotes(skipped, !skipped.isEmpty()));
    }

    public static DecodedTask decodeTaskJson(String json, String protocolVersion) {
        requireVersion(protocolVersion);
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("Task JSON must be an object");
        }
        JsonObject task = root.getAsJsonObject();
        DecodeNotes notes = DecodeNotes.EMPTY;
        if (V1_1.equals(protocolVersion)) {
            notes = normalizeTaskTimeline(task);
        } else {
            requireNoV1_1TaskFields(task);
        }
        org.a2aproject.sdk.grpc.Task.Builder builder = org.a2aproject.sdk.grpc.Task.newBuilder();
        mergeJson(task, builder);
        DecodedTask decoded = decodeTask(builder.build(), protocolVersion);
        return new DecodedTask(decoded.task(), new DecodeNotes(notes.skippedTimelineGenerations(),
                notes.unknownSkipped() || decoded.notes().unknownSkipped()));
    }

    public static DecodedTaskList decodeTaskList(org.a2aproject.sdk.grpc.ListTasksResponse response, String protocolVersion) {
        requireVersion(protocolVersion);
        List<DecodedTask> decoded = new ArrayList<>(response.getTasksCount());
        org.a2aproject.sdk.grpc.ListTasksResponse.Builder filtered = response.toBuilder().clearTasks();
        for (org.a2aproject.sdk.grpc.Task task : response.getTasksList()) {
            DecodedTask item = decodeTask(task, protocolVersion);
            decoded.add(item);
            filtered.addTasks(TaskMapper.INSTANCE.toProto(item.task()));
        }
        return new DecodedTaskList(ProtoUtils.FromProto.listTasksResult(filtered.build()), decoded);
    }

    public static DecodedTaskList decodeTaskListJson(String json, String protocolVersion) {
        requireVersion(protocolVersion);
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("ListTasksResponse JSON must be an object");
        }
        JsonObject response = root.getAsJsonObject();
        JsonElement tasksElement = response.get("tasks");
        if (tasksElement != null && !tasksElement.isJsonArray()) {
            throw new IllegalArgumentException("ListTasksResponse tasks must be an array");
        }
        List<DecodedTask> decoded = new ArrayList<>();
        JsonArray tasks = tasksElement == null ? new JsonArray() : tasksElement.getAsJsonArray();
        for (int i = 0; i < tasks.size(); i++) {
            if (!tasks.get(i).isJsonObject()) {
                throw new IllegalArgumentException("ListTasksResponse task must be an object");
            }
            DecodedTask task = decodeTaskJson(tasks.get(i).toString(), protocolVersion);
            decoded.add(task);
            tasks.set(i, taskToJson(task.task()));
        }
        org.a2aproject.sdk.grpc.ListTasksResponse.Builder builder = org.a2aproject.sdk.grpc.ListTasksResponse.newBuilder();
        mergeJson(response, builder);
        return new DecodedTaskList(ProtoUtils.FromProto.listTasksResult(builder.build()), decoded);
    }

    public static DecodedEvent decodeSendResponse(org.a2aproject.sdk.grpc.SendMessageResponse response,
            String protocolVersion) {
        requireVersion(protocolVersion);
        return switch (response.getPayloadCase()) {
            case TASK -> {
                DecodedTask task = decodeTask(response.getTask(), protocolVersion);
                yield new DecodedEvent(task.task(), task);
            }
            case MESSAGE -> {
                validateMessage(response.getMessage());
                Message message = ProtoUtils.FromProto.message(response.getMessage());
                yield new DecodedEvent(message, null);
            }
            case PAYLOAD_NOT_SET -> throw new IllegalArgumentException("SendMessageResponse has no known payload");
        };
    }

    public static DecodedEvent decodeSendResponseJson(String json, String protocolVersion) {
        requireVersion(protocolVersion);
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("SendMessageResponse JSON must be an object");
        }
        JsonObject response = root.getAsJsonObject();
        if (response.size() != 1) {
            throw new IllegalArgumentException("SendMessageResponse must contain exactly one payload");
        }
        if (response.has("task")) {
            DecodedTask task = decodeTaskJson(response.get("task").toString(), protocolVersion);
            return new DecodedEvent(task.task(), task);
        }
        if (response.has("message")) {
            org.a2aproject.sdk.grpc.Message.Builder builder = org.a2aproject.sdk.grpc.Message.newBuilder();
            mergeJson(response.get("message"), builder);
            validateMessage(builder.build());
            return new DecodedEvent(ProtoUtils.FromProto.message(builder.build()), null);
        }
        throw new IllegalArgumentException("Unknown SendMessageResponse payload");
    }

    private static StreamingEventKind decodeLegacyEvent(org.a2aproject.sdk.grpc.StreamResponse response) {
        return switch (response.getPayloadCase()) {
            case TASK -> decodeTask(response.getTask(), V1_0).task();
            case MESSAGE -> ProtoUtils.FromProto.message(response.getMessage());
            case STATUS_UPDATE -> ProtoUtils.FromProto.taskStatusUpdateEvent(
                    response.getStatusUpdate().toBuilder().setGeneration(0).build());
            case ARTIFACT_UPDATE -> {
                org.a2aproject.sdk.grpc.TaskArtifactUpdateEvent update = response.getArtifactUpdate().toBuilder()
                        .setGeneration(0).setArtifact(stripArtifactV1_1Fields(response.getArtifactUpdate().getArtifact()))
                        .build();
                yield ProtoUtils.FromProto.taskArtifactUpdateEvent(update);
            }
            case MESSAGE_UPDATE, ERROR ->
                throw new IllegalArgumentException("Stream payload is not part of protocol version 1.0");
            case PAYLOAD_NOT_SET -> throw new IllegalArgumentException("StreamResponse payload oneof field not set");
        };
    }

    private static org.a2aproject.sdk.grpc.Artifact stripArtifactV1_1Fields(org.a2aproject.sdk.grpc.Artifact artifact) {
        return artifact.toBuilder().setStartGeneration(0).setEndGeneration(0)
                .setRole(org.a2aproject.sdk.grpc.Role.ROLE_UNSPECIFIED).build();
    }

    private static void rejectV1_0StreamFields(JsonObject response) {
        if (response.has("messageUpdate") || response.has("error")) {
            throw new IllegalArgumentException("Stream payload is not part of protocol version 1.0");
        }
        JsonElement task = response.get("task");
        if (task != null && task.isJsonObject()) {
            requireNoV1_1TaskFields(task.getAsJsonObject());
        }
        JsonElement status = response.has("statusUpdate") ? response.get("statusUpdate") : response.get("status_update");
        if (status != null && status.isJsonObject() && status.getAsJsonObject().has("generation")) {
            throw new IllegalArgumentException("Event generation is not part of protocol version 1.0");
        }
        JsonElement artifactUpdate = response.has("artifactUpdate")
                ? response.get("artifactUpdate") : response.get("artifact_update");
        if (artifactUpdate != null && artifactUpdate.isJsonObject()) {
            JsonObject update = artifactUpdate.getAsJsonObject();
            if (update.has("generation")) {
                throw new IllegalArgumentException("Event generation is not part of protocol version 1.0");
            }
            JsonElement artifact = update.get("artifact");
            if (artifact != null && artifact.isJsonObject()) {
                JsonObject value = artifact.getAsJsonObject();
                if (value.has("startGeneration") || value.has("start_generation") || value.has("endGeneration")
                        || value.has("end_generation") || value.has("role")) {
                    throw new IllegalArgumentException("Artifact provenance is not part of protocol version 1.0");
                }
            }
        }
    }

    private static void requireNoV1_1TaskFields(JsonObject task) {
        if (task.has("generation") || task.has("timeline")) {
            throw new IllegalArgumentException("Task generation and timeline are not part of protocol version 1.0");
        }
        JsonElement artifactsElement = task.get("artifacts");
        if (artifactsElement != null) {
            if (!artifactsElement.isJsonArray()) {
                throw new IllegalArgumentException("Task artifacts must be an array");
            }
            for (JsonElement artifactElement : artifactsElement.getAsJsonArray()) {
                if (!artifactElement.isJsonObject()) {
                    throw new IllegalArgumentException("Task artifact must be an object");
                }
                JsonObject artifact = artifactElement.getAsJsonObject();
                if (artifact.has("startGeneration") || artifact.has("start_generation")
                        || artifact.has("endGeneration") || artifact.has("end_generation") || artifact.has("role")) {
                    throw new IllegalArgumentException("Artifact provenance is not part of protocol version 1.0");
                }
            }
        }
    }

    private static DecodeNotes normalizeTaskTimeline(JsonObject task) {
        JsonElement timelineElement = task.get("timeline");
        if (timelineElement == null) {
            return DecodeNotes.EMPTY;
        }
        if (!timelineElement.isJsonArray()) {
            throw new IllegalArgumentException("Task timeline must be an array");
        }
        JsonArray timeline = timelineElement.getAsJsonArray();
        JsonArray knownEntries = new JsonArray();
        List<Long> skipped = new ArrayList<>();
        boolean unknownSkipped = false;
        for (JsonElement element : timeline) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Timeline entry must be an object");
            }
            JsonObject entry = element.getAsJsonObject();
            JsonElement generation = entry.get("generation");
            if (generation == null) {
                throw new IllegalArgumentException("Timeline entry generation is required");
            }
            long value = parseInt64(generation, "timeline generation");
            boolean hasMessage = entry.has("message");
            boolean hasStatus = entry.has("status");
            if (hasMessage && hasStatus) {
                throw new IllegalArgumentException("Timeline entry contains multiple known payloads");
            }
            if (hasMessage || hasStatus) {
                String known = hasMessage ? "message" : "status";
                if (!entry.get(known).isJsonObject()) {
                    throw new IllegalArgumentException("Known timeline payload must be an object");
                }
                JsonObject sanitized = new JsonObject();
                sanitized.add("generation", generation);
                sanitized.add(known, entry.get(known));
                if (entry.size() > 2) {
                    unknownSkipped = true;
                }
                knownEntries.add(sanitized);
            } else {
                List<String> variants = new ArrayList<>();
                for (String key : entry.keySet()) {
                    if (!"generation".equals(key)) {
                        variants.add(key);
                    }
                }
                if (variants.size() != 1 || !entry.get(variants.get(0)).isJsonObject()) {
                    throw new IllegalArgumentException("Unknown timeline variant must be a single object payload");
                }
                skipped.add(value);
                unknownSkipped = true;
            }
        }
        task.add("timeline", knownEntries);
        return new DecodeNotes(skipped, unknownSkipped);
    }

    private static JsonObject taskToJson(Task task) {
        org.a2aproject.sdk.grpc.Task proto = TaskMapper.INSTANCE.toProto(task);
        try {
            return JsonParser.parseString(ProtoJsonUtils.toJson(JsonFormat.printer(), proto)).getAsJsonObject();
        } catch (InvalidProtocolBufferException ex) {
            throw new IllegalArgumentException("Unable to serialize filtered Task", ex);
        }
    }

    private static void validateTask(org.a2aproject.sdk.grpc.Task task) {
        validateTaskAndContext(task.getId(), task.getContextId());
        if (!task.hasStatus()) {
            throw new IllegalArgumentException("Task status is required");
        }
        validateStatus(task.getStatus());
        task.getArtifactsList().forEach(StreamDecoder::validateArtifact);
    }

    private static void validateTaskAndContext(String taskId, String contextId) {
        if (taskId.isBlank() || contextId.isBlank()) {
            throw new IllegalArgumentException("Task and context IDs are required");
        }
    }

    private static void validateStatusUpdate(org.a2aproject.sdk.grpc.TaskStatusUpdateEvent update) {
        validateTaskAndContext(update.getTaskId(), update.getContextId());
        if (!update.hasStatus()) {
            throw new IllegalArgumentException("Task status update content is required");
        }
        validateStatus(update.getStatus());
    }

    private static void validateArtifactUpdate(org.a2aproject.sdk.grpc.TaskArtifactUpdateEvent update) {
        validateTaskAndContext(update.getTaskId(), update.getContextId());
        if (!update.hasArtifact()) {
            throw new IllegalArgumentException("Artifact update content is required");
        }
        validateArtifact(update.getArtifact());
    }

    private static void validateStatus(org.a2aproject.sdk.grpc.TaskStatus status) {
        if (status.getState() == org.a2aproject.sdk.grpc.TaskState.TASK_STATE_UNSPECIFIED) {
            throw new IllegalArgumentException("Task status state is required");
        }
        if (status.hasMessage()) {
            validateMessage(status.getMessage());
        }
    }

    private static void validateArtifact(org.a2aproject.sdk.grpc.Artifact artifact) {
        if (artifact.getArtifactId().isBlank() || artifact.getPartsCount() == 0) {
            throw new IllegalArgumentException("Artifact is missing a required ID or parts");
        }
    }

    private static void validateTimelineEntry(org.a2aproject.sdk.grpc.TimelineEntry entry) {
        switch (entry.getEntryCase()) {
            case MESSAGE -> validateMessage(entry.getMessage());
            case STATUS -> validateStatus(entry.getStatus());
            case ENTRY_NOT_SET -> throw new IllegalArgumentException("Timeline entry has no known payload");
        }
    }

    private static void validateMessage(org.a2aproject.sdk.grpc.Message message) {
        if (message.getMessageId().isBlank() || message.getPartsCount() == 0
                || message.getRole() == org.a2aproject.sdk.grpc.Role.ROLE_UNSPECIFIED) {
            throw new IllegalArgumentException("Message is missing a required id, role, or parts");
        }
    }

    private static long parseInt64(JsonElement value, String label) {
        if (!value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(label + " must be a signed 64-bit integer");
        }
        try {
            return Long.parseLong(value.getAsString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a signed 64-bit integer", ex);
        }
    }

    private static void mergeJson(JsonElement json, com.google.protobuf.Message.Builder builder) {
        try {
            JsonFormat.parser().merge(json.toString(), builder);
        } catch (InvalidProtocolBufferException ex) {
            throw new IllegalArgumentException("Malformed known protocol content", ex);
        }
    }

    private static void requireVersion(String version) {
        if (!V1_0.equals(version) && !V1_1.equals(version)) {
            throw new IllegalArgumentException("Unsupported protocol version for protobuf decoding: " + version);
        }
    }
}
