# P3-T17 targeted re-review of correction 3 (37aa8600..5c0369db)

## Disposition: **Targeted review clean. No blocking finding. T17 is NOT accepted.**

- **R1 is closed.** A committed event at or below the starting generation `G` that reaches a snapshot-less send observer after `completeWithoutSnapshot(G)` now closes the stream with the existing resync `InternalError`. I traced the code and ran the tests. I also ran six mutation probes on a `/tmp` copy, and all were killed (see "Mutation probes").
- **R2 is closed.** A non-string `method` on selected 1.1 now yields `IdJsonMappingException` carrying the request id. I probed 72 id/method/version combinations. The 1.0, 1.1 and 0.3 results are identical.
- Two Low findings and three Info items are listed below. None blocks this targeted review.
- A clean targeted review is not full acceptance. **A separate fresh full independent review is still required** before T17 can be accepted or imported. T18/T19/T20 were not started.
- Nothing was edited, committed or pushed in the repository except this new file. Tests, production, the old review and status files are untouched.

## Identification

| Item | Value |
|---|---|
| Candidate (HEAD) | `5c0369db1e55b27325c8154887a0e181ddaf0368`, branch `work/p3-t17` (`git rev-parse HEAD`, verified at start and end) |
| Parent / original base | `37aa86003cd18d7011351f469c1b554d2a74e113` / `d869e957b310e2585409ac3b3adb166d7823d36c` |
| Git state | `git status --short` empty at start (21:47) and end (22:08) |
| Diff reviewed | `git diff 37aa8600 5c0369db`, 10 files, +575/-21. Production: `PublicationReconciler`, `DefaultRequestHandler` (javadoc only), `JSONRPCUtils`. Docs: `configuration.md`. Tests: `PublicationGapRecoveryTest`, `StreamingSendPublicationRecoveryTest`, `Phase2CodecTest`. The rest is handoff, progress and the preserved `P3-T17-fix2-review.md`. |
| Source hashes | `sha256sum -c` of the 8 files in `progress-T17.md` (fix-3 section) all matched, before and after my runs. File `/tmp/p3-t17-review-fix3-logs/sha256-now.txt`. The old review `P3-T17-fix2-review.md` is byte-identical (`b15d0fa4…`). |
| Compiled-class provenance | `server-common` and `spec-grpc` sources were recompiled by my own runs (class mtimes 21:48-21:49, newer than source mtimes 21:22-21:23). The aborted first chain (below) did the compile for `spec-grpc`. |
| Window | Start 2026-11-26 21:47 UTC, deadline 23:47 UTC. Finished about 22:10. The two-hour extension was **not used**. |
| Requested model | IBM Bob configured model. |
| Observed model ID | **Unavailable.** No telemetry. Not claimed to be Sol. Astra not used. |
| Other constraints | No nested agents, no Serena, no headless Bob, no push, no external writes. Only `/tmp` and this review file were written. |
| Maven | Online, private repo `/tmp/p3-t17-review-fix3-m2`. It was seeded from `/tmp/p3-t17-review-fix2-m2` (third-party cache) with `org/a2aproject` removed. Every command ran serially under `timeout -k 30 1200`. Every selector run used `-Dsurefire.failIfNoSpecifiedTests=false`. Logs, `.exit`, `.start` and `.end` files are in `/tmp/p3-t17-review-fix3-logs`. No root `test.log`. |

### Aborted first chain (preserved, not counted)

- My first chain omitted `-Dsurefire.failIfNoSpecifiedTests=false` on reactor selectors. `-am` pulls in modules with no matching test, so every selector command failed with "No tests matching pattern". That was my mistake.
- Logs are kept in `/tmp/p3-t17-review-fix3-logs/aborted-first-chain`. The `scfull` log there is incomplete.
- A broad `pkill -f` pattern then interrupted my session. I inspected the processes afterwards and no Maven process was left. I restarted the chain with the flag.
- Only the restarted chain's results are counted below.

## Checks actually run (on HEAD, exit codes from `.exit` files)

| Name | Scope | Exit | Time (UTC) | Result |
|---|---|---|---|---|
| codec | `-pl spec-grpc,jsonrpc-common -am -Dtest=Phase2CodecTest test` | 0 | 21:49:19-21:49:21 | `Phase2CodecTest` 11 run, 0 fail, 0 skip |
| recovery | `-pl server-common -am -Dtest=PublicationGapRecoveryTest,StreamingSendPublicationRecoveryTest,PublicationRecoveryProvisioningTest test` | 0 | 21:49:21-21:49:37 | Gap 37, StreamingSend 45, Provisioning 10 = **92**, 0 fail, 0 skip |
| routes | `-pl reference/jsonrpc -am -Dtest=A2AServerRoutesTest,Phase2BoundaryTest test` | 0 | 21:49:37-21:49:55 | 41 + 5 = 46, 0 skips |
| plan1 | `-pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest test` | 0 | 21:49:55-21:50:01 | client 26 (19 + 7); `JSONRPCHandlerTest` 80 run, **1 skipped = the pre-existing `@Disabled`** |
| cbase | `-pl client/base,reference/jsonrpc -am -Dtest=Phase3JSONRPCPublicClientTest,ExistingOrdinaryEntryPointReductionTest,Phase2BoundaryTest test` | 0 | 21:50:01-21:50:23 | 18 + 31 = 49, plus `Phase2BoundaryTest` 5, 0 skips |
| scfull | `-pl server-common -am test` | 0 | 21:50:23-21:51:57 | **server-common 1048 run, 0 fail/err/skip.** The only skips in the reactor are 3 upstream `VertxA2AHttpClientSSETest` (91 run / 3 skipped in that module). |
| jsonrpcmod | `-pl tests/multiversion/jsonrpc -am verify` | 0 | 21:51:57-21:57:56 (5:58) | BUILD SUCCESS. Module **164 run, 0 skips**: `MultiVersionJSONRPCTest` 75, `MultiVersionJSONRPC_v0_3_Test` 40, `Phase3JSONRPCMutationTest` **21**, `Phase3JSONRPCNoUserArtifactsTest` 4, auth/provisioning classes. The reactor skips are the same 3 Vert.x tests plus the 1 `JSONRPCHandlerTest`. |
| grpcrest | `-pl spec-grpc,jsonrpc-common,tests/multiversion/rest,tests/multiversion/grpc -am verify` | 0 | 21:57:56-22:04:04 (6:07) | BUILD SUCCESS. Multiversion REST **146 run / 18 skipped**, gRPC **131 / 22**. Compat-0.3 REST 200 (27), gRPC 53 (11). Reference gRPC 76 (11), REST 309 (27). `AndroidA2AHttpClientSSETest` 25 run, 0 fail. No failures or errors. |

Timing was normal (compare the 54-minute anomaly in the fix-2 review). `/proc/uptime` and wall time agree, and `jsonrpcmod` took 5:58 against 5:15 for the implementer.

Skip counts are identical to the previous baseline (`progress-T17.md`, fix-2 session). Nothing in this diff touches REST, gRPC or compat-0.3. I did not re-investigate the skip reasons.

**Not run:** the full reactor, extras (JPA, Kafka/Docker, OpenTelemetry), any CDI + JPA wiring, and recovery enabled on REST/gRPC.

## Mutation probes (my own, on a `git archive HEAD` copy in `/tmp/p3-t17-review-fix3-probe`)

The workspace was never mutated. The probe copy was restored and its `PublicationReconciler` `sha256` re-checked as `229be9e0…` (equal to HEAD) after each run. The mutation scripts and logs are in `/tmp/p3-t17-review-fix3-logs` (`mut.sh`, `mut4.sh`, `mut*.log`, `mut*.exit`). Selector: `PublicationGapRecoveryTest` + `StreamingSendPublicationRecoveryTest`, 82 tests each run.

| Mutant | Result | Failing assertion |
|---|---|---|
| mut1: drop `terminalSeen` from the guard | **1 failure** | `…PublishedAfterTheFinalEventDoesNotAddAFailureToAFinishedStream:624` expected null but was `COVERED_WITHOUT_SNAPSHOT` |
| mut2: guard always returns, which reverts the fix | **4 failures** | Gap `…AfterTheAttachClosesTheObservation…:555`, Gap `…AfterLaterGenerationsAreDelivered…:577`, and Streaming `…WasRead…:274` for both variants |
| mut4: set `unseenThrough` also for `complete(G)` | **1 failure** | `withASnapshotACoveredCommitPublishedAfterTheAttachIsStillDiscardedSilently:643` |
| mut5: `if (generation >= 0)` | **4 failures** | Same set as mut2. It is the same behaviour as the revert, so it adds nothing new. |
| mut6: guard reduced to `terminalSeen` | **2 failures** | `withASnapshot…:643` and `withoutASnapshotARedeliveredLaterGenerationIsStillIgnored…:603` |
| mut3: 1.1 path back to `request.get("method").getAsString()` | **1 failure** | `Phase2CodecTest.selected11NonStringMethodRejectionsCarryTheRequestIdLikeTheFrozenPath:239` (1.1, `"method":null`) |

All six were killed. The new tests discriminate each clause of the new branch: stale/duplicate above `G`, `terminalSeen`, the subscription discard, and the covered-late failure. All mutation exits were 1 as expected. The ordering of the new unit tests is a real commit, read, complete, publish sequence (see R1 below).

## Assessment

### R1: late covered publication after `completeWithoutSnapshot`: **resolved**

**Code** (`PublicationReconciler.Observation.admit`, `:587-610`, and `complete`, `:659-690`):

- `completeWithoutSnapshot(G)` sets `unseenThrough = G`. `complete(G)` leaves it at `NONE = Long.MIN_VALUE`.
- After attach, an arrival with `generation <= watermark` is ignored if `generation > unseenThrough` or `terminalSeen`. Otherwise `fail(COVERED_WITHOUT_SNAPSHOT)` runs.
- `unseenThrough` and `terminalSeen` are read and written under the observation `lock`. `fail` is idempotent through `ended`, so concurrent covered arrivals fail once.
- No wire code is added. The failure is the existing `InternalError` "...call GetTask to resynchronize...".
- Recovery changes no task: tests assert the store head is unchanged and, for remote delivery, that no webhook and no replication occurred.

**Ordering is genuine in the tests.**

- Unit test `withoutASnapshotACoveredCommitPublishedAfterTheAttach…`:
  - `saveTask(4)`, then `commit()` (generation 5, **unpublished**)
  - then tap, then attach, then `readAuthoritativeTask` (G = 5 asserted)
  - then `completeWithoutSnapshot(5)`, then `assertNull(failureReason)` and one active observation
  - and only then `publish(delayed, remote)`.
- The later-generations test also publishes generation 6 and 7 first, and asserts that they are delivered. Only then does the late generation 5 close the stream, and generations 6 and 7 are not repeated.
- The handler test `aCoveredCommitPublishedAfterTheStartingGenerationWasRead…` commits through the store hook that runs **before** the observed read. The delayed batch is distributed by the agent only after the stream started. It asserts the resync `InternalError`, no event, no Task snapshot, a released observation, and that `GetTask` returns the committed state. RED evidence in `progress-T17.md` row 2 (4 failures on old code) and my mut2 agree.
- Both local and remote (`isReplicated`) delivery are exercised at unit level, in loops over `{false, true}`.

**Legitimate duplicates and pre-tap events.**

- A redelivered generation above `G` is still ignored (`withoutASnapshotARedeliveredLaterGenerationIsStillIgnored…`, killed by mut6).
- Any arrival at or below `G` at a snapshot-less observation was never delivered. The sender's own agent events cannot be at or below `G`: `observeSendStream` reads `G` before `registerAndExecuteAgentAsync` starts the agent, and a new task has `G = 0`. The sender's own input commit is published synchronously (`commitAndPublish`) before the tap and the read.
- A commit made before the tap and distributed after it is indistinguishable from one made after the tap. It is handled the same way (conservative false positive). This is the user's N1 decision, and it is now documented in the class javadoc and in `configuration.md`. See F3.
- After the final event: `terminalSeen` leaves the finished stream alone (mut1).

**Subscriptions with a snapshot.** They keep the silent discard, because the snapshot replaces the covered event. This is pinned by `withASnapshotACoveredCommitPublishedAfterTheAttachIsStillDiscardedSilently` (killed by mut4/mut6).

**Gap/finality, queue cleanup, delivery guard, T13.**

- `watermark`, gap and stranded logic (`check`, `drainLocked`) are not changed.
- `MainEventBusProcessor` is not in the diff. The T13 reserve/release pairing and the commit-to-registration hooks are untouched. `theInputCommitOfAnObservedSendKeepsItsReserveAndReleasePaired` and `aRejectedInputToAnObservedSendLeavesNoHoldAndNoObservation` pass inside the 45 StreamingSend tests.
- Cleanup after failure: tests assert `activeObservationCount() == 0`, `!isScheduled()` and `child.isClosed()`.

**Default off.** The `DefaultRequestHandler` change is javadoc only. Provisioning stays 10/10.

**Weakening check.** No existing test was changed except one helper: `commitAndPublish` now delegates to the new `publish(batch, remote)`. Every other test change is an addition. No guard, assertion or wire semantic was relaxed.

### R2: selected-1.1 non-string `method`: **resolved**

- `getAndValidateMethod` is shared by the legacy path (`:218`) and the selected-1.1 path (`:255`). The legacy behaviour is byte-for-byte the same check, so frozen 1.0 and 0.3 are unaffected. `compat-0.3` has an empty diff in this commit.
- Probe `IdProbeTest` (in the probe copy only, not committed): 72 cases per run, versions 1.0, 1.1 and 0.3, ids `7`, `"s"`, `null` and missing, methods `null`, `{}`, `5`, `true`, `[]` and missing. Output is in `/tmp/p3-t17-review-fix3-logs/idprobe-output.txt`.

| Id | Method | Result in 1.0, 1.1 and 0.3 (identical) |
|---|---|---|
| numeric `7` | invalid or missing | `IdJsonMappingException` with `id=7` (Integer) |
| string `"s"` | invalid or missing | `IdJsonMappingException` with `id=s` |
| `null` | any | `JsonMappingException` "Invalid 'id' type: JsonNull" (no id echo) |
| missing | any invalid | `JsonMappingException` "Request 'id' cannot be null" |
| `null` or missing | missing | `IdJsonMappingException` carrying the placeholder `"UNDETERMINED ID"` |

- `A2AServerRoutes.invokeJSONRPCHandler` (`:406-407`) answers `IdJsonMappingException` with `e.getId()` as `InvalidRequestError`. It is untouched by this commit.
- The 1.1 path no longer throws `UnsupportedOperationException` (the old `"method": null` route result `InternalError` with `"id": null`).
- Committed evidence: `Phase2CodecTest.selected11NonStringMethodRejectionsCarryTheRequestIdLikeTheFrozenPath` (methods `null`, `{}`, `[..]`, `true`, `5`; versions 1.0 and 1.1; string id only). Four more non-send methods were added to the bad-tenant id test. Mutant mut3 kills it.

## Findings

Severity: **Medium** is a concrete defect or material gap. **Low** is a limited evidence gap or hygiene issue. **Info** needs no action.

### F1: Low: R2 committed evidence covers one id and no route-level test

- The codec test uses the single string id `"req-m"`. Numeric, `null` and missing ids are not in a committed test. My probe shows identical and sensible results across 1.0/1.1/0.3, so this is an evidence gap, not a defect.
- No route-level test sends a non-string `method` to `A2AServerRoutes` on selected 1.1. The echo is established by code reading (`:406-407`) plus the codec test, not by a test over the route.
- Fix direction (not required): parameterise the codec test over ids `7`, `"s"`, and a route test case for `"method": null` on 1.1.

### F2: Low: the handler-level N1 test does not pin the failure reason or cover remote delivery

- `aCoveredCommitPublishedAfterTheStartingGenerationWasRead…` asserts the message "call GetTask to resynchronize", which is shared with other recovery failures (buffer overflow, gap, stranded). The reason is pinned only at unit level (`failureReason() == COVERED_WITHOUT_SNAPSHOT`).
- The handler test delivers the late batch locally only (`deliverCommittedBatch(..., false)`). Remote delivery is covered at unit level only.
- The R4 gap from the fix-2 review remains: agent-run and main-queue cleanup after an N1 close are not asserted (same cleanup path as a cancelled consumer; the input is already committed and the agent keeps running, as documented).
- The mutants show the paths are discriminated, so I rate this Low.

### F3: Info: conservative false positive, opt-in only

- With recovery enabled, a commit made before the tap and distributed after it (or a slow publisher's commit, the T13 case) closes the sender's stream with the resync failure. The send is not undone, so the client must call `GetTask`.
- It is the user's N1 decision. It is documented in `configuration.md` and the class javadoc, and it is covered by tests. It only applies when `a2a.publication-recovery.enabled=true`.
- It trades availability for no silent loss. The alternative (a replacement snapshot on a send stream) was recorded in an earlier handoff as a later protocol decision.

### F4: Info: pre-existing id placeholder

- A request with a `null` or missing id **and** a missing `method` gets the placeholder id `"UNDETERMINED ID"` rather than a JSON `null` id. It is the same in 1.0, 1.1 and 0.3. It is not caused by this diff. I list it for completeness only.

### F5: Info: new `unseenThrough` field

- It is a plain field under the observation lock. No visibility concern. It is set only by `completeWithoutSnapshot`.

## Android SSE cancellation failure (preserved, **not fixed, cause unknown**)

- Evidence from the implementer's session: `AndroidA2AHttpClientSSETest.cancellableSseDisconnectsHeldOpenPeer:213` ("SSE completion should be cancelled ==> expected: true but was: false") failed once, in `/tmp/p3-t17-fix3-logs/grpcrest-1-android-failure.log` (25 run, 1 failure; downstream modules skipped). The identical rerun in `grpcrest-rerun.log` passed 25/0. I read both logs.
- `extras/http-client-android` is not changed by `37aa8600..5c0369db` (the diff has no `extras` paths). It entered the reactor through `-am`.
- In my own run, `AndroidA2AHttpClientSSETest` passed 25/0. **One pass does not establish the cause or a fix.** It is consistent with an intermittent failure and proves nothing more. I did not investigate it, as the user deferred that. I do not call it fixed.
- The test is outside T17's scope, but it can make a full-reactor acceptance run intermittently red. The full review and the developer should track it.

## Retained limits (none blocks this targeted review; all stay open for the full review)

| ID | Status |
|---|---|
| F1.1 wire-level finite demand | Retained. The reference `SseResponseWriter` requests `Long.MAX_VALUE`. Core-level T12 tests and in-process subscribers carry the evidence. |
| CDI + JPA enabled path | Not tested. Extras (JPA, replicated queue manager, Kafka, OpenTelemetry) were not run. Recovery is exercised end to end only for JSON-RPC with the in-memory `AtomicTaskStore`. REST and gRPC run with recovery off. |
| N3 artifact-only `SendInput` has no stream to watch | Retained Info. Documented. |
| N4 `@PreDestroy` tested by direct call | Retained Low. |
| N6 sleeps | Retained Low, unchanged (push negative control 1000 ms, `sendCollected` 400 ms settle, `Phase3OldObserver.awaitFrames`, `Phase3JSONRPCPublicClientTest` ~300 ms and `awaitQuiescent`). |
| N7 rejected opening input may already have run the executor | Retained Info. |
| N8 tenant ownership case not modelled | Retained Low. |
| N9 input message role not validated | Retained for the developer. No code change. |
| Default off | With the default, a lost final publication is not detected on any stream. Deployments must enable it. It needs an `AtomicTaskStore`. |
| Android SSE failure | Unexplained, preserved above. |

## What was and was not checked

**Checked:**

- The full `git diff 37aa8600 5c0369db` for production, tests and docs.
- The surrounding code: `PublicationReconciler.Observation` (`admit`, `complete`, `fail`, `drainLocked`), `DefaultRequestHandler.sendOrdinaryStream`, `observeSendStream` and `commitExistingTaskInput`, `MainEventBusProcessor.deliverCommittedBatch`, `JSONRPCUtils` 1.0/1.1 paths, `getAndValidateId` and `getIdIfPossible`, and `A2AServerRoutes` error mapping.
- Source hashes against `progress-T17.md`.
- The eight commands in the table above.
- Six mutation probes and a 72-case id probe, all in `/tmp`.
- Both Android logs.

**Not checked or not possible:**

- The full reactor, extras, Docker/Kafka/JPA, and a CDI boot with the JPA store.
- REST/gRPC with recovery enabled.
- A real HTTP request carrying a non-string `method` (see F1).
- The reason for the Android failure.
- The reviewer's own model identity is not observable.

## Final disposition

**Targeted review clean: no blocking finding, no changes required for R1 or R2.** The two Low findings (F1, F2) and the Info items may be handled in the developer's next session or the full review. T17 is **not accepted**. A distinct, fresh, full independent review must follow. It should keep the retained limits and the unexplained Android failure visible. T18/T19/T20 are not started, and nothing was committed or pushed.
