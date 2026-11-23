package org.a2aproject.sdk.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.spec.A2AClientInvalidArgsError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.jspecify.annotations.Nullable;

/**
 * Pure per-task reducer for selected-1.1 generation/timeline semantics.
 * <p>
 * It merges authoritative snapshots and known ordered updates into an immutable {@link TaskView}. It never
 * allocates generations, performs I/O, recovers from gaps or fabricates events. Two notions are kept apart:
 * <ul>
 * <li><b>sequence accounting</b> ({@code lastGeneration}): every observed generation, including known generations
 * of skipped timeline kinds, so no false gap is invented around an unknown kind;</li>
 * <li><b>semantic state</b> ({@code through}): the last safely interpreted generation. Once a gap or an
 * uninterpretable payload is seen, the projection stays frozen at its last safe state until an authoritative
 * snapshot replaces it.</li>
 * </ul>
 * Generation zero or two consecutive equal same-task event generations permanently disable generation
 * reliability for this reducer; a snapshot never resets that.
 */
final class ClientTaskReducer {

    private final @Nullable Integer streamHistoryLimit;
    private final @Nullable Integer streamTimelineLimit;

    private @Nullable String taskId;
    private @Nullable String contextId;
    private @Nullable Task task;
    private boolean baseline;
    private @Nullable Long through;
    private boolean accounted;
    private long lastGeneration;
    private boolean haveLastEvent;
    private long lastEventGeneration;
    private boolean gap;
    private boolean uncertain;
    private boolean tainted;
    private boolean legacyGaps;
    private @Nullable Integer historyLimit;
    private @Nullable Integer timelineLimit;
    private final Set<HistoryCoverage.Flag> historyFlags = EnumSet.noneOf(HistoryCoverage.Flag.class);
    private final Set<HistoryCoverage.Flag> timelineFlags = EnumSet.noneOf(HistoryCoverage.Flag.class);

    ClientTaskReducer() {
        this(null, null);
    }

    /**
     * @param streamHistoryLimit the history window limit requested for Task events received in a stream, or null
     * @param streamTimelineLimit the timeline window limit requested for Task events received in a stream, or null
     */
    ClientTaskReducer(@Nullable Integer streamHistoryLimit, @Nullable Integer streamTimelineLimit) {
        this.streamHistoryLimit = streamHistoryLimit;
        this.streamTimelineLimit = streamTimelineLimit;
    }

    /** Returns false once generation zero or consecutive equal generations were observed. */
    synchronized boolean generationReliable() {
        return !tainted;
    }

    synchronized TaskView view() {
        return buildView();
    }

    /** Records that migration provenance explicitly states earlier entries are missing. */
    synchronized TaskView recordLegacyGaps() {
        legacyGaps = true;
        historyFlags.add(HistoryCoverage.Flag.LEGACY_GAPS);
        timelineFlags.add(HistoryCoverage.Flag.LEGACY_GAPS);
        return buildView();
    }

    /**
     * Adopts an authoritative snapshot (unary result or read), replacing the exposed windows. This is not a
     * stream event: it never counts toward consecutive-equal generation detection.
     */
    synchronized TaskView applySnapshot(Task snapshot, @Nullable TaskDecodeEvidence evidence,
            @Nullable Integer requestedHistoryLimit, @Nullable Integer requestedTimelineLimit) {
        checkIdentity(snapshot.id(), snapshot.contextId());
        boolean unknown = evidence != null
                && (evidence.unknownSkipped() || !evidence.skippedTimelineGenerations().isEmpty());
        adoptSnapshot(snapshot, unknown, requestedHistoryLimit, requestedTimelineLimit);
        return buildView();
    }

    /** Applies one decoded stream frame including its skipped-variant evidence. */
    synchronized TaskView applyFrame(TransportFrame frame) {
        if (frame.streamError() != null) {
            return buildView();
        }
        StreamingEventKind event = frame.event();
        if (event == null) {
            return applyUnknown(frame.skippedTimelineGenerations());
        }
        return applyEventLocked(event, frame.unknownSkipped(), frame.skippedTimelineGenerations());
    }

    /** Applies one known event without skipped-variant evidence. */
    synchronized TaskView apply(StreamingEventKind event) {
        return applyEventLocked(event, false, List.of());
    }

    /**
     * Records an ignored stream payload. Known skipped timeline generations count for sequence accounting; an
     * empty list means the payload had no interpretable generation and a later jump can reveal a real gap.
     */
    synchronized TaskView applyUnknown(List<Long> skippedTimelineGenerations) {
        accountSkipped(skippedTimelineGenerations, Long.MAX_VALUE);
        markUncertain();
        return buildView();
    }

    private TaskView applyEventLocked(StreamingEventKind event, boolean unknownSkipped,
            List<Long> skippedGenerations) {
        if (event instanceof Message) {
            // A direct Message has no task baseline and no task effect.
            if (unknownSkipped) {
                markUncertain();
            }
            return buildView();
        }
        long generation;
        String eventTaskId;
        String eventContextId;
        if (event instanceof Task snapshot) {
            generation = snapshot.generation();
            eventTaskId = snapshot.id();
            eventContextId = snapshot.contextId();
        } else if (event instanceof TaskStatusUpdateEvent status) {
            generation = status.generation();
            eventTaskId = status.taskId();
            eventContextId = status.contextId();
        } else if (event instanceof TaskArtifactUpdateEvent artifact) {
            generation = artifact.generation();
            eventTaskId = artifact.taskId();
            eventContextId = artifact.contextId();
        } else if (event instanceof TaskMessageUpdateEvent message) {
            generation = message.generation();
            eventTaskId = message.taskId();
            eventContextId = message.contextId();
        } else {
            throw new A2AClientInvalidArgsError("Unsupported event " + event.getClass().getName());
        }
        checkIdentity(eventTaskId, eventContextId);

        if (generation == 0 || (haveLastEvent && generation == lastEventGeneration)) {
            tainted = true;
        }
        haveLastEvent = true;
        lastEventGeneration = generation;

        boolean uncertainEvidence = unknownSkipped || !skippedGenerations.isEmpty();
        if (event instanceof Task snapshot) {
            adoptSnapshot(snapshot, uncertainEvidence, streamHistoryLimit, streamTimelineLimit);
            return buildView();
        }
        if (generation == 0) {
            return buildView(); // ordering is meaningless; keep the last safe projection
        }

        accountSkipped(skippedGenerations, generation);
        if (uncertainEvidence) {
            markUncertain();
        }
        if (accounted && generation <= lastGeneration) {
            return buildView(); // duplicate or late stale event: never re-append history or chunks
        }
        if (accounted && generation > lastGeneration + 1) {
            markGap();
            lastGeneration = generation;
            return buildView();
        }
        accounted = true;
        lastGeneration = generation;
        if (gap || uncertain) {
            return buildView(); // preserve the last safely interpreted state
        }
        Task updated = applyUpdate(event, generation);
        if (updated == null) {
            markGap();
            return buildView();
        }
        task = updated;
        if (baseline) {
            through = generation;
        }
        return buildView();
    }

    private @Nullable Task applyUpdate(StreamingEventKind event, long generation) {
        Task current = task != null ? task : partialTask();
        Task.Builder builder = Task.builder(current);
        if (baseline) {
            builder.generation(generation);
        }
        if (event instanceof TaskStatusUpdateEvent status) {
            List<Message> history = new ArrayList<>(current.history());
            Message old = current.status().message();
            Message carried = status.status().message();
            // A carried status message with the same identity is not a newly authored occurrence.
            if (old != null && !(carried != null && Objects.equals(old.messageId(), carried.messageId()))) {
                history.add(old);
            }
            builder.status(status.status()).history(history)
                    .timeline(appendTimeline(current, new TimelineEntry(generation,
                            new TimelineEntry.StatusValue(status.status()))));
            Map<String, Object> metadata = mergeMetadata(current.metadata(), status.metadata());
            if (metadata != null) {
                builder.metadata(metadata);
            }
            return builder.build();
        }
        if (event instanceof TaskMessageUpdateEvent message) {
            List<Message> history = new ArrayList<>(current.history());
            Message old = current.status().message();
            if (old != null) {
                history.add(old);
                builder.status(new TaskStatus(current.status().state(), null, current.status().timestamp()));
            }
            history.add(message.message());
            builder.history(history).timeline(appendTimeline(current, new TimelineEntry(generation,
                    new TimelineEntry.MessageValue(message.message()))));
            Map<String, Object> metadata = mergeMetadata(current.metadata(), message.metadata());
            if (metadata != null) {
                builder.metadata(metadata);
            }
            return builder.build();
        }
        TaskArtifactUpdateEvent artifactUpdate = (TaskArtifactUpdateEvent) event;
        List<Artifact> artifacts = new ArrayList<>(current.artifacts());
        Artifact incoming = artifactUpdate.artifact();
        int index = -1;
        for (int i = 0; i < artifacts.size(); i++) {
            if (artifacts.get(i).artifactId().equals(incoming.artifactId())) {
                index = i;
                break;
            }
        }
        if (Boolean.TRUE.equals(artifactUpdate.append())) {
            if (index < 0) {
                if (baseline) {
                    return null; // an earlier chunk is missing: do not fabricate it
                }
                artifacts.add(incoming);
            } else {
                Artifact existing = artifacts.get(index);
                List<Part<?>> parts = new ArrayList<>(existing.parts());
                parts.addAll(incoming.parts());
                long start = incoming.startGeneration() > 0 ? incoming.startGeneration()
                        : existing.startGeneration() > 0 ? existing.startGeneration() : generation;
                long end = incoming.endGeneration() > 0 ? incoming.endGeneration() : generation;
                artifacts.set(index, Artifact.builder(existing).parts(parts)
                        .metadata(mergeMetadata(existing.metadata(), incoming.metadata()))
                        .startGeneration(start).endGeneration(end)
                        .role(incoming.role() != null ? incoming.role() : existing.role()).build());
            }
        } else if (index >= 0) {
            artifacts.set(index, incoming);
        } else {
            artifacts.add(incoming);
        }
        return builder.artifacts(artifacts).build();
    }

    private static List<TimelineEntry> appendTimeline(Task current, TimelineEntry entry) {
        List<TimelineEntry> timeline = new ArrayList<>(current.timeline() == null ? List.of() : current.timeline());
        timeline.add(entry);
        return timeline;
    }

    private Task partialTask() {
        return Task.builder().id(Objects.requireNonNull(taskId))
                .contextId(contextId == null ? "" : contextId)
                .status(new TaskStatus(TaskState.TASK_STATE_UNSPECIFIED))
                .artifacts(List.of()).history(List.of()).timeline(List.of()).generation(0).build();
    }

    private void adoptSnapshot(Task snapshot, boolean unknown, @Nullable Integer requestedHistoryLimit,
            @Nullable Integer requestedTimelineLimit) {
        long generation = snapshot.generation();
        if (baseline && generation != 0 && accounted && generation < lastGeneration) {
            return; // stale relative to what was already observed; keep the last safe state
        }
        task = snapshot;
        baseline = true;
        gap = false;
        uncertain = unknown;
        through = generation == 0 ? null : generation;
        accounted = generation != 0;
        lastGeneration = generation;
        historyLimit = requestedHistoryLimit;
        timelineLimit = requestedTimelineLimit;
        historyFlags.clear();
        timelineFlags.clear();
        historyFlags.add(HistoryCoverage.Flag.WINDOW_ONLY);
        timelineFlags.add(HistoryCoverage.Flag.WINDOW_ONLY);
        if (snapshot.timeline() == null) {
            timelineFlags.add(HistoryCoverage.Flag.NOT_ASSERTED);
        }
        if (unknown) {
            historyFlags.add(HistoryCoverage.Flag.UNKNOWN_SKIPPED);
            timelineFlags.add(HistoryCoverage.Flag.UNKNOWN_SKIPPED);
        }
        if (legacyGaps) {
            historyFlags.add(HistoryCoverage.Flag.LEGACY_GAPS);
            timelineFlags.add(HistoryCoverage.Flag.LEGACY_GAPS);
        }
    }

    /** Counts known generations of skipped timeline kinds without inventing a gap around them. */
    private void accountSkipped(List<Long> skippedGenerations, long exclusiveUpperBound) {
        for (long skipped : new TreeSet<>(skippedGenerations)) {
            if (skipped <= 0 || skipped >= exclusiveUpperBound) {
                continue;
            }
            if (!accounted) {
                accounted = true;
                lastGeneration = skipped;
            } else if (skipped == lastGeneration + 1) {
                lastGeneration = skipped;
            } else if (skipped > lastGeneration + 1) {
                markGap();
                lastGeneration = skipped;
            }
        }
    }

    private void markUncertain() {
        uncertain = true;
        historyFlags.add(HistoryCoverage.Flag.UNKNOWN_SKIPPED);
        timelineFlags.add(HistoryCoverage.Flag.UNKNOWN_SKIPPED);
    }

    private void markGap() {
        gap = true;
        historyFlags.add(HistoryCoverage.Flag.NOT_ASSERTED);
        timelineFlags.add(HistoryCoverage.Flag.NOT_ASSERTED);
    }

    private void checkIdentity(String eventTaskId, String eventContextId) {
        String knownTaskId = taskId;
        if (knownTaskId == null) {
            taskId = eventTaskId;
            contextId = eventContextId;
            return;
        }
        if (!knownTaskId.equals(eventTaskId) || (contextId != null && !contextId.equals(eventContextId))) {
            throw new A2AClientInvalidArgsError("Event identity does not match the reduced task");
        }
    }

    private TaskView buildView() {
        Long watermark = baseline ? through : null;
        ViewSync sync = !baseline ? ViewSync.NO_BASELINE
                : (gap || uncertain || tainted || watermark == null) ? ViewSync.INCOMPLETE : ViewSync.SYNCHRONIZED;
        Task current = task;
        Set<HistoryCoverage.Flag> history = EnumSet.noneOf(HistoryCoverage.Flag.class);
        history.addAll(historyFlags);
        Set<HistoryCoverage.Flag> timeline = EnumSet.noneOf(HistoryCoverage.Flag.class);
        timeline.addAll(timelineFlags);
        if (!baseline) {
            history.add(HistoryCoverage.Flag.NOT_ASSERTED);
            timeline.add(HistoryCoverage.Flag.NOT_ASSERTED);
        }
        HistoryCoverage.Range range = null;
        List<TimelineEntry> entries = current == null ? null : current.timeline();
        if (baseline && entries != null && !entries.isEmpty()) {
            long first = Long.MAX_VALUE;
            long last = Long.MIN_VALUE;
            for (TimelineEntry entry : entries) {
                first = Math.min(first, entry.generation());
                last = Math.max(last, entry.generation());
            }
            range = new HistoryCoverage.Range(first, last);
        }
        return new TaskView(current, sync, watermark,
                new HistoryCoverage(baseline ? historyLimit : null, null, history),
                new HistoryCoverage(baseline ? timelineLimit : null, range, timeline));
    }

    private static @Nullable Map<String, Object> mergeMetadata(@Nullable Map<String, Object> existing,
            @Nullable Map<String, Object> update) {
        if (existing == null && update == null) {
            return null;
        }
        Map<String, Object> merged = new HashMap<>();
        if (existing != null) {
            merged.putAll(existing);
        }
        if (update != null) {
            merged.putAll(update);
        }
        return Collections.unmodifiableMap(merged);
    }
}
