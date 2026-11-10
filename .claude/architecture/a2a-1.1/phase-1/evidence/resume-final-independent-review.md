# Independent review of resumed Phase 1 candidate

**Current final verdict: no blocking findings for the reviewed Phase 1 candidate.** The current full build passed all 83 modules: 4,041 discovered / 3,918 executed / 123 skipped / zero failures or errors; all skipped identities exactly match Phase 0. Independent administrative checks verified 156 functional files, the complete 394-path proposed inventory, the exact source diff, byte-exact current report/source archives, frozen inputs and unchanged branch/HEAD/index. Remaining limitations and specification gates are accurately retained. This supports presenting the concrete Phase 1 commit proposal for the user's separate explicit approval; it does not authorize creating a commit, push, PR or Phase 2.

**Historical review log follows.** Earlier timed source, pending and failed-build sections preserve their original checkpoint meaning and are superseded by this verdict and the final administrative section below. None of the retained failed evidence is relabeled successful.

Scope: read-only inspection of the current support/a2a-1.1.0 worktree at HEAD e468dd70a415d17c19ae70d75750b6464d983ad1, current corrections/consumer migrations and retained root-run evidence. No Maven, source/test/document edits, staging, commits, push, PR or Phase 2 actions by this reviewer. Temporary review reports only. Applied verification-before-completion discipline: results below come from inspected actual metadata/logs, not agent completion assertions.

## Source disposition at 19:01Z

No blocking defect found in the currently reviewed resumed provider-isolation or fresh-provider cleanup implementation. The old findings are not silently waived: their concrete paths are closed by materially revised implementation under the user's explicit owned-channel compatibility/backend/lifecycle approval and later five-iteration authorization. Historical three material F1 failures, original eight Phase 1 iterations plus four correction iterations, and all Phase 0 accounting/breach remain historical. This review does not restart budgets or authorize a commit.

### Strict gRPC provider boundary

OwnedGrpcChannel checks the exact final PickFirstLoadBalancerProvider class and builds while holding the actual default LoadBalancerRegistry monitor. Independently checked exact cached 1.81.0 source: register/deregister/getProvider synchronize on this same monitor; ManagedChannelImpl construction synchronously constructs AutoConfiguredLoadBalancerFactory; that factory captures its defaultProvider in a final field. Both stock pick-first variants select helper-owned subchannels without a custom final-slot tracer. Later provider replacement, cold first calls and post-idle recreation use that captured provider rather than re-reading the registry. The earlier check/capture race and later programmable LB tracer path are therefore closed within this pinned backend profile.

A private resolver registry contains the SDK wrapper around stock DNS, selected with an explicit dns:/// URI. Both URI and io.grpc.Uri paths replace the original service-config parser with an inert error-producing parser using copied Args. disableServiceConfigLookUp separately forces empty applied policy. This prevents optional TXT config parsing from invoking a dynamic provider parser or supplying a replacement LB policy. The private no-proxy detector prevents gRPC from invoking global ProxySelector/Authenticator routing. Census callbacks are disabled; configurator rejection remains before construction and after build, with shutdown before returning any configured candidate. CallOptions credentials/tracers are rejected; SDK-issued identity, immutable private pin, endpoint/authority match, copied static authentication, atomic one-owner claim and bounded owner close remain enforced.

This is a source-proved implementation profile tied to grpc-java 1.81.0, not a stable public gRPC isolation API and not a sandbox for JVM-wide code instrumentation/reflection/network-provider replacement. The source must be re-audited on dependency upgrade. No new wire header, session, receipt, fallback, SDK resend or protocol retry guarantee was introduced.

### Fresh allocation ownership

GrpcTransportProvider captures the private SDK-issued factory exactly once, validates card/descriptor/version/tenant before allocation, and catches construction failures (including getInterceptors failures) to close only its freshly issued handle. The original failure is retained and cleanup failures are suppressed. Direct rejection of a reused/mismatched handle does not close another owner's channel. This closes the previously reported allocation leak without adding production testing hooks.

### Consumer migration and negative-test intent

Inspected migrated gRPC unit/provisioning/auth/public-client call sites and repository-wide remaining raw factory uses. Shared 1.x consumers use managed options; remaining ordinary production raw factories are native 0.3, whose API/ownership stay separate. Raw server-wire probes remain available to send intentionally invalid protocol metadata. Constructor tests obtain genuine SDK-issued handles and bind them to the selected endpoint; negative context/interceptor tests construct valid transports before asserting request rejection. Thus they cannot pass merely because the new raw-channel admission rule rejects earlier. The old late-tracer case explicitly asserts opaque admission rejection and zero actual server streams/ingress under the approved runtime break. Alternating versions use independent owners, with source/binary descriptors and non-null factory getter preserved.

Both helloworld and stream-lifecycle examples explicitly select protocol 1.0 and managed options, with client ownership cleanup. Existing native 0.3 factories remain unchanged. No frozen historical expected wire/codec output was regenerated by the migration. Legacy AgentInterface.CURRENT_PROTOCOL_VERSION remains 1.0; no Phase 0 files differ from HEAD.

## Independently inspected retained command evidence

- resume-provider-cleanup-red: 5 tests, 4 intended resource-leak assertion failures, zero errors/skips; exit 1; 4.242 seconds under 180-second deadline. Positive real Channelz allocation/close control passed.
- resume-global-provider-red: 5 tests, 3 assertion failures, zero errors/skips; exit 1; 8.593 seconds under 180-second deadline. Real raw Netty attack control demonstrates the programmable final-slot tracer; duplicate selected-version values also violate the exact-one contract.
- resume-owned-and-cleanup-green-final: 8 modules successful, 24 discovered/executed, zero failures/errors/skips; exit 0; 18:45:48.782108–18:45:57.405728Z, 8.624 seconds under 240-second deadline.
- resume-client-migration-regression: 12 modules successful, 916 discovered / 911 executed / 5 skipped / zero failures/errors; exit 0; 18:46:17.414898–18:46:38.821644Z, 21.409 seconds under 360-second deadline. No excluded GrpcChannelMetadataTest.
- resume-provider-extra-proof: 8 modules successful, 7 discovered/executed, zero failures/errors/skips; exit 0; 18:55:34.390793–18:55:44.242829Z, 9.852 seconds under 180-second deadline. Test source includes actual raw attack control, preconstruction rejection for both selected/injected versions, hostile global resolver exclusion, cold/warm/post-enterIdle calls, provider mutation stress with outcome counts, ProxySelector exclusion and a finite fresh-JVM configurator rejection probe.
- resume-tls-lifecycle-proof: 8 modules successful, 4 discovered/executed, zero failures/errors/skips; exit 0; 18:56:11.263538–18:56:15.007968Z, 3.744 seconds under 180-second deadline. Each test covers both selected versions using actual Netty HTTP/2 TLS: normal JVM trust/authentication, untrusted certificate rejection before RPC, caller cancellation observed by server plus owner reuse, and bounded active close/idempotence.

These selected commands are not additive unique test totals. The initially mislabeled green-compile-fixed command actually exits 1 due to the separately recorded test-harness null-completion assertion; its passing cleanup cases do not make that whole command successful. The final green command above is the complete successful focused execution.

## Remaining review/checkpoint obligations

The current root full clean install is still running; no complete current-reactor result is asserted here. Standalone consumers excluded from the root reactor require their promised compilation evidence. Final current-source hashes/diff/inventory, original skipped identities and immutable historical fixtures/inputs remain to be checked after the final edits. Existing top-of-file checkpoints in verification/review-response/design documents describe the prior stopped run and must be clearly superseded while preserving history.

Source review supports F1 closure for the supported pinned profile once appropriate final verification is complete. Direct tests for missing/wrong private pins and rejected opaque CallCredentials/explicit tracer factories were not present at the initial inspection; this approved acceptance-gate coverage was sent to the root for a bounded follow-up. Async credential support is deliberately absent; do not imply that asynchronous adapters/race handling were implemented or tested.

Nonblocking limits: DNS TXT adversarial records and explicit wrong-host TLS rejection are not directly exercised; TXT callback exclusion is source-reviewed and actual untrusted-certificate rejection is the retained TLS negative. The provider race stress is supporting evidence, not a replacement for the monitor/final-capture source proof. No full A03 or final 1.1 conformance claim follows from Phase 1. HTTP association, exact artifact/batch outcomes and cursor/recovery gates, including unresolved spec issue #2415, remain binding for later work.

Final verdict pending final current evidence/checkpoint update. No commit approval is implied.


## First current full-reactor result — failure retained

The first resumed full root `clean install` completed at 19:09:49.023480Z with exit 1 after 641.917 seconds, within its 1200-second deadline. Independent fresh-XML scan counted 299 reports, 4040 discovered / 3917 executed / 123 skipped / one failure / zero errors; the reactor summary has 78 successful modules and one failed module. All 123 skip identities exactly match Phase 0, with no newly skipped or missing baseline identity. Detailed independent counts/report hashes are in resume-final-independent-full-summary.json. This is not a passing full build.

Sole failure is GrpcProvisioningSubset1Test's second (historical 0.3 service) raw whitespace-header probe: expected INTERNAL, received UNAVAILABLE. The preceding shared-service whitespace probe passed the exact INTERNAL + PROTOCOL_ERROR assertions. Both requests reuse the same raw channel after intentionally malformed HTTP/2 input. Exact grpc-netty 1.81.0 source shows GOAWAY marks transport/unsent streams UNAVAILABLE while sent PROTOCOL_ERROR streams map to INTERNAL; this supports a preceding-connection shutdown race rather than proving a server version-policy regression. Source excerpts/hashes are retained in resume-whitespace-independent-source-review.json. A diagnostic run and justified harness isolation are needed; the expected transport status must not simply be widened, and zero application ingress must remain asserted.

The new GrpcOwnedCallAdmissionTest source addresses the requested private-pin/opaque-hook coverage: valid issued handles, reason-specific missing/wrong pin and credential/tracer rejections, zero executable hook callbacks and zero server streams, then successful call on the same owner for both versions. Execution is pending. The unreferenced superseded ProtocolVersionClientInterceptor production source was removed; historical source/evidence remain retained.

ITK is confirmed optional and depends on an absent external a2a-itk proto checkout. Its narrow managed-options expression is source/type coherent and identical APIs are compiled by ordinary consumers; lack of whole ITK compilation can be a nonblocking explicit verification limitation. Helloworld is in the root reactor, so it is not an excluded standalone verification obligation.

A successful appropriate current regression is still required before commit readiness. No commit approval requested or implied by this review.


## Bounded failure correction and current focused evidence

The diagnostic unchanged subset-1 rerun passed (exit 0, one test, 7.103 seconds). That alone was not treated as a passing full build or proof of cause. The test harness was then changed to create a separate real raw channel for each destructive malformed whitespace request, with finite shutdown/await. Exact INTERNAL and PROTOCOL_ERROR assertions are retained for each service, and application version capture must remain empty. Source inspection finds this correction isolates the known GOAWAY lifecycle without changing production behavior, weakening status expectations or synthesizing protocol guarantees. All seven subset profiles subsequently passed in resume-whitespace-isolated-channel-green (exit 0, seven tests, 19:14:01.943495–19:14:17.707507Z, 15.764 seconds under 180-second deadline).

resume-final-grpc-affected ran a clean install of client/transport/grpc with dependencies: exit 0, eight modules successful, 698 discovered/executed and zero skipped/failures/errors, 19:15:11.468075–19:15:40.293812Z, 28.826 seconds under a 300-second deadline. Independently checked the log totals and fresh GrpcOwnedCallAdmissionTest XML (one test, zero failures/errors/skips). This executes the requested pin/opaque-hook gate and cleanly recompiles after obsolete guard deletion.

No new blocking source defect found. Successful final current whole-reactor regression and final administrative/integrity checkpoint remain pending; the failed first whole-reactor result above stays retained.


Selected ITK evidence inspected: itk-type-probe/result.json records javac --release 17 -proc:none exit 0 for the exact migrated managed-options expression. Source, compiled class and classpath-file SHA-256 values independently match retained files, and compiler output is empty. This is narrow selected type evidence, not compilation of the optional ITK module or its unavailable external proto inputs.

Final root retry is running as resume-full-current-reactor-final, with all current production/test changes included. Its result will be recorded separately; the first failed full summary/archive is preserved.


## Successful final current whole-reactor evidence

Independently parsed actual metadata, the final root reactor summary and all fresh XML reports for resume-full-current-reactor-final. Command: mvn -B -ntp clean install. Start 19:16:29.023726Z; end 19:28:29.630519Z; elapsed 720.606 seconds; exit 0; no timeout under 1200-second bound. All 83 modules succeeded. The 300 fresh reports total 4041 discovered / 3918 executed / 123 skipped / zero failures/errors. All 123 (module, class, method) skip identities exactly match accepted Phase 0, with no newly skipped or missing baseline identity. No GrpcChannelMetadataTest exclusion or replacement status expectation was used. Independent per-report hashes/counts are retained in resume-full-current-reactor-final-independent-review-summary.json.

The module counter initially relied on dot leaders and undercounted long display names; corrected parsing of actual reactor rows confirms first full attempt had 78 SUCCESS / one FAILURE / four SKIPPED modules, final attempt 83 SUCCESS. First-run XML counts and failure remain intact; this is a reviewer parsing correction, not a changed Maven outcome.

This final clean build includes current production inputs, isolated destructive raw-wire probe harness, direct pin/opaque-hook test and removal of the obsolete partial guard. It covers actual paired/provisioning/auth/compatibility consumers and included examples. No broad build rerun is required for the remaining documentation/evidence finalization if source inputs remain identical.

Source and runtime verification support closing original F1, F2 and F3 within the approved experimental Phase 1 scope. The first failed full build remains retained and is superseded only by the justified harness correction plus actual successful rerun. Final current-source/hash/inventory and handoff review is still pending; no commit authorization is implied.


## Final independent administrative disposition

At the final source checkpoint, independently hashed every one of the 156 functional inputs against source-scope.json (73 modified / 83 new). All match. Decompressed source-and-tests.diff.gz is 695019 bytes, contains 156 file diff headers including the retained binary patch, and has SHA-256 853960af48a30475a94581a72b64f34a9de4564aebd13d749e26d6e3ebd3a4c3. A read-only git apply --check --reverse of that exact patch against the current tree exited 0; no source or index was changed. The complete git status inventory exactly equals all 394 proposed commit paths, including untracked files.

Independently verified branch support/a2a-1.1.0, unchanged full HEAD e468dd70a415d17c19ae70d75750b6464d983ad1 and clean/unchanged index; unchanged Phase 0/authoritative architecture/proto inputs and frozen released documentation; all 120 original fixture hashes, five retained Phase 1 fixture hashes and eleven archived original compiler-jar hashes; CURRENT_PROTOCOL_VERSION remains 1.0. The final report tar contains all 300 current XML reports byte-for-byte with their manifest/current-tree hashes. The durable source archive contains all 17 reviewed pinned source entries byte-for-byte with the original grpc-java 1.81.0 source archives and their archive SHA-256 values.

Independently reconciled all 58 bounded command records: 32 original / 12 correction / 14 resumed. No recorded timeout, overlapping interval or command/run deadline breach found. UTC and monotonic differences in the first resumed full run remain explicitly recorded; neither time basis is rewritten. Phase 0 historical 8 / A03 audit 10 / no-progress 0 / original breach, original Phase 1 eight plus four correction iterations and historical F1 failure count three are retained. The final administrative closure will record resumed 5/5 and cumulative 17; it supplies no additional implementation allowance and no commit permission.

Reviewed current verification, design, review response, compatibility guidance, next-session handoff, follow-up register and proposed Conventional Commit. They preserve experimental RC limitations, explicit approved BOTH-version gRPC runtime migration and bounded ownership, the failed first full run and exact-status harness correction, optional whole ITK omission with selected type evidence, source-only TXT exclusion and untrusted-peer TLS negative, incomplete A03 and binding later specification gates. The proposed message identifies the breaking behavior and ends with This fixes #1303. The requested examples-to-1.0/native-0.3-REST note is retained as completed rather than falsely deferred.

No blocking source, verification, scope or administrative finding remains in this reviewed candidate. The coordinating agent's final accounting/prose write and exact-inventory refresh can close iteration 5 without another build if these functional input hashes remain unchanged. Commit creation still requires the user's explicit approval. No Maven, repository source/test/docs edits, staging, commit, push, PR or Phase 2 work was performed by this reviewer.
