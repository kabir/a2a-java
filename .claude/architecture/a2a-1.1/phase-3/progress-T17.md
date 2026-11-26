# P3-T17 progress — JSON-RPC production end-to-end mutation slice

- Base commit: `d869e957b310e2585409ac3b3adb166d7823d36c`; branch `work/p3-t17`; worktree `/home/agentuser/a2a-java`.
- Assignment start: 2026-11-26 14:35:00 UTC. Absolute backstop: **2026-11-26 16:35:00 UTC** (not reset by the resumed
  or fresh sessions). `date -u` is authoritative.
- Status: **candidate, NOT accepted.** A separate fresh-context Bob review is required. No shared status row
  (`codex-progress.md`, `bob-progress.md`, task-list) was changed, by instruction. T18/T19 not started.
- Model: requested = Bob's configured model (user authorisation replaces Luna/Sol, implementation and review).
  Observed = identifier unavailable; no telemetry observed. Sol not claimed, Astra not used.
- Environment: Java 17, Maven 3.10.2, online (`-o` fails, see failures), `-Dmaven.repo.local=/tmp/p3-t17-m2`, no root `test.log`,
  no Serena/MCP, no nested agents, no push.

## Prior-session failures (recorded, not hidden)

1. First Bob implementation session (`/tmp/p3-t17-bob-implementation.jsonl`) ended without a final message after
   partially writing the untracked `Phase3ClientGenerationTest.java`. The partial file was retained and completed, not reset.
2. Resumed session (`/tmp/p3-t17-bob-continuation-1.jsonl`) stalled after one successful inspection; no code produced.
3. This fresh-context session (`/tmp/p3-t17-bob-fresh-2.jsonl`) did the implementation below. The Maven `test-compile`
   warm-up (`/tmp/p3-t17-warm-test-compile.log`, exit 0, `-DskipTests`) is **not** acceptance evidence.

## Production changes

- `JSONRPCTransport`: no-cursor `observe` (1.1 only; a cursor or selected 1.0 throws `UnsupportedOperationException`
  before any request); `streamInput` refactored onto the shared `startObservation` helper.
- `JSONRPCHandler`: removed the T17 guards for `timelineLength`, `ifGenerationMatch` and artifacts on `onSendInput`,
  `onSendInputStream`, `onGetTask`, `onListTasks`. Kept delivery-version rejection, `currentGeneration` rejection, cursor
  rejection and all 1.0 gates. **Bug fixed:** `onGetTask`/`onListTasks` rebuilt `TaskQueryParams`/`ListTasksParams` without
  `timelineLength`; it is now forwarded (selected-1.0 `timelineLength` still rejected via `rejectLegacyTimeline`).
- `A2AServerRoutes`: streaming-input route guard is delivery-version only.
- `DefaultRequestHandler.initConfig`: wires the T14 `PublicationReconciler.forTaskStore(...)` when the store is an
  `AtomicTaskStore` (affects all transports). Optional keys `a2a.publication-recovery.interval.millis`,
  `.read-deadline.millis`, `.gap-deadline.millis`; defaults 5 s / 5 s / 10 s (worst case about 30 s).
- **Paired hooks (T13 diagnosis):** no new request-thread commit/publication path was added. The handler forwards to existing
  `DefaultRequestHandler` paths, which already use `MainEventBusProcessor.commitAndPublish`. The lost-publication test commits
  straight to the store as a test-only hook (no production fault hook).

## Tests

- `Phase3ClientGenerationTest` (client/transport/jsonrpc): 7, controlled `HttpServer` peer.
- `Phase3JSONRPCPublicClientTest` (client/base): 11, real `JSONRPCTransport` + controlled peer with ingress counted.
- `JSONRPCHandlerTest`: migrated and extended, 80 run / 1 pre-existing `@Disabled` skip.
- `Phase2BoundaryTest` (reference/jsonrpc): 5.
- Shared fixture `Phase3MutationScenarios` + `Phase3TestProfile` (tests/server-common); extensions to `AgentExecutorProducer`
  (`phase3:` branches), `AgentCardProducer` (`test.agent.user-artifacts`), `TestUtilsBean` (`pushConfigCount`),
  `Public03ClientSupport` (`sendToTask`).
- `Phase3JSONRPCMutationTest` (tests/multiversion/jsonrpc): 12 `@QuarkusTest` scenarios: guard race (one accepted, one typed
  `TaskGenerationMismatchError`, ingress 2); message plus multiple artifacts / artifact-only / USER origin hidden from 1.0;
  malformed inputs with no partial mutation; direct-Message no-task branch (unary + streaming, `returnImmediately` both,
  inline push config); opening message plus artifacts; 1.0 writer / 1.1 subscriber; 0.3 writer / 1.1 subscriber;
  existing-task direct reply; terminal full proposal; metadata-only status; lost final publication then explicit `GetTask`
  (T14 gap failure, 1 ingress, no mutation); unary / streaming-observed / no-cursor-subscribe reduce identical stamps.

## Evidence (final candidate; logs in `/tmp`)

| Check | Result | Log | Time |
|---|---|---|---|
| Plan cmd 1 (`-pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest ... test`) | exit 0. client/transport/jsonrpc 26 (Phase2PublicClientTest 19, Phase3ClientGenerationTest 7); JSONRPCHandlerTest 80 run, 0 fail, 1 pre-existing skip | `p3-t17-final-cmd1b.log` | 20 s |
| Plan cmd 2 (`-pl tests/multiversion/jsonrpc -am -Dtest=Phase3JSONRPCMutationTest ... verify`) | exit 0, 12 run, 0 fail, 0 skip | `p3-t17-final-cmd2b.log`; frozen re-run `p3-t17-final-cmd2d.log` (see handoff) | 36 s |
| client/base | 54 total: Phase3JSONRPCPublicClientTest 11, ExistingOrdinaryEntryPointReductionTest 31, Phase2PublicClientTest 12 | `p3-t17-final-clientbase.log` | 27 s |
| Full `tests/multiversion/jsonrpc -am verify` | BUILD SUCCESS, 151 module tests (139 existing + 12 new) | `p3-t17-final-full.log` | 4 min 2 s |
| server-common `-am test` | 984 pass | `p3-t17-sc-1.log` | |
| reference/jsonrpc `Phase2BoundaryTest` | 5 pass | `p3-t17-ref-1.log` | |
| rest + grpc multiversion `verify` (regression) | BUILD SUCCESS; REST 146 (18 skipped), gRPC 131 (22 skipped); skips not compared to base | `p3-t17-restgrpc-1.log` | |

Note: the full multiversion run finished 15:40:13Z, before the final production-file timestamps (restored byte-identical from
`/tmp/p3t17-final-prod/` after the RED run, hashes unchanged). The frozen-hash re-run of both plan commands is recorded in the
handoff.

## RED / preserved failures

- `p3-t17-red-1-offline-preserved.log`: `-o` environment failure (online required).
- `p3-t17-red-2-preserved.log`: unit RED at base production: `Phase3ClientGenerationTest` 5 errors, `JSONRPCHandlerTest` 2 failures.
- `p3-t17-red-3-e2e-preserved.log`: e2e RED with base `JSONRPCHandler`/`A2AServerRoutes`/`DefaultRequestHandler`/`JSONRPCTransport`:
  12 run, 1 failure + 3 errors (`lostFinalPublication`, `unaryStreamingAndNoCursorSubscribe...`, `twoPublicClientsRace...`,
  `metadataOnlyStatus...`). The other 8 pass at base production because core and handler already support those paths.
  Production restored; 12/12 again.
- Intermediate failures `p3-t17-mv-1..7.log`, `p3-t17-pubclient-1.log`: test/compile corrections, each fixed in one or two
  attempts. **No defect reached three consecutive substantive attempts without progress; the diagnosis stop was not triggered.**

## Source hashes

`/tmp/p3-t17-sha256.txt` (15 files) is reproduced in the handoff.

## Limitations and next actions

See `handoffs/P3-T17.md`. Next: separate fresh-context Bob review; corrections if any; then (not before acceptance) T18/T19.

---

# Correction session (review `reviews/P3-T17-review.md`, candidate `c572b04b`)

- Window: start **2026-11-26 16:13 UTC**, initial deadline 18:13 UTC; end 2026-11-26 17:30 UTC (about 1 h 17 min). The
  authorised second two-hour window was **not used**. `date -u` authoritative.
- Branch `work/p3-t17`, base `d869e957`, candidate `c572b04b`. Only preexisting untracked file was the review, kept byte-unchanged.
- Model: requested = Bob's configured model (user authorisation in place of Luna/Sol). Observed = identifier unavailable,
  no telemetry; Sol not claimed, Astra not used. No nested agents, no Serena/MCP, no push, no shared status row edited.
- Maven: online, private repo `/tmp/p3-t17-correction-m2` only; every command `timeout -k 30 <=1200`, detached with `setsid`,
  serial reactors, log plus real exit code in `/tmp/p3-t17-correction-*.log/.exit`. No root `test.log`.
- Provenance note (review F11): the earlier detached full reactor `p3-t17-final-full2.log` was launched without a timeout/exit
  file and was externally bounded by the coordinator's watchdog; it and the earlier overlapping builds sharing `target/` are
  **not** final evidence. All final evidence below is from this correction session's serial, bounded runs.
- Status: **corrected candidate, NOT accepted.** F2 is open (needs a user decision). A targeted affected-diff re-review and then a
  full independent review follow. T18/T19 not started.

## Two real production defects found by the new tests (both minimal)

1. `A2AServerRoutes.invokeJSONRPCHandler`: a pre-stream `A2AError` (route-level `authorizeTaskAccess` before the stream is
   created, e.g. unknown task) was answered as plain JSON with `"id": null`. The strict 1.1 SSE envelope reader of the
   public client then failed with "response id is missing or null" and the typed `TaskNotFoundError` was lost. Fix: keep the parsed
   request outside the `try` and answer `new A2AErrorResponse(request.getId(), e)`. Wire shape (plain JSON, HTTP 200) unchanged, id now echoed.
2. `spec-grpc` `JSONRPCUtils.parseRequestBody(..., "1.1")`: `ProtoUtils.FromProto.sendInputParams` throws `InvalidParamsError`
   without an id for blank/missing `messageId`; the server answered -32602 with `id: null` (same client-side loss). Fix: rethrow
   as `InvalidParamsJsonMappingException(message, id)`, exactly the pattern used by the legacy parser and `validateVersion10Input`.
RED evidence: `/tmp/p3-t17-correction-it1.log` (before the fixes: streaming unknown-task and blank-messageId cases failed
untyped, probe showed `id:null`); unit RED `/tmp/p3-t17-correction-red-unit-preserved.log` (production reverted, new
`Phase2CodecTest.selected11BlankMessageIdIsRejectedAsInvalidParamsCarryingTheRequestId` fails with raw `InvalidParamsError`; production restored, sha verified, test green afterwards).

## Finding-by-finding response

| Finding | Technical assessment | Disposition / evidence |
|---|---|---|
| F1.1 old observers, finite filtered demand | Well-founded (plan bullet 4). The reference HTTP edge requests `Long.MAX_VALUE`, so finite demand cannot be produced over HTTP | New `Phase3OldObserver` (one-at-a-time demand subscriber). 1.0: `RequestHandler.onSubscribeToTask` + `VersionedOutputProjector.projectStream`; 0.3: `Convert_v0_3_To10RequestHandler.onResubscribeToTask`; both in-process against the real server stack. 1.0/0.3 reads (get/list) are real public-client HTTP. Progress, 0.3/1.0 projection, finality and USER-artifact hiding asserted. `Phase3MutationScenarios` (older-writer/observer scenarios) |
| F1.2 capability error | Well-founded | `Phase3JSONRPCNoUserArtifactsProfile`/`Test` (4 tests): effective card without `userArtifacts`, typed `InvalidParamsError` "User artifacts are not enabled", no task/config persisted; the agent may already have started (opening input is admitted atomically with the first agent event) — asserted nothing persisted for any started execution |
| F1.3 ID errors | Well-founded; exposed defect 2 | Blank `messageId` typed `InvalidParamsError`, id echoed, unary + streaming; unit test in `Phase2CodecTest` |
| F1.4 tenant/context attribution | Well-founded | Tenant `phase1-2` asserted typed `VersionNotSupportedError` "Protocol version '1.1' is not provisioned. Supported versions: [1.0]"; wrong context / unknown task typed and attributed; full no-partial effects checked on **both** target tasks. Exposed defect 1 |
| F1.5 message role | **Not adopted as a validation.** Neither `architecture.md` nor `spec-delta.md` requires a server-side check of the *input message* role; the plan's "role" is the artifact origin role (decision G6), which is covered (agentOrigin rejected, USER origin hidden from 1.0/0.3). Inventing a ROLE_USER check or pinning "ROLE_AGENT input accepted" would create an unrequired contract. No spec conflict, no escalation | Recorded only |
| F2 global default-on reconciler | Material policy decision | **Not touched** (no change to provisioning/policy). See "F2 open questions" |
| F3 direct Message | Well-founded (cleanup was vacuous: `getActiveChildQueueCount` is -1 for a missing queue) | Opening Message **plus artifacts** direct-reply over HTTP (blank contextId required: only a pure opening message may carry provisional empty-ID artifacts); `Phase3Spy`/`Phase3SpyQueueManager` count queue created/closed/replicated/published and task-store totalSize before/after; cleanup is non-vacuous (created == closed, no -1 treated as released); unary + streaming × both `returnImmediately` |
| F4 streaming guards | Well-founded | Streaming guarded sends (race: one accepted, one typed `TaskGenerationMismatchError`, ingress counted), artifact-only (answered with a snapshot, SYNCHRONIZED, `task.generation == head`), message+artifacts, `timelineLength` through the actual server. Observed contracts pinned: streaming send to an existing task with `phase3:noop` emits **no** frames (so `phase3:work` is used); artifact-only adds no timeline entry so the `timelineLength(2)` window equals the last two stored entries |
| F5 null `throughGeneration` | Well-founded | Streaming message send to an existing task is `ViewSync.NO_BASELINE`, `throughGeneration == null`, a partial task (`generation()==0`) with committed stamps; asserted explicitly with the reason; unary requires `task.generation == through == stored head` |
| F6 inline config ordering, projected terminal | Well-founded | `Phase3Webhook` loopback receiver (profile config enables private targets/http): notification delivered only after the inline config; body is the 1.1 `{"task":...}` wrapper; the notified state contains final artifact `p3-final`; terminal state accepted as Task or status update; a no-config control delivers nothing. 1.0/0.3 reads of the projected terminal proposal |
| F7 one-directional mixed version | Well-founded | 1.0 writer / 0.3 writer read back through 1.0 and 0.3 (get/list/subscribe); USER artifacts hidden on all old reads; 1.1 sees all |
| F8 malformed oracle | Well-founded | Typed error asserted for every case; `other` task rechecked after raw malformed cases; raw malformed responses assert the echoed id |
| F9 controlled-peer public client | Well-founded | Guarded rejection via `sendInput`, `sendInputObserved`, `sendMessage`; malformed known payload parameterised over unary, streaming send, subscribe with a valid-generation control (decoder message "Malformed known protocol content", bad value named; it cannot name the JSON field); typed error envelopes for all three routes; unknown **timeline** variant (INCOMPLETE, `UNKNOWN_SKIPPED`, no gap invented); live held-open uncertain stream cancelled. Interceptor/wrapper behaviour of `observe` not added: production calls `applyInterceptors` (review itself says so) — not needed |
| F10 lost final publication | Well-founded | Asserts `InternalError` with the documented GetTask resync message, no terminal invented, no extra mutation (one ingress for the explicit `GetTask`). Test-profile bounds are 200/1000/600 ms; shipped 5/5/10 s unchanged |
| F11 provenance/hygiene | Accepted | See provenance note above. `java.time.Duration`/`Optional` fully qualified names in `DefaultRequestHandler:465-467` are part of the F2 reconciler wiring block and are left untouched until the policy decision; config-key docs belong to T20 |
| F12 info | Accepted | No action. The new tests add true RED for the two defects above; the remaining scenarios remain regression/characterisation coverage |

### F2 open questions (exact; nothing implemented)

1. Keep default-on, make it opt-in via a property, default-on with explicit opt-out, or JSON-RPC-reference-only?
2. Streaming **send** paths have no reconciler attach: `onMessageSendStream` (about line 1209) / `sendOrdinaryStream` (about
   1215) and `onSendInputStream` (about 932); only `onSubscribeToTask` attaches (production call about 1565). Must these get
   the same lost-publication bound, or is that limit documented (T14 listed it as retained)? **Policy-dependent: not implemented.**
   Precise follow-up once decided: attach in the streaming-send paths behind the same policy gate and extend the lost-publication scenario with a streaming-send stream.
3. If the wiring stays: tests for default bounds, each of the three keys, an invalid value (`NumberFormatException` in
   `@PostConstruct`), `@PreDestroy` close, and a non-`AtomicTaskStore`/decorated store (silently unprotected).
4. Is a CDI + JPA wiring test needed (JPA is only covered by builder-built handlers today)?

## Commands and results (final, serial, bounded; all on the final sources)

| Command | Exit | Result | Log |
|---|---|---|---|
| Plan cmd 1 (`-pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest -Dsurefire.failIfNoSpecifiedTests=false test`) | 0 | client/transport/jsonrpc 26 (19+7); `JSONRPCHandlerTest` 80 run, 0 fail, 1 pre-existing `@Disabled` skip (same as base/baseline) | `p3-t17-correction-plan1` |
| Plan cmd 2 (`-pl tests/multiversion/jsonrpc -am -Dtest=Phase3JSONRPCMutationTest,Phase3JSONRPCNoUserArtifactsTest ... verify`; plan command extended with the new class) | 0 | 24/24: `Phase3JSONRPCMutationTest` 20 (12 before), `Phase3JSONRPCNoUserArtifactsTest` 4; 0 skips | `p3-t17-correction-plan2` |
| `-pl client/base,reference/jsonrpc -am -Dtest=Phase3JSONRPCPublicClientTest,ExistingOrdinaryEntryPointReductionTest,Phase2BoundaryTest test` | 0 | client/base 49 (18 + 31; 11 before), `Phase2BoundaryTest` 5; 0 skips | `p3-t17-correction-cbfinal` |
| `-pl spec-grpc,jsonrpc-common,tests/multiversion/rest,tests/multiversion/grpc -am verify` (JSONRPCUtils changed; REST/gRPC regression) | 0 | BUILD SUCCESS; `Phase2CodecTest` 7 (new test included); server-common 984; REST 146 (18 skipped), gRPC 131 (22 skipped) — identical to baseline counts; skips are the JSON-RPC-specific assumption/`@Disabled` ones | `p3-t17-correction-grpcrest` |
| `-pl tests/multiversion/jsonrpc -am verify` (full module) | 0 | BUILD SUCCESS; module 163 = 139 existing + 20 + 4, 0 fail/err/skip in the module; `JSONRPCHandlerTest` 80/1 pre-existing skip; server-common 984 | `p3-t17-correction-jsonrpcmod` |
| Unit RED (production `JSONRPCUtils` reverted) | 1 (expected) | `Phase2CodecTest` 7 run, 1 failure | `p3-t17-correction-red-unit-preserved` |

Final-source integrity: `sha256sum` list in `/tmp/p3-t17-correction-sha256.txt` (reproduced in the handoff). The last edit to a
production file (the `JSONRPCUtils` revert/re-apply for the RED probe) was verified byte-identical afterwards
(`sha256sum -c`, OK) and was not rebuilt separately; the `grpcrest` and `jsonrpcmod` runs preceded it on the same bytes.

## Failed substantive attempts during the correction (not hidden)

| Run | Failure | Cause / fix |
|---|---|---|
| it1 | 9 failures + 2 errors | test-design errors (opening-message shape, window assertion, streaming `task()` assumption) **plus the two real null-id defects** (fixed in production) |
| it2 | ambiguity error | probe subclass; probe removed |
| it3 | 7 failures | `listTasks` defaults omit artifacts/history; webhook validator requires https and blocks private targets with the real HTTP client; noop streaming emits no frames; streaming `task()` is partial |
| it4 | 2 failures | async `phase3:agent-artifact` race (poll stored task first); webhook terminal state may be a status event |
| it6 | 1 failure | tenant rejection type is `VersionNotSupportedError` |
| cb | 4 failures | unary client reports errors by throwing synchronously (`A2AClientException`); attribution wording |

No defect reached three consecutive substantive attempts without progress; the diagnosis stop was not triggered. Earlier
overlapping or timed-out Maven runs are intermediate only.

## Source hashes after correction

`/tmp/p3-t17-correction-sha256.txt` (changed and new Java files); reproduced in `handoffs/P3-T17.md`.

---

# F2 correction session (opt-in publication recovery) — base `5d8deaf2b9df33cc3f111edf391e93af89e989ba`

- Windows (UTC, `date -u` authoritative): original correction session start **2026-11-26 17:27**, initial absolute deadline
  **19:27**. The previous Bob session ended unexpectedly at about 18:01 without a handoff (its transcript
  `/tmp/p3-t17-bob-f2.jsonl` is retained) while a detached serial verification chain ran (`chain.start` 17:52:06,
  `chain.end` 18:08:30). This resumed session started about 18:09 and finished inside the initial window; the user-authorised
  second two-hour window was **not used**. Nothing was reset or discarded: the working tree contents found were kept.
- Model: requested = Bob's configured model (user authorisation in place of Luna/Sol). Observed = identifier unavailable, no
  telemetry; Sol not claimed, Astra not used. No nested agents, Serena/MCP, push, or shared task-list/status change.
- Maven: online, private repository `/tmp/p3-t17-f2-m2`, one reactor at a time, every command `timeout -k 30 <=900`, log plus
  real exit code under `/tmp/p3-t17-f2-logs/<name>.log/.exit`. No root `test.log`.

## Exact user decision (recorded verbatim in substance)

"User explicitly chose opt-in via configuration, default disabled, for the reconciler." This resolves F2 of
`reviews/P3-T17-review.md` (kept unchanged): the global default-on wiring that the first candidate shipped is **replaced** by
opt-in. The F2 open questions in the section above are answered as: (1) opt-in property, default `false`; (2) ordinary streaming
send paths get the same bound when enabled; (3) provisioning tests added; (4) CDI+JPA test **not** added (see limits).

## What changed (6 modified, 2 new files; nothing outside server-common, the shared fixture and docs)

- `DefaultRequestHandler.initConfig`: the reconciler is created only when `a2a.publication-recovery.enabled` is `true` **and**
  the task store is an `AtomicTaskStore`. Absent key = off. Only `true`/`false` (case-insensitive, trimmed) are accepted; any
  other value throws `IllegalArgumentException` from initialisation. With a non-atomic store the handler logs a warning and
  installs nothing. The three bound keys (`interval`, `read-deadline`, `gap-deadline`, positive millis) are read only when
  enabled; a bad bound still fails initialisation. A builder-installed reconciler is kept whatever the property says.
  `@PreDestroy` closing is unchanged. Package-private accessors `publicationReconciler()` / `publicationRecoveryLimits()` for tests.
- New `observeSendStream` in `DefaultRequestHandler`, called from `sendOrdinaryStream` (so `onMessageSendStream` and the
  message-bearing `onSendInputStream`, 1.1 and legacy projections): attaches the child queue to the reconciler before the agent
  starts (delivery gate first, then the authoritative generation read `G`), detaches when the agent never starts, on a failed
  consumer, on a failed setup and on client cancel. A new task has watermark 0. No snapshot frame is added to the stream.
  Artifact-only `SendInput` has no stream and is not observed. `onSubscribeToTask` is unchanged.
- `a2a-defaults.properties`: `a2a.publication-recovery.enabled=false` with comment. `docs/content/dev/configuration.md`
  (dev only): new "Publication Recovery (opt-in)" section with default, bound, invalid values, store requirement and limits.
- Tests: `PublicationRecoveryProvisioningTest` (10), `StreamingSendPublicationRecoveryTest` (33); shared fixture
  `Phase3TestProfile` now enables the property explicitly with 200/1000/600 ms bounds, `AgentExecutorProducer` `phase3:hold`
  and `PHASE3_HOLD`, `Phase3MutationScenarios` new streaming-send lost-publication scenario (`Phase3JSONRPCMutationTest` 21).

## Assessment against the F2 checklist

| Item | Evidence |
|---|---|
| Default disabled | `shippedDefaultsDisablePublicationRecovery` loads the real `META-INF/a2a-defaults.properties` (`false`), absent key and `null` Optional also off; the bounds are not parsed when disabled |
| Enabled / invalid / bounds | enabled for an atomic store gives `Limits.DEFAULTS` (5/5/10 s, 256 / 4 MiB); `TRUE`, ` True ` accepted; `yes`, `1`, `ture`, empty, `enabled` rejected; each key overrides only itself; `abc`, `1.5`, empty give `NumberFormatException`, `0`, `-1` give `IllegalArgumentException` |
| Lifecycle | `preDestroy…` closes, is idempotent, a closed reconciler rejects new observations |
| Unsupported store | decorated `LegacyOnlyStore` installs nothing and does not fail |
| Subscribed + streaming sends, 1.1 and legacy | Subscribe unchanged (existing T14/T17 tests). `StreamingSendPublicationRecoveryTest` over four variants (new/existing task x message send / SendInput message) plus 1.0/0.3 projections: lost final closes with `InternalError` and GetTask returns the committed state |
| Cleanup, ordering | healthy completion, direct message, cancel, agent failure, closed reconciler, missing task, rejected input all release the observation and queue; racing commit before/after `G` discarded/delivered exactly once; new task delivered from generation 1 |
| Old transport protection, T13 | `theInputCommitOfAnObservedSendKeepsItsReserveAndReleasePaired`, `aRejectedInputToAnObservedSendLeavesNoHoldAndNoObservation`; REST/gRPC/compat suites green with the property off |
| Real JSON-RPC lost final then explicit `GetTask` | subscribe scenario and the new `lostFinalPublicationOnAStreamingSendCloses…` run in `Phase3JSONRPCMutationTest` (21/21): closed within bound, `InternalError` resync message, no invented terminal, child queue released, one ingress for the GetTask, stored task unchanged |
| No replay / cursor / Phase 4-5 | none added; recovery never mutates, resends or invents an event; `observe` cursor still unsupported |

## Commands, results, chronology (all exit code from `.exit` files; times UTC)

| # | Check | Exit | Result | Log |
|---|---|---|---|---|
| 1 | `-pl server-common -am compile` warm-up (17:31) | 0 | not acceptance evidence | `compile-1` |
| 2 | `PublicationRecoveryProvisioningTest` `-pl server-common -am -Dtest=… test` | 1, 1, 0 | RED/fix: test-design failures (10 run, 1 failure; TaskNotFound where closed-reconciler ISE expected) fixed in the test, then 10/10 | `prov-1`, `prov-2`, `prov-3` |
| 3 | `StreamingSendPublicationRecoveryTest` | 1, 0 | 33 run, 1 error (agent-failure test timing) then 33/33, 0 skip | `send-1`, `send-2` |
| 4 | Mutation probe: attach removed in `DefaultRequestHandler` | 1 (expected) | 33 run, 19 failures | `mutation-1` |
| 5 | e2e `Phase3JSONRPCMutationTest` | 1 (compile of an interrupted edit), 0 | then 25 run (21 + `Phase3JSONRPCNoUserArtifactsTest` 4), 0 fail/skip | `e2e-1`, `e2e-2` |
| 6 | e2e mutation probe: attach removed | 1 (expected) | new streaming-send scenario fails ("stream must be closed within the recovery bound"); production restored byte-identical to `DRH.bak2` at 17:49:09 | `e2e-mutation` |
| 7 | server-common `-am test` full (17:49:21–17:51:07) | 0 | 1027 run (984 + 10 + 33), 0 fail/err; skips only the 3 upstream `VertxA2AHttpClientSSETest` | `sc-full` |
| 8 | Plan cmd 1 (`-pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest … test`) | 0 | client/transport/jsonrpc 26, `JSONRPCHandlerTest` 80 run / 1 pre-existing `@Disabled` skip | `plan1` |
| 9 | `-pl client/base,reference/jsonrpc -am -Dtest=Phase3JSONRPCPublicClientTest,ExistingOrdinaryEntryPointReductionTest,Phase2BoundaryTest test` | 0 | client/base 49, `Phase2BoundaryTest` 5, 0 skips | `cbase` |
| 10 | `-pl …,tests/multiversion/rest,tests/multiversion/grpc -am verify` (17:52–17:58:56, 6 min 11 s) | 0 | BUILD SUCCESS; REST 146 (18 skipped), gRPC 131 (22 skipped), compat-0.3 200 (27 skipped) and 53 (11 skipped): the JSON-RPC-specific assumption / `@Disabled` skips, same counts as the previous correction baseline | `grpcrest` |
| 11 | `-pl tests/multiversion/jsonrpc -am verify` (17:58:57–18:08:30, 9 min 33 s) | 0 | BUILD SUCCESS; module 164 = 139 + 21 + 4 (+ client `Phase3JSONRPCPublicClientTest` 18 in the reactor); only skips are the 3 upstream Vert.x tests and the pre-existing `JSONRPCHandlerTest` one | `jsonrpcmod` |
| 12 | Gate mutation probe (this session, 18:10): `publicationRecoveryEnabled()` replaced by `true` in `initConfig`; `-pl server-common -am -Dtest=PublicationRecoveryProvisioningTest test` | 1 (expected) | 10 run, 3 failures + 1 error (default, absent-key/`null` provider and invalid-value tests fail). Production restored from `DRH.bak3`, `sha256sum` equal to the verified hash. A first attempt did not apply the mutation (`python3` missing) and is kept as an unmutated re-run: exit 0, 10/10 | `gate-mutation`, `gate-noop-unmutated` |

Every verification run in rows 7-11 started after the last source change (all candidate file mtimes are 17:49:09 or
earlier; `find -newer chain.start` over all `*.java`, `*.properties`, `*.md` outside `target` returned nothing). The
`DefaultRequestHandler` copy used for the e2e probe is byte-identical to `DRH.bak`, `DRH.bak2` and `DRH.bak3`.
The detached chain completed after the previous session ended; this session only read its logs.
`Phase3MutationScenarios.corrupt` is the preserved broken intermediate edit that caused the first e2e compile failure.

## Source hashes (sha256; `/tmp/p3-t17-f2-logs/sha256.txt`)

```
e726745e05768374a328eaeb92e1a299dd18d0059243788d04609e2c75b7e758  docs/content/dev/configuration.md
f2c1b21da16aab9e4ac492531d13794b85da80d0937b620802de4e895b6ad945  server-common/src/main/java/org/a2aproject/sdk/server/requesthandlers/DefaultRequestHandler.java
b0a314ad7dfef992809477b201b178619c755c3b95aa3fa23a3f910c6f2dd157  server-common/src/main/resources/META-INF/a2a-defaults.properties
219bcfb7f7ff834dc72d82269c7d26aca5682fd705eb57f4b4565a0fcd7e95e7  server-common/src/test/java/org/a2aproject/sdk/server/events/StreamingSendPublicationRecoveryTest.java
0e01ec254a6a495d883342b66fa2c61e6ebb32f4e6ea684d15ee69cbc15be2b4  server-common/src/test/java/org/a2aproject/sdk/server/requesthandlers/PublicationRecoveryProvisioningTest.java
bec0a2602413dddbed640e27d2c7820df5a43a77a466daa82977b36b37c5ce4a  tests/server-common/src/test/java/org/a2aproject/sdk/server/apps/common/AgentExecutorProducer.java
414fa331fd663da7134fdf70f30e55c03965c98c00d2b11fab3c3635f2a81833  tests/server-common/src/test/java/org/a2aproject/sdk/server/apps/common/Phase3MutationScenarios.java
04cf749e7ceee84523856ff3cb139ca42e5e1bede1cb1424be8bd68834a5f884  tests/server-common/src/test/java/org/a2aproject/sdk/server/apps/common/Phase3TestProfile.java
```

## Failed attempts (not hidden)

`prov-1`/`prov-2` (same test-design defect twice, fixed on the third), `send-1` (timing of the agent-failure test, fixed once),
`e2e-1` (compile error from an interrupted edit of `Phase3MutationScenarios`; the broken copy is preserved), expected mutation
failures. No defect reached three consecutive substantive attempts without progress; the diagnosis stop was not triggered.

## Known limits (explicit)

- Disabled by default: with the default, a lost final publication is **not** detected on any stream (subscribe or send); the
  stream may stay open. Deployments must set `a2a.publication-recovery.enabled=true`.
- Needs an `AtomicTaskStore`. A decorated or custom store that is not one is silently (warning only) unprotected.
- **CDI + JPA is incomplete:** no test wires the enabled property through CDI with the JPA task store; JPA is covered only by
  builder-built handlers and the in-memory `AtomicTaskStore`. Extras modules (JPA, replicated queue manager, Kafka, OTel) were
  not run in this session. The enabled CDI path is exercised only with the in-memory store in `Phase3JSONRPCMutationTest`.
- Only JSON-RPC runs the enabled lost-publication scenario end to end; REST and gRPC are covered by the shared core path and
  by unchanged suites run with the property off.
- Streaming sends carry no snapshot: an event committed by another writer between attach and the first generation read is
  covered by that read and not repeated on the sender's stream (`GetTask` shows it). Artifact-only `SendInput` is not watched.
- Bounds are about 30 s worst case with defaults; tests use short bounds. Lost publication is simulated by a store-only commit
  in a test hook (no production fault hook). No replay, cursor, long-poll or Phase 4/5/6 behaviour. T18/T19 not started.
- Status: **corrected candidate, NOT accepted.** A fresh targeted affected-diff review, then a full independent final review,
  must run before import.

---

# Targeted-review correction 2 session

Window 2026-11-26 18:47–~19:25 UTC (initial deadline 20:47; second window not used). Branch `work/p3-t17`, base HEAD
`71abc014921417b0b353c7f970be998aa7d4861a`. Model: Bob configured model, user-authorised; observed identifier unavailable; Sol
not claimed; Astra not used. Every Maven command: `mvn -B -Dmaven.repo.local=/tmp/p3-t17-fix2-m2 … -am`, serial, `timeout 1200`,
log + `.exit` in `/tmp/p3-t17-fix2-logs`, no root `test.log`. Finding responses: `handoffs/P3-T17.md` (last section).

## Commands and results

| # | Command | Exit | Result | Log |
|---|---------|------|--------|-----|
| 1 | RED: `-pl spec-grpc -am -Dtest=Phase2CodecTest test` on old `JSONRPCUtils` | 1 | 10 run, 2 failures | `n2-red-codec` |
| 2 | GREEN codec after fix | 0 | 10/0/0/0 | `n2-green-codec` |
| 3 | RED routes: `-pl reference/jsonrpc -am` with `JSONRPCUtils` reverted to HEAD (fixed copy restored afterwards) | 1 | `A2AServerRoutesTest` 41 run, 4 errors | `n2-red-routes` |
| 4 | server-common recovery tests, first run | 1 | 3 test-design failures (fixed in tests) | `n1-n11-green` |
| 5 | same, second run | 0 | Gap 32, StreamingSend 43, Provisioning 10 = 85, 0 fail/skip | `n1-n11-green2` |
| 6 | mutation probes (probe copy, 75 tests each) mutN1 / mut2 / mut3 / mut1 | 1 each (expected) | killed: 2 failures / 4 failures / 4 failures / 1 error | `mutN1`, `mut2`, `mut3`, `mut1` |
| 7 | `-pl spec-grpc,jsonrpc-common -am -Dtest=Phase2CodecTest test` (final) | 0 | `Phase2CodecTest` 10, 0 fail/skip | `v1-codec` |
| 8 | `-pl reference/jsonrpc -am -Dtest=A2AServerRoutesTest,Phase2BoundaryTest test` | 0 | 41 + 5 = 46, 0 skips | `v2-routes` |
| 9 | `-pl server-common -am test` | 0 | server-common 1041 run, 0 fail/err; skips only 3 upstream `VertxA2AHttpClientSSETest` (91/3 in that module); Gap 32, StreamingSend 43, Provisioning 10 | `v3-sc-full` |
| 10 | `-pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest test` | 0 | client 26 (19 + 7); `JSONRPCHandlerTest` 80 run / 1 pre-existing `@Disabled` skip | `v4-plan1` |
| 11 | `-pl client/base,reference/jsonrpc -am -Dtest=Phase3JSONRPCPublicClientTest,ExistingOrdinaryEntryPointReductionTest,Phase2BoundaryTest test` | 0 | client/base 49 (18 + 31), `Phase2BoundaryTest` 5, 0 skips | `v5-cbase` |
| 12 | `-pl tests/multiversion/jsonrpc -am verify` (19:09–19:15) | 0 | BUILD SUCCESS; module 164 = `MultiVersionJSONRPCTest` 75, `_v0_3_Test` 40, `WithAuth` 4, `_v0_3_WithAuth` 4, `WithTaskAuthorization` 7, `Phase3JSONRPCMutationTest` 21, `Phase3JSONRPCNoUserArtifactsTest` 4; 0 fail/skip in module | `v6-jsonrpcmod` |
| 13 | `-pl spec-grpc,jsonrpc-common,tests/multiversion/rest,tests/multiversion/grpc -am verify` (19:15–19:21) | 0 | BUILD SUCCESS; REST 146 (18 skipped), gRPC 131 (22 skipped), compat-0.3 200 (27 skipped) and 53 (11 skipped): same counts as the previous baseline | `v7-grpcrest` |

Steps 7–13 ran after the last source change (the `PublicationReconciler` javadoc note). Extras modules not run.

## Mutation probes

Probe copy `/tmp/p3-t17-fix2-probe` (production tree not mutated). Probe restored byte-identical to production
(`DefaultRequestHandler` sha `e50c4cad…`) before the final run. mutN1: `observeSendStream` calls `complete` instead of
`completeWithoutSnapshot`; mut2/mut3/mut1: the `detach()` calls at the pre-agent failure, publisher-construction failure and
consumer `onError` removed. All killed (see row 6). The first mutation chain died when a tool call was cancelled (`mutN1.exit`
and `mut2.exit` not written by the script; `mut2.exit` reconstructed from the log; incomplete evidence preserved); mut3/mut1
were rerun detached.

## Failed or non-progress attempts (not hidden)

- RED runs 1 and 3 (expected). Three test-design failures in the new N1/N11 tests (run 4), fixed by test changes (generation
  numbers: the status commit on the seeded task produced racing generation 6, not 5, so the "later commit" test asserts ordered
  distinct generations ending at `racing+1`).
- The first N2 route test passed on the old code because it ran as protocol 0.3; fixed by `selectProtocol11()`.
- The editing tools misapplied several edits (extra blank lines, a misplaced javadoc insert); every file was re-read and
  corrected. `diff`, `cmp` and `rsync` are not installed (used `sha256sum`). No defect reached three consecutive substantive
  attempts without progress; the diagnosis checkpoint was not triggered.

## Source hashes (sha256; `/tmp/p3-t17-fix2-logs/sha256.txt`)

```
4da21f98e4dbb6e9f235c6479696f49cc76504eb1926956092e0e8296742deac  docs/content/dev/configuration.md
3784911b02e993abff944fc967daaa8637ef006b92e0c5f81f89726bacdd01c7  reference/jsonrpc/src/test/java/org/a2aproject/sdk/server/apps/quarkus/A2AServerRoutesTest.java
4d1c97b446890d937426835f7e44544046de79636e0608e3d56253b650242186  server-common/src/main/java/org/a2aproject/sdk/server/events/PublicationReconciler.java
e50c4cad5def1182949283c55404d91af75776d24cfb76861ae298ad453c687c  server-common/src/main/java/org/a2aproject/sdk/server/requesthandlers/DefaultRequestHandler.java
d68e3514e5fc96078db1f7629b7baa484b3dadb6af2191a847428bd0c5251d8f  server-common/src/test/java/org/a2aproject/sdk/server/events/PublicationGapRecoveryTest.java
4046a32120ad20b8667e98687d70c3808aab3502180ffe47dfcda44a923710b1  server-common/src/test/java/org/a2aproject/sdk/server/events/StreamingSendPublicationRecoveryTest.java
21748f5338faadbe5c4ed8da757f96cdfb978f55f30d0d02a3271e4b0c7c28d8  spec-grpc/src/main/java/org/a2aproject/sdk/grpc/utils/JSONRPCUtils.java
a792b51759bdc6cef810be6ca401a78d95c2f3848fa46bc168c1bde91eca7160  spec-grpc/src/test/java/org/a2aproject/sdk/grpc/utils/Phase2CodecTest.java
b781f0fc54bd96717cfe11366710cbedebb67008d7c1c00e1d060ffc9eefc9c3  tests/server-common/src/test/java/org/a2aproject/sdk/server/apps/common/Phase3MutationScenarios.java
870d15866e98cca24fb2e38bf2dc5b0330130436f138fe4914f70cb3c8d3c72c  .claude/architecture/a2a-1.1/phase-3/reviews/P3-T17-correction-review.md
```

## Known limits

See `handoffs/P3-T17.md`: unresolved N6 sleeps (push negative control 1000 ms, `sendCollected` 400 ms settle,
`Phase3OldObserver.awaitFrames`, `Phase3JSONRPCPublicClientTest` ~300 ms / `awaitQuiescent`); CDI+JPA, extras and F1.1 wire demand
not exercised; N3/N4/N7/N8 unchanged. Status: **corrected candidate, NOT accepted.** A targeted affected-diff review, then a full
independent final review, must precede T17 acceptance/import. T18/T19 not started.

---

# Fix-2 review correction session (R1, R2; R3/R4 assessed)

Window 2026-11-26 21:19 UTC, original deadline 23:19 (two-hour extension authorised, **not used**); work finished ~21:46. Base HEAD
`37aa86003cd18d7011351f469c1b554d2a74e113`, branch `work/p3-t17`, original task base `d869e957…`. Input
`reviews/P3-T17-fix2-review.md` (untracked at start, preserved byte-identical, committed with the fix; sha256
`b15d0fa4fb8b0eee25118e4f71ad42e6a4120e6ff8caf3286a457d5156cbfcc2`). Model: requested IBM Bob configured model
(user-authorised); observed identifier unavailable; Sol not claimed; Astra not used; no nested agents, Serena, push.
Maven: `mvn -B -ntp -Dmaven.repo.local=/tmp/p3-t17-fix3-m2` (seeded from `/tmp/p3-t17-fix2-m2` third-party cache, `org/a2aproject`
removed), serial, each `timeout -k 30 1200`, logs/.exit/.start/.end in `/tmp/p3-t17-fix3-logs`. No root `test.log`.

## R1 verified independently

Reviewer claim confirmed by a deterministic RED, not only by reading. Root cause: `Observation.admit` returned early for any
`attached && generation <= watermark`, and after `completeWithoutSnapshot(G)` the watermark is `G`, so a committed event with
generation <= G distributed *after* the attach was silently dropped (the stream never carries a snapshot for it).
Pre-tap commits and duplicates: a covered event that was committed before the tap and distributed afterwards is
indistinguishable from one committed after the tap (both reach the child after the tap and are at or below `G`). Duplicates
cannot be mistaken for it: any delivered generation is `> G`, and any arrival `<= G` at a snapshot-less observation is by
definition never delivered, so there is no legitimate `<= G` duplicate. The conservative choice is the user's: close with the
existing resync `InternalError`. Subscriptions keep the discard (snapshot replaces the covered event).

Fix (`PublicationReconciler.Observation`): new field `unseenThrough` (= `G` for `completeWithoutSnapshot`, `NONE` for
`complete`). In `admit`, an attached arrival `<= watermark` is ignored as before, except when `generation <= unseenThrough` and the
observation has not delivered its final event (`terminalSeen`): then `fail(COVERED_WITHOUT_SNAPSHOT)`. No new wire code, no resend,
default-off unchanged, T13 reserve/release untouched (`MainEventBusProcessor` not changed), later-generation duplicates still ignored.
Javadoc/`configuration.md`/builder javadoc updated. Retained limit: a pre-tap commit distributed after the attach also closes the
stream (conservative false positive of the same user decision); a stream that already delivered its final event is left alone.

## Commands and results

| # | Command (all `-Dmaven.repo.local=/tmp/p3-t17-fix3-m2`) | Exit | Result | Log |
|---|---|---|---|---|
| 1 | RED attempt 1 `-pl server-common -am -Dtest=PublicationGapRecoveryTest,StreamingSendPublicationRecoveryTest test` | 1 | test compile error (a misapplied edit in my test file); kept | `red0-compile-error` |
| 2 | RED `…` same command, production unchanged (21:22:00–21:22:23) | 1 | 82 run, **4 failures** (Gap 2: `…PublishedAfterTheAttach…`, `…AfterLaterGenerationsAreDelivered…`; Streaming 2: `…PublishedAfterTheStartingGenerationWasRead…` both variants, `collector.failed` never fired). The three companion tests (duplicate, after-final, with-snapshot) passed on old code | `red` |
| 3 | RED codec `-pl spec-grpc,jsonrpc-common -am -Dtest=Phase2CodecTest test` (21:23:25) | 1 | 11 run, 1 failure: 1.1 `"method":null` -> `UnsupportedOperationException` instead of `IdJsonMappingException` carrying the id. The other-method id tests (CancelTask, push-config x3) passed already | `red-codec` |
| 4 | GREEN codec (21:23:44–21:23:50) | 0 | 11/0/0/0 | `green-codec` |
| 5 | GREEN `-pl server-common -am -Dtest=PublicationGapRecoveryTest,StreamingSendPublicationRecoveryTest,PublicationRecoveryProvisioningTest test` | 0 | Gap 37, StreamingSend 45, Provisioning 10 = 92, 0 fail/skip | `green-recovery` |
| 6 | `routes`: `-pl reference/jsonrpc -am -Dtest=A2AServerRoutesTest,Phase2BoundaryTest test` | 0 | 41 + 5 = 46, 0 skips | `routes` |
| 7 | `scfull`: `-pl server-common -am test` | 0 | server-common 1048 run, 0 fail/err, 0 skips in module (3 upstream `VertxA2AHttpClientSSETest` in the reactor); Gap 37, StreamingSend 45 | `scfull` |
| 8 | `plan1`: `-pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest test` | 0 | client 26 (19 + 7); `JSONRPCHandlerTest` 80 run / 1 pre-existing `@Disabled` skip | `plan1` |
| 9 | `cbase`: `-pl client/base,reference/jsonrpc -am -Dtest=Phase3JSONRPCPublicClientTest,ExistingOrdinaryEntryPointReductionTest,Phase2BoundaryTest test` | 0 | client/base 49, `Phase2BoundaryTest` 5, 0 skips | `cbase` |
| 10 | `jsonrpcmod` (plan check 2): `-pl tests/multiversion/jsonrpc -am -Dtest=Phase3JSONRPCMutationTest verify` (21:28:28–21:30:15) | 0 | `Phase3JSONRPCMutationTest` 21, 0 fail/skip, 1:47 | `jsonrpcmod` |
| 11 | `jsonrpcmodfull`: `-pl tests/multiversion/jsonrpc -am verify` (21:30:15–21:35:32, 5:15) | 0 | module 164, 0 skips (`MultiVersionJSONRPCTest` 75, `Phase3JSONRPCMutationTest` 21, `Phase3JSONRPCNoUserArtifactsTest` 4); wall time normal, no anomaly | `jsonrpcmodfull` |
| 12 | `grpcrest` first run: `-pl spec-grpc,jsonrpc-common,tests/multiversion/rest,tests/multiversion/grpc -am verify` (21:35:32–21:37:42) | **1** | **FAILED** in `extras/http-client-android`: `AndroidA2AHttpClientSSETest.cancellableSseDisconnectsHeldOpenPeer:213 SSE completion should be cancelled ==> expected true but was false` (25 run, 1 failure). Downstream REST/gRPC modules SKIPPED | `grpcrest-1-android-failure` |
| 13 | `grpcrest-rerun` identical command (21:38:01–21:44:08, 6:05) | 0 | BUILD SUCCESS; `AndroidA2AHttpClientSSETest` 25/0 failures; multiversion REST 146 (18 skipped), gRPC 131 (22 skipped), compat-0.3 REST 200 (27), gRPC 53 (11), reference gRPC 76 (11), REST 309 (27): same counts as the previous baseline | `grpcrest-rerun` |

Incomplete provenance preserved: the first background chain (`chain-1-killed.sh`) died when a tool call was cancelled; only `routes`
had run to BUILD SUCCESS (21:24:37) without writing `.exit`/`chain.status`. Its log is kept as `routes-incomplete-chain-killed.log`
and was **not** counted; `routes` was rerun (row 6) under `setsid` in `chain.sh`. Every counted check started after the last source edit
(hashes below).

## Android SSE cancellation intermittent failure (not fixed, deferred by the user)

`AndroidA2AHttpClientSSETest.cancellableSseDisconnectsHeldOpenPeer` failed once (row 12) and passed in the identical rerun (row 13).
`extras/http-client-android` is not touched by this task or by any T17 change; it entered the reactor through `-am` of the REST/gRPC
multiversion modules. **The cause is not established** (no investigation, no Android code change). The user explicitly accepted deferring
this for their later follow-up. It is not claimed fixed; both logs are kept. It is not a T17 acceptance claim either way.

## Low findings

- **R2 – fixed (small, demonstrated).** Selected-1.1 `parseRequestBody` used `request.get("method").getAsString()`, so `"method": null`
  or `{}` threw `UnsupportedOperationException` (route: `InternalError`, `"id": null`) and a numeric/boolean method was treated as an
  unknown method. A shared `getAndValidateMethod` (same check the legacy path already had, now used by both) throws `IdJsonMappingException`
  with the request id. New test `selected11NonStringMethodRejectionsCarryTheRequestIdLikeTheFrozenPath` (null, object, array, boolean, number;
  versions 1.0 and 1.1). RED row 3. Also added CancelTask and the three push-config methods (bad tenant) to the non-send id tests; they
  passed on the old code (no defect there).
- **R3 – assessed, no change.** The mutation provenance gaps (missing `mutN1.exit`, reconstructed `mut2.exit`, unpreserved sed) concern the
  previous session's logs, which are frozen. The existing mutants were not rerun. The new tests were verified by RED against unmodified
  production (row 2), not by a new mutant. The `getActiveChildQueueCount <= 0` weakness noted by the reviewer is unchanged.
- **R4 – assessed, partial.** The new handler test asserts `activeObservationCount() == 0`, `!isScheduled()`, no events, no Task snapshot and
  that `GetTask` returns the committed state. Agent-run and main-queue cleanup after an N1 close are still not asserted (same cleanup path as a
  cancelled consumer; the input is already committed and the agent still runs, as documented). Retained Low.

## Tests added

`PublicationGapRecoveryTest` (+5, local and remote): covered commit published after the attach closes with the resync failure (no event,
child closed, 0 observations, not scheduled, no task change, no webhook/replication for remote); same after later generations 6 and 7 were
delivered (progress of non-covered events, then failure, store head unchanged); redelivered later generation ignored, no failure; covered
commit after the final event adds nothing; with a snapshot a late covered commit is still discarded and later ones delivered. A `Fixture.publish(batch, remote)`
helper was added. `StreamingSendPublicationRecoveryTest` (+2 parameterised): the delayed batch is distributed by the agent on its first
run, i.e. after the observation completed; the stream ends with the resync `InternalError`, no Task snapshot or event, observation released,
`GetTask` returns committed state. `Phase2CodecTest` +1 test and 4 more non-send cases.

## Source hashes (sha256; `/tmp/p3-t17-fix3-logs/sha256.txt`)

```
08b76e5ce753261d0e7ab1408412f72957719990f5c35a6a5f9f3af53cf27bfe  docs/content/dev/configuration.md
229be9e048f1c958e6e27e158705ac064227061c3ac39edf29ef318050d8e7e0  server-common/src/main/java/org/a2aproject/sdk/server/events/PublicationReconciler.java
aa24708a68aa6571fc40573efa28d7ab84950f40abd6d697340baa416a6dc572  server-common/src/main/java/org/a2aproject/sdk/server/requesthandlers/DefaultRequestHandler.java
d0b085165f934f6708be78278221fb2f8436a1f178559d65c46ceb20b4bc8123  server-common/src/test/java/org/a2aproject/sdk/server/events/PublicationGapRecoveryTest.java
c7538e391411d5f9ac42e4f9cf6e487c63f2f28397cf3589b535ff1bb3b591ba  server-common/src/test/java/org/a2aproject/sdk/server/events/StreamingSendPublicationRecoveryTest.java
0c7b62b0f19c53908cadcc50ff522d3809d1ce0302c6c1115a3ef5c2f48037b9  spec-grpc/src/main/java/org/a2aproject/sdk/grpc/utils/JSONRPCUtils.java
819c208fd531ade9d47ccec4bc8cee42497ed4cf81d451ab56ba885989467c2d  spec-grpc/src/test/java/org/a2aproject/sdk/grpc/utils/Phase2CodecTest.java
b15d0fa4fb8b0eee25118e4f71ad42e6a4120e6ff8caf3286a457d5156cbfcc2  .claude/architecture/a2a-1.1/phase-3/reviews/P3-T17-fix2-review.md
```

## Failed or non-progress attempts (not hidden)

RED attempt 1 compile error (test edit misapplied; fixed). Two parallel `apply_diff` calls: one silently did not apply and one test edit
left a syntax error (fixed by re-reading and line edits). The first check chain was killed (above). The `grpcrest` Android failure (above).
No defect reached three consecutive substantive attempts without progress; the diagnosis stop was not triggered.

## Known limits

All earlier limits stand (CDI + JPA enabled path and extras modules not run, F1.1 wire demand, N3/N4/N6/N7/N8/N9, opt-in default off,
REST/gRPC run with recovery off, artifact-only `SendInput` unwatched). Plus: pre-tap commit distributed after the attach closes the stream
(conservative); Android SSE intermittent failure unexplained. Status: **corrected candidate, NOT accepted.** A targeted affected-diff
re-review, then a separate fresh full review, must follow. T18/T19/T20 not started.

# Final-review F-2 correction session (fix4)

- Assignment: start 2026-11-26 22:19 UTC, original two-hour deadline **2026-11-27 00:19 UTC** (not reached; extension not needed;
  finished about 22:40 UTC). Base candidate `5c0369db1e55b27325c8154887a0e181ddaf0368`, task base `d869e957…`, branch `work/p3-t17`.
- Model: requested = configured IBM Bob model (user-authorised); observed identifier unavailable; Sol not claimed; Astra not used.
- Logs `/tmp/p3-t17-fix4-logs` (script `run.sh`: `timeout -k 30 1200 mvn -B -ntp -Dmaven.repo.local=/tmp/p3-t17-fix4-m2 …`, each run has
  `.cmd/.start/.end/.exit`); private repo `/tmp/p3-t17-fix4-m2` was seeded by copying `/tmp/p3-t17-review-final-m2` (a prior private
  repo; online resolution still allowed). No nested agents, no Serena, no push, shared task-list/`codex-progress.md`/`bob-progress.md` untouched.
- Preserved reviews, committed byte-identical (sha256 `…/sha256-final.txt`): `P3-T17-final-review.md` (revision 2, `363fdb38…`),
  `P3-T17-fix3-review.md` (`9d8c8eeb…`), and **`P3-T17-final-review-rev1.md`** (`07599c85…`), the initial full-review revision 1 copied
  from `/tmp/p3-t17-review-final-logs/P3-T17-final-review.rev1.md`. **Revision 1 was superseded by revision 2** (revision 1 accepted
  T17 and rated F-2 Low; revision 2 withdrew acceptance and made F-2 blocking). Its contents are unchanged.

## F-2 independently verified before editing

Read `Phase3MutationScenarios.metadataOnlyStatusPreservesTheMessageOccurrenceCount` and `AgentExecutorProducer.handlePhase3`: the old
test sends an ordinary input to a task created by `phase3:work` (`startWork()`, no status message); it submits no full Task, has no
message-bearing starting status, no metadata, no timeline status assertion, and no stream check. The gap is real (confirmed, as the review states).
That test is kept unchanged (name and body), with only a javadoc line saying what it proves.

## Design finding (root cause of the first failed scenario, retained)

First attempt (`new1-npe-failure.log`): leave a message-bearing status in one invocation and submit `addTask` from the next. The
scenario died with a `NullPointerException` on `before.status().message()` because **`TaskMutationPlanner.planInput` (lines 92-97) moves
any status message into the history and clears it whenever an input message is committed**. An input-triggered invocation therefore never
starts from a message-bearing status. Also `AgentEmitter.addTask` always proposes against the Task captured at invocation start
(`ExecutionProposalContext.submitLegacyTask`), and a proposal built before the agent's own status delta conflicts (A03/A05 contract), so
a legacy `addTask` after the agent's own `startWork(message)` would be a stale-base conflict. Only the cancel invocation starts from the
stored status without an input. This is by design, not a production defect, so no production change was made.
**Deviation to review:** the user asked for `addTask/emitEvent(Task)`. The scenario therefore uses the same production submission path
(`AgentEmitter.submitTask(TaskProposal.create(refreshTask(), task))`, which `addTask` calls with the invocation-start snapshot), built from a
freshly refreshed snapshot after the agent's own acknowledged `startWork(message M)` status commit. `addTask`/`emitEvent(Task)` from a
message-bearing starting status is reachable only in a cancel invocation, which ends in a terminal state and was not used. The reviewer
should rule whether `submitTask` is acceptable for the shared bullet or whether a cancel-based `addTask` variant is also required.

## Test-only changes (0 production lines)

- `AgentExecutorProducer`: `phase3:metadata-proposal` branch, `PHASE3_STATUS_MESSAGE_ID`, `PHASE3_METADATA_PROBE` / `MetadataProposalProbe`
  (barriers `entered`/`release`, captured committed snapshot, correlated `ProposalResult`). The branch needs an installed probe.
- `Phase3MutationScenarios`: new shared scenario
  `metadataOnlyFullTaskProposalFromAMessageBearingStatusCommitsOneStatusObservationAndNoMessage` plus helpers. Subscriber (1.1,
  streaming) is attached before the send; the writer's blocking send runs in a thread; barrier: input and message-bearing status are
  committed and observed by the subscriber, the held agent's snapshot equals the stored task, `generation = created + 2`, spy marks
  taken, then release. Asserted: `ProposalResult.Committed` with `changed`, exactly one event, a `TaskStatusUpdateEvent` at
  `before+1` with the metadata and the same status message id and status; stored: exactly one new generation, earlier timeline untouched,
  exactly one new `StatusValue` entry (status and message id unchanged), no `MessageValue` entry for the message, history/artifacts
  unchanged, status message occurrence count 1 before and after, metadata merged; the same via public 1.1 send, get and list (and the
  reduced stream task); server spies after the marks: only `TaskStatusUpdateEvent` published and no message event replicated; the
  subscriber receives exactly one event, a status update (metadata + message id), no `TaskMessageUpdateEvent`; 1.0 client history ids and
  occurrence count unchanged with status message and metadata visible; 0.3 reader history ids and state unchanged.
- Wire views are compared by status identity (state, timestamp, message id/role/task/context/text), not record equality, because the
  JSON codec maps absent metadata to `{}` (second failure `new2-status-equality-failure.log`, a test over-strictness, fixed in the test; exact
  equality remains on the stored and committed server-side values).
- Fixture hooks (`MetadataProposalProbe`, the agent branch) live in `tests/server-common`; REST/gRPC reuse the shared scenario by
  extending `Phase3MutationScenarios` with their existing hooks (no new abstract hook was needed).

## Commands and results (all serial; exit codes from `.exit`)

| Log | Command (after `mvn -B -ntp -Dmaven.repo.local=/tmp/p3-t17-fix4-m2`) | Exit | Result |
|---|---|---|---|
| `red1-compile-error` | `-pl tests/multiversion/jsonrpc -am -Dtest=Phase3JSONRPCMutationTest … verify` | 1 | compile error: a corrupted edit of `AgentExecutorProducer` (concurrent edits); not behavioral evidence; file reset to HEAD and reapplied |
| `new1-npe-failure` | same | 1 | 22 run, 1 error: NPE `before.status().message()` (design finding above) |
| `new2-status-equality-failure` | same | 1 | 22 run, 1 failure: wire status equality null vs `{}` metadata (test-only fix) |
| `new3` | same (plan check 2) | 0 | 22 run, 0 failures, 0 skips |
| `mut1` | mutant: `applyProposalStatus` ignores `syntheticMetadata` (adds the status message to history); `-Dtest=Phase3JSONRPCMutationTest#metadataOnly*` | 1 | killed: `stored: the status message still occurs once ==> expected 1 but was 2`; old input-count test passed (2 run, 1 failure). Diff `mutant1.diff`; planner restored (sha256 identical to `TaskMutationPlanner.java.orig`, `git status` clean for production) |
| `plan1` | plan check 1: `-pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest -Dsurefire.failIfNoSpecifiedTests=false test` | 0 | client 26 (19+7); `JSONRPCHandlerTest` 80 run, **1 skipped** (pre-existing `@Disabled` custom components test) |
| `jsonrpcmodfull` | `-pl tests/multiversion/jsonrpc -am verify` (22:31-22:37) | 0 | module 165, 0 skips: `MultiVersionJSONRPCTest` 75, `Phase3JSONRPCMutationTest` 22, `Phase3JSONRPCNoUserArtifactsTest` 4, others |
| `consumers-testcompile` | `-pl tests/multiversion/rest,tests/multiversion/grpc,reference/rest,reference/grpc,reference/jsonrpc -am test-compile` | 0 | the other consumers of the shared test fixture compile |

**Reused, not rerun (unchanged sources).** Production code is byte-identical to `5c0369db` (diff is two test files). The codec, recovery,
routes, client/base, server-common-full and REST/gRPC multiversion runs of `/tmp/p3-t17-review-final-logs` and `/tmp/p3-t17-fix3-logs`
(REST 146/18 skipped, gRPC 131/22 skipped, compat REST 200/27, compat gRPC 53/11, reference gRPC 76/11, reference REST 309/27) stand as
earlier evidence; this session does not claim to have observed them. They were not repeated because no production or REST/gRPC test source
changed; only a compile check of the fixture consumers was run (user's cost direction).

Android SSE cancellation (`AndroidA2AHttpClientSSETest.cancellableSseDisconnectsHeldOpenPeer:213`): first failure retained
(`/tmp/p3-t17-fix3-logs/grpcrest-1-android-failure.log`), rerun passed, root cause unestablished, deferred by the user, no Android change.
No defect reached three consecutive substantive attempts (two scenario failures with distinct causes, each resolved).

Retained limits unchanged: N9 (input message role not validated) remains an **unresolved normative question**, not a decision; F-1
(route `Throwable` null-id) and F-3 (interceptor test on built-in observe) remain open Low items; T13 live PostgreSQL/Kafka gate, the compatibility
gate, the Java 17 clean install and the phase audit stay T20; Phase 4/5/6 obligations excluded; opt-in publication recovery remains default off.
Status: **corrected candidate, NOT accepted.** Required next: independent targeted affected-diff re-review, then a separate fresh full review.
T18/T19/T20 not started.
