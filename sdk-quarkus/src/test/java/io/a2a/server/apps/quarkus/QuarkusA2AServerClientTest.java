package io.a2a.server.apps.quarkus;

import io.a2a.server.apps.common.AbstractA2AServerClientTest;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.tasks.TaskStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class QuarkusA2AServerClientTest extends AbstractA2AServerClientTest {

    @Inject
    TaskStore taskStore;

    @Inject
    InMemoryQueueManager queueManager;

    public QuarkusA2AServerClientTest() {
        super(8081);
    }

    @Override
    protected TaskStore getTaskStore() {
        return taskStore;
    }

    @Override
    protected InMemoryQueueManager getQueueManager() {
        return queueManager;
    }

    @Override
    protected void setStreamingSubscribedRunnable(Runnable runnable) {
        A2AServerRoutes.setStreamingMultiSseSupportSubscribedRunnable(runnable);
    }
} 