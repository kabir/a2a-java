# A2A Java SDK - Project Structure

## Module Organization (28 Modules)

```
a2a-java/
├── spec/                          # Protocol data models (111 Java files)
├── spec-grpc/                     # gRPC protocol buffers
├── common/                        # Shared utilities
├── http-client/                   # HTTP abstraction layer
├── server-common/                 # Core server logic (runtime-agnostic)
├── transport/                     # Transport implementations
│   ├── jsonrpc/                   # JSON-RPC server transport
│   ├── grpc/                      # gRPC server transport
│   └── rest/                      # REST server transport
├── client/                        # Client SDK
│   ├── base/                      # Client, ClientBuilder, A2A utilities
│   └── transport/
│       ├── spi/                   # ClientTransport interface
│       ├── jsonrpc/               # JSON-RPC client
│       ├── grpc/                  # gRPC client
│       └── rest/                  # REST client
├── reference/                     # Quarkus reference implementations
│   ├── common/                    # Shared reference code
│   ├── jsonrpc/                   # Quarkus + JSON-RPC server
│   ├── grpc/                      # Quarkus + gRPC server
│   └── rest/                      # Quarkus + REST server
├── examples/                      # Working examples
│   ├── helloworld/
│   │   ├── client/                # Java client example
│   │   └── server/                # Java server example
│   └── cloud-deployment/server/   # Cloud deployment example
├── extras/                        # Optional production features
│   ├── task-store-database-jpa/   # Database persistence for tasks
│   ├── push-notification-config-store-database-jpa/
│   └── queue-manager-replicated/  # Distributed event replication
├── tck/                           # Technology Compatibility Kit
└── tests/                         # Shared test utilities
```

## Key Modules Deep Dive

### spec/ - Protocol Definitions
**Purpose**: Core A2A protocol data models
**Key Classes**:
- `AgentCard`: Agent metadata and capabilities
- `Task`: Task state and lifecycle
- `Message`: User messages and parts
- `Event`: Task events (status updates, artifacts)
- `Part` hierarchy: TextPart, FilePart, DataPart
- `JSONRPCError`: Error types

### server-common/ - Server Core
**Purpose**: Runtime-agnostic server implementation
**Subpackages**:
- `agentexecution/`: AgentExecutor interface, RequestContext
- `events/`: EventQueue, QueueManager, EventConsumer
- `requesthandlers/`: DefaultRequestHandler (main entry)
- `tasks/`: TaskManager, TaskStore, TaskUpdater, ResultAggregator
- `auth/`: Authentication interfaces

**Critical Flow**: All transports → DefaultRequestHandler

### transport/* - Server Transports
**Purpose**: Protocol-specific server implementations
- `jsonrpc/`: JSON-RPC 2.0 over HTTP
- `grpc/`: gRPC with protobuf
- `rest/`: HTTP+JSON RESTful API

### client/base/ - Client SDK
**Purpose**: Client implementation for connecting to A2A servers
**Key Classes**:
- `Client`: Main client interface
- `ClientBuilder`: Fluent builder for client configuration
- `A2A`: Utility class (getAgentCard, toUserMessage, etc.)

### client/transport/* - Client Transports
**Purpose**: Protocol-specific client implementations
- `spi/`: ClientTransport interface (SPI)
- `jsonrpc/`, `grpc/`, `rest/`: Implementations

### reference/* - Quarkus Implementations
**Purpose**: Production-ready Quarkus-based servers
**Use Case**: 
- Examples and testing
- Starting point for custom implementations
- Demonstrates CDI integration

### examples/ - Working Examples
**helloworld/client/**: Java client → Python server
**helloworld/server/**: Python client → Java server
**cloud-deployment/**: Kubernetes deployment example

### extras/ - Production Features
**task-store-database-jpa/**: RDBMS persistence for tasks
**push-notification-config-store-database-jpa/**: RDBMS for push configs
**queue-manager-replicated/**: Distributed queue replication via Kafka

## Dependency Flow

```
Application Code
    ↓ uses
reference/* (jsonrpc/grpc/rest)
    ↓ depends on
server-common + transport/*
    ↓ depends on
spec + common
```

## Directory Conventions

### Source Structure (per module)
```
<module>/
├── src/
│   ├── main/
│   │   ├── java/          # Java source code
│   │   └── resources/     # Configuration files
│   └── test/
│       ├── java/          # Test code
│       └── resources/     # Test resources
├── pom.xml                # Module build configuration
└── README.md              # Module documentation
```

### Important Files

**Root Level**:
- `pom.xml`: Parent POM with dependency management
- `README.md`: Main project documentation
- `CONTRIBUTING.md`: Contribution guidelines
- `CONTRIBUTING_INTEGRATIONS.md`: Integration contribution guide
- `LICENSE`: Apache 2.0 license
- `COMMUNITY_ARTICLES.md`: External resources

**Configuration**:
- `.gitignore`: Git ignore rules
- `.github/workflows/`: CI/CD pipelines
- `.serena/`: Serena MCP project configuration
- `.claude/`: Claude Code project documentation

## Build Artifacts
```
<module>/target/
├── classes/               # Compiled .class files
├── test-classes/          # Compiled test classes
├── *.jar                  # Built JAR artifact
├── surefire-reports/      # Test results
└── javadoc/              # Generated JavaDoc
```

## Module Categories

### Core (Required)
- spec, common, http-client

### Server (Choose based on transport)
- server-common (always)
- transport/jsonrpc OR transport/grpc OR transport/rest
- reference/jsonrpc OR reference/grpc OR reference/rest

### Client (Optional)
- client/base
- client/transport/spi
- client/transport/* (one or more)

### Extras (Optional)
- Any from extras/ based on needs

### Development (Non-production)
- examples/, tck/, tests/

## Special Directories

### spec-grpc/
**Purpose**: Protocol buffer definitions and generated code
**Regeneration**: `cd spec-grpc && mvn clean install -Pproto-compile`
**Source**: `a2a.proto` from https://github.com/a2aproject/A2A

### tck/
**Purpose**: Technology Compatibility Kit server
**Usage**: Test A2A protocol compliance
**External Tests**: https://github.com/a2aproject/a2a-tck

### .serena/
**Purpose**: Serena MCP project configuration
**Contents**: project.yml with project metadata and settings
