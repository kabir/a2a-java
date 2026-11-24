package org.a2aproject.sdk.extras.taskstore.database.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

/**
 * Allocates storage revisions. A revision is {@code (block << 20) + sequence}; a task row takes a fresh
 * block when it is created (or when its block is exhausted) and increments within it. Because a
 * recreated task ID always starts in a never-used block, a deleted task's revision is never reused.
 *
 * <p>Invoke through the CDI proxy only: {@link #newRevision()} must run inside the caller's
 * transaction so that the counter row lock and the task write commit or roll back together.</p>
 */
@ApplicationScoped
public class JpaTaskRevisionAllocator {

    static final int BLOCK_BITS = 20;
    static final long SEQUENCE_MASK = (1L << BLOCK_BITS) - 1;

    @PersistenceContext(unitName = "a2a-java")
    EntityManager em;

    @Inject
    JpaTaskRevisionAllocator self;

    private volatile boolean counterReady;

    /** Returns the first revision of a never-before-used block. */
    @Transactional(Transactional.TxType.MANDATORY)
    public long newRevision() {
        ensureCounterReady();
        long block = allocateBlock();
        return (block << BLOCK_BITS) + 1;
    }

    /** Returns the revision following {@code current}, taking a new block when the current one is full. */
    @Transactional(Transactional.TxType.MANDATORY)
    public long successor(long current) {
        return (current & SEQUENCE_MASK) == SEQUENCE_MASK ? newRevision() : current + 1;
    }

    /**
     * Creates the single counter row in its own transaction. It must run before the caller's transaction
     * locks the counter: inserting it while the caller already holds a gap lock from an {@code UPDATE} that
     * matched no row (for example MySQL REPEATABLE READ) would block on the caller itself.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void ensureCounter() {
        if (em.find(JpaTaskRevisionCounter.class, JpaTaskRevisionCounter.SINGLETON_ID) == null) {
            em.persist(new JpaTaskRevisionCounter(JpaTaskRevisionCounter.SINGLETON_ID, 0));
            em.flush();
        }
    }

    private void ensureCounterReady() {
        if (counterReady) {
            return;
        }
        try {
            self.ensureCounter();
        } catch (RuntimeException concurrentInitialization) {
            // Another writer may have created the row first; the second call only reads it.
            self.ensureCounter();
        }
        counterReady = true;
    }

    private long allocateBlock() {
        if (bump() == 0) {
            throw new IllegalStateException("Task revision counter row is missing");
        }
        return em.createQuery("SELECT c.nextBlock FROM JpaTaskRevisionCounter c WHERE c.id = :id", Long.class)
                .setParameter("id", JpaTaskRevisionCounter.SINGLETON_ID).getSingleResult();
    }

    private int bump() {
        return em.createQuery("UPDATE JpaTaskRevisionCounter c SET c.nextBlock = c.nextBlock + 1 WHERE c.id = :id")
                .setParameter("id", JpaTaskRevisionCounter.SINGLETON_ID).executeUpdate();
    }
}
