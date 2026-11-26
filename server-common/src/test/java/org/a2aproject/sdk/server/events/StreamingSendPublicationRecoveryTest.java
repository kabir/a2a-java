package org.a2aproject.sdk.server.events;

import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_COMPLETED;
import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_INPUT_REQUIRED;
import static org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.CommittedMutation;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskManager;
import org.a2aproject.sdk.server.tasks.TaskMutationAuthority;
import org.a2aproject.sdk.server.tasks.TaskMutationPlanner;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Publication recovery for ordinary streaming sends ({@code onMessageSendStream} and the message-bearing
 * {@code onSendInputStream}), with the reconciler installed. Time is injected: every test drives
 * {@link PublicationReconciler#tick()} directly and moves a fake nano clock; the agent is held on a latch rather
 * than raced against a timer.
 */
class StreamingSendPublicationRecoveryTest {

    private static final String TASK_ID = "send-task";
    private static final String CONTEXT_ID = "send-context";
    private static final long START_NANOS = 1_000_000_000_000L;
    private static final String CURRENT = "1.1";

    private enum Variant {
        NEW_SEND_STREAM(false, false),
        EXISTING_SEND_STREAM(true, false),
        NEW_SEND_INPUT_STREAM(false, true),
        EXISTING_SEND_INPUT_STREAM(true, true);

        private final boolean existing;
        private final boolean viaInput;

        Variant(boolean existing, boolean viaInput) {
            this.existing = existing;
            this.viaInput = viaInput;
        }
    }

    // ---- a lost final publication closes the affected stream, and GetTask has the committed final state ----

    @ParameterizedTest
    @EnumSource(Variant.class)
    void lostFinalPublicationClosesTheSendStreamAndGetTaskReturnsTheCommittedFinalState(Variant variant)
            throws Exception {
        lostFinal(variant, CURRENT);
    }

    @ParameterizedTest
    @CsvSource({
            "NEW_SEND_STREAM, 1.0", "EXISTING_SEND_STREAM, 1.0", "NEW_SEND_INPUT_STREAM, 1.0",
            "NEW_SEND_STREAM, 0.3", "EXISTING_SEND_STREAM, 0.3", "EXISTING_SEND_INPUT_STREAM, 0.3"
    })
    void lostFinalPublicationIsDetectedForEveryLegacyProjection(Variant variant, String version) throws Exception {
        lostFinal(variant, version);
    }

    private static void lostFinal(Variant variant, String version) throws Exception {
        try (Rig rig = new Rig()) {
            if (variant.existing) {
                rig.seed(TASK_STATE_INPUT_REQUIRED, 4);
            }
            rig.script = (context, emitter) -> {
                rig.begin(context);
                emitter.startWork();
                rig.release.await();
            };
            ServerCallContext context = rig.context(version);

            Collector collector = rig.send(variant, context);
            awaitWorking(collector);
            String taskId = rig.taskId.get();
            Task before = rig.store.get(taskId);
            long deliveredHead = before.generation();

            rig.commit(taskId, TASK_STATE_COMPLETED);   // committed to the authority; its publication is lost
            Task committed = rig.store.get(taskId);
            assertEquals(deliveredHead + 1, committed.generation());
            rig.tick(0);
            rig.tick(5);
            rig.tick(5);

            assertTrue(collector.failed.await(5, TimeUnit.SECONDS), "the stream must be closed within the bound");
            InternalError failure = assertInstanceOf(InternalError.class, collector.failure);
            assertTrue(failure.getMessage().contains("GetTask"), failure.getMessage());
            assertEquals(1, collector.completed.getCount(), "an error, not a normal completion");
            Task finalState = rig.handler.onGetTask(new TaskQueryParams(taskId, null, null), context);
            assertEquals(TASK_STATE_COMPLETED, finalState.status().state());
            // Recovery is an error plus an explicit read: nothing was mutated and nothing was resent.
            assertEquals(committed, rig.store.get(taskId), "no mutation by recovery");
            rig.tick(30);
            assertEquals(committed, rig.store.get(taskId));
            assertEquals(1, rig.agentRuns.get());
            assertEquals(0, rig.reconciler.activeObservationCount());
            if (CURRENT.equals(version)) {
                List<Long> generations = collector.generations();
                assertFalse(generations.isEmpty());
                for (int i = 0; i < generations.size(); i++) {
                    assertTrue(generations.get(i) > (variant.existing ? 4 : 0), "no baseline before the first commit");
                    assertTrue(i == 0 || generations.get(i) > generations.get(i - 1), "strictly increasing");
                    assertTrue(generations.get(i) <= deliveredHead, "the lost final state is never resent");
                }
                if (variant.existing) {
                    assertEquals(0, collector.events.stream().filter(Task.class::isInstance).count(),
                            "no snapshot is prepended to an ordinary send stream");
                }
            }
        }
    }

    // ---- no recovery cost or false failure on a healthy send ----

    @ParameterizedTest
    @CsvSource({
            "NEW_SEND_STREAM, 1.1", "EXISTING_SEND_STREAM, 1.1", "NEW_SEND_INPUT_STREAM, 1.1",
            "EXISTING_SEND_INPUT_STREAM, 1.1", "NEW_SEND_STREAM, 1.0", "EXISTING_SEND_STREAM, 0.3"
    })
    void aHealthySendStreamCompletesOnceAndReleasesItsObservation(Variant variant, String version) throws Exception {
        try (Rig rig = new Rig()) {
            if (variant.existing) {
                rig.seed(TASK_STATE_INPUT_REQUIRED, 4);
            }
            rig.script = (context, emitter) -> {
                rig.begin(context);
                emitter.startWork();
                emitter.addArtifact(List.of(new TextPart("data")));
                emitter.complete();
            };

            Collector collector = rig.send(variant, rig.context(version));

            assertTrue(collector.completed.await(5, TimeUnit.SECONDS));
            assertNull(collector.failure);
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> rig.reconciler.activeObservationCount() == 0);
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !rig.reconciler.isScheduled());
            for (int i = 0; i < 4; i++) {
                rig.tick(5);
            }
            assertNull(collector.failure, "no false gap after the stream ended");
            assertEquals(TASK_STATE_COMPLETED, rig.store.get(rig.taskId.get()).status().state());
            if (CURRENT.equals(version)) {
                List<Long> generations = collector.generations();
                assertEquals(generations.stream().distinct().toList(), generations, "no event repeated");
                assertEquals(generations.stream().sorted().toList(), generations, "in order");
                assertEquals(rig.store.get(rig.taskId.get()).generation(),
                        generations.get(generations.size() - 1).longValue());
            }
        }
    }

    // ---- handover: the observation exists before the authoritative generation is read ----

    @ParameterizedTest
    @EnumSource(value = Variant.class, names = {"EXISTING_SEND_STREAM", "EXISTING_SEND_INPUT_STREAM"})
    void aCommitRacingTheAttachIsDiscardedWhenCoveredAndALaterOneIsDeliveredOnce(Variant variant) throws Exception {
        try (Rig rig = new Rig()) {
            rig.seed(TASK_STATE_INPUT_REQUIRED, 4);
            AtomicLong racing = new AtomicLong();
            rig.store.afterObservationExists.set(() -> {
                rig.commitAndPublish(TASK_ID, TASK_STATE_WORKING);
                racing.set(rig.store.get(TASK_ID).generation());
            });
            rig.script = (context, emitter) -> {
                rig.begin(context);
                emitter.addArtifact(List.of(new TextPart("later")));
                rig.release.await();
            };

            Collector collector = rig.send(variant, rig.context(CURRENT));

            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(
                    () -> collector.events.stream().anyMatch(TaskArtifactUpdateEvent.class::isInstance));
            assertTrue(racing.get() > 4, "the racing commit happened while the observation was installed");
            List<Long> generations = collector.generations();
            assertEquals(List.of(racing.get() + 1), generations,
                    "the covered commit " + racing.get() + " is discarded and the later one delivered exactly once");
            assertEquals(0, collector.events.stream().filter(Task.class::isInstance).count());
            assertNull(collector.failure);
        }
    }

    @Test
    void aNewTaskStreamDeliversEveryCommittedGenerationFromTheFirst() throws Exception {
        try (Rig rig = new Rig()) {
            rig.script = (context, emitter) -> {
                rig.begin(context);
                emitter.startWork();
                rig.release.await();
            };

            Collector collector = rig.send(Variant.NEW_SEND_STREAM, rig.context(CURRENT));
            awaitWorking(collector);

            List<Long> generations = collector.generations();
            assertEquals(1L, generations.get(0).longValue(), "the opening batch is not covered by a snapshot");
            assertEquals(rig.store.get(rig.taskId.get()).generation(),
                    generations.get(generations.size() - 1).longValue());
            assertEquals(generations.stream().distinct().toList(), generations);
        }
    }

    // ---- other stream lifecycles ----

    @ParameterizedTest
    @EnumSource(value = Variant.class, names = {"NEW_SEND_STREAM", "NEW_SEND_INPUT_STREAM"})
    void aStreamingDirectMessageReleasesItsObservationAndSchedulesNothing(Variant variant) throws Exception {
        try (Rig rig = new Rig()) {
            rig.script = (context, emitter) -> {
                rig.begin(context);
                emitter.sendMessage("direct reply");
            };

            Collector collector = rig.send(variant, rig.context(CURRENT));

            assertTrue(collector.completed.await(5, TimeUnit.SECONDS));
            assertNull(collector.failure);
            assertEquals(1, collector.events.size());
            assertInstanceOf(Message.class, collector.events.get(0));
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> rig.reconciler.activeObservationCount() == 0);
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !rig.reconciler.isScheduled());
            assertNull(rig.store.get(rig.taskId.get()), "a direct message creates no task");
        }
    }

    @Test
    void anArtifactOnlyInputStreamHasNoQueueAndIsNotObserved() throws Exception {
        try (Rig rig = new Rig()) {
            rig.seed(TASK_STATE_WORKING, 1);
            TaskArtifactUpdateEvent artifact = TaskArtifactUpdateEvent.builder().taskId(TASK_ID)
                    .contextId(CONTEXT_ID)
                    .artifact(Artifact.builder().artifactId("a1").parts(new TextPart("d")).build()).build();
            Collector collector = new Collector();

            rig.handler.onSendInputStream(SendInputParams.builder().artifactUpdates(List.of(artifact)).build(),
                    rig.context(CURRENT)).subscribe(collector);

            assertTrue(collector.completed.await(5, TimeUnit.SECONDS));
            assertEquals(1, collector.events.size());
            assertEquals(0, rig.agentRuns.get(), "artifact-only input does not invoke the executor");
            assertEquals(0, rig.reconciler.activeObservationCount());
            assertFalse(rig.reconciler.isScheduled());
            verifyNoInteractions(rig.scheduler);
        }
    }

    @Test
    void aClientThatCancelsAnObservedSendStreamReleasesTheObservation() throws Exception {
        try (Rig rig = new Rig()) {
            rig.script = (context, emitter) -> {
                rig.begin(context);
                emitter.startWork();
                rig.release.await();
            };
            Collector collector = rig.send(Variant.NEW_SEND_STREAM, rig.context(CURRENT));
            awaitWorking(collector);
            assertEquals(1, rig.reconciler.activeObservationCount());

            collector.subscription.cancel();

            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> rig.reconciler.activeObservationCount() == 0);
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !rig.reconciler.isScheduled());
        }
    }

    @Test
    void anAgentFailureEndsTheStreamAndReleasesTheObservation() throws Exception {
        try (Rig rig = new Rig()) {
            rig.script = (context, emitter) -> {
                rig.begin(context);
                throw new InternalError("agent failed");
            };

            Collector collector = rig.send(Variant.NEW_SEND_STREAM, rig.context(CURRENT));

            assertTrue(collector.failed.await(5, TimeUnit.SECONDS) || collector.completed.await(5, TimeUnit.SECONDS));
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> rig.reconciler.activeObservationCount() == 0);
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !rig.reconciler.isScheduled());
        }
    }

    // ---- exception cleanup before the stream is returned ----

    @ParameterizedTest
    @EnumSource(Variant.class)
    void aClosedReconcilerRejectsTheSendAndLeavesNoAgentOrChild(Variant variant) {
        try (Rig rig = new Rig()) {
            if (variant.existing) {
                rig.seed(TASK_STATE_INPUT_REQUIRED, 4);
            }
            rig.script = (context, emitter) -> rig.begin(context);
            rig.reconciler.close();

            assertThrows(IllegalStateException.class, () -> rig.send(variant, rig.context(CURRENT)));

            assertEquals(0, rig.agentRuns.get(), "the agent must not start on a stream nobody will observe");
            assertEquals(0, rig.reconciler.activeObservationCount());
            if (variant.existing) {
                assertTrue(rig.queueManager.getActiveChildQueueCount(TASK_ID) <= 0, "the tapped child is closed");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Variant.class, names = {"EXISTING_SEND_STREAM", "EXISTING_SEND_INPUT_STREAM"})
    void anAuthorityReadThatFindsNoTaskRejectsTheSendAndDetachesTheObservation(Variant variant) {
        try (Rig rig = new Rig()) {
            rig.seed(TASK_STATE_INPUT_REQUIRED, 4);
            rig.store.hideWhenObserved = true;
            rig.script = (context, emitter) -> rig.begin(context);

            assertThrows(TaskNotFoundError.class, () -> rig.send(variant, rig.context(CURRENT)));

            assertEquals(0, rig.agentRuns.get());
            assertEquals(0, rig.reconciler.activeObservationCount());
            assertFalse(rig.reconciler.isScheduled());
            assertTrue(rig.queueManager.getActiveChildQueueCount(TASK_ID) <= 0, "the tapped child is closed");
        }
    }

    // ---- T13: reserve and release stay paired around the input commit ----

    @ParameterizedTest
    @EnumSource(value = Variant.class, names = {"EXISTING_SEND_STREAM", "EXISTING_SEND_INPUT_STREAM"})
    void theInputCommitOfAnObservedSendKeepsItsReserveAndReleasePaired(Variant variant) throws Exception {
        try (Rig rig = new Rig()) {
            rig.seed(TASK_STATE_INPUT_REQUIRED, 4);
            EventQueue live = rig.queueManager.createOrTap(TASK_ID);   // a live task queue, so the hook is in play
            EventQueue keepAlive = rig.queueManager.tap(TASK_ID);
            rig.script = (context, emitter) -> {
                rig.begin(context);
                emitter.startWork();
                rig.release.await();
            };
            try {
                Collector collector = rig.send(variant, rig.context(CURRENT));
                awaitWorking(collector);

                assertEquals(List.of("reserve:" + TASK_ID, "release:" + TASK_ID),
                        rig.hook.holds.stream().filter(entry -> !entry.startsWith("batch")).toList(),
                        "exactly one hold, released after the input committed");
                assertEquals(1, rig.hook.reserved.get());
                assertEquals(1, rig.hook.released.get());
            } finally {
                keepAlive.close(true);
                live.close(true);
            }
        }
    }

    @Test
    void aRejectedInputToAnObservedSendLeavesNoHoldAndNoObservation() {
        try (Rig rig = new Rig()) {
            rig.seed(TASK_STATE_COMPLETED, 4);
            EventQueue live = rig.queueManager.createOrTap(TASK_ID);
            rig.script = (context, emitter) -> rig.begin(context);
            try {
                assertThrows(A2AError.class, () -> rig.send(Variant.EXISTING_SEND_STREAM, rig.context(CURRENT)));

                assertEquals(rig.hook.reserved.get(), rig.hook.released.get(), "reserve and release stay paired");
                assertEquals(0, rig.agentRuns.get());
                assertEquals(0, rig.reconciler.activeObservationCount());
            } finally {
                live.close(true);
            }
        }
    }

    // ---- helpers ----

    private static void awaitWorking(Collector collector) {
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> collector.events.stream()
                .anyMatch(event -> event instanceof TaskStatusUpdateEvent update
                        && update.status().state() == TASK_STATE_WORKING));
    }

    private static long generationOf(Event event) {
        if (event instanceof Task task) {
            return task.generation();
        }
        if (event instanceof TaskStatusUpdateEvent update) {
            return update.generation();
        }
        if (event instanceof TaskArtifactUpdateEvent update) {
            return update.generation();
        }
        if (event instanceof TaskMessageUpdateEvent update) {
            return update.generation();
        }
        return -1;
    }

    private static Task saved(long generation, TaskState state) {
        return Task.builder()
                .id(TASK_ID)
                .contextId(CONTEXT_ID)
                .status(new TaskStatus(state))
                .generation(generation)
                .history(List.of())
                .artifacts(List.of())
                .build();
    }

    @FunctionalInterface
    private interface Script {
        void run(RequestContext context, AgentEmitter emitter) throws Exception;
    }

    /** Counts holds and records their order so reserve and release pairing can be asserted. */
    private static final class PairingHook implements EventEnqueueHook {

        private final List<String> holds = new CopyOnWriteArrayList<>();
        private final AtomicInteger reserved = new AtomicInteger();
        private final AtomicInteger released = new AtomicInteger();

        @Override
        public void onEnqueue(EventQueueItem item) {
        }

        @Override
        public boolean handlesCommittedBatches() {
            return true;
        }

        @Override
        public void onCommittedBatch(CommittedTaskBatch batch) {
            holds.add("batch");
        }

        @Override
        public void onCommittedBatchReserved(String taskId) {
            reserved.incrementAndGet();
            holds.add("reserve:" + taskId);
        }

        @Override
        public void onCommittedBatchReleased(String taskId) {
            released.incrementAndGet();
            holds.add("release:" + taskId);
        }
    }

    /** A store whose snapshot read can run a hook once the handler's observation is installed, or report no task. */
    private static final class GatedStore extends InMemoryTaskStore {

        private final AtomicReference<Runnable> afterObservationExists = new AtomicReference<>();
        private volatile PublicationReconciler observer;
        private volatile boolean hideWhenObserved;

        @Override
        public TaskSnapshot readSnapshot(String taskId) {
            PublicationReconciler watching = observer;
            if (watching != null && watching.activeObservationCount() > 0) {
                if (hideWhenObserved) {
                    return null;
                }
                Runnable hook = afterObservationExists.getAndSet(null);
                if (hook != null) {
                    hook.run();
                }
            }
            return super.readSnapshot(taskId);
        }
    }

    private static final class Rig implements AutoCloseable {

        private final GatedStore store = new GatedStore();
        private final MainEventBus bus = new MainEventBus();
        private final PairingHook hook = new PairingHook();
        private final InMemoryQueueManager queueManager = new InMemoryQueueManager(store, bus) {
            @Override
            public EventQueue.EventQueueBuilder createBaseEventQueueBuilder(String taskId) {
                return super.createBaseEventQueueBuilder(taskId).hook(hook);
            }
        };
        private final MainEventBusProcessor processor =
                new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queueManager);
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final TaskMutationAuthority authority = new TaskMutationAuthority(store);
        private final AtomicLong clock = new AtomicLong(START_NANOS);
        private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        private final PublicationReconciler reconciler;
        private final DefaultRequestHandler handler;
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<String> taskId = new AtomicReference<>();
        private final AtomicInteger agentRuns = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile Script script = (context, emitter) -> {
        };

        private Rig() {
            ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
            doReturn(scheduled).when(scheduler)
                    .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            PublicationReconciler.AuthorityHead head = id ->
                    java.util.concurrent.CompletableFuture.completedFuture(OptionalLong.of(
                            TaskManager.readAuthoritativeTask(store, id).generation()));
            reconciler = new PublicationReconciler(PublicationReconciler.Limits.DEFAULTS, head, clock::get,
                    scheduler, event -> 100L);
            store.observer = reconciler;
            processor.setPushNotificationExecutor(Runnable::run);
            EventQueueUtil.start(processor);
            handler = DefaultRequestHandler.builder()
                    .agentExecutor(new AgentExecutor() {
                        @Override
                        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
                            agentRuns.incrementAndGet();
                            try {
                                script.run(context, emitter);
                            } catch (A2AError error) {
                                throw error;
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            } catch (Exception failure) {
                                throw new InternalError(String.valueOf(failure));
                            }
                        }

                        @Override
                        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
                        }
                    })
                    .taskStore(store)
                    .queueManager(queueManager)
                    .pushConfigStore(new InMemoryPushNotificationConfigStore())
                    .mainEventBusProcessor(processor)
                    .executor(executor)
                    .eventConsumerExecutor(executor)
                    .authorizationRequired(false)
                    .publicationReconciler(reconciler)
                    .build();
        }

        private void begin(RequestContext context) {
            taskId.set(context.getTaskId());
        }

        private void seed(TaskState state, long generation) {
            store.save(saved(generation, state), false);
        }

        private ServerCallContext context(String version) {
            ServerCallContext context = new ServerCallContext(new TestUser(), Map.of(), Set.of(), version);
            context.attachEffectiveAgentCard(null, AgentCard.builder()
                    .name("test").description("Test agent").version("1.0")
                    .supportedInterfaces(List.of()).skills(List.of())
                    .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text"))
                    .capabilities(AgentCapabilities.builder().streaming(true).userArtifacts(true).build())
                    .build());
            return context;
        }

        private Collector send(Variant variant, ServerCallContext context) throws A2AError {
            Message.Builder message = Message.builder().messageId("m-" + System.nanoTime())
                    .role(Message.Role.ROLE_USER).parts(new TextPart("hello"));
            if (variant.existing) {
                message.taskId(TASK_ID).contextId(CONTEXT_ID);
            }
            Flow.Publisher<StreamingEventKind> publisher = variant.viaInput
                    ? handler.onSendInputStream(SendInputParams.builder().message(message.build())
                            .artifactUpdates(List.of()).build(), context)
                    : handler.onMessageSendStream(MessageSendParams.builder().message(message.build()).build(),
                            context);
            Collector collector = new Collector();
            publisher.subscribe(collector);
            return collector;
        }

        /** Commits a status mutation to the authority without publishing it. */
        private CommittedTaskBatch commit(String id, TaskState state) {
            String contextId = store.get(id).contextId();
            CommittedMutation mutation = authority.mutate(id, current -> TaskMutationPlanner.planStatus(
                    current, TaskStatusUpdateEvent.builder().taskId(id).contextId(contextId)
                            .status(new TaskStatus(state)).build()));
            return new CommittedTaskBatch(mutation.snapshot(), mutation.events());
        }

        private void commitAndPublish(String id, TaskState state) {
            CommittedTaskBatch batch = commit(id, state);
            processor.deliverCommittedBatch((EventQueue.MainQueue) queueManager.get(id), batch, false);
        }

        private void tick(long secondsLater) {
            clock.addAndGet(TimeUnit.SECONDS.toNanos(secondsLater));
            reconciler.tick();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.countDown();
                reconciler.close();
                EventQueueUtil.stop(processor);
                executor.shutdownNow();
            }
        }
    }

    private static final class Collector implements Flow.Subscriber<StreamingEventKind> {

        private final List<StreamingEventKind> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch failed = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile Flow.Subscription subscription;
        private volatile Throwable failure;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(StreamingEventKind event) {
            events.add(event);
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
            failed.countDown();
        }

        @Override
        public void onComplete() {
            completed.countDown();
        }

        private List<Long> generations() {
            List<Long> generations = new ArrayList<>();
            for (StreamingEventKind event : events) {
                long generation = generationOf(event);
                if (generation >= 0) {
                    generations.add(generation);
                }
            }
            return generations;
        }
    }

    private record TestUser() implements User {

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public String getUsername() {
            return "send-user";
        }
    }
}
