package org.a2aproject.sdk.extras.queuemanager.replicated.events;

import org.a2aproject.sdk.server.events.EventQueueTestHelper;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;

public class EventQueueUtil {
    public static void start(MainEventBusProcessor processor) {
        EventQueueTestHelper.startProcessor(processor);
    }

    public static void stop(MainEventBusProcessor processor) {
        EventQueueTestHelper.stopProcessor(processor);
    }
}
