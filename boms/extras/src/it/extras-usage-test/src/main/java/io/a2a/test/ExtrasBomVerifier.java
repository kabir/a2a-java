package io.a2a.test;

import io.a2a.bom.test.DynamicBomVerifier;

import java.util.Set;

/**
 * Verifies Extras BOM completeness by attempting to load all discovered classes.
 * Includes SDK modules + Extras modules (task stores, queue managers, etc.).
 * Note: extras/ is NOT excluded - that's what we're testing!
 */
public class ExtrasBomVerifier extends DynamicBomVerifier {

    private static final Set<String> EXTRAS_EXCLUSIONS = Set.of(
        "boms/",       // BOM test modules themselves
        "reference/",  // Reference implementations (separate BOM)
        "examples/",   // Example applications
        "tck/",        // TCK test suite
        "tests/",      // Integration tests
        "extras/queue-manager-replicated/tests-multi-instance/",   // Test harness applications
        "extras/queue-manager-replicated/tests-single-instance/"   // Test harness applications
        // Note: extras/ production modules are NOT in this list - we want to verify those classes load
    );

    public ExtrasBomVerifier() {
        super(EXTRAS_EXCLUSIONS);
    }

    public static void main(String[] args) throws Exception {
        new ExtrasBomVerifier().verify();
    }
}
