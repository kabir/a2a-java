# P3-T05 correction 6 handoff

**Status:** The artifact-only and copied-artifact webhook findings are
addressed in the candidate. The required selector and retained correction
regressions pass. This is not an independent acceptance; stop for a separate
Sol medium/normal review. Do not start P3-T06.

## Assignment and provenance

- Issue #1303, confirmed. Worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, HEAD `81dac751c1ab1ce405fc579c2a4873559ad38813`.
- Correction 6 started at 14:19:27 UTC on 2026-11-20. Its two-hour deadline is 16:19:27 UTC.
- Requested implementation model/role per project policy: Luna medium/normal. Serving model telemetry was unavailable. The pending independent review is Sol medium/normal.
- The correction-5 artifact reproductions remain in [P3-T05-review.md](../reviews/P3-T05-review.md#correction-5-independent-re-review--2026-11-20-1418-utc). Earlier corrections and dispositions remain historical evidence.
- `test.log` remains untracked and was not read, recreated, removed, written, or staged. Correction 6 Maven output is under `/private/tmp/a2a-p3-t05-correction6-*.log`.
- No commit, push, PR, external communication, agent, full-reactor rebuild, T06/T07/JPA/cluster work, or Phase 4/5 work.

## Correction

The default batch method previously sent a no-status proposal summary through
the optional `sendLegacyTaskSnapshot` hook. A valid older wrapper can inherit
that no-op hook, so its artifact-only proposal reached no legacy destination.
The batch still registered the canonical artifact by object identity; when a
wrapper rebuilt the artifact event as an equal copy, it missed registration
and BasePushNotificationSender sent the covered artifact update to older
destinations after already sending the summary.

For a batch without a status event, the default sender now forwards its final
Task summary through the existing `sendNotification(Task, Task)` seam under a
scoped legacy-summary marker. BasePushNotificationSender recognizes that
exact summary object during the scoped call and sends it only to 0.3/1.0
destinations. The optional no-op hook remains available to skip active-task
no-op delivery; changed proposal delivery no longer depends on it.

Canonical artifact forwarding has a second, narrower scope around each
synchronous `sendNotification` call. It carries the expected artifact event
and final snapshot identity across the wrapper's worker-thread handoff.
BasePushNotificationSender suppresses the legacy artifact projection when a
wrapper forwards an equal copied event during that call. The scope closes
before any later notification. The regression then forwards an equal-valued
ordinary artifact after proposal dispatch and checks that it still reaches
both protocol versions. The existing batch registry remains identity-based;
there is no global equal-event match.

For batches containing status, the status event remains the one legacy summary
route from correction 5; artifact forwarding uses the scoped copy context.
The original event loop order remains intact for 1.1. This keeps status-only,
mixed status/artifact, and artifact-only routes consistent, while no-op
snapshot delivery remains independent.

Two worker-thread integration tests were added. The artifact-only case uses a
wrapper that forwards notifications and inherits the no-op snapshot hook; it
asserts no active no-op output, one legacy Task containing `answer`, and one
canonical 1.1 artifact. The mixed case forwards the no-op snapshot and status,
rebuilds artifact updates with `TaskArtifactUpdateEvent.builder(update).build()`,
and checks one legacy summary containing the artifact, one status and one
artifact at 1.1, and ordinary delivery of an equal-valued artifact after the
proposal scope ends.

The tests failed before the change: the inherited-hook artifact-only wrapper
produced no legacy summary, and the copied-artifact wrapper delivered two
legacy artifact updates (proposal copy and later ordinary equal event) after
its no-op and proposal Task summaries. After the change, both focused cases
pass.

## Verification evidence

Maven commands used the installed Byte Buddy 1.18.8 javaagent at
`/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar`,
disabled failure-ignore, and were bounded by `/usr/bin/timeout -k 30s 1200s`:

```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#artifactOnlyProposalUsesTaskSummaryWhenWrapperInheritsNoOpSnapshotHook+copiedArtifactProposalUsesOneTaskSummaryAndLeavesEqualOrdinaryArtifactVisible' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=ExistingTaskProposalStreamLifecycleTest,CustomEventQueueCompatibilityTest,ExecutorSubmissionTest,TaskManagerSnapshotTest,FullTaskProposalCompatibilityTest,ResultAggregatorTest,PushNotificationSenderTest*,AtomicTaskStoreContractTest,EventQueueTest,MainEventBusProcessorPushNotificationOrderTest,CommittedPublicationTest,EventConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/queue-manager-replicated/core -am '-Dtest=ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc -am '-Dtest=JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess+testOnMessageSendStreamMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/opentelemetry/server -am -Dtest=OpenTelemetryPushNotificationSenderDecoratorTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test
```

| Evidence | Executed | Failures | Errors | Skips | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `/private/tmp/a2a-p3-t05-correction6-red.log` | 2 | 2 | 0 | 0 | Expected reproduction of both artifact findings |
| `/private/tmp/a2a-p3-t05-correction6-green.log` | 2 | 0 | 0 | 0 | Focused artifact-only and copied-artifact tests pass |
| `/private/tmp/a2a-p3-t05-correction6-compile-attempt.txt` | 0 | 0 | 1 compile error | 0 | First focused build lacked the `TaskArtifactUpdateEvent` import; import added and focused tests rerun successfully |
| `/private/tmp/a2a-p3-t05-correction6-required.log` | 44 | 0 | 0 | 0 | Required plan selector passes |
| `/private/tmp/a2a-p3-t05-correction6-server.log` | 207 | 0 | 0 | 0 | Retained server-common regressions pass, including 38 sender tests |
| `/private/tmp/a2a-p3-t05-correction6-replication.log` | 1 | 0 | 0 | 0 | Replication passes |
| `/private/tmp/a2a-p3-t05-correction6-bindings.log` | 8 | 0 | 0 | 0 | JSON-RPC, gRPC and 0.3 binding assertions pass |
| `/private/tmp/a2a-p3-t05-correction6-otel.log` | 9 | 0 | 0 | 0 | Explicit OpenTelemetry server selector passes |

The retained regression aggregate is **225** (207 + 1 + 8 + 9). Selectors
overlap; this is not a distinct-test count. The required selector executed 44
tests, and the focused selector executed two new tests. All final test
selectors reported zero failures, errors, or skips. Earlier failed evidence,
including corrections 1–5 and accepted reviews, remains in its prior handoffs
and review record.

## Changed-source provenance

Correction 6 changes `PushNotificationSender.java`,
`TaskProposalDispatchContext.java`, `BasePushNotificationSender.java`,
`PushNotificationSenderTest.java`, the T05 coordination row in `task-list.md`,
and this handoff. `git diff --check` passed; Git emitted the known fsmonitor IPC
diagnostic during the check and exited 0.

SHA-256:

- `PushNotificationSender.java`: `9b4c16ed6cae9372237d1eb9b0e2212ff547b039ac4158dcf75fb52ca5856ac8`
- `TaskProposalDispatchContext.java`: `fceed3e0dc7c49aab954bec5e9760a85a4b4df048a8320b7433818e7f526f7a2`
- `BasePushNotificationSender.java`: `e6a18b905a6e420a11d13557dcfabd492c202adc2589f7cb416a295d5cf76157`
- `PushNotificationSenderTest.java`: `2e9a8f255f891628af76b2333dccb24a1bdf181494faf69e6a8ee847ad6bb757`
- `task-list.md`: `bd480535eb02d77b35536eaf17b3ff8878685726a0c42d37ae6b7d293afd2812`
- Tracked diff from HEAD: `763beb907f501e5177f07418a9e30e4f0f586a73917c74123ab05ec4a7ac144a`

## Review boundary

Implementation and pending review are separate in the task-list row. Next
action: separate Sol medium/normal review of artifact-only summary delivery,
copied-artifact suppression, the ordinary equal-event control, mixed-version
event order, and the fresh required/regression evidence. Stop here; do not
start P3-T06.
