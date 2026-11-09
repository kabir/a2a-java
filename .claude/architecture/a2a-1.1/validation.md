# Validation, rollout, and decisions for three server versions

This is a research checklist, not an implemented test suite or an approved
implementation plan. See the [compatibility assessment](compatibility.md).

## Server acceptance matrix

Use the existing 0.3 and 1.0 clients as test peers. Where the main SDK types are
upgraded, retain old-client artifacts or raw captured requests so tests do not
accidentally validate only new serializers against new handlers.

| Scenario | Expected result |
| --- | --- |
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

Tests involving shared tasks are necessary if those tasks remain accessible
through multiple interfaces. If the product chooses to restrict mixed-version
access, test and document that restriction explicitly rather than assuming
projection makes every feature representable to old clients.

## Suggested order for later implementation

1. Capture current 0.3/1.0 wire and lifecycle behavior in regression tests.
2. Resolve the discovery, error-profile, mixed-version task, and RC questions
   below; design the authoritative mutation/storage contract.
3. Evolve shared domain/protobuf types and direct 0.3 mappings, preserving old
   construction and extension points where practical.
4. Add version routing/policies, response filtering, and per-config webhook
   delivery; prove old server interfaces still work.
5. Implement and test generation/timeline mutation consistently across in-memory,
   JPA, and replicated configurations before advertising full 1.1 support.
6. Add long-poll, cursor handling/replay, user artifacts, and live interaction
   against that foundation. Recheck final-spec differences before release.

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
9. **Forward-compatible reading:** new unknown payload rules affect client
   readers as well as server request handling. Existing Gson and protobuf stream
   mappers reject unknown payloads. A complete SDK 1.1 implementation will need
   that work, but general client negotiation remains outside this server study.
10. **Public Java API:** decide constructor, sealed-interface, RequestHandler,
    custom TaskStore, and custom executor compatibility expectations separately
    from wire compatibility. A full `compat-1.0/` stack is only worth revisiting
    if these requirements cannot be met with focused adapters/overloads.

## Validation performed for this investigation

The findings come from direct tagged-file comparison and inspection of the SDK
revision in the index. The documents' relative links and referenced source paths
are checked as part of preparing them. No production code, generated sources,
build configuration, or tests were changed; no Maven build or runtime baseline
was run for this documentation-only investigation. The matrix above remains
required evidence for the eventual implementation.
