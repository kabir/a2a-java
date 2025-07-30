package io.a2a.server.apps.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.a2a.client.A2AClient;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.A2AServerException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Artifact;
import io.a2a.spec.CancelTaskResponse;
import io.a2a.spec.Event;
import io.a2a.spec.GetTaskPushNotificationConfigResponse;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.InvalidParamsError;
import io.a2a.spec.InvalidRequestError;
import io.a2a.spec.JSONParseError;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.JSONRPCErrorResponse;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.MethodNotFoundError;
import io.a2a.spec.Part;
import io.a2a.spec.PushNotificationConfig;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.SendStreamingMessageResponse;
import io.a2a.spec.SetTaskPushNotificationConfigResponse;
import io.a2a.spec.StreamingJSONRPCRequest;
import io.a2a.spec.Task;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TaskNotFoundError;
import io.a2a.spec.TaskPushNotificationConfig;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskResubscriptionRequest;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.TextPart;
import io.a2a.spec.UnsupportedOperationError;
import io.a2a.util.Utils;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.wildfly.common.Assert.assertNotNull;
import static org.wildfly.common.Assert.assertTrue;

public abstract class AbstractA2AServerTest {
    
    private static final Task MINIMAL_TASK = new Task.Builder().id("task-123").contextId("session-xyz")
            .status(new TaskStatus(TaskState.SUBMITTED)).build();
    
    private static final Task CANCEL_TASK = new Task.Builder().id("cancel-task-123").contextId("session-xyz")
            .status(new TaskStatus(TaskState.SUBMITTED)).build();
    
    private static final Task CANCEL_TASK_NOT_SUPPORTED = new Task.Builder().id("cancel-task-not-supported-123")
            .contextId("session-xyz").status(new TaskStatus(TaskState.SUBMITTED)).build();
    
    private static final Task SEND_MESSAGE_NOT_SUPPORTED = new Task.Builder().id("task-not-supported-123")
            .contextId("session-xyz").status(new TaskStatus(TaskState.SUBMITTED)).build();
    
    private static final Message MESSAGE = new Message.Builder().messageId("111").role(Message.Role.AGENT)
            .parts(new TextPart("test message")).build();
    
    private final int serverPort;
    
    private A2AClient client;
    
    protected AbstractA2AServerTest(int serverPort) {
        this.serverPort = serverPort;
        this.client = new A2AClient("http://localhost:" + serverPort);
    }
    
    @Test
    public void testGetTaskSuccess() {
        getTaskStore().save(MINIMAL_TASK);
        try {
            GetTaskResponse response = client.getTask("1", new TaskQueryParams(MINIMAL_TASK.getId()));
            assertEquals("1", response.getId());
            assertEquals("task-123", response.getResult().getId());
            assertEquals("session-xyz", response.getResult().getContextId());
            assertEquals(TaskState.SUBMITTED, response.getResult().getStatus().state());
            assertNull(response.getError());
        } catch (A2AServerException e) {
            fail("Unexpected exception during getTask: " + e.getMessage(), e);
        } finally {
            getTaskStore().delete(MINIMAL_TASK.getId());
        }
    }
    
    @Test
    public void testGetTaskNotFound() {
        assertTrue(getTaskStore().get("non-existent-task") == null);
        try {
            GetTaskResponse response = client.getTask("1", new TaskQueryParams("non-existent-task"));
            assertEquals("1", response.getId());
            assertInstanceOf(JSONRPCError.class, response.getError());
            assertEquals(new TaskNotFoundError().getCode(), response.getError().getCode());
            assertNull(response.getResult());
        } catch (A2AServerException e) {
            fail("Unexpected exception during getTask for non-existent task: " + e.getMessage(), e);
        }
    }
    
    @Test
    public void testCancelTaskSuccess() {
        getTaskStore().save(CANCEL_TASK);
        try {
            CancelTaskResponse response = client.cancelTask("1", new TaskIdParams(CANCEL_TASK.getId()));
            assertEquals("1", response.getId());
            assertNull(response.getError());
            Task task = response.getResult();
            assertEquals(CANCEL_TASK.getContextId(), task.getContextId());
            assertEquals(TaskState.CANCELED, task.getStatus().state());
        } catch (A2AServerException e) {
            fail("Unexpected exception during cancel task success test: " + e.getMessage(), e);
        } finally {
            getTaskStore().delete(CANCEL_TASK.getId());
        }
    }
    
    @Test
    public void testCancelTaskNotSupported() {
        getTaskStore().save(CANCEL_TASK_NOT_SUPPORTED);
        try {
            CancelTaskResponse response = client.cancelTask("1", new TaskIdParams(CANCEL_TASK_NOT_SUPPORTED.getId()));
            assertNull(response.getResult());
            assertEquals("1", response.getId());
            assertInstanceOf(JSONRPCError.class, response.getError());
            assertEquals(new UnsupportedOperationError().getCode(), response.getError().getCode());
        } catch (A2AServerException e) {
            fail("Unexpected exception during cancel task not supported test: " + e.getMessage(), e);
        } finally {
            getTaskStore().delete(CANCEL_TASK_NOT_SUPPORTED.getId());
        }
    }
    
    @Test
    public void testCancelTaskNotFound() {
        try {
            CancelTaskResponse response = client.cancelTask("1", new TaskIdParams("non-existent-task"));
            assertEquals("1", response.getId());
            assertNull(response.getResult());
            assertInstanceOf(JSONRPCError.class, response.getError());
            assertEquals(new TaskNotFoundError().getCode(), response.getError().getCode());
        } catch (A2AServerException e) {
            fail("Unexpected exception during cancel task not found test: " + e.getMessage(), e);
        }
    }
    
    @Test
    public void testSendMessageNewMessageSuccess() {
        assertTrue(getTaskStore().get(MINIMAL_TASK.getId()) == null);
        Message message = new Message.Builder(MESSAGE).taskId(MINIMAL_TASK.getId())
                .contextId(MINIMAL_TASK.getContextId()).build();
        MessageSendParams messageSendParams = new MessageSendParams(message, null, null);
        
        try {
            SendMessageResponse response = client.sendMessage("1", messageSendParams);
            assertEquals("1", response.getId());
            assertNull(response.getError());
            Message messageResponse = (Message) response.getResult();
            assertEquals(MESSAGE.getMessageId(), messageResponse.getMessageId());
            assertEquals(MESSAGE.getRole(), messageResponse.getRole());
            Part<?> part = messageResponse.getParts().get(0);
            assertEquals(Part.Kind.TEXT, part.getKind());
            assertEquals("test message", ((TextPart) part).getText());
        } catch (A2AServerException e) {
            fail("Unexpected exception during send new message test: " + e.getMessage(), e);
        }
    }
    
    @Test
    public void testSendMessageExistingTaskSuccess() {
        getTaskStore().save(MINIMAL_TASK);
        try {
            Message message = new Message.Builder(MESSAGE).taskId(MINIMAL_TASK.getId())
                    .contextId(MINIMAL_TASK.getContextId()).build();
            MessageSendParams messageSendParams = new MessageSendParams(message, null, null);
            
            SendMessageResponse response = client.sendMessage("1", messageSendParams);
            assertEquals("1", response.getId());
            assertNull(response.getError());
            Message messageResponse = (Message) response.getResult();
            assertEquals(MESSAGE.getMessageId(), messageResponse.getMessageId());
            assertEquals(MESSAGE.getRole(), messageResponse.getRole());
            Part<?> part = messageResponse.getParts().get(0);
            assertEquals(Part.Kind.TEXT, part.getKind());
            assertEquals("test message", ((TextPart) part).getText());
        } catch (A2AServerException e) {
            fail("Unexpected exception during send message to existing task test: " + e.getMessage(), e);
        } finally {
            getTaskStore().delete(MINIMAL_TASK.getId());
        }
    }
    
    @Test
    public void testSetPushNotificationSuccess() {
        getTaskStore().save(MINIMAL_TASK);
        try {
            PushNotificationConfig pushNotificationConfig = new PushNotificationConfig.Builder().url(
                    "http://example.com").build();
            SetTaskPushNotificationConfigResponse response = client.setTaskPushNotificationConfig("1",
                    MINIMAL_TASK.getId(), pushNotificationConfig);
            assertEquals("1", response.getId());
            assertNull(response.getError());
            TaskPushNotificationConfig config = response.getResult();
            assertEquals(MINIMAL_TASK.getId(), config.taskId());
            assertEquals("http://example.com", config.pushNotificationConfig().url());
        } catch (A2AServerException e) {
            fail("Unexpected exception during set push notification test: " + e.getMessage(), e);
        } finally {
            getTaskStore().delete(MINIMAL_TASK.getId());
        }
    }
    
    @Test
    public void testGetPushNotificationSuccess() {
        getTaskStore().save(MINIMAL_TASK);
        try {
            PushNotificationConfig pushNotificationConfig = new PushNotificationConfig.Builder().url(
                    "http://example.com").build();
            
            // First set the push notification config
            SetTaskPushNotificationConfigResponse setResponse = client.setTaskPushNotificationConfig("1",
                    MINIMAL_TASK.getId(), pushNotificationConfig);
            assertEquals("1", setResponse.getId());
            assertNotNull(setResponse);
            
            // Then get the push notification config
            GetTaskPushNotificationConfigResponse response = client.getTaskPushNotificationConfig("2",
                    new TaskIdParams(MINIMAL_TASK.getId()));
            assertEquals("2", response.getId());
            assertNull(response.getError());
            TaskPushNotificationConfig config = response.getResult();
            assertEquals(MINIMAL_TASK.getId(), config.taskId());
            assertEquals("http://example.com", config.pushNotificationConfig().url());
        } catch (A2AServerException e) {
            fail("Unexpected exception during get push notification test: " + e.getMessage(), e);
        } finally {
            getTaskStore().delete(MINIMAL_TASK.getId());
        }
    }
    
    @Test
    public void testError() {
        Message message = new Message.Builder(MESSAGE).taskId(SEND_MESSAGE_NOT_SUPPORTED.getId())
                .contextId(SEND_MESSAGE_NOT_SUPPORTED.getContextId()).build();
        MessageSendParams messageSendParams = new MessageSendParams(message, null, null);
        
        try {
            SendMessageResponse response = client.sendMessage("1", messageSendParams);
            assertEquals("1", response.getId());
            assertNull(response.getResult());
            assertInstanceOf(JSONRPCError.class, response.getError());
            assertEquals(new UnsupportedOperationError().getCode(), response.getError().getCode());
        } catch (A2AServerException e) {
            fail("Unexpected exception during error handling test: " + e.getMessage(), e);
        }
    }
    
    @Test
    public void testGetAgentCard() {
        AgentCard agentCard = given().contentType(MediaType.APPLICATION_JSON).when().get("/.well-known/agent.json")
                .then().statusCode(200).extract().as(AgentCard.class);
        assertNotNull(agentCard);
        assertEquals("test-card", agentCard.name());
        assertEquals("A test agent card", agentCard.description());
        assertEquals("http://localhost:8081", agentCard.url());
        assertEquals("1.0", agentCard.version());
        assertEquals("http://example.com/docs", agentCard.documentationUrl());
        assertTrue(agentCard.capabilities().pushNotifications());
        assertTrue(agentCard.capabilities().streaming());
        assertTrue(agentCard.capabilities().stateTransitionHistory());
        assertTrue(agentCard.skills().isEmpty());
    }
    
    @Test
    public void testGetExtendAgentCardNotSupported() {
        given().contentType(MediaType.APPLICATION_JSON).when().get("/agent/authenticatedExtendedCard").then()
                .statusCode(404).body("error", equalTo("Extended agent card not supported or not enabled."));
    }
    
    @Test
    public void testMalformedJSONRPCRequest() {
        // missing closing bracket
        String malformedRequest = "{\"jsonrpc\": \"2.0\", \"method\": \"message/send\", \"params\": {\"foo\": \"bar\"}";
        JSONRPCErrorResponse response = given().contentType(MediaType.APPLICATION_JSON).body(malformedRequest).when()
                .post("/").then().statusCode(200).extract().as(JSONRPCErrorResponse.class);
        assertNotNull(response.getError());
        assertEquals(new JSONParseError().getCode(), response.getError().getCode());
    }
    
    @Test
    public void testInvalidParamsJSONRPCRequest() {
        String invalidParamsRequest = """
                {"jsonrpc": "2.0", "method": "message/send", "params": "not_a_dict", "id": "1"}
                """;
        testInvalidParams(invalidParamsRequest);
        
        invalidParamsRequest = """
                {"jsonrpc": "2.0", "method": "message/send", "params": {"message": {"parts": "invalid"}}, "id": "1"}
                """;
        testInvalidParams(invalidParamsRequest);
    }
    
    private void testInvalidParams(String invalidParamsRequest) {
        JSONRPCErrorResponse response = given().contentType(MediaType.APPLICATION_JSON).body(invalidParamsRequest)
                .when().post("/").then().statusCode(200).extract().as(JSONRPCErrorResponse.class);
        assertNotNull(response.getError());
        assertEquals(new InvalidParamsError().getCode(), response.getError().getCode());
        assertEquals("1", response.getId());
    }
    
    @Test
    public void testInvalidJSONRPCRequestMissingJsonrpc() {
        String invalidRequest = """
                {
                 "method": "message/send",
                 "params": {}
                }
                """;
        JSONRPCErrorResponse response = given().contentType(MediaType.APPLICATION_JSON).body(invalidRequest).when()
                .post("/").then().statusCode(200).extract().as(JSONRPCErrorResponse.class);
        assertNotNull(response.getError());
        assertEquals(new InvalidRequestError().getCode(), response.getError().getCode());
    }
    
    @Test
    public void testInvalidJSONRPCRequestMissingMethod() {
        String invalidRequest = """
                {"jsonrpc": "2.0", "params": {}}
                """;
        JSONRPCErrorResponse response = given().contentType(MediaType.APPLICATION_JSON).body(invalidRequest).when()
                .post("/").then().statusCode(200).extract().as(JSONRPCErrorResponse.class);
        assertNotNull(response.getError());
        assertEquals(new InvalidRequestError().getCode(), response.getError().getCode());
    }
    
    @Test
    public void testInvalidJSONRPCRequestInvalidId() {
        String invalidRequest = """
                {"jsonrpc": "2.0", "method": "message/send", "params": {}, "id": {"bad": "type"}}
                """;
        JSONRPCErrorResponse response = given().contentType(MediaType.APPLICATION_JSON).body(invalidRequest).when()
                .post("/").then().statusCode(200).extract().as(JSONRPCErrorResponse.class);
        assertNotNull(response.getError());
        assertEquals(new InvalidRequestError().getCode(), response.getError().getCode());
    }
    
    @Test
    public void testInvalidJSONRPCRequestNonExistentMethod() {
        String invalidRequest = """
                {"jsonrpc": "2.0", "method" : "nonexistent/method", "params": {}}
                """;
        JSONRPCErrorResponse response = given().contentType(MediaType.APPLICATION_JSON).body(invalidRequest).when()
                .post("/").then().statusCode(200).extract().as(JSONRPCErrorResponse.class);
        assertNotNull(response.getError());
        assertEquals(new MethodNotFoundError().getCode(), response.getError().getCode());
    }
    
    private void testGetTask(String mediaType) {
        getTaskStore().save(MINIMAL_TASK);
        try {
            GetTaskRequest request = new GetTaskRequest("1", new TaskQueryParams(MINIMAL_TASK.getId()));
            RequestSpecification requestSpecification = RestAssured.given().contentType(MediaType.APPLICATION_JSON)
                    .body(request);
            if (mediaType != null) {
                requestSpecification = requestSpecification.accept(mediaType);
            }
            GetTaskResponse response = requestSpecification.when().post("/").then().statusCode(200).extract()
                    .as(GetTaskResponse.class);
            assertEquals("1", response.getId());
            assertEquals("task-123", response.getResult().getId());
            assertEquals("session-xyz", response.getResult().getContextId());
            assertEquals(TaskState.SUBMITTED, response.getResult().getStatus().state());
            assertNull(response.getError());
        } catch (Exception e) {
        } finally {
            getTaskStore().delete(MINIMAL_TASK.getId());
        }
    }
    
    @Test
    public void testNonStreamingMethodWithAcceptHeader() {
        testGetTask(MediaType.APPLICATION_JSON);
    }
    
    @Test
    public void testSendMessageStreamExistingTaskSuccess() {
        getTaskStore().save(MINIMAL_TASK);
        try {
            Message message = new Message.Builder(MESSAGE).taskId(MINIMAL_TASK.getId())
                    .contextId(MINIMAL_TASK.getContextId()).build();
            SendStreamingMessageRequest request = new SendStreamingMessageRequest("1",
                    new MessageSendParams(message, null, null));
            
            CompletableFuture<HttpResponse<Stream<String>>> responseFuture = initialiseStreamingRequest(request, null);
            
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            
            responseFuture.thenAccept(response -> {
                if (response.statusCode() != 200) {
                    //errorRef.set(new IllegalStateException("Status code was " + response.statusCode()));
                    throw new IllegalStateException("Status code was " + response.statusCode());
                }
                response.body().forEach(line -> {
                    try {
                        SendStreamingMessageResponse jsonResponse = extractJsonResponseFromSseLine(line);
                        if (jsonResponse != null) {
                            assertNull(jsonResponse.getError());
                            Message messageResponse = (Message) jsonResponse.getResult();
                            assertEquals(MESSAGE.getMessageId(), messageResponse.getMessageId());
                            assertEquals(MESSAGE.getRole(), messageResponse.getRole());
                            Part<?> part = messageResponse.getParts().get(0);
                            assertEquals(Part.Kind.TEXT, part.getKind());
                            assertEquals("test message", ((TextPart) part).getText());
                            latch.countDown();
                        }
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
            }).exceptionally(t -> {
                if (!isStreamClosedError(t)) {
                    errorRef.set(t);
                }
                latch.countDown();
                return null;
            });
            
            boolean dataRead = latch.await(20, TimeUnit.SECONDS);
            Assertions.assertTrue(dataRead);
            Assertions.assertNull(errorRef.get());
        } catch (Exception e) {
        } finally {
            getTaskStore().delete(MINIMAL_TASK.getId());
        }
    }
    
    @Test
    public void testResubscribeExistingTaskSuccess() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        getTaskStore().save(MINIMAL_TASK);
        
        try {
            // attempting to send a streaming message instead of explicitly calling queueManager#createOrTap
            // does not work because after the message is sent, the queue becomes null but task resubscription
            // requires the queue to still be active
            getQueueManager().createOrTap(MINIMAL_TASK.getId());
            
            CountDownLatch taskResubscriptionRequestSent = new CountDownLatch(1);
            CountDownLatch taskResubscriptionResponseReceived = new CountDownLatch(2);
            AtomicReference<SendStreamingMessageResponse> firstResponse = new AtomicReference<>();
            AtomicReference<SendStreamingMessageResponse> secondResponse = new AtomicReference<>();
            
            // resubscribe to the task, requires the task and its queue to still be active
            TaskResubscriptionRequest taskResubscriptionRequest = new TaskResubscriptionRequest("1",
                    new TaskIdParams(MINIMAL_TASK.getId()));
            
            // Count down the latch when the MultiSseSupport on the server has started subscribing
            setStreamingSubscribedRunnable(taskResubscriptionRequestSent::countDown);
            
            CompletableFuture<HttpResponse<Stream<String>>> responseFuture = initialiseStreamingRequest(
                    taskResubscriptionRequest, null);
            
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            
            responseFuture.thenAccept(response -> {
                
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Status code was " + response.statusCode());
                }
                try {
                    response.body().forEach(line -> {
                        try {
                            SendStreamingMessageResponse jsonResponse = extractJsonResponseFromSseLine(line);
                            if (jsonResponse != null) {
                                SendStreamingMessageResponse sendStreamingMessageResponse = Utils.OBJECT_MAPPER.readValue(
                                        line.substring("data: ".length()).trim(), SendStreamingMessageResponse.class);
                                if (taskResubscriptionResponseReceived.getCount() == 2) {
                                    firstResponse.set(sendStreamingMessageResponse);
                                } else {
                                    secondResponse.set(sendStreamingMessageResponse);
                                }
                                taskResubscriptionResponseReceived.countDown();
                                if (taskResubscriptionResponseReceived.getCount() == 0) {
                                    throw new BreakException();
                                }
                            }
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (BreakException e) {
                }
            }).exceptionally(t -> {
                if (!isStreamClosedError(t)) {
                    errorRef.set(t);
                }
                return null;
            });
            
            try {
                taskResubscriptionRequestSent.await();
                List<Event> events = List.of(new TaskArtifactUpdateEvent.Builder().taskId(MINIMAL_TASK.getId())
                                .contextId(MINIMAL_TASK.getContextId())
                                .artifact(new Artifact.Builder().artifactId("11").parts(new TextPart("text")).build()).build(),
                        new TaskStatusUpdateEvent.Builder().taskId(MINIMAL_TASK.getId())
                                .contextId(MINIMAL_TASK.getContextId()).status(new TaskStatus(TaskState.COMPLETED))
                                .isFinal(true).build());
                
                for (Event event : events) {
                    getQueueManager().get(MINIMAL_TASK.getId()).enqueueEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // wait for the client to receive the responses
            taskResubscriptionResponseReceived.await();
            
            assertNotNull(firstResponse.get());
            SendStreamingMessageResponse sendStreamingMessageResponse = firstResponse.get();
            assertNull(sendStreamingMessageResponse.getError());
            TaskArtifactUpdateEvent taskArtifactUpdateEvent = (TaskArtifactUpdateEvent) sendStreamingMessageResponse.getResult();
            assertEquals(MINIMAL_TASK.getId(), taskArtifactUpdateEvent.getTaskId());
            assertEquals(MINIMAL_TASK.getContextId(), taskArtifactUpdateEvent.getContextId());
            Part<?> part = taskArtifactUpdateEvent.getArtifact().parts().get(0);
            assertEquals(Part.Kind.TEXT, part.getKind());
            assertEquals("text", ((TextPart) part).getText());
            
            assertNotNull(secondResponse.get());
            sendStreamingMessageResponse = secondResponse.get();
            assertNull(sendStreamingMessageResponse.getError());
            TaskStatusUpdateEvent taskStatusUpdateEvent = (TaskStatusUpdateEvent) sendStreamingMessageResponse.getResult();
            assertEquals(MINIMAL_TASK.getId(), taskStatusUpdateEvent.getTaskId());
            assertEquals(MINIMAL_TASK.getContextId(), taskStatusUpdateEvent.getContextId());
            assertEquals(TaskState.COMPLETED, taskStatusUpdateEvent.getStatus().state());
            assertNotNull(taskStatusUpdateEvent.getStatus().timestamp());
        } finally {
            setStreamingSubscribedRunnable(null);
            getTaskStore().delete(MINIMAL_TASK.getId());
            executorService.shutdown();
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }
    }
    
    @Test
    public void testResubscribeNoExistingTaskError() throws Exception {
        TaskResubscriptionRequest request = new TaskResubscriptionRequest("1", new TaskIdParams("non-existent-task"));
        
        CompletableFuture<HttpResponse<Stream<String>>> responseFuture = initialiseStreamingRequest(request, null);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        
        responseFuture.thenAccept(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Status code was " + response.statusCode());
            }
            response.body().forEach(line -> {
                try {
                    SendStreamingMessageResponse jsonResponse = extractJsonResponseFromSseLine(line);
                    if (jsonResponse != null) {
                        assertEquals(request.getId(), jsonResponse.getId());
                        assertNull(jsonResponse.getResult());
                        assertInstanceOf(JSONRPCError.class, jsonResponse.getError());
                        assertEquals(new TaskNotFoundError().getCode(), jsonResponse.getError().getCode());
                        latch.countDown();
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
        }).exceptionally(t -> {
            if (!isStreamClosedError(t)) {
                errorRef.set(t);
            }
            latch.countDown();
            return null;
        });
        
        boolean dataRead = latch.await(20, TimeUnit.SECONDS);
        Assertions.assertTrue(dataRead);
        Assertions.assertNull(errorRef.get());
    }
    
    @Test
    public void testStreamingMethodWithAcceptHeader() throws Exception {
        testSendStreamingMessage(MediaType.SERVER_SENT_EVENTS);
    }
    
    @Test
    public void testSendMessageStreamNewMessageSuccess() throws Exception {
        testSendStreamingMessage(null);
    }
    
    private void testSendStreamingMessage(String mediaType) throws Exception {
        Message message = new Message.Builder(MESSAGE).taskId(MINIMAL_TASK.getId())
                .contextId(MINIMAL_TASK.getContextId()).build();
        SendStreamingMessageRequest request = new SendStreamingMessageRequest("1",
                new MessageSendParams(message, null, null));
        
        CompletableFuture<HttpResponse<Stream<String>>> responseFuture = initialiseStreamingRequest(request, mediaType);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        
        responseFuture.thenAccept(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Status code was " + response.statusCode());
            }
            response.body().forEach(line -> {
                try {
                    SendStreamingMessageResponse jsonResponse = extractJsonResponseFromSseLine(line);
                    if (jsonResponse != null) {
                        assertNull(jsonResponse.getError());
                        Message messageResponse = (Message) jsonResponse.getResult();
                        assertEquals(MESSAGE.getMessageId(), messageResponse.getMessageId());
                        assertEquals(MESSAGE.getRole(), messageResponse.getRole());
                        Part<?> part = messageResponse.getParts().get(0);
                        assertEquals(Part.Kind.TEXT, part.getKind());
                        assertEquals("test message", ((TextPart) part).getText());
                        latch.countDown();
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
        }).exceptionally(t -> {
            if (!isStreamClosedError(t)) {
                errorRef.set(t);
            }
            latch.countDown();
            return null;
        });
        
        boolean dataRead = latch.await(20, TimeUnit.SECONDS);
        Assertions.assertTrue(dataRead);
        Assertions.assertNull(errorRef.get());
        
    }
    
    private SendStreamingMessageResponse extractJsonResponseFromSseLine(String line) throws JsonProcessingException {
        line = extractSseData(line);
        if (line != null) {
            return Utils.OBJECT_MAPPER.readValue(line, SendStreamingMessageResponse.class);
        }
        return null;
    }
    
    private static String extractSseData(String line) {
        if (line.startsWith("data:")) {
            line = line.substring(5).trim();
            return line;
        }
        return null;
    }
    
    private boolean isStreamClosedError(Throwable throwable) {
        Throwable cause = throwable;
        
        while (cause != null) {
            if (cause instanceof EOFException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
    
    private CompletableFuture<HttpResponse<Stream<String>>> initialiseStreamingRequest(
            StreamingJSONRPCRequest<?> request, String mediaType) throws Exception {
        
        // Create the client
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
        
        // Create the request
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + serverPort + "/"))
                .POST(HttpRequest.BodyPublishers.ofString(Utils.OBJECT_MAPPER.writeValueAsString(request)))
                .header("Content-Type", "application/json");
        if (mediaType != null) {
            builder.header("Accept", mediaType);
        }
        HttpRequest httpRequest = builder.build();
        
        // Send request async and return the CompletableFuture
        return client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines());
    }
    
    protected abstract TaskStore getTaskStore();
    
    protected abstract InMemoryQueueManager getQueueManager();
    
    protected abstract void setStreamingSubscribedRunnable(Runnable runnable);
    
    private static class BreakException extends RuntimeException {
    
    }
}
