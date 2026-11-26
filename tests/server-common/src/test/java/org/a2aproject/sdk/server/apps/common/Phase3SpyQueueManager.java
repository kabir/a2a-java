package org.a2aproject.sdk.server.apps.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.a2aproject.sdk.server.events.EventQueue;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.TaskStreamLifecycleHook;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;

/**
 * In-memory queue manager that additionally installs the replication-seam hook and a publication-recording lifecycle
 * hook on every queue. It is an unselected alternative: only the Phase 3 test profile enables it, so other suites
 * keep the plain {@link InMemoryQueueManager}.
 */
@ApplicationScoped
@Alternative
public class Phase3SpyQueueManager extends InMemoryQueueManager {

    @SuppressWarnings("NullAway")
    protected Phase3SpyQueueManager() {
        super();
    }

    @Inject
    public Phase3SpyQueueManager(TaskStateProvider taskStateProvider, MainEventBus mainEventBus,
            TaskStreamLifecycleHook streamLifecycleHook) {
        super(taskStateProvider, mainEventBus, Phase3Spy.publicationHook(streamLifecycleHook));
    }

    @Override
    public EventQueue.EventQueueBuilder createBaseEventQueueBuilder(String taskId) {
        Phase3Spy.queueCreated(taskId);
        return super.createBaseEventQueueBuilder(taskId).hook(Phase3Spy.replicationHook(taskId))
                .addOnCloseCallback(() -> Phase3Spy.queueClosed(taskId));
    }
}
