# Phase 1 explicit version policy implementation plan

> **For agentic workers:** use superpowers:executing-plans with scoped parallel implementation and independent review. No commits without user approval.

**Goal:** explicit experimental server provisioning and immutable exact-version public clients over JSON-RPC, REST and gRPC, preserving demonstrated legacy behavior.

**Architecture:** additive registration/policy in server-common, exact routing through existing shared handlers, separate optional0.3. ClientConfig owns independent1.1 default; SPI owns pure selection/header/version helpers and capability companion. Built-in transports and both telemetry wrapper families carry selected version; builder validates every capability boundary. No new module or model schema changes.

**Tech Stack:** Java17/Maven, JUnit5, Quarkus, MockServer, gRPC, Gson/protobuf JSON.

**Spec:** ../implementation-loop.md Phase1, ../architecture.md and ../phase-0/next-session.md PlanP1; pinned experimental RC78e1d6a1f7f118c8e9bc830b04a8579c1119cb78.

## Global constraints

- Phase1 only. No generation/timeline, observation/controller, mutation authority, live, schema regeneration or invented wire guarantees.
- Immutable client default1.1; explicit1.0 stays1.0; separate optional0.3 client. CURRENT_PROTOCOL_VERSION and AgentInterface model/server convenience default remain1.0.
- No automatic version negotiation/fallback; card version and optional endpoint/tenant selectors precede binding preference. Existing descriptor signatures preserved.
- New registration requires nonempty exact versions; experimental1.1 explicitly enabled. Missing/blank new HTTP/gRPC version resolves0.3, reject when unavailable on selected interface. Legacy entrypoints retain demonstrated exceptions/defaults.
- Wrapper failure on1.1 fails construction, explicit1.0 preserves historical catch/log/continue. Legacy provider fixed1.0 ordinary profile, companion required1.1; no unwrap/bypass.
- Frozen Phase0 inputs/expectations unchanged. Historical counters/breach retained. Three failing implementation attempts per criterion; max10 focused iterations/two hours ending16:57:25UTC. Every local command <=min(1200seconds,remaining budget)+30seconds cleanup.

## Tasks and verification

### Task 1 — selection and capability boundary (root; A02/A04)

Files: client/base ClientConfig, ClientBuilder, Client; new client/transport/spi ClientFeature, VersionedClientTransport, ClientProtocolVersion; provider default capability method; internal legacy transport adapter; ClientBuilderVersionTest/capability tests and SPI policy tests. Preserve old provider create descriptors. Add ClientConfig.DEFAULT_CLIENT_PROTOCOL_VERSION=1.1, getProtocolVersion and Builder.setProtocolVersion; optional setTenant/setEndpoint selectors scope matches. Filter exact version before existing preference. Expose immutable selected version on Client. Provider map defaults1.0 ORDINARY; builtins1.0/1.1 ORDINARY; actual transport version and retained features checked. Legacy plain results adapted only1.0. Every wrapper intersects prior features and cannot remove required ORDINARY for1.1. Test identity/plain/escalating/throwing wrappers and mismatch delegates, default/explicit selection, card-order/tenant/endpoint/missing versions. First RED uses existing APIs to demonstrate wrong current default selection before production edits.

### Task 2 — binding constructors, emission and telemetry (client_wire; A02/A04)

Files: client/transport/{jsonrpc,rest,grpc} transports/providers and tests; extras/opentelemetry/{client,client-propagation} wrappers/tests. Retain direct signatures, default implicit construction1.1 and add explicit1.0 overloads; descriptor selection wins; exact card binding+version helper; gRPC provider forwards version. ClientProtocolVersion.validate(String):String; selectInterface(card,binding,version):AgentInterface; normalizeHeaders(Map<String,String>,version):Map<String,String> checks every case-insensitive override, collapses identical values and emits one canonical header. Reject conflicts before create/network, including context/interceptors and shared-channel metadata conflicts. Telemetry companion reports delegate version and immutable effective capabilities while same existing call paths preserve tracing/propagation. Tests inspect emitted version across unary/GET/DELETE/SSE/gRPC and constructor migration/error paths.

### Task 3 — explicit server provisioning and policy (server_phase1; A01/A02)

Files: server-common InterfaceRegistration/Resolver/ProtocolVersionPolicy, ServerCallContext, AgentCardRequestPolicy, A2AVersionValidator; reference/common VersionRouter; reference multiversion and single-version HTTP routes; gRPC handler/context/reference registration hook; existing REST/gRPC error mapping. Registration derives exact card descriptors and request validation from same binding/endpoint/tenant versions. Optional resolver is endpoint-scoped; legacy absence retains old entrypoints. New shared gRPC rejects absent->0.3 on lf service even if optional separate03 exists. Tests all seven subsets, missing/empty/whitespace and unsupported/stale descriptors, registration/card/scope consistency, old entrypoints and corrected1.1 versus old1.0 error mapping including predispatch. No0.3 transitive core dependency.

### Task 4 — public paired clients and legacy migration (paired_tests; A01/A02/A03)

Files: tests/server-common ordinary/auth/taskauth factories and AgentCardProducer; tests/multiversion/{jsonrpc,rest,grpc} tests/profiles and narrow other test config pins as required. Existing regressions explicitly1.0; old0.3 remain. Experimental registration profile advertises exact enabled versions from registration. On same running server per binding exercise public default1.1/explicit1.1/explicit1.0 ordinary Task/directMessage send/get/list/cancel/config/stream/subscribe/extended card where supported, assert ingress version. Test disabled version via stale card independently from local selection. Preserve channel ownership and auth behavior. Record genuine subset coverage and skips, not assumed conformance.

### Task 5 — compatibility, review and handoff (root; A03/A04/A14)

Candidate historical fixture comparison only if touched serialization/error paths justify it, with existing frozen expectations/read-only original provenance; add compile/load/wire legacy provider using frozen constant and implicit-constructor delegate rejection/adapter examples. Obtain independent review of working diff against plan/Phase1 exact requirements. Fix findings within retained attempt bounds and verify amended paths. Record API/default migration and exact commands/report counts/skips, incomplete rows/gates and next-session handoff. Present diff scope and Conventional Commit proposal `feat(a2a-1.1): add explicit interface and client version policy` with `This fixes #1303`; ask explicit approval before commit. Do not advance Phase2.

## Commands

Root coordinates one reactor at a time, using /tmp/a2a-phase1-run/bounded.py for captured logs/metadata.

1. `mvn -B -ntp -pl client/base -am -Dtest=ClientBuilderVersionTest -Dsurefire.failIfNoSpecifiedTests=false test` (RED then focused GREEN).
2. `mvn -B -ntp -pl client/base,client/transport/jsonrpc,client/transport/rest,client/transport/grpc,extras/opentelemetry/client,extras/opentelemetry/client-propagation -am test`.
3. `mvn -B -ntp -pl reference/multiversion-jsonrpc,reference/multiversion-rest,reference/grpc -am test`.
4. `mvn -B -ntp -pl tests/multiversion/jsonrpc,tests/multiversion/rest,tests/multiversion/grpc -am verify` (actual Surefire discovery; no tests aggregator exists).
5. Appropriate source/binary/historical checks, whitespace/dependency review; broader final changed-code `mvn clean install` only after focused checks pass within remaining budget. No unchanged baseline rerun.

## Preflight consistency

Task1 produces SPI signatures Task2 consumes; coordinated before editing. Task3 registration produces descriptors/resolver Task4 consumes; server agent sends exact API before integration edits. Tasks1/2 have disjoint production ownership; Task3/4 disjoint server versus shared integration sources. Root owns progress/evidence; each agent has separate report. Tests request only ordinary implemented phase1 capabilities, never later-phase claims. User no-commit and3attempt/time limits override generic skill commit/five-round guidance.


## Fresh-review correction plan — 2026-11-10 15:43Z

The user requested a fresh independent reviewer before further implementation,
then explicitly clarified that **0.3 has no tenants**. Fresh review report
`/tmp/a2a-phase1-run/fresh-review.md` requires two P1 fixes and a P2 coverage
correction. Source and actual failed root output confirm the findings. The
receiving-code-review skill is applied; no functional edit was made while the
requested read-only review was in progress.

1. Root: pin existing `examples/stream-lifecycle/client/.../StreamLifecycleClient`
and `examples/cloud-deployment/server/src/test/.../A2ACloudExampleClient` factories
to explicit1.0. Preserve oldservercards1.0 and newSDKdefault1.1; nofallback. Existing
root StreamLifecycleTest3/3fail plus redirectedNoCompatibleTransport are RED.
Run its existing three substantive binding tests after the fix; compile cloudhelper.
2. Server agent: newregistration rejects03 with nonnullnormalizedtenant. Correct
newPhase1unitfixtures to defaultscope for03, retain1.xtenant tests, add rejecting
case beforeproductionedit. Native03REST `/v1/...` resolves defaultregistration;
1.x `/v1/...` remains a tenantv1 scope. Preserve legacy noresolver behavior and03
originalversion context. Do not route03through1.xtenantaliases.
3. Paired-test agent: add actualoldpublic03Get/send on registeredexperimentalservers
for allbindings alongside shared10/11. Reproduce ordinaryRESTprefix defect with
divergentdefault/tenantv1 settings before routingedit. Replace invalid03tenantmask
coverage with seven realdefault-endpoint HTTP profiles perbinding, retaining the
existinggrpcdefault7profiles. Keep1.xtenant subset tests to10/11 only, filter03
from testtenant registrations and avoidemptyregisteredsets. Frozenoldfixtures and
existing separate03behavioral sources stay unchanged. Correct earlier evidence
claims explicitly ratherthan treating rawtenant03aliases as acceptedcoverage.
4. Root: capture coordinated boundedREDs, focusedGREENs then one finalchangedroot
cleaninstall. Independent reviewer rechecks corrections. No other behavior,
protocolguarantee, API/SPI break, Phase2, commit/push/PR is authorized.

Counter linkage: historicalcardgate had2failedmaterialcycles then thirdfocusedpass.
That didnotproveordinary03prefixrouting. Ordinarydispatcher methods were never
changed bycardfixes; retain their failedinitialimplementation and the sharedprefix
rootcause explicitly without resetting/relabeling broadacceptance. Stop after3
failedfix/verifycycles for any stablebehavior. Current5/10completed, iteration6
active, no-progress0; deadline16:57:25Z unchanged.


## Authorized correction run — 2026-11-10T17:33:12Z

User answered **Yes** to an additional60minutes and **four additional focused
iterations**, explicitly retaining historical counters/safeguards and separate
commit approval. Correctiondeadline **18:33:12Z**. OriginalPhase1 iterations8
remain completed; correctionrun0/4, cumulative8completed, iteration9active
(A01/A02/A04 boundary reproductions). No-progress0, budgetexhaustedfalse.
No commit/push/PR/Phase2. Phase0history8/A03audit10/breachtrue unchanged.

Focused plan before production edits:
1. Reproduce allthree findings with independent boundary evidence. RESTGET-only
peer plus rawsingle/multiversionGET and legacyPOST; suppliedstandardgrpcchannel
conflicting/identicalmetadata unary+streaming; native03downstreamcontext plus
conflictingfactoryzeroexecution. Root coordinates one boundedMaven at a time;
agents own disjoint files and run no Maven. Exactcommands/results retained in
/tmp/a2a-phase1-run with unique correction-* labels.
2. Implement focused corrections only after each validRED. Investigate a client
gRPC pretransport guard using supported lifecycle hooks; no silent opaqueChannel
exception or contract narrowing. Validate realmergedmetadata and normalizeequal
duplicates. Native03registration reachescore and customfactories cannot replace
originalversion. REST1.1GET preserves demonstrated1.0POST/native03.
3. Run focused regressions and appropriate changed-code reactor withinremaining
budget; allcommands <=min1200seconds,remainingbudget,controlledcleanup30seconds.
Count every materialfailedcycle againststableobservables, including currentreview
reproductions; preserve priorhigh-watercounts. Stopdependentwork at3failures.
4. Independentreview then refreshproposal/diff/evidence/handoff. No claimcomplete
untilcorrectedcode+tests+reviewpass. Retainoldsuccessfulbuild as pre-correction
evidence ratherthan proofnewfixes.
