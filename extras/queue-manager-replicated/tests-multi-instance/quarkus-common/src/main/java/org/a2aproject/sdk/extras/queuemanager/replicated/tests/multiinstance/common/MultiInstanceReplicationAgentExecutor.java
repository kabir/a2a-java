package org.a2aproject.sdk.extras.queuemanager.replicated.tests.multiinstance.common;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Shared test agent executor for multi-instance replication tests.
 *
 * Behavior:
 * 1. Creates task in SUBMITTED state on first message
 * 2. Adds messages as artifacts on subsequent messages
 * 3. Completes task when message contains "close"
 * 4. Phase 3: a message containing {@code hold:<name>} first waits at the {@code executor} barrier, so a test can
 *    keep an execution in flight while the storage authority changes underneath it
 */
public class MultiInstanceReplicationAgentExecutor implements AgentExecutor {
    private static final String HOLD_PREFIX = "hold:";

    @Override
    public void execute(RequestContext context, AgentEmitter agentEmitter) throws A2AError {
        Task task = context.getTask();

        String text = context.getMessage().parts().stream()
                .filter(part -> part instanceof TextPart tp && tp.text() != null)
                .map(part -> ((TextPart) part).text())
                .reduce("", (a, b) -> a + " " + b);

        // Check if message contains "close" signal
        boolean shouldClose = text.toLowerCase().contains("close");

        int hold = text.indexOf(HOLD_PREFIX);
        if (hold >= 0) {
            String name = text.substring(hold + HOLD_PREFIX.length()).trim().split("\\s+")[0];
            Phase3Instrumentation.trace("executor-hold", name);
            Phase3Instrumentation.pass(Phase3Instrumentation.EXECUTOR, name);
            Phase3Instrumentation.trace("executor-emit", name);
        }

        if (shouldClose) {
            // Close the task
            agentEmitter.complete();
        } else if (task == null) {
            // First message - create task in SUBMITTED state
            agentEmitter.submit();
        } else {
            // Subsequent messages - add as artifact
            agentEmitter.addArtifact(context.getMessage().parts());
        }
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter agentEmitter) throws A2AError {
        agentEmitter.cancel();
    }
}
