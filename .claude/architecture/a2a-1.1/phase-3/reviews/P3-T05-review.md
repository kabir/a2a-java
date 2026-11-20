# P3-T05 independent review

**Disposition — Changes required.** The required selector and the correction
regression tests passed. Independent probes reproduced defects in multi-event
delivery, opening-input immutability, the late proposal path after a direct
Message, unary result provenance, and creation-race classification.

## Assignment and evidence provenance

- Issue #1303. Independent review started **2026-11-20 11:37:57 UTC**, with a
  deadline of **13:37:57 UTC**. Requested review model and settings were Sol,
  medium reasoning, normal speed. Serving metadata was unavailable.
- Reviewed branch `support/a2a-1.1.0` at
  `81dac751c1ab1ce405fc579c2a4873559ad38813` in the assigned worktree. No
  descendant commits appeared in the local refs. The candidate was the tracked
  server-common diff and the two untracked tests. Initial status also included
  the task-list edits, the untracked implementation handoff and untracked
  `test.log`. The index was empty.
- The tracked server-common diff from HEAD had SHA-256
  `48b3cbef1d819c4e56eb0e34b479bec1a88b7d15191239d22be35d8f4de065c5`.
  Untracked `FullTaskProposalTest.java` had SHA-256
  `56655c098c9288a9cb807d1368fd6de5fe2942eff431b4bf1c0bffd1a36b0b2d`.
  Untracked `ExecutorMigrationExampleTest.java` had SHA-256
  `10a5d033d8402bfe5d6427c69bc25d20231970a0034db86436b5e5c0e45ccb04`.
  The handoff had SHA-256
  `aaca2c4a6455d82e2765c8e3593280f73607f2f5692c4793f4a17858ab242cd2`.
- The plan's status banner and hold language describe the earlier T04 checkpoint.
  This review follows the current T05 assignment. The accepted T04 status-only
  and active no-op cases remain valid predecessor evidence. The implementation
  handoff's counts are historical claims and were checked against its logs.
  [T05 plan](../implementation-plan.md#p3-t05--normalize-full-task-proposals-safely) ·
  [T04 final correction review](P3-T04-full-reactor-correction-review.md#fourth-follow-up-review--2026-11-20-1111-utc) ·
  [T05 handoff](../handoffs/P3-T05.md)

## Demonstrated findings

### P1 — A multi-event proposal duplicates covered output for older versions

The bus marks every proposal event for legacy summary projection.
`DefaultRequestHandler.proposalSummaryOrEvent` replaces each marked event with
the same final committed Task. Through the real streaming handler, a 1.0
existing-task proposal with final artifact and COMPLETED status returned
`[Task, Task]` with equal snapshots. A 1.0 initial proposal returned three equal
Task summaries for its opening message, artifact and status. The corresponding
1.1 existing-task stream emitted artifact then terminal status and completed
normally. The same stream conversion is used for the 0.3 version policy and the
existing-task subscription path.

The sender has a second form of the problem. The bus sends the proposal status
through version selection and proposal artifacts through ordinary delivery.
The mixed-version webhook probe used a delegating sender with a deferred
earlier admission. The 1.0 config received a Task summary containing the new
artifact and then a separate update for that artifact. The 1.1 config received
the two canonical events. The active no-op summary in the same trace was
delivered only to the older version.

Make the proposal output choice at the batch boundary. Verify one final legacy
summary and zero covered deltas for a status-plus-artifact proposal in streams
and webhooks. Also exercise an artifact-only proposal, a terminal batch, and a
1.1 existing observer receiving each canonical event once. Retain the accepted
sender wrapper and active no-op behavior. This is T05 rule 5 and A06/A07.
[Bus dispatch](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/events/MainEventBusProcessor.java#L593) ·
[Stream selection](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/requesthandlers/DefaultRequestHandler.java#L1345) ·
[Push dispatch](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/events/MainEventBusProcessor.java#L681) ·
[Version-specific sender](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/BasePushNotificationSender.java#L219)

### P1 — Opening input can change committed state without a new revision

The new submission path retains the RequestContext Message in
`PendingTaskInput`, which shares nested JSON values. `canonicalMessage`
rebuilds the Message with the same nested values. The atomic store persists the
plan's Task and publishes events derived from that Message, while separately
copying the committed TaskSnapshot. After an initial proposal committed and
published, appending to a list in the original opening Message's metadata changed
both stored history and the already delivered canonical Message event. The
revision and generation stayed the same, the committed result and queue item's
snapshot still carried the original list, and no new event appeared. A control
probe showed that metadata supplied through TaskProposal itself is protected by
its existing deep copy.

Capture an immutable opening value before handing the request to the executor,
and carry that value into both canonical storage and the committed event.
Verify nested opening values can change in caller/executor-owned input before
submission and after commit without changing the committed request content,
event, history, timeline or snapshot. Check the revision and generation stay
paired with the same value. This applies A05/A06 and full-Task rules 1–2.
[Opening capture](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/ExecutorSubmissionService.java#L526) ·
[Pending input](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/PendingTaskInput.java#L33) ·
[Canonical message](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskMutationPlanner.java#L348) ·
[Atomic storage](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/InMemoryTaskStore.java#L216)

### P1 — A full-Task proposal can materialize an orphan after direct Message delivery

The new production initial branch checks for retained opening input and an absent
store row. It has no check that the execution already chose a direct response.
The streaming probe waited inside the executor until the subscriber received its
direct Message, then submitted a null-base terminal proposal. For both 1.0 and
1.1, the subscriber received only the direct Message, the proposal returned
`Committed`, and the generated task ID acquired a Task with generation 3. In
the direct-only control, both handlers returned a Message and left no Task.

Guard this newly enabled full-Task entry point against an already selected
no-task response, so a later submission rejects locally before storage and
publication. Run a production-path test with a delivery barrier and a proposal
result assertion. Verify no task write, generation, event or proposal push
after direct delivery. This finding is about the behavior introduced by T05
initial proposals. The broader branch orchestration and enrollment remain
assigned to T07 and T06. [Initial authority branch](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/ExecutorSubmissionService.java#L622) ·
[Direct Message architecture](../../architecture.md#ordinary-send-direct-message-or-task-materialization)

### P2 — The immediate unary proposal result can come from a later unrelated write

The first canonical item carries the final committed batch snapshot.
`ResultAggregator` captures it but uses it as the returned Task only for
cancellation. For ordinary nonblocking sends the normalized proposal has no
raw Task item, so aggregation falls through to a later store read. The unary
probe used `returnImmediately=true` and a store seam that committed an unrelated
INPUT_REQUIRED status at the request thread's first post-commit `get`. The
proposal returned generation 3/WORKING to the executor. The caller got
generation 4/INPUT_REQUIRED.

Use the snapshot attached to the proposal batch when selecting the immediate
unary proposal result. Verify a controlled later write between publication and
result selection cannot replace that result. Keep this correction centered on
proposal provenance. General blocking and read-projection orchestration remain
T07 work. This applies A05/A06 and the binding committed-snapshot checkpoint.
[Result aggregation](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/ResultAggregator.java#L308) ·
[Plan checkpoint](../implementation-plan.md#independent-approval-and-binding-implementation-checkpoints)

### P2 — A valid initial proposal losing a creation race reports validation failure

Two matching retained opening contexts submitted null-base proposals through
the same production queue. The first materialized the Task. The second returned
`Rejected(VALIDATION)` with the winner's revision and the message that an
existing Task requires an immutable base snapshot. Its absent base described
the valid observation at invocation start. The probe confirmed the winner's
history and revision were preserved and the loser produced no observer event.

Classify a valid opening proposal that loses materialization as a local
conflict. Preserve validation for malformed requests and retain the atomic
existence check. Verify one winner, one `CONFLICT` with the observed winner
revision, no losing input or events, and a successful explicit refresh/rebuild
when the winning task permits it. This applies full-Task rules 2–3 and the
executor migration contract. [Authority check](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/ExecutorSubmissionService.java#L608) ·
[Full-Task architecture](../../architecture.md#full-task-output-from-executors)

## Verified behavior and remaining evidence

- The production authority compares both Task and revision inside the atomic
  callback. The tests cover the independent stale-after-client-input and
  stale-after-self-delta cases, local completion, no stale mutation, and an
  observer receiving later valid output. The client-input test advances the
  authority directly. The full ordinary input admission path remains T07.
  The executable examples exercise ordered initial creation, explicit refresh
  and rebuild, and status/artifact delta emission.
- The planner tests cover omission, canonical history/timeline, USER artifact
  retention, agent upserts, generation reassignment, terminality, and a synthetic
  metadata-only status observation with preserved message identity and
  occurrence count. The initial ordered-path test covers opening-message ID
  deduplication and legacy bootstrap history without fabricated timeline
  entries. The separate authority probe rejected duplicate artifact IDs during
  initial creation, with no Task or observer event, then delivered canonical
  generations 1–4 with the same final snapshot on all four items.
- The 1.1 streaming probe delivered an existing-task terminal artifact before
  terminal status and completion. The initial 1.1 stream delivered the opening
  message, artifact and status as canonical events. The coordinated opening
  stream Task and watermark handover remain in T07. The raw-queue multi-event
  tests alone cannot establish stream or webhook output selection.
- The correction selector exercised ordered queue capability ownership,
  duplicate-enqueue isolation, local result delivery, independently bounded
  proposal and ordinary-final waits, custom queue timeout hooks, and sender
  wrappers. The active no-op has no canonical events or mutation in the required
  selector. Source inspection confirms its delivery-only branch skips the
  committed publisher and its replication hook. The webhook probe confirmed
  legacy-only no-op delivery. The replication selector was the retained
  counting-strategy regression. T10 committed replication/finalization and
  later shared-authority behavior were outside this review.
- Production probes used in-memory authority and the shared handler/sender.
  They did not run all bindings for multi-event proposals. The nominated
  binding regressions cover their recorded existing paths. Direct-only probes
  used ordinary message input without inline push configuration. Pending
  artifact input orchestration, inline enrollment, and their all-binding tests
  remain in the approved later tasks.

## Independent verification

Maven 3.9.14 and Temurin Java 17.0.18, from the assigned worktree. Existing
Surefire reports were archived before running selectors. Each run took fresh
XML and output files newer than its own marker before running the next selector.
The required run recompiled 116 server-common production and 58 test sources,
including both untracked tests. Candidate source hashes matched after the
checks. Maven logs, report snapshots, probe source, probe logs, and the
pre-review report archive are under `/private/tmp/a2a-p3-t05-review.5r5skk7b/`.

The following shell form records the exact commands. Each run began with over
20 minutes left and used the 1,200-second limit plus 30 seconds cleanup.

```sh
run() {
  /usr/bin/timeout -k 30s 1200s mvn -B -ntp "$@" \
    -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
    -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar test
}
run -pl server-common -am -Dtest=FullTaskProposalTest,ExecutorMigrationExampleTest,TaskManagerSnapshotTest,ExecutorSubmissionTest
run -pl server-common -am '-Dtest=ExistingTaskProposalStreamLifecycleTest,CustomEventQueueCompatibilityTest,ExecutorSubmissionTest,TaskManagerSnapshotTest,FullTaskProposalCompatibilityTest,ResultAggregatorTest,PushNotificationSenderTest*,AtomicTaskStoreContractTest,EventQueueTest,MainEventBusProcessorPushNotificationOrderTest,CommittedPublicationTest,EventConsumerTest'
run -pl extras/queue-manager-replicated/core -am -Dtest=ReplicatedQueueManagerTest#testReplicationStrategyWithCountingImplementation
run -pl transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc -am '-Dtest=JSONRPCHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,GrpcHandlerTest#testOnMessageStreamNewMessageExistingTaskSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess,JSONRPCHandler_v0_3_Test#testOnMessageSendStreamExistingTaskSuccess+testOnMessageSendStreamMultipleEventsSuccess+testOnMessageStreamNewMessageSendPushNotificationSuccess'
run -pl extras/opentelemetry -am -Dtest=OpenTelemetryPushNotificationSenderDecoratorTest
run -pl extras/opentelemetry/server -am -Dtest=OpenTelemetryPushNotificationSenderDecoratorTest
```

| Log | Finished UTC | Exit | Executed | Failures | Errors | Skips |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `required.log` | 11:38:35 | 0 | 42 | 0 | 0 | 0 |
| `server.log` | 11:39:08 | 0 | 199 | 0 | 0 | 0 |
| `replication.log` | 11:39:12 | 0 | 1 | 0 | 0 | 0 |
| `bindings.log` | 11:39:22 | 0 | 8 | 0 | 0 | 0 |
| `otel.log` | 11:39:24 | 0 | 0 | 0 | 0 | 0 |
| `otel-server.log` | 11:39:54 | 0 | 8 | 0 | 0 | 0 |

Required breakdown was 7 full-proposal, 2 migration, 3 TaskManager snapshot,
and 30 executor-submission cases. Binding breakdown was 3 JSON-RPC 1.0,
2 gRPC 1.0, and 3 JSON-RPC 0.3. The handoff's OpenTelemetry log showed
the correct server suite, but its written parent-POM command selected just
two POMs in this review. The explicit server module produced the eight fresh
XML cases. An initial attempt to construct that rerun's shell wrapper had a
syntax error and invoked no build. With the explicit module, the four
correction selectors executed 216 cases with zero failures, errors or skips.
The required and correction sets both include the 30 executor-submission and
three TaskManager snapshot cases.

For the probes, `classpath` is the `java.class.path` property from the fresh
`FullTaskProposalTest` Surefire XML. From the worktree, compile and execute
each class using the following commands. Replace the argument to `run_probe`
with each table entry. The probe exit codes indicate execution success.
The observations above determine whether the candidate satisfied the contract.

```sh
review_dir=/private/tmp/a2a-p3-t05-review.5r5skk7b
cp_value=$(cat "$review_dir/classpath")
run_probe() {
  /usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$review_dir" "$review_dir/$1.java" &&
  /usr/bin/timeout -k 30s 90s java -cp "$review_dir:$cp_value" "$1"
}
run_probe ProposalHandlerProbe
run_probe ProposalAuthorityProbe
run_probe ProposalUnaryProbe
run_probe ProposalMetadataProbe
run_probe ProposalOpeningAliasingProbe
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$review_dir" "$review_dir/ProposalWebhookProbe.java"
/usr/bin/timeout -k 30s 90s java \
  -javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar \
  -cp "$review_dir:$cp_value" org.a2aproject.sdk.server.tasks.ProposalWebhookProbe
```

| Probe | Execution exit | Log |
| --- | ---: | --- |
| Handler, terminal proposals and direct responses | 0 | `probe-handler.log` |
| Authority, initial rejection and competing creation | 0 | `probe-authority.log` |
| Immediate unary result provenance | 0 | `probe-unary.log` |
| Control for deep copying TaskProposal metadata | 0 | `probe-metadata.log` |
| Opening Message aliasing | 0 | `probe-opening-aliasing.log` |
| Wrapped, version-selected webhooks | 0 | `probe-webhook.log` |

The initial authority probe source used an incorrect method name and failed
compilation. That log is retained as `probe-authority-build-attempt1.log`.
The first unary probe allowed the injected write on any thread and raced with
a background read. Its source and log are retained. The reported run gates
the write on the request thread's store read.

Only this review and the T05 coordination row were edited. The candidate
source, tests and historical reviews were preserved. `test.log` remained
untracked with the same file metadata and was neither opened nor used for
output. This handoff closes the review assignment with corrections pending.

## Correction 1 independent re-review — 2026-11-20 12:12 UTC

**Disposition — Changes required.** The correction fixes the creation-race
classification and several of the original reproduction cases. Five findings
remain in the corrected candidate. Four were reproduced by independent
production-path probes and the unary race was reproduced under repeated
concurrent execution. The focused tests and the retained correction tests
both passed.

### Assignment and candidate

The developer separately assigned this re-review. It started at **12:10:15 UTC**
with a deadline of **14:10:15 UTC**. Requested settings were Sol, medium
reasoning, normal speed. Serving metadata was unavailable. The assigned
worktree and branch remained unchanged at
`81dac751c1ab1ce405fc579c2a4873559ad38813`; no descendant commits appeared in
local refs. The index was empty. The original review's SHA-256 before this
append was `7c060313f9b64d9efddaa8ab97739259e0b6af2ac19aecd81e4d8f9620e3e1bb`.
The plan and phase summary contain stale T05 hold language from the earlier
T04 checkpoint. The current assignments and recorded review dispositions govern.
[Correction handoff](../handoffs/P3-T05-correction-1.md) ·
[T04 final correction review](P3-T04-full-reactor-correction-review.md#fourth-follow-up-review--2026-11-20-1111-utc)

| Candidate evidence | SHA-256 |
| --- | --- |
| Initial tracked diff from HEAD, including the handoff's coordination edit | `fbce710ae88bf8414b09f9889373bbda9c5bcb4c05e7010dd8c7701a1e92d99b` |
| Tracked diff restricted to server-common and OpenTelemetry | `276c3e71aba0711035b4d58fa0cf015d1a363847aee248cf7888691cd4ef27c5` |
| Untracked `ExecutorProposalOutputTest.java` | `e12663020a8887888269e8b60068f8c82b7c506cd817e21c541b919d94a31fab` |
| Untracked `FullTaskProposalTest.java` | `8128b1151874c0ceb59a81612eb7855aefb54028b0344bc4f8030c392e1a2d25` |
| Untracked `ExecutorMigrationExampleTest.java` | `10a5d033d8402bfe5d6427c69bc25d20231970a0034db86436b5e5c0e45ccb04` |
| Correction handoff | `9891fd8c27564077fdb9e34d8618751de43bb63853d259b57cc5f8057f83ec88` |

### P1 — A legacy terminal batch needs extra downstream demand to complete

The handler delivers the one visible Task summary on the first proposal item.
The dropped items remain queued behind the subscriber's demand. The filter
requests replacement items only after it receives a dropped item. If the
subscriber requests the one visible response and never requests a second,
the filter cannot reach the covered terminal item or relay completion.

The 1.0 production handler delivered one terminal Task for both existing-task
and opening multi-event proposals, but neither stream completed during a
two-second wait. In both cases a second request for one item completed the
stream without emitting anything else. The status-only control delivered one
Task and completed with the first request. This violates the finite-demand and
finality requirements in A06/A07.

Ensure covered items can drain after the sole summary consumes the last unit
of demand. Verify that `request(1)` yields one terminal summary and completion,
without supplemental demand. Also verify a nonterminal summary followed by a
new visible event never exceeds requested demand. The batch's canonical 1.1
artifact-before-status order still passed.
[Summary selection](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/requesthandlers/DefaultRequestHandler.java#L1348) ·
[Bus finality](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/events/MainEventBusProcessor.java#L617)

### P1 — An unchanged T04 version-aware sender wrapper still duplicates covered output

The new batch method selects per config in the SDK base sender, and the updated
OpenTelemetry decorator forwards the new call. An existing wrapper forwarding
the T04 `sendNotification`, `sendTaskProposalStatusNotification` and
`sendLegacyTaskSnapshot` methods inherits the new default. That default sends
each item through the prior event-level methods. The retained T04-style wrapper
probe, with deferred admission, again delivered a 1.0 Task summary containing
the new artifact followed by a separate update for that artifact. The 1.1 config
received the expected canonical events. The updated repository test overrides
the new batch method, so it exercises opted-in wrappers.

Preserve one per-version output choice when the accepted T04 wrapper is used
unchanged. Verify that wrapper with mixed 1.0/1.1 configs, deferred admission,
a multi-event proposal and the active no-op. Retain the updated batch-aware and
version-unaware controls. This applies the T05 projection rule and the accepted
T04 sender seam.
[Sender default](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/PushNotificationSender.java#L149) ·
[Base sender](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/BasePushNotificationSender.java#L233) ·
[Updated wrapper test](../../../../../server-common/src/test/java/org/a2aproject/sdk/server/tasks/PushNotificationSenderTest.java#L164)

### P1 — Direct Message selection still permits an orphan and can drop a valid response

The new guard marks the origin only for `ROLE_AGENT` and only after a
successful atomic snapshot read. `AgentEmitter.emitEvent(Message)` accepts a
Message without a role check, and the stream delivers and closes on any raw
Message. The 1.0 and 1.1 handler probes emitted a `ROLE_USER` Message through
that supported Java entry point, waited for its delivery, then submitted a
full Task on the same invocation. Both callers received only the Message and
both proposals committed orphan tasks at generation 3.

A second probe emitted only a `ROLE_AGENT` Message with an atomic store whose
bus-thread `readSnapshot` throws. The new read occurs before distribution, so
the bus logged the read failure and the client stream completed with zero
items. The direct Message path previously required no atomic store read. This
is a change at the T05 guard, independent of future enrollment and ordinary
send orchestration.

Use the branch and invocation facts that govern actual direct response selection,
without a new fallible delivery dependency. Verify that every Message accepted
as the direct response bars later initial proposals on that invocation. A
Message rejected before response selection may use existing validation. With a
store read fault, a valid direct-only Message should still reach the caller
without a task write or proposal publication.
[Bus guard](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/events/MainEventBusProcessor.java#L346) ·
[Direct stream finality](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/events/EventConsumer.java#L229) ·
[Raw emitter entry point](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/AgentEmitter.java#L631)

### P2 — Nested request metadata still changes the committed opening event

Deep copying the opening Message fixes the original shared history and Message
payload trace. `PendingTaskInput` still shallow-copies the separate request-level
metadata map. `planInput` includes this metadata on the opening
`TaskMessageUpdateEvent`. An ordered-authority probe changed a retained list in
request-level metadata after commit. Its already-delivered opening event changed
from `{nested=[before]}` to `{nested=[before, after]}` at the same generation
without another observer item.

Capture request-level metadata with the same value isolation as the opening
Message. Extend the before-submission and after-commit tests to cover both
metadata locations and assert event content remains stable. This applies A05/A06
and the committed publication invariant.
[Pending input copy](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/PendingTaskInput.java#L32) ·
[Opening event construction](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskMutationPlanner.java#L92)

### P2 — Immediate unary completion can still expose a later TaskStore write

The consumer completes `completionFuture` before assigning
`immediateProposalSnapshot`. The waiting request can resume and read an empty
snapshot reference, fall through to TaskStore reconciliation and return a
later write. The focused test controls the later write but does not control
the order between completion and snapshot assignment.

A real handler stress probe ran 600 independent `returnImmediately=true`
opening proposals. Its TaskStore committed a generation-4 INPUT_REQUIRED
status only if the request thread performed the fallback read. Seven responses
returned that later state. The expected proposal snapshot was generation 3
and WORKING.

Publish the selected response and its snapshot together before signalling
completion. Verify that a waiter released at that boundary always receives the
batch result, even with a later unrelated write. Retain the existing
direct-Message precedence and blocking behavior. This applies the binding
committed-snapshot checkpoint and A05/A06.
[Signal order](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/ResultAggregator.java#L211) ·
[Result selection](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/ResultAggregator.java#L313)

### Closed and verified portions

- **Creation race closed.** The independent ordered-authority replay returned
  `CONFLICT` with the winner's revision. It observed one committed batch at
  generations 1–4 with a shared final snapshot, no losing history or extra
  observer event and no write from the invalid duplicate-ID proposal. The
  focused test also verifies explicit refresh/rebuild.
- The old unlimited-demand duplicate-stream probe now yields one 1.0 summary
  for both opening and existing-task terminal batches. The 1.1 streams retain
  their canonical order. The batch-aware wrapper and OpenTelemetry suite,
  active no-op, ordinary stale proposals, capability gating, duplicate enqueue,
  timeouts and custom queue regression selectors passed. The remaining output
  gaps are the finite-demand and unchanged wrapper cases above.
- The original opening Message aliasing and `ROLE_AGENT` direct-then-proposal
  traces are repaired under the default in-memory handler. The related
  request-metadata, role and snapshot-read-fault cases above remain.
- The probes used the shared handlers and in-memory store. The eight retained
  binding assertions passed. This review included no all-binding multi-event
  matrix, later-phase enrollment, ordinary orchestration, JPA or cluster work.

### Fresh execution evidence

The five commands are the exact selectors in the correction handoff. The
bounded runner is saved as
`/private/tmp/a2a-p3-t05-rereview.q_9y0ial/run.sh`. It invoked
`/usr/bin/timeout -k 30s 1200s mvn -B -ntp` for each selector, with
`-Dsurefire.failIfNoSpecifiedTests=false`, `-Dmaven.test.failure.ignore=false`,
and `-DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar`.
All started with more than 20 minutes remaining. Maven used Java 17. Existing
reports were archived before execution, and each run copied its own fresh
Surefire XML and output based on a per-run marker. Source hashes matched the
implementation handoff. The focused run recompiled server-common, including
59 test source files.
[Exact Maven selectors](../handoffs/P3-T05-correction-1.md#final-verification)

| Log | Finished UTC | Exit | Executed | Failures | Errors | Skips |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `required.log` | 12:10:33 | 0 | 50 | 0 | 0 | 0 |
| `server.log` | 12:11:07 | 0 | 199 | 0 | 0 | 0 |
| `replication.log` | 12:11:11 | 0 | 1 | 0 | 0 | 0 |
| `bindings.log` | 12:11:21 | 0 | 8 | 0 | 0 | 0 |
| `otel.log` | 12:11:26 | 0 | 9 | 0 | 0 | 0 |

The 50 focused cases and the 217 correction regression cases overlap. The
binding breakdown was 3 JSON-RPC 1.0, 2 gRPC 1.0, and 3 JSON-RPC 0.3.
All evidence is under `/private/tmp/a2a-p3-t05-rereview.q_9y0ial/`, with the
pre-review archive and one reports directory per selector.

The independent probes use the `java.class.path` from the fresh
`FullTaskProposalTest` XML saved as `classpath`. Each compile and execution
used `/usr/bin/timeout -k 30s 90s`. The standard compile and execute forms are
shown below. The webhook probe also used the Maven Byte Buddy javaagent.

```sh
review_dir=/private/tmp/a2a-p3-t05-rereview.q_9y0ial
cp_value=$(cat "$review_dir/classpath")
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$review_dir" "$review_dir/<Class>.java"
/usr/bin/timeout -k 30s 90s java -cp "$review_dir:$cp_value" <Class>
/usr/bin/timeout -k 30s 90s java \
  -javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar \
  -cp "$review_dir:$cp_value" org.a2aproject.sdk.server.tasks.ProposalWebhookProbe
/usr/bin/timeout -k 30s 90s java \
  -Dlogback.configurationFile="$review_dir/quiet-logback.xml" \
  -cp "$review_dir:$cp_value" UnaryRaceStress
```

| Probe source and same-named log | Exit | Result |
| --- | ---: | --- |
| `ProposalHandlerProbe` | 0 | Original default handler reproductions now pass |
| `ProposalAuthorityProbe` | 0 | Conflict with winner revision and no losing mutation |
| `ProposalOpeningAliasingProbe` | 0 | Original Message copy trace passes |
| `ProposalRequestMetadataProbe` | 0 | Opening event metadata changed after commit |
| `ProposalDirectRoleProbe` | 0 | Orphan committed after a delivered Message in both versions |
| `ProposalDirectReadProbe` | 0 | Empty stream when the added snapshot read throws |
| `ProposalDemandProbe` | 0 | Multi-event terminal streams needed an extra request |
| `org.a2aproject.sdk.server.tasks.ProposalWebhookProbe` | 0 | Duplicate legacy coverage through the unchanged wrapper |
| `UnaryRaceStress` | 0 | 7 wrong responses out of 600 |

Probe exits report execution, with observations above recording contract
violations. The first demand probe exited 1 on a completion timeout. Its
source and log were saved as `ProposalDemandProbe-attempt1.*`; the final
probe added a second demand and a status-only control. An optional JDI
scheduler probe could not open its local debug socket under the sandbox
(`unary-schedule.log`, exit 1). The stress probe reproduced the race without
a debugger.

Only this append and the T05 coordination row were edited. Candidate source
and tests remain unchanged, the original review text is preserved, and
`test.log` remained untracked with unchanged metadata. No full-reactor run
or later task work occurred.

## Correction 2 independent re-review — 2026-11-20 12:52 UTC

**Disposition — Changes required.** The previously reproduced stream,
direct-response, opening-input and unary defects are corrected, and the
creation-race fix holds. The unchanged T04 webhook wrapper works when it calls
its delegate on the same thread. One compatibility finding remains for an
unchanged wrapper that calls its delegate on a worker thread and waits for its
result.

### Assignment and candidate

This separate re-review started at **12:50:51 UTC** with a deadline of
**14:50:51 UTC**. Requested settings were Sol, medium reasoning, normal speed.
Serving metadata was unavailable. The assigned worktree and branch remained
at `81dac751c1ab1ce405fc579c2a4873559ad38813`; no descendant commits appeared
in local refs. The index was empty. Prior review dispositions remain
historical evidence. The plan and phase summary still carry stale T05 hold
language from the earlier checkpoint. [Correction 2 handoff](../handoffs/P3-T05-correction-2.md)

| Candidate evidence | SHA-256 |
| --- | --- |
| Tracked diff from HEAD at review start, including the coordination row | `1032ed7917d3ac143cf51b521476185b7304a04fa90889944658d955590b352b` |
| Tracked server-common and OpenTelemetry diff | `46bd01a25f1bb6844ae5c553e90b53cf470988764a374edd6c73f6c779aaccce` |
| Untracked `TaskProposalDispatchContext.java` | `e5f5f582116397bc071229293ca5edec6feacd19e8a076a9f757ab01ac4b6ef0` |
| Untracked `ExecutorProposalOutputTest.java` | `f3c2bcebd0b740dffbff992ea960def013cdbe16fd838545b3641bfcc6a785e7` |
| Untracked `FullTaskProposalTest.java` | `f7125ebaffe37704a63175270b68be1d62cfa28922e66c7d5725507a7f55f648` |
| Untracked `ExecutorMigrationExampleTest.java` | `10a5d033d8402bfe5d6427c69bc25d20231970a0034db86436b5e5c0e45ccb04` |
| Correction 2 handoff | `830afce7a0bbac5b48c4c8f32a3c5eb97be97749b638d2d78753018cae0d02db` |
| Review before this append | `96669a42b5f6877f3eee8a06ba94e93f9c11f3d5c856be843cba3e0e8ea1eabc` |

### P1 — A T04 wrapper that delegates on a worker duplicates even a status-only summary

The new default batch method sends a legacy Task summary and then invokes the
previous per-event methods. The base sender suppresses legacy sends in those
per-event methods only if it sees the batch in `TaskProposalDispatchContext`.
That context is thread-local. A custom wrapper can implement the established
boolean interface by forwarding each call through an executor and joining the
result. Such a wrapper still observes the delegate's return value and preserves
invocation ordering. The original sender SPI does not require same-thread
forwarding.

The independent probe exercised deferred admission, an active no-op and a
status-only proposal through that unchanged three-method wrapper. The 1.0
destination saw `[Status, Task(no-op), Task(proposal), Task(proposal), Status]`.
The 1.1 destination received its three canonical statuses. The same probe
using the accepted T04 `MainEventBusProcessor` from HEAD, compiled in a
temporary directory and placed first on the otherwise identical candidate
classpath, delivered
`[Status, Task(no-op), Task(proposal), Status]` to 1.0. This control isolates
the regression to the proposed dispatch change. The synchronous wrapper also
passed the original multi-event case on the candidate.

Preserve per-version batch selection across the existing sender boundary.
Verify the same wrapper with a joined worker-thread delegate, in addition to
the same-thread and batch-aware controls. Assert one proposal summary and zero
covered deltas for 1.0, all canonical events once and in order for 1.1, and
the legacy-only no-op. Cover status-only as well as status-plus-artifact
proposals. This is an A07/T05 projection violation and a regression in the
accepted T04 status-only sender path.
[Batch fallback](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/PushNotificationSender.java#L149) ·
[Thread-local forwarding context](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L12) ·
[Status projection](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/BasePushNotificationSender.java#L225)

### Verified corrections and scope

- Both initial and existing-task 1.0 terminal batches emit one Task and
  complete after a single `request(1)`. The status-only control also completes.
  Focused coverage checks that a nonterminal summary does not consume demand
  for later visible output. The established 1.1 artifact-before-status order
  remains intact.
- The unchanged same-thread T04 wrapper now sends one legacy summary for the
  multi-event proposal and keeps canonical 1.1 delivery. The SDK batch-aware
  sender, OpenTelemetry wrapper, and version-unaware fallback passed their
  selectors. The worker-thread case above is the remaining sender finding.
- Both accepted raw Message roles reached the caller and blocked a subsequent
  initial proposal on the same invocation. The snapshot-read-fault probe
  delivered the direct-only Message successfully, with no stored Task.
- Both independently retained metadata aliases were isolated. Mutating the
  original nested opening Message and request-level metadata left committed
  history, snapshots and the published opening event stable at the same
  generation.
- Proposal snapshots are selected before the completion signal. The independent
  unary stress rerun returned **0 wrong results in 600 attempts**, and the
  focused controlled later-write and completion-order tests passed.
- The creation-race replay returned `CONFLICT` with the winner revision,
  no losing mutation or extra publication, and four canonical generations
  sharing one final snapshot. The retained selectors passed admission,
  duplicate enqueue, lifecycle timeouts, custom queue, and no-op coverage.
- This review used the shared production handler and in-memory authority for
  probes. It retained the eight recorded binding assertions without extending
  them to an all-binding multi-event matrix. T06 enrollment, T07 orchestration
  and later authority work remain outside the assignment.

### Fresh execution evidence

Each of the seven exact commands in the correction handoff ran sequentially
through the archived `run.sh`, with `/usr/bin/timeout -k 30s 1200s`,
`mvn -B -ntp`, `-Dsurefire.failIfNoSpecifiedTests=false`,
`-Dmaven.test.failure.ignore=false`, and the documented Byte Buddy 1.18.8
agent. Each started with over 20 minutes remaining. Existing Surefire reports
were archived before the first run. Fresh reports for all runs were copied
after separate timestamp markers. The plan run used incremental compilation.
The production and focused test class timestamps postdate the reviewed source
changes, and the probes exercised the new code on the fresh Surefire classpath.
[Exact Maven commands](../handoffs/P3-T05-correction-2.md#verification-evidence)

| Log | Finished UTC | Exit | Executed | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `plan.log` | 12:51:05 | 0 | 44 | 0 | 0 | 0 |
| `focused.log` | 12:51:17 | 0 | 73 | 0 | 0 | 0 |
| `server.log` | 12:51:50 | 0 | 201 | 0 | 0 | 0 |
| `replication.log` | 12:51:53 | 0 | 1 | 0 | 0 | 0 |
| `bindings.log` | 12:52:01 | 0 | 8 | 0 | 0 | 0 |
| `otel.log` | 12:52:03 | 0 | 9 | 0 | 0 | 0 |
| `sender.log` | 12:52:06 | 0 | 3 | 0 | 0 | 0 |

The retained correction selectors executed 219 cases. The plan, focused,
correction and sender selectors overlap. Evidence is in
`/private/tmp/a2a-p3-t05-rereview2.7ypjq2p4/`. The independent replay compiled
the copied probe sources using the `java.class.path` property from the fresh
plan `FullTaskProposalTest` XML. The compile and run forms were:

```sh
review_dir=/private/tmp/a2a-p3-t05-rereview2.7ypjq2p4
cp_value=$(cat "$review_dir/classpath")
agent=/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$review_dir" "$review_dir"/Proposal*.java "$review_dir/UnaryRaceStress.java"
/usr/bin/timeout -k 30s 90s java -Dlogback.configurationFile="$review_dir/quiet-logback.xml" \
  -javaagent:"$agent" -cp "$review_dir:$cp_value" <probe-class>
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$review_dir/base-bus-classes" "$review_dir/base-bus-src/MainEventBusProcessor.java"
/usr/bin/timeout -k 30s 90s java -Dprobe.webhook.count=7 -Dlogback.configurationFile="$review_dir/quiet-logback.xml" \
  -javaagent:"$agent" -cp "$review_dir/base-bus-classes:$review_dir:$cp_value" \
  org.a2aproject.sdk.server.tasks.ProposalOffloadedWebhookProbe
```

| Probe class | Exit | Result |
| --- | ---: | --- |
| `ProposalHandlerProbe` | 0 | Default versioned handler and direct response traces pass |
| `ProposalAuthorityProbe` | 0 | Race and invalid initial proposal traces pass |
| `ProposalOpeningAliasingProbe` | 0 | Opening Message and snapshots stable |
| `ProposalRequestMetadataProbe` | 0 | Nested request-level metadata stable |
| `ProposalDirectRoleProbe` | 0 | Direct response bars late proposal in both versions |
| `ProposalDirectReadProbe` | 0 | Injected snapshot read failure does not drop Message |
| `ProposalDemandProbe` | 0 | Single request receives summary and completion |
| `UnaryRaceStress` | 0 | 0 wrong responses in 600 attempts |
| `org.a2aproject.sdk.server.tasks.ProposalWebhookProbe` | 0 | Same-thread wrapper yields one summary |
| `org.a2aproject.sdk.server.tasks.ProposalOffloadedWebhookProbe` | 0 | Candidate produces one extra status-only summary |
| Offloaded wrapper with T04 bus control | 0 | One status-only proposal summary |

The probe logs use class names. The control log is `offload-base-bus.log`;
the repeated candidate trace is `offload-candidate-recheck.log`. The exit
codes record probe execution. The captured event sequences establish the
finding. No Maven run or probe compilation failed. `git diff --check` passed.
Only this append and the T05 row were edited. Candidate sources, tests and the
untracked `test.log` were preserved. The review stops with the sender
correction pending.

## Correction 3 independent re-review — 2026-11-20 13:43 UTC

**Disposition — Changes required.** The correction-2 worker-thread
reproduction now passes with the original three-method forwarding wrapper.
Two variants of the existing sender SPI still regress status-only proposals.
The required selector, focused worker-thread test and retained regressions
passed when run with the installed agent.

### Assignment and candidate

This separate re-review started at **13:41:52 UTC** with a deadline of
**15:41:52 UTC**. Requested settings were Sol, medium reasoning, normal speed.
Serving metadata was unavailable. HEAD was
`81dac751c1ab1ce405fc579c2a4873559ad38813` on `support/a2a-1.1.0`. No
descendant commits appeared in local refs and the index was empty. The plan
and phase summary still contain stale T05 hold language. The predecessor
acceptance and prior review dispositions remain historical evidence.
[Correction 3 handoff](../handoffs/P3-T05-correction-3.md)

| Candidate evidence | SHA-256 |
| --- | --- |
| Tracked diff from HEAD at start | `f68e6fb4c2440cdd31a2321f536d34a6ecb5792f2e6ebc7c417139190cb37864` |
| Tracked server-common and OpenTelemetry diff | `119aa94cd24be894f09c41295043bf2c3a808c2503f0a271ed77e1a6f228601f` |
| Untracked `TaskProposalDispatchContext.java` | `fe48fd74a6cf5926d4c84e23247473acb4a72d85998134351e88258e54736926` |
| `PushNotificationSenderTest.java` | `c2e9fcf09bf0fe473340b2a2c38279f48d187ff26a32c2b26326181e52e370ee` |
| Correction 3 handoff | `9decd4a1cb17d9a4d82ba2cabf28d338fabb044e25059128a4413755fee495cd` |
| Review before this append | `c4d91bfe07ceba89601c91d0fe7b0942762affa775515e4e53b7cdfdf5dfc176` |

### P1 — Status-only webhook delivery now depends on the optional no-op hook and event object identity

The new fallback sends a changed proposal's legacy summary through
`sendLegacyTaskSnapshot`, then the base sender suppresses the legacy
projection of any registered status event. The pre-batch SPI exposes these
as independent methods. In particular, inheriting the no-op hook's default
was previously a valid way to skip active no-op output while still supporting
version-selected proposal statuses.

The independent probe used a wrapper forwarding ordinary and proposal status
calls to a worker and joining each result. It inherited the default no-op
method. The 1.0 destination received only the earlier and later ordinary
statuses. It received **zero** summaries for the intervening status-only
proposal. The same wrapper with the accepted T04 bus delivered **one**
proposal summary. Both paths correctly skipped the active no-op for this wrapper
and delivered three canonical 1.1 statuses.

A related probe forwarded all three old methods on a worker and defensively
rebuilt the status with `TaskStatusUpdateEvent.builder(event).build()`. The
value was equal and the final snapshot was forwarded unchanged. The candidate
delivered **two** summaries for the status-only proposal, while the T04 bus
delivered **one**. The fresh event missed the identity registration, so the
base sender projected an additional Task after the default batch method had
already sent its summary.

Preserve the accepted status-only route through the status seam when that is
the entire batch. Verify both pre-batch wrappers against the status-only
case, including the intended no-op behavior and mixed 1.0/1.1 output. Retain
the multi-event tests for full forwarding and verify that a version-aware
sender still receives one legacy output choice when handling a multi-event
batch. Avoid globally classifying an unrelated equal-valued event as a
proposal event. This is a T04 status-only compatibility regression and
violates A07 and T05's per-version projection rule.
[Batch default](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/PushNotificationSender.java#L149) ·
[Optional no-op hook](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/PushNotificationSender.java#L169) ·
[Base status projection](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/BasePushNotificationSender.java#L224) ·
[Identity registration](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L19)

### Verified corrections and scope

- The original joined-worker repro now sends one legacy status-only summary and
  each canonical 1.1 event once. The same-thread multi-event probe also passes.
  The fresh repository test verifies a joined-worker wrapper that forwards
  exact objects for status-only, artifact-only and mixed proposals under
  deferred admission. The scope probe confirmed matching objects are recognized
  across threads, equal-valued distinct objects stay isolated, and exceptions
  remove the registration.
- The correction changes the sender registry and its existing test. The
  previously reviewed stream-demand, direct Message, opening metadata,
  committed unary response, and creation-race implementations are unchanged.
  The plan and retained regression selectors were rerun. No all-binding
  multi-event matrix, full reactor, or later phase work was requested.

### Fresh verification

The six handoff commands ran from the assigned worktree. The complete commands
are archived in `run.sh` under
`/private/tmp/a2a-p3-t05-rereview3.0f7fkttb/`. Each Maven run used
`/usr/bin/timeout -k 30s 1200s`, `mvn -B -ntp`,
`-Dsurefire.failIfNoSpecifiedTests=false`, and
`-Dmaven.test.failure.ignore=false`. All started with more than 20 minutes
remaining. Original Surefire reports were archived before execution. Fresh
XML and output were copied after each run. Compilation was incremental, and
the corrected registry and sender test class timestamps postdate their source
changes. The independent probes exercised the new classpath behavior.

The handoff's plan command has a slash in the agent filename
(`byte-buddy-agent/1.18.8.jar`). Both executions of that written command failed
at fork startup with zero tests. The installed agent passed its archive
integrity check. The plan selector then passed with the corrected filename
`byte-buddy-agent-1.18.8.jar`, using the same agent path as the five other
commands. The tool output and first two fork failure logs are retained.
[Recorded selectors](../handoffs/P3-T05-correction-3.md#verification-evidence)

| Log | Finished UTC | Exit | Executed | Failures | Errors | Skips |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `sender.log` | 13:42:03 | 0 | 1 | 0 | 0 | 0 |
| `plan.log` | 13:42:06 | 1 | 0 | 0 | 0 | 0 |
| `server.log` | 13:42:39 | 0 | 201 | 0 | 0 | 0 |
| `replication.log` | 13:42:43 | 0 | 1 | 0 | 0 | 0 |
| `bindings.log` | 13:42:50 | 0 | 8 | 0 | 0 | 0 |
| `otel.log` | 13:42:53 | 0 | 9 | 0 | 0 | 0 |
| `plan-retry.log` | 13:43:00 | 1 | 0 | 0 | 0 | 0 |
| `plan-fixed-agent.log` | 13:43:19 | 0 | 44 | 0 | 0 | 0 |

The four retained regression selectors executed **219** cases. They overlap
the required and focused selectors. The implementor's retained red/green logs
show the worker-thread test failing with 10 older-version deliveries versus
6 expected on the old registry, followed by the passing run.

The probe classpath came from the fresh focused sender XML, saved as
`classpath`. All probe sources, compiled classes and logs are in the review
temporary directory. The accepted bus control compiled
`git show HEAD:server-common/src/main/java/org/a2aproject/sdk/server/events/MainEventBusProcessor.java`
and prepended only that class and its nested classes to the candidate
classpath. The following records the probe commands:

```sh
p=/private/tmp/a2a-p3-t05-rereview3.0f7fkttb
cp_value=$(cat "$p/classpath")
agent=/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$p" "$p"/Proposal*.java
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$p/base-bus" "$p/base-bus/MainEventBusProcessor.java"
run_probe() {
  /usr/bin/timeout -k 30s 90s java -Dprobe.webhook.count="$2" \
    -Dlogback.configurationFile="$p/quiet-logback.xml" -javaagent:"$agent" \
    -cp "$3$p:$cp_value" "org.a2aproject.sdk.server.tasks.$1"
}
run_probe ProposalWebhookProbe 8 ""
run_probe ProposalOffloadedWebhookProbe 7 ""
run_probe ProposalStatusOnlyHookProbe 5 ""
run_probe ProposalStatusOnlyHookProbe 6 "$p/base-bus:"
run_probe ProposalCopyingWebhookProbe 8 ""
run_probe ProposalCopyingWebhookProbe 7 "$p/base-bus:"
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$p" "$p/DispatchRegistryScopeProbe.java"
/usr/bin/timeout -k 30s 90s java -cp "$p:$cp_value" org.a2aproject.sdk.server.tasks.DispatchRegistryScopeProbe
```

All probes exited 0. The webhook probe logs use the class name and expected
delivery count, and the registry log is `registry-scope.log`. The recorded
event sequences establish the regressions above. `git diff --check` passed.
Only this append and the T05 coordination row were edited. Candidate source,
tests, prior reviews and the untracked `test.log` were preserved.

## Correction 4 independent re-review — 2026-11-20 14:11 UTC

**Disposition — Changes required.** The two status-only regressions are
closed. The same wrappers still drop or duplicate legacy Task summaries
when the proposal changes both status and artifacts. The exact required,
focused, and retained regression selectors passed.

### Assignment and candidate

This separate re-review started at **14:09:57 UTC**, with a deadline of
**16:09:57 UTC**. Requested settings were Sol, medium reasoning, normal
speed. Serving metadata was unavailable. HEAD was
`81dac751c1ab1ce405fc579c2a4873559ad38813` on `support/a2a-1.1.0`, with no
descendant commits in local refs and nothing staged. Prior dispositions
remain historical evidence. The plan and phase summary still carry stale
hold language from the T04 checkpoint. [Correction 4 handoff](../handoffs/P3-T05-correction-4.md)

| Candidate evidence | SHA-256 |
| --- | --- |
| Tracked diff from HEAD at start | `f25cd0e0b17c130ebcb935655d1f475bbc8e58e793a102df295f830639f21a09` |
| Tracked server-common and OpenTelemetry diff | `ee9331ddb4e3591584313c51450d4a42cbb0ff08a47b16f1f0f47440acdb7b77` |
| `PushNotificationSender.java` | `fc6efa49414500539baacdc67f76705cb6a50bc14c472eae21e916c5d367a085` |
| `PushNotificationSenderTest.java` | `2a30d1c98ea0f400adb0a6ed422c5b458547f3ed75700a0a3255191822a846d9` |
| Untracked `TaskProposalDispatchContext.java` | `fe48fd74a6cf5926d4c84e23247473acb4a72d85998134351e88258e54736926` |
| Correction 4 handoff | `d3517646ed371a4ed67c9a5d152f55a38d5850c1e1106afa4176506db774e9b1` |
| Review before this append | `455697065c2daeb62a02184f4d72630f80e6e1d4901c2490636752dfafb26984` |

### P1 — Mixed proposals still lose or duplicate the legacy summary through the two existing wrappers

Correction 4 restores the existing status hook only when the batch has one
status event. Adding a single agent artifact switches to the fallback that
sends a summary through the optional no-op hook and suppresses legacy
projection in the status hook based on event object identity. The two
dependencies that caused the status-only defects are both still live on
this T05 batch shape.

The independent deferred-dispatch replay kept the wrappers unchanged and
added one artifact named `answer` to the status proposal:

| Existing worker-thread wrapper | Legacy proposal summaries | Active no-op summaries | 1.1 proposal output |
| --- | ---: | ---: | --- |
| Forwards status and ordinary send, inherits the optional no-op method | 0 | 0 | One status, one artifact |
| Forwards all three methods and rebuilds the status as an equal value | 2 | 1 | One status, one artifact |

Both duplicate summaries contained `answer`. The no-op summary in the second
trace had no artifacts. Earlier and later ordinary status notifications
arrived in both versions. In the status-only controls the same wrappers each
delivered one proposal summary and retained their respective no-op behavior.
The full forwarding wrapper that preserves the event objects also delivered
one summary in the mixed control.

Verify the two compatibility wrappers against a status-plus-artifact batch
through the ordered bus, with deferred admission and mixed 1.0/1.1 configs.
Each older destination must receive one final proposal Task containing the
artifact and zero covered deltas. Each 1.1 destination must receive the
canonical status and artifact once in order. Preserve each wrapper's
independent choice about the active no-op. This is the T05 per-version
projection rule and A07. It does not require expanded no-op behavior or
later-phase orchestration.
[Status-only and multi-event routes](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/PushNotificationSender.java#L149) ·
[Conditional legacy suppression](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/BasePushNotificationSender.java#L224) ·
[Identity matching](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L32)

### Verified scope

The independent status-only replays and both new focused tests passed.
The full forwarding same-thread and joined-worker controls passed. The registry
scope probe confirmed that the exact registered event is visible to the
worker, an independent equal-valued event is isolated, and the registration
is cleaned up on exception. Prior source and review evidence for opening input,
unary provenance, stream demand, direct Message, and creation races remain
applicable. Fresh retained selectors included the ordered admission, queue
and lifecycle, sender, replication, and eight binding cases. The independent
probes used in-memory authority and mixed 1.0/1.1 webhook configs. Full
reactor, all-binding multi-event coverage, and later tasks were outside scope.

### Fresh execution evidence

All six exact handoff commands ran from this worktree with the installed Byte
Buddy 1.18.8 agent, `/usr/bin/timeout -k 30s 1200s`, `mvn -B -ntp`,
`-Dsurefire.failIfNoSpecifiedTests=false`, and
`-Dmaven.test.failure.ignore=false`. All started with more than 20 minutes
remaining. Existing Surefire reports were archived before the first run. Each
run saved fresh XML and output using its own timestamp marker. The first
selector recompiled 117 server-common production sources and 59 test sources.
The historical red log contained two failures for the expected status-only
traces. Those failures were absent from the fresh candidate runs.
[Exact Maven commands](../handoffs/P3-T05-correction-4.md#verification-evidence)

| Log | Finished UTC | Exit | Executed | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `sender.log` | 14:10:13 | 0 | 2 | 0 | 0 | 0 |
| `plan.log` | 14:10:17 | 0 | 44 | 0 | 0 | 0 |
| `server.log` | 14:10:51 | 0 | 203 | 0 | 0 | 0 |
| `replication.log` | 14:10:55 | 0 | 1 | 0 | 0 | 0 |
| `bindings.log` | 14:11:05 | 0 | 8 | 0 | 0 | 0 |
| `otel.log` | 14:11:09 | 0 | 9 | 0 | 0 | 0 |

The four retained regression selectors executed **221** cases and overlap the
required and focused selectors. Evidence is under
`/private/tmp/a2a-p3-t05-rereview4.8801h0_2/`, including `run.sh`, fresh
reports and probe source. The probes were compiled on the classpath from the
fresh sender Surefire XML. All probe executions exited 0. Their recorded
event counts and contents establish the remaining finding.

```sh
p=/private/tmp/a2a-p3-t05-rereview4.8801h0_2
cp_value=$(cat "$p/classpath")
agent=/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$p" "$p"/Proposal*.java "$p/DispatchRegistryScopeProbe.java"
run_probe() {
  /usr/bin/timeout -k 30s 90s java -Dprobe.webhook.count="$2" \
    -Dlogback.configurationFile="$p/quiet-logback.xml" -javaagent:"$agent" \
    -cp "$p:$cp_value" "org.a2aproject.sdk.server.tasks.$1"
}
run_probe ProposalWebhookProbe 8
run_probe ProposalOffloadedWebhookProbe 7
run_probe ProposalStatusOnlyHookProbe 6
run_probe ProposalCopyingWebhookProbe 7
run_probe ProposalStatusOnlyHookMixedProbe 6
run_probe ProposalCopyingWebhookMixedProbe 9
run_probe DispatchRegistryScopeProbe 0
```

Logs are named after the probe class and its count. The detailed mixed logs
end in `-detail.log` and list the artifacts carried by each summary.
`git diff --check` passed. Only this append and the T05 coordination row
were edited. Candidate source, tests, historical reviews, and untracked
`test.log` were preserved.

## Correction 5 independent re-review — 2026-11-20 14:18 UTC

**Disposition — Changes required.** Both correction-4 mixed-wrapper
reproductions now pass. Legacy projection still loses an artifact-only
proposal through the wrapper with the inherited no-op hook, and still
duplicates covered output when a wrapper forwards a copied artifact event.

### Assignment and candidate

This separate re-review started at **14:17:21 UTC**, with a deadline of
**16:17:21 UTC**. Requested settings were Sol, medium reasoning, normal
speed. Serving metadata was unavailable. HEAD was
`81dac751c1ab1ce405fc579c2a4873559ad38813` on `support/a2a-1.1.0`, with no
descendant commits in local refs and nothing staged. Prior dispositions
remain historical evidence. The plan and phase summary's old hold language
predates the assigned T05 review. [Correction 5 handoff](../handoffs/P3-T05-correction-5.md)

| Candidate evidence | SHA-256 |
| --- | --- |
| Tracked diff from HEAD at start | `ccf3b6e8700ffcb5c530c2703c1be187ec8394cfefd9df3bf4749aafa877b839` |
| Tracked server-common and OpenTelemetry diff | `b0e3a615e66535dd4f0e606609ce8e3a476660eb3154dad02a626710e3459388` |
| `PushNotificationSender.java` | `125723501511ad646ba95baee05484daf8f1e4793d3e6fe3a065afca179912ad` |
| Untracked `TaskProposalDispatchContext.java` | `973a5673e3e752bc00fbe9ff44d5f39686cbeab1964a8f582c66f1dd1ee2f654` |
| `PushNotificationSenderTest.java` | `d9536681410c77092c1e72984526704d017edbbbc317a3db4b4c88324fa4e841` |
| Correction 5 handoff | `30d058505575ee6d0b6f909fb35bd8ec0d1f701b71ac9f6572f8f9a00ca1a538` |
| Review before this append | `38a491316de25d40e516924c0805e7e8af1278b3d855cf355d805240be5e4886` |

### P1 — Legacy artifact projection still depends on the optional no-op hook and event identity

The updated sender uses the status hook for the summary of a batch containing
a status. An artifact-only batch has no status, so the summary is still
sent through the optional active-no-op hook. The subsequent artifact update
is filtered out for legacy destinations by the registered dispatch context.
For the same joined-worker wrapper that inherits the no-op method, removing
the status change from the proposal caused the 1.0 destination to receive
**zero** proposal notifications. It received its surrounding ordinary
statuses, and 1.1 received the proposal's canonical artifact. The wrapper's
choice to skip the active no-op should be independent of changed proposal
delivery.

The status-plus-artifact replay also tested the forwarding wrapper with
`TaskArtifactUpdateEvent.builder(update).build()` at its ordinary
notification method, mirroring the copied status in the preceding correction.
The 1.0 destination received a Task containing `answer` followed by the
covered `TaskArtifactUpdateEvent`. The copied event missed the scoped
identity lookup and was dispatched as an ordinary artifact. The 1.1
destination received the status and artifact once in order. The active no-op
still produced exactly one legacy summary.

Check artifact-only and mixed proposals in the same sender compatibility
matrix. For the inherited no-op method, require one final legacy Task for
an artifact-only proposal and no active-no-op delivery. For a wrapper that
copies the artifact, require one final legacy Task and zero covered artifact
updates. Both should still receive canonical 1.1 events in order. Keep the
ordinary equal-valued event isolated from the proposal's projection.
These are T05 projection and A07 cases for the already supported batch
shapes and sender seams.
[Batch summary routes](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/PushNotificationSender.java#L157) ·
[Artifact dispatch](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/BasePushNotificationSender.java#L200) ·
[Identity lookup](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L46)

### Verified scope

- Both original correction-4 mixed replays returned one legacy proposal
  Task containing `answer`, with status then artifact on 1.1 and the expected
  active no-op behavior. Status-only, same-thread and exact-object joined-worker
  controls also passed.
- The registry probe confirmed that the registered object is visible to the
  worker, a separate equal-valued event remains isolated, and exception cleanup
  removes the registration.
- The prior stream, direct-response, opening-input, unary and creation-race
  implementation remains unchanged. The retained selectors cover queue
  admission and lifecycle, sender, replication, and the eight binding
  assertions. These probes used the ordered in-memory bus and mixed 1.0/1.1
  webhook configs. Full reactor, all-binding multi-event coverage, and T06
  and later work were outside this review.

### Fresh execution evidence

The six exact handoff commands ran under `/usr/bin/timeout -k 30s 1200s`
with the installed Byte Buddy 1.18.8 agent, `mvn -B -ntp`,
`-Dsurefire.failIfNoSpecifiedTests=false`, and
`-Dmaven.test.failure.ignore=false`. All started with over 20 minutes
remaining. Existing Surefire reports were archived first, and each run
copied fresh XML and output after a per-run timestamp marker. Server-common
used incremental compilation, and the dispatch classes' timestamps postdate
the reviewed changes. The historical red log reproduced both mixed-wrapper
failures, and the fresh focused selector confirmed their correction.
[Exact Maven selectors](../handoffs/P3-T05-correction-5.md#verification-evidence)

| Log | Finished UTC | Exit | Executed | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `sender.log` | 14:17:34 | 0 | 2 | 0 | 0 | 0 |
| `plan.log` | 14:17:37 | 0 | 44 | 0 | 0 | 0 |
| `server.log` | 14:18:11 | 0 | 205 | 0 | 0 | 0 |
| `replication.log` | 14:18:13 | 0 | 1 | 0 | 0 | 0 |
| `bindings.log` | 14:18:17 | 0 | 8 | 0 | 0 | 0 |
| `otel.log` | 14:18:20 | 0 | 9 | 0 | 0 | 0 |

The four retained regression selectors executed **223** cases. They overlap
the required and focused selectors. The runner, reports, and probes are in
`/private/tmp/a2a-p3-t05-rereview5.f4gwjoxi/`. The probes used the
`java.class.path` from fresh sender Surefire XML. All compiles and probe
executions exited 0. Their event sequences establish the reproduced defects.

```sh
p=/private/tmp/a2a-p3-t05-rereview5.f4gwjoxi
cp_value=$(cat "$p/classpath")
agent=/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$p" "$p"/Proposal*.java "$p/DispatchRegistryScopeProbe.java"
run_probe() {
  /usr/bin/timeout -k 30s 90s java -Dprobe.webhook.count="$2" \
    -Dlogback.configurationFile="$p/quiet-logback.xml" -javaagent:"$agent" \
    -cp "$p:$cp_value" "org.a2aproject.sdk.server.tasks.$1"
}
run_probe ProposalWebhookProbe 8
run_probe ProposalOffloadedWebhookProbe 7
run_probe ProposalStatusOnlyHookProbe 6
run_probe ProposalCopyingWebhookProbe 7
run_probe ProposalStatusOnlyHookMixedProbe 7
run_probe ProposalCopyingWebhookMixedProbe 8
run_probe ProposalStatusOnlyHookArtifactProbe 5
run_probe ProposalCopyingArtifactWebhookProbe 9
run_probe DispatchRegistryScopeProbe 0
```

Each probe log uses its class name and count. `git diff --check` passed.
Only this append and the T05 coordination row were edited. Candidate
sources, tests, historical reviews, and untracked `test.log` were preserved.

## Correction 6 independent re-review — 2026-11-20 14:36 UTC

**Disposition — Changes required.** Both correction-5 artifact reproductions
are closed, and the later equal-valued ordinary event is delivered. The
no-status fallback now duplicates output for a version-unaware sender, and a
concurrent ordinary call can be captured by an active proposal scope.

### Assignment and candidate

This separate re-review started at **14:34:48 UTC**, with a deadline of
**16:34:48 UTC**. Requested settings were Sol, medium reasoning, normal
speed. Serving metadata was unavailable. HEAD was
`81dac751c1ab1ce405fc579c2a4873559ad38813` on `support/a2a-1.1.0`, with no
descendant commits in local refs and nothing staged. Earlier dispositions
remain historical evidence. The old T05 hold language in the plan and phase
summary predates the current assignment. [Correction 6 handoff](../handoffs/P3-T05-correction-6.md)

| Candidate evidence | SHA-256 |
| --- | --- |
| Tracked diff from HEAD at start | `763beb907f501e5177f07418a9e30e4f0f586a73917c74123ab05ec4a7ac144a` |
| Tracked server-common and OpenTelemetry diff | `fc2d04c4e25564cdbc49838a974e97a94758a0525726b9f720c1cc148768ac11` |
| `PushNotificationSender.java` | `9b4c16ed6cae9372237d1eb9b0e2212ff547b039ac4158dcf75fb52ca5856ac8` |
| `BasePushNotificationSender.java` | `e6a18b905a6e420a11d13557dcfabd492c202adc2589f7cb416a295d5cf76157` |
| Untracked `TaskProposalDispatchContext.java` | `fceed3e0dc7c49aab954bec5e9760a85a4b4df048a8320b7433818e7f526f7a2` |
| `PushNotificationSenderTest.java` | `2e9a8f255f891628af76b2333dccb24a1bdf181494faf69e6a8ee847ad6bb757` |
| Correction 6 handoff | `d008a1943ea773fce03a65ffc4140c167780697c44fb32da03f0cadf4cdc4aa8` |
| Review before this append | `e30da0795ef63b2ec5d35841ed2e8916068f9e0c3548af8162d0a5db7b101b53` |

### P1 — A version-unaware sender receives a summary and the covered artifact

The default no-status path now calls the public `sendNotification` method
with the final Task before forwarding canonical events. The scoped summary
marker is consumed by the SDK base sender. A custom implementation of the
established ordinary method, such as the forwarding-only Kafka sender shown
in the interface Javadoc, has no such projection. A minimal sender that
records every event passed to its ordinary method received
`[Task, TaskArtifactUpdateEvent]` for a proposal containing one canonical
artifact update. The Task already contained the artifact.

Keep the established canonical fallback for senders without version-aware
projection. Verify that a sender implementing only the ordinary method
receives exactly one canonical event for an artifact-only proposal, while
the version-aware worker-wrapper control receives one summary for 1.0 and
one artifact event for 1.1. This applies the T04 sender fallback checkpoint
and the T05 requirement to deliver one output choice per recipient.
[Public custom sender example](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/PushNotificationSender.java#L49) ·
[Default dispatch](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/PushNotificationSender.java#L176) ·
[SDK projection](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/BasePushNotificationSender.java#L217)

### P2 — An equal ordinary artifact is suppressed if it overlaps an active proposal call

The cross-thread scope uses a process-wide set whose lookup compares artifact
value and final Task object identity. Those checks establish which value is
being forwarded but cannot identify which call sent it. An independent
ordinary call using the same committed snapshot can match while a proposal
worker is still active.

A barrier in the forwarding wrapper paused the worker before it delegated the
artifact copy. During that pause the probe called the base sender's ordinary
method with a separate equal-valued artifact and the same Task snapshot.
The 1.1 destination received that call's artifact and 1.0 received nothing
beyond the already-sent proposal summary. After releasing the worker and
joining proposal delivery, the same ordinary call reached both destinations.
This tests the public sender seam. It does not require or demonstrate two
concurrent commits on the same task through the ordered bus.

Keep the projection bound to the forwarding call's provenance. Verify the
worker's copied event remains covered while an independent equal-valued
ordinary call during the pause still reaches both destinations. Keep the
existing after-exit and exception-cleanup controls. This is the remaining
ordinary-event isolation case for A07 and T05.
[Scope activation](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L42) ·
[Copy matching](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L86) ·
[Ordinary sender selector](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/BasePushNotificationSender.java#L221)

### Verified scope and execution

- Both prior artifact reproductions now emit one legacy Task summary and
  retain ordered canonical 1.1 output. The focused repository test confirms
  that an equal ordinary artifact sent after dispatch reaches both versions.
  Status-only, mixed, no-op, same-thread, and joined-worker controls also
  passed. Identity lookup and exception cleanup passed the registry probe.
- The prior stream, direct response, opening metadata, unary provenance, and
  creation-race implementation is unchanged. Fresh retained selectors covered
  queue admission and lifecycle, sender, replication, and eight binding cases.
  Full-reactor and later task work were outside this review.
- All six exact handoff commands ran from the worktree with the documented
  Byte Buddy agent, `/usr/bin/timeout -k 30s 1200s`, `mvn -B -ntp`,
  `-Dsurefire.failIfNoSpecifiedTests=false` and
  `-Dmaven.test.failure.ignore=false`. All started with over 20 minutes
  remaining. Existing Surefire reports were archived first. Each selector
  copied fresh reports and output after its own timestamp marker. Incremental
  class timestamps postdate the reviewed source changes. The implementor's
  historical red log records both earlier artifact defects.
  [Exact commands](../handoffs/P3-T05-correction-6.md#verification-evidence)

| Log | Finished UTC | Exit | Executed | Failures | Errors | Skips |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `sender.log` | 14:35:01 | 0 | 2 | 0 | 0 | 0 |
| `plan.log` | 14:35:05 | 0 | 44 | 0 | 0 | 0 |
| `server.log` | 14:35:39 | 0 | 207 | 0 | 0 | 0 |
| `replication.log` | 14:35:41 | 0 | 1 | 0 | 0 | 0 |
| `bindings.log` | 14:35:45 | 0 | 8 | 0 | 0 | 0 |
| `otel.log` | 14:35:48 | 0 | 9 | 0 | 0 | 0 |

The four retained regression selectors executed **225** cases. They overlap
the required and focused selectors. The runner, reports, and probe sources
are in `/private/tmp/a2a-p3-t05-rereview6.wzvgkdw0/`. The probes used the
`java.class.path` from fresh sender XML. All compiled and exited 0, with
their logged event sequences establishing the remaining findings.

```sh
p=/private/tmp/a2a-p3-t05-rereview6.wzvgkdw0
cp_value=$(cat "$p/classpath")
agent=/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$p" "$p"/Proposal*.java "$p/DispatchRegistryScopeProbe.java"
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$p" "$p/ConcurrentProposalScopeProbe.java"
run_probe() {
  /usr/bin/timeout -k 30s 90s java -Dprobe.webhook.count="$2" \
    -Dlogback.configurationFile="$p/quiet-logback.xml" -javaagent:"$agent" \
    -cp "$p:$cp_value" "org.a2aproject.sdk.server.tasks.$1"
}
run_probe ProposalWebhookProbe 8
run_probe ProposalOffloadedWebhookProbe 7
run_probe ProposalStatusOnlyHookProbe 6
run_probe ProposalCopyingWebhookProbe 7
run_probe ProposalStatusOnlyHookMixedProbe 7
run_probe ProposalCopyingWebhookMixedProbe 8
run_probe ProposalStatusOnlyHookArtifactProbe 6
run_probe ProposalCopyingArtifactWebhookProbe 8
run_probe DispatchRegistryScopeProbe 0
run_probe ConcurrentProposalScopeProbe 0
```

The two new traces are in `concurrent-probe.log`. The prior replay logs use
the probe class name and count. `git diff --check` passed. Only this append
and the T05 coordination row were edited. Candidate source, tests, prior
reviews, and untracked `test.log` were preserved.

## Correction 7 independent re-review — 2026-11-20 14:50 UTC

**Disposition — Changes required.** Both correction-6 findings are closed in
the original reproductions. Two forwarding cases still break per-destination
projection. An equal copy of the Task snapshot lets a covered artifact through,
and forwarding one artifact to two SDK sender instances loses the second
sender's legacy summary.

### Assignment and candidate

This separate re-review started at **14:49:24 UTC**, with a deadline of
**16:49:24 UTC**. Requested settings were Sol, medium reasoning, normal
speed. Serving metadata was unavailable. HEAD was
`81dac751c1ab1ce405fc579c2a4873559ad38813` on `support/a2a-1.1.0`, with no
descendant commits in local refs and nothing staged. Earlier dispositions are
preserved. The plan and phase summary carry hold language from before the
current assignment. [Correction 7 handoff](../handoffs/P3-T05-correction-7.md)

| Candidate evidence | SHA-256 |
| --- | --- |
| Tracked diff from HEAD at start | `2839270d6cc54f317c28992c4e2c76df526612d49cf8f7230cca120fd55d0d5d` |
| Tracked server-common and OpenTelemetry diff | `3c2e8d04de5f08c38fd53ee42775a7dc2c368ae8fbef456bce47a258ed2be9a9` |
| `PushNotificationSender.java` | `652ff75e672d09268b1fe61c8b6bf7f55af4081eed1eba128c99b8fc89b0e694` |
| `BasePushNotificationSender.java` | `6f17b31a25abe49562bd6a7595ee6d5bedee2c74e123346481194dffd1639fd7` |
| Untracked `TaskProposalDispatchContext.java` | `47cc9b2d7b3045df74dd4bf00e0833b74f5532cf01c5078e51eb97fe9e37f2db` |
| `PushNotificationSenderTest.java` | `e81b452cbf0ac633259fee6abb42806e63a6a0eae97fd43d43c095a1a5583dc5` |
| Correction 7 handoff | `2cafb92ff589c88a63488e94eee59074d3b7091d6f1f0fb326ae1899c17eef04` |
| Review before this append | `99644f2a1ac654a8be9e6e6f0b08eb9679e48e197abe9fd30843280327828bed` |

### P1 — An equal forwarded snapshot loses proposal classification for the original canonical event

The candidate requires both the exact event and the exact dispatch Task
object for identity lookup. Before this correction, an exact event's lookup
compared the snapshot by value. The prior sender methods accept a Task
snapshot and do not require preserving its object identity.

The deferred, mixed-version handler probe used the existing three-method
wrapper and changed only ordinary forwarding to pass
`Task.builder(snapshot).build()`, preserving the exact artifact event.
The 1.0 destination received its proposal Task containing the new artifact
and a separate covered artifact delta. The 1.1 destination received the
expected canonical status and artifact once. The unmodified forwarding
control emitted one 1.0 proposal summary and zero covered deltas. The
version-unaware and concurrent ordinary-event fixes also passed.

Preserve proposal classification when a wrapper forwards the original
canonical event with an equal Task copy. Verify one legacy summary and no
covered delta under deferred admission. Keep the correction-6 controls for
a copied artifact forwarding the dispatch snapshot, and for a concurrent
equal ordinary event using the committed snapshot. This reproduces an
observable sender compatibility and A07/T05 projection failure with an
exact event and an equal Task value.
[Dispatch snapshot](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/PushNotificationSender.java#L158) ·
[Identity lookup](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L71) ·
[Ordinary sender fallback](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/BasePushNotificationSender.java#L217)

### P1 — The legacy summary claim is shared across independent SDK sender destinations

The scope allows exactly one `claimLegacySummary` for the entire default
dispatch. That grant is consumed by the first SDK sender to handle a scoped
event. A wrapper can use the existing notification method to forward to
two SDK senders with independent configurations. The second sender then
filters out all legacy output even when its configs have received no summary.

The independent artifact-only probe used a simple forwarding wrapper and
two SDK sender instances with disjoint config IDs and destinations. Both
received the exact event and snapshot from the caller. Each instance had a
1.0 and a 1.1 config. The first instance sent its 1.0 Task summary and 1.1
artifact. The second sent only its 1.1 artifact. This is a direct public
sender test with one committed batch. It does not depend on multiple
writers or on a shared config store.

Apply the one-summary requirement at the recipient projection boundary.
Verify one Task and zero covered deltas at each legacy destination when a
wrapper forwards the same artifact-only or multi-artifact batch to both
delegates. Verify each 1.1 destination gets each canonical event once. This
is a T05 per-recipient projection failure and A07, independent of the
copied-snapshot trace.
[Shared summary claim](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L61) ·
[Per-config dispatch](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/BasePushNotificationSender.java#L216)

### Verified scope and execution

- The correction-6 version-unaware sender repro now receives only its
  canonical artifact. The paused-worker repro delivers the independent
  equal ordinary artifact to both versions during the pause and after the
  proposal ends. The proposal receives one legacy Task. The same-thread
  and joined-worker controls, status-only, mixed, artifact-only, copied
  event, no-op, and exception cleanup replays also passed within the
  specific forwarding cases covered by those probes.
- The prior stream, direct-response, opening metadata, unary provenance,
  and creation-race implementation is unchanged. Fresh retained selectors
  covered queue admission and lifecycle, sender, replication and the eight
  binding cases. The independent webhook probes use test HTTP clients with
  1.0 and 1.1 configs. Full reactor, all-binding multi-event coverage and
  subsequent tasks were outside this review.
- The exact required, regression, and full sender commands ran from the
  worktree. The focused command removes the two stale method names identified
  in the handoff. Each run used `/usr/bin/timeout -k 30s 1200s`,
  `mvn -B -ntp`, `-Dsurefire.failIfNoSpecifiedTests=false`,
  `-Dmaven.test.failure.ignore=false`, and the installed Byte Buddy agent.
  All started with more than 20 minutes remaining. Earlier Surefire reports
  were archived before the runs. Fresh XML and output were copied using
  per-run markers. Compilation was incremental, and the compiled dispatch
  classes postdate their source changes. The implementor's historical red
  log shows the exact two correction-6 reproductions.
  [Handoff commands and evidence](../handoffs/P3-T05-correction-7.md#verification-evidence)

| Log | Finished UTC | Exit | Executed | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `plan.log` | 14:49:39 | 0 | 44 | 0 | 0 | 0 |
| `server.log` | 14:50:13 | 0 | 209 | 0 | 0 | 0 |
| `replication.log` | 14:50:16 | 0 | 1 | 0 | 0 | 0 |
| `bindings.log` | 14:50:20 | 0 | 8 | 0 | 0 | 0 |
| `otel.log` | 14:50:22 | 0 | 9 | 0 | 0 | 0 |
| `focused.log` | 14:50:25 | 0 | 4 | 0 | 0 | 0 |
| `sender.log` | 14:50:28 | 0 | 40 | 0 | 0 | 0 |

The four retained regression selectors executed **227** cases, overlapping
the required and sender selectors. Exact commands, reports and probe sources
are under `/private/tmp/a2a-p3-t05-rereview7.u0jzlovo/`. The probes used the
`java.class.path` saved from Surefire XML. Each final probe execution exited
0. Its observed event sequence records whether the candidate met the
contract.

```sh
p=/private/tmp/a2a-p3-t05-rereview7.u0jzlovo
cp_value=$(cat "$p/classpath")
agent=/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$p" "$p"/Proposal*.java "$p/DispatchRegistryScopeProbe.java" "$p/ConcurrentProposalScopeProbe.java"
run_probe() {
  /usr/bin/timeout -k 30s 90s java -Dprobe.webhook.count="$2" \
    -Dlogback.configurationFile="$p/quiet-logback.xml" -javaagent:"$agent" \
    -cp "$p:$cp_value" "org.a2aproject.sdk.server.tasks.$1"
}
run_probe ProposalWebhookProbe 8
run_probe ProposalOffloadedWebhookProbe 7
run_probe ProposalStatusOnlyHookProbe 6
run_probe ProposalCopyingWebhookProbe 7
run_probe ProposalStatusOnlyHookMixedProbe 7
run_probe ProposalCopyingWebhookMixedProbe 8
run_probe ProposalStatusOnlyHookArtifactProbe 6
run_probe ProposalCopyingArtifactWebhookProbe 8
run_probe DispatchRegistryScopeProbe 0
run_probe ConcurrentProposalScopeProbe 0
run_probe ProposalCopiedSnapshotProbe 9
run_probe ProposalMultiDelegateProbe 0
```

The new evidence is in `ProposalCopiedSnapshotProbe-9.log` and
`fanout.log`. The first fanout compile failed because the probe named the
wrong URL validator. The retained `fanout-build-attempt1.log` records that
probe-only error. The final probe used the fixture's validator. `git diff
--check` passed. Only this append and the T05 coordination row were edited.
Candidate sources, tests, prior reviews, and untracked `test.log` were
preserved.

## Correction 8 independent re-review — 2026-11-20 15:26 UTC

**Disposition — Changes required.** The original equal-snapshot and
two-sender failures are closed. The prior copied-artifact and concurrent
ordinary-event controls also passed. The forwarding path still confuses
proposal and ordinary calls when both values are copied or when a separate
ordinary call reuses the original published pair.

### Assignment and candidate

This independent re-review started at **15:25:21 UTC**, with a deadline of
**17:25:21 UTC**. Requested settings were Sol, medium reasoning, normal
speed. Serving metadata was unavailable. HEAD was
`81dac751c1ab1ce405fc579c2a4873559ad38813` on `support/a2a-1.1.0`, with no
descendant commits in local refs and nothing staged. Earlier dispositions are
preserved. The older plan and phase status hold language predates this
assignment. [Correction 8 handoff](../handoffs/P3-T05-correction-8.md)

| Candidate evidence | SHA-256 |
| --- | --- |
| Tracked diff from HEAD at start | `81bd199a54f01cb15d23e7ac1c0beaabe53bf4d26fae2d180e8ffa82b73eb884` |
| Tracked server-common and OpenTelemetry diff | `1a55aa553abfd7d4bbe186a1f4960410cdf171d9f4d08051118bef9a0cc41371` |
| `PushNotificationSender.java` | `652ff75e672d09268b1fe61c8b6bf7f55af4081eed1eba128c99b8fc89b0e694` |
| `BasePushNotificationSender.java` | `d84ebcd2a8a88c453ccef1d9ecf4671f8b701572ef991f3b1ec24e98ca15c208` |
| Untracked `TaskProposalDispatchContext.java` | `73eafdb284f86518e18b3e122700e728c83c3c2da7ab926d76be7db46e899c3a` |
| `PushNotificationSenderTest.java` | `6da4725d3c8d6f03950b326ff2228fc5af1edf6ac1e7a6132d2bfb71906ff6f3` |
| Correction 8 handoff | `fb40a6dcaea04b224984befda74e94353423cdc0b471351d4e0fe21e7866df3b` |
| Review before this append | `1a1ee896f0f74cbc12fee1252e763895966f817c3861a165565c34c1e4ab36a3` |

### P1 — Copying both forwarding arguments duplicates the covered legacy artifact

The existing ordinary sender method accepts the artifact event and the final
Task. Correction 8 recognizes either the original event with an equal Task,
or a copied artifact with the exact dispatch Task. Neither test recognizes
the pair when a wrapper rebuilds both as equal immutable values.

The deferred mixed-version bus replay combined the two previously tested
copies in the same pre-batch wrapper. It forwarded
`TaskArtifactUpdateEvent.builder(update).build()` and
`Task.builder(snapshot).build()` without changing either value. The 1.0
destination received one proposal Task containing `answer` and then the
covered artifact delta. The 1.1 destination received the canonical status
and artifact once in order. The separate original-event/copied-Task and
copied-event/original-dispatch-Task controls each passed.

Exercise the existing forwarding interface with both equal copies and
verify one final legacy summary, zero covered deltas and the ordered 1.1
events. The projection needs an explicit way to carry forwarding intent
when a wrapper preserves values and changes both object identities. If the
compatibility contract is to require wrappers to preserve one identity or
adopt a new forwarding method, record that restriction as a design decision.
This is the existing T05 per-recipient projection rule and A07.
[Original event lookup](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L88) ·
[Copied artifact lookup](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L103) ·
[Fallback dispatch](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/BasePushNotificationSender.java#L217)

### P2 — An ordinary call can claim the proposal summary when it reuses the committed pair

An independently initiated ordinary call with the original published event
and final Task matches the same value-and-identity lookup as a wrapper that
forwards the original event with an equal copy. The per-sender claim can
therefore be granted to the ordinary call while proposal forwarding is
paused.

The direct public sender probe reused the prior barrier test and changed only
the independent call's artifact from an equal copy to the exact canonical
event. It passed the original committed Task. While the wrapper was paused
before its delegate call, 1.0 received a Task summary in place of the ordinary
artifact, and 1.1 received the artifact. On release the proposal call delivered
no further 1.0 output. The same ordinary call after scope exit reached both
versions as an artifact. The distinct equal-event control still passed. This
exercises the public sender seam with one committed batch and does not rely
on reentrant TaskStore writes or concurrent commits through the ordered bus.

Verify that overlapping ordinary use of the published pair remains ordinary,
while a wrapper forwarding the original event and an equal Task still gets
proposal projection. The existing direct-call probe can discriminate those
paths. Together with the copied-argument case, it shows why the comparison
rules alone cannot establish the call's forwarding intent.
[Scope lookup](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L88) ·
[Summary claim](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L60)

### Verified corrections and evidence

- The original correction-7 probes now send one legacy summary through the
  equal-snapshot wrapper and one legacy summary per SDK delegate with disjoint
  config stores. Canonical 1.1 output remains correct. Version-unaware fallback,
  artifact-only, status-only, mixed, copied artifact with preserved dispatch
  Task, concurrent distinct equal ordinary event, no-op, and registry cleanup
  controls passed.
- The exact seven green handoff commands ran with `/usr/bin/timeout -k 30s
  1200s`, `mvn -B -ntp`, `-Dsurefire.failIfNoSpecifiedTests=false`,
  `-Dmaven.test.failure.ignore=false`, and the installed Byte Buddy agent.
  Each started with over 20 minutes remaining. Previous Surefire material was
  archived before the first run. Fresh XML and test output were copied after
  each run. The compiled changed classes postdate the reviewed source.
  The archived historical red log reproduced the two correction-7 findings.
  [Exact commands](../handoffs/P3-T05-correction-8.md#verification-evidence)
- The prior stream, direct-response, opening-input, unary, and creation-race
  implementations are unchanged. Fresh retained checks cover queue admission
  and lifecycle, sender, replication, and eight binding assertions. The
  independent webhook probes used test HTTP clients with 1.0 and 1.1 configs.
  Full reactor, all-binding multi-event coverage, and subsequent tasks were
  outside this review.

| Log | Finished UTC | Exit | Executed | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `focused.log` | 15:25:36 | 0 | 6 | 0 | 0 | 0 |
| `sender.log` | 15:25:39 | 0 | 41 | 0 | 0 | 0 |
| `plan.log` | 15:25:44 | 0 | 44 | 0 | 0 | 0 |
| `server.log` | 15:26:17 | 0 | 210 | 0 | 0 | 0 |
| `replication.log` | 15:26:21 | 0 | 1 | 0 | 0 | 0 |
| `bindings.log` | 15:26:26 | 0 | 8 | 0 | 0 | 0 |
| `otel.log` | 15:26:29 | 0 | 9 | 0 | 0 | 0 |

The four retained regression selectors executed **228** cases. They overlap
the required, full sender and focused selectors. Evidence is under
`/private/tmp/a2a-p3-t05-rereview8.hcd103zn/`. The probe classpath was taken
from fresh sender Surefire XML. The equal-snapshot replay initially had a
hard-coded latch expecting the former duplicate and exited 1 on that wait.
Its original log is retained as `ProposalCopiedSnapshotProbe-attempt1.log`.
The corrected control waited for the proper count and exited 0. The final
probe commands and counts are:

```sh
p=/private/tmp/a2a-p3-t05-rereview8.hcd103zn
cp_value=$(cat "$p/classpath")
agent=/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$p" "$p"/Proposal*.java "$p/DispatchRegistryScopeProbe.java" "$p/ConcurrentProposalScopeProbe.java" "$p/ExactOrdinaryProposalScopeProbe.java"
run_probe() {
  /usr/bin/timeout -k 30s 90s java -Dprobe.webhook.count="$2" \
    -Dlogback.configurationFile="$p/quiet-logback.xml" -javaagent:"$agent" \
    -cp "$p:$cp_value" "org.a2aproject.sdk.server.tasks.$1"
}
run_probe ProposalWebhookProbe 8
run_probe ProposalOffloadedWebhookProbe 7
run_probe ProposalStatusOnlyHookProbe 6
run_probe ProposalCopyingWebhookProbe 7
run_probe ProposalStatusOnlyHookMixedProbe 7
run_probe ProposalCopyingWebhookMixedProbe 8
run_probe ProposalStatusOnlyHookArtifactProbe 6
run_probe ProposalCopyingArtifactWebhookProbe 8
run_probe DispatchRegistryScopeProbe 0
run_probe ConcurrentProposalScopeProbe 0
run_probe ProposalCopiedSnapshotProbe 8
run_probe ProposalMultiDelegateProbe 0
run_probe ProposalCopiedEventAndSnapshotProbe 9
run_probe ExactOrdinaryProposalScopeProbe 0
```

The final probe runs exited 0. Their observed event sequences establish the
two findings. Logs are named for the probe class and count. `git diff
--check` passed. Only this review append and the T05 coordination row were
edited. Candidate sources and tests, prior reviews, and untracked
`test.log` were preserved.

## Correction 9 independent re-review — 2026-11-20 16:45 UTC

**Disposition — Accepted within P3-T05 scope.** Both recorded correction-8
findings are closed by independent replay. The fixed review boundary for this
pass was those two findings, their established sender controls, and the
required and retained regression selectors. No findings remain within that
boundary.

### Assignment and candidate

This separate independent review started at **16:44:29 UTC**, with a
deadline of **18:44:29 UTC**. Requested settings were Sol, medium reasoning,
normal speed. Serving metadata was unavailable. HEAD was
`81dac751c1ab1ce405fc579c2a4873559ad38813` on `support/a2a-1.1.0`. No
descendant commits appeared in local refs and the index was empty. Previous
dispositions remain historical evidence. Earlier hold language in the plan
and phase summary predates this assignment.
[Correction 9 handoff](../handoffs/P3-T05-correction-9.md)

| Candidate evidence | SHA-256 |
| --- | --- |
| Tracked diff from HEAD at start | `d82765adea0ba14b8bc33e23230959b237ff6e1478ca10205e2067f3b034dd80` |
| Tracked server-common and OpenTelemetry diff | `e9503a5f332a8f06afb274213f039a0a2b7b27406af34d784e3995daa4ac9bc0` |
| `PushNotificationSender.java` | `652ff75e672d09268b1fe61c8b6bf7f55af4081eed1eba128c99b8fc89b0e694` |
| `BasePushNotificationSender.java` | `d84ebcd2a8a88c453ccef1d9ecf4671f8b701572ef991f3b1ec24e98ca15c208` |
| Untracked `TaskProposalDispatchContext.java` | `b21956c197330658fc77d4772217524f3630954c3f69297bebb90c0073aa1be9` |
| `PushNotificationSenderTest.java` | `6fa0caa5f0d55f7e5ecd8f19803aacd879fa0738319d68d41a78ad1a8b2a8715` |
| Untracked `ExecutorProposalOutputTest.java` | `f3c2bcebd0b740dffbff992ea960def013cdbe16fd838545b3641bfcc6a785e7` |
| Untracked `FullTaskProposalTest.java` | `f7125ebaffe37704a63175270b68be1d62cfa28922e66c7d5725507a7f55f648` |
| Untracked `ExecutorMigrationExampleTest.java` | `10a5d033d8402bfe5d6427c69bc25d20231970a0034db86436b5e5c0e45ccb04` |
| Correction 9 handoff | `43af2888a86270b6298265dae64db0e6a4e502a12fa60fc100974ad0983bf34c` |
| Review before this append | `508e3dc61bba2ed21aabdde683c9a790c19a1bc0b82bac3bb5c16b253a83bde7` |

### Disposition of the recorded findings

- **P1 closed.** The joined-worker forwarding replay rebuilt both the
  artifact event and the Task as equal copies. With deferred admission,
  1.0 received the surrounding statuses, one active no-op summary and one
  proposal summary containing the artifact. It received no covered artifact
  delta. The 1.1 destination received the canonical proposal status then
  artifact. The new lookup recognizes an equal Task copy while an artifact
  forwarding scope is active.
  [Copy lookup](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L104) ·
  [Deferred admission test](../../../../../server-common/src/test/java/org/a2aproject/sdk/server/tasks/PushNotificationSenderTest.java#L655)
- **P2 closed.** With the proposal worker paused before delegating, an
  independent ordinary call using the original committed event and Task
  delivered the artifact to both versions. Releasing the worker produced
  the proposal's one legacy Task and canonical 1.1 artifact. The ordinary
  call after scope exit again reached both versions, and the focused test
  also covers a distinct equal ordinary event during forwarding.
  [Original-event lookup](../../../../../server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskProposalDispatchContext.java#L88) ·
  [Paused-worker test](../../../../../server-common/src/test/java/org/a2aproject/sdk/server/tasks/PushNotificationSenderTest.java#L518)
- The exact-event/equal-snapshot and independent two-sender controls passed.
  Each SDK delegate delivered one summary to its own legacy config and one
  artifact to its 1.1 config. The independent version-unaware control saw
  only the artifact. The seven focused cases retain status/mixed/artifact-only
  projection, no-op behavior and the previous copied-artifact and ordinary-event
  controls.

### Fresh execution evidence

All seven green handoff commands ran in the worktree with the documented
Byte Buddy 1.18.8 agent, `/usr/bin/timeout -k 30s 1200s`, `mvn -B -ntp`,
`-Dsurefire.failIfNoSpecifiedTests=false`, and
`-Dmaven.test.failure.ignore=false`. Each run started with over 20 minutes
remaining. Earlier Surefire reports were archived before execution, and
each run copied fresh XML and output using its own timestamp marker. The
build used incremental compilation. The reviewed dispatch class was compiled
after its recorded source change and the probes exercised its corrected
behavior on the Surefire classpath. The implementation's historical red log
showed both recorded failures, and the implementation handoff preserves its
intermediate assertion failure.
[Exact Maven commands](../handoffs/P3-T05-correction-9.md#verification-evidence)

| Log | Finished UTC | Exit | Executed | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `focused.log` | 16:44:43 | 0 | 7 | 0 | 0 | 0 |
| `sender.log` | 16:44:46 | 0 | 41 | 0 | 0 | 0 |
| `plan.log` | 16:44:50 | 0 | 44 | 0 | 0 | 0 |
| `server.log` | 16:45:24 | 0 | 210 | 0 | 0 | 0 |
| `replication.log` | 16:45:26 | 0 | 1 | 0 | 0 | 0 |
| `bindings.log` | 16:45:31 | 0 | 8 | 0 | 0 | 0 |
| `otel.log` | 16:45:34 | 0 | 9 | 0 | 0 | 0 |

The retained selectors executed **228** cases. The focused, sender, required
and retained selectors overlap. The full runner, reports, probe source and
logs are in `/private/tmp/a2a-p3-t05-rereview9.gcx5947q/`. The probes took
the classpath from the fresh focused Surefire XML. The copied-pair replay
updated its latch to the fixed output count. All four probes exited 0.

```sh
p=/private/tmp/a2a-p3-t05-rereview9.gcx5947q
cp_value=$(cat "$p/classpath")
agent=/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar
/usr/bin/timeout -k 30s 90s javac -cp "$cp_value" -d "$p" "$p"/Proposal*.java "$p/ExactOrdinaryProposalScopeProbe.java"
run_probe() {
  /usr/bin/timeout -k 30s 90s java -Dprobe.webhook.count="$2" \
    -Dlogback.configurationFile="$p/quiet-logback.xml" -javaagent:"$agent" \
    -cp "$p:$cp_value" "org.a2aproject.sdk.server.tasks.$1"
}
run_probe ProposalCopiedEventAndSnapshotProbe 8
run_probe ExactOrdinaryProposalScopeProbe 0
run_probe ProposalCopiedSnapshotProbe 8
run_probe ProposalMultiDelegateProbe 0
```

The independent webhook checks use the shared in-memory sender and test HTTP
clients with 1.0 and 1.1 configs. The retained binding selector covers the
eight recorded JSON-RPC, gRPC and 0.3 cases. Full-reactor, later orchestration,
enrollment and cluster gates remain in their assigned scopes. `git diff
--check` passed. Only this append and the T05 coordination row were edited.
Candidate sources, tests, prior reviews and untracked `test.log` were
preserved.
