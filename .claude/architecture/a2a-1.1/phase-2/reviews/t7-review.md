# T7 evidence review — 2026-11-16

**Disposition: T7 not accepted; Phase 2 completion not established.** This
record transcribes the independent Sol review supplied by the developer. The
reviewer did not run tests or builds; it inspected the candidate and the saved
evidence.

## Blocking finding

The candidate default 1.0 fixture run reproduces the historical cancellation
request failure fingerprint, then exits 1 with nine changed or missing
outputs. The current T3 evidence checks selected output paths, especially
webhook bodies, but does not establish parity for all nine outputs through the
explicit selected-1.0 paths. The unchanged fixture corpus remains authoritative;
the original failure fingerprint stays separate from positive comparisons.

The nine affected outputs are:

- `event-artifact_update.json`
- `event-status_update.json`
- `event-status_update_event_final.json`
- `event-task.json`
- `jsonrpc-task.json`
- `rest-task.json`
- `rpc-CANCEL_TASK_TEST_RESPONSE.json`
- `rpc-GET_TASK_TEST_RESPONSE.json`
- `rpc-SEND_MESSAGE_TEST_RESPONSE.json`

This remains blocking until exact affected cases pass through selected-version
1.0 paths against the unchanged expectations. Candidate 0.3 remains a separate
result: 24 positive comparisons plus its two historical failure fingerprints.

## Other reviewed evidence

- The three evidence layers remain distinct; same-instance candidate tests
  were not treated as historical compatibility evidence.
- Frozen/candidate API classpaths each contain 192 entries; 11 SDK JAR entries
  differ and 181 shared entries are identical. Available classpath hashes were
  independently matched. The retained old-compiled probe binary is present.
- NullAway legacy-caller compilation passes. The exhaustive sealed-switch
  failure is the D1-approved source break. Fixture checksums and expectations
  are unchanged, and `test.log` retains SHA-256
  `e31ad06e2049cdb7315c5da47bf3882ed092d0b0a5bea76da5d4a0dccba92e7f`.
- Earlier task records support T6 dispositions through T6c. T6c is accepted
  with a nonblocking race-test follow-up.
- The current-workflow header in `implementation-progress.md` incorrectly
  described T6c as pending; that current summary has been corrected. Historical
  checkpoint text remains preserved as history.

## Follow-up comparison evidence — 2026-11-16

After this review was supplied, a bounded diagnostic compared all nine outputs
through explicit candidate 1.0 projection/encoder paths against the unchanged
Phase 0 expectations. It exited 1: five outputs matched and four did not. The
results, actual and expected hashes, and candidate classpath hash are recorded
in [verification.md](../verification.md). The four mismatches are JSON-RPC
Task, REST Task, GET response, and SEND_MESSAGE response.

A separate `FrozenProtoSerializerProbe` process exited 0 on the 192-entry
frozen classpath. Its no-version protobuf serializer produced the same actual
hashes as the candidate explicit 1.0 path for those four cases. The frozen and
candidate classpaths were used in separate processes and were never combined.
The expected files are unchanged. This parity does not make the four outputs
positive fixture comparisons and does not resolve the original blocking
finding; it raises a focused question about selected-version output behavior
versus fixture-generation provenance.

## Remaining gates

Request a separate focused Sol disposition of the nine-case results, frozen
serializer parity, and bounded correction scope. Keep the original default 1.0
failure fingerprint and independent 0.3 result separate. Do not run the bounded
clean install while the review gate is closed. If the focused review accepts
the evidence and candidate, then run the bounded clean install and refresh
candidate artifact hashes and fixture comparisons; record Java 17, exit
status/time, reactor, Surefire/Failsafe discovery, zero-test targets, skip
identities, and Docker execution. Keep RC specification gates open and make no
final conformance claim.

The reviewer reports that runtime model metadata was unavailable, so active
model identity is not independently verified. No tests/builds were run as part
of that review.

## Correction re-review — 2026-11-16

**Disposition:** The original four-mismatch finding is closed as an invalid
cross-serializer comparison. The remaining 0/9 Gson result is an intentional
canonical-model representation change under the approved T2/D1 additions, not
by itself a 1.0 transport failure. One separate wire-contract question remains
for the public no-version SSE/JSON-RPC helpers, and the current-candidate full
build gate is not evidenced. T7 is not accepted.

### Revision and evidence scope

Reviewed branch `support/a2a-1.1.0`, HEAD
`34a120f73e551affca271850aff6d057baf413f6`. The worktree already had T6c
source/test edits and Phase 2 documentation edits, plus untracked probe sources,
evidence, `test.log`, and review records. These were preserved. The expected
fixture files are unchanged; I independently recomputed the nine relevant
`expected/1.0/*.json` hashes and they match the values recorded in
`verification.md`. `test.log` remains SHA-256
`e31ad06e2049cdb7315c5da47bf3882ed092d0b0a5bea76da5d4a0dccba92e7f`.
No build or test was run for this review.

The corrected evidence keeps these paths separate:

- The original-runtime reconstruction identifies source revision
  `2817ed78d02d9a6e609dafdf7f5129484edef017`; its separate Phase 0 classpath
  reports 355 verified SDK/dependency hashes and no candidate SDK classes.
  The unchanged `WireProbe` output matches 33 positive historical expectations
  and retains the original cancellation-request NPE fingerprint.
- The candidate protobuf payload probe reads the two unchanged Task inputs,
  adds the new fields, applies the selected 1.0 projection, then uses the
  historical `JsonFormat.printer().print(proto)` and `toByteArray()` paths.
  Its four separate `.proto.json`/`.pb` comparisons pass 4/4.
- The selected-version event probe uses the 1.0 event inputs, applies the
  projector and calls `JsonUtil.toJsonStreamingEvent(event, "1.0")`. The
  selected-version protobuf response probe uses the REST Task and JSON-RPC
  cancel/get/send inputs, applies the 1.0 projector and calls the same
  version-aware protobuf serializers used by the reference handlers. Candidate
  and frozen-runtime processes use separate classpaths; the comparison parses
  JSON values, so REST printer whitespace differences do not count as content
  differences. The four event and four response bodies match the original
  frozen runtime 8/8. These remain serializer-body comparisons, not full HTTP
  server tests.

### Original four-mismatch finding

Closed. The first 5/9 diagnostic compared protobuf response-printer output to
Gson `.json` expectations, so its four apparent mismatches were not valid
comparisons. The frozen no-version protobuf probe reproduced those outputs,
which did not validate them against the Gson fixtures. The corrected probes
compare each serializer with its own contract: protobuf payloads to
`.proto.json`/`.pb`, and selected-version endpoint serializer bodies to the
original frozen endpoint serializers. Those comparisons pass 4/4 and 8/8.
The original diagnostic and its failures remain preserved as history; they are
not positive fixture results.

The old candidate default 1.0 fixture command still records the separate
historical Gson mismatch. That result is not reclassified as a passing fixture
run: it is addressed by the model-level disposition below, while the original
cancellation failure fingerprint and the frozen 33-positive corpus result
remain separate.

### Gson difference and wire-contract disposition

The 0/9 result is valid for its stated scope. `SelectedVersionGsonFixtureProbe`
reads the historical 1.0 model inputs, adds the new fields, applies
`VersionedOutputProjector` with `V1_0`, and compares `JsonUtil.toJson` output to
the old `.json` expectations. The projector resets primitive stamps to zero,
but Gson still serializes those newly added primitive record components (for
example `generation: 0` and artifact generation stamps). `JsonUtil.toJson`
has no selected protocol-version parameter, so this is canonical shared-model
JSON output, not the versioned transport encoder.

That model-level difference is covered by the approved decisions: D1 in
`api-migration.md` explicitly approved the listed Task/Artifact record
component additions and their structural effects; T2 in the implementation
plan requires Gson serialization and round-trip coverage for the new model
fields. `Phase2ModelSerializationTest` asserts that Task generation, timeline,
and artifact provenance fields serialize and round-trip. Thus the 0/9 result
does not violate a contract requiring canonical 1.1 model JSON to equal a
historical 1.0 transport representation. No serializer or fixture change is
indicated by this comparison alone.

For selected 1.0 production routes, the reviewed call sites pass an explicit
version to the serializer boundary: JSON-RPC routes pass the selected policy
to `SseFormatter` and `JSONRPCUtils`; REST handlers pass it to
`ProtoJsonUtils.toJsonForVersion`; webhook dispatch calls
`JsonUtil.toJsonStreamingEvent(projectedEvent, version)`. Those paths apply
schema-path omission, and the corrected endpoint probes match the frozen
runtime. No in-repository selected-version 1.0 route was found that uses
generic `JsonUtil.toJson` for Task/event output.

There is a separate unresolved contract at the public no-version helpers.
`SseFormatter.formatResponseAsSSE(response, eventId)` is documented as a
framework-agnostic server integration helper and shown in its usage example;
it calls `JSONRPCUtils.toJsonRPCResultResponse(id, proto)` without a version.
That overload selects the unfiltered protobuf printer, which prints fields
without presence, including new zero-valued fields. The current reference
JSON-RPC route uses the three-argument, selected-version overload, so this is
not evidence that the in-repository 1.0 route leaks fields. However, the old
public helper can be used by an existing legacy server integration, and the
reviewed T2/D1 decisions do not specify whether its no-version wire output is
legacy 1.0 or version-agnostic canonical output.

**Recommended resolution:** preserve the old no-version server helper's
historical 1.0 wire behavior, and require the explicit-version overload for
1.1 output. Before T7 closes, either demonstrate that the old public overloads
omit the new schema fields (with unchanged historical output comparisons), or
obtain explicit owner approval that `SseFormatter`/`JSONRPCUtils` no-version
overloads are version-agnostic and may emit the added fields. The latter
approval must also update their API documentation and state that callers
serving 1.0 must pass the selected version. This unresolved public-helper
contract is separate from, and does not invalidate, the canonical Gson
disposition above.

### Full-build gate and remaining T7 status

The preserved `test.log` reports an 83/83-module `BUILD SUCCESS`, but it ended
at `2026-11-13T17:04:37Z`, before the current HEAD commit at 17:13:46Z and
before the T6c worktree edits. It does not record the Maven command or a
candidate source/artifact hash. It therefore cannot establish the current
candidate's full-build gate. The developer reports a later full-suite pass;
the available record does not yet identify that run as the reviewed candidate.

To satisfy this gate without automatically repeating a run that may already
have completed, retain from the developer's run (or a required fresh run) the
exact Maven argv, exit status and elapsed time, Java version, reactor module
inventory, current candidate identity including dirty-source hash, Surefire
and Failsafe discovery/results, zero-test targets, skip identities, Docker
execution, and resulting candidate artifact hashes. If those details establish
that the reported pass is for this candidate, no duplicate full run is needed.
If a clean install produces different artifacts, rerun only the affected T7
comparisons as required by the plan.

The recorded changed-module and same-instance regression selectors passed,
and the independent 0.3 fixture evidence remains 24 positive comparisons plus
its two historical failure fingerprints. No accepted binding review was
repeated. T7 remains open on the no-version helper contract and current
candidate full-build provenance; Phase 2 completion and final conformance are
not established.
