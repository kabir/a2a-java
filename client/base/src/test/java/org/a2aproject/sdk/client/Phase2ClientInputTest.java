package org.a2aproject.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.spi.ClientFeature;
import org.a2aproject.sdk.client.transport.spi.SpiCallOptions;
import org.a2aproject.sdk.client.transport.spi.TransportCall;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.TransportResult;
import org.a2aproject.sdk.client.transport.spi.VersionedClientTransport;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class Phase2ClientInputTest {

    @Test
    void unarySendInputForwardsCompanionAndCompletesWithTaskEventOnce() throws Exception {
        AtomicReference<SendInputParams> received = new AtomicReference<>();
        AtomicReference<TransportCall<TransportResult<org.a2aproject.sdk.spec.EventKind>>> pending = new AtomicReference<>();
        VersionedClientTransport delegate = transport("1.1", (method, args) -> {
            if (method.equals("sendInput")) {
                received.set((SendInputParams) args[0]);
                TransportCall<TransportResult<org.a2aproject.sdk.spec.EventKind>> call = TransportCall.create(() -> { });
                pending.set(call);
                return call;
            }
            throw new UnsupportedOperationException(method);
        });
        Client client = client(delegate, "1.1", false, false);
        Message input = message("input");
        SendInputParams params = SendInputParams.builder().message(input).tenant("canonical")
                .configuration(org.a2aproject.sdk.spec.MessageSendConfiguration.builder()
                        .ifGenerationMatch(12L).timelineLength(4).build())
                .metadata(Map.of("trace", "value")).build();
        List<ClientEvent> events = new ArrayList<>();
        AtomicInteger completionCount = new AtomicInteger();
        AtomicInteger eventsAtCompletion = new AtomicInteger();
        AtomicReference<Throwable> terminal = new AtomicReference<>();

        client.sendInput(params, consumers(events), error -> {
            terminal.set(error);
            eventsAtCompletion.set(events.size());
            completionCount.incrementAndGet();
        }, null);

        assertSame(params, received.get());
        Task response = task("task", "context", List.of(), Map.of());
        var evidence = new org.a2aproject.sdk.client.transport.spi.TaskDecodeEvidence("task", List.of(7L), true);
        var envelope = new TransportResult<org.a2aproject.sdk.spec.EventKind>(response, List.of(evidence));
        pending.get().complete(envelope);
        assertSame(envelope, pending.get().result().toCompletableFuture().join());
        assertEquals(1, events.size());
        assertSame(response, ((TaskEvent) events.get(0)).getTask());
        assertEquals(1, completionCount.get());
        assertEquals(1, eventsAtCompletion.get());
        assertEquals(null, terminal.get());
    }

    @Test
    void explicitOneZeroRejectsAdvancedInputAndDroppedOptionsBeforeDelegate() {
        AtomicInteger calls = new AtomicInteger();
        VersionedClientTransport delegate = transport("1.0", (method, args) -> {
            calls.incrementAndGet();
            return null;
        });
        Client client = client(delegate, "1.0", false, false);

        SendInputParams guarded = SendInputParams.builder().message(message("input"))
                .configuration(org.a2aproject.sdk.spec.MessageSendConfiguration.builder()
                        .ifGenerationMatch(4L).build())
                .build();
        assertThrows(A2AClientException.class, () -> client.sendInput(guarded, consumers(new ArrayList<>()), null, null));

        SendInputParams artifactOnly = SendInputParams.builder()
                .artifactUpdates(List.of(new org.a2aproject.sdk.spec.TaskArtifactUpdateEvent(
                        "task", org.a2aproject.sdk.spec.Artifact.builder().artifactId("a")
                                .parts(List.of(new TextPart("x"))).build(), "context", null, null, null, 0)))
                .build();
        assertThrows(A2AClientException.class,
                () -> client.sendInput(artifactOnly, consumers(new ArrayList<>()), null, null));

        assertThrows(A2AClientException.class,
                () -> client.getTask(new org.a2aproject.sdk.spec.TaskQueryParams("task", null, null, 5L, null), null));
        assertThrows(A2AClientException.class,
                () -> client.getTask(new org.a2aproject.sdk.spec.TaskQueryParams("task", null, null, null, 3), null));
        var versionedConfig = org.a2aproject.sdk.spec.TaskPushNotificationConfig.builder()
                .url("https://example.test/hook").protocolVersion("1.1").build();
        assertThrows(A2AClientException.class,
                () -> client.createTaskPushNotificationConfiguration(versionedConfig, null));
        assertEquals(0, calls.get());
    }

    @Test
    void streamingMessageUpdateKeepsBaselineAndOriginalUpdate() throws Exception {
        Message baselineMessage = message("baseline");
        var baselineArtifact = org.a2aproject.sdk.spec.Artifact.builder().artifactId("baseline-artifact")
                .parts(List.of(new TextPart("baseline"))).build();
        var baselineTimeline = List.of(new org.a2aproject.sdk.spec.TimelineEntry(8,
                new org.a2aproject.sdk.spec.TimelineEntry.MessageValue(baselineMessage)));
        Task baseline = new Task("task", "context", new TaskStatus(TaskState.TASK_STATE_WORKING),
                List.of(baselineArtifact), List.of(baselineMessage), Map.of("baseline", true), 8, baselineTimeline);
        Message updateMessage = message("next");
        TaskMessageUpdateEvent update = new TaskMessageUpdateEvent("task", "context", updateMessage,
                Map.of("update", true), 2);
        TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
        observation.markReady();
        observation.publish(new TransportFrame(baseline, false, List.of(), null, 1));
        observation.publish(new TransportFrame(update, false, List.of(), null, 1));
        observation.complete();
        assertFalse(observation.complete());
        VersionedClientTransport delegate = transport("1.1", (method, args) -> {
            if (method.equals("streamInput")) {
                return observation;
            }
            throw new UnsupportedOperationException(method);
        });
        Client client = client(delegate, "1.1", true, true);
        List<ClientEvent> events = new ArrayList<>();
        AtomicInteger completionCount = new AtomicInteger();
        AtomicInteger eventsAtCompletion = new AtomicInteger();

        client.sendInput(SendInputParams.builder().message(message("input")).build(),
                consumers(events), error -> {
                    assertEquals(null, error);
                    eventsAtCompletion.set(events.size());
                    completionCount.incrementAndGet();
                }, null);

        assertEquals(2, events.size());
        assertInstanceOf(TaskEvent.class, events.get(0));
        TaskUpdateEvent callback = assertInstanceOf(TaskUpdateEvent.class, events.get(1));
        assertSame(update, callback.getUpdateEvent());
        assertEquals(List.of(baselineMessage, updateMessage), callback.getTask().history());
        assertEquals(Map.of("baseline", true, "update", true), callback.getTask().metadata());
        assertEquals(8, callback.getTask().generation());
        assertSame(baselineTimeline, callback.getTask().timeline());
        assertSame(baselineArtifact, callback.getTask().artifacts().get(0));
        assertEquals(1, completionCount.get());
        assertEquals(2, eventsAtCompletion.get());
    }

    @Test
    void failedObservationSignalsTerminalAfterActiveEventConsumerReturns() throws Exception {
        TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), () -> { });
        observation.markReady();
        VersionedClientTransport delegate = transport("1.1", (method, args) -> {
            if (method.equals("streamInput")) {
                return observation;
            }
            throw new UnsupportedOperationException(method);
        });
        Client client = client(delegate, "1.1", true, true);
        CountDownLatch eventEntered = new CountDownLatch(1);
        CountDownLatch releaseEvent = new CountDownLatch(1);
        CountDownLatch terminalDelivered = new CountDownLatch(1);
        AtomicBoolean eventConsumerActive = new AtomicBoolean();
        AtomicBoolean terminalRanWhileEventActive = new AtomicBoolean();
        AtomicInteger completionCount = new AtomicInteger();
        AtomicReference<Throwable> terminalFailure = new AtomicReference<>();
        RuntimeException failure = new RuntimeException("stream failed");

        client.sendInput(SendInputParams.builder().message(message("input")).build(), List.of((event, card) -> {
            eventConsumerActive.set(true);
            eventEntered.countDown();
            try {
                if (!releaseEvent.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("event consumer was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            } finally {
                eventConsumerActive.set(false);
            }
        }), error -> {
            terminalFailure.set(error);
            terminalRanWhileEventActive.set(eventConsumerActive.get());
            completionCount.incrementAndGet();
            terminalDelivered.countDown();
        }, null);

        Thread publisher = new Thread(() -> observation.publish(
                new TransportFrame(task("task", "context", List.of(), Map.of()), false, List.of(), null, 1)));
        publisher.start();
        try {
            assertTrue(eventEntered.await(5, TimeUnit.SECONDS));
            assertTrue(observation.fail(failure));
            assertEquals(0, completionCount.get(), "terminal callback must wait for serialized event delivery");
        } finally {
            releaseEvent.countDown();
            publisher.join(TimeUnit.SECONDS.toMillis(5));
        }

        assertFalse(publisher.isAlive());
        assertTrue(terminalDelivered.await(5, TimeUnit.SECONDS));
        assertSame(failure, terminalFailure.get());
        assertFalse(terminalRanWhileEventActive.get());
        assertEquals(1, completionCount.get());
    }

    @Test
    void cancelledObservationCompletesErrorCallbackOnce() throws Exception {
        AtomicInteger detachCount = new AtomicInteger();
        TransportObservation observation = TransportObservation.create(SpiCallOptions.defaults(), detachCount::incrementAndGet);
        observation.markReady();
        VersionedClientTransport delegate = transport("1.1", (method, args) -> {
            if (method.equals("streamInput")) {
                return observation;
            }
            throw new UnsupportedOperationException(method);
        });
        Client client = client(delegate, "1.1", true, true);
        AtomicInteger completionCount = new AtomicInteger();
        AtomicReference<Throwable> terminalFailure = new AtomicReference<>();

        client.sendInput(SendInputParams.builder().message(message("input")).build(), consumers(new ArrayList<>()),
                error -> {
                    terminalFailure.set(error);
                    completionCount.incrementAndGet();
                }, null);

        observation.cancel();
        observation.cancel();
        assertFalse(observation.complete());

        assertInstanceOf(CancellationException.class, terminalFailure.get());
        assertEquals(1, completionCount.get());
        assertEquals(1, detachCount.get());
    }

    @Test
    void asynchronousFailureCompletesErrorCallbackOnce() throws Exception {
        AtomicReference<TransportCall<TransportResult<org.a2aproject.sdk.spec.EventKind>>> pending = new AtomicReference<>();
        VersionedClientTransport delegate = transport("1.1", (method, args) -> {
            if (method.equals("sendInput")) {
                TransportCall<TransportResult<org.a2aproject.sdk.spec.EventKind>> call = TransportCall.create(() -> { });
                pending.set(call);
                return call;
            }
            throw new UnsupportedOperationException(method);
        });
        Client client = client(delegate, "1.1", false, false);
        AtomicInteger completions = new AtomicInteger();
        AtomicReference<Throwable> terminal = new AtomicReference<>();
        RuntimeException failure = new RuntimeException("transport failed");

        client.sendInput(SendInputParams.builder().message(message("input")).build(), consumers(new ArrayList<>()),
                error -> {
                    terminal.set(error);
                    completions.incrementAndGet();
                }, null);
        pending.get().fail(failure);

        assertSame(failure, terminal.get());
        assertEquals(1, completions.get());
    }

    @Test
    void explicitOneZeroAllowsMessageOnlyCompanionThroughLegacyTransport() throws Exception {
        Message response = message("legacy-response");
        VersionedClientTransport delegate = transport("1.0", (method, args) -> {
            if (method.equals("sendMessage")) {
                return response;
            }
            throw new UnsupportedOperationException(method);
        });
        Client client = client(delegate, "1.0", false, false);
        List<ClientEvent> events = new ArrayList<>();
        AtomicInteger completions = new AtomicInteger();

        client.sendInput(SendInputParams.from(org.a2aproject.sdk.spec.MessageSendParams.builder()
                .message(message("legacy-input")).build()), consumers(events), error -> {
                    assertEquals(null, error);
                    completions.incrementAndGet();
                }, null);

        assertEquals(1, events.size());
        assertSame(response, ((MessageEvent) events.get(0)).getMessage());
        assertEquals(1, completions.get());
    }

    @Test
    void artifactInputRequiresAgentCapabilityBeforeTransportCall() {
        AtomicInteger calls = new AtomicInteger();
        VersionedClientTransport delegate = transport("1.1", (method, args) -> {
            calls.incrementAndGet();
            return null;
        });
        Client client = client(delegate, "1.1", false, false);
        SendInputParams artifact = SendInputParams.builder()
                .artifactUpdates(List.of(new org.a2aproject.sdk.spec.TaskArtifactUpdateEvent(
                        "task", org.a2aproject.sdk.spec.Artifact.builder().artifactId("a")
                                .parts(List.of(new TextPart("x"))).build(), "context", null, null, null, 0)))
                .build();

        assertThrows(A2AClientException.class,
                () -> client.sendInput(artifact, consumers(new ArrayList<>()), null, null));
        assertEquals(0, calls.get());
    }

    private static Client client(VersionedClientTransport delegate, String version, boolean streaming,
            boolean userArtifacts) {
        AgentCard card = AgentCard.builder().name("test").description("test").version("test")
                .capabilities(AgentCapabilities.builder().streaming(streaming).userArtifacts(userArtifacts).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost", null, version))).build();
        return new Client(card, new ClientConfig.Builder().setProtocolVersion(version).setStreaming(streaming).build(),
                selected(delegate), List.of(), null);
    }

    private static SelectedClientTransport selected(VersionedClientTransport delegate) {
        return new SelectedClientTransport(delegate, delegate.protocolVersion(), delegate.capabilities());
    }

    private static VersionedClientTransport transport(String version, TransportInvocation invocation) {
        @SuppressWarnings("unchecked")
        VersionedClientTransport proxy = (VersionedClientTransport) Proxy.newProxyInstance(
                VersionedClientTransport.class.getClassLoader(), new Class<?>[] {VersionedClientTransport.class},
                (instance, method, args) -> {
                    if (method.getName().equals("protocolVersion")) {
                        return version;
                    }
                    if (method.getName().equals("capabilities")) {
                        return Set.of(ClientFeature.ORDINARY, ClientFeature.UNKNOWN_VARIANT_SKIP);
                    }
                    return invocation.invoke(method.getName(), args == null ? new Object[0] : args);
                });
        return proxy;
    }

    private static BiConsumer<ClientEvent, AgentCard> consumer(List<ClientEvent> events) {
        return (event, card) -> events.add(event);
    }

    private static List<BiConsumer<ClientEvent, AgentCard>> consumers(List<ClientEvent> events) {
        return List.of(consumer(events));
    }

    private static Message message(String text) {
        return Message.builder().role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart(text))).build();
    }

    private static Task task(String id, String contextId, List<Message> history, Map<String, Object> metadata) {
        return Task.builder().id(id).contextId(contextId)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).history(history).metadata(metadata).build();
    }

    @FunctionalInterface
    private interface TransportInvocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
