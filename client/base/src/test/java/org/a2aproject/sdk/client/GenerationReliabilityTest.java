package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.ObserveTaskParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class GenerationReliabilityTest {

    private static final String TASK = "task";
    private static final String CTX = "ctx";

    private static Task task(long generation) {
        return Task.builder().id(TASK).contextId(CTX).status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .generation(generation).history(List.of()).artifacts(List.of()).timeline(List.of()).build();
    }

    private static TaskStatusUpdateEvent status(long generation) {
        return new TaskStatusUpdateEvent(TASK, new TaskStatus(TaskState.TASK_STATE_WORKING), CTX, null, generation);
    }

    private static TransportFrame frame(org.a2aproject.sdk.spec.StreamingEventKind event) {
        return new TransportFrame(event, false, List.of(), null, 1);
    }

    private static TransportResult<EventKind> unary(Task task) {
        return new TransportResult<>(task, List.of(new TaskDecodeEvidence(task.id(), List.of(), false)));
    }

    private static SendInputParams guarded(String taskId, Long guard) {
        Message message = Message.builder().role(Message.Role.ROLE_USER).parts(new TextPart("go")).taskId(taskId)
                .contextId(CTX).build();
        return SendInputParams.builder().message(message)
                .configuration(MessageSendConfiguration.builder().ifGenerationMatch(guard).build()).build();
    }

    private static AgentCard card() {
        return AgentCard.builder().name("n").description("d").version("v")
                .capabilities(AgentCapabilities.builder().build()).defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text")).skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost", null, "1.1"))).build();
    }

    @Test
    void generationZeroTaintsAndRejectsLaterGuardBeforeAnyIo() {
        GenerationReliability reliability = new GenerationReliability();
        ClientTaskReducer reducer = new ClientTaskReducer();

        TaskView view = reliability.reduceFrame(reducer, frame(task(0)));

        assertTrue(reliability.isTainted(TASK));
        assertEquals(ViewSync.INCOMPLETE, view.sync());
        A2AClientException rejected = assertThrows(A2AClientException.class,
                () -> reliability.requireGuardAllowed(guarded(TASK, 4L)));
        assertTrue(rejected.getMessage().contains(TASK));
    }

    @Test
    void consecutiveEqualEventGenerationsTaint() {
        GenerationReliability reliability = new GenerationReliability();
        ClientTaskReducer reducer = new ClientTaskReducer();

        reliability.reduceFrame(reducer, frame(task(5)));
        reliability.reduceFrame(reducer, frame(status(6)));
        assertFalse(reliability.isTainted(TASK));
        reliability.reduceFrame(reducer, frame(status(6)));

        assertTrue(reliability.isTainted(TASK));
        assertThrows(A2AClientException.class, () -> reliability.requireGuardAllowed(guarded(TASK, 6L)));
    }

    @Test
    void taintSurvivesSnapshotsAndIsCarriedIntoLaterReducers() {
        GenerationReliability reliability = new GenerationReliability();
        reliability.reduceFrame(new ClientTaskReducer(), frame(task(0)));

        ClientTaskReducer reconnect = new ClientTaskReducer();
        TaskView view = reliability.reduceFrame(reconnect, frame(task(9)));

        assertEquals(ViewSync.INCOMPLETE, view.sync(), "a fresh snapshot must not reset the taint");
        assertFalse(reconnect.generationReliable());
        assertTrue(reliability.isTainted(TASK));
        assertThrows(A2AClientException.class, () -> reliability.requireGuardAllowed(guarded(TASK, 9L)));
    }

    @Test
    void guardOnReliableOrOtherTaskAndGuardlessInputAreNotRejected() {
        GenerationReliability reliability = new GenerationReliability();
        reliability.reduceFrame(new ClientTaskReducer(), frame(task(0)));

        assertDoesNotThrow(() -> reliability.requireGuardAllowed(guarded("other", 3L)));
        SendInputParams unguarded = SendInputParams.builder().message(Message.builder()
                .role(Message.Role.ROLE_USER).parts(new TextPart("go")).taskId(TASK).contextId(CTX).build()).build();
        assertDoesNotThrow(() -> reliability.requireGuardAllowed(unguarded));
        assertDoesNotThrow(() -> new GenerationReliability().requireGuardAllowed(guarded(TASK, 3L)));
    }

    @Test
    void unaryResultsRetainKnownReliabilityAcrossCalls() {
        GenerationReliability reliability = new GenerationReliability();

        reliability.reduceUnary(new ClientTaskReducer(), unary(task(7)));
        reliability.reduceUnary(new ClientTaskReducer(), unary(task(8)));
        assertFalse(reliability.isTainted(TASK));
        reliability.reduceUnary(new ClientTaskReducer(), unary(task(8)));

        assertTrue(reliability.isTainted(TASK));
        // a later call with a fresh reducer and an advancing generation does not reset it
        TaskView view = reliability.reduceUnary(new ClientTaskReducer(), unary(task(20)));
        assertEquals(ViewSync.INCOMPLETE, view.sync());
        assertThrows(A2AClientException.class, () -> reliability.requireGuardAllowed(guarded(TASK, 20L)));
    }

    @Test
    void guardTargetingATaintedTaskThroughArtifactUpdatesIsRejectedLocally() {
        GenerationReliability reliability = new GenerationReliability();
        reliability.reduceFrame(new ClientTaskReducer(), frame(task(0)));
        TaskArtifactUpdateEvent artifactInput = new TaskArtifactUpdateEvent(TASK,
                Artifact.builder().artifactId("a").parts(List.of(new TextPart("x"))).build(), CTX, null, null, null, 0);
        SendInputParams guardedArtifact = SendInputParams.builder().artifactUpdates(List.of(artifactInput))
                .configuration(MessageSendConfiguration.builder().ifGenerationMatch(4L).build()).build();

        assertThrows(A2AClientException.class, () -> reliability.requireGuardAllowed(guardedArtifact));
        assertDoesNotThrow(() -> new GenerationReliability().requireGuardAllowed(guardedArtifact));
    }

    @Test
    void unaryGenerationZeroTaintsAndDirectMessageHasNoTaskEffect() {
        GenerationReliability reliability = new GenerationReliability();
        Message message = Message.builder().role(Message.Role.ROLE_AGENT).parts(new TextPart("hi")).build();

        TaskView messageView = reliability.reduceUnary(new ClientTaskReducer(),
                new TransportResult<EventKind>(message, List.of()));
        assertNull(messageView.task());
        assertEquals(ViewSync.NO_BASELINE, messageView.sync());

        reliability.reduceUnary(new ClientTaskReducer(), unary(task(0)));
        assertTrue(reliability.isTainted(TASK));
    }

    @Test
    void sourceWithoutSkippedEvidenceMarksCoverageNotAssertedAndBaselineIncomplete() {
        GenerationReliability reliability = new GenerationReliability();
        ClientTaskReducer withBaseline = new ClientTaskReducer();

        TaskView baseline = reliability.reduceEventWithoutEvidence(withBaseline, task(3));
        assertEquals(ViewSync.INCOMPLETE, baseline.sync());
        assertTrue(baseline.history().flags().contains(HistoryCoverage.Flag.NOT_ASSERTED));
        assertTrue(baseline.timeline().flags().contains(HistoryCoverage.Flag.NOT_ASSERTED));
        assertFalse(baseline.timeline().flags().contains(HistoryCoverage.Flag.UNKNOWN_SKIPPED));
        TaskView next = reliability.reduceEventWithoutEvidence(withBaseline, status(4));
        assertEquals(4L, next.throughGeneration(), "known events are still reduced, not treated as a gap");

        ClientTaskReducer suffix = new ClientTaskReducer();
        TaskView noBaseline = reliability.reduceEventWithoutEvidence(suffix, status(11));
        assertEquals(ViewSync.NO_BASELINE, noBaseline.sync());
        assertTrue(noBaseline.timeline().flags().contains(HistoryCoverage.Flag.NOT_ASSERTED));
    }

    @Test
    void sourceWithoutEvidenceStillTaintsOnGenerationZero() {
        GenerationReliability reliability = new GenerationReliability();
        reliability.reduceEventWithoutEvidence(new ClientTaskReducer(), status(0));
        assertTrue(reliability.isTainted(TASK));
    }

    @Test
    void policyAppliesGuardRejectionOnlyToSelectedOneOneAndRejectsCursors() throws Exception {
        GenerationReliability reliability = new GenerationReliability();
        reliability.reduceFrame(new ClientTaskReducer(), frame(task(0)));
        SendInputParams guard = guarded(TASK, 2L);

        assertThrows(A2AClientException.class,
                () -> ClientInputPolicy.validateSend("1.1", guard, card(), reliability));
        // explicit 1.0 keeps its own historical rejection of guards; the registry is not consulted
        A2AClientException legacy = assertThrows(A2AClientException.class,
                () -> ClientInputPolicy.validateSend("1.0", guard, card(), reliability));
        assertTrue(legacy.getMessage().contains("require protocol version 1.1"));

        ClientInputPolicy.validateObserve(new ObserveTaskParams(TASK, null, null));
        assertThrows(A2AClientException.class,
                () -> ClientInputPolicy.validateObserve(new ObserveTaskParams(TASK, 3L, null)));
    }
}
