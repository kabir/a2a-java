# P3-T17 targeted correction re-review (c572b04..71abc014)

## Disposition: **Changes required — T17 is NOT accepted**

- One concrete, reproduced defect (N2, Medium): on the selected 1.1 JSON-RPC route a parse-time `InvalidParamsError` for
  `GetTask`, `ListTasks` (and any non-send method that validates parameters in `FromProto`) is still answered with
  `"id": null`, although the request id is known. The correction fixed this only for `SendMessage`/`SendStreamingMessage`.
- Everything else I checked in the correction is either met, an explicit residual (N1, N3, N4, N8, N9) or low-severity hygiene.
- This is a targeted re-review only. After the correction(s) below, **a fresh full independent review is still required** before
  T17 can be accepted. T18/T19 not started. Nothing was committed, pushed or changed in the repository except this file.

## Identification

| Item | Value |
|---|---|
| Candidate (HEAD) | `71abc014921417b0b353c7f970be998aa7d4861a`, branch `work/p3-t17` (`git rev-parse HEAD`) |
| Previous independently reviewed candidate / base | `c572b04bd9bc809f734d4a136b4d189168cc0734` (ancestor of HEAD, verified) / `d869e957b310e2585409ac3b3adb166d7823d36c` |
| Git state at start | `git status --short` empty |
| Source hashes | `sha256sum` of the committed tree for the 20 files listed in `handoffs/P3-T17.md` (correction list) and `progress-T17.md` (F2 list) equals the recorded hashes (spot-checked `DefaultRequestHandler` f2c1b21d…, `A2AServerRoutes` 2d3e9797…, `JSONRPCUtils` 9960db08…, `a2a-defaults.properties` b0a314ad…, scenarios 414fa331…, both new server-common tests). My runs used the tree at HEAD (clean); no earlier overlapping-build log was used as evidence. |
| Window | Start 2026-11-26 18:13 UTC, original deadline **20:13 UTC**; review finished ≈18:46 UTC (system clock). The pre-authorised two-hour extension was **not used**. |
| Model | Requested: Bob's configured model (coordinator asked "Terra medium"). Observed model id / telemetry: **unavailable**. I do not claim Sol or Terra. Astra not used. No nested agents, Serena/MCP, push or external writes. |
| Maven | Online, own repo `/tmp/p3-t17-review2-m2`; every command `timeout ≤ 1200 s`, run serially, own log + `.exit` in `/tmp/p3-t17-review2-logs/`; no root `test.log`. Mutations/probe ran only in `/tmp/p3-t17-review2-probe` (a `git archive HEAD` copy), never in the repo. |

## Checks actually run (all on HEAD, exit codes from `.exit` files)

| Name | Scope | Exit | Result |
|---|---|---|---|
| plan1 | `transport/jsonrpc`, `client/transport/jsonrpc`: `JSONRPCHandlerTest`, `Phase2PublicClientTest`, `Phase3ClientGenerationTest` | 0 | client 26; `JSONRPCHandlerTest` 80 run, **1 skipped (pre-existing `@Disabled`)**, 0 fail |
| recovery | `server-common`: `PublicationRecoveryProvisioningTest` (10) + `StreamingSendPublicationRecoveryTest` (33) | 0 | 43/0/0/0 skip |
| codec | `spec-grpc`: `Phase2CodecTest` | 0 | 7/0/0/0 |
| plan2 | `tests/multiversion/jsonrpc`: `Phase3JSONRPCMutationTest` (21) + `Phase3JSONRPCNoUserArtifactsTest` (4), `verify` | 0 | 25/0/0/0 skip |
| cbase | `client/base`: `Phase3JSONRPCPublicClientTest` + `ExistingOrdinaryEntryPointReductionTest` (49), `reference/jsonrpc`: `Phase2BoundaryTest` (5) | 0 | 54/0/0/0 |
| grpcrest | `-pl spec-grpc,jsonrpc-common,tests/multiversion/rest,tests/multiversion/grpc -am verify` (whole upstream reactor incl. spec 171, server-common 1027 with 0 skipped) | 0 | `BUILD SUCCESS`; REST module 146 run / **18 skipped**; gRPC module 131 run / **22 skipped**; 0 failures/errors |
| jsonrpcmod | `-pl tests/multiversion/jsonrpc -am verify` | 0 | `BUILD SUCCESS`; module 164 run, 0 skipped, 0 failures; `MultiVersionJSONRPCTest` 75, `Phase3JSONRPCMutationTest` 21, `Phase3JSONRPCNoUserArtifactsTest` 4, `Phase3JSONRPCPublicClientTest` 18 |
| routes | `reference/jsonrpc`: `A2AServerRoutesTest` | 0 | 36/0/0/0 |
| idprobe | N2 probe `ReviewIdProbeTest` (probe copy only) | 0 | output below |
| mut1–mut5, mut3b | unit mutation probes (probe copy only), `StreamingSendPublicationRecoveryTest` + `PublicationRecoveryProvisioningTest` | see table | see below |

The skips in REST/gRPC are the existing suite skips; I did not investigate them and nothing in T17 changed those suites. Not run: full reactor,
Docker/Kafka/JPA modules, `extras/*` (see limitations).

### Mutation probes (production mutated in the probe copy, unit tests re-run)

| Mutation (line in `DefaultRequestHandler.java`) | Result | Killed by |
|---|---|---|
| mut1 remove `streamRecovery.detach()` in consumer `onError` (:1419) | exit 1, **killed** | `anAgentFailureEndsTheStreamAndReleasesTheObservation:339` |
| mut2 remove detach in the `!agentStarted` finally (:1331) | exit 0, **survived** | – (no test makes pre-agent setup fail after the attach) |
| mut3 / mut3b remove detach in the catch at the end of `sendOrdinaryStream` (:1444) | exit 0 both times, **survived** | – (mut3's first run took 181.6 s for the class versus ≈6 s; the repeat took 5.9 s, so that was an environmental stall, not caused by the mutation) |
| mut4 covered watermark forced to 0 (:2133) | exit 1, **killed** (11 errors) | e.g. `aCommitRacingTheAttachIsDiscardedWhen…`, `aHealthySendStream…`, `lostFinal…` |
| mut5 do not close the queue when `attach` fails (:2123) | exit 1, **killed** (2 failures) | `aClosedReconcilerRejectsTheSendAndLeavesNoAgentOrChild:361` |
| (implementer, not re-run) attach removed; gate forced on | killed per progress file | – |

N2 probe output (`PROBE[version] case => result`, `parseRequestBody(body, null, version)`, request id `r1`):

```
PROBE[1.1] GetTask neg history     => InvalidParamsError (raw, no id)   "Invalid history length"
PROBE[1.1] GetTask neg timeline    => InvalidParamsError (raw, no id)   "Invalid timeline length"
PROBE[1.1] ListTasks neg pageSize  => InvalidParamsError (raw, no id)   "pageSize must be between 1 and 100, got: -3"
PROBE[1.1] SendMessage blank id / no message / neg timeline / bad artifact => InvalidParamsJsonMappingException id=r1
PROBE[1.0] GetTask neg history     => InvalidParamsJsonMappingException id=r1
PROBE[1.0] GetTask neg timeline    => InvalidParamsJsonMappingException id=r1
PROBE[1.0] ListTasks neg pageSize  => InvalidParamsJsonMappingException id=r1
```

## New findings

### N2 — Medium (concrete defect, reproduced): request id dropped for non-send parse-time `InvalidParamsError` on selected 1.1
- Path: `spec-grpc/src/main/java/org/a2aproject/sdk/grpc/utils/JSONRPCUtils.java:256-281` (selected-1.1 `parseRequestBody`); caller
  `reference/jsonrpc/src/main/java/org/a2aproject/sdk/server/apps/quarkus/A2AServerRoutes.java:390-401`.
- The correction wraps `InvalidParamsError` with the request id only inside the `SendMessage`/`SendStreamingMessage` branch (:261-268). The
  `SubscribeToTask` branch (:273-279, `observeTaskParams`) and the fall-through `parseMethodRequest(...)` (:280) are not wrapped, whereas the
  retained 1.0 path wraps the whole `parseMethodRequest` (:225-229). In the route, `request` is still `null` when the parser throws, so
  `new A2AErrorResponse(e)` is used and the response has `"id": null` even though the id was parsed (JSON-RPC 2.0 §5 requires the id when it can be detected).
- Repro: probe above. On 1.1 `{"jsonrpc":"2.0","id":"r1","method":"GetTask","params":{"id":"t","historyLength":-1}}` (also `timelineLength:-1`, and
  `ListTasks` with `pageSize:-3`) throws the raw `InvalidParamsError`; the same requests on 1.0 return the id. So the new selected version is *less*
  attributable than the frozen one. Not exercised by `A2AServerRoutesTest` or the multi-version tests (all green). Raw HTTP e2e was not run
  for these inputs; the conclusion rests on the parser probe plus the route code at :390-401.
- Why it matters here: the review asked for attributable typed errors on all routes. F1.3 is therefore met for sends only.
- Fix: wrap the whole selected-1.1 dispatch (`SubscribeToTask` and the fall-through) the same way as :225-229, then add parser tests for these methods and a route test with an echoed id.
  The pre-existing `A2AServerRoutesTest:853,1020` null-id assertions (invalid request / protobuf parse error) are not affected.

### N1 — Low–Medium (design residual, documented, developer decision)
With recovery enabled, an event committed by another writer whose publication reaches the sender's child queue between the tap and the generation read `G`
is treated as covered and discarded, and a send stream carries no snapshot to replace it. Pinned by `aCommitRacingTheAttachIsDiscardedWhenCoveredAndALaterOneIsDeliveredOnce`
and described in `docs/content/dev/configuration.md`. With recovery off it would be delivered. The unobserved path already misses events committed before the tap, so
the added window is tiny, and it does not violate the lost-*final* guarantee. Not a blocker alone; the full review should confirm it is acceptable.

### N3 — Info: artifact-only stream and the lost-final guarantee
`onSendInputStream` for artifact-only input commits eagerly and returns a one-element publisher built from the commit result (`DefaultRequestHandler` ≈:966-988). There is no queue stream
that could strand a final publication, so the "lost final publication closes the affected stream" guarantee is not prevented by this limitation. It is stated in the handoff, progress and docs.
Other observers of that task (subscribe, ordinary streaming send) are covered independently. I do not consider it an acceptance conflict.

### N4 — Low (evidence gap, not a defect)
- `@PreDestroy` (annotation present at :504) is tested only by calling `closePublicationReconciler()`. Default-off through CDI is shown by a unit test that loads the real
  `a2a-defaults.properties`, not by a Quarkus boot; enabled-through-CDI is shown end to end only by `Phase3JSONRPCMutationTest` with the in-memory store.
- `JpaDatabaseTaskStore` does implement `AtomicTaskStore` (`JpaDatabaseTaskStore.java:69`), so with the property on, CDI + JPA would install a reconciler; this combination is **untested**.
  My judgement: not required at the T17 JSON-RPC/in-memory boundary because the feature is opt-in and default false; it must stay an explicit open limit
  (extras/JPA coverage) for the full review and the extras phase. I did not invent coverage.
- REST and gRPC suites run with the property off only.

### N5 — Low (doc)
`DefaultRequestHandler.Builder.publicationReconciler` javadoc (:677) still says "for task subscriptions"; it now also covers ordinary streaming sends.

### N6 — Low (test hygiene)
Fixed sleeps/quiescence waits: `Phase3MutationScenarios` ≈:379 (300 ms), :673 (1 s negative control), :901/:908 (50 ms poll loops with 10 s caps), plus the ones listed in
`Phase3ScenarioSupport.sendCollected` (≈:117/:122, 400 ms settle), `Phase3OldObserver.awaitFrames` and `Phase3JSONRPCPublicClientTest` (≈:286, :297, :361, :573; line numbers approximate). None substitutes for a race barrier: the guard race uses a `CyclicBarrier`;
the recovery unit tests use an injected clock, a mocked scheduler and direct `tick()`. A positive assertion can only be late, not falsely passed by a sleep; the negative
waits can miss a late event (accepted weakness).

### N7 — Info (pre-existing design)
A rejected opening input with artifacts (client artifacts disabled) may already have run the executor (opening input is admitted atomically with the first agent event). The test proves nothing is persisted and the error is typed; it cannot prove the executor did not run.

### N8 — Low
The tenant case uses a tenant that does not provision 1.1 (`VersionNotSupportedError`, attributable). The core has no per-task tenant ownership, so a "task belongs to another tenant" mismatch is not modelled or tested.

### N9 — Open question for the developer (Low)
The input *message* role is neither validated nor pinned. The normative basis I found (`phase-0/selected-policies.md` G6 lines 42-51, `decisions.md:12`,
`architecture.md:195-210`, `validation.md:90`) covers the **artifact** role/origin only. The implementer's reading ("plan 'role' means artifact origin") is defensible and covered
(`agentOrigin` rejection, USER origin hidden from 1.0/0.3). A client-sent `ROLE_AGENT` message is still stored as agent-role history; whether that is acceptable is a developer decision.

### N10 — Resolved (no defect)
The route now echoes the id for any `A2AError` of a parsed request, for every version served by `A2AServerRoutes` (was `null`). Existing frozen assertions still pass:
`A2AServerRoutesTest` 36/36 (including the null-id invalid-request/protobuf-decode cases at :853/:1020) and `MultiVersionJSONRPCTest` 75/75 (`-32009` id null at :127 goes
through the untouched `VersionRouter.requestIdForError`, which returns null for non-1.1). The change is a JSON-RPC 2.0-conformant behaviour change for 1.0 on this route; I found no fixture that contradicts it. REST/gRPC/0.3 routes are unaffected.

### N11 — Low (evidence gap, found by mutation)
Mutations mut2 and mut3 survive: no test makes setup fail after `observeSendStream` attached but before the agent starts (e.g. `pushConfigStore.setInfo`, ownership recording,
`registerAndExecuteAgentAsync` throwing), nor fails publisher construction. The detaches are defensive; without them the child queue and the observation would leak until the process ends.
Without recovery the pre-existing code leaks the same child queue on such failures, so this is not a regression. A test that injects such a failure would close the gap.

## Original findings F1–F12: final mapping

| ID | Status | Evidence (what I checked, not the implementer's claim) |
|---|---|---|
| F1.1 old observers, finite filtered demand | **Met in-process; wire edge-demand not demonstrated (recorded limit)** | `Phase3OldObserver` subscribes to the same publisher the route uses (1.0: `onSubscribeToTask` + `VersionedOutputProjector.projectStream`, as `JSONRPCHandler.onSubscribeToTask`; 0.3: `Convert_v0_3_To10RequestHandler.onResubscribeToTask`), requests one item at a time, interleaves 12 filtered message-only inputs, and asserts progress to completion, `frames < committed generations`, USER artifacts/message/generation hidden for 1.0 and 0.3; reads (get/list/0.3 get) over real HTTP. The reference `SseResponseWriter` requests `Long.MAX_VALUE` (`reference/common/.../SseResponseWriter.java:82`), so finite demand cannot be produced on the wire; that seam is not tested. The full review must decide whether this satisfies "the shared scenario contract". |
| F1.2 disabled artifact capability | **Met** | `Phase3JSONRPCNoUserArtifactsTest` 4/4 with its own profile; typed `InvalidParamsError` for unary and streaming; nothing persisted (N7) |
| F1.3 ID errors | **Partly met — N2** | blank `messageId` typed and id echoed (send routes, `Phase2CodecTest` 7, `A2AServerRoutesTest`); other methods on 1.1 still lose the id |
| F1.4 tenant/context attribution, both tasks | **Mostly met** | typed `VersionNotSupportedError`, wrong context, unknown task, both tasks re-read; N8 |
| F1.5 message role | **Open question with explicit basis** | N9 |
| F2 default-on reconciler | **Resolved as the user decided (opt-in, default false); residuals N1, N4, N5, N11** | see F2 section |
| F3 direct Message | **Met** | `Phase3Spy`/`Phase3SpyQueueManager` assert queue created *and* closed, nothing replicated, only `Message` published, no task/config, no webhook; non-vacuous control (`taskBearingInput…` replicated and persisted); unary/streaming × `returnImmediately` × artifacts; opening message plus artifacts is persisted as one task |
| F4 streaming guards | **Met** | barrier race through the real streaming server, guarded stale rejection typed, artifact-only stream, message+artifacts, `timelineLength` windows |
| F5 `throughGeneration` | **Met** | streaming send to an existing task asserts `NO_BASELINE`, `through == null`, partial task, last timeline stamp = head; unary requires `through == head` |
| F6 inline config / terminal projection | **Met** | loopback `Phase3Webhook`; the COMPLETED notification with the final artifact reaches the webhook only when the inline config exists (negative control without config; 1 s sleep, N6); 1.0 and 0.3 read the projected terminal. Assertion checks the final artifact is present, not that the notification body is exclusively the committed shape (weak, Info) |
| F7 mixed version | **Met** | 1.0/0.3 writers with a 1.1 subscriber and read-back through 1.0/0.3; USER artifacts hidden |
| F8 malformed oracle | **Met** | typed errors, other task re-checked, raw `-32602` cases echo ids |
| F9 controlled-peer client | **Met** | three input APIs, malformed cases parameterised over three routes with typed envelopes, unknown timeline variant, live cancellation (`heldOpenSse`) |
| F10 lost final | **Met** | `InternalError` with the GetTask resync message, one ingress, no mutation; streaming-send variant (recovery unit + e2e) |
| F11 hygiene | **Partly** | provenance recorded; style item fixed; N5 doc and N6 remain |
| F12 info | n/a | RED-at-base set is a characterisation set, not a defect |

## F2 (user-chosen opt-in) assessment

Read from code, then exercised by tests and mutation probes.
- **Configuration**: `initConfig` (:454-462) installs a reconciler only when `a2a.publication-recovery.enabled` is `true` **and** the store is an `AtomicTaskStore`;
  absent/`false` is off; shipped default `false` in `a2a-defaults.properties` (`MicroProfileConfigProvider` falls back to it); only `true`/`false` accepted (trim, case-insensitive),
  anything else throws `IllegalArgumentException` at startup; bounds are read only when enabled and reject non-numeric/non-positive; non-atomic store logs a warning that nothing is
  protected and installs nothing; a builder-supplied reconciler is kept. Covered by `PublicationRecoveryProvisioningTest` (10/10). Default-on is not present.
- **Ordinary streaming send** (`sendOrdinaryStream` :1249-1466, `observeSendStream` :2113-2141): attach (delivery gate) before the agent starts, then read `G` (existing task) or 0 (new task),
  then `complete(G)`; no snapshot is added, no duplicate baseline, no retry or side effect (the observation is a delivery gate that closes the stream with `InternalError` and never mutates or resends).
  A commit racing the attach is discarded when covered and delivered once when later (N1). Mut4/mut5/mut1 prove the covered watermark, attach-failure close and consumer-error detach are tested; mut2/mut3 show the two defensive detaches are not (N11).
- **Cleanup**: detach on setup failure, consumer `onError`, client cancel (`queue.close(true)` → `onChildClosed` → unregister), and the closed-reconciler rejection test checks the child closed and no agent left.
- **Direct Message and artifact-only**: a direct Message creates no observation that can outlive the queue (queue closed assertion); artifact-only has no stream (N3).
- **CDI lifecycle**: `@PreDestroy` close tested by direct call (N4); CDI + JPA untested (N4).

## Reference route and codec impact (F1.3/N10 summary)
- `A2AServerRoutes` change (:386-401) echoes the id of a parsed request on `A2AError`; `JSONRPCUtils` change (:261-268) wraps send-input `InvalidParamsError`. No other production
  change in `reference/` or `spec-grpc`. Routes (`/` and `/{tenant}`) share `invokeJSONRPCHandler`. Old 1.0/0.3 gates (`validateVersion10Input`, `VersionRouter`) are untouched.
- Existing frozen suites green: `Phase2CodecTest` 7, `A2AServerRoutesTest` 36, `MultiVersionJSONRPCTest` 75, REST/gRPC modules unchanged and green.

## Required before a full review (for the separate correction session)
1. **N2**: carry the request id for every selected-1.1 parse-time `InvalidParamsError` (wrap the `SubscribeToTask` branch and the fall-through, like :225-229); add parser tests for `GetTask`/`ListTasks`/subscribe and a route-level echoed-id test. Rerun `Phase2CodecTest`, `A2AServerRoutesTest`, `MultiVersionJSONRPCTest`.
2. N5 (javadoc) and, ideally, N11 (a pre-agent-failure test so mut2/mut3 are killed).
3. Developer decisions to record, not fix: N1 (race window), N9 (input message role), F1.1 wire-level finite demand, and the CDI+JPA limit (N4).

## Limitations of this review
- Did not run the full reactor, Docker/Kafka/JPA/extras modules, REST/gRPC with recovery enabled, or a real Quarkus boot with the JPA store.
- Mutation probes covered only `StreamingSendPublicationRecoveryTest` and `PublicationRecoveryProvisioningTest`; no end-to-end mutation of the new code was re-run (the implementer's end-to-end mutation results are not re-verified).
- N2 was reproduced at parser level; the raw HTTP response for those inputs was inferred from the route code, not executed.
- Model identity of this reviewer is not observable; I make no claim of Sol/Terra capability.
- This is a targeted review. It is not an acceptance of T17; a fresh full independent review must follow the corrections above.
