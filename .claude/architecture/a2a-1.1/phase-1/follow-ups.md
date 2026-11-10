# Phase 1 follow-ups and completed requested corrections

This is the local register for findings and verification improvements deferred beyond the current candidate. It does not authorize implementation, change the frozen target, waive a Phase 1 blocker, or create GitHub state. Read the normative [gate register](../phase-0/decisions.md) for binding specification dependencies.

| Item | Disposition / next action |
| --- | --- |
| “existing examples to 1.0, correct native 0.3 REST routing” | Initially noted for future work, then explicitly authorized to proceed. Both are implemented and covered; no remaining deferred fix. Existing example clients explicitly select 1.0, and native 0.3 REST uses its historical default endpoint/version prefix without invented tenants. |
| Pinned gRPC implementation coupling | Re-audit private DNS/parser isolation, provider monitor/final capture, Census/configurator admission and metadata timing before upgrading grpc-java from 1.81.0. This is an upgrade gate, not a known open bypass in the reviewed pinned profile. |
| Optional whole ITK module | Compile/test when its external a2a-itk proto checkout is available. Current evidence type-checks only the exact migrated managed-factory expression; default full reactor passes. |
| Additional direct DNS TXT / hostname-negative proof | Source review covers inert TXT parser exclusion, and actual untrusted-certificate rejection is tested. Adversarial TXT records and explicit wrong-host rejection are optional additional proof, not claimed as executed. |
| Opaque asynchronous credentials | Unsupported by the approved initial owned-channel profile. Any later adapter requires its own design and cancellation/context/exactly-once metadata application tests; no current support claim. |
| Complete A03 | Retained frozen selected source/binary/wire evidence is not full acceptance. Finish the complete matrix in its authorized scope without regenerating expected outputs with candidate codecs. |
| HTTP live association / exact artifact-batch outcomes / cursor-recovery | Binding specification gates remain unresolved, including #2415. Await normative resolution and later-phase authorization; no invented protocol fields, receipts, status RPCs or retry guarantees. PR2411 is only a proposal. |

All reported Phase 1 implementation blockers from the supplied reviews are corrected under the approved material channel change and current verification. The final current disposition is in [review response](review-response.md), [verification](verification.md) and [independent review](evidence/resume-final-independent-review.md). Historical failures and exhausted allowances remain in the progress ledger and pre-resume snapshots.
