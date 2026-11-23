package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.client.HistoryCoverage.Flag;
import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.spec.A2AClientInvalidArgsError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.Test;

class ClientTaskReducerTest {

    private static final String TASK = "task-1";
    private static final String CTX = "ctx-1";

    private static Message message(String id, Message.Role role) {
        return Message.builder().messageId(id).role(role).parts(new TextPart(id)).taskId(TASK).contextId(CTX)
                .build();
    }

    private static Task snapshot(long generation, TaskState state, Message statusMessage) {
        return Task.builder().id(TASK).contextId(CTX)
                .status(new TaskStatus(state, statusMessage, null))
                .generation(generation).history(List.of()).artifacts(List.of()).timeline(List.of()).build();
    }

    private static TaskStatusUpdateEvent status(long generation, TaskState state, Message statusMessage,
            Map<String, Object> metadata) {
        return new TaskStatusUpdateEvent(TASK, new TaskStatus(state, statusMessage, null), CTX, metadata,
                generation);
    }

    private static TaskMessageUpdateEvent messageUpdate(long generation, Message message) {
        return new TaskMessageUpdateEvent(TASK, CTX, message, null, generation);
    }

    private static TaskArtifactUpdateEvent artifact(long generation, String id, String text, boolean append,
            long start, long end) {
        Artifact artifact = Artifact.builder().artifactId(id).parts(new TextPart(text))
                .startGeneration(start).endGeneration(end).role(Message.Role.ROLE_AGENT).build();
        return new TaskArtifactUpdateEvent(TASK, artifact, CTX, append, null, null, generation);
    }

    private static TransportFrame frame(org.a2aproject.sdk.spec.StreamingEventKind event, List<Long> skipped) {
        return new TransportFrame(event, !skipped.isEmpty(), skipped, null, 10);
    }

    // -- snapshots -----------------------------------------------------------------------------------------

    @Test
    void snapshotEstablishesSynchronizedWindowOnlyStateAndNeverFullHistory() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        TaskView view = reducer.applySnapshot(snapshot(5, TaskState.TASK_STATE_WORKING, null), null, 10, 20);

        assertEquals(ViewSync.SYNCHRONIZED, view.sync());
        assertEquals(5L, view.throughGeneration());
        assertTrue(view.history().flags().contains(Flag.WINDOW_ONLY));
        assertTrue(view.timeline().flags().contains(Flag.WINDOW_ONLY));
        assertEquals(10, view.history().requestedLimit());
        assertEquals(20, view.timeline().requestedLimit());
        assertEquals(5L, view.task().generation());
    }

    @Test
    void historyAndTimelineLimitsAreIndependentForZeroFiniteAndOmitted() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        TaskView zeroHistory = reducer.applySnapshot(snapshot(2, TaskState.TASK_STATE_WORKING, null), null, 0, null);
        assertEquals(0, zeroHistory.history().requestedLimit());
        assertNull(zeroHistory.timeline().requestedLimit());
        assertEquals(ViewSync.SYNCHRONIZED, zeroHistory.sync()); // a zero limit does not unsynchronize

        TaskView zeroTimeline = reducer.applySnapshot(snapshot(3, TaskState.TASK_STATE_WORKING, null), null, null, 0);
        assertNull(zeroTimeline.history().requestedLimit());
        assertEquals(0, zeroTimeline.timeline().requestedLimit());
        assertEquals(ViewSync.SYNCHRONIZED, zeroTimeline.sync());
    }

    @Test
    void laterSnapshotReplacesWindowsAndDoesNotMergeOlderEntries() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        Message first = message("m1", Message.Role.ROLE_USER);
        Task withHistory = Task.builder(snapshot(4, TaskState.TASK_STATE_WORKING, null)).history(List.of(first))
                .timeline(List.of(new TimelineEntry(4, new TimelineEntry.MessageValue(first)))).build();
        reducer.applySnapshot(withHistory, null, 5, 5);

        TaskView replaced = reducer.applySnapshot(snapshot(9, TaskState.TASK_STATE_WORKING, null), null, 0, 0);
        assertTrue(replaced.task().history().isEmpty());
        assertTrue(replaced.task().timeline().isEmpty());
        assertEquals(9L, replaced.throughGeneration());
        assertEquals(0, replaced.history().requestedLimit());
        assertNull(replaced.timeline().returnedRange());
    }

    @Test
    void timelineCoverageReportsKnownReturnedRangeAndUnknownRangeStaysUnknown() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        Message m = message("m1", Message.Role.ROLE_USER);
        Task task = Task.builder(snapshot(7, TaskState.TASK_STATE_WORKING, null))
                .timeline(List.of(new TimelineEntry(3, new TimelineEntry.MessageValue(m)),
                        new TimelineEntry(7, new TimelineEntry.MessageValue(m)))).build();
        TaskView view = reducer.applySnapshot(task, null, null, 2);
        assertEquals(new HistoryCoverage.Range(3, 7), view.timeline().returnedRange());
        assertNull(view.history().returnedRange());

        TaskView omitted = new ClientTaskReducer().applySnapshot(
                Task.builder(snapshot(7, TaskState.TASK_STATE_WORKING, null)).timeline(null).build(), null, null, null);
        assertNull(omitted.timeline().returnedRange());
        assertTrue(omitted.timeline().flags().contains(Flag.NOT_ASSERTED));
    }

    @Test
    void snapshotWithSkippedEvidenceCannotClearUncertaintyAndMarksUnknownSkipped() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        TaskView view = reducer.applySnapshot(snapshot(6, TaskState.TASK_STATE_WORKING, null),
                new TaskDecodeEvidence(TASK, List.of(4L), false), null, null);
        assertEquals(ViewSync.INCOMPLETE, view.sync());
        assertTrue(view.timeline().flags().contains(Flag.UNKNOWN_SKIPPED));
        assertTrue(view.history().flags().contains(Flag.UNKNOWN_SKIPPED));
        assertEquals(6L, view.throughGeneration());
        assertEquals(ViewSync.INCOMPLETE, reducer.apply(status(7, TaskState.TASK_STATE_WORKING, null, null)).sync());

        TaskView clean = reducer.applySnapshot(snapshot(8, TaskState.TASK_STATE_WORKING, null), null, null, null);
        assertEquals(ViewSync.SYNCHRONIZED, clean.sync());
    }

    // -- contiguous known updates --------------------------------------------------------------------------

    @Test
    void contiguousMessageStatusAndArtifactUpdatesPreserveStampsOriginRangesAndOccurrenceCounts() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_SUBMITTED, null), null, null, null);

        Message user = message("u1", Message.Role.ROLE_USER);
        Message agent = message("a1", Message.Role.ROLE_AGENT);
        reducer.apply(messageUpdate(2, user));
        reducer.apply(artifact(3, "art", "A", false, 3, 3));
        reducer.apply(artifact(4, "art", "B", true, 3, 4));
        reducer.apply(artifact(5, "art", "C", true, 3, 5));
        TaskView view = reducer.apply(status(6, TaskState.TASK_STATE_WORKING, agent, Map.of("k", "v")));

        assertEquals(ViewSync.SYNCHRONIZED, view.sync());
        assertEquals(6L, view.throughGeneration());
        Task task = view.task();
        assertEquals(6L, task.generation());
        assertEquals(List.of(user), task.history());
        assertEquals(agent, task.status().message());
        assertEquals("v", task.metadata().get("k"));
        assertEquals(1, task.artifacts().size());
        Artifact art = task.artifacts().get(0);
        assertEquals(3, art.startGeneration());
        assertEquals(5, art.endGeneration());
        assertEquals(Message.Role.ROLE_AGENT, art.role());
        assertEquals(List.of("A", "B", "C"), art.parts().stream().map(p -> ((TextPart) p).text()).toList());
        assertEquals(List.of(2L, 6L), task.timeline().stream().map(TimelineEntry::generation).toList());
        assertTrue(reducer.generationReliable());
    }

    @Test
    void statusWithNewMessageMovesPreviousStatusMessageToHistoryExactlyOnce() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        Message s1 = message("s1", Message.Role.ROLE_AGENT);
        Message s2 = message("s2", Message.Role.ROLE_AGENT);
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, s1), null, null, null);
        TaskView view = reducer.apply(status(2, TaskState.TASK_STATE_WORKING, s2, null));
        assertEquals(List.of(s1), view.task().history());
        assertEquals(s2, view.task().status().message());
    }

    @Test
    void metadataOnlyStatusCarryingSameMessageIdentityIsNotANewOccurrence() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        Message s1 = message("s1", Message.Role.ROLE_AGENT);
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, s1), null, null, null);
        TaskView view = reducer.apply(status(2, TaskState.TASK_STATE_WORKING, s1, Map.of("progress", 1)));
        assertTrue(view.task().history().isEmpty(), "same-identity carried message must not enter history");
        assertEquals(s1, view.task().status().message());
        assertEquals(1, view.task().metadata().get("progress"));
        assertEquals(2L, view.throughGeneration());
    }

    @Test
    void messageUpdateFoldsStatusMessageThenAppendsAndCountsOccurrencesOnce() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        Message s1 = message("s1", Message.Role.ROLE_AGENT);
        Message u1 = message("u1", Message.Role.ROLE_USER);
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_INPUT_REQUIRED, s1), null, null, null);
        TaskView view = reducer.apply(messageUpdate(2, u1));
        assertEquals(List.of(s1, u1), view.task().history());
        assertNull(view.task().status().message());
    }

    // -- no baseline ---------------------------------------------------------------------------------------

    @Test
    void noBaselineSuffixStaysNoBaselineWithPartialTaskAndNotAssertedCoverage() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        Message agent = message("a1", Message.Role.ROLE_AGENT);
        TaskView view = reducer.apply(status(7, TaskState.TASK_STATE_WORKING, agent, null));
        view = reducer.apply(artifact(8, "art", "X", false, 8, 8));

        assertEquals(ViewSync.NO_BASELINE, view.sync());
        assertNull(view.throughGeneration());
        assertTrue(view.history().flags().contains(Flag.NOT_ASSERTED));
        assertTrue(view.timeline().flags().contains(Flag.NOT_ASSERTED));
        assertFalse(view.history().flags().contains(Flag.WINDOW_ONLY));
        assertNotNull(view.task());
        assertEquals(TASK, view.task().id());
        assertEquals(1, view.task().artifacts().size());
    }

    @Test
    void directMessageProducesNoTaskAndNoBaseline() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        TaskView view = reducer.apply(message("m", Message.Role.ROLE_AGENT));
        assertNull(view.task());
        assertEquals(ViewSync.NO_BASELINE, view.sync());
        assertNull(view.throughGeneration());
    }

    @Test
    void noBaselineAppendWithoutEarlierChunkIsRetainedAsSuffixOnly() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        TaskView view = reducer.apply(artifact(9, "art", "tail", true, 4, 9));
        assertEquals(ViewSync.NO_BASELINE, view.sync());
        assertEquals(4, view.task().artifacts().get(0).startGeneration());
    }

    // -- gaps, unknown, stale ------------------------------------------------------------------------------

    @Test
    void generationGapFreezesLastSafeStateAndSnapshotRestoresSynchronization() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, null, null);
        Task safe = reducer.apply(artifact(2, "art", "A", false, 2, 2)).task();

        TaskView gap = reducer.apply(artifact(4, "art", "C", true, 2, 4));
        assertEquals(ViewSync.INCOMPLETE, gap.sync());
        assertEquals(2L, gap.throughGeneration());
        assertSame(safe, gap.task());
        assertTrue(gap.timeline().flags().contains(Flag.NOT_ASSERTED));

        TaskView still = reducer.apply(status(5, TaskState.TASK_STATE_WORKING, null, null));
        assertEquals(ViewSync.INCOMPLETE, still.sync());
        assertSame(safe, still.task());

        TaskView resynced = reducer.applySnapshot(snapshot(6, TaskState.TASK_STATE_WORKING, null), null, null, null);
        assertEquals(ViewSync.SYNCHRONIZED, resynced.sync());
        assertEquals(6L, resynced.throughGeneration());
    }

    @Test
    void unknownTimelineKindWithKnownGenerationCreatesNoFalseGapButMarksSemanticUncertainty() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, null, null);
        TaskView applied = reducer.apply(artifact(2, "art", "A", false, 2, 2));
        assertEquals(ViewSync.SYNCHRONIZED, applied.sync());

        // generation 3 was a skipped unknown timeline kind; the known event at 4 carries that evidence
        TaskView around = reducer.applyFrame(frame(status(4, TaskState.TASK_STATE_WORKING, null, null), List.of(3L)));
        assertEquals(ViewSync.INCOMPLETE, around.sync());
        assertEquals(2L, around.throughGeneration(), "as-of state does not move past an uninterpreted entry");
        assertTrue(around.timeline().flags().contains(Flag.UNKNOWN_SKIPPED));
        assertFalse(around.timeline().flags().contains(Flag.NOT_ASSERTED), "no fabricated missing-generation gap");

        TaskView next = reducer.apply(status(5, TaskState.TASK_STATE_WORKING, null, null));
        assertFalse(next.timeline().flags().contains(Flag.NOT_ASSERTED), "sequence stays contiguous through 5");
        assertEquals(ViewSync.INCOMPLETE, next.sync());
        assertEquals(2L, next.throughGeneration());
    }

    @Test
    void unknownStreamVariantWithKnownGenerationInIgnoredFrameCountsForSequenceAccounting() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, null, null);
        TaskView ignored = reducer.applyFrame(new TransportFrame(null, true, List.of(2L), null, 5));
        assertEquals(ViewSync.INCOMPLETE, ignored.sync());
        TaskView after = reducer.apply(status(3, TaskState.TASK_STATE_WORKING, null, null));
        assertFalse(after.timeline().flags().contains(Flag.NOT_ASSERTED));
        assertEquals(1L, after.throughGeneration());
    }

    @Test
    void unknownStreamVariantWithoutGenerationCanLeaveARealGap() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, null, null);
        reducer.applyFrame(new TransportFrame(null, true, List.of(), null, 5));
        TaskView after = reducer.apply(status(3, TaskState.TASK_STATE_WORKING, null, null));
        assertEquals(ViewSync.INCOMPLETE, after.sync());
        assertTrue(after.timeline().flags().contains(Flag.NOT_ASSERTED), "generation 2 is unaccounted: real gap");
        assertEquals(1L, after.throughGeneration());
    }

    @Test
    void unknownFrameWithoutBaselineStaysNoBaseline() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        TaskView view = reducer.applyFrame(new TransportFrame(null, true, List.of(), null, 5));
        assertEquals(ViewSync.NO_BASELINE, view.sync());
        assertTrue(view.timeline().flags().contains(Flag.UNKNOWN_SKIPPED));
    }

    @Test
    void lateStaleAndDuplicateEventsDoNotAppendHistoryOrChunksAgain() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, null, null);
        Message user = message("u1", Message.Role.ROLE_USER);
        reducer.apply(messageUpdate(2, user));
        reducer.apply(artifact(3, "art", "A", false, 3, 3));
        reducer.apply(artifact(4, "art", "B", true, 3, 4));
        Task before = reducer.view().task();

        TaskView stale = reducer.apply(messageUpdate(2, user));
        stale = reducer.apply(artifact(4, "art", "B", true, 3, 4));
        stale = reducer.apply(artifact(3, "art", "A", false, 3, 3));
        assertEquals(before, stale.task());
        assertEquals(1, stale.task().history().size());
        assertEquals(2, stale.task().artifacts().get(0).parts().size());
        assertEquals(4L, stale.throughGeneration());
    }

    @Test
    void staleSnapshotOlderThanObservedStateIsIgnored() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(5, TaskState.TASK_STATE_WORKING, null), null, null, null);
        TaskView view = reducer.applySnapshot(snapshot(3, TaskState.TASK_STATE_SUBMITTED, null), null, null, null);
        assertEquals(5L, view.throughGeneration());
        assertEquals(TaskState.TASK_STATE_WORKING, view.task().status().state());
    }

    @Test
    void appendToMissingEarlierChunkAfterBaselineIsAGapNotAFabricatedChunk() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, null, null);
        TaskView view = reducer.apply(artifact(2, "missing", "tail", true, 1, 2));
        assertEquals(ViewSync.INCOMPLETE, view.sync());
        assertTrue(view.task().artifacts().isEmpty());
        assertEquals(1L, view.throughGeneration());
    }

    // -- invalid generation signals -------------------------------------------------------------------------

    @Test
    void generationZeroDisablesReliabilityAndKeepsLastSafeState() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, null, null);
        reducer.apply(artifact(2, "art", "A", false, 2, 2));
        Task safe = reducer.view().task();

        TaskView view = reducer.apply(status(0, TaskState.TASK_STATE_WORKING, null, null));
        assertFalse(reducer.generationReliable());
        assertEquals(ViewSync.INCOMPLETE, view.sync());
        assertSame(safe, view.task());
        assertEquals(2L, view.throughGeneration());
    }

    @Test
    void consecutiveEqualGenerationsDisableReliabilityEvenThoughStateIsDeduplicated() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, null, null);
        Message user = message("u1", Message.Role.ROLE_USER);
        reducer.apply(messageUpdate(2, user));
        assertTrue(reducer.generationReliable());

        TaskView view = reducer.apply(messageUpdate(2, user));
        assertFalse(reducer.generationReliable());
        assertEquals(1, view.task().history().size(), "state application is still deduplicated");
        assertEquals(ViewSync.INCOMPLETE, view.sync());
    }

    @Test
    void nonConsecutiveRepeatedGenerationIsNotAnEqualConsecutiveSignal() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, null, null);
        reducer.apply(artifact(2, "a", "A", false, 2, 2));
        reducer.apply(artifact(3, "b", "B", false, 3, 3));
        reducer.apply(artifact(2, "a", "A", false, 2, 2)); // late stale, but not equal to the immediately previous
        assertTrue(reducer.generationReliable());
    }

    @Test
    void taintSurvivesLaterSnapshotsAndSnapshotsDoNotCountAsEqualEvents() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(3, TaskState.TASK_STATE_WORKING, null), null, null, null);
        reducer.applySnapshot(snapshot(3, TaskState.TASK_STATE_WORKING, null), null, null, null);
        assertTrue(reducer.generationReliable(), "repeated read snapshots are not stream events");

        reducer.apply(status(0, TaskState.TASK_STATE_WORKING, null, null));
        assertFalse(reducer.generationReliable());
        TaskView view = reducer.applySnapshot(snapshot(9, TaskState.TASK_STATE_WORKING, null), null, null, null);
        assertFalse(reducer.generationReliable(), "a snapshot never resets the taint");
        assertEquals(ViewSync.INCOMPLETE, view.sync());
        assertEquals(9L, view.throughGeneration());
    }

    @Test
    void zeroGenerationSnapshotCannotProvideAGenerationGuarantee() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        TaskView view = reducer.applySnapshot(snapshot(0, TaskState.TASK_STATE_WORKING, null), null, null, null);
        assertNull(view.throughGeneration());
        assertEquals(ViewSync.INCOMPLETE, view.sync());
    }

    // -- legacy / immutability / identity --------------------------------------------------------------------

    @Test
    void legacyGapsOnlyWhenEvidenceSaysSo() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        TaskView plain = reducer.applySnapshot(snapshot(2, TaskState.TASK_STATE_WORKING, null), null, null, null);
        assertFalse(plain.history().flags().contains(Flag.LEGACY_GAPS));
        assertFalse(plain.timeline().flags().contains(Flag.LEGACY_GAPS));

        TaskView legacy = reducer.recordLegacyGaps();
        assertTrue(legacy.history().flags().contains(Flag.LEGACY_GAPS));
        assertTrue(legacy.timeline().flags().contains(Flag.LEGACY_GAPS));
        TaskView later = reducer.applySnapshot(snapshot(3, TaskState.TASK_STATE_WORKING, null), null, null, null);
        assertTrue(later.history().flags().contains(Flag.LEGACY_GAPS));
    }

    @Test
    void viewsAreImmutableSnapshotsOfReducerState() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        TaskView first = reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, 1, 1);
        reducer.apply(artifact(2, "art", "A", false, 2, 2));
        reducer.apply(status(4, TaskState.TASK_STATE_WORKING, null, null));

        assertEquals(1L, first.throughGeneration());
        assertEquals(ViewSync.SYNCHRONIZED, first.sync());
        assertTrue(first.task().artifacts().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> first.history().flags().add(Flag.LEGACY_GAPS));
        assertThrows(UnsupportedOperationException.class, () -> first.task().history().add(
                message("x", Message.Role.ROLE_USER)));
    }

    @Test
    void differentTaskIdentityIsRejectedLocally() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, null, null);
        TaskStatusUpdateEvent other = new TaskStatusUpdateEvent("other", new TaskStatus(TaskState.TASK_STATE_WORKING),
                CTX, null, 2);
        assertThrows(A2AClientInvalidArgsError.class, () -> reducer.apply(other));
    }

    @Test
    void streamErrorFramesDoNotChangeTheView() {
        ClientTaskReducer reducer = new ClientTaskReducer();
        TaskView before = reducer.applySnapshot(snapshot(1, TaskState.TASK_STATE_WORKING, null), null, null, null);
        TaskView after = reducer.applyFrame(new TransportFrame(null, false, List.of(),
                new org.a2aproject.sdk.spec.StreamError(null, "boom", null, null, null), 1));
        assertEquals(before, after);
    }

    @Test
    void streamTaskEventsReplaceStateUsingTheStreamWindowLimits() {
        ClientTaskReducer reducer = new ClientTaskReducer(3, 0);
        reducer.apply(status(3, TaskState.TASK_STATE_WORKING, null, null));
        TaskView view = reducer.apply(snapshot(5, TaskState.TASK_STATE_COMPLETED, null));
        assertEquals(ViewSync.SYNCHRONIZED, view.sync());
        assertEquals(5L, view.throughGeneration());
        assertEquals(3, view.history().requestedLimit());
        assertEquals(0, view.timeline().requestedLimit());
    }
}
