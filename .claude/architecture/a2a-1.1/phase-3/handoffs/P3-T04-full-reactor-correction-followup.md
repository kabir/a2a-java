# P3-T04 full-reactor correction follow-up — 2026-11-20

**Disposition — Implemented for separate review.** This correction addresses the
three findings in [the correction review](../reviews/P3-T04-full-reactor-correction-review.md)
against `a0a5b6b9158f6df1352f8aa017be1be294e84a2d` on `support/a2a-1.1.0`.
P3-T05 remains held. Initial materialization and the remaining T05 scope were not
started.

## Assignment record

- Started **2026-11-20 10:21:47 UTC**; deadline **12:21:47 UTC**.
- Requested implementation settings: **Luna, medium reasoning, normal speed**.
  Serving-model telemetry was unavailable for independent verification.
- No subagents, commits, pushes, PRs or external communication.
- The task index, coordinator checkpoint, accepted reviews and `test.log` were
  preserved. Final SHA-256 values: task index
  `5f6d7bc4e8e36ade707caaad84f50fac7f08dc157152f68347d6bba71d2ff0a1`,
  coordinator checkpoint
  `d12aa0d71c58fb52df15119fe6146d2842f99458576c384fdfa036ea3506b9d5`,
  `test.log`
  `f611bf040c02e6af53838c8dda4298d69a916369cbf4236962d0a440fc6fde3d`.

## Corrections

1. **Ordered proposal processing:** `ProposalAdmission.process()` no longer
   permits the production ordered authority to commit directly. `MainQueue`
   records admission before exposing an item to the bus and issues a one-use
   capability for that exact queue and proposal. The processor must claim the
   capability before processing. Injected non-production authorities retain
   their local-result path. Added
   `ExecutorSubmissionTest.productionProposalCannotCommitUntilProcessedByItsOrderedBus`.
2. **Origin stream lifetime:** a child queue tracks pending proposal delivery
   separately from final-event counters. It remains open while an admitted
   proposal is waiting for ordered processing, and the exact expectation is
   resolved on processing, rejection, enqueue failure or immediate close.
   Added `ExistingTaskProposalStreamLifecycleTest`, exercising delayed terminal
   delivery to 1.0 and 1.1 streams.
3. **Sender dispatch intent:** added backward-compatible proposal-status and
   legacy-snapshot methods to `PushNotificationSender`. The base sender performs
   per-config version selection; version-unaware implementations fall back to
   canonical changed status and skip no-op snapshots. Processor dispatch no
   longer branches on the sender's concrete class. The OpenTelemetry decorator
   forwards and instruments both methods. Tests cover a delegating sender, a
   deferred earlier admission, no-op, changed proposal and later delta, plus
   canonical fallback and decorator forwarding.

## Verification

All Maven runs used `-Dmaven.test.failure.ignore=false`,
`-Dsurefire.failIfNoSpecifiedTests=false`, and a finite timeout no greater than
20 minutes plus 30 seconds for cleanup. Fresh Surefire XML reports show:

| Target | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Server-common targeted selector | 193 | 0 | 0 | 0 |
| Replicated queue counting selector | 1 | 0 | 0 | 0 |
| Three binding selectors (1.0 and 0.3) | 8 | 0 | 0 | 0 |
| OpenTelemetry push-sender decorator | 8 | 0 | 0 | 0 |
| **Total** | **210** | **0** | **0** | **0** |

Server-common Surefire counts were `ExistingTaskProposalStreamLifecycleTest`
2, `ExecutorSubmissionTest` 28, `TaskManagerSnapshotTest` 3,
`FullTaskProposalCompatibilityTest` 5, `ResultAggregatorTest` 19,
`PushNotificationSenderTest` 31, `AtomicTaskStoreContractTest` 15,
`EventQueueTest` 34, `MainEventBusProcessorPushNotificationOrderTest` 4,
`CommittedPublicationTest` 34, and `EventConsumerTest` 18. Binding counts were
`JSONRPCHandlerTest` 3 (`testOnMessageStreamNewMessageExistingTaskSuccess`,
`testOnMessageStreamNewMessageMultipleEventsSuccess`,
`testOnMessageStreamNewMessageSendPushNotificationSuccess`),
`GrpcHandlerTest` 2 (`testOnMessageStreamNewMessageExistingTaskSuccess`,
`testOnMessageStreamNewMessageSendPushNotificationSuccess`), and
`JSONRPCHandler_v0_3_Test` 3 (`testOnMessageSendStreamExistingTaskSuccess`,
`testOnMessageSendStreamMultipleEventsSuccess`,
`testOnMessageStreamNewMessageSendPushNotificationSuccess`). The replication count is
`ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation`.
The OpenTelemetry count is `OpenTelemetryPushNotificationSenderDecoratorTest`
8. The required three fixture migrations and all eight binding assertions
remain in place.

Exact selectors and logs:

- Server-common used `-pl server-common -am` with
  `AtomicTaskStoreContractTest,EventConsumerTest,FullTaskProposalCompatibilityTest,ExecutorSubmissionTest,EventQueueTest,CommittedPublicationTest,MainEventBusProcessorPushNotificationOrderTest,ResultAggregatorTest,TaskManagerSnapshotTest,ExistingTaskProposalStreamLifecycleTest,PushNotificationSenderTest`.
  Log: `/private/tmp/a2a-p3-t04-review-corrections.20261120/server-common-final.log`.
- Replication used `-pl extras/queue-manager-replicated/core -am` with
  `org.a2aproject.sdk.extras.queuemanager.replicated.core.ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation`.
  Log: Maven output completed at `2026-11-20T10:36:39Z`; the fresh report is in
  `extras/queue-manager-replicated/core/target/surefire-reports/`.
- Bindings used `-pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc -am`
  and the eight handoff methods. Log:
  `/private/tmp/a2a-p3-t04-review-corrections.20261120/bindings-final.log`.
- OpenTelemetry used `-pl extras/opentelemetry/server -am` with
  `OpenTelemetryPushNotificationSenderDecoratorTest`. Log:
  `/private/tmp/a2a-p3-t04-review-corrections.20261120/otel-final.log`.

Regression evidence before the fixes is retained in the same temporary
directory: the admission bypass test failed before correction;
`stream-red.log` records both delayed-stream cases failing before correction;
`webhook-red.log` records the sender-dispatch test failing before correction.
The webhook test had an initial test-compilation correction (imports/package
setup); its subsequent RED run executed one test and failed on the missing
deliveries. GREEN runs are recorded as `server-common-green.log`,
`otel-green.log`, and `stream-green.log`. No historical failure evidence was
overwritten; earlier Surefire report directories are in `pre-recheck/`.

`git diff --check` passed. No full reactor was run. Stop here for the separately
assigned correction review; do not advance the held P3-T05 task.
