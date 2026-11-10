# Phase 1 handoff — incomplete, safeguard stop

> Current checkpoint: **Phase 1 incomplete; no commit approval requested.** The user explicitly approved the SDK-owned Netty channel/API/backend/lifecycle design for both 1.0 and 1.1. Its partial implementation passed 14 focused tests, but independent review found an admitted global load-balancer tracer can still mutate metadata after the SDK assertion. A newly allocated provider channel can also leak on descriptor-validation failure. Existing raw-channel/factory callers and tests still require migration. Stronger zero-server-stream assertions added after the focused build remain unrun. Earlier readiness/design-pending statements below are historical and superseded.
>
> Correction iteration 4 is closed: **4/4 additional iterations, 12 cumulative Phase 1; no-progress 0**. The owned attempt is conservatively the third materially failed F1 implementation; **the three-attempt safeguard stops further F1 work**. This is a source-established design failure, not a third executed failing probe. The original correction deadline remains 2026-11-10T18:33:12Z. Further implementation requires an explicitly authorized revised approach and renewed budget; material design approval already granted does not reset either limit. No commit, push, PR or Phase 2 work occurred.

Worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, accepted Phase 0 HEAD `e468dd70a415d17c19ae70d75750b6464d983ad1`. Issue #1303 confirmed. Inspect AGENTS.md, branch, HEAD, index and status; preserve all uncommitted changes and frozen Phase 0 evidence.

The user approved material owned-channel admission/backend/lifecycle changes for both 1.0 and 1.1. This approval persists. Do not ask for it again. It did not approve a commit or reset the correction limit. Do not continue implementation until the safeguard and exhausted iteration budget are explicitly addressed.

Required next work, if authorized:

1. Present a revised enforceable design for global resolver/load-balancer providers and late tracer ordering. Preserve strict conflict rejection before I/O; no trust-boundary narrowing or explicit-1.0 exception is approved. Review the exact pinned source excerpts in evidence/owned-channel-provider-source-evidence.json. The current SDK-owned wrapper alone does not close F1.
2. Fix channel allocation cleanup on descriptor-validation failures while preserving another owner's channel. Add meaningful negative lifecycle coverage.
3. Migrate the remaining existing raw-channel/factory consumers and fixtures to the approved constrained API. Static candidate inventory contains 52 calls and is not exhaustive. Run the final stronger zero-server-stream assertions, which were added after the 14-test proof.
4. Verify TLS, cancellation/lifecycle, provider isolation and both selected versions with independent boundary evidence, then run appropriate complete affected/full verification. Obtain independent review again. Do not claim the earlier complete build verifies the current candidate.

F2 is corrected: REST 1.1 subscription GET; demonstrated 1.0/native 0.3 POST preserved; wrong verbs rejected before handlers. F3 is corrected: native 0.3 admitted registration and original version/binding reach the converted core, conflicting factories reject before execution, legacy no-resolver behavior retained. Protocol 0.3 has no tenants. Both example factories explicitly select 1.0; native 0.3 REST default routing is corrected. CURRENT_PROTOCOL_VERSION and demonstrated model defaults remain 1.0; new shared clients default to 1.1.

Verification: 14 focused owned/admission tests passed, 8 modules, zero skips/failures/errors (selected cases only). Earlier correction regression: 37 modules, 2,802 executed / 99 existing skips, zero failures/errors, explicitly excluded GrpcChannelMetadataTest. Original complete 83-module build with 3,864 executed / 123 skips predates these corrections. No final current full build is passing. See verification.md and retained independent reports.

Historical accounting is unchanged: Phase 0 8 completed, A03 conservative audit 10, no-progress 0, original breach retained. Original Phase 1 8 completed; correction 4 additional / 12 cumulative. F1 conservative failed material cycles 3; stop is binding. Original correction window 17:33:12Z–18:33:12Z on 2026-11-10 was not restarted by material approval.

Remain experimental RC. PR2411 is a proposal. HTTP association (#2415), exact artifact/batch outcomes and cursor/recovery gates remain binding. No later-phase generation/timeline, observation, authority or live work; no invented headers, session identifiers, receipts, status RPCs or retry guarantees. Do not regenerate frozen expected outputs or rerun unchanged baseline without a concrete need.

No commit, push or PR. Future proposed Conventional Commit, only after completion, independent review and explicit commit approval:

    feat(a2a-1.1): add explicit interface and client version policy

    This fixes #1303
