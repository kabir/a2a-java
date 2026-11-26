package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence;
import org.a2aproject.sdk.client.transport.spi.TransportCall;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Every selected-1.1 ordinary entry point reduces through the same reducer and the Client-owned reliability
 * registry; explicit 1.0 keeps its historical path.
 */
class ExistingOrdinaryEntryPointReductionTest {

    private static final String TASK = "task";
    private static final String CTX = "ctx";

    enum Route {
        SEND_UNARY, SEND_STREAMING, SUBSCRIBE, CALLBACK_SEND_INPUT, SEND_INPUT_OBSERVED;

        boolean streaming() {
            return this != SEND_UNARY;
        }
    }

    // ---- reduction through every route -------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Route.class)
    void routeReducesGenerationsTimelineArtifactsAndMessageOccurrences(Route route) throws Exception {
        Fixture fixture = new Fixture("1.1", route.streaming());
        Message m1 = message("m1");
        Message m2 = message("m2");
        if (route == Route.SEND_UNARY) {
            fixture.unary.add(Task.builder().id(TASK).contextId(CTX).status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                    .generation(5).history(List.of(m1, m2)).artifacts(List.of()).timeline(List.of()).build());
        } else {
            Artifact first = Artifact.builder().artifactId("a").parts(List.of(new TextPart("x")))
                    .startGeneration(3).endGeneration(3).build();
            Artifact more = Artifact.builder().artifactId("a").parts(List.of(new TextPart("y"))).build();
            fixture.streams.add(List.of(snapshot(1),
                    new TaskStatusUpdateEvent(TASK, new TaskStatus(TaskState.TASK_STATE_WORKING, m1, null), CTX, null, 2),
                    new TaskArtifactUpdateEvent(TASK, first, CTX, null, null, null, 3),
                    new TaskArtifactUpdateEvent(TASK, more, CTX, true, null, null, 4),
                    new TaskMessageUpdateEvent(TASK, CTX, m2, null, 5)));
        }

        Outcome outcome = fixture.run(route);

        Task result = outcome.finalTask();
        assertEquals(5, result.generation());
        assertEquals(List.of("m1", "m2"), result.history().stream()
                .map(message -> ((TextPart) message.parts().get(0)).text()).toList());
        if (route != Route.SEND_UNARY) {
            assertEquals(List.of(2L, 5L), result.timeline().stream().map(TimelineEntry::generation).toList());
            Artifact artifact = result.artifacts().get(0);
            assertEquals(2, artifact.parts().size());
            assertEquals(3, artifact.startGeneration());
            assertEquals(4, artifact.endGeneration());
        }
        if (route == Route.SEND_INPUT_OBSERVED) {
            assertEquals(5L, outcome.end.finalView().throughGeneration());
        }
        assertEquals(0, outcome.errors.size());
        // every route uses its decoded companion; none falls back to the established transport methods
        String companion = switch (route) {
            case SEND_UNARY -> "sendInput";
            case SUBSCRIBE -> "observe";
            default -> "streamInput";
        };
        assertEquals(1, fixture.count(companion));
        assertEquals(1, fixture.io.get());
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void directMessageGainsNoTaskAndHasNoPersistedEffect(Route route) throws Exception {
        Fixture fixture = new Fixture("1.1", route.streaming());
        Message reply = message("reply");
        if (route == Route.SEND_UNARY) {
            fixture.unary.add(reply);
        } else {
            fixture.streams.add(List.of(reply));
        }

        Outcome outcome = fixture.run(route);

        if (route == Route.SEND_INPUT_OBSERVED) {
            assertNull(outcome.end.finalView().task());
            assertEquals(ViewSync.NO_BASELINE, outcome.end.finalView().sync());
        } else {
            assertEquals(1, outcome.events.size());
            assertSame(reply, assertInstanceOf(MessageEvent.class, outcome.events.get(0)).getMessage());
        }
        // a direct Message never taints or reserves the task: a guarded follow-up is not rejected locally
        fixture.guardedFollowUp(1);
    }

    // ---- taint and local rejection before any I/O ----------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Route.class)
    void generationZeroRejectsLaterGuardWithoutAnyNetworkRequest(Route route) throws Exception {
        Fixture fixture = new Fixture("1.1", route.streaming());
        if (route == Route.SEND_UNARY) {
            fixture.unary.add(Task.builder().id(TASK).contextId(CTX).status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                    .generation(0).history(List.of()).artifacts(List.of()).timeline(List.of()).build());
        } else {
            fixture.streams.add(List.of(snapshot(0)));
        }
        fixture.run(route);

        fixture.assertGuardedRejectedWithoutIo();
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void consecutiveEqualGenerationRejectsLaterGuardWithoutAnyNetworkRequest(Route route) throws Exception {
        Fixture fixture = new Fixture("1.1", route.streaming());
        if (route == Route.SEND_UNARY) {
            fixture.unary.add(snapshot(6));
            fixture.run(route);
            fixture.unary.add(snapshot(6));
            fixture.run(route);
        } else {
            fixture.streams.add(List.of(snapshot(5),
                    new TaskStatusUpdateEvent(TASK, new TaskStatus(TaskState.TASK_STATE_WORKING), CTX, null, 6),
                    new TaskStatusUpdateEvent(TASK, new TaskStatus(TaskState.TASK_STATE_WORKING), CTX, null, 6)));
            fixture.run(route);
        }

        fixture.assertGuardedRejectedWithoutIo();
    }

    @Test
    void unaryKnownReliabilityIsRetainedAcrossCallsAndSnapshots() throws Exception {
        Fixture fixture = new Fixture("1.1", false);
        fixture.unary.add(snapshot(7));
        fixture.run(Route.SEND_UNARY);
        fixture.unary.add(snapshot(8));
        fixture.run(Route.SEND_UNARY);
        fixture.guardedFollowUp(9); // advancing unary generations stay reliable: the guard is dispatched
        fixture.unary.add(snapshot(9));
        fixture.run(Route.SEND_UNARY);
        // a later call with an advancing snapshot never resets the taint
        fixture.unary.add(snapshot(20));
        fixture.run(Route.SEND_UNARY);

        fixture.assertGuardedRejectedWithoutIo();
    }

    @Test
    void taintSurvivesReconnectAndFreshSnapshotsAcrossRoutes() throws Exception {
        Fixture fixture = new Fixture("1.1", true);
        fixture.streams.add(List.of(snapshot(0)));
        fixture.run(Route.SEND_STREAMING);
        fixture.streams.add(List.of(snapshot(40)));
        fixture.run(Route.SUBSCRIBE);
        fixture.streams.add(List.of(snapshot(50)));
        Outcome observed = fixture.run(Route.SEND_INPUT_OBSERVED);

        assertEquals(ViewSync.INCOMPLETE, observed.end.finalView().sync());
        fixture.assertGuardedRejectedWithoutIo();
    }

    @Test
    void taintIsScopedToTheClientAndTheTask() throws Exception {
        Fixture tainted = new Fixture("1.1", true);
        tainted.streams.add(List.of(snapshot(0)));
        tainted.run(Route.SEND_STREAMING);
        tainted.assertGuardedRejectedWithoutIo();

        // another task on the same client is unaffected
        tainted.guardedFollowUpFor("other-task", 3);

        // another client has its own registry
        new Fixture("1.1", true).guardedFollowUp(3);
    }

    @Test
    void guardAndGuardlessInputOnReliableTaskAreForwardedUnchanged() throws Exception {
        Fixture fixture = new Fixture("1.1", true);
        fixture.streams.add(List.of(snapshot(3),
                new TaskStatusUpdateEvent(TASK, new TaskStatus(TaskState.TASK_STATE_WORKING), CTX, null, 4)));
        fixture.run(Route.SEND_STREAMING);

        SendInputParams guarded = fixture.guardedParams(TASK);
        fixture.streams.add(List.of(snapshot(5)));
        fixture.client.sendInput(guarded, List.of(), error -> { }, null);

        assertSame(guarded, fixture.lastStreamInput.get());
    }

    // ---- optional companions and evidence -------------------------------------------------------------------

    @Test
    void customProviderWithoutObserveFallsBackToCallbackSubscriptionAndStillTaints() throws Exception {
        Fixture fixture = new Fixture("1.1", true);
        fixture.observeUnsupported = true;
        fixture.streams.add(List.of(snapshot(0)));

        Outcome outcome = fixture.run(Route.SUBSCRIBE);

        assertEquals(1, fixture.count("observe"));
        assertEquals(1, fixture.count("subscribeToTask"));
        assertEquals(1, outcome.events.size());
        assertInstanceOf(TaskEvent.class, outcome.events.get(0));
        fixture.assertGuardedRejectedWithoutIo();
    }

    @Test
    void observeFailureOtherThanUnsupportedDoesNotFallBack() {
        Fixture fixture = new Fixture("1.1", true);
        fixture.observeFailure = new IllegalStateException("boom");

        assertThrows(IllegalStateException.class, () -> fixture.run(Route.SUBSCRIBE));

        assertEquals(1, fixture.count("observe"));
        assertEquals(0, fixture.count("subscribeToTask"));
    }

    @Test
    void unsupportedStreamingCompanionFallsBackToEstablishedStreamingAndStillReduces() throws Exception {
        Fixture fixture = new Fixture("1.1", true);
        fixture.companionUnsupported = true;
        fixture.streams.add(List.of(snapshot(0)));

        Outcome outcome = fixture.run(Route.SEND_STREAMING);

        assertEquals(1, fixture.count("sendMessageStreaming"));
        assertEquals(1, outcome.events.size());
        fixture.assertGuardedRejectedWithoutIo();
    }

    @Test
    void unsupportedUnaryCompanionFallsBackToEstablishedUnaryAndStillReduces() throws Exception {
        Fixture fixture = new Fixture("1.1", false);
        fixture.companionUnsupported = true;
        fixture.unary.add(snapshot(0));

        Outcome outcome = fixture.run(Route.SEND_UNARY);

        assertEquals(1, fixture.count("sendMessage"));
        assertEquals(1, outcome.events.size());
        fixture.assertGuardedRejectedWithoutIo();
    }

    // ---- explicit 1.0 --------------------------------------------------------------------------------------------

    @Test
    void explicitOneZeroSendKeepsHistoricalPathAndTaskManagerSemantics() throws Exception {
        Fixture fixture = new Fixture("1.0", true);
        fixture.streams.add(List.of(snapshot(8),
                new TaskStatusUpdateEvent(TASK, new TaskStatus(TaskState.TASK_STATE_COMPLETED), CTX, null, 2)));

        Outcome outcome = fixture.run(Route.SEND_STREAMING);

        assertEquals(0, fixture.count("sendInput") + fixture.count("streamInput") + fixture.count("observe"));
        assertEquals(1, fixture.count("sendMessageStreaming"));
        TaskUpdateEvent update = assertInstanceOf(TaskUpdateEvent.class, outcome.events.get(1));
        assertEquals(TaskState.TASK_STATE_COMPLETED, update.getTask().status().state());
    }

    @Test
    void explicitOneZeroUnarySendAndSubscribeKeepHistoricalPath() throws Exception {
        Fixture unary = new Fixture("1.0", false);
        unary.unary.add(snapshot(0));
        Outcome sent = unary.run(Route.SEND_UNARY);
        assertEquals(1, unary.count("sendMessage"));
        assertEquals(0, unary.count("sendInput"));
        assertSame(unary.lastUnaryEvent, assertInstanceOf(TaskEvent.class, sent.events.get(0)).getTask());

        Fixture streaming = new Fixture("1.0", true);
        streaming.streams.add(List.of(snapshot(0)));
        streaming.run(Route.SUBSCRIBE);
        assertEquals(1, streaming.count("subscribeToTask"));
        assertEquals(0, streaming.count("observe"));
    }

    @Test
    void explicitOneZeroRejectsObservedInputBeforeIoAndGenerationZeroDoesNotBlockPlainSends() throws Exception {
        Fixture fixture = new Fixture("1.0", true);
        fixture.streams.add(List.of(snapshot(0)));
        fixture.run(Route.SEND_STREAMING);
        int before = fixture.io.get();

        assertThrows(A2AClientException.class,
                () -> fixture.client.sendInputObserved(fixture.plainParams(TASK), null));
        assertEquals(before, fixture.io.get());

        fixture.streams.add(List.of(snapshot(0)));
        fixture.run(Route.SEND_STREAMING);
        assertEquals(before + 1, fixture.io.get());
    }

    // ---- fixture ------------------------------------------------------------------------------------------------------

    private static final class Outcome {
        final List<ClientEvent> events = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        ObservationEnd end;

        Task finalTask() {
            if (end != null) {
                return end.finalView().task();
            }
            ClientEvent last = events.get(events.size() - 1);
            if (last instanceof TaskEvent taskEvent) {
                return taskEvent.getTask();
            }
            return ((TaskUpdateEvent) last).getTask();
        }
    }

    private static final class Fixture {
        final AtomicInteger io = new AtomicInteger();
        final Map<String, Integer> calls = new ConcurrentHashMap<>();
        final Deque<List<StreamingEventKind>> streams = new ArrayDeque<>();
        final Deque<EventKind> unary = new ArrayDeque<>();
        final AtomicReference<SendInputParams> lastStreamInput = new AtomicReference<>();
        final boolean streaming;
        volatile boolean observeUnsupported;
        volatile boolean companionUnsupported;
        volatile RuntimeException observeFailure;
        volatile EventKind lastUnaryEvent;
        final Client client;

        Fixture(String version, boolean streaming) {
            this.streaming = streaming;
            VersionedClientTransport delegate = transport(version, this::invoke);
            AgentCard card = AgentCard.builder().name("test").description("test").version("test")
                    .capabilities(AgentCapabilities.builder().streaming(streaming).build())
                    .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of())
                    .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost", null, version)))
                    .build();
            client = new Client(card,
                    new ClientConfig.Builder().setProtocolVersion(version).setStreaming(streaming).build(),
                    new SelectedClientTransport(delegate, delegate.protocolVersion(), delegate.capabilities()),
                    List.of(), null);
        }

        int count(String method) {
            return calls.getOrDefault(method, 0);
        }

        private Object invoke(String method, Object[] args) throws Throwable {
            io.incrementAndGet();
            calls.merge(method, 1, Integer::sum);
            switch (method) {
                case "sendInput" -> {
                    if (companionUnsupported) {
                        throw new UnsupportedOperationException(method);
                    }
                    TransportCall<TransportResult<EventKind>> call = TransportCall.create(() -> { });
                    EventKind value = unary.poll();
                    lastUnaryEvent = value;
                    call.complete(new TransportResult<>(value, value instanceof Task task
                            ? List.of(new TaskDecodeEvidence(task.id(), List.of(), false)) : List.of()));
                    return call;
                }
                case "streamInput" -> {
                    if (companionUnsupported) {
                        throw new UnsupportedOperationException(method);
                    }
                    lastStreamInput.set((SendInputParams) args[0]);
                    return observation(streams.poll());
                }
                case "observe" -> {
                    if (observeFailure != null) {
                        throw observeFailure;
                    }
                    if (observeUnsupported) {
                        throw new UnsupportedOperationException(method);
                    }
                    return observation(streams.poll());
                }
                case "sendMessage" -> {
                    lastUnaryEvent = unary.poll();
                    return lastUnaryEvent;
                }
                case "sendMessageStreaming" -> {
                    replay(streams.poll(), args[1]);
                    return null;
                }
                case "subscribeToTask" -> {
                    replay(streams.poll(), args[1]);
                    return null;
                }
                default -> throw new UnsupportedOperationException(method);
            }
        }

        @SuppressWarnings("unchecked")
        private static void replay(List<StreamingEventKind> events, Object handler) {
            for (StreamingEventKind event : events) {
                ((Consumer<StreamingEventKind>) handler).accept(event);
            }
        }

        private static TransportObservation observation(List<StreamingEventKind> events) {
            TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
            observation.markReady();
            for (StreamingEventKind event : events) {
                observation.publish(new TransportFrame(event, false, List.of(), null, 1));
            }
            observation.complete();
            return observation;
        }

        Outcome run(Route route) throws Exception {
            Outcome outcome = new Outcome();
            List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of((event, card) -> outcome.events.add(event));
            Consumer<Throwable> errors = outcome.errors::add;
            switch (route) {
                case SEND_UNARY, SEND_STREAMING ->
                        client.sendMessage(plainParams(TASK).requireMessageOnly(), consumers, errors, null);
                case SUBSCRIBE -> client.subscribeToTask(new TaskIdParams(TASK), consumers, errors, null);
                case CALLBACK_SEND_INPUT -> client.sendInput(plainParams(TASK), consumers, error -> {
                    if (error != null) {
                        errors.accept(error);
                    }
                }, null);
                case SEND_INPUT_OBSERVED -> {
                    TaskObservation observed = client.sendInputObserved(plainParams(TASK), null);
                    outcome.end = observed.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
                    assertEquals(ObservationEnd.Kind.COMPLETED, outcome.end.kind());
                }
            }
            return outcome;
        }

        SendInputParams plainParams(String taskId) {
            return SendInputParams.builder().message(Message.builder().role(Message.Role.ROLE_USER)
                    .parts(new TextPart("go")).taskId(taskId).contextId(CTX).build()).build();
        }

        SendInputParams guardedParams(String taskId) {
            return SendInputParams.builder().message(Message.builder().role(Message.Role.ROLE_USER)
                    .parts(new TextPart("go")).taskId(taskId).contextId(CTX).build())
                    .configuration(MessageSendConfiguration.builder().ifGenerationMatch(5L).build()).build();
        }

        /** Sends a guarded input that must be accepted and dispatched; the peer answers with a snapshot. */
        void guardedFollowUp(long responseGeneration) throws Exception {
            guardedFollowUpFor(TASK, responseGeneration);
        }

        void guardedFollowUpFor(String taskId, long responseGeneration) throws Exception {
            if (streaming) {
                streams.add(List.of(snapshot(responseGeneration)));
            } else {
                unary.add(snapshot(responseGeneration));
            }
            int before = count(streaming ? "streamInput" : "sendInput");
            client.sendInput(guardedParams(taskId), List.of(), error -> { }, null);
            assertEquals(before + 1, count(streaming ? "streamInput" : "sendInput"));
        }

        /** The guarded input is rejected through sendInput, sendInputObserved and sendMessage without any I/O. */
        void assertGuardedRejectedWithoutIo() {
            int before = io.get();
            SendInputParams guarded = guardedParams(TASK);
            assertThrows(A2AClientException.class, () -> client.sendInput(guarded, List.of(), error -> { }, null));
            assertThrows(A2AClientException.class, () -> client.sendInputObserved(guarded, null));
            MessageSendParams legacy = MessageSendParams.builder().message(guarded.message())
                    .configuration(guarded.configuration()).build();
            assertThrows(A2AClientException.class, () -> client.sendMessage(legacy, List.of(), error -> { }, null));
            assertEquals(before, io.get(), "a rejected guard must perform zero network requests");
        }
    }

    private static Task snapshot(long generation) {
        return Task.builder().id(TASK).contextId(CTX).status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .generation(generation).history(List.of()).artifacts(List.of()).timeline(List.of()).build();
    }

    private static Message message(String text) {
        return Message.builder().role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart(text))).build();
    }

    private static VersionedClientTransport transport(String version, Invocation invocation) {
        return (VersionedClientTransport) Proxy.newProxyInstance(VersionedClientTransport.class.getClassLoader(),
                new Class<?>[] {VersionedClientTransport.class}, (instance, method, args) -> {
                    if (method.getName().equals("protocolVersion")) {
                        return version;
                    }
                    if (method.getName().equals("capabilities")) {
                        return Set.of(ClientFeature.ORDINARY, ClientFeature.UNKNOWN_VARIANT_SKIP);
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    return invocation.invoke(method.getName(), args == null ? new Object[0] : args);
                });
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
