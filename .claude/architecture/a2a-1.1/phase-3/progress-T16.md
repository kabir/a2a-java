# P3-T16 progress — public ordinary entry points, views and reliability enforcement

- Base commit: `0e04f44ce` (branch `support/a2a-1.1.0`); worktree `/home/agentuser/a2a-java/.t16/wt`, branch `t16-work`.
- Started: 2026-11-25 17:10 UTC (`date -u`). Backstop 4h → **deadline 2026-11-25 21:10 UTC**.
- Environment deviations (recorded, no gate weakened):
  - `/private/tmp` is root-owned/unwritable in this Linux sandbox and the tool sandbox only allows paths
    inside `/home/agentuser/a2a-java`. The worktree is therefore `/home/agentuser/a2a-java/.t16/wt`
    (`.t16/` added to the original repo's `.git/info/exclude` only); logs `/home/agentuser/a2a-java/.t16/t16-*.log`,
    review brief/frozen files under `.t16/` with the `t16-` names.
  - No `~/.m2` exists; Maven Central is reachable, so Maven runs **online** with
    `-Dmaven.repo.local=/home/agentuser/a2a-java/.t16/m2` (no `-o`, no tail repo).
- Implementer: Bob (this context). Astra not used. No model telemetry observable.

## Log
- 2026-11-25 17:10–~17:45 UTC: setup, read plan/T15 handoff, implemented reducer hooks (`taint()`,
  `markSkippedEvidenceUnavailable()`), `GenerationReliability`, `TaskObservation`/`ObservationUpdate`/`ObservationEnd`,
  `OrdinaryInputObservation`, `ClientInputPolicy` additions and `Client` routing (uncompiled when the session was interrupted).
- **Assignment window reset by the developer, 2026-11-26 09:07 UTC** ("reset the assignment window to fresh since Bob's
  servers went down last night"). The previous 2026-11-25 21:10 UTC deadline lapsed only because of that outage; the
  explicit developer instruction changes the backstop. **New start 2026-11-26 09:07 UTC; new 4h deadline 2026-11-26 13:07 UTC.**
  Earlier work above is retained, not re-done. The interrupted Maven compile (`.t16/t16-compile1.log`) produced no result.

## Evidence log (all cwd `/home/agentuser/a2a-java/.t16/wt`; logs in `/home/agentuser/a2a-java/.t16/`)
Common Maven flags: `-B -ntp -Dmaven.test.failure.ignore=false -Dmaven.repo.local=/home/agentuser/a2a-java/.t16/m2
-Dspotless.check.skip=true -DargLine=-javaagent:<m2>/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar`, always `-am`.
- Narrow iteration runs: `t16-narrow1/2/3.log`; failed ones preserved as `t16-narrow1-preserved.log`,
  `t16-narrow2-preserved.log`, `t16-narrow3-preserved.log` (compile/test-expectation corrections, each fixed at once;
  no defect needed more than one substantive attempt).
- **Required plan command** (`-pl client/base,extras/opentelemetry/client,extras/opentelemetry/client-propagation -am
  -Dtest=OrdinaryInputObservationTest,GenerationReliabilityTest,ExistingOrdinaryEntryPointReductionTest,ClientTaskReducerTest,
  Phase2ClientInputTest,Phase2InputTerminalTest,ClientTransportPolicyTest,VersionedWrapperTest,OpenTelemetryClientTransportTest,
  Phase2PropagationTest -Dsurefire.failIfNoSpecifiedTests=false test`), final rerun on the frozen candidate:
  `t16-required.log`, exit 0, BUILD SUCCESS. Module-qualified counts:
  - `client/base`: 102 (ClientTaskReducerTest 30, ClientTransportPolicyTest 9, ExistingOrdinaryEntryPointReductionTest 31,
    GenerationReliabilityTest 10, OrdinaryInputObservationTest 13, Phase2ClientInputTest 8, Phase2InputTerminalTest 1).
  - `extras/opentelemetry/client`: 33 (OpenTelemetryClientTransportTest 28, **VersionedWrapperTest 5**).
  - `extras/opentelemetry/client-propagation`: 8 (Phase2PropagationTest 2, **VersionedWrapperTest 6**).
  - VersionedWrapperTest therefore ran in BOTH wrapper modules. 0 failures/errors/skips.
- **Full affected modules once on the frozen candidate** (`-pl client/base,extras/opentelemetry/client,
  extras/opentelemetry/client-propagation -am test`, no `-Dtest`): `t16-full.log`, exit 0, BUILD SUCCESS (16 reactor modules).
  Counts: Common 19, Spec 171, JSONRPC Common 114, Spec gRPC 181, HTTP Client 188, Vert.x HTTP 91 (**3 skipped,
  pre-existing**, unrelated), transport SPI 48, jsonrpc 91, grpc 76, rest 85, **client/base 183**, OTel Common 4,
  **OTel client 33**, **OTel client-propagation 8**. No other skips/failures.
  - Earlier full run `t16-full-1-preserved.log` failed one test in `Phase2RestPublicClientTest` (legacy expectation, see
    migrations); `t16-full-pre-review-pass.log` is the passing pre-review run.
- **Mutation probes** (10, scripts `probes2.sh`/`probe6.sh`, logs `t16-probes.log`, `t16-probes-run2-hermetic.log`,
  `t16-probes-p6.log`): baseline and final restore green; every probe fails the named test(s): P1 guard not rejected,
  P2 taint not exported, P3 taint not imported, P4 no-evidence not marked, P5 cursor accepted, P6 terminal not once,
  P7 explicit 1.0 forced through reducer, P8 unary equal-generation check removed, P9 `sendMessage` skips guard check,
  P10 subscribe bypasses observe. First probe run was contaminated by stale classes after restore (preserved
  `t16-probes-run1-contaminated-preserved.log`); later runs `touch` the restored source. Details in `.t16/t16-review-brief.md`.

## Changes outside the plan's file list (recorded)
- `client/base/pom.xml`: test-scope `org.junit.jupiter:junit-jupiter-params` (parent-managed version) — required for the
  plan-mandated parameterized `ExistingOrdinaryEntryPointReductionTest`.
- Three existing tests migrated from legacy `ClientTaskManager` expectations to reducer semantics, as the plan requires
  (no selected-1.1 branch may bypass reduction): `Phase2ClientInputTest` (stream stamp 2→9), `Phase2PublicClientTest`
  (~l.304–341), `Phase2RestPublicClientTest` (~l.293). Behaviour: after a skipped unknown variant the view is uncertain and
  later updates are delivered but not applied to `TaskUpdateEvent.getTask()`.
- Both OTel `VersionedWrapperTest` files gained two tests each (skipped evidence through wrappers, `observe`/`sendInput`/`streamInput`).
  Wrapper main code unchanged.

## Review
- Reviewer A (public API additivity, 1.0 path, completion-once/cancel races): **Accepted within scope**
  (`reviews/P3-T16-review-A.md`). Reviewer B (taint lifetime, guard rejection before I/O, wrapper evidence,
  NOT_ASSERTED/INCOMPLETE): **Accepted within scope** (`reviews/P3-T16-review-B.md`). Fresh read-only Bob subagents.
- No Medium+ findings → no correction round. Retained: A4 Low, A3 Info, A5 Info, B1 Info. B2/B3 Low (test gaps) closed by
  adding the artifactUpdates-guard test and the `streamInput` wrapper-evidence tests (test-only; no re-review required).
  Frozen candidate refreshed after those test additions (`t16-files.txt`, `t16-sha256.txt`, `t16-candidate.patch`); final
  required and full runs above are on this candidate.

## Status
Completed before deadline (2026-11-26 13:07 UTC). Single commit follows.
