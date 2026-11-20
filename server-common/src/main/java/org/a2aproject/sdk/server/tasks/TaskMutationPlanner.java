package org.a2aproject.sdk.server.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Message.Role;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.a2aproject.sdk.spec.TimelineEntry.MessageValue;
import org.a2aproject.sdk.spec.TimelineEntry.StatusValue;
import org.a2aproject.sdk.spec.util.CollectionCopies;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/** Pure canonical generation, timeline, status, and artifact mutation rules. */
public final class TaskMutationPlanner {

    private TaskMutationPlanner() {
    }

    /** Plans one atomic input batch against the authoritative pre-batch snapshot. */
    public static MutationPlan planInput(@Nullable TaskSnapshot current, PendingTaskInput input,
            MutationOrigin origin) {
        Assert.checkNotNullParam("input", input);
        Assert.checkNotNullParam("origin", origin);
        PendingTaskInput.Admission admission = input.admission();
        if (!hasIdentity(admission.taskId()) || !hasIdentity(admission.contextId())) {
            throw invalid("Admitted task and context IDs must be non-empty");
        }
        if (!admission.authorized()) {
            throw new TaskNotFoundError();
        }

        Task task = current == null ? null : current.task();
        if (current != null) {
            Task authoritativeTask = current.task();
            if (!authoritativeTask.id().equals(admission.taskId())
                    || !authoritativeTask.contextId().equals(admission.contextId())) {
                throw invalid("Input task or context identity does not match the authoritative task");
            }
            task = authoritativeTask;
        }

        Long expectedGeneration = input.configuration() == null ? null : input.configuration().ifGenerationMatch();
        if (expectedGeneration != null
                && (task == null || task.generation() != expectedGeneration.longValue())) {
            throw new TaskGenerationMismatchError();
        }
        if (task != null) {
            rejectTerminal(task);
        } else if (input.message() == null) {
            throw invalid("Artifact-only input requires an existing task");
        }
        if (origin == MutationOrigin.USER && !input.artifactUpdates().isEmpty()
                && !admission.userArtifactsEnabled()) {
            throw invalid("User artifacts are not enabled by the effective agent card");
        }

        List<StreamingEventKind> events = new ArrayList<>();
        List<TimelineEntry> timeline = new ArrayList<>();
        List<Message> history = new ArrayList<>();
        List<Artifact> artifacts = new ArrayList<>();
        TaskStatus status;
        long generation;
        if (task == null) {
            status = new TaskStatus(TaskState.TASK_STATE_SUBMITTED, null, input.receivedAt());
            generation = 0;
        } else {
            history.addAll(task.history());
            timeline.addAll(task.timeline() == null ? List.of() : task.timeline());
            artifacts.addAll(task.artifacts());
            status = task.status();
            generation = task.generation();
        }

        if (input.message() != null) {
            Message message = canonicalMessage(input.message(), admission);
            if (status.message() != null) {
                history.add(status.message());
                status = new TaskStatus(status.state(), null, status.timestamp());
            }
            generation = nextGeneration(generation);
            history.add(message);
            timeline.add(new TimelineEntry(generation, new MessageValue(message)));
            events.add(new TaskMessageUpdateEvent(admission.taskId(), admission.contextId(), message,
                    input.metadata(), generation));
        }

        for (TaskArtifactUpdateEvent supplied : input.artifactUpdates()) {
            validateInputIdentity(supplied, admission, task == null && input.message() != null);
            generation = nextGeneration(generation);
            CanonicalArtifact canonical = canonicalArtifact(artifacts, supplied, origin, generation);
            TaskArtifactUpdateEvent event = TaskArtifactUpdateEvent.builder(supplied)
                    .taskId(admission.taskId())
                    .contextId(admission.contextId())
                    .artifact(canonical.eventArtifact())
                    .generation(generation)
                    .build();
            events.add(event);
        }

        if (events.isEmpty()) {
            throw invalid("Input did not produce a task mutation");
        }

        Task finalTask = task == null
                ? Task.builder().id(admission.taskId()).contextId(admission.contextId()).status(status)
                        .history(history).artifacts(artifacts).timeline(timeline).generation(generation).build()
                : Task.builder(task).status(status).history(history).artifacts(artifacts).timeline(timeline)
                        .generation(generation).build();
        return new MutationPlan(finalTask, events, true);
    }

    /** Plans one status progress event, including same-state progress, against current state. */
    public static MutationPlan planStatus(TaskSnapshot current, TaskStatusUpdateEvent supplied) {
        Assert.checkNotNullParam("current", current);
        Assert.checkNotNullParam("supplied", supplied);
        Task task = current.task();
        if (!task.id().equals(supplied.taskId()) || !task.contextId().equals(supplied.contextId())) {
            throw invalid("Status task or context identity does not match the authoritative task");
        }
        rejectTerminal(task);

        long generation = nextGeneration(task.generation());
        List<Message> history = new ArrayList<>(task.history());
        if (task.status().message() != null) {
            history.add(task.status().message());
        }
        List<TimelineEntry> timeline = new ArrayList<>(task.timeline() == null ? List.of() : task.timeline());
        timeline.add(new TimelineEntry(generation, new StatusValue(supplied.status())));
        Map<String, Object> metadata = mergeMetadata(task.metadata(), supplied.metadata());
        Task.Builder taskBuilder = Task.builder(task).status(supplied.status()).history(history).timeline(timeline)
                .generation(generation);
        if (metadata != null) {
            taskBuilder.metadata(metadata);
        }
        Task finalTask = taskBuilder.build();
        TaskStatusUpdateEvent canonical = TaskStatusUpdateEvent.builder(supplied).generation(generation).build();
        return new MutationPlan(finalTask, List.of(canonical), true);
    }

    /** Plans a matching-base full Task proposal for an existing task. */
    public static MutationPlan planTaskProposal(TaskSnapshot current, Task proposed) {
        return planTaskProposal(current, proposed, null);
    }

    /**
     * Plans a full Task proposal. A null current snapshot is an initial materialization and may
     * include request-local opening input; it never persists that input before this atomic plan.
     */
    public static MutationPlan planTaskProposal(@Nullable TaskSnapshot current, Task proposed,
            @Nullable PendingTaskInput openingInput) {
        Assert.checkNotNullParam("proposed", proposed);
        if (current == null) {
            return planInitialTaskProposal(proposed, openingInput);
        }

        Task task = current.task();
        if (!task.id().equals(proposed.id()) || !task.contextId().equals(proposed.contextId())) {
            throw invalid("Task proposal identity does not match the authoritative task");
        }
        return normalizeProposal(task, proposed, new ArrayList<>(), false);
    }

    private static MutationPlan planInitialTaskProposal(Task proposed, @Nullable PendingTaskInput openingInput) {
        List<StreamingEventKind> events = new ArrayList<>();
        Task initial;
        if (openingInput == null) {
            initial = Task.builder().id(proposed.id()).contextId(proposed.contextId())
                    .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                    .artifacts(List.of()).history(List.of()).timeline(List.of()).generation(0).build();
        } else {
            PendingTaskInput.Admission admission = openingInput.admission();
            if (!proposed.id().equals(admission.taskId()) || !proposed.contextId().equals(admission.contextId())) {
                throw invalid("Initial Task identity does not match the opening request");
            }
            MutationPlan opening = planInput(null, openingInput, MutationOrigin.USER);
            initial = opening.task();
            events.addAll(opening.events());
        }

        List<Message> bootstrapHistory = new ArrayList<>();
        Message openingMessage = openingInput == null ? null : openingInput.message();
        Message canonicalOpening = null;
        for (StreamingEventKind event : events) {
            if (event instanceof TaskMessageUpdateEvent messageUpdate) {
                canonicalOpening = messageUpdate.message();
                break;
            }
        }
        boolean openingIncluded = false;
        if (proposed.history() != null) {
            for (Message message : proposed.history()) {
                if (openingMessage != null && Objects.equals(openingMessage.messageId(), message.messageId())) {
                    if (!openingIncluded) {
                        bootstrapHistory.add(canonicalOpening == null ? openingMessage : canonicalOpening);
                        openingIncluded = true;
                    }
                } else {
                    bootstrapHistory.add(message);
                }
            }
        }
        if (openingMessage != null && !openingIncluded) {
            bootstrapHistory.add(canonicalOpening == null ? openingMessage : canonicalOpening);
        }
        Task bootstrapped = Task.builder(initial).history(bootstrapHistory).build();
        return normalizeProposal(bootstrapped, proposed, events, true);
    }

    private static MutationPlan normalizeProposal(Task startingTask, Task proposed,
            List<StreamingEventKind> events, boolean initial) {
        List<Artifact> suppliedArtifacts = proposed.artifacts() == null ? List.of() : proposed.artifacts();
        List<Artifact> artifacts = new ArrayList<>(startingTask.artifacts());
        List<Artifact> changedArtifacts = new ArrayList<>();
        Set<String> suppliedIds = new HashSet<>();
        for (Artifact supplied : suppliedArtifacts) {
            if (!suppliedIds.add(supplied.artifactId())) {
                throw invalid("Task proposal contains duplicate artifact IDs");
            }
            Artifact stored = findArtifact(artifacts, supplied.artifactId());
            if (stored != null && sameArtifactContent(stored, supplied)) {
                continue;
            }
            Role suppliedRole = supplied.role();
            if (suppliedRole != null && suppliedRole != Role.ROLE_AGENT) {
                throw invalid("Task proposal cannot create or edit a non-agent artifact");
            }
            if (stored != null && (stored.role() == Role.ROLE_USER)) {
                throw invalid("Task proposal cannot edit an artifact created by another origin");
            }
            changedArtifacts.add(supplied);
        }

        TaskStatus suppliedStatus = proposed.status();
        boolean statusChanged = !startingTask.status().equals(suppliedStatus);
        Map<String, Object> metadata = proposed.metadata() == null
                ? startingTask.metadata() : mergeMetadata(startingTask.metadata(), proposed.metadata());
        boolean metadataChanged = !Objects.equals(metadata, startingTask.metadata());
        boolean hasChanges = statusChanged || metadataChanged || !changedArtifacts.isEmpty();
        if (!hasChanges && !initial) {
            return new MutationPlan(startingTask, List.of(), false);
        }
        if (!initial && hasChanges) {
            rejectTerminal(startingTask);
        }

        long generation = startingTask.generation();
        Task task = startingTask;
        @Nullable Map<String, Object> metadataUpdate = metadataChanged ? proposed.metadata() : null;
        boolean terminalStatus = statusChanged && suppliedStatus.state().isFinal();
        boolean statusObservation = statusChanged || metadataChanged;

        if (statusObservation && !terminalStatus) {
            generation = nextGeneration(generation);
            task = applyProposalStatus(task, statusChanged ? suppliedStatus : task.status(), metadataUpdate,
                    metadata, generation, !statusChanged, events);
        }

        for (Artifact supplied : changedArtifacts) {
            generation = nextGeneration(generation);
            TaskArtifactUpdateEvent rawEvent = TaskArtifactUpdateEvent.builder()
                    .taskId(task.id()).contextId(task.contextId()).artifact(supplied)
                    .append(false).lastChunk(true).generation(generation).build();
            CanonicalArtifact canonical = canonicalArtifact(artifacts, rawEvent, MutationOrigin.AGENT, generation);
            TaskArtifactUpdateEvent event = TaskArtifactUpdateEvent.builder(rawEvent)
                    .artifact(canonical.eventArtifact()).build();
            events.add(event);
            task = Task.builder(task).artifacts(artifacts).generation(generation).build();
        }

        if (terminalStatus) {
            generation = nextGeneration(generation);
            task = applyProposalStatus(task, suppliedStatus, metadataUpdate, metadata, generation, false, events);
        }

        if (metadata != null && !Objects.equals(task.metadata(), metadata)) {
            task = Task.builder(task).metadata(metadata).build();
        }
        boolean changed = initial || !events.isEmpty();
        if (!changed) {
            return new MutationPlan(startingTask, List.of(), false);
        }
        return new MutationPlan(task, events, true);
    }

    private static Task applyProposalStatus(Task task, TaskStatus status,
            @Nullable Map<String, Object> metadataUpdate, @Nullable Map<String, Object> mergedMetadata,
            long generation, boolean syntheticMetadata, List<StreamingEventKind> events) {
        List<Message> history = new ArrayList<>(task.history());
        if (!syntheticMetadata && task.status().message() != null) {
            history.add(task.status().message());
        }
        List<TimelineEntry> timeline = new ArrayList<>(task.timeline() == null ? List.of() : task.timeline());
        timeline.add(new TimelineEntry(generation, new StatusValue(status)));
        Task.Builder builder = Task.builder(task).status(status).history(history).timeline(timeline)
                .generation(generation);
        if (mergedMetadata != null) {
            builder.metadata(mergedMetadata);
        }
        TaskStatusUpdateEvent.Builder eventBuilder = TaskStatusUpdateEvent.builder().taskId(task.id())
                .contextId(task.contextId()).status(status).generation(generation);
        if (metadataUpdate != null) {
            eventBuilder.metadata(metadataUpdate);
        }
        TaskStatusUpdateEvent event = eventBuilder.build();
        events.add(event);
        return builder.build();
    }

    private static @Nullable Artifact findArtifact(List<Artifact> artifacts, String artifactId) {
        for (Artifact artifact : artifacts) {
            if (artifact.artifactId().equals(artifactId)) {
                return artifact;
            }
        }
        return null;
    }

    private static boolean sameArtifactContent(Artifact stored, Artifact supplied) {
        Role storedRole = stored.role() == null ? Role.ROLE_AGENT : stored.role();
        Role suppliedRole = supplied.role() == null ? Role.ROLE_AGENT : supplied.role();
        return stored.artifactId().equals(supplied.artifactId())
                && Objects.equals(stored.name(), supplied.name())
                && Objects.equals(stored.description(), supplied.description())
                && stored.parts().equals(supplied.parts())
                && Objects.equals(stored.metadata(), supplied.metadata())
                && Objects.equals(stored.extensions(), supplied.extensions())
                && storedRole == suppliedRole;
    }

    private static Message canonicalMessage(Message message, PendingTaskInput.Admission admission) {
        if (hasIdentity(message.taskId()) && !admission.taskId().equals(message.taskId())) {
            throw invalid("Message task ID does not match the admitted task");
        }
        if (hasIdentity(message.contextId()) && !admission.contextId().equals(message.contextId())) {
            throw invalid("Message context ID does not match the admitted context");
        }
        return Message.builder(message).taskId(admission.taskId()).contextId(admission.contextId()).build();
    }

    private static void validateInputIdentity(TaskArtifactUpdateEvent update, PendingTaskInput.Admission admission,
            boolean opening) {
        boolean blankTaskId = !hasIdentity(update.taskId());
        boolean blankContextId = !hasIdentity(update.contextId());
        if (blankTaskId || blankContextId) {
            if (opening && blankTaskId && blankContextId) {
                return;
            }
            throw invalid("Artifact update task and context IDs must match the admitted task");
        }
        if (!admission.taskId().equals(update.taskId()) || !admission.contextId().equals(update.contextId())) {
            throw invalid("Artifact update task or context identity does not match the admitted task");
        }
    }

    private static CanonicalArtifact canonicalArtifact(List<Artifact> artifacts, TaskArtifactUpdateEvent update,
            MutationOrigin origin, long generation) {
        Artifact incoming = update.artifact();
        Role expectedRole = origin.artifactRole();
        if (incoming.role() != null && incoming.role() != expectedRole) {
            throw invalid("Artifact origin does not match authenticated mutation origin");
        }

        int index = -1;
        Artifact existing = null;
        for (int i = 0; i < artifacts.size(); i++) {
            if (artifacts.get(i).artifactId().equals(incoming.artifactId())) {
                index = i;
                existing = artifacts.get(i);
                break;
            }
        }
        Role creator = existing == null ? expectedRole
                : existing.role() == null ? Role.ROLE_AGENT : existing.role();
        if (creator != expectedRole) {
            throw invalid("Mutation origin cannot edit an artifact created by another origin");
        }

        Artifact storedArtifact;
        Artifact eventArtifact;
        if (Boolean.TRUE.equals(update.append())) {
            if (existing == null) {
                throw invalid("Cannot append to an artifact that does not exist");
            }
            List<org.a2aproject.sdk.spec.Part<?>> parts = new ArrayList<>(existing.parts());
            parts.addAll(incoming.parts());
            long startGeneration = existing.startGeneration() > 0 ? existing.startGeneration() : generation;
            storedArtifact = Artifact.builder(existing)
                    .parts(parts)
                    .metadata(mergeMetadata(existing.metadata(), incoming.metadata()))
                    .startGeneration(startGeneration)
                    .endGeneration(generation)
                    .role(creator)
                    .build();
            eventArtifact = Artifact.builder(incoming)
                    .startGeneration(startGeneration)
                    .endGeneration(generation)
                    .role(creator)
                    .build();
            artifacts.set(index, storedArtifact);
        } else {
            storedArtifact = Artifact.builder(incoming)
                    .startGeneration(generation)
                    .endGeneration(generation)
                    .role(creator)
                    .build();
            eventArtifact = storedArtifact;
            if (existing == null) {
                artifacts.add(storedArtifact);
            } else {
                artifacts.set(index, storedArtifact);
            }
        }
        return new CanonicalArtifact(storedArtifact, eventArtifact);
    }

    private record CanonicalArtifact(Artifact storedArtifact, Artifact eventArtifact) {
    }

    private static @Nullable Map<String, Object> mergeMetadata(@Nullable Map<String, Object> existing,
            @Nullable Map<String, Object> update) {
        if (existing == null && update == null) {
            return null;
        }
        Map<String, Object> metadata = new HashMap<>();
        if (existing != null) {
            metadata.putAll(existing);
        }
        if (update != null) {
            metadata.putAll(update);
        }
        return CollectionCopies.unmodifiableNullableShallowMap(metadata);
    }

    private static void rejectTerminal(Task task) {
        if (task.status().state().isFinal() || task.status().state() == TaskState.TASK_STATE_UNSPECIFIED) {
            throw invalid("Terminal tasks cannot accept fresh mutations");
        }
    }

    private static long nextGeneration(long current) {
        try {
            return Math.addExact(current, 1L);
        } catch (ArithmeticException failure) {
            throw invalid("Task generation is exhausted");
        }
    }

    private static boolean hasIdentity(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private static InvalidParamsError invalid(String message) {
        return new InvalidParamsError(message);
    }
}
