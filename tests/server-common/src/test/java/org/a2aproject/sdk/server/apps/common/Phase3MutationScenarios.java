package org.a2aproject.sdk.server.apps.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.ObservationEnd;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskObservation;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.spec.A2AClientException;
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
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Reusable P3-T17 public-client scenarios over one real server. Each transport supplies only its client wiring and
 * server-side hooks; the assertions are shared so later transports reuse the same contract. Every request crosses the
 * public {@link Client}; the only direct server access is the store/queue inspection and the explicit
 * lost-publication commit hooks.
 */
public abstract class Phase3MutationScenarios {

    private final List<String> createdTasks = new CopyOnWriteArrayList<>();
    private final List<Client> openClients = new CopyOnWriteArrayList<>();

    protected abstract String transportProtocol();

    protected abstract String transportUrl();

    protected abstract void configureTransport(ClientBuilder builder);

    /** Returns and clears the protocol-version header of every request that reached the server ingress. */
    protected abstract List<String> drainIngress();

    /** Commits {@code task} to the authoritative store without publishing anything (a lost publication). */
    protected abstract void commitWithoutPublication(Task task);

    protected abstract @Nullable Task storedTask(String taskId);

    protected abstract void deleteStoredTask(String taskId);

    protected abstract int childQueueCount(String taskId);

    protected abstract int pushConfigCount(String taskId);

    /** Sends one message to an existing task through the unchanged public 0.3 SDK. */
    protected abstract void send03Message(String taskId, String contextId, String text) throws Exception;

    /**
     * Posts a raw selected-1.1 input whose final artifact element is malformed and returns normally only if the
     * server rejected it with a protocol error.
     */
    protected abstract void assertRawMalformedFinalArtifactRejected(String taskId, String contextId, String otherTaskId)
            throws Exception;

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

    // ---- scenario 1: guard race -----------------------------------------------------------------------------

    @Test
    public void twoPublicClientsRaceTheSameGuardExactlyOneIsAccepted() throws Exception {
        Task base = createWorkingTask();
        long guard = base.generation();
        Client first = open("1.1", false, false);
        Client second = open("1.1", false, false);
        drainIngress();

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Outcome> outcomes = new CopyOnWriteArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (Client client : List.of(first, second)) {
            String id = "race-" + threads.size();
            Thread thread = new Thread(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    outcomes.add(observe(client, input(base, id, "phase3:noop", guard)));
                } catch (Exception failure) {
                    outcomes.add(new Outcome(null, failure));
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join(30_000);
        }

        assertEquals(2, outcomes.size());
        List<Outcome> accepted = outcomes.stream().filter(outcome -> outcome.failure == null).toList();
        List<Outcome> mismatched = outcomes.stream()
                .filter(outcome -> outcome.failure != null && causedBy(outcome.failure, TaskGenerationMismatchError.class))
                .toList();
        assertEquals(1, accepted.size(), () -> "exactly one accepted: " + outcomes);
        assertEquals(1, mismatched.size(), () -> "exactly one typed mismatch: " + outcomes);
        assertEquals(2, drainIngress().size(), "no automatic retry or rebase: one ingress per client");

        Task winnerView = accepted.get(0).task;
        assertTrue(winnerView.generation() > guard);
        Task stored = storedTask(base.id());
        assertEquals(stored.generation(), winnerView.generation(), "send stamp equals the committed head");
        Task got = first.getTask(new TaskQueryParams(base.id(), null, null, null, 10));
        Task listed = first.listTasks(ListTasksParams.builder().contextId(base.contextId()).timelineLength(10).build())
                .tasks().get(0);
        assertEquals(stored.generation(), got.generation());
        assertEquals(stored.generation(), listed.generation());
        assertEquals(timelineGenerations(got), timelineGenerations(listed));
        assertEquals(1, messageEntries(got, "race-"), "only the accepted input is on the timeline");
        assertEquals(winnerView.generation(), got.timeline().get(got.timeline().size() - 1).generation());
    }

    // ---- scenario 2: message plus artifacts, artifact-only and malformed inputs -------------------------------

    @Test
    public void messagePlusMultipleArtifactsAndArtifactOnlyInputMutateAtomically() throws Exception {
        Task base = createWorkingTask();
        Client client = open("1.1", false, false);

        Outcome both = observe(client, SendInputParams.builder().message(userMessage(base, "p3-both", "phase3:noop"))
                .artifactUpdates(List.of(artifactUpdate(base, "p3-u1"), artifactUpdate(base, "p3-u2"))).build());
        assertNull(both.failure, () -> "message + artifacts must be accepted: " + both.failure);
        Task afterBoth = storedTask(base.id());
        assertTrue(afterBoth.generation() > base.generation());
        assertEquals(List.of("p3-u1", "p3-u2"), afterBoth.artifacts().stream().map(Artifact::artifactId)
                .filter(id -> id.startsWith("p3-u")).sorted().toList());
        assertTrue(afterBoth.artifacts().stream().filter(a -> a.artifactId().startsWith("p3-u"))
                .allMatch(a -> a.role() == Message.Role.ROLE_USER), "client-written artifacts carry the USER origin");

        Outcome only = observe(client, SendInputParams.builder().artifactUpdates(List.of(artifactUpdate(base, "p3-u3")))
                .build());
        assertNull(only.failure, () -> "artifact-only input to an existing task must be accepted: " + only.failure);
        Task afterOnly = storedTask(base.id());
        assertTrue(afterOnly.generation() > afterBoth.generation());
        assertEquals(afterBoth.history().size(), afterOnly.history().size(), "artifact-only adds no message");
        assertTrue(afterOnly.artifacts().stream().anyMatch(a -> a.artifactId().equals("p3-u3")));

        // An older 1.0 client reading the same task never sees the USER artifacts or the new fields.
        Client legacy = open("1.0", false, false);
        Task legacyView = legacy.getTask(new TaskQueryParams(base.id()));
        assertTrue(legacyView.artifacts().stream().noneMatch(a -> a.artifactId().startsWith("p3-u")));
        assertEquals(0, legacyView.generation());
        assertTrue(legacyView.timeline().isEmpty());
    }

    @Test
    public void malformedInputsCauseNoPartialMutation() throws Exception {
        Task base = createWorkingTask();
        Client client = open("1.1", false, false);
        Task before = storedTask(base.id());
        Task other = createWorkingTask();
        Task otherBefore = storedTask(other.id());

        TaskArtifactUpdateEvent agentOrigin = new TaskArtifactUpdateEvent(base.id(), Artifact.builder().artifactId("p3-m1")
                .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("p3-m1"))).build(), base.contextId(), false,
                true, null, 0);
        Message wrongContext = Message.builder().messageId("p3-ctx").taskId(base.id()).contextId("another-context")
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("phase3:noop"))).build();
        Message unknownTask = Message.builder().messageId("p3-unknown").taskId("p3-no-such-task")
                .contextId(base.contextId()).role(Message.Role.ROLE_USER).parts(List.of(new TextPart("phase3:noop")))
                .build();
        List<SendInputParams> malformed = List.of(
                SendInputParams.builder().message(userMessage(base, "p3-role", "phase3:noop"))
                        .artifactUpdates(List.of(agentOrigin)).build(),
                SendInputParams.builder().message(wrongContext).build(),
                SendInputParams.builder().message(unknownTask).build(),
                // a tenant whose registration does not provision the selected version
                SendInputParams.builder().message(userMessage(base, "p3-tenant", "phase3:noop")).tenant("phase1-2").build());
        for (SendInputParams params : malformed) {
            Outcome outcome = observe(client, params);
            assertNotNull(outcome.failure, () -> "malformed input must be rejected: " + params);
            assertUnchanged(before, storedTask(base.id()));
            assertUnchanged(otherBefore, storedTask(other.id()));
        }
        assertRawMalformedFinalArtifactRejected(base.id(), base.contextId(), other.id());
        assertUnchanged(before, storedTask(base.id()));
    }

    // ---- scenario 3: direct-Message no-task branch ------------------------------------------------------------

    @Test
    public void directMessageCreatesNoTaskConfigPublicationOrQueueOnEveryUnaryAndStreamingEntry() throws Exception {
        for (boolean streaming : new boolean[] {false, true}) {
            for (boolean returnImmediately : new boolean[] {false, true}) {
                Client client = open("1.1", streaming, false);
                int seen = Phase3Executions.count();
                Collected collected = new Collected();
                String contextId = "p3-direct-" + streaming + "-" + returnImmediately;
                Message message = Message.builder().messageId(contextId).contextId(contextId)
                        .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("phase3:direct"))).build();
                client.sendInput(SendInputParams.builder().message(message)
                        .configuration(MessageSendConfiguration.builder().returnImmediately(returnImmediately)
                                .taskPushNotificationConfig(inlineConfig()).build()).build(),
                        List.of(collected.consumer()), collected.errors(), null);
                assertTrue(collected.awaitEvents(1, 15), "direct reply must arrive");
                Thread.sleep(300);
                assertNull(collected.failure.get());
                assertTrue(collected.events.stream().allMatch(MessageEvent.class::isInstance),
                        () -> "only a Message: " + collected.events);
                assertEquals(1, collected.events.size());

                String executionTask = Phase3Executions.taskIdAt(seen);
                assertNull(storedTask(executionTask), "the no-task branch persists no task");
                assertEquals(0, pushConfigCount(executionTask), "the inline config is not stored for a direct reply");
                assertEquals(0, client.listTasks(ListTasksParams.builder().contextId(contextId).build()).tasks().size());
                assertTrue(awaitChildQueues(executionTask, 0), "the no-task queue is cleaned up");
            }
        }

        // contrast: task-bearing output persists a task
        Task created = createWorkingTask();
        assertNotNull(storedTask(created.id()));
    }

    @Test
    public void openingMessagePlusArtifactsIsPersistedAsOneTaskUnlikeADirectReply() throws Exception {
        Client client = open("1.1", false, false);
        Message opening = Message.builder().messageId("p3-open-" + System.nanoTime()).role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("phase3:work"))).build();
        Outcome outcome = observe(client, SendInputParams.builder().message(opening)
                .artifactUpdates(List.of(new TaskArtifactUpdateEvent("", Artifact.builder()
                        .artifactId("p3-open-a").parts(List.of(new TextPart("a"))).build(), "", false, true,
                        null, 0))).build());
        assertNull(outcome.failure, () -> String.valueOf(outcome.failure));
        assertNotNull(outcome.task, "opening message + artifacts is task-bearing");
        createdTasks.add(outcome.task.id());
        Task stored = storedTask(outcome.task.id());
        assertNotNull(stored);
        assertTrue(stored.artifacts().stream().anyMatch(a -> a.artifactId().equals("p3-open-a")
                && a.role() == Message.Role.ROLE_USER));
        assertEquals(1, client.listTasks(ListTasksParams.builder().contextId(stored.contextId()).build()).tasks().size());
    }

    // ---- scenario 4: older writers share a task with a 1.1 subscriber ----------------------------------------

    @Test
    public void older10WriterAndNewSubscriberShareOneTaskWithStableShapes() throws Exception {
        Task base = createWorkingTask();
        Client subscriber = open("1.1", true, false);
        Collected seen = new Collected();
        subscriber.subscribeToTask(new TaskIdParams(base.id()), List.of(seen.consumer()), seen.errors(), null);
        assertTrue(seen.awaitEvents(1, 10), "initial snapshot");
        assertTrue(awaitChildQueues(base.id(), 1));

        Client legacy = open("1.0", false, false);
        legacy.sendMessage(Message.builder().messageId("p3-legacy").taskId(base.id()).contextId(base.contextId())
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("phase3:artifacts-complete"))).build(),
                List.of((event, card) -> { }), null, null);

        assertTrue(seen.awaitTerminal(15), () -> "subscriber must see the completion: " + seen.events);
        Task view = seen.lastTask();
        assertEquals(TaskState.TASK_STATE_COMPLETED, view.status().state());
        assertEquals(storedTask(base.id()).generation(), view.generation());
        assertTrue(view.artifacts().stream().map(Artifact::artifactId).toList().containsAll(List.of("p3-a1", "p3-a2")));
        assertTrue(view.history().stream().anyMatch(message -> message.messageId().equals("p3-legacy")));
        assertTrue(generationsStrictlyIncrease(seen.generations()), () -> "stamps: " + seen.generations());

        Task legacyView = legacy.getTask(new TaskQueryParams(base.id()));
        assertEquals(TaskState.TASK_STATE_COMPLETED, legacyView.status().state());
        assertEquals(0, legacyView.generation(), "old clients never see generations");
        assertTrue(legacyView.timeline().isEmpty());
    }

    @Test
    public void older03WriterAndNewSubscriberShareOneTask() throws Exception {
        Task base = createWorkingTask();
        Client subscriber = open("1.1", true, false);
        Collected seen = new Collected();
        subscriber.subscribeToTask(new TaskIdParams(base.id()), List.of(seen.consumer()), seen.errors(), null);
        assertTrue(seen.awaitEvents(1, 10), "initial snapshot");
        assertTrue(awaitChildQueues(base.id(), 1));
        long before = storedTask(base.id()).generation();

        send03Message(base.id(), base.contextId(), "phase3:noop");

        Task stored = storedTask(base.id());
        assertTrue(stored.generation() > before, "the 0.3 write is committed with a generation");
        assertTrue(seen.awaitEvents(2, 10), () -> "the 1.1 subscriber sees the 0.3 message: " + seen.events);
        assertNull(seen.failure.get());
        assertTrue(seen.events.stream().skip(1).anyMatch(event -> event instanceof TaskUpdateEvent update
                && update.getUpdateEvent() instanceof TaskMessageUpdateEvent message
                && message.generation() > before && message.message().role() == Message.Role.ROLE_USER), () -> "message update with its generation: " + seen.events);
        assertTrue(generationsStrictlyIncrease(seen.generations().stream().filter(g -> g > 0).toList()),
                () -> "stamps: " + seen.generations());
    }

    @Test
    public void existingTaskDirectReplyLeavesOneOpenTaskAndCreatesNoSecondTask() throws Exception {
        Task base = createWorkingTask();
        Client client = open("1.1", false, false);

        Outcome outcome = observe(client, input(base, "p3-existing-direct", "phase3:direct", null));

        assertNull(outcome.failure, () -> String.valueOf(outcome.failure));
        assertEquals(1, client.listTasks(ListTasksParams.builder().contextId(base.contextId()).build()).tasks().size());
        assertFalse(storedTask(base.id()).status().state().isFinal());
    }

    // ---- scenario 5: terminal full proposal, inline config and metadata-only status -------------------------

    @Test
    public void terminalFullProposalDeliversFinalArtifactsBeforeClosureAndConfigPrecedesEligibility() throws Exception {
        for (boolean streaming : new boolean[] {false, true}) {
            Client client = open("1.1", streaming, false);
            Collected seen = new Collected();
            int mark = Phase3Executions.count();
            String id = "p3-terminal-" + streaming;
            client.sendInput(SendInputParams.builder().message(Message.builder().messageId(id).contextId(id)
                            .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("phase3:terminal-proposal"))).build())
                    .configuration(MessageSendConfiguration.builder().taskPushNotificationConfig(inlineConfig()).build())
                    .build(), List.of(seen.consumer()), seen.errors(), null);

            assertTrue(seen.awaitTerminal(15), () -> "terminal proposal must close the stream: " + seen.events);
            assertNull(seen.failure.get());
            Task last = seen.lastTask();
            createdTasks.add(last.id());
            assertEquals(TaskState.TASK_STATE_COMPLETED, last.status().state());
            assertEquals("p3-final", last.artifacts().get(0).artifactId(), "final artifacts arrive before closure");
            assertEquals(last.id(), Phase3Executions.taskIdAt(mark));
            assertEquals(1, pushConfigCount(last.id()),
                    "the inline config of an immediately terminal task is registered, so it is eligible");
        }
    }

    @Test
    public void metadataOnlyStatusPreservesTheMessageOccurrenceCount() throws Exception {
        Task base = createWorkingTask();
        Client client = open("1.1", false, false);
        int before = storedTask(base.id()).history().size();

        Outcome outcome = observe(client, input(base, "p3-meta", "phase3:work", null));

        assertNull(outcome.failure);
        Task after = storedTask(base.id());
        assertEquals(before + 1, after.history().size(), "one occurrence for the input; the status adds none");
        assertEquals(1, messageEntries(client.getTask(new TaskQueryParams(base.id(), null, null, null, 20)), "p3-meta"));
    }

    // ---- scenario 6: lost final publication -------------------------------------------------------------------

    @Test
    public void lostFinalPublicationClosesTheStreamAndExplicitGetTaskReturnsTheFinalStateWithoutMutation()
            throws Exception {
        Task base = createWorkingTask();
        Client subscriber = open("1.1", true, false);
        Collected seen = new Collected();
        subscriber.subscribeToTask(new TaskIdParams(base.id()), List.of(seen.consumer()), seen.errors(), null);
        assertTrue(seen.awaitEvents(1, 10), "initial snapshot");
        assertTrue(awaitChildQueues(base.id(), 1));
        drainIngress();

        Task completed = Task.builder(storedTask(base.id()))
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).generation(base.generation() + 1).build();
        long lostAt = System.nanoTime();
        commitWithoutPublication(completed);

        assertTrue(seen.awaitFailure(20), () -> "the affected stream must be closed within the recovery bound: "
                + seen.events);
        long closedAfterMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lostAt);
        assertTrue(closedAfterMillis < 15_000, "closed after " + closedAfterMillis + " ms");
        assertFalse(seen.events.stream().anyMatch(event -> terminal(event)), "the lost final state is never invented");
        boolean released = awaitChildQueues(base.id(), 0);
        assertTrue(released, "the closed observation releases its child queue, count=" + childQueueCount(base.id()));

        drainIngress();
        Task committedBefore = storedTask(base.id());
        Task explicit = subscriber.getTask(new TaskQueryParams(base.id()));
        assertEquals(TaskState.TASK_STATE_COMPLETED, explicit.status().state());
        assertEquals(completed.generation(), explicit.generation());
        assertEquals(1, drainIngress().size(), "the explicit GetTask is the only request: nothing is resent");
        assertEquals(committedBefore.generation(), storedTask(base.id()).generation(), "recovery never mutates");
        assertEquals(committedBefore.status().state(), storedTask(base.id()).status().state());
    }

    // ---- scenario 7: every existing entry point reduces the same stamps ------------------------------------------

    @Test
    public void unaryStreamingAndNoCursorSubscribeReduceTheSameCommittedStamps() throws Exception {
        for (String route : List.of("unary", "streaming", "subscribe", "streaming-observed")) {
            Task base = createWorkingTask();
            Collected seen = new Collected();
            if (route.equals("subscribe")) {
                Client subscriber = open("1.1", true, false);
                subscriber.subscribeToTask(new TaskIdParams(base.id()), List.of(seen.consumer()), seen.errors(), null);
                assertTrue(seen.awaitEvents(1, 10));
                assertTrue(awaitChildQueues(base.id(), 1));
            }
            Client writer = open("1.1", route.startsWith("streaming"), false);
            Task view;
            Long through = null;
            if (route.equals("streaming-observed")) {
                Outcome outcome = observe(writer, input(base, "p3-" + route, "phase3:artifacts-complete", null));
                assertNull(outcome.failure, route);
                view = outcome.task;
                through = outcome.through;
            } else {
                writer.sendMessage(userMessage(base, "p3-" + route, "phase3:artifacts-complete"),
                        List.of(route.equals("subscribe") ? (event, card) -> { } : seen.consumer()), seen.errors(), null);
                assertTrue(seen.awaitTerminal(15), () -> route + " must reach the terminal state: " + seen.events);
                view = seen.lastTask();
            }
            Task stored = storedTask(base.id());
            if (route.equals("streaming-observed")) {
                assertTrue(through == null || through == stored.generation(), route + " through-generation " + through);
                assertEquals(stored.generation(), view.timeline().get(view.timeline().size() - 1).generation(),
                        route + " reduced timeline stamp");
            } else if (!route.equals("streaming")) {
                // a streaming sendMessage without a snapshot frame has no baseline: its callback task carries no stamp
                assertEquals(stored.generation(), view.generation(), route + " stamp");
            }
            assertEquals(TaskState.TASK_STATE_COMPLETED, view.status().state(), route);
            assertTrue(view.artifacts().stream().map(Artifact::artifactId).toList().containsAll(List.of("p3-a1", "p3-a2")),
                    route + " artifacts");
            assertEquals(1, messageEntries(writer.getTask(new TaskQueryParams(base.id(), null, null, null, 50)),
                    "p3-" + route), route + " message count");
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private record Outcome(@Nullable Task task, @Nullable Throwable failure, @Nullable Long through) {
        Outcome(@Nullable Task task, @Nullable Throwable failure) {
            this(task, failure, null);
        }
    }

    private Outcome observe(Client client, SendInputParams params) {
        try {
            TaskObservation observation = client.sendInputObserved(params, null);
            ObservationEnd end = observation.completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            if (end.kind() != ObservationEnd.Kind.COMPLETED) {
                return new Outcome(end.finalView().task(), end.cause() == null
                        ? new IllegalStateException("observation " + end.kind()) : end.cause());
            }
            return new Outcome(end.finalView().task(), null, end.finalView().throughGeneration());
        } catch (A2AClientException | ExecutionException | TimeoutException failure) {
            return new Outcome(null, failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Outcome(null, interrupted);
        }
    }

    private static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static boolean terminal(ClientEvent event) {
        Task task = event instanceof TaskEvent taskEvent ? taskEvent.getTask()
                : event instanceof TaskUpdateEvent update ? update.getTask() : null;
        return task != null && task.status().state().isFinal();
    }

    private static TaskPushNotificationConfig inlineConfig() {
        return TaskPushNotificationConfig.builder().url("http://localhost:1/hook").build();
    }

    private static SendInputParams input(Task base, String messageId, String text, @Nullable Long guard) {
        SendInputParams.Builder builder = SendInputParams.builder().message(userMessage(base, messageId, text));
        if (guard != null) {
            builder.configuration(MessageSendConfiguration.builder().ifGenerationMatch(guard).build());
        }
        return builder.build();
    }

    private static Message userMessage(Task base, String messageId, String text) {
        return Message.builder().messageId(messageId).taskId(base.id()).contextId(base.contextId())
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart(text))).build();
    }

    private static TaskArtifactUpdateEvent artifactUpdate(Task task, String artifactId) {
        return new TaskArtifactUpdateEvent(task.id(), Artifact.builder().artifactId(artifactId)
                .parts(List.of(new TextPart(artifactId))).build(), task.contextId(), false, true, null, 0);
    }

    private static List<Long> timelineGenerations(Task task) {
        return task.timeline().stream().map(TimelineEntry::generation).toList();
    }

    private static long messageEntries(Task task, String idPrefix) {
        return task.timeline().stream().filter(entry -> entry.entry() instanceof TimelineEntry.MessageValue value
                && value.message().messageId().startsWith(idPrefix)).count();
    }

    private static boolean generationsStrictlyIncrease(List<Long> generations) {
        for (int i = 1; i < generations.size(); i++) {
            if (generations.get(i) <= generations.get(i - 1)) {
                return false;
            }
        }
        return true;
    }

    private static void assertUnchanged(Task before, @Nullable Task after) {
        assertNotNull(after);
        assertEquals(before.generation(), after.generation(), "a rejected input never advances the generation");
        assertEquals(before.history().size(), after.history().size(), "no partial message");
        assertEquals(before.artifacts().size(), after.artifacts().size(), "no partial artifact");
    }

    private boolean awaitChildQueues(String taskId, int expected) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (expected == 0 ? childQueueCount(taskId) <= 0 : childQueueCount(taskId) == expected) {
                return true;
            }
            Thread.sleep(100);
        }
        return childQueueCount(taskId) <= expected;
    }

    private Task createWorkingTask() throws Exception {
        return createWorkingTask(open("1.1", false, true));
    }

    private Task createWorkingTask(Client polling) throws Exception {
        Collected seen = new Collected();
        polling.sendMessage(Message.builder().messageId("p3-create-" + System.nanoTime())
                .contextId("p3-ctx-" + System.nanoTime()).role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("phase3:work"))).build(), List.of(seen.consumer()), seen.errors(), null);
        assertTrue(seen.awaitEvents(1, 15), "task creation must answer");
        Task created = seen.lastTask();
        createdTasks.add(created.id());
        return storedTask(created.id());
    }

    private Client open(String version, boolean streaming, boolean polling) throws A2AClientException {
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

    /** Collects a public callback stream; a finite stream's quiescence is observed through its events. */
    private static final class Collected {
        final List<ClientEvent> events = new CopyOnWriteArrayList<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final CountDownLatch failed = new CountDownLatch(1);

        BiConsumer<ClientEvent, AgentCard> consumer() {
            return (event, card) -> events.add(event);
        }

        java.util.function.Consumer<Throwable> errors() {
            return error -> {
                if (error != null && !(error instanceof java.util.concurrent.CancellationException)) {
                    failure.compareAndSet(null, error);
                    failed.countDown();
                }
            };
        }

        boolean awaitEvents(int count, int seconds) throws InterruptedException {
            for (int i = 0; i < seconds * 20 && events.size() < count; i++) {
                Thread.sleep(50);
            }
            return events.size() >= count;
        }

        boolean awaitTerminal(int seconds) throws InterruptedException {
            for (int i = 0; i < seconds * 20; i++) {
                if (events.stream().anyMatch(Phase3MutationScenarios::terminal) || failure.get() != null) {
                    return failure.get() == null;
                }
                Thread.sleep(50);
            }
            return false;
        }

        boolean awaitFailure(int seconds) throws InterruptedException {
            return failed.await(seconds, TimeUnit.SECONDS);
        }

        Task lastTask() {
            Task last = null;
            for (ClientEvent event : events) {
                if (event instanceof TaskEvent taskEvent) {
                    last = taskEvent.getTask();
                } else if (event instanceof TaskUpdateEvent update) {
                    last = update.getTask();
                }
            }
            return assertNotNullTask(last);
        }

        List<Long> generations() {
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

        private static Task assertNotNullTask(@Nullable Task task) {
            assertNotNull(task, "a task view must have been delivered");
            return task;
        }
    }

    /** Test-side view of the controlled executor's invocations. */
    static final class Phase3Executions {
        private Phase3Executions() {
        }

        static int count() {
            return AgentExecutorProducer.PHASE3_TASK_IDS.size();
        }

        static String taskIdAt(int index) {
            assertTrue(AgentExecutorProducer.PHASE3_TASK_IDS.size() > index, "the controlled executor must have run");
            return AgentExecutorProducer.PHASE3_TASK_IDS.get(index);
        }
    }
}
