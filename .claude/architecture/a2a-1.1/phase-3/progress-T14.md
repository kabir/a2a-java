# P3-T14 progress — bounded detection of publication loss

- Issue: #1303. Base commit `0e04f44ce`, branch `t14-work`.
- Assignment start: **2026-11-25 17:08 UTC** (first `date -u` 17:08:31 UTC; progress file written 17:09 UTC, before any edit).
- Backstop deadline (4h): **2026-11-25 21:08 UTC**.
- Models: configured Bob models; Astra not selected. Serving metadata not asserted as telemetry.

## Environment deviations (recorded, not silent)
- `/private/tmp` does not exist on this Linux host and cannot be created without root. File tools are also restricted to the
  workspace root, so the worktree is `/home/agentuser/a2a-java/.worktrees/t14-wt` (branch `t14-work`; `/.worktrees/` is listed in
  the original tree's `.git/info/exclude` so its status stays clean). Logs are written to `/tmp/t14-*.log`.
- `~/.m2/repository` does not exist, so the offline `-Dmaven.repo.local.tail` repository is unavailable. Maven runs online
  against Maven Central with `-Dmaven.repo.local=/tmp/m2-t14` (dependency download only). `-o` is therefore not used.
- No `python3`; scripted edits use the editing tools.

## Log
- 17:09 UTC warm-up `mvn -pl server-common -am -DskipTests test-compile` (dependencies downloaded; exit 0 — `/tmp/t14-warmup.log`).
- 2026-11-25 17:22 UTC: implementation underway (PublicationReconciler, ChildQueue gate, TaskManager read helper written, uncompiled). Session interrupted (Bob servers down overnight).
- **2026-11-26 09:07 UTC: assignment window reset by the developer** ("reset the assignment window to fresh since Bob's servers went down last night").
  New start **2026-11-26 09:07 UTC**; new 4h backstop deadline **2026-11-26 13:07 UTC**. The earlier 2026-11-25 21:08 UTC deadline is superseded by this explicit instruction.
- 09:10 UTC: `mvn -o ... test-compile` BUILD SUCCESS (`/tmp/t14-compile2.log`), including the DefaultRequestHandler edit. Offline surefire is not in `/tmp/m2-t14`, so test runs use online resolution (see deviations).
- 09:20–09:30 UTC: `PublicationGapRecoveryTest` written (25 tests). First run `/tmp/t14-gap1-preserved.log`: 25 run, 1 failure (child-count assertion, -1 means queue already removed), 1 error (test fixture closed an already-removed queue). Both were test-fixture defects, fixed in the test only (no production change).
- 09:27 UTC: required check `/tmp/t14-required1.log` BUILD SUCCESS: server-common CommittedPublicationTest 34, MainEventBusProcessorPushNotificationOrderTest 8, PublicationGapRecoveryTest 25, ProjectingPublisherTest 4 = 71 run, 0 failures, 0 errors, 0 skipped.
- 09:28–09:31 UTC: mutation probes (`/tmp/t14-probe-*.result`), production files restored and verified by sha256 (`/tmp/t14-prod-before-probes.sha`).
- 09:35–09:40 UTC: review brief `/tmp/t14-review-brief.md` copy at `reviews/P3-T14-review-brief.md`; first freeze `/tmp/t14-candidate.patch|sha256`.
  Two parallel fresh-context reviewers: A (`reviews/P3-T14-review-A.md`: Changes required — one Medium, reader-pool rejection under
  more watched tasks than threads; several Low) and B (`reviews/P3-T14-review-B.md`: Accepted within scope, Low only).
- Correction round (one only): reader pool now 4 core threads (`allowCoreThreadTimeOut`) + 1024-slot bounded queue, queued reads skipped when
  already done; duplicate held-generation check before charging the buffer; pre-install items at or below G skipped; `register()` rolled back
  on schedule failure; handler closes the tapped queue if `attach()` fails; `failQuietly` per observation; error text now
  "...call GetTask to resynchronize, then resubscribe if the task is not finished."; test/comment fixes. Added 3 tests (25 -> 28).
  Delta `/tmp/t14-correction-delta.patch`; second freeze `/tmp/t14-candidate2.patch|sha256`.
- Correction probes (`/tmp/t14-probe-pool|duplicate|attachclose.result`): each named test fails when the fix is reverted; production restored by sha256.
- Correction reviewer C (`reviews/P3-T14-review-C.md`): **Accepted within scope**; all A/B findings closed or retained as Low/Info; no new Medium+.
- Required check on the corrected candidate (`/tmp/t14-required2.log`, online Maven, javaagent argLine): exit 0, server-common 74 run = CommittedPublicationTest 34,
  MainEventBusProcessorPushNotificationOrderTest 8, PublicationGapRecoveryTest 28, ProjectingPublisherTest 4; 0 failures/errors/skips.
  (First-candidate run `/tmp/t14-required1.log`: 71 tests, exit 0.)
- Full server-common on the first candidate (`/tmp/t14-full1.log`): BUILD SUCCESS, 981 tests.
- Full server-common on the corrected candidate:
  - `/tmp/t14-full2-preserved.log` (preserved failure): 984 run, 1 failure `DefaultRequestHandlerTest.interruptedTerminalEnqueueStillReportsAgentFailure`;
    no reconciler is configured in that test (the new code is opt-in); it passed in full1 and full3. Recorded as an unrelated timing flake, not changed.
  - `/tmp/t14-full3.log`: exit 0, BUILD SUCCESS, server-common **984 run, 0 failures, 0 errors, 0 skipped**. The only skips in the reactor (3) are in the
    upstream `http-client-vertx` module (`VertxA2AHttpClientSSETest`), not in server-common.
- 09:48 UTC: `sha256sum -c /tmp/t14-candidate2.sha256` OK for all 7 files; candidate unchanged since the full3 run. Handoff written; committing.
