package org.a2aproject.sdk.server.tasks;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.tasks.TaskAuthorityCapabilities.QueueReplication;
import org.a2aproject.sdk.server.tasks.TaskAuthorityCapabilities.WebhookDispatch;
import org.a2aproject.sdk.server.tasks.TaskAuthorityConfiguration.Topology;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

class TaskAuthorityConfigurationTest {

    private static final WebhookDispatch SHARED_WEBHOOKS = new WebhookDispatch(true, true, true);
    private static final WebhookDispatch LOCAL_WEBHOOKS = new WebhookDispatch(true, true, false);

    private static Topology atomic(TaskAuthorityCapabilities store, QueueReplication replication,
            WebhookDispatch webhooks) {
        return new Topology(true, store, replication, webhooks, "test.Store");
    }

    private static TaskAuthorityCapabilities sharedStore(Long storage, Long node, boolean migrated) {
        return TaskAuthorityCapabilities.sharedStorage(storage, node, migrated);
    }

    private static List<String> violations(Topology topology) {
        return TaskAuthorityConfiguration.of(topology).violations();
    }

    @Test
    void singleProcessMemoryProfilePassesWithoutDispatcherOrReplication() {
        assertEquals(List.of(), violations(atomic(TaskAuthorityCapabilities.processLocal(),
                QueueReplication.NONE, WebhookDispatch.NONE)));
        assertEquals(List.of(), violations(atomic(TaskAuthorityCapabilities.processLocal(),
                QueueReplication.NONE, LOCAL_WEBHOOKS)));
    }

    @Test
    void supportedSharedJpaProfilePasses() {
        assertEquals(List.of(), violations(atomic(sharedStore(7L, 7L, true),
                QueueReplication.NONE, SHARED_WEBHOOKS)));
        assertEquals(List.of(), violations(atomic(sharedStore(7L, 7L, true),
                new QueueReplication(true, 7L, true), SHARED_WEBHOOKS)));
    }

    @Test
    void legacyOnlyStoreFailsOnlyWhen11IsRequested() {
        Topology legacy = new Topology(false, TaskAuthorityCapabilities.UNDECLARED, QueueReplication.NONE,
                WebhookDispatch.NONE, "custom.LegacyStore");
        List<String> violations = violations(legacy);
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("no AtomicTaskStore companion"), violations.get(0));
        TaskAuthorityConfiguration configuration = TaskAuthorityConfiguration.of(legacy);
        assertThrows(IllegalStateException.class, configuration::requireVersion11);
    }

    @Test
    void atomicStoreWithoutDeclarationIsRejected() {
        List<String> violations = violations(atomic(TaskAuthorityCapabilities.UNDECLARED, QueueReplication.NONE,
                WebhookDispatch.NONE));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("authorityCapabilities"), violations.get(0));
    }

    @Test
    void replicatedQueuesWithIndependentInMemoryStoreAreRejected() {
        List<String> violations = violations(atomic(TaskAuthorityCapabilities.processLocal(),
                new QueueReplication(true, 7L, true), WebhookDispatch.NONE));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("process-local"), violations.get(0));
    }

    @Test
    void sharedGateWithProcessLocalStoreIsRejected() {
        List<String> violations = violations(atomic(TaskAuthorityCapabilities.processLocal(),
                QueueReplication.NONE, SHARED_WEBHOOKS));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("shared webhook gate"), violations.get(0));
    }

    @Test
    void unmigratedOrUnfencedSharedStoreIsRejected() {
        List<String> noEpoch = violations(atomic(sharedStore(null, null, false), QueueReplication.NONE,
                SHARED_WEBHOOKS));
        assertEquals(1, noEpoch.size());
        assertTrue(noEpoch.get(0).contains("unmigrated and unfenced"), noEpoch.get(0));

        List<String> unmigratedRows = violations(atomic(sharedStore(7L, 7L, false), QueueReplication.NONE,
                SHARED_WEBHOOKS));
        assertEquals(1, unmigratedRows.size());
        assertTrue(unmigratedRows.get(0).contains("not all baselined"), unmigratedRows.get(0));
    }

    @Test
    void mismatchedWriterEpochsAreRejected() {
        List<String> nodeMismatch = violations(atomic(sharedStore(7L, 6L, true), QueueReplication.NONE,
                SHARED_WEBHOOKS));
        assertEquals(1, nodeMismatch.size());
        assertTrue(nodeMismatch.get(0).contains("node's writer epoch 6"), nodeMismatch.get(0));

        List<String> noNodeEpoch = violations(atomic(sharedStore(7L, null, true), QueueReplication.NONE,
                SHARED_WEBHOOKS));
        assertEquals(1, noNodeEpoch.size());

        List<String> queueMismatch = violations(atomic(sharedStore(7L, 7L, true),
                new QueueReplication(true, 8L, true), SHARED_WEBHOOKS));
        assertEquals(1, queueMismatch.size());
        assertTrue(queueMismatch.get(0).contains("replicated queue manager writer epoch 8"), queueMismatch.get(0));
    }

    @Test
    void replicationWithoutEpochOrCommittedEnvelopesIsRejected() {
        List<String> noEpoch = violations(atomic(sharedStore(7L, 7L, true),
                new QueueReplication(true, null, true), SHARED_WEBHOOKS));
        assertEquals(1, noEpoch.size());
        assertTrue(noEpoch.get(0).contains("no writer epoch"), noEpoch.get(0));

        List<String> noEnvelopes = violations(atomic(sharedStore(7L, 7L, true),
                new QueueReplication(true, 7L, false), SHARED_WEBHOOKS));
        assertEquals(1, noEnvelopes.size());
        assertTrue(noEnvelopes.get(0).contains("supportsCommittedReplication"), noEnvelopes.get(0));
    }

    @Test
    void sharedProfileRejectsMissingOrMismatchedWebhookGate() {
        List<String> noDispatcher = violations(atomic(sharedStore(7L, 7L, true), QueueReplication.NONE,
                WebhookDispatch.NONE));
        assertEquals(1, noDispatcher.size());
        assertTrue(noDispatcher.get(0).contains("independent push chains"), noDispatcher.get(0));

        List<String> localGate = violations(atomic(sharedStore(7L, 7L, true), QueueReplication.NONE,
                LOCAL_WEBHOOKS));
        assertEquals(1, localGate.size());
        assertTrue(localGate.get(0).contains("process-local gate"), localGate.get(0));

        List<String> noQuiescence = violations(atomic(sharedStore(7L, 7L, true), QueueReplication.NONE,
                new WebhookDispatch(true, false, true)));
        assertEquals(1, noQuiescence.size());
        assertTrue(noQuiescence.get(0).contains("quiesce"), noQuiescence.get(0));
    }

    @Test
    void everyViolationIsReportedTogether() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                TaskAuthorityConfiguration.of(atomic(sharedStore(null, null, false),
                        new QueueReplication(true, null, false), WebhookDispatch.NONE))::requireVersion11);
        assertTrue(failure.getMessage().contains("unmigrated and unfenced"), failure.getMessage());
        assertTrue(failure.getMessage().contains("no writer epoch"), failure.getMessage());
        assertTrue(failure.getMessage().contains("supportsCommittedReplication"), failure.getMessage());
        assertTrue(failure.getMessage().contains("independent push chains"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Legacy-only 0.3/1.0"), failure.getMessage());
    }

    @Test
    void anUnreadableTopologyFailsClosed() {
        TaskAuthorityConfiguration configuration = TaskAuthorityConfiguration.of(() -> {
            throw new IllegalStateException("database unavailable");
        });
        assertThrows(IllegalStateException.class, configuration::requireVersion11);
        assertTrue(configuration.violations().get(0).contains("database unavailable"));
    }

    @Test
    void failureIsRetriedButSuccessIsCachedUntilTheNextObservationOfTheSameObject() {
        AtomicInteger observations = new AtomicInteger();
        TaskAuthorityConfiguration configuration = TaskAuthorityConfiguration.of(() -> {
            // The first observation is unmigrated; a corrected deployment passes afterwards.
            return atomic(sharedStore(7L, 7L, observations.incrementAndGet() > 1), QueueReplication.NONE,
                    SHARED_WEBHOOKS);
        });
        assertThrows(IllegalStateException.class, configuration::requireVersion11);
        assertDoesNotThrow(configuration::requireVersion11);
        int afterPass = observations.get();
        assertDoesNotThrow(configuration::requireVersion11);
        assertEquals(afterPass, observations.get(), "a passing result is cached");
    }

    @Test
    void validatingResolverChecksOnlyRegistrationsThatIncludeVersion11() {
        TaskAuthorityConfiguration failing = TaskAuthorityConfiguration.of(new Topology(false,
                TaskAuthorityCapabilities.UNDECLARED, QueueReplication.NONE, WebhookDispatch.NONE, "legacy"));
        InterfaceRegistration legacyOnly = new InterfaceRegistration(TransportProtocol.JSONRPC, "http://h", null,
                Set.of("0.3", "1.0"), false);
        InterfaceRegistration withV11 = new InterfaceRegistration(TransportProtocol.JSONRPC, "http://h", null,
                Set.of("1.0", "1.1"), true);

        assertSame(legacyOnly, failing.validating((binding, tenant) -> legacyOnly).resolve(TransportProtocol.JSONRPC, null));
        assertThrows(IllegalStateException.class,
                () -> failing.validating((binding, tenant) -> withV11).resolve(TransportProtocol.JSONRPC, null));
        assertEquals(null, failing.validating((binding, tenant) -> null).resolve(TransportProtocol.JSONRPC, null));
    }

    @Test
    void validatingResolverDoesNotNarrowARejectedRegistration() {
        TaskAuthorityConfiguration failing = TaskAuthorityConfiguration.of(new Topology(false,
                TaskAuthorityCapabilities.UNDECLARED, QueueReplication.NONE, WebhookDispatch.NONE, "legacy"));
        InterfaceRegistration withV11 = new InterfaceRegistration(TransportProtocol.HTTP_JSON, "http://h", "t1",
                Set.of("1.0", "1.1"), true);
        // The rejected registration is reported; no registration with {1.0} is ever returned instead.
        assertThrows(IllegalStateException.class,
                () -> failing.validating((binding, tenant) -> withV11).resolve(TransportProtocol.HTTP_JSON, "t1"));
        assertEquals(Set.of("1.0", "1.1"), withV11.versions());
    }

    @Test
    void inMemoryStoreDeclaresAProcessLocalAuthority() {
        assertEquals(TaskAuthorityCapabilities.Scope.PROCESS_LOCAL,
                new InMemoryTaskStore().authorityCapabilities().scope());
    }

    @Test
    void customAtomicStoreIsUndeclaredUnlessItOptsIn() {
        AtomicTaskStore optedOut = new DelegatingAtomicStore(new InMemoryTaskStore(), false);
        AtomicTaskStore optedIn = new DelegatingAtomicStore(new InMemoryTaskStore(), true);
        assertEquals(TaskAuthorityCapabilities.Scope.UNDECLARED, optedOut.authorityCapabilities().scope());
        assertEquals(TaskAuthorityCapabilities.Scope.PROCESS_LOCAL, optedIn.authorityCapabilities().scope());
    }

    @Test
    void liveTopologyFromStoreQueueManagerAndBusProcessorBacksSingleProcessMemory() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queues);
        TaskAuthorityConfiguration configuration = TaskAuthorityConfiguration.of(store, queues,
                processor::webhookDispatchTopology);
        assertEquals(List.of(), configuration.violations());
        assertEquals(WebhookDispatch.NONE, processor.webhookDispatchTopology());
    }

    @Test
    void liveTopologyFromLegacyOnlyCustomStoreIsRejectedFor11() {
        LegacyOnlyStore store = new LegacyOnlyStore();
        MainEventBus bus = new MainEventBus();
        QueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queues);
        TaskAuthorityConfiguration configuration = TaskAuthorityConfiguration.of(store, queues,
                processor::webhookDispatchTopology);
        List<String> violations = configuration.violations();
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains(LegacyOnlyStore.class.getName()), violations.get(0));
        // The unexposed 0.3/1.0 path never evaluates the gate.
        InterfaceRegistration legacyOnly = new InterfaceRegistration(TransportProtocol.JSONRPC, "http://h", null,
                Set.of("0.3", "1.0"), false);
        assertSame(legacyOnly, configuration.validating((binding, tenant) -> legacyOnly)
                .resolve(TransportProtocol.JSONRPC, null));
    }

    @Test
    void dispatcherTopologyReportsGateAndSenderFacts() {
        InMemoryWebhookDispatchGate local = new InMemoryWebhookDispatchGate();
        OrderedWebhookDispatcher withBase = new OrderedWebhookDispatcher(local, new FixedOrderedSender(true));
        try {
            assertEquals(new WebhookDispatch(true, true, false), withBase.topology());
        } finally {
            withBase.close();
        }
        OrderedWebhookDispatcher withoutQuiescence = new OrderedWebhookDispatcher(local, new FixedOrderedSender(false));
        try {
            assertEquals(new WebhookDispatch(true, false, false), withoutQuiescence.topology());
        } finally {
            withoutQuiescence.close();
        }
    }

    @Test
    void sharedGateWithoutQuiescentSenderCannotEvenBeBuilt() {
        // The existing T11 constructor already rejects this; topology validation never sees such a dispatcher.
        assertThrows(IllegalStateException.class,
                () -> new OrderedWebhookDispatcher(new SharedGate(), new FixedOrderedSender(false)));
    }

    @Test
    void handlerExposesItsTopologyForPlainJavaWiring() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queues);
        org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler handler =
                org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler.builder()
                        .agentExecutor((context, emitter) -> { })
                        .taskStore(store)
                        .queueManager(queues)
                        .mainEventBusProcessor(processor)
                        .executor(Runnable::run)
                        .eventConsumerExecutor(Runnable::run)
                        .build();
        assertEquals(List.of(), handler.taskAuthorityConfiguration().violations());
    }

    @Test
    void liveSharedStoreWithTheFourArgumentBusProcessorIsRejected() {
        InMemoryTaskStore memory = new InMemoryTaskStore();
        DelegatingAtomicStore store = new DelegatingAtomicStore(memory, sharedStore(7L, 7L, true));
        MainEventBus bus = new MainEventBus();
        QueueManager queues = new InMemoryQueueManager(memory, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queues);
        List<String> violations = TaskAuthorityConfiguration.of(store, queues, processor::webhookDispatchTopology)
                .violations();
        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("push"), violations.get(0));
    }

    @Test
    void liveSharedStoreWithASharedGateDispatcherAndQuiescentSenderPasses() {
        InMemoryTaskStore memory = new InMemoryTaskStore();
        DelegatingAtomicStore store = new DelegatingAtomicStore(memory, sharedStore(7L, 7L, true));
        MainEventBus bus = new MainEventBus();
        QueueManager queues = new InMemoryQueueManager(memory, bus);
        PushNotificationSender sender = new FixedOrderedSender(true);
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(new SharedGate(), sender);
        try {
            MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, sender, queues, dispatcher);
            assertEquals(new WebhookDispatch(true, true, true), processor.webhookDispatchTopology());
            assertEquals(List.of(), TaskAuthorityConfiguration.of(store, queues, processor::webhookDispatchTopology)
                    .violations());
        } finally {
            dispatcher.close();
        }
    }

    @Test
    void liveSharedStoreWithAProcessLocalGateDispatcherIsRejected() {
        InMemoryTaskStore memory = new InMemoryTaskStore();
        DelegatingAtomicStore store = new DelegatingAtomicStore(memory, sharedStore(7L, 7L, true));
        MainEventBus bus = new MainEventBus();
        QueueManager queues = new InMemoryQueueManager(memory, bus);
        PushNotificationSender sender = new FixedOrderedSender(true);
        OrderedWebhookDispatcher dispatcher = new OrderedWebhookDispatcher(new InMemoryWebhookDispatchGate(), sender);
        try {
            MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, sender, queues, dispatcher);
            List<String> violations = TaskAuthorityConfiguration.of(store, queues, processor::webhookDispatchTopology)
                    .violations();
            assertEquals(1, violations.size(), violations.toString());
            assertTrue(violations.get(0).contains("gate"), violations.get(0));
        } finally {
            dispatcher.close();
        }
    }

    private static final class FixedOrderedSender implements PushNotificationSender {

        private final boolean quiescent;

        FixedOrderedSender(boolean quiescent) {
            this.quiescent = quiescent;
        }

        @Override
        public boolean sendNotification(org.a2aproject.sdk.spec.StreamingEventKind event, Task taskSnapshot) {
            return true;
        }

        @Override
        public boolean supportsOrderedDispatch() {
            return quiescent;
        }
    }

    private static final class SharedGate implements WebhookDispatchLaneStore {

        private final InMemoryWebhookDispatchGate delegate = new InMemoryWebhookDispatchGate();

        @Override
        public boolean isSharedStorage() {
            return true;
        }

        @Override
        public boolean initializeTaskLane(String taskId, long writerEpoch, long nextGeneration) {
            return delegate.initializeTaskLane(taskId, writerEpoch, nextGeneration);
        }

        @Override
        public java.util.Optional<WebhookDispatchLaneState> readLane(String taskId) {
            return delegate.readLane(taskId);
        }

        @Override
        public java.util.Optional<WebhookDispatchClaim> claim(String taskId, long writerEpoch, long first, long last) {
            return delegate.claim(taskId, writerEpoch, first, last);
        }

        @Override
        public boolean complete(WebhookDispatchClaim claim) {
            return delegate.complete(claim);
        }

        @Override
        public boolean retireUnclaimedGap(String taskId, long writerEpoch, long expected, long through) {
            return delegate.retireUnclaimedGap(taskId, writerEpoch, expected, through);
        }

        @Override
        public java.util.Optional<WebhookDispatchNoopClaim> claimNoop(String taskId, long writerEpoch, long expected) {
            return delegate.claimNoop(taskId, writerEpoch, expected);
        }

        @Override
        public boolean releaseNoop(WebhookDispatchNoopClaim claim) {
            return delegate.releaseNoop(claim);
        }

        @Override
        public boolean completeWithLoss(WebhookDispatchClaim claim, String reason) {
            return delegate.completeWithLoss(claim, reason);
        }

        @Override
        public boolean recordAdmissionLoss(String taskId, long writerEpoch, long first, long last, String reason) {
            return delegate.recordAdmissionLoss(taskId, writerEpoch, first, last, reason);
        }

        @Override
        public boolean rebaseUnclaimedLane(String taskId, long expectedWriterEpoch, long newWriterEpoch) {
            return delegate.rebaseUnclaimedLane(taskId, expectedWriterEpoch, newWriterEpoch);
        }

        @Override
        public boolean recoverQuiescedClaim(WebhookDispatchClaim abandonedClaim, long newWriterEpoch) {
            return delegate.recoverQuiescedClaim(abandonedClaim, newWriterEpoch);
        }

        @Override
        public boolean recoverQuiescedNoop(WebhookDispatchNoopClaim abandonedClaim, long newWriterEpoch) {
            return delegate.recoverQuiescedNoop(abandonedClaim, newWriterEpoch);
        }
    }

    /** A legacy-only custom store: no atomic companion. */
    private static final class LegacyOnlyStore implements TaskStore, TaskStateProvider {

        @Override
        public void save(Task task, boolean isReplicated) {
        }

        @Override
        public Task get(String taskId) {
            return null;
        }

        @Override
        public void delete(String taskId) {
        }

        @Override
        public org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult list(ListTasksParams params,
                ServerCallContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTaskActive(String taskId) {
            return false;
        }

        @Override
        public boolean isTaskFinalized(String taskId) {
            return false;
        }
    }

    /** A custom atomic store that either opts in to the capability declaration or does not. */
    private static final class DelegatingAtomicStore implements AtomicTaskStore {

        private final InMemoryTaskStore delegate;
        private final boolean declare;
        private final TaskAuthorityCapabilities declared;

        DelegatingAtomicStore(InMemoryTaskStore delegate, boolean declare) {
            this.delegate = delegate;
            this.declare = declare;
            this.declared = null;
        }

        DelegatingAtomicStore(InMemoryTaskStore delegate, TaskAuthorityCapabilities declared) {
            this.delegate = delegate;
            this.declare = true;
            this.declared = declared;
        }

        @Override
        public TaskAuthorityCapabilities authorityCapabilities() {
            if (declared != null) {
                return declared;
            }
            return declare ? TaskAuthorityCapabilities.processLocal() : AtomicTaskStore.super.authorityCapabilities();
        }

        @Override
        public TaskSnapshot readSnapshot(String taskId) {
            return delegate.readSnapshot(taskId);
        }

        @Override
        public CommittedMutation mutate(String taskId, TaskMutation mutation) {
            return delegate.mutate(taskId, mutation);
        }

        @Override
        public CommittedMutation mutate(String taskId, TaskMutation mutation, TaskCommitSlotCallback slotCallback) {
            return delegate.mutate(taskId, mutation, slotCallback);
        }

        @Override
        public void save(Task task, boolean isReplicated) {
            delegate.save(task, isReplicated);
        }

        @Override
        public Task get(String taskId) {
            return delegate.get(taskId);
        }

        @Override
        public void delete(String taskId) {
            delegate.delete(taskId);
        }

        @Override
        public org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult list(ListTasksParams params,
                ServerCallContext context) {
            return delegate.list(params, context);
        }
    }
}
