package org.a2aproject.sdk.tests.multiversion.grpc;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(GrpcProvisioningProfiles.Subset2.class)
public class GrpcProvisioningSubset2Test extends AbstractGrpcProvisioningTest {
}
