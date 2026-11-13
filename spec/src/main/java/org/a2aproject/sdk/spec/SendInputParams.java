package org.a2aproject.sdk.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.a2aproject.sdk.spec.util.CollectionCopies;
import org.a2aproject.sdk.spec.util.Utils;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/** Companion input shape for message-bearing and artifact-only requests. */
public record SendInputParams(@Nullable Message message, List<TaskArtifactUpdateEvent> artifactUpdates,
        @Nullable MessageSendConfiguration configuration, @Nullable Map<String, Object> metadata,
        @Nullable String tenant) {

    public SendInputParams {
        artifactUpdates = List.copyOf(Assert.checkNotNullParam("artifactUpdates", artifactUpdates));
        metadata = CollectionCopies.unmodifiableNullableShallowMap(metadata);
        Utils.validateTenant(tenant);
        validateInput(message, artifactUpdates);
    }

    /** Adapts the legacy message-only API without changing its non-null message contract. */
    public static SendInputParams from(MessageSendParams legacy) {
        Assert.checkNotNullParam("legacy", legacy);
        return new SendInputParams(legacy.message(), List.of(), legacy.configuration(), legacy.metadata(), legacy.tenant());
    }

    /** Converts to the legacy shape only when no information would be discarded. */
    public MessageSendParams requireMessageOnly() {
        if (message == null || !artifactUpdates.isEmpty()) {
            throw new IllegalArgumentException("Request cannot be represented by MessageSendParams");
        }
        return new MessageSendParams(message, configuration, metadata, tenant);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static void validateInput(@Nullable Message message, List<TaskArtifactUpdateEvent> updates) {
        if (message == null && updates.isEmpty()) {
            throw new IllegalArgumentException("A message or at least one artifact update is required");
        }
        if (updates.isEmpty()) {
            return;
        }

        String taskId = null;
        String contextId = null;
        boolean openingMessage = message != null && blank(message.taskId()) && blank(message.contextId());
        for (TaskArtifactUpdateEvent update : updates) {
            String updateTaskId = update.taskId();
            String updateContextId = update.contextId();
            if (message == null) {
                if (blank(updateTaskId) || blank(updateContextId)) {
                    throw new IllegalArgumentException("Artifact-only input requires existing task and context IDs");
                }
                if (taskId != null && (!taskId.equals(updateTaskId) || !Objects.equals(contextId, updateContextId))) {
                    throw new IllegalArgumentException("Artifact updates must target the same task and context");
                }
                taskId = updateTaskId;
                contextId = updateContextId;
            } else if (blank(updateTaskId) || blank(updateContextId)) {
                if (!openingMessage || !blank(updateTaskId) || !blank(updateContextId)) {
                    throw new IllegalArgumentException("Only an opening message may use empty provisional update IDs");
                }
            } else {
                if (taskId != null && (!taskId.equals(updateTaskId) || !Objects.equals(contextId, updateContextId))) {
                    throw new IllegalArgumentException("Artifact updates must target the same task and context");
                }
                taskId = updateTaskId;
                contextId = updateContextId;
                if ((!blank(message.taskId()) && !Objects.equals(message.taskId(), updateTaskId))
                        || (!blank(message.contextId()) && !Objects.equals(message.contextId(), updateContextId))) {
                    throw new IllegalArgumentException("Artifact update identity conflicts with the message");
                }
            }
        }
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /** Builder for constructing immutable companion input parameters. */
    public static final class Builder {
        private @Nullable Message message;
        private List<TaskArtifactUpdateEvent> artifactUpdates = List.of();
        private @Nullable MessageSendConfiguration configuration;
        private @Nullable Map<String, Object> metadata;
        private @Nullable String tenant;

        private Builder() {
        }

        public Builder message(@Nullable Message message) {
            this.message = message;
            return this;
        }

        public Builder artifactUpdates(List<TaskArtifactUpdateEvent> artifactUpdates) {
            this.artifactUpdates = artifactUpdates;
            return this;
        }

        public Builder configuration(@Nullable MessageSendConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder metadata(@Nullable Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder tenant(@Nullable String tenant) {
            this.tenant = tenant;
            return this;
        }

        public SendInputParams build() {
            return new SendInputParams(message, artifactUpdates, configuration, metadata, tenant);
        }
    }
}
