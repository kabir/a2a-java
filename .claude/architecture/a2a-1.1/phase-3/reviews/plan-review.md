# Phase 3 independent plan correction re-review — 2026-11-17

Disposition: APPROVED at the plan level for separately developer-assigned,
bounded implementation tasks. All three original findings are closed. No runtime
implementation or verification is accepted by this review; the task reviews and
real-infrastructure evidence gates remain mandatory.

## Assignment and evidence provenance

- Workspace: `/Users/kabir/.codex-api/worktrees/3053/a2a-java`;
  branch `support/a2a-1.1.0`; inspected HEAD `b99a29152`.
- Requested reviewer/settings: Sol, High reasoning, normal speed. High explicitly
  overrides the standing medium default for this assignment. Exact serving
  model/settings telemetry is unavailable and is not asserted as verified.
- First recorded clock: 2026-11-17 15:59:48 UTC. Two-hour assignment backstop:
  17:59:48 UTC. This is a focused correction re-review, not a repeated phase audit.
- Initial status: modified `task-list.md`, untracked `phase-3/`, and untracked
  `test.log`. No original on-disk `reviews/plan-review.md` existed when checked.
  The original findings were supplied in the conversation; their substance and
  original disposition are retained below. If a review file appears before this
  report is saved, append this report without replacing that file.
- Reviewed plan SHA-256:
  `19effbda642e133c0265befc4dc98f465dfd8dedc80de305f582b1dae34c2f54`.
- Reviewed task-index SHA-256:
  `1cf7ce6645e74547aff52ef781bd6d96adcb19f879afcc99e8913f209bb76678`.
- Preserved `test.log` SHA-256:
  `bfa5d13b2bc8ac8702d4a1f6b42b622004d922a2ee28d54fcfe939eacd5495ba`.
- Read applicable AGENTS.md instructions, the plan's correction record and affected
  tasks, task index, standing workflow/model/time limits, and relevant architecture
  contracts. Inspected actual client, transport/wrapper, sender, config-store and
  bus/store seams. Used read-only git, source reads/searches and hashes. No agents,
  builds, tests, Phase 2 verification, runtime edits, commits or external writes.

## Original findings and correction dispositions

The original review recommended revising the plan before implementation:

1. **P1 — Existing selected-1.1 entry points bypass reduction/reliability: CLOSED.**
   T16 now explicitly includes every sendMessage overload, unary/streaming,
   callback sendInput, sendInputObserved and existing no-cursor subscribeToTask.
   They share reduction and a Client-owned task reliability registry. T17–T19
   require each binding to prove the existing-entry-point matrix and cross-entry
   zero/equal-generation taint before a subsequent guarded send can perform I/O.
   Explicit 1.0 remains on its historical path; callback shapes/contracts remain.

   This addresses the actual bypasses in `Client.java:329` and `:825`, which
   currently construct/use ClientTaskManager separately. The plan uses existing
   decoded sendInput/streamInput companions and adds only null-cursor SPI observe
   behavior for the built-ins. VersionedClientTransport already has this optional
   method; both telemetry wrapper families already forward it. Custom-provider
   callback fallback must retain wrappers and conservatively expose missing
   evidence, as now required. No public Client.observe or retained-cursor recovery
   is required for this correction. Implementation must preserve synchronous unary
   sendMessage exception/callback timing when adapting its asynchronous companion.

2. **P1 — Independent writer webhook chains can overtake: CLOSED.**
   T03/T10 now route originating bus/finalization output into new T11's common
   shared-store lane. Remote subscriber copies cannot independently send. T12
   rejects the shared profile without a compatible gate/sender. T13 separately
   requires the real paused-after-claim/before-HTTP two-node trace, duplicate and
   finalization cases, recovery, and independently recorded attempt starts.

   The mechanism fixes the original missing cross-node dispatch boundary; it no
   longer relies on MainEventBusProcessor's JVM-local pushNotificationChains.
   See the detailed safety/availability assessment below. This closes a planning
   finding, not A06/A13 runtime acceptance.

3. **P2 — Existing-task enrollment partial effects unspecified: CLOSED.**
   T06–T08 now distinguish hidden new-task provisional enrollment from observable
   existing-task writes. Guard failure/known non-commit does not imply config
   rollback; the original typed input error is retained and supported error
   details/local diagnostics report known task/config IDs. Indeterminate outcomes
   retain enrollment. Concurrent replacement tests prohibit delete/restore
   compensation. Shared-transaction rollback and independent-store partial effects
   have separate evidence requirements.

   This agrees with `architecture.md:278` and the actual setInfo-returned config
   identity and independent/JPA transaction seams. No new protocol error kind,
   receipt or required wire field is introduced. Read the earlier T08 sentence
   about rollback exposing no config in its shared-transaction context; the new
   explicit independent-adapter paragraph governs independent stores.

## Shared webhook lane assessment

- **Initialization:** T11 initializes the cursor atomically with new task creation
  at 1, or migrated baseline plus 1 under the writer fence. It cannot use the first
  arriving replication batch as its starting watermark. All canonical positions,
  including filtered/no-config positions, participate in accounting.
- **Ordering and duplicate exclusion:** A claim compares storage epoch, exact
  next generation and absence of an active holder before HTTP. Completion compares
  the exact token/range/epoch after work settles. N+1 can commit while N owns the
  lane but cannot dispatch. Common bus/finalization identity prevents duplicate
  attempts. Ordering is SDK attempt order; remote receipt/processing order and
  exactly-once delivery are expressly excluded.
- **Stale-worker prevention:** A token check only at completion would be
  insufficient. The corrected plan also forbids takeover while an old worker can
  resume outbound requests. Expiry, observer timeout or DB/broker credential
  revocation alone cannot authorize takeover. Claimed-range recovery requires
  demonstrated worker/process quiescence or fencing plus conditional metadata
  updates. Unclaimed-gap retirement atomically excludes a competing claim and
  makes later arrivals fail the cursor check before sending.
- **Actual asynchronous seam:** BasePushNotificationSender currently schedules
  per-config CompletableFutures and waits on an aggregate future. Its interrupted
  wait returns false while child work may still run (`:225–244`). A boolean return
  or cancelled wrapper future therefore cannot by itself release the new lane.
  T11 explicitly changes this seam to retain ownership until actual dispatch work
  settles/quiesces, and rejects incapable adapters. These are necessary task
  acceptance checks, not properties of today's sender.
- **Bounds:** The plan sets 30-second local attempt and 10-second missing-range
  admission deadlines, and 256 events/4 MiB pending per task. Built-in config
  stores also have an existing configurable per-task config cap (default 100),
  so the actual seam is not inherently an unlimited config list. Tests must cover
  queued as well as running sender work, blocked-owner overflow/drop behavior,
  and incapable custom adapters. The payload bound does not by itself prove
  executor, pagination or diagnostic-retention bounds. Those remain within T11's
  explicit bounded-adapter/dispatcher acceptance, with T13 real-node evidence.
- **Availability:** An abandoned claimed lane can block that task's webhooks
  indefinitely until operational recovery proves quiescence. The 30-second
  attempt deadline is not a guaranteed lane-recovery deadline. This is a stated
  safety-over-automatic-recovery tradeoff, acceptable for the proposed experimental
  best-effort profile. Mutations and subscriber delivery must remain independent;
  pending payloads may be lost within the documented bounds. Operational docs and
  tests must preserve this limitation, including queued pre-HTTP work. Recovery
  cannot be relabeled as automatic failover or inferred from a storage epoch.
- **Delivery scope:** Durable cursor/claim/loss metadata is coordination metadata,
  not a payload outbox. Payload loss, uncertain attempts, no late retry and manual
  retirement remain explicit. No durable-delivery guarantee is introduced, and
  no Phase 4 cursor/replay requirement is needed to make this lane safe.

## Regression assessment and implementation checkpoints

No blocking regression was identified in these plan corrections. Two wording/seam
checks should be made concrete in the already-required implementation tests:

1. The mutation contract returns one final committed Task snapshot for a batch,
   while individual canonical events have consecutive generations. Interpret
   T11/T13's “matching event/snapshot generations” as correct batch provenance:
   an early event's generation need not equal the batch's final Task generation.
   Assert an actual multi-generation batch, preserve its final post-commit snapshot,
   and never fetch a later unrelated Task or fabricate intermediate persisted
   snapshots. If per-event intermediate views are desired, define that additional
   contract before freezing T01/T03; it is not supplied by the current record.
2. T16's optional-provider callback fallback must be for an unsupported optional
   operation, not a general retry after an opening failure or a wrapper rejection.
   Keep the explicit no-wrapper-bypass rule and no duplicate subscription I/O.

These checkpoints follow existing explicit contracts; neither reopens an original
finding or authorizes broader implementation. G1/G2 final claims, G3 cursor and
repeated-snapshot residuals, G4/G6 normative residuals, G5 live outcomes and all
real-infrastructure/independent implementation gates remain open as documented.

Next action: the developer may select one bounded implementation task from the
revised plan. This review does not start that task or authorize a commit.
