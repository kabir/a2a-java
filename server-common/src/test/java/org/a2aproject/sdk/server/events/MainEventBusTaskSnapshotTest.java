package org.a2aproject.sdk.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.ExecutorSubmissionService;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.ProposalResult;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskProposal;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class MainEventBusTaskSnapshotTest {
    @Test
    void firstStatusOrArtifactUpdatePreservesOpeningMessage() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, mock(TaskStore.class, delegatesTo(store)), PushNotificationSender.NOOP, queues);
        EventQueueUtil.start(processor);
        try {
            Message opening = Message.builder().messageId("opening").role(Message.Role.ROLE_USER)
                    .parts(new TextPart("hello")).build();
            List<Event> events = List.of(
                    TaskStatusUpdateEvent.builder().taskId("status").contextId("context")
                            .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build(),
                    TaskArtifactUpdateEvent.builder().taskId("artifact").contextId("context")
                            .artifact(Artifact.builder().artifactId("output").parts(new TextPart("result")).build())
                            .build());
            List<String> ids = List.of("status", "artifact");
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                processor.registerInitialMessage(id, opening);
                EventQueue queue = EventQueueUtil.getEventQueueBuilder(bus).taskId(id).build().tap();
                queue.enqueueEvent(events.get(i));
                queue.dequeueEventItem(5000);
                assertEquals(List.of(opening), store.get(id).history());
            }
        } finally {
            EventQueueUtil.stop(processor);
        }
    }

    @Test
    void committedProposalSnapshotIsDispatchedReplicatedAndNotified() throws Exception {
        String id = "task-snapshot";
        InMemoryTaskStore store = new InMemoryTaskStore();
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        CountDownLatch delivered = new CountDownLatch(2);
        List<Task> notified = new CopyOnWriteArrayList<>();
        List<EventQueueItem> replicated = new CopyOnWriteArrayList<>();
        PushNotificationSender sender = (event, snapshot) -> {
            notified.add(snapshot);
            delivered.countDown();
            return true;
        };
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, sender, queues);
        EventQueueUtil.start(processor);
        try {
            EventQueue queue = EventQueueUtil.getEventQueueBuilder(bus).taskId(id)
                    .hook(replicated::add).build().tap();
            ProposalResult.Committed result = submitOpeningProposal(queue, store, id, TaskState.TASK_STATE_WORKING);
            List<EventQueueItem> streamed = new ArrayList<>();
            streamed.add(queue.dequeueEventItem(5000));
            streamed.add(queue.dequeueEventItem(5000));

            assertTrue(delivered.await(5, TimeUnit.SECONDS));
            Task stored = store.get(id);
            assertEquals(List.of("opening"), stored.history().stream().map(Message::messageId).toList());
            assertEquals(stored, result.snapshot().task());
            assertEquals(result.events(), streamed.stream().map(EventQueueItem::getEvent).toList());
            assertEquals(result.events(), replicated.stream().map(EventQueueItem::getEvent).toList());
            assertEquals(List.of(stored, stored), streamed.stream().map(EventQueueItem::resultTaskSnapshot).toList());
            assertEquals(List.of(stored, stored), replicated.stream().map(EventQueueItem::resultTaskSnapshot).toList());
            assertEquals(List.of(stored, stored), notified);
        } finally {
            EventQueueUtil.stop(processor);
        }
    }

    @Test
    void terminalProposalStillTriggersFinalizationCallback() throws Exception {
        String id = "final-task-snapshot";
        InMemoryTaskStore store = new InMemoryTaskStore();
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store, PushNotificationSender.NOOP, queues);
        CountDownLatch finalized = new CountDownLatch(1);
        processor.setCallback(new MainEventBusProcessorCallback() {
            public void onEventProcessed(String taskId, Event event) {
            }

            public void onTaskFinalized(String taskId) {
                if (id.equals(taskId)) {
                    finalized.countDown();
                }
            }
        });
        EventQueueUtil.start(processor);
        try {
            EventQueue queue = EventQueueUtil.getEventQueueBuilder(bus).taskId(id).build().tap();
            ProposalResult.Committed result = submitOpeningProposal(queue, store, id, TaskState.TASK_STATE_COMPLETED);
            assertTrue(finalized.await(5, TimeUnit.SECONDS));
            assertEquals(result.snapshot().task(), store.get(id));
            assertEquals(TaskState.TASK_STATE_COMPLETED, store.get(id).status().state());
            assertEquals(List.of("opening"), store.get(id).history().stream().map(Message::messageId).toList());
        } finally {
            EventQueueUtil.stop(processor);
        }
    }

    private static ProposalResult.Committed submitOpeningProposal(EventQueue queue, InMemoryTaskStore store,
            String taskId, TaskState state) throws Exception {
        Message opening = Message.builder().messageId("opening").role(Message.Role.ROLE_USER)
                .parts(new TextPart("hello")).build();
        RequestContext context = new RequestContext.Builder().setTaskId(taskId).setContextId("context")
                .setParams(MessageSendParams.builder().message(opening).build()).build();
        ExecutorSubmissionService submissions = new ExecutorSubmissionService(
                ExecutorSubmissionService.forTaskStore(store), context, Duration.ofSeconds(5), Duration.ofSeconds(5));
        Task goal = Task.builder().id(taskId).contextId("context").status(new TaskStatus(state)).build();
        return assertInstanceOf(ProposalResult.Committed.class,
                submissions.submitTask(new TaskProposal("proposal", null, goal), queue)
                        .toCompletableFuture().get(5, TimeUnit.SECONDS));
    }
}
