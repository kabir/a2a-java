package org.a2aproject.sdk.server.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
