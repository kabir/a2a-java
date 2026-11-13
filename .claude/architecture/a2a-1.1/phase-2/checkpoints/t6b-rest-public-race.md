# T6b REST public completion/error/cancel race — 2026-11-12T20:41Z

Authorized window: 2026-11-12T20:28:06Z–21:28:06Z. This is coherent objective 3/10 of the new authorization; the earlier Sol review and REST error-body bound remain objectives 1 and 2. Requested implementer: `gpt-6-luna`, medium reasoning; observed runtime model metadata unavailable. No subagents were used. Cumulative raw Maven count is **180** (previous checkpoint: 179). Substantive no-progress streak remains **0**; this objective added missing acceptance evidence and did not reproduce a production defect.

## Implemented

Added `publicRestCompletionErrorAndCancelRaceDuringInFlightEventCallback` to the existing public REST suite. It builds a public `Client.builder(card)` client with REST selected at immutable version 1.1 and a controlled HTTP peer. The peer records and verifies `POST /message:stream`, `A2A-Version: 1.1`, and a REST message request body. It emits a valid protobuf-JSON REST task event, holds the first public event callback, then races malformed SSE data plus a subsequent valid event and peer response closure against `Client.close()`. After releasing the in-flight callback, the test asserts exactly one terminal callback and that no later event callback started. The peer frames use the REST `StreamResponse` representation; this is not a raw transport substitution or JSON-RPC envelope.

The new test passed against the existing REST production implementation. No production race defect was reproduced, so no production code changed and no REST raw selector was needed. This closes the review's public REST race evidence gap for this bounded test; it does not independently accept all of T6b.

## Raw Maven ledger

Raw invocation **#180**, run from `/Users/kabir/.codex-api/worktrees/3053/a2a-java`:

```text
mvn -B -ntp -pl client/base -am -Dtest=Phase2RestPublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Result: exit 0, `BUILD SUCCESS`, all 12 reactor modules successful, total time 13.096 seconds, finished 2026-11-12T20:41:42Z. `Phase2RestPublicClientTest`: **8 run, 0 failures, 0 errors, 0 skipped**, including the new race test. Java 17 selected Quarkus plugin 3.40.1.2 after compatibility warnings; missing Maven plugin-version warnings remain, and Spotless checks were skipped by project configuration. No timeout.

## Files changed in this objective

- `client/base/src/test/java/org/a2aproject/sdk/client/Phase2RestPublicClientTest.java` — added the public REST race test and its controlled peer helper to the existing untracked Phase 2 test file.
- `.claude/architecture/a2a-1.1/implementation-progress.md` — this objective's ledger entry.
- This checkpoint.

All other pre-existing worktree changes were left untouched. No T6a accepted code, REST production code, commit, push, PR, T6c, or T7 work occurred. Preserve earlier checkpoint histories and raw failures; cumulative Maven count is now **180**, not a replacement for the prior ledgers.
