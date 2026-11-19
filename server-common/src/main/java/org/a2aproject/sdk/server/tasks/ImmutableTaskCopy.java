package org.a2aproject.sdk.server.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.FilePart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.a2aproject.sdk.spec.TextPart;
import org.jspecify.annotations.Nullable;

/** Deep value copy for mutable JSON containers held in protocol metadata. */
final class ImmutableTaskCopy {
    private ImmutableTaskCopy() {
    }

    static Task copy(Task task) {
        List<Artifact> artifacts = task.artifacts() == null ? null
                : task.artifacts().stream().map(ImmutableTaskCopy::copy).toList();
        List<Message> history = task.history() == null ? null
                : task.history().stream().map(ImmutableTaskCopy::copy).toList();
        List<TimelineEntry> timeline = task.timeline() == null ? null
                : task.timeline().stream().map(ImmutableTaskCopy::copy).toList();
        Task.Builder builder = Task.builder(task).status(copy(task.status()))
                .artifacts(artifacts).history(history).timeline(timeline);
        if (task.metadata() != null) {
            builder.metadata(copyNonNullMap(task.metadata()));
        }
        return builder.build();
    }

    private static TimelineEntry copy(TimelineEntry entry) {
        TimelineEntry.Payload payload = entry.entry();
        if (payload instanceof TimelineEntry.MessageValue messageValue) {
            payload = new TimelineEntry.MessageValue(copy(messageValue.message()));
        } else if (payload instanceof TimelineEntry.StatusValue statusValue) {
            payload = new TimelineEntry.StatusValue(copy(statusValue.status()));
        }
        return new TimelineEntry(entry.generation(), payload);
    }

    private static TaskStatus copy(TaskStatus status) {
        Message message = status.message();
        return new TaskStatus(status.state(), message == null ? null : copy(message), status.timestamp());
    }

    private static Artifact copy(Artifact artifact) {
        return Artifact.builder(artifact).parts(copyParts(artifact.parts()))
                .metadata(copyMap(artifact.metadata())).build();
    }

    private static Message copy(Message message) {
        return Message.builder(message).parts(copyParts(message.parts()))
                .metadata(copyMap(message.metadata())).build();
    }

    private static List<Part<?>> copyParts(List<Part<?>> parts) {
        return parts.stream().map(part -> {
            if (part instanceof TextPart text) {
                return (Part<?>) new TextPart(text.text(), copyMap(text.metadata()), text.mediaType());
            }
            if (part instanceof DataPart data) {
                return (Part<?>) new DataPart(copyValue(data.data()), copyMap(data.metadata()), data.mediaType());
            }
            if (part instanceof FilePart file) {
                return (Part<?>) new FilePart(file.file(), copyMap(file.metadata()));
            }
            return part;
        }).toList();
    }

    private static @Nullable Map<String, Object> copyMap(@Nullable Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Object> copyNonNullMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static @Nullable Object copyValue(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(key, copyValue(nested)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(nested -> copy.add(copyValue(nested)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
