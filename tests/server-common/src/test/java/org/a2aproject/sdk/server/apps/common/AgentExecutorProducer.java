package org.a2aproject.sdk.server.apps.common;

import static org.a2aproject.sdk.server.ServerCallContext.TRANSPORT_KEY;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.ProposalResult;
import org.a2aproject.sdk.server.tasks.TaskProposal;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import io.quarkus.arc.profile.IfBuildProfile;

@ApplicationScoped
@IfBuildProfile("test")
public class AgentExecutorProducer {

    // Inject the existing AgentCard to avoid special handling for grpc
    /** Task ids the controlled phase3 branches were invoked for, in arrival order. */
    public static final CopyOnWriteArrayList<String> PHASE3_TASK_IDS =
            new CopyOnWriteArrayList<>();

    /**
     * Released by the test that holds a {@code phase3:hold} agent open. The default is already released, so a stray
     * hold request never blocks; a holding test installs its own latch first and counts it down when it finishes.
     */
    public static final AtomicReference<CountDownLatch> PHASE3_HOLD =
            new AtomicReference<>(new CountDownLatch(0));

    /** Id of the agent message carried by the status that {@code phase3:metadata-proposal} leaves on its task. */
    public static final String PHASE3_STATUS_MESSAGE_ID = "p3-status-message";

    /**
     * Installed by a test that drives {@code phase3:metadata-proposal}. The agent first emits a WORKING status that
     * carries the agent message {@link #PHASE3_STATUS_MESSAGE_ID} and waits until that status is committed. It then
     * records the committed snapshot, signals {@link MetadataProposalProbe#entered}, waits for
     * {@link MetadataProposalProbe#release}, and submits a full Task built from that snapshot that keeps the status and
     * changes only the metadata. The correlated result is reported through {@link MetadataProposalProbe#result}.
     * The legacy {@code addTask} cannot do this: it always proposes against the Task captured when the invocation
     * started, and every ordinary input moves a status message into the history and clears it, so an input-triggered
     * invocation never starts from a message-bearing status.
     */
    public static final AtomicReference<MetadataProposalProbe> PHASE3_METADATA_PROBE = new AtomicReference<>();

    /** Barriers and observations of one {@code phase3:metadata-proposal} invocation. */
    public static final class MetadataProposalProbe {
        public final Map<String, Object> metadata;
        public final CountDownLatch entered = new CountDownLatch(1);
        public final CountDownLatch release = new CountDownLatch(1);
        public final AtomicReference<Task> invocationTask = new AtomicReference<>();
        public final CompletableFuture<ProposalResult> result = new CompletableFuture<>();

        public MetadataProposalProbe(Map<String, Object> metadata) {
            this.metadata = Map.copyOf(metadata);
        }
    }

    @Inject
    @PublicAgentCard
    AgentCard agentCard;

    @Inject
    RequestScopedBean requestScopedBean;

    @Produces
    public AgentExecutor agentExecutor() {
        return new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter agentEmitter) throws A2AError {
                String taskId = context.getTaskId();
                String input = context.getMessage() != null ? extractTextFromMessage(context.getMessage()) : "";

                if (input.startsWith("tck-message-response")) {
                    agentEmitter.sendMessage("tck message response");
                    return;
                }

                // Agent-to-agent communication test (routed by message content prefix)
                if (input.startsWith("delegate:") || input.startsWith("a2a-local:")) {
                    handleAgentToAgentTest(context, agentEmitter);
                    return;
                }

                // Request-scoped bean test: verify CDI request context propagation
                if (input.startsWith("request-scoped:")) {
                    agentEmitter.startWork();
                    String value = requestScopedBean.getValue();
                    agentEmitter.addArtifact(List.of(new TextPart("request-scoped:" + value)));
                    agentEmitter.complete();
                    return;
                }

                // P3-T17 controlled branches, routed by message content prefix.
                if (input.startsWith("phase3:")) {
                    handlePhase3(context, agentEmitter, input);
                    return;
                }

                // Create and finish a task for the finalized queue lifecycle test.
                if (input.startsWith("queue-finalization:")) {
                    agentEmitter.startWork();
                    agentEmitter.complete();
                    return;
                }

                // Special handling for multi-event test (routed by message content)
                if (input.startsWith("multi-event:first")) {
                    agentEmitter.startWork();
                    // Return immediately - queue stays open because task is in WORKING state
                    return;
                }
                if (input.startsWith("multi-event:second")) {
                    agentEmitter.addArtifact(
                        List.of(new TextPart("Second message artifact")),
                        "artifact-2", "Second Artifact", null);
                    agentEmitter.complete();
                    return;
                }

                // Special handling for input-required test (routed by message content)
                if (input.startsWith("input-required:")) {
                    String payload = input.substring("input-required:".length());
                    // Second call: user provided the required input - complete the task
                    if ("User input".equals(payload)) {
                        agentEmitter.complete();
                        return;
                    }
                    // First call: emit INPUT_REQUIRED
                    agentEmitter.requiresInput(agentEmitter.newAgentMessage(
                            List.of(new TextPart("Please provide additional information")),
                            context.getMessage().metadata()));
                    return;
                }

                if (input.startsWith("history-workflow:")) {
                    agentEmitter.requiresInput(agentEmitter.newAgentMessage(
                            List.of(new TextPart("Please provide the next history message")),
                            context.getMessage().metadata()));
                    return;
                }

                // Special handling for auth-required test (routed by message content)
                if (input.startsWith("auth-required:")) {
                    agentEmitter.requiresAuth(agentEmitter.newAgentMessage(
                            List.of(new TextPart("Please authenticate with OAuth provider")),
                            context.getMessage().metadata()));

                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InternalError("Auth simulation interrupted: " + e.getMessage());
                    }

                    agentEmitter.complete();
                    return;
                }

                if ("task-not-supported-123".equals(taskId)) {
                    throw new UnsupportedOperationError();
                }

                // Check for delegated agent-to-agent messages (marked with special prefix)
                if (input.startsWith("#a2a-delegated#")) {
                    String actualContent = input.substring("#a2a-delegated#".length());
                    agentEmitter.startWork();
                    String response = "Handled locally: " + actualContent;
                    agentEmitter.addArtifact(List.of(new TextPart(response)));
                    agentEmitter.complete();
                    return;
                }

                // Default handler: echo back message or task
                if (context.getMessage() != null) {
                    agentEmitter.sendMessage(context.getMessage());
                } else {
                    agentEmitter.addTask(context.getTask());
                }
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter agentEmitter) throws A2AError {
                if (context.getTask().id().equals("cancel-task-123")) {
                    agentEmitter.cancel();
                } else if (context.getTask().id().equals("cancel-task-not-supported-123")) {
                    throw new UnsupportedOperationError();
                }
            }

            /** Controlled P3-T17 behaviours; input appended to an existing task by the core never reaches this. */
            private void handlePhase3(RequestContext context, AgentEmitter agentEmitter, String input) {
                PHASE3_TASK_IDS.add(String.valueOf(context.getTaskId()));
                switch (input) {
                    case "phase3:work" -> agentEmitter.startWork();
                    case "phase3:noop" -> { }
                    case "phase3:hold" -> {
                        // Streams an artifact, then keeps the send stream open until the test releases it.
                        agentEmitter.startWork();
                        agentEmitter.addArtifact(List.of(new TextPart("held")), "p3-hold-art", "held", null);
                        try {
                            PHASE3_HOLD.get().await(60, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    case "phase3:direct" -> agentEmitter.sendMessage("phase3 direct reply");
                    case "phase3:metadata-proposal" -> proposeMetadataOnly(agentEmitter);
                    case "phase3:agent-artifact" -> {
                        agentEmitter.startWork();
                        agentEmitter.addArtifact(List.of(new TextPart("agent")), "p3-agent-art", "agent", null);
                    }
                    case "phase3:artifacts-complete" -> {
                        agentEmitter.startWork();
                        agentEmitter.addArtifact(List.of(new TextPart("first")), "p3-a1", "first", null);
                        agentEmitter.addArtifact(List.of(new TextPart("second")), "p3-a2", "second", null);
                        agentEmitter.complete();
                    }
                    case "phase3:terminal-proposal" -> agentEmitter.addTask(Task.builder()
                            .id(context.getTaskId())
                            .contextId(context.getContextId())
                            .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                            .artifacts(List.of(Artifact.builder().artifactId("p3-final")
                                    .parts(List.of(new TextPart("final artifact"))).build()))
                            .build());
                    default -> throw new InvalidParamsError("Unknown phase3 branch: " + input);
                }
            }

            /**
             * Leaves the task with a WORKING status carrying a known agent message, then submits a full Task built from
             * the committed snapshot that keeps that status and changes only the metadata.
             */
            private void proposeMetadataOnly(AgentEmitter agentEmitter) {
                MetadataProposalProbe probe = PHASE3_METADATA_PROBE.get();
                if (probe == null) {
                    throw new InvalidParamsError("phase3:metadata-proposal requires an installed probe");
                }
                agentEmitter.startWork(agentEmitter.messageBuilder().messageId(PHASE3_STATUS_MESSAGE_ID)
                        .parts(List.of(new TextPart("phase3 status message"))).build());
                try {
                    TaskSnapshot snapshot = agentEmitter.refreshTask();
                    for (int i = 0; i < 400 && !hasStatusMessage(snapshot); i++) {
                        Thread.sleep(50);
                        snapshot = agentEmitter.refreshTask();
                    }
                    if (!hasStatusMessage(snapshot)) {
                        throw new InternalError("the message-bearing status was never committed");
                    }
                    probe.invocationTask.set(snapshot.task());
                    probe.entered.countDown();
                    if (!probe.release.await(60, TimeUnit.SECONDS)) {
                        throw new InternalError("phase3:metadata-proposal was never released");
                    }
                    Task proposed = Task.builder(snapshot.task()).metadata(probe.metadata).build();
                    agentEmitter.submitTask(TaskProposal.create(snapshot, proposed)).whenComplete((result, failure) -> {
                        if (failure != null) {
                            probe.result.completeExceptionally(failure);
                        } else {
                            probe.result.complete(result);
                        }
                    });
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new InternalError("phase3:metadata-proposal interrupted");
                }
            }

            private boolean hasStatusMessage(TaskSnapshot snapshot) {
                Message statusMessage = snapshot.task().status().message();
                return statusMessage != null && PHASE3_STATUS_MESSAGE_ID.equals(statusMessage.messageId());
            }

            /**
             * Handles agent-to-agent communication testing.
             * Detects "delegate:" prefix and forwards requests to another agent via client.
             */
            private void handleAgentToAgentTest(RequestContext context, AgentEmitter agentEmitter) throws A2AError {
                try {
                    // Get transport protocol from ServerCallContext
                    ServerCallContext callContext = context.getCallContext();
                    if (callContext == null) {
                        agentEmitter.fail(new InternalError("No call context available for agent-to-agent test"));
                        return;
                    }

                    TransportProtocol transportProtocol = (TransportProtocol) callContext.getState().get(TRANSPORT_KEY);
                    if (transportProtocol == null) {
                        agentEmitter.fail(new InternalError("Transport type not set in call context"));
                        return;
                    }

                    // Extract user message
                    String userInput = context.getUserInput("\n");
                    if (userInput == null || userInput.isEmpty()) {
                        agentEmitter.fail(new InternalError("No user input received"));
                        return;
                    }

                    // Check for delegation pattern
                    if (userInput.startsWith("delegate:")) {
                        handleDelegation(userInput, transportProtocol, agentEmitter);
                    } else if (userInput.startsWith("a2a-local:")) {
                        handleLocally(userInput.substring("a2a-local:".length()), agentEmitter);
                    } else {
                        handleLocally(userInput, agentEmitter);
                    }
                } catch (Exception e) {
                    // Log the full stack trace to help debug intermittent failures
                    e.printStackTrace();
                    agentEmitter.fail(new InternalError("Agent-to-agent test failed: " + e.getMessage()));
                }
            }

            /**
             * Handles delegation by forwarding to another agent via client.
             * <p>
             * Uses blocking client call (streaming=false) which should return the final task state
             * synchronously without requiring async callbacks and latches. This simplified approach
             * avoids race conditions between event consumption and callback invocation.
             */
            private void handleDelegation(String userInput, TransportProtocol transportProtocol,
                                          AgentEmitter agentEmitter) {
                // Strip "delegate:" prefix
                String delegatedContent = userInput.substring("delegate:".length()).trim();

                // Create client for same transport (streaming=false for blocking behavior)
                try (Client client = AgentToAgentClientFactory.createClient(agentCard, transportProtocol)) {
                    agentEmitter.startWork();

                    // Store the result task from blocking call
                    AtomicReference<Task> taskRef = new AtomicReference<>();

                    // Delegate to another agent (new task on same server)
                    // Add a marker so the receiving agent knows to complete the task
                    Message delegatedMessage = A2A.toUserMessage("#a2a-delegated#" + delegatedContent);

                    // Blocking call should return final task synchronously
                    client.sendMessage(delegatedMessage, List.of((event, card) -> {
                        if (event instanceof TaskEvent te) {
                            taskRef.set(te.getTask());
                        } else if (event instanceof TaskUpdateEvent tue) {
                            taskRef.set(tue.getTask());
                        }
                    }), null);

                    // Blocking call should have completed before returning
                    Task delegatedResult = taskRef.get();

                    if (delegatedResult == null) {
                        agentEmitter.fail(new InternalError("No result received from blocking delegation call"));
                        return;
                    }

                    // DIAGNOSTIC: Check if task is actually final
                    // If blocking call returns non-final task, it indicates a server-side race condition
                    if (!delegatedResult.status().state().isFinal()) {
                        String diagnostic = String.format(
                            "RACE CONDITION DETECTED: Blocking call returned non-final task! " +
                            "State: %s, TaskId: %s, Artifacts: %d. " +
                            "This indicates DefaultRequestHandler wait logic failed to synchronize with MainEventBusProcessor.",
                            delegatedResult.status().state(),
                            delegatedResult.id(),
                            delegatedResult.artifacts() != null ? delegatedResult.artifacts().size() : 0);
                        System.err.println(diagnostic);  // Also print to stderr for CI visibility
                        agentEmitter.fail(new InternalError(diagnostic));
                        return;
                    }

                    // Extract artifacts from delegated task and add to current task
                    // NOTE: We cannot use emitter.addTask(delegatedResult) because it has a different taskId
                    if (delegatedResult.artifacts() != null && !delegatedResult.artifacts().isEmpty()) {
                        for (Artifact artifact : delegatedResult.artifacts()) {
                            agentEmitter.addArtifact(artifact.parts());
                        }
                    }

                    // Complete current task
                    agentEmitter.complete();
                } catch (A2AClientException e) {
                    agentEmitter.fail(new InternalError("Failed to create client: " + e.getMessage()));
                }
            }

            /**
             * Handles request locally without delegation.
             */
            private void handleLocally(String userInput, AgentEmitter agentEmitter) {
                try {
                    agentEmitter.startWork();
                    String response = "Handled locally: " + userInput;
                    agentEmitter.addArtifact(List.of(new TextPart(response)));
                    agentEmitter.complete();
                } catch (Exception e) {
                    // Defensive catch to ensure we always emit a final state
                    e.printStackTrace();
                    agentEmitter.fail(new InternalError("Local handling failed: " + e.getMessage()));
                }
            }
        };
    }

    /**
     * Extract the content of TextPart in a message to create a single String.
     * @param message the message containing the TextPart.
     * @return a String aggreagating all the TextPart contents of the message.
     */
    private String extractTextFromMessage(final Message message) {
        final StringBuilder textBuilder = new StringBuilder();
        if (message.parts() != null) {
            for (final Part part : message.parts()) {
                if (part instanceof TextPart textPart) {
                    textBuilder.append(textPart.text());
                }
            }
        }
        return textBuilder.toString();
    }
}
