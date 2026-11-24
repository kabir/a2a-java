# P3-T10 review record (fresh Bob contexts; not Sol, not independent-model reviews)

Base `bcc3bdef2`. Candidate frozen 2026-11-24 15:47 UTC. Hash-list sha256 `d78d334e768a3274eaffe73abe01e70ef5b319348ce9835d6da9b69756800c0f`.
Files `/private/tmp/t10-files.txt`, hashes `/private/tmp/t10-hashes.txt`, patch `/private/tmp/t10-candidate.patch`.

## Ledger

| Round | Candidate hash | Reviewer | Disposition |
|---|---|---|---|
| 1 | `d78d334e…c0f` | fresh Bob subagent | Incomplete evidence (no blocker; reading only) |
| 2 (v1→v2 correction) | `6888f349…201` | fresh Bob subagent | No disposition written (subagent returned nothing); superseded by A/B/C |
| 3 (Kafka/Gson/CDI) | `6888f349…201` | fresh Bob subagent | Only R3-A (Kafka notes) written; superseded by A/B |
| 4 (correction + unexamined) | `6888f349…201` | fresh Bob subagent | Header only; superseded by A/B |
| A (sequencer, Gson, terminal guard, gating; file `P3-T10-review-A.md`) | v2 `6888f349…201` | fresh Bob subagent (resumed 16:43 UTC) | Changes required: A2-1, A3-1, A3-2, A1 test gap (A5/A6 not examined) |
| B (server-common, CDI, JPA, strategy; `P3-T10-review-B.md`) | v2 `6888f349…201` | fresh Bob subagent | Changes required (mild): B1-1 finality predicates, B5-1 note |
| C (correction of A/B; `P3-T10-review-C.md`) | v3 `03b42ad2e25b735257a361af63b5a98cd2331018f789759e1a90d5cb53d0a9d3` (before two test-only additions) | fresh Bob subagent | Accepted within scope (reading only; ran no Maven); F1/F2 test-strength notes closed afterwards, F3/F4 retained |

### Resolution of A/B/C findings (v3)
- A2-1 CLOSED: `CommittedBatchSequencer.closedThrough`; a pill that runs with no prior batch makes a later terminal batch STALE. Tests `terminalBatchArrivingAfterItsPillIsDroppedAsStale`, `sequencerDropsATerminalBatchBehindAnImmediatelyRunPillWithoutDeliveringIt`.
- A3-1/A3-2 CLOSED: `offer()` delivers every ready batch and runs the deferred pill even if a delivery throws, rethrowing the first failure. Tests `aFailedDeliveryDoesNotWedgeLaterBufferedBatchesOrStrandTheDeferredPill`, `aFailedDeliveryFollowedByReadyBatchesStillDeliversThemInOrder`.
- A3-3 / C-F4 RETAINED (documented bound): LRU eviction at 4096 tasks loses per-task state.
- A1 CLOSED: `nonStatusBatchEventsSurviveTheEnvelopeRoundTripWithTheirTypes` (artifact, message-update and status events; compared on canonical wire form because Gson normalizes absent part metadata to `{}`).
- B1-1 CLOSED for reachable states: `MainEventBusProcessor` keys hook replication on `!snapshot.status.state.isFinal()`, the same predicate `JpaTaskMutationTransactions` uses to fire `TaskFinalizedEvent`. C-F3 RETAINED: a snapshot in `TASK_STATE_UNSPECIFIED` is replicated by the hook once with no pill while `CommittedTaskBatch.isFinal()` treats it as final on the receiver (not traced further; not a v3 regression; same as R1-F2 edge).
- B5-1 RETAINED note: a third-party strategy without `send(ReplicatedEventQueueItem)` under an epoch fails at send time (documented in the docs).
- B4-1/B3 notes RETAINED (low): batch constructed inside the JPA transaction; literal `null` fifth constructor argument is ambiguous (no in-repo caller).
- A5/A6 were not examined by reviewer A; the docs were re-read and amended by the implementer, and all R1 findings were handled as listed above (not an independent confirmation).



## Review 1

Reviewer: fresh-context Bob reviewer. Start: 2026-11-24T15:48Z (limit +25 min = 16:13Z). READ-ONLY except this file. Findings appended below as found.

Reviewer ended early (turn limit, ~16:04Z). Evidence is from reading `/private/tmp/t10-candidate.patch` plus the current sources of `MainEventBusProcessor`, `EventQueue.MainQueue.enqueueItemInternal/isFinalProposal`, `JpaTaskMutationTransactions`, `JpaDatabaseTaskStore.save` and `ReplicatedQueueManager`. I ran no Maven and no test. All findings below are by reading only.

### Probe answers

1. **No store write on remote receipt: confirmed by reading.** In `MainEventBusProcessor.processEvent`, a replicated item with `committedBatch() != null` takes the early branch. That branch skips `updateTaskStore`, `taskManager.process` and `pushSender`, and releases the semaphore in `finally`, so acquire and release balance once per item.
   - Admission in `enqueueItemInternal`: `carriesUntrustedResult` requires `!item.isReplicated()`, so the envelope is admitted. A local item with `committedBatch()` is rejected by the new guard in `carriesUntrustedResult` (EventQueue.java:~707).
   - `isFinalProposal` returns true for a replicated canonical-final batch, and the terminal event delivered through `deliverCommittedBatch` (`lastInFinalBatch`) satisfies the child's expected-final bookkeeping. I saw no imbalance.
   - The stamped poison pill is NOT a committed batch. It still goes through the legacy `taskManager.process(event, isReplicated=true, …)` path (MainEventBusProcessor.java:~558). I did not verify that a replicated `QueueClosedEvent` never writes to the store there. This is pre-existing behaviour, but the "never calls mutate/save" claim should be tested for the pill (see R1-F6).
2. `getEvent()` returns the last event of the batch. Its uses are `validateEventIds` (the task ID is the same for all events), `isFinalEvent` in `isFinalProposal`, and the legacy path. The legacy path is not reached for batches. Remote `deliverCommittedBatch` delivers every event to children, runs the lifecycle hook only when `childCount > 0`, calls `onEventProcessed` for each event, and calls `onTaskFinalized` when `batch.isFinal()`. I did not examine the MainQueue close and poison-pill interplay beyond this.
3. **Webhooks: no push dispatch for remote copies.** For `isReplicated`, `PushAdmission` is a no-op and `callback.admitPushNotifications` is skipped. Local batches are unchanged. See R1-F5 for how weak the test is.
   - Single replication: `batchLegacyFinal` is true for a terminal batch, so the hook is skipped and only JPA `TaskFinalizedEvent` replicates it. A non-terminal batch is replicated once, through `onCommittedBatch`. I found no double send in the normal path.
   - Zero replication is possible. See R1-F2.
4. **Epoch checks.** In `acceptedByEpoch`, a legacy-only node rejects committed envelopes. Under an epoch the order is own-origin, then epoch equal, then bare-event rejection. An unversioned record cannot pass when `writerEpoch != null`. A stamped pill with `closedAfterGeneration == null` is delivered immediately. A legacy-only node accepts a stamped bare or pill record, which is acceptable. The docs state "self-declared … not broker authentication or fencing". I found no overclaim of authentication or fencing there. The "at-least-once" wording is a separate problem (R1-F4).

### Findings

- **R1-F1 (minor, conditional): later-first reordering drops the earlier batch.**
  - Location: `CommittedBatchSequencer.TaskSequence.offer`/`screen` (`lastDelivered >= 0 && last <= lastDelivered` returns STALE).
  - Scenario: if batch G5..G6 reaches a node first and G3..G4 reaches it afterwards, the first-seen batch becomes the start and G3..G4 is dropped as STALE. Remote subscribers never see those events.
  - Scope: the main sender path may be ordered, but I did not verify that `MainEventBusProcessor` is single-threaded. Broker reordering and joined or caller-transaction commits are not examined. The docs say only "starts from first received batch", not that a later-first arrival discards earlier batches.
  - Gate: ordered publication and no silent loss.
  - Discriminating check: a `CommittedReplicationTest` case that offers G2 and then G1 on a fresh task. Today it drops G1, and the test should state the expected behaviour.
- **R1-F2 (minor/note): zero replication for terminal batches from non-JPA stores.**
  - Location: `MainEventBusProcessor.deliverCommittedBatch`, `replicateWholeBatch = … && !batchLegacyFinal`.
  - Scenario: a terminal batch is replicated only if the store fires `TaskFinalizedEvent` with the batch. Only `JpaTaskMutationTransactions` does. A custom `AtomicTaskStore` under an epoch replicates nothing for terminal batches.
  - Related: `CommittedTaskBatch.isFinal()` treats `UNSPECIFIED` as final, while `isFinalEvent`/JPA use `TaskState.isFinal()` (false for `UNSPECIFIED`). An `UNSPECIFIED` snapshot batch is therefore replicated through the non-terminal hook with no `TaskFinalizedEvent` and no pill, yet receivers treat it as final. Undocumented edge.
  - Check: unit test of `deliverCommittedBatch` with a terminal batch and a `handlesCommittedBatches` hook, asserting the hook call count (0 today), and the same with `UNSPECIFIED`.
- **R1-F3 (minor): a pill arriving before any batch closes immediately.**
  - Location: `runAfterGeneration` (`lastDelivered < 0` runs now).
  - Scenario: a reordered or restarted node gets the pill first, the queue closes, and the terminal batch then goes to a closed or no-child queue. `reversedTerminalBatchAndPoisonPillStill…` covers only the case where an earlier batch was already delivered.
  - Also: `MAX_TASKS` LRU eviction silently drops pending batches and a deferred pill. A dropped deferred pill means the remote queue never closes.
  - Check: test pill-first with no prior batch.
- **R1-F4 (minor, docs): "at-least-once" is overstated on the receiver.**
  - Location: `CommittedBatchSequencer.offer`, "Record before delivering".
  - Scenario: if `enqueueReplicated` throws (timeout, interrupt), the batch is already recorded as delivered, so a broker redelivery is dropped as DUPLICATE. That batch is lost for this node's subscribers.
  - The docs say "at-least-once … ordering and deduplication". This does not violate a gate, but the wording should be narrowed to "broker at-least-once; receive-side failure is not retried".
- **R1-F5 (minor/major for test quality): the webhook suppression assertion is likely vacuous.**
  - Location: `CommittedReplicationTest.remoteBatchIsVisible…`.
  - Scenario: `webhookEvents.isEmpty()` is asserted right after draining children. Local webhook dispatch is asynchronous (`reserveCommittedPushBatch` / executor), and no local control proves that the recording sender is ever invoked. If remote dispatch regressed, the assertion could still pass because it ran before the async send.
  - I did not verify the default callback or how pushes are admitted in this test setup.
  - Check: add a local-batch positive control that awaits the webhook, then assert the remote case after the same wait. Otherwise mutate production so remote admits push, and see whether the test fails.
  - The other probe-10 tests discriminate: no-store-write counts, delayed legacy and wrong-epoch drains (the legacy path would deliver generation 0), own-origin echo, conflict, and duplicate finalization. They assert the delivered generations first, so they are not vacuous.
- **R1-F6 (note, test gap): no test that a received stamped pill never mutates or saves.** `stampedBareEventIsRejected…` asserts store counters at 0, but the delivered-pill test does not.
- **R1-F7 (minor): the committed-batch exemption from the inactive-task guard creates MainQueues on all nodes.**
  - Location: `enqueueReplicated`, `!replicatedEvent.isCommittedBatch()` was added to the guard.
  - Scenario: every committed batch for any task creates a MainQueue on every receiving node, even for tasks the node never served or that have expired. The previous guard existed "to prevent queue creation for expired tasks" for non-Task events.
  - Check: a test with `isTaskActive=false` and a remote batch.
- **R1-F8 (note): `JpaTaskMutationTransactions` batch matches the bus batch.** The batch is built from `next` and `plan.events()`, the same as `CommittedMutation(next, plan.events())`. The `TaskFinalizedEvent` is fired only for a terminal `plan.task()`, and only with a batch when events are non-empty. A legacy `JpaDatabaseTaskStore.save` final still fires without a batch. Under an epoch that sends only the pill with a warning. This is consistent with "do not synthesize", but remote subscribers never see the terminal status for that path. The docs do mention it.
- **R1-F9 (note): CDI wiring.**
  - The `@Inject` constructor now takes `A2AConfigProvider`. `DefaultValuesConfigProvider` is `@ApplicationScoped` in server-common, and `JpaTaskMigration` already injects it. I expect it to be available.
  - The fail-fast `instanceof InMemoryTaskStore` check is plausibly correct for a CDI client proxy, because the proxy subclasses the bean class. The test uses `new InMemoryTaskStore()`, not a proxy. I did not run it under ArC.
  - The check exists only in the `@Inject` constructor. The public `(…, Long writerEpoch)` constructor does not have it.
- **R1-F10 (note): mixed fleets.** A pre-T10 node deserializing a committed envelope gets an item with null `event`. I did not examine its handling.

### Not examined

- The Kafka failure claim: I did not compare `t10-kafka-single-BASE.log` with `t10-kafka-single-preserved.log`.
- `MainEventBusProcessor` threading and ordered-publisher semantics.
- MainQueue close and poison-pill timing.
- `TaskManager.process` handling of a replicated `QueueClosedEvent`.
- Gson serialization of `List<StreamingEventKind>`, beyond the reported `EventSerializationTest`.
- NullAway, Error Prone and import style (probe 8).
- `DynamicBomVerifier`, the multi-instance apps, and the quarkus wiring.
- The joined/shared-transaction lock order and AFTER_SUCCESS timing (probe 6).
- The test logs listed in the task.

### Disposition: Incomplete evidence

Reading found no blocker. The no-second-mutation path for committed batches looks sound, and the docs do not overclaim fencing. R1-F5 (possibly vacuous webhook assertion) and R1-F1 (silent drop of an earlier batch when a later one arrives first) should be resolved or documented before acceptance. R1-F2 to F4 and F7 are minor and need either tests or doc tweaks.

## Review 3 (areas Review 1 did not examine)

Start: 2026-11-24T16:11:23Z (limit 13 min). Fresh-context Bob reviewer. Read-only.

## Review 2 (correction)

Started: 2026-11-24T16:11Z (limit 13 min, until ~16:24Z). Fresh-context Bob correction reviewer.

### R3-A Kafka claim (tests-single-instance; BASE log vs preserved log)

- **Failure is identical.** Both logs fail only `KafkaReplicationIntegrationTest.testA2AMessageReplicatedToKafka:175` with the same message: expected `TaskStatusUpdateEvent` but was `TaskMessageUpdateEvent`. Time elapsed is 0.526 s on BASE and 0.525 s on the candidate. The failure is the first event-type assertion after a non-null replicated event arrived, so the legacy envelope was received and deserialized on both trees.
- **Tests run on each tree.**
  - BASE log: `KafkaReplicationIntegrationTest` only, 4 run, 1 failure.
  - Candidate log: `KafkaReplicationIntegrationTest` (4 run, 1 failure) plus `ReplicatedQueueManagerTest` (1 run, passed), 5 total, 1 failure.
  - The surefire XML in `target/surefire-reports` (candidate run) lists `testKafkaEventReceivedByA2AServer`, `testQueueClosedEventTerminatesRemoteSubscribers` and `testPoisonPillGenerationOnTaskFinalization` as passing. Only `testA2AMessageReplicatedToKafka` has a `<failure>`.
  - BASE log has no per-test pass lines. The three remaining tests are inferred to pass because "4 run, 1 failure" is identical and the failing one is named.
- **R3-A1 (note): the two logs ran different test sets.** BASE lacks `ReplicatedQueueManagerTest`, so BASE and candidate were not run with the same command or selection. This does not affect the "no regression" conclusion for the 4 Kafka tests. It does mean "same module, same invocation" is not strictly true, and the logs contain no command line. The BASE tree identity cannot be verified from the log. Discriminating check: re-run both with an identical `-Dtest=` selection and record the command.
- **Legacy-only profile: exercised.** No `writer.epoch` property appears under `tests-single-instance/src`, so this module runs the legacy-only (null `writerEpoch`) profile. The three passing tests are the end-to-end legacy coverage: server-to-Kafka replication, Kafka-to-server receive, `QueueClosedEvent` termination of remote subscribers, and pill generation on task finalization.
- **R3-A2 (note): the failing test is non-discriminating.** It asserts the legacy event path at line 175 and fails before its later assertions on either tree. Remaining assertions in that test (task id, context id) are therefore not exercised on either tree. The pre-existing failure hides any T10 regression in those lines. The claim "identical failure, no regression" holds only to line 175.
- **R3-A3 (note): the tests-multi-instance modules were not part of the logs.** No claim is made about them.

## Review 4 (correction + areas not examined)

Start: 2026-11-24T16:20:29Z. Fresh-context Bob reviewer. READ-ONLY except this file.
