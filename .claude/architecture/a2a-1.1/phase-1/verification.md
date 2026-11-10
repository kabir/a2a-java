# Phase 1 current verification

The current Phase 1 candidate passed the complete root `mvn -B -ntp clean install`: **83 successful modules; 4,041 discovered / 3,918 executed / 123 skipped / zero failures or errors**. All 123 skipped test identities match the accepted Phase 0 baseline exactly. No current test class was excluded. Final independent review and administrative integrity disposition are recorded in [independent review](evidence/resume-final-independent-review.md) and [final integrity](evidence/final-integrity.json); commit creation still requires separate explicit user approval.

Worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, unchanged accepted Phase 0 HEAD `e468dd70a415d17c19ae70d75750b6464d983ad1`. Experimental RC target remains `78e1d6a1f7f118c8e9bc830b04a8579c1119cb78`.

## Complete current result

Command label `resume-full-current-reactor-final` ran **2026-11-10T19:16:29.023726+00:00–2026-11-10T19:28:29.630519+00:00**, exit 0, supervisor elapsed **720.606 seconds**, within its 1,200-second bound and the resumed 20:37:06Z authorization deadline. The UTC interval and monotonic supervisor duration are retained separately where the host clock differs; neither was rewritten.

[Exact command and timing](evidence/resume-full-current-reactor-final.json), [complete log](evidence/resume-full-current-reactor-final.log.gz), [fresh XML counts and hashes](evidence/resume-full-current-reactor-final-test-summary.json), [byte-exact report archive](evidence/resume-full-final-reports.tar.gz), [archive manifest](evidence/resume-full-final-reports-manifest.json), and [independent counts/skip comparison](evidence/resume-full-current-reactor-final-independent-review-summary.json) identify the actual current evidence. Overlapping focused runs are not additive unique test totals.

## Focused verification and correction history

| Command | Actual result and purpose |
| --- | --- |
| `resume-provider-cleanup-red` | 5 tests, 4 expected leak assertion failures, zero errors/skips; real allocation positive control passes. |
| `resume-global-provider-red` | 5 tests, 3 expected assertion failures, zero errors/skips; raw Netty control actually emits conflicting `[1.1,1.0]` via a final LB tracer. |
| `resume-owned-and-cleanup-green` | Compilation failure for missing direct grpc-core dependency; dependency declared. No behavioral fix inferred from compilation. |
| `resume-owned-and-cleanup-green-compile-fixed` | 24 tests, one test-harness assertion failure: normal null completion wrongly counted as an error; harness corrected without changing SDK behavior. |
| `resume-owned-and-cleanup-green-final` | 8 successful modules; 24 executed, no skips/failures/errors. All 12 owned-channel, 5 provider-isolation, 5 cleanup, and 2 opaque-admission cases run. |
| `resume-client-migration-regression` | 12 successful modules; 916 discovered / 911 executed / 5 existing skips / zero failures/errors; all GrpcChannelMetadataTest cases included. |
| `resume-provider-extra-proof` | 7 executed, no skips/failures/errors: actual hostile global provider control; exact-provider early rejection; private DNS; cold/warm/post-idle calls; registry mutation; ProxySelector exclusion; finite child-JVM configurator rejection. |
| `resume-tls-lifecycle-proof` | 4 executed, no skips/failures/errors, each covering selected 1.0 and 1.1: real TLS/ALPN/system trust/authentication; untrusted-peer rejection; caller cancellation and reuse; bounded active close and idempotence. |
| `resume-full-current-reactor` | **Failed**: 78 successful / 1 failed / 4 skipped modules; 4,040 discovered / 3,917 executed / 123 skips / one failure / zero errors. This is retained as a failed full run. |
| `resume-whitespace-diagnostic` | Unchanged exact transport-status assertion rerun passed one case; alone not treated as closure or proof of cause. |
| `resume-whitespace-isolated-channel-green` | All seven provisioning subsets pass after each destructive malformed HTTP/2 request gets its own raw channel. |
| `resume-final-grpc-affected` | Clean install, 8 successful modules; 698 discovered/executed, no skips/failures/errors. Includes new private-pin/opaque-hook admission class and recompilation after obsolete guard removal. |
| `resume-itk-expression-type-compile` | Exact migrated managed-factory expression passes javac release 17. Selected type evidence only; optional whole ITK module is not compiled. |
| `resume-full-current-reactor-final` | **83 successful modules; 4,041 discovered / 3,918 executed / 123 skipped / zero failures or errors.** |

Every command, exit, finite bound, actual log suite count and retained failure appears in [command summary](evidence/command-summary.json) and [run-control audit](evidence/run-control-audit.json). The original 83-module / 3,864-executed / 123-skipped pass and selected 37-module / 2,802-executed / 99-skipped correction regression remain historical evidence; the latter excluded GrpcChannelMetadataTest. They are not substituted for the current result. The original 14 owned-channel passes and then-unexecuted stronger assertions remain historical in [pre-resume verification](evidence/pre-resume/verification.md); the strengthened assertions now execute in the current build.

The first full resumed failure was the second raw whitespace-header request reusing a connection after deliberately malformed HTTP/2 input. The shared-service call had already passed exact INTERNAL + PROTOCOL_ERROR; the historical-service call observed UNAVAILABLE during teardown. Independent pinned Netty source explains GOAWAY/unsent-request behavior: [source review](evidence/resume-whitespace-independent-source-review.json). The corrected test uses fresh channels for both destructive requests and preserves **exact INTERNAL, PROTOCOL_ERROR, and zero application ingress**, with bounded cleanup. No expected status was widened and no production mapping or frozen fixture was changed. [Failed summary](evidence/resume-full-first-independent-summary.json), [299 exact failed-run XML reports](evidence/resume-full-first-reports.tar.gz), [manifest](evidence/resume-full-first-reports-manifest.json).

## Contract coverage and limits

All seven nonempty default-endpoint version subsets have real coverage for JSON-RPC, REST and gRPC. Paired public clients/server policies cover explicit registration, exact descriptor selection before binding preference, immutable default 1.1 and explicit 1.0, version/endpoint/tenant propagation, providers and both OpenTelemetry wrapper families. `CURRENT_PROTOCOL_VERSION` remains 1.0. Representative old-source/binary/wire probes retain their original compiler inputs and frozen expectations. Native 0.3 remains separate and has no tenants; its admitted registration/original binding reaches core, with conflicting factories rejected before execution. REST ordinary subscription uses GET for 1.1 and demonstrated POST behavior for 1.0/native 0.3, with independent peer/router verb checks.

Strict gRPC rejection applies to both selected versions under the approved SDK-owned pinned backend profile. Provider isolation is established by exact 1.81.0 source plus adversarial controls; stress outcomes supplement the atomic monitor/final-provider proof. Owned-handle tests reject missing/wrong private pins, opaque CallCredentials and caller tracers before real server streams, then execute a valid call on the same owner. Provider construction snapshots its factory once, validates before allocation and closes only a fresh failed candidate. Tests protect existing owners from accidental closure. Opaque asynchronous credentials are unsupported; no credential adapter/race-support claim is made.

Hello-world is in the default root reactor. The opt-in ITK module depends on an absent external a2a-itk proto checkout; its exact migrated expression was separately type-checked ([provenance](evidence/itk-type-probe/result.json)), not the whole module. DNS TXT adversarial records and explicit wrong-host TLS rejection are not directly tested: TXT parser exclusion is source-reviewed, while the retained negative TLS case proves untrusted-certificate rejection before RPC. Backend isolation relies on pinned grpc-java 1.81.0 internals and must be re-audited on upgrade. It does not sandbox JVM-wide instrumentation/reflection or network-provider replacement.

No complete A03 or final 1.1 conformance claim follows. PR2411 is a proposal; HTTP association (#2415), exact artifact/batch outcomes and cursor/recovery gates remain binding. No later generation/timeline, observation, mutation-authority or live functionality was added.

## Accounting and history

The five additional iterations authorized at 18:37:06Z preserve Phase 0's 8 completed / A03 conservative audit 10 / no-progress 0 / original breach, and the original Phase 1 eight plus four correction iterations and three materially failed F1 approaches. The resumed revised implementation has no failed material F1 cycle; red proofs, a compile prerequisite, the documented harness correction and malformed-channel fixture isolation are separately recorded. The historical stop is not erased by current success. See [progress](../implementation-progress.md), [authorization](evidence/resume-authorization.json), [audit](evidence/run-control-audit.json), [pre-resume checkpoint](evidence/pre-resume/verification.md), and [next session](next-session.md).

Pinned upstream source entries cited in the independent provider reports are retained in the [audited source archive](evidence/resume-audited-grpc-source.tar.gz) and [source/archive hash manifest](evidence/resume-audited-grpc-source-manifest.json). Extract entry paths under the original grpc-source-review root to resolve historical temporary-file links.
