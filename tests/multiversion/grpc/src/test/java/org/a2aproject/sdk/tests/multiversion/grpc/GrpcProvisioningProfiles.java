package org.a2aproject.sdk.tests.multiversion.grpc;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Separate boots are required because historical 0.3 requests have no tenant field. */
public final class GrpcProvisioningProfiles {
    private GrpcProvisioningProfiles() {
    }

    public abstract static class Subset implements QuarkusTestProfile {
        protected abstract String versions();

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("test.a2a.experimental", "true", "test.a2a.versions", versions());
        }

        @Override
        public String getConfigProfile() {
            return "test";
        }
    }

    public static class Subset1 extends Subset {
        @Override
        protected String versions() {
            return "0.3";
        }
    }

    public static class Subset2 extends Subset {
        @Override
        protected String versions() {
            return "1.0";
        }
    }

    public static class Subset3 extends Subset {
        @Override
        protected String versions() {
            return "0.3,1.0";
        }
    }

    public static class Subset4 extends Subset {
        @Override
        protected String versions() {
            return "1.1";
        }
    }

    public static class Subset5 extends Subset {
        @Override
        protected String versions() {
            return "0.3,1.1";
        }
    }

    public static class Subset6 extends Subset {
        @Override
        protected String versions() {
            return "1.0,1.1";
        }
    }

    public static class Subset7 extends Subset {
        @Override
        protected String versions() {
            return "0.3,1.0,1.1";
        }
    }
}
