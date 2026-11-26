package org.a2aproject.sdk.server.requesthandlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.events.EventQueueUtil;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.PublicationReconciler;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Provisioning of the opt-in publication recovery by {@link DefaultRequestHandler#initConfig()}: disabled unless
 * {@code a2a.publication-recovery.enabled=true}, installed only for an {@code AtomicTaskStore}, bounded by three
 * optional positive keys, closed with the handler, and never replacing a builder-installed reconciler.
 */
class PublicationRecoveryProvisioningTest {

    private static final String ENABLED = "a2a.publication-recovery.enabled";
    private static final String INTERVAL = "a2a.publication-recovery.interval.millis";
    private static final String READ_DEADLINE = "a2a.publication-recovery.read-deadline.millis";
    private static final String GAP_DEADLINE = "a2a.publication-recovery.gap-deadline.millis";

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final MainEventBus bus = new MainEventBus();
    private MainEventBusProcessor processor;

    @AfterEach
    void stop() {
        if (processor != null) {
            EventQueueUtil.stop(processor);
        }
        executor.shutdownNow();
    }

    @Test
    void shippedDefaultsDisablePublicationRecovery() {
        Map<String, String> shipped = shippedDefaults();
        assertEquals("false", shipped.get(ENABLED));

        DefaultRequestHandler handler = initialised(new InMemoryTaskStore(), shipped);

        assertNull(handler.publicationReconciler(), "no reconciler without an explicit opt-in");
    }

    @Test
    void anAbsentKeyAndAnExplicitFalseKeepRecoveryOffEvenWhenTheBoundsAreSet() {
        Map<String, String> absent = shippedDefaults();
        absent.remove(ENABLED);
        absent.put(INTERVAL, "not-a-number");
        assertNull(initialised(new InMemoryTaskStore(), absent).publicationReconciler());

        Map<String, String> disabled = shippedDefaults();
        disabled.put(ENABLED, "false");
        disabled.put(INTERVAL, "-5");
        assertNull(initialised(new InMemoryTaskStore(), disabled).publicationReconciler(),
                "the bounds are read only when the feature is enabled");
    }

    @Test
    void aProviderThatReturnsNoOptionalLeavesRecoveryOff() {
        A2AConfigProvider provider = new MapProvider(shippedDefaults()) {
            @Override
            public Optional<String> getOptionalValue(String name) {
                return null;
            }
        };
        assertNull(initialised(new InMemoryTaskStore(), provider).publicationReconciler());
    }

    @Test
    void enablingInstallsTheBoundedReconcilerForAnAtomicStoreWithTheDocumentedDefaultBounds() {
        Map<String, String> config = shippedDefaults();
        config.put(ENABLED, "true");

        DefaultRequestHandler handler = initialised(new InMemoryTaskStore(), config);

        assertNotNull(handler.publicationReconciler());
        assertEquals(PublicationReconciler.Limits.DEFAULTS, handler.publicationRecoveryLimits());
        assertEquals(Duration.ofSeconds(5), handler.publicationRecoveryLimits().reconciliationInterval());
        assertEquals(Duration.ofSeconds(5), handler.publicationRecoveryLimits().authorityReadDeadline());
        assertEquals(Duration.ofSeconds(10), handler.publicationRecoveryLimits().reorderGapDeadline());
        assertEquals(256, handler.publicationRecoveryLimits().maxBufferedEvents());
        assertEquals(4L * 1024 * 1024, handler.publicationRecoveryLimits().maxBufferedBytes());
        handler.closePublicationReconciler();
    }

    @Test
    void theValueIsCaseAndWhitespaceTolerantButOnlyTrueOrFalseIsAccepted() {
        for (String value : List.of("TRUE", " True ")) {
            Map<String, String> config = shippedDefaults();
            config.put(ENABLED, value);
            DefaultRequestHandler handler = initialised(new InMemoryTaskStore(), config);
            assertNotNull(handler.publicationReconciler(), value);
            handler.closePublicationReconciler();
        }
        for (String value : List.of("yes", "1", "ture", "", "enabled")) {
            Map<String, String> config = shippedDefaults();
            config.put(ENABLED, value);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> initialised(new InMemoryTaskStore(), config), "value '" + value + "'");
            assertTrue(failure.getMessage().contains(ENABLED), failure.getMessage());
        }
    }

    @Test
    void everyBoundKeyOverridesOnlyItsOwnBoundAndEachIsIndependent() {
        Map<String, String> config = shippedDefaults();
        config.put(ENABLED, "true");

        config.put(INTERVAL, "250");
        PublicationReconciler.Limits limits = initialised(new InMemoryTaskStore(), config).publicationRecoveryLimits();
        assertEquals(Duration.ofMillis(250), limits.reconciliationInterval());
        assertEquals(Duration.ofSeconds(5), limits.authorityReadDeadline());
        assertEquals(Duration.ofSeconds(10), limits.reorderGapDeadline());

        config.remove(INTERVAL);
        config.put(READ_DEADLINE, "1500");
        limits = initialised(new InMemoryTaskStore(), config).publicationRecoveryLimits();
        assertEquals(Duration.ofSeconds(5), limits.reconciliationInterval());
        assertEquals(Duration.ofMillis(1500), limits.authorityReadDeadline());
        assertEquals(Duration.ofSeconds(10), limits.reorderGapDeadline());

        config.remove(READ_DEADLINE);
        config.put(GAP_DEADLINE, "3000");
        limits = initialised(new InMemoryTaskStore(), config).publicationRecoveryLimits();
        assertEquals(Duration.ofSeconds(5), limits.reconciliationInterval());
        assertEquals(Duration.ofSeconds(5), limits.authorityReadDeadline());
        assertEquals(Duration.ofMillis(3000), limits.reorderGapDeadline());

        config.put(INTERVAL, "200");
        config.put(READ_DEADLINE, "1000");
        limits = initialised(new InMemoryTaskStore(), config).publicationRecoveryLimits();
        assertEquals(Duration.ofMillis(200), limits.reconciliationInterval());
        assertEquals(Duration.ofMillis(1000), limits.authorityReadDeadline());
        assertEquals(Duration.ofMillis(3000), limits.reorderGapDeadline());
        assertEquals(256, limits.maxBufferedEvents(), "the buffer bounds are not configurable");
    }

    @Test
    void anInvalidBoundFailsTheEnabledConfigurationInsteadOfFallingBackSilently() {
        for (String key : List.of(INTERVAL, READ_DEADLINE, GAP_DEADLINE)) {
            for (String bad : List.of("abc", "1.5", "")) {
                Map<String, String> config = shippedDefaults();
                config.put(ENABLED, "true");
                config.put(key, bad);
                assertThrows(NumberFormatException.class, () -> initialised(new InMemoryTaskStore(), config),
                        key + "=" + bad);
            }
            for (String bad : List.of("0", "-1")) {
                Map<String, String> config = shippedDefaults();
                config.put(ENABLED, "true");
                config.put(key, bad);
                assertThrows(IllegalArgumentException.class, () -> initialised(new InMemoryTaskStore(), config),
                        key + "=" + bad);
            }
        }
    }

    @Test
    void enablingWithAStoreThatIsNotAtomicInstallsNothingAndDoesNotFail() {
        Map<String, String> config = shippedDefaults();
        config.put(ENABLED, "true");
        TaskStore decorated = new LegacyOnlyStore(new InMemoryTaskStore());

        DefaultRequestHandler handler = initialised(decorated, config);

        assertNull(handler.publicationReconciler(),
                "a store without an authoritative generation cannot be covered, and no coverage is claimed");
        handler.closePublicationReconciler();
    }

    @Test
    void aBuilderInstalledReconcilerIsKeptWhetherOrNotThePropertyIsEnabled() {
        for (String enabled : List.of("true", "false")) {
            InMemoryTaskStore store = new InMemoryTaskStore();
            PublicationReconciler own = PublicationReconciler.forTaskStore(store);
            try {
                Map<String, String> config = shippedDefaults();
                config.put(ENABLED, enabled);
                DefaultRequestHandler handler = initialised(store, config, own);
                assertSame(own, handler.publicationReconciler(), "enabled=" + enabled);
            } finally {
                own.close();
            }
        }
    }

    @Test
    void preDestroyClosesTheInstalledReconcilerAndIsHarmlessWhenNoneIsInstalled() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.save(Task.builder().id("t").contextId("c").status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .generation(1).history(List.of()).artifacts(List.of()).build(), false);
        Map<String, String> config = shippedDefaults();
        config.put(ENABLED, "true");
        config.put("a2a.authorization.required", "false");   // initConfig re-reads it from configuration
        DefaultRequestHandler handler = initialised(store, config);
        ServerCallContext context = new ServerCallContext(new TestUser(), Map.of(), Set.of());

        handler.closePublicationReconciler();
        handler.closePublicationReconciler();   // idempotent

        assertThrows(IllegalStateException.class,
                () -> handler.onSubscribeToTask(new TaskIdParams("t"), context),
                "a closed reconciler rejects new observations");

        DefaultRequestHandler off = initialised(new InMemoryTaskStore(), shippedDefaults());
        off.closePublicationReconciler();
    }

    // ---- fixtures ----

    private DefaultRequestHandler initialised(TaskStore store, Map<String, String> config) {
        return initialised(store, new MapProvider(config), null);
    }

    private DefaultRequestHandler initialised(TaskStore store, A2AConfigProvider provider) {
        return initialised(store, provider, null);
    }

    private DefaultRequestHandler initialised(TaskStore store, Map<String, String> config,
            PublicationReconciler builderReconciler) {
        return initialised(store, new MapProvider(config), builderReconciler);
    }

    private DefaultRequestHandler initialised(TaskStore store, A2AConfigProvider provider,
            PublicationReconciler builderReconciler) {
        if (processor != null) {
            EventQueueUtil.stop(processor);
        }
        InMemoryQueueManager queueManager = new InMemoryQueueManager(
                store instanceof InMemoryTaskStore memory ? memory : new InMemoryTaskStore(), bus);
        processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queueManager);
        DefaultRequestHandler handler = DefaultRequestHandler.builder()
                .agentExecutor(new NoopExecutor())
                .taskStore(store)
                .queueManager(queueManager)
                .pushConfigStore(new InMemoryPushNotificationConfigStore())
                .mainEventBusProcessor(processor)
                .executor(executor)
                .eventConsumerExecutor(executor)
                .authorizationRequired(false)
                .publicationReconciler(builderReconciler)
                .build();
        handler.configProvider = provider;
        handler.initConfig();
        return handler;
    }

    private static Map<String, String> shippedDefaults() {
        Properties properties = new Properties();
        try (InputStream in = PublicationRecoveryProvisioningTest.class.getClassLoader()
                .getResourceAsStream("META-INF/a2a-defaults.properties")) {
            assertNotNull(in, "the shipped defaults resource must be on the classpath");
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Map<String, String> values = new HashMap<>();
        properties.stringPropertyNames().forEach(name -> values.put(name, properties.getProperty(name)));
        return values;
    }

    private static class MapProvider implements A2AConfigProvider {

        private final Map<String, String> values;

        MapProvider(Map<String, String> values) {
            this.values = new HashMap<>(values);
        }

        @Override
        public String getValue(String name) {
            String value = values.get(name);
            if (value == null) {
                throw new IllegalArgumentException("No configuration value for " + name);
            }
            return value;
        }

        @Override
        public Optional<String> getOptionalValue(String name) {
            return Optional.ofNullable(values.get(name));
        }
    }

    private static final class NoopExecutor implements AgentExecutor {

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
        }
    }

    /** A decorator that forwards the legacy {@link TaskStore} contract only, as a wrapping store would. */
    private static final class LegacyOnlyStore implements TaskStore {

        private final TaskStore delegate;

        LegacyOnlyStore(TaskStore delegate) {
            this.delegate = delegate;
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
        public ListTasksResult list(ListTasksParams params, ServerCallContext context) {
            return delegate.list(params, context);
        }
    }

    private static final class TestUser implements User {

        @Override
        public boolean isAuthenticated() {
            return false;
        }

        @Override
        public String getUsername() {
            return "";
        }
    }
}
