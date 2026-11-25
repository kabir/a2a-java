package org.a2aproject.sdk.server.common.quarkus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.inject.Instance;

import org.a2aproject.sdk.server.FixedInstance;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskAuthorityCapabilities;
import org.a2aproject.sdk.server.tasks.TaskAuthorityCapabilities.QueueReplication;
import org.a2aproject.sdk.server.tasks.TaskAuthorityCapabilities.WebhookDispatch;
import org.a2aproject.sdk.server.tasks.TaskAuthorityConfiguration;
import org.a2aproject.sdk.server.tasks.TaskAuthorityConfiguration.Topology;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.server.version.InterfaceRegistrationResolver;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

/**
 * The reference wiring never exposes 1.1 when the actual task authority topology cannot back it, and never
 * changes a legacy-only registration or silently narrows a rejected one.
 */
class TaskAuthorityConfigurationTest {

    private static final Topology LEGACY_STORE = new Topology(false, TaskAuthorityCapabilities.UNDECLARED,
            QueueReplication.NONE, WebhookDispatch.NONE, "custom.LegacyStore");
    private static final Topology MEMORY = new Topology(true, TaskAuthorityCapabilities.processLocal(),
            QueueReplication.NONE, WebhookDispatch.NONE, "memory");
    private static final Topology REPLICATED_INDEPENDENT_MEMORY = new Topology(true,
            TaskAuthorityCapabilities.processLocal(), new QueueReplication(true, 7L, true), WebhookDispatch.NONE,
            "memory");
    private static final Topology UNMIGRATED_SHARED = new Topology(true,
            TaskAuthorityCapabilities.sharedStorage(null, 7L, false), QueueReplication.NONE,
            new WebhookDispatch(true, true, true), "jpa");
    private static final Topology FENCED_SHARED = new Topology(true,
            TaskAuthorityCapabilities.sharedStorage(7L, 7L, true), new QueueReplication(true, 7L, true),
            new WebhookDispatch(true, true, true), "jpa");

    private static InterfaceRegistration registration(TransportProtocol binding, String tenant, String... versions) {
        Set<String> set = Set.of(versions);
        return new InterfaceRegistration(binding, "https://example.test", tenant, set, set.contains("1.1"));
    }

    private static Instance<TaskAuthorityConfiguration> authority(Topology topology) {
        return new FixedInstance<>(TaskAuthorityConfiguration.of(topology));
    }

    private static Instance<InterfaceRegistrationResolver> resolver(InterfaceRegistration registration) {
        InterfaceRegistrationResolver resolver = (binding, tenant) -> registration;
        return new FixedInstance<>(resolver);
    }

    private static ServerCallContext context(String version) {
        return new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of(), version);
    }

    @Test
    void supportedProfilesActivateAndServe11() {
        for (Topology supported : List.of(MEMORY, FENCED_SHARED)) {
            for (TransportProtocol binding : TransportProtocol.values()) {
                InterfaceRegistration registration = registration(binding, null, "1.0", "1.1");
                assertDoesNotThrow(() -> VersionRouter.validateDefaultRegistration(resolver(registration),
                        authority(supported), binding));
                ServerCallContext context = context("1.1");
                assertDoesNotThrow(() -> VersionRouter.attachRegistration(context, resolver(registration),
                        authority(supported), binding, null, "1.1"));
                assertSame(registration, context.getInterfaceRegistration());
            }
        }
    }

    @Test
    void rejectedTopologiesFailActivationBeforeServingOrAdvertising11() {
        for (Topology rejected : List.of(LEGACY_STORE, REPLICATED_INDEPENDENT_MEMORY, UNMIGRATED_SHARED)) {
            for (TransportProtocol binding : TransportProtocol.values()) {
                InterfaceRegistration registration = registration(binding, null, "1.1");
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                        () -> VersionRouter.validateDefaultRegistration(resolver(registration), authority(rejected),
                                binding));
                assertTrue(failure.getMessage().contains("A2A 1.1 cannot be exposed"), failure.getMessage());
            }
        }
    }

    @Test
    void rejectedTopologyFailsEveryRequestAndNeverAttachesANarrowedRegistration() {
        InterfaceRegistration registration = registration(TransportProtocol.JSONRPC, null, "1.0", "1.1");
        ServerCallContext requestFor11 = context("1.1");
        assertThrows(IllegalStateException.class, () -> VersionRouter.attachRegistration(requestFor11,
                resolver(registration), authority(LEGACY_STORE), TransportProtocol.JSONRPC, null, "1.1"));
        assertNull(requestFor11.getInterfaceRegistration());

        // An explicit 1.0 request against the same rejected registration is also refused: the registration is
        // rejected as a whole, never silently served as {1.0}.
        ServerCallContext requestFor10 = context("1.0");
        assertThrows(IllegalStateException.class, () -> VersionRouter.attachRegistration(requestFor10,
                resolver(registration), authority(LEGACY_STORE), TransportProtocol.JSONRPC, null, "1.0"));
        assertNull(requestFor10.getInterfaceRegistration());
        assertEquals(Set.of("1.0", "1.1"), registration.versions());
    }

    @Test
    void effectiveTenantRegistrationIsCheckedPerRequest() {
        InterfaceRegistrationResolver resolver = (binding, tenant) -> tenant == null
                ? registration(binding, null, "1.0")
                : registration(binding, tenant, "1.0", "1.1");
        Instance<InterfaceRegistrationResolver> instance = new FixedInstance<>(resolver);
        // The default tenant is legacy-only 1.0: startup and requests pass even with a rejected topology.
        assertDoesNotThrow(() -> VersionRouter.validateDefaultRegistration(instance, authority(LEGACY_STORE),
                TransportProtocol.HTTP_JSON));
        assertDoesNotThrow(() -> VersionRouter.attachRegistration(context("1.0"), instance, authority(LEGACY_STORE),
                TransportProtocol.HTTP_JSON, null, "1.0"));
        // A dynamic tenant with 1.1 is checked when it is requested.
        assertThrows(IllegalStateException.class, () -> VersionRouter.attachRegistration(context("1.1"), instance,
                authority(LEGACY_STORE), TransportProtocol.HTTP_JSON, "acme", "1.1"));
        assertDoesNotThrow(() -> VersionRouter.attachRegistration(context("1.1"), instance, authority(MEMORY),
                TransportProtocol.HTTP_JSON, "acme", "1.1"));
    }

    @Test
    void legacyOnlyRegistrationsWithACustomLegacyStoreKeepWorking() {
        // A legacy-only {1.0} registration: the optional 0.3 adapter lives in another module (see VersionRouterTest).
        InterfaceRegistration registration = registration(TransportProtocol.JSONRPC, null, "1.0");
        assertDoesNotThrow(() -> VersionRouter.validateDefaultRegistration(resolver(registration),
                authority(LEGACY_STORE), TransportProtocol.JSONRPC));
        ServerCallContext context = context("1.0");
        assertDoesNotThrow(() -> VersionRouter.attachRegistration(context, resolver(registration),
                authority(LEGACY_STORE), TransportProtocol.JSONRPC, null, "1.0"));
        assertSame(registration, context.getInterfaceRegistration());
        // No registrations at all (historical entry points): a legacy store with 1.0 and 0.3 requests is untouched.
        for (String version : new String[] {null, "", "0.3", "1.0"}) {
            assertDoesNotThrow(() -> VersionRouter.attachRegistration(context(version),
                    FixedInstance.empty(), authority(LEGACY_STORE), TransportProtocol.JSONRPC, null, version));
        }
    }

    @Test
    void a11RequestWithoutAnyRegistrationCannotBypassTheGate() {
        assertThrows(IllegalStateException.class, () -> VersionRouter.attachRegistration(context("1.1"),
                FixedInstance.empty(), authority(LEGACY_STORE), TransportProtocol.JSONRPC, null, "1.1"));
        assertDoesNotThrow(() -> VersionRouter.attachRegistration(context("1.1"), FixedInstance.empty(),
                authority(MEMORY), TransportProtocol.JSONRPC, null, "1.1"));
    }

    @Test
    void anUnsatisfiedConfigurationBeanFailsClosedFor11AndIsIgnoredForLegacy() {
        InterfaceRegistration with11 = registration(TransportProtocol.GRPC, null, "1.1");
        InterfaceRegistration legacy = registration(TransportProtocol.GRPC, null, "1.0");
        Instance<TaskAuthorityConfiguration> missing = FixedInstance.empty();
        assertThrows(IllegalStateException.class,
                () -> VersionRouter.validateDefaultRegistration(resolver(with11), missing, TransportProtocol.GRPC));
        assertDoesNotThrow(
                () -> VersionRouter.validateDefaultRegistration(resolver(legacy), missing, TransportProtocol.GRPC));
        // A manually constructed route without CDI keeps its historical behavior.
        assertDoesNotThrow(() -> VersionRouter.validateDefaultRegistration(resolver(with11), null,
                TransportProtocol.GRPC));
    }

    @Test
    void gRpcPerTenantRegistrationRequiresTheAuthority() {
        InterfaceRegistration registration = registration(TransportProtocol.GRPC, "acme", "1.1");
        assertThrows(IllegalStateException.class,
                () -> VersionRouter.requireTaskAuthority(registration, authority(UNMIGRATED_SHARED)));
        assertDoesNotThrow(() -> VersionRouter.requireTaskAuthority(registration, authority(FENCED_SHARED)));
        assertDoesNotThrow(() -> VersionRouter.requireTaskAuthority(null, authority(LEGACY_STORE)));
    }

    @Test
    void requestForVersion11WithoutRegistrationIsGatedAndOtherVersionsAreNot() {
        assertThrows(IllegalStateException.class,
                () -> VersionRouter.requireTaskAuthorityForVersion("1.1", authority(LEGACY_STORE)));
        assertDoesNotThrow(() -> VersionRouter.requireTaskAuthorityForVersion("1.1", authority(MEMORY)));
        for (String version : new String[] {null, "", "0.3", "1.0"}) {
            assertDoesNotThrow(
                    () -> VersionRouter.requireTaskAuthorityForVersion(version, authority(LEGACY_STORE)));
        }
        assertDoesNotThrow(() -> VersionRouter.requireTaskAuthorityForVersion("1.1", null));
        assertThrows(IllegalStateException.class,
                () -> VersionRouter.requireTaskAuthorityForVersion("1.1", FixedInstance.empty()));
    }

    @Test
    void agentCardExposureIsGatedOnlyForRegistrationsThatInclude11() {
        InterfaceRegistration with11 = registration(TransportProtocol.HTTP_JSON, "acme", "1.0", "1.1");
        InterfaceRegistration legacy = registration(TransportProtocol.HTTP_JSON, "acme", "1.0");
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> VersionRouter.requireTaskAuthorityForCard(resolver(with11), authority(UNMIGRATED_SHARED),
                        TransportProtocol.HTTP_JSON, "acme"));
        assertTrue(rejected.getMessage().contains("unmigrated and unfenced"), rejected.getMessage());
        assertDoesNotThrow(() -> VersionRouter.requireTaskAuthorityForCard(resolver(with11),
                authority(FENCED_SHARED), TransportProtocol.HTTP_JSON, "acme"));
        // A legacy-only registration, no resolver, or no registration keeps the historical card behaviour.
        assertDoesNotThrow(() -> VersionRouter.requireTaskAuthorityForCard(resolver(legacy),
                authority(LEGACY_STORE), TransportProtocol.HTTP_JSON, "acme"));
        assertDoesNotThrow(() -> VersionRouter.requireTaskAuthorityForCard(FixedInstance.empty(),
                authority(LEGACY_STORE), TransportProtocol.HTTP_JSON, "acme"));
        assertDoesNotThrow(() -> VersionRouter.requireTaskAuthorityForCard(null, authority(LEGACY_STORE),
                TransportProtocol.HTTP_JSON, null));
        // A registration for another binding or tenant is not this card's registration and is not gated.
        assertDoesNotThrow(() -> VersionRouter.requireTaskAuthorityForCard(resolver(with11),
                authority(UNMIGRATED_SHARED), TransportProtocol.JSONRPC, "acme"));
        assertDoesNotThrow(() -> VersionRouter.requireTaskAuthorityForCard(resolver(with11),
                authority(UNMIGRATED_SHARED), TransportProtocol.HTTP_JSON, "other"));
        assertDoesNotThrow(() -> VersionRouter.requireTaskAuthorityForCard(resolver(null),
                authority(LEGACY_STORE), TransportProtocol.HTTP_JSON, null));
    }

    @Test
    void defaultProducerObservesTheLiveStoreQueueManagerAndBus() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queues);

        TaskAuthorityConfiguration configuration =
                new DefaultProducers().createTaskAuthorityConfiguration(store, queues, processor);
        assertNotNull(configuration);
        assertEquals(List.of(), configuration.violations());

        // The same producer rejects a legacy-only store for 1.1 but leaves 0.3/1.0 registrations alone.
        TaskAuthorityConfiguration legacy = new DefaultProducers().createTaskAuthorityConfiguration(
                new LegacyStore(), queues, processor);
        assertEquals(1, legacy.violations().size());
        assertThrows(IllegalStateException.class, legacy::requireVersion11);
        InterfaceRegistration legacyOnly = registration(TransportProtocol.JSONRPC, null, "1.0");
        assertSame(legacyOnly, legacy.validating((binding, tenant) -> legacyOnly).resolve(TransportProtocol.JSONRPC, null));
    }

    private static final class LegacyStore implements org.a2aproject.sdk.server.tasks.TaskStore {

        @Override
        public void save(org.a2aproject.sdk.spec.Task task, boolean isReplicated) {
        }

        @Override
        public org.a2aproject.sdk.spec.Task get(String taskId) {
            return null;
        }

        @Override
        public void delete(String taskId) {
        }

        @Override
        public org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult list(
                org.a2aproject.sdk.spec.ListTasksParams params, ServerCallContext context) {
            throw new UnsupportedOperationException();
        }
    }
}
