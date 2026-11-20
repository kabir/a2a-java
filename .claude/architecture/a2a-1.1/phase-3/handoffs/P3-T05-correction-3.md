# P3-T05 correction 3 handoff

**Status:** The correction-2 worker-thread webhook finding is addressed in the
candidate and has focused regression coverage. The required selector and
retained regression selectors pass. This is not an independent acceptance;
stop for a separate Sol medium/normal review. Do not start P3-T06.

## Assignment and provenance

- Issue #1303, confirmed. Worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, expected and actual HEAD `81dac751c1ab1ce405fc579c2a4873559ad38813`.
- Correction 3 work resumed at 12:57 UTC on 2026-11-20. The original P3-T05 assignment deadline remains 13:21 UTC; this correction did not reset it.
- Requested implementation model/role per project policy: Luna medium/normal. Serving model telemetry was unavailable. The pending independent review is Sol medium/normal.
- The correction-2 independent review and prior accepted/rejected dispositions remain in [P3-T05-review.md](../reviews/P3-T05-review.md). Correction 1, correction 2 and original implementation handoffs remain unchanged.
- `test.log` remains untracked and was not read, recreated, removed, written, or staged. Correction 3 Maven output is under `/private/tmp/a2a-p3-t05-correction3-20261120/`.
- No commit, push, PR, external communication, agent, full-reactor rebuild, T06/T07/JPA/cluster work, or Phase 4/5 work.

## Correction

The default sender batch method keeps its dispatch context active while it
forwards the legacy summary and canonical events through the pre-batch sender
methods. That forwarding may synchronously hop through a worker executor. The
old `ThreadLocal` context disappeared on that worker, so the base sender sent
the legacy Task summary and then treated the forwarded status as an unrelated
legacy mutation. A multi-event proposal also leaked covered deltas to older
protocol configurations.

`TaskProposalDispatchContext` now registers the committed event objects by
identity in a concurrent map for the lifetime of the batch dispatch. A worker
forwarding the same event and final Task snapshot can therefore retain the
existing per-version projection. The registration is removed in `finally` when
the synchronous sender call returns. `BasePushNotificationSender` keeps
selecting one Task summary for older versions and canonical events for 1.1.

The existing unchanged-wrapper integration test now forwards all three old
sender methods to a single worker executor and waits for each result. It covers
deferred admission, an active no-op, status-plus-artifact, artifact-only and
status-only proposals. It asserts the exact legacy sequence (external status,
no-op summary, one summary for each proposal, external completion) with no
covered deltas, and six canonical 1.1 events in order. The wrapper does not
override the new batch method.

The regression first failed against thread-local context: 1 test, 1 failure,
0 errors, 0 skips; it observed 10 older-version deliveries instead of 6. The
same test passes with the cross-thread context.

## Verification evidence

Maven commands used the installed Byte Buddy 1.18.8 javaagent, disabled
failure-ignore, and were bounded by `/usr/bin/timeout -k 30s 1200s`:

```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#proposalWebhookVersionSelectionSurvivesDelegatingSenderAndDeferredAdmission' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent/1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=ExistingTaskProposalStreamLifecycleTest,CustomEventQueueCompatibilityTest,ExecutorSubmissionTest,TaskManagerSnapshotTest,FullTaskProposalCompatibilityTest,ResultAggregatorTest,PushNotificationSenderTest*,AtomicTaskStoreContractTest,EventQueueTest,MainEventBusProcessorPushNotificationOrderTest,CommittedPublicationTest,EventConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/queue-manager-replicated/core -am '-Dtest=ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc -am '-Dtest=JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess+testOnMessageSendStreamMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/opentelemetry/server -am -Dtest=OpenTelemetryPushNotificationSenderDecoratorTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test
```

| Log under `/private/tmp/a2a-p3-t05-correction3-20261120/` | Executed | Failures | Errors | Skips | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `red.log` | 1 | 1 | 0 | 0 | Expected failure against thread-local context; 10 legacy deliveries vs 6 |
| `green.log` | 1 | 0 | 0 | 0 | Initial pass with cross-thread context before tightening the exact sequence assertion |
| `green-final.log` | 1 | 0 | 0 | 0 | Pass; exact legacy sequence and canonical count |
| `required-plan.log` | 44 | 0 | 0 | 0 | Pass |
| `regression-server-final.log` | 201 | 0 | 0 | 0 | Pass |
| `regression-replication.log` | 1 | 0 | 0 | 0 | Pass |
| `regression-bindings.log` | 8 | 0 | 0 | 0 | Pass |
| `regression-otel.log` | 9 | 0 | 0 | 0 | Pass |

The retained correction regression total is **219** (201 + 1 + 8 + 9); the
selectors overlap and this is not a distinct-test count. All eight binding
assertions, replication, explicit OpenTelemetry server coverage, custom queue,
proposal lifecycle and webhook projection ran. The required plan selector
executed 44 tests. Final focused and regression runs reported zero failures,
errors or skips. The tracked diff passed `git diff --check`; Git also emitted
the existing fsmonitor IPC diagnostic while reading status.

Earlier failure evidence and accepted reviews in the correction 1/2 handoffs
remain intact. The correction 2 report's findings for stream completion,
direct Message behavior, opening metadata isolation, creation-race handling
and unary snapshot ordering remain preserved; this correction only addresses
the worker-thread sender context.

## Changed-source provenance

Correction 3 changed `TaskProposalDispatchContext.java`,
`PushNotificationSenderTest.java`, and the P3-T05 coordination row in
`task-list.md`. SHA-256:

- `TaskProposalDispatchContext.java`: `fe48fd74a6cf5926d4c84e23247473acb4a72d85998134351e88258e54736926`
- `PushNotificationSenderTest.java`: `c2e9fcf09bf0fe473340b2a2c38279f48d187ff26a32c2b26326181e52e370ee`

The larger P3-T05 candidate sources and prior verification evidence are
recorded in the [correction 2 handoff](P3-T05-correction-2.md) and earlier
handoffs.

## Review boundary

The task-list row separates implementation from independent review. Next
action: a separate Sol medium/normal review of the worker-thread forwarding
regression and batch-scoped event identity registration, plus confirmation
that the prior correction findings and regression evidence remain intact.
Stop at this handoff; do not begin P3-T06.
