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
                } else if (context.getTaskId() != null && context.getTaskId().startsWith("resubscribe-nonstreaming-test-")) {
                    // Special handling for resubscription test
                    // Enqueue 3 artifacts with delays to simulate streaming behavior
                    TaskUpdater updater = new TaskUpdater(context, eventQueue);
                    if (context.getTask() == null) {
                        updater.submit();
                    }
                    updater.startWork();

                    for (int i = 1; i <= 3; i++) {
                        try {
                            Thread.sleep(100); // Small delay between artifacts
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        updater.addArtifact(List.of(new TextPart("artifact-" + i, null)), null, null, null);
                    }
                    updater.complete();
                } else {
                    // Default: enqueue the message if present, otherwise use TaskUpdater to ensure proper final event
                    if (context.getMessage() != null) {
                        eventQueue.enqueueEvent(context.getMessage());
                    } else {
                        // Use TaskUpdater to ensure final events are properly enqueued
                        TaskUpdater updater = new TaskUpdater(context, eventQueue);
                        if (context.getTask() == null) {
                            updater.submit();
                        }
                        updater.complete();
                    }
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
