# P3-T12 Review D (fresh-context, read-only)

- Task: P3-T12, task-authority topology validation before A2A 1.1 is served/advertised
- Repo: /Users/kabir/.codex-api/worktrees/3053/a2a-java
- Branch: support/a2a-1.1.0 (base 44c276a17)
- Candidate: v4, uncommitted, FROZEN
- Scope: docs-claims audit and public-API check only (small scope, per request)
- Start time (`date -u`): Wed Nov 25 15:55:06 UTC 2026
- Time limit: 15 minutes (ends about 16:10 UTC)
- Constraints: no source, test or doc edits; no Maven or git writes; test.log untouched

## Answers (appended as produced)


### Q1 part 1 (docs audit, verified so far)

Evidence paths are relative to the repo root. Verdicts: Supported / Overstated / Unsupported.

- (a) "at startup for the default registration (single-version REST, JSON-RPC and gRPC)": **Supported.** REST `setupRouter` calls `validateDefaultRegistration(..., taskAuthority, HTTP_JSON)` at `reference/rest/src/main/java/org/a2aproject/sdk/server/rest/quarkus/A2AServerRoutes.java:171-172`. JSON-RPC `setupRoutes` does the same at `reference/jsonrpc/src/main/java/org/a2aproject/sdk/server/apps/quarkus/A2AServerRoutes.java:223-224`. gRPC `validateRegistrationAtStartup(@Observes StartupEvent)` does it at `reference/grpc/src/main/java/org/a2aproject/sdk/server/grpc/quarkus/QuarkusGrpcHandler.java:147-148`. Neither multiversion route validates at startup, which matches the "single-version" qualifier.
- (b) "per request or card fetch for a dynamically resolved tenant": **Supported.** Per-request `attachRegistration` is at `rest/.../A2AServerRoutes.java:886-888` and `jsonrpc/.../A2AServerRoutes.java:694-696`. gRPC's per-RPC `getInterfaceRegistration` override is at `QuarkusGrpcHandler.java:152-164`. Card fetches are gated through `requireTaskAuthorityForCard` at `rest/.../A2AServerRoutes.java:791,810` and `jsonrpc/.../A2AServerRoutes.java:484,509`.
- (d) Profile table rows versus `checkProcessLocal` and `checkSharedStorage` (`server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskAuthorityConfiguration.java`):
  - Row 3 (replicated queues with in-memory store, no writer epoch, no committed envelopes, or queue epoch differs from storage epoch) → **Supported**, Rejected. Replicated with process-local is rejected at `:104-107`; shared-storage replication checks are at `:130-141`.
  - Row 4 (JPA with no epoch, node epoch differs, or unmigrated rows) → **Supported**, Rejected at `:117-128`.
  - Row 5 (shared storage without ordered dispatcher, with process-local gate, or with a non-quiescent sender) → **Supported**, Rejected at `:143-153`.
  - Row 6 (no atomic companion, or undeclared authority) → **Supported**, Rejected at `:87-93`.
  - **F1 (Overstated).** Row 2 (JPA, Accepted) lists "shared webhook gate with a quiescent sender" but omits the required shared ordered webhook dispatcher on every bus and finalization sender (`:143-145`). A configuration with the gate and sender but no ordered dispatcher is rejected, so the Accepted row is not a sufficient condition list.
  - **F2 (Incomplete).** Row 1 (in-memory store, no replicated queue manager, Accepted) omits that a shared webhook gate with a process-local store is rejected (`:108-111`).
- (f) "does not detect a node that later loses its epoch (the store refuses its writes itself)": **Supported** for the paths checked. `JpaTaskMigration.checkWritable()` throws `StaleWriterEpochException` at `extras/task-store-database-jpa/src/main/java/org/a2aproject/sdk/extras/taskstore/database/jpa/JpaTaskMigration.java:100-110`. It is called from `JpaDatabaseTaskStore.java:151,311` and `JpaTaskMutationTransactions.java:74,103`. The gate's pass cache (`TaskAuthorityConfiguration.java:45,163-171`) means it does not re-check after a pass. Write-path coverage for the webhook-lane transactions is still unverified.
- (h) Custom-store opt-in text: **Supported.** `AtomicTaskStore.authorityCapabilities()` is a default returning `UNDECLARED` at `server-common/.../tasks/AtomicTaskStore.java:20-21`. `TaskAuthorityCapabilities.processLocal()` and `sharedStorage(Long, Long, boolean)` exist at `server-common/.../tasks/TaskAuthorityCapabilities.java:53-61`. `QueueManager.replication()` is a default at `server-common/.../events/QueueManager.java:224`. `ReplicationStrategy.supportsCommittedReplication()` is a default at `extras/queue-manager-replicated/core/.../ReplicationStrategy.java:25`. Its default value is still to be checked.
- (g) Plain-Java instructions, method existence: **Supported.** `DefaultRequestHandler.taskAuthorityConfiguration()` is at `server-common/.../requesthandlers/DefaultRequestHandler.java:387-388`. `requireVersion11()` is public at `TaskAuthorityConfiguration.java:162`. `validating(...)` is public at `:178`.
  - **F3 (Overstated, pending the CDI scope check).** `storage.md:249-250` says "a passing result is cached for the process". `taskAuthorityConfiguration()` builds a new `TaskAuthorityConfiguration` on every call (`DefaultRequestHandler.java:388`). The pass cache is per object (`TaskAuthorityConfiguration.java:45,163-171`). The `server.md:248` inline form `taskAuthorityConfiguration().requireVersion11()` therefore re-evaluates every call, and for JPA that re-reads the database each time.
- Pending: (c), (e), (i), the rest of (h), and the public-API and multiversion checks (Q2 and Q3).

### Q2 public API (read-only `git diff HEAD --unified=0`, six files)

Method: every hunk header in all six diffs is `-N,0` (pure insertion). Removed content lines (`^-`, excluding `---` file headers) = **0 in every file**. Nothing removed, nothing changed in place.

- `server-common/.../tasks/AtomicTaskStore.java`: +`default authorityCapabilities()` returning `UNDECLARED`. Default: yes.
- `server-common/.../events/QueueManager.java`: +import, +`default replication()` returning `QueueReplication.NONE`. Default: yes.
- `extras/queue-manager-replicated/core/.../ReplicationStrategy.java`: +`default boolean supportsCommittedReplication()` returning `false`. Default: yes. This also closes the earlier (h) "default value still to be checked" item.
- `extras/queue-manager-replicated/core/.../ReplicatedQueueManager.java`: +`@Override replication()` on a public class (not an interface). Additive.
- `reference/common/.../quarkus/VersionRouter.java` (public final class): all pre-existing public overloads keep their signatures. The 2-arg `validateDefaultRegistration(resolvers, binding)` (`:145-148`) and 5-arg `attachRegistration(..., inboundVersion)` (`:111-115`) now delegate with a `null` authority, which skips the 1.1 gate. Their observable behaviour is unchanged from HEAD, but they are ungated. Added public statics: `attachRegistration` 6-arg (`:121`), `validateDefaultRegistration` 3-arg (`:151`), `requireTaskAuthority` (`:171`), `requireTaskAuthorityForVersion` (`:182`), `requireTaskAuthorityForCard` (`:194`). All additive.
- `server-common/.../requesthandlers/DefaultRequestHandler.java` (public class): +concrete `taskAuthorityConfiguration()` (`:387-388`). Additive; not an interface.
- Interfaces: all new methods on public interfaces are `default` (`AtomicTaskStore`, `QueueManager`, `ReplicationStrategy`). `InterfaceRegistrationResolver` is untouched.
- New public types (`TaskAuthorityCapabilities` record, `TaskAuthorityConfiguration` final class) are additive.

**Finding L1 (Low, API).** `VersionRouter.java:111-115` (5-arg `attachRegistration`) and `:145-148` (2-arg `validateDefaultRegistration`) are retained public overloads that silently skip the 1.1 gate. Shipped callers were checked by grep and all use the gated overloads (REST `A2AServerRoutes.java:172,886`, JSON-RPC `:224,694`, gRPC `QuarkusGrpcHandler.java:148`). Failure scenario: a third-party integrator calls the legacy overload and never gets startup fail-fast for 1.1. The per-request gate in the shipped routes still applies. Severity is low because nothing shipped reaches them.

Q2 verdict: no removed or changed pre-existing public signature. Every new interface method is `default`.
