# Phase 1 integration and paired public-client coverage

Worktree: `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`; accepted Phase 0 commit `e468dd70a415d17c19ae70d75750b6464d983ad1`.

## Implemented test migration

Existing shared ordinary, authenticated, task-authorization, polling and agent-to-agent public SDK client factories explicitly select protocol 1.0. Shared synthetic and discovered cards explicitly name interface 1.0. The six identified extras integration-test factories (multitenancy, OpenTelemetry, two JPA stores, single-instance and multi-instance replicated queues) also explicitly select 1.0. Existing separate 0.3 behavioral test classes retain their client implementation and expectations.

## New tests and fixture boundaries

`AbstractPairedVersionServerTest` is inherited by the ordinary JSON-RPC, REST and gRPC multiversion suites. Each binding uses one running Quarkus server and constructs separate public `Client.builder` instances selecting explicit 1.0, explicit 1.1 and default 1.1. Each instance exercises the existing substantive assertions for direct Message send, Task send with request context, get, list, cancel, create/get/list/delete push configuration, extended card, streaming send and ordinary subscribe. HTTP request headers and gRPC server-interceptor metadata are captured independently at ingress; every operation must reach the server and emit only its selected version.

Additional public tests cover both same-binding version card orders; endpoint and tenant scope selection across three same-binding descriptor permutations; no exact version match before I/O; actual tenant-scoped requests; and stale 1.1 descriptors reaching a tenant provisioned only for 1.0 and receiving a version error.

The dedicated `PairedVersionTestProfile` enables incomplete 1.1 only in test fixtures. A conditional registration resolver and tenant card router derive cards and dispatch from the same registrations. HTTP raw GetTask requests exercise all seven nonempty tenant version subsets, each explicit protocol, missing/empty/blank version values and unsupported 2.0. The shared gRPC tenant matrix checks 1.0/1.1 provisioned dispatch and rejects 0.3/missing values on the shared 1.x service, whose schema differs from the historical 0.3 service.

Seven separate gRPC provisioning profiles boot the actual default endpoint once per subset. Each profile calls both historical `a2a.v1` and shared `lf.a2a.v1` services with 0.3, 1.0, 1.1, 2.0 and missing/empty/blank metadata. Positive cases assert a real stored task ID; disabled/wrong-service versions assert structured VERSION_NOT_SUPPORTED, or the historical service's UNIMPLEMENTED VERSION_NOT_SUPPORTED rejection. Separate boots are necessary because old 0.3 request messages have no tenant field.

Every managed channel created by paired public gRPC clients is retained and closed. Raw test channels are closed in finally blocks.

## Verification evidence

- `git -c core.fsmonitor=false diff --check -- tests extras/multitenancy/tests/test-common extras/opentelemetry/integration-tests extras/push-notification-config-store-database-jpa/src/test extras/task-store-database-jpa/src/test extras/queue-manager-replicated/tests-single-instance extras/queue-manager-replicated/tests-multi-instance/tests`: exit 0, no output (2026-11-10; read-only verification by test agent).
- Maven commands are coordinated and owned by the parent agent to prevent concurrent targets and port 8081 conflicts. No Maven execution has been performed by this test agent as of this checkpoint. New tests are written; compile/runtime passing status remains pending parent verification and must not be inferred from this document.

Experimental-profile unsupported-version assertions retain envelope/code/decode-precedence checks while checking the new registration rejection message independently of unordered supported-set formatting. The 0.3 behavioral sources and frozen Phase 0 fixture corpus are unchanged.

## Additional REST card-route regressions

Before the server fix, added `MultiVersionRestTest.testHistoricalCardRouteDoesNotServe03For11`: with all three versions provisioned, explicit 1.1 at `/v1/card` must receive a version rejection rather than the historical 0.3 card. Added `RestCardProvisioningWithout03Test` with a separate real default-endpoint 1.0+1.1 profile: explicit 0.3 and missing/empty/blank version requests at that route must be rejected, while public discovery must advertise exactly 1.0 and 1.1. Assertions require HTTP 400 and VERSION_NOT_SUPPORTED represented by either the retained legacy code or the structured error reason. These tests were written and notified to the parent before the production fix; runtime RED/green evidence remains root-owned and pending.

## First integrated runtime result and corrections

Parent-owned first integration log: `/tmp/a2a-phase1-run/paired-first-verification.log`. Actual observed results: JSON-RPC 120 tests, one stale-card typed-error failure; REST 123 tests, three failures (stale-card typed error and two historical card-route provisioning failures); gRPC 122 tests, eight failures and two auth-profile startup errors (31 skips, including boot fallout). These are RED evidence, not passing acceptance.

All seven gRPC subset failures and the tenant raw-matrix failure were `INTERNAL: RST_STREAM closed stream. HTTP/2 error code: PROTOCOL_ERROR` at the whitespace-only header case. In subset 1's retained server output, three old-service GetTask calls succeeded before this failure: explicit 0.3, missing metadata, and empty metadata. The raw space-only HTTP/2 field value is rejected before application interceptors. With the parent's explicit approval, valid missing/empty metadata retain their exact version-policy assertions; whitespace-only wire requests separately require transport PROTOCOL_ERROR and zero ingress-interceptor observations on both services. Application-boundary whitespace normalization remains the server policy/unit-test responsibility.

The auth startup stack identifies `QuarkusGrpcHandler_Subclass.validateRegistrationAtStartup` passing through `AuthenticatedInterceptor` during StartupEvent before any user identity. This was reported to the server agent for a production fix; auth regression expectations were retained.

## Actual 1.1-only classpath tests

Added `Only11JSONRPCPackagingTest`, `Only11RestPackagingTest`, and `Only11GrpcPackagingTest` in the corresponding single-version reference modules, whose test dependencies omit optional 0.3 modules. Shared `Only11TestProfile` provisions only experimental 1.1 from the same conditional registration/card fixtures. `AbstractOnly11ServerTest` asserts actual runtime absence of the 0.3 AgentCard class, exact card versions `{1.1}`, a real stored GetTask through public default Client.builder, local rejection of explicit 1.0 with the actual card, and raw rejection of explicit 1.0, explicit 0.3, missing and empty version values. These new files were handed to the parent for coordinated verification; no Maven command was executed by the test agent.

## Follow-up observed REST codec and route-scope checks

The parent-owned `/tmp/a2a-phase1-run/paired-and-packaging-verification.log` records each of the three actual no-0.3 packaging tests passing (one test per binding). The same run exposed the intended new 1.1 REST rejection codec: HTTP 400, error code 400, `FAILED_PRECONDITION`, and structured `VERSION_NOT_SUPPORTED` details. Only the new requested-1.1 disabled-subset assertion was updated to require code 400 plus that reason; 1.0/0.3/missing/unsupported-2.0 legacy raw `-32009` expectations are retained.

Strengthened the historical REST card fixture before its scope fix: the default endpoint provisions `{1.0,1.1}`, while a distinct tenant literally named `v1` provisions `{0.3,1.0,1.1}` via `test.a2a.v1-versions`. Existing `/v1/card` explicit-0.3 and absent-version rejection tests must still return 400 because `v1` is a fixed historical prefix there, not a tenant. This distinguishes the two scopes and exposes a gate incorrectly consulting tenant `v1`. Parent and server agent notified before the production null-tenant correction; narrow RED evidence remains parent-owned.

## Fresh-review correction: native 0.3 has no tenants

The user's clarification and `/tmp/a2a-phase1-run/fresh-review.md` supersede the earlier HTTP tenant-alias subset claims in this report. Those earlier results do not establish native 0.3 provisioning. Current fixtures never advertise 0.3 for a nonblank tenant: the resolver removes it and returns no registration if no 1.x version remains. Tenant matrices now cover only the three nonempty 1.x subsets (mask 2, 4, 6); fabricated 0.3 tenant requests are negative cases and contain no 0.3 tenant payload field. The historical card fixture's distinct `v1` tenant is 1.0-only.

Added unchanged public `Client_v0_3` ordinary GetTask and sendMessage assertions on each experimental registered all-three server alongside its shared 1.0/1.1 clients. These helpers exist only in multiversion test modules, keeping optional 0.3 types out of shared test-common classpaths. Native 0.3 REST uses its existing `/v1` prefix and no tenant selector. Dedicated `Native03RestDefaultScopeTest` and `Native03RestDisabledDefaultScopeTest` exercise public get and send with divergent default and 1.x tenant-`v1` registrations: default enabling 0.3 must succeed independently of tenant `v1`; default disabling 0.3 must reject. Tests were handed to the parent before the ordinary routing fix.

Replaced HTTP seven-subset alias coverage with seven actual default-endpoint boot profiles per JSON-RPC and REST binding, matching the existing seven gRPC default-endpoint boots. Enabled 0.3 subsets exercise public old-SDK get and send; enabled 1.0/1.1 subsets exercise public shared-client get; raw negatives and missing/empty/default-version selection remain independent. Existing gRPC default subset tests now also include those public SDK enabled-path assertions, in addition to their generated wire-service checks. Cards come from the same configured registration as dispatch. Original old behavior suites and frozen fixtures are unchanged.

Parent-owned `/tmp/a2a-phase1-run/native03-rest-routing-red.log` captured the actual public old-SDK native REST get/send failures before the production routing fix: default-enabled tests had two errors; default-disabled tests had two assertion failures due to an unsupported assumption about old mapper messages. Inspection confirmed the unchanged old mapper has no version error type and reads a primitive class name plus top-level message, while the retained dispatcher envelope contains a nested error object. Disabled public tests now preserve its exact generic `A2AClientException_v0_3` with empty message and null cause; separate same-native-path GET and valid protobuf-JSON POST require HTTP 400, code -32009 and a 0.3 rejection message to establish version cause. The mapper and legacy envelope were not changed.

Added independent native-profile checks that explicit 1.0 `/v1/tasks/...` still selects tenant `v1` and succeeds, while explicit 1.1 on that path rejects because the tenant is 1.0-only although the default endpoint enables 1.1. Fabricated 0.3 tenant GET and POST aliases require version rejection. No Maven commands were run by this test agent; corrected matrix runtime results remain parent-owned and pending.

## Final focused runtime evidence — 2026-11-10 15:52:20 UTC

**The current native, default-endpoint matrix passed.** Parent-owned command provenance is `/tmp/a2a-phase1-run/native03-and-paired-focused.json`, with output `/tmp/a2a-phase1-run/native03-and-paired-focused.log`:

```sh
mvn -B -ntp -fae -pl tests/multiversion/jsonrpc,tests/multiversion/rest,tests/multiversion/grpc,reference/jsonrpc,reference/rest,reference/grpc -am '-Dtest=Native03*Test,*ProvisioningSubset*Test,MultiVersion*Test,InterfaceRegistrationTest,Only11*PackagingTest' -Dsurefire.failIfNoSpecifiedTests=false verify
```

Start `2026-11-10T15:49:48.977703+00:00`; end `2026-11-10T15:52:20.837192+00:00`; elapsed **151.859 seconds**; deadline **900 seconds**; exit **0**; timed out **false**. The 44-module selected reactor reported BUILD SUCCESS. Its 42 suites discovered 400 tests: **360 executed, 40 skipped, zero failures and zero errors**.

| Selected coverage | Discovered | Executed | Skipped |
| --- | ---: | ---: | ---: |
| JSON-RPC multiversion | 129 | 129 | 0 |
| REST multiversion | 136 | 118 | 18 |
| gRPC multiversion | 124 | 102 | 22 |
| Registration constructor policy | 8 | 8 | 0 |
| Actual 1.1-only classpath tests, three bindings | 3 | 3 | 0 |
| Total | 400 | 360 | 40 |

This includes all **21 actual default-endpoint subset boots** (seven per binding), **six native REST scope tests**, public old 0.3 get/send on the same registered server as shared 1.0/1.1 clients for each binding, and all three no-0.3-classpath/default-public-1.1 tests. In each enabled default subset, public 0.3 get/send and public 1.x get exercise the enabled SDK paths; separate raw calls establish disabled-version rejection and valid missing/empty version behavior. Shared 1.x paired clients also exercise ordinary send, get, list, cancel, push configuration, extended cards, streams and subscription, with ingress-version assertions. Reported skips remain skips and are not passing executions.

**Historical invalid-fixture notice:** all earlier statements in this report describing 0.3 tenant aliases, tenant-scoped 0.3 descriptors, or a `v1` tenant provisioned with 0.3 are withdrawn as acceptance evidence. They record an invalid intermediate fixture and its failures only. The passing current fixture treats **0.3 exclusively at its configured default endpoint**; native REST `/v1` is its historical fixed prefix, while explicit 1.x `/v1` remains a separate tenant. Nonblank tenant registrations expose only 1.x versions. No 0.3 tenant field or tenant-qualified advertisement was introduced. Original legacy behavior suites and frozen fixture inputs remain unchanged.

These are focused Phase 1 policy, provisioning and ordinary SDK results. They do not establish full A2A 1.1 conformance, close later-phase protocol gates, exercise Docker/cluster scenarios, or replace the parent's final clean full-reactor check. That final check is separately running as `/tmp/a2a-phase1-run/full-changed-reactor-final.log`; no result is claimed here. All Maven execution was parent-coordinated; this test agent ran none.


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
