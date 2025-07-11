package io.a2a.server.apps.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import static org.wildfly.common.Assert.assertNotNull;
import static org.wildfly.common.Assert.assertTrue;

import io.a2a.client.A2AClient;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.A2AClientError;
import io.a2a.spec.A2AServerException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.Part;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;

public abstract class AbstractA2AServerClientTest {

    private static final Task MINIMAL_TASK = new Task.Builder()
            .id("task-123")
            .contextId("session-xyz")
            .status(new TaskStatus(TaskState.SUBMITTED))
            .build();

    private static final Message MESSAGE = new Message.Builder()
            .messageId("111")
            .role(Message.Role.AGENT)
            .parts(new TextPart("test message"))
            .build();

    private final int serverPort;

    protected AbstractA2AServerClientTest(int serverPort) {
        this.serverPort = serverPort;
    }

    @Test
    public void testGetTaskSuccess() {
        getTaskStore().save(MINIMAL_TASK);
        try {
            A2AClient client = createA2AClient();
            GetTaskResponse response = client.getTask(MINIMAL_TASK.getId());
            assertEquals("task-123", response.getResult().getId());
            assertEquals("session-xyz", response.getResult().getContextId());
            assertEquals(TaskState.SUBMITTED, response.getResult().getStatus().state());
            assertNull(response.getError());
        } catch (A2AServerException e) {
            org.junit.jupiter.api.Assertions.fail("A2AServerException was thrown when not expected: " + e.getMessage(), e);
        } finally {
            getTaskStore().delete(MINIMAL_TASK.getId());
        }
    }

    @Test
    public void testSendMessageNewMessageSuccess() {
        Message message = new Message.Builder(MESSAGE)
                .taskId(MINIMAL_TASK.getId())
                .contextId(MINIMAL_TASK.getContextId())
                .build();
        MessageSendParams messageSendParams = new MessageSendParams(message, null, null);
        
        A2AClient client = createA2AClient();
        try {
            SendMessageResponse response = client.sendMessage(messageSendParams);
            assertNull(response.getError());
            Message messageResponse = (Message) response.getResult();
            assertEquals(MESSAGE.getMessageId(), messageResponse.getMessageId());
            assertEquals(MESSAGE.getRole(), messageResponse.getRole());
            Part<?> part = messageResponse.getParts().get(0);
            assertEquals(Part.Kind.TEXT, part.getKind());
            assertEquals("test message", ((TextPart) part).getText());
        } catch (A2AServerException e) {
            org.junit.jupiter.api.Assertions.fail("A2AServerException was thrown when not expected: " + e.getMessage(), e);
        }
    }

    @Test
    public void testGetAgentCard() {
        A2AClient client = createA2AClient();
        try {
            AgentCard agentCard = client.getAgentCard();
            assertNotNull(agentCard);
            assertEquals("test-card", agentCard.name());
            assertEquals("A test agent card", agentCard.description());
            assertEquals("http://localhost:" + serverPort, agentCard.url());
            assertEquals("1.0", agentCard.version());
            assertEquals("http://example.com/docs", agentCard.documentationUrl());
            assertTrue(agentCard.capabilities().pushNotifications());
            assertTrue(agentCard.capabilities().streaming());
            assertTrue(agentCard.capabilities().stateTransitionHistory());
            assertTrue(agentCard.skills().isEmpty());
        } catch (A2AClientError e) {
            // Handle exception
        }
    }

    protected A2AClient createA2AClient() {
        return new A2AClient("http://localhost:" + serverPort);
    }

    protected abstract TaskStore getTaskStore();

    protected abstract InMemoryQueueManager getQueueManager();

    protected abstract void setStreamingSubscribedRunnable(Runnable runnable);
} 