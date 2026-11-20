# A2A 1.1 task list

Current workflow, approved 2026-11-16: the developer selects one task, starts an
implementor, then requests independent review at a meaningful boundary. No
persistent coordinator or automatic progression. Start only the assigned row;
this checklist is not implementation or commit authorization.

At Phase 2 close-out, the inspected HEAD was `b99a29152` on
`support/a2a-1.1.0` in worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`.
Phase 2 close-out evidence is retained below; no Phase 2 build was repeated to
begin Phase 3 planning. That checkpoint's initial status contained only
untracked `test.log`. Commits are context, not reset targets. Existing dependent
specification gates remain open.

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

## Phase 3 — P3-T04 regression corrections independently accepted

Planning is prepared in [implementation-plan.md](phase-3/implementation-plan.md).
**Approved at plan level by the independent Sol High/normal correction
[re-review](phase-3/reviews/plan-review.md), 2026-11-17.** All three original
findings are closed; no blocking regression was found. P3-T01 is implemented
and independently accepted after correction re-review, with 48/48 focused tests
passing. P3-T02 is independently accepted after correction re-review, with
28/28 focused tests passing. P3-T03 is independently accepted after its final
correction re-review; 80/80 focused and 57/57 regression checks pass. See its
[handoff](phase-3/handoffs/P3-T03.md) and retained review dispositions.
P3-T04 is independently accepted after its third correction re-review. Its
required selector passed 109/109, with retained accepted handler 5/5 and T03
regression 60/60 evidence in the [handoff](phase-3/handoffs/P3-T04.md). The developer
requested a full-reactor test checkpoint before the next task and asked to hold
the next prompt. The existing-task compatibility correction and fixture
migrations are implemented; the saved full run completed 83 modules with zero
failures/errors, 4,241 executed tests and 123 skips. See the
[updated checkpoint](phase-3/handoffs/P3-T04-full-reactor.md) and
[correction handoff](phase-3/handoffs/P3-T04-full-reactor-correction.md).
The regression repair and subsequent admission, stream-lifecycle and sender
corrections are now accepted by the [fourth independent correction follow-up review](phase-3/reviews/P3-T04-full-reactor-correction-review.md#fourth-follow-up-review--2026-11-20-1111-utc).
The accepted follow-up snapshot passed 216 targeted tests with zero failures,
errors or skips and failure-ignore disabled. The full-reactor evidence above
predates these follow-ups. P3-T05 remains unassigned and its prompt stays held
pending developer selection. No automatic progression is implied.

Task identifiers are in intended execution order. Dependencies below abbreviate
`P3-TNN` as `TNN`; they require accepted predecessor contracts/behavior. Each row
has concrete files, acceptance scenarios, commands, a review boundary and a short
implementation brief in the plan. All task implementation/review statuses remain
separate from plan preparation. Planning requested Sol medium/normal; exact
serving metadata is unavailable, not asserted as telemetry.

| Task | Depends on | Implementation | Review |
| --- | --- | --- | --- |
| P3-T01 atomic store companion/in-memory authority | Accepted plan; developer assignment | Implemented and independently accepted; focused tests 48/48 passed ([handoff](phase-3/handoffs/P3-T01.md)) | Accepted for P3-T01 scope; fresh 48/48 focused tests ([second correction re-review](phase-3/reviews/P3-T01-review.md#second-correction-re-review--2026-11-17-1728-utc)) |
| P3-T02 canonical input/delta rules | T01 | Implemented and independently accepted; focused 28/28 tests passed ([handoff](phase-3/handoffs/P3-T02.md#correction-handoff--2026-11-17)) | Accepted for P3-T02 scope; fresh focused 28/28 ([correction re-review](phase-3/reviews/P3-T02-review.md#correction-re-review--2026-11-17-1843-utc)) |
| P3-T03 committed bus/local publication order | T01, T02 | Implemented and independently accepted; focused 80/80 and cancellation/aggregator/prerequisite 57/57 ([follow-up evidence](phase-3/handoffs/P3-T03.md#follow-up-test-synchronization-correction-2026-11-18)) | Accepted for P3-T03 scope; fresh 80/80 focused and 57/57 regressions ([final correction re-review](phase-3/reviews/P3-T03-review.md#final-correction-re-review--2026-11-18-1757-utc)) |
| P3-T04 proposal provenance/local completion | T01, T03 | Implemented and independently accepted; required 109/109 selector, handler/cancellation/config 5/5, T03 regressions 60/60; see [latest correction handoff](phase-3/handoffs/P3-T04.md#third-correction-implementation-addendum--2026-11-19) | Accepted for P3-T04 scope ([third correction re-review](phase-3/reviews/P3-T04-review.md#third-correction-re-review--2026-11-19-1412-utc)) |
| P3-T05 full-Task normalization/migration | T02, T04 | Planned; not started | Pending |
| P3-T06 materialization config enrollment | T01, T03 | Planned; not started | Pending |
| P3-T07 ordinary-send branches/read projections | T02–T06 | Planned; not started | Pending |
| P3-T08 JPA transactional authority | T01–T03, T06, T07 | Planned; not started | Pending |
| P3-T09 baseline migration/writer fencing | T08 | Planned; not started | Pending |
| P3-T10 committed replication/finalization | T03–T05, T08, T09 | Planned; not started | Pending |
| P3-T11 shared ordered webhook dispatch | T03, T06, T08–T10 | Planned; not started | Pending implementation review; approved lane/quiescence tradeoff |
| P3-T12 topology/provisioning gate | T07–T11 | Planned; not started | Pending |
| P3-T13 real multi-instance evidence | T10, T12 | Planned; not started; requires DB/Kafka/Docker evidence | Pending |
| P3-T14 bounded publication-gap handling | T03, T07, T10 | Planned; not started | Pending; G3 limits retained |
| P3-T15 generation/timeline reducer/TaskView | Phase 2; T02/T05 rules | Planned; not started | Pending |
| P3-T16 public ordinary entry-point views/reliability | T15 | Planned; not started | Pending |
| P3-T17 JSON-RPC production integration | T07, T12, T14, T16 | Planned; not started | Pending |
| P3-T18 REST production integration | Accepted T17 shared fixture and prerequisites | Planned; not started | Pending |
| P3-T19 gRPC production integration | Accepted T17 shared fixture and prerequisites | Planned; not started | Pending |
| P3-T20 compatibility/migration/phase gate | All prior rows | Planned; not started | Pending independent phase audit |

Retain the selected experimental bootstrap/origin/no-retry policies; G4/G6
normative residuals do not require selecting them again. G1/G2 final claims,
G3 cursor/reliable repeated-snapshot recovery and G5 live outcomes stay open.
The plan uses bounded stream failure plus explicit GetTask for publication gaps,
without inventing a retry code, snapshot exemption or lossless delivery guarantee.
Authority/fencing and real infrastructure are prerequisites for their dependent
cluster/migration tasks; they do not block unrelated reducer/unit work. Any new
unavoidable common-API/client-SPI break or authority/scope change requires an
explicit material decision. See the plan's gate table and review checkpoints.

The supplied review's three findings are addressed in the approved plan:
all selected-1.1 sendMessage/ordinary-subscription/input routes share reducer and
reliability state (T16–T19); cross-node webhooks use a shared dispatch gate with
real paused-dispatch evidence (new T11, T13); existing-task independent-store
enrollment partial effects/IDs and unsafe-compensation tests are explicit (T06–T08).
Original T11–T19 became T12–T20 to retain execution-order numbering. These
corrections are now independently accepted at plan level. Preserve the approved
shared lane's quiescence/availability tradeoff: abandoned claims can block webhook
dispatch indefinitely until quiescence is proven. It adds no payload outbox,
lossless guarantee or automatic stale-owner takeover.

The retained [review](phase-3/reviews/plan-review.md) also requires batch-final
snapshot provenance (T01/T03/T11/T13), synchronous unary sendMessage timing,
and unsupported-before-dispatch-only optional subscription fallback with no
wrapper bypass or duplicate I/O (T16–T19). The plan records these as binding
implementation checkpoints; approval is not runtime evidence.

Preserve the independent review report and its reviewed-input hashes. Future
selected-task handoffs/reviews use `phase-3/handoffs/P3-TNN.md` and
`phase-3/reviews/P3-TNN-review.md`. Preserve existing Phase 2 evidence and test.log.

## Later phases

| Phase | Status | Next planning work |
| --- | --- | --- |
| 0: semantics/baseline | Retained completed research | [Historical evidence](phase-0/decisions.md); unresolved dependent gates still apply |
| 1: exact interfaces/client versions | Committed checkpoint | [Historical handoff](phase-1/next-session.md) |
| 2: models/adapters/public bindings | Complete within the approved experimental pinned-RC scope | [Close-out and evidence](phase-2/checkpoints/phase2-complete.md); Phase 3 needs a separate assignment |
| 3: mutation/publication/client reducer | P3-T01 through P3-T04 implemented and independently accepted | Full-reactor checks pass ([checkpoint](phase-3/handoffs/P3-T04-full-reactor.md)); regression-correction review not recorded; P3-T05 prompt held |
| 4: observation/versioned delivery | Not assigned | Plan after prerequisites and gates are resolved |
| 5: live execution/bindings | Not assigned | Plan only the supported, resolved specification contract |
| 6: final audit/docs | Not assigned | Retain all final acceptance and independent evidence requirements |

## How to assign and close work

Give Luna medium/normal the selected task, relevant plan sections, files and
checks. It implements and records a short handoff, then stops. Give Sol
medium/normal the actual diff and relevant evidence for independent review.
The developer routes findings back for correction and chooses the next task.
Checkpoint each independently accepted task in an explicitly developer-authorized
commit before starting the next task; use that commit SHA as its base. Review
acceptance does not authorize committing. Astra remains prohibited. No subagents
are required or implicitly authorized.

Use [task bounds](acceptance.md#run-limits) and the
[implementation guide](implementation-tasks.md#task-execution-and-progress-record).
Record exact command outcomes, not mandatory iteration totals or globally numbered
Maven calls. Preserve old ledgers; do not reread/rewrite them for bookkeeping.
No architecture/acceptance gate or commit-approval requirement is removed.
