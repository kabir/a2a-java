# P3-T10 review A (fresh-context Bob reviewer, not Sol)

Start: Tue Nov 24 16:48:37 UTC 2026 (date -u)

## A1. Gson round trip
`ReplicatedEventQueueItem` holds `batchEvents` (`List<StreamingEventKind>`), `batchTask` (`Task`) and `batchRevision` (`Long`) as plain fields (ReplicatedEventQueueItem.java:31-35, set at :95-97). Both strategies serialize them with `JsonUtil.toJson`/`fromJson` (ReactiveMessagingReplicationStrategy.java:34,47,59). Gson takes the element type from the field's generic type. It therefore applies the registered `StreamingEventKindTypeAdapter` (JsonUtil.java:306, 993-1069). That adapter writes a wrapper keyed by `value.kind()` and reads it back by member name (task / message / statusUpdate / artifactUpdate / TaskMessageUpdateEvent). So the polymorphic type survives by design for Task, Message, TaskStatusUpdateEvent, TaskArtifactUpdateEvent and TaskMessageUpdateEvent. One caveat: `CommittedTaskBatch.generationOf` (CommittedTaskBatch.java:74-87) throws for a bare `Message`, so a direct `Message` is not a valid batch member. The "Message" case in the question therefore cannot occur in a committed batch.

**GAP (Low/Medium, test coverage):** `CommittedReplicationTest` only builds batches from `TaskStatusUpdateEvent` (`status()` / `batch()` helpers, lines 93-108). The only round-trip test is `committedEnvelopeSerializesWithOpaqueBatchAndRoundTripsIdentity` (:150), and it asserts only identity, generation positions and revision. `rebuilt.events()` is never compared to the original with `assertEquals`. `EventSerializationTest` has no committed-batch case (grep for committed/batch found nothing). I found no test where a `TaskArtifactUpdateEvent`, `TaskMessageUpdateEvent` or `Task` event goes through the batch envelope, and none checks that `batchTask` equals the original snapshot task.
- Violated requirement: the envelope must carry the original ordered events.
- Discriminating check: a unit test builds a batch of `[TaskArtifactUpdateEvent gen3, TaskMessageUpdateEvent gen4, TaskStatusUpdateEvent gen5]` plus a `Task` snapshot. It runs `JsonUtil.fromJson(JsonUtil.toJson(ReplicatedEventQueueItem.committed(b, E, "o")), ReplicatedEventQueueItem.class).committedBatch()` and asserts `assertEquals(b, rebuilt)` and `instanceof` per element.

## A2. Terminal-batch guard
`enqueueReplicated` (ReplicatedQueueManager.java:226-235) exempts `isClosedEvent()`, `isTaskEvent()` and `isCommittedBatch() && isCanonicalFinal()` from the `isTaskActive` check. For a canonical-final batch on a task unknown locally, `delegate.get` returns null and `delegate.createOrTap` creates a MainQueue (:255-258). Only the temporary child is closed, in the `finally` block (:271-278). The MainQueue is closed only by later events:
- The ordered poison pill goes through `enqueueReplicated`, which is exempt, but I did not trace how `QueueClosedEvent` closes the MainQueue.
- `MainEventBusProcessor.deliverCommittedBatch` calls `callback.onTaskFinalized` for a final batch (MainEventBusProcessor.java:780-785). I found no production caller of `setCallback`; the grep hits were all in tests. In production the callback is probably NOOP, so delivery of the final event does not by itself close or remove the queue.
- `InMemoryQueueManager.getCleanupCallback` (:176-210) removes a queue only if `isTaskFinalized` is true.

**Finding A2-1 (Medium, leak, plausible but not executed):** the terminal batch can be delivered after its pill.
- Where: `onReplicatedEvent` (:157-163) and `CommittedBatchSequencer.runAfterGeneration` (:61, `lastDelivered < 0` means run now).
- Scenario: a node has no sequencer state for task T. The pill (`closedAfterGeneration=N`) arrives first and runs immediately. This is the behaviour pinned by `poisonPillBeforeAnyBatchClosesImmediatelyAndNeverMutatesTheStore` (CommittedReplicationTest:234).
- Then the terminal batch arrives. The canonical-final exemption lets it through, and `enqueueReplicated` creates a fresh MainQueue for T that no pill will ever close.
- Violated requirement: the terminal batch is forwarded before the ordered pill, and no queue is leaked.
- Discriminating check: on a receiver that has no queue for T, send `stampedClosed(T, E, "o", 2L)` and then `remote(batch(T, 2, COMPLETED, 2))`. Assert that the manager's queue map is empty afterwards, or that the queue is closed. I did not run it.

**Test gap (Low):** `remoteNonTerminalBatchForAnInactiveTaskIsSkippedButATerminalOneIsDelivered` (:245) pre-creates the queues with `receiver.createOrTap("expired-final")`. It therefore never exercises the create-MainQueue branch for an unknown task, and it does not assert that the queue is closed afterwards. `reversedTerminalBatchAndPoisonPillStillCloseOnlyAfterTheTerminalBatch` (:407) covers pill closure, but only for a pre-existing queue.

## A3. CommittedBatchSequencer
Checked from the full source, read at lines 1-180.
- **Locking.** Locks are taken in the order `tasks` and then `sequence`, and never nested the other way, so there is no deadlock. `offer` runs delivery and the deferred action while holding the per-task monitor (:45-47, :130, :142). Same-task offers are therefore serialized and delivered in order. The cost is that `enqueueReplicated` can block on the MainQueue semaphore while holding the task lock. This only blocks the same task, so I rate it Low, but it is a potential stall.
- **Outcomes.** DELIVERED, DUPLICATE, STALE, BUFFERED, CONFLICT and OVERFLOW have no off-by-one that I could see.
  - `first > lastDelivered+1` buffers (:102).
  - `pending.remove(cursor+1)` drains contiguously (:122).
  - The overlap tests are inclusive on both ends (:111, :152).
  - A first-seen batch is always delivered, which is documented.
  - OVERFLOW is checked before the overlap scan, but only after the same-`first` duplicate and conflict check (:103-109). That is fine.

**Finding A3-1 (Medium, wedge on delivery failure):** `record()` advances `lastDelivered` before delivery (:128). If delivery throws, the remaining ready batches are re-inserted into `pending` (:133-135), but they now sit exactly at `lastDelivered+1`. Nothing drains them: `offer` drains only after a successful delivery of a later batch.
- The retry of that same batch is not blocked. It passes `screen` (it is not in `delivered` and `last > lastDelivered`), is delivered, and leaves the stale `pending` entry behind.
- That leftover entry consumes the MAX_PENDING budget, and `pendingCount` over-reports.
- Violated requirement: a delivery exception cannot leave the sequencer wedged.
- Discriminating check: offer batch 1 with a delivery that throws, while batch 2 was buffered with its position already ready. Then offer 3. Assert that 2 and 3 are delivered in order, and that `pendingCount == 0`.

**Finding A3-2 (Medium, pill never fires):** the deferred action runs only on the success tail of `offer` (:139-143). Two paths skip it:
- The terminal batch's delivery throws. `throw failure` at :136 skips the deferred pill, and later offers return STALE or DUPLICATE from `screen` before reaching :139.
- A batch is rejected for any reason after a pill is parked.
In both cases the ordered pill is stranded, and the queue stays open. This interacts with A2-1.
- Discriminating check: park a pill for generation 2, make the delivery of batch 2 throw, then call `runAfterGeneration` again. Assert that the pill still runs.

**Finding A3-3 (Low):** LRU eviction at MAX_TASKS=4096 (:33-38) drops the per-task state, including a parked deferred pill and the delivered history.
- A replay of an older batch for an evicted task is then treated as first-seen and delivered, so the generation moves backwards.
- A thread that already holds the evicted `TaskSequence` object (obtained at :43/:57) can park a pill on an orphaned object.
- The documented bound is acceptable, but the doc should say so. I checked this only in the sequencer source and did not check the docs.

## A4. Receive gating (ReplicatedQueueManager.java:151-205; line numbers verified from source)
- **Legacy-only node (`epoch == null`, :176-184).** A committed envelope is rejected with a warning (:177-181). Everything else returns true, so the legacy path continues to `enqueueReplicated`.
- **Own-origin drop (:186-190).** `originId.equals(item.getOriginId())` is dropped. This applies only under an epoch.
- **Unversioned and wrong-epoch (:191-196).** `itemEpoch == null || !equals` is rejected. The generation-0 half of "unversioned" is not checked in `acceptedByEpoch`. Generation validation is done in `ReplicatedEventQueueItem.committedBatch()` (:148-168), which calls `onCommittedEnvelope` (:200-205 region). That covers the batch generation consecutive-range and snapshot-end checks.
- **Bare event under an epoch (:197-202).** `!isCommittedBatch() && !isClosedEvent()` is rejected.
- **Corroborating tests:** `delayedUnversionedAndWrongEpochRecordsAreRejectedAfterMigration` (:311), `ownBroadcastEchoedByTheBrokerIsDropped` (:333), `committedEnvelopeIsRejectedByALegacyOnlyNode` (:348), `stampedBareEventIsRejectedUnderAnEpochSoItCannotReachTheSharedStoreAgain` (:451).
- **NOT verified:** that the legacy branches are byte-for-byte unchanged. I did not run `git diff HEAD -- <file>`. Judging by the code alone, the epoch==null branch returns true for non-committed items, and `onReplicatedEvent` then calls `enqueueReplicated` as before. The only visible delta on that path is the added canonical-final exemption, which cannot be true for a legacy item.
- **Residual (Low):** a stamped pill with a null `closedAfterGeneration` under an epoch skips the sequencer and is enqueued at once (:157-165). That is consistent with `finalizationWithoutACommittedBatchUnderAnEpochSendsOnlyAStampedPoisonPill`.

## A5. Docs claims
NOT EXAMINED. I did not read `docs/content/dev/extra/replicated-queue-manager.md`, section 'Committed replication (A2A 1.1 writer epoch)'. This is evidence-incomplete.
- Needed check: `grep -n -i "exactly-once\|receipt\|outbox\|fenc\|replay\|webhook" <doc>`. Compare each hit with these code facts:
  - Receiver-side stamp checks only; no sender authentication (ReplicatedQueueManager.java:166-172 javadoc).
  - Recovery of a permanent gap is deferred to T14 (CommittedBatchSequencer javadoc, :20-22).
  - Remote copies never enter webhook admission (MainEventBusProcessor.java:704-712).

## A6. Prior findings closure
NOT EXAMINED. I did not read P3-T10-review.md lines 13-86, so I cannot state CLOSED or RETAINED for R1-F1, F3, F4, F5, F6 or F7. I could not map them to tests with confidence.
- Tests that probably bear on them: the ordering and deferral tests (:223, :271, :407), the rejection tests (:311, :333, :348, :451), and the legacy-profile test (:357).
- To complete: read the prior review, then for each finding name the test and doc sentence. Mark RETAINED any finding whose condition is A2-1, A3-1, A3-2 or the A1 gap.

### Disposition: Changes required
Reasons: A2-1, A3-1 and A3-2 are plausible leak and wedge defects in the sequencer and terminal-batch path. A1 has no non-status round-trip test. A5, A6 and the git-diff legacy comparison in A4 were not examined, so evidence is incomplete for those items. None of the defects was executed, because the review was read-only.

### Not examined
- A5 (docs section) and A6 (prior findings closure) entirely.
- `git diff HEAD` of ReplicatedQueueManager.java for legacy byte-equality.
- How `QueueClosedEvent` closes the MainQueue on a receiver (EventConsumer.java:240-263 and the manager's close wiring).
- Whether any production code sets the `MainEventBusProcessorCallback`.
- The `ReplicationStrategy` and `ReactiveMessagingReplicationStrategy` diffs beyond the JsonUtil call sites.
- The JPA, `TaskFinalizedEvent` and `WriterEpochProperty` changes.
- No tests or builds were run, per the read-only constraint. `test.log` was not touched.
Tue Nov 24 17:01:17 UTC 2026
