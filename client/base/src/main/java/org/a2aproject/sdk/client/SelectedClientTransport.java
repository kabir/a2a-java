package org.a2aproject.sdk.client;

import java.util.Set;
import java.util.function.Consumer;

import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetExtendedAgentCardParams;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.jspecify.annotations.Nullable;

/** Ordinary-only adapter that retains decorators and the validated capability intersection. */
final class SelectedClientTransport implements VersionedClientTransport {
    private final ClientTransport delegate;
    private final String version;
    private final Set<ClientFeature> features;

    SelectedClientTransport(ClientTransport delegate, String version, Set<ClientFeature> features) {
        this.delegate = delegate;
        this.version = version;
        this.features = Set.copyOf(features);
    }

    @Override
    public String protocolVersion() {
        return version;
    }

    @Override
    public Set<ClientFeature> capabilities() {
        return features;
    }

    @Override
    public EventKind sendMessage(MessageSendParams request, @Nullable ClientCallContext context) throws A2AClientException {
        return delegate.sendMessage(request, context);
    }

    @Override
    public void sendMessageStreaming(MessageSendParams request, Consumer<StreamingEventKind> eventConsumer, Consumer<Throwable> errorConsumer, @Nullable ClientCallContext context) throws A2AClientException {
        delegate.sendMessageStreaming(request, eventConsumer, errorConsumer, context);
    }

    @Override
    public Task getTask(TaskQueryParams request, @Nullable ClientCallContext context) throws A2AClientException {
        return delegate.getTask(request, context);
    }

    @Override
    public Task cancelTask(CancelTaskParams request, @Nullable ClientCallContext context) throws A2AClientException {
        return delegate.cancelTask(request, context);
    }

    @Override
    public ListTasksResult listTasks(ListTasksParams request, @Nullable ClientCallContext context) throws A2AClientException {
        return delegate.listTasks(request, context);
    }

    @Override
    public TaskPushNotificationConfig createTaskPushNotificationConfiguration(TaskPushNotificationConfig request, @Nullable ClientCallContext context) throws A2AClientException {
        return delegate.createTaskPushNotificationConfiguration(request, context);
    }

    @Override
    public TaskPushNotificationConfig getTaskPushNotificationConfiguration(GetTaskPushNotificationConfigParams request, @Nullable ClientCallContext context) throws A2AClientException {
        return delegate.getTaskPushNotificationConfiguration(request, context);
    }

    @Override
    public ListTaskPushNotificationConfigsResult listTaskPushNotificationConfigurations(ListTaskPushNotificationConfigsParams request, @Nullable ClientCallContext context) throws A2AClientException {
        return delegate.listTaskPushNotificationConfigurations(request, context);
    }

    @Override
    public void deleteTaskPushNotificationConfigurations(DeleteTaskPushNotificationConfigParams request, @Nullable ClientCallContext context) throws A2AClientException {
        delegate.deleteTaskPushNotificationConfigurations(request, context);
    }

    @Override
    public void subscribeToTask(TaskIdParams request, Consumer<StreamingEventKind> eventConsumer, Consumer<Throwable> errorConsumer, @Nullable ClientCallContext context) throws A2AClientException {
        delegate.subscribeToTask(request, eventConsumer, errorConsumer, context);
    }

    @Override
    public AgentCard getExtendedAgentCard(GetExtendedAgentCardParams params, @Nullable ClientCallContext context) throws A2AClientException {
        return delegate.getExtendedAgentCard(params, context);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
