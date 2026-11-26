# P3-T14 review brief — bounded detection of publication loss

Worktree: `/home/agentuser/a2a-java/.worktrees/t14-wt` (branch `t14-work`, base `0e04f44ce`). Issue #1303.
Read-only review; do not run Maven and do not edit source. Evidence already gathered by the implementer:

- Required check (`/tmp/t14-required1.log`, exit 0): `PublicationGapRecoveryTest` 25, `CommittedPublicationTest` 34,
  `MainEventBusProcessorPushNotificationOrderTest` 8, `ProjectingPublisherTest` 4 = 71 run, 0 failures, 0 errors, 0 skipped.
- Mutation probes (each: break the guarded line, run `PublicationGapRecoveryTest` only, then restore; production sha256 verified
  afterwards). All probe logs are `/tmp/t14-probe-<name>.log`, summaries `/tmp/t14-probe-<name>.result`.

| Probe | Edit | Failing named test(s) |
|---|---|---|
| stranded | `check()` returns `null` instead of `STRANDED_WATERMARK` (PublicationReconciler:651) | `lostTerminalPublicationIsDetected...`, `recoveryCreatesNoMutation...`, `subscriberOfALostTerminalPublication...`, `realSchedulerClosesAStrandedObservation...` |
| discardcovered | skip dropping held events `<= G` (PublicationReconciler:599) | `localCommitsRacingTheAttach...`, `injectedRemoteCommitsRacingTheAttach...`, `itemsQueuedBeforeTheAttach...`, both `subscriptionRegistersItsSink...` |
| preinstall | `complete()` ignores items queued before install (PublicationReconciler:574) | `itemsQueuedBeforeTheAttachAreDiscardedWhenCovered...` (expected [6,7], got [5,6]) |
| snapshotgen | handler passes `0` instead of the snapshot generation (DefaultRequestHandler:1537) | both `subscriptionRegistersItsSink...` |
| detach | handler no longer detaches on rejection (DefaultRequestHandler:1540) | `subscriptionThatFindsATerminalTaskOnTheSnapshotRead...` (1 observation left) |
| overflow | charge check always passes (PublicationReconciler:705) | the 256-event, 4 MiB and held-events buffer tests |
| filtered | message updates not counted (committedGeneration, :237) | `generationsFilteredForAnOlderProtocolView...` |
| stale | `generation <= watermark` discard disabled (:538) | `lateStaleArrivalIsDiscardedAndNeverRedelivered` |
| reordergap | reorder-gap deadline disabled (:636) | `absentIntermediateEventClosesTheObservationAtTheReorderGapDeadline` |
| readdeadline | read deadline never expires (:343) | `authorityReadThatNeverCompletesIsAbandoned...` |
| readfailure | failed read no longer closes (:381) | `failedAuthorityReadClosesTheObservation` |
| terminalcleanup | terminal delivery does not unregister (:688-691) | `terminalDeliveryReleasesTheObservationTheTaskAndTheScheduler` |

Design summary: an observer is one `EventQueue.ChildQueue`. `PublicationReconciler.attach(child)` installs an `Observation`
gate; `ChildQueue.internalEnqueueItem` (EventQueue.java:1223-1235) routes committed-batch items (`LocalEventQueueItem.batchGeneration() > 0`,
set in `MainEventBusProcessor.deliverCommittedBatch` :846-864 for both local and `isReplicated` batches) through the gate;
everything else keeps the original path (`offerToQueue`). `OrderedTaskPublisher` is intentionally NOT modified.

Known, accepted limits (do not flag as defects; flag only if the claim is wrong): ordinary-send path is not wired
(only `onSubscribeToTask`); no CDI auto-wiring (opt-in through `DefaultRequestHandler.builder().publicationReconciler(..)`);
legacy bootstrap and non-batch raw events carry no batch generation and are ungated; broker fencing is a documented precondition.

Finding format: severity (High/Medium/Low/Info), `file:line`, concrete failure scenario, discriminating check. Do not edit
production or test source. Write only your own review file.

## Group A — liveness, bounds, clock, buffer limits, cleanup (reviewer A, `P3-T14-review-A.md`)

A1. Defaults and stated bound. `Limits.DEFAULTS` (PublicationReconciler.java:95-96) must be 5s/5s/10s/256/4 MiB; the Javadoc bound
    (lines 64-71) says about `2 x (interval + read deadline) + gap deadline` = 30s plus scheduler delay. Walk `tick()` (:318),
    `reconcile` (:329), `onRead` (:370) and `check` (:630-652) with the test timeline in
    `lostTerminalPublicationIsDetected...` (PublicationGapRecoveryTest:90): is the claimed bound honest, and is it free of any
    claim of instantaneous repair, retryability guarantee or new error code? DISCRIMINATING CHECK: probe `stranded` and
    `reordergap` fail the named tests; confirm no other test could mask them.

A2. Authority-read bounds. One read per task per tick (coalesced), unfinished read not duplicated, abandoned at its deadline,
    late completion ignored, failure/exception closes only the affected observations (:329-397; tests :189, :203, :215).
    Can a read ever stay in flight forever, or can a stale `onRead` apply a head to a newer observation? DISCRIMINATING CHECK:
    probes `readdeadline`, `readfailure`.

A3. Buffer limits. 256 events / 4 MiB charged for held plus delivered-but-unconsumed items, released on consumption
    (`EventQueue.ChildQueue.noteConsumed` at EventQueue.java:1284/1294, `Observation.consumed` :560-567, `tryChargeLocked` :704-712);
    overflow purges the observer queue and delivers one error (`terminateObservation` EventQueue.java:1349). Is any path able to
    exceed the limits (e.g. duplicate held generation, pre-install items in `complete()` :574-594)? DISCRIMINATING CHECK:
    probe `overflow` fails three tests; slow-demand test (:299) shows no false gap.

A4. Cleanup. No leaked tasks/threads: unregister on terminal delivery (:688-691, :554-556), on child close
    (EventQueue.java:1409 -> `onChildClosed` :619), on failure (`fail` :656-676), on `close()` (:202-216); scheduler started on
    first attach and released with the last observation (:272-306); reader pool is bounded (0-4 daemon threads, 5s idle) and shut
    down by `close()` (:435-467). Tests :341, :360, :375, :505. DISCRIMINATING CHECK: probe `terminalcleanup`; verify the real
    scheduler test checks thread names after cleanup.

A5. Concurrency/locking. Documented lock order (:473-475): observation lock may take child monitor; child monitor reaches only
    `onChildClosed`. Check `admit` (:528-557), `complete` (:570-611), `fail` (:656-676) and `terminateObservation`
    (EventQueue.java:1349-1364) for a deadlock or a lost/duplicated delivery, and that `tickGuarded` (:308-315) cannot kill the schedule.

## Group B — attach-vs-commit ordering, filtered accounting, side effects, unchanged T03/T10/T11 behaviour (reviewer B, `P3-T14-review-B.md`)

B1. Sink before snapshot. In `DefaultRequestHandler.onSubscribeToTask` (:1498-1558) the child is tapped (:1515-1523), attached
    (:1530), and only then is the authoritative snapshot read (:1532) and completed (:1537). Held events `<= G` are discarded,
    `> G` delivered once and in order, items already queued before install are taken (`takeQueuedCommittedItems`
    EventQueue.java:1331). Tests: :424 (local), :429 (injected remote), :456 (pre-install queue), :540/:545 (real handler with a store
    that commits and publishes between attach and snapshot). DISCRIMINATING CHECK: probes `discardcovered`, `preinstall`,
    `snapshotgen`. Is there any window where a commit is neither in the snapshot nor delivered?

B2. Filtered old-version generations. Accounting happens when an item enters the child queue, before
    `VersionedOutputProjector.projectEvent` (which maps events to generation 0) and `ProjectingPublisher`. Verify the code path
    and the test at :390 (older view hides both message updates yet watermark reaches 3 and no failure after ticks).
    DISCRIMINATING CHECK: probe `filtered` fails it. Also check that legacy events (`batchGeneration == 0`) pass through ungated.

B3. No mutation/task failure/webhook/replication. Grep `PublicationReconciler.java` for any call that writes to a TaskStore,
    push sender, replication hook, or queue manager (there must be none besides `TaskManager.readAuthoritativeTask` reads).
    `fail()` (:656-676) only terminates the child via `terminateObservation`. Test :479 asserts the store snapshot is unchanged,
    state stays COMPLETED, pushes and replications empty. Is any other observer, or the main queue, affected by one failure
    (test :90 keeps a healthy second observer)?

B4. Unchanged behaviour. Without a reconciler: `internalEnqueueItem` falls through to the old body (`offerToQueue`,
    EventQueue.java:1223-1245), `LocalEventQueueItem` 7-arg constructor delegates with 0 (LocalEventQueueItem.java:53-58),
    `onSubscribeToTask` keeps its previous order, `MainEventBusProcessor` only passes one extra constructor argument (:846-864).
    Confirm T03 (committed results before visibility), T10 (ordered publication, one final snapshot per batch) and T11
    behaviour are untouched; the required check shows `CommittedPublicationTest`, `ProjectingPublisherTest` and
    `MainEventBusProcessorPushNotificationOrderTest` green. `OrderedTaskPublisher`, webhook dispatch and replication files must
    have no diff (`git diff --stat`).

B5. Rejection/detach and public API. A subscription that finds the task missing or terminal on the snapshot read detaches its
    sink (DefaultRequestHandler:1536-1542; test :567; probe `detach`). New public API is additive only
    (`PublicationReconciler`, `DefaultRequestHandler.Builder.publicationReconciler`, `TaskManager.readAuthoritativeTask`); the
    failure is an existing `InternalError` (no new StreamError/retry code, no protocol field, no version fallback).
