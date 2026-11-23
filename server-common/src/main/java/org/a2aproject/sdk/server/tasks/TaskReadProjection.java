package org.a2aproject.sdk.server.tasks;

import java.util.List;

import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.jspecify.annotations.Nullable;

/**
 * Read-only windows over a task's {@code history} and {@code timeline}.
 *
 * <p>The two limits are independent. {@code null} leaves the field as stored, zero returns an empty
 * list, and a finite value keeps the most recent N entries. The result is a returned view only; it
 * never changes canonical stored state, generation or any other field.</p>
 */
public final class TaskReadProjection {

    private TaskReadProjection() {
    }

    /** Applies independent history and timeline windows to a returned task view. */
    public static Task project(Task task, @Nullable Integer historyLength, @Nullable Integer timelineLength) {
        List<Message> history = task.history();
        List<TimelineEntry> timeline = task.timeline();
        List<Message> limitedHistory = limit(history, historyLength);
        List<TimelineEntry> limitedTimeline = limit(timeline, timelineLength);
        if (limitedHistory == history && limitedTimeline == timeline) {
            return task;
        }
        return Task.builder(task).history(limitedHistory).timeline(limitedTimeline).build();
    }

    private static <T> @Nullable List<T> limit(@Nullable List<T> entries, @Nullable Integer limit) {
        // Negative values leave entries untouched (defensive guard against IndexOutOfBounds).
        if (entries == null || limit == null || limit < 0 || limit >= entries.size()) {
            return entries;
        }
        return limit == 0 ? List.of() : List.copyOf(entries.subList(entries.size() - limit, entries.size()));
    }
}
