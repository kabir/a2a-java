# Handoff — intermittent `server-common` test failures (not a P3 task row)

Developer-requested side task, not a row in `task-list.md`; it authorizes no task progression and no commit.
Base `f95cdcc4` (HEAD, branch `support/a2a-1.1.0`). Window 2026-11-26 12:32 UTC onward (two-hour limit given by the
developer; follow-up edits 13:30-13:45 UTC). Implementer: Bob (this context, no subagents). Astra not used; no model
telemetry observable. Independent review: none yet (self-review only, recorded below).

## Result

Only `server-common/src/test/java/org/a2aproject/sdk/server/requesthandlers/DefaultRequestHandlerTest.java` changed.
**No production code changed.** The change is uncommitted. Both defects are races in the tests, not in `server-common`.

## Defect 1 — `interruptedTerminalEnqueueStillReportsAgentFailure`

- Symptom: `assertThrows` "Expected InternalError to be thrown, but nothing was thrown" (blocking send). 3 of 8 full runs
  before the fix; 1 of 6 on `DefaultRequestHandlerTest` alone.
- Cause (from the captured test output, not inferred): the helper `assertInterruptedTerminalEnqueueReportsError` forced
  `queueSize(1)`. `EventQueue.ChildQueue` is bounded by the parent's queue size, and one processed event fans out to two
  child items (initial user message + WORKING status). If the consumer had not dequeued the first before the second arrived,
  the log shows `ChildQueue ... is full. Closing immediately.` then `Cleared 1 events`. The later error event was dropped
  and the blocking call returned a FAILED task read from the store.
- Fix: `queueSize(2)`, and the agent emits one filler artifact after `startWork()` so the main queue is still full when the
  terminal enqueue is interrupted. The helper serves all three `interrupted*` tests.
- Retained limit: not deterministic by construction. The child queue (capacity 2) must have the consumer drain one item during
  the several milliseconds before `releaseWorkingEvent`. A deterministic version needs a "consumer has dequeued" hook.

## Defect 2 — AUTH_REQUIRED tests tapping the queue too early

- Symptom: `testAuthRequired_QueueRemainsOpen` — "First event should be TaskArtifactUpdateEvent" but a
  `TaskStatusUpdateEvent` arrived. 1 failure in about 16 full runs.
- Cause (from the captured output): `onMessageSend` returns on the first consumed event, before `MainEventBusProcessor` has
  distributed the AUTH_REQUIRED status to the child queues. A queue tapped in that window receives the stale AUTH_REQUIRED status
  first.
- Fix: new test helper `watchAuthRequiredDistribution()` installs a `MainEventBusProcessorCallback` (invoked after distribution)
  and returns a latch. The test awaits it (5 s) before `queueManager.tap(...)`. Applied to `testAuthRequired_QueueRemainsOpen`,
  and, after self-review, to the same-pattern siblings `testAuthRequired_Streaming_ContinuesInBackground` and
  `testAuthRequired_Resubscription`. The siblings had not failed in the runs below; they are fixed on the shared mechanism.
- Not changed: `testAuthRequired_NonStreaming_ReturnsImmediately` / `_TaskStorePersistence` (do not tap), and the tap at the
  late-final-event test (~line 2446, blocking send, drained before the tap).

## Evidence (all `mvn -o test -pl server-common`, 984 tests per full run, 0 failures/errors/skips unless stated)

| Phase | Result |
| --- | --- |
| Baseline, no fix | 3/8 full runs failed (all defect 1) |
| Defect 1 fixed, `DefaultRequestHandlerTest` only (73 tests) | 10/10 passed |
| Defects 1+2 fixed (first tests only) | 8/8 full runs passed (an earlier loop had one defect-2 failure before that fix) |
| Same, `DefaultRequestHandlerTest` under CPU load (12 busy-loop shells launched on 6 cores; I did not separately confirm they ran for the whole period) | 12/12 passed |
| Defects 1+2 fixed and both siblings wired, final file | 6/6 full runs passed |

Statistical weight differs: defect 1 occurred about 1 in 4 runs, so 18 clean runs after the fix is strong. Defect 2 occurred about
1 in 16, so clean runs alone are weak support; the fix rests mainly on the log-based cause. Logs were kept in `/tmp/runs`
(not in the repo); nothing there is required to read this handoff.

Process notes: one loop was discarded and restarted because the test file was edited while it ran; the final 6/6 run began
only after the last edit. Two parallel `apply_diff` calls to the same file lost two edits; this was caught by `grep` before
trusting the run, and the edits were reapplied sequentially.

## Self-review findings (open)

1. Defect 1 retained timing limit above.
2. Bookkeeping: `task-list.md` still shows P3-T13, T14 and T16 as "Planned; not started" although commits `aee64459`/`1234c48f`/
   `ec4fa960` and their handoffs exist. Not edited here (outside this task).
3. No independent review of this diff. The task-list workflow expects a separate review session for task rows; this is a
   small test-only side change, so that is the developer's call.

## Next action for the developer

Decide whether to commit the test change (Conventional Commit, for example
`test(server-common): fix races in DefaultRequestHandlerTest`; ask about an issue number) and whether to fix the stale
task-list rows as a separate assignment.
