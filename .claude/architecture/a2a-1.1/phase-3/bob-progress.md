## CHECKPOINT 2026-11-25 17:05 UTC: P3-T12 COMMITTED; read this first

- Commit follows base 44c276a17; see git log -1. Window 16:21-18:21 UTC (closed early). Candidate v6: 29 files (`/private/tmp/t12-files.txt`), hash-list sha256 `ca8bdb0a6534cd49...`, patch sha256 `a52a870b2796633d...`.
- Reviews (fresh Bob contexts, not Sol): E (docs) Changes required, F (wiring) Incomplete evidence, G (logic) Changes required small; all actionable findings fixed in v6; H returned only a header; H2 correction review on the v6 delta **Accepted within scope**. Ledger `reviews/P3-T12-review.md`, handoff `handoffs/P3-T12.md`.
- Evidence: `/private/tmp/t12-v6-full-clean.log` clean-worktree reactor exit 0, 4194 tests, 0 failures/errors, 70 skipped (H2/JPA only, no PostgreSQL/Kafka). v5 `t12-v5-full-clean.log` also exit 0.
- Retained: no `@QuarkusTest` (producer proxy `instanceof`, gRPC wiring, startup observers, card route on Vert.x); A6-4 no-batch pushes use the node push sender directly (T10/T11 behaviour, documented); cards without a registration ungated; epoch read repeats per failing request; pass cached per object; declaration-based checks; broker fencing a documented precondition only.
- A passing T12 check is NOT proof that a cluster profile passed T13. T13 stays blocked on real PostgreSQL/Kafka/two-node infrastructure. Ready by dependency, not started: T14, T16. Next action for the developer: assign one of them (or T13 once infrastructure exists) with a fresh backstop.

## P3-T12 RESUMED 2026-11-25 16:21 UTC (developer: "let's make these changes and wrap this T12 task up"; treated as a fresh two-hour backstop)

Start 16:21 UTC (`date -u` 16:21:10); deadline 18:21 UTC. HEAD `44c276a17` (verified). No Maven running. Plan: fix R1-F3 (log gate exception in REST card routes) and A2-3 (skip JPA scan when node epoch mismatches), narrow fresh reviewers E/F/G (own files), clean-worktree re-run, commit.

## CHECKPOINT 2026-11-25 16:25 UTC: P3-T12 PARKED, implementation and tests complete, review gate NOT closed, NOT committed

- Commit follows base 44c276a17; see git log -1 (no T12 commit exists yet: HEAD is `44c276a17`). Window 14:41-16:41 UTC; parked before the deadline so the unclosed gate is not hidden.
- Candidate v4: 28 files (`/private/tmp/t12-files.txt`), hash list sha256 `f84a051ad51b...`, patch `/private/tmp/t12-candidate.patch` sha256 `e4fde6850e6e...`. Handoff `handoffs/P3-T12.md`; ledger `reviews/P3-T12-review.md`; reviews `-A`, `-A2`, `-B2`, `-C`, `-D` (`-B` is an empty header).
- Evidence: v3 reactor `t12-v3-full-clean.log` exit 0, 4193 tests, 0 failures/errors, 70 skipped (clean git worktree copy); v4 delta only added a test and docs, reference-common selectors green (`t12-v4-card.log`: 25 + 11 + 10).
- Corrections done in this window: gRPC no-registration 1.1 gate (B2 F1), REST/JSON-RPC card gates (B2 F2), card binding/tenant test (C R1-F4), docs (profile table, per-object cache, plain-Java object reuse, AgentCardRouter limit, failure surfacing).
- Why not committed: every fresh-context review (A, A2, B2, C, D) ended Incomplete evidence; none returned "Accepted within scope". Required next: one narrow fresh review on v4 that finishes the docs claims (c), (e), (i) and the open items below, then commit.
- Open: R1-F3 (REST card gate exception not logged by the route), R1-F5/B2 F6 (no `@QuarkusTest` for gRPC wiring, startup observers, ArC producer proxy `instanceof`), B2 F4, A2-3 (JPA scan per failed 1.1 request), A6-4 (reasoned only), L1 (two retained public overloads skip the gate), F3 per-request failure is a generic 500 to the client.
- Environment: building the LIVE worktree with `-DargLine=-javaagent...` breaks `compat-0.3/server-conversion` (21/27 "Unresolved compilation problem" in MapStruct impls); clean git worktrees pass. Build in a clean worktree or stop the IDE/Serena JDT servers.
- Exact next action for the developer: new short assignment: (1) spawn one reviewer per question group (docs claims c/e/i; R1-F3 + gRPC/card wiring) each writing its own file immediately; (2) fix findings (consider logging in the REST card routes and a `@QuarkusTest`); (3) re-run the affected modules in a clean worktree; (4) stage the 28 files in `/private/tmp/t12-files.txt` plus the handoff, reviews, task-list and this file by name, never `test.log`; commit `feat(a2a-1.1): validate task authority topology before serving 1.1` ending `This fixes #1303`. T13 stays blocked on T12 plus real PostgreSQL/Kafka/two-node infrastructure; T14 and T16 are ready by dependency but must not start in this task.

## P3-T12 RESUMED 2026-11-25 14:41 UTC (developer: "We need to complete the task"; treated as a fresh two-hour backstop)

Start 14:41 UTC (`date -u` 14:40:52); deadline 16:41 UTC. HEAD `44c276a17` (verified). No Maven running. Candidate v2 verified byte-identical to the freeze (all 28 hashes in `t12-hashes.txt` match; sha256 of the list `718061416c3314dd...`). The v2 test evidence (`t12-v2-full-clean.log` etc.) therefore stands; no rebuild until a correction changes code. Plan: fresh-context reviewers A2 and B (own files), correction, delta review, ledger/handoff/task-list, commit.

## CHECKPOINT 2026-11-25 14:40 UTC: P3-T12 PARKED again, tests green, review gate NOT done (NOT committed)

- HEAD `44c276a17`. Candidate v2 is uncommitted: 28 non-.claude files (`/private/tmp/t12-files.txt`, hashes sha256 `718061416c3314dd...3cb8` in `t12-hashes.txt`, patch `t12-candidate.patch` sha256 `ea9dceacab50ed38...3e16`). Untracked/staged state restored (`git reset`); `test.log` never touched.
- Evidence on v2 (all in clean copy `/private/tmp/t12-cand-wt`, byte-identical file set to the tree; see below): `t12-v2-required.log` exit 0 (server-common `TaskAuthorityConfigurationTest` 25, reference-common `TaskAuthorityConfigurationTest` 9, `VersionRouterTest` 10); `t12-v2-extras-selected.log` exit 0 (`JpaTaskMigrationTest` 14, `ReplicatedQueueManagerTest` 18, `ReactiveMessagingReplicationStrategyTest` 7); `t12-v2-full-clean.log` exit 0: `-am` reactor of server-common, JPA, replicated core/reactive, reference common/rest/jsonrpc/grpc/multiversion-rest/multiversion-jsonrpc, 45 modules, 4191 tests, 0 failures/errors, 70 skipped (skips were not itemised; Docker-conditional tests are not acceptance evidence).
- PRE-EXISTING/ENVIRONMENTAL, not a T12 defect but unresolved: in the LIVE worktree `compat-0.3/server-conversion` fails (21 of 27: "Unresolved compilation problem: FileContentMapper_v0_3 cannot be resolved", MapStruct `*Impl` classes compiled by an Eclipse compiler) with `-DargLine=-javaagent...`, on every run (`t12-v2-full-preserved.log`, `t12-v2-compat-conversion-agent.log`). Same tree content copied to a clean git worktree passes 27/27 (`t12-cand-wt-compat.log`), and base `44c276a17` in a clean worktree passes (`t12-base-compat-conversion-agent.log`). Cause is almost certainly a JDT/Eclipse language server (Serena/redhat.java, running against the live tree) writing `target/classes`; I did not prove it. Do not treat the live-tree compat failure as a T12 regression, and stop those IDE servers or build in a clean worktree. No compat-0.3 file is changed by T12.
- Tests added since review A all passed: 3 server-common live-composition tests, JPA live shared-gate composition, replicated-core live topology, reactive `supportsCommittedReplication`. Docs gained a "Limits of the check" paragraph (A1-1, A1-3, A3-3, A2-3); `TaskAuthorityConfiguration` Javadoc states the truthful-declaration reliance.
- NOT done: fresh Bob reviewers on v2 (review A was Incomplete evidence on v1, B returned only a header, so wiring/docs and the final delta have had NO independent review), correction round, `reviews/P3-T12-review.md`, `handoffs/P3-T12.md`, task-list row, commit. Deadline 14:45 left no time for a 15-20 minute review.
- Still open from review A: A2-3 (failed check re-runs the JPA scan per 1.1 request; only documented, not changed), A6-4 (reasoned from code only: atomic stores set `committedBatch` whenever events changed; the null path covers unchanged/error/legacy cases; unchanged T10/T11 behaviour).
- Exact next action: new assignment with a new backstop. (1) Freeze v2 (or the tree as found), spawn narrow numbered fresh-context reviewers A2 (logic/tests incl. A6-4, A2-3) and B (wiring: DefaultProducers, VersionRouter, routes, gRPC startup observer; docs claims), each into its own `reviews/P3-T12-review-<X>.md`; (2) correct and run a delta correction review; (3) write the ledger, handoff, task-list row and commit with `This fixes #1303`, line "commit follows base 44c276a17; see git log -1". Build in a clean worktree. T13 stays blocked on T12 plus real PostgreSQL/Kafka/two-node infrastructure.

## P3-T12 RESUMED 2026-11-25 12:45 UTC (developer: "I ran the tests manually and they pass. Please configure the task"; treated as a fresh two-hour backstop)

Start 12:45 UTC (`date -u` 12:45:45); deadline 14:45 UTC. HEAD `44c276a17` (verified). The developer's own Maven run (started 12:41) was still active at start; no second reactor is started until it ends. The developer's manual test results are NOT recorded as evidence here until the logs are seen (path unknown); the implementer re-runs the affected modules once on the frozen candidate. `TaskAuthorityConfigurationTest` (server-common) was modified externally after the parking checkpoint; the frozen candidate is taken from the tree as found.

## CHECKPOINT 2026-11-25 12:33 UTC: P3-T12 PARKED at the two-hour backstop (NOT committed, NOT complete)

- HEAD is still base `44c276a17`; working tree holds the uncommitted T12 candidate (about 30 files). `test.log` was never touched.
- Implemented (see the design entry below): `TaskAuthorityCapabilities`/`TaskAuthorityConfiguration`, additive store/queue/strategy/dispatcher/processor/handler declarations, JPA capability read, Quarkus producer and `VersionRouter` wiring in rest, jsonrpc, grpc, multiversion-rest and multiversion-jsonrpc, tests and docs (storage.md, replicated-queue-manager.md, server.md).
- Evidence that passed on candidate v1 (before the post-review edits): `t12-required1.log` (server-common `TaskAuthorityConfigurationTest` 22, reference-common `TaskAuthorityConfigurationTest` 9, `VersionRouterTest` 10), `t12-extras2.log` (JpaTaskMigrationTest 13, ReplicatedQueueManagerTest 17). After the review-A edits only `t12-compile2.log` passed: the install with `-DskipTests` of all reference modules, JPA, replicated core and reactive, with `-am`, exit 0. It proves compilation only.
- Review A (`reviews/P3-T12-review-A.md`): Incomplete evidence, no blockers. Review B (`-B.md`) returned only a header; wiring and docs have NOT been independently reviewed.
- Edits made AFTER review A and NOT yet run: (1) server-common `TaskAuthorityConfigurationTest`: three live-composition tests (`liveSharedStoreWithTheFourArgumentBusProcessorIsRejected`, `...SharedGateDispatcherAndQuiescentSenderPasses`, `...ProcessLocalGateDispatcherIsRejected`) and a `DelegatingAtomicStore` constructor that takes declared capabilities; (2) `JpaTaskMigrationTest.liveSharedJpaCompositionPassesOnlyWithTheSharedGateDispatcher` (real JPA store, `JpaWebhookDispatchGate`, real processor); (3) `ReplicatedQueueManagerTest.liveReplicatedSharedProfileIsValidatedThroughTheTopologyGate`; (4) `ReactiveMessagingReplicationStrategyTest.testDeclaresCommittedReplicationSupport`; (5) indentation fix in the two multiversion routes. Expected message fragments ("push", "gate", "dispatcher") are unverified and may need adjusting to the real violation text.
- A6-4 analysis (done by reading, not tested): for an atomic store `updateTaskStore` always produces `committedBatch != null` whenever a mutation changed events. The `committedBatch == null` direct push at MainEventBusProcessor ~436 is reached for unchanged mutations, errors with no change, and legacy-only stores. Whether an unchanged atomic mutation should push is T10/T11 behaviour and is NOT changed here. Record this in the handoff as a retained note; do not assert it is proven.
- Not done: running the full affected modules (server-common, JPA, replicated core, reactive, reference/common, reference/* wiring tests) on a frozen candidate; fresh re-review (A2/B/C) of the final delta; `reviews/P3-T12-review.md`, `handoffs/P3-T12.md`, task-list row; the commit.
- Retained from review A: A1-1 (custom QueueManager that does not report `replication()` evades the gate; opt-in declaration), A2-3 (a failing check re-runs the JPA scan on every 1.1 request; consider skipping `unmigratedIds` when the node epoch already mismatches), A3-3 (startup observer vs migration ordering untested; docs should say migrate before starting the node), A1-3 (gate/store same-datasource coupling is a deployment precondition). Broker fencing remains a documented precondition only; T12 passing is not T13 evidence.
- Exact next action for the developer: start a fresh T12 assignment with a new backstop. Order: (1) run `mvn -o -B -ntp ... -am -pl server-common,extras/task-store-database-jpa,extras/queue-manager-replicated/core,extras/queue-manager-replicated/replication-mp-reactive,reference/common,reference/rest,reference/jsonrpc,reference/grpc,reference/multiversion-rest,reference/multiversion-jsonrpc` with the Byte Buddy agent and fix any failure in the new tests; (2) refreeze, and run fresh reviewers A2 (logic/tests incl. A6-4 and A2-3) and B (wiring/docs, narrow numbered questions); (3) correct, then write the review ledger, handoff and task-list row, and commit with `This fixes #1303`. T13 stays blocked on T12 plus real PostgreSQL/Kafka/two-node infrastructure.

## P3-T12 STARTED 2026-11-25 10:36 UTC (developer assignment; base `44c276a17`, verified HEAD)

Start 10:35 UTC (`date -u` 10:35:23); two-hour backstop deadline 12:35 UTC. Tree clean except untracked `test.log` (not ours, never touched). No competing Maven/java processes.
Failed approaches: none yet. Scope: authority topology/provisioning gate per plan P3-T12 (configuration validation only; T13 cluster evidence out of scope).

Design (11:10 UTC): additive `AtomicTaskStore.authorityCapabilities()` (default UNDECLARED; InMemory=PROCESS_LOCAL; JPA=SHARED_STORAGE read from `JpaTaskMigration`),
`QueueManager.replication()` (default none; ReplicatedQueueManager reports epoch + committed-envelope support via new `ReplicationStrategy.supportsCommittedReplication()`),
`TaskAuthorityConfiguration` (server-common; pure violations() + cached requireVersion11()), Quarkus wiring through `DefaultProducers` + `VersionRouter` overloads
(startup `validateDefaultRegistration`, per-request `registration`/`attachRegistration`, gRPC handler), plain-Java via `DefaultRequestHandler.taskAuthorityTopology()` +
`TaskAuthorityConfiguration.validating(resolver)`. Only registrations (or registration-less requests) that select 1.1 are checked; legacy-only registrations are untouched.


# Phase 3 Bob-coordinated progress record

## P3-T10 RESUMED 2026-11-24 16:43 UTC (developer: "We should complete T10"; treated as a fresh two-hour backstop)

Start 16:43 UTC (first date -u 16:43:43); deadline 18:43 UTC. Same candidate v2 (hash list sha256 `6888f349...201`), HEAD `bcc3bdef2`. Plan: narrow parallel read-only fresh-context reviewers, each writing to its own file `reviews/P3-T10-review-<X>.md`; merge dispositions into the ledger; commit only after acceptance.

## CHECKPOINT 2026-11-24 17:46 UTC — P3-T10 COMMITTED; read this first

- **P3-T10 finished** in its resumed window (16:43–18:43 UTC); commit `3943f313d` on base `bcc3bdef2`. Reviews (fresh Bob contexts, reading only, not Sol):
  Review 1 Incomplete evidence; A and B Changes required (A2-1 pill/terminal leak, A3-1/A3-2 sequencer wedge, A1 test gap, B1-1 finality predicates: all fixed in v3);
  C correction review Accepted within scope. Record `reviews/P3-T10-review.md` (+ `-A`, `-B`, `-C`).
- Evidence: server-common 834/834 (`/private/tmp/t10-v3-server-common-full.log`), JPA 95/95 + core 48 + reactive 6 (`t10-v3-extras-full.log`), core 50/50 after
  test-only additions (`t10-v3b-core.log`), mutation probe `t10-mutation-webhook3.log`. v3 hash-list sha256 `03b42ad2e25b735257a361af63b5a98cd2331018f789759e1a90d5cb53d0a9d3`. Kafka single-instance
  `testA2AMessageReplicatedToKafka:175` fails identically on base and candidate (pre-existing).
- Retained notes: `UNSPECIFIED`-final edge, LRU eviction bound, third-party strategies without `send(ReplicatedEventQueueItem)`, no broker fencing (self-declared stamp),
  no real Kafka multi-instance/PostgreSQL/two-node evidence (T13), `mutateInTransaction` still has no production caller, receiver does not retry a failed local delivery.
- Completed in this assignment: T06 `e229ce160`, T15 `399b6b047`, T07 `3826b0333`, T08 `300a005bd`, T09 `bcc3bdef2`, T10 (this commit). T16 not started.
- **Next ready by dependency:** T11 (T03, T06, T08–T10), T14, independent T16; T13 also needs T12 and real PostgreSQL/Kafka/two-node infrastructure. Each needs a fresh
  developer-granted two-hour backstop; none is active now.

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
 think this## P3-T10 STARTED 2026-11-24 14:46 UTC (developer assignment; base `bcc3bdef2` = T09 commit, verified HEAD, tree clean)

Start 14:43 UTC (first `date -u` check 14:43:47; corrected from an earlier 14:46 guess); two-hour backstop deadline 16:43 UTC. No competing Maven processes; `test.log` absent. Failed approaches: none yet.
Scope per plan P3-T10; T11 webhook dispatch, Phase 4 replay, exactly-once delivery out of scope.



## CHECKPOINT 2026-11-24 14:42 UTC — P3-T09 COMMITTED; read this first

- **P3-T09 finished** inside its 12:50–14:50 UTC window; commit follows base `300a005bd` (see `git log -1`). Reviews (fresh Bob contexts, not Sol):
  1 Changes required (partial, F1 major docs), 2 correction re-review Accepted within scope (N1/N3 doc fixes applied; N2/N4 non-blocking notes). Record: `reviews/P3-T09-review.md`.
- Evidence: `/private/tmp/t09-run3.log` migration 10 + PostgreSQL fence 3 (SQLSTATE 42501 denial, owner re-GRANT probe); `t09-fullmodule1.log` 93/93,
  `t09-server-common-full2.log` 834/834 (pre-correction code; corrections were docs/tests/whitespace/suppressed-exception), `t09-exec-rerun.log` 34/34,
  `t09-replicated.log` 15/15. Corrected candidate hash list `13b1b545...bd9`. Preserved failures: `t09-run1-preserved.log`, `t09-server-common*-preserved.log`.
- Retained notes: epoch check best-effort (DB privilege fence is the exclusion); `get`/`list` do not baseline (run `migrateAll()` before traffic); T10 must account for
  generation-0 replicated saves; MySQL concurrent-`activate` probe not run; no broker fencing/mixed-binary rolling writes claimed.
- Completed in this assignment: T06 `e229ce160`, T15 `399b6b047`, T07 `3826b0333`, T08 `300a005bd`, T09 (this commit). T16 not started.
- **Next ready by dependency:** P3-T10 (committed replication/finalization; T03–T05, T08, T09 satisfied) or independent T16. Each needs a fresh developer-granted
  two-hour backstop; none active now. T13 requires real PostgreSQL/Kafka/two-node execution.

## P3-T09 STARTED 2026-11-24 12:50 UTC (developer: "I believe the next task is T09"; resume authorization T06-T20)

Verified: HEAD `300a005bd` (`feat(a2a-1.1): add transactional JPA task mutation authority` = T08), tree clean, `test.log` absent, no competing
Maven/test processes. Base `300a005bd`. Start 12:50 UTC; two-hour backstop deadline 14:50 UTC. Failed approaches: none yet.
Scope: baseline migration + enforced writer fencing per plan T09; PostgreSQL GRANT/REVOKE evidence in a disposable Testcontainers fixture only.


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
