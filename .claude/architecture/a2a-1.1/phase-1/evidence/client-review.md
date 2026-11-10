# Independent Phase 1 client review

Reviewed working tree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, starting HEAD `e468dd70a415d17c19ae70d75750b6464d983ad1`. Read-only client review, including untracked sources/tests; only this report written to tmp. No builds rerun. Scope: client/base, all three built-in transport/provider implementations, transport SPI additions, both telemetry wrapper families, frozen AgentInterface constant. Server/integration review deferred until root requests the full review.

Inputs read: AGENTS.md; Phase 1 implementation plan; architecture construction/default and capability/provider/wrapper contracts; Phase 0 required-client-rules supplement (later-phase rules excluded from this implementation).

## Findings at initial snapshot

1. **P1 — telemetry client module does not compile.** `extras/opentelemetry/client/src/main/java/org/a2aproject/sdk/extras/opentelemetry/client/OpenTelemetryClientTransport.java:538` introduces final `protocolVersion` and `capabilities` fields into the nested `OpenTelemetryEventConsumer`; its constructor at line 544 never assigns them. Building the client module fails definite-assignment checking before tests can run. Independently confirmed in root-owned `client-otel-green.log:600` and `:671`, compilation diagnostic at source line550. Root already acknowledged and is fixing.

2. **P1 — explicit 1.0 JSON-RPC tenant requests change endpoint and break a demonstrated regression.** `client/transport/jsonrpc/src/main/java/org/a2aproject/sdk/client/transport/jsonrpc/JSONRPCTransport.java:212` (and equivalent changes to all ordinary methods) replaces the old descriptor URL with `Utils.buildBaseUrl(..., request.tenant())` for every selected version. An existing explicit1.0 `listTasks` request with tenant previously POSTed to the root URL with tenant in JSON; it now POSTs to `/tenant`, producing404 against existing deployments/root-only expectations. Root-owned `client-otel-green.log:434–450` shows `JSONRPCTransportTest.testListTasksWithTenant` failing HTTP404. Preserve demonstrated explicit1.0 behavior while providing the approved1.1 descriptor-tenant path. Root is tracking the fix.

3. **P2 — supplied-channel gRPC card construction omits exact advertised-interface selection.** `client/transport/grpc/src/main/java/org/a2aproject/sdk/client/transport/grpc/GrpcTransport.java:84` validates only the requested scalar version; construction never checks that the card contains an exact GRPC/version descriptor. Consequently `new GrpcTransport(channel, oneZeroOnlyCard)` silently constructs/sends1.1, and a REST-only card is also accepted. Both HTTP card constructors reject these cases, as required by the architecture row for card constructors. The new default gRPC test at `GrpcTransportTest.java:138` actually uses CARD containing an implicit1.0 descriptor, so it enshrines the omission. Add exact card/binding/version validation to direct channel/card entrypoints, with separate internal/provider handling if a selected descriptor needs to be carried through. If supplied-channel construction is deliberately exempt, record that as an explicit architecture deviation rather than claiming all card constructors select exact descriptors. Sent to root for resolution.

4. **P2 — invalid companion declaration can bypass construction-failure cleanup and component diagnostics.** `client/base/src/main/java/org/a2aproject/sdk/client/ClientTransportPolicy.java:63` accepts `selected.capabilities()` and line71 calls `retainAll` without guarding malformed or throwing declarations; the outer cleanup catches only A2AClientException at line46. A third-party VersionedClientTransport returning null or throwing IllegalStateException from capabilities/protocolVersion makes build fail with an unchecked exception, without closing the created chain and without naming the responsible wrapper. This matters at the explicitly supported custom extension boundary and is distinct from a wrapper.wrap exception, which is handled. Convert invalid profile inspection into a named A2AClientException inside the owned cleanup boundary; test close invocation for malformed/throwing profiles. Not exercised by current tests.

## Contracts implemented correctly on inspection

- ClientConfig stores immutable exact1.0/1.1 selection, default1.1 independent of frozen AgentInterface.CURRENT_PROTOCOL_VERSION1.0; optional endpoint/tenant selectors apply before binding deduplication/preferences. Explicit unsupported versions reject.
- Old provider create signatures remain unchanged; additive default provider declaration is fixed1.0 ORDINARY. Built-ins explicitly declare1.0/1.1 ordinary. Builder checks declared support before create and checks actual transport version/features afterward.
- Initial transport and every wrapper intersect retained features. Plain transports are accepted only for1.0; required ORDINARY cannot disappear, wrapper exceptions fail1.1, and explicit1.0 retains historical catch/log/continue. SelectedClientTransport delegates all ordinary operations and close through the retained chain; no unwrap/bypass found.
- HTTP and gRPC normalize initial context and every SDK interceptor result before exchange/call. Every case-insensitive A2A-Version entry is checked; identical values collapse into one canonical value. Built-in emission uses the instance version for all11 ordinary unary/streaming operations.
- Both telemetry families implement companion profile, copy version/features once, and retain existing tracing/metrics/propagation/delegation paths. Existing profile tests use defensive-copy mutation checks. Propagation test also verifies actual W3C injection and caller state/header preservation.
- No schema generation, generation/timeline, observation/live or mutation-authority work found in this client scope.

## Real coverage and remaining evidence gaps

Inspected root-owned `/tmp/a2a-phase1-run/client-otel-green.json` and log: command exited1 at15:08:17Z. It is **not GREEN**. gRPC24 tests passed; REST51 passed; JSON-RPC49 had1 error; propagation4 passed; tracing module compilation failed. Root client/base did not run in that reactor because its dependency on the failed JSON-RPC module prevented it. Earlier focused evidence is not a final full-suite result.

HTTP version tests inspect headers for all11 operations using mocked builders whose exchanges throw; they prove construction/emission/conflict guards at that boundary, not successful remote operations. gRPC tests inspect actual in-process server metadata for all11 operations (most service methods intentionally UNIMPLEMENTED), context/interceptor conflict nonarrival, and alternating1.0/1.1 transports over one shared channel. These are useful and correctly limited tests.

Current `ClientTransportPolicyTest` calls the internal helper directly and uses one wrapper at a time. Missing public-builder rows: reject absent provider version support before create; legacy/plain provider adaptation1.0; actual-version mismatch through the public provider path; multiwrapper attempted re-escalation/intermediate removal; operation invocation through wrapper chain (not just profile getters); malformed/throwing profile cleanup. The plan separately requires legacy source/binary compile/load/wire evidence, which root is preparing. Current tests do not establish that evidence by themselves.

Shared-channel limit: the SDK guards ClientCallContext and ClientCallInterceptor metadata and keeps selected versions per transport. Arbitrary caller-owned gRPC Channel/interceptor implementations can mutate metadata below this boundary; the current implementation cannot guarantee conflicting lower-level injection is rejected before I/O. Root explicitly clarified this as a documented limit. Plan Task2 currently says shared-channel metadata conflicts without that qualification; align the report/plan claim with the actual guarded boundary. Simple shared-channel version isolation is covered and passes.

Migration documentation currently consists of implementation Javadocs/reports; ensure the final Phase1 handoff includes concrete public ClientConfig and direct-transport explicit1.0 examples, exact final passing commands/counts/skips, and the incomplete experimental ordinary-only capability scope. No full1.1 conformance claim is warranted.

## Status

Initial client review has actionable findings and cannot approve completion yet. Findings1/2 acknowledged by root; finding3 sent for resolution. Findings and line numbers describe the initial reviewed snapshot and require final recheck after fixes. Final whole-Phase1 review remains outstanding.

## Client recheck after fixes

Rechecked current source and root-owned `client-review-green.{json,log}` (15:14:34–15:14:54Z,20.320s,exit0). All four initial client findings are resolved on inspection and meaningful verification:

1. Stray nested telemetry profile fields removed; tracing module compiles and28 tests pass.
2. JSONRPC central `requestUrl` preserves rootURL+JSON tenant for1.0 when descriptor has no tenant;1.1/descriptor-tenant routing retained. Existing tenant regression and11 focused version tests pass; JSONRPC50 total.
3. gRPC card constructors now perform exact GRPC/version selection, tenant-bearing constructors perform exact normalized-tenant selection, and provider-selected descriptors use additive static `forInterface`. Old public constructors remain callable without a new ambiguous four-argument overload.18 GrpcTransportTest tests pass;29 gRPC module tests total.
4. Capability profile inspection snapshots/validates actual features, catches malformed/throwing RuntimeExceptions with component diagnostics, and enters owned cleanup. Null-profile test and new multiwrapper delegation/removal test pass;7 policy tests total.

Base59 tests pass, including3 new public-builder legacy-provider tests. REST51 and propagation4 pass. Dependency Vertx tests retain5 skips; scoped changed client tests report0 skips. No build rerun by reviewer.

The new multiwrapper test verifies both decorators receive close through the retained chain and prevents a later wrapper restoring removed ORDINARY. Public builder legacy tests and independent frozen compile/load/wire probes address the previous provider evidence gap; final handoff should retain their provenance/results. Client-scope initial findings are closed, subject to any later integration edits and complete Phase1 validation. No full1.1 conformance claim follows from ordinary-only tests.
