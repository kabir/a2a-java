# Phase 1 verification and limits

> **Superseded by subsequent independent review: changes required.**
> Three open findings invalidate the earlier no-blockers/commit-ready conclusion.
> See [review response and correction plan](review-response.md).
> The recorded successful build remains valid evidence, but does not cover these
> failures. The original implementation authorization has expired; corrections
> require explicit additional time/iteration authorization. No commit is approved.

**The authorized Phase 1 scope is implemented, verified and independently reviewed.
No commit has been created.** Accepted Phase 0 and unchanged HEAD:
`e468dd70a415d17c19ae70d75750b6464d983ad1`, branch `support/a2a-1.1.0`.
The target remains experimental RC `78e1d6a1f7f118c8e9bc830b04a8579c1119cb78`.
Execution and architecture approval do not authorize a commit or Phase 2.

## Final changed-code build

```sh
mvn -B -ntp clean install
```

Java 17.0.18 (Temurin), Maven 3.9.14. Run in the requested worktree, without a
Docker skip switch. Started **2026-11-10T15:52:54.831198Z**, ended
**16:03:38.421153Z**, elapsed **643.588 seconds**, exit **0**, no timeout within
the **1,200-second** deadline. **All 83 reactor modules succeeded.**

| Fresh XML reports | Discovered | Executed | Skipped | Failures | Errors |
| --- | ---: | ---: | ---: | ---: | ---: |
| 287 Surefire + 1 Failsafe suites | 3,987 | 3,864 | 123 | 0 | 0 |

[Metadata](evidence/full-changed-reactor-final.json),
[complete compressed log](evidence/full-changed-reactor-final.log.gz),
[exact suites, skipped cases and report hashes](evidence/full-changed-reactor-final-test-summary.json),
[environment](evidence/environment.json).
All report modification times fall inside this clean build's recorded interval.
The independent reviewer separately recounted the XML and reactor summary.

The skipped test identities exactly match the retained Phase 0 baseline:
[comparison](evidence/skip-identity-comparison.json). They are existing binding
assumptions and disabled/unsupported cases, not verified behavior. No new skip was
introduced. PostgreSQL authorization 5, MySQL authorization 6, Kafka replication
integration 4, actual multi-instance replication 2 and OpenTelemetry Failsafe 9
all executed with no skips, failures or errors. These verify their existing
scope, not future 1.1 mutation or live semantics.

## Evidence by requirement

| Phase 1 slice | Direct evidence | Limits |
| --- | --- | --- |
| Exact server provisioning and dispatch | InterfaceRegistrationTest, VersionRouterTest, shared/optional gRPC preflight, HTTP route and versioned error tests; seven actual default-endpoint profiles per binding | Ordinary implemented behavior only; incomplete 1.1 requires experimental registration. |
| Immutable client version and selection | ClientBuilderVersionTest, ClientProtocolVersionTest, both HTTP ProtocolVersionTest families, GrpcTransportTest, real paired public clients | SDK context/interceptor conflicts reject before I/O; arbitrary caller-owned Channel mutations afterward are outside this guarantee. |
| Paired public usability | Same registered running server per binding: default 1.1, explicit 1.1/1.0, separate public Client_v0_3 GetTask/send; 1.x ordinary send/get/list/cancel/config/extended-card/stream/subscribe with ingress capture | Full ordinary matrix on the experimental all-three profile; subset profiles exercise public GetTask and 0.3 send, not every operation for every subset. |
| Provisioning subsets and scope | 21 actual default-endpoint boots; disabled/missing/empty/unsupported and stale-descriptor negatives; three nonempty 1.x tenant subsets; six native 0.3 REST scope cases | 0.3 has no tenants. Earlier raw 0.3 tenant aliases and tenant-v1-with-0.3 fixtures are withdrawn as acceptance evidence. |
| Optional 0.3 packaging | Only11JSONRPCPackagingTest, Only11RestPackagingTest, Only11GrpcPackagingTest | Runtime absence of optional 0.3 model; exact 1.1 card, default public GetTask and disabled/missing rejection. |
| Provider/wrapper compatibility | Unchanged old-API bytecode, same sources recompiled and separately run, ClientTransportPolicyTest, both OpenTelemetry wrapper families | Representative additive Phase 1 boundaries; no full downstream binary compatibility or complete A03 claim. |
| Frozen compatibility evidence | Byte-identical original 1.0 GetTask response in a public wire test; all original Phase 0 fixture hashes retained; archived old compiler SDK inputs | Selected historical evidence, not a complete old-peer lifecycle/history/error/blocking/webhook matrix. |
| Review/build | Initial client/server independent review and user-requested fresh review; final clean build | Building TCK SUT is not execution of external target TCK or certification. |

[Client report](client-wire-report.md), [server report](server-report.md),
[paired-test report](paired-tests-report.md),
[concrete migration/provisioning examples and fixture provenance](compatibility/README.md).

## Focused checkpoints and retained failures

| Command label | Actual result |
| --- | --- |
| client-review-green | Exit 0; 16 modules; 922 discovered / 917 executed / 5 existing skips; no failures/errors. |
| legacy-wrapper-binary-green, legacy-source-wire-green | Each 4 tests, no skips/failures/errors. All three provider/wrapper classfiles byte-identical across old and candidate compilation. |
| server-review-green | Exit 0; 35 modules; 291 executed, no skips/failures/errors across 8 actual suites. |
| rest-final-focused | Exit 0; 68 discovered / 59 executed / 9 existing skips; card default-scope correction's third implementation passed. |
| full-changed-reactor (first root build) | Exit 1; partial 164 fresh reports, 2,528 discovered / 2,484 executed / 44 skipped / 3 example failures / 0 errors. Later modules were skipped. This is retained failed evidence. |
| example-migration-green | Exit 0; existing StreamLifecycleTest passes all 3 bindings; cloud helper compiles. No separate cloud behavioral test claim. |
| native03-registration-red | Initial new registration allowed 0.3 with tenants: 8 tests, 1 failure before guard correction. |
| native03-rest-routing-red | Guard's 8 tests passed; native routing still failed before correction; disabled public-client checks also exposed an invalid typed-error assumption in the new test. |
| native03-and-paired-focused | Exit 0, 151.859 seconds, 44 modules; 42 suites, 400 discovered / 360 executed / 40 existing skips, no failures/errors. All 21 subsets, 6 native REST scope cases and 3 packaging tests pass. |

[All 32 bounded command records and actual log-suite totals](evidence/command-summary.json)
retain exact argv, cwd, timestamps, PID, deadline, exit and timeout. Corresponding
JSON and deterministic gzip logs are alongside that index. Counts for filtered
commands use suites printed by that command, avoiding stale XML.
The [first failed root snapshot](evidence/full-changed-reactor-test-summary.json)
and its [original example failure reports](evidence/first-full-build-failure)
remain separate from the final successful result. Expectations were never
regenerated with candidate codecs.

Native 0.3 REST now resolves historical `/v1/...` at the default endpoint;
explicit 1.x `/v1/...` still selects tenant `v1`. Fabricated 0.3 tenant aliases
reject. The unchanged old REST mapper has no typed version error for the retained
dispatcher envelope. Public disabled-path checks preserve its observed generic
empty-message exception; independent native GET and valid POST establish HTTP
400 / -32009 / the 0.3 version reason. No new 0.3 error guarantee was invented.

Space-only gRPC metadata is rejected by the tested HTTP/2 layer with
PROTOCOL_ERROR before application ingress. Wire tests assert that result and zero
ingress; direct boundary tests separately establish blank-to-0.3 normalization.
Missing and empty valid metadata keep exact application-version assertions.

The requested note, **“existing examples to 1.0, correct native 0.3 REST routing,”**
was initially recorded for future work, then explicitly authorized to proceed in
this run. Both corrections are included and verified. Existing model/server
convenience defaults and `CURRENT_PROTOCOL_VERSION` remain 1.0; new shared clients
independently default to 1.1, without negotiation or fallback.

## Independent review, counters and remaining gates

The [fresh independent review](evidence/fresh-review.md) found two P1 corrections
and one P2 coverage gap, then verified their fixes and the final build. Its final
verdict is **no open blocking findings for authorized Phase 1**. The original
[client review](evidence/client-review.md) and [server review](evidence/server-review.md)
are retained. The [stable attempt audit](evidence/attempt-audit.md) preserves
failed cycles: REST card scope retains 2 failures followed by the third pass;
ordinary native 0.3 REST retains its initial failure and passing second
implementation, linked to the same historical-prefix root cause. No inspected
observable reached three failed implementation cycles.

Phase 0 remains 8 completed iterations, conservative A03 audit 10, no-progress 0,
original attempt-limit breach true; subsequent authorized checks did not erase
that history. Phase 1 closes separately within 8/10 focused iterations and the
authorized two hours, no-progress 0, budget exhausted false. All build commands
were sequential with finite deadlines; no build remains live.

No full A01–A14 criterion is marked complete by these Phase 1 slices.
Generation/timeline, mutation authority, observation, long-poll, delivery versions,
artifact inputs and live functionality remain later-phase work. HTTP live
association (#2415), exact artifact/batch outcomes and cursor/recovery semantics
remain binding unresolved gates. PR2411 is proposal evidence, not specification
authority. Final 1.1 reconciliation, complete compatibility acceptance, broader
migration/docs and target TCK/conformance remain open. No session identifier,
receipt, status RPC, retry guarantee or positive-input guarantee was added.

Final preservation and link checks are in [integrity evidence](evidence/final-integrity.json).
The next action is the [commit proposal](commit-proposal.md), subject to explicit
user approval; see [next-session handoff](next-session.md).
