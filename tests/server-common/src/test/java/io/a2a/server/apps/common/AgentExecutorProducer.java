package io.a2a.server.apps.common;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.TextPart;
import io.a2a.spec.UnsupportedOperationError;
import io.quarkus.arc.profile.IfBuildProfile;

@ApplicationScoped
@IfBuildProfile("test")
public class AgentExecutorProducer {

    @Produces
    public AgentExecutor agentExecutor() {
        return new AgentExecutor() {
            @Override
            public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
                if (context.getTaskId().equals("task-not-supported-123")) {
                    eventQueue.enqueueEvent(new UnsupportedOperationError());
                } else if (context.getTaskId().startsWith("resubscribe-nonstreaming-test-")) {
                    // Special handling for resubscription test - all messages use streaming client
                    TaskUpdater updater = new TaskUpdater(context, eventQueue);
                    if (context.getTask() == null) {
                        // First message - ensure task is created
                        updater.submit();
                    }
                    updater.addArtifact(List.of(new TextPart("response", null)), null, null, null);
                } else {
                    eventQueue.enqueueEvent(context.getMessage() != null ? context.getMessage() : context.getTask());
                }
            }

            @Override
            public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
                if (context.getTask().getId().equals("cancel-task-123")) {
                    TaskUpdater taskUpdater = new TaskUpdater(context, eventQueue);
                    taskUpdater.cancel();
                } else if (context.getTask().getId().equals("cancel-task-not-supported-123")) {
                    throw new UnsupportedOperationError();
                }
            }
        };
    }
}
