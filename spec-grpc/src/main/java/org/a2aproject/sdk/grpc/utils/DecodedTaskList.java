package org.a2aproject.sdk.grpc.utils;

import java.util.List;

import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;

/** A task-list result with per-task decode evidence in the same order as the result tasks. */
public record DecodedTaskList(ListTasksResult value, List<DecodedTask> tasks) {

    public DecodedTaskList {
        tasks = List.copyOf(tasks);
    }
}
