package org.a2aproject.sdk.extras.queuemanager.replicated.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.a2aproject.sdk.server.events.CommittedTaskBatch;
import org.jspecify.annotations.Nullable;

/**
 * Orders, de-duplicates and screens committed replicated batches per task before they are made visible.
 *
 * <p>A batch is delivered when it directly follows the last delivered one. The first batch seen for a task is the
 * starting point (this node cannot know what preceded it; the shared store already holds it). Later batches that
 * arrive early are buffered, up to {@link #MAX_PENDING}, and released in order. An identical repeat is dropped; a
 * batch with the same range but different content, or one overlapping a delivered or buffered range differently,
 * is rejected. A batch older than the delivered position that is not a retained repeat is stale and dropped, so
 * observers only ever see generations move forward. This class does not recover a missing batch: a permanent gap
 * stays buffered and is the concern of bounded publication-gap handling (T14). State is bounded per task and
 * across tasks, and is process-local.</p>
 */
final class CommittedBatchSequencer {

    /** Outcome of offering one batch. */
    enum Outcome { DELIVERED, DUPLICATE, STALE, BUFFERED, CONFLICT, OVERFLOW }

    static final int MAX_PENDING = 64;
    static final int MAX_DELIVERED_RETAINED = 64;
    static final int MAX_TASKS = 4096;

    private final Map<String, TaskSequence> tasks = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TaskSequence> eldest) {
            return size() > MAX_TASKS;
        }
    };

    Outcome offer(CommittedTaskBatch batch, Runnable delivery) {
        TaskSequence sequence;
        synchronized (tasks) {
            sequence = tasks.computeIfAbsent(batch.taskId(), id -> new TaskSequence());
        }
        synchronized (sequence) {
            return sequence.offer(batch, delivery);
        }
    }

    /**
     * Runs {@code action} once every batch up to {@code generation} has been delivered for the task, immediately
     * when that is already true or when no batch has been seen for the task (this node then has no ordered state
     * to wait for, and holding the close would leave subscribers waiting forever). A batch that arrives later at or
     * below that generation is dropped as stale, so it cannot create a queue nobody will close. At most one deferred
     * action is kept per task; a newer one replaces it.
     */
    void runAfterGeneration(String taskId, long generation, Runnable action) {
        TaskSequence sequence;
        synchronized (tasks) {
            sequence = tasks.computeIfAbsent(taskId, id -> new TaskSequence());
        }
        boolean runNow;
        synchronized (sequence) {
            runNow = sequence.lastDelivered < 0 || sequence.lastDelivered >= generation;
            if (runNow) {
                sequence.closedThrough = Math.max(sequence.closedThrough, generation);
            } else {
                sequence.deferredGeneration = generation;
                sequence.deferred = action;
            }
        }
        if (runNow) {
            action.run();
        }
    }

    int pendingCount(String taskId) {
        TaskSequence sequence;
        synchronized (tasks) {
            sequence = tasks.get(taskId);
        }
        if (sequence == null) {
            return 0;
        }
        synchronized (sequence) {
            return sequence.pending.size();
        }
    }

    private record Entry(CommittedTaskBatch batch, Runnable delivery) {
    }

    private static final class TaskSequence {
        private long lastDelivered = -1;
        private long closedThrough = -1;
        private final Map<Long, CommittedTaskBatch> delivered = new LinkedHashMap<>();
        private final TreeMap<Long, Entry> pending = new TreeMap<>();
        private long deferredGeneration;
        private @Nullable Runnable deferred;

        Outcome offer(CommittedTaskBatch batch, Runnable delivery) {
            long first = firstOf(batch);
            long last = lastOf(batch);
            Outcome screened = screen(batch, first, last);
            if (screened != null) {
                return screened;
            }
            if (lastDelivered >= 0 && first > lastDelivered + 1) {
                Entry buffered = pending.get(first);
                if (buffered != null) {
                    return buffered.batch().equals(batch) ? Outcome.DUPLICATE : Outcome.CONFLICT;
                }
                if (pending.size() >= MAX_PENDING) {
                    return Outcome.OVERFLOW;
                }
                for (Entry other : pending.values()) {
                    if (first <= lastOf(other.batch()) && firstOf(other.batch()) <= last) {
                        return Outcome.CONFLICT;
                    }
                }
                pending.put(first, new Entry(batch, delivery));
                return Outcome.BUFFERED;
            }
            List<Entry> ready = new ArrayList<>();
            ready.add(new Entry(batch, delivery));
            long cursor = last;
            Entry next;
            while ((next = pending.remove(cursor + 1)) != null) {
                ready.add(next);
                cursor = lastOf(next.batch());
            }
            RuntimeException failure = null;
            Error fatal = null;
            for (Entry entry : ready) {
                // Record before delivering: a failed delivery must not be re-run by a repeat.
                record(entry.batch());
                try {
                    entry.delivery().run();
                } catch (RuntimeException e) {
                    // Keep ordering moving: later ready batches are still delivered; the first failure is rethrown.
                    if (failure == null) {
                        failure = e;
                    }
                } catch (Error e) {
                    if (fatal == null) {
                        fatal = e;
                    }
                }
            }
            Runnable action = deferred;
            if (action != null && lastDelivered >= deferredGeneration) {
                deferred = null;
                try {
                    action.run();
                } catch (RuntimeException e) {
                    if (failure == null) {
                        failure = e;
                    }
                } catch (Error e) {
                    if (fatal == null) {
                        fatal = e;
                    }
                }
            }
            if (fatal != null) {
                throw fatal;
            }
            if (failure != null) {
                throw failure;
            }
            return Outcome.DELIVERED;
        }

        private @Nullable Outcome screen(CommittedTaskBatch batch, long first, long last) {
            for (CommittedTaskBatch seen : delivered.values()) {
                if (firstOf(seen) == first && lastOf(seen) == last) {
                    return seen.equals(batch) ? Outcome.DUPLICATE : Outcome.CONFLICT;
                }
                if (first <= lastOf(seen) && firstOf(seen) <= last) {
                    return Outcome.CONFLICT;
                }
            }
            if ((lastDelivered >= 0 && last <= lastDelivered) || last <= closedThrough) {
                return Outcome.STALE;
            }
            return null;
        }

        private void record(CommittedTaskBatch batch) {
            lastDelivered = lastOf(batch);
            delivered.put(firstOf(batch), batch);
            var iterator = delivered.entrySet().iterator();
            while (delivered.size() > MAX_DELIVERED_RETAINED && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        private static long firstOf(CommittedTaskBatch batch) {
            return batch.identity().firstGeneration();
        }

        private static long lastOf(CommittedTaskBatch batch) {
            return batch.identity().lastGeneration();
        }
    }
}
