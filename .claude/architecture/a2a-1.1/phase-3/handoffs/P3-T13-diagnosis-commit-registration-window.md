# P3-T13 follow-up diagnosis: commit-to-registration ordering window (review finding B4-2)

> **Disposition 2026-11-26: confirmed (narrower than first stated) and fixed.** See
> [Disposition and fix](#disposition-and-fix--2026-11-26) at the end. The text below is the original diagnosis and is kept unchanged.

Written 2026-11-26 for an independent agent. Source: reading of commit `1d27f803` (branch `t13-work`, base `0e04f44c`). **Nothing below has
been reproduced by a test**; it is derived from the code and from what the live tests do and do not exercise. Status of the claims is marked.

## Upstream state

T14 and T16 are reported as done upstream. This repository (`support/a2a-1.1.0`, `origin/support/a2a-1.1.0`) still ends at `0e04f44c`, so
neither is visible here and I have not read them. Before acting, rebase or compare `t13-work` against upstream and re-check, at minimum, the
anchors below: `MainEventBusProcessor.deliverCommittedBatch`, `EventEnqueueHook`, `OrderedTaskPublisher`, the replicated core
`CommittedBatchSequencer` / `ReplicatedQueueManager`. T14 may already have changed the hook placement or added publisher/gap detection, which
would change this diagnosis. Also check whether the T13 fix (`onCommittedBatchPublished`) conflicts textually with T14.

## What T13 fixed (verified live, test 3 `pausedPublisherNeverLetsSubscribersSeeReversedGenerations`)

A remote committed batch could overtake a local batch whose publication was in flight. Fix: the local batch is registered with the sequencer when
its publication starts (`ReplicatedQueueManager.sendCommitted` -> `CommittedBatchSequencer.localBegin`, `CommittedBatchSequencer.java:82`/`:162`);
remote batches are BUFFERED while `localInFlight > 0` (`:138`); the hold ends after local fan-out via the new hook
`EventEnqueueHook.onCommittedBatchPublished`, called at `MainEventBusProcessor.java:871-877` -> `localEnd` (`:93`, `:173`). Probes M1 and M2
in the handoff show both halves are needed.

## The remaining defect (UNPROVEN, derived from code)

`localBegin` is reached only inside the delivery callback, i.e. AFTER the database commit:

1. `MainEventBusProcessor.processEvent` commits through `updateTaskStore` (`:373`), then
2. `orderedTaskPublisher.publish(slot, batch, deliver).join()` (`:384-388`). `OrderedTaskPublisher` orders only LOCAL reservations; a later local
   slot cannot overtake an earlier placeholder, so the callback can wait for earlier local slots.
3. `deliverCommittedBatch` (`:810`) then runs `admitCommittedPushBatch(...)` (`:818`, webhook-lane admission, which can do database work),
   routes any origin error (`:823-830`), and only then calls `batchHook.onCommittedBatch(batch)` (`:840`) -> `sendCommitted` -> `localBegin`.

Between (1) and `localBegin` the sequencer on this node does not know the batch exists. Another node cannot commit generation N+1 before this
node's N is committed (the guarded store serialises them), but it can commit N+1 and publish it to Kafka, and this node's consumer can call
`CommittedBatchSequencer.offer` inside that window.

What `offer` does in that window depends on the per-task sequencer state (`TaskSequence.offer`, `:132-158`; condition at `:138`):

| State on this node when remote N+1 arrives | Outcome | Ordered? |
|---|---|---|
| `lastDelivered == N-1` (or any value with `N+1 > lastDelivered+1`) | BUFFERED (gap), later released by `localEnd` after local N is fanned out | **Safe** |
| `lastDelivered < 0` (no state: first batch this process sees for the task, process restarted, or the task was evicted from the `MAX_TASKS = 4096` access-order LRU at `:31-38`) | falls through the `lastDelivered >= 0 &&` test, `localInFlight == 0` => DELIVERED immediately | **Reversal possible** |

In the unsafe row, local N then arrives: `localBegin` calls `screen` (`:238`), sees `last <= lastDelivered`, returns STALE and does not record
it; `localInFlight` is still incremented and the local fan-out for N proceeds (`localBegin` gates nothing). Local subscribers see N+1 then N.
Replication of N to the other node is then dropped there as stale (its own lastDelivered >= N+1).

So this is narrower than my handoff states. **Erratum:** `handoffs/P3-T13.md` says "an event-bus backlog"; commit and delivery run on the same
processor thread in `processEvent`, so the window is the publisher-slot wait + push admission + error routing, not the bus queue. It also does not
say the hole is limited to the cold-sequencer case. Treat this file as the corrected statement.

## Why the live tests do not catch it

Test 3 holds A inside the replication strategy (`publish-before`, after `localBegin`), and A's sequencer was warm. No test starts from a cold
sequencer and no barrier sits between commit and the `onCommittedBatch` hook. The mutation probes only cover the post-`localBegin` region.

## Suggested direction (not implemented)

Register the local publication intent at the reservation point rather than at hand-off. `orderedTaskPublisher.reserve(slot)` already runs inside
the store's atomic boundary (`MainEventBusProcessor.java:520-522`) and is described as bounded local bookkeeping. Add an additive default-method
hook (same style as `onCommittedBatchPublished`) that bumps the sequencer's `localInFlight` for the task at reservation, converts to the
`localBegin(batch)` record when the batch is published, and releases on `discard`/rollback and after fan-out. Being conservative (hold from
reservation) is safe because a remote N+1 can only exist after local N is committed. Keep it additive, keep the one-final-snapshot and no-fetch
gates (do not fetch store state to warm the sequencer). Alternative of warming `lastDelivered` from storage is rejected by those gates.
Check that discard and failure paths always release (a leaked hold buffers remote batches up to `MAX_PENDING = 64`, then OVERFLOW).
This is T14's area; coordinate with whatever T14 changed.

## How to prove it (needs the live two-node fixture)

1. Restart node A so its sequencer is cold (as test 5 does; mind the Kafka session-timeout env and the consumer-id wait in `Phase3Cluster.Node.start`).
2. Send a guarded update to A for an existing task and hold A after the commit and before `onCommittedBatch` (a new internal barrier, e.g. in the
   push-admission seam or a store/hook decorator in `quarkus-common`; the existing `publish-before` barrier is too late).
3. Commit N+1 on B while A is held; release A.
4. Assert A's subscribers receive strictly increasing generations. Expected today: `[.., N+1, N]` (failure). After a fix: `[.., N, N+1]`.
5. Add a mutation probe (remove the fix) and a unit test in `CommittedReplicationTest` for the cold-sequencer + registration-at-reserve sequence.
Run on the same combination only (PostgreSQL 16.16, Kafka 4.2.0 SASL, two nodes); claim nothing wider.

## Other retained items that interact

* No release timeout for the hold; an unguarded `cancelSubmittedFinalEvents()` in the `deliverCommittedBatch` catch (`:861-863`) could skip
  `onCommittedBatchPublished` (reviewer B4-1, not reproduced).
* A batch committed but never published (crash, test 5) is recovered only by an explicit GetTask on the surviving node (no Phase 4 replay); the
  other nodes then see a generation gap and drop or buffer later batches according to their sequencer state.
* Capacity (256 events / 4 MiB) and the 30 s attempt-deadline loss were not exercised live.

## Disposition and fix — 2026-11-26

Evaluated after T14 and T16 were merged (HEAD `f95cdcc42`). The T14/T16 changes do not alter the anchors; T14's `PublicationReconciler`
gates per observation, it does not change `CommittedBatchSequencer` or hook placement.

**Verdict: a real defect, but narrower than the diagnosis.** Reproduced by a test (the new test fails with `expected [] but was [3]` when the fix is removed).

* Correction to the diagnosis: `MainEventBusProcessor` has ONE processor thread. A remote batch reaches `deliverCommittedBatch` only through
  that thread (`enqueueReplicated` -> bus). Mutations that commit ON that thread (`processEvent`, `processProposal`) therefore always deliver
  before any later remote batch is processed, whatever the sequencer state. The window exists only for batches committed on a REQUEST thread,
  i.e. `MainEventBusProcessor.commitAndPublish` (ordinary guarded input to an existing task: `commitExistingTaskInput`,
  `commitArtifactOnlyInput`), which publishes concurrently with the processor thread. T13 test 3 exercised that path.
* In that path, with a cold sequencer (no state for the task on this node), a remote N+1 offered between the commit and `localBegin` was
  DELIVERED at once and then local N was stale-screened but still fanned out: `[.., N+1, N]`.

**Fix (additive, request-thread path only):**

| File | Change |
|---|---|
| `server-common/.../events/EventEnqueueHook.java` | default no-op `onCommittedBatchReserved(taskId)` / `onCommittedBatchReleased(taskId)` |
| `server-common/.../events/MainEventBusProcessor.java` | `commitAndPublish` calls `onCommittedBatchReserved` inside the store's slot callback (before the commit is visible to other nodes, next to `orderedTaskPublisher.reserve`) and `onCommittedBatchReleased` in its `finally`, after publish or discard, only if the reserve call succeeded and the hook `handlesCommittedBatches()` |
| `extras/queue-manager-replicated/core/.../CommittedBatchSequencer.java` | `localReserve` / `localRelease`: a separate `localReserved` count buffers remote batches like `localInFlight`; on release with no ordered state the earliest held batch is the starting point (covers a rolled-back local mutation whose place another node took) |
| `extras/queue-manager-replicated/core/.../ReplicatedQueueManager.java` | `ReplicationHook` maps the pair to the sequencer |

No store fetch, no warming of `lastDelivered` from storage (one-final-snapshot / no-fetch gates unchanged); `OrderedTaskPublisher`,
webhook dispatch and T14's reconciler are untouched.

**Evidence** (`-Dmaven.repo.local=/tmp/m2-fix`, online, no `-javaagent` needed): `server-common` 984 tests and `queue-manager-replicated/core` 58 tests
(`CommittedReplicationTest` 31, +3) pass with 0 failures/errors/skips. New: `CommittedReplicationTest.aBatchFromAnotherNodeCannotOvertakeALocalCommitWhoseSequencerStateIsCold`
(real `ReplicatedQueueManager` + `MainEventBusProcessor` + `InMemoryTaskStore`, a barrier after the store commit and before publication) and two
sequencer unit tests. Mutation probe: commenting out the `onCommittedBatchReserved` call makes the integration test fail
(`/tmp/fix-probe.log`); production restored and sha256-verified.

**NOT done / for later tasks (read before touching this area):**

* **T13 live evidence is not refreshed.** The two-node PostgreSQL/Kafka suite (`Phase3AuthorityMultiInstanceTest`) was not re-run here (no container
  images present). The fix touches the path test 3 exercises, so per the T20 rule ("T13 real multi-instance evidence must match final affected sources")
  it must be re-run unchanged on the final sources. The cold-restart + hold-after-commit live scenario described above is still not a live test;
  the new test is in-JVM only and claims nothing about Kafka/PostgreSQL timing.
* **T16-T19:** any NEW production path that commits a mutation on a request/caller thread and publishes it through `OrderedTaskPublisher`
  MUST bracket it with `onCommittedBatchReserved` (inside the slot callback) and `onCommittedBatchReleased` (in a `finally`), as `commitAndPublish`
  does. Paths that commit on the event-bus processor thread need nothing. Prefer reusing `commitAndPublish`.
* **T14 reconciler** (opt-in, wired only in `onSubscribeToTask`, no CDI auto-wiring) would independently hold a later generation behind a missing one
  for an observation it gates. T17-T19 wiring of it is a second layer, not a substitute: the fix above is what keeps `CommittedBatchSequencer`
  itself ordered for observers that are not gated.
* Unchanged retained items: no release timeout for a hold (a leaked hold buffers up to `MAX_PENDING = 64`, then OVERFLOW); reviewer B4-1
  (unguarded `cancelSubmittedFinalEvents()` can skip `onCommittedBatchPublished`) is still open and would also leak a `localInFlight` hold; the
  sequencer's `MAX_TASKS` LRU could in theory evict an entry that holds a reservation.
