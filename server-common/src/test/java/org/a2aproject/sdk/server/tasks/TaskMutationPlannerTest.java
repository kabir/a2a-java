package org.a2aproject.sdk.server.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Message.Role;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.a2aproject.sdk.spec.util.Utils;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

class TaskMutationPlannerTest {

    @Test
    void bootstrapAndSeparateStatusUseTheSelectedGenerationTrace() {
        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-11-17T17:00:00Z");
        PendingTaskInput opening = new PendingTaskInput(message("open", Role.ROLE_USER, null, null), List.of(
                artifactUpdate("task-1", "context-1", artifact("a1", null, "one"), false, true, 901),
                artifactUpdate("task-1", "context-1", artifact("a2", null, "two"), false, true, 902)),
                configuration(null), Map.of("source", "test"),
                new PendingTaskInput.Admission("task-1", "context-1", "tenant-a", true, true), receivedAt);

        MutationPlan openingPlan = TaskMutationPlanner.planInput(null, opening, MutationOrigin.USER);

        assertEquals(3, openingPlan.task().generation());
        assertEquals(List.of(1L, 2L, 3L), openingPlan.events().stream().map(TaskMutationPlannerTest::generation).toList());
        assertInstanceOf(TaskMessageUpdateEvent.class, openingPlan.events().get(0));
        assertEquals(1, openingPlan.task().timeline().size());
        assertEquals(1, openingPlan.task().timeline().get(0).generation());
        assertEquals(2, openingPlan.task().artifacts().get(0).startGeneration());
        assertEquals(2, openingPlan.task().artifacts().get(0).endGeneration());
        assertEquals(Role.ROLE_USER, openingPlan.task().artifacts().get(0).role());
        assertEquals(receivedAt, openingPlan.task().status().timestamp());

        TaskStatusUpdateEvent supplied = statusUpdate("task-1", "context-1", TaskState.TASK_STATE_SUBMITTED, 77);
        MutationPlan statusPlan = TaskMutationPlanner.planStatus(
                new TaskSnapshot(openingPlan.task(), new TaskRevision(1)), supplied);

        assertEquals(4, statusPlan.task().generation());
        assertEquals(4, generation(statusPlan.events().get(0)));
        assertEquals(4, statusPlan.task().timeline().get(1).generation());
        assertEquals(TaskState.TASK_STATE_SUBMITTED, statusPlan.task().status().state());
        assertEquals(4, statusPlan.task().generation(), "the opening snapshot summarizes mutations through generation four");
    }

    @Test
    void messagePrecedesArtifactsAndClientSuppliedStampsAreIgnored() {
        Task base = task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 8);
        PendingTaskInput batch = input(message("next", Role.ROLE_USER, null, null), List.of(
                artifactUpdate("task-1", "context-1", artifact("a1", null, "first"), false, false, 800),
                artifactUpdate("task-1", "context-1", artifact("a2", null, "second"), false, true, 900)),
                configuration(8L), "task-1", "context-1", true, true);

        MutationPlan plan = TaskMutationPlanner.planInput(new TaskSnapshot(base, new TaskRevision(4)), batch,
                MutationOrigin.USER);

        assertEquals(List.of(9L, 10L, 11L), plan.events().stream().map(TaskMutationPlannerTest::generation).toList());
        assertInstanceOf(TaskMessageUpdateEvent.class, plan.events().get(0));
        assertEquals(List.of("a1", "a2"), plan.events().subList(1, 3).stream()
                .map(event -> ((TaskArtifactUpdateEvent) event).artifact().artifactId()).toList());
        assertEquals(11, plan.task().generation());
        assertEquals(9, plan.task().timeline().get(0).generation());
        assertEquals(0, plan.task().timeline().stream().filter(entry -> entry.entry() instanceof TimelineEntry.StatusValue).count());
    }

    @Test
    void generationGuardIsCheckedAgainstPreBatchTaskAndMismatchKeepsItsTypeThroughAuthority() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task base = task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 5);
        store.save(base, false);
        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        PendingTaskInput matching = input(message("next", Role.ROLE_USER, null, null), List.of(
                artifactUpdate("task-1", "context-1", artifact("a1", null, "one"), false, true, 555),
                artifactUpdate("task-1", "context-1", artifact("a2", null, "two"), false, true, 556)),
                configuration(5L), "task-1", "context-1", true, true);

        var committed = authority.mutate("task-1", snapshot ->
                TaskMutationPlanner.planInput(snapshot, matching, MutationOrigin.USER));

        assertEquals(List.of(6L, 7L, 8L), committed.events().stream().map(TaskMutationPlannerTest::generation).toList());
        assertEquals(8, committed.snapshot().task().generation());

        PendingTaskInput stale = input(message("stale", Role.ROLE_USER, null, null), List.of(), configuration(5L),
                "task-1", "context-1", true, true);
        Task before = store.get("task-1");
        TaskGenerationMismatchError failure = assertThrows(TaskGenerationMismatchError.class,
                () -> authority.mutate("task-1", snapshot ->
                        TaskMutationPlanner.planInput(snapshot, stale, MutationOrigin.USER)));
        assertEquals(TaskGenerationMismatchError.class, failure.getClass());
        assertSame(before, store.get("task-1"));
        assertEquals(8, store.get("task-1").generation());
    }

    @Test
    void invalidFinalArtifactRollsBackTheWholeBatchAndPreservesParameterErrorThroughAuthority() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task before = task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 2);
        store.save(before, false);
        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        AtomicBoolean slotReserved = new AtomicBoolean();
        PendingTaskInput invalidBatch = input(message("next", Role.ROLE_USER, null, null), List.of(
                artifactUpdate("task-1", "context-1", artifact("new", null, "valid prefix"), false, false, 200),
                artifactUpdate("task-1", "context-1", artifact("bad", Role.ROLE_AGENT, "wrong origin"), false, true,
                        201)), configuration(2L), "task-1", "context-1", true, true);

        InvalidParamsError failure = assertThrows(InvalidParamsError.class,
                () -> authority.mutate("task-1", snapshot ->
                        TaskMutationPlanner.planInput(snapshot, invalidBatch, MutationOrigin.USER),
                        (slot, committed) -> slotReserved.set(true)));

        assertTrue(failure.getMessage() != null);
        assertFalse(slotReserved.get(), "an invalid batch must not reserve an event publication slot");
        assertSame(before, store.get("task-1"));
        assertEquals(2, store.get("task-1").generation());
        assertTrue(store.get("task-1").artifacts().isEmpty());
        assertTrue(store.get("task-1").timeline().isEmpty());
    }

    @Test
    void typedSlotReservationFailureRemainsACommitFailureThroughAuthority() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task before = task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 2);
        store.save(before, false);
        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        InvalidParamsError reservationFailure = new InvalidParamsError("slot reservation failed");

        TaskMutationNotCommittedException failure = assertThrows(TaskMutationNotCommittedException.class,
                () -> authority.mutate("task-1", snapshot -> TaskMutationPlanner.planStatus(snapshot,
                        statusUpdate("task-1", "context-1", TaskState.TASK_STATE_WORKING, 0)),
                        (slot, committed) -> {
                            throw reservationFailure;
                        }));

        assertSame(reservationFailure, failure.getCause());
        assertSame(before, store.get("task-1"));
        assertEquals(2, store.get("task-1").generation());
    }

    @Test
    void legacyHistoryOccurrenceIsPreservedWhileTimelineRecordsMessageAndStatusSeparately() {
        Message priorStatusMessage = message("status-note", Role.ROLE_AGENT, "task-1", "context-1");
        Task base = Task.builder(task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 4))
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, priorStatusMessage, null))
                .history(List.of(message("old", Role.ROLE_USER, "task-1", "context-1")))
                .build();

        MutationPlan messagePlan = TaskMutationPlanner.planInput(new TaskSnapshot(base, new TaskRevision(1)),
                input(message("new", Role.ROLE_USER, null, null), List.of(), null,
                        "task-1", "context-1", true, true), MutationOrigin.USER);
        assertEquals(List.of("old", "status-note", "new"),
                messagePlan.task().history().stream().map(Message::messageId).toList());
        assertNull(messagePlan.task().status().message());
        assertEquals(5, messagePlan.task().timeline().get(0).generation());

        MutationPlan statusOnlyPlan = TaskMutationPlanner.planStatus(new TaskSnapshot(base, new TaskRevision(3)),
                statusUpdate("task-1", "context-1", TaskState.TASK_STATE_WORKING, 1));
        assertEquals(List.of("old", "status-note"),
                statusOnlyPlan.task().history().stream().map(Message::messageId).toList());
        assertEquals(5, statusOnlyPlan.task().timeline().get(0).generation());
        assertInstanceOf(TimelineEntry.StatusValue.class, statusOnlyPlan.task().timeline().get(0).entry());

        MutationPlan statusPlan = TaskMutationPlanner.planStatus(new TaskSnapshot(messagePlan.task(), new TaskRevision(2)),
                statusUpdate("task-1", "context-1", TaskState.TASK_STATE_WORKING, 3));
        assertEquals(6, statusPlan.task().generation());
        assertEquals(6, statusPlan.task().timeline().get(1).generation());
        assertEquals(2, statusPlan.task().timeline().size());
        assertTrue(statusPlan.task().history().contains(priorStatusMessage));
    }

    @Test
    void sameStateStatusProgressAdvancesOnceAndIgnoresSuppliedGeneration() {
        Task base = task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 3);

        MutationPlan plan = TaskMutationPlanner.planStatus(new TaskSnapshot(base, new TaskRevision(1)),
                statusUpdate("task-1", "context-1", TaskState.TASK_STATE_WORKING, 999));

        assertEquals(4, plan.task().generation());
        assertEquals(4, generation(plan.events().get(0)));
        assertEquals(4, plan.task().timeline().get(0).generation());
    }

    @Test
    void freshTerminalTaskMutationsAreRejectedButInterruptedTaskCanContinue() {
        for (TaskState terminal : List.of(TaskState.TASK_STATE_COMPLETED, TaskState.TASK_STATE_FAILED,
                TaskState.TASK_STATE_CANCELED, TaskState.TASK_STATE_REJECTED, TaskState.TASK_STATE_UNSPECIFIED)) {
            Task base = task("task-1", "context-1", terminal, 7);
            PendingTaskInput input = input(message("next", Role.ROLE_USER, null, null), List.of(), null,
                    "task-1", "context-1", true, true);
            assertThrows(InvalidParamsError.class, () -> TaskMutationPlanner.planInput(
                    new TaskSnapshot(base, new TaskRevision(1)), input, MutationOrigin.USER), terminal.toString());
            assertThrows(InvalidParamsError.class, () -> TaskMutationPlanner.planStatus(
                    new TaskSnapshot(base, new TaskRevision(1)), statusUpdate("task-1", "context-1", terminal, 8)),
                    terminal.toString());
        }

        Task interrupted = task("task-1", "context-1", TaskState.TASK_STATE_INPUT_REQUIRED, 7);
        MutationPlan continuation = TaskMutationPlanner.planInput(new TaskSnapshot(interrupted, new TaskRevision(2)),
                input(message("answer", Role.ROLE_USER, null, null), List.of(), null,
                        "task-1", "context-1", true, true), MutationOrigin.USER);
        assertEquals(8, continuation.task().generation());
    }

    @Test
    void existingTaskAcceptsArtifactOnlyInputButCreationRequiresMessage() {
        PendingTaskInput artifactOnly = input(null, List.of(
                artifactUpdate("task-1", "context-1", artifact("only", null, "value"), false, true, 44)),
                configuration(1L), "task-1", "context-1", true, true);

        MutationPlan existing = TaskMutationPlanner.planInput(
                new TaskSnapshot(task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 1), new TaskRevision(2)),
                artifactOnly, MutationOrigin.USER);
        assertEquals(2, existing.task().generation());
        assertEquals(1, existing.events().size());
        assertInstanceOf(TaskArtifactUpdateEvent.class, existing.events().get(0));
        assertThrows(InvalidParamsError.class,
                () -> TaskMutationPlanner.planInput(null,
                        input(null, artifactOnly.artifactUpdates(), null, "task-1", "context-1", true, true),
                        MutationOrigin.USER));
        PendingTaskInput empty = new PendingTaskInput(null, List.of(), null, null,
                new PendingTaskInput.Admission("task-1", "context-1", "tenant-a", true, true));
        assertThrows(InvalidParamsError.class,
                () -> TaskMutationPlanner.planInput(null, empty, MutationOrigin.USER));
    }

    @Test
    void artifactReplacementAppendLastChunkAndRangesUseAuthoritativeGenerations() {
        Artifact original = Artifact.builder(artifact("doc", Role.ROLE_USER, "old"))
                .metadata(Map.of("base", "original"))
                .startGeneration(1)
                .endGeneration(3)
                .build();
        Task withOriginal = task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 4,
                original);
        Artifact chunk = Artifact.builder(artifact("doc", Role.ROLE_USER, "chunk"))
                .metadata(Map.of("chunk", "update"))
                .build();
        PendingTaskInput appending = input(null, List.of(
                artifactUpdate("task-1", "context-1", chunk, true, false, 4)),
                null, "task-1", "context-1", true, true);
        MutationPlan appendPlan = TaskMutationPlanner.planInput(new TaskSnapshot(withOriginal, new TaskRevision(1)),
                appending, MutationOrigin.USER);
        Artifact appended = appendPlan.task().artifacts().get(0);
        assertEquals(List.of("old", "chunk"), appended.parts().stream().map(part -> ((TextPart) part).text()).toList());
        assertEquals(Role.ROLE_USER, appended.role());
        assertEquals(1, appended.startGeneration());
        assertEquals(5, appended.endGeneration());
        assertEquals(Map.of("base", "original", "chunk", "update"), appended.metadata());
        TaskArtifactUpdateEvent appendEvent = (TaskArtifactUpdateEvent) appendPlan.events().get(0);
        assertEquals(5, generation(appendEvent));
        assertEquals(Boolean.FALSE, appendEvent.lastChunk());
        assertEquals(List.of("chunk"), appendEvent.artifact().parts().stream()
                .map(part -> ((TextPart) part).text()).toList());
        assertEquals(1, appendEvent.artifact().startGeneration());
        assertEquals(5, appendEvent.artifact().endGeneration());
        assertEquals(Map.of("chunk", "update"), appendEvent.artifact().metadata());

        Task reduced = applyCanonicalArtifactEvent(withOriginal, appendEvent);
        Artifact reducedArtifact = reduced.artifacts().get(0);
        assertEquals(List.of("old", "chunk"), reducedArtifact.parts().stream()
                .map(part -> ((TextPart) part).text()).toList());
        assertEquals(1, reducedArtifact.startGeneration());
        assertEquals(5, reducedArtifact.endGeneration());
        assertEquals(Map.of("base", "original", "chunk", "update"), reducedArtifact.metadata());

        PendingTaskInput replacing = input(null, List.of(
                artifactUpdate("task-1", "context-1", artifact("doc", Role.ROLE_USER, "replacement"), false, true, 1)),
                null, "task-1", "context-1", true, true);
        MutationPlan replacePlan = TaskMutationPlanner.planInput(new TaskSnapshot(appendPlan.task(), new TaskRevision(2)),
                replacing, MutationOrigin.USER);
        Artifact replaced = replacePlan.task().artifacts().get(0);
        assertEquals(List.of("replacement"), replaced.parts().stream().map(part -> ((TextPart) part).text()).toList());
        assertEquals(6, replaced.startGeneration());
        assertEquals(6, replaced.endGeneration());
        assertEquals(Boolean.TRUE, ((TaskArtifactUpdateEvent) replacePlan.events().get(0)).lastChunk());
        assertEquals(List.of("replacement"), ((TaskArtifactUpdateEvent) replacePlan.events().get(0)).artifact().parts()
                .stream().map(part -> ((TextPart) part).text()).toList());
    }

    @Test
    void legacyMissingOriginDefaultsToAgentAndCrossOriginEditsAreRejectedBothWays() {
        Task legacyAgentArtifact = task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 2,
                artifact("legacy", null, "agent-owned"));
        PendingTaskInput userEdit = input(null, List.of(
                artifactUpdate("task-1", "context-1", artifact("legacy", null, "user-edit"), false, true, 2)),
                null, "task-1", "context-1", true, true);
        assertThrows(InvalidParamsError.class, () -> TaskMutationPlanner.planInput(
                new TaskSnapshot(legacyAgentArtifact, new TaskRevision(1)), userEdit, MutationOrigin.USER));
        PendingTaskInput agentAppend = input(null, List.of(
                artifactUpdate("task-1", "context-1", artifact("legacy", null, "agent continuation"), true, true, 0)),
                null, "task-1", "context-1", true, false);
        MutationPlan legacyNormalized = TaskMutationPlanner.planInput(
                new TaskSnapshot(legacyAgentArtifact, new TaskRevision(2)), agentAppend, MutationOrigin.AGENT);
        assertEquals(Role.ROLE_AGENT, legacyNormalized.task().artifacts().get(0).role());
        assertEquals(3, legacyNormalized.task().artifacts().get(0).startGeneration());
        assertEquals(3, legacyNormalized.task().artifacts().get(0).endGeneration());

        Task userArtifact = task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 2,
                artifact("user", Role.ROLE_USER, "user-owned"));
        PendingTaskInput agentEdit = input(null, List.of(
                artifactUpdate("task-1", "context-1", artifact("user", null, "agent-edit"), false, true, 2)),
                null, "task-1", "context-1", true, true);
        assertThrows(InvalidParamsError.class, () -> TaskMutationPlanner.planInput(
                new TaskSnapshot(userArtifact, new TaskRevision(1)), agentEdit, MutationOrigin.AGENT));

        PendingTaskInput agentAddsAnother = input(null, List.of(
                artifactUpdate("task-1", "context-1", artifact("agent-new", null, "agent output"), false, true, 0)),
                null, "task-1", "context-1", true, false);
        MutationPlan preserved = TaskMutationPlanner.planInput(new TaskSnapshot(userArtifact, new TaskRevision(2)),
                agentAddsAnother, MutationOrigin.AGENT);
        assertEquals(2, preserved.task().artifacts().size());
        assertEquals(Role.ROLE_USER, preserved.task().artifacts().get(0).role());
        assertEquals(Role.ROLE_AGENT, preserved.task().artifacts().get(1).role());
    }

    @Test
    void userArtifactCapabilityIdentityAndAuthorizationAreAdmissionInputs() {
        Task base = task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 2);
        PendingTaskInput noCapability = input(null, List.of(
                artifactUpdate("task-1", "context-1", artifact("user", null, "value"), false, true, 0)),
                null, "task-1", "context-1", true, false);
        assertThrows(InvalidParamsError.class, () -> TaskMutationPlanner.planInput(
                new TaskSnapshot(base, new TaskRevision(1)), noCapability, MutationOrigin.USER));

        PendingTaskInput wrongContext = input(message("bad", Role.ROLE_USER, null, null), List.of(), null,
                "task-1", "other-context", true, true);
        assertThrows(InvalidParamsError.class, () -> TaskMutationPlanner.planInput(
                new TaskSnapshot(base, new TaskRevision(1)), wrongContext, MutationOrigin.USER));
        InMemoryTaskStore identityStore = new InMemoryTaskStore();
        identityStore.save(base, false);
        TaskMutationAuthority identityAuthority = new TaskMutationAuthority(identityStore);
        assertThrows(InvalidParamsError.class, () -> identityAuthority.mutate("task-1", snapshot ->
                TaskMutationPlanner.planInput(snapshot, wrongContext, MutationOrigin.USER)));
        assertSame(base, identityStore.get("task-1"));

        PendingTaskInput denied = input(message("denied", Role.ROLE_USER, null, null), List.of(), null,
                "task-1", "context-1", false, true);
        assertThrows(TaskNotFoundError.class, () -> TaskMutationPlanner.planInput(
                new TaskSnapshot(base, new TaskRevision(1)), denied, MutationOrigin.USER));

        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(base, false);
        TaskMutationAuthority authority = new TaskMutationAuthority(store);
        assertThrows(TaskNotFoundError.class, () -> authority.mutate("task-1", snapshot ->
                TaskMutationPlanner.planInput(snapshot, denied, MutationOrigin.USER)));
    }

    @Test
    void inputRolesMustMatchAuthenticatedOriginAndOmittedRolesAreNormalized() {
        PendingTaskInput explicitMismatch = input(null, List.of(
                artifactUpdate("task-1", "context-1", artifact("x", Role.ROLE_AGENT, "x"), false, true, 700)),
                null, "task-1", "context-1", true, true);
        assertThrows(InvalidParamsError.class, () -> TaskMutationPlanner.planInput(
                new TaskSnapshot(task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 0), new TaskRevision(1)),
                explicitMismatch, MutationOrigin.USER));

        PendingTaskInput agent = input(null, List.of(
                artifactUpdate("task-1", "context-1", artifact("agent", null, "agent"), false, true, 700)),
                null, "task-1", "context-1", true, false);
        MutationPlan normalized = TaskMutationPlanner.planInput(
                new TaskSnapshot(task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 0), new TaskRevision(2)),
                agent, MutationOrigin.AGENT);
        assertEquals(Role.ROLE_AGENT, normalized.task().artifacts().get(0).role());
        assertEquals(1, normalized.task().artifacts().get(0).startGeneration());
        assertEquals(1, normalized.task().artifacts().get(0).endGeneration());

        PendingTaskInput mismatchedAgentRole = input(null, List.of(
                artifactUpdate("task-1", "context-1", artifact("wrong", Role.ROLE_USER, "user claim"), false, true, 1)),
                null, "task-1", "context-1", true, false);
        assertThrows(InvalidParamsError.class, () -> TaskMutationPlanner.planInput(
                new TaskSnapshot(task("task-1", "context-1", TaskState.TASK_STATE_WORKING, 0), new TaskRevision(3)),
                mismatchedAgentRole, MutationOrigin.AGENT));
    }

    private static PendingTaskInput input(@Nullable Message message, List<TaskArtifactUpdateEvent> updates,
            MessageSendConfiguration configuration, String taskId, String contextId,
            boolean authorized, boolean userArtifactsEnabled) {
        return new PendingTaskInput(message, updates, configuration, Map.of("source", "test"),
                new PendingTaskInput.Admission(taskId, contextId, "tenant-a", authorized, userArtifactsEnabled));
    }

    private static Message message(String id, Role role, String taskId, String contextId) {
        return Message.builder().messageId(id).role(role).parts(List.of(new TextPart(id)))
                .taskId(taskId).contextId(contextId).build();
    }

    private static Artifact artifact(String id, Role role, String value) {
        return Artifact.builder().artifactId(id).parts(List.of(new TextPart(value))).role(role).build();
    }

    private static TaskArtifactUpdateEvent artifactUpdate(String taskId, String contextId, Artifact artifact,
            boolean append, boolean lastChunk, long generation) {
        return TaskArtifactUpdateEvent.builder().taskId(taskId).contextId(contextId).artifact(artifact)
                .append(append).lastChunk(lastChunk).generation(generation).build();
    }

    /** Applies a canonical event with the existing part/metadata merge and the 1.1 generation-range rule. */
    private static Task applyCanonicalArtifactEvent(Task task, TaskArtifactUpdateEvent event) {
        Task merged = Utils.appendArtifactToTask(task, event, task.id());
        List<Artifact> artifacts = new java.util.ArrayList<>(merged.artifacts());
        for (int i = 0; i < artifacts.size(); i++) {
            Artifact artifact = artifacts.get(i);
            if (artifact.artifactId().equals(event.artifact().artifactId())) {
                artifacts.set(i, Artifact.builder(artifact)
                        .startGeneration(event.artifact().startGeneration())
                        .endGeneration(event.artifact().endGeneration())
                        .role(event.artifact().role())
                        .build());
                break;
            }
        }
        return Task.builder(merged).artifacts(artifacts).build();
    }

    private static MessageSendConfiguration configuration(Long guard) {
        return MessageSendConfiguration.builder().ifGenerationMatch(guard).build();
    }

    private static TaskStatusUpdateEvent statusUpdate(String taskId, String contextId, TaskState state,
            long generation) {
        return TaskStatusUpdateEvent.builder().taskId(taskId).contextId(contextId)
                .status(new TaskStatus(state)).generation(generation).build();
    }

    private static Task task(String taskId, String contextId, TaskState state, long generation, Artifact... artifacts) {
        return Task.builder().id(taskId).contextId(contextId).status(new TaskStatus(state))
                .artifacts(List.of(artifacts)).history(List.of()).timeline(List.of()).generation(generation).build();
    }

    private static long generation(Object event) {
        if (event instanceof TaskMessageUpdateEvent message) {
            return message.generation();
        }
        if (event instanceof TaskArtifactUpdateEvent artifact) {
            return artifact.generation();
        }
        return ((TaskStatusUpdateEvent) event).generation();
    }
}
