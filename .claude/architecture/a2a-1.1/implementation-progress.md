# Current Phase 2 checkpoint — through T6b, 2026-11-13

The user requested a local checkpoint commit for the accumulated Phase 2 work through accepted T6b REST. **Phase 2 remains incomplete: T6c gRPC and T7 are pending.** Existing specification gates remain unchanged. This authorization supersedes older no-commit statements for this checkpoint only; no push, PR or Phase 3 is authorized.

Fresh commit verification (raw Maven **181**): **35 selected reactor modules successful; 610 discovered / 609 executed / 1 unchanged disabled test / zero failures or errors**. This is focused checkpoint evidence, not a full-reactor or final compatibility claim. See the [commit checkpoint and retained evidence](phase-2/checkpoints/through-t6b-commit.md) and [T6b acceptance review](phase-2/reviews/t6b-final-review.md). Historical ledgers and failures below remain preserved.

---

# Historical Phase 1 checkpoint — committed locally

Phase 1 commit: **`4efaf334e485af5d80dda39c35c6e635e2c11b1a`** on `support/a2a-1.1.0`. Accepted Phase 0 parent: `e468dd70a415d17c19ae70d75750b6464d983ad1`.

The user explicitly approved this commit with **“YEs, commit”** after reviewing the concrete scope, evidence and message. The commit contains all 394 approved paths, including 156 unchanged functional inputs and retained architecture/verification evidence. Its message ends with `This fixes #1303`.

The reviewed complete build has 83 successful modules, 4,041 discovered / 3,918 executed / 123 unchanged skips / zero failures or errors. Independent review has no blocking findings within the experimental Phase 1 scope. All committed functional hashes match that reviewed build. Remaining verification limits and specification gates are retained in the handoff and follow-up register.

All five additional iterations are closed (17 cumulative Phase 1). Historical Phase 0 accounting, the original breach and prior F1 failures remain preserved. No implementation continuation, push, PR or Phase 2 is authorized. This resulting-SHA receipt is the sole post-commit documentation change; it is deliberately outside the commit whose identity it records.

Current documents: [verification](phase-1/verification.md), [independent review](phase-1/evidence/resume-final-independent-review.md), [approved proposal](phase-1/commit-proposal.md), [handoff](phase-1/next-session.md), [follow-ups](phase-1/follow-ups.md). Earlier pending/stop/readiness statements below retain their dated historical meaning.

---

## Historical progress ledger (preserved)

> Current checkpoint: **Phase 1 incomplete; no commit approval requested.** The user explicitly approved the SDK-owned Netty channel/API/backend/lifecycle design for both 1.0 and 1.1. Its partial implementation passed 14 focused tests, but independent review found an admitted global load-balancer tracer can still mutate metadata after the SDK assertion. A newly allocated provider channel can also leak on descriptor-validation failure. Existing raw-channel/factory callers and tests still require migration. Stronger zero-server-stream assertions added after the focused build remain unrun. Earlier readiness/design-pending statements below are historical and superseded.
>
> Correction iteration 4 is closed: **4/4 additional iterations, 12 cumulative Phase 1; no-progress 0**. The owned attempt is conservatively the third materially failed F1 implementation; **the three-attempt safeguard stops further F1 work**. This is a source-established design failure, not a third executed failing probe. The original correction deadline remains 2026-11-10T18:33:12Z. Further implementation requires an explicitly authorized revised approach and renewed budget; material design approval already granted does not reset either limit. No commit, push, PR or Phase 2 work occurred.

# A2A 1.1 implementation progress

> Current correction checkpoint: **Phase 1 incomplete**. REST F2 and native 0.3 F3
> are corrected and reviewed; strict gRPC F1 remains open with a failing test.
> The [owned-channel design](phase-1/grpc-channel-design.md) requires explicit
> compatibility/lifecycle/backend approval before implementation. No commit is
> approved. The [handoff](phase-1/next-session.md) and appended closure govern
> current work; Phase 0 sections below preserve their historical accounting.

## Historical Phase 0 run control

- Scope: **Phase 0 only** in `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, HEAD `2817ed78d02d9a6e609dafdf7f5129484edef017`. Original upstream base `b96e0b3f21927eb551881232c69acf077ebdcbc4`. No replacement branch/reset.
- Original run start **2026-11-10T13:16:00Z**, deadline **15:16:00Z**, maximum **10 focused iterations / two hours**. A continuation does not grant a fresh run. Completed iterations **8/10**; no active iteration remains. Iteration8 closed after independent retained-artifact review/packaging. No-progress counter **0**.
- Original three-attempt safeguard was breached by historical harness work; the historical breach is retained. The user subsequently said the limits were arbitrary and Phase 0 must be finished, then explicitly answered **“Yes, resume and finish Phase 0 checks”** to a revised staged-verification proposal. This replaces the earlier A03 stop for that approach, not the prior history. It does not authorize repeated unchanged failures, Phase 1, commits, push or PR.
- Time/iteration budget exhausted: **false**. Historical A03 cap exceeded: **true**. Current A03 stop superseded by explicit follow-up authorization; both resumed checks passed without a harness behavior fix, failure or retry. No additional runtime check is scheduled merely because time remains.
- Command bounds: min(20 minutes, remaining run budget), plus at most 30 seconds controlled cleanup. Resumed probes used tighter **180-second** bounds; their internal javac/java subprocess bounds are 60 seconds.
- Architecture: revised public client/server scope and recommended contracts authorized for Phase 0. Independent client/contract review and its mandatory corrections are retained. No explicit production architecture approval or Phase 1 authorization is recorded. Approval is unnecessary for routine planning names or recording already selected recommended SDK policies.
- No root build/probe process remains live. One root build only. No production, test, POM or generated-source edits; no commit, push, PR or external GitHub mutation.

## Phase state and exit scope

**Phase 0 deliverables verified within documented research/baseline scope; explicit dependent normative gates remain.** Final independent retained-artifact review passed with no important findings remaining. Its deliverables are pinned research, explicit selected SDK policies and unresolved dependent gates, unchanged-code baseline, frozen historical regression checks, client/API/SPI inventory and concrete next-phase plans. Phase 0 explicitly requires recording missing receipts as dependent gates and producing a minimal HTTP trace/decision request when undefined. It does not require inventing a missing protocol rule or passing all final A01–A14 criteria before implementation begins.

This is not a claim that every normative gate is closed, the original execution was fully compliant, or 1.1 is implemented/conforming. Phases 1–6 are **not started**. The earlier incomplete checkpoint remains under [historical checkpoint](phase-0/evidence/checkpoint-1409.md); its unverified0.3/stop/noauthorization observations are superseded by the explicitly authorized results below.

## Attempt counters and prior breach

Cumulative conservative **A03 audit counter=10**: prior **8** plus **2** resumed verification groups. The prior8 comprise seven worker-reported audit entries plus its already-completed first0.3 capture. Entry5 overlaps comparison4; entry6 recaptures/diffs without a new fix. Thus neither8 nor10 means that many independently proven distinct fix/verify cycles. Successive extraction/harness fixes still exceeded the three-attempt limit, and overlap does not erase the breach. [Exact audit](phase-0/fixtures/attempts.md).

At the first checkpoint the parent interrupted the worker, informed the user and permitted packaging only. The0.3 capture had already completed before STOP and was then retained as unverified. No execution resumed until the user's explicit subsequent authorization. No raw fixture, derived input, existing1.0 expectation or harness behavior changed for the resumed checks. Original0.3 archive bytes were promoted only after an independent new execution matched them. Both checks passed; no recurring failure was retried.

All production implementation attempt counters remain zero. Other per-criterion implementation counters **A01=0,A02=0,A04=0,A05=0,A06=0,A07=0,A08=0,A09=0,A10=0,A11=0,A12=0,A13=0,A14=0**. Root A14 baseline verification count=1; initial document-review correction/recheck round=1 (A04/A09), followed by one authorized Phase0 clarification/exit review. The proposed two-no-progress safeguard was not separately approved; no such stop was reached.

## Focused iteration ledger

| Iteration | Targets | Evidence/result | State |
| --- | --- | --- | --- |
| 1 | A02/A05/A09/A10/A11/A14 | Official tag/binding/PR research, six gates, outcome matrix and minimal HTTP ambiguity trace. | Complete research; residuals explicit. |
| 2 | A03/A14 | One bounded root build passed; historical fixture worker exceeded attempt cap; first0.3 capture unverified at stop. | Stopped partial, breach retained. |
| 3 | A02/A04/A07/A09 | Construction/defaults/SPI/both telemetry wrapper families/reducer/decoder/private factory/API impact inventory. | Complete inventory; no1.1 runtime claim. |
| 4 | A04/A09/A14 | Independent review; generation reliability and unknown timeline accounting corrections, artifact preservation, handoff. | Complete; R1/R2 addressed, R3 retained. |
| 5 | A11/A14 | User-requested issue search/read found exact OPEN A2A#2415; snapshots retained; no duplicate/write. | Complete; G5 unresolved. |
| 6 | A02/A05/A07/A09/A10 | Reconciled already selected discovery/message-at1/origin policies and cursor/recovery recommendations with architecture; corrected audit wording. | Complete; [selected policies](phase-0/selected-policies.md). |
| 7 | A03/A14 | Froze/hashed unchanged runtime away from targets;1.0 matched33 positives+1 known failure;0.3 matched original24 positives+2 known failures. | Complete selected codec baseline; no complete A03 claim. |
| 8 | A03/A04/A09/A14 | Independent Phase0 exit/scope/evidence review, current-state reconciliation and static final integrity. | Complete; final independent verdict passed, static integrity verified. |

## Specification decisions and dependent gates

- Target: experimental `v1.1.0-rc` at `78e1d6a1f7f118c8e9bc830b04a8579c1119cb78`, fresh tag/release/PR checks13:30:15–16Z. Final1.1 not found. PR2411 OPEN at `d318e2e074bcc967ce5f09b746304a72a9794f03`; its artifact-key body disagrees with its diff. An open proposal is not target authority.
- Selected discovery SDK policy: explicit exact0.3/1.0/1.1 entries, immutable exact client selection, no fallback/latest-minor expansion. Default new shared client1.1; explicit1.0 remains1.0; legacy CURRENT_PROTOCOL_VERSION remains1.0. Latest-minor wording tension stays a conformance clarification.
- Selected SDK bootstrap: accepted opening message at generation1, opening artifacts and separately emitted status next; baseline required status is not a fabricated status event; opening snapshot is not another mutation. [Precise trace](phase-0/selected-policies.md#g4-bootstrap-explicit-recommended-trace). Upstream first-append ambiguity is retained before normative/final claims.
- Artifact key: artifactId alone within task. Selected SDK policy preserves creator origin, assigns omitted origin from ingress, rejects explicitly inconsistent role and defaults to cross-origin edit rejection. No composite key, invented error or universal wire mandate. RC origin/collision clarification remains explicit.
- Cursor/recovery: retained replay/no opening Task, absent/unavailable snapshot fallback, ordinary terminal rejection; ordinary prose versus cursor proto conflict and live-terminal/recovery interaction boundaries remain. Experimental recommended profile is concrete; it does not close the normative conflict. Never fabricate generation or silently exempt same-generation snapshots from disablement.
- HTTP live: no defined opening/uplink discriminator or same-task/downlink selector; exact artifact/batch outcomes lack general correlation. No invented header/session field/status RPC/idempotency/executor receipt. User requested spec issue; exact existing OPEN [A2A#2415](https://github.com/a2aproject/A2A/issues/2415) covers it and supplies no resolution. No duplicate/comment created. Earlier A2A-Version answer concerns version, not exchange identity.
- [Required client rules](phase-0/required-client-rules.md): zero/equal generations disable generation use for the interaction; unknown timeline entry's known generation counts for sequence detection independently from semantic TaskView uncertainty. Keep finite uncertain outcomes/no unsafe retry.

[Gate/dependency register](phase-0/decisions.md), [primary source evidence and per-input/binding outcome matrix](phase-0/spec-gates.md), [HTTP trace](phase-0/http-live-ambiguity.md), [concrete selected policies](phase-0/selected-policies.md) and [API/SPI inventory](phase-0/client-inventory.md) define what later work is blocked. These records complete investigation; they do not manufacture missing guarantees.

## Baseline command and result

```sh
java -version
mvn -version
python3 /tmp/a2a-phase0-run/bounded.py 1200 baseline-full mvn -B -ntp clean install
```

Temurin17.0.18/Maven3.9.14; parent release17. Started13:19:16.151765Z, ended13:29:13.361176Z,597.21seconds, exit0, no timeout; **83 reactor modules SUCCESS**. Process leader67363/session70023 consumed/closed; no duplicate build.

**248 reports:247 Surefire+1 Failsafe;3844 discovered,3721 executed,123 skipped,0 failures/errors.** Failsafe includes9 OTEL integration tests. JPA58 (Postgres5/MySQL6), Kafka single-instance5 and actual multi-instance2 all ran without skips. [Exact suites/skips](phase-0/evidence/baseline-test-summary.json), [log](phase-0/evidence/baseline-full.log.gz), [metadata](phase-0/evidence/baseline-full.json), [coverage limits](phase-0/baseline.md). Existing topology only; not1.1 authority/live proof. Building TCK SUT is not running external target TCK/certification.

## Historical regression checks — authorized follow-up

The unchanged successful baseline SDK artifacts were copied away from reactor targets before the checks. [Runtime manifest](phase-0/evidence/historical-runtime-manifest.json) pins39 unique classpath entries/586 files; [SDK archive](phase-0/evidence/historical-sdk-runtime.tar.gz) retains8 SDK entries. Third-party jars are SHA256-pinned but not bundled. This is pre1.1 HEAD2817ed78 SDK binary provenance, not an assertion of published-release or full live-peer execution.

```sh
python3 /tmp/a2a-phase0-run/freeze-runtime.py
python3 /tmp/a2a-phase0-run/bounded.py 180 historical-1.0-followup python3 /tmp/a2a-phase0-deliverable/fixtures/run.py --version 1.0 --classpath-file /tmp/a2a-phase0-frozen-runtime/classpath-1.0.txt
python3 /tmp/a2a-phase0-run/bounded.py 180 historical-0.3-followup python3 /tmp/a2a-phase0-deliverable/fixtures/run.py --version 0.3 --classpath-file /tmp/a2a-phase0-frozen-runtime/classpath-0.3.txt --capture /tmp/a2a-phase0-03-followup
python3 /tmp/a2a-phase0-run/verify-original-03.py
```

1.0 at14:18:39Z: exit0,0.704seconds, no timeout; **33 positive comparisons+1 known-failure fingerprint** against existing frozen expectations. 0.3 at14:18:43Z: exit0,0.508seconds, no timeout; follow-up comparison14:19:02Z matched original pre-STOP archive's exact26-file set: **24 positive comparisons+2 known-failure fingerprints**. JSON compares semantically; protobuf compares bytes. Original archive SHA256 `952804a568a7938a9450379167decaa27d7523eb91008b1527a1861a75fef422`; expected/0.3 contains its original bytes, not recaptured replacements.

[1.0 metadata](phase-0/evidence/historical-1.0-followup.json)/[output](phase-0/evidence/historical-1.0-followup.log), [0.3 metadata](phase-0/evidence/historical-0.3-followup.json)/[output](phase-0/evidence/historical-0.3-followup.log), [comparison](phase-0/evidence/historical-0.3-comparison.json), [provenance/commands](phase-0/fixtures/README.md). Raw trailing commas and historical null-metadata/kind failures remain unchanged and separate from positive passes. These codec regressions can detect shared-model changes; full final A03 all-binding lifecycle/error/history/blocking/webhook coverage remains an implementation acceptance obligation.

## Acceptance state

**No full A01–A14 criterion is passed by Phase 0.** A01/A02 interface provisioning/client behavior, A04 executable API/SPI binary callers, A05/A06/A08 mutation/authority/migration, A07/A10 projection/artifact inputs, A09 observation, A11 live, A12 webhook versions and A13 future1.1 topologies are not implemented. A03 has frozen prechange regression evidence, not the full future compatibility matrix. A14 has the Java17 root baseline and experimental pin, not final docs/target TCK/final-spec reconciliation. See gate register for precisely blocked dependent work.

## Preservation and next action

At the verified Phase 0 closure, seven initial user-authored files were byte-for-byte preserved (README,compatibility,spec-delta,validation,acceptance,architecture,implementation-loop); acceptance.md and implementation-loop.md were subsequently amended under the explicit user instruction below, with their before/after hashes retained; [initial hashes](phase-0/evidence/initial-user-files.json). New work is only this progress file and phase-0/. Root POM includes multiversion modules directly; no tests-directory aggregator. Repository inspection and the root build targeted the requested worktree; follow-up probes had the main checkout as their shell working directory and used explicit frozen/tmp runtime paths, without writing to the main checkout. Fsmonitor was disabled per Git invocation after the initial IPC warning, without Git config mutation.

Final independent retained-artifact review and static integrity are complete. Seven initial document hashes,99 local links (including the final review/handoff links),226 fixture hashes,198 preserved original non-document fixture hashes,18 upstream hashes,26 original0.3 expectation files and555 SDK snapshot file hashes passed. `git -c core.fsmonitor=false diff --check` exited0. See [integrity result](phase-0/evidence/final-integrity.json). Read [exit review](phase-0/exit-review.md) and [next-session handoff](phase-0/next-session.md). No more build/probe is justified absent a new failure or artifact change. Do not start Phase1 automatically. Later production work needs explicit authorization and revised client architecture approval; unresolved binding gates remain binding for their dependent implementation.

Final closure: **2026-11-10T14:24:56+00:00**, elapsed **4136 seconds**, **8/10 completed iterations**, no-progress **0**, cumulative conservative A03 audit **10** (prior8+2 authorized successful verification groups), other implementation counters0. Original time budget remaining at closure **3064 seconds**; time/iteration exhausted **false**. Original attempt breach remains **true** and explicitly followed by user-authorized revised verification; do not describe the historical execution as fully compliant. Stop reason: **authorized Phase0 research/baseline deliverables verified**; Phase1 remains unauthorized and unstarted.

## Subsequent user instruction: phase commits and fresh-agent handoff

After the final external review, the user requested that Phase 1 run in a fresh
agent and that each completed phase have a commit requiring the user's approval.
Updated implementation-loop.md, acceptance.md and the next-session handoff to
require a concrete phase commit proposal, the repository issue-association
question, explicit user approval before each commit, and the approved commit
before beginning the next separately authorized phase. Architecture or execution
approval does not authorize a commit. Phase 1 remains unstarted here.

**Phase 0 commit approval: subsequently granted**, as recorded below. The
instruction-update request alone did not authorize a commit; the user then
explicitly requested committing everything. Phase 1 remains unauthorized here.
Historical run accounting remains **8/10 iterations, A03=10, no-progress=0**;
no budget reset or new implementation iteration is inferred. The prior baseline,
probe results and execution-limit breach are unchanged. The original input/hash
and final-integrity reports describe Phase 0 closure before this authorized
amendment; do not misinterpret the deliberate instruction edits as preservation
failures. See [authorized amendment hashes](phase-0/evidence/instruction-amendment.json).

## Phase 0 commit approval and handoff

The user explicitly requested **“ok, please commit”**, clarified **“we should
commit everything”**. After initially answering **“No related issue”**, the
user corrected the issue association to [a2aproject/a2a-java#1303](https://github.com/a2aproject/a2a-java/issues/1303).
Approved scope is all current changes: the revised
architecture/plan, Phase 0 research, baseline evidence, frozen fixtures, reviews,
progress and phase-commit approval instructions. All272 files are under this
A2A1.1 architecture directory; no production implementation is included.

Approved Conventional Commit message:
`docs(a2a-1.1): record reviewed Phase 0 baseline and implementation plan`

Issue footer: `This fixes #1303`, per AGENTS.md. This associates the phase commit
with the overall feature issue; Phase 0 remains research/baseline work and does
not claim implementation of all 1.1 behavior.

This entry is included in the approved Phase 0 commit. Its resulting SHA is
reported after creation; the fresh agent must record that literal SHA in its
first progress update, without amending the Phase 0 commit merely to insert
its own hash. Verify HEAD/status before beginning any separately authorized
Phase 1 work. Existing counters and specification gates are unchanged.

Precommit checks: all226 fixture hashes match. Authored-file staged whitespace
check passes. The full staged check reports only preserved blank EOF lines or
trailing spaces in immutable raw fixtures/historical source copies/upstream
snapshots; no frozen bytes were reformatted. Earlier initial-input and
instruction-amendment hash records describe their respective snapshots;
subsequent approval/handoff entries are intentional current progress updates.


## Phase 1 authorized run — 2026-11-10

- Start: **2026-11-10T14:57:25Z**, deadline **16:57:25Z**; authorized maximum **two hours / ten focused iterations**. First inspection confirmed branch `support/a2a-1.1.0`, clean worktree (fsmonitor disabled per invocation), accepted Phase 0 commit **e468dd70a415d17c19ae70d75750b6464d983ad1**.
- Approval reference: current user explicitly authorizes Phase 1 and architecture recommended contracts, including revised client scope. No commit, push, PR, material API/SPI break or later phase authorized. Related SDK issue already confirmed **#1303**. Commit approval remains pending.
- Historical accounting is preserved above: **8 completed Phase 0 iterations, conservative A03 audit 10, no-progress 0; original attempt-limit breach true**, followed by explicitly authorized successful checks. This fresh Phase 1 budget does not erase that history or manufacture complete A03 acceptance.
- Phase 1 counters: **0/10 completed**, iteration 1 active (A01/A02/A04 inspection and focused planning); no-progress **0**, budget exhausted **false**. Implementation fix/verify counters initially zero; selected frozen baseline will not be rerun without an actual change requiring it. Three-attempt safeguard and command deadline min(1200 seconds, remaining run time), plus <=30 seconds controlled cleanup, apply.
- Scope: explicit experimental registrations/exact routing, immutable client default1.1/explicit1.0, version propagation, capabilities/both telemetry wrapper families, paired public clients and provisioning/compatibility checks. Legacy CURRENT_PROTOCOL_VERSION remains1.0; shared model convenience defaults remain separately assessed. No generation/timeline/observation/mutation/live implementation.
- Inputs: inspected applicable AGENTS, branch/HEAD/status and accepted Phase0 handoff; authoritative README, architecture, spec-delta, compatibility, validation, acceptance and requested progress/Phase0 records read. Normative gates remain binding, especially HTTP live association, exact artifact/batch outcomes, cursor/recovery and final-spec reconciliation. Target stays experimental RC `78e1d6a1f7f118c8e9bc830b04a8579c1119cb78`; PR2411 is proposal only.
- Skills: using-superpowers, brainstorming (existing architecture approval satisfies design gate), using-git-worktrees (existing isolated worktree verified), writing-plans/executing-plans, dispatching-parallel-agents for separate server/client-wire scopes, TDD, verification-before-completion and requesting-code-review. User limits/no commits override conflicting generic skill steps. Root owns progress, selection/SPI and integration; scoped agents inspect server and transport/wrapper areas, return plans before edits, and coordinate build ownership.
- Initial command: `gh issue view 1303 --repo a2aproject/a2a-java --json number,title,state,body,url` failed due sandbox network access, without external mutation. Issue association is already supplied; read-only retry may use authorized escalation.

### Phase 1 iteration 1 — A01/A02/A04 inspection and plan

Completed current-code inspection, accepted P1 contract mapping and disjoint implementation ownership. Focused plan: [implementation-plan](phase-1/implementation-plan.md). Decisions: preserve AgentInterface model convenience defaults1.0; new client default1.1 is explicit in construction; provider/transport companion initially exposes only implemented ORDINARY capability. Optional client endpoint/tenant selectors are additive. Server registration and card descriptors share one source; no new model schema or module. Existing worktree verified and untouched by setup; unchanged baseline not repeated. Iteration1 complete; **1/10 completed**, no-progress0. Iteration2 active: immutable selection/capability tests and implementation (A02/A04), server and transport scopes prepare disjoint RED tests.

### Phase 1 iteration 2 — A02/A04 selection and emission boundary

Complete focused advancement: selection RED2/2failed on old first-binding/version behavior; helper absent-API RED; candidate pure policy3/3 and selection/capability9/9 passed in `focused-policy-green-server-red`. JSONRPC/REST11-operation HTTP emission tests and actual inprocess gRPC metadata capture reproduced old1.0 constant, duplicate/conflicting values and I/O before rejection; then selected-version/header tests passed. Both telemetry companion tests reproduced missinginterface before implementation. Constructor/model decision: CURRENT_PROTOCOL_VERSION and model convenience descriptor defaults remain1.0; client config/direct implicit constructor default1.1 independently. Only ORDINARY capability is claimed, not full1.1. No API method removed. **2/10 iterations complete**, no-progress0.

### Phase 1 iteration 3 — A01/A02/A04 registration, review and corrections (active)

Server registration/policy7 tests, router7 tests, sharedgrpc2 tests and actual RESTerror1test passed in `server-green-legacy-grpc-red`; explicit7-subset unitpolicy and corrected1.1 vs frozen1.0 mappings established. Additional router missing03adapter test reproduced misconfiguration not failing; old03grpc newresolver extension valid absent-API RED captured in `legacy-grpc-api-red` after correcting test-only missingMockito fixture. These are separately named requirements, not silent repeated unchanged baseline checks.

First independent client review `/tmp/a2a-phase1-run/client-review.md` found: tracing innerclass accidentalfields (compilefailure); demonstrated explicit1.0 JSONRPC rootURL+tenantparam regression (404); directgrpc cardconstructors skipped exactbinding/version/tenantselection; malformed transportcapability getter bypassed cleanup. Fixes preserve old1.0 expectedURL; directgrpc additive static forInterface avoids ambiguous oldnullable4arg source call. Malformed declaration RED7tests1failure(NPE vs A2AClientException), then percomponent diagnostic/cleanup correction. Public-builder legacy provider cases added with classfiles compiled using11 copied Phase0-installed SDK jars (before any Phase1 install), unchanged-source compile provenance and original frozen1.0 response bytes; no Phase0 expectation changed.

Implementation verification accounting (stable scenarios): P1.client-selection/capability1successful cycle; P1.client-version-emission1successful focusedcycle; P1.JSONRPC-descriptor-tenant1successful focusedcycle; P1.JSONRPC-demonstrated1.0requesttenant1failed fullsuite cycle, correction pendingverification; P1.OTELtracing candidate1failed compilation due accidental innerfields, correctionpending; P1.grpc-direct-card firstimplementationpending after5expectedRED failures; P1.malformed-capability firstimplementationpending after expectedRED; serverinitialregistration/errorpolicy firstcyclepassed. No scenario has three failing implementation attempts. Harness/testsetup compilation mistakes are retained in commandlogs, not hidden as passing behavior. All local commands bounded600seconds, none timedout. Root coordinated one reactor at a time, reusing processhandles; no agent Maven/build process.

Current authorizations and scope unchanged. Fresh Phase1 deadline16:57:25Z, historical Phase0 counters8/A03conservative10/breach retained. No-progress0, budgetexhaustedfalse. No commit/push/PR or Phase2. Current client fix verification live session89674; logs/metadata under /tmp/a2a-phase1-run, to be retained at handoff.


### Phase 1 iteration 3 closure / iteration 4 — paired runtime and review corrections

Iteration3 complete: `client-review-green` ended15:14:54Z, exit0, all16 modules SUCCESS. Client/base59, JSONRPC50, grpc29, REST51, SPI37, tracing28, propagation4 discovered; HTTPVertx64 with5 existing skips. This verifies the explicit1.0 tenantURL fix, directgrpc exactcard selection and malformedcapability cleanup. Independent client source review has no open finding in that snapshot. Extended old-wrapper fixture checks follow separately; no claim that every A03/A04 criterion is complete.

`server-regression-check` ended15:19:07Z, exit1: new/core policy passes, grpc64/REST90 passed;4 referenceJSONRPC tests exposed null optionalresolver fixture compatibility and2 referenceREST tests exposed bypass of the old virtual error-mapper hook. Production corrections preserve legacy tests unchanged. `paired-first-verification` ran15:20:13–15:22:55Z (162.21s), exit1, no timeout. New/default1.1 and explicit1.0 ordinary public operation matrix reached real servers. Remaining failures: staleJSONRPC1.1 rejection loses requestid; REST stale1.1 lacks ErrorInfo; two REST03-card route bypasses; eight gRPC whitespace-only wire cases rejected by HTTP2 before application dispatch; two authenticated gRPC profiles cannot start because the startup observer is intercepted by class-level authentication. Raw whitespace wire rejection will be recorded separately from direct application-boundary normalization, rather than changing the protocol or claiming the handler received it.

Independent server review identified four P1 findings: REST03-card gating, version-aware REST predispatch errors, HTTPcustomfactory version override and grpc extendedcard registration bypass. First two reproduced in paired runtime; latter three focused tests reproduced actual runtime failures in `server-review-red` (36JSONRPC1fail,84REST1fail,4grpc1fail), ended15:23:40Z, exit1. Legacy nullresolver and oldmapper-hook failures no longer occur. Scoped fixes now authorized by this evidence; no old assertions changed merely to accept regressions. Additional JSONRPC requestid and authenticated startup corrections are part of the same dispatch boundary.

OldAPI representative sources for two providers and a plain wrapper compile against the candidate (javac release17 exit0,15:23:45Z), with all three classfiles byte-identical to their old-SDK compile. This is separate source/binary/wire evidence, not released-provider provenance. Frozen Phase0 fixtures remain untouched; only one exact original1.0 GETresponse is reused. Packaging without optional03 remains a separate pending test requirement.

Current counters: **3/10 completed iterations; iteration4 active**, no-progress0, no scenario has three failing implementation attempts. First paired runtime is one failed integration cycle; initial review regressions are expected REDs before fixes. Deadline16:57:25Z unchanged. Historical Phase0 accounting8/A03conservative10/breachtrue remains preserved. No commit, push, PR or Phase2.


### Phase 1 iteration 4 runtime checkpoint — 15:30Z

`server-review-green`15:25:20–15:26:00Z exited0,39.98s,35modulesSUCCESS. **291 actual executed tests across8 suites,0failures/errors/skips**, counted from this command log; older XML reports are excluded. The previously reported larger333 XML aggregate included stale suites and is not this command count. `legacy-wrapper-binary-green` and `legacy-source-wire-green` each4tests pass, separate oldbytecode and candidate-recompiled source cases; all3 classfiles have identical SHA256 before/after recompilation.

`paired-and-packaging-verification`15:26:28–15:29:23Z exited1,175.40s. JSONRPC120pass; grpc122discovered/100executed/22existing skips,0failure/error, including7actualbootprovisioning profiles and authenticated startup. Three singleversion reference packaging tests eachpass with actual03ClassNotFound, defaultpublic1.1GetTask and disabled/missing-version rejection. REST123discovered/105executed/18skips has1failed newrawsubset assertion: production nowreturns the required1.1 richHTTP400/ErrorInfo, but test still expected legacy -32009. Only that new1.1 assertion was corrected; legacy1.0/03/unknownversion assertions remain unchanged. Public stale1.1 REST/JSONRPC errors and original cardnegative tests passed.

Independent follow-up review found `/v1/card` is a historical versionprefix/defaultcard path, not tenantv1. First gate incorrectly looked up tenantv1. Strengthened test uses default{1.0,1.1}, tenantv1{0.3,1.0,1.1}; `card-default-scope-red`15:29:52–15:30:01Z exited1,2tests/1failure (404 instead400), reproducing improper defaultcard exposure. Same cardgating behavior conservatively counts **2 failed material implementations** (initialmissinggate + wrongtenantgate), not a renamed criterion. Next defaultscope correction is **attempt3**; stop dependent work if it fails. Other initial reviewREDs against already-written implementations count conservatively as first implementation failures, even if commandname saysRED. Genuinely pre-firstimplementation TDD/API absence is separate. Independent counter audit found no other3failed-cycle behavior; finaltable will retain counts. Current **3/10 completed, iteration4 active**, no-progress0, exhaustedfalse. HistoricalPhase0 accounting/breach intact. No commit/push/PR/Phase2.


### Phase 1 iteration 4 closure / iteration 5 — final changed-code reactor

`rest-final-focused`15:30:58–15:31:24Z exited0,25.92s: MultiVersionRestTest66discovered/57executed/9existing skips plus divergentdefault RestCardProvisioningWithout03Test2/2pass. Both the corrected1.1 rich error assertion and defaultcard scope are verified. **REST cardgating attempt3 passed; no further correction cycle is scheduled.** Independent conservative attempt audit retained at /tmp/a2a-phase1-run/attempt-audit.md; no samebehavior has3failedmaterialcycles, no reset for renamedcriteria or newcommands.

Iteration4 complete: **4/10 completed**, no-progress0. Iteration5 targets A03/A14 changed-code compatibility and root `mvn clean install`, alongside preservation/static integrity. Focused new-to-new matrices, independent legacybytecode/source/wire probes, newregistration subsets and no03packaging have appropriate passing evidence; broader root verification is now justified by actual production/test changes. This is not a repeat of the unchanged Phase0 baseline. Java17 is retained; no Docker skip switch is introduced. Root coordinates one bounded reactor. Deadline16:57:25Z unchanged, exhaustedfalse. No commit/push/PR/Phase2.


### Phase 1 iteration 5 failed build / iteration 6 fresh independent review

`full-changed-reactor` (`mvn -B -ntp clean install`) ran15:32:01–15:36:06Z,244.70s,exit1,no timeout. **164fresh Surefire reports:2528discovered,2484executed,44skipped,3failures,0errors.** This is partial coverage only: remaining reactor modules skipped after the failing stream-lifecycle example. Its3binding tests fail taskcreation; redirected output shows `No compatible transport found`. Existing examplecard remains1.0 while StreamLifecycleClient constructs an implicitdefault1.1 client. The cloudexample testfactory has the analogous unpinned configs. No root-success claim; failure reports retained before any futureclean. Iteration5 completes with useful regression narrowing: **5/10 completed**, no-progress0.

The user explicitly requested **another agent review first**. A fresh independent read-only reviewer was spawned (`fresh_phase1_review`); root holds further implementation changes until that review returns. Earlier review does not substitute for this requested fresh pass. Fresh initial findings independently confirm missed examplemigration and reveal ordinary03REST historicalprefix misrouting beyond the already-fixedcardpath. Cardgating's third focusedpass never established allordinaryprefix correctness. Keep related rootcause visible; do not erase prior2failedcardcycles or relabel a broadcriterionpassed. OrdinaryGet/send dispatcher methods were not changed by the cardfixes; fresh review is auditing bothscope andcounters.

Current user clarification: **0.3 does not have tenants.** Its `/v1` is a versionprefix/defaultendpoint path, not a1.x tenant. Preserve separate native03 APIs and defaultcontext. Any experimental rawtenant03 aliases/registrations must not be presented as protocolsemantics; fresh review now checks this explicit constraint and required default-endpoint subset/public03client coverage. No functional edits since the review request. Iteration6 active (A01/A02/A03 oldentrypoint/default migration review); no-progress0, budgetexhaustedfalse, deadline16:57:25Z unchanged. HistoricalPhase0 counters8/A03audit10/breachtrue remain intact. No commit/push/PR/Phase2.


### Requested follow-up note and explicit continuation — 15:45Z

User asked to note **existing examples to1.0** and **correct native0.3 REST routing**
for future work, then immediately clarified **“if you were intending to do those
two things now, then we should proceed”**. Latest instruction authorizes continuing
both corrections now; retain this note and their final status in the handoff.
Agents briefly stopped; no production routing/registration correction occurred
during that stop. Root's proposed example-pin rollback tool was aborted before
dispatch, so the verified pins remain applied; no rolled-back result is claimed.

`example-migration-green`15:44:10–15:44:45Z exited0,34.17s: existing StreamLifecycleTest
**3binding tests passed**, and cloudexample helper compiled. This verifies the
explicit1.0 pins after the captured3failed legacyexample tests, without changing
servercards, sharedSDK1.1 default or fallbackpolicy. Both corrections remain
currentPhase1 scope, not authorization for commits orPhase2.

Fresh-review functional plan resumes. Registration tests now separately require
03onlyatnormalizeddefaultscope; all1.xtenant checks remain. NativeordinaryREST
routing awaits actualoldpublic03client divergent-scope RED, then focusedfix/check.
Current5/10completed, iteration6active, no-progress0, exhaustedfalse; deadline
16:57:25Z unchanged. Preserve all priorattemptcounts and Phase0history.


### Phase 1 iteration 6 correction verification — 15:49Z

Fresh user-requested review retained at `/tmp/a2a-phase1-run/fresh-review.md`:
P1 missed legacyexamplepins; P1 ordinarynative03RESTprefix defaultscope; P2 missing
actualoldpublic03client pairing/defaultendpoint subsetcoverage. The earliercard
focusedpass and rawtenant03aliases didnotestablish native03compatibility. Those
initial coverageclaims are **superseded**, not accepted protocolsemantics. The
user clarification is binding: **0.3 has no tenants**; tenant-bearing03 registrations
and alias tests are invalid. Historicalfixtures/expectations remain unchanged.

`native03-registration-red`15:45:38–15:45:54Z exit1,8tests/1failure: allbinding
03-containing tenant registrations were wronglyaccepted. After constructor guard,
`native03-rest-routing-red`15:46:42–15:47:23Z verifies **all8registrationtests pass**,
including normalizeddefaultscope; nativepublic03RESTget/send still2errors as
expected before routingfix. Disabled03public tests also2assertfailures from an
incorrect newtest assumption: old03spec has no VersionNotSupportedError type and
its frozenRESTmapper returns genericempty-messageA2AClientException for the
retained dispatcher envelope. No oldmapper/codec changes or new03typedguarantees
are introduced. Corrected tests retain exactgenericrejection and separately prove
HTTP400/-32009/versionreason on the same native GET/validPOST paths.

Production now maps native03REST `/v1/...` to defaultregistration/nulltenant,
rejects fabricated03tenant aliases, preserves explicit1.x `v1` tenants and legacy
noresolver delegation. New03tenant guard uses normalizedscope. New tests use
seven realdefault-endpoint profiles perHTTPbinding, existingseven grpc profiles,
actualpublicold03Get/send alongside shared10/11clients, and only three nonempty
1.xtenant subsets. Added native03 positive/negative defaultscope, explicit1.xv1
scope and aliasnegative checks. Fresh reviewer inspected corrections and found
no newproduction blocker; runtimeverification remains pending.

Stablecounter linkage: examplemigration1failedmaterialcycle then correctionpassed;
03tenant-registration initialfailure then guardpass; ordinary03RESTdispatch
initialimplementationfailure now correction2pendingverification. Priorcardrow
retains2failedcycles/thirdfocusedpass and sharedprefixrootcause; it neverpassed
allordinaryRESTscope. No scenario has3failedmaterialcycles. Root runs
`native03-and-paired-focused` sequentially with900sbound, no duplicateMaven.
Current5/10completed, iteration6active, no-progress0, budgetexhaustedfalse;
deadline16:57:25Z unchanged. No commit/push/PR/Phase2.


### Phase 1 iteration 6 closure / iteration 7 — final clean reactor

`native03-and-paired-focused`15:49:48–15:52:20Z exit0,151.86s,no timeout,44modulesSUCCESS. Actualcommandlog totals: {"suites": 42, "totals": {"discovered": 400, "failures": 0, "errors": 0, "skipped": 40}, "bindings": {"jsonrpc": {"discovered": 129, "failures": 0, "errors": 0, "skipped": 0}, "rest": {"discovered": 136, "failures": 0, "errors": 0, "skipped": 18}, "grpc": {"discovered": 124, "failures": 0, "errors": 0, "skipped": 22}}}. AllsixnativeRESTscope tests pass, all21default-endpoint subsetprofiles pass, actualoldpublic03Get/send plusshared10/11/default11pairing pass, and three1.1-only packaging tests pass. No03tenantclaim remains; 1.xtenantmatrices are separate. Ordinary03RESTcorrection2passed; priorcard2failed/thirdpass retained. Examplepins3pass/cloudcompile alreadyrecorded.

Iteration6 complete: **6/10 completed**, no-progress0. Iteration7 targets finalchanged-code `mvn -B -ntp clean install` afterallfreshreviewcorrections. Firstfullfailedbuild evidence remainsretained; no claim itpassed. Allcommandssequential, finite1200srootdeadlinewithinremainingrunbudget. Overallrun deadline16:57:25Z unchanged, exhaustedfalse. No commit/push/PR/Phase2; explicitcommitapprovalpending.


### Phase 1 iteration 7 closure / iteration 8 — final independent review and handoff

Final changed-code `mvn -B -ntp clean install` ran
**2026-11-10T15:52:54.831198Z–16:03:38.421153Z**, **643.588 seconds**,
exit **0**, timeout **false**, within its **1,200-second** deadline. All **83 modules
SUCCESS**. Fresh XML collection: **288 suites (287 Surefire + 1 Failsafe),
3,987 discovered / 3,864 executed / 123 skipped / 0 failures / 0 errors**.
Exact skipped test identities match the retained Phase 0 report; no new skips.
PostgreSQL5/MySQL6, Kafka replication integration4, actual multi-instance2 and
OTEL Failsafe9 ran with no skips or failures. These prove their existing scope,
not future mutation/live semantics. Building the TCK SUT is not target certification.
[Full metadata](phase-1/evidence/full-changed-reactor-final.json),
[fresh suites/skips](phase-1/evidence/full-changed-reactor-final-test-summary.json),
[skip comparison](phase-1/evidence/skip-identity-comparison.json).

Iteration7 complete, **7/10 completed**. Iteration8 targets A03/A04/A14 evidence
and independent review, with no further source implementation. The user-requested
fresh reviewer independently inspected final metadata/log, all report mtimes and
XML totals, and all83 SUCCESS rows. **No open blocking finding for authorized
Phase1**; all three initial fresh findings are resolved with passing evidence.
[Final review](phase-1/evidence/fresh-review.md). No review implies commit approval.

Both requested corrections are now verified: established examples explicitly
select1.0; native03 REST uses its historical `/v1` prefix at default/null scope,
independent of explicit1.x tenantv1. 0.3 has no tenant contract; invalid intermediate
rawtenant03 coverage and tenant03 registrations are withdrawn/rejected. Frozen
legacy outputs, model/convenience defaults and CURRENT_PROTOCOL_VERSION1.0 remain.
No schema/generation/observation/mutation-authority/live work was added.

First failed root logs, partial counts and original three example-failure reports
are retained separately; all32 bounded command records/logs include actual argv,
workdir, deadlines and outcomes. Old compiler inputs remain archived and hashed;
same-source candidate compilation and unchanged binary wire probes are distinct.
[Verification](phase-1/verification.md), [reviewable122-file source/test diff and
proposal](phase-1/commit-proposal.md), [next-session handoff](phase-1/next-session.md).
No baseline rerun or candidate regeneration of frozen expectations occurred.

The existing stable attempt audit retains every failed cycle and related root
cause. REST card scope:2failed/thirdimplementationpass; linked ordinary native
REST:initialfailure/secondimplementationpass; examples:1failure/correctionpass;
03tenant guard:1failure/correctionpass. JSONRPCtenant/legacyroot and optional03
availability/activation retain conservative high-water2. No inspected observable
reached3failedmaterialcycles. Historical Phase0 remains8iterations/A03audit10/
no-progress0/originalbreachtrue; subsequent authorization did not erase it.

Remaining gates are unchanged: experimental RC, final-spec reconciliation, #2415
HTTP live association, exactartifact/batch outcomes and cursor/recovery semantics.
PR2411 is a proposal. No complete A01–A14 criterion or final conformance is claimed.
Phase2 and all later functionality remain unstarted and unauthorized. Explicit
commit approval remains pending; no staging, commit, push, PR or external mutation.


### Phase 1 iteration 8 closure — commit approval pending

Completed **2026-11-10T16:10:32.445320+00:00**, elapsed **4387 seconds** since14:57:25Z;
**8/10 completed iterations**, no active implementation iteration, no-progress **0**,
budget exhausted **false**; authorized deadline16:57:25Z unchanged. Independent
source/build review and handoff review are complete; no open blocking Phase1
finding remains. This is completion of the authorized Phase1 scope, not full
A01–A14 acceptance or final1.1 conformance. Explicit commit approval is pending.
No build/probe remains live, no index changes, no commit/push/PR and no Phase2.

Final preservation check passed:120 original manifest fixture hashes,5 Phase1
fixture hashes,11 archived old SDK jars, frozenresponse byteidentity, unchanged
Phase0/authoritativeinputs/protobufsources, CURRENT_PROTOCOL_VERSION1.0 and clean
index. Link-check helper's first invocation found its own output was not yet
created; its self-output dependency was corrected, then all checks passed. This
was an evidence-helper issue; no production/test code changed or build reran.
[Integrity](phase-1/evidence/final-integrity.json),
[all32commanddeadline/overlapaudit](phase-1/evidence/run-control-audit.json).
HistoricalPhase0 remains8completed/A03audit10/no-progress0/originalbreachtrue.


### Subsequent independent review — commit readiness withdrawn (2026-11-10T17:32:28.033293+00:00)

The user supplied two P1 findings (supplied gRPC channel metadata override and
wrong1.1 REST subscription verb) and one P2 finding (native03 gRPC registration/
original-version context lost). Root inspected all implicated source paths and
confirmed the gaps; no reproduction rerun or production/test edit was performed.
The old standard-channel exclusion was not an approved contract narrowing.
[Findings and focused correction plan](phase-1/review-response.md).
Earlier no-blockers/Phase1-complete/commit-ready conclusions are superseded.
Existing build totals and original review reports remain historical evidence.

Original deadline16:57:25Z has passed. Further implementation requires explicit
additional time/iteration authorization; no new run or iteration has started.
Historical Phase0 counters8/A03audit10/no-progress0/breachtrue and Phase1 completed8
remain intact. Preserve all existing attempt counts and audit the new reproduced
failures against stable criteria before correction; do not reset or relabel.
Only administrative checkpoint/readiness documents were updated. Source hashes
remain unchanged, index clean, HEAD unchanged; no commit/push/PR/Phase2.


## Authorized correction run — 2026-11-10T17:33:12Z

User answered **Yes** to an additional60minutes and **four additional focused
iterations**, explicitly retaining historical counters/safeguards and separate
commit approval. Correctiondeadline **18:33:12Z**. OriginalPhase1 iterations8
remain completed; correctionrun0/4, cumulative8completed, iteration9active
(A01/A02/A04 boundary reproductions). No-progress0, budgetexhaustedfalse.
No commit/push/PR/Phase2. Phase0history8/A03audit10/breachtrue unchanged.

Focused plan before production edits:
1. Reproduce allthree findings with independent boundary evidence. RESTGET-only
peer plus rawsingle/multiversionGET and legacyPOST; suppliedstandardgrpcchannel
conflicting/identicalmetadata unary+streaming; native03downstreamcontext plus
conflictingfactoryzeroexecution. Root coordinates one boundedMaven at a time;
agents own disjoint files and run no Maven. Exactcommands/results retained in
/tmp/a2a-phase1-run with unique correction-* labels.
2. Implement focused corrections only after each validRED. Investigate a client
gRPC pretransport guard using supported lifecycle hooks; no silent opaqueChannel
exception or contract narrowing. Validate realmergedmetadata and normalizeequal
duplicates. Native03registration reachescore and customfactories cannot replace
originalversion. REST1.1GET preserves demonstrated1.0POST/native03.
3. Run focused regressions and appropriate changed-code reactor withinremaining
budget; allcommands <=min1200seconds,remainingbudget,controlledcleanup30seconds.
Count every materialfailedcycle againststableobservables, including currentreview
reproductions; preserve priorhigh-watercounts. Stopdependentwork at3failures.
4. Independentreview then refreshproposal/diff/evidence/handoff. No claimcomplete
untilcorrectedcode+tests+reviewpass. Retainoldsuccessfulbuild as pre-correction
evidence ratherthan proofnewfixes.


### Correction iteration 1 closure / iteration 2 — focused fixes and strict gRPC gate

Authorized correctionrun began17:33:12Z,deadline18:33:12Z,fouradditionaliterations.
Allthree suppliedfindings reproduced with meaningful tests before relevantedits.
Native03 RED4tests/3fails; standardchannelRED4/4fails; sharedmetadataingressRED2/2;
RESTclient/single6tests3fails andmultiversion6tests2fails. Initialnativecommand
stoppedatgrpcnewtestfixturecompile(privateBuilder/wrongenum), fixedas harness
issue; one rootcommandwronglyselectedclientmoduleandran0tests, nocoverageclaim.
Allcommands/logsretained. Correctioniteration1 complete; **1/4 additional,
9 cumulative completed**, no-progress0, budgetexhaustedfalse.

Iteration2 verifiesfocusedfixes. `correction-focused-green`17:43:11–17:43:27Z
exit0,no timeout,16.646seconds:37executed/0skips/failures/errors,7actualsuites.
REST12and native03context4 pass, sharedingress2 andoldadmission1 pass, existing
grpctransport18pass. F2/F3 sourceandfocusedruntime resolved; broaderregression
pending. StandardMetadataUtilscases4pass in separate23testprobe, butlaterfactory
acceptancetestfails. Currentcandidate thereforehasaknownfailingstrictacceptance
test; no completebuild/readinessclaim.

The publiclatertracerprobe verifiesremainingF1bypass afterthepartialguard.
Userexplicitlychose **keepthestrictcontract; leavefindingopen andpresentan
enforceablechanneldesignbeforecompatibilitychanges**. No narrowingapproved.
F1conservativelyretains2failedmaterialcycles(initialimplementation pluspartial
guard); no furtherproductionfixattemptuntilconcretecontractdesignapproved.
Noopaque-channel exclusionmaycloseit. Design-onlywork+independentreviewcontinue;
REST/native03independentregressionscontinue. Phase1remainsincomplete, no commit.
HistoricalPhase0 counters/breachandoriginalPhase1iterations8remainintact.


### Correction iteration 2 closure / iteration 3 — reviewed design and checkpoint

`correction-affected-regression`17:45:16.610691–17:48:57.917444Z exited0,
221.306seconds within900secondbound,no timeout;37modulesSUCCESS. FreshXML211
suites:2901discovered/2802executed/99skipped/0failures/errors. All99skips match
existingPhase0identities. ThiscommandexplicitlyexcludedGrpcChannelMetadataTest
(all5cases); separateprobeproves4standardcasespassand1strictlatertracerFAILS.
This is a passing affected-module subset, **not a completepassingbuild**.
RESTF2andnative03F3 correctionsnowhavefocused+broaderregression andindependent
source review. No additionalimplementationfailurecycle occurred for either.

Correctioniteration2 complete: **2/4 additional,10 cumulative completed**,
no-progress0,budgetexhaustedfalse. Iteration3 targets F1enforceableAPI design,
independentdesignreview andhonestcheckpoint. StrictF1remainsopen with2conservative
failedcycles; no furtherproductionattemptstarted. Userexplicitlyretainedstrict
contract andaskeddesignbeforecompatibilitychanges. Newownedchannel API/removal
ofopaquechanneladmission requiresseparateexplicitmaterialcompatibilityapproval;
no silentexplicit1.0legacyexception. Deadline18:33:12Z unchanged. No staging,
commit,push,PR,Phase2 ornormativeguarantee; historicalPhase0 counters/breachintact.


### Correction iteration 3 closure — material design approval pending

Checkpoint **2026-11-10T17:57:26.702888+00:00**, correctionelapsed **1454seconds**;
**3/4 additional iterations completed, 11 cumulative Phase1 completed**,
no-progress0,budgetexhaustedfalse,deadline18:33:12Z unchanged. One additional
iteration remains within that original correctiondeadline; continuation doesnot
renew it. No activeimplementation/build remains. Stop reason: explicit material
channel admission/lifecycle/backend decision needed, as requested by user; no
restriction or narrower guarantee has been implemented.

Independent correction review verified F2/F3 source+37focusedpasses+selected
37module/2802executed/99skipped regression. Strict F1 staysopen andretains its
knownfailedlate-traceracceptancecase and2conservativefailedcycles. Designed
constrainedSDKownedchannels for BOTH1.0/1.1; rawChannel/factories/hooks rejection,
credentialboundary, freshsingleownerendpointboundhandles andownedNettybackend/
shutdown are explicitmaterialchanges requiringapproval. Endpointmatch usescard;
TLSauthority usesdeclaredTLSoptions, no newmodel/wirefield. Independentdesign
review foundproposalcoherent/readyforuserdecision withproofgates stillpending.
[Design](phase-1/grpc-channel-design.md),
[review](phase-1/evidence/correction-independent-review.md),
[verification](phase-1/verification.md), [handoff](phase-1/next-session.md).

Fullcurrentbuildisnotpassing: selectedregressionexplicitlyexcluded5-caseclass;
separateprobehas4standardpasses+1strictfailure. Original83modulecleansuccess
is pre-correction evidence. Current134source/testfiles retained withhashes/diff;
alloriginalfixtures/Phase0/authoritativeinputs unchanged. No stage/commit/push/PR
orPhase2. HistoricalPhase0 remains8/A03audit10/no-progress0/originalbreachtrue;
originalPhase1iterationcount8 retained, notreset. No furtherF1productionattempt
authorized untilmaterialdesign decision; existingthree-attemptsafeguard binds.

Final administrative integrity completed **2026-11-10T17:58:36.695535+00:00**: all134current
source hashes/282checkpointpaths match; index/HEAD unchanged;120originalfixture,
5Phase1fixture and11oldSDKjar hashes pass;84locallinks verified. ArchivedMD
links initially failed aftersnapshotrelocation; checker now resolves them from
theoriginalPhase1base, preserving archivedbytes. No production/test edit orbuild
rerun accompaniedthathelpercorrection. This doesnot changeiterationcounts or
closeF1. No currentbuild/test process remainslive.


### Correction iteration 4 — approved owned-channel implementation (2026-11-10T18:22:07.572527+00:00)

User answered **Yes** to explicitcompatibility/backend/lifecycleproposal. This
authorizesstrictSDKownedNettychannels andrawchannel/factory rejection for BOTH
1.0/1.1, restrictedhooks, guardedmetadata andSDKownedshutdown. It doesnotapprove
committing,Phase2 or a newtime/iterationbudget. Clockatresume18:21:00Z; original
correctiondeadline18:33:12Z remains. **3/4additional completed,11cumulative**,
iteration4active(A02/A04F1strictadmission); no-progress0. F1retains2failedmaterial
cycles; thirdfailedimplementationtriggersstop. No ledgerreset.

Focusedplanbeforeedits: behaviorREDusingexistingrawconstructor/providerAPIs,
thenSDKprivateissuedendpointbound/singleownerNettyhandle+immutableownedoptions/
SDKauthheaders;buildermanagedpath andnonnullfactoryadapter;opaqueconfigreject
beforeinvocation;oldconstructorspreservedbutrawadmissionnowmigrationerror.
RealNettyfocusedproofbothversions/unary+streaming,metadata/endpoint/ownership/
closefailurecases,independentreview. NoMavenoverlap; commandsboundedbyremaining
budgetincludingcontrolledcleanup. Remainingfullmigration/proofobligationsmust
beexplicitifdeadlinepreventscompletion; no claimingcompletefrompartialchecks.


### Correction iteration 4 closure — 2026-11-10T18:29:24.285527+00:00

Material SDK-owned channel approval was implemented in part without renewing the original correction authorization. The bounded raw-admission RED ran 2 tests, both failed as expected before the admission fix. The owned-channel focused proof ran 18:25:36.084482–18:25:40.601220Z, exit 0 in 4.517 seconds under a 180-second deadline: 8 successful modules, 14 executed tests, zero failures/errors/skips. Final strengthened ServerStreamTracer assertions were edited at 18:25:44Z, after that build, and remain unverified. No current full-module/full-reactor passing claim is made.

Independent review traced grpc-core 1.81's reserved final load-balancer tracer slot: an admitted global provider can mutate headers after the SDK guard. Provider allocation also precedes descriptor validation without cleanup on failure. No further production attempt or compensating proof was started after these findings. F1 remains open; F2 REST verb and F3 native 0.3 context corrections retain their earlier selected evidence. Existing raw-channel/factory consumers remain unmigrated; static inventory lists 52 candidate calls, not an exhaustive dataflow proof.

Counters: correction 4/4 closed, cumulative Phase 1 12 (original 8 retained), no-progress 0; iteration budget exhausted true; F1 conservative materially failed cycles 3 and safeguard stop. Phase 0 remains 8 completed / A03 audit 10 / no-progress 0 / original breach retained. Historical full-build totals remain pre-correction evidence only. Current source scope, logs, independent review, limits and next-session handoff are retained. No staging, commit, push, PR, Phase 2 or new protocol guarantee.


Final administrative checkpoint 2026-11-10T18:29:54.582598+00:00: 141 source/test paths (64 modified, 77 new), 7,103 insertions / 677 deletions; 299 total changed paths including architecture/evidence. Current source hashes and compressed diff agree. All 44 bounded command records fit their retained authorization deadlines (32 original, 12 correction), with no overlap or timeout. Integrity passed: unchanged branch/HEAD/index, authoritative inputs and Phase 0 preserved, 120 original fixture hashes, 5 Phase 1 fixture hashes, 11 old SDK jar hashes, frozen response bytes and legacy CURRENT_PROTOCOL_VERSION=1.0. These administrative checks do not close F1 or establish a current full build. Final no-server-stream assertions remain unrun. No further implementation/testing after safeguard stop.


### Five-iteration Phase 1 continuation — 2026-11-10T18:37:06+00:00

User explicitly authorized **five more focused iterations** after the recorded stop and estimate. This authorizes a resumed revision of F1 and remaining Phase 1 fixes; it does not erase its historical three failures, the original eight plus four correction iterations, or Phase 0 accounting/breach. Default two-hour cap retained: deadline **2026-11-10T20:37:06+00:00**. Additional completed 0/5, iteration 1 active (A02/A04 enforcement design), prior cumulative 12; no-progress 0. Historical F1 failures 3 retained; resumed failing-cycle safeguard remains three, not unlimited retry. No commit, push, PR or Phase 2 permission.

Plan: iteration 1 inspect exact grpc 1.81 source/API and independently review a revised enforceable provider boundary; iteration 2 implement one proved design with global-provider injection and pre-I/O tests; iteration 3 fix fresh-allocation cleanup and migrate existing callers/negative test boundaries; iteration 4 affected/full regression plus TLS/cancellation/ownership checks; iteration 5 independent review, bounded corrections, evidence/integrity and concrete handoff. Count materially distinct implementation cycles separately even within iterations. Stop if first design checkpoint cannot establish a defensible boundary. No baseline rerun or frozen fixture regeneration.


### Resumed iteration 1 closure / iteration 2 start — 2026-11-10T18:42:30.677133+00:00

A02/A04: exact backend/source investigation and independent design review established atomic trusted-provider capture as enforceable in pinned 1.81.0. Transport-only enforcement rejected due foreign-subchannel bypass; no speculative production fix attempted. Chosen private stock-DNS resolver avoids unnecessary resolved-once DNS behavior change while service-config lookup remains disabled. Independent capture review retained in phase-1/evidence/resume-provider-design-review.md; focused plan phase-1/resume-plan.md. Additional completed 1/5, cumulative 13, iteration 2 active; no-progress 0, historical F1 failures 3, resumed failed implementations 0. Deadline unchanged. Existing consumer and provider-cleanup work scoped separately; root alone runs Maven.


### Resumed iteration 2 closure / iteration 3 start — 2026-11-10T18:47:00.759048+00:00

A02/A04 enforcement: global-provider RED 18:43:46–55Z, 5 tests / 3 assertion failures / 0 errors / 0 skips; raw Netty control actually emitted [1.1,1.0] through the hostile final LB tracer. One revised implementation uses atomic exact stock-provider capture, private stock-DNS registry, inert DNS policy parser, SDK no-proxy callback, disabled service-config lookup and Census callbacks. Independent review found no blocking semantic defect; pinned source coupling and direct-test limits retained. First compile failed for absent direct grpc-core dependency (declared, no behavioral change). Next 24-case focused command had one harness assertion: normal errorConsumer null completion was incorrectly counted as error; corrected test only, no protocol/production change. Final focused command 18:45:48.782108–18:45:57.405728Z exit0/8.624s, 8 successful modules, 24 discovered/executed / 0 skipped/failures/errors. All 12 owned tests, including formerly unrun zero-server-stream assertions, executed; 5 provider-isolation, 5 cleanup, 2 opaque-admission tests passed. No additional materially failed enforcement implementation after resume; historical failures3 retained.

A04 cleanup RED 18:42:48–53Z 5tests/4failures/0errors/skips reproduced allocation leaks, including postallocation interception failure. Provider now snapshots one private SDK factory before admission, prevalidates descriptors/card, and closes only freshly allocated channel on construction failure while preserving cause. Existing-owner negative tests still pass.

Additional completed2/5, cumulative14; iteration3 active for complete consumer migration and current client regression. 22 migration files ready: real Netty fixtures, explicit1.0 preserved, intended request-negative construction moved outside assertion lambdas, shared references/extras/examples/dev docs/client-base/itk managed options, pertestClientclose. Root-only bounded client/base -am test running. No-progress0; original deadlines/counters preserved; Phase1 not yet verified.


### Resumed iteration 3 closure / iteration 4 verification — 2026-11-10T18:56:40.936826+00:00

A02/A04 consumer migration: current client/base -am test ran 18:46:17.414898–18:46:38.821644Z, exit0/21.409s; 12 successful modules, 83 logged suites / 916 discovered / 911 executed / 5 preexisting skips / 0 failures/errors. No excluded GrpcChannelMetadataTest: all 5 cases ran, including explicit opaque-channel admission test; original request-context and equal-header tests exercise owned channels. Examples/reference/provisioning/extras/client-base/itk migrations ready. Additional helloworld standalone example explicitly pinned1.0, preserving its existing server/card contract alongside stream-lifecycle/cloud factories.

Further independent provider evidence: 7 tests passed in resume-provider-extra-proof, exit0/9.852s,18:55:34.390793–18:55:44.242829Z. Includes global ProxySelector exclusion and a finite10-second fresh-JVM configurator rejection probe; mutation-race outcome counts retained. DNS TXT parser exclusion remains source-reviewed, not directly exercised with adversarial TXT records.

A14 backend/lifecycle: resume-tls-lifecycle-proof exits0; four actualTLS tests eachcover1.0/1.1, defaultsystemtrust+ALPN/auth unary+streaming, untrustedcertificate rejectionbeforeRPC, callerContextcancellationwithservercallback+ownerreuse, boundedactiveclose+idempotence. Final/currentverification stillrequirescomplete rootbuild andstandaloneexample/itk compile whereexcludedrootdefaults. No baseline rerun.

Additional completed3/5,cumulative15; iteration4activefullcurrentregression; no-progress0; historicalF1failure3retained,resumedmaterialimplementationfailures0. Finitedeadline20:37:06Zunchanged. Independentfinalreviewstillrequired,noPhase2/commit/push/PR.


Iteration4 verification refinement 2026-11-10T19:01:05.356158+00:00: finalreview requested direct owned-handle pin/credential/tracer rejection coverage. Added GrpcOwnedCallAdmissionTest after the full reactor had passed that module; production unchanged. It will run separately after the full process, and full-build totals will not pretend to include this extra test. Source review confirms unsupported asynchronous opaque credentials are rejected, not adapted; no async credential-race support claim. Checked root module list: HelloWorld client/server ARE included in the current83-module reactor, so no separate HelloWorld build needed. ITK optional profile lacks external a2a-itk proto checkout; no whole optional ITK compile claimed.


Iteration4 full-build diagnostic 2026-11-10T19:14:37.880022+00:00

First resumed full clean install failed (label resume-full-current-reactor): start18:56:40.957103Z,end19:09:49.023480Z,runner monotonicelapsed641.917s,exit1,no timeout/1200s bound. Fresh299XML:4040discovered/3917executed/123skips/1failure/0errors;55modulesSUCCESS,1FAILURE,27SKIPPED. Every skipidentity matches Phase0. First failed full results/XML archived before overwrites. No full-pass claim. Only failure was the SECOND/historical raw whitespace metadata call in subset1, exactexpectedINTERNAL vsUNAVAILABLE. Diagnostic rerun kept expectation unchanged and passed; it did not alone close the gate. Independent exactNettysource identified GOAWAY teardown on the reusedconnection: initialsharedmalformedrequest passed INTERNAL+PROTOCOL_ERROR, thenhistoricalcall couldobserve connection shutdown/UNAVAILABLE. Test nowcreates a freshrawchannel for each destructive malformedrequest, preserving EXACT INTERNAL+PROTOCOL_ERROR andzeroapplicationingress assertions. No production statusmapping orfrozenhistoricalfixture changed. This is one fixtureisolation fix/verify cycle, not a new F1 enforcement implementation; historical F1 failures remain3,resumedF1failures0. Allsevenprofiles focusedverification running. Full currentroot willrerun aftertargetednewpin/admission anddeadguardcleanup validation.


### Resumed iteration 4 closure / iteration 5 final review — 2026-11-10T19:29:34.208523+00:00

Complete current root `mvn -B -ntp clean install` (resume-full-current-reactor-final) ran 19:16:29.023726–19:28:29.630519Z, exit0,720.606s under1200s; all83modules SUCCESS. Fresh300XML:4041discovered/3918executed/123skipped/0failures/errors. Independent scan matches all123Phase0 skip identities, with no new or missing baseline skips. No class exclusion. All156 frozen functional input hashes match; current clean build includes the private-pin/opaque-hook test and all strengthened actual-server-stream assertions. The first failed full run remains retained byte-exact; no failed evidence is relabeled passing. Fresh raw-channel fixture isolation preserved exact INTERNAL+PROTOCOL_ERROR and zero application ingress; all seven subset profiles and current full build pass.

Root build ended; no Maven remains running. HelloWorld is included in the root83modules. Optional whole ITK module is uncompiled due absent external proto checkout; exact migrated managed-factory expression separately type-compiled at19:16:23Z with provenance retained, not a whole ITK claim. Static integrity already passes unchanged branch/accepted Phase0HEAD/clean index, authoritative inputs/Phase0,120 frozen fixture hashes,5Phase1 fixture hashes,11 old SDK compiler-input hashes and legacy CURRENT_PROTOCOL_VERSION1.0. Markdown check was corrected to recognize valid :line clickable file links; no source or protocol expectation changed.

Additional completed4/5,cumulative16; iteration5 active only for independent final review, current documentation/evidence reconciliation and concrete handoff. No-progress0; historical F1failed3 and resumedmaterialfailed0 retained. No additional implementation or build is planned; noPhase2/staging/commit/push/PR. Deadline20:37:06Z unchanged.


Iteration5 reporting erratum 2026-11-10T19:30:07.712553+00:00: independent review found the first failed reactor module matcher omitted long module names without dot leaders. Its correct result is **78 SUCCESS / 1 FAILURE / 4 SKIPPED**, not the earlier reported55/1/27. The complete log and299archivedXML are unchanged;4040discovered/3917executed/123skipped/1failure/0errors remain correct. Initial derived count retained separately; current independent summary corrected transparently. Final current full reactor remains83SUCCESS/4041discovered/3918executed/123skipped/0failures/errors.


### Resumed iteration 5 closure — 2026-11-10T19:36:01.629226+00:00

Independent final review passed with no blocking source, verification, scope or administrative findings for the current experimental Phase 1 candidate. The full clean install passed 83 modules: 4,041 discovered / 3,918 executed / 123 exact Phase 0 skips / zero failures or errors. It exited 0 in 720.606 seconds under the 1,200-second bound. The reviewer independently checked all 300 fresh XML reports, all 156 functional hashes, and the exact retained patch (SHA-256 `853960af48a30475a94581a72b64f34a9de4564aebd13d749e26d6e3ebd3a4c3`). Read-only reverse patch validation passed. The complete proposed inventory matches all 394 paths, including untracked files.

Integrity passed for 120 original fixture hashes, five Phase 1 fixture hashes, eleven archived original compiler jars, 300 byte-exact current XML reports, and seventeen pinned upstream source entries and source archives. Phase 0, authoritative architecture, protobuf inputs, released docs, branch, HEAD and index remain unchanged. `CURRENT_PROTOCOL_VERSION` remains 1.0. All 58 bounded commands (32 original / 12 first correction / 14 resumed) fit their retained deadlines, with no overlap or timeout. Subsequent edits were administrative only; the successful current build was not rerun without cause.

The latest supplied findings are corrected under the approved gRPC migration for both versions: atomic stock-provider capture and private resolver/policy/proxy boundaries; shared-consumer migration with valid negative-test construction; and descriptor validation, factory snapshot and fresh-only allocation cleanup. The final review requested one wording correction: the hostile provider is rejected before its `newLoadBalancer` callback, rather than a universal claim about all its methods. Documentation was corrected; implementation is unchanged. REST 1.1 GET and native 0.3 registered-context corrections remain passing. Native 0.3 has no tenants. The requested examples/native REST note is marked completed in the follow-up register.

Proposed functional diff: 156 files (73 modified / 83 new), 8,460 insertions / 1,015 deletions. The complete 394-path proposal includes phase-scoped architecture and retained evidence. The breaking Conventional Commit proposal ends with `This fixes #1303`. Separate commit approval is required and absent. No staging, commit, push, PR or Phase 2 work occurred. The root build has ended; no build process remains live.

Counters: all five additional iterations closed, 17 cumulative Phase 1 iterations (original 8 + first correction 4 + resumed 5), no-progress 0. The additional allowance is exhausted at successful handoff. Historical F1 materially failed cycles remain 3; resumed failed material cycles are 0, with one verified revised implementation. The malformed-channel fixture isolation is one separate fix/verify cycle. Phase 0 remains 8 completed / conservative A03 audit 10 / no-progress 0 / original breach retained. No authorization, deadline or safeguard history was restarted or erased.

Remaining limits: the optional whole ITK module is uncompiled because its external proto checkout is absent; only its exact migrated expression was type-checked. Adversarial DNS TXT and explicit wrong-host TLS negatives were not executed; source-reviewed parser exclusion and an actual untrusted-peer negative are retained. The pinned gRPC 1.81.0 internals require re-audit on upgrade. Opaque asynchronous credentials are unsupported, and complete A03 is not claimed. Experimental RC registration remains incomplete for later semantics. HTTP association (#2415), exact artifact/batch outcomes and cursor/recovery gates remain binding; PR2411 is only a proposal. No later generation/timeline, observation, mutation-authority or live implementation was added.

Next action: present the concrete scope, diff, verification and remaining gates for explicit user approval of the Phase 1 commit. Do not begin Phase 2.


### Explicit Phase 1 commit approval — 2026-11-10T19:47:38.723628+00:00

The user explicitly answered **“YEs, commit”** to the concrete 394-path Phase 1 proposal and its breaking Conventional Commit message ending `This fixes #1303`. This authorizes creating that local Phase 1 commit. It does not authorize push, PR, Phase 2, new implementation iterations or protocol guarantees. Earlier pending-approval statements and JSON records remain historical checkpoints.

Pre-commit checks reconfirmed branch `support/a2a-1.1.0`, accepted Phase 0 parent `e468dd70a415d17c19ae70d75750b6464d983ad1`, clean initial index, exact 394-path scope, all 156 frozen functional hashes, and read-only reverse application of the exact retained patch. The retained complete build and independent final review apply to those unchanged inputs. No tests were rerun without a new verification need. Only this approval record and handoff/proposal status changed after review. The resulting commit SHA will be recorded as a post-commit receipt.

Staged verification note: the complete index contains 79 trailing-space diagnostics solely in three retained Git-generated `source-diffstat.txt` files (pre-correction, pre-resume and current evidence). Their bytes are preserved. Staged whitespace validation excluding exactly those three evidence files passes; no functional file or other staged path has a whitespace diagnostic. This is an evidence-format exception, not a source or protocol change.


### Phase 1 commit receipt — 2026-11-10T19:48:36.329211+00:00

Created **`4efaf334e485af5d80dda39c35c6e635e2c11b1a`**, `feat(a2a-1.1)!: add explicit interface and client version policy`, on `support/a2a-1.1.0`, with parent `e468dd70a415d17c19ae70d75750b6464d983ad1`. Git exited 0. Commit scope is exactly the approved 394 paths; all 156 committed functional hashes match the reviewed candidate and the complete approved message, including `This fixes #1303`, matches. Phase 0 and protobuf inputs remain unchanged. The index and worktree were clean immediately after commit. This required resulting-SHA receipt is the only subsequent uncommitted modification. No push, PR, amendment or Phase 2 action occurred.

### Phase 2 implementation authorization receipt — 2026-11-11

The user explicitly approved D1: accept the Phase 2 record-structure changes and add `TaskMessageUpdateEvent` to the sealed event families, while retaining existing constructors and non-null contracts. This authorizes no additional API/SPI breaks. The user authorized recording this and continuing the existing Phase 2 run, with no budget reset.

Model: Luna implementation requested by the user; runtime model telemetry was not independently observable. No subagents used. Phase 2 counters at resumption: 0/10 focused iterations completed; criterion failed-attempt counters 0; no-progress 0. The prior turn performed preflight only and made no worktree edits. Its wall-clock start was not sampled; no reset is taken. Conservative operational stop: 2026-11-11 13:30 UTC, and in all cases no later than two hours after the original authorization. Phase 0 and Phase 1 historical counters remain unchanged.

Review disposition: the plan records two blocking omissions and incorporates them into T3 and T4/T6. It does not record independent re-review acceptance; that remains required at the planned review gate and will not be claimed in this implementation session.

Current action: T1 compatibility inventory and probes. T2 and dependent tasks proceed under the approved D1 contract; any further material API/SPI break is blocked for Sol checkpoint. No commit, push, PR or Phase 3.

### Phase 2 T1 checkpoint — 2026-11-11T11:45:38Z

**Status:** T1 blocked; T2 and all dependent tasks not started. T1 iteration 1 is consumed (1/10). The same probe criterion reached its three-attempt limit; no retry or counter reset is authorized. No-progress remains 0. The earlier initial sandbox failure occurred before javac and is recorded separately from the three focused attempts below.

**Completed/created:** D1 approval is recorded above and in `phase-2/api-migration.md`. Added the standalone probe POM and `LegacyCaller`/`LegacySwitch` sources. The approved D1 scope is the exact table in `api-migration.md`; no additional API/SPI break was introduced or approved. No production Java, generated schema or runtime module was changed. Existing Phase 0 and Phase 1 history/counters remain intact.

**Inputs checked:** worktree remains on `support/a2a-1.1.0`, HEAD `4efaf334e485af5d80dda39c35c6e635e2c11b1a`; initial unrelated changes remain preserved. All ten jars in the frozen Phase 1 legacy SDK archive matched the hashes in `legacy-compile-manifest.json`. Retained upstream and historical fixture evidence was not modified. Current Java is Temurin 17.0.18; no Java 21 record-pattern run is claimed.

**Exact command results:**

1. `mvn -B -ntp -f .claude/architecture/a2a-1.1/phase-2/api-probes/pom.xml dependency:build-classpath -Dmdep.outputFile=/tmp/a2a-phase2-probe-deps.cp -DincludeScope=compile` — exit 0, 0.705 s.
2. `mvn -B -ntp -f .claude/architecture/a2a-1.1/phase-2/api-probes/pom.xml -Dphase2.sdk.classpathFile=/tmp/a2a-phase2-legacy.cp verify` in the sandbox — exit 1 before javac because Maven could not write an `.m2` `.lastUpdated` file. The same command with escalation reached javac and is the first focused attempt.
3. Focused attempt 1 — exit 1: javac received `-XepDisableAllChecks` as an invalid standalone flag; the POM was corrected to pass Error Prone options through the plugin argument.
4. Focused attempt 2 — exit 1: the probe referred to nonexistent `TaskState.SUBMITTED`; source inspection confirmed `TASK_STATE_SUBMITTED`, and the probe was corrected.
5. Focused attempt 3 — exit 1: Error Prone 2.42.0 reports `RequireExplicitNullMarking is not a valid checker name`. Stop here under the three-attempt cap.
6. `mvn -B -ntp -f .claude/architecture/a2a-1.1/phase-2/api-probes/pom.xml clean` — exit 0, 0.081 s; removed only the generated `api-probes/target` directory after the stop.

**Root cause evidence / Sol handoff:** the repository's active `old_jdk_support` profile for Java 17 selects Error Prone 2.42.0 and NullAway 0.12.10, and its `nullaway.args` omit both `RequireExplicitNullMarking` and the `src/test` exclusion. The standalone POM used the incompatible checker flag. Fixing this POM and proving the legacy probe would require another attempt at the capped criterion. Independently review the T1 probe setup and determine a compliant continuation before any dependent task; do not exceed the attempt cap. T2 remains gated on T1 per the ordered plan.

**Verification:** no successful T1 compile, legacy class-byte run, candidate compatibility run, Phase 2 test, or build is claimed. Worktree review after the checkpoint must include `.claude/architecture/a2a-1.1/phase-2/api-migration.md`, `phase-2/api-probes/pom.xml`, `LegacyCaller.java`, and `LegacySwitch.java`.

**Remaining budget:** 9/10 focused-iteration slots by arithmetic, but T1's criterion attempt cap is exhausted, so they are not authorization to repeat T1. The original two-hour ceiling is unchanged; its initial wall-clock timestamp was not captured before the prior stop. Conservative operational stop remains 2026-11-11 13:30 UTC, without representing this as a reset. Model remains Luna requested by the user; runtime telemetry is unavailable. Next action: fresh Sol review of the blocked T1 probe/checkpoint, then continue only if the existing caps permit it. No commit, push, PR, or Phase 3.


### Phase 2 T1 remediation result — 2026-11-11T12:26Z

The user explicitly instructed: “We need to fix the T1 so that the main implementing agent can continue.” This authorizes a bounded T1 remediation and verification; it does not reset any historical attempt count, Phase 2 iteration, or original run deadline. Remediation is recorded as Phase 2 iteration 2/10. The conservative stop remains 13:30 UTC; the original start time is still unavailable.

The previous three T1 failures remain counted. The first resumed correction (attempt 4) aligned the checker flags with Java 17, but the legacy compile exposed a second setup defect: the compile-scope classpath omitted NullAway's runtime dataflow-nullaway jar, while the POM reused that classpath for javac processorpath. The POM now reads a separate runtime-scope processor classpath. Attempt 5 passed the frozen legacy compile, installed Phase 1 candidate compile, and old-bytecode linkage/runtime check. Full commands, results, and hashes are in [T1 probe results](phase-2/evidence/t1-probe-results.md).

T1 is now passed for the frozen Phase 1 API and installed Phase 1 candidate baseline. The T1 checklist is complete in the plan. T2 may proceed under the already approved D1 and existing Phase 2 authorization. T7 remains responsible for rerunning compatibility probes against the post-T2 candidate. No production Java, generated proto, or runtime module was changed. No commit, push, or PR was created by this remediation.

Files changed by this remediation: phase-2/api-probes/pom.xml, phase-2/api-migration.md, phase-2/implementation-plan.md, this progress ledger, phase-2/evidence/t1-probe-results.md, phase-2/evidence/legacy-api-probes.jar, its SHA-256 sidecar, and the retained failed javac argument file. Pre-existing unrelated modifications were preserved.


### Phase 2 T2 start — 2026-11-11T12:32:43Z

T1 passed on the evidence documented above. Its historical three failed attempts and remediation attempts 4-5 remain counted; no retry or counter reset. Phase 2 cumulative iteration 3/10 is active for T2; T2 criterion attempt counts start at 0. No-progress remains 0. The T1 candidate probe is explicitly deferred to T7 for the post-T2 candidate.

D1 is now marked approved in the plan and API migration report. The plan's two earlier blocking omissions are incorporated into their T3 and T4/T6 tasks; independent implementation review remains a T7 gate. This session does not claim that independent review occurred. Current T2 scope is exactly the plan's shared models and Gson forms; no extra API/SPI break, generated schema change, server behavior or later phase is included.

Model: Luna requested by user; runtime telemetry not independently observable. No agents used. Original operational stop remains 2026-11-11 13:30 UTC; the initial authorization timestamp remains unavailable, so this is not a new deadline. Continue only while both the original wall-clock ceiling and 10-iteration cap permit. No commit, push, PR or Phase 3.

#### T2 focused verification ledger (iteration 3; active)

- Attempt 1/3 at 2026-11-11 12:39:48Z: `mvn -B -ntp -pl spec,jsonrpc-common -am -Dtest=Phase2ModelTest,Phase2ModelSerializationTest -Dsurefire.failIfNoSpecifiedTests=false test` — exit 1; compilation stopped in `spec/Artifact.java` on three invalid `@Nullable Message.Role` type-use annotations (lines 36, 113, 228); no tests executed.
- Attempt 2/3 at 12:40:05Z: same command — exit 1; `Phase2ModelTest` ran 6 tests, 1 failed because `requireMessageOnly()` threw `IllegalStateException` while the test expected `IllegalArgumentException`; JSON-RPC tests were not reached. Corrected the nested Role annotation to use an imported simple type, aligned the conversion failure to IllegalArgumentException, and found/fixed missing wrapped and unwrapped TaskMessageUpdateEvent recognition in JsonUtil. Added coverage for provisional opening-message update IDs, configuration/version round-trip, and unwrapped status events.
- No baseline red run exists for the already-present candidate tests; TDD red/green evidence is unavailable and is not claimed. Current candidate fixes require the third and final focused attempt for this criterion.
- Attempt 3/3 at 2026-11-11 12:41:46Z: same command — exit 1. The spec module compiled and `Phase2ModelTest` passed 6/6; JSON-RPC Common compilation then failed at `JsonUtil.java:175` because `JsonObject` was not imported. Its serialization tests did not run. Added the missing Gson import after this capped attempt; no retry is authorized here.
- T2 focused criterion is **blocked at 3/3 attempts**, not verified. The named Maven command has no successful full result in this run. No-progress remains 0; cumulative Phase 2 iteration 3/10 is closed at this T2 checkpoint. T2 implementation is partial/unverified, and no T4 or other Phase 2 task started. T1's historical attempts and remediation evidence remain unchanged. Next action: a fresh independent Sol review of the T2 diff, followed by explicit authorization before any resumed implementation or verification budget.

### Explicit Phase 2 T2 continuation — 2026-11-11

The user explicitly requested more iterations until T2 is done properly. This supersedes the earlier stop at T2 attempt 3; no historical counts are reset. The continuation remains within the original 10-iteration / two-hour Phase 2 allowance and 2026-11-11 13:30 UTC conservative stop. T2 criterion attempts continue at 4 (not 1); cumulative Phase 2 iteration 4/10 is active. No later Phase 2 task is authorized by this continuation.

- Attempt 4 at 12:42:47Z: same focused Maven command — exit 1. Spec passed 6/6. JSON-RPC Common compiled; serialization tests ran 5 with 2 failures and 3 passes. Both failures were record equality differences because existing Part decoding maps absent TextPart metadata to an empty map. No production Part behavior changed. Updated the new fixture to use explicit empty metadata, matching the established decoded representation; rerun is attempt 5.
- Attempt 5 at 12:43:10Z: same command — exit 1 after spec passed 6/6. JUnit discovery for the JSON-RPC class failed with `NoClassDefFoundError: JsonProcessingException`; zero JSON-RPC tests ran. The production exception class is present in `target/classes`, but `javap` showed the test bytecode contains ECJ-style unresolved-compilation stubs for the exception and JsonUtil methods. The stale compiler output appears to have been produced after the test source edit; use a clean reactor run to ensure Maven javac recompiles the test source and classpath. This diagnostic is source/bytecode evidence, not a successful check.
- Attempt 6 at 12:44:04Z: `mvn -B -ntp -pl spec,jsonrpc-common -am clean -Dtest=Phase2ModelTest,Phase2ModelSerializationTest -Dsurefire.failIfNoSpecifiedTests=false test` — exit 1 during `spec` compilation with three NullAway errors: nullable `contextId` and nullable `message.taskId()` / `message.contextId()` were dereferenced in `SendInputParams`. No T2 tests ran. Replaced those equality dereferences with null-safe `Objects.equals`; no validation policy changed.
- Attempt 7 at 12:44:26Z: `mvn -B -ntp -pl spec,jsonrpc-common -am clean -Dtest=Phase2ModelTest,Phase2ModelSerializationTest -Dsurefire.failIfNoSpecifiedTests=false test` — exit 1. The clean javac compile passed; `Phase2ModelTest` passed 6/6; JSON-RPC serialization ran 5 tests with 4 passing and 1 failing because the Artifact fixture's TextPart used null metadata while the established decoder returns an empty map. Updated that fixture to explicit empty metadata, preserving production behavior.
- Attempt 8 at 12:44:40Z: `mvn -B -ntp -pl spec,jsonrpc-common -am clean -Dtest=Phase2ModelTest,Phase2ModelSerializationTest -Dsurefire.failIfNoSpecifiedTests=false test` — exit 0, reactor 4/4 modules successful; `Phase2ModelTest` 6/6 and `Phase2ModelSerializationTest` 5/5, zero failures/errors/skips. Spotless reports skipped by project configuration. Added explicit defensive-copy assertions for companion list/maps and the `TaskPushNotificationConfig` copy-builder; this test-only addition needs one final clean focused run (attempt 9).
- Attempt 9 at 12:45:23Z: `mvn -B -ntp -pl spec,jsonrpc-common -am clean -Dtest=Phase2ModelTest,Phase2ModelSerializationTest -Dsurefire.failIfNoSpecifiedTests=false test` — exit 0, reactor 4/4 modules successful; `Phase2ModelTest` 7/7 and `Phase2ModelSerializationTest` 5/5, zero failures/errors/skips. This is the planned T2 verification. Because JsonUtil's legacy streaming event adapter was changed, one extra focused run of existing `StreamingEventKindSerializationTest` is queued to verify old wrapped/unwrapped formats remain intact; count continues at 10.
- Attempt 10 at 12:45:48Z: `mvn -B -ntp -pl spec,jsonrpc-common -am clean -Dtest=Phase2ModelTest,Phase2ModelSerializationTest,StreamingEventKindSerializationTest -Dsurefire.failIfNoSpecifiedTests=false test` — exit 0, reactor 4/4 modules successful; `Phase2ModelTest` 7/7, `Phase2ModelSerializationTest` 5/5, and legacy `StreamingEventKindSerializationTest` 8/8 (20 selected tests total, zero failures/errors/skips). Spotless reports skipped by project configuration.
- **T2 is implemented and verified within its planned spec/Gson scope.** Its two checklist gates are checked. Supplied event-generation values are accepted/preserved in the input model; wire-level omission remains explicitly assigned to T4's codec tests. No server/runtime behavior, SPI, proto, or later task was started.
- Run accounting: cumulative Phase 2 iterations 4/10 closed; no-progress 0; original conservative stop 2026-11-11 13:30 UTC remains in force, with no new time budget. T1 remains 5 attempts total (3 initial failures, attempt 4 failed, attempt 5 passed); no T1 counter reset. T2 focused verification attempts total 10; the user's follow-up explicitly authorized continuation beyond the former three-attempt stop, with every prior attempt retained. Model: Luna requested; runtime model telemetry unavailable; no subagents used.
- Stop at the T2 boundary per the user's instruction. Next action is a fresh independent Sol implementation review of T2; independent review has not occurred in this session. Do not start T4 without a separate continuation instruction.

### T2 input-stamp acceptance cross-check — iteration 5 active

The original T2 checklist wording is retained. Added a model-level assertion that `SendInputParams` accepts a `Long.MIN_VALUE` supplied event generation without validating it as authoritative. T4 remains responsible for proving the value is omitted by wire encoding. This was a test-only change. Attempt 11 at 12:46:54Z ran `mvn -B -ntp -pl spec -am clean -Dtest=Phase2ModelTest -Dsurefire.failIfNoSpecifiedTests=false test` — exit 0; reactor 3/3 modules successful and `Phase2ModelTest` 7/7, zero failures/errors/skips. The unchanged JSON-RPC model/Gson evidence remains attempt 9 (5/5) and attempt 10's legacy adapter regression remains 8/8.

**T2 completion:** both plan checkboxes are complete and verified. Cumulative Phase 2 iteration 5/10 is closed; T2 has 11 raw focused verification invocations; all original failures and results are preserved. Under the user's 2026-11-11 run-control clarification, raw attempt totals no longer impose a standalone stop. No defect reached three consecutive substantive no-progress attempts; current no-progress streak 0. Original stop 2026-11-11 13:30 UTC remains unchanged. The only deferred T2-adjacent proof is wire-level omission of supplied input generation, explicitly assigned to T4. No T4 or later task has started. Stop at T2 per the user's instruction; next action is a fresh independent Sol implementation review. No independent review occurred here.

### T2 reproduced-finding corrections and re-verification — iteration 6

The user supplied four read-only Java-probe findings after the prior T2 completion entry. Those findings reopened T2 acceptance; the entry above is retained as historical evidence, not treated as final acceptance. This correction is T2-only. No later Phase 2 task was started.

**Implemented corrections:**

- `StreamingEventKindTypeAdapter` now recognizes the complete unwrapped task-message-update shape before inspecting wrapper member names. This preserves task/context IDs, metadata and generation when the nested `message` key would otherwise be mistaken for the standalone Message wrapper.
- Timeline generations now require an exact integral int64 value using exact decimal conversion. Fractional values and values outside signed int64 fail parsing instead of truncating or wrapping.
- A dedicated `List<TimelineEntry>` adapter drops future timeline alternatives while validating their generation. Known `message`/`status` alternatives still use the strict entry adapter; an entry with no known payload and no unknown alternative remains malformed. No placeholder `TimelineEntry` is created.
- `SendInputParams` now requires all concrete artifact updates accompanying a message to use the same task/context identity, including when the opening message has no IDs. Existing provisional empty-ID updates remain accepted.

**Regression evidence:** the new tests reproduced the cross-update identity conflict, the unwrapped update decoding as `Message`, and unknown timeline entries aborting Task parsing. Focused tests also exercise fractional and out-of-range generations, an unknown entry interleaved with a known message entry, and rejection of an empty known timeline entry.

**Raw focused invocations 12–16** (prior 11 invocations remain in the ledger above):

1. At 13:16:40Z, clean T2 reactor command — exit 1; model suite 7 tests with the new cross-update assertion failing as expected; JSON-RPC module was not reached.
2. At 13:16:49Z, clean JSON-RPC focused command — exit 1; 7 tests, with the unwrapped update returning `Message` and unknown timeline kind throwing during Task decode. The combined test stopped before checking exact-generation assertions; those were separated for the subsequent run.
3. At 13:17:35Z, clean full focused command — exit 1; model 7/7 passed; serialization 8 tests with one failure, still decoding unwrapped message update as `Message`. Unknown-kind filtering, malformed-known rejection, and exact-generation assertions passed.
4. At 13:17:51Z, clean focused command including the legacy adapter suite — exit 0; model 7/7, serialization 8/8, existing streaming adapter 8/8. The unknown-kind test was then strengthened to place the future variant beside a known message entry.
5. At 13:18:17Z, final clean focused command including the legacy adapter suite — exit 0; reactor 4/4 modules, 23 selected tests (model 7/7, serialization 8/8, legacy streaming adapter 8/8), zero failures/errors/skips. Spotless reports skipped by project configuration. `git diff --check` exited 0.

The T2 plan checklist remains complete with fresh evidence. Cumulative Phase 2 iteration 6/10 is closed. T2 has 16 raw focused verification invocations total; the five results above extend, rather than reset, the historical 11. No-progress streak remains 0. The original conservative stop at 2026-11-11 13:30 UTC is unchanged. Model: Luna requested by the user; runtime model telemetry unavailable; no subagents used. T2 is implemented and verified for its planned model/Gson scope. Stop here: no T4, T3, or other next task. A fresh independent Sol implementation review remains outstanding; none occurred in this session. No commit, push, or PR.

Final current-tree verification at 13:18:56Z reran `mvn -B -ntp -pl spec,jsonrpc-common -am -Dtest=Phase2ModelTest,Phase2ModelSerializationTest,StreamingEventKindSerializationTest -Dsurefire.failIfNoSpecifiedTests=false test` — exit 0, reactor 4/4; 23 selected tests passed with zero failures/errors/skips. This included recompiling the changed JsonUtil and JSON-RPC test sources after the final unused-import cleanup. T2 now has 17 raw focused verification invocations total; no counter reset. Cumulative Phase 2 iteration 6/10 remains closed, no-progress 0.

### T2 unknown timeline payload qualification correction — iteration 7

The user reported that unknown timeline detection still treated any extra property as a future variant, allowing scalar, null, and array values to hide missing payloads. This finding reopened the T2 Gson acceptance check; the earlier iteration 6 completion is retained as historical evidence and superseded by this correction. Scope remains T2 only.

Added `timelineEntryNeedsAnObjectPayloadToQualifyAsAnUnknownKind`, covering `futureScalar: true`, `futureKind: null`, and `futureKind: []` as failures while the existing test continues to accept `futureKind: {}` and preserve neighboring known entries. The regression failed against the prior classifier at 13:22:32Z: the first scalar-only entry was silently accepted. `isUnknownTimelineKind` now requires an object-valued member other than `generation`, with no known `message` or `status` payload.

At 13:22:44Z, `mvn -B -ntp -pl spec,jsonrpc-common -am clean -Dtest=Phase2ModelTest,Phase2ModelSerializationTest,StreamingEventKindSerializationTest -Dsurefire.failIfNoSpecifiedTests=false test` exited 0. Reactor 4/4; 24 selected tests passed (model 7/7, serialization 9/9, existing streaming adapter 8/8), zero failures/errors/skips. Spotless was skipped by project configuration. The run also retained coverage for all four earlier reproduced findings.

Phase 2 cumulative iteration 7/10 is closed. Raw focused verification invocations are now 19 total: the 17 retained above plus this expected RED run and the passing run; no counter reset. No-progress streak remains 0. Original conservative stop 2026-11-11 13:30 UTC remains unchanged. Model: Luna requested by the user; runtime telemetry unavailable; no agents used. No T3/T4 or other next task started. All five reported T2 findings now have regression coverage and pass the focused checks; no independent implementation review has occurred. No commit, push, or PR.

### T2 review disposition — 2026-11-11T13:24:57Z

The user reports that the independent review passed. This user-provided disposition supersedes the outstanding-review statements above; no separate review activity or additional technical changes are claimed in this session. With the five reported findings fixed and the focused checks passing, T2 is accepted and closed. Counters remain at Phase 2 iteration 7/10, 19 raw focused verification invocations, and zero consecutive no-progress attempts. Stop at T2; do not begin another phase task without a separate instruction. No commit, push, or PR.

### Phase 2 T4 continuation authorization — 2026-11-11T13:46:54Z

The user explicitly replied “Keep going” to the request for authorization for a new bounded run. This authorizes the requested T4-only implementation under the existing maximum of 10 coherent iterations or two hours, whichever comes first. New run start: 2026-11-11T13:46:54Z; conservative deadline: 2026-11-11T15:46:54Z. This explicit authorization permits a new run allowance; it does not erase or rewrite prior history.

The prior Phase 2 run remains closed at iteration 7/10, with 19 raw focused verification invocations and zero consecutive no-progress attempts. Those counters and every prior failure remain retained. The current run starts at 0/10. T4 is the only authorized task in this run; preserve all T2 and other existing worktree changes. Model requested: Luna; no model switching or agents. No commit, push, PR, T3 or later task is authorized.


### Phase 2 T4 implementation checkpoint — 2026-11-11T14:05:36Z

T4 is implemented and its focused checks pass. T2 remains accepted per the user-reported review disposition above; no T2 findings were reopened. Only T4 files and this progress record were added to the implementation scope. No T3 or later capability was started.

The pinned RC is commit `78e1d6a1f7f118c8e9bc830b04a8579c1119cb78`. Rechecked all 18 retained upstream archive entries against `upstream-SHA256SUMS`; no mismatch. Compared the extracted RC proto with `spec-grpc/src/main/proto/a2a.proto`: the only differences are the required provenance comment and Java package adaptation. Regenerated with `mvn -B -ntp generate-sources -pl spec-grpc -Dskip.protobuf.generate=false`; only the RC was generated, and handwritten `mapper/` and `utils/` directories were kept intact. No 0.3 regeneration occurred. The generated `SendLiveMessage` service surface remains schema-only; no functional live support or advertisement was added.

T4 now includes protobuf mappings and companion request wrappers, explicit 1.1 request decoding, 1.0 legacy rejection for unrepresentable send/subscribe inputs, version-aware task/list/send/event decode results with per-task skipped-generation notes, malformed-known-payload checks, `StreamError` local-rejection outcomes, and the typed -32010 / ABORTED / 409 task-generation mismatch error. Outbound input artifact generations are reset to zero; optional zero presence and int64 JSON encoding are preserved. 1.0 decoding rejects 1.1-only JSON/stream variants and strips new protobuf fields to the legacy view. Existing legacy message-only and cursor-free entry points remain callable.

**Verification:**

- At 2026-11-11T14:05:04Z, the required focused command `mvn -B -ntp -pl spec-grpc -am -Dtest=Phase2CodecTest,StreamDecoderTest,StreamResponseMapperTest -Dsurefire.failIfNoSpecifiedTests=false test` exited 0. Reactor 5/5; 23 selected tests passed, zero failures/errors/skips.
- At 2026-11-11T14:05:16Z, `mvn -B -ntp -pl client/transport/rest,client/transport/grpc -am -DskipTests compile` exited 0. Reactor 9/9, including REST/gRPC client error mappers.
- `git diff --check` reports trailing whitespace on lines emitted by protoc in generated direct Java files. The same check excluding only those generated direct Java files is clean. No nested duplicate `mapper/mapper` or `utils/utils` directories exist.
- Proto source comparison confirms only the two planned adaptations; generated direct classes correspond to the pinned RC. No dependencies were upgraded.

**Invocation record for this T4 run:** 11 focused Maven invocations and two downstream compile invocations (13 total). Focused results in order: invocation 1 failed test compilation due a checked exception declaration; invocation 2 had one brittle whitespace-sensitive JSON assertion; invocation 3 passed 20 tests; invocation 5 found a switch-statement semicolon compile correction; invocation 6 passed 20 tests; invocation 7 found numeric metadata normalization in the added StreamError assertion; invocation 8 passed 23 tests; invocation 9 passed 23 tests after legacy-wrapper/version behavior changes; invocation 10 found a missing `StreamingEventKind` import; invocation 11 passed 23 tests; invocation 12 passed 23 tests with the unary/list envelope assertions. Downstream compile invocations 4 and 13 both passed, with invocation 13 run after final mapper changes. Ordinary compile corrections did not consume separate iterations. There were no three consecutive substantive no-progress attempts on one defect.

The current authorized run used one coherent implementation iteration (1/10), within its two-hour deadline of 2026-11-11T15:46:54Z. The prior Phase 2 count remains recorded at 7/10; cumulative count is 8/10. T4 has 11 focused invocations; retained Phase 2 focused-invocation history is 19 before this run and 30 total after it. No commit, push or PR.

### T4 verification follow-up — 2026-11-11T14:07:48Z

After the 14:05:36 checkpoint, extended the focused tests to exercise the explicit 1.1 `ObserveTaskRequest` wrapper and verify the legacy request parser rejects a cursor instead of dropping it; the same focused command passed at 14:06:47Z. Sorted imports in the touched JSON-RPC utility and tests, and reran the same focused command at 14:07:40Z: reactor 5/5, 23 tests passed, zero failures/errors/skips. The final T4 count is 13 focused invocations and two downstream compile invocations (15 total); retained Phase 2 focused invocation history is 19 before T4 and 32 after T4. The run remains at one coherent iteration (1/10), with no-progress streak 0; no budget renewal or scope change. The earlier 14:05:04 and 14:05:16 evidence entries remain historical and are supplemented by these final passing results.

### T4 review corrections — 2026-11-11T14:25:06Z

Applied the three corrections from the user-provided `.claude/architecture/a2a-1.1/phase-2/reviews/t4-review.md`; the review report itself was left unchanged.

- Stream `task` payloads now go through `decodeTaskJson` before strict `StreamResponse` parsing, and the resulting skipped-generation and unknown-sibling notes are retained. Regressions cover direct `decodeJson` and `JSONRPCUtils.decodeResponseEvent` with the same future timeline entry.
- Stream variant detection canonicalizes camelCase and protobuf snake_case names before unknown classification. It validates aliased status, artifact and message updates, rejects malformed aliases and duplicate/mixed aliases, and preserves the multiple-known-payload rule.
- An omitted repeated `tasks` field is treated as empty in both 1.0 and 1.1 JSON decoding. Tests use actual ordinary `JsonFormat.printer()` output for a page-size-10 empty `ListTasksResponse`, verify JSON-RPC list pagination, and confirm explicit non-array values still fail.

The new regressions were first run red at 14:22:49Z: `StreamDecoderTest` reported the streamed Task and alias failures plus the omitted-list exception. Each correction then passed its targeted regression (14:23:31Z, 14:23:53Z, and 14:24:07Z). At 14:24:52Z, the required T4 command `mvn -B -ntp -pl spec-grpc -am -Dtest=Phase2CodecTest,StreamDecoderTest,StreamResponseMapperTest -Dsurefire.failIfNoSpecifiedTests=false test` exited 0: reactor 5/5, 26 tests passed, zero failures/errors/skips. One intermediate targeted RPC run (14:23:10Z) exposed a malformed test envelope, which was corrected before the passing target run; no production change was made for that test setup error. The hand-written diff check is clean when excluding protoc-generated direct Java output, which retains its generator-emitted trailing whitespace.

This review correction used the second coherent iteration of the existing authorized T4 run (2/10), within its recorded 15:46:54Z deadline. The prior 1/10 iteration and all historical failures remain retained; no budget renewal, scope change, T3 work, commit, push or PR. Seven focused invocations were added by this correction, for 20 T4 and 39 retained Phase 2 focused invocations total. The no-progress streak remains zero.


### T3 prerequisite checkpoint — 2026-11-11T14:30Z

User requested T3 only, inline Luna, no agents/model switching, no commit/push/PR, and an explicit check that no unresolved T4 finding blocks T3. Verified the requested linked worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java` is on `support/a2a-1.1.0`. Read applicable global/worktree AGENTS.md, shared contracts and complete T3 brief, execution/model limits, latest checkpoint, and T4 review/correction evidence.

**Blocked before T3 implementation:** `phase-2/reviews/t4-review.md` explicitly leaves finding 4 open and states T4 requires correction before acceptance for dependent T3. The latest correction checkpoint (14:25:06Z) closes only findings 1–3. Current `StreamDecoder.java` checks at lines 327 and 348 still recognize only `startGeneration`, `endGeneration`, and `role`; neither recognizes `start_generation` or `end_generation`. A repository search found no corresponding alias regression in `StreamDecoderTest.java` and no later Phase 2 correction evidence. This is source/document inspection, not a fresh runtime reproduction. The user's statement that T4 is complete conflicts with these retained artifacts. T3-only scope does not authorize silently fixing T4 or waiving its review gate.

**Changes and verification:** only this appended checkpoint changed in this session. No implementation, fixture, test, generated source or review file edited. No Maven/test invocation; no T3 verification claimed. Git branch/rev-parse inspection confirmed branch and linked-worktree identity; source inspection confirmed the outstanding checks above. Existing changes and frozen fixtures preserved. No agents, model switching, commit, push or PR. Requested implementation model identifier: `gpt-6-luna`; independently verified runtime model telemetry unavailable, and no inline model-selection capability used. No claim that Luna execution occurred.

**Accounting:** no new coherent implementation iteration or raw test attempt consumed; existing authorized run remains 2/10 coherent iterations, 20 T4 focused invocations / 39 retained Phase 2 focused invocations, no-progress streak 0. Prior run and all failure history retained. Recorded run deadline remains 2026-11-11T15:46:54Z; no budget renewal inferred.

**Handoff:** close T4 review finding 4 with Task/artifact-update alias rejection regressions, inherited JSON-RPC coverage, focused T4 verification and correction disposition; then resume T3 only within the remaining authorized budget. T3 and T4b have not started. If the deadline expires, obtain an explicit budget decision before implementation.


### T4 review finding 4 correction — 2026-11-11T14:39:09Z

Added the missing snake_case artifact provenance checks to both explicit-1.0 JSON rejection sites in `StreamDecoder`: Task artifact arrays and artifact-update stream payloads now reject `start_generation` and `end_generation`, matching the existing camelCase checks. The change is limited to those predicates, their regression tests, and this progress entry. The review report remains unchanged. No source generation, frozen-fixture edits, T3/later work, commit, push or PR occurred.

Added two regression methods to `StreamDecoderTest`. The Task test covers all four spellings (`startGeneration`, `start_generation`, `endGeneration`, `end_generation`) through direct Task decoding and JSON-RPC unary, list, send, and stream decoding; it verifies explicit 1.1 preserves the supplied provenance and explicit 1.0 still decodes valid legacy Task JSON through those paths. The artifact-update test covers all four spellings in direct and JSON-RPC stream decoding, verifies 1.1 decoding, and verifies valid legacy artifact-update streams continue to decode under 1.0. Existing protobuf-object `decodeTask(..., "1.0")` projection coverage remains unchanged and continues to verify provenance is stripped from the legacy view.

**Commands and results:**

- `mvn -B -ntp -pl spec-grpc -am -Dtest=StreamDecoderTest -Dsurefire.failIfNoSpecifiedTests=false test` — first invocation failed during test compilation (`void cannot be dereferenced` in the newly added assertion); zero tests ran. Corrected the assertion setup without changing production code.
- The same focused command then ran 9 tests and failed 2 assertions as expected: `start_generation` was accepted at both the Task JSON and artifact-update stream rejection sites. This is the RED evidence for finding 4.
- After adding the two snake_case checks, the same focused command passed: `StreamDecoderTest` 9/9, zero failures/errors/skips; reactor 5/5.
- Required command `mvn -B -ntp -pl spec-grpc -am -Dtest=Phase2CodecTest,StreamDecoderTest,StreamResponseMapperTest -Dsurefire.failIfNoSpecifiedTests=false test` passed: `Phase2CodecTest` 6/6, `StreamDecoderTest` 9/9, `StreamResponseMapperTest` 13/13; 28 total, zero failures/errors/skips; reactor 5/5. Protobuf generation was skipped by the build configuration; Spotless was skipped by project configuration.

**Accounting:** this correction is coherent iteration 3/10 in the existing T4 run, not a reset or renewal. The original 2026-11-11T15:46:54Z deadline is unchanged; at this checkpoint the run is still within that deadline. Four focused Maven invocations were added (including the initial compile failure), bringing this run to 24 focused invocations and retained Phase 2 focused history to 43. The compile correction was ordinary test setup repair within this iteration. No-progress streak remains 0. Requested model: Luna; runtime model telemetry unavailable; no agents or model switches used. Finding 4 is ready for independent re-review and is not declared independently accepted. T4 remains pending that review; do not proceed with T3 until the prerequisite is independently re-reviewed.


### T4 independent review disposition — 2026-11-11T15:00:16Z

The user reports the independent review closed finding 4 and marked T4 ready for dependent T3 work. The updated `phase-2/reviews/t4-review.md` confirms all four findings closed and supersedes the prior T3 prerequisite block. It records a fresh `javac -proc:none` compilation of the decoder and focused test sources, reflective execution of 28/28 methods (Phase2CodecTest 6, StreamDecoderTest 9, StreamResponseMapperTest 13), and independent probes confirming all four explicit-1.0 provenance spellings reject while the previously reviewed decode behaviors remain correct. The review inspected the recorded Maven result of 28/28 across reactor 5/5; it did not run a new Maven command, full reactor, downstream compile, source generation or conformance suite. G1–G6 and historical corpus/live-peer/Java 21 limits remain as recorded.

This is a recorded review disposition only: no implementation or test command was run for this update. The earlier T3 prerequisite block is resolved, but T3 has not started. Preserve the original T4-run counters at 3/10 coherent iterations, 24 T4 / 43 retained Phase 2 focused invocations, no-progress streak 0, and deadline 2026-11-11T15:46:54Z; this review does not renew or alter them. The report update is review evidence, not a claim of full Phase 2 completion or protocol conformance.


### T3 budget checkpoint — 2026-11-11T15:52:34Z

The user requested T3 only in the existing `support/a2a-1.1.0` linked worktree, using Luna inline with no agents/model switching, retaining all existing changes and fixtures, and stopping before T4b. The requested executing-plans procedure was read. Current worktree remains the requested branch; it has the pre-existing Phase 1/T1/T2/T4 changes and unrelated generated/source/test changes described by the prior checkpoints. No implementation files or historical fixtures were edited in this checkpoint.

**T4 gate:** checked the complete current `phase-2/reviews/t4-review.md` disposition and its finding-4 correction evidence. Findings 1–4 are explicitly closed; the report states T4 is ready for T3. No unresolved T4 finding blocks T3.

**Budget stop:** the latest implementation checkpoint records the only authorized Phase 2 implementation run at 3/10 iterations, with 24 T4 / 43 retained Phase 2 focused invocations, no-progress streak 0, and a hard deadline of 2026-11-11T15:46:54Z. Current UTC time when checked was 2026-11-11T15:52:34Z. Per `implementation-loop.md`, after run-budget exhaustion a new explicit user budget decision is required; this T3 request specified no replacement allowance. The existing run was not renewed or reset. T3 production work and tests therefore did not start. No iteration or test invocation was consumed.

**Files and verification:** only this progress checkpoint was appended. Read applicable `AGENTS.md`, the plan's shared contracts and complete T3 brief, `implementation-loop.md`, the latest implementation checkpoint, and T4 review/correction evidence. Confirmed branch/worktree identity and reviewed the existing dirty status; no T3 build or test ran. Model requested: `gpt-6-luna`; runtime model telemetry was not independently available. No agents, model switches, commit, push or PR.

**Handoff:** T4 is unblocked and T3 is next. Obtain an explicit bounded allowance for a new implementation run, then execute T3's named projection, filtered publisher, binding, 0.3 conversion and webhook serialization work and the focused tests against unchanged historical fixtures. Preserve the existing 3/10 and all prior attempt counts; do not start T4b.


### T3 implementation authorization — 2026-11-11T15:55:23Z

The user explicitly authorized a new T3-only run after the prior run's expiry and approved up to one hour. Set this run's limit to **5 coherent iterations or one hour, whichever comes first**, starting 2026-11-11T15:55:23Z and ending no later than 2026-11-11T16:55:23Z. This is a new allowance only; prior Phase 2 history (10 cumulative iterations before this run; T4's 3/10 run, 24 T4 / 43 total focused invocations) remains intact. No-progress streak remains 0. Model requested by user: `gpt-6-luna`; no agents/model switching. T3 is the sole authorized task; stop before T4b. No commit, push or PR.


### T3 implementation checkpoint — 2026-11-11T16:16:52Z

Implemented T3 output projection, publisher, binding integrations, 0.3 conversion and webhook serialization changes in the files listed below. The latest T4 review and correction evidence confirms findings 1–4 closed; no unresolved T4 finding blocks T3. Existing changes and all historical fixtures remain preserved. The user authorized five coherent iterations or one hour, whichever comes first, through 16:55:23Z. Two coherent T3 iterations are complete; the current integration/verification iteration is paused for diagnosis. T4b has not started.

**Implementation present:**

- Added pure `VersionedOutputProjector` projections and finite-demand `ProjectingPublisher`; added the phase-2 output policy predicate.
- Integrated projection into JSON-RPC, REST and gRPC unary/list/config/stream paths, the 0.3 To10 request conversion path, legacy mapper defaults, and the 0.3 formatter.
- Added schema-path removal for 1.0 JSON/Gson and protobuf JSON boundaries, retaining 1.1 fields. Webhook dispatch resolves the stored version with the fixed 1.0 fallback, preserves the separate 0.3 formatter and suppresses filtered updates before HTTP.
- Added 1.0/1.1 actual JSON serializer tests, publisher demand/synchronous/filter/terminal/cancel tests, and actual outbound HTTP body tests. The 1.0 and 0.3 bodies are compared to unchanged fixtures under `phase-0/fixtures/expected`; no fixture was edited.
- Added handler projection coverage in JSON-RPC, REST and gRPC for send/get/list/cancel and stream task snapshots.

**Passing verification:**

- Projector/publisher focused command passed at 15:59:37Z (server-common reactor 10/10; 5 tests).
- JSON event serialization focused command passed at 16:01:15Z (jsonrpc-common reactor 4/4; 10 tests).
- Webhook focused command passed at 16:08:01Z (server-common reactor 10/10; 4 named tests, including fixture body comparisons and suppressed-event no-request assertions).
- `mvn -B -ntp -pl server-common -am -Dtest=ProjectingPublisherTest -Dsurefire.failIfNoSpecifiedTests=false test` first exposed a test expectation error (it expected no replenishment after a filtered item); corrected expected source count to 2, and the next focused run passed ProjectingPublisherTest 4/4, reactor 10/10.
- `mvn -B -ntp -pl server-common,compat-0.3/server-conversion -am -Dtest=ProjectingPublisherTest,Phase2ProjectionTest -Dsurefire.failIfNoSpecifiedTests=false test` compiled all 20 modules; publisher tests passed, while the initial 0.3 test attempt failed only because the test fixture path walked up one directory too few. `mvn -B -ntp -pl compat-0.3/server-conversion -am -Dtest=Phase2ProjectionTest -Dsurefire.failIfNoSpecifiedTests=false test` then passed Phase2ProjectionTest 1/1, reactor 20/20, comparing the captured outbound 0.3 body to the unchanged fixture.

**Current blocker and diagnosis gate:**

The required command `mvn -B -ntp -pl server-common,compat-0.3/server-conversion,transport/jsonrpc,transport/rest,transport/grpc -am -Dtest=VersionedOutputProjectorTest,ProjectingPublisherTest,Phase2ProjectionTest,PushNotificationSenderTest,PushNotificationPayloadFormatter_v0_3_Test,JSONRPCHandlerTest,RestHandlerTest,GrpcHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` has not completed. At 16:14:57Z it failed compilation in JSONRPCHandler lines 327 and 429: NullAway rejects the nullable `projectEvent` lambda result. Two subsequent targeted/full compiler invocations after changing the `ProjectingPublisher` function type also reported the same two errors. The current signature is `Function<T, @Nullable R>`; neither attempted generic annotation placement resolved it. A third proposed call-site type-inference patch was rejected by automatic review before application. Its stated reason was that the same NullAway issue had reached the three-consecutive-no-progress threshold and requires a Sol diagnosis checkpoint. Per `implementation-loop.md`, three consecutive substantive no-progress attempts on one defect require checkpointing dependent work for Sol diagnosis; ordinary compiler corrections do not consume separate coherent iterations. User instructions prohibit switching models or spawning agents, so no Sol diagnosis was attempted and the rejected patch was not applied. Do not retry this NullAway defect without resolving that gate.

The three compile attempts were correction attempts, not new coherent iterations. Coherent count remains 2/5 in this run; per-defect NullAway no-progress streak is 3. Record the run deadline as 2026-11-11T16:55:23Z. The exact full focused command above and `mvn -B -ntp -pl transport/jsonrpc -am -DskipTests compile` both exited nonzero on the same two NullAway diagnostics. `git diff --check` reports trailing whitespace only in pre-existing protoc-generated Java sources from T4; do not hand-edit generated output. The first local test HTTP stub compile error and fixture path error were setup corrections and subsequently passed.

**Changed T3 files:**

- `server-common/src/main/java/org/a2aproject/sdk/server/version/VersionedOutputProjector.java` (new), `ServerVersionPolicy.java`, `server-common/src/main/java/org/a2aproject/sdk/server/util/async/ProjectingPublisher.java` (new), `BasePushNotificationSender.java`.
- `server-common/src/test/java/org/a2aproject/sdk/server/version/VersionedOutputProjectorTest.java` (new), `server-common/src/test/java/org/a2aproject/sdk/server/util/async/ProjectingPublisherTest.java` (new), `server-common/src/test/java/org/a2aproject/sdk/server/tasks/PushNotificationSenderTest.java`.
- `jsonrpc-common/src/main/java/org/a2aproject/sdk/jsonrpc/common/json/JsonUtil.java`, `jsonrpc-common/src/test/java/org/a2aproject/sdk/jsonrpc/common/json/StreamingEventKindSerializationTest.java`.
- `spec-grpc/src/main/java/org/a2aproject/sdk/grpc/utils/ProtoJsonUtils.java`, `transport/jsonrpc-common` JSON-RPC response serializer utility (if distinct from the shared `jsonrpc-common` paths above).
- `transport/jsonrpc/src/main/java/org/a2aproject/sdk/transport/jsonrpc/handler/JSONRPCHandler.java`, `transport/jsonrpc/src/test/java/org/a2aproject/sdk/transport/jsonrpc/handler/JSONRPCHandlerTest.java`.
- `transport/rest/src/main/java/org/a2aproject/sdk/transport/rest/handler/RestHandler.java`, `transport/rest/src/test/java/org/a2aproject/sdk/transport/rest/handler/RestHandlerTest.java`.
- `transport/grpc/src/main/java/org/a2aproject/sdk/transport/grpc/handler/GrpcHandler.java`, `transport/grpc/src/test/java/org/a2aproject/sdk/transport/grpc/handler/GrpcHandlerTest.java`.
- `server-common/src/main/java/org/a2aproject/sdk/server/util/sse/SseFormatter.java`, `reference/jsonrpc/src/main/java/org/a2aproject/sdk/server/apps/quarkus/A2AServerRoutes.java`.
- `compat-0.3/server-conversion/src/main/java/org/a2aproject/sdk/compat03/conversion/Convert_v0_3_To10RequestHandler.java`, `PushNotificationPayloadFormatter_v0_3.java`, mapper files `ArtifactMapper_v0_3.java`, `TaskArtifactUpdateEventMapper_v0_3.java`, `TaskMapper_v0_3.java`, `TaskStatusUpdateEventMapper_v0_3.java`, `MessageSendConfigurationMapper_v0_3.java`, `TaskQueryParamsMapper_v0_3.java`.
- `compat-0.3/server-conversion/src/test/java/org/a2aproject/sdk/compat03/conversion/Phase2ProjectionTest.java` (new), `PushNotificationPayloadFormatter_v0_3_Test.java`.

The automatic review rejection and its reason are retained here. No workaround was applied. Do not commit, push, create a PR, edit frozen 0.3 wire types, or begin T4b. Once the diagnosis gate is addressed within the user's no-model-switch constraint, finish the exact focused T3 command, inspect discovered test counts and changed-file diff, correct the file inventory above (notably the JSON-RPC utility path), and append exact final results and any remaining issues. No claim of full T3 completion is made.


### T3 independent coverage follow-up — 2026-11-11T16:19:02Z

Continued only on T3 tests independent of the shared publisher NullAway diagnosis gate. Added direct 0.3 formatter regressions for `TaskMessageUpdateEvent` and USER artifact updates with snapshots, plus a sender test proving both updates bypass the stored-0.3 formatter and produce no HTTP body. No change to production code after the 16:16:52 checkpoint; no historical fixture changed.

**Exact command/result:** `mvn -B -ntp -pl compat-0.3/server-conversion -am -Dtest=Phase2ProjectionTest,PushNotificationPayloadFormatter_v0_3_Test -Dsurefire.failIfNoSpecifiedTests=false test` exited 0 at 16:19:02Z. Reactor 20/20; `Phase2ProjectionTest` 2/2 and `PushNotificationPayloadFormatter_v0_3_Test` 7/7; total 9 tests, zero failures/errors/skips. This includes actual outbound 0.3 Task body comparison to unchanged historical fixture and no-request/no-formatter-call assertions for suppressed updates. The initial invocation before adding the missing test import failed test compilation; corrected and reran successfully.

`mvn -B -ntp -pl transport/rest,transport/grpc -am -Dtest=RestHandlerTest,GrpcHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` exited nonzero at 16:17:44Z: gRPC handler lambdas at lines 440/464 have the same NullAway nullable projection error. `mvn -B -ntp -pl transport/rest -am -Dtest=RestHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` exited nonzero at 16:17:50Z with the same error at `RestHandler.java:852`. Thus all three bindings are blocked at compile time; their newly added handler boundary regressions have not yet executed. The required full T3 command has likewise not completed. No changes were made to address the shared defect after the automatic review gate.

The first checkpoint's changed-file inventory correction: the T3 serializer adapter is `spec-grpc/src/main/java/org/a2aproject/sdk/grpc/utils/JSONRPCUtils.java` (not a `transport/jsonrpc-common` path). `compat-0.3/.../PushNotificationPayloadFormatter_v0_3_Test.java` was modified in the follow-up above. `NonStreamingJSONRPCRequest.java` and `StreamingJSONRPCRequest.java` changes, and added wrapper types/model tests in `jsonrpc-common`, were pre-existing T1/T2 changes and are not T3 changes.

T3 raw Maven invocation history for this run is 17: projector/publisher RED and GREEN; JSON serialization RED and GREEN; webhook RED and GREEN; publisher edge test's initial assertion failure; initial 0.3 stub compile failure; 0.3 fixture path failure; 0.3 body fixture pass; full T3 compile attempt 1; full T3 compile attempt 2 after nullable wildcard change; JSON-RPC compile attempt after direct function signature change; Rest+gRPC targeted compile failure; REST targeted compile failure; 0.3 regression test's missing-import compile failure; 0.3 final pass. The three repeated NullAway corrections do not consume separate coherent iterations; two of five coherent iterations are complete. Per-defect no-progress streak remains 3 for the shared nullable callback compile issue. Run deadline remains 2026-11-11T16:55:23Z.

Current permitted next action requires resolving the automatic-review diagnosis gate. The user's explicit inline Luna/no-model-switch rule precludes the documented Sol diagnosis, so the NullAway call-site correction remains blocked. Do not retry it or use an indirect workaround under this authorization. The current branch retains the handler regressions, but they remain unverified. Preserve all existing changes and stop before T4b. No commit, push or PR.


### T3 NullAway diagnosis follow-up — 2026-11-11T16:34:36Z

The user authorized one more Luna-only investigation and said they can call Sol if I get stuck. I tested one evidence-based hypothesis: generic inference at the `ProjectingPublisher.mapNotNull` assignment loses the nullable result qualifier. Added explicit `<StreamingEventKind, StreamingEventKind>` type arguments at both JSON-RPC calls and ran `mvn -B -ntp -pl transport/jsonrpc -am -DskipTests compile`. It exited nonzero with the same NullAway errors at `JSONRPCHandler.java:327` and `:429`. The hypothesis is falsified; no further generic-inference variants will be tried here.

Additional root-cause evidence: the compiled `ProjectingPublisher.mapNotNull` signature includes a runtime-visible JSpecify `@Nullable` annotation on the `Function` result type argument. The same diagnostic occurs at REST (`RestHandler.java:852`) and gRPC (`GrpcHandler.java:440`, `:464`) nullable-projection lambdas. The 0.3 adapter methods in the unmarked compat package compiled successfully. This points to an unresolved interaction between NullAway's JSpecify handling of `java.util.function.Function` lambda results and `@NullMarked` callers; a suppression or API change is not justified without stronger diagnosis.

No T3 implementation iteration is completed by this compile attempt. The per-defect no-progress streak is now 4 including the post-checkpoint explicit-type-argument experiment; invocation history is 18 raw Maven commands for this run. Two of the five coherent iteration allowance remain recorded complete. The one-hour deadline remains 2026-11-11T16:55:23Z. All project/0.3 independent checks and the exact commands/results are preserved above. The full focused T3 command and all three new handler coverage tests remain unverified because their modules do not compile.

The current JSON-RPC call sites retain explicit generic arguments from the falsified experiment; they do not change runtime behavior but have not resolved compilation. Before further code changes, obtain the user's offered Sol diagnosis on whether NullAway requires a local suppression, a marked nullable functional interface, or another root-level adjustment that preserves T3's documented `Function<T, @Nullable R>` contract. The user still prohibits this agent from switching models or spawning agents, so stop here and await that diagnosis. No T4b, commit, push or PR.

### T3 compiler diagnosis and JDK matrix — 2026-11-11T16:51:18Z

The user explicitly requested a follow-up diagnosis and tested fix across the three CI JDK versions (17, 21, 25), then asked this agent to leave test-failure edits to another agent. No delegated agents or model switch were used in this turn; an exact controller model ID was not exposed by runtime metadata. This work addresses only the T3 compile blocker and does not start T4b or renew the prior T3 phase budget.

Root cause: Temurin 17.0.18 cannot reliably expose the `Function<T, @Nullable R>` type-use annotation from a compiled dependency to NullAway; `pom.xml`'s `old_jdk_support` profile removes `-XDaddTypeAnnotationsToSymbol=true` on that JDK. This matches upstream NullAway issue #1011. The same direct lambda call fails in marked transport packages, although `javap` shows `@Nullable` at `METHOD_FORMAL_PARAMETER` / `TYPE_ARGUMENT(1)`. An unmarked wrapper alone compiled but hid the check, so it was replaced with an explicitly nullable `NullableProjector` SAM and a distinct mapping method; the original `mapNotNull(Flow.Publisher<T>, Function<T, @Nullable R>)` remains public and unchanged in signature. `VersionedOutputProjector.projectStream` is explicitly `@NullMarked` and uses the new SAM; JSON-RPC, REST, and gRPC call it. The shared publisher's initially empty `AtomicReference` now has a nullable type argument, resolving the separate JDK 21/25 NullAway error.

An isolated negative control replaced the marked `projectStream` method's new-SAM call with the old `Function` call on JDK 17; NullAway again rejected the nullable lambda with the original return diagnostic. Restoring the new-SAM call recompiled successfully. This confirms the marked method is checked rather than merely bypassing analysis.

In a source-only `/private/tmp/a2a-t3-jdk-matrix` copy of the current production files, `mvn -B -ntp -pl transport/jsonrpc,transport/rest,transport/grpc -am -DskipTests clean compile` exited 0 on Temurin 17.0.18, 21.0.12, and 25.0.2; each run recompiled all 13 reactor modules. `mvn -B -ntp -pl transport/rest,transport/grpc -am -DargLine=-javaagent:/Users/kabir/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar -Dtest=ProjectingPublisherTest,VersionedOutputProjectorTest,RestHandlerTest,GrpcHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` exited 0 on each JDK: 7 shared tests, 62 gRPC tests, and 90 REST tests, with zero failures/errors/skips. The startup agent avoids this sandbox's Byte Buddy self-attachment failure.

The current JSON-RPC handler test source remains unmodified by this follow-up. Its new `legacyTaskOutputsAreProjectedAcrossUnaryListAndStreamBoundaries` test has an undeclared `InterruptedException` from `drainStream`, so the full JSON-RPC test class does not compile. An older `assertSame` in `testOnGetTaskSuccess` also fails after 1.0 output projection returns a value-equivalent copy. Temporary edits for both were reverted at the user's direction. JSON-RPC production compilation passes on all three JDKs; current-source JSON-RPC handler tests remain for the other agent. No full T3 validation or completion claim is made.

### T3 JSON-RPC test correction and focused gate — 2026-11-11T17:07:15Z

The user granted a new **T3-only allowance of three coherent iterations or 30 minutes, whichever first**. The first observed UTC time after authorization was 17:05:39Z; use 17:35:00Z as a conservative stop. This does not reset the expired 15:55:23Z–16:55:23Z run. This continuation consumed one coherent iteration and one raw Maven invocation; two coherent iterations remain unused. Preserve prior Phase 0/1/2 accounting, T3's earlier 2/5 iterations and 18 raw commands, and the historical four-attempt NullAway no-progress streak recorded above. The NullAway compile defect is resolved in present source; current no-progress streak is zero. T4 findings 1–4 remain closed.

Inspected `support/a2a-1.1.0` source before editing. In `transport/jsonrpc/src/test/java/org/a2aproject/sdk/transport/jsonrpc/handler/JSONRPCHandlerTest.java`, the new stream projection test still called `drainStream` without declaring `InterruptedException`, and `testOnGetTaskSuccess` still asserted object identity. Changed only that test file this turn: added `throws InterruptedException` to `legacyTaskOutputsAreProjectedAcrossUnaryListAndStreamBoundaries` and changed the task assertion to `assertEquals(MINIMAL_TASK, response.getResult())`. Projection returns an equal copy. The existing production compile fix, original public `ProjectingPublisher.mapNotNull(Flow.Publisher<T>, Function<T, @Nullable R>)` signature, nullable callback path, and nullable `AtomicReference` type were preserved.

Ran the exact planned focused command on Temurin 17.0.18:

```text
mvn -B -ntp -pl server-common,compat-0.3/server-conversion,transport/jsonrpc,transport/rest,transport/grpc -am -Dtest=VersionedOutputProjectorTest,ProjectingPublisherTest,Phase2ProjectionTest,PushNotificationSenderTest,PushNotificationPayloadFormatter_v0_3_Test,JSONRPCHandlerTest,RestHandlerTest,GrpcHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

It exited **0** at 17:07:03Z; all 23 reactor modules succeeded. Surefire discovered all eight selected classes: `PushNotificationSenderTest` 28, `ProjectingPublisherTest` 4, `VersionedOutputProjectorTest` 3, `JSONRPCHandlerTest` 74, `GrpcHandlerTest` 62, `RestHandlerTest` 90, `Phase2ProjectionTest` 2, and `PushNotificationPayloadFormatter_v0_3_Test` 7. Total: 270 reported tests, zero failures/errors, one skipped. The skip is the existing `@Disabled` JSON-RPC `testDefaultRequestHandlerWithCustomComponents`; 269 ran. Scoped `git diff --check` passed. Historical fixture paths have no Git status entries. Existing fixture comparisons and suppression coverage from the earlier T3 checkpoint are retained. The user reports clean production compiles on JDK 17.0.18, 21.0.12 and 25.0.2 and 7 shared plus 152 REST/gRPC handler tests on each; this continuation independently reran only the exact JDK 17 focused command, so the multi-JDK results remain user-reported here.

Current T3 diff scope includes the previously added projector/publisher and tests, server version/push notification handling, JSON schema-path serialization, 0.3 conversion/formatter and tests, and JSON-RPC/REST/gRPC handlers and tests. The only code edit in this continuation is the JSON-RPC test file; this checkpoint is the other changed file. No remaining failure is known in the named T3 focused gate. Independent task review, if required by the broader phase process, has not occurred in this continuation; no Phase 2 completion claim is made. Handoff: stop at T3, preserve all existing changes and historical fixtures, and seek separate authorization before T4b. Model requested: `gpt-6-luna`; active runtime model metadata was unavailable, so model identity is not independently verified. No agents, model switch, commit, push or PR.

### T3 review findings 1–2 corrections — 2026-11-11T17:14:27Z

The user supplied an independent T3 review with two P2 findings. Both were verified against current source. Finding 1: `ProtoJsonUtils.toJsonForVersion` treated unsupported versions as 1.0; a new `ProtoJsonUtilsTest.toJsonForVersion_rejectsUnsupportedVersion` reproduced the defect for `1.2` (6 tests, 1 expected failure, no errors; Maven exit 1 at 17:13:09Z). The encoder now accepts only exact `1.0` and `1.1` and throws `IllegalArgumentException` for other versions before encoding. The same focused command, `mvn -B -ntp -pl spec-grpc -am -Dtest=ProtoJsonUtilsTest -Dsurefire.failIfNoSpecifiedTests=false test`, then exited 0 at 17:13:21Z: 6/6 tests passed, reactor 5/5.

Finding 2: the actual outbound webhook body tests lacked zero-stamp and same-named content-key evidence. Added `PushNotificationSenderTest.actualWebhookBodiesOmitZeroStampsWithoutRemovingSameNamedContentKeys`. It captures actual 1.0 and 1.1 HTTP bodies for task, status update and artifact update with zero generation values. It asserts 1.0 schema fields are absent, 1.1 zero stamps remain, and `generation`, `timeline`, `startGeneration`, `endGeneration` and `role` keys remain in Task/Artifact/Part metadata and Part data. `mvn -B -ntp -pl server-common -am -Dtest=PushNotificationSenderTest -Dsurefire.failIfNoSpecifiedTests=false test` exited 0 at 17:13:56Z: 29/29 tests passed, reactor 10/10. Frozen fixture comparisons in the same suite remain unchanged.

After both fixes, the exact T3 focused command recorded above was rerun unchanged on Temurin 17.0.18 and exited 0 at 17:14:21Z: all 23 reactor modules succeeded. Eight selected Surefire reports show 271 reported tests, zero failures/errors, and one existing `@Disabled` JSON-RPC skip (270 executed). Scoped `git diff --check` passed, and historical fixture paths have no Git status entries. Review fixes changed only `spec-grpc/src/main/java/org/a2aproject/sdk/grpc/utils/ProtoJsonUtils.java`, `spec-grpc/src/test/java/org/a2aproject/sdk/grpc/utils/ProtoJsonUtilsTest.java`, and `server-common/src/test/java/org/a2aproject/sdk/server/tasks/PushNotificationSenderTest.java`, plus this checkpoint. No demonstrated binding leak was claimed for finding 1; the direct encoder contract is now enforced.

Allowance accounting: the user's 17:05 continuation had three coherent iterations; the prior JSON-RPC correction used one, and these two independently scoped review corrections used the remaining two. **3/3 coherent iterations are consumed; no implementation allowance remains**, even though the conservative 17:35:00Z time stop had not arrived. Raw Maven invocations for this allowance total five: initial exact T3 gate, expected RED `ProtoJsonUtilsTest`, GREEN `ProtoJsonUtilsTest`, webhook focused test, and final exact T3 gate. Expected RED is recorded, not a no-progress attempt; current per-defect no-progress streaks are zero. Preserve all earlier attempt/failure history. Model requested: `gpt-6-luna`; runtime model metadata remains unavailable. T3 review findings 1–2 are addressed by code and tests; no remaining T3 focused-gate failure is known. Stop at T3. No T4b, agents, model switch, commit, push or PR. Separate authorization is required for later implementation work.

### T3 independent re-review disposition — 2026-11-11T17:18:18Z

The user independently rechecked the updated T3 sources and saved Surefire XML. Both prior P2 findings are **closed**: `ProtoJsonUtils.toJsonForVersion` rejects unsupported versions, and `PushNotificationSenderTest` covers actual 1.0/1.1 outbound bodies for zero stamps and same-named metadata/Part data keys. The reviewer found no remaining actionable T3 finding and made no file changes. Their report confirms the retained eight-class result of 271 tests reported, zero failures/errors and one existing skip; they did **not** rerun Maven. The last fresh Maven evidence remains the exact T3 focused command completed at 17:14:21Z above.

T3 implementation, focused verification and independent review are complete. This ledger-only disposition does not renew or consume implementation iterations: the 17:05 allowance remains exhausted at 3/3, with five raw Maven invocations and all historical failure accounting unchanged. T4b remains unstarted. No commit, push or PR.


### T4b implementation checkpoint — 2026-11-11T17:47:16Z

Implemented only T4b under the new coordinated run that began at 17:28Z, with a 19:28Z deadline. This is coherent iteration 1; the prior T3 allowance remains exhausted at 3/3 and its history is retained. No frozen fixture was edited.

**Changes:** added default `RequestHandler.onSendInput`, `onSendInputStream` and `onObserveTask` callbacks with conservative legacy delegation and `UnsupportedOperationError` for unsupported advanced input/cursor semantics. JSON-RPC, REST and gRPC now dispatch selected 1.1 wrappers, preserve tenant/version handling, reject unsupported query/list/delivery options before callbacks, reject cursor observation until its phase, and reject REST/gRPC/JSON-RPC 1.1 timeline or delivery-version input options before custom handlers. Exact 1.0 parsing/advanced-option rejection remains in place. REST has compatible query/subscription overloads and route forwarding. Added default, boundary, and three handler regressions.

T4b-owned files: `server-common/src/main/java/org/a2aproject/sdk/server/requesthandlers/RequestHandler.java`, new `server-common/src/test/java/org/a2aproject/sdk/server/requesthandlers/Phase2HandlerDefaultsTest.java`, `spec-grpc/src/main/java/org/a2aproject/sdk/grpc/utils/JSONRPCUtils.java`, JSON-RPC/REST/gRPC transport handler sources and their three named handler tests, both reference route sources and their new `Phase2BoundaryTest.java` files, plus this checkpoint. All other existing worktree changes were preserved.

**Verification:** the exact required command `mvn -B -ntp -pl reference/jsonrpc,reference/rest,transport/grpc -am -Dtest=Phase2HandlerDefaultsTest,Phase2BoundaryTest,JSONRPCHandlerTest,RestHandlerTest,GrpcHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` exited 0 at 17:47:01Z. Reactor 22/22 succeeded on Temurin 17.0.18. The five report classes contain 243 tests: 242 executed, zero failures/errors, and one existing disabled test (`JSONRPCHandlerTest.testDefaultRequestHandlerWithCustomComponents`). Scoped `git diff --check` passed. Individual counts: defaults 4, JSON-RPC boundary 2, REST boundary 2, JSON-RPC handler 76 (one disabled), REST handler 94, gRPC handler 65.

**Raw T4b Maven invocation record (14 total, all under 20 minutes):** (1) expected red compile failure before default callbacks existed; (2) four default-test errors from a message fixture with no parts; (3) defaults passed 4/4; (4–6) two production compile failures from missing `UnsupportedOperationError` imports, then compile passed; (7–8) route test compile error for a missing checked-exception declaration, then route tests passed 4/4; (9–10) exact gate exposed three legacy `historyLength` error-message regressions, then passed after preserving its original message; (11) new JSON-RPC handler test used a 1.0-only card; (12) three gRPC status assertions lacked selected-version interceptor metadata; (13) three REST tests used 1.0-only card fixtures; (14) exact gate passed. Fixture, import and context corrections were made on the next attempt. Current no-progress streak is 0 for every defect; no same-defect three-attempt streak occurred. Earlier failures remain recorded here, not erased by the final pass.

No T4b issue is currently known. Independent implementation review remains pending. Stop for review; no Phase 3, commit, push or PR. Requested model: `gpt-6-luna`; runtime model telemetry was unavailable, so execution identity is unverified. No subagents or model switch were used.

### T4b independent review disposition — 2026-11-11

Independent Sol review is complete with **two P2 findings; T4b is not yet accepted for T5**. The report is [phase-2/reviews/t4b-review.md](phase-2/reviews/t4b-review.md). Unsupported selected-1.1 streaming artifacts/guards can reach `DefaultRequestHandler.authorizeTaskAccess` and `taskStore.get` before the default companion callback rejects them; explicit 1.0 JSON-RPC/gRPC subscription cursors are dropped by legacy `TaskIdParams` conversion and the old subscription runs. Correct these with focused zero-store/zero-executor and exact-version error tests, then rerun the named T4b gate and request re-review. Current saved six-class Surefire XML confirms 243 reported, 242 executed, zero failures/errors, one existing disabled test; the reviewer did not rerun Maven. Review only: no implementation iteration, production/test edit, fixture change, commit, push or PR. Requested reviewer model `gpt-6-sol` at medium reasoning; runtime model/speed telemetry unavailable, so model identity is not independently verified. Preserve the 17:28Z–19:28Z coordinated run and its one consumed coherent implementation iteration.


### T4b independent-review corrections — 2026-11-11T17:57:37Z

Corrected both P2 findings from [the T4b review](phase-2/reviews/t4b-review.md) as coherent iteration 2 of the 17:28Z–19:28Z coordinated run. Iteration 1, its 14 Maven invocations, prior findings, and the per-defect no-progress streak of 0 are retained. Five raw Maven invocations were added in this correction; T4b total is 19. All commands completed within the 20-minute limit. No Phase 3/T5 work, fixture edits, commit, push, or PR.

**Changes:** selected-1.1 streaming handlers and the actual JSON-RPC route now reject artifacts and advanced input configuration (including ifGenerationMatch) before authorizeTaskAccess; ordinary message-only authorization and effective-tenant dispatch remain unchanged. The 1.0 JSON-RPC parser rejects a present non-null lastSeenGeneration before legacy conversion; gRPC checks protobuf field presence before legacy dispatch, including explicit zero. Boundary tests verify the unsupported error/status and zero handler-delegate interactions, including no authorization or send/subscription callback (the only paths onward to TaskStore/AgentExecutor). The JSON-RPC route tests exercise the actual route.

Correction-owned files: reference/jsonrpc/src/main/java/org/a2aproject/sdk/server/apps/quarkus/A2AServerRoutes.java, its Phase2BoundaryTest.java, spec-grpc/src/main/java/org/a2aproject/sdk/grpc/utils/JSONRPCUtils.java, and the JSON-RPC/REST/gRPC transport handlers and their named tests; this progress record. No other Phase 2 source or historical fixture was edited for this correction.

**Raw correction invocations and failures:** (1) focused RED run failed as expected: JSONRPCHandlerTest.selected11StreamingArtifactsAndGuardsStopBeforeAuthorization observed authorization and send-handler interactions before validation. (2) exact gate first attempt stopped at test compilation: Phase2BoundaryTest lacked a times import and propagated JsonProcessingException; fixed. (3) exact gate reached gRPC and the new 1.0 cursor test expected FAILED_PRECONDITION, while the existing v1.0 unsupported-operation mapping is UNIMPLEMENTED/UNSUPPORTED_OPERATION; assertion corrected to the established mapping. (4) exact gate reached REST; the regression inspected getBody() although streaming errors are emitted as SSE events; the test now consumes the publisher and checks its error event. (5) exact gate passed.

**Verification:** the final exact command, mvn -B -ntp -pl reference/jsonrpc,reference/rest,transport/grpc -am -Dtest=Phase2HandlerDefaultsTest,Phase2BoundaryTest,JSONRPCHandlerTest,RestHandlerTest,GrpcHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test, exited 0 at 17:56:30Z; reactor 22/22 succeeded. Six selected reports: defaults 4, JSON-RPC boundary 4, REST boundary 2, JSON-RPC handler 77 (one existing disabled test), REST handler 95, gRPC handler 67 = **249 reported, 248 executed, zero failures/errors, one disabled**. Scoped git diff --check passed on modified tracked sources/tests; the new boundary test has no trailing whitespace. A repository-wide diff check still reports trailing whitespace in pre-existing generated protobuf Java diffs under spec-grpc/src/main/java/org/a2aproject/sdk/grpc/; those generated files were outside this correction.

No unresolved T4b implementation issue is known. **Next action: stop for Sol re-review; T5 remains blocked pending its disposition.** Requested implementation model/role: gpt-6-luna, bounded correction. Runtime model metadata was unavailable, so execution identity is not independently verified. Reviewer requested gpt-6-sol at medium reasoning in the existing review; its runtime identity was also unverified. No model switch or agents.

### T4b focused correction re-review — 2026-11-11

Independent Sol re-review closed both prior P2 findings. The actual JSON-RPC route and all three binding handlers now reject unsupported selected-1.1 streaming artifacts/guards before `authorizeTaskAccess` or downstream core access; 1.0 JSON-RPC and gRPC reject a present subscription cursor, including zero, before legacy conversion/dispatch. The correction tests verify binding errors and zero authorization/delegate interactions. [Review detail](phase-2/reviews/t4b-review.md). Current saved six-class Surefire XML confirms **249 reported, 248 executed, zero failures/errors, one existing disabled**. The correction checkpoint records the exact T4b gate exit 0, reactor 22/22; this review did not rerun Maven. **T4b review accepted within the focused scope; pause after T4b as requested.** No new implementation iteration, code edit, fixture change, commit, push, PR or T5 work. Requested reviewer model `gpt-6-sol` at medium reasoning; no runtime model/speed telemetry was available.

### T5 SPI/core checkpoint — 2026-11-11T18:15:16Z

Implemented the SPI/core portion of T5 as coherent iteration 3 of the coordinated run that began at 17:28Z, with its original 19:28Z deadline. T4b review remains accepted as recorded above; this work does not change that disposition or renew the run budget. Requested implementation model: `gpt-6-luna` at medium reasoning. Runtime model/speed metadata is unavailable, so execution identity is not independently verified. No agents were used by this T5 subtask and no model switch was made; there is no commit, push, PR, Phase 3, or T6 public send-input path. The parent separately delegated the out-of-scope mapper prerequisite.

**Changes:** added the additive `VersionedClientTransport` defaults and SPI values `TransportCall`, `TransportObservation`, `TransportFrame`, `TransportEnd`, `SpiCallOptions`, `TransportResult`, and `TaskDecodeEvidence`. Unary envelopes defensively copy evidence and validate one matching evidence item per returned task, in order; direct `Message` values require an empty evidence list. Observation handles enforce one subscriber, demand-serialized delivery, finite frame/byte buffering, a local opening deadline, and exactly-once completion/cancellation. Added `UNKNOWN_VARIANT_SKIP` to the feature enum, but did not advertise it from providers; existing profiles stay `ORDINARY` until client bindings carry decode evidence. `SelectedClientTransport` forwards each added operation through the wrapped instance, preserving call/result/observation identity. Public get/list keep their signatures and use decoded companions only on a selected 1.1 transport advertising `UNKNOWN_VARIANT_SKIP`, unwrapping at the existing return boundary; 1.0 still uses the legacy methods. Existing `ClientTransportPolicy` intersection logic was left intact and is covered by a non-escalation regression. The API migration report now records these additive contracts.

**Files changed by this subtask:** `client/transport/spi/src/main/java/org/a2aproject/sdk/client/transport/spi/{ClientFeature,VersionedClientTransport,SpiCallOptions,TaskDecodeEvidence,TransportCall,TransportEnd,TransportFrame,TransportObservation,TransportResult}.java`; new `client/transport/spi/src/test/java/org/a2aproject/sdk/client/transport/spi/TransportObservationTest.java`; `client/base/src/main/java/org/a2aproject/sdk/client/{Client,SelectedClientTransport}.java`; `client/base/src/test/java/org/a2aproject/sdk/client/ClientTransportPolicyTest.java`; `.claude/architecture/a2a-1.1/phase-2/api-migration.md`; this checkpoint. No OTEL files or frozen fixtures were changed.

**Verification:** final bounded command:

```text
mvn -B -ntp -pl client/base,client/transport/spi -am -Dtest=ClientTransportPolicyTest,TransportObservationTest,LegacyProviderCompatibilityTest#legacyProviderRejectsOneOneBeforeCreate+unchangedImplicitConstructorDelegateFailsItsReportedVersionMismatch+unchangedBinaryWrapperRemovesOneOneAndThrowingWrapperNeverBypassesIt -Dsurefire.failIfNoSpecifiedTests=false test
```

Exited 0 at 18:16:35Z; reactor 12/12. Surefire: `TransportObservationTest` 7, `ClientTransportPolicyTest` 9, and three passing `LegacyProviderCompatibilityTest` methods = **19 reported, 19 executed, zero failures/errors/skips**. These cover unsupported defaults, cancellation/terminal exactness, bounded serialized observation, one-subscriber behavior, evidence immutability/alignment, wrapper envelope/context forwarding, capability non-escalation, public 1.1 get/list unwrapping, and old binary provider behavior. Scoped `git diff --check` exited 0. All commands completed well below the 20-minute cap.

**Raw invocation record (10 Maven invocations; all failures retained):** (1) expected TDD RED, missing planned SPI types/default methods; (2) compile stopped on Java 17 nested `Flow.Subscriber` nullness annotation syntax; corrected to `Flow.@Nullable Subscriber`; (3) test compile stopped on incorrect Message role/Part fixture names and missing `AtomicReference`; corrected; (4) the exact client/base+SPI named selector reached tests but one legacy test failed (4 legacy tests, one error; 8 policy and 5 observation tests passed) with the tenant-null NPE below; (5) independently scoped selector exposed a wrong TaskState fixture constant; corrected; (6) SPI/base-owned tests passed 5+9 = 14; (7) filtered legacy+owned suite exposed CompletionStage minimal-stage wrapping cancellation in `CompletionException`; assertion corrected to verify its `CancellationException` cause; (8) filtered suite passed 3 legacy + 9 policy + 7 observation = 19; (9) after adding result/evidence alignment checks, that selector passed 19/19; (10) after adding direct optional-capability escalation coverage, the same selector exited 0 at 18:16:35Z (19/19). All routine compile/test fixture corrections were narrowed and fixed on the next invocation; current no-progress streak is 0 for each defect.

**Unresolved focused gate:** the full client/base+SPI selector remains blocked by the 1.0 legacy `getTask` NPE in `LegacyProviderCompatibilityTest.unchangedConstantProviderLinksAndEmitsOneZeroThroughPublicClient`. A source trace confirms it takes the unchanged 1.0 legacy path (`Client.getTask` → `SelectedClientTransport.getTask` → legacy wrapper → `JSONRPCTransport.getTask`); the failure occurs earlier in the current dirty spec-grpc `TaskQueryParamsMapper` output. Its checked-in interface differs from HEAD by removing `@BeanMapping(builder = ...)` and the null-conditional tenant mapping. Current MapStruct output at `spec-grpc/target/generated-sources/annotations/.../TaskQueryParamsMapperImpl.java:49` unconditionally invokes `getTaskRequest.setTenant(domain.tenant())`, and the generated proto builder rejects the null tenant from the frozen old call. This is a pre-existing dirty mapper regression outside the T5 SPI/core scope, not a stale generated file or a T5 decoded-read effect. The exact failing test and stack trace were sent to the parent; a separate Luna agent is correcting that prerequisite. Three other legacy binary-compatibility methods passed. Do not call T5's full selector green until it is rerun after the mapper correction.

**Handoff:** the SPI/core boundary is coherent and independently green. Next authorized action is the OTEL subtask: forward all new call and envelope methods through both decorators and add the propagation/telemetry tests. The parent is separately addressing the traced mapper prerequisite; after that correction, rerun the full T5 selector including all five named test classes. Preserve the 17:28Z start, 19:28Z deadline, prior iteration/attempt history, and all existing dirty work and fixtures.

### T5 mapper prerequisite correction — iteration 4 — 2026-11-11T18:19:37Z

Restored the optional-tenant null condition in the already-dirty `TaskQueryParamsMapper.toProto` interface, keeping the T5 `currentGeneration` and `timelineLength` mappings unchanged. Added `TaskQueryParamsMapperTest.toProtoWithoutTenantPreservesCurrentGenerationAndTimelineLength`. The generated MapStruct implementation is build output only and was not edited by hand. This is the prerequisite isolated in the T5 SPI/core checkpoint above; no OTEL, other T5, T6, frozen-fixture, commit, push, or PR work was done.

**Verification and raw invocation accounting:** four Maven invocations were added to the preceding ten (14 cumulative for the coordinated run; previous history and failures remain retained): (11) focused mapper regression was expected RED: one test errored with `NullPointerException` at generated `GetTaskRequest.Builder.setTenant`, confirming the missing guard; (12) same mapper test passed 1/1 after the interface correction, reactor 1/1; (13) exact `LegacyProviderCompatibilityTest#unchangedConstantProviderLinksAndEmitsOneZeroThroughPublicClient` passed 1/1, reactor 12/12; (14) exact T4 command `mvn -B -ntp -pl spec-grpc -am -Dtest=Phase2CodecTest,StreamDecoderTest,StreamResponseMapperTest -Dsurefire.failIfNoSpecifiedTests=false test` passed 28/28 (6 + 9 + 13), reactor 5/5. No invocation timed out and no compile/setup failure occurred. Inspected regenerated `TaskQueryParamsMapperImpl.toProto`: tenant assignment now sits inside `if (domain.tenant() != null)`, and current-generation/timeline-length checks remain present. Scoped `git diff --check` passed. The defect was resolved in one correction cycle; no-progress streak remains 0.

**Checkpoint and handoff:** requested implementation model `gpt-6-luna`, medium reasoning; observable runtime model/speed telemetry is unavailable, so execution identity is unverified. This counts as coherent iteration 4 of the existing 17:28Z–19:28Z run (first three remain counted; no renewal). Mapper prerequisite is cleared. T5's full five-class selector remains pending the separately scoped OTEL forwarding/tests; next action is that OTEL work, then rerun the full T5 selector. No claim of full T5 acceptance is made here.

### T5 OpenTelemetry forwarding checkpoint — iteration 5 — 2026-11-11T18:26:07Z

Implemented the OpenTelemetry portion of T5 within the original coordinated 17:28Z–19:28Z run. Iterations 1–4, prior invocation history, and all earlier findings remain unchanged. T4b review remains accepted and the mapper prerequisite remains corrected as recorded above. Requested implementation model: `gpt-6-luna` at medium reasoning. Runtime model/speed telemetry is unavailable, so execution identity is not independently verified. No agents or model switch were used.

**Changes:** `OpenTelemetryClientTransport` now forwards all six Phase 2 operations. The decoded task and list results retain the exact `TransportResult` envelope; async call wrappers relay the original envelope and forward cancellation, ending spans once on completion, error, or cancellation. Observation spans end once on completion, error, deadline, or cancellation while the same observation object and frames remain visible to callers. Tracing records the operation duration and closes spans around unsupported delegates and synchronous failures. `OpenTelemetryClientPropagatorTransport` forwards the six operations through its versioned delegate with injected trace headers and a copied caller context, preserving tenant/auth state. Both wrappers retain their existing protocol version and capability snapshots and do not advertise new features.

**Files changed by this subtask:** `extras/opentelemetry/client/src/main/java/org/a2aproject/sdk/extras/opentelemetry/client/OpenTelemetryClientTransport.java`; `extras/opentelemetry/client-propagation/src/main/java/org/a2aproject/sdk/extras/opentelemetry/client/propagation/OpenTelemetryClientPropagatorTransport.java`; `extras/opentelemetry/client/src/test/java/org/a2aproject/sdk/extras/opentelemetry/client/OpenTelemetryClientTransportTest.java`; new `extras/opentelemetry/client-propagation/src/test/java/org/a2aproject/sdk/extras/opentelemetry/client/propagation/Phase2PropagationTest.java`; this checkpoint. No frozen fixtures, signatures, provider defaults, commits, pushes, PRs, T6, or Phase 3 work were changed.

**Verification:** the exact T5 command `mvn -B -ntp -pl client/base,extras/opentelemetry/client,extras/opentelemetry/client-propagation -am -Dtest=ClientTransportPolicyTest,LegacyProviderCompatibilityTest,TransportObservationTest,Phase2PropagationTest,OpenTelemetryClientTransportTest -Dsurefire.failIfNoSpecifiedTests=false test` exited 0 at 18:25:56Z; reactor 16/16. Surefire reports confirm `LegacyProviderCompatibilityTest` 4, `ClientTransportPolicyTest` 9, `TransportObservationTest` 7, `OpenTelemetryClientTransportTest` 28, and `Phase2PropagationTest` 2 = **50 reported, 50 executed, zero failures/errors/skips**. Coverage includes all six delegated operations through both wrappers, envelope identity/evidence, caller tenant/auth state and W3C trace headers, and span cleanup on unary and observation completion/error/cancel. Scoped `git diff --check` passed on the two production files and the extended existing test; the new test has no trailing whitespace. Commands completed well below the 20-minute limit.

**Raw invocation additions:** five Maven invocations were added to the preceding 14 (19 cumulative; all under 20 minutes): (15) expected TDD RED—the client selector passed its 13 base cases, `OpenTelemetryClientTransportTest` reported two expected unsupported-default errors for `sendInput`/`getTaskAsync`, and propagation was skipped after the module failure; (16) exact selector passed 49/49 after implementation; (17) exact selector passed 49/49 after extending propagation checks across all six operations; (18) exact selector failed one new observation cleanup assertion because the test completed an observation that had not been passed through the decorator; the test fixture was corrected; (19) exact selector passed 50/50 with all selected classes present. The two failed invocations and the fixture-only correction remain in this record; no implementation defect remains known, and the no-progress streak for the fixture issue is 0.

**Handoff:** the T5 focused gate is green, with no known unresolved OTEL issue. Next action is independent Sol review of the full T5 scope; stop here pending that review. Preserve the original deadline and do not begin T6 or Phase 3. No commit, push, or PR was created.

### T5 independent review disposition — 2026-11-11

Independent Sol review found **two P2 observation lifecycle findings; T5 is not yet accepted**. [Review detail](phase-2/reviews/t5-review.md). A failed observation with a buffered frame and zero demand resolves its completion stage but never delivers subscriber `onError`; opening deadline and buffer overflow terminate without invoking the read-detach action. A standalone Java probe against current compiled classes confirmed `completion=FAILED, onError=0, detach=0` and `completion=DEADLINE, detach=0`. Correct the lifecycle with focused exact-once terminal/detachment tests, then rerun the named T5 selector and request focused re-review. The saved five-class Surefire XML confirms 50 reported/executed with zero failures/errors/skips; iteration 5 records exact selector exit 0 and reactor 16/16, but these cases were absent. This review did not rerun Maven or alter implementation, tests, fixtures, commits, pushes, PRs, T6, or Phase 3. Preserve the 17:28Z–19:28Z run and five coherent iterations; review consumed no implementation iteration. Requested reviewer model `gpt-6-sol` at medium reasoning; runtime model/speed metadata unavailable, so identity is not independently verified. Pause after T5 as requested.


### T5 observation lifecycle correction — iteration 6 — 2026-11-11T18:34:00Z

Corrected both P2 findings from the [T5 independent review](phase-2/reviews/t5-review.md) within the original coordinated run (start 17:28Z, deadline 19:28Z). This is iteration 6; prior iterations, failures, and the 19-invocation history remain intact. Requested implementation model: `gpt-6-luna`, medium reasoning. Runtime model/speed metadata is unavailable, so execution identity is not independently verified. No subagents or model switch were used.

**Changes:** `TransportObservation.finish` now drops buffered frames for FAILED and DEADLINE outcomes as it already did for CANCELLED, so terminal errors reach the subscriber without waiting for frame demand and stale frames cannot precede the error. Opening-deadline expiry, buffer overflow, and accepted explicit cancellation invoke a shared `AtomicBoolean`-guarded read-detach action; the first terminal result remains immutable, so a later cancel cannot rewrite DEADLINE or overflow FAILED. Completion remains once-only, and existing single-subscriber serialized delivery and finite buffering remain in place.

**Files changed in this correction:** `client/transport/spi/src/main/java/org/a2aproject/sdk/client/transport/spi/TransportObservation.java`, its `client/transport/spi/src/test/java/org/a2aproject/sdk/client/transport/spi/TransportObservationTest.java`, and this checkpoint. No other source/test files, frozen fixtures, T6/Phase 3 work, commits, pushes or PRs were changed by this correction.

**Verification:** focused selector `mvn -B -ntp -pl client/transport/spi -am -Dtest=TransportObservationTest -Dsurefire.failIfNoSpecifiedTests=false test` passed with 9/9 tests, zero failures/errors/skips, reactor 7/7. Then the exact required T5 selector `mvn -B -ntp -pl client/base,extras/opentelemetry/client,extras/opentelemetry/client-propagation -am -Dtest=ClientTransportPolicyTest,LegacyProviderCompatibilityTest,TransportObservationTest,Phase2PropagationTest,OpenTelemetryClientTransportTest -Dsurefire.failIfNoSpecifiedTests=false test` exited 0 at 18:33:17Z, reactor 16/16. Parsed current Surefire XML: `ClientTransportPolicyTest` 9, `LegacyProviderCompatibilityTest` 4, `TransportObservationTest` 9, `Phase2PropagationTest` 2, `OpenTelemetryClientTransportTest` 28 = **52 reported/executed, zero failures/errors/skips**. The new tests exercise no-demand buffered failure followed by later demand, opening deadline delivery/detachment, overflow detachment with FAILED preserved, and a deterministic deadline-versus-cancel race. Target file trailing-whitespace scan and scoped `git diff --check` on this checkpoint passed.

**Raw Maven invocations added (23 cumulative; prior 19 retained):** (20) expected RED for the new regression cases: `TransportObservationTest` reported 9 tests, 4 expected failures (missing zero-demand onError, missing overflow/deadline detachment, and the deadline/cancel race never entered the detach action), zero errors/skips; (21) focused selector passed 9/9 after the correction; (22) focused selector passed 9/9 after strengthening the zero-demand test to request later and confirm no stale frame or duplicate error; (23) exact T5 selector passed 52/52. The first test-edit script used an overly strict text anchor and exited before writing; no files changed in that attempt, then the insertion was reapplied by symbol anchors. No build timed out. Expected RED is not a no-progress attempt; no-progress streak is 0 for each P2 defect.

**Remaining gate:** stop here for the requested focused Sol re-review of the observation lifecycle correction. T5's exact named selector is green, but T5 acceptance remains pending that review. Do not start T6 or Phase 3. No commit, push or PR was created.

### T5 focused correction re-review — 2026-11-11

Independent Sol re-review closed both prior P2 observation findings. `TransportObservation.finish` clears buffered frames on FAILED/DEADLINE, so zero-demand failure delivers one `onError` without stale frames; deadline and overflow detach the underlying read once while retaining their terminal outcome through later cancel. The corrected tests cover the original no-demand repro, later demand, overflow, deadline, and a deterministic cancel race. [Review detail](phase-2/reviews/t5-review.md). Current saved five-class Surefire XML confirms **52 reported/executed, zero failures/errors/skips**; iteration 6 records exact T5 selector exit 0, reactor 16/16. This re-review did not rerun Maven or change implementation/tests/fixtures. **T5 accepted within the focused scope; pause after T5 as requested.** No new implementation iteration, T6/Phase 3, commit, push or PR. Preserve the 17:28Z–19:28Z coordinated run and its six coherent implementation iterations. Requested reviewer model `gpt-6-sol` at medium reasoning; runtime model/speed metadata unavailable.

### T6 shared client adapter checkpoint — iteration 7 — 2026-11-11T19:03:20Z

Implemented the bounded shared T6 client adapter within the original coordinated run (start 17:28Z, hard deadline 19:28Z). This is coherent iteration 7; the prior six iterations and 23 recorded Maven invocations remain unchanged. Requested implementation model: `gpt-6-luna`, medium reasoning. Runtime model/speed telemetry is unavailable, so execution identity is unverified. No agents or model switch were used.

**Changes:** Added public `Client.sendInput` overloads. Selected 1.1 uses T5 `TransportCall<TransportResult<EventKind>>` or `TransportObservation` according to existing streaming configuration, forwards the companion object unchanged, maps task and direct-message responses to the established callback types, skips unknown notes, and delivers one terminal error-or-null callback after stream events. Selected 1.0 accepts message-only companion input through the old send path. Added `TaskMessageUpdateEvent` dispatch as `TaskUpdateEvent`, copying the baseline task while preserving its other fields and carrying the original update. `ClientInputPolicy` rejects explicit-1.0 artifacts, guards, timeline limits and generation queries before transport access, requires `userArtifacts` for public artifacts, and rejects unsupported current-generation reads and push delivery-version requests before transport access. No stamps, retries, reducer state, cursor/live semantics or binding implementations were added.

**Files changed by this iteration:** `client/base/src/main/java/org/a2aproject/sdk/client/Client.java`, `ClientTaskManager.java`, new `ClientInputPolicy.java`, new `client/base/src/test/java/org/a2aproject/sdk/client/Phase2ClientInputTest.java`, and this checkpoint. No binding implementations, shared SSE reader, T7, Phase 3, fixtures, commits, pushes or PRs were changed.

**Verification:** The required exact selector `mvn -B -ntp -pl client/base -am -Dtest=Phase2ClientInputTest,ClientTransportPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test` passed after final code changes at 19:04:59Z: reactor 12/12, target tests `ClientTransportPolicyTest` 9 and `Phase2ClientInputTest` 6 = **15 reported/executed, zero failures/errors/skips**. Additional regression selector `mvn -B -ntp -pl client/base -am -Dtest=ClientTaskManagerTest,Phase2ClientInputTest -Dsurefire.failIfNoSpecifiedTests=false test` passed: reactor 12/12, **20 reported/executed, zero failures/errors/skips**. Scoped `git diff --check` passed. Maven emitted existing Java 17/Quarkus plugin compatibility and missing plugin version warnings; the selected Quarkus plugin fell back to 3.40.1.2. Spotless checks were skipped by project configuration.

**Raw invocation additions (30 cumulative):** (24) expected RED command `mvn -B -ntp -pl client/base -am -Dtest=Phase2ClientInputTest -Dsurefire.failIfNoSpecifiedTests=false test` failed test compilation because `Client.sendInput` did not exist; the same compile also exposed two fixture mistakes (artifact constructor order and omitted version helper argument), which were corrected before implementation. (25) focused selector passed 3/3 after initial implementation. (26) focused selector passed 5/5 after preflight/callback refinements. (27) exact shared selector passed 14/14. (28) exact selector passed 15/15 after callback exact-once/order refinements. (29) task manager regression plus new client tests passed 20/20. (30) final exact shared selector passed 15/15 after adding assertions that message updates preserve baseline generation, timeline, and artifact data. The initial expected RED and fixture compile errors were resolved in this iteration; no substantive no-progress streak was incurred. No command timed out.

**Review handoff:** The shared adapter objective is ready for focused Sol review. Binding work T6a/T6b/T6c and subsequent tasks remain unresolved and outside this assignment. Preserve the original deadline; do not commit, push, open a PR, or start excluded work.

### T6 shared adapter independent review — 2026-11-11

Independent Sol review found one P2 callback-order blocker; [review detail](phase-2/reviews/t6-shared-review.md). On a failed 1.1 stream, `Client.consumeObservation` can call the public terminal error consumer from `completion().whenComplete` while a prior event consumer remains active, because `TransportObservation.finish` resolves completion before serialized subscriber draining. A deterministic temporary Java probe printed `terminalBeforeEventEnded=1, terminalObserved=true`. The saved exact shared selector reports **15/15** and the manager regression reports **20/20**, both zero failures/errors/skips, but neither tests this race. **T6 shared acceptance and T6a/T6b/T6c remain paused pending correction and focused re-review.** No implementation iteration, source/test/fixture edit, Maven rerun, binding work, commit, push or PR was performed by this review. Preserve the original 17:28Z–19:28Z run, seven coherent implementation iterations and 30 raw Maven invocations. Requested reviewer model `gpt-6-sol`, medium reasoning; runtime model/speed telemetry unavailable.


### T6 shared callback ordering correction — iteration 8 — 2026-11-11T19:10:13Z

Corrected the P2 finding from the [T6 shared adapter review](phase-2/reviews/t6-shared-review.md) within the original coordinated run (start 17:28Z, hard deadline 19:28Z). This is coherent iteration 8; iterations 1–7 and the 30 prior raw Maven invocations remain intact. Requested implementation model: `gpt-6-luna`, medium reasoning. Runtime model/speed telemetry is unavailable, so execution identity is unverified. No agents or model switch were used.

**Changes:** `Client.consumeObservation` no longer invokes the public terminal consumer from a separate `TransportObservation.completion()` callback. Failure and cancellation now reach the public callback through the serialized subscriber's `onError`/`onComplete` signals; the existing atomic guard still enforces one error-or-null callback. Added a latch-controlled regression that blocks an event consumer, fails the observation, verifies the terminal callback has not started before release, then verifies the original failure arrives once after the event callback ends. Added repeated-cancel exact-once coverage and an assertion that repeated observation completion is rejected. No SPI/T5 production changes were made.

**Files changed in this correction:** `client/base/src/main/java/org/a2aproject/sdk/client/Client.java`, `client/base/src/test/java/org/a2aproject/sdk/client/Phase2ClientInputTest.java`, and this checkpoint. Earlier T6/T5 file changes listed in prior entries are preserved; no binding implementation, shared SPI, T6a/T6b/T6c, T7, Phase 3, commit, push or PR work was performed.

**Verification:** Before the production fix, the exact required selector `mvn -B -ntp -pl client/base -am -Dtest=Phase2ClientInputTest,ClientTransportPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test` exited 1 at 19:09:38Z as expected: `ClientTransportPolicyTest` 9/9 passed; `Phase2ClientInputTest` ran 8 with 2 failures, zero errors/skips. The deterministic race observed the terminal callback before event release; cancellation also exposed the same completion-stage side channel mapping cancellation as `A2AClientException` instead of the subscriber's `CancellationException`.

After the correction, that exact selector exited 0 at 19:09:55Z, reactor 12/12: `Phase2ClientInputTest` 8/8 plus `ClientTransportPolicyTest` 9/9 = **17 reported/executed, zero failures/errors/skips**. The additional regression selector `mvn -B -ntp -pl client/base,client/transport/spi -am -Dtest=ClientTaskManagerTest,TransportObservationTest -Dsurefire.failIfNoSpecifiedTests=false test` exited 0 at 19:10:06Z, reactor 12/12: `ClientTaskManagerTest` 14/14 and unchanged T5 `TransportObservationTest` 9/9 = **23 reported/executed, zero failures/errors/skips**. Scoped `git diff --check` passed. Maven reported the existing Java 17/Quarkus plugin compatibility and missing plugin-version warnings; it selected Quarkus plugin 3.40.1.2. No command timed out.

**Raw invocation additions (33 cumulative):** (31) expected RED exact shared selector, with the two failures described above; (32) exact shared selector passed 17/17; (33) manager plus unchanged T5 observation regression selector passed 23/23. The two expected failures are retained; neither is a substantive no-progress attempt. Current no-progress streak for this P2 is zero. Preserve all earlier failure/streak accounting.

**Handoff:** Request focused Sol re-review of the callback-order correction. T6 shared acceptance remains pending that review. Stop here before T6a/T6b/T6c, T7, Phase 3, commit, push or PR. Preserve the 17:28Z–19:28Z run and its eight consumed coherent iterations.

### T6 shared callback ordering focused re-review — 2026-11-11

Independent Sol re-review closed the P2 terminal-order finding; [review detail](phase-2/reviews/t6-shared-review.md#focused-correction-re-review--2026-11-11). Terminal delivery now follows serialized subscriber signals after active event callbacks; failure, cancellation and completed outcomes retain original error / `CancellationException` / null mapping with at-most-once delivery. The latch regression and original temporary probe confirm the previously reproduced overlap is gone. Current saved exact shared XML is **17/17** and manager plus unchanged T5 SPI XML is **23/23**, zero failures/errors/skips; iteration 8 records reactor 12/12 for both selectors. **T6 shared adapter accepted within the focused review scope; T6a/T6b/T6c remain separate pending work.** No new implementation iteration, Maven rerun, source/test/fixture edit, binding work, commit, push or PR by this review. Preserve the 17:28Z–19:28Z run, eight consumed coherent iterations and 33 raw Maven invocations. Requested reviewer model `gpt-6-sol`, medium reasoning; runtime model/speed telemetry unavailable.

### T6a JSON-RPC decoded unary reads — iteration 9 — 2026-11-11T19:18Z checkpoint

Implemented the selected-1.1 synchronous JSON-RPC `getTaskDecoded` and `listTasksDecoded` portion of T6a within the original coordinated run (17:28Z start, 19:28Z hard deadline). This is coherent iteration 9; iterations 1–8 and the prior 33 raw Maven invocations remain intact. Requested implementation model: `gpt-6-luna`, medium reasoning. Runtime model/speed telemetry is unavailable, so execution identity is not independently verified. No agents or model switch were used.

**Changes:** The decoded get/list methods issue the same JSON-RPC requests through the existing interceptor, tenant URL and error handling path, then call `JSONRPCUtils.decodeTaskResponse` / `decodeListTasksResponse` before any bare `Task` mapping. `DecodeNotes` are copied into immutable, ordered `TaskDecodeEvidence` entries. A malformed known value is surfaced as an `A2AClientException`; ignored variants retain their generation and unknown-skipped flag. Both methods explicitly require protocol 1.1; existing synchronous 1.0 operations and parsing remain unchanged. The JSON-RPC transport/provider continue to advertise only `ORDINARY`: `UNKNOWN_VARIANT_SKIP` stays disabled because task-bearing unary send and the streaming reader are not complete in this slice.

Controlled peer coverage uses Task A known generations 7/9 with skipped 8 and Task B 17/19 with skipped 18, asserts returned known timeline order, ordered task IDs and independent per-task evidence, `totalSize`/`pageSize`/`nextPageToken`, and verifies malformed known task content fails. A second base-module controlled-peer test builds the real selected 1.1 `Client`, accesses its `SelectedClientTransport` SPI seam, and confirms list evidence for A/B survives the JSON-RPC adapter plus actual selected wrapper. This base-module test avoids a dependency cycle; no POM dependency changed. It uses reflection only to inspect the private selected transport because the public API unwraps the envelope by design.

**Files changed in this iteration:** `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransport.java`; new `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java`; new `client/base/src/test/java/org/a2aproject/sdk/client/Phase2PublicClientTest.java`; and this checkpoint. No provider capabilities, SSE listener/streaming, async GET, task/direct-message SEND, frozen fixture, T7, Phase 3, commit, push or PR changes were made.

**Verification:** Final JSON-RPC gate `mvn -B -ntp -pl client/transport/jsonrpc -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` passed at 19:17:11Z, reactor 8/8. Saved Surefire XML: SPI `SSEEventListenerTest` 16/16, JSON-RPC `SSEEventListenerTest` 14/14, JSON-RPC `Phase2PublicClientTest` 2/2 = **32 executed, zero failures/errors/skips**. Final actual-wrapper gate `mvn -B -ntp -pl client/base -am -Dtest=Phase2PublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test` passed at 19:17:19Z, reactor 12/12; JSON-RPC peer class 2/2 and selected-wrapper peer class 1/1 = **3 executed, zero failures/errors/skips**. `git diff --check` on the three source/test files passed. Maven selected Quarkus plugin 3.40.1.2 due current Java 17 and emitted existing missing plugin-version warnings. Spotless checks are skipped by project configuration.

**Raw invocation additions (39 cumulative):** (34) initial focused JSON-RPC selector failed at test compilation (8 errors): the first test draft referenced unavailable `client/base` types from the transport module and had two fixture API mistakes; corrected by keeping the binding test at the transport SPI seam and moving selected-wrapper evidence coverage to the existing base module, without changing dependencies. (35) expected RED rerun then compiled and ran 32 cases: SPI SSE 16 and JSON-RPC SSE 14 passed; new JSON-RPC tests had one expected unsupported-operation error and one expected assertion failure because `getTaskDecoded` was not implemented. (36) exact JSON-RPC selector passed 32/32 after implementation. (37) base selector passed its initial wrapper peer case (3 matching tests total across JSON-RPC and base). (38) exact JSON-RPC selector passed 32/32 after final capability assertion. (39) base selector passed 3/3 after final pagination-token assertion. The compile/setup correction and expected RED did not represent substantive no-progress attempts; the no-progress streak is 0. No command timed out.

**Handoff at 19:18Z:** This bounded synchronous GET/LIST slice is complete and reviewable. T6a remains incomplete: implement async GET and task-bearing unary SEND envelope mapping when authorized, direct Message empty-evidence result, complete/versioned SSE decoding and T6a listener coverage; preserve `UNKNOWN_VARIANT_SKIP` disabled until the full implemented path is complete and tested. Next action is focused Sol review of this unary slice. Stop at the original coordinated deadline (checkpoint target ~19:22Z); do not enter T6b/T6c, T7 or Phase 3. Preserve all dirty/frozen files. No commit, push or PR.

### T6a JSON-RPC decoded unary GET/LIST focused independent review — 2026-11-11

Independent Sol review accepted only the bounded direct decoded GET/LIST SPI slice; [review detail](phase-2/reviews/t6a-unary-review.md). The 1.1 methods reach the T4 named JSON-RPC decoders before strict Task mapping and convert ordered per-task notes to T5 `TransportResult` evidence. Controlled peers prove A/B skipped generations 8/18, known-entry order, pagination and malformed-known failure; selected-wrapper forwarding preserves the envelope. Existing synchronous 1.0 methods are unchanged. `UNKNOWN_VARIANT_SKIP` remains disabled on the transport/provider, so public Client GET/LIST still use the legacy parser for unknown entries. Async GET, unary SEND, direct Message and versioned SSE remain pending; **T6a is not accepted as a complete binding.** Saved XML was independently parsed: JSON-RPC selector **32/32** and wrapper selector **3/3**, zero failures/errors/skips; no Maven rerun. This review adds no implementation iteration or raw Maven invocation; preserve nine consumed coherent iterations, 39 invocations and the original 17:28Z–19:28Z deadline. Requested reviewer model `gpt-6-sol`, medium reasoning; runtime model/speed metadata unavailable. No source/test/fixture implementation edit, T6b/T6c, T7, Phase 3, commit, push or PR.

### T6a JSON-RPC async decoded GET — iteration 10 checkpoint — 2026-11-11T19:23:30Z

Implemented the selected-1.1 async JSON-RPC `getTaskAsync(TaskQueryParams, SpiCallOptions, ClientCallContext)` slice within the original coordinated run (17:28Z start, 19:28Z hard deadline). This is coherent iteration 10/10; iterations 1–9 and the preceding 39 raw Maven invocations remain retained. Requested implementation model: `gpt-6-luna`, medium reasoning. Runtime model/speed telemetry is unavailable, so execution identity is unverified. No agents or model switch were used.

**Changes:** `JSONRPCTransport.getTaskAsync` validates the request/options and explicit protocol 1.1 before I/O, applies the existing interceptor/context and tenant URL path, then uses the HTTP client's asynchronous POST callback. Plain JSON response bodies go through the same named `JSONRPCUtils.decodeTaskResponse` path as synchronous `getTaskDecoded`; `DecodedTask.notes()` becomes one `TaskDecodeEvidence` inside `TransportResult<Task>`. HTTP/decode/setup failures fail the call; cancellation cancels the returned HTTP future and completes the `TransportCall` through its existing exactly-once cancellation path. Existing 1.0 methods and default SPI unsupported behavior are unchanged. Provider capabilities remain `ORDINARY`; `UNKNOWN_VARIANT_SKIP` is still disabled.

Added a controlled-peer regression to `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java`: async GET returns Task `task-async` with known generations 27/29 and unknown generation 28 in the task-specific evidence; malformed known timeline content fails with `A2AClientException`; a delayed peer response can be cancelled and produces `CancellationException` from the returned stage.

**Files changed by this iteration:** `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransport.java`, `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java`, and this checkpoint. Existing prior iteration work remains in the shared worktree; no other files were edited by this iteration. No provider capability change, public GET/LIST selection, unary SEND, direct Message, SSE adapter, T6b/T6c, T7, Phase 3, commit, push or PR was performed.

**Verification:** Exact selector `mvn -B -ntp -pl client/transport/jsonrpc -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` passed at 19:23:15Z, reactor 8/8. Saved Surefire reports: SPI `SSEEventListenerTest` 16/16, JSON-RPC `SSEEventListenerTest` 14/14, and JSON-RPC `Phase2PublicClientTest` 3/3 = **33 executed, zero failures/errors/skips**. `git diff --check` for the two implementation/test files exited 0 (Git emitted the known fsmonitor IPC warning). Maven emitted the existing Java 17/Quarkus plugin compatibility and missing plugin-version warnings and selected Quarkus 3.40.1.2; Spotless checks were skipped by project configuration.

**Raw invocation additions (45 cumulative):** (40) test compilation failed because the first test draft lacked the `Delay` import; import corrected; (41) expected RED run failed with `UnsupportedOperationException: Transport does not support getTaskAsync`; (42) first implementation compile exposed the checked `JsonMappingException`; (43) correction caught `IOException` instead of the SDK JSON exception and compilation reported both the unhandled mapping exception and invalid catch; (44) corrected exception type, compile then reported NullAway nullable Boolean unboxing; (45) replaced the flag with `AtomicBoolean`, exact selector passed. These were one expected RED and localized test/compile corrections; current substantive no-progress streak is 0. All failures remain recorded; no invocation timed out.

**Handoff:** Async GET is implemented and the focused selector passes, but this does not complete T6a or enable the transport feature. Remaining T6a work includes task-bearing unary SEND with evidence and direct Message handling, a complete versioned SSE reader/cancellation path, and any wrapper/selection coverage needed before `UNKNOWN_VARIANT_SKIP` can be advertised or public GET/LIST can select decoded methods. Next action: independent focused Sol review of the bounded async GET change, then continue only with separately authorized T6a work. Stop this iteration at 19:25Z; preserve the original 19:28Z deadline and do not reset or continue past it. Requested reviewer role/model: `gpt-6-sol`, medium reasoning; runtime model/speed telemetry unavailable.

### T6a JSON-RPC async decoded GET focused independent review — iteration 1/5 of new run — 2026-11-12

Independent Sol review found two P2 defects; [review detail](phase-2/reviews/t6a-async-review.md). **The bounded async GET slice is pending correction and is not accepted.** `getTaskAsync` uses the named T4 decoder and retains skipped generation 28 in task-specific evidence, but cancellation of the JDK HTTP helper's returned `.handle(...)` stage does not cancel the underlying `sendAsync` request. A compiled Java future-shape probe confirmed the source remains pending after dependent-stage cancellation. The async helper also forwards non-401/403 error bodies without exposing status, allowing a non-2xx response with a valid JSON-RPC Task result to complete successfully, unlike synchronous GET. The existing controlled-peer cancellation assertion covers only the result stage, and the peer returns 200 for success/failure-of-decoding cases.

Saved Surefire XML was parsed directly: SPI SSE 16/16, JSON-RPC SSE 14/14, JSON-RPC peer 3/3 = **33/33**, zero failures/errors/skips; iteration 10 records exact-selector reactor 8/8. No Maven rerun or raw Maven invocation was added. The only new executable check was the network-free Java future-shape probe (after a sandbox-blocked JShell attempt). Protocol 1.1 pre-I/O guard, tenant/context/interceptor request construction, malformed-known failure, and absence of `UNKNOWN_VARIANT_SKIP` were checked in source. No implementation source/test/fixture edit, T6b/T6c, T7, Phase 3, commit, push or PR was performed.

This review consumes **iteration 1 of the new five-coherent-iteration or 30-minute allowance**, started 2026-11-12T13:44:17Z with hard deadline 14:14:17Z; four coherent iterations remain, subject to the deadline. Preserve the prior exhausted 2026-11-11 run at **10/10 coherent iterations, 45 raw Maven invocations**, including its recorded failures and zero no-progress streak. Requested reviewer role/model: `gpt-6-sol`, medium reasoning; runtime model/speed metadata unavailable. Next scoped action is a bounded cancellation/status correction and focused regression/re-review, then remaining T6a tasks under the new allowance. Do not advertise `UNKNOWN_VARIANT_SKIP` or claim T6a complete from this review.

### T6a JSON-RPC async GET correction — iteration 2/5 — 2026-11-12T13:52Z checkpoint

Corrected both P2 findings from the [T6a async GET review](phase-2/reviews/t6a-async-review.md) during the newly authorized 2026-11-12T13:44:17Z–14:14:17Z run. This is iteration 2/5; the previous 2026-11-11 allowance remains exhausted at 10/10 coherent iterations and 45 raw Maven invocations. Requested implementation model: `gpt-6-luna`, medium reasoning. Runtime model/speed telemetry is unavailable, so execution identity is unverified. No agents or model switch were used.

**Changes in this correction:** Added an additive `A2AHttpClient.AsyncPostCall` / `PostBuilder.postAsyncResponse()` contract that returns `A2AHttpResponse` and has explicit underlying-request cancellation. The default method fails clearly for older/custom implementations that cannot guarantee status-aware cancellation; existing provider creation and SSE method signatures and SSE behavior are unchanged. The JDK implementation retains and cancels the actual `HttpClient.sendAsync` source future, while the Vert.x implementation resets its active HTTP request and Android disconnects its active connection. `JSONRPCTransport.getTaskAsync` now checks the returned HTTP status before decoding and maps non-2xx responses through `A2AClientHTTPError` like synchronous GET. Unknown-variant feature advertisement remains disabled.

**Files changed by this correction:** `http-client/src/main/java/org/a2aproject/sdk/client/http/A2AHttpClient.java`, `http-client/src/main/java/org/a2aproject/sdk/client/http/JdkA2AHttpClient.java`, `http-client/src/test/java/org/a2aproject/sdk/client/http/JdkA2AHttpClientTest.java`, `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransport.java`, `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java`, `extras/http-client-vertx/src/main/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClient.java`, and `extras/http-client-android/src/main/java/org/a2aproject/sdk/client/http/android/AndroidA2AHttpClient.java`, plus this checkpoint. Other existing dirty worktree files from prior authorized Phase 2 work are preserved; no unrelated source/test/fixture, T6b/T6c, T7 or Phase 3 work was done. No commit, push or PR was created.

**Verification:** Expected RED for the valid JSON-RPC result carried by HTTP 500 reproduced the finding: the async call completed successfully, so the test failed because no `CompletionException` was thrown. The focused JDK HTTP selector `mvn -B -ntp -pl http-client -Dtest=JdkA2AHttpClientTest#postAsyncResponsePreservesNonSuccessStatusAndBody+cancellingAsyncResponseClosesHeldOpenPeerConnection -Dsurefire.failIfNoSpecifiedTests=false test` passed 2/2. Its controlled raw TCP peer read the complete POST, held the response open, then observed EOF after cancellation. The exact JSON-RPC selector `mvn -B -ntp -pl client/transport/jsonrpc -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` passed: SPI SSE 16/16, JSON-RPC SSE 14/14, JSON-RPC peer 3/3 = 33/33, zero failures/errors/skips. The 3 peer cases include unknown generation 28 evidence, malformed known timeline rejection, cancellation, and the new HTTP 500 valid-result rejection. `mvn -B -ntp -pl extras/http-client-vertx,extras/http-client-android -am -DskipTests compile` passed (8/8 reactor modules), confirming both optional implementations compile. Existing Maven Java17/Quarkus compatibility and plugin-version warnings remain; Spotless checks are skipped by project configuration.

**Raw Maven invocation additions (52 cumulative; prior 45 retained):** (46) expected HTTP-500 RED failed because the async GET accepted the valid result body; (47) initial HTTP regression compile failed because the new JDK override omitted checked exceptions from the additive interface signature; (48) first HTTP behavior run found a cancellation-order race where the dependent result callback completed before explicit result cancellation; reordered result cancellation before cancelling the source; (49) HTTP status and held-open connection tests passed 2/2; (50) exact JSON-RPC selector passed 33/33; (51) optional-client compile found the Android connection factory's checked `IOException`; (52) both optional HTTP modules compiled. Expected RED and localized compile/cancellation corrections are recorded; the substantive no-progress streak remains **0**. No Maven command timed out.

**Next action:** request focused Sol re-review of this correction and its HTTP abstraction compatibility, then preserve the iteration/deadline ledger and only continue separately authorized T6a work. Do not advertise `UNKNOWN_VARIANT_SKIP`, claim T6a complete, implement SEND/streaming, expand to other bindings, or enter T7/Phase 3.

### T6a JSON-RPC async GET correction focused re-review — iteration 3/5 — 2026-11-12

Independent Sol re-review closed the two original P2 findings for the default JDK path and HTTP status handling, but found one remaining P2 race in the new Vert.x `postAsyncResponse`; [review detail](phase-2/reviews/t6a-async-review.md#focused-correction-re-review--2026-11-12). JDK cancellation now reaches the actual `sendAsync` source and the held-open raw TCP peer observes EOF; async GET rejects HTTP 500 containing a valid JSON-RPC Task result before decoding. The additive HTTP method defaults to a clear unsupported error for older/custom clients; old SSE methods and provider creation remain unchanged. `UNKNOWN_VARIANT_SKIP` remains disabled.

Vert.x checks its cancellation flag before storing the active request, while cancellation reads the handle separately. Cancellation between those operations can see no handle, then the callback can send the request without reset. **The correction remains pending this race; do not accept the async GET slice yet.** Vert.x/Android optional modules have compile evidence only, no runtime cancellation test. Saved XML was parsed: focused JDK **2/2**, exact JSON-RPC selector **33/33** (SPI SSE 16, JSON-RPC SSE 14, peer 3), all zero failures/errors/skips. Iteration 2 records reactor 8/8 for the exact selector and optional compile. No Maven rerun, new raw invocation, implementation edit, T6b/T6c, T7, Phase 3, commit, push or PR by this review; scoped source/test `git diff --check` passed.

This review consumes **iteration 3/5** of the new run begun 2026-11-12T13:44:17Z with hard deadline 14:14:17Z; two coherent iterations remain subject to time. Preserve the prior exhausted run at **10/10 and 45 raw Maven invocations** and the new correction's cumulative **52** raw Maven invocations, including expected RED/compile failures; substantive no-progress streak remains 0. Requested reviewer role/model: `gpt-6-sol`, medium reasoning; runtime model/speed metadata unavailable. Next scoped action is a race-safe Vert.x cancellation handoff and focused regression/re-review, within the remaining allowance. T6a remains incomplete beyond this async GET slice.

### T6a Vert.x async cancellation handoff correction — iteration 4/5 — 2026-11-12T14:00:06Z checkpoint

Corrected the remaining Vert.x P2 found in the focused Sol re-review. The prior post-checkpoint edit assigned `activeRequest` before checking cancellation, closing the originally described missing-handle window but leaving cancellation able to reset the request between the flag check and `request.send`; repeated cancellation could also reset the same handle again. `AsyncRequestHandoff` now synchronizes request registration plus send initiation with cancellation. Cancellation transfers and clears the active reset callback once; a request arriving after cancellation is reset without being sent. `AsyncPostCall.cancel()` invokes this handoff only when it successfully cancels the result future. The JDK/Android corrections, existing SSE request paths, provider creation and frozen fixtures were left untouched.

**Files changed in this correction:** `extras/http-client-vertx/src/main/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClient.java`, new `extras/http-client-vertx/src/test/java/org/a2aproject/sdk/client/http/vertx/VertxAsyncRequestHandoffTest.java`, `extras/http-client-vertx/src/test/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClientIntegrationTest.java`, and this checkpoint. No other source/test/fixture file was changed by this correction. No shared HTTP/JSON-RPC code changed, so the previously saved exact JSON-RPC selector remains applicable and was not rerun. No SEND/SSE/T6b/T6c/T7/Phase 3 work, commit, push or PR was performed.

**Verification:** The deterministic handoff test holds request-send setup inside the handoff monitor, waits until the concurrent cancellation thread is actually blocked on that monitor, then releases send setup and asserts exactly one active-request reset. Its second test cancels before request readiness and asserts reset-without-send exactly once. A Vert.x integration test sends a complete POST to a local peer that holds the response open, cancels twice, and verifies the actual peer connection closes as well as the response future becoming cancelled. Final Vert.x focused reactor command `mvn -B -ntp -pl extras/http-client-vertx -am -Dtest=VertxA2AHttpClientFactoryTest,VertxA2AHttpClientRedirectTest,VertxA2AHttpClientSSETest,VertxA2AHttpClientTest,VertxA2AHttpClientIntegrationTest,VertxAsyncRequestHandoffTest -Dsurefire.failIfNoSpecifiedTests=false test` passed at 14:00:06Z: **67 tests, 0 failures, 0 errors, 5 existing skips**, including all 18 SSE cases (13 executed, 5 skipped), redirects and connection cancellation. Reactor compiled the changed module. Scoped `git diff --check` exited 0; Git printed the pre-existing linked-worktree fsmonitor IPC warning. Maven selected Quarkus 3.40.1.2 for Java 17 and retained existing missing-plugin-version warnings.

**Raw Maven invocation additions (58 cumulative, prior 52 retained):** (53) direct Vert.x module test failed during main compilation because it resolved an older installed `http-client` jar without the worktree's new `AsyncPostCall` API; corrected by building reactor dependencies with `-am`. (54) expected TDD RED using `-am` reached test compilation and failed only because the new `AsyncRequestHandoff` production seam had not yet been implemented. (55) deterministic handoff selector passed 2/2. (56) handoff plus held-open peer selector passed 3/3. (57) full focused Vert.x selector passed 67 tests, 0 failures/errors, 5 existing skips. (58) final rerun after import/format cleanup passed with the same 67/0/0/5 result. These were a dependency setup issue, expected RED and successful green checks; substantive no-progress streak remains **0**. No command timed out.

This is **iteration 4/5** of the 2026-11-12T13:44:17Z–14:14:17Z run. Preserve the exhausted prior run at 10/10 coherent iterations and 45 raw Maven invocations, and the new run's 52-invocation correction baseline plus the six invocations above. Requested implementation model: `gpt-6-luna`, medium reasoning. Runtime model/speed telemetry is unavailable; execution identity is unverified. No agents or model switch were used. **Next action:** focused independent Sol re-review of the Vert.x handoff and real connection-close regression, before continuing any separately authorized T6a work. T6a remains incomplete beyond this async GET slice; keep `UNKNOWN_VARIANT_SKIP` disabled.

### T6a JSON-RPC async GET final Vert.x handoff re-review — iteration 5/5 — 2026-11-12

Independent Sol re-review accepted the **bounded direct selected-1.1 async decoded GET slice** after the Vert.x cancellation handoff correction; [review detail](phase-2/reviews/t6a-async-review.md#final-vertx-handoff-re-review--2026-11-12). The handoff synchronizes request registration and send initiation with cancellation: a request arriving after cancellation is reset without send, while cancellation during/after handoff resets the active request once. Deterministic tests cover both interleavings; a held-open Vert.x peer observes connection close after repeated cancellation. The earlier JDK source-cancellation and HTTP-500 status findings remain closed. Existing SSE behavior is untouched by this correction, JDK/Android implementations were not edited in iteration 4, and `UNKNOWN_VARIANT_SKIP` remains disabled.

Saved final Vert.x Surefire XML was independently parsed: factory 2, integration 13, redirect 22, SSE 18, ordinary 10, handoff 2 = **67 reported, 0 failures, 0 errors, 5 SSE skips** (62 executed). Iteration 4 records the final focused reactor pass after cleanup; scoped Vert.x diff check passed. No Maven rerun, new raw invocation, targeted probe, implementation edit, SEND/SSE/T6b/T6c/T7/Phase 3 work, commit, push or PR by this review. Android cancellation still has compile-only, not held-open runtime, evidence; this does not extend the bounded acceptance claim.

This review consumes **iteration 5/5** of the run begun 2026-11-12T13:44:17Z (deadline 14:14:17Z). The new allowance is exhausted. Preserve the prior exhausted 2026-11-11 run at **10/10 coherent iterations and 45 raw Maven invocations** and the current cumulative **58 raw Maven invocations**, including all recorded RED/setup failures; substantive no-progress streak remains 0. Requested reviewer role/model: `gpt-6-sol`, medium reasoning; runtime model/speed metadata unavailable. Stop this run here. T6a remains incomplete beyond async GET: task-bearing SEND, direct Message evidence, versioned SSE and full public binding selection require separate authorized work.

### T6a JSON-RPC unary SEND — 2026-11-12T14:30Z checkpoint — iteration 1/5 of new allowance

Implemented the bounded JSON-RPC unary `sendInput` SPI path following the accepted direct 1.1 async GET review. This is iteration **1/5** of the separately authorized run begun **2026-11-12T14:18:20Z**, with a hard two-hour cap at 16:18:20Z. Preserve the earlier exhausted run at **10/10 iterations, 45 raw Maven invocations**, the immediately preceding run at **5/5 iterations, 58 invocations**, and all recorded failures. Requested implementation model: `gpt-6-luna`, medium reasoning. Runtime model/speed telemetry is unavailable; execution identity is unverified. No agents or model switch were used.

**Changes:** `JSONRPCTransport.sendInput(SendInputParams, SpiCallOptions, ClientCallContext)` requires selected protocol 1.1 before I/O, runs the existing `SendMessage` conversion/interceptor/context/version/tenant request path using the full companion request mapper, and calls status-aware `postAsyncResponse()`. Successful HTTP responses go through `JSONRPCUtils.decodeSendResponse` before returning `TransportResult<EventKind>`: Task results include their own `TaskDecodeEvidence`, while direct Messages have an empty evidence list. HTTP errors retain `A2AClientHTTPError`; malformed known responses fail; -32010 remains a typed `TaskGenerationMismatchError` and does not retry. Cancellation forwards to the underlying async post call. The explicit 1.0 legacy `sendMessage` path is unchanged; direct 1.0 `sendInput` rejects before I/O. Provider/transport capability remains `ORDINARY`; no feature or public-client selection was enabled.

Added controlled-peer cases in `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java` for Task evidence around unknown generation 38, direct Message empty evidence, complete encoded 1.1 message/artifact/configuration/guard/metadata/tenant data, caller/interceptor headers and version, typed mismatch/no retry, HTTP 500, malformed known timeline content, cancellation, and pre-I/O 1.0 rejection. The cancellation regression asserts result cancellation; it does not itself assert TCP peer closure. Accepted async GET review provides the separate status-aware HTTP call cancellation evidence.

**Files changed in this iteration:** `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransport.java`, `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java`, and this progress checkpoint. Pre-existing worktree changes and frozen fixtures were preserved. No provider, shared client, REST/gRPC binding, SSE reader, T7, Phase 3, commit, push or PR was changed.

**Verification:** Exact selector `mvn -B -ntp -pl client/transport/jsonrpc -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` passed at 14:30:10Z, reactor **8/8**. Surefire: SPI `SSEEventListenerTest` 16/16, JSON-RPC `SSEEventListenerTest` 14/14, JSON-RPC `Phase2PublicClientTest` 8/8 = **38 executed, zero failures/errors/skips**. Scoped `git diff --check` passed for the two implementation/test files. Maven selected Quarkus 3.40.1.2 for Java 17; existing plugin-version warnings remain and Spotless was skipped by project configuration.

**Raw Maven invocation additions (66 cumulative; prior 58 retained):** (59) initial test draft compile failed on an abstract interceptor fixture and missing artifact helper; fixed test fixture. (60) expected RED failed with `Transport does not support sendInput`. (61) implementation compiled, but first peer fixture placed a raw Task at the response result instead of the required `{task: ...}` send result; corrected fixture. (62) selector passed 37/37. (63) expanded malformed-known/cancellation/1.0 checks passed 38/38. (64) deeper wire assertion failed because protobuf JSON omits default false `lastChunk`; assertion corrected to the wire omission. (65) selector passed 38/38. (66) final selector after import cleanup passed 38/38. No command timed out; per-defect substantive no-progress streak remains **0**. The RED and assertion/fixture failures were expected or test-only, with no known production defect remaining.

**Handoff:** Remaining T6a work is the versioned SSE reader and any additional selected public-client/capability wiring; `UNKNOWN_VARIANT_SKIP` remains disabled and the public GET/LIST/SEND feature path is not claimed complete. Next action is independent `gpt-6-sol`, medium-reasoning review of this bounded unary SEND slice, with runtime/speed telemetry recorded as unavailable. Continue only within the new five-iteration/two-hour allowance and checkpoint by 15:00Z. Do not begin T6b/T6c, T7 or Phase 3; no commit, push or PR.

### T6a JSON-RPC unary SEND independent review — 2026-11-12T14:33Z — iteration 2/5

Independent Sol review **accepted the bounded direct selected-1.1 unary SEND SPI slice**, with no blocking SEND-specific finding; [review detail](phase-2/reviews/t6a-send-review.md). Source inspection confirmed full companion request mapping, tenant/version/context/interceptor encoding, 1.0 pre-I/O rejection, status-aware HTTP failure, typed `-32010` without retry, Task-specific and direct-Message evidence, shared actual-read cancellation, atomic terminal completion and `ORDINARY`-only capability advertisement. The SEND peer cancellation test proves the cancelled result but not a SEND-specific held-open connection close or completion/cancel race; the previously accepted async GET/JDK/Vert.x shared-handle evidence supports the underlying read-detach contract. Public selected-client unknown-entry success and versioned SSE remain pending.

Saved XML was independently parsed: SPI SSE 16, JSON-RPC SSE 14, JSON-RPC peer 8 = **38/38**, zero failures/errors/skips. Iteration-1 exact-selector reactor **8/8** and its failure ledger remain as recorded. Scoped SEND source/test `git diff --check` exited 0 with an fsmonitor IPC warning. No Maven rerun, executable probe, implementation edit, other binding, T7, Phase 3, commit, push or PR was made. This review consumes **iteration 2/5** of the run begun 2026-11-12T14:18:20Z (deadline 16:18:20Z), with **66 cumulative raw Maven invocations** unchanged and substantive no-progress streak **0**. Preserve the prior exhausted 10/10/45 and 5/5/58 ledgers. Requested reviewer role/model: independent `gpt-6-sol`, medium reasoning; runtime model/speed metadata unavailable. Next eligible work is bounded T6a SSE/public feature wiring within the remaining run allowance; T6a is not complete.

### T6a JSON-RPC selected-1.1 SSE input and cancellation — 2026-11-12T15:04Z checkpoint — iteration 3/5

Implemented the bounded selected-1.1 JSON-RPC SSE `streamInput` binding slice. This checkpoint is iteration **3/5** in the run authorized from **2026-11-12T14:18:20Z**, with a two-hour deadline at 16:18:20Z. Iteration 1 was the unary SEND implementation, iteration 2 its independent Sol review, and iteration 3 this SSE implementation. The parent requested stopping mutations/tests at this checkpoint pending independent Sol review.

**Changes:** `VersionedClientTransport.streamInput(SendInputParams, SpiCallOptions, ClientCallContext)` is implemented for selected protocol 1.1 in `JSONRPCTransport`. It applies the 1.1 send-streaming request mapper, tenant URL, selected version header, context and interceptors; rejects unsupported selected protocol before I/O; maps shared JSON-RPC `StreamDecodeResult` values to known-event frames, unknown-variant frames with generation notes, separate local `StreamError` frames, or malformed-stream failure. The versioned reader calls the shared response-envelope decoder, which validates JSON-RPC version/id and converts error envelopes into failure. Legacy `SSEEventListener` constructors, callbacks, and parsing behavior remain on their original path.

The stream observation retains a cancellable HTTP SSE handle and registers active input observations for `JSONRPCTransport.close()`. Closing a transport cancels only its active reads, not an injected HTTP client. `A2AHttpClient.PostBuilder.postAsyncSSECall` is additive and has an explicit unsupported default for custom HTTP clients; JDK, Vert.x, and Android backends now retain their source read/request and implement cancellation. Listener/observation paths retain bounded buffering, demand handling, and at-most-once terminal delivery. Provider and transport capabilities remain **`ORDINARY` only**; `UNKNOWN_VARIANT_SKIP` is not advertised.

**Peer coverage:** JSON-RPC transport tests exercise a selected-1.1 Task → message update → future variant → status update → artifact update → terminal sequence and assert tenant, version, context, interceptor and method. Future variants become raw unknown markers and do not fabricate public events. Malformed known payloads fail. A held-open, all-unknown peer verifies actual JDK HTTP read detachment, independent ownership across two JSON-RPC transports/HTTP clients, and that closing one leaves the other's stream open. The base-module public peer tests use the existing dependency direction (no base↔JSON-RPC cycle): selected `Client.sendInput` delivers known events while skipping a future variant, and `Client.close()` detaches a held-open all-unknown stream, emits exactly one cancellation terminal, and emits no event for the unknown frame. Public two-client isolation is covered at the transport ownership seam rather than with two public `Client` instances.

**Files changed for this iteration:**
- `client/transport/spi/src/main/java/org/a2aproject/sdk/client/transport/spi/sse/AbstractSSEEventListener.java`
- `client/transport/spi/src/test/java/org/a2aproject/sdk/client/transport/spi/sse/SSEEventListenerTest.java`
- `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransport.java`
- `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/sse/SSEEventListener.java`
- `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/sse/SSEEventListenerTest.java`
- `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java`
- `client/base/src/test/java/org/a2aproject/sdk/client/Phase2PublicClientTest.java`
- `http-client/src/main/java/org/a2aproject/sdk/client/http/A2AHttpClient.java`
- `http-client/src/main/java/org/a2aproject/sdk/client/http/JdkA2AHttpClient.java`
- `http-client/src/test/java/org/a2aproject/sdk/client/http/JdkA2AHttpClientTest.java`
- `extras/http-client-vertx/src/main/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClient.java`
- `extras/http-client-vertx/src/test/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClientIntegrationTest.java`
- `extras/http-client-android/src/main/java/org/a2aproject/sdk/client/http/android/AndroidA2AHttpClient.java`
- `extras/http-client-android/src/test/java/org/a2aproject/sdk/client/http/android/AndroidA2AHttpClientSSETest.java`

No cyclic dependency or POM change was made. No frozen fixtures, T6b/T6c, T7, Phase 3, commit, push, or PR work was done.

**Final verification:** The requested exact selector, `mvn -B -ntp -pl client/transport/jsonrpc -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test`, passed at 15:04:27Z, reactor 8/8. Surefire XML: SPI SSE 16/16, JSON-RPC SSE 17/17, JSON-RPC `Phase2PublicClientTest` 10/10 = **43 reported/executed, zero skipped, zero failures, zero errors**. The final JSON-RPC SSE class includes response envelopes with missing id, null id, and a JSON-RPC error. Base controlled-peer selector `mvn -B -ntp -pl client/base -am -Dtest=Phase2PublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test` passed **3/3**, zero skipped/failures/errors. Android plus Vert.x selector `mvn -B -ntp -pl extras/http-client-android,extras/http-client-vertx -am -Dtest=AndroidA2AHttpClientSSETest,VertxA2AHttpClientIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test` passed Android SSE **19/19** and Vert.x integration **14/14**, zero skips/failures/errors. The earlier JDK held-open cancellation selector passed **1/1**, and the focused two-transport held-open/ownership selector passed **1/1**. Scoped `git diff --check` on the touched files exited 0; git printed a nonfatal fsmonitor IPC warning. Maven selected Quarkus 3.40.1.2 under Java 17 and reported existing plugin-version warnings; Spotless checks were skipped by project configuration.

**Raw Maven invocation ledger:** This run began at 66 cumulative invocations. Invocations **67–81** are recorded below; count at checkpoint is **81 cumulative** (15 added). Preserve the earlier exhausted run at **10/10 iterations and 45 raw invocations**, the subsequent exhausted run at **5/5 and 58 raw invocations**, and their prior failure ledgers.

- **67:** Exact selector compile failed on a one-line Java text block plus the not-yet-added versioned SSE constructor; repaired the test string and then added the constructor as implementation work.
- **68:** Exact selector compile failed NullAway because nullable `protocolVersion` reached the decoder; use of the checked local selected version fixed it.
- **69:** Exact selector passed: SPI SSE 16, JSON-RPC SSE 16, peer class 8 = **40**, zero skips/failures/errors.
- **70:** JDK held-open SSE actual-read cancellation test passed **1/1**.
- **71:** Exact selector failed compilation after a draft imported public `Client` into the JSON-RPC module, which would make a base↔JSON-RPC test dependency cycle. Removed the draft; kept public tests in `client/base`.
- **72:** Exact selector passed: SPI SSE 16, JSON-RPC SSE 16, peer class 9 = **41** (correct arithmetic), zero skips/failures/errors. An earlier console summary incorrectly said 25; class counts are authoritative.
- **73:** Exact selector failed the initial held-open ownership assertion while using a shared client/incorrect closed-session expectation; changed the test to give each transport its own JDK HTTP client.
- **74:** Rerun failed because cancellation cleared the first observation's queued frame before the assertion. Added a subscriber latch and waited until the unknown frame was actually delivered before close.
- **75:** Focused `closingOneTransportDetachesOnlyItsAllUnknownHeldOpenStream` passed **1/1**.
- **76:** Exact selector passed: SPI 16, JSON-RPC SSE 16, peer class 10 = **42**, zero skips/failures/errors.
- **77:** Android/Vert.x selector passed Android SSE 19/19 and Vert.x integration 14/14, zero skips/failures/errors.
- **78:** Base-module public selected-1.1 stream test plus existing public peer test passed **2/2**.
- **79:** Base-module public selector after adding `Client.close()` held-open coverage passed **3/3**.
- **80:** Exact selector after public peer changes passed **42** (16 + 16 + 10), zero skips/failures/errors.
- **81:** Final exact selector after response-id/error-envelope tests passed **43** (16 + 17 + 10), zero skips/failures/errors.

The transient compile/setup and test synchronization failures above were corrected; no substantive no-progress streak remains (**0**). No command timed out or exceeded the stated 20-minute command limit.

**Remaining review/work:** Independent Sol review is next; it should inspect cancellation handoff races and actual-read cancellation across the additive API/backends, immutable selected-1.1 behavior, local rejection versus malformed/error envelopes, buffering/terminal semantics, legacy reader compatibility, and whether the public binding/capability selection policy is complete. The public `Client.sendInput` and `Client.close()` selected-1.1 path has passing coverage; two-client close isolation is verified at the transport level. Keep capabilities at `ORDINARY` and `UNKNOWN_VARIANT_SKIP` disabled unless review and any required public selection work explicitly approve changing them. Do not begin unrelated T6b/T6c/T7/Phase 3 work.

Requested implementation/reviewer model remains `gpt-6-luna`, medium reasoning; runtime model identity and speed telemetry are unavailable. No model switch or agent delegation occurred during this SSE iteration. Stop after this checkpoint until the independent Sol review handoff.

### T6a JSON-RPC SSE independent review — 2026-11-12 — iteration 4/5

Independent Sol review finds the selected-1.1 **raw SSE slice useful but T6a COMPLETE acceptance pending**; see [review detail](phase-2/reviews/t6a-sse-review.md). Three P2 binding blockers remain. Provider and actual transport still advertise only `ORDINARY`, so public `Client.getTask`/`listTasks` select legacy reads despite the direct decoded SPI methods; the base peer test calls the selected wrapper by reflection and does not prove public unknown-entry reads. The SSE HTTP API forwards non-401/403 non-SSE bodies without status, so a 500 response with a syntactically valid JSON-RPC result can be decoded as a successful stream. The new JSON-RPC response decoder validates id shape but does not compare it with the generated request UUID, so wrong-id events and error envelopes are accepted; current peer fixtures themselves use a fixed id different from the request UUID. Required binding/public evidence is also missing for local rejection, malformed-known exactly-one public error, finite demand/buffer behavior, terminal/error/cancel race, and two public clients sharing an injected HTTP client. Transport-level held-open cancellation and close isolation, public one-client close, selected-version forwarding, distinct raw decode outcomes, and legacy reader isolation are supported within their stated scope. No Phase 3/4 synchronization or recovery guarantee is claimed.

Saved Surefire XML was independently parsed: exact JSON-RPC selector **43/43** (SPI SSE 16, JSON-RPC SSE 17, JSON-RPC peer 10), base selected-wrapper/public **3/3**, Android SSE **19/19**, Vert.x integration **14/14**, JDK held-open **1/1**, all zero failures/errors/skips. Final exact XML timestamps follow touched SSE source timestamps. The iteration-3 checkpoint records exact-selector reactor 8/8 and raw invocation ledger 67–81; this review ran no Maven command or executable probe and added no raw invocation. It consumes **iteration 4/5** of the run begun 2026-11-12T14:18:20Z, with unchanged hard deadline 16:18:20Z and **81 cumulative raw Maven invocations**; substantive no-progress streak remains **0**. Preserve the earlier exhausted runs at 10/10/45 and 5/5/58, including their failures. Requested reviewer model/role: `gpt-6-sol`, medium reasoning; runtime model/speed metadata unavailable. No source/test/fixture implementation edit, T6b/T6c/T7, Phase 3, commit, push or PR by this review. Hold further implementation until this disposition is considered; any bounded correction/re-review must respect the one remaining iteration and original deadline.

### T6a JSON-RPC SSE HTTP status correction — 2026-11-12 checkpoint — iteration 5/5

This is the final focused correction iteration of the run authorized for 2026-11-12T14:18:20Z–16:18:20Z. It addresses only review finding #2: a non-2xx HTTP SSE response carrying a valid JSON-RPC result could be decoded as a successful stream. `JSONRPCTransport.streamInput` now uses the additive `PostBuilder.postAsyncSSECallStatusAware` contract. Its default rejects unsupported custom HTTP implementations before their body can be decoded. JDK, Vert.x and Android implementations reject non-2xx status at response headers and report `A2AClientHTTPError`; ordinary cancellable and legacy SSE methods retain their previous body-forwarding behavior. Successful HTTP responses, including JSON-RPC error envelopes, still reach the JSON-RPC decoder.

**Regression evidence:** a controlled HTTP 500 peer returns a valid Task result to the raw JSON-RPC observation; the observation fails with `A2AClientHTTPError` and publishes no frame. A separate HTTP 200 JSON-RPC generation-mismatch envelope remains a typed `TaskGenerationMismatchError`. JDK, Vert.x and Android backend tests use valid Task-result bodies and assert status failure precedes the message consumer and normal completion. `Phase2InputTerminalTest` verifies the public `Client.sendInput` terminal callback receives one HTTP error and no event from a failed `TransportObservation`. Because `client/base` already depends on JSON-RPC transport, the public callback test is in `client/base` and uses a deterministic failed observation; the actual controlled-peer assertion is at the raw JSON-RPC observation boundary. No full public-Client-plus-wire-peer assertion was run. Android backend tests ran on the JVM with `HttpServer`; no Android device/emulator runtime was exercised.

**Files changed for this focused correction:**
- `http-client/src/main/java/org/a2aproject/sdk/client/http/A2AHttpClient.java`
- `http-client/src/main/java/org/a2aproject/sdk/client/http/JdkA2AHttpClient.java`
- `http-client/src/test/java/org/a2aproject/sdk/client/http/JdkA2AHttpClientTest.java`
- `extras/http-client-vertx/src/main/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClient.java`
- `extras/http-client-vertx/src/test/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClientIntegrationTest.java`
- `extras/http-client-android/src/main/java/org/a2aproject/sdk/client/http/android/AndroidA2AHttpClient.java`
- `extras/http-client-android/src/test/java/org/a2aproject/sdk/client/http/android/AndroidA2AHttpClientSSETest.java`
- `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransport.java`
- `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java`
- `client/base/src/test/java/org/a2aproject/sdk/client/Phase2InputTerminalTest.java`

**Final focused checks:**
- `mvn -B -ntp -pl client/transport/jsonrpc -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` — exit 0, reactor 8/8. Surefire: SPI SSE 16, JSON-RPC SSE 17, JSON-RPC peer 12 = **45/45**, zero failures/errors/skips.
- `mvn -B -ntp -pl http-client,extras/http-client-vertx,extras/http-client-android -am -Dtest=JdkA2AHttpClientTest,VertxA2AHttpClientIntegrationTest,AndroidA2AHttpClientSSETest -Dsurefire.failIfNoSpecifiedTests=false test` — exit 0, reactor 8/8. JDK 17, Vert.x 15, Android 20 = **52/52**, zero failures/errors/skips.
- `mvn -B -ntp -pl client/base -am -Dtest=Phase2InputTerminalTest -Dsurefire.failIfNoSpecifiedTests=false test` — exit 0, reactor 12/12; public terminal callback **1/1**, zero skips/failures/errors.
- Total final selected Surefire XML evidence is **98 tests, zero failures/errors/skips**. The focused `git diff --check` exited 0. Maven used Java 17.0.18 and Quarkus plugin 3.40.1.2; existing plugin-version/JDK-21 compatibility warnings remain, and Spotless was skipped by project configuration. The last source edits after the final Maven selectors were import sorting/removal only.

**Raw Maven ledger and failure streak:** this run began at 81 cumulative raw Maven invocations and closes at **90 cumulative invocations**. Invocation 82 had a test compile error from placing a `Client` test in the JSON-RPC module; it was moved to `client/base`. Invocation 83 was the expected RED: the 500 Task response completed `COMPLETED` instead of `FAILED`. Invocation 86 caught an Android helper-visibility compile error, which was corrected. Invocations 88–90 are the final exact JSON-RPC, three-backend, and base selectors above. No command timed out. These were test-placement/compile corrections plus one expected RED; substantive no-progress streak remains **0**. Preserve prior exhausted runs at 10/10 with 45 raw Maven invocations and 5/5 with 58; no budget or historical failure was reset.

`UNKNOWN_VARIANT_SKIP` remains disabled; provider capability declarations and public GET/LIST selection were not changed. Unresolved T6a review findings are #1 (public GET/LIST capability selection), #3 (JSON-RPC response id must correlate with the request UUID, including the late Sol note), #4 (local-rejection/malformed-known callback, finite-demand/buffer and terminal-race evidence), and #5 (two public clients sharing an injected HTTP client). Finding #2 has the focused implementation and regressions above but awaits independent re-review. T6a remains incomplete. Do not start T6b/T6c/T7/Phase 3, change fixtures, commit, push or create a PR.

Requested model/role: `gpt-6-luna`, medium reasoning. Runtime model identity and speed telemetry are unavailable; no model switch or delegation occurred. Next action is an independent `gpt-6-sol`, medium review of this focused correction in a separately authorized run. Stop at this checkpoint; the five-iteration allowance is exhausted.

### T6a HTTP status correction independent re-review — 2026-11-12 — new run iteration 1/10

Independent Sol review **accepts prior finding #2 within the bounded status-correction scope**; see [re-review detail](phase-2/reviews/t6a-sse-review.md#focused-http-status-correction-re-review--2026-11-12--new-run-iteration-110). Selected-1.1 JSON-RPC SSE uses the additive status-aware HTTP method. JDK, Vert.x, and Android reject non-2xx at response headers before body delivery, preserve HTTP 200 typed JSON-RPC errors, and leave legacy SSE methods on their previous paths. The unsupported custom-client default is explicit. Existing cancellation and injected-client ownership paths remain intact. Controlled raw peer and three-backend 500-valid-result tests, 200 typed-error test, and deterministic public callback test support this disposition; the public callback test is **not** a full public Client plus HTTP wire-peer test, and Android testing is JVM-only.

Saved Surefire XML was inspected: JSON-RPC **45/45**, backends **52/52**, base terminal **1/1**, zero failures/errors/skips. The final selector XML precedes import-only sorting/removal in the two peer test files. This review ran no Maven command or probe; cumulative raw Maven invocations remain **90**, substantive no-progress streak **0**. New run begins 2026-11-12T15:25:52Z with a maximum **10 coherent iterations or 2 hours** (17:25:52Z); this is **iteration 1/10**. Preserve the prior exhausted run ledgers, including 10/10/45, 5/5/58, and 5/5/90, and all prior failures. Requested reviewer model/role: `gpt-6-sol`, medium; observed runtime model/speed metadata unavailable.

T6a remains **incomplete**. Earlier review findings #1 (public GET/LIST capability selection), #3 (response id correlation), #4 (stream outcome/race/demand evidence), and #5 (two public clients sharing injected HTTP client) remain open. No T6b/T6c/T7, Phase 3, fixture, implementation, commit, push, or PR work was done. Stop after this checkpoint.

### T6a selected-1.1 JSON-RPC unary response-ID correlation — new run iteration 2/10 — 2026-11-12T15:42Z checkpoint

Implemented only unary request/response ID correlation for the selected 1.1 JSON-RPC GET, LIST, async GET, and unary SEND paths. Each request now creates one UUID, passes it to request serialization, and supplies the same value to an additive response-decoder overload. The new JSONRPCUtils.decodeTaskResponse, decodeListTasksResponse, and decodeSendResponse overloads validate response ID presence, nullness, JSON string/number type, and equality before processing either result or error envelopes. Existing decoder signatures and the 1.0 legacy call paths remain unchanged. The SSE listener still uses the previous non-correlating decoder and is intentionally out of scope here.

The selected JSON-RPC peer tests now echo actual request IDs on successful unary GET/LIST, async GET, and unary SEND fixtures. They assert distinct GET/LIST UUIDs; cover matching results and a matching typed error; exercise wrong, missing, null, object, boolean, and array IDs across sync GET, sync LIST, async GET, and SEND; and prove a wrong-ID JSON-RPC error is rejected before typed error mapping. This matrix has 24 invalid-result cases plus the separate error-envelope correlation regression.

**Changed files for this iteration:** client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransport.java, spec-grpc/src/main/java/org/a2aproject/sdk/grpc/utils/JSONRPCUtils.java, and the existing untracked client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java; this checkpoint is also appended here. Pre-existing worktree changes were preserved. No SSE ID correlation, capability enablement, other review findings, T6b/T6c/T7, Phase 3, commit, push, or PR work was performed.

**Verification:** the clean exact selector mvn -B -ntp -pl client/transport/jsonrpc -am clean -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test exited 0 at 15:40:16Z, reactor 8/8. Surefire discovered 48 selected tests: Phase2PublicClientTest 15, JSON-RPC SSEEventListenerTest 17, SPI SSEEventListenerTest 16; zero failures, errors, or skips. The JSONRPCUtils/StreamDecoder-only selector was not run before the checkpoint; the new overloads are exercised by the selected transport controlled-peer tests. Scoped tracked-file git diff --check emitted no whitespace diagnostics; the no-index check of the pre-existing untracked peer test also emitted no whitespace diagnostics (exit 1 only because the file differs from /dev/null). Immediately after the clean build, at 15:40:26Z, javap showed JSONRPCTransport.class mtime 15:40:14Z and no ECJ “Unresolved compilation problems” stub.

**Raw Maven invocation ledger:** preserve cumulative baseline 90. This iteration used invocations 91–97: (91) test compile error, unchecked JsonProcessingException in the first peer lambda; (92) runtime failure from stale/missing A2AHttpClient.AsyncSSECall output; (93) expected RED, mismatch regression failed because no exception was thrown; (94) focused GREEN passed 1/1; (95) exact selector test compile error, two peer-constructor IOException declarations; (96) exact selector failed with 15 runtime errors because JSONRPCTransport.class contained ECJ “Unresolved compilation problems” stubs. The coordinator found no other live Maven/javac/ECJ process at 15:39:44Z; classpath inspection pointed at this worktree’s target/classes, and the cause of invocation 96 remains unproven. (97) the clean exact selector passed as reported above, and the immediate class inspection was clean. No invocation timed out or exceeded 20 minutes. Substantive no-progress streak is 0; retain all earlier run ledgers and failure history.

This is coherent implementation iteration 2/10 of the 2026-11-12T15:25:52Z–17:25:52Z run; the Sol status review is iteration 1 and the prior 90-invocation baseline remains unchanged. Requested model/role: gpt-6-luna, medium reasoning. Runtime model identity and speed metadata are unavailable and not independently verified. The next action is focused independent gpt-6-sol, medium review of this unary correction. Remaining T6a work includes SSE response-ID correlation and prior open findings #1, #4, #5; T6a remains incomplete. Stop at this checkpoint.

### T6a selected-1.1 JSON-RPC unary response-ID independent review — iteration 3/10 — 2026-11-12T15:44Z

Independent `gpt-6-sol`, medium-reasoning review accepted the bounded unary request/response ID-correlation slice for selected-1.1 GET, LIST, async GET and unary SEND; [review detail](phase-2/reviews/t6a-unary-id-review.md). Runtime model identity/speed metadata unavailable. Each call captures one outgoing UUID and supplies it to the ID-aware response decoder. The decoder rejects missing/null/wrong-type/mismatched IDs before result or error decoding; a wrong-ID `-32010` error is not mapped to typed generation mismatch, while a matching one remains typed. Legacy 1.0 decoder signatures and request paths remain unchanged. The fixed-ID unary success fixtures were updated to echo request IDs; fixed IDs in SSE/HTTP-failure fixtures are outside this unary acceptance. No blocking defect found within scope.

Saved clean exact-selector Surefire XML independently inspected: JSON-RPC peer 15/15, JSON-RPC SSE 17/17, SPI SSE 16/16 = **48/48**, zero failures/errors/skips; XML mtimes follow the touched source/test files. Iteration 2 records clean selector exit 0, reactor 8/8 at 15:40:16Z. `javap` of post-build transport and codec classes shows the ID-aware overload calls with no unresolved-compilation stub. No Maven rerun or executable probe was needed. Cumulative raw Maven invocations remain **97**, including prior failure history and invocation 96's unproven ECJ-stub cause; substantive no-progress streak **0**. Preserve exhausted prior run ledgers **10/10/45**, **5/5/58**, **5/5/90**. This review consumes **iteration 3/10** of the 2026-11-12T15:25:52Z–17:25:52Z run.

**T6a remains incomplete.** SSE response-ID correlation and earlier open findings #1 (public GET/LIST capability selection), #4 (stream outcome/race/demand evidence), and #5 (two-public-client shared-HTTP ownership) remain separate. No implementation, fixture edit, T6b/T6c/T7, Phase 3, commit, push, or PR by this review. Stop after this checkpoint.

### T6a selected-1.1 JSON-RPC SSE response-ID correlation — iteration 4/10 — 2026-11-12T15:54Z checkpoint

Implemented the bounded selected-1.1 JSON-RPC SSE response-ID correlation slice. `JSONRPCTransport.streamInput` now generates one UUID before constructing the selected SSE listener and HTTP call, passes that exact ID to both the listener and `createPostBuilder`, and the builder serializes it on the request. The additive `JSONRPCUtils.decodeResponseEvent(envelope, version, requestId)` reuses the accepted unary ID-aware `responseResult` helper, so ID presence, nullness, JSON type, and equality are checked before either result decoding or JSON-RPC error mapping. The selected listener cannot be constructed without an expected request ID. The legacy callback listener and its 1.0 parsing path are unchanged.

Matching JSON-RPC `-32010` remains a typed `TaskGenerationMismatchError`; a matching result containing `StreamError` remains a local `TransportFrame.streamError`. Correlation failure is terminal before known, ignored, or local-rejection frames can be published. Listener tests cover a mismatched string, missing, null, object, boolean, array, and numeric ID across known result, local-rejection result, and JSON-RPC `-32010` error envelopes; they assert one terminal error and no frames even after a later matching event is delivered. Matched local rejection and matched typed error behavior are separately asserted. Controlled-peer tests cover an unrelated-ID result followed by a later event, and an unrelated-ID `-32010` error. Selected success/status/error fixtures now echo the request ID; the held-open peer reads and echoes each request ID.

**Files changed in this iteration:** `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransport.java`, `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/sse/SSEEventListener.java`, `spec-grpc/src/main/java/org/a2aproject/sdk/grpc/utils/JSONRPCUtils.java`, `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java`, `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/sse/SSEEventListenerTest.java`, `spec-grpc/src/test/java/org/a2aproject/sdk/grpc/utils/StreamDecoderTest.java`, and this progress record. These files were already dirty or untracked from authorized Phase 2 work; the current slice's edits were limited to SSE ID correlation, its tests/fixtures, and this checkpoint. No commit, push, or PR was made.

**Verification:** The final exact selector `mvn -B -ntp -pl client/transport/jsonrpc -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` passed at 15:52:52Z, reactor **8/8**. Surefire reported SPI SSE **16/16**, JSON-RPC SSE listener **19/19**, and JSON-RPC controlled peer **17/17** = **52/52**, zero failures/errors/skips. The focused codec selector `mvn -B -ntp -pl spec-grpc -Dtest=JSONRPCUtilsTest,StreamDecoderTest -Dsurefire.failIfNoSpecifiedTests=false test` passed at 15:52:30Z: **68/68**, zero failures/errors/skips (JSONRPCUtils 58, StreamDecoder 10). Scoped `git -c core.fsmonitor=false diff --check` passed with no whitespace diagnostics. Java 17 selected Quarkus plugin 3.40.1.2; existing missing plugin-version warnings remain, and project Spotless checks are skipped by configuration.

**Raw Maven invocation additions (104 cumulative; baseline 97 retained):** (98) initial exact selector stopped at test compilation because the new mismatch test referenced an event-envelope helper overload not yet added; fixed the test helper. (99) expected RED reproduced the finding: the mismatched-ID peer test expected `FAILED` but the current reader completed normally. (100) first post-implementation exact selector stopped at test compilation because the test used a nonexistent `TransportFrame.localRejection()` accessor; corrected it to the existing `streamError()` accessor. (101) exact selector passed 52/52. (102) codec selector found only a test assertion mismatch: the new overload correctly threw checked `JsonMappingException`, while the test expected `RuntimeException`; corrected the expected exception. (103) codec selector passed 68/68. (104) final exact selector passed 52/52 after adding numeric-ID coverage. These were test-only corrections and an expected RED; substantive no-progress streak remains **0**. No command timed out or exceeded 20 minutes.

This is coherent implementation **iteration 4/10** of the run begun 2026-11-12T15:25:52Z, with deadline 17:25:52Z. Preserve current cumulative 104 raw Maven invocations and substantive no-progress streak 0, plus exhausted prior ledgers **10/10 with 45**, **5/5 with 58**, and **5/5 with 90** raw invocations and their failures. Requested implementation model/role: `gpt-6-luna`, medium reasoning; runtime model identity and speed metadata unavailable and not independently verified. No subagents were used.

**Next action:** focused independent `gpt-6-sol`, medium review of this SSE ID-correlation slice. The earlier SSE response-ID finding (#3) is implemented here but awaits that review. T6a remains incomplete; open findings #1 (public GET/LIST capability selection), #4 (stream outcomes, demand, and terminal-race evidence), and #5 (two public clients sharing an injected HTTP client) remain. Keep the accepted unary ID slice and the accepted HTTP status correction as recorded. No capability, unrelated SSE behavior, T6b/T6c/T7, Phase 3, commit, push, or PR work was done. Stop at this checkpoint.

### T6a selected-1.1 JSON-RPC SSE response-ID independent review — 2026-11-12 — iteration 5/10

Independent Sol review accepts the bounded SSE response-ID correlation slice; see [review detail](phase-2/reviews/t6a-sse-id-review.md). One UUID is serialized into the selected request and captured by its versioned listener. The ID-aware decoder validates every result/error envelope before creating any known, ignored, or local-rejection frame. Invalid IDs terminate once with no later frames; matching `-32010` and local `StreamError` retain their typed/distinct outcomes. Legacy callback decoding and 1.0 rejection remain as before. Original SSE finding #3 is closed for this scope; T6a remains incomplete with prior findings #1, #4, and #5 open, and no new capability advertisement.

Saved exact-selector XML reports **52/52** (SPI SSE 16, JSON-RPC SSE 19, peer 17), and codec XML **68/68** (JSONRPCUtils 58, StreamDecoder 10), all zero failures/errors/skips. The iteration-4 checkpoint records exact-selector reactor **8/8**. The listener source mtime 15:54:00Z follows its 15:52:51Z XML, so the saved selector does not prove that exact latest listener text state; the current source was reviewed directly and no concrete ID defect or targeted rerun need was found. Scoped `git -c core.fsmonitor=false diff --check` passed. This review ran no Maven or executable probe; raw invocation count remains **104**, substantive no-progress streak **0**. Preserve earlier exhausted 10/10/45, 5/5/58 and 5/5/90 ledgers and failures.

This review consumes **iteration 5/10** of the 2026-11-12T15:25:52Z–17:25:52Z run. Requested reviewer model/role: `gpt-6-sol`, medium reasoning; observed runtime model/speed metadata unavailable. No implementation, fixture edit, T6b/T6c/T7, Phase 3, commit, push, or PR. Stop after this checkpoint.

### T6a SSE ID current-source verification follow-up — 2026-11-12T15:56:58Z

Coordinator reran the exact JSON-RPC selector after the independent review found the listener source mtime newer than saved XML: `mvn -B -ntp -pl client/transport/jsonrpc -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` exited 0; reactor 8/8. Surefire: SPI SSE 16/16, JSON-RPC SSE 19/19, JSON-RPC controlled peer 17/17 = **52/52**, zero failures/errors/skips. The JSON-RPC transport module recompiled seven source files and seven test files in this run; this closes the timestamp gap for the current listener text. No source/test edit, feature change, commit, push, PR, or new coherent iteration. Cumulative raw Maven invocations are **105**; current run remains 5/10, no-progress streak 0.
### T6a public stream evidence — iteration 6/10 — 2026-11-12T16:06Z checkpoint

Completed the bounded T6a public stream evidence objective for the current run, without production changes. In the base public peer tests, Client.builder(card) selects protocol 1.1; one injected JdkA2AHttpClient instance is shared by two public Clients with concurrently held-open observations. Closing the first Client cancels only its response, and the second Client remains live, receives its Task event and completes successfully through the same injected HTTP client. Public callback coverage now verifies that an SSE StreamError becomes one request-local A2AClientException, and that a malformed known Task event followed by another event results in exactly one error callback and no event callbacks. The older selected-stream fixture now echoes the actual SSE request ID. The pre-existing selected-wrapper LIST peer now echoes its unary request ID as well, so the focused base selector remains valid after accepted unary ID correlation.

The JSON-RPC raw peer test drives finite Flow demand: the first request(1) receives the explicit unknownSkipped frame; a second unit of demand receives the following known Task frame, with no extra frame delivered before that request. The shared TransportObservation test races completion, failure and cancellation while onNext is held in progress, then verifies exactly one terminal callback and no callback starts after terminal delivery. Existing bounded-buffer overflow coverage remains in the same SPI selector. No changes were made to T5 observation behavior, capabilities, UNKNOWN_VARIANT_SKIP, public decoded GET/LIST selection, T6b/T6c/T7 or Phase 3.

Files changed in this iteration:
- client/base/src/test/java/org/a2aproject/sdk/client/Phase2PublicClientTest.java
- client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java
- client/transport/spi/src/test/java/org/a2aproject/sdk/client/transport/spi/TransportObservationTest.java
- this progress record

Focused verification:
- mvn -B -ntp -pl client/base -am -Dtest=Phase2PublicClientTest,Phase2InputTerminalTest -Dsurefire.failIfNoSpecifiedTests=false test — exit 0, reactor 12/12; base selected 7/7 (public peer 6/6, terminal 1/1), zero failures/errors/skips.
- mvn -B -ntp -pl client/transport/jsonrpc -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test — exit 0, reactor 8/8. Current Surefire XML, checked directly at 16:05:27–29Z: SPI SSE listener 16/16, JSON-RPC SSE listener 19/19, JSON-RPC peer 18/18, total 53/53, zero failures/errors/skips. Maven's final JSON-RPC module summary says 37 selected tests because it omits the SPI dependency suite; the reactor/XML total is 53.
- mvn -B -ntp -pl client/transport/spi -am -Dtest=TransportObservationTest -Dsurefire.failIfNoSpecifiedTests=false test — exit 0, reactor 7/7; observation 10/10, zero failures/errors/skips.
- Java runtime 17.0.18; compatible Quarkus plugin 3.40.1.2 selected. Existing missing-plugin-version warnings remain; Spotless checks are skipped by project configuration.

Raw Maven ledger: baseline was 105. Invocations 106–111: (106) base selector stopped at test compilation for missing AtomicReference/AtomicInteger imports; (107) base selector stopped at test compilation because the new public two-session assertion referenced a session accessor not yet added to its controlled peer; (108) after those test-only corrections, the base selector reached tests and exposed the old selected-wrapper LIST fixture's fixed response ID; it was updated to echo the request ID. (109) base selector passed 7/7; (110) JSON-RPC selector passed 53/53; (111) SPI observation selector passed 10/10. No command timed out or exceeded 20 minutes. These were compiler/test-fixture corrections, not substantive no-progress attempts; current substantive no-progress streak remains 0. Preserve the earlier historical ledgers 10/10 with 45, 5/5 with 58, 5/5 with 90, and the pre-objective 105-invocation ledger with all recorded failures.

This is public stream evidence objective implementation iteration 6/10 in the run begun 2026-11-12T15:25:52Z; deadline remains 17:25:52Z. Requested implementation model/role: gpt-6-luna, medium reasoning. Runtime model identity/speed telemetry unavailable; no subagents were used. Independent Sol review is next and should review the public/shared-client and demand/race evidence, its test assertions and latest selector XML. Review findings #4 and #5 now have focused tests pending review; earlier #1 (public decoded GET/LIST capability selection) remains intentionally deferred. Accepted SSE/unary ID correlation and HTTP status slices remain intact. No commit, push or PR. Stop after this checkpoint.

### T6a public stream evidence independent review — 2026-11-12 — iteration 7/10

Independent Sol review closes original SSE finding **#5**: two public Clients share the exact same injected JDK HTTP client, both have live controlled-peer streams, and closing one cancels only its stream while the other receives a Task and completes. Finding **#4** is narrowed: public local `StreamError` and malformed-known outcomes, controlled JSON-RPC finite raw demand, and shared SPI bounded-buffer and completion/error/cancel serialization now have passing assertions. The per-binding plan still requires a held-open **public JSON-RPC Client** completion/error/cancel race with no callback starts after terminal; the new race test is only at the shared `TransportObservation` layer. This is an acceptance evidence gap, not an observed production defect. See [review detail](phase-2/reviews/t6a-public-stream-review.md). Earlier finding #1 remains open. No `UNKNOWN_VARIANT_SKIP` capability is advertised.

Saved XML independently parsed: base **7/7** (public peer 6, terminal 1), exact JSON-RPC reactor **53/53** (SPI SSE 16, JSON-RPC SSE 19, peer 18), SPI observation **10/10**, all zero failures/errors/skips. The JSON-RPC target-module summary **37** excludes the SPI dependency's 16 tests. Relevant test mtimes precede XML; iteration-6 checkpoint records focused reactor successes 12/12, 8/8 and 7/7. Scoped diff check passed. This review ran no Maven command or executable probe, leaving cumulative raw Maven invocations **111** and substantive no-progress streak **0**. Preserve prior exhausted 10/10/45, 5/5/58 and 5/5/90 ledgers and failures.

This review consumes **iteration 7/10** of the run begun 2026-11-12T15:25:52Z, deadline 17:25:52Z. Requested reviewer model/role: `gpt-6-sol`, medium reasoning; runtime model identity/speed metadata unavailable. No implementation, T6b/T6c/T7, Phase 3, commit, push or PR. Stop after this checkpoint.

### T6a selected-1.1 JSON-RPC public enablement and public terminal race — iteration 8/10 — 2026-11-12T16:22Z checkpoint

Completed this bounded public enablement objective, pending independent Sol full-binding review. JSON-RPC provider and transport now advertise the same selected-version feature sets: 1.0 is `ORDINARY` only; 1.1 is `ORDINARY`, `GUARDED_INPUT`, `ARTIFACT_INPUT`, and `UNKNOWN_VARIANT_SKIP`. No cursor, long-poll, delivery-version, observation-handle, or live feature was added. This follows the T5 policy to declare only complete transport paths. Guard and artifact encoding was confirmed through public unary and streaming `Client.sendInput`; `userArtifacts` card gating and explicit 1.0 pre-I/O guard rejection are asserted.

Public controlled-peer coverage now calls `Client.getTask` for Task A with generations 7/8-unknown/9 and `Client.listTasks` for Tasks A and B with 7/8-unknown/9 and 17/18-unknown/19, checks known-only public timelines and pagination fields, and reads matching per-task skipped-generation evidence (8 and 18) through the selected wrapper. Both explicit 1.1 and the card-default 1.1 path are exercised. An explicit 1.0 peer proves ordinary known-only GET/LIST behavior and `ORDINARY` capability selection. A malformed known timeline member fails public GET instead of being treated as a skipped future variant.

Public send tests cover task and direct-Message unary results, empty evidence for a direct Message at the raw companion boundary, guard/artifact serialization on unary and streaming calls, card gating, typed JSON-RPC `-32010` in both send modes with exactly one request, and explicit 1.0 pre-I/O rejection. Existing streamed future-variant coverage continues to assert only known Task/status events reach public callbacks; it does not fabricate an event for the unknown variant. The required held-open public JSON-RPC terminal-race test holds an event callback in flight while a malformed known frame/remote close and `Client.close()` compete, then asserts one public terminal callback and no later event callback starts. Shared-HTTP-client ownership, finite raw demand, and SPI buffer/race tests remain as previously recorded.

**Files changed in this iteration:** `client/transport/spi/src/main/java/org/a2aproject/sdk/client/transport/spi/ClientFeature.java`; `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransport.java`; `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransportProvider.java`; `client/base/src/test/java/org/a2aproject/sdk/client/Phase2PublicClientTest.java`; `client/transport/jsonrpc/src/test/java/org/a2aproject/sdk/client/transport/jsonrpc/Phase2PublicClientTest.java`; `.claude/architecture/a2a-1.1/phase-2/api-migration.md`; this record. These files had pre-existing Phase 2 modifications/untracked state; the scoped edits above preserve that work.

**Verification:**
- Exact JSON-RPC selector `mvn -B -ntp -pl client/transport/jsonrpc -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` passed, reactor 8/8. Current XML: SPI SSE 16/16, JSON-RPC SSE 19/19, JSON-RPC peer 19/19 = **54/54**, zero failures/errors/skips.
- Base shared selector `mvn -B -ntp -pl client/base -am -Dtest=Phase2PublicClientTest,Phase2InputTerminalTest,Phase2ClientInputTest,ClientTransportPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test` passed, reactor 12/12. Current XML: base public 12/12, terminal 1/1, client input 8/8, transport policy 9/9, plus JSON-RPC peer 19/19 = **49/49**, zero failures/errors/skips.
- Focused public selector `mvn -B -ntp -pl client/base -am -Dtest=Phase2PublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test` passed after explicit 1.0 coverage was added: base public 12/12 + JSON-RPC peer 19/19 = **31/31**, zero failures/errors/skips.
- Parsed Surefire XML directly; the reported XML mtimes follow their corresponding source/test mtimes. Scoped `git -c core.fsmonitor=false diff --check` produced no whitespace diagnostics. Java runtime 17.0.18; compatible Quarkus plugin 3.40.1.2 selected. Existing missing-plugin-version warnings remain; Spotless is skipped by project configuration.

**Raw Maven ledger:** cumulative baseline **111** preserved. Invocations 112–120: (112) expected RED: public GET/LIST failed through the legacy path and malformed known content surfaced as its legacy `InvalidParamsError`; (113) test compile caught a checked `JsonProcessingException` in a newly added response fixture; (114) rerun exposed the same missing feature plus the malformed-known behavior and a malformed SSE test envelope; (115) first post-feature base selector isolated that envelope issue; (116) corrected base shared selector passed (48 total selected tests); (117) exact JSON-RPC selector passed 54/54; (118) focused public selector passed before explicit 1.0 coverage (30/30); (119) focused public selector passed after explicit 1.0 control (31/31); (120) final base shared selector passed (49/49). No command timed out or exceeded 20 minutes. These were expected RED, test compile, and test-fixture corrections; substantive no-progress streak remains **0**. Preserve all earlier raw invocation ledgers/failures, including exhausted 10/10/45, 5/5/58, and 5/5/90 histories.

This is coherent public enablement implementation **iteration 8/10** of the run begun 2026-11-12T15:25:52Z; deadline 17:25:52Z. Requested model/role: `gpt-6-luna`, medium reasoning. Runtime model identity/speed metadata are unavailable and not independently verified; no subagents were used. Findings #1 (public decoded-read capability selection) and #4 (public JSON-RPC terminal race evidence) now have passing implementation/test evidence; finding #5 remains closed from iteration 7. Request the next bounded independent `gpt-6-sol`, medium full-binding review of these public read/send/capability and race assertions plus current XML. T6a remains incomplete until review. No T6b/T6c/T7, Phase 3, commit, push, or PR was performed. Stop at this checkpoint.

### T6a JSON-RPC full-binding independent review — iteration 9/10 — 2026-11-12

Independent `gpt-6-sol`, medium-reasoning review found no P1/P2 production defect in the inspected T6a binding, but found one **P2 public integration-evidence gap**: the real `Client.builder(card)` JSON-RPC stream test does not include a message-update frame or assert that the public `TaskUpdateEvent` retains the typed `TaskMessageUpdateEvent`. Raw JSON-RPC decoding and synthetic shared-base callback mapping are separately tested; the required combined public sequence remains unproven. See [full review](phase-2/reviews/t6a-final-review.md). T6a remains incomplete pending that focused assertion and its verification; do not advance to T6b/T6c/T7 on this disposition.

Current saved XML was parsed directly: exact JSON-RPC selector **54/54**, shared base selector **49/49**, focused public selector **31/31**, each zero failures/errors/skips; totals overlap and relevant source/test mtimes precede the XML. The iteration-8 checkpoint records successful focused reactors. No Maven command or executable probe was run in this review; cumulative raw Maven invocations remain **120**, substantive no-progress streak **0**. Scoped tracked-source diff check found no whitespace issue. Requested reviewer model/role `gpt-6-sol`, medium reasoning; observed runtime model and speed metadata unavailable. This consumes review **iteration 9/10** of the 2026-11-12T15:25:52Z–17:25:52Z run. Retain all earlier 10/10/45, 5/5/58 and 5/5/90 histories and failures. No implementation, T6b/T6c/T7, Phase 3, commit, push or PR. Stop at this checkpoint.

### T6a public per-binding sequence evidence — iteration 10/10 — 2026-11-12T16:34Z checkpoint

Closed the remaining public integration-evidence gap from the iteration-9 independent review. The existing real `Client.builder(card)` selected-1.1 JSON-RPC peer test now sends matching-request-ID frames in this order: Task baseline, typed `TaskMessageUpdateEvent`, ignored future variant, typed status update, typed artifact update, then stream completion. The baseline has generation 2, timeline generations 1/2, one artifact, prior history and task metadata. Assertions check ordered public `TaskEvent`/`TaskUpdateEvent` callbacks, typed message-update identity and fields, retained generation/timeline/artifact/history/metadata, typed status/artifact values, no public callback for the future variant, one successful terminal callback, and no event callback start after terminal. The request-side guarded/artifact/tenant assertions and all other fixtures remain in this test. No production code changed; no concrete production defect was reproduced.

**Files changed in this iteration:** `client/base/src/test/java/org/a2aproject/sdk/client/Phase2PublicClientTest.java`; this progress record. The test file was already untracked before this iteration; its prior Phase 2 tests remain present. Worktree-wide pre-existing Phase 2 modifications are preserved.

**Verification:**
- Focused base public selector `mvn -B -ntp -pl client/base -am -Dtest=Phase2PublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test` passed at 16:33:09Z, reactor 12/12. Current Surefire XML: base public **12/12** plus selected JSON-RPC peer **19/19** = **31/31**, zero failures/errors/skips.
- Exact JSON-RPC selector `mvn -B -ntp -pl client/transport/jsonrpc -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` passed at 16:31:55Z, reactor 8/8. Current XML: SPI SSE **16/16**, JSON-RPC SSE **19/19**, JSON-RPC peer **19/19** = **54/54**, zero failures/errors/skips. Selector totals overlap and are not additive.
- Surefire XML was parsed directly after the final base run; all reported `skipped=0`, and the base source mtime precedes its passing public-test XML. The focused test source has no trailing whitespace; `git diff --check` on this checkpoint was clean. Maven selected Quarkus plugin 3.40.1.2 under Java 17.0.18 and emitted the existing missing-plugin-version warnings; Spotless is skipped by project configuration.

**Raw Maven ledger:** cumulative baseline **120** preserved. Invocations 121–128: (121) test compilation failed because the response fixture called checked `JsonUtil.toJson` inside the peer lambda; (122) expected RED, 12 base tests with the new assertion failing because only two callbacks were emitted; (123) first run with added message/artifact frames exposed absent-versus-empty text-part metadata in whole-Message equality; (124) after field-level message checks, prior history equality exposed the same optional metadata default; (125) after history identity checks, whole-Artifact equality exposed optional metadata/extensions defaults; (126) focused base selector passed 31/31; (127) exact JSON-RPC selector passed 54/54; (128) focused base selector passed 31/31 after import-order cleanup. Preserve each failure above and all historical ledgers/failures. No invocation timed out or exceeded 20 minutes. These were one expected RED, a checked-fixture compile correction and serialization-stable assertion corrections; substantive no-progress streak remains **0**. Cumulative raw Maven invocation count is now **128**.

This is final implementation iteration **10/10** of the run begun 2026-11-12T15:25:52Z, deadline 17:25:52Z. Requested implementation model/role: `gpt-6-luna`, medium reasoning. Runtime model identity and speed metadata remain unavailable; no subagents were used. Request a focused independent `gpt-6-sol`, medium re-review of this public sequence assertion and the passing base/JSON-RPC selector XML to disposition the sole P2 evidence gap. Keep T6a pending until that review. Preserve prior exhausted 10/10/45, 5/5/58 and 5/5/90 ledgers and failures. No T6b/T6c/T7, Phase 3, production changes, commit, push or PR. Stop after this checkpoint.

### T6a public message-update sequence focused independent re-review — 2026-11-12

Independent `gpt-6-sol`, medium-reasoning re-review **closes the sole iteration-9 P2 public integration-evidence finding**. The corrected real `Client.builder(card)` JSON-RPC peer test sends matching-ID Task → typed message update → ignored future variant → typed status update → typed artifact update → terminal; it asserts ordered public callback types/values, retained Task generation/timeline/artifact/metadata/history, no fabricated future callback, one successful terminal callback and no event callback start after terminal. Exact decoded-object pass-through follows `Client.getClientEvent`; the test's self-referential `assertSame` adds no independent identity proof, while its typed value/identity field assertions provide the meaningful peer evidence. See the [focused re-review](phase-2/reviews/t6a-final-review.md#focused-iteration-10-correction-re-review--2026-11-12). **T6a JSON-RPC is accepted within the reviewed Phase 2 binding scope.** This is not acceptance of T6b/T6c/T7, full reactor or later-phase operational guarantees.

Current saved XML was parsed directly: focused base public selector **31/31** and exact JSON-RPC selector **54/54**, zero failures/errors/skips; totals overlap. The corrected base test source mtime precedes its passing XML, and JSON-RPC production source is older than the focused XML. Iteration 10 records successful 12/12 and 8/8 reactors. This independent verification adds no Maven invocation, implementation objective or production edit; cumulative raw Maven invocations remain **128**, substantive no-progress streak **0**. The 2026-11-12 run is exhausted at **10/10** coherent iterations and retains its 17:25:52Z deadline. Preserve the earlier exhausted **10/10/45**, **5/5/58** and **5/5/90** histories/failures. Requested reviewer model/role `gpt-6-sol`, medium reasoning; observed runtime model and speed metadata unavailable. No T6b/T6c/T7, Phase 3, commit, push or PR. Stop at this checkpoint.

### T6b REST-only bounded run — expired checkpoint — 2026-11-12T19:00Z

The newly authorized T6b-only run began 16:54:39Z with at most ten coherent iterations or a hard deadline of 18:54:39Z. Final status was inspected at 19:00:51Z, 2 hours 6 minutes 12 seconds after start. The elapsed-time bound expired; no further T6b implementation or review proceeded. The detailed [REST unary checkpoint](phase-2/checkpoints/t6b-rest-unary.md) records the exact state and handoff. Known work comprises one unfinished REST unary objective, continued through fresh Luna agents without resetting the count, and one read-only Sol SSE investigation. Requested models were `gpt-6-luna` medium for implementation and `gpt-6-sol` medium for targeted investigation, both normal speed; runtime model/speed telemetry was unavailable. Astra was not selected.

The only new test file is `client/transport/rest/src/test/java/org/a2aproject/sdk/client/transport/rest/Phase2PublicClientTest.java` (raw transport tests). Raw Maven #129 failed at test compilation from a REST/base classpath cycle; #130 compiled and exposed the expected unsupported decoded-read default. The existing REST suite reported 24/24 in #130, while the new suite had one expected failure and one error; the full focused selector did **not** pass. No later Maven invocation was reported. Cumulative raw Maven count is 130, preserving all historical failures; current substantive no-progress streak is 0 because no production fix was tested. No REST production file changed during this run; the `RestErrorMapper` generation-mismatch delta was pre-existing. No public REST peer test or independent full REST capability review was completed. **T6b is incomplete, not accepted.** T6 shared and T6a retain their earlier acceptance and checks; their code was not changed or rerun. The Sol investigation identified missing cancellable/status-aware GET SSE and lost non-2xx body in status-aware POST, but did not review a completed binding. Keep `UNKNOWN_VARIANT_SKIP` disabled until the full REST path is implemented and verified.

Next separately authorized T6b action: implement and verify decoded synchronous GET/LIST from the saved RED, then a cancellable async GET HTTP seam, unary SEND, versioned SSE/status/body preservation, accurate feature flags, public REST controlled-peer tests, and a full independent Sol review. Any shared HTTP correction requires affected shared/JSON-RPC checks. Preserve independent historical compatibility fixtures and legacy 1.0 behavior. Do not begin T6c or T7 before T6b acceptance. No commit, push, or PR was made.

### T6b REST public completion/error/cancel race — iteration 3/10 — 2026-11-12T20:41Z

Added a public `Client.builder(card)` REST controlled-peer race test for P2 finding #2; see the durable [race checkpoint](phase-2/checkpoints/t6b-rest-public-race.md). The selected immutable 1.1 client sends REST `POST /message:stream`, the `A2A-Version: 1.1` header, and a REST message body. The peer holds a public event callback, then races malformed SSE data plus a following valid event and peer close against `Client.close()`. After release, the test verifies exactly one terminal callback and no later event callback start. Its real REST `StreamResponse` protobuf-JSON frames exercise the public 1.1 path; no JSON-RPC or raw-only substitute is used.

Raw Maven #180: `mvn -B -ntp -pl client/base -am -Dtest=Phase2RestPublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test` completed at 20:41:42Z with exit 0 and `BUILD SUCCESS`, all 12 reactor modules successful. `Phase2RestPublicClientTest` reported 8/8, zero failures/errors/skips. The new test passes on existing production code, so no race defect was reproduced and no REST production edit or raw REST selector was needed. Existing Java 17/Quarkus and missing plugin-version warnings remain; Spotless was skipped by configuration. Requested implementation model/role: `gpt-6-luna`, medium reasoning; runtime model metadata unavailable. No subagents were used.

This objective adds acceptance evidence only; substantive no-progress streak remains **0**. Cumulative raw Maven count is **180**, retaining prior ledger #179 and all older histories/failures. Files changed in this objective: `client/base/src/test/java/org/a2aproject/sdk/client/Phase2RestPublicClientTest.java`, this progress record, and the new race checkpoint. No T6a accepted code, T6c/T7, production change, commit, push or PR. T6b still requires its focused independent review before acceptance.

### T6b REST binding accepted — 2026-11-12T20:45Z final handoff

The new user-authorized T6b correction/review window began **20:28:06Z**, with a **21:28:06Z** deadline and a fresh maximum of ten coherent objectives. T6b was accepted after **5/10** objectives by the [independent Sol full and focused review](phase-2/reviews/t6b-final-review.md): original full review, bounded HTTP error-body correction, public REST terminal-race evidence, focused P2 re-review, and explicit final binding disposition. The earlier expired T6b windows and failures remain recorded in their checkpoints; this allowance did not erase them. Cumulative raw Maven invocation count is **180**, with all prior failures retained. The current per-defect substantive no-progress streak is **0**. Requested roles/models were `gpt-6-sol` medium for both independent review objectives and `gpt-6-luna` medium for both correction/evidence objectives, at normal speed; observed runtime model/speed metadata was unavailable. Astra was not selected.

**Implementation scope:** selected immutable REST 1.1 decoded GET/LIST/async GET and unary SEND preserve per-Task unknown-timeline evidence; Task and direct Message responses retain distinct evidence shapes. REST 1.1 stream input and subscription use version-aware known/ignored/malformed/local-rejection handling, finite demand and owned-call cancellation; explicit 1.0 retains its legacy path. Provider and selected transport advertise matching implemented 1.1 features. Public `Client.builder(card)` REST peers cover request fields, version/tenant/interceptor headers, guards/artifacts, typed 409 without retry, A/B known timeline with skipped 8/18, message/status/artifact callbacks, held-open close and shared injected-client ownership, plus completion/error/cancel race. Necessary source-compatible HTTP seams support cancellable GET and status-aware SSE. The final correction bounds status-aware non-2xx SSE error bodies to 64 KiB before materialization in JDK, Vert.x and Android; over-limit reads cancel and report one status/header-bearing error, while small HTTP 409 ErrorInfo remains typed. Exact file lists and failed/green command ledgers are in [unary](phase-2/checkpoints/t6b-rest-unary.md), [async](phase-2/checkpoints/t6b-rest-async.md), [SEND](phase-2/checkpoints/t6b-rest-send.md), [SSE](phase-2/checkpoints/t6b-rest-sse.md), [public](phase-2/checkpoints/t6b-rest-public.md), [HTTP bound](phase-2/checkpoints/t6b-rest-error-bound.md), and [public race](phase-2/checkpoints/t6b-rest-public-race.md) checkpoints. The existing `RestErrorMapper` generation-mismatch mapping preceded this T6b run.

**Exact verification and review:** raw Maven #178 passed REST **65/65** (peer 22, transport 24, mapper 7, listener 12) and JSON-RPC **63/63**, zero failures/errors/skips. JDK #179 passed **22/22**; Vert.x #176 **20/20** and Android #177 **25/25**, each zero failures/errors/skips, including GET/POST oversized no-newline error-body and small 409 cases. Public base #180 passed **8/8** in a 12-module reactor, zero failures/errors/skips, including the controlled public race. These selector totals overlap and are not additive. The independent reviewer parsed current Surefire XML and confirmed relevant source/test mtimes precede it; the focused re-review closed both original P2 findings and found no new P1/P2 blocker. **T6b REST is accepted within the reviewed Phase 2 binding scope.** No full-reactor build, historical compatibility fixture gate, T7 evidence gate or aggregate Phase 2 acceptance is claimed. The finite HTTP cap intentionally yields a generic status-bearing error for oversized bodies; exact resident memory peaks inside HTTP libraries were not measured.

**T6c handoff:** gRPC remains a separate binding assignment. Start only under its own authorization and budget, using the accepted T6 shared, T6a JSON-RPC and T6b REST contracts/evidence unchanged unless a concrete defect requires a focused shared correction. T6c must implement and independently verify its selected-version request/response/evidence, streaming unknown/malformed/local-rejection semantics and public-client path; T7 remains after all binding adapters. No T6c/T7 work, commit, push, PR or Phase 2 completion claim was made in this T6b run. Stop here at T6b acceptance.
