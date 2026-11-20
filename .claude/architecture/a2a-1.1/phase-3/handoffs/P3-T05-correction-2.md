# P3-T05 correction 2 handoff

**Status:** The five remaining correction-1 findings have implementation fixes
and regression coverage. The required selector and retained regression set
pass. This is not an independent acceptance; stop for a separate Sol
medium/normal re-review. Do not start P3-T06.

## Assignment and provenance

- Issue #1303, confirmed. Worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`.
- Correction work started 2026-11-20 12:14 UTC. The P3-T05 assignment's original start was 11:21 UTC and its two-hour deadline remains 13:21 UTC; this correction does not reset it. HEAD remains `81dac751c1ab1ce405fc579c2a4873559ad38813`.
- Requested implementation model/role: Luna medium/normal. Serving model telemetry is unavailable. Independent review requested: Sol medium/normal.
- The reproduced findings and correction-1 dispositions are in the [unchanged review record](../reviews/P3-T05-review.md#correction-1-independent-re-review--2026-11-20-1212-utc). Correction 1 and the original P3-T05 implementation records remain preserved.
- `test.log` remains untracked and was not read, recreated, removed, written, or staged. New Maven and probe output is under `/private/tmp/a2a-p3-t05-corrections2.20261120/`.
- No agents, commit, push, PR, external communication, full-reactor rebuild, T06/T07/JPA/cluster work, or Phase 4/5 work.

## Corrections

1. **Terminal legacy stream demand:** When the first proposal item projects to a terminal Task summary, the legacy stream operator drains only the suppressed tail of that same final batch. It stops at the batch's final local item, allowing completion after one requested summary. Nonterminal batches do not prefetch their tail; hidden items consume compensating upstream demand, and later visible output still waits for subscriber demand.
2. **Unchanged T04 sender wrapper:** The default batch method now scopes a synchronous proposal dispatch context while forwarding the one legacy summary and canonical items through the old sender methods. `BasePushNotificationSender` recognizes those forwarded batch events: legacy configs receive the summary once and 1.1 configs receive each canonical event in order. The OpenTelemetry batch-aware override is unchanged. The deferred mixed-version integration test now uses a T04-style wrapper that forwards only the old methods, including active no-op, status-plus-artifact, artifact-only, and later delta cases. Separate batch-aware and version-unaware controls remain.
3. **Direct Message selection:** A successfully distributed raw Message from the executor marks its originating child regardless of Message role. Initial proposals from that invocation are rejected before atomic authority access. The guard no longer calls `readSnapshot`, so a read failure cannot block the direct response. The regression covers both `ROLE_AGENT` and `ROLE_USER`, plus a store whose snapshot reads throw.
4. **Request metadata isolation:** `PendingTaskInput` deep-copies request-level nested metadata using the same recursive value-copy routine as opening Message metadata. The opening-event regression mutates both metadata sources before submission and after commit, then checks the published event and committed snapshot.
5. **Unary result ordering:** Immediate completion and result selection now synchronize on the completion future. The proposal snapshot is published before the future wakes the caller; all competing normal/error completion paths use the same synchronization. The original 600-attempt handler stress probe now reports zero wrong responses.

The initial creation-race conflict fix remains unchanged and closed. The accepted matching-base normalization, ordered atomic commit, and prior T04 corrections are preserved.

### Changed candidate files

Production changes for this correction are in `server-common/src/main/java/org/a2aproject/sdk/server/{events/MainEventBusProcessor.java,events/EventQueue.java,requesthandlers/DefaultRequestHandler.java,tasks/BasePushNotificationSender.java,tasks/ImmutableTaskCopy.java,tasks/PendingTaskInput.java,tasks/PushNotificationSender.java,tasks/ResultAggregator.java,tasks/TaskProposalDispatchContext.java}`. Tests changed in `server-common/src/test/java/org/a2aproject/sdk/server/{requesthandlers/ExecutorProposalOutputTest.java,tasks/FullTaskProposalTest.java,tasks/PushNotificationSenderTest.java,tasks/ResultAggregatorTest.java}`. Other candidate changes from correction 1 remain intact.

At this handoff, SHA-256 of the tracked diff from HEAD is `1032ed7917d3ac143cf51b521476185b7304a04fa90889944658d955590b352b`. The unchanged-at-this-correction review file hashes to `96669a42b5f6877f3eee8a06ba94e93f9c11f3d5c856be843cba3e0e8ea1eabc`. New/updated focused sources: `TaskProposalDispatchContext.java` `e5f5f582116397bc071229293ca5edec6feacd19e8a076a9f757ab01ac4b6ef0`, `ExecutorProposalOutputTest.java` `f3c2bcebd0b740dffbff992ea960def013cdbe16fd838545b3641bfcc6a785e7`, `FullTaskProposalTest.java` `f7125ebaffe37704a63175270b68be1d62cfa28922e66c7d5725507a7f55f648`, `PushNotificationSenderTest.java` `ce725f442803a01a01e904e194acca5ca1d0476766801fb769ba4029e34f3efd`, and `ResultAggregatorTest.java` `592405c3dd8d6e5791f9ce4a56acb5420b2e13aa543e0a5a4a22e2efb2a52474` (SHA-256).

## Verification evidence

Each Maven command used the installed Byte Buddy javaagent, failure-ignore disabled, and `/usr/bin/timeout -k 30s 1200s`. The exact plan selector passed 44 tests:

```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am \
  -Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test
```

The expanded focused selector, accepted correction regressions, and sender controls used:

```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest,ExecutorProposalOutputTest,ResultAggregatorTest,PushNotificationSenderTest#proposalWebhookVersionSelectionSurvivesDelegatingSenderAndDeferredAdmission' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=ExistingTaskProposalStreamLifecycleTest,CustomEventQueueCompatibilityTest,ExecutorSubmissionTest,TaskManagerSnapshotTest,FullTaskProposalCompatibilityTest,ResultAggregatorTest,PushNotificationSenderTest*,AtomicTaskStoreContractTest,EventQueueTest,MainEventBusProcessorPushNotificationOrderTest,CommittedPublicationTest,EventConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/queue-manager-replicated/core -am -Dtest=ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc -am '-Dtest=JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess+testOnMessageSendStreamMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/opentelemetry/server -am -Dtest=OpenTelemetryPushNotificationSenderDecoratorTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=PushNotificationSenderTest#batchAwareSenderSelectsOneLegacySummaryAndAllCanonicalEvents+proposalWebhookVersionSelectionSurvivesDelegatingSenderAndDeferredAdmission+versionUnawareSenderFallsBackToCanonicalProposalStatusAndSkipsNoOpSnapshot' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test
```

| Log under `/private/tmp/a2a-p3-t05-corrections2.20261120/` | Executed | Failures | Errors | Skips | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `required-plan-selector.log` | 44 | 0 | 0 | 0 | Pass |
| `required-final.log` | 73 | 0 | 0 | 0 | Pass |
| `regression-server-final.log` | 201 | 0 | 0 | 0 | Pass |
| `regression-replication-final.log` | 1 | 0 | 0 | 0 | Pass |
| `regression-bindings-final.log` | 8 | 0 | 0 | 0 | Pass |
| `regression-otel-final.log` | 9 | 0 | 0 | 0 | Pass |
| `sender-controls-final.log` | 3 | 0 | 0 | 0 | Pass |

The focused 73 comprises `FullTaskProposalTest` 9, `ExecutorMigrationExampleTest` 2, `TaskManagerSnapshotTest` 3, `ExecutorSubmissionTest` 30, `ExecutorProposalOutputTest` 8, `ResultAggregatorTest` 20, and the deferred unchanged-wrapper webhook method 1. The retained correction regression total is **219** (201 + 1 + 8 + 9). The selectors overlap; the count is not a distinct-test total. Coverage retains all eight binding assertions, replication, the nine-case explicit OpenTelemetry server suite, sender wrapper and no-op behavior, custom queue, and proposal lifecycle.

The re-review's 600-attempt unary stress probe was recompiled and run against the fresh server-common test classpath:

```sh
cp_value=$(cat /private/tmp/a2a-p3-t05-rereview.q_9y0ial/classpath)
/usr/bin/timeout -k 5s 90s javac -cp "$cp_value" -d /private/tmp/a2a-p3-t05-corrections2.20261120/unary-race /private/tmp/a2a-p3-t05-rereview.q_9y0ial/UnaryRaceStress.java
/usr/bin/timeout -k 5s 90s java -Dlogback.configurationFile=/private/tmp/a2a-p3-t05-rereview.q_9y0ial/quiet-logback.xml -cp "/private/tmp/a2a-p3-t05-corrections2.20261120/unary-race:$cp_value" UnaryRaceStress
```

`unary-stress.log`: `PROBE attempts=600 wrong=0`. The exact classpath value is retained in `/private/tmp/a2a-p3-t05-rereview.q_9y0ial/classpath`; compilation and execution exited 0.

### Failed attempts retained

- `focused-before-wrapper.log`: test compilation stopped before execution with three test-source errors (a duplicate ResultAggregator test method and two missing `assertFalse` imports). Corrected; no tests executed in that attempt.
- `sender-controls.log`: 3 executed, 1 failure, 0 errors/skips. The batch-aware test compared record values across JSON serialization; deserialization normalizes null part metadata to an empty map. Assertions now check the canonical event sequence, payload and generation. `sender-controls-final.log` passes 3/3.
- `focused-round2.log` passed 19/19; the later `required-final.log` includes the added terminal existing-task demand case and all correction tests.

No defect reached three substantive attempts without progress. No full-reactor test ran. Final Maven/probe source provenance and independent reproduction details remain available in the review record and logs above.

## Review boundary

The task-list row now separates implementation from review. Next action is the
separate Sol medium/normal re-review of correction 2, including finite-demand
termination, the unchanged T04 wrapper, direct Message faults/roles, both
opening metadata sources, the snapshot signal boundary, and the final logs.
Stop there; T06 is not part of this handoff.
