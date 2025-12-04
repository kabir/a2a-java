package io.a2a.server.events;

import io.a2a.server.tasks.TaskManager;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.A2AServerException;
import io.a2a.spec.Event;
import io.a2a.spec.Task;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskStatusUpdateEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background processor for the MainEventBus.
 * <p>
 * This processor runs in a dedicated background thread, consuming events from the MainEventBus
 * and performing two critical operations in order:
 * </p>
 * <ol>
 *   <li>Update TaskStore with event data (persistence FIRST)</li>
 *   <li>Distribute event to ChildQueues (clients see it AFTER persistence)</li>
 * </ol>
 * <p>
 * This architecture ensures clients never receive events before they're persisted,
 * eliminating race conditions and enabling reliable event replay.
 * </p>
 */
@ApplicationScoped
public class MainEventBusProcessor implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainEventBusProcessor.class);


    private final MainEventBus eventBus;

    private final TaskStore taskStore;

    private volatile boolean running = true;
    private Thread processorThread;

    @Inject
    public MainEventBusProcessor(MainEventBus eventBus, TaskStore taskStore) {
        this.eventBus = eventBus;
        this.taskStore = taskStore;
    }

    @PostConstruct
    void start() {
        processorThread = new Thread(this, "MainEventBusProcessor");
        processorThread.setDaemon(false); // Keep JVM alive
        processorThread.start();
        LOGGER.info("MainEventBusProcessor started");
    }

    @PreDestroy
    void stop() {
        LOGGER.info("MainEventBusProcessor stopping...");
        running = false;
        if (processorThread != null) {
            processorThread.interrupt();
            try {
                processorThread.join(5000); // Wait up to 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("MainEventBusProcessor stopped");
    }

    @Override
    public void run() {
        LOGGER.info("MainEventBusProcessor processing loop started");
        while (running) {
            try {
                MainEventBusContext context = eventBus.take();
                processEvent(context);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.info("MainEventBusProcessor interrupted, shutting down");
                break;
            } catch (Exception e) {
                LOGGER.error("Error processing event from MainEventBus", e);
                // Continue processing despite errors
            }
        }
        LOGGER.info("MainEventBusProcessor processing loop ended");
    }

    private void processEvent(MainEventBusContext context) {
        String taskId = context.taskId();
        Event event = context.eventQueueItem().getEvent();
        EventQueue eventQueue = context.eventQueue();

        LOGGER.debug("Processing event for task {}: {}", taskId, event.getClass().getSimpleName());

        // Step 1: Update TaskStore FIRST (persistence before clients see it)
        updateTaskStore(taskId, event);

        // Step 2: Then distribute to ChildQueues (clients see it AFTER persistence)
        if (eventQueue instanceof EventQueue.MainQueue mainQueue) {
            mainQueue.distributeToChildren(context.eventQueueItem());
            LOGGER.debug("Distributed event to children for task {}", taskId);
        } else {
            LOGGER.warn("Expected MainQueue but got {} for task {}",
                    eventQueue.getClass().getSimpleName(), taskId);
        }

        LOGGER.debug("Completed processing event for task {}", taskId);
    }

    /**
     * Updates TaskStore using TaskManager.process().
     * <p>
     * Creates a temporary TaskManager instance for this event and delegates to its process() method,
     * which handles all event types (Task, TaskStatusUpdateEvent, TaskArtifactUpdateEvent).
     * This leverages existing TaskManager logic for status updates, artifact appending, message history, etc.
     * </p>
     */
    private void updateTaskStore(String taskId, Event event) {
        try {
            // Extract contextId from event (all relevant events have it)
            String contextId = extractContextId(event);

            // Create temporary TaskManager instance for this event
            TaskManager taskManager = new TaskManager(taskId, contextId, taskStore, null);

            // Use TaskManager.process() - handles all event types with existing logic
            taskManager.process(event);
            LOGGER.debug("TaskStore updated via TaskManager.process() for task {}: {}",
                        taskId, event.getClass().getSimpleName());
        } catch (A2AServerException e) {
            LOGGER.error("Error updating TaskStore via TaskManager for task {}", taskId, e);
            // Don't rethrow - we still want to distribute to ChildQueues
        } catch (Exception e) {
            LOGGER.error("Unexpected error updating TaskStore for task {}", taskId, e);
            // Don't rethrow - we still want to distribute to ChildQueues
        }
    }

    /**
     * Extracts contextId from an event.
     * Returns null if the event type doesn't have a contextId (e.g., Message).
     */
    private String extractContextId(Event event) {
        if (event instanceof Task task) {
            return task.getContextId();
        } else if (event instanceof TaskStatusUpdateEvent statusUpdate) {
            return statusUpdate.getContextId();
        } else if (event instanceof TaskArtifactUpdateEvent artifactUpdate) {
            return artifactUpdate.getContextId();
        }
        // Message and other events don't have contextId
        return null;
    }
}
