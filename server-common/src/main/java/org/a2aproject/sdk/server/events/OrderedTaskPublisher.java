package org.a2aproject.sdk.server.events;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.a2aproject.sdk.server.tasks.TaskCommitSlot;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * Orders local publication by the reservation order assigned inside an atomic task mutation.
 *
 * <p>Reservation is bounded local bookkeeping and may run inside the store's atomic boundary.
 * Delivery always runs after commit and outside the publisher's queue monitor.</p>
 */
public final class OrderedTaskPublisher {

    private final ConcurrentHashMap<String, TaskQueue> queues = new ConcurrentHashMap<>();

    /** Records an ordered placeholder. This method is safe for an atomic-store reservation callback. */
    public void reserve(TaskCommitSlot slot) {
        Assert.checkNotNullParam("slot", slot);
        if (slot.isCancelled() || slot.isPublishable()) {
            throw new IllegalArgumentException("A publication slot must still be reserved");
        }
        queues.compute(slot.taskId(), (taskId, current) -> {
            TaskQueue queue = current == null ? new TaskQueue() : current;
            synchronized (queue) {
                if (queue.bySlot.containsKey(slot)) {
                    throw new IllegalStateException("Publication slot was already reserved");
                }
                Entry entry = new Entry(slot);
                queue.entries.addLast(entry);
                queue.bySlot.put(slot, entry);
            }
            return queue;
        });
    }

    /**
     * Attaches the committed result to its placeholder and returns a stage completed after the
     * whole batch has been delivered. A later reservation can become ready first, but it cannot
     * overtake an earlier placeholder.
     */
    public CompletionStage<Void> publish(TaskCommitSlot slot, CommittedTaskBatch batch,
            Consumer<CommittedTaskBatch> delivery) {
        Assert.checkNotNullParam("slot", slot);
        Assert.checkNotNullParam("batch", batch);
        Assert.checkNotNullParam("delivery", delivery);
        if (!slot.isPublishable()) {
            throw new IllegalStateException("Cannot publish before the store makes the slot publishable");
        }
        if (!slot.taskId().equals(batch.taskId()) || !slot.revision().equals(batch.snapshot().revision())) {
            throw new IllegalArgumentException("Committed batch does not match its publication slot");
        }
        TaskQueue queue = queues.get(slot.taskId());
        if (queue == null) {
            throw new IllegalStateException("No publication reservation exists for the slot");
        }
        Entry entry;
        synchronized (queue) {
            entry = queue.bySlot.get(slot);
            if (entry == null) {
                throw new IllegalStateException("No publication reservation exists for the slot");
            }
            if (entry.batch != null) {
                throw new IllegalStateException("Publication slot already has a committed batch");
            }
            entry.batch = batch;
            entry.delivery = delivery;
        }
        drain(slot.taskId(), queue);
        return entry.completion;
    }

    /**
     * Removes a failed reservation so it cannot leave a permanent hole. This suppresses local
     * publication only; it makes no assertion about whether storage committed.
     */
    public void discard(TaskCommitSlot slot) {
        Assert.checkNotNullParam("slot", slot);
        TaskQueue queue = queues.get(slot.taskId());
        if (queue == null) {
            return;
        }
        Entry entry;
        synchronized (queue) {
            entry = queue.bySlot.remove(slot);
            if (entry == null) {
                return;
            }
            if (entry.delivering) {
                queue.bySlot.put(slot, entry);
                throw new IllegalStateException("Cannot discard a batch while it is being delivered");
            }
            queue.entries.remove(entry);
        }
        entry.completion.completeExceptionally(new IllegalStateException("Committed batch was not published"));
        drain(slot.taskId(), queue);
        removeIfEmpty(slot.taskId(), queue);
    }

    private void drain(String taskId, TaskQueue queue) {
        synchronized (queue) {
            if (queue.draining) {
                return;
            }
            queue.draining = true;
        }
        while (true) {
            Entry entry;
            Entry cancelledEntry = null;
            synchronized (queue) {
                entry = queue.entries.peekFirst();
                if (entry == null) {
                    queue.draining = false;
                    break;
                }
                if (entry.slot.isCancelled()) {
                    cancelledEntry = queue.entries.removeFirst();
                    queue.bySlot.remove(entry.slot);
                } else if (!entry.slot.isPublishable() || entry.batch == null || entry.delivery == null) {
                    queue.draining = false;
                    return;
                } else {
                    entry.delivering = true;
                }
            }

            if (cancelledEntry != null) {
                cancelledEntry.completion.completeExceptionally(
                        new IllegalStateException("Store cancelled the publication reservation"));
                continue;
            }

            Throwable failure = null;
            try {
                Consumer<CommittedTaskBatch> delivery = Objects.requireNonNull(entry.delivery);
                CommittedTaskBatch batch = Objects.requireNonNull(entry.batch);
                delivery.accept(batch);
            } catch (Throwable t) {
                failure = t;
            }

            synchronized (queue) {
                queue.entries.removeFirst();
                queue.bySlot.remove(entry.slot);
                entry.delivering = false;
            }
            if (failure == null) {
                entry.completion.complete(null);
            } else {
                entry.completion.completeExceptionally(failure);
            }
        }
        removeIfEmpty(taskId, queue);
    }

    private void removeIfEmpty(String taskId, TaskQueue queue) {
        queues.computeIfPresent(taskId, (id, current) -> {
            if (current != queue) {
                return current;
            }
            synchronized (queue) {
                return queue.entries.isEmpty() && !queue.draining ? null : queue;
            }
        });
    }

    private static final class TaskQueue {

        private final Deque<Entry> entries = new ArrayDeque<>();
        private final IdentityHashMap<TaskCommitSlot, Entry> bySlot = new IdentityHashMap<>();
        private boolean draining;
    }

    private static final class Entry {

        private final TaskCommitSlot slot;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private @Nullable CommittedTaskBatch batch;
        private @Nullable Consumer<CommittedTaskBatch> delivery;
        private boolean delivering;

        private Entry(TaskCommitSlot slot) {
            this.slot = slot;
        }
    }
}
