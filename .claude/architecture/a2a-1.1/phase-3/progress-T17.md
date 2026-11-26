# P3-T17 progress — JSON-RPC production end-to-end mutation slice

- Base commit: `d869e957b310e2585409ac3b3adb166d7823d36c`; branch `work/p3-t17`; worktree `/home/agentuser/a2a-java`.
- Assignment start: 2026-11-26 14:35:00 UTC. Absolute backstop: **2026-11-26 16:35:00 UTC** (not reset by the resumed
  or fresh sessions). `date -u` is authoritative.
- Status: **candidate, NOT accepted.** A separate fresh-context Bob review is required. No shared status row
  (`codex-progress.md`, `bob-progress.md`, task-list) was changed, by instruction. T18/T19 not started.
- Model: requested = Bob's configured model (user authorisation replaces Luna/Sol, implementation and review).
  Observed = identifier unavailable; no telemetry observed. Sol not claimed, Astra not used.
- Environment: Java 17, Maven 3.10.2, online (`-o` fails, see failures), `-Dmaven.repo.local=/tmp/p3-t17-m2`, no root `test.log`,
  no Serena/MCP, no nested agents, no push.

## Prior-session failures (recorded, not hidden)

1. First Bob implementation session (`/tmp/p3-t17-bob-implementation.jsonl`) ended without a final message after
   partially writing the untracked `Phase3ClientGenerationTest.java`. The partial file was retained and completed, not reset.
2. Resumed session (`/tmp/p3-t17-bob-continuation-1.jsonl`) stalled after one successful inspection; no code produced.
3. This fresh-context session (`/tmp/p3-t17-bob-fresh-2.jsonl`) did the implementation below. The Maven `test-compile`
   warm-up (`/tmp/p3-t17-warm-test-compile.log`, exit 0, `-DskipTests`) is **not** acceptance evidence.

## Production changes

- `JSONRPCTransport`: no-cursor `observe` (1.1 only; a cursor or selected 1.0 throws `UnsupportedOperationException`
  before any request); `streamInput` refactored onto the shared `startObservation` helper.
- `JSONRPCHandler`: removed the T17 guards for `timelineLength`, `ifGenerationMatch` and artifacts on `onSendInput`,
  `onSendInputStream`, `onGetTask`, `onListTasks`. Kept delivery-version rejection, `currentGeneration` rejection, cursor
  rejection and all 1.0 gates. **Bug fixed:** `onGetTask`/`onListTasks` rebuilt `TaskQueryParams`/`ListTasksParams` without
  `timelineLength`; it is now forwarded (selected-1.0 `timelineLength` still rejected via `rejectLegacyTimeline`).
- `A2AServerRoutes`: streaming-input route guard is delivery-version only.
- `DefaultRequestHandler.initConfig`: wires the T14 `PublicationReconciler.forTaskStore(...)` when the store is an
  `AtomicTaskStore` (affects all transports). Optional keys `a2a.publication-recovery.interval.millis`,
  `.read-deadline.millis`, `.gap-deadline.millis`; defaults 5 s / 5 s / 10 s (worst case about 30 s).
- **Paired hooks (T13 diagnosis):** no new request-thread commit/publication path was added. The handler forwards to existing
  `DefaultRequestHandler` paths, which already use `MainEventBusProcessor.commitAndPublish`. The lost-publication test commits
  straight to the store as a test-only hook (no production fault hook).

## Tests

- `Phase3ClientGenerationTest` (client/transport/jsonrpc): 7, controlled `HttpServer` peer.
- `Phase3JSONRPCPublicClientTest` (client/base): 11, real `JSONRPCTransport` + controlled peer with ingress counted.
- `JSONRPCHandlerTest`: migrated and extended, 80 run / 1 pre-existing `@Disabled` skip.
- `Phase2BoundaryTest` (reference/jsonrpc): 5.
- Shared fixture `Phase3MutationScenarios` + `Phase3TestProfile` (tests/server-common); extensions to `AgentExecutorProducer`
  (`phase3:` branches), `AgentCardProducer` (`test.agent.user-artifacts`), `TestUtilsBean` (`pushConfigCount`),
  `Public03ClientSupport` (`sendToTask`).
- `Phase3JSONRPCMutationTest` (tests/multiversion/jsonrpc): 12 `@QuarkusTest` scenarios: guard race (one accepted, one typed
  `TaskGenerationMismatchError`, ingress 2); message plus multiple artifacts / artifact-only / USER origin hidden from 1.0;
  malformed inputs with no partial mutation; direct-Message no-task branch (unary + streaming, `returnImmediately` both,
  inline push config); opening message plus artifacts; 1.0 writer / 1.1 subscriber; 0.3 writer / 1.1 subscriber;
  existing-task direct reply; terminal full proposal; metadata-only status; lost final publication then explicit `GetTask`
  (T14 gap failure, 1 ingress, no mutation); unary / streaming-observed / no-cursor-subscribe reduce identical stamps.

## Evidence (final candidate; logs in `/tmp`)

| Check | Result | Log | Time |
|---|---|---|---|
| Plan cmd 1 (`-pl transport/jsonrpc,client/transport/jsonrpc -am -Dtest=JSONRPCHandlerTest,Phase2PublicClientTest,Phase3ClientGenerationTest ... test`) | exit 0. client/transport/jsonrpc 26 (Phase2PublicClientTest 19, Phase3ClientGenerationTest 7); JSONRPCHandlerTest 80 run, 0 fail, 1 pre-existing skip | `p3-t17-final-cmd1b.log` | 20 s |
| Plan cmd 2 (`-pl tests/multiversion/jsonrpc -am -Dtest=Phase3JSONRPCMutationTest ... verify`) | exit 0, 12 run, 0 fail, 0 skip | `p3-t17-final-cmd2b.log`; frozen re-run `p3-t17-final-cmd2d.log` (see handoff) | 36 s |
| client/base | 54 total: Phase3JSONRPCPublicClientTest 11, ExistingOrdinaryEntryPointReductionTest 31, Phase2PublicClientTest 12 | `p3-t17-final-clientbase.log` | 27 s |
| Full `tests/multiversion/jsonrpc -am verify` | BUILD SUCCESS, 151 module tests (139 existing + 12 new) | `p3-t17-final-full.log` | 4 min 2 s |
| server-common `-am test` | 984 pass | `p3-t17-sc-1.log` | |
| reference/jsonrpc `Phase2BoundaryTest` | 5 pass | `p3-t17-ref-1.log` | |
| rest + grpc multiversion `verify` (regression) | BUILD SUCCESS; REST 146 (18 skipped), gRPC 131 (22 skipped); skips not compared to base | `p3-t17-restgrpc-1.log` | |

Note: the full multiversion run finished 15:40:13Z, before the final production-file timestamps (restored byte-identical from
`/tmp/p3t17-final-prod/` after the RED run, hashes unchanged). The frozen-hash re-run of both plan commands is recorded in the
handoff.

## RED / preserved failures

- `p3-t17-red-1-offline-preserved.log`: `-o` environment failure (online required).
- `p3-t17-red-2-preserved.log`: unit RED at base production: `Phase3ClientGenerationTest` 5 errors, `JSONRPCHandlerTest` 2 failures.
- `p3-t17-red-3-e2e-preserved.log`: e2e RED with base `JSONRPCHandler`/`A2AServerRoutes`/`DefaultRequestHandler`/`JSONRPCTransport`:
  12 run, 1 failure + 3 errors (`lostFinalPublication`, `unaryStreamingAndNoCursorSubscribe...`, `twoPublicClientsRace...`,
  `metadataOnlyStatus...`). The other 8 pass at base production because core and handler already support those paths.
  Production restored; 12/12 again.
- Intermediate failures `p3-t17-mv-1..7.log`, `p3-t17-pubclient-1.log`: test/compile corrections, each fixed in one or two
  attempts. **No defect reached three consecutive substantive attempts without progress; the diagnosis stop was not triggered.**

## Source hashes

`/tmp/p3-t17-sha256.txt` (15 files) is reproduced in the handoff.

## Limitations and next actions

See `handoffs/P3-T17.md`. Next: separate fresh-context Bob review; corrections if any; then (not before acceptance) T18/T19.
