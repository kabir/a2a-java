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

## Checkpoint index

| Item | Value |
| --- | --- |
| Branch / worktree | `support/a2a-1.1.0`, `/Users/kabir/.codex-api/worktrees/3053/a2a-java` |
| Last accepted commit at start | `aa78cc26132d3823fc57cbf75af30cbf06093fa1` |
| Committed in this assignment | P3-T06 `e229ce160` (Bob review: accepted, no findings) |

## Active task

| Task | Base SHA | Started (UTC) | Two-hour deadline (UTC) | Status |
| --- | --- | --- | --- | --- |
| P3-T06 | `aa78cc261` | 2026-11-23 11:30 | 2026-11-23 13:30 | committed `e229ce160` |
| P3-T15 | `e229ce160` | 2026-11-23 12:51 | 2026-11-23 14:51 | committed `399b6b047` (Bob review accepted) |
| P3-T16 | `399b6b047` | not started | n/a | NOT STARTED. Deliberately held until the server branch (T07-T14) is worked in dependency order. |
| P3-T07 | `a60ea0259` | 2026-11-23 14:05 (renewed 16:08) | 2026-11-23 18:08 | COMMITTED locally (see git log: `feat(a2a-1.1): materialize ordinary input atomically`). Review 3 (Bob): Accepted within scope. Hash `069307d6d5ae06e73d995e6484176555772f4f77f74140b48e7313d2e39e74d5`. Next ready by dependency: T08 (not started). |

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
