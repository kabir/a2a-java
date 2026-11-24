package org.a2aproject.sdk.extras.taskstore.database.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-row storage writer epoch. Its presence means the shared task storage has been migrated and
 * declared writable only by 1.1 writers of this epoch. It is a cooperative check among new binaries;
 * old binaries ignore it, so exclusion of old writers must be enforced with database privileges.
 */
@Entity
@Table(name = "a2a_writer_epoch")
public class JpaWriterEpoch {

    static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "epoch_id")
    private int id;

    @Column(name = "epoch", nullable = false)
    private long epoch;

    public JpaWriterEpoch() {
    }

    JpaWriterEpoch(int id, long epoch) {
        this.id = id;
        this.epoch = epoch;
    }

    public int getId() {
        return id;
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }
}
