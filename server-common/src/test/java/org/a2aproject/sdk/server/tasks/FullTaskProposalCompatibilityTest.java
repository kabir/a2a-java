package org.a2aproject.sdk.server.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.Test;

class FullTaskProposalCompatibilityTest {

    private static final String TASK_ID = "proposal-task";
    private static final String CONTEXT_ID = "proposal-context";

    @Test
    void matchingBaseNoOpPreservesAcceptedHistoryTimelineAndUserArtifactsWithoutMutation() throws Exception {
        InMemoryTaskStore store = storeWithTask();
        TaskSnapshot base = requireSnapshot(store);
        Task template = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(base.task().status()).build();

        MutationPlan plan = TaskMutationPlanner.planTaskProposal(base, template);
        assertFalse(plan.changed());
        assertTrue(plan.events().isEmpty());
        assertEquals(base.task(), plan.task());
        assertEquals(base.task().history(), plan.task().history());
        assertEquals(base.task().timeline(), plan.task().timeline());
        assertEquals(base.task().artifacts(), plan.task().artifacts());
    }

    @Test
    void statusOnlyProposalPlansOneCanonicalEventFromTheAuthoritativeTask() throws Exception {
        InMemoryTaskStore store = storeWithTask();
        TaskSnapshot base = requireSnapshot(store);
        Task proposed = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build();

        MutationPlan plan = TaskMutationPlanner.planTaskProposal(base, proposed);
        assertTrue(plan.changed());
        assertEquals(1, plan.events().size());
        TaskStatusUpdateEvent status = assertInstanceOf(TaskStatusUpdateEvent.class, plan.events().get(0));
        assertEquals(4, status.generation());
        assertEquals(4, plan.task().generation());
        assertEquals(base.task().history(), plan.task().history());
        assertEquals(base.task().timeline().size() + 1, plan.task().timeline().size());
        assertEquals(base.task().artifacts(), plan.task().artifacts());
    }

    @Test
    void matchingCurrentStatusIsAPlannerNoOp() throws Exception {
        InMemoryTaskStore store = storeWithTask();
        TaskSnapshot capturedBase = requireSnapshot(store);
        store.mutate(TASK_ID, current -> TaskMutationPlanner.planStatus(current,
                TaskStatusUpdateEvent.builder().taskId(TASK_ID).contextId(CONTEXT_ID)
                        .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build()));
        TaskSnapshot current = requireSnapshot(store);
        Task proposed = Task.builder(current.task()).build();

        assertTrue(current.revision().value() > capturedBase.revision().value());
        MutationPlan plan = TaskMutationPlanner.planTaskProposal(current, proposed);
        assertFalse(plan.changed());
        assertTrue(plan.events().isEmpty());
        assertEquals(current.task(), plan.task());
    }

    @Test
    void metadataAndAgentArtifactChangesNormalizeButUserArtifactEditsRemainRejected() throws Exception {
        InMemoryTaskStore store = storeWithTask();
        TaskSnapshot base = requireSnapshot(store);
        Task changedMetadata = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(base.task().status()).metadata(Map.of("different", true)).build();

        MutationPlan metadataPlan = TaskMutationPlanner.planTaskProposal(base, changedMetadata);
        assertTrue(metadataPlan.changed());
        assertEquals(1, metadataPlan.events().size());
        assertEquals(4, assertInstanceOf(TaskStatusUpdateEvent.class, metadataPlan.events().get(0)).generation());
        assertEquals(Map.of("stored", true, "different", true), metadataPlan.task().metadata());

        Artifact changedUserArtifact = Artifact.builder(base.task().artifacts().get(0))
                .parts(List.of(new TextPart("changed user artifact"))).build();
        Task changedArtifact = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(base.task().status()).artifacts(List.of(changedUserArtifact)).build();
        assertThrows(InvalidParamsError.class, () -> TaskMutationPlanner.planTaskProposal(base, changedArtifact));
    }

    @Test
    void changedStatusOnTerminalTaskIsRejectedWithoutChangingItsSnapshot() throws Exception {
        InMemoryTaskStore store = storeWithTask();
        Task initial = requireSnapshot(store).task();
        Task terminal = Task.builder(initial).status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();
        store.save(terminal, false);
        TaskSnapshot base = requireSnapshot(store);
        Task proposed = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_CANCELED)).build();

        assertThrows(InvalidParamsError.class, () -> TaskMutationPlanner.planTaskProposal(base, proposed));

        MutationPlan terminalNoOp = TaskMutationPlanner.planTaskProposal(base, terminal);
        assertFalse(terminalNoOp.changed());
        assertTrue(terminalNoOp.events().isEmpty());
        assertEquals(base.task(), terminalNoOp.task());
    }

    private static InMemoryTaskStore storeWithTask() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Message accepted = Message.builder().messageId("accepted").taskId(TASK_ID).contextId(CONTEXT_ID)
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("accepted input"))).build();
        Artifact userArtifact = Artifact.builder().artifactId("user-input")
                .parts(List.of(new TextPart("user artifact"))).role(Message.Role.ROLE_USER)
                .startGeneration(2).endGeneration(3).build();
        Task task = Task.builder().id(TASK_ID).contextId(CONTEXT_ID)
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                .history(List.of(accepted)).timeline(List.of(new TimelineEntry(3,
                        new TimelineEntry.MessageValue(accepted))))
                .artifacts(List.of(userArtifact)).metadata(Map.of("stored", true)).generation(3).build();
        store.save(task, false);
        return store;
    }

    private static TaskSnapshot requireSnapshot(InMemoryTaskStore store) {
        return Objects.requireNonNull(store.readSnapshot(TASK_ID));
    }
}
