package org.a2aproject.sdk.server.events;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.a2aproject.sdk.server.tasks.AtomicTaskStore;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.ExecutorSubmissionService;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskCommitSlot;
import org.a2aproject.sdk.server.tasks.TaskManager;
import org.a2aproject.sdk.server.tasks.TaskMutationNotCommittedException;
import org.a2aproject.sdk.server.tasks.TaskMutationOutcomeUnknownException;
import org.a2aproject.sdk.server.tasks.TaskPersistenceException;
import org.a2aproject.sdk.server.tasks.TaskSerializationException;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
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
 * <p>
 * <b>Note:</b> This bean is eagerly initialized by {@link MainEventBusProcessorInitializer}
 * to ensure the background thread starts automatically when the application starts.
 * </p>
 *
 * <h2>Exception Handling</h2>
 * TaskStore persistence failures are caught and handled gracefully:
 * <ul>
 *   <li>{@link TaskSerializationException} - Data corruption or schema mismatch.
 *       Logged at ERROR level, distributed as {@link InternalError} to clients.</li>
 *   <li>{@link TaskPersistenceException} - Database/storage system failure.
 *       Logged at ERROR level, distributed as {@link InternalError} to clients.</li>
 * </ul>
 *
 * <p>Processing continues after errors - the failed event is distributed as InternalError
 * to all ChildQueues, and the MainEventBusProcessor continues consuming subsequent events.</p>
 */
@ApplicationScoped
public class MainEventBusProcessor implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainEventBusProcessor.class);

    private record UpdateResult(boolean isFinal, @Nullable Task taskSnapshot,
            @Nullable TaskSnapshot committedSnapshot,
            boolean compatibilityBootstrap,
            @Nullable CommittedTaskBatch committedBatch, @Nullable TaskCommitSlot slot) {}

    private record PushAdmission(Runnable invokeSender, Runnable dropSender) {}

    /**
     * Callback for testing synchronization with async event processing.
     * Default is NOOP to avoid null checks in production code.
     * Tests can inject their own callback via setCallback().
     */
    private volatile MainEventBusProcessorCallback callback = MainEventBusProcessorCallback.NOOP;

    /**
     * Optional executor for push notifications.
     * If null, uses default ForkJoinPool (async).
     * Tests can inject a synchronous executor to ensure deterministic ordering.
     */
    private volatile @Nullable java.util.concurrent.Executor pushNotificationExecutor = null;

    /**
     * Per-task tail of the push-notification chain. {@link #sendPushNotification} is only ever
     * called from this class's own single-threaded processing loop, so pushes for a given task
     * are chained onto this exact sequence rather than submitted independently -- each one only
     * starts once the previous one for the same task has finished, which is what guarantees they
     * reach the client in the order they were produced even though delivery itself happens off
     * this thread. A completed chain removes its own entry (compare-and-remove against the future
     * it added) so the map does not grow forever.
     */
    private final ConcurrentHashMap<String, CompletableFuture<Void>> pushNotificationChains = new ConcurrentHashMap<>();

    private final OrderedTaskPublisher orderedTaskPublisher = new OrderedTaskPublisher();

    private MainEventBus eventBus;

    private TaskStore taskStore;

    private PushNotificationSender pushSender;

    private QueueManager queueManager;

    private volatile boolean running = true;
    private @Nullable Thread processorThread;

    /**
     * No-arg constructor for CDI proxying.
     * CDI requires this for @ApplicationScoped beans.
     * Fields are initialized via the @Inject constructor.
     */
    @SuppressWarnings("NullAway")
    protected MainEventBusProcessor() {
    }

    @Inject
    public MainEventBusProcessor(MainEventBus eventBus, TaskStore taskStore, PushNotificationSender pushSender, QueueManager queueManager) {
        this.eventBus = eventBus;
        this.taskStore = taskStore;
        this.pushSender = pushSender;
        this.queueManager = queueManager;
    }

    /**
     * Set a callback for testing synchronization with async event processing.
     * <p>
     * This is primarily intended for tests that need to wait for event processing to complete.
     * Pass null to reset to the default NOOP callback.
     * </p>
     *
     * @param callback the callback to invoke during event processing, or null for NOOP
     */
    public void setCallback(MainEventBusProcessorCallback callback) {
        this.callback = callback != null ? callback : MainEventBusProcessorCallback.NOOP;
    }

    /**
     * Set a custom executor for push notifications (primarily for testing).
     * <p>
     * By default, push notifications are sent asynchronously using CompletableFuture.runAsync()
     * with the default ForkJoinPool. For tests that need deterministic ordering of push
     * notifications, inject a synchronous executor that runs tasks immediately on the calling thread.
     * </p>
     * Example synchronous executor for tests:
     * <pre>{@code
     * Executor syncExecutor = Runnable::run;
     * mainEventBusProcessor.setPushNotificationExecutor(syncExecutor);
     * }</pre>
     *
     * @param executor the executor to use for push notifications, or null to use default ForkJoinPool
     */
    public void setPushNotificationExecutor(java.util.concurrent.Executor executor) {
        this.pushNotificationExecutor = executor;
    }

    /**
     * Test-only visibility into the per-task chain map, to assert entries don't leak.
     */
    int pushNotificationChainCount() {
        return pushNotificationChains.size();
    }

    /**
     * Starts the background processor thread if it is not already running.
     */
    @SuppressWarnings("NullAway.Init")
    @PostConstruct
    public synchronized void start() {
        if (processorThread != null && processorThread.isAlive()) {
            LOGGER.debug("MainEventBusProcessor already started");
            return;
        }
        running = true;
        processorThread = new Thread(this, "MainEventBusProcessor");
        processorThread.setDaemon(true); // Allow JVM to exit even if this thread is running
        processorThread.start();
        LOGGER.info("MainEventBusProcessor started");
    }

    @PreDestroy
    synchronized void stop() {
        LOGGER.info("MainEventBusProcessor stopping...");
        running = false;
        if (processorThread != null) {
            processorThread.interrupt();
            try {
                long start = System.currentTimeMillis();
                processorThread.join(5000); // Wait up to 5 seconds
                long elapsed = System.currentTimeMillis() - start;
                LOGGER.info("MainEventBusProcessor thread stopped in {}ms", elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for MainEventBusProcessor thread to stop");
            }
        }
        LOGGER.info("MainEventBusProcessor stopped");
    }

    @Override
    public void run() {
        LOGGER.info("MainEventBusProcessor processing loop started");
        while (running) {
            try {
                LOGGER.debug("MainEventBusProcessor: Waiting for event from MainEventBus...");
                MainEventBusContext context = eventBus.take();
                LOGGER.debug("MainEventBusProcessor: Retrieved event for task {} from MainEventBus",
                            context.taskId());
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
        // MainEventBus.submit() guarantees this is always a MainQueue
        EventQueue.MainQueue mainQueue = (EventQueue.MainQueue) context.eventQueue();

        if (context.eventQueueItem() instanceof ExecutorSubmissionService.ProposalAdmission proposalItem) {
            EventQueue.ProposalProcessingCapability capability =
                    mainQueue.claimProposalProcessing(proposalItem);
            try {
                if (capability == null) {
                    LOGGER.warn("Ignoring proposal without an owning MainQueue processing capability for task {}",
                            taskId);
                } else {
                    EventQueue.ChildQueue origin = context.origin();
                    boolean directResponseAlreadySelected = origin != null
                            && origin.hasSelectedDirectMessageResponse() && proposalItem.isInitialProposal();
                    processProposal(mainQueue, origin, proposalItem, capability, directResponseAlreadySelected);
                }
            } finally {
                try {
                    mainQueue.resolveProposalDelivery(proposalItem, context.origin());
                } finally {
                    mainQueue.releaseSemaphore();
                }
            }
            return;
        }

        LOGGER.debug("MainEventBusProcessor: Processing event for task {}: {}",
                    taskId, event.getClass().getSimpleName());

        Event eventToDistribute = null;
        UpdateResult updateResult = null;
        boolean eventRoutedLocally = false;
        boolean isReplicated = context.eventQueueItem().isReplicated();
        try {
            // Step 1: Update TaskStore FIRST (persistence before clients see it)
            // If this throws, we distribute an error to ensure "persist before client visibility"

            try {
                updateResult = updateTaskStore(taskId, event, isReplicated);
                boolean isFinal = updateResult.isFinal();

                if (updateResult.committedBatch() != null && updateResult.slot() != null) {
                    CommittedTaskBatch batch = updateResult.committedBatch();
                    orderedTaskPublisher.publish(updateResult.slot(), batch,
                            committedBatch -> deliverCommittedBatch(mainQueue, committedBatch, isReplicated,
                                    event instanceof A2AError error && !isReplicated ? error : null,
                                    !isReplicated ? context.origin() : null, false))
                            .toCompletableFuture().join();
                } else {
                    if (!isReplicated && taskStore instanceof AtomicTaskStore && event instanceof A2AError) {
                        // An executor error with no changed task result still belongs to the
                        // producer. Do not turn the protocol error into a shared child event.
                        mainQueue.rejectProposal(context.eventQueueItem(), context.origin(), event);
                        eventToDistribute = event;
                        eventRoutedLocally = true;
                    } else {
                        eventToDistribute = event;
                    }

                    // Trigger replication AFTER successful persistence. Finalization and its
                    // committed batch envelope remain the responsibility of T10.
                    if (!isFinal) {
                        EventEnqueueHook hook = mainQueue.getEnqueueHook();
                        if (hook != null) {
                            LOGGER.debug("Triggering replication hook for task {} after successful persistence", taskId);
                            hook.onEnqueue(context.eventQueueItem());
                        }
                    }
                }
            } catch (InternalError e) {
                // Persistence failed - create error event to distribute instead
                LOGGER.error("Failed to persist event for task {}, distributing error to clients", taskId, e);
                String errorMessage = "Failed to persist event: " + e.getMessage();
                eventToDistribute = e;
            } catch (A2AError e) {
                // Planner rejection is typed and non-mutating; it is not a storage failure or a
                // reason to create a fresh task-failure mutation or fail unrelated consumers.
                if (!isReplicated && taskStore instanceof AtomicTaskStore) {
                    mainQueue.rejectProposal(context.eventQueueItem(), context.origin(), e);
                } else {
                    eventToDistribute = e;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to persist event for task {}, distributing error to clients", taskId, e);
                String errorMessage = "Failed to persist event: " + e.getMessage();
                eventToDistribute = new InternalError(errorMessage);
            }

            // Step 2: Send push notification AFTER successful persistence (only from active node)
            // Skip push notifications for replicated events to avoid duplicate notifications in multi-instance deployments
            // Push notifications are sent for all StreamingEventKind events (Task, Message, TaskStatusUpdateEvent, TaskArtifactUpdateEvent)
            // per A2A spec section 4.3.3
            if (!isReplicated && updateResult != null && updateResult.committedBatch() == null
                    && eventToDistribute == event && event instanceof StreamingEventKind streamingEvent) {
                // Send the streaming event directly - it will be wrapped in StreamResponse format by PushNotificationSender
                sendPushNotification(taskId, streamingEvent, updateResult != null ? updateResult.taskSnapshot() : null);
            }

            // Step 3: Then distribute to ChildQueues (clients see either event or error AFTER persistence attempt)
            if (eventToDistribute != null) {
                int childCount = mainQueue.getChildCount();
                LOGGER.debug("MainEventBusProcessor: Distributing {} to {} children for task {}",
                            eventToDistribute.getClass().getSimpleName(), childCount, taskId);
                // Direct Message and control/error behavior remains on the established single-event path.
                TaskSnapshot committedSnapshot = updateResult == null || updateResult.compatibilityBootstrap()
                        ? null : updateResult.committedSnapshot();
                Task legacyTaskSnapshot = updateResult == null ? null : updateResult.taskSnapshot();
                boolean localFinal = updateResult != null && updateResult.isFinal()
                        && (updateResult.committedSnapshot() != null || updateResult.compatibilityBootstrap());
                if (eventToDistribute == event && event instanceof Message
                        && context.origin() != null && !isReplicated) {
                    context.origin().markDirectMessageResponseSelected();
                }
                EventQueueItem itemToDistribute = new LocalEventQueueItem(
                        eventToDistribute, committedSnapshot, false, legacyTaskSnapshot, localFinal);
                if (!eventRoutedLocally) {
                    mainQueue.distributeToChildren(itemToDistribute);
                }
                LOGGER.debug("MainEventBusProcessor: Distributed {} to {} children for task {}",
                            eventToDistribute.getClass().getSimpleName(), childCount, taskId);

                TaskStreamLifecycleHook streamHook = mainQueue.getTaskStreamLifecycleHook();
                if (streamHook != null && childCount > 0) {
                    try {
                        streamHook.onEvent(taskId, eventToDistribute, mainQueue.getStreamCloseHandle());
                    } catch (Exception e) {
                        LOGGER.error("Error in TaskStreamLifecycleHook.onEvent for task {}", taskId, e);
                    }
                }
            }

            LOGGER.debug("MainEventBusProcessor: Completed processing event for task {}", taskId);

        } finally {
            try {
                // Step 4: Notify callback after all processing is complete
                // Call callback with the distributed event (original or error)
                if (eventToDistribute != null) {
                    try {
                        callback.onEventProcessed(taskId, eventToDistribute);
                    } catch (RuntimeException | Error callbackFailure) {
                        LOGGER.error("Post-processing callback failed for task {} after distribution", taskId,
                                callbackFailure);
                    }

                    // Step 5: If this is a final event, notify task finalization
                    // Only for successful persistence (not for errors)
                    if (eventToDistribute == event && updateResult != null
                            && (EventQueue.isFinalEvent(event)
                                    || (updateResult.committedSnapshot() != null
                                            || updateResult.compatibilityBootstrap()) && updateResult.isFinal())) {
                        try {
                            callback.onTaskFinalized(taskId);
                        } catch (RuntimeException | Error callbackFailure) {
                            LOGGER.error("Finalization callback failed for task {} after distribution", taskId,
                                    callbackFailure);
                        }
                    }
                }
            } finally {
                 // ALWAYS release semaphore, even if processing fails
                // Balances the acquire() in MainQueue.enqueueEvent()
                mainQueue.releaseSemaphore();
            }
        }
    }

    private void processProposal(EventQueue.MainQueue mainQueue, @Nullable EventQueue.ChildQueue origin,
            ExecutorSubmissionService.ProposalAdmission proposalItem,
            EventQueue.ProposalProcessingCapability capability, boolean directResponseAlreadySelected) {
        AtomicReference<TaskCommitSlot> reservedSlot = new AtomicReference<>();
        AtomicBoolean published = new AtomicBoolean();
        try {
            ExecutorSubmissionService.ProposalExecution execution = proposalItem.process(capability,
                    directResponseAlreadySelected,
                    (slot, mutation) -> {
                orderedTaskPublisher.reserve(slot);
                reservedSlot.set(slot);
            });
            if (execution == null || execution.mutation() == null) {
                return;
            }
            CommittedMutation mutation = execution.mutation();
            if (mutation.changed() && !mutation.events().isEmpty()) {
                TaskCommitSlot slot = reservedSlot.get();
                if (slot == null) {
                    throw new IllegalStateException("Committed proposal has no reserved publication slot");
                }
                CommittedTaskBatch batch = new CommittedTaskBatch(mutation.snapshot(), mutation.events());
                orderedTaskPublisher.publish(slot, batch,
                        committedBatch -> deliverCommittedBatch(mainQueue, committedBatch, false, null, null, true))
                        .toCompletableFuture().join();
                published.set(true);
                return;
            }

            Task task = mutation.snapshot().task();
            if (task.status().state().isFinal()
                    || task.status().state() == TaskState.TASK_STATE_UNSPECIFIED) {
                return;
            }
            if (origin != null && proposalItem.hasLegacyTaskSummaryRequest()) {
                origin.enqueueLocalOnly(new LocalEventQueueItem(task, mutation.snapshot(), false, task, false, true));
            }
            sendLegacyTaskSnapshotInOrder(task);
        } catch (RuntimeException | Error failure) {
            LOGGER.error("Executor Task proposal processing or ordered delivery failed", failure);
        } finally {
            TaskCommitSlot slot = reservedSlot.get();
            if (slot != null && !published.get()) {
                try {
                    orderedTaskPublisher.discard(slot);
                } catch (RuntimeException discardFailure) {
                    LOGGER.warn("Could not discard unpublished proposal slot for task {}", slot.taskId(),
                            discardFailure);
                }
            }
        }
    }

    /**
     * Updates TaskStore using TaskManager.process().
     * <p>
     * Creates a temporary TaskManager instance for this event and delegates to its process() method,
     * which handles all event types (Task, TaskStatusUpdateEvent, TaskArtifactUpdateEvent).
     * This leverages existing TaskManager logic for status updates, artifact appending, message history, etc.
     * </p>
     * <p>
     * If persistence fails, the exception is propagated to processEvent() which distributes an
     * InternalError to clients instead of the original event, ensuring "persist before visibility".
     * See Gemini's comment: https://github.com/a2aproject/a2a-java/pull/515#discussion_r2604621833
     * </p>
     *
     * @param taskId the task ID
     * @param event the event to persist
     * @return true if the task reached a final state, false otherwise
     * @throws InternalError if persistence fails
     */
    private UpdateResult updateTaskStore(String taskId, Event event, boolean isReplicated) throws InternalError {
        try {
            // Extract contextId from event (all relevant events have it)
            String contextId = extractContextId(event);

            // Create temporary TaskManager instance for this event
            TaskManager taskManager = new TaskManager(taskId, contextId, taskStore, null);

            if (!isReplicated && taskStore instanceof AtomicTaskStore) {
                AtomicReference<TaskCommitSlot> reservedSlot = new AtomicReference<>();
                try {
                    TaskManager.CommittedProcessingResult result = taskManager.processCommitted(event,
                            (slot, mutation) -> {
                                orderedTaskPublisher.reserve(slot);
                                reservedSlot.set(slot);
                            });
                    CommittedMutation mutation = result.mutation();
                    CommittedTaskBatch batch = mutation != null && mutation.changed()
                            && !mutation.events().isEmpty()
                                    ? new CommittedTaskBatch(mutation.snapshot(), mutation.events())
                                    : null;
                    TaskCommitSlot slot = reservedSlot.get();
                    if (batch == null && slot != null) {
                        orderedTaskPublisher.discard(slot);
                        slot = null;
                    }
                    LOGGER.debug("TaskStore committed canonical result for task {}: {} (final: {})",
                            taskId, event.getClass().getSimpleName(), result.isFinal());
                    return new UpdateResult(result.isFinal(),
                            mutation == null ? null : mutation.snapshot().task(),
                            mutation == null ? null : mutation.snapshot(),
                            mutation != null && mutation.changed() && mutation.events().isEmpty(), batch, slot);
                } catch (RuntimeException | Error failure) {
                    TaskCommitSlot slot = reservedSlot.get();
                    if (slot != null) {
                        orderedTaskPublisher.discard(slot);
                    }
                    throw failure;
                }
            }

            // Legacy-only stores and already committed replicated copies retain their existing
            // TaskStore contract; they are not adapted to an atomic get/save pair.
            AtomicReference<Task> taskSnapshot = new AtomicReference<>();
            boolean isFinal = taskManager.process(event, isReplicated, taskSnapshot);
            LOGGER.debug("TaskStore updated via TaskManager.process() for task {}: {} (final: {}, replicated: {})",
                        taskId, event.getClass().getSimpleName(), isFinal, isReplicated);
            return new UpdateResult(isFinal, taskSnapshot.get(), null, false, null, null);

        } catch (TaskSerializationException e) {
            // Data corruption or schema mismatch - ALWAYS permanent
            LOGGER.error("Task {} event serialization failed - data corruption detected: {}",
                        taskId, e.getMessage(), e);
            throw new InternalError("Failed to serialize task " + taskId + ": " + e.getMessage());

        } catch (TaskPersistenceException e) {
            // Database/storage failure
            LOGGER.error("Task {} event persistence failed: {}", taskId, e.getMessage(), e);
            throw new InternalError("Storage failure for task " + taskId + ": " + e.getMessage());

        } catch (InternalError e) {
            // Already an InternalError from TaskManager validation - pass through
            LOGGER.error("Error updating TaskStore via TaskManager for task {}", taskId, e);
            // Rethrow to prevent distributing unpersisted event to clients
            throw e;

        } catch (TaskMutationOutcomeUnknownException e) {
            LOGGER.error("Task {} mutation outcome is unknown; local publication is suppressed", taskId, e);
            throw new InternalError("Storage outcome is unknown for task " + taskId);

        } catch (TaskMutationNotCommittedException e) {
            LOGGER.error("Task {} mutation was not committed", taskId, e);
            throw new InternalError("Task mutation was not committed for task " + taskId);

        } catch (A2AError e) {
            // Typed planner errors represent a rejected proposal, not a storage failure.
            throw e;

        } catch (Exception e) {
            // Unexpected exception type - treat as permanent failure
            LOGGER.error("Unexpected error updating TaskStore for task {}", taskId, e);
            // Rethrow to prevent distributing unpersisted event to clients
            throw new InternalError("TaskStore persistence failed: " + e.getMessage());
        }
    }

    void deliverCommittedBatch(EventQueue.MainQueue mainQueue, CommittedTaskBatch batch,
            boolean isReplicated) {
        deliverCommittedBatch(mainQueue, batch, isReplicated, null, null, false);
    }

    private void deliverCommittedBatch(EventQueue.MainQueue mainQueue, CommittedTaskBatch batch,
            boolean isReplicated, @Nullable A2AError originError,
            @Nullable EventQueue.ChildQueue origin, boolean legacyTaskSummary) {
        String taskId = batch.taskId();
        int childCount = mainQueue.getChildCount();
        PushAdmission pushAdmission = isReplicated
                ? new PushAdmission(() -> { }, () -> { })
                : reserveCommittedPushBatch(batch, legacyTaskSummary);
        AtomicBoolean admissionSettled = new AtomicBoolean();
        Runnable admittedSender = () -> {
            if (admissionSettled.compareAndSet(false, true)) {
                pushAdmission.invokeSender().run();
            }
        };
        java.util.function.Consumer<Throwable> failedAdmission = failure -> {
            reportPostCommitFailure(taskId, batch, failure);
            if (admissionSettled.compareAndSet(false, true)) {
                pushAdmission.dropSender().run();
            }
        };
        try {
            callback.admitPushNotifications(batch, admittedSender, failedAdmission);
        } catch (RuntimeException | Error failure) {
            failedAdmission.accept(failure);
        }

        // The input error is request-scoped. Route it after the batch admission seam and before
        // publishing the canonical FAILED event to every observer.
        if (originError != null && origin != null) {
            try {
                origin.enqueueLocalOnly(new LocalEventQueueItem(originError, batch.snapshot(), batch.isFinal()));
            } catch (RuntimeException | Error failure) {
                reportPostCommitFailure(taskId, batch, failure);
            }
        }

        for (int index = 0; index < batch.events().size(); index++) {
            StreamingEventKind event = batch.events().get(index);
            boolean legacyFinal = batch.events().stream().anyMatch(batchEvent -> EventQueue.isFinalEvent(batchEvent));
            if (!isReplicated && !legacyFinal) {
                EventEnqueueHook hook = mainQueue.getEnqueueHook();
                if (hook != null) {
                    try {
                        hook.onEnqueue(new LocalEventQueueItem(event, batch.snapshot(), false));
                    } catch (RuntimeException | Error failure) {
                        reportPostCommitFailure(taskId, batch, failure);
                    }
                }
            }
            boolean lastInFinalBatch = batch.isFinal() && index == batch.events().size() - 1;
            try {
                mainQueue.distributeToChildren(new LocalEventQueueItem(event, batch.snapshot(), lastInFinalBatch,
                        batch.snapshot().task(), lastInFinalBatch, legacyTaskSummary,
                        legacyTaskSummary ? index : -1));
            } catch (RuntimeException | Error failure) {
                reportPostCommitFailure(taskId, batch, failure);
                if (lastInFinalBatch) {
                    mainQueue.cancelSubmittedFinalEvents();
                }
            }
        }

        // Delay lifecycle callbacks until every event in the batch has reached child queues.
        TaskStreamLifecycleHook streamHook = mainQueue.getTaskStreamLifecycleHook();
        if (streamHook != null && childCount > 0) {
            for (StreamingEventKind event : batch.events()) {
                try {
                    streamHook.onEvent(taskId, event, mainQueue.getStreamCloseHandle());
                } catch (RuntimeException | Error e) {
                    LOGGER.error("Error in TaskStreamLifecycleHook.onEvent for task {}", taskId, e);
                    reportPostCommitFailure(taskId, batch, e);
                }
            }
        }
        for (StreamingEventKind event : batch.events()) {
            try {
                callback.onEventProcessed(taskId, event);
            } catch (RuntimeException | Error failure) {
                reportPostCommitFailure(taskId, batch, failure);
            }
        }
        if (batch.isFinal()) {
            try {
                callback.onTaskFinalized(taskId);
            } catch (RuntimeException | Error failure) {
                reportPostCommitFailure(taskId, batch, failure);
            }
        }
    }

    /** Reserves a per-task push position before external webhook admission can defer the sender. */
    private PushAdmission reserveCommittedPushBatch(CommittedTaskBatch batch, boolean legacyTaskSummary) {
        String taskId = batch.taskId();
        CompletableFuture<Boolean> admission = new CompletableFuture<>();
        Executor executor = pushNotificationExecutor != null ? pushNotificationExecutor : ForkJoinPool.commonPool();
        CompletableFuture<Void> tail = pushNotificationChains.compute(taskId, (id, previous) -> {
            CompletableFuture<Void> predecessor = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((ignored, failure) -> null);
            return predecessor.thenCompose(ignored -> admission.thenAcceptAsync(admitted -> {
                if (admitted) {
                    invokeCommittedPushBatch(batch, legacyTaskSummary);
                }
            }, executor));
        });
        tail.whenComplete((ignored, failure) -> {
            pushNotificationChains.remove(taskId, tail);
            if (failure != null) {
                reportPostCommitFailure(taskId, batch, failure);
            }
        });
        AtomicBoolean settled = new AtomicBoolean();
        Runnable invoke = () -> {
            if (settled.compareAndSet(false, true)) {
                admission.complete(true);
            }
        };
        Runnable drop = () -> {
            if (settled.compareAndSet(false, true)) {
                admission.complete(false);
            }
        };
        return new PushAdmission(invoke, drop);
    }

    private void invokeCommittedPushBatch(CommittedTaskBatch batch, boolean legacyTaskSummary) {
        String taskId = batch.taskId();
        if (legacyTaskSummary) {
            try {
                if (!pushSender.sendTaskProposalBatchNotification(batch)) {
                    reportPostCommitFailure(taskId, batch,
                            new IllegalStateException("Push notification delivery failed"));
                }
            } catch (Exception failure) {
                reportPostCommitFailure(taskId, batch, failure);
            }
            return;
        }
        for (StreamingEventKind event : batch.events()) {
            try {
                boolean sent = pushSender.sendNotification(event, batch.snapshot().task());
                if (!sent) {
                    reportPostCommitFailure(taskId, batch,
                            new IllegalStateException("Push notification delivery failed"));
                }
            } catch (Exception failure) {
                reportPostCommitFailure(taskId, batch, failure);
            }
        }
    }

    /** Appends a verified no-op summary after prior committed webhook admissions for this task. */
    private void sendLegacyTaskSnapshotInOrder(Task task) {
        String taskId = task.id();
        Executor executor = pushNotificationExecutor != null ? pushNotificationExecutor : ForkJoinPool.commonPool();
        CompletableFuture<Void> tail = pushNotificationChains.compute(taskId, (id, previous) -> {
            CompletableFuture<Void> predecessor = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((ignored, failure) -> null);
            return predecessor.thenRunAsync(() -> {
                if (!pushSender.sendLegacyTaskSnapshot(task)) {
                    LOGGER.warn("Legacy no-op snapshot webhook delivery failed for task {}", taskId);
                }
            }, executor);
        });
        tail.whenComplete((ignored, failure) -> {
            pushNotificationChains.remove(taskId, tail);
            if (failure != null) {
                LOGGER.warn("Legacy no-op snapshot webhook delivery failed for task {}", taskId, failure);
            }
        });
    }

    private void reportPostCommitFailure(String taskId, CommittedTaskBatch batch, Throwable failure) {
        LOGGER.error("Post-commit delivery failed for task {} at generations {}", taskId,
                batch.generationPositions(), failure);
        MainEventBusProcessorCallback failureReporter = callback;
        try {
            ForkJoinPool.commonPool().execute(() -> {
                try {
                    failureReporter.onPostCommitFailure(taskId, batch, failure);
                } catch (RuntimeException | Error reportFailure) {
                    LOGGER.error("Could not report post-commit failure for task {}", taskId, reportFailure);
                }
            });
        } catch (RuntimeException | Error reportFailure) {
            LOGGER.error("Could not schedule post-commit failure report for task {}", taskId, reportFailure);
        }
    }

    /**
     * Sends push notification for the streaming event AFTER persistence.
     * <p>
     * This is called after updateTaskStore() to ensure the notification contains
     * the latest persisted state, avoiding race conditions.
     * </p>
     * <p>
     * <b>CRITICAL:</b> Push notifications are sent asynchronously in the background
     * to avoid blocking event distribution to ChildQueues. The 83ms overhead from
     * PushNotificationSender.sendNotification() was causing streaming delays.
     * </p>
     * <p>
     * <b>IMPORTANT:</b> The event parameter is the actual event being processed.
     * This ensures we send the event as it was when processed, not whatever state
     * might exist in TaskStore when the async callback executes (subsequent events
     * may have already updated the store).
     * </p>
     * <p>
     * Supports all StreamingEventKind event types per A2A spec section 4.3.3:
     * Task, Message, TaskStatusUpdateEvent, TaskArtifactUpdateEvent.
     * The event will be automatically wrapped in StreamResponse format by JsonUtil.
     * </p>
     * <p>
     * <b>Ordering:</b> chained onto {@link #pushNotificationChains this task's own tail future}
     * rather than submitted independently, so pushes for the same task are always delivered in
     * the order they were produced, regardless of executor scheduling. Different tasks remain
     * fully independent -- one task's slow delivery never delays another's.
     * </p>
     * <p>
     * Tests can inject a synchronous executor via setPushNotificationExecutor() to make delivery
     * itself synchronous too, but ordering does not depend on that.
     * </p>
     *
     * @param taskId the task ID
     * @param event the streaming event to send (Task, Message, TaskStatusUpdateEvent, or TaskArtifactUpdateEvent)
     */
    private void sendPushNotification(String taskId, StreamingEventKind event, @Nullable Task taskSnapshot) {
        sendPushNotification(taskId, event, taskSnapshot, null);
    }

    private void sendPushNotification(String taskId, StreamingEventKind event, @Nullable Task taskSnapshot,
            @Nullable CommittedTaskBatch committedBatch) {
        Runnable pushTask = () -> {
            try {
                if (event != null) {
                    LOGGER.debug("Sending push notification for task {}", taskId);
                    boolean sent = pushSender.sendNotification(event, taskSnapshot);
                    if (!sent) {
                        LOGGER.warn("One or more push notification deliveries failed for task {}", taskId);
                        if (committedBatch != null) {
                            reportPostCommitFailure(taskId, committedBatch,
                                    new IllegalStateException("Push notification delivery failed"));
                        }
                    }
                } else {
                    LOGGER.debug("Skipping push notification - event is null for task {}", taskId);
                }
            } catch (Exception e) {
                LOGGER.error("Error sending push notification for task {}", taskId, e);
                // Don't rethrow - push notifications are best-effort
                if (committedBatch != null) {
                    reportPostCommitFailure(taskId, committedBatch, e);
                }
            }
        };

        Executor executor = pushNotificationExecutor != null ? pushNotificationExecutor : ForkJoinPool.commonPool();

        // Registered after compute() returns: with a synchronous executor, next
        // completes before compute()'s lambda returns, so attaching whenComplete
        // in there fires the removal before next is even stored -- a no-op that
        // leaks one entry per task.
        CompletableFuture<Void> next = pushNotificationChains.compute(taskId, (id, previous) -> {
            CompletableFuture<Void> previousOrDone = previous != null ? previous : CompletableFuture.completedFuture(null);
            return previousOrDone.thenRunAsync(pushTask, executor);
        });
        next.whenComplete((v, t) -> pushNotificationChains.remove(taskId, next));
    }

    /**
     * Extracts contextId from an event.
     * Returns null if the event type doesn't have a contextId (e.g., Message).
     */
    @Nullable
    private String extractContextId(Event event) {
        if (event instanceof Task task) {
            return task.contextId();
        } else if (event instanceof TaskStatusUpdateEvent statusUpdate) {
            return statusUpdate.contextId();
        } else if (event instanceof TaskArtifactUpdateEvent artifactUpdate) {
            return artifactUpdate.contextId();
        }
        // Message and other events don't have contextId
        return null;
    }

}
