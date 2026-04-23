package org.a2aproject.sdk.server.apps.quarkus;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Quarkus test profile that enables security for authentication tests.
 * <p>
 * This profile:
 * <ul>
 *   <li>Enables embedded user store with a test user</li>
 *   <li>Configures HTTP Basic authentication</li>
 *   <li>Provides test credentials: username=testuser, password=testpass</li>
 * </ul>
 */
public class AuthTestProfile implements QuarkusTestProfile {

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
        // Use "test" profile to ensure test beans are active
        return "test";
    }
}
