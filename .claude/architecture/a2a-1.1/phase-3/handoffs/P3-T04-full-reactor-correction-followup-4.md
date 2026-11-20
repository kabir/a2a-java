# P3-T04 correction follow-up 4 — 2026-11-20

**Disposition — Implemented for separate review.** This correction addresses
the remaining P2 in the [third follow-up review](../reviews/P3-T04-full-reactor-correction-review.md:239).
P3-T05 remains held; no initial materialization or other T05 work was started.

## Assignment record

- Started **2026-11-20 10:58:50 UTC**; fresh deadline **12:58:50 UTC**.
- Requested implementation settings: **Luna, medium reasoning, normal speed**.
  Serving-model telemetry was unavailable for independent verification.
- Worktree branch: `support/a2a-1.1.0`; starting HEAD:
  `a0a5b6b9158f6df1352f8aa017be1be294e84a2d`.
- No subagents, commits, pushes, PRs, or external communication.
- The task index, coordinator checkpoint, accepted reviews, prior handoffs,
  and `test.log` were preserved. Final SHA-256 values:
  task index `5f6d7bc4e8e36ade707caaad84f50fac7f08dc157152f68347d6bba71d2ff0a1`,
  coordinator checkpoint
  `d12aa0d71c58fb52df15119fe6146d2842f99458576c384fdfa036ea3506b9d5`,
  `test.log`
  `f611bf040c02e6af53838c8dda4298d69a916369cbf4236962d0a440fc6fde3d`.

## Correction

`EventQueue.isAwaitingOrdinaryFinalEvent()` now delegates to the established
public `isAwaitingFinalEvent()` hook by default. `ChildQueue` retains its
override, which reports only ordinary final-event counters, so proposal waits
continue using their independent deadline. Existing custom queue subclasses
that implement the public `isAwaitingFinalEvent()` and
`clearAwaitingFinalEvent()` hooks therefore keep the prior timeout behavior.

Added `CustomEventQueueCompatibilityTest` in the out-of-package
`org.a2aproject.sdk.server.requesthandlers` test package. Its custom
`EventQueue` only implements the existing public final-event hooks; it cannot
override the package-private timeout predicate. The test verifies that the
consumer calls the custom clear hook within the ordinary final-event timeout.

## Verification

Maven runs used `/usr/bin/timeout -k 30s 1200s`, `mvn -B -ntp`,
`-Dmaven.test.failure.ignore=false`,
`-Dsurefire.failIfNoSpecifiedTests=false`, and the installed Byte Buddy agent.
Each targeted invocation was bounded to 20 minutes plus 30 seconds for
cleanup. Fresh Surefire reports show:

| Target | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Server-common handoff selector, including custom queue regression | 199 | 0 | 0 | 0 |
| Replicated queue counting selector | 1 | 0 | 0 | 0 |
| Eight binding assertions | 8 | 0 | 0 | 0 |
| OpenTelemetry push-sender decorator | 8 | 0 | 0 | 0 |
| **Total** | **216** | **0** | **0** | **0** |

The 199 server-common cases were `ExistingTaskProposalStreamLifecycleTest`
4, `CustomEventQueueCompatibilityTest` 1, `ExecutorSubmissionTest` 30,
`TaskManagerSnapshotTest` 3, `FullTaskProposalCompatibilityTest` 5,
`ResultAggregatorTest` 19, `PushNotificationSenderTest` 31,
`AtomicTaskStoreContractTest` 15, `EventQueueTest` 35,
`MainEventBusProcessorPushNotificationOrderTest` 4,
`CommittedPublicationTest` 34, and `EventConsumerTest` 18. Replication ran
`ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation`
once. Binding counts were `JSONRPCHandlerTest` 3, `GrpcHandlerTest` 2, and
`JSONRPCHandler_v0_3_Test` 3, covering all eight 1.0/0.3 binding assertions.
The methods were `JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess`,
`JSONRPCHandlerTest#testOnMessageStreamNewMessageMultipleEventsSuccess`,
`JSONRPCHandlerTest#testOnMessageStreamNewMessageSendPushNotificationSuccess`,
`GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess`,
`GrpcHandlerTest#testOnMessageStreamNewMessageSendPushNotificationSuccess`,
`JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess`,
`JSONRPCHandler_v0_3_Test#testOnMessageSendStreamMultipleEventsSuccess`, and
`JSONRPCHandler_v0_3_Test#testOnMessageStreamNewMessageSendPushNotificationSuccess`.
The OpenTelemetry suite ran
`OpenTelemetryPushNotificationSenderDecoratorTest` 8. The three fixture
migrations remain covered.

The new custom-queue regression first failed as intended: **1 test, 1 failure,
0 errors, 0 skips**. It passed after the predicate change: **1 test, 0
failures, 0 errors, 0 skips**; it is included in the final server-common
count. Reports replaced by final runs are preserved in `pre-final/`.

Logs and preserved reports are under
`/private/tmp/a2a-p3-t04-fourth-followup.20261120/`:

- `custom-red.log` and `red/`
- `custom-green.log`
- `server-common-final.log`
- `replication-final.log`
- `bindings-final.log`
- `otel-final.log`
- `pre-final/`

`git diff --check` passed. No full reactor was run. Stop here for the
separately assigned review; do not advance P3-T05.
