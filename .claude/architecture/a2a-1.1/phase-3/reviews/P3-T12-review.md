# P3-T12 review ledger (fresh Bob contexts only; none is a "Sol" or independent-model review)

Base `44c276a17`. All reviewers were read-only fresh-context Bob subagents. Same-context self-review is not claimed as an independent gate.

## Candidate history

| Candidate | Files | Hash list sha256 (prefix) | Patch sha256 (prefix) | Reviewed by |
| --- | --- | --- | --- | --- |
| v1 (11:36 UTC) | 27 | `1b0af119e063...` | `2e02713c3d1597...` | A (Incomplete evidence), B (empty header only) |
| v2 (14:38 UTC) | 28 | `718061416c33...` | `ea9dceacab50ed...` | A2 (stopped mid-answer), B2 (Incomplete evidence) |
| v3 (15:37 UTC, correction of B2 F1/F2) | 28 | `cc96495b4fdb10aa...` | `e1cedc3dea850a9d...` | C (Incomplete evidence) |
| v4 (16:19 UTC, docs + one test over v3) | 28 | `f84a051ad51b59342a7b92ad` | `e4fde6850e6e400dad7fbd88` | D (Incomplete evidence, docs audit part) |
| v5 (16:25 UTC; R1-F3 REST card catch + test, A2-3 scan skip, docs) | 29 | `22f441491366...` | `e3500d041d9c...` | E (Changes required, docs), F (Incomplete evidence, wiring), G (Changes required small, logic) |
| v6 (16:47 UTC; correction of E/F/G) | 29 | `ca8bdb0a6534cd49...` | `a52a870b2796633d...` | H (header only, not evidence), H2 (Accepted within scope) |

## Dispositions

- **Review A (v1)**: Incomplete evidence, no blockers. `reviews/P3-T12-review-A.md`.
- **Review B (v1)**: returned only a header. `reviews/P3-T12-review-B.md` is not evidence.
- **Review A2 (v2)**: Incomplete evidence. Answered Q1 (validation matrix: no wrongly accepted or rejected combination) and began Q2, then stopped without a disposition, ledger or "not examined" list. `reviews/P3-T12-review-A2.md`. Its Q2 conclusion (a shared atomic store can reach the direct push path at `MainEventBusProcessor` ~436 only for events with no task mutation, i.e. `Message`/error with no changed batch) is the reviewer's reading and matches the implementer's reading of `updateTaskStore`; it was not proven by a test. Q3–Q7 unanswered.
- **Review B2 (v2)**: Incomplete evidence. Answered Q1–Q4, Q6, Q7 from source; Q5 (docs audit) and test bodies not examined. `reviews/P3-T12-review-B2.md`.

- **Review C (v3, correction)**: Incomplete evidence. Confirmed F1 closed (`QuarkusGrpcHandler.java:152-158`), F2 partly closed; raised R1-F2 (tenant card via `AgentCardRouter` with no registration still ungated: now documented, not gated), R1-F3 (REST card routes: gate exception unlogged), R1-F4 (no test dropped binding/tenant check in the card gate: test added in v4), R1-F5 (no gRPC no-resolver test). Docs audit not done. `reviews/P3-T12-review-C.md`.
- **Review D (v4, docs + API)**: Incomplete evidence (ran out of output before a disposition). Verified docs claims (a), (b), (d), (f), (g), (h) Supported; F1/F2 table rows overstated/incomplete and F3 (per-object cache) fixed in v4 docs; (c), (e), (i) not examined. Public API: no removed or changed signature, every new interface method is `default`; L1 low: retained overloads `attachRegistration(..., inboundVersion)` and `validateDefaultRegistration(resolvers, binding)` skip the gate (unused by shipped routes). `reviews/P3-T12-review-D.md`.

## Findings and their state in v3/v4

| ID | Sev (reviewer) | Finding | State |
| --- | --- | --- | --- |
| A7-1 (A) | major, test gap | no live shared-JPA composition test | Closed in v2: `JpaTaskMigrationTest.liveSharedJpaCompositionPassesOnlyWithTheSharedGateDispatcher` (H2, real store/gate/processor), `ReplicatedQueueManagerTest.liveReplicatedSharedProfileIsValidatedThroughTheTopologyGate`, three server-common live tests. H2 only; no PostgreSQL. |
| A5-4 (A) | minor | no test pins `ReactiveMessagingReplicationStrategy.supportsCommittedReplication()` | Closed in v2 (`testDeclaresCommittedReplicationSupport`). |
| A1-1, A1-3, A3-3 (A) | minor | declared-topology reliance; gate/store datasource coupling; startup vs migration order | Documented in v2 ("Limits of the check", `TaskAuthorityConfiguration` Javadoc). Not enforceable. |
| A2-3 (A) | minor | failed check re-runs the JPA scan on every 1.1 request | Open, documented only. A safe small change (skip `unmigratedIds` when node epoch != storage epoch) was not made. |
| A6-4 (A, A2) | major if reachable | direct push path at `MainEventBusProcessor` ~436 | Retained note. Reading: for atomic stores `committedBatch` is null only for no-mutation/unchanged/bootstrap results; not a new T12 behaviour and T10/T11 behaviour is unchanged. Not proven by a test. |
| F1 (B2) | major | gRPC: a request selecting 1.1 with no resolver/registration was not gated (REST/JSON-RPC were) | Fixed in v3: `QuarkusGrpcHandler.getInterfaceRegistration` calls `VersionRouter.requireTaskAuthorityForVersion(requestedVersion(), taskAuthority)` when no resolver exists; `VersionRouter.attachRegistration` shares the same method. Unit-tested in `TaskAuthorityConfigurationTest.requestForVersion11WithoutRegistrationIsGatedAndOtherVersionsAreNot` (the gRPC wiring itself has no test). |
| F2 (B2) | major | public and tenant agent-card endpoints never checked per request | Fixed in v3 for single-version REST and JSON-RPC: `VersionRouter.requireTaskAuthorityForCard` is called by `getAgentCard`/`getTenantAgentCard`. Unit-tested (`agentCardExposureIsGatedOnlyForRegistrationsThatInclude11`); the route wiring itself has no test. The multiversion `/v1/card` route was already gated. gRPC has no card endpoint here. |
| F3 (B2) | minor | per-request failure surfaces as a generic 500 | Unverified whether the violations are logged on every route; JSON-RPC card routes log and return 500. Open. |
| F4 (B2) | minor, unverified | `instanceof AtomicTaskStore` on a normal-scope proxy of an interface-typed producer | Open. Would give a false rejection (fail closed), never a false pass. No `@QuarkusTest`. |
| F5, F6 (B2) | note | compat-0.3 gRPC interceptor has no own check; no `@QuarkusTest` for startup observers/producer | Open notes. |
| N1 (A) | minor, cosmetic | mis-indented `requireAvailableVersions` in two multiversion routes | Fixed in v2. |

- **Review E (v5, docs claims)**: Changes required (docs only). Q1 holds for the routes checked; E-Q2 wording on the scan skip (also skipped with no active epoch or null node epoch); E-Q3 the "logged on REST/JSON-RPC" claim was false for request routes (`createCallContext` runs outside the logging try, a bare 500 results); Q4 no fencing/cluster overclaim; Q5 table rows match `violations()`. `reviews/P3-T12-review-E.md`.
- **Review F (v5, wiring)**: Incomplete evidence (wrote all answers at once). Fail-closed on all five routes from reading; no version narrowing or fallback; legacy-only registrations never evaluated; every new interface method `default`. F-1/F-2/F-6 unverified (ArC proxy `instanceof`, event-loop card read, card with no resolver). `reviews/P3-T12-review-F.md`.
- **Review G (v5, logic)**: Changes required (small). No false pass; matrix as expected; caching is volatile and re-evaluated after failure; G-1 misleading second violation; G-3 over-claiming message; G-4 pass never invalidated; G-6/A6-4 note incomplete. `reviews/P3-T12-review-G.md`.
- **Review H (v6)**: returned only a header (not evidence). **Review H2 (v6, correction review, narrow Q1-Q4)**: **Accepted within scope**. Verified C1 (logging funnel, same rethrow, slf4j on classpath), C3 (no test expects both violations), C7 (test fails without the catch; the catch only wraps the gate call), C2/C5 docs accurate, and that T12 does not touch `MainEventBusProcessor` lines 380-470. `reviews/P3-T12-review-H2.md`. H2 was a single fresh context with a narrow scope; E, F, G findings it did not re-examine are listed as retained below.

## Findings and their final state (v6)

| ID | State |
| --- | --- |
| A7-1, A5-4, N1 | Closed in v2 (tests and indentation). |
| B2 F1 (gRPC no-resolver 1.1) | Fixed v3 (`QuarkusGrpcHandler`, `requireTaskAuthorityForVersion`); gate logic unit-tested, gRPC wiring has no route test. |
| B2 F2 (card gate), R1-F4 | Fixed v3/v4 with unit tests of `requireTaskAuthorityForCard`. |
| R1-F3 (REST card exception unlogged / uncaught) | Closed v5/v6: `cardTaskAuthorityAllows` returns 500; `VersionRouter` logs violations at ERROR for every route; test `agentCardRoutesRefuseToServeWhenTheTaskAuthorityTopologyIsRejected`. |
| E-Q3 / F-5 (violations not logged on request routes) | Closed v6: logging moved into `VersionRouter.requireTaskAuthority(Instance)`, so every route logs; docs say "by the gate itself on every route". The unsatisfied-bean exception is not logged (message is in the exception; routes surface a 500). |
| A2-3 (JPA scan per failed request) | Closed v5: `JpaTaskMigration.authorityCapabilities` scans only when the node epoch equals the active epoch; docs updated. The epoch read itself still repeats per failing request. |
| E-Q2 wording | Closed v6 ("runs only while this node's epoch equals the active one"). |
| G-1 | Closed v6 (`else if`: no baselining violation while epochs mismatch). |
| G-3 | Closed v6 (message says "this node's bus and finalization senders"). |
| A6-4 / G-6 | Retained, unchanged T10/T11 behaviour. For atomic stores `committedBatch` is null for unchanged mutations, a changed mutation with no events (`TaskManager.planLegacyInitialDelta`, bootstrap of a task opened by a status update with no opening message), and a direct Message; those pushes use the node's push sender. Docs now state it. No processor test was added; it is not T12 behaviour (T12 does not touch `MainEventBusProcessor` lines 380-470). A shared-store deployment relying on ordered webhooks for that bootstrap case needs T11 follow-up. |
| F-2 (card read on the I/O thread) | Documented (keep the startup check). Not tested under Vert.x. |
| F-1 / B2 F4 (`instanceof AtomicTaskStore` on a possible proxy) | Retained. Would be a false reject (fail closed), never a false pass; no `@QuarkusTest`. |
| F-6 (card advertising 1.1 with no resolver) and tenant card via `AgentCardRouter` with no registration | Retained, documented. |
| F-4 / L1 (two retained overloads skip the gate) | Retained; unused by shipped routes. |
| F-7 (generic 500 to the client) | Retained. |
| G-2 (scan checks lane row existence, not lane epoch), G-4 (a pass is never invalidated) | Retained; G-4 is already stated in the docs ("does not detect a node that later loses its epoch"). |
| E low: 0.3 request on a non-`v1` multiversion REST tenant path gets `VersionNotSupportedError` before the gate | Retained; still rejected. |

## Gate status

Mandatory independent gate: reviews E, F, G, H2 are fresh Bob contexts (never Sol). H2 returned "Accepted within scope" for the v6 delta over E/F/G; the findings E/F/G left open are listed above as retained with their reasons. The earlier reviews A, A2, B2, C, D were Incomplete evidence. No review examined a `@QuarkusTest` because none exists.

## Evidence

See `handoffs/P3-T12.md`.
