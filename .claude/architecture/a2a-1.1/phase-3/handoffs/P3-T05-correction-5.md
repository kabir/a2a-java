# P3-T05 correction 5 handoff

**Status:** The mixed status-and-artifact wrapper regression is addressed in
the candidate. The required selector and retained correction regressions pass.
This is not an independent acceptance; stop for a separate Sol medium/normal
review. Do not start P3-T06.

## Assignment and provenance

- Issue #1303, confirmed. Worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, HEAD `81dac751c1ab1ce405fc579c2a4873559ad38813`.
- Correction 5 started at 14:12 UTC on 2026-11-20, following the correction-4 re-review recorded at 14:11 UTC. The two-hour assignment deadline is 16:12 UTC.
- Requested implementation model/role per project policy: Luna medium/normal. Serving model telemetry was unavailable. The pending independent review is Sol medium/normal.
- The reproduced findings remain in [P3-T05-review.md](../reviews/P3-T05-review.md#correction-4-independent-re-review--2026-11-20-1411-utc). The review's accepted T04 control and earlier correction dispositions remain unchanged.
- `test.log` remains untracked and was not read, recreated, removed, written, or staged. Correction 5 Maven output is under `/private/tmp/a2a-p3-t05-correction5-*.log`.
- No commit, push, PR, external communication, agent, full-reactor rebuild, T06/T07/JPA/cluster work, or Phase 4/5 work.

## Correction

The correction-4 special case routed a single-status proposal through the old
status sender seam. For a mixed proposal the default batch method still sent a
summary through optional `sendLegacyTaskSnapshot`, then marked every
canonical event as covered. A wrapper inheriting the no-op hook therefore
delivered no summary. A wrapper forwarding an equal copy of the status missed
identity-based registration and generated a second legacy Task summary. The
independent review's T04 control delivered one summary for both wrappers.

The default batch sender now chooses the first status event as the one legacy
summary route for any batch containing a status. It dispatches that event
through `sendTaskProposalStatusNotification` without registering it in the
scoped event context. Every other event remains in the context and is forwarded
in original batch order. This preserves canonical 1.1 ordering, lets the base
status method select exactly one final Task summary for older configs, and
does not depend on the optional no-op hook. It also handles wrappers that
rebuild the status as an equal copy without widening matching to equal-valued
events. Batches without a status continue to use the optional legacy summary
hook and the full scoped context. The new `withBatchExcept` scope excludes the
status summary event by object identity only.

Two new worker-forwarding tests cover status-plus-artifact proposals. One
wrapper forwards the old notification/status methods and inherits the no-op
snapshot hook; the other forwards the snapshot hook and reconstructs the status
with `TaskStatusUpdateEvent.builder(event).build()`. Both assert exactly one
legacy proposal Task containing the artifact and the canonical status and
artifact once at 1.1. They also cover active-task no-op behavior: the inherited
hook sends nothing, while the forwarding hook delivers one no-op Task.
Correction-4's two status-only wrapper tests and correction-3's full worker
wrapper/multi-event test remain intact.

The new mixed-proposal tests failed before the change: the inherited-hook
wrapper delivered zero proposal summaries; the copied-status wrapper delivered
three legacy Task summaries where two (no-op plus proposal) were expected.
After the production change, both focused cases pass.

## Verification evidence

Maven commands used the installed Byte Buddy 1.18.8 javaagent at
`/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar`,
disabled failure-ignore, and were bounded by `/usr/bin/timeout -k 30s 1200s`:

```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#statusAndArtifactProposalUsesStatusHookWhenWrapperInheritsNoOpSnapshotHook+statusAndArtifactProposalWithCopiedStatusKeepsOneLegacySummary' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=ExistingTaskProposalStreamLifecycleTest,CustomEventQueueCompatibilityTest,ExecutorSubmissionTest,TaskManagerSnapshotTest,FullTaskProposalCompatibilityTest,ResultAggregatorTest,PushNotificationSenderTest*,AtomicTaskStoreContractTest,EventQueueTest,MainEventBusProcessorPushNotificationOrderTest,CommittedPublicationTest,EventConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/queue-manager-replicated/core -am '-Dtest=ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc -am '-Dtest=JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess+testOnMessageSendStreamMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/opentelemetry/server -am -Dtest=OpenTelemetryPushNotificationSenderDecoratorTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test
```

| Evidence | Executed | Failures | Errors | Skips | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `/private/tmp/a2a-p3-t05-correction5-red.log` | 2 | 2 | 0 | 0 | Expected pre-fix reproduction of both mixed-wrapper failures |
| `/private/tmp/a2a-p3-t05-correction5-green.log` | 2 | 0 | 0 | 0 | Focused mixed-wrapper tests pass |
| `/private/tmp/a2a-p3-t05-correction5-assertion-attempt.txt` | 2 | 2 | 0 | 0 | Preserved output excerpt from an intermediate artifact-shape assertion failure; normalized absent metadata from null to `{}` |
| `/private/tmp/a2a-p3-t05-correction5-required.log` | 44 | 0 | 0 | 0 | Required plan selector passes |
| `/private/tmp/a2a-p3-t05-correction5-server.log` | 205 | 0 | 0 | 0 | Retained server-common regressions pass |
| `/private/tmp/a2a-p3-t05-correction5-replication.log` | 1 | 0 | 0 | 0 | Replication passes |
| `/private/tmp/a2a-p3-t05-correction5-bindings.log` | 8 | 0 | 0 | 0 | JSON-RPC, gRPC and 0.3 binding assertions pass |
| `/private/tmp/a2a-p3-t05-correction5-otel.log` | 9 | 0 | 0 | 0 | Explicit OpenTelemetry server selector passes |

The retained regression aggregate is **223** (205 + 1 + 8 + 9). Selectors
overlap, so this is not a distinct-test count. The required selector executed
44 tests, and the focused selector executed two new tests. All final
verification selectors have zero failures, errors, or skips. Earlier failed
evidence, including corrections 1–4 and the accepted T04 reviews, remains in
its prior handoffs and review record.

## Changed-source provenance

Correction 5 changes `PushNotificationSender.java`,
`TaskProposalDispatchContext.java`, `PushNotificationSenderTest.java`, the T05
coordination row in `task-list.md`, and this handoff. After `git diff --check`,
Git emitted the known fsmonitor IPC diagnostic during status inspection; the
check exited 0.

SHA-256:

- `PushNotificationSender.java`: `125723501511ad646ba95baee05484daf8f1e4793d3e6fe3a065afca179912ad`
- `TaskProposalDispatchContext.java`: `973a5673e3e752bc00fbe9ff44d5f39686cbeab1964a8f582c66f1dd1ee2f654`
- `PushNotificationSenderTest.java`: `d9536681410c77092c1e72984526704d017edbbbc317a3db4b4c88324fa4e841`
- `task-list.md`: `b70493ef341113ab5696289d966ef468a42084cc28413e3090db9bea374bb579`
- Tracked diff from HEAD: `ccf3b6e8700ffcb5c530c2703c1be187ec8394cfefd9df3bf4749aafa877b839`

## Review boundary

Implementation status and pending review are separate in the task-list row.
Next action: separate Sol medium/normal review of both worker wrappers for
status-plus-artifact proposals, no-op delivery, 1.0/1.1 output, preservation of
multi-event order, and the fresh required/regression evidence. Stop here; do
not start P3-T06.
