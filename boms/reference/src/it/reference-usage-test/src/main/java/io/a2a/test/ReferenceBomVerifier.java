package io.a2a.test;

import io.a2a.bom.test.DynamicBomVerifier;

import java.util.Set;

/**
 * Verifies Reference BOM completeness by attempting to load all discovered classes.
 * Includes SDK modules + Reference implementation modules.
 * Note: reference/ is NOT excluded - that's what we're testing!
 */
public class ReferenceBomVerifier extends DynamicBomVerifier {

    private static final Set<String> REFERENCE_EXCLUSIONS = Set.of(
        "boms/",       // BOM test modules themselves
        "examples/",   // Example applications
        "extras/",     // Extra modules (potential separate BOM)
        "tck/",        // TCK test suite
        "tests/"       // Integration tests
        // Note: reference/ is NOT in this list - we want to verify those classes load
    );

    public ReferenceBomVerifier() {
        super(REFERENCE_EXCLUSIONS);
    }

    public static void main(String[] args) throws Exception {
        new ReferenceBomVerifier().verify();
    }
}
