package org.a2aproject.sdk.server.apps.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.ObservationEnd;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskObservation;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.TaskView;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;

/** Client wiring, server hooks and result helpers shared by the Phase 3 scenario families. */
public abstract class Phase3ScenarioSupport {

    private final List<String> createdTasks = new CopyOnWriteArrayList<>();
    private final List<Client> openClients = new CopyOnWriteArrayList<>();

    protected abstract String transportProtocol();

    protected abstract String transportUrl();

    protected abstract void configureTransport(ClientBuilder builder);

    protected abstract @Nullable Task storedTask(String taskId);

    protected abstract void deleteStoredTask(String taskId);

    @AfterEach
    void phase3Cleanup() {
        for (Client client : openClients) {
            client.close();
        }
        openClients.clear();
        for (String taskId : createdTasks) {
            deleteStoredTask(taskId);
        }
        createdTasks.clear();
    }

    protected final void track(String taskId) {
        createdTasks.add(taskId);
    }

    // ---- results ----------------------------------------------------------------------------------------------

    protected record Outcome(@Nullable Task task, @Nullable Throwable failure, @Nullable Long through,
            @Nullable TaskView view) {
        Outcome(@Nullable Task task, @Nullable Throwable failure) {
            this(task, failure, null, null);
        }
    }

    protected final Outcome observe(Client client, SendInputParams params) {
        try {
            TaskObservation observation = client.sendInputObserved(params, null);
            ObservationEnd end = observation.completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            if (end.kind() != ObservationEnd.Kind.COMPLETED) {
                return new Outcome(end.finalView().task(), end.cause() == null
                        ? new IllegalStateException("observation " + end.kind()) : end.cause());
            }
            return new Outcome(end.finalView().task(), null, end.finalView().throughGeneration(), end.finalView());
        } catch (A2AClientException | ExecutionException | TimeoutException failure) {
            return new Outcome(null, failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Outcome(null, interrupted);
        }
    }

    /**
     * Sends through the callback API and waits until the stream is quiescent: a failure arrived, or events arrived
     * and none followed for a short settle period. A synchronous client failure is recorded like an asynchronous one.
     */
    protected final Collected sendCollected(Client client, SendInputParams params) throws InterruptedException {
        Collected collected = new Collected();
        try {
            client.sendInput(params, List.of(collected.consumer()), collected.errors(), null);
        } catch (A2AClientException failure) {
            collected.failure.compareAndSet(null, failure);
            return collected;
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline && collected.failure.get() == null && collected.events.isEmpty()) {
            Thread.sleep(50);
        }
        int size;
        do {
            size = collected.events.size();
            Thread.sleep(400);
        } while (collected.events.size() != size && System.nanoTime() < deadline);
        return collected;
    }

    /**
     * Outcome of a callback-API send. An existing-task stream has no snapshot frame, so there is no task view; the
     * outcome's {@code through} is the largest generation stamped on a streamed update event.
     */
    protected final Outcome attemptCollected(Client client, SendInputParams params) throws InterruptedException {
        Collected collected = sendCollected(client, params);
        return new Outcome(null, collected.failure.get(), collected.maxUpdateEventGeneration(), null);
    }

    protected static <T extends Throwable> @Nullable T find(@Nullable Throwable failure, Class<T> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        return null;
    }

    protected static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        return find(failure, type) != null;
    }

    /** Asserts that {@code failure} carries a server error of exactly this type and that its text names the cause. */
    protected static <T extends Throwable> T assertTypedFailure(@Nullable Throwable failure, Class<T> type,
            @Nullable String messageContains) {
        assertNotNull(failure, "a failure of type " + type.getSimpleName() + " was expected");
        T typed = find(failure, type);
        assertNotNull(typed, () -> "expected " + type.getSimpleName() + " in the cause chain but got: "
                + describe(failure));
        if (messageContains != null) {
            assertTrue(describe(failure).toLowerCase(Locale.ROOT).contains(messageContains.toLowerCase(Locale.ROOT)),
                    () -> "the error must be attributable to '" + messageContains + "': " + describe(failure));
        }
        return typed;
    }

    protected static String describe(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            text.append(cause.getClass().getSimpleName()).append(':').append(cause.getMessage()).append(" <- ");
        }
        return text.toString();
    }

    // ---- values -----------------------------------------------------------------------------------------------

    protected static SendInputParams input(Task base, String messageId, String text, @Nullable Long guard) {
        SendInputParams.Builder builder = SendInputParams.builder().message(userMessage(base, messageId, text));
        if (guard != null) {
            builder.configuration(MessageSendConfiguration.builder().ifGenerationMatch(guard).build());
        }
        return builder.build();
    }

    protected static Message userMessage(Task base, String messageId, String text) {
        return Message.builder().messageId(messageId).taskId(base.id()).contextId(base.contextId())
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart(text))).build();
    }

    protected static TaskArtifactUpdateEvent artifactUpdate(Task task, String artifactId) {
        return new TaskArtifactUpdateEvent(task.id(), Artifact.builder().artifactId(artifactId)
                .parts(List.of(new TextPart(artifactId))).build(), task.contextId(), false, true, null, 0);
    }

    protected static List<Long> timelineGenerations(Task task) {
        return task.timeline().stream().map(TimelineEntry::generation).toList();
    }

    protected static long messageEntries(Task task, String idPrefix) {
        return task.timeline().stream().filter(entry -> entry.entry() instanceof TimelineEntry.MessageValue value
                && value.message().messageId().startsWith(idPrefix)).count();
    }

    // ---- clients and tasks ----------------------------------------------------------------------------------------

    protected final Task createWorkingTask() throws Exception {
        return createTask("phase3:work");
    }

    protected final Task createTask(String branch) throws Exception {
        Collected seen = new Collected();
        open("1.1", false, true).sendMessage(Message.builder().messageId("p3-create-" + System.nanoTime())
                .contextId("p3-ctx-" + System.nanoTime()).role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(branch))).build(), List.of(seen.consumer()), seen.errors(), null);
        assertTrue(seen.awaitEvents(1, 15), "task creation must answer");
        Task created = seen.lastTask();
        track(created.id());
        return storedTask(created.id());
    }

    protected final Client open(String version, boolean streaming, boolean polling) throws A2AClientException {
        AgentCard card = AgentCard.builder().name("test-card").description("A test agent card").version("1.0")
                .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(true)
                        .extendedAgentCard(true).userArtifacts(version.equals("1.1")).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(transportProtocol(), transportUrl(), null, version)))
                .build();
        ClientBuilder builder = Client.builder(card).clientConfig(new ClientConfig.Builder().setStreaming(streaming)
                .setPolling(polling).setProtocolVersion(version).build());
        configureTransport(builder);
        Client client = builder.build();
        openClients.add(client);
        return client;
    }

    protected static boolean terminal(ClientEvent event) {
        Task task = event instanceof TaskEvent taskEvent ? taskEvent.getTask()
                : event instanceof TaskUpdateEvent update ? update.getTask() : null;
        return task != null && task.status().state().isFinal();
    }

    protected static void assertUntouched(Task before, @Nullable Task after, String what) {
        assertNotNull(after, what + ": the task must still exist");
        assertEquals(before, after, what + ": a rejected input must leave the whole task unchanged");
    }

    /** Collects a public callback stream; a finite stream's quiescence is observed through its events. */
    protected static final class Collected {
        public final List<ClientEvent> events = new CopyOnWriteArrayList<>();
        public final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final CountDownLatch failed = new CountDownLatch(1);

        public BiConsumer<ClientEvent, AgentCard> consumer() {
            return (event, card) -> events.add(event);
        }

        public Consumer<Throwable> errors() {
            return error -> {
                if (error != null && !(error instanceof java.util.concurrent.CancellationException)) {
                    failure.compareAndSet(null, error);
                    failed.countDown();
                }
            };
        }

        public boolean awaitEvents(int count, int seconds) throws InterruptedException {
            for (int i = 0; i < seconds * 20 && events.size() < count; i++) {
                Thread.sleep(50);
            }
            return events.size() >= count;
        }

        public boolean awaitTerminal(int seconds) throws InterruptedException {
            for (int i = 0; i < seconds * 20; i++) {
                if (events.stream().anyMatch(Phase3ScenarioSupport::terminal) || failure.get() != null) {
                    return failure.get() == null;
                }
                Thread.sleep(50);
            }
            return false;
        }

        public boolean awaitFailure(int seconds) throws InterruptedException {
            return failed.await(seconds, TimeUnit.SECONDS);
        }

        public Task lastTask() {
            Task last = null;
            for (ClientEvent event : events) {
                if (event instanceof TaskEvent taskEvent) {
                    last = taskEvent.getTask();
                } else if (event instanceof TaskUpdateEvent update) {
                    last = update.getTask();
                }
            }
            assertNotNull(last, "a task view must have been delivered");
            return last;
        }

        public List<Long> generations() {
            List<Long> generations = new ArrayList<>();
            for (ClientEvent event : events) {
                if (event instanceof TaskEvent taskEvent) {
                    generations.add(taskEvent.getTask().generation());
                } else if (event instanceof TaskUpdateEvent update) {
                    generations.add(update.getTask().generation());
                }
            }
            return generations;
        }

        /** The largest generation stamped on a streamed update event itself, or 0 if none carries one. */
        public long maxUpdateEventGeneration() {
            long max = 0;
            for (ClientEvent event : events) {
                if (event instanceof TaskUpdateEvent update) {
                    Object raw = update.getUpdateEvent();
                    long generation = raw instanceof TaskStatusUpdateEvent status ? status.generation()
                            : raw instanceof TaskArtifactUpdateEvent artifact ? artifact.generation()
                            : raw instanceof TaskMessageUpdateEvent message ? message.generation() : 0;
                    max = Math.max(max, generation);
                }
            }
            return max;
        }
    }
}
