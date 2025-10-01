# EventQueue Lifecycle Fix - Implementation Plan

**Branch**: `replicated-eventqueues-with-example`
**Issue**: Non-streaming requests close MainQueue prematurely, breaking streaming resubscriptions

## Problem Summary

When a non-streaming `sendMessage()` completes, it closes the EventQueue, which causes:
1. MainQueue closes and all ChildQueues are force-closed
2. `QueueManager.close(taskId)` removes MainQueue from map
3. Next `sendMessage()` creates NEW MainQueue with different object ID
4. Streaming resubscriptions listen to old closed MainQueue
5. Events sent to new MainQueue never reach subscribers

**Evidence**: Changing MainQueue object IDs in logs:
- `MainQueue@4343e3f7` → `MainQueue@2d82e704` → `MainQueue@62d61da8`

## Root Cause Analysis

**Current flow**:
```
1. Non-streaming sendMessage() arrives
2. createOrTap() returns MainQueue M directly
3. Agent processes message
4. queue.close() called (line 393 in registerAndExecuteAgentAsync)
5. cleanupProducer() calls queueManager.close(taskId) (line 422)
6. MainQueue M removed from map
7. Next sendMessage() → createOrTap() → creates NEW MainQueue
8. Resubscribed ChildQueue listening to old MainQueue M
```

**Key files**:
- `EventQueue.java:164-231` - MainQueue closes all children
- `DefaultRequestHandler.java:393` - queue.close() in agent callback
- `DefaultRequestHandler.java:422` - queueManager.close(taskId) removes from map
- `InMemoryQueueManager.java:42-46` - close() removes queue from map

## Solution Design

### Core Principles

1. **Always return ChildQueue**: `createOrTap()` returns tapped ChildQueue, never MainQueue directly
2. **Reference counting**: MainQueue auto-closes only when all children close
3. **Explicit cleanup**: Close the specific ChildQueue used, not the MainQueue
4. **Lazy map cleanup**: Remove closed MainQueues from map when accessed
5. **Deprecate QueueManager.close()**: No longer needed with auto-cleanup

### Implementation Changes

#### 1. InMemoryQueueManager.createOrTap() - Always Return ChildQueue

**File**: `server-common/src/main/java/io/a2a/server/events/InMemoryQueueManager.java`

**Current** (line 49-61):
```java
@Override
public EventQueue createOrTap(String taskId) {
    EventQueue existing = queues.get(taskId);
    EventQueue newQueue = null;
    if (existing == null) {
        newQueue = factory.builder(taskId).build();
        existing = queues.putIfAbsent(taskId, newQueue);
    }
    return existing == null ? newQueue : existing;  // Returns MainQueue
}
```

**New**:
```java
@Override
public EventQueue createOrTap(String taskId) {
    EventQueue existing = queues.get(taskId);

    // Lazy cleanup: remove closed queues from map
    if (existing != null && existing.isClosed()) {
        queues.remove(taskId);
        existing = null;
    }

    EventQueue newQueue = null;
    if (existing == null) {
        newQueue = factory.builder(taskId).build();
        existing = queues.putIfAbsent(taskId, newQueue);
    }

    EventQueue main = existing == null ? newQueue : existing;
    return main.tap();  // Always return ChildQueue
}
```

**Impact**:
- Every caller gets ChildQueue instead of MainQueue
- MainQueue stays hidden in QueueManager map
- Enables reference counting cleanup

---

#### 2. EventQueue.ChildQueue - Close Self and Notify Parent

**File**: `server-common/src/main/java/io/a2a/server/events/EventQueue.java`

**Current** (lines 265-272):
```java
@Override
public void close() {
    parent.close();  // Delegates to parent
}

@Override
public void close(boolean immediate) {
    parent.close(immediate);  // Delegates to parent
}
```

**New**:
```java
@Override
public void close() {
    close(false);  // Delegate to close(boolean)
}

@Override
public void close(boolean immediate) {
    this.doClose(immediate);           // Close self first
    parent.childClosing(this, immediate);  // Notify parent
}
```

**Impact**:
- ChildQueue closes itself before notifying parent
- Parent can check if other children are still active
- Enables reference counting

---

#### 3. EventQueue.MainQueue - Add childClosing() Method

**File**: `server-common/src/main/java/io/a2a/server/events/EventQueue.java`

**Add new method** (after line 231):
```java
void childClosing(ChildQueue child, boolean immediate) {
    children.remove(child);  // Remove the closing child

    // Only close MainQueue if immediate OR no children left
    if (immediate || children.isEmpty()) {
        this.doClose(immediate);
    }
}
```

**Modify existing close()** (lines 227-230):
```java
@Override
public void close(boolean immediate) {
    doClose(immediate);
    if (immediate) {
        // Force-close all remaining children
        children.forEach(child -> child.doClose(immediate));
    }
    children.clear();
}
```

**Impact**:
- Reference counting: MainQueue stays open while children active
- Graceful close (immediate=false): MainQueue waits for children
- Immediate close (immediate=true): Force-closes everything

---

#### 4. DefaultRequestHandler - Modify cleanupProducer()

**File**: `server-common/src/main/java/io/a2a/server/requesthandlers/DefaultRequestHandler.java`

**Current** (lines 418-425):
```java
private void cleanupProducer(String taskId) {
    runningAgents.get(taskId)
            .whenComplete((v, t) -> {
                queueManager.close(taskId);  // Remove from map
                runningAgents.remove(taskId);
            });
}
```

**New**:
```java
private void cleanupProducer(String taskId, EventQueue queue) {
    runningAgents.get(taskId)
            .whenComplete((v, t) -> {
                queue.close();  // Close the ChildQueue
                runningAgents.remove(taskId);
                // queueManager.close(taskId) removed!
            });
}
```

**Call site changes**:

**onMessageSend()** (~line 195):
```java
// Current
cleanupProducer(taskId.get());

// New
cleanupProducer(taskId.get(), queue);
```

**onMessageSendStream()** (~line 263):
```java
// Current
CompletableFuture<Void> cleanupTask = CompletableFuture.runAsync(
    () -> cleanupProducer(taskId.get()), executor);

// New
CompletableFuture<Void> cleanupTask = CompletableFuture.runAsync(
    () -> cleanupProducer(taskId.get(), queue), executor);
```

**Impact**:
- Closes specific ChildQueue, not MainQueue
- No premature map removal
- MainQueue stays stable across requests

---

#### 5. DefaultRequestHandler - Remove Duplicate close()

**File**: `server-common/src/main/java/io/a2a/server/requesthandlers/DefaultRequestHandler.java`

**Remove line 393** (in registerAndExecuteAgentAsync):
```java
CompletableFuture<Void> cf = CompletableFuture.runAsync(runnable, executor)
        .whenComplete((v, err) -> {
            if (err != null) {
                runnable.setError(err);
            }
            queue.close();  // ← REMOVE THIS LINE
            runnable.invokeDoneCallbacks();
        });
```

**Rationale**:
- cleanupProducer() now closes the queue
- Avoids double-close (though idempotent, cleaner to have single close point)
- Clear ownership: cleanupProducer handles cleanup

---

#### 6. QueueManager - Deprecate close() Method

**File**: `server-common/src/main/java/io/a2a/server/events/QueueManager.java`

**Deprecate** (line 41):
```java
@Deprecated(since = "0.3.0", forRemoval = true)
default void close(String taskId) {
    // Auto-cleanup now handled via reference counting
    // Queues removed from map lazily in createOrTap()
    // This method no longer used
}
```

**File**: `server-common/src/main/java/io/a2a/server/events/InMemoryQueueManager.java`

**Keep implementation but mark deprecated** (lines 40-46):
```java
@Override
@Deprecated(since = "0.3.0", forRemoval = true)
public void close(String taskId) {
    EventQueue existing = queues.remove(taskId);
    if (existing == null) {
        throw new NoTaskQueueException();
    }
}
```

**Impact**:
- Signals deprecation for future removal
- Existing code continues to work
- Clarifies that reference counting is the new approach

---

## Complete Request Flow

### First Non-Streaming sendMessage()

```
1. createOrTap("task-123")
   → MainQueue M created and stored in map
   → M.tap() returns ChildQueue A

2. Agent processes message via A

3. Agent completes
   → cleanupProducer(taskId, A) called
   → A.close()
     → A.doClose() (close self)
     → A calls M.childClosing(A, false)
       → M.children.remove(A)
       → children.isEmpty() == true
       → M.doClose() (closes MainQueue)

4. MainQueue M is closed but still in map
```

### Streaming Resubscribe

```
1. Client calls resubscribe()

2. createOrTap("task-123")
   → existing = M (closed)
   → M.isClosed() == true
   → queues.remove("task-123")
   → Create NEW MainQueue M2
   → M2.tap() returns ChildQueue B

3. ChildQueue B starts listening for events
```

**Wait!** This still creates a new queue if resubscribe happens AFTER first message completes.

**Actually, the key is timing**: Resubscribe typically happens BEFORE first message completes cleanup:

```
Timeline:
T1: First sendMessage() arrives
T2: createOrTap() → MainQueue M → ChildQueue A
T3: sendMessage() returns to client (agent still running)
T4: Client calls resubscribe()
T5: createOrTap() → gets same MainQueue M → ChildQueue B
T6: Agent completes → A.close()
    → M.childClosing(A)
    → M.children.remove(A)
    → children = [B] (NOT empty!)
    → M stays open ✅
T7: Second sendMessage()
T8: createOrTap() → gets same M → ChildQueue C
T9: C enqueues events
T10: M distributes to children [B, C]
T11: B receives events! ✅
```

**Key insight**: MainQueue stays open as long as ANY child is active. Resubscribe creates a child that keeps MainQueue alive across multiple non-streaming requests.

---

## Unit Test Changes Required

### EventQueueTest.java

**Tests likely needing updates**:

1. **testTapCreatesChildQueue()** - May need adjustment for new behavior
2. **testCloseImmediatePropagationToChildren()** - Update for childClosing() behavior
3. **testCloseChildQueues()** - Update for reference counting behavior

**New test needed**:
```java
@Test
public void testMainQueueStaysOpenWithActiveChildren() {
    EventQueue mainQueue = EventQueue.builder().build();
    EventQueue child1 = mainQueue.tap();
    EventQueue child2 = mainQueue.tap();

    // Close child1
    child1.close();

    // MainQueue should still be open (child2 active)
    assertFalse(mainQueue.isClosed());

    // Close child2
    child2.close();

    // Now MainQueue should close
    assertTrue(mainQueue.isClosed());
}
```

### InMemoryQueueManagerTest.java

**Tests likely needing updates**:

1. **testCreateOrTapNewQueue()** - Should return ChildQueue, not MainQueue
2. **testCreateOrTapExistingQueue()** - Should return NEW ChildQueue each time
3. **testTapExistingQueue()** - Behavior unchanged
4. **testCloseExistingQueue()** - Now deprecated, but should still work

**New tests needed**:
```java
@Test
public void testCreateOrTapAlwaysReturnsChildQueue() {
    EventQueue queue1 = queueManager.createOrTap("task-123");
    assertNotNull(queue1);
    assertThrows(IllegalStateException.class, () -> queue1.tap()); // Can't tap ChildQueue

    EventQueue queue2 = queueManager.createOrTap("task-123");
    assertNotNull(queue2);
    assertNotSame(queue1, queue2); // Different ChildQueue instances
}

@Test
public void testLazyCleanupRemovesClosedQueues() {
    EventQueue queue1 = queueManager.createOrTap("task-123");
    queue1.close(); // Close the ChildQueue

    // This should trigger lazy cleanup
    EventQueue queue2 = queueManager.createOrTap("task-123");
    assertNotNull(queue2);
    // Should have created new MainQueue since old one was closed
}
```

---

## New Integration Test

### Location
`tests/server-common/src/test/java/io/a2a/server/apps/common/AbstractA2AServerTest.java`

### Test Name
`testNonStreamingMessagesWithActiveStreamingResubscription()`

### Purpose
Verify that non-streaming `sendMessage()` operations don't break active streaming resubscriptions.

### Special AgentExecutor Handling

**File**: `tests/server-common/src/test/java/io/a2a/server/apps/common/AgentExecutorProducer.java`

Add special taskId handling:
```java
@Override
public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
    String taskId = context.getTaskId();

    // Special handling for resubscription test
    if (taskId.startsWith("resubscribe-nonstreaming-test-")) {
        // Enqueue multiple artifacts with small delays
        TaskUpdater updater = new TaskUpdater(context, eventQueue);
        updater.submit();
        updater.startWork();

        String messageText = extractText(context.getMessage());
        for (int i = 1; i <= 3; i++) {
            String artifactText = messageText + " - artifact " + i;
            updater.addArtifact(List.of(new TextPart(artifactText, null)));
            try {
                Thread.sleep(100); // Small delay for ordering
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        updater.complete();
        return;
    }

    // Existing logic for other taskIds...
    if (context.getTaskId().equals("task-not-supported-123")) {
        eventQueue.enqueueEvent(new UnsupportedOperationError());
    }
    eventQueue.enqueueEvent(context.getMessage() != null ? context.getMessage() : context.getTask());
}
```

### Test Implementation

```java
@Test
@Timeout(value = 30, unit = TimeUnit.SECONDS)
public void testNonStreamingMessagesWithActiveStreamingResubscription() throws Exception {
    String taskId = "resubscribe-nonstreaming-test-" + System.currentTimeMillis();

    // Step 1: Send initial non-streaming message to create task
    Message initialMessage = new Message.Builder(MESSAGE)
            .taskId(taskId)
            .contextId("resubscribe-context-" + System.currentTimeMillis())
            .parts(List.of(new TextPart("initial")))
            .build();

    CountDownLatch initialLatch = new CountDownLatch(1);
    AtomicReference<Task> createdTask = new AtomicReference<>();

    getNonStreamingClient().sendMessage(initialMessage, List.of((event, agentCard) -> {
        if (event instanceof TaskEvent taskEvent) {
            createdTask.set(taskEvent.getTask());
            initialLatch.countDown();
        }
    }), null);

    assertTrue(initialLatch.await(10, TimeUnit.SECONDS), "Initial message should complete");
    assertNotNull(createdTask.get(), "Task should be created");

    // Small delay to ensure initial message cleanup has started
    Thread.sleep(500);

    // Step 2: Resubscribe to the task (creates streaming subscription)
    CountDownLatch resubscribeLatch = new CountDownLatch(6); // Expect 6 artifacts (2 msgs × 3 artifacts)
    List<String> receivedArtifacts = new CopyOnWriteArrayList<>();
    AtomicBoolean unexpectedEvent = new AtomicBoolean(false);
    AtomicReference<Throwable> errorRef = new AtomicReference<>();

    BiConsumer<ClientEvent, AgentCard> resubscribeConsumer = (event, agentCard) -> {
        if (event instanceof TaskUpdateEvent taskUpdateEvent) {
            if (taskUpdateEvent.getUpdateEvent() instanceof TaskArtifactUpdateEvent artifactEvent) {
                String text = extractText(artifactEvent.getArtifact().parts());
                receivedArtifacts.add(text);
                resubscribeLatch.countDown();
            }
        } else {
            unexpectedEvent.set(true);
        }
    };

    Consumer<Throwable> errorHandler = error -> {
        if (!isStreamClosedError(error)) {
            errorRef.set(error);
        }
    };

    // Wait for streaming subscription to establish
    CountDownLatch subscriptionLatch = new CountDownLatch(1);
    awaitStreamingSubscription().whenComplete((unused, throwable) -> subscriptionLatch.countDown());

    getClient().resubscribe(new TaskIdParams(taskId), List.of(resubscribeConsumer), errorHandler);

    assertTrue(subscriptionLatch.await(15, TimeUnit.SECONDS), "Subscription should establish");

    // Step 3: Send two non-streaming messages while subscription is active
    Message message1 = new Message.Builder(MESSAGE)
            .taskId(taskId)
            .contextId(createdTask.get().getContextId())
            .parts(List.of(new TextPart("message1")))
            .messageId("msg1-" + System.currentTimeMillis())
            .build();

    Message message2 = new Message.Builder(MESSAGE)
            .taskId(taskId)
            .contextId(createdTask.get().getContextId())
            .parts(List.of(new TextPart("message2")))
            .messageId("msg2-" + System.currentTimeMillis())
            .build();

    // Send both messages non-streaming
    getNonStreamingClient().sendMessage(message1);
    Thread.sleep(200); // Small delay between messages
    getNonStreamingClient().sendMessage(message2);

    // Step 4: Verify all artifacts received by streaming subscription
    assertTrue(resubscribeLatch.await(15, TimeUnit.SECONDS),
        "Should receive all 6 artifacts via streaming resubscription");
    assertFalse(unexpectedEvent.get(), "Should not receive unexpected events");
    assertNull(errorRef.get(), "Should not receive errors");

    // Verify artifact content
    assertEquals(6, receivedArtifacts.size(), "Should receive 6 artifacts total");

    // Each message produces 3 artifacts
    assertTrue(receivedArtifacts.stream().anyMatch(a -> a.contains("message1 - artifact 1")));
    assertTrue(receivedArtifacts.stream().anyMatch(a -> a.contains("message1 - artifact 2")));
    assertTrue(receivedArtifacts.stream().anyMatch(a -> a.contains("message1 - artifact 3")));
    assertTrue(receivedArtifacts.stream().anyMatch(a -> a.contains("message2 - artifact 1")));
    assertTrue(receivedArtifacts.stream().anyMatch(a -> a.contains("message2 - artifact 2")));
    assertTrue(receivedArtifacts.stream().anyMatch(a -> a.contains("message2 - artifact 3")));
}

private String extractText(List<Part<?>> parts) {
    if (parts == null || parts.isEmpty()) return "";
    Part<?> part = parts.get(0);
    if (part instanceof TextPart textPart) {
        return textPart.getText();
    }
    return "";
}
```

### Test Verification Points

1. **Initial message creates task successfully**
2. **Resubscription establishes without errors**
3. **Non-streaming message 1 artifacts received by subscriber**
4. **Non-streaming message 2 artifacts received by subscriber**
5. **All 6 artifacts received in order**
6. **No errors or unexpected events**

### Expected Behavior

With the fix in place:
- MainQueue stays stable across all operations (same object ID)
- ChildQueue from resubscribe receives events from subsequent messages
- No queue object ID changes
- No event loss

Without the fix:
- MainQueue closes after first message
- New MainQueue created for message1 (different object ID)
- Subscriber still listening to old closed queue
- Events from message1 and message2 never reach subscriber
- Test fails with timeout waiting for artifacts

---

## Testing Strategy

### Unit Test Execution
```bash
# Run EventQueue tests
mvn test -Dtest=EventQueueTest

# Run QueueManager tests
mvn test -Dtest=InMemoryQueueManagerTest
```

### Integration Test Execution
```bash
# Run the new integration test
mvn test -Dtest=AbstractA2AServerTest#testNonStreamingMessagesWithActiveStreamingResubscription

# Run all AbstractA2AServerTest tests
mvn test -Dtest=AbstractA2AServerTest
```

### Manual Testing with Example
```bash
# Run the cloud deployment example
cd examples/cloud-deployment
mvn quarkus:dev

# In another terminal, run the client test
cd examples/cloud-deployment/server
mvn test -Dtest=A2ACloudExampleClient
```

---

## Rollout Plan

### Phase 1: Core Queue Changes
1. Implement EventQueue.childClosing() method
2. Update ChildQueue.close() to call childClosing()
3. Update MainQueue.close() for new behavior
4. Add unit tests for reference counting

### Phase 2: QueueManager Changes
1. Update InMemoryQueueManager.createOrTap() to return ChildQueue
2. Add lazy cleanup for closed queues
3. Update unit tests

### Phase 3: DefaultRequestHandler Changes
1. Modify cleanupProducer() signature
2. Update call sites in onMessageSend() and onMessageSendStream()
3. Remove duplicate queue.close() in registerAndExecuteAgentAsync()

### Phase 4: Integration Testing
1. Add special taskId handling to test AgentExecutor
2. Implement new integration test
3. Verify with cloud deployment example

### Phase 5: Deprecation and Cleanup
1. Mark QueueManager.close() as deprecated
2. Update documentation
3. Run full test suite

---

## Risk Assessment

**Low Risk**:
- EventQueue changes are internal to queue lifecycle
- ChildQueue close behavior is backward compatible (doClose is idempotent)
- Reference counting is additive, doesn't break existing behavior

**Medium Risk**:
- createOrTap() return type change (MainQueue → ChildQueue)
  - Mitigation: ChildQueue implements EventQueue interface fully
  - Most code treats as EventQueue interface, not concrete type

**Testing Required**:
- Unit tests for reference counting
- Integration test for non-streaming + streaming interaction
- Existing test suite must pass unchanged

---

## Success Criteria

1. **MainQueue object ID stays stable** across multiple requests
2. **Streaming resubscriptions receive events** from subsequent non-streaming messages
3. **All existing tests pass** without modification
4. **New integration test passes** consistently
5. **Cloud deployment example works** without event loss
6. **No regression** in non-streaming-only or streaming-only scenarios

---

## Documentation Updates

### CLAUDE.md
Update the "Event Queue Lifecycle & Closing" section with:
- New createOrTap() behavior (returns ChildQueue)
- Reference counting cleanup logic
- QueueManager.close() deprecation
- Lazy cleanup approach

### README.md
Add note about EventQueue lifecycle changes in 0.3.0

---

## Follow-up Considerations

1. **Replicated Queue Manager**: Ensure ReplicatedQueueManager also returns ChildQueue from createOrTap()
2. **Memory Management**: Monitor for MainQueue buildup in map if queues never accessed after closing
3. **Explicit Cleanup API**: Consider adding explicit cleanup method for long-running tasks
4. **Metrics**: Add metrics for queue lifecycle (created, closed, tapped, children count)

---

**Date**: 2025-01-10
**Author**: Claude + Kabir
**Status**: Ready for Implementation
