package org.a2aproject.sdk.client.transport.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.google.rpc.ErrorInfo;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.GetTaskRequest;
import org.a2aproject.sdk.grpc.ListTasksRequest;
import org.a2aproject.sdk.grpc.SendMessageRequest;
import org.a2aproject.sdk.grpc.SendMessageResponse;
import org.a2aproject.sdk.grpc.StreamResponse;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class Phase2PublicClientTest {

    private static final Metadata.Key<String> VERSION_KEY = Metadata.Key.of("a2a-version",
            Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void publicReadsAndWrappedDecodedReadsRetainPerTaskTimelineEvidence() throws Exception {
        try (GrpcPeer peer = new GrpcPeer()) {
            GrpcTransport transport = transport(peer.card(true, true), "1.1");

            assertEquals(Set.of(ClientFeature.ORDINARY, ClientFeature.GUARDED_INPUT, ClientFeature.ARTIFACT_INPUT,
                    ClientFeature.UNKNOWN_VARIANT_SKIP), transport.capabilities());
            assertEquals(Set.of(ClientFeature.ORDINARY), new GrpcTransportProvider().capabilities().get("1.0"));
            assertEquals(transport.capabilities(), new GrpcTransportProvider().capabilities().get("1.1"));

            Task taskA = transport.getTaskDecoded(new TaskQueryParams("task-a", 5, null, null, 3), null).value();
            assertEquals("task-a", taskA.id());
            assertEquals(List.of(7L, 9L), taskA.timeline().stream().map(TimelineEntry::generation).toList());
            assertTrue(peer.getTaskRequest.get().hasHistoryLength());
            assertEquals(5, peer.getTaskRequest.get().getHistoryLength());
            assertEquals(3, peer.getTaskRequest.get().getTimelineLength());
            assertEquals("tenant-default", peer.getTaskRequest.get().getTenant());

            Task legacyMethodTask = transport.getTask(new TaskQueryParams("task-a"), null);
            assertEquals(List.of(7L, 9L), legacyMethodTask.timeline().stream()
                    .map(TimelineEntry::generation).toList());

            var listed = transport.listTasksDecoded(ListTasksParams.builder().contextId("context").pageSize(2)
                    .pageToken("page-1").historyLength(4).timelineLength(6).includeArtifacts(true).build(), null).value();
            assertEquals(List.of("task-a", "task-b"), listed.tasks().stream().map(Task::id).toList());
            assertEquals(2, listed.pageSize());
            assertEquals(2, listed.totalSize());
            assertEquals("page-next", listed.nextPageToken());
            assertEquals("tenant-default", peer.listTasksRequest.get().getTenant());
            assertEquals(6, peer.listTasksRequest.get().getTimelineLength());

            TransportResult<Task> decodedA = transport.getTaskDecoded(new TaskQueryParams("task-a"), null);
            TransportResult<org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult> decodedList =
                    transport.listTasksDecoded(new ListTasksParams(2, "page-1"), null);
            TransportResult<Task> asyncB = transport.getTaskAsync(new TaskQueryParams("task-b"),
                    SpiCallOptions.defaults(), null).result().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(new TaskDecodeEvidence("task-a", List.of(8L), true), decodedA.taskEvidence().get(0));
            assertEquals(List.of(new TaskDecodeEvidence("task-a", List.of(8L), true),
                    new TaskDecodeEvidence("task-b", List.of(18L), true)), decodedList.taskEvidence());
            assertEquals(new TaskDecodeEvidence("task-b", List.of(18L), true), asyncB.taskEvidence().get(0));
            assertEquals("page-next", decodedList.value().nextPageToken());

            TransportResult<Task> fullyKnown = transport.getTaskDecoded(new TaskQueryParams("known"), null);
            assertEquals(List.of(), fullyKnown.taskEvidence().get(0).skippedTimelineGenerations());
            assertFalse(fullyKnown.taskEvidence().get(0).unknownSkipped());
            assertThrows(org.a2aproject.sdk.spec.A2AClientException.class,
                    () -> transport.getTaskDecoded(new TaskQueryParams("malformed"), null));
            assertThrows(UnsupportedOperationException.class, () -> transport.getTaskDecoded(
                    new TaskQueryParams("task-a", null, null, 7L, null), null));
            assertTrue(peer.versions.stream().allMatch("1.1"::equals));
            transport.close();
        }
    }

    @Test
    void publicUnaryInputPreservesGuardArtifactTenantAndTypedMismatch() throws Exception {
        try (GrpcPeer peer = new GrpcPeer()) {
            GrpcTransport transport = transport(peer.card(true, true), "1.1");
            TaskArtifactUpdateEvent artifactUpdate = TaskArtifactUpdateEvent.builder()
                    .taskId("task-target").contextId("context-task-target")
                    .artifact(Artifact.builder().artifactId("user-artifact").parts(List.of(new TextPart("input")))
                            .role(Message.Role.ROLE_USER).build())
                    .generation(77).build();
            MessageSendConfiguration configuration = MessageSendConfiguration.builder()
                    .ifGenerationMatch(12L).timelineLength(4).build();
            SendInputParams input = SendInputParams.builder().artifactUpdates(List.of(artifactUpdate))
                    .configuration(configuration).metadata(Map.of("request", "metadata"))
                    .tenant("tenant-request").build();
            TransportResult<org.a2aproject.sdk.spec.EventKind> result = transport.sendInput(input,
                    SpiCallOptions.defaults(), null).result().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(new TaskDecodeEvidence("task-target", List.of(8L), true), result.taskEvidence().get(0));
            SendMessageRequest request = peer.sendRequest.get();
            assertFalse(request.hasMessage());
            assertEquals("tenant-request", request.getTenant());
            assertEquals(12, request.getConfiguration().getIfGenerationMatch());
            assertEquals(4, request.getConfiguration().getTimelineLength());
            assertEquals(1, request.getArtifactUpdatesCount());
            assertEquals("user-artifact", request.getArtifactUpdates(0).getArtifact().getArtifactId());
            assertEquals(org.a2aproject.sdk.grpc.Role.ROLE_USER,
                    request.getArtifactUpdates(0).getArtifact().getRole());
            assertEquals(0, request.getArtifactUpdates(0).getGeneration(),
                    "client supplied artifact generation must not be forwarded");
            assertEquals("metadata", request.getMetadata().getFieldsOrThrow("request").getStringValue());

            TransportResult<org.a2aproject.sdk.spec.EventKind> taskSend = transport.sendInput(
                    SendInputParams.builder().message(message("", "", "task-send")).build(),
                    SpiCallOptions.defaults(), null).result().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(new TaskDecodeEvidence("task-target", List.of(8L), true),
                    taskSend.taskEvidence().get(0));

            peer.directMessageResponse.set(true);
            TransportResult<org.a2aproject.sdk.spec.EventKind> directMessage = transport.sendInput(
                    SendInputParams.builder().message(message("", "", "direct")).build(),
                    SpiCallOptions.defaults(), null).result().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertInstanceOf(Message.class, directMessage.value());
            assertTrue(directMessage.taskEvidence().isEmpty());
            peer.directMessageResponse.set(false);

            peer.mismatchSend.set(true);
            var mismatch = transport.sendInput(
                    SendInputParams.builder().message(message("", "", "input-2")).build(),
                    SpiCallOptions.defaults(), null).result().toCompletableFuture();
            var failed = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> mismatch.get(5, TimeUnit.SECONDS));
            assertInstanceOf(org.a2aproject.sdk.spec.A2AClientException.class, failed.getCause());
            assertInstanceOf(org.a2aproject.sdk.spec.TaskGenerationMismatchError.class,
                    failed.getCause().getCause());
            assertEquals(4, peer.sendCalls.get());
            transport.close();
        }
    }

    @Test
    void publicStreamingSkipsFutureVariantsAndClientCloseCancelsHeldStream() throws Exception {
        try (GrpcPeer peer = new GrpcPeer()) {
            AgentCard card = peer.card(true, true);
            GrpcTransport eventsTransport = transport(card, "1.1");
            TransportObservation eventsObservation = eventsTransport.streamInput(
                    SendInputParams.builder().message(message("", "", "events")).build(),
                    SpiCallOptions.defaults(), null);
            CopyOnWriteArrayList<TransportFrame> frames = new CopyOnWriteArrayList<>();
            CountDownLatch completed = new CountDownLatch(1);
            eventsObservation.frames().subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(TransportFrame frame) {
                    frames.add(frame);
                }

                @Override
                public void onError(Throwable failure) {
                    completed.countDown();
                }

                @Override
                public void onComplete() {
                    completed.countDown();
                }
            });
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertEquals(5, frames.size());
            assertEquals("task", frames.get(0).event().kind());
            TaskMessageUpdateEvent messageUpdate = assertInstanceOf(TaskMessageUpdateEvent.class,
                    frames.get(1).event());
            assertEquals("stream-message", messageUpdate.message().messageId());
            assertTrue(frames.get(2).unknownSkipped());
            assertNull(frames.get(2).event());
            assertEquals("statusUpdate", frames.get(3).event().kind());
            assertEquals("artifactUpdate", frames.get(4).event().kind());

            GrpcTransport heldTransport = transport(card, "1.1");
            GrpcTransport independentTransport = transport(card, "1.1");
            TransportObservation held = heldTransport.streamInput(
                    SendInputParams.builder().message(message("", "", "hold")).build(),
                    SpiCallOptions.defaults(), null);
            assertTrue(peer.heldStreamOpened.await(5, TimeUnit.SECONDS));
            heldTransport.close();
            assertTrue(peer.heldStreamCancelled.await(5, TimeUnit.SECONDS));
            assertEquals(org.a2aproject.sdk.client.transport.spi.TransportEnd.Kind.CANCELLED,
                    held.completion().toCompletableFuture().get(5, TimeUnit.SECONDS).kind());
            assertEquals("task-a", independentTransport.getTaskDecoded(new TaskQueryParams("task-a"), null)
                    .value().id());

            eventsTransport.close();
            independentTransport.close();
        }
    }

    @Test
    void rawStreamingReplenishesIgnoredFramesUnderOneAtATimeDemand() throws Exception {
        try (GrpcPeer peer = new GrpcPeer()) {
            GrpcTransport transport = transport(peer.card(true, true), "1.1");
            TransportObservation observation = transport.streamInput(
                    SendInputParams.builder().message(message("", "", "events")).build(),
                    SpiCallOptions.defaults(), null);
            CopyOnWriteArrayList<TransportFrame> frames = new CopyOnWriteArrayList<>();
            CountDownLatch first = new CountDownLatch(1);
            CountDownLatch receivedAll = new CountDownLatch(5);
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            observation.frames().subscribe(new Flow.Subscriber<>() {
                private Flow.@Nullable Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(1);
                }

                @Override
                public void onNext(TransportFrame frame) {
                    frames.add(frame);
                    first.countDown();
                    receivedAll.countDown();
                    if (subscription != null) {
                        subscription.request(1);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    failure.set(error);
                    completed.countDown();
                }

                @Override
                public void onComplete() {
                    completed.countDown();
                }
            });

            assertTrue(first.await(5, TimeUnit.SECONDS));
            assertTrue(receivedAll.await(5, TimeUnit.SECONDS),
                    "the ignored future response must not stall later events under finite demand");
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertEquals(5, frames.size());
            assertEquals("task", frames.get(0).event().kind());
            assertEquals("messageUpdate", frames.get(1).event().kind());
            assertTrue(frames.get(2).unknownSkipped());
            assertNull(frames.get(2).event());
            assertEquals("statusUpdate", frames.get(3).event().kind());
            assertEquals("artifactUpdate", frames.get(4).event().kind());
            assertTrue(observation.completion().toCompletableFuture().join().kind()
                    == org.a2aproject.sdk.client.transport.spi.TransportEnd.Kind.COMPLETED);
            transport.close();
        }
    }

    @Test
    void explicitOneZeroRejectsNewInputLocallyAndKeepsLegacyCapabilities() throws Exception {
        try (GrpcPeer peer = new GrpcPeer()) {
            GrpcTransport transport = transport(peer.card(true, true), "1.0");
            assertEquals(Set.of(ClientFeature.ORDINARY), transport.capabilities());
            SendInputParams guarded = SendInputParams.builder().message(message("", "", "legacy"))
                    .configuration(MessageSendConfiguration.builder().ifGenerationMatch(1L).build()).build();
            assertThrows(UnsupportedOperationException.class,
                    () -> transport.sendInput(guarded, SpiCallOptions.defaults(), null));
            assertEquals(0, peer.sendCalls.get());
            assertThrows(UnsupportedOperationException.class,
                    () -> transport.getTask(new TaskQueryParams("legacy", null, null, null, 4), null));
            assertEquals(0, peer.getTaskCalls.get());
            assertEquals("legacy", transport.getTask(new TaskQueryParams("legacy"), null).id());

            var finalPage = transport.listTasks(ListTasksParams.builder().contextId("legacy-final").build(), null);
            assertEquals(2, finalPage.pageSize());
            assertNull(finalPage.nextPageToken());
            var sizedPage = transport.listTasks(ListTasksParams.builder().contextId("legacy-wire-page-size").build(),
                    null);
            assertEquals(2, sizedPage.pageSize(), "legacy page size is the number of returned tasks");
            assertNull(sizedPage.nextPageToken());
            assertTrue(peer.versions.contains("1.0"));
            transport.close();
        }
    }

    private static GrpcTransport transport(AgentCard card, String protocolVersion) {
        GrpcTransportConfig config = new GrpcTransportConfigBuilder().managedChannel(
                GrpcManagedChannelOptions.builder().usePlaintext().build()).build();
        AgentInterface selected = card.supportedInterfaces().stream()
                .filter(agentInterface -> protocolVersion.equals(agentInterface.protocolVersion()))
                .findFirst().orElseThrow();
        return new GrpcTransportProvider().create(config, card, selected);
    }

    private static Message message(String taskId, String contextId, String id) {
        return Message.builder().messageId(id).taskId(taskId).contextId(contextId)
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart(id))).build();
    }

    private static org.a2aproject.sdk.grpc.Task task(String id, boolean unknownTimeline) {
        String context = "context-" + id;
        long firstGeneration = "task-b".equals(id) ? 17 : 7;
        long unknownGeneration = firstGeneration + 1;
        long lastGeneration = firstGeneration + 2;
        org.a2aproject.sdk.grpc.Task.Builder task = org.a2aproject.sdk.grpc.Task.newBuilder().setId(id)
                .setContextId(context).setStatus(org.a2aproject.sdk.grpc.TaskStatus.newBuilder()
                        .setState(org.a2aproject.sdk.grpc.TaskState.TASK_STATE_WORKING))
                .setGeneration(lastGeneration)
                .addTimeline(entry(id, firstGeneration))
                .addTimeline(unknownTimeline ? unknownTimelineEntry(unknownGeneration) : entry(id, unknownGeneration))
                .addTimeline(entry(id, lastGeneration));
        return task.build();
    }

    private static org.a2aproject.sdk.grpc.TimelineEntry entry(String taskId, long generation) {
        String context = "context-" + taskId;
        org.a2aproject.sdk.grpc.Message message = org.a2aproject.sdk.grpc.Message.newBuilder()
                .setMessageId("message-" + taskId + "-" + generation).setTaskId(taskId).setContextId(context)
                .setRole(org.a2aproject.sdk.grpc.Role.ROLE_USER)
                .addParts(org.a2aproject.sdk.grpc.Part.newBuilder().setText("generation-" + generation)).build();
        return org.a2aproject.sdk.grpc.TimelineEntry.newBuilder().setGeneration(generation).setMessage(message).build();
    }

    private static org.a2aproject.sdk.grpc.TimelineEntry unknownTimelineEntry(long generation) {
        return org.a2aproject.sdk.grpc.TimelineEntry.newBuilder().setGeneration(generation)
                .setUnknownFields(UnknownFieldSet.newBuilder().addField(100,
                        UnknownFieldSet.Field.newBuilder().addLengthDelimited(ByteString.copyFromUtf8("future"))
                                .build()).build())
                .build();
    }

    private static StreamResponse futureStreamVariant() {
        return StreamResponse.newBuilder().setUnknownFields(UnknownFieldSet.newBuilder().addField(100,
                UnknownFieldSet.Field.newBuilder().addLengthDelimited(ByteString.copyFromUtf8("future"))
                        .build()).build()).build();
    }

    private static final class GrpcPeer implements AutoCloseable {
        private final AtomicReference<GetTaskRequest> getTaskRequest = new AtomicReference<>();
        private final AtomicReference<ListTasksRequest> listTasksRequest = new AtomicReference<>();
        private final AtomicReference<SendMessageRequest> sendRequest = new AtomicReference<>();
        private final AtomicBoolean mismatchSend = new AtomicBoolean();
        private final AtomicBoolean directMessageResponse = new AtomicBoolean();
        private final AtomicInteger getTaskCalls = new AtomicInteger();
        private final AtomicInteger sendCalls = new AtomicInteger();
        private final List<String> versions = new CopyOnWriteArrayList<>();
        private final CountDownLatch heldStreamOpened = new CountDownLatch(1);
        private final CountDownLatch heldStreamCancelled = new CountDownLatch(1);
        private final Server server;

        GrpcPeer() throws Exception {
            server = NettyServerBuilder.forPort(0).directExecutor().addService(ServerInterceptors.intercept(
                    new A2AServiceGrpc.A2AServiceImplBase() {
                        @Override
                        public void getTask(GetTaskRequest request,
                                StreamObserver<org.a2aproject.sdk.grpc.Task> responseObserver) {
                            getTaskCalls.incrementAndGet();
                            getTaskRequest.set(request);
                            if ("malformed".equals(request.getId())) {
                                responseObserver.onNext(task("malformed", true).toBuilder().setTimeline(1,
                                        org.a2aproject.sdk.grpc.TimelineEntry.newBuilder().setGeneration(8)
                                                .setMessage(org.a2aproject.sdk.grpc.Message.getDefaultInstance()))
                                        .build());
                            } else {
                                responseObserver.onNext(task(request.getId(), !"known".equals(request.getId())
                                        && !"legacy".equals(request.getId())));
                            }
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void listTasks(ListTasksRequest request,
                                StreamObserver<org.a2aproject.sdk.grpc.ListTasksResponse> responseObserver) {
                            listTasksRequest.set(request);
                            boolean legacyQuery = request.getContextId().startsWith("legacy-");
                            org.a2aproject.sdk.grpc.ListTasksResponse.Builder response =
                                    org.a2aproject.sdk.grpc.ListTasksResponse.newBuilder()
                                            .addTasks(task("task-a", !legacyQuery))
                                            .addTasks(task("task-b", !legacyQuery)).setTotalSize(2);
                            if ("legacy-wire-page-size".equals(request.getContextId())) {
                                response.setPageSize(1);
                            } else if (!"legacy-final".equals(request.getContextId())) {
                                response.setPageSize(request.hasPageSize() ? request.getPageSize() : 2)
                                        .setNextPageToken("page-next");
                            }
                            responseObserver.onNext(response.build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void sendMessage(SendMessageRequest request,
                                StreamObserver<SendMessageResponse> responseObserver) {
                            sendRequest.set(request);
                            sendCalls.incrementAndGet();
                            if (mismatchSend.get()) {
                                ErrorInfo errorInfo = ErrorInfo.newBuilder().setReason("TASK_GENERATION_MISMATCH")
                                        .setDomain("a2a-protocol.org").build();
                                com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                                        .setCode(Status.Code.ABORTED.value()).setMessage("generation mismatch")
                                        .addDetails(Any.pack(errorInfo)).build();
                                responseObserver.onError(StatusProto.toStatusRuntimeException(status));
                            } else if (directMessageResponse.get()) {
                                responseObserver.onNext(SendMessageResponse.newBuilder()
                                        .setMessage(org.a2aproject.sdk.grpc.Message.newBuilder()
                                                .setMessageId("direct-message").setRole(org.a2aproject.sdk.grpc.Role.ROLE_AGENT)
                                                .addParts(org.a2aproject.sdk.grpc.Part.newBuilder().setText("direct")))
                                        .build());
                                responseObserver.onCompleted();
                            } else {
                                responseObserver.onNext(SendMessageResponse.newBuilder()
                                        .setTask(task("task-target", true)).build());
                                responseObserver.onCompleted();
                            }
                        }

                        @Override
                        public void sendStreamingMessage(SendMessageRequest request,
                                StreamObserver<StreamResponse> responseObserver) {
                            String input = request.getMessage().getPartsCount() == 0 ? ""
                                    : request.getMessage().getParts(0).getText();
                            if ("hold".equals(input)) {
                                ServerCallStreamObserver<StreamResponse> serverObserver =
                                        (ServerCallStreamObserver<StreamResponse>) responseObserver;
                                serverObserver.setOnCancelHandler(heldStreamCancelled::countDown);
                                serverObserver.onNext(futureStreamVariant());
                                heldStreamOpened.countDown();
                                return;
                            }
                            responseObserver.onNext(StreamResponse.newBuilder().setTask(task("task-stream", false)).build());
                            Message updateMessage = Message.builder().messageId("stream-message").taskId("task-stream")
                                    .contextId("context-task-stream").role(Message.Role.ROLE_USER)
                                    .parts(List.of(new TextPart("update"))).build();
                            TaskMessageUpdateEvent update = new TaskMessageUpdateEvent("task-stream",
                                    "context-task-stream", updateMessage, null, 3);
                            responseObserver.onNext(org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto.streamResponse(update));
                            responseObserver.onNext(futureStreamVariant());
                            responseObserver.onNext(StreamResponse.newBuilder().setStatusUpdate(
                                    org.a2aproject.sdk.grpc.TaskStatusUpdateEvent.newBuilder().setTaskId("task-stream")
                                            .setContextId("context-task-stream").setGeneration(4)
                                            .setStatus(org.a2aproject.sdk.grpc.TaskStatus.newBuilder()
                                                    .setState(org.a2aproject.sdk.grpc.TaskState.TASK_STATE_WORKING)))
                                    .build());
                            responseObserver.onNext(StreamResponse.newBuilder().setArtifactUpdate(
                                    org.a2aproject.sdk.grpc.TaskArtifactUpdateEvent.newBuilder()
                                            .setTaskId("task-stream").setContextId("context-task-stream")
                                            .setGeneration(5).setArtifact(org.a2aproject.sdk.grpc.Artifact.newBuilder()
                                                    .setArtifactId("stream-artifact")
                                                    .addParts(org.a2aproject.sdk.grpc.Part.newBuilder().setText("result"))))
                                    .build());
                            responseObserver.onCompleted();
                        }
                    }, new ServerInterceptor() {
                        @Override
                        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                            versions.add(headers.get(VERSION_KEY));
                            return next.startCall(call, headers);
                        }
                    })).build().start();
        }

        AgentCard card(boolean streaming, boolean userArtifacts) {
            String endpoint = "http://localhost:" + server.getPort();
            return AgentCard.builder().name("grpc-peer").description("controlled gRPC peer").version("1")
                    .supportedInterfaces(List.of(new AgentInterface("GRPC", endpoint, "tenant-default", "1.0"),
                            new AgentInterface("GRPC", endpoint, "tenant-default", "1.1")))
                    .capabilities(AgentCapabilities.builder().streaming(streaming).userArtifacts(userArtifacts).build())
                    .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
        }

        @Override
        public void close() throws Exception {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
