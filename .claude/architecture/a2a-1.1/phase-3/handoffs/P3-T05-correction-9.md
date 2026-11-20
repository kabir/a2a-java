# P3-T05 correction 9 handoff

**Status:** Both correction 8 findings are addressed in the candidate. Required and retained selectors pass. Stop for separate Sol medium/normal review; this handoff does not accept the work or authorize P3-T06.

## Assignment and provenance

- Issue #1303, confirmed. Worktree /Users/kabir/.codex-api/worktrees/3053/a2a-java, branch support/a2a-1.1.0, expected and actual HEAD 81dac751c1ab1ce405fc579c2a4873559ad38813.
- Correction 9 started at 2026-11-20 15:35:17 UTC. Deadline: 2026-11-20 17:35:17 UTC.
- Requested implementation model/role per project policy: Luna medium/normal. Serving model telemetry was unavailable. Separate independent review requested: Sol medium/normal.
- test.log remains untracked and was not read, recreated, deleted, written, or staged. Correction 9 command output is under /private/tmp/a2a-p3-t05-correction9-*.log.
- No commit, push, PR, external communication, agent, full-reactor context rebuild, or T06/T07/JPA/cluster/Phase 4/5 work.
- Earlier correction failures and accepted review dispositions remain in their existing handoffs and review record.

## Correction

Correction 8 classified the original event with any equal Task snapshot. That allowed an ordinary call using the exact original event and committed Task to claim the active proposal summary. It also recognized a copied artifact only when the wrapper preserved the dispatch Task object's identity.

The original-event lookup now requires an equal Task value that is not the original committed Task object. This accepts wrappers that preserve the event but pass an equal Task copy, while leaving an independent call using the published original event/Task pair ordinary. Copied-artifact lookup now accepts equal Task copies during the active artifact-forwarding scope, except when the caller uses the original committed Task object. This covers wrappers that rebuild both values and retains the prior copied-event/original-dispatch-snapshot case.

The deferred-admission mixed-version test now rebuilds both the artifact event and Task snapshot. The paused-worker ordinary-event control first forwards the exact canonical event with the original committed Task, checks that both versions receive an artifact, then sends a distinct equal event and releases the copied proposal forwarding. It checks the final legacy Task summary and canonical 1.1 output.

Both focused tests failed before the correction: the equal-copied pair produced covered legacy artifact updates, and the direct ordinary original-pair call received a Task instead of its artifact. After the scope change, the two reproductions pass along with the previous equal-snapshot, two-sender, copied-artifact and concurrent equal-event controls.

The first focused green attempt had one test assertion failure because the captured HTTP event normalized null part metadata to an empty map. The assertion was narrowed to event type and artifact ID; this was a test representation mismatch, not a delivery failure. The corrected focused selector passed 7/7.

## Verification evidence

Maven commands used /usr/bin/timeout -k 30s 1200s, mvn -B -ntp, -Dsurefire.failIfNoSpecifiedTests=false, -Dmaven.test.failure.ignore=false, and:

-DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar

| Selector / log | Executed | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Focused red, /private/tmp/a2a-p3-t05-correction9-red.log | 2 | 2 | 0 | 0 |
| First focused green attempt, /private/tmp/a2a-p3-t05-correction9-focused.log | 7 | 1 | 0 | 0 |
| Corrected focused green, /private/tmp/a2a-p3-t05-correction9-focused-green.log | 7 | 0 | 0 | 0 |
| Full PushNotificationSenderTest, /private/tmp/a2a-p3-t05-correction9-sender.log | 41 | 0 | 0 | 0 |
| Required plan selector, /private/tmp/a2a-p3-t05-correction9-required.log | 44 | 0 | 0 | 0 |
| Retained server-common selector, /private/tmp/a2a-p3-t05-correction9-server.log | 210 | 0 | 0 | 0 |
| Replication, /private/tmp/a2a-p3-t05-correction9-replication.log | 1 | 0 | 0 | 0 |
| Transport bindings, /private/tmp/a2a-p3-t05-correction9-bindings.log | 8 | 0 | 0 | 0 |
| OpenTelemetry, /private/tmp/a2a-p3-t05-correction9-otel.log | 9 | 0 | 0 | 0 |

The retained regression selectors total **228** (210 + 1 + 8 + 9); selectors overlap. Required and focused sender cases overlap those selectors. All final selectors reported zero failures, errors, and skips. No full-reactor context rebuild ran.

Exact focused, required, and retained commands:

```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#proposalWebhookVersionSelectionSurvivesDelegatingSenderAndDeferredAdmission+equalOrdinaryArtifactDuringProposalForwardingStillReachesBothVersions' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction9-red.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#proposalWebhookVersionSelectionSurvivesDelegatingSenderAndDeferredAdmission+equalOrdinaryArtifactDuringProposalForwardingStillReachesBothVersions+copiedArtifactProposalUsesOneTaskSummaryAndLeavesEqualOrdinaryArtifactVisible+artifactOnlyProposalProjectsOneLegacySummaryToEachDelegatedSender+versionUnawareSenderReceivesOnlyCanonicalArtifactForArtifactOnlyProposal+statusAndArtifactProposalWithCopiedStatusKeepsOneLegacySummary+artifactOnlyProposalUsesTaskSummaryWhenWrapperInheritsNoOpSnapshotHook' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction9-focused-green.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am -Dtest=PushNotificationSenderTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction9-sender.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am -Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction9-required.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=ExistingTaskProposalStreamLifecycleTest,CustomEventQueueCompatibilityTest,ExecutorSubmissionTest,TaskManagerSnapshotTest,FullTaskProposalCompatibilityTest,ResultAggregatorTest,PushNotificationSenderTest*,AtomicTaskStoreContractTest,EventQueueTest,MainEventBusProcessorPushNotificationOrderTest,CommittedPublicationTest,EventConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction9-server.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/queue-manager-replicated/core -am -Dtest=ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction9-replication.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc -am '-Dtest=JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess+testOnMessageSendStreamMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction9-bindings.log 2>&1

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/opentelemetry/server -am -Dtest=OpenTelemetryPushNotificationSenderDecoratorTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test > /private/tmp/a2a-p3-t05-correction9-otel.log 2>&1
```

## Changed-source provenance

SHA-256 at handoff:

- BasePushNotificationSender.java: d84ebcd2a8a88c453ccef1d9ecf4671f8b701572ef991f3b1ec24e98ca15c208
- TaskProposalDispatchContext.java: b21956c197330658fc77d4772217524f3630954c3f69297bebb90c0073aa1be9
- PushNotificationSenderTest.java: 6fa0caa5f0d55f7e5ecd8f19803aacd879fa0738319d68d41a78ad1a8b2a8715

## Review boundary

Implementation status and independent review status remain separate. Next action is a Sol medium/normal review of the combined event/Task copy case, the original committed event/Task ordinary call during a paused proposal, per-sender summary projection, and the fresh required/regression evidence. Stop here; do not start P3-T06.
