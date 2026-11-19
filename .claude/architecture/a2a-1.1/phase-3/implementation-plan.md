# A2A 1.1 Phase 3 Implementation Plan

**Status: APPROVED AT PLAN LEVEL — independent Sol High re-review, 2026-11-17. P3-T01, P3-T02 and P3-T03 are implemented and independently accepted. P3-T04 is ready for a separate developer assignment; later rows remain planned.**

> Execute one developer-selected task per separate Luna medium/normal session;
> stop with a handoff for a separate Sol medium/normal review. The user's manual
> workflow overrides the writing-plans skill's automatic execution/subagent and
> commit suggestions. Do not spawn agents or advance to another task automatically.

**Goal:** Implement authoritative ordinary-send/task mutation and committed ordered
publication, with matching public-client generation/artifact behavior and evidence
over JSON-RPC, REST and gRPC, within the selected experimental pinned-RC profile.

**Architecture:** An additive atomic-store companion owns the read/validate/plan/
commit boundary. A shared planner derives canonical mutations, and an ordered
publisher distributes only committed results. Executor proposals have immutable
bases and local completion provenance; clients reduce authoritative events without
allocating generations, rebasing input or changing selected versions.

**Tech stack:** Java 17+, Maven, JUnit 5, Mockito, Quarkus, JPA, PostgreSQL/MySQL,
Kafka/Testcontainers, existing Gson/protobuf codecs and client transport companions.

**Spec/design:** [Phase 3 contract](../implementation-tasks.md#phase-3-authoritative-task-mutation-and-publication),
[architecture](../architecture.md#mutation-persistence-and-replication),
[acceptance](../acceptance.md), [validation](../validation.md),
[selected policies](../phase-0/selected-policies.md),
[required client rules](../phase-0/required-client-rules.md),
[API migration decision](../phase-2/api-migration.md).

## Independent approval and binding implementation checkpoints

[Independent Sol High correction re-review](reviews/plan-review.md) approved this
plan for separately developer-assigned, bounded implementation tasks. All three
original findings are closed; no blocking regression was found. This is not
runtime acceptance, test evidence, commit authorization or permission to advance
automatically. The review's source hashes pin the reviewed plan/index; this update
records its disposition/checkpoints and leaves the review report unchanged.

Carry these reviewed checkpoints into the assigned tasks:

- **T01/T03, T11/T13:** CommittedMutation contains the batch's one final committed
  Task snapshot. An earlier event in that batch has an earlier generation; its
  generation need not equal the snapshot's final generation. “Matching event/
  snapshot generations” means matching committed batch provenance. Test a real
  multi-generation batch; never fetch a later unrelated Task or fabricate
  intermediate persisted snapshots. Additional per-event snapshot contracts
  require a separate design decision before they are introduced.
- **T11/T13:** An abandoned claimed webhook lane may block dispatch indefinitely
  until actual worker/process quiescence is proven. The 30-second attempt deadline
  is not a lane-recovery deadline. Tests include queued pre-HTTP work, running
  child futures, overflow/drop and incapable adapters; a boolean sender return,
  interrupted wait or cancelled wrapper future alone cannot release ownership.
  Preserve the accepted best-effort payload-loss and operational recovery limits.
- **T16–T19:** Preserve synchronous unary sendMessage exception/callback timing
  while adapting decoded asynchronous companions. Optional custom-provider
  subscription fallback is allowed only when the optional operation is unsupported
  before dispatch, never as retry after an opening failure/wrapper rejection or
  where dispatch is uncertain. Test one subscription ingress and no wrapper bypass.

P3-T01, P3-T02 and P3-T03 are implemented and independently accepted. P3-T03
acceptance is recorded in its final correction re-review; original findings and
intermediate dispositions remain preserved. Later task implementation/review
remains not started/pending. P3-T04 is ready for a separate developer assignment;
there is no automatic progression.

## Review correction record — 2026-11-17

Correction assignment first recorded clock: 2026-11-17 15:38:15 UTC; conservative
two-hour deadline: 17:38:15 UTC. Requested planning role/settings remain Sol
medium/normal; actual serving telemetry is unavailable.

The developer supplied an independent Sol High review recommending revision with
three findings. These are the writing agent's proposed corrections, not an
independent acceptance or a claim of runtime verification:

| Supplied finding | Proposed correction / re-review target |
| --- | --- |
| P1 existing 1.1 entry points bypass reduction/reliability | T16 covers sendMessage unary/streaming and ordinary subscribeToTask alongside both input APIs; T17–T19 prove each binding, without cursor support. |
| P1 independent writer webhook chains can overtake | New T11 owns a shared dispatch lane; T13 proves the paused-dispatch two-node trace. Remote subscriber copies cannot dispatch independently. |
| P2 existing-task enrollment can survive failed input | T06–T08 require partial-effect reporting with known IDs, no unsafe compensation, and concurrent-config tests. |

A task was inserted to keep webhook coordination bounded: original T11–T19 are
now T12–T20. No implementation began. The shared dispatch lane's availability
tradeoff (no automatic takeover of a potentially active sender) is a proposed
mechanism requiring independent re-review, not an accepted stronger delivery
profile. The developer requests Sol High, normal speed for that re-review;
serving telemetry is not asserted. Bootstrap/origin policies and specification
gates are unchanged.

## Planning provenance and constraints

- Workspace: `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch
  `support/a2a-1.1.0`. Inspected HEAD: `b99a29152`, the Phase 2 completion commit.
  Initial status contained only untracked `test.log`; preserve it and all subsequent
  changes. The fsmonitor IPC warning was bypassed for later status reads with
  `git -c core.fsmonitor=false`; no reset, checkout or Phase 2 verification rerun.
- Planning assignment: 2026-11-17; deadline no later than 17:12 UTC (two-hour
  backstop from approximately 15:12 UTC). Requested role/settings: Sol,
  medium reasoning, normal speed. Exact serving model/settings telemetry is not
  exposed here; this is a request record, not independently verified telemetry.
- Target remains the retained experimental `v1.1.0-rc` pin
  `78e1d6a1f7f118c8e9bc830b04a8579c1119cb78`. No fresh upstream-resolution claim
  is made. Open-PR proposals are not target requirements.
- Astra is prohibited. No agents, builds, commits, pushes, PRs or runtime edits
  are part of this planning assignment. No historical ledgers need reloading.
- Every implementation assignment has the current two-hour backstop. Every
  test/build command has a process deadline of the smaller of 20 minutes and
  remaining assignment time, with at most 30 seconds controlled cleanup. Record
  exact commands, results, suite counts, skips and source provenance. After three
  substantive attempts without progress on one defect, stop for a developer-started
  Sol diagnosis. No iteration quotas or cumulative command numbering.
- Keep `MessageSendParams.message()` non-null, the legacy constructors and
  `CURRENT_PROTOCOL_VERSION=1.0`; preserve the separate client default of 1.1.
  D1 authorizes only its recorded model/sealed-family changes. Default methods
  alone do not prove API/SPI compatibility. Any additional unavoidable common-API
  or client-SPI break requires an explicit decision before that change.
- Reuse Phase 2 codecs, typed errors, projection and transport capabilities.
  Do not silently enable cursor/long-poll/live/delivery-version operations.
  Incomplete 1.1 registrations remain experimental; this phase is not certification.

## Policy and decision boundaries

Already selected; do not ask the developer to choose these again:

1. Creation absorbs the accepted opening message at generation 1. Opening
   artifacts then consume consecutive generations in list order. Required baseline
   status consumes no extra generation; a separately emitted status does, including
   same-state progress. The returned snapshot summarizes the committed watermark.
2. A message-plus-artifact batch checks its guard once against pre-batch state,
   validates the whole batch, then commits atomically. This stronger transactional
   SDK policy is not a new protocol receipt or request-idempotency promise.
3. Artifact identity is `(taskId, artifactId)`. Preserve creator origin; missing
   legacy origin means AGENT. Normalize omitted new origin from ingress; reject
   explicitly inconsistent roles and cross-origin edits with ordinary invalid
   parameters. USER artifacts stay canonical but are hidden from older views.
4. No-task direct Message means no task/input/artifact/config persistence,
   generation, task publication or replication. Existing-task accepted input
   remains committed even if the executor responds with Message.
5. Full Task output is a proposal, never a replacement write. Omission is not
   deletion; canonical input/history/timeline/USER artifacts survive. Proposal
   base is immutable. Terminal artifacts precede terminal status. Metadata-only
   normalization retains status-message identity and occurrence count.
6. Baseline persist-before-visibility permits a commit-to-publication crash gap.
   Durable outbox delivery, durable executor-result indexing and durable replay
   are optional profiles, not Phase 3 defaults. No automatic public input retry.

Plan choices approved at plan level (routine additive API names need no separate
preference approval): use shared transactional JPA as the initial cluster authority;
keep an additive `AtomicTaskStore` companion so legacy-only custom stores work;
expose the already-designed TaskView/TaskObservation contract for **ordinary send**
early enough to make reducer uncertainty public. The proposed entry point is
`Client.sendInputObserved(SendInputParams, ClientCallContext): TaskObservation`.
This is not `Client.observe`, cursor replay, long-poll or a live InputHandle.

Material decisions are required only if investigation demonstrates an unavoidable
API/SPI break, the recommended JPA authority is not viable and topology/scope must
change, an outbox/stronger recovery profile is requested, or selected SDK policy
is changed (for example cross-origin collaboration). Do not add wire fields,
receipts, a public status RPC, new retryability codes or stronger delivery claims.

| Gate | Effect on this plan |
| --- | --- |
| G1 final target | Blocks final release/conformance claims; does not block the experimental Phase 3 tasks. |
| G2 discovery wording | Retain exact explicit entries and no fallback. No new discovery interpretation in this phase. |
| G4 bootstrap / G6 origin normative residuals | Selected experimental traces permit the tasks below; authoritative reconciliation still gates normative/final claims. |
| G3 cursor, repeated snapshots and interaction boundary | Blocks reliable repeated-equal-generation resync, retained-cursor and Phase 4 controller finalization. T14 uses bounded stream failure plus explicit GetTask rather than assuming a snapshot exception; T15/T16 must disable reliability on zero/equal generations. If an existing binding cannot terminate with its existing error channel, stop only its T14/T17–T19 recovery integration for a targeted decision. |
| G5 live exchange/outcomes | Blocks affected Phase 5 live association and positive input-correlation guarantees. Ordinary send request/result paths below do not require a live receipt. |
| Deployment fence / authority evidence | Real prerequisites for migrating shared data or enabling clustered 1.1. T09 needs deployment/storage enforcement, T13 needs Docker/DB/broker execution. Missing infrastructure blocks those checks and clustered acceptance, not unrelated reducer/unit work. |
| New incompatible extension point | Blocks that API change pending explicit approval, not all Phase 3 work. Preserve legacy-only fallbacks until resolved. |

## Inspected seams and file map

Paths in task sections use these exact directory prefixes:

| Prefix | Exact directory |
| --- | --- |
| `S` | `server-common/src/main/java/org/a2aproject/sdk/server` |
| `ST` | `server-common/src/test/java/org/a2aproject/sdk/server` |
| `C` | `client/base/src/main/java/org/a2aproject/sdk/client` |
| `CT` | `client/base/src/test/java/org/a2aproject/sdk/client` |
| `J` | `extras/task-store-database-jpa/src/main/java/org/a2aproject/sdk/extras/taskstore/database/jpa` |
| `JT` | `extras/task-store-database-jpa/src/test/java/org/a2aproject/sdk/extras/taskstore/database/jpa` |
| `R` | `extras/queue-manager-replicated/core/src/main/java/org/a2aproject/sdk/extras/queuemanager/replicated/core` |
| `RT` | `extras/queue-manager-replicated/core/src/test/java/org/a2aproject/sdk/extras/queuemanager/replicated/core` |

- `S/tasks/TaskStore.java` exposes `save/get/delete/list`, with no atomic mutation
  result. `InMemoryTaskStore` has a concurrent map; that does not make a separate
  read/validate/save atomic. `TaskManager.updateWithMessage` saves outside the bus;
  `saveTaskEvent(Task,...)` saves the supplied full object.
- `S/events/MainEventBusProcessor.processEvent` saves, then distributes the
  **original** event and invokes replication hooks. `EventQueueItem` has event and
  replication flag, but no proposal base/result or committed-mutation provenance.
- `S/tasks/AgentEmitter.addTask/emitEvent` enqueue directly. `taskBuilder` and
  `S/agentexecution/RequestContext` are the custom-executor migration seams.
- `S/requesthandlers/RequestHandler` has additive input defaults rejecting advanced
  requests. `DefaultRequestHandler` does not override those companions;
  `initMessageSend` mutates existing history before config enrollment. Unary and
  streaming setup also enroll config independently. All must share one path.
- `J/JpaDatabaseTaskStore.save` merges Task JSON and fires TaskFinalizedEvent;
  `J/JpaTask` has no independent mutation revision/epoch/migration marker.
  `R/ReplicatedQueueManager.onTaskFinalized` synthesizes an unstamped final status
  then poison pill; `onReplicatedEvent` can re-enter persistence.
- All three server binding handlers reject advanced ordinary-stream inputs via
  `rejectUnsupportedStreamingInput`; `timelineLength` and delivery version are also
  guarded. Remove only guards whose behavior this phase implements.
- `C/Client.sendInput` already uses `VersionedClientTransport`; Phase 2 production
  transports encode guards/artifacts and map mismatch errors. `consumeObservation`
  discards skipped-frame evidence; `ClientTaskManager` merges without generation/
  timeline reduction and refuses a second snapshot. Retain its explicit-1.0 path.
- Existing `TransportFrame` and `TaskDecodeEvidence` carry skipped timeline
  generations. No new wire metadata is needed for client uncertainty.
- Reuse `tests/server-common/src/test/java/org/a2aproject/sdk/server/apps/common/AbstractPairedVersionServerTest.java`, its registration
  resolver/card router/TestUtilsBean, and existing `tests/multiversion/*` apps.
  the `Phase2PublicClientTest` in each client transport test package are controlled-peer evidence,
  not a substitute for running the actual server core.

## Proposed internal interfaces

Names below are new Java types in `S/tasks`; all are additive, server-local and
must not enter `spec` or protocol JSON. T01 freezes these in executable contract
tests before downstream implementation. Records use defensive collection copies.

```java
record TaskRevision(long value) {} // storage revision, never a caller generation
record TaskSnapshot(Task task, TaskRevision revision) {}
record MutationPlan(Task task, List<StreamingEventKind> events, boolean changed) {}
record CommittedMutation(TaskSnapshot snapshot,
        List<StreamingEventKind> events, boolean changed) {}
@FunctionalInterface
interface TaskMutation {
    MutationPlan plan(@Nullable TaskSnapshot current);
}
interface AtomicTaskStore extends TaskStore {
    @Nullable TaskSnapshot readSnapshot(String taskId);
    CommittedMutation mutate(String taskId, TaskMutation mutation);
}
```

`TaskMutationAuthority` is the sole runtime caller of `mutate`; it uses the pure
`TaskMutationPlanner` and the store's atomic snapshot, never a previously cached
TaskManager task. Callback plans are side-effect-free; custom store retries may
not re-invoke executor/user callbacks. Creation races and ownership creation must
be included in the store transaction, not a check-then-insert hole. An unchanged
plan preserves revision/generation and returns no events. Revision, writer epoch
and migration markers are persisted independently from Task JSON. Store failure
classification distinguishes definite non-commit from indeterminate outcome.

Each changed plan contains consecutive canonical event generations and final
Task state from the same transaction. Stable internal mutation identity is task
ID plus assigned generation under the validated storage epoch; retain payload
consistency checks. Committed envelopes additionally carry the batch's starting/
ending generations and writer epoch. They contain no completion callbacks on wire.
Subscription summary snapshots and replication copies are not fresh mutations.

Ordered publication reserves its local per-task batch slot **inside the atomic
mutation critical section**, releases it only after definitive commit, and invokes
fan-out outside storage locks. JPA/multi-node delivery also buffers by expected
generation, not arrival order; a missing predecessor cannot be skipped and later
emitted backwards. Authority state and bounded gap recovery determine whether to
deliver a coordinated baseline or terminate that observation. Reserving a slot
does not promise survival of a publisher crash or become a durable outbox.

```java
record TaskProposal(String correlationId, @Nullable TaskSnapshot base, Task proposed) {}
// Null base is only valid for first materialization with retained opening context.
// ProposalResult is a sealed server-local family defined by T04:
// Committed(correlationId, CommittedMutation),
// Rejected(correlationId, reason, nullable permitted current revision),
// Failed(correlationId, cause, knownNonCommit).
CompletionStage<ProposalResult> submitTask(TaskProposal proposal);
TaskSnapshot refreshTask(); // atomic Task/revision pair for explicit rebuild
```

These two operations are additive methods on AgentEmitter, backed by an
execution-scoped submission service. Existing void full-Task methods carry their
immutable invocation-start base and registered local result sink; no mutable
latest-revision stamping. Snapshot revisions are not authorization credentials.

## Execution index

Implementation status is tracked in [task-list.md](../task-list.md) and the
per-task handoffs; review disposition remains separate. P3-T01, P3-T02 and
P3-T03 are implemented and independently accepted; later rows remain planned. Dependencies
mean accepted predecessor interfaces/behavior, not permission to run the next row.
Identifiers are in intended execution order; no automatic progression is implied.

| ID | Deliverable | Depends on |
| --- | --- | --- |
| P3-T01 | Atomic store companion and in-memory authority contract | Approved plan; developer assignment |
| P3-T02 | Pure input/delta mutation rules | T01 |
| P3-T03 | Commit-result bus and local ordered publication | T01, T02 |
| P3-T04 | Executor proposal provenance and local results | T01, T03 |
| P3-T05 | Full-Task normalization/conflict migration | T02, T04 |
| P3-T06 | Materialization enrollment transaction protocol | T01, T03 |
| P3-T07 | Shared ordinary-send branch/orchestration | T02–T06 |
| P3-T08 | JPA transactional authority | T01–T03, T06, T07 |
| P3-T09 | Persisted baseline migration and writer fencing | T08 |
| P3-T10 | Committed replication/finalization | T03–T05, T08, T09 |
| P3-T11 | Shared ordered webhook dispatch | T03, T06, T08–T10 |
| P3-T12 | Authority topology/provisioning gate | T07–T11 |
| P3-T13 | Real multi-instance authority/failure evidence | T10, T12 |
| P3-T14 | Finite publication-gap reconciliation seam | T03, T07, T10 |
| P3-T15 | Generation/timeline reducer and immutable view | Phase 2; T02/T05 rules |
| P3-T16 | Public ordinary entry-point view/reliability adapter | T15 |
| P3-T17 | JSON-RPC production end-to-end slice | T07, T12, T14, T16 |
| P3-T18 | REST production end-to-end slice | T17 shared fixture accepted |
| P3-T19 | gRPC production end-to-end slice | T17 shared fixture accepted |
| P3-T20 | Compatibility, migrations and phase evidence gate | All preceding rows |

## Task conventions

Apply this checklist within each assigned task:

- [ ] Read its brief/contracts and preserve workspace changes.
- [ ] Add the specified behavioral tests and record the expected failure.
- [ ] Implement the bounded deliverable and run its listed checks.
- [ ] Save exact evidence/handoff, request separate review, then stop.

For every task: read only its brief, dependencies' public contracts/handoffs and
the cited design sections. Inspect status first. Add the specified behavioral
tests, run to establish the expected failure, implement the bounded deliverable,
then run its selector and affected existing regressions. Use latches, barriers and
injected clocks, not sleep-based races. Existing tests listed below must be kept.
New test class names are proposed files in the listed test package.

The commands below are **future implementation checks; none ran during planning**.
Use root Maven invocation with `-am` and fail-if-no-selected-tests disabled only
for upstream modules. Inspect Surefire XML in each target module: a successful
reactor with zero target tests or Docker skips is not acceptance. Prefer `test`
unless packaged app/Docker integration needs `verify`. Record artifact prerequisites
and exact executed suites. Do not write new evidence over root `test.log`.

Each handoff goes to `phase-3/handoffs/P3-TNN.md`; each separate Sol review goes
to `phase-3/reviews/P3-TNN-review.md`. Record changes, test names/counts/outcomes,
limitations, requested/observed settings and next developer action. Update only
that task's index status. No commit is implied.

### P3-T01 — Atomic store companion and in-memory authority

**Scope/dependencies:** Plan accepted; establish the atomic extension without
activating runtime 1.1 or changing legacy-only custom stores.

**Files/interfaces:** Create the six contract types shown above plus
`S/tasks/TaskMutationAuthority.java`, storage outcome exceptions and focused
`ST/tasks/AtomicTaskStoreContractTest.java`. Modify
`S/tasks/InMemoryTaskStore.java`; preserve `TaskStore` descriptors. A per-task
lock/compute must cover authoritative read, guard callback, allocation and replace;
`readSnapshot` returns a matching immutable pair. Define a commit-slot callback
for T03 that runs inside this boundary and has no network/user side effects.

**Acceptance:** A04/A05/A06: barrier-race two writers against revision/generation
G, exactly one accepts; callback rejection/failure changes nothing. Read/commit
pairs cannot tear. Separate task IDs do not serialize globally. A throwing commit
never releases a publishable slot. Custom legacy store compiles unchanged.

**Checks:**
```sh
mvn -B -ntp -pl server-common -am -Dtest=AtomicTaskStoreContractTest,InMemoryTaskStoreTest,TaskStoreExceptionTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** Atomicity, failure outcomes and additive SPI; no reliance on
ConcurrentHashMap thread safety alone. Freeze exact signatures before T02.

**Implementation brief:** Build only the atomic companion and in-memory adapter
against the interface block. Supply deterministic simultaneous-writer and rollback
tests. Do not route handlers/bus through it yet or add a fake default transaction
around arbitrary TaskStore.save.

### P3-T02 — Canonical input, status and artifact mutation planner

**Scope/dependencies:** T01. One pure planner shared by client ingress and agent
deltas; no handler, publication or live operation changes.

**Files/interfaces:** Create `S/tasks/TaskMutationPlanner.java`,
`PendingTaskInput.java` (request-local message/artifacts/config/identity), and
`MutationOrigin.java` (authenticated ingress classification). Test
`ST/tasks/TaskMutationPlannerTest.java`. Reuse shared model builders and artifact
merge helpers where they preserve roles/ranges; avoid changing legacy utility
semantics globally. Authority maps guard mismatch to existing
`TaskGenerationMismatchError`, invalid ownership/shape to existing parameter errors.

**Behavior/acceptance:** A05/A07/A10: message-at-1 bootstrap; later message then
each artifact chunk in list order, contiguous stamps; guard checked once before
the batch. Timeline records messages and status independently of legacy history;
artifact placement uses ranges, not invented timeline entries. Same-state status
advances once. Ignore client-supplied event/artifact generations. Resolve effective
card `userArtifacts`, tenant/task/context/authorization before admission and recheck
mutable task identity/terminal state in the transaction. Existing-task artifact-only
works; artifact-only creation fails. Test replacement/append/lastChunk, ingress
role normalization, both cross-origin collision directions, terminal rejection,
interrupted continuation and rollback of an invalid final batch element.

**Checks:**
```sh
mvn -B -ntp -pl server-common -am -Dtest=TaskMutationPlannerTest,AtomicTaskStoreContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** Exact selected-policy trace and no hidden partial effects.

**Implementation brief:** Implement the planner from architecture mutation rules
and selected G4/G6 policies. Example expected trace: opening M=1, A1=2, A2=3,
separately emitted S=4, summary still 4. A rejected batch yields no events/state.
Add no new protocol error or idempotency behavior.

### P3-T03 — Committed results and ordered local publication

**Scope/dependencies:** T01/T02. Route TaskManager deltas, cancellation status
and bus persistence through committed results; ordinary input setup remains T07.

**Files/interfaces:** Modify `S/tasks/TaskManager.java`,
`S/events/MainEventBusProcessor.java`, its callback/update result and
`S/requesthandlers/DefaultRequestHandler.doCancelTask`. Create
`S/events/CommittedTaskBatch.java`, `OrderedTaskPublisher.java` and
`ST/events/CommittedPublicationTest.java`. Retain event-processing semaphore/
queue cleanup and per-task push chains for the single-process profile; use the
exact post-commit Task snapshot. Local push chains alone do not establish
cross-node webhook ordering. Shared-store dispatch is gated by T11.

**Acceptance:** A05/A06: no stored-only stamp followed by unstamped original fan-out.
Gate a commit, verify subscriber/push/replication spies see nothing. Pause N after
commit before release; commit N+1; deliver N then N+1. Failed N reservation is
removed without a permanent hole. Terminal callback/queue closure runs after
all events in the committed batch; snapshots never allocate generations.

**Webhook admission seam for T11:** Hand committed batches, matching task
snapshots and all generation positions to the common webhook coordinator before
any sender invocation. Filtered/no-config positions advance delivery accounting
without a webhook. No bus, enrollment or finalization callback may dispatch around
the gate. Single-process chains remain an implementation of local ordering only.

**Checks:**
```sh
mvn -B -ntp -pl server-common -am -Dtest=CommittedPublicationTest,TaskManagerTest,TaskManagerSnapshotTest,MainEventBusProcessorExceptionTest,MainEventBusProcessorPushNotificationOrderTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** Commit/admission ordering, lock boundaries and callback
finality. T10 adds remote provenance; do not pretend a JVM lock solves clustering.

**Implementation brief:** Consume CommittedMutation everywhere this task touches.
Reserve under atomic authority, mark publishable only after commit, fan out outside
locks. Publish canonical objects and immutable snapshots; keep loss after process
crash explicit. Do not implement replay/outbox.

### P3-T04 — Proposal admission, provenance and executor-local completion

**Scope/dependencies:** T01/T03. Introduce result-bearing submission and carry
the immutable base/result envelope; T05 supplies full normalization.

**Files/interfaces:** Add `S/tasks/TaskProposal.java`, `ProposalResult.java`,
`ExecutorSubmissionService.java`, `ExecutionProposalContext.java`; add
`AgentEmitter.submitTask/refreshTask` and execution-local legacy sink registration.
Modify `S/agentexecution/RequestContext.java`, `S/events/EventQueueItem.java`,
`EventQueue.java`, `LocalEventQueueItem.java` and bus rejection handling. Use a
new internal proposal queue item; preserve old method descriptors. Add
`ST/tasks/ExecutorSubmissionTest.java` and queue admission tests.

**Behavior/acceptance:** A04/A05/A06: complete exactly once as committed/rejected/
failed with correlation ID; distinguish known non-commit from indeterminate.
Install the default handler's local sink before executor invocation. Unadapted
raw local full-Task admission without base/completion rejects synchronously.
Legacy full-Task calls retain invocation-start base even after acknowledged deltas.
Expected conflict never enters the bus's subscriber-error catch. Dispatch results
off event-loop/authority locks. Local admission/result observation deadlines use
existing Spi-style finite configuration patterns: proposed defaults 5s admission,
30s result observation, injected in tests. A result timeout keeps the correlation
and eventual in-process completion; it does not cancel/undo a commit or authorize
automatic retry. Refresh/rebuild from a rejection callback must not deadlock.

**Checks:**
```sh
mvn -B -ntp -pl server-common -am -Dtest=ExecutorSubmissionTest,AgentEmitterTest,AgentEmitterConcurrencyTest,EventQueueTest,MainEventBusProcessorExceptionTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** Additive API and synchronous admission vs asynchronous result
semantics. Inspect local lookup-by-correlation lifetime; no durable result index
is promised. Lost outcome evidence after crash stays indeterminate.

**Implementation brief:** Implement the result/provenance plumbing and tests with
an injected rejecting authority. No observer receives executor-local conflict.
Document raw-queue and void-path migration in the handoff. Never turn enqueue
success into committed acceptance or use Task.generation as a proposal receipt.

### P3-T05 — Normalize full Task proposals safely

**Scope/dependencies:** T02/T04. Replace full-object overwrite on the proposal
path, including `addTask`, `emitEvent(Task)` and admitted raw proposals.

**Files/interfaces:** Extend `TaskMutationPlanner`, `TaskManager.saveTaskEvent`,
`AgentEmitter.taskBuilder`/submission integration and `S/tasks/ResultAggregator.java`
for committed result/legacy full-Task shape handling. Adapt
`S/version/VersionedOutputProjector.java` only where needed for per-subscriber
committed summary versus canonical delta selection; add
`ST/tasks/FullTaskProposalTest.java` and executable
`ST/tasks/ExecutorMigrationExampleTest.java`. `refreshTask` returns the stored
Task and revision together. Do not use a mutable execution revision for proposals.

**Acceptance:** A04/A05/A06/A07: pause after base capture, accept client input,
then reject stale full Task with no overwrite/generation/failure. Repeat after
the same executor's acknowledged delta. Matching-base proposal preserves history,
timeline and USER artifacts; ignore supplied generations. Initial full Task
deduplicates opening message by ID and preserves supported legacy bootstrap
history without fabricating historical timeline. Nonterminal changes use status
then agent artifacts; terminal proposals apply final artifacts before terminal
status. No-op emits nothing. Metadata-only proposal emits one status observation,
preserves current message ID/occurrence count, and emits no message update.

**Checks:**
```sh
mvn -B -ntp -pl server-common -am -Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** Immutable provenance, normalization order and message counts.

**Implementation brief:** Implement architecture's five full-Task rules exactly.
Test stale external input and stale self-delta separately. Provide working initial
creation, fresh-snapshot rebuild, conflict, and delta examples; describe the changed
custom-executor semantics without claiming arbitrary replacement compatibility.

### P3-T06 — Enrollment before notification eligibility

**Scope/dependencies:** T01/T03. Build the enrollment/materialization resource
protocol for T07; do not implement Phase 4 delivery-version management APIs.

**Files/interfaces:** Add `S/tasks/TaskEnrollmentCoordinator.java`; modify
`PushNotificationConfigStore` through additive companions/defaults only where
needed, `InMemoryPushNotificationConfigStore`, and `S/tasks/BasePushNotificationSender.java`
at its actual eligibility lookup seam. Add
`ST/tasks/TaskEnrollmentCoordinatorTest.java`. JPA participation follows in T08.

**Acceptance:** A03/A06/A10/A12: enroll first and ensure read-after-write config
visibility before task commit/first notification. Combined transaction when
available; otherwise provisional enrollment invisible until task existence.
Config failure precedes task mutation. Definitive task non-commit cleans/expires
only the hidden **new-task** provisional config owned by this attempt. This
cleanup rule never applies to visible existing-task enrollment. Indeterminate commit retains enrollment and reconciles using existing
task/internal correlation evidence; absence from one read is not proof of failure.
No-task direct Message never starts enrollment. No fallible second install after commit.

**Existing-task independent-store contract (A06/A10/A12):** A successful config
write is already observable when the task exists. A later input guard rejection or
known input non-commit does not imply config rollback. Report that partial effect
with known task/config IDs through supported existing error data/details and local
failure diagnostics, preserving the original typed input error. Add no protocol
error kind, receipt or required wire field. Do not blindly delete or restore the
config: another operation may have changed it since enrollment. Indeterminate
input outcome retains enrollment and reports uncertainty. Only the shared-
transaction adapter may claim input/enrollment all-or-nothing behavior.

Add independent-store cases for existing-task guard failure, definite commit
failure and indeterminate commit, asserting visible config/known IDs/no task
mutation on rejection. Interleave a concurrent config replacement after enrollment
before the failure; prove its value survives with no delete/restore compensation.
Keep config-store failure-before-input and unaffected-config tests separate from
new-task provisional orphan expiry. The implementation brief and review must
cover this matrix as well as initial materialization.

**Checks:**
```sh
mvn -B -ntp -pl server-common -am -Dtest=TaskEnrollmentCoordinatorTest,PushNotificationSenderTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** Independent-store failure matrix and eligibility; no false
cross-store atomicity or early externally visible provisional config.

**Implementation brief:** Provide a prepare/commit-outcome/cleanup resource used
by materialization, not duplicated handler calls. Test config-first visibility,
orphan expiry, definite failure and uncertainty with store spies and injected time.

### P3-T07 — One ordinary-send orchestration and no-task branch

**Scope/dependencies:** T02–T06. Override input companions and unify message-only
legacy entry points with the same authority when upgraded task mode is enabled.

**Files/interfaces:** Modify `S/requesthandlers/DefaultRequestHandler.java`
(`onMessageSend`, stream, `initMessageSend`, execution registration/cleanup),
`SimpleRequestContextBuilder`, `RequestContext`, `TaskManager` and enrollment
integration. Add `ST/requesthandlers/OrdinaryInputMutationTest.java` and
`DirectMessageMaterializationTest.java`. Keep RequestHandler default behavior for
unmodified custom handlers; never make message() nullable to handle artifact-only.
Add a shared read-projection helper for independent historyLength/timelineLength
windows on send/get/list; apply it to returned views, never canonical state.
Update InMemoryTaskStore list transformation too. Retain currentGeneration
long-poll rejection. Test omitted/zero/finite timeline limits in this task.

**Acceptance:** A03/A05/A10/A12: authorize/validate before invocation; hold new
opening input only in request-local PendingTaskInput. First task/status/artifact
output atomically materializes pending batch plus normalized executor output.
An initial snapshot covers the batch once; subsequent delivery starts after it.
No-task direct Message discards pending artifacts/config and cleans queues, even
with returnImmediately. No temporary task-and-delete approach. Reject late Task
after completed direct branch. Failure before materialization leaves no record;
existing-task Message reply preserves previously committed guarded input. For
existing task, enrollment failure precedes input commit. Reread canonical state
under authority rather than trusting authorization's earlier read.

**Existing-task failure reporting:** Preserve T06's visible independent-store
enrollment effect when the subsequent guarded input fails. Propagate the existing
typed input error plus known task/config IDs through supported error details/local
diagnostics; do not claim that an unchanged task means nothing else persisted.
Never send this branch through new-task orphan cleanup. Test guard rejection and
commit failure after enrollment, with a concurrent config update. Unary and
streaming adapters retain the same distinction.

**Checks:**
```sh
mvn -B -ntp -pl server-common -am -Dtest=OrdinaryInputMutationTest,DirectMessageMaterializationTest,DefaultRequestHandlerTest,DefaultRequestHandlerReferenceTaskAuthorizationTest,RequestContextTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** Unary/streaming branch equivalence, cleanup, initial snapshot
watermark and legacy blocking/history/finality. All-binding wire evidence is later.

**Implementation brief:** Route both ordinary input shapes through one setup.
Test a direct Message against a task-creating response using task/config/publisher/
replication spies. Keep returnImmediately from manufacturing a Task; readiness
alone cannot settle the executor's branch. Do not activate live input delivery.

### P3-T08 — Transactional JPA authority and finalization enrollment

**Scope/dependencies:** T01–T03/T06/T07. Implement the same atomic contract against
database state; do not infer atomicity from `@Transactional` on separate get/save.

**Files/interfaces:** Modify `J/JpaDatabaseTaskStore.java`, `JpaTask.java`; create
`JpaTaskMutationTransactions.java` and schema migration resources for independent
revision/writer epoch/migration marker columns. Adapt existing authorization/
ownership repositories to participate in creation. Join the existing
`extras/push-notification-config-store-database-jpa/src/main/java/org/a2aproject/sdk/extras/pushnotificationconfigstore/database/jpa/JpaDatabasePushNotificationConfigStore.java`
transaction where both stores share it; otherwise use T06's independent-store
protocol. Add `JT/JpaAtomicTaskMutationTest.java` and
`JpaMaterializationEnrollmentTest.java` alongside existing tests.

**Behavior/acceptance:** A05/A06/A08/A12/A13: lock or conditional-update the row
and creation uniqueness in one transaction, compare guard/revision, plan all
changes, persist, then expose the committed result after transaction success.
Protocol generation is separate from ORM/store revision. Concurrent creation
and concurrent update both need tests. Rollback yields no visible event/config;
commit uncertainty yields an indeterminate result. Immediately terminal creation
has config available before any AFTER_SUCCESS finalization observer can notify.
Preserve existing ownership and list/query indexes. Carry T07's independent
history/timeline windows through JPA list/get projections. No default durable outbox.

**Enrollment atomicity evidence:** Pair the shared-transaction test (both config
and input roll back on failure) with T06's independent-adapter test (config remains
observable after guard/commit failure, reported with task/config IDs). Concurrent
config replacement must survive. Do not market independent stores as all-or-nothing.

**Checks:**
```sh
mvn -B -ntp -pl extras/task-store-database-jpa,extras/push-notification-config-store-database-jpa -am -Dtest=JpaAtomicTaskMutationTest,JpaMaterializationEnrollmentTest,JpaDatabaseTaskStoreTest,JpaTaskOwnershipTest,JpaPushNotificationConfigStoreTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** Real transaction scope including commit completion, uniqueness
race, enrollment and after-commit callbacks. H2 success is not cluster evidence.

**Implementation brief:** Implement AtomicTaskStore with a transactional helper
whose caller receives success only after commit. Test the store contract against
JPA and immediate-terminal enrollment. Do not preserve the unsafe merge-only
write path for migrated shared tasks.

### P3-T09 — Migration baseline and enforced writer transition

**Scope/dependencies:** T08. Install coordinated drain/upgrade safeguards before
enabling lazy/task baseline migration on shared persisted data.

**Files/interfaces:** Add `J/JpaTaskMigration.java`, `JpaWriterEpoch.java`,
schema/epoch resources, the one-time process-local legacy baseline in
`S/tasks/InMemoryTaskStore.java`, and `JT/JpaTaskMigrationTest.java`,
`JpaWriterFencePostgresTest.java` using the existing PostgreSQL resource/profile
patterns. Update `docs/content/dev/extra/storage.md` and
`docs/content/dev/extra/replicated-queue-manager.md` with the drain/fence/snapshot
restore procedure. Migration metadata lives outside whole-Task JSON.

**Acceptance:** A08/A13: after old writers are demonstrably denied, atomically
establish old task generation 1 once, retain old history/artifacts and absent-role
AGENT meaning, do not invent past timeline/chunk order. Restart retains the persisted JPA baseline; in-memory storage still loses tasks
on restart and only stabilizes each task for that process lifetime.
A migrated row missing canonical generation data fails as corrupt, not reinitialized.
Use distinct old/new DB and broker identities, prove revoked old DB write fails,
and persist/check writer epoch plus per-task marker on new writes. A JSON marker
that old binaries ignore is not an enforcement fence. Embedded mode requires
exclusive process/storage ownership. Never claim mixed-binary rolling writes.
Rollback requires outage plus consistent pre-upgrade task/config snapshot and
broker boundary, with explicit loss of later changes.

**Checks:**
```sh
mvn -B -ntp -pl extras/task-store-database-jpa -am -Dtest=JpaTaskMigrationTest,JpaWriterFencePostgresTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Evidence:** Old-credential attempted write denied by PostgreSQL itself; exact
DDL/GRANT/REVOKE or equivalent operational commands retained with test identities,
restart/migrated-corruption assertions. Run against a disposable fixture only.
If Docker or credential-control evidence is unavailable, report this task's
enforcement check blocked; do not migrate shared live data as a workaround.

**Review boundary:** Storage enforcement vs merely cooperative markers, data
preservation and rollback restrictions. T13 adds broker/real cluster evidence.

**Implementation brief:** Implement migration and test denied old writes on an
isolated DB. Document fencing as a required deployment precondition, not a new
server capability inferred from schema version. Preserve legacy-only deployments.

### P3-T10 — Committed replication and terminal batch delivery

**Scope/dependencies:** T03–T05/T08/T09. Separate trusted committed delivery from
executor proposals and prevent second mutation of shared state.

**Files/interfaces:** Modify `R/ReplicatedEventQueueItem.java`,
`ReplicatedQueueManager.java`, `ReplicationStrategy.java`, and
`extras/queue-manager-replicated/replication-mp-reactive/src/main/java/org/a2aproject/sdk/extras/queuemanager/replicated/mp_reactive/ReactiveMessagingReplicationStrategy.java`.
Adapt `extras/common/src/main/java/org/a2aproject/sdk/extras/common/events/TaskFinalizedEvent.java` and JPA emission. Keep extras/common independent of server-common: add an
opaque committed-envelope companion/overload while preserving old constructors;
serialize typed internal batch data in the replication module, with no dependency
cycle. Add
`RT/CommittedReplicationTest.java`, extend ReplicatedQueueManagerTest and reactive
strategy serialization tests. Keep the existing legacy-only replication profile.

**Acceptance:** A05/A06/A13: internal envelope carries canonical batch/range,
epoch and stable identities. Validate epoch/provenance before delivery; dedup
same identity/payload, reject conflicting payloads, buffer out-of-order batches.
Shared-store receipt never calls mutate/save again. Reject delayed old unversioned
replication after migration. A full Task committed envelope is not a local proposal.
JPA finalization forwards the original stamped terminal batch before poison pill;
do not synthesize another status generation or drop terminal artifacts. Keep
callbacks/completion handles process-local. Remote subscriber copies never
independently dispatch webhooks. Originating writer delivery enters T11's common
ordered lane; suppressing remote copies cannot leave independent local chains
free to overtake. Bus and finalization paths use the same dispatch identity.

**Checks:**
```sh
mvn -B -ntp -pl extras/queue-manager-replicated/core,extras/queue-manager-replicated/replication-mp-reactive -am -Dtest=CommittedReplicationTest,ReplicatedQueueManagerTest,ReactiveMessagingReplicationStrategyTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** Epoch trust, serialization, remote ordering/dedup and terminal
closure. No exactly-once network delivery or client idempotency claim.

**Implementation brief:** Replicate the committed result, not original queue
input. Test duplicate finalization, delayed legacy records and reversed delivery.
Preserve supported legacy-only setups; reject clustered 1.1 on replicated local
memory rather than advertising a guarantee it cannot enforce.

### P3-T11 — Shared ordered webhook dispatch across writers

**Scope/dependencies:** T03/T06/T08–T10. One separately reviewable dispatch
coordination task. Implement ordering metadata and admission, not durable payload
recovery, an executor-owner lease, or a new public protocol.

**Files/interfaces:** Create `S/tasks/OrderedWebhookDispatcher.java`,
`WebhookDispatchGate.java`, `InMemoryWebhookDispatchGate.java` and
`ST/tasks/OrderedWebhookDispatcherTest.java`. Modify MainEventBusProcessor's push
admission and `S/tasks/BasePushNotificationSender.java` so asynchronous sends remain
inside lane ownership until their dispatch stage settles; preserve existing public
sender signatures through an additive adapter. Add `J/JpaWebhookDispatchLane.java`,
`JpaWebhookDispatchGate.java`, schema resources and
`JT/JpaWebhookDispatchGateTest.java`. Modify TaskMutationAuthority and the JPA
mutation/migration helpers to initialize lane metadata atomically. Integrate
ReplicatedQueueManager finalization
and the shared committed-batch dispatch seam. No server/extras dependency cycle.

**Mechanism:** All nodes writing the same task use one shared-store per-task
webhook lane. Its durable metadata contains the next canonical generation,
nullable in-flight range, unique holder token and validated storage epoch; event
payloads stay in bounded volatile buffers. A separate row/transaction from the
task mutation row atomically claims only the next range and records its holder
before any HTTP attempt. Initialize next generation to 1 in the task-creation
transaction; for migrated tasks initialize to the accepted baseline plus 1 under
T09's fence, with no pre-upgrade replay. Never initialize from whichever replicated
batch happens to arrive first. N+1 may commit normally, but cannot claim/start
webhook dispatch while N owns the lane. A process-local chain alone is insufficient.
The claim survives a connection/process failure; there is no expiry-based takeover.

Freeze this additive internal contract in executable gate tests:
```java
record WebhookDispatchClaim(String taskId, long writerEpoch, String holderToken,
        long firstGeneration, long lastGeneration) {}
interface WebhookDispatchGate {
    Optional<WebhookDispatchClaim> claim(String taskId, long writerEpoch,
            long firstGeneration, long lastGeneration);
    boolean complete(WebhookDispatchClaim claim);
    boolean retireUnclaimedGap(String taskId, long writerEpoch,
            long expectedNextGeneration, long throughGeneration);
}
```
Claim is an atomic compare of epoch, no active holder and exact next generation;
no claim means no HTTP side effect. Complete compares exact holder/range/epoch and
advances to last+1 only after dispatch work has settled/quiesced. Gap retirement
atomically checks the expected next value and absence of a holder, records its
operational loss range and advances past it. Recovery of a claimed abandoned lane
is an explicit fenced/quiesced administration procedure using conditional holder/
epoch updates, never this unclaimed-gap operation. Holder tokens and cursor state
are server/store-local, not new protocol fields or client receipts.

The holder dispatches the committed batch in generation order to eligible configs,
using each event's matching committed task snapshot and existing version projection.
For a given config, finish its attempt stage before starting a later generation.
A timeout/failure does not prove non-delivery. The baseline makes at most one
ordered attempt per eligible event/config; no automatic late retry may overtake
later generations. Do not replay retired positions. This concerns SDK dispatch
attempt order, not remote processing, receipt order or exactly-once delivery.
After all attempts settle (or are retired under this best-effort policy), compare
holder/epoch to advance the cursor and release. Filtered/no-config generations
still advance accounting. Ordinary bus and JPA terminal paths share this identity,
so neither can duplicate dispatch. Replicated subscriber fan-out never sends.

**Failure/bounds:** Do not hold a task-mutation lock or authority transaction
across network I/O. Use a 30-second local attempt deadline, 10-second missing-range
admission deadline, and maximum 256 events/4 MiB pending per task; test injected
time. A timed-out observer must not release a lane whose worker may still initiate
requests. Cancel/quiesce the worker first. An unclaimed missing range may be
atomically retired after its gap deadline (with an operational loss record), only
if no holder claimed it; a late origin then fails the cursor check before sending.
An already claimed abandoned range stays blocked until the holder process/worker
is demonstrably quiesced/fenced, then explicit operational recovery retires its
uncertain range and advances the cursor. Database/broker credential revocation
alone is not proof an old outbound HTTP worker stopped. No automatic reassignment
while that worker could resume. Pending payloads can be dropped with an explicit
loss record on capacity/deadline; retain lane/cursor metadata so late work cannot
send backwards. Task mutation and subscriber delivery remain independent.

If the sender adapter cannot bound/quiesce its asynchronous work, fail the shared
ordered-dispatch profile clearly rather than falling back to independent chains.
Losing payloads/attempt results on crash is the accepted best-effort gap. Lane
metadata is not a payload outbox and promises no durable delivery/retry. Document
the operational availability tradeoff and how safe recovery proves quiescence.

**Acceptance:** A06/A12/A13 in Phase 3 scope: two gate instances sharing a DB cannot
claim overlapping/out-of-order ranges. Pause N after claim/before HTTP; N+1 can
commit but cannot dispatch. After releasing N, attempts start N then N+1. Prove
duplicate bus/finalization admission, filtered generations, known failure, uncertain
attempt, no late retry, missing unclaimed range, bounded buffering and abandoned-
claim recovery. A stale/retired holder or batch cannot send after cursor advance.
T13 supplies the real two-node dispatch proof, distinct from subscriber ordering.

**Checks:**
```sh
mvn -B -ntp -pl server-common,extras/task-store-database-jpa -am -Dtest=OrderedWebhookDispatcherTest,JpaWebhookDispatchGateTest,MainEventBusProcessorPushNotificationOrderTest,PushNotificationSenderTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** One gate across every writer/finalization path; no network
side effect before a valid claim, no task lock during HTTP, no unsafe takeover or
retroactive retry, and honest dispatch-order/crash-gap/availability limits.

**Implementation brief:** Implement the shared claim/cursor gate and bounded
best-effort dispatcher. Preserve public sender compatibility. Prove the pause
before network dispatch and stale-holder recovery using controlled stages. Record
the availability tradeoff for review; do not substitute a durable outbox or an
unfenced expiring lease. Stop for independent review before cluster evidence.

### P3-T12 — Validate authority topology before exposing 1.1

**Scope/dependencies:** T07–T11. Add explicit provider capability/config checks;
do not change exact protocol selection or make an ad hoc runtime fallback.

**Files/interfaces:** Add `S/tasks/TaskAuthorityCapabilities.java` and
`TaskAuthorityConfiguration.java`; integrate with
`reference/common/src/main/java/org/a2aproject/sdk/server/common/quarkus/DefaultProducers.java`,
`VersionRouter.java`, `S/version/InterfaceRegistrationResolver.java` and binding
registration/agent-card validation seams. Add
`reference/common/src/test/java/org/a2aproject/sdk/server/common/quarkus/TaskAuthorityConfigurationTest.java`.
Non-CDI builder construction needs the same validation before serving a 1.1 route.

**Acceptance:** A01/A02/A04/A13: single-process memory and supported shared-JPA
profile pass; custom legacy TaskStore + legacy-only registrations still works.
Replicated queues + independent in-memory stores, missing atomic companion,
unmigrated/unfenced shared store and mismatched writer epochs fail clearly before
advertising/serving 1.1. Effective tenant/binding registration must be checked;
do not hide a rejected configuration by silently narrowing/changing versions.
All three protocol writers sharing upgraded tasks use the authority. A mere
capability declaration is not proof that a cluster profile has passed T13.

**Shared webhook profile check:** The shared-JPA profile requires T11's shared
gate on every bus/finalization sender. Reject missing/mismatched gates or an adapter
that cannot bound/quiesce its worker; no fallback to independent push chains.
Document explicit abandoned-lane recovery and its availability limitation.

**Checks:**
```sh
mvn -B -ntp -pl reference/common -am -Dtest=TaskAuthorityConfigurationTest,VersionRouterTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** Plain Java/CDI and dynamic registration coverage, preservation
of legacy-only deployments, precise experimental cluster scope.

**Implementation brief:** Validate actual authority topology, not whether a
replication dependency is present. Use additive store/provider capabilities and
fail before card exposure. Document how a custom store opts into the contract.

### P3-T13 — Real shared-authority concurrency and failover evidence

**Scope/dependencies:** T10/T12. Verify the selected PostgreSQL + Kafka + two-node
profile with actual processes; this task may correct that integration only.

**Files/interfaces:** Extend the existing
`extras/queue-manager-replicated/tests-multi-instance/quarkus-common/src/main/java/org/a2aproject/sdk/extras/queuemanager/replicated/tests/multiinstance/common/MultiInstanceReplicationAgentExecutor.java`,
both `quarkus-app-*/src/main/resources/application.properties`, Docker fixtures,
and `tests/src/test/java/org/a2aproject/sdk/extras/queuemanager/replicated/tests/multiinstance/`.
Add `Phase3AuthorityMultiInstanceTest.java`; reuse shared PostgreSQL/Kafka and
existing container lifecycle. Test instrumentation uses internal barriers only.

**Acceptance:** A05/A06/A08/A13: competing guarded writers on different nodes
accept one; every accepted mutation has one durable sequence and one webhook
eligibility. Pause N's publisher on one node while N+1 commits elsewhere;
subscribers never see reversed generations. Duplicate/reordered broker records
do not mutate again. Kill after commit before publish; stored state survives and
the documented gap remains recoverable by explicit authorized GetTask (T14 adds
bounded active-stream handling). Restart cannot reset generation. Deny the old
broker producer identity, inject already-delayed legacy/obsolete-epoch records,
reject obsolete execution output; a storage epoch must not be confused with a
future live executor-owner lease. Do not implement Phase 5 remote live routing.

**Separate cross-node webhook-order trace:** Pause generation N on node A after
its shared lane claim and before HTTP dispatch; commit N+1 on node B. The receiver
must see no N+1 attempt while N is paused. Release A, then record N/N+1 attempt
order and matching event/snapshot generations. Repeat with finalization, filtered
positions and duplicate remote records. Subscriber ordering alone is insufficient.
Crash/quiesce the holder and perform documented lane recovery; prove retired/old
work cannot dispatch backwards. Exercise missing unclaimed range and capacity/
deadline loss without fabricating task mutations or claiming durable delivery.
Record lane claims/cursor/holder transitions and HTTP attempt starts independently
from receiver processing/completion, which is not ordered by this SDK contract.

**Checks, separate finite invocations:**
```sh
mvn -B -ntp -pl extras/queue-manager-replicated/tests-multi-instance/quarkus-app-1,extras/queue-manager-replicated/tests-multi-instance/quarkus-app-2 -am -DskipTests package
mvn -B -ntp -pl extras/queue-manager-replicated/tests-multi-instance/tests -am -Dtest=Phase3AuthorityMultiInstanceTest -Dsurefire.failIfNoSpecifiedTests=false verify
```
**Evidence:** App artifact/source hashes, both live node IDs, DB/broker versions,
test names and barriers, distinct accepted/rejected outcomes, denied old producer
record and crash/restart logs. Verify Docker tests executed with zero required
skips. If fixtures need installed sibling artifacts, record a bounded targeted
`install -DskipTests` preparation; no broad build loop.

**Review boundary:** Independent Sol review of the topology claim and actual
execution evidence. Missing Docker/infrastructure blocks clustered acceptance;
do not replace it with mocks or claim other DB/cluster combinations verified.

**Implementation brief:** Extend the existing two-node fixture for ordinary
mutation authority. Keep live-owner failover explicitly in Phase 5. If shared-JPA
cannot meet ordering/fencing, stop with a focused alternative-authority decision
instead of silently removing supported clustered behavior.

### P3-T14 — Bounded detection of publication loss

**Scope/dependencies:** T03/T07/T10. Establish the minimum authority/publisher
reconciliation seam needed for the baseline crash-gap claim. No retained-cursor,
long-poll or general replacement-snapshot controller implementation. Minimal
no-cursor snapshot/stream coordination used by the existing subscription and
ordinary-send paths belongs here so the mutation evidence has a valid baseline.

**Files/interfaces:** Extend OrderedTaskPublisher, main bus lifecycle/child-queue
registration and TaskManager snapshot reads; create
`S/events/PublicationReconciler.java`, `ST/events/PublicationGapRecoveryTest.java`.
Register/buffer a local delivery sink before reading its authoritative snapshot
at G, then discard covered events and deliver only generations greater than G.
Coordinate initial terminal checks and detach on rejection. Test attach-versus-commit
barriers locally and with an injected remote delivery. This supplies no replay
range or repeated-snapshot guarantee. Use existing subscriber error channels
and cancellation cleanup; binding-specific
error integration is tested in T17–T19.

**Acceptance:** A06 and the Phase 3 part of A09: injected-clock test loses the
terminal publication with **no later mutation**. Poll authoritative head per active
task, detect stranded watermark and close only affected observations using the
binding's existing failure path, leaving explicit GetTask to retrieve final state.
Document this as retry/resync needed, not a new protocol retryability guarantee.
No speculative replacement Task exemption from equal-generation disablement.
Use architecture defaults: 5s reconciliation interval, 5s authority-read deadline,
10s reorder-gap deadline, 256 events/4 MiB maximum buffered per observation.
Test absent intermediate event, failed authority read, full buffer, slow demand,
late stale arrival and terminal cleanup. Filtered old-version generations still
advance internal delivery accounting; no false gaps or starvation. Recovery
creates no mutation, task failure, webhook or replication. Publish the resulting
bound, including scheduler/read deadline, rather than promising instantaneous repair.

**Checks:**
```sh
mvn -B -ntp -pl server-common -am -Dtest=PublicationGapRecoveryTest,CommittedPublicationTest,ProjectingPublisherTest,MainEventBusProcessorPushNotificationOrderTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** Finite resource/liveness behavior and explicit G3 limits.
If a binding has no legal existing stream failure path, gate that integration;
do not invent a new StreamError code or claim a same-generation snapshot is safe.

**Implementation brief:** Add periodic/coalesced authority comparison independent
of event arrival. Exercise lost final event deterministically. Choose the accepted
bounded-close/GetTask alternative; leave replay and reliable resync to Phase 4.

### P3-T15 — Generation/timeline reducer and immutable TaskView

**Scope/dependencies:** Phase 2 evidence types; T02/T05 semantics. Keep explicit
1.0 ClientTaskManager behavior isolated and covered.

**Files/interfaces:** Create `C/ClientTaskReducer.java`, `TaskView.java`,
`ViewSync.java`, `HistoryCoverage.java` and
`CT/ClientTaskReducerTest.java`. Retain ClientTaskManager for legacy adaptation.
TaskView contains nullable Task, ViewSync, nullable throughGeneration and separate
history/timeline coverage; coverage stores requested limit, nullable known returned
range, and WINDOW_ONLY/LEGACY_GAPS/UNKNOWN_SKIPPED/NOT_ASSERTED flags. No FULL flag.

**Behavior/acceptance:** A05/A07 and reducer portions of A09/A10: authoritative
snapshot establishes represented state at G, never lifetime history. Contiguous
message/status/artifact events preserve exact stamps, origin/ranges/chunks,
metadata and message occurrence counts. No-baseline suffix stays NO_BASELINE;
do not synthesize complete TaskEvents. Snapshots replace returned windows;
historyLength/timelineLength zero/finite/default-unknown are independent. Unknown
timeline kinds with known generations count for sequence accounting but mark
semantic uncertainty; do not fabricate a missing-generation gap. Unknown stream
payload without generation can leave a gap. Preserve last safely interpreted
as-of state on ambiguity; late stale events do not append history/chunks again.
Consecutive equal events trigger reliability disablement even if their state
application is deduplicated. Metadata-only carried status message with the same
identity is not a newly authored occurrence.

**Checks:**
```sh
mvn -B -ntp -pl client/base -am -Dtest=ClientTaskReducerTest,ClientTaskManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```
**Review boundary:** State completeness vs generation accounting, immutable views,
legacy callback preservation and no fabricated migration provenance.

**Implementation brief:** Implement the architecture transition table as a pure
reducer. Include snapshots with skipped timeline generations, duplicates/stale
chunks and metadata-only status identity. No network recovery or server generation
allocation. A migration gap is only LEGACY_GAPS when evidence actually says so.

### P3-T16 — Public ordinary entry points, views and reliability enforcement

**Scope/dependencies:** T15. Make state/uncertainty usable through the public
production Client, adapting the already-implemented input transport companions.

**Files/interfaces:** Add `C/TaskObservation.java`, `ObservationUpdate.java`,
`ObservationEnd.java`, an internal `OrdinaryInputObservation.java` and
`GenerationReliability.java`; modify `Client.java` and `ClientInputPolicy.java`.
Add `CT/OrdinaryInputObservationTest.java`, `GenerationReliabilityTest.java`.
Implement the proposed `sendInputObserved` method without changing existing
sendInput/sendMessage/subscribe signatures or ClientEvent's three subtypes.

**Interfaces/behavior:** TaskObservation exposes updates()/ready()/completion()/
currentView()/cancel()/close() as in architecture. ObservationUpdate contains a
nullable known StreamingEventKind plus immutable TaskView; unknown frames produce
view-only invalidation here, no invented ClientEvent. Unary wraps one decoded
TransportResult; streaming consumes TransportFrame and its skipped evidence before
legacy adaptation. Direct Message completes with no Task baseline. Existing callback
sendInput shares the reducer but retains known event shapes and exactly-one
error-or-null terminal convention; no unknown/view-only callback is fabricated.

**Acceptance:** A04/A05/A07 and client portions of A09: generation 0/equal consecutive
same-task events disable ordering/guard/long-poll reliance for the remainder of the
interaction. Keep the taint across transport reconnect; no automatic reset from a
snapshot. Because a normative interaction-reset boundary is unresolved, retain
the known task's taint conservatively for the same Client instance's later calls;
do not advertise a fresh Client as proof of normative reset. Reject a subsequent
explicit guard locally before I/O when tainted; never drop it. Unsupported future
long-poll is still rejected. Keep selected version immutable and never resend or
rebase on mismatch/timeout/uncertainty. Bound demand/buffers/deadlines, preserve
the final view, complete once on cancel/error/normal races, and never call cancelTask.
Callback adapters must not turn partial state into a synthetic complete TaskEvent.

**Existing selected-1.1 entry points are mandatory integration scope:** Route all
sendMessage overloads (unary and ordinary streaming), callback sendInput,
sendInputObserved, and existing no-cursor subscribeToTask through the same reducer
and Client-owned task reliability registry. No selected-1.1 ClientTaskManager
branch may bypass stamp/timeline reduction or taint propagation. Convenience
overloads delegate to these common paths. Preserve each established callback/
completion contract and ClientEvent shapes; explicit 1.0 keeps its historical
ClientTaskManager/history/decoding path.

Use sendInput/streamInput decoded companions for selected-1.1 sendMessage. For
ordinary subscribeToTask, each built-in exposes its decoded stream via existing
VersionedClientTransport.observe **only when lastSeenGeneration is null**. Retain
TransportFrame skipped evidence through both telemetry wrapper families. Non-null
cursors still reject before I/O; do not implement public Client.observe, replay or
Phase 4 recovery. A custom versioned provider whose optional observe default is
unsupported can retain its callback subscription feeding known events through the
shared reducer; conservatively mark unavailable skipped-evidence coverage
NOT_ASSERTED and a baseline-bearing view INCOMPLETE (NO_BASELINE if absent),
rather than claiming synchronization. Observed zero/equal
generations still taint that Client/task. Do not unwrap or bypass wrappers.
The base adapter/default/wrapper tests belong here; built-in no-cursor transport
implementations and production evidence belong to T17–T19.

Create `CT/ExistingOrdinaryEntryPointReductionTest.java`. Parameterize the existing
routes: sendMessage unary, sendMessage streaming, ordinary subscribeToTask,
callback sendInput and sendInputObserved. Check reduced generation/timeline,
artifact origin/chunks and message occurrence counts. For every applicable route,
receive generation zero and separately consecutive equal-generation events, then
invoke guarded sendInput on the same Client/task; assert local rejection before
any new network I/O. Unary responses retain known task reliability evidence across
calls, without claiming a reset. Verify all explicit-1.0 routes retain legacy
behavior. These requirements extend the brief and independent review boundary.

**Checks:**
```sh
mvn -B -ntp -pl client/base,extras/opentelemetry/client,extras/opentelemetry/client-propagation -am -Dtest=OrdinaryInputObservationTest,GenerationReliabilityTest,ExistingOrdinaryEntryPointReductionTest,ClientTaskReducerTest,Phase2ClientInputTest,Phase2InputTerminalTest,ClientTransportPolicyTest,VersionedWrapperTest,OpenTelemetryClientTransportTest,Phase2PropagationTest -Dsurefire.failIfNoSpecifiedTests=false test
```
Confirm VersionedWrapperTest executes in both wrapper modules and record the
module-qualified results, rather than counting one same-named suite twice.
No extra transport capability is advertised for cursor/live/long-poll support.

**Review boundary:** Additive public API, conservative reliability lifetime,
wrapper interception, unary skipped evidence and unchanged legacy behavior.

**Implementation brief:** Expose ordinary send's TaskView using the recommended
observation contract and integrate existing selected-1.1 sendMessage/subscription
callbacks. Reuse sendInput/streamInput and the narrowly supported no-cursor observe
adapter; every cursor request remains unsupported. Test zero/equal generation
then guarded call causes zero additional network requests, unknown frames remain
cancellable, and direct Message never gains a fabricated Task.

### P3-T17 — JSON-RPC production end-to-end mutation slice

**Scope/dependencies:** T07/T12/T14/T16. First complete binding slice, including
shared scenarios for REST/gRPC. No separate binding-specific mutation engine.

**Files/interfaces:** Modify
`transport/jsonrpc/src/main/java/org/a2aproject/sdk/transport/jsonrpc/handler/JSONRPCHandler.java`
and relevant reference JSON-RPC routes only as needed. Reuse
`client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransport.java`; correct it only for a
demonstrated production-path defect. Create
`tests/server-common/src/test/java/org/a2aproject/sdk/server/apps/common/Phase3MutationScenarios.java`
(assertions/controlled-executor hooks), extend TestUtilsBean and paired-version
registrations, and create
`tests/multiversion/jsonrpc/src/test/java/org/a2aproject/sdk/tests/multiversion/jsonrpc/Phase3JSONRPCMutationTest.java`.
Add `Phase3ClientGenerationTest.java` in the existing client JSON-RPC test package
for invalid-generation peers the correct server should never emulate.

**Acceptance:** A03/A05/A07/A10 and relevant A04/A06/A12; the following is the
shared scenario contract for **each** of T17/T18/T19:

- Public production Clients race the same guard; exactly one accepts and one
  receives typed TaskGenerationMismatchError. Count ingress to exclude automatic
  retry/rebase. Read canonical stamps/timeline through send/get/list and the
  ordinary-send TaskObservation.currentView API.
- Message + multiple artifacts and existing-task artifact-only inputs; malformed
  final element, capability, role, ID, tenant/context errors cause no partial
  mutation. Raw malformed cases supplement DTO-valid public-client cases.
- Unary and ordinary streaming, both returnImmediately values: opening Message
  plus artifacts yields direct Message with no task/config/publication/replication,
  and queue cleanup. Contrast task-bearing output and existing-task direct reply.
- 1.0 and retained 0.3 writers share a task with 1.1 subscribers. New clients see
  message updates/generations/origin; old clients preserve shapes/history/finality,
  omit new fields/USER artifacts and make progress under finite filtered demand.
  Legacy full-Task shape is projected from committed results, never raw proposals.
- Immediately terminal inline config precedes notification eligibility; terminal
  full proposals deliver final artifacts before closure; metadata-only status
  preserves message occurrence count. Lost final publication closes the affected
  stream within the documented bound; explicit GetTask retrieves committed final
  state without allocating a mutation or invoking automatic resend.
- Controlled public-client zero/equal generations disable reliability and stop
  subsequent guarded I/O. Unknown stream/timeline variants retain uncertainty
  and cancellation; malformed known payloads/error envelopes still fail. Selected
  1.0 advanced inputs reject locally without switching version.

Remove only guards for implemented 1.1 ordinary guard/artifact/timeline options.
Retain 1.0, live, cursor, currentGeneration long-poll and delivery-version gates.
Enable timelineLength send/get/list only after T07/T08's shared read projections.

**Existing-entry-point evidence for every binding task (T17–T19):** Implement
built-in SPI observe for null-cursor ordinary subscription only. Through public
sendMessage unary/streaming and ordinary subscribeToTask, prove reduced stamps,
timeline/message counts and cross-entry-point zero/equal-generation taint leading
to local guarded-sendInput rejection before I/O. Run these alongside both input
APIs; a sendInputObserved-only test does not close the finding. Preserve wrapper
interception and explicit-1.0 behavior. The shared scenario contract includes this
matrix for REST and gRPC as well as JSON-RPC.

**Checks, each independently bounded:**
```sh
mvn -B -ntp -pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -pl tests/multiversion/jsonrpc -am -Dtest=Phase3JSONRPCMutationTest -Dsurefire.failIfNoSpecifiedTests=false verify
```
**Review boundary:** Separate Sol review of real public production/core paths,
request-error correlation and the shared fixture. Controlled peers alone do not
prove server integration. Freeze the reusable scenario helper at this boundary.

**Implementation brief:** Enable ordinary companion mutation in JSON-RPC; prove
every shared bullet over actual core/store. Reuse accepted mappings/exact-version
fixtures. Keep frozen/raw compatibility evidence independent of new-to-new tests.

### P3-T18 — REST production end-to-end mutation slice

**Scope/dependencies:** Accepted T17 shared fixture and its prerequisites. One
REST slice; shared-core corrections return to their owning review boundary.

**Files/interfaces:** Modify
`transport/rest/src/main/java/org/a2aproject/sdk/transport/rest/handler/RestHandler.java`
and existing reference/multiversion REST routes only as needed. Reuse the existing
client REST transport. Add
`tests/multiversion/rest/src/test/java/org/a2aproject/sdk/tests/multiversion/rest/Phase3RestMutationTest.java`
using Phase3MutationScenarios, and `Phase3ClientGenerationTest.java` in the client
REST test package for controlled peers. Preserve protobuf-JSON mapping policies.

**Acceptance:** A03/A05/A07/A10 and the full T17 shared scenario contract over
REST unary/SSE. HTTP 409 plus protocol details yields typed mismatch; status alone
does not invent a typed error. Verify artifact-only routing, version/tenant, int64
stamps, finite filtered SSE demand, direct Message no-task/config/replication and
cleanup. Zero/equal SSE generations stop next guarded ingress; malformed known
and error bodies fail. Unknown kinds stay usable/cancellable. Preserve frozen
explicit-1.0 shape and USER artifact hiding. Exercise existing REST failure path
for publication loss and explicit GetTask. No HTTP live exchange work.

**Checks:**
```sh
mvn -B -ntp -pl transport/rest,client/transport/rest -am -Dtest=RestHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -pl tests/multiversion/rest -am -Dtest=Phase3RestMutationTest -Dsurefire.failIfNoSpecifiedTests=false verify
```
**Review boundary:** REST admission guards, HTTP/SSE mapping and actual shared-core
evidence; no general removal of deferred-option protection.

**Implementation brief:** Execute the shared contract through production public
Client and actual REST routes. Retain 1.0 and deferred-option rejections. Verify
unary/streaming/returnImmediately and queue cleanup separately.

### P3-T19 — gRPC production end-to-end mutation slice

**Scope/dependencies:** Accepted T17 shared fixture and its prerequisites. No
duplex/live implementation or channel-policy redesign.

**Files/interfaces:** Modify
`transport/grpc/src/main/java/org/a2aproject/sdk/transport/grpc/handler/GrpcHandler.java`
and reference services only as needed. Reuse existing GrpcTransport. Add
`tests/multiversion/grpc/src/test/java/org/a2aproject/sdk/tests/multiversion/grpc/Phase3GrpcMutationTest.java`
and `Phase3ClientGenerationTest.java` in the client gRPC test package. Retain
SDK-owned channel options/lifecycle from the accepted Phase 1 migration report.

**Acceptance:** A03/A05/A07/A10 and every T17 shared scenario over unary and
server-streaming RPCs. Typed mismatch requires existing protocol ErrorInfo reason/
details, not generic gRPC status. Verify guarded artifacts, artifact-only routing,
metadata/tenant, long stamps, final artifact before closure and direct-Message
task/config/replication spies. Test observer cancellation, queue/channel cleanup
and exactly-one completion. Controlled zero/equal/unknown-oneof cases preserve
uncertainty/no retry. Explicit 1.0 cannot send advanced input. Lost final publication
uses existing gRPC failure mapping; explicit GetTask returns final committed state.

**Checks:**
```sh
mvn -B -ntp -pl transport/grpc,client/transport/grpc -am -Dtest=GrpcHandlerTest,GrpcErrorMapperTest,Phase2PublicClientTest,Phase3ClientGenerationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -pl tests/multiversion/grpc -am -Dtest=Phase3GrpcMutationTest -Dsurefire.failIfNoSpecifiedTests=false verify
```
**Review boundary:** Owned-channel public path, protocol-typed errors, stream
finality and deterministic race evidence.

**Implementation brief:** Reuse Phase 2 adapters and accepted race-test patterns.
Prove the shared ordinary scenarios against the authority. Do not use a raw-channel
workaround, invent a duplex receipt or relax metadata/version protections.

### P3-T20 — Compatibility, migration and Phase 3 evidence gate

**Scope/dependencies:** All preceding tasks accepted. Prepare evidence for a
separate independent Sol phase review; do not close Phase 4/5/6 obligations.

**Files/interfaces:** Create `phase-3/api-migration.md`, `verification.md`,
`checkpoints/phase3-complete.md` and `next-session.md` only when supported by
evidence. Add custom-store/legacy-emitter probes under `phase-3/api-probes/`;
preserve frozen Phase 1/2 jars/classes/expected outputs. Update task-list and
implementation-progress; update development docs `server.md`, `client.md`,
`extra/storage.md`, `extra/replicated-queue-manager.md` under `docs/content/dev/`.
No new docs page/menu entry is necessary.

**Acceptance:** A03/A04/A05/A06/A07/A08/A10/A13/A14 **within Phase 3 scope**,
not blanket Axx pass labels. Link results to source hashes/revision and actual
suites. Execute old source/binary linkage and NullAway non-null-message probes,
legacy-only custom TaskStore and AtomicTaskStore opt-in, emitter sink/refresh/delta/
raw-queue migration examples. Preserve explicit-1.0 wrapper behavior. Recheck
affected frozen peer paths independently of updated SDK integration. Keep T7's
distinction between canonical Gson model output and old-version endpoint/protobuf
serializers; do not compare unlike paths or recapture expected output.

**Commands/evidence groups, each bounded:**
```sh
mvn -B -ntp -pl server-common,client/base,extras/task-store-database-jpa,extras/push-notification-config-store-database-jpa,extras/queue-manager-replicated/core -am test
mvn -B -ntp -pl tests/multiversion/jsonrpc,tests/multiversion/rest,tests/multiversion/grpc -am verify
mvn -B -ntp -pl compat-0.3/server-conversion,transport/jsonrpc,transport/rest,transport/grpc -am -Dtest=VersionedOutputProjectorTest,ProjectingPublisherTest,PushNotificationSenderTest,PushNotificationPayloadFormatter_v0_3_Test,JSONRPCHandlerTest,RestHandlerTest,GrpcHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```
Run the unchanged Phase 0 0.3 fixture harness against a newly recorded candidate
classpath, using `run.py --version 0.3 --classpath-file` with that actual absolute
file path. For 1.0 reuse the affected frozen endpoint/protobuf comparison procedure
in `phase-2/verification.md`, with new actual outputs in a new Phase 3 directory.
Accepted canonical Gson differences are not newly failing old-wire evidence.
Compile/load Phase 3 probes using fresh candidate classpaths and retained frozen
binaries, recording exact dependency/classpath commands with the new probe POM.
Do not overwrite historical inputs or rely on old `/tmp` files still existing.

T13 real multi-instance evidence must match final affected sources; refresh changed
scenarios when fixes invalidate it. Before phase completion, run the required
Java 17 `mvn -B -ntp clean install` with the normal deadline, in a separately
developer-assigned verification session if needed. Preserve its log at a new path,
never `test.log`; separate discovered/executed/skipped counts. Timeout or required
Docker skip means incomplete evidence. No build runs in this planning assignment.

**Review boundary:** Independent Sol phase audit of API migration, experimental
policy, all-binding usability, old contracts, cluster scope, crash-gap bounds and
evidence provenance. Findings return to bounded Luna corrections, followed by
affected-diff re-review. No automatic coordinator loop.

**Implementation brief:** Produce an honest Phase 3 evidence map/migration guide,
run affected checks, retain mandatory phase gates, stop for Sol. Leave final
target/discovery normative residuals, cursor/resync and live gates and Phases 4–6
open. No commit, production-conformance advertisement or next phase.

## Coverage and review checkpoints

| Obligation | Tasks | Required evidence boundary |
| --- | --- | --- |
| Atomic guards, artifacts, cancellation | T01–T03/T07/T08 | Transaction/barrier tests plus T17–T19 public calls |
| Direct Message and enrollment partial effects | T06–T08 | New-task no-persistence spies; existing-task visible config/IDs and concurrent-update tests; all-binding input cases |
| Immutable proposals and local outcomes | T04/T05 | Stale external/self cases, completion/deadlock tests, executable migration |
| Commit-before-visibility and order | T03/T08/T10/T11/T13 | Paused publication plus separately paused shared webhook dispatch on real nodes |
| Crash gap, stranded terminal, resource bounds | T13/T14/T17–T19 | Injected time, existing failure + explicit GetTask, no lossless claim |
| Persistence, migration and fencing | T08–T13 | Restart/corruption, denied old DB/broker identity, delayed epoch records |
| Public reduction, coverage, unknowns, no retry | T15–T19 | Pure transitions, every existing ordinary/input entry point, controlled invalid peers |
| Older contracts and extension points | Changed seams; T20 | Frozen matching-path checks, old binaries/NullAway, mixed-version shared task |

Review each task before downstream dependence. High-risk checkpoints are T01
atomic SPI; T03 publication; T04/T05 executor migration; T07 branch/enrollment;
T08/T09 persistence/fence; T10 replication; T11 shared webhook gate;
T12/T13 topology/real evidence; T14 recovery limits;
T15/T16 public reduction; each binding independently; T20 phase audit. Adjacent
reviews may combine only by developer assignment, with every risk still covered.

## Self-review and next action

- Phase 3 mutation, ordinary branch, full proposals/results, enrollment, storage/
  migration/fencing, replication/finality, public input/reducer and independent
  evidence requirements have task owners. Phase 4 replay/controller and Phase 5
  live routing/outcomes retain their separate gates; no pass is asserted here.
- Commands are proposed selectors, not passing evidence. The independent review
  assessed the T01/T03 commit-slot seam, remote reorder/gap safety, T07/T08
  task sizing, and the early ordinary TaskObservation API and conservative
  generation-reliability lifetime. Its implementation checkpoints also cover the complete ordinary
  entry-point matrix, shared webhook lane/quiescence bounds and existing-task
  independent-store enrollment partial effects with safe concurrent-config handling.
- Bootstrap/origin/no retry need no new policy approval. No blanket specification
  blocker was found for the selected experimental Phase 3 profile. Actual gates
  are developer task assignment, demonstrated authority/fence/
  infrastructure prerequisites, and any material conflict or API break discovered.
- **Disposition:** the independent Sol High correction re-review is approved;
  retain its binding implementation checkpoints above. The developer may select
  P3-T01 for a separate Luna medium/normal session. No runtime task or commit
  starts automatically; later task/phase reviews and evidence gates remain.
