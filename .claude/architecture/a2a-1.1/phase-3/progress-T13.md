# P3-T13 progress

- Task: P3-T13 Real shared-authority concurrency and failover evidence (issue #1303)
- Start: 2026-11-25T17:18:09Z (UTC)
- Deadline (3h backstop): 2026-11-25T20:18:09Z (UTC)
- Maven command bound: 30 min each
- Base: 0e04f44ce, branch `t13-work`, worktree `/tmp/t13-wt`
- Environment note: the instruction paths `/private/tmp/...` are macOS paths and do not
  exist on this Linux host (cannot be created: permission denied). All `/private/tmp/t13-*`
  logs/briefs/worktree/maven repo are placed under `/tmp/t13-*` / `/tmp/m2-t13` instead.
- Models: requested/used the configured Bob model only; no Astra selected. Reviewers are
  fresh Bob subagents (not Sol).

## Log
- 2026-11-25T17:18Z start. Worktree `/tmp/t13-wt` (branch t13-work @ 0e04f44ce) created.
- Infrastructure check: `/var/run/docker.sock` -> podman 6.2.0 socket, `_ping` OK. Images pulled:
  `mirror.gcr.io/apache/kafka:4.2.0`, `docker.io/library/postgres:16-alpine` (short name `postgres:16-alpine`
  needed the fully qualified pull to resolve), `mirror.gcr.io/eclipse-temurin:17-jre-jammy`.
- Read: AGENTS.md, plan section P3-T13, handoffs P3-T09/T10/T11, T12 "Not claimed", existing fixture
  (MultiInstanceReplicationTest, both application.properties, executor, Dockerfiles).
- Check 1 (`package` of quarkus-app-1/2, log `/tmp/t13-package-1.log`) was started in the background; the log is
  EMPTY and no Maven process remains. The result is unknown and was never confirmed (not evidence).
- 2026-11-26T09:06Z: the 3h backstop (20:18Z on 11-25) has passed. PARKED per instructions: no further edits,
  no commit. Nothing was changed in the source tree; the only new file is this progress record.
- NOT done: two-node boot proof, Phase3AuthorityMultiInstanceTest, fixture instrumentation, mutation probes,
  review briefs/reviewers, handoff P3-T13.md. No acceptance item is claimed.
- Next action (developer decision): grant a fresh assignment window; re-run check 1, confirm both nodes boot
  with the Phase 3 epoch/webhook configuration (T12 gate is config validation only), then write the test.
- RESUMED 2026-11-26T09:06:53Z on developer's instruction (fresh window; Bob outage). New 3h backstop: 2026-11-26T12:06:53Z. Prior window parked at 20:18Z 11-25 without edits.
- 09:08Z `package` check (quarkus-app-1/2, -am) exit 0, BUILD SUCCESS, log `/tmp/t13-package-1.log`
  (earlier backgrounded Maven was killed when the tool call ended: preserved `/tmp/t13-package-1-killed-preserved.log`;
  relaunched with `setsid nohup`).
- 09:09Z infrastructure proof: existing `MultiInstanceReplicationTest` (baseline, unmodified) on real containers via
  Podman socket: kafka `mirror.gcr.io/apache/kafka:4.2.0`, `postgres:16-alpine`, two Quarkus containers; Tests run 2,
  Failures 0, Errors 0, Skipped 0. Log `/tmp/t13-baseline-multi.log` (copy in `/tmp/t13-evidence/`). Infrastructure is NOT blocked.
- 09:20Z worktree MOVED (`git worktree move`) from `/tmp/t13-wt` to `/home/agentuser/a2a-java/.t13-wt` because the edit tools are
  blocked outside the workspace (developer chose this; `.t13-wt/` added to the original repo's `.git/info/exclude`). Logs stay in `/tmp/t13-*`,
  private Maven repo `/tmp/m2-t13`. Branch `t13-work` unchanged.
- 09:47Z-10:15Z iteration. Run 3 (`/tmp/t13-run-3-preserved.log`) FAILED `pausedPublisherNeverLetsSubscribersSeeReversedGenerations`:
  real defect: subscribers saw `[2,4,5,3,6]` on node A and `[2,4,5,3]` on node B (remote record for generation 3 delivered
  after this node's own generation 4/5; a locally committed batch was never registered with `CommittedBatchSequencer`, and a
  remote batch could overtake a local batch whose replication hand-off was still in flight).
  Fix (replicated core): `CommittedBatchSequencer.localBegin/localEnd`, `ReplicatedQueueManager.sendCommitted` registers the local batch
  and holds remote batches until local fan-out finished. Run 6 (`/tmp/t13-run-6-preserved.log`) showed the hold must end after
  local fan-out, not after `send()`, so a minimal ADDITIVE server-common hook was added (`EventEnqueueHook.onCommittedBatchPublished`,
  called from `MainEventBusProcessor.deliverCommittedBatch` after the fan-out loop). T14 edits this area: listed in the handoff.
  Core module tests after the change: `CommittedReplicationTest` 28, `ReplicatedQueueManagerTest` 18, 0 failures/skips (`/tmp/t13-core-2.log`).
- Run 6 also failed on fixture issues (preserved): Podman `Broken pipe` on `execInContainer` (retry added), restarted node A not yet
  assigned its consumer partition (dead member's session must expire; start now waits for the assignment log), crash test waited for a
  loss that does not occur before the documented recovery (now observes 40 s then recovers).
- 10:15Z-10:46Z runs 7-9 (intermediate failures: diagnostic prints, fixture fixes - Kafka session timeout via env, consumer-id wait, kill retry, console producer idempotence=false) then run 10 GREEN: 12 tests, 0 failures/errors/skips (`/tmp/t13-green-full-run.log`).
- Mutation probes M1-M6 run and restored (`/tmp/t13-mutation-run{1,2,3}-*.log`; results in the handoff); `sha256sum -c` of originals OK, no MUTATION markers.
- 10:47Z FREEZE: `/tmp/t13-files.txt` (21 files), `/tmp/t13-sha256.txt`, `/tmp/t13-candidate.patch`.
- 10:46Z-10:52Z FINAL run on the frozen candidate: `/tmp/t13-run.sh final`: check 1 `package` exit 0; check 2 `verify` exit 0;
  `Phase3AuthorityMultiInstanceTest` Tests run: 12, Failures 0, Errors 0, Skipped 0 (244 s), BUILD SUCCESS. Evidence in `/tmp/t13-evidence-final/` (0 `<skipped>`).
  Source hashes unchanged vs `/tmp/t13-sha256.txt`; artifact hashes in `/tmp/t13-artifact-sha.txt`.
- 10:53Z affected-module run started (`/tmp/t13-affected.sh`): server-common + replicated core `test`, then legacy `MultiInstanceReplicationTest` verify.
- 10:52Z final run on the frozen candidate: check 1 package exit 0; check 2 verify exit 0, `Phase3AuthorityMultiInstanceTest` 12/0/0/0.
- 10:53Z-11:00Z affected modules: server-common 956 tests, replicated core 55 tests (CommittedReplicationTest 28, ReplicatedQueueManagerTest 18), legacy `MultiInstanceReplicationTest` 2/2; all exit 0, 0 skips.
- 10:55Z-11:00Z reviewers A (Accepted within scope, wording) and B (Changes required: claims/documentation level) wrote `reviews/P3-T13-review-A.md`, `-B.md`.
- 11:02Z-11:20Z ONE correction round (test-only/wording): test 6 forged next-generation records + await instead of sleep; test 5 bootId assertion and wording; test 10 asserts B sent nothing during the outage; claim-limit comments on tests 4 and 9. Re-run final2 12/0/0/0; probe M3b (gate logs but accepts) fails test 6 `:606` as intended and was restored (sha verified); final3 (clean evidence) 12/0/0/0, 0 skipped, exit 0, `/tmp/t13-evidence-final3/`. Re-frozen: 21 files, `/tmp/t13-sha256.txt`, `/tmp/t13-candidate.patch`.
- Retained Medium: B4-2 commit-to-registration window (production, T14 area) - documented as a limit in the handoff, not fixed.
- 11:22Z handoff `handoffs/P3-T13.md` written; committing.
