> Historical worker report: the dated implementation/correction notes below preserve their original checkpoint. The current candidate subsequently passed the complete 83-module clean build (3,918 executed / 123 unchanged skips / zero failures or errors) and independent source/runtime review. See [current verification](verification.md), [current review disposition](review-response.md), and [next-session handoff](next-session.md) for the final candidate and commit-approval state. Earlier open/stopped/readiness wording below is historical, not a current blocker declaration.

# Phase 1 server implementation report

Scope: Phase 1 only on `support/a2a-1.1.0`, initial HEAD `e468dd70a415d17c19ae70d75750b6464d983ad1`. Root owns reactor commands, iteration/deadline accounting and final acceptance. No commits, pushes or PRs. Incomplete 1.1 requires an explicit experimental registration. No schema, protocol conversion, task mutation, replay or live changes.

## Agreed API and resulting behavior

- New immutable `InterfaceRegistration(TransportProtocol binding, String endpoint, @Nullable String tenant, Set<String> versions, boolean experimental)` requires a nonempty exact subset of 0.3, 1.0 and 1.1. The collection is copied, tenants normalized, and enabling 1.1 requires `experimental=true`. Any 0.3-containing registration requires the normalized tenant to be null; 0.3 has no tenant semantics for any binding. `supportedInterfaces()` derives card descriptors from the same set. `select(requested)` selects exactly and returns `ServerVersionPolicy`; absent/empty/blank means 0.3. `matches(descriptor)` checks binding, configured endpoint URL and normalized tenant. `requireAvailableVersions(available)` rejects missing adapters.
- New optional `InterfaceRegistrationResolver.resolve(binding, tenant)` is scoped by the application to the configured route/server endpoint. A present resolver returning null disables that interface; no resolver preserves legacy entry points. Distinct endpoints must use appropriately scoped resolver instances. No request-authority reconstruction or process-wide exposure union is used.
- `ServerCallContext.attachInterfaceRegistration(registration)`, `getInterfaceRegistration()` and `getVersionPolicy()` are additive. Attaching registration cannot replace the original requested version. `AgentCardRequestPolicy` validates effective tenant, and `A2AVersionValidator` requires a matching binding/endpoint/tenant/version descriptor. Existing unregistered validation retains its historical patch compatibility.
- HTTP reference routes use the same registration as dispatch. Default endpoint registrations support all seven nonempty subsets; tenant registrations are restricted to the three nonempty 1.x-only subsets. The optional 0.3 adapters preserve original 0.3 and default endpoint context through the existing converters. Registered custom context factories cannot override the actual header/query selected version.
- The shared `lf.a2a.v1` gRPC service rejects 0.3, including absent/empty/blank metadata, even if the separate old service is installed. The optional old 0.3 service accepts enabled 0.3 only and rejects explicit 1.0/1.1. Its historical protobuf has no tenant field, so its registration lookup is for the configured default endpoint; real separate default-endpoint profiles test all seven subsets.
- Default registrations are validated at route setup/gRPC startup for optional 0.3 adapter availability without adding a production dependency to 0.3. Dynamic tenant registrations are checked per request. The gRPC startup observer alone is `@PermitAll` to avoid requiring a request identity during StartupEvent; class-level RPC authentication remains intact.
- `ServerVersionPolicy` freezes existing 1.0 mappings and corrects existing 1.1 mappings: REST task-not-cancelable/content-type-not-supported use 400 and invalid-agent-response uses 500; gRPC push-not-supported/unsupported-operation/version-not-supported use FAILED_PRECONDITION. Dispatch, preflight, ordinary errors and asynchronous stream errors retain the request policy. Legacy REST error mapper overrides remain honored through the original public overload.
- New 1.1 REST dispatch errors use existing rich ErrorInfo encoding so clients recover typed causes. New 1.1 JSON-RPC dispatch errors preserve a valid original string/number request ID, avoiding an id:null deserialization error masking the typed rejection. Demonstrated legacy envelopes remain unchanged.

## Exact production paths owned by this agent

- `server-common/src/main/java/org/a2aproject/sdk/server/version/InterfaceRegistration.java` (new)
- `server-common/src/main/java/org/a2aproject/sdk/server/version/InterfaceRegistrationResolver.java` (new)
- `server-common/src/main/java/org/a2aproject/sdk/server/version/ServerVersionPolicy.java` (new)
- `server-common/src/main/java/org/a2aproject/sdk/server/version/A2AVersionValidator.java`
- `server-common/src/main/java/org/a2aproject/sdk/server/ServerCallContext.java`
- `server-common/src/main/java/org/a2aproject/sdk/server/AgentCardRequestPolicy.java`
- `reference/common/src/main/java/org/a2aproject/sdk/server/common/quarkus/VersionRouter.java`
- `reference/jsonrpc/src/main/java/org/a2aproject/sdk/server/apps/quarkus/A2AServerRoutes.java`
- `reference/rest/src/main/java/org/a2aproject/sdk/server/rest/quarkus/A2AServerRoutes.java`
- `reference/grpc/src/main/java/org/a2aproject/sdk/server/grpc/quarkus/QuarkusGrpcHandler.java`
- `reference/multiversion-jsonrpc/src/main/java/org/a2aproject/sdk/server/multiversion/jsonrpc/MultiVersionJSONRPCRoutes.java`
- `reference/multiversion-rest/src/main/java/org/a2aproject/sdk/server/multiversion/rest/MultiVersionRestRoutes.java`
- `transport/grpc/src/main/java/org/a2aproject/sdk/transport/grpc/handler/GrpcHandler.java`
- `transport/rest/src/main/java/org/a2aproject/sdk/transport/rest/handler/RestHandler.java`
- `compat-0.3/reference/jsonrpc/src/main/java/org/a2aproject/sdk/compat03/server/apps/quarkus/A2AServerRoutes_v0_3.java`
- `compat-0.3/reference/rest/src/main/java/org/a2aproject/sdk/compat03/server/rest/quarkus/A2AServerRoutes_v0_3.java`
- `compat-0.3/reference/grpc/src/main/java/org/a2aproject/sdk/compat03/server/grpc/quarkus/A2AExtensionsInterceptor_v0_3.java`

## Exact test paths owned by this agent

- `server-common/src/test/java/org/a2aproject/sdk/server/version/InterfaceRegistrationTest.java` (new): explicit/immutable exposure, all seven subsets, optional availability, binding/endpoint/tenant card scope and immutable attachment.
- `server-common/src/test/java/org/a2aproject/sdk/server/version/ServerVersionPolicyTest.java` (new): frozen 1.0 baseline, corrected existing 1.1 mappings and unchanged CURRENT_PROTOCOL_VERSION=1.0.
- `reference/common/src/test/java/org/a2aproject/sdk/server/common/quarkus/VersionRouterTest.java`: header/query precedence, missing/blank normalization, optional module absence and startup default validation.
- `reference/jsonrpc/src/test/java/org/a2aproject/sdk/server/apps/quarkus/A2AServerRoutesTest.java`: actual route entry rejects custom context version override, both provisioned/disabled 1.1 and header/query forms.
- `reference/rest/src/test/java/org/a2aproject/sdk/server/rest/quarkus/A2AServerRoutesTest.java`: equivalent custom context ingress tests. Existing legacy public mapper assertions were preserved.
- `transport/grpc/src/test/java/org/a2aproject/sdk/transport/grpc/handler/GrpcVersionRegistrationTest.java` (new): shared-service 0.3 rejection, version mapping, version-before-capability order including extended-card ingress.
- `transport/rest/src/test/java/org/a2aproject/sdk/transport/rest/handler/RestVersionPolicyTest.java` (new): actual cancel error returns old 409 for 1.0 and corrected 400 for 1.1.
- `compat-0.3/reference/grpc/src/test/java/org/a2aproject/sdk/compat03/server/grpc/quarkus/GrpcRegistrationInterceptorTest.java` (new): real ServerCall recorder for old-service enable/disable and exact metadata handling; no new Mockito dependency.

The paired-test agent owns shared fixtures, all-binding runtime provisioning, stale-card clients, card-route provisioning, gRPC profiles, auth regressions and optional-0.3-free packaging tests. Client and wire compatibility work is owned separately.

## Root-orchestrated evidence

Exact command arrays, start/end times, exit code and timeout state are in `/tmp/a2a-phase1-run/<label>.json`; console evidence is the matching `.log`. Root's `command-summary.json` sums only suites actually executed by each command. Counts below never aggregate stale Surefire XML from earlier commands.

| Label | Evidence and resulting change |
| --- | --- |
| `server-red` | 15:01:38Z expected absent registration/policy API compile RED before production. |
| `core-transport-red` | 15:07:32Z registration/policy 7 tests passed; actual REST 1.1 cancel expected400/got409 RED; new gRPC hook compile RED. |
| `legacy-registration-red` | 15:11:40Z expected configuration throw absent: automatic optional-0.3 guard RED. |
| `legacy-grpc-api-red` | 15:12:37Z isolated absent optional resolver field compile RED before old-service guard. |
| `startup-and-preflight-red` | 15:15:32Z startup validation API absent and actual disabled-version error lost to capability error (-32004 versus -32009). |
| `server-regression-check` | 15:19:07Z 330 tests discovered, 2 failures/4 errors: manually constructed JSON-RPC routes had null optional injection; legacy REST mapper extension hook was bypassed. Fixed production null handling/virtual delegation without weakening old assertions. |
| `paired-first-verification` | Actual REST card bypass/typed-error failure, JSON-RPC stale-card id:null masking, auth startup interception and invalid gRPC whitespace wire input identified before corresponding corrections. |
| `server-review-red` | 15:23:40Z 124 tests, exactly 3 actual failures: JSON-RPC/REST custom-factory override and gRPC extended-card version preflight. Fixed after captured RED. |
| `rest-final-focused` | 15:31:24Z exit0: REST ordinary/provisioning66 tests with9 skips and divergent default-card2 tests, zero failures/errors; default card scope fix verified. |
| `server-review-green` | 15:26:00Z exit0, 39.983s, no timeout. Exactly 8 suites/291 tests, zero failures/errors/skips: core policy1+registration6, router10, JSON-RPC routes36, gRPC handler61+registration4, REST handler89+routes84. Old-service/lazy/core legacy suites not selected by this command are not included in this count. |

Early fixture compilation mistakes (private Builder constructors and assuming Mockito in the old-service test module) were corrected using existing builder factories and a real recorder. Those command failures are retained in root logs and are not described as behavioral RED evidence.

## Current remaining verification and limits

- Root is executing the final clean changed-reactor verification (`full-changed-reactor-final`). No server production/test edits remain pending that result. The earlier `full-changed-reactor` failed in the example migration; its failure evidence is retained below.
- Historical `/v1/card` is a global/default 0.3 card endpoint. `card-default-scope-red` ended15:30:01Z with actual404 versus required400 under default{1.0,1.1}/tenant-v1{0.3,1.0,1.1}. The guard now resolves default/null tenant. `rest-final-focused` ended15:31:24Z exit0: ordinary REST66 tests (9 skips) plus divergent default-card2 tests, with zero failures/errors. Those first-review findings passed; the subsequent fresh review and corrections are recorded below. Full-reactor verification remains with root.
- A whitespace-only gRPC metadata field is rejected by HTTP/2 before the interceptor. Valid absent and empty metadata are covered on the wire; blank-to-0.3 normalization is covered directly at the boundary. No transport rejection is claimed as an application version rejection.
- Exact explicit interface advertisement is selected SDK policy; the pinned RC's conflicting latest-minor comment remains a conformance residual. Existing ordinary operation coverage does not establish full 1.1 conformance or authorize production advertisement without the experimental flag.
- No commits, pushes, PRs or later-phase work performed.


## Fresh independent review checkpoint (15:45 UTC)

The previous focused passes do not establish full Phase 1 acceptance. Fresh review is retained in `/tmp/a2a-phase1-run/fresh-review.md`. The bounded clean reactor failed in the existing stream-lifecycle example (3 failures); the root owns explicit-1.0 example migration and its verification. Later reactor modules were skipped, so no full-reactor pass is claimed.

The user clarified that protocol 0.3 has no tenants. New registered 0.3 tenant aliases and corresponding subset evidence are invalid and are being corrected without changing native 0.3 wire data or legacy no-resolver behavior. The historical card fix passed only its card case; ordinary REST dispatch still interprets native `/v1/...` as tenant v1 and requires correction under divergent default/v1 registrations. This shares the historical-prefix cause with the retained card ledger and does not erase either prior failure.

- New `InterfaceRegistrationTest` regression covers every binding and all four 0.3-containing sets, rejecting non-null normalized tenants; normalized-default forms remain valid. Its subset helper now uses default/null for 0.3 and retains tenant-a for 1.x. `native03-registration-red` ended15:45:54Z exit1 with8 tests/1 expected negative failure; normalized-default positive passed. The constructor guard is now implemented after normalization; `native03-rest-routing-red` also verified all8 constructor tests passing.
- Planned registered REST correction: native 0.3 `/v1/...` resolves default/null registration and context; fabricated 0.3 tenant aliases reject; explicit 1.x `/v1/...` remains tenant v1. Both body/no-body dispatch paths need the same rule. `native03-rest-routing-red` captured actual public0.3 get/send errors on the enabled default endpoint. Both dispatch paths now use the same selector: registered03 only permits native v1 paths, clears the path tenant, and resolves default/null;1.x retains its tenant; no-resolver behavior is unchanged. `native03-and-paired-focused` passed as recorded below; no server functional corrections remain pending.
- Paired agent is adding actual optional public 0.3 clients alongside shared 1.0/1.1 clients, default-endpoint seven-subset profiles and native REST divergent-scope coverage. Existing raw/stub/legacy tests are complementary evidence and do not substitute for those public-client checks.

The temporary user deferral was superseded by explicit instruction to proceed. No guard/routing production changes were made during the pause; no commit is authorized.


## Fresh review corrections verified (15:52:20 UTC)

`native03-and-paired-focused` completed exit0 across44 reactor modules, no timeout:400 discovered tests,360 executed,40 skipped, zero failures/errors. These are the command's actual suite totals, not stale XML aggregates. It selected native03 tests, real default-endpoint provisioning subset tests, multiversion tests, InterfaceRegistrationTest and optional-0.3-free packaging tests across all three bindings/reference modules.

- All8 constructor tests passed. Every binding rejects tenant-qualified 0.3 registrations across all four sets containing0.3; null/empty/blank/slash normalized-default forms remain valid. Existing 1.x tenant/binding/endpoint validation remains intact.
- `Native03RestDefaultScopeTest` passed4 tests: actual old public SDK get/send use the enabled default endpoint despite a divergent1.x-only tenantv1, explicit1.x `/v1` keeps its actual tenant behavior, and fabricated03 tenant aliases reject.
- `Native03RestDisabledDefaultScopeTest` passed2 tests: old public SDK get/send reject when default03 is disabled. Raw native requests establish the version error while preserving the old SDK's frozen generic-error decoding behavior.
- Real default-endpoint seven-subset profiles and actual public old03 SDK calls passed over JSON-RPC, REST and gRPC. The paired agent owns precise per-binding/test-path accounting; invalid03 tenant subset evidence is superseded by these default-endpoint profiles. Tenant matrices now use only nonempty1.x subsets.

Production correction paths since fresh review are only `server-common/src/main/java/org/a2aproject/sdk/server/version/InterfaceRegistration.java` (reject03+nonnullnormalizedtenant) and `reference/multiversion-rest/src/main/java/org/a2aproject/sdk/server/multiversion/rest/MultiVersionRestRoutes.java` (one shared selector for body/no-body native03 default scope, rejectaliases, retain1.x tenantv1 and legacy no-resolver behavior). The new unit regressions/helper correction are in `server-common/src/test/java/org/a2aproject/sdk/server/version/InterfaceRegistrationTest.java`. No0.3 schema, protobuf, converter or generated code changed.

All server functional review findings are resolved in focused verification. Root's final clean reactor and final independent review remain pending. No full1.1 conformance or broad acceptance claim is made; no commits/pushes/PRs.


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
