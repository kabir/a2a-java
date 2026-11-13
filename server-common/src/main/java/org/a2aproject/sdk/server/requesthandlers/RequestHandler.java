package org.a2aproject.sdk.server.requesthandlers;

import java.util.concurrent.Flow;

import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.ObserveTaskParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.jspecify.annotations.Nullable;

public interface RequestHandler {
    Task onGetTask(
            TaskQueryParams params,
            ServerCallContext context) throws A2AError;

    ListTasksResult onListTasks(
            ListTasksParams params,
            ServerCallContext context) throws A2AError;

    Task onCancelTask(
            CancelTaskParams params,
            ServerCallContext context) throws A2AError;

    EventKind onMessageSend(
            MessageSendParams params,
            ServerCallContext context) throws A2AError;

    Flow.Publisher<StreamingEventKind> onMessageSendStream(
            MessageSendParams params,
            ServerCallContext context) throws A2AError;

    /**
     * Companion input boundary for 1.1 callers. Existing handlers retain their legacy behavior
     * for message-only requests, while unsupported mutation semantics fail before core access.
     */
    default EventKind onSendInput(SendInputParams params, ServerCallContext context) throws A2AError {
        requireLegacyInput(params);
        return onMessageSend(params.requireMessageOnly(), context);
    }

    /** Streaming companion input boundary; advanced mutation semantics require an override. */
    default Flow.Publisher<StreamingEventKind> onSendInputStream(
            SendInputParams params, ServerCallContext context) throws A2AError {
        requireLegacyInput(params);
        return onMessageSendStream(params.requireMessageOnly(), context);
    }

    TaskPushNotificationConfig onCreateTaskPushNotificationConfig(
            TaskPushNotificationConfig params,
            ServerCallContext context) throws A2AError;

    TaskPushNotificationConfig onGetTaskPushNotificationConfig(
            GetTaskPushNotificationConfigParams params,
            ServerCallContext context) throws A2AError;

    Flow.Publisher<StreamingEventKind> onSubscribeToTask(
            TaskIdParams params,
            ServerCallContext context) throws A2AError;

    /** Observation companion boundary; cursor semantics require a Phase 4 override. */
    default Flow.Publisher<StreamingEventKind> onObserveTask(
            ObserveTaskParams params, ServerCallContext context) throws A2AError {
        if (params.lastSeenGeneration() != null) {
            throw new UnsupportedOperationError();
        }
        return onSubscribeToTask(new TaskIdParams(params.id(), params.tenant()), context);
    }

    private static void requireLegacyInput(SendInputParams params) throws UnsupportedOperationError {
        if (!params.artifactUpdates().isEmpty()) {
            throw new UnsupportedOperationError();
        }
        if (params.configuration() != null
                && (params.configuration().ifGenerationMatch() != null
                        || params.configuration().timelineLength() != null
                        || params.configuration().taskPushNotificationConfig() != null
                                && params.configuration().taskPushNotificationConfig().protocolVersion() != null)) {
            throw new UnsupportedOperationError();
        }
    }

    ListTaskPushNotificationConfigsResult onListTaskPushNotificationConfigs(
            ListTaskPushNotificationConfigsParams params,
            ServerCallContext context) throws A2AError;

    void onDeleteTaskPushNotificationConfig(
            DeleteTaskPushNotificationConfigParams params,
            ServerCallContext context) throws A2AError;

    void authorizeTaskAccess(@Nullable String requestedTaskId, ServerCallContext context,
            TaskOperation operation) throws A2AError;
}
