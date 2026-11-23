package org.a2aproject.sdk.server.tasks;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enrolls inline webhook configuration before the task mutation that may notify it.
 *
 * <p>The coordinator is a prepare / outcome / cleanup resource for ordinary-send materialization.
 * It writes the configuration to the independent {@link PushNotificationConfigStore} <em>first</em>
 * and verifies read-after-write visibility. A failure at that point is reported before any task
 * mutation. It never performs a second configuration write after the task commit.</p>
 *
 * <ul>
 *   <li><b>New task:</b> the configuration is provisional until the task exists. Public
 *       configuration operations already require task existence, and notifications are only
 *       dispatched for committed task events, so it is neither visible nor deliverable earlier.
 *       A definitive non-commit removes only the exact value this attempt wrote
 *       ({@link PushNotificationConfigStore#deleteInfoIfUnchanged}); an unremovable orphan is
 *       retained for bounded expiry. An indeterminate commit retains the enrollment for
 *       reconciliation and never deletes it, because absence from one read is not proof of failure.</li>
 *   <li><b>Existing task:</b> a successful write is observable immediately. A later input rejection
 *       never deletes or restores it, since another operation may have changed it meanwhile. The
 *       partial effect is reported with the known task and configuration IDs instead.</li>
 * </ul>
 *
 * <p>Only a shared-transaction adapter may claim all-or-nothing behavior. A no-task direct Message
 * never calls this class.</p>
 */
public final class TaskEnrollmentCoordinator {

    /** Detail key carrying the task ID of a possibly persisted enrollment. */
    public static final String DETAIL_TASK_ID = "taskId";
    /** Detail key carrying the push notification configuration ID. */
    public static final String DETAIL_CONFIG_ID = "pushNotificationConfigId";
    /** Detail key stating that the configuration write succeeded and was not compensated. */
    public static final String DETAIL_CONFIG_ENROLLED = "pushNotificationConfigEnrolled";
    /** Detail key: {@code not-committed} or {@code unknown}. */
    public static final String DETAIL_INPUT_OUTCOME = "inputOutcome";

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskEnrollmentCoordinator.class);

    private enum State { PROVISIONAL, INDETERMINATE }

    private record Attempt(TaskPushNotificationConfig config, Instant expiresAt, State state) {
    }

    private final PushNotificationConfigStore configStore;
    private final Clock clock;
    private final Duration orphanTtl;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public TaskEnrollmentCoordinator(PushNotificationConfigStore configStore, Clock clock, Duration orphanTtl) {
        this.configStore = Assert.checkNotNullParam("configStore", configStore);
        this.clock = Assert.checkNotNullParam("clock", clock);
        this.orphanTtl = Assert.checkNotNullParam("orphanTtl", orphanTtl);
        if (orphanTtl.isZero() || orphanTtl.isNegative()) {
            throw new IllegalArgumentException("orphanTtl must be positive");
        }
    }

    /**
     * Enrolls configuration for a task that does not yet exist. Throws before any task mutation if the
     * store rejects the write or the write is not readable afterwards.
     */
    public Enrollment enrollNewTask(TaskPushNotificationConfig config, String taskId,
            @Nullable String protocolVersion) {
        TaskPushNotificationConfig written = write(config, taskId, protocolVersion);
        Enrollment enrollment = new Enrollment(written, true);
        attempts.put(key(written), new Attempt(written, clock.instant().plus(orphanTtl), State.PROVISIONAL));
        return enrollment;
    }

    /**
     * Enrolls configuration for an existing task. The returned enrollment never compensates.
     */
    public Enrollment enrollExistingTask(TaskPushNotificationConfig config, String taskId,
            @Nullable String protocolVersion) {
        return new Enrollment(write(config, taskId, protocolVersion), false);
    }

    /**
     * Removes expired new-task orphans whose task still does not exist. Indeterminate enrollments are
     * never deleted here. Only an exact, unchanged value is deleted; an entry past expiry whose value
     * could not be deleted is dropped from tracking so retries stay bounded.
     *
     * @return number of configurations actually deleted
     */
    public int expireOrphans(Predicate<String> taskExists) {
        Instant now = clock.instant();
        int deleted = 0;
        for (Map.Entry<String, Attempt> entry : attempts.entrySet()) {
            Attempt attempt = entry.getValue();
            if (attempt.state() != State.PROVISIONAL || attempt.expiresAt().isAfter(now)) {
                continue;
            }
            if (!attempts.remove(entry.getKey(), attempt)) {
                continue;
            }
            String taskId = Assert.checkNotNullParam("taskId", attempt.config().taskId());
            if (!taskExists.test(taskId) && configStore.deleteInfoIfUnchanged(attempt.config())) {
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * Stops tracking indeterminate enrollments whose task is now known to exist. Absence of the task
     * keeps the enrollment; this method never deletes configuration.
     *
     * @return number of enrollments reconciled as committed
     */
    public int reconcileIndeterminate(Predicate<String> taskExists) {
        int reconciled = 0;
        for (Map.Entry<String, Attempt> entry : attempts.entrySet()) {
            Attempt attempt = entry.getValue();
            if (attempt.state() == State.INDETERMINATE
                    && taskExists.test(Assert.checkNotNullParam("taskId", attempt.config().taskId()))
                    && attempts.remove(entry.getKey(), attempt)) {
                reconciled++;
            }
        }
        return reconciled;
    }

    /** Number of enrollments still tracked for expiry or reconciliation. */
    public int trackedEnrollments() {
        return attempts.size();
    }

    private TaskPushNotificationConfig write(TaskPushNotificationConfig config, String taskId,
            @Nullable String protocolVersion) {
        Assert.checkNotNullParam("config", config);
        Assert.checkNotNullParam("taskId", taskId);
        TaskPushNotificationConfig request = TaskPushNotificationConfig.builder(config).taskId(taskId).build();
        TaskPushNotificationConfig written = configStore.setInfo(request, protocolVersion);
        String configId = written.id() == null ? taskId : written.id();
        if (!isVisible(taskId, configId)) {
            configStore.deleteInfoIfUnchanged(written);
            throw new InternalError(null, "Push notification config was not readable after enrollment",
                    Map.of(DETAIL_TASK_ID, taskId, DETAIL_CONFIG_ID, configId));
        }
        return written;
    }

    private boolean isVisible(String taskId, String configId) {
        String pageToken = "";
        do {
            var page = configStore.getInfo(new org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams(
                    taskId, PushNotificationConfigStore.DEFAULT_MAX_PUSH_CONFIGS_PER_TASK, pageToken, ""));
            for (TaskPushNotificationConfig candidate : page.configs()) {
                if (configId.equals(candidate.id())) {
                    return true;
                }
            }
            String next = page.nextPageToken();
            pageToken = next == null ? "" : next;
        } while (!pageToken.isEmpty());
        return false;
    }

    private static String key(TaskPushNotificationConfig config) {
        return config.taskId() + ":" + config.id();
    }

    /** One enrollment attempt; report exactly one task outcome. */
    public final class Enrollment {

        private final TaskPushNotificationConfig config;
        private final boolean newTask;

        private Enrollment(TaskPushNotificationConfig config, boolean newTask) {
            this.config = config;
            this.newTask = newTask;
        }

        /** The exact stored value written by this attempt (ID defaulted by the store). */
        public TaskPushNotificationConfig config() {
            return config;
        }

        /** The task committed: enrollment is already visible; nothing more is written. */
        public void taskCommitted() {
            if (newTask) {
                attempts.remove(key(config));
            }
        }

        /**
         * The task mutation definitively did not commit. A new-task provisional enrollment is removed if
         * still unchanged; an existing-task enrollment is left exactly as stored.
         *
         * @return {@code true} if a hidden provisional configuration was deleted by this call
         */
        public boolean taskNotCommitted() {
            if (!newTask) {
                return false;
            }
            Attempt attempt = attempts.get(key(config));
            if (attempt == null) {
                return false;
            }
            boolean deleted = false;
            try {
                deleted = configStore.deleteInfoIfUnchanged(config);
            } catch (RuntimeException e) {
                LOGGER.warn("Cleanup of provisional push notification config {} for new task {} failed;"
                        + " it stays hidden until expiry", config.id(), config.taskId(), e);
            }
            if (deleted) {
                attempts.remove(key(config), attempt);
            }
            return deleted;
        }

        /** The commit outcome is unknown: retain the enrollment and keep it for reconciliation. */
        public void outcomeUnknown() {
            if (newTask) {
                attempts.computeIfPresent(key(config),
                        (k, attempt) -> new Attempt(attempt.config(), attempt.expiresAt(), State.INDETERMINATE));
            }
        }

        /**
         * Builds the error to report after the task input did not commit, preserving the original typed
         * error where it is one of the supported input errors and adding the known IDs and outcome.
         * Never changes stored configuration.
         *
         * @param failure the input failure
         * @param outcomeUnknown whether the storage outcome is indeterminate rather than a known non-commit
         */
        public A2AError partialEffectError(Throwable failure, boolean outcomeUnknown) {
            Map<String, Object> details = new LinkedHashMap<>();
            if (failure instanceof A2AError error) {
                details.putAll(error.getDetails());
            }
            details.put(DETAIL_TASK_ID, String.valueOf(config.taskId()));
            details.put(DETAIL_CONFIG_ID, String.valueOf(config.id()));
            details.put(DETAIL_CONFIG_ENROLLED, Boolean.TRUE);
            details.put(DETAIL_INPUT_OUTCOME, outcomeUnknown ? "unknown" : "not-committed");
            LOGGER.warn("Task input for {} did not commit after push notification config {} was enrolled"
                    + " (outcome {}); the configuration was left in place", config.taskId(), config.id(),
                    outcomeUnknown ? "unknown" : "not-committed", failure);
            if (failure instanceof TaskGenerationMismatchError error) {
                return new TaskGenerationMismatchError(error.getCode(), error.getMessage(), details);
            }
            if (failure instanceof InvalidParamsError error) {
                return new InvalidParamsError(error.getCode(), error.getMessage(), details);
            }
            if (failure instanceof TaskNotFoundError error) {
                return new TaskNotFoundError(error.getMessage(), details);
            }
            if (failure instanceof InternalError error) {
                return new InternalError(error.getCode(), error.getMessage(), details);
            }
            String message = failure.getMessage() == null ? "Task input did not commit" : failure.getMessage();
            return new InternalError(null, message, details);
        }
    }
}
