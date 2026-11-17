# P3-T02 independent implementation review — 2026-11-17

**Disposition: Changes required for P3-T02 scope.** The planner meets most of
the selected batch, history/timeline, ownership, admission and purity rules, but
the append event is not a valid canonical delta, and the authority can misclassify
a slot-reservation failure as a planner rejection. No correction was made here.

## Review provenance and limits

- Review start: **2026-11-17 17:56:53 UTC**. Two-hour review deadline:
  **19:56:53 UTC**. Requested settings: **Sol, medium reasoning, normal speed**;
  serving model/settings metadata is unavailable and is not asserted as telemetry.
  Astra was not selected.
- Workspace `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch
  `support/a2a-1.1.0`, HEAD/base
  `8c1f9ddf5ce5de7107eb8086e6fd7e999eaffd0a`. The candidate was uncommitted:
  modified `TaskMutationAuthority.java`, `task-list.md`, and
  `phase-3/implementation-plan.md`; untracked T02 handoff, `MutationOrigin`,
  `PendingTaskInput`, `TaskMutationPlanner`, and `TaskMutationPlannerTest`.
  No staged changes or later commits were present. The complete source and test
  files were read directly because ordinary `git diff` omits them.
- Inspected `AGENTS.md`; the T02 brief and handoff; the P3-T01 handoff and latest
  accepted review; the plan’s interface, policy and batch contracts; plan review;
  acceptance A05/A07/A10 and relevant validation material; architecture mutation,
  admission, artifact, history and timeline rules; G4/G6 decisions and policies;
  required client rules; and the T02/T01 tests and current artifact consumer.
- Compared the implementation-plan change with the base. It is execution-status
  maintenance only; the P3-T02 requirements are unchanged. The task-list and
  handoff status changes match the candidate and prior verification. No existing
  P3-T02 review report existed; this report is new. `test.log` was not inspected,
  read, written, or used.
- No handler, authorization provider, publication path, T03/T05 work, external
  communication, commit or push was performed. This review does not accept or
  authorize any of those activities.

## Findings

### 1. P1 — append event contains the accumulated artifact, so reducing it duplicates parts

**Location:** `TaskMutationPlanner.java:102-111,203-217`.

The append branch correctly builds the final stored artifact from existing parts
plus incoming parts, but returns that same merged artifact in an event whose
`append` flag remains `true`. The existing consumer contract is explicit:
`Utils.appendArtifactToTask` appends the event artifact’s parts to the current
Task (`Utils.java:148-169`). For a task whose `doc` contains `[old]`, appending
`[chunk]` at generation 5 yields planned storage `[old, chunk]`; the event also
contains `[old, chunk]`, so applying it to the previous task produces
`[old, old, chunk]`. This violates the requirement that the result contain the
exact canonical events and that stored and emitted stamps/state agree (A05 and
the architecture’s canonical mutation contract).

For the same example, the planned stored artifact has range `1..5`, and that
range is copied into the event artifact. The existing append consumer builds
from the prior artifact and does not copy the event range, so applying the event
leaves the prior range (for example `1..3`). Top-level event metadata and
`lastChunk` are copied from the supplied event; the embedded artifact metadata
has already been merged with prior metadata, rather than containing just this
append’s metadata. Merging that map again is value-idempotent, but it does not
make the event payload a delta. The current test checks planned storage,
`lastChunk`, and the event artifact end generation
(`TaskMutationPlannerTest.java:227-244`), but never reduces the event against the
previous Task, so it misses both the duplicate-parts result and range reduction
behavior.

**Smallest correction / discriminating test:** keep the accumulated artifact for
the final Task, but construct the append event from only the incoming chunk,
with normalized origin and the assigned event generation. Define its range and
metadata representation so applying that event advances the prior artifact to
the planned final state. Add a test that applies the emitted event to the prior
Task using the actual append consumer (or the specified 1.1 reducer) and asserts
parts `[old, chunk]` exactly once, metadata, `lastChunk`, event generation 5, and
final range `1..5`. Keep separate assertions for replacement, which is a complete
artifact payload with `append=false`.

### 2. P2 — slot-callback errors can be surfaced as typed planner rejections

**Location:** `TaskMutationAuthority.java:31-33,41-60`; the slot-callback failure
is wrapped at `InMemoryTaskStore.java:224-229`.

The authority unwraps any `TaskGenerationMismatchError`, `InvalidParamsError`, or
`TaskNotFoundError` found anywhere under a `TaskMutationNotCommittedException`.
The accepted T01 store contract uses that same wrapper for both planner runtime
failures and slot-callback runtime failures (`AtomicTaskStore.java:24-30`). Thus,
if the `TaskCommitSlotCallback` in the three-argument authority overload throws
one of those protocol error types—for example, an internal reservation rejects
with `InvalidParamsError`—the store correctly reports a definite non-commit,
but the authority presents it as a client/planner rejection. The exception
provenance is lost. Only known **planner** errors are specified to regain their
typed protocol meaning; reservation/storage failures must retain their store
classification.

**Smallest correction / discriminating test:** mark planner callback failures
separately before they reach the store (or otherwise carry an explicit failure
origin), and unwrap the three protocol error types only from that planner path.
Exercise both authority overloads: known planner errors remain typed, while a
reservation callback throwing the same typed exception remains a definite store
failure with its cause preserved. The current code does preserve
`TaskMutationOutcomeUnknownException` and unexpected in-memory storage runtime
failures: the authority catches only `TaskMutationNotCommittedException`, while
unknown outcomes have a separate exception type. A direct authority-level
pass-through regression is still useful evidence, but I found no current
misclassification of those unknown outcomes.

## Independent assessment and evidence gaps

- **Generation/batch semantics:** The guard is checked once against the supplied
  authoritative pre-batch Task. Opening M=1, A1=2, A2=3, separately emitted S=4,
  and the final snapshot at 4 are implemented and asserted. Later message then
  artifacts receive consecutive planner-owned stamps; supplied stamps are
  ignored. Planning uses local copies, and the invalid final artifact test checks
  no events, slot reservation, Task mutation, artifact prefix or timeline prefix
  escapes. T01 returns one final snapshot alongside the original ordered events.
- **History, timeline and status:** Existing status-message/history occurrence
  behavior is retained for input-message and status updates. Messages and status
  updates add timeline entries independently; artifact updates do not. Same-state
  status progress advances once, and interrupted states can continue. The tests
  cover these paths.
- **`TASK_STATE_UNSPECIFIED`:** `rejectTerminal` explicitly rejects it even though
  `TaskState.TASK_STATE_UNSPECIFIED` is constructed with `isFinal=false`
  (`TaskState.java:66-89`). The controlling mutation rule says to reject once
  terminal (`architecture.md:713-716`), and the TaskState Javadoc lists
  UNSPECIFIED as terminal both in its state description and the `isFinal()` list.
  Therefore the planner’s explicit rejection follows the documented terminal
  contract; relying only on the inconsistent enum flag would contradict that
  contract. The compatibility consequence is that existing `isFinal()`-based
  legacy consumers can still treat an unspecified-state task as non-final, while
  future mutations through this planner will reject it. T02 does not alter the
  enum or activate runtime routing; align those existing consumers separately
  before claiming uniform runtime behavior.
- **Artifact/admission rules:** Effective `userArtifacts` is required only for
  USER artifact input; artifact-only input is accepted for an existing task and
  rejected on creation; message is required for creation. Admission denial maps
  to `TaskNotFoundError`; existing Task/context identity is checked in the
  planner and the store independently checks the plan Task ID against its key.
  Missing stored role is normalized as AGENT; explicit role mismatches and both
  cross-origin edit directions are rejected; unrelated USER artifacts are
  preserved. The planner relies on the future adapter to supply already-resolved
  tenant, authorization and effective-card facts; it does not perform those
  lookups or claim to recheck them transactionally. Rejecting append to a missing
  artifact is consistent with append’s existing-ID semantics and leaves legacy
  `Utils` behavior unchanged.
- **Purity and compatibility:** The planner reads no clock, storage, config,
  authorization provider or user code. `PendingTaskInput` captures time before
  the callback and defensively copies the request collections. The existing
  `TaskMutationAuthority` signatures and legacy TaskStore/API utilities remain;
  no spec model, handler or runtime behavior was changed. The only authority
  concern is the error-origin ambiguity in finding 2.
- **Test limitations:** The required selector was run once, from the repository
  root, with the requested `/usr/bin/timeout -k 30s 1200s` bound:

  ```sh
  /usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am -Dtest=TaskMutationPlannerTest,AtomicTaskStoreContractTest -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  It exited **0** at **2026-11-17 17:58:56 UTC**. The target-module Surefire XML
  refreshed at that time reports `TaskMutationPlannerTest` **12** and
  `AtomicTaskStoreContractTest` **15**: **27 total, zero failures, errors or
  skips**. Retained XML before the run was timestamped 17:46:01 UTC and had the
  same counts; it is implementation evidence, not the fresh review run. Maven
  reported “Nothing to compile” for the modules, so this was fresh test execution
  against already compiled candidate classes, not a clean compilation. The
  10-module reactor succeeded; target-module XML, not reactor count, establishes
  the 27 tests. `git diff --check` passed for tracked changes. No full-reactor or
  Phase 2 build, handler integration, JPA, replication, T03 publication, T05
  normalization, T06 enrollment or T07 orchestration was run.

This disposition is limited to P3-T02. The developer should assign corrections
and a separate re-review; this acceptance record authorizes no commit or T03 work.

## Correction re-review — 2026-11-17, 18:43 UTC

**Current disposition: Accepted for P3-T02 scope.** Both findings from the first
review are closed in the current candidate. The initial findings and
“Changes required” disposition above remain the historical review record.

### Re-review provenance and candidate

- Re-review started **2026-11-17 18:43:46 UTC**; two-hour deadline
  **20:43:46 UTC**. Requested settings: **Sol, medium reasoning, normal speed**;
  serving model/settings metadata is unavailable and is not asserted as telemetry.
  Issue #1303 remains established; Astra was not selected.
- Workspace `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch
  `support/a2a-1.1.0`, HEAD/base still
  `8c1f9ddf5ce5de7107eb8086e6fd7e999eaffd0a`. The implementation remained
  uncommitted and unstaged. I reviewed the changed planner, authority, new
  planner tests, correction handoff, task index and plan status. No later commit
  was present. The plan diff changes execution-status wording only; the T02
  requirements remain intact. `test.log` was not inspected, read, written, or
  used.
- No implementation or test files were changed in this re-review. No handler,
  publication path, T03/T05 work, commit, push or external communication was
  performed.

### Disposition of the findings

1. **Prior P1 append-event duplication — closed.** The planner now keeps the
   accumulated artifact in the final Task and constructs an append event from
   only the incoming chunk, with incoming artifact metadata, authenticated
   creator role, assigned event generation and authoritative aggregate range
   (`TaskMutationPlanner.java:203-224`). The new regression applies that event
   to the prior Task using `Utils.appendArtifactToTask` for the existing parts
   and metadata semantics, then applies the documented 1.1 range update. It
   verifies `[old, chunk]` exactly once, merged metadata, `lastChunk`, event
   generation 5 and final range `1..5` (`TaskMutationPlannerTest.java:250-286`).
   Replacement remains a complete payload. The range step is test-local because
   the production reducer belongs to T15; this re-review does not claim that
   reducer has been implemented. The event now carries the range needed by that
   later reducer, and the append-delta defect identified in T02 is resolved.
2. **Prior P2 planner/reservation error-provenance ambiguity — closed.** The
   authority wraps only the three recognized protocol errors thrown by
   `TaskMutation.plan` in a private `PlannerRejection` marker, and unwraps only
   that marker from the store wrapper. It passes the reservation callback
   unchanged (`TaskMutationAuthority.java:29-80`). The new regression throws an
   `InvalidParamsError` from the reservation callback and verifies the outward
   result remains `TaskMutationNotCommittedException`, retains the original
   cause, and leaves state unchanged (`TaskMutationPlannerTest.java:142-160`).
   Existing typed planner-error tests still cover both authority overloads.
   `TaskMutationOutcomeUnknownException` remains outside the authority’s
   `TaskMutationNotCommittedException` catch path. There is no new direct
   authority-level unknown-outcome regression; the exception classification is
   nevertheless preserved by the current code and the T01 store tests cover its
   underlying before/after replacement behavior.

The correction introduces no additional correctness or compatibility finding
within P3-T02. The earlier `TASK_STATE_UNSPECIFIED` assessment and its existing
`isFinal()` compatibility consequence remain as recorded above. This acceptance
is limited to T02 and does not start T03 or authorize a commit.

### Fresh verification and limits

- Before the re-review run, the retained target Surefire XML was timestamped
  **18:42:48 UTC** and reported 13 planner plus 15 atomic-store tests, all
  passing. That is correction-handoff evidence, not this re-review’s run.
- Ran the required focused selector once under the requested 20-minute process
  bound plus up to 30 seconds cleanup:

  ```sh
  /usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am -Dtest=TaskMutationPlannerTest,AtomicTaskStoreContractTest -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  It exited **0** at **2026-11-17 18:44:45 UTC**. Refreshed target-module XML
  reports `TaskMutationPlannerTest` **13** and `AtomicTaskStoreContractTest`
  **15**, totaling **28 tests with zero failures, errors or skips**. Maven
  reported no compilation was needed, so this is fresh test execution against
  the already compiled correction candidate, not a clean compilation. The
  10-module reactor succeeded; the target-module reports establish the count.
  Existing Java 17/Quarkus compatibility warnings were emitted and Spotless was
  skipped by its configured lifecycle. `git diff --check` passed for tracked
  changes. No full-reactor/Phase 2 build, production reducer, handler, JPA,
  replication, T03 publication or T07 orchestration was tested.

The developer retains control of any commit and next task selection.
