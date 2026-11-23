# P3-T07 review record

Reviewer: Bob review in a separate context (not Sol; no independent-model claim). Static review only: Maven was not
re-run and the candidate hash was not recomputed by the reviewer.

Base SHA `a60ea0259`; candidate hash at review (git diff + untracked server-common files, sha256)
`c0e28973586ac9b8d75f72ed03223821875e69c6db687ce3b28038e532648c68`.

## Review 1 verdict: Changes required (original, preserved)

Production wiring reads correctly for: authorize/validate-before-invoke, request-local opening input, single commit of
batch+first output, late-Task rejection after direct Message, existing-task enrollment-then-commit with
`Enrollment.partialEffectError` (never `taskNotCommitted`), `message()` stays non-null.

| # | Severity | Finding |
|---|----------|---------|
| 1 | Major (missing evidence) | `OrdinaryInputMutationTest` concurrent config update runs in `finally` after the throw, so it cannot discriminate a delete/restore during failure handling; commit-failure variant has no concurrent update. |
| 2 | Major (missing evidence) | No test for late Task/status after a direct Message. |
| 3 | Major (missing evidence) | Direct-Message tests assert only store emptiness and config writes/deletes; no publisher/replication spies or queue-cleanup assertions; `pushed` never asserted; `ChildQueue.clearOpeningInput()` unused. |
| 4 | Major (missing evidence) | No test that opening batch + first output is one mutation, the Task/artifact-first/status-first variants, initial-snapshot-once, or legacy blocking history/finality. |
| 5 | Minor | Tautological assertion at `OrdinaryInputMutationTest:87`; swallowed exception at `:232-234`; streaming parity only for guard rejection. |
| 6 | Minor (suspected) | `ExecutorSubmissionService` ~425-466: new-task `opening.enroll()` failure reported INDETERMINATE instead of KNOWN_NOT_COMMITTED. |
| 7 | Minor (suspected) | Post-commit publisher `CompletionException` on existing task escapes `commitExistingTaskInput` unwrapped. |
| 8 | Minor (suspected) | New task + webhook on a non-`AtomicTaskStore` may no longer enroll config. |
| 9 | Minor (suspected) | `markMaterialized()` called when `mutation != null` even if the task appeared concurrently. |

Not verified by reviewer: initial snapshot watermark, unary/streaming equivalence beyond guard rejection, sync timing.

## Correction dispositions

(appended below)

### Correction 1 (hash `c2d4bed0591d0c8cd8397227c81a750c990b23ba3d88dd3c37df4e4d16e4ad43`)

F1 test now injects the concurrent config update inside `mutate()` (after enrollment, before guard/commit) for guard
rejection, commit failure, outcome-unknown and streaming commit failure. F2 late-Task-after-direct test added. F3 queue
cleanup test added; this required a production change (`MainQueue.childClosing` closes a queue whose only invocation ended
in a direct Message with no task; `InMemoryQueueManager.getCleanupCallback` removes it) and removal of unused
`ChildQueue.clearOpeningInput()`. F4 `CountingStore` tests (status-first, artifact-first, unary). F5 assertions fixed.
F6 `opening.enroll()` failure wrapped as `TaskMutationNotCommittedException`. F7 `CompletionException` after commit reported
as partial effect with unknown outcome. F8 (demonstrated regression: JPA/legacy `TaskStore` is not `AtomicTaskStore`, so
new-task webhook enrollment had been removed) legacy path restored and tested. F9 `markMaterialized` only if retained.

## Review 2 verdict (Bob review, separate context): Changes required

F1, F2, F4, F5, F8, F9 closed. F3, F6, F7 partially closed (no test for F6/F7; queue-close change weakly bounded).
Reviewer re-ran the required check: 118 tests, 0 failures, 0 skipped; ReplicatedQueueManagerTest 15 tests, 0 failures;
candidate hash matched. Reviewer did not re-run the full module and ran no mutation probes.

| # | Severity | Finding |
|---|----------|---------|
| N1 | Major (suspected risk, not demonstrated) | `closedForDirectBranch` close/removal could drop a queue if a second concurrent request on the same new task ID materializes a task while `isTaskActive` is still false; only a happy-path test. Asked: bounding tests (concurrent child keeps queue; active existing task keeps queue) or narrowing. |
| N2 | Minor | Unused `hasMessageUpdate`, `OrdinaryInputTestSupport.firstTask`; `IDLE_WORKING` declared after use (legal). |
| N3 | Minor (no defect) | `CompletionException` catch is narrow; pre-commit case cannot occur. |
| F6 | Required | Test: first-Task proposal with failing `setInfo` asserts `KNOWN_NOT_COMMITTED` and an empty store. |
| F7 | Required | Test: publication throws after a successful commit; assert committed, `inputOutcome=unknown`, config kept. |

## Correction 2 dispositions (hash `069307d6d5ae06e73d995e6484176555772f4f77f74140b48e7313d2e39e74d5`)

Added after Review 2 (not yet independently reviewed):
- F6: `OrdinaryInputMutationTest.firstTaskProposalWhoseEnrollmentFailsIsKnownNotCommittedAndLeavesNoTask`.
- F7: `OrdinaryInputMutationTest.publicationFailureAfterAnExistingTaskCommitIsReportedAsAnUnknownPartialEffect`
  (spies `OrderedTaskPublisher.publish` to fail after a real commit).
- Mutation check by the implementer: with the F6 wrapping and the F7 `CompletionException` catch each reverted, exactly these two
  tests fail (`INDETERMINATE` instead of `KNOWN_NOT_COMMITTED`; raw `CompletionException` escapes). Production restored
  byte-for-byte afterwards (`cmp` verified).
- N1: bounding tests in `EventQueueTest` (direct-branch child with no task closes; sibling child keeps queue open; active
  non-final task keeps queue; non-direct child with no task keeps queue). Rationale documented at the close site: client-supplied
  task IDs must name an existing task (`DefaultRequestHandler.authorizeTaskAccess` throws `TaskNotFoundError`) and new IDs are
  server-generated, so a concurrent second request cannot materialize a task under the same ID. No concurrent two-request race
  test was added because that scenario is unreachable through the handler.
- N2: not changed (legal, no effect). N3: no defect.

## Review 3 verdict (Bob review in a separate context; not Sol, no independent-model claim): Accepted within scope

Candidate hash at start and end `069307d6d5ae06e73d995e6484176555772f4f77f74140b48e7313d2e39e74d5` (matches). Reviewer did not edit
the worktree; probes ran in a copy under `/private/tmp/t07-review3/copy`.

Required check re-run by the reviewer: 159 tests, 0 failures/errors/skips (`DefaultRequestHandlerReferenceTaskAuthorizationTest` 7,
`DefaultRequestHandlerTest` 60, `OrdinaryInputMutationTest` 13, `DirectMessageMaterializationTest` 10, `TaskReadProjectionTest` 4,
`RequestContextTest` 26, `EventQueueTest` 39).

Dispositions: **F3 closed**, **F6 closed**, **F7 closed**, **N1 closed**; F1, F2, F4, F5, F8, F9 remain closed.
Mutation probes (copy): removing the `MainQueue.childClosing` direct-branch close fails
`EventQueueTest.directBranchChildClosingWithNoTaskClosesTheMainQueue` and
`DirectMessageMaterializationTest.directBranchClosesItsQueuesEvenWithReturnImmediately`; reverting the F6 wrapping and the F7
`CompletionException` catch fails exactly the two F6/F7 tests. Race analysis: a second request cannot create a task under the same ID
(`authorizeTaskAccess` throws `TaskNotFoundError`; new IDs server-generated); an active task keeps the queue; replicated manager
only re-creates a queue at worst.

| # | Severity | Finding | Blocking |
|---|---|---|---|
| R1 | Minor (suspected, not demonstrated) | Close logic relies on the invariant that no other request can create a task under the ID; a lagging `isTaskActive` on an unusual store/queue manager is theoretical. | No |
| R2 | Minor (limitation) | No concurrent two-request race test (scenario unreachable through the handler). | No |
| R3 | Info | Reviewer did not re-run full module or `ReplicatedQueueManagerTest`; done by the coordinator below. | No |

Reviewer limitation: `getCleanupCallback` check-then-remove is non-atomic; unreachable via the handler.

## Coordinator verification after acceptance (2026-11-23)

Required check: 159 tests, 0 failures/errors/skips (`/private/tmp/t07-final/required.log`). Full `server-common` (via `-am install`):
830 tests, 0 failures/errors/skips (`/private/tmp/t07-final/fullmodule.log`). `ReplicatedQueueManagerTest`: 15 tests, 0 failures
(`/private/tmp/t07-final/replicated.log`). Hash unchanged.

Status: T07 accepted within scope; committed locally.
