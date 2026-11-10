# Phase 0 evidence and handoff

This directory records research, unchanged-code baseline verification and frozen historical wire probes for SDK `2817ed78d02d9a6e609dafdf7f5129484edef017`. It adds no production 1.1 support and does not authorize Phase 1. See [run control and status](../implementation-progress.md).

Read in this order:

1. [Pinned specification gates](spec-gates.md) and [minimal HTTP ambiguity trace](http-live-ambiguity.md).
2. [Required client rules from independent review](required-client-rules.md), including invalid-generation disablement, unknown timeline generation accounting and the repeated-snapshot recovery gate.
3. [Construction, SPI/wrappers, reducers and integration-suite inventory](client-inventory.md).
4. [Baseline command/results and coverage limits](baseline.md), with complete test/skip summary under evidence.
5. [Frozen historical fixture provenance and probe commands](fixtures/README.md). Expected bytes are immutable; candidate runs compare without recapturing expectations.
6. [Concrete selected SDK policies and remaining normative limits](selected-policies.md).
7. [Independent review](independent-review.md), [Phase 0 exit review](exit-review.md) and [concrete next-session plan](next-session.md).

The [decision register](decisions.md) maps each residual to blocked client/server work. Historical probe work exceeded the three-attempt limit and stopped at the first checkpoint. The user explicitly authorized a staged follow-up; both version checks now pass their frozen selected codec baselines (1.0: 33 positive plus one known failure; 0.3: 24 positive plus two known failures). Prior breach/history remain retained. **Phase 0 deliverables are verified within the documented research/baseline scope**, confirmed by the final independent exit review. This does not resolve undefined protocol rules, pass all A01–A14 or authorize Phase 1.

The six original gates are investigated and their factual portions pinned; unresolved normative portions remain explicit dependent blockers. The target is RC/experimental, not final 1.1. Current upstream PR source/diff is evidence of a proposal only. Raw/frozen compatibility probes, public updated SDK integration, and malformed binding tests remain separate evidence layers.

The initial seven user-authored architecture inputs were preserved byte-for-byte; evidence/initial-user-files.json records their SHA-256 fingerprints. No full A01–A14 criterion is passed by a Phase 0 baseline.
