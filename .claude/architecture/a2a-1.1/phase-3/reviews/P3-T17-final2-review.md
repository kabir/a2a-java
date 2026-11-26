# P3-T17 final independent full review (final2)

## Header

| Item | Value |
|---|---|
| Task | P3-T17: JSON-RPC production end-to-end mutation slice and shared T18/T19 fixture |
| Candidate | `ba09fda2177d2a752e1c38eff736ee125db2cb6e` on `work/p3-t17`. `git rev-parse HEAD` returned exactly this SHA. |
| Reviewed range | `d869e957b310e2585409ac3b3adb166d7823d36c..ba09fda2177d2a752e1c38eff736ee125db2cb6e`: 6 commits (`c572b04b`, `5d8deaf2`, `71abc014`, `37aa8600`, `5c0369db`, `ba09fda2`). 41 files, +7501/-83. |
| Review type | Final independent full task review. It is not the T20 phase audit. |
| Requested model | The configured IBM Bob model. |
| Observed model | Unavailable. The session exposes no model identifier. This review does not claim Sol, Terra or Luna. Astra was not used. |
| Assignment start | 2026-11-26 22:43:57 UTC (as corrected by the developer). |
| Initial deadline | 2026-11-27 00:43:57 UTC. The authorized two-hour extension was not needed, and the original deadline is preserved. The report file was last written at 22:51:31 UTC (observed file mtime); the review finished by about 22:52 UTC. |
| Tree state | `git status --short` at start and at end shows only the untracked `P3-T17-fix4-review.md`. Its sha256 is `56b84abf0f6969f32483dca8e2e3b35124b79e851c655ffed1af37077d9edaf1`, identical at start and at end. It was preserved byte for byte. |
| Own scratch | `/tmp/p3-t17-review-final2-logs` (logs, scripts, diffs), `/tmp/p3-t17-review-final2-m2` (private Maven repository, copied from the fix4 repository), `/tmp/p3-t17-review-final2-src` (a `git archive` of the candidate, used only for mutation probes; deleted after the review, with the diffs and logs kept). The repository working tree was never mutated. |
| Constraints kept | I edited no tests, production code, previous reviews or shared status. I made no commit and no push, and used no Serena, nested agents or external state. |

## 1. Decision

**ACCEPTED for T17 (task-level), with every retained limit and the unresolved N9 question recorded below. This is not the T20 phase audit.**

- I found no Critical, High or Medium defect. I found no production defect.
- The previous blocker (F-2, the metadata-only full Task proposal from a message-bearing status) is closed by real production-path evidence. I read the actual test code and fixture branch, and I added three independent mutation probes of my own. All three were killed by the new test, and the old input-count test passed in each of those runs.
- The remaining findings are Low and non-blocking. They are F-1, F-3 and the new or refined test-strength items G-1 to G-4 in section 6.
- **N9 is not resolved by this acceptance.** An input message's role is not validated. This is an open normative question, and no user approval of the current behavior is recorded anywhere. The acceptance rests on the reading that the shared bullet's "role" means artifact origin (section 7, L-6b), and that reading needs a developer ruling. If the developer rules that "role" includes the input message role, T17 and the T18/T19 fixture reopen with one validation change and one no-partial-mutation scenario.
- **Not complete, and not claimed here** (T20 or later):
  - the T13 live PostgreSQL/Kafka rerun on final sources;
  - the compatibility gate;
  - the Java 17 `mvn clean install`;
  - the independent phase audit;
  - the Phase 4/5/6 obligations.
- The REST and gRPC bindings are not accepted by this review. They are T18/T19. Only their test compilation against the shared fixture was evidenced (section 2).

## 2. Provenance and evidence

### 2.1 Source integrity

- **Production source is unchanged since `5c0369db`.**
  - `git diff --stat 5c0369db ba09fda2 -- '*.java'` shows exactly two files, +291 lines and no deletions: `tests/server-common/.../AgentExecutorProducer.java` (+78) and `.../Phase3MutationScenarios.java` (+213).
  - Every pre-`ba09fda2` evidence log was taken on the same production bytes. The `5c0369db` logs therefore remain valid for production behavior.
- **The test sources match the logs.** The sha256 of the two changed test files at HEAD equals the fix4 `sha256-final.txt`: `3ee14a2c160aff97…` for `AgentExecutorProducer`, and `7ca4ca10a20da3bc…` for `Phase3MutationScenarios`. The fix4 log timings (`new3` at 22:28:47, `jsonrpcmodfull` at 22:31:11) are before the commit at 22:38:35, and the sources did not change in between.
- **The planner mutation in the fix4 mutation run was restored.** The sha256 of `TaskMutationPlanner.java` at HEAD is `cf469c1ca845…`, equal to `/tmp/p3-t17-fix4-logs/TaskMutationPlanner.java.orig`. `git status` is clean.
- **Preserved reviews.** The committed `P3-T17-final-review.md` (`363fdb38…`), `-fix3-review.md` (`9d8c8eeb…`) and `-final-review-rev1.md` (`07599c85…`) match `preserved-sha256-before.txt`, and the rev1 file matches its `/tmp` original.

### 2.2 Commands run by earlier sessions and inspected directly by me (not rerun)

I read the `.exit`, `.start` and `.end` files and the surefire summary lines of each log. These are other sessions' runs. I did not observe them being executed.

| Log directory and name | Scope | Exit | Counts and skips |
|---|---|---|---|
| `/tmp/p3-t17-review-final-logs/codec` | `Phase2CodecTest` | 0 | 11 tests, 0 skips |
| `…/recovery` | `PublicationGapRecoveryTest` 37, `StreamingSendPublicationRecoveryTest` 45, `PublicationRecoveryProvisioningTest` 10 | 0 | 92 tests, 0 skips |
| `…/routes` | `A2AServerRoutesTest` 41, `Phase2BoundaryTest` 5 | 0 | 46 tests, 0 skips |
| `…/plan1` | client JSON-RPC and handler tests | 0 | `Phase2PublicClientTest` 19 and `Phase3ClientGenerationTest` 7, 0 skips. `JSONRPCHandlerTest` 80 tests with **1 skip**. |
| `…/cbase` | `ExistingOrdinaryEntryPointReductionTest` 31, `Phase3JSONRPCPublicClientTest` 18, `Phase2BoundaryTest` 5 | 0 | 54 tests, 0 skips |
| `…/plan2` | `Phase3JSONRPCMutationTest` 21 (before `ba09fda2`) and `Phase3JSONRPCNoUserArtifactsTest` 4 | 0 | 25 tests, 0 skips |
| `/tmp/p3-t17-fix4-logs/new3` | `Phase3JSONRPCMutationTest` on the final test sources | 0 | 22 tests, 0 failures, 0 skips |
| `…/jsonrpcmodfull` | `-pl tests/multiversion/jsonrpc -am verify` | 0 | BUILD SUCCESS. The module ran **165 tests, 0 failures, 0 errors, 0 skips**. This includes `MultiVersionJSONRPCTest` 75, `MultiVersionJSONRPC_v0_3_Test` 40, `Phase3JSONRPCMutationTest` 22 and `Phase3JSONRPCNoUserArtifactsTest` 4. |
| `…/plan1` (fix4) | client JSON-RPC and handler tests | 0 | Same counts and the same single pre-existing `JSONRPCHandlerTest` skip. |
| `…/consumers-testcompile` | REST/gRPC multiversion plus reference modules, `test-compile` only | 0 | **No REST or gRPC test was run on the candidate.** Compile only. |
| `/tmp/p3-t17-fix3-logs/grpcrest-rerun` | REST/gRPC/all-module reactor on `5c0369db` | 0 | REST 146 tests with **18 skips**. gRPC 131 tests with **22 skips**. Android SSE: 25 tests, 0 failures. |
| `/tmp/p3-t17-fix3-logs/grpcrest-1-android-failure` | the same reactor, first attempt | 1 | Preserved failure (see 2.4). |
| `/tmp/p3-t17-review-fix3-logs/mut1…mut6` | six recovery and codec mutants | all 1 (killed) | I confirmed from the logs that each failed on a named recovery or codec test assertion, none on a compile error. |

- **Skips.**
  - The one skip in the JSON-RPC path is the pre-existing `@Disabled testDefaultRequestHandlerWithCustomComponents`. It is unrelated, it is not counted as passed, and no T17 evidence depends on it.
  - The REST and gRPC skips (18 and 22) are in modules owned by T18/T19. They were not re-observed on `ba09fda2` and are not claimed as proven for REST or gRPC. I did not re-derive their causes.
- **Why I did not rerun broad reactors.** The production bytes are identical to those of `5c0369db`, and the two test files that changed are only used by the JSON-RPC module (REST/gRPC were compile-checked). The existing passing logs cover the same bytes.

### 2.3 Commands run by me

All are in `/tmp/p3-t17-review-final2-logs`, with the private repository `/tmp/p3-t17-review-final2-m2`, run serially under `timeout -k 30 1200`. They ran against the `git archive` copy of `ba09fda2`, never against the repository.

Command: `mvn -B -ntp -Dmaven.repo.local=/tmp/p3-t17-review-final2-m2 -pl tests/multiversion/jsonrpc -am '-Dtest=Phase3JSONRPCMutationTest#metadataOnly*' -Dsurefire.failIfNoSpecifiedTests=false verify`.
This selects the new scenario and the old `metadataOnlyStatusPreservesTheMessageOccurrenceCount`, so 2 tests run each time.

| Run | What changed in `TaskMutationPlanner.applyProposalStatus` | Exit | Result |
|---|---|---|---|
| `control-mutB-timeline`, `control-mutC-messageevent`, `control-mutD-stripmessage` | **Nothing.** The patch step failed because the container has no `python3` and no `diff`. The three runs are therefore unmutated controls. | 0, 0, 0 | 2 tests, 0 failures each. This is a valid green baseline on the candidate with my own repository. **It is not mutation evidence and is not counted as such.** The failed setup is described in `failed-setup-note.txt` (the original `chain.out` text was overwritten by the rerun, so the note transcribes it). |
| `mutB-timeline` | A synthetic metadata status adds no timeline entry (`if (!syntheticMetadata) timeline.add(...)`). | 1 | 2 tests, **1 failure**: `assertMetadataOnlyCommit:882 stored: the newest timeline entry ==> expected: <5> but was: <4>`. The old test passed. |
| `mutC-messageevent` | A synthetic metadata status also emits a `TaskMessageUpdateEvent` carrying the status message. | 1 | 2 tests, **1 failure** at `Phase3MutationScenarios:801`, the check that the commit holds exactly one event (the failure message lists the extra event). The old test passed. |
| `mutD-stripmessage` | A synthetic metadata status drops the status message. | 1 | 2 tests, **1 error**: `NullPointerException` at `:806` (`Objects.requireNonNull(committedStatus.status().message())`). The old test passed. |

- **Patch mechanics.** The first patch attempt failed for tooling reasons (no `python3` or `diff`). That was a tooling failure on a different cause, not a repeated attempt on one defect, so the three-attempt stop rule was not reached. I re-wrote the script with `perl` and `git diff --no-index`, and re-ran it. The diffs are in `mutB-timeline.diff`, `mutC-messageevent.diff` and `mutD-stripmessage.diff`.
- **Restoration.** After each run the script restored the planner in the scratch copy. Its sha256 `cf469c1ca845…` equals `Planner.orig` and the repository copy.
- **Not run by me:** Maven runs on the repository itself, REST/gRPC tests, Android tests, the full `jsonrpcmodfull`, wire-level probes, and CDI/JPA runs.

### 2.4 Android SSE failure (user-deferred, unmodified, cause unknown)

- `AndroidA2AHttpClientSSETest.cancellableSseDisconnectsHeldOpenPeer:213` failed once: `SSE completion should be cancelled ==> expected: <true> but was: <false>`.
- The log is `/tmp/p3-t17-fix3-logs/grpcrest-1-android-failure.log` (21:35:32 to 21:37:42, exit 1). It is preserved.
- It passed in `grpcrest-rerun` (25 Android SSE tests, 0 failures) and in the fix3 reviewer's runs.
- **The cause is unknown. The failure is not called fixed.** The later passes do not erase it. No Android source is in the T17 range, and I did not run it.

## 3. Acceptance matrix

Evidence key: **E2E** is the public JSON-RPC client, the Quarkus route, `DefaultRequestHandler` or core, and the real store. **CP** is a controlled-peer client test. **UNIT** is a unit or focused test. "Inspected" means I read the actual test or source code. "Log" means I inspected the existing log counts.

### 3.1 T17 shared scenario contract (`implementation-plan.md` 1130-1157)

| # | Requirement | Verdict | Evidence |
|---|---|---|---|
| S1 | Public production clients race the same guard. Exactly one accepts, one gets a typed `TaskGenerationMismatchError`. Ingress counted, so no automatic retry or rebase. Stamps and timeline read through send, get, list and `currentView`. | Pass | Inspected `twoUnaryPublicClientsRaceTheSameGuardExactlyOneIsAccepted` and `twoStreamingPublicClientsRace…` (E2E, `CyclicBarrier`). `streamingGuardedInputIsAcceptedOnTheCurrentGeneration…` (E2E). `drainIngress` is the server-side ingress count. Log: `jsonrpcmodfull`, 22 of 22. |
| S2 | Message plus multiple artifacts and artifact-only inputs. Malformed final element, capability, role, ID and tenant/context errors cause no partial mutation. Raw malformed supplements DTO-valid cases. | Pass for artifact origin; N9 open for message role | Inspected `unary…` and `streaming…MessagePlusMultipleArtifactsAndArtifactOnlyInputMutateAtomically`, and `unary/streamingRejectedInputsAreTypedAttributedAndCauseNoPartialMutationOnEitherTask`. The rejection table covers agent-origin artifact, artifact-only agent origin, wrong context, unknown task, blank message ID, two origin/ID collisions and the unprovisioned tenant. Each case asserts one server-side ingress, an untouched target and a second task, and no push config. `assertRawMalformedFinalArtifactRejected` is the raw hook. `Phase3JSONRPCNoUserArtifactsTest` (4) covers the capability. "Role" is covered as **artifact origin** only. See L-6b. |
| S3 | Unary and streaming, both `returnImmediately` values. An opening message plus artifacts that yields a direct Message creates no task, config, publication, replication or queue. Contrast with task-bearing output and an existing-task direct reply. | Pass | Inspected `directMessageCreatesNoTaskConfigPublicationReplicationOrQueueEvenWithOpeningArtifacts`, with `Phase3Spy` and `Phase3SpyQueueManager` as real seam spies (replication hook at the enqueue seam, publication hook at `TaskStreamLifecycleHook.onEvent`), `awaitQueueClosed`, and `pushConfigCount`. Also inspected `taskBearingInputIsReplicatedAndPersistedSoTheDirectMessageSpyIsNotVacuous`, `openingMessagePlusArtifactsIsPersistedAsOneTaskUnlikeADirectReply` and `existingTaskDirectReplyLeavesOneOpenTaskAndCreatesNoSecondTask`. |
| S4 | 1.0 and retained 0.3 writers share a task with 1.1 subscribers. New clients see message updates, generations and origin. Old clients keep shape, history and finality, omit new fields and USER artifacts, and progress under finite filtered demand. Legacy full-Task shape comes from committed results. | Pass with L-1 and L-3 | `older10WriterAndNewSubscriberShareOneTaskWithStableShapes`, `older03Writer…`, `older10/older03ObserverMakesProgressUnderFiniteFilteredDemand…`, `legacyOldClientsReceiveTheTerminalFullTaskProjectedFromCommittedResults` (E2E). The 0.3 side uses the unchanged `compat-0.3` client (`Public03ClientSupport`). Frozen raw evidence is the unchanged `MultiVersionJSONRPCTest` 75 and `MultiVersionJSONRPC_v0_3_Test` 40 (log: both 0 failures, 0 skips). |
| S5a | An immediately terminal inline config precedes notification eligibility. | Pass with L-4 | `terminalFullProposalDeliversFinalArtifactsBeforeClosureAndTheInlineConfigPrecedesEligibility` (E2E, `Phase3Webhook`). The webhook negative control is sleep-based (L-4). |
| S5b | A terminal full proposal delivers final artifacts before closure. | Pass | The same scenario. |
| S5c | **Metadata-only status preserves the message occurrence count** (A03 `acceptance.md` 66-69). | **Pass (section 4)** | New scenario `metadataOnlyFullTaskProposalFromAMessageBearingStatusCommitsOneStatusObservationAndNoMessage`. The old test is retained and honestly documented as an ordinary-input occurrence count. |
| S5d | A lost final publication closes the affected stream within the documented bound. Explicit GetTask retrieves the committed final state with no mutation and no automatic resend. | Pass | `lostFinalPublicationClosesTheStreamAndExplicitGetTaskReturnsTheFinalStateWithoutMutation` (subscription) and `lostFinalPublicationOnAStreamingSendClosesTheStreamAndExplicitGetTaskReturnsTheFinalState` (streaming send). They use the `commitWithoutPublication` hook. The recovery bounds are 200/1000/600 ms in the e2e profile only (L-5). Recovery is opt-in and default off (see 3.3). |
| S6a | Controlled public-client zero and equal generations disable reliability and stop later guarded I/O. | Pass (CP by contract) | `Phase3JSONRPCPublicClientTest.zeroAndEqualGenerationsFromEveryEntryPointDisableGuardedInputBeforeIo` (18 tests in the class, 0 failures). The contract text itself says "controlled public-client". |
| S6b | Unknown stream and timeline variants retain uncertainty and cancellation. Malformed known payloads and error envelopes still fail. | Pass (CP) | `unknownStreamVariantRetainsUncertainty…`, `unknownTimelineVariantInASnapshot…`, `cancellingALiveUncertainStream…`, `malformedKnownPayloadsFail…`, `errorEnvelopesStayTyped…`. `Phase3ClientGenerationTest` (7) and `Phase2CodecTest` (11) cover the raw layer. |
| S6c | Selected 1.0 advanced inputs reject locally without switching version. | Pass (CP) | `selected10AdvancedInputsAreRejectedLocallyAndObserveStaysUnavailable`. `observeSelectedOneZero…UnsupportedBeforeAnyRequest` in `Phase3ClientGenerationTest`. |
| S7 | Remove only guards for implemented 1.1 options. Retain 1.0, live, cursor, `currentGeneration` long-poll and delivery-version gates. Enable `timelineLength` on send, get and list only after the shared projections. | Pass | Inspected the production diff. `rejectDeliveryVersion` keeps only the delivery-version gate. `rejectLegacyTimeline` applies only to `V1_0`. `currentGeneration` still raises `UnsupportedOperationError`. The `observe` cursor request is rejected before any request (`observeWithCursorIsRejectedBeforeAnyRequest`). Live was not touched. |
| S8 | Existing-entry-point reduction and reliability. Built-in no-cursor `observe`. Reduced stamps through send unary and streaming and ordinary subscribe. Cross-entry zero and equal taint leads to a local guarded `sendInput` rejection before I/O, run alongside both input APIs. Wrapper interception and explicit 1.0 preserved. | Pass; F-3 Low | `ExistingOrdinaryEntryPointReductionTest` (31), `unaryStreamingAndNoCursorSubscribeReduceTheSameCommittedStamps` (E2E), and `streamingSendAndNoCursorObserveReduceTheSameStamps…` (CP). Interceptors: `startObservation` calls `applyInterceptors(method, …)`, and the shared helper is already tested by the existing `streamInput` interceptor test (`Phase2PublicClientTest:489-510`). No test pins an interceptor on the new `observe` (F-3). |
| S9 | Real public JSON-RPC clients, routes, core and store. No substitute for server integration. | Pass | The E2E scenarios run through `Phase3JSONRPCMutationTest`, the Quarkus reference server, the real executor, the committer and the store. Controlled peers are used only where the contract says so. |

### 3.2 Acceptance criteria

| Criterion | Verdict for T17 (JSON-RPC slice) | Evidence |
|---|---|---|
| A03 old clients retain contracts | Pass with L-3 | `MultiVersionJSONRPCTest` 75 and `MultiVersionJSONRPC_v0_3_Test` 40 are unchanged and green (log). The 0.3 e2e uses the unchanged `compat-0.3` client. The "1.0" client in the T17 scenarios is the current SDK pinned to 1.0. Historical-peer evidence is the unchanged suites (L-3). The A03 direct-Message, full-Task and metadata-only bullets are in S3 and S5c. |
| A04 common APIs usable | Pass within T17 | No change to `spec` or client common APIs. The `JSONRPCTransport.observe` override is additive. No NullAway or API-report claim is made; that belongs to T20. |
| A05 atomic generations | Pass | Guard race, single-attempt ingress, typed mismatch, artifact chunks and timeline, and stored versus emitted stamps in S1, S2 and S5c. |
| A06 persistence and publication under failure | Pass with opt-in limits | Lost publication (S5d). Recovery tests: 37 + 45 + 10 and six mutation kills (log). Replication and duplicate handling are in `Observation.admit`. The T13 live gate is not claimed. |
| A07 old projections | Pass with L-1 | Mixed observers, hidden USER artifacts, history and finality (S4, S5c). Finite filtered demand is covered in-process. The wire-level demand probe was not run (L-1). |
| A10 client artifact inputs | Pass for artifact rules; N9 open for message role | S2. |
| A12 webhook delivery version stable | Pass for the T17 slice | `rejectDeliveryVersion` retained. S5a inline config. The webhook negative control is sleep-based (L-4). Full A12 persistence and restart evidence is not a T17 claim. |
| A09 (reliability sub-bullets used by T17), A11 | Not claimed | T17 retains live and cursor gates. |

### 3.3 User decisions carried

| Decision | Verdict | Evidence |
|---|---|---|
| Publication recovery is opt-in, default false | Pass | `a2a-defaults.properties` has `a2a.publication-recovery.enabled=false`, and `publicationRecoveryEnabled()` accepts only `true` or `false` and throws otherwise. `PublicationRecoveryProvisioningTest` (10) covers it. The docs section matches the code. `Phase3TestProfile` enables it with short bounds for the e2e only. |
| A post-tap covered publication without a snapshot closes the stream with the existing resync/GetTask failure, including a late arrival after the watermark | Pass | `PublicationReconciler.Observation.admit` and `complete`. `completeWithoutSnapshot` sets `unseenThrough = G`. A late arrival at or below `G` raises `COVERED_WITHOUT_SNAPSHOT` unless the stream already saw its final event. A redelivery above `G` is ignored. Subscriptions with a snapshot keep the silent discard. Mutants mut1 to mut6 of the fix3 review each fail a named test (log). |

## 4. The previously missing scenario: metadata-only full Task proposal from a message-bearing status

I read the actual fixture branch (`AgentExecutorProducer.proposeMetadataOnly`) and the actual scenario (`Phase3MutationScenarios:750-865`, plus its helper methods).

| Required element | Finding |
|---|---|
| Real public send, get, list and stream | **Met.** A public 1.1 writer (`open("1.1", false, false)`) sends the input through the real route. A public 1.1 subscriber is attached throughout and its initial snapshot is awaited. After the proposal the scenario calls `writer.getTask(…timelineLength 50)` and `writer.listTasks(…timelineLength 50)`. It also reads the stored task from the store, a public 1.0 client, and the 0.3 reader. |
| Deterministic barriers | **Met for the ordering that matters.** `entered` and `release` latches hold the agent between its own committed status and the proposal. The test reads the isolated pre-state and takes the published, replicated and stream marks before releasing. The only polling waits are positive waits (the agent's 400 × 50 ms poll for its own status commit, `awaitSubscriberGeneration`, `awaitPublished`). A positive wait cannot falsely pass. |
| Starting state is a message-bearing status, and the proposal is built from it | **Met.** The agent first commits `startWork(message "p3-status-message")`. It polls `refreshTask()` until that status message id is stored. The test asserts `before` equals the agent's captured snapshot exactly, and `before.status().message().messageId()` equals the expected id. `generation == created.generation + 2` (the input and the status are the only mutations). |
| Committed generation | **Met.** `ProposalResult.Committed` with `changed()`. Exactly one event, a `TaskStatusUpdateEvent` at `before.generation + 1`. `assertMetadataOnlyCommit` checks `generation == before + 1` on the stored, send, get, list and stream views. |
| Status timeline observation | **Met.** The newest timeline entry is a `StatusValue` at `before.generation + 1`. On full-timeline views the timeline grows by exactly one, and exactly one entry lies above the starting generation. The status is compared by identity to the unchanged status. |
| Metadata | **Met, loosely.** The committed event metadata equals `change` exactly. Views are checked by `containsAll(change)` (G-3). |
| Status message ID and legacy occurrence count | **Met.** The status message id is retained on the stored task, committed event, newest timeline entry, streamed event and the 1.0 view. `occurrences` (history plus status message) is 1 before and after on the 1.1 views and the 1.0 client. History ids are unchanged. `messageEntries(...) == 0` on the full-timeline views. |
| No new Message or message-update event | **Met.** The committed events list has size 1. The published spy slice after the mark is non-empty and `allMatch("TaskStatusUpdateEvent")`. The replicated slice has no `TaskMessageUpdateEvent` or `Message`. The subscriber receives exactly one new event and it is a status update. The mutation probes (below) show that an extra message-update event in the commit fails the test. |
| 0.3 view | **Partly met.** The `TaskRead` of the 0.3 reader has no status message, so 0.3 coverage is unchanged history ids and unchanged state (G-3). |
| Mutation evidence | **Strong for three families; one residual.** The fix4 session killed the "status message duplicated into history" mutant (`expected: <1> but was: <2>` at `assertMetadataOnlyCommit:880`), with the old test passing. I added three independent mutants. Each failed the new test and none failed the old one (section 2.3): no timeline entry (`:882`), an extra message-update event (`:801`), and the status message dropped (an NPE at `:806`). **Residual:** I did not mutate the subscriber stream or published-spy assertions on their own, so those two checks have no demonstrated teeth (G-1). |
| Cleanup and reuse | **Met.** `PHASE3_METADATA_PROBE` is set before the writer opens and cleared in `finally` by `compareAndSet`. The `finally` also releases the latch, so a failure cannot leave the agent held or the static installed. The agent throws `InvalidParamsError` if its branch is hit without a probe. Task and client cleanup is covered by the existing `@AfterEach phase3Cleanup`. The scenario needs no new abstract hook, so REST and gRPC inherit it by extending `Phase3MutationScenarios`. The static probe assumes serial test execution, the same convention as `PHASE3_HOLD`. |

**Design note (not a defect).** The agent uses `submitTask(TaskProposal.create(snapshot, proposed))` with a refreshed snapshot, not the legacy `addTask` or `emitEvent(Task)`. The fixture javadoc explains why. `ExecutionProposalContext.submitLegacyTask` proposes against the Task captured when the invocation started. `TaskMutationPlanner.planInput` (lines 92-97) moves a status message into the history and clears it when an input commits, so an input-triggered invocation never starts from a message-bearing status. I checked both claims against the source. The scenario still goes through `AgentEmitter`, `ExecutionProposalContext`, the submission service, the committer and the store. The shared bullet in A03 says "metadata-only full Task updates" and does not name `addTask`. I accept this, with G-2 below. The thin legacy wrappers `addTask` and `emitEvent(Task)` are not end-to-end proven from a message-bearing start; only a cancel-invocation variant could do that, and I do not require it.

## 5. Independent production review

I read the production diff of the whole range (`JSONRPCTransport`, `A2AServerRoutes`, `JSONRPCUtils`, `JSONRPCHandler`, `PublicationReconciler`, `DefaultRequestHandler`, `a2a-defaults.properties`). I found no defect.

- **Client `observe`.** It shares `startObservation` with `streamInput` and applies interceptors, rejects a cursor before any I/O, and is unsupported on selected 1.0 before any request. Cancellation closes the observation.
- **Route id echo.** A parsed request now echoes its id in a pre-stream `A2AError`. `JSONRPCUtils` wraps a selected-1.1 `InvalidParamsError` as `InvalidParamsJsonMappingException` with the id, and `getAndValidateMethod` is shared with the frozen path. `Phase2CodecTest` pins the 1.0 behavior as unchanged. This is a JSON-RPC 2.0-conformant change for 1.0 on this route, and no fixture contradicts it.
- **Handler.** The only guards kept are the delivery-version gate and the 1.0 legacy `timelineLength` gate. `timelineLength` is forwarded on get and list.
- **Recovery.** It is attached before the agent starts. `observeSendStream` reads the authoritative generation after the gate is installed. A new task starts at watermark 0. Detach paths cover `!agentStarted`, consumer `onError`, the outer `RuntimeException | Error` catch, and attach failure (`queue.close(true)`). `@PreDestroy` closes the reconciler. With a non-`AtomicTaskStore` the feature logs a warning and stays off.
- **No change** in `compat-0.3`, REST, gRPC, `MainEventBusProcessor` or the T13 reserve/release pairing.
- **Fixture sharing.** `AgentExecutorProducer` is shared with other test modules, but the new branch is reached only by the exact input text `phase3:metadata-proposal` and an installed probe, so other tests are unaffected. This is by reading and by the compile-only evidence of the consumer modules.

## 6. Findings

No Critical, High or Medium finding. All are Low and non-blocking, and none was introduced as a defect by the candidate.

### F-1 (Low, carried, unchanged) Route-level `Throwable` after a successful parse returns a null-id `InternalError`

- **Path:** `reference/jsonrpc/src/main/java/org/a2aproject/sdk/server/apps/quarkus/A2AServerRoutes.java:413-415`. `catch (Throwable t)` builds `new A2AErrorResponse(new InternalError("Internal error"))` with no id. The adjacent `A2AError` branch (line 401) now echoes `request.getId()`. I re-read the lines at HEAD and confirmed the branch is unchanged.
- **Consequence (by code reading, not executed):** a 1.1 client that requires a matching id would report a missing id instead of the typed `InternalError` for an unexpected route-level throwable.
- **Repro idea:** make the handler throw a `RuntimeException` after a parse and call `invokeJSONRPCHandler`. `A2AServerRoutesTest` has no such test.
- **Why Low:** the path predates T17 and needs an unexpected route-level throwable. It is not a shared acceptance bullet.
- **Follow-up:** use the same `request != null ? … : …` pattern and add one route test.

### F-3 (Low, carried) No test pins interceptor interception on the new built-in `observe`

- **Path:** `JSONRPCTransport.observe` to `startObservation` to `applyInterceptors(SUBSCRIBE_TO_TASK_METHOD, …)`.
- I found no test in `client/transport/jsonrpc`, `client/base` or the OTel modules that drives an interceptor through `observe`. The `streamInput` interceptor test (`Phase2PublicClientTest:489-510`) covers the shared helper, and the wrapper tests (`VersionedWrapperTest`, `OpenTelemetryClientTransportTest`) are mock-based.
- **Why Low:** the code path is shared and the code is small.

### G-1 (Low, evidence) Stream and published-spy assertions of the new scenario have no demonstrated mutation teeth

My three mutants show that the commit-event list, the timeline entry and the retained status message are checked. They do not isolate the `streamed.size() == 1` check or the `published … allMatch("TaskStatusUpdateEvent")` check. A mutation that publishes an extra event but leaves the committed list unchanged was not tried. Reading shows both assertions and the positive `awaitPublished` guard.

### G-2 (Low, documentation) The scenario javadoc and probe wording say `addTask` where the code uses `submitTask`

- The javadoc of the new test says the agent "submits a real full Task (`addTask`)". The code uses `agentEmitter.submitTask(TaskProposal.create(snapshot, proposed))`. The fixture javadoc explains the reason, but the scenario javadoc is inexact. It also says "a later invocation", while the proposal is made by the same invocation after its own status.
- **Effect:** none on behavior. This only risks misleading T18/T19 authors.

### G-3 (Low, assertion looseness)

- Metadata on views is checked with `containsAll(change)`, so an extra stored metadata key would not be caught. The committed event metadata is exact.
- `replicated` uses `noneMatch`, which can be vacuous. The positive check is the `published` list.
- The 0.3 `TaskRead` has no status message, so the 0.3 occurrence count is covered only through history ids and state.
- The negative stream and published checks run right after the first event arrives, and are not repeated at a server-side quiescence barrier. A late extra event would be missed. This can cause a false pass on a rare late event, never a false failure.

### G-4 (Low, pre-existing stale comment)

`AgentExecutorProducer.java:222` says "input appended to an existing task by the core never reaches this". The new scenario does reach `handlePhase3` through an ordinary input on an existing task, so the comment is misleading. The line predates this commit.

### Carried findings from earlier reviews

- The fix3 Low evidence gaps remain:
  - the committed id-echo evidence covers one string id and has no route-level test of a numeric id (the codec test covers `42`);
  - the handler N1 test does not pin the failure reason or remote delivery.
- N2 and N11 of the correction review, the F1-F12 findings of the first review, R1 and R2 of the fix2 review, and F-2 of the earlier final review are resolved. F-2 is closed by section 4.

## 7. Retained limits: scope limit versus blocker

| ID | Item | Classification | Rationale |
|---|---|---|---|
| L-1 | F1.1 finite filtered demand is proven in-process (`Phase3OldObserver`, `ProjectingPublisherTest`), with no separate wire probe. | **Limitation, not a blocker** | `JSONRPCHandler` subscribes to the projected publisher with `request(1)` at lines 896 and 902, and replenishes one item per `onNext`, so production applies finite demand on the wire path. I did not run a wire probe. It could be added in T18/T19 or T20. |
| L-2 | CDI with JPA (`@PreDestroy` with `JpaDatabaseTaskStore`) is not proven. The `@PreDestroy` is verified by a direct call. | **Limitation, not a blocker** | The feature is opt-in and default off. T17 changed no JPA code. Live PostgreSQL and Kafka verification is T20. |
| L-3 | The "1.0" client in the new e2e is the current SDK pinned to 1.0, not a historical binary. | **Limitation, not a blocker** | Frozen 1.0 evidence is the unchanged suites. Server changes for 1.0 semantics are limited to `rejectLegacyTimeline` (V1_0 only) and the id echo on this route. A historical-peer check is the T20 compatibility gate. |
| L-4 | Sleep-based checks: the 1000 ms webhook negative control, the 300 ms direct-message settle (re-asserted at a server-side `awaitQueueClosed`), the 400 ms `sendCollected` settle, `Phase3OldObserver.awaitFrames` polling, `awaitQuiescent`, and the post-event negative checks of G-3. | **Limitation, not a blocker** | Positive waits cannot falsely pass. Negative waits could miss a very late event, so the risk is a false pass on a rare late effect, not a false failure. The direct-message path is backed by the queue-closed barrier. The guard race and the new scenario use barriers. |
| L-5 | Default recovery bounds (5/5/10 s) are covered only by injected-clock unit tests. The e2e uses 200/1000/600 ms. | **Limitation, not a blocker** | The bounds are configuration, and the logic is identical. |
| L-6a | N3 (an artifact-only request has no stream), N6, N7 (an opening input with artifacts may already have run the executor), N8 (no per-task tenant ownership model). | **Retained limits** | Documented earlier. No T17 shared bullet requires them to be closed. |
| L-6b | **N9: an input message's role is not validated.** A client-sent `ROLE_AGENT` message is stored as agent-role history. | **Unresolved normative question. No user approval is recorded. Not a decision.** | I looked for normative text and found only artifact role and origin (`phase-0/selected-policies.md` G6, `architecture.md` 195-210, `validation.md:90`). The plan's "role" bullet is therefore read as artifact origin, which the tests cover. That reading is defensible but not confirmed. I do not treat the absence of a rule as approval. **If the developer rules that the shared bullet includes the input message role, T17 and the T18/T19 fixture reopen,** and the fix is a validation plus a no-partial-mutation scenario. Until then it is carried openly. |
| L-7 | The Android SSE cancellation failure (section 2.4). | **Deferred by the user, unrelated, cause unknown** | It is not called fixed. The failed log and the later passes are both preserved. |
| L-8 | REST and gRPC skips (18 and 22) and no REST/gRPC test run on the candidate. | **Out of T17 scope (T18/T19)** | Only compile evidence exists. Skipped tests are not counted as passed, and no REST or gRPC acceptance is claimed. |
| L-9 | The three mutation probes ran in a copy of the candidate, not in a repository build. | **Limitation, not a blocker** | The copy is a `git archive` of the pinned SHA with the planner restored and its hash checked. |

## 8. Previous findings, evaluated

| Finding | Status |
|---|---|
| F1-F12 (first review), N2, N11 (correction review), R1 and R2 (fix2 review) | Resolved, re-verified in code and by the recovery and codec mutants (log). |
| F-2 (final review, revision 2, Medium blocking) | **Closed.** Section 4. |
| F-1 | Open, Low, unchanged (section 6). |
| F-3 | Open, Low, unchanged (section 6). |
| F1.1 finite-demand wire proof | Limitation L-1. |
| CDI/JPA not proven | Limitation L-2. |
| N9 | Unresolved normative question L-6b. It is not resolved by this acceptance. |
| Sleep-based negative checks | Limitation L-4. |
| Test hook cleanup and reuse | Verified for `PHASE3_METADATA_PROBE` (section 4). The static probe and `PHASE3_HOLD` assume serial execution, and the new probe is `compareAndSet`-cleared in `finally`. |
| N-1 to N-4 (fix4 review) | N-1 is narrowed by my three added mutants (G-1 keeps the residual). N-2 is accepted (section 4). N-3 is G-4. N-4 is G-3. |

## 9. Next actions for the developer

1. Rule on N9 (does the shared "role" bullet include the input message role?). Until then the question stays open, and acceptance depends on the artifact-origin reading.
2. T18 and T19 (REST and gRPC) inherit the shared fixture, including the new scenario. These tasks and the T20 items listed in section 1 were not started.
3. Optional cleanups, none blocking: F-1 (id in the `Throwable` branch plus one route test), F-3, G-2 and G-4.

## 10. Final check

- `git status --short` shows only the untracked `P3-T17-fix4-review.md`, plus this file once written. The fix4 review hash `56b84abf…` is unchanged.
- No test, production code, previous review or shared status was edited. No commit and no push were made. No nested agents and no Serena were used.
- I recorded the failures of this session: the tooling failure of my first mutation patch (no `python3` or `diff`), whose three runs are labeled as controls, and the user-deferred Android failure of earlier sessions, preserved and not called fixed.
