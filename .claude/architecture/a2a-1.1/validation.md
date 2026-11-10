# Validation, rollout, and decisions for client and server versions

This is a research checklist, not an implemented test suite or an approved
implementation plan. See the [compatibility assessment](compatibility.md).

The [architecture](architecture.md) proposes answers to SDK policy decisions and
records the confirmed user constraints. The [implementation-loop
prompt](implementation-loop.md) turns this matrix into later evidence gates.
The user-supplied review confirmed the shared-core direction and its requested
clarifications are incorporated. The questions below retain the original
research findings, rather than implying that every item is still undecided.

## Client and server acceptance matrix

Use production public SDK clients configured explicitly for 1.0 and 1.1, together
on each server/binding. Default-client tests separately assert the user-selected
1.1 default. Preserve existing 0.3 suites. Retain unchanged old client artifacts
or frozen independent fixtures; updated serializers against updated handlers do
not establish old contracts. The original server scenarios below remain required.

### Same-instance version matrix

Run each row over JSON-RPC, REST and gRPC. All enabled clients in a row share one
server instance; include every enabled version pair observing one authorized task.
`works` includes that version's responses, errors and event semantics, not only a
successful connection. Use public explicit SDK 1.x instances and the optional
0.3 client for the enabled paths.

| Provisioned server versions | 0.3 client | Explicit 1.0 client | Explicit 1.1 client |
| --- | --- | --- | --- |
| 0.3 | works | rejects | rejects |
| 1.0 | rejects | works | rejects |
| 1.1 | rejects | rejects | works |
| 0.3 + 1.0 | works | works | rejects |
| 0.3 + 1.1 | works | rejects | works |
| 1.0 + 1.1 | rejects | works | works |
| 0.3 + 1.0 + 1.1 | works | works | works |

For disabled versions, test both local card-selection failure and on-wire server
rejection through stale cards/pinned descriptors or independent raw fixtures.
These are different assertions. Unsupported versions, tenant/endpoint scoping
and missing/blank wire metadata have separate negative tests. SDK clients always
send their pinned version; do not use omission to test the new 1.1 default.

### Client usability and independent compatibility evidence

| Scenario | Required evidence |
| --- | --- |
| Default and explicit construction | Default builder/config and direct convenience paths use 1.1; explicit descriptor or config 1.0 stays 1.0. Migration examples pin 1.0 without changing common signatures. Server missing-version defaults remain unchanged. |
| Card duplicates/order and version overrides | Filter exact version before binding preferences; no card-refresh upgrade; reject conflicting per-call/interceptor headers or duplicate gRPC metadata before I/O. |
| Public send/get/list/cancel/config APIs per binding | Request and response semantics match the instance; both unary/direct Message and streaming paths work. Config delivery version round-trips without changing legacy fallback. |
| Generation/timeline/precondition and artifact public inputs | Public params/companion input types transmit guards and artifacts; typed mismatch errors, no retry/rebase, correct canonical task/timeline/chunks and no phantom persistence for direct Message. |
| Cursor and long-poll public APIs | Cursor/current-generation reach server; no-initial-Task replay has explicit completeness state, resync snapshots replace correctly, waits/handles cancel without task cancellation. |
| Unknown stream payload/timeline kinds | Inject independent future JSON variants and protobuf unknown oneof fields between known events. 1.1 skips without spurious terminal callback/cursor advancement; malformed known data is still rejected. All-unknown streams remain bounded and cancellable. |
| Live-session public APIs | All-binding readiness/input/downlink/half-close and bounded flow; input-local errors permit later success; no duplicate acknowledgement events, no unsafe retry after unknown commit. Resolve HTTP association gate first. |
| Existing custom transport/provider/wrapper | Explicit 1.0 behavior works; unsupported new capabilities fail clearly. Preserve interception/auth, channel ownership and cancellation. API/SPI report includes source/binary examples and decisions. |
| Independent historical peers | Unchanged 1.0/0.3 artifacts run isolated from current reactor dependencies, or frozen fixtures with provenance cover old semantics. Never regenerate expectations from current codecs. |
| Malformed/raw binding cases | Raw tests remain alongside public SDK cases for inputs rejected locally, wire errors, routing, unknown payloads and server-side disabled-version rejection. |

### Handle, compatibility and coverage refinements

| Scenario | Required evidence |
| --- | --- |
| Capability intersection and wrappers | Provider declaration and actual version match; plain wrappers remove unsupported extensions, identity wrappers retain them, wrappers cannot escalate capabilities, configured wrapper failure on the 1.1 path never bypasses interception; explicit-1.0 historical wrapper-error behavior has separate regressions. Include both OpenTelemetry wrapper families. |
| Legacy provider and constant | Keep CURRENT_PROTOCOL_VERSION at 1.0 independently of default client 1.1. Test unchanged compiled and recompiled providers, actual wire version, and an explicit adapter/rejection for a provider ignoring its descriptor and using new implicit defaults. |
| Current projection versus history | Synchronized means as-of represented state, not full lifetime history. Test zero/finite historyLength/timelineLength, unknown returned ranges, migration gaps, unknown skipped events and replacement windows; legacy callbacks are unchanged. |
| Observation handles | Test pre-ready failure/cancel, cursor replay without baseline, bounded demand, immutable currentView, exactly-one completion outcome, deadline/close races, frozen final view and no cancelTask side effect. |
| Live input outcomes | Test admission without commit, correlated commit without executor receipt, local NOT_SENT versus remote REJECTED, cancelled/deadline-after-dispatch INDETERMINATE, half-close drain, opening-input uncertainty and late acknowledgement after terminal result. |
| Missing outcome evidence | For each binding/input shape, pin the actual success/rejection correlation. GetTask/timeline suffix or unrelated generation cannot prove operation status. Mark missing positive evidence as a gate; do not invent a lookup RPC, wire ID or success-on-silence rule. |


### Retained server scenarios

| Scenario | Expected result |
| --- | --- |
| One instance provisions 0.3, 1.0, and 1.1 over each binding | Each client reaches its exact version's server contract; no silent upgrade, downgrade, or fallback. |
| Only a subset is provisioned; 1.x without 0.3 modules | Only advertised/enabled interfaces work; unsupported versions reject; no accidental core dependency on optional 0.3 artifacts. |
| Existing 0.3 request and subscription over JSON-RPC, REST, and gRPC | Existing wire format, mapping, history, blocking, errors, and cancellation behavior remain intact. |
| Existing 1.0 request over each transport | 1.0 semantics and existing 1.0.0 error mappings remain intact unless explicitly changed by a separate policy decision. |
| 1.1 ordinary send/get/list/subscribe | New model fields and requested limits work through the same core and store. |
| HTTP version header, query fallback, absent version, unsupported minor | Existing precedence/defaults survive; unsupported versions are rejected and 1.1 routes correctly. |
| Tenant with different supported interfaces/capabilities | Version acceptance, routing, ownership, and capability checks use the appropriate effective card. |
| Concurrent 1.1 inputs with the same `ifGenerationMatch` | Exactly one accepts at the expected generation; rejection causes no mutation or task failure. |
| 0.3/1.0 mutation while a 1.1 subscriber observes a shared task | Internal sequence and history remain coherent; new message events are visible only to the 1.1 subscriber. |
| Old and new subscribers simultaneously | Dropped new event kinds do not cause mapper failures, demand starvation, early completion, or altered finality. |
| Client message, status progress without state change, artifact chunks | Correct increments and event stamps; timeline includes messages/status and artifact placement is consistent. |
| Store failure during mutation | No successful response or uncommitted stamped event becomes visible. |
| Long-poll below/equal/above current generation, timeout, disconnect | Immediate versus waiting behavior is correct; authorization and resource cleanup remain intact. |
| Resume from retained and expired generations | Replay or snapshot fallback; no snapshot/live handover gap or duplicate mutation. |
| Client artifacts with/without message and capability | Required message for creation, authorized existing-task updates, valid task/context/tenant IDs, correct role/chunking, ignored client generation. |
| Malformed live input, wrong-task input, late subscribe | Exchange-scoped rejection; stream stays usable, other observers and task state are unaffected. |
| Interrupted, terminal, and canceled task | Blocking completion and stream lifetime remain distinct; new mutations after terminality are rejected. |
| 0.3, 1.0, 1.1 webhook configurations on a task | Delivery version is selected per config, survives persistence, and filters events correctly. |
| Existing persisted task/config without new fields | Safe load and explicit migration/default policy; old webhook configs retain 1.0 fallback. |
| Replication, duplicate delivery, finalization, multi-node writers | Stable generation across nodes, no duplicate logical mutation, correct final status stamp, atomic concurrency guard. |
| Concurrent writers commit in order but publish at different speeds | Subscriber events remain ordered; no lower generation arrives after a higher one. |
| Commit succeeds, publisher/node crashes before delivery | Preserve committed state/generation; document the missed-event window and prove gap reconciliation or snapshot fallback. If durable publication is promised, prove recovery without a second mutation. |
| User-origin artifact collides with old agent artifact ID | Preserve creator identity and apply the pinned cross-origin policy; never overwrite hidden input silently. |
| Legacy constructors/builders and custom stores/executors | Common APIs remain usable; necessary extension-point changes have tested migration examples and clear capability checks. |
| Ordinary send returns direct Message with no task; 1.1 input also carries artifacts | Return Message with no task/input/artifact persistence, generation/event publication, or task-bound push config; clean provisional resources. Cover unary/streaming and `returnImmediately`. |
| Existing-task send returns direct Message | Accepted input remains committed under its generation guard; the no-task exception does not roll back an existing task update. |
| Executor creates task with full Task, status, or artifact output | Materialize pending opening input once before publication; normalize first executor output and avoid duplicate history/timeline entries. |
| Executor full Task is stale after concurrent accepted input | Reject its observed-revision proposal without overwriting canonical input/history/timeline/user artifacts, advancing generation, or failing the task. |
| Executor full Task contains status/artifact changes and supplied generations | Ignore supplied sequence authority; normalize matching-revision changes into ordered canonical mutations; preserve legacy output shape through projection. |
| Same executor constructs full Task then commits a delta before submitting it | The old proposal retains its immutable base revision and conflicts; a mutable latest-revision token must not make it appear fresh. |
| Full executor Task contains terminal status and final artifacts | Validate atomically against pre-batch state, apply/deliver final artifacts before terminal status, and close streams only after their delivery. |
| Metadata-only full Task update starts from a status with a recorded message | Stamp one synthetic metadata/status mutation; preserve the existing message identity and legacy occurrence count, with no new Message/message-update event. Its new timeline record is a status observation. |
| New interface registration versus existing deployment entry point | New registration requires explicit versions and documented missing-version rules; primary model/handler does not imply automatic 1.1 exposure. Existing entry-point defaults survive. |
| Missing, empty, or whitespace gRPC version metadata | Resolve to 0.3 and reject if unavailable on the selected service/interface; do not infer 1.0 from the protobuf service name. |
| Executor proposal conflicts after successful queue admission | Originating completion stage/legacy local sink receives correlated rejection; no observer error, task failure, generation advance, or webhook. Refresh/retry succeeds. |
| Inline webhook config with immediately terminal task creation | Enrollment is available before initial notification eligibility, including JPA after-commit finalization. |
| Config store fails, task store fails, or task commit is indeterminate | Config failure precedes task mutation; definite task failure cleans/expires invisible enrollment; uncertain task commit preserves enrollment and reconciles by task/operation identity. |
| Terminal publication is lost and no subsequent mutation occurs | Finite active-observation reconciliation delivers stored terminal snapshot or a documented retryable resync outcome; buffers and waits cannot grow indefinitely. |
| Old server/broker writer attempts write after migration | Coordinated upgrade fencing rejects it; migrated generation/timeline/origin fields cannot be erased or reinitialized. Rollback requires the documented coordinated snapshot restoration. |
| Live input arrives on another node and ownership changes | Input reaches current leased executor owner; correlated acceptance and owner-epoch checks reject obsolete owner output without task mutation. |

Tests involving shared tasks are necessary if those tasks remain accessible
through multiple interfaces. If the product chooses to restrict mixed-version
access, test and document that restriction explicitly rather than assuming
projection makes every feature representable to old clients.

## Suggested order for later implementation

Follow the seven phases in [the implementation prompt](implementation-loop.md):

1. Phase 0 pins semantics, inventories client construction/SPI/stream reduction,
   and captures historical and public API baselines.
2. Phase 1 implements server interface policy together with immutable client
   selection (default 1.1, explicit 1.0), all-binding emission/errors and paired
   public ordinary-operation tests.
3. Phase 2 extends shared types/codecs and public input/query/event/SPI shapes,
   including known new events and unknown-kind handling with fixture-backed tests.
4. Phase 3 delivers mutation/storage alongside public guarded/artifact sends and
   generation/timeline state reduction, with all-binding integration evidence.
5. Phase 4 delivers replay/long-poll/webhook policy alongside public cursor,
   cancellable observation and config APIs; mixed versions observe one task.
6. Phase 5 delivers live execution with public client live handles and binding
   lifecycle/error/flow-control tests, including supported multi-node input.
7. Phase 6 audits the full client/server matrix, API/SPI migrations, historical
   peers, runtime evidence and final-spec reconciliation before release.

No phase claims full usability from generated models or raw wire tests alone.
Do not advertise incomplete 1.1 endpoints as conforming production interfaces.
Phases are dependency groups within the existing bounded runs, not budget resets.

This is dependency order, not a promise that the RC permits partial feature
advertisement as a conforming 1.1 interface. In particular, live interaction
shares the existing streaming capability; the RC has no separate live flag.

## Decisions and RC ambiguities

1. **1.0 error contract:** recommendation is to preserve the current 1.0.0
   mappings for `A2A-Version: 1.0` and use corrected mappings for 1.1. 1.0.1 has
   no distinct negotiable patch identifier. Adopting its fixes for all 1.0
   callers would be an explicit behavioral change, not a way to support both
   interpretations separately.
2. **Discovery declarations:** reconcile the `AgentInterface.protocol_version`
   comment recommending the latest minor per major with the prose permitting
   multiple minor-version interfaces and the SDK's exact-minor validator. Also
   verify validation is scoped to the binding/endpoint as well as tenant.
3. **Subscription cursor semantics:** the new protobuf cursor and live-operation
   rules describe replay without an initial Task, with snapshot fallback. The
   older standalone Subscribe-to-Task prose still unconditionally requires an
   initial Task. Confirm the rule for the ordinary subscription operation and
   terminal-task reconnection.
4. **Generation initialization:** pin down the initial task/message/status
   sequence so creation at generation 1, the opening snapshot, and one-event
   per mutation do not produce duplicate or off-by-one generations.
5. **Existing stored tasks:** old message history does not recover original
   status updates or chunk ordering. Decide whether to start a new sequence
   from a snapshot and record the limitation, rather than inventing exact past
   events. Test whichever migration policy is selected.
6. **Replay retention:** define bounded retention, restart behavior, timeout and
   snapshot fallback, and whether a journal participates in the same commit as
   the Task mutation. A timeline alone cannot replay artifact chunks.
7. **Mixed-version artifact visibility:** decide how an old interface represents
   a task with user-owned artifacts and whether such access is supported. Avoid
   silently relabeling client inputs as agent outputs.
8. **Live execution contract:** define input delivery to an already-running
   executor, acknowledgement versus downlink event behavior, disconnect/half-close
   semantics, and request-local rejection. The current RequestHandler has no
   bidirectional exchange abstraction.
9. **Forward-compatible reading:** production client reading is now in scope.
   Pin unknown-payload/timeline skip behavior and partial-state/cursor handling
   across the existing JSON/protobuf stream decoders. General client version
   negotiation/fallback remains outside scope; it is not needed for this work.
10. **Public Java API:** decide constructor, sealed-interface, RequestHandler,
    custom TaskStore, and custom executor compatibility expectations separately
    from wire compatibility. A full `compat-1.0/` stack is only worth revisiting
    if these requirements cannot be met with focused adapters/overloads.
11. **HTTP live request association:** the RC uses separate uplink calls but
    does not explicitly identify an exchange on the wire. Resolve acknowledgement
    and local-error delivery when multiple exchanges observe the same task;
    do not broadcast a rejected request to other subscribers.

### Later upstream evidence

On 2026-11-09, [upstream PR #2411](https://github.com/a2aproject/A2A/pull/2411)
was open at `d318e2e074bcc967ce5f09b746304a72a9794f03`. Its current diff
proposes cursor-as-hint with snapshot fallback, renames the live input member,
and makes non-mutating, caller-local rejection explicit. It also refines
artifact identity/origin. The PR body and diff disagree about the artifact key;
inspect the pinned diff/source. This proposal is not part of the tagged RC and
must be rechecked before implementation. The architecture records its impact.

## Validation performed for this investigation

The findings come from direct tagged-file comparison and inspection of the SDK
revision in the index. The documents' relative links and referenced source paths
are checked as part of preparing them. No production code, generated sources,
build configuration, or tests were changed; no Maven build or runtime baseline
was run for this documentation-only investigation. The matrix above remains
required evidence for the eventual implementation.
