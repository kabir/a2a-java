# P3-T04 full-reactor correction handoff — 2026-11-19

**Latest disposition — 2026-11-20:** The repair and subsequent follow-up
corrections are independently accepted by the [fourth follow-up review](../reviews/P3-T04-full-reactor-correction-review.md#fourth-follow-up-review--2026-11-20-1111-utc).
The accepted candidate passed 216 targeted tests with zero failures, errors or
skips and `maven.test.failure.ignore=false`. The saved full-reactor run below
predates the follow-up fixes; it is retained as historical evidence. The
developer explicitly requested amending the repair commit to include this
accepted candidate and its evidence. Earlier dispositions below are historical;
P3-T05 remains unassigned pending developer selection.

**Current checkpoint — 2026-11-20:** The developer reports all tests passing and
explicitly authorizes committing this correction. The saved full run verifies
**4,241 executed tests, 123 skips and zero failures/errors**; see the
[passing full-run record](P3-T04-full-reactor.md#passing-full-run-verification-and-authorized-commit--2026-11-20).
A separate independent correction review is not recorded. The implementation
handoff and its earlier stop-for-review statement below are preserved. P3-T05
and its prompt remain held.

Branch `support/a2a-1.1.0`, base `2e2ee29a886905ef171a41135376e24dc6e93259`, issue #1303. The correction assignment started **16:14:50 UTC** and its fresh two-hour deadline was **18:14:50 UTC**. Requested settings were **Luna, medium reasoning, normal speed**; serving-model metadata was unavailable for independent verification. No subagents were used. This is the correction handoff for the diagnosis in [P3-T04-full-reactor-diagnosis.md](P3-T04-full-reactor-diagnosis.md). Stop here for separate review.

## Disposition

The existing-task compatibility slice and all three prescribed fixture migrations are implemented. The immutable base pair is checked inside the task store's atomic mutation boundary, including the no-op case. Status-only changes use canonical status planning and ordered publication. The 0.3/1.0 paths project the authoritative Task snapshot. A verified active-task no-op delivers an ordered legacy snapshot to the initiating consumer and eligible older-version push configs while creating no mutation, event, generation, revision, replication, or canonical publication. Terminal and unspecified-state no-ops do not synthesize finality or notify.

The planner retains the authoritative accepted input, history, timeline, artifacts (including USER artifacts), and metadata. Supplied history, timeline, generation ranges, omitted artifacts, and omitted metadata are not mutation inputs. Unsupported artifact or metadata edits and cross-origin collisions remain rejected. Raw Task admission guards, missing-task/base checks, and legacy-store guards remain in place.

The existing-task portion of P3-T05 was moved into this repair as directed. Initial Task materialization, opening-input deduplication, broader normalization, and all other held P3-T05 work remain held. No accepted review, the task index, coordinator checkpoint, diagnosis report, or `test.log` was edited.

## Changes

- `server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskMutationPlanner.java` plans only equivalent or status-only full-Task proposals against the atomic authoritative snapshot.
- `server-common/src/main/java/org/a2aproject/sdk/server/tasks/ExecutorSubmissionService.java` validates the exact invocation base at the atomic boundary and carries trusted commit evidence into ordered publication while retaining typed conflict/rejection outcomes.
- `server-common/src/main/java/org/a2aproject/sdk/server/events/MainEventBusProcessor.java` reserves ordered publication for committed status mutations and places active no-op legacy deliveries at the proposal's processing position without allocating mutation positions.
- `server-common/src/main/java/org/a2aproject/sdk/server/events/EventQueueItem.java`, `LocalEventQueueItem.java`, `requesthandlers/DefaultRequestHandler.java`, and `tasks/BasePushNotificationSender.java` carry internal legacy-summary metadata and select the authoritative 0.3/1.0 Task view while preserving 1.1 canonical status delivery and the public push sender SPI.
- `server-common/src/test/java/org/a2aproject/sdk/server/tasks/FullTaskProposalCompatibilityTest.java` adds five production-path compatibility cases, including the active/terminal no-op behavior.
- `AtomicTaskStoreContractTest` now compares deep-copied snapshots by value. `EventConsumerTest` uses local-only enqueue for its four consumer-only event cases. The replicated queue counting fixture verifies the generation-0 baseline followed by the generation-1 canonical status and waits for the replicated echo before the final count assertion.

## Targeted verification

Only the three selectors from the diagnosis handoff were run. Each invocation used `-B -ntp -am`, `-Dmaven.test.failure.ignore=false`, `-Dsurefire.failIfNoSpecifiedTests=false`, the specified Byte Buddy `-javaagent`, and `/usr/bin/timeout -k 30s 1200s` (less than the 20-minute cap and within the remaining assignment time). Each final invocation exited 0.

| Selector | Fresh Surefire counts |
| --- | --- |
| `-pl server-common -Dtest=AtomicTaskStoreContractTest,EventConsumerTest,FullTaskProposalCompatibilityTest,ExecutorSubmissionTest,EventQueueTest,CommittedPublicationTest,MainEventBusProcessorPushNotificationOrderTest,ResultAggregatorTest,TaskManagerSnapshotTest` | AtomicTaskStoreContractTest 15; EventConsumerTest 18; FullTaskProposalCompatibilityTest 5; ExecutorSubmissionTest 27; EventQueueTest 34; CommittedPublicationTest 34; MainEventBusProcessorPushNotificationOrderTest 4; ResultAggregatorTest 19; TaskManagerSnapshotTest 3. **159 total; 0 failures, 0 errors, 0 skipped.** |
| `-pl extras/queue-manager-replicated/core -Dtest=org.a2aproject.sdk.extras.queuemanager.replicated.core.ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation` | ReplicatedQueueManagerTest: **1 total; 0 failures, 0 errors, 0 skipped.** |
| `-pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc` with the three handoff method selectors | JSONRPCHandlerTest: **3**; GrpcHandlerTest: **2**; JSONRPCHandler_v0_3_Test: **3**. **8 total; 0 failures, 0 errors, 0 skipped.** |

The eight final binding methods were:

- `JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess`
- `JSONRPCHandlerTest#testOnMessageStreamNewMessageMultipleEventsSuccess`
- `JSONRPCHandlerTest#testOnMessageStreamNewMessageSendPushNotificationSuccess`
- `GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess`
- `GrpcHandlerTest#testOnMessageStreamNewMessageSendPushNotificationSuccess`
- `JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess`
- `JSONRPCHandler_v0_3_Test#testOnMessageSendStreamMultipleEventsSuccess`
- `JSONRPCHandler_v0_3_Test#testOnMessageStreamNewMessageSendPushNotificationSuccess`

Fresh server-common and replication XML reports and logs are under `/private/tmp/a2a-p3-t04-reports.OVVcT2/`; final binding XML is in the three module `target/surefire-reports/` directories, with its console log at `/private/tmp/a2a-p3-t04-reports.OVVcT2/binding-final-retry.log`. The earlier server-common and replication runs after the terminal-no-op assertion also passed 159/159 and 1/1 respectively.

Failed-attempt evidence was retained in that archive. The initial binding reproduction observed 3 JSON-RPC tests, all 3 failing assertions. Three intermediate compilation attempts were corrected (annotation placement, NullAway, and a test signature), and the first complete server-common run had one fixture failure caused by a differing TaskStatus timestamp; that fixture was corrected before the passing 159-test run. A later binding rerun failed before test execution (0 tests) because the fork reported a missing/invalid Byte Buddy agent manifest. The archive retains that log and dumpstream. The JAR archive validated and a direct JVM agent load succeeded; the same bounded handoff selector then passed all 8 tests. No failure was suppressed.

## Preserved state and stop point

The protected artifact hashes match their assignment-entry hashes:

- task index `.claude/architecture/a2a-1.1/task-list.md`: `f5bef2dee721f9f958b9ec19855153c1c95f73b90252f1797930ff2314670e98`
- coordinator checkpoint `.claude/architecture/a2a-1.1/phase-3/handoffs/P3-T04-full-reactor.md`: `114b016284b4274b3cd374f66e425722806148bef7c7902a68a2b02f565f392e`
- `test.log`: `170310647872efe1eaf52b8a24018d63fb305aaa14f8afc6da5dbbbec3d3ca28`

The worktree remains on the requested branch and immutable base. No commit, push, PR, external communication, full-reactor run, or further P3-T05 work was performed. Handoff is complete; the separate review/correction workflow remains for the developer to assign.
