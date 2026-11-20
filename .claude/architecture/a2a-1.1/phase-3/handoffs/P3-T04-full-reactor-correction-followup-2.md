# P3-T04 correction follow-up — 2026-11-20

**Disposition — Implemented for separate review.** This assignment fixes the
two remaining P1 findings in the [10:37 follow-up review](../reviews/P3-T04-full-reactor-correction-review.md#follow-up-review--2026-11-20-1037-utc)
on the uncommitted `support/a2a-1.1.0` worktree at
`a0a5b6b9158f6df1352f8aa017be1be294e84a2d`. The earlier webhook finding remains
closed. P3-T05 remains held; no initial materialization or other T05 work was
started.

## Assignment record

- Started **2026-11-20 10:39:46 UTC**; deadline **12:39:46 UTC**.
- Requested implementation settings: **Luna, medium reasoning, normal speed**.
  Serving-model telemetry was unavailable for independent verification.
- No subagents, commits, pushes, PRs or external communication.
- The task index, coordinator checkpoint, accepted reviews, prior handoff and
  `test.log` were not edited. Final SHA-256 values: task index
  `5f6d7bc4e8e36ade707caaad84f50fac7f08dc157152f68347d6bba71d2ff0a1`,
  coordinator checkpoint
  `d12aa0d71c58fb52df15119fe6146d2842f99458576c384fdfa036ea3506b9d5`,
  `test.log`
  `f611bf040c02e6af53838c8dda4298d69a916369cbf4236962d0a440fc6fde3d`.

## Corrections

1. **Duplicate enqueue and direct production authority:** `ProposalAdmission`
   now binds queue admission to the first exact `QueueAdmissionToken`. A
   duplicate MainQueue attempt is rejected before it can wait for capacity or
   change the first attempt's accepted/rejected state. Rollback applies only to
   that claimed token. The authority returned by `forTaskStore()` now rejects
   direct `ProposalAuthority.process()` calls; production mutation remains on
   the capability-gated ordered service path. Added regressions proving direct
   factory use leaves storage unchanged and a failed duplicate attempt leaves
   the original accepted proposal's completion intact and publishes it once.
   Test names: `productionAuthorityReturnedByFactoryCannotCommitDirectly` and
   `failedDuplicateEnqueueCannotCancelTheAlreadyAcceptedProposal`.
   The existing full-task compatibility unit tests now exercise the pure
   `TaskMutationPlanner`; production commits are tested through ordered queue
   admission.
2. **Bounded stream wait:** child queues timestamp each pending proposal
   delivery and expire each expectation independently after the shared
   three-second delivery-wait bound. `isAwaitingFinalEvent()` and the atomic
   close check prune only expired entries; expiry does not change the authority
   result or clear a newer proposal expectation. `EventConsumer` resets its
   final-wait counter after clearing final-event counters. Added a stalled
   processor test that waits past seven seconds, observes stream completion,
   then releases the processor and verifies the ordered Task commit still
   occurs. Existing recoverable-delay tests for 1.0 and 1.1 remain enabled. An
   EventQueue test verifies that expiry of an older proposal preserves a newer
   expectation until its own deadline. Test names:
   `stalledProposalExpiresItsOriginWaitWithoutCancellingTheCommit` and
   `expiringAnOlderProposalWaitPreservesANewerOriginExpectation`.

## Verification

All final Maven runs used `/usr/bin/timeout -k 30s 1200s`, `mvn -B -ntp`,
`-Dmaven.test.failure.ignore=false`,
`-Dsurefire.failIfNoSpecifiedTests=false`, and the installed Byte Buddy agent.
Each run was bounded to 20 minutes plus 30 seconds cleanup. Fresh Surefire
reports show:

| Target | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Server-common handoff selector | 197 | 0 | 0 | 0 |
| Replicated queue counting selector | 1 | 0 | 0 | 0 |
| Eight binding assertions | 8 | 0 | 0 | 0 |
| OpenTelemetry push-sender decorator | 8 | 0 | 0 | 0 |
| **Total** | **214** | **0** | **0** | **0** |

The 197 server-common cases were `ExistingTaskProposalStreamLifecycleTest`
3, `ExecutorSubmissionTest` 30, `TaskManagerSnapshotTest` 3,
`FullTaskProposalCompatibilityTest` 5, `ResultAggregatorTest` 19,
`PushNotificationSenderTest` 31, `AtomicTaskStoreContractTest` 15,
`EventQueueTest` 35, `MainEventBusProcessorPushNotificationOrderTest` 4,
`CommittedPublicationTest` 34, and `EventConsumerTest` 18. Replication ran
`ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation`
once. Binding counts were `JSONRPCHandlerTest` 3,
`GrpcHandlerTest` 2, and `JSONRPCHandler_v0_3_Test` 3, covering the same eight
1.0/0.3 methods:
`JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess`,
`JSONRPCHandlerTest#testOnMessageStreamNewMessageMultipleEventsSuccess`,
`JSONRPCHandlerTest#testOnMessageStreamNewMessageSendPushNotificationSuccess`,
`GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess`,
`GrpcHandlerTest#testOnMessageStreamNewMessageSendPushNotificationSuccess`,
`JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess`,
`JSONRPCHandler_v0_3_Test#testOnMessageSendStreamMultipleEventsSuccess`, and
`JSONRPCHandler_v0_3_Test#testOnMessageStreamNewMessageSendPushNotificationSuccess`.
The OpenTelemetry suite ran
`OpenTelemetryPushNotificationSenderDecoratorTest` 8. The three fixture
migrations and all eight binding assertions remain covered.

A separate focused run of `ExecutorSubmissionTest`,
`ExistingTaskProposalStreamLifecycleTest`, `FullTaskProposalCompatibilityTest`
and `EventQueueTest` passed **73/73**. This count overlaps the server-common
selector and is not added to the total.

Final logs are under
`/private/tmp/a2a-p3-t04-next-correction.20261120/`:

- `server-common-final.log`
- `replication-final.log`
- `bindings-final.log`
- `otel-final.log`
- `focused-final.log`
- `iteration-notes.md` records the first RED results, an intermediate failing
  focused run and the test-harness corrections.

The new regressions first failed as intended: **3 tests, 3 failures, 0 errors,
0 skips**. Surefire XML is preserved in `red/`. The first post-fix focused run
reported 38 tests with 1 failure and 1 error; its duplicate test swallowed the
expected enqueue exception and its no-op fixture omitted the exact current Task
status. Those test issues were corrected; the 73-test focused run and all final
selectors passed. Original reports replaced by the final selectors are
preserved in `pre-full-targeted/`. A first RED compile attempt also needed a
test method to declare `throws Exception`; this is recorded in
`iteration-notes.md`.

`git diff --check` passed. No full reactor was run. Stop here for the separately
assigned review; do not advance P3-T05.
