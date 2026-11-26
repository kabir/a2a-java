package org.a2aproject.sdk.server.events;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.a2aproject.sdk.server.tasks.AtomicTaskStore;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.ExecutorSubmissionService;
import org.a2aproject.sdk.server.tasks.OrderedWebhookDispatcher;
import org.a2aproject.sdk.server.tasks.OpeningTaskInput;
import org.a2aproject.sdk.server.tasks.TaskEnrollmentCoordinator;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskAuthorityCapabilities;
import org.a2aproject.sdk.server.tasks.TaskCommitSlot;
import org.a2aproject.sdk.server.tasks.TaskManager;
import org.a2aproject.sdk.server.tasks.TaskMutation;
import org.a2aproject.sdk.server.tasks.TaskMutationAuthority;
import org.a2aproject.sdk.server.tasks.TaskMutationNotCommittedException;
import org.a2aproject.sdk.server.tasks.TaskMutationOutcomeUnknownException;
import org.a2aproject.sdk.server.tasks.TaskPersistenceException;
import org.a2aproject.sdk.server.tasks.TaskSerializationException;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
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

    private static final long ORDERED_WEBHOOK_REPORT_INTERVAL_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
    private static final Logger LOGGER = LoggerFactory.getLogger(MainEventBusProcessor.class);

    private record UpdateResult(boolean isFinal, @Nullable Task taskSnapshot,
            @Nullable TaskSnapshot committedSnapshot,
            boolean compatibilityBootstrap,
            @Nullable CommittedTaskBatch committedBatch, @Nullable TaskCommitSlot slot,
            @Nullable Long writerEpoch) {}

    private record PushAdmission(Runnable invokeSender, Runnable dropSender, CompletionStage<Boolean> result) {}

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
    private static final int MAX_ACTIVE_PUSH_ADMISSIONS = 2048;
    private static final int MAX_COMPLETED_PUSH_ADMISSIONS = 2048;
    private final Object pushAdmissionLock = new Object();
    private final LinkedHashMap<CommittedTaskBatch.Identity, CompletableFuture<Boolean>> activePushAdmissions =
            new LinkedHashMap<>();
    private final LinkedHashMap<CommittedTaskBatch.Identity, Boolean> completedPushAdmissions =
            new LinkedHashMap<>(256, 0.75f, true);
    private final AtomicLong lastOrderedWebhookReportNanos = new AtomicLong(Long.MIN_VALUE);
    private final AtomicBoolean orderedWebhookReportInFlight = new AtomicBoolean();

    private final OrderedTaskPublisher orderedTaskPublisher = new OrderedTaskPublisher();
    /**
     * Initial client messages for tasks that have not emitted their first event yet. Event
     * persistence creates its own TaskManager, so this preserves the message that opened a task
     * when that first event creates the task in the TaskStore.
     */
    private final ConcurrentHashMap<String, Message> initialMessages = new ConcurrentHashMap<>();

    /**
     * Initial Tasks for new streaming tasks that have not emitted their first event yet. When that
     * first event is a status or artifact update, this Task is what the TaskStore creates for the
     * task, so the streaming response can begin with the same Task object.
     */
    private final ConcurrentHashMap<String, Task> initialTasks = new ConcurrentHashMap<>();

    private MainEventBus eventBus;

    private TaskStore taskStore;

    private PushNotificationSender pushSender;

    private @Nullable OrderedWebhookDispatcher orderedWebhookDispatcher;

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

    public MainEventBusProcessor(MainEventBus eventBus, TaskStore taskStore, PushNotificationSender pushSender, QueueManager queueManager) {
        this.eventBus = eventBus;
        this.taskStore = taskStore;
        this.pushSender = pushSender;
        this.queueManager = queueManager;
        this.orderedWebhookDispatcher = null;
    }

    @Inject
    public MainEventBusProcessor(MainEventBus eventBus, TaskStore taskStore, PushNotificationSender pushSender,
            QueueManager queueManager, OrderedWebhookDispatcher orderedWebhookDispatcher) {
        this.eventBus = eventBus;
        this.taskStore = taskStore;
        this.pushSender = pushSender;
        this.queueManager = queueManager;
        this.orderedWebhookDispatcher = orderedWebhookDispatcher;
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
     * Registers the message that opens a new task until its first event is persisted.
     *
     * @param taskId the new task identifier
     * @param message the opening client message
     */
    public void registerInitialMessage(String taskId, Message message) {
        initialMessages.put(taskId, message);
    }

    /**
     * Registers the Task that a new task's first event creates in the TaskStore when that event is a
     * status or artifact update, until its first event is persisted. Without a registration, the
     * TaskManager creates it.
     *
     * @param taskId the new task identifier
     * @param initialTask the Task to create: SUBMITTED, with the opening message as its history
     */
    public void registerInitialTask(String taskId, Task initialTask) {
        initialTasks.put(taskId, initialTask);
    }

    /** Remove a registration after all events already submitted by the agent are processed. */
    public void clearInitialMessageAfterEvents(String taskId) {
        eventBus.submit(() -> {
            initialMessages.remove(taskId);
            initialTasks.remove(taskId);
        });
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
                Object item = eventBus.take();
                if (item instanceof MainEventBusContext context) {
                    processEvent(context);
                } else if (item instanceof Runnable action) {
                    action.run();
                }
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
        Event event = resolveMessageTaskAssociation(context);
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

        CommittedTaskBatch replicatedBatch = context.eventQueueItem().isReplicated()
                ? context.eventQueueItem().committedBatch() : null;
        if (replicatedBatch != null) {
            // A trusted committed envelope from another node: the shared store already holds this result, so
            // nothing is mutated or saved here. It is only made visible to this node's local observers.
            try {
                deliverCommittedBatch(mainQueue, replicatedBatch, true);
            } catch (RuntimeException | Error failure) {
                reportPostCommitFailure(taskId, replicatedBatch, failure);
            } finally {
                mainQueue.releaseSemaphore();
            }
            return;
        }

        LOGGER.debug("MainEventBusProcessor: Processing event for task {}: {}",
                    taskId, event.getClass().getSimpleName());

        Event eventToDistribute = null;
        boolean persisted = false;
        UpdateResult updateResult = null;
        boolean eventRoutedLocally = false;
        boolean isReplicated = context.eventQueueItem().isReplicated();
        try {
            // Step 1: Update TaskStore FIRST (persistence before clients see it)
            // If this throws, we distribute an error to ensure "persist before client visibility"

            try {
                rejectTaskOutputAfterDirectResponse(context, event, isReplicated);
                updateResult = updateTaskStore(taskId, event, isReplicated, context.origin());
                if (!isReplicated && (event instanceof Task || event instanceof TaskStatusUpdateEvent
                        || event instanceof TaskArtifactUpdateEvent || event instanceof A2AError)) {
                    initialMessages.remove(taskId);
                    initialTasks.remove(taskId);
                }
                boolean isFinal = updateResult.isFinal();

                if (updateResult.committedBatch() != null && updateResult.slot() != null) {
                    CommittedTaskBatch batch = updateResult.committedBatch();
                    @Nullable Long batchWriterEpoch = updateResult.writerEpoch();
                    orderedTaskPublisher.publish(updateResult.slot(), batch,
                            committedBatch -> deliverCommittedBatch(mainQueue, committedBatch, isReplicated,
                                    event instanceof A2AError error && !isReplicated ? error : null,
                                    !isReplicated ? context.origin() : null, false, batchWriterEpoch))
                            .toCompletableFuture().join();
                } else {
                    if (!isReplicated && taskStore instanceof AtomicTaskStore && event instanceof A2AError) {
                        // An executor error with no changed task result still belongs to the
                        // producer. Do not turn the protocol error into a shared child event.
                        mainQueue.rejectProposal(context.eventQueueItem(), context.origin(), event);
                        eventToDistribute = event;
                        eventRoutedLocally = true;
                    } else {
                        eventToDistribute = event instanceof Task && updateResult.taskSnapshot() != null
                                ? updateResult.taskSnapshot() : event;
                    }

                    // Trigger replication AFTER successful persistence. Finalization and its
                    // committed batch envelope remain the responsibility of T10.
                    if (!isFinal && !isUnassociatedMessage(eventToDistribute)) {
                        EventEnqueueHook hook = mainQueue.getEnqueueHook();
                        if (hook != null) {
                            LOGGER.debug("Triggering replication hook for task {} after successful persistence", taskId);
                            hook.onEnqueue(eventToDistribute == event ? context.eventQueueItem()
                                    : new LocalEventQueueItem(eventToDistribute));
                        }
                    }
                }
                persisted = true;
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
            if (!isReplicated && persisted && updateResult != null && updateResult.committedBatch() == null
                    && eventToDistribute instanceof StreamingEventKind streamingEvent
                    && !isUnassociatedMessage(streamingEvent)) {
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
                        && context.origin() != null && context.origin().openingInput() != null && !isReplicated) {
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
                    if (persisted && updateResult != null
                            && (EventQueue.isFinalEvent(eventToDistribute)
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
                        committedBatch -> deliverCommittedBatch(mainQueue, committedBatch, false, null, null, true,
                                mutation.writerEpoch()))
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
            sendLegacyTaskSnapshotInOrder(task, mutation.writerEpoch());
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
    private UpdateResult updateTaskStore(String taskId, Event event, boolean isReplicated,
            @Nullable EventQueue.ChildQueue origin) throws InternalError {
        try {
            // A new task opened by a status or artifact update: create it as registered (see
            // registerInitialTask); TaskManager then finds it in the TaskStore and applies the update
            Task initialTask = initialTasks.get(taskId);
            if (!isReplicated && !(taskStore instanceof AtomicTaskStore) && initialTask != null
                    && (event instanceof TaskStatusUpdateEvent || event instanceof TaskArtifactUpdateEvent)
                    && taskStore.get(taskId) == null) {
                taskStore.save(initialTask, false);
            }

            // Extract contextId from event (all relevant events have it)
            String contextId = extractContextId(event);

            // Create temporary TaskManager instance for this event
            TaskManager taskManager = new TaskManager(taskId, contextId, taskStore, isReplicated ? null : initialMessages.get(taskId));

            if (!isReplicated && taskStore instanceof AtomicTaskStore atomicStore) {
                AtomicReference<TaskCommitSlot> reservedSlot = new AtomicReference<>();
                OpeningTaskInput opening = pendingOpeningInput(origin, event);
                TaskEnrollmentCoordinator.Enrollment enrollment = null;
                boolean openingRetained = false;
                try {
                    if (opening != null && atomicStore.readSnapshot(taskId) == null) {
                        // Enrollment precedes the task mutation that may notify it; nothing is
                        // persisted for the task itself unless this succeeds.
                        enrollment = opening.enroll();
                        taskManager.retainOpeningInput(opening.input());
                        openingRetained = true;
                    }
                    TaskManager.CommittedProcessingResult result = taskManager.processCommitted(event,
                            (slot, mutation) -> {
                                orderedTaskPublisher.reserve(slot);
                                reservedSlot.set(slot);
                            });
                    CommittedMutation mutation = result.mutation();
                    if (opening != null && openingRetained && mutation != null) {
                        opening.markMaterialized();
                        if (enrollment != null) {
                            enrollment.taskCommitted();
                        }
                    }
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
                            mutation != null && mutation.changed() && mutation.events().isEmpty(), batch, slot,
                            mutation == null ? null : mutation.writerEpoch());
                } catch (RuntimeException | Error failure) {
                    TaskCommitSlot slot = reservedSlot.get();
                    if (slot != null) {
                        orderedTaskPublisher.discard(slot);
                    }
                    settleEnrollment(enrollment, failure);
                    throw failure;
                } catch (Exception failure) {
                    settleEnrollment(enrollment, failure);
                    throw failure;
                }
            }

            // Legacy-only stores and already committed replicated copies retain their existing
            // TaskStore contract; they are not adapted to an atomic get/save pair.
            AtomicReference<Task> taskSnapshot = new AtomicReference<>();
            boolean isFinal = taskManager.process(event, isReplicated, taskSnapshot);
            LOGGER.debug("TaskStore updated via TaskManager.process() for task {}: {} (final: {}, replicated: {})",
                        taskId, event.getClass().getSimpleName(), isFinal, isReplicated);
            return new UpdateResult(isFinal, taskSnapshot.get(), null, false, null, null, null);

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

    /**
     * Commits one request-originated mutation of an existing task through the authority and publishes
     * its committed batch in order to this task's live observers, if any. Nothing is published when the
     * store reports a non-commit. A task with no live queue has no local observers to notify.
     *
     * @throws TaskMutationNotCommittedException if the store definitively did not commit
     * @throws TaskMutationOutcomeUnknownException if the commit outcome cannot be established
     */
    public CommittedMutation commitAndPublish(String taskId, TaskMutation mutation) {
        if (!(taskStore instanceof AtomicTaskStore atomicStore)) {
            throw new IllegalStateException("Committed input requires an AtomicTaskStore");
        }
        EventQueue queue = queueManager.get(taskId);
        EventQueue.MainQueue mainQueue = queue instanceof EventQueue.MainQueue main ? main : null;
        AtomicReference<TaskCommitSlot> reservedSlot = new AtomicReference<>();
        boolean published = false;
        try {
            CommittedMutation committed = new TaskMutationAuthority(atomicStore).mutate(taskId, mutation,
                    (slot, planned) -> {
                        orderedTaskPublisher.reserve(slot);
                        reservedSlot.set(slot);
                    });
            TaskCommitSlot slot = reservedSlot.get();
            if (slot != null && committed.changed() && !committed.events().isEmpty()) {
                CommittedTaskBatch batch = new CommittedTaskBatch(committed.snapshot(), committed.events());
                if (mainQueue != null) {
                    orderedTaskPublisher.publish(slot, batch,
                            committedBatch -> deliverCommittedBatch(mainQueue, committedBatch, false, null, null, false,
                                    committed.writerEpoch()))
                            .toCompletableFuture().join();
                    published = true;
                } else {
                    admitCommittedPushBatch(batch, false, committed.writerEpoch());
                }
            }
            return committed;
        } finally {
            TaskCommitSlot slot = reservedSlot.get();
            if (slot != null && !published) {
                try {
                    orderedTaskPublisher.discard(slot);
                } catch (RuntimeException discardFailure) {
                    LOGGER.warn("Could not discard unpublished input slot for task {}", taskId, discardFailure);
                }
            }
        }
    }

    private static Event resolveMessageTaskAssociation(MainEventBusContext context) {
        Event event = context.eventQueueItem().getEvent();
        EventQueue.ChildQueue origin = context.origin();
        if (!context.eventQueueItem().isReplicated() && origin != null
                && event instanceof Message message && message.taskId() != null) {
            OpeningTaskInput opening = origin.openingInput();
            if (opening != null && !opening.isMaterialized()) {
                return Message.builder(message).taskId(null).build();
            }
        }
        return event;
    }

    private static boolean isUnassociatedMessage(Event event) {
        return event instanceof Message message && message.taskId() == null;
    }

    /** Returns the request's unmaterialized opening input if this event may materialize the task. */
    private static @Nullable OpeningTaskInput pendingOpeningInput(@Nullable EventQueue.ChildQueue origin,
            Event event) {
        if (origin == null || !(event instanceof Task || event instanceof TaskStatusUpdateEvent
                || event instanceof TaskArtifactUpdateEvent)) {
            return null;
        }
        OpeningTaskInput opening = origin.openingInput();
        return opening == null || opening.isMaterialized() ? null : opening;
    }

    private static void settleEnrollment(@Nullable TaskEnrollmentCoordinator.Enrollment enrollment,
            Throwable failure) {
        if (enrollment == null) {
            return;
        }
        if (failure instanceof TaskMutationOutcomeUnknownException) {
            enrollment.outcomeUnknown();
        } else {
            enrollment.taskNotCommitted();
        }
    }

    /** A completed direct Message response cannot be followed by task creation in the same exchange. */
    private static void rejectTaskOutputAfterDirectResponse(MainEventBusContext context, Event event,
            boolean isReplicated) {
        EventQueue.ChildQueue origin = context.origin();
        if (!isReplicated && origin != null && origin.hasSelectedDirectMessageResponse()
                && pendingOpeningInput(origin, event) != null) {
            throw new InvalidParamsError("this invocation already selected a direct Message response");
        }
    }

    void deliverCommittedBatch(EventQueue.MainQueue mainQueue, CommittedTaskBatch batch,
            boolean isReplicated) {
        deliverCommittedBatch(mainQueue, batch, isReplicated, null, null, false, null);
    }

    /** Shares terminal finalization and event-bus admission through one identity-keyed decision. */
    public CompletionStage<Boolean> admitFinalizedCommittedBatch(CommittedTaskBatch batch, boolean taskProposal,
            @Nullable Long writerEpoch) {
        return admitCommittedPushBatch(batch, taskProposal, writerEpoch);
    }

    private void deliverCommittedBatch(EventQueue.MainQueue mainQueue, CommittedTaskBatch batch,
            boolean isReplicated, @Nullable A2AError originError,
            @Nullable EventQueue.ChildQueue origin, boolean legacyTaskSummary, @Nullable Long writerEpoch) {
        String taskId = batch.taskId();
        int childCount = mainQueue.getChildCount();
        if (!isReplicated) {
            // This is also the finalization observer's admission seam. Remote copies never dispatch.
            admitCommittedPushBatch(batch, legacyTaskSummary, writerEpoch);
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

        boolean batchLegacyFinal = batch.events().stream().anyMatch(batchEvent -> EventQueue.isFinalEvent(batchEvent));
        EventEnqueueHook batchHook = mainQueue.getEnqueueHook();
        // Same terminal test as the store's TaskFinalizedEvent trigger (TaskState.isFinal()), so a batch is replicated
        // exactly once: through the hook while the snapshot is non-terminal, through finalization once it is terminal.
        boolean snapshotTerminal = batch.snapshot().task().status().state().isFinal();
        boolean replicateWholeBatch = !isReplicated && !snapshotTerminal && batchHook != null
                && batchHook.handlesCommittedBatches();
        if (replicateWholeBatch && batchHook != null) {
            // One call per committed batch, in publication order, before local fan-out.
            try {
                batchHook.onCommittedBatch(batch);
            } catch (RuntimeException | Error failure) {
                reportPostCommitFailure(taskId, batch, failure);
            }
        }
        List<Long> generationPositions = batch.generationPositions();
        for (int index = 0; index < batch.events().size(); index++) {
            StreamingEventKind event = batch.events().get(index);
            boolean legacyFinal = batchLegacyFinal;
            if (!isReplicated && !legacyFinal && !replicateWholeBatch) {
                EventEnqueueHook hook = batchHook;
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
                        legacyTaskSummary ? index : -1, generationPositions.get(index)));
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
    private PushAdmission reserveCommittedPushBatch(CommittedTaskBatch batch, boolean legacyTaskSummary,
            @Nullable Long writerEpoch) {
        String taskId = batch.taskId();
        OrderedWebhookDispatcher dispatcher = orderedWebhookDispatcher;
        if (dispatcher != null && orderedWebhookDispatchAvailable()) {
            CompletableFuture<Boolean> admission = new CompletableFuture<>();
            CommittedTaskBatch.Identity identity = batch.identity();
            WeakReference<CommittedTaskBatch> batchForReport = new WeakReference<>(batch);
            CompletionStage<Boolean> dispatchResult;
            try {
                dispatchResult = dispatcher.dispatch(batch, legacyTaskSummary, writerEpoch, admission);
                dispatchResult.whenComplete((sent, failure) -> {
                    CommittedTaskBatch reportBatch = batchForReport.get();
                    if (failure != null) {
                        if (reportBatch != null) {
                            reportPostCommitFailure(taskId, reportBatch, failure);
                        } else {
                            LOGGER.warn("Ordered webhook gate failed for task {} at generations {}..{}", taskId,
                                    identity.firstGeneration(), identity.lastGeneration(), failure);
                        }
                    } else if (Boolean.TRUE.equals(admission.getNow(false)) && !Boolean.TRUE.equals(sent)) {
                        if (reportBatch != null) {
                            reportOrderedWebhookFailure(taskId, reportBatch,
                                    new IllegalStateException("Ordered webhook dispatch did not complete successfully"));
                        } else {
                            LOGGER.warn("Ordered webhook dispatch failed for task {} at generations {}..{}", taskId,
                                    identity.firstGeneration(), identity.lastGeneration());
                        }
                    }
                });
            } catch (RuntimeException | Error failure) {
                reportPostCommitFailure(taskId, batch, failure);
                admission.complete(false);
                CompletableFuture<Boolean> rejected = new CompletableFuture<>();
                rejected.completeExceptionally(failure);
                dispatchResult = rejected;
            }
            return new PushAdmission(() -> admission.complete(true), () -> admission.complete(false), dispatchResult);
        }
        CompletableFuture<Boolean> admission = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(10, java.util.concurrent.TimeUnit.SECONDS)
                .execute(() -> admission.complete(false));
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
        return new PushAdmission(invoke, drop, tail.handle((ignored, failure) -> failure == null));
    }

    private CompletionStage<Boolean> admitCommittedPushBatch(CommittedTaskBatch batch, boolean taskProposal,
            @Nullable Long writerEpoch) {
        CommittedTaskBatch.Identity identity = batch.identity();
        CompletableFuture<Boolean> decision;
        boolean owner = false;
        boolean full = false;
        synchronized (pushAdmissionLock) {
            Boolean completed = completedPushAdmissions.get(identity);
            if (completed != null) {
                return CompletableFuture.completedFuture(completed);
            }
            decision = activePushAdmissions.get(identity);
            if (decision == null) {
                if (activePushAdmissions.size() >= MAX_ACTIVE_PUSH_ADMISSIONS) {
                    full = true;
                    decision = CompletableFuture.completedFuture(false);
                    completedPushAdmissions.put(identity, false);
                    while (completedPushAdmissions.size() > MAX_COMPLETED_PUSH_ADMISSIONS) {
                        Iterator<CommittedTaskBatch.Identity> iterator = completedPushAdmissions.keySet().iterator();
                        iterator.next();
                        iterator.remove();
                    }
                } else {
                    decision = new CompletableFuture<>();
                    activePushAdmissions.put(identity, decision);
                    owner = true;
                }
            }
        }
        if (full) {
            LOGGER.warn("Webhook admission capacity is full; recording dropped committed task {} generations {}..{}",
                    identity.taskId(), identity.firstGeneration(), identity.lastGeneration());
            OrderedWebhookDispatcher dispatcher = orderedWebhookDispatcher;
            if (dispatcher != null) {
                try {
                    dispatcher.recordAdmissionLoss(batch, writerEpoch, "webhook admission decision capacity exhausted");
                } catch (RuntimeException | Error failure) {
                    LOGGER.warn("Could not record webhook admission loss for task {}", batch.taskId(), failure);
                }
            }
            return decision;
        }
        if (!owner) {
            return decision;
        }

        PushAdmission admission;
        try {
            admission = reserveCommittedPushBatch(batch, taskProposal, writerEpoch);
        } catch (RuntimeException | Error failure) {
            reportPostCommitFailure(batch.taskId(), batch, failure);
            completePushAdmission(identity, decision, false, null);
            return decision;
        }

        WeakReference<CommittedTaskBatch> reportBatch = new WeakReference<>(batch);
        java.util.function.Consumer<Throwable> failedAdmission = failure -> {
            CommittedTaskBatch retained = reportBatch.get();
            if (retained == null) {
                LOGGER.warn("Webhook admission failed for task {} generations {}..{}", identity.taskId(),
                        identity.firstGeneration(), identity.lastGeneration(), failure);
            } else {
                reportPostCommitFailure(identity.taskId(), retained, failure);
            }
            admission.dropSender().run();
        };
        try {
            callback.admitPushNotifications(batch, admission.invokeSender(), failedAdmission);
        } catch (RuntimeException | Error failure) {
            failedAdmission.accept(failure);
        }
        CompletableFuture<Boolean> admissionDecision = decision;
        admission.result().whenComplete((sent, failure) ->
                completePushAdmission(identity, admissionDecision, sent, failure));
        return admissionDecision;
    }

    private void completePushAdmission(CommittedTaskBatch.Identity identity, CompletableFuture<Boolean> decision,
            @Nullable Boolean sent, @Nullable Throwable failure) {
        boolean result = failure == null && Boolean.TRUE.equals(sent);
        synchronized (pushAdmissionLock) {
            activePushAdmissions.remove(identity, decision);
            completedPushAdmissions.put(identity, result);
            while (completedPushAdmissions.size() > MAX_COMPLETED_PUSH_ADMISSIONS) {
                Iterator<CommittedTaskBatch.Identity> iterator = completedPushAdmissions.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
        if (failure == null) {
            decision.complete(result);
        } else {
            decision.completeExceptionally(failure);
        }
    }

    /**
     * Webhook wiring of this bus for authority-topology validation: without an ordered dispatcher, bus and
     * finalization senders use independent per-task push chains.
     */
    public TaskAuthorityCapabilities.WebhookDispatch webhookDispatchTopology() {
        OrderedWebhookDispatcher dispatcher = orderedWebhookDispatcher;
        return dispatcher == null ? TaskAuthorityCapabilities.WebhookDispatch.NONE : dispatcher.topology();
    }

    private boolean orderedWebhookDispatchAvailable() {
        OrderedWebhookDispatcher dispatcher = orderedWebhookDispatcher;
        if (dispatcher == null) {
            return false;
        }
        try {
            return dispatcher.supportsOrderedDispatch();
        } catch (RuntimeException | Error failure) {
            LOGGER.error("Could not inspect the ordered webhook sender capability", failure);
            throw new IllegalStateException("Could not inspect the ordered webhook sender capability", failure);
        }
    }

    private void invokeCommittedPushBatch(CommittedTaskBatch batch, boolean legacyTaskSummary) {
        String taskId = batch.taskId();
        if (legacyTaskSummary) {
            try {
                if (!pushSender.sendTaskProposalBatchNotification(batch)) {
                    reportPostCommitFailure(taskId, batch,
                            new IllegalStateException("Push notification delivery failed"));
                }
            } catch (RuntimeException | Error failure) {
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
            } catch (RuntimeException | Error failure) {
                reportPostCommitFailure(taskId, batch, failure);
            }
        }
    }

    /** Appends a verified no-op summary after prior committed webhook admissions for this task. */
    private void sendLegacyTaskSnapshotInOrder(Task task, @Nullable Long writerEpoch) {
        String taskId = task.id();
        OrderedWebhookDispatcher dispatcher = orderedWebhookDispatcher;
        boolean orderedAvailable = false;
        if (dispatcher != null) {
            try {
                orderedAvailable = orderedWebhookDispatchAvailable();
            } catch (RuntimeException | Error failure) {
                LOGGER.error("Could not inspect ordered webhook sender capability for task {}", taskId, failure);
                return;
            }
        }
        if (dispatcher != null && orderedAvailable) {
            try {
                dispatcher.dispatchLegacySummary(task, writerEpoch).whenComplete((sent, failure) -> {
                    if (failure != null || !Boolean.TRUE.equals(sent)) {
                        LOGGER.warn("Legacy no-op snapshot webhook delivery failed for task {}", taskId, failure);
                    }
                });
            } catch (RuntimeException | Error failure) {
                LOGGER.warn("Legacy no-op snapshot webhook delivery failed for task {}", taskId, failure);
            }
            return;
        }
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

    /** Rate-limits post-commit reporting for best-effort ordered losses and bounds outstanding callbacks to one. */
    private void reportOrderedWebhookFailure(String taskId, CommittedTaskBatch batch, Throwable failure) {
        long now = System.nanoTime();
        while (true) {
            long previous = lastOrderedWebhookReportNanos.get();
            if (previous != Long.MIN_VALUE && now - previous < ORDERED_WEBHOOK_REPORT_INTERVAL_NANOS) {
                return;
            }
            if (lastOrderedWebhookReportNanos.compareAndSet(previous, now)) {
                break;
            }
        }
        if (!orderedWebhookReportInFlight.compareAndSet(false, true)) {
            return;
        }
        MainEventBusProcessorCallback failureReporter = callback;
        try {
            ForkJoinPool.commonPool().execute(() -> {
                try {
                    failureReporter.onPostCommitFailure(taskId, batch, failure);
                } catch (RuntimeException | Error reportFailure) {
                    LOGGER.error("Could not report ordered webhook failure for task {}", taskId, reportFailure);
                } finally {
                    orderedWebhookReportInFlight.set(false);
                }
            });
        } catch (RuntimeException | Error reportFailure) {
            orderedWebhookReportInFlight.set(false);
            LOGGER.error("Could not schedule ordered webhook failure report for task {}", taskId, reportFailure);
        }
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
