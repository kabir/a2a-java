# P3-T12 review G (fresh Bob context, validation logic)

Start: Wed Nov 25 16:26:15 UTC 2026

Read-only review. I did not run Maven or any tests, and I edited nothing except this file.
The review stopped at the turn limit, so every answer below comes from reading code and the diff (`git diff HEAD`).

## Q1. JpaTaskMigration.authorityCapabilities and epoch mismatch

Code: `JpaTaskMigration.java:90-97`. `migrated = stored != null && node != null && node == stored && unmigratedIds("").isEmpty()`.

- **Mismatch is always rejected.** `TaskAuthorityConfiguration.java:121` runs whenever `storageEpoch != null`, and it uses `Objects.equals(nodeEpoch, storageEpoch)`. A null `nodeEpoch` against a non-null storage epoch is a mismatch, so it is rejected too. A null storage epoch is rejected at `:117-119`. Whatever `migrationComplete` says, a mismatched node always gets at least the epoch violation.
- **Violation order.** The epoch violation is added first (`:121`). When the scan is skipped, `migrationComplete` is false, so `:125` adds a second violation.
- **No accept/reject flips.** `migrated` can only be true when the epochs are equal and the scan is empty. That is the only state where `:121` passes, so a node that was accepted before is still accepted and a rejected one is still rejected. The method is new relative to base (`44c276a17`), so there is no accepted/rejected topology in base to compare against. I did not see the earlier uncommitted draft.
- **Finding G-1 (low).**
  - Where: `TaskAuthorityConfiguration.java:125-127` together with `JpaTaskMigration.java:93-95`.
  - Failure scenario: node epoch 6, storage epoch 7, every row baselined. The operator is told "Shared task rows are not all baselined under writer epoch 7". That is untrue, because the scan was skipped, so the operator may chase the wrong problem.
  - Requirement violated: an accurate diagnostic.
  - Discriminating check: a JPA test with a mismatched epoch and a fully migrated DB, asserting the `:125` message is absent or says the scan was skipped. `JpaTaskMigrationTest:296-306` only asserts `!violations().isEmpty()` and does not assert `migrationComplete()`.
- **Finding G-2 (low, unverified).**
  - Where: `JpaTaskMigration.java:181-183`.
  - The scan checks that a lane row exists. It does not check the lane's `writerEpoch` against the active epoch. `JpaTaskMutationTransactions.java:69-71` shows that `read()` rebases an unclaimed lane with a different epoch.
  - Failure scenario: the epoch is raised from 7 to 8 and lanes still carry epoch 7. `migrationComplete` is true, although `read()` would still rebase those lanes lazily.
  - The message at `:126` ("baselined under writer epoch N") is stronger than what is checked.
  - Impact is probably benign because the rebase is lazy and happens under a lock. I did not confirm how the dispatcher treats a stale-epoch unclaimed lane.
  - Discriminating check: a JPA test that activates 7, migrates, activates 8, and expects `migrationComplete` to be false (or the doc to say otherwise).
- **Other notes.**
  - `activeEpoch`, `nodeEpoch` and `unmigratedIds` run in separate transactions, so the result can race with an epoch change.
  - Any `RuntimeException` is caught at `violations():80-82` and reported as a violation, so it fails closed. An `Error` is not caught.

## Q2. Matrix: is violations() empty?

| Case | Result | Code |
|---|---|---|
| (a) InMemoryTaskStore only | Empty | `InMemoryTaskStore.java:175-177` returns `processLocal()`. `MainEventBusProcessor.webhookDispatchTopology` returns `NONE` when there is no dispatcher. `violations():94-95` reaches `checkProcessLocal`, which has no hit at `:104/:108`. Test `:48`. |
| (b) Custom non-Atomic TaskStore, 1.1 | Rejected | `of(...)` at `:69-72` gives `atomicStore=false` and `UNDECLARED`. `:87-89` adds a violation, and `:90` is skipped because of the `else`. One violation, per test `:65-69`. |
| (b') Custom AtomicTaskStore without an override | Rejected | The default `AtomicTaskStore.authorityCapabilities()` returns `UNDECLARED`, so `:90-93` rejects it. |
| (c) JPA, epoch equal, migrated, shared-gate dispatcher, no replication | Empty | `JpaDatabaseTaskStore.authorityCapabilities` delegates to `JpaTaskMigration:96`. Epoch passes `:117/:121/:125`, `replication.replicated()` is false (`:130`), and webhooks `(true,true,true)` pass `:143-153`. Test `:57`. |
| (d) Same plus ReplicatedQueueManager with equal epoch | Empty only if the strategy overrides `supportsCommittedReplication()` | `ReplicatedQueueManager.replication():128-131` gives `(true, writerEpoch, strategy.supportsCommittedReplication())`. `:131-141` pass when the epoch equals storage and committed envelopes are true. A third-party strategy that overrides `send` but not the new default `false` is rejected at `:138`. That fails closed but is a compatibility break. Test `:59` is synthetic. |
| (e) Replicated queue + InMemoryTaskStore | Rejected | `:104-107`. The CDI constructor (`ReplicatedQueueManager:119-124`) also throws when an epoch is set. Test `:83`. |
| (f) Replicated queue, epoch null | Rejected | Shared: `:131-133`. Process-local: `:104`. Test `:129-134`. |
| (g) Replicated epoch != storage epoch | Rejected | `:134-137`. The guard `storageEpoch != null` skips this check when `storageEpoch` is null. Test `:122-125`. |
| (h) Shared store, no dispatcher | Rejected | `:143-145`. |
| (h) Shared store, process-local gate | Rejected | `:147-149`. |
| (h) Process-local store with a shared gate | Rejected | `:108-111`. |
| (h) Missing dispatcher with a process-local store | Accepted | Intended. Test `:48-52`. |
| (i) Sender not quiescent | Rejected | `OrderedWebhookDispatcher.topology():227-229` gives `quiescent = sender != null`. `:150-153` reject it. |

- **Case (i) limits.** The `@Inject` constructor (`OrderedWebhookDispatcher.java:184`) already throws for shared gate plus null sender, so `:150` is reachable only through the package-private constructors or a synthetic Topology. Tests `:153-156` and `:284-288` confirm this.
- **Finding G-3 (low).**
  - Where: `TaskAuthorityConfiguration.java:143-146` (message) and `DefaultRequestHandler.taskAuthorityConfiguration()`.
  - The check observes one MainEventBusProcessor's dispatcher. The message says "on every bus and finalization sender", but no other bus or sender is observed. Nothing checks that the dispatcher's gate and the JPA store share the same database. `isSharedStorage()` is only a boolean.
  - The same applies to the `taskStateProvider` of the queue manager versus the task store.
  - These gaps are partly acknowledged ("declared pieces are consistent", `TaskAuthorityConfiguration.java:18-26`), but the `:144` wording over-claims.

## Q3. False pass from a missing or UNDECLARED capability?

I found no path that accepts 1.1 when a capability is missing or UNDECLARED.

- `UNDECLARED` hits `:90-93`, and it hits no branch at `:94-98`.
- `Topology` rejects null `store`, `replication`, `webhooks` and `storeName` (`:36-41`).
- Shared storage with `atomicStore=false` still hits `:87`.
- A wrapper that implements `AtomicTaskStore` without delegating `authorityCapabilities()` gets the `UNDECLARED` default.
- A wrapper that does not implement `AtomicTaskStore` fails `instanceof` at `:69-70`.

**Residual false passes, which the class doc acknowledges at `:23-26`:**
- A custom QueueManager that replicates but does not override `replication()` returns `NONE` and is invisible.
- A store that falsely declares `SHARED_STORAGE` is an assertion, not evidence.
- `of(Supplier)` and `of(Topology)` accept any caller-built topology.

## Q4. requireVersion11 caching

- **Thread safety.** `validated` is volatile (`:45`) and is set only after an empty violation list (`:171`). Concurrent first calls may each compute `violations()` (including the JPA scan) and each set it to true. That is benign, and I found no lock-free hazard.
- **After a failure.** A failure throws before `:171`, so the next call re-evaluates. Test `:181-193` shows this.
- **Documented.** Javadoc `:27-30` states that a pass is cached for the life of the object and that a stale-epoch node is refused by the store on every write.
- **Finding G-4 (low, a documentation gap).**
  - Where: `TaskAuthorityConfiguration.java:27-30` and `:162-172`.
  - Failure scenario: after a pass, another operator raises the storage epoch. This node keeps serving and advertising 1.1 while every write fails. The cached pass is never invalidated.
  - Separately, the cache also covers `migrationComplete`. Rows written later by an unfenced old-version writer are not seen again. That is the database-fencing precondition, but the doc only discusses epochs.
  - Discriminating check: a test that changes the supplier's result after a pass and asserts the documented behaviour.
- **Known open item.** A failing check re-runs the JPA scan on every call (A2-3). It is already documented.

## Q5. Do the tests discriminate?

I did not run the tests. For each branch, I asked which assertion would fail if the check were deleted.

- **Epoch mismatch, `:121-124`.** `TaskAuthorityConfigurationTest.mismatchedWriterEpochsAreRejected` at `:113-121`. Both `assertEquals(1, ...)` assertions (`:115` and `:120`) and the `contains("node's writer epoch 6")` assertion fail if the check is removed, because `migrated` is true in those fixtures.
- **Migration incomplete, `:125-128`.** `unmigratedOrUnfencedSharedStoreIsRejected` at `:105-108`: `assertEquals(1, ...)` plus `contains("not all baselined")`.
- **Committed envelopes, `:138-141`.** `replicationWithoutEpochOrCommittedEnvelopesIsRejected` at `:135-138`.
- **Others, also covered:**
  - `:117`: test `:100-103`.
  - `:134`: test `:122-125`.
  - `:143`: test `:143-146`.
  - `:147`: test `:148-151`.
  - `:150`: test `:153-156`.
  - `:104`: test `:84-87`.
  - `:108`: test `:92-95`.
  - `:90`: test `:76-79`.
  - `:87`: test `:65-69`.
- **Finding G-5 (low): weak tests.**
  - The `storageEpoch != null &&` guard at `:134` has no test with a null storage epoch and a non-null queue epoch. Removing the guard would add a spurious second violation that nothing asserts.
  - The JPA test `JpaTaskMigrationTest:296-306` asserts only `!isEmpty()`. It would still pass if the epoch-mismatch check were deleted, because the migration and queue checks could also produce violations. It cannot tell that the scan was skipped.
  - Several tests use hand-built Topology records. The only live compositions are InMemory ones (`:237`, `:308-356`).
  - I saw no test composing the real JPA store, ReplicatedQueueManager and a dispatcher through `of(store, queueManager, ...)`. I did not read the `ReplicatedQueueManagerTest` or `ReactiveMessagingReplicationStrategyTest` diffs.

## Q6. Retained note A6-4: MainEventBusProcessor ~436

- **T12 did not change that code.** The T12 diff to `MainEventBusProcessor.java` is 10 added lines: the import and `webhookDispatchTopology()` at about line 1066. Lines 436-441 are untouched.
- **The retained note is incomplete.** The condition at `:436-441` is `!isReplicated && persisted && updateResult.committedBatch() == null && eventToDistribute instanceof StreamingEventKind && !isUnassociatedMessage(...)`.
- **When the batch is null for an atomic store.** `updateTaskStore:627-630` builds a batch only when `mutation != null && mutation.changed() && !mutation.events().isEmpty()`. Reading the code, the batch is null in these cases:
  - **No mutation.** `TaskManager.processCommitted:323` returns `(false, null)` for a direct Message or control event.
  - **Unchanged mutation.** `TaskManager:341`, `TaskMutationPlanner:258/:298`, and the store paths `InMemoryTaskStore:263-273` and `JpaTaskMutationTransactions:134-139`.
  - **Changed mutation with empty events.** `TaskManager.planLegacyInitialDelta` at `:423` and `:429` returns `new MutationPlan(..., List.of(), true)`. `InMemoryTaskStore:283` and `JpaTaskMutationTransactions:148-149` handle this as `compatibilityBootstrap`, with no batch and no slot.
- **Finding G-6 (medium, retained from T10/T11, not introduced by T12).**
  - Where: `MainEventBusProcessor.java:436-441` and `:1250-1286`.
  - Failure scenario: on a shared JPA store, a first status or artifact event with no opening input and no initial message goes through `planLegacyInitialDelta`. A direct Message that has a `taskId` is also reachable. For these, `sendPushNotification` runs through the per-task `pushNotificationChains` on the commonPool or push executor. That is an independent chain that bypasses the shared ordered gate.
  - Requirement violated: `TaskAuthorityConfiguration.java:143-146` says "independent push chains are not allowed", and the handoff and progress notes state the null path is only for no-mutation/unchanged results. That statement is inaccurate for the changed-but-no-events case.
  - Evidence limits: reasoned from code only. I did not confirm that the legacy bootstrap is reachable with the 1.1 request flow, so the severity is conditional.
  - Discriminating check: a processor test with a shared-gate dispatcher and a shared atomic store. It sends an initial status update with no opening input, and a Message with a taskId. It asserts that `PushNotificationSender.sendNotification` is not called outside the ordered dispatcher.

## Disposition: Changes required (small)

The validation logic in `violations()`, `requireVersion11` and `validating` is sound for the Topology record, and I found no false pass for missing or UNDECLARED capabilities.

The changes needed are:
- Correct the retained A6-4 statement in the handoff, `bob-progress.md` and the review ledger so it lists the changed-but-no-events and Message-with-taskId paths. Either add the discriminating test (G-6) or state it as an open gap.
- Soften the `:144` message and the docs about "every bus" (G-3). Document the cache and fencing limits (G-4), and the lane-epoch limit (G-2).
- Optionally make the G-1 message honest and strengthen the G-5 tests.

None of these needs a change to the pass/fail logic. If the team prefers to leave the code and notes as they are, the disposition becomes Accepted within scope only with G-6 recorded as an open, unproven risk.

## Not examined
- Any test execution or Maven build.
- The `ReplicatedQueueManagerTest`, `ReactiveMessagingReplicationStrategyTest` and the other JPA test diffs beyond `JpaTaskMigrationTest:265-306`.
- The wiring in `reference/*` (DefaultProducers, VersionRouter, routes, gRPC) and where `requireVersion11` is invoked.
- The docs diffs (`docs/content/dev/**`).
- `JpaWebhookDispatchGateTransactions` and how the dispatcher treats stale-epoch lanes.
- The `OrderedWebhookDispatcher` internals beyond `topology()`, the constructors and the shared-gate checks.
- Whether the legacy-bootstrap path is reachable in a real 1.1 request.
- The earlier reviews A and A2, except for the A6-4 text.

End: see the last command's `date -u` below.
Wed Nov 25 16:36:16 UTC 2026
