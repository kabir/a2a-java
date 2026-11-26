# P3-T17 fix4 targeted review (F-2 correction)

**Verdict: targeted review CLEAR for F-2. No blocking finding. This is not final task acceptance.** A separate fresh full independent review must follow before T17 acceptance or import.

## 0. Identity, window and model

- Candidate: `ba09fda2177d2a752e1c38eff736ee125db2cb6e` (`test(t17): prove metadata-only full Task proposal from a message-bearing status end to end`). `ba09fda2` resolves to exactly this SHA, which equals `HEAD` on branch `work/p3-t17`.
- Parent: `5c0369db1e55b27325c8154887a0e181ddaf0368`, verified as `HEAD^`. Original task base: `d869e957b310e2585409ac3b3adb166d7823d36c`.
- `git status --short` was empty at the start of the review. The only file written by this review is this one.
- Review window: started 2026-11-26 22:39 UTC, initial deadline 2026-11-27 00:39 UTC. Finished about 22:45 UTC. The extension was not used and the original window is preserved.
- Requested model: the configured IBM Bob model. Observed model identifier: **unavailable** (no telemetry). Sol is not claimed. Astra was not used.
- No nested agents, no Serena, no push, no external state. I edited no production code, tests, previous records or shared status, and made no commit.
- Maven and test commands run by this review: **none**. All build and test evidence is inspected from the implementer's committed-candidate logs. See section 5 for why, and for the one probe I attempted but could not run.

## 1. Scope of the diff reviewed

`git diff --name-only 5c0369db ba09fda2` lists 7 files:

| File | Lines | Kind |
|---|---|---|
| `tests/server-common/.../AgentExecutorProducer.java` | +78 | test fixture |
| `tests/server-common/.../Phase3MutationScenarios.java` | +213 | shared scenario |
| `handoffs/P3-T17.md`, `progress-T17.md` | docs | records |
| `reviews/P3-T17-final-review.md`, `-final-review-rev1.md`, `-fix3-review.md` | docs | preserved reviews, committed unchanged |

`git diff --stat 5c0369db HEAD -- '*.java'` shows 2 files and 291 insertions. **No production file changed.** No `server-common`, transport, client or spec source is in the diff, so no architecture change.

## 2. Findings

No Critical, High or Medium finding. The Low items below do not block the F-2 closure. Each cites its path and explains its effect.

### N-1 (Low, evidence gap) Only one mutation probe; the other assertion families have no mutation proof

- **Evidence:** `mut1.log` (the only probe). `mutant1.diff` removes `!syntheticMetadata &&` at `TaskMutationPlanner.java:307`. This makes a metadata-only proposal copy the status message into the history.
- **What it proves:** the new test fails and the old input-count test passes with the same mutant.
  - The log shows `Tests run: 2, Failures: 1`. The failing assertion is `Phase3MutationScenarios.assertMetadataOnlyCommit:880`: `stored: the status message still occurs once ==> expected: <1> but was: <2>`.
  - The failure is a behavioural assertion reached through the real stack. It is not a compile error and not a setup failure.
  - The mutant compiled and reached the test. The planner was rebuilt in the `-am` reactor.
  - The planner was restored byte-identically afterwards. The sha256 of the current file equals the `.orig` copy (`cf469c1c…`). Its mtime of 22:30:36 is before `jsonrpcmodfull` started at 22:31:11, and `git status` is clean.
- **Gap:** no mutant targets the other required elements: the generation count, the new status timeline entry, an extra `MessageValue` entry, a `TaskMessageUpdateEvent` in the committed events, or the stream and publication checks. Code reading shows assertions for each (section 3, items 4-8), and the core planner has its own unit tests (`FullTaskProposalTest`). Those assertions have not been shown to have teeth.
- **My attempt:** I tried to add a second probe, a mutant that emits an extra `TaskMessageUpdateEvent`, in an isolated copy under `/tmp`. The edit tool refused to write outside the workspace and I did not work around it. I removed the partial copy and did not run it. This is recorded as a limit, not as a pass.
- **Effect:** Low. The user's requirement is that the probe demonstrates a real failure rather than setup noise. It does for the occurrence-count mutation. The remaining assertions are covered by reading, not by mutation.

### N-2 (Low, ruling recorded) `submitTask(TaskProposal.create(refreshTask(), …))` is used instead of `addTask` / `emitEvent(Task)`

- **Claim checked:** the implementer says a legacy `addTask` cannot start from a message-bearing status.
  - `ExecutionProposalContext.java:70-72` confirms `submitLegacyTask` is `submitTask(TaskProposal.create(invocationStartSnapshot, task), queue)`. `AgentEmitter.addTask` (line 615) delegates to it, and `emitEvent(Task)` (line 646) delegates to `addTask`.
  - `TaskMutationPlanner.planInput:92-97` moves a status message into the history and clears it whenever an input message is committed. An input-triggered invocation therefore never starts from a message-bearing status.
  - A proposal built from the old snapshot after the agent's own acknowledged delta must conflict (`acceptance.md` lines 62-64).
  - So the only way `addTask` can start from a message-bearing status is a cancel invocation, which ends in a terminal state.
- **Ruling:** acceptable for the shared bullet. The bullet in `acceptance.md` lines 66-69 says "metadata-only full Task updates" and does not name `addTask`. The scenario goes through the production `AgentEmitter` → `ExecutionProposalContext` → submission service → committer → store path, and only the choice of base snapshot differs from `addTask`. The F-2 review wording was "via `addTask` or `emitEvent(Task)`" as an example of the full Task path. The deviation is recorded openly in `progress-T17.md`.
- **Residual:** the thin wrappers `addTask` and `emitEvent(Task)` are not end-to-end proven from a message-bearing start, and that is hard to do given the design. If the developer wants that, it is a cancel-invocation variant. I do not require it, and I do not treat it as weakening acceptance.

### N-3 (Low, stale comment, pre-existing) `AgentExecutorProducer.java:222`

- The javadoc says "input appended to an existing task by the core never reaches this". The new scenario reaches `handlePhase3` through an ordinary input on an existing task. The probe barrier `entered` is only released by that agent branch, and the test passed in `new3.log`.
- The line is not part of this diff (it predates the commit), so this is not a regression. The comment is misleading, and it appears to have shaped the earlier description of the old test as "not reaching the agent branch". The F-2 gap itself (no full Task proposal, no message-bearing status, no metadata, no stream check) is unaffected and was real.
- Suggested follow-up: fix the comment when the file is next touched.

### N-4 (Low, negative-assertion rigour and looseness)

- The stream and published checks happen immediately after the first status event arrives. They are not repeated at a server-side quiescence barrier (for example a queue-closed wait) after the proposal. An extra late event would be missed. This can only cause a false pass on a rare late event, not a false failure.
  - The writer's blocking send has already returned (`send.get`) and the proposal result has been awaited. This reduces the window.
- `replicated` uses `noneMatch` and can be vacuous (no positive check). The positive check is the `published` list, which `awaitPublished` forces to be non-empty, and its `allMatch("TaskStatusUpdateEvent")`.
- Stored metadata is asserted by `containsAll(change)`, not as exactly "before plus change". Because `before.metadata()` has no `p3-meta-key` and the committed status event metadata equals `change` exactly, an extra stored metadata key would not be caught.
- The 0.3 reader's `TaskRead` has no status message. For 0.3 the occurrence count is covered only through unchanged history ids and state. A duplicate history entry would still be caught.
- **Effect:** Low, test strength only.

### Carried unchanged

F-1 (route `Throwable` null-id), F-3 (no interceptor test on built-in `observe`), the two fix3 Low evidence gaps, N9 (unresolved normative question, not a decision), and L-1 to L-7 of `P3-T17-final-review.md` section 7. None was changed by this commit. The Android SSE failure `AndroidA2AHttpClientSSETest.cancellableSseDisconnectsHeldOpenPeer:213` is user-deferred and its cause is unknown. It is not called fixed here. No Android source is in the diff, and no Android run is part of this review.

## 3. Acceptance analysis: `acceptance.md` A03 metadata-only full Task bullet (lines 66-69)

Evidence is the shared scenario `metadataOnlyFullTaskProposalFromAMessageBearingStatusCommitsOneStatusObservationAndNoMessage` (`Phase3MutationScenarios.java:~750-865`, driven by `Phase3JSONRPCMutationTest`) plus the `phase3:metadata-proposal` branch (`AgentExecutorProducer.java:~262-300`).

1. **Production path.**
   - The writer and subscriber are real public 1.1 clients (`open("1.1", …)`) over the JSON-RPC routes to the Quarkus reference server, with the real executor, `ExecutorSubmissionService`, committer, store, event bus and subscriber delivery.
   - The proposal is a full `Task` built as `Task.builder(snapshot.task()).metadata(...)`, submitted through `AgentEmitter.submitTask(TaskProposal.create(snapshot, proposed))`.
   - There is no controlled peer and no planner-direct call in this scenario. **Satisfied**, subject to N-2.
2. **Message-bearing starting status.** The agent first calls `startWork(message "p3-status-message")` and polls `refreshTask()` until the stored status carries that message id. Before the proposal the test asserts that the status message id is the expected one and that the stored task equals the agent's snapshot exactly (`assertEquals(before, probe.invocationTask.get())`).
3. **Deterministic barrier timing.**
   - Latches `entered` and `release` are used instead of sleeps for the key ordering.
   - The proposal is held until the test has read the isolated pre-state: `created.generation() + 2` (input plus status), the subscriber has seen the generation, and the `published`, `replicated` and stream marks are taken.
   - The `entered` wait is 20 s and the agent's poll bound is 400 × 50 ms. The only polling is positive waits, which cannot falsely pass.
4. **Exactly one generation and one status timeline entry.**
   - `ProposalResult.Committed` with `changed()`, exactly one event, a `TaskStatusUpdateEvent` at `before.generation + 1`.
   - `assertMetadataOnlyCommit` checks `generation == before + 1`, and the newest timeline entry is a `StatusValue` at that generation.
   - On full-timeline views it also checks `size == before.size + 1` and exactly one entry above the starting generation.
   - The check is run on stored, send (generation, newest entry and message occurrence only), get, list and the reduced stream task.
5. **Metadata mutation.** The committed status event carries exactly `change`. All views contain the entry, subject to the looseness noted in N-4.
6. **Message identity and occurrence count.**
   - The status message id is retained on the stored task, the committed event, the newest timeline status, the streamed status event and the 1.0 view.
   - `occurrences` (history plus status message) equals 1 before and after, on 1.1 views and on the 1.0 client. Legacy history ids are identical.
   - `messageEntries(...) == 0` on full-timeline views, so there is no `MessageValue` timeline entry.
   - 0.3: history ids and state unchanged (see N-4).
7. **No new Message or message-update event.** The committed events contain exactly one status event. The `published` spy slice after the mark is non-empty and contains only `TaskStatusUpdateEvent`. Replication has no `TaskMessageUpdateEvent` or `Message`. The active subscriber receives exactly one event, a status update, and no `TaskMessageUpdateEvent`.
8. **No other mutation.** The scenario asserts equal status, artifacts and history, an untouched timeline prefix and unchanged legacy history.
9. **Appropriate send, get, list and stream evidence.** All four are present for the public 1.1 client, plus the legacy 1.0 and 0.3 reads.
10. **Wire comparison fidelity.** Wire views are compared by status identity (state, timestamp, message id, role, task, context, text), because the JSON codec maps absent metadata to `{}`. Exact record equality remains on server-side stored and committed values. This is a legitimate narrowing and was documented after the second failed run.
11. **No weakened acceptance.** The old test `metadataOnlyStatusPreservesTheMessageOccurrenceCount` keeps its name and body, with only a javadoc line stating that it covers ordinary-input occurrence counts. No other assertion changed. The commit adds 291 test lines and removes none (`git diff` shows insertions only).

## 4. Cleanup and reuse

- **Probe state.** `PHASE3_METADATA_PROBE` is set before the writer is opened and cleared in `finally` by `compareAndSet(probe, null)`. The `finally` also does `probe.release.countDown()`. So a failure at any later point (for example the 20 s `entered` wait) cannot leave the agent held or leave the static installed for another test.
- **Branch guard.** The agent throws `InvalidParamsError` if the branch is hit without an installed probe.
- **Task and client cleanup.** The existing `@AfterEach phase3Cleanup` closes the tracked clients and deletes the created tasks. The new test opens its clients through `open(...)` and creates the task with `createWorkingTask()`, so it is covered.
- **Reuse for T18 and T19.** `AgentExecutorProducer` is a shared fixture in `tests/server-common`. The new agent branch is gated on the `phase3:` prefix and an installed probe, so other tests are unaffected. The scenario needs no new abstract hook (it uses the existing `storedTask`, `read03Task` and `open`). REST and gRPC modules will inherit it by extending `Phase3MutationScenarios`.
  - Today only `Phase3JSONRPCMutationTest` extends it. The REST and gRPC modules were only compile-checked against the fixture, not run (below).
- **Serial execution assumption.** The static `PHASE3_METADATA_PROBE` and `PHASE3_HOLD` assume tests in the module do not run in parallel. This matches the existing `PHASE3_HOLD` convention.

## 5. Reviewed evidence (existing logs, not rerun)

All logs are in `/tmp/p3-t17-fix4-logs`. Every command is `mvn -B -ntp -Dmaven.repo.local=/tmp/p3-t17-fix4-m2 …` under `timeout -k 30 1200`. Exit codes come from the `.exit` files.

| Log | Command scope | Start to end (UTC) | Exit | Result |
|---|---|---|---|---|
| `red1-compile-error` | `-pl tests/multiversion/jsonrpc -am -Dtest=Phase3JSONRPCMutationTest verify` | 22:22:58 to 22:23:21 | 1 | Compile error from a corrupted concurrent edit. **Not behavioural evidence.** Recorded as such. |
| `new1-npe-failure` | same | 22:24:19 to 22:25:39 | 1 | 22 run, 1 error: NPE on `before.status().message()`. This revealed the design finding about `planInput`. |
| `new2-status-equality-failure` | same | 22:27:17 to 22:28:19 | 1 | 22 run, 1 failure: wire status equality, absent vs `{}` metadata. Test over-strictness, fixed in the test. |
| `new3` | same (plan check 2) | 22:28:47 to 22:29:50 | 0 | **22 run, 0 failures, 0 errors, 0 skips**; the class took 55.9 s, the build 1:02. |
| `mut1` | same, `#metadataOnly*`, with the planner mutant | 22:30:12 to 22:30:31 | 1 | **2 run, 1 failure (killed)**, `expected: <1> but was: <2>` at `assertMetadataOnlyCommit:880`. The old test passed. |
| `plan1` | `-pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest … test` | 22:30:49 to 22:31:11 | 0 | client 26 (19 + 7), 0 skips; `JSONRPCHandlerTest` 80 run, **1 skipped (pre-existing)**, 0 failures. BUILD SUCCESS, 21 s. |
| `jsonrpcmodfull` | `-pl tests/multiversion/jsonrpc -am verify` | 22:31:11 to 22:36:43 | 0 | BUILD SUCCESS, 5:31. Module **165 run, 0 failures, 0 errors, 0 skips**. This includes `MultiVersionJSONRPCTest` 75, `MultiVersionJSONRPC_v0_3_Test` 40, `Phase3JSONRPCMutationTest` 22, `Phase3JSONRPCNoUserArtifactsTest` 4. |
| `consumers-testcompile` | `-pl tests/multiversion/rest,tests/multiversion/grpc,reference/rest,reference/grpc,reference/jsonrpc -am test-compile` | 22:36:52 to 22:37:42 | 0 | BUILD SUCCESS, 50 s. Compile only. No REST or gRPC tests were run. |

- **New check counts.** One new shared scenario. `Phase3JSONRPCMutationTest` goes from 21 to 22. The module goes from 164 to 165. **New skips: 0.** The only skip anywhere in the evidence is the pre-existing `JSONRPCHandlerTest` one.
- **Source hashes and timing.**
  - The current sha256 of the two changed test files equals `sha256-final.txt` (`AgentExecutorProducer` `3ee14a2c…`, `Phase3MutationScenarios` `7ca4ca10…`).
  - `Phase3MutationScenarios` was last modified at 22:28:40, before `new3` started at 22:28:47. `AgentExecutorProducer` was last modified at 22:27:06. So the counts above come from the committed final test sources.
  - `jsonrpcmodfull`, `plan1` and `consumers-testcompile` started after the planner was restored.
- **Failed runs preserved.** The compile-error run, the NPE run and the wire-equality run are retained as logs and in `progress-T17.md`. The null-vs-empty serialized metadata handling is recorded in the progress record and in the `assertSameStatus` javadoc.
- **Consecutive failed attempts.** None of the three failed runs reached three attempts on the same defect (distinct causes), so the Sol-diagnosis trigger was not reached.
- **Preserved reviews.** The committed `P3-T17-final-review.md` (revision 2, `363fdb38…`), `P3-T17-fix3-review.md` (`9d8c8eeb…`) and `P3-T17-final-review-rev1.md` (`07599c85…`) match the `preserved-sha256-before.txt` values recorded before the correction, and the rev1 file matches the original `/tmp/p3-t17-review-final-logs/P3-T17-final-review.rev1.md` (`07599c85…`). **All three are byte-identical.** Revision 1 is superseded by revision 2, which is stated in the handoff and progress record.
- **Reused earlier evidence, not rerun here and not re-observed by this review.**
  - The codec, recovery, routes, client/base, server-common-full and REST/gRPC reactors from `/tmp/p3-t17-review-final-logs` and `/tmp/p3-t17-fix3-logs` remain valid because production is byte-identical to `5c0369db` and no REST/gRPC test source changed.
  - The earlier REST and gRPC counts (REST 146 with 18 skipped, gRPC 131 with 22 skipped) are inherited and were not re-observed by this review or by this commit's session.
- **Why I ran nothing.** The existing logs contain a targeted failing probe, a passing candidate run on frozen sources, and a full module run on the same sources. No specific uncertainty needed a re-run. The one uncertainty I wanted to probe (N-1, the event-level mutant) could not be run, as described above.

## 6. Constraints check

- Production lines changed: 0. Architecture: unchanged.
- Skips: nothing is hidden behind a skip. The only skip is pre-existing and unrelated to the F-2 scenario.
- The Android SSE failure is user-deferred, its cause is unknown, and it is not claimed fixed.
- The T13 live PostgreSQL/Kafka gate, the compatibility gate, the Java 17 clean install and the phase audit remain T20 obligations. Phase 4/5/6 exclusions and the opt-in default-off publication recovery are unchanged.

## 7. Conclusion

The correction closes F-2 with real end-to-end evidence: public 1.1 clients, JSON-RPC routes, real executor, core, store and an active subscriber, with deterministic barriers, an isolated pre-state, one generation, one status timeline entry, a metadata change, an unchanged message id and legacy occurrence count, and no message or message-update event. A real mutation made the new test fail on an assertion while the old input-count test still passed. State cleanup is sound and the fixture is reusable.

The Low items N-1 to N-4 and the carried F-1, F-3, N9 and L-1 to L-7 do not block this targeted clearance.

**Targeted review: clear. T17 is not accepted.** A separate fresh full independent review must follow. T18, T19 and T20 were not started.
