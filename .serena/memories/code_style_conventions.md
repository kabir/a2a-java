# A2A Java SDK - Code Style & Conventions

## Java Language Standards
- **Java Version**: 17+
- **Source Compatibility**: Java 17
- **Target Compatibility**: Java 17
- **Encoding**: UTF-8 (enforced in Maven)
- **Compiler Args**: `-parameters` (preserves method parameter names for runtime inspection)

## Package Structure & Naming
- **Base Package**: `io.a2a.*`
  - Core spec: `io.a2a.spec.*`
  - Server: `io.a2a.server.*`
  - Client: `io.a2a.client.*`
  - gRPC: `io.a2a.grpc.*`
  - Utilities: `io.a2a.util.*`

## Naming Conventions
- **Classes**: PascalCase (e.g., `AgentExecutor`, `DefaultRequestHandler`)
- **Interfaces**: PascalCase with descriptive names (e.g., `TaskStore`, `QueueManager`)
- **Methods**: camelCase (e.g., `execute()`, `onMessageSend()`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `QUEUE_WAIT_MILLISECONDS`)
- **Variables**: camelCase (e.g., `eventQueue`, `taskManager`)
- **Packages**: lowercase, dot-separated (e.g., `io.a2a.server.tasks`)

## Code Organization Patterns

### Builder Pattern Usage
Widely used for immutable objects:
```java
new AgentCard.Builder()
    .name("Agent Name")
    .description("Description")
    .build();
```

### CDI Patterns
- **Producers**: Methods annotated with `@Produces` and qualifiers
- **Qualifiers**: `@PublicAgentCard`, `@Internal`, `@ExtendedAgentCard`
- **Scopes**: `@ApplicationScoped` for singletons, `@RequestScoped` for per-request
- **Alternatives**: Use `@Alternative` + `@Priority` for overriding default implementations

### Interface-First Design
- Define interfaces for all extension points
- Provide default implementations (often `InMemory*` variants)
- Allow CDI-based override with `@Alternative`

## File Structure
- **One public class per file** (nested private classes allowed)
- **File name matches public class name**
- **Package statement first**, then imports, then class

## Documentation Standards
- **JavaDoc required** for:
  - All public classes and interfaces
  - All public methods
  - Complex internal logic (private methods when needed)
- **Parameter documentation**: Use `@param` tags
- **Return values**: Use `@return` tags
- **Exceptions**: Use `@throws` tags
- **Examples**: Include usage examples in class-level JavaDoc where helpful

## Testing Conventions
- **Test class naming**: `<ClassName>Test` (e.g., `TaskManagerTest`)
- **Test method naming**: Descriptive, often `test<MethodName>` or `should<Behavior>`
- **Test location**: `src/test/java` mirroring `src/main/java` structure
- **Test scope**: JUnit 5, Mockito for mocking, REST Assured for API tests

## Import Organization
- **Group imports**: java.*, javax.*/jakarta.*, third-party, project
- **No wildcard imports**: Prefer explicit imports
- **Static imports**: Allowed for test assertions and utilities

## Code Formatting
While there's no explicit formatter configuration (no checkstyle.xml found), the codebase follows these patterns:
- **Indentation**: 4 spaces (no tabs)
- **Line length**: Reasonable (typically ~120 characters)
- **Braces**: K&R style (opening brace on same line)
- **Blank lines**: Used to separate logical blocks

## Dependency Injection Best Practices
- **Constructor injection** preferred over field injection
- **Use `@Inject`** for dependency injection
- **Use `@Produces`** for creating CDI beans
- **Interface dependencies** over concrete implementations

## Error Handling
- **Use specific exceptions**: Custom exception hierarchy (e.g., `JSONRPCError`, `TaskNotFoundError`)
- **Document exceptions**: All public methods document thrown exceptions
- **Fail fast**: Validate inputs early
- **Meaningful messages**: Include context in exception messages

## Records vs Classes
- **Records**: Used for immutable data holders (e.g., events, simple value objects)
- **Classes**: Used when mutability or complex behavior needed
- **Builders**: Provided for complex immutable objects

## Null Handling
- **Avoid null where possible**: Use Optional for optional return values
- **Null checks**: Validate inputs in public methods
- **Jackson handling**: Configure serialization to handle null appropriately

## Module Separation
- **Clean dependencies**: Modules depend only on what they need
- **No circular dependencies**: Strict layered architecture
- **Minimal coupling**: Interfaces define contracts between modules

## Design Patterns in Use
1. **Builder Pattern**: For complex object construction
2. **Factory Pattern**: `EventQueueFactory`, `ClientTransportProvider`
3. **Strategy Pattern**: `ClientTransport` implementations
4. **Observer Pattern**: `EventConsumer`, publishers/subscribers
5. **Producer-Consumer**: Event queues
6. **CDI Producer Pattern**: For bean creation
