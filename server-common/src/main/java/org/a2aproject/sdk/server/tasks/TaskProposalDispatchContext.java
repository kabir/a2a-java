package org.a2aproject.sdk.server.tasks;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.jspecify.annotations.Nullable;

/** Context retained while older sender wrappers forward proposal events to worker threads. */
final class TaskProposalDispatchContext {
    private static final Set<DispatchScope> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Set<ArtifactCopyScope> ACTIVE_ARTIFACT_COPY_SCOPES = ConcurrentHashMap.newKeySet();

    private TaskProposalDispatchContext() {
    }

    static boolean withBatch(CommittedTaskBatch batch, Task taskSnapshot, BooleanSupplier dispatch) {
        return dispatchWithBatch(batch, null, taskSnapshot, false, dispatch);
    }

    static boolean withBatchExcept(CommittedTaskBatch batch, StreamingEventKind excludedEvent, Task taskSnapshot,
            BooleanSupplier dispatch) {
        return dispatchWithBatch(batch, excludedEvent, taskSnapshot, true, dispatch);
    }

    static boolean withArtifactForwarding(TaskArtifactUpdateEvent artifactUpdate, Task taskSnapshot,
            BooleanSupplier dispatch) {
        DispatchScope dispatchScope = findScope(artifactUpdate, taskSnapshot);
        if (dispatchScope == null) {
            return dispatch.getAsBoolean();
        }
        ArtifactCopyScope scope = new ArtifactCopyScope(artifactUpdate, dispatchScope);
        ACTIVE_ARTIFACT_COPY_SCOPES.add(scope);
        try {
            return dispatch.getAsBoolean();
        } finally {
            ACTIVE_ARTIFACT_COPY_SCOPES.remove(scope);
        }
    }

    private static boolean dispatchWithBatch(CommittedTaskBatch batch, @Nullable StreamingEventKind excludedEvent,
            Task taskSnapshot, boolean summaryViaStatus, BooleanSupplier dispatch) {
        DispatchScope scope = new DispatchScope(batch, excludedEvent, taskSnapshot, summaryViaStatus);
        ACTIVE.add(scope);
        try {
            return dispatch.getAsBoolean();
        } finally {
            ACTIVE.remove(scope);
        }
    }

    static boolean contains(StreamingEventKind event, @Nullable Task taskSnapshot) {
        return findOriginalEventScope(event, taskSnapshot) != null;
    }

    static boolean claimLegacySummary(StreamingEventKind event, @Nullable Task taskSnapshot, Object recipient) {
        DispatchScope scope = findOriginalEventScope(event, taskSnapshot);
        if (scope == null) {
            scope = findScope(event, taskSnapshot);
        }
        return scope != null && !scope.summaryViaStatus
                && scope.legacySummaryRecipients.add(new SenderIdentity(recipient));
    }

    static boolean isForwardedArtifactCopy(StreamingEventKind event, @Nullable Task taskSnapshot) {
        return findArtifactCopyScope(event, taskSnapshot) != null;
    }

    private static @Nullable DispatchScope findScope(StreamingEventKind event, @Nullable Task taskSnapshot) {
        if (taskSnapshot == null) {
            return null;
        }
        for (DispatchScope scope : ACTIVE) {
            if (scope.taskSnapshot == taskSnapshot
                    && event != scope.excludedEvent
                    && scope.batch.events().stream().anyMatch(candidate -> candidate == event)) {
                return scope;
            }
        }
        ArtifactCopyScope artifactCopyScope = findArtifactCopyScope(event, taskSnapshot);
        return artifactCopyScope == null ? null : artifactCopyScope.dispatchScope;
    }

    private static @Nullable DispatchScope findOriginalEventScope(StreamingEventKind event,
            @Nullable Task taskSnapshot) {
        if (taskSnapshot == null) {
            return null;
        }
        for (DispatchScope scope : ACTIVE) {
            if (scope.batch.snapshot().task() != taskSnapshot
                    && scope.taskSnapshot.equals(taskSnapshot)
                    && event != scope.excludedEvent
                    && scope.batch.events().stream().anyMatch(candidate -> candidate == event)) {
                return scope;
            }
        }
        return null;
    }

    private static @Nullable ArtifactCopyScope findArtifactCopyScope(StreamingEventKind event,
            @Nullable Task taskSnapshot) {
        if (!(event instanceof TaskArtifactUpdateEvent artifactUpdate) || taskSnapshot == null) {
            return null;
        }
        return ACTIVE_ARTIFACT_COPY_SCOPES.stream().filter(scope -> ACTIVE.contains(scope.dispatchScope)
                && scope.dispatchScope.batch.snapshot().task() != taskSnapshot
                && scope.dispatchScope.taskSnapshot.equals(taskSnapshot)
                && scope.artifactUpdate != artifactUpdate && scope.artifactUpdate.equals(artifactUpdate))
                .findFirst().orElse(null);
    }

    private static final class DispatchScope {
        private final CommittedTaskBatch batch;
        private final @Nullable StreamingEventKind excludedEvent;
        private final Task taskSnapshot;
        private final boolean summaryViaStatus;
        private final Set<SenderIdentity> legacySummaryRecipients = ConcurrentHashMap.newKeySet();

        private DispatchScope(CommittedTaskBatch batch, @Nullable StreamingEventKind excludedEvent,
                Task taskSnapshot, boolean summaryViaStatus) {
            this.batch = batch;
            this.excludedEvent = excludedEvent;
            this.taskSnapshot = taskSnapshot;
            this.summaryViaStatus = summaryViaStatus;
        }
    }

    private static final class SenderIdentity {
        private final Object sender;

        private SenderIdentity(Object sender) {
            this.sender = sender;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SenderIdentity identity && identity.sender == sender;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(sender);
        }
    }

    private static final class ArtifactCopyScope {
        private final TaskArtifactUpdateEvent artifactUpdate;
        private final DispatchScope dispatchScope;

        private ArtifactCopyScope(TaskArtifactUpdateEvent artifactUpdate, DispatchScope dispatchScope) {
            this.artifactUpdate = artifactUpdate;
            this.dispatchScope = dispatchScope;
        }
    }
}
