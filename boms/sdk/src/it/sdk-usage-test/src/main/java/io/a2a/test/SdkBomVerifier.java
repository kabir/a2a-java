package io.a2a.test;

import io.a2a.bom.test.DynamicBomVerifier;

import java.util.Set;

/**
 * Verifies SDK BOM completeness by attempting to load all discovered classes.
 * Excludes paths not part of the SDK BOM scope.
 */
public class SdkBomVerifier extends DynamicBomVerifier {

    private static final Set<String> SDK_EXCLUSIONS = Set.of(
        "boms/",       // BOM test modules themselves
        "reference/",  // Reference implementations (in separate BOM)
        "examples/",   // Example applications
        "extras/",     // Extra modules (potential separate BOM)
        "tck/",        // TCK test suite
        "tests/"       // Integration tests
    );

    public SdkBomVerifier() {
        super(SDK_EXCLUSIONS);
    }

    public static void main(String[] args) throws Exception {
        new SdkBomVerifier().verify();
    }
}
