package org.a2aproject.sdk.extras.multitenancy.tests;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.security.spi.runtime.AuthorizationController;

@Alternative
@Priority(Interceptor.Priority.LIBRARY_AFTER + 1)
@Singleton
public class TestAuthorizationController extends AuthorizationController {

    @ConfigProperty(name = "test.authorization.enabled", defaultValue = "false")
    boolean enabled;

    @Override
    public boolean isAuthorizationEnabled() {
        return enabled;
    }
}
