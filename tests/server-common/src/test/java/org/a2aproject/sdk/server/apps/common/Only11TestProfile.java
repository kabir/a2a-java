package org.a2aproject.sdk.server.apps.common;

import java.util.Map;

/** Enables only experimental 1.1, including on classpaths without the optional 0.3 adapters. */
public class Only11TestProfile extends PairedVersionTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("test.a2a.experimental", "true", "test.a2a.versions", "1.1");
    }
}
