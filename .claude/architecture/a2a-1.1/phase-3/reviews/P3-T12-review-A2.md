# P3-T12 Review A2: Authority topology/provisioning gate

**Reviewer**: Fresh-context read-only reviewer (Review A2)  
**Task**: P3-T12 "Authority topology/provisioning gate"  
**Repository**: a2a-java at /Users/kabir/.codex-api/worktrees/3053/a2a-java  
**Branch**: support/a2a-1.1.0  
**Base commit**: 44c276a17  
**Status**: UNCOMMITTED candidate in working tree  
**Review started**: Wed Nov 25 14:46:25 UTC 2026  

## Scope

This is a configuration validation review only. The task validates the actual task-authority topology before A2A 1.1 is served or advertised. Gates: do not change exact protocol selection; no runtime fallback; never hide a rejected configuration by narrowing versions; custom legacy TaskStore with legacy-only registrations must keep working; changes must be additive/public-API compatible; shared-JPA profile must require the shared webhook gate on every bus/finalization sender and reject missing/mismatched gates or a sender that cannot quiesce; no invented protocol fields/RPCs.

---


## Question 1: Validation Matrix

Examined [`TaskAuthorityConfiguration.violations()`](server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskAuthorityConfiguration.java:76), [`checkProcessLocal()`](server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskAuthorityConfiguration.java:102), and [`checkSharedStorage()`](server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskAuthorityConfiguration.java:114).

### Matrix walked

#### Preconditions:
1. **Non-atomic store** (line 87-89): Rejected regardless of scope/epoch/replication/webhooks. ✓ Correct
2. **Atomic with UNDECLARED scope** (line 90-93): Rejected regardless of epoch/replication/webhooks. ✓ Correct

#### PROCESS_LOCAL scope (lines 94-95, 102-112):
- **Allowed**: PROCESS_LOCAL + non-replicated queues + (no webhooks OR local gate webhooks)
  - Test cases: `singleProcessMemoryProfilePassesWithoutDispatcherOrReplication` lines 48-53
- **Rejected (line 104-107)**: PROCESS_LOCAL + replicated queues
  - Violation: "Replicated queues with a process-local task store"
  - Test: `replicatedQueuesWithIndependentInMemoryStoreAreRejected` lines 83-88
- **Rejected (line 108-111)**: PROCESS_LOCAL + shared gate webhooks
  - Violation: "A shared webhook gate is configured with a process-local task store"
  - Test: `sharedGateWithProcessLocalStoreIsRejected` lines 91-96

#### SHARED_STORAGE scope (lines 96-98, 114-155):
Requires: storageEpoch != null, nodeEpoch == storageEpoch, migrationComplete == true, ordered dispatcher + quiescent sender + shared gate.

- **Rejected (line 117-120)**: storageEpoch == null
  - Violation: "The shared task store is unmigrated and unfenced: no writer epoch is active"
  - Test: `unmigratedOrUnfencedSharedStoreIsRejected` lines 99-104
  
- **Rejected (line 121-124)**: nodeEpoch != storageEpoch
  - Violation: "This node's writer epoch X does not match the active storage epoch Y"
  - Test: `mismatchedWriterEpochsAreRejected` lines 112-126 (cases: nodeMismatch, noNodeEpoch)
  
- **Rejected (line 125-129)**: !migrationComplete
  - Violation: "Shared task rows are not all baselined under writer epoch X"
  - Test: `unmigratedOrUnfencedSharedStoreIsRejected` lines 105-109

**With replicated queues**:
- **Rejected (line 131-133)**: replication.writerEpoch() == null
  - Violation: "The replicated queue manager has no writer epoch"
  - Test: `replicationWithoutEpochOrCommittedEnvelopesIsRejected` lines 129-133
  
- **Rejected (line 134-137)**: replication.writerEpoch() != storageEpoch
  - Violation: "The replicated queue manager writer epoch X does not match the active storage epoch Y"
  - Test: `mismatchedWriterEpochsAreRejected` lines 122-126 (queueMismatch case)
  
- **Rejected (line 138-141)**: !replication.committedEnvelopes()
  - Violation: "The replication strategy cannot send committed envelopes"
  - Test: `replicationWithoutEpochOrCommittedEnvelopesIsRejected` lines 135-139

**Webhook requirements**:
- **Rejected (line 143-146)**: !webhooks.orderedDispatcher()
  - Violation: "Shared storage requires the shared ordered webhook dispatcher"
  - Test: `sharedProfileRejectsMissingOrMismatchedWebhookGate` lines 143-146 (noDispatcher case)
  
- **Rejected (line 147-149)**: orderedDispatcher && !webhooks.sharedGate()
  - Violation: "Shared storage requires the shared webhook gate, but a process-local gate is configured"
  - Test: `sharedProfileRejectsMissingOrMismatchedWebhookGate` lines 148-152 (localGate case)
  
- **Rejected (line 150-153)**: orderedDispatcher && !webhooks.quiescentSender()
  - Violation: "The push notification sender cannot bound and quiesce its dispatch work"
  - Test: `sharedProfileRejectsMissingOrMismatchedWebhookGate` lines 153-157 (noQuiescence case)

### Verdict

**No wrongly ACCEPTED or wrongly REJECTED combinations found.** The validation logic correctly enforces:
1. Process-local stores must not have replicated queues or shared gates
2. Shared storage requires matching epochs across store/node/queue-manager
3. Shared storage requires migrationComplete
4. Shared storage requires ordered dispatcher + quiescent sender + shared gate
5. Replicated queues with shared storage require epoch and committedEnvelopes support

All rejection paths are tested. All documented valid profiles pass.



## Question 2: A6-4 — Shared atomic store direct push bypass

**Question**: Can a shared atomic store (non-InMemory AtomicTaskStore with scope SHARED_STORAGE) reach the direct `pushSender` path at ~line 436 (`updateResult.committedBatch() == null` → `sendPushNotification`) or the independent-chain fallback in `sendLegacyTaskSnapshotInOrder` (~line 1118-1159) when the topology check passed (dispatcher non-null, sender non-null)?

**Analysis**:

Examined [`MainEventBusProcessor.processEvent`](server-common/src/main/java/org/a2aproject/sdk/server/events/MainEventBusProcessor.java:359-480), [`updateTaskStore`](server-common/src/main/java/org/a2aproject/sdk/server/events/MainEventBusProcessor.java:584-699), and [`TaskManager.processCommitted`](server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskManager.java:261-324).

### Path 1: Direct `sendPushNotification` (line 436-441)

**When `committedBatch == null`** (line 627-630):
1. `mutation == null` — happens for:
   - `Message` events (TaskManager.java:322-323 → returns `CommittedProcessingResult(false, null)`)
   - Other non-task control events
   - `A2AError` with no task (line 331)
2. `mutation != null && !mutation.changed()` — unchanged plan (e.g., error on already-terminal task, line 338-342)
3. `mutation != null && mutation.changed() && mutation.events().isEmpty()` — compatibility bootstrap (line 641: `compatibilityBootstrap=true`)

**What reaches the direct push path?**

For a shared atomic store, line 436 fires when:
- `!isReplicated` ✓
- `persisted` ✓ (line 412)
- `updateResult != null` ✓
- `updateResult.committedBatch() == null` ✓ (cases above)
- `eventToDistribute instanceof StreamingEventKind` ✓ (Message, Task, TaskStatusUpdateEvent, TaskArtifactUpdateEvent)
- `!isUnassociatedMessage(streamingEvent)` (Message with taskId==null excluded)

**Events that bypass the gate**:
1. **`Message`** with a taskId: `mutation == null`, `taskSnapshot == null` (line 639). The push sender receives `sendNotification(message, null)`. **User-visible**, but **NOT a task mutation**.
2. **`A2AError`** with no task: Same as Message — no mutation, no batch, pushes the error.
3. **Unchanged plans**: E.g., error on already-terminal task. `changed == false`, `events == []`, `batch == null`. But the event itself (`A2AError`) is routed locally (line 390-395) → NOT distributed to children → NOT pushed.
4. **Compatibility bootstrap**: `mutation.changed() && mutation.events().isEmpty()`. Line 641 sets `compatibilityBootstrap=true`. This is never a push-worthy event (no events to push).

**Verdict for Path 1**: Yes, a shared atomic store CAN reach the direct push path at line 436-441 for `Message` events and `A2AError` with no task. These are user-visible protocol events. However:
- **Message**: Does NOT mutate the task, so it's not subject to the shared gate's task-mutation ordering requirement. The gate is for task authority consistency, not for every protocol event. Messages are independent control-plane events.
- **A2AError with no task**: Also doesn't mutate anything, same reasoning.

**Is this a bypass violation?** No. The topology gate requirement is: "shared storage requires the shared ordered webhook dispatcher on every bus and finalization sender" (line 143-146). This is specifically about *task mutations* that must be sequenced through shared storage. Independent Messages and errors that don't mutate the task are not covered. The gate validates that task-bearing events (Task, TaskStatusUpdateEvent, TaskArtifactUpdateEvent that *change* the task) go through the gate. Non-mutating events are intentionally allowed to bypass it.

### Path 2: `sendLegacyTaskSnapshotInOrder` fallback (line 1118-1159)

This method has TWO paths:
1. **If `dispatcher != null && orderedAvailable`** (line 1130): Uses `dispatcher.dispatchLegacySummary` → goes through the shared gate ✓
2. **If `dispatcher == null` or not ordered** (line 1142-1159): Falls back to `pushNotificationChains` — independent per-task chaining, NOT through the gate ✗

**When is this called?** Line 437-441 path excludes `Message`/errors. This is for changed task snapshots. But looking at the call sites:
- Searched for `sendLegacyTaskSnapshotInOrder` calls: only appears in its own definition. Not called anywhere in the diff!

Wait, let me check the full file:

