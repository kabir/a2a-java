package org.a2aproject.sdk.compat03.server.grpc.quarkus;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class AuthTestProfile_v0_3 implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                // Disable test security (injected test user) - we want REAL authentication
                Map.entry("quarkus.test.security.auth.enabled", "false"),

                // Enable embedded user store
                Map.entry("quarkus.security.users.embedded.enabled", "true"),
                Map.entry("quarkus.security.users.embedded.plain-text", "true"),
                Map.entry("quarkus.security.users.embedded.users.testuser", "testpass"),
                Map.entry("quarkus.security.users.embedded.roles.testuser", "user"),

                // Enable security in agent card
                Map.entry("test.agent.security.enabled", "true"),

                // Enable authorization so @Authenticated is enforced
                Map.entry("test.authorization.enabled", "true"),

                // Enable HTTP Basic authentication
                Map.entry("quarkus.http.auth.basic", "true")
        );
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
