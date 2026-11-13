package org.a2aproject.sdk.compat03.conversion;

import jakarta.enterprise.context.ApplicationScoped;

import org.a2aproject.sdk.compat03.conversion.mappers.domain.TaskMapper_v0_3;
import org.a2aproject.sdk.compat03.json.JsonProcessingException_v0_3;
import org.a2aproject.sdk.compat03.json.JsonUtil_v0_3;
import org.a2aproject.sdk.compat03.spec.Task_v0_3;
import org.a2aproject.sdk.server.tasks.PushNotificationPayloadFormatter;
import org.a2aproject.sdk.server.version.ServerVersionPolicy;
import org.a2aproject.sdk.server.version.VersionedOutputProjector;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class PushNotificationPayloadFormatter_v0_3 implements PushNotificationPayloadFormatter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushNotificationPayloadFormatter_v0_3.class);

    @Override
    public String targetVersion() {
        return A2AProtocol_v0_3.PROTOCOL_VERSION;
    }

    @Override
    public @Nullable String formatPayload(StreamingEventKind event, @Nullable Task taskSnapshot) {
        StreamingEventKind projectedEvent = VersionedOutputProjector.projectEvent(event, ServerVersionPolicy.V0_3);
        if (projectedEvent == null || projectedEvent instanceof Message) {
            return null;
        }
        if (taskSnapshot == null) {
            LOGGER.warn("Cannot format v0.3 push notification: no task snapshot available");
            return null;
        }
        Task legacySnapshot = VersionedOutputProjector.projectTask(taskSnapshot, ServerVersionPolicy.V0_3);
        Task_v0_3 v03Task = TaskMapper_v0_3.INSTANCE.fromV10(legacySnapshot);
        try {
            return JsonUtil_v0_3.toJson(v03Task);
        } catch (JsonProcessingException_v0_3 e) {
            LOGGER.error("Failed to serialize v0.3 task for push notification", e);
            return null;
        }
    }
}
