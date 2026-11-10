package org.a2aproject.sdk.client.transport.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;

import org.a2aproject.sdk.spec.A2AClientException;
import org.junit.jupiter.api.Test;

class ClientProtocolVersionTest {
    @Test
    void supportsOnlyExactSharedMinorVersions() {
        assertEquals("1.1", ClientProtocolVersion.DEFAULT);
        assertEquals("1.0", ClientProtocolVersion.validate("1.0"));
        for (String version : new String[] {"0.3", "1.0.1", "1.2", "", " 1.1"}) {
            assertThrows(IllegalArgumentException.class, () -> ClientProtocolVersion.validate(version));
        }
    }

    @Test
    void normalizesSameVersionWithoutChangingInput() throws A2AClientException {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("a2a-version", "1.1");
        input.put("A2A-Version", "1.1");
        input.put("Authorization", "credential");
        Map<String, String> actual = ClientProtocolVersion.normalizeHeaders(input, "1.1");
        assertEquals(Map.of("A2A-Version", "1.1", "Authorization", "credential"), actual);
        assertEquals(3, input.size());
    }

    @Test
    void rejectsEveryConflictingValueIncludingCaseVariants() {
        for (String value : new String[] {"1.0", "", "1.1,1.0", "0.3"}) {
            assertThrows(A2AClientException.class, () -> ClientProtocolVersion.normalizeHeaders(
                    Map.of("a2a-version", "1.1", "A2A-VERSION", value), "1.1"));
        }
    }
}
