> Historical worker report: the dated implementation/correction notes below preserve their original checkpoint. The current candidate subsequently passed the complete 83-module clean build (3,918 executed / 123 unchanged skips / zero failures or errors) and independent source/runtime review. See [current verification](verification.md), [current review disposition](review-response.md), and [next-session handoff](next-session.md) for the final candidate and commit-approval state. Earlier open/stopped/readiness wording below is historical, not a current blocker declaration.

# Phase 1 client binding and telemetry implementation report

Worktree: `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`; initial HEAD `e468dd70a415d17c19ae70d75750b6464d983ad1`. Scoped client transport/telemetry work only. No commit, push, PR, schema/codec changes, later-phase handles or invented protocol guarantees.

## Implemented behavior

- JSON-RPC, REST and gRPC built-ins implement root-owned `VersionedClientTransport`: immutable selected version and `ORDINARY` only. Providers declare immutable explicit 1.0/1.1 ordinary profiles.
- Existing implicit constructors default independently to 1.1. JSON-RPC adds `(String url, String protocolVersion)` and `(AgentCard, String protocolVersion)`; REST adds `(AgentCard, String protocolVersion)`; gRPC adds `(Channel, AgentCard, String protocolVersion)` and the existing four-argument tenant/interceptor constructor plus trailing `String protocolVersion`. Both card-only paths select exact GRPC/version and inherit the descriptor tenant; explicit tenant paths filter exact GRPC/version/normalized tenant. `GrpcTransport.forInterface(Channel, AgentCard, AgentInterface, List<ClientCallInterceptor>)` carries an already-selected descriptor without reselecting the card; the provider uses this factory. Old nullable four-argument calls remain unambiguous.
- Existing descriptor constructors retain signatures, honor explicit 1.0/1.1 and validate supported versions before constructing their HTTP client. Direct card constructors select the exact binding/version through root's SPI helper. Shared `AgentInterface` convenience/default behavior and `CURRENT_PROTOCOL_VERSION` remain unchanged at 1.0.
- Every ordinary operation, POST/GET/DELETE and SSE path uses the instance version. gRPC provider forwards descriptor version and tenant; immutable per-instance stubs can reuse a caller-owned Channel without changing another instance's selected version.
- Initial context headers and each `ClientCallInterceptor` result are normalized through root's `ClientProtocolVersion.normalizeHeaders`. Identical case-insensitive version entries become one pinned header; any conflicting entry fails before an HTTP exchange or gRPC call. No promise is made about arbitrary user Channel implementations mutating metadata afterward.
- JSON-RPC scopes every operation through the selected descriptor tenant. Protocol 1.1 also scopes an explicit request tenant through `Utils.buildBaseUrl`; demonstrated 1.0 calls with no descriptor tenant retain the exact original URL and carry the request tenant in their existing JSON payload. Reference JSON-RPC parsing supplies an absent payload tenant from the route; no DTO mutation/shared codec change is required.
- Both OpenTelemetry wrapper families retain a validated immutable version and copied capability profile; a legacy delegate retains fixed 1.0 ordinary behavior. All ordinary calls continue through their existing tracing/metrics/propagation/callback/resource paths. No unwrap or wrapper bypass is introduced.
- Existing JSON-RPC URL and REST card regression callers are explicitly pinned to 1.0. Mockito is added only as a test dependency to the two HTTP transport modules.

## Test-first evidence read and inspected

Root coordinates all Maven invocations; this agent started no standalone build.

| Root evidence | Observed result before relevant production edit |
| --- | --- |
| `/tmp/a2a-phase1-run/client-wire-red.log` (15:02:06Z) | JSON-RPC 5 tests, 4 expected failures, no errors: descriptor 1.1 emitted 1.0; duplicate versions; context/interceptor conflicts reached exchanges. |
| `/tmp/a2a-phase1-run/client-wire-red-rest-grpc.log` (15:02:46Z) | gRPC 9 tests/4 failures; REST 8 tests/7 failures; no errors. Wrong emitted version, duplicate metadata, conflict-before-I/O and constructor selection failures. |
| `/tmp/a2a-phase1-run/focused-client-green-server-otel-red.log` (15:05:21Z) | REST version tests and gRPC 10 tests passed, including shared Channel version isolation. JSON-RPC's newly added tenant endpoint tests failed on bare root URL. Tracing wrapper 3 tests/2 expected companion-profile failures. Propagation initially had test typo `TaskState.WORKING`; corrected to existing `TASK_STATE_WORKING` before repeating RED. |
| `/tmp/a2a-phase1-run/focused-policy-green-server-red.log` (15:06:35Z) | Propagation 4 tests/2 expected companion-profile failures, no compilation error. Actual W3C propagation/caller-context test already passed. Propagation production changed only after this valid RED. |

`git diff --check` was clean following owned whitespace/import cleanup. The final full owned-module GREEN run is pending root execution when this report is handed back; the earlier focused pass does not establish the final suite result.

## Coverage in source

- HTTP `ProtocolVersionTest` in both modules: selected descriptor version across all 11 ordinary unary/streaming methods; explicit 1.0; case-insensitive identical normalization; conflicts before exchange; interceptor duplicate conflict; unsupported descriptor; default card rejects 1.0-only and wrong-binding card. JSON-RPC additionally checks selected descriptor tenant across all 11 URLs and explicit request tenant URL.
- `GrpcTransportTest`: default 1.1 wire metadata; provider descriptor version and tenant; identical normalization; context conflicts; alternating 1.0/1.1 transports on a shared Channel; all 11 unary/streaming metadata paths; context/interceptor conflicts on all 11 paths; existing push-notification ID/tenant regressions.
- Both `VersionedWrapperTest` suites: configured wrapper retains immutable selected profile, capability defensive copy, legacy ordinary 1.0 profile, and unconfigured identity. Propagation additionally uses real W3C trace injection and checks caller headers/state remain preserved.
- Existing tracing/metrics suite and existing HTTP wire/streaming suites remain part of the requested final full-module run.

Root must append exact final GREEN command, discovered counts and skips to the overall Phase 1 evidence before claiming completion. Public paired-client compatibility and historical binary/wire probes belong to root/paired-test ownership; this report makes no claim that unit coverage establishes full experimental 1.1 conformance.

## First full-suite follow-up (15:08:17Z)

Root's `/tmp/a2a-phase1-run/client-otel-green.log` and JSON record exited 1: JSON-RPC 49 tests had 1 HTTP404 error (`testListTasksWithTenant`); gRPC 24 and REST 51 passed; propagation 4 passed; tracing failed compilation. These were inspected using systematic-debugging and receiving-code-review workflows before changes.

- Tracing root cause: a broad source replacement accidentally inserted the outer profile fields into nested `OpenTelemetryEventConsumer`, without initialization. Removed only those accidental inner fields and restored its original tracer declaration. This was candidate 1's compile failure; next verification is pending root.
- JSON-RPC root cause: unconditional request-tenant URL scoping changed demonstrated explicit1.0 behavior. A narrow `requestUrl` helper now preserves the original exact URL for 1.0 with no descriptor tenant; selected descriptor tenant still scopes either version, and 1.1 uses the approved request-tenant route. Existing test expectation remains unchanged. Added exact trailing-slash preservation coverage and extended descriptor-tenant coverage to both versions. This was candidate 1's legacy-routing failure; next verification is pending root.
- Independent review found direct gRPC Channel/card construction lacked exact advertised binding/version/tenant selection. Added five failing-behavior tests before changing that implementation: 1.0-only default-card rejection, wrong binding rejection, card permutations/descriptor tenant, explicit tenant/version rejection, and explicit1.0 descriptor tenant inheritance. Original ID/tenant regressions now use explicit1.0 and matching descriptors; new default tests advertise explicit1.1. Root owns the next RED capture. Approved API direction is a static `forInterface` factory, avoiding an overload that would make existing nullable four-argument constructor calls ambiguous.

## Direct gRPC selection correction (RED 15:13:04Z)

Inspected `/tmp/a2a-phase1-run/grpc-card-selection-red.log` and JSON record: 18 tests, 5 expected failures, 0 errors before production change. Card-only constructors now select exact GRPC/version and inherit that descriptor's tenant. Existing explicit tenant signatures filter exact binding/version and null-or-blank normalized tenant; absence fails locally. The provider uses the additive static `forInterface` factory to carry its already-selected/stale descriptor without card reselection. This avoids introducing a public AgentInterface overload that would make old nullable String-tenant constructor calls ambiguous. No Channel ownership or global version state changes. Root owns final GREEN verification of this correction and the two full-suite regressions above.


## Final root verification and independent review

The final parent-owned `mvn -B -ntp clean install` completed at
2026-11-10T16:03:38.421153Z, exit 0, no timeout, 643.588 seconds within
1,200 seconds. All 83 modules succeeded; 288 fresh XML suites record
3,987 discovered / 3,864 executed / 123 unchanged skips / 0 failures / 0 errors.
The user-requested fresh reviewer independently confirmed these totals and has
no open blocking Phase 1 finding. Earlier pending statements above describe
their historical checkpoint only. See [final evidence and limits](verification.md)
and [review verdict](evidence/fresh-review.md). This does not establish full
A01–A14 acceptance or final 1.1 conformance. No commit, push, PR or Phase 2.


## Subsequent correction checkpoint — readiness withdrawn

Earlier full-build/no-blockers statements describe the pre-correction tree.
REST F2/native03 F3 now pass37focusedtests and selected37module regression
(2802executed,99existing skips,no failures), with known grpcclass explicitly
excluded. Strict F1 retains one failing later-tracer test. User requested
[design](grpc-channel-design.md) before compatibility changes; Phase1 is incomplete.
See [current verification](verification.md). No commit/push/PR/Phase2.
