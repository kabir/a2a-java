# P3-T17 final independent full review

## Header

| Item | Value |
|---|---|
| Task | P3-T17: JSON-RPC production end-to-end mutation slice and shared T18/T19 fixture |
| Candidate | `5c0369db1e55b27325c8154887a0e181ddaf0368` on `work/p3-t17` (verified with `git rev-parse HEAD`) |
| Reviewed range | `d869e957b310e2585409ac3b3adb166d7823d36c..5c0369db1e55b27325c8154887a0e181ddaf0368` (5 commits: `c572b04b`, `5d8deaf2`, `71abc014`, `37aa8600`, `5c0369db`; 38 files, +6510/-83) |
| Review type | Final independent task review. It is not the T20 phase audit. |
| Requested model | IBM Bob configured model |
| Observed model | Unavailable. The session exposes no model identifier. This review does not claim Sol, Terra or Luna, and Astra was not used. |
| Assignment start | 2026-11-26 22:09:32 UTC |
| Initial deadline | 2026-11-27 00:09:32 UTC. The user authorized an additional two hours on expiry. The original deadline is preserved and the extension was not needed. |
| Tree state at start and end | Clean except the expected untracked `P3-T17-fix3-review.md`. Its sha256 is `9d8c8eebdbb7586863036daa3ffd56be1d96ee533a48d41125a812f08cfbf6d4`, identical at start and end (preserved byte for byte). |
| Maven repo / logs | `/tmp/p3-t17-review-final-m2`, `/tmp/p3-t17-review-final-logs` |

## Decision

**ACCEPTED for T17, with retained limits (section 7) and three non-blocking Low findings (section 6).**

I found no Critical, High or Medium defect. No acceptance bullet is satisfied only by a skipped test. No limit below weakens a T17 shared acceptance bullet. Each limit is classified with a rationale, and none is silently waived.

This acceptance covers T17 only. It does not close the following, which remain T20 or later obligations:
- the T13 unchanged live PostgreSQL/Kafka suite;
- the compatibility gate;
- the Java 17 clean install;
- the phase audit;
- the Phase 4/5/6 obligations.

The in-JVM tests in this task are not a T13 live gate.

## 1. Provenance and source integrity

- The sha256 of the 7 production, doc and test files recorded in `progress-T17.md` (fix-3 section) match the committed tree. Results are in `/tmp/p3-t17-review-final-logs/sha256-now.txt`. The `P3-T17-fix2-review.md` hash `b15d0fa4…` also matches.
- Production diff, 7 files:
  - `JSONRPCTransport` (client): a no-cursor `observe` that shares `startObservation`, including interceptors.
  - `A2AServerRoutes`: id echo for parsed requests, and the streaming-input guard reduced to the delivery-version check.
  - `PublicationReconciler`: `completeWithoutSnapshot`, `unseenThrough`, `COVERED_WITHOUT_SNAPSHOT`.
  - `DefaultRequestHandler`: opt-in recovery wiring, `observeSendStream`, detach paths, `@PreDestroy`.
  - `a2a-defaults.properties`: `a2a.publication-recovery.enabled=false`.
  - `JSONRPCUtils`: `getAndValidateMethod`. The selected-1.1 parse wraps `InvalidParamsError` as `InvalidParamsJsonMappingException` carrying the id.
  - `JSONRPCHandler`: guard removal, `timelineLength` forwarded on get/list, and `rejectLegacyTimeline` applied only to V1_0.
- No change in `compat-0.3`, REST, gRPC, `MainEventBusProcessor` or the T13 reserve/release pairing. Frozen 0.3 behavior is therefore untouched at the source level.

## 2. Evidence: commands, counts, skips

All commands ran serially on HEAD with `timeout -k 30 1200`, `-B -ntp`, the own repo, and `-Dsurefire.failIfNoSpecifiedTests=false`. Exit codes come from the `.exit` files. Counts come from the surefire summary lines in the logs.

| Log | Scope | Exit | Tests | Skips |
|---|---|---|---|---|
| `codec.log` | `-pl spec-grpc,jsonrpc-common -am -Dtest=Phase2CodecTest test` | 0 | 11 | 0 |
| `recovery.log` | `-pl server-common -am` with `PublicationGapRecoveryTest` (37), `StreamingSendPublicationRecoveryTest` (45), `PublicationRecoveryProvisioningTest` (10) | 0 | 92 | 0 |
| `routes.log` | `-pl reference/jsonrpc -am` with `A2AServerRoutesTest` (41), `Phase2BoundaryTest` (5) | 0 | 46 | 0 |
| `plan1.log` | `-pl transport/jsonrpc,client/transport/jsonrpc -am` | 0 | `Phase2PublicClientTest` + `Phase3ClientGenerationTest` = 26; `JSONRPCHandlerTest` = 80 | client 0; handler **1** |
| `cbase.log` | `-pl client/base,reference/jsonrpc -am` with `Phase3JSONRPCPublicClientTest` (18), `ExistingOrdinaryEntryPointReductionTest` (31), `Phase2BoundaryTest` (5) | 0 | 54 | 0 |
| `plan2.log` | `-pl tests/multiversion/jsonrpc -am verify` with `Phase3JSONRPCMutationTest` (21), `Phase3JSONRPCNoUserArtifactsTest` (4) | 0 | 25 | 0 |

**The one skip.** It is the `@Disabled testDefaultRequestHandlerWithCustomComponents` in `JSONRPCHandlerTest`. I verified it is also `@Disabled` at base `d869e957`, so it is pre-existing and unrelated. It is not counted as passed.

**Earlier recorded runs, cited but not repeated.**
- `/tmp/p3-t17-fix3-logs/` and `/tmp/p3-t17-review-fix3-logs/` hold the broad reactors:
  - the REST/gRPC multiversion runs, with 18 and 22 skips respectively. These come from JSON-RPC-specific assumptions and `@Disabled` auth tests, per earlier reviews;
  - `MultiVersionJSONRPCTest` (75).
- Nothing in REST, gRPC or `MainEventBusProcessor` changed in the range. Repeating those reactors would not resolve any open uncertainty, so I did not repeat them. I did not rerun them and I do not claim this review observed them.

**Android SSE cancellation failure (deferred by the user, unrelated).**
- `AndroidA2AHttpClientSSETest.cancellableSseDisconnectsHeldOpenPeer:213` failed once, in `/tmp/p3-t17-fix3-logs/grpcrest-1-android-failure.log`.
- It passed in the rerun (`grpcrest-rerun.log`) and in the fix3 reviewer's run.
- The cause is unestablished. It is not called fixed. The failed log is preserved, and the later passes do not erase it.
- It is outside the files changed by T17.

**Probes not run.**
- I did not run the optional wire-level probes (a filtered-demand 1.0 subscriber, or a mocked route-level Throwable).
- Findings F-1 and F-3 and the F1.1 assessment rest on direct code reading. They are labeled as such.

## 3. Acceptance matrix (T17 shared bullets, A03/A04/A05/A06/A07/A10/A12)

| Requirement | Verdict | Evidence |
|---|---|---|
| Real JSON-RPC public clients, routes, core and store (no mocks of the path under test) | Pass | `Phase3JSONRPCMutationTest` (21) runs `Phase3MutationScenarios` over real HTTP with the Quarkus route, `DefaultRequestHandler` and the real store. `Phase3JSONRPCPublicClientTest` (18) covers the public client against a real server. |
| Race guards and typed errors, no retry | Pass | The guard race uses a `CyclicBarrier`. Typed errors are asserted on the wire and in the client. Server-side spies show a single attempt with no retry or resend. |
| Ordinary send, stream and subscription reduction, cross-entry reliability | Pass | `ExistingOrdinaryEntryPointReductionTest` (31) and the e2e scenarios. |
| Invalid payloads and error correlation | Pass | `Phase2CodecTest` (11), `A2AServerRoutesTest` (41) and the handler tests. The route echoes the id for parsed requests. The 1.1 client requires a matching id, and the 1.0 client is unaffected because it checks the error first. |
| Frozen 1.0/0.3 behavior | Pass with limit L-3 | The 0.3 reader and writer use the unchanged `compat-0.3` client (`Public03ClientSupport`), independent of new-to-new tests. The "1.0" client in the T17 scenarios is the updated SDK pinned to 1.0. Raw frozen 1.0 evidence comes from the unchanged `MultiVersionJSONRPCTest` (75) and the Phase 0 fixtures. |
| Filtered finite demand | Pass with limit L-1 | `ProjectingPublisherTest` (T12) and `Phase3OldObserver`. Production uses `request(1)` (see L-1). |
| Direct Message and task-bearing output with no-task, config, publication, replication and queue spies | Pass | The scenarios assert zero task, config, publication and replication effects and no queue growth, through `Phase3Spy` and `Phase3SpyQueueManager`. |
| Terminal artifacts, inline config and notification | Pass | The e2e scenarios plus `Phase3JSONRPCNoUserArtifactsTest` (4). The webhook negative control is sleep-based (L-4). |
| Zero, equal and unknown generation paths | Pass | `Phase3ClientGenerationTest`, handler tests and the e2e scenarios. |
| Lost publication plus explicit GetTask, no mutation or resend | Pass | `commitWithoutPublication` and the lost-publication hook. Explicit GetTask returns the stored state, and no mutation or resend is observed. |
| **User decision:** recovery opt-in, default false | Pass | `a2a-defaults.properties` has `a2a.publication-recovery.enabled=false`. `PublicationRecoveryProvisioningTest` (10) covers provisioning and default-off. The docs section matches the code. `Phase3TestProfile` enables it with short bounds (200/1000/600 ms) for the e2e only. |
| **User decision:** a post-tap covered publication without a replacement snapshot closes with the existing resync/GetTask, including late arrival after the watermark | Pass | `Observation.complete` and `admit`:<br>- `completeWithoutSnapshot` sets `unseenThrough = G`.<br>- A late arrival at or below G fails with the resync `InternalError`, unless `terminalSeen`.<br>- A redelivery above G is ignored.<br>- Subscriptions with a snapshot keep the discard.<br>`StreamingSendPublicationRecoveryTest` (45) covers this. |
| Ordering, cleanup, duplicates | Pass | `observeSendStream` attaches before the agent starts. Detach covers the `!agentStarted` finally, the consumer `onError`, the outer `catch RuntimeException \| Error`, and attach failure (`queue.close(true)`). Duplicates are handled in `admit`. |
| T13 reservation pairing | Pass | The code is unchanged. The recovery tests assert pairing for the new paths. This is not a live T13 gate. |
| Conservative pre-tap ambiguity | Pass | Covered in `PublicationGapRecoveryTest` (37). |
| Artifact-only path | Pass, limit N3 | The artifact-only request has no stream, which is a documented, retained design limit. |
| Shared fixture fit for T18/T19 | Pass with caveat (F-2) | See section 5. |

## 4. Architectural and compatibility assessment

- **Layering.**
  - Recovery is an opt-in observation attached in the request handler before the agent starts.
  - `PublicationReconciler.Observation` is the single place that decides a gap, a covered publication, a late arrival or a terminal.
  - The route, handler, codec and client changes are thin and local.
- **Typed errors and no retry.** The client never retries and surfaces the server error with its id. The server never resends.
- **Compatibility.**
  - Frozen 0.3 is untouched at the source level, and 0.3 end-to-end evidence uses the unchanged `compat-0.3` client.
  - 1.0 is protected by `rejectLegacyTimeline` applying only to V1_0, and by the unchanged 1.0 suites.
  - The one soft spot is that the "1.0 client" in the new e2e is the current SDK, not a historical binary (L-3).
- **Shared core.** The new default behavior is off. With recovery enabled, the paths that change are the covered-without-snapshot completion and `unseenThrough`. They are guarded by 45 + 37 + 10 focused tests.

## 5. Shared fixture fitness for T18/T19

Reusable as is: `drainIngress`, `commitWithoutPublication`, `storedTask`, `deleteStoredTask`, `childQueueCount`, `pushConfigCount`, `send03Message`, `observeUnderFiniteDemand`, `read03Task` and `assertRawMalformedFinalArtifactRejected`.

JSON-RPC-specific and expected to be overridden by T18/T19: the raw malformed hook, the 0.3 send hook, the lost-publication hook and the registration-specific tenant `phase1-2` VersionNotSupported case. They are abstract hooks, so this is a normal extension point and not a defect. See F-2 for the one e2e whose name overstates its coverage.

## 6. Findings (ordered by severity; none blocks)

### F-1 (Low) Route-level `Throwable` after a successful parse returns a null-id InternalError

- **Path:** `reference/jsonrpc/.../A2AServerRoutes.java:413-415`.
  `catch (Throwable t)` builds `new A2AErrorResponse(new InternalError("Internal error"))` with no id, even when `request != null`. The adjacent `A2AError` branch (line 401) now echoes `request.getId()`.
- **Consequence (code reading, not executed):** a 1.1 client's `responseResult` requires a matching id, so it would report a missing or null id instead of a typed InternalError.
- **Repro idea:** mock the handler so it throws `RuntimeException` after a parse, then call `invokeJSONRPCHandler`. `A2AServerRoutesTest` has no such test.
- **Why Low:** the path predates T17. The handlers catch `Throwable` themselves, so it needs an unexpected route-level throwable. It is not a T17 acceptance bullet.
- **Suggested follow-up:** apply the same `request != null ? … : …` pattern in the `Throwable` branch, plus one route test.

### F-2 (Low) `metadataOnlyStatusPreservesTheMessageOccurrenceCount` is weaker than its name

- **Path:** `tests/server-common/.../Phase3MutationScenarios.java` (~line 717).
  - The scenario uses `phase3:work` (`startWork`) on an already-WORKING task. That is a same-state status with no message.
  - It asserts history +1 and one message timeline entry.
  - It does not exercise the acceptance.md scenario "metadata-only full Task update starting from a message-bearing status".
- **Mitigation:** that scenario is covered at core level by `FullTaskProposalTest.metadataOnlyProposalAddsOneSyntheticStatusObservationWithoutDuplicatingItsMessage` (T05) and `ClientTaskReducerTest`.
- **Why Low:** the core behavior is tested. The gap is that the shared e2e name overstates its coverage, and T18/T19 will inherit it. Either rename it or add the message-bearing variant.

### F-3 (Low) No T17 test for interceptor or wrapper interception on the new built-in `observe`

- **Path:** `JSONRPCTransport.startObservation` applies `applyInterceptors(method, …)`.
- Wrappers are covered by `VersionedWrapperTest` and `OpenTelemetryClientTransportTest` (mock-based, T16), but no T17 test exercises interceptors on this path.
- **Why Low:** the code path is shared with the existing cursor path, and the wrappers have their own tests.

### Carry-over of the fix3 review's Low items

- **F1 (committed R2 evidence covers one string id and has no route-level test):** confirmed as a retained Low evidence gap. The behavior itself is covered at codec level by `Phase2CodecTest` and for non-send parameters by `A2AServerRoutesTest`. Non-blocking.
- **F2 (the handler N1 test does not pin the failure reason or remote delivery, and agent/main-queue cleanup after an N1 close is unasserted):** confirmed as a retained Low evidence gap. Cleanup is covered by `childQueueCount` and `awaitQueueClosed` in the e2e. Non-blocking.

## 7. Retained limits: limitation versus blocker

| ID | Item | Classification | Rationale |
|---|---|---|---|
| L-1 | F1.1 finite filtered demand is tested in-process (`Phase3OldObserver`, `ProjectingPublisherTest`), not as a separate wire probe | **Limitation, not a blocker** | `JSONRPCHandler.convertToSendStreamingMessageResponse` (lines 883-924; `request(1)` at 896 and 902) subscribes upstream with demand 1 and replenishes one item per `onNext`. This is independent of the SSE writer's `request(Long.MAX_VALUE)` (`SseResponseWriter:82`). So production applies finite demand of 1 to the projected publisher on the wire path, and the in-process observer exercises the same demand pattern. A wire probe would add confidence, not coverage of a distinct code path. I did not run one. It could be added in T18/T19 or T20. |
| L-2 | CDI + JPA with `@PreDestroy` is untested (N4); `JpaDatabaseTaskStore` is an `AtomicTaskStore` | **Limitation, not a blocker** | The feature is opt-in and default off. `@PreDestroy` is verified by a direct call. The JPA store is not changed by T17, and live PostgreSQL/Kafka verification is T20 by decision. |
| L-3 | The "1.0" client in the new e2e is the current SDK pinned to 1.0, not a historical binary; frozen 1.0 raw evidence comes from the unchanged `MultiVersionJSONRPCTest` (75) and the Phase 0 fixtures | **Limitation, not a blocker** | No server or compat source changed for 1.0 semantics beyond `rejectLegacyTimeline` (V1_0 only). The independent frozen evidence is the unchanged pre-existing suites, and the 0.3 e2e uses the unchanged `compat-0.3` client. A historical-peer check belongs to the T20 compatibility gate. |
| L-4 | Sleep-based observations: the 1000 ms webhook negative control, the 300 ms direct-message settle (re-asserted at a server-side `awaitQueueClosed` barrier), the 400 ms `sendCollected` settle, `Phase3OldObserver.awaitFrames` polling, and `awaitQuiescent` | **Limitation, not a blocker** | Positive waits cannot falsely pass. Negative waits could miss a very late event, but the direct-message path is backed by the queue-closed barrier, and the guard race is deterministic (`CyclicBarrier`). The risk is a false pass on a rare late effect, not a false failure. |
| L-5 | Default recovery bounds (5/5/10 s) are covered only by injected-clock unit tests; the e2e uses 200/1000/600 ms | **Limitation, not a blocker** | The bounds are configuration. The logic is identical, and the clock-injected tests cover it. |
| L-6 | N3 (artifact-only has no stream), N6, N7, N8 (no per-task tenant ownership model), N9 (input message role not validated) | **Retained, developer-accepted** | All were raised by earlier reviews and resolved by decision or deferred to the documented phase. N9 is an explicit developer decision. |
| L-7 | The Android SSE cancellation intermittent failure | **Deferred by the user, unrelated, cause unestablished** | It is not called fixed. Its failed log and later passes are preserved. |

## 8. Previous review findings

- The F1-F12 findings of `P3-T17-review.md` are resolved (confirmed in `P3-T17-correction-review.md`).
- N2 and N11 of the correction review are resolved (confirmed in the fix2 review).
- R1 (late covered arrival) and R2 of the fix2 review are resolved. I re-verified them directly: the code in `Observation.admit` and `complete`, and the 45 + 37 recovery tests that passed on HEAD.
- The two Low items of the fix3 review are carried over in section 6.

## 9. Final check

- `git status` shows only the untracked `P3-T17-fix3-review.md`, plus this review file once written.
- The fix3 review hash is unchanged.
- I edited no tests, production code, previous reviews or shared status, and made no commit or push.
- I used no nested agents and no Serena.
