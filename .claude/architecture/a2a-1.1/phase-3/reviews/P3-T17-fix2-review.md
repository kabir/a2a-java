# P3-T17 targeted re-review of correction 2 (71abc014..37aa8600)

## Disposition: **Changes required — T17 is NOT accepted**

- N2 (request id on non-send parse errors) and N11 (cleanup tests) are resolved. They carry only low-severity notes.
- N1 is only partly closed. One medium defect is open (R1). Only a covered event that reaches the child queue **before** `completeWithoutSnapshot(G)` closes the stream. A covered event whose publication arrives **after** it is still dropped silently. I established this by reading the code and tracing it, not by running a probe (see "Limitations of this review"). No test covers it.
- Either fix R1, or the developer explicitly narrows the N1 decision to "arrival before the starting-generation read".
- This is a targeted review only. Even if R1 is resolved, a distinct fresh full review must follow. T18/T19 are not started. Nothing was committed, pushed, or edited in the repository except this file.

## Identification

| Item | Value |
|---|---|
| Candidate (HEAD) | `37aa86003cd18d7011351f469c1b554d2a74e113`, branch `work/p3-t17` (`git rev-parse HEAD`) |
| Parent / task base | `71abc014921417b0b353c7f970be998aa7d4861a` (`git rev-parse HEAD^` verified) / `d869e957b310e2585409ac3b3adb166d7823d36c` |
| Git state at start | `git status --short` empty, 0 lines |
| Diff | 12 files, +722/-33. Production: `JSONRPCUtils`, `PublicationReconciler`, `DefaultRequestHandler` (javadoc and one call). Docs: `configuration.md`. The rest is tests and handoff/progress/review files. |
| Source hashes | `sha256sum` of 9 files in the committed tree equals the hashes in `progress-T17.md` (correction 2): `configuration.md`, `A2AServerRoutesTest`, `PublicationReconciler`, `DefaultRequestHandler` (`e50c4cad…`), `PublicationGapRecoveryTest`, `StreamingSendPublicationRecoveryTest`, `JSONRPCUtils` (`21748f53…`), `Phase2CodecTest`, `Phase3MutationScenarios`. Output: `/tmp/p3-t17-review-fix2-logs/sha256-now.txt`. |
| Window | Start 2026-11-26 19:28 UTC, original deadline **21:28 UTC**. The report was finished at about 21:20 UTC. The user-authorised two-hour extension (to 23:28) was **not used**. |
| Model | Requested: IBM Bob configured model. Observed model ID: **unavailable**. Not Sol. Astra not used. No nested agents, no Serena, no headless Bob, no push. |
| Maven | Online, private repo `/tmp/p3-t17-review-fix2-m2`. It was seeded from an earlier review's third-party dependency cache, with `org/a2aproject` removed. Commands ran serially, each wrapped in `timeout -k 30 1200`. Logs, `.exit`, `.start`/`.end` and `.clock` files are in `/tmp/p3-t17-review-fix2-logs`. No root `test.log`. |
| Probe copy | A `git archive HEAD` copy was created in `/tmp` but **deleted unused**. The tooling blocked writes outside the workspace, so I did not run my own mutation or probe builds (see limitations). |

## Timing anomaly in the first `jsonrpcmod` run (recorded, not resolved to a cause)

| Fact | Evidence |
|---|---|
| Start/end/exit | `jsonrpcmod.start` 19:34:56, `.end` 20:29:47, exit 0. Maven reports `Total time: 54:50 min`, `Finished at 2026-11-26T20:29:47Z`. The command was `timeout -k 30 1200 mvn …`. |
| Wall-clock bound | **Not demonstrated.** The run lasted far longer than 1200 s by wall clock, yet `timeout` never fired and the exit was 0. I do not claim the 1200 s bound held. |
| Where the time went | Four single tests, none with a multi-minute bound of its own, account for about 49 of the 55 minutes. The values come from the surefire XML written by this run. |
| | `QuarkusA2AJSONRPC_v0_3_JdkTest.testMainQueueStaysOpenForNonFinalTasks` 586.5 s (normally ~1 s) |
| | `…v0_3_WithTaskAuthorizationVertxTest.testNonOwnerCannotGetTask` 1017.0 s (normally <1.5 s) |
| | `Phase3JSONRPCMutationTest.lostFinalPublicationClosesTheStream…` 924.7 s |
| | `Phase3JSONRPCMutationTest.older10Observer…` 442.9 s |
| | Class-level totals in the log: JdkTest 588.6 s, WithTaskAuthorization 1019 s, `Phase3JSONRPCMutationTest` 1402 s. Previous runs of the same module: 5:04 (earlier review, `p3-t17-review2-logs`) and 5:39 (implementer, `p3-t17-fix2-logs/v6`). `Phase3JSONRPCMutationTest` took 54 s in both. |
| Passing despite the stalls | The slow tests passed with their own bounds intact (for example, the lost-final test waits ≤15 s). This points to a **process freeze or clock discontinuity** rather than slow execution. The sum of excess time (~49:08) matches 54:50 minus a normal ~5:15. |
| Correlation | The first stall began at about 19:38:45, the moment my previous tool call was interrupted. My session then had no activity until about 20:59. The stalls end at 20:29. The `date` values in my own commands jump from 19:38 to 21:00. I could not determine the mechanism: `/proc/timer_list` is unreadable, and I have no evidence beyond this correlation. `/proc/uptime` and wall time agree (constant offset), so they do not discriminate. |
| What the original run still shows | Exit 0, `BUILD SUCCESS`, module counts as expected (164 tests, 0 failures, 0 skips in the module), `Phase3JSONRPCMutationTest` 21 and `Phase3JSONRPCNoUserArtifactsTest` 4. Test outcomes under the JVM's own timers were valid. Wall-clock bounded verification was not. |
| Rerun | **A rerun was done** because the bound was not demonstrated (see the table below). Same command, same private repo. Wall start 21:03:23, end 21:08:27 (304 s). `/proc/uptime` advanced 303.8 s. Maven reported 05:03 min. Exit 0. `Phase3JSONRPCMutationTest` took 55.0 s and the two previously stalled compat classes took 6.2 s and 1.5 s. All counts are identical to the first run. The rerun is the evidence I rely on. |
| Retained | The original `jsonrpcmod.log/.exit/.start/.end` are kept unchanged beside `jsonrpcmod-rerun.*` and `.clock`. The anomaly does not change any finding below. |

A required rerun for any other command is not needed. All other commands finished in seconds to minutes (`routes` 25 s, `recovery` 10 s, `cbase` 23 s, and so on). Their `.start`/`.end` times are consistent with their Maven durations.

## Checks actually run (on HEAD, exit codes from `.exit` files)

| Name | Scope | Exit | Result |
|---|---|---|---|
| codec | `-pl spec-grpc,jsonrpc-common -am -Dtest=Phase2CodecTest test` | 0 | `Phase2CodecTest` 10 run, 0 fail, 0 skip |
| routes | `-pl reference/jsonrpc -am -Dtest=A2AServerRoutesTest,Phase2BoundaryTest test` | 0 | 41 + 5 = 46, 0 skips |
| recovery | `-pl server-common -am -Dtest=PublicationGapRecoveryTest,StreamingSendPublicationRecoveryTest,PublicationRecoveryProvisioningTest test` | 0 | 32 + 43 + 10 = 85, 0 skips |
| plan1 | `-pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest test` | 0 | client 26 (19 + 7); `JSONRPCHandlerTest` 80 run, **1 skipped = the pre-existing `@Disabled`** |
| cbase | `-pl client/base,reference/jsonrpc -am -Dtest=Phase3JSONRPCPublicClientTest,ExistingOrdinaryEntryPointReductionTest,Phase2BoundaryTest test` | 0 | 18 + 31 = 49, plus `Phase2BoundaryTest` 5, 0 skips |
| jsonrpcmod (1st) | `-pl tests/multiversion/jsonrpc -am verify` | 0 | BUILD SUCCESS, **wall time anomalous (54:50)**. Module 164, 0 skips. Reactor skips: 3 upstream `VertxA2AHttpClientSSETest` and 1 `JSONRPCHandlerTest`. |
| jsonrpcmod-rerun | same command | 0 | BUILD SUCCESS, 05:03. `MultiVersionJSONRPCTest` 75, `MultiVersionJSONRPC_v0_3_Test` 40, `Phase3JSONRPCMutationTest` 21, `Phase3JSONRPCNoUserArtifactsTest` 4, auth and provisioning classes. Module 164, 0 skips. `server-common` in the reactor 1041, 0 skipped. Reactor skips: 3 Vert.x, 1 `JSONRPCHandlerTest`. |
| grpcrest | `-pl spec-grpc,jsonrpc-common,tests/multiversion/rest,tests/multiversion/grpc -am verify` | 0 | BUILD SUCCESS, 07:00 min. Multiversion REST **146 run / 18 skipped**, gRPC **131 run / 22 skipped**. Compat-0.3 REST 200 (27 skipped), gRPC 53 (11 skipped). Reference gRPC 76 (11), REST 309 (27). 0 failures or errors. |

The skip counts in REST and gRPC are the same as the previous correction baseline (`progress-T17.md` rows 10 and 13), and nothing in T17 touches those suites. The skip reasons were not re-investigated this time. Earlier review `P3-T17-review.md` recorded them as `Assumption failed: JSONRPC-specific` plus `@Disabled` auth tests.

Not run: the full reactor, extras (JPA, Kafka/Docker, OpenTelemetry), recovery enabled on REST/gRPC, any CDI+JPA wiring, and my own mutation builds.

## Findings

Severity: **Medium** is a concrete defect or a material gap against the stated decision. **Low** is hygiene or a limited evidence gap. **Info** needs no action.

### R1 — Medium (concrete defect by code reading; not executed): a covered event published after `completeWithoutSnapshot(G)` is still dropped silently (N1)

**What the user chose.** Close the affected send stream with the existing resync failure when the starting-generation read covers an event that arrived after the tap, instead of silently dropping it.

**What is implemented.**
- `PublicationReconciler.Observation.complete(G, snapshotReplacesCovered=false)` (`PublicationReconciler.java:628-647`) fails the observation with `COVERED_WITHOUT_SNAPSHOT` only if an item with `generation ≤ G` is **already** in `held` or in `beforeInstall` at the moment `complete` runs.
- After `complete`, `attached = true` and `watermark = G` (`:660-663`).
- Any later `admit(item)` with `generation ≤ watermark` takes the early return at `:584` (`attached && generation <= watermark`) with the comment "Stale or duplicate". It is neither delivered nor failed.

**Why this loses an event.**
- A concurrent writer W commits generation `k` and then publishes it. The window between the store commit and distribution to children exists for both the agent-event path and the `commitAndPublish` path, and it is the T13 "delay writer N after commit" case.
- Take `k ≤ G`, with the sender's tap and G read happening between W's commit and W's distribution.
- The sender has the tap (so the child will receive `k`), and G already includes `k`.
- If `k` reaches the child after `complete(G)`, it is dropped silently.
- No snapshot is given to the stream, so the stream never shows `k`.
- This is an "event arriving after tap, covered by the snapshot" in the user's words. With recovery **off** (the default) the child would simply deliver it.

**Test coverage of this window: none.**
- `PublicationGapRecoveryTest` covers only held-before-complete and queued-before-attach.
  - `withoutASnapshotACoveredCommitHeldAfterTheAttachClosesTheObservationWithTheResyncFailure`
  - `withoutASnapshotACoveredCommitQueuedBeforeTheAttachClosesTheObservationWithTheResyncFailure`
- `withoutASnapshotOnlyLaterCommitsAreDeliveredAndNothingCoveredMeansNoFailure` commits generation 5 **without ever publishing it**. It documents the quiet skip rather than testing arrival.
- The handler-level `aCommitCoveredByTheStartingGenerationClosesTheSendStreamWithTheResyncFailure` triggers the commit inside `readSnapshot`, before the read returns, so the publication again lands before `complete`.
- Nothing exercises publication after `complete`.

**Fix direction (for the correction session, not applied).**
- Remember on the observation that it is snapshot-less, and the starting generation G.
- In `admit`, if such an observation receives a committed item with `generation ≤ G` after attach, call `fail(COVERED_WITHOUT_SNAPSHOT)` instead of returning silently.
- Keep a delivered or duplicate generation `> G` ignored as today.
- Add a unit test (commit and read, `completeWithoutSnapshot`, then publish the earlier batch) for local and remote delivery. Add an optional handler-level test with the store hook.
- If a pre-tap commit published after tap must instead be treated as "not this stream's event", the developer must say so, and the docs and class javadoc must state it.

**Not affected.** The `complete(G)` subscription path keeps its discard, because its snapshot replaces the covered events.

### R2 — Low (evidence breadth): N2 verified, with tests on four methods only

**Production** (`JSONRPCUtils.java:233-284`).
- The selected-1.1 dispatch is now one `try { parseSelected11Method(...) } catch (InvalidParamsError e)` that rethrows `InvalidParamsJsonMappingException(message, id)`.
- It covers `SendMessage`/`SendStreamingMessage`, `SubscribeToTask` and the fall-through to `parseMethodRequest` (GetTask, CancelTask, ListTasks, CreateTaskPushNotificationConfig, Get/List/Delete push config, GetExtendedAgentCard).
- I verified in `ProtoUtils.FromProto` that these parse methods go through `convert`, which maps `IllegalArgumentException` to `InvalidParamsError`. Their parse-time failures therefore reach the wrapper.

**Route.**
- `A2AServerRoutes.invokeJSONRPCHandler` (`:399-403`) answers `InvalidParamsJsonMappingException` with `e.getId()`.
- It was not changed in this commit; the request-id echo for parsed requests was introduced in the previous one.
- Because the exception is an `InvalidParamsJsonMappingException`, `request` stays null on that path. The id comes from the exception, not from `request`.

**Frozen versions and malformed envelopes are unaffected.**
- Selected 1.0 and 0.3 still use the original `parseRequestBody` (`:205-230`), which wraps the same way.
- `parseRequestBody(body, tenant, "1.1")` for non-object input and a missing `method` are unchanged (`:241-248`). `getAndValidateJsonrpc` and `getAndValidateId` are untouched.

**Tests.**
- `Phase2CodecTest` 10 (3 added).
  - `selected11NonSendParameterRejectionsCarryTheRequestId`
  - `selected11NonSendRejectionsKeepANumericRequestIdAndTheOriginalMessage`
  - `theFrozenOneZeroPathAndMalformedRequestsAreNotAffectedByTheNonSendWrapping`
- `A2AServerRoutesTest` 41 (parameterised route test over GetTask history, GetTask timeline, ListTasks pageSize and SubscribeToTask tenant, plus a malformed null-id test).
- The route test selects the 1.1 contract through `selectProtocol11()` (registration `{1.0,1.1}` plus the `A2A-Version: 1.1` header). It asserts the echoed id `np-1`, code -32602, HTTP 200 and no handler interaction.
- The implementer's RED evidence is credible. Codec 10 run / 2 failures on old code, and routes 41 run / 4 errors with `JSONRPCUtils` reverted. I did not reproduce the RED runs myself.
- This is **not a real HTTP end-to-end test** for these inputs. It drives `invokeJSONRPCHandler` with a mocked routing context, but the response is serialised with the real `serializeResponse`, so the id echo is genuine.

**Gaps (Low).**
- CancelTask and the push-config methods on 1.1 are covered only through the shared wrapper, not by tests. The mechanism is generic, so I rate this Low.
- Observation, outside this diff and not executed: in the selected-1.1 path `request.get("method").getAsString()` (`JSONRPCUtils.java:251`) is unchecked, unlike the selected-1.0 path, which rejects a non-string `method` with `IdJsonMappingException` carrying the id (`:219-222`). By reading, `"method": null` or `"method": {}` would throw `UnsupportedOperationException` on 1.1, which the route turns into `InternalError` with `"id": null`. The request id is known there. This is a pre-existing malformed-envelope case, so it is not a regression of this diff. I list it for the developer as a possible follow-up, not as a blocker, and I did not execute it.

**Verdict.** N2 is resolved for the selected 1.1 methods, as the plan requires.

### R3 — Low: N11 cleanup evidence is genuine for observation release, with provenance gaps

- The new tests inject real faults.
  - `aFailureAfterTheAttachAndBeforeTheAgentStartsReleasesTheObservationAndTheChild`: the executor service throws `RejectedExecutionException`, and the agent never runs.
  - `aFailureBuildingThePublisherAfterTheAgentStartedReleasesTheObservationAndTheChild`: a `ServerCallContext` subclass throws after the consumer cancel callback is installed.
  - Both run over all four variants (new and existing task, send and send-input streams).
  - Both assert `activeObservationCount() == 0` and `!isScheduled()`.
- The implementer's mutation logs in `/tmp/p3-t17-fix2-logs` were inspected. They show the tests fail at the intended assertions when each `detach()` is removed.

| Mutation | Result | Failing assertion |
|---|---|---|
| mut2 (pre-agent `detach()` removed) | 4 failures | `…ReleasesTheObservationAndTheChild:437` "the observation is released ==> expected 0 but was 1" |
| mut3 (publisher-failure `detach()` removed) | 4 failures | `:461`, same message |
| mut1 (consumer `onError` `detach()` removed) | 1 error | `anAgentFailureEndsTheStreamAndReleasesTheObservation:378` (`ConditionTimeout`) |
| mutN1 (`completeWithoutSnapshot`→`complete`) | 2 failures | `aCommitCoveredByTheStartingGenerationCloses…:236` |

- The line numbers match assertions in the committed test file (`sed` on HEAD) and the test-file hash matches. So the mutations did change the observation count from 0 to 1, which means the child was not closed by another path. This establishes that the detaches are what release the observation.
- This indirectly establishes child closure too, because `onChildClosed` is what unregisters the observation.
- The separate assertion `getActiveChildQueueCount(TASK_ID) <= 0` does not discriminate: `InMemoryQueueManager.getActiveChildQueueCount` returns -1 for a missing main queue. It is only an accompanying check. The new-task variants skip it.
- Provenance caveats (Low). I could not rerun these mutations.
  - `mutN1.exit` was never written and `mutN1.diff` is empty.
  - `mut2.exit` is documented as reconstructed from the log (first chain killed when a tool call was cancelled).
  - The mut2 sed expression was not preserved (only `mutate.sh`, with mut3 and mut1 in `chain.sh`).
  - The mutations were applied with line-number-based `sed` on the probe copy.
  - I accept the logs as consistent evidence, not as independently reproduced.
- Not asserted: after the N1 early failure at attach time, the agent is still started (see R4), and no test asserts agent or main-queue cleanup afterwards.

**Verdict.** N11 is resolved at the level the plan requires (deterministic injected failures, with failing mutants); the Low caveats are listed above.

### R4 — Low: behaviour and cleanup after an N1 failure

- `observeSendStream` does not throw on the covered case. `completeWithoutSnapshot` fails the observation internally, which queues the `InternalError` and closes the child gracefully (`PublicationReconciler.java:721-741`).
- `sendOrdinaryStream` then continues: for an existing task the input is **already committed** (`commitExistingTaskInput` in `initMessageSend`), the agent is started, and the consumer reads the error and ends. The docs say "the send itself is not undone" and match this.
- The handler test asserts the stream ends with the resync `InternalError`, no events, no Task snapshot, and `GetTask` returns the committed state. The commit it injects is on the executor path, not the same writer.
- Not asserted: the agent run and main queue are cleaned up (for example, the run is not leaked). In the test the agent only blocks on `rig.release`. Low, because the cleanup path is the same as for a consumer that cancelled.

### R5 — Info: T13 pairing, wire codes, subscriptions, regressions

- No new wire code. The failure is the existing `InternalError` "Task publication could not be confirmed (…). The stream was closed; call GetTask to resynchronize…". The new `FailureReason.COVERED_WITHOUT_SNAPSHOT` only changes the description string.
- Subscriptions are unchanged. `complete(long)` keeps the discard semantics. `withASnapshotACoveredCommitIsStillDiscardedBecauseTheSnapshotReplacesIt` pins this.
- T13 reserve/release is untouched in this diff. `MainEventBusProcessor.commitAndPublish` is unchanged. The sender's own input commit is published synchronously (`orderedTaskPublisher.publish(...).toCompletableFuture().join()`) **before** the tap and the generation read, so it cannot trip the new covered check. `theInputCommitOfAnObservedSendKeepsItsReserveAndReleasePaired` and `aRejectedInputToAnObservedSendLeavesNoHoldAndNoObservation` are unchanged and pass (43/43).
- Existing-test changes, assessed for weakening:
  - `StreamingSendPublicationRecoveryTest`: the old test `aCommitRacingTheAttachIsDiscardedWhenCoveredAndALaterOneIsDeliveredOnce` was replaced by two tests, which follows the user's N1 decision. The new "after the read" test asserts that generations are distinct, ordered, `> 4`, contain the racing commit and end at `racing+1`. That is not weaker than the old exact-list assertion for the delivered case. `GatedStore` gained before/after hooks, and the old hook name is gone.
  - `Phase3MutationScenarios`: only added assertions (no event after the barrier). Strengthening only.
  - `PublicationGapRecoveryTest`, `A2AServerRoutesTest`, `Phase2CodecTest`: additions only.
- Opt-in default off and the docs stay consistent with the code except for the window in R1. `configuration.md` says "An event committed after that read is delivered once" and describes only the arrival-before-read race.
- The queued-before-attach branch and the held branch are both covered at unit level. The e2e `Phase3JSONRPCMutationTest` does not exercise N1; the handler-level test is the only integration-level N1 evidence.

## Retained limits and prior findings (unresolved, not blocking this targeted review unless noted)

| ID | Status |
|---|---|
| N3 artifact-only `SendInput` has no stream to watch | Retained Info. Documented limit. |
| N4 `@PreDestroy` tested by direct call; CDI + JPA enabled path untested; REST/gRPC run with recovery off | Retained Low. Explicit open limit for the full review and the extras phase. |
| N5 javadoc | Fixed (`Builder.publicationReconciler`, `observeSendStream`, class javadoc). |
| N6 sleeps | Retained Low, unchanged. Push negative control 1000 ms, `sendCollected` 400 ms settle, `Phase3OldObserver.awaitFrames`, `Phase3JSONRPCPublicClientTest` ~300 ms and `awaitQuiescent`. The only change is the server-side barrier re-assertion in `Phase3MutationScenarios`. |
| N7 rejected opening input may already have run the executor | Retained Info. |
| N8 tenant ownership case not modelled | Retained Low. |
| N9 input message role not validated | Retained for the developer. Unchanged, no code. |
| F1.1 wire-level finite demand | Retained. Reference `SseResponseWriter` requests `Long.MAX_VALUE`. |
| N1 residual | R1 above. The earlier "documented silent discard" is replaced by close-with-resync, except for the post-complete arrival case. |
| Wall-clock bound of the first `jsonrpcmod` run | Not demonstrated. Rerun passed in 5:03. |

None of N3, N4, N6, N7, N8 or N9 blocks this targeted review on its own. They stay open for the full review.

## What was and was not checked

Checked: the full `git diff 71abc014 37aa8600` for production, tests and docs; the surrounding code (`PublicationReconciler` admit/complete/fail, `EventQueue.ChildQueue` gate and `takeQueuedCommittedItems`, `DefaultRequestHandler.sendOrdinaryStream`/`observeSendStream`/`initMessageSend`/`commitExistingTaskInput`, `MainEventBusProcessor.commitAndPublish`, `JSONRPCUtils` selected-1.1 and 1.0 paths, `ProtoUtils.FromProto.convert`, `A2AServerRoutes.invokeJSONRPCHandler`); the implementer's mutation and RED logs; the runs listed above.

Not done:
- No own mutation or probe builds. The file tools are blocked outside the workspace, and I deleted an unused `/tmp` probe copy rather than work around that. The R1 trace comes from code reading, not from an executed probe. The `"method": null` observation in R2 is also unexecuted.
- No full reactor, extras, Docker/Kafka/JPA, or real Quarkus boot with the JPA store.
- No REST/gRPC with recovery enabled.
- The REST/gRPC skip reasons were not re-verified this time.
- The model identity of this reviewer is not observable. No Sol claim.

## Required before a fresh full review

1. **R1**: close the post-attach covered-arrival hole in `Observation.admit` for snapshot-less observations, with unit and ideally handler tests. Alternatively, the developer records that N1 means "arrival before the starting-generation read" and the docs and javadoc say so.
2. Low items worth doing in the same session: CancelTask/push-config parse-error id tests on 1.1; optionally assert agent/queue cleanup after the N1 close (R4); the `method` null/object id loss on 1.1 as a developer decision (R2).
3. Rerun the affected commands, then a distinct fresh full independent review. Keep the N3/N4/N6/N7/N8/N9 limits visible.

## Final disposition

**Changes required.** N2 resolved, N11 resolved with Low caveats, N1 not fully closed (R1, Medium). The first `jsonrpcmod` timing anomaly did not change any result: the rerun passed with consistent timing. T17 is not accepted. A distinct fresh full review must follow any correction.
