# Phase 1 next-session handoff

> **Superseded by subsequent independent review: changes required.**
> Three open findings invalidate the earlier no-blockers/commit-ready conclusion.
> See [review response and correction plan](review-response.md).
> The recorded successful build remains valid evidence, but does not cover these
> failures. The original implementation authorization has expired; corrections
> require explicit additional time/iteration authorization. No commit is approved.

**Phase 1 is implemented, verified and independently reviewed; explicit commit
approval is pending.** Everything remains uncommitted. No Phase 2 work was started.

## Resume location and authority

- Worktree: `/Users/kabir/.codex-api/worktrees/3053/a2a-java`.
- Branch: `support/a2a-1.1.0`.
- Accepted Phase 0 and unchanged HEAD: `e468dd70a415d17c19ae70d75750b6464d983ad1`.
- SDK issue: [#1303](https://github.com/a2aproject/a2a-java/issues/1303), already confirmed.
- User approved Phase 1 execution and recommended architecture contracts, up to
  two hours / ten iterations. That does not authorize committing, pushing, a PR,
  material API/SPI breaks, invented protocol guarantees or later phases.
- User requested another independent agent review first; that review completed,
  its findings were fixed and re-reviewed, with no open Phase 1 blockers.
- User clarified 0.3 has no tenants. That correction governs production, tests and
  current acceptance evidence. Earlier raw tenant-alias evidence is withdrawn.
- The requested note **“existing examples to 1.0, correct native 0.3 REST routing”**
  was retained. The immediate follow-up authorized proceeding with both now;
  both are included and verified, not left as unimplemented future work.

Read applicable AGENTS.md and skills, inspect branch/HEAD/status and preserve
all existing changes before any further action. The main checkout was not edited.
Do not reset to the old upstream base or create a replacement branch.

## Implemented Phase 1 scope

Exact binding/endpoint/tenant registrations provision explicit version sets and
derive matching cards; incomplete 1.1 requires experimental opt-in. HTTP and gRPC
reject unprovisioned versions without upgrade/fallback. Optional 0.3 remains
separate and uses only default endpoint scope; native REST `/v1` is its fixed
version prefix, while explicit 1.x `/v1` still means tenant `v1`.

Shared clients freeze version at construction, default to 1.1 and preserve
explicit 1.0. Exact card version and scope selection precede binding preference.
Selected version flows through all ordinary transport requests/metadata,
providers, interceptors and both OpenTelemetry wrapper families. Provider and
wrapper capabilities are checked without bypassing the chain. Legacy
CURRENT_PROTOCOL_VERSION/model defaults remain 1.0. Established test/example
clients are explicitly pinned to 1.0. Common original signatures and old
entrypoints remain available; additive migration examples and representative
old source/binary/wire probes are retained.

[Scope and Conventional Commit proposal](commit-proposal.md),
[122-file source/test diffstat](evidence/source-diffstat.txt),
[complete source/test diff](evidence/source-and-tests.diff.gz),
[hashed file inventory](evidence/source-scope.json).

## Verified checkpoint

Final `mvn -B -ntp clean install` ran **15:52:54.831198–16:03:38.421153 UTC on
2026-11-10**, exit 0, no timeout, **643.588 seconds**, within its 1,200-second bound.
All **83 modules succeeded**. Fresh XML: **288 suites (287 Surefire + 1 Failsafe),
3,987 discovered, 3,864 executed, 123 skipped, no failures/errors**. All skipped
test identities match the preserved Phase 0 baseline; no new skip was introduced.
Docker-backed PostgreSQL/MySQL/Kafka/multi-instance and nine OTEL Failsafe tests
executed with no skips in their suites.

Focused matrix: 21 actual default-endpoint boot profiles, six native 0.3 REST
scope cases, public old 0.3 GetTask/send alongside shared explicit/default 1.x
on each experimental server, all 1.x ordinary methods with ingress capture,
1.x-only tenant checks and three actual no-0.3-classpath checks. Representative
old provider/wrapper unchanged bytecode and candidate-recompiled source both pass
wire tests. All original frozen Phase 0 fixtures remain unchanged; compiler-input
provenance and original response bytes are retained.

[Verification and limitations](verification.md),
[final independent review](evidence/fresh-review.md),
[final build metadata](evidence/full-changed-reactor-final.json),
[suite/skip evidence](evidence/full-changed-reactor-final-test-summary.json),
[preservation check](evidence/final-integrity.json).
Do not rerun the unchanged baseline without a concrete need. Do not rerun
`freeze-old-client.py` after candidate installs; use the retained old SDK archive.
No root build or probe remains active.

## Run accounting and failures to preserve

Phase 1 began **2026-11-10T14:57:25Z**, authorized deadline **16:57:25Z**,
maximum 10 focused iterations. **8/10 iterations completed**, no-progress **0**,
budget exhausted **false**. The last iteration packaged evidence and independent
review; it did not start another implementation phase. A later continuation is
not automatically a fresh time/iteration budget.

Historical Phase 0 remains **8 completed iterations, conservative A03 audit 10,
no-progress 0, original attempt-limit breach true**. Later authorization and
successful checks did not erase it. Phase 1's [stable attempt audit](evidence/attempt-audit.md)
retains failed implementation cycles: REST card scope failed twice then its third
implementation passed; linked ordinary native REST scope had an initially failing
implementation then passed its second. No inspected observable had three failed
material implementation cycles. Three-attempt and finite-deadline safeguards remain.

The first full root build failed three stream-lifecycle example cases and stopped
before later modules. Its log, partial counts and original failure reports are
retained separately; the final success does not turn that attempt into a pass.
Other RED/fix cycles and test-assumption failures remain in the command index
and progress record, including the withdrawn 0.3 tenant fixtures.

## Remaining gates and next action

Phase 1 is ordinary experimental plumbing, not final A2A 1.1 conformance or
complete A01–A14 acceptance. Generation/timeline, mutation authority, observation,
artifact input, long-poll, delivery-version config and live remain later work.
Issue #2415/HTTP live association, exact artifact/batch outcomes and cursor/recovery
semantics remain binding unresolved gates. PR2411 is not specification authority.
Final-spec reconciliation, full independent compatibility, complete documentation
and target TCK/conformance remain open. No invented header, session ID, receipt,
status RPC or retry/positive-input guarantee may close those gates.

**Wait for the user's explicit approval of the Phase 1 commit proposal.** If
approval arrives, inspect the current worktree against the approved scope, include
only approved Phase 1 files, use the proposed Conventional Commit message and
`This fixes #1303`, then record approval and resulting SHA in progress. Do not
push or create a PR. Do not begin Phase 2 without its own explicit authorization;
its approved Phase 1 commit is a prerequisite. If commit approval is absent,
leave this checkpoint uncommitted.
