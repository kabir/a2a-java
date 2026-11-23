package org.a2aproject.sdk.server.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TaskEnrollmentCoordinatorTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    private MutableClock clock;
    private RecordingStore store;
    private TaskEnrollmentCoordinator coordinator;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-23T12:00:00Z"));
        store = new RecordingStore();
        coordinator = new TaskEnrollmentCoordinator(store, clock, TTL);
    }

    @Test
    void newTaskConfigIsReadableBeforeTaskCommitAndNoSecondWriteFollowsCommit() {
        TaskEnrollmentCoordinator.Enrollment enrollment = coordinator.enrollNewTask(config("t1", "c1", "http://a"),
                "t1", "1.1");

        // Read-after-write visibility is established before the caller commits the task.
        assertEquals(List.of("c1"), ids("t1"));
        assertEquals("1.1", store.getProtocolVersion("t1", "c1"));
        assertEquals(1, store.setInfoCalls.get());

        enrollment.taskCommitted();

        assertEquals(1, store.setInfoCalls.get(), "no second config write after task commit");
        assertEquals(0, store.deleteCalls.get());
        assertEquals(0, coordinator.trackedEnrollments());
        assertEquals(List.of("c1"), ids("t1"));
    }

    @Test
    void defaultConfigIdIsResolvedAndVerifiedThroughTheStore() {
        TaskEnrollmentCoordinator.Enrollment enrollment = coordinator.enrollNewTask(
                TaskPushNotificationConfig.builder().url("http://a").taskId("t1").build(), "t1", null);

        assertEquals("t1", enrollment.config().id());
        assertEquals(List.of("t1"), ids("t1"));
    }

    @Test
    void configStoreFailureIsReportedBeforeAnyTaskMutationAndTracksNothing() {
        store.failNextSet.set(new InvalidParamsError("Too many push notification configs"));

        InvalidParamsError error = assertThrows(InvalidParamsError.class,
                () -> coordinator.enrollNewTask(config("t1", "c1", "http://a"), "t1", "1.1"));

        assertEquals("Too many push notification configs", error.getMessage());
        assertEquals(List.of(), ids("t1"));
        assertEquals(0, coordinator.trackedEnrollments());
    }

    @Test
    void writeThatIsNotReadableAfterwardsFailsBeforeTaskCommitAndCleansUpItsOwnValue() {
        store.hideWrites.set(true);

        InternalError error = assertThrows(InternalError.class,
                () -> coordinator.enrollNewTask(config("t1", "c1", "http://a"), "t1", "1.1"));

        assertEquals("t1", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_TASK_ID));
        assertEquals("c1", error.getDetails().get(TaskEnrollmentCoordinator.DETAIL_CONFIG_ID));
        store.hideWrites.set(false);
        assertEquals(List.of(), ids("t1"), "unreadable write was cleaned up");
        assertEquals(0, coordinator.trackedEnrollments());
    }

    @Test
    void existingTaskConfigStoreFailureLeavesOtherConfigsUntouched() {
        store.setInfo(config("t1", "other", "http://other"));
        store.failNextSet.set(new InvalidParamsError("rejected"));

        assertThrows(InvalidParamsError.class,
                () -> coordinator.enrollExistingTask(config("t1", "c1", "http://a"), "t1", "1.1"));

        assertEquals(List.of("other"), ids("t1"));
        assertEquals("http://other", store.getInfo(new ListTaskPushNotificationConfigsParams("t1"))
                .configs().get(0).url());
    }

    @Test
    void definitiveNonCommitRemovesOnlyTheHiddenNewTaskConfigWrittenByThisAttempt() {
        store.setInfo(config("t1", "unrelated", "http://u"));
        TaskEnrollmentCoordinator.Enrollment enrollment = coordinator.enrollNewTask(
                config("t1", "c1", "http://a"), "t1", "1.1");

        assertTrue(enrollment.taskNotCommitted());

        assertEquals(List.of("unrelated"), ids("t1"));
        assertEquals(0, coordinator.trackedEnrollments());
    }

    @Test
    void definitiveNonCommitDoesNotDeleteAConfigReplacedAfterEnrollment() {
        TaskEnrollmentCoordinator.Enrollment enrollment = coordinator.enrollNewTask(
                config("t1", "c1", "http://a"), "t1", "1.1");
        store.setInfo(config("t1", "c1", "http://replacement"), "1.1");

        assertFalse(enrollment.taskNotCommitted());

        assertEquals("http://replacement", store.getInfo(new ListTaskPushNotificationConfigsParams("t1"))
                .configs().get(0).url());
    }

    @Test
    void failedCleanupLeavesBoundedOrphanThatExpiresOnlyAfterTtlAndWhileTaskIsAbsent() {
        TaskEnrollmentCoordinator.Enrollment enrollment = coordinator.enrollNewTask(
                config("t1", "c1", "http://a"), "t1", "1.1");
        TaskEnrollmentCoordinator.Enrollment kept = coordinator.enrollNewTask(
                config("t2", "c2", "http://b"), "t2", "1.1");
        store.failDelete.set(true);

        assertFalse(enrollment.taskNotCommitted(), "cleanup failure must not throw or claim deletion");
        assertEquals(2, coordinator.trackedEnrollments());
        store.failDelete.set(false);

        clock.advance(TTL.minusSeconds(1));
        assertEquals(0, coordinator.expireOrphans(taskId -> false), "not before expiry");
        assertEquals(List.of("c1"), ids("t1"));

        clock.advance(Duration.ofSeconds(2));
        assertEquals(1, coordinator.expireOrphans(taskId -> taskId.equals("t2")),
                "t2's task exists, so its config is not an orphan");
        assertEquals(List.of(), ids("t1"));
        assertEquals(List.of("c2"), ids("t2"));
        assertEquals(0, coordinator.trackedEnrollments());
        kept.taskCommitted();
    }

    @Test
    void indeterminateCommitRetainsEnrollmentAcrossExpiryUntilReconciled() {
        TaskEnrollmentCoordinator.Enrollment enrollment = coordinator.enrollNewTask(
                config("t1", "c1", "http://a"), "t1", "1.1");

        enrollment.outcomeUnknown();
        clock.advance(TTL.multipliedBy(5));

        assertEquals(0, coordinator.expireOrphans(taskId -> false), "absence is not proof of failure");
        assertEquals(0, coordinator.reconcileIndeterminate(taskId -> false));
        assertEquals(List.of("c1"), ids("t1"));
        assertEquals(0, store.deleteCalls.get());

        assertEquals(1, coordinator.reconcileIndeterminate(taskId -> true));
        assertEquals(List.of("c1"), ids("t1"), "reconciling a committed task never deletes");
        assertEquals(0, coordinator.trackedEnrollments());
    }

    @Test
    void existingTaskInputRejectionKeepsConfigAndReportsKnownIdsWithOriginalTypedError() {
        TaskEnrollmentCoordinator.Enrollment enrollment = coordinator.enrollExistingTask(
                config("t1", "c1", "http://a"), "t1", "1.1");
        TaskGenerationMismatchError original = new TaskGenerationMismatchError();

        assertFalse(enrollment.taskNotCommitted(), "existing-task enrollment is never compensated");
        A2AError reported = enrollment.partialEffectError(original, false);

        TaskGenerationMismatchError typed = assertInstanceOf(TaskGenerationMismatchError.class, reported);
        assertEquals(original.getCode(), typed.getCode());
        assertEquals(original.getMessage(), typed.getMessage());
        assertEquals("t1", typed.getDetails().get(TaskEnrollmentCoordinator.DETAIL_TASK_ID));
        assertEquals("c1", typed.getDetails().get(TaskEnrollmentCoordinator.DETAIL_CONFIG_ID));
        assertEquals(Boolean.TRUE, typed.getDetails().get(TaskEnrollmentCoordinator.DETAIL_CONFIG_ENROLLED));
        assertEquals("not-committed", typed.getDetails().get(TaskEnrollmentCoordinator.DETAIL_INPUT_OUTCOME));
        assertEquals(List.of("c1"), ids("t1"));
        assertEquals(0, store.deleteCalls.get());
        assertEquals(1, store.setInfoCalls.get(), "no restore write either");
        assertEquals(0, coordinator.trackedEnrollments());
    }

    @Test
    void concurrentConfigReplacementBetweenEnrollmentAndInputFailureSurvives() {
        TaskEnrollmentCoordinator.Enrollment enrollment = coordinator.enrollExistingTask(
                config("t1", "c1", "http://a"), "t1", "1.1");
        // Another operation changes the config after enrollment, before the input fails.
        store.setInfo(config("t1", "c1", "http://changed"), "1.1");

        enrollment.taskNotCommitted();
        A2AError reported = enrollment.partialEffectError(new InvalidParamsError("terminal task"), false);

        assertInstanceOf(InvalidParamsError.class, reported);
        assertEquals("terminal task", reported.getMessage());
        assertEquals("http://changed", store.getInfo(new ListTaskPushNotificationConfigsParams("t1"))
                .configs().get(0).url());
        assertEquals(0, store.deleteCalls.get());
        assertEquals(2, store.setInfoCalls.get(), "only the two explicit writes; nothing was restored");
    }

    @Test
    void indeterminateExistingTaskInputReportsUncertaintyAndRetainsConfig() {
        TaskEnrollmentCoordinator.Enrollment enrollment = coordinator.enrollExistingTask(
                config("t1", "c1", "http://a"), "t1", "1.1");

        enrollment.outcomeUnknown();
        A2AError reported = enrollment.partialEffectError(
                new TaskMutationOutcomeUnknownException("t1", "storage timed out"), true);

        assertInstanceOf(InternalError.class, reported);
        assertEquals("storage timed out", reported.getMessage());
        assertEquals("unknown", reported.getDetails().get(TaskEnrollmentCoordinator.DETAIL_INPUT_OUTCOME));
        assertEquals("c1", reported.getDetails().get(TaskEnrollmentCoordinator.DETAIL_CONFIG_ID));
        assertEquals(List.of("c1"), ids("t1"));
        assertEquals(0, store.deleteCalls.get());
    }

    @Test
    void inMemoryDeleteIfUnchangedMatchesExactValueOnly() {
        TaskPushNotificationConfig written = store.setInfo(config("t1", "c1", "http://a"), "1.1");

        assertFalse(store.deleteInfoIfUnchanged(config("t1", "c1", "http://different")));
        assertEquals(List.of("c1"), ids("t1"));
        assertFalse(store.deleteInfoIfUnchanged(TaskPushNotificationConfig.builder().url("http://a")
                .taskId("t1").build()), "missing ID never matches");
        assertTrue(store.deleteInfoIfUnchanged(written));
        assertEquals(List.of(), ids("t1"));
        assertEquals("1.0", store.getProtocolVersion("t1", "c1"), "protocol version removed with config");
    }

    @Test
    void legacyStoreDefaultNeverDeletesAndLeavesOrphanTracked() {
        PushNotificationConfigStore legacy = new PushNotificationConfigStore() {
            private final InMemoryPushNotificationConfigStore delegate = new InMemoryPushNotificationConfigStore();

            @Override
            public TaskPushNotificationConfig setInfo(TaskPushNotificationConfig notificationConfig) {
                return delegate.setInfo(notificationConfig);
            }

            @Override
            public org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult getInfo(
                    ListTaskPushNotificationConfigsParams params) {
                return delegate.getInfo(params);
            }

            @Override
            public void deleteInfo(String taskId, String configId) {
                throw new AssertionError("must not blindly delete");
            }
        };
        TaskEnrollmentCoordinator legacyCoordinator = new TaskEnrollmentCoordinator(legacy, clock, TTL);
        TaskEnrollmentCoordinator.Enrollment enrollment = legacyCoordinator.enrollNewTask(
                config("t1", "c1", "http://a"), "t1", "1.1");

        assertFalse(enrollment.taskNotCommitted());

        assertEquals(1, legacyCoordinator.trackedEnrollments());
        clock.advance(TTL.plusSeconds(1));
        assertEquals(0, legacyCoordinator.expireOrphans(taskId -> false));
        assertEquals(0, legacyCoordinator.trackedEnrollments(), "expiry stays bounded");
    }

    @Test
    void provisionalConfigDoesNotNotifyUntilCommittedEventAndIsAlreadyAvailableForTheFirstOne() throws Exception {
        A2AHttpClient httpClient = Mockito.mock(A2AHttpClient.class);
        A2AHttpClient.PostBuilder post = Mockito.mock(A2AHttpClient.PostBuilder.class);
        A2AHttpResponse response = Mockito.mock(A2AHttpResponse.class);
        Mockito.when(httpClient.createPost()).thenReturn(post);
        Mockito.when(post.followRedirects(false)).thenReturn(post);
        Mockito.when(post.url(Mockito.anyString())).thenReturn(post);
        Mockito.when(post.addHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(post);
        Mockito.when(post.body(Mockito.anyString())).thenReturn(post);
        Mockito.when(post.post()).thenReturn(response);
        Mockito.when(response.success()).thenReturn(true);
        BasePushNotificationSender sender = new BasePushNotificationSender(store, httpClient,
                PushNotificationUrlValidator.ALLOW_ALL);

        TaskEnrollmentCoordinator.Enrollment enrollment = coordinator.enrollNewTask(
                config("t1", "c1", "http://a"), "t1", "1.1");
        // Enrollment alone, and a definitive non-commit, never reach the sender.
        Mockito.verify(httpClient, Mockito.never()).createPost();

        // First committed notification (immediately terminal task) sees the already-enrolled config.
        Task terminal = Task.builder().id("t1").contextId("ctx").status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .build();
        enrollment.taskCommitted();
        assertTrue(sender.sendNotification(terminal, terminal));

        Mockito.verify(httpClient, Mockito.times(1)).createPost();
        Mockito.verify(post).url("http://a");
        assertEquals(1, store.setInfoCalls.get(), "no config write between commit and notification");
    }

    private List<String> ids(String taskId) {
        return store.getInfo(new ListTaskPushNotificationConfigsParams(taskId)).configs().stream()
                .map(TaskPushNotificationConfig::id).toList();
    }

    private static TaskPushNotificationConfig config(String taskId, String id, String url) {
        return TaskPushNotificationConfig.builder().id(id).taskId(taskId).url(url).build();
    }

    private static final class RecordingStore extends InMemoryPushNotificationConfigStore {

        final AtomicInteger setInfoCalls = new AtomicInteger();
        final AtomicInteger deleteCalls = new AtomicInteger();
        final AtomicReference<RuntimeException> failNextSet = new AtomicReference<>();
        final AtomicBoolean hideWrites = new AtomicBoolean();
        final AtomicBoolean failDelete = new AtomicBoolean();

        @Override
        public TaskPushNotificationConfig setInfo(TaskPushNotificationConfig config, String protocolVersion) {
            RuntimeException failure = failNextSet.getAndSet(null);
            if (failure != null) {
                throw failure;
            }
            setInfoCalls.incrementAndGet();
            return super.setInfo(config, protocolVersion);
        }

        @Override
        public org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult getInfo(
                ListTaskPushNotificationConfigsParams params) {
            if (hideWrites.get()) {
                return new org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult(List.of());
            }
            return super.getInfo(params);
        }

        @Override
        public void deleteInfo(String taskId, String configId) {
            deleteCalls.incrementAndGet();
            super.deleteInfo(taskId, configId);
        }

        @Override
        public boolean deleteInfoIfUnchanged(TaskPushNotificationConfig expected) {
            if (failDelete.get()) {
                throw new IllegalStateException("config store unavailable");
            }
            return super.deleteInfoIfUnchanged(expected);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
