package org.a2aproject.sdk.grpc.utils;

import org.a2aproject.sdk.spec.Task;

/** A task snapshot and the decode evidence associated with that snapshot. */
public record DecodedTask(Task task, DecodeNotes notes) {
}
