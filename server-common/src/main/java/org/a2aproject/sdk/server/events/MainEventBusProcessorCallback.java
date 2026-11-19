package org.a2aproject.sdk.server.events;

import java.util.function.Consumer;

import org.a2aproject.sdk.spec.Event;

/**
 * Callback interface for MainEventBusProcessor events.
 * <p>
 * This interface is primarily intended for testing, allowing tests to synchronize
 * with the asynchronous MainEventBusProcessor. Production code should not rely on this.
 * </p>
 * Usage in tests:
 * <pre>
 * {@code
 * @Inject
 * MainEventBusProcessor processor;
 *
 * @BeforeEach
 * void setUp() {
 *     CountDownLatch latch = new CountDownLatch(3);
 *     processor.setCallback(new MainEventBusProcessorCallback() {
 *         public void onEventProcessed(String taskId, Event event) {
 *             latch.countDown();
 *         }
 *     });
 * }
 *
 * @AfterEach
 * void tearDown() {
 *     processor.setCallback(null); // Reset to NOOP
 * }
 * }
 * </pre>
 */
public interface MainEventBusProcessorCallback {

    /**
     * Admission seam for a committed webhook batch. Implementations receive the matching final
     * snapshot and every generation position before the processor invokes the push sender for any
     * event in the batch. The default implementation preserves existing callback implementations.
     */
    default void beforePushNotification(CommittedTaskBatch batch) {
        // No-op unless an ordered webhook admission coordinator is installed.
    }

    /**
     * Admits this committed batch for webhook delivery and controls sender invocation. An
     * implementation may retain {@code invokeSender} and run it after asynchronous admission;
     * it must return promptly so subscriber delivery and terminal cleanup can proceed. Deferred
     * admission failures are reported through {@code reportPostCommitFailure}.
     */
    default void admitPushNotifications(CommittedTaskBatch batch, Runnable invokeSender) {
        beforePushNotification(batch);
        invokeSender.run();
    }

    /**
     * Admission overload for asynchronous coordinators that need to report a later failure.
     * Existing implementations continue through the two-argument compatibility seam.
     */
    default void admitPushNotifications(CommittedTaskBatch batch, Runnable invokeSender,
            Consumer<Throwable> reportPostCommitFailure) {
        try {
            admitPushNotifications(batch, invokeSender);
        } catch (RuntimeException | Error failure) {
            reportPostCommitFailure.accept(failure);
        }
    }

    /** Reports a best-effort side-effect failure after task state has already committed. */
    default void onPostCommitFailure(String taskId, CommittedTaskBatch batch, Throwable failure) {
        // No-op unless the application records post-commit delivery failures.
    }

    /**
     * Called after an event has been fully processed (persisted, notification sent, distributed to children).
     *
     * @param taskId the task ID
     * @param event the event that was processed
     */
    void onEventProcessed(String taskId, Event event);

    /**
     * Called when a task reaches a final state (COMPLETED, FAILED, CANCELED, REJECTED).
     *
     * @param taskId the task ID that was finalized
     */
    void onTaskFinalized(String taskId);

    /**
     * No-op implementation that does nothing.
     * Used as the default callback to avoid null checks.
     */
    MainEventBusProcessorCallback NOOP = new MainEventBusProcessorCallback() {
        @Override
        public void onEventProcessed(String taskId, Event event) {
            // No-op
        }

        @Override
        public void onTaskFinalized(String taskId) {
            // No-op
        }
    };
}
