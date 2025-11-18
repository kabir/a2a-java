package io.a2a.bom.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base class for dynamically discovering and verifying all classes in a BOM can be loaded.
 * Subclass this and pass BOM-specific exclusions and forbidden paths to the constructor.
 *
 * - Excluded paths: Not tested at all (e.g., boms/, examples/, tck/, tests/)
 * - Forbidden paths: Must NOT be loadable (proves BOM doesn't include wrong dependencies)
 * - Required paths: Must be loadable (everything else)
 */
public abstract class DynamicBomVerifier {

    private final Set<String> excludedPaths;
    private final Set<String> forbiddenPaths;
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package\\s+([a-zA-Z0-9_.]+);");

    protected DynamicBomVerifier(Set<String> excludedPaths, Set<String> forbiddenPaths) {
        this.excludedPaths = excludedPaths;
        this.forbiddenPaths = forbiddenPaths;
    }

    // Backwards compatibility constructor for verifiers without forbidden paths
    protected DynamicBomVerifier(Set<String> excludedPaths) {
        this(excludedPaths, Set.of());
    }

    public void verify() throws Exception {
        // Project root is 4 levels up from test: {module}/src/main/java/...
        Path projectRoot = Paths.get("").toAbsolutePath().getParent().getParent().getParent().getParent().getParent();

        System.out.println("Scanning project root: " + projectRoot);

        Set<String> requiredClasses = discoverRequiredClasses(projectRoot);
        Set<String> forbiddenClasses = discoverForbiddenClasses(projectRoot);

        // Do some sanity checks for some classes from both top-level and nested modules to make sure the
        // discovery mechanism worked properly
        sanityCheckDisovery("io.a2a.spec.AgentCard", requiredClasses);
        sanityCheckDisovery("io.a2a.server.events.EventConsumer", requiredClasses);
        sanityCheckDisovery("io.a2a.client.transport.spi.ClientTransport", requiredClasses);

        System.out.println("Discovered " + requiredClasses.size() + " required classes to verify");
        System.out.println("Discovered " + forbiddenClasses.size() + " forbidden classes to verify");

        List<String> failures = new ArrayList<>();
        int successful = 0;

        // Test required classes - must be loadable
        for (String className : requiredClasses) {
            try {
                Class.forName(className);
                successful++;
            } catch (ClassNotFoundException e) {
                failures.add("[REQUIRED] " + className + " - NOT FOUND");
            } catch (NoClassDefFoundError e) {
                failures.add("[REQUIRED] " + className + " - MISSING DEPENDENCY: " + e.getMessage());
            } catch (Exception e) {
                failures.add("[REQUIRED] " + className + " - ERROR: " + e.getMessage());
            }
        }

        // Test forbidden classes - must NOT be loadable
        int correctlyForbidden = 0;
        for (String className : forbiddenClasses) {
            try {
                Class.forName(className);
                failures.add("[FORBIDDEN] " + className + " - INCORRECTLY LOADABLE (BOM includes dependency it shouldn't!)");
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // Expected - class should not be loadable
                correctlyForbidden++;
            } catch (Exception e) {
                failures.add("[FORBIDDEN] " + className + " - UNEXPECTED ERROR: " + e.getMessage());
            }
        }

        System.out.println("\n=== BOM Verification Results ===");
        System.out.println("Required classes successfully loaded: " + successful + "/" + requiredClasses.size());
        System.out.println("Forbidden classes correctly not loadable: " + correctlyForbidden + "/" + forbiddenClasses.size());
        System.out.println("Total failures: " + failures.size());

        if (!failures.isEmpty()) {
            System.err.println("\n=== FAILURES ===");
            failures.forEach(System.err::println);
            System.err.println("\nBOM verification FAILED!");
            System.exit(1);
        }

        System.out.println("\n✅ BOM is COMPLETE - all required classes loaded, all forbidden classes not loadable!");
    }

    private void sanityCheckDisovery(String className, Set<String> discovered) {
        if (!discovered.contains(className)) {
            System.err.println("Class expected to be on the classpath was not discovered: " + className);
            System.exit(1);
        }
    }

    /**
     * Discover classes that MUST be loadable (not excluded, not forbidden)
     */
    private Set<String> discoverRequiredClasses(Path projectRoot) throws IOException {
        return discoverClasses(projectRoot, relativePath -> !isExcluded(relativePath) && !isForbidden(relativePath));
    }

    /**
     * Discover classes that must NOT be loadable (forbidden but not excluded)
     */
    private Set<String> discoverForbiddenClasses(Path projectRoot) throws IOException {
        return discoverClasses(projectRoot, relativePath -> !isExcluded(relativePath) && isForbidden(relativePath));
    }

    /**
     * Common discovery logic with custom filter
     */
    private Set<String> discoverClasses(Path projectRoot, java.util.function.Predicate<String> pathFilter) throws IOException {
        Set<String> classes = new TreeSet<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> p.toString().contains("/src/main/java/"))
                 .filter(p -> pathFilter.test(projectRoot.relativize(p).toString()))
                 .forEach(javaFile -> {
                     try {
                         String className = extractClassName(javaFile);
                         if (className != null) {
                             classes.add(className);
                         }
                     } catch (IOException e) {
                         System.err.println("Failed to parse: " + javaFile + " - " + e.getMessage());
                     }
                 });
        }

        return classes;
    }

    private boolean isExcluded(String relativePath) {
        return excludedPaths.stream().anyMatch(relativePath::startsWith);
    }

    private boolean isForbidden(String relativePath) {
        return forbiddenPaths.stream().anyMatch(relativePath::startsWith);
    }

    private static String extractClassName(Path javaFile) throws IOException {
        // Extract simple class name from filename
        String fileName = javaFile.getFileName().toString();
        if (!fileName.endsWith(".java")) {
            return null;
        }
        String simpleClassName = fileName.substring(0, fileName.length() - 5); // Remove ".java"

        // Extract package name from file content
        String packageName = null;
        try (Stream<String> lines = Files.lines(javaFile)) {
            for (String line : lines.collect(Collectors.toList())) {
                Matcher pkgMatcher = PACKAGE_PATTERN.matcher(line.trim());
                if (pkgMatcher.matches()) {
                    packageName = pkgMatcher.group(1);
                    break;
                }
            }
        }

        if (packageName != null) {
            return packageName + "." + simpleClassName;
        }

        return null;
    }
}
