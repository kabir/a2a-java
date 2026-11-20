# P3-T05 correction 8 handoff

**Status:** Both correction 7 webhook findings are addressed in the candidate. Required and retained selectors pass. Stop for separate Sol medium/normal review; this handoff does not accept the work or authorize P3-T06.

## Assignment and provenance

- Issue #1303, confirmed. Worktree /Users/kabir/.codex-api/worktrees/3053/a2a-java, branch support/a2a-1.1.0, expected and actual HEAD 81dac751c1ab1ce405fc579c2a4873559ad38813.
- Correction 8 started at 2026-11-20 14:59:59 UTC. Deadline: 2026-11-20 16:59:59 UTC.
- Requested implementation model/role per project policy: Luna medium/normal. Serving model telemetry was unavailable. Separate independent review requested: Sol medium/normal.
- test.log remains untracked and was not read, recreated, deleted, written, or staged. Correction 8 command output is under /private/tmp/a2a-p3-t05-correction8-*.log.
- No commit, push, PR, external communication, agent, full-reactor context rebuild, or T06/T07/JPA/cluster/Phase 4/5 work.
- Earlier correction failures and accepted review dispositions remain in their existing handoffs and review record.

## Correction

The correction 7 dispatch scope required identity equality for both the canonical event and the Task snapshot. A forwarding wrapper that retained the original artifact event but rebuilt an equal Task snapshot therefore bypassed proposal classification and sent a covered artifact update to 1.0.

Proposal lookup now recognizes the original canonical event by identity together with a value-equal Task snapshot. Copied artifact events continue to require the unique dispatch snapshot identity and the active copy-forward scope, preserving correction 6's concurrent ordinary-event isolation.

The correction 7 summary grant was also shared by the whole batch. It is now tracked per BasePushNotificationSender instance using sender identity. Each SDK sender can therefore project one Task summary to its own older-version configs, while each continues to receive every canonical 1.1 event. A sender does not emit another legacy summary for later events in that batch.

The deferred-admission webhook test now forwards the original event with Task.builder(snapshot).build() and verifies the legacy destination receives summaries without covered artifact deltas. A new artifact-only fanout test forwards one batch to two SDK senders with separate config stores and clients; it checks one Task summary and one artifact at each legacy/current destination.

Both focused cases failed before the correction: equal-snapshot forwarding produced legacy TaskArtifactUpdateEvent entries alongside Task summaries, and the fanout test timed out waiting for the second sender's missing legacy summary. Both pass after the correction. The correction 6 copied-event and concurrent equal ordinary-event controls also pass.

## Verification evidence

Each Maven command used /usr/bin/timeout -k 30s 1200s, mvn -B -ntp, -Dsurefire.failIfNoSpecifiedTests=false, -Dmaven.test.failure.ignore=false, and:

-DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar

| Selector / log | Executed | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Focused red, /private/tmp/a2a-p3-t05-correction8-red.log | 2 | 2 | 0 | 0 |
| Focused green, /private/tmp/a2a-p3-t05-correction8-focused.log | 6 | 0 | 0 | 0 |
| Full PushNotificationSenderTest, /private/tmp/a2a-p3-t05-correction8-sender.log | 41 | 0 | 0 | 0 |
| Required plan selector, /private/tmp/a2a-p3-t05-correction8-required.log | 44 | 0 | 0 | 0 |
| Retained server-common selector, /private/tmp/a2a-p3-t05-correction8-server.log | 210 | 0 | 0 | 0 |
| Replication, /private/tmp/a2a-p3-t05-correction8-replication.log | 1 | 0 | 0 | 0 |
| Transport bindings, /private/tmp/a2a-p3-t05-correction8-bindings.log | 8 | 0 | 0 | 0 |
| OpenTelemetry, /private/tmp/a2a-p3-t05-correction8-otel.log | 9 | 0 | 0 | 0 |

The retained regression selectors total **228** (210 + 1 + 8 + 9); selectors overlap the required and focused selectors. All final selectors reported zero failures, errors, and skips. The focused red run reproduced both findings. No full-reactor context rebuild ran.

Exact focused, required, and retained commands:

```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#proposalWebhookVersionSelectionSurvivesDelegatingSenderAndDeferredAdmission+artifactOnlyProposalProjectsOneLegacySummaryToEachDelegatedSender' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction8-red.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#proposalWebhookVersionSelectionSurvivesDelegatingSenderAndDeferredAdmission+artifactOnlyProposalProjectsOneLegacySummaryToEachDelegatedSender+equalOrdinaryArtifactDuringProposalForwardingStillReachesBothVersions+copiedArtifactProposalUsesOneTaskSummaryAndLeavesEqualOrdinaryArtifactVisible+artifactOnlyProposalUsesTaskSummaryWhenWrapperInheritsNoOpSnapshotHook+statusAndArtifactProposalWithCopiedStatusKeepsOneLegacySummary' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction8-focused.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am -Dtest=PushNotificationSenderTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction8-sender.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am -Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction8-required.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=ExistingTaskProposalStreamLifecycleTest,CustomEventQueueCompatibilityTest,ExecutorSubmissionTest,TaskManagerSnapshotTest,FullTaskProposalCompatibilityTest,ResultAggregatorTest,PushNotificationSenderTest*,AtomicTaskStoreContractTest,EventQueueTest,MainEventBusProcessorPushNotificationOrderTest,CommittedPublicationTest,EventConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction8-server.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/queue-manager-replicated/core -am -Dtest=ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction8-replication.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc -am '-Dtest=JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess+testOnMessageSendStreamMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction8-bindings.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/opentelemetry/server -am -Dtest=OpenTelemetryPushNotificationSenderDecoratorTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction8-otel.log 2>&1
```

git diff --check exited 0. Git printed the known fsmonitor IPC diagnostic during status/diff reads.

## Changed-source provenance

SHA-256 at handoff:

- BasePushNotificationSender.java: d84ebcd2a8a88c453ccef1d9ecf4671f8b701572ef991f3b1ec24e98ca15c208
- TaskProposalDispatchContext.java: 73eafdb284f86518e18b3e122700e728c83c3c2da7ab926d76be7db46e899c3a
- PushNotificationSenderTest.java: 6da4725d3c8d6f03950b326ff2228fc5af1edf6ac1e7a6132d2bfb71906ff6f3

## Review boundary

Implementation status and independent review status remain separate. Next action is a Sol medium/normal review of original-event/equal-snapshot recognition, per-sender summary claims, the two-delegate artifact-only case, the retained correction 6 copy/ordinary controls, and fresh required/regression evidence. Stop here; do not start P3-T06.
