> Historical review record through the first checkpoint (14:09 UTC). Its pending0.3/noauthorization conclusions describe that time. The subsequent explicit user authorization, successful checks and [Phase 0 exit review](exit-review.md) supersede those current-state observations without erasing the original breach or findings.

# Independent Phase 0 review — 2026-11-10

Review scope: architecture/client contract and Phase 0 research only, at SDK `2817ed78d02d9a6e609dafdf7f5129484edef017`. No repository, build, external-state, commit, push, or PR mutation by reviewer. Read authoritative README, architecture, spec-delta, compatibility, validation, acceptance and implementation-loop, plus staged spec findings/HTTP trace, client inventory, next-session handoff and progress. This is independent assessment, not architecture approval or production conformance evidence. Final retained artifact/baseline recheck is pending parent evidence.

## Important findings

Update after recheck: the mandatory `required-client-rules.md` supplement and its next-session link address R1 and R2 without modifying the original seven architecture documents. R1a remains explicitly gated; that is appropriate Phase 0 handling, not false closure. Final evidence/retention recheck remains pending.

### R1 — Mandatory invalid-generation fallback is missing from recommended reducer contract

**Location:** `.claude/architecture/a2a-1.1/architecture.md:499`, `:515–558`; staged `/tmp/a2a-phase0-client-inventory.md:114`; Phase 0 spec findings do not currently record it.

**Evidence:** Pinned RC `docs/specification.md:587` requires a client observing generation zero or consecutive events of the same task with identical generation to conclude generation is unsupported, and stop using generation for ordering, long-polling and precondition checks for the remainder of that interaction. The current design discusses contiguous events, gap reconciliation and de-duplication but does not pin this mandatory disablement. Snapshot recovery must not silently re-enable generation in the same interaction. Treating equal events as ordinary duplicate replay could conceal this required branch.

**Concrete remedy:** Add an explicit generation-reliability flag/internal policy and focused future tests for zero and consecutive duplicate generations, permanent disablement for the interaction, stream-order fallback, and suppression/rejection of generation-based long-poll/precondition/cursor behavior once disabled. Preserve the selected protocol version; this is not version negotiation. This can be recorded in Phase 0 findings/next-session constraints without editing the user's existing architecture files.

### R1a — Same-generation recovery snapshots need a pinned interpretation

**Location:** `.claude/architecture/a2a-1.1/architecture.md:518–521`, `:533–538`; RC `docs/specification.md:587` and `specification/a2a.proto:955–969`.

**Evidence:** Replacement/resync snapshots may carry an unchanged generation. The RC duplicate-generation rule has no explicit full-Task/snapshot exemption, while StreamResponse carries both Task snapshots and mutation events. Whether its term "event" excludes snapshots, and the boundary of "interaction" across reconnects, are not pinned by inspected text. The proto distinguishes payload descriptions but does not expressly override this prose rule. This is an ambiguity, not proof that snapshots necessarily violate the RC.

**Concrete remedy:** Keep same-generation recovery as a dependent gate. Do not silently invent a snapshot exemption or assume reconnect clears the interaction reliability state. Apply mandatory disablement wherever the pinned rule applies; obtain authoritative clarification or label a consciously selected experimental SDK policy before implementing the disputed recovery behavior. A newer valid snapshot must not automatically reset already-disabled generation use within the same interaction.

### R2 — Unknown TimelineEntry sequence accounting needs separation from semantic synchronization

**Location:** `.claude/architecture/a2a-1.1/architecture.md:520–528`, `:546–555`; `.claude/architecture/a2a-1.1/validation.md` unknown-kind row; `/tmp/a2a-phase0-client-inventory.md:114`.

**Evidence:** RC `docs/specification.md:630` expressly requires skipping an unknown timeline kind while accounting for its known generation, which does not count as a gap. The combined unknown/gap row currently preserves the last safe generation and forbids reliable cursor advancement; an implementer could apply that to the delivery/sequence watermark and manufacture a gap. Conservative `INCOMPLETE` semantic TaskView and unknown history coverage are defensible, but are different from receiving no generation.

**Concrete remedy:** Pin separate internal sequence/delivery and semantic synchronization markers. Account for known unknown-entry generation in missed-event detection; do not synthesize unknown content or claim semantic synchronization. A conservative resume cursor may stay behind when necessary to recover semantics, but label it independently of sequence receipt and avoid a replay loop. Add future trace `known N → unknown entry N+1 → known N+2` proving no missing-sequence claim and no false complete view. Unknown whole stream variants lacking accessible generation remain a separate case.

## Secondary observations and final-check conditions

- **Bootstrap remains a decision, not a closed gate.** Architecture `:704–709` describes initial message in generation 1; staged spec findings propose a candidate baseline at 1 and separate first append at 2. Both correctly acknowledge the gate. The final handoff must preserve both as alternatives rather than accidentally record either as the selected normative trace; request one pinned trace before dependent implementation.
- **Retention/provenance is not yet final.** Staged handoff links refer to sibling `spec-gates.md`, `client-inventory.md` and retained evidence not yet present under its temporary directory. Final checkpoint must copy/link a complete repository artifact set and retain scripts, manifests, raw sources/fixtures and frozen expectations. `/tmp` paths alone are not a resumable handoff.
- **Baseline still pending.** At review time the sole root `mvn clean install` is live under its original bounded handle. Do not mark the Phase 0 baseline captured until a result/timeout and actual module/test/skip evidence is retained. No reviewer build was launched.
- **Historical fixture harness still pending.** `manifest.json` currently freezes original HEAD fixtures with exact source paths/checksums and identifies adapted upstream protos; this can provide independent frozen old expectations. It is not unchanged released-client binary evidence. Future comparisons must consume the frozen expected files, not recapture expected output. Current `run.py` permits explicit capture to a new directory only and otherwise compares to frozen expectations. Inspect final harness outputs and fixture scope before claiming the deliverable complete.
- **Progress counters require finalization.** Current progress says zero completed and three active investigations; update completed iterations, elapsed time, no-progress counter and exact verification-command counts at checkpoint. Parallel research shares one run deadline; it must not create another budget. No implementation fix/verify cycles occurred, so zero implementation attempts is presently justified; baseline/harness verification commands still need distinct command counts.

## Contract assessment

No critical contradiction found in the revised public/base versus raw SPI ownership: public TaskObservation/TaskView/LiveSession/InputHandle remain in base; wire DTOs in spec; version/capability/raw handles below base in SPI prevent transport→base cycles. Companion interfaces preserve old abstract method descriptors, with actual source/binary/wire probes explicitly required rather than asserted by default methods.

Freezing CURRENT_PROTOCOL_VERSION at 1.0 independently of a new 1.1 client default addresses recompilation as well as constant inlining. The documents correctly identify the provider that ignores its explicit descriptor and uses changed implicit constructors as requiring an adapter/audit; arbitrary custom behavior is not guaranteed.

Both tracing/metrics and propagation telemetry wrapper families are inventoried. Capability intersection, no unwrapping, plain legacy-wrapper removal of advanced features and fail-closed new 1.1 wrapper construction are coherent; historical explicit-1.0 catch/log/continue remains a separately named baseline.

The as-of TaskView/independent history-window model avoids an unconditional full-history promise. Public callback shapes stay intact, partial cursor observations use additive APIs, and synchronized current projection is distinct from complete history. Apply R2 to prevent conflating missing semantics with missing sequence delivery.

Input result semantics appropriately distinguish NOT_SENT/REJECTED/COMMITTED/INDETERMINATE; readiness, queue admission, commit and executor delivery are separated. HTTP exchange association, optional StreamError referenceId, same-ID artifact chunks and multi-input batch receipts remain explicitly gated. No invented status RPC, receipt object, sticky connection rule, universal idempotency or retry guarantee was found. Successful ordinary Task operation responses are documented as operation acceptance evidence, not exact later lookup or executor delivery.

The six gates are responsibly left partially unresolved: RC-only final target, conflicting discovery wording, ordinary cursor versus initial snapshot/live terminal nuance, bootstrap trace, HTTP exchange discrimination/rejection association, and RC artifact-origin/collision behavior. The artifact ID-only key and ordinary terminal subscription rejection have direct pinned evidence. PR #2411 source diff is distinguished from its stale body and from the selected RC. These findings permit a useful Phase 0 checkpoint; they do not satisfy production A01–A14 or authorize Phase 1.

## Two-consecutive-no-progress safeguard recommendation

Recommend adopting the proposed early stop after two consecutive iterations without demonstrable advancement, verified necessary change, or evidence materially narrowing the next action. Retain the ten-iteration/two-hour/three-attempt limits; elapsed command polling is not another iteration. Record the counter and meaningful-progress evidence at each iteration/checkpoint. This is the independent review recommendation requested by Phase 0, **not user approval**. A stopped investigation reports incomplete work and preserves gates; it never resets the run budget or changes goal status to complete.


## Baseline recheck update

Read bounded process metadata and root log after completion: exit 0, no timeout, start 13:19:16Z/end 13:29:13Z, elapsed 597.21 seconds, BUILD SUCCESS. Independently aggregated current reports and compared each path/count with retained `baseline-test-summary.json`: **247 Surefire XML reports (3835 tests) plus one Failsafe report (9 tests)**, total 248 reports / 3844 discovered / 3721 executed / 123 skipped / 0 failures / 0 errors. The Failsafe report is OpenTelemetryA2AIT, so calling all 248 reports Surefire would be inaccurate. Parent notified to correct terminology. JPA totals 58 with no skips, including MySQL 6 and Postgres 5; replicated queue multi-instance 2 and single-instance 5 with no skips. This resolves the pending baseline-result condition, subject to retaining the exact log/metadata/report summary and skip details in the final checkpoint. It verifies unchanged baseline only, not 1.1 functionality.


## Important run-control finding — R3: historical harness exceeded the attempt cap

**Location:** historical fixture worker's seven reported prep/capture/verify cycles; final fixture attempt log and implementation-progress.md must retain the exact breakdown.

**Evidence available at this review point:** parent reports seven materially distinct fixture preparation/capture/verification cycles and has immediately interrupted the worker, allowed packaging only, and informed the user. The authorized safeguard is at most three attempts on a failing criterion, regardless of multiple cycles in one nominal iteration or delegation. Root baseline remained a single successful bounded invocation; the fixture issue does not invalidate that independent result. Exact worker cycle log is still awaiting final packaging, so seven is presently the parent's conservative reported count rather than an independently reconstructed log count.

**Concrete remedy:** Record the breach plainly, retain all counts/cycle evidence, stop dependent fixture fixes/probes/captures immediately, and perform read-only packaging/review only. Do not report run-limit compliance, relabel cycles to reset the counter, or rerun under this session's remaining time budget. A further attempt needs an explicit user decision on a new approach/budget. Mark the standalone 0.3 executable probe unverified wherever applicable; captured immutable raw 0.3 fixtures/provenance and reactor-built 0.3 tests are distinct from a passing independent executable probe. Partial Phase 0 is a valid checkpoint, not completed verification. A final fixture worker report may narrow the exact unresolved probe scope; it must not erase the breach.


## Historical package recheck update

Read final `fixtures/README.md`, `attempts.md`, classpath/run scripts and package contents without executing probes or preparation. All **200 SHA256SUMS entries match**. Manifest has 120 source/raw entries, including **112 exact historical Java fixture constants**; `expected/1.0` has **34 outputs**, with the reported prior result explicitly separated as 33 positive comparisons plus one known failure fingerprint. There is **no expected/0.3 directory**. The one 0.3 capture produced 26 outputs before STOP and was neither verified nor promoted; the README correctly leaves that criterion incomplete. Frozen HEAD fixtures/protos are independently retained pre-change expectations, not released historical binary or live-peer execution.

The final attempt audit supports a real cap breach but not a precise claim of seven distinct fix/verify cycles: audit item 5 repeats the comparison failure in item 4, item 6 records another capture/diff without a new fix, and an additional 0.3 capture is listed outside the seven. Preserve **seven as a conservative recorded audit/attempt count**, include that qualification and the additional capture, and retain the detailed breakdown. Successive extraction/harness fixes continued after failures beyond the safeguard; overlapping audit entries do not excuse the breach or authorize another attempt. No reviewer rerun occurred. Packaging records that all worker calls had returned and no download/build/probe remained live after STOP; that last process-state claim is worker-reported, not independently reconstructed from timestamps.

Read-only preservation check at 13:37 UTC: all seven initial user-document SHA-256 values still match `initial-user-files.json`; no original architecture input was changed by the Phase 0 corrections.


## Final retained-checkpoint consistency verdict

Final read-only recheck completed at 2026-11-10T14:04:57.880055+00:00. Reviewed retained `implementation-progress.md` plus `phase-0/{independent-review,required-client-rules,decisions,next-session,baseline,README,spec-gates}.md`, existing fixture provenance/audit and the full local link/artifact set. No new normative research, test, probe preparation, capture or wire comparison was performed.

**Verdict:** the retained package is a coherent, resumable **incomplete Phase 0 checkpoint**. No new critical or important artifact/contract contradiction was found in final packaging. R1/R2 are addressed by the mandatory supplement; same-generation recovery and the original normative residuals remain explicitly gated. This assessment is not architecture approval, run-limit compliance, completion of A01–A14 or production conformance evidence.

- Every relative Markdown file link checked from progress and retained phase-0 Markdown resolves.
- All 200 retained fixture checksum entries match; all 18 upstream checksum entries match the 19-file source archive. The extra archive member is its checksum manifest. The unverified 0.3 archive contains the reported 26 files and is not promoted to expected/0.3.
- All seven original user-document hashes still match. Baseline compressed log ends in BUILD SUCCESS and retained metadata/counts agree with the previously independently checked 247 Surefire + 1 Failsafe report totals. The final text correctly distinguishes 3721 executed from 123 skipped and baseline coverage from future 1.1 acceptance.
- The final conservative A03 audit counter **8** correctly includes seven reported entries plus the already-completed 0.3 capture, expressly notes overlap and does not claim eight distinct fix/verify cycles. The attempt-cap breach is plainly retained, dependent fixture execution is stopped, and further work requires a user decision on approach/attempt budget. Final harness textual cleanup is expressly not claimed verified. Time/iteration budgets were not exhausted or reset; iteration 4 is accurately still active pending this verdict, after which the parent may record its completion timestamp/counter.
- The user's A2A-Version reply is correctly recorded as version selection, without treating it as an exchange-routing decision. The clarified same-version/two-exchange question remains pending; no routing policy is inferred.

Earlier report paragraphs describing pending baseline, packaging or counters are review-time observations superseded by the recheck updates and this final verdict. The original unresolved protocol gates and the historical attempt-limit breach remain substantive limitations; packaging integrity does not close them.


## Final upstream-issue delta review

Read-only review of staged progress and decisions/HTTP trace/next-session plus retained issue #2415, its comment and exact read commands found **no new important contradiction**. The saved OPEN issue directly covers opening-versus-uplink discrimination, multiple same-task exchanges and StreamError downlink selection. Its sole comment links PR2411 without resolving routing. Reusing this existing issue rather than creating a duplicate fits the user's focused issue request; no GitHub write is claimed or evidenced by the recorded read commands. G5 remains blocked, and A2A-Version is not treated as an exchange selector.

The staged record now closes **5 of 10 iterations**, with no active iteration and no run-budget reset. Conservative A03=8, its overlaps, the attempt-limit breach and unverified 0.3 capture remain explicit. The corrected next-session first-action bullet requires a new user approach/attempt-budget decision before any historical probe preparation, capture or comparison, eliminating the earlier ambiguous rerun instruction. Phase 0 stays incomplete; Phase 1 and production work remain unauthorized. No probe, capture, compile, wire comparison or build was executed for this delta review. Earlier pending-question wording is superseded by the existing-issue follow-up, not by a normative routing decision.
