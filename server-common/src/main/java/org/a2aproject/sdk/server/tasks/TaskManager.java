package org.a2aproject.sdk.server.tasks;

import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_FAILED;
import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_SUBMITTED;
import static org.a2aproject.sdk.spec.util.Utils.appendArtifactToTask;
import static org.a2aproject.sdk.util.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.A2AServerException;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.a2aproject.sdk.spec.TimelineEntry.StatusValue;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskManager {

    /** Result of processing an event through an atomic task store. */
    public record CommittedProcessingResult(boolean isFinal, @Nullable CommittedMutation mutation) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManager.class);

    private volatile @Nullable String taskId;
    private volatile @Nullable String contextId;
    private final TaskStore taskStore;
    private final @Nullable Message initialMessage;
    private volatile @Nullable Task currentTask;
    private volatile @Nullable PendingTaskInput openingInput;

    public TaskManager(@Nullable String taskId, @Nullable String contextId, TaskStore taskStore, @Nullable Message initialMessage) {
        checkNotNullParam("taskStore", taskStore);
        this.taskId = taskId;
        this.contextId = contextId;
        this.taskStore = taskStore;
        this.initialMessage = initialMessage;
    }

    /**
     * Retains request-local opening input to be materialized atomically with the first task-bearing
     * event when no task exists yet. Retaining it persists nothing.
     */
    public void retainOpeningInput(@Nullable PendingTaskInput input) {
        this.openingInput = input;
    }

    @Nullable String getTaskId() {
        return taskId;
    }

    @Nullable String getContextId() {
        return contextId;
    }

    /**
     * Reads the current authoritative task straight from the store, never from a manager's cached copy. An
     * {@link AtomicTaskStore} supplies its committed snapshot; a legacy-only store supplies {@code get}, whose task
     * carries no canonical generation. A reader that must not miss a later committed result registers its delivery
     * sink before calling this method and discards events at or below the returned generation.
     */
    public static @Nullable Task readAuthoritativeTask(TaskStore store, String taskId) {
        checkNotNullParam("store", store);
        checkNotNullParam("taskId", taskId);
        if (store instanceof AtomicTaskStore atomicStore) {
            TaskSnapshot snapshot = atomicStore.readSnapshot(taskId);
            return snapshot == null ? null : snapshot.task();
        }
        return store.get(taskId);
    }

    public @Nullable Task getTask() {
        if (taskId == null) {
            return null;
        }
        if (currentTask != null) {
            return currentTask;
        }
        currentTask = taskStore.get(taskId);
        return currentTask;
    }

    boolean saveTaskEvent(Task task, boolean isReplicated) throws A2AServerException {
        return saveTaskEvent(task, isReplicated, null);
    }

    boolean saveTaskEvent(Task task, boolean isReplicated, @Nullable AtomicReference<Task> taskSnapshot)
            throws A2AServerException {
        checkIdsAndUpdateIfNecessary(task.id(), task.contextId());
        Task current = getTask();
        if (current == null && initialMessage != null
                && (task.history() == null || task.history().stream()
                        .noneMatch(message -> message.messageId().equals(initialMessage.messageId())))) {
            List<Message> history = new ArrayList<>();
            history.add(initialMessage);
            if (task.history() != null) {
                history.addAll(task.history());
            }
            task = Task.builder(task).history(history).build();
        }
        // Defensive state-machine check: a task that already reached a terminal state must
        // not be overwritten by a task snapshot carrying a different state.
        if (current != null && current.status() != null && current.status().state() != null
                && task.status() != null && task.status().state() != null) {
            validateStateTransition(current.status().state(), task.status().state(), task.id());
        }
        Task savedTask = saveTask(task, isReplicated);
        if (taskSnapshot != null) {
            taskSnapshot.set(savedTask);
        }
        return savedTask.status() != null && savedTask.status().state() != null
                && savedTask.status().state().isFinal();
    }

    boolean saveTaskEvent(TaskStatusUpdateEvent event, boolean isReplicated) throws A2AServerException {
        return saveTaskEvent(event, isReplicated, null);
    }

    boolean saveTaskEvent(TaskStatusUpdateEvent event, boolean isReplicated, @Nullable AtomicReference<Task> taskSnapshot)
            throws A2AServerException {
        checkIdsAndUpdateIfNecessary(event.taskId(), event.contextId());
        Task task = ensureTask(event.taskId(), event.contextId());

        // State-machine validation: reject transitions that would overwrite a terminal
        // state with a different state. Re-arriving events carrying the same
        // final state remain allowed (idempotent replays / replication).
        TaskState currentState = task.status() != null ? task.status().state() : null;
        TaskState newState = event.status() != null ? event.status().state() : null;
        validateStateTransition(currentState, newState, event.taskId());

        Task.Builder builder = Task.builder(task)
                .status(event.status());

        if (task.status().message() != null) {
            List<Message> newHistory = task.history() == null ? new ArrayList<>() : new ArrayList<>(task.history());
            newHistory.add(task.status().message());
            builder.history(newHistory);
        }

        // Handle metadata from the event
        if (event.metadata() != null) {
            Map<String, Object> metadata = task.metadata() == null ? new HashMap<>() : new HashMap<>(task.metadata());
            metadata.putAll(event.metadata());
            builder.metadata(metadata);
        }

        task = builder.build();
        Task savedTask = saveTask(task, isReplicated);
        if (taskSnapshot != null) {
            taskSnapshot.set(savedTask);
        }
        return savedTask.status() != null && savedTask.status().state() != null
                && savedTask.status().state().isFinal();
    }

    boolean saveTaskEvent(TaskArtifactUpdateEvent event, boolean isReplicated) throws A2AServerException {
        return saveTaskEvent(event, isReplicated, null);
    }

    boolean saveTaskEvent(TaskArtifactUpdateEvent event, boolean isReplicated, @Nullable AtomicReference<Task> taskSnapshot)
            throws A2AServerException {
        checkIdsAndUpdateIfNecessary(event.taskId(), event.contextId());
        Task task = ensureTask(event.taskId(), event.contextId());
        // taskId is guaranteed to be non-null after checkIdsAndUpdateIfNecessary
        String nonNullTaskId = taskId;
        if (nonNullTaskId == null) {
            throw new IllegalStateException("taskId should not be null after checkIdsAndUpdateIfNecessary");
        }
        task = appendArtifactToTask(task, event, nonNullTaskId);
        Task savedTask = saveTask(task, isReplicated);
        if (taskSnapshot != null) {
            taskSnapshot.set(savedTask);
        }
        return savedTask.status() != null && savedTask.status().state() != null
                && savedTask.status().state().isFinal();
    }

    public boolean process(Event event, boolean isReplicated) throws A2AServerException {
        return process(event, isReplicated, null);
    }

    public boolean process(Event event, boolean isReplicated, @Nullable AtomicReference<Task> taskSnapshot)
            throws A2AServerException {
        if (!isReplicated && taskStore instanceof AtomicTaskStore) {
            CommittedProcessingResult result = processCommitted(event, TaskCommitSlotCallback.NONE);
            if (taskSnapshot != null && result.mutation() != null) {
                taskSnapshot.set(result.mutation().snapshot().task());
            }
            return result.isFinal();
        }
        return processLegacyEvent(event, isReplicated, taskSnapshot);
    }

    private boolean processLegacyEvent(Event event, boolean isReplicated,
            @Nullable AtomicReference<Task> taskSnapshot) throws A2AServerException {
        boolean isFinal = false;
        if (event instanceof Task task) {
            isFinal = saveTaskEvent(task, isReplicated, taskSnapshot);
        } else if (event instanceof TaskStatusUpdateEvent taskStatusUpdateEvent) {
            isFinal = saveTaskEvent(taskStatusUpdateEvent, isReplicated, taskSnapshot);
        } else if (event instanceof TaskArtifactUpdateEvent taskArtifactUpdateEvent) {
            isFinal = saveTaskEvent(taskArtifactUpdateEvent, isReplicated, taskSnapshot);
        } else if (event instanceof A2AError) {
            // A2AError events trigger automatic transition to FAILED state
            // Error details are NOT persisted in TaskStore (client-specific)
            // Only the FAILED status is persisted and replicated across nodes

            // A2AError events don't have taskId/contextId fields, so we need to ensure
            // we have these from the existing task or TaskManager state
            if (taskId == null) {
                // No task context - A2AError event will be distributed to clients but no state update
                LOGGER.debug("A2AError event without task context - skipping state update");
                return true;  // Return true (is final) to stop event consumption
            }

            // Ensure we have contextId - get from existing task if not set
            String errorContextId = contextId;
            if (errorContextId == null) {
                Task existingTask = getTask();
                if (existingTask != null) {
                    errorContextId = existingTask.contextId();
                }
            }

            // Only create status update if we have contextId
            if (errorContextId != null) {
                LOGGER.debug("A2AError event detected, transitioning task {} to FAILED", taskId);
                TaskStatusUpdateEvent failedEvent = TaskStatusUpdateEvent.builder()
                        .taskId(taskId)
                        .contextId(errorContextId)
                        .status(new TaskStatus(TASK_STATE_FAILED))
                        .build();
                try {
                    isFinal = saveTaskEvent(failedEvent, isReplicated, taskSnapshot);
                } catch (A2AServerException e) {
                    // Task already in a terminal state: the state-machine guard in
                    // validateStateTransition rejected the synthesized FAILED event
                    // (terminal -> FAILED). No state update is needed — the A2AError
                    // itself still signals finality to clients.
                    LOGGER.debug("A2AError for task {} already in terminal state - skipping state update", taskId);
                    isFinal = true;
                }
            } else {
                // Can't update status without contextId, but error is still terminal
                LOGGER.debug("A2AError event for task {} without contextId - skipping state update", taskId);
                isFinal = true;
            }
        }
        return isFinal;
    }

    /**
     * Processes one local event against an atomic store's authoritative snapshot and returns its
     * committed canonical result. Replicated copies continue through their existing stamped-event
     * path until T10 supplies their distinct provenance/finalization contract.
     */
    public CommittedProcessingResult processCommitted(Event event, TaskCommitSlotCallback reservation)
            throws A2AServerException {
        checkNotNullParam("event", event);
        checkNotNullParam("reservation", reservation);
        if (!(taskStore instanceof AtomicTaskStore atomicTaskStore)) {
            throw new IllegalStateException("Committed processing requires an AtomicTaskStore");
        }

        if (event instanceof Task task) {
            checkIdsAndUpdateIfNecessary(task.id(), task.contextId());
            CommittedMutation mutation = mutate(atomicTaskStore, task.id(), current -> {
                if (current != null) {
                    // A full Task is not a safe replacement proposal: it can reset the
                    // authoritative generation/context and discard accumulated artifacts.
                    // T04/T05 own normalization for subsequent executor proposals.
                    throw new InvalidParamsError(
                            "Full Task replacement is not supported by committed mutation processing");
                }
                PendingTaskInput opening = openingInput;
                if (opening != null) {
                    return TaskMutationPlanner.planTaskProposal(null, task, opening);
                }
                Task initial = normalizeInitialTaskAuthority(task);
                return new MutationPlan(initial, List.of(initial), true);
            }, reservation);
            return committedResult(mutation);
        }
        if (event instanceof TaskStatusUpdateEvent statusEvent) {
            checkIdsAndUpdateIfNecessary(statusEvent.taskId(), statusEvent.contextId());
            CommittedMutation mutation = mutate(atomicTaskStore, statusEvent.taskId(), current ->
                    current == null
                            ? openingInput != null ? planOpeningThen(statusEvent)
                            : initialMessage == null ? planLegacyInitialDelta(statusEvent)
                                    : planInitialStatus(statusEvent)
                            : TaskMutationPlanner.planStatus(current, statusEvent),
                    reservation);
            return committedResult(mutation);
        }
        if (event instanceof TaskArtifactUpdateEvent artifactEvent) {
            checkIdsAndUpdateIfNecessary(artifactEvent.taskId(), artifactEvent.contextId());
            CommittedMutation mutation = mutate(atomicTaskStore, artifactEvent.taskId(), current -> current == null
                    ? openingInput != null ? planOpeningThen(artifactEvent)
                    : initialMessage == null ? planLegacyInitialDelta(artifactEvent)
                            : TaskMutationPlanner.planInput(null,
                                    new PendingTaskInput(initialMessage, List.of(artifactEvent), null, null,
                                            new PendingTaskInput.Admission(artifactEvent.taskId(),
                                                    artifactEvent.contextId(), null, true, true)),
                                    MutationOrigin.AGENT)
                    : TaskMutationPlanner.planInput(current,
                            new PendingTaskInput(null, List.of(artifactEvent), null, null,
                                    new PendingTaskInput.Admission(artifactEvent.taskId(), artifactEvent.contextId(),
                                            null, true, true)),
                            MutationOrigin.AGENT), reservation);
            return committedResult(mutation);
        }
        if (event instanceof A2AError) {
            return processErrorCommitted(atomicTaskStore, reservation);
        }
        if (event instanceof TaskMessageUpdateEvent) {
            throw new InvalidParamsError("Raw task message updates are not supported at the committed boundary");
        }
        // Direct Message and other control events keep their established non-task behavior.
        return new CommittedProcessingResult(false, null);
    }

    private CommittedProcessingResult processErrorCommitted(AtomicTaskStore atomicTaskStore,
            TaskCommitSlotCallback reservation)
            throws A2AServerException {
        String currentTaskId = taskId;
        if (currentTaskId == null) {
            return new CommittedProcessingResult(true, null);
        }
        TaskMutationAuthority authority = new TaskMutationAuthority(atomicTaskStore);
        CommittedMutation mutation;
        try {
            mutation = authority.mutate(currentTaskId, current -> {
                if (current == null) {
                    throw new NoAuthoritativeTask();
                }
                if (isTerminal(current.task().status().state())) {
                    return new MutationPlan(current.task(), List.of(), false);
                }
                TaskStatusUpdateEvent failedEvent = TaskStatusUpdateEvent.builder()
                        .taskId(current.task().id())
                        .contextId(current.task().contextId())
                        .status(new TaskStatus(TASK_STATE_FAILED))
                        .build();
                return TaskMutationPlanner.planStatus(current, failedEvent);
            }, reservation);
        } catch (TaskMutationNotCommittedException failure) {
            if (causedByNoAuthoritativeTask(failure)) {
                return new CommittedProcessingResult(true, null);
            }
            throw failure;
        }
        currentTask = mutation.snapshot().task();
        taskId = currentTask.id();
        contextId = currentTask.contextId();
        return new CommittedProcessingResult(isTerminal(currentTask.status().state()), mutation);
    }

    private static boolean causedByNoAuthoritativeTask(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof NoAuthoritativeTask) {
                return true;
            }
        }
        return false;
    }

    private static Task normalizeInitialTaskAuthority(Task task) {
        List<TimelineEntry> timeline = task.timeline();
        if (timeline != null) {
            Set<Long> generations = new HashSet<>();
            for (TimelineEntry entry : timeline) {
                if (entry.generation() != 1 || !generations.add(entry.generation())) {
                    throw new InvalidParamsError("Initial Task timeline contains unsupported or duplicate generations");
                }
            }
        }
        List<Artifact> artifacts = task.artifacts();
        if (artifacts != null) {
            List<Artifact> normalizedArtifacts = new ArrayList<>(artifacts.size());
            for (Artifact artifact : artifacts) {
                Message.Role role = artifact.role();
                if (role == Message.Role.ROLE_USER || role == Message.Role.ROLE_UNSPECIFIED
                        || role != null && role != Message.Role.ROLE_AGENT) {
                    throw new InvalidParamsError("Initial Task contains an unsupported artifact origin");
                }
                boolean legacyRange = artifact.startGeneration() == 0 && artifact.endGeneration() == 0;
                boolean generationOneRange = artifact.startGeneration() == 1 && artifact.endGeneration() == 1;
                if (!legacyRange && !generationOneRange) {
                    throw new InvalidParamsError("Initial Task contains unsupported artifact generations");
                }
                normalizedArtifacts.add(Artifact.builder(artifact)
                        .role(Message.Role.ROLE_AGENT)
                        .startGeneration(1)
                        .endGeneration(1)
                        .build());
            }
            return Task.builder(task).generation(1).artifacts(normalizedArtifacts).build();
        }
        return Task.builder(task).generation(1).build();
    }

    private MutationPlan planLegacyInitialDelta(Event event) {
        String initialTaskId = taskId;
        String initialContextId = contextId;
        if (initialTaskId == null || initialContextId == null) {
            throw new IllegalStateException("Legacy bootstrap requires task and context IDs");
        }
        Task initial = createTask(initialTaskId, initialContextId);
        if (event instanceof TaskStatusUpdateEvent statusEvent) {
            validateStateTransition(initial.status().state(), statusEvent.status().state(), statusEvent.taskId());
            List<Message> history = new ArrayList<>(initial.history());
            if (initial.status().message() != null) {
                history.add(initial.status().message());
            }
            Task.Builder builder = Task.builder(initial).status(statusEvent.status()).history(history);
            if (statusEvent.metadata() != null) {
                builder.metadata(statusEvent.metadata());
            }
            return new MutationPlan(builder.build(), List.of(), true);
        }
        if (event instanceof TaskArtifactUpdateEvent artifactEvent) {
            if (Boolean.TRUE.equals(artifactEvent.append())) {
                throw new InvalidParamsError("Cannot append to an artifact that does not exist");
            }
            return new MutationPlan(appendArtifactToTask(initial, artifactEvent, artifactEvent.taskId()), List.of(), true);
        }
        throw new IllegalArgumentException("Only initial status or artifact deltas use legacy bootstrap");
    }

    private CommittedMutation mutate(AtomicTaskStore atomicTaskStore, String eventTaskId,
            TaskMutation mutation, TaskCommitSlotCallback reservation) throws A2AServerException {
        try {
            CommittedMutation committed = new TaskMutationAuthority(atomicTaskStore)
                    .mutate(eventTaskId, mutation, reservation);
            taskId = eventTaskId;
            contextId = committed.snapshot().task().contextId();
            currentTask = committed.snapshot().task();
            return committed;
        } catch (StateTransitionRejected rejection) {
            throw rejection.failure;
        } catch (TaskMutationNotCommittedException failure) {
            Throwable cause = failure.getCause();
            while (cause != null) {
                if (cause instanceof StateTransitionRejected rejection) {
                    throw rejection.failure;
                }
                cause = cause.getCause();
            }
            throw failure;
        }
    }

    /** Plans the pending opening batch and then the first executor status/artifact output, atomically. */
    private MutationPlan planOpeningThen(Event event) {
        PendingTaskInput opening = openingInput;
        if (opening == null) {
            throw new IllegalStateException("No retained opening input");
        }
        MutationPlan open = TaskMutationPlanner.planInput(null, opening, MutationOrigin.USER);
        // The planner reads only the task of its base snapshot; the placeholder revision is never stored.
        TaskSnapshot base = new TaskSnapshot(open.task(), new TaskRevision(1));
        MutationPlan next;
        if (event instanceof TaskStatusUpdateEvent statusEvent) {
            next = TaskMutationPlanner.planStatus(base, statusEvent);
        } else if (event instanceof TaskArtifactUpdateEvent artifactEvent) {
            next = TaskMutationPlanner.planInput(base,
                    new PendingTaskInput(null, List.of(artifactEvent), null, null,
                            new PendingTaskInput.Admission(artifactEvent.taskId(), artifactEvent.contextId(), null,
                                    true, true)),
                    MutationOrigin.AGENT);
        } else {
            throw new IllegalArgumentException("Only status or artifact output can follow opening input");
        }
        List<StreamingEventKind> events = new ArrayList<>(open.events());
        events.addAll(next.events());
        return new MutationPlan(next.task(), events, true);
    }

    private MutationPlan planInitialStatus(TaskStatusUpdateEvent event) {
        if (initialMessage != null) {
            MutationPlan opening = TaskMutationPlanner.planInput(null,
                    new PendingTaskInput(initialMessage, List.of(), null, null,
                            new PendingTaskInput.Admission(event.taskId(), event.contextId(), null, true, true)),
                    MutationOrigin.USER);
            Task initialTask = opening.task();
            long generation = 2;
            TaskStatusUpdateEvent canonicalStatus = TaskStatusUpdateEvent.builder(event)
                    .generation(generation).build();
            List<Message> history = new ArrayList<>(initialTask.history());
            List<TimelineEntry> timeline = new ArrayList<>(initialTask.timeline());
            timeline.add(new TimelineEntry(generation, new StatusValue(event.status())));
            Task.Builder builder = Task.builder(initialTask).status(event.status()).history(history)
                    .timeline(timeline).generation(generation);
            if (event.metadata() != null) {
                builder.metadata(event.metadata());
            }
            List<StreamingEventKind> events = new ArrayList<>(opening.events());
            events.add(canonicalStatus);
            return new MutationPlan(builder.build(), events, true);
        }
        long generation = 1;
        TaskStatusUpdateEvent canonical = TaskStatusUpdateEvent.builder(event).generation(generation).build();
        Task.Builder taskBuilder = Task.builder()
                .id(event.taskId())
                .contextId(event.contextId())
                .status(event.status())
                .timeline(List.of(new TimelineEntry(generation, new StatusValue(event.status()))))
                .generation(generation);
        if (event.metadata() != null) {
            taskBuilder.metadata(event.metadata());
        }
        if (initialMessage != null) {
            taskBuilder.history(List.of(initialMessage));
        } else {
            taskBuilder.history(Collections.emptyList());
        }
        Task task = taskBuilder.build();
        return new MutationPlan(task, List.of(canonical), true);
    }

    private MutationPlan planInitialArtifact(TaskArtifactUpdateEvent event) {
        if (Boolean.TRUE.equals(event.append())) {
            throw new InvalidParamsError("Cannot append to an artifact that does not exist");
        }
        Artifact supplied = event.artifact();
        if (supplied.role() != null && supplied.role() != Message.Role.ROLE_AGENT) {
            throw new InvalidParamsError("Artifact origin does not match authenticated mutation origin");
        }
        long generation = 1;
        Artifact artifact = Artifact.builder(supplied)
                .role(Message.Role.ROLE_AGENT)
                .startGeneration(generation)
                .endGeneration(generation)
                .build();
        TaskArtifactUpdateEvent canonical = TaskArtifactUpdateEvent.builder(event)
                .artifact(artifact)
                .generation(generation)
                .build();
        Task task = Task.builder()
                .id(event.taskId())
                .contextId(event.contextId())
                .status(new TaskStatus(TASK_STATE_SUBMITTED))
                .history(initialMessage == null ? Collections.emptyList() : List.of(initialMessage))
                .artifacts(List.of(artifact))
                .timeline(Collections.emptyList())
                .generation(generation)
                .build();
        return new MutationPlan(task, List.of(canonical), true);
    }

    private static CommittedProcessingResult committedResult(CommittedMutation mutation) {
        return new CommittedProcessingResult(isTerminal(mutation.snapshot().task().status().state()), mutation);
    }

    private static boolean isTerminal(TaskState state) {
        return state == TaskState.TASK_STATE_UNSPECIFIED || state.isFinal();
    }

    private static final class StateTransitionRejected extends RuntimeException {

        private final A2AServerException failure;

        private StateTransitionRejected(A2AServerException failure) {
            super(failure);
            this.failure = failure;
        }
    }

    private static final class NoAuthoritativeTask extends RuntimeException {
    }

    public Task updateWithMessage(Message message, Task task) {
        List<Message> history = new ArrayList<>(task.history());

        TaskStatus status = task.status();
        if (status.message() != null) {
            history.add(status.message());
            status = new TaskStatus(status.state(), null, status.timestamp());
        }
        history.add(message);
        task = Task.builder(task)
                .status(status)
                .history(history)
                .build();
        saveTask(task, false);  // Local operation, not replicated
        return task;
    }

    private void checkIdsAndUpdateIfNecessary(String eventTaskId, String eventContextId) throws A2AServerException {
        if (taskId != null && !eventTaskId.equals(taskId)) {
            throw new A2AServerException(
                    "Invalid task id",
                    new InternalError(String.format("Task event has taskId %s but TaskManager has %s", eventTaskId, taskId)));
        }
        if (taskId == null) {
            taskId = eventTaskId;
        }
        if (contextId == null) {
            contextId = eventContextId;
        }
    }

    private Task ensureTask(String eventTaskId, String eventContextId) {
        Task task = currentTask;
        if (task != null) {
            return task;
        }
        // taskId may be null here, but get() accepts @Nullable
        String currentTaskId = taskId;
        if (currentTaskId != null) {
            task = taskStore.get(currentTaskId);
        }
        if (task == null) {
            task = createTask(eventTaskId, eventContextId);
            saveTask(task, false);  // Local operation, not replicated
        }
        return task;
    }

    /**
     * Validates a task state transition before it is persisted.
     * <p>
     * A terminal (final) state must not be overwritten by a <em>different</em> state:
     * once a task is {@code COMPLETED}/{@code FAILED}/{@code CANCELED}/{@code REJECTED}
     * it stays in that state. Events re-arriving with the <em>same</em> final state are
     * allowed, so replicated replays and idempotent retries keep working.
     * <p>
     * Transitions from any non-terminal state to any state are permitted (e.g.
     * SUBMITTED → WORKING → COMPLETED/FAILED/CANCELED, interrupted-state resume flows),
     * matching the transitions the A2A spec and the reference agents exercise.
     *
     * @param currentState the task's current state, or {@code null} if unknown
     * @param newState     the state requested by the event, or {@code null} if unknown
     * @param taskId       the task identifier, used in the error message
     * @throws A2AServerException if the transition would overwrite a terminal state
     */
    private static void validateStateTransition(@Nullable TaskState currentState, @Nullable TaskState newState,
            String taskId) throws A2AServerException {
        if (currentState == null || newState == null) {
            return;
        }
        if (currentState.isFinal() && currentState != newState) {
            throw new A2AServerException(
                    "Task " + taskId + " is already in terminal state " + currentState
                            + " and cannot transition to " + newState,
                    new InternalError("Task " + taskId + " is already in terminal state " + currentState));
        }
    }

    private Task createTask(String taskId, String contextId) {
        List<Message> history = initialMessage != null ? List.of(initialMessage) : Collections.emptyList();
        return Task.builder()
                .id(taskId)
                .contextId(contextId)
                .status(new TaskStatus(TASK_STATE_SUBMITTED))
                .history(history)
                .build();
    }

    private Task saveTask(Task task, boolean isReplicated) {
        taskStore.save(task, isReplicated);
        if (taskId == null) {
            taskId = task.id();
            contextId = task.contextId();
        }
        currentTask = task;
        return currentTask;
    }
}
