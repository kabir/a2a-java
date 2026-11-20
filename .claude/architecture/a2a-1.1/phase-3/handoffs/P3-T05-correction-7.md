# P3-T05 correction 7 handoff

**Status:** The two correction 6 sender findings are addressed in the candidate. Required and retained selectors pass. Stop for separate Sol medium/normal review; this handoff does not accept the work or authorize P3-T06.

## Assignment and provenance

- Issue #1303, confirmed. Worktree /Users/kabir/.codex-api/worktrees/3053/a2a-java, branch support/a2a-1.1.0, expected and actual HEAD 81dac751c1ab1ce405fc579c2a4873559ad38813.
- Correction 7 started at 2026-11-20 14:38:26 UTC. Deadline: 2026-11-20 16:38:26 UTC.
- Requested implementation model/role per project policy: Luna medium/normal. Serving model telemetry was unavailable. Separate independent review requested: Sol medium/normal.
- test.log remains untracked and was not read, recreated, deleted, written, or staged. Correction 7 output is under /private/tmp/a2a-p3-t05-correction7-*.log.
- No commit, push, PR, external communication, agent, full-reactor context rebuild, or T06/T07/JPA/cluster/Phase 4/5 work.
- Earlier failures and accepted review dispositions remain in their existing handoffs and review record.

## Correction

The default batch route previously sent a standalone Task summary through the ordinary sender method for artifact-only proposals. A version-unaware sender therefore received both a Task containing the artifact and the canonical artifact event. Copied-artifact matching also used the committed snapshot identity, so an unrelated equal-valued artifact using that snapshot could match while a proposal wrapper was paused.

The default route now forwards canonical events only. When an older wrapper forwards into BasePushNotificationSender, the SDK sender projects a final Task summary to older protocol versions on the first event in a no-status batch, while sending canonical events to 1.1. A version-unaware sender sees only the canonical event. Status batches continue to use their status route for the legacy summary, and later events in that batch remain current-version-only.

Each default batch dispatch passes a deep immutable copy of its committed Task snapshot as a scoped identity. The copy retains the committed state and is not re-read from the store. A wrapper can carry that object through a worker handoff, including when it rebuilds an equal artifact event. An independent ordinary call using the original committed snapshot no longer matches the proposal scope and reaches both protocol versions. Artifact-copy suppression remains limited to the active forwarding call and its dispatch scope.

Two tests reproduce the findings. The version-unaware sender test asserts that an artifact-only proposal produces one canonical event and no Task summary. The paused worker test concurrently sends an equal-valued ordinary artifact using the committed snapshot, then releases the wrapper's copied proposal artifact; it checks one proposal Task summary and delivery of the ordinary artifact to both versions.

The focused tests failed before the production change: the version-unaware sender received [Task, TaskArtifactUpdateEvent], and the concurrent equal ordinary artifact did not reach the 1.0 destination. Both pass after the change.

## Verification evidence

All Maven commands used /usr/bin/timeout -k 30s 1200s, mvn -B -ntp, -Dsurefire.failIfNoSpecifiedTests=false, -Dmaven.test.failure.ignore=false, and this installed agent:

- Common agent argument: -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar

| Command / log | Executed | Failures | Errors | Skips | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Initial focused red compile attempt, /private/tmp/a2a-p3-t05-correction7-red.log | 0 | — | 1 compile error | 0 | Test lambda did not handle InterruptedException; corrected before execution |
| Focused red, /private/tmp/a2a-p3-t05-correction7-red2.log | 2 | 2 | 0 | 0 | Both findings reproduced |
| Focused green, /private/tmp/a2a-p3-t05-correction7-focused.log | 4 | 0 | 0 | 0 | Two new cases and two existing artifact wrapper controls pass; two stale method selectors matched no tests |
| Full PushNotificationSenderTest, /private/tmp/a2a-p3-t05-correction7-sender.log | 40 | 0 | 0 | 0 | Sender suite passes |
| Required selector, /private/tmp/a2a-p3-t05-correction7-required.log | 44 | 0 | 0 | 0 | Required selector passes |
| Retained server-common selector, /private/tmp/a2a-p3-t05-correction7-server.log | 209 | 0 | 0 | 0 | Proposal, lifecycle, queue, publication, sender, and aggregation regressions pass |
| Replication, /private/tmp/a2a-p3-t05-correction7-replication.log | 1 | 0 | 0 | 0 | Pass |
| Transport bindings, /private/tmp/a2a-p3-t05-correction7-bindings.log | 8 | 0 | 0 | 0 | JSON-RPC, gRPC, and 0.3 assertions pass |
| OpenTelemetry, /private/tmp/a2a-p3-t05-correction7-otel.log | 9 | 0 | 0 | 0 | Explicit server selector passes |

Retained regressions total 227 (209 + 1 + 8 + 9); selectors overlap. The required 44 tests and sender/focused selectors also overlap. All final test selectors reported zero failures, errors, or skips. No full-reactor context rebuild ran. git diff --check exited 0; Git emitted the known fsmonitor IPC diagnostic during status/diff reads.

Exact final required and retained commands:

```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am \
  -Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am \
  '-Dtest=ExistingTaskProposalStreamLifecycleTest,CustomEventQueueCompatibilityTest,ExecutorSubmissionTest,TaskManagerSnapshotTest,FullTaskProposalCompatibilityTest,ResultAggregatorTest,PushNotificationSenderTest*,AtomicTaskStoreContractTest,EventQueueTest,MainEventBusProcessorPushNotificationOrderTest,CommittedPublicationTest,EventConsumerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/queue-manager-replicated/core -am \
  -Dtest=ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc -am \
  '-Dtest=JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess+testOnMessageSendStreamMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/opentelemetry/server -am \
  -Dtest=OpenTelemetryPushNotificationSenderDecoratorTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test
```


Focused red, green, and sender-suite commands:

```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#versionUnawareSenderReceivesOnlyCanonicalArtifactForArtifactOnlyProposal+equalOrdinaryArtifactDuringProposalForwardingStillReachesBothVersions' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction7-red.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#versionUnawareSenderReceivesOnlyCanonicalArtifactForArtifactOnlyProposal+equalOrdinaryArtifactDuringProposalForwardingStillReachesBothVersions' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction7-red2.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#versionUnawareSenderReceivesOnlyCanonicalArtifactForArtifactOnlyProposal+equalOrdinaryArtifactDuringProposalForwardingStillReachesBothVersions+artifactOnlyProposalUsesTaskSummaryWhenWrapperInheritsNoOpSnapshotHook+copiedArtifactProposalUsesOneTaskSummaryAndLeavesEqualOrdinaryArtifactVisible+copyingStatusOnlyProposalPreservesLegacySummaryAndCurrentStatus+copyingStatusAndArtifactProposalProjectsOneLegacySummary' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction7-focused.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am -Dtest=PushNotificationSenderTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction7-sender.log 2>&1
```

## Changed-source provenance

SHA-256 at handoff:

- PushNotificationSender.java: 652ff75e672d09268b1fe61c8b6bf7f55af4081eed1eba128c99b8fc89b0e694
- BasePushNotificationSender.java: 6f17b31a25abe49562bd6a7595ee6d5bedee2c74e123346481194dffd1639fd7
- TaskProposalDispatchContext.java: 47cc9b2d7b3045df74dd4bf00e0833b74f5532cf01c5078e51eb97fe9e37f2db
- PushNotificationSenderTest.java: e81b452cbf0ac633259fee6abb42806e63a6a0eae97fd43d43c095a1a5583dc5

## Review boundary

Implementation status and independent review status remain separate. Next action is a Sol medium/normal review of version-unaware canonical fallback, legacy summary projection for artifact-only wrapper batches, concurrent equal ordinary-artifact isolation, copied-event worker forwarding, and the fresh required/regression evidence. Stop here; do not start P3-T06.
