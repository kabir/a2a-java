package org.a2aproject.sdk.server.apps.common;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.a2aproject.sdk.server.events.EventEnqueueHook;
import org.a2aproject.sdk.server.events.EventQueueItem;
import org.a2aproject.sdk.server.events.StreamCloseHandle;
import org.a2aproject.sdk.server.events.TaskStreamLifecycleHook;
import org.a2aproject.sdk.spec.Event;

/**
 * Records, per task id, which queues were created and which events reached the replication seam
 * ({@link EventEnqueueHook}) and the published-to-observers seam ({@link TaskStreamLifecycleHook#onEvent}).
 * It is installed only by {@link Phase3SpyQueueManager}, which the Phase 3 test profile selects.
 */
public final class Phase3Spy {

    private static final Set<String> QUEUES_CREATED = ConcurrentHashMap.newKeySet();
    private static final Set<String> QUEUES_CLOSED = ConcurrentHashMap.newKeySet();
    private static final Map<String, List<String>> REPLICATION = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> PUBLICATION = new ConcurrentHashMap<>();

    private Phase3Spy() {
    }

    static void queueCreated(String taskId) {
        QUEUES_CREATED.add(taskId);
    }

    static void queueClosed(String taskId) {
        QUEUES_CLOSED.add(taskId);
    }

    /** True if the main queue created for the task id has been closed (its close callback ran). */
    public static boolean queueWasClosed(String taskId) {
        return QUEUES_CLOSED.contains(taskId);
    }

    /** True if a main queue was ever created for the task id since the server started. */
    public static boolean queueWasCreated(String taskId) {
        return QUEUES_CREATED.contains(taskId);
    }

    /** Event type names handed to the replication hook for the task, in order. */
    public static List<String> replicated(String taskId) {
        return List.copyOf(REPLICATION.getOrDefault(taskId, List.of()));
    }

    /** Event type names published to the task's observers, in order. */
    public static List<String> published(String taskId) {
        return List.copyOf(PUBLICATION.getOrDefault(taskId, List.of()));
    }

    static EventEnqueueHook replicationHook(String taskId) {
        return new EventEnqueueHook() {
            @Override
            public void onEnqueue(EventQueueItem item) {
                REPLICATION.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>())
                        .add(item.getEvent().getClass().getSimpleName());
            }
        };
    }

    static TaskStreamLifecycleHook publicationHook(TaskStreamLifecycleHook delegate) {
        return new TaskStreamLifecycleHook() {
            @Override
            public void onSubscribe(String taskId, StreamCloseHandle handle) {
                delegate.onSubscribe(taskId, handle);
            }

            @Override
            public void onUnsubscribe(String taskId, StreamCloseHandle handle) {
                delegate.onUnsubscribe(taskId, handle);
            }

            @Override
            public void onEvent(String taskId, Event event, StreamCloseHandle handle) {
                PUBLICATION.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>())
                        .add(event.getClass().getSimpleName());
                delegate.onEvent(taskId, event, handle);
            }
        };
    }
}
