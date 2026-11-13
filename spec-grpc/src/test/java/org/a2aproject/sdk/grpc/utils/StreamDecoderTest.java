package org.a2aproject.sdk.grpc.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.protobuf.UnknownFieldSet;
import org.a2aproject.sdk.spec.StreamError;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.junit.jupiter.api.Test;

class StreamDecoderTest {

    @Test
    void skipsUnknownProtobufOneofButKeepsStreamErrorSeparate() {
        org.a2aproject.sdk.grpc.StreamResponse unknown = org.a2aproject.sdk.grpc.StreamResponse.newBuilder()
                .setUnknownFields(UnknownFieldSet.newBuilder().addField(99,
                        UnknownFieldSet.Field.newBuilder().addVarint(1).build()).build())
                .build();
        StreamDecodeResult.Ignored ignored = assertInstanceOf(StreamDecodeResult.Ignored.class,
                StreamDecoder.decode(unknown, "1.1"));
        assertTrue(ignored.notes().unknownSkipped());
        assertInstanceOf(StreamDecodeResult.Malformed.class,
                StreamDecoder.decode(org.a2aproject.sdk.grpc.StreamResponse.getDefaultInstance(), "1.1"));
        assertInstanceOf(StreamDecodeResult.Malformed.class, StreamDecoder.decodeJson("{}", "1.1"));

        org.a2aproject.sdk.grpc.TimelineEntry futureTimeline = org.a2aproject.sdk.grpc.TimelineEntry.newBuilder()
                .setGeneration(19).setUnknownFields(UnknownFieldSet.newBuilder().addField(77,
                        UnknownFieldSet.Field.newBuilder().addVarint(1).build()).build()).build();
        org.a2aproject.sdk.grpc.Task task = org.a2aproject.sdk.grpc.Task.newBuilder().setId("t").setContextId("c")
                .setStatus(org.a2aproject.sdk.grpc.TaskStatus.newBuilder()
                        .setState(org.a2aproject.sdk.grpc.TaskState.TASK_STATE_WORKING))
                .addTimeline(futureTimeline).build();
        DecodedTask decodedTask = StreamDecoder.decodeTask(task, "1.1");
        assertEquals(List.of(19L), decodedTask.notes().skippedTimelineGenerations());
        assertTrue(decodedTask.task().timeline().isEmpty());

        org.a2aproject.sdk.grpc.StreamResponse error = org.a2aproject.sdk.grpc.StreamResponse.newBuilder()
                .setError(org.a2aproject.sdk.grpc.StreamError.newBuilder().setCode("ABORTED")
                        .setMessage("generation mismatch").setReferenceId("m-1"))
                .build();
        StreamDecodeResult.LocalRejection rejection = assertInstanceOf(StreamDecodeResult.LocalRejection.class,
                StreamDecoder.decode(error, "1.1"));
        assertEquals(new StreamError(null, "ABORTED", "generation mismatch", "m-1", null), rejection.error());
    }

    @Test
    void decodesKnownEventsAroundUnknownJsonVariant() {
        String status = "{\"statusUpdate\":{\"taskId\":\"t\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},\"generation\":\"7\"}}";
        String future = "{\"futureEvent\":{\"generation\":\"8\"}}";
        String message = "{\"messageUpdate\":{\"taskId\":\"t\",\"contextId\":\"c\",\"message\":{\"role\":\"ROLE_USER\",\"messageId\":\"m\",\"parts\":[{\"text\":\"hello\"}]},\"generation\":\"9\"}}";

        assertInstanceOf(StreamDecodeResult.Known.class, StreamDecoder.decodeJson(status, "1.1"));
        assertInstanceOf(StreamDecodeResult.Malformed.class,
                StreamDecoder.decodeJson("{\"messageUpdate\":{}}", "1.0"));
        assertThrows(IllegalArgumentException.class,
                () -> StreamDecoder.decodeTaskJson("{\"id\":\"t\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},\"generation\":\"1\"}", "1.0"));
        org.a2aproject.sdk.grpc.Task legacyWire = org.a2aproject.sdk.grpc.Task.newBuilder().setId("t")
                .setContextId("c").setGeneration(8)
                .setStatus(org.a2aproject.sdk.grpc.TaskStatus.newBuilder()
                        .setState(org.a2aproject.sdk.grpc.TaskState.TASK_STATE_WORKING))
                .addTimeline(org.a2aproject.sdk.grpc.TimelineEntry.newBuilder().setGeneration(8)
                        .setMessage(org.a2aproject.sdk.grpc.Message.newBuilder().setMessageId("m")
                                .setRole(org.a2aproject.sdk.grpc.Role.ROLE_USER)
                                .addParts(org.a2aproject.sdk.grpc.Part.newBuilder().setText("input"))))
                .addArtifacts(org.a2aproject.sdk.grpc.Artifact.newBuilder().setArtifactId("a")
                        .addParts(org.a2aproject.sdk.grpc.Part.newBuilder().setText("body"))
                        .setStartGeneration(7).setEndGeneration(8).setRole(org.a2aproject.sdk.grpc.Role.ROLE_USER))
                .build();
        DecodedTask legacyDecoded = StreamDecoder.decodeTask(legacyWire, "1.0");
        assertEquals(0L, legacyDecoded.task().generation());
        assertTrue(legacyDecoded.task().timeline().isEmpty());
        assertEquals(0L, legacyDecoded.task().artifacts().get(0).startGeneration());
        assertNull(legacyDecoded.task().artifacts().get(0).role());
        assertTrue(((StreamDecodeResult.Ignored) StreamDecoder.decodeJson(future, "1.1")).notes().unknownSkipped());
        assertInstanceOf(StreamDecodeResult.Known.class, StreamDecoder.decodeJson(message, "1.1"));
    }

    @Test
    void decodesStreamTaskSnapshotsWithUnknownTimelineEvidence() throws Exception {
        String task = "{\"id\":\"t\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},\"timeline\":[{\"generation\":\"8\",\"futureEntry\":{}}]}";
        StreamDecodeResult.Known direct = assertInstanceOf(StreamDecodeResult.Known.class,
                StreamDecoder.decodeJson("{\"task\":" + task + "}", "1.1"));
        assertEquals(List.of(8L), direct.notes().skippedTimelineGenerations());
        assertInstanceOf(org.a2aproject.sdk.spec.Task.class, direct.event());

        StreamDecodeResult.Known enveloped = assertInstanceOf(StreamDecodeResult.Known.class,
                JSONRPCUtils.decodeResponseEvent(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"r\",\"result\":{\"task\":" + task + "}}", "1.1"));
        assertEquals(List.of(8L), enveloped.notes().skippedTimelineGenerations());
        assertInstanceOf(org.a2aproject.sdk.spec.Task.class, enveloped.event());
    }

    @Test
    void correlatesStreamingResponseIdsBeforeDecodingResultsOrErrors() throws Exception {
        String localRejection = "{\"jsonrpc\":\"2.0\",\"id\":\"request-1\",\"result\":{\"error\":{\"code\":\"ABORTED\",\"message\":\"stale\"}}}";
        assertInstanceOf(StreamDecodeResult.LocalRejection.class,
                JSONRPCUtils.decodeResponseEvent(localRejection, "1.1", "request-1"));

        String[] invalidIdFields = {
                "\"id\":\"other-request\",", "", "\"id\":null,", "\"id\":{},",
                "\"id\":true,", "\"id\":[],", "\"id\":17,"
        };
        String[] responsePayloads = {
                "\"result\":{\"futureEvent\":{}},",
                "\"error\":{\"code\":-32010,\"message\":\"generation mismatch\"},"
        };
        for (String idField : invalidIdFields) {
            for (String payload : responsePayloads) {
                String envelope = "{\"jsonrpc\":\"2.0\", " + idField + payload.substring(0, payload.length() - 1) + "}";
                assertThrows(org.a2aproject.sdk.jsonrpc.common.json.JsonMappingException.class,
                        () -> JSONRPCUtils.decodeResponseEvent(envelope, "1.1", "request-1"));
            }
        }

        String matchingError = "{\"jsonrpc\":\"2.0\",\"id\":\"request-1\",\"error\":{\"code\":-32010,\"message\":\"generation mismatch\"}}";
        assertInstanceOf(TaskGenerationMismatchError.class,
                assertThrows(RuntimeException.class,
                        () -> JSONRPCUtils.decodeResponseEvent(matchingError, "1.1", "request-1")));
    }

    @Test
    void recognizesProtoJsonFieldAliasesAsKnownStreamPayloads() {
        String status = "{\"status_update\":{\"taskId\":\"t\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}";
        String artifact = "{\"artifact_update\":{\"taskId\":\"t\",\"contextId\":\"c\",\"artifact\":{\"artifactId\":\"a\",\"parts\":[{\"text\":\"body\"}]}}}";
        String message = "{\"message_update\":{\"taskId\":\"t\",\"contextId\":\"c\",\"message\":{\"role\":\"ROLE_USER\",\"messageId\":\"m\",\"parts\":[{\"text\":\"input\"}]}}}";
        assertInstanceOf(StreamDecodeResult.Known.class, StreamDecoder.decodeJson(status, "1.1"));
        assertInstanceOf(StreamDecodeResult.Known.class, StreamDecoder.decodeJson(artifact, "1.1"));
        assertInstanceOf(StreamDecodeResult.Known.class, StreamDecoder.decodeJson(message, "1.1"));

        assertInstanceOf(StreamDecodeResult.Malformed.class,
                StreamDecoder.decodeJson("{\"status_update\":{}}", "1.1"));
        assertInstanceOf(StreamDecodeResult.Malformed.class,
                StreamDecoder.decodeJson("{\"artifact_update\":{}}", "1.1"));
        assertInstanceOf(StreamDecodeResult.Malformed.class,
                StreamDecoder.decodeJson("{\"message_update\":{}}", "1.1"));
        String validStatus = "\"statusUpdate\":{" + status.substring("{\"status_update\":".length(), status.length() - 1);
        assertInstanceOf(StreamDecodeResult.Malformed.class,
                StreamDecoder.decodeJson("{" + validStatus + ",\"status_update\":{" + status.substring(status.indexOf(':') + 1, status.length() - 1) + "}}", "1.1"));
        String mixedKnownAliases = "{\"statusUpdate\":"
                + status.substring(status.indexOf(':') + 1, status.length() - 1)
                + ",\"artifact_update\":"
                + artifact.substring(artifact.indexOf(':') + 1, artifact.length() - 1) + "}";
        assertInstanceOf(StreamDecodeResult.Malformed.class, StreamDecoder.decodeJson(mixedKnownAliases, "1.1"));
    }

    @Test
    void decodesOmittedTaskListAsEmptyButRejectsPresentNonArray() throws Exception {
        String protobufJson = ProtoJsonUtils.toJson(com.google.protobuf.util.JsonFormat.printer(),
                org.a2aproject.sdk.grpc.ListTasksResponse.newBuilder().setPageSize(10).build());
        assertFalse(com.google.gson.JsonParser.parseString(protobufJson).getAsJsonObject().has("tasks"));
        for (String version : List.of("1.0", "1.1")) {
            assertTrue(StreamDecoder.decodeTaskListJson(protobufJson, version).tasks().isEmpty());
            DecodedTaskList result = StreamDecoder.decodeTaskListJson(
                    "{\"pageSize\":10,\"nextPageToken\":\"next\"}", version);
            assertTrue(result.tasks().isEmpty());
            assertEquals("next", result.value().nextPageToken());
        }
        assertThrows(IllegalArgumentException.class,
                () -> StreamDecoder.decodeTaskListJson("{\"tasks\":null}", "1.1"));
        assertThrows(IllegalArgumentException.class,
                () -> StreamDecoder.decodeTaskListJson("{\"tasks\":10}", "1.0"));
        DecodedTaskList enveloped = JSONRPCUtils.decodeListTasksResponse(
                "{\"jsonrpc\":\"2.0\",\"id\":\"r\",\"result\":{\"pageSize\":10,\"nextPageToken\":\"next\"}}",
                "1.1");
        assertTrue(enveloped.tasks().isEmpty());
        assertEquals("next", enveloped.value().nextPageToken());
    }

    @Test
    void collectsUnknownTimelineGenerationsPerTaskAndPreservesInt64() {
        String json = "{\"id\":\"t\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},\"generation\":\"9223372036854775807\",\"timeline\":[{\"generation\":\"9223372036854775806\",\"futureEntry\":{}},{\"generation\":\"0\",\"message\":{\"role\":\"ROLE_USER\",\"messageId\":\"m\",\"parts\":[{\"text\":\"hello\"}]}}]}";
        DecodedTask decoded = StreamDecoder.decodeTaskJson(json, "1.1");
        assertEquals(Long.MAX_VALUE, decoded.task().generation());
        assertEquals(List.of(Long.MAX_VALUE - 1), decoded.notes().skippedTimelineGenerations());
        assertEquals(1, decoded.task().timeline().size());
        assertEquals(0L, decoded.task().timeline().get(0).generation());

        String list = "{\"tasks\":[{\"id\":\"a\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},\"timeline\":[{\"generation\":\"11\",\"futureEntry\":{}}]},{\"id\":\"b\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},\"timeline\":[]}],\"nextPageToken\":\"next\"}";
        DecodedTaskList decodedList = StreamDecoder.decodeTaskListJson(list, "1.1");
        assertEquals("next", decodedList.value().nextPageToken());
        assertEquals(List.of(11L), decodedList.tasks().get(0).notes().skippedTimelineGenerations());
        assertTrue(decodedList.tasks().get(1).notes().skippedTimelineGenerations().isEmpty());
    }

    @Test
    void doesNotHideMalformedKnownTimelineEntriesOrRpcErrors() throws Exception {
        String malformed = "{\"id\":\"t\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},\"timeline\":[{\"generation\":\"2\",\"futureEntry\":{}},{\"generation\":\"3\",\"message\":{}}]}";
        assertThrows(RuntimeException.class, () -> StreamDecoder.decodeTaskJson(malformed, "1.1"));
        String multiple = "{\"id\":\"t\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},\"timeline\":[{\"generation\":\"4\",\"message\":{\"role\":\"ROLE_USER\",\"messageId\":\"m\",\"parts\":[{\"text\":\"x\"}]},\"status\":{\"state\":\"TASK_STATE_WORKING\"}}]}";
        assertThrows(RuntimeException.class, () -> StreamDecoder.decodeTaskJson(multiple, "1.1"));
        assertInstanceOf(StreamDecodeResult.Malformed.class,
                StreamDecoder.decodeJson("{\"statusUpdate\":{},\"artifactUpdate\":{}}", "1.1"));
        assertInstanceOf(StreamDecodeResult.Malformed.class,
                StreamDecoder.decodeJson("{\"statusUpdate\":{}}", "1.1"));
        assertInstanceOf(StreamDecodeResult.Malformed.class,
                StreamDecoder.decodeJson("{\"messageUpdate\":{\"taskId\":\"t\",\"contextId\":\"c\"}}", "1.1"));
        assertThrows(IllegalArgumentException.class,
                () -> StreamDecoder.decodeSendResponseJson("{\"message\":{}}", "1.1"));

        String error = "{\"jsonrpc\":\"2.0\",\"id\":\"r\",\"error\":{\"code\":-32010,\"message\":\"generation mismatch\"}}";
        assertInstanceOf(TaskGenerationMismatchError.class,
                assertThrows(RuntimeException.class, () -> JSONRPCUtils.decodeResponseEvent(error, "1.1")));
        assertInstanceOf(TaskGenerationMismatchError.class,
                assertThrows(RuntimeException.class, () -> JSONRPCUtils.decodeTaskResponse(error, "1.1")));

        String taskResult = "{\"id\":\"t\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},\"timeline\":[{\"generation\":\"23\",\"futureEntry\":{}}]}";
        DecodedTask unary = JSONRPCUtils.decodeTaskResponse(
                "{\"jsonrpc\":\"2.0\",\"id\":\"r\",\"result\":" + taskResult + "}", "1.1");
        assertEquals(List.of(23L), unary.notes().skippedTimelineGenerations());
        DecodedTaskList unaryList = JSONRPCUtils.decodeListTasksResponse(
                "{\"jsonrpc\":\"2.0\",\"id\":\"r\",\"result\":{\"tasks\":[" + taskResult
                        + "],\"nextPageToken\":\"p\"}}", "1.1");
        assertEquals("p", unaryList.value().nextPageToken());
        assertEquals(List.of(23L), unaryList.tasks().get(0).notes().skippedTimelineGenerations());

        assertTrue(StreamDecoder.decode(org.a2aproject.sdk.grpc.StreamResponse.newBuilder()
                .setStatusUpdate(org.a2aproject.sdk.grpc.TaskStatusUpdateEvent.newBuilder().setTaskId("t")
                        .setContextId("c").setStatus(org.a2aproject.sdk.grpc.TaskStatus.newBuilder()
                                .setState(org.a2aproject.sdk.grpc.TaskState.TASK_STATE_WORKING)).build()).build(), "1.1")
                instanceof StreamDecodeResult.Known);
    }

    @Test
    void rejectsTaskArtifactProvenanceAliasesAcrossExplicitV10JsonEntryPoints() throws Exception {
        for (String field : List.of("startGeneration", "start_generation", "endGeneration", "end_generation")) {
            String task = taskJsonWithArtifactField(field);
            String taskEnvelope = jsonRpcEnvelope(task);
            assertThrows(IllegalArgumentException.class, () -> StreamDecoder.decodeTaskJson(task, "1.0"), field);
            assertThrows(IllegalArgumentException.class,
                    () -> JSONRPCUtils.decodeTaskResponse(taskEnvelope, "1.0"), field);
            assertThrows(IllegalArgumentException.class,
                    () -> JSONRPCUtils.decodeListTasksResponse(
                            jsonRpcEnvelope("{\"tasks\":[" + task + "]}"), "1.0"), field);
            assertThrows(IllegalArgumentException.class,
                    () -> JSONRPCUtils.decodeSendResponse(
                            jsonRpcEnvelope("{\"task\":" + task + "}"), "1.0"), field);
            String stream = "{\"task\":" + task + "}";
            assertInstanceOf(StreamDecodeResult.Malformed.class, StreamDecoder.decodeJson(stream, "1.0"), field);
            assertInstanceOf(StreamDecodeResult.Malformed.class,
                    JSONRPCUtils.decodeResponseEvent(jsonRpcEnvelope(stream), "1.0"), field);

            DecodedTask decoded11 = StreamDecoder.decodeTaskJson(task, "1.1");
            org.a2aproject.sdk.spec.Artifact artifact = decoded11.task().artifacts().get(0);
            assertEquals(field.startsWith("start") ? 7L : 0L, artifact.startGeneration(), field);
            assertEquals(field.startsWith("end") ? 7L : 0L, artifact.endGeneration(), field);
        }

        String legacyTask = legacyTaskJson();
        assertEquals("t", StreamDecoder.decodeTaskJson(legacyTask, "1.0").task().id());
        assertEquals("t", JSONRPCUtils.decodeTaskResponse(jsonRpcEnvelope(legacyTask), "1.0").task().id());
        assertEquals("t", JSONRPCUtils.decodeListTasksResponse(
                jsonRpcEnvelope("{\"tasks\":[" + legacyTask + "]}"), "1.0").tasks().get(0).task().id());
        DecodedEvent legacySend = JSONRPCUtils.decodeSendResponse(
                jsonRpcEnvelope("{\"task\":" + legacyTask + "}"), "1.0");
        DecodedTask legacySendTask = assertInstanceOf(DecodedTask.class, legacySend.task());
        assertEquals("t", legacySendTask.task().id());
        String legacyStream = "{\"task\":" + legacyTask + "}";
        assertInstanceOf(StreamDecodeResult.Known.class, StreamDecoder.decodeJson(legacyStream, "1.0"));
        assertInstanceOf(StreamDecodeResult.Known.class,
                JSONRPCUtils.decodeResponseEvent(jsonRpcEnvelope(legacyStream), "1.0"));
    }

    @Test
    void rejectsArtifactUpdateProvenanceAliasesInExplicitV10Streams() throws Exception {
        for (String field : List.of("startGeneration", "start_generation", "endGeneration", "end_generation")) {
            String artifact = "{\"artifactId\":\"a\",\"parts\":[{\"text\":\"body\"}],\""
                    + field + "\":\"7\"}";
            String update = "{\"artifactUpdate\":{\"taskId\":\"t\",\"contextId\":\"c\",\"artifact\":"
                    + artifact + "}}";
            assertInstanceOf(StreamDecodeResult.Malformed.class, StreamDecoder.decodeJson(update, "1.0"), field);
            assertInstanceOf(StreamDecodeResult.Malformed.class,
                    JSONRPCUtils.decodeResponseEvent(jsonRpcEnvelope(update), "1.0"), field);
            assertInstanceOf(StreamDecodeResult.Known.class, StreamDecoder.decodeJson(update, "1.1"), field);
            assertInstanceOf(StreamDecodeResult.Known.class,
                    JSONRPCUtils.decodeResponseEvent(jsonRpcEnvelope(update), "1.1"), field);
        }

        String legacyUpdate = "{\"artifactUpdate\":{\"taskId\":\"t\",\"contextId\":\"c\","
                + "\"artifact\":{\"artifactId\":\"a\",\"parts\":[{\"text\":\"body\"}]}}}";
        assertInstanceOf(StreamDecodeResult.Known.class, StreamDecoder.decodeJson(legacyUpdate, "1.0"));
        assertInstanceOf(StreamDecodeResult.Known.class,
                JSONRPCUtils.decodeResponseEvent(jsonRpcEnvelope(legacyUpdate), "1.0"));
    }

    private static String taskJsonWithArtifactField(String provenanceField) {
        return "{\"id\":\"t\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},"
                + "\"artifacts\":[{\"artifactId\":\"a\",\"parts\":[{\"text\":\"body\"}],\""
                + provenanceField + "\":\"7\"}]}";
    }

    private static String legacyTaskJson() {
        return "{\"id\":\"t\",\"contextId\":\"c\",\"status\":{\"state\":\"TASK_STATE_WORKING\"},"
                + "\"artifacts\":[{\"artifactId\":\"a\",\"parts\":[{\"text\":\"body\"}]}]}";
    }

    private static String jsonRpcEnvelope(String result) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"r\",\"result\":" + result + "}";
    }
}
