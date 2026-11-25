# P3-T12 review E (fresh Bob context, docs claims)

Start: Wed Nov 25 16:26:07 UTC 2026

## Q1 - "whole registration rejected, so a 0.3/1.0 request fails too" (storage.md:235-237)
Verdict: TRUE for every route family that has a registration listing 1.1; two nuances, no counterexample found.
- VersionRouter.java:121-134, 171-176: `attachRegistration` -> `requireTaskAuthority(registration, authority)` fires on `registration.versions().contains("1.1")` and never looks at the inbound version, so a 0.3/1.0 request is gated. Single-version REST (rest A2AServerRoutes.java:898-901 via createCallContext) and JSON-RPC (jsonrpc A2AServerRoutes.java:694-695) use it.
- Multiversion REST: MultiVersionRestRoutes.java:285-292 `selectRegistration` calls `requireTaskAuthority` (288) BEFORE `registration.select(version)` (291), so the version does not matter; `/v1/card` also gates (153) before `select` (156). Multiversion JSON-RPC: MultiVersionJSONRPCRoutes.java:63-69, gate (66) before `select` (69).
- gRPC: QuarkusGrpcHandler.java:159-165 gates by registration only, no version check.
- Nuance A (Low, docs precision): MultiVersionRestRoutes.java:276-280 - with a resolver present, a 0.3 request on a non-`v1` tenant path throws VersionNotSupportedError BEFORE the gate, so the client sees "0.3 only provides historical paths" rather than the topology failure. Not a gate bypass (still rejected). Discriminating check: 0.3 request to /acme/message:send with a failing configuration; expect VersionNotSupported, not 500.
- Nuance B (Low): MultiVersionRestRoutes.java:201-203 / MultiVersionJSONRPCRoutes.java:58-61 `ctx.next()` when no resolver bean exists -> no registration, no gate; this is the legacy path and consistent with "registrations without 1.1 are unaffected".
- Unverified: compat-0.3 native routes (A2AServerRoutes_v0_3 REST/JSON-RPC, GrpcHandler_v0_3 which takes the registration from GrpcContextKeys_v0_3.INTERFACE_REGISTRATION_KEY, GrpcHandler_v0_3.java:395-413). I did not confirm that a pure-0.3 native route under a registration that lists 1.1 hits the gate. Severity: Medium for evidence gap only. Discriminating check: failing configuration plus a registration {0.3,1.0,1.1}, then call the 0.3 native gRPC and REST routes and expect rejection.

## Q2 - JPA read counts (storage.md:249-251)
Verdict: ESSENTIALLY EXACT; two imprecisions (Low).
- TaskAuthorityConfiguration.java:162-172: `validated` is cached only after a pass; a failure is re-evaluated on every call. `violations()` (76-81) calls `topology.get()` every time. The CDI wiring `of(TaskStore, QueueManager, Supplier)` (63-73) builds a new Topology per call, and that calls `atomic.authorityCapabilities()` each time, so a Supplier-based Topology IS re-evaluated on every `violations()` call. `of(Topology)` (52-55) is fixed and never re-read.
- JpaTaskMigration.java:90-97: one `activeEpoch()` (REQUIRES_NEW) per evaluation; `unmigratedIds("")` runs only when `stored != null && node != null && equal`. The docs say the scan is skipped "while this node's epoch differs"; it is also skipped when no epoch is activated (stored == null) or the node epoch is null. Low, wording is incomplete.
- DB reads happen on every evaluation even when the failure is unrelated to JPA (for example a webhook violation), because the supplier is always evaluated before the violations are computed. The docs "each time the gate fails" is consistent.
- "once when it passes": `validated` is a volatile flag with no lock (162-171), so concurrent first requests can each evaluate before the flag is set. Wording "once" is approximate (Low). Check: N concurrent first requests against a counting store; expect more than 1 call to authorityCapabilities().
- The `unmigratedIds("")` cost/limit was not read, so "full unmigrated-row scan" is not confirmed.

## Q3 - "violations are written to the server log (error level) on REST, JSON-RPC and gRPC routes" (storage.md:277-279)
Verdict: FALSE as a blanket claim for REST and JSON-RPC REQUEST routes. TRUE for REST/JSON-RPC card routes and gRPC.
- [Medium] REST request routes, rest A2AServerRoutes.java:327-341 (sendMessage), identical pattern in the other handlers (lines ~410-690): `createCallContext(...)` (331) runs OUTSIDE the `try`/`catch (Throwable)` that calls LOGGER.error (335-336). The IllegalStateException from `VersionRouter.attachRegistration` (900-901) propagates to `authenticated()`/`authenticatedStreaming()` (275-301), whose `catch (Exception e)` only calls `VertxSecurityHelper.handleGenericError(ctx)` (VertxSecurityHelper.java:265-271: 500 "Internal Server Error", no logging). `runInRequestContext[Deferred]` (VertxSecurityHelper.java:112-218) rethrows without logging. Failure scenario: operator with a rejected topology sees HTTP 500 and an empty server log; the violation list is lost. Violated requirement: the docs' own diagnosability claim. Discriminating check: register a failing TaskAuthorityConfiguration, POST /message:send with A2A-Version 1.1, capture ERROR logs for A2AServerRoutes, and assert a message containing the "A2A 1.1 cannot be exposed" text. I expect none today.
- [Medium] Multiversion REST: MultiVersionRestRoutes.java:169-171, 227-229, 268-270 `catch (Exception e) { handleGenericError }`, no log (selectRegistration at 285-293 throws inside). Same defect.
- [Medium] Single-version JSON-RPC: jsonrpc A2AServerRoutes.java `invokeJSONRPCHandler` calls `createCallContext(rc)` before its `try` (the LOGGER.error at 412 is inside the try). The ISE escapes to the route handlers (setupRoutes, ~lines 235-243 and 252-260) whose `catch (Exception e)` calls `handleGenericError`, no log. Multiversion JSON-RPC: MultiVersionJSONRPCRoutes.java:92-94, same. Exact line of createCallContext in invokeJSONRPCHandler not recorded; I read the body but did not number it.
- TRUE: REST card routes, rest A2AServerRoutes.java:798-808 (`cardTaskAuthorityAllows`: LOGGER.error with the exception, 500). JSON-RPC cards, jsonrpc A2AServerRoutes.java:268-281 and 289-306 (`LOGGER.error("Internal error while resolving agent card", e)` includes the ISE message; getAgentCard 484 and getTenantAgentCard 509 throw it). Note: the public JSON-RPC tenant card catches `IllegalArgumentException`/`TenantNotFoundException` before `Exception`; ISE is neither, so it is logged.
- TRUE: gRPC. QuarkusGrpcHandler.java:152-166 throws the ISE inside GrpcHandler's try (e.g. sendMessage GrpcHandler.java:224 within try 223-258), the `catch (Throwable)` calls `handleInternalError`, which logs at Level.SEVERE with the throwable (GrpcHandler.java:1042-1047). That is JUL SEVERE, which the docs call "error level" - acceptable. Streaming and extended-card paths are inside their try blocks too (lines 458, 499, 669). Startup failure for the default registration comes from the StartupEvent observer at QuarkusGrpcHandler.java:146-149.
- Required fix (either): wrap with logging at the ISE catch in authenticated()/authenticatedStreaming(), the multiversion dispatchers and the JSON-RPC route catch blocks, or narrow the docs to "card routes and gRPC".

## Q4 - claims implying fencing enforcement or cluster acceptance
No overclaim found; fencing is repeatedly disclaimed.
- Disclaimers: storage.md:251-255 ("configuration validation only", "not evidence ... cluster profile passed multi-instance verification", "does not fence old database writers or brokers"); TaskAuthorityConfiguration.java:19-21; server.md:245-247 ("not proof"); table row storage.md:243 "(not cluster acceptance)".
- Wording to tighten (Low):
  - server.md:243 "A2A 1.1 is served and advertised only when ..." is unconditional, but storage.md:275-278 admits that a card from an AgentCardRouter for a tenant with no registration is not checked. server.md should carry the caveat or link to it.
  - replicated-queue-manager.md:331-335 says a strategy without `supportsCommittedReplication()` is rejected "at configuration time". The gate runs at startup only for default registrations on single-version routes (REST setupRouter:172, JSON-RPC:224, gRPC:148). Multiversion routes and dynamic tenants are checked per request, so "at configuration time" is approximate. I did not confirm that the multiversion modules also trigger startup validation through the injected single-version route beans.
  - storage.md:271-272 "a node that checks before the migration completes refuses to start" holds only where startup validation applies (same limitation).
- replicated-queue-manager.md paragraph before "## Monitoring" (git diff lines 331-335) makes no broker-fencing claim; it links to the storage.md anchor. The anchor `#upgrading-to-a2a-11-broker-fencing` (storage.md:254) was not verified to exist.

## Q5 - storage.md table vs TaskAuthorityConfiguration.violations() (240-247)
All rows consistent; no row contradicts the code.
- Row 242 (in-memory, no replication, no shared gate): process-local checks at TaskAuthorityConfiguration.java:102-112 add violations only for replicated (104) or sharedGate (108). Accepted otherwise. Assumes InMemoryTaskStore declares processLocal (InMemoryTaskStore.java not read).
- Row 243 (JPA accepted): needs storageEpoch non-null (116-118), node epoch equal (121), migrationComplete (125), orderedDispatcher (143), sharedGate (147), quiescentSender (150), and, if replicated, writerEpoch present and equal (131-137) plus committedEnvelopes (138).
- Row 244 (replicated with in-memory store / no writer epoch / no committed envelopes / epoch mismatch): 104-107, 131-133, 138-141, 134-137.
- Row 245 (no activated epoch / different node epoch / unmigrated rows): 118-119, 121-124, 125-128. A null node epoch also fails via Objects.equals (121).
- Row 246 (no ordered dispatcher / process-local gate / non-quiescent sender): 143-153.
- Row 247 (no AtomicTaskStore / undeclared authority): 87-93.
- Not in the table (Low): a topology supplier that throws is itself a violation (line 80-82), and a process-local store with a shared webhook gate (108-111) is only implied by row 242's "no shared webhook gate".
- Not verified: the property name `a2a.task-store.writer-epoch` against JpaTaskMigration.NODE_EPOCH_PROPERTY (not read).

## Disposition
**Changes required.** One docs claim is false against the code: storage.md:277-279 says the violations are logged at error level on the REST, JSON-RPC and gRPC routes. In fact, REST and JSON-RPC request routes, single-version and multiversion, turn the gate's IllegalStateException into a bare 500 with no SDK log line (Q3). Either log in those catch blocks or narrow the docs to "card routes and gRPC". The Q1, Q2 and Q5 claims and the no-fencing disclaimers (Q4) hold, apart from the Low wording items listed there.

## Not examined
- compat-0.3 native routes (A2AServerRoutes_v0_3 REST and JSON-RPC, GrpcHandler_v0_3 interceptor) for the Q1 gate.
- Whether Quarkus, Vert.x or a CDI interceptor logs the exception elsewhere (only SDK code was read).
- The implementation and cost of JpaTaskMigration.unmigratedIds; NODE_EPOCH_PROPERTY name; InMemoryTaskStore.authorityCapabilities.
- Whether the multiversion modules trigger startup validation via the single-version route beans.
- The storage.md anchor `#upgrading-to-a2a-11-broker-fencing`.
- The tests (TaskAuthorityConfigurationTest, A2AServerRoutesTest, JpaTaskMigrationTest) and every changed file outside the docs-claims scope. I did not run Maven, per the instructions.

End: Wed Nov 25 16:35:12 UTC 2026
