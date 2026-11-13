package org.a2aproject.sdk.extras.opentelemetry.client;

import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.ERROR_TYPE;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.EXTRACT_REQUEST_SYS_PROPERTY;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.EXTRACT_RESPONSE_SYS_PROPERTY;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.GENAI_REQUEST;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.GENAI_RESPONSE;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.GENAI_STREAMING_EVENT;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.STREAMING_END_SUFFIX;
import static org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames.STREAMING_EVENT_SUFFIX;
import static org.a2aproject.sdk.extras.opentelemetry.OtelSpanMocks.mockClosingSpanBuilder;
import static org.a2aproject.sdk.extras.opentelemetry.OtelSpanMocks.mockEventSpanBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportCall;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.A2AMethods;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetExtendedAgentCardParams;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.ObserveTaskParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenTelemetryClientTransportTest {

    @Mock
    private ClientTransport delegate;

    @Mock
    private Tracer tracer;

    @Mock
    private SpanBuilder spanBuilder;

    @Mock
    private Span span;

    @Mock
    private Scope scope;

    @Mock
    private SpanContext spanContext;

    @Mock
    private ClientCallContext context;

    private OpenTelemetryClientTransport transport;

    @BeforeEach
    void setUp() {
        System.setProperty(EXTRACT_REQUEST_SYS_PROPERTY, "true");
        System.setProperty(EXTRACT_RESPONSE_SYS_PROPERTY, "true");
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.setSpanKind(any(SpanKind.class))).thenReturn(spanBuilder);
        when(spanBuilder.setAttribute(anyString(), anyString())).thenReturn(spanBuilder);
        when(spanBuilder.setAttribute(anyString(), anyLong())).thenReturn(spanBuilder);
        when(spanBuilder.addLink(any(SpanContext.class))).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);
        when(span.getSpanContext()).thenReturn(spanContext);
        when(context.getHeaders()).thenReturn(new java.util.HashMap<>());

        transport = new OpenTelemetryClientTransport(delegate, tracer);
    }


    @Test
    void testSendMessage_Success() throws A2AClientException {
        MessageSendParams request = mock(MessageSendParams.class);
        EventKind expectedResult = mock(EventKind.class);
        when(request.toString()).thenReturn("request-string");
        when(expectedResult.toString()).thenReturn("response-string");
        when(delegate.sendMessage(eq(request), any(ClientCallContext.class))).thenReturn(expectedResult);

        EventKind result = transport.sendMessage(request, context);

        assertEquals(expectedResult, result);
        verify(tracer).spanBuilder(A2AMethods.SEND_MESSAGE_METHOD);
        verify(spanBuilder).setSpanKind(SpanKind.CLIENT);
        verify(spanBuilder).setAttribute(GENAI_REQUEST, "request-string");
        verify(span).setAttribute(GENAI_RESPONSE, "response-string");
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
        verify(scope).close();
    }

    @Test
    void forwardsPhase2OperationsAndPreservesResultEnvelopes() {
        VersionedClientTransport versionedDelegate = mock(VersionedClientTransport.class);
        when(versionedDelegate.protocolVersion()).thenReturn("1.1");
        when(versionedDelegate.capabilities()).thenReturn(Set.of());
        OpenTelemetryClientTransport versionedTransport = new OpenTelemetryClientTransport(versionedDelegate, tracer);
        ClientCallContext callContext = new ClientCallContext(Map.of("tenant", "acme"), Map.of("Authorization", "Bearer token"));
        SpiCallOptions options = new SpiCallOptions(Instant.now().plusSeconds(5), 4, 1024);
        SendInputParams input = new SendInputParams(
                Message.builder().messageId("input").role(Message.Role.ROLE_USER).parts(List.of(new TextPart("hello"))).build(),
                List.of(), null, null, "acme");
        ObserveTaskParams observeParams = new ObserveTaskParams("task", 2L, "acme");
        TaskQueryParams taskParams = new TaskQueryParams("task", null, "acme");
        ListTasksParams listParams = new ListTasksParams(null, null, null, null, null, null, null, "acme");

        TransportResult<EventKind> sendEnvelope = new TransportResult<>(
                Message.builder().messageId("result").role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("done"))).build(), List.of());
        TransportCall<TransportResult<EventKind>> sendCall = TransportCall.create(() -> { });
        when(versionedDelegate.sendInput(eq(input), eq(options), any(ClientCallContext.class))).thenReturn(sendCall);
        TransportCall<TransportResult<Task>> getCall = TransportCall.create(() -> { });
        Task task = new Task("task", "context", new org.a2aproject.sdk.spec.TaskStatus(
                org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING), null, null, null);
        TransportResult<Task> taskEnvelope = new TransportResult<>(task,
                List.of(new TaskDecodeEvidence("task", List.of(3L), true)));
        when(versionedDelegate.getTaskAsync(eq(taskParams), eq(options), any(ClientCallContext.class))).thenReturn(getCall);

        TransportObservation stream = TransportObservation.create(options, () -> { });
        TransportObservation observation = TransportObservation.create(options, () -> { });
        when(versionedDelegate.streamInput(eq(input), eq(options), any(ClientCallContext.class))).thenReturn(stream);
        when(versionedDelegate.observe(eq(observeParams), eq(options), any(ClientCallContext.class))).thenReturn(observation);
        when(versionedDelegate.getTaskDecoded(eq(taskParams), any(ClientCallContext.class))).thenReturn(taskEnvelope);
        TransportResult<ListTasksResult> listEnvelope = new TransportResult<>(new ListTasksResult(List.of()), List.of());
        when(versionedDelegate.listTasksDecoded(eq(listParams), any(ClientCallContext.class))).thenReturn(listEnvelope);

        TransportCall<TransportResult<EventKind>> tracedSendCall = versionedTransport.sendInput(input, options, callContext);
        assertSame(stream, versionedTransport.streamInput(input, options, callContext));
        assertSame(observation, versionedTransport.observe(observeParams, options, callContext));
        TransportCall<TransportResult<Task>> tracedGetCall = versionedTransport.getTaskAsync(taskParams, options, callContext);
        assertSame(taskEnvelope, versionedTransport.getTaskDecoded(taskParams, callContext));
        assertSame(listEnvelope, versionedTransport.listTasksDecoded(listParams, callContext));

        sendCall.complete(sendEnvelope);
        getCall.complete(taskEnvelope);
        assertSame(sendEnvelope, tracedSendCall.result().toCompletableFuture().join());
        assertSame(taskEnvelope, tracedGetCall.result().toCompletableFuture().join());
        stream.complete();
        observation.complete();

        verify(versionedDelegate).sendInput(eq(input), eq(options), any(ClientCallContext.class));
        verify(versionedDelegate).streamInput(eq(input), eq(options), any(ClientCallContext.class));
        verify(versionedDelegate).observe(eq(observeParams), eq(options), any(ClientCallContext.class));
        verify(versionedDelegate).getTaskAsync(eq(taskParams), eq(options), any(ClientCallContext.class));
        verify(versionedDelegate).getTaskDecoded(eq(taskParams), any(ClientCallContext.class));
        verify(versionedDelegate).listTasksDecoded(eq(listParams), any(ClientCallContext.class));
        verify(span, org.mockito.Mockito.times(6)).end();
    }

    @Test
    void asyncOperationSpanClosesOnCancellationAndFailure() {
        VersionedClientTransport versionedDelegate = mock(VersionedClientTransport.class);
        when(versionedDelegate.protocolVersion()).thenReturn("1.1");
        when(versionedDelegate.capabilities()).thenReturn(Set.of());
        OpenTelemetryClientTransport versionedTransport = new OpenTelemetryClientTransport(versionedDelegate, tracer);
        ClientCallContext callContext = new ClientCallContext(Map.of(), Map.of());
        SpiCallOptions options = SpiCallOptions.defaults();
        TaskQueryParams params = new TaskQueryParams("task");
        AtomicInteger cancelCount = new AtomicInteger();
        TransportCall<TransportResult<Task>> pending = TransportCall.create(cancelCount::incrementAndGet);
        TransportCall<TransportResult<Task>> failing = TransportCall.create(() -> { });
        when(versionedDelegate.getTaskAsync(eq(params), eq(options), any(ClientCallContext.class))).thenReturn(pending, failing);

        TransportCall<TransportResult<Task>> traced = versionedTransport.getTaskAsync(params, options, callContext);
        traced.cancel();

        assertEquals(1, cancelCount.get());
        assertThrows(CompletionException.class, () -> traced.result().toCompletableFuture().join());

        RuntimeException failure = new RuntimeException("read failed");
        TransportCall<TransportResult<Task>> tracedFailure = versionedTransport.getTaskAsync(params, options, callContext);
        failing.fail(failure);
        assertThrows(CompletionException.class, () -> tracedFailure.result().toCompletableFuture().join());
        verify(span, org.mockito.Mockito.times(2)).end();
    }

    @Test
    void observationSpanClosesOnCancellationAndFailure() {
        VersionedClientTransport versionedDelegate = mock(VersionedClientTransport.class);
        when(versionedDelegate.protocolVersion()).thenReturn("1.1");
        when(versionedDelegate.capabilities()).thenReturn(Set.of());
        OpenTelemetryClientTransport versionedTransport = new OpenTelemetryClientTransport(versionedDelegate, tracer);
        ClientCallContext callContext = new ClientCallContext(Map.of(), Map.of());
        SpiCallOptions options = SpiCallOptions.defaults();
        ObserveTaskParams params = new ObserveTaskParams("task", null, null);
        TransportObservation cancelled = TransportObservation.create(options, () -> { });
        TransportObservation failed = TransportObservation.create(options, () -> { });
        when(versionedDelegate.observe(eq(params), eq(options), any(ClientCallContext.class)))
                .thenReturn(cancelled, failed);

        versionedTransport.observe(params, options, callContext).cancel();
        versionedTransport.observe(params, options, callContext);
        failed.fail(new RuntimeException("observation failed"));

        verify(span, org.mockito.Mockito.times(2)).end();
    }

    @Test
    void testSendMessage_NullResponse() throws A2AClientException {
        MessageSendParams request = mock(MessageSendParams.class);
        when(request.toString()).thenReturn("request-string");
        when(delegate.sendMessage(eq(request), any(ClientCallContext.class))).thenReturn(null);

        EventKind result = transport.sendMessage(request, context);

        assertNull(result);
        verify(spanBuilder).setAttribute(GENAI_REQUEST, "request-string");
        verify(spanBuilder, never()).setAttribute(eq(GENAI_RESPONSE), anyString());
        verify(span, never()).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void testSendMessage_ThrowsException() throws A2AClientException {
        MessageSendParams request = mock(MessageSendParams.class);
        when(request.toString()).thenReturn("request-string");
        A2AClientException expectedException = new A2AClientException("Test error");
        when(delegate.sendMessage(eq(request), any(ClientCallContext.class))).thenThrow(expectedException);

        A2AClientException exception = assertThrows(A2AClientException.class,
                () -> transport.sendMessage(request, context));

        assertEquals(expectedException, exception);
        verify(span).setStatus(StatusCode.ERROR, "Test error");
        verify(span).end();
        verify(scope).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSendMessageStreaming() throws A2AClientException {
        MessageSendParams request = mock(MessageSendParams.class);
        when(request.toString()).thenReturn("request-string");
        Consumer<StreamingEventKind> eventConsumer = mock(Consumer.class);
        Consumer<Throwable> errorConsumer = mock(Consumer.class);

        transport.sendMessageStreaming(request, eventConsumer, errorConsumer, context);

        verify(tracer).spanBuilder(A2AMethods.SEND_STREAMING_MESSAGE_METHOD);
        verify(spanBuilder).setSpanKind(SpanKind.CLIENT);
        verify(spanBuilder).setAttribute(GENAI_REQUEST, "request-string");
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();

        ArgumentCaptor<Consumer<StreamingEventKind>> eventConsumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<Throwable>> errorConsumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(delegate).sendMessageStreaming(eq(request), eventConsumerCaptor.capture(),
                errorConsumerCaptor.capture(), any(ClientCallContext.class));

        assertNotNull(eventConsumerCaptor.getValue());
        assertNotNull(errorConsumerCaptor.getValue());
    }

    @Test
    void testGetTask_Success() throws A2AClientException {
        TaskQueryParams request = mock(TaskQueryParams.class);
        Task expectedResult = mock(Task.class);
        when(request.toString()).thenReturn("request-string");
        when(expectedResult.toString()).thenReturn("response-string");
        when(delegate.getTask(eq(request), any(ClientCallContext.class))).thenReturn(expectedResult);

        Task result = transport.getTask(request, context);

        assertEquals(expectedResult, result);
        verify(tracer).spanBuilder(A2AMethods.GET_TASK_METHOD);
        verify(spanBuilder).setAttribute(GENAI_REQUEST, "request-string");
        verify(span).setAttribute(GENAI_RESPONSE, "response-string");
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void testCancelTask_Success() throws A2AClientException {
        CancelTaskParams request = mock(CancelTaskParams.class);
        Task expectedResult = mock(Task.class);
        when(request.toString()).thenReturn("request-string");
        when(expectedResult.toString()).thenReturn("response-string");
        when(delegate.cancelTask(eq(request), any(ClientCallContext.class))).thenReturn(expectedResult);

        Task result = transport.cancelTask(request, context);

        assertEquals(expectedResult, result);
        verify(tracer).spanBuilder(A2AMethods.CANCEL_TASK_METHOD);
        verify(spanBuilder).setAttribute(GENAI_REQUEST, "request-string");
        verify(span).setAttribute(GENAI_RESPONSE, "response-string");
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void testListTasks_Success() throws A2AClientException {
        ListTasksParams request = mock(ListTasksParams.class);
        ListTasksResult expectedResult = mock(ListTasksResult.class);
        when(request.toString()).thenReturn("request-string");
        when(expectedResult.toString()).thenReturn("response-string");
        when(delegate.listTasks(eq(request), any(ClientCallContext.class))).thenReturn(expectedResult);

        ListTasksResult result = transport.listTasks(request, context);

        assertEquals(expectedResult, result);
        verify(tracer).spanBuilder(A2AMethods.LIST_TASK_METHOD);
        verify(spanBuilder).setAttribute(GENAI_REQUEST, "request-string");
        verify(span).setAttribute(GENAI_RESPONSE, "response-string");
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void testCreateTaskPushNotificationConfiguration_Success() throws A2AClientException {
        TaskPushNotificationConfig request = mock(TaskPushNotificationConfig.class);
        TaskPushNotificationConfig expectedResult = mock(TaskPushNotificationConfig.class);
        when(request.toString()).thenReturn("request-string");
        when(expectedResult.toString()).thenReturn("response-string");
        when(delegate.createTaskPushNotificationConfiguration(eq(request), any(ClientCallContext.class))).thenReturn(expectedResult);

        TaskPushNotificationConfig result = transport.createTaskPushNotificationConfiguration(request, context);

        assertEquals(expectedResult, result);
        verify(tracer).spanBuilder(A2AMethods.SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
        verify(spanBuilder).setAttribute(GENAI_REQUEST, "request-string");
        verify(span).setAttribute(GENAI_RESPONSE, "response-string");
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void testGetTaskPushNotificationConfiguration_Success() throws A2AClientException {
        GetTaskPushNotificationConfigParams request = mock(GetTaskPushNotificationConfigParams.class);
        TaskPushNotificationConfig expectedResult = mock(TaskPushNotificationConfig.class);
        when(request.toString()).thenReturn("request-string");
        when(expectedResult.toString()).thenReturn("response-string");
        when(delegate.getTaskPushNotificationConfiguration(eq(request), any(ClientCallContext.class))).thenReturn(expectedResult);

        TaskPushNotificationConfig result = transport.getTaskPushNotificationConfiguration(request, context);

        assertEquals(expectedResult, result);
        verify(tracer).spanBuilder(A2AMethods.GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
        verify(spanBuilder).setAttribute(GENAI_REQUEST, "request-string");
        verify(span).setAttribute(GENAI_RESPONSE, "response-string");
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void testListTaskPushNotificationConfigurations_Success() throws A2AClientException {
        ListTaskPushNotificationConfigsParams request = mock(ListTaskPushNotificationConfigsParams.class);
        TaskPushNotificationConfig config1 = mock(TaskPushNotificationConfig.class);
        TaskPushNotificationConfig config2 = mock(TaskPushNotificationConfig.class);
        when(config1.toString()).thenReturn("config1");
        when(config2.toString()).thenReturn("config2");
        ListTaskPushNotificationConfigsResult expectedResult = new ListTaskPushNotificationConfigsResult(List.of(config1, config2));
        when(request.toString()).thenReturn("request-string");
        when(delegate.listTaskPushNotificationConfigurations(eq(request), any(ClientCallContext.class))).thenReturn(expectedResult);

        ListTaskPushNotificationConfigsResult result = transport.listTaskPushNotificationConfigurations(request, context);

        assertEquals(expectedResult, result);
        verify(tracer).spanBuilder(A2AMethods.LIST_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
        verify(spanBuilder).setSpanKind(SpanKind.CLIENT);
        verify(spanBuilder).setAttribute(GENAI_REQUEST, "request-string");
        verify(span).setAttribute(GENAI_RESPONSE, "config1,config2");
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void testDeleteTaskPushNotificationConfigurations_Success() throws A2AClientException {
        DeleteTaskPushNotificationConfigParams request = mock(DeleteTaskPushNotificationConfigParams.class);
        when(request.toString()).thenReturn("request-string");
        doNothing().when(delegate).deleteTaskPushNotificationConfigurations(eq(request), any(ClientCallContext.class));

        transport.deleteTaskPushNotificationConfigurations(request, context);

        verify(tracer).spanBuilder(A2AMethods.DELETE_TASK_PUSH_NOTIFICATION_CONFIG_METHOD);
        verify(spanBuilder).setAttribute(GENAI_REQUEST, "request-string");
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
        verify(delegate).deleteTaskPushNotificationConfigurations(eq(request), any(ClientCallContext.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSubscribeToTask() throws A2AClientException {
        TaskIdParams request = mock(TaskIdParams.class);
        when(request.toString()).thenReturn("request-string");
        Consumer<StreamingEventKind> eventConsumer = mock(Consumer.class);
        Consumer<Throwable> errorConsumer = mock(Consumer.class);

        transport.subscribeToTask(request, eventConsumer, errorConsumer, context);

        verify(tracer).spanBuilder(A2AMethods.SUBSCRIBE_TO_TASK_METHOD);
        verify(spanBuilder).setSpanKind(SpanKind.CLIENT);
        verify(spanBuilder).setAttribute(GENAI_REQUEST, "request-string");
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();

        ArgumentCaptor<Consumer<StreamingEventKind>> eventConsumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<Throwable>> errorConsumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(delegate).subscribeToTask(eq(request), eventConsumerCaptor.capture(),
                errorConsumerCaptor.capture(), any(ClientCallContext.class));

        assertNotNull(eventConsumerCaptor.getValue());
        assertNotNull(errorConsumerCaptor.getValue());
    }

    @Test
    void testGetAgentCard_Success() throws A2AClientException {
        AgentCard expectedResult = mock(AgentCard.class);
        when(expectedResult.toString()).thenReturn("response-string");
        when(delegate.getExtendedAgentCard(any(GetExtendedAgentCardParams.class), any(ClientCallContext.class))).thenReturn(expectedResult);

       GetExtendedAgentCardParams params = mock(GetExtendedAgentCardParams.class);
        AgentCard result = transport.getExtendedAgentCard(params, context);

        assertEquals(expectedResult, result);
        verify(tracer).spanBuilder(A2AMethods.GET_EXTENDED_AGENT_CARD_METHOD);
        verify(spanBuilder).setSpanKind(SpanKind.CLIENT);
        verify(span).setAttribute(GENAI_RESPONSE, "response-string");
        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void testGetAgentCard_NullResponse() throws A2AClientException {
        when(delegate.getExtendedAgentCard(any(GetExtendedAgentCardParams.class), any(ClientCallContext.class))).thenReturn(null);

        GetExtendedAgentCardParams params = mock(GetExtendedAgentCardParams.class);
        AgentCard result = transport.getExtendedAgentCard(params, context);

        assertNull(result);
        verify(tracer).spanBuilder(A2AMethods.GET_EXTENDED_AGENT_CARD_METHOD);
        verify(spanBuilder).setSpanKind(SpanKind.CLIENT);
        verify(span, never()).setAttribute(eq(GENAI_RESPONSE), anyString());
        verify(span, never()).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void testClose() {
        transport.close();
        verify(delegate).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testEventConsumer_ThroughSendMessageStreaming() throws A2AClientException {
        MessageSendParams request = mock(MessageSendParams.class);
        when(request.toString()).thenReturn("request-string");

        Span eventSpan = mock(Span.class);
        SpanBuilder eventSpanBuilder = mockEventSpanBuilder(tracer,
                A2AMethods.SEND_STREAMING_MESSAGE_METHOD + STREAMING_EVENT_SUFFIX, eventSpan);

        Span closingSpan = mock(Span.class);
        mockClosingSpanBuilder(tracer, A2AMethods.SEND_STREAMING_MESSAGE_METHOD + STREAMING_END_SUFFIX, closingSpan);

        ArgumentCaptor<Consumer<StreamingEventKind>> eventConsumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        Consumer<StreamingEventKind> originalConsumer = mock(Consumer.class);

        transport.sendMessageStreaming(request, originalConsumer, mock(Consumer.class), context);

        verify(delegate).sendMessageStreaming(eq(request), eventConsumerCaptor.capture(), any(Consumer.class), any(ClientCallContext.class));

        Message event = Message.builder()
                .messageId("test-id")
                .taskId("task-id")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("test content")))
                .build();

        eventConsumerCaptor.getValue().accept(event);

        verify(tracer).spanBuilder(A2AMethods.SEND_STREAMING_MESSAGE_METHOD + STREAMING_EVENT_SUFFIX);
        verify(eventSpanBuilder).addLink(spanContext);
        verify(eventSpanBuilder).setAttribute(GENAI_STREAMING_EVENT, event.kind());
        verify(eventSpan).end();
        verify(originalConsumer).accept(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testErrorConsumer_ThroughSendMessageStreaming() throws A2AClientException {
        MessageSendParams request = mock(MessageSendParams.class);
        when(request.toString()).thenReturn("request-string");

        Span closingSpan = mock(Span.class);
        mockClosingSpanBuilder(tracer, A2AMethods.SEND_STREAMING_MESSAGE_METHOD + STREAMING_END_SUFFIX, closingSpan);

        ArgumentCaptor<Consumer<Throwable>> errorConsumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        Consumer<Throwable> originalConsumer = mock(Consumer.class);

        transport.sendMessageStreaming(request, mock(Consumer.class), originalConsumer, context);

        verify(delegate).sendMessageStreaming(eq(request), any(Consumer.class), errorConsumerCaptor.capture(), any(ClientCallContext.class));

        Throwable error = new RuntimeException("Test error");
        errorConsumerCaptor.getValue().accept(error);

        verify(closingSpan).setAttribute(ERROR_TYPE, RuntimeException.class.getName());
        verify(closingSpan).setStatus(StatusCode.ERROR, "Test error");
        verify(closingSpan, atLeastOnce()).end();
        verify(originalConsumer).accept(error);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTerminalConsumer_WithNullThrowable_CreatesOkClosingSpan() throws A2AClientException {
        MessageSendParams request = mock(MessageSendParams.class);
        when(request.toString()).thenReturn("request-string");

        Span closingSpan = mock(Span.class);
        mockClosingSpanBuilder(tracer, A2AMethods.SEND_STREAMING_MESSAGE_METHOD + STREAMING_END_SUFFIX, closingSpan);

        ArgumentCaptor<Consumer<Throwable>> terminalConsumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        Consumer<Throwable> originalConsumer = mock(Consumer.class);

        transport.sendMessageStreaming(request, mock(Consumer.class), originalConsumer, context);

        verify(delegate).sendMessageStreaming(eq(request), any(Consumer.class), terminalConsumerCaptor.capture(), any(ClientCallContext.class));

        terminalConsumerCaptor.getValue().accept(null);

        verify(closingSpan).setStatus(StatusCode.OK);
        verify(closingSpan).end();
        verify(originalConsumer).accept(null);
    }

    @Test
    void testDeleteTaskPushNotificationConfigurations_ThrowsException() throws A2AClientException {
        DeleteTaskPushNotificationConfigParams request = mock(DeleteTaskPushNotificationConfigParams.class);
        when(request.toString()).thenReturn("request-string");
        A2AClientException expectedException = new A2AClientException("Delete failed");
        doThrow(expectedException).when(delegate).deleteTaskPushNotificationConfigurations(eq(request), any(ClientCallContext.class));

        A2AClientException exception = assertThrows(A2AClientException.class,
                () -> transport.deleteTaskPushNotificationConfigurations(request, context));

        assertEquals(expectedException, exception);
        verify(span).setStatus(StatusCode.ERROR, "Delete failed");
        verify(span).end();
    }

    @Test
    void testResubscribe_ThrowsException() throws A2AClientException {
        TaskIdParams request = mock(TaskIdParams.class);
        when(request.toString()).thenReturn("request-string");
        Consumer<StreamingEventKind> eventConsumer = mock(Consumer.class);
        Consumer<Throwable> errorConsumer = mock(Consumer.class);
        A2AClientException expectedException = new A2AClientException("Resubscribe failed");
        doThrow(expectedException).when(delegate).subscribeToTask(any(TaskIdParams.class), any(Consumer.class),
                any(Consumer.class), any(ClientCallContext.class));

        A2AClientException exception = assertThrows(A2AClientException.class,
                () -> transport.subscribeToTask(request, eventConsumer, errorConsumer, context));

        assertEquals(expectedException, exception);
        verify(span).setStatus(StatusCode.ERROR, "Resubscribe failed");
        verify(span).end();
    }

    @Nested
    class MetricsTests {

        @Mock
        private Meter meter;

        @Mock
        private DoubleHistogramBuilder histogramBuilder;

        @Mock
        private DoubleHistogram histogram;

        @BeforeEach
        void setUpMeter() {
            lenient().when(meter.histogramBuilder(anyString())).thenReturn(histogramBuilder);
            lenient().when(histogramBuilder.setUnit(anyString())).thenReturn(histogramBuilder);
            lenient().when(histogramBuilder.setDescription(anyString())).thenReturn(histogramBuilder);
            lenient().when(histogramBuilder.build()).thenReturn(histogram);
        }

        @Test
        void sendMessage_recordsOperationDuration() throws A2AClientException {
            OpenTelemetryClientTransport transportWithMeter = new OpenTelemetryClientTransport(delegate, tracer, meter);
            MessageSendParams request = mock(MessageSendParams.class);
            EventKind expectedResult = mock(EventKind.class);
            when(request.toString()).thenReturn("request-string");
            when(expectedResult.toString()).thenReturn("response-string");
            when(delegate.sendMessage(eq(request), any(ClientCallContext.class))).thenReturn(expectedResult);

            transportWithMeter.sendMessage(request, context);

            verify(histogram).record(anyDouble(), any(io.opentelemetry.api.common.Attributes.class));
        }

        @Test
        void sendMessage_noHistogramWhenNoMeter() throws A2AClientException {
            // transport without meter — should not throw, histogram never invoked
            MessageSendParams request = mock(MessageSendParams.class);
            EventKind expectedResult = mock(EventKind.class);
            when(request.toString()).thenReturn("request-string");
            when(expectedResult.toString()).thenReturn("response-string");
            when(delegate.sendMessage(eq(request), any(ClientCallContext.class))).thenReturn(expectedResult);

            transport.sendMessage(request, context);

            verify(histogram, never()).record(anyDouble(), any(io.opentelemetry.api.common.Attributes.class));
        }

        @Test
        @SuppressWarnings("unchecked")
        void sendMessageStreaming_recordsFailedOperationDuration() throws A2AClientException {
            OpenTelemetryClientTransport transportWithMeter = new OpenTelemetryClientTransport(delegate, tracer, meter);
            MessageSendParams request = mock(MessageSendParams.class);
            A2AClientException expectedException = new A2AClientException("Streaming failed");
            doThrow(expectedException).when(delegate).sendMessageStreaming(any(MessageSendParams.class), any(Consumer.class),
                    any(Consumer.class), any(ClientCallContext.class));

            assertThrows(A2AClientException.class,
                    () -> transportWithMeter.sendMessageStreaming(request, mock(Consumer.class), mock(Consumer.class), context));

            verify(histogram).record(anyDouble(), any(io.opentelemetry.api.common.Attributes.class));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void closingSpan_notCreatedDuringStreamingInitiation() throws A2AClientException {
        MessageSendParams request = mock(MessageSendParams.class);
        when(request.toString()).thenReturn("request-string");

        transport.sendMessageStreaming(request, mock(Consumer.class), mock(Consumer.class), context);

        verify(tracer, never()).spanBuilder(A2AMethods.SEND_STREAMING_MESSAGE_METHOD + STREAMING_END_SUFFIX);
    }

    @Test
    void testSendMessageStreaming_ThrowsException() throws A2AClientException {
        MessageSendParams request = mock(MessageSendParams.class);
        when(request.toString()).thenReturn("request-string");
        Consumer<StreamingEventKind> eventConsumer = mock(Consumer.class);
        Consumer<Throwable> errorConsumer = mock(Consumer.class);
        A2AClientException expectedException = new A2AClientException("Streaming failed");
        doThrow(expectedException).when(delegate).sendMessageStreaming(any(MessageSendParams.class), any(Consumer.class),
                any(Consumer.class), any(ClientCallContext.class));

        A2AClientException exception = assertThrows(A2AClientException.class,
                () -> transport.sendMessageStreaming(request, eventConsumer, errorConsumer, context));

        assertEquals(expectedException, exception);
        verify(span).setStatus(StatusCode.ERROR, "Streaming failed");
        verify(span).end();
    }
}
