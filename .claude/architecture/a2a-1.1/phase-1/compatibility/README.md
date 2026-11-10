# Phase 1 client/default migration and compatibility evidence

The current user-approved gRPC profile deliberately rejects arbitrary raw channels and factories for both selected 1.0 and 1.1. Shared callers use SDK-managed options or `GrpcTransport.createManaged(...)`, with owned bounded cleanup. See [approved design](../grpc-channel-design.md), [current verification](../verification.md), and [final independent review](../evidence/resume-final-independent-review.md). Historical stopped admission guidance remains under the pre-resume evidence snapshot.

This experimental Phase 1 keeps existing provider `create(config, card, interface)`
and ordinary transport/wrapper method descriptors. It adds the default provider
`capabilities()` method and `VersionedClientTransport` companion. Only `ORDINARY`
is declared here. This is not full A2A 1.1 conformance or later-phase capability
support.

## Client construction

New shared clients select exactly 1.1 by default. Keep an existing 1.0 peer pinned:

```java
Client client = Client.builder(card)
        .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.0").build())
        .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
        .build();
```

Optional `setEndpoint(url)` and `setTenant(tenant)` selectors filter descriptors
before binding preference. No matching exact descriptor fails construction; there
is no version negotiation or retry/fallback. `Client.getProtocolVersion()` reports
the frozen selection. Optional 0.3 clients remain separate.

Implicit direct JSONRPC/REST card or URL constructors and the managed gRPC factory now select 1.1. Use
`new JSONRPCTransport(url, "1.0")`, `new RestTransport(card, "1.0")` or
`GrpcTransport.createManaged(options, card, "1.0")` for explicit 1.0. Constructors receiving
an explicit `AgentInterface` use that descriptor. The gRPC provider uses
`GrpcTransport.forInterface(...)` so it does not discard endpoint/tenant/version.
`AgentInterface.CURRENT_PROTOCOL_VERSION` and its existing convenience descriptor
default remain **1.0**, independently of the new client default. No protocol model
or generated schema was changed.

## Providers and wrappers

An unchanged provider inherits only the fixed 1.0 ordinary profile. A provider
supporting experimental 1.1 must explicitly declare it and return a companion
reporting the selected version/capabilities. A legacy provider that ignores its
selected descriptor and calls a newly implicit-1.1 constructor is rejected when
constructing an explicit 1.0 client, rather than silently sending another version.

Every wrapper retains the intersection of provider/delegate/preceding-wrapper
capabilities. A plain legacy wrapper is accepted for explicit 1.0; it cannot carry
1.1. An exception from a configured 1.1 wrapper fails construction and names the
component. Explicit 1.0 retains historical log/continue on wrapper exceptions.
Both telemetry wrapper families carry the companion version/profile.

## Explicit experimental server provisioning

For a single configured REST endpoint, supply this optional CDI resolver and use
its same registration to build the advertised card. The endpoint URL must match
the actual configured server; the resolver belongs to that endpoint. This example
opts into incomplete experimental 1.1 only:

```java
@ApplicationScoped
public class ExperimentalRestRegistration implements InterfaceRegistrationResolver {
    public static final InterfaceRegistration REGISTRATION = new InterfaceRegistration(
            TransportProtocol.HTTP_JSON, "http://localhost:9999", null, Set.of("1.1"), true);

    @Override
    public @Nullable InterfaceRegistration resolve(TransportProtocol binding, @Nullable String tenant) {
        return binding == TransportProtocol.HTTP_JSON
                && AgentCardResolver.normalizeTenantForResolution(tenant) == null
                ? REGISTRATION : null;
    }
}

// In the existing AgentCard producer, on its otherwise configured builder:
cardBuilder.supportedInterfaces(ExperimentalRestRegistration.REGISTRATION.supportedInterfaces());
```

Use `TransportProtocol.JSONRPC` with its HTTP endpoint or `TransportProtocol.GRPC`
with its channel target for the corresponding binding. Register each configured
binding/endpoint/tenant independently; do not combine their version sets into a
global exposure policy. Protocol 0.3 has no tenants: any registration containing
0.3 must use the default tenant (null), and native REST `/v1/...` is a version
prefix at that configured endpoint. Tenant-bearing registrations are 1.x only.
A present resolver returning null disables that scope.
With no resolver, existing entrypoints retain their historical behavior.
An empty version set is invalid. Enabling 0.3 requires its optional adapter;
1.1-only packaging has no 0.3 runtime dependency.

Missing/empty/blank HTTP versions select 0.3 and reject in this 1.1-only example.
The shared 1.x gRPC service similarly rejects absent/empty metadata as 0.3, even
if a separate historical service exists. Whitespace-only gRPC metadata is rejected
by the tested HTTP/2 transport before application ingress; direct boundary tests
separately verify its normalization. No session, receipt or retry guarantee is
introduced. Experimental registration is not a complete 1.1 advertisement.

## Representative old-API probes

The three `Legacy*.java` sources were compiled with Java release17 using unchanged
Phase 0 installed SDK jars copied before any Phase 1 install. Their original class
files are retained in `client/base/src/test/resources/legacy-providers/legacy-providers.jar`.
This is representative old-API compilation, not released-provider provenance.
[Compiler-input hashes](legacy-compile-manifest.json),
[unchanged SDK compiler inputs](legacy-compile-sdk.tar.gz) and
[fixture provenance](fixture-provenance.json) identify the exact inputs.

`LegacyProviderCompatibilityTest` loads unchanged bytecode against the candidate,
uses the public builder and an actual HTTP GetTask request, tests fixed-constant
1.0 emission, unsupported1.1 before create, implicit-constructor delegate mismatch,
and plain/throwing legacy wrappers. The response is an exact copy of the original
Phase 0 frozen 1.0 fixture; candidate codecs never regenerate its expectation.
The same sources also compile against the candidate; retained evidence records
class-file identity and the separately executed source-recompiled wire cases.
Full command results and limits belong to the Phase 1 progress/evidence records.
These probes do not establish complete A03 acceptance.

The historical `freeze-old-client.py` is retained only to explain the initial capture.
Do not run it again after candidate installs: that would replace the old compiler
inputs. Use the retained archive and verify its manifest instead. External dependency
classpath entries and exact candidate compile command are retained in `../evidence/`.

The approved owned-channel runtime break is explicitly reported in the proposed Conventional Commit. Existing source/binary/wire probes are representative selected coverage, not complete A03 acceptance. Both existing example factories explicitly select 1.0; native 0.3 REST retains its correct non-tenant route. No outstanding task for those already completed corrections is implied.
