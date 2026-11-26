package org.a2aproject.sdk.extras.queuemanager.replicated.core;

import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.a2aproject.sdk.extras.common.WriterEpochProperty;
import org.a2aproject.sdk.extras.common.events.TaskFinalizedEvent;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.server.events.DefaultTaskStreamLifecycleHook;
import org.a2aproject.sdk.server.events.EventEnqueueHook;
import org.a2aproject.sdk.server.events.EventQueue;
import org.a2aproject.sdk.server.events.EventQueueFactory;
import org.a2aproject.sdk.server.events.EventQueueItem;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.events.TaskStreamLifecycleHook;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.TaskAuthorityCapabilities;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Alternative
@Priority(50)
public class ReplicatedQueueManager implements QueueManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplicatedQueueManager.class);

    // Fields set by constructor injection cannot be final. We need a noargs constructor for
    // Jakarta compatibility, and it seems that making fields set by constructor injection
    // final, is not proxyable in all runtimes
    private InMemoryQueueManager delegate;
    private ReplicationStrategy replicationStrategy;
    private TaskStateProvider taskStateProvider;
    private TaskStreamLifecycleHook streamLifecycleHook;

    @Inject
    private @Nullable MainEventBusProcessor mainEventBusProcessor;

    /**
     * Migrated writer epoch of this node, or null in the legacy-only profile. When set, local committed batches
     * are replicated as epoch-stamped committed envelopes and only envelopes of the same epoch are accepted.
     * Fencing old brokers/producers is a deployment precondition; nothing here enforces it.
     */
    private @Nullable Long writerEpoch;

    private String originId = UUID.randomUUID().toString();

    private final CommittedBatchSequencer sequencer = new CommittedBatchSequencer();

    void setMainEventBusProcessor(MainEventBusProcessor processor) {
        this.mainEventBusProcessor = processor;
    }

    /**
     * No-args constructor for CDI proxy creation.
     * CDI requires a non-private constructor to create proxies for @ApplicationScoped beans.
     * All fields are initialized by the @Inject constructor during actual bean creation.
     */
    @SuppressWarnings("NullAway")
    protected ReplicatedQueueManager() {
        // For CDI proxy creation
        this.delegate = null;
        this.replicationStrategy = null;
        this.taskStateProvider = null;
        this.streamLifecycleHook = null;
        this.mainEventBusProcessor = null;
    }

    public ReplicatedQueueManager(ReplicationStrategy replicationStrategy,
                                    TaskStateProvider taskStateProvider,
                                    MainEventBus mainEventBus) {
        this(replicationStrategy, taskStateProvider, mainEventBus, new DefaultTaskStreamLifecycleHook());
    }

    public ReplicatedQueueManager(ReplicationStrategy replicationStrategy,
                                    TaskStateProvider taskStateProvider,
                                    MainEventBus mainEventBus,
                                    TaskStreamLifecycleHook streamLifecycleHook) {
        this(replicationStrategy, taskStateProvider, mainEventBus, streamLifecycleHook, (Long) null);
    }

    /**
     * Creates a manager that replicates committed batches under {@code writerEpoch} (legacy-only when null).
     *
     * @param writerEpoch the node's migrated writer epoch, or null for the legacy-only profile
     */
    public ReplicatedQueueManager(ReplicationStrategy replicationStrategy,
                                    TaskStateProvider taskStateProvider,
                                    MainEventBus mainEventBus,
                                    TaskStreamLifecycleHook streamLifecycleHook,
                                    @Nullable Long writerEpoch) {
        this.replicationStrategy = replicationStrategy;
        this.taskStateProvider = taskStateProvider;
        this.streamLifecycleHook = streamLifecycleHook;
        this.writerEpoch = writerEpoch;
        this.delegate = new InMemoryQueueManager(
                new ReplicatingEventQueueFactory(), taskStateProvider, mainEventBus, streamLifecycleHook);
    }

    @Inject
    public ReplicatedQueueManager(ReplicationStrategy replicationStrategy,
                                    TaskStateProvider taskStateProvider,
                                    MainEventBus mainEventBus,
                                    TaskStreamLifecycleHook streamLifecycleHook,
                                    A2AConfigProvider configProvider) {
        this(replicationStrategy, taskStateProvider, mainEventBus, streamLifecycleHook,
                configProvider.getOptionalValue(WriterEpochProperty.NAME).map(Long::valueOf).orElse(null));
        if (writerEpoch != null && taskStateProvider instanceof InMemoryTaskStore) {
            // Replicating committed batches needs a shared store that holds them; local memory cannot.
            throw new IllegalStateException("A configured " + WriterEpochProperty.NAME
                    + " requires a shared task store; replicated local in-memory task storage cannot provide "
                    + "committed cluster replication");
        }
    }

    @Override
    public TaskAuthorityCapabilities.QueueReplication replication() {
        return new TaskAuthorityCapabilities.QueueReplication(true, writerEpoch,
                replicationStrategy.supportsCommittedReplication());
    }

    @Override
    public void add(String taskId, EventQueue queue) {
        delegate.add(taskId, queue);
    }

    @Override
    public EventQueue get(String taskId) {
        return delegate.get(taskId);
    }

    @Override
    public EventQueue tap(String taskId) {
        return delegate.tap(taskId);
    }

    @Override
    public void close(String taskId) {
        // Close the local queue - this will trigger onClose callbacks
        // The poison pill callback will check isTaskFinalized() and send if needed
        // The cleanup callback will remove the queue from the map
        delegate.close(taskId);
    }

    @Override
    public EventQueue createOrTap(String taskId) {
        return delegate.createOrTap(taskId);
    }

    @Override
    public void awaitQueuePollerStart(EventQueue eventQueue) throws InterruptedException {
        delegate.awaitQueuePollerStart(eventQueue);
    }

    public void onReplicatedEvent(@Observes ReplicatedEventQueueItem replicatedEvent) {
        if (!acceptedByEpoch(replicatedEvent)) {
            return;
        }
        if (replicatedEvent.isCommittedBatch()) {
            onCommittedEnvelope(replicatedEvent);
            return;
        }
        if (writerEpoch != null && replicatedEvent.isClosedEvent()
                && replicatedEvent.getClosedAfterGeneration() != null) {
            // Poison pill ordered behind the terminal batch it follows.
            long after = replicatedEvent.getClosedAfterGeneration();
            sequencer.runAfterGeneration(replicatedEvent.getTaskId(), after, () -> enqueueReplicated(replicatedEvent));
            return;
        }
        enqueueReplicated(replicatedEvent);
    }

    /**
     * Validates provenance. In the legacy-only profile every record is accepted as before. Under a writer epoch,
     * only records stamped with the same epoch by another manager are delivered: a delayed unversioned record
     * (an old binary or pre-migration producer) and a record from another epoch are rejected. This is a receiver
     * check on a self-declared stamp; it does not authenticate the sender or fence an old broker.
     */
    private boolean acceptedByEpoch(ReplicatedEventQueueItem item) {
        Long epoch = writerEpoch;
        if (epoch == null) {
            if (item.isCommittedBatch()) {
                LOGGER.warn("Rejecting committed replication envelope for task {}: this node has no writer epoch",
                        item.getTaskId());
                return false;
            }
            return true;
        }
        if (originId.equals(item.getOriginId())) {
            // Own broadcast echoed back by the broker; the local observers already saw it.
            LOGGER.debug("Dropping own replicated record for task {}", item.getTaskId());
            return false;
        }
        Long itemEpoch = item.getWriterEpoch();
        if (itemEpoch == null || !itemEpoch.equals(epoch)) {
            LOGGER.warn("Rejecting replicated record for task {}: writer epoch {} does not match this node's {}",
                    item.getTaskId(), itemEpoch, epoch);
            return false;
        }
        if (!item.isCommittedBatch() && !item.isClosedEvent()) {
            // A bare event would be re-applied to the shared store by the legacy receive path (a second mutation).
            LOGGER.warn("Rejecting non-committed replicated event for task {} under writer epoch {}",
                    item.getTaskId(), epoch);
            return false;
        }
        return true;
    }

    private void onCommittedEnvelope(ReplicatedEventQueueItem envelope) {
        CommittedTaskBatch batch;
        try {
            batch = envelope.committedBatch();
        } catch (RuntimeException invalid) {
            LOGGER.warn("Rejecting invalid committed replication envelope for task {}", envelope.getTaskId(), invalid);
            return;
        }
        if (batch == null) {
            return;
        }
        CommittedBatchSequencer.Outcome outcome = sequencer.offer(batch, () -> enqueueReplicated(envelope));
        switch (outcome) {
            case DELIVERED, BUFFERED -> LOGGER.debug("Committed batch {} for task {}: {}", batch.identity(),
                    batch.taskId(), outcome);
            case DUPLICATE, STALE -> LOGGER.debug("Ignoring {} committed batch {} for task {}", outcome,
                    batch.identity(), batch.taskId());
            case CONFLICT, OVERFLOW -> LOGGER.warn("Rejected committed batch {} for task {}: {}",
                    batch.identity(), batch.taskId(), outcome);
        }
    }

    private void enqueueReplicated(ReplicatedEventQueueItem replicatedEvent) {
        // Check if task is still active before processing replicated event
        // Always allow QueueClosedEvent and Task events (they carry final state)
        // Skip other event types for inactive tasks to prevent queue creation for expired tasks
        if (!replicatedEvent.isClosedEvent()
                && !replicatedEvent.isTaskEvent()
                && !(replicatedEvent.isCommittedBatch() && replicatedEvent.isCanonicalFinal())
                && !taskStateProvider.isTaskActive(replicatedEvent.getTaskId())) {
            // Task is no longer active - skip processing this replicated event
            // This prevents creating queues for tasks that have been finalized beyond the grace period
            LOGGER.debug("Skipping replicated event for inactive task {}", replicatedEvent.getTaskId());
            return;
        }

        // Get the MainQueue to enqueue the replicated event item
        // We must use enqueueItem (not enqueueEvent) to preserve the isReplicated() flag
        // and avoid triggering the replication hook again (which would cause a replication loop)
        //
        // IMPORTANT: We must NOT create a ChildQueue here! Creating and immediately closing
        // a ChildQueue means there are zero children when MainEventBusProcessor distributes
        // the event. Existing ChildQueues (from active client subscriptions) will receive
        // the event when MainEventBusProcessor distributes it to all children.
        //
        // If MainQueue doesn't exist, create it. This handles late-arriving replicated events
        // for tasks that were created on another instance.
        EventQueue childQueue = null;  // Track ChildQueue we might create
        EventQueue mainQueue = delegate.get(replicatedEvent.getTaskId());
        try {
            if (mainQueue == null) {
                LOGGER.debug("Creating MainQueue for replicated event on task {}", replicatedEvent.getTaskId());
                childQueue = delegate.createOrTap(replicatedEvent.getTaskId());  // Creates MainQueue + returns ChildQueue
                mainQueue = delegate.get(replicatedEvent.getTaskId());          // Get MainQueue from map
            }

            if (mainQueue != null) {
                mainQueue.enqueueItem(replicatedEvent);
            } else {
                LOGGER.warn(
                        "MainQueue not found for task {}, cannot enqueue replicated event. This may happen if the queue was already cleaned up.",
                        replicatedEvent.getTaskId());
            }
        } finally {
            if (childQueue != null) {
                try {
                    childQueue.close();  // Close the ChildQueue we created (not MainQueue!)
                } catch (Exception ignore) {
                    // The close is safe, but print a stacktrace just in case
                    if (LOGGER.isDebugEnabled()) {
                        ignore.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Observes task finalization events fired AFTER database transaction commits.
     * This guarantees the task's final state is durably stored before replication.
     *
     * Sends TaskStatusUpdateEvent (not full Task) FIRST, then the poison pill (QueueClosedEvent),
     * ensuring correct event ordering across instances and eliminating race conditions where
     * the poison pill arrives before the final task state.
     *
     * IMPORTANT: We send TaskStatusUpdateEvent instead of full Task to maintain consistency
     * with local event distribution. Clients expect TaskStatusUpdateEvent for status changes,
     * and sending the full Task causes issues in remote instances where clients don't handle
     * bare Task objects the same way they handle TaskStatusUpdateEvent.
     *
     * @param event the task finalized event containing the task ID and final Task
     */
    public void onTaskFinalized(@Observes(during = TransactionPhase.AFTER_SUCCESS) TaskFinalizedEvent event) {
        String taskId = event.getTaskId();
        org.a2aproject.sdk.spec.Task finalTask = (org.a2aproject.sdk.spec.Task) event.getTask();  // Cast from Object
        Long epoch = writerEpoch;
        CommittedTaskBatch finalBatch = event.getCommittedBatch() instanceof CommittedTaskBatch committed
                ? committed : null;

        MainEventBusProcessor busProcessor = mainEventBusProcessor;
        if (finalBatch != null && busProcessor != null) {
            try {
                CommittedTaskBatch.Identity identity = finalBatch.identity();
                busProcessor.admitFinalizedCommittedBatch(finalBatch, event.isTaskProposal(), event.getWriterEpoch())
                        .whenComplete((sent, failure) -> {
                            if (failure != null || !Boolean.TRUE.equals(sent)) {
                                LOGGER.warn("Finalized webhook admission failed for task {} generations {}..{}",
                                        identity.taskId(), identity.firstGeneration(), identity.lastGeneration(), failure);
                            }
                        });
            } catch (RuntimeException | Error dispatchFailure) {
                LOGGER.warn("Finalized webhook admission failed for task {}", taskId, dispatchFailure);
            }
        }

        if (epoch != null && finalBatch != null) {
            // The terminal batch was committed through the authority and already carries its canonical stamps.
            // Forward it unchanged (never a synthesized status generation), then the ordered poison pill.
            LOGGER.debug("Task {} finalized - forwarding committed terminal batch then poison pill", taskId);
            try {
                sendCommitted(finalBatch, true);
            } catch (RuntimeException | Error replicationFailure) {
                LOGGER.error("Could not replicate committed terminal batch for task {}", taskId, replicationFailure);
            } finally {
                try {
                    replicationStrategy.send(ReplicatedEventQueueItem.stampedClosed(taskId, epoch, originId,
                            finalBatch.identity().lastGeneration()));
                } catch (RuntimeException | Error closeFailure) {
                    LOGGER.error("Could not replicate terminal close marker for task {}", taskId, closeFailure);
                }
            }
            return;
        }

        if (epoch != null) {
            // No committed batch (a legacy save path) under an epoch: a status event would have to be synthesized
            // with a made-up generation, which receivers must not accept. Only the closing pill is sent.
            LOGGER.warn("Task {} finalized without a committed batch under writer epoch {}; no terminal event is "
                    + "replicated, only the poison pill", taskId, epoch);
            try {
                replicationStrategy.send(ReplicatedEventQueueItem.stampedClosed(taskId, epoch, originId, null));
            } catch (RuntimeException | Error closeFailure) {
                LOGGER.error("Could not replicate terminal close marker for task {}", taskId, closeFailure);
            }
            return;
        }

        LOGGER.debug("Task {} finalized - sending TaskStatusUpdateEvent then poison pill (QueueClosedEvent) after transaction commit", taskId);

        // Legacy path: convert final Task to TaskStatusUpdateEvent to match local event distribution.
        org.a2aproject.sdk.spec.TaskStatusUpdateEvent finalStatusEvent = org.a2aproject.sdk.spec.TaskStatusUpdateEvent.builder()
                .taskId(taskId)
                .contextId(finalTask.contextId())
                .status(finalTask.status())
                .build();

        // Send TaskStatusUpdateEvent FIRST to ensure it arrives before poison pill
        try {
            replicationStrategy.send(taskId, finalStatusEvent);
        } catch (RuntimeException | Error replicationFailure) {
            LOGGER.error("Could not replicate final status for task {}", taskId, replicationFailure);
        } finally {
            // Keep the terminal close marker independent from final-status replication failure.
            org.a2aproject.sdk.server.events.QueueClosedEvent closedEvent =
                    new org.a2aproject.sdk.server.events.QueueClosedEvent(taskId);
            try {
                replicationStrategy.send(taskId, closedEvent);
            } catch (RuntimeException | Error closeFailure) {
                LOGGER.error("Could not replicate terminal close marker for task {}", taskId, closeFailure);
            }
        }
    }

    /**
     * Replicates one locally committed batch. The same call serves the event-bus path (non-terminal batches) and
     * the post-commit finalization path (terminal batches); both carry the same {@link CommittedTaskBatch.Identity}.
     *
     * <p>The batch is first recorded as this node's own publication: a record from another node for a later
     * generation then waits, and one for an earlier generation is dropped as stale, so local observers never see
     * generations move backwards however long the broker hand-off takes. The hold ends when the local fan-out of
     * the batch is done (signalled by the hook) or, with {@code endWithSend}, when the send returns.</p>
     */
    private void sendCommitted(CommittedTaskBatch batch, boolean endWithSend) {
        Long epoch = writerEpoch;
        if (epoch == null) {
            return;
        }
        sequencer.localBegin(batch);
        try {
            replicationStrategy.send(ReplicatedEventQueueItem.committed(batch, epoch, originId));
        } finally {
            if (endWithSend) {
                sequencer.localEnd(batch.taskId());
            }
        }
    }

    @Override
    public EventQueue.EventQueueBuilder getEventQueueBuilder(String taskId) {
        return QueueManager.super.getEventQueueBuilder(taskId)
                .hook(new ReplicationHook(taskId));
    }

    @Override
    public int getActiveChildQueueCount(String taskId) {
        return delegate.getActiveChildQueueCount(taskId);
    }

    private class ReplicatingEventQueueFactory implements EventQueueFactory {
        @Override
        public EventQueue.EventQueueBuilder builder(String taskId) {
            // Poison pill sending is handled by the TaskFinalizedEvent observer (onTaskFinalized)
            // which sends the QueueClosedEvent after the database transaction commits.
            // This ensures proper ordering and transactional guarantees.

            // Call createBaseEventQueueBuilder() directly to avoid infinite recursion
            // (getEventQueueBuilder() would delegate back to this factory, creating a loop)
            // The base builder already includes: taskId, cleanup callback, taskStateProvider, mainEventBus
            return delegate.createBaseEventQueueBuilder(taskId)
                    .hook(new ReplicationHook(taskId));
        }
    }


    private class ReplicationHook implements EventEnqueueHook {
        private final String taskId;

        public ReplicationHook(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public boolean handlesCommittedBatches() {
            return writerEpoch != null;
        }

        @Override
        public void onCommittedBatch(CommittedTaskBatch batch) {
            sendCommitted(batch, false);
        }

        @Override
        public void onCommittedBatchPublished(CommittedTaskBatch batch) {
            sequencer.localEnd(batch.taskId());
        }

        @Override
        public void onEnqueue(EventQueueItem item) {
            if (writerEpoch != null) {
                // Under a writer epoch only committed, epoch-stamped batches are replicated (onCommittedBatch).
                return;
            }
            if (!item.isReplicated()) {
                // Only replicate if this isn't already a replicated event
                // This prevents replication loops
                if (taskId != null) {
                    replicationStrategy.send(taskId, item.getEvent());
                }
            }
        }
    }
}
