package org.a2aproject.sdk.server.events;

import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_COMPLETED;
import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_INPUT_REQUIRED;
import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskManager;
import org.a2aproject.sdk.server.tasks.TaskMutationAuthority;
import org.a2aproject.sdk.server.tasks.TaskMutationPlanner;
import org.a2aproject.sdk.server.tasks.TaskRevision;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.server.version.ServerVersionPolicy;
import org.a2aproject.sdk.server.version.VersionedOutputProjector;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Bounded detection of lost or stranded publications. Time is injected: every test drives
 * {@link PublicationReconciler#tick()} directly and moves a fake nano clock, so no test sleeps.
 */
class PublicationGapRecoveryTest {

    private static final String TASK_ID = "gap-task";
    private static final String CONTEXT_ID = "gap-context";
    private static final long START_NANOS = 1_000_000_000_000L;

    // ---- lost terminal publication, stranded watermark with no later mutation ----

    @Test
    void lostTerminalPublicationIsDetectedWithoutAnyLaterMutationAndOnlyForTheAffectedObserver() {
        try (Fixture fixture = new Fixture()) {
            Observer stranded = fixture.observe(4);
            Observer healthy = fixture.observe(6);
            fixture.verifyScheduledEveryFiveSeconds();

            // Generations 5 and 6 (terminal) committed, but their publication was lost: no later mutation follows.
            fixture.tick(0);
            assertEquals(1, fixture.head.reads.size(), "one authority read per task per tick, not per observer");
            fixture.head.answer(6);
            assertNull(stranded.observation().failureReason(), "first sighting only starts the clock");

            fixture.tick(5);
            fixture.head.answer(6);
            assertNull(stranded.observation().failureReason(), "5s after the sighting is below the 10s gap deadline");
            assertEquals(2, fixture.head.reads.size());

            fixture.tick(5);
            fixture.head.answer(6);
            assertEquals(PublicationReconciler.FailureReason.STRANDED_WATERMARK,
                    stranded.observation().failureReason());
            List<EventQueueItem> failed = drain(stranded.child());
            assertEquals(1, failed.size());
            InternalError error = assertInstanceOf(InternalError.class, failed.get(0).getEvent());
            assertTrue(error.getMessage().contains("call GetTask to resynchronize"), error.getMessage());
            assertTrue(stranded.child().isClosed());

            assertNull(healthy.observation().failureReason(), "an observer that is up to date is untouched");
            assertFalse(healthy.child().isClosed());
            assertEquals(1, fixture.reconciler.activeObservationCount());
            assertTrue(fixture.reconciler.isScheduled());
            healthy.child().close(true);
            assertEquals(0, fixture.reconciler.activeTaskCount());
            assertFalse(fixture.reconciler.isScheduled());
            verify(fixture.scheduled).cancel(false);
        }
    }

    @Test
    void strandedWatermarkThatAdvancesIsNotFailed() {
        try (Fixture fixture = new Fixture()) {
            Observer observer = fixture.observe(4);
            fixture.tick(0);
            fixture.head.answer(5);
            fixture.publish(item(5, TASK_STATE_WORKING));
            fixture.tick(5);
            fixture.head.answer(5);
            fixture.tick(5);
            fixture.head.answer(5);
            fixture.tick(60);
            fixture.head.answer(5);

            assertNull(observer.observation().failureReason());
            assertEquals(List.of(5L), generations(drain(observer.child())));
        }
    }

    // ---- absent intermediate event ----

    @Test
    void absentIntermediateEventHoldsLaterOnesAndReleasesThemInOrderWhenItArrives() {
        try (Fixture fixture = new Fixture()) {
            Observer observer = fixture.observe(4);
            fixture.publish(item(6, TASK_STATE_WORKING));
            assertEquals(List.of(), generations(drain(observer.child())), "generation 6 waits for generation 5");
            assertEquals(1, observer.observation().bufferedEvents());
            assertEquals(4, observer.observation().watermark());

            fixture.tick(3);
            assertNull(observer.observation().failureReason());
            fixture.publish(item(5, TASK_STATE_INPUT_REQUIRED));

            assertEquals(6, observer.observation().watermark());
            assertEquals(List.of(5L, 6L), generations(drain(observer.child())));
            assertEquals(0, observer.observation().bufferedEvents(), "consumed events release their budget");
        }
    }

    @Test
    void absentIntermediateEventClosesTheObservationAtTheReorderGapDeadline() {
        try (Fixture fixture = new Fixture()) {
            Observer observer = fixture.observe(4);
            fixture.publish(item(6, TASK_STATE_WORKING));

            fixture.tick(5);
            assertNull(observer.observation().failureReason(), "5s is below the 10s reorder-gap deadline");
            fixture.tick(5);

            assertEquals(PublicationReconciler.FailureReason.REORDER_GAP_DEADLINE,
                    observer.observation().failureReason());
            assertInstanceOf(InternalError.class, drain(observer.child()).get(0).getEvent());
            assertTrue(fixture.head.last().isCancelled(), "the unanswered authority read is abandoned");
            assertEquals(0, fixture.reconciler.activeTaskCount());
        }
    }

    // ---- authority read failure and deadline ----

    @Test
    void failedAuthorityReadClosesTheObservation() {
        try (Fixture fixture = new Fixture()) {
            Observer observer = fixture.observe(4);
            fixture.tick(0);
            fixture.head.fail(new IllegalStateException("store unavailable"));

            assertEquals(PublicationReconciler.FailureReason.AUTHORITY_READ_FAILED,
                    observer.observation().failureReason());
            assertInstanceOf(InternalError.class, drain(observer.child()).get(0).getEvent());
            assertEquals(0, fixture.reconciler.activeObservationCount());
        }
    }

    @Test
    void authorityReadThatStartsWithAnExceptionClosesTheObservation() {
        try (Fixture fixture = new Fixture()) {
            fixture.head.throwOnRead = new IllegalStateException("cannot start");
            Observer observer = fixture.observe(4);
            fixture.tick(0);

            assertEquals(PublicationReconciler.FailureReason.AUTHORITY_READ_FAILED,
                    observer.observation().failureReason());
        }
    }

    @Test
    void authorityReadThatNeverCompletesIsAbandonedAtItsDeadlineAndALateAnswerIsIgnored() {
        try (Fixture fixture = new Fixture()) {
            Observer observer = fixture.observe(4);
            fixture.tick(0);
            CompletableFuture<OptionalLong> read = fixture.head.last();

            fixture.tick(3);
            assertNull(observer.observation().failureReason(), "still within the 5s read deadline");
            assertEquals(1, fixture.head.reads.size(), "an unfinished read is not duplicated");

            fixture.tick(2);
            assertEquals(PublicationReconciler.FailureReason.AUTHORITY_READ_DEADLINE,
                    observer.observation().failureReason());
            assertTrue(read.isCancelled());
            assertFalse(read.complete(OptionalLong.of(9)), "a late completion cannot revive the read");
            assertEquals(1, drain(observer.child()).size());
        }
    }

    // ---- buffer limits ----

    @Test
    void bufferOfMoreThan256EventsClosesOnlyThatObservationAndPurgesItsQueue() {
        try (Fixture fixture = new Fixture()) {
            Observer overflowing = fixture.observe(0);
            Observer other = fixture.observe(0);
            for (int generation = 1; generation <= 256; generation++) {
                fixture.publish(item(generation, TASK_STATE_WORKING));
                drain(other.child());
            }
            assertEquals(256, overflowing.observation().bufferedEvents());
            assertNull(overflowing.observation().failureReason(), "256 events is exactly the limit");

            fixture.publish(item(257, TASK_STATE_WORKING));

            assertEquals(PublicationReconciler.FailureReason.BUFFER_OVERFLOW,
                    overflowing.observation().failureReason());
            List<EventQueueItem> remaining = drain(overflowing.child());
            assertEquals(1, remaining.size(), "undelivered events are purged and only the failure remains");
            assertInstanceOf(InternalError.class, remaining.get(0).getEvent());
            assertNull(other.observation().failureReason(), "an observer that keeps consuming is unaffected");
        }
    }

    @Test
    void bufferOfMoreThanFourMebibytesClosesTheObservation() {
        try (Fixture fixture = new Fixture(PublicationReconciler.Limits.DEFAULTS, event -> 1024L * 1024)) {
            Observer observer = fixture.observe(0);
            for (int generation = 1; generation <= 4; generation++) {
                fixture.publish(item(generation, TASK_STATE_WORKING));
            }
            assertNull(observer.observation().failureReason(), "4 MiB is exactly the limit");
            assertEquals(4L * 1024 * 1024, observer.observation().bufferedBytes());

            fixture.publish(item(5, TASK_STATE_WORKING));

            assertEquals(PublicationReconciler.FailureReason.BUFFER_OVERFLOW,
                    observer.observation().failureReason());
            assertEquals(1, drain(observer.child()).size());
            assertEquals(0, observer.observation().bufferedBytes());
        }
    }

    @Test
    void heldEventsCountAgainstTheBufferToo() {
        PublicationReconciler.Limits small = new PublicationReconciler.Limits(Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofSeconds(10), 3, 4L * 1024 * 1024);
        try (Fixture fixture = new Fixture(small, event -> 10L)) {
            Observer observer = fixture.observe(0);
            fixture.publish(item(2, TASK_STATE_WORKING));
            fixture.publish(item(3, TASK_STATE_WORKING));
            fixture.publish(item(4, TASK_STATE_WORKING));
            assertNull(observer.observation().failureReason());

            fixture.publish(item(5, TASK_STATE_WORKING));

            assertEquals(PublicationReconciler.FailureReason.BUFFER_OVERFLOW,
                    observer.observation().failureReason());
        }
    }

    @Test
    void redeliveredHeldGenerationAtTheBufferLimitDoesNotOverflow() {
        PublicationReconciler.Limits small = new PublicationReconciler.Limits(Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofSeconds(10), 3, 4L * 1024 * 1024);
        try (Fixture fixture = new Fixture(small, event -> 10L)) {
            Observer observer = fixture.observe(0);
            fixture.publish(item(2, TASK_STATE_WORKING));
            fixture.publish(item(3, TASK_STATE_WORKING));
            fixture.publish(item(4, TASK_STATE_WORKING));
            fixture.publish(item(3, TASK_STATE_WORKING));

            assertNull(observer.observation().failureReason(), "a duplicate grows nothing");
            assertEquals(3, observer.observation().bufferedEvents());
            assertEquals(List.of(), generations(drain(observer.child())), "the gap before 2 is still open");
        }
    }

    // ---- slow demand ----

    @Test
    void slowDemandIsNotAGapAndConsumingReleasesTheBuffer() {
        try (Fixture fixture = new Fixture()) {
            Observer observer = fixture.observe(0);
            for (int generation = 1; generation <= 10; generation++) {
                fixture.publish(item(generation, TASK_STATE_WORKING));
            }
            for (int tick = 0; tick < 12; tick++) {
                fixture.tick(5);
                fixture.head.answer(10);
            }

            assertNull(observer.observation().failureReason(), "a consumer that has not demanded is not stranded");
            assertEquals(10, observer.observation().bufferedEvents());
            assertEquals(10, drain(observer.child()).size());
            assertEquals(0, observer.observation().bufferedEvents());

            fixture.publish(item(11, TASK_STATE_WORKING));
            assertEquals(List.of(11L), generations(drain(observer.child())));
        }
    }

    // ---- late stale arrival ----

    @Test
    void lateStaleArrivalIsDiscardedAndNeverRedelivered() {
        try (Fixture fixture = new Fixture()) {
            Observer observer = fixture.observe(4);
            fixture.publish(item(5, TASK_STATE_WORKING));
            fixture.publish(item(6, TASK_STATE_INPUT_REQUIRED));
            fixture.publish(item(5, TASK_STATE_WORKING));
            fixture.publish(item(3, TASK_STATE_WORKING));
            fixture.publish(item(6, TASK_STATE_INPUT_REQUIRED));

            assertEquals(List.of(5L, 6L), generations(drain(observer.child())));
            assertEquals(0, observer.observation().bufferedEvents());
            assertEquals(6, observer.observation().watermark());
        }
    }

    // ---- terminal cleanup ----

    @Test
    void terminalDeliveryReleasesTheObservationTheTaskAndTheScheduler() {
        try (Fixture fixture = new Fixture()) {
            Observer observer = fixture.observe(0);
            assertEquals(1, fixture.reconciler.activeObservationCount());

            fixture.publish(item(1, TASK_STATE_WORKING));
            fixture.publish(item(2, TASK_STATE_COMPLETED));

            assertEquals(0, fixture.reconciler.activeObservationCount());
            assertEquals(0, fixture.reconciler.activeTaskCount());
            assertFalse(fixture.reconciler.isScheduled());
            verify(fixture.scheduled).cancel(false);
            fixture.tick(60);
            assertEquals(0, fixture.head.reads.size(), "a finished task is never read again");
            assertEquals(List.of(1L, 2L), generations(drain(observer.child())));
        }
    }

    @Test
    void consumerCloseReleasesTheObservationAndAbandonsItsPendingRead() {
        try (Fixture fixture = new Fixture()) {
            Observer observer = fixture.observe(4);
            fixture.tick(0);
            CompletableFuture<OptionalLong> read = fixture.head.last();

            observer.child().close(true);

            assertEquals(0, fixture.reconciler.activeObservationCount());
            assertTrue(read.isCancelled());
            assertFalse(fixture.reconciler.isScheduled());
        }
    }

    @Test
    void closedReconcilerRejectsNewObservationsAndReleasesEverything() {
        Fixture fixture = new Fixture();
        fixture.observe(4);
        fixture.reconciler.close();

        assertFalse(fixture.reconciler.isScheduled());
        assertEquals(0, fixture.reconciler.activeTaskCount());
        EventQueue.ChildQueue late = (EventQueue.ChildQueue) fixture.mainQueue.tap();
        assertThrows(IllegalStateException.class, () -> fixture.reconciler.attach(late));
        fixture.close();
    }

    // ---- older-version filtering does not hide generations from the accounting ----

    @Test
    void generationsFilteredForAnOlderProtocolViewStillAdvanceDeliveryAccounting() {
        try (Fixture fixture = new Fixture()) {
            Observer observer = fixture.observe(0);
            Message update = new Message(Message.Role.ROLE_AGENT, List.of(new TextPart("update")), "update-id",
                    CONTEXT_ID, TASK_ID, null, null, null);
            fixture.publish(item(new TaskMessageUpdateEvent(TASK_ID, CONTEXT_ID, update, null, 1), 1,
                    TASK_STATE_WORKING));
            fixture.publish(item(2, TASK_STATE_INPUT_REQUIRED));
            fixture.publish(item(new TaskMessageUpdateEvent(TASK_ID, CONTEXT_ID, update, null, 3), 3,
                    TASK_STATE_INPUT_REQUIRED));

            List<EventQueueItem> delivered = drain(observer.child());
            assertEquals(List.of(1L, 2L, 3L), generations(delivered));
            List<StreamingEventKind> visibleToOldClient = delivered.stream()
                    .map(item -> VersionedOutputProjector.projectEvent((StreamingEventKind) item.getEvent(),
                            ServerVersionPolicy.V1_0))
                    .filter(Objects::nonNull)
                    .toList();
            assertEquals(1, visibleToOldClient.size(), "the older view hides both message updates");
            assertEquals(0, ((TaskStatusUpdateEvent) visibleToOldClient.get(0)).generation(),
                    "projected events carry no generation, so accounting cannot rely on them");

            assertEquals(3, observer.observation().watermark());
            for (int tick = 0; tick < 6; tick++) {
                fixture.tick(5);
                fixture.head.answer(3);
            }
            assertNull(observer.observation().failureReason(), "no false gap and no starvation");
        }
    }

    // ---- attach versus commit: the sink exists before the snapshot is read ----

    @Test
    void localCommitsRacingTheAttachAreDiscardedWhenCoveredAndDeliveredWhenLater() {
        attachBeforeSnapshotBarrier(false);
    }

    @Test
    void injectedRemoteCommitsRacingTheAttachAreDiscardedWhenCoveredAndDeliveredWhenLater() {
        attachBeforeSnapshotBarrier(true);
    }

    private static void attachBeforeSnapshotBarrier(boolean remote) {
        try (Fixture fixture = new Fixture()) {
            fixture.saveTask(4, TASK_STATE_WORKING);
            EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
            PublicationReconciler.Attachment attachment = fixture.reconciler.attach(child);

            fixture.commitAndPublish(TASK_STATE_INPUT_REQUIRED, remote);   // generation 5, before the read
            Task snapshot = TaskManager.readAuthoritativeTask(fixture.store, TASK_ID);
            assertEquals(5, snapshot.generation());
            fixture.commitAndPublish(TASK_STATE_WORKING, remote);          // generation 6, after the read
            attachment.complete(snapshot.generation());

            assertEquals(List.of(6L), generations(drain(child)), "5 is covered by the snapshot, 6 is not");
            fixture.commitAndPublish(TASK_STATE_INPUT_REQUIRED, remote);   // generation 7, after the attach
            assertEquals(List.of(7L), generations(drain(child)));
            if (remote) {
                assertTrue(fixture.pushes.isEmpty(), "a remote copy never dispatches a webhook");
                assertTrue(fixture.replications.isEmpty(), "a remote copy is never replicated again");
            }
        }
    }

    @Test
    void itemsQueuedBeforeTheAttachAreDiscardedWhenCoveredByTheSnapshot() {
        for (boolean remote : new boolean[] {false, true}) {
            try (Fixture fixture = new Fixture()) {
                fixture.saveTask(4, TASK_STATE_WORKING);
                EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
                fixture.commitAndPublish(TASK_STATE_INPUT_REQUIRED, remote);   // generation 5, queued unobserved
                fixture.commitAndPublish(TASK_STATE_WORKING, remote);          // generation 6, queued unobserved
                assertEquals(2, child.size());

                PublicationReconciler.Attachment attachment = fixture.reconciler.attach(child);
                Task snapshot = TaskManager.readAuthoritativeTask(fixture.store, TASK_ID);
                assertEquals(6, snapshot.generation());
                fixture.commitAndPublish(TASK_STATE_INPUT_REQUIRED, remote);   // generation 7
                attachment.complete(snapshot.generation());

                assertEquals(List.of(7L), generations(drain(child)), "remote=" + remote);
            }
        }
    }

    // ---- an observer that is given no snapshot: a covered concurrent commit cannot be dropped silently ----

    @Test
    void withoutASnapshotACoveredCommitHeldAfterTheAttachClosesTheObservationWithTheResyncFailure() {
        for (boolean remote : new boolean[] {false, true}) {
            try (Fixture fixture = new Fixture()) {
                fixture.saveTask(4, TASK_STATE_WORKING);
                EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
                PublicationReconciler.Observation observation =
                        (PublicationReconciler.Observation) fixture.reconciler.attach(child);

                fixture.commitAndPublish(TASK_STATE_INPUT_REQUIRED, remote);   // generation 5, held by the gate
                long covered = TaskManager.readAuthoritativeTask(fixture.store, TASK_ID).generation();
                assertEquals(5, covered);
                fixture.commitAndPublish(TASK_STATE_WORKING, remote);          // generation 6, held, not covered
                observation.completeWithoutSnapshot(covered);

                assertCoveredCommitEndedTheStream(fixture, child, observation, remote, 6);
            }
        }
    }

    @Test
    void withoutASnapshotACoveredCommitQueuedBeforeTheAttachClosesTheObservationWithTheResyncFailure() {
        for (boolean remote : new boolean[] {false, true}) {
            try (Fixture fixture = new Fixture()) {
                fixture.saveTask(4, TASK_STATE_WORKING);
                EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
                fixture.commitAndPublish(TASK_STATE_INPUT_REQUIRED, remote);   // generation 5, queued unobserved
                PublicationReconciler.Observation observation =
                        (PublicationReconciler.Observation) fixture.reconciler.attach(child);
                long covered = TaskManager.readAuthoritativeTask(fixture.store, TASK_ID).generation();
                assertEquals(5, covered);
                observation.completeWithoutSnapshot(covered);

                assertCoveredCommitEndedTheStream(fixture, child, observation, remote, 5);
            }
        }
    }

    /**
     * A concurrent writer commits generation 5 and is slow to distribute it. The observer's tap and the starting
     * generation read (5) both happen in between, so the observer, which is given no snapshot, would never see it.
     */
    @Test
    void withoutASnapshotACoveredCommitPublishedAfterTheAttachClosesTheObservationWithTheResyncFailure() {
        for (boolean remote : new boolean[] {false, true}) {
            try (Fixture fixture = new Fixture()) {
                fixture.saveTask(4, TASK_STATE_WORKING);
                CommittedTaskBatch delayed = fixture.commit(TASK_STATE_INPUT_REQUIRED);   // generation 5, not published
                EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
                PublicationReconciler.Observation observation =
                        (PublicationReconciler.Observation) fixture.reconciler.attach(child);
                long covered = TaskManager.readAuthoritativeTask(fixture.store, TASK_ID).generation();
                assertEquals(5, covered);
                observation.completeWithoutSnapshot(covered);
                assertNull(observation.failureReason(), "nothing has arrived yet: remote=" + remote);
                assertEquals(1, fixture.reconciler.activeObservationCount());

                fixture.publish(delayed, remote);                                         // arrives after the attach

                assertCoveredCommitEndedTheStream(fixture, child, observation, remote, 5);
            }
        }
    }

    @Test
    void withoutASnapshotACoveredCommitPublishedAfterLaterGenerationsAreDeliveredStillClosesTheObservation() {
        for (boolean remote : new boolean[] {false, true}) {
            try (Fixture fixture = new Fixture()) {
                fixture.saveTask(4, TASK_STATE_WORKING);
                CommittedTaskBatch delayed = fixture.commit(TASK_STATE_INPUT_REQUIRED);   // generation 5, not published
                EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
                PublicationReconciler.Observation observation =
                        (PublicationReconciler.Observation) fixture.reconciler.attach(child);
                observation.completeWithoutSnapshot(5);

                fixture.commitAndPublish(TASK_STATE_WORKING, remote);                     // generation 6: not covered
                assertEquals(List.of(6L), generations(drain(child)), "a later commit still progresses: " + remote);
                fixture.publish(fixture.commit(TASK_STATE_WORKING), remote);              // generation 7
                assertEquals(List.of(7L), generations(drain(child)), "remote=" + remote);
                fixture.publish(delayed, remote);                                         // generation 5, now very late

                assertEquals(PublicationReconciler.FailureReason.COVERED_WITHOUT_SNAPSHOT,
                        observation.failureReason(), "remote=" + remote);
                List<EventQueueItem> afterwards = drain(child);
                assertEquals(1, afterwards.size(), "only the resynchronization failure: remote=" + remote);
                assertInstanceOf(InternalError.class, afterwards.get(0).getEvent());
                assertEquals(7, fixture.store.get(TASK_ID).generation(), "recovery changes no task");
                assertTrue(child.isClosed());
                assertEquals(0, fixture.reconciler.activeObservationCount());
                assertFalse(fixture.reconciler.isScheduled());
            }
        }
    }

    @Test
    void withoutASnapshotARedeliveredLaterGenerationIsStillIgnoredAndOnlyACoveredOneFails() {
        try (Fixture fixture = new Fixture()) {
            fixture.saveTask(4, TASK_STATE_WORKING);
            EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
            PublicationReconciler.Observation observation =
                    (PublicationReconciler.Observation) fixture.reconciler.attach(child);
            observation.completeWithoutSnapshot(4);

            CommittedTaskBatch later = fixture.commit(TASK_STATE_INPUT_REQUIRED);         // generation 5
            fixture.publish(later, false);
            fixture.publish(later, false);                                                // duplicate of a delivered one
            assertEquals(List.of(5L), generations(drain(child)));
            assertNull(observation.failureReason(), "a duplicate above the starting generation is not covered");
            assertEquals(5, observation.watermark());
            assertEquals(1, fixture.reconciler.activeObservationCount());
        }
    }

    @Test
    void withoutASnapshotACoveredCommitPublishedAfterTheFinalEventDoesNotAddAFailureToAFinishedStream() {
        try (Fixture fixture = new Fixture()) {
            fixture.saveTask(4, TASK_STATE_WORKING);
            CommittedTaskBatch delayed = fixture.commit(TASK_STATE_INPUT_REQUIRED);       // generation 5, not published
            EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
            PublicationReconciler.Observation observation =
                    (PublicationReconciler.Observation) fixture.reconciler.attach(child);
            observation.completeWithoutSnapshot(5);
            fixture.commitAndPublish(TASK_STATE_COMPLETED, false);                        // generation 6, final
            assertEquals(List.of(6L), generations(drain(child)));
            assertEquals(0, fixture.reconciler.activeObservationCount(), "a finished observation is released");

            fixture.publish(delayed, false);

            assertNull(observation.failureReason());
            assertEquals(List.of(), generations(drain(child)), "nothing is added after the final event");
        }
    }

    @Test
    void withASnapshotACoveredCommitPublishedAfterTheAttachIsStillDiscardedSilently() {
        for (boolean remote : new boolean[] {false, true}) {
            try (Fixture fixture = new Fixture()) {
                fixture.saveTask(4, TASK_STATE_WORKING);
                CommittedTaskBatch delayed = fixture.commit(TASK_STATE_INPUT_REQUIRED);   // generation 5, not published
                EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
                PublicationReconciler.Observation observation =
                        (PublicationReconciler.Observation) fixture.reconciler.attach(child);
                observation.complete(5);                                                  // the snapshot carries 5

                fixture.publish(delayed, remote);
                fixture.commitAndPublish(TASK_STATE_WORKING, remote);                     // generation 6

                assertNull(observation.failureReason(), "remote=" + remote);
                assertEquals(List.of(6L), generations(drain(child)), "remote=" + remote);
                assertEquals(6, observation.watermark());
            }
        }
    }

    private static void assertCoveredCommitEndedTheStream(Fixture fixture, EventQueue.ChildQueue child,
            PublicationReconciler.Observation observation, boolean remote, long expectedHead) {
        assertEquals(PublicationReconciler.FailureReason.COVERED_WITHOUT_SNAPSHOT, observation.failureReason(),
                "remote=" + remote);
        List<EventQueueItem> delivered = drain(child);
        assertEquals(1, delivered.size(), "only the resynchronization failure, no event: remote=" + remote);
        InternalError error = assertInstanceOf(InternalError.class, delivered.get(0).getEvent());
        assertTrue(error.getMessage().contains("call GetTask to resynchronize"), error.getMessage());
        assertTrue(child.isClosed());
        assertEquals(0, fixture.reconciler.activeObservationCount());
        assertFalse(fixture.reconciler.isScheduled());
        assertEquals(0, observation.bufferedEvents());
        if (remote) {
            assertTrue(fixture.pushes.isEmpty(), "a remote copy never dispatches a webhook");
            assertTrue(fixture.replications.isEmpty(), "a remote copy is never replicated again");
        }
        assertEquals(expectedHead, fixture.store.get(TASK_ID).generation(), "recovery changes no task");
    }

    @Test
    void withoutASnapshotOnlyLaterCommitsAreDeliveredAndNothingCoveredMeansNoFailure() {
        for (boolean remote : new boolean[] {false, true}) {
            try (Fixture fixture = new Fixture()) {
                fixture.saveTask(4, TASK_STATE_WORKING);
                EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
                PublicationReconciler.Observation observation =
                        (PublicationReconciler.Observation) fixture.reconciler.attach(child);

                fixture.commit(TASK_STATE_INPUT_REQUIRED);                         // generation 5, committed, not published
                long read = TaskManager.readAuthoritativeTask(fixture.store, TASK_ID).generation();
                assertEquals(5, read);
                fixture.commitAndPublish(TASK_STATE_WORKING, remote);              // generation 6, after the read
                observation.completeWithoutSnapshot(read);

                assertNull(observation.failureReason(), "remote=" + remote);
                assertEquals(List.of(6L), generations(drain(child)), "remote=" + remote);
                assertEquals(6, observation.watermark());
            }
        }
        try (Fixture fixture = new Fixture()) {
            fixture.saveTask(4, TASK_STATE_WORKING);
            EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
            PublicationReconciler.Observation observation =
                    (PublicationReconciler.Observation) fixture.reconciler.attach(child);
            observation.completeWithoutSnapshot(4);                                // nothing arrived before the read
            fixture.commitAndPublish(TASK_STATE_INPUT_REQUIRED, false);            // generation 5
            assertNull(observation.failureReason());
            assertEquals(List.of(5L), generations(drain(child)));
        }
    }

    @Test
    void withASnapshotACoveredCommitIsStillDiscardedBecauseTheSnapshotReplacesIt() {
        try (Fixture fixture = new Fixture()) {
            fixture.saveTask(4, TASK_STATE_WORKING);
            EventQueue.ChildQueue child = (EventQueue.ChildQueue) fixture.mainQueue.tap();
            PublicationReconciler.Observation observation =
                    (PublicationReconciler.Observation) fixture.reconciler.attach(child);
            fixture.commitAndPublish(TASK_STATE_INPUT_REQUIRED, false);            // generation 5
            observation.complete(5);

            assertNull(observation.failureReason());
            assertEquals(List.of(), generations(drain(child)));
            assertEquals(5, observation.watermark());
        }
    }

    // ---- recovery has no side effect on the task, webhooks or replication ----

    @Test
    void recoveryCreatesNoMutationTaskFailureWebhookOrReplication() {
        try (Fixture fixture = new Fixture()) {
            fixture.saveTask(4, TASK_STATE_WORKING);
            fixture.head.immediate = id -> OptionalLong.of(
                    TaskManager.readAuthoritativeTask(fixture.store, id).generation());
            Observer observer = fixture.observe(4);
            fixture.commit(TASK_STATE_COMPLETED);                              // the lost terminal publication
            TaskSnapshot committed = fixture.store.readSnapshot(TASK_ID);
            assertEquals(5, committed.task().generation());

            fixture.tick(0);
            fixture.tick(5);
            fixture.tick(5);

            assertEquals(PublicationReconciler.FailureReason.STRANDED_WATERMARK,
                    observer.observation().failureReason());
            assertEquals(committed, fixture.store.readSnapshot(TASK_ID), "no mutation, no new revision");
            assertEquals(TASK_STATE_COMPLETED, fixture.store.get(TASK_ID).status().state(), "no task failure");
            assertTrue(fixture.pushes.isEmpty(), "no webhook");
            assertTrue(fixture.replications.isEmpty(), "no replication");
        }
    }

    // ---- real scheduler: the bound holds and nothing leaks ----

    @Test
    void moreWatchedTasksThanReaderThreadsAreQueuedNotRejectedOrClosed() throws Exception {
        int tasks = 9;
        AtomicBoolean blocking = new AtomicBoolean();
        AtomicInteger started = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        InMemoryTaskStore store = new InMemoryTaskStore() {
            @Override
            public TaskSnapshot readSnapshot(String taskId) {
                if (blocking.get()) {
                    started.incrementAndGet();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                return super.readSnapshot(taskId);
            }
        };
        PublicationReconciler reconciler = PublicationReconciler.forTaskStore(store,
                new PublicationReconciler.Limits(Duration.ofMillis(50), Duration.ofSeconds(5),
                        Duration.ofSeconds(10), 256, 4L * 1024 * 1024));
        List<EventQueue.MainQueue> queues = new ArrayList<>();
        List<EventQueue> keepAlives = new ArrayList<>();
        List<EventQueue.ChildQueue> children = new ArrayList<>();
        List<PublicationReconciler.Observation> observations = new ArrayList<>();
        try {
            for (int index = 0; index < tasks; index++) {
                String taskId = "pool-task-" + index;
                store.save(Task.builder().id(taskId).contextId(CONTEXT_ID).status(new TaskStatus(TASK_STATE_WORKING))
                        .generation(1).history(List.of()).artifacts(List.of()).build(), false);
                EventQueue.MainQueue main = (EventQueue.MainQueue) EventQueue.builder(new MainEventBus())
                        .taskId(taskId).taskStateProvider(store).build();
                queues.add(main);
                keepAlives.add(main.tap());
                EventQueue.ChildQueue child = (EventQueue.ChildQueue) main.tap();
                children.add(child);
                PublicationReconciler.Attachment attachment = reconciler.attach(child);
                attachment.complete(1);
                observations.add((PublicationReconciler.Observation) attachment);
            }
            blocking.set(true);
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> started.get() >= 4);
            Awaitility.await().pollDelay(500, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS)
                    .until(() -> children.stream().noneMatch(EventQueue::isClosed));

            release.countDown();
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> started.get() >= tasks);

            for (int index = 0; index < tasks; index++) {
                assertNull(observations.get(index).failureReason(), "task " + index);
                assertFalse(children.get(index).isClosed(), "task " + index);
            }
        } finally {
            release.countDown();
            reconciler.close();
            children.forEach(child -> child.close(true));
            keepAlives.forEach(keepAlive -> keepAlive.close(true));
            queues.forEach(queue -> queue.close(true));
        }
    }

    @Test
    void realSchedulerClosesAStrandedObservationAndLeavesNoThreadsBehind() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(saved(4, TASK_STATE_WORKING), false);
        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        EventQueue.MainQueue main = (EventQueue.MainQueue) EventQueue.builder(new MainEventBus())
                .taskId(TASK_ID).taskStateProvider(store).build();
        EventQueue keepAlive = main.tap();
        EventQueue.ChildQueue child = (EventQueue.ChildQueue) main.tap();
        PublicationReconciler reconciler = PublicationReconciler.forTaskStore(store,
                new PublicationReconciler.Limits(Duration.ofMillis(50), Duration.ofSeconds(2),
                        Duration.ofMillis(200), 256, 4L * 1024 * 1024));
        try {
            reconciler.attach(child).complete(4);
            assertTrue(reconciler.isScheduled());
            authority.mutate(TASK_ID, current -> TaskMutationPlanner.planStatus(current,
                    TaskStatusUpdateEvent.builder().taskId(TASK_ID).contextId(CONTEXT_ID)
                            .status(new TaskStatus(TASK_STATE_COMPLETED)).build()));

            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(child::isClosed);
            assertInstanceOf(InternalError.class, drain(child).get(0).getEvent());
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> !reconciler.isScheduled());
            Awaitility.await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> !threadAlive("a2a-publication-reconciler"));
        } finally {
            reconciler.close();
            main.close(true);
            keepAlive.close(true);
        }
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until(() -> !threadAlive("a2a-publication-authority-read"));
    }

    // ---- request handler: subscription attaches before it reads the snapshot ----

    @Test
    void subscriptionRegistersItsSinkBeforeReadingTheSnapshotLocally() throws Exception {
        subscriptionBarrier(false);
    }

    @Test
    void subscriptionRegistersItsSinkBeforeReadingTheSnapshotWithAnInjectedRemoteDelivery() throws Exception {
        subscriptionBarrier(true);
    }

    private static void subscriptionBarrier(boolean remote) throws Exception {
        try (HandlerRig rig = new HandlerRig()) {
            rig.store.save(saved(4, TASK_STATE_WORKING), false);
            rig.store.beforeRead.set(() -> rig.commitAndPublish(TASK_STATE_INPUT_REQUIRED, remote));
            Collector collector = rig.subscribe();

            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !collector.events.isEmpty());
            rig.commitAndPublish(TASK_STATE_WORKING, remote);
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> collector.events.size() >= 2);

            List<Long> seen = collector.events.stream().map(PublicationGapRecoveryTest::generationOf).toList();
            assertEquals(List.of(5L, 6L), seen, "first the snapshot at 5, then only the later commit");
            assertInstanceOf(Task.class, collector.events.get(0));
            assertNull(collector.failure);
        }
    }

    @Test
    void subscriptionOnAClosedReconcilerClosesItsTappedChild() {
        try (HandlerRig rig = new HandlerRig()) {
            rig.store.save(saved(4, TASK_STATE_WORKING), false);
            rig.reconciler.close();

            assertThrows(IllegalStateException.class, rig::subscribe);

            assertTrue(rig.queueManager.getActiveChildQueueCount(TASK_ID) <= 0, "the tapped child must be closed");
        }
    }

    @Test
    void subscriptionThatFindsATerminalTaskOnTheSnapshotReadDetachesItsSink() {
        try (HandlerRig rig = new HandlerRig()) {
            rig.store.save(saved(4, TASK_STATE_WORKING), false);
            rig.store.beforeRead.set(() -> rig.commit(TASK_STATE_COMPLETED));

            assertThrows(UnsupportedOperationError.class, rig::subscribe);

            assertEquals(0, rig.reconciler.activeObservationCount());
            assertFalse(rig.reconciler.isScheduled());
            assertTrue(rig.queueManager.getActiveChildQueueCount(TASK_ID) <= 0, "no observing child may remain");
        }
    }

    @Test
    void subscriberOfALostTerminalPublicationIsClosedAndGetTaskReturnsTheFinalState() throws Exception {
        try (HandlerRig rig = new HandlerRig()) {
            rig.store.save(saved(4, TASK_STATE_WORKING), false);
            Collector collector = rig.subscribe();
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !collector.events.isEmpty());
            assertEquals(4, generationOf(collector.events.get(0)));

            rig.commit(TASK_STATE_COMPLETED);                                  // committed, publication lost
            rig.tick(0);
            rig.tick(5);
            rig.tick(5);

            assertTrue(collector.failed.await(5, TimeUnit.SECONDS), "the stream must be closed");
            assertInstanceOf(InternalError.class, collector.failure);
            Task finalState = rig.handler.onGetTask(new TaskQueryParams(TASK_ID, null, null), rig.context());
            assertEquals(TASK_STATE_COMPLETED, finalState.status().state());
            assertEquals(0, rig.reconciler.activeObservationCount());
        }
    }

    // ---- helpers ----

    private static boolean threadAlive(String name) {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.isAlive() && name.equals(thread.getName()));
    }

    private static Task saved(long generation, TaskState state) {
        return Task.builder(task(state, generation)).history(List.of()).artifacts(List.of()).build();
    }

    private static Task task(TaskState state, long generation) {
        return Task.builder()
                .id(TASK_ID)
                .contextId(CONTEXT_ID)
                .status(new TaskStatus(state))
                .generation(generation)
                .build();
    }

    private static TaskStatusUpdateEvent status(TaskState state, long generation) {
        return TaskStatusUpdateEvent.builder()
                .taskId(TASK_ID)
                .contextId(CONTEXT_ID)
                .status(new TaskStatus(state))
                .generation(generation)
                .build();
    }

    private static LocalEventQueueItem item(long generation, TaskState state) {
        return item(status(state, generation), generation, state);
    }

    /** A position of a committed batch: the event itself carries the generation the observer accounts for. */
    private static LocalEventQueueItem item(StreamingEventKind event, long generation, TaskState state) {
        boolean terminal = state.isFinal();
        Task snapshot = task(state, generation);
        return new LocalEventQueueItem(event, new TaskSnapshot(snapshot, new TaskRevision(generation)), terminal,
                snapshot, terminal, false, -1, generation);
    }

    private static long generationOf(Event event) {
        if (event instanceof Task task) {
            return task.generation();
        }
        if (event instanceof TaskStatusUpdateEvent update) {
            return update.generation();
        }
        if (event instanceof TaskMessageUpdateEvent update) {
            return update.generation();
        }
        return -1;
    }

    private static List<Long> generations(List<EventQueueItem> items) {
        return items.stream().map(item -> generationOf(item.getEvent())).filter(generation -> generation >= 0)
                .toList();
    }

    private static List<EventQueueItem> drain(EventQueue child) {
        List<EventQueueItem> items = new ArrayList<>();
        try {
            EventQueueItem next;
            while ((next = child.dequeueEventItem(0)) != null) {
                items.add(next);
            }
        } catch (EventQueueClosedException closed) {
            // The child finished after delivering what it held.
        }
        return items;
    }

    private record Observer(EventQueue.ChildQueue child, PublicationReconciler.Observation observation) {
    }

    private static final class Fixture implements AutoCloseable {

        private final AtomicLong clock = new AtomicLong(START_NANOS);
        private final InMemoryTaskStore store = new InMemoryTaskStore();
        private final TaskMutationAuthority authority = new TaskMutationAuthority(store);
        private final List<Event> pushes = new CopyOnWriteArrayList<>();
        private final List<Event> replications = new CopyOnWriteArrayList<>();
        private final ManualHead head = new ManualHead();
        private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        private final ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        private final PublicationReconciler reconciler;
        private final EventQueue.MainQueue mainQueue;
        private final EventQueue keepAlive;
        private final MainEventBusProcessor processor;

        private Fixture() {
            this(PublicationReconciler.Limits.DEFAULTS, event -> 100L);
        }

        private Fixture(PublicationReconciler.Limits limits, ToLongFunction<Event> sizer) {
            doReturn(scheduled).when(scheduler)
                    .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            reconciler = new PublicationReconciler(limits, head, clock::get, scheduler, sizer);
            MainEventBus bus = new MainEventBus();
            mainQueue = (EventQueue.MainQueue) EventQueue.builder(bus)
                    .taskId(TASK_ID)
                    .taskStateProvider(store)
                    .hook(item -> replications.add(item.getEvent()))
                    .build();
            keepAlive = mainQueue.tap();
            processor = new MainEventBusProcessor(bus, store, (event, snapshot) -> {
                pushes.add(event);
                return true;
            }, new InMemoryQueueManager(store, bus));
            processor.setPushNotificationExecutor(Runnable::run);
        }

        private void verifyScheduledEveryFiveSeconds() {
            long interval = TimeUnit.SECONDS.toNanos(5);
            verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(interval), eq(interval),
                    eq(TimeUnit.NANOSECONDS));
        }

        private Observer observe(long snapshotGeneration) {
            EventQueue.ChildQueue child = (EventQueue.ChildQueue) mainQueue.tap();
            PublicationReconciler.Attachment attachment = reconciler.attach(child);
            attachment.complete(snapshotGeneration);
            return new Observer(child, (PublicationReconciler.Observation) attachment);
        }

        private void publish(LocalEventQueueItem item) {
            mainQueue.distributeToChildren(item);
        }

        /** Moves the injected clock forward and runs one reconciliation pass, as the scheduler would. */
        private void tick(long secondsLater) {
            clock.addAndGet(TimeUnit.SECONDS.toNanos(secondsLater));
            reconciler.tick();
        }

        private void saveTask(long generation, TaskState state) {
            store.save(saved(generation, state), false);
        }

        /** Commits a status mutation to the authoritative store without publishing it. */
        private CommittedTaskBatch commit(TaskState state) {
            CommittedMutation mutation = authority.mutate(TASK_ID, current -> TaskMutationPlanner.planStatus(
                    current, TaskStatusUpdateEvent.builder().taskId(TASK_ID).contextId(CONTEXT_ID)
                            .status(new TaskStatus(state)).build()));
            return new CommittedTaskBatch(mutation.snapshot(), mutation.events());
        }

        private void commitAndPublish(TaskState state, boolean remote) {
            publish(commit(state), remote);
        }

        /** Distributes a batch that was committed earlier, as a writer that was slow to publish would. */
        private void publish(CommittedTaskBatch batch, boolean remote) {
            processor.deliverCommittedBatch(mainQueue, batch, remote);
        }

        @Override
        public void close() {
            reconciler.close();
            mainQueue.close(true);
            keepAlive.close(true);
        }
    }

    private static final class ManualHead implements PublicationReconciler.AuthorityHead {

        private final List<String> reads = new ArrayList<>();
        private final List<CompletableFuture<OptionalLong>> pending = new ArrayList<>();
        private volatile Function<String, OptionalLong> immediate;
        private volatile RuntimeException throwOnRead;

        @Override
        public CompletionStage<OptionalLong> read(String taskId) {
            if (throwOnRead != null) {
                throw throwOnRead;
            }
            reads.add(taskId);
            CompletableFuture<OptionalLong> read = new CompletableFuture<>();
            pending.add(read);
            if (immediate != null) {
                read.complete(immediate.apply(taskId));
            }
            return read;
        }

        private CompletableFuture<OptionalLong> last() {
            return pending.get(pending.size() - 1);
        }

        private void answer(long head) {
            last().complete(OptionalLong.of(head));
        }

        private void fail(Throwable failure) {
            last().completeExceptionally(failure);
        }
    }

    /** A store that runs a hook once, after the reader attached its sink and before it reads the snapshot. */
    private static final class RacingStore extends InMemoryTaskStore {

        private final AtomicReference<Runnable> beforeRead = new AtomicReference<>();

        @Override
        public TaskSnapshot readSnapshot(String taskId) {
            Runnable hook = beforeRead.getAndSet(null);
            if (hook != null) {
                hook.run();
            }
            return super.readSnapshot(taskId);
        }
    }

    private static final class HandlerRig implements AutoCloseable {

        private final RacingStore store = new RacingStore();
        private final MainEventBus bus = new MainEventBus();
        private final InMemoryQueueManager queueManager = new InMemoryQueueManager(store, bus);
        private final MainEventBusProcessor processor =
                new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queueManager);
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final TaskMutationAuthority authority = new TaskMutationAuthority(store);
        private final AtomicLong clock = new AtomicLong(START_NANOS);
        private final PublicationReconciler reconciler;
        private final RequestHandler handler;
        private final AtomicBoolean closed = new AtomicBoolean();

        private HandlerRig() {
            ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
            ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
            doReturn(scheduled).when(scheduler)
                    .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            ManualHead head = new ManualHead();
            head.immediate = id -> OptionalLong.of(TaskManager.readAuthoritativeTask(store, id).generation());
            reconciler = new PublicationReconciler(PublicationReconciler.Limits.DEFAULTS, head, clock::get,
                    scheduler, event -> 100L);
            processor.setPushNotificationExecutor(Runnable::run);
            handler = DefaultRequestHandler.builder()
                    .agentExecutor(new AgentExecutor() {
                        @Override
                        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
                        }

                        @Override
                        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
                        }
                    })
                    .taskStore(store)
                    .queueManager(queueManager)
                    .pushConfigStore(new InMemoryPushNotificationConfigStore())
                    .mainEventBusProcessor(processor)
                    .executor(executor)
                    .eventConsumerExecutor(executor)
                    .authorizationRequired(false)
                    .publicationReconciler(reconciler)
                    .build();
        }

        private ServerCallContext context() {
            return new ServerCallContext(new TestUser(), Map.of(), Set.of());
        }

        private Collector subscribe() throws A2AError {
            Collector collector = new Collector();
            handler.onSubscribeToTask(new TaskIdParams(TASK_ID), context()).subscribe(collector);
            return collector;
        }

        private CommittedTaskBatch commit(TaskState state) {
            CommittedMutation mutation = authority.mutate(TASK_ID, current -> TaskMutationPlanner.planStatus(
                    current, TaskStatusUpdateEvent.builder().taskId(TASK_ID).contextId(CONTEXT_ID)
                            .status(new TaskStatus(state)).build()));
            return new CommittedTaskBatch(mutation.snapshot(), mutation.events());
        }

        private void commitAndPublish(TaskState state, boolean remote) {
            CommittedTaskBatch batch = commit(state);
            processor.deliverCommittedBatch((EventQueue.MainQueue) queueManager.get(TASK_ID), batch, remote);
        }

        private void tick(long secondsLater) {
            clock.addAndGet(TimeUnit.SECONDS.toNanos(secondsLater));
            reconciler.tick();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                reconciler.close();
                try {
                    queueManager.close(TASK_ID);
                } catch (NoTaskQueueException alreadyClosed) {
                    // The last observer already closed the queue.
                }
                executor.shutdownNow();
            }
        }
    }

    private static final class Collector implements Flow.Subscriber<StreamingEventKind> {

        private final List<StreamingEventKind> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch failed = new CountDownLatch(1);
        private volatile Throwable failure;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(StreamingEventKind event) {
            events.add(event);
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
            failed.countDown();
        }

        @Override
        public void onComplete() {
        }
    }

    private record TestUser() implements User {

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public String getUsername() {
            return "gap-user";
        }
    }
}
