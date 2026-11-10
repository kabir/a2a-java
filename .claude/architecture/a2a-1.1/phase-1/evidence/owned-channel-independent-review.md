# Independent review of approved strict owned-channel implementation

Reviewer scope is read-only source/evidence inspection in the authorized worktree. No source/test/docs edits, Maven, staging, commit, push or PR by this reviewer. The user approved the specific strict owned-channel compatibility/backend/lifecycle design; that does not reset time/iterations or authorize a commit. The retained correction deadline is18:33:12Z, one final additional iteration remains, and the existing F1 conservative failed-material-cycle count is two.

## Starting checkpoint

At18:21:42Z no strict owned-channel implementation had landed yet. Reviewed the previously approved design: strict admission for BOTH1.0/1.1; reject arbitrary raw Channels/factories/programmable gRPC hooks before invocation/I/O; private SDK-issued identity, canonical endpoint/declarative TLS authority checks and atomic single-owner handles; controlled metadata authentication; owned shutdown; io.grpc:grpc-netty1.81.0 in the existing client transport module. No general backend/factory callback may silently enter the supported profile.

The design is not executable evidence. New strict transport tests, implementation inspection and actual results remain pending. F1 stays open until appropriate proof passes. Any third failed material implementation for the same F1 observable triggers the existing dependent stop; do not reset counts because the ownership design differs. Preimplementation absent-API compile evidence and test-harness mistakes must remain separate from meaningful behavioral RED/GREEN.

Material migration approval permits reporting updated runtime expectations for raw constructor/factory admission; it does not permit regenerating frozen historical codec/wire expectations or claiming pre-change historical compatibility is preserved. Existing SDK telemetry wrappers, immutable1.0/1.1 selection, no-fallback policy and original method descriptors/non-null factory getter remain review obligations.


## Source review at 18:28Z: changes still required

### P1 — SDK-owned channels still admit programmable global resolver/load-balancer callbacks after the metadata guard

`OwnedGrpcChannel.java:44` uses `NettyChannelBuilder.forTarget(expectedAuthority)` with the default registries. The before/after global-configurator checks and explicit Census stats/tracing disables are useful corrections, but they do not close the separately registered resolver/load-balancer path. `newCall` rejects only tracer factories present in caller CallOptions (`OwnedGrpcChannel.java:117`), and its purported final assertion is its own `streamCreated` callback (`:126`).

Read-only inspection of the exact locally cached gRPC 1.81.0 source archives establishes the path: `ManagedChannelImplBuilder` initializes its resolver registry from `NameResolverRegistry.getDefaultRegistry()`. `AutoConfiguredLoadBalancerFactory` initializes from `LoadBalancerRegistry.getDefaultRegistry()` and calls the selected provider's `newLoadBalancer`. Public `LoadBalancer.PickResult.withSubchannel(Subchannel, ClientStreamTracer.Factory)` permits the chosen provider to install a tracer. In `GrpcUtil.getClientStreamTracers:772–777`, normal CallOptions tracers (including the SDK guard) are constructed and the LAST tracer slot is reserved for the load balancer. `GrpcUtil.getTransportFromPickResult:714–728` invokes that load-balancer factory against the actual metadata and installs the returned tracer in the final slot. Consequently its `streamCreated` callback can mutate version metadata after the SDK assertion. This is the same ordering hazard as the earlier demonstrated later-tracer failure, through an extension point the owned implementation still admits.

This is a source-established admission/proof gap, not a new executed exploit probe. No new Maven or probe was run by this reviewer. Closing it requires a proved controlled provider/routing profile or rejection before invoking unknown providers, including later registry changes; a default policy string and a one-time registry inspection do not establish that profile. No approval to narrow the strict contract was found. F1 remains open.

### P2 — Public provider allocates before validating selected descriptor, without failure cleanup

`GrpcTransportProvider.java:28` invokes its SDK factory and builds a ManagedChannel, then `:30` calls `GrpcTransport.forInterface`. The latter validates protocolVersion, agentCard, and tenant before claiming the handle. An invalid public-provider descriptor (for example a malformed tenant or unsupported version) can therefore throw with the newly created channel left unclosed and no returned owner. Validate before creation or close only this freshly issued handle on construction failure. Do not close a caller's existing owner on reuse/mismatch rejection; those existing owner-preservation paths are correct and covered.

## Independently checked selected implementation evidence

`owned-channel-focused-proof.json` records the exact `mvn -B -ntp -pl client/transport/grpc -am -Dtest=GrpcOpaqueChannelAdmissionTest,GrpcOwnedChannelTest -Dsurefire.failIfNoSpecifiedTests=false test` invocation, start 18:25:36.084482Z, end 18:25:40.601220Z, elapsed 4.517 seconds, deadline 180 seconds, exit 0, no timeout. The log has eight successful modules. The two fresh XML reports total 14 discovered/executed, zero skipped/failures/errors: 2 opaque-channel admission tests and 12 owned-channel tests. XML mtimes are 18:25:39.922676Z and 18:25:40.173174Z; reviewed OwnedGrpcChannel source mtime 18:25:21.178234Z precedes that compilation.

The selected tests exercise actual Netty unary/streaming calls for both versions, default 1.1, copied static authentication metadata, conflicting context/authentication before server RPC, collapse of equal version values, non-null public factory/provider path, descriptor scope, reuse and concurrent owner rejection, lifecycle/version isolation and closed handles. These are useful evidence. They do not exercise the global provider path above, automatic configurator rejection, TLS interoperability, or the complete existing test inventory. Existing raw-channel/factory tests still require the explicitly approved runtime-behavior migration; no full final regression or compatibility closure can be inferred from this selected command. Frozen historical codec/wire expected output must remain unchanged.

The earlier affected regression (37 successful modules / 211 suites / 2901 discovered / 2802 executed / 99 skips / zero failures/errors) predates this material owned-channel compatibility change and cannot be carried forward as a current complete result. Historical original full-build counts likewise remain historical.

## Review verdict

Changes required; Phase 1 and F1 remain incomplete, with an additional provider-allocation cleanup issue. Do not claim commit readiness. Preserve the approved strict BOTH-version contract, the existing failure counters and deadline, all historical baseline/skip/fixture records, and report the source gap distinctly from the 14 green selected tests. No commit, push, PR, later-phase work, source edits or Maven were performed by this reviewer.
