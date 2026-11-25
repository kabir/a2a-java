# P3-T12 Review B2 (fresh-context, read-only)

Start time (UTC): Wed Nov 25 14:45:18 UTC 2026

Scope: production wiring and docs claims. The review ran out of turns. Q1-Q4, Q6 and Q7 were answered from source I opened. Q5 (docs audit) was NOT performed, and I did not open the test bodies, so test claims below rest on grep line hits only.

Paths below are relative to the repo root. Abbreviations: VR = reference/common/.../quarkus/VersionRouter.java. TAC = server-common/.../tasks/TaskAuthorityConfiguration.java. REST = reference/rest/.../A2AServerRoutes.java. JRPC = reference/jsonrpc/.../A2AServerRoutes.java. GRPC = reference/grpc/.../QuarkusGrpcHandler.java. MVR = reference/multiversion-rest/.../MultiVersionRestRoutes.java. MVJ = reference/multiversion-jsonrpc/.../MultiVersionJSONRPCRoutes.java.

## Q1. Where the check runs relative to serving/advertising

Single-version REST (REST):
- `createCallContext` (REST:879-889) calls `VersionRouter.attachRegistration(..., taskAuthority, ...)` at REST:881.
- Every request handler I opened calls `createCallContext` before the `RestHandler` call (sendMessage REST:331 then 334; sendMessageStreaming REST:368 then 372). The other handlers in the grep (listTasks, getTask, cancelTask, subscribeToTask, the push-config routes, getExtendedAgentCard REST:825) use the same `createCallContext` pattern. I did not read their bodies.
- In the VR gate, a request with a registration is checked at VR:129. A request that selects 1.1 with no registration is checked at VR:131-132. The no-registration branch also applies when the resolver is unsatisfied, because `registration()` returns null at VR:95-96.
- Result: the dynamic-tenant and no-registration cases are gated for REST and for JSON-RPC (JRPC:688-691).

JSON-RPC (JRPC):
- `invokeJSONRPCHandler` calls `createCallContext` at JRPC:385, before parse and dispatch. The gate is VR:121-134.
- Both `POST /` and the regex `/{tenant}` route reach it (JRPC:228, 247).

Multiversion REST (MVR):
- `selectRegistration` (MVR:274-295) calls `requireTaskAuthority` at MVR:288 before `select(version)` at MVR:291. Dispatch to `v10Handler` happens afterwards (MVR:211-212, 252-253), so a rejection precedes any 1.1 handling.
- The `/v1/card` route is gated at MVR:153.
- For 1.1 with no registration, `registration != null` is false (MVR:211), so the request falls to "unsupported version" (MVR:216) and is not served.

Multiversion JSON-RPC (MVJ):
- The gate is at MVJ:66, before `select` and before `invokeJSONRPCHandler`.
- 1.1 with no registration is not dispatched (the `registration != null` guard at the 1.1 branch).

gRPC (GRPC):
- `getInterfaceRegistration` (GRPC:151-163) is the only gate. The base `GrpcHandler.createCallContext` calls it for both overloads (GrpcHandler:875, 893), so every RPC is gated when a registration exists. That includes streaming, subscribe and cancel (GrpcHandler:320, 458, 499).
- Gap: when the resolver is null or unsatisfied, GRPC:153-155 returns null and no check runs. A request selecting 1.1 with no registration is therefore NOT gated on gRPC. REST and JSON-RPC do gate this case (VR:131).
- The legacy path in `A2AVersionValidator` (A2AVersionValidator:56-69) accepts 1.1 if the card advertises a 1.1 interface. I did not confirm end to end that gRPC then serves the request.

Agent-card advertisement:
- REST `getAgentCard` (REST:790-792) and `getTenantAgentCard` (REST:806-808) never call `createCallContext`. JRPC `getAgentCard` (JRPC:483) and `getTenantAgentCard` (JRPC:501-507) do not either.
- The only advertisement check is the startup `validateDefaultRegistration` for the default registration (REST:172, JRPC:224, GRPC:147).
- A tenant card served through `AgentCardRouter` is not tied to any registration check, so no authority check covers it. Requests to that tenant would still be rejected later.

Check-failure surfacing:
- The `IllegalStateException` from `createCallContext` propagates out of the handler. JRPC:385 is outside the `try` that starts at JRPC:389. It is caught only by the outer route `catch (Exception)` (JRPC:241) and returned as a generic 500 via `handleGenericError`. The multiversion routes behave the same (MVR:227, MVJ).
- The client gets a generic error and the reason is not in the response body. I did not verify that `handleGenericError` or any other code logs the violations (see F3).

## Q2. Null vs unsatisfied; CDI ambiguity; producer

- Semantics (VR:178-187): a null `Instance` is skipped. An unsatisfied `Instance` throws `IllegalStateException` (fail closed). An ambiguous `Instance` has `isUnsatisfied() == false`, so `authority.get()` (VR:186) throws `AmbiguousResolutionException`. That is also a failure, not a silent skip.
- Null can occur only in manually constructed routes with no CDI. The REST, JSON-RPC and multiversion `@Inject Instance` fields are always non-null under CDI. The GRPC field is `@Nullable` (GRPC:100-101), but Arc injects it. This is consistent and safe for CDI deployments.
- A `@DefaultBean` override is by design. `TaskAuthorityConfiguration` is a final class with a private constructor, but `of(Topology)` (TAC:52-55) lets an app supply a fixed always-passing topology. That is an explicit app choice, not a silent skip.
- Producer scope (DefaultProducers:36-42, `@Singleton`): it captures the injected `TaskStore`, `QueueManager`, and the method reference `mainEventBusProcessor::webhookDispatchTopology`.
  - If a store bean is `@ApplicationScoped` class-based, the Arc client proxy subclasses the bean class, so `instanceof AtomicTaskStore` is true. A normal-scope proxy also delegates on each call, so it cannot go stale.
  - Risk I could not confirm: if an app declares the store via a producer method whose return type is the interface `TaskStore` with a normal scope, the proxy is generated for the declared type. It would then NOT implement `AtomicTaskStore`. The result is `atomicStore=false` and a false rejection at TAC:87-89, which fails closed, not open. `storeName` would also show the proxy class (TAC:72).
  - I did not inspect how the default store beans are declared (the `InMemoryTaskStore` bean definition, `JpaDatabaseTaskStore`), so this is unverified. See F4.
- Unit tests build the producer with plain objects (`new DefaultProducers().createTaskAuthorityConfiguration(store, queues, processor)`, reference-common TaskAuthorityConfigurationTest:191-197). They cannot reveal a proxy `instanceof` problem.
- Missing @QuarkusTest: no test covers the startup observer with a failing topology, real CDI injection, or the real producer chain. That matters moderately. The risks it would catch are the proxy `instanceof` result, the observer order and failure mode (REST/JSON-RPC `@Observes Router` vs gRPC `StartupEvent`), and an unsatisfied dependency of the new `@DefaultBean` at build time. Unit tests with `FixedInstance` cannot catch these.

## Q3. Legacy-only behaviour; {0.3,1.0,1.1} rejection

- Legacy-only: `requireTaskAuthority(registration, authority)` only acts when `registration.versions().contains("1.1")` (VR:173). A registration of {0.3,1.0} never reaches `authority.get()` or `requireVersion11`, so a custom legacy `TaskStore` is untouched.
- The no-registration branch gates only when the requested version is 1.1 (VR:131).
- The producer is lazy (DefaultProducers:36-42, javadoc 31-35), so a legacy store is never evaluated unless 1.1 is exercised.
- Tests (grep hits only; I did not read the bodies): reference-common TaskAuthorityConfigurationTest:120-132 uses `LEGACY_STORE` with `attachRegistration(context("1.0"), ...)` expecting no throw and `context("1.1")` expecting a throw. Lines 140-157 loop over versions and use `FixedInstance.empty()` and null authority. Lines 78-83 do `validateDefaultRegistration` and `attachRegistration` with a non-throwing path. Line 165-171 covers unsatisfied and null authority.
- {0.3,1.0,1.1} with a failing check (test lines 105-114): the requests for 1.1 and 1.0 both throw `IllegalStateException`, and `getInterfaceRegistration()` stays null. The code rejects the whole registration.
- The multiversion routes call `requireTaskAuthority` before `select(version)` (MVR:288 vs 291; MVJ:66 vs the `select` line), so a 0.3 or 1.0 request on a rejected {0.3,1.0,1.1} registration is rejected too.
- Docs promise vs code: I did not read the docs wording (Q5), so I cannot say whether "rejected as a whole" matches. The code's behaviour is precisely: any registration containing 1.1 that fails the topology check rejects all its versions on that endpoint and tenant. Registrations without 1.1 are never affected.

## Q4. Startup vs per-request

- REST validates at `setupRouter` (REST:172, `@Observes @Priority(10) Router`). JSON-RPC validates at `setupRoutes` (JRPC:224). gRPC validates at `StartupEvent` (GRPC:145-148).
- Multiversion REST (MVR:57) and multiversion JSON-RPC (MVJ `setupRoutes`) do not validate at startup. They are per-request only. The single-version `A2AServerRoutes` beans are injected there (`v10Routes`, MVR:40), so their router observers run in the same app and cover the default registration. I did not verify that the observers fire for the default registration in that deployment.
- Per-request gating still applies to the default tenant. A pass is cached (TAC:163-164, 171). A failure is re-evaluated on each call (TAC:166-170).
- The `@PermitAll` annotation on the gRPC observer (GRPC:145) is irrelevant to an observer method.
- Not verified: the ordering of the gRPC `StartupEvent` against the gRPC server starting to listen, and the behaviour of a JPA store reading the database at startup when the DB is unavailable or late. Whether failing startup is the documented intent for the default registration requires the docs (Q5).
- Inconsistency: gRPC does not gate 1.1-without-registration (Q1 and F1), but REST and JSON-RPC do.

## Q5. Docs claims audit

NOT PERFORMED. I did not open docs/content/dev/extra/storage.md, replicated-queue-manager.md or server.md, so I make no statement on any claim. That includes the profile table, "reads the database each time the gate fails and once when it passes", "does not detect a node that later loses its epoch", the plain-Java instructions, the custom-store opt-in text, "Limits of the check", and the "not T13 proof" and "broker fencing is a precondition" wording.

What the code javadoc says that docs must match (TAC:16-30): configuration validation only; not multi-instance verification; does not fence old DB or broker writers; custom components that don't declare topology cannot be detected; a failure is re-evaluated every call; a pass is cached for the life of the object; a stale-epoch node is refused by the store itself.

## Q6. Public API compatibility

- The old VR overloads are preserved (VR:106-109, 111-115, 145-148) and delegate with a null authority. The new overloads are additive. A caller of an old overload (an app's own route, or the compat-0.3 style) gets no authority check. That is the "manual route" path. Arity is different so there is no overload ambiguity, but a null passed positionally compiles against `validateDefaultRegistration(resolvers, null, binding)` as intended.
- `DefaultProducers`: a `@DefaultBean` producing a new type (`TaskAuthorityConfiguration`) cannot clash with an app that has no such bean. Ambiguity would arise only if an app defines two non-default beans of that type.
- New injected fields are package-private `@Inject` (REST:157-158, JRPC:208-209, MVR:51-52, MVJ:40-41, GRPC:100-101).
  - A manually constructed route leaves the field null. `requireTaskAuthority` treats null as skip (VR:179-181), and `validateDefaultRegistration` returns on null resolvers (VR:153-155), so there is no NPE.
  - I did not open the tests/ or examples/ classes to see whether any construct these classes manually.
- compat-0.3: `A2AServerRoutes_v0_3` (REST and JSON-RPC variants) only consume `REGISTRATION_KEY` put by the multiversion gate (compat-0.3 REST A2AServerRoutes_v0_3:364-366), so they are gated upstream. The compat-0.3 gRPC `A2AExtensionsInterceptor_v0_3` (:37-46) resolves registrations itself and has no authority check. A rejected {0.3,1.0,1.1} gRPC default registration relies on `QuarkusGrpcHandler`'s startup check being present in the same app (F5).

## Q7. No silent narrowing

Enforced in the wiring I read.
- Every wiring path throws (`IllegalStateException`) and none returns a reduced registration (VR:129, 132, 162, 171-187; GRPC:161; MVR:153, 288; MVJ:66).
- The `validating(...)` resolver (TAC:178-187) returns the original registration or throws. It never rebuilds it with a smaller version set.
- Evidence from the unit test: after a throw the registration is not attached (test line 107, 114).
- Caveat: the gRPC no-registration path (F1) and the card endpoints (F2) are outside this guarantee because they never reach the gate.

---

## Disposition

Incomplete evidence

(Q5 docs audit not done; the test bodies, JPA/DB startup behaviour, and runtime behaviour were not examined. F1 and F2 should become "Changes required" if their discriminating checks confirm.)

## Findings ledger

- **F1** (major, unconfirmed end to end): `reference/grpc/.../QuarkusGrpcHandler.java:151-155` vs `reference/common/.../VersionRouter.java:131-132`. With no `InterfaceRegistrationResolver`, `getInterfaceRegistration` returns null and no authority check runs. REST and JSON-RPC check the same case. Scenario: a gRPC app with no resolver, a card advertising a 1.1 interface, and a topology that fails `requireVersion11`. A request with A2A-Version 1.1 is not checked. Violates "fail before serving 1.1" and consistency across bindings. Discriminating check: a gRPC test with no resolver bean, a failing store and a 1.1 request must fail; today GrpcHandler:887-889 returns null with no check on this path.
- **F2** (major): Agent-card advertisement is ungated per request: REST:790-792, 806-808; JRPC:483, 501-507. The only coverage is the startup check of the default registration (REST:172, JRPC:224, GRPC:147). Scenario: a tenant card from an `AgentCardRouter` advertising 1.1 is served with a failing topology while that tenant's requests are rejected. Violates "before 1.1 is served or advertised" for dynamic tenants. Discriminating check: request `/.well-known/{tenant}/agent-card.json` with a failing store and a tenant card containing a 1.1 interface; it should be refused, and today it returns 200.
- **F3** (minor): On per-request failure, the `IllegalStateException` carrying the violations becomes a generic 500 (JRPC:385 outside the try, JRPC:241; MVR:227-228). I did not verify that the reason is logged. Violated requirement: operator diagnosability. Check: a request for 1.1 with a failing topology must produce a log line containing the violations.
- **F4** (minor, unverified): `DefaultProducers.java:39-41` evaluates `taskStore instanceof AtomicTaskStore` on the injected object. A client proxy created for a producer-declared `TaskStore` interface type would not implement `AtomicTaskStore`, giving a false rejection and a proxy class name in the error. Check: a `@QuarkusTest` with a store provided through an `@ApplicationScoped` producer method returning `TaskStore`, asserting the gate passes for a valid atomic store.
- **F5** (note): compat-0.3 gRPC `A2AExtensionsInterceptor_v0_3:37-46` has no authority check. A rejected {0.3,1.0,1.1} registration is therefore blocked only via the `QuarkusGrpcHandler` startup observer in the same app. Check: a deployment with only the compat-0.3 gRPC module and a 1.1 registration.
- **F6** (note): There is no `@QuarkusTest` for the startup observers or the real producer wiring (see Q2). A passing reactor run (4191 tests) cannot show that the startup failures happen.

## Not examined

- All three docs sections (Q5), and every docs claim.
- The bodies of the reference-common `TaskAuthorityConfigurationTest` and `VersionRouterTest` (only grep hits seen), plus the server-common `TaskAuthorityConfigurationTest`.
- `DefaultRequestHandler.taskAuthorityConfiguration()`, `MainEventBusProcessor.webhookDispatchTopology`, and `JpaDatabaseTaskStore` or the replicated queue manager behaviour.
- `handleGenericError` logging, the REST/gRPC/JSON-RPC handler bodies beyond the lines cited, and whether startup validation order precedes the servers listening.
- Whether any class under `tests/`, `examples/` or `compat-0.3/` constructs the routes or handlers manually.
- The test logs `/private/tmp/t12-v2-*.log`.
