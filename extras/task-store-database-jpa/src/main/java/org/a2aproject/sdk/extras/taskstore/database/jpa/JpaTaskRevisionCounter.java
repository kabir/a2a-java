package org.a2aproject.sdk.extras.taskstore.database.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-row allocator of storage-revision blocks.
 *
 * <p>A task row gets a fresh block when it is created and increments within that block, so deleting
 * and recreating a task ID can never reuse a revision a reader may still hold. The counter row is only
 * locked while a block is allocated (task creation or block exhaustion), not on every update.</p>
 */
@Entity
@Table(name = "a2a_task_revision_counter")
public class JpaTaskRevisionCounter {

    static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "counter_id")
    private int id;

    @Column(name = "next_block", nullable = false)
    private long nextBlock;

    public JpaTaskRevisionCounter() {
    }

    JpaTaskRevisionCounter(int id, long nextBlock) {
        this.id = id;
        this.nextBlock = nextBlock;
    }

    public int getId() {
        return id;
    }

    public long getNextBlock() {
        return nextBlock;
    }

    public void setNextBlock(long nextBlock) {
        this.nextBlock = nextBlock;
    }
}
