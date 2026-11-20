# P3-T05 correction 1 handoff

**Status:** The five findings in the independent review have implementation
corrections and focused regression coverage. The required and retained
correction selectors pass. This is not review acceptance; stop for a separate
Sol medium/normal independent re-review. P3-T06 is not authorized here.

## Assignment and provenance

- Issue #1303, confirmed. Worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`.
- Start remains 2026-11-20 11:21 UTC; the two-hour assignment deadline is 13:21 UTC. HEAD remains `81dac751c1ab1ce405fc579c2a4873559ad38813`.
- Requested implementation model/role: Luna medium/normal. Serving model telemetry was unavailable. Separate review role: Sol medium/normal.
- The disposition and probes are retained in [P3-T05-review.md](../reviews/P3-T05-review.md). The approved scope and predecessor acceptance remain in the [implementation plan](../implementation-plan.md#p3-t05--normalize-full-task-proposals-safely), [T05 implementation handoff](P3-T05.md), and [fourth T04 correction review](../reviews/P3-T04-full-reactor-correction-review.md#fourth-follow-up-review--2026-11-20-1111-utc). Those records were not rewritten.
- `test.log` remains untracked and was not read, recreated, removed, written, or staged. Maven output was written to `/private/tmp/a2a-p3-t05-corrections.20261120/`.
- No agents, commit, push, PR, external communication, full-reactor rebuild, T06/T07/JPA/cluster work, or Phase 4/5 work.

## Corrections

1. **Covered multi-event output:** Proposal batch items now retain their event index. Legacy 0.3/1.0 stream projection emits only the first batch item's final Task summary and suppresses later deltas already covered by that summary. 1.1 keeps canonical ordered events. Push dispatch now selects once at the committed batch boundary: the base sender projects one final Task per legacy config and canonical events for 1.1 configs. The OpenTelemetry decorator and the deferred custom sender test preserve the batch boundary. The existing active-task no-op delivery-only summary remains intact.
2. **Opening input immutability:** `PendingTaskInput` captures a deep value copy of its opening Message before the executor can retain or mutate caller-owned nested metadata. Canonical history and published events derive from the captured value. The regression mutates nested caller data before submission and after commit and checks the event, stored history, revision and generation remain paired.
3. **Direct Message lifecycle:** The main bus marks the originating child after a successful no-task direct Message is selected. An initial proposal from that invocation is then rejected locally before authority/store/publication. Existing-task Message input and no-task direct Message semantics are unchanged.
4. **Unary result provenance:** `ResultAggregator` selects the committed snapshot attached to the proposal batch for an immediate proposal response, rather than reading a later TaskStore value. A callback-controlled unrelated write after commit makes the regression deterministic.
5. **Creation race classification:** When retained valid opening input reaches the atomic authority after another proposal materialized the Task, it now returns `CONFLICT` with the current revision. The test checks one winner, no losing input or publication, and successful explicit refresh/rebuild.

The corrections preserve the earlier accepted matching-base normalization and 0.3/1.0 projection slice, admission/timeout/lifecycle/sender-wrapper behavior, and capability-gated ordered atomic commit path. No new ordinary-send persistence or orchestration was introduced.

### Changed source and test files

Production changes are in `server-common/src/main/java/org/a2aproject/sdk/server/events/{EventQueue.java,EventQueueItem.java,LocalEventQueueItem.java,MainEventBusProcessor.java}`, `server-common/src/main/java/org/a2aproject/sdk/server/requesthandlers/DefaultRequestHandler.java`, `server-common/src/main/java/org/a2aproject/sdk/server/tasks/{ExecutorSubmissionService.java,ImmutableTaskCopy.java,PendingTaskInput.java,PushNotificationSender.java,BasePushNotificationSender.java,ResultAggregator.java,AgentEmitter.java}`, and the OpenTelemetry push sender decorator. Regression changes are in `server-common/src/test/java/org/a2aproject/sdk/server/{requesthandlers/ExecutorProposalOutputTest.java,tasks/FullTaskProposalTest.java,tasks/ExecutorMigrationExampleTest.java,tasks/FullTaskProposalCompatibilityTest.java,tasks/PushNotificationSenderTest.java}` and the OpenTelemetry sender decorator test. `TaskMutationPlanner` retains the T05 normalization behavior already described in the original handoff.

Final re-review provenance at this checkpoint: the tracked diff from HEAD hashes to `fbce710ae88bf8414b09f9889373bbda9c5bcb4c05e7010dd8c7701a1e92d99b` (SHA-256 of `git diff --binary HEAD`). The unchanged review is `7c060313f9b64d9efddaa8ab97739259e0b6af2ac19aecd81e4d8f9620e3e1bb`; the focused test sources are `ExecutorProposalOutputTest.java` `e12663020a8887888269e8b60068f8c82b7c506cd817e21c541b919d94a31fab`, `FullTaskProposalTest.java` `8128b1151874c0ceb59a81612eb7855aefb54028b0344bc4f8030c392e1a2d25`, and `ExecutorMigrationExampleTest.java` `10a5d033d8402bfe5d6427c69bc25d20231970a0034db86436b5e5c0e45ccb04` (SHA-256). The focused and final Maven logs are retained in the temporary directory listed below.

## Final verification

All listed final Maven commands used the installed Byte Buddy agent, disabled failure-ignore, selected tests with `failIfNoSpecifiedTests=false`, and were bounded by `timeout -k 30s 1200s`:

```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest,ExecutorProposalOutputTest,PushNotificationSenderTest#proposalWebhookVersionSelectionSurvivesDelegatingSenderAndDeferredAdmission' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am '-Dtest=ExistingTaskProposalStreamLifecycleTest,CustomEventQueueCompatibilityTest,ExecutorSubmissionTest,TaskManagerSnapshotTest,FullTaskProposalCompatibilityTest,ResultAggregatorTest,PushNotificationSenderTest*,AtomicTaskStoreContractTest,EventQueueTest,MainEventBusProcessorPushNotificationOrderTest,CommittedPublicationTest,EventConsumerTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/queue-manager-replicated/core -am -Dtest=ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc -am '-Dtest=JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess+testOnMessageSendStreamMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test

/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl extras/opentelemetry/server -am -Dtest=OpenTelemetryPushNotificationSenderDecoratorTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test
```

| Log under `/private/tmp/a2a-p3-t05-corrections.20261120/` | Tests | Failures | Errors | Skips | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `required-final.log` | 50 | 0 | 0 | 0 | Pass |
| `regression-server-final.log` | 199 | 0 | 0 | 0 | Pass |
| `regression-replication-final.log` | 1 | 0 | 0 | 0 | Pass |
| `regression-bindings-final.log` | 8 | 0 | 0 | 0 | Pass |
| `regression-otel-final.log` | 9 | 0 | 0 | 0 | Pass |

The required 50 comprised 9 `FullTaskProposalTest`, 2 `ExecutorMigrationExampleTest`, 3 `TaskManagerSnapshotTest`, 30 `ExecutorSubmissionTest`, 5 `ExecutorProposalOutputTest`, and 1 deferred webhook wrapper regression. The retained correction regression total is 217 (199 + 1 + 8 + 9). It preserves all eight binding assertions and covers replication, OpenTelemetry, custom queue, sender ordering, proposal lifecycle, and batch projection. The review's parent OpenTelemetry selector executed zero tests; the explicit server-module selector above executed all nine cases. No full-reactor test was run.

## Failed attempts retained

Earlier logs remain in the same temporary directory; they were not overwritten:

- `red.log`: test compilation stopped before tests (2 missing `ServerCallContext` symbols); import corrected.
- `red2.log`: 50 executed, 7 failures, 0 errors, 0 skips. These were the expected red regressions for four output-duplication cases, two opening-value cases, and the deferred webhook duplicate.
- `unary-red.log`: 1 test, 1 failure, 0 errors/skips, reproducing the later-write result. The controlled callback seam made it deterministic; `unary-green.log` passed 1/1.
- `opening-green.log`: 1 test, 1 assertion failure because the test expected no item after consuming the opening Message, while the established input path also publishes a status. The test now consumes that item; `opening-green2.log` passed 1/1.
- `output-green1.log`: compilation stopped before tests on nullable nested type syntax; fixed to `Flow.@Nullable Subscription`.
- `output-green2.log`: 6 executed, 1 failure, 0 errors/skips. The artifact-only assertion omitted an ordinary input status; it now checks the proposal artifact count and event kinds. `output-green3.log` passed 6/6.
- The initial `required-round1.log` is retained and passed 50/50 after the compile and focused assertion corrections; final rerun is `required-final.log`.

No repeated defect reached the three substantive-attempt diagnosis threshold. No failures remain in the final verification above.

## Review boundary

The implementation now addresses all five recorded findings, with candidate code and final evidence above. The task-list row records implementation separately from review. Next action: separate Sol medium/normal independent re-review of [the unchanged review findings](../reviews/P3-T05-review.md), corrections, production seams, final evidence, and scope boundaries. Stop after handing off; do not start P3-T06.
