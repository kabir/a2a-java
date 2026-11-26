package org.a2aproject.sdk.client;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client-owned registry of tasks whose generation signal proved unreliable, shared by every selected-1.1
 * ordinary entry point of one {@link Client}.
 * <p>
 * Generation zero or two consecutive equal same-task event generations taint a task for the rest of this client's
 * use of it. The taint survives transport reconnects and snapshots (no snapshot ever resets it) and is applied to
 * the per-observation {@link ClientTaskReducer} of every later route. Because no normative interaction-reset
 * boundary is defined, a fresh client is not evidence of a reset either; the registry is simply not shared.
 * A tainted task rejects an explicit input guard locally, before any I/O. Nothing here retries, resends or
 * rebases a request.
 */
final class GenerationReliability {

    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    /** Returns whether ordering, guard and long-poll reliance is disabled for the task. */
    boolean isTainted(String taskId) {
        TaskRecord record = tasks.get(taskId);
        return record != null && record.isTainted();
    }

    void taint(String taskId) {
        record(taskId).taint();
    }

    /**
     * Rejects an explicit generation guard against a tainted task before any request is dispatched. The guard is
     * never dropped or rewritten.
     */
    void requireGuardAllowed(SendInputParams params) throws A2AClientException {
        if (params.configuration() == null || params.configuration().ifGenerationMatch() == null) {
            return;
        }
        for (String taskId : targetTaskIds(params)) {
            if (isTainted(taskId)) {
                throw new A2AClientException("Generation guard rejected locally: generations observed for task "
                        + taskId + " are not reliable (zero or consecutive equal generation)");
            }
        }
    }

    /** Reduces one decoded stream frame, carrying taint into and out of the registry. */
    TaskView reduceFrame(ClientTaskReducer reducer, TransportFrame frame) {
        String taskId = taskIdOf(frame.event());
        carryTaintIn(reducer, taskId);
        TaskView view = reducer.applyFrame(frame);
        carryTaintOut(reducer, taskId);
        return view;
    }

    /** Reduces a known event from a source that cannot supply skipped-variant evidence. */
    TaskView reduceEventWithoutEvidence(ClientTaskReducer reducer, StreamingEventKind event) {
        String taskId = taskIdOf(event);
        carryTaintIn(reducer, taskId);
        reducer.markSkippedEvidenceUnavailable();
        TaskView view = reducer.apply(event);
        carryTaintOut(reducer, taskId);
        return view;
    }

    /**
     * Reduces one decoded unary result. Generation zero, or a generation equal to the previous unary result for
     * the same task, taints it; a direct Message has no task effect and yields no task baseline.
     */
    TaskView reduceUnary(ClientTaskReducer reducer, TransportResult<? extends EventKind> result) {
        EventKind value = result.value();
        if (value instanceof Task task) {
            TaskDecodeEvidence evidence = result.taskEvidence().get(0);
            record(task.id()).observeUnary(task.generation());
            return reduceFrame(reducer, new TransportFrame(task, evidence.unknownSkipped(),
                    evidence.skippedTimelineGenerations(), null, 0));
        }
        return reducer.apply((StreamingEventKind) value);
    }

    /** Reduces a unary result obtained through a source without decode evidence. */
    TaskView reduceUnaryWithoutEvidence(ClientTaskReducer reducer, EventKind value) {
        if (value instanceof Task task) {
            record(task.id()).observeUnary(task.generation());
        }
        return reduceEventWithoutEvidence(reducer, (StreamingEventKind) value);
    }

    static @Nullable String taskIdOf(@Nullable StreamingEventKind event) {
        if (event instanceof Task task) {
            return task.id();
        } else if (event instanceof TaskStatusUpdateEvent status) {
            return status.taskId();
        } else if (event instanceof TaskArtifactUpdateEvent artifact) {
            return artifact.taskId();
        } else if (event instanceof TaskMessageUpdateEvent message) {
            return message.taskId();
        }
        return null;
    }

    private void carryTaintIn(ClientTaskReducer reducer, @Nullable String taskId) {
        if (taskId != null && isTainted(taskId)) {
            reducer.taint();
        }
    }

    private void carryTaintOut(ClientTaskReducer reducer, @Nullable String taskId) {
        if (taskId != null && !reducer.generationReliable()) {
            taint(taskId);
        }
    }

    private TaskRecord record(String taskId) {
        return tasks.computeIfAbsent(taskId, ignored -> new TaskRecord());
    }

    private static Set<String> targetTaskIds(SendInputParams params) {
        Set<String> ids = new HashSet<>();
        if (params.message() != null && params.message().taskId() != null && !params.message().taskId().isBlank()) {
            ids.add(params.message().taskId());
        }
        for (TaskArtifactUpdateEvent update : params.artifactUpdates()) {
            if (update.taskId() != null && !update.taskId().isBlank()) {
                ids.add(update.taskId());
            }
        }
        return ids;
    }

    private static final class TaskRecord {
        private boolean tainted;
        private @Nullable Long lastUnaryGeneration;

        synchronized boolean isTainted() {
            return tainted;
        }

        synchronized void taint() {
            tainted = true;
        }

        synchronized void observeUnary(long generation) {
            if (generation == 0 || (lastUnaryGeneration != null && lastUnaryGeneration == generation)) {
                tainted = true;
            }
            lastUnaryGeneration = generation;
        }
    }
}
