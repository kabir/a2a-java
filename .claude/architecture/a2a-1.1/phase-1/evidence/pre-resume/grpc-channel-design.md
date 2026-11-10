> Current checkpoint: **Phase 1 incomplete; no commit approval requested.** The user explicitly approved the SDK-owned Netty channel/API/backend/lifecycle design for both 1.0 and 1.1. Its partial implementation passed 14 focused tests, but independent review found an admitted global load-balancer tracer can still mutate metadata after the SDK assertion. A newly allocated provider channel can also leak on descriptor-validation failure. Existing raw-channel/factory callers and tests still require migration. Stronger zero-server-stream assertions added after the focused build remain unrun. Earlier readiness/design-pending statements below are historical and superseded.
>
> Correction iteration 4 is closed: **4/4 additional iterations, 12 cumulative Phase 1; no-progress 0**. The owned attempt is conservatively the third materially failed F1 implementation; **the three-attempt safeguard stops further F1 work**. This is a source-established design failure, not a third executed failing probe. The original correction deadline remains 2026-11-10T18:33:12Z. Further implementation requires an explicitly authorized revised approach and renewed budget; material design approval already granted does not reset either limit. No commit, push, PR or Phase 2 work occurred.

# Strict gRPC construction design — compatibility approval pending

**Design only. F1 remains open and implementation is blocked pending a user decision.** No production/test edit, Maven command, protocol change or new implementation attempt accompanies this proposal. Retain the initial run's eight iterations, all Phase 0 accounting/breach, and the separate 17:33:12Z–18:33:12Z/four-iteration authorization. Keep the conservative two failed F1 implementation cycles and the failing later-tracer probe.

## Required invariant and present evidence

Both selected **1.0 and 1.1** must either send exactly one selected version or reject locally before an RPC request stream is admitted. Identical values normalize; a conflict cannot change the selected contract. Unary and streaming paths obey the same rule. Native optional0.3 remains separate and unchanged. A legacy raw-channel exception for1.0 would narrow the retained contract and is not recommended.

Current partial fix: four standard supplied-channel MetadataUtils cases and eighteen existing transport tests pass. The legitimate public later-factory demonstrator still fails: a supplied Channel interceptor appends a factory which adds1.0 after the SDK checked1.1. Server rejection is defense in depth and does not close this client failure. An adapter outside an already-built arbitrary Channel cannot inspect or control its inner routing, factories, callbacks or credentials.

## Recommendation: constrained SDK-owned construction

The initial strict profile must own channel construction and every metadata/routing extension point. Merely putting a last tracer in an owned channel is insufficient: a raw `io.grpc.ClientInterceptor` can ignore `next` and return a call from another channel, and asynchronous `streamCreated` exceptions have not been proved to provide safe per-call completion/cleanup.

Expose immutable, declarative target/TLS/timeout/message-limit/keepalive settings. The selected card descriptor supplies the endpoint; configuration cannot choose another endpoint after selection. Use the existing SDK `ClientCallInterceptor` payload/header API, whose returned headers are checked before I/O. Allow an SDK-controlled authentication/credential adapter which only contributes copied metadata. Do **not** expose arbitrary Channel, ChannelBuilder, builder-customizer Consumer, ManagedChannelProvider, raw gRPC ClientInterceptor, untrusted ClientStreamTracer.Factory, opaque CompositeChannelCredentials or routing callback as a strict-mode escape hatch.

Reject unsupported configuration before starting a call. Do not invoke an arbitrary channel factory to discover whether its returned channel appears safe. A wrapper that delegates to an SDK factory is still an arbitrary factory unless admitted through an explicit SDK-owned construction API; a marker implemented by user code is not proof.

These restrictions are material compatibility changes, including for legitimate instrumentation and all current raw-factory users. They require explicit approval. Existing SDK OpenTelemetry wrapper families remain supported/tested outside the channel; that does not imply arbitrary gRPC auto-instrumentation is supported. Do not silently remove an instrumentation wrapper and continue.

### Ordered supported path

1. ClientConfig/card selection freezes1.0 or1.1 and the exact endpoint/tenant as today. An internal per-call pin is carried using a private-identity CallOptions key or an equally SDK-owned bound-call object. A missing pin fails locally; dropping CallOptions never selects a default.
2. Existing SDK ClientCallInterceptor outputs are normalized. No raw user gRPC routing interceptor is installed. Any SDK-auth adapter copies and checks metadata. The SDK owns the base channel and its internal interceptor order.
3. An innermost SDK gRPC call guard verifies supported CallOptions before `delegate.start`: no untrusted tracer factory, unwrapped credentials or unknown routing hooks. It copies/checks all application version values and collapses identical values to the pin. A conflict fails before starting a transport call.
4. An SDK-owned tracer factory checks the merged headers before physical stream creation. Only audited core/backend callbacks may follow it. grpc-java1.81.0's internally appended retry-buffer tracer does not mutate version metadata; it is explicitly part of the supported dependency profile, not a class-name-based allowlist for arbitrary factories.
5. The owned credential applier validates every returned version value, copies the metadata, removes matching redundant version entries, and only then applies unrelated/authentication headers. Conflicts fail locally through MetadataApplier.fail. No hidden channel call credential may merge later.
6. A final SDK streamCreated assertion may verify the invariant before serialization, but must not be the ordinary rejection mechanism. Supported configuration has already removed untrusted later mutations. Any mismatch here is an SDK/backend invariant failure; fail-closed bounded error completion/cleanup must be proved before enabling this profile. An unproved throw from a tracing callback is not a design guarantee.
7. The audited backend may then serialize/start the request. Retain server-side duplicate validation without counting it as client pre-I/O enforcement.

`ClientInterceptors.intercept` makes the last listed interceptor outermost. Therefore an SDK guard installed first on an SDK-owned base is innermost; appending it after a user-configured builder would repeat the ordering mistake. The constrained profile avoids untrusted raw interceptors entirely; ordering is still tested against the supported library's internal instrumentation. Automatically installed instrumentation/providers require explicit admission/audit or early rejection, not an assumption that ownership alone makes them safe.

### Credentials and final metadata

Separate transport security from metadata-producing call credentials. Initial options expose audited TLS configuration (and explicit insecure testing configuration), not an opaque ChannelCredentials object. If a known composite is later admitted, split its inspectable transport/call parts and wrap every call component before channel creation.

The SDK authentication adapter must control MetadataApplier: snapshot returned metadata, compare every version against the pin, reject conflicts, strip matching redundant version entries, preserve all other headers and invoke apply/fail exactly once. Preserve asynchronous credentials, executor/context requirements, cancellation and original credential failures. Do not expose the mutable actual transport metadata to credential code. The initial API may accept only SDK-owned metadata contributors; accepting arbitrary CallCredentials requires this exact guarded adapter and its proof tests, not direct installation. No auth exception may bypass version checking.

## Public API and migration shape

Names below are illustrative; approval is requested for the semantics/restrictions.

```java
GrpcTransportConfig config = new GrpcTransportConfigBuilder()
    .managedChannel(GrpcManagedChannelOptions.builder()
        .transportSecurity(tlsOptions)
        .authentication(authMetadataProvider)
        .build())
    .addInterceptor(existingSdkClientCallInterceptor)
    .build();

GrpcTransport transport = GrpcTransport.createManaged(options, card, "1.0");
```

The named direct factory avoids adding overloads ambiguous with existing nullable arguments. An explicit1.1 factory/default path and explicit1.0 path use the same strict owned construction. Existing SDK interceptor configuration remains available.

Keep the descriptors of `GrpcTransportConfig(Function<String, Channel>)`, `getChannelFactory()`, `channelFactory(Function)` and all public direct Channel constructors. **The getter must remain non-null.** Implement owned mode internally using a private SDK-issued factory adapter, so owned configuration still has a valid Function getter; do not introduce a null factory field/getter shortcut. Build must reject simultaneous managed-options and raw-factory settings.

Provider admission checks the configuration's SDK-issued identity **before invoking its function**. Direct Channel constructors similarly accept only a verifiable SDK-issued handle. Recognition must come from an SDK-private implementation/issuance path, not a public marker an arbitrary Channel can implement. New SDK-owned target configuration is canonical; an SDK factory adapter can reduce edits at old factory call sites but cannot certify an arbitrary function or already-built ManagedChannel.

SDK-issued identity is necessary but insufficient. Bind every issued handle to
the canonical selected endpoint and its configured TLS/security authority. Direct
constructors compare the endpoint with the selected card descriptor and compare
authority with the declared TLS options (default derived from that endpoint);
mismatches reject before I/O. AgentInterface has no separate authority field,
and no model/wire field is proposed. An owned handle cannot silently select
another endpoint.
Minimal mode atomically grants each handle to exactly one transport. A second
claim is rejected before I/O even for the same endpoint/version; closing a
rejected client must not close the first client's channel. Closed handles cannot
be recycled. The SDK factory adapter issues a fresh handle for each construction.
Sharing requires the separately approved lease/pool design below.


| Existing/new construction | Proposed strict result | Compatibility impact |
| --- | --- | --- |
| Raw Channel constructor, either version | Local migration error before I/O unless the Channel is an SDK-issued owned handle | Binary/source descriptors remain; runtime behavior changes materially. |
| Arbitrary Function channelFactory, either version | Reject configuration before invoking the function | All existing such users need migration, including valid explicit1.0 users. |
| Existing factory signature with SDK-issued factory adapter | Accepted through the owned profile | Source shape may remain, but implementation/ownership changes. |
| New managed target/options mode | Accepted after all supported-profile proof gates pass | Recommended explicit ownership/configuration API. |

**Preserving signatures is not preservation of demonstrated old runtime behavior.** Strict admission for both versions is a material break requiring approval; preserving an unsafe1.0 exception would instead weaken the strict contract and contradict the user's current choice. Do not claim both unchanged raw-channel compatibility and this strict invariant. Optional native0.3 APIs are unaffected. Provider capability/delegate checks must not claim strict support for an opaque transport result.

### Ownership, shutdown and pooling

The current API leaves caller-provided channel shutdown with the caller. Owned mode must state its changed lifecycle explicitly. Minimal initial mode: one SDK-owned ManagedChannel per transport/client, with an atomic single-owner claim and early rejection of repeated handle admission, released by transport/client.close with finite graceful shutdown and bounded cancellation cleanup. A stream keeps its transport alive until completion/cancellation or explicit client close. No close may stop another client's channel accidentally.

Do not initially promise arbitrary shared-channel reuse or pooling. If reuse is approved, require an SDK-managed lease/pool with per-call pins, refcounts and separate explicit pool shutdown; prove that closing one client does not affect another and1.0/1.1 calls remain isolated. This is an additional compatibility/lifecycle decision. Returning one owned handle repeatedly from an arbitrary function must not create an untracked sharing path.

The backend decision is also explicit: use an audited supported grpc-java1.81.0 backend, recommended Netty, with a declared dependency profile. The concrete initial dependency proposal is `io.grpc:grpc-netty` in the existing `client/transport/grpc` module, using the parent-managed 1.81.0 version; no new production module is proposed. Existing client artifacts do not currently own a concrete ManagedChannel implementation. Adding a concrete backend dependency or optional module is outside the original no-material-change authorization and needs approval. A generic ServiceLoader provider or mutable builder callback cannot silently substitute an unreviewed backend behind the proof.

## Cancellation/retry proof obligations

Reject unsupported configuration/headers before start. A synchronous factory validation failure must cancel the delegate before rethrowing to release the existing core retry-stream registration. The current partial fix demonstrates that cleanup pattern for ordinary supplied headers, not every asynchronous path.

Use the credential applier failure path for asynchronous credential conflicts, and complete the caller once even with cancellation/deadline races. Do not rely on throwing from asynchronous tracing callbacks, shutdown the whole shared channel for a local conflict, or leak a pending applier. Keep the backend's existing transport retry behavior; add no SDK retry/resend. Every retry/substream retains the same pin and repeats validation. A final streamCreated assertion needs fail-closed bounded behavior tests, but ordinary input rejection must not depend on this late callback.

## Acceptance gates before F1 can close

- Preserve the current four standard-interceptor tests and the failing public later-factory probe; change its expected runtime result only after approval of explicit strict admission. Test that unsupported hooks/factories reject **before invocation**, not merely after their request reaches a server.
- Both versions; unary and streaming; ordinary same/conflicting duplicates; missing private pin; copied sync/async credentials with same/conflicting versions; unrelated Authorization metadata preserved; hidden channel credentials rejected; unsupported routing/tracing hooks rejected plainly.
- Real supported Netty transport, ready/cold connection, retry enabled/disabled, cancellation/deadline during async credentials, exact one-time completion and bounded resource cleanup. Observe client transport send plus zero server RPC streams/ingress for rejected calls.
- Old constructor/provider/source/binary link probes with the explicitly approved runtime migration result, no ambiguous overloads, non-null factory getter, early raw-factory rejection, both telemetry wrapper families and documented auto-instrumentation impacts.
- Dedicated endpoint/security-authority mismatch and atomic single-owner/reused/closed-handle rejection tests, plus ownership/close tests; shared lease tests only if that feature is approved. No version fallback, new skip or invented wire guarantee may make the matrix pass.

No implementation attempt is consumed by this document. A third failed material implementation at the same F1 criterion triggers the existing safeguard; do not reset its two failures because the admission design is different.

## Exact primary dependency provenance

Read these local archives for the dependency used by the retained tests: `/Users/kabir/.m2/repository/io/grpc/<artifact>/1.81.0/<artifact>-1.81.0-sources.jar`. Entry line numbers below refer to the archived sources, whose offsets can differ from a remote tag.

| Archive SHA-256 | Source entries / local line anchors | Finding |
| --- | --- | --- |
| grpc-api `997363465fde902cafb0b500b2e4dddc8163ca443aba7b2738f63711d133dc1c` | `io/grpc/ClientInterceptors.java:78–91`; `ClientStreamTracer.java:34–45,107–118`; `CompositeChannelCredentials.java:41–46` | Interceptor nesting; mutable factory/streamCreated headers; known composite parts inspectable. |
| grpc-core `d6fd286c3a94a16cab65fb48eba50250081891538f6031fc0756f55660670e87` | `io/grpc/internal/GrpcUtil.java:709–726`; `ManagedChannelImpl.java:465–528`; `StatsTraceContext.java:49–55` | Factory order and retry/nonretry creation, followed by streamCreated order. |
| same core archive | `CallCredentialsApplyingTransportFactory.java:114–125,173–179`; `MetadataApplierImpl.java:66–86` | Credentials merge after factory creation; apply/fail is a distinct completion boundary. |
| same core archive | `ClientCallImpl.java:247–250,283–292,459–487`; `RetriableStream.java:251–275,392–406`; `AbstractClientStream.java:154–160` | Start/cancellation timing, retry registration and headers written from start. |
| grpc-netty `9568deab9aee56f4a0e298f32d65f12af28bcb03e89db6f0f64aae70b33f5610` | `io/grpc/netty/NettyClientTransport.java:205–207`; `NettyClientStream.java:120–142` | streamCreated runs before NettyClientStream construction and header serialization/write. |

Primary upstream references: [API callbacks](https://github.com/grpc/grpc-java/blob/v1.81.0/api/src/main/java/io/grpc/ClientStreamTracer.java), [core ordering](https://github.com/grpc/grpc-java/blob/v1.81.0/core/src/main/java/io/grpc/internal/GrpcUtil.java), [Netty boundary](https://github.com/grpc/grpc-java/blob/v1.81.0/netty/src/main/java/io/grpc/netty/NettyClientTransport.java). These support the ordering analysis; they do not turn the proposed profile into tested implementation evidence.

**Decision required:** approve or revise strict owned-channel admission for both1.0/1.1, rejection of raw programmable routing/tracing hooks, the credential adapter, owned lifecycle and backend/dependency scope. Until then leave F1 open, retain the current failing demonstrator, make no further compatibility changes and do not claim this candidate commit-ready.
