# Strict gRPC owned-channel design — approved and implemented

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
3. Hold the actual default LoadBalancerRegistry monitor across checking the exact final PickFirstLoadBalancerProvider class and completing builder.build(). The pinned register/deregister/getProvider methods synchronize on this same monitor. ManagedChannelImpl constructs AutoConfiguredLoadBalancerFactory synchronously; the latter captures its defaultProvider in a final field. Idle/recreation and later provider replacement therefore use the captured stock provider. The stock picker only selects helper-owned subchannels and supplies no programmable final tracer. A hostile installed provider is rejected without invoking its newLoadBalancer callback.
4. Before each owned call, require the private selected-version pin; reject arbitrary credentials and tracer factories. After all admitted SDK metadata contributions, normalize matching version entries to exactly one and reject every conflict before starting the backend. Authentication cannot override the selection. Retain duplicate/conflict server validation as defense in depth, not as a substitute for client rejection.

This closes the later load-balancer tracer path that defeated both the arbitrary-channel guard and the earlier ownership-only attempt. A before/after registry scan would still race; synchronized capture is essential. It also avoids a transport-only guard vulnerable to foreign-subchannel pickers. Source details and exact paths appear in [provider design review](evidence/resume-provider-design-review.md) and [implementation review](evidence/resume-implementation-review.md).

## Evidence and explicit limits

[Current verification](verification.md) and [final independent review](evidence/resume-final-independent-review.md) retain actual raw hostile-provider mutation, preconstruction rejection for both selected/injected versions, global resolver/proxy exclusion, cold/warm/post-idle calls, concurrent registry mutation, finite configurator child probe, private-pin/opaque-hook rejection with zero server streams, valid calls afterward, real TLS/authentication, cancellation, bounded active close, leak controls and complete current regression. Stress tests support the monitor/final-field proof rather than replacing it.

Direct hostile DNS TXT records and a wrong-host TLS case are not exercised. TXT callback exclusion is source-reviewed; untrusted-certificate rejection is directly tested. TLS uses JVM system trust; no trust-all path, custom asynchronous credential adapter or arbitrary transport extension is offered. Whole optional ITK compilation remains unavailable because its external proto checkout is absent; its migrated factory expression has selected type evidence.

The profile must be re-audited when grpc-java changes. It does not sandbox arbitrary JVM instrumentation/reflection or replacement of underlying network providers. No new wire header, session identifier, receipt, status RPC, automatic fallback or SDK retry guarantee is introduced. Native 0.3 retains its separate API. The target remains experimental RC with later protocol gates open.

Pinned upstream source entries cited in the independent provider reports are retained in the [audited source archive](evidence/resume-audited-grpc-source.tar.gz) and [source/archive hash manifest](evidence/resume-audited-grpc-source-manifest.json). Extract entry paths under the original grpc-source-review root to resolve historical temporary-file links.
