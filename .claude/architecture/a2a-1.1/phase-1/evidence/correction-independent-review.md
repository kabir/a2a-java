# Independent review of reopened Phase 1 corrections

Worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, unchanged HEAD `e468dd70a415d17c19ae70d75750b6464d983ad1`. This reviewer inspected source, tests and retained command results directly, wrote only this temporary report, and ran no Maven, edits, staging, commit, push or PR action.

## Current findings and verdict

**P1 remains open: the approved general gRPC pre-I/O version-conflict contract is not enforced for later supported extension hooks.** The package-private ProtocolVersionClientInterceptor sees ordinary supplied-channel MetadataUtils header additions and now rejects conflicts/collapses identical values. A legitimate later ClientStreamTracer.Factory can mutate metadata after its check; the actual demonstrator still fails. The existing opaque Channel API prevents the outer SDK interceptor from positioning its guard after all inner hooks. The user explicitly chose to retain the strict contract and requested an enforceable design before compatibility changes. No narrower contract was approved. Phase 1 is therefore not ready for commit approval.

No additional blocking defect was identified in the focused REST and native 0.3 correction source. Their original findings are resolved by the inspected implementation and focused passing evidence, subject to broader regression results still pending. The earlier whole-project clean build is pre-correction evidence, not a verification of the current candidate.

Minor style cleanup: the new REST tests' contiguous import blocks put io.vertx/com.sun imports ahead of java imports and put nested RestHandler.HTTPRestResponse before RestHandler. Sort these to satisfy AGENTS.md. This is not a behavior blocker.

## REST subscription review

The public shared 1.1 client uses getAsyncSSE with the same normalized headers/callback lifecycle; explicit 1.0 retains postAsyncSSE. Both real reference route families now register GET and retain POST. The actual shared handler creates and validates its registered context before selecting GET for policy1.1 or POST for the historical policy, rejects the wrong verb with 405/Allow before invoking RestHandler, and keeps the existing authentication and deferred request-context wrappers for both methods. Native 0.3 POST remains delegated to its historical handler; native 0.3 GET rejects without invoking that handler. No cursor/replay behavior was added.

Tests are meaningful boundary evidence: public Client receives a real SSE Task from an independent JDK HTTP peer accepting only the required verb, asserts exact version/tenant path and exactly one request; raw HTTP invokes the actual single/multiversion Vert.x routes with only the terminal RestHandler boundary doubled. Wrong-verb tests assert zero downstream calls. These avoid the matching-client/server mismatch that concealed the original defect. The fixture HTTP method change in existing direct-handler unit tests supplies the actual historical POST method without altering old assertions.

## Native 0.3 gRPC review

The reference interceptor carries the exact admitted immutable registration in the existing compat gRPC Context. It explicitly clears an inherited value on the legacy no-resolver path. The old transport handler validates literal original0.3, any factory-supplied binding and existing attached registration before conversion, establishes actual GRPC binding and attaches the same admitted object. Existing custom state is retained. On the no-resolver path the historical custom factory result remains unchanged.

All ordinary converter/core delegations obtain context through this helper; stream operations create the validated context before installing the existing forked execution wrapper. The new test invokes the real interceptor's context-wrapped listener, real old transport handler and real old-to-shared converter into a captured RequestHandler boundary. It asserts downstream registration identity/binding/originalversion, conflicting version/binding factory zero execution and legacy no-resolver preservation. No schema, generated source, converter implementation or new dependency was added for this correction.

## gRPC ordering and failure evidence

Independently read exact local grpc-java1.81.0 primary sources. GrpcUtil invokes CallOptions tracer factories in list order; StatsTraceContext.newClientContext later invokes returned tracers' streamCreated callbacks in order. ClientStreamTracer's documented mutable headers permit changes reaching the socket unseen by application/interceptors. MetadataApplierImpl.apply merges credential headers immediately before transport.newStream. Consequently moving the guard into its present factory is a valid standard-header fix but not an unrestricted ordering solution. Server duplicate rejection is useful defense in depth and cannot establish client pre-I/O enforcement.

The client's start-failure forwarding wrapper cancels the delegate before rethrowing. Source ordering supports that cleanup: with retries enabled ClientCallImpl has assigned its RetryStream before RetriableStream.start creates the initial substream/factories; cancellation can remove that registered retry stream. No new SDK retry guarantee was introduced.

Actual retained `correction-grpc-late-tracer-probe` evidence: 23 tests, 22 passing, 1 failure, zero errors/skips, exit1. GrpcTransportTest18 and the four new standard MetadataUtils cases pass; the later-factory assertion fails because no A2AClientException was thrown. Its zero-stream/zero-ingress requirements remain unsatisfied. Do not disable or silently exclude that acceptance failure to claim a full passing candidate. Root has explicitly excluded it only from partial focused/regression commands and retains it as the known blocker.

## Independently verified focused GREEN

Inspected correction-focused-green JSON/log. Exact command selects REST independent peer/routes, native03 downstream context, old admission, shared duplicate ingress and existing GrpcTransportTest across the selected reference modules with -am. Start17:43:11.149709Z, end17:43:27.795748Z, 16.646seconds, exit0, no timeout within900seconds.

Recounted the seven actual suite lines, excluding duplicate module aggregate lines: **37 tests, 0 failures, 0 errors, 0 skipped**:

- GrpcTransportTest18;
- A2AExtensionsInterceptorVersionTest2;
- RestSubscribeIndependentPeerTest2;
- RestSubscriptionRouteTest4;
- MultiVersionRestSubscriptionRouteTest6;
- GrpcRegisteredContextPropagationTest4;
- GrpcRegistrationInterceptorTest1.

This focused result deliberately does not run GrpcChannelMetadataTest's known failing later-tracer case. Standard header cases' evidence is the separate partial probe above. Neither focused result establishes a current whole-project passing build or broad A01–A14 acceptance.

## Safeguards and authority

Correction authorization17:33:12Z–18:33:12Z permits four additional focused iterations, retaining the original eight Phase1 iterations and all prior failed-cycle records. F1 conservatively retains two failed material implementations: original unguarded behavior and partial guard still failing the broader supported-hook criterion. Do not reset by calling the later hook a different unrelated criterion. The three-failed-cycle safeguard remains binding for any subsequent implementation. Phase0 remains8iterations/A03conservative10/no-progress0/originalbreachtrue. Neither this review nor the additional run authorizes a material API/SPI break, a narrowed contract, commit, push, PR or Phase2. Experimental/specification gates remain unchanged.

An enforceable channel design, broader regression evidence, final documentation/inventory reconciliation and final review update remain pending at this checkpoint.

## Broader affected-module regression verification

Independently read correction-affected-regression JSON/log and recounted actual XML whose mtimes fall inside that command's recorded interval. Command17:45:16.610691Z–17:48:57.917444Z,221.306seconds,exit0,no timeout within900seconds. The reactor summary contains **37 SUCCESS module rows**. Exactly **211 fresh suites** contain **2,901 discovered / 2,802 executed / 99 skipped / 0 failures / 0 errors**, matching the retained correction-affected-regression-test-summary.json. No stale filtered reports were counted.

The exact command explicitly passes `-Dtest=*,!GrpcChannelMetadataTest`. Consequently this is a passing affected-module regression with the known five-test class excluded, not a current unrestricted full build. Four of that class's standard-interceptor tests separately passed in the partial probe; the fifth later-tracer case remains failing and is not treated as skipped/passing coverage. The 99 skips in this selected reactor are reported as unverified behavior. Original full-build totals remain pre-correction evidence.

The minor import-order issue is corrected in the current REST test source; the previously noted RestHandler/nested import ordering and Java-before-Vert.x groups are sorted. The final tracked whitespace check is clean. No additional blocking source finding arose from this follow-up. REST and native03 corrections now have focused and broader relevant regression evidence; F1 and whole-Phase1 commit readiness remain open under the retained strict contract.

## Proposed strict channel design review

Read phase-1/grpc-channel-design.md and inspected current GrpcTransportConfig/Builder descriptors and grpc transport POM. The proposal accurately identifies a material runtime compatibility change for both1.0/1.1: arbitrary Channel/factory/raw routing and tracing hooks are no longer accepted in the strict profile. It retains original descriptors and a non-null Function getter using a private SDK-issued adapter, explicitly avoids an unsafe legacy1.0 exception, separates transport security from guarded metadata-producing credentials, and states changed owned lifecycle/backend dependency decisions. Current grpc client artifacts have no concrete ManagedChannel backend dependency; the proposal correctly requests separate backend/dependency approval.

The supported path is coherent as an SDK-owned constrained profile, subject to its explicit implementation/proof gates. A caller-defined marker or outer wrapper is insufficient; untrusted raw interceptors can bypass next entirely, later factories/tracers can mutate metadata and arbitrary credentials may merge afterward. Rejecting unsupported configuration before invoking those callbacks is the appropriate design boundary once explicitly approved. No user metadata/wire extension or retry guarantee is invented. The final streamCreated assertion is appropriately not presented as an already-verified safe rejection mechanism; asynchronous failure/cancellation cleanup must be demonstrated on the exact backend.

Two migration details should be made explicit before approval: an SDK-issued handle must be bound to and checked against the selected canonical endpoint/security authority, not just recognized by class/identity; minimal unshared ownership must atomically claim a handle for one transport and reject repeated use before I/O, or adopt a separately approved lease/pool. Otherwise direct Channel constructors could select a card for a different endpoint or accidentally share shutdown ownership. These are design clarifications, not authorization to implement a public break. All source/binary/old-runtime and transport-boundary proof obligations remain pending.

**Final current verdict:** REST and native03 findings are resolved with relevant passing evidence; standard gRPC MetadataUtils cases are corrected; F1's general strict pre-I/O guarantee remains an open P1 and current Phase1 is not commit-ready. The proposed constrained owned-channel profile is a coherent design for user consideration after the two explicit migration details above are incorporated. Design approval would still not be implementation verification or commit approval. No further failing F1 implementation cycle was consumed by this review/design; the retained conservative failed count is two and the safeguard remains binding.

## Final targeted design clarification review

Verified the updated proposal explicitly binds SDK-issued handles to the canonical selected endpoint/security authority, requires atomic single-owner admission, rejects repeated/closed handle use before I/O, protects the existing owner from a rejected client's close, and requires each SDK factory construction to issue a fresh handle. The acceptance matrix now includes these endpoint/authority/ownership cases. The concrete backend proposal is `io.grpc:grpc-netty` at parent-managed1.81.0 in the existing client/transport/grpc module, with no new production module; dependency/runtime compatibility approval remains explicit.

These additions resolve the two design omissions identified above. One wording precision was sent to root: AgentInterface has no separate TLS-authority field; endpoint matching is against the selected descriptor, while authority matching is against declared TLS options (default derived from the endpoint). This calls for no new model or wire field.

**The constrained owned-channel design is coherent and ready for the user's explicit compatibility/lifecycle/backend decision.** This verdict approves its readiness for that decision, not its implementation or verification. Existing raw-channel users of both1.0/1.1 would face the stated material runtime migration. The broader F1 pre-I/O implementation remains open, the failing demonstrator remains evidence, and current Phase1 is not commit-ready. No narrowing, third F1 implementation attempt, API break or commit has been authorized by this review.
