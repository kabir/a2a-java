package org.a2aproject.sdk.server.grpc.quarkus;

import jakarta.enterprise.inject.Instance;

import org.a2aproject.sdk.server.ResolvedInstance;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.transport.grpc.handler.CallContextFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies that {@link QuarkusGrpcHandler} does not eagerly resolve {@link Instance} parameters
 * during construction. This guards against the regression described in
 * <a href="https://github.com/a2aproject/a2a-java/issues/1033">issue #1033</a>, where eager
 * resolution prevented startup when the AgentCard producer depended on the HTTP server address.
 */
class QuarkusGrpcHandlerLazyResolutionTest {

    @Test
    void constructorDoesNotResolveAgentCardInstances() {
        Instance<AgentCard> throwOnGet = new ResolvedInstance<>(null) {
            @Override
            public AgentCard get() {
                throw new AssertionError("Instance.get() must not be called during construction");
            }

            @Override
            public boolean isUnsatisfied() {
                return false;
            }
        };

        @SuppressWarnings("unchecked")
        Instance<CallContextFactory> emptyCallContextFactory = (Instance<CallContextFactory>) (Instance<?>) new ResolvedInstance<>(null);

        assertDoesNotThrow(() -> new QuarkusGrpcHandler(
                throwOnGet,
                throwOnGet,
                null,
                emptyCallContextFactory,
                Runnable::run));
    }
}
