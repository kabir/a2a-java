# Queue Cleanup Issue - Debug Session Summary

**Date**: 2025-10-01
**Branch**: replicated-eventqueues
**Session Type**: Deep debugging of streaming resubscription issue

## Root Cause Identified

**Problem**: Non-streaming message sends close EventQueues that have active streaming subscriptions (child queues) attached to them.

**Evidence**: Queue object IDs constantly changing between messages:
```
MainQueue@4343e3f7 (initial events)
  → Queue becomes null
  → MainQueue@2d82e704 (Initialize artifact)  
  → MainQueue@62d61da8 (Test 2 artifact)
  → Queue becomes null again
  → MainQueue@2205a1c8 (Test 3 artifact)
```

**Impact**: Client's resubscribe creates a child queue tap, but when non-streaming sends close the parent queue, the child loses connection to new events. Client only receives 1 artifact out of 11 messages processed.

## Technical Details

### Cleanup Flow (Current - Broken)
1. Client calls `resubscribe()` → `DefaultRequestHandler.onResubscribeToTask()` → creates child queue via `createOrTap()`
2. Client sends message (non-streaming) → `DefaultRequestHandler.onMessageSend()` → processes message
3. Cleanup runs → `cleanupProducer()` → `queueManager.close(taskId)` → **closes queue with active child**
4. Next message creates NEW queue (different object ID)
5. Child queue is orphaned, receives no more events

### Fix Location

**File**: `server-common/src/main/java/io/a2a/server/requesthandlers/DefaultRequestHandler.java`

**Method**: `cleanupProducer()` (lines 418-425)
```java
private void cleanupProducer(String taskId) {
    // TODO the Python implementation waits for the producerRunnable
    runningAgents.get(taskId)
            .whenComplete((v, t) -> {
                queueManager.close(taskId);  // ← TOO AGGRESSIVE! Closes queue with active children
                runningAgents.remove(taskId);
            });
}
```

**Proposed Fix**: Check if MainQueue has active children before closing
```java
// In EventQueue.MainQueue
public boolean hasActiveChildren() {
    return !children.isEmpty();
}

// In cleanupProducer
EventQueue queue = queueManager.get(taskId);
if (queue instanceof EventQueue.MainQueue mainQueue && mainQueue.hasActiveChildren()) {
    // Don't close - there are active subscriptions
    LOGGER.debug("Not closing queue {} - has active child subscriptions", taskId);
} else {
    queueManager.close(taskId);
}
```

## Files Modified (Debug Logging)

1. **extras/queue-manager-replicated/core/.../ReplicatedQueueManager.java**
   - Added DEBUG logging throughout `onReplicatedEvent()` method
   - Added LOGGER field and imports
   - Logs: queue lookup, createOrTap calls, enqueue operations

2. **examples/cloud-deployment/server/src/main/resources/application.properties**
   - Added debug logging categories:
     ```properties
     quarkus.log.category."io.a2a.server.events".level=DEBUG
     quarkus.log.category."io.a2a.extras.queuemanager.replicated".level=DEBUG
     quarkus.log.category."io.a2a.server.requesthandlers".level=DEBUG
     ```

3. **server-common/src/main/java/io/a2a/server/requesthandlers/DefaultRequestHandler.java**
   - Changed line 326: `queueManager.tap()` → `queueManager.createOrTap()`
   - Added comment explaining the fix allows resubscription after initial request completes

## Test Setup

**Location**: `examples/cloud-deployment/server/`

**Test Command**:
```bash
mvn exec:java -Dexec.mainClass="io.a2a.examples.cloud.A2ACloudExampleClient" -Dexec.classpathScope=test
```

**Kubernetes Environment**:
- Minikube with Podman driver
- 2 replica pods (a2a-agent)
- PostgreSQL database (shared task store)
- Kafka (event replication)
- Port-forward: `kubectl port-forward -n a2a-demo svc/a2a-agent-service 8080:8080`

**Test Flow**:
1. Initial message creates task (non-streaming)
2. Client resubscribes (streaming) to task
3. Client sends 10 test messages (non-streaming)
4. **Expected**: Receive 11 artifacts (Initialize + Test 1-10)
5. **Actual**: Receive only 1 artifact (Test 1)

## Additional Issues Discovered

**Load Balancing**: All requests hitting same pod (not round-robin)
- Kubernetes Service should distribute across pods
- Need to investigate Service configuration or client connection pooling

## Next Steps

1. **Switch to new branch** for core framework fix
2. **Implement fix** in `DefaultRequestHandler.cleanupProducer()`
3. **Add method** to `EventQueue.MainQueue` to check for active children
4. **Test fix** with cloud deployment example
5. **Investigate load balancing** issue (secondary priority)

## Restoration Instructions

To resume this session:
1. `git checkout replicated-eventqueues`
2. Review this memory: `/sc:load` or read memory directly
3. Check modified files for debug logging (keep for now)
4. Create new branch for fix: `git checkout -b fix/queue-cleanup-active-children`
5. Implement fix in DefaultRequestHandler.java
