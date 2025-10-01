# A2A Java SDK - Technology Stack

## Language & Build
- **Java**: 17+ (minimum requirement)
- **Build Tool**: Apache Maven 3.9+
- **Encoding**: UTF-8
- **Compiler**: `-parameters` flag enabled for method parameter names

## Core Frameworks & Libraries

### Dependency Injection & Jakarta EE
- **Jakarta CDI**: 4.1.0 (Enterprise Context and Dependency Injection)
- **Jakarta Inject**: 2.0.1
- **Jakarta JSON**: 2.1.3
- **Jakarta WebSocket/REST**: 3.1.0

### Runtime Platform
- **Quarkus**: 3.28.1 (Reference implementation runtime)
  - Used for JSON-RPC, gRPC, and REST reference implementations
  - Provides CDI, RESTEasy, SmallRye integrations

### Communication Protocols
- **gRPC**: 1.73.0 (with protobuf 4.31.1)
- **JSON-RPC 2.0**: Custom implementation over HTTP
- **HTTP+JSON/REST**: JAX-RS based

### Data & Serialization
- **Jackson**: 2.17.0 (JSON serialization/deserialization)
  - jackson-databind
  - jackson-datatype-jsr310 (Java 8 date/time support)
- **Protocol Buffers**: 4.31.1 (gRPC protocol definitions)

### Reactive & Async
- **Mutiny Zero**: 1.1.1 (Lightweight reactive utilities)
- **Java Flow API**: Native reactive streams (java.util.concurrent.Flow)

### Testing
- **JUnit 5**: 5.13.4 (junit-jupiter)
- **Mockito**: 5.17.0
- **REST Assured**: 5.5.1 (API testing)
- **MockServer**: 5.15.0 (HTTP mocking)

### Logging
- **SLF4J**: 2.0.17 (API)
- **Logback**: 1.5.18 (Implementation, test scope)

## Database Support (Optional - Extras)
- **JPA/Hibernate**: Via Quarkus Hibernate ORM
- **PostgreSQL**: Primary database for examples
- **Kafka**: For distributed queue replication (via SmallRye Reactive Messaging)

## Development Tools
- **Maven Plugins**:
  - maven-compiler-plugin: 3.11.0
  - maven-surefire-plugin: 3.1.2
  - quarkus-maven-plugin: ${quarkus.platform.version}
  - protobuf-maven-plugin: 0.6.1 (for gRPC code generation)
  - maven-source-plugin: 3.3.1
  - maven-javadoc-plugin: 3.8.0

## Platform Support
- **macOS**: Primary development platform (Darwin)
- **Linux**: Production deployment target
- **Windows**: Community supported
