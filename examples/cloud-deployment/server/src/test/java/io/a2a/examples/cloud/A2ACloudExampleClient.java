package io.a2a.examples.cloud;

import io.a2a.client.A2A;
import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TextPart;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test client demonstrating multi-pod A2A agent deployment.
 * <p>
 * This client:
 * 1. Subscribes to a task to receive streaming updates
 * 2. Sends multiple messages to the agent
 * 3. Collects artifacts showing which pod processed each message
 * 4. Verifies that at least 2 different pods handled requests (proving load balancing)
 * 5. Cancels the task to cleanly terminate
 * <p>
 * Usage: Run after deploying the agent to Kubernetes and setting up port-forward:
 * kubectl port-forward -n a2a-demo svc/a2a-agent-service 8080:8080
 */
public class A2ACloudExampleClient {

    private static final String AGENT_URL = System.getProperty("agent.url", "http://localhost:8080");
    private static final int MESSAGE_COUNT = 10;
    private static final int MESSAGE_INTERVAL_MS = 2000;

    public static void main(String[] args) throws Exception {
        System.out.println("=============================================");
        System.out.println("A2A Cloud Deployment Example Client");
        System.out.println("=============================================");
        System.out.println();
        System.out.println("Agent URL: " + AGENT_URL);
        System.out.println("Message count: " + MESSAGE_COUNT);
        System.out.println("Message interval: " + MESSAGE_INTERVAL_MS + "ms");
        System.out.println();

        // Fetch agent card
        System.out.println("Fetching agent card...");
        AgentCard agentCard = A2A.getAgentCard(AGENT_URL);
        System.out.println("✓ Agent: " + agentCard.name());
        System.out.println("✓ Description: " + agentCard.description());
        System.out.println();

        // Generate unique task ID for this test run
        String taskId = "cloud-test-" + System.currentTimeMillis();
        System.out.println("Task ID: " + taskId);
        System.out.println();

        // Track observed pod names
        Set<String> observedPods = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger artifactCount = new AtomicInteger(0);
        AtomicBoolean testFailed = new AtomicBoolean(false);
        CountDownLatch messageLatch = new CountDownLatch(MESSAGE_COUNT);

        // Create streaming client for subscribing
        System.out.println("Creating streaming client for subscription...");
        ClientConfig streamingConfig = new ClientConfig.Builder()
                .setStreaming(true)
                .build();

        Client streamingClient = Client.builder(agentCard)
                .clientConfig(streamingConfig)
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
                .build();

        // Create non-streaming client for sending messages
        System.out.println("Creating non-streaming client for sending messages...");
        ClientConfig nonStreamingConfig = new ClientConfig.Builder()
                .setStreaming(false)
                .build();

        Client nonStreamingClient = Client.builder(agentCard)
                .clientConfig(nonStreamingConfig)
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
                .build();

        System.out.println("✓ Clients created");
        System.out.println();

        // Create initial task by sending first message
        System.out.println("Creating initial task...");
        Message initialMessage = A2A.toUserMessage("Initialize", taskId);
        try {
            nonStreamingClient.sendMessage(initialMessage, List.of((ClientEvent event, AgentCard card) -> {
                if (event instanceof TaskEvent te) {
                    System.out.println("✓ Initial task created: " + te.getTask().getId());
                    System.out.println("  State: " + te.getTask().getStatus().state());
                }
            }), error -> {
                System.err.println("✗ Failed to create initial task: " + error.getMessage());
                testFailed.set(true);
            });

            // Wait a bit for task to be created
            Thread.sleep(1000);
        } catch (Exception e) {
            System.err.println("✗ Failed to create initial task: " + e.getMessage());
            System.exit(1);
        }

        // Subscribe to task updates
        System.out.println();
        System.out.println("Subscribing to task updates...");
        try {
            streamingClient.resubscribe(
                    new TaskIdParams(taskId),
                    List.of((ClientEvent event, AgentCard card) -> {
                        if (event instanceof TaskUpdateEvent tue) {
                            if (tue.getUpdateEvent() instanceof TaskArtifactUpdateEvent artifactEvent) {
                                artifactCount.incrementAndGet();
                                String artifactText = extractTextFromArtifact(artifactEvent);
                                System.out.println("  Artifact #" + artifactCount.get() + ": " + artifactText);

                                // Extract pod name from artifact text
                                String podName = extractPodName(artifactText);
                                if (podName != null && !podName.equals("unknown-pod")) {
                                    observedPods.add(podName);
                                    System.out.println("    → Pod: " + podName + " (Total unique pods: " + observedPods.size() + ")");
                                }
                            }
                        }
                    }),
                    error -> {
                        System.err.println("✗ Subscription error: " + error.getMessage());
                        testFailed.set(true);
                    }
            );
            System.out.println("✓ Subscribed to task updates");
        } catch (Exception e) {
            System.err.println("✗ Failed to subscribe: " + e.getMessage());
            System.exit(1);
        }

        // Send messages in a loop
        System.out.println();
        System.out.println("Sending " + MESSAGE_COUNT + " messages (interval: " + MESSAGE_INTERVAL_MS + "ms)...");
        System.out.println("--------------------------------------------");

        for (int i = 1; i <= MESSAGE_COUNT; i++) {
            final int messageNum = i;
            Message message = A2A.toUserMessage("Test message " + i, taskId);

            try {
                nonStreamingClient.sendMessage(message, List.of((ClientEvent event, AgentCard card) -> {
                    if (event instanceof MessageEvent || event instanceof TaskEvent) {
                        System.out.println("✓ Message " + messageNum + " sent");
                        messageLatch.countDown();
                    }
                }), error -> {
                    System.err.println("✗ Message " + messageNum + " failed: " + error.getMessage());
                    testFailed.set(true);
                    messageLatch.countDown();
                });

                Thread.sleep(MESSAGE_INTERVAL_MS);
            } catch (Exception e) {
                System.err.println("✗ Failed to send message " + i + ": " + e.getMessage());
                testFailed.set(true);
            }
        }

        // Wait for all messages to be sent
        System.out.println();
        System.out.println("Waiting for all messages to be sent...");
        messageLatch.await(30, TimeUnit.SECONDS);

        // Wait a bit for final artifacts to arrive
        System.out.println("Waiting for final artifacts...");
        Thread.sleep(3000);

        // Cancel the task to cleanly terminate
        System.out.println();
        System.out.println("Cancelling task...");
        try {
            Task cancelledTask = nonStreamingClient.cancelTask(new TaskIdParams(taskId));
            System.out.println("✓ Task cancelled: " + cancelledTask.getStatus().state());
        } catch (A2AClientException e) {
            System.err.println("✗ Failed to cancel task: " + e.getMessage());
        }

        // Print results
        System.out.println();
        System.out.println("=============================================");
        System.out.println("Test Results");
        System.out.println("=============================================");
        System.out.println("Total artifacts received: " + artifactCount.get());
        System.out.println("Unique pods observed: " + observedPods.size());
        System.out.println("Pod names: " + observedPods);
        System.out.println();

        if (testFailed.get()) {
            System.out.println("✗ TEST FAILED - Errors occurred during execution");
            System.exit(1);
        } else if (observedPods.size() < 2) {
            System.out.println("✗ TEST FAILED - Expected at least 2 different pods, but only saw: " + observedPods.size());
            System.out.println("  This suggests load balancing is not working correctly.");
            System.exit(1);
        } else {
            System.out.println("✓ TEST PASSED - Successfully demonstrated multi-pod processing!");
            System.out.println("  Messages were handled by " + observedPods.size() + " different pods.");
            System.out.println("  This proves that:");
            System.out.println("    - Load balancing is working (round-robin across pods)");
            System.out.println("    - Event replication is working (subscriber sees events from all pods)");
            System.out.println("    - Database persistence is working (task state shared across pods)");
            System.exit(0);
        }
    }

    private static String extractTextFromArtifact(TaskArtifactUpdateEvent event) {
        StringBuilder text = new StringBuilder();
        if (event.getArtifacts() != null) {
            for (Part<?> part : event.getArtifacts().parts()) {
                if (part instanceof TextPart textPart) {
                    text.append(textPart.getText());
                }
            }
        }
        return text.toString();
    }

    private static String extractPodName(String artifactText) {
        // Artifact text format: "Processed by <pod-name>: Received message '...'"
        if (artifactText != null && artifactText.startsWith("Processed by ")) {
            int colonIndex = artifactText.indexOf(':');
            if (colonIndex > 0) {
                return artifactText.substring("Processed by ".length(), colonIndex).trim();
            }
        }
        return null;
    }
}
