# P3-T09 review — migration baseline and enforced writer transition

- Reviewer: independent fresh Bob context (not Sol, not the developer's model).
- Base SHA: `300a005bd`, branch `support/a2a-1.1.0`.
- Candidate hash verification: `xargs shasum -a 256 < /private/tmp/t09-files.txt | diff - /private/tmp/t09-hashes.txt` printed no difference (`HASH_OK`). The candidate did not move during review. `git status --short` shows exactly the 9 modified and 7 untracked files from the file list, plus the one file this review adds.
- Review was cut short by the turn limit (~25 min budget). **I did not run Maven and did not write a probe of my own.** Sections 5 and 8 of the brief are therefore only partly covered; see the LEDGER and the "Not done" list. Everything below comes from source reading plus the candidate's own logs and Surefire XML.

## LEDGER

### Files examined
- Read in full: `JpaTaskMigration.java`, `JpaWriterEpoch.java`, `JpaWriterFencePostgresTest.java`, `JpaWriterFenceDatabaseResource.java`, `JpaWriterFencePostgresProfile.java`, handoff `P3-T09.md`.
- Read as diff or in relevant ranges: `JpaDatabaseTaskStore.java` (save/readSnapshot/delete), `JpaTaskMutationTransactions.java` (all), `JpaTaskRevisionAllocator.java`, `JpaTask.java`, `InMemoryTaskStore.java` (record, save/get/readSnapshot/mutate), `ExecutorSubmissionTest.java` diff (4 hunks, plus context at lines ~700-765 and ~1117-1140), docs diffs (`storage.md`, `replicated-queue-manager.md`, module README, test `persistence.xml`), and the plan section P3-T09.
- Call-site greps: `getOptionalValue` (only `JpaTaskMigration` in the JPA module, using the standard API). `taskStore.save` / `readSnapshot` in server-common main: `TaskManager.saveTask`, `DefaultRequestHandler`, `ExecutorSubmissionService.refreshTask`, `MainEventBusProcessor:496`, `TaskMutationAuthority`.
- NOT read: `JpaTaskMigrationTest`, `InMemoryTaskStoreBaselineTest`, `architecture.md` lines ~976-1020 (that path does not exist under phase-3; I did not locate the file), `acceptance.md`.

### Commands run (all by me)
| Command | Exit | Log |
|---|---|---|
| `xargs shasum … \| diff - /private/tmp/t09-hashes.txt` | 0 (no diff) | stdout only |
| `git status --short` | 0 | stdout only |
| `grep` on `/private/tmp/t09-required1.log` and the candidate's Surefire XML under `extras/task-store-database-jpa/target/surefire-reports/` | 0 | stdout only |

The Surefire XML and `/private/tmp/t09-required1.log` are the **candidate's** artifacts, not a fresh run by me.

- `required1.log` shows `JpaTaskMigrationTest` 10/10, `JpaWriterFencePostgresTest` 2/2, 12 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS.
- The Surefire XML shows `tests="2" errors="0" skipped="0" failures="0"` for the PostgreSQL test and `tests="10" … skipped="0"` for the migration test, so PostgreSQL was not skipped.
- `/private/tmp/t09-fence-transcript.txt` contains 4 lines matching `42501` or `permission denied`.
- I did not independently confirm that the XML files are the ones produced by the logged run, and I did not check the Testcontainers log.

### Probes
None executed (see "Not done").

### Not done (needed for a re-review to close out)
1. A fresh `mvn -B -ntp -o -pl extras/task-store-database-jpa -am -Dmaven.test.failure.ignore=false -Dtest=JpaTaskMigrationTest,JpaWriterFencePostgresTest -Dsurefire.failIfNoSpecifiedTests=false test` run, with logs under `/private/tmp/t09-review1/`.
2. A discriminating probe. Suggested: a PostgreSQL probe where the old role remains **owner** of `a2a_tasks` and the REVOKE is applied, to confirm F1 (the owner can GRANT itself back and write).
3. A second suggested probe: two concurrent first-time `activate` calls on MySQL, to check the gap lock on the absent `a2a_writer_epoch` row (F5).
4. Reading `JpaTaskMigrationTest` and `InMemoryTaskStoreBaselineTest` to confirm they cover the race and idempotence claims.

## What checks out (source plus candidate evidence, not re-run)
- **Fence evidence is real storage enforcement, not mocked.** `JpaWriterFenceDatabaseResource` starts `postgres:16` via Testcontainers. The legacy 6-column schema is created by connecting as role `a2a_old`, so that role is the table owner. The application datasource connects as a distinct role `a2a_new`.
  - `JpaWriterFencePostgresTest` order: ownership transfer to `a2a_new` and explicit grants to `a2a_old`, then upgrade DDL, then a pre-fence successful old INSERT, then the REVOKE plus `pg_terminate_backend`, then JDBC INSERT/UPDATE/DELETE as `a2a_old`. Each is asserted to throw `SQLException` with `getSQLState()=="42501"`.
  - Only after that does it call `activate(2)` and `migrateAll()`. A delayed "reset to generation 0" UPDATE by `a2a_old` after migration is also asserted denied with 42501, and the row is asserted unchanged. The fence-then-migrate order is therefore real.
  - The pre-fence INSERT succeeds, which discriminates against a role that was never able to write.
- **Data preservation.** `JpaTaskMigration.baseline` changes only `generation` (0 to 1) through `Task.builder(task)`. History, artifacts, absent artifact role, metadata and status are untouched, and no timeline is created. The PostgreSQL test asserts history equality, artifact parts equality and `assertNull(role)`.
- **Corrupt detection.** A marked row with generation 0 fails in `baseline`, in `snapshot()` (readSnapshot and mutate) and in `save`. It is never reset. Under mutate it surfaces as `TaskMutationNotCommittedException` caused by `TaskSerializationException`.
- **Marker and epoch live in columns, not in Task JSON.**
- **Legacy mode.** With no `a2a_writer_epoch` row, `checkWritable()` returns null, `stamp` is a no-op, nothing is baselined, and the pre-fence section asserts this.
- **Config.** `a2a.task-store.writer-epoch` is read only through `getOptionalValue`. `DefaultValuesConfigProvider` returns an empty Optional, so there is no throw when it is absent. No defaults entry is needed.
- **CDI.** The `JpaTaskMigration` ↔ `JpaTaskMutationTransactions` cycle and the self-injection are all normal-scoped (`@ApplicationScoped`) beans, the same pattern as `JpaTaskRevisionAllocator`. The candidate's passing Quarkus tests corroborate this.
- **Docs make no claim of** mixed-binary rolling writes, marker-as-fence, or broker enforcement. Rollback is stated as outage plus snapshot plus broker boundary with loss of later changes. In-memory loss on restart and the "process lifetime only" stabilization are stated.

## FINDINGS

### F1 — major — `docs/content/dev/extra/storage.md` (step 3, ~line 140) and the same text in `extras/task-store-database-jpa/README.md`
- **Text:** "make the new identity the owner of, **or give it write privileges on**, all task tables."
- **Scenario:** An operator follows the "give it write privileges" branch and leaves the old role as table owner (the normal state when the old binary created the schema, as in the test fixture). The step-4 `REVOKE … FROM a2a_old` does not fence an owner. In PostgreSQL the owner holds all privileges implicitly and can `GRANT` them back to itself (or recreate or alter the table). A stale or delayed old writer then writes after migration, and the doc's own "verify 42501" step may pass or fail depending on the exact commands.
- **Evidence gap:** `JpaWriterFencePostgresTest` only proves the ownership-transfer branch (`ALTER TABLE … OWNER TO a2a_new` in `TAKE_OWNERSHIP`). The "or give write privileges" branch is documented as equivalent but is untested.
- **Violated requirement:** "A JSON marker old binaries ignore is not an enforcement fence" and "prove a revoked old DB write FAILS AT POSTGRESQL"; the docs must state exactly the procedure that was proven. Docs honesty is also a review-boundary item.
- **Required change:** Remove the "or give it write privileges" branch and require that the old identity does not own the tables (transfer ownership to the new identity or a dedicated owner role distinct from both). Also state that the old role must be unable to `GRANT`, `ALTER` or `DROP`, and must not be a superuser or a member of the owner role. Add `GRANT SELECT … TO a2a_old` if old read access is intended, since `TAKE_OWNERSHIP` does this but the docs omit it.
- **Discriminating verification:** A PostgreSQL probe where `a2a_old` remains owner: apply the doc's REVOKE; then as `a2a_old` run `GRANT INSERT ON a2a_tasks TO a2a_old` and an INSERT, and observe that the write succeeds. Not run by me (PostgreSQL semantics, from knowledge).

### F2 — minor — `JpaTaskMigration.checkWritable` / `storedEpoch`, `JpaTaskMutationTransactions.apply:95`, `JpaDatabaseTaskStore.save:150`, `delete:305`
- **Scenario:** The epoch is read with no lock, so there is no ordering between `activate(n+1)` and an in-flight writer at epoch `n`. In PostgreSQL READ COMMITTED a writer can pass `checkWritable()`, `activate(n+1)` can commit, and the writer then commits its row stamped `n` under the new epoch.
- **Mitigation:** The Javadoc and handoff already disclose this ("the database fence excludes old writers"), and old writers are fenced at the database. This is only a gap in the "persist and check writer epoch" cooperative layer among new nodes.
- **Suggested change (optional):** Take a shared lock on the epoch row (`PESSIMISTIC_READ`) in `checkWritable` within the write transaction, or document it as best-effort among new nodes. Verification: a barrier test where activate(3) commits between a writer's `checkWritable` and its commit.

### F3 — minor/note — `JpaDatabaseTaskStore.get` and `list` bypass baseline and corruption checks
- `get`/`list` return the raw stored Task. Before `readSnapshot`, `mutate` or `migrateAll`, an unmarked legacy row appears as generation 0, and a marked generation-0 corrupt row is served as-is through `get`.
- `InMemoryTaskStore.get` behaves the same way (it can return generation 0 while `readSnapshot` returns generation 1 with a different Task value).
- **Note:** DefaultRequestHandler uses both `get` (lines 622, 763, 1058, …) and `readSnapshot` (759, 1741, 1771, 1838). Within one request a client can therefore see two generations of the same task until `migrateAll` has run.
- **Suggested change:** The docs recommend `migrateAll` but call lazy migration an alternative. They should state that reads through `get`/`list` are not baselined and that `migrateAll` should complete before serving traffic.

### F4 — minor/note — `JpaTaskMutationTransactions.read` and `JpaTaskMigration.baseline` lock order (not proven)
- `baseline()` takes the task row lock first (`refresh(PESSIMISTIC_WRITE)`), then may allocate a revision from the counter row (`revisions.newRevision()` when `store_revision` is NULL, which is every pre-upgrade row; or `successor()` on block exhaustion).
- T08's creators lock the counter first and then the row. This is the reverse order, but a cycle needs a creator to wait on a row that already exists while a baseline holds it. T08's creator only re-locks a row that appeared concurrently, and old writers are fenced, so I found no reachable deadlock.
- **It does serialize `migrateAll` on the counter row under load** (each baseline holds the counter until its transaction commits). `migrateAll` uses one transaction per task, so this is acceptable throughput-wise.
- **Verification (not done):** A MySQL REPEATABLE READ test running `migrateAll` concurrently with creators of new IDs.

### F5 — minor/note — `JpaTaskMigration.install:114`
- `em.find(…, PESSIMISTIC_WRITE)` on an absent `a2a_writer_epoch` row is exactly the pattern T08 avoided for task rows (InnoDB gap lock; two concurrent first-time `activate` calls could deadlock).
- `activate()` retries once on any `RuntimeException`, which rescues one deadlock victim. `activate` is an operator action, so exposure is low.
- **Note:** The retry catch-all also re-runs a second `install` after non-concurrency failures (for example permission denied) and then throws the second exception. This is harmless but loses the first cause.
- **Verification:** Two-thread MySQL `activate` race.

### F6 — minor — legacy-only deployments now need the `a2a_writer_epoch` table and entity mapping
- `read()`, `apply()`, `save()` and `delete()` unconditionally run `SELECT … FROM a2a_writer_epoch`. A legacy-only downstream deployment with an explicit `persistence.xml` that was not updated, or a database without the new table, now fails every read and mutate (the entity is unmapped or the table is missing).
- This is a deliberate trade-off (the docs say to register `JpaWriterEpoch` and create the table), and T08 already introduced a similar requirement for the counter table. It does not match "no epoch => legacy behaviour unchanged" exactly: the table must exist even when the epoch is never activated.
- **Suggested change:** State explicitly in the docs and handoff that `a2a_writer_epoch` must exist (and be empty) for legacy-only operation, and that the upgrade DDL creates it before the new jar is deployed.

### F7 — note — `InMemoryTaskStore` (lines 108-121, 165-169, 182-201, 217-218) and the 4 changed `ExecutorSubmissionTest` expectations
- The `baselined` flag logic is correct. A task saved through `save()` is unbaselined; its first `readSnapshot` takes a `computeIfPresent` write path only if generation is 0. A non-legacy snapshot returns without waiting for a planner, which fixes the earlier regression for non-legacy tasks.
- **Caveat 1:** A legacy generation-0 task's first `readSnapshot` can still block behind a parked planner on that task's `ConcurrentHashMap` bin. It is a one-time cost and acceptable.
- **Caveat 2:** "exactly once" is "once per `save`". `TaskManager.saveTask` (legacy / replicated path) saves again and resets to an unbaselined entry, so every re-save of a generation-0 task is re-baselined on the next snapshot read. In-process revisions also advance on each baseline.
- **ExecutorSubmissionTest changes:** The three `1 -> 2` generation expectations are a legitimate consequence of the required baseline (a generation-0 task saved through `save()` and then mutated through `mutate`). Production initial tasks created through `TaskManager.normalizeInitialTaskAuthority` or `mutate` are already generation 1 and are unaffected.
  - Hunk 2 has an indentation slip (`assertEquals` at column 8 in a deeper block, ~line 405).
  - Hunk 4 changes `assertEquals(externalMutation, store.get(…))` to compare against `externallyCommitted.task()`. This is legitimate because the snapshot read baselined the stored task, but it weakens "preserves the external mutation" to "preserves whatever the snapshot says". A stricter assertion would compare `store.get(id).metadata()` and the baselined generation separately.
- No hidden regression found in `DefaultRequestHandler` or `TaskManager` flows in what I read, but I did not run the server-common suite.

### F8 — note — `JpaDatabaseTaskStore.save:161-167` refuses generation-0 saves over a migrated row
- Under an active epoch, `save()` of a task with `generation()==0` over a row whose generation is >=1 throws `TaskPersistenceException`. This is the intended "refuse lower generation" guard.
- The replicated/legacy flow `TaskManager.saveTask` (isReplicated=true) can still carry generation-0 tasks until T10 commits replication. The docs correctly describe this as a last-resort guard that is not broker fencing, so it is out of scope for T09, but T10 must account for it.

### F9 — note — not verified because of my truncated run
- (a) `JpaTaskMigrationTest` race coverage (baseline vs concurrent mutate and save) and (b) `InMemoryTaskStoreBaselineTest` cover the claims in the handoff; I did not read them.
- (c) The candidate's `t09-fullmodule1.log` (93 tests, 0 failures) and `t09-server-common-full2.log` (834/834) are taken as given.

## DISPOSITION

**Changes required**

- F1 (major, docs) must be fixed: remove the unproven "or give it write privileges" branch and require that the old identity does not own the task tables, as the test proves.
- F6 should be acknowledged in the docs and handoff.
- F2-F5 and F7-F9 are minor notes and can be accepted with the doc clarifications listed above.
- A re-review should run the required Maven command, write the F1 owner-retains-GRANT probe, and read `JpaTaskMigrationTest` and `InMemoryTaskStoreBaselineTest` (see "Not done").
- Beyond F1, nothing I read contradicts the candidate's own evidence: PostgreSQL itself denies INSERT/UPDATE/DELETE with SQLSTATE 42501 before migration and for a delayed generation-0 reset, using distinct DB identities and the real fence-then-migrate order.

## Correction re-review (fresh Bob context)

- Reviewer: fresh Bob context. This is not Sol and not an independent-model review. Time-boxed to 10 minutes.
- Method: source reading plus the candidate's logs. I did not run Maven and did not write a probe of my own.
- Candidate hash check: `xargs shasum -a 256 < /private/tmp/t09-files.txt | diff - /private/tmp/t09-hashes.txt` printed no difference (`HASH_OK`).
- Evidence read, not re-run: `/private/tmp/t09-run3.log` has JpaTaskMigrationTest 10/10 and JpaWriterFencePostgresTest 3/3 (13 run, 0 skipped). `/private/tmp/t09-exec-rerun.log` has ExecutorSubmissionTest 30 and InMemoryTaskStoreBaselineTest 4 (34 run, 0 failures). `/private/tmp/t09-fence-transcript.txt` has the owner-probe lines.

### Disposition: **Accepted within scope**

This is conditional on the doc-only fix N1 below, which is a wrong test name in the docs and is not a blocker for the T09 claims. The storage-enforced PostgreSQL denial, the baseline, and the corruption/epoch refusals are demonstrated. No mixed-binary rolling claim, broker enforcement claim or invented timeline was found.

### Per-finding status

| Finding | Status | Basis |
|---|---|---|
| F1 (major, docs/fence) | **Closed**, with N1 and N2 below | The "or give write privileges" branch is gone from `docs/content/dev/extra/storage.md:128-136` and `extras/task-store-database-jpa/README.md:~165-172`. Both now require ownership transfer, say the old identity must not own the tables, be superuser or be a member of the owner role, state that granting the new identity privileges while the old identity stays owner is "not a supported fence", and add `GRANT SELECT ... TO a2a_old`. The new test is real (see below). |
| F2 (epoch check unlocked) | **Closed as documented** | `storage.md:156-158` and the `activate` Javadoc state the check is best-effort and the DB fence is the exclusion. No code change; the residual gap among new nodes is disclosed. |
| F3 (`get`/`list` do not baseline) | **Closed as documented** | `storage.md:153-155` (step 6) says to finish `migrateAll()` before traffic and that `get`/`list` can show generation 0. Contradiction N3 below. |
| F4 (lock order) | **Retained as note** | Not changed and not probed; no deadlock found. |
| F5 (activate retry, gap lock) | **Partly closed** | `JpaTaskMigration.java:110-113` now keeps the first exception as suppressed. The MySQL concurrent-activate probe was not run, so the gap-lock exposure is retained as a low note. |
| F6 (epoch table must exist in legacy mode) | **Closed** | Stated in `storage.md:135-136` and the README. |
| F7 (ExecutorSubmissionTest) | **Partly closed** | The indentation slip at ~line 405 is fixed (delta 1452). The hunk-4 weakening is retained as a note (N4). |
| F8 (generation-0 replicated saves) | **Retained, out of scope** | T10 must handle it. It is recorded in the handoff and in `replicated-queue-manager.md` as a last-resort guard that is not broker fencing. |
| F9 (tests unread) | **Closed** | `JpaTaskMigrationTest` and `InMemoryTaskStoreBaselineTest` were read. See "Test discrimination". |

### F1: does the new test demonstrate it?

`JpaWriterFencePostgresTest.revokeDoesNotFenceAnOwnerWhoCanGrantItselfBack` (lines 260-280) does demonstrate the claim.

- `a2a_old` creates a scratch table `t09_owner_probe`, so it is the owner. The admin runs `REVOKE ALL ... FROM a2a_old`.
- An INSERT by `a2a_old` is asserted denied with `42501`. This shows the REVOKE took effect.
- `a2a_old` then runs `GRANT INSERT ... TO a2a_old` and a successful INSERT. Any failure of GRANT or INSERT would throw and fail the test.
- The transcript at `/private/tmp/t09-fence-transcript.txt:58-60` shows this sequence.
- The probe is `@Order(3)` and uses a separate scratch table. The earlier fence assertions in `oldIdentityIsDeniedByPostgresBeforeMigrationAndCannotRewriteMigratedData` (the delta shows only an addition) and the order-2 test are unchanged, so nothing is weakened.
- The prior review's order-1 evidence still holds: ownership of `a2a_tasks` and `a2a_task_owners` is transferred to `a2a_new`, and the REVOKE, the 42501 denials and the delayed generation-0 reset denial are asserted before and after `activate(2)` and `migrateAll()`.

### Test discrimination

- `JpaTaskMigrationTest` discriminates against removal of each behaviour:
  - Baseline: `legacyRowIsBaselinedOnce...`, `baselineIsAtomicWithTheFirstMutation` and `migrateAll...` assert generation 1, the marker, and unchanged history, parts and absent role.
  - Corruption: `migratedRowMissingGenerationIsCorrupt...` asserts `TaskSerializationException` on `readSnapshot`, `TaskMutationNotCommittedException` with that cause on `mutate`, an exception on `save`, and an unchanged JSON. Removing the check would fail it.
  - Epoch refusal: `staleNodeEpochCannotWriteOrDelete` covers mutate, save, delete and a baselining read, with an unchanged row and marker. The saved generation (9) is higher than the stored one (1), so the refusal is attributable to the epoch.
  - Legacy mode, monotonic epoch and the lower-generation guard are also asserted.
- `InMemoryTaskStoreBaselineTest` discriminates on baseline-once and stability, baseline before the first mutation plans, positive generation kept, and no re-baseline for atomic creates. It does not cover concurrency or re-save re-baselining.
- Gap, unchanged from F9(a): there is no concurrent baseline-vs-mutate/save test. The race claim rests on the row lock plus source reading.

### New findings

- **N1 (minor, doc, fix before merge)**: `docs/content/dev/extra/storage.md:132` and `extras/task-store-database-jpa/README.md:169` cite `JpaWriterFencePostgresTest.ownerCanGrantItselfBackAfterRevoke`. That method does not exist; the real name is `revokeDoesNotFenceAnOwnerWhoCanGrantItselfBack` (test line 262). Scenario: a reader or a doc-vs-test audit cannot find the cited evidence. Change: correct the name in both files.
- **N2 (minor/note, doc)**: the fence statement covers only revocation from the named role and ownership. It does not mention privileges inherited through `PUBLIC` or through membership in another role (the doc says "must not be a member of the owner role" but not other privileged roles), nor default privileges. Scenario: `a2a_old` still writes through a `PUBLIC` or group-role grant after the REVOKE. The doc's own "must fail with 42501" verification step would catch it, so the exposure is limited. Not examined by a probe.
- **N3 (minor/note, doc)**: `storage.md:149-150` step 5 still says `migrateAll()` "can also be left to lazy migration", while step 6 requires completing it before traffic. Change: drop the lazy-migration alternative from step 5 or mark it as non-recommended.
- **N4 (note)**: `ExecutorSubmissionTest` hunk 4 now compares `store.get(...)` against `externallyCommitted.task()` instead of `externalMutation`. This is consistent with the baseline, but it is weaker than asserting the external mutation's content separately. It is not blocking.

### Suppressed-exception change and whitespace fix

- `activate` (`JpaTaskMigration.java:101-114`): behaviour is the same except that the second failure now carries the first as suppressed. The `IllegalStateException` fast-path (a lower epoch) is unchanged. I found no new risk; `addSuppressed` can only throw if the two exceptions are the same object, which they cannot be here.
- The ExecutorSubmissionTest indentation fix is whitespace only.

### Docs overclaim check

- `storage.md` and `replicated-queue-manager.md` say "coordinated drain-and-upgrade, not a rolling upgrade". Mixed old/new writers are unsupported.
- A marker is explicitly "not a fence", and the epoch is "cooperative among new binaries only".
- Broker fencing is a deployment precondition, "not enforced here". `JpaDatabaseTaskStore.save` refusing lower generations is called a last-resort guard that is not broker fencing.
- No delivery guarantee is claimed. Rollback is described as outage, consistent snapshot and broker boundary, with loss of later changes. No timeline is invented.
- The corrupt-row rule (a marked row with no generation is never reset) is documented and tested.
- I found no overclaim.

### Not examined

- No fresh Maven run, and no probe of my own. This includes the F1 probe and a MySQL concurrent-`activate` race.
- No concurrent baseline-vs-writer test (F9a), and the F4 lock-order interaction with T08 creators.
- Full server-common (834) and task-store module (93) suites were run only on the pre-correction candidate per the handoff, and I did not re-verify them. The corrections touched docs, test code, whitespace and the `activate` catch block only.
- PUBLIC and default-privilege grants on PostgreSQL (N2), and databases other than PostgreSQL for the fence.
- `architecture.md` and `acceptance.md`.

### Dispositions of correction re-review findings (coordinator, 14:40 UTC)
N1 corrected (docs now cite `revokeDoesNotFenceAnOwnerWhoCanGrantItselfBack`). N3 corrected (step 5 no longer offers lazy migration as an alternative).
N2 (PUBLIC/inherited privileges) and N4 (weaker assertion) retained as non-blocking notes; the operator step-4 denial check detects inherited grants. Doc-only edits after
the reviewer's hash; no code changed.
