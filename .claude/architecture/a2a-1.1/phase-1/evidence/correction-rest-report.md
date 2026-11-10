# REST ordinary subscription verb correction

Authorization: additional 60 minutes/four focused iterations from 2026-11-10 17:33:12 UTC, deadline 18:33:12 UTC. Original Phase 1 completed-eight counter and inherited three-failed-cycle safeguard remain unchanged. Scope only the reviewed ordinary 1.1 REST subscription verb, retaining 1.0 POST and native 0.3 behavior. No commit, staging, schema/codecs, later-phase behavior, or new guarantees.

## Cause and intended correction

The shared REST client uses POST SSE for both versions, and both reference routers expose only POST for `tasks/{id}:subscribe`. This makes candidate peers agree while violating the pinned RC GET requirement (`spec-delta.md` lines94–95). Selected-version emission must be GET for 1.1 and demonstrated POST for 1.0. Reference dispatch must admit the matching verb and return method-not-allowed without executing the handler for the wrong version/verb. Native 0.3 POST remains on its historical route.

## Tests written before production

- `reference/rest/.../RestSubscribeIndependentPeerTest`: public Client over a JDK HttpServer independent peer; 1.1 peer accepts only GET, 1.0 accepts only POST. A real SSE Task must reach the public callback; exact version, tenant endpoint and one request/no verb fallback are checked.
- `reference/rest/.../RestSubscriptionRouteTest`: raw HTTP through a real Vert.x server using actual route registration, A2AServerRoutes context/registration handling and a deterministic RestHandler boundary response. Positive GET1.1 and POST1.0; wrong-verb rejection has zero core calls and correct Allow header.
- `reference/multiversion-rest/.../MultiVersionRestSubscriptionRouteTest`: equivalent raw HTTP through actual multiversion routing, plus native0.3 POST positive and GET negative with zero legacy execution.
- Only test dependencies added to the previously testless multiversion-reference module. No production changes before valid RED evidence.

Root-owned requested focused command:

```
mvn -B -ntp -fae -pl reference/multiversion-rest -am -Dtest=RestSubscribeIndependentPeerTest,RestSubscriptionRouteTest,MultiVersionRestSubscriptionRouteTest -Dsurefire.failIfNoSpecifiedTests=false test
```

At this checkpoint RED capture and all production edits remain pending. This agent starts no Maven process. Final exact commands, outcomes, counts and remaining limits will be appended after root verification.

## First observed RED

Read root evidence `/tmp/a2a-phase1-run/correction-rest-verb-red.log` and `.json`, completed 17:39:16.441896Z, exit1. Six tests ran in the single reference module: 3 expected failures, 0 errors/skips. Independent public 1.1 client emitted POST where GET-only peer required GET; single reference GET1.1 returned405; single reference POST1.1 executed and returned200 instead of rejecting405. The 1.0 peer and POST route cases passed. The downstream multiversion module was dependency-skipped, so root is capturing multiversion RED separately before production edits. No criterion counter was reset. Root also records an earlier wrong-module command selecting zero tests; that command supplies no coverage.

## Multiversion RED and implementation

Read `/tmp/a2a-phase1-run/correction-rest-multiversion-red.log` and `.json`, completed17:40:27.082304Z: six tests, two expected failures, no errors/skips. GET1.1 returned405 and POST1.1 incorrectly executed200; 1.0 POST and native0.3 POST remained passing. All12 focused cases have now run before production edits.

Implemented selected1.1 `getAsyncSSE` with the same normalized headers and callback lifecycle; selected1.0 retains existing `postAsyncSSE`. Single and multiversion routers now register GET subscription paths. The actual shared subscription handler first creates/validates its registered call context, then enforces GET for admitted1.1 or POST for legacy versions, returning405 with version-specific Allow before invoking RestHandler. Multiversion GET0.3 returns405 with AllowPOST without invoking the native legacy handler; existing POST0.3 dispatch is unchanged. No cursor/replay or other later-phase change.

Existing direct-handler unit tests supplied no HTTP method before this correction. Their fixture now supplies POST, reflecting the historical subscription requests; no previous assertion was changed. Source endpoint documentation states GET1.1 and historical POST1.0. Final focused GREEN and broader regressions remain root-owned/pending.


## Observed focused GREEN

Read root-owned `/tmp/a2a-phase1-run/correction-focused-green.log` and `.json`: completed 2026-11-10 17:43:27.795748 UTC, exit 0, elapsed 16.646 seconds, BUILD SUCCESS. Exact command:

```sh
mvn -B -ntp -pl reference/multiversion-rest,compat-0.3/reference/grpc,reference/grpc -am -Dtest=RestSubscribeIndependentPeerTest,RestSubscriptionRouteTest,MultiVersionRestSubscriptionRouteTest,GrpcRegisteredContextPropagationTest,GrpcRegistrationInterceptorTest,A2AExtensionsInterceptorVersionTest,GrpcTransportTest -Dsurefire.failIfNoSpecifiedTests=false test
```

All 12 owned REST cases passed: independent-peer 2, single-version raw-route 4, multiversion raw-route 6; zero failures, errors or skips. The combined command passed 37 tests across seven suites, including the separately owned gRPC correction tests. This is the first production correction candidate for the REST criterion and passed; the inherited failure safeguard was not reset.

After that completed build, corrected import order in the three new REST test classes (java before third-party imports, RestHandler before its nested HTTPRestResponse). No semantic changes followed the GREEN build. Fresh `git diff --check` exits 0. Root owns the subsequent broader affected-module regression and its evidence; this report makes no claim that broader regression has completed. No commits, staging or external writes were performed.
