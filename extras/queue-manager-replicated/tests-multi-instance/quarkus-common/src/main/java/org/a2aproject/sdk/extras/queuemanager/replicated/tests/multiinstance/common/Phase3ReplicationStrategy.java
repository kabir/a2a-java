package org.a2aproject.sdk.extras.queuemanager.replicated.tests.multiinstance.common;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.a2aproject.sdk.extras.queuemanager.replicated.core.ReplicatedEventQueueItem;
import org.a2aproject.sdk.extras.queuemanager.replicated.core.ReplicationStrategy;
import org.a2aproject.sdk.extras.queuemanager.replicated.mp_reactive.ReactiveMessagingReplicationStrategy;
import org.a2aproject.sdk.server.events.CommittedTaskBatch;

/**
 * Test decorator around the real Kafka strategy. It records every publication and offers the "publish" barrier,
 * which sits after the database commit and before the record is handed to the broker emitter.
 */
@ApplicationScoped
@Alternative
@Priority(100)
public class Phase3ReplicationStrategy implements ReplicationStrategy {

    @Inject
    ReactiveMessagingReplicationStrategy delegate;

    @Override
    public void send(String taskId, org.a2aproject.sdk.spec.Event event) {
        String key = taskId + ":legacy-event";
        Phase3Instrumentation.trace("publish-legacy", key);
        delegate.send(taskId, event);
    }

    @Override
    public boolean supportsCommittedReplication() {
        return delegate.supportsCommittedReplication();
    }

    @Override
    public void send(ReplicatedEventQueueItem item) {
        String key = describe(item);
        Phase3Instrumentation.trace("publish-before", key);
        Phase3Instrumentation.pass(Phase3Instrumentation.PUBLISH, key);
        try {
            delegate.send(item);
            Phase3Instrumentation.trace("publish-after", key);
        } catch (RuntimeException | Error failure) {
            Phase3Instrumentation.trace("publish-failed", key + " " + failure);
            throw failure;
        }
    }

    static String describe(ReplicatedEventQueueItem item) {
        if (item.isCommittedBatch()) {
            CommittedTaskBatch batch = item.committedBatch();
            if (batch != null) {
                CommittedTaskBatch.Identity id = batch.identity();
                return id.taskId() + ":batch:" + id.firstGeneration() + "-" + id.lastGeneration();
            }
        }
        if (item.isClosedEvent()) {
            return item.getTaskId() + ":closed-after:" + item.getClosedAfterGeneration();
        }
        return item.getTaskId() + ":other";
    }
}
