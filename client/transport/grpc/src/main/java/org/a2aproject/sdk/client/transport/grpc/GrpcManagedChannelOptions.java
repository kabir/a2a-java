package org.a2aproject.sdk.client.transport.grpc;

import java.time.Duration;
import java.util.Map;

import org.a2aproject.sdk.util.Assert;

/**
 * Declarative options for a dedicated SDK-owned Netty channel. TLS with system trust is the
 * default. Arbitrary gRPC interceptors, credentials, builders and channel sharing are not supported.
 * Channels use private stock DNS resolution and the audited stock pick-first provider. Global
 * resolver replacements, proxies and DNS service-config policy changes are not applied. A custom
 * global pick-first provider causes construction to fail. The isolation relies on pinned gRPC
 * 1.81.0 implementation details and must be re-audited when that dependency changes.
 */
public record GrpcManagedChannelOptions(boolean plaintext, Map<String, String> authenticationHeaders,
        Duration shutdownTimeout) {
    public GrpcManagedChannelOptions {
        Assert.checkNotNullParam("authenticationHeaders", authenticationHeaders);
        Assert.checkNotNullParam("shutdownTimeout", shutdownTimeout);
        authenticationHeaders = Map.copyOf(authenticationHeaders);
        if (shutdownTimeout.isNegative() || shutdownTimeout.isZero() || shutdownTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("shutdownTimeout must be positive and at most 30 seconds");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean plaintext;
        private Map<String, String> authenticationHeaders = Map.of();
        private Duration shutdownTimeout = Duration.ofSeconds(5);

        private Builder() { }

        public Builder usePlaintext() {
            plaintext = true;
            return this;
        }

        public Builder useTransportSecurity() {
            plaintext = false;
            return this;
        }

        /** Copies static authentication metadata; version conflicts reject before RPC start. */
        public Builder authentication(Map<String, String> headers) {
            authenticationHeaders = Map.copyOf(headers);
            return this;
        }

        public Builder shutdownTimeout(Duration timeout) {
            shutdownTimeout = timeout;
            return this;
        }

        public GrpcManagedChannelOptions build() {
            return new GrpcManagedChannelOptions(plaintext, authenticationHeaders, shutdownTimeout);
        }
    }
}
