# P3-T12 Review A (fresh Bob context review)

Start: Wed Nov 25 11:37:36 UTC 2026


## Progress note (preliminary, written early)
Read so far: TaskAuthorityConfiguration, TaskAuthorityCapabilities, JpaTaskMigration, patch (all files), server-common test, MainEventBusProcessor push paths. Findings are being appended below in order of importance as they are confirmed.

# Answers (fresh Bob context review; read-only; time-boxed, see "Not examined")

Line numbers are working-tree lines (approximate `~` where read via the patch only).

## A1. `violations()` / `checkProcessLocal` / `checkSharedStorage` (TaskAuthorityConfiguration.java:72-151)

Matrix walked against the code:
- legacy-only store (`atomicStore=false`): 1 violation; the process-local/shared branches are skipped (line 90/92 gate on `atomicStore`). Correct. Legacy-only registrations never call it (only `registration.versions().contains("1.1")` triggers).
- atomic UNDECLARED: 1 violation, no further checks (neither branch matches). Correct (fail closed), but replication/webhook faults are hidden until the first is fixed (minor, UX only).
- PROCESS_LOCAL: rejects `replication.replicated()` regardless of epoch/envelopes (line 100) and rejects `sharedGate` (104). Dispatcher present/absent and sender quiescent or not are all accepted, which is right for a single JVM. No false reject found.
- SHARED_STORAGE: storage epoch null -> reject; node epoch != storage epoch (`Objects.equals`, so null node epoch rejects) -> reject; `!migrationComplete` -> reject; replicated: queue epoch null -> reject, queue epoch != storage epoch -> reject (only compared when storageEpoch != null, to avoid a duplicate message), `!committedEnvelopes` -> reject; no dispatcher -> reject; local gate -> reject; non-quiescent sender -> reject. Non-replicated shared profile with a shared quiescent dispatcher passes. No false accept found among values the record can express.

Findings:
- A1-1 (minor, note) TaskAuthorityConfiguration.java:98-108 / QueueManager.replication() default. Process-local store + a custom `QueueManager` that replicates but keeps the default `replication()==NONE` is accepted. Scenario: a third-party Kafka `QueueManager implements QueueManager` + InMemoryTaskStore + 1.1 registration -> passes. Only `ReplicatedQueueManager` overrides it (grep: the only QueueManager implementers in repo are InMemoryQueueManager and ReplicatedQueueManager). The same applies to a decorator around a ReplicatedQueueManager that does not forward `replication()`. This is an opt-in declaration limit, comparable to `AtomicTaskStore.authorityCapabilities()`. It is documented in docs/content/dev/extra/storage.md ("Custom queue managers report replication through `QueueManager.replication()`"), but the Javadoc on `TaskAuthorityConfiguration` should say a passing check relies on the declarations being truthful. Acceptable limitation, not a defect, provided that sentence is added. Requirement: "replicated queues + independent stores fail". Check: a test with a stub QueueManager that overrides `replication()` to replicated=true and a custom stub that does not, documenting the evasion explicitly.
- A1-2 (note) "PROCESS_LOCAL store with a non-replicated queue manager but a replicated one elsewhere": cannot be caught; the topology is observed only through the one `QueueManager` bean injected. Same class of limit as A1-1.
- A1-3 (minor) the gate/store coupling is boolean only: `JpaDatabaseTaskStore` + a `sharedGate=true` gate backed by a different datasource would pass. Not detectable here; document it as a deployment precondition.
- A1-4 (note) a process-local store with a non-ordered or non-quiescent sender passes (no check). Intended.

## A2. `requireVersion11()` caching (TaskAuthorityConfiguration.java:41,158-168)
- A2-1 (note) Caching a pass is acceptable: epochs only increase and the JPA store refuses stale writes itself (`checkWritable`, JpaTaskMigration.java:100). Non-detected after pass: queue-manager reconfiguration (cannot change at runtime in CDI), and old-binary writers inserting unmarked rows after the first pass (database-level fence is the exclusion, documented in the class Javadoc and storage.md). OK.
- A2-2 (note) Thread safety: `validated` is volatile and written only after a successful evaluation; concurrent first calls may each evaluate (harmless duplicate DB reads); no torn state. Failure is never cached, so first-failure-then-fix retry works (test `failureIsRetriedButSuccessIsCachedUntilTheNextObservationOfTheSameObject` exercises the real flip).
- A2-3 (minor) JpaTaskMigration.java:90-94 + VersionRouter per-request path: while the check keeps failing, EVERY 1.1 request (and every dynamic-tenant request) runs `activeEpoch()` plus, if the epoch is active, `unmigratedIds("")`. When failure is epoch mismatch with all rows migrated, `unmigratedIds` does a full anti-join scan per request (no result cap helps because the empty result needs a full scan). Scenario: node with wrong `a2a.task-store.writer-epoch` under load becomes a table-scan amplifier. Fix idea: short-circuit by skipping the migration query when `nodeEpoch != storageEpoch` (the node is rejected anyway), or rate-limit failed re-evaluation. Check: a counting test on `unmigratedIds` when epochs mismatch.

## A3. JPA capability source (JpaTaskMigration.java:79-94,173-182; JpaDatabaseTaskStore.java:204-206)
- A3-1 (note, positive) `migrationComplete = stored != null && self.unmigratedIds("").isEmpty()` is the right meaning: `unmigratedIds` selects rows with `migrationMarker IS NULL OR NOT EXISTS(lane)`, ordered by id, first page only (`after=""`, BULK_BATCH=100). Emptiness of the first page is exactly "no unmigrated row exists", so pagination is irrelevant. `unmigratedIds` throws only when no epoch is stored, and the `stored != null &&` short circuit prevents that (epochs are never deleted, so no race).
- A3-2 (note, positive) CDI: `self` is an injected `@ApplicationScoped` client proxy, so `self.activeEpoch()` / `self.unmigratedIds()` honour `REQUIRES_NEW` even when the caller (`authorityCapabilities`) is non-transactional. `nodeEpoch()` reads config, no tx needed. `null` node epoch -> `Objects.equals(null, 7)` false -> rejected. Legacy mode (no stored epoch) -> `storageEpoch==null` -> "unmigrated and unfenced" rejection for 1.1; intended and tested at the JPA level (first test asserts `storageEpoch` null and `migrationComplete` false).
- A3-3 (minor/unverified) Startup: `QuarkusGrpcHandler.validateRegistrationAtStartup(@Observes StartupEvent)` and `A2AServerRoutes.setupRoutes(@Observes Router)` now call into the database (`REQUIRES_NEW` tx) during startup. Not covered by any Quarkus-level test in the evidence I was given (only the H2 `JpaTaskMigrationTest` calling the store directly). Ordering risk: if the same app performs `activate()`/`migrateAll()` in its own startup observer, observer order is undefined and the default 1.1 registration can fail startup before the migration ran (chicken-and-egg). The docs say migrate before traffic, but do not say it must be done by a separate admin step/process before this node starts. Suggest the docs state this explicitly. Check: a @QuarkusTest with a 1.1 default registration + unmigrated rows expects startup failure with the violation message, and one with migrated rows expects start.
- A3-4 (minor) cost: one `activeEpoch` query per evaluation plus one limited anti-join query; on a pass this is a one-time full scan (cached). Acceptable. A lane-less-but-marked row is counted as unmigrated and `migrateAll()` repairs it via `baseline()`; that specific case (marker set, lane missing) has no test (only legacy-row case is tested).
- A3-5 (note) `taskStore.getClass().getName()` (TaskAuthorityConfiguration.java:68) is the CDI client-proxy class name in Quarkus, making the message cosmetic-noisy; and a wrapper/decorator TaskStore that does not implement `AtomicTaskStore` is reported as legacy-only (fail closed). OK.

## A4. InMemoryTaskStore (InMemoryTaskStore.java:~172-176)
- A4-1 (note) `authorityCapabilities()` = `processLocal()` is consistent with the current class: tasks live in a `ConcurrentHashMap<String, StoredTask>` with per-store `InMemoryWebhookDispatchGate`, a `revisionSequence`, and `save()` initializes the webhook lane for baselined tasks (generation >= 1) under `compute`. I only read the head of the class (fields, constructors, `save`/`get`); I did NOT re-read `readSnapshot`/`mutate` bodies after commit 44c276a17's snapshot change, so "still accurate" is based on the structure and on the declaration being scope-only. Mark as partially verified.
- A4-2 (note) `processLocal()` sets `migrationComplete=true`, `storageEpoch=nodeEpoch=null`. `checkProcessLocal` never reads those fields, so it is harmless. If a custom store wrongly declared SHARED with these values, it would be rejected (epoch null), so no accidental pass.

## A5. Replication seam
- A5-1 (note, positive) `ReplicatedQueueManager.replication()` returns `(true, writerEpoch, strategy.supportsCommittedReplication())`. Legacy-only replicated deployments (epoch null, 1.0/0.3 only) are never evaluated, so unaffected. A replicated deployment with in-memory store and epoch null asking for 1.1: `checkProcessLocal` rejects it on `replicated()` alone (epoch is irrelevant), good. Replicated + shared JPA + epoch null: "no writer epoch" violation, good.
- A5-2 (minor) The public `(..., Long)` constructor gap is surfaced only through topology validation, and only at 1.1 exposure. `ReplicatedQueueManagerTest.replicationTopologyReportsEpochAndCommittedEnvelopeSupport` shows it, but via a hand-built `Topology("memory")`, not `TaskAuthorityConfiguration.of(store, publicCtor, ...)`. Mutating `of(TaskStore, QueueManager, ...)` to ignore `queueManager.replication()` would not fail this test (it would fail nothing in server-common either; see A7).
- A5-3 (minor) `supportsCommittedReplication()` default false is a behaviour trap for third-party strategies that do implement `send(ReplicatedEventQueueItem)` but also for wrapper/decorator strategies that do not forward the new method: both are false-rejected for 1.1 (fail closed, documented in replicated-queue-manager.md). Conversely a strategy returning true without implementing `send(item)` is a false accept (self-declared). Acceptable; docs cover the first.
- A5-4 (minor) `ReactiveMessagingReplicationStrategy.supportsCommittedReplication()=true` (replication-mp-reactive) has no test in the evidence. Mutating it to `false` would reject every supported Kafka profile at 1.1 and no listed test would fail.

## A6. Webhook facts
- A6-1 (note) Confirmed: the `@Inject` constructor `OrderedWebhookDispatcher(WebhookDispatchLaneStore, PushNotificationSender)` throws for `gate.isSharedStorage() && sender == null`. Hence `quiescentSender=false` with `sharedGate=true` is unreachable through production constructors; it is reachable only through the package-private test constructors. The check at TaskAuthorityConfiguration.java:146 is defense in depth, and with a local gate no violation is emitted for a non-quiescent sender (correct for single JVM). Test `sharedGateWithoutQuiescentSenderCannotEvenBeBuilt` documents this.
- A6-2 (note) "Process-local gate with shared storage": reachable through plain-Java wiring or an app that overrides the gate bean; in CDI with the JPA module, `JpaWebhookDispatchGate` is `@Alternative @Priority(50)` and replaces the InMemory gate for `WebhookDispatchLaneStore` injection, so the normal supported wiring is consistent. Conversely "shared gate with process-local store" (checkProcessLocal:104) is reachable in CDI when the JPA module is on the classpath with a custom in-memory atomic store: that app is rejected for 1.1. Intended.
- A6-3 (minor) Sender coverage: `MainEventBusProcessor` is the only code path that calls `PushNotificationSender` (grep over server-common main: DefaultRequestHandler references are Javadoc only), and it holds one dispatcher for bus and finalization sends, so the topology covers the sender population. The processor's own `pushSender` and the dispatcher's `sender` are independent constructor parameters in plain Java wiring (`MainEventBusProcessor.java:177-184`), so `topology()` reporting `sender != null` describes the dispatcher's sender, not necessarily the processor's. Make this a documented assumption or compare them.
- A6-4 (major if reachable, unverified) Direct-sender / independent-chain fallbacks remain in code: MainEventBusProcessor.java:~440 (`sendPushNotification(...)` when `updateResult.committedBatch()==null`), ~938-950 (`reserveCommittedPushBatch` fallback when `dispatcher==null || !orderedWebhookDispatchAvailable()`), ~1118-1148 (`sendLegacyTaskSnapshotInOrder` fallback) and ~1246-1256. With the topology gate satisfied for shared storage (dispatcher non-null, sender non-null) the second and third are not taken. The line-440 path is guarded only by `committedBatch == null`; I did not establish whether a shared atomic JPA store can ever produce a result with a null `committedBatch` (e.g. a non-committing branch). If it can, a shared profile would use an independent push chain while the gate says OK. Needed: confirm in `MainEventBusProcessor`/`updateResult` creation that atomic stores always set `committedBatch` for events that are pushed. Discriminating test: shared-store profile through real processor with a no-op/replicated-event branch asserting `sendPushNotification` direct path is not invoked.
- A6-5 (note, positive) A 4-arg `MainEventBusProcessor` reports `WebhookDispatch.NONE` (`orderedDispatcher=false`) -> rejected for shared storage ("independent push chains"); accepted for process-local. Only the hand-built `Topology` test covers the rejection; the live test `liveTopologyFromStoreQueueManagerAndBusProcessorBacksSingleProcessMemory` covers acceptance only (see A7).

## A7. Tests
Real classes exercised: server-common `liveTopologyFromStoreQueueManagerAndBusProcessorBacksSingleProcessMemory` (real InMemoryTaskStore + InMemoryQueueManager + 4-arg processor), `liveTopologyFromLegacyOnlyCustomStoreIsRejectedFor11`, `dispatcherTopologyReportsGateAndSenderFacts` (real dispatcher), `sharedGateWithoutQuiescentSenderCannotEvenBeBuilt` (real constructor), `handlerExposesItsTopologyForPlainJavaWiring`, `inMemoryStoreDeclaresAProcessLocalAuthority`, `customAtomicStoreIsUndeclaredUnlessItOptsIn`, JPA `authorityCapabilities...` (real H2 store; webhook and replication facts hand-built), ReplicatedQueueManager `replication()` values.

NOT exercised against real classes (hand-built `Topology` only):
- A7-1 (major, test gap) The supported shared-JPA profile never runs through `TaskAuthorityConfiguration.of(taskStore, queueManager, processor::webhookDispatchTopology)` with a real `JpaDatabaseTaskStore` + real `ReplicatedQueueManager` + real `OrderedWebhookDispatcher(JpaWebhookDispatchGate, sender)` + real `MainEventBusProcessor`. Mutations that would escape: (a) `of(...)` passing `QueueReplication.NONE` instead of `queueManager.replication()`; (b) `webhookDispatchTopology()` returning `NONE`/ignoring the dispatcher; (c) `topology()` reporting `sharedGate=false`; (d) `atomicStore` computed as `true` always. The `Topology`-record tests would not fail on any of (a)-(d); the in-memory live tests would fail only on (d)-ish and on (b) not at all (NONE is what the 4-arg processor reports and it passes for process-local).
- A7-2 (minor) real 4-arg processor + shared store (reject) and real dispatcher with local gate + shared store (reject) are not live-tested.
- A7-3 (minor) `replicationTopologyReportsEpochAndCommittedEnvelopeSupport` does not use `of(store, qm, ...)`; see A5-2.
- A7-4 (minor) JPA tests: null node epoch (config unset while epoch active), marker-set-lane-missing row, and failing-then-passing re-evaluation on a real store are not covered; node epoch is always `2`.
- A7-5 (minor) vacuous/weak: `validatingResolverDoesNotNarrowARejectedRegistration` asserts `withV11.versions()` unchanged after the delegate returned that very object; `validating()` cannot mutate it, so the assertion is tautological (the `assertThrows` half is real). `assertEquals(List.of(), ...violations())` first assert in the public-ctor test uses `QueueReplication.NONE`, so it proves nothing about the constructor.

Mutation table (existing test that would fail?):
- flip node/storage epoch comparison (line 117): `mismatchedWriterEpochsAreRejected` fails. Yes.
- flip queue/storage epoch comparison (line 130): same test, `queueMismatch`. Yes.
- drop `migrationComplete` check (121): `unmigratedOrUnfencedSharedStoreIsRejected`. Yes.
- drop `storageEpoch == null` check (113): same test `noEpoch`. Yes.
- drop `orderedDispatcher` / `sharedGate` check (139-145): `sharedProfileRejectsMissingOrMismatchedWebhookGate`. Yes.
- drop `quiescentSender` check (146): `noQuiescence` hand-built. Yes (but unreachable in production, A6-1).
- drop `!committedEnvelopes` check (134): `replicationWithoutEpochOrCommittedEnvelopesIsRejected`. Yes.
- drop `replication.replicated()` check in `checkProcessLocal`: `replicatedQueuesWithIndependentInMemoryStoreAreRejected`. Yes.
- `validated=true` set even on failure: `failureIsRetried...` fails. Yes.
- JpaTaskMigration: drop `self.unmigratedIds("")` emptiness -> `unmigrated.migrationComplete()` false assertion fails; drop `stored != null` -> `unmigratedIds` throws in the legacy test. Yes.
- ReplicatedQueueManager.replication() returns replicated=false/epoch null: `replicationTopologyReports...` fails. Yes.
- `of(store, qm, ...)` ignoring `queueManager.replication()` or `webhooks.get()`: NO existing test fails (A7-1).
- ReactiveMessagingReplicationStrategy returns false: NO listed test (A5-4).
- VersionRouter/route wiring mutations: not examined (reference/common tests unread).

## Additional observations (outside my main area, noted while reading the patch)
- N1 (minor) MultiVersionJSONRPCRoutes.java ~line 66 and MultiVersionRestRoutes.java ~line 153: the inserted `VersionRouter.requireTaskAuthority(...)` line leaves the following `registration.requireAvailableVersions(...)` mis-indented (cosmetic; also checkstyle/format risk).
- N2 (note) In the multiversion routes the authority check runs for any request hitting a registration that includes 1.1, including 0.3/1.0-selected requests. This is consistent with the docs ("rejected as a whole, never served as {0.3,1.0}") but it means a failing topology also stops legacy-version traffic on a 1.1-advertising registration. If that was not the intent of "legacy-only registrations unaffected", it should be stated clearly: unaffected means registrations that do not include 1.1.
- N3 (note) The `else if` in `VersionRouter.attachRegistration` checks a 1.1-selecting request with no registration; `authority == null` returns silently (manual wiring) as documented.

## Disposition
**Incomplete evidence**

The validation logic is sound for every combination expressible in the `Topology` record, and I found no false accept or false reject there. The remaining concerns are outside what the evidence proves: (1) no test composes the real shared-JPA profile (store + replicated queue manager + dispatcher + processor) through `TaskAuthorityConfiguration.of(store, queueManager, ...)` (A7-1, major), (2) the `committedBatch == null` direct-push path at MainEventBusProcessor.java:~440 is unconfirmed unreachable for shared atomic stores (A6-4), (3) startup-time DB access and migration ordering are untested (A3-3). To move this to "Accepted within scope": add the live-composition tests from A7-1/A7-2, confirm A6-4 by reading `updateResult.committedBatch()` construction, add a test for `ReactiveMessagingReplicationStrategy` (A5-4), and document A1-1/A3-3.

## Findings ledger
| ID | Severity | Location |
| --- | --- | --- |
| A7-1 | major (test gap) | TaskAuthorityConfiguration.java:59-69 / server-common & JPA & extras tests |
| A6-4 | major if reachable (unverified) | MainEventBusProcessor.java:~440, ~938-950, ~1118-1148, ~1246-1256 |
| A1-1 | minor (documented limitation) | QueueManager.java (`replication()` default), TaskAuthorityConfiguration.java:98-108 |
| A2-3 | minor | JpaTaskMigration.java:90-94 (cost per failed request) |
| A3-3 | minor (untested) | QuarkusGrpcHandler.validateRegistrationAtStartup, A2AServerRoutes.setupRoutes, JpaTaskMigration.java:80-94 |
| A5-2 | minor | ReplicatedQueueManagerTest.replicationTopologyReportsEpochAndCommittedEnvelopeSupport |
| A5-3 | minor | ReplicationStrategy.supportsCommittedReplication default false |
| A5-4 | minor (test gap) | ReactiveMessagingReplicationStrategy.supportsCommittedReplication |
| A6-3 | minor | MainEventBusProcessor.java:177-184, OrderedWebhookDispatcher.topology() |
| A7-2 / A7-3 / A7-4 / A7-5 | minor (test gaps / vacuous assert) | server-common & JPA test files |
| A1-3 | minor | TaskAuthorityCapabilities / gate-store coupling |
| A3-4 | minor | JpaTaskMigration.unmigratedIds lane-missing case untested |
| N1 | minor (formatting) | MultiVersionJSONRPCRoutes ~66, MultiVersionRestRoutes ~153 |
| A1-2, A1-4, A2-1, A2-2, A3-1, A3-2, A3-5, A4-1, A4-2, A5-1, A6-1, A6-2, A6-5, N2, N3 | note | various |

No blockers found.

## Not examined
- reference/common TaskAuthorityConfigurationTest (9 tests) and VersionRouterTest: not read; VersionRouter/route wiring beyond the patch hunks.
- Full InMemoryTaskStore `readSnapshot`/`mutate` bodies after 44c276a17 (A4 only partially verified).
- How `updateResult.committedBatch()` is produced in MainEventBusProcessor (A6-4).
- Quarkus runtime behaviour: startup-event DB access, CDI alternative resolution (read from annotations only: `@Alternative @Priority(50)`), proxy class names.
- replication-mp-reactive tests, docs accuracy beyond the sections in the patch, acceptance A01/A02/A04/A13 text itself (I used the task statement summary).
- Test logs (/private/tmp/t12-*.log) were not opened; no Maven or git state was touched.

End: $(date -u)
Wed Nov 25 11:43:44 UTC 2026
