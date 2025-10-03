package io.a2a.server.events;

/**
 * Test utility class that has access to package-private EventQueue members.
 * This class must be in the io.a2a.server.events package to access internal types.
 */
public class EventQueueTestUtils {

    /**
     * Check if the queue is a MainQueue.
     */
    public static boolean isMainQueue(EventQueue queue) {
        return queue instanceof EventQueue.MainQueue;
    }

    /**
     * Check if the queue is a ChildQueue.
     */
    public static boolean isChildQueue(EventQueue queue) {
        return queue instanceof EventQueue.ChildQueue;
    }

    /**
     * Check if a ChildQueue can be tapped (it shouldn't be able to).
     */
    public static boolean canTap(EventQueue queue) {
        try {
            queue.tap();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
