package org.a2aproject.sdk.server.requesthandlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.events.EventQueueUtil;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TextPart;

/** Shared fixture: real handler, in-memory atomic store, real bus processor, observable webhook seams. */
final class OrdinaryInputTestSupport {

    final InMemoryTaskStore taskStore;
    final List<String> configWrites = new CopyOnWriteArrayList<>();
    final List<String> configDeletes = new CopyOnWriteArrayList<>();
    final List<StreamingEventKind> pushed = new CopyOnWriteArrayList<>();
    final PushNotificationConfigStore configStore;
    final MainEventBusProcessor processor;
    final InMemoryQueueManager queues;
    final Executor workers = Executors.newCachedThreadPool();
    DefaultRequestHandler handler;

    OrdinaryInputTestSupport(AgentExecutor executor) {
        this(executor, new InMemoryPushNotificationConfigStore());
    }

    OrdinaryInputTestSupport(AgentExecutor executor, InMemoryPushNotificationConfigStore delegate) {
        this(executor, delegate, new InMemoryTaskStore());
    }

    /** Builds the bus processor; lets a test inject a fault into the post-commit publication seam. */
    interface ProcessorFactory {
        MainEventBusProcessor create(MainEventBus bus, InMemoryTaskStore store, PushNotificationSender sender,
                InMemoryQueueManager queues);
    }

    OrdinaryInputTestSupport(AgentExecutor executor, InMemoryPushNotificationConfigStore delegate,
            InMemoryTaskStore taskStore) {
        this(executor, delegate, taskStore, MainEventBusProcessor::new);
    }

    OrdinaryInputTestSupport(AgentExecutor executor, InMemoryPushNotificationConfigStore delegate,
            InMemoryTaskStore taskStore, ProcessorFactory processorFactory) {
        this.taskStore = taskStore;
        this.configStore = new PushNotificationConfigStore() {
            @Override
            public TaskPushNotificationConfig setInfo(TaskPushNotificationConfig config) {
                TaskPushNotificationConfig written = delegate.setInfo(config);
                configWrites.add(config.taskId() + ":" + written.id());
                return written;
            }

            @Override
            public org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult getInfo(
                    org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams params) {
                return delegate.getInfo(params);
            }

            @Override
            public void deleteInfo(String taskId, String configId) {
                configDeletes.add(taskId + ":" + configId);
                delegate.deleteInfo(taskId, configId);
            }

            @Override
            public boolean deleteInfoIfUnchanged(TaskPushNotificationConfig config) {
                configDeletes.add(config.taskId() + ":" + config.id());
                return delegate.deleteInfoIfUnchanged(config);
            }
        };
        MainEventBus bus = new MainEventBus();
        queues = new InMemoryQueueManager(taskStore, bus);
        PushNotificationSender sender = (event, snapshot) -> {
            pushed.add(event);
            return true;
        };
        processor = processorFactory.create(bus, taskStore, sender, queues);
        processor.setPushNotificationExecutor(Runnable::run);
        EventQueueUtil.start(processor);
        handler = DefaultRequestHandler.builder()
                .agentExecutor(executor)
                .taskStore(taskStore)
                .queueManager(queues)
                .pushConfigStore(configStore)
                .pushNotificationsEnabled(true)
                .mainEventBusProcessor(processor)
                .executor(workers)
                .eventConsumerExecutor(workers)
                .authorizationRequired(false)
                .build();
    }

    void stop() {
        EventQueueUtil.stop(processor);
    }

    static AgentCard card(boolean userArtifacts) {
        return AgentCard.builder().name("card").description("d")
                .supportedInterfaces(Collections.singletonList(new AgentInterface("JSONRPC", "http://example.com")))
                .version("1.0").documentationUrl("http://example.com/docs")
                .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(true)
                        .userArtifacts(userArtifacts).build())
                .defaultInputModes(new ArrayList<>()).defaultOutputModes(new ArrayList<>())
                .skills(new ArrayList<>()).build();
    }

    static ServerCallContext context(boolean userArtifacts) {
        ServerCallContext context = new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of(), "1.1");
        context.attachEffectiveAgentCard(null, card(userArtifacts));
        return context;
    }

    static Message userMessage(String id, String text) {
        return Message.builder().messageId(id).role(Message.Role.ROLE_USER).parts(List.of(new TextPart(text))).build();
    }

    static Message userMessage(String id, String text, String taskId, String contextId) {
        return Message.builder().messageId(id).taskId(taskId).contextId(contextId).role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(text))).build();
    }

    static List<StreamingEventKind> drain(java.util.concurrent.Flow.Publisher<StreamingEventKind> publisher)
            throws Exception {
        List<StreamingEventKind> events = new CopyOnWriteArrayList<>();
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        publisher.subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEventKind item) {
                events.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                failure.set(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        if (!done.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new AssertionError("stream did not complete");
        }
        if (failure.get() instanceof Exception e) {
            throw e;
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return events;
    }

    static long generationOf(StreamingEventKind event) {
        if (event instanceof Task task) {
            return task.generation();
        }
        if (event instanceof org.a2aproject.sdk.spec.TaskStatusUpdateEvent status) {
            return status.generation();
        }
        if (event instanceof org.a2aproject.sdk.spec.TaskArtifactUpdateEvent artifact) {
            return artifact.generation();
        }
        if (event instanceof org.a2aproject.sdk.spec.TaskMessageUpdateEvent message) {
            return message.generation();
        }
        return 0;
    }

    static Task firstTask(List<StreamingEventKind> events) {
        for (StreamingEventKind event : events) {
            if (event instanceof Task task) {
                return task;
            }
        }
        throw new AssertionError("no Task in " + events);
    }
}
