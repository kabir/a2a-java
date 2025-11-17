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
 * Subclass this and pass BOM-specific exclusions to the constructor.
 */
public abstract class DynamicBomVerifier {

    private final Set<String> excludedPaths;
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package\\s+([a-zA-Z0-9_.]+);");

    protected DynamicBomVerifier(Set<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }

    public void verify() throws Exception {
        // Project root is 4 levels up from test: {module}/src/main/java/...
        Path projectRoot = Paths.get("").toAbsolutePath().getParent().getParent().getParent().getParent().getParent();

        System.out.println("Scanning project root: " + projectRoot);

        Set<String> allClasses = discoverClasses(projectRoot);
        // Do some sanity checks for some classes from both top-level and nested modules to make sure the
        // discovery mechanism worked properly
        sanityCheckDisovery("io.a2a.spec.AgentCard", allClasses);
        sanityCheckDisovery("io.a2a.server.events.EventConsumer", allClasses);
        sanityCheckDisovery("io.a2a.client.transport.spi.ClientTransport", allClasses);

        System.out.println("Discovered " + allClasses.size() + " classes to verify");

        List<String> failures = new ArrayList<>();
        int successful = 0;

        for (String className : allClasses) {
            try {
                Class.forName(className);
                successful++;
            } catch (ClassNotFoundException e) {
                failures.add(className + " - NOT FOUND");
            } catch (NoClassDefFoundError e) {
                failures.add(className + " - MISSING DEPENDENCY: " + e.getMessage());
            } catch (Exception e) {
                failures.add(className + " - ERROR: " + e.getMessage());
            }
        }

        System.out.println("\n=== BOM Verification Results ===");
        System.out.println("Successfully loaded: " + successful + " classes");
        System.out.println("Failed to load: " + failures.size() + " classes");

        if (!failures.isEmpty()) {
            System.err.println("\n=== FAILURES ===");
            failures.forEach(System.err::println);
            System.err.println("\nBOM is INCOMPLETE - some classes cannot be loaded!");
            System.exit(1);
        }

        System.out.println("\n✅ BOM is COMPLETE - all classes successfully loaded!");
    }

    private void sanityCheckDisovery(String className, Set<String> discovered) {
        if (!discovered.contains(className)) {
            System.err.println("Class expected to be on the classpath was not discovered: " + className);
            System.exit(1);
        }
    }

    private Set<String> discoverClasses(Path projectRoot) throws IOException {
        Set<String> classes = new TreeSet<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> p.toString().contains("/src/main/java/"))
                 .filter(p -> !isExcluded(projectRoot.relativize(p).toString()))
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
