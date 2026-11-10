# Independent Phase1 attempt safeguard audit

Audited retained per-command JSON metadata and failure summaries in `/tmp/a2a-phase1-run` through `card-default-scope-red` (2026-11-10). No Maven commands rerun. This audit concerns the requested limit of3 failed material implementation/verification cycles for the same failing behavior; it does not reset historical Phase0 accounting or mark A01–A14 passed.

## Assessment

**No observable behavior currently has3 failed material implementation/verification cycles in the inspected evidence.** The highest conservative count is2 for historical REST card provisioning/scope (initial ungated route, then gate using versionprefix as tenant). The pending default-tenant correction is the next implementation attempt for that same scenario. If its focused verification fails materially, stop dependent work for that scenario and report the safeguard; do not relabel it as a new criterion or reset it because other paths pass.

A command failure is not necessarily one failure of every scenario in its reactor. Conversely, review tests called RED are not automatically exempt once they test an already implemented Phase1 behavior. The table counts such review regressions conservatively against the first incomplete implementation. Pre-firstimplementation missingAPI/TDD evidence and test/harness mistakes are retained but distinguished.

| Stable observable behavior | Material failed cycle evidence | Conservative failed count | Resolution evidence/status |
| --- | --- | ---: | --- |
| Exact client version selection/default | selection-red predates implementation |0| focused/client-review-green pass |
| Pinned HTTP/gRPC header emission and SDK override conflicts | client-wire-red and client-wire-red-rest-grpc predate implementation |0| all11 operation paths pass client-review-green |
| JSONRPC descriptor/request tenant path plus legacy1.0 root preservation | focused-client-green-server-otel-red missingselectedtenant; client-otel-green legacy1.0tenant404 after routingfix |2 if grouped conservatively| client-review-green passes bothnewtenant andunchangedlegacyroot regressions |
| Telemetry profile retention and compilable tracing module | VersionedWrapper RED before profile implementation excluded; client-otel-green straynestedfinalfields is material |1| client-review-green tracing28/propagation4 pass |
| Direct gRPC exact card/binding/version/tenant selection | grpc-card-selection-red after initialconstructor implementation |1| client-review-green18 transport tests pass |
| Malformed companion profile cleanup/diagnostics | capability-review-red after initialpolicy implementation |1| client-review-green7 policy tests pass |
| Legacy provider/wrapper source/binary/wire | no material failing cycle |0| binary4/source-recompiled4 pass; classfilesidentitychecked |
| Correct requested1.1 REST cancel mapping | core-transport-red precedes mapping implementation |0| RestVersionPolicyTest and handler suites pass |
| Optional03 availability and activation validation | legacy-registration-red plus startup-and-preflight-red reveal incomplete initial per-requestonly validation |up to2 if grouped conservatively| server-review-green router/startup code passes; actualonly11packaging classpaths pass initial integratedrun |
| gRPC disabled version before capability | startup-and-preflight-red after initialregistered handler implementation |1| server-review-green passes |
| Legacy reference route construction/null optional injection | server-regression-check4nullinstance errors |1| server-review-green36JSONRPCtests pass |
| Legacy virtual REST error mapper hook preservation | server-regression-check2mockedhook failures |1| originallegacyassertions pass server-review-green84RESTtests |
| HTTP ingress version cannot be overridden by custom context | server-review-redJSONRPC+REST mismatch cases |1 per sharedbehavior, not2bindings| server-review-green passes |
| Shared gRPC extended-card registered preflight | server-review-red extendedcardwrongcapabilitybeforeversion |1| server-review-green extendedcard-specific test passes |
| Public stale1.1 JSONRPC error correlation | paired-first-verification requestidnullmasking |1| paired-and-packaging-verificationJSONRPC120pass |
| Public stale1.1 REST rich error details | paired-first-verification missingErrorInfo |1| corresponding publicstalecardtest passes paired-and-packaging-verification |
| Historical REST card version/provisioning/default scope | paired-first-verification ungated03route; card-default-scope-red divergentdefault/v1scope afterinitialgate |2| next correction pending |
| Authenticated gRPC startup validation | paired-first-verification authprofilesbootfails |1| @PermitAllobserveronly fixed; paired-and-packaging-verification gRPCprofiles pass |
| gRPC whitespace metadata admission boundary | paired-first-verification expectedapplicationVERSION_NOT_SUPPORTED buttransportHTTP2PROTOCOL_ERROR |0 SDK implementation failures;1 testassumptionfailure| actualwire test nowassertstransportrejection+noingress; directhandler whitespace-to03 testsretained |
| REST subset rich1.1 wire assertion | paired-and-packaging-verification expected-32009 wherecorrect1.1envelopehasHTTP400+ErrorInfo |0 productionfailures;1 stale newtestexpectation| pending assertion correction; retain fullmessage/status/reason checks |

Excluded initial harness/API evidence: client-policy-red and capability-api-red absentnewSPI; server-red/focused-policy-green-server-red absentregistrationAPI; client-propagation WORKING enum typo; old03interceptor test assumed unavailableMockito before rewritten recorder; gRPC absentnewhook compile before implementation. These failures are not successes and remain in logs.

## Command/time scope

Per-command metadata inspected lists no timeout. Reactor commands use600s or900s bounds and execute sequentially; no overlapping Maven reactor intervals found in the inspected per-command start/end timestamps. The largest initial integrated duration is162.210s; subsequent integrated command175.397s. Phase1 deadline and historical Phase0 breach/counters remain root-owned and must be carried forward unchanged. This audit does not authorize work after a time/iteration budget limit.

## Ledger requirement

Keep each scenario's failed count when adding new related coverage. In particular, record the REST card scope refinement on the same row as its initial gate, and keep the two JSONRPC tenant compatibility deficiencies visible together even if component reports also split them by1.0/1.1. Do not mark broad A01/A02/A03/A04 complete from focused GREENs; ordinary-only Phase1 evidence and incomplete full criteria must remain explicit.

## Final focused result

REST historical-card/default-scope third implementation passed in rest-final-focused, ended2026-11-10T15:31:24.727054Z,exit0,25.921s. MultiVersionRestTest66tests/9skips and divergentcard2tests/0skips; nofailures/errors. The scenario retains2 failed historical cycles; it is not reset to0 and did not reach3 failed attempts. NewRESTsubsetrich-error assertion also passes. No safeguard-triggered dependent stop is required by the inspected evidence. Root's bounded1200s fullcleaninstall is pending; any resulting failure must be mapped to the same stable scenario ledger before further corrective work.

## Fresh-review correction audit, 2026-11-10 15:52:20Z

Append-only follow-up by the fresh independent reviewer. Inspected the retained full-changed-reactor failure, example-migration-green, native03-registration-red, native03-rest-routing-red and native03-and-paired-focused JSON/log evidence without rerunning Maven. The previous rows and their historical failed counts remain intact.

| Stable observable behavior | Additional failed cycle evidence | Retained failed count | Resolution |
| --- | --- | ---: | --- |
| Existing 1.0 example factories after shared-client implicit default changes | First full-changed-reactor: StreamLifecycleTest 3 failures from exact selection against 1.0 cards | 1 | Explicit-1.0 correction passes example-migration-green: all 3 stream-lifecycle cases; cloud source/test source compiled, behavioral coverage not claimed |
| Native ordinary 0.3 REST historical `/v1` default-endpoint scope | Initial implementation rejects enabled default 0.3 get/send when it resolves 1.x tenant `v1`; reproduced by native03-rest-routing-red | 1 | Version-aware ordinary dispatch correction, the second implementation, passes all 6 native scope cases in native03-and-paired-focused |
| New registration must not advertise tenant-qualified 0.3 | native03-registration-red: one guard test fails against the initial permissive registration | 1 conservatively | Guard correction passes all 8 InterfaceRegistrationTest cases, including every binding and mixed version sets |
| Disabled old public 0.3 REST client rejection evidence | native03-rest-routing-red: test expected a typed/message version result absent from unchanged old mapper | 0 production failures; 1 test-assumption failure | Preserve generic old client exception and prove HTTP400/-32009/0.3 independently on the same native path; corrected cases pass |
| Default-endpoint seven-subset public-client matrix / old public 0.3 paired coverage | No additional material failed implementation cycle in inspected evidence | 0 | All 21 actual endpoint-profile tests pass; actual old public 0.3 get/send now pass alongside shared clients in all 3 experimental binding suites |

The ordinary REST finding and prior card finding share the historical-prefix root cause and remain linked. Neither card correction changed the ordinary dispatch methods. The card's two failures are not erased, and its focused third pass is not retrospectively presented as proof of all ordinary scope. The new ordinary correction passed at its next actual verification; this is not a renamed third failed card correction. If a broader root ledger criterion covered all historical default scope, preserve that criterion as having needed this additional proof, not as newly starting at zero.

The focused command finished 15:52:20.837192Z, exit0/no timeout, 151.859s within its900s deadline; all44modules SUCCESS. Actual per-suite evidence totals42suites/400discovered/360executed/40existing skips/0failures/0errors. The example correction ended15:44:45Z; registration RED ran15:45:38–15:45:54Z; native route RED ran15:46:42–15:47:23Z; focused verification ran15:49:48–15:52:20Z. These retained intervals do not overlap. Root's final clean reactor began after this focused command and remains the only pending Maven reactor; no new command was started by this reviewer.

**No inspected observable has three failed material implementation/verification cycles.** Prior high-water marks remain two for REST card scope, JSON-RPC tenant/legacy-root behavior and conservatively optional03 availability/activation. Do not convert passing corrections to zero historical failures. Phase0 counters8/A03audit10/no-progress0/breachtrue and root-owned Phase1 time/iteration counters are untouched. This audit is not commit approval or an extension of run limits. The final clean reactor remains pending.
