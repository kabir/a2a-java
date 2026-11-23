package org.a2aproject.sdk.client;

import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * An immutable client projection of a task with explicit synchronization and coverage.
 * <p>
 * The task may be partial or windowed; {@code sync} and the coverage fields say how far it can be trusted.
 * The history and timeline windows are tracked independently.
 *
 * @param task the projected task, or {@code null} when nothing has been observed
 * @param sync the synchronization state
 * @param throughGeneration the last safely interpreted authoritative generation, or {@code null} if none
 * @param history the coverage of the task history window
 * @param timeline the coverage of the task timeline window
 */
public record TaskView(@Nullable Task task, ViewSync sync, @Nullable Long throughGeneration,
        HistoryCoverage history, HistoryCoverage timeline) {

    public TaskView {
        Assert.checkNotNullParam("sync", sync);
        Assert.checkNotNullParam("history", history);
        Assert.checkNotNullParam("timeline", timeline);
    }
}
