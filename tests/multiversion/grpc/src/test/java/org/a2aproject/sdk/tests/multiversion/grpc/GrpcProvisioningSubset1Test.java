package org.a2aproject.sdk.tests.multiversion.grpc;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(GrpcProvisioningProfiles.Subset1.class)
public class GrpcProvisioningSubset1Test extends AbstractGrpcProvisioningTest {
}
