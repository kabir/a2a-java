# Frozen historical A2A wire fixtures

Source revision: `2817ed78d02d9a6e609dafdf7f5129484edef017` of
https://github.com/a2aproject/a2a-java, extracted from the authorized worktree
`/Users/kabir/.codex-api/worktrees/3053/a2a-java` on 2026-11-10.
No repository production or test source was changed by this extraction.

## Provenance

`manifest.json` records source commit/path, Java constant name and SHA256 for
six complete historical fixture source classes, two historical locally adapted
proto sources and112 exact Java runtime fixture constants. `extract.py` uses
`git show COMMIT:PATH`, compiles the unmodified fixture classes independently
and obtains their static String values through reflection. This preserves Java
text-block whitespace and escape semantics exactly. Raw fixtures include full
JSON-RPC and REST send/get/cancel/push-config examples and complete SSE streams.

Official upstream files fetched read-only through `gh api` are included as
`upstream-1.0.proto`, `upstream-0.3.proto`, and `upstream-0.3.json`.
Official1.0 tag resolves directly to commit
`173695755607e884aa9acf8ce4feed90e32727a1`; source path
`specification/a2a.proto`. Official0.3 tag object
`210f03d426e2f2fa92000e14ef0de3b7ba15aee5` resolves to commit
`8d57eba286de756176892518a8fc39b0ac2ccefb`; source paths
`specification/grpc/a2a.proto` and `specification/json/a2a.json`.
The1.0 tag has no JSON schema file in specification/json (only README.md).
The locally adapted old protos are preserved separately; their changes are
listed in their historical source comments. `SHA256SUMS` covers all packaged
files except itself, including raw, derived and expected data.

## Executed evidence and limits — initial checkpoint

The prior1.0 run passed33 positive wire comparisons and1 known-failure
fingerprint. The positive outputs cover JSON-RPC send/get/cancel responses,
request envelopes and errors, REST/JSONRPC send parameters and tasks, and
five task/message/status/artifact event examples. Direct domain values also
have historical protobuf JSON and binary bytes. `expected/1.0` was captured
from unchanged old built artifacts before shared model changes.

Known1.0 defect: deserializing the exact historical cancellation request with
absent metadata throws NullPointerException in CancelTaskParams through
CollectionCopies.unmodifiableShallowMap. It is recorded separately as
`rpc-CANCEL_TASK_TEST_REQUEST.error.json`; it is not a positive wire pass.

The first0.3 capture completed immediately before the STOP instruction arrived.
It produced26 outputs in `/tmp/a2a-history-baseline-03/0.3`, including two
failure fingerprints: historical REST send/task fixtures omit kind fields
required by JsonUtil_v0_3. That capture was NOT verified and was NOT promoted
into expected/0.3. At that initial checkpoint the executable0.3 criterion was incomplete; see the explicitly authorized follow-up below.

Historical raw0.3 request constants with trailing commas remain unchanged.
They were excluded from positive derived probes instead of silently repaired.
`probes.json` identifies every selected raw source and derivation. Derived
inputs add `2026-01-01T00:00:00Z` only where a status timestamp was absent,
because historical constructors otherwise inject current time. Original raw
bytes remain unchanged. JSON outputs compare semantically; protobuf outputs
compare exact bytes. This is a selected codec regression baseline, not proof
of HTTP/gRPC routing, cancellation lifecycle or complete protocol conformance.
Full send/get/list/cancel/history/blocking/webhook coverage across all three
bindings, gRPC status errors and transport execution remain future work.

## Explicitly authorized follow-up and frozen runtime

The user subsequently authorized resuming Phase 0 checks with a staged
approach. The final retained harness passed1.0 again:33 positive comparisons
plus1 known-failure fingerprint. A separate0.3 capture matched all26 outputs
in the archived original pre-STOP capture:24 positive comparisons plus2 known
failure fingerprints. The original archived bytes, not the follow-up capture,
were then promoted into expected/0.3. No raw fixtures, derived inputs, harness
behavior or1.0 expectations changed. The two known0.3 defects remain failures
of historical fixture/decoder compatibility, not positive passes.

Before execution, all SDK classpath entries were copied away from reactor
targets and hashed. `../evidence/historical-sdk-runtime.tar.gz` retains the
eight SDK class directories/jars and manifest from unchanged HEAD2817ed78.
`../evidence/historical-runtime-manifest.json` records every SDK/dependency
file hash and original classpath. Third-party jar coordinates/paths/hashes are
recorded but third-party binaries are not bundled. Runtime checks used copied
dependencies and SDK entries only; no reactor classpath replacement or SDK
rebuild took place. This is a pre-1.1 source-revision SDK binary snapshot, not
a claim of executing a published release or a complete transport peer.

Exact commands, exit codes, times, output and the original-archive comparison
are retained under `../evidence/historical-*followup.{json,log}` and
`../evidence/historical-0.3-comparison.json`. Both checks passed under180second
bounds without retries. Final A03 still requires the complete all-binding
contract matrix; selected codec evidence does not pass that final criterion.

## Candidate rerun instructions

The historical attempt breach and explicit follow-up authorization are retained
in `attempts.md`. Future runs must read current progress and authorization;
a successful baseline comparison does not authorize Phase 1 or repeated work.

After new authorization and a completed candidate build, obtain its runtime
classpath from an existing Surefire report and compare against frozen expected
outputs, without running extract.py or prepare_probes.py and without capture
(use `--version 0.3` analogously for the now-frozen0.3 expectations):

```sh
python3 classpath.py /absolute/path/to/candidate --version 1.0 --output /tmp/candidate-1.0.cp
python3 run.py --version 1.0 --classpath-file /tmp/candidate-1.0.cp
```

The scripts need Java17+ and Python3; Maven is not invoked. `run.py` compiles
only the independent WireProbe.java to a temporary directory, with60second
javac/java subprocess timeouts. `classpath.py` reads existing Surefire XML.
Current capture classpath files are provenance snapshots of paths, not bundled
SDK binaries. They must not be reused as evidence against a changed candidate
without checking that they point to its actual built classes and dependencies.

`--capture` refuses an existing destination, but must not be used to regenerate
frozen expectations as part of a candidate verification. No captures or verification occurred after STOP until the user explicitly
authorized the follow-up above. No harness behavioral changes were required
for that follow-up.
