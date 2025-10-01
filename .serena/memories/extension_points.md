# A2A Java SDK - Extension Points & Customization

## Primary Extension Points (User-Facing)

### 1. AgentCard + AgentExecutor (REQUIRED)
**Purpose**: Define your agent's capabilities and behavior
**Location**: Your application code
**Usage**: Every A2A server MUST provide both

#### AgentCard Producer
```java
@ApplicationScoped
public class MyAgentCardProducer {
    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        return new AgentCard.Builder()
            .name("My Agent")
            .description("Agent description")
            .url("http://localhost:9999")
            .version("1.0.0")
            .capabilities(...)
            .skills(...)
            .build();
    }
}
```

#### AgentExecutor Producer
```java
@ApplicationScoped
public class MyAgentExecutorProducer {
    @Produces
    public AgentExecutor agentExecutor() {
        return new MyAgentExecutor();
    }
    
    private static class MyAgentExecutor implements AgentExecutor {
        @Override
        public void execute(RequestContext context, EventQueue eventQueue) {
            TaskUpdater updater = new TaskUpdater(context, eventQueue);
            // Your agent logic here
            updater.submit();
            updater.startWork();
            // ... do work ...
            updater.complete();
        }
        
        @Override
        public void cancel(RequestContext context, EventQueue eventQueue) {
            // Cancellation logic
        }
    }
}
```

**Key Points**:
- Both are required for functional agent
- Use TaskUpdater helper for task lifecycle management
- Execute runs asynchronously in background thread
- Communicate via eventQueue.enqueueEvent()

## Storage & Persistence Extension Points

### 2. TaskStore - Task Persistence
**Interface**: `io.a2a.server.tasks.TaskStore`
**Default**: `InMemoryTaskStore` (lost on restart)
**JPA Implementation**: `extras/task-store-database-jpa`

```java
@ApplicationScoped
@Alternative
@Priority(100)  // Higher than default
public class CustomTaskStore implements TaskStore {
    @Override
    public void save(Task task) {
        // Custom persistence logic
    }
    
    @Override
    public Task get(String taskId) {
        // Custom retrieval logic
    }
    
    @Override
    public void delete(String taskId) {
        // Custom deletion logic
    }
}
```

### 3. PushNotificationConfigStore - Push Config Persistence
**Interface**: `io.a2a.server.tasks.PushNotificationConfigStore`
**Default**: `InMemoryPushNotificationConfigStore`
**JPA Implementation**: `extras/push-notification-config-store-database-jpa`

```java
@ApplicationScoped
@Alternative
@Priority(100)
public class CustomPushNotificationConfigStore implements PushNotificationConfigStore {
    // Implement storage methods
}
```

## Event Queue & Distribution Extension Points

### 4. QueueManager - Event Queue Management
**Interface**: `io.a2a.server.events.QueueManager`
**Default**: `InMemoryQueueManager` (single instance)
**Replicated Implementation**: `extras/queue-manager-replicated`

```java
@ApplicationScoped
@Alternative
@Priority(100)
public class CustomQueueManager implements QueueManager {
    @Override
    public EventQueue.EventQueueBuilder getEventQueueBuilder(String taskId) {
        return EventQueue.builder()
            .queueSize(1000)
            .hook(customHook);
    }
    // Implement other methods
}
```

### 5. EventEnqueueHook - Event Interception
**Interface**: `io.a2a.server.events.EventEnqueueHook`
**Purpose**: Intercept events for logging, metrics, replication

```java
public class MetricsHook implements EventEnqueueHook {
    @Override
    public void onEnqueue(Event event) {
        metrics.recordEvent(event.getClass().getSimpleName());
    }
}

// Usage in EventQueueBuilder:
EventQueue queue = EventQueue.builder()
    .hook(new MetricsHook())
    .build();
```

## Authentication & Authorization Extension Points

### 6. AuthenticationProvider - Custom Auth
**Interface**: `io.a2a.server.auth.AuthenticationProvider`
**Purpose**: Implement custom authentication logic

```java
@ApplicationScoped
@Alternative
@Priority(100)
public class JWTAuthenticationProvider implements AuthenticationProvider {
    @Override
    public User authenticate(ServerCallContext context) throws JSONRPCError {
        String token = context.getAuthToken();
        // Validate JWT token
        return new User(userId, roles);
    }
}
```

## Client Transport Extension Points

### 7. ClientTransport - Custom Client Protocols
**Interface**: `io.a2a.client.transport.spi.ClientTransport`
**Purpose**: Implement custom transport protocols (e.g., WebSocket)

```java
public class WebSocketTransport implements ClientTransport {
    @Override
    public void sendMessage(MessageSendParams params, ClientCallContext context) {
        // WebSocket implementation
    }
    
    @Override
    public void sendMessageStream(MessageSendParams params, ClientCallContext context) {
        // Streaming over WebSocket
    }
    
    // Implement other protocol methods
}

// Provider:
public class WebSocketTransportProvider implements ClientTransportProvider {
    @Override
    public ClientTransport createTransport(AgentCard agentCard, ClientConfig config) {
        return new WebSocketTransport(agentCard, config);
    }
}

// Usage:
Client client = Client.builder(agentCard)
    .withTransport(WebSocketTransport.class, new WebSocketTransportConfig())
    .build();
```

## Server Transport Extension Points

### 8. Custom Server Transport
**Pattern**: Implement handler that delegates to DefaultRequestHandler
**Example**: See transport/jsonrpc, transport/grpc, transport/rest

```java
// Create transport-specific handler
@ApplicationScoped
public class MyTransportHandler {
    @Inject
    DefaultRequestHandler defaultRequestHandler;
    
    public Response handleRequest(MyTransportRequest request) {
        // Convert transport request to MessageSendParams
        MessageSendParams params = convertToParams(request);
        ServerCallContext context = createContext(request);
        
        // Delegate to DefaultRequestHandler
        return defaultRequestHandler.onMessageSend(params, context);
    }
}
```

## Advanced Extension Points

### 9. PushNotificationSender - Custom Push Notifications
**Interface**: `io.a2a.server.tasks.PushNotificationSender`
**Purpose**: Implement custom push notification delivery

```java
@ApplicationScoped
@Alternative
@Priority(100)
public class CustomPushNotificationSender extends BasePushNotificationSender {
    @Override
    protected void sendNotification(String url, Event event, 
                                    PushNotificationAuthenticationInfo auth) {
        // Custom notification delivery logic
    }
}
```

### 10. RequestContext - Custom Request Context
**Interface**: `io.a2a.server.agentexecution.RequestContext`
**Purpose**: Add custom metadata to request processing

```java
public class EnhancedRequestContext implements RequestContext {
    private final RequestContext delegate;
    private final Map<String, Object> customMetadata;
    
    // Delegate to base context, add custom methods
    public Object getCustomMetadata(String key) {
        return customMetadata.get(key);
    }
}
```

## Extension Strategy Decision Matrix

| Need | Extension Point | Complexity | Default Available |
|------|----------------|------------|-------------------|
| Define agent behavior | AgentExecutor | Low | ❌ (required) |
| Define agent metadata | AgentCard | Low | ❌ (required) |
| Persist tasks | TaskStore | Medium | ✅ InMemory |
| Distribute queues | QueueManager | High | ✅ InMemory |
| Custom authentication | AuthenticationProvider | Medium | ✅ Unauthenticated |
| New client protocol | ClientTransport | High | ✅ JSON-RPC/gRPC/REST |
| New server protocol | Transport Handler | High | ✅ JSON-RPC/gRPC/REST |
| Event interception | EventEnqueueHook | Low | ❌ (optional) |
| Push notifications | PushNotificationSender | Medium | ✅ HTTP-based |

## Best Practices

1. **Use CDI properly**:
   - @Alternative + @Priority for overriding defaults
   - @Produces for bean creation
   - @Inject for dependencies

2. **Maintain interface contracts**:
   - Implement all required methods
   - Follow documented behavior
   - Handle errors appropriately

3. **Test thoroughly**:
   - Unit test custom implementations
   - Integration test with server/client
   - Test failure scenarios

4. **Document extensions**:
   - JavaDoc on custom classes
   - Configuration requirements
   - Usage examples

5. **Consider performance**:
   - TaskStore: Database queries can be slow
   - QueueManager: Replication adds latency
   - EventEnqueueHook: Runs on critical path
