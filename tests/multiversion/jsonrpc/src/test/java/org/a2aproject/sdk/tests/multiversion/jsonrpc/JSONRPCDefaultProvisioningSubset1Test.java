package org.a2aproject.sdk.tests.multiversion.jsonrpc;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.a2aproject.sdk.server.apps.common.DefaultEndpointProvisioningProfiles;

@QuarkusTest
@TestProfile(DefaultEndpointProvisioningProfiles.Subset1.class)
public class JSONRPCDefaultProvisioningSubset1Test extends AbstractJSONRPCDefaultProvisioningTest {
}
