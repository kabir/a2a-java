# P3-T04 correction follow-up 3 — 2026-11-20

**Disposition — Implemented for separate review.** This correction addresses
the remaining P1 in the [second follow-up review](../reviews/P3-T04-full-reactor-correction-review.md:180).
The prior duplicate-enqueue, direct-commit, stalled-proposal, and webhook
findings remain closed. P3-T05 remains held; no initial materialization or
other T05 work was started.

## Assignment record

- Started **2026-11-20 10:52:06 UTC**; fresh deadline **12:52:06 UTC**.
- Requested implementation settings: **Luna, medium reasoning, normal speed**.
  Serving-model telemetry was unavailable for independent verification.
- No subagents, commits, pushes, PRs, or external communication.
- Worktree branch: `support/a2a-1.1.0`; starting HEAD for this correction:
  `a0a5b6b9158f6df1352f8aa017be1be294e84a2d`.
- The task index, coordinator checkpoint, accepted reviews, prior handoffs,
  and `test.log` were preserved. Final SHA-256 values:
  task index `5f6d7bc4e8e36ade707caaad84f50fac7f08dc157152f68347d6bba71d2ff0a1`,
  coordinator checkpoint
  `d12aa0d71c58fb52df15119fe6146d2842f99458576c384fdfa036ea3506b9d5`,
  `test.log`
  `f611bf040c02e6af53838c8dda4298d69a916369cbf4236962d0a440fc6fde3d`.

## Correction

`ChildQueue.isAwaitingFinalEvent()` continues to keep a stream open for either
an ordinary final event or an admitted proposal delivery. A new
`isAwaitingOrdinaryFinalEvent()` signal reports only pending/submitted ordinary
final events. `EventConsumer` now advances and clears its ordinary final-event
timeout using that separate signal, then recomputes the combined wait state.
An older proposal's independent expiry can therefore no longer clear a newer
ordinary terminal event's timeout or close its stream early.

Added production-path regression
`ExistingTaskProposalStreamLifecycleTest#olderProposalTimeoutDoesNotExpireANewerOrdinaryTerminalEvent`.
It blocks ordered bus processing, admits an older no-op Task proposal, queues a
newer ordinary terminal status, and releases the bus while the ordinary final
event is still within its own timeout. It verifies the 1.1 stream stays open
and receives the canonical terminal status before completing. Existing
1.0/1.1 terminal proposal-delivery cases and the stalled-proposal timeout case
remain in the suite.

## Verification

Maven runs used `/usr/bin/timeout -k 30s 1200s`, `mvn -B -ntp`,
`-Dmaven.test.failure.ignore=false`,
`-Dsurefire.failIfNoSpecifiedTests=false`, and the installed Byte Buddy agent.
Each final targeted run was bounded to 20 minutes plus 30 seconds for cleanup.
Fresh Surefire reports show:

| Target | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Server-common handoff selector | 198 | 0 | 0 | 0 |
| Replicated queue counting selector | 1 | 0 | 0 | 0 |
| Eight binding assertions | 8 | 0 | 0 | 0 |
| OpenTelemetry push-sender decorator | 8 | 0 | 0 | 0 |
| **Total** | **215** | **0** | **0** | **0** |

Server-common counts were `ExistingTaskProposalStreamLifecycleTest` 4,
`ExecutorSubmissionTest` 30, `TaskManagerSnapshotTest` 3,
`FullTaskProposalCompatibilityTest` 5, `ResultAggregatorTest` 19,
`PushNotificationSenderTest` 31, `AtomicTaskStoreContractTest` 15,
`EventQueueTest` 35, `MainEventBusProcessorPushNotificationOrderTest` 4,
`CommittedPublicationTest` 34, and `EventConsumerTest` 18. The replicated
selector ran `ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation`
once. Binding counts were `JSONRPCHandlerTest` 3, `GrpcHandlerTest` 2, and
`JSONRPCHandler_v0_3_Test` 3, covering all eight 1.0/0.3 binding assertions.
The OpenTelemetry suite ran
`OpenTelemetryPushNotificationSenderDecoratorTest` 8. The three fixture
migrations and all eight binding assertions remain covered.

The new mixed-timeout regression first failed as intended: **1 test, 1 failure,
0 errors, 0 skips**. It then passed in isolation: **1 test, 0 failures, 0
errors, 0 skips**. Its final run is also included in the server-common count.
The RED report is preserved in `red/`; reports overwritten by the final runs
are preserved in `pre-final/`.

Logs and preserved reports are under
`/private/tmp/a2a-p3-t04-third-followup.20261120/`:

- `mixed-final-red.log` and `red/`
- `mixed-final-green.log`
- `server-common-final.log`
- `replication-final.log`
- `bindings-final.log`
- `otel-final.log`
- `pre-final/`

`git diff --check` passed. No full reactor was run. Stop here for the
separately assigned review; do not advance P3-T05.
