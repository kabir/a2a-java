# Proposed Phase 1 commit — approval pending

> **Superseded by subsequent independent review: changes required.**
> Three open findings invalidate the earlier no-blockers/commit-ready conclusion.
> See [review response and correction plan](review-response.md).
> The recorded successful build remains valid evidence, but does not cover these
> failures. The original implementation authorization has expired; corrections
> require explicit additional time/iteration authorization. No commit is approved.

Branch: `support/a2a-1.1.0`, worktree
`/Users/kabir/.codex-api/worktrees/3053/a2a-java`.
Accepted Phase0/unchanged HEAD: `e468dd70a415d17c19ae70d75750b6464d983ad1`.
All candidate changes remain uncommitted. Execution/architecture approval and
review requests do not authorize a commit. Related issue is already confirmed
as [a2aproject/a2a-java#1303](https://github.com/a2aproject/a2a-java/issues/1303).

## Scope

| Area | Proposed change |
| --- | --- |
| client/base and transport/spi | Independent immutable1.1 default/explicit1.0, exact card/endpoint/tenant selection before binding preference, additive companion/provider capabilities, validated wrapper intersection and legacy adapter. |
| JSONRPC/REST/gRPC clients and both telemetry families | Carry selectedversion across every ordinary request/interceptor/metadata path, exact directconstructor selection, explicitversion migration overloads, immutable telemetry profiles. |
| server-common/reference/transports | Explicit nonempty experimental registrations, exact dispatch/card scope, versioned request/error policy, preserved legacy paths and hooks, optional03 availability;03defaultendpoint only with no tenants. |
| Native03 compatibility adapters | Preserve original03version/context, apply explicit defaultregistration while keeping noresolver entrypoints. No schema/conversion regeneration. |
| Test factories/examples | Pin demonstrated legacy usage to explicit1.0; preserve old03 APIs and frozenexpectations. |
| Verification | Real public10/11/default11 pairedclients, oldpublic03Get/send on same experimentalinstance, seven actualdefaultendpoint subsets perbinding,1.xtenant checks, no03classpath tests, source/binary/wire provider/wrapper probes. |
| Architecture progress/evidence | Focused plans, decisions, userrequested freshreview, boundedcommand logs/counts, migration/provisioning examples, compilerinput snapshot and concrete nextsession handoff. |

No new productionmodule, model field, proto generation, generation/timeline,
observation, mutationauthority, live function, receipt, session ID, status RPC or
retry guarantee is included. `CURRENT_PROTOCOL_VERSION` remains1.0; newclient
construction defaults independently to1.1. Incomplete1.1 stays experimental.

The requested note about **existing examples to1.0** and **native03REST routing**
is retained in implementation-progress.md. Latest user instruction says to do
both now. Both are now corrected and passing in focused checks and the final clean reactor.
0.3 remains default-endpoint only, with no tenant semantics.

## Proposed diff

Source, tests, POMs and frozen compatibility resources: **122 files** (56 modified,
66 new), **5,298 insertions / 433 deletions**, including the binary old-API probe.
[Complete source/test diff](evidence/source-and-tests.diff.gz),
[diffstat](evidence/source-diffstat.txt),
[file inventory and SHA-256 values](evidence/source-scope.json).
The [complete proposed path list](evidence/commit-files.txt) also includes the
progress record and retained documentation/evidence.
The index remains untouched. Architecture documentation is limited to the appended
progress record and new `phase-1/` plans, reports, migration examples, provenance,
bounded command evidence, reviews and handoff. Phase 0, the seven authoritative
architecture inputs and generated/protobuf sources remain unchanged.

## Verification and remaining gates

Final `mvn -B -ntp clean install`: **83 modules SUCCESS; 3,987 discovered,
3,864 executed, 123 unchanged skips, 0 failures/errors**, exit 0 in 643.588 seconds.
The fresh independent reviewer reports **no open blockers for Phase 1**.


See [verification](verification.md) for evidence layers, exact counts, skips and
limits, plus [migration/provisioning examples](compatibility/README.md).
HTTP live association(#2415), exact artifact/batch outcomes, cursor/recovery and
final1.1specreconciliation remain binding gates. PR2411 is proposal only.
No complete A01–A14 criterion or certification claim follows from Phase1.

Proposed Conventional Commit message:

```text
feat(a2a-1.1): add explicit interface and client version policy

This fixes #1303
```

Ask for the user's explicit approval before creating this commit. Do not push,
create a PR or advance to Phase2. After approval, verify the current worktree and
scope against this proposal, commit only approvedPhase1 files, and record the
approval/resulting SHA in implementation-progress.md.
