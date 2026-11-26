package org.a2aproject.sdk.server.apps.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

/**
 * Reusable P3-T17 scenarios for a server whose <em>effective</em> agent card does not advertise client-written
 * artifacts, driven by a public client whose own card claims the capability, so the server-side check is what rejects.
 */
public abstract class Phase3CapabilityScenarios extends Phase3ScenarioSupport {

    private static final String DISABLED = "User artifacts are not enabled";

    @Test
    public void theEffectiveServerCardDoesNotAdvertiseUserArtifacts() throws Exception {
        assertFalse(open("1.1", false, false).getExtendedAgentCard().capabilities().userArtifacts(),
                "the profile under test serves a card without the capability");
    }

    @Test
    public void unaryClientArtifactsAreRejectedByTheServerCapabilityCheckWithoutAnyMutation() throws Exception {
        clientArtifactsAreRejected(false);
    }

    @Test
    public void streamingClientArtifactsAreRejectedByTheServerCapabilityCheckWithoutAnyMutation() throws Exception {
        clientArtifactsAreRejected(true);
    }

    private void clientArtifactsAreRejected(boolean streaming) throws Exception {
        Task base = createWorkingTask();
        Task before = storedTask(base.id());
        assertNotNull(before);
        Client client = open("1.1", streaming, false);

        List<SendInputParams> rejected = List.of(
                SendInputParams.builder().message(userMessage(base, "p3-cap-both", "phase3:noop"))
                        .artifactUpdates(List.of(artifactUpdate(base, "p3-cap-1"), artifactUpdate(base, "p3-cap-2")))
                        .build(),
                SendInputParams.builder().artifactUpdates(List.of(artifactUpdate(base, "p3-cap-only"))).build(),
                SendInputParams.builder().artifactUpdates(List.of(artifactUpdate(base, "p3-cap-guard")))
                        .configuration(MessageSendConfiguration.builder().ifGenerationMatch(base.generation()).build())
                        .build());
        for (SendInputParams params : rejected) {
            Outcome outcome = observe(client, params);
            assertTypedFailure(outcome.failure(), InvalidParamsError.class, DISABLED);
            assertUntouched(before, storedTask(base.id()), "capability rejection");
        }

        // control: the capability, not the transport or the task, is what is refused
        Outcome plain = observe(client, input(base, "p3-cap-plain", "phase3:noop", null));
        assertNull(plain.failure(), () -> String.valueOf(plain.failure()));
        Task after = storedTask(base.id());
        assertNotNull(after);
        assertTrue(after.generation() > before.generation());
        assertTrue(after.artifacts().stream().noneMatch(a -> a.artifactId().startsWith("p3-cap")));
    }

    @Test
    public void anOpeningMessageWithClientArtifactsCreatesNoTaskWhenTheCapabilityIsOff() throws Exception {
        Client client = open("1.1", false, false);
        int executions = Phase3MutationScenarios.Phase3Executions.count();
        String messageId = "p3-cap-open-" + System.nanoTime();
        int tasksBefore = client.listTasks(ListTasksParams.builder().pageSize(1).build()).totalSize();

        // An opening message carries neither a task ID nor a context ID, so provisional artifact IDs are legal.
        Outcome outcome = observe(client, SendInputParams.builder()
                .message(Message.builder().messageId(messageId)
                        .role(Message.Role.ROLE_USER)
                        .parts(List.of(new TextPart("phase3:work"))).build())
                .artifactUpdates(List.of(new TaskArtifactUpdateEvent("",
                        Artifact.builder().artifactId("p3-cap-open")
                                .parts(List.of(new TextPart("a"))).build(), "", false, true,
                        null, 0))).build());

        assertTypedFailure(outcome.failure(), InvalidParamsError.class, DISABLED);
        assertEquals(tasksBefore, client.listTasks(ListTasksParams.builder().pageSize(1).build()).totalSize(),
                "no task was created");
        // The opening input is admitted atomically with the agent's first task event, so the controlled agent may
        // already have started; what the capability guarantees is that nothing it produced was persisted.
        for (int i = executions; i < Phase3MutationScenarios.Phase3Executions.count(); i++) {
            String started = Phase3MutationScenarios.Phase3Executions.taskIdAt(i);
            assertNull(storedTask(started), "no task was persisted for the rejected opening input");
        }
    }
}
