# Native 0.3 gRPC registered context correction

Authorized additional run: 2026-11-10 17:33:12Z–18:33:12Z, four further iterations; original iteration/failure accounting retained by root. Scope is Phase 1 only. No commits/staging/push/PR, no Maven launched by this agent.

Finding: the reference old-service interceptor admits a local registration but discards it. The transport handler's custom context factory can then supply version1.1 or an unrelated binding and reach the converted core unregistered.

Tests-first change:

- New `compat-0.3/reference/grpc/src/test/java/org/a2aproject/sdk/compat03/server/grpc/quarkus/GrpcRegisteredContextPropagationTest.java`.
- Uses the actual reference interceptor's context-wrapped listener callback, actual old transport handler and actual `Convert_v0_3_To10RequestHandler` into a captured core `RequestHandler` proxy. No new dependencies.
- Covers exact admitted registration identity, actual GRPC binding and immutable original0.3 with absent/empty/blank/explicit03 metadata and default/custom03 factories; wrong1.0/1.1 custom factory requires zero core execution; pre-registered unrelated HTTP_JSON factory also requires zero core execution. No-resolver custom1.1 historical behavior remains demonstrated.
- Focused RED command supplied to root: `mvn -B -ntp -pl compat-0.3/reference/grpc -am -Dtest=GrpcRegisteredContextPropagationTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- Production edits are held until root confirms actual failing evidence. `git diff --check` is clean.

Approved implementation direction after RED: carry the admitted immutable registration through a dedicated key in the existing compat gRPC context module; old handler validates exact original0.3 and any factory binding/attached-registration conflict, then attaches that same registration and actual GRPC binding before conversion. Only the admitted-registration path is enforced; no-resolver legacy adapter entry points retain their contract. No schema/converter/protobuf changes.

Verification: RED pending root; GREEN/regression pending. The previous clean build and focused passes did not exercise this finding and remain historical evidence only.

## Captured RED and implementation

Root `correction-native03-red-valid` completed17:37:25Z, exit1,27.547s:4 tests,3 expected assertion failures,0 errors. The downstream registration was null; conflicting version and binding factories each reached core once instead of zero times. The no-resolver historical case passed. The earlier initial RED stopped at an unrelated shared-gRPC fixture compile problem; that retained harness failure is not claimed as native03 behavioral evidence.

Focused production changes, after root confirmed RED:

- `compat-0.3/transport/grpc/src/main/java/org/a2aproject/sdk/compat03/transport/grpc/context/GrpcContextKeys_v0_3.java`: additive `INTERFACE_REGISTRATION_KEY` carries the exact admitted immutable object across gRPC callbacks/offload context propagation.
- `compat-0.3/reference/grpc/src/main/java/org/a2aproject/sdk/compat03/server/grpc/quarkus/A2AExtensionsInterceptor_v0_3.java`: retain the admitted registration and install it in the enhanced gRPC context. A no-resolver call explicitly clears an inherited registration. Existing metadata admission semantics remain unchanged, per root scope coordination.
- `compat-0.3/transport/grpc/src/main/java/org/a2aproject/sdk/compat03/transport/grpc/handler/GrpcHandler_v0_3.java`: preserve default/custom context creation; only with an admitted registration, require literal original0.3 (the existing factory contract), reject a conflicting binding or attached registration before core execution, validate native GRPC/default scope and selected03, and attach actual GRPC binding plus the same registration. Factory context data is retained and its immutable requested version is never rewritten.

No-resolver context creation remains historical, including legacy custom factory behavior. No03 schema, protobuf, conversion implementation or generated code changed; no dependency added. Import groups were sorted only in the three touched production files. `git diff --check` passed after implementation.

Focused GREEN/regression remains pending root orchestration. Suggested focused selection adds `GrpcRegistrationInterceptorTest,GrpcHandler_v0_3_Test` to the exact RED command, plus existing old Quarkus auth/task-authorization suites as appropriate. No success beyond the observed RED/historical no-resolver pass is claimed yet.
