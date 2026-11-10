> Current checkpoint: **Phase 1 incomplete; no commit approval requested.** The user explicitly approved the SDK-owned Netty channel/API/backend/lifecycle design for both 1.0 and 1.1. Its partial implementation passed 14 focused tests, but independent review found an admitted global load-balancer tracer can still mutate metadata after the SDK assertion. A newly allocated provider channel can also leak on descriptor-validation failure. Existing raw-channel/factory callers and tests still require migration. Stronger zero-server-stream assertions added after the focused build remain unrun. Earlier readiness/design-pending statements below are historical and superseded.
>
> Correction iteration 4 is closed: **4/4 additional iterations, 12 cumulative Phase 1; no-progress 0**. The owned attempt is conservatively the third materially failed F1 implementation; **the three-attempt safeguard stops further F1 work**. This is a source-established design failure, not a third executed failing probe. The original correction deadline remains 2026-11-10T18:33:12Z. Further implementation requires an explicitly authorized revised approach and renewed budget; material design approval already granted does not reset either limit. No commit, push, PR or Phase 2 work occurred.

# Subsequent independent review — Phase 1 reopened

> Current correction checkpoint: F2/F3 resolved with selected regression/review;
> F1 strict contract remains open. User retained strictness and requested
> [design](grpc-channel-design.md) before material compatibility changes.
> The initial findings below and original counters remain historical evidence.

> Correction run now explicitly authorized: 17:33:12Z–18:33:12Z, four additional
> iterations; prior counters retained. Findings remain open pending verification.

Recorded 2026-11-10T17:32:28.033293+00:00. Branch and HEAD remain `support/a2a-1.1.0` /
`e468dd70a415d17c19ae70d75750b6464d983ad1`. No source/test changes or new build
were made in response. The index remains clean. The user's supplied independent
review reports bounded reproductions; this response independently inspected the
source paths, without claiming to have rerun those probes.

## Open findings and focused correction plan

1. **P1: supplied gRPC channel interceptors bypass selected-version validation.**
   `GrpcTransport.createBlockingStubWithMetadata` and its async counterpart attach
   selected metadata before the supplied channel's interceptor chain. The server
   interceptor uses `Metadata.get`, allowing the last value to govern policy.
   The supplied review reproduced `[1.1, 1.0]` and `[1.1, 1.1]` with the standard
   `MetadataUtils.newAttachHeadersInterceptor`. Existing SDK-interceptor tests
   do not cover this path. The exclusion in verification was not an approved
   contract decision and cannot close the finding.
   Add ordinary unary/streaming standard-channel regression cases for conflicting
   and identical values. Determine an enforceable correction preserving the
   approved pre-I/O conflict contract; do not promise that an arbitrary opaque
   Channel can be guarded without evidence. If the existing Channel API makes
   that impossible, present the concrete limitation and request an explicit
   contract decision rather than silently narrowing the guarantee. Also assess
   duplicate metadata handling at server ingress to prevent wrong-policy execution.
2. **P1: ordinary 1.1 REST subscription uses POST instead of GET.**
   Both shared client and single/multiversion reference routes use POST only.
   The pinned delta records the RC GET requirement. Matching candidate peers
   therefore concealed an interoperability defect. Add a GET-only independent
   server test for the 1.1 public client and a raw GET test for each reference
   route. Implement the selected-version verb boundary, preserve demonstrated
   historical 1.0 POST behavior and separate native 0.3 routes. No cursor or other
   later-phase behavior is needed to fix this finding.
3. **P2: native 0.3 gRPC loses registration/original-version enforcement.**
   The reference interceptor validates a local registration but does not propagate
   it. `GrpcHandler_v0_3.createCallContext` creates an unregistered context or
   returns a custom factory result unchecked. The supplied probe observed null
   registration/binding at the converted core and accepted a factory version1.1.
   Add downstream core assertions for binding, registration and original0.3,
   plus a conflicting-factory negative requiring zero core execution. Carry the
   admitted registration across the context boundary and enforce it before
   delegation, while preserving the legacy no-resolver adapter contract.

Each correction needs failing evidence, a focused passing check, regression
verification appropriate to actual changes and independent re-review. Refresh
source hashes/diff/inventory and verification/handoff after any approved fixes.
Do not regenerate frozen expectations or implement later-phase functionality.

## What remains valid and what is withdrawn

The prior clean build's 83 modules, 3,987 discovered / 3,864 executed / 123 skipped
and zero failures/errors remain supported by retained evidence. They do not prove
these omitted cases. Historical fixture hashes, compatibility provenance, actual
subset coverage, example pins and 0.3 no-tenant correction remain evidence within
their documented limits. Earlier independent reports are preserved as historical
reviews; their no-blockers conclusions are superseded by these three findings.
**Commit readiness is withdrawn. No commit approval is being requested now.**

## Authority and counters

Original Phase1 authorization began14:57:25Z and ended16:57:25Z on2026-11-10.
It had closed8/10 iterations before this review; no implementation iteration9
has started. That authorization is now expired for further implementation.
Read-only verification and this corrective checkpoint do not start a fresh run.
Request explicit additional time/iteration authorization before source fixes.

Keep Phase0 completed8/A03conservative10/no-progress0/originalbreachtrue and all
Phase1 failed-cycle records. Map these independent reproductions to the existing
stable observables before the next fix/verify cycle; do not reset counts or split
criteria to avoid the three-attempt safeguard. No new fix cycle has been attempted
here. Any new authorization must retain the historical counters, finite command
deadlines, Phase1-only scope and separate explicit commit approval.

Experimental RC status and #2415/HTTP association, exact artifact/batch outcomes,
cursor/recovery and final-spec gates remain binding. No commit, push, PR or Phase2.
