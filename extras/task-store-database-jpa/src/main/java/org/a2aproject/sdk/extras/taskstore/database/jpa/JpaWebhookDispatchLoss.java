package org.a2aproject.sdk.extras.taskstore.database.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Durable operational record for a retired range; it stores no webhook payload or retry data. */
@Entity
@Table(name = "a2a_webhook_dispatch_losses",
        indexes = @Index(name = "a2a_webhook_dispatch_loss_task", columnList = "task_id, first_generation"))
public class JpaWebhookDispatchLoss {

    @Id
    @Column(name = "loss_id", nullable = false, length = 36)
    private String lossId;

    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(name = "first_generation", nullable = false)
    private long firstGeneration;

    @Column(name = "last_generation", nullable = false)
    private long lastGeneration;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    protected JpaWebhookDispatchLoss() {
    }

    JpaWebhookDispatchLoss(String lossId, String taskId, long firstGeneration, long lastGeneration,
            Instant recordedAt, String reason) {
        this.lossId = lossId;
        this.taskId = taskId;
        this.firstGeneration = firstGeneration;
        this.lastGeneration = lastGeneration;
        this.recordedAt = recordedAt;
        this.reason = reason;
    }

    String getTaskId() {
        return taskId;
    }

    long getFirstGeneration() {
        return firstGeneration;
    }

    long getLastGeneration() {
        return lastGeneration;
    }

    Instant getRecordedAt() {
        return recordedAt;
    }

    String getReason() {
        return reason;
    }
}
