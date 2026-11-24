# P3-T10 correction review C (fresh-context Bob reviewer, not Sol, not an independent-model review)
Start: Tue Nov 24 17:31:50 UTC 2026
Method: static reading only (CommittedBatchSequencer, ReplicatedQueueManager, MainEventBusProcessor diff, TaskMutationPlanner, CommittedTaskBatch, ReplicatedEventQueueItem, JpaTaskMutationTransactions, test file). The one allowed mvn command was NOT run; evidence logs were not opened.

## C1. A2-1 (late terminal batch after its pill) — CLOSED (with two low residuals)
Trace: `onReplicatedEvent` (ReplicatedQueueManager.java:150-166) -> epoch-stamped pill with `closedAfterGeneration` -> `sequencer.runAfterGeneration(task, G, ...)` (CommittedBatchSequencer.java:57-75). No state exists, so `computeIfAbsent` makes a TaskSequence with lastDelivered=-1 and `runNow` is true. `closedThrough = max(-1, G)` is set at :66, then the pill runs `enqueueReplicated` and a pill-only MainQueue is made (closed events bypass isTaskActive). A later terminal batch goes `onCommittedEnvelope` (:204-224) -> `sequencer.offer` -> `screen()` (:174-187). `delivered` is empty, so no overlap/CONFLICT, and `last <= closedThrough` returns STALE (:183). The delivery lambda never runs, so `enqueueReplicated` is not called and no queue is created. Logged at debug as "Ignoring STALE".

Reverse risks:
- `closedThrough` is written in one place only (:66, the runNow branch). Two sub-cases:
  - (a) `lastDelivered >= G`: `closedThrough = G <= lastDelivered`. Redundant with the existing `last <= lastDelivered` stale test, so harmless.
  - (b) `lastDelivered < 0`: the new case.
  - The deferred branch (0 <= lastDelivered < G) does not set it. That is correct, because the terminal batch is still expected.
- Re-opened task, same taskId, higher generation: not reachable. `TaskMutationPlanner.rejectTerminal` (:452-456) refuses fresh mutations on a terminal task, and the pill is stamped only with the terminal batch's last generation (ReplicatedQueueManager.java:308-309). A null-stamped pill takes the unsequenced path and never sets `closedThrough`. A delete-and-recreate of the same id with generations restarting at 1 would be dropped as STALE until the TaskSequence is evicted. This is the same class as the pre-existing `lastDelivered` staleness, not a new defect (Low, informational).
- Pill generation higher than a legitimate later batch of a still-live task: the pill is only emitted after a terminal commit, and nothing legitimate follows a terminal task. Non-terminal batches below G arriving after the close are dropped, which is the desired behaviour.
- MAX_TASKS LRU: `closedThrough` lives inside TaskSequence, so evicting an entry (`removeEldestEntry`, :33-38) also drops the marker. It is NOT retained across eviction. A terminal batch arriving after eviction of its pill's TaskSequence (more than 4096 other tasks touched in between) would again be treated as a first batch. A terminal batch bypasses isTaskActive (ReplicatedQueueManager.java:232), so a queue could be created. That is a bounded, improbable residual, the same eviction-amnesia class already present for `lastDelivered` and dedup. Severity Low; should be mentioned in docs or the T14 gap-handling scope.
- Concurrency: `action.run()` is outside the synchronized block (:72-74), but a batch offered after `closedThrough` is set is already STALE. A batch offered just before could win and deliver, then the pill closes it. No leak.

## C2. A3-1 / A3-2 — CLOSED for code; test discriminates only A3-2 (NEW FINDING F2, Low)
`offer()` (CommittedBatchSequencer.java:101-172):
- `ready` is built by removing consecutive entries from `pending` (:124-131), so `pending` has no leftover entries after a failure. `subList` is gone.
- Each entry is `record()`ed before delivery (:136), so a repeat is a DUPLICATE and not re-run.
- Every ready entry is delivered. The first RuntimeException and the first Error are kept.
- The deferred action is cleared (`deferred = null`, :152) before it runs, so it runs at most once. It runs only if `lastDelivered >= deferredGeneration`, evaluated after all records. It also runs when a delivery failed, which fixes A3-2.
- Throw order: Error first, else the first RuntimeException (:165-170). Later RuntimeExceptions are dropped (not added as suppressed; trivial info loss).
- The caller gets an exception, never a silent DELIVERED. `onCommittedEnvelope` therefore propagates out of the observer, as before.
- Failed delivery is not retried, per the docs sentence.

F2 (Low, test gap): `aFailedDeliveryDoesNotWedge...` (CommittedReplicationTest, lines ~214-235) makes the FAILING batch (gen 3) the LAST ready entry (ready=[2,3]). Against v2 it fails on the pill assertion (`events == ["pill"]`, because v2 rethrew before running the deferred action; this is A3-2). It does not exercise A3-1 itself (a failing batch followed by further ready batches that v2 re-buffered at lastDelivered+1). Discriminating check to add: pending={3,4}, offer 2 whose delivery throws, then assert 3 and 4 were delivered and `pendingCount == 0`.

## C3. B1-1 replication-path matrix — CLOSED for reachable states; UNSPECIFIED edge RETAINED (F3, Low)
Definitions: S = `snapshot.state().isFinal()` (COMPLETED/FAILED/CANCELED/REJECTED; UNSPECIFIED is false). H = `hook.handlesCommittedBatches()` (true only when the epoch is non-null). F = a final-looking event in the batch. R = isReplicated.

| R | H | S | F | Result |
|---|---|---|---|---|
| true | any | any | any | 0 replications. Replicated envelopes go through the early path, which only delivers locally. |
| false | true | false | false | 1: `onCommittedBatch` -> `sendCommitted`. Per-event onEnqueue skipped (`!replicateWholeBatch`). No TaskFinalizedEvent (JPA :141 uses `isFinal()`). |
| false | true | false | true (non-UNSPECIFIED) | Unreachable. The planner appends the terminal status last and rejects later mutations, so F implies S. If it ever occurred: 1 via hook, and consistent with the receiver, which keys on the snapshot. |
| false | true | true | any | 0 via hook and per-event. `ReplicationHook.onEnqueue` returns immediately under an epoch. 1 via `TaskFinalizedEvent` -> `onTaskFinalized` (`sendCommitted(finalBatch)` + stamped pill), when events are non-empty. |
| false | false (legacy hook / null epoch) | any | F=false | per-event `onEnqueue`, as before. |
| false | false | any | F=true | per-event skipped; legacy TaskFinalizedEvent sends status + pill, as before. |

So one path per state, and the hook and finalization predicates are now the same (`isFinal()`). The old per-event `legacyFinal` gate is retained only for non-batch hooks.

UNSPECIFIED edge (F3, Low, latent, not a regression):
- Reachable: `planStatus` (:131-156) does not reject a supplied UNSPECIFIED status. `normalizeProposal` treats `terminalStatus = isFinal()` as false for UNSPECIFIED (:267) but then commits the status. Only later mutations are rejected (:453).
- Path for such a batch: S=false, so `ReplicationHook.onCommittedBatch` -> `sendCommitted` sends it once (same as v2). JPA does not fire TaskFinalizedEvent (`isFinal()` false), so no ordered pill follows.
- Receiver: `ReplicatedEventQueueItem.isCanonicalFinal` -> `CommittedTaskBatch.isFinal` (:69-72) treats UNSPECIFIED as final. It skips the isTaskActive screen, and the MainQueue distribution marks it final / `callback.onTaskFinalized`.
- Whether the receiver's MainQueue is then closed WITHOUT a pill was not traced (Incomplete evidence). The local side already treats it as final via `batch.isFinal()`.
- Verdict: not a v3 defect. A documented-or-fix decision is needed: either use `batch.isFinal()` as the single predicate in both MainEventBusProcessor and JpaTaskMutationTransactions, or document that UNSPECIFIED is replicated without a pill.

## C4. Legacy-only profile — CLOSED
Diff of MainEventBusProcessor (git diff, hunk @@ -702): `batchLegacyFinal` is the same predicate hoisted out of the loop (same value every iteration). For a hook with `handlesCommittedBatches() == false` (default), `replicateWholeBatch` is false, so the gate `!isReplicated && !legacyFinal && !replicateWholeBatch` reduces to the previous `!isReplicated && !legacyFinal`. Per-event `onEnqueue(new LocalEventQueueItem(event, snapshot, false))`, the try/catch and `reportPostCommitFailure` are unchanged. A ReplicatedQueueManager with null epoch returns false from `handlesCommittedBatches` (ReplicatedQueueManager.java:387-389). Other changes in this file (replicated early path, `!isReplicated` guard around push admission) are earlier T10 scope and affect only replicated items.

## C5. Tests
- `nonStatusBatchEventsSurviveTheEnvelopeRoundTripWithTheirTypes`: non-vacuous. It checks the wire form round-trip, identity, generationPositions [3,4,5], and `assertInstanceOf` for Artifact/Message/Status. It fails if subtype information is lost. CLOSED (A1), subject to the unrun suite.
- `aFailedDelivery...`: non-vacuous for A3-2 (see F2); fails against v2 on the pill and `pendingCount` expectations.
- `terminalBatchArrivingAfterItsPillIsDroppedAsStale` (F1, Medium-Low, NEW FINDING — discriminating power unproven): pre-correction code would run the pill immediately (no state), then deliver the late terminal batch (lastDelivered=-1, screen null -> DELIVERED) and `enqueueReplicated` would create or reuse a MainQueue. The test asserts `assertSame(afterPill, manager.get(...))` and zero store mutations/saves. If the pill's queue persists in the manager, the pre-correction code would reuse the SAME queue instance and the old code would pass the assertSame and the zero-mutation asserts. If the pill queue is cleaned up (get returns null), both sides are null and assertSame(null, null) passes even with the correct code, but the old code would create a non-null queue and fail. Which of these holds was not determined. Fix: also assert `assertNotNull(afterPill)` or drive the sequencer directly (`runAfterGeneration` then `offer(...) == STALE` and the delivery lambda never invoked), which is deterministic.

## Findings summary
- F1 Medium-Low: terminal-after-pill test may not discriminate (ReplicatedQueueManager test file, test ~lines 202-212).
- F2 Low: A3-1 (failing batch followed by further ready batches) not exercised.
- F3 Low latent: UNSPECIFIED snapshot replicated by hook without pill; receiver treats as final; three predicates (isFinal vs batch.isFinal vs isFinalEvent) still not unified.
- F4 Low: closedThrough lost on LRU eviction (bounded, same class as existing dedup state).

### Disposition: Accepted within scope
Production logic of all five corrections is sound on reading. F1/F2 are non-blocking test-strengthening requests; F3/F4 are documentation/latent. Not confirmed by execution.

### Not examined
- No Maven run and no log opened (evidence 48/48, 834/834, 95/95, 6/6 not re-verified).
- Whether the receiver MainQueue closes for an UNSPECIFIED batch without a pill (EventQueue distribution/cleanup not traced).
- Whether the pill-created queue persists in `InMemoryQueueManager` after close (decides F1).
- Reactive/other ReplicationStrategy implementations, docs sentence accuracy, thread-safety of the observer under concurrent delivery, and gap handling (T14).
