package org.a2aproject.sdk.server.tasks;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.util.CdiUtils;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.util.PageToken;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory implementation of {@link TaskStore} and {@link TaskStateProvider}.
 * <p>
 * This implementation uses a {@link ConcurrentHashMap} to store tasks in memory.
 * Tasks are lost on application restart. For persistent storage, use a database-backed
 * implementation such as the JPA TaskStore in the extras module.
 * </p>
 * <p>
 * This is the default TaskStore used when no other implementation is provided.
 * </p>
 *
 * <h2>Exception Behavior</h2>
 * InMemoryTaskStore has minimal exception scenarios compared to database-backed implementations:
 * <ul>
 *   <li><b>No TaskSerializationException:</b> Task objects are stored directly in memory without
 *       serialization. No JSON parsing or schema compatibility issues can occur.</li>
 *   <li><b>No TaskPersistenceException:</b> ConcurrentHashMap operations do not involve I/O,
 *       network, or transactional concerns. Standard put/get/remove operations are guaranteed
 *       to succeed under normal JVM operation.</li>
 *   <li><b>OutOfMemoryError (potential):</b> The only failure scenario is JVM heap exhaustion if
 *       too many tasks are stored. This is an {@link Error} (not Exception) and indicates a fatal
 *       system condition requiring JVM restart and capacity planning.</li>
 * </ul>
 *
 * <h3>Design Rationale</h3>
 * This implementation intentionally does NOT throw {@link TaskStoreException} or its subclasses
 * because:
 * <ul>
 *   <li>No serialization step exists - tasks stored as Java objects</li>
 *   <li>No I/O or network operations that can fail</li>
 *   <li>ConcurrentHashMap guarantees thread-safe operations without checked exceptions</li>
 *   <li>Memory exhaustion (OutOfMemoryError) is an unrecoverable system failure</li>
 * </ul>
 *
 * <h3>Comparison to Database Implementations</h3>
 * Database-backed implementations (e.g., JpaDatabaseTaskStore) throw exceptions for:
 * <ul>
 *   <li>Serialization errors (JSON parsing, schema mismatches)</li>
 *   <li>Connection failures (network, timeouts)</li>
 *   <li>Transaction failures (deadlocks, constraint violations)</li>
 *   <li>Capacity issues (disk full, quota exceeded)</li>
 * </ul>
 * InMemoryTaskStore avoids all of these by operating entirely in-process.
 *
 * <h3>Memory Management Considerations</h3>
 * Callers should monitor memory usage and implement task cleanup policies:
 * <pre>{@code
 * // Example: Delete finalized tasks older than 48 hours
 * ListTasksParams params = new ListTasksParams.Builder()
 *     .statusTimestampBefore(Instant.now().minus(Duration.ofHours(48)))
 *     .build();
 *
 * List<Task> oldTasks = taskStore.list(params, context).tasks();
 * oldTasks.stream()
 *     .filter(task -> task.status().state().isFinal())
 *     .forEach(task -> taskStore.delete(task.id()));
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * All operations are thread-safe via {@link ConcurrentHashMap}. Multiple threads can
 * concurrently save, get, list, and delete tasks without synchronization. Last-write-wins
 * semantics apply for concurrent {@code save()} calls to the same task ID.
 *
 * @see TaskStore for interface contract and exception documentation
 * @see TaskStoreException for exception hierarchy (not thrown by this implementation)
 * @see TaskStateProvider for queue lifecycle integration
 */
@ApplicationScoped
public class InMemoryTaskStore implements AtomicTaskStore, TaskStateProvider {

    enum CommitFaultPoint {
        BEFORE_REPLACEMENT,
        AFTER_REPLACEMENT
    }

    @FunctionalInterface
    interface CommitFaultInjector {
        void at(CommitFaultPoint point, String taskId, TaskSnapshot proposed);
    }

    private static final CommitFaultInjector NO_COMMIT_FAULT = (point, taskId, proposed) -> {
    };

    /**
     * {@code baselined} is false only for a task written by the legacy {@link #save} path: the first atomic
     * read or mutation of it in this process establishes legacy generation 0 as generation 1 exactly once.
     * This stabilizes the task for the process lifetime only; in-memory tasks are still lost on restart.
     */
    private record StoredTask(Task task, TaskRevision revision, boolean baselined) {
        StoredTask(Task task, TaskRevision revision) {
            this(task, revision, true);
        }

        TaskSnapshot snapshot() {
            return new TaskSnapshot(task, revision);
        }
    }

    private record MutationResult(CommittedMutation mutation,
            TaskCommitSlot.@Nullable Reservation reservation) {
    }

    private final ConcurrentMap<String, StoredTask> tasks = new ConcurrentHashMap<>();
    private final AtomicLong revisionSequence = new AtomicLong();
    private final @Nullable TaskAuthorizationProvider authorizationProvider;
    private final InMemoryWebhookDispatchGate webhookDispatchGate;
    private final CommitFaultInjector commitFaultInjector;

    public InMemoryTaskStore() {
        this.authorizationProvider = null;
        this.webhookDispatchGate = new InMemoryWebhookDispatchGate();
        this.commitFaultInjector = NO_COMMIT_FAULT;
    }

    @Inject
    public InMemoryTaskStore(@Any Instance<TaskAuthorizationProvider> authorizationProviderInstance,
            InMemoryWebhookDispatchGate webhookDispatchGate) {
        this.authorizationProvider = CdiUtils.getIfResolvable(authorizationProviderInstance);
        this.webhookDispatchGate = webhookDispatchGate;
        this.commitFaultInjector = NO_COMMIT_FAULT;
    }

    /**
     * Creates an in-memory task store with optional
     * task-level authorization.
     *
     * <p>This constructor supports programmatic wiring
     * in non-CDI runtimes such as Spring Framework.</p>
     *
     * @param authorizationProvider provider used to filter
     *                              tasks during list operations;
     *                              {@code null} permits all tasks
     */
    public InMemoryTaskStore(@Nullable TaskAuthorizationProvider authorizationProvider) {
        this.authorizationProvider = authorizationProvider;
        this.webhookDispatchGate = new InMemoryWebhookDispatchGate();
        this.commitFaultInjector = NO_COMMIT_FAULT;
    }

    InMemoryTaskStore(@Nullable TaskAuthorizationProvider authorizationProvider,
            CommitFaultInjector commitFaultInjector) {
        this.authorizationProvider = authorizationProvider;
        this.webhookDispatchGate = new InMemoryWebhookDispatchGate();
        this.commitFaultInjector = Assert.checkNotNullParam("commitFaultInjector", commitFaultInjector);
    }

    InMemoryWebhookDispatchGate webhookDispatchGate() {
        return webhookDispatchGate;
    }

    @Override
    public void save(Task task, boolean isReplicated) {
        tasks.compute(task.id(), (taskId, current) -> {
            boolean baselined = task.generation() >= 1;
            if (baselined && task.generation() < Long.MAX_VALUE) {
                webhookDispatchGate.initializeTaskLane(taskId, 0, task.generation() + 1);
            }
            return new StoredTask(task, nextRevision(), baselined);
        });
        // InMemoryTaskStore doesn't fire TaskFinalizedEvent, so isReplicated is unused here
    }

    @Override
    public @Nullable Task get(String taskId) {
        StoredTask stored = tasks.get(taskId);
        return stored == null ? null : stored.task();
    }

    @Override
    public void delete(String taskId) {
        tasks.computeIfPresent(taskId, (id, current) -> null);
    }

    @Override
    public @Nullable TaskSnapshot readSnapshot(String taskId) {
        StoredTask stored = tasks.get(taskId);
        if (stored == null) {
            return null;
        }
        if (stored.baselined()) {
            return stored.snapshot();
        }

        // Only legacy generation-zero saves need baseline migration. Keep the accepted snapshot and lane
        // initialization under the same per-task map compute before publishing the baselined task.
        StoredTask baselined = tasks.computeIfPresent(taskId, (id, current) -> {
            if (current.baselined()) {
                return current;
            }
            StoredTask accepted = baseline(current);
            if (accepted.task().generation() >= 1 && accepted.task().generation() < Long.MAX_VALUE) {
                webhookDispatchGate.initializeTaskLane(id, 0, accepted.task().generation() + 1);
            }
            return accepted;
        });
        return baselined == null ? null : baselined.snapshot();
    }

    private StoredTask baseline(StoredTask stored) {
        if (stored.baselined()) {
            return stored;
        }
        Task task = stored.task();
        return task.generation() == 0
                ? new StoredTask(Task.builder(task).generation(1).build(), nextRevision(), true)
                : new StoredTask(task, stored.revision(), true);
    }

    @Override
    public CommittedMutation mutate(String taskId, TaskMutation mutation) {
        return mutate(taskId, mutation, TaskCommitSlotCallback.NONE);
    }

    @Override
    public CommittedMutation mutate(String taskId, TaskMutation mutation, TaskCommitSlotCallback slotCallback) {
        Assert.checkNotNullParam("taskId", taskId);
        Assert.checkNotNullParam("mutation", mutation);
        Assert.checkNotNullParam("slotCallback", slotCallback);

        AtomicReference<MutationResult> result = new AtomicReference<>();
        AtomicReference<TaskCommitSlot.Reservation> reservedSlot = new AtomicReference<>();
        try {
            tasks.compute(taskId, (id, unbaselined) -> {
                StoredTask current = unbaselined == null ? null : baseline(unbaselined);
                TaskSnapshot currentSnapshot = current == null ? null : current.snapshot();
                MutationPlan plan;
                try {
                    plan = Assert.checkNotNullParam("plan", mutation.plan(currentSnapshot));
                } catch (RuntimeException failure) {
                    throw new TaskMutationNotCommittedException(taskId, "Task mutation planning failed", failure);
                }

                if (!plan.changed()) {
                    if (current == null) {
                        throw new TaskMutationNotCommittedException(taskId,
                                "An absent task cannot accept an unchanged mutation");
                    }
                    if (current.task().generation() >= 1) {
                        webhookDispatchGate.initializeTaskLane(id, 0, current.task().generation() + 1);
                    }
                    result.set(new MutationResult(
                            new CommittedMutation(current.snapshot(), List.of(), false, 0L), null));
                    return current;
                }

                if (!taskId.equals(plan.task().id())) {
                    throw new TaskMutationNotCommittedException(taskId,
                            "Mutation plan task ID does not match the requested task ID");
                }

                TaskRevision revision = nextRevision();
                TaskSnapshot committedSnapshot = new TaskSnapshot(plan.task(), revision);
                CommittedMutation committed = new CommittedMutation(committedSnapshot, plan.events(), true, 0L);
                if (plan.events().isEmpty()) {
                    webhookDispatchGate.initializeTaskLane(id, 0,
                            Math.max(1, plan.task().generation() + 1));
                    result.set(new MutationResult(committed, null));
                    return new StoredTask(plan.task(), revision);
                }
                TaskCommitSlot.Reservation reservation = TaskCommitSlot.reserve(taskId, revision);
                TaskCommitSlot slot = reservation.slot();
                reservedSlot.set(reservation);
                try {
                    slotCallback.reserve(slot, committed);
                } catch (RuntimeException failure) {
                    reservation.cancel();
                    throw new TaskMutationNotCommittedException(taskId,
                            "Task publication slot reservation failed", failure);
                } catch (Error failure) {
                    reservation.cancel();
                    throw failure;
                }

                result.set(new MutationResult(committed, reservation));
                commitFaultInjector.at(CommitFaultPoint.BEFORE_REPLACEMENT, taskId, committedSnapshot);
                if (current == null) {
                    webhookDispatchGate.initializeTaskLane(id, 0, 1);
                } else if (current.task().generation() >= 1) {
                    webhookDispatchGate.initializeTaskLane(id, 0, current.task().generation() + 1);
                }
                return new StoredTask(plan.task(), revision);
            });
            MutationResult committed = result.get();
            if (committed == null) {
                throw new TaskMutationOutcomeUnknownException(taskId,
                        "Task mutation completed without a committed result");
            }
            if (committed.reservation() != null) {
                try {
                    commitFaultInjector.at(CommitFaultPoint.AFTER_REPLACEMENT, taskId,
                            committed.mutation().snapshot());
                    committed.reservation().markCommitted();
                } catch (RuntimeException failure) {
                    committed.reservation().cancel();
                    throw new TaskMutationOutcomeUnknownException(taskId,
                            "Task replacement completed but its storage outcome is unknown", failure);
                }
            }
            return committed.mutation();
        } catch (TaskMutationNotCommittedException failure) {
            cancel(reservedSlot.get());
            throw failure;
        } catch (TaskMutationOutcomeUnknownException failure) {
            cancel(reservedSlot.get());
            throw failure;
        } catch (RuntimeException failure) {
            cancel(reservedSlot.get());
            throw new TaskMutationOutcomeUnknownException(taskId,
                    "Task mutation storage outcome is unknown", failure);
        } catch (Error failure) {
            cancel(reservedSlot.get());
            throw failure;
        }
    }

    @Override
    public ListTasksResult list(ListTasksParams params, @Nullable ServerCallContext context) {
        return listWithAuthorization(params, context, authorizationProvider);
    }

    @Override
    public ListTasksResult listAuthorized(ListTasksParams params, ServerCallContext context,
            TaskAuthorizationProvider provider) {
        return listWithAuthorization(params, context, provider);
    }

    private ListTasksResult listWithAuthorization(ListTasksParams params, @Nullable ServerCallContext context,
            @Nullable TaskAuthorizationProvider provider) {
        // Filter and sort tasks in a single stream pipeline
        List<Task> allFilteredTasks = tasks.values().stream()
                .map(StoredTask::task)
                .filter(task -> params.contextId() == null || params.contextId().equals(task.contextId()))
                .filter(task -> params.status() == null ||
                        (task.status() != null && params.status().equals(task.status().state())))
                .filter(task -> params.statusTimestampAfter() == null ||
                        (task.status() != null &&
                         task.status().timestamp() != null &&
                         task.status().timestamp().toInstant().isAfter(params.statusTimestampAfter())))
                .filter(task -> isReadAuthorized(provider, context, task.id()))
                .sorted(Comparator.comparing(
                        (Task t) -> (t.status() != null && t.status().timestamp() != null)
                                // Truncate to milliseconds for consistency with pageToken precision
                                ? t.status().timestamp().toInstant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
                                : null,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Task::id))
                .toList();

        int totalSize = allFilteredTasks.size();

        // Apply pagination
        int pageSize = params.getEffectivePageSize();
        int startIndex = 0;

        // Handle page token using keyset pagination (format: "timestamp_millis:taskId")
        // Use binary search to efficiently find the first task after the pageToken position (O(log N))
        PageToken pageToken = PageToken.fromString(params.pageToken());
        if (pageToken != null) {
            java.time.Instant tokenTimestamp = pageToken.timestamp();
            String tokenId = pageToken.id();

            // Binary search for first task where: timestamp < tokenTimestamp OR (timestamp == tokenTimestamp AND id > tokenId)
            // Since list is sorted (timestamp DESC, id ASC), we search for the insertion point
            int left = 0;
            int right = allFilteredTasks.size();

            while (left < right) {
                int mid = left + (right - left) / 2;
                Task task = allFilteredTasks.get(mid);

                java.time.Instant taskTimestamp = (task.status() != null && task.status().timestamp() != null)
                        ? task.status().timestamp().toInstant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
                        : null;

                if (taskTimestamp == null) {
                    // Task with null timestamp is always "before" a token with a timestamp, as they are sorted last.
                    // So, we search in the right half.
                    left = mid + 1;
                } else {
                    int timestampCompare = taskTimestamp.compareTo(tokenTimestamp);

                    if (timestampCompare < 0 || (timestampCompare == 0 && task.id().compareTo(tokenId) > 0)) {
                        // This task is after the token, search left half
                        right = mid;
                    } else {
                        // This task is before or equal to token, search right half
                        left = mid + 1;
                    }
                }
            }
            startIndex = left;
        }

        // Get the page of tasks
        int endIndex = Math.min(startIndex + pageSize, allFilteredTasks.size());
        List<Task> pageTasks = allFilteredTasks.subList(startIndex, endIndex);

        // Determine next page token (format: "timestamp_millis:taskId")
        String nextPageToken = null;
        if (endIndex < allFilteredTasks.size()) {
            Task lastTask = allFilteredTasks.get(endIndex - 1);
            // All tasks have timestamps (TaskStatus canonical constructor ensures this)
            java.time.Instant timestamp = lastTask.status().timestamp().toInstant();
            nextPageToken = new PageToken(timestamp, lastTask.id()).toString();
        }

        // Transform tasks: limit history and optionally remove artifacts
        int historyLength = params.getEffectiveHistoryLength();
        @Nullable Integer timelineLength = params.timelineLength();
        boolean includeArtifacts = params.shouldIncludeArtifacts();

        List<Task> transformedTasks = pageTasks.stream()
                .map(task -> transformTask(task, historyLength, timelineLength, includeArtifacts))
                .toList();

        return new ListTasksResult(transformedTasks, totalSize, transformedTasks.size(), nextPageToken);
    }

    private TaskRevision nextRevision() {
        long revision = revisionSequence.incrementAndGet();
        if (revision <= 0) {
            throw new IllegalStateException("Task storage revision sequence is exhausted");
        }
        return new TaskRevision(revision);
    }

    private static void cancel(TaskCommitSlot.@Nullable Reservation reservation) {
        if (reservation != null) {
            reservation.cancel();
        }
    }

    private Task transformTask(Task task, int historyLength, @Nullable Integer timelineLength,
            boolean includeArtifacts) {
        // History keeps the most recent N messages (list defaults to zero); the timeline window is
        // independent and left as stored when its limit is omitted.
        Task windowed = TaskReadProjection.project(task, historyLength, timelineLength);

        // Remove artifacts if not requested
        if (includeArtifacts) {
            return windowed;
        }
        return Task.builder(windowed).artifacts(List.of()).build();
    }

    @Override
    public boolean isTaskActive(String taskId) {
        Task task = get(taskId);
        if (task == null) {
            return false;
        }
        // Task is active if not in final state
        return task.status() == null || task.status().state() == null
                || !(task.status().state().isFinal()
                        || task.status().state() == TaskState.TASK_STATE_UNSPECIFIED);
    }

    @Override
    public boolean isTaskFinalized(String taskId) {
        Task task = get(taskId);
        if (task == null) {
            return false;
        }
        // Task is finalized if in final state (ignores grace period)
        return task.status() != null && task.status().state() != null
                && (task.status().state().isFinal()
                        || task.status().state() == TaskState.TASK_STATE_UNSPECIFIED);
    }
}
