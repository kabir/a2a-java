# T6b REST public-client gate — 2026-11-12T20:01Z

Authorized continuation window: 2026-11-12T19:27:35Z–20:27:35Z. Requested implementer model: `gpt-6-luna`, medium reasoning; observed runtime metadata unavailable. Six coherent objectives had been counted before this gate; this gate brings the count to 7/10, leaving at most three objectives under the original cap. Raw Maven ledger began at #158 and is now #165. Substantive no-progress streak remains 0. No subagents were used.

## Implemented

- Enabled `ORDINARY`, `GUARDED_INPUT`, `ARTIFACT_INPUT`, and `UNKNOWN_VARIANT_SKIP` for REST 1.1 in both `RestTransportProvider` and the selected `RestTransport`. REST 1.0 still reports only `ORDINARY`.
- Added `client/base/src/test/java/org/a2aproject/sdk/client/Phase2RestPublicClientTest.java` using real `Client.builder(card)` clients and controlled HTTP/SSE peers. The public cases cover:
  - explicit and default 1.1 GET/LIST with Task A generations 7/9 around unknown 8, Task B 17/19 around unknown 18, and unchanged pagination;
  - 1.0 guard preflight rejection with zero HTTP requests and 1.0 ordinary-only selected capabilities;
  - unary `sendInput` returning both Task and direct Message, including guard, artifact, tenant, metadata, interceptor header, and version header encoding;
  - typed HTTP 409 generation mismatch with one request/no retry, and malformed known Task input with exactly one error callback;
  - public REST streaming of Task baseline, typed `TaskMessageUpdateEvent`, ignored future variant, status, artifact, and terminal status with retained metadata and one terminal callback;
  - close of an all-unknown held-open public stream, and closing one of two public clients while both share an injected JDK HTTP client.
- The initial public unknown-timeline test failed as expected before capability enablement at the legacy REST protobuf parser (`Cannot find field: futureKind`). It passed after matching provider and transport capability declarations were enabled.

## Raw Maven ledger

All commands ran in `/Users/kabir/.codex-api/worktrees/3053/a2a-java` and completed under the 1200-second per-command limit. Maven consistently emitted existing Java 17/Quarkus version and missing plugin-version warnings. Spotless was skipped by project configuration.

| Raw # | Command selector | Result |
| --- | --- | --- |
| 159 | `mvn -B -ntp -pl client/base -am -Dtest=Phase2RestPublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test` | Expected RED: 1 public test failed because REST 1.1 still used strict legacy parsing for unknown `futureKind`. |
| 160 | Same base selector | Test compilation failed on new test source imports/signatures; corrected. |
| 161 | Same base selector | 5 tests, 1 failure: stream fixture omitted its tenant prefix (`/tenant-stream/message:stream`); corrected. |
| 162 | Same base selector | BUILD SUCCESS, 5 public REST client tests. |
| 163 | `mvn -B -ntp -pl client/transport/rest -am -Dtest=Phase2PublicClientTest,SSEEventListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` | BUILD SUCCESS, 34 REST tests: `Phase2PublicClientTest` 22 and `SSEEventListenerTest` 12. |
| 164 | Same base selector | BUILD SUCCESS, 7 public REST client tests, including held-open close and shared injected-client ownership. |
| 165 | Same base selector | BUILD SUCCESS, 7 public REST client tests, including explicit 1.0 capability check. |

## Files changed for this objective

- `.claude/architecture/a2a-1.1/phase-2/checkpoints/t6b-rest-public.md`
- `client/base/src/test/java/org/a2aproject/sdk/client/Phase2RestPublicClientTest.java`
- `client/transport/rest/src/main/java/org/a2aproject/sdk/client/transport/rest/RestTransport.java` — capability declaration only in this objective; other REST changes are from earlier T6b objectives.
- `client/transport/rest/src/main/java/org/a2aproject/sdk/client/transport/rest/RestTransportProvider.java`

## Remaining gates and limitations

- No public completion/error/cancel race test was added in this slice. Public tests assert one terminal callback for normal stream completion and held-open close; raw REST/SPI tests cover the underlying cancellation and observation lifecycle. Independent Sol review should decide whether a public race test is still needed before T6b acceptance.
- Cursor/current-generation observation stays disabled. This checkpoint does not authorize T6c/T7, commits, pushes, or PR work.
- Focused base and REST commands passed; no full reactor build ran. Existing Spotless checks were skipped by project configuration.
