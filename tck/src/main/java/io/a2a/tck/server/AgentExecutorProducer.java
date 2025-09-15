package io.a2a.tck.server;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Task;
import io.a2a.spec.TaskNotCancelableError;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TaskStatusUpdateEvent;

@ApplicationScoped
public class AgentExecutorProducer {

    @Produces
    public AgentExecutor agentExecutor() {
        return new FireAndForgetAgentExecutor();
    }
    
    private static class FireAndForgetAgentExecutor implements AgentExecutor {
        private static final String HANDLED_SUFFIX = "-handled";
        private static final String DONE_SUFFIX = "-done";

        @Override
        public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
            Task task = context.getTask();

            if (task == null) {
                task = new Task.Builder()
                        .id(context.getTaskId())
                        .contextId(context.getContextId())
                        .status(new TaskStatus(TaskState.SUBMITTED))
                        .history(context.getMessage())
                        .build();
                eventQueue.enqueueEvent(task);
            }

            String messageId = context.getMessage().getMessageId();
            if (messageId.startsWith("test-resubscribe-message-id")) {
                waitForAsyncCaller(messageId);
            }
            TaskUpdater updater = new TaskUpdater(context, eventQueue);

            // Immediately set to WORKING state
            updater.startWork();
            System.out.println("====> task set to WORKING, starting background execution");
            
            // Method returns immediately - task continues in background
            System.out.println("====> execute() method returning immediately, task running in background");
        }

        @Override
        public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
            System.out.println("====> task cancel request received");
            Task task = context.getTask();

            if (task.getStatus().state() == TaskState.CANCELED) {
                System.out.println("====> task already canceled");
                throw new TaskNotCancelableError();
            }
            
            if (task.getStatus().state() == TaskState.COMPLETED) {
                System.out.println("====> task already completed");
                throw new TaskNotCancelableError();
            }

            TaskUpdater updater = new TaskUpdater(context, eventQueue);
            updater.cancel();
            eventQueue.enqueueEvent(new TaskStatusUpdateEvent.Builder()
                    .taskId(task.getId())
                    .contextId(task.getContextId())
                    .status(new TaskStatus(TaskState.CANCELED))
                    .isFinal(true)
                    .build());
            
            System.out.println("====> task canceled");
        }

        /**
         * Cleanup method for proper resource management
         */
        @PreDestroy
        public void cleanup() {
            System.out.println("====> shutting down task executor");
         }

        private void waitForAsyncCaller(String messageId) {
            String asyncFileMarkerDir = System.getenv("ASYNC_FILE_MARKER_DIR");
            
            if (asyncFileMarkerDir == null) {
                int timeoutMs = Integer.parseInt(System.getenv().getOrDefault("RESUBSCRIBE_TIMEOUT_MS", "3000"));
                System.out.println("====> task id starts with test-resubscribe-message-id, sleeping for " + timeoutMs + " ms");
                try {
                    Thread.sleep(timeoutMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                handleAsyncWithFiles(messageId, asyncFileMarkerDir);
            }
        }
        
        private void handleAsyncWithFiles(String messageId, String asyncFileMarkerDir) {
            try {
                java.nio.file.Path markerDir = java.nio.file.Paths.get(asyncFileMarkerDir);
                
                java.nio.file.Files.createDirectories(markerDir);
                
                java.nio.file.Path handledFile = markerDir.resolve(messageId + HANDLED_SUFFIX);
                java.nio.file.Files.createFile(handledFile);
                System.out.println("====> created handled file: " + handledFile);
                
                java.nio.file.Path doneFile = markerDir.resolve(messageId + DONE_SUFFIX);
                System.out.println("====> waiting for done file: " + doneFile);
                
                long startTime = System.currentTimeMillis();
                long timeoutMs = 30000; // 30 seconds
                
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    if (java.nio.file.Files.exists(doneFile)) {
                        System.out.println("====> found done file, proceeding");
                        return;
                    }
                    Thread.sleep(100);
                }
                
                System.out.println("====> timeout waiting for done file");
            } catch (Exception e) {
                System.err.println("====> error handling async files: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}