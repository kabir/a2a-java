package org.a2aproject.sdk.server.apps.common;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Shared P3-T17..T19 profile: the paired-version policy plus client-written artifacts and short publication-recovery
 * bounds so a lost final publication is observable inside a test.
 */
public class Phase3TestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("test.a2a.experimental", "true",
                "test.agent.user-artifacts", "true",
                "a2a.publication-recovery.interval.millis", "200",
                "a2a.publication-recovery.read-deadline.millis", "1000",
                "a2a.publication-recovery.gap-deadline.millis", "600");
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
