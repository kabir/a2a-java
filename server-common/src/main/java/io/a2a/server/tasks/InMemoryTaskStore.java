package io.a2a.server.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import io.a2a.spec.Artifact;
import io.a2a.spec.ListTasksParams;
import io.a2a.spec.ListTasksResult;
import io.a2a.spec.Message;
import io.a2a.spec.Task;

@ApplicationScoped
public class InMemoryTaskStore implements TaskStore, TaskStateProvider {

    private final ConcurrentMap<String, Task> tasks = new ConcurrentHashMap<>();

    @Override
    public void save(Task task) {
        tasks.put(task.getId(), task);
    }

    @Override
    public Task get(String taskId) {
        return tasks.get(taskId);
    }

    @Override
    public void delete(String taskId) {
        tasks.remove(taskId);
    }

    @Override
    public ListTasksResult list(ListTasksParams params) {
        // Filter and sort tasks in a single stream pipeline
        List<Task> allFilteredTasks = tasks.values().stream()
                .filter(task -> params.contextId() == null || params.contextId().equals(task.getContextId()))
                .filter(task -> params.status() == null ||
                        (task.getStatus() != null && params.status().equals(task.getStatus().state())))
                .filter(task -> params.lastUpdatedAfter() == null ||
                        (task.getStatus() != null &&
                         task.getStatus().timestamp() != null &&
                         task.getStatus().timestamp().toInstant().isAfter(params.lastUpdatedAfter())))
                .sorted(Comparator.comparing((Task t) -> t.getStatus().timestamp(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Task::getId))
                .toList();

        int totalSize = allFilteredTasks.size();

        // Apply pagination
        int pageSize = params.getEffectivePageSize();
        int startIndex = 0;

        // Handle page token using keyset pagination (format: "timestamp_millis:taskId")
        // Find the first task that comes after the pageToken position in sorted order
        if (params.pageToken() != null && !params.pageToken().isEmpty()) {
            String[] tokenParts = params.pageToken().split(":", 2);
            if (tokenParts.length == 2) {
                try {
                    long tokenTimestampMillis = Long.parseLong(tokenParts[0]);
                    java.time.Instant tokenTimestamp = java.time.Instant.ofEpochMilli(tokenTimestampMillis);
                    String tokenId = tokenParts[1];

                    // Find first task where: timestamp < tokenTimestamp OR (timestamp == tokenTimestamp AND id > tokenId)
                    for (int i = 0; i < allFilteredTasks.size(); i++) {
                        Task task = allFilteredTasks.get(i);
                        if (task.getStatus() != null && task.getStatus().timestamp() != null) {
                            java.time.Instant taskTimestamp = task.getStatus().timestamp().toInstant();
                            int timestampCompare = taskTimestamp.compareTo(tokenTimestamp);

                            if (timestampCompare < 0 || (timestampCompare == 0 && task.getId().compareTo(tokenId) > 0)) {
                                startIndex = i;
                                break;
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // Invalid pageToken format, start from beginning
                    startIndex = 0;
                }
            } else {
                // Legacy format (ID only) - fallback for backward compatibility
                for (int i = 0; i < allFilteredTasks.size(); i++) {
                    if (allFilteredTasks.get(i).getId().equals(params.pageToken())) {
                        startIndex = i + 1;
                        break;
                    }
                }
            }
        }

        // Get the page of tasks
        int endIndex = Math.min(startIndex + pageSize, allFilteredTasks.size());
        List<Task> pageTasks = allFilteredTasks.subList(startIndex, endIndex);

        // Determine next page token (format: "timestamp_millis:taskId")
        String nextPageToken = null;
        if (endIndex < allFilteredTasks.size()) {
            Task lastTask = allFilteredTasks.get(endIndex - 1);
            if (lastTask.getStatus() != null && lastTask.getStatus().timestamp() != null) {
                long timestampMillis = lastTask.getStatus().timestamp().toInstant().toEpochMilli();
                nextPageToken = timestampMillis + ":" + lastTask.getId();
            } else {
                // Fallback to ID-only if timestamp is missing
                nextPageToken = lastTask.getId();
            }
        }

        // Transform tasks: limit history and optionally remove artifacts
        int historyLength = params.getEffectiveHistoryLength();
        boolean includeArtifacts = params.shouldIncludeArtifacts();

        List<Task> transformedTasks = pageTasks.stream()
                .map(task -> transformTask(task, historyLength, includeArtifacts))
                .toList();

        return new ListTasksResult(transformedTasks, totalSize, transformedTasks.size(), nextPageToken);
    }

    private Task transformTask(Task task, int historyLength, boolean includeArtifacts) {
        // Limit history if needed (keep most recent N messages)
        List<Message> history = task.getHistory();
        if (historyLength > 0 && history != null && history.size() > historyLength) {
            history = history.subList(history.size() - historyLength, history.size());
        }

        // Remove artifacts if not requested
        List<Artifact> artifacts = includeArtifacts ? task.getArtifacts() : List.of();

        // If no transformation needed, return original task
        if (history == task.getHistory() && artifacts == task.getArtifacts()) {
            return task;
        }

        // Build new task with transformed data
        return new Task.Builder(task)
                .artifacts(artifacts)
                .history(history)
                .build();
    }

    @Override
    public boolean isTaskActive(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        // Task is active if not in final state
        return task.getStatus() == null || task.getStatus().state() == null || !task.getStatus().state().isFinal();
    }

    @Override
    public boolean isTaskFinalized(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        // Task is finalized if in final state (ignores grace period)
        return task.getStatus() != null
                && task.getStatus().state() != null
                && task.getStatus().state().isFinal();
    }
}
