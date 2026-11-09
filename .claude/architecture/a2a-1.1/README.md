# A2A 1.1 server support investigation

Status: research and proposed direction; no runtime implementation.

## Scope and conclusion

Support a 1.1 client using a 1.1 server interface alongside the existing 1.0 and
0.3 server interfaces. Each interface must preserve its version's semantics.
Client negotiation and teaching a 1.1 client to use a 1.0 server are outside this
investigation.

The recommended direction is to evolve `spec/`, `server-common/`, and
`transport/` to a 1.1-capable internal model, retain the existing 0.3 translation
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

- [Specification changes](spec-delta.md): changes relevant to server support,
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
