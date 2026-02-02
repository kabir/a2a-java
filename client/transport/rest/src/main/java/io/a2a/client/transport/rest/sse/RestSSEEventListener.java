package io.a2a.client.transport.rest.sse;

import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.a2a.client.transport.rest.RestErrorMapper;
import io.a2a.grpc.StreamResponse;
import io.a2a.grpc.utils.ProtoUtils;
import io.a2a.spec.StreamingEventKind;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatusUpdateEvent;
import org.jspecify.annotations.Nullable;

public class RestSSEEventListener {

    private static final Logger log = Logger.getLogger(RestSSEEventListener.class.getName());
    private final Consumer<StreamingEventKind> eventHandler;
    private final Consumer<Throwable> errorHandler;

    public RestSSEEventListener(Consumer<StreamingEventKind> eventHandler,
            Consumer<Throwable> errorHandler) {
        this.eventHandler = eventHandler;
        this.errorHandler = errorHandler;
    }

    public void onMessage(String message, @Nullable Future<Void> completableFuture) {
        try {
            log.fine("Streaming message received: " + message);
            io.a2a.grpc.StreamResponse.Builder builder = io.a2a.grpc.StreamResponse.newBuilder();
            JsonFormat.parser().merge(message, builder);
            handleMessage(builder.build(), completableFuture);
        } catch (InvalidProtocolBufferException e) {
            errorHandler.accept(RestErrorMapper.mapRestError(message, 500));
        }
    }

    public void onError(Throwable throwable, @Nullable Future<Void> future) {
        if (errorHandler != null) {
            errorHandler.accept(throwable);
        }
        if (future != null) {
            future.cancel(true); // close SSE channel
        }
    }

    private void handleMessage(StreamResponse response, @Nullable Future<Void> future) {
        StreamingEventKind event;
        switch (response.getPayloadCase()) {
            case MESSAGE ->
                event = ProtoUtils.FromProto.message(response.getMessage());
            case TASK ->
                event = ProtoUtils.FromProto.task(response.getTask());
            case STATUS_UPDATE ->
                event = ProtoUtils.FromProto.taskStatusUpdateEvent(response.getStatusUpdate());
            case ARTIFACT_UPDATE ->
                event = ProtoUtils.FromProto.taskArtifactUpdateEvent(response.getArtifactUpdate());
            default -> {
                log.warning("Invalid stream response " + response.getPayloadCase());
                errorHandler.accept(new IllegalStateException("Invalid stream response from server: " + response.getPayloadCase()));
                return;
            }
        }
        eventHandler.accept(event);

        // Client-side auto-close on final events to prevent connection leaks
        // Handles both TaskStatusUpdateEvent and Task objects with final states
        // This covers late subscriptions to completed tasks and ensures no connection leaks
        boolean shouldClose = false;
        if (event instanceof TaskStatusUpdateEvent tue && tue.isFinal()) {
            shouldClose = true;
        } else if (event instanceof Task task) {
            TaskState state = task.status().state();
            if (state.isFinal()) {
                shouldClose = true;
            }
        }

        if (shouldClose && future != null) {
            future.cancel(true); // close SSE channel
        }
    }

}
