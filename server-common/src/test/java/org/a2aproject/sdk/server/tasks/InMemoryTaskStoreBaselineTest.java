package org.a2aproject.sdk.server.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

/** The one-time, process-local legacy baseline of {@link InMemoryTaskStore}. */
class InMemoryTaskStoreBaselineTest {

    private static Task legacy(String id) {
        Message message = Message.builder().role(Message.Role.ROLE_USER).messageId("m")
                .parts(List.of(new TextPart("hi"))).build();
        Artifact artifact = Artifact.builder().artifactId("a").parts(List.of(new TextPart("d"))).build();
        return Task.builder().id(id).contextId("ctx").status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .history(List.of(message)).artifacts(List.of(artifact)).build();
    }

    @Test
    void legacySavedTaskIsBaselinedToGenerationOneOnceAndStaysStable() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task legacy = legacy("t1");
        store.save(legacy, false);
        assertSame(legacy, store.get("t1"), "the legacy get path is unchanged");

        TaskSnapshot first = store.readSnapshot("t1");
        assertNotNull(first);
        assertEquals(1, first.task().generation());
        assertEquals(legacy.history(), first.task().history());
        assertEquals(legacy.artifacts(), first.task().artifacts());
        assertNull(first.task().artifacts().get(0).role(), "absent role is not invented");
        assertEquals(first, store.readSnapshot("t1"));
        assertEquals(first, store.readSnapshot("t1"));
        assertEquals(2, store.webhookDispatchGate().readLane("t1").orElseThrow().nextGeneration());
    }

    @Test
    void baselineIsAppliedBeforeTheFirstMutationPlans() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(legacy("t2"), false);
        CommittedMutation committed = store.mutate("t2", current -> {
            assertEquals(1, current.task().generation());
            Task next = Task.builder(current.task()).generation(2).build();
            TaskStatusUpdateEvent event = new TaskStatusUpdateEvent("t2",
                    new TaskStatus(TaskState.TASK_STATE_WORKING), "ctx", null, 2);
            return new MutationPlan(next, List.<StreamingEventKind>of(event), true);
        });
        assertEquals(2, committed.snapshot().task().generation());
        assertEquals(2, store.webhookDispatchGate().readLane("t2").orElseThrow().nextGeneration());
    }

    @Test
    void positiveGenerationIsKeptAndInitializesLaneBeforeLockFreeReads() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(Task.builder(legacy("t3")).generation(7).build(), false);
        WebhookDispatchLaneState initialLane = store.webhookDispatchGate().readLane("t3").orElse(null);
        assertNotNull(initialLane,
                "legacy save must publish the accepted task and its lane in the same map update");
        assertEquals(8, initialLane.nextGeneration());

        TaskSnapshot snapshot = store.readSnapshot("t3");
        assertEquals(7, snapshot.task().generation());
        assertEquals(snapshot, store.readSnapshot("t3"));
        assertEquals(8, store.webhookDispatchGate().readLane("t3").orElseThrow().nextGeneration());

        WebhookDispatchClaim held = store.webhookDispatchGate().claim("t3", 0, 8, 8).orElseThrow();
        store.save(Task.builder(legacy("t3")).generation(9).build(), false);
        WebhookDispatchLaneState afterSave = store.webhookDispatchGate().readLane("t3").orElseThrow();
        assertEquals(8, afterSave.nextGeneration(), "a subsequent save must not reset the accepted cursor");
        assertEquals(held.holderToken(), afterSave.holderToken(), "a subsequent save must preserve lane ownership");
    }

    @Test
    void exhaustedPositiveGenerationSaveDoesNotOverflowOrDisturbAnExistingLane() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task exhausted = Task.builder(legacy("t6")).generation(Long.MAX_VALUE).build();

        assertDoesNotThrow(() -> store.save(exhausted, false));
        TaskSnapshot snapshot = store.readSnapshot("t6");
        assertNotNull(snapshot);
        assertEquals(Long.MAX_VALUE, snapshot.task().generation());
        assertNull(store.webhookDispatchGate().readLane("t6").orElse(null),
                "no representable next generation exists for an initially exhausted task");

        assertTrue(store.webhookDispatchGate().initializeTaskLane("t7", 0, 12));
        WebhookDispatchClaim held = store.webhookDispatchGate().claim("t7", 0, 12, 12).orElseThrow();
        assertDoesNotThrow(() -> store.save(Task.builder(legacy("t7")).generation(Long.MAX_VALUE).build(), false));
        WebhookDispatchLaneState lane = store.webhookDispatchGate().readLane("t7").orElseThrow();
        assertEquals(12, lane.nextGeneration());
        assertEquals(held.holderToken(), lane.holderToken());
    }

    @Test
    void atomicallyCreatedTasksAreNotRebaselined() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        CommittedMutation created = store.mutate("t4", current -> new MutationPlan(
                Task.builder().id("t4").contextId("ctx").status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                        .generation(1).build(),
                List.<StreamingEventKind>of(new TaskStatusUpdateEvent("t4",
                        new TaskStatus(TaskState.TASK_STATE_WORKING), "ctx", null, 1)), true));
        assertEquals(created.snapshot(), store.readSnapshot("t4"));
        assertEquals(1, store.webhookDispatchGate().readLane("t4").orElseThrow().nextGeneration());
    }

    @Test
    void rejectedBaselinePlannerDoesNotPublishWebhookLane() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(legacy("t5"), false);

        assertThrows(TaskMutationNotCommittedException.class,
                () -> store.mutate("t5", current -> {
                    assertEquals(1, current.task().generation());
                    throw new IllegalArgumentException("reject baseline plan");
                }));

        assertNull(store.webhookDispatchGate().readLane("t5").orElse(null),
                "a rejected generation-zero plan must not publish migration metadata");
    }
}
