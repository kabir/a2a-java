# P3-T05 correction 4 handoff

**Status:** The two correction-3 status-only wrapper findings are addressed in
the candidate. The required selector and retained correction regressions pass.
This is not an independent acceptance; stop for a separate Sol medium/normal
review. Do not start P3-T06.

## Assignment and provenance

- Issue #1303, confirmed. Worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, HEAD `81dac751c1ab1ce405fc579c2a4873559ad38813`.
- Correction 4 work started at 13:45:05 UTC on 2026-11-20. Its two-hour deadline is 15:45:05 UTC.
- Requested implementation model/role per project policy: Luna medium/normal. Serving model telemetry was unavailable. The pending independent review is Sol medium/normal.
- The correction-3 reproductions and dispositions remain in [P3-T05-review.md](../reviews/P3-T05-review.md#correction-3-independent-re-review--2026-11-20-1343-utc). Earlier review dispositions and correction handoffs remain historical evidence.
- `test.log` remains untracked and was not read, recreated, removed, written, or staged. Correction 4 Maven output is under `/private/tmp/a2a-p3-t05-correction4-*.log`.
- No commit, push, PR, external communication, agent, full-reactor rebuild, T06/T07/JPA/cluster work, or Phase 4/5 work.

## Correction

The default batch sender used the optional `sendLegacyTaskSnapshot` hook and
then marked its status event as covered by the batch. A valid old wrapper can
forward `sendTaskProposalStatusNotification` while inheriting the default
no-op snapshot hook. In that case the summary was skipped by the hook and the
status seam suppressed the legacy summary. A wrapper that rebuilt the status
event as an equal copy missed identity-based batch registration, so the base
sender sent a second legacy summary.

`PushNotificationSender.sendTaskProposalBatchNotification` now detects a
single-event batch containing only `TaskStatusUpdateEvent` and routes it
directly through the established status method. It does not call the optional
no-op snapshot hook or register the event in multi-event dispatch context. The
base sender's status method therefore selects one Task summary for older
protocols and the canonical status for 1.1, including when an old wrapper
forwards an equal copied event. It also leaves an inherited no-op snapshot
hook free to skip active-task no-op delivery. Multi-event batches retain their
scoped identity context and prior per-version behavior. The batch method
Javadoc now describes these two routes.

Two integration tests exercise old wrappers that forward through and wait on
a worker thread. The first forwards ordinary and proposal statuses and
inherits the no-op snapshot hook; it checks that the no-op sends nothing while
the status-only proposal sends one legacy Task and one 1.1 status. The second
forwards the snapshot hook and rebuilds the forwarded status with
`TaskStatusUpdateEvent.builder(event).build()`; it checks that the active
no-op and changed proposal each send one legacy Task and that 1.1 receives the
status once.

Before the production change, the two tests failed as intended: the inherited
hook wrapper delivered zero proposal summaries, and the copied-event wrapper
delivered three legacy Task summaries where two were expected including its
active no-op. After the change, both focused tests pass.

## Verification evidence

Maven commands used the installed Byte Buddy 1.18.8 javaagent at
`/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar`,
disabled failure-ignore, and were bounded by `/usr/bin/timeout -k 30s 1200s`:

```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#statusOnlyProposalUsesStatusHookWhenWrapperInheritsNoOpSnapshotHook+statusOnlyProposalWithCopiedStatusKeepsOneLegacySummary' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=ExistingTaskProposalStreamLifecycleTest,CustomEventQueueCompatibilityTest,ExecutorSubmissionTest,TaskManagerSnapshotTest,FullTaskProposalCompatibilityTest,ResultAggregatorTest,PushNotificationSenderTest*,AtomicTaskStoreContractTest,EventQueueTest,MainEventBusProcessorPushNotificationOrderTest,CommittedPublicationTest,EventConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/queue-manager-replicated/core -am '-Dtest=ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc -am '-Dtest=JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess+testOnMessageSendStreamMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/opentelemetry/server -am -Dtest=OpenTelemetryPushNotificationSenderDecoratorTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test
```

| Log | Executed | Failures | Errors | Skips | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `/private/tmp/a2a-p3-t05-correction4-red.log` | 2 | 2 | 0 | 0 | Expected failures for both reproduced wrapper variants |
| `/private/tmp/a2a-p3-t05-correction4-green.log` | 2 | 0 | 0 | 0 | Pass |
| `/private/tmp/a2a-p3-t05-correction4-required.log` | 44 | 0 | 0 | 0 | Pass |
| `/private/tmp/a2a-p3-t05-correction4-server.log` | 203 | 0 | 0 | 0 | Pass, including all `PushNotificationSenderTest` cases |
| `/private/tmp/a2a-p3-t05-correction4-replication.log` | 1 | 0 | 0 | 0 | Pass |
| `/private/tmp/a2a-p3-t05-correction4-bindings.log` | 8 | 0 | 0 | 0 | Pass |
| `/private/tmp/a2a-p3-t05-correction4-otel.log` | 9 | 0 | 0 | 0 | Pass |

The retained regression aggregate is **221** (203 + 1 + 8 + 9). Selectors
overlap, so this is not a distinct-test count. The required plan selector
executed 44 tests. All final selectors reported zero failures, errors, or
skips. This includes the preceding 219-test correction coverage plus the two
new wrapper tests. The earlier handoff's agent path typo is corrected in the
commands above.

## Changed-source provenance

Correction 4 changes `PushNotificationSender.java`,
`PushNotificationSenderTest.java`, the T05 coordination row in `task-list.md`,
and this handoff. The base sender and `TaskProposalDispatchContext` remain
unchanged from correction 3. After `git diff --check`, Git emitted the known
fsmonitor IPC diagnostic during status inspection; the command exited 0.

SHA-256:

- `PushNotificationSender.java`: `fc6efa49414500539baacdc67f76705cb6a50bc14c472eae21e916c5d367a085`
- `PushNotificationSenderTest.java`: `2a30d1c98ea0f400adb0a6ed422c5b458547f3ed75700a0a3255191822a846d9`
- `task-list.md`: `bd0de6b58008a364e225314c5f47414c418706bb22db0b44bf42b4d140224d36`
- Tracked diff from HEAD: `f25cd0e0b17c130ebcb935655d1f475bbc8e58e793a102df295f830639f21a09`

## Review boundary

Implementation status and pending review are separate in the task-list row.
Next action: separate Sol medium/normal review of both status-only wrappers,
the no-op behavior, 1.0/1.1 selection, preserved multi-event wrapper behavior,
and the fresh required/regression evidence. Stop here; do not start P3-T06.
