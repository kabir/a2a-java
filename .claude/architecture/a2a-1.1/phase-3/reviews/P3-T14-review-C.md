Start: Thu Nov 26 09:42:17 UTC 2026 (date -u)
# P3-T14 correction review C (read-only; delta /tmp/t14-correction-delta.patch + probe result files, no Maven run)

Source lines below refer to the current worktree files.

## 1. F-A2-1 reader pool rejection — CLOSED
- `PublicationReconciler.java:466-471`: core=max=4, `allowCoreThreadTimeOut(true)`, keepAlive 5s, `LinkedBlockingQueue(1024)`. Idle threads are still released. Resources stay bounded at 4 threads + 1024 small closures. The 1029th concurrent read is still rejected (`:489`) and becomes AUTHORITY_READ_FAILED. That only happens with >1028 simultaneously watched tasks, which is an acceptable documented-by-code limit.
- `:496-498` `close()` still calls `shutdownNow()`. `PublicationReconciler.close():210` still closes the `AutoCloseable` head.
- Deadline semantics unchanged: `readStarted = now` is set at enqueue (`:376`) and `reconcile():354-364` abandons and fails with AUTHORITY_READ_DEADLINE after the deadline. A read queued longer than the deadline is therefore abandoned. The `result.isDone()` skip (`:479`) makes its queued runnable a no-op once cancelled. After the deadline `failAll` closes the observers, so a stuck store cannot keep re-queueing for that task.
- Test `moreWatchedTasksThanReaderThreadsAreQueuedNotRejectedOrClosed` (test :565-567): 9 tasks, 4 threads blocked, so 5 reads are queued. It asserts that nothing is closed for 500ms, then that all observers are healthy after release. The probe `/tmp/t14-probe-pool.result` (SynchronousQueue) fails at line 567, the no-child-closed condition, so it is discriminating. The 500ms wait is well inside the 5s read deadline; the test is deterministic enough. Weak spot (Info): `started >= 9` can be satisfied by repeat reads of the same tasks, so it does not by itself prove that the queued reads ran. The final health assertions and the probe cover the property.

## 2. F-A3-1 duplicate held generation overflow — CLOSED
- `admit():574-577` returns on `held.containsKey` before charging. `complete():621-623` skips items `<= snapshotGeneration` and duplicates before charging. Charge points are only `held.put` after a successful `tryChargeLocked` (`:581`, `:629`). Release points are unchanged: the `complete()` poll loop `:635-637`, consumption `:597-598`, and the `fail()` clear. No double-charge path remains, and the old charge-then-uncharge compensation is removed. Items that are never charged are never released, so there is no leak and no under-release. The first item is kept, as before (`putIfAbsent` semantics).
- Test `redeliveredHeldGenerationAtTheBufferLimitDoesNotOverflow` (cap 3, hold 2,3,4, redeliver 3) asserts no failure and `bufferedEvents==3`. Probe `/tmp/t14-probe-duplicate.result` fails with BUFFER_OVERFLOW on the old code, so it is discriminating.

## 3. F-A4-1 register() rollback — CLOSED (code); Info: no test
- `register():247-258` removes the observation and the empty watch when `ensureScheduledLocked()` throws. Everything runs under `registryLock`, and `ensureScheduledLocked` already shuts its owned scheduler on failure (`:297-302`), so `schedule` stays null. Nothing is left registered.
- Info (Low, evidence only): no test or probe in the delta forces a rejecting scheduler (grep for RejectedExecution in the events tests is empty). The rollback is verified by reading only.

## 4. F-A4-2 / B5-Low attach failure leaks tapped child — CLOSED
- `DefaultRequestHandler.java:1531-1537` wraps `attach` and calls `queue.close(true)`, then rethrows. This covers register's ISE on a closed reconciler, IAE, and rollback failures. `attach` throws before `installObservation`, so nothing is installed and no observation remains.
- Test `subscriptionOnAClosedReconcilerClosesItsTappedChild` asserts ISE and `getActiveChildQueueCount <= 0`. Probe `/tmp/t14-probe-attachclose.result` fails with "tapped child must be closed", so it is discriminating.

## 5. F-A5-2 failQuietly — CLOSED
- `failQuietly():411-417` catches only `RuntimeException` and logs WARN with the throwable and task id. An `Error` is not swallowed: it propagates to `tickGuarded` (catches Throwable and logs ERROR) or into the `whenComplete` stage. State is not hidden. `fail()` has already set `ended`/`failure` and unregistered before it can throw from `terminateObservation`, so the observation state is accurate, and the other observers of the task are still closed. Limit (Info, not new): if `terminateObservation` throws, that child is left unclosed and no longer reconciled. The previous code had the same outcome, but it also aborted the remaining observers. No test covers it.

## 6. B5-Low InternalError text — CLOSED
- `:709-711` now reads "call GetTask to resynchronize, then resubscribe if the task is not finished." Test :115 is updated to match. The class Javadoc (`:59-62`) still says "'retry or resynchronize with GetTask is needed'; it adds no protocol error code, retryability guarantee ...". The disclaimer is intact. Nit: the "retry" wording in the Javadoc is now slightly looser than the message, which is not a defect.

## 7. B1-Low test and comment — CLOSED
- Test renamed `itemsQueuedBeforeTheAttachAreDiscardedWhenCoveredByTheSnapshot`. It uses `snapshot.generation()` (6) and expects `[7]` for local and remote. This agrees with the pre-install skip `<= snapshotGeneration` (`:621`). The handler comment (`DefaultRequestHandler.java:1528-1530`) is now "not lost and a covered one is not repeated".

## New defects introduced by the delta
- None at Medium or above.
- Info only: a stuck store with 4 hung readers now makes the queued healthy tasks close with AUTHORITY_READ_DEADLINE once the deadline has passed since enqueue, instead of closing at once with AUTHORITY_READ_FAILED. This is bounded and consistent with the documented deadline semantics. Abandoned queued closures (at most 1024) are skipped by workers once dequeued.
- Retained items (F-A1-1, F-A2-2, F-A2-3, F-A3-2/3/4, F-A4-3/4, F-A5-1, B2/B3) are not made worse by the delta. `cancel(true)` on the CompletableFuture still interrupts nothing (F-A2-2), and the new pool does not change that.

## Disposition
Accepted within scope

## Not examined
- Maven test execution; I relied on the implementer's probe result files (pool, duplicate, attachclose), not re-run.
- Files outside the 3-file delta (EventQueue, TaskManager, MainEventBusProcessor, etc.) except `EventQueue.installObservation`/`terminateObservation`.
- A real rejecting scheduler and a throwing `terminateObservation` (no tests exist).
