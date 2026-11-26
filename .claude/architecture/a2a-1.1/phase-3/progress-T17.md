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
