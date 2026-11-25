# P3-T12 review F (fresh Bob context, wiring)

Start: Wed Nov 25 16:26:08 UTC 2026 (date -u)


Note: the turn limit forced a stop before every answer could be appended separately. Q1-Q6 are appended together below. Nothing was edited and Maven was not run. Findings marked "unverified" come from reading the code only and were not run.

## Q1. DefaultProducers: how the TaskAuthorityConfiguration bean is produced

- `DefaultProducers.createTaskAuthorityConfiguration(TaskStore, QueueManager, MainEventBusProcessor)` (DefaultProducers.java, new method after `createAgentExecutor`) is `@Produces @DefaultBean @Singleton`.
- It calls `TaskAuthorityConfiguration.of(taskStore, queueManager, mainEventBusProcessor::webhookDispatchTopology)`. This only null-checks and captures a `Supplier`. Nothing is evaluated at construction, so the topology is read lazily on each `violations()` call. `requireVersion11()` caches a pass in `validated`. A failure is not cached.
- `@Singleton` is a pseudo-scope, so the configuration bean itself is not proxied.
- The injected parameters are proxied. These beans are `@ApplicationScoped`:
  - `InMemoryTaskStore` (InMemoryTaskStore.java:92)
  - `JpaDatabaseTaskStore` (@ApplicationScoped @Alternative @Priority(50))
  - `InMemoryQueueManager`
  - `ReplicatedQueueManager`
  - `MainEventBusProcessor`
- `instanceof AtomicTaskStore` and `atomic.authorityCapabilities()` therefore run on an ArC client proxy.
  - For the class beans above, ArC generates a subclass of the bean class. The proxy is an `AtomicTaskStore` and delegates `authorityCapabilities()` to the contextual instance, because both `InMemoryTaskStore` and `JpaDatabaseTaskStore` override it. I expect no problem here, but I did not run it.
  - **Possible false reject (fail-closed), unverified.** A user supplies a `TaskStore` through a normal-scoped `@Produces` method whose declared return type is `TaskStore`, not `AtomicTaskStore`. The proxy then only has the bean types `TaskStore` and `Object`. `taskStore instanceof AtomicTaskStore` is false even though the real instance is atomic. The result is the violation "no AtomicTaskStore companion". 1.1 is rejected on a valid atomic store.
  - **False accept.** I found no mechanism that makes a non-atomic store pass. The remaining risk is a store that declares `PROCESS_LOCAL` or `SHARED_STORAGE` falsely. The class javadoc says this cannot be detected.
- There is no ArC or `@QuarkusTest` test of the real bean wiring. The tests in reference/common `TaskAuthorityConfigurationTest` use `FixedInstance` and fixed `Topology` records. I did not find a test that exercises the proxy.

## Q2. Fail-closed when the bean is unsatisfied or ambiguous

`VersionRouter.requireTaskAuthority(Instance)` (VersionRouter.java:207-216):
- `authority == null` returns without checking. This is the "manually constructed route" case.
- `authority.isUnsatisfied()` throws `IllegalStateException`.
- Otherwise `authority.get().requireVersion11()` runs. An ambiguous bean throws `AmbiguousResolutionException` from `get()`. That is a `RuntimeException`, so it is fail-closed.

Per transport:
- **Single-version REST.** `createCallContext` calls `attachRegistration(..., taskAuthority, ...)` (rest A2AServerRoutes.java:900). Request paths:
  - If the registration lists 1.1, `requireTaskAuthority` runs.
  - If the registration is null (resolvers unsatisfied), `requireTaskAuthorityForVersion(inboundVersion)` runs when the header is 1.1.
  - The exception propagates out of `createCallContext`. In `sendMessage` that call is outside the try block. The `authenticated()` wrapper catches `Exception` and returns a generic 500. It is fail-closed, but the error is untyped.
  - Cards go through `cardTaskAuthorityAllows`, which catches only `IllegalStateException` and returns 500. `AmbiguousResolutionException` escapes that catch, but Vert.x still returns a 500.
  - The startup observer is `validateDefaultRegistration` (rest A2AServerRoutes.java:172).
- **JSON-RPC.** `createCallContext` calls `attachRegistration` (jsonrpc A2AServerRoutes.java:694). The card methods (:484, :509) call `requireTaskAuthorityForCard`. The `post("/")` and tenant POST handlers catch `Exception` and call `handleGenericError`. The card handlers catch `Exception` and return a 500. Fail-closed.
- **gRPC.** `getInterfaceRegistration` (QuarkusGrpcHandler.java:153-165) behaves as follows:
  - With resolvers unsatisfied it calls `requireTaskAuthorityForVersion(requestedVersion(), taskAuthority)`.
  - With a registration it calls `requireTaskAuthority(registration, taskAuthority)`.
  - `GrpcHandler` runs it inside try/catch, ending in `catch (Throwable)` and `handleInternalError`.
  - `requestedVersion()` returns null, which means 0.3, when no interceptor populates `VERSION_HEADER_KEY`. That does not create a 1.1 path.
- **Multiversion REST.** `selectRegistration` (:288) and `/v1/card` (:153) gate before `registration.select(version)`. If the registration is null and the version is 1.1, dispatch does not go to the v10 handler. It falls through to `VersionNotSupportedError`. `registration != null && VERSION_1_1` is the guard. The v10 handlers also gate again through `createCallContext`.
- **Multiversion JSON-RPC.** The registration gate is at :66. The null-registration 1.1 path is blocked the same way as in REST. The v10 handler gates again.
- No path found serves or advertises 1.1 with the bean unsatisfied, except the card gap in F-6.

## Q3. Exact protocol selection unchanged

- The diff never changes the registration, the `versions()` set, or `select(version)`. A gate failure only throws.
- Nothing falls back to 0.3 or 1.0. The multiversion v10/v03 dispatch conditions are untouched.
- The gate runs before `select`, so a failure does not touch selection. Cards that fail are not served, rather than served without 1.1.
- Observation (F-3): the gate fires for any registration that lists 1.1, regardless of the version the request selects. With a registration {0.3,1.0,1.1} and a failing topology, 0.3 and 1.0 requests are also rejected. This is fail-closed and not narrowing, but legacy traffic on a mixed registration goes down.

## Q4. Legacy-only registration with a custom legacy TaskStore

- Per request: `requireTaskAuthority(registration, authority)` is a no-op unless `registration.versions().contains("1.1")` (VersionRouter.java:171-175). `requireTaskAuthorityForVersion` is a no-op unless `ServerVersionPolicy.resolveVersion(inboundVersion)` equals "1.1" (:182-186).
- Card: `requireTaskAuthorityForCard` also goes through the same 1.1 guard (:194-204).
- Startup: `validateDefaultRegistration` goes through the same guard.
- The producer does not run for legacy deployments. `Instance.get()` is never called, because a `@Singleton` is lazy and the constructor only captures a lambda.
- A non-atomic store would still not throw if the gate were evaluated. `taskStore instanceof AtomicTaskStore` is false, so a violation is recorded. The exception comes from `requireVersion11`, not from construction.
- Custom `QueueManager` implementations are unaffected: `replication()` is a default method.
- Not verified: `DefaultRequestHandler`'s constructor was not examined for any `AtomicTaskStore` requirement.

## Q5. Startup observers

- REST: `setupRouter` (rest :172). JSON-RPC: `setupRoutes` (jsonrpc :224). gRPC: `validateRegistrationAtStartup(@Observes StartupEvent)` (QuarkusGrpcHandler.java:146).
- `VersionRouter.validateDefaultRegistration(resolvers, authority, binding)` (VersionRouter.java:151-163) behaves as follows:
  - `resolvers == null || isUnsatisfied()` returns, so nothing is validated.
  - It resolves `(binding, null)`. If the result is non-null, it checks the binding and tenant, then `requireAvailableAdapters`, then `requireTaskAuthority(registration, authority)`. That call only fires when the registration lists 1.1.
  - So startup fails only for a default registration with 1.1, either from `requireVersion11` or from an unsatisfied authority bean. A default registration that is null or legacy-only does not fail on the task authority.
- The multiversion route classes (MultiVersionRestRoutes, MultiVersionJSONRPCRoutes) have no startup observer. They gate per request only. The inner single-version `A2AServerRoutes` observers may still fire, but I did not examine that.

## Q6. Public API compatibility

All additions are new. I found no removed or renamed pre-existing signature. The grep of `^-` in the main-source diffs for server-common, extras and reference/common showed none. The route-class call-site replacements are not API.

New or changed:
- `QueueManager.replication()` is `default`, returning `QueueReplication.NONE`. `ReplicatedQueueManager.replication()` overrides it.
- `AtomicTaskStore.authorityCapabilities()` is `default`, returning `UNDECLARED`. `InMemoryTaskStore` and `JpaDatabaseTaskStore` override it.
- `ReplicationStrategy.supportsCommittedReplication()` is `default false`. `ReactiveMessagingReplicationStrategy` overrides it. Behaviour note: an existing custom strategy that overrides `send(ReplicatedEventQueueItem)` but not this method is rejected for 1.1. It still works for legacy.
- `OrderedWebhookDispatcher.topology()` is public.
- `MainEventBusProcessor.webhookDispatchTopology()` is public.
- `DefaultRequestHandler.taskAuthorityConfiguration()` is public.
- `JpaTaskMigration.activeEpoch()` is public. It is `@Transactional(REQUIRES_NEW)`. `JpaTaskMigration.authorityCapabilities()` is public.
- `VersionRouter` adds these public static methods:
  - `attachRegistration` with an extra `authority` argument
  - `validateDefaultRegistration` with an extra `authority` argument
  - `requireTaskAuthority(InterfaceRegistration, Instance)`
  - `requireTaskAuthorityForVersion`
  - `requireTaskAuthorityForCard`
- `DefaultProducers.createTaskAuthorityConfiguration` is a public `@Produces` method.
- New types: `TaskAuthorityConfiguration` and `TaskAuthorityCapabilities`.
- Every new method on an existing interface is `default`.
- The old `VersionRouter` overloads remain and delegate with `authority = null`. This keeps source and binary compatibility. It also leaves a gate bypass for any external caller (F-4).

## Findings

**F-1. Medium, unverified.** DefaultProducers.java (the new producer) and TaskAuthorityConfiguration.java:`of(TaskStore, ...)` (`taskStore instanceof AtomicTaskStore`).
- Failure scenario: an application provides its `TaskStore` through a normal-scoped `@Produces` method declared as `TaskStore`. The injected ArC client proxy does not implement `AtomicTaskStore`. 1.1 is rejected with "no AtomicTaskStore companion" although the real instance is atomic. This is a false reject (fail-closed).
- Requirement violated: "a pass/fail must reflect the actual store".
- Discriminating check: a `@QuarkusTest` with two variants:
  - one with a `@Produces @ApplicationScoped TaskStore` returning an `InMemoryTaskStore`
  - one with `JpaDatabaseTaskStore` selected through its `@Alternative`
  
  Each is wired to the real `TaskAuthorityConfiguration` bean. Assert `violations()` is empty, with a 1.1 registration and a non-1.1 one. Current tests only use fixed `Topology` records.

**F-2. Medium, unverified.** rest A2AServerRoutes.java:261-267 (cards registered with `.handler(...)`, not `blockingHandler`) calling `cardTaskAuthorityAllows` (:798). Also jsonrpc A2AServerRoutes.java:266 and :286 calling :484 and :509.
- Failure scenario: with `JpaDatabaseTaskStore` and a 1.1 registration, the first agent-card request runs `JpaTaskMigration.authorityCapabilities()` on the event loop. That is a `REQUIRES_NEW` transaction plus an `unmigratedIds` scan. Blocking JTA/Hibernate on an IO thread either throws `IllegalStateException` or blocks the loop.
  - `TaskAuthorityConfiguration.violations()` turns a `RuntimeException` into "could not be determined". The card is rejected with a 500, although a worker-thread request would pass.
  - The result depends on which request runs first, because a pass is cached.
- Requirement violated: validation must be deterministic and non-blocking on request threads.
- Discriminating check: a Quarkus test with the Jpa store, a migrated shared epoch and a 1.1 registration. Make the first request `GET /.well-known/agent-card.json` before any task call. Expect 200.

**F-3. Low.** VersionRouter.java:125-133 and the multiversion `selectRegistration`.
- Failure scenario: tenant registration {0.3,1.0,1.1} on a failing topology. 0.3 and 1.0 requests to that tenant return 500, because the gate runs before `select(version)` and does not check the selected version.
- The failure is evaluated again on every request, which means an extra DB scan per rejected request on JPA (the failing result is not cached).
- This is fail-closed, not narrowing. The task text says "before 1.1 is served", so the intended scope for legacy versions on a mixed registration should be confirmed.
- Discriminating check: a test sending an `A2A-Version: 1.0` request to a {0.3,1.0,1.1} registration with a failing topology. Decide the expected status and assert it.

**F-4. Low.** VersionRouter.java:111-115 (5-argument `attachRegistration`), :144-148 (2-argument `validateDefaultRegistration`) and :207-209 (`authority == null` returns).
- Failure scenario: external code (compat or custom routes) calls the old overloads. 1.1 is served with no gate, silently.
- I found no production caller in this repo; the grep showed only the new wiring.
- Discriminating check: a grep for the old overloads, which I did once. Consider deprecating them or documenting that null skips the gate.

**F-5. Low.** rest A2AServerRoutes.java:803 (`catch (IllegalStateException)`).
- Failure scenario: two user-supplied `TaskAuthorityConfiguration` beans. `get()` throws `AmbiguousResolutionException`. It is not caught by the card handler and surfaces as an unlogged Vert.x 500. Fail-closed, but the diagnostics differ from the unsatisfied case.
- Discriminating check: a unit test for the REST card handler with an ambiguous `Instance`.

**F-6. Medium, unverified.** The card gate (`requireTaskAuthorityForCard`, VersionRouter.java:194-204).
- Failure scenario: it only fires when a registration for the binding and tenant lists 1.1. With `InterfaceRegistrationResolver` unsatisfied it returns, so the card is served unchecked. If an application's `AgentCard` advertises a 1.1 `supportedInterface` without a registration, the card advertises 1.1 and the gate never runs. Requests using that version are still gated through `requireTaskAuthorityForVersion`.
- I did not verify whether card validation elsewhere prevents advertising 1.1 without a registration.
- Discriminating check: a test with no resolver bean and a card listing protocolVersion 1.1 on a failing topology. Assert the card request is rejected.

**F-7. Info.** Per-request rejection returns a generic 500 / `handleGenericError` / gRPC internal error. This is not a typed A2A error. It is consistent but opaque to clients.

## Disposition

**Incomplete evidence.** The wiring looks fail-closed in each of the five routes, with no fallback and no change to the selected versions. Legacy-only registrations are guarded by the `versions().contains("1.1")` check and by the producer being lazy. I cannot accept it as fully verified. F-1, F-2 and F-6 depend on ArC proxy and event-loop behaviour that was not run.

## Not examined

- ArC proxy generation behaviour, since I ran no build or Quarkus test
- Quarkus behaviour for JPA transactions on an IO thread
- Whether the inner single-version `A2AServerRoutes` startup observers fire in multiversion deployments
- `DefaultRequestHandler` constructor requirements on the store type
- Compat03 modules and the other callers of the old `VersionRouter` overloads
- Whether an agent card can advertise 1.1 without a registration
- `GrpcHandler` card handling outside `createCallContext`
- Migration ordering relative to the startup observers, which matters for JPA unmigrated rows when the observer runs
- The docs and test files, and all `TaskAuthorityConfiguration` internals beyond the code read above
End: Wed Nov 25 16:38:25 UTC 2026
