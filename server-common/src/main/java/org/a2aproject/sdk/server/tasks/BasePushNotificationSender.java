package org.a2aproject.sdk.server.tasks;

import static org.a2aproject.sdk.client.http.A2AHttpClient.APPLICATION_JSON;
import static org.a2aproject.sdk.client.http.A2AHttpClient.CONTENT_TYPE;
import static org.a2aproject.sdk.common.A2AHeaders.X_A2A_NOTIFICATION_TOKEN;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpClientConfiguration;
import org.a2aproject.sdk.client.http.A2AHttpClientFactory;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.Instrumented;
import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.a2aproject.sdk.server.version.ServerVersionPolicy;
import org.a2aproject.sdk.server.version.VersionedOutputProjector;
import org.a2aproject.sdk.spec.AuthenticationInfo;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class BasePushNotificationSender implements PushNotificationSender, OrderedWebhookSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasePushNotificationSender.class);
    public static final int DEFAULT_PAGE_SIZE = 100;

    /**
     * CDI {@link Named @Named} qualifier ({@value}) for an optional application-provided
     * {@link Executor} used to dispatch push notifications asynchronously.
     *
     * <p>Applications can produce a bean with this name to supply a custom thread pool
     * (e.g. for sizing or monitoring). When an instrumentation module (such as the
     * OpenTelemetry server extra) is present, it produces an
     * {@link Instrumented @Instrumented} {@link Executor} that wraps the application's
     * {@code @Named} executor (or a default pool) with context propagation.
     *
     * <p>Resolution order (see the {@link #BasePushNotificationSender CDI constructor}):
     * <ol>
     *   <li>{@link Instrumented @Instrumented} {@link Executor} (instrumented wrapper)</li>
     *   <li>{@code @Named("a2a.pushNotificationDispatch")} {@link Executor}
     *       (application-provided)</li>
     *   <li>{@link ForkJoinPool#commonPool()} as last resort</li>
     * </ol>
     */
    public static final String DISPATCH_EXECUTOR_NAME = "a2a.pushNotificationDispatch";

    // Fields set by constructor injection cannot be final. We need a noargs constructor for
    // Jakarta compatibility, and it seems that making fields set by constructor injection
    // final, is not proxyable in all runtimes
    private A2AHttpClient httpClient;
    private PushNotificationConfigStore configStore;
    private Map<String, PushNotificationPayloadFormatter> formattersByVersion;
    private PushNotificationUrlValidator urlValidator;
    private Executor dispatchExecutor = ForkJoinPool.commonPool();
    private Executor orderedDispatchExecutor;
    private final Semaphore orderedDispatchPermits = new Semaphore(1024);
    private final Semaphore orderedDispatchShellPermits = new Semaphore(1024);
    private final Set<OrderedDispatchAttempt> orderedDispatchAttempts = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean orderedDispatchClosed = new AtomicBoolean();
    private final ThreadPoolExecutor orderedDispatchWorkers = newOrderedWorkerPool();
    private final ThreadPoolExecutor orderedDispatchIngressWorkers = newOrderedIngressPool();
    private final ScheduledThreadPoolExecutor orderedDispatchDeadlines = newOrderedDeadlineScheduler();


    /**
     * No-args constructor for CDI proxy creation.
     * CDI requires a non-private constructor to create proxies for @ApplicationScoped beans.
     * All fields are initialized by the @Inject constructor during actual bean creation.
     */
    @SuppressWarnings("NullAway")
    protected BasePushNotificationSender() {
        // For CDI proxy creation — all fields are overwritten by the @Inject constructor.
        // urlValidator is intentionally non-null so that SSRF protection is active even
        // if an instance is accidentally used before injection completes.
        this.httpClient = null;
        this.configStore = null;
        this.formattersByVersion = Map.of();
        this.urlValidator = new DefaultPushNotificationUrlValidator();
        this.orderedDispatchExecutor = orderedDispatchWorkers;
    }

    public BasePushNotificationSender(PushNotificationConfigStore configStore,
                                       PushNotificationUrlValidator urlValidator) {
        this.httpClient = A2AHttpClientFactory.create(A2AHttpClientConfiguration.DEFAULT);
        this.configStore = configStore;
        this.formattersByVersion = Map.of();
        this.urlValidator = urlValidator;
        this.orderedDispatchExecutor = orderedDispatchWorkers;
    }

    /**
     * CDI injection constructor.
     *
     * <p>The HTTP client is resolved in order of preference:
     * <ol>
     *   <li>A {@link Instrumented @Instrumented} bean (e.g. one
     *       produced by the OpenTelemetry server extra, which wraps the
     *       application's client with trace-context injection)</li>
     *   <li>An unqualified {@link A2AHttpClient} bean provided by the
     *       application (custom mTLS, proxy, authentication, etc.)</li>
     *   <li>{@link A2AHttpClientFactory#create(A2AHttpClientConfiguration)} as last resort</li>
     * </ol>
     *
     * <p>The dispatch executor is resolved in order of preference:
     * <ol>
     *   <li>An {@link Instrumented @Instrumented} {@link Executor} (e.g. one
     *       produced by the OpenTelemetry server extra, which wraps the
     *       application's executor with context propagation)</li>
     *   <li>A {@link Named @Named}{@code ("a2a.pushNotificationDispatch")}
     *       {@link Executor} provided by the application</li>
     *   <li>{@link ForkJoinPool#commonPool()} as last resort</li>
     * </ol>
     *
     * @param configStore the store for push notification configurations
     * @param formatters optional formatters for protocol-version-specific payloads
     * @param urlValidator validates push notification URLs to prevent SSRF
     * @param httpClient optional CDI-provided HTTP client
     * @param propagatingClient optional context-propagating HTTP client that wraps the
     *                          application's client with observability instrumentation
     * @param instrumentedExecutor optional instrumented executor with context propagation
     * @param dispatchExecutor optional named executor for async dispatch
     */
    @Inject
    public BasePushNotificationSender(PushNotificationConfigStore configStore,
                                       Instance<PushNotificationPayloadFormatter> formatters,
                                       PushNotificationUrlValidator urlValidator,
                                       Instance<A2AHttpClient> httpClient,
                                       @Instrumented Instance<A2AHttpClient> propagatingClient,
                                       @Instrumented Instance<Executor> instrumentedExecutor,
                                       @Named(DISPATCH_EXECUTOR_NAME) Instance<Executor> dispatchExecutor) {
        this.httpClient = resolveHttpClient(propagatingClient, httpClient);
        this.configStore = configStore;
        this.formattersByVersion = toFormatterMap(formatters);
        this.urlValidator = urlValidator;
        this.dispatchExecutor = resolveExecutor(instrumentedExecutor, dispatchExecutor);
        this.orderedDispatchExecutor = resolveOrderedExecutor(dispatchExecutor);
    }

    public BasePushNotificationSender(PushNotificationConfigStore configStore, A2AHttpClient httpClient,
                                       PushNotificationUrlValidator urlValidator) {
        this.configStore = configStore;
        this.httpClient = httpClient;
        this.formattersByVersion = Map.of();
        this.urlValidator = urlValidator;
        this.orderedDispatchExecutor = orderedDispatchWorkers;
    }

    public BasePushNotificationSender(PushNotificationConfigStore configStore, A2AHttpClient httpClient,
                                       List<PushNotificationPayloadFormatter> formatters,
                                       PushNotificationUrlValidator urlValidator) {
        this.configStore = configStore;
        this.httpClient = httpClient;
        this.formattersByVersion = toFormatterMap(formatters);
        this.urlValidator = urlValidator;
        this.orderedDispatchExecutor = orderedDispatchWorkers;
    }

    private static A2AHttpClient resolveHttpClient(Instance<A2AHttpClient> propagating,
                                                     Instance<A2AHttpClient> plain) {
        if (propagating.isResolvable()) {
            return propagating.get();
        }
        if (plain.isResolvable()) {
            return plain.get();
        }
        return A2AHttpClientFactory.create(A2AHttpClientConfiguration.DEFAULT);
    }

    private static Executor resolveExecutor(Instance<Executor> instrumented,
                                              Instance<Executor> named) {
        if (instrumented.isResolvable()) {
            return instrumented.get();
        }
        if (named.isResolvable()) {
            return named.get();
        }
        return ForkJoinPool.commonPool();
    }

    private Executor resolveOrderedExecutor(Instance<Executor> named) {
        return named.isResolvable() ? named.get() : orderedDispatchWorkers;
    }

    private static Map<String, PushNotificationPayloadFormatter> toFormatterMap(
            Iterable<PushNotificationPayloadFormatter> formatters) {
        Map<String, PushNotificationPayloadFormatter> map = new HashMap<>();
        for (PushNotificationPayloadFormatter f : formatters) {
            map.put(f.targetVersion(), f);
        }
        return map;
    }

    @Override
    public boolean sendNotification(StreamingEventKind event, @Nullable Task taskSnapshot) {
        String taskId = extractTaskId(event);
        if (taskId == null && event instanceof Message && taskSnapshot != null) {
            taskId = taskSnapshot.id();
        }
        if (taskId == null) {
            throw new IllegalArgumentException("Cannot send push notification: event does not contain taskId");
        }

        List<TaskPushNotificationConfig> configs = getPushConfigs(taskId);
        if (configs.isEmpty()) {
            LOGGER.debug("No push notification configs registered for taskId: {}", taskId);
            return true;
        }
        Map<String, String> versionsByConfigId = configStore.getProtocolVersions(taskId);
        if (TaskProposalDispatchContext.contains(event, taskSnapshot)
                || TaskProposalDispatchContext.isForwardedArtifactCopy(event, taskSnapshot)) {
            StreamingEventKind legacySummary = TaskProposalDispatchContext.claimLegacySummary(event, taskSnapshot,
                    this)
                    ? taskSnapshot : null;
            return dispatchConfigs(taskId, taskSnapshot, configs, versionsByConfigId, pushConfig ->
                    "1.1".equals(versionsByConfigId.getOrDefault(pushConfig.id(), "1.0")) ? event : legacySummary);
        }
        return dispatchConfigs(taskId, taskSnapshot, configs, versionsByConfigId, ignored -> event);
    }

    /** Delivers a proposal status using each stored config's selected wire version. */
    @Override
    public boolean sendTaskProposalStatusNotification(TaskStatusUpdateEvent event, Task taskSnapshot) {
        String taskId = event.taskId();
        List<TaskPushNotificationConfig> configs = getPushConfigs(taskId);
        if (configs.isEmpty()) {
            return true;
        }
        Map<String, String> versionsByConfigId = configStore.getProtocolVersions(taskId);
        if (TaskProposalDispatchContext.contains(event, taskSnapshot)) {
            return dispatchConfigs(taskId, taskSnapshot, configs, versionsByConfigId, pushConfig ->
                    "1.1".equals(versionsByConfigId.getOrDefault(pushConfig.id(), "1.0")) ? event : null);
        }
        return dispatchConfigs(taskId, taskSnapshot, configs, versionsByConfigId, pushConfig ->
                "1.1".equals(versionsByConfigId.getOrDefault(pushConfig.id(), "1.0")) ? event : taskSnapshot);
    }

    /** Selects one legacy Task summary or the complete canonical 1.1 batch per webhook config. */
    @Override
    public boolean sendTaskProposalBatchNotification(CommittedTaskBatch batch) {
        String taskId = batch.taskId();
        Task taskSnapshot = batch.snapshot().task();
        List<TaskPushNotificationConfig> configs = getPushConfigs(taskId);
        if (configs.isEmpty()) {
            return true;
        }
        Map<String, String> versionsByConfigId = configStore.getProtocolVersions(taskId);
        boolean delivered = true;
        for (int index = 0; index < batch.events().size(); index++) {
            StreamingEventKind event = batch.events().get(index);
            boolean firstEvent = index == 0;
            boolean eventDelivered = dispatchConfigs(taskId, taskSnapshot, configs, versionsByConfigId, pushConfig -> {
                String version = versionsByConfigId.getOrDefault(pushConfig.id(), "1.0");
                if ("1.1".equals(version)) {
                    return event;
                }
                return firstEvent ? taskSnapshot : null;
            });
            delivered &= eventDelivered;
        }
        return delivered;
    }

    @Override
    public boolean supportsOrderedDispatch() {
        return true;
    }

    /**
     * Ordered adapter: performs one HTTP attempt at a time and completes only when the active attempt worker exits.
     * A deadline prevents later requests from starting. An interrupted blocking HTTP call is conservatively left
     * unresolved because the underlying client may still be working after its observer was interrupted.
     */
    @Override
    public CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, long deadlineNanos,
            LongSupplier nanoTime) {
        return sendOrderedBatch(batch, deadlineNanos, nanoTime, UnaryOperator.identity());
    }

    @Override
    public CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, long deadlineNanos,
            LongSupplier nanoTime, UnaryOperator<Runnable> taskContext) {
        return sendOrderedBatch(batch, false, deadlineNanos, nanoTime, taskContext);
    }

    @Override
    public CompletionStage<Boolean> sendOrderedBatch(CommittedTaskBatch batch, boolean taskProposal,
            long deadlineNanos, LongSupplier nanoTime, UnaryOperator<Runnable> taskContext) {
        AtomicBoolean uncertainAttempt = new AtomicBoolean();
        return dispatchOrderedWork(() -> {
            String taskId = batch.taskId();
            Task taskSnapshot = batch.snapshot().task();
            List<TaskPushNotificationConfig> configs = getPushConfigs(taskId);
            if (configs.isEmpty()) {
                return true;
            }
            Map<String, String> versionsByConfigId = configStore.getProtocolVersions(taskId);
            boolean delivered = true;
            for (int eventIndex = 0; eventIndex < batch.events().size(); eventIndex++) {
                StreamingEventKind event = batch.events().get(eventIndex);
                for (TaskPushNotificationConfig config : configs) {
                    if (nanoTime.getAsLong() >= deadlineNanos) {
                        return false;
                    }
                    String version = versionsByConfigId.getOrDefault(config.id(), "1.0");
                    StreamingEventKind projected = "1.1".equals(version)
                            ? event : taskProposal ? eventIndex == 0 ? taskSnapshot : null : event;
                    if (projected != null) {
                        boolean sent = dispatchNotification(projected, taskSnapshot, config, versionsByConfigId,
                                deadlineNanos, nanoTime, uncertainAttempt);
                        if (uncertainAttempt.get()) {
                            return false;
                        }
                        delivered &= sent;
                    }
                }
            }
            return delivered;
        }, deadlineNanos, nanoTime, uncertainAttempt, taskContext);
    }

    @Override
    public CompletionStage<Boolean> sendOrderedLegacyTaskSnapshot(Task snapshot, long deadlineNanos,
            LongSupplier nanoTime) {
        return sendOrderedLegacyTaskSnapshot(snapshot, deadlineNanos, nanoTime, UnaryOperator.identity());
    }

    @Override
    public CompletionStage<Boolean> sendOrderedLegacyTaskSnapshot(Task snapshot, long deadlineNanos,
            LongSupplier nanoTime, UnaryOperator<Runnable> taskContext) {
        AtomicBoolean uncertainAttempt = new AtomicBoolean();
        return dispatchOrderedWork(() -> {
            List<TaskPushNotificationConfig> configs = getPushConfigs(snapshot.id());
            if (configs.isEmpty()) {
                return true;
            }
            Map<String, String> versionsByConfigId = configStore.getProtocolVersions(snapshot.id());
            boolean delivered = true;
            for (TaskPushNotificationConfig config : configs) {
                if (nanoTime.getAsLong() >= deadlineNanos) {
                    return false;
                }
                String version = versionsByConfigId.getOrDefault(config.id(), "1.0");
                if ("1.1".equals(version)) {
                    continue;
                }
                boolean sent = dispatchNotification(snapshot, snapshot, config, versionsByConfigId,
                        deadlineNanos, nanoTime, uncertainAttempt);
                if (uncertainAttempt.get()) {
                    return false;
                }
                delivered &= sent;
            }
            return delivered;
        }, deadlineNanos, nanoTime, uncertainAttempt, taskContext);
    }

    /** Delivers an active-task no-op snapshot only to configs using an older protocol version. */
    @Override
    public boolean sendLegacyTaskSnapshot(Task taskSnapshot) {
        String taskId = taskSnapshot.id();
        List<TaskPushNotificationConfig> configs = getPushConfigs(taskId);
        if (configs.isEmpty()) {
            return true;
        }
        Map<String, String> versionsByConfigId = configStore.getProtocolVersions(taskId);
        return dispatchConfigs(taskId, taskSnapshot, configs, versionsByConfigId, pushConfig -> {
            String version = versionsByConfigId.getOrDefault(pushConfig.id(), "1.0");
            return "0.3".equals(version) || "1.0".equals(version) ? taskSnapshot : null;
        });
    }

    private List<TaskPushNotificationConfig> getPushConfigs(String taskId) {
        List<TaskPushNotificationConfig> configs = new ArrayList<>();
        String nextPageToken = null;
        do {
          ListTaskPushNotificationConfigsResult pageResult = configStore.getInfo(new ListTaskPushNotificationConfigsParams(taskId,
              DEFAULT_PAGE_SIZE, nextPageToken == null ? "" : nextPageToken, ""));
          if (!pageResult.configs().isEmpty()) {
            configs.addAll(pageResult.configs());
          }
          nextPageToken = pageResult.nextPageToken();
        } while (nextPageToken != null);

        return configs;
    }

    private boolean dispatchConfigs(String taskId, @Nullable Task taskSnapshot,
            List<TaskPushNotificationConfig> configs, Map<String, String> versionsByConfigId,
            Function<TaskPushNotificationConfig, @Nullable StreamingEventKind> eventSelector) {
        List<CompletableFuture<Boolean>> dispatchResults = new ArrayList<>();
        for (TaskPushNotificationConfig pushConfig : configs) {
            StreamingEventKind event = eventSelector.apply(pushConfig);
            if (event != null) {
                dispatchResults.add(dispatch(event, taskSnapshot, pushConfig, versionsByConfigId));
            }
        }
        if (dispatchResults.isEmpty()) {
            return true;
        }
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(dispatchResults.toArray(new CompletableFuture[0]));
        CompletableFuture<Boolean> dispatchResult = allFutures.thenApply(v -> dispatchResults.stream()
                .allMatch(CompletableFuture::join));
        try {
            boolean allSent = dispatchResult.get();
            if (!allSent) {
                LOGGER.warn("Some push notifications failed to send for taskId: {}", taskId);
            }
            return allSent;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Some push notifications failed to send for taskId {}: {}", taskId, e.getMessage(), e);
            return false;
        } catch (ExecutionException e) {
            LOGGER.warn("Some push notifications failed to send for taskId {}: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extracts the task ID from a streaming event.
     *
     * @param event the streaming event
     * @return the task ID, or {@code null} if the event type does not carry one
     *         (e.g. a {@link Message} without a task ID)
     * @throws IllegalStateException if the event is not a recognized
     *         {@link StreamingEventKind} subtype
     */
    protected @Nullable String extractTaskId(StreamingEventKind event) {
        if (event instanceof Task task) {
            return task.id();
        }
        if (event instanceof Message message) {
            return message.taskId();
        }
        if (event instanceof TaskStatusUpdateEvent statusUpdate) {
            return statusUpdate.taskId();
        }
        if (event instanceof TaskArtifactUpdateEvent artifactUpdate) {
            return artifactUpdate.taskId();
        }
        if (event instanceof TaskMessageUpdateEvent messageUpdate) {
            return messageUpdate.taskId();
        }
        throw new IllegalStateException("Unknown StreamingEventKind: " + event);
    }

    private CompletableFuture<Boolean> dispatch(StreamingEventKind event,
                                                 @Nullable Task taskSnapshot,
                                                 TaskPushNotificationConfig pushInfo,
                                                 Map<String, String> versionsByConfigId) {
        return CompletableFuture.supplyAsync(
                () -> dispatchNotification(event, taskSnapshot, pushInfo, versionsByConfigId), dispatchExecutor);
    }

    /** Keeps scheduler threads away from user-configured executors and bounds retained ordered payloads. */
    private CompletionStage<Boolean> dispatchOrderedWork(Supplier<Boolean> work, long deadlineNanos,
            LongSupplier nanoTime, AtomicBoolean uncertainAttempt, UnaryOperator<Runnable> taskContext) {
        if (orderedDispatchClosed.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Ordered webhook sender is closed"));
        }
        if (!orderedDispatchPermits.tryAcquire()) {
            throw new RejectedExecutionException("Ordered webhook dispatch capacity is full");
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        OrderedDispatchAttempt attempt = new OrderedDispatchAttempt(result, uncertainAttempt);
        try {
            attempt.setPayload(taskContext.apply(() -> {
                try {
                    result.complete(work.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            }));
        } catch (RuntimeException | Error contextFailure) {
            attempt.rejectPending(contextFailure);
            return CompletableFuture.failedFuture(contextFailure);
        }

        orderedDispatchAttempts.add(attempt);
        if (orderedDispatchClosed.get()) {
            attempt.revokeNeverStarted();
            return result;
        }

        try {
            long delayNanos = Math.max(0, deadlineNanos - nanoTime.getAsLong());
            attempt.setDeadline(orderedDispatchDeadlines.schedule(attempt::expire, delayNanos,
                    TimeUnit.NANOSECONDS));
            orderedDispatchIngressWorkers.execute(attempt::handoff);
        } catch (RuntimeException | Error rejected) {
            attempt.rejectPending(rejected);
        }
        return result.thenCompose(sent -> uncertainAttempt.get()
                ? new CompletableFuture<>() : CompletableFuture.completedFuture(sent));
    }

    private final class OrderedDispatchAttempt implements Runnable {
        private enum State {
            QUEUED,
            SUBMITTED,
            RUNNING,
            EXPIRED,
            DONE,
            UNCERTAIN
        }

        private final CompletableFuture<Boolean> result;
        private final AtomicBoolean uncertainAttempt;
        private final AtomicReference<State> state = new AtomicReference<>(State.QUEUED);
        private final AtomicReference<@Nullable Runnable> payload = new AtomicReference<>();
        private final AtomicBoolean httpPermitReleased = new AtomicBoolean();
        private final AtomicBoolean shellPermitHeld = new AtomicBoolean();
        private volatile @Nullable ScheduledFuture<?> deadlineTask;

        private OrderedDispatchAttempt(CompletableFuture<Boolean> result, AtomicBoolean uncertainAttempt) {
            this.result = result;
            this.uncertainAttempt = uncertainAttempt;
        }

        private void setPayload(Runnable work) {
            payload.set(work);
        }

        private void setDeadline(ScheduledFuture<?> scheduledTask) {
            deadlineTask = scheduledTask;
            State current = state.get();
            if (current == State.RUNNING || current == State.EXPIRED || current == State.DONE
                    || current == State.UNCERTAIN) {
                scheduledTask.cancel(false);
            }
        }

        private void handoff() {
            if (state.get() != State.QUEUED) {
                return;
            }
            if (!orderedDispatchShellPermits.tryAcquire()) {
                rejectPending(new RejectedExecutionException("Ordered webhook handoff capacity is full"));
                return;
            }
            shellPermitHeld.set(true);
            synchronized (this) {
                if (!state.compareAndSet(State.QUEUED, State.SUBMITTED)) {
                    releaseShellPermit();
                    return;
                }
            }
            try {
                // This runs only on a private ingress worker. Even a caller-runs executor cannot put HTTP
                // work on the ordered dispatcher's scheduler thread.
                orderedDispatchExecutor.execute(this);
            } catch (RuntimeException | Error failure) {
                rejectPending(failure);
            }
        }

        private synchronized void expire() {
            State current = state.get();
            if (current != State.QUEUED && current != State.SUBMITTED) {
                return;
            }
            if (!state.compareAndSet(current, State.EXPIRED)) {
                return;
            }
            payload.set(null);
            releaseHttpPermit();
            if (current == State.QUEUED) {
                releaseShellPermit();
            }
            result.complete(false);
            orderedDispatchAttempts.remove(this);
        }

        private synchronized void rejectPending(Throwable failure) {
            State current = state.get();
            if (current != State.QUEUED && current != State.SUBMITTED) {
                // An executor may enqueue or run the command and then throw. If it already started, retain
                // ownership until the actual worker exits; if it remains queued, invalidate it atomically.
                return;
            }
            if (!state.compareAndSet(current, State.EXPIRED)) {
                return;
            }
            payload.set(null);
            releaseHttpPermit();
            if (current == State.QUEUED) {
                releaseShellPermit();
            } else if (failure instanceof RejectedExecutionException && isProvenAbortPolicyExecutor()) {
                releaseShellPermit();
            }
            cancelDeadline();
            LOGGER.debug("Ordered webhook executor rejected a pending attempt", failure);
            result.complete(false);
            orderedDispatchAttempts.remove(this);
        }

        @Override
        public void run() {
            if (!state.compareAndSet(State.SUBMITTED, State.RUNNING)) {
                if (state.get() == State.EXPIRED) {
                    releaseShellPermit();
                }
                return;
            }
            releaseShellPermit();
            Runnable task = payload.getAndSet(null);
            try {
                if (task == null) {
                    result.complete(false);
                } else {
                    task.run();
                }
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
            } finally {
                if (uncertainAttempt.get()) {
                    state.set(State.UNCERTAIN);
                } else {
                    state.set(State.DONE);
                    releaseHttpPermit();
                }
                cancelDeadline();
                orderedDispatchAttempts.remove(this);
            }
        }

        private synchronized void revokeNeverStarted() {
            State current = state.get();
            if (current != State.QUEUED && current != State.SUBMITTED) {
                return;
            }
            if (!state.compareAndSet(current, State.EXPIRED)) {
                return;
            }
            payload.set(null);
            releaseHttpPermit();
            if (current == State.QUEUED) {
                releaseShellPermit();
            }
            cancelDeadline();
            result.complete(false);
            orderedDispatchAttempts.remove(this);
        }

        private void releaseHttpPermit() {
            if (httpPermitReleased.compareAndSet(false, true)) {
                orderedDispatchPermits.release();
            }
        }

        private void releaseShellPermit() {
            if (shellPermitHeld.compareAndSet(true, false)) {
                orderedDispatchShellPermits.release();
            }
        }

        private void cancelDeadline() {
            ScheduledFuture<?> scheduledTask = deadlineTask;
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
            }
        }
    }

    private boolean isProvenAbortPolicyExecutor() {
        Executor executor = orderedDispatchExecutor;
        return executor != null && executor.getClass() == ThreadPoolExecutor.class
                && ((ThreadPoolExecutor) executor).getRejectedExecutionHandler().getClass()
                        == ThreadPoolExecutor.AbortPolicy.class;
    }

    private static ThreadPoolExecutor newOrderedWorkerPool() {
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "a2a-ordered-webhook-http");
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1022), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    private static ThreadPoolExecutor newOrderedIngressPool() {
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "a2a-ordered-webhook-ingress");
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1022), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    private static ScheduledThreadPoolExecutor newOrderedDeadlineScheduler() {
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "a2a-ordered-webhook-deadline");
            thread.setDaemon(true);
            return thread;
        };
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, factory);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @PreDestroy
    public void closeOrderedDispatchWorkers() {
        if (!orderedDispatchClosed.compareAndSet(false, true)) {
            return;
        }
        for (OrderedDispatchAttempt attempt : orderedDispatchAttempts) {
            attempt.revokeNeverStarted();
        }
        orderedDispatchIngressWorkers.shutdown();
        orderedDispatchWorkers.shutdown();
        orderedDispatchDeadlines.shutdownNow();
    }

    private boolean dispatchNotification(StreamingEventKind event,
                                          @Nullable Task taskSnapshot,
                                          TaskPushNotificationConfig pushInfo,
                                          Map<String, String> versionsByConfigId) {
        return dispatchNotification(event, taskSnapshot, pushInfo, versionsByConfigId,
                Long.MAX_VALUE, System::nanoTime, null);
    }

    private boolean dispatchNotification(StreamingEventKind event,
                                          @Nullable Task taskSnapshot,
                                          TaskPushNotificationConfig pushInfo,
                                          Map<String, String> versionsByConfigId,
                                          long deadlineNanos, LongSupplier nanoTime,
                                          @Nullable AtomicBoolean uncertainAttempt) {
        String version = versionsByConfigId.get(pushInfo.id());
        if (version == null) {
            version = "1.0";
        }
        ServerVersionPolicy policy = switch (version) {
            case "0.3" -> ServerVersionPolicy.V0_3;
            case "1.0" -> ServerVersionPolicy.V1_0;
            case "1.1" -> ServerVersionPolicy.V1_1;
            default -> null;
        };
        if (policy == null) {
            LOGGER.error("No webhook formatter is available for protocol version {}", version);
            return false;
        }
        StreamingEventKind projectedEvent = VersionedOutputProjector.projectEvent(event, policy);
        if (projectedEvent == null) {
            LOGGER.debug("Suppressing {} webhook event for protocol version {}", event.kind(), version);
            return true;
        }
        @Nullable Task projectedSnapshot = taskSnapshot == null ? null
                : VersionedOutputProjector.projectTask(taskSnapshot, policy);

        String url = pushInfo.url();

        try {
            urlValidator.validate(url);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Rejecting push notification to {}: {}", url, e.getMessage());
            return false;
        }

        String token = pushInfo.token();

        String body;
        if ("0.3".equals(version)) {
            PushNotificationPayloadFormatter formatter = formattersByVersion.get(version);
            if (formatter == null) {
                LOGGER.error("No webhook formatter is registered for protocol version {}", version);
                return false;
            }
            try {
                body = formatter.formatPayload(projectedEvent, projectedSnapshot);
            } catch (Throwable throwable) {
                LOGGER.error("Error formatting payload with {} formatter: {}",
                        version, throwable.getMessage(), throwable);
                return false;
            }
            if (body == null) {
                LOGGER.debug("Formatter for version {} returned null, skipping notification for {}",
                        version, url);
                return true;
            }
        } else {
            try {
                body = JsonUtil.toJsonStreamingEvent(projectedEvent, version);
            } catch (Throwable throwable) {
                LOGGER.error("Error serializing StreamingEventKind to JSON: {}", throwable.getMessage(), throwable);
                return false;
            }
        }

        A2AHttpClient.PostBuilder postBuilder = httpClient.createPost()
                .followRedirects(false);
        if (token != null && !token.isBlank()) {
            try {
                rejectCrlf(token, X_A2A_NOTIFICATION_TOKEN);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Rejecting push notification to {}: {}", url, e.getMessage());
                return false;
            }
            postBuilder.addHeader(X_A2A_NOTIFICATION_TOKEN, token);
        }
        AuthenticationInfo authentication = pushInfo.authentication();
        if (authentication != null) {
            String credentials = authentication.credentials();
            if (credentials != null) {
                String authorizationHeader;
                try {
                    authorizationHeader = buildAuthorizationHeader(authentication.scheme(), credentials);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Rejecting push notification to {}: {}", url, e.getMessage());
                    return false;
                }
                postBuilder.addHeader("Authorization", authorizationHeader);
            }
        }

        try {
            postBuilder
                    .url(url)
                    .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .body(body);
            // Custom builders may block during setup, so recheck immediately before POST.
            if (nanoTime.getAsLong() >= deadlineNanos) {
                return false;
            }
            A2AHttpResponse response = postBuilder.post();
            if (!response.success()) {
                LOGGER.warn("Push notification [config={}] to {} failed with HTTP status {}",
                        pushInfo.id(), url, response.status());
                return false;
            }
        } catch (InterruptedException e) {
            if (uncertainAttempt != null) {
                uncertainAttempt.set(true);
                Thread.currentThread().interrupt();
                LOGGER.warn("Ordered push notification [config={}] to {} was interrupted; retaining lane ownership",
                        pushInfo.id(), url, e);
                return false;
            }
            Thread.currentThread().interrupt();
            LOGGER.warn("Push notification [config={}] to {} failed: {}", pushInfo.id(), url, e.getMessage(), e);
            return false;
        } catch (IOException e) {
            LOGGER.warn("Push notification [config={}] to {} failed: {}", pushInfo.id(), url, e.getMessage(), e);
            return false;
        }
        return true;
    }

    /**
     * Builds the Authorization header value for a push notification config.
     *
     * <p>The {@code scheme} and {@code credentials} are client-controlled values that are
     * concatenated directly into the header. Rejecting CR/LF characters here prevents
     * HTTP header injection (CWE-113). The {@link A2AHttpClient} SPI is pluggable, so we
     * cannot rely on every implementation (or the underlying HTTP client) to validate
     * header values.</p>
     *
     * @param scheme the authentication scheme
     * @param credentials the authentication credentials
     * @return the assembled {@code "scheme credentials"} header value
     * @throws IllegalArgumentException if either field contains CR or LF
     */
    private static String buildAuthorizationHeader(String scheme, String credentials) {
        rejectCrlf(scheme, "Authorization scheme");
        rejectCrlf(credentials, "Authorization credentials");
        return scheme + " " + credentials;
    }

    /**
     * Throws {@link IllegalArgumentException} if {@code value} contains CR or LF.
     *
     * <p>Prevents HTTP header injection (CWE-113) for client-controlled header values.</p>
     *
     * @param value non-null string to validate
     * @param label human-readable description of the field, used in the exception message
     */
    private static void rejectCrlf(String value, String label) {
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                    label + " must not contain CR/LF characters");
        }
    }
}
