package org.a2aproject.sdk.tests.multiversion.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.a2aproject.sdk.server.apps.common.DefaultEndpointProvisioningProfiles;

@QuarkusTest
@TestProfile(DefaultEndpointProvisioningProfiles.Subset5.class)
public class RestDefaultProvisioningSubset5Test extends AbstractRestDefaultProvisioningTest {
}
