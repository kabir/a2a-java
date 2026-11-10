# SDK-owned channel lifecycle verification

Scope: new `GrpcOwnedLifecycleTest.java` and new `client/transport/grpc/src/test/resources/owned-tls/` only. No production edits. Existing cleanup report independently records root's 5/5 cleanup GREEN.

Prepared four actual Netty HTTP/2 tests, each covering protocol 1.0 and 1.1:

1. **Default TLS/system trust:** SDK default TLS options, static authorization, real `GrpcSslContexts` Netty TLS peer, unary and completed streaming calls. Temporary Java PKCS12 truststore contains the retained localhost certificate; normal `javax.net.ssl.trustStore` configuration is installed/restored under `ResourceLock(SYSTEM_PROPERTIES)`. Server observations require a TLS session, one selected-version header, and preserved authorization on every successful RPC. Successful HTTP/2 RPCs exercise the pinned backend's h2 ALPN negotiation.
2. **Untrusted TLS:** restore JVM default trust settings before creating each client, connect to the self-signed TLS peer, require SSLHandshakeException in unary and streaming failure causes, require zero stream events and zero server RPC/service calls. This checks actual certificate rejection without trust-all hooks or cached-trust assumptions; new channels use the active normal trust configuration.
3. **Caller cancellation:** hold a TLS stream open after a first real event; cancel the caller's `Context.CancellableContext`; require one client CANCELLED terminal error and one server `ServerCallStreamObserver` cancellation callback within bounded latches; then perform a unary RPC through the same owner with preserved version/authentication.
4. **Close active stream:** hold a TLS stream open; configure 500ms shutdown timeout; require close within configured timeout plus a one-second scheduling margin, client error and server cancellation within five-second latches, second close below one second, and closed-owner rejection with no extra server RPC.

All tests have a 30-second per-test limit, finite five-second request/latch/cleanup bounds, no sleeps, and real channels/server callbacks. The normal streaming completion callback (`errorConsumer.accept(null)`) is explicitly treated as successful completion. Channels, servers, deadline scheduler, trust properties, and temporary stores are cleaned up.

Fixture: OpenSSL-generated test-only RSA key and self-signed SAN certificate (`localhost`, `127.0.0.1`), validity 2026-11-10 18:46:54 UTC through 2036-11-07 18:46:54 UTC. README documents purpose and regeneration. No historical fixtures changed.

Hostname-mismatch limit: local socket probe showed a wildcard IPv4 listener was not reachable on `127.0.0.2` (two-second connect timeout), so use the authorized untrusted-peer alternative rather than an unreliable hostname mismatch case. Hostname verification is not disabled; this suite does not separately prove wrong-host rejection.

Verification-gap tests against already implemented behavior: no artificial production mutation to force RED. Root exclusively runs Maven. Ready for focused compilation/execution; results pending.
