package org.a2aproject.sdk.extras.common;

/**
 * Configuration property naming the migrated writer epoch of a node. It is shared by the JPA task store
 * (which fences storage writes with it) and the replicated queue manager (which stamps and validates
 * replication records with it). Both read it independently; nothing here enforces broker fencing.
 */
public final class WriterEpochProperty {

    /** Writer epoch of this node; absent for a legacy-only node. */
    public static final String NAME = "a2a.task-store.writer-epoch";

    private WriterEpochProperty() {
    }
}
