package org.a2aproject.sdk.compat03.server.apps.quarkus;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class AuthTestProfile_v0_3 implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                // Disable the TestIdentityProvider - we want real authentication
                Map.entry("test.identity.auto-auth", "false"),

                // Enable security in AgentCard (server advertises Basic Auth support)
                Map.entry("test.agent.security.enabled", "true"),

                // Enable embedded user store
                Map.entry("quarkus.security.users.embedded.enabled", "true"),
                Map.entry("quarkus.security.users.embedded.plain-text", "true"),
                Map.entry("quarkus.security.users.embedded.users.testuser", "testpass"),
                Map.entry("quarkus.security.users.embedded.roles.testuser", "user"),

                // Enable HTTP Basic authentication
                Map.entry("quarkus.http.auth.basic", "true"),

                // Enable proactive authentication - authenticate at HTTP layer before route handler
                Map.entry("quarkus.http.auth.proactive", "true")
        );
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
