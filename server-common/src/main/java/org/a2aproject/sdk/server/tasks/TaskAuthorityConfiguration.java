package org.a2aproject.sdk.server.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.tasks.TaskAuthorityCapabilities.QueueReplication;
import org.a2aproject.sdk.server.tasks.TaskAuthorityCapabilities.Scope;
import org.a2aproject.sdk.server.tasks.TaskAuthorityCapabilities.WebhookDispatch;
import org.a2aproject.sdk.server.version.InterfaceRegistration;
import org.a2aproject.sdk.server.version.InterfaceRegistrationResolver;
import org.a2aproject.sdk.util.Assert;

/**
 * Validates the actual task-authority topology of one node before A2A 1.1 is served or advertised.
 *
 * <p>This is configuration validation only. A passing check means the declared pieces are consistent; it is not
 * evidence that a cluster profile passed multi-instance verification, and it does not fence old database or broker
 * writers (those remain documented deployment preconditions). Legacy-only registrations are never checked here.</p>
 *
 * <p>The check relies on the store, queue manager and webhook dispatcher declaring their topology truthfully; a
 * component that does not declare it (for example a custom {@link QueueManager} that replicates without reporting it)
 * cannot be detected.</p>
 *
 * <p>A failed result is re-evaluated on each call so an operator can correct the deployment; a passing result is
 * cached for the life of this object. Shared-storage epochs only increase, and a node whose epoch later becomes
 * stale is refused by the store itself on every write.</p>
 */
public final class TaskAuthorityConfiguration {

    /** One observation of the topology. */
    public record Topology(boolean atomicStore, TaskAuthorityCapabilities store, QueueReplication replication,
            WebhookDispatch webhooks, String storeName) {
        public Topology {
            Assert.checkNotNullParam("store", store);
            Assert.checkNotNullParam("replication", replication);
            Assert.checkNotNullParam("webhooks", webhooks);
            Assert.checkNotNullParam("storeName", storeName);
        }
    }

    private final Supplier<Topology> topology;
    private volatile boolean validated;

    private TaskAuthorityConfiguration(Supplier<Topology> topology) {
        this.topology = topology;
    }

    /** Observes a fixed topology. */
    public static TaskAuthorityConfiguration of(Topology topology) {
        Assert.checkNotNullParam("topology", topology);
        return new TaskAuthorityConfiguration(() -> topology);
    }

    /** Observes the live topology each time it is evaluated. */
    public static TaskAuthorityConfiguration of(Supplier<Topology> topology) {
        return new TaskAuthorityConfiguration(Assert.checkNotNullParam("topology", topology));
    }

    /** Observes a task store, queue manager and webhook wiring; a legacy-only store reports no atomic companion. */
    public static TaskAuthorityConfiguration of(TaskStore taskStore, QueueManager queueManager,
            Supplier<WebhookDispatch> webhooks) {
        Assert.checkNotNullParam("taskStore", taskStore);
        Assert.checkNotNullParam("queueManager", queueManager);
        Assert.checkNotNullParam("webhooks", webhooks);
        return new TaskAuthorityConfiguration(() -> new Topology(
                taskStore instanceof AtomicTaskStore,
                taskStore instanceof AtomicTaskStore atomic ? atomic.authorityCapabilities()
                        : TaskAuthorityCapabilities.UNDECLARED,
                queueManager.replication(), webhooks.get(), taskStore.getClass().getName()));
    }

    /** Every reason this topology cannot expose 1.1; empty when the declared topology is consistent. */
    public List<String> violations() {
        Topology observed;
        try {
            observed = topology.get();
        } catch (RuntimeException failure) {
            return List.of("The task authority topology could not be determined: " + failure);
        }
        List<String> violations = new ArrayList<>();
        TaskAuthorityCapabilities store = observed.store();
        QueueReplication replication = observed.replication();
        WebhookDispatch webhooks = observed.webhooks();
        if (!observed.atomicStore()) {
            violations.add("TaskStore " + observed.storeName() + " has no AtomicTaskStore companion; a separate "
                    + "get/save pair cannot provide the atomic mutation authority required by 1.1");
        } else if (store.scope() == Scope.UNDECLARED) {
            violations.add("TaskStore " + observed.storeName() + " does not declare its authority; override "
                    + "AtomicTaskStore.authorityCapabilities()");
        }
        if (observed.atomicStore() && store.scope() == Scope.PROCESS_LOCAL) {
            checkProcessLocal(violations, replication, webhooks);
        } else if (observed.atomicStore() && store.scope() == Scope.SHARED_STORAGE) {
            checkSharedStorage(violations, store, replication, webhooks);
        }
        return List.copyOf(violations);
    }

    private static void checkProcessLocal(List<String> violations, QueueReplication replication,
            WebhookDispatch webhooks) {
        if (replication.replicated()) {
            violations.add("Replicated queues with a process-local task store: each node would hold an independent "
                    + "task authority; use a shared transactional store with a configured writer epoch");
        }
        if (webhooks.sharedGate()) {
            violations.add("A shared webhook gate is configured with a process-local task store; the gate and "
                    + "the task store must have the same authority");
        }
    }

    private static void checkSharedStorage(List<String> violations, TaskAuthorityCapabilities store,
            QueueReplication replication, WebhookDispatch webhooks) {
        Long storageEpoch = store.storageEpoch();
        if (storageEpoch == null) {
            violations.add("The shared task store is unmigrated and unfenced: no writer epoch is active; fence old "
                    + "writers at the database, activate an epoch and migrate before exposing 1.1");
        } else {
            if (!Objects.equals(store.nodeEpoch(), storageEpoch)) {
                violations.add("This node's writer epoch " + store.nodeEpoch() + " does not match the active "
                        + "storage epoch " + storageEpoch);
            } else if (!store.migrationComplete()) {
                violations.add("Shared task rows are not all baselined under writer epoch " + storageEpoch
                        + "; complete the migration before serving");
            }
        }
        if (replication.replicated()) {
            if (replication.writerEpoch() == null) {
                violations.add("The replicated queue manager has no writer epoch and replicates legacy per-event "
                        + "records that cannot carry committed batches");
            } else if (storageEpoch != null && !replication.writerEpoch().equals(storageEpoch)) {
                violations.add("The replicated queue manager writer epoch " + replication.writerEpoch()
                        + " does not match the active storage epoch " + storageEpoch);
            }
            if (!replication.committedEnvelopes()) {
                violations.add("The replication strategy cannot send committed envelopes "
                        + "(supportsCommittedReplication() is false)");
            }
        }
        if (!webhooks.orderedDispatcher()) {
            violations.add("Shared storage requires the shared ordered webhook dispatcher on this node's bus and "
                    + "finalization senders; independent push chains are not allowed");
        } else {
            if (!webhooks.sharedGate()) {
                violations.add("Shared storage requires the shared webhook gate, but a process-local gate is configured");
            }
            if (!webhooks.quiescentSender()) {
                violations.add("The push notification sender cannot bound and quiesce its dispatch work, "
                        + "so shared ordered dispatch is unavailable");
            }
        }
    }

    /**
     * Fails before 1.1 is served or advertised.
     *
     * @throws IllegalStateException with every violation when the topology cannot back 1.1
     */
    public void requireVersion11() {
        if (validated) {
            return;
        }
        List<String> violations = violations();
        if (!violations.isEmpty()) {
            throw new IllegalStateException("A2A 1.1 cannot be exposed with this task authority topology: "
                    + String.join("; ", violations) + ". Legacy-only 0.3/1.0 registrations are unaffected.");
        }
        validated = true;
    }

    /**
     * Returns a resolver that applies {@link #requireVersion11()} to every registration that includes 1.1,
     * for plain-Java wiring. Other registrations are returned unchanged.
     */
    public InterfaceRegistrationResolver validating(InterfaceRegistrationResolver delegate) {
        Assert.checkNotNullParam("delegate", delegate);
        return (binding, tenant) -> {
            InterfaceRegistration registration = delegate.resolve(binding, tenant);
            if (registration != null && registration.versions().contains("1.1")) {
                requireVersion11();
            }
            return registration;
        };
    }
}
