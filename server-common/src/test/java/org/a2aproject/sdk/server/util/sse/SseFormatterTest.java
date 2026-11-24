package org.a2aproject.sdk.server.util.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.junit.jupiter.api.Test;

class SseFormatterTest {

    @Test
    void legacyOverloadUsesHistoricalOneZeroResponseAndVersionedOverloadSelectsOneOne() {
        Task task = Task.builder()
                .id("task-legacy")
                .contextId("context-legacy")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null,
                        OffsetDateTime.parse("2026-11-16T00:00:00Z")))
                .generation(0)
                .timeline(null)
                .build();
        GetTaskResponse response = new GetTaskResponse("legacy-id", task);

        String historicalOneZeroJson = "{\"jsonrpc\":\"2.0\",\"id\":\"legacy-id\",\"result\":"
                + "{\"id\":\"task-legacy\",\"contextId\":\"context-legacy\",\"status\":"
                + "{\"state\":\"TASK_STATE_WORKING\",\"timestamp\":\"2026-11-16T00:00:00Z\"},"
                + "\"artifacts\":[],\"history\":[],\"metadata\":{}}}";

        assertEquals("data: " + historicalOneZeroJson + "\nid: 4\n\n",
                SseFormatter.formatResponseAsSSE(response, 4));

        String oneZero = SseFormatter.formatResponseAsSSE(response, 4, "1.0");
        var oneZeroTask = JsonParser.parseString(oneZero.substring("data: ".length(), oneZero.indexOf("\nid:")))
                .getAsJsonObject().getAsJsonObject("result");
        assertFalse(oneZeroTask.has("generation"));
        assertFalse(oneZeroTask.has("timeline"));

        String oneOne = SseFormatter.formatResponseAsSSE(response, 4, "1.1");
        var oneOneTask = JsonParser.parseString(oneOne.substring("data: ".length(), oneOne.indexOf("\nid:")))
                .getAsJsonObject().getAsJsonObject("result");
        assertEquals(0, oneOneTask.get("generation").getAsLong());
        assertTrue(oneOneTask.get("timeline").getAsJsonArray().isEmpty());
    }

    @Test
    void legacyOverloadRejectsMessageUpdateInsteadOfDroppingTheUnsupportedPayload() {
        TaskMessageUpdateEvent update = new TaskMessageUpdateEvent("task-1", "context-1",
                Message.builder()
                        .role(Message.Role.ROLE_AGENT)
                        .parts(List.of(new TextPart("update")))
                        .messageId("message-1")
                        .build(), null, 0);
        SendStreamingMessageResponse response = new SendStreamingMessageResponse("id", update);

        assertThrows(UnsupportedOperationException.class,
                () -> SseFormatter.formatResponseAsSSE(response, 1));
        assertTrue(SseFormatter.formatResponseAsSSE(response, 1, "1.1").contains("messageUpdate"));
    }

    @Test
    void errorResponsesKeepTheSameJsonRpcErrorBodyForBothOverloads() {
        UnsupportedOperationError error = new UnsupportedOperationError();
        A2AErrorResponse response = new A2AErrorResponse("error-id", error);
        String expected = "data: " + JSONRPCUtils.toJsonRPCErrorResponse("error-id", error) + "\nid: 8\n\n";

        assertEquals(expected, SseFormatter.formatResponseAsSSE(response, 8));
        assertEquals(expected, SseFormatter.formatResponseAsSSE(response, 8, "1.1"));
    }

    @Test
    void directStreamingMessageOmitsTaskId() {
        Message message = Message.builder().messageId("message").contextId("context")
                .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("hello"))).build();
        String event = SseFormatter.formatResponseAsSSE(new SendStreamingMessageResponse("request", message), 0);
        String data = event.lines().filter(line -> line.startsWith("data: ")).findFirst().orElseThrow().substring(6);
        JsonObject result = JsonParser.parseString(data).getAsJsonObject().getAsJsonObject("result")
                .getAsJsonObject("message");
        assertFalse(result.has("taskId"));
        assertEquals("context", result.get("contextId").getAsString());
    }
    @Test
    void taskAssociatedStreamingMessageKeepsTaskId() {
        Message message = Message.builder().messageId("message").taskId("task").contextId("context")
                .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("hello"))).build();
        String event = SseFormatter.formatResponseAsSSE(new SendStreamingMessageResponse("request", message), 0);
        String data = event.lines().filter(line -> line.startsWith("data: ")).findFirst().orElseThrow().substring(6);
        JsonObject result = JsonParser.parseString(data).getAsJsonObject().getAsJsonObject("result")
                .getAsJsonObject("message");
        assertEquals("task", result.get("taskId").getAsString());
    }
}
