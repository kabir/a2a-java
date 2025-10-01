# A2A Java SDK - Task Completion Checklist

## Before Completing Any Task

### 1. Build Verification
```bash
# Full build with tests
mvn clean install

# If build fails, investigate and fix
# Do NOT skip tests unless explicitly discussed
```

### 2. Code Quality Checks
- [ ] All new code has appropriate JavaDoc comments
- [ ] Public APIs are documented with usage examples
- [ ] No compiler warnings introduced
- [ ] Code follows project conventions (see code_style_conventions.md)

### 3. Testing Requirements
- [ ] Unit tests written for new functionality
- [ ] Integration tests added if applicable
- [ ] All tests pass locally: `mvn test`
- [ ] Test coverage adequate for changes

### 4. Documentation Updates
- [ ] README.md updated if public API changes
- [ ] Module-specific README updated if needed
- [ ] JavaDoc generated successfully: `mvn javadoc:javadoc`
- [ ] CONTRIBUTING.md updated if contribution process changes

### 5. Dependency Management
- [ ] New dependencies added to `dependencyManagement` in parent pom.xml
- [ ] Version properties defined in parent pom.xml
- [ ] No duplicate or conflicting dependencies
- [ ] Minimal dependency additions (use existing when possible)

### 6. Multi-Module Consistency
If changes affect multiple modules:
- [ ] All transport implementations updated (JSON-RPC, gRPC, REST)
- [ ] Client and server kept in sync
- [ ] Spec module updated before implementation modules

### 7. Git Hygiene
```bash
# Check what's changed
git status
git diff

# Ensure working on feature branch
git branch  # Should NOT be on main

# Stage only relevant files
git add <specific-files>

# Commit with meaningful message
git commit -m "Brief summary

Fixes #<issue-number>"
```

### 8. Protocol Compliance
If changes affect A2A protocol:
- [ ] TCK tests still pass (run TCK server and external tests)
- [ ] Protocol version updated if breaking change
- [ ] Backward compatibility maintained or documented

### 9. Performance Considerations
- [ ] No obvious performance regressions
- [ ] Async operations used appropriately
- [ ] Resource cleanup (close queues, streams, connections)

### 10. Security Review
- [ ] No hardcoded credentials or secrets
- [ ] Input validation for user-provided data
- [ ] Proper authentication/authorization handling
- [ ] No sensitive data in logs

## Specific Task Types

### Adding New Feature
- [ ] Feature flag if needed for gradual rollout
- [ ] Examples updated to demonstrate new feature
- [ ] Migration guide if existing code affected

### Fixing Bug
- [ ] Regression test added to prevent recurrence
- [ ] Root cause documented in commit message
- [ ] Related issues linked

### Refactoring
- [ ] Behavior unchanged (verified by tests)
- [ ] Performance impact assessed
- [ ] Deprecation notices if removing public APIs

### Adding New Transport
- [ ] All A2A protocol methods implemented
- [ ] Transport-specific tests added
- [ ] Transport config class created
- [ ] Reference implementation provided
- [ ] Documentation with usage examples

### Database/Persistence Changes
- [ ] Migration scripts provided (if using JPA extras)
- [ ] Backward compatibility with existing data
- [ ] Transaction handling correct

### Performance Optimization
- [ ] Benchmark results documented
- [ ] No functional changes mixed with optimization
- [ ] Trade-offs documented

## Final Verification Commands

```bash
# Complete build
mvn clean install

# Verify no uncommitted changes
git status

# Check branch is not main
git branch

# Review all changes
git diff main..HEAD

# Ensure commit message follows convention
git log -1
```

## When to Skip Steps
- **Prototype/WIP**: Mark PR as draft, skip some checks
- **Documentation-only**: Skip code quality checks
- **Dependency update**: Focus on compatibility testing

## Common Mistakes to Avoid
- ❌ Committing to main branch
- ❌ Skipping tests to "save time"
- ❌ Adding dependencies without parent pom management
- ❌ Updating only one transport when all need changes
- ❌ Breaking backward compatibility without deprecation
- ❌ Leaving debug code or print statements
- ❌ Not updating documentation for public API changes
- ❌ Mixing multiple unrelated changes in one commit
