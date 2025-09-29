# A2A Java SDK - Replicated Queue Manager

This module provides a replicated implementation of the `QueueManager` interface that enables event replication across multiple A2A instances using message brokers like Apache Kafka. It ensures that events generated in one A2A instance are propagated to other instances for distributed operation.

The replication works by intercepting events as they are enqueued and sending them to a message broker. Events received from the broker are then processed by the local A2A instance, maintaining consistency across the distributed system.

## Architecture

The main components in the replicated queue manager are:

- **[`ReplicatedQueueManager`](./core/src/main/java/io/a2a/extras/queuemanager/replicated/core/ReplicatedQueueManager.java)**: Core queue manager that wraps the default `InMemoryQueueManager` and handles event replication.
- **[`ReplicationStrategy`](./core/src/main/java/io/a2a/extras/queuemanager/replicated/core/ReplicationStrategy.java)**: Interface for different replication implementations. If `ReplicatedQueueManager` is used, a `ReplicationStrategy` **must** be provided.

Currently, one implementation is provided: [`ReactiveMessagingReplicationStrategy`](./replication-mp-reactive/src/main/java/io/a2a/extras/queuemanager/replicated/mp_reactive/ReactiveMessagingReplicationStrategy.java), which uses MicroProfile Reactive Messaging with message brokers like Apache Kafka.

## Quick Start

This section will get you up and running quickly with a `ReplicatedQueueManager` using the `ReactiveMessagingReplicationStrategy` set up to use Kafka as the message broker.

### 1. Add Dependencies

#### Core Module (Required)

Add the core replicated queue manager module to your project's `pom.xml`:

```xml
<dependency>
    <groupId>io.github.a2asdk</groupId>
    <artifactId>a2a-java-queue-manager-replicated-core</artifactId>
    <version>${a2a.version}</version>
</dependency>
```

The `ReplicatedQueueManager` is annotated in such a way that it should take precedence over the default `InMemoryQueueManager`. Hence, it is a drop-in replacement.

#### Replication Strategy Implementation (Required)

You must also include a replication strategy implementation. Currently, we provide one implementation using MicroProfile Reactive Messaging:

```xml
<dependency>
    <groupId>io.github.a2asdk</groupId>
    <artifactId>a2a-java-queue-manager-replication-mp-reactive</artifactId>
    <version>${a2a.version}</version>
</dependency>
```

### 2. Basic Configuration

Add to your `application.properties`:

```properties
# Configure the outgoing channel (QueueManager -> Kafka)
mp.messaging.outgoing.replicated-events-out.connector=smallrye-kafka
mp.messaging.outgoing.replicated-events-out.topic=replicated-events
mp.messaging.outgoing.replicated-events-out.value.serializer=org.apache.kafka.common.serialization.StringSerializer

# Configure the incoming channel (Kafka -> QueueManager)
mp.messaging.incoming.replicated-events-in.connector=smallrye-kafka
mp.messaging.incoming.replicated-events-in.topic=replicated-events
mp.messaging.incoming.replicated-events-in.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
```

The channel names `replicated-events-in` and `replicated-events-out` correspond to the `@Incoming` and `@Channel` annotations in the ReactiveMessagingReplicationStrategy.

### 3. Kafka Topic Setup

Ensure your Kafka broker has the topic configured:

```bash
# Create the replicated-events topic
kafka-topics.sh --create --topic replicated-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

## Configuration

### Kafka Configuration

#### Basic Settings

```properties
# Kafka broker configuration
kafka.bootstrap.servers=kafka-broker-1:9092,kafka-broker-2:9092

# Topic configuration
mp.messaging.outgoing.replicated-events-out.topic=my-replicated-events
mp.messaging.incoming.replicated-events-in.topic=my-replicated-events

# Consumer behavior
mp.messaging.incoming.replicated-events-in.auto.offset.reset=earliest
```

#### Advanced Settings

```properties
# Consumer group configuration (important for multiple A2A instances)
mp.messaging.incoming.replicated-events-in.group.id=a2a-instance-group

# Reliability configuration
mp.messaging.outgoing.replicated-events-out.acks=all
mp.messaging.outgoing.replicated-events-out.retries=3

# Performance tuning
mp.messaging.outgoing.replicated-events-out.batch.size=16384
mp.messaging.incoming.replicated-events-in.max.poll.records=500

# Serialization configuration
mp.messaging.outgoing.replicated-events-out.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.incoming.replicated-events-in.key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
```

### Alternative Message Brokers

While Kafka is the primary tested message broker, Quarkus Reactive Messaging supports other brokers:

#### Apache Pulsar

```properties
mp.messaging.outgoing.replicated-events-out.connector=smallrye-pulsar
mp.messaging.incoming.replicated-events-in.connector=smallrye-pulsar
pulsar.client.serviceUrl=pulsar://localhost:6650
```

#### AMQP (RabbitMQ, etc.)

```properties
mp.messaging.outgoing.replicated-events-out.connector=smallrye-amqp
mp.messaging.incoming.replicated-events-in.connector=smallrye-amqp
amqp-host=localhost
amqp-port=5672
```

**Note**: Alternative message brokers have not been tested in this project yet.

### WildFly/Jakarta EE Servers

For non-Quarkus environments, you'll need to configure MicroProfile Reactive Messaging according to your application server's documentation. The exact configuration will depend on your server's messaging capabilities, but generally, you will need to make sure the same properties as above are made available to the server application.

## How It Works

### Event Flow

1. **Event Generation**: When an event is generated in the A2A system (e.g., TaskStatusUpdateEvent), it's enqueued in the local queue
2. **Replication Hook**: The `ReplicationHook` intercepts the event and sends it to the replication strategy
3. **Message Broker**: The replication strategy serializes the event and sends it to the configured message broker
4. **Event Reception**: Other A2A instances receive the event from the message broker
5. **Local Processing**: The received event is deserialized and enqueued in the local instance's queue
6. **Event Processing**: The local instance processes the replicated event, updating its state accordingly

### Event Types

The system replicates various event types while preserving their specific types:

- **TaskStatusUpdateEvent**: Task state changes (SUBMITTED, COMPLETED, etc.)
- **TaskArtifactUpdateEvent**: Task artifact changes
- **Message**: Chat messages and responses
- **Task**: Complete task objects
- **JSONRPCError**: Error events

### Serialization

Events are serialized using Jackson with polymorphic type information to ensure proper deserialization:

```json
{
  "taskId": "task-123",
  "event": {
    "@type": "TaskStatusUpdateEvent",
    "taskId": "task-123",
    "status": {
      "state": "completed",
      "timestamp": "2023-09-29T10:30:00Z"
    },
    "final": true,
    "kind": "status-update"
  }
}
```

## Advanced Topics

### Custom Replication Strategies

The architecture is designed to support additional replication strategies in the future. To implement a custom replication strategy, create a class that implements the `ReplicationStrategy` interface and ensure it's discoverable via CDI:

```java
@ApplicationScoped
public class CustomReplicationStrategy implements ReplicationStrategy {

    @Override
    public void send(String taskId, Event event) {
        // Implement custom replication logic
        // e.g., send to database, REST API, etc.
    }
}
```

### Monitoring Events

You can monitor replicated events by observing CDI events:

```java
@ApplicationScoped
public class ReplicationMonitor {

    public void onReplicatedEvent(@Observes ReplicatedEvent event) {
        // Monitor replicated events for metrics, logging, etc.
        LOGGER.info("Received replicated event for task: " + event.getTaskId());
    }
}
```

### Logging

Enable debug logging to monitor replication activity:

```properties
# For Quarkus
quarkus.log.category."io.a2a.extras.queuemanager.replicated".level=DEBUG

# For other servers, configure your logging framework accordingly
```

### Health Checks

When using Quarkus, the module integrates with MicroProfile Health to provide health checks:

```properties
# Configure health check timeout
quarkus.messaging.kafka.health.timeout=5s
```
