-- Apply with the task-store JPA schema upgrade before activating the shared 1.1 dispatch profile.
CREATE TABLE a2a_webhook_dispatch_lanes (
    task_id VARCHAR(255) PRIMARY KEY,
    writer_epoch BIGINT NOT NULL,
    next_generation BIGINT NOT NULL,
    holder_token VARCHAR(36),
    claimed_first_generation BIGINT,
    claimed_last_generation BIGINT
);

CREATE TABLE a2a_webhook_dispatch_losses (
    loss_id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL,
    first_generation BIGINT NOT NULL,
    last_generation BIGINT NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    reason VARCHAR(512) NOT NULL
);

CREATE INDEX a2a_webhook_dispatch_loss_task
    ON a2a_webhook_dispatch_losses (task_id, first_generation);
