# Strict SDK-owned gRPC admission test evidence

Scope: only two new test classes under client/transport/grpc; no production changes or Maven invocation by this test owner. The retained deadline is 2026-11-10 18:33:12 UTC and the original counters were not reset. Parent owns build commands and third-failure safeguard decisions.

## RED observed before production

Read `/tmp/a2a-phase1-run/owned-channel-admission-red.log` and `.json`. Exact command:

```sh
mvn -B -ntp -pl client/transport/grpc -am -Dtest=GrpcOpaqueChannelAdmissionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Completed 18:22:14.770653 UTC, exit 1, 2 tests, 2 failures, no errors/skips. Direct opaque constructor failed to reject; raw configured factory was actually invoked and threw the test assertion instead of the required client exception. Tests loop selected 1.0 and 1.1; an initial failure terminates the loop, so RED alone does not establish both versions. Production was authorized by parent only after this valid behavior RED.

## New proof cases

`GrpcOpaqueChannelAdmissionTest`: raw channel admission before newCall; raw config factory admission before invoking the factory. Both selected versions checked on a passing run.

`GrpcOwnedChannelTest` (12 tests): actual Netty HTTP/2 loopback server observes exactly one selected version for unary and streaming, both 1.0/1.1; implicit version 1.1; conflicting static authentication and call-context version metadata without RPC; identical differently cased version keys collapse and bearer authentication reaches peer; nonnull factory getter and provider path; exact endpoint match before consuming handle; rejected second owner leaves existing owner usable; simultaneous claim race yields one owner; separate handles from one config retain separate versions and closing one leaves another usable; idempotent close rejects reattachment and future calls. Positive streaming requires an actual event, not only completion.

Requested GREEN command:

```sh
mvn -B -ntp -pl client/transport/grpc -am -Dtest=GrpcOpaqueChannelAdmissionTest,GrpcOwnedChannelTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Observed selected GREEN and source timing

Read `/tmp/a2a-phase1-run/owned-channel-focused-proof.log` and `.json`. The exact requested GREEN command above completed 2026-11-10 18:25:40.601220 UTC, exit 0, elapsed 4.517 seconds, BUILD SUCCESS. Fourteen tests ran: 2 admission and 12 owned-channel tests, zero failures/errors/skips. The loops exercise both selected versions on this passing run. This is selected evidence, not a full-module or full-reactor result.

After that compilation, a final test-only strengthening added a Netty ServerStreamTracer counter and zero-stream assertions for rejected authentication, call-context and closed-owner cases. Source mtime was 18:25:44 UTC; the compiled GrpcOwnedChannelTest.class mtime was 18:25:39 UTC. Those final zero-server-stream assertions were NOT executed. The earlier compiled tests already checked zero server-handler ingress for rejection and the other twelve proof behaviors. The test count did not change. No subsequent source edit was made by this agent; parent stopped implementation/testing under the inherited three-attempt safeguard and explicitly authorized no rerun.

## Remaining gates and limits

- Independent review `/tmp/a2a-phase1-run/owned-channel-independent-review.md` identifies a source-established admission/proof gap through default global name-resolver/load-balancer registries. A load-balancer-installed final tracer can follow the SDK guard. These selected tests do not exercise that global provider path or establish the strict F1 invariant; F1 remains open. This is a source-review finding, not an executed exploit result from this agent.
- The review also identifies provider channel allocation before descriptor validation, with no cleanup for a rejected newly issued handle. Existing-owner preservation tests do not prove that rejected fresh allocation is released.
- Existing raw-channel/factory test fixtures were intentionally not rewritten by this agent. Strict admission is an explicitly approved runtime behavior change; those callers, source examples and broader regression/migration status remain separate work. Earlier broad passing results predate this material change and cannot serve as current complete regression evidence.
- No automatic-configurator/global-provider rejection, TLS handshake/interoperability, retry/cancellation timing or programmable/async gRPC CallCredentials tests were added by this agent. The new API's first supported authentication profile is a copied static header map.
- No repository/source edits, test reruns, Maven commands, commits, staging, push or PR followed the parent's stop. This final report update is packaging only.

The fourteen observed selected passes must be reported separately from the unrun final assertions and incomplete proof/migration gates. Phase 1/F1 completion or commit readiness is not established.
