# Shared gRPC consumer migration

Migration edits are ready for coordinating Maven validation; no Maven was run by this agent, and no compilation/test success is claimed. Fresh `git -c core.fsmonitor=false diff --check` exited 0.

All shared 1.x consumer factories now use SDK-owned `GrpcManagedChannelOptions`. Native 0.3 factories and direct raw server-wire probes remain unchanged. The inventory was extended to itk and client/base after a repository-wide Java search. No frozen released docs, Phase 0 files, or expected fixtures were edited by this migration.

Coverage preserved:
- GrpcTransportTest now uses a real ephemeral Netty server and SDK factory handles, tracks every handle/provider transport for close, preserves constructor/default/explicit-version/descriptor/tenant/request precedence tests, and tests alternating 1.0/1.1 transports on dedicated owned channels. Request/header negative tests construct their valid transport before assertions. Stream ingress observations use a latch because Netty is asynchronous.
- GrpcChannelMetadataTest now uses real Netty: both versions exercise conflicting owned authentication before unary/streaming ingress and identical case-variant normalization. The late raw-channel tracer case explicitly asserts opaque admission rejection and no server stream/ingress, under its renamed admission test.
- AuthenticationAuthorizationTest preserves streaming/nonstreaming UNAUTHENTICATED/PERMISSION_DENIED behavior on Netty; nonstreaming and streaming clients close.
- ClientBuilderTest selection/no-compatible-transport behavior remains, with owned options replacing unused null-producing factories.
- Reference auth SDK interceptors and task-authorization behavior remain. Raw server-version probes remain raw so they can send intentionally unsupported/missing metadata.
- Shared reference bases close SDK clients after each test instead of tracking raw channels in gRPC subclasses. Multitenancy already closes clients; obsolete raw channel tracking removed.
- Examples and dev docs use owned options; explicit stream-lifecycle protocol 1.0 remains, and example clients close.

API needs: no additional production API needed; nonnull getChannelFactory provides owned handles for constructor coverage. Unit fixture teardown casts these package-private handles to OwnedGrpcChannel solely to close every created handle (including rejected constructor candidates).

Files edited by this migration:
- reference/grpc/src/test/java/org/a2aproject/sdk/server/grpc/quarkus/Only11GrpcPackagingTest.java
- reference/grpc/src/test/java/org/a2aproject/sdk/server/grpc/quarkus/QuarkusA2AGrpcWithTaskAuthorizationTest.java
- reference/grpc/src/test/java/org/a2aproject/sdk/server/grpc/quarkus/QuarkusA2AGrpcWithAuthTest.java
- reference/grpc/src/test/java/org/a2aproject/sdk/server/grpc/quarkus/QuarkusA2AGrpcTest.java
- tests/multiversion/grpc/src/test/java/org/a2aproject/sdk/tests/multiversion/grpc/MultiVersionGrpcWithTaskAuthorizationTest.java
- tests/multiversion/grpc/src/test/java/org/a2aproject/sdk/tests/multiversion/grpc/MultiVersionGrpcWithAuthTest.java
- tests/multiversion/grpc/src/test/java/org/a2aproject/sdk/tests/multiversion/grpc/AbstractGrpcProvisioningTest.java
- tests/multiversion/grpc/src/test/java/org/a2aproject/sdk/tests/multiversion/grpc/MultiVersionGrpcTest.java
- tests/server-common/src/test/java/org/a2aproject/sdk/server/apps/common/AgentToAgentClientFactory.java
- extras/multitenancy/tests/grpc/src/test/java/org/a2aproject/sdk/extras/multitenancy/tests/grpc/MultiTenantGrpcTest.java
- client/transport/grpc/src/test/java/org/a2aproject/sdk/client/transport/grpc/GrpcTransportTest.java
- client/transport/grpc/src/test/java/org/a2aproject/sdk/client/transport/grpc/GrpcChannelMetadataTest.java
- client/base/src/test/java/org/a2aproject/sdk/client/AuthenticationAuthorizationTest.java
- client/base/src/test/java/org/a2aproject/sdk/client/ClientBuilderTest.java
- client/base/src/main/java/org/a2aproject/sdk/client/ClientBuilder.java
- itk/src/main/java/org/a2aproject/sdk/itk/AgentExecutorProducer.java
- examples/helloworld/client/src/main/java/org/a2aproject/sdk/examples/helloworld/client/HelloWorldClient.java
- examples/stream-lifecycle/client/src/main/java/org/a2aproject/sdk/examples/streamlifecycle/client/StreamLifecycleClient.java
- docs/content/dev/client.md
- tests/server-common/src/test/java/org/a2aproject/sdk/server/apps/common/AbstractA2AServerTest.java
- tests/server-common/src/test/java/org/a2aproject/sdk/server/apps/common/AbstractA2AServerWithAuthTest.java
- tests/server-common/src/test/java/org/a2aproject/sdk/server/apps/common/AbstractA2AServerWithTaskAuthorizationTest.java
