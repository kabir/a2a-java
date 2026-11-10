# Unchanged-code baseline evidence

Worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, HEAD `2817ed78d02d9a6e609dafdf7f5129484edef017`. No production/test sources changed by this session before or during this build. Existing architecture edits were present at start and remain preserved.

## Build and process control

```sh
java -version
mvn -version
python3 /tmp/a2a-phase0-run/bounded.py 1200 baseline-full mvn -B -ntp clean install
```

Temurin 17.0.18 and Maven 3.9.14. Parent compiler source/target/release is 17 and Error Prone/NullAway remain configured; build log records release17 compilation. No alternate Java toolchain was selected.

Started `2026-11-10T13:19:16.151765Z`; finished `2026-11-10T13:29:13.361176Z`; exit 0, 597.21 seconds, no timeout. The 1200-second bound was below remaining run budget. Process-group leader 67363; reusable session 70023 was polled and consumed at completion. No duplicate build or resume command was started. Retained [metadata](evidence/baseline-full.json), [compressed complete log](evidence/baseline-full.log.gz), [exact runner](evidence/bounded.py), and [test report summary](evidence/baseline-test-summary.json).

All 83 reactor modules report SUCCESS. The root build also runs BOM Maven Invoker consumer checks; their results are in the complete log. Building the TCK SUT module does not execute an external target-revision TCK suite.

## Discovered tests

247 Surefire reports plus one Failsafe report (nine OpenTelemetry integration tests), 248 fresh JUnit XML reports total: **3844 discovered, 3721 executed, 123 skipped, 0 failures, 0 errors**. Counts come from XML attributes, not summed console aggregates; only files written since the recorded build start were included. The summary preserves each module/suite and every skipped case/reason. These are baseline 1.0/0.3 results, not 1.1 acceptance.

| Module | Discovered | Executed | Skipped | Failures/errors |
| --- | ---: | ---: | ---: | ---: |
| `client/base` | 45 | 45 | 0 | 0/0 |
| `client/transport/grpc` | 16 | 16 | 0 | 0/0 |
| `client/transport/jsonrpc` | 39 | 39 | 0 | 0/0 |
| `client/transport/rest` | 43 | 43 | 0 | 0/0 |
| `client/transport/spi` | 34 | 34 | 0 | 0/0 |
| `compat-0.3/client/base` | 27 | 27 | 0 | 0/0 |
| `compat-0.3/client/transport/grpc` | 21 | 21 | 0 | 0/0 |
| `compat-0.3/client/transport/jsonrpc` | 36 | 36 | 0 | 0/0 |
| `compat-0.3/client/transport/rest` | 28 | 28 | 0 | 0/0 |
| `compat-0.3/client/transport/spi` | 25 | 25 | 0 | 0/0 |
| `extras/queue-manager-replicated/tests-multi-instance/tests` | 2 | 2 | 0 | 0/0 |
| `extras/queue-manager-replicated/tests-single-instance` | 5 | 5 | 0 | 0/0 |
| `extras/task-store-database-jpa` | 58 | 58 | 0 | 0/0 |
| `server-common` | 613 | 613 | 0 | 0/0 |
| `tests/multiversion/grpc` | 106 | 84 | 22 | 0/0 |
| `tests/multiversion/jsonrpc` | 111 | 111 | 0 | 0/0 |
| `tests/multiversion/rest` | 111 | 93 | 18 | 0/0 |

The actual root POM directly lists `tests/multiversion/{jsonrpc,rest,grpc}`. There are no `tests/pom.xml` or `tests/multiversion/pom.xml` aggregators. These suites use Surefire. Commands suggested by implementation-loop.md remain planning inputs until actually executed.

## Skips and infrastructure

- 108 JSONRPC-specific assumptions in non-JSONRPC binding suites.
- 8 explicitly disabled gRPC HTTP-card/basic-auth cases.
- 3 Vert.x SSEParserConfig unsupported assumptions.
- 2 explicitly disabled Vert.x SSE semantics cases (last event ID, missing final blank line).
- 1 disabled token-auth push sender case.
- 1 disabled custom-components JSONRPC handler case.

The full summary identifies each test, not just these totals. No `-DskipDockerTests` was passed. JPA 58 tests executed, including Postgres 5 and MySQL 6. Kafka single-instance 5 and actual Testcontainers multi-instance 2 executed without skips. This establishes existing topology baseline only; it proves none of the future 1.1 atomicity/epoch/migration/live guarantees.

## Historical fixtures and limits

See [frozen historical probes](fixtures/README.md) for unchanged fixture bytes, source/blob hashes, derived-input notes, candidate commands and actual results, including the stopped attempt-limit breach. The [original 0.3 capture](evidence/unverified-0.3-capture.tar.gz) was unverified at the first checkpoint. After explicit user authorization, a separate run from frozen unchanged SDK binaries matched its 26 files: 24 positive comparisons and two known-failure fingerprints. The archive keeps its historical filename; see [comparison evidence](evidence/historical-0.3-comparison.json). Original archive bytes were then frozen as expected/0.3. The final 1.0 harness also passed 33 positive comparisons plus one known-failure fingerprint. The [isolated SDK binary snapshot](evidence/historical-sdk-runtime.tar.gz) and [runtime manifest](evidence/historical-runtime-manifest.json) prevent later reactor replacement. No harness behavior or baseline codec changed. They are independent frozen wire expectations, not unchanged released-client binary execution or live historical network peers. Their source is pinned before any 1.1 changes and expected outputs are never regenerated with candidate codecs. Current reactor 0.3 clients in public tests are separate evidence and do not by themselves establish frozen compatibility.

Known defects remain separate from intended contracts. Historical cancel decoding without metadata raises a null-metadata exception; some raw 0.3 constants contain invalid trailing commas. The fixture report records exact reproductions/exclusions. The source inventory separately notes descriptor/wire version mismatch, strict response parsing versus lenient discovery, wrapper bypass after exception, and incomplete terminal guards; source inspection is not an executed failure proof.

Existing public baseline tests cover ordinary send/direct Message/get/list/cancel/subscribe/history/interruption/nonblocking, auth and task authorization over current transports; refer to [inventory](client-inventory.md) for actual inherited methods/factories. Future explicit 1.0/1.1 paired clients, capability-aware wrappers, NullAway legacy-caller/API reports and independent binary/provider/switch checks remain required. Neither this green build nor wire roundtrips passes any full A01–A14 criterion. No external 1.1 TCK or certification is claimed.
