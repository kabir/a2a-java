package org.a2aproject.sdk.server.tasks;

import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * Declared task-mutation authority of one {@link AtomicTaskStore}, used only to validate configuration before
 * A2A 1.1 is exposed. A declaration is an assertion by the store, not evidence: it never proves that a cluster
 * profile passed multi-instance verification, and it does not fence old writers or brokers.
 *
 * <p>A custom {@link AtomicTaskStore} opts in by overriding {@link AtomicTaskStore#authorityCapabilities()}.
 * Stores that do not override it remain usable for legacy-only registrations but cannot back 1.1.</p>
 *
 * @param scope where the atomic authority lives
 * @param storageEpoch the activated writer epoch of shared storage, or {@code null} when it is unmigrated/unfenced
 * @param nodeEpoch this node's configured writer epoch, or {@code null}
 * @param migrationComplete whether every shared row has been baselined under the active epoch
 */
public record TaskAuthorityCapabilities(Scope scope, @Nullable Long storageEpoch, @Nullable Long nodeEpoch,
        boolean migrationComplete) {

    /** Where the authority that serializes task mutations lives. */
    public enum Scope {
        /** The store has not declared an authority; it cannot back 1.1. */
        UNDECLARED,
        /** One JVM owns the authority (for example the in-memory store). */
        PROCESS_LOCAL,
        /** Writers on every node commit through the same transactional storage. */
        SHARED_STORAGE
    }

    /** Queue-manager replication as observed on this node. */
    public record QueueReplication(boolean replicated, @Nullable Long writerEpoch, boolean committedEnvelopes) {

        public static final QueueReplication NONE = new QueueReplication(false, null, false);
    }

    /** Webhook dispatch wiring as observed on this node. */
    public record WebhookDispatch(boolean orderedDispatcher, boolean quiescentSender, boolean sharedGate) {

        /** No ordered dispatcher: independent per-task push chains in this JVM. */
        public static final WebhookDispatch NONE = new WebhookDispatch(false, false, false);
    }

    public static final TaskAuthorityCapabilities UNDECLARED =
            new TaskAuthorityCapabilities(Scope.UNDECLARED, null, null, false);

    public TaskAuthorityCapabilities {
        Assert.checkNotNullParam("scope", scope);
    }

    /** The authority is this JVM; there is no shared storage or epoch. */
    public static TaskAuthorityCapabilities processLocal() {
        return new TaskAuthorityCapabilities(Scope.PROCESS_LOCAL, null, null, true);
    }

    /** Shared transactional storage observed in the given fencing state. */
    public static TaskAuthorityCapabilities sharedStorage(@Nullable Long storageEpoch, @Nullable Long nodeEpoch,
            boolean migrationComplete) {
        return new TaskAuthorityCapabilities(Scope.SHARED_STORAGE, storageEpoch, nodeEpoch, migrationComplete);
    }
}
