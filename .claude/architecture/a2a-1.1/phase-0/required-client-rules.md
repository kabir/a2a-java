# Required client rules found in Phase 0 review

Read this supplement alongside the existing architecture before any production implementation. It fills two omissions; it does not change selected-version/default semantics or invent a binding contract. The original seven architecture inputs remain byte-for-byte unchanged in this run.

Source pin: [RC specification, generation rules](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/docs/specification.md#L576-L587) and [timeline rules](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/docs/specification.md#L619-L630). These are pinned requirements, not new SDK guarantees.

## Invalid generation signals

For a generation-aware interaction, receiving generation 0 or two consecutive same-task events with identical generations triggers the RC's required reliability disablement for the remainder of that interaction: do not use generation for ordering, long-polling or precondition checks. The suggested ordering fallback is stream order/timestamps, not another protocol version. Selected version remains immutable.

Do not silently drop a caller's explicit generation guard or claim successful guarded input after disablement. A local unsupported/unreliable-feature result must precede sending an operation that can no longer uphold the requested guard. Do not keep claiming generation-synchronized TaskView after evidence invalidates it. Preserve the last safe state/as-of provenance and expose uncertainty through the additive view API; no fabricated task failure or task cancellation follows.

Required tests in later phases: valid sequence then zero; consecutive equal values; subsequent long-poll/guard usage; retained selected version; no automatic resend/rebase; old 1.0 callbacks remain unchanged. Public capability declaration alone does not promise reliable generations from a nonconforming peer.

## Unknown timeline kinds versus missing generations

An unknown timeline entry kind is skipped, but its known generation remains accounted for in sequence/missed-event detection. Do not manufacture a missing-generation gap from that known entry. This sequence accounting is distinct from the semantic synchronization watermark and history/timeline coverage: an uninterpretable entry can leave TaskView INCOMPLETE (or NO_BASELINE) and coverage UNKNOWN_SKIPPED, without moving the last safely interpreted as-of state. The raw entry is not a fabricated known ClientEvent.

Required later tests: known entries around an unknown kind with a known generation produce no false sequence gap; the client remains cancellable and semantically uncertain until supported authoritative reconciliation. Unknown stream variants without an interpretable generation have different evidence and may leave a real sequence gap.

## Additional dependent recovery gate: repeated snapshot watermarks

The RC says equal consecutive event generations disable reliability and states no general full-Task snapshot exception. The architecture permits replacement/resynchronization snapshots using stored generation. If recovery emits a second Task snapshot at the same generation within an interaction, do not silently exempt it from the rule above. Pin whether/when that snapshot is an event for this requirement, and what bounds the interaction, or apply the specified disablement. A reconnect must not be assumed to reset the interaction without evidence.

This blocks claims that repeated unchanged-generation recovery snapshots preserve reliable generation synchronization under A06/A09, and the affected Phase 4 controller/reducer/recovery behavior. It does not authorize adding a generation, task mutation, artificial acknowledgement, or wire marker merely to distinguish snapshots. The Phase 0 trace/decision register must retain this gate until authoritative clarification or an explicitly documented experimental policy is selected.
