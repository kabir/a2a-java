from pathlib import Path
import json, datetime as D
root=Path('/Users/kabir/.codex-api/worktrees/3053/a2a-java'); base=root/'.claude/architecture/a2a-1.1'; p1=base/'phase-1'; ev=p1/'evidence'; run=Path('/tmp/a2a-phase1-run')
label='resume-full-current-reactor-final'; meta=json.loads((run/(label+'.json')).read_text()); summary=json.loads((ev/(label+'-test-summary.json')).read_text()); totals=summary['totals']
assert meta['exit_code']==0 and totals['failures']==totals['errors']==0
stamp=D.datetime.now(D.timezone.utc).isoformat(); counts=f"{totals['discovered']:,} discovered / {totals['executed']:,} executed / {totals['skipped']} skipped / zero failures or errors"
verification=f'''# Phase 1 current verification

The current Phase 1 candidate passed the complete root `mvn -B -ntp clean install`: **83 successful modules; {counts}**. All 123 skipped test identities match the accepted Phase 0 baseline exactly. No current test class was excluded. Final independent review and administrative integrity disposition are recorded in [independent review](evidence/resume-final-independent-review.md) and [final integrity](evidence/final-integrity.json); commit creation still requires separate explicit user approval.

Worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, unchanged accepted Phase 0 HEAD `e468dd70a415d17c19ae70d75750b6464d983ad1`. Experimental RC target remains `78e1d6a1f7f118c8e9bc830b04a8579c1119cb78`.

## Complete current result

Command label `{label}` ran **{meta['start']}–{meta['end']}**, exit 0, supervisor elapsed **{meta['elapsed_seconds']} seconds**, within its 1,200-second bound and the resumed 20:37:06Z authorization deadline. The UTC interval and monotonic supervisor duration are retained separately where the host clock differs; neither was rewritten.

[Exact command and timing](evidence/{label}.json), [complete log](evidence/{label}.log.gz), [fresh XML counts and hashes](evidence/{label}-test-summary.json), [byte-exact report archive](evidence/resume-full-final-reports.tar.gz), [archive manifest](evidence/resume-full-final-reports-manifest.json), and [independent counts/skip comparison](evidence/{label}-independent-review-summary.json) identify the actual current evidence. Overlapping focused runs are not additive unique test totals.

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
| `{label}` | **83 successful modules; {counts}.** |

Every command, exit, finite bound, actual log suite count and retained failure appears in [command summary](evidence/command-summary.json) and [run-control audit](evidence/run-control-audit.json). The original 83-module / 3,864-executed / 123-skipped pass and selected 37-module / 2,802-executed / 99-skipped correction regression remain historical evidence; the latter excluded GrpcChannelMetadataTest. They are not substituted for the current result. The original 14 owned-channel passes and then-unexecuted stronger assertions remain historical in [pre-resume verification](evidence/pre-resume/verification.md); the strengthened assertions now execute in the current build.

The first full resumed failure was the second raw whitespace-header request reusing a connection after deliberately malformed HTTP/2 input. The shared-service call had already passed exact INTERNAL + PROTOCOL_ERROR; the historical-service call observed UNAVAILABLE during teardown. Independent pinned Netty source explains GOAWAY/unsent-request behavior: [source review](evidence/resume-whitespace-independent-source-review.json). The corrected test uses fresh channels for both destructive requests and preserves **exact INTERNAL, PROTOCOL_ERROR, and zero application ingress**, with bounded cleanup. No expected status was widened and no production mapping or frozen fixture was changed. [Failed summary](evidence/resume-full-first-independent-summary.json), [299 exact failed-run XML reports](evidence/resume-full-first-reports.tar.gz), [manifest](evidence/resume-full-first-reports-manifest.json).

## Contract coverage and limits

All seven nonempty default-endpoint version subsets have real coverage for JSON-RPC, REST and gRPC. Paired public clients/server policies cover explicit registration, exact descriptor selection before binding preference, immutable default 1.1 and explicit 1.0, version/endpoint/tenant propagation, providers and both OpenTelemetry wrapper families. `CURRENT_PROTOCOL_VERSION` remains 1.0. Representative old-source/binary/wire probes retain their original compiler inputs and frozen expectations. Native 0.3 remains separate and has no tenants; its admitted registration/original binding reaches core, with conflicting factories rejected before execution. REST ordinary subscription uses GET for 1.1 and demonstrated POST behavior for 1.0/native 0.3, with independent peer/router verb checks.

Strict gRPC rejection applies to both selected versions under the approved SDK-owned pinned backend profile. Provider isolation is established by exact 1.81.0 source plus adversarial controls; stress outcomes supplement the atomic monitor/final-provider proof. Owned-handle tests reject missing/wrong private pins, opaque CallCredentials and caller tracers before real server streams, then execute a valid call on the same owner. Provider construction snapshots its factory once, validates before allocation and closes only a fresh failed candidate. Tests protect existing owners from accidental closure. Opaque asynchronous credentials are unsupported; no credential adapter/race-support claim is made.

Hello-world is in the default root reactor. The opt-in ITK module depends on an absent external a2a-itk proto checkout; its exact migrated expression was separately type-checked ([provenance](evidence/itk-type-probe/result.json)), not the whole module. DNS TXT adversarial records and explicit wrong-host TLS rejection are not directly tested: TXT parser exclusion is source-reviewed, while the retained negative TLS case proves untrusted-certificate rejection before RPC. Backend isolation relies on pinned grpc-java 1.81.0 internals and must be re-audited on upgrade. It does not sandbox JVM-wide instrumentation/reflection or network-provider replacement.

No complete A03 or final 1.1 conformance claim follows. PR2411 is a proposal; HTTP association (#2415), exact artifact/batch outcomes and cursor/recovery gates remain binding. No later generation/timeline, observation, mutation-authority or live functionality was added.

## Accounting and history

The five additional iterations authorized at 18:37:06Z preserve Phase 0's 8 completed / A03 conservative audit 10 / no-progress 0 / original breach, and the original Phase 1 eight plus four correction iterations and three materially failed F1 approaches. The resumed revised implementation has no failed material F1 cycle; red proofs, a compile prerequisite, the documented harness correction and malformed-channel fixture isolation are separately recorded. The historical stop is not erased by current success. See [progress](../implementation-progress.md), [authorization](evidence/resume-authorization.json), [audit](evidence/run-control-audit.json), [pre-resume checkpoint](evidence/pre-resume/verification.md), and [next session](next-session.md).
'''
(p1/'verification.md').write_text(verification)
(p1/'review-response.md').write_text('''# Phase 1 independent-review disposition

The resumed implementation preserves the strict selected-version contract. No channel-interceptor/tracer exclusion is used to waive the finding. The user explicitly approved the material SDK-owned channel/API/backend/lifecycle change for both 1.0 and 1.1, then authorized five more iterations. These permissions do not authorize a commit, push, PR or Phase 2.

| Finding | Current disposition and evidence |
| --- | --- |
| Supplied-channel metadata/tracer bypass, including global load-balancer mutation | Corrected within the approved pinned SDK-owned profile: arbitrary raw channels/factories rejected before use; private DNS/inert policy parser/no-proxy callback; exact stock pick-first provider checked and captured atomically under its registry monitor; no configurable Census/credentials/tracer injection. Actual raw exploit control and zero-server-stream rejection tests retained. [Design](grpc-channel-design.md), [source review](evidence/resume-provider-design-review.md), [implementation review](evidence/resume-implementation-review.md). |
| Shared gRPC consumers still supply rejected factories | Migrated all affected shared callers/examples/dev docs to managed options, including seven subsets/1.1-only packaging/client base/extras. Negative tests construct valid transports before behavior assertions. Native 0.3 factories remain separate. [Migration](evidence/resume-migration-report.md), [verification](verification.md). |
| Fresh provider allocation leaks | Snapshot and admit factory once; validate descriptor/card/version/tenant before allocation; close only newly issued candidate on later construction failure. Real Channelz allocation/leak controls, postallocation failure and existing-owner protection pass. [Cleanup](evidence/resume-cleanup-report.md). |
| 1.1 ordinary REST subscription uses POST | Corrected to GET for 1.1 in client and both router families. Explicit 1.0/native 0.3 retain demonstrated POST behavior; independent GET-only peer/router and wrong-verb tests pass. |
| Native 0.3 registration lost before core; conflicting context factory accepted | Admitted registration/original 0.3/binding carried into core; conflicting factory rejected with zero core execution; legacy absent-resolver behavior retained. Native 0.3 has no tenants. |
| Missing owned-channel admission proof / obsolete partial guard | Direct both-version private-pin/CallCredentials/tracer admission test executes before any real server stream, followed by a valid call on the same owner. Unreferenced superseded guard removed and cleanly recompiled. |
| First resumed full run malformed-header failure | Failed run preserved. Independent pinned Netty source and unchanged diagnostic supported fresh channels for independent destructive requests. Exact INTERNAL + PROTOCOL_ERROR + zero application assertions retained. All seven subsets and final full reactor pass. |

The current complete build, independent final verdict and exact-scope integrity are linked in [verification](verification.md). The original review findings, three material F1 failures, exhausted earlier allowance and stopped handoff remain verbatim under [pre-resume response](evidence/pre-resume/review-response.md), [pre-resume handoff](evidence/pre-resume/next-session.md), and the append-only progress ledger. Current success does not rewrite failed evidence or reset counters.

Remaining nonblocking verification limits are optional whole ITK compilation, directly adversarial DNS TXT and wrong-host TLS cases, and full A03 acceptance. The pinned implementation requires re-audit on gRPC upgrade. Experimental RC, HTTP association, artifact/batch and cursor/recovery gates remain open; no later phase is implemented. No commit approval is implied by this disposition.
''')
(p1/'grpc-channel-design.md').write_text('''# Strict gRPC owned-channel design — approved and implemented

The user approved this material channel/API/backend/lifecycle restriction for **both 1.0 and 1.1** before the resumed work. The later five-iteration allowance authorized revising the failed global-provider approach. No additional architecture approval is pending for the current profile; creating a commit still requires separate explicit approval. Historical unimplemented proposals and the stopped three-failure checkpoint remain in [pre-resume design](evidence/pre-resume/grpc-channel-design.md).

## Public construction and ownership

Shared gRPC transports accept only a fresh private SDK-issued handle. Arbitrary raw channels, factories, builders and channel sharing are rejected before invoking caller factories. Existing method descriptors remain for linkage, but raw-channel runtime admission is a deliberate approved compatibility break for both protocol versions. Use:

```java
new GrpcTransportConfigBuilder().managedChannel(
        GrpcManagedChannelOptions.builder().useTransportSecurity().build());

GrpcTransport.createManaged(options, card, "1.0");
```

Options copy static authentication headers, default to TLS using ordinary JVM trust and expose explicit plaintext plus a positive bounded shutdown timeout (default five seconds, maximum thirty). SDK ClientCallInterceptors remain supported with copied/normalized headers. There is no opaque CallCredentials adapter; arbitrary CallOptions credentials and tracer factories are rejected before RPC start. Asynchronous credential adapters would need a separately designed/verified extension and are not claimed here.

The selected descriptor supplies exact version, binding, endpoint and tenant. Handles are endpoint-bound, claimed once with an immutable private version identity, and owned by their SDK transport/client. Close is bounded and idempotent; tests cover active streaming cancellation, caller cancellation/reuse, prior-owner protection and failed fresh allocations. Provider construction reads the factory once, validates card/descriptor before allocation, and closes only its own fresh candidate if later construction fails.

## Enforceable pinned backend boundary

The unshaded Netty backend and direct grpc-core dependency are pinned at **grpc-java 1.81.0**. The supported profile is established against that exact source, not an assumed portable isolation API.

1. Build with an explicit `dns:///` target and a private resolver registry containing the SDK wrapper around final stock DNS. Override both URI resolver methods. Copy resolver Args while replacing its service-config parser with an inert error-producing parser. Disable lookup/application of service configs; a DNS TXT document cannot invoke a foreign LB parser or select another policy.
2. Install an SDK no-proxy detector, excluding global ProxySelector/Authenticator routing. Disable Census tracing/stats. Reject nonempty global configurators before build and after build, closing any candidate before returning it.
3. Hold the actual default LoadBalancerRegistry monitor across checking the exact final PickFirstLoadBalancerProvider class and completing builder.build(). The pinned register/deregister/getProvider methods synchronize on this same monitor. ManagedChannelImpl constructs AutoConfiguredLoadBalancerFactory synchronously; the latter captures its defaultProvider in a final field. Idle/recreation and later provider replacement therefore use the captured stock provider. The stock picker only selects helper-owned subchannels and supplies no programmable final tracer. A hostile installed provider is rejected without invoking its callbacks.
4. Before each owned call, require the private selected-version pin; reject arbitrary credentials and tracer factories. After all admitted SDK metadata contributions, normalize matching version entries to exactly one and reject every conflict before starting the backend. Authentication cannot override the selection. Retain duplicate/conflict server validation as defense in depth, not as a substitute for client rejection.

This closes the later load-balancer tracer path that defeated both the arbitrary-channel guard and the earlier ownership-only attempt. A before/after registry scan would still race; synchronized capture is essential. It also avoids a transport-only guard vulnerable to foreign-subchannel pickers. Source details and exact paths appear in [provider design review](evidence/resume-provider-design-review.md) and [implementation review](evidence/resume-implementation-review.md).

## Evidence and explicit limits

[Current verification](verification.md) and [final independent review](evidence/resume-final-independent-review.md) retain actual raw hostile-provider mutation, preconstruction rejection for both selected/injected versions, global resolver/proxy exclusion, cold/warm/post-idle calls, concurrent registry mutation, finite configurator child probe, private-pin/opaque-hook rejection with zero server streams, valid calls afterward, real TLS/authentication, cancellation, bounded active close, leak controls and complete current regression. Stress tests support the monitor/final-field proof rather than replacing it.

Direct hostile DNS TXT records and a wrong-host TLS case are not exercised. TXT callback exclusion is source-reviewed; untrusted-certificate rejection is directly tested. TLS uses JVM system trust; no trust-all path, custom asynchronous credential adapter or arbitrary transport extension is offered. Whole optional ITK compilation remains unavailable because its external proto checkout is absent; its migrated factory expression has selected type evidence.

The profile must be re-audited when grpc-java changes. It does not sandbox arbitrary JVM instrumentation/reflection or replacement of underlying network providers. No new wire header, session identifier, receipt, status RPC, automatic fallback or SDK retry guarantee is introduced. Native 0.3 retains its separate API. The target remains experimental RC with later protocol gates open.
''')
# Keep the detailed old-API/provisioning explanation while replacing obsolete admission guidance.
compat=p1/'compatibility/README.md'; old=(ev/'pre-resume/compatibility-README.md')
if not old.exists():
    old.write_bytes(compat.read_bytes())
text=old.read_text(); text=text[text.index('# Phase 1 client/default migration and compatibility evidence'):]
start=text.index('> Strict gRPC'); end=text.index('This experimental Phase 1',start)
text=text[:start]+'''The current user-approved gRPC profile deliberately rejects arbitrary raw channels and factories for both selected 1.0 and 1.1. Shared callers use SDK-managed options or `GrpcTransport.createManaged(...)`, with owned bounded cleanup. See [approved design](../grpc-channel-design.md), [current verification](../verification.md), and [final independent review](../evidence/resume-final-independent-review.md). Historical stopped admission guidance remains under the pre-resume evidence snapshot.

'''+text[end:]
text=text.replace('`new GrpcTransport(channel, card, "1.0")`','`GrpcTransport.createManaged(options, card, "1.0")`')
text=text.replace('Implicit direct JSONRPC/REST/gRPC card or URL constructors now select 1.1.','Implicit direct JSONRPC/REST card or URL constructors and the managed gRPC factory now select 1.1.')
text += '\nThe approved owned-channel runtime break is explicitly reported in the proposed Conventional Commit. Existing source/binary/wire probes are representative selected coverage, not complete A03 acceptance. Both existing example factories explicitly select 1.0; native 0.3 REST retains its correct non-tenant route. No outstanding task for those already completed corrections is implied.\n'
compat.write_text(text)
# Fill agent-prepared handoff and exact commit proposal from actual evidence.
handoff=(run/'drafts/next-session.md').read_text().replace('# Phase 1 handoff — final verification pending','# Phase 1 handoff — verified candidate awaiting commit approval')
handoff=handoff.replace('Three of the five resumed iterations are complete; iteration 4 is active for final whole-reactor verification. Iteration 5 is reserved for final independent review, evidence/integrity checks, and this handoff.','All five resumed iterations are complete after final independent review and evidence/integrity reconciliation (17 cumulative Phase 1 iterations). No implementation iteration remains authorized.')
handoff=handoff.replace('**Final verification/result: [COORDINATOR TO FILL after `resume-full-current-reactor-final` completes].**',f'**Final verification: 83 successful modules; {counts}; exit 0 within the 1,200-second command bound. All 123 skip identities match Phase 0 exactly.**')
handoff=handoff.replace('**Final independent verdict and integrity comparison: [COORDINATOR TO FILL after iteration 5].**','**Final independent review: no blocking findings for this Phase 1 candidate; final integrity and exact-source comparison passed. See the retained review, build-input hashes and integrity record. Commit approval is still absent.**')
handoff=handoff.replace('No final full-build pass or commit readiness is asserted until its actual result and the final review are recorded below.','The final result below applies only to these exact unchanged functional inputs.')
handoff=handoff.replace('The second full run, `resume-full-current-reactor-final`, is the current whole-reactor gate and remains pending in this draft.',f'The second full run, `resume-full-current-reactor-final`, passes the current whole-reactor gate: {counts}.')
handoff=handoff.replace('2. Read the fixed resumed authorization/accounting and actual full-run result. If the run is still active, let the coordinating process finish within its existing deadline; do not start concurrent Maven. If the budget is exhausted, stop implementation and report the checkpoint. A new session does not renew the budget or erase historical failures.','2. Read the fixed resumed authorization/accounting and actual full-run result. The five resumed iterations are exhausted at successful handoff. No build remains running and no implementation continuation is authorized by opening another session. A new session does not renew the budget or erase historical failures.')
(p1/'next-session.md').write_text(handoff)
proposal=(run/'drafts/commit-proposal.md').read_text().replace('# Phase 1 commit proposal — pending final verification and review','# Phase 1 commit proposal — verified, awaiting approval')
proposal=proposal.replace('No commit approval is requested by this draft. The final current whole-reactor result, final independent review, and refreshed integrity/scope comparison must be recorded before this proposal is presented for approval.','The final current whole-reactor result, final independent review and refreshed integrity/scope comparison support presenting this exact candidate for commit approval. Creating the commit still requires the user’s explicit approval.')
proposal=proposal.replace('the coordinator must fill current complete proposed-path totals after evidence refresh','the complete proposed-path inventory is refreshed after all administrative evidence is retained')
proposal=proposal.replace('**Final whole-reactor command/result: [COORDINATOR TO FILL from `resume-full-current-reactor-final`; do not infer a pass from focused tests].**',f'**Final whole-reactor command: `mvn -B -ntp clean install`, exit 0 within 1,200 seconds (`{label}`).**')
proposal=proposal.replace('**Final discovered/executed/skipped/failure/error and reactor totals: [COORDINATOR TO FILL].**',f'**83 successful modules; {counts}.**')
proposal=proposal.replace('**Final independent review, scope/hash integrity, and unchanged skip identities: [COORDINATOR TO FILL].**','**Final independent review finds no blocking defect; exact source hashes/diff/inventory and frozen-input integrity pass. All 123 skipped identities match Phase 0.**')
(p1/'commit-proposal.md').write_text(proposal)
print('Prepared six current Phase 1 documents, compatibility guidance and handoff from actual completed build metadata at',stamp)
