> Current approval status: the user explicitly authorized creating the reviewed Phase 1 commit with **“YEs, commit”** at 2026-11-10T19:47:38.723628+00:00. Read the latest [progress record](../implementation-progress.md) for the resulting commit receipt. The pre-approval handoff below remains historical; its no-commit-approval statements are superseded by this explicit approval. No push, PR, new implementation allowance or Phase 2 is authorized.

# Phase 1 handoff — verified candidate awaiting commit approval

This is the resumed Phase 1 candidate for issue #1303. The user approved the strict SDK-owned gRPC channel/API/backend/lifecycle change for both protocol 1.0 and 1.1, then explicitly authorized five further focused iterations. That material design approval persists. No commit, push, PR, or Phase 2 work is authorized.

Worktree: `/Users/kabir/.codex-api/worktrees/3053/a2a-java`.
Branch: `support/a2a-1.1.0`.
Expected accepted Phase 0 HEAD: `e468dd70a415d17c19ae70d75750b6464d983ad1`.

## Current checkpoint

All five resumed iterations are complete after final independent review and evidence/integrity reconciliation (17 cumulative Phase 1 iterations). No implementation iteration remains authorized. Resumed no-progress count is 0. The fixed resumed deadline is **2026-11-10T20:37:06Z**, from the authorized start at 18:37:06Z; neither design approval nor context/session changes restart it. Historical original Phase 1 8 iterations plus 4 correction iterations, three materially failed F1 implementations, Phase 0 8 completed / conservative A03 audit 10 / no-progress 0, and the original safeguard breach remain recorded in [run-control audit](evidence/run-control-audit.json) and historical evidence.

The verified candidate has 156 frozen functional inputs and 394 complete proposed paths including architecture/evidence. Match those exact hashes and inventory before any approved commit. A source, fixture or dependency change requires new scope and verification; it cannot inherit this candidate’s results. The final result below applies only to these exact unchanged functional inputs.

**Final verification: 83 successful modules; 4,041 discovered / 3,918 executed / 123 skipped / zero failures or errors; exit 0 within the 1,200-second command bound. All 123 skip identities match Phase 0 exactly.**

**Final independent review: no blocking findings for this Phase 1 candidate; final integrity and exact-source comparison passed. See the retained review, build-input hashes and integrity record. Commit approval is still absent.**

## Implemented behavior and corrections

Shared clients default to protocol 1.1 and can explicitly select 1.0. Client selection binds one exact supported interface, protocol binding, version, endpoint, and descriptor tenant before transport construction. Legacy `AgentInterface.CURRENT_PROTOCOL_VERSION` and demonstrated model defaults remain 1.0. Both hello-world and stream-lifecycle examples deliberately select 1.0.

Strict gRPC admission applies to both selected versions: arbitrary supplied raw channels and factories are rejected before invocation. Consumers use `GrpcTransportConfigBuilder.managedChannel(GrpcManagedChannelOptions)` or `GrpcTransport.createManaged(...)`; owned channels have bounded SDK lifecycle cleanup. Constructor tests use genuine SDK-issued handles, and request/header rejection tests construct valid transports before assertions. The pinned grpc-java 1.81.0 profile isolates global resolver/service-config/proxy hooks, checks and captures the stock pick-first provider under the registry monitor, disables Census callbacks, and rejects arbitrary CallOptions credentials/tracers. This is a source-audited backend profile, requiring re-audit on grpc-java upgrade; it does not implement asynchronous credential adapters. Fresh provider allocation failures close only their new candidate and preserve another owner's handle. See [provider review](evidence/resume-provider-design-review.md), [independent review](evidence/resume-final-independent-review.md), and [migration report](evidence/resume-migration-report.md).

F2 is corrected: REST 1.1 subscription uses GET; explicit 1.0/native 0.3 retain POST, with wrong verbs rejected before handlers. F3 is corrected: admitted native 0.3 registration and original version/binding reach the converted core; conflicting factories reject before execution and legacy no-resolver behavior remains. Native 0.3 has no tenants. Native 0.3 clients retain their separate transport API. HTTP wire probes can still deliberately send unsupported/missing metadata.

The first resumed full clean install is retained as a failure: **4,040 discovered / 3,917 executed / 123 existing skips / 1 failure / 0 errors; 78 successful, 1 failed, and 4 skipped modules**. All skip identities matched Phase 0. Its sole failure was the second destructive whitespace-metadata request reusing the connection after HTTP/2 GOAWAY. Independent pinned-source diagnosis and an unchanged passing diagnostic rerun supported isolating those probes on fresh raw channels. The correction preserves exact **INTERNAL**, **PROTOCOL_ERROR**, and zero application-ingress assertions for each service. All seven provisioning subsets then passed. This historical failed full run must remain visible and must not be relabeled successful.

The final affected clean build passed **8 modules, 698 discovered/executed, 0 skips/failures/errors**. It includes private-pin and opaque CallCredentials/tracer admission coverage and recompilation after removal of the unused superseded guard. Focused provider, TLS/authentication, cancellation, bounded close, cleanup, and consumer suites are retained; overlapping runs are not additive unique test totals. The second full run, `resume-full-current-reactor-final`, passes the current whole-reactor gate: 4,041 discovered / 3,918 executed / 123 skipped / zero failures or errors. See [verification](verification.md) and [command records](evidence/command-summary.json).

Hello-world is included in the 83-module root reactor. The optional whole ITK module remains uncompiled because its external a2a-itk proto checkout is absent. The exact migrated managed-factory expression separately passed `javac --release 17 -proc:none` at 19:16:23Z; this is selected type evidence only, not ITK module compilation.

## Resume and approval sequence

1. Read AGENTS.md and current Phase 1 verification/review/evidence. Inspect the worktree, branch, full HEAD, index, and `git status` before any action. Preserve uncommitted user work. The accepted Phase 0 HEAD above is expected; investigate any mismatch.
2. Read the fixed resumed authorization/accounting and actual full-run result. The five resumed iterations are exhausted at successful handoff. No build remains running and no implementation continuation is authorized by opening another session. A new session does not renew the budget or erase historical failures.
3. Match the exact candidate against the refreshed [source scope](evidence/source-scope.json), [proposed paths](evidence/commit-files.txt), retained [diff](evidence/source-and-tests.diff.gz), build-input hashes, and [final integrity](evidence/final-integrity.json). Confirm Phase 0/frozen expected outputs remain unchanged and skip identities remain accounted for. A changed candidate cannot inherit the earlier candidate's approval or verification claims.
4. Only after the complete current verification and independent final review are successful, present the exact [commit proposal](commit-proposal.md), scope, validation, and limitations, then ask for **commit approval only**. Issue #1303 is already confirmed; do not ask again. The material channel design is already approved; do not ask again. Do not stage or commit before the requested commit approval arrives.
5. If approval is given for the exact matching candidate, follow that scope. Push, PR, release, and Phase 2 remain separate actions requiring authorization; this handoff authorizes none of them.

Remain an experimental RC. PR2411 is a proposal. HTTP association (#2415), exact artifact/batch outcomes, and cursor/recovery gates remain binding for later phases. This candidate does not establish final 1.1 conformance or complete A03. Do not invent headers, sessions, receipts, status RPCs, fallback, SDK resends, or retry guarantees. Do not regenerate frozen expected outputs or rerun an unchanged baseline without a concrete reason.

Future findings and verification improvements are recorded in [follow-ups](follow-ups.md). The previously requested examples/native REST note is marked completed, with remaining specification gates kept separate.
