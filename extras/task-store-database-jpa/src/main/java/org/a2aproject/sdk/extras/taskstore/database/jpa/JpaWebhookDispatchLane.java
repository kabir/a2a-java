package org.a2aproject.sdk.extras.taskstore.database.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Durable coordination metadata only; webhook payloads remain volatile in dispatcher memory. */
@Entity
@Table(name = "a2a_webhook_dispatch_lanes")
public class JpaWebhookDispatchLane {

    @Id
    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(name = "writer_epoch", nullable = false)
    private long writerEpoch;

    @Column(name = "next_generation", nullable = false)
    private long nextGeneration;

    @Column(name = "holder_token")
    private String holderToken;

    @Column(name = "claimed_first_generation")
    private Long claimedFirstGeneration;

    @Column(name = "claimed_last_generation")
    private Long claimedLastGeneration;

    protected JpaWebhookDispatchLane() {
    }

    public JpaWebhookDispatchLane(String taskId, long writerEpoch, long nextGeneration) {
        this.taskId = taskId;
        this.writerEpoch = writerEpoch;
        this.nextGeneration = nextGeneration;
    }

    public String getTaskId() {
        return taskId;
    }

    public long getWriterEpoch() {
        return writerEpoch;
    }

    public void setWriterEpoch(long writerEpoch) {
        this.writerEpoch = writerEpoch;
    }

    public long getNextGeneration() {
        return nextGeneration;
    }

    public void setNextGeneration(long nextGeneration) {
        this.nextGeneration = nextGeneration;
    }

    public String getHolderToken() {
        return holderToken;
    }

    public void setHolderToken(String holderToken) {
        this.holderToken = holderToken;
    }

    public Long getClaimedFirstGeneration() {
        return claimedFirstGeneration;
    }

    public void setClaimedFirstGeneration(Long claimedFirstGeneration) {
        this.claimedFirstGeneration = claimedFirstGeneration;
    }

    public Long getClaimedLastGeneration() {
        return claimedLastGeneration;
    }

    public void setClaimedLastGeneration(Long claimedLastGeneration) {
        this.claimedLastGeneration = claimedLastGeneration;
    }
}
