# Current Phase 1 checkpoint — verified candidate approved for commit

Accepted Phase 0 commit: **`e468dd70a415d17c19ae70d75750b6464d983ad1`**. Work remains on `support/a2a-1.1.0` in the requested worktree; HEAD and index are unchanged.

Current full clean install passes **83 modules, 4,041 discovered / 3,918 executed / 123 unchanged skips / zero failures or errors**. Independent source review finds no blocking defect in the approved pinned SDK-owned gRPC profile, caller migrations, or fresh allocation cleanup. REST subscription and native 0.3 registration corrections remain covered. Phase 1 only; experimental RC registration remains incomplete for later semantics.

The five-iteration continuation authorized at 18:37:06Z is complete: **5/5 additional iterations, 17 cumulative Phase 1 iterations**, no-progress 0. Closed at 2026-11-10T19:36:01.629226+00:00 before the fixed 20:37:06Z deadline. No implementation allowance remains. Prior Phase 1 eight plus four iterations, historical F1 three-failure stop, Phase 0 eight completed / A03 audit ten / no-progress zero / original breach are preserved below. The resumed revised F1 implementation has no failed material cycle. No commit, staging, push, PR or Phase 2 occurred; creating a commit requires explicit approval.

Current authoritative checkpoint documents: [verification](phase-1/verification.md), [independent review](phase-1/evidence/resume-final-independent-review.md), [commit proposal](phase-1/commit-proposal.md), [next session](phase-1/next-session.md), [future follow-ups](phase-1/follow-ups.md). Earlier current/stop/readiness statements in the historical ledger below retain their original checkpoint meaning; this preface and the final appended closure govern the current candidate.

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
