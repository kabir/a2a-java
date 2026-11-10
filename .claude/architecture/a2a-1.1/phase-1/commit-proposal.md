> Current status: user explicitly approved this exact Phase 1 commit with **“YEs, commit”** at 2026-11-10T19:47:38.723628+00:00. The proposal below preserves the reviewed pre-approval text and exact approved message. Push, PR and Phase 2 remain unauthorized.

# Phase 1 commit proposal — verified, awaiting approval

The final current whole-reactor result, final independent review and refreshed integrity/scope comparison support presenting this exact candidate for commit approval. Creating the commit still requires the user’s explicit approval. No staging, commit, push, PR, or Phase 2 action has been authorized.

Candidate worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`, branch `support/a2a-1.1.0`, expected unchanged HEAD `e468dd70a415d17c19ae70d75750b6464d983ad1`. The issue is confirmed as #1303. The user has approved the material owned-channel compatibility/backend/lifecycle change for both 1.0 and 1.1 and five resumed focused iterations. That approval does not include creating a commit.

## Proposed change

Add explicit shared-client protocol/interface selection and matching server registration/routing policy. Shared clients default to 1.1; explicit 1.0 remains available. Preserve native 0.3 conversion and registered original call context. Use the selected descriptor's binding, endpoint, version, and tenant consistently. Keep `AgentInterface.CURRENT_PROTOCOL_VERSION` and demonstrated model defaults at 1.0.

Require SDK-owned gRPC channels for both versions, using managed options or `createManaged`, with pinned Netty provider isolation, strict pre-I/O metadata validation, fresh allocation cleanup, and bounded owned close. Migrate shared consumers, tests, examples, and dev docs; preserve native 0.3 APIs and frozen released docs/expected outputs. Correct REST subscription verb handling and native 0.3 registered context. Both hello-world and stream-lifecycle examples explicitly retain protocol 1.0.

Exact proposed paths and hashes come from refreshed [commit files](evidence/commit-files.txt), [source scope](evidence/source-scope.json), [diffstat](evidence/source-diffstat.txt), [diff](evidence/source-and-tests.diff.gz), and [final integrity](evidence/final-integrity.json). The final functional input set has **156 files (73 modified / 83 new), 8,460 insertions / 1,015 deletions**. The complete proposed commit inventory has **394 paths**, including the phase-scoped architecture and retained evidence. All functional hashes match the completed build and independent review. No Phase 0 changes or later-phase implementation are included.

## Validation to present with approval request

- **Final whole-reactor command: `mvn -B -ntp clean install`, exit 0 within 1,200 seconds (`resume-full-current-reactor-final`).**
- **83 successful modules; 4,041 discovered / 3,918 executed / 123 skipped / zero failures or errors.**
- **Final independent review finds no blocking defect; exact source hashes/diff/inventory and frozen-input integrity pass. All 123 skipped identities match Phase 0.**
- Current affected clean build: 8 successful modules, 698 discovered/executed, zero skips/failures/errors, including private-pin and opaque CallCredentials/tracer rejection coverage. All seven provisioning subsets passed after isolating destructive malformed-header probes on fresh raw connections.
- The first full resumed attempt remains archived as failed: 4,040 discovered / 3,917 executed / 123 same baseline skips / 1 failure / 0 errors; 78 successful / 1 failed / 4 skipped modules. Its sole failure was the second malformed whitespace request reusing a GOAWAY connection. The correction retains exact INTERNAL + PROTOCOL_ERROR + zero application assertions; expectations were not weakened.
- TLS/authentication, cancellation, owned close, provider race/isolation, lifecycle cleanup, exact descriptor/tenant/version binding, REST GET/POST, and native 0.3 context evidence are retained in [verification](verification.md) and [independent review](evidence/resume-final-independent-review.md). Overlapping focused totals are not additive.
- Hello-world is included in the 83-module root reactor. Optional whole ITK compilation is unavailable because its external proto checkout is absent; the exact migrated factory expression passed a narrow javac type check at 19:16:23Z, which is not an ITK module build.

The pinned grpc-java 1.81.0 provider profile requires source re-audit on dependency upgrade. Async credential adapters are intentionally absent. The release remains experimental RC; PR2411 remains a proposal. HTTP association (#2415), artifact/batch outcome, and cursor/recovery gates remain open for later phases. No final conformance or completed A03 claim is made.

## Exact proposed Conventional Commit message

```text
feat(a2a-1.1)!: add explicit interface and client version policy

Bind shared clients to the selected protocol version and interface and enforce
matching server registration and routing. Preserve explicit 1.0 and native 0.3
behavior, REST subscription verbs, and registered original call context.

Require SDK-owned gRPC channels with pinned Netty provider isolation, strict
metadata validation, and bounded lifecycle cleanup. Migrate shared consumers,
examples, tests, and development documentation to managed channel options.

BREAKING CHANGE: Shared clients default to protocol 1.1; select 1.0 explicitly
when migrating existing integrations. Arbitrary raw gRPC channels and channel
factories are rejected for both 1.0 and 1.1. Use
GrpcTransportConfigBuilder.managedChannel(GrpcManagedChannelOptions) or
GrpcTransport.createManaged(...) and close the owning SDK client or transport.

This fixes #1303
```

After final verification/review/integrity checks pass, ask: **Approve creating this commit for the exact reviewed Phase 1 candidate and message above?** The reason for asking is that creating a commit has not yet been authorized; the user's continuation and material API approvals cover implementation and review only. This question grants no authority to push, open a PR, release, or start Phase 2.
