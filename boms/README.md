# A2A Java SDK - Bill of Materials (BOM)

This directory contains Bill of Materials (BOM) modules for the A2A Java SDK project, providing dependency management for external users.

## Overview

The A2A Java SDK provides three BOMs for different use cases:

1. **`a2a-java-sdk-bom`** - Core SDK BOM for general A2A agent development
2. **`a2a-java-sdk-extras-bom`** - Extras BOM with server-side enhancements (task stores, queue managers)
3. **`a2a-java-sdk-reference-bom`** - Reference implementations BOM with Quarkus dependencies

## BOM Modules

### SDK BOM (`boms/sdk`)

**Artifact:** `io.github.a2asdk:a2a-java-sdk-bom`

The SDK BOM includes:
- All A2A SDK core modules (spec, server, client, transport)
- Core third-party dependencies (Jackson, gRPC, SLF4J)
- Jakarta APIs (CDI, Inject, JSON, JAX-RS)
- Test utilities

**Use this BOM when:** Building A2A agents with any framework (Quarkus, Spring Boot, vanilla Java, etc.)

### Extras BOM (`boms/extras`)

**Artifact:** `io.github.a2asdk:a2a-java-sdk-extras-bom`

The Extras BOM includes:
- Everything from `a2a-java-sdk-bom` (via import)
- Server-side enhancement modules (database persistence, distributed queue management, etc.)

**Use this BOM when:** Building production A2A servers needing advanced server-side features beyond the core SDK

### Reference BOM (`boms/reference`)

**Artifact:** `io.github.a2asdk:a2a-java-sdk-reference-bom`

The Reference BOM includes:
- Everything from `a2a-java-sdk-bom` (via import)
- Quarkus BOM (complete Quarkus platform)
- A2A reference implementation modules (JSON-RPC, gRPC, REST)
- TCK module for testing

**Use this BOM when:** Building Quarkus-based A2A agents or reference implementations

## Usage

### For SDK Users (Any Framework)

Add to your project's `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.a2asdk</groupId>
            <artifactId>a2a-java-sdk-bom</artifactId>
            <version>0.4.0.Alpha1-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- No version needed - managed by BOM -->
    <dependency>
        <groupId>io.github.a2asdk</groupId>
        <artifactId>a2a-java-sdk-server-common</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.a2asdk</groupId>
        <artifactId>a2a-java-sdk-transport-jsonrpc</artifactId>
    </dependency>
</dependencies>
```

### For Extras Users (Database Persistence, Distributed Deployments)

Add to your project's `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.a2asdk</groupId>
            <artifactId>a2a-java-sdk-extras-bom</artifactId>
            <version>0.4.0.Alpha1-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- No version needed - managed by BOM -->
    <dependency>
        <groupId>io.github.a2asdk</groupId>
        <artifactId>a2a-java-sdk-server-common</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.a2asdk</groupId>
        <artifactId>a2a-java-extras-task-store-database-jpa</artifactId>
    </dependency>
</dependencies>
```

### For Quarkus Reference Implementation Users

Add to your project's `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.a2asdk</groupId>
            <artifactId>a2a-java-sdk-reference-bom</artifactId>
            <version>0.4.0.Alpha1-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- A2A SDK and Quarkus versions both managed -->
    <dependency>
        <groupId>io.github.a2asdk</groupId>
        <artifactId>a2a-java-sdk-reference-jsonrpc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-arc</artifactId>
    </dependency>
</dependencies>
```

## Internal Project Usage

**Important:** The A2A Java SDK project itself does **NOT** import these BOMs in the parent `pom.xml`.

### Why Not Use BOMs Internally?

Following the WildFly pattern, we keep BOMs separate from internal dependency management for several reasons:

1. **Build Order Complexity**: BOMs must be built before other modules, adding reactor ordering constraints
2. **Flexibility**: Parent pom may need different scopes/configurations than external users
3. **Simplicity**: Avoids potential circular dependencies and build complexity
4. **Single Source of Truth**: Parent `pom.xml` remains authoritative for internal builds

### Maintenance Strategy

- Parent `pom.xml` `<dependencyManagement>` is the **single source of truth** for versions
- BOMs are **maintained to match** parent pom versions
- When updating dependencies, update **both** parent pom and relevant BOMs
- BOMs are separate artifacts for **external consumption only**

## Automated Testing

All three BOMs include **maven-invoker-plugin** integration tests that automatically verify:
- ✅ BOM can be imported correctly
- ✅ All declared dependencies resolve
- ✅ No missing versions or conflicts
- ✅ Code using the BOM compiles successfully

### Test Structure

```
boms/
├── sdk/
│   └── src/it/
│       ├── settings.xml                 # Test repository config
│       └── sdk-usage-test/              # Integration test project
│           ├── pom.xml                  # Imports SDK BOM
│           └── src/main/java/           # Test code using SDK
├── extras/
│   └── src/it/
│       └── extras-usage-test/           # Integration test project
│           ├── pom.xml                  # Imports Extras BOM
│           └── src/main/java/           # Test code using SDK + Extras
└── reference/
    └── src/it/
        └── reference-usage-test/        # Integration test project
            ├── pom.xml                  # Imports Reference BOM
            └── src/main/java/           # Test code using Quarkus + SDK
```

### Running Tests

Tests run automatically during `mvn install`:

```bash
# Test all BOMs
mvn clean install -DskipTests -pl boms/sdk,boms/extras,boms/reference

# Test individual BOM
mvn clean install -DskipTests -pl boms/sdk
```

**What happens:**
1. BOM is installed to `target/local-repo/`
2. Test project builds using the BOM
3. If compilation succeeds → BOM is valid ✅
4. If dependencies missing → Build fails ❌

## Maintenance Guidelines

When updating dependencies in the project:

1. **Update parent `pom.xml`** - Change version properties and dependencyManagement
2. **Update SDK BOM** (`boms/sdk/pom.xml`) - Sync core dependency versions
3. **Update Extras BOM** (`boms/extras/pom.xml`) - Sync if extras modules changed
4. **Update Reference BOM** (`boms/reference/pom.xml`) - Sync if Quarkus or reference modules changed
5. **Run automated tests** - Integration tests will catch any missing dependencies:
   ```bash
   mvn clean install -DskipTests -pl boms/sdk,boms/extras,boms/reference
   ```

### Adding New Dependencies to BOMs

When adding new SDK modules or dependencies:

1. Add to appropriate BOM's `<dependencyManagement>`
2. Add usage example in `src/it/*/src/main/java/` test code
3. Run tests to verify compilation
4. Tests will fail if versions are missing or incorrect

## Version Alignment

The BOMs use `${project.version}` for all A2A SDK modules, ensuring:
- BOMs always reference the correct SDK version
- Version updates only need to change parent pom
- No version drift between BOMs and SDK modules

## Build Order

BOMs have explicit dependencies to ensure correct reactor build order:
- **SDK BOM** builds first (no BOM dependencies)
- **Extras BOM** builds second (depends on SDK BOM)
- **Reference BOM** builds third (depends on SDK BOM)

Maven's reactor automatically orders them correctly based on their `<dependencies>` declarations, regardless of their position in the parent pom's `<modules>` section.

## Examples

See the `examples/` directory for sample projects using these BOMs:
- `examples/helloworld/` - Basic agent using SDK BOM
- `examples/cloud-deployment/` - Quarkus-based agent using Reference BOM

## Release Process

When releasing the A2A Java SDK:

1. All three BOMs are released as separate Maven artifacts
2. External users can depend on them via Maven Central
3. BOMs follow the same version scheme as the SDK (e.g., `0.4.0.Alpha1`, `0.4.0`)

## Questions?

- **Which BOM should I use?**
  - Quarkus project → Reference BOM
  - Production server with database/distributed features → Extras BOM
  - Other framework/basic agent → SDK BOM

- **Can I use Extras BOM with Spring Boot?**
  - Yes! Extras BOM imports SDK BOM and adds server enhancements that work with any framework.

- **Can I use Reference BOM with Spring Boot?**
  - Yes, but you'll get unnecessary Quarkus dependencies. Use SDK BOM or Extras BOM instead.

- **Do I need multiple BOMs?**
  - No, choose one. Extras BOM imports SDK BOM. Reference BOM imports SDK BOM.

- **Why are test dependencies in SDK BOM?**
  - Test scope dependencies don't pollute runtime, but allow users to easily import test utilities.
