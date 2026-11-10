# Acceptance criteria and bounded implementation runs

Status: acceptance proposal updated for production client and server support;
prior independent review covered the server architecture, not this client revision.
These criteria govern the later implementation,
not the completion of this architecture-only task. Read with
[architecture](architecture.md) and the [implementation prompt](implementation-loop.md).

## Definition of done

An implementation is done only when every criterion below has direct evidence
for its full scope. Track each as `not started`, `in progress`, `passed`, or
`blocked`, with exact test names, commands, result locations, and remaining gaps.
These are progress-record labels, not instructions to override goal-tool status
rules. A green build or exhausted run budget cannot replace this checklist.

| ID | Acceptance criterion | Required evidence |
| --- | --- | --- |
| A01 | One instance serves exact provisioned 0.3, 1.0, and 1.1 contracts. | For each binding, exercise all seven nonempty version subsets. Explicit updated SDK 1.0 and 1.1 clients plus existing 0.3 clients reach their respective contracts on the same instance. Each enabled version works, each disabled/unsupported version rejects, and none silently selects another contract. Include all-three on one application/port where the binding permits it. |
| A02 | Version exposure and discovery agree. | Tests cover effective tenant, binding, endpoint, explicit card entries, HTTP header/query precedence, historical absent/blank defaults, and gRPC metadata/defaults. 1.x-only packaging has no transitive 0.3 runtime dependency. Missing optional modules fail configuration clearly. Client default is 1.1, explicit selection is immutable, card selection filters version before binding preference, and all HTTP/gRPC requests emit that version. No negotiation/fallback. |
| A03 | Old clients retain their established contracts. | Unchanged 0.3 and 1.0 peer binaries or historical wire fixtures over all bindings verify send/get/list/subscribe/cancel and relevant errors, history, blocking, stream finality, and webhook formats. New-to-new serialization alone is insufficient. Updated explicit 1.0 clients also retain their selected semantics. Record the intentional implicit-default change to 1.1 and the explicit 1.0 migration separately from regressions. |
| A04 | Common Java APIs remain usable. | Representative legacy source and binaries exercise old constructors/builders and handler methods; a NullAway-checked caller retains non-null `message()`. API report covers client construction, additive input/query/observation/live APIs, new record/components and sealed event kinds, plus custom transport/provider/wrapper source and binary compatibility. No extra ClientEvent subtype is assumed; assess UpdateEvent extension. Any unavoidable client SPI break requires an explicit decision. Necessary store/executor migrations have executable examples. Any unavoidable common-API break has an explicit approved decision. |
| A05 | Task mutations have one atomic, authoritative generation sequence. | Concurrent expected-generation writers accept exactly one; failures cause no partial mutation. Tests cover bootstrap, same-state status updates, every artifact chunk, message-plus-artifact batch ordering, cancellation, terminal rejection, and mutations originating through all three interfaces. Stored and emitted stamps agree. All-binding public 1.1 SDK tests send guards/artifact inputs and read generation/timeline; the client preserves stamps, maps typed mismatches and never automatically rebases/retries. |
| A06 | Persistence and publication remain correct under concurrency/failure. | No uncommitted event is visible. Delay writer N after commit while N+1 commits; observers never receive reversed generations. Replication duplicates and synthetic finalization never generate a second mutation. Commit-to-publication crash tests prove the documented gap/reconciliation policy; test durable recovery only if that optional profile is promised. |
| A07 | Old-version projections consistently protect their contracts. | Mixed subscribers on one authorized task plus get/list/send snapshots and webhooks verify history limits, omission of new fields/events, hidden user-origin artifacts, collision policy, ordering, cancellation, and finite demand even when all intermediate events are filtered. Canonical stored data is unchanged by projection. Public explicit 1.0 and 1.1 SDK observations share that task on each binding; the 1.1 reducer exposes message updates/timeline/origin while 1.0 retains its view. |
| A08 | Existing data migrates safely. | Load pre-upgrade tasks/configurations, retain legacy history/artifacts and delivery metadata, persist the chosen baseline generation once, and keep it stable after restart. Do not fabricate historical timeline/chunk ordering. Missing webhook version uses fixed 1.0 fallback. |
| A09 | Subscription and long-poll observation have no handover races. | Deterministic tests cover retained/unavailable cursors, snapshot/live watermark, retention expiry, target-spec terminal subscriptions, generation below/equal/above current state, timeout, disconnect, and cleanup. No gaps caused by registration races, duplicate mutations, or lost wakeups. Public client APIs carry cursor/current-generation and expose cancellation. Replay without an initial Task is explicitly partial until a baseline arrives; replacement snapshots and unknown-payload skipping do not duplicate or fabricate state. |
| A10 | Client artifact inputs follow version, capability, and ownership rules. | Tests cover message-required creation, authorized existing-task artifact-only input, tenant/task/context mismatch, client-supplied generation ignored, origin/ID collision, chunking, and rejection without partial mutation. Test each exposed binding through public production SDK input APIs as well as raw malformed cases. Updated 1.0 clients reject unsupported inputs locally rather than drop fields or switch version. |
| A11 | Live interaction works over every provisioned 1.1 binding. | gRPC duplex and HTTP separate-input/downlink traces prove one task binding, no duplicate event acknowledgement, no second executor per input, bounded readiness/acceptance, malformed/wrong-task/late-subscribe rejection, and half-close/disconnect/terminal cleanup. A local rejection changes no task state/generation and reaches no other subscriber/webhook. 0.3/1.0 cannot activate the operation. Public 1.1 live handles provide readiness, input results, downlink, flow control, half-close and cancellation; local rejection leaves the client session usable and no automatic resend follows indeterminate acceptance. |
| A12 | Webhook delivery version is stable per configuration. | All three versions on one task select their own payload policy, honor explicit/default versions, survive persistence/restart, reject invalid/conflicting versions, and suppress unsupported events before formatting. Public client config create/get/list round-trips delivery version under the selected request contract. |
| A13 | Supported storage/cluster topologies uphold the same invariants. | Execute in-memory and JPA tests plus real multi-instance tests for the documented 1.1 authority path. Prove competing writers and failover behavior. Unsupported authority configurations fail before advertising 1.1; legacy-only deployments remain supported. Do not imply additional cluster topologies were verified. |
| A14 | The result is buildable, documented, and scoped honestly. | Relevant tests actually execute; `mvn clean install` passes with Java 17 compatibility. Docker-dependent checks required by A13 are not silently skipped. Development docs explain provisioning and migration. Pinned spec decisions are recorded. Run available target-revision conformance tests; if no official target TCK exists, direct protocol tests cover these criteria and the report explicitly avoids certification claims. RC-only verification never becomes a final-1.1 conformance claim. |

The following scenarios are required parts of the existing criteria, not optional
extensions to the checklist:

- **A02 — new versus existing configuration:** new registration requires an
  explicit version set; examples select 1.1 explicitly. Missing-version HTTP
  and gRPC both select 0.3, rejected if the selected interface cannot provide it.
  Test absent, empty, and whitespace-only metadata separately; the protobuf
  service name does not supply a 1.0 default.
  Existing entry-point exposure/defaults are covered by independent regressions.
  A primary 1.1 model must not implicitly expose or select a 1.1 interface.
- **A03/A05/A08/A10 — no-task direct Message:** over unary and ordinary streaming
  sends for every binding, send a request with no existing task and have the
  executor emit only a direct Message. For 1.1 also supply artifact updates.
  Assert the response is the Message, no TaskStore write or task-bound push
  config occurs, no task events/generations are published/replicated, pending
  input is discarded, and queue cleanup completes. Include `returnImmediately`.
  Contrast with a task-creating response, and with an existing-task input that
  remains committed even if the executor replies with Message.
- **A04/A05/A06/A07 — full executor Task:** cover initial Task emission with
  pending input, supplied/fabricated generations, canonical history/timeline
  preservation, and multiple status/artifact changes normalized into correctly
  stamped events. Pause an executor after its snapshot/revision was observed,
  accept concurrent client input, then emit the stale full Task. Assert conflict,
  no overwrite/deletion, no generation advance or task failure, and a working
  refresh/delta migration path. Exercise `addTask`, `emitEvent(Task)`, and raw
  queue provenance handling alongside normal status/artifact events. Replicated
  committed Task envelopes preserve their assigned generations instead of
  becoming fresh local proposals.
  Also advance the revision with the same executor's own acknowledged delta
  after it constructed a full-state proposal: the proposal must retain its old
  immutable base and conflict. A full Task with completed status and final
  artifacts commits and delivers those artifacts before final stream closure.
  For metadata-only full Task updates starting from a message-bearing status,
  assert one metadata/status mutation and new status timeline observation while
  retaining the message ID, unchanged legacy message occurrence count, and no
  newly authored Message/message-update event.

- **A04/A05/A06/A11 — executor result delivery:** prove that proposal submission
  distinguishes queue admission from committed acceptance. Delay processing,
  force conflict after enqueue, and assert the originating completion stage or
  execution-local legacy sink receives a correlated rejection. Task state and
  other observers remain usable; refresh/rebuild succeeds. Missing completion
  provenance rejects raw legacy admission synchronously. Test known non-commit
  versus indeterminate storage outcomes and callback execution without deadlock.
- **A03/A10/A12 — webhook enrollment:** create an immediately terminal Task with
  inline config, including through JPA finalization; config/version must be
  available before first notification eligibility. Config-store failure occurs
  before task commitment. With independent stores, test config-first visibility,
  definitive task-store failure and orphan cleanup/expiry, and indeterminate
  commit reconciliation without deleting potentially needed enrollment. Direct
  Message leaves no config; existing-task enrollment failure rejects its input
  before mutation and leaves other configs intact.
- **A06/A09 — observation liveness:** lose terminal publication and perform no
  subsequent mutation. Advance injected time and prove bounded authority
  reconciliation yields the final snapshot before closure, or the binding's
  specified retryable resynchronization outcome. Cover permanently missing
  intermediate generations, gap timeout, buffer overflow, late stale events,
  read failure and slow consumers. Recovery creates no task mutation or webhook.
- **A08/A13 — deployment transition:** fence old database/broker writers before
  migration and demonstrate an old identity cannot rewrite migrated data.
  Reject delayed old replication and stale execution epochs; a migrated record
  missing generation fails rather than resetting to 1. Test drain/upgrade and
  snapshot-based rollback restrictions. Document the absence of mixed-binary
  rolling-write support separately from support for all three client protocols.
- **A11/A13 — remote live input:** route ingress on one node to the leased
  execution owner on another, acknowledge commit separately from queue admission,
  and prove an obsolete owner cannot commit input/output after failover.

- **A02/A03/A04 — client construction and extensibility:** default builder/config,
  convenience and direct transport constructors select 1.1 unless explicitly
  pinned. A descriptor explicitly selecting 1.0 stays 1.0. Card order, duplicate
  bindings, refreshed cards, concurrent mixed-version clients and changed shared
  constants cannot upgrade an existing instance. Conflicting interceptor/context
  headers or metadata reject before network I/O; same-version duplicates normalize.
  A legacy custom provider/wrapper remains usable for explicit 1.0 and clearly
  rejects unsupported 1.1 features. Inventory defaults, public signatures and
  wrappers; document additive extension points without introducing a new stack.
- **A04/A07/A09 — usable client events:** via public SDK over each binding,
  known message updates, generations/timeline and artifacts reach callers.
  Independently inject unknown JSON/protobuf stream variants and timeline kinds
  between known events; 1.1 skips them, remains usable and cancellable, and does
  not fabricate cursor/state completeness. Malformed known payloads and error
  envelopes still fail correctly. Test partial cursor bootstrap, authoritative
  replacement snapshots, duplicate history/chunks, slow-consumer bounds and one
  terminal callback across disconnect/error/cancel races. Preserve selected 1.0
  decoding semantics with independent fixtures.
- **A01/A03/A07/A14 — three evidence layers:** updated public SDK end-to-end
  scenarios demonstrate usability; unchanged historical 1.0/0.3 peers or frozen
  fixtures demonstrate old contracts; raw protocol tests cover malformed/unknown
  payloads and binding details independently. Updated-to-updated success alone
  cannot pass backward compatibility. Do not silently rebuild historical peers
  from current sources or regenerate their expected payloads with new codecs.


- **A02/A04 — concrete capability chain:** validate provider and actual transport
  versions and intersect features through every wrapper. Test a plain legacy
  wrapper removing advanced capabilities, inapplicable identity wrapper, invalid
  capability escalation and a throwing wrapper without bypass on the new 1.1
  path; retain the explicit-1.0 historical wrapper-error baseline. Exercise both
  OpenTelemetry wrapper families. Compile/load an unchanged legacy-constant
  provider for explicit 1.0 and inspect its emitted version; separately test a
  provider delegating to changed implicit constructors and its adapter/rejection.
  Defaults alone cannot pass the API/SPI compatibility report.
- **A04/A07/A09 — synchronization versus history:** test NO_BASELINE replay,
  synchronized as-of projection with historyLength/timelineLength zero/finite,
  unknown ranges, legacy migration gaps and unknown skipped events. Replacement
  snapshots replace returned windows without claiming full history or silently
  merging stale cache. Uncoordinated GET/stream cannot mark synchronization.
  Legacy callback event types/completion behavior remain unchanged.
- **A09/A11 — handle lifecycle and evidence:** readiness is separate from local
  queue admission, authoritative commit and executor delivery. Test exact-once
  input outcomes for cancel/deadline before dispatch, after possible commit and
  after definitive results, half-close drain, opening input cancelled pre-ready,
  observation cancel/failure/clean-end with unresolved input, and late replies.
  No task cancellation or unsafe retry occurs. Demonstrate actual positive
  correlation for each supported binding/input shape; unresolved receipts block
  that dependent work. GetTask/reconnect refreshes state but is not a general
  exact-operation lookup. No executor-delivery receipt or status RPC is assumed.


The [original validation matrix](validation.md) provides detailed scenarios for
these criteria. A passing checklist entry must link those scenarios to evidence;
merely copying the expected result into the progress record is not evidence.

## Run limits

The user agreed to the ten-iteration, two-hour, three-attempt limits on
2026-11-09. They remain adjustable explicitly before a later implementation run:

- Maximum **10 focused iterations per run**.
- Maximum **2 hours of elapsed work per run**. Start no new iteration once the
  limit is reached; finish/report the current operation at a safe boundary.
- Proposed command safeguard: local build/test commands have a finite deadline
  of at most 20 minutes and no more than the remaining run budget, with at most
  30 seconds for controlled termination/cleanup. Record process handles and
  timeout evidence; a timeout is not a passing check. This makes a hung local
  command bounded as well as bounding iteration scheduling. External CI jobs
  may continue under their own deadlines; report/reuse their handles.
- Maximum **3 implementation attempts at the same failing criterion** before
  stopping dependent work and reporting the obstacle. Preserve attempt counts
  across checkpoints; renaming the criterion or opening another run does not
  erase a repeated failure. A user decision can authorize a new approach/budget.
- Additional proposed safeguard: stop early after **2 consecutive iterations
  with no meaningful progress**.
  Progress means demonstrating criterion advancement, verifying a necessary
  change, or obtaining evidence that materially narrows the next action.
  A speculative edit alone does not reset this counter. Restating plans or
  rerunning an unchanged failing command is not progress.

An iteration has one named criterion or narrowly related set, one hypothesis or
implementation objective, a bounded edit/investigation, verification, and a
recorded result. The seven phases (0 through 6) are dependency groups, not seven
unbounded iterations. Short status messages and polling a confirmed live build
do not consume another implementation attempt, but elapsed time still counts.

An implementation attempt is one materially distinct fix followed by
verification against the failing criterion. Count every fix/verify cycle, even
when several occur inside one iteration; three cycles cannot be labeled one
attempt. After the third attempt, continue only if the criterion passed;
otherwise stop dependent work and report. Investigations without a fix still
consume iteration/time budget and require recorded findings.

At a limit, stop scheduling new work and report a checkpoint. Do not declare
success, silently increase the budget, weaken acceptance, or automatically start
another run. Preserve work and record any live process/session handles; do not
launch a duplicate process because a wait timed out. Follow the environment's
goal-status rules: a run limit is not itself authorization to mark a goal
complete, paused, or blocked.

Persist the run start timestamp, budget, iteration/attempt counters, and exhausted
status. A resumed context or automatic continuation reads these same counters;
it does not grant a fresh run. Obtain an explicit user budget decision after
exhaustion rather than looping through successive nominal runs.

## Checkpoint report

Every bounded run ends with:

1. Iterations/attempts and elapsed time used; reason for stopping.
2. Acceptance IDs passed, with evidence; IDs still incomplete or blocked.
3. Changed files and tests/build results, including failures and skips.
4. A concrete obstacle or unresolved decision, if any, and what evidence rules
   out the approaches already tried.
5. The next safe action and the budget/decision needed for another run.
6. For a completed phase, the proposed phase commit scope/message and its user
   approval status. Obtain explicit user approval before each phase commit;
   architecture/phase-execution approval alone does not authorize a commit.
   Follow [phase commit instructions](implementation-loop.md#phase-commits-and-user-approval)
   and record the approved commit SHA before starting the next authorized phase.

Completion and stopping are separate outcomes. A finite run can make useful
progress and stop with the implementation correctly reported as incomplete.
