# Phase 2 checkpoint through T6b — 2026-11-13

The user requested a local checkpoint commit after T6b. This packages the accumulated Phase 2 work since Phase 1 commit `4efaf334e485af5d80dda39c35c6e635e2c11b1a`; it does not claim Phase 2 completion. Shared models/codecs, older-version projection, additive server/SPI boundaries, shared public client support, JSON-RPC T6a and REST T6b are included. T6c gRPC client support and T7 independent compatibility/evidence acceptance remain pending. Existing specification gates remain in force.

The [T6b independent final review](../reviews/t6b-final-review.md) accepts its reviewed REST scope and closes the bounded HTTP error-body and public terminal-race findings. Earlier review dispositions and failed attempts remain preserved. This packaging check is not a replacement for the pending T7 audit.

## Fresh checkpoint verification

Raw Maven invocation **181**, following the retained implementation ledger through 180, ran the exact focused selector in the [machine-readable receipt](../evidence/through-t6b-commit-check.json). Java 17.0.18 / Maven 3.9.14; 2026-11-13 10:02–10:03 UTC; exit 0, **35 reactor modules successful**, **37 suites / 610 discovered / 609 executed / 1 skipped / 0 failures / 0 errors**. The skip is the unchanged `@Disabled` JSON-RPC handler custom-components test. The [compressed Maven log](../evidence/through-t6b-commit-check.log.gz) and receipt retain the command, suite counts and tested changed functional-file hashes.

Coverage includes the focused Phase 2 model/codec tests, projection and handler boundaries, SPI/public HTTP client paths, both telemetry wrappers, and JDK/Vert.x/Android HTTP regressions. This was a selected reactor test run, not a full-reactor build, historical fixture gate, external conformance run, T6c implementation or T7 acceptance. Existing missing-plugin-version warnings remain; Spotless is skipped by project configuration.

The historical API-probe JAR matches its retained SHA-256. Frozen Phase 0 fixtures are unchanged. Handwritten tracked changes pass `git diff --check`; standard protobuf-generated direct Java output retains generator-emitted trailing whitespace. Functional candidate hashes were unchanged during verification. No runtime or test source was edited for packaging.

This is commit-only verification authorized by the user's commit request, not an implementation iteration or renewal of an expired implementation allowance. No push, PR or Phase 3 is authorized by this checkpoint.
