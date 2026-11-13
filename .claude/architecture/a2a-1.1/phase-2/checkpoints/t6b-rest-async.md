# T6b REST async GET checkpoint — 2026-11-12T19:38Z

Authorized continuation window: 2026-11-12T19:27:35Z–20:27:35Z. Requested implementation model: `gpt-6-luna`, medium reasoning; observed runtime metadata unavailable. Historical coherent-iteration count before continuation was 2/10. This continuation completed one new bounded objective (async decoded REST GET plus its status-aware HTTP seam and the focused GET query corrections), leaving the assigned seven-objective ceiling intact. Raw Maven ledger started at #134 and is now #140. Substantive no-progress streak remains 0. No subagents were used.

## Implemented

- Added source-compatible `GetBuilder.getAsyncResponse()` with an unsupported default for external HTTP-client implementations, plus `AsyncGetCall` exposing the full `A2AHttpResponse` and explicit cancellation.
- Implemented status-preserving cancellable GET response calls in the JDK, Vert.x, and Android HTTP clients. Android's async response path preserves non-2xx bodies, including 401/403, for REST error mapping.
- Implemented selected-1.1 `RestTransport.getTaskAsync` with `StreamDecoder.decodeTaskJson`, per-task `TaskDecodeEvidence`, no retry, propagation of cancellation to the underlying GET, and `RestErrorMapper` handling for non-2xx responses.
- Shared the selected-1.1 sync/async GET URL builder. It includes `historyLength` and `timelineLength` when supplied, including zero. `currentGeneration` is rejected before interceptors or I/O per the Phase 2 unsupported-query policy. Explicit 1.0 legacy GET remains unchanged.
- Selected-1.1 decoded REST list GET now includes `ListTasksParams.timelineLength`, including zero. The legacy list URL still omits it.
- Added focused coverage for raw async timeline evidence, status/body error mapping, REST cancellation delegation, 1.0 pre-I/O rejection and URL preservation, current-generation rejection, zero-valued GET/LIST timeline limits, async tenant/interceptor headers, and underlying GET cancellation/status handling in all three HTTP clients.

## Raw Maven ledger

All commands ran in `/Users/kabir/.codex-api/worktrees/3053/a2a-java` and completed within the command time cap.

| Raw # | Command selector | Result |
| --- | --- | --- |
| 135 | `mvn -B -ntp -pl client/transport/rest -am '-Dtest=Phase2PublicClientTest#wrappedOneOneAsyncGetRetainsUnknownTimelineEvidence' -Dsurefire.failIfNoSpecifiedTests=false test` | Expected RED: 1 test errored with `UnsupportedOperationException: Transport does not support getTaskAsync` from the SPI default. |
| 136 | `mvn -B -ntp -pl client/transport/rest -am -Dtest=Phase2PublicClientTest,RestTransportTest -Dsurefire.failIfNoSpecifiedTests=false test` | Compile failure at `RestTransport.java:277`: `JsonProcessingException` was not thrown by the async start try block. Removed the unnecessary checked exception from that catch. |
| 137 | Same selector as #136 | BUILD SUCCESS; 32 tests, 0 failures/errors (8 `Phase2PublicClientTest`, 24 `RestTransportTest`). |
| 138 | `mvn -B -ntp -pl http-client,extras/http-client-vertx,extras/http-client-android,client/transport/jsonrpc -am -Dtest=JdkA2AHttpClientTest,VertxA2AHttpClientIntegrationTest,AndroidA2AHttpClientSSETest,Phase2PublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test` | Android compile failed because `AndroidGetBuilder` could not access private `executeStatusAware(HttpURLConnection)`. Changed it to `protected`. JDK, Vert.x and JSON-RPC selectors passed in this invocation. |
| 139 | Same selector as #138 | BUILD SUCCESS; 77 tests, 0 failures/errors (19 JDK, 17 Vert.x, 19 JSON-RPC, 22 Android). |
| 140 | Same selector as #136 | BUILD SUCCESS; 33 tests, 0 failures/errors (9 `Phase2PublicClientTest`, 24 `RestTransportTest`), including tenant/interceptor and zero-valued timeline query assertions. |

No sandbox escalation was needed. Maven emitted the known Java 17/Quarkus plugin compatibility and unversioned plugin warnings; they did not block these focused builds.

## Files touched for this objective

- `.claude/architecture/a2a-1.1/phase-2/checkpoints/t6b-rest-async.md`
- `http-client/src/main/java/org/a2aproject/sdk/client/http/A2AHttpClient.java`
- `http-client/src/main/java/org/a2aproject/sdk/client/http/JdkA2AHttpClient.java`
- `http-client/src/test/java/org/a2aproject/sdk/client/http/JdkA2AHttpClientTest.java`
- `extras/http-client-vertx/src/main/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClient.java`
- `extras/http-client-vertx/src/test/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClientIntegrationTest.java`
- `extras/http-client-android/src/main/java/org/a2aproject/sdk/client/http/android/AndroidA2AHttpClient.java`
- `extras/http-client-android/src/test/java/org/a2aproject/sdk/client/http/android/AndroidA2AHttpClientSSETest.java`
- `client/transport/rest/src/main/java/org/a2aproject/sdk/client/transport/rest/RestTransport.java`
- `client/transport/rest/src/test/java/org/a2aproject/sdk/client/transport/rest/Phase2PublicClientTest.java`

The REST source/test files already contained the prior sync GET/LIST work recorded in `t6b-rest-unary.md`; this continuation added the async path and the query-policy corrections above.

## Remaining scope and risks

- This is only the REST async GET slice plus the focused GET query correction. It does not complete or accept T6b. Unary SEND, SSE, provider feature claims, public `Client` coverage, independent review, and broader phase verification remain pending.
- `currentGeneration` remains intentionally unsupported in REST GET until its designated phase; the adapter rejects it explicitly.
- Verification covered the JDK, Vert.x, Android, REST, and JSON-RPC selectors listed above; no full reactor build was run. Maven configuration skipped Spotless checks in these invocations.
- No SEND/SSE/provider-capability, T6c/T7, commit, push, or PR work occurred in this objective.
