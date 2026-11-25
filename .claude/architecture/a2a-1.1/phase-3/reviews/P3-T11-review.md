# P3-T11 independent review

## v1 — changes required

Fresh reviewer requested as `gpt-6.2-sol`, medium reasoning, with the normal speed requested for the assignment. The delegation tool accepted the model and reasoning choice. Serving telemetry was unavailable. Reviewed base `28a3930067671433894e187838b1c0968101581b`, v1 manifest SHA-256 `993d393e16076db52b00012001e3750e4ffe2d289af2eb71d00e1acc360160a2`. Verified file hashes, reverse patch application and diff whitespace read-only. The reviewer used the existing tests and ran no Maven command.

| ID | Severity | v1 location | Failure case and correction |
| --- | --- | --- | --- |
| R1 | P1 | `MainEventBusProcessor.java:935`, `BasePushNotificationSender.java:300`, `JpaTaskMutationTransactions.java:159` | The ordered path ignored proposal provenance and sent a final Task to older configurations even for ordinary status/artifact batches. The terminal observer could claim first without this distinction. Preserve ordinary event projection and the versioned proposal summary using immutable provenance across both origins. |
| R2 | P1 | `MainEventBusProcessor.java:797`, `ReplicatedQueueManager.java:301` | A deferred bus callback or stalled executor retained committed payloads outside the dispatcher. Terminal admission bypassed the callback, and a successful duplicate was reported as a failure. Reserve through one bounded seam and coordinate trusted terminal and bus admission by committed identity. |
| R3 | P1 | `OrderedWebhookDispatcher.java:175`, `:232`, `:361` | No-op snapshots counted as zero bytes. Replacement loss markers captured rejected payloads. If a range-3 overflow marker absorbed a duplicate of held range 1, cursor 2 could discard the whole marker. The active-task cap also dropped known ranges above the cursor without accounting. Bound all retained payloads and preserve the unaccounted range without discarding accepted pending work. |
| R4 | P1 | `OrderedWebhookDispatcher.java:403`, `JpaWebhookDispatchGateTransactions.java:276` | An epoch-9 origin could adopt epoch 10 by reading a lane rebased while its batch was queued. Retain the originating epoch during admission, selection and claim. |
| R5 | P1 | `BasePushNotificationSender.java:581`, `OrderedWebhookDispatcher.java:556` | A stalled POST builder could resume after the deadline because the last check preceded builder setup. An executor with `CallerRunsPolicy` could block both lane scheduler threads in HTTP, and a silent discard policy could strand claimed work. Check immediately before POST and isolate scheduled gate work from reliable bounded outbound work. |
| R6 | P1 | `OrderedWebhookDispatcher.java:264`, `:354`, `:420`, `MainEventBusProcessor.java:716`, `ReplicatedQueueManager.java:308` | Gate exceptions wedged task scheduling or completion. A synchronous read exception could leak after input commit or suppress terminal replication. Isolate webhook failures and preserve unresolved durable ownership where the claim outcome is uncertain. |
| R7 | P1 | `WebhookDispatchClaim.java:9`, `JpaWebhookDispatchGateTransactions.java:170`, `:247` | A zero-width abandoned summary could not use canonical claimed-range recovery, and normal release failed after epoch change. Recover the exact no-op claim explicitly after quiescence without advancing the cursor. |
| R8 | P2 | `OrderedWebhookDispatcher.java:316` | A no-op ahead of the cursor could poll indefinitely, and an overflow marker could make it spin on zero-delay rescheduling. Bound the summary's wait without inventing a canonical position. |
| R9 | P2 | `InMemoryTaskStore.java:229`, `extras/task-store-database-jpa/README.md:65`, `docs/content/dev/extra/storage.md:349` | A rejected generation-0 baseline mutation could leave a lane initialized at 2 while the Task stayed at 0. Two persistence examples excluded unlisted classes and omitted both lane entities. Publish the in-memory lane with the successful baseline or mutation, and complete those entity lists. |

The v1 required selector executed 59 tests with zero skips. The PostgreSQL 16 fixture exercised independent gate facades, concurrent range exclusion, gap retirement and canonical claim recovery. The reviewer verified transaction-joined JPA initialization, short separate gate transactions and remote subscriber exclusion in production code. The [handoff](../handoffs/P3-T11.md#v1-verification) records the other v1 regressions. The v1 result is changes required despite passing tests.

All findings were sent to a fresh `gpt-6-luna` medium worker after the reviewer ended and the candidate was released for correction.

## v2 — changes required

The original `gpt-6.2-sol` medium reviewer checked the correction diff and affected risks. Frozen manifest SHA-256 `76df8acb904030447a3d6c36fd6c9ca2be1cd5b5d4474ce3e92912b121525f30`. Both bundle digests, all 41 file hashes, reverse application and diff whitespace passed. Existing logs and fresh XML record 69 required tests and 29 directly affected regressions with zero skips, including a real PostgreSQL 16 run. The review ran read-only checks and inspected the installed Arc processor source.

R1, R4, R5, R7 and R9 closed within their original scope. Trusted proposal and writer-epoch provenance stays local while the replicated batch format retains its existing fields. The ordered sender checks the deadline after POST preparation and uses bounded workers separate from the scheduler. PostgreSQL covers exact zero-width recovery. In-memory baseline rollback and both persistence mappings are corrected.

| ID | Severity | v2 location | Open failure |
| --- | --- | --- | --- |
| R2 | P1 | `ReplicatedQueueManager.java:309`, `OrderedWebhookDispatcher.java:453` | The finalization observer still preapproves the same terminal identity before the bus can decide. Deferred admission has no deadline and a completion racing `running=false` can lose its wakeup. |
| R3 | P1 | `OrderedWebhookDispatcher.java:315`, `:368`, `:543` | The capacity bridge retains rejected `Pending` payloads. Global and sparse-marker saturation can lose a known canonical range. Overflow merging ignores epoch and can discard a remaining suffix. A new-epoch successor arriving while an old-epoch claim is held is dropped without loss accounting. |
| R6 | P1 | `OrderedWebhookDispatcher.java:704`, `MainEventBusProcessor.java:914`, `ReplicatedQueueManager.java:309` | Queue failure can detach a concurrent admission between clearing and map removal. Lazy shared-sender initialization can throw outside webhook failure boundaries and stop subscriber or terminal publication. |
| R8 | P2 | `OrderedWebhookDispatcher.java:492` | An ahead-of-cursor summary still spins at zero delay when the selected canonical item is an overflow marker. |
| R10 | P1 | `OpenTelemetryPushNotificationSenderDecorator.java:52` | Quarkus Arc 3.40.1.1 checks that the `@Delegate` type closure includes every decorated interface. `PushNotificationSender` lacked `OrderedWebhookSender`, causing a definition error when the decorator was augmented. Wrapper unit construction bypasses this check. |

The same `gpt-6-luna` medium worker received these focused corrections after the reviewer confirmed that source-sensitive tools had ended.

## v3 — changes required

The scoped `gpt-6.2-sol` medium re-review verified both bundle digests, all 42 source hashes, reverse application and diff whitespace. Manifest SHA-256 `3af2bffb60ff60b362169e26b2c38ea73175ae2f4b90cb2afc9891226a96840e`. The reviewer inspected source and existing [v3 test evidence](../handoffs/P3-T11.md#candidate-v3). No new build ran in review.

R10 closed after real Quarkus augmentation and the named-executor trace-propagation regression passed. R1, R4, R5, R7 and R9 remain closed in their original scope. The v2 finalization callback bypass, queue-detachment race and zero-delay no-op spin were corrected. Residual cases follow.

| ID | Severity | v3 location | Residual failure |
| --- | --- | --- | --- |
| R2 | P1 | `OrderedWebhookDispatcher.java:521` | A token decided after 10 seconds can still send if the dispatcher sees it already completed. This occurs behind a held predecessor and between polls. Record when admission succeeded and reject late success. |
| R3 | P1 | `MainEventBusProcessor.java:985`, `OrderedWebhookDispatcher.java:181` | Rejecting capacity journals the dropped range without making it actionable at the cursor. If finalization hits the cap before the bus sees the same identity, the bus can admit the journaled range after a slot frees. The cursor can also park on an exact-next recorded loss indefinitely. |
| R6 | P1 | `MainEventBusProcessor.java:900`, `:1062` | Failing to initialize the shared dispatcher with an incapable sender is treated as ordinary absence of the capability, and the bus starts the ungated legacy chain. Keep webhook attempts disabled on initialization failure while publishing the committed result. |
| R8 | P2 | `OrderedWebhookDispatcher.java:464` | At the same cursor boundary, the scheduler prioritizes a later canonical batch over an earlier admitted legacy summary. The canonical batch advances the cursor and makes the summary stale. |
| R11 | P1 | `BasePushNotificationSender.java:563`, `:703` | A normal named thread-pool rejection leaves a shell permit consumed, which can permanently disable handoff after transient saturation. Shutdown also stops the deadline monitor while submitted, never-started work can retain its payload. Preserve conservative handling for ambiguous enqueue-and-throw behavior and revoke pending work on shutdown. |

The scoped findings went to the `gpt-6-luna` medium correction worker after the reviewer ended. The original assignment deadline remains 23:29:50 UTC.

## v4 — changes required

The same scoped Sol reviewer verified 42 paths at manifest SHA-256 `25e64b7aabb6f73e560099833c1abcccd64904ff0c3918409c6407bc47a09c4b`, both digests, reverse patch application and whitespace. Existing tests ran 89 required cases including four on PostgreSQL, 126 stream/proposal/JPA regressions, 42 replication cases, and 68 telemetry/sender cases. R6, R8 and R11 closed within the reviewed scope.

| ID | Severity | v4 location | Residual failure |
| --- | --- | --- | --- |
| R2 | P1 | `OrderedWebhookDispatcher.java:553` | A `CompletableFuture` can publish approval before its dependents record the time. A later-registered blocking dependent can deterministically leave approval visible with an unset timestamp after the deadline. The scheduler may then start the next range. |
| R3a | P1 | `JpaWebhookDispatchGateTransactions.java:293`, `InMemoryWebhookDispatchGate.java:232` | A full journal of future losses can block recording settlement of the current claim. Explicit recovery repeatedly fails for the same quiesced holder. Historical retention must allow settlement without evicting future actionable loss metadata. |
| R3b | P1 | `MainEventBusProcessor.java:978` | The cap check and publication of the rejected identity occur in separate critical sections. If a slot frees between them, the terminal/bus duplicate can become owner before the rejection is cached. |

Sol used read-only source and existing evidence. The focused v5 correction was routed after the reviewer stopped.

## v5 — changes required

The same Sol reviewer confirmed all 42 source hashes, both bundle digests, reverse application and whitespace at manifest SHA-256 `7e3f296428ee93f4afda55954aff1ef805e04550279007d02c58c107c60c1ad4`. Required tests passed 92/92 including five PostgreSQL cases, and replication passed 42/42. The review used read-only checks.

- R2 and R3b closed. The new blocking-dependent test establishes that a positive future result with no observed decision timestamp cannot release a later webhook. The capacity check now records the rejection under the original identity lock.
- R3a's recovery wedge closed. The new tests show that explicit recovery and loss settlement can proceed with 256 recorded future losses.
- R3c remains P1. The actionable journal limit still drops the 257th future range, throwing in `JpaWebhookDispatchGateTransactions.java:399` or returning false in `InMemoryWebhookDispatchGate.java:289`. When the held predecessor settles, the cursor can stop at the unrecorded range. A delayed admission can then send the already dropped identity. The v6 correction removes the artificial cap on actionable coordination entries and preserves the independent historical diagnostic bound.

## v6 — changes required

Sol verified all 42 paths, bundle digests, reverse application and whitespace for manifest SHA-256 `f192a861a2c248f5a8ef8bcd5bdc3bae6e4e5cff487b79abe1cd11995209d9c6`. Existing XML records 92 required tests including five on PostgreSQL 16. R3c closed for actionable accounting. The tests exercise the 257th sparse future range and show that the cursor stops at an unrecorded position.

R12 is P2. A cursor advance can convert more than 256 actionable entries to history, while later insertions only remove one historical entry each. This leaves oversized history in both stores and can expose it through in-memory lane snapshots. In addition, JPA's dispatcher position poll materializes the actionable journal even though its scheduling decisions use only cursor, epoch and holder. Locations `InMemoryWebhookDispatchGate.java:286`, `JpaWebhookDispatchGateTransactions.java:83` and `docs/content/dev/extra/storage.md:206`. The reviewer ran read-only checks. v7 was assigned after review stopped with the original backstop approaching.

## v7 — changes required

Sol verified all 43 file hashes, both bundle digests, reverse application and whitespace for manifest SHA-256 `79704c32d79e8f35d87fc9701da41ec40f96e300833433f2277cec0eac783768`. Existing XML records 94 cases including six on PostgreSQL. The position-only polling change and JPA historical reduction close R12 for those paths.

R13 is P1 in `InMemoryWebhookDispatchGate.java:334`. Ascending removal of precomputed indexes shifts the list, so historical pruning can remove an actionable range. With generation 1 held and recorded losses 2, 3, 1000, then 4–259, settlement reaches cursor 260. Removing two historical indexes can incorrectly remove the still-actionable range at 1000. The current exact-next test did not exercise one settlement reclassifying many ranges. Sol confirmed no tests were running and the narrow reverse-removal correction was routed to Luna within the original deadline.

## v8 — accepted within Sol review scope

The same independent Sol reviewer confirmed manifest SHA-256 `54c63d3822cd00a4477bf523dde4fd5015ab7d76ec0ab84ecac73ee70bfee4ca` across 43 files. Only the in-memory gate and its test changed from v7. R13 closes because reverse removal of the oldest excess historical indexes preserves interleaved actionable entries. The regression verifies cursor 260, actionable generation 1000, and historical ranges 4–259. The final focused selector passed 31/31. The review used existing logs and read-only checks. The full frozen-head verification disposition is recorded in the handoff.
