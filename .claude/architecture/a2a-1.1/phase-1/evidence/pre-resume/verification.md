> Current checkpoint: **Phase 1 incomplete; no commit approval requested.** The user explicitly approved the SDK-owned Netty channel/API/backend/lifecycle design for both 1.0 and 1.1. Its partial implementation passed 14 focused tests, but independent review found an admitted global load-balancer tracer can still mutate metadata after the SDK assertion. A newly allocated provider channel can also leak on descriptor-validation failure. Existing raw-channel/factory callers and tests still require migration. Stronger zero-server-stream assertions added after the focused build remain unrun. Earlier readiness/design-pending statements below are historical and superseded.
>
> Correction iteration 4 is closed: **4/4 additional iterations, 12 cumulative Phase 1; no-progress 0**. The owned attempt is conservatively the third materially failed F1 implementation; **the three-attempt safeguard stops further F1 work**. This is a source-established design failure, not a third executed failing probe. The original correction deadline remains 2026-11-10T18:33:12Z. Further implementation requires an explicitly authorized revised approach and renewed budget; material design approval already granted does not reset either limit. No commit, push, PR or Phase 2 work occurred.

# Phase 1 correction verification — incomplete

**F2 REST subscription and F3 native 0.3 context are corrected and independently
reviewed. F1 strict gRPC pre-I/O enforcement remains open, with a known failing
acceptance test. No complete passing build or commit readiness is claimed.**

Branch support/a2a-1.1.0, unchanged accepted Phase 0 HEAD
`e468dd70a415d17c19ae70d75750b6464d983ad1`. Target remains experimental RC
`78e1d6a1f7f118c8e9bc830b04a8579c1119cb78`.

| Command label | Actual result |
| --- | --- |
| correction-native03-red-valid | 4 tests, 3 failures: missing registration and conflicting version/binding factory contexts reach core. |
| correction-grpc-channel-red | 4 tests, 4 failures against standard channel metadata interceptors. |
| correction-grpc-ingress-red | 2 tests, 2 failures: conflicts admitted and identical duplicates retained. |
| correction-rest-verb-red | Independent GET-only peer and actual single-version router: 6 tests, 3 failures. Multiversion module skipped after dependency failure. |
| correction-rest-multiversion-red | Actual router: 6 tests, 2 failures; legacy 1.0/0.3 cases pass. |
| correction-grpc-late-tracer-probe | **23 tests, 22 pass, 1 fails**, no skips/errors: transport18 and standard-channel4 pass; later public factory still bypasses selected version. |
| correction-focused-green | 37 executed across 7 suites, no skips/failures/errors: REST12, native context4, old admission1, shared duplicates2, transport18. |
| correction-affected-regression | **37 modules SUCCESS; 211 fresh suites; 2,901 discovered / 2,802 executed / 99 skipped / 0 failures/errors.** Explicitly excludes all five GrpcChannelMetadataTest cases. |

The selected regression ran17:45:16.610691–17:48:57.917444 UTC on2026-11-10,
221.306 seconds, exit0, no timeout within900seconds.
[Command](evidence/correction-affected-regression.json),
[log](evidence/correction-affected-regression.log.gz),
[exact fresh suites/skips/hashes](evidence/correction-affected-regression-test-summary.json).
All99 skipped identities occur in the preserved baseline:
[comparison and explicit exclusion](evidence/correction-skip-comparison.json).
Exclusion is not a passing or skipped execution. The separate five-case class
retains four passes and one strict failure, alongside18 old transport passes:
[probe](evidence/correction-grpc-late-tracer-probe.log.gz),
[original failure reports](evidence/late-tracer-failure).

All42 bounded command records/logs are retained in
[command index](evidence/command-summary.json). Two new setup mistakes remain:
correction-native03-red stopped at a new grpc fixture compile error, corrected
before valid RED; correction-rest-client-red selected the wrong module and ran
zero tests, so its exit0 supplies no coverage. Neither is a hidden pass or a
production fix cycle. All commands were sequential with finite deadlines.

REST1.1 now uses GET in the public client and actual single/multiversion routes.
Historical1.0 POST and native0.3 POST remain; wrong verbs reject before handler
execution. Native0.3 admission now carries exact registration/original0.3/GRPC
through conversion into core, rejecting conflicting factory contexts with zero
core calls. Historical no-resolver behavior remains;0.3 has no tenants.

The partial grpc guard handles standard MetadataUtils duplicates/conflicts and
cancels failed starts; shared server ingress also rejects conflicts. A legitimate
later factory still bypasses the client guard. Server rejection cannot satisfy
client pre-I/O rejection. User explicitly kept the strict contract and requested
[an enforceable design](grpc-channel-design.md) before compatibility changes.
The proposed owned channel profile covers both1.0/1.1 with material admission,
backend and lifecycle changes. It is not implemented or approved. F1 retains two
conservative failed cycles; its counter does not reset for a new design.

[Independent correction review](evidence/correction-independent-review.md),
[grpc report](evidence/correction-grpc-report.md),
[REST report](evidence/correction-rest-report.md),
[native03 report](evidence/correction-native03-report.md).
F2/F3 and selected totals are independently confirmed; F1 remains P1.

The earlier clean root build (83modules,3864executed,123skipped,0failures) describes
the pre-correction tree, not this candidate. Its evidence and superseded readiness
claims are retained in [historical verification](evidence/pre-correction/verification.md)
and original build records. Frozen expectations/compiler inputs/Phase0 are
unchanged; compatibility probes remain selected evidence, not completeA03.

Historical Phase0:8iterations/A03audit10/no-progress0/originalbreachtrue. Original
Phase1 completed8. Additional authorization:17:33:12Z–18:33:12Z,up to4iterations;
read latest progress closure for current counts. Three-attempt and finite-command
safeguards remain. No fullA01–A14 acceptance/finalconformance. #2415/HTTPassociation,
artifact/batch outcomes,cursor/recovery and final-spec gates remain binding;
PR2411 is proposal only. No later-phase work, invented wire guarantees or commits.
[Integrity](evidence/final-integrity.json), [handoff](next-session.md).


Final administrative checkpoint 2026-11-10T18:29:54.582598+00:00: 141 source/test paths (64 modified, 77 new), 7,103 insertions / 677 deletions; 299 total changed paths including architecture/evidence. Current source hashes and compressed diff agree. All 44 bounded command records fit their retained authorization deadlines (32 original, 12 correction), with no overlap or timeout. Integrity passed: unchanged branch/HEAD/index, authoritative inputs and Phase 0 preserved, 120 original fixture hashes, 5 Phase 1 fixture hashes, 11 old SDK jar hashes, frozen response bytes and legacy CURRENT_PROTOCOL_VERSION=1.0. These administrative checks do not close F1 or establish a current full build. Final no-server-stream assertions remain unrun. No further implementation/testing after safeguard stop.
