package org.a2aproject.sdk.server.tasks;

/**
 * Opaque revision of a task's stored representation.
 *
 * <p>This value is assigned by storage and is independent of the protocol generation carried by
 * {@link org.a2aproject.sdk.spec.Task} and its events.</p>
 *
 * @param value positive storage revision
 */
public record TaskRevision(long value) {

    public TaskRevision {
        if (value <= 0) {
            throw new IllegalArgumentException("value must be positive");
        }
    }
}
