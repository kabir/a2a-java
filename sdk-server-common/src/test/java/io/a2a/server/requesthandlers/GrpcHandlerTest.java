package io.a2a.server.requesthandlers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.Struct;

import io.a2a.grpc.CancelTaskRequest;
import io.a2a.grpc.GetTaskRequest;
import io.a2a.grpc.Message;
import io.a2a.grpc.Part;
import io.a2a.grpc.Role;
import io.a2a.grpc.SendMessageRequest;
import io.a2a.grpc.SendMessageResponse;
import io.a2a.grpc.Task;
import io.a2a.grpc.TaskState;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.TextPart;
import io.a2a.spec.UnsupportedOperationError;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.internal.testing.StreamRecorder;

import org.junit.jupiter.api.Test;

public class GrpcHandlerTest extends AbstractA2ARequestHandlerTest {

    @Test
    public void testOnGetTaskSuccess() throws Exception {
        GrpcHandler handler = new GrpcHandler(CARD, requestHandler);
        taskStore.save(MINIMAL_TASK);
        GetTaskRequest request = GetTaskRequest.newBuilder()
                .setName("tasks/" + MINIMAL_TASK.getId())
                .build();

        StreamRecorder<Task> streamRecorder = StreamRecorder.create();
        handler.getTask(request, streamRecorder);
        streamRecorder.awaitCompletion(5, TimeUnit.SECONDS);

        assertNull(streamRecorder.getError());
        List<Task> result = streamRecorder.getValues();
        assertNotNull(result);
        assertEquals(1, result.size());
        Task task = result.get(0);
        assertEquals(MINIMAL_TASK.getId(), task.getId());
        assertEquals(MINIMAL_TASK.getContextId(), task.getContextId());
        assertEquals(TaskState.TASK_STATE_SUBMITTED, task.getStatus().getState());
    }

    @Test
    public void testOnGetTaskNotFound() throws Exception {
        GrpcHandler handler = new GrpcHandler(CARD, requestHandler);
        GetTaskRequest request = GetTaskRequest.newBuilder()
                .setName("tasks/" + MINIMAL_TASK.getId())
                .build();

        StreamRecorder<Task> streamRecorder = StreamRecorder.create();
        handler.getTask(request, streamRecorder);
        streamRecorder.awaitCompletion(5, TimeUnit.SECONDS);

        assertGrpcError(streamRecorder, Status.Code.NOT_FOUND);
    }

    @Test
    public void testOnCancelTaskSuccess() throws Exception {
        GrpcHandler handler = new GrpcHandler(CARD, requestHandler);
        taskStore.save(MINIMAL_TASK);

        agentExecutorCancel = (context, eventQueue) -> {
            // We need to cancel the task or the EventConsumer never finds a 'final' event.
            // Looking at the Python implementation, they typically use AgentExecutors that
            // don't support cancellation. So my theory is the Agent updates the task to the CANCEL status
            io.a2a.spec.Task task = context.getTask();
            TaskUpdater taskUpdater = new TaskUpdater(context, eventQueue);
            taskUpdater.cancel();
        };

        CancelTaskRequest request = CancelTaskRequest.newBuilder()
                .setName("tasks/" + MINIMAL_TASK.getId())
                .build();
        StreamRecorder<Task> streamRecorder = StreamRecorder.create();
        handler.cancelTask(request, streamRecorder);
        streamRecorder.awaitCompletion(5, TimeUnit.SECONDS);

        assertNull(streamRecorder.getError());
        List<Task> result = streamRecorder.getValues();
        assertNotNull(result);
        assertEquals(1, result.size());
        Task task = result.get(0);
        assertEquals(MINIMAL_TASK.getId(), task.getId());
        assertEquals(MINIMAL_TASK.getContextId(), task.getContextId());
        assertEquals(TaskState.TASK_STATE_CANCELLED, task.getStatus().getState());
    }

    @Test
    public void testOnCancelTaskNotSupported() throws Exception {
        GrpcHandler handler = new GrpcHandler(CARD, requestHandler);
        taskStore.save(MINIMAL_TASK);

        agentExecutorCancel = (context, eventQueue) -> {
            throw new UnsupportedOperationError();
        };

        CancelTaskRequest request = CancelTaskRequest.newBuilder()
                .setName("tasks/" + MINIMAL_TASK.getId())
                .build();
        StreamRecorder<Task> streamRecorder = StreamRecorder.create();
        handler.cancelTask(request, streamRecorder);
        streamRecorder.awaitCompletion(5, TimeUnit.SECONDS);

        assertGrpcError(streamRecorder, Status.Code.UNIMPLEMENTED);
    }

    @Test
    public void testOnCancelTaskNotFound() throws Exception {
        GrpcHandler handler = new GrpcHandler(CARD, requestHandler);
        CancelTaskRequest request = CancelTaskRequest.newBuilder()
                .setName("tasks/" + MINIMAL_TASK.getId())
                .build();
        StreamRecorder<Task> streamRecorder = StreamRecorder.create();
        handler.cancelTask(request, streamRecorder);
        streamRecorder.awaitCompletion(5, TimeUnit.SECONDS);

        assertGrpcError(streamRecorder, Status.Code.NOT_FOUND);
    }

    @Test
    public void testOnMessageNewMessageSuccess() throws Exception {
        GrpcHandler handler = new GrpcHandler(CARD, requestHandler);
        agentExecutorExecute = (context, eventQueue) -> {
            eventQueue.enqueueEvent(context.getMessage());
        };

        Message message = Message.newBuilder()
                .setTaskId(MINIMAL_TASK.getId())
                .setContextId(MINIMAL_TASK.getContextId())
                .setMessageId(MESSAGE.getMessageId())
                .setRole(Role.ROLE_AGENT)
                .addContent(Part.newBuilder().setText(((TextPart)MESSAGE.getParts().get(0)).getText()).build())
                .setMetadata(Struct.newBuilder().build())
                .build();
        SendMessageRequest request = SendMessageRequest.newBuilder()
                .setRequest(message)
                .build();
        StreamRecorder<SendMessageResponse> streamRecorder = StreamRecorder.create();
        handler.sendMessage(request, streamRecorder);
        streamRecorder.awaitCompletion(5, TimeUnit.SECONDS);

        assertNull(streamRecorder.getError());
        List<SendMessageResponse> result = streamRecorder.getValues();
        assertNotNull(result);
        assertEquals(1, result.size());
        SendMessageResponse response = result.get(0);
        assertEquals(message, response.getMsg());
    }

    @Test
    public void testOnMessageNewMessageWithExistingTaskSuccess() throws Exception {
        GrpcHandler handler = new GrpcHandler(CARD, requestHandler);
        taskStore.save(MINIMAL_TASK);
        agentExecutorExecute = (context, eventQueue) -> {
            eventQueue.enqueueEvent(context.getMessage());
        };
        Message message = Message.newBuilder()
                .setTaskId(MINIMAL_TASK.getId())
                .setContextId(MINIMAL_TASK.getContextId())
                .setMessageId(MESSAGE.getMessageId())
                .setRole(Role.ROLE_AGENT)
                .addContent(Part.newBuilder().setText(((TextPart)MESSAGE.getParts().get(0)).getText()).build())
                .setMetadata(Struct.newBuilder().build())
                .build();
        SendMessageRequest request = SendMessageRequest.newBuilder()
                .setRequest(message)
                .build();
        StreamRecorder<SendMessageResponse> streamRecorder = StreamRecorder.create();
        handler.sendMessage(request, streamRecorder);
        streamRecorder.awaitCompletion(5, TimeUnit.SECONDS);

        assertNull(streamRecorder.getError());
        List<SendMessageResponse> result = streamRecorder.getValues();
        assertNotNull(result);
        assertEquals(1, result.size());
        SendMessageResponse response = result.get(0);
        assertEquals(message, response.getMsg());
    }

    @Test
    public void testOnMessageError() throws Exception {
        GrpcHandler handler = new GrpcHandler(CARD, requestHandler);
        agentExecutorExecute = (context, eventQueue) -> {
            eventQueue.enqueueEvent(new UnsupportedOperationError());
        };
        Message message = Message.newBuilder()
                .setTaskId(MINIMAL_TASK.getId())
                .setContextId(MINIMAL_TASK.getContextId())
                .setMessageId(MESSAGE.getMessageId())
                .setRole(Role.ROLE_AGENT)
                .addContent(Part.newBuilder().setText(((TextPart)MESSAGE.getParts().get(0)).getText()).build())
                .setMetadata(Struct.newBuilder().build())
                .build();
        SendMessageRequest request = SendMessageRequest.newBuilder()
                .setRequest(message)
                .build();
        StreamRecorder<SendMessageResponse> streamRecorder = StreamRecorder.create();
        handler.sendMessage(request, streamRecorder);
        streamRecorder.awaitCompletion(5, TimeUnit.SECONDS);

        assertGrpcError(streamRecorder, Status.Code.UNIMPLEMENTED);
    }

    private <V> void assertGrpcError(StreamRecorder<V> streamRecorder, Status.Code expectedStatusCode) {
        assertNotNull(streamRecorder.getError());
        assertInstanceOf(StatusRuntimeException.class, streamRecorder.getError());
        assertEquals(expectedStatusCode, ((StatusRuntimeException) streamRecorder.getError()).getStatus().getCode());
        assertTrue(streamRecorder.getValues().isEmpty());
    }
}
