# T6b REST P2 #1 status-aware SSE error-body bound — 2026-11-12T20:40Z

Authorized window: 2026-11-12T20:28:06Z–21:28:06Z. This is coherent objective 2/10 in the new authorization; the preceding Sol full review is objective 1/10. Earlier 8/10 history remains in its prior checkpoints and is not replaced. Requested implementer: `gpt-6-luna`, medium reasoning; observed runtime metadata unavailable. Raw Maven ledger started at #166 and is now #179. Substantive no-progress streak remains 0. No subagents were used.

## Implemented

- Bounded only status-aware non-2xx SSE response bodies at 64 KiB, before materializing a complete response body or line:
  - JDK uses a byte-stream `BodySubscriber`, requests chunks incrementally, retains at most the cap, and cancels the subscription on the first byte beyond it.
  - Vert.x consumes response `Buffer` chunks, retains at most the cap, and resets the request on overflow.
  - Android reads bytes in bounded chunks and closes the error stream immediately on overflow; the existing `BufferedReader.readLine()` helper remains unchanged for legacy and ordinary HTTP paths.
- Overflow sends one `IOException` whose message identifies the limit and whose cause is `A2AClientHTTPError(status, message, null, headers)`. Status and response headers are retained, the body is omitted to prevent parsing truncated JSON, and the SSE event callback is not invoked.
- Small status-aware HTTP 409 bodies continue through the existing REST mapper and retain typed `TaskGenerationMismatchError` behavior. The legacy non-status-aware SSE paths and REST 1.0 behavior are unchanged.
- Added status-aware GET and POST oversized-body tests for each implementation. Each sends a 2 MiB response containing one long line with no newline, streams slowly, and checks one error, no event/completion, 500 status, the custom response header, null body plus too-large message, and peer-observed early connection closure.

## Raw Maven ledger

All invocations ran from `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, each within the 1200-second limit. Counts below include ordinary successful tests plus the new tests. Red runs intentionally timed out while proving the previous implementations did not cancel boundedly. Maven consistently showed existing Java 17/Quarkus compatibility and missing plugin-version warnings. Spotless checks were skipped by project configuration.

| Raw # | Selector / command | Result |
| --- | --- | --- |
| 167 | `mvn -pl http-client,extras/http-client-vertx,extras/http-client-android -Dtest=JdkA2AHttpClientTest,VertxA2AHttpClientIntegrationTest,AndroidA2AHttpClientSSETest -Dsurefire.failIfNoSpecifiedTests=false test -am` | Expected RED: JDK class 22 run, 0 failures, 2 errors, 0 skipped; both new GET/POST tests timed out at the 5 s completion wait. Vert.x and Android modules were skipped after the JDK module failed. |
| 168 | `mvn -pl extras/http-client-vertx -Dtest=VertxA2AHttpClientIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test -am` | Expected RED: 20 run, 0 failures, 2 errors, 0 skipped; both new GET/POST tests timed out at the completion wait. |
| 169 | `mvn -pl extras/http-client-android -Dtest=AndroidA2AHttpClientSSETest -Dsurefire.failIfNoSpecifiedTests=false test -am` | Expected RED: 25 run, 0 failures, 2 errors, 0 skipped; both new GET/POST tests timed out at the completion wait. |
| 170 | Combined JDK/Vert.x/Android selector above, after first implementation edits | Compile failed because the JDK helper had been inserted inside a method. No tests ran; the helper was moved to class scope. |
| 171 | `mvn -pl http-client -DskipTests compile -am` (diagnostic output piped through `rg`) | Sandbox denied a write to `spec/target/classes/META-INF/beans.xml`; no source or test result. Retried with required worktree access. |
| 172 | `mvn -pl http-client -DskipTests compile -am` | BUILD SUCCESS; JDK production code compiled. |
| 173 | `mvn -pl http-client -Dtest=JdkA2AHttpClientTest -Dsurefire.failIfNoSpecifiedTests=false test -am` | BUILD SUCCESS: JDK class 22 run, 0 failures, 0 errors, 0 skipped. This includes small GET/POST 409 body preservation and both oversized-body cases. |
| 174 | `mvn -pl extras/http-client-vertx -Dtest=VertxA2AHttpClientIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test -am` | Compile failed because `ByteArrayOutputStream.write(byte[])` required handling `IOException`; switched to the offset/length overload. No tests ran. |
| 175 | Same Vert.x selector | 20 run, 0 assertion failures, 4 errors, 0 skipped: both new oversized tests and existing small GET/POST 409 tests timed out. Root cause: the old outer completion CAS was marking the call complete before the newly registered response handlers ran. Changed only that guard so handlers register while the call is pending. |
| 176 | Same Vert.x selector | BUILD SUCCESS: 20 run, 0 failures, 0 errors, 0 skipped; small 409 and oversized GET/POST cases pass. |
| 177 | `mvn -pl extras/http-client-android -Dtest=AndroidA2AHttpClientSSETest -Dsurefire.failIfNoSpecifiedTests=false test -am` | BUILD SUCCESS: 25 run, 0 failures, 0 errors, 0 skipped; small 409 and oversized GET/POST cases pass. |
| 178 | `mvn -pl client/transport/rest,client/transport/jsonrpc -Dtest=RestErrorMapperTest,RestTransportTest,SSEEventListenerTest,Phase2PublicClientTest,JSONRPCTransportTest,JSONRPCTransportStreamingTest -Dsurefire.failIfNoSpecifiedTests=false test -am` | BUILD SUCCESS. REST 65 run, 0 failures/errors/skips (`SSEEventListenerTest` 12, `RestErrorMapperTest` 7, `Phase2PublicClientTest` 22, `RestTransportTest` 24). JSON-RPC 63 run, 0 failures/errors/skips (`SSEEventListenerTest` 19, `JSONRPCTransportTest` 22, `JSONRPCTransportStreamingTest` 3, `Phase2PublicClientTest` 19). Public REST includes the typed HTTP 409 subscribe mapping. Raw full output: `/private/tmp/t6b-rest-error-bound-maven.log`. |
| 179 | `mvn -pl http-client -Dtest=JdkA2AHttpClientTest -Dsurefire.failIfNoSpecifiedTests=false test -am` | BUILD SUCCESS after final callback-completion safeguard: JDK class 22 run, 0 failures, 0 errors, 0 skipped. Raw full output: `/private/tmp/t6b-jdk-error-bound-final.log`. |

## Files changed in this objective

- `http-client/src/main/java/org/a2aproject/sdk/client/http/JdkA2AHttpClient.java`
- `http-client/src/test/java/org/a2aproject/sdk/client/http/JdkA2AHttpClientTest.java`
- `extras/http-client-vertx/src/main/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClient.java`
- `extras/http-client-vertx/src/test/java/org/a2aproject/sdk/client/http/vertx/VertxA2AHttpClientIntegrationTest.java`
- `extras/http-client-android/src/main/java/org/a2aproject/sdk/client/http/android/AndroidA2AHttpClient.java`
- `extras/http-client-android/src/test/java/org/a2aproject/sdk/client/http/android/AndroidA2AHttpClientSSETest.java`
- This checkpoint.

`git diff --check` reported no whitespace errors in the six implementation/test files. Other pre-existing worktree modifications were left untouched.

## Handoff and limits

This checkpoint covers T6b final-review P2 finding #1 only. The separate public REST completion/error/cancel race evidence gap from P2 finding #2 remains for the coordinator to assign or review. T6b is not marked accepted here. No T6c/T7 work, commit, push, PR, or release action occurred. Continue only within the current authorization and existing iteration/time limits; preserve the previous run's 8/10 accounting.
