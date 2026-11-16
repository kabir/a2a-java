# A2A 1.1 task list

Current workflow, approved 2026-11-16: the developer selects one task, starts an
implementor, then requests independent review at a meaningful boundary. No
persistent coordinator or automatic progression. Start only the assigned row;
this checklist is not implementation or commit authorization.

The latest inspected HEAD is `34a120f73` (Phase 2 reactor regression fixes), after
checkpoint `eb8cff42a` (Phase 2 through T6b). These commits are context, not reset
targets or proof of T6c/T7 acceptance. Existing specification gates remain open.
No runtime verification was rerun for this workflow conversion.

## Phase 2

Keep the reviewed task identifiers: their dependency order is intentionally
T1 → T2 → T4 → T3 → T4b → T5 → T6 shared → T6a/T6b/T6c → T7.
Earlier completion/dispositions below are retained records, not new verification.
The detailed [Phase 2 plan](phase-2/implementation-plan.md) remains the scope and
acceptance authority; an unchecked box in that older plan is not a new finding.

| Task | Depends on | Implementation | Review | Brief / evidence |
| --- | --- | --- | --- | --- |
| T1 API decisions/probes | Phase 1 | Recorded complete | Retained plan/API decisions | [Plan](phase-2/implementation-plan.md#t1--freeze-the-api-decision-and-downstream-compatibility-probes-a04-a14), [migration](phase-2/api-migration.md) |
| T2 shared model/Gson | T1 | Recorded complete | Prior disposition retained | [Plan](phase-2/implementation-plan.md#t2--shared-model-and-gson-forms-a04-a07-a10-a12), [progress](implementation-progress.md) |
| T4 pinned proto/mapping/decode | T2 | Recorded complete | Reviewed | [Review](phase-2/reviews/t4-review.md) |
| T3 older-view projection | T4 | Recorded complete | Prior disposition retained | [Plan](phase-2/implementation-plan.md#t3--older-view-projection-before-new-events-reach-bindings-a03-a07), [progress](implementation-progress.md) |
| T4b additive server boundary | T3 | Recorded complete | Reviewed | [Review](phase-2/reviews/t4b-review.md) |
| T5 transport SPI/wrappers | T4b | Recorded complete | Accepted in scope | [Review](phase-2/reviews/t5-review.md) |
| T6 shared public adapter | T5 | Recorded complete | Accepted in scope | [Review](phase-2/reviews/t6-shared-review.md) |
| T6a JSON-RPC | T6 shared | Recorded complete | Accepted in scope | [Review](phase-2/reviews/t6a-final-review.md) |
| T6b REST | T6 shared | Recorded complete | Accepted in scope | [Review](phase-2/reviews/t6b-final-review.md), [checkpoint](phase-2/checkpoints/through-t6b-commit.md) |
| **T6c gRPC** | T6 shared; retain HTTP regressions | **Implemented and accepted; public race follow-up and both review notes closed** | Focused correction re-review confirms closure; focused suite passed 8/8 | [Race evidence checkpoint](phase-2/checkpoints/t6c-public-race-followup.md), [accepted correction re-review](phase-2/reviews/t6c-public-race-followup-review.md#correction-re-review--2026-11-16), [original T6c correction re-review](phase-2/reviews/t6c-review.md#correction-re-review--2026-11-16), [correction evidence](phase-2/t6c-grpc-handoff.md#review-corrections-follow-up), [binding contract](phase-2/implementation-plan.md#t6--public-client-path-and-three-independently-verifiable-binding-adapters-a02-a04-a07-a09-a10-a12) |
| **T7 compatibility/evidence gate** | All T6 bindings accepted | **Implemented and accepted:** no-version public helpers preserve 1.0; explicit 1.0 filters zero-valued additions; explicit 1.1 retains them; unsupported new legacy payloads are rejected. Focused regressions, final candidate clean install and affected frozen-serializer comparisons pass | **Focused Sol review accepted the correction; T7 accepted for its compatibility/evidence scope.** Phase 2 complete within the approved experimental pinned-RC scope; final conformance and dependent future specification gates remain open | [Correction re-review](phase-2/reviews/t7-review.md#correction-re-review--2026-11-16), [focused helper review](phase-2/reviews/t7-helper-correction-review.md), [verification](phase-2/verification.md#final-candidate-build-and-affected-comparison-refresh--2026-11-16), [handoff](phase-2/next-session.md), [gate contract](phase-2/implementation-plan.md#t7--independent-compatibility-and-phase-evidence-gate-a03-a04-a07-a14) |

### T6c assignment

Read the T6 shared contracts and T6c bullets in the existing plan, the latest
relevant checkpoint, and the API migration/gate records. Inspect the current gRPC
transport, provider, observer and public client tests. Preserve the accepted
SDK-owned channel/metadata protections and explicit version selection.

Implement only the missing gRPC public SDK path, mappings/evidence, typed errors,
known/unknown/malformed streaming behavior and accurate capabilities required by
the plan. Keep 1.0 behavior and historical inputs intact. Operational mutation,
replay and live guarantees remain assigned to later phases.

If needed, split this assignment into small, developer-selected subtasks:
request/unary/evidence paths; streaming/lifecycle paths; public-client evidence.
These are suggested boundaries, not new API decisions or permission to execute
all slices automatically. Reuse the reviewed plan; ask Sol for a short delta plan
only if actual code reveals an unresolved design choice.

Run the plan's gRPC selector and relevant shared public-client checks; record
which target tests actually executed. Add affected HTTP checks if shared code
changes. Save a short handoff and stop for independent Sol review. Write the
review/disposition under `phase-2/reviews/`; keep implementation and review status
separate. No full-phase acceptance or T7 execution follows automatically.

### T7 assignment

After all bindings are accepted, use the exact evidence layers/commands in T7:
public SDK controlled peers, malformed/future raw wire cases, unchanged historical
fixtures/binaries, API/SPI probes, changed-module and same-instance regressions,
independent review and the required bounded candidate full build. Preserve the
order and scope of those gates. Do not recapture expected historical outputs.

Evidence collection is checkpointed in [verification](phase-2/verification.md).
The candidate 0.3 fixture comparison and regression gates passed. Corrected
1.0 evidence separates historical Gson outputs from protobuf payload and
endpoint serializers: frozen historical fixtures pass 33 positive cases plus
the unchanged cancellation failure; candidate protobuf payloads match 4/4;
candidate event/protobuf endpoint serializers match frozen runtime output
8/8. Candidate model-level Gson output matches 0/9 historical `.json`
expectations; the correction re-review finds this an intentional canonical
model change under T2/D1, separate from selected 1.0 wire output. The initial
review findings remain preserved and the correction disposition is appended
in [the T7 review](phase-2/reviews/t7-review.md#correction-re-review--2026-11-16).
The no-version helper contract is addressed by the bounded correction recorded
in [verification](phase-2/verification.md#t7-public-helper-compatibility-correction--2026-11-16).
T7's final build and the three affected comparison refreshes passed and are
recorded in the [verification](phase-2/verification.md). T7 is accepted for
its compatibility/evidence scope. Phase 2 is complete within the approved
experimental pinned-RC scope; open specification gates govern affected later
work and final conformance. See the [close-out](phase-2/checkpoints/phase2-complete.md). Follow the
[handoff](phase-2/next-session.md); do not begin Phase 3 without a separate
developer assignment.

## Later phases

| Phase | Status | Next planning work |
| --- | --- | --- |
| 0: semantics/baseline | Retained completed research | [Historical evidence](phase-0/decisions.md); unresolved dependent gates still apply |
| 1: exact interfaces/client versions | Committed checkpoint | [Historical handoff](phase-1/next-session.md) |
| 2: models/adapters/public bindings | Complete within the approved experimental pinned-RC scope | [Close-out and evidence](phase-2/checkpoints/phase2-complete.md); Phase 3 needs a separate assignment |
| 3: mutation/publication/client reducer | Not assigned | On developer request, Sol makes a focused task plan from the [phase contract](implementation-tasks.md#phase-3-authoritative-task-mutation-and-publication) and then-current code |
| 4: observation/versioned delivery | Not assigned | Plan after prerequisites and gates are resolved |
| 5: live execution/bindings | Not assigned | Plan only the supported, resolved specification contract |
| 6: final audit/docs | Not assigned | Retain all final acceptance and independent evidence requirements |

## How to assign and close work

Give Luna medium/normal the selected task, relevant plan sections, files and
checks. It implements and records a short handoff, then stops. Give Sol
medium/normal the actual diff and relevant evidence for independent review.
The developer routes findings back for correction and chooses the next task.
Astra remains prohibited. No subagents are required or implicitly authorized.

Use [task bounds](acceptance.md#run-limits) and the
[implementation guide](implementation-tasks.md#task-execution-and-progress-record).
Record exact command outcomes, not mandatory iteration totals or globally numbered
Maven calls. Preserve old ledgers; do not reread/rewrite them for bookkeeping.
No architecture/acceptance gate or commit-approval requirement is removed.
