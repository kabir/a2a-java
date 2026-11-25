---
title: Replicated Queue Manager
description: Distributed QueueManager implementation for the A2A Java SDK that replicates events across multiple server instances using message brokers like Apache Kafka.
layout: page
---

# Replicated Queue Manager

Replaces the default `InMemoryQueueManager` with a queue manager that replicates events across multiple A2A server instances. Required for multi-instance (load-balanced) deployments where all nodes must see every event.

## Architecture

The replicated queue manager has two core components:

- **`ReplicatedQueueManager`** — wraps the default `InMemoryQueueManager` and handles event replication. It is annotated to take precedence over the default, making it a drop-in replacement.
- **`ReplicationStrategy`** — interface for pluggable replication implementations. A `ReplicationStrategy` **must** be provided when using `ReplicatedQueueManager`.

The provided implementation, `ReactiveMessagingReplicationStrategy`, uses MicroProfile Reactive Messaging with message brokers like Apache Kafka.

## Event Flow

1. An event is generated in the A2A system (e.g., `TaskStatusUpdateEvent`) and enqueued locally
2. A `ReplicationHook` intercepts the event and sends it to the replication strategy
3. The replication strategy serializes the event and sends it to the message broker
4. Other A2A instances receive the event from the broker
5. The received event is deserialized and enqueued in each instance's local queue

### Replicated Event Types

The system replicates these event types while preserving their specific types:

- `TaskStatusUpdateEvent` — task state changes (SUBMITTED, COMPLETED, etc.)
- `TaskArtifactUpdateEvent` — task artifact changes
- `Message` — chat messages and responses
- `Task` — complete task objects
- `A2AError` — error events

Events are serialized using Gson with member-name wrapping (the event type name becomes the JSON key wrapping the event data) to preserve type information:

```json
{
  "taskId": "task-123",
  "event": {
    "statusUpdate": {
      "taskId": "task-123",
      "status": {
        "state": "completed"
      },
      "kind": "status-update"
    }
  }
}
```

## Quick Start

### 1. Add Dependencies

```xml
<!-- Core (required) -->
<dependency>
    <groupId>org.a2aproject.sdk</groupId>
    <artifactId>a2a-java-queue-manager-replicated-core</artifactId>
</dependency>

<!-- MicroProfile Reactive Messaging strategy (required) -->
<dependency>
    <groupId>org.a2aproject.sdk</groupId>
    <artifactId>a2a-java-queue-manager-replication-mp-reactive</artifactId>
</dependency>
```

### 2. Configure Kafka

Add to your `application.properties`:

```properties
# Outgoing (QueueManager -> Kafka)
mp.messaging.outgoing.replicated-events-out.connector=smallrye-kafka
mp.messaging.outgoing.replicated-events-out.topic=replicated-events
mp.messaging.outgoing.replicated-events-out.value.serializer=org.apache.kafka.common.serialization.StringSerializer

# Incoming (Kafka -> QueueManager)
mp.messaging.incoming.replicated-events-in.connector=smallrye-kafka
mp.messaging.incoming.replicated-events-in.topic=replicated-events
mp.messaging.incoming.replicated-events-in.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
```

The channel names `replicated-events-in` and `replicated-events-out` correspond to the `@Incoming` and `@Channel` annotations in the `ReactiveMessagingReplicationStrategy`.

### 3. Create the Kafka Topic

```bash
kafka-topics.sh --create --topic replicated-events \
    --bootstrap-server localhost:9092 \
    --partitions 3 --replication-factor 1
```

## Configuration

### Kafka Settings

```properties
# Kafka broker configuration
kafka.bootstrap.servers=kafka-broker-1:9092,kafka-broker-2:9092

# Topic configuration
mp.messaging.outgoing.replicated-events-out.topic=my-replicated-events
mp.messaging.incoming.replicated-events-in.topic=my-replicated-events

# Consumer group (all A2A instances should share the same group)
mp.messaging.incoming.replicated-events-in.group.id=a2a-instance-group

# Consumer behavior
mp.messaging.incoming.replicated-events-in.auto.offset.reset=earliest

# Reliability configuration
mp.messaging.outgoing.replicated-events-out.acks=all
mp.messaging.outgoing.replicated-events-out.retries=3

# Performance tuning
mp.messaging.outgoing.replicated-events-out.batch.size=16384
mp.messaging.incoming.replicated-events-in.max.poll.records=500

# Serialization (key serialization needed for partition-by-taskId strategy)
mp.messaging.outgoing.replicated-events-out.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.incoming.replicated-events-in.key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
```

## Upgrading to A2A 1.1: broker fencing

Upgrading a deployment that shares tasks across nodes is a coordinated drain-and-upgrade, never a rolling upgrade of mixed
old and new writers. The task-store half of the procedure (drain, distinct database identities, database `REVOKE`, writer
epoch activation, baseline migration, rollback) is in [Storage & Persistence](storage.md#upgrading-shared-task-storage-to-a2a-11-drain-fence-migrate).
For the broker:

1. Stop all old replication producers and consumers before migrating task data.
2. Give the new fleet its own broker identity and topic access (for example a new Kafka principal with ACLs); revoke the
   old principal's produce permission on the replication topic. Removing nodes from routing does not fence their producers.
3. Do not let a delayed record from an old producer reach new nodes: use a new topic or consumer group for the new fleet, or
   drain the old topic completely first. Once a writer epoch is active, `JpaDatabaseTaskStore.save` refuses to overwrite a
   migrated task with a lower generation, but that is a last-resort guard, not broker fencing.
4. Rolling back requires an outage, a consistent pre-upgrade task/configuration snapshot and a matching broker boundary;
   changes after the upgrade are lost.

This module does not itself enforce broker permissions or reject stale producers; that enforcement is a deployment
precondition and is verified separately from the database fence.

## Committed replication (A2A 1.1 writer epoch)

Setting `a2a.task-store.writer-epoch` (the same property the JPA task store uses) switches the node to committed replication.
Without it the module behaves exactly as before (legacy per-event replication and a status-update-plus-poison-pill
finalization), so legacy-only deployments are unchanged.

Under a writer epoch:

- Only a batch the task store has already committed is replicated, once per batch, as one epoch-stamped envelope that carries the
  original ordered events (with their canonical generations) and the final snapshot with its store revision. Receivers never
  call `mutate` or `save` for it: the shared store already holds that result. They only make it visible to local subscribers.
- A terminal batch is forwarded after the database commit exactly as committed (no synthesized status update or extra
  generation), followed by a poison pill that receivers apply only after that terminal batch. If a final task is saved without a
  committed batch under an epoch, only the poison pill is sent and a warning is logged.
- Receivers drop an own-broadcast echo, an unversioned record (for example one delayed from a pre-migration producer), a record
  of another epoch, a bare event, and an incomplete or non-consecutive envelope. An identical repeat is shown once, a same-range
  payload that differs is rejected, and an early batch is buffered (at most 64 per task) until its predecessor arrives.
  A permanently missing predecessor is not recovered here; that is bounded publication-gap handling, not replay.
  The first batch a node sees for a task is its starting point: if a later batch arrives first, an earlier one arriving
  afterwards is dropped as stale (observers never see generations go backwards), and a poison pill that arrives before any
  batch closes the queue immediately (a terminal batch arriving behind it is dropped as stale, so it cannot recreate a queue).
  If delivering one batch throws, later buffered batches and the deferred poison pill still run, and the first failure is
  rethrown; the failed batch is not retried by the receiver. A batch for a task this node considers expired does not create a queue unless it is
  terminal.
- Remote copies never dispatch webhooks. Only the node that committed the batch hands it to webhook dispatch.
- `ReplicationStrategy` gains a default `send(ReplicatedEventQueueItem)`; a custom strategy must implement it to run under an
  epoch. The reactive messaging strategy does.
- Starting with a writer epoch on the in-memory task store fails fast: committed cluster replication needs a shared store.

Limits: the epoch stamp is self-declared by the sender, so this is receiver-side validation, not broker authentication or
fencing (see above). The broker may redeliver, but the receiver does not retry: a batch whose local delivery fails is still
recorded as seen, so a redelivery is dropped. Ordering and deduplication are in-process and best-effort; receiver state is not
persisted, so a restarted node starts a task from its first received batch, and per-task state is bounded (older tasks are
evicted). A terminal batch is replicated only by a store that publishes the terminal batch with its finalization event (the
JPA store does); a custom store that does not will not replicate terminal batches. There is no exactly-once delivery, replay or
cursor recovery.

## Shared ordered webhook dispatch

When the JPA task store and replicated queue manager use a shared writer epoch, originating webhook attempts pass through
`a2a_webhook_dispatch_lanes`. Task creation initializes its cursor at generation 1 in the task transaction. `migrateAll()`
creates missing lanes from each accepted task generation plus one, including tasks already marked by the earlier task-store
migration. A batch must claim its exact next contiguous range and epoch before any HTTP request starts. Terminal finalization
and ordinary bus admission carry the same committed batch identity through that gate; remote copies only fan out to local
subscribers. A verified older-version delivery-only Task summary may hold a zero-width claim at the boundary after its
snapshot generation; it never moves the canonical cursor. Per-configuration 0.3, 1.0 and 1.1 projection remains in the sender.

The guarantee is ordered SDK attempt start per task, not exactly-once delivery, server receipt order or durable webhook
delivery. Payloads live in bounded process memory (up to 256 pending events or 4 MiB per task) and may be dropped with a
recorded loss range. The sender allows 30 seconds for one ordered batch's outbound starts; a missing unclaimed generation
may be retired after 10 seconds. A sender stage remains attached to its lane until its actual worker exits. If an outbound
call is interrupted and its transport cannot prove quiescence, the claim remains held and later generations wait. Pending
payloads and attempt results are lost on process failure; there is no payload outbox or automatic retry.

Loss ranges still relevant to the lane cursor are coordination metadata and are retained without a count cap; sparse
ranges can accumulate while an earlier claim is held. Only historical diagnostics are capped at 256 rows per task. This
keeps known losses authoritative without expanding the volatile payload queue.

An abandoned claim can block that task's webhooks indefinitely. Recovery is an explicit operator action: stop or fence the
old process and verify that all queued and running outbound work is quiescent before conditionally retiring the exact
claimed range and moving the lane to the active writer epoch. Database or broker credential revocation alone does not prove
an old HTTP worker has stopped. Task mutations and subscriber delivery continue independently while webhook dispatch is
blocked. See [Storage & Persistence](storage.md#webhook-dispatch-lane-and-availability) before enabling the shared profile.

## Production Considerations

### Kafka Partitioning Strategy

How you partition your Kafka topic significantly impacts scalability and correctness.

#### Single Partition

```bash
kafka-topics.sh --create --topic replicated-events \
    --bootstrap-server localhost:9092 \
    --partitions 1 --replication-factor 1
```

- Guarantees global event ordering
- Suitable for development, testing, and low-throughput production (<1000 events/sec)
- Cannot parallelize consumption

#### Partition by Task ID (Recommended)

```bash
kafka-topics.sh --create --topic replicated-events \
    --bootstrap-server localhost:9092 \
    --partitions 10 --replication-factor 3
```

The `ReactiveMessagingReplicationStrategy` sends the `taskId` as the Kafka message key, so Kafka automatically partitions by task ID.

- **Per-task ordering guarantee**: all events for a single task go to the same partition
- **Horizontal scalability**: different tasks can be processed in parallel across partitions
- **Consumer parallelism**: multiple consumer instances can process different partitions concurrently
- **Rule of thumb**: number of partitions >= number of A2A instances

### Consumer Group Configuration

When using multiple partitions, ensure all A2A instances belong to the same consumer group:

```properties
mp.messaging.incoming.replicated-events-in.group.id=a2a-instance-group
```

This ensures each partition is consumed by exactly one instance, and events for the same task always reach the same instance.

### Poison Pill Cleanup

When a task reaches a final state (COMPLETED, FAILED, CANCELED), all nodes must terminate their event consumers for that task. This is handled automatically via a `QueueClosedEvent` (poison pill) that is replicated to all nodes.

The mechanism uses **transaction-aware CDI events**: the poison pill is only sent AFTER the final task state is committed to the database (`@Observes(during = TransactionPhase.AFTER_SUCCESS)`), eliminating race conditions.

```java
// JpaDatabaseTaskStore fires CDI event after persist
if (task.status().state().isFinal()) {
    taskFinalizedEvent.fire(new TaskFinalizedEvent(task.id(), task));
}

// ReplicatedQueueManager observes it after transaction commit
public void onTaskFinalized(
        @Observes(during = TransactionPhase.AFTER_SUCCESS) TaskFinalizedEvent event) {
    QueueClosedEvent closedEvent = new QueueClosedEvent(event.getTaskId());
    replicationStrategy.send(event.getTaskId(), closedEvent);
}
```

No configuration is required — the poison pill mechanism works automatically when using `JpaDatabaseTaskStore` together with `ReplicatedQueueManager`.

### Grace Period

A configurable grace period prevents queue recreation for tasks that were recently finalized, accommodating Kafka consumer lag and network delays:

```properties
# Grace period for handling late-arriving events (default: 15 seconds)
a2a.replication.grace-period-seconds=15
```

When a replicated event arrives after a task is finalized, the system checks if the task is still within the grace period before creating a new queue. Increase the grace period in high-latency networks; decrease it for systems with low latency and high task turnover.

## Alternative Message Brokers

While Kafka is the primary tested broker, MicroProfile Reactive Messaging supports other brokers:

### Apache Pulsar

```properties
mp.messaging.outgoing.replicated-events-out.connector=smallrye-pulsar
mp.messaging.incoming.replicated-events-in.connector=smallrye-pulsar
pulsar.client.serviceUrl=pulsar://localhost:6650
```

### AMQP (RabbitMQ, etc.)

```properties
mp.messaging.outgoing.replicated-events-out.connector=smallrye-amqp
mp.messaging.incoming.replicated-events-in.connector=smallrye-amqp
amqp-host=localhost
amqp-port=5672
```

> **Note:** Alternative message brokers have not been tested in this project yet.

### WildFly/Jakarta EE Servers

For non-Quarkus environments, configure MicroProfile Reactive Messaging according to your application server's documentation.

## Custom Replication Strategies

You can implement your own `ReplicationStrategy` if MicroProfile Reactive Messaging does not fit your environment:

```java
@ApplicationScoped
public class CustomReplicationStrategy implements ReplicationStrategy {

    @Override
    public void send(String taskId, Event event) {
        // Your replication logic (e.g., database, REST API, etc.)
    }
}
```

## Monitoring

### Logging

Enable debug logging to monitor replication activity:

```properties
quarkus.log.category."org.a2aproject.sdk.extras.queuemanager.replicated".level=DEBUG
quarkus.log.category."org.a2aproject.sdk.extras.taskstore.database.jpa".level=DEBUG
```

Example log entries:

```
Task abc-123 is in final state, firing TaskFinalizedEvent
Task abc-123 finalized - sending poison pill (QueueClosedEvent) after transaction commit
Skipping replicated event for inactive task abc-123
```

### Health Checks

When using Quarkus, the module integrates with MicroProfile Health:

```properties
quarkus.messaging.kafka.health.timeout=5s
```

### CDI Event Monitoring

You can monitor replicated events by observing CDI events:

```java
@ApplicationScoped
public class ReplicationMonitor {

    public void onReplicatedEvent(@Observes ReplicatedEventQueueItem replicatedEvent) {
        LOGGER.info("Received replicated event for task: " + replicatedEvent.getTaskId());
    }
}
```
