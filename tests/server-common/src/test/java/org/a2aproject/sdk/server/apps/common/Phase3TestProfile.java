package org.a2aproject.sdk.server.apps.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Shared P3-T17..T19 profile: the paired-version policy plus client-written artifacts, publication recovery enabled
 * with short bounds so a lost final publication is observable inside a test, and the spy queue manager so a scenario can tell
 * which events reached the replication and publication seams.
 */
public class Phase3TestProfile implements QuarkusTestProfile {

    /**
     * Selected CDI alternatives. A profile override replaces the module's {@code quarkus.arc.selected-alternatives},
     * so the module's own selections are re-listed by a transport profile through {@link #extraAlternatives()}.
     */
    protected List<String> extraAlternatives() {
        return List.of();
    }

    protected boolean userArtifacts() {
        return true;
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("test.a2a.experimental", "true");
        overrides.put("test.agent.user-artifacts", Boolean.toString(userArtifacts()));
        // Publication recovery is opt-in: it is off unless a deployment enables it, so the lost-publication
        // scenarios enable it explicitly, with short bounds.
        overrides.put("a2a.publication-recovery.enabled", "true");
        overrides.put("a2a.publication-recovery.interval.millis", "200");
        overrides.put("a2a.publication-recovery.read-deadline.millis", "1000");
        overrides.put("a2a.publication-recovery.gap-deadline.millis", "600");
        // The webhook receiver is a loopback HTTP server owned by the scenarios (Phase3Webhook).
        overrides.put("a2a.push-notification.url.allow-private-network-targets", "true");
        overrides.put("a2a.push-notification.url.allowed-schemes", "http,https");

        List<String> alternatives = new ArrayList<>(List.of(TestHttpClient.class.getName(),
                Phase3SpyQueueManager.class.getName()));
        alternatives.addAll(extraAlternatives());
        overrides.put("quarkus.arc.selected-alternatives", String.join(",", alternatives));
        return overrides;
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
