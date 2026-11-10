# Prompt for the later implementation loop

Status: handoff accompanying the reviewed [architecture](architecture.md). This is a concrete
execution prompt and phase contract, not an already executed implementation plan.
Do not start production changes merely because this file exists.

Copy the following prompt into the session that will implement the design after
architecture approval, including review of the client scope revision. Its scope
is production client and server support. The loop must derive detailed, current-code plans
at each phase rather than relying on guessed future method signatures.

---

Implement A2A 1.1 production client and server support in a2a-java while preserving
distinct 0.3 and 1.0 server contracts in the same instance and explicitly selected
client versions, following the approved architecture. Client and server features
must arrive together through public APIs and end-to-end tests.

## Workspace and authoritative inputs

Use the existing worktree
`/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`.
Do not change the main checkout or create a replacement branch without cause.
First inspect current git status, branch, AGENTS.md, and the current files below;
preserve unrelated work. The initial base was upstream/main at
`b96e0b3f21927eb551881232c69acf077ebdcbc4`; do not silently reset to it.

Read in order:

1. `.claude/architecture/a2a-1.1/README.md`
2. `.claude/architecture/a2a-1.1/architecture.md`
3. `.claude/architecture/a2a-1.1/spec-delta.md`
4. `.claude/architecture/a2a-1.1/compatibility.md`
5. `.claude/architecture/a2a-1.1/validation.md`
6. `.claude/architecture/a2a-1.1/acceptance.md`
7. `.claude/architecture/EVENTQUEUE.md` and the linked lifecycle/request-flow
   documents when changing task/event handling.
8. `.claude/architecture/compatibility_0.3.md` when changing conversion.

The approved architecture governs choices; older research records evidence and
does not supersede it. Verify whether review actually approved the architecture.
If approval is missing, obtain it before implementing. Respect current session
authorization and applicable skills; do not add redundant approval requests.

## Required final behavior

- A single application can provision all three server interfaces for JSON-RPC,
  REST, and gRPC. Explicit 1.0 goes to the 1.0 contract; explicit 1.1 to the 1.1
  contract; 0.3 to its existing contract. Reject unprovisioned/unsupported
  versions. No silent upgrade or fallback between requested versions.
- 0.3 remains an optional module dependency. Shared 1.x artifacts work without
  it. 1.0 policy is built into the shared 1.x implementation and its exposure is
  configurable. Advertising and reachable behavior match per tenant/endpoint.
- Preserve common Java constructors/builders, method signatures, non-null
  accessors, and existing handler entry points. Keep
  `MessageSendParams.message()` non-null; use a companion input shape for
  artifact-only requests. Document necessary custom store/executor migrations.
  Report record/sealed-interface compatibility impacts and obtain an explicit
  decision before implementing an unavoidable common-API break. Do not
  duplicate the entire 1.0 stack to avoid focused migration work.
- Preserve old history, errors, blocking/cancellation behavior, missing-version
  defaults, and webhook formats. Keep deprecated history alongside timeline.
- Shared tasks have one authoritative mutation sequence across all input
  versions, with atomic generation checks, correct stamps, and persistence
  before visibility. Per-task publication preserves order across writers.
  Replicated copies retain sequence identity.
- Provide 1.1 generation/timeline, long-poll, subscription cursor/fallback,
  message-update events, artifact inputs, per-config delivery versions, and live
  interaction as required by the pinned target. Do not replace the full goal
  with adding fields, routing, or passing old tests.
- Version projection covers get/list/send results, snapshots, streams, and
  webhooks. Hide user-origin artifacts from older views; filter new events with
  correct demand/cancellation. Preserve authorization throughout.
- Exchange-local live rejection never fails the task, mutates history,
  increments generation, replicates, or reaches another subscriber/webhook.
- Ordinary no-task direct Message responses persist neither pending input
  messages nor artifact updates. Task IDs/queues created for execution are not
  task materialization. Existing-task accepted inputs remain committed even if
  their executor later returns Message.
- Treat full executor Tasks as observed-revision proposals, not blind store
  replacements. Preserve canonical interaction data, normalize accepted changes,
  reject stale proposals without task failure, and distinguish committed
  replication from new executor writes.
- Extend existing client/base, transport SPI, JSON-RPC, REST and gRPC modules with
  public usable 1.1 operations. A client instance defaults to 1.1; explicit 1.0
  remains 1.0. Freeze the version at construction and apply it to selection,
  headers/metadata, validation, decoding/errors and state tracking. Document the
  intentional default change and explicit 1.0 migration; do not infer a new
  version from card order, refreshed discovery or a current-version constant.
- No automatic discovery-driven version negotiation/fallback. A requested version
  unavailable in the card fails selection; stale-card/raw cases separately prove
  server-side disabled-version rejection. Keep old 0.3 clients and independent
  historical peer/fixture evidence. Keep the 0.3 client separate in its existing
  optional modules/API/transports; the shared 1.x client selects only 1.0 or 1.1.
- Use cohesive policy, codec, reducer and live-lifecycle boundaries in existing
  modules. No new production module, duplicated client stack or compat-1.0/ is
  planned. Report any concrete module need before adding one. Preserve common
  API signatures; additive transport capabilities need provider/wrapper migration
  examples and an explicit decision for unavoidable SPI breaks. Unsupported
  legacy providers must fail clearly rather than drop 1.1 parameters.

## Loop mechanics and progress record

Use the fourteen acceptance IDs and run limits in `acceptance.md`. Unless the
user changes the budget, cap this run at ten focused iterations or two hours,
whichever comes first. Stop dependent work after three attempts at the same
failing criterion. Include the proposed safeguard of stopping after two
consecutive iterations without meaningful progress in the architecture review;
apply it if approved. Each iteration names its acceptance target and records
verification/new evidence. The phases below are dependency groups, not permission
for an unlimited number of iterations. A budget stop means checkpoint and report
incomplete work; it never means success or an automatic fresh run. Follow the
environment's goal-status rules when recording the stop.

Maintain `.claude/architecture/a2a-1.1/implementation-progress.md` with target
spec SHA, architecture approval reference, phase state, relevant files,
decisions, exact commands/results, current blockers, and next action. Do not
mark a phase verified based on a plan, a comment, or a skipped test.
Record the run start timestamp, authorized budget, completed iteration count,
per-criterion attempt counts, and any exhausted-budget flag before beginning
work. Check these on every continuation. Automatic continuation is not a fresh
budget authorization; after exhaustion require an explicit user budget decision.
Count every materially distinct fix/verify cycle as an attempt, including
multiple cycles inside one iteration. A speculative edit without demonstrated
advancement or useful narrowing evidence does not reset the no-progress counter.
Apply the proposed finite-command safeguard from `acceptance.md`: a local
build/test deadline is at most 20 minutes and the remaining run budget, plus
30 seconds of controlled cleanup. Record timeouts as incomplete verification,
not failures proved by assertion or permission to start an unlimited rerun.

For each phase: inspect current code; write a focused implementation plan with
named client and server files, interfaces, public SDK integration tests,
independent compatibility fixtures, and commands; implement the
behavior; run the relevant checks; review the diff against the architecture;
fix findings; record evidence; then prepare the phase commit for user approval
as described below. Reuse a live process handle while waiting for builds. Do
not restart because an observation timed out.

Work autonomously within the authorized scope. Ask only for a material missing
constraint or architecture change. Stop dependent work when a normative conflict
cannot be resolved; continue independent phases when possible. Never invent
conformance rules to keep the loop running. Do not spawn agents unless the user
or applicable instructions authorize it. Do not commit without following the
repository's issue-association question and commit conventions. Do not push,
create PRs, or contact upstream unless separately requested.

## Phase commits and user approval

After each completed, verified and independently reviewed phase, including
Phase 0, prepare a separate phase-scoped commit proposal. Present the proposed
file scope/diff, verification evidence, remaining gates and Conventional Commit
message for the user to review. Ask whether the commit relates to a GitHub issue;
if so, include `This fixes #{issue_number}` as required by AGENTS.md.

**Obtain explicit user approval before creating each phase commit.** Approval
of the architecture or permission to execute a phase is not commit approval.
Preserve unrelated user changes and include only the approved scope. Record the
approval and resulting commit SHA in implementation-progress.md. If approval is
pending, leave the phase changes uncommitted and hand off the proposal. Create
the approved phase commit before beginning the next phase; next-phase execution
still requires its own authorization. Do not push or create a PR unless requested.

Phase 1 must be executed by a fresh agent, as requested by the user. The Phase 0
agent must leave the reviewed checkpoint and commit proposal/approval status in
the handoff, without starting Phase 1.

## Concrete client contract to implement

Follow the recommended contracts in [architecture](architecture.md#recommended-api-and-spi-contracts),
not an open-ended choice of observation/completeness/SPI design. Public base owns
TaskObservation/TaskView/ObservationUpdate, LiveSession and InputHandle; lower SPI
owns VersionedClientTransport and raw transport observation/live/input handles;
shared request DTOs stay in spec. Internal reducer/controller state is not public
or wire state. Final names and class factoring are routine planning choices.

Provider capability declarations default to a frozen 1.0 profile; built-in 1.1
providers opt in. Check the actual selected version and capability intersection
through all wrappers. Update the two OpenTelemetry wrapper families; never unwrap
or silently skip a failing configured wrapper to access advanced operations.
Retain the explicit-1.0 historical wrapper error baseline; do not expand this
revision into unrelated legacy wrapper hardening.
Preserve the legacy public CURRENT_PROTOCOL_VERSION value at 1.0 and introduce
an independent 1.1 client construction default. Audit a provider that ignores its
explicit descriptor and calls implicit built-in constructors; such a provider may
need an adapter. Default methods do not establish source/binary/wire compatibility.

TaskView distinguishes NO_BASELINE/SYNCHRONIZED/INCOMPLETE at an as-of generation
from separate limited/legacy/unknown history and timeline coverage. A snapshot
never proves lifetime history. Preserve legacy callbacks; cursor partial views
and metadata-only invalidations use the additive observation API.

Implement the architecture's lifecycle/outcome tables, including NOT_SENT versus
REJECTED versus COMMITTED versus INDETERMINATE, finite input/ready deadlines,
half-close, observation cancellation and no task cancellation. Require real
binding evidence for positive input correlation. There is no public operation-
status RPC or executor-delivery receipt; internal proposal correlation is separate.
If the pinned binding lacks evidence (especially artifact-only/duplex inputs),
record the precise gate instead of inventing an acknowledgement or retry rule.

Only material changes to public/API compatibility, default/selection semantics,
or unresolved normative binding rules require a decision. Preserve the existing
run caps; no new approval is needed for routine names/packages/internal layout
that implement the recommended semantics.

## Client/server feature sequence

The existing seven phase numbers and run limits remain unchanged. A phase is
verified only with its matching public SDK support and integration evidence;
shared model compilation, raw requests or private transport calls alone do not
satisfy usability. Raw/frozen cases remain independent evidence. Use explicit
version profiles in tests; never migrate old suites to 1.1 merely by changing
a shared constant. Incomplete 1.1 interfaces are test/experimental registrations,
not a production conformance claim or advertisement.

| Phase | Public client work alongside server work |
| --- | --- |
| 0 | Inventory construction/defaults, SPI/wrappers and stream tracking; baseline old public usage and frozen peers; plan public signatures against resolved gates. |
| 1 | Immutable explicit instance version with 1.1 default, exact card selection and all transport headers/metadata/error policies; paired 1.0/1.1 ordinary operations on one test server. |
| 2 | Shared codecs and public additive input/query/event types; known new event dispatch and unknown-kind skipping; wire tests now, stateful behavior in Phase 3. |
| 3 | Public precondition/artifact sends and generation/timeline state reduction; all-binding end-to-end mutation/direct-Message/conflict tests. |
| 4 | Public cursor/cancellable observation, long-poll and delivery-version config operations; replay/resync and mixed-version observation tests. |
| 5 | Public live-session handle plus all binding implementations; correlated outcomes, cancellation/flow control and cross-node traces. |
| 6 | Complete client/server matrix, API/SPI migrations, independent peer audit, docs and final build/conformance coverage. |

## Phase 0: pin semantics and baseline

Resolve the architecture's six specification gates against current upstream
tagged files and official binding definitions. Prefer `gh` for GitHub reading.
Check upstream #2411's current state and pinned diff; its summary has previously
disagreed with its artifact-identity changes. An open PR is not the target spec.
If final 1.1 is unavailable, clearly record RC/experimental status and retain a
final-spec reconciliation gate.

Pin the decisions for discovery, ordinary/live cursor handling, terminal-task
subscription, generation bootstrap, artifact identity/origin, and HTTP uplink
acknowledgement/error association. If HTTP exchange correlation remains
underspecified, produce a minimal request/response trace demonstrating the
ambiguity and request a decision; do not invent a wire extension silently.

Capture a baseline build and existing behavior. Establish unchanged 0.3 and 1.0
peers using old artifacts in an isolated process or raw historical fixtures.
Record the difference between historical intended behavior and known defects;
do not change unrelated defects as incidental compatibility work.

Inspect `ClientBuilder`/`ClientConfig`, direct transport constructors/providers,
`A2A` convenience factories, interceptors/wrappers, `ClientTaskManager`, event
callbacks, SSE listeners and `EventStreamObserver`. Record the existing first-
interface-per-binding and current-version-header behavior. Inventory every
implicit construction path and specify the intentional new 1.1 default versus
explicit 1.0. Old API signatures still work; compatibility evidence must distinguish
selection-default changes from unrelated behavioral regressions.

Inspect `tests/server-common/.../AbstractA2AServerTest` and existing multiversion
Quarkus suites. Plan explicit-version client factories/cards and all-binding
paired-client scenarios; retain the old 0.3 suites. Establish frozen binary or
wire-fixture provenance and isolate old artifacts from reactor replacements.
Resolve public cursor/live correlation against the same spec gates as the server;
use the recommended projection synchronization/coverage model rather than deferring
it. Pin exact commit/rejection evidence per binding and message/artifact input;
list shapes lacking receipts as dependent gates. Do not infer a status RPC. Write an API/SPI impact inventory and proposed focused
plans, not guessed finalized signatures or an automatic negotiation mechanism.

Deliverable: pinned semantics, baseline evidence, and tests that can detect
regressions when the shared model changes.

## Phase 1: explicit interfaces and version policy

Inspect/extend `reference/common/.../VersionRouter.java`, multi-version HTTP
routes, gRPC context/handler dispatch, `ServerCallContext`,
`AgentCardRequestPolicy`, and `A2AVersionValidator`. Keep interface selection
scoped to binding/endpoint/tenant. Define shared policy for operation/parameter
semantics, error mapping, and output projection. Keep 0.3 conversion original
version context. Do not advertise incomplete 1.1 functionality.

Implement the architecture's new-registration defaults explicitly: require a
nonempty version set, use explicit 1.1-only examples, retain historical existing
entry points, resolve absent HTTP version to 0.3, and default the new shared
1.x gRPC registration to 0.3 for absent/empty/whitespace metadata (reject when
the selected interface cannot provide 0.3). Do not infer 1.0 from the service
name. Preserve only demonstrated historical exceptions in explicit legacy
adapters; primary model/handler version must never drive implicit exposure.

Implement instance client protocol selection in `ClientConfig` and `ClientBuilder`:
1.1 default, explicit 1.0/1.1, filter exact version/tenant/endpoint before binding
preferences, reject absent matches. Carry that version through providers,
HTTP GET/POST/SSE and gRPC metadata; remove runtime reliance on the current-version
constant. Preserve direct-constructor signatures with documented 1.1 defaults or
explicit descriptor selection, and add explicit-version overloads. Conflicting
context/interceptor version overrides fail before sending, including duplicates.
Use the correct version's request/response/error policy without fallback.

Pin existing public-SDK regressions explicitly to 1.0; add explicit and default
1.1 ordinary send/get/list/cancel tests using only capabilities implemented at this
phase. Run paired instances on the same server per binding and verify headers,
card-order permutations, tenant endpoints, explicit disabled-version rejection
with a stale descriptor, unsupported versions, old constructor migrations and
conservative legacy-provider behavior. Run source/binary/wire cases for frozen
CURRENT_PROTOCOL_VERSION providers, provider delegates using changed convenience
defaults, legacy wrappers that remove capabilities, and mismatched declarations.
A wrapper exception on the 1.1 capability path must fail construction, not drop
the wrapper; separately retain the explicit-1.0 historical baseline. This is an incremental test setup, not
permission to advertise a complete 1.1 interface early.

Deliverable: provisioning and exact-dispatch tests for all subsets, including
1.1-only without 0.3 artifacts, and unchanged legacy missing-version behavior.

## Phase 2: shared model and compatibility adapters

Follow `.agents/skills/update-a2a-proto/SKILL.md` when updating the proto.
Update `spec/`, `spec-grpc/`, shared Gson adapters, and direct 0.3 MapStruct
conversion. Preserve old constructor/builder usage and non-null accessor
contracts; decode artifact-only inputs through a companion shape. Introduce
cursor-specific subscription parameters without polluting other ID operations.
Cover artifact-only request validation and all new stream payload kinds.

Implement old-version projections before exposing new event kinds. Keep ordinary
unknown-field tolerance and implement required unknown-payload handling without
silently changing old contracts. Add an API migration report with concrete
before/after examples for each necessary breaking extension-point change.

Extend public client parameter overloads/companion input types and additive SPI
contracts in existing modules. Expose generation guards, task/timeline fields,
current-generation queries, cursor subscription, artifact inputs and delivery
versions without nullable legacy `message()`. Keep ClientEvent's three variants;
reuse TaskUpdateEvent for known message updates where compatible. Record sealed
UpdateEvent and record-component impacts. A default unsupported extension must
preserve old providers without falsely claiming 1.1; test wrappers/decorators too.

Implement version-aware known/ignored/malformed decode across ProtoUtils,
JSONRPCUtils, REST/protobuf JSON, SSE and gRPC observers. Test unknown stream
variants and timeline kinds surrounded by known events, malformed known payloads,
all-unknown cancellation, and exactly-once completion/error callbacks. Exercise
public 1.1 callbacks with a controlled server/fixture; do not add a test-only SDK.
Public types/codecs arrive here; stateful generation/artifact semantics follow
with their server implementation in Phase 3.

Deliverable: schema/codec round-trip evidence, old fixtures readable, old-client
wire regressions passing, and new event filtering respecting finite demand.
Include a representative NullAway-checked legacy caller and exhaustive-switch
impact assessment; successful library compilation alone does not prove
downstream source compatibility.

## Phase 3: authoritative task mutation and publication

Inspect `TaskManager`, `MainEventBusProcessor`, `TaskStore`, `InMemoryTaskStore`,
`JpaDatabaseTaskStore`/`JpaTask`, and replicated queue processing. Introduce the
atomic mutation/result contract. Cover input messages and agent events through
the same authority. Return committed stamped events for publication, webhooks,
and replication; stop distributing the original unstamped object.

Implement ordinary-send branch selection before speculative persistence: pending
new-task input stays local until task-bearing output or explicit task creation;
a direct Message without a task discards pending message/artifacts and creates
no task-bound push config. Already-existing task input commits normally.
Add binding-level unary/streaming regressions and store/replication spies for
both branches, including artifact-bearing direct responses and cleanup.

Cover `TaskManager.saveTaskEvent(Task, ...)`, `AgentEmitter.addTask`,
`emitEvent(Task)`, and raw queue events. Add internal observed-revision/provenance
envelopes without trusting supplied Task generations. Implement initial
materialization and matching-revision normalization; preserve canonical
timeline/history/user artifacts and reject stale/missing-revision replacement
without a fresh mutation or task failure. Test concurrent input versus stale
full Task, several mutations from one Task proposal, and replication dedup.
Bind each proposal to an immutable base-snapshot/revision pair, not mutable
execution state; conservatively retain invocation-start revision for legacy
Task emissions. Test staleness after the same executor's acknowledged delta.
For terminal full Tasks, normalize artifacts before terminal status and verify
final artifacts arrive before stream closure.
Test metadata-only normalization from a message-bearing current status; preserve
the message identity and existing legacy history occurrence count. Record the
new status timeline observation without inventing a newly authored Message or
message-update event.
Document refresh/delta migration examples for custom executors and preserve
legacy wire event shapes through per-subscriber projection where required.

Define result-bearing proposal submission returning a completion stage with
correlated committed/rejected/failed outcomes, including indeterminate storage
results. Admission is not commit. Register execution-local sinks for retained
void paths; reject raw proposals without a completion envelope before enqueue.
Intercept expected rejection before MainEventBusProcessor broadcasts exceptions.
Test a conflict after admission, unaffected observers, refresh/retry, and callback
dispatch without event-loop/authority deadlock.

Wire initial webhook enrollment into materialization before notification
eligibility, including JPA finalization hooks. Use combined commit where stores
support it; otherwise enroll config first with provisional invisibility until
task existence, read-after-write visibility, orphan cleanup on definitive task
failure, and reconciliation rather than deletion on indeterminate task commit.
Test immediate terminal creation and config-store failure in unary/streaming
paths. Do not add a second fallible enrollment step after task commit.

Implement transaction/precondition handling, generation/timeline/history
updates, artifact generation ranges/origin, terminal-state checks, ordered
publication, and old-task migration. Keep live rejection outside this path.
Choose and prove cross-node authority: shared transactional storage is the
starting recommendation; a fenced task owner is valid if it provides atomicity,
failover fencing, and ordered publication. Compare its impact on existing
replicated deployments before finalizing the storage plan. Fail unsupported
configuration clearly; a local lock or replicated queues do not establish
atomic multi-node writes. Preserve legacy-only deployments.

Implement the coordinated drain-and-upgrade policy before migrating shared data:
fence old database/broker identities, persist writer epoch/per-task migration
markers outside Task JSON, reject delayed legacy/stale-owner writes, and fail
corrupt migrated rows rather than resetting generation. Test attempted writes
from an old identity and document snapshot-based rollback restrictions; do not
claim mixed-binary rolling upgrade support.

Complete public message/artifact-input sends with generation guards in all three
client transports as the mutation authority becomes available. Extend the shared
client reducer for generation/timeline, message updates, artifact origin/chunks
and version-specific history. Preserve authoritative stamps and distinguish
partial cursor views from complete snapshots. No speculative generation changes
or automatic retry/rebase on mismatch, timeout or indeterminate acceptance.

Add public SDK end-to-end tests per binding for competing guarded sends, typed
mismatch errors, message-plus-artifact and artifact-only inputs, canonical Task
results, direct Message without persistence, and a 1.0 writer observed by 1.1.
Include client tests for stale/duplicate updates and message occurrence counts.
Keep server-side store/replication fault injection and frozen compatibility cases;
they complement the public-client assertions rather than being replaced.

Deliverable: exactly one accepted concurrent expected-generation writer; no
partial rejected batch; monotonic stamps for mutations from every version;
no visibility before commit; ordered publication when one writer pauses after
commit; duplicate-safe replication; finalization without fresh duplicate
generations; restart-safe stored generations. Document the baseline
commit-to-publish crash window and prove gap/reconciliation behavior. Durable
outbox recovery is optional and needs its own evidence if selected; do not
silently promise lossless crash delivery.

## Phase 4: observation and per-version delivery

Implement coordinated snapshot/replay/live handover and long-poll wait/recheck
using the pinned semantics. Bound retention and input/waiter resources. Add
deterministic expiry, timeout, disconnect, and terminal cleanup tests. Timeline
alone must not be treated as an artifact-chunk replay log.

Implement periodic authority reconciliation for active observations, bounded
reorder gap time/buffer size, and snapshot-or-retryable-close resynchronization
from the architecture. Test committed terminal publication lost with no later
mutation, unavailable authority, overflow, late events and slow subscribers.
Recovery snapshots retain stored generations and do not create bus/webhook
mutations. Existing long-polls must also wake or time out within finite bounds.

Extend `PushNotificationConfigStore`, persistence, and
`BasePushNotificationSender`/formatters with per-config canonical delivery
version and explicit legacy fallback. Test mixed-version subscribers and
webhook configs on the same task, including user-artifact projection.

Implement public cursor-specific subscribe overloads/observation handles,
long-poll parameters and cancellation, and config delivery-version fields across
the client SPI and all bindings. Old subscribe callbacks remain callable. For
no-initial-Task replay, expose known raw updates, TaskView as-of synchronization
and independent history/timeline coverage via the additive observation API.
Test zero/finite limits, migrated gaps, unknown-entry invalidation and replacement
windows; a baseline never proves lifetime history. Test ready/cancel/deadline races,
exactly-once outcomes and bounded resync without unsafe GET/live handover.
Support authoritative replacement snapshots without duplicate history/chunks.

Use explicit 1.0 and 1.1 public clients to observe the same task on the same server
per binding, alongside retained 0.3 cases. Cover retained/expired cursor, terminal
reconnect, long-poll below/equal/above generation, timeout/disconnect, lost terminal
publication with no later mutation, repeated resync snapshots, bounded slow
consumers and no leaked handles. Config create/get/list must round-trip selected
versions without silently overriding legacy fallback. Keep raw binding/error
fixtures as independent evidence.

Deliverable: no handover gaps or duplicated mutations; correct cursor fallback;
no lost long-poll wakeups; stable webhook formats after load/restart.

## Phase 5: live execution and all bindings

Implement the explicit task-bound executor input/session contract, readiness,
bounded reservation/acceptance, and recoverable committed-input delivery or a
documented recovery contract. Do not start another executor for every uplink.
Implement gRPC duplex and HTTP uplink/downlink behavior from the pinned binding.
Cover malformed, wrong-task, late-subscribe, unauthorized, generation-mismatched,
and unavailable-input cases while preserving the exchange and other observers.

Pin and implement the internal command path from remote ingress to the leased
execution owner. Check owner epochs on input/output commit, fence obsolete
owners before failover, and document executor recovery/side-effect limits. Test
cross-node input delivery and stale-owner rejection before enabling that topology.

Implement the recommended public LiveSession/InputHandle and lower SPI contracts:
readiness separate from opening input outcome, actual correlated commit evidence,
NOT_SENT/REJECTED/COMMITTED/INDETERMINATE, separate downlink, bounded pending/receive
resources, finite result deadlines, drain/half-close and explicit close. Add tests
for cancel before dispatch versus after possible commit, late acknowledgements,
clean stream end with unresolved input, and no executor-delivery assertion. gRPC is duplex; HTTP
follows the resolved binding association without invented wire fields. Serialize
or reject ambiguous outstanding inputs where required; local correlation tokens
alone do not establish on-wire association. Keep request-local rejection out of
ClientTaskManager task failure and the ordinary stream terminal-error callback.

Exercise all bindings using public 1.1 clients, including artifact/precondition
uplinks, rejected input followed by successful input, other unaffected 1.0/1.1
observers, remote ingress/owner failover, indeterminate completion without blind
retry, and cleanup/ownership of HTTP streams/shared gRPC channels. Explicit 1.0
clients reject 1.1-only operations locally without changing their contract.

Deliverable: live traces/tests for each transport, isolated local errors,
acknowledgements without duplicate events, no task cancellation on disconnect,
and no leaks on half-close/terminal/error paths. Old custom executors have a
documented migration; their unsupported default must not masquerade as 1.1 live
support. Only now enable appropriate 1.1 interface declarations in tested setups.

## Phase 6: completion audit and documentation

Map all fourteen acceptance IDs, every row of `validation.md`, and each
requirement above to actual test names and results. Add user documentation only
under `docs/content/dev/` and update
`docs/data/versions/dev.yml` for page changes. Document provisioning examples,
reduced older-client task views, client default and explicit 1.0 pinning,
public observation/live usage, API/SPI/store/executor migrations, and operational
limits. Leave frozen release documentation unchanged.

Use focused commands during phases, for example:

```bash
mvn -pl client/base,client/transport/jsonrpc,client/transport/rest,client/transport/grpc -am test
mvn -pl server-common -am test
mvn -pl reference/multiversion-jsonrpc,reference/multiversion-rest -am test
mvn -pl tests/multiversion/jsonrpc,tests/multiversion/rest,tests/multiversion/grpc -am verify
mvn -pl extras/task-store-database-jpa -am verify
mvn -pl extras/queue-manager-replicated/tests-multi-instance/tests -am verify
```

Confirm reactor selection and test-discovery behavior against current POMs;
these commands are starting points, not evidence of execution or coverage.
Record actual discovered test counts and skip reasons. Once relevant checks
pass, run the repository's required final build:

```bash
mvn clean install
```

Docker-dependent JPA and multi-instance tests must actually execute on suitable
infrastructure. A successful build that skips them does not prove their rows.
Run target-revision TCK/conformance checks where available; if missing, document
the missing coverage and supply direct protocol tests without claiming official
certification. Verify Java 17 compatibility through the configured toolchain.

Audit the public-client feature matrix and independent historical evidence
separately. Assert no test client bypasses public APIs to claim SDK usability.
Document the later-version extension path and unresolved API/SPI decisions.

Do not declare completion until the same-instance three-version matrix, API/SPI
migrations, persistence/multi-node guarantees, and all required 1.1 client and
server behaviors have direct evidence. If infrastructure or normative decisions block
verification, report the exact gap and leave the corresponding work incomplete.
Do not mark the goal achieved solely because the tests already present pass.

Final report: implemented behavior, validation evidence, material limitations,
and any approval-dependent actions still outstanding. Keep RC support labeled
RC until final-spec reconciliation has passed.
