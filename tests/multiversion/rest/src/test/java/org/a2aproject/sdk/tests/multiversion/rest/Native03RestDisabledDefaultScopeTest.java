package org.a2aproject.sdk.tests.multiversion.rest;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.a2aproject.sdk.server.apps.common.PairedVersionTestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(Native03RestDisabledDefaultScopeTest.Default03Disabled.class)
public class Native03RestDisabledDefaultScopeTest {
    public static class Default03Disabled extends PairedVersionTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("test.a2a.experimental", "true", "test.a2a.versions", "1.0,1.1",
                    "test.a2a.v1-versions", "1.0");
        }
    }

    @Test
    public void testPublic03GetRejectsDisabledDefaultEndpoint() throws Exception {
        Public03ClientSupport.assertGetRejected("http://localhost:8081", "native03-disabled-task");
    }

    @Test
    public void testPublic03SendRejectsDisabledDefaultEndpoint() throws Exception {
        Public03ClientSupport.assertSendRejected("http://localhost:8081");
    }
}
