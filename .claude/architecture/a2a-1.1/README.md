# A2A 1.1 client and server architecture

Status: architecture reference with a developer-selected task workflow. Phase 2 is complete within the approved experimental pinned-RC scope; no final 1.1 conformance claim. Start with [the task list](task-list.md).

## Architecture review and implementation handoff

The user clarified the outcome on 2026-11-09: a reviewed architecture and a
concrete implementation handoff. On 2026-11-16 the user replaced coordinator loops
with developer-selected tasks and separate implementation/review sessions.
One instance must serve the distinct 0.3, 1.0, and 1.1 server interfaces when provisioned; explicit requests
must not silently select another version. Preserve common Java APIs, allowing
documented custom-store/executor changes required by 1.1.

- [Proposed architecture](architecture.md): provisioning, protocol policies,
  storage/replication, migration, live execution, and specification gates.
- [Task list](task-list.md): current status, dependencies and next assignments.
- [Task implementation guide](implementation-tasks.md): scope, phase
  deliverables, evidence requirements, and completion criteria.
- [Model and token-cost policy](implementation-tasks.md#model-selection-and-token-cost-policy):
  Luna implementation by default, Sol planning/review, an explicit Astra ban,
  model audit records, and focused task context.
- [Acceptance and task bounds](acceptance.md): fourteen evidence-based criteria,
  task scope/time bounds, stuck-task diagnosis and concise handoffs.

The user-supplied review confirmed the shared-core direction; its requested
clarifications have been incorporated. This is an architecture reference and
handoff; current implementation status and review dispositions live in the task
list and progress records. The documents below retain the original research baseline. Where a previously open design choice now has a proposed
answer, consult the architecture; specification ambiguities remain explicit
preflight gates. Historical research is not fresh implementation or conformance evidence.

The architecture now explicitly covers ordinary direct-Message responses without
task persistence, revision-checked full Task output from executors, and defaults
for new interface registrations versus existing deployment entry points. These
address architectural review findings; no runtime failure or production fix is
claimed.

A further independent review corrected the proposed gRPC missing-version default
to the specified 0.3 and strengthened executor completion channels, initial
webhook enrollment, bounded observation recovery, and mixed-binary migration.
These contracts and their regression obligations are included in the architecture,
acceptance criteria, and implementation prompt. The temporary review report is
not part of the retained handoff.

## Client scope revision — 2026-11-10

This dated section records the original scope revision and subsequent contract
review; use the task list for current implementation/review status.

The user expanded the handoff to include production public SDK client support
alongside each server capability, over all three bindings. The client default is
**1.1**; explicit 1.0 clients keep 1.0 semantics. Rebuilt applications using an
implicit default intentionally change selection and can pin 1.0 before upgrading.
This does not change server missing-version defaults or optional 0.3 behavior.
The 0.3 client stays separate in its existing optional modules with its own API
and transports. Automatic version negotiation/fallback remains outside scope.

The [client architecture](architecture.md#production-client-architecture) covers
instance selection, API/SPI changes, codecs, state reduction, live lifecycle and
extensibility within existing modules. The revised phases require public-client
integration evidence as features arrive, alongside unchanged historical peers
and raw fixtures. No new production Maven module or duplicate 1.0 client stack
is proposed. Prior review conclusions apply to the server design; the client
revision required independent review at that point. Current task bounds are
defined in acceptance.md; historical run limits remain part of the original evidence.

The follow-up contract review now has recommended public observation/live/input
handles, raw SPI companion interfaces and provider/wrapper capability checks.
TaskView synchronization is explicitly as-of and separate from limited or missing
history. Lifecycle tables distinguish readiness, admission, commit, delivery and
indeterminate cancellation. The legacy constant stays 1.0 independently of the
1.1 client default; custom compatibility requires actual source/binary/wire
checks. No public operation-status lookup or executor-delivery receipt is assumed.
Missing binding correlation remains a specification gate, not a new protocol.

The 2026-11-16 task workflow supersedes loop scheduling and mandatory iteration
arithmetic. The developer selects one task, uses the reviewed plan where it is
already concrete, requests independent review at a meaningful boundary, and
routes corrections. No persistent coordinator or automatic dispatch is needed.
Luna medium/normal implements; Sol medium/normal plans when needed and reviews.
Keep the Astra ban, historical evidence, task time bounds, stuck-task diagnosis
and all technical acceptance gates. See [task bounds](acceptance.md#run-limits).

## Scope and conclusion

Deliver a production 1.1 SDK client using a 1.1 server interface alongside explicit
1.0 clients and the existing optional 0.3 clients on one server instance. Each
client and interface preserves its selected version's semantics. Automatic
negotiation and fallback to a different protocol version remain outside scope.

The recommended direction is to evolve `spec/`, `server-common/`, and
`transport/` to a 1.1-capable internal model, extend existing `client/` modules,
retain the existing 0.3 translation
boundary, and implement explicit 1.0 and 1.1 policies in the shared server
transport pipeline. The inspected changes do not justify duplicating the 1.0
domain model, generated service, or server implementation in `compat-1.0/`.

There is still a 1.0 compatibility responsibility. It includes method and
parameter semantics, event filtering, history, response projection, error
mapping, and webhook formatting. Sharing classes does not remove that work.

This is an architectural recommendation from code inspection, not a claim of
tested compatibility. Generation assignment, atomic mutation, live input, and
event replay are substantial core changes whichever packaging approach is used.

## Documents

- [Specification changes](spec-delta.md): original server research with client consequences,
  including the corrections inherited from 1.0.1.
- [Compatibility design assessment](compatibility.md): current architecture,
  options, recommended boundaries, and how 0.3 would reach the newer core.
- [Validation and open decisions](validation.md): acceptance scenarios, rollout
  sequence, and RC questions that should be settled before implementation.

## Evidence baseline

The branch was created from upstream `main`, not the fork's `main`:

- SDK commit: `b96e0b3f21927eb551881232c69acf077ebdcbc4`.
- Spec `v1.0.0`: `173695755607e884aa9acf8ce4feed90e32727a1`.
- Spec `v1.0.1`: `3303592588e388e62e0f69f701af531d2f4e3991`.
- Spec `v1.1.0-rc`: `78e1d6a1f7f118c8e9bc830b04a8579c1119cb78`.

Both `docs/specification.md` and `specification/a2a.proto` were compared directly
at those spec revisions. The 1.0.1 tag was used to identify inherited fixes; its
existence is not taken as evidence of a separately negotiable protocol version.

Primary sources:

- [1.0.0 specification](https://github.com/a2aproject/A2A/blob/173695755607e884aa9acf8ce4feed90e32727a1/docs/specification.md)
- [1.0.1 specification](https://github.com/a2aproject/A2A/blob/3303592588e388e62e0f69f701af531d2f4e3991/docs/specification.md)
- [1.1 RC specification](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/docs/specification.md)
- [1.1 RC protobuf](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/specification/a2a.proto)
- [Existing 0.3 architecture](../compatibility_0.3.md)
- [Existing event pipeline](../EVENTQUEUE.md)

Protocol versions here are `0.3`, `1.0`, and `1.1`. SDK release numbers and spec
patch numbers are separate. These notes describe the RC and need reconciliation
with the final 1.1 specification before release.
