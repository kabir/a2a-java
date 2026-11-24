# P3-T08 review — Transactional JPA authority and finalization enrollment

**Reviewer:** Bob review (fresh context; not Sol, not an independent-model review)
**Timestamp:** 2026-11-24T11:03Z (`date -u`: Tue Nov 24 11:02:54 UTC 2026)
**Workspace/branch:** `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, `support/a2a-1.1.0`, base `3826b0333`, uncommitted candidate
**Candidate hash:** `xargs cat < /private/tmp/t08-files.txt | shasum -a 256` = `468999512075aa9cd18af37d6c6ad80ca8ad1f7554523cf9cf31b7182c2b1d84`. **Matches** the expected value. The file list has 11 entries.
**Review constraints kept:** no candidate/production/test file edited; no `git add/stash/checkout/reset`; `test.log` absent (`ls test.log` fails); no commit.

## Disposition: **Changes required**

The atomic skeleton is sound. It locks the row, plans, persists, and calls `markCommitted` only after the proxy returns. It reports unknown outcomes conservatively and keeps protocol generation separate from store revision. The H2 evidence is real.

Four things are open:

1. The concurrent-creation loser is never re-planned, and the test does not exercise that.
2. The counter bootstrap has a plausible self-block on MySQL InnoDB REPEATABLE READ, and no engine test covers it.
3. The T07 timeline-window test cannot fail.
4. Several plan-required failure paths have no test at all.

I did not reproduce the first two. Each has a discriminating check listed below.

## Commands and probes run (this review)

| # | Command / probe | Result |
|---|---|---|
| 1 | `xargs cat < /private/tmp/t08-files.txt \| shasum -a 256`; `git status --short`; `ls test.log` | Hash matches. Candidate is the expected 6 modified plus 5 untracked files (plus the handoff and `bob-progress.md`). `test.log` is absent. |
| 2 | `git diff 3826b0333 -- extras docs`, plus a full read of the 5 untracked files, `AtomicTaskStore`, `InMemoryTaskStore`, `TaskCommitSlot`, `TaskEnrollmentCoordinator`, `TaskMutationAuthority`, `TaskReadProjection`, and the ownership repository/transactions | Static review only. |
| 3 | `grep` of the preserved logs: `t08-run1.log`, `t08-run2.log`, `t08-required.log`, `t08-fullmodule.log` | Counts below. This is a re-check of the log text, **not a re-execution**. |
| 4 | `grep` for production callers of `mutateInTransaction`, `.save(`, `recordOwnership`, `JpaTaskRevisionCounter` registration, and `persistence.xml` files containing `JpaTask` | Results in the findings below. |
| 5 | Environment: Java 17.0.18, Maven 3.9.14, `/var/run/docker.sock` symlinked to a podman machine socket. Whether the podman machine is running was not verified. | No Maven or Testcontainers probe was run in this review (turn limit). |

Counts from the preserved logs:

- **run1:** 14 tests, 1 error + 1 failure. This is a preserved test-side failure.
- **run2:** `JpaAtomicTaskMutationTest` 9/9 and `JpaMaterializationEnrollmentTest` 5/5, total 14/14. `BUILD SUCCESS`.
- **required:** push-config 17 run, 0 failures, 1 skipped. Task store 43 run (`JpaAtomicTaskMutationTest` 9, `JpaDatabaseTaskStoreTest` 23, `JpaMaterializationEnrollmentTest` 5, `JpaTaskOwnershipTest` 6), 0 failures. `BUILD SUCCESS`.
- **fullmodule:** 72 run, 0 failures, 0 errors, 0 skipped. This includes `JpaTaskAuthorizationMySqlTest` (6) and `JpaTaskAuthorizationPostgresTest` (5). `BUILD SUCCESS`. Those two tests exercise the schema on real engines. They **do not** call `mutate`, `readSnapshot` or the counter, so the allocator and locking are not engine-verified.

## Verified as correct (reading only)

- **Slot only after commit.** `JpaDatabaseTaskStore.mutate` (lines 186-212) calls `markCommitted` only after the `REQUIRES_NEW` proxy method returns. The proxy method (`JpaTaskMutationTransactions.commit`, lines 71-75) is a separate CDI bean, so the transaction interceptor has already committed by then.
- **Commit-time failures are not misclassified.** Interceptor or commit exceptions are `RuntimeException`s that are not `TaskMutationNotCommittedException`. They fall to the generic catch and become `TaskMutationOutcomeUnknownException` with the slot cancelled. No rollback is claimed.
- **Errors during reservation.** A `RuntimeException` in the slot callback becomes `NotCommitted` and the reservation is cancelled (`reserve()`, lines 130-144). An `Error` is cancelled and rethrown. The holder is set before the callback runs, so the outer `cancel()` always sees the reservation.
- **Legacy `save()`** is still `merge`, still fires `TaskFinalizedEvent` when `!isReplicated` and final, and is no longer used by `mutate`. The mutation path persists via `em.persist` or a managed-row update on a `PESSIMISTIC_WRITE`-locked row.
- **Revision allocator.** A revision is `(block << 20) + seq`. The counter starts at 0 and the first block is 1. A task takes a new block on creation or exhaustion. `successor()` is correct at the boundary. Uncommitted bumps roll back with the transaction. Delete/recreate always yields a higher block, so no revision is reused.
- **List projection.** `TaskReadProjection.project` replaces the old inline history logic, and `timelineLength` is carried through (`JpaDatabaseTaskStore` lines 498-502 and 637-641). Ownership and index code is untouched.
- **Docs and test persistence.** The new entity is registered in test `persistence.xml`, README and `storage.md`. DDL for the three new columns and the counter table is documented. `writer_epoch` and `migration_marker` are stated to be unenforced. No default outbox is introduced. Independent stores are not marketed as all-or-nothing.

## Findings

### F1 — major — Concurrent-creation loser is never re-planned and gets a generic error

- **Where:** `JpaTaskMutationTransactions.java:85` (`em.find(..., PESSIMISTIC_WRITE)` on an absent row), `:123-126` (`PersistenceException` becomes `TaskMutationNotCommittedException`), `:146-157` (`persist`). The test is `JpaAtomicTaskMutationTest.java:202-216`.
- **Scenario:** Two requests create task X concurrently.
  - A `PESSIMISTIC_WRITE` lock on an absent row locks nothing, so both transactions see `current == null`.
  - Both run the planner and take a slot reservation.
  - The loser fails only at `flush()` with a PK violation.
  - The loser's planner never sees the winner's task. It therefore never produces the typed rejection that `InMemoryTaskStore` produces (the loser is re-planned there against the winner and throws, for example, `TaskGenerationMismatchError` / `TaskNotFound`).
  - `TaskMutationAuthority.rethrowPlannerError` finds no `PlannerRejection` in the cause chain, so the caller gets a bare `TaskMutationNotCommittedException("rejected by storage")` wrapping a Hibernate exception.
- **Why the test does not catch it:** The test's `create()` planner throws `IllegalStateException("exists")`, but the loser never reaches that branch. It passes only on "exactly one winner".
- **Requirement violated:** The plan's "same atomic contract as InMemoryTaskStore" and the A05 failure typing. Also AtomicTaskStore's rule that planner outcomes are reported as typed rejections.
- **Discriminating check:** Race two creators through `TaskMutationAuthority` where the planner throws `TaskGenerationMismatchError` (or an `InvalidParamsError`) if `current != null`. Assert that the loser receives that error and that the planner was invoked with the winner's snapshot. If this fails, the fix is to retry the mutation once in a fresh transaction after a creation-time PK violation. On PostgreSQL the first transaction is aborted and cannot continue.
- **Evidence status:** Static analysis only. Not reproduced.

### F2 — major — Counter bootstrap may self-block on MySQL InnoDB REPEATABLE READ

- **Where:** `JpaTaskRevisionAllocator.java:50-63`. The `bump()` UPDATE runs first in the outer transaction. If it matches 0 rows, `self.ensureCounter()` runs in `REQUIRES_NEW` and INSERTs the row. Any exception is swallowed and then `bump()` is retried.
- **Scenario:** On InnoDB under REPEATABLE READ, an `UPDATE ... WHERE counter_id = 1` that matches no row takes a gap lock on the absent key. The inner INSERT from a second connection (the same thread) then waits on the outer transaction's gap lock, and the outer transaction waits on the inner call. The wait ends at the lock-wait timeout (50s by default). The exception is swallowed as "concurrent initialization", the second `bump()` returns 0, and an `IllegalStateException` surfaces, which becomes `TaskMutationOutcomeUnknownException`. The first creation after a fresh upgrade would then fail on MySQL unless the counter row was seeded. The docs say the row is created "on first use" and do not mention seeding.
- **Why H2 is no evidence:** H2 and PostgreSQL READ COMMITTED take no such lock. `JpaTaskAuthorizationMySqlTest` never calls `mutate`.
- **Requirement violated:** A13 (storage topologies uphold the same invariants) and the plan's review boundary, "real transaction scope ... H2 success is not cluster evidence".
- **Discriminating check:** On a MySQL 8.0 container with an empty `a2a_task_revision_counter`, run `store.mutate(newId, create-plan)` and assert it commits within a few seconds. The alternative fix is to initialize the counter in a separate transaction before the outer UPDATE, or to seed it in the DDL and document the seed.
- **Evidence status:** Reasoned from documented InnoDB gap-lock behaviour. Not reproduced.

### F3 — major — The T07 timeline-window test cannot fail

- **Where:** `JpaAtomicTaskMutationTest.java:241-255`, line 254: `assertTrue(timeline() == null || timeline().isEmpty())`.
- **Scenario:** The saved task has no timeline, so the assertion holds whether or not `timelineLength` is applied. The preserved run1 failure was the null-timeline assertion, and it was fixed by loosening the assertion, not by seeding a timeline.
- **Requirement violated:** "Carry T07's independent history/timeline windows through JPA list/get projections."
- **Discriminating check:** Save a task with at least 3 `TimelineEntry` items and 3 history messages. List with `historyLength(2)` and `timelineLength(1)`. Assert the last 2 messages and the last 1 timeline entry. Then list with `historyLength(1)` and `timelineLength` unset, and assert the timeline is untouched. Revert `transformTask` to the old history-only version and confirm the test goes red.

### F4 — major — Plan-required failure paths have no test

The candidate has 14 tests, all H2, all happy-path or planner-failure. These plan items are untested:

- **Commit-time failure becoming `TaskMutationOutcomeUnknownException` with the slot cancelled.** This is a primary requirement. A direct test needs a fault at commit, for example a `Synchronization` or `XAResource` that throws on `beforeCompletion`. The failed-flush case does not substitute for it.
- **An `Error` thrown from the slot callback.** Assert the slot is cancelled and the `Error` is propagated.
- **`TaskFinalizedEvent` exactly once through `mutate`.** The only finalization tests go through `mutateInTransaction`. The non-shared path with a final task has no observer test.
- **Rollback visibility of an event/config pair on the independent path.**
- **Block exhaustion.** There is no test of `successor` at `SEQUENCE_MASK`, and no test of first-use counter races.

### F5 — minor — Legacy `save()` overwrites `store_revision`, `writer_epoch`, `migration_marker` with NULL

- **Where:** `JpaDatabaseTaskStore.java:140-141` (`JpaTask.createFromTask` followed by `em.merge`).
- **Scenario:** `createFromTask` builds a detached entity with null revision, epoch and marker. `merge` copies those nulls onto an existing row. A legacy `save` after an atomic mutation therefore erases the row's revision (the next read assigns a fresh, higher block revision, which is safe for guards). It also erases any `migration_marker`. This is the unsafe merge-only write path, and `TaskManager.save` (`TaskManager.java:655`) still uses it. It is out of T08 for atomic mutations, but T09 will store the marker there.
- **Discriminating check:** Set `writer_epoch` and `migration_marker` on a row (for example with an UPDATE through a second EntityManager), call `save()`, and read the row. Document or guard the behaviour before T09.

### F6 — minor — Shared-transaction semantics

- **No production caller.** `mutateInTransaction` is used only by tests and `storage.md`. The shared-tx path has no wiring yet.
- **Returned events before commit.** It returns a `CommittedMutation` with events before the caller commits and reserves no slot. A caller that publishes immediately would publish uncommitted events. The javadoc says the caller owns the commit, but the type does not enforce it.
- **Rollback-only on guard failure.** A `RuntimeException` out of `joined` (the `MANDATORY` interceptor) marks the caller's transaction rollback-only, even if the caller catches the exception. A guard rejection therefore always rolls back the config. This is the intended "roll back together" behaviour, but callers cannot catch and continue.
- **Possible stale read under lock.** `apply` uses `em.find(..., PESSIMISTIC_WRITE)`. If the caller's persistence context already holds the managed `JpaTask`, Hibernate may take the lock without refreshing the state, so the planner could see stale state. Discriminating check: in a shared transaction, `em.find` the task, commit a concurrent update from another transaction, then call `mutateInTransaction` and compare the planner's `current` with the database.
- **Snapshot read in a caller transaction.** `readSnapshot` is `REQUIRES_NEW`, so a caller inside a transaction will not see its own uncommitted write through it.

### F7 — minor — Unused import

`JpaDatabaseTaskStore.java:43` still imports `org.a2aproject.sdk.server.tasks.TaskStore` after the class changed to `implements AtomicTaskStore`. `AGENTS.md` requires unused imports to be removed. The compile succeeds in the logs. NullAway warnings were not separately inspected.

### F8 — note — Behaviour to confirm or document

- **Lock-wait failure becomes "not committed".** A `PESSIMISTIC_WRITE` lock timeout (a `PersistenceException`) maps to `TaskMutationNotCommittedException("rejected by storage")` through the same path as constraint violations (lines 123-126). That is safe. It loses the infrastructure/retriable distinction, and `TaskMutationAuthority` treats it as a rejection.
- **Observer failure becomes "unknown".** `taskFinalizedEvent.fire(...)` (line 115) runs synchronous IN_PROGRESS observers inside the transaction. An observer exception is not caught by `apply`. It rolls back the transaction and surfaces as `TaskMutationOutcomeUnknownException` after a known rollback. This is conservative and acceptable.
- **No `isReplicated` guard.** `mutate` fires `TaskFinalizedEvent` unconditionally and has no `isReplicated` parameter. That is correct for local mutations, and the replicated-feedback guard belongs to T10.
- **Pool needs.** `commit` and `read` are `REQUIRES_NEW`, and the counter bootstrap nests another `REQUIRES_NEW`. Each needs an extra connection when called inside an enclosing transaction (up to 3 for first use). A small pool can starve.
- **Counter serialization.** Every task creation holds the single counter row lock from the `bump()` until commit. Creation throughput is serialized database-wide. This is documented as "only locked while a block is allocated" but is not measured.
- **Other persistence units.** Only one `persistence.xml` containing `JpaTask` exists (the module's test one). The `queue-manager-replicated` multi-instance apps, the cloud-deployment example and the bom IT use the module and were not checked for the new entity or DDL.

## Out of scope, not claimed

T09 writer fencing and migration, T10 committed replication, and T13 real-cluster evidence are out of scope. The candidate and handoff say so, and `writer_epoch`/`migration_marker` are described as unenforced. H2 success is not presented as cluster evidence.

## Evidence still needed to move to "Accepted within scope"

1. **F1:** a failing-then-passing creation-race test that asserts the loser's typed planner error, and the fix.
2. **F2:** a MySQL 8 run of `mutate` on a fresh schema (podman availability must be confirmed), or a bootstrap that avoids the in-transaction gap-lock pattern.
3. **F3:** a timeline-window test that goes red on the old projection.
4. **F4:** at minimum the commit-fault and slot-`Error` tests.
5. A fresh run of the plan's required command after those changes. The existing logs were only re-read here, not re-executed.

## Correction re-review — Tue Nov 24 12:02 UTC 2026

**Reviewer:** Bob review (fresh context; not Sol, not an independent-model review).
**Candidate hash:** `xargs cat < /private/tmp/t08-files.txt | shasum -a 256` = `2be3871e77dca06c8b4f1121bfe4cdd11f629a56fe8d99b033eeb25b0a0a9edb`. **Matches.** The file list has 14 entries.
**Constraints kept:** no candidate/production/test file edited, no git add/stash/checkout/reset, no commit, `test.log` absent. I used no extra worktree and `target/` is git-ignored. Probes and logs are under `/private/tmp/t08-review2/`. The temporary MySQL container I started was stopped.

### Disposition: **Changes required**

F1-F8 are closed or downgraded. One new major finding remains (N1). It is a deadlock between the legacy `save()` path and the new `mutate()` path on MySQL. The corrected `save()` takes locks in the opposite order from `mutate()`.

### Commands and probes (this review)

| # | Command / probe | Result |
|---|---|---|
| 1 | Hash check, `git status`, `ls test.log` | Hash matches. `test.log` absent. |
| 2 | Re-read `JpaDatabaseTaskStore`, `JpaTaskMutationTransactions`, `JpaTaskRevisionAllocator`, `JpaTaskRevisionCounter`, `JpaTask` diff, `JpaAtomicTaskMutationTest`, `JpaAtomicTaskMutationDatabaseTest` and the MySQL test | Static review. |
| 3 | Re-check of the preserved logs (not re-execution): `t08-run4.log` 19/19 (13+6). `t08-run6.log` 21/21 (H2 13+6, Postgres 1, MySQL 1). `t08-required2.log` push-config 17 run/1 skip, task store 48/48. `t08-fullmodule2.log` 79 run, 0 fail/err/skip. `t08-run5-db.log` shows the earlier MySQL failure `expected 7 but was 6`. Only `t08-run5-db.log` is the earlier-run artefact I checked for the deadlock text. | Counts agree with the claim. `grep -i deadlock` on `t08-*.log` found no hits in the logs I checked (see Evidence status). |
| 4 | **Re-execution, 3 repetitions.** `mvn -o -q -Dmaven.test.failure.ignore=false -pl extras/task-store-database-jpa test -Dtest='JpaAtomicTaskMutation*Test,JpaMaterializationEnrollmentTest'`, output in `/private/tmp/t08-review2/rep{1,2,3}.log` | Exit 0 for all three runs. The surefire reports from the last run: `JpaAtomicTaskMutationMySqlTest` 1/1, `JpaAtomicTaskMutationPostgresTest` 1/1, `JpaAtomicTaskMutationTest` 13/13, `JpaMaterializationEnrollmentTest` 6/6; 0 failures/errors/skips. 0 "deadlock" lines in all three logs. The report files only show the last run. Runs 1 and 2 are evidenced by exit code only. |
| 5 | Raw JDBC probe on a fresh MySQL 8.0.46 container (`GapProbe`, `/private/tmp/t08-review2/gap-probe.log`): T1 runs the `save()` order (`SELECT ... FOR UPDATE` on an absent id, then counter UPDATE, then INSERT). T2 runs either the same order (`save-save`) or the `mutate()` order (counter UPDATE, then `SELECT FOR UPDATE` on an absent id, then INSERT) (`save-create`). A 1.5 s sleep between steps forces the interleaving. | `save-save`: T1 `Deadlock found when trying to get lock`, T2 OK. `save-create`: T1 `Deadlock found when trying to get lock`, T2 OK. |
| 6 | Unforced storm probe (`Storm`, `Storm2`, `/private/tmp/t08-review2/storm*.log`): 8 threads x 100 distinct new ids on MySQL, no sleep | No lock: 800 ok. `mutate` order: 800 ok, 0 deadlocks. `save` order with no sleep: 800 ok. With a 30 ms pause between the `FOR UPDATE` and the counter UPDATE (`Storm2`): `mutate` order 800 ok, 0 deadlocks; `save` order 764 ok, **36 deadlocks**. |

Probes 5 and 6 are raw JDBC on a plain table pair. They are not the Hibernate/Quarkus stack, but the lock acquisition order matches what the code does (see N1).

### Findings

#### F1 — **Closed** (main claim), residual note
- **Creation no longer locks an absent row first.** `JpaTaskMutationTransactions.apply` (lines 90-96) takes the counter row first with `revisions.newRevision()`, then re-reads the row with `PESSIMISTIC_WRITE`. A losing creator sees the winner's committed row and is rejected by its own planner (`current != null`). The DB test asserts 7 planner rejections out of 8 racers, and passes on H2, PostgreSQL and MySQL.
- **The `mutate` order did not deadlock** in the storm probe, including with a forced pause (probe 6). The `mutate` order is therefore plausible on MySQL.
- **Counter serialization is acceptable within scope.** Every creation (and block exhaustion) holds the single counter row until commit. That serializes creation database-wide and it is not measured. The README and `JpaTaskRevisionCounter` javadoc say the row is locked only on allocation, which is accurate. Note this as a throughput cost for T13. It is not a blocker.
- **Lock timeout or deadlock classification.** A `PersistenceException` from lock timeout or deadlock inside `apply` maps to `TaskMutationNotCommittedException("rejected by storage")` (lines 134-137). That is safe, but it hides a retryable infrastructure failure (carried over from the earlier F8, still true).

#### F2 — **Closed**
- `ensureCounterReady` runs `ensureCounter` in `REQUIRES_NEW` before the caller's transaction touches the counter. Both PostgreSQL and MySQL tests start on a fresh generated schema and initialise the counter on the first creation. That passed in 3 repetitions.
- **Volatile `counterReady` flag.** It is per allocator instance. If the table is truncated or the row deleted while the application runs, `allocateBlock` throws `IllegalStateException("Task revision counter row is missing")`, because `ensureCounterReady` is skipped. For `mutate` this becomes `TaskMutationOutcomeUnknownException`, which is conservative and visible. It does not silently reuse revisions. Minor note: a restart or `ensureCounter` call restores it.
- **First-use race.** The first `ensureCounter` can fail for a concurrent creator with a PK violation. The `catch` retries `ensureCounter`, which only reads the row, so this is handled. The 8-racer test covers this only on the first creation of `first`, which is not concurrent, so a concurrent first-use race is not exercised. Minor.

#### F3 — **Closed**
`listAppliesIndependentHistoryAndTimelineWindows` now stores 3 timeline entries and checks windows `[2,3]`, `0` with history 2, and the omitted window `[1,2,3]`. It can fail on the old projection. I did not revert `transformTask` to prove that.

#### F4 — **Closed with a caveat**
- `commitTimeFailureIsUnknownAndCancelsSlotAndErrorsPropagate` mocks `JpaTaskMutationTransactions.commit` to set the reservation holder and then throw. It asserts `TaskMutationOutcomeUnknownException` with a cause, and that an `Error` propagates. **Caveat:** the test never asserts that the reservation was cancelled, and never asserts that `markCommitted` was not reached. It holds a reservation that nothing inspects after the call. Because the mock throws before `mutate` reaches `markCommitted`, the "never committed" part is true by construction, not discriminating. The exception typing is genuinely tested. Minor: keep the reservation in a local variable and assert `isCancelled()` and `!isPublishable()`.
- `errorFromSlotCallbackPropagatesAndLeavesNoRowAndCancelledSlot` asserts the `AssertionError` propagates, the slot is cancelled, and no row exists. That discriminates.
- `exhaustedRevisionBlockMovesToANewBlockAndStaysMonotonic` forces the sequence to the mask and asserts a new block with sequence 1. That discriminates.
- `finalTaskThroughAtomicMutateNotifiesExactlyOnceAfterCommitAndRollbackNotifiesNever` exists in the enrollment test (6 tests now). I did not read its body in detail.

#### F5 — **Closed**, no regression found
`save()` updates the managed row under `PESSIMISTIC_WRITE` and persists new rows with a fresh revision. The new test `legacySavePreservesRevisionEpochAndMigrationMarker` checks that the revision advances and that the epoch and marker survive. The `isReplicated` branches and the `TaskFinalizedEvent` conditions are textually unchanged. See **N1** for a new problem with the same code.

#### F6 — **Closed within scope**
`mutateInTransaction` refreshes a loaded row under lock (`apply` line 89), and the javadoc says the returned events and snapshot are proposed and must not be published before the caller's commit. Production callers: there are none (only tests, README and `storage.md`). That is acceptable for T08 as wiring is not part of the task, provided the handoff keeps saying so. The rollback-only-on-failure behaviour and the `REQUIRES_NEW` snapshot read stay as documented notes.

#### F7 — **Closed**
`JpaDatabaseTaskStore` no longer imports `TaskStore`. The import list shows `AtomicTaskStore`. The module compiled in all three repetitions.

#### F8 — **Closed**
The cloud example and queue-manager tests that scan packages pick up `JpaTaskRevisionCounter` automatically (the test profile uses `packages` plus `drop-and-create`). I only confirmed this for the module's own test profile. Only `persistence.xml` files that list classes explicitly need the new entity. The only one that lists `JpaTask` is the module's test file, and it now lists the counter and the push-config entity. I did not build the cloud example or the queue-manager modules.

### New findings

#### N1 — **major** — `save()` and `mutate()` take the same locks in opposite order and can deadlock on MySQL
- **Where:** `JpaDatabaseTaskStore.java:145-149`. For a new task, `save()` runs `em.find(..., PESSIMISTIC_WRITE)` on the absent row first, and then `revisions.newRevision()` (the counter UPDATE). `JpaTaskMutationTransactions.java:94-95` does the opposite: counter first, then the absent-row lock.
- **Failure scenario (reproduced in raw JDBC, probe 5):** two concurrent creators of different or equal task ids on MySQL InnoDB, one through `save()`, one through `mutate()` (or two through `save()`). The `save()` transaction holds a gap lock from `SELECT ... FOR UPDATE` on the absent row and waits for the counter row. The other transaction holds the counter row and waits for the gap or insert intention lock. MySQL reports `Deadlock found when trying to get lock` for the `save()` transaction. In the unforced storm probe (probe 6, no pause) there were 0 deadlocks in 800 inserts, and with a 30 ms pause there were 36 deadlocks out of 800 `save` order inserts and 0 for the `mutate` order. So the failure depends on timing and the window is the time between the row lock and the counter UPDATE.
- **Impact:** `save()` is still used by `TaskManager.save` (production path), so a concurrent creator on MySQL can see `TaskPersistenceException` ("Database save failed for task") where before the change `merge` had no such lock order. This is a regression introduced by the F5 correction (before, `merge` took no explicit absent-row lock and no counter UPDATE). The earlier real-MySQL deadlock the candidate hit (`t08-run5-db.log`) is the same pattern, and the fix was applied only on the `mutate` side.
- **Not covered by tests:** all three DB tests call `mutate`, never concurrent `save` and `mutate` on MySQL.
- **Requirement violated:** A13 (same invariants on supported topologies) and "no regression for the legacy path". Also the JPA module's own claim that creation uses a consistent lock order.
- **Suggested fix:** in `save()` for a new row, allocate `revisions.newRevision()` first and only then lock and re-read the row, exactly as `apply` does. Alternatively route `save()` through the same helper.
- **Discriminating verification:** on MySQL 8, a test racing `save(newId)` against `mutate(newId)` and against another `save(newId2)` many times, asserting there is no `TaskPersistenceException`. It must be red on the current order and green after the fix. A Hibernate-level check should replace my raw JDBC probe, which does not run Hibernate.

#### N2 — **minor** — The F4 commit-fault test does not prove cancellation
See F4. The reservation is created inside the mock but never checked afterwards.

#### N3 — **note** — Concurrent first-use of the counter is not exercised
See F2. Only a sequential first creation is covered.

### Evidence status
- **Reproduced:** 3 repetitions of the new mutation tests plus the enrollment test, including PostgreSQL 16 and MySQL 8 containers, exit 0 each.
- **Not re-executed:** the plan's required command and the full module (`t08-required2.log`, `t08-fullmodule2.log`) were only re-read. Only the last repetition's surefire report was inspected for counts.
- **Not verified:** whether `grep -i deadlock` was run on every log; I checked `t08-*.log` and the three repetition logs only.
- **Out of scope, not claimed:** T09 fencing and migration, T10 committed replication, T13 real cluster evidence. H2 and single-node results are not cluster evidence, and the candidate does not claim that.

### Evidence needed for "Accepted within scope"
1. Fix N1 (counter first, then row lock, in `save()`) and add a MySQL test that races `save` and `mutate` creators and fails on the old order.
2. Optionally tighten the F4 test to assert the cancelled reservation.
3. A fresh run of the plan's required command and the full module after the fix.

## Second correction re-review — Tue Nov 24 12:20:02 UTC 2026

**Reviewer:** Bob review (fresh context; not Sol, not an independent-model review).
**Candidate hash:** `xargs cat < /private/tmp/t08-files.txt | shasum -a 256` = `156bfc5c8483ebcaeb990fa8f1d4ae399084664515ad69d9b8a0f22ce900c3b1`. **Matches** the expected value. I checked it again after all runs and it was unchanged.
**Constraints kept:** no candidate, production or test file edited. No commit, `git add`, `stash`, `checkout` or `reset`. `test.log` absent. Probes and logs are only under `/private/tmp/t08-review3/`. The one mutation experiment ran in a throw-away copy of the module (`/private/tmp/t08-review3/copy`), not in the worktree.

### Disposition: **Accepted within scope**

N1 and N2 are closed. The flush-before-refresh change is sound for the paths I could construct. I found no new major finding. Two minor notes are listed below (N4, N5). They do not block acceptance. Out of scope and not claimed: T09 fencing, T10 replication, T13 real-cluster evidence. H2 and single-node results are not cluster evidence.

### Commands and probes (this review)

| # | Command / probe | Exit | Result |
|---|---|---|---|
| 1 | Hash check, `git status --short`, `ls test.log` | 0 / n/a | Hash matches. `test.log` absent. |
| 2 | Static read of `JpaDatabaseTaskStore.save/delete/mutate/mutateInTransaction`, `JpaTaskMutationTransactions`, `JpaTaskRevisionAllocator`, the DB test and the commit-failure test. `grep` for every `PESSIMISTIC`, `LockModeType`, `newRevision` and `successor` use in main code. | n/a | See lock-order analysis. |
| 3 | **3 repetitions**, `mvn -o -Dmaven.test.failure.ignore=false -pl extras/task-store-database-jpa test -Dtest='JpaAtomicTaskMutation*Test,JpaMaterializationEnrollmentTest'` (script `reps.sh`, logs `rep{1,2,3}.log`, surefire copies `surefire{1,2,3}/`) | 0, 0, 0 | Each run: **23 run, 0 failures, 0 errors, 0 skipped**. `JpaAtomicTaskMutationMySqlTest` 2, `JpaAtomicTaskMutationPostgresTest` 2, `JpaAtomicTaskMutationTest` 13, `JpaMaterializationEnrollmentTest` 6. `BUILD SUCCESS`. No "deadlock" text in any of the three logs. The new race test is one of the 2 per engine. Each run does 5 rounds x 8 racers per engine, so 15 rounds per engine over the three runs. |
| 4 | **Full module**, `mvn -o -Dmaven.test.failure.ignore=false -pl extras/task-store-database-jpa test` (`fullmodule.log`) | 0 | **81 run, 0 failures, 0 errors, 0 skipped.** `JpaDatabaseTaskStoreTest` 23/23 (includes `testUpdateExistingTask` and `testIsTaskActiveForFinalTaskWithinGracePeriod`). Both Postgres and MySQL authorization and mutation tests ran. So the DB mutation tests have passed 4 times after the flush fix, with no flake. |
| 5 | **Discriminating old-order experiment** in the throw-away copy. I moved `revisions.newRevision()` in `save()` to run *after* the locking `em.find`, which is the previous order. Command: `mvn -o test -Dtest='JpaAtomicTaskMutationMySqlTest,JpaAtomicTaskMutationPostgresTest'` from the copied module directory (`oldorder.log`). | 1 (expected) | The new race test is **red on both engines**. MySQL: `PessimisticLockException ... Deadlock found when trying to get lock` on `update a2a_task_revision_counter`, thrown from `save()`. PostgreSQL: `duplicate key value violates unique constraint "a2a_tasks_pkey"` from `save()`. With the production order the same test is green (items 3 and 4). So the new test discriminates and the order change is what fixes it. |
| 6 | Preserved logs re-read, not re-executed: `t08-required3-preserved.log`, `t08-required4.log`, `t08-fullmodule4.log` | n/a | `required3`: `JpaDatabaseTaskStoreTest` 23 run, 2 errors, `UnresolvableObjectException: No row with the given identifier exists ... JpaTask with id 'test-task-3'`. `required4`: push-config 17 run/1 skip, task store 48/48. `fullmodule4`: 81 run, 0 failures. All three match the claim. Item 4 above is my own re-execution of the full module. |

### Prior findings

#### N1 — **Closed**
- **Lock order now matches.** For an absent row, `save()` (`JpaDatabaseTaskStore.java:153-156`) and `apply` (`JpaTaskMutationTransactions.java:92-98`) both take the counter row first (`revisions.newRevision()`) and then re-read with `PESSIMISTIC_WRITE`. For an existing row, both flush and then refresh under the row lock (`save` 149-152, `apply` 87-91). Neither locks an absent row before the counter.
- **Counter after row lock** happens only when the stored revision is null (legacy row) or the block is exhausted. That is row, then counter. A creator holds the counter and then touches only its own, different, absent id, so there is no cycle with those paths. On MySQL a gap lock on an absent key does not conflict with another transaction's record lock on an existing row.
- **MySQL, PostgreSQL, H2.** The same ordering argument holds on all three. The real-engine test passes on MySQL 8 and PostgreSQL 16, and H2 runs the same mutation tests.
- **`delete()`** takes no counter lock. It deletes owner rows, then does a plain `find` and `remove` of the task row. There is no foreign key between the tables. It cannot take part in a counter/row cycle.
- **Old raw-JDBC probe.** `/private/tmp/t08-review2/GapProbe` reproduced the *old* `save()` order (locking select on an absent id, then counter UPDATE). Production `save()` no longer does that, so the probe no longer corresponds to production code. I did not re-run it. The Hibernate-level experiment in item 5 replaces it and shows the old order failing and the new order passing.
- **Stability.** 4 full executions of the DB mutation tests passed after the flush fix, which is 20 race rounds per engine.

#### N2 — **Closed**
`JpaAtomicTaskMutationTest.commitTimeFailureIsUnknownAndCancelsSlotAndErrorsPropagate` (lines 311-346) keeps the reservation in `lastReservation` and asserts `slot().isCancelled()` and `!slot().isPublishable()` after a `TaskMutationOutcomeUnknownException`. It passes in the runs above. I did not mutation-test the `cancel(reservation)` call, so I rely on reading that `mutate` cancels in every failure branch (`JpaDatabaseTaskStore.java:222-235`).

#### Flush-before-refresh regression fix — **Assessed, complete and safe for the constructible paths**
- **Cause.** A row persisted in the caller's transaction but not yet flushed has no database row, so `refresh` fails with `UnresolvableObjectException`. `em.flush()` before `em.refresh(..., PESSIMISTIC_WRITE)` removes that. `testUpdateExistingTask` and `testIsTaskActiveForFinalTaskWithinGracePeriod` now pass (23/23 in items 3 and 4).
- **A new task persisted but not flushed, then `find()`.** `find` returns the managed instance from the persistence context, so `existing != null`. The code flushes, then refreshes, which locks the caller's own just-inserted row. This is the case the two tests cover. The revision stays whatever the caller set (null for a bare `em.persist`). `save()` then calls `successor` or `newRevision` after the row lock.
- **Side effects of flushing the caller's context.** `flush()` writes all of the caller's pending changes, not only this task. That is the intent of the comment ("must reach the database first"). A constraint violation on an unrelated pending entity now surfaces at this point. In `save()` it becomes `TaskPersistenceException`. In `apply` it becomes `TaskMutationNotCommittedException("rejected by storage")`, before the planner runs. Both are safe, and the error is attributed to the task call. The flush on a `REQUIRES_NEW` `commit()` path is a no-op because the context is empty.
- **Refresh discards nothing.** The flush runs first, so no pending state of the row is lost by the refresh.
- **Removed-in-context entity.** `find` returns null for an entity removed in the same context and not flushed, so the absent path runs. That is an unusual caller pattern and not exercised. I do not count it.

#### Legacy `save()` and finalization — **Unchanged, verified**
The `TaskFinalizedEvent` block (`JpaDatabaseTaskStore.java:168-178`) is the same: it fires when `!isReplicated` and the state is final, and it only logs when `isReplicated`. `JpaDatabaseTaskStoreTest` passes 23/23 four times, and `JpaMaterializationEnrollmentTest` 6/6 three times plus the full module.

### New findings

#### N4 — minor — The race test can mask a deadlock or lock timeout on the `mutate` side
- **Where:** `JpaAtomicTaskMutationDatabaseTest.java:94-96`.
- **Scenario:** The `mutate` racers swallow *every* `TaskMutationNotCommittedException` as "the planner rejected it". `apply` maps a `PersistenceException` (deadlock victim, lock wait timeout, duplicate key) to the same exception type (`JpaTaskMutationTransactions.java:136-139`). If a `mutate` racer were the deadlock victim, the test would pass. The final check is only `assertNotNull(readSnapshot(id))`, and "lose rows" is not checked beyond that.
- **Why it still caught N1:** the `save()` racers are not wrapped, so a `save()` victim fails the test. Item 5 shows red on both engines. A deadlock whose victim is always a `mutate` racer would not be seen.
- **Discriminating check:** count rejections by whether the planner saw `current != null` (as the first test does with `sawExisting`), and assert that the number of racers that neither succeeded nor were planner-rejected is 0. Also assert exactly one row with the expected revision monotonicity. This is a test-quality note, not a production defect.

#### N5 — note — The counter row lock lasts until commit, so callers with larger transactions must pick a lock order
- **Where:** `JpaTaskRevisionAllocator.newRevision/successor`, used inside the caller's transaction by `save()` and `mutateInTransaction`.
- **Scenario:** In a caller transaction, T1 locks some other row first (an existing task row, or a push-config row) and then creates a task (waits on the counter). T2 has already created a task (holds the counter) and then touches the same other row. That is a deadlock the store cannot prevent. The same applies to a caller that persists a `JpaTask` with `em.persist` directly, outside the store: it holds the insert lock on that row and then waits on the counter, while a creator that holds the counter waits on that row.
- **Production exposure now:** `TaskManager.save` calls `save()` once per transaction, and `mutateInTransaction` has no production caller. The scenario needs a later multi-store caller (T-tasks that wire push-config and task writes together). I did not reproduce it.
- **Recommendation:** when a caller starts sharing the transaction, document or test its lock order: counter-touching task mutation first, other rows after. The docs already say the counter is "only locked while a block is allocated", but the lock is held to commit. This is also a throughput note for T13.

### Evidence status
- **Reproduced by me:** the 3 repetitions, the full module, and the old-order experiment (items 3-5).
- **Not re-executed:** the plan's required command (push-config 17 run/1 pre-existing skip, task store 48/48) was only re-read in `t08-required4.log`. My full-module run (81 run, 0 failures) covers the task-store side of it. I did not re-run the push-config module.
- **Not exercised:** a concurrent first use of the counter (carried from the previous review as a note), and a caller-transaction deadlock (N5).
- **Cluster claims:** none made. No multi-node PostgreSQL or Kafka evidence was produced or claimed.
