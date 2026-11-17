package org.a2aproject.sdk.server.tasks;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.util.CollectionCopies;
import org.a2aproject.sdk.spec.util.Utils;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * Immutable request-local input and already-resolved admission facts.
 *
 * <p>Constructing this value has no task-store, ownership, or configuration side effects.</p>
 */
public record PendingTaskInput(@Nullable Message message, List<TaskArtifactUpdateEvent> artifactUpdates,
        @Nullable MessageSendConfiguration configuration, @Nullable Map<String, Object> metadata,
        Admission admission, OffsetDateTime receivedAt) {

    /** Captures request input and its timestamp before entering the atomic planner callback. */
    public PendingTaskInput(@Nullable Message message, List<TaskArtifactUpdateEvent> artifactUpdates,
            @Nullable MessageSendConfiguration configuration, @Nullable Map<String, Object> metadata,
            Admission admission) {
        this(message, artifactUpdates, configuration, metadata, admission, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public PendingTaskInput {
        artifactUpdates = List.copyOf(Assert.checkNotNullParam("artifactUpdates", artifactUpdates));
        metadata = CollectionCopies.unmodifiableNullableShallowMap(metadata);
        Assert.checkNotNullParam("admission", admission);
        Assert.checkNotNullParam("receivedAt", receivedAt);
    }

    /**
     * Request identity and facts resolved outside the pure planner.
     *
     * @param taskId proposed or requested task ID
     * @param contextId proposed or requested context ID
     * @param tenant selected tenant, if any
     * @param authorized result of the applicable authorization check
     * @param userArtifactsEnabled effective-card userArtifacts capability
     */
    public record Admission(String taskId, String contextId, @Nullable String tenant, boolean authorized,
            boolean userArtifactsEnabled) {
        public Admission {
            Assert.checkNotNullParam("taskId", taskId);
            Assert.checkNotNullParam("contextId", contextId);
            Utils.validateTenant(tenant);
        }
    }
}
