package org.a2aproject.sdk.server.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.Test;

class TaskReadProjectionTest {

    private static Message message(int index) {
        return Message.builder().messageId("m" + index).role(Message.Role.ROLE_USER).parts(new TextPart("m" + index))
                .build();
    }

    private static Task task(int count) {
        List<Message> history = new ArrayList<>();
        List<TimelineEntry> timeline = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            history.add(message(i));
            timeline.add(new TimelineEntry(i, new TimelineEntry.MessageValue(message(i))));
        }
        return Task.builder().id("t").contextId("c").status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .history(history).timeline(timeline).generation(count).build();
    }

    @Test
    void omittedLimitsReturnTheStoredTaskUnchanged() {
        Task stored = task(4);
        assertSame(stored, TaskReadProjection.project(stored, null, null));
    }

    @Test
    void zeroAndFiniteLimitsAreIndependentForHistoryAndTimeline() {
        Task stored = task(5);

        Task historyOnly = TaskReadProjection.project(stored, 2, null);
        assertEquals(List.of("m4", "m5"), historyOnly.history().stream().map(Message::messageId).toList());
        assertEquals(5, historyOnly.timeline().size(), "an omitted timeline limit leaves the timeline alone");

        Task timelineOnly = TaskReadProjection.project(stored, null, 3);
        assertEquals(5, timelineOnly.history().size(), "an omitted history limit leaves history alone");
        assertEquals(List.of(3L, 4L, 5L), timelineOnly.timeline().stream().map(TimelineEntry::generation).toList());

        Task zeroTimeline = TaskReadProjection.project(stored, 4, 0);
        assertEquals(4, zeroTimeline.history().size());
        assertTrue(zeroTimeline.timeline().isEmpty());

        Task zeroHistory = TaskReadProjection.project(stored, 0, 1);
        assertTrue(zeroHistory.history().isEmpty());
        assertEquals(List.of(5L), zeroHistory.timeline().stream().map(TimelineEntry::generation).toList());
    }

    @Test
    void projectionNeverChangesGenerationOrCanonicalStoredTask() {
        Task stored = task(3);
        Task projected = TaskReadProjection.project(stored, 0, 0);
        assertNotSame(stored, projected);
        assertEquals(stored.generation(), projected.generation());
        assertEquals(stored.status(), projected.status());
        assertEquals(3, stored.history().size());
        assertEquals(3, stored.timeline().size());
    }

    @Test
    void limitAtOrAboveSizeAndAbsentTimelineAreLeftAsStored() {
        Task stored = task(2);
        assertSame(stored, TaskReadProjection.project(stored, 2, 2));
        assertSame(stored, TaskReadProjection.project(stored, 10, 10));

        Task noTimeline = Task.builder(stored).timeline(null).build();
        assertSame(noTimeline, TaskReadProjection.project(noTimeline, null, 0));
    }
}
