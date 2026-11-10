package org.a2aproject.sdk.server.apps.common;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Enables incomplete 1.1 policy only in the dedicated paired-client fixture. */
public class PairedVersionTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("test.a2a.experimental", "true");
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
