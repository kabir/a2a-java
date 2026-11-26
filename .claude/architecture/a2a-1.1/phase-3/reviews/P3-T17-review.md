# P3-T17 independent full review — JSON-RPC production end-to-end mutation slice

## Decision: **Changes required**

Not "Accepted within scope". The code that was changed is largely sound and every command I ran was green. But several
**required** items in the T17 shared scenario contract have no end-to-end evidence, and that contract is the helper the plan says is
frozen at this boundary for T18/T19. The global default-on `PublicationReconciler` wiring is also a material decision that needs the
developer. Details, severities and exact repro/line evidence are below. No finding is "tests passed, therefore accepted".

## Identification and provenance

| Item | Value |
|---|---|
| Candidate | `c572b04bd9bc809f734d4a136b4d189168cc0734` (`feat(a2a-1.1): enable JSON-RPC ordinary mutation end to end (P3-T17)`, committed 2026-11-26 15:56:28 UTC) |
| Base | `d869e957b310e2585409ac3b3adb166d7823d36c` |
| Branch / HEAD / status | `work/p3-t17`; `git rev-parse HEAD` = candidate; `git status --short` empty before, and empty again after my builds (all `target/` dirs ignored) |
| Review window | **Start 2026-11-26 15:58:00 UTC, end 2026-11-26 17:58:00 UTC** (two-hour assignment). Not reset or extended. The user's authorization for a second two-hour window was **not used**. Review file written about 16:15 UTC. |
| Reviewer | Fresh Bob context, separate from the implementer. No nested agents, no Serena/MCP, no push, no shared status touched, no implementation/test modified, nothing committed. |
| Requested model | Reviewer = Bob's configured model (user explicitly substituted it for Sol). Coordinator separately requested "Terra medium". |
| Observed model | **Not observable**: this session shows no model identifier or telemetry. I do not claim Sol, Terra or any specific model. Astra was not used. The request and the (absent) observation are recorded separately on purpose. |
| Maven | Online, independent local repository `/tmp/p3-t17-review-m2`. It was seeded by `cp -a /tmp/p3-t17-m2` (a third-party dependency cache; it holds no `org.a2aproject` artifacts, checked with `find`). It was not shared with later runs. Each command was wrapped in `timeout 1200` (≤ 20 min), run detached with its exit code written to a `.exit` file, and used its own log under `/tmp/p3-t17-review-*.log`. Builds ran strictly one after another, so there was no target-directory overlap. |
| Source integrity | `/tmp/p3-t17-sha256.txt` (15 files) verified with `sha256sum -c`: all OK **against the committed working tree**. So the frozen-hash runs and the commit describe identical source. |

## Scope reviewed

Read in full: `AGENTS.md`; `acceptance.md` run limits (:158-190); plan P3-T17 (`implementation-plan.md` :1112-1170); `handoffs/P3-T13.md`,
`P3-T13-diagnosis-commit-registration-window.md`, `P3-T14.md`, `P3-T16.md`, `P3-T17.md`; `progress-T17.md`. Read the full
`git diff d869e957 c572b04b` (17 files, +2051/-61), the full new test sources, and the nearby production code: `DefaultRequestHandler`
(`initConfig`, `onSubscribeToTask`, `onSendInputStream`, `commitArtifactOnlyInput`, `commitExistingTaskInput`), `MainEventBusProcessor.commitAndPublish`,
`PublicationReconciler` (lifecycle, reader pool, scheduler), `JSONRPCUtils` 1.0/1.1 parsing, `ServerVersionPolicy`, `JSONRPCTransport.startObservation`,
`InMemoryQueueManager.getActiveChildQueueCount`, `JpaDatabaseTaskStore.readSnapshot`. Read the logs listed under "Evidence".

Changed production code: `JSONRPCTransport` (no-cursor `observe`, shared `startObservation`), `JSONRPCHandler` (guard removal, `timelineLength` forwarding),
`A2AServerRoutes` (streaming guard), `DefaultRequestHandler` (global reconciler wiring). No REST/gRPC handler, store, Phase 4/5 or live/cursor code changed.

## Findings

Severity scale: **High** = a required plan item is unproven or a defect that blocks acceptance. **Medium** = a material evidence gap or an
unauthorised/unevidenced behaviour change. **Low** = weak assertion or hygiene. **Info** = no action needed. "Concrete" = reproducible from
the code as read. "Gap" = missing evidence, not a proven defect. I found **no concrete functional defect** in the changed production lines.

### F1 — High (evidence gap, required): required shared-scenario items have no end-to-end coverage, in the helper that is to be frozen
The plan says the shared contract applies to each of T17/T18/T19 and "Freeze the reusable scenario helper at this boundary". Missing in
`Phase3MutationScenarios` / `Phase3JSONRPCMutationTest`:
1. **Old clients make progress under finite filtered demand** (plan bullet 4). `grep -i 'demand|filtered'` over the three new test files returns nothing. The handoff admits this
   (`handoffs/P3-T17.md:77`, "core-level T12 tests only"). No 1.0 or 0.3 *subscriber/streaming* reader exists in the scenarios: 1.0 and 0.3 appear only as
   writers (`Phase3MutationScenarios.java:283-330`) and 1.0 as a `getTask` reader (`:186`, `:304`).
2. **Capability error**: no scenario sends client artifacts to a server whose effective card has `userArtifacts` off. The profile enables it
   (`Phase3TestProfile.java` `test.agent.user-artifacts=true`), and the only card toggle is a default-`false` property in `AgentCardProducer` that no test sets to `false`. Production does enforce it
   (`DefaultRequestHandler.userArtifactsEnabled`, `PendingTaskInput.Admission`), and core test `OrdinaryInputMutationTest:247` covers it, but not through the public client/server path.
3. **ID errors**: no blank/missing `messageId` case. The "unknown task" case (`:205-207`) is covered.
4. **Tenant/context attribution**: the `tenant("phase1-2")` case (`:214`) is accepted by `assertNotNull(outcome.failure)` (`:216-217`), so it passes for *any* failure (client URL/HTTP error as well as a
   typed server error). The handoff concedes this (`:77-79`). The wrong-context case (`:203-204`) has the same weak oracle.
5. **Message role**: only the *artifact* origin role is covered (`agentOrigin`, `:200-202`). The handoff records that an input `ROLE_AGENT` message is accepted (observed server behaviour).
   Whether the architecture requires `ROLE_USER` was not resolved; I found no normative text in `architecture.md`/`spec-delta.md` requiring a server-side check, so I report this as
   an **open question for the developer**, not a defect.
Because T18/T19 reuse this helper unchanged, each gap would be inherited twice. Fix: add the scenarios now (or record, with the developer's explicit agreement, that they move to a named later task).

### F2 — High (decision required): global, default-on `PublicationReconciler` for every `AtomicTaskStore`
`DefaultRequestHandler.java:450-452` creates `PublicationReconciler.forTaskStore(...)` whenever the CDI handler is built with an `AtomicTaskStore` (in-memory and JPA both qualify), and `:470-476` closes it in `@PreDestroy`.
What I verified:
- **Lifecycle/resources/thread safety — acceptable by reading.** The scheduler is created lazily on the first attached observation and released when none remain (`PublicationReconciler.java` `ensureScheduledLocked`/`unregister`);
  the reader pool is 4 daemon threads with idle timeout and a 1024-entry queue (`StoreAuthorityHead`); `close()` is idempotent and clears watches; registry state is guarded by `registryLock`. An idle server costs nothing. A busy one pays one store read per watched task
  per interval.
- **It is a behaviour change for every transport and every deployment.** `onSubscribeToTask` (`:1556-1585`) now reads the authoritative snapshot, uses it as the first frame, and may close the stream with `InternalError` about 30 s after a stranded or missing publication
  (defaults 5/5/10 s). REST, gRPC and both retained versions go through the same method. There is **no opt-out**: the three optional keys (`:455-468`) only change the bounds, and `Limits` rejects non-positive values (`PublicationReconciler.java:99-107`) so a bound cannot be set to "off".
- **It contradicts the recorded T14 position.** `handoffs/P3-T14.md` says the reconciler is "opt-in via the builder" and lists "no CDI auto-wiring" as a retained limit, deferring wiring to "T16–T19". T17's plan file list names only `JSONRPCHandler` and reference routes "as needed" — not `DefaultRequestHandler`.
  The implementer recorded the change (`handoffs/P3-T17.md:87`) but nobody with authority has decided it.
- **No direct test of the wiring itself.** The git diff contains no server-common test. `DefaultRequestHandlerTest`-style coverage of: default bounds, each optional key, an invalid value (`Long.parseLong` → `NumberFormatException` in `@PostConstruct`), `@PreDestroy` close, and a non-`AtomicTaskStore` or decorated store (a CDI proxy that does not implement `AtomicTaskStore` silently gets no protection) is absent.
  It is exercised only indirectly through `Phase3JSONRPCMutationTest` (which fails at base production — see RED log) and, with the default config, by the unchanged REST/gRPC suites that I re-ran (green). JPA is covered only by builder-built handlers (`JpaTaskAuthorization*Test` use `DefaultRequestHandler.builder()`), never CDI + JPA.
- **Protection is partial.** Only `onSubscribeToTask` attaches (`grep 'attach('` shows one production call, `:1565`). The streaming send paths — `onMessageSendStream` (`:1209`/`sendOrdinaryStream` `:1215`) and `onSendInputStream` (`:932`) — have no gate, so a lost final publication on a streaming *send* stream is not
  bounded. T14 listed this as a retained limit; T17's plan bullet "Lost final publication closes the affected stream within the documented bound" is satisfied only for subscribe. The test hook (`TestUtilsBean.saveTask` store-only commit, `Phase3MutationScenarios.java:401`) only drives the subscribe stream.
- **T13 reserve/release pairing: verified OK, not a finding.** The only request-thread commit paths T17 reaches are `commitArtifactOnlyInput` (`DefaultRequestHandler.java:2071`) and `commitExistingTaskInput` (`:2122`), both via `MainEventBusProcessor.commitAndPublish`, whose `onCommittedBatchReserved`
  runs inside the slot callback (`:727-731`) and `onCommittedBatchReleased` in `finally` guarded by `held` (`:757-763`). The other `reserve` sites (`:522`, `:618`) are on the single processor thread, which T13's disposition says always delivers before a later remote batch. T17 adds no new commit path.
**Material architecture decision for the developer:** keep default-on (and add an explicit opt-out and tests), make it opt-in through a property, or limit it to the JSON-RPC reference configuration. I will not decide this. Until it is decided, acceptance would silently approve a cross-transport behaviour change.

### F3 — Medium (gap): direct-Message scenario omits the plan's "opening Message plus artifacts" and has no publication/replication spy; cleanup assertion is vacuous
Plan: "opening Message plus artifacts yields direct Message with no task/config/publication/replication, and queue cleanup".
- The e2e direct test (`Phase3MutationScenarios.java:228-258`) sends a message only; no `artifactUpdates`. Opening message **plus artifacts** is tested only with the task-creating executor (`:262-279`). The core test `DirectMessageMaterializationTest:87` covers pending artifacts at core level, not over HTTP.
- No spy on the event bus, task store writes or replication hook; it checks stored task, config count, list result and child queue only (`:248-253`).
- `awaitChildQueues(..., 0)` treats `<= 0` as released (`:552-560`), and `InMemoryQueueManager.getActiveChildQueueCount` returns **-1** for a missing or closed queue (`InMemoryQueueManager.java:156-160`). The assertion therefore cannot tell "cleaned up" from "never created or wrong id" (the handoff says the same).

### F4 — Medium (gap): the removed streaming guards have no real-server test; the streaming routes in the matrix assert nothing about stamps
T17 removes the streaming guards for artifacts, `ifGenerationMatch` and `timelineLength` (`JSONRPCHandler.java:376`, `A2AServerRoutes.java:606,632-637`). Evidence for that removal is a Mockito route test (`Phase2BoundaryTest`) and a mocked-handler unit test. The shared race, artifact-only and message+artifacts scenarios all use a **non-streaming** client
(`open("1.1", false, false)`: `:112-113`, `:164`, `:195`). Streaming input with a guard, artifacts or `timelineLength` therefore never crosses the real server. `onSendInputStream` for artifact-only input commits eagerly at call time and returns a one-element publisher (`DefaultRequestHandler.java:934-955`); that behaviour is unexercised over HTTP.
Related: in scenario 7 the plain **streaming `sendMessage` route skips the stamp assertion entirely** (`:453`, `else if (!route.equals("streaming"))`), because the existing-task stream has no snapshot frame (handoff). The plan asks for reduced stamps through `sendMessage` streaming; that is only shown against the controlled peer (`Phase3JSONRPCPublicClientTest.streamingSendAndNoCursorObserve…`).

### F5 — Medium (weak guarantee): loosened null `throughGeneration` assertion
`Phase3MutationScenarios.java:450`: `assertTrue(through == null || through == stored.generation(), …)`. A `null` through-generation passes, so a regression that stops the observation reporting its safely interpreted generation cannot fail this check. The remaining assertion
(`:451-452`, last timeline entry stamp equals stored generation) is meaningful but is the only real oracle for the `streaming-observed` route, and it does not assert `view.sync()`. Either explain and assert *why* `null` is legitimate (no baseline frame on this route) and assert the corresponding `ViewSync`, or require the value.

### F6 — Medium (gap): inline-config timing and old-client terminal projection are not demonstrated
- "Immediately terminal inline config precedes notification eligibility": the test asserts only the **end state** `pushConfigCount == 1` (`:366`). The URL is `http://localhost:1/hook` (`:505`), nothing receives a notification, so ordering (config registered before the task becomes notification-eligible) is not observable. At best this proves the config is stored, not the timing.
- "Legacy full-Task shape is projected from committed results, never raw proposals": the terminal full-proposal scenario (`:347-370`) is read only by 1.1 clients. No 1.0/0.3 reader sees the projected result.
- "Final artifacts before closure" is satisfied for 1.1 (final artifact in the last view, `:364`).

### F7 — Medium (gap): mixed-version scenarios are one-directional and USER-artifact hiding is shallow
- 1.0/0.3 are writers; the 1.1 subscriber is the only streaming reader (`:283-330`). `older03…` asserts the 1.1 subscriber sees the 0.3 message, but never reads back through 0.3.
- USER-origin artifact hiding is asserted only for a 1.0 unary `getTask` (`:185-190`), not for 1.0 streaming/subscribe/list or any 0.3 read, and USER artifacts are not present in the older-writer scenarios.

### F8 — Low/Medium (weak oracle): malformed-input scenario
- `malformedInputsCauseNoPartialMutation` (`:193-222`) asserts `failure != null` and unchanged generation/history/artifact counts for four cases; it does not assert the error type, so a client-side failure, 404 or transport error passes (this is the same defect as F1.4).
- After the raw malformed requests the test re-checks only `base` (`:222`), not `other`, though the third raw element deliberately targets `otherTaskId` (`Phase3JSONRPCMutationTest.java` third `finalElement`). Cross-task leakage is what that case is designed to catch, and `otherBefore` is available.
- Raw cases assert JSON-RPC `-32602` correctly (`Phase3JSONRPCMutationTest.java`), good, but this is the only attributed error.

### F9 — Low/Medium (weak oracle): controlled-peer public-client tests
`Phase3JSONRPCPublicClientTest`:
- Guarded-input rejection after taint is asserted through `client.sendInput` only (`:91-93`). T16 asserts it through `sendInput`, `sendInputObserved` and `sendMessage` (`ExistingOrdinaryEntryPointReductionTest:506-511`); the plan says "alongside both input APIs".
- The malformed-known-payload test (`:186-209`) is named "through every public entry point" but exercises streaming `sendMessage` and no-cursor subscribe only (no unary), and asserts only `failure != null`. The fixture frame lacks `taskId`/`contextId`, so the cause of failure is not attributed to "malformed generation".
- Error envelope typing is asserted for subscribe only (`:203-208`), not for streaming send or unary.
- Unknown **timeline** variants are untested (only an unknown stream variant, `:161-181`). The `observation.cancel()` at `:176-180` happens after completion, so it only proves cancel is harmless once finished, not cancellation of a live uncertain stream (that is covered separately at transport level in `Phase3ClientGenerationTest.cancellingAHeldOpenObservation…`).
- Interceptor/wrapper behaviour for the new `observe` is not tested (production calls `applyInterceptors`, so I believe it is correct).

### F10 — Low: lost-publication scenario and recovery bounds
Simulated by a store-only `testUtils.saveTask` commit (`Phase3JSONRPCMutationTest.commitWithoutPublication`); the e2e test asserts closure within 15 s using the test profile bounds 200/1000/600 ms, not the shipped 5/5/10 s defaults, and only `awaitFailure` — it does not check the error is the documented `InternalError` resync message. Positive: it asserts no terminal event was invented (`:407`), child queue released (`:408-410`), one ingress for the explicit `GetTask` (`:416`), and generation/state unchanged (`:417-418`).

### F11 — Low: provenance/hygiene
- The frozen full-reactor log `/tmp/p3-t17-final-full2.log` was launched detached, **has no recorded exit code**, no command line inside it, and per the user an external watchdog was added after launch because no shell timeout was used. Maven printed `BUILD SUCCESS` and `Finished at 15:55:45Z`; that is the only completion evidence. I treat it as supporting, not definitive, and re-ran the relevant commands myself (below).
- `p3-t17-final-full.log` (cited in `progress-T17.md` evidence table) finished 15:40:13Z, before the final production-file restore at 15:44; the handoff discloses this and the later frozen run supersedes it. The empty `p3-t17-final-cmd2c.log` is a killed attempt (disclosed).
- Earlier overlapping reactors shared `target/`; I did not rely on any of them as final evidence.
- Style: `DefaultRequestHandler.java:465-467` uses `java.time.Duration`/`java.util.Optional` fully qualified although `java.util.Optional` is already imported (`:14`); redundant `value != null` for an `Optional`.
- Docs for the three new config keys were not added (plan assigns docs to T20; noted only).

### F12 — Info
- Only 4 of the 12 e2e scenarios fail at base production (`p3-t17-red-3-e2e-preserved.log`: 1 failure + 3 errors). The other 8 pass at base because they characterise behaviour already in core; they are regression coverage, not proof the T17 edits are needed. Plan-level probes beyond RED were not run (handoff says so).
- `JSONRPCHandler.rejectLegacyTimeline` is limited to `V1_0` as `rejectLegacyAdvancedInput` already is; no 0.3 policy reaches this handler.

### Verified-OK points (not findings)
- **Guard removal is exactly scoped.** Delivery-version (`taskPushNotificationConfig.protocolVersion`), `currentGeneration` long-poll, cursor `ObserveTaskParams.lastSeenGeneration`, and all selected-1.0 advanced-input gates remain: `JSONRPCHandler.rejectDeliveryVersion` (:945-951), `rejectUnsupportedQuery` (:973-982), `rejectLegacyAdvancedInput` (:953-963), `JSONRPCUtils.validateVersion10Input`/`hasAdvancedInput` (selected-1.0 `artifactUpdates`, `ifGenerationMatch`, `timelineLength`, delivery version all rejected at parse), `JSONRPCTransport.observe` rejects a cursor and 1.0 before any request. `JSONRPCHandlerTest.selected11DeliveryVersionLongPollAndCursorStayRejected…` and `selected10TimelineLengthOnGetAndListStaysRejected…` assert `verifyNoInteractions(customHandler)`.
- **Real bug fixed and evidenced:** `onGetTask`/`onListTasks` previously rebuilt params without `timelineLength` (`JSONRPCHandler.java:636-637`, `:690-691` now forward it). The new unit test `selected11TimelineLengthReachesCoreForSendGetAndList` fails at base (`red-2-preserved.log:369`) and the e2e race test fails at base for the same reason (`red-3` :1005).
- **No retry/rebase:** the race scenario counts ingress as exactly 2 (`:143`), one accepted, one `TaskGenerationMismatchError` (`:141-142`). `Phase3ClientGenerationTest` shows a single request for observe/cancel; client taint tests count peer requests (`Phase3JSONRPCPublicClientTest.java:94`).
- **Genuine public client + production route:** scenarios use `Client.builder(...)` with `JSONRPCTransport` and the Quarkus reference server over real HTTP/SSE; the controlled peers are used only for invalid-generation/unknown/malformed streams, as the plan prescribes.
- **Zero/equal/unknown/malformed (controlled):** zero and equal-generation from unary send, streaming send and no-cursor subscribe each lead to a local rejection with an unchanged peer request count and keep unguarded input allowed (`:77-103`); increasing generations keep the guard usable (`:106-123`); unknown variant keeps the view `INCOMPLETE` with `throughGeneration` held at 1 (`:161-181`); selected-1.0 advanced input rejected with 0 requests (`:212-225`).
- **No Phase 4/5 behaviour:** no cursor/live/long-poll gate opened; `observe` null-cursor only.
- **Lifecycle:** the reconciler cleanup in `PublicationReconciler`/`ChildQueue` was previously reviewed (T14); T17 only adds creation/closure.
- **Fixture usability for T18/T19:** reusable in principle (abstract hooks `drainIngress`, `commitWithoutPublication`, `storedTask`, `deleteStoredTask`, `childQueueCount`, `pushConfigCount`, `send03Message`, `assertRawMalformedFinalArtifactRejected`; shared `TestUtilsBean`, `AgentExecutorProducer` `phase3:` branches, `Phase3TestProfile`). Caveats: `drainIngress`/raw-malformed/0.3 hooks need REST/gRPC implementations; the tenant `phase1-2` case is registration-specific; and the missing scenarios in F1/F3/F4 are in the shared class.

## Acceptance-criteria mapping

| Criterion (plan T17) | Status | Evidence / gap |
|---|---|---|
| A03/A05/A07/A10, relevant A04/A06/A12 | Partly | see rows below |
| Public clients race same guard; one accepted, one typed mismatch; ingress counted; stamps via send/get/list and `currentView` | **Met** (unary only) | `Phase3MutationScenarios.java:109-155` (ingress `:143`); streaming race absent (F4) |
| Message + multiple artifacts; existing-task artifact-only | **Met** (unary only) | `:162-190` |
| Malformed final element, capability, role, ID, tenant/context cause no partial mutation | **Partly** | final element (raw, `-32602`) and artifact role met; capability, ID, tenant attribution, message role not (F1, F8) |
| Raw malformed supplements DTO-valid public-client cases | Met | raw in `Phase3JSONRPCMutationTest`; `other` not rechecked (F8) |
| Unary + streaming × both `returnImmediately`: direct Message, no task/config/publication/replication, queue cleanup | **Partly** | no artifacts, no publication/replication spy, vacuous cleanup (F3); task-bearing contrast and existing-task direct reply present |
| 1.0 and 0.3 writers share task with 1.1 subscribers; old clients preserve shape/finality, omit new fields/USER artifacts, progress under finite filtered demand | **Partly / not met for filtered demand** | F1.1, F7 |
| Legacy full-Task from committed results | **Not shown** | F6 |
| Inline terminal config precedes eligibility; final artifacts before closure; metadata-only status keeps message count | **Partly** | config timing end-state only; final artifact met; metadata-only met (`:372-383`) |
| Lost final publication closes stream within bound; explicit `GetTask` retrieves final state without mutation or resend | **Met for subscribe** | `:388-421`; streaming send paths unprotected (F2) |
| Controlled zero/equal generations disable reliability and stop guarded I/O | **Met** | `Phase3JSONRPCPublicClientTest:77-103` (callback `sendInput` only, F9) |
| Unknown stream/timeline variants retain uncertainty/cancellation; malformed known/error envelopes fail | **Partly** | unknown stream met, unknown timeline absent; malformed/error attribution weak (F9) |
| Selected 1.0 advanced inputs reject locally without switching version | Met | `:212-225` plus parse-time gates |
| Remove only implemented ordinary guards; retain 1.0/live/cursor/long-poll/delivery-version gates | **Met** | see Verified-OK |
| `timelineLength` send/get/list enabled | Met (unary) | handler tests + race scenario `:134-141`; streaming `timelineLength` not e2e |
| Existing-entry-point matrix (sendMessage unary/streaming, subscribe: reduced stamps, counts, zero/equal taint → local guarded-input rejection; both input APIs; wrapper interception; explicit 1.0) | **Partly** | taint matrix met via `sendInput` only; e2e stamps for streaming route skipped (`:453`), `throughGeneration` loosened (`:450`) |
| Built-in `observe` for null cursor only; cursor unsupported | Met | `JSONRPCTransport.observe`; `Phase3ClientGenerationTest` |
| Shared helper usable for T18/T19 | **Partly** | F1/F3/F4 gaps would be inherited |
| Paired reserve/release, version-aware validation, no Phase 4/5 | Met | F2 verified-OK note |

## What was and was not checked

Checked: all of the above by reading source and diff; the committed hashes; the plan commands and extra suites listed below, run by me.
**Not checked / not done:**
- No fresh run of the full multi-module reactor with every test (I ran targeted suites plus REST/gRPC multiversion; the implementer's `server-common` 984 and the frozen full-reactor log are cited, not reproduced).
- Extras modules (JPA task store, replicated queue manager, Kafka/Docker-dependent tests, OTel) were not run; JPA store with CDI wiring is untested by anyone (F2).
- I did not mutate production code to probe tests (read-only review); I did not re-run the base (`d869e957`) to compare REST/gRPC skip counts. I did record the **skip reasons** on the candidate: the REST and gRPC multiversion skips are `Assumption failed: JSONRPC-specific test` (18 each) plus four `@Disabled` auth tests in gRPC; none are in files touched by T17.
- No manual exercise of `ROLE_AGENT` input, the disabled-capability card, blank `messageId`, streaming guard/artifact-only, or old-client filtered demand over the real server — these are listed as missing, not claimed broken.
- Nested subagents, Serena/MCP, push: none used.

## Test and evidence provenance

My runs (all `-B -ntp -Dmaven.repo.local=/tmp/p3-t17-review-m2`, candidate working tree, exit codes from `.exit` files):

| Command | Result | Log / exit |
|---|---|---|
| Plan cmd 1: `-pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest -Dsurefire.failIfNoSpecifiedTests=false test` | BUILD SUCCESS, 23 s. client/transport/jsonrpc 26 (Phase2PublicClientTest 19, Phase3ClientGenerationTest 7), 0 fail/err/skip. `JSONRPCHandlerTest` 80 run, 0 fail, **1 skipped = pre-existing `@Disabled testDefaultRequestHandlerWithCustomComponents`** (`JSONRPCHandlerTest.java:1657`, also `@Disabled` at base `d869e957`) | `/tmp/p3-t17-review-cmd1.log`, exit=0 |
| Plan cmd 2: `-pl tests/multiversion/jsonrpc -am -Dtest=Phase3JSONRPCMutationTest -Dsurefire.failIfNoSpecifiedTests=false verify` | BUILD SUCCESS (finished 16:02:15Z). `Phase3JSONRPCMutationTest` 12 run, 0 fail/err/skip, 8.7 s | `/tmp/p3-t17-review-cmd2.log`, exit=0 |
| `-pl client/base,reference/jsonrpc -am -Dtest=Phase3JSONRPCPublicClientTest,ExistingOrdinaryEntryPointReductionTest,Phase2BoundaryTest …test` | BUILD SUCCESS (16:03:30Z). `Phase3JSONRPCPublicClientTest` 11, `ExistingOrdinaryEntryPointReductionTest` 31, `Phase2BoundaryTest` 5, 0 fail/err/skip | `/tmp/p3-t17-review-cmd3.log`, exit=0 |
| `-pl tests/multiversion/rest,tests/multiversion/grpc -am verify` (regression of the global reconciler wiring on other transports; includes `server-common` 984, reference REST/gRPC suites, compat-0.3 suites) | BUILD SUCCESS, 6 min 12 s (16:09:49Z). `server-common` 984/0/0/0; multiversion REST 146 (18 skipped), gRPC 131 (22 skipped); reactor total skips = 3 upstream `VertxA2AHttpClientSSETest` + the JSON-RPC-specific/`@Disabled` ones above; 0 failures/errors | `/tmp/p3-t17-review-cmd4.log`, exit=0 |

These reproduce the implementer's counts (cmd1 26/80+1, cmd2 12, client/base subset, REST 146/18, gRPC 131/22). The `Phase3JSONRPCPublicClientTest` took 14 s because of fixed quiescence sleeps (`awaitQuiescent`), which is slow rather than flaky.

Implementer evidence I **inspected but did not rely on as final**: `/tmp/p3-t17-final-cmd1c.log` (exit/26/80/1, 15:51:20Z), `/tmp/p3-t17-final-cmd2d.log` (12/12, 15:50:35Z), `/tmp/p3-t17-final-full2.log` (BUILD SUCCESS, 15:55:45Z, 4 min 21 s; module 151 tests 0 fail/err/skip; reactor skips 3 upstream Vert.x + 1 pre-existing `JSONRPCHandlerTest`; `server-common` 984), `/tmp/p3-t17-red-2-preserved.log` (5 errors + 2 failures at base), `/tmp/p3-t17-red-3-e2e-preserved.log` (1 failure + 3 errors of 12). Chronology is consistent: the frozen hashes were computed 15:46, verified 15:50, the full run finished 15:55:45 and the commit is at 15:56:28 from those same hashes. Deviations noted: detached launch without an exit-code file and an after-the-fact watchdog (F11). The one disabled `JSONRPCHandlerTest` is pre-existing and distinct from required skips; the REST/gRPC "JSONRPC-specific" assumption skips are by design.

## Required before acceptance (for the separate Bob correction session / developer)

1. **Developer decision (F2):** default-on vs opt-in vs scoped reconciler wiring; if kept, add an opt-out and tests (defaults, each key, invalid value, `@PreDestroy`, non-`AtomicTaskStore`), and decide whether streaming send paths need the same bound or the limit is documented.
2. Add the missing shared scenarios to `Phase3MutationScenarios` (F1, F3, F4, F6, F7): finite-filtered-demand progress for 1.0/0.3 subscribers; disabled `userArtifacts` capability; blank `messageId`; attributable tenant/context errors; direct Message **with artifacts** plus a publication/replication spy and a non-vacuous cleanup check; streaming guard/artifact-only/message+artifacts/`timelineLength`; 1.0/0.3 reads of terminal full proposals and USER-artifact hiding; inline-config ordering that is observable.
3. Tighten F5 (`through == null`), F8 (assert typed error; recheck `other`) and F9 (guarded rejection via `sendInputObserved`/`sendMessage`; typed attribution for malformed payloads; unknown timeline variant).
4. Decide the message-role question (F1.5) explicitly, with a test either way.
5. Record the T17 fixture freeze only after the above.

## Final disposition

**Changes required.** Not "Incomplete evidence" because the evidence that exists is internally consistent and reproduced; the missing items are required scenarios and a developer decision, not unverifiable claims. No claim is made that the implementation is broken: I found no concrete defect in the changed production lines, and the 1.0/live/cursor/long-poll/delivery-version gates, T13 pairing and the `timelineLength` forwarding fix check out.

Reviewed within the assignment window (started 15:58:00 UTC, ended before 17:58:00 UTC); no extension used.
