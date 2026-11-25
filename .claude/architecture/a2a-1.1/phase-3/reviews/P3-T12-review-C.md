# P3-T12 Review C (fresh-context correction review, read-only)

- Task: P3-T12 "Authority topology/provisioning gate"
- Repo: /Users/kabir/.codex-api/worktrees/3053/a2a-java (branch support/a2a-1.1.0, base 44c276a17)
- Candidate: v3 (uncommitted, frozen). Delta reviewed: VersionRouter.requireTaskAuthorityForVersion / requireTaskAuthorityForCard, QuarkusGrpcHandler.getInterfaceRegistration + requestedVersion, REST and JSON-RPC A2AServerRoutes card routes, TaskAuthorityConfigurationTest (2 new tests), storage.md, replicated-queue-manager.md, server.md, TaskAuthorityConfiguration Javadoc.
- Start time (`date -u`): Wed Nov 25 15:38:22 UTC 2026
- Hard limit: 20 minutes (ends about 15:58:22 UTC)
- Constraints: read-only except this file; no Maven, no git writes, no test.log edits.

## Answers


NOTE: Answers for Q1-Q4 were written in one batch at the turn limit, not after each question as the process required. Q5 and Q6 were not completed.

### Q1. gRPC gate (F1)
- `QuarkusGrpcHandler.java:152-158`: when `resolvers == null || isUnsatisfied()`, it calls `requireTaskAuthorityForVersion(requestedVersion(), taskAuthority)` and returns null. This closes the no-registration 1.1 bypass for gRPC.
- Both `createCallContext` overloads reach `getInterfaceRegistration`: `GrpcHandler.java:866-867` (via `createRegisteredCallContext(..., tenant)` at line 893) and `GrpcHandler.java:875`. Every gRPC RPC method calls one of these inside its own `try` (for example `sendMessage` at `GrpcHandler.java:222-224`).
- Validity of reading the key there: the interceptor stores it on the call `Context` with `withValue` at `A2AExtensionsInterceptor.java:143-145`. `GrpcHandler.getVersionFromContext` (`GrpcHandler.java:1175-1182`) reads the same key and returns null on any exception. `requestedVersion()` does the same with `RuntimeException`. Both are read on the same call thread after the interceptor, so the read is valid.
- Null, blank, "0.3" and "1.0": `ServerVersionPolicy.resolveVersion` (`ServerVersionPolicy.java:26-28`) maps null or blank to "0.3". `requireTaskAuthorityForVersion` (`VersionRouter.java:184`) only acts on "1.1". So none of these throw. A null authority returns early (`VersionRouter.java:208-209`).
- Error mapping: the throw is an `IllegalStateException` inside the method's `try`. It reaches `catch (Throwable t)` and `handleInternalError` (`GrpcHandler.java:1042-1047`), which returns a generic `InternalError("Internal error")` and logs at SEVERE. The client does not see the reason. This matches the existing registered-path `requireTaskAuthority` throw (`QuarkusGrpcHandler.java:164`). It does not match the registered-path `VersionNotSupportedError` (line 161), which is a typed A2AError.
- Legacy store: no effect. The new branch runs only for 1.1.

### Q2. REST and JSON-RPC card routes (F2)
- REST: `requireTaskAuthorityForCard` is called before the card is read, at `reference/rest/.../A2AServerRoutes.java:791` (no tenant) and `:810` (tenant), with `jsonRestHandler.getAgentCard` after it.
- JSON-RPC: `reference/jsonrpc/.../A2AServerRoutes.java:484` and `:509`. In the tenant route, `Utils.validateTenant(tenant)` (line 508) runs before the gate, so an invalid tenant still gets its 400 first. REST `extractTenant` (`:713-725`) does no validation, so an invalid tenant reaches the resolver unvalidated. The same is true of `VersionRouter.registration`.
- No resolver, or a null registration: the card is served as before (`VersionRouter.java:197-198`, `:201-204`).
- Legacy-only registration: `requireTaskAuthority(registration, ...)` ignores it (`VersionRouter.java:173`).
- Exception surfacing, REST: the card handlers are registered directly with `handler(this::getAgentCard)` (`reference/rest/.../A2AServerRoutes.java:258-267`), not through `authenticated(...)`. The `IllegalStateException` therefore skips the `catch (Exception)` at line 283. It escapes to Vert.x's default failure handling. Nothing in these methods logs the violations.
- Exception surfacing, JSON-RPC: `reference/jsonrpc/.../A2AServerRoutes.java:279-282` and `:303-306` catch `Exception`, log at ERROR and return 500. The violations are logged.
- Resolver consistency: `requireTaskAuthorityForCard` (`VersionRouter.java:200-204`) resolves once and skips a mismatched binding or tenant. `registration()` (`:98-102`) throws `VersionNotSupportedError` for the same case. The card variant is deliberately silent (Javadoc, `:189-193`). The REST/JSON-RPC handler still resolves again when it builds the card, so the resolver runs twice per card request. I did not check for side effects. No NPE is possible, because `registration` is null-checked before use.
- Remaining gap: a tenant card served only by `AgentCardRouter`, with no registration, is still not gated. B2 F2 is only partly closed.

### Q3. Legacy and multiversion behaviour
- The delta only adds the no-registration check, which acts only when the resolved version is "1.1" (`VersionRouter.java:184`). Null, "", "0.3" and "1.0" are unchanged. `VersionRouter.java:121-133` is the attachRegistration wiring.
- The test at `TaskAuthorityConfigurationTest.java:189-192` asserts this for null, "", "0.3" and "1.0".
- I did not re-open the multiversion route files in this pass. B2 reports the v2 gates at MVR:288 and MVJ:66; those lines are not confirmed.

### Q4. Test adequacy
- `requestForVersion11...` (`:185-196`) asserts exception type only, not message. It does catch an always-pass mutation and a version-ignoring mutation.
- `agentCardExposure...` (`:199-217`) asserts the message "unmigrated and unfenced" (`:205`). That catches an authority-ignoring mutation. The legacy-only and no-resolver cases at `:209-216` catch an always-gate mutation.
- Not caught:
  - Dropping the `registration.binding() == binding` or tenant check in `requireTaskAuthorityForCard` (`VersionRouter.java:201-202`). The test resolver lambda ignores its arguments (`:65`), and no test passes a mismatched binding or tenant.
  - Passing `null` or the wrong tenant to `resolve` (`VersionRouter.java:200`).
  - Removing the gate from `QuarkusGrpcHandler.java:156`. No test covers the gRPC no-resolver path.
  - Removing the gate from the REST and JSON-RPC card routes (REST `:791,:810`, JRPC `:484,:509`). No route-level test is listed.

### Q5. Docs audit: NOT PERFORMED
I did not open storage.md, replicated-queue-manager.md or server.md in this pass, so none of the docs claims are verified. The wording "cluster acceptance (T13)" and "enforced broker fencing" cannot be assessed here.

### Q6. Compatibility and gates: PARTIAL
- `VersionRouter` adds new public static overloads (`:182`, `:194`) and a new `attachRegistration` overload (`:121`). I did not compare against the pre-delta signatures in `/private/tmp/t12-candidate.patch`, so "no existing signature changed" is not confirmed.
- No narrowing or fallback: every new path throws or returns. None rebuilds a registration.

## Disposition
Incomplete evidence. Q5 (docs) was not done and Q6 was only partly checked.

## Findings ledger
| ID | Severity | file:line | Failure scenario | Violated requirement | Discriminating check |
|---|---|---|---|---|---|
| R1-F1 | closed by delta (was major) | QuarkusGrpcHandler.java:152-158 | gRPC 1.1 request with no resolver is now gated | none | gRPC test with no resolver, failing authority, 1.1 header must fail. Not present |
| R1-F2 | major (partly open) | reference/rest/.../A2AServerRoutes.java:790-815; reference/jsonrpc/.../A2AServerRoutes.java:483-517 | Tenant card served by AgentCardRouter only (no registration) is still ungated | "before 1.1 is advertised" | Request /.well-known/{tenant}/agent-card.json with a failing store and a card listing 1.1 |
| R1-F3 | minor | reference/rest/.../A2AServerRoutes.java:258-267 | Card gate IllegalStateException escapes unlogged to the Vert.x default handler | operator diagnosability | Request card with failing store; check for a log line and status |
| R1-F4 | minor | VersionRouter.java:200-202; TaskAuthorityConfigurationTest.java:65,203 | Mutation dropping binding/tenant check in card gate is not caught | test adequacy | Pass a mismatched binding or tenant registration to requireTaskAuthorityForCard, expect no gate |
| R1-F5 | minor | QuarkusGrpcHandler.java:156 | Removing the new gRPC gate is not caught by any test | test adequacy | gRPC handler unit test with no resolver |
| R1-F6 | note | GrpcHandler.java:1046-1047 | gRPC gate failure surfaces as generic InternalError, not a typed 1.1 error | consistency with line 161 | Compare client status for registered vs unregistered failure |
| R1-F7 | major (open) | docs (not opened) | Docs claims unverified; possible overstated wording | docs accuracy | Docs audit (Q5) |

## Not examined
- Docs: storage.md, replicated-queue-manager.md, server.md (Q5 entirely).
- Pre-delta public signatures in /private/tmp/t12-candidate.patch (Q6).
- Multiversion route files MVR:288 and MVJ:66 for v3 (Q3 relies on B2).
- Whether Vert.x or any global failure handler logs the REST card exception.
- Whether the resolver double-call in the card path has side effects.
- Test bodies beyond the cited lines, and the v3 reactor log /private/tmp/t12-v3-full-clean.log (not read).
- The no-registration `TaskStore` proxy issue (B2 F4) and the startup observer ordering.
