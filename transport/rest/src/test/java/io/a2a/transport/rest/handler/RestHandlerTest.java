package io.a2a.transport.rest.handler;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.a2a.server.ServerCallContext;
import io.a2a.server.auth.UnauthenticatedUser;
import io.a2a.server.requesthandlers.AbstractA2ARequestHandlerTest;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Task;
import io.a2a.server.tasks.TaskUpdater;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RestHandlerTest extends AbstractA2ARequestHandlerTest {

    private final ServerCallContext callContext = new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of("foo", "bar"));

    @Test
    public void testGetAgentCard() {
        RestHandler handler = new RestHandler(CARD, requestHandler);
        RestHandler.HTTPRestResponse response = handler.handleRequest("GET", "/v1/card", null, callContext);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/json", response.getContentType());
        Assertions.assertNotNull(response.getBody());
    }

    @Test
    public void testGetTaskSuccess() {
        RestHandler handler = new RestHandler(CARD, requestHandler);
        taskStore.save(MINIMAL_TASK);

        RestHandler.HTTPRestResponse response = handler.handleRequest("GET", "/v1/tasks/" + MINIMAL_TASK.getId(), null, callContext);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/json", response.getContentType());
        Assertions.assertTrue(response.getBody().contains(MINIMAL_TASK.getId()));
    }

    @Test
    public void testGetTaskNotFound() {
        RestHandler handler = new RestHandler(CARD, requestHandler);

        RestHandler.HTTPRestResponse response = handler.handleRequest("GET", "/v1/tasks/nonexistent", null, callContext);

        Assertions.assertEquals(404, response.getStatusCode());
        Assertions.assertEquals("application/json", response.getContentType());
        Assertions.assertTrue(response.getBody().contains("TaskNotFoundError"));
    }

    @Test
    public void testSendMessage() throws InvalidProtocolBufferException {
        RestHandler handler = new RestHandler(CARD, requestHandler);
        agentExecutorExecute = (context, eventQueue) -> {
            eventQueue.enqueueEvent(context.getMessage());
        };
        String requestBody = """
            {
              "message":
                {
                  "messageId": "message-1234",
                  "contextId": "context-1234",
                  "role": "ROLE_USER",
                  "content": [{
                    "text": "tell me a joke"
                  }],
                  "metadata": {
                  }
              }
            }""";

        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/message:send", requestBody, callContext);
        Assertions.assertEquals(200, response.getStatusCode(), response.toString());
        Assertions.assertEquals("application/json", response.getContentType());
        Assertions.assertNotNull(response.getBody());
    }

    @Test
    public void testSendMessageInvalidBody() {
        RestHandler handler = new RestHandler(CARD, requestHandler);

        String invalidBody = "invalid json";
        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/message:send", invalidBody, callContext);

        Assertions.assertEquals(400, response.getStatusCode());
        Assertions.assertEquals("application/json", response.getContentType());
        Assertions.assertTrue(response.getBody().contains("InvalidParamsError"));
    }

    @Test
    public void testSendMessageEmptyBody() {
        RestHandler handler = new RestHandler(CARD, requestHandler);

        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/message:send", null, callContext);

        Assertions.assertEquals(400, response.getStatusCode());
        Assertions.assertEquals("application/json", response.getContentType());
        Assertions.assertTrue(response.getBody().contains("InvalidParamsError"));
    }

    @Test
    public void testCancelTaskSuccess() {
        RestHandler handler = new RestHandler(CARD, requestHandler);
        taskStore.save(MINIMAL_TASK);

        agentExecutorCancel = (context, eventQueue) -> {
            // We need to cancel the task or the EventConsumer never finds a 'final' event.
            // Looking at the Python implementation, they typically use AgentExecutors that
            // don't support cancellation. So my theory is the Agent updates the task to the CANCEL status
            Task task = context.getTask();
            TaskUpdater taskUpdater = new TaskUpdater(context, eventQueue);
            taskUpdater.cancel();
        };

        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/tasks/" + MINIMAL_TASK.getId() + ":cancel", null, callContext);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/json", response.getContentType());
        Assertions.assertTrue(response.getBody().contains(MINIMAL_TASK.getId()));
    }

    @Test
    public void testCancelTaskNotFound() {
        RestHandler handler = new RestHandler(CARD, requestHandler);

        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/tasks/nonexistent:cancel", null, callContext);

        Assertions.assertEquals(404, response.getStatusCode());
        Assertions.assertEquals("application/json", response.getContentType());
        Assertions.assertTrue(response.getBody().contains("TaskNotFoundError"));
    }

    @Test
    public void testSendStreamingMessageSuccess() {
        RestHandler handler = new RestHandler(CARD, requestHandler);
        agentExecutorExecute = (context, eventQueue) -> {
            eventQueue.enqueueEvent(context.getMessage());
        };
        String requestBody = """
            {
              "message": {
                "role": "ROLE_USER",
                "content": [
                  {
                    "text": "tell me some jokes"
                  }
                ],
                "messageId": "message-1234",
                "contextId": "context-1234"
              },
              "configuration": {
                "acceptedOutputModes": ["text"]
              }
            }""";

        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/message:stream", requestBody, callContext);
        Assertions.assertEquals(200, response.getStatusCode(), response.toString());
        Assertions.assertInstanceOf(RestHandler.HTTPRestStreamingResponse.class, response);
        RestHandler.HTTPRestStreamingResponse streamingResponse = (RestHandler.HTTPRestStreamingResponse) response;
        Assertions.assertNotNull(streamingResponse.getPublisher());
        Assertions.assertEquals("text/event-stream", streamingResponse.getContentType());
    }

    @Test
    public void testSendStreamingMessageNotSupported() {
        AgentCard card = createAgentCard(false, true, true);
        RestHandler handler = new RestHandler(card, requestHandler);

        String requestBody = """
            {
                "contextId": "ctx123",
                "role": "ROLE_USER",
                "content": [{
                    "text": "Hello"
                }]
            }
            """;

        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/message:stream", requestBody, callContext);

        Assertions.assertEquals(400, response.getStatusCode());
        Assertions.assertTrue(response.getBody().contains("InvalidRequestError"));
    }

    @Test
    public void testPushNotificationConfigSuccess() {
        RestHandler handler = new RestHandler(CARD, requestHandler);
        taskStore.save(MINIMAL_TASK);

        String requestBody = """
            {
              "parent": "tasks/%s",
              "config": {
                "name": "tasks/%s/pushNotificationConfigs/",
                "pushNotificationConfig": {
                  "url": "https://example.com/callback",
                  "authentication": {
                    "schemes": ["jwt"]
                  }
                }
              }
            }""".formatted(MINIMAL_TASK.getId(),MINIMAL_TASK.getId());

        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/tasks/" + MINIMAL_TASK.getId() + "/pushNotificationConfigs", requestBody, callContext);

        Assertions.assertEquals(201, response.getStatusCode(), response.toString());
        Assertions.assertEquals("application/json", response.getContentType());
        Assertions.assertNotNull(response.getBody());
    }

    @Test
    public void testPushNotificationConfigNotSupported() {
        AgentCard card = createAgentCard(true, false, true);
        RestHandler handler = new RestHandler(card, requestHandler);

        String requestBody = """
            {
                "taskId": "%s",
                "pushNotificationConfig": {
                    "url": "http://example.com"
                }
            }
            """.formatted(MINIMAL_TASK.getId());

        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/tasks/" + MINIMAL_TASK.getId() + "/pushNotificationConfigs", requestBody, callContext);

        Assertions.assertEquals(501, response.getStatusCode());
        Assertions.assertTrue(response.getBody().contains("PushNotificationNotSupportedError"));
    }

    @Test
    public void testGetPushNotificationConfig() {
        RestHandler handler = new RestHandler(CARD, requestHandler);
        taskStore.save(MINIMAL_TASK);

        // First, create a push notification config
        String createRequestBody = """
            {
              "parent": "tasks/%s",
              "config": {
                "name": "tasks/%s/pushNotificationConfigs/",
                "pushNotificationConfig": {
                  "url": "https://example.com/callback",
                  "authentication": {
                    "schemes": ["jwt"]
                  }
                }
              }
            }""".formatted(MINIMAL_TASK.getId(),MINIMAL_TASK.getId());
        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/tasks/" + MINIMAL_TASK.getId() + "/pushNotificationConfigs", createRequestBody, callContext);
        Assertions.assertEquals(201, response.getStatusCode(), response.toString());
        Assertions.assertEquals("application/json", response.getContentType());
        // Now get it
        response = handler.handleRequest("GET", "/v1/tasks/" + MINIMAL_TASK.getId() + "/pushNotificationConfigs/default-config-id", null, callContext);
        Assertions.assertEquals(200, response.getStatusCode(), response.toString());
        Assertions.assertEquals("application/json", response.getContentType());
    }

    @Test
    public void testDeletePushNotificationConfig() {
        RestHandler handler = new RestHandler(CARD, requestHandler);
        taskStore.save(MINIMAL_TASK);

        RestHandler.HTTPRestResponse response = handler.handleRequest("DELETE", "/v1/tasks/" + MINIMAL_TASK.getId() + "/pushNotificationConfigs/default-config-id", null, callContext);

        Assertions.assertEquals(204, response.getStatusCode());
    }

    @Test
    public void testListPushNotificationConfigs() {
        RestHandler handler = new RestHandler(CARD, requestHandler);
        taskStore.save(MINIMAL_TASK);

        RestHandler.HTTPRestResponse response = handler.handleRequest("GET", "/v1/tasks/" + MINIMAL_TASK.getId() + "/pushNotificationConfigs", null, callContext);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/json", response.getContentType());
        Assertions.assertNotNull(response.getBody());
    }

    @Test
    public void testMethodNotFound() {
        RestHandler handler = new RestHandler(CARD, requestHandler);

        RestHandler.HTTPRestResponse response = handler.handleRequest("GET", "/v1/unknown/endpoint", null, callContext);

        Assertions.assertEquals(404, response.getStatusCode());
        Assertions.assertTrue(response.getBody().contains("MethodNotFoundError"));
    }

    @Test
    public void testUnsupportedHttpMethod() {
        RestHandler handler = new RestHandler(CARD, requestHandler);

        RestHandler.HTTPRestResponse response = handler.handleRequest("PATCH", "/v1/card", null, callContext);

        Assertions.assertEquals(405, response.getStatusCode());
        Assertions.assertTrue(response.getBody().contains("MethodNotFoundError"));
    }

    @Test
    public void testHttpStatusCodeMapping() {
        RestHandler handler = new RestHandler(CARD, requestHandler);

        // Test 400 for invalid request
        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/message:send", null, callContext);
        Assertions.assertEquals(400, response.getStatusCode());

        // Test 404 for not found
        response = handler.handleRequest("GET", "/v1/tasks/nonexistent", null, callContext);
        Assertions.assertEquals(404, response.getStatusCode());

        // Test 405 for unsupported method
        response = handler.handleRequest("PATCH", "/v1/card", null, callContext);
        Assertions.assertEquals(405, response.getStatusCode());
    }

    @Test
    public void testStreamingDoesNotBlockMainThread() throws Exception {
        RestHandler handler = new RestHandler(CARD, requestHandler);

        // Track if the main thread gets blocked during streaming
        AtomicBoolean eventReceived = new AtomicBoolean(false);
        CountDownLatch streamStarted = new CountDownLatch(1);
        CountDownLatch eventProcessed = new CountDownLatch(1);
        agentExecutorExecute = (context, eventQueue) -> {
            // Wait a bit to ensure the main thread continues
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            eventQueue.enqueueEvent(context.getMessage());
        };

        String requestBody = """
            {
              "message": {
                "role": "ROLE_USER",
                "content": [
                  {
                    "text": "tell me some jokes"
                  }
                ],
                "messageId": "message-1234",
                "contextId": "context-1234"
              },
              "configuration": {
                "acceptedOutputModes": ["text"]
              }
            }""";

        // Start streaming
        RestHandler.HTTPRestResponse response = handler.handleRequest("POST", "/v1/message:stream", requestBody, callContext);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertInstanceOf(RestHandler.HTTPRestStreamingResponse.class, response);

        RestHandler.HTTPRestStreamingResponse streamingResponse = (RestHandler.HTTPRestStreamingResponse) response;
        Flow.Publisher<String> publisher = streamingResponse.getPublisher();
        publisher.subscribe(new Flow.Subscriber<String>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                streamStarted.countDown();
                subscription.request(1);
            }

            @Override
            public void onNext(String item) {
                eventReceived.set(true);
                eventProcessed.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                eventProcessed.countDown();
            }

            @Override
            public void onComplete() {
                eventProcessed.countDown();
            }
        });

        // The main thread should not be blocked - we should be able to continue immediately
        Assertions.assertTrue(streamStarted.await(100, TimeUnit.MILLISECONDS),
            "Streaming subscription should start quickly without blocking main thread");

        // This proves the main thread is not blocked - we can do other work
        // Simulate main thread doing other work
        Thread.sleep(50);

        // Wait for the actual event processing to complete
        Assertions.assertTrue(eventProcessed.await(2, TimeUnit.SECONDS),
            "Event should be processed within reasonable time");

        // Verify we received the event
        Assertions.assertTrue(eventReceived.get(), "Should have received streaming event");
    }
}
