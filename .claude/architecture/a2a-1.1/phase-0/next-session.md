# Phase 0 handoff and focused dependent plans

> **For agentic workers:** use `superpowers:executing-plans` for a separately authorized follow-up. Phase 1 must run in a fresh agent after explicit authorization. This checkpoint grants no permission to execute Phase 1 or create a commit.

**Goal:** advance revised public client/server support with independently frozen old contracts and no invented guarantees.

**Architecture:** shared 1.x core with exact version policy and optional separate 0.3; follow [recommended public/SPI contracts](../architecture.md#recommended-api-and-spi-contracts).

**Tech stack:** Java 17, Maven, JUnit/Quarkus, Gson/protobuf JSON, HTTP/SSE and gRPC.

**Spec:** [Phase 0](../implementation-loop.md#phase-0-pin-semantics-and-baseline), [acceptance](../acceptance.md), [gates](spec-gates.md), [inventory](client-inventory.md).

## Constraints

Phase 0 only in this run; no production changes, schema regeneration, Phase 1, commit, push or PR. Later default client selection is 1.1, explicit 1.0 stays 1.0, and legacy CURRENT_PROTOCOL_VERSION stays 1.0 independently. Preserve non-null message(), common constructors/methods/handlers and separate optional 0.3 clients. No automatic negotiation/fallback, status RPC, wire correlation ID, idempotency guarantee or executor-delivery receipt. No new production module or duplicate 1.0 stack. Do not repair unrelated baseline defects incidentally.

Ten iterations/two hours/three attempts remain the run limits; read counters on every continuation. Budget exhaustion is never completion or automatic new authorization.

The [required client rules](required-client-rules.md) are a mandatory Phase 0 supplement: invalid-generation reliability disablement and known-generation accounting for unknown timeline kinds. Repeated snapshot watermark semantics remain gated.

Historical probe work exceeded the three-attempt limit and was stopped at the first checkpoint. The user then explicitly authorized the staged follow-up. Final1.0 and0.3 selected codec comparisons passed against unchanged frozen historical data; original0.3 archive bytes were promoted only after matching. Read the audit and current counters; no failed approach was silently reset. Do not rerun successful checks automatically. Full final A03 transport/lifecycle coverage remains future implementation acceptance.

## First actions next session

**Phase0 research/baseline deliverables are verified**, confirmed by [final independent review](exit-review.md). It records the preflight evidence and explicit dependent protocol blockers. Its completion does not authorize Phase1. No production changes are part of this handoff; obtain the requested next-phase authorization before executing P1 below.

- [ ] Use a fresh agent for Phase 1, as requested by the user. Read implementation-progress.md, review, gates and baseline before scheduling work; verify worktree/HEAD and original hashes together with the authorized instruction-amendment record. Automatic continuation retains the original deadline/counters.
- [ ] Verify the Phase 0 commit containing this handoff. The user explicitly approved committing everything and subsequently corrected the related issue to [a2aproject/a2a-java#1303](https://github.com/a2aproject/a2a-java/issues/1303); the approved message is `docs(a2a-1.1): record reviewed Phase 0 baseline and implementation plan`, with footer `This fixes #1303`. Record its resulting SHA in the first progress update. Do not request the same approval again or amend Phase 0 merely to insert its own hash. Each later phase still requires a new commit proposal and explicit approval before committing; see [phase commit instructions](../implementation-loop.md#phase-commits-and-user-approval).
- [ ] Record approval of the revised client architecture before production implementation. This session authorizes Phase 0 and recommended contracts; independent review is evidence, not user approval.
- [ ] Read [selected SDK policies](selected-policies.md): exact selection, message-at1 bootstrap and origin-preserving default cross-origin rejection are already recommended choices. Resolve the remaining normative/binding residuals before their affected implementation/conformance claims. Keep final-spec reconciliation open; do not select open PR2411 as target.
- [ ] Read baseline failures/skips and the authorized follow-up results. Preserve frozen0.3/1.0 expectations and isolated historical SDK snapshot. Candidate checks compare without recapturing expectations; expand the final all-binding compatibility matrix only as part of explicitly authorized later implementation.

## Plan P1: provisioning plus immutable public-client version (not executed)

**Client files:** `client/base/.../client/{ClientBuilder,Client}.java`, `client/base/.../client/config/ClientConfig.java`; `spec/.../spec/AgentInterface.java` and `spec/.../spec/util/Utils.java`; constructors/configs/providers in `client/transport/{jsonrpc,rest,grpc}`; SPI provider/wrapper companion; both `extras/opentelemetry` wrapper families. Inventory gives full paths and methods.

**Server files:** `reference/common/.../VersionRouter.java`, `reference/multiversion-{jsonrpc,rest}/.../MultiVersion*Routes.java`, gRPC handler/context dispatch, `server-common/.../{ServerCallContext,AgentCardRequestPolicy,version/A2AVersionValidator}.java`.

**Interfaces:** retain provider create(config,card,interface); add the architecture's immutable version/capability companion. Client emission/decoding policy stays independent of server acceptance/projection. Routine naming is free; gated cursor/live signatures are not finalized here.

- [ ] Pin all shared ordinary/auth/task-authorization/polling test factories/cards to explicit 1.0 before changing defaults. Retain old 0.3 suites and independent frozen fixtures.
- [ ] Write failing tests for default 1.1, explicit 1.0, same-binding descriptor permutations/tenants/endpoints and unavailable selection. Separate implicit constructors from explicit descriptor constructors; cover providers discarding descriptors and using new convenience defaults.
- [ ] Test provider/delegate/every-wrapper feature intersection, identity/plain/escalating/throwing wrappers and both telemetry families. Keep explicit 1.0 historical catch/log/continue; fail configured 1.1 wrapper exceptions without bypass.
- [ ] Select exact version before deduplication/binding preference, freeze in built instance, propagate through all unary/SSE/gRPC requests and decode/error policies. Normalize identical overrides, reject conflicting headers/metadata before I/O.
- [ ] Pair public SDK 1.0/1.1 clients on one server per binding. Test all seven nonempty provisioned subsets, missing/empty/blank metadata, tenant endpoints and stale-card disabled-version rejection independently of local card failure. New registration requires explicit versions; old entry points preserve exposure/defaults. Incomplete1.1 remains experimental.
- [ ] Run legacy source/binary/wire probes separately from public new-to-new tests, review diff, then record actual evidence. Do not advance phases based on a plan.

Starting commands for a later authorized run; confirm discovery/counts rather than treating these as results:

```sh
mvn -B -ntp -pl client/base,client/transport/jsonrpc,client/transport/rest,client/transport/grpc -am test
mvn -B -ntp -pl reference/multiversion-jsonrpc,reference/multiversion-rest -am test
mvn -B -ntp -pl tests/multiversion/jsonrpc,tests/multiversion/rest,tests/multiversion/grpc -am verify
```

Cap each command at min(20 minutes, remaining run time), plus 30 seconds cleanup, retain one process handle. Root POM includes multiversion modules directly; there is no tests/pom.xml or tests/multiversion/pom.xml.

## Later dependency boundaries (planning only)

| Work | Existing files/boundaries | Required prior evidence |
| --- | --- | --- |
| Phase 2 types/codecs | spec, spec-grpc, jsonrpc-common; 0.3 MapStruct conversion; binding decoders and shared SPI | Artifact origin/cursor rules; record/sealed API impact decisions. Apply update-a2a-proto skill then. |
| Phase 3 mutation/client reducer | TaskManager/TaskStore/InMemoryTaskStore/MainEventBusProcessor/DefaultRequestHandler; JpaDatabaseTaskStore/JpaTask; replicated queues; ClientTaskManager | Exact creation trace, atomic authority, stamped result/order, full-Task revision/completion, deployment writer fence. |
| Phase 4 observation/config | onSubscribeToTask/replay/wait coordination; push stores/senders; client reducer/observation controller and binding decode | Ordinary/live cursor/terminal rules; legal recovery; as-of synchronization separate from independent history/timeline window coverage. |
| Phase 5 live | Executor session, HTTP uplink/downlink, gRPC duplex; recommended public/base and raw SPI handles | Exchange routing and exact commit/rejection evidence per binding/input. Missing receipts block COMMITTED guarantees; no fabricated lookup/retry rule. |
| Phase 6 audit | Public SDK matrix, frozen peers/fixtures, raw protocol, dev docs | A01–A14 and every validation row linked to actual results; real Docker/cluster coverage; final spec reconciliation. |

## Server baseline risks

At unchanged HEAD, TaskManager.updateWithMessage saves outside the bus; full Task emission saves supplied state; MainEventBusProcessor persists then distributes the original event. These do not establish 1.1 atomic generation/publication semantics. Unary new-task inline webhook config is installed after observing a Task result, so terminal creation needs the architecture's enrollment gate. Ordinary subscribe reads state before tapping its queue and prepends a snapshot, rejecting terminal tasks; this does not establish coordinated cursor handover. Keep current code authoritative over older simplified queue-flow prose. No mutation/conversion code changed here.


## Upstream clarification tracking

User-requested issue filing was checked against existing issues. [A2A #2415](https://github.com/a2aproject/A2A/issues/2415) already covers this HTTP association ambiguity and remains OPEN at the fresh check on 2026-11-10. Its comment links PR2411 without supplying a routing rule. Follow this issue; do not infer resolution from its existence or create a duplicate. Read-only snapshots: [issue](evidence/upstream-issue-2415.json), [comments](evidence/upstream-issue-2415-comments.json). No external GitHub state was changed.
