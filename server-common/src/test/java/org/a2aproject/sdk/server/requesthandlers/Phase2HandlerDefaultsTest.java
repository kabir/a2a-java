package org.a2aproject.sdk.server.requesthandlers;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.Flow;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.ObserveTaskParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class Phase2HandlerDefaultsTest {
    private static final Message MESSAGE = Message.builder().role(Message.Role.ROLE_USER)
            .parts(new TextPart("test")).build();

    @Test
    void messageOnlyCompanionDelegatesToLegacyHandlerWithTenantAndContext() throws Exception {
        RequestHandler handler = mock(RequestHandler.class, CALLS_REAL_METHODS);
        ServerCallContext context = mock(ServerCallContext.class);
        SendInputParams input = new SendInputParams(MESSAGE, List.of(), null, null, "tenant-a");
        EventKind expected = MESSAGE;
        doReturn(expected).when(handler).onMessageSend(new MessageSendParams(MESSAGE, null, null, "tenant-a"), context);

        assertSame(expected, handler.onSendInput(input, context));

        verify(handler).onMessageSend(new MessageSendParams(MESSAGE, null, null, "tenant-a"), context);
    }

    @Test
    void messageOnlyStreamingCompanionDelegatesToLegacyHandler() throws Exception {
        RequestHandler handler = mock(RequestHandler.class, CALLS_REAL_METHODS);
        ServerCallContext context = mock(ServerCallContext.class);
        SendInputParams input = new SendInputParams(MESSAGE, List.of(), null, null, null);
        Flow.Publisher<org.a2aproject.sdk.spec.StreamingEventKind> expected = subscriber -> { };
        doReturn(expected).when(handler).onMessageSendStream(new MessageSendParams(MESSAGE, null, null, null), context);

        assertSame(expected, handler.onSendInputStream(input, context));

        verify(handler).onMessageSendStream(new MessageSendParams(MESSAGE, null, null, null), context);
    }

    @Test
    void inputWithGuardOrArtifactIsRejectedBeforeLegacyHandler() {
        RequestHandler handler = mock(RequestHandler.class, CALLS_REAL_METHODS);
        ServerCallContext context = mock(ServerCallContext.class);
        SendInputParams guarded = new SendInputParams(MESSAGE, List.of(),
                MessageSendConfiguration.builder().ifGenerationMatch(4L).build(), null, null);

        assertThrows(UnsupportedOperationError.class, () -> handler.onSendInput(guarded, context));

        TaskArtifactUpdateEvent update = mock(TaskArtifactUpdateEvent.class);
        doReturn("task-1").when(update).taskId();
        doReturn("context-1").when(update).contextId();
        SendInputParams artifactOnly = SendInputParams.builder().artifactUpdates(List.of(update)).build();
        assertThrows(UnsupportedOperationError.class, () -> handler.onSendInput(artifactOnly, context));
        verify(handler, never()).onMessageSend(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void subscriptionsDelegateOnlyWithoutCursorAndPreserveTenant() throws Exception {
        RequestHandler handler = mock(RequestHandler.class, CALLS_REAL_METHODS);
        ServerCallContext context = mock(ServerCallContext.class);
        Flow.Publisher<org.a2aproject.sdk.spec.StreamingEventKind> expected = subscriber -> { };
        doReturn(expected).when(handler).onSubscribeToTask(new TaskIdParams("task-1", "tenant-a"), context);

        assertSame(expected, handler.onObserveTask(new ObserveTaskParams("task-1", null, "tenant-a"), context));
        assertThrows(UnsupportedOperationError.class,
                () -> handler.onObserveTask(new ObserveTaskParams("task-1", 8L, "tenant-a"), context));
        verify(handler).onSubscribeToTask(new TaskIdParams("task-1", "tenant-a"), context);
    }
}
