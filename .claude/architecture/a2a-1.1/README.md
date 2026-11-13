# A2A 1.1 client and server architecture

Status: reviewed server direction with a proposed client scope revision; no runtime implementation.

## Architecture review and implementation handoff

The user clarified the outcome on 2026-11-09: a reviewed architecture and a
concrete prompt for a later implementation loop. One instance must serve the
distinct 0.3, 1.0, and 1.1 server interfaces when provisioned; explicit requests
must not silently select another version. Preserve common Java APIs, allowing
documented custom-store/executor changes required by 1.1.

- [Proposed architecture](architecture.md): provisioning, protocol policies,
  storage/replication, migration, live execution, and specification gates.
- [Implementation-loop prompt](implementation-loop.md): copyable scope, phase
  deliverables, evidence requirements, and completion criteria.
- [Model and token-cost policy](implementation-loop.md#model-selection-and-token-cost-policy):
  Luna implementation by default, Sol planning/review, an explicit Astra ban,
  model audit records, and focused task context.
- [Acceptance and run limits](acceptance.md): fourteen evidence-based criteria,
  agreed iteration/time/attempt limits, and checkpoint behavior.

The user-supplied review confirmed the shared-core direction; its requested
clarifications have been incorporated. This is a reviewed proposal and handoff,
not an implemented feature. The documents below retain the original research
baseline. Where a previously open design choice now has a proposed
answer, consult the architecture; specification ambiguities remain explicit
preflight gates. No implementation or conformance is claimed.

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
revision still needs independent review. The agreed run limits remain unchanged.

The follow-up contract review now has recommended public observation/live/input
handles, raw SPI companion interfaces and provider/wrapper capability checks.
TaskView synchronization is explicitly as-of and separate from limited or missing
history. Lifecycle tables distinguish readiness, admission, commit, delivery and
indeterminate cancellation. The legacy constant stays 1.0 independently of the
1.1 client default; custom compatibility requires actual source/binary/wire
checks. No public operation-status lookup or executor-delivery receipt is assumed.
Missing binding correlation remains a specification gate, not a new protocol.

The user clarified retry accounting on 2026-11-11: routine compilation/setup
repairs are not automatically new iterations or failed substantive attempts.
Three consecutive substantive attempts without progress on the same defect now
trigger Sol diagnosis, replacing the blanket three-attempt cap. The authorized
ten-iteration/two-hour bounds and historical evidence remain; see
[the current counting rules](acceptance.md#counting-work-and-progress).

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
