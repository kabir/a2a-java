# T6b REST SSE checkpoint — 2026-11-12T19:54Z

Authorized continuation window: 2026-11-12T19:27:35Z–20:27:35Z. Requested implementer model: `gpt-6-luna`, medium reasoning; observed runtime metadata unavailable. Historical coherent-objective count before this continuation was 4/10. This continuation has completed two bounded REST SSE objectives (6/10; up to four objectives remain under the original cap). Raw Maven ledger started at #144 and is now #158. Substantive no-progress streak remains 0. No subagents were used.

## Implemented

- Added selected-1.1 `RestTransport.streamInput` over REST `POST /message:stream`. It uses the existing bounded `TransportObservation`, versioned REST SSE reader, and `StreamDecoder.decodeJson` to publish known Message/Task/status/artifact frames, future unknown frames and notes, malformed-known failures, and local `StreamError` rejection frames. Explicit 1.0 still fails before the new operation starts.
- Added transport-local tracking for active observations. `RestTransport.close()` cancels the transport's own 1.1 stream reads and leaves an injected `A2AHttpClient` caller-owned. Tests cover an all-unknown held-open stream and two REST transports sharing one injected HTTP client.
- Changed selected-1.1 `subscribeToTask` to use the versioned REST reader over GET and bridge known events to the existing callback signature. Ignored frames do not fabricate callback events. Explicit 1.0 retains its legacy listener and POST body path.
- Added a source-compatible `GetBuilder.getAsyncSSECallStatusAware` default and implemented cancellable GET SSE in JDK, Vert.x, and Android HTTP clients. Status-aware GET/POST SSE keeps non-2xx bodies separate from SSE decoding; the resulting `A2AClientHTTPError` retains status/body/headers and REST maps it through `RestErrorMapper`, including typed HTTP 409 `TaskGenerationMismatchError`.
- Did not enable `UNKNOWN_VARIANT_SKIP` or update provider capability claims. Cursor observation remains unimplemented as assigned.

## Raw Maven ledger

Commands ran in `/Users/kabir/.codex-api/worktrees/3053/a2a-java` and completed within the 1200-second command limit. Maven printed existing Java 17/Quarkus plugin and unversioned-plugin warnings; they did not block the builds.

| Raw # | Command selector | Result |
| --- | --- | --- |
| 145 | `mvn -B -ntp -pl client/transport/rest -am -Dtest=Phase2PublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test` | Test-source compile setup errors (`A2AHttpClient.close`, Java 21 `List.getFirst`); fixed before expected feature RED. |
| 146 | Same selector | Expected RED for missing `streamInput`; existing 1.1 callback subscribe also failed to deliver future+known frames on its legacy reader. |
| 147 | `mvn -B -ntp -pl client/transport/rest -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` | Compile correction: unnecessary checked `JsonProcessingException` in stream setup catch. |
| 148 | Same selector | Compile correction: NullAway required a checked non-null selected-version local in the raw SSE decoder. |
| 149 | Same selector | 32 tests; 3 failures. Investigation identified a pretty-printed multi-line JSON SSE test fixture and a MockServer expectation collision. |
| 150 | Targeted `Phase2PublicClientTest#oneOneStreamInputRetainsIgnoredFutureFrameThenKnownMessageUnderFiniteDemand` selector | BUILD SUCCESS, 1 test. |
| 151 | REST gate selector | 32 tests; 2 test-only issues (MockServer first-match response and asynchronous 1.0 POST verification timing); fixed. |
| 152 | `mvn -B -ntp -pl client/transport/rest -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` | BUILD SUCCESS, 32 tests (REST `Phase2PublicClientTest` 20, REST `SSEEventListenerTest` 12). |
| 153 | `mvn -B -ntp -pl http-client,extras/http-client-vertx,extras/http-client-android,client/transport/jsonrpc -am -Dtest=JdkA2AHttpClientTest,VertxA2AHttpClientIntegrationTest,AndroidA2AHttpClientSSETest,Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` | BUILD SUCCESS; JDK 20, Vert.x integration 18, JSON-RPC public/listener 38, Android SSE 23, shared SPI SSE 16. |
| 154 | `mvn -B -ntp -pl client/transport/rest -am -Dtest=Phase2PublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test` | BUILD SUCCESS, 21 REST raw adapter tests, including all-unknown held-open cancellation and two transports sharing the injected HTTP client. |
| 155 | `mvn -B -ntp -pl client/transport/spi,client/transport/jsonrpc -am -Dtest=TransportObservationTest,Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` | BUILD SUCCESS; shared observation 10, shared SPI SSE 16, JSON-RPC public/listener 38. |

## Files changed for this objective

- `.claude/architecture/a2a-1.1/phase-2/checkpoints/t6b-rest-sse.md`
- `client/transport/rest/src/main/java/org/a2aproject/sdk/client/transport/rest/RestTransport.java`
- `client/transport/rest/src/main/java/org/a2aproject/sdk/client/transport/rest/sse/SSEEventListener.java`
- `client/transport/rest/src/test/java/org/a2aproject/sdk/client/transport/rest/Phase2PublicClientTest.java`
- `http-client/src/main/java/org/a2aproject/sdk/client/http/A2AHttpClient.java`
- `http-client/src/main/java/org/a2aproject/sdk/client/http/JdkA2AHttpClient.java`
- `http-client/src/test/java/org/a2aproject/sdk/client/http/JdkA2AHttpClientTest.java`
- `extras/http-client-vertx/src/main/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClient.java`
- `extras/http-client-vertx/src/test/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClientIntegrationTest.java`
- `extras/http-client-android/src/main/java/org/a2aproject/sdk/client/http/android/AndroidA2AHttpClient.java`
- `extras/http-client-android/src/test/java/org/a2aproject/sdk/client/http/android/AndroidA2AHttpClientSSETest.java`

## Remaining scope and risks

- This checkpoint covers the REST SSE slice and shared HTTP GET SSE seam only; it does not mark T6b complete. Provider capability declarations, public `Client` feature exposure, and independent final acceptance review remain pending. `UNKNOWN_VARIANT_SKIP` must stay disabled until the complete public gate passes.
- Cursor/long-poll operations remain disabled. No T6c/T7, commit, push, or PR work occurred.
- The status-aware SSE error path collects non-2xx bodies. Android retains its existing 10 MB limit; JDK `BodySubscribers.ofString` and Vert.x `response.body()` collect the full error response body.
- Focused tests passed; no full reactor build was run, and Maven skipped Spotless checks by project configuration.

## Callback cancellation correction — 2026-11-12T19:54Z

- #156: REST lifecycle test source needed an import correction; fixed before the expected behavior check.
- #157: Expected RED reproduced: closing a selected-1.1 callback subscription delivered `errorConsumer(null)` once because cancelled observations signal Flow `onComplete`.
- Corrected `RestTransport.subscribeToTaskOneOne` so it delivers the null success callback only when the recorded `TransportObservation.completion()` outcome is `COMPLETED`. `FAILED` still reaches `onError`, and the existing atomic guard still limits terminal callbacks to once.
- #158: `mvn -B -ntp -pl client/transport/rest -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` — BUILD SUCCESS; 34 tests (REST `Phase2PublicClientTest` 22, REST `SSEEventListenerTest` 12; shared SPI SSE listener 16).
