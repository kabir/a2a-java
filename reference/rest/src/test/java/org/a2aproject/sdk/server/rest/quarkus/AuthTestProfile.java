package org.a2aproject.sdk.server.rest.quarkus;

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
                // Disable test security (injected test user) - we want REAL authentication
                Map.entry("quarkus.test.security.auth.enabled", "false"),

                // Disable TestIdentityProvider auto-authentication (from JSON-RPC tests)
                Map.entry("test.identity.auto-auth", "false"),

                // Enable security in agent card
                Map.entry("test.agent.security.enabled", "true"),

                // Enable embedded user store
                Map.entry("quarkus.security.users.embedded.enabled", "true"),
                Map.entry("quarkus.security.users.embedded.plain-text", "true"),
                Map.entry("quarkus.security.users.embedded.users.testuser", "testpass"),
                Map.entry("quarkus.security.users.embedded.roles.testuser", "user"),

                // Enable HTTP Basic authentication
                Map.entry("quarkus.http.auth.basic", "true"),
                Map.entry("quarkus.http.auth.proactive", "true")
        );
    }

    @Override
    public String getConfigProfile() {
        // Use "test" profile to ensure test beans are active
        return "test";
    }
}
