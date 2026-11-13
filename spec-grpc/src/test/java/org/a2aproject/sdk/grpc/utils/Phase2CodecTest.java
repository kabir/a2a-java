package org.a2aproject.sdk.grpc.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.Test;

class Phase2CodecTest {

    @Test
    void preservesOptionalZeroPresenceAndInt64JsonEncoding() throws Exception {
        MessageSendConfiguration config = new MessageSendConfiguration(null, 0, null, false, 0L, 0);
        org.a2aproject.sdk.grpc.SendMessageConfiguration encoded = ProtoUtils.ToProto.messageSendConfiguration(config);

        assertTrue(encoded.hasHistoryLength());
        assertEquals(0, encoded.getHistoryLength());
        assertTrue(encoded.hasIfGenerationMatch());
        assertEquals(0L, encoded.getIfGenerationMatch());
        assertTrue(encoded.hasTimelineLength());
        assertEquals(0, encoded.getTimelineLength());

        MessageSendConfiguration decoded = ProtoUtils.FromProto.messageSendConfiguration(encoded);
        assertEquals(0, decoded.historyLength());
        assertEquals(0L, decoded.ifGenerationMatch());
        assertEquals(0, decoded.timelineLength());
        org.a2aproject.sdk.grpc.SendMessageConfiguration absent =
                ProtoUtils.ToProto.messageSendConfiguration(new MessageSendConfiguration(null, null, null, false));
        assertFalse(absent.hasHistoryLength());
        assertFalse(absent.hasIfGenerationMatch());
        assertFalse(absent.hasTimelineLength());

        String json = ProtoJsonUtils.toJson(com.google.protobuf.util.JsonFormat.printer(),
                encoded.toBuilder().setIfGenerationMatch(Long.MAX_VALUE).build());
        assertEquals("9223372036854775807", com.google.gson.JsonParser.parseString(json).getAsJsonObject()
                .get("ifGenerationMatch").getAsString());
    }

    @Test
    void companionInputRetainsBothArtifactShapesButOmitsCallerGeneration() throws Exception {
        TaskArtifactUpdateEvent openingUpdate = new TaskArtifactUpdateEvent("", artifact("open"), "", false, true,
                Map.of("origin", "user"), Long.MIN_VALUE);
        SendInputParams opening = new SendInputParams(message(), List.of(openingUpdate), null, Map.of("trace", "t"), "tenant-a");
        org.a2aproject.sdk.grpc.SendMessageRequest openingWire = ProtoUtils.ToProto.sendInputRequest(opening);
        assertTrue(openingWire.hasMessage());
        assertEquals(1, openingWire.getArtifactUpdatesCount());
        assertEquals(0L, openingWire.getArtifactUpdates(0).getGeneration());
        assertEquals("tenant-a", openingWire.getTenant());
        assertEquals("t", openingWire.getMetadata().getFieldsOrThrow("trace").getStringValue());
        assertEquals(0L, ProtoUtils.FromProto.sendInputParams(openingWire).artifactUpdates().get(0).generation());
        String inputJson = ProtoJsonUtils.toJson(com.google.protobuf.util.JsonFormat.printer(), openingWire);
        assertFalse(inputJson.contains("generation"), inputJson);

        TaskArtifactUpdateEvent existingUpdate = new TaskArtifactUpdateEvent("task-1", artifact("existing"), "ctx-1",
                true, false, null, Long.MAX_VALUE);
        SendInputParams artifactOnly = new SendInputParams(null, List.of(existingUpdate), null, null, null);
        org.a2aproject.sdk.grpc.SendMessageRequest artifactWire = ProtoUtils.ToProto.sendInputRequest(artifactOnly);
        assertFalse(artifactWire.hasMessage());
        assertEquals("task-1", artifactWire.getArtifactUpdates(0).getTaskId());
        assertEquals(0L, artifactWire.getArtifactUpdates(0).getGeneration());
        assertThrows(IllegalArgumentException.class, artifactOnly::requireMessageOnly);
    }

    @Test
    void legacyMessageOnlyEntryPointRemainsAvailable() {
        MessageSendParams legacy = new MessageSendParams(message(), null, null, null);
        org.a2aproject.sdk.grpc.SendMessageRequest wire = ProtoUtils.ToProto.sendMessageRequest(legacy);
        assertTrue(wire.hasMessage());
        Message decoded = ProtoUtils.FromProto.messageSendParams(wire).message();
        assertEquals(legacy.message().messageId(), decoded.messageId());
        assertEquals(legacy.message().role(), decoded.role());
        assertEquals(((TextPart) legacy.message().parts().get(0)).text(), ((TextPart) decoded.parts().get(0)).text());
        assertEquals(legacy.message().messageId(), ProtoUtils.FromProto.sendInputParams(wire).message().messageId());
        org.a2aproject.sdk.grpc.SendMessageRequest companion = ProtoUtils.ToProto.sendInputRequest(
                new SendInputParams(message(), List.of(new TaskArtifactUpdateEvent("task", artifact("a"), "ctx",
                        null, null, null, 0)), null, null, null));
        assertThrows(org.a2aproject.sdk.spec.InvalidParamsError.class,
                () -> ProtoUtils.FromProto.messageSendParams(companion));
        org.a2aproject.sdk.grpc.SubscribeToTaskRequest observed =
                ProtoUtils.ToProto.subscribeToTaskRequest(new org.a2aproject.sdk.spec.ObserveTaskParams("task", 0L, null));
        assertThrows(org.a2aproject.sdk.spec.InvalidParamsError.class,
                () -> ProtoUtils.FromProto.taskIdParams(observed));
    }

    @Test
    void taskTimelineAndArtifactProvenanceRoundTrip() {
        Artifact artifact = new Artifact("a", "name", null, List.of(new TextPart("body")), Map.of(), List.of(),
                Long.MIN_VALUE, Long.MAX_VALUE, Message.Role.ROLE_USER);
        org.a2aproject.sdk.spec.Task task = new org.a2aproject.sdk.spec.Task("task", "ctx",
                new TaskStatus(TaskState.TASK_STATE_WORKING), List.of(artifact), List.of(), Map.of(), Long.MAX_VALUE,
                List.of(new TimelineEntry(Long.MIN_VALUE, new TimelineEntry.MessageValue(message()))));
        org.a2aproject.sdk.grpc.Task wire = ProtoUtils.ToProto.task(task);

        assertEquals(Long.MAX_VALUE, wire.getGeneration());
        assertEquals(Long.MIN_VALUE, wire.getArtifacts(0).getStartGeneration());
        assertEquals(Long.MAX_VALUE, wire.getArtifacts(0).getEndGeneration());
        assertEquals(org.a2aproject.sdk.grpc.Role.ROLE_USER, wire.getArtifacts(0).getRole());
        assertEquals(Long.MIN_VALUE, wire.getTimeline(0).getGeneration());
        assertEquals(org.a2aproject.sdk.grpc.TimelineEntry.EntryCase.MESSAGE, wire.getTimeline(0).getEntryCase());
        org.a2aproject.sdk.spec.Task decoded = ProtoUtils.FromProto.task(wire);
        assertEquals(task.generation(), decoded.generation());
        assertEquals(1, decoded.timeline().size());
        assertEquals(Long.MIN_VALUE, decoded.timeline().get(0).generation());
        Message timelineMessage = ((TimelineEntry.MessageValue) decoded.timeline().get(0).entry()).message();
        assertEquals(message().messageId(), timelineMessage.messageId());
        assertEquals(artifact.role(), decoded.artifacts().get(0).role());
    }

    @Test
    void observeCursorAndTaskQueryFieldsPreserveZeroPresence() throws Exception {
        org.a2aproject.sdk.grpc.SubscribeToTaskRequest observe = ProtoUtils.ToProto.subscribeToTaskRequest(
                new org.a2aproject.sdk.spec.ObserveTaskParams("task", 0L, "tenant"));
        assertTrue(observe.hasLastSeenGeneration());
        assertEquals(0L, observe.getLastSeenGeneration());
        assertEquals(0L, ProtoUtils.FromProto.observeTaskParams(observe).lastSeenGeneration());

        org.a2aproject.sdk.grpc.GetTaskRequest query = ProtoUtils.ToProto.getTaskRequest(
                new org.a2aproject.sdk.spec.TaskQueryParams("task", null, "tenant", 0L, 0));
        assertTrue(query.hasCurrentGeneration());
        assertTrue(query.hasTimelineLength());
        assertEquals(0L, query.getCurrentGeneration());
        assertEquals(0, query.getTimelineLength());

        String requestJson = JSONRPCUtils.toJsonRPCRequest("request-1",
                org.a2aproject.sdk.spec.A2AMethods.SEND_MESSAGE_METHOD,
                ProtoUtils.ToProto.sendInputRequest(new SendInputParams(null,
                        List.of(new TaskArtifactUpdateEvent("task", artifact("a"), "ctx", null, null, null, 17)),
                        null, null, null)));
        org.a2aproject.sdk.jsonrpc.common.wrappers.A2ARequest<?> parsed =
                JSONRPCUtils.parseRequestBody(requestJson, null, "1.1");
        org.a2aproject.sdk.jsonrpc.common.wrappers.SendInputRequest companion =
                assertInstanceOf(org.a2aproject.sdk.jsonrpc.common.wrappers.SendInputRequest.class, parsed);
        assertEquals(0L, companion.getParams().artifactUpdates().get(0).generation());

        String observeJson = JSONRPCUtils.toJsonRPCRequest("request-2",
                org.a2aproject.sdk.spec.A2AMethods.SUBSCRIBE_TO_TASK_METHOD,
                ProtoUtils.ToProto.subscribeToTaskRequest(
                        new org.a2aproject.sdk.spec.ObserveTaskParams("task", 0L, "tenant")));
        org.a2aproject.sdk.jsonrpc.common.wrappers.A2ARequest<?> observeParsed =
                JSONRPCUtils.parseRequestBody(observeJson, null, "1.1");
        org.a2aproject.sdk.jsonrpc.common.wrappers.ObserveTaskRequest observeCompanion =
                assertInstanceOf(org.a2aproject.sdk.jsonrpc.common.wrappers.ObserveTaskRequest.class, observeParsed);
        assertEquals(0L, observeCompanion.getParams().lastSeenGeneration());
        assertThrows(org.a2aproject.sdk.jsonrpc.common.json.JsonMappingException.class,
                () -> JSONRPCUtils.parseRequestBody(observeJson, null));
    }

    @Test
    void mapsVersionFieldsAndBothCompanionStreamOneofs() {
        org.a2aproject.sdk.spec.TaskStatusUpdateEvent statusEvent =
                new org.a2aproject.sdk.spec.TaskStatusUpdateEvent("task", new TaskStatus(TaskState.TASK_STATE_WORKING),
                        "ctx", null, Long.MAX_VALUE);
        org.a2aproject.sdk.grpc.TaskStatusUpdateEvent statusWire =
                ProtoUtils.ToProto.taskStatusUpdateEvent(statusEvent);
        assertEquals(Long.MAX_VALUE, statusWire.getGeneration());
        assertEquals(Long.MAX_VALUE, ProtoUtils.FromProto.taskStatusUpdateEvent(statusWire).generation());

        TaskArtifactUpdateEvent artifactEvent = new TaskArtifactUpdateEvent("task", artifact("artifact"), "ctx",
                true, false, Map.of("k", "v"), Long.MIN_VALUE);
        org.a2aproject.sdk.grpc.TaskArtifactUpdateEvent artifactWire =
                ProtoUtils.ToProto.taskArtifactUpdateEvent(artifactEvent);
        assertEquals(Long.MIN_VALUE, artifactWire.getGeneration());
        assertEquals(Long.MIN_VALUE, ProtoUtils.FromProto.taskArtifactUpdateEvent(artifactWire).generation());

        org.a2aproject.sdk.spec.AgentCapabilities capabilities =
                org.a2aproject.sdk.spec.AgentCapabilities.builder().userArtifacts(true).build();
        org.a2aproject.sdk.grpc.AgentCapabilities capabilitiesWire =
                org.a2aproject.sdk.grpc.mapper.AgentCapabilitiesMapper.INSTANCE.toProto(capabilities);
        assertTrue(capabilitiesWire.getUserArtifacts());
        assertTrue(org.a2aproject.sdk.grpc.mapper.AgentCapabilitiesMapper.INSTANCE.fromProto(capabilitiesWire)
                .userArtifacts());

        org.a2aproject.sdk.spec.TaskPushNotificationConfig pushConfig =
                new org.a2aproject.sdk.spec.TaskPushNotificationConfig("cfg", "task", "https://example.test", null,
                        null, "tenant", "1.1");
        org.a2aproject.sdk.grpc.TaskPushNotificationConfig pushWire =
                ProtoUtils.ToProto.taskPushNotificationConfig(pushConfig);
        assertEquals("1.1", pushWire.getProtocolVersion());
        assertEquals("1.1", ProtoUtils.FromProto.taskPushNotificationConfig(pushWire).protocolVersion());

        org.a2aproject.sdk.spec.StreamRequest send = new org.a2aproject.sdk.spec.StreamRequest("tenant",
                new org.a2aproject.sdk.spec.StreamRequest.Send(new SendInputParams(message(), List.of(), null, null, null)));
        org.a2aproject.sdk.grpc.StreamRequest sendWire = ProtoUtils.ToProto.streamRequest(send);
        assertEquals(org.a2aproject.sdk.grpc.StreamRequest.PayloadCase.SEND_MESSAGE, sendWire.getPayloadCase());
        assertEquals("tenant", ProtoUtils.FromProto.streamRequest(sendWire).tenant());

        org.a2aproject.sdk.spec.StreamRequest subscribe = new org.a2aproject.sdk.spec.StreamRequest(null,
                new org.a2aproject.sdk.spec.StreamRequest.Subscribe(
                        new org.a2aproject.sdk.spec.ObserveTaskParams("task", Long.MAX_VALUE, null)));
        org.a2aproject.sdk.grpc.StreamRequest subscribeWire = ProtoUtils.ToProto.streamRequest(subscribe);
        assertEquals(org.a2aproject.sdk.grpc.StreamRequest.PayloadCase.SUBSCRIBE, subscribeWire.getPayloadCase());
        assertEquals(Long.MAX_VALUE,
                ProtoUtils.FromProto.streamRequest(subscribeWire).payload() instanceof org.a2aproject.sdk.spec.StreamRequest.Subscribe decoded
                        ? decoded.subscribe().lastSeenGeneration() : null);

        org.a2aproject.sdk.spec.StreamError streamError =
                new org.a2aproject.sdk.spec.StreamError("task", "ABORTED", "retry", "message", Map.of("x", 1));
        org.a2aproject.sdk.spec.StreamError decodedError =
                ProtoUtils.FromProto.streamError(ProtoUtils.ToProto.streamError(streamError));
        assertEquals(streamError.taskId(), decodedError.taskId());
        assertEquals(streamError.code(), decodedError.code());
        assertEquals(streamError.message(), decodedError.message());
        assertEquals(streamError.referenceId(), decodedError.referenceId());
        assertEquals("1.0", decodedError.metadata().get("x").toString());
    }

    private static Message message() {
        return Message.builder().role(Message.Role.ROLE_USER).parts(List.of(new TextPart("input")))
                .messageId("message-1").build();
    }

    private static Artifact artifact(String id) {
        return new Artifact(id, null, null, List.of(new TextPart("artifact")), null, null);
    }
}
