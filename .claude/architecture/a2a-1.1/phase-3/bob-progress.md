# Phase 3 Bob-coordinated progress record

## Workflow authorization (recorded once; do not rewrite)

Explicit developer assignment, received 2026-11-23, for P3-T06 through P3-T20.
For this Bob assignment it supersedes the repository instructions requiring Luna
implementation / Sol review (IBM Bob is used instead), manually started sessions
(Bob coordinates separate contexts where available), stopping after every task, and
separate per-commit permission (local task commits are authorized once acceptance
conditions are met). All other architecture, compatibility, evidence and safety
requirements remain. Astra is not selected. Model/tool metadata is recorded only
when actually observable; none is invented. Pushes, PRs, releases, external
messages, live-data migration and real credential changes are NOT authorized.
Issue: #1303 ("This fixes #1303" in commit messages). `test.log` absent at start;
must stay untouched. Logs go under `/private/tmp`. Phases 4–6 are out of scope.
Independent review means a fresh Bob context labelled as Bob review; if unavailable,
self-review is labelled as such and does not satisfy a mandatory independent gate.

## CHECKPOINT 2026-11-24 12:50 UTC — P3-T08 COMMITTED; read this first

- **P3-T08 finished** within its 10:27–12:27 UTC window for implementation/corrections/reviews; commit follows base `3826b0333`
  (see `git log -1`). Reviews (Bob, fresh contexts, not Sol): 1 Changes required (F1-F4 major), 2 Changes required (N1 save() lock order),
  3 Accepted within scope (N4 minor test weakness, N5 note: counter-row-first lock order for any future shared-tx caller). Record: `reviews/P3-T08-review.md`.
- Evidence: required command 48/48 + push-config 17 (1 pre-existing @Disabled skip) `/private/tmp/t08-required4.log`; full task-store module 81/81, 0 skips
  `/private/tmp/t08-fullmodule4.log`; real PostgreSQL 16 / MySQL 8 mutation races pass; candidate hash `156bfc5c8483ebcaeb990fa8f1d4ae399084664515ad69d9b8a0f22ce900c3b1`.
  Failed/preserved: `/private/tmp/t08-run1.log`, `t08-run5-db.log` (MySQL gap-lock deadlock), `t08-required3-preserved.log`.
- Completed in this assignment: T06 `e229ce160`, T15 `399b6b047`, T07 `3826b0333`, T08 (this commit). T16 not started.
- **Next ready by dependency:** P3-T09 (migration baseline + enforced writer fencing; needs real PostgreSQL credential/GRANT-REVOKE evidence in a disposable
  fixture; Podman socket works). T16 is an independent client branch. Each new task needs a fresh developer-granted backstop; none active now.
- Not claimed by T08: writer fencing (T09), committed replication/finalization (T10), cluster evidence (T13). `mutateInTransaction` has no production caller yet.

## P3-T08 RESUMED 2026-11-24 10:27 UTC (developer: "Let's finish T08")

Fresh developer-granted two-hour backstop: start 10:27 UTC, deadline 12:27 UTC (`date -u`). Base `3826b0333`, candidate files as listed below,
no concurrent processes, `test.log` absent. Prior failed approaches: none. Commands bounded to min(20 min, remaining).

## CHECKPOINT 2026-11-24 10:16 UTC — P3-T08 PARKED AGAIN (superseded by resume above), UNCOMMITTED CANDIDATE

- Second backstop (20:36–22:36 UTC) expired by wall clock (`date -u` read 08:49 then 10:16 UTC on 11-24 during/after tool calls;
  tool calls repeatedly stalled). Parked; not committed; no review requested; T08 NOT complete. Bob cannot renew the budget.
- **Uncommitted candidate (base `3826b0333`):** modified `extras/task-store-database-jpa/pom.xml` (test dep on push-config JPA store),
  `JpaDatabaseTaskStore.java` (now `AtomicTaskStore`; `readSnapshot`/`mutate`; `mutateInTransaction` for shared tx; list uses
  `TaskReadProjection` for timeline), `JpaTask.java` (columns `store_revision`, `writer_epoch`, `migration_marker`, `@PostLoad` cache
  reset), test `persistence.xml`; new `JpaTaskMutationTransactions.java`, `JpaTaskRevisionAllocator.java`,
  `JpaTaskRevisionCounter.java`, tests `JpaAtomicTaskMutationTest.java`, `JpaMaterializationEnrollmentTest.java`.
  Not yet done: README/storage.md DDL for new columns + `a2a_task_revision_counter`; required full check command; Bob review.
- **Evidence (run 1, `/private/tmp/t08-run1.log`, `-o`, failure.ignore=false):** 14 tests, 12 pass, 1 error, 1 failure.
  1. `JpaAtomicTaskMutationTest.listAppliesIndependentHistoryAndTimelineWindows`: test bug/assumption — `Task.timeline()` is null
     when none stored; assert null-or-empty instead (or store a timeline).
  2. `JpaMaterializationEnrollmentTest.immediatelyTerminalCreation...`: `FinalizationObserver` result null. Likely the
     `@Observes(AFTER_SUCCESS)` bean in a nested test class is not discovered/fired in the test app, or the event is fired by
     `JpaTaskMutationTransactions` before the observer map is read asynchronously. Diagnose (one attempt so far; not a failed approach yet).
  Also unverified: the contention tests passed, but only on H2; no PostgreSQL evidence (T09/T13 own that).
- Known design risks for the reviewer: `JpaTaskRevisionAllocator.self` injected proxy field and `ensureCounter` REQUIRES_NEW during an
  outer tx under H2 locking; `PESSIMISTIC_WRITE` on absent row does not by itself serialize creation (PK violation yields
  not-committed); shared-tx path fires `TaskFinalizedEvent` inside caller tx.
- **Exact next action:** developer grants a new two-hour T08 backstop; then fix the 2 test issues, run the plan's required
  check command (`-pl extras/task-store-database-jpa,extras/push-notification-config-store-database-jpa -am -Dtest=JpaAtomicTaskMutationTest,JpaMaterializationEnrollmentTest,JpaDatabaseTaskStoreTest,JpaTaskOwnershipTest,JpaPushNotificationConfigStoreTest`),
  update docs, freeze candidate, fresh Bob review, commit. T16 remains an independent alternative.

## P3-T08 RESUMED 2026-11-23 20:36 UTC (developer: "You can continue" after park)

Interpreted as developer-granted fresh two-hour backstop: start 20:36 UTC, deadline 22:36 UTC. Base `3826b0333`, tree clean
apart from this file, no concurrent processes. Failed approaches: none. Active task: P3-T08.

## CHECKPOINT 2026-11-23 20:19 UTC — P3-T08 PARKED (backstop expired, no code written; since resumed above)

- T08 started 17:05 UTC (base `3826b0333`), deadline 19:03 UTC. At 20:19 UTC (`date -u`) the deadline had passed; the
  session spent the interval on recovery/inspection only. No source, test, doc or schema file was edited; the only change
  is this file. Per the retained rule, T08 is parked, not restarted; Bob cannot renew the budget. Failed approaches: none.
- **Exact next action:** developer grants a new two-hour T08 backstop (or tells Bob to run independent T16 instead,
  which would get its own fresh deadline). Then start T08 from the notes below.
- T08 design notes from inspection (restart here):
  - `JpaDatabaseTaskStore` (extras/task-store-database-jpa) implements `TaskStore, TaskStateProvider` only; it must implement
    `AtomicTaskStore` (`readSnapshot`, `mutate`, `mutate(..., TaskCommitSlotCallback)`), mirroring `InMemoryTaskStore.mutate`
    semantics (planner errors -> `TaskMutationNotCommittedException`, unchanged plan keeps revision, empty-event changed plan
    commits without slot, unknown commit -> `TaskMutationOutcomeUnknownException`, slot cancelled on any failure, `markCommitted`
    only after the real JTA commit completes).
  - `JpaTask` needs new columns: store `revision` (BIGINT, monotonic per row, never reused after delete+recreate: use a
    sequence/global counter, not row-local), writer epoch and migration marker (schema for T09 fencing). README/storage.md DDL
    must list them (both currently show only 6 columns).
  - Atomicity: row lock (`PESSIMISTIC_WRITE`) or conditional update inside one `@Transactional(REQUIRES_NEW)`-style helper
    (`JpaTaskMutationTransactions`, invoked through CDI proxy like `JpaTaskOwnershipTransactions`); creation uniqueness via PK
    insert + flush; commit success only known after the helper returns, so slot `markCommitted` and the finalization observer
    (`TaskFinalizedEvent`, AFTER_SUCCESS) run after commit; outcome-unknown on commit-time exceptions (`RollbackException`
    ambiguity). Immediate-terminal creation must have push config available before the finalization observer fires.
  - Keep `save(...)` legacy path (merge) for non-migrated callers; do not use it for atomic mutations. `list`'s `transformTask`
    (line ~557) ignores `timelineLength`: use `TaskReadProjection.project(task, historyLength, timelineLength)`.
  - Push config store (`JpaDatabasePushNotificationConfigStore`) shares persistence unit `a2a-java`; join-the-transaction
    enrollment is only possible when both stores share it; otherwise T06's independent protocol applies. Tests to add:
    `JpaAtomicTaskMutationTest`, `JpaMaterializationEnrollmentTest` (test persistence.xml registers only JpaTask/JpaTaskOwner
    for the task-store module; the push-config module has its own). Required check command is in plan T08 section.
  - Infra: `/var/run/docker.sock` -> podman answered `_ping` OK at 20:19 UTC; `podman ps` empty. T08 acceptance is H2 + contract;
    real PostgreSQL evidence is T09/T13.

## CHECKPOINT 2026-11-23 16:48 UTC (superseded by the T08 parked checkpoint above; still accurate for T01–T07)

- **Current task:** none active. P3-T07 is finished and committed; the developer instruction was "stop after T07".
- **HEAD:** `3826b0333` `feat(a2a-1.1): materialize ordinary input atomically` (base `a60ea0259`). Local only, NOT pushed.
- **Completed commits in this assignment:** P3-T06 `e229ce160`, P3-T15 `399b6b047`, P3-T07 `3826b0333`.
- **Uncommitted changes:** none (clean tree at checkpoint time, apart from this checkpoint edit to `bob-progress.md` itself).
  `test.log` absent; never create/write/delete/stage it.
- **Review status:** T07 reviews 1 and 2 "Changes required"; review 3 (fresh Bob context, not Sol) "Accepted within scope"; F3/F6/F7/N1
  closed. Non-blocking: R1 (theoretical queue-close invariant), R2 (no concurrent two-request race test; unreachable via handler).
  Record: `reviews/P3-T07-review.md`.
- **Evidence (T07):** required check 159/159, full `server-common` 830/830, `ReplicatedQueueManagerTest` 15/15, all 0 failures/skips;
  candidate hash `069307d6d5ae06e73d995e6484176555772f4f77f74140b48e7313d2e39e74d5`. Logs: `/private/tmp/t07-final/{required,fullmodule,replicated}.log`,
  reviewer logs `/private/tmp/t07-review3/` (probes in `/private/tmp/t07-review3/copy`), earlier `/private/tmp/t07-test-*.log`.
  Docs: `handoffs/P3-T07.md`, `reviews/P3-T07-review.md`, `../task-list.md` (root-level, not under phase-3).
- **Blockers:** none for T08 identified. T08/T09/T13 need real PostgreSQL/Kafka via Testcontainers: Podman socket
  (`/var/run/docker.sock` -> podman) answered `_ping` at ~14:04 UTC but must be re-verified at start; execution must be demonstrated.
- **Active deadline:** none. The T07 backstop (16:08-18:08 UTC, developer-granted) covered T07 only and is spent by task completion.
  Any new task needs a new developer-granted two-hour backstop recorded here before work starts; Bob cannot renew budgets.
- **Exact next action:** await developer assignment/budget for **P3-T08** (JPA transactional authority and finalization enrollment;
  depends on T01-T03, T06, T07, all committed). Then: read `AGENTS.md`, this file, `implementation-plan.md` T08 section, and
  `.claude/architecture/a2a-1.1/task-list.md`; verify clean tree at `3826b0333`; record start/deadline here; implement; fresh Bob review;
  local commit with explicit paths and "This fixes #1303". Do NOT start T08 (or T16) without that instruction. T16 remains held.

## Checkpoint index

| Item | Value |
| --- | --- |
| Branch / worktree | `support/a2a-1.1.0`, `/Users/kabir/.codex-api/worktrees/3053/a2a-java` |
| Last accepted commit at start | `aa78cc26132d3823fc57cbf75af30cbf06093fa1` |
| Committed in this assignment | P3-T06 `e229ce160` (Bob review: accepted, no findings); P3-T15 `399b6b047`; P3-T07 `3826b0333` (Bob review 3: accepted within scope) |

## Active task

| Task | Base SHA | Started (UTC) | Two-hour deadline (UTC) | Status |
| --- | --- | --- | --- | --- |
| P3-T06 | `aa78cc261` | 2026-11-23 11:30 | 2026-11-23 13:30 | committed `e229ce160` |
| P3-T15 | `e229ce160` | 2026-11-23 12:51 | 2026-11-23 14:51 | committed `399b6b047` (Bob review accepted) |
| P3-T16 | `399b6b047` | not started | n/a | NOT STARTED. Deliberately held until the server branch (T07-T14) is worked in dependency order. |
| P3-T07 | `a60ea0259` | 2026-11-23 14:05 (renewed 16:08) | 2026-11-23 18:08 | COMMITTED locally (see git log: `feat(a2a-1.1): materialize ordinary input atomically`). Review 3 (Bob): Accepted within scope. Hash `069307d6d5ae06e73d995e6484176555772f4f77f74140b48e7313d2e39e74d5`. Next ready by dependency: T08 (not started). |

### P3-T08 assignment (developer-directed 2026-11-23: "I think the next task is T08")

Verified: HEAD `3826b0333`, tree clean (except this file), `test.log` absent, no competing process (a developer
`mvn clean install` in this worktree, pid 88808, finished ~16:58 UTC before any edit). Base SHA `3826b0333`.
Start 2026-11-23 17:03 UTC; two-hour backstop deadline 2026-11-23 19:03 UTC. Failed approaches: none yet.
Scope: JPA `AtomicTaskStore` (extras/task-store-database-jpa) per plan T08; checks per plan.

### P3-T07 renewed budget (developer-granted)

The developer explicitly granted a NEW two-hour backstop for P3-T07 (Bob cannot renew budgets itself; this is the developer's
grant). Started 2026-11-23 16:08 UTC, deadline 2026-11-23 18:08 UTC. Build/test commands bounded to min(20 min, remaining) + 30 s.
Candidate hash verified unchanged at start: `069307d6d5ae06e73d995e6484176555772f4f77f74140b48e7313d2e39e74d5`. Scope: third
(correction) review, then local commit if accepted; stop after T07.

## Known blockers / notes

- T09/T13: developer started Podman at ~14:04 UTC. `/var/run/docker.sock` -> podman socket answers `_ping` OK (`podman ps` works; `docker` CLI absent). Testcontainers should work; real PostgreSQL/Kafka execution must still be demonstrated when T09/T13 run.

- T15/T16 are an independent ready branch (after T05) if infrastructure blocks T08+.

## T07 inspection notes (no code edited; restart here)

- `RequestHandler.onSendInput/onSendInputStream` defaults reject artifact updates / guard / timelineLength /
  config.protocolVersion with `UnsupportedOperationError`; `DefaultRequestHandler` does not override them.
- `DefaultRequestHandler.initMessageSend` (≈L1698) for an existing task calls `TaskManager.updateWithMessage`
  (plain `taskStore.save`, no authority/generation) and THEN `pushConfigStore.setInfo` (enrollment after input). T07 must
  replace this with: enrollment (`TaskEnrollmentCoordinator.enrollExistingTask`) -> `TaskMutationAuthority.mutate` using
  `TaskMutationPlanner.planInput(current, PendingTaskInput, MutationOrigin.USER)` with slot reservation on
  `OrderedTaskPublisher` -> publish committed batch; on failure use `Enrollment.partialEffectError`.
  Unary new-task enrollment currently happens AFTER first event (`onMessageSend` ≈L937) and streaming before agent start
  (≈L1109): both must move to materialization via `enrollNewTask` + `taskCommitted/taskNotCommitted/outcomeUnknown`.
- New-task path: bus `MainEventBusProcessor.updateTaskStore` builds `new TaskManager(taskId, ctx, store, null)` (no opening
  message), so the opening input is only carried by `ExecutorSubmissionService.openingTaskInput(RequestContext)` for full-Task
  proposals (`planTaskProposal(null, proposed, openingInput)`). Status/artifact-first executors do not get the pending
  opening message/artifacts yet: T07 must thread `PendingTaskInput` to the bus (per task ID, request-local) and use
  `planInitialStatus/Artifact` with it. `PendingTaskInput.Admission.userArtifactsEnabled` is currently hard-coded false/true in
  several call sites: resolve from effective card.
- No-task direct Message: `MainEventBusProcessor` already tracks `origin.markDirectMessageResponseSelected()` and rejects a late
  initial proposal; still need: discard pending artifacts/config, no enrollment, cleanup with returnImmediately.
- Read projection: only `limitTaskHistory` exists in DefaultRequestHandler (get/send/stream) and InMemoryTaskStore.list;
  `timelineLength` is unimplemented (need independent windows; omitted/zero/finite tests).
- Tests to add: `OrdinaryInputMutationTest`, `DirectMessageMaterializationTest` (ST/requesthandlers).

## Next ready task chosen: P3-T15 (client reducer; independent of server work)
