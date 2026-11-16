package org.a2aproject.sdk.client;

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
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.grpc.GrpcManagedChannelOptions;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.GetTaskRequest;
import org.a2aproject.sdk.grpc.StreamResponse;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.grpc.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jspecify.annotations.Nullable;

@Timeout(20)
class Phase2GrpcPublicClientTest {

    @Test
    void publicDefaultSelectionUsesDecodedReadsAndExplicitOneZeroRejectsInputLocally() throws Exception {
        AtomicInteger sendCalls = new AtomicInteger();
        Server server = NettyServerBuilder.forPort(0).directExecutor()
                .addService(new A2AServiceGrpc.A2AServiceImplBase() {
                    @Override
                    public void getTask(GetTaskRequest request,
                            StreamObserver<org.a2aproject.sdk.grpc.Task> responseObserver) {
                        responseObserver.onNext(task("task-default"));
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void listTasks(org.a2aproject.sdk.grpc.ListTasksRequest request,
                            StreamObserver<org.a2aproject.sdk.grpc.ListTasksResponse> responseObserver) {
                        responseObserver.onNext(org.a2aproject.sdk.grpc.ListTasksResponse.newBuilder()
                                .addTasks(task("task-list")).setPageSize(1).setTotalSize(1).build());
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void sendMessage(SendMessageRequest request,
                            StreamObserver<org.a2aproject.sdk.grpc.SendMessageResponse> responseObserver) {
                        sendCalls.incrementAndGet();
                        responseObserver.onNext(org.a2aproject.sdk.grpc.SendMessageResponse.newBuilder()
                                .setMessage(org.a2aproject.sdk.grpc.Message.newBuilder().setMessageId("reply")
                                        .setRole(org.a2aproject.sdk.grpc.Role.ROLE_AGENT)
                                        .addParts(org.a2aproject.sdk.grpc.Part.newBuilder().setText("reply")))
                                .build());
                        responseObserver.onCompleted();
                    }
                }).build().start();
        Client defaultClient = client(server, null, false, true);
        Client legacyClient = client(server, "1.0", false, true);
        try {
            assertEquals(List.of(7L, 9L), defaultClient.getTask(new TaskQueryParams("task-default"), null)
                    .timeline().stream().map(entry -> entry.generation()).toList());
            ListTasksResult listed = defaultClient.listTasks(ListTasksParams.builder().build(), null);
            assertEquals(List.of(7L, 9L), listed.tasks().get(0).timeline().stream()
                    .map(entry -> entry.generation()).toList());

            SendInputParams guarded = SendInputParams.builder().message(inputMessage("legacy-guard"))
                    .configuration(MessageSendConfiguration.builder().ifGenerationMatch(2L).build()).build();
            assertThrows(A2AClientException.class, () -> legacyClient.sendInput(guarded, List.of(), null, null));
            SendInputParams artifact = SendInputParams.builder().artifactUpdates(List.of(
                    TaskArtifactUpdateEvent.builder().taskId("task-legacy").contextId("context-legacy")
                            .artifact(Artifact.builder().artifactId("artifact").role(Message.Role.ROLE_USER)
                                    .parts(List.of(new TextPart("input"))).build()).build())).build();
            assertThrows(A2AClientException.class, () -> legacyClient.sendInput(artifact, List.of(), null, null));
            assertEquals(0, sendCalls.get(), "explicit 1.0 input rejection must happen before network I/O");
        } finally {
            defaultClient.close();
            legacyClient.close();
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void publicArtifactGuardAndMismatchTravelThroughClient() throws Exception {
        AtomicReference<SendMessageRequest> lastRequest = new AtomicReference<>();
        Server server = NettyServerBuilder.forPort(0).directExecutor()
                .addService(new A2AServiceGrpc.A2AServiceImplBase() {
                    @Override
                    public void sendMessage(SendMessageRequest request,
                            StreamObserver<org.a2aproject.sdk.grpc.SendMessageResponse> responseObserver) {
                        lastRequest.set(request);
                        if (request.hasConfiguration() && request.getConfiguration().hasIfGenerationMatch()) {
                            ErrorInfo mismatch = ErrorInfo.newBuilder().setReason("TASK_GENERATION_MISMATCH")
                                    .setDomain("a2a-protocol.org").build();
                            com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                                    .setCode(Status.Code.ABORTED.value()).setMessage("generation mismatch")
                                    .addDetails(Any.pack(mismatch)).build();
                            responseObserver.onError(StatusProto.toStatusRuntimeException(status));
                        } else {
                            responseObserver.onNext(org.a2aproject.sdk.grpc.SendMessageResponse.newBuilder()
                                    .setTask(task("task-artifact")).build());
                            responseObserver.onCompleted();
                        }
                    }
                }).build().start();
        Client client = client(server, "1.1", false, true);
        try {
            CountDownLatch artifactDone = new CountDownLatch(1);
            AtomicReference<Throwable> artifactFailure = new AtomicReference<>();
            SendInputParams artifactInput = SendInputParams.builder().artifactUpdates(List.of(
                    TaskArtifactUpdateEvent.builder().taskId("task-artifact").contextId("context-artifact")
                            .artifact(Artifact.builder().artifactId("user-input").role(Message.Role.ROLE_USER)
                                    .parts(List.of(new TextPart("payload"))).build()).build())).build();
            client.sendInput(artifactInput, List.of(), error -> {
                artifactFailure.set(error);
                artifactDone.countDown();
            }, null);
            assertTrue(artifactDone.await(5, TimeUnit.SECONDS));
            assertNull(artifactFailure.get());
            assertFalse(lastRequest.get().hasMessage());
            assertEquals("user-input", lastRequest.get().getArtifactUpdates(0).getArtifact().getArtifactId());

            CountDownLatch mismatchDone = new CountDownLatch(1);
            AtomicReference<Throwable> mismatchFailure = new AtomicReference<>();
            SendInputParams guarded = SendInputParams.builder().message(inputMessage("guarded"))
                    .configuration(MessageSendConfiguration.builder().ifGenerationMatch(9L).build()).build();
            client.sendInput(guarded, List.of(), error -> {
                mismatchFailure.set(error);
                mismatchDone.countDown();
            }, null);
            assertTrue(mismatchDone.await(5, TimeUnit.SECONDS));
            assertEquals(9, lastRequest.get().getConfiguration().getIfGenerationMatch());
            assertInstanceOf(TaskGenerationMismatchError.class, mismatchFailure.get().getCause());
        } finally {
            client.close();
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void publicUnarySendMessageDecodesUnknownTimelineAndRetainsDirectMessage() throws Exception {
        AtomicInteger sendCalls = new AtomicInteger();
        Server server = NettyServerBuilder.forPort(0).directExecutor()
                .addService(new A2AServiceGrpc.A2AServiceImplBase() {
                    @Override
                    public void sendMessage(org.a2aproject.sdk.grpc.SendMessageRequest request,
                            StreamObserver<org.a2aproject.sdk.grpc.SendMessageResponse> responseObserver) {
                        sendCalls.incrementAndGet();
                        String messageId = request.getMessage().getMessageId();
                        if ("direct-message".equals(messageId)) {
                            responseObserver.onNext(org.a2aproject.sdk.grpc.SendMessageResponse.newBuilder()
                                    .setMessage(org.a2aproject.sdk.grpc.Message.newBuilder()
                                            .setMessageId("direct-reply")
                                            .setRole(org.a2aproject.sdk.grpc.Role.ROLE_AGENT)
                                            .addParts(org.a2aproject.sdk.grpc.Part.newBuilder().setText("reply")))
                                    .build());
                        } else {
                            org.a2aproject.sdk.grpc.Task responseTask = task("task-send");
                            if ("malformed-known".equals(messageId)) {
                                responseTask = responseTask.toBuilder().setTimeline(1,
                                        org.a2aproject.sdk.grpc.TimelineEntry.newBuilder().setGeneration(8)
                                                .setMessage(org.a2aproject.sdk.grpc.Message.getDefaultInstance()))
                                        .build();
                            }
                            responseObserver.onNext(org.a2aproject.sdk.grpc.SendMessageResponse.newBuilder()
                                    .setTask(responseTask).build());
                        }
                        responseObserver.onCompleted();
                    }
                }).build().start();

        Client client = client(server, "1.1", false, true);
        try {
            CopyOnWriteArrayList<ClientEvent> events = new CopyOnWriteArrayList<>();
            client.sendMessage(sendParams("unknown-timeline"), List.of((event, ignored) -> events.add(event)),
                    null, null);
            TaskEvent taskEvent = assertInstanceOf(TaskEvent.class, events.get(0));
            assertEquals(List.of(7L, 9L), taskEvent.getTask().timeline().stream()
                    .map(entry -> entry.generation()).toList());

            events.clear();
            client.sendMessage(sendParams("direct-message"), List.of((event, ignored) -> events.add(event)),
                    null, null);
            assertEquals("direct-reply", assertInstanceOf(MessageEvent.class, events.get(0))
                    .getMessage().messageId());

            assertThrows(A2AClientException.class,
                    () -> client.sendMessage(sendParams("malformed-known"), List.of(), null, null));
            assertEquals(3, sendCalls.get());
        } finally {
            client.close();
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void publicClientUsesGrpcTimelineDecoderAndKnownStreamingCallbacks() throws Exception {
        AtomicReference<Server> serverReference = new AtomicReference<>();
        Server server = NettyServerBuilder.forPort(0).directExecutor()
                .addService(new A2AServiceGrpc.A2AServiceImplBase() {
                    @Override
                    public void getTask(GetTaskRequest request,
                            StreamObserver<org.a2aproject.sdk.grpc.Task> responseObserver) {
                        responseObserver.onNext(task("task-public"));
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void sendStreamingMessage(org.a2aproject.sdk.grpc.SendMessageRequest request,
                            StreamObserver<StreamResponse> responseObserver) {
                        responseObserver.onNext(StreamResponse.newBuilder().setTask(task("task-public")).build());
                        Message updateMessage = Message.builder().messageId("grpc-message-update")
                                .taskId("task-public").contextId("context-task-public")
                                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("input"))).build();
                        TaskMessageUpdateEvent update = new TaskMessageUpdateEvent("task-public",
                                "context-task-public", updateMessage, null, 10);
                        responseObserver.onNext(org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto.streamResponse(update));
                        responseObserver.onNext(StreamResponse.newBuilder().setUnknownFields(
                                UnknownFieldSet.newBuilder().addField(100, UnknownFieldSet.Field.newBuilder()
                                        .addLengthDelimited(ByteString.copyFromUtf8("future")).build()).build())
                                .build());
                        responseObserver.onCompleted();
                    }
                }).build().start();
        serverReference.set(server);

        String endpoint = "http://localhost:" + server.getPort();
        AgentCard card = AgentCard.builder().name("grpc-public").description("controlled peer").version("1")
                .supportedInterfaces(List.of(new AgentInterface("GRPC", endpoint, null, "1.1")))
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
        Client client = Client.builder(card)
                .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.1").setStreaming(true).build())
                .withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder()
                        .managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()).build())
                .build();
        try {
            Task task = client.getTask(new TaskQueryParams("task-public"), null);
            assertEquals(List.of(7L, 9L), task.timeline().stream().map(entry -> entry.generation()).toList());

            CopyOnWriteArrayList<ClientEvent> events = new CopyOnWriteArrayList<>();
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            client.sendInput(SendInputParams.builder().message(Message.builder().messageId("client-input")
                            .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("hello"))).build()).build(),
                    List.of((event, ignored) -> events.add(event)), error -> {
                        failure.set(error);
                        completed.countDown();
                    }, null);

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertEquals(2, events.size(), "the unknown future variant must not become a public event");
            assertInstanceOf(TaskEvent.class, events.get(0));
            TaskUpdateEvent messageEvent = assertInstanceOf(TaskUpdateEvent.class, events.get(1));
            TaskMessageUpdateEvent update = assertInstanceOf(TaskMessageUpdateEvent.class,
                    messageEvent.getUpdateEvent());
            assertEquals("grpc-message-update", update.message().messageId());
            assertEquals(10, update.generation());
        } finally {
            client.close();
            serverReference.get().shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void publicCloseCancelsAllUnknownStreamAndLeavesSecondClientUsable() throws Exception {
        Map<String, ServerCallStreamObserver<StreamResponse>> streams = new ConcurrentHashMap<>();
        CountDownLatch unknownStarted = new CountDownLatch(1);
        CountDownLatch unknownCancelled = new CountDownLatch(1);
        CountDownLatch liveStarted = new CountDownLatch(1);
        Server server = NettyServerBuilder.forPort(0).directExecutor()
                .addService(new A2AServiceGrpc.A2AServiceImplBase() {
                    @Override
                    public void getTask(GetTaskRequest request,
                            StreamObserver<org.a2aproject.sdk.grpc.Task> responseObserver) {
                        responseObserver.onNext(task(request.getId()));
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void sendStreamingMessage(org.a2aproject.sdk.grpc.SendMessageRequest request,
                            StreamObserver<StreamResponse> responseObserver) {
                        String id = request.getMessage().getMessageId();
                        ServerCallStreamObserver<StreamResponse> serverStream =
                                (ServerCallStreamObserver<StreamResponse>) responseObserver;
                        streams.put(id, serverStream);
                        if ("unknown-held".equals(id)) {
                            serverStream.setOnCancelHandler(unknownCancelled::countDown);
                            serverStream.onNext(futureStreamVariant());
                            unknownStarted.countDown();
                        } else {
                            serverStream.onNext(StreamResponse.newBuilder().setTask(task("task-live")).build());
                            serverStream.onCompleted();
                            liveStarted.countDown();
                        }
                    }
                }).build().start();
        Client first = client(server, "1.1", true, false);
        Client second = client(server, "1.1", true, false);
        try {
            CopyOnWriteArrayList<ClientEvent> firstEvents = new CopyOnWriteArrayList<>();
            AtomicInteger firstTerminals = new AtomicInteger();
            CountDownLatch firstDone = new CountDownLatch(1);
            first.sendInput(SendInputParams.builder().message(inputMessage("unknown-held")).build(),
                    List.of((event, ignored) -> firstEvents.add(event)), error -> {
                        firstTerminals.incrementAndGet();
                        firstDone.countDown();
                    }, null);
            assertTrue(unknownStarted.await(5, TimeUnit.SECONDS));

            CopyOnWriteArrayList<ClientEvent> secondEvents = new CopyOnWriteArrayList<>();
            CountDownLatch secondDone = new CountDownLatch(1);
            AtomicReference<Throwable> secondFailure = new AtomicReference<>();
            second.sendInput(SendInputParams.builder().message(inputMessage("live")).build(),
                    List.of((event, ignored) -> secondEvents.add(event)), error -> {
                        secondFailure.set(error);
                        secondDone.countDown();
                    }, null);
            assertTrue(liveStarted.await(5, TimeUnit.SECONDS));
            assertTrue(secondDone.await(5, TimeUnit.SECONDS));
            assertNull(secondFailure.get());
            assertEquals(1, secondEvents.size());

            first.close();
            assertTrue(firstDone.await(5, TimeUnit.SECONDS));
            assertTrue(unknownCancelled.await(5, TimeUnit.SECONDS));
            assertEquals(1, firstTerminals.get());
            assertTrue(firstEvents.isEmpty(), "unknown-only input must not fabricate public callbacks");
            assertEquals("task-live", second.getTask(new TaskQueryParams("task-live"), null).id());
        } finally {
            first.close();
            second.close();
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void publicMalformedKnownStreamPayloadTerminatesWithoutDeliveringAnEvent() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        Server server = NettyServerBuilder.forPort(0).directExecutor()
                .addService(new A2AServiceGrpc.A2AServiceImplBase() {
                    @Override
                    public void sendStreamingMessage(org.a2aproject.sdk.grpc.SendMessageRequest request,
                            StreamObserver<StreamResponse> responseObserver) {
                        ServerCallStreamObserver<StreamResponse> serverStream =
                                (ServerCallStreamObserver<StreamResponse>) responseObserver;
                        serverStream.setOnCancelHandler(cancelled::countDown);
                        org.a2aproject.sdk.grpc.Task malformed = task("task-malformed-stream").toBuilder()
                                .setTimeline(1, org.a2aproject.sdk.grpc.TimelineEntry.newBuilder()
                                        .setGeneration(8)
                                        .setMessage(org.a2aproject.sdk.grpc.Message.getDefaultInstance()))
                                .build();
                        serverStream.onNext(StreamResponse.newBuilder().setTask(malformed).build());
                    }
                }).build().start();
        Client client = client(server, "1.1", true, false);
        try {
            AtomicInteger events = new AtomicInteger();
            AtomicInteger terminals = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            client.sendInput(SendInputParams.builder().message(inputMessage("malformed-stream")).build(),
                    List.of((event, ignored) -> events.incrementAndGet()), error -> {
                        failure.set(error);
                        terminals.incrementAndGet();
                        done.countDown();
                    }, null);

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
            assertInstanceOf(A2AClientException.class, failure.get());
            assertEquals(0, events.get());
            assertEquals(1, terminals.get());
        } finally {
            client.close();
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void publicCallbackCloseAndPeerCompletionRaceHasOneTerminalAndNoLateCallback() throws Exception {
        AtomicReference<ServerCallStreamObserver<StreamResponse>> serverStream = new AtomicReference<>();
        CountDownLatch streamStarted = new CountDownLatch(1);
        Server server = NettyServerBuilder.forPort(0).directExecutor()
                .addService(new A2AServiceGrpc.A2AServiceImplBase() {
                    @Override
                    public void sendStreamingMessage(org.a2aproject.sdk.grpc.SendMessageRequest request,
                            StreamObserver<StreamResponse> responseObserver) {
                        serverStream.set((ServerCallStreamObserver<StreamResponse>) responseObserver);
                        responseObserver.onNext(StreamResponse.newBuilder().setTask(task("task-race")).build());
                        streamStarted.countDown();
                    }
                }).build().start();
        Client client = client(server, "1.1", true, false);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch callbackExited = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        CountDownLatch terminalDelivered = new CountDownLatch(1);
        AtomicBoolean terminal = new AtomicBoolean();
        AtomicInteger terminals = new AtomicInteger();
        AtomicInteger callbacksAfterTerminal = new AtomicInteger();
        AtomicInteger events = new AtomicInteger();
        ExecutorService race = Executors.newFixedThreadPool(2);
        CountDownLatch raceStart = new CountDownLatch(1);
        CountDownLatch actionsStaged = new CountDownLatch(2);
        try {
            client.sendInput(SendInputParams.builder().message(inputMessage("race")).build(),
                    List.of((event, ignored) -> {
                        if (terminal.get()) {
                            callbacksAfterTerminal.incrementAndGet();
                        }
                        events.incrementAndGet();
                        callbackEntered.countDown();
                        try {
                            if (!releaseCallback.await(15, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("held callback was not released");
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        } finally {
                            callbackExited.countDown();
                        }
                    }), error -> {
                        terminal.set(true);
                        terminals.incrementAndGet();
                        terminalDelivered.countDown();
                    }, null);
            assertTrue(streamStarted.await(5, TimeUnit.SECONDS));
            assertTrue(callbackEntered.await(5, TimeUnit.SECONDS));

            // The first callback holds the public observation drain. Submit another frame before
            // attempting either terminal path so a second delivery is pending in the gRPC path.
            race.submit(() -> {
                serverStream.get().onNext(messageUpdateResponse("task-race", "pending-race-update", 10));
                return null;
            }).get(5, TimeUnit.SECONDS);

            var completionAttempt = race.submit(() -> {
                actionsStaged.countDown();
                raceStart.await();
                serverStream.get().onCompleted();
                return null;
            });
            var closeAttempt = race.submit(() -> {
                actionsStaged.countDown();
                raceStart.await();
                client.close();
                return null;
            });
            assertTrue(actionsStaged.await(5, TimeUnit.SECONDS));
            raceStart.countDown();
            // Join both actual terminal operations while the public callback remains held. A
            // pre-action latch alone would not prove that either operation reached its path.
            completionAttempt.get(5, TimeUnit.SECONDS);
            closeAttempt.get(7, TimeUnit.SECONDS);
            assertEquals(0, terminals.get(), "terminal callback ran while the first event callback was held");
            assertEquals(1, events.get());
            assertEquals(1, callbackExited.getCount(), "the first callback must still be held after both terminal paths");

            releaseCallback.countDown();
            assertTrue(callbackExited.await(5, TimeUnit.SECONDS));
            assertTrue(terminalDelivered.await(5, TimeUnit.SECONDS));
        } finally {
            releaseCallback.countDown();
            try {
                client.close();
            } finally {
                try {
                    race.shutdownNow();
                    assertTrue(race.awaitTermination(5, TimeUnit.SECONDS));
                } finally {
                    assertTrue(server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS));
                }
            }
        }
        assertEquals(1, terminals.get());
        assertEquals(1, events.get(), "client close must suppress the pending second frame");
        assertEquals(0, callbacksAfterTerminal.get());
    }

    @Test
    void publicCallbackPeerErrorWaitsForPendingUpdateAndHasOneTerminal() throws Exception {
        AtomicReference<ServerCallStreamObserver<StreamResponse>> serverStream = new AtomicReference<>();
        CountDownLatch streamStarted = new CountDownLatch(1);
        Server server = NettyServerBuilder.forPort(0).directExecutor()
                .addService(new A2AServiceGrpc.A2AServiceImplBase() {
                    @Override
                    public void sendStreamingMessage(org.a2aproject.sdk.grpc.SendMessageRequest request,
                            StreamObserver<StreamResponse> responseObserver) {
                        serverStream.set((ServerCallStreamObserver<StreamResponse>) responseObserver);
                        responseObserver.onNext(StreamResponse.newBuilder().setTask(task("task-error-race")).build());
                        streamStarted.countDown();
                    }
                }).build().start();
        Client client = client(server, "1.1", true, false);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch callbackExited = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        CountDownLatch secondCallbackEntered = new CountDownLatch(1);
        CountDownLatch terminalDelivered = new CountDownLatch(1);
        AtomicBoolean terminal = new AtomicBoolean();
        AtomicInteger terminals = new AtomicInteger();
        AtomicInteger callbacksAfterTerminal = new AtomicInteger();
        AtomicInteger events = new AtomicInteger();
        AtomicReference<Throwable> terminalError = new AtomicReference<>();
        ExecutorService peer = Executors.newSingleThreadExecutor();
        try {
            client.sendInput(SendInputParams.builder().message(inputMessage("error-race")).build(),
                    List.of((event, ignored) -> {
                        if (terminal.get()) {
                            callbacksAfterTerminal.incrementAndGet();
                        }
                        int eventNumber = events.incrementAndGet();
                        if (eventNumber == 1) {
                            callbackEntered.countDown();
                            try {
                                if (!releaseCallback.await(15, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("held callback was not released");
                                }
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(interrupted);
                            } finally {
                                callbackExited.countDown();
                            }
                        } else if (eventNumber == 2) {
                            secondCallbackEntered.countDown();
                        }
                    }), error -> {
                        terminal.set(true);
                        terminalError.set(error);
                        terminals.incrementAndGet();
                        terminalDelivered.countDown();
                    }, null);
            assertTrue(streamStarted.await(5, TimeUnit.SECONDS));
            assertTrue(callbackEntered.await(5, TimeUnit.SECONDS));

            // This peer write precedes the error and returns while the first public callback is
            // held. The client can deliver it only after that callback releases the serial drain.
            peer.submit(() -> {
                serverStream.get().onNext(messageUpdateResponse("task-error-race", "pending-error-update", 10));
                return null;
            }).get(5, TimeUnit.SECONDS);
            peer.submit(() -> {
                serverStream.get().onError(
                        Status.UNAVAILABLE.withDescription("peer error race").asRuntimeException());
                return null;
            }).get(5, TimeUnit.SECONDS);
            assertEquals(0, terminals.get(), "terminal callback ran while the first event callback was held");
            assertEquals(1, events.get());
            assertEquals(1, callbackExited.getCount(), "the first callback must still be held after the peer error");

            releaseCallback.countDown();
            assertTrue(callbackExited.await(5, TimeUnit.SECONDS));
            assertTrue(secondCallbackEntered.await(5, TimeUnit.SECONDS));
            assertTrue(terminalDelivered.await(5, TimeUnit.SECONDS));
        } finally {
            releaseCallback.countDown();
            try {
                client.close();
            } finally {
                try {
                    peer.shutdownNow();
                    assertTrue(peer.awaitTermination(5, TimeUnit.SECONDS));
                } finally {
                    assertTrue(server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS));
                }
            }
        }
        assertEquals(2, events.get(), "the pending second peer update must be delivered");
        assertEquals(1, terminals.get());
        A2AClientException mappedError = assertInstanceOf(A2AClientException.class, terminalError.get());
        io.grpc.StatusRuntimeException mappedCause =
                assertInstanceOf(io.grpc.StatusRuntimeException.class, mappedError.getCause());
        assertEquals(Status.Code.UNAVAILABLE, Status.fromThrowable(mappedCause).getCode());
        assertEquals(0, callbacksAfterTerminal.get());
    }

    private static org.a2aproject.sdk.grpc.Task task(String id) {
        String contextId = "context-" + id;
        return org.a2aproject.sdk.grpc.Task.newBuilder().setId(id).setContextId(contextId)
                .setStatus(org.a2aproject.sdk.grpc.TaskStatus.newBuilder()
                        .setState(org.a2aproject.sdk.grpc.TaskState.TASK_STATE_WORKING))
                .setGeneration(9).addTimeline(entry(id, contextId, 7))
                .addTimeline(org.a2aproject.sdk.grpc.TimelineEntry.newBuilder().setGeneration(8)
                        .setUnknownFields(UnknownFieldSet.newBuilder().addField(100,
                                UnknownFieldSet.Field.newBuilder()
                                        .addLengthDelimited(ByteString.copyFromUtf8("timeline-future")).build())
                                .build()))
                .addTimeline(entry(id, contextId, 9)).build();
    }

    private static StreamResponse messageUpdateResponse(String taskId, String messageId, long generation) {
        String contextId = "context-" + taskId;
        Message message = Message.builder().messageId(messageId).taskId(taskId).contextId(contextId)
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart(messageId))).build();
        TaskMessageUpdateEvent update = new TaskMessageUpdateEvent(taskId, contextId, message, null, generation);
        return org.a2aproject.sdk.grpc.utils.ProtoUtils.ToProto.streamResponse(update);
    }

    private static Client client(Server server, @Nullable String selectedProtocolVersion,
            boolean streaming, boolean userArtifacts) {
        String cardVersion = selectedProtocolVersion == null ? "1.1" : selectedProtocolVersion;
        String endpoint = "http://localhost:" + server.getPort();
        AgentCard card = AgentCard.builder().name("grpc-public").description("controlled peer").version("1")
                .supportedInterfaces(List.of(new AgentInterface("GRPC", endpoint, null, cardVersion)))
                .capabilities(AgentCapabilities.builder().streaming(streaming).userArtifacts(userArtifacts).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of()).build();
        var builder = Client.builder(card)
                .withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder()
                        .managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()).build());
        if (selectedProtocolVersion != null) {
            builder.clientConfig(new ClientConfig.Builder().setProtocolVersion(selectedProtocolVersion)
                    .setStreaming(streaming).build());
        }
        return builder.build();
    }

    private static MessageSendParams sendParams(String messageId) {
        Message message = Message.builder().messageId(messageId).role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("input"))).build();
        return new MessageSendParams(message, null, null);
    }

    private static Message inputMessage(String id) {
        return Message.builder().messageId(id).role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(id))).build();
    }

    private static StreamResponse futureStreamVariant() {
        return StreamResponse.newBuilder().setUnknownFields(UnknownFieldSet.newBuilder().addField(100,
                UnknownFieldSet.Field.newBuilder().addLengthDelimited(ByteString.copyFromUtf8("future")).build())
                .build()).build();
    }

    private static org.a2aproject.sdk.grpc.TimelineEntry entry(String taskId, String contextId, long generation) {
        return org.a2aproject.sdk.grpc.TimelineEntry.newBuilder().setGeneration(generation)
                .setMessage(org.a2aproject.sdk.grpc.Message.newBuilder()
                        .setMessageId("message-" + generation).setTaskId(taskId).setContextId(contextId)
                        .setRole(org.a2aproject.sdk.grpc.Role.ROLE_USER)
                        .addParts(org.a2aproject.sdk.grpc.Part.newBuilder().setText("generation-" + generation)))
                .build();
    }
}
