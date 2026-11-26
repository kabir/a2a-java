package org.a2aproject.sdk.client;

import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.ObserveTaskParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.jspecify.annotations.Nullable;

/** Preflight checks for companion parameters whose semantics are not available on every selected path. */
final class ClientInputPolicy {
    private ClientInputPolicy() {
    }

    /**
     * Validates a send and, for selected 1.1, rejects locally (before any I/O) an explicit generation guard on a
     * task whose generation signal this client already found unreliable.
     */
    static void validateSend(String version, SendInputParams params, AgentCard card, GenerationReliability reliability)
            throws A2AClientException {
        validateSend(version, params, card);
        if ("1.1".equals(version)) {
            reliability.requireGuardAllowed(params);
        }
    }

    /** Only a no-cursor observation is implemented; a cursor is rejected before any I/O. */
    static void validateObserve(ObserveTaskParams params) throws A2AClientException {
        if (params.lastSeenGeneration() != null) {
            throw new A2AClientException("Cursor observation is not implemented yet");
        }
    }

    static void validateSend(String version, SendInputParams params, AgentCard card) throws A2AClientException {
        boolean legacy = "1.0".equals(version);
        if (!legacy && !"1.1".equals(version)) {
            throw new A2AClientException("Unsupported selected protocol version: " + version);
        }
        if (legacy && !params.artifactUpdates().isEmpty()) {
            throw new A2AClientException("Artifact input requires protocol version 1.1");
        }
        if (!params.artifactUpdates().isEmpty() && !card.capabilities().userArtifacts()) {
            throw new A2AClientException("Agent does not advertise userArtifacts");
        }
        if (params.configuration() != null) {
            if (legacy && (params.configuration().ifGenerationMatch() != null
                    || params.configuration().timelineLength() != null)) {
                throw new A2AClientException("Input guards and timeline limits require protocol version 1.1");
            }
            rejectDeliveryVersion(params.configuration().taskPushNotificationConfig());
        }
    }

    static void validateTaskQuery(String version, TaskQueryParams params) throws A2AClientException {
        if (params.currentGeneration() != null) {
            throw new A2AClientException("Current-generation task queries are not implemented yet");
        }
        if ("1.0".equals(version) && params.timelineLength() != null) {
            throw new A2AClientException("Timeline limits require protocol version 1.1");
        }
    }

    static void validateListTasks(String version, ListTasksParams params) throws A2AClientException {
        if ("1.0".equals(version) && params.timelineLength() != null) {
            throw new A2AClientException("Timeline limits require protocol version 1.1");
        }
    }

    static void validatePushNotificationConfig(TaskPushNotificationConfig params) throws A2AClientException {
        rejectDeliveryVersion(params);
    }

    private static void rejectDeliveryVersion(@Nullable TaskPushNotificationConfig config) throws A2AClientException {
        if (config != null && config.protocolVersion() != null) {
            throw new A2AClientException("Push notification delivery-version selection is not implemented yet");
        }
    }
}
