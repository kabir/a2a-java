package org.a2aproject.sdk.extras.queuemanager.replicated.mp_reactive;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.enterprise.event.Event;

import org.a2aproject.sdk.extras.queuemanager.replicated.core.ReplicatedEventQueueItem;
import org.a2aproject.sdk.extras.queuemanager.replicated.core.ReplicationStrategy;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReactiveMessagingReplicationStrategyTest {

    @Mock
    private Emitter<String> emitter;

    @Mock
    private Event<ReplicatedEventQueueItem> cdiEvent;

    @InjectMocks
    private ReactiveMessagingReplicationStrategy strategy;

    private StreamingEventKind testEvent;

    @BeforeEach
    public void setUp() {
        testEvent = TaskStatusUpdateEvent.builder()
                .taskId("test-task")
                .contextId("test-context")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                .build();
    }

    private String createValidJsonMessage(String taskId, String contextId) throws Exception {
        // Create a proper ReplicatedEventQueueItem JSON with StreamingEventKind
        TaskStatusUpdateEvent event = TaskStatusUpdateEvent.builder()
                .taskId(taskId)
                .contextId(contextId)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .build();
        ReplicatedEventQueueItem replicatedEvent = new ReplicatedEventQueueItem(taskId, event);
        return JsonUtil.toJson(replicatedEvent);
    }

    @Test
    public void testDeclaresCommittedReplicationSupport() {
        assertTrue(strategy.supportsCommittedReplication());
    }

    @Test
    public void testSendCallsEmitter() {
        String taskId = "test-task-123";

        strategy.send(taskId, testEvent);

        // Verify that emitter.send was called (don't care about exact JSON format)
        verify(emitter).send(any(String.class));
    }

    @Test
    public void testSendPropagatesEmitterExceptions() {
        String taskId = "test-task-456";
        RuntimeException emitterException = new RuntimeException("Failed to send replicated event");

        doThrow(emitterException).when(emitter).send(any(String.class));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                strategy.send(taskId, testEvent)
        );

        // The implementation wraps the original exception, so check the cause
        assertEquals(emitterException, exception.getCause());
        assertEquals("Failed to send replicated event", exception.getMessage());
    }

    @Test
    public void testOnReplicatedEventWithValidJson() throws Exception {
        String validJsonMessage = createValidJsonMessage("test-task-101", "test-context");

        // Should not throw - valid JSON should be handled gracefully
        assertDoesNotThrow(() -> strategy.onReplicatedEvent(validJsonMessage));

        // Note: We test the actual CDI firing in integration tests
        // Unit tests here focus on error handling and basic flow
    }


    @Test
    public void testOnReplicatedEventHandlesInvalidJson() {
        String invalidJsonMessage = "invalid-json";

        // Should not throw - invalid JSON should be handled gracefully
        assertDoesNotThrow(() -> strategy.onReplicatedEvent(invalidJsonMessage));

        // CDI event should not be fired for invalid JSON
        verify(cdiEvent, never()).fire(any(ReplicatedEventQueueItem.class));
    }



    @Test
    public void testSendCommittedEnvelopeEmitsOneRecordThatRoundTripsWithEpochAndBatch() throws Exception {
        org.a2aproject.sdk.spec.Task task = org.a2aproject.sdk.spec.Task.builder().id("batch-task").contextId("ctx")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).generation(2).build();
        org.a2aproject.sdk.server.events.CommittedTaskBatch batch = new org.a2aproject.sdk.server.events.CommittedTaskBatch(
                new org.a2aproject.sdk.server.tasks.TaskSnapshot(task, new org.a2aproject.sdk.server.tasks.TaskRevision(5)),
                java.util.List.of(TaskStatusUpdateEvent.builder().taskId("batch-task").contextId("ctx")
                        .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).generation(2).build()));

        strategy.send(ReplicatedEventQueueItem.committed(batch, 9L, "node-a"));

        org.mockito.ArgumentCaptor<String> sent = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emitter).send(sent.capture());
        ReplicatedEventQueueItem received = JsonUtil.fromJson(sent.getValue(), ReplicatedEventQueueItem.class);
        assertEquals(9L, received.getWriterEpoch());
        assertEquals(batch.identity(), received.committedBatch().identity());

        // The receiving path fires the same CDI event; an envelope reaches observers unchanged.
        strategy.onReplicatedEvent(sent.getValue());
        verify(cdiEvent).fire(any(ReplicatedEventQueueItem.class));
    }

    @Test
    public void testLegacyStrategyWithoutEnvelopeSupportRejectsEnvelopesExplicitly() {
        ReplicationStrategy legacy = (taskId, event) -> { };
        assertThrows(UnsupportedOperationException.class, () -> legacy.send(
                ReplicatedEventQueueItem.stampedClosed("t", 1L, "o", null)));
    }
}
