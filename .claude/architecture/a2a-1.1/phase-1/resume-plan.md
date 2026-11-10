# Phase 1 resumed provider isolation plan

Authorization: user granted five more focused iterations after review and estimate; approved owned Netty/API/backend/lifecycle changes for both 1.0 and 1.1 persist. No commit/push/PR/Phase 2. Deadline 2026-11-10T20:37:06Z. Historical counters and failed designs remain historical; resumed failures counted separately under the three-attempt safeguard.

## Iteration 1 — source investigation and independent design checkpoint

Exact grpc 1.81.0 source confirms the final default LB provider is captured synchronously inside channel build; registry get/register/deregister are synchronized on the same registry. Both stock pick-first implementations use owned subchannels without LB tracers. A transport-only guard is rejected because foreign subchannels bypass it. A random policy name or uncoordinated registry scan is rejected because neither proves identity or prevents races.

Chosen revision: create an SDK-private resolver registry via deprecated public nameResolverFactory(new final DnsNameResolverProvider()) and explicit dns:/// target, preserving stock DNS refresh and multiaddress behavior. Disable service-config lookup and leave default config null. Under the default LoadBalancerRegistry instance monitor require exact final PickFirstLoadBalancerProvider.class identity for pick_first, then build before releasing that monitor. Later registrations cannot replace the captured final provider, and discarded service configs cannot select another provider. Keep Census disabled and existing configurator rejection. This relies explicitly on pinned internal 1.81.0 code, not a stable public isolation contract. No protocol header or guarantee is added. A dependency upgrade must re-audit this boundary.

Files: OwnedGrpcChannel.java constructor plus a private construction helper, GrpcGlobalProviderIsolationTest.java for actual Netty unary and streaming provider injection. No public API addition is needed. Existing options and wrapper behavior stay as approved. Tests reject hostile same-policy providers before allocation and before newLoadBalancer invocation for both versions; ignore hostile global resolver; after construction replace providers before first call and between calls; prove singleton selected metadata and zero hostile callbacks; concurrent registry mutation yields only local rejection or pinned successful construction.

## Iteration 2 — one enforcement fix/verify cycle

Write adversarial tests and run bounded RED against current source. Apply only the chosen provider/resolver isolation revision. Run targeted owned/admission/provider tests and independent review. Count a materially failed revised implementation even inside this iteration; no relabeling counter reset.

## Iteration 3 — provider cleanup and consumer migration

GrpcTransportProvider.java validates input before allocation and defensively closes only fresh SDK-owned handles on failed construction. GrpcProviderCleanupTest.java proves invalid input before allocation, valid owner still usable. Migration agent owns existing client tests, reference/paired/provisioning/auth tests, extras, examples and dev docs. Use managedChannel options for both versions, copied SDK auth metadata instead of raw interceptors; real Netty test peers; intended negative requests construct transports outside assertion lambdas. Keep separate compat-0.3 clients and all frozen expectations. Preserve test intent rather than accepting unrelated admission exceptions.

## Iteration 4 — current regression and targeted lifecycle evidence

Root alone runs Maven; all commands through bounded.py with <=1200 seconds and remaining run time. Targeted client reactor first, then affected/full root clean install once consumers compile. Record fresh XML counts and exact skip identities. Add appropriate real TLS/default-trust, cancellation, cold/warm and ownership checks. No unchanged baseline rerun or codec expected-output regeneration.

## Iteration 5 — independent review and handoff

Obtain independent review of current code, meaningful tests, actual command evidence and compatibility impact. Bound any material corrections by remaining attempts, iterations and time. Refresh current diff/hashes/inventory, verification, progress, blocker state and next-session handoff. Only if Phase 1 actually complete ask separately for commit approval with Conventional Commit footer This fixes #1303. Never advance Phase 2 or commit/push/create PR automatically.
