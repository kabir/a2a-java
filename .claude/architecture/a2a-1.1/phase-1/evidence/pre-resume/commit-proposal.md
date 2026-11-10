> Current checkpoint: **Phase 1 incomplete; no commit approval requested.** The user explicitly approved the SDK-owned Netty channel/API/backend/lifecycle design for both 1.0 and 1.1. Its partial implementation passed 14 focused tests, but independent review found an admitted global load-balancer tracer can still mutate metadata after the SDK assertion. A newly allocated provider channel can also leak on descriptor-validation failure. Existing raw-channel/factory callers and tests still require migration. Stronger zero-server-stream assertions added after the focused build remain unrun. Earlier readiness/design-pending statements below are historical and superseded.
>
> Correction iteration 4 is closed: **4/4 additional iterations, 12 cumulative Phase 1; no-progress 0**. The owned attempt is conservatively the third materially failed F1 implementation; **the three-attempt safeguard stops further F1 work**. This is a source-established design failure, not a third executed failing probe. The original correction deadline remains 2026-11-10T18:33:12Z. Further implementation requires an explicitly authorized revised approach and renewed budget; material design approval already granted does not reset either limit. No commit, push, PR or Phase 2 work occurred.

# Phase 1 commit status — not ready

**No commit approval is requested. F1 remains P1 with a known failing acceptance
test.** Earlier readiness is withdrawn. F2 REST/F3 native03 fixes pass review and
selected regressions. [Material channel design](grpc-channel-design.md) awaits
approval; it has not been implemented.

Branch support/a2a-1.1.0, unchanged HEAD
`e468dd70a415d17c19ae70d75750b6464d983ad1`; issue#1303 confirmed. All changes remain
uncommitted/indexclean. [Current diff](evidence/source-and-tests.diff.gz),
[diffstat](evidence/source-diffstat.txt), [hash inventory](evidence/source-scope.json),
[all proposed paths](evidence/commit-files.txt). Original reviewed scope remains
in evidence/pre-correction/. Current scope includes the retained failing probe.

[Verification](verification.md), [independent review](evidence/correction-independent-review.md),
[handoff](next-session.md). Selected37module regression passes2802executions/
99skips with failinggrpcclass explicitlyexcluded. Earlier83module root success
is historical evidence. No later-phase/schema/wire-guarantee change is included.

Historical proposed message, to re-present after Phase1 actually passes:

```text
feat(a2a-1.1): add explicit interface and client version policy

This fixes #1303
```

Require approval of material channel compatibility changes before implementation,
then separate commit approval after verification/review. No push/PR/Phase2.


Current proposed scope is 141 source/test paths (64 modified, 77 new), 7,103 insertions and 677 deletions, plus architecture/evidence; the complete inventory contains 299 changed paths. See evidence/source-diffstat.txt, evidence/source-scope.json, evidence/source-and-tests.diff.gz and evidence/commit-files.txt. This includes the approved but incomplete material gRPC owned-channel admission/backend/lifecycle change. Do not commit this checkpoint. The three-attempt safeguard and exhausted correction iterations require explicit continuation authorization for a revised approach; completion, independent review and separate commit approval remain required.
