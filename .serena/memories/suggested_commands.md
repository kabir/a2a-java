# A2A Java SDK - Essential Commands

## Build & Installation

### Full Build with Tests
```bash
mvn clean install
```

### Fast Build (Skip Tests)
```bash
mvn clean install -DskipTests
```

### Build Specific Module
```bash
cd <module-name>
mvn clean install
```

## Development Workflow

### Run Examples with Hot Reload
```bash
cd examples/helloworld/server
mvn quarkus:dev
```

### Regenerate gRPC Classes
```bash
cd spec-grpc
mvn clean install -Pproto-compile
```

## Testing

### Run All Tests
```bash
mvn test
```

### Run Tests for Specific Module
```bash
cd <module-name>
mvn test
```

### Run Single Test Class
```bash
mvn test -Dtest=ClassNameTest
```

### Run TCK Compliance Tests
```bash
# 1. Start TCK server
cd tck
mvn quarkus:dev

# 2. In separate terminal, run external TCK suite
# (Requires Python TCK from https://github.com/a2aproject/a2a-tck)
```

## Code Quality

### Run Tests with Coverage
```bash
mvn clean verify
```

### Generate Javadoc
```bash
mvn javadoc:javadoc
```

## Release & Publishing

### Build with Sources and Javadoc
```bash
mvn clean install -Prelease
```

### Deploy to Maven Central
```bash
mvn clean deploy -Prelease
```

## Git Workflow

### Check Repository Status
```bash
git status
git branch
```

### Create Feature Branch
```bash
git checkout -b issue-<number>
```

### Update from Upstream
```bash
git fetch upstream
git merge upstream/main
```

## Project Structure Navigation

### List Modules
```bash
ls -d */
```

### Find Java Files
```bash
find . -name "*.java" -type f
```

### Search Codebase
```bash
grep -r "pattern" --include="*.java" .
```

## macOS-Specific Utilities

### File Operations
```bash
ls -la          # List all files with details
cd <directory>  # Change directory
pwd             # Print working directory
```

### Search & Find
```bash
find . -name "*.xml"     # Find files by name
grep -r "text" .         # Recursive text search
```

### Process Management
```bash
ps aux | grep java       # Find running Java processes
kill -9 <pid>            # Force kill process
```

## Quick Module Access

### Core Modules
- Server: `cd server-common`
- Client: `cd client/base`
- Spec: `cd spec`
- Examples: `cd examples/helloworld`
- Extras: `cd extras`

### Transport Implementations
- JSON-RPC Server: `cd transport/jsonrpc`
- gRPC Server: `cd transport/grpc`
- REST Server: `cd transport/rest`

### Reference Implementations
- JSON-RPC Ref: `cd reference/jsonrpc`
- gRPC Ref: `cd reference/grpc`
- REST Ref: `cd reference/rest`
