package org.a2aproject.sdk.server.apps.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.ViewSync;
import org.a2aproject.sdk.server.tasks.ProposalResult;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.a2aproject.sdk.spec.VersionNotSupportedError;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Reusable P3-T17 public-client scenarios over one real server. Each transport supplies only its client wiring and
 * server-side hooks; the assertions are shared so later transports reuse the same contract. Every request crosses the
 * public {@link Client}. The only direct server access is the store/queue/spy inspection, the explicit
 * lost-publication commit hook, and the in-process finite-demand observers of older versions (the reference HTTP edge
 * always requests unbounded demand, so finite demand can only be exercised on the publisher the edge subscribes to).
 */
public abstract class Phase3MutationScenarios extends Phase3ScenarioSupport {

    /** Returns and clears the protocol-version header of every request that reached the server ingress. */
    protected abstract List<String> drainIngress();

    /** Commits {@code task} to the authoritative store without publishing anything (a lost publication). */
    protected abstract void commitWithoutPublication(Task task);

    protected abstract int childQueueCount(String taskId);

    protected abstract int pushConfigCount(String taskId);

    /** Sends one message to an existing task through the unchanged public 0.3 SDK. */
    protected abstract void send03Message(String taskId, String contextId, String text) throws Exception;

    /**
     * Subscribes an older-version ({@code "1.0"} or {@code "0.3"}) observer to the task's stream through the server's
     * version projection, requesting exactly one item at a time.
     */
    protected abstract Phase3OldObserver<?> observeUnderFiniteDemand(String version, String taskId) throws Exception;

    /** Reads the task through the unchanged public 0.3 SDK. */
    protected abstract Phase3OldObserver.TaskRead read03Task(String taskId) throws Exception;

    /**
     * Posts a raw selected-1.1 input whose final artifact element is malformed and returns normally only if the
     * server rejected it with a protocol error.
     */
    protected abstract void assertRawMalformedFinalArtifactRejected(String taskId, String contextId, String otherTaskId)
            throws Exception;

    // ---- scenario 1: guard race, unary and streaming --------------------------------------------------------

    @Test
    public void twoUnaryPublicClientsRaceTheSameGuardExactlyOneIsAccepted() throws Exception {
        raceTheGuard(false);
    }

    @Test
    public void twoStreamingPublicClientsRaceTheSameGuardExactlyOneIsAccepted() throws Exception {
        raceTheGuard(true);
    }

    private void raceTheGuard(boolean streaming) throws Exception {
        Task base = createWorkingTask();
        long guard = base.generation();
        Client first = open("1.1", streaming, false);
        Client second = open("1.1", streaming, false);
        drainIngress();

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Outcome> outcomes = new CopyOnWriteArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (Client client : List.of(first, second)) {
            String id = "race-" + threads.size();
            Thread thread = new Thread(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    SendInputParams params = input(base, id, "phase3:work", guard);
                    outcomes.add(streaming ? attemptCollected(client, params) : observe(client, params));
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
        List<Outcome> accepted = outcomes.stream().filter(outcome -> outcome.failure() == null).toList();
        List<Outcome> mismatched = outcomes.stream()
                .filter(outcome -> outcome.failure() != null
                        && causedBy(outcome.failure(), TaskGenerationMismatchError.class))
                .toList();
        assertEquals(1, accepted.size(), () -> "exactly one accepted: " + outcomes);
        assertEquals(1, mismatched.size(), () -> "exactly one typed mismatch: " + outcomes);
        assertEquals(2, drainIngress().size(), "no automatic retry or rebase: one ingress per client");

        // A unary send answers with the task; an existing-task stream has no snapshot, so its stamp is the one on
        // the streamed update events (the observed-view contract for that case is pinned in the entry-point scenario).
        Task winnerView = accepted.get(0).task();
        long winnerStamp;
        if (streaming) {
            assertNull(winnerView, "an existing-task stream carries no task snapshot");
            winnerStamp = accepted.get(0).through();
        } else {
            assertNotNull(winnerView);
            winnerStamp = winnerView.generation();
        }
        assertTrue(winnerStamp > guard);
        Task stored = storedTask(base.id());
        assertNotNull(stored);
        assertEquals(stored.generation(), winnerStamp, "send stamp equals the committed head");
        Task got = first.getTask(new TaskQueryParams(base.id(), null, null, null, 10));
        Task listed = first.listTasks(ListTasksParams.builder().contextId(base.contextId()).timelineLength(10).build())
                .tasks().get(0);
        assertEquals(stored.generation(), got.generation());
        assertEquals(stored.generation(), listed.generation());
        assertEquals(timelineGenerations(got), timelineGenerations(listed));
        assertEquals(1, messageEntries(got, "race-"), "only the accepted input is on the timeline");
        assertEquals(winnerStamp, got.timeline().get(got.timeline().size() - 1).generation());
    }

    // ---- scenario 2: message plus artifacts and artifact-only inputs, unary and streaming ---------------------

    @Test
    public void unaryMessagePlusMultipleArtifactsAndArtifactOnlyInputMutateAtomically() throws Exception {
        messagePlusArtifactsAndArtifactOnly(false);
    }

    @Test
    public void streamingMessagePlusMultipleArtifactsAndArtifactOnlyInputMutateAtomically() throws Exception {
        messagePlusArtifactsAndArtifactOnly(true);
    }

    private void messagePlusArtifactsAndArtifactOnly(boolean streaming) throws Exception {
        Task base = createWorkingTask();
        Client client = open("1.1", streaming, false);
        String prefix = streaming ? "p3-s" : "p3-u";

        SendInputParams bothParams = SendInputParams.builder().message(userMessage(base, prefix + "-both", "phase3:work"))
                .artifactUpdates(List.of(artifactUpdate(base, prefix + "1"), artifactUpdate(base, prefix + "2"))).build();
        Outcome both = streaming ? attemptCollected(client, bothParams) : observe(client, bothParams);
        assertNull(both.failure(), () -> "message + artifacts must be accepted: " + both.failure());
        Task afterBoth = storedTask(base.id());
        assertNotNull(afterBoth);
        assertTrue(afterBoth.generation() > base.generation());
        assertEquals(List.of(prefix + "1", prefix + "2"), afterBoth.artifacts().stream().map(Artifact::artifactId)
                .filter(id -> id.startsWith(prefix)).sorted().toList());
        assertTrue(afterBoth.artifacts().stream().filter(a -> a.artifactId().startsWith(prefix))
                .allMatch(a -> a.role() == Message.Role.ROLE_USER), "client-written artifacts carry the USER origin");
        if (streaming) {
            // an existing-task stream has no snapshot frame: its streamed update events carry the stamps
            assertNull(both.task());
            assertEquals(afterBoth.generation(), both.through(), "the last streamed update is the committed head");
        } else {
            assertNotNull(both.task());
            assertEquals(afterBoth.generation(), both.task().generation(), "the stamp is the committed head");
        }

        Outcome only = observe(client, SendInputParams.builder()
                .artifactUpdates(List.of(artifactUpdate(base, prefix + "3"))).build());
        assertNull(only.failure(), () -> "artifact-only input to an existing task must be accepted: " + only.failure());
        Task afterOnly = storedTask(base.id());
        assertNotNull(afterOnly);
        assertTrue(afterOnly.generation() > afterBoth.generation());
        assertEquals(afterBoth.history().size(), afterOnly.history().size(), "artifact-only adds no message");
        assertTrue(afterOnly.artifacts().stream().anyMatch(a -> a.artifactId().equals(prefix + "3")));
        assertNotNull(only.task());
        assertEquals(afterOnly.generation(), only.task().generation(), "the artifact-only stamp is the committed head");

        // A bounded timeline window is honoured by the streaming and the unary send alike.
        Outcome windowed = observe(client, SendInputParams.builder()
                .artifactUpdates(List.of(artifactUpdate(base, prefix + "4")))
                .configuration(MessageSendConfiguration.builder().timelineLength(2).build()).build());
        assertNull(windowed.failure(), () -> String.valueOf(windowed.failure()));
        Task afterWindow = storedTask(base.id());
        assertNotNull(afterWindow);
        assertNotNull(windowed.task());
        // an artifact-only input adds no timeline entry, so the window is the last two message/status entries
        List<Long> storedTimeline = timelineGenerations(afterWindow);
        assertEquals(storedTimeline.subList(storedTimeline.size() - 2, storedTimeline.size()),
                timelineGenerations(windowed.task()), "timelineLength(2) is the last two stored timeline entries");
        assertEquals(afterWindow.generation(), windowed.task().generation(), "the stamp is the committed head");
        assertNotNull(windowed.view());
        assertEquals(ViewSync.SYNCHRONIZED, windowed.view().sync());
        assertEquals(afterWindow.generation(), windowed.through());

        // An older 1.0 client reading the same task never sees the USER artifacts or the new fields.
        Client legacy = open("1.0", false, false);
        Task legacyView = legacy.getTask(new TaskQueryParams(base.id()));
        assertTrue(legacyView.artifacts().stream().noneMatch(a -> a.artifactId().startsWith(prefix)));
        assertEquals(0, legacyView.generation());
        assertTrue(legacyView.timeline().isEmpty());
    }

    @Test
    public void streamingGuardedInputIsAcceptedOnTheCurrentGenerationAndStaleOnesFailTypedWithoutMutation()
            throws Exception {
        Task base = createWorkingTask();
        Client client = open("1.1", true, false);
        long guard = base.generation();

        Outcome accepted = attemptCollected(client, input(base, "p3-guarded-ok", "phase3:work", guard));
        assertNull(accepted.failure(), () -> String.valueOf(accepted.failure()));
        Task afterAccept = storedTask(base.id());
        assertNotNull(afterAccept);
        assertTrue(afterAccept.generation() > guard);
        assertEquals(afterAccept.generation(), accepted.through(), "the streamed update stamps reach the committed head");

        drainIngress();
        Outcome stale = observe(client, input(base, "p3-guarded-stale", "phase3:noop", guard));
        assertTypedFailure(stale.failure(), TaskGenerationMismatchError.class, null);
        assertEquals(1, drainIngress().size(), "a stale streaming guard is one request, never retried or rebased");
        assertUntouched(afterAccept, storedTask(base.id()), "stale streaming guard");

        Outcome staleArtifactOnly = observe(client, SendInputParams.builder()
                .artifactUpdates(List.of(artifactUpdate(base, "p3-stale-art")))
                .configuration(MessageSendConfiguration.builder().ifGenerationMatch(guard).build()).build());
        assertTypedFailure(staleArtifactOnly.failure(), TaskGenerationMismatchError.class, null);
        assertUntouched(afterAccept, storedTask(base.id()), "stale artifact-only streaming guard");
    }

    // ---- scenario 3: rejected inputs are typed, attributed and leave both target tasks untouched ---------------

    private record Rejection(String name, SendInputParams params, Class<? extends Throwable> type,
            @Nullable String contains) {
    }

    @Test
    public void unaryRejectedInputsAreTypedAttributedAndCauseNoPartialMutationOnEitherTask() throws Exception {
        rejectedInputs(false);
    }

    @Test
    public void streamingRejectedInputsAreTypedAttributedAndCauseNoPartialMutationOnEitherTask() throws Exception {
        rejectedInputs(true);
    }

    private void rejectedInputs(boolean streaming) throws Exception {
        Task base = createWorkingTask();
        Task other = createTask("phase3:agent-artifact");
        // the agent emits its artifact asynchronously to the creating reply, so wait for it to be committed
        for (int i = 0; i < 100 && storedTask(other.id()).artifacts().isEmpty(); i++) {
            Thread.sleep(50);
        }

        Client client = open("1.1", streaming, false);
        Task baseBefore = storedTask(base.id());
        Task otherBefore = storedTask(other.id());
        assertNotNull(baseBefore);
        assertNotNull(otherBefore);
        assertTrue(otherBefore.artifacts().stream().anyMatch(a -> a.artifactId().equals("p3-agent-art")),
                "the second task owns an agent-origin artifact");

        TaskArtifactUpdateEvent agentOrigin = new TaskArtifactUpdateEvent(base.id(), Artifact.builder()
                .artifactId("p3-m1").role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("p3-m1"))).build(),
                base.contextId(), false, true, null, 0);
        TaskArtifactUpdateEvent collision = new TaskArtifactUpdateEvent(other.id(), Artifact.builder()
                .artifactId("p3-agent-art").parts(List.of(new TextPart("hijack"))).build(), other.contextId(), false,
                true, null, 0);
        Message wrongContext = Message.builder().messageId("p3-ctx").taskId(base.id()).contextId("another-context")
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("phase3:noop"))).build();
        Message unknownTask = Message.builder().messageId("p3-unknown").taskId("p3-no-such-task")
                .contextId(base.contextId()).role(Message.Role.ROLE_USER).parts(List.of(new TextPart("phase3:noop")))
                .build();
        List<Rejection> rejections = List.of(
                new Rejection("agent-origin artifact", SendInputParams.builder()
                        .message(userMessage(base, "p3-role", "phase3:noop")).artifactUpdates(List.of(agentOrigin))
                        .build(), InvalidParamsError.class, "origin"),
                new Rejection("artifact-only agent-origin artifact", SendInputParams.builder()
                        .artifactUpdates(List.of(agentOrigin)).build(), InvalidParamsError.class, "origin"),
                new Rejection("wrong context", SendInputParams.builder().message(wrongContext).build(),
                        InvalidParamsError.class, "context"),
                new Rejection("unknown task", SendInputParams.builder().message(unknownTask).build(),
                        TaskNotFoundError.class, null),
                new Rejection("blank messageId", SendInputParams.builder().message(Message.builder().messageId("")
                        .taskId(base.id()).contextId(base.contextId()).role(Message.Role.ROLE_USER)
                        .parts(List.of(new TextPart("phase3:noop"))).build()).build(),
                        InvalidParamsError.class, "required"),
                new Rejection("origin/ID collision with an agent artifact of the other task", SendInputParams.builder()
                        .message(userMessage(other, "p3-collide", "phase3:noop")).artifactUpdates(List.of(collision))
                        .build(), InvalidParamsError.class, "another origin"),
                new Rejection("artifact-only origin/ID collision", SendInputParams.builder()
                        .artifactUpdates(List.of(collision)).build(), InvalidParamsError.class, "another origin"));
        for (Rejection rejection : rejections) {
            drainIngress();
            Outcome outcome = observe(client, rejection.params());
            assertTypedFailure(outcome.failure(), rejection.type(), rejection.contains());
            assertEquals(1, drainIngress().size(), rejection.name() + ": rejected by the server, not a client retry");
            assertUntouched(baseBefore, storedTask(base.id()), rejection.name() + " (target task)");
            assertUntouched(otherBefore, storedTask(other.id()), rejection.name() + " (other task)");
            assertEquals(0, pushConfigCount(base.id()), rejection.name() + ": no config on the target");
            assertEquals(0, pushConfigCount(other.id()), rejection.name() + ": no config on the other task");
            assertEquals(1, client.listTasks(ListTasksParams.builder().contextId(base.contextId()).build()).tasks().size(),
                    rejection.name() + ": no task created in the context");
        }
        assertEquals(0, client.listTasks(ListTasksParams.builder().contextId("another-context").build()).tasks().size());
        assertEquals(0, client.listTasks(ListTasksParams.builder().contextId("p3-no-such-task").build()).tasks().size());

        // the tenant whose registration does not provision the selected 1.1 version
        drainIngress();
        Outcome tenant = observe(client, SendInputParams.builder().message(userMessage(base, "p3-tenant", "phase3:noop"))
                .tenant("phase1-2").build());
        assertTypedFailure(tenant.failure(), VersionNotSupportedError.class, "not provisioned");
        assertEquals(1, drainIngress().size());
        assertUntouched(baseBefore, storedTask(base.id()), "tenant (target task)");
        assertUntouched(otherBefore, storedTask(other.id()), "tenant (other task)");

        assertRawMalformedFinalArtifactRejected(base.id(), base.contextId(), other.id());
        assertUntouched(baseBefore, storedTask(base.id()), "raw malformed final element (target task)");
        assertUntouched(otherBefore, storedTask(other.id()), "raw malformed final element (other task)");
        assertEquals(0, pushConfigCount(base.id()));
        assertEquals(0, pushConfigCount(other.id()));
    }

    // ---- scenario 4: direct-Message no-task branch -------------------------------------------------------------

    @Test
    public void directMessageCreatesNoTaskConfigPublicationReplicationOrQueueEvenWithOpeningArtifacts() throws Exception {
        for (boolean streaming : new boolean[] {false, true}) {
            for (boolean returnImmediately : new boolean[] {false, true}) {
                for (boolean withArtifacts : new boolean[] {false, true}) {
                    Client client = open("1.1", streaming, false);
                    int seen = Phase3Executions.count();
                    int tasksBefore = client.listTasks(ListTasksParams.builder().pageSize(1).build()).totalSize();
                    Collected collected = new Collected();
                    String name = "p3-direct-" + streaming + "-" + returnImmediately + "-" + withArtifacts;
                    // Only an opening message (no task ID and no context ID) may carry provisional-ID artifact updates.
                    Message.Builder opening = Message.builder().messageId(name).role(Message.Role.ROLE_USER)
                            .parts(List.of(new TextPart("phase3:direct")));
                    if (!withArtifacts) {
                        opening.contextId(name);
                    }
                    Message message = opening.build();
                    SendInputParams.Builder params = SendInputParams.builder().message(message)
                            .configuration(MessageSendConfiguration.builder().returnImmediately(returnImmediately)
                                    .taskPushNotificationConfig(inlineConfig()).build());
                    if (withArtifacts) {
                        params.artifactUpdates(List.of(new TaskArtifactUpdateEvent("", Artifact.builder()
                                .artifactId("p3-opening-" + name).parts(List.of(new TextPart("a"))).build(), "", false,
                                true, null, 0)));
                    }
                    client.sendInput(params.build(), List.of(collected.consumer()), collected.errors(), null);
                    assertTrue(collected.awaitEvents(1, 15), name + ": the direct reply must arrive");
                    Thread.sleep(300);
                    assertNull(collected.failure.get(), name);
                    assertEquals(1, collected.events.size(), () -> name + ": only the Message: " + collected.events);
                    assertTrue(collected.events.stream().allMatch(MessageEvent.class::isInstance),
                            () -> name + ": only a Message: " + collected.events);

                    String executionTask = Phase3Executions.taskIdAt(seen);
                    assertNull(storedTask(executionTask), name + ": the no-task branch persists no task");
                    assertEquals(0, pushConfigCount(executionTask), name + ": the inline config is not stored");
                    assertEquals(tasksBefore, client.listTasks(ListTasksParams.builder().pageSize(1).build()).totalSize(),
                            name + ": no task was created in the store");
                    assertTrue(Phase3Spy.queueWasCreated(executionTask),
                            name + ": a queue existed, so the cleanup assertion is not vacuous");
                    assertTrue(awaitQueueClosed(executionTask), name + ": the no-task queue was closed");
                    // The fixed settle period above cannot rule out a later event, so the same claim is judged again
                    // at a server-side barrier: the stream's queue is closed, so nothing more can be emitted for it.
                    assertNull(collected.failure.get(), name);
                    assertEquals(1, collected.events.size(), () -> name + ": only the Message: " + collected.events);
                    assertTrue(collected.events.stream().allMatch(MessageEvent.class::isInstance),
                            () -> name + ": only a Message: " + collected.events);
                    assertEquals(List.of(), Phase3Spy.replicated(executionTask), name + ": nothing was replicated");
                    assertTrue(Phase3Spy.published(executionTask).stream().allMatch("Message"::equals),
                            () -> name + ": only the Message may be published: " + Phase3Spy.published(executionTask));
                    assertTrue(Phase3Webhook.eventsFor(executionTask).isEmpty(),
                            name + ": no push notification");
                }
            }
        }
    }

    @Test
    public void taskBearingInputIsReplicatedAndPersistedSoTheDirectMessageSpyIsNotVacuous() throws Exception {
        Task created = createWorkingTask();
        assertNotNull(storedTask(created.id()));
        assertTrue(Phase3Spy.queueWasCreated(created.id()));
        assertFalse(Phase3Spy.replicated(created.id()).isEmpty(), "committed events reach the replication seam");
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
        assertNull(outcome.failure(), () -> String.valueOf(outcome.failure()));
        assertNotNull(outcome.task(), "opening message + artifacts is task-bearing");
        track(outcome.task().id());
        Task stored = storedTask(outcome.task().id());
        assertNotNull(stored);
        assertTrue(stored.artifacts().stream().anyMatch(a -> a.artifactId().equals("p3-open-a")
                && a.role() == Message.Role.ROLE_USER));
        assertEquals(1, client.listTasks(ListTasksParams.builder().contextId(stored.contextId()).build()).tasks().size());
        assertFalse(Phase3Spy.replicated(stored.id()).isEmpty(), "a task-bearing opening is replicated");
    }

    @Test
    public void existingTaskDirectReplyLeavesOneOpenTaskAndCreatesNoSecondTask() throws Exception {
        Task base = createWorkingTask();
        Client client = open("1.1", false, false);

        Outcome outcome = observe(client, input(base, "p3-existing-direct", "phase3:direct", null));

        assertNull(outcome.failure(), () -> String.valueOf(outcome.failure()));
        assertEquals(1, client.listTasks(ListTasksParams.builder().contextId(base.contextId()).build()).tasks().size());
        assertFalse(storedTask(base.id()).status().state().isFinal());
    }

    // ---- scenario 5: older observers under finite filtered demand and their reads -----------------------------

    @Test
    public void older10ObserverMakesProgressUnderFiniteFilteredDemandAndReadsStayProjected() throws Exception {
        olderObserverProgresses("1.0");
    }

    @Test
    public void older03ObserverMakesProgressUnderFiniteFilteredDemandAndReadsStayProjected() throws Exception {
        olderObserverProgresses("0.3");
    }

    private void olderObserverProgresses(String version) throws Exception {
        Task base = createWorkingTask();
        Client writer = open("1.1", false, false);
        // A USER artifact exists before the older observer attaches, so even its first snapshot must hide it.
        Outcome early = observe(writer, SendInputParams.builder().message(userMessage(base, "p3-old-early", "phase3:noop"))
                .artifactUpdates(List.of(artifactUpdate(base, "p3-uearly"))).build());
        assertNull(early.failure(), () -> String.valueOf(early.failure()));
        long attachedAt = storedTask(base.id()).generation();

        try (Phase3OldObserver<?> observer = observeUnderFiniteDemand(version, base.id())) {
            assertTrue(observer.awaitFrames(1, 10), "initial snapshot");
            assertTrue(awaitChildQueues(base.id(), 1));

            // Twelve inputs the old version must not see, one with a USER artifact.
            for (int i = 0; i < 12; i++) {
                SendInputParams.Builder params = SendInputParams.builder()
                        .message(userMessage(base, "p3-old-" + i, "phase3:noop"));
                if (i == 5) {
                    params.artifactUpdates(List.of(artifactUpdate(base, "p3-ulive")));
                }
                Outcome outcome = observe(writer, params.build());
                assertNull(outcome.failure(), () -> String.valueOf(outcome.failure()));
            }
            Outcome done = observe(writer, input(base, "p3-old-done", "phase3:artifacts-complete", null));
            assertNull(done.failure(), () -> String.valueOf(done.failure()));

            assertTrue(observer.awaitCompleted(20), () -> version + " observer must progress to the end: "
                    + observer.frames());
            assertNull(observer.failure(), () -> String.valueOf(observer.failure()));
            List<Phase3OldObserver.Frame> frames = observer.frames();
            Task committed = storedTask(base.id());
            assertNotNull(committed);
            long committedGenerations = committed.generation() - attachedAt;
            assertTrue(committedGenerations >= 14, "the 1.1 writer committed " + committedGenerations + " generations");
            assertTrue(frames.size() < committedGenerations, () -> version + " frames " + frames.size()
                    + " must be fewer than the " + committedGenerations + " committed generations: " + frames);

            assertEquals("task", frames.get(0).kind(), "the stream starts with the snapshot");
            assertTrue(frames.get(0).taskArtifactIds().stream().noneMatch(id -> id.startsWith("p3-u")),
                    () -> "the first snapshot hides USER artifacts: " + frames.get(0));
            assertTrue(frames.stream().noneMatch(frame -> frame.kind().equals("message")),
                    () -> "no message update is visible to " + version + ": " + frames);
            assertTrue(frames.stream().noneMatch(frame -> frame.artifactId() != null
                    && frame.artifactId().startsWith("p3-u")), () -> "no USER artifact update: " + frames);
            assertTrue(frames.stream().allMatch(frame -> frame.taskArtifactIds().stream()
                    .noneMatch(id -> id.startsWith("p3-u"))), () -> "no USER artifact in any task frame: " + frames);
            assertTrue(frames.stream().allMatch(frame -> frame.generation() == 0),
                    () -> "generations are never visible to " + version + ": " + frames);
            List<String> artifactIds = frames.stream().map(Phase3OldObserver.Frame::artifactId)
                    .filter(id -> id != null).toList();
            assertTrue(artifactIds.containsAll(List.of("p3-a1", "p3-a2")), () -> "agent artifacts delivered: " + frames);
            Phase3OldObserver.Frame last = frames.get(frames.size() - 1);
            assertEquals("completed", last.state(), () -> "the final frame carries the final state: " + frames);
            assertTrue(observer.demandSignals() >= frames.size(), "demand was granted one frame at a time");
        }

        // Reads by the same older version agree with the stream: projected, history and finality preserved.
        if (version.equals("1.0")) {
            Client legacy = open("1.0", false, false);
            for (Task view : List.of(legacy.getTask(new TaskQueryParams(base.id())),
                    legacy.listTasks(ListTasksParams.builder().contextId(base.contextId()).includeArtifacts(true)
                            .historyLength(50).build()).tasks().get(0))) {
                assertEquals(TaskState.TASK_STATE_COMPLETED, view.status().state());
                assertEquals(0, view.generation());
                assertTrue(view.timeline().isEmpty());
                assertTrue(view.artifacts().stream().noneMatch(a -> a.artifactId().startsWith("p3-u")),
                        () -> "USER artifacts hidden from 1.0 reads: " + view.artifacts());
                assertTrue(view.artifacts().stream().map(Artifact::artifactId).toList()
                        .containsAll(List.of("p3-a1", "p3-a2")));
                assertTrue(view.history().stream().anyMatch(m -> m.messageId().equals("p3-old-early")));
                assertTrue(view.history().stream().anyMatch(m -> m.messageId().equals("p3-old-done")));
            }
        } else {
            Phase3OldObserver.TaskRead read = read03Task(base.id());
            assertTrue(read.finalState(), () -> "0.3 reads see the final state: " + read);
            assertTrue(read.artifactIds().stream().noneMatch(id -> id.startsWith("p3-u")),
                    () -> "USER artifacts hidden from 0.3 reads: " + read);
            assertTrue(read.artifactIds().containsAll(List.of("p3-a1", "p3-a2")));
            assertTrue(read.historyMessageIds().containsAll(List.of("p3-old-early", "p3-old-done")), read::toString);
        }
        // The authoritative 1.1 view still has the USER artifacts.
        Task authoritative = open("1.1", false, false).getTask(new TaskQueryParams(base.id()));
        assertTrue(authoritative.artifacts().stream().anyMatch(a -> a.artifactId().equals("p3-uearly")
                && a.role() == Message.Role.ROLE_USER));
        assertTrue(authoritative.artifacts().stream().anyMatch(a -> a.artifactId().equals("p3-ulive")
                && a.role() == Message.Role.ROLE_USER));
    }

    // ---- scenario 6: older writers share a task with a 1.1 subscriber -------------------------------------------

    @Test
    public void older10WriterAndNewSubscriberShareOneTaskWithStableShapes() throws Exception {
        Task base = createWorkingTask();
        Client modern = open("1.1", false, false);
        Outcome userArtifact = observe(modern, SendInputParams.builder()
                .message(userMessage(base, "p3-modern-art", "phase3:noop"))
                .artifactUpdates(List.of(artifactUpdate(base, "p3-u-old10"))).build());
        assertNull(userArtifact.failure(), () -> String.valueOf(userArtifact.failure()));
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
        assertTrue(view.artifacts().stream().anyMatch(a -> a.artifactId().equals("p3-u-old10")
                && a.role() == Message.Role.ROLE_USER), "the 1.1 view keeps the USER artifact with its origin");
        assertTrue(view.history().stream().anyMatch(message -> message.messageId().equals("p3-legacy")));
        assertTrue(generationsStrictlyIncrease(seen.generations()), () -> "stamps: " + seen.generations());

        Task legacyView = legacy.getTask(new TaskQueryParams(base.id()));
        Task legacyListed = legacy.listTasks(ListTasksParams.builder().contextId(base.contextId()).includeArtifacts(true)
                .historyLength(50).build()).tasks().get(0);
        for (Task legacyTask : List.of(legacyView, legacyListed)) {
            assertEquals(TaskState.TASK_STATE_COMPLETED, legacyTask.status().state(), "finality is preserved");
            assertEquals(0, legacyTask.generation(), "old clients never see generations");
            assertTrue(legacyTask.timeline().isEmpty());
            assertTrue(legacyTask.artifacts().stream().noneMatch(a -> a.artifactId().startsWith("p3-u")),
                    () -> "USER artifacts are hidden from 1.0: " + legacyTask.artifacts());
            assertTrue(legacyTask.history().stream().anyMatch(m -> m.messageId().equals("p3-legacy")));
            assertTrue(legacyTask.history().stream().anyMatch(m -> m.messageId().equals("p3-modern-art")),
                    "the 1.1 message stays in the old history");
        }
    }

    @Test
    public void older03WriterAndNewSubscriberShareOneTaskAndReadBackThrough03() throws Exception {
        Task base = createWorkingTask();
        Client modern = open("1.1", false, false);
        Outcome userArtifact = observe(modern, SendInputParams.builder()
                .message(userMessage(base, "p3-modern-art", "phase3:noop"))
                .artifactUpdates(List.of(artifactUpdate(base, "p3-u-old03"))).build());
        assertNull(userArtifact.failure(), () -> String.valueOf(userArtifact.failure()));
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
                && message.generation() > before && message.message().role() == Message.Role.ROLE_USER),
                () -> "message update with its generation: " + seen.events);
        assertTrue(generationsStrictlyIncrease(seen.generations().stream().filter(g -> g > 0).toList()),
                () -> "stamps: " + seen.generations());

        Phase3OldObserver.TaskRead read = read03Task(base.id());
        assertFalse(read.finalState(), () -> "the task is still working: " + read);
        assertTrue(read.artifactIds().stream().noneMatch(id -> id.startsWith("p3-u")),
                () -> "USER artifacts are hidden from 0.3: " + read);
        assertTrue(read.historyMessageIds().stream().anyMatch(id -> id.startsWith("public03-")),
                () -> "the 0.3 message is in the 0.3 history: " + read);
        assertTrue(read.historyMessageIds().contains("p3-modern-art"), () -> "the 1.1 message stays: " + read);
        assertTrue(stored.artifacts().stream().anyMatch(a -> a.artifactId().equals("p3-u-old03")
                && a.role() == Message.Role.ROLE_USER), "the authoritative task keeps the USER artifact");
    }

    // ---- scenario 7: terminal full proposal, inline config and metadata-only status ---------------------------

    @Test
    public void terminalFullProposalDeliversFinalArtifactsBeforeClosureAndTheInlineConfigPrecedesEligibility()
            throws Exception {
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
            track(last.id());
            assertEquals(TaskState.TASK_STATE_COMPLETED, last.status().state());
            assertEquals("p3-final", last.artifacts().get(0).artifactId(), "final artifacts arrive before closure");
            assertEquals(last.id(), Phase3Executions.taskIdAt(mark));
            assertEquals(1, pushConfigCount(last.id()),
                    "the inline config of an immediately terminal task is registered");

            // The config was registered before the task became notification-eligible, so the terminal state itself
            // is delivered to the webhook, with its final artifact, and exactly the committed state.
            List<StreamingEventKind> delivered = awaitTerminalNotification(last.id());
            assertFalse(delivered.isEmpty(), () -> "the terminal state must be notified through the inline config; "
                    + "everything the receiver saw: " + Phase3Webhook.RECEIVED);
            assertTrue(delivered.stream().anyMatch(event -> event instanceof Task task
                            && task.artifacts().stream().anyMatch(a -> a.artifactId().equals("p3-final"))
                            || event instanceof TaskArtifactUpdateEvent artifact
                            && artifact.artifact().artifactId().equals("p3-final")),
                    () -> "the final artifact is part of the notified state: " + delivered);
        }

        // Negative control: the same input without an inline config is never notified.
        Client client = open("1.1", false, false);
        int mark = Phase3Executions.count();
        client.sendMessage(Message.builder().messageId("p3-terminal-noconfig").contextId("p3-terminal-noconfig")
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("phase3:terminal-proposal"))).build(),
                List.of((event, card) -> { }), null, null);
        String noConfigTask = Phase3Executions.taskIdAt(mark);
        track(noConfigTask);
        Thread.sleep(1_000);
        assertEquals(0, pushConfigCount(noConfigTask));
        assertTrue(Phase3Webhook.eventsFor(noConfigTask).isEmpty(),
                "without a registered config nothing is notified");
    }

    @Test
    public void legacyOldClientsReceiveTheTerminalFullTaskProjectedFromCommittedResults() throws Exception {
        Client legacy = open("1.0", false, false);
        Collected seen = new Collected();
        int mark = Phase3Executions.count();
        legacy.sendMessage(Message.builder().messageId("p3-legacy-terminal").contextId("p3-legacy-terminal")
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("phase3:terminal-proposal"))).build(),
                List.of(seen.consumer()), seen.errors(), null);
        assertTrue(seen.awaitTerminal(15), () -> String.valueOf(seen.events));
        assertNull(seen.failure.get());
        String taskId = Phase3Executions.taskIdAt(mark);
        track(taskId);

        Task committed = storedTask(taskId);
        assertNotNull(committed);
        assertTrue(committed.history().stream().anyMatch(m -> m.messageId().equals("p3-legacy-terminal")),
                "the committed task holds the writer's input, which the raw proposal does not");
        for (Task view : List.of(seen.lastTask(), legacy.getTask(new TaskQueryParams(taskId)))) {
            assertEquals(TaskState.TASK_STATE_COMPLETED, view.status().state());
            assertEquals(List.of("p3-final"), view.artifacts().stream().map(Artifact::artifactId).toList());
            assertEquals(0, view.generation(), "no generation reaches a 1.0 client");
            assertTrue(view.timeline().isEmpty());
            assertTrue(view.history().stream().anyMatch(m -> m.messageId().equals("p3-legacy-terminal")),
                    () -> "the 1.0 result is projected from the committed task, not the raw proposal: " + view);
        }
        Phase3OldObserver.TaskRead read = read03Task(taskId);
        assertTrue(read.finalState());
        assertEquals(List.of("p3-final"), read.artifactIds());
        assertTrue(read.historyMessageIds().contains("p3-legacy-terminal"), read::toString);
    }

    /**
     * Ordinary-input occurrence count only: a same-state status from a status without a message. The metadata-only full
     * Task proposal from a message-bearing status is covered by
     * {@link #metadataOnlyFullTaskProposalFromAMessageBearingStatusCommitsOneStatusObservationAndNoMessage()}.
     */
    @Test
    public void metadataOnlyStatusPreservesTheMessageOccurrenceCount() throws Exception {
        Task base = createWorkingTask();
        Client client = open("1.1", false, false);
        int before = storedTask(base.id()).history().size();

        Outcome outcome = observe(client, input(base, "p3-meta", "phase3:work", null));

        assertNull(outcome.failure());
        Task after = storedTask(base.id());
        assertEquals(before + 1, after.history().size(), "one occurrence for the input; the status adds none");
        assertEquals(1, messageEntries(client.getTask(new TaskQueryParams(base.id(), null, null, null, 20)), "p3-meta"));
    }

    /**
     * An agent leaves the task with a status that carries a known agent message, and a later invocation submits a real
     * full Task ({@code addTask}) that keeps that status and changes only the metadata. A barrier holds the agent after
     * its invocation Task was captured, so the committed state before the proposal is read in isolation; a 1.1
     * subscriber is attached throughout. The commit must be exactly one new generation and one new status timeline
     * entry carrying the same status message, a metadata change, and no newly authored message or message update.
     */
    @Test
    public void metadataOnlyFullTaskProposalFromAMessageBearingStatusCommitsOneStatusObservationAndNoMessage()
            throws Exception {
        String messageId = AgentExecutorProducer.PHASE3_STATUS_MESSAGE_ID;
        Task created = createWorkingTask();
        assertEquals(TaskState.TASK_STATE_WORKING, created.status().state());
        assertNull(created.status().message(), "the task starts without a status message");

        Client subscriber = open("1.1", true, false);
        Collected seen = new Collected();
        subscriber.subscribeToTask(new TaskIdParams(created.id()), List.of(seen.consumer()), seen.errors(), null);
        assertTrue(seen.awaitEvents(1, 10), "initial snapshot");
        assertTrue(awaitChildQueues(created.id(), 1));

        Map<String, Object> change = Map.of("p3-meta-key", "p3-meta-value");
        AgentExecutorProducer.MetadataProposalProbe probe = new AgentExecutorProducer.MetadataProposalProbe(change);
        AgentExecutorProducer.PHASE3_METADATA_PROBE.set(probe);
        try {
            Client writer = open("1.1", false, false);
            CompletableFuture<Outcome> send = CompletableFuture.supplyAsync(
                    () -> observe(writer, input(created, "p3-meta-input", "phase3:metadata-proposal", null)));
            assertTrue(probe.entered.await(20, TimeUnit.SECONDS), "the agent must reach its proposal barrier");

            // Isolated pre-state: the ordinary input (which clears any status message) and then the agent's own
            // message-bearing status are committed, the agent is held, and nothing else can mutate the task.
            Task before = Objects.requireNonNull(storedTask(created.id()));
            assertEquals(before, probe.invocationTask.get(), "the proposal is built from exactly the committed state");
            assertEquals(created.generation() + 2, before.generation(),
                    "the input and the message-bearing status are the only mutations");
            assertEquals(TaskState.TASK_STATE_WORKING, before.status().state());
            assertNull(before.metadata() == null ? null : before.metadata().get("p3-meta-key"));
            assertEquals(messageId, Objects.requireNonNull(before.status().message()).messageId());
            assertTrue(awaitSubscriberGeneration(seen, before.generation()),
                    () -> "the subscriber must have seen the input before the proposal: " + seen.generations());
            int streamMark = seen.events.size();
            int publishedMark = Phase3Spy.published(created.id()).size();
            int replicatedMark = Phase3Spy.replicated(created.id()).size();
            Client legacy = open("1.0", false, false);
            Task legacyBefore = legacy.getTask(new TaskQueryParams(created.id()));
            Phase3OldObserver.TaskRead read03Before = read03Task(created.id());
            long occurrencesBefore = occurrences(before, messageId);
            assertEquals(1, occurrencesBefore, "the status message occurs once before the proposal");
            assertEquals(occurrencesBefore, occurrences(legacyBefore, messageId));

            probe.release.countDown();
            Outcome outcome = send.get(30, TimeUnit.SECONDS);
            assertNull(outcome.failure(), () -> String.valueOf(outcome.failure()));

            // The correlated local result is the committed proposal: one status event and nothing else.
            ProposalResult result = probe.result.get(20, TimeUnit.SECONDS);
            ProposalResult.Committed committed = assertInstanceOf(ProposalResult.Committed.class, result);
            assertTrue(committed.changed(), "a metadata-only proposal is a change");
            assertEquals(1, committed.events().size(), () -> String.valueOf(committed.events()));
            TaskStatusUpdateEvent committedStatus =
                    assertInstanceOf(TaskStatusUpdateEvent.class, committed.events().get(0));
            assertEquals(before.generation() + 1, committedStatus.generation());
            assertEquals(change, committedStatus.metadata());
            assertEquals(messageId, Objects.requireNonNull(committedStatus.status().message()).messageId());
            assertEquals(before.status(), committedStatus.status(), "the status is unchanged");

            Task after = Objects.requireNonNull(storedTask(created.id()));
            assertMetadataOnlyCommit("stored", before, after, messageId, change, true);
            assertEquals(committed.snapshot().task().generation(), after.generation());
            assertEquals(before.status(), after.status());
            assertEquals(before.artifacts(), after.artifacts());
            assertEquals(before.history(), after.history(), "no message was authored or moved into the history");
            assertEquals(before.timeline(), after.timeline().subList(0, before.timeline().size()),
                    "the earlier timeline is untouched");
            assertEquals(0, messageEntries(after, messageId), "no message timeline entry for the status message");
            assertEquals(occurrencesBefore, occurrences(after, messageId));

            // send, get and list through the public 1.1 client
            assertMetadataOnlyCommit("send", before, Objects.requireNonNull(outcome.task()), messageId, change, false);
            Task got = writer.getTask(new TaskQueryParams(created.id(), 50, null, null, 50));
            assertMetadataOnlyCommit("get", before, got, messageId, change, true);
            Task listed = writer.listTasks(ListTasksParams.builder().contextId(created.contextId()).historyLength(50)
                    .timelineLength(50).build()).tasks().stream().filter(task -> task.id().equals(created.id()))
                    .findFirst().orElseThrow();
            assertMetadataOnlyCommit("list", before, listed, messageId, change, true);

            // the active subscriber: exactly one published status event, no message update
            assertTrue(awaitPublished(created.id(), publishedMark), "the status event must be published");
            List<String> publishedAll = Phase3Spy.published(created.id());
            List<String> published = publishedAll.subList(publishedMark, publishedAll.size());
            assertTrue(published.stream().allMatch("TaskStatusUpdateEvent"::equals),
                    () -> "only status events are published for the proposal: " + published);
            List<String> replicatedAll = Phase3Spy.replicated(created.id());
            List<String> replicated = replicatedAll.subList(replicatedMark, replicatedAll.size());
            assertTrue(replicated.stream().noneMatch(kind -> kind.equals("TaskMessageUpdateEvent")
                    || kind.equals("Message")), () -> "nothing message-bearing is replicated: " + replicated);
            assertTrue(seen.awaitEvents(streamMark + 1, 10), "the subscriber must receive the status update");
            List<ClientEvent> eventsNow = List.copyOf(seen.events);
            List<ClientEvent> streamed = eventsNow.subList(streamMark, eventsNow.size());
            assertEquals(1, streamed.size(), () -> "exactly one event for the proposal: " + streamed);
            TaskUpdateEvent update = assertInstanceOf(TaskUpdateEvent.class, streamed.get(0));
            TaskStatusUpdateEvent streamedStatus = assertInstanceOf(TaskStatusUpdateEvent.class, update.getUpdateEvent());
            assertEquals(before.generation() + 1, streamedStatus.generation());
            assertEquals(change, streamedStatus.metadata());
            assertEquals(messageId, Objects.requireNonNull(streamedStatus.status().message()).messageId());
            assertTrue(streamed.stream().noneMatch(event -> event instanceof TaskUpdateEvent message
                    && message.getUpdateEvent() instanceof TaskMessageUpdateEvent), "no message update event");
            assertMetadataOnlyCommit("stream", before, update.getTask(), messageId, change, true);

            // older readers: the same legacy occurrence count, no duplicate history entry
            Task legacyAfter = legacy.getTask(new TaskQueryParams(created.id()));
            assertEquals(historyIds(legacyBefore), historyIds(legacyAfter), "1.0 history is unchanged");
            assertEquals(occurrencesBefore, occurrences(legacyAfter, messageId), "1.0 occurrence count is unchanged");
            assertEquals(messageId, Objects.requireNonNull(legacyAfter.status().message()).messageId());
            assertEquals("p3-meta-value", Objects.requireNonNull(legacyAfter.metadata()).get("p3-meta-key"));
            Phase3OldObserver.TaskRead read03After = read03Task(created.id());
            assertEquals(read03Before.historyMessageIds(), read03After.historyMessageIds(),
                    "0.3 history is unchanged");
            assertEquals(read03Before.state(), read03After.state());
        } finally {
            probe.release.countDown();
            AgentExecutorProducer.PHASE3_METADATA_PROBE.compareAndSet(probe, null);
        }
    }

    /**
     * One committed generation, one new status timeline entry carrying the same status message, the metadata change,
     * and the same message occurrences, on a view of the committed task.
     */
    private static void assertMetadataOnlyCommit(String what, Task before, Task view, String messageId,
            Map<String, Object> change, boolean fullTimeline) {
        assertEquals(before.generation() + 1, view.generation(), what + ": exactly one new generation");
        assertEquals(TaskState.TASK_STATE_WORKING, view.status().state(), what);
        assertEquals(messageId, Objects.requireNonNull(view.status().message(), what + ": status message").messageId(),
                what + ": the status message id is retained");
        assertTrue(Objects.requireNonNull(view.metadata(), what + ": metadata").entrySet().containsAll(change.entrySet()),
                () -> what + ": the metadata change is visible: " + view.metadata());
        assertEquals(1, occurrences(view, messageId), what + ": the status message still occurs once");
        TimelineEntry last = view.timeline().get(view.timeline().size() - 1);
        assertEquals(before.generation() + 1, last.generation(), what + ": the newest timeline entry");
        TimelineEntry.StatusValue newest = assertInstanceOf(TimelineEntry.StatusValue.class, last.entry(), what);
        assertEquals(messageId, Objects.requireNonNull(newest.status().message()).messageId(), what);
        assertSameStatus(what + ": the observed status is the unchanged status", before.status(), newest.status());
        assertSameStatus(what + ": the task status is the unchanged status", before.status(), view.status());
        if (fullTimeline) {
            assertEquals(before.timeline().size() + 1, view.timeline().size(), what + ": one new timeline entry");
            assertEquals(1, view.timeline().stream().filter(entry -> entry.generation() > before.generation()).count(),
                    what + ": no other entry after the starting generation");
            assertEquals(0, messageEntries(view, messageId), what + ": no message timeline entry");
            assertEquals(historyIds(before), historyIds(view), what + ": the history is unchanged");
        }
    }

    /**
     * Identity of a status across the wire: state, timestamp and the message's id, role, task/context and text. The
     * binding codecs map an absent metadata map to an empty one, so exact record equality is asserted only on the
     * server-side stored and committed values, never on a wire view.
     */
    private static void assertSameStatus(String what, TaskStatus expected, TaskStatus actual) {
        assertEquals(expected.state(), actual.state(), what);
        assertEquals(expected.timestamp(), actual.timestamp(), what);
        Message expectedMessage = Objects.requireNonNull(expected.message(), what);
        Message actualMessage = Objects.requireNonNull(actual.message(), what);
        assertEquals(expectedMessage.messageId(), actualMessage.messageId(), what);
        assertEquals(expectedMessage.role(), actualMessage.role(), what);
        assertEquals(expectedMessage.taskId(), actualMessage.taskId(), what);
        assertEquals(expectedMessage.contextId(), actualMessage.contextId(), what);
        assertEquals(expectedMessage.parts().stream().map(part -> ((TextPart) part).text()).toList(),
                actualMessage.parts().stream().map(part -> ((TextPart) part).text()).toList(), what);
    }

    /** Occurrences of the message in the legacy view: history entries plus the status message. */
    private static long occurrences(Task task, String messageId) {
        long count = task.history().stream().filter(message -> messageId.equals(message.messageId())).count();
        Message status = task.status().message();
        return count + (status != null && messageId.equals(status.messageId()) ? 1 : 0);
    }

    private static List<String> historyIds(Task task) {
        return task.history().stream().map(Message::messageId).toList();
    }

    private static boolean awaitSubscriberGeneration(Collected seen, long generation) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            List<Long> generations = seen.generations();
            if (!generations.isEmpty() && generations.get(generations.size() - 1) >= generation) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static boolean awaitPublished(String taskId, int mark) throws InterruptedException {
        for (int i = 0; i < 200 && Phase3Spy.published(taskId).size() <= mark; i++) {
            Thread.sleep(50);
        }
        return Phase3Spy.published(taskId).size() > mark;
    }

    // ---- scenario 8: lost final publication ---------------------------------------------------------------------

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

        // The test profile shortens the recovery bounds (200/1000/600 ms) from the shipped 5/5/10 s defaults.
        assertTrue(seen.awaitFailure(20), () -> "the affected stream must be closed within the recovery bound: "
                + seen.events);
        long closedAfterMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lostAt);
        assertTrue(closedAfterMillis < 15_000, "closed after " + closedAfterMillis + " ms");
        assertTypedFailure(seen.failure.get(), InternalError.class, "GetTask");
        assertFalse(seen.events.stream().anyMatch(event -> terminal(event)), "the lost final state is never invented");
        boolean released = awaitChildQueues(base.id(), 0);
        assertTrue(released, "the closed observation releases its child queue, count=" + childQueueCount(base.id()));

        drainIngress();
        Task committedBefore = storedTask(base.id());
        Task explicit = subscriber.getTask(new TaskQueryParams(base.id()));
        assertEquals(TaskState.TASK_STATE_COMPLETED, explicit.status().state());
        assertEquals(completed.generation(), explicit.generation());
        assertEquals(1, drainIngress().size(), "the explicit GetTask is the only request: nothing is resent");
        assertEquals(committedBefore, storedTask(base.id()), "recovery never mutates the task");
        assertEquals(0, pushConfigCount(base.id()));
    }

    /**
     * The same loss on an ordinary streaming send rather than a subscription: the sender's own stream is observed from
     * the moment it attaches, so a final publication lost while the agent is still running closes that stream with
     * the resynchronize error, and an explicit GetTask returns the committed final state with nothing resent.
     */
    @Test
    public void lostFinalPublicationOnAStreamingSendClosesTheStreamAndExplicitGetTaskReturnsTheFinalState()
            throws Exception {
        Task base = createWorkingTask();
        CountDownLatch hold = new CountDownLatch(1);
        AgentExecutorProducer.PHASE3_HOLD.set(hold);
        try {
            Client sender = open("1.1", true, false);
            Collected seen = new Collected();
            sender.sendInput(input(base, "p3-held-send", "phase3:hold", null), List.of(seen.consumer()),
                    seen.errors(), null);
            // The held agent has streamed its artifact, so its work is committed and published before the loss.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (System.nanoTime() < deadline && seen.events.stream().noneMatch(event ->
                    event instanceof TaskUpdateEvent update
                            && update.getUpdateEvent() instanceof TaskArtifactUpdateEvent)) {
                Thread.sleep(50);
            }
            assertTrue(seen.events.stream().anyMatch(event -> event instanceof TaskUpdateEvent update
                    && update.getUpdateEvent() instanceof TaskArtifactUpdateEvent), "the held agent's artifact");
            assertNull(seen.failure.get());
            assertTrue(awaitChildQueues(base.id(), 1));

            Task committedWork = storedTask(base.id());
            assertNotNull(committedWork);
            Task completed = Task.builder(committedWork)
                    .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).generation(committedWork.generation() + 1)
                    .build();
            long lostAt = System.nanoTime();
            commitWithoutPublication(completed);

            assertTrue(seen.awaitFailure(20), () -> "the sender's stream must be closed within the recovery bound: "
                    + seen.events);
            long closedAfterMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lostAt);
            assertTrue(closedAfterMillis < 15_000, "closed after " + closedAfterMillis + " ms");
            assertTypedFailure(seen.failure.get(), InternalError.class, "GetTask");
            assertFalse(seen.events.stream().anyMatch(event -> terminal(event)),
                    "the lost final state is never invented");
            assertTrue(awaitChildQueues(base.id(), 0),
                    "the closed observation releases its child queue, count=" + childQueueCount(base.id()));

            drainIngress();
            Task committedBefore = storedTask(base.id());
            Task explicit = sender.getTask(new TaskQueryParams(base.id()));
            assertEquals(TaskState.TASK_STATE_COMPLETED, explicit.status().state());
            assertEquals(completed.generation(), explicit.generation());
            assertEquals(1, drainIngress().size(), "the explicit GetTask is the only request: nothing is resent");
            assertEquals(committedBefore, storedTask(base.id()), "recovery never mutates the task");
            assertEquals(0, pushConfigCount(base.id()));
        } finally {
            hold.countDown();
        }
    }

    // ---- scenario 9: every existing entry point reduces the same stamps ------------------------------------------

    @Test
    public void unaryStreamingAndNoCursorSubscribeReduceTheSameCommittedStamps() throws Exception {
        for (String route : List.of("unary", "streaming", "subscribe", "streaming-observed", "unary-observed")) {
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
            if (route.endsWith("-observed")) {
                Outcome outcome = observe(writer, input(base, "p3-" + route, "phase3:artifacts-complete", null));
                assertNull(outcome.failure(), route);
                Task stored = storedTask(base.id());
                assertNotNull(outcome.view(), route);
                if (route.equals("streaming-observed")) {
                    // An existing-task stream has no snapshot frame, so the reducer has no baseline and must say so
                    // rather than invent a synchronized stamp: the view is partial, carries no task-level generation
                    // and no through-generation, while the timeline entries it did see keep their committed stamps.
                    assertEquals(ViewSync.NO_BASELINE, outcome.view().sync(), route + " sync");
                    assertNull(outcome.through(), route + " has no through-generation");
                    Task partial = outcome.task();
                    assertNotNull(partial, route + " partial task");
                    assertEquals(0, partial.generation(), route + " invents no task-level stamp");
                    assertEquals(stored.generation(),
                            partial.timeline().get(partial.timeline().size() - 1).generation(),
                            route + " the last streamed timeline stamp is the committed head");
                    view = partial;
                } else {
                    view = outcome.task();
                    assertNotNull(view, route);
                    assertEquals(stored.generation(), view.timeline().get(view.timeline().size() - 1).generation(),
                            route + " reduced timeline stamp");
                    assertEquals(stored.generation(), view.generation(), route + " stamp");
                    assertEquals(ViewSync.SYNCHRONIZED, outcome.view().sync(), route + " sync");
                    assertEquals(stored.generation(), outcome.through(), route + " through-generation");
                }
            } else {
                writer.sendMessage(userMessage(base, "p3-" + route, "phase3:artifacts-complete"),
                        List.of(route.equals("subscribe") ? (event, card) -> { } : seen.consumer()), seen.errors(), null);
                assertTrue(seen.awaitTerminal(15), () -> route + " must reach the terminal state: " + seen.events);
                view = seen.lastTask();
                Task stored = storedTask(base.id());
                if (route.equals("streaming")) {
                    // an existing-task stream has no snapshot frame: its callback task carries no stamp, but every
                    // streamed update event carries its committed generation and the last one is the head
                    assertEquals(stored.generation(), seen.maxUpdateEventGeneration(), route + " update stamps");
                } else {
                    assertEquals(stored.generation(), view.generation(), route + " stamp");
                }
            }
            assertEquals(TaskState.TASK_STATE_COMPLETED, view.status().state(), route);
            assertTrue(view.artifacts().stream().map(Artifact::artifactId).toList().containsAll(List.of("p3-a1", "p3-a2")),
                    route + " artifacts");
            assertEquals(1, messageEntries(writer.getTask(new TaskQueryParams(base.id(), null, null, null, 50)),
                    "p3-" + route), route + " message count");
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private static TaskPushNotificationConfig inlineConfig() {
        return TaskPushNotificationConfig.builder().url(Phase3Webhook.url()).build();
    }

    /** Waits until the webhook has received the COMPLETED state of the task, as a task or a status update. */
    private static List<StreamingEventKind> awaitTerminalNotification(String taskId) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            List<StreamingEventKind> events = Phase3Webhook.eventsFor(taskId);
            if (events.stream().anyMatch(event -> event instanceof Task task
                    && task.status().state() == TaskState.TASK_STATE_COMPLETED
                    || event instanceof TaskStatusUpdateEvent status
                    && status.status().state() == TaskState.TASK_STATE_COMPLETED)) {
                return events;
            }
            Thread.sleep(50);
        }
        return List.of();
    }

    private static boolean awaitQueueClosed(String taskId) throws InterruptedException {
        for (int i = 0; i < 200 && !Phase3Spy.queueWasClosed(taskId); i++) {
            Thread.sleep(50);
        }
        return Phase3Spy.queueWasClosed(taskId);
    }

    private static boolean generationsStrictlyIncrease(List<Long> generations) {
        for (int i = 1; i < generations.size(); i++) {
            if (generations.get(i) <= generations.get(i - 1)) {
                return false;
            }
        }
        return true;
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
