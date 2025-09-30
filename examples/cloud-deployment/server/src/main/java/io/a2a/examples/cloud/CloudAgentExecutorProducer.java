package io.a2a.examples.cloud;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.InternalError;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Producer for the cloud deployment example agent executor.
 */
@ApplicationScoped
public class CloudAgentExecutorProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudAgentExecutorProducer.class);

    @Produces
    public AgentExecutor agentExecutor() {
        return new CloudAgentExecutor();
    }

    /**
     * Agent executor that processes messages and includes pod information in the response.
     * Keeps the task in WORKING state to allow continuous streaming.
     * Use the cancel() method to properly terminate the task.
     */
    private static class CloudAgentExecutor implements AgentExecutor {

        @Override
        public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
            TaskUpdater updater = new TaskUpdater(context, eventQueue);

            try {
                // Only transition through states for new tasks
                if (context.getTask() == null) {
                    updater.submit();
                    updater.startWork();
                }

                // Extract user message
                String userMessage = extractTextFromMessage(context.getMessage());
                LOGGER.info("Processing message: '{}'", userMessage);

                // Get pod name from environment (set by Kubernetes downward API)
                String podName = System.getenv("POD_NAME");
                if (podName == null || podName.isEmpty()) {
                    podName = "unknown-pod";
                }
                LOGGER.info("Processing on pod: {}", podName);

                // Simulate some processing time
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InternalError("Processing interrupted");
                }

                // Create response with pod information
                String responseText = String.format(
                        "Processed by %s: Received message '%s'",
                        podName,
                        userMessage
                );

                TextPart responsePart = new TextPart(responseText, null);
                List<Part<?>> parts = List.of(responsePart);

                // Add the response as an artifact
                updater.addArtifact(parts, null, null, null);

                // Keep task in WORKING state - don't call complete()
                // This allows continuous streaming. Client should call cancel() to terminate.
                LOGGER.info("Successfully processed message on pod: {}", podName);

            } catch (JSONRPCError e) {
                LOGGER.error("JSONRPC error processing task", e);
                throw e;
            } catch (Exception e) {
                LOGGER.error("Error processing task", e);
                throw new InternalError("Processing failed: " + e.getMessage());
            }
        }

        @Override
        public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
            LOGGER.info("Task cancellation requested");
            TaskUpdater updater = new TaskUpdater(context, eventQueue);
            updater.cancel();
        }

        /**
         * Extracts text content from a message.
         */
        private String extractTextFromMessage(Message message) {
            StringBuilder textBuilder = new StringBuilder();
            if (message.getParts() != null) {
                for (Part<?> part : message.getParts()) {
                    if (part instanceof TextPart textPart) {
                        textBuilder.append(textPart.getText());
                    }
                }
            }
            return textBuilder.toString();
        }
    }
}
