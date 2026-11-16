# Phase 2 completion and compatibility verification

**Current disposition: Phase 2 complete within the approved experimental pinned-RC
scope.** All Phase 2 tasks, the T6c public race follow-up and T7 are accepted.
Open specification gates remain for affected later work and final conformance;
they do not invalidate the completed representations/codecs/adapters in this scope.
See the [final commit close-out](checkpoints/phase2-complete.md), which identifies
the later 18:40:54 UTC full-build log, current source hashes and fresh focused
verification. Earlier statuses/receipts below are preserved historical records.
No final conformance claim or Phase 3 authorization is implied.

---

# Historical Phase 2 T7 compatibility and evidence verification

**Status: T7 compatibility/evidence gate accepted after the focused Sol
correction review, the final Java 17 clean install and all affected historical
serializer comparisons passed. Phase 2 completion is not established.** The
independent correction re-review closed the earlier protobuf-versus-Gson
four-mismatch finding and accepted the separate evidence: protobuf payload
fixtures 4/4, selected-version event and response serializer bodies match the
isolated frozen runtime 8/8, and model-level Gson output 0/9 is an intentional
T2/D1 model change. The old no-version public helpers select legacy 1.0;
explicit-version overloads carry 1.1. No fixtures or generic Gson
serialization were changed.

Run context: branch `support/a2a-1.1.0`, HEAD `34a120f73e551affca271850aff6d057baf413f6`,
Java Temurin 17.0.18. T6c correction re-review is accepted with nonblocking
follow-ups ([review](reviews/t6c-review.md#correction-re-review--2026-11-16));
it is the prerequisite disposition for this work. Pre-existing Phase 2 edits
and untracked files were preserved. The prior root `test.log` was absent at
the start of the helper correction; the final clean-install log is now saved
at the root as `test.log`.

## Evidence layer 1: public SDK controlled peers

These exercise the public SDK and controlled peers. They are not historical
wire-compatibility results.

| Surefire suite | Tests | Failures / errors / skips |
| --- | ---: | ---: |
| `client.base` `Phase2PublicClientTest` | 12 | 0 / 0 / 0 |
| `client.base` `Phase2RestPublicClientTest` | 8 | 0 / 0 / 0 |
| `client.base` `Phase2GrpcPublicClientTest` | 7 | 0 / 0 / 0 |
| `client.base` `AuthenticationAuthorizationTest` | 12 | 0 / 0 / 0 |
| `client.base` `Phase2ClientInputTest` | 8 | 0 / 0 / 0 |
| `client.base` `ClientTransportPolicyTest` | 9 | 0 / 0 / 0 |
| `client.base` `LegacyProviderCompatibilityTest` | 4 | 0 / 0 / 0 |

The first three rows are the public controlled-peer suites. The remaining rows
are supporting public SDK input, authorization, policy and legacy-provider
checks. All listed suites were present in the passing candidate regression
reactors below. The table reports suite counts, not extra selector totals; do
not add these to the reactor-wide totals.

## Evidence layer 2: raw malformed and future payloads

These are transport-level raw-payload tests. Their passing results do not
replace public SDK tests or frozen historical comparisons.

| Surefire suite | Tests | Failures / errors / skips |
| --- | ---: | ---: |
| `client.transport.jsonrpc` `Phase2PublicClientTest` | 19 | 0 / 0 / 0 |
| `client.transport.rest` `Phase2PublicClientTest` | 22 | 0 / 0 / 0 |
| `client.transport.grpc` `Phase2PublicClientTest` | 5 | 0 / 0 / 0 |

## Evidence layer 3: unchanged historical fixtures and binary/API probes

The fixture commands were run exactly as listed in the T7 plan. No extraction,
capture, preparation, or expected-output update was run.

```sh
python3 .claude/architecture/a2a-1.1/phase-0/fixtures/classpath.py "$PWD" --version 1.0 --output /tmp/a2a-phase2-candidate-1.0.cp
python3 .claude/architecture/a2a-1.1/phase-0/fixtures/run.py --version 1.0 --classpath-file /tmp/a2a-phase2-candidate-1.0.cp
python3 .claude/architecture/a2a-1.1/phase-0/fixtures/classpath.py "$PWD" --version 0.3 --output /tmp/a2a-phase2-candidate-0.3.cp
python3 .claude/architecture/a2a-1.1/phase-0/fixtures/run.py --version 0.3 --classpath-file /tmp/a2a-phase2-candidate-0.3.cp
```

| Command | Exit | Result |
| --- | ---: | --- |
| 1.0 `classpath.py` | 0 | Runtime classpath read from `spec-grpc/target/surefire-reports/TEST-org.a2aproject.sdk.grpc.utils.JSONRPCUtilsTest.xml`. |
| 1.0 `run.py` | 1 | Historical `rpc-CANCEL_TASK_TEST_REQUEST` failure fingerprint reproduced (`NullPointerException`, `Cannot invoke "java.util.Map.size()" because "m" is null`), then candidate default-serialization regression reported. Changed/missing expected files: `event-artifact_update.json`, `event-status_update.json`, `event-status_update_event_final.json`, `event-task.json`, `jsonrpc-task.json`, `rest-task.json`, `rpc-CANCEL_TASK_TEST_RESPONSE.json`, `rpc-GET_TASK_TEST_RESPONSE.json`, `rpc-SEND_MESSAGE_TEST_RESPONSE.json`; no extras. **No 1.0 positive-pass total is claimed.** |
| 0.3 `classpath.py` | 0 | Runtime classpath read from the compatibility `spec-grpc` Surefire report. |
| 0.3 `run.py` | 0 | `PASS: 24 wire comparisons; 2 known-failure fingerprints`; the unchanged failures are the historical REST send-params and missing-task-kind fingerprints. |

### Superseded diagnostic: protobuf output compared with Gson expectations

The following earlier 5/9 result is retained as a failed diagnostic, not valid
compatibility evidence. It compared protobuf JSON printers with `.json`
expectations generated by `WireProbe.java` through Gson. The correct protobuf
payload expectations are the separate `.proto.json` and `.pb` files. The
independent original-runtime diagnosis reproduced the same four false
mismatches and hashes on the frozen runtime. Do not count this result as a
candidate failure or a positive pass. The original candidate `run.py` failure
above remains separately preserved as historical Gson-harness evidence.

The candidate classpath, earlier diagnostic output and hashes below are
retained for audit. The 67-entry path `/tmp/a2a-t7-selected-version.cp` has
SHA-256 `8c2896be6752c8e7f11d6824928f57c7a116432eeb0436ecf770cbf367e6e595`.
The old 192-entry `/tmp/a2a-t7-frozen.cp` used in that diagnostic is not the
independent Phase 0 runtime classpath used by the corrected comparisons.

The final direct invocation was:

```sh
mkdir -p /tmp/a2a-t7-candidate-probe-classes
javac -cp "$(cat /tmp/a2a-t7-selected-version.cp)" -d /tmp/a2a-t7-candidate-probe-classes .claude/architecture/a2a-1.1/phase-2/evidence/SelectedVersionFixtureProbe.java
java -cp "/tmp/a2a-t7-candidate-probe-classes:$(cat /tmp/a2a-t7-selected-version.cp)" SelectedVersionFixtureProbe
```

The directory creation and compilation exited 0. An initial launcher command
used a package-qualified class name even though the probe is in the default
package; it exited 1 with `ClassNotFoundException`. The corrected final
launcher shown above exited 1 because the probe found the four fixture
mismatches. An earlier diagnostic source revision also passed the JSON-RPC
request ID as a `JsonElement` and therefore quoted it; that revision was
corrected to use the string ID and none of its results are counted here.

| Output | Explicit candidate 1.0 result | Actual SHA-256 |
| --- | --- | --- |
| `event-artifact_update.json` | match | `65baf9e3b9bdb9b091f2cc6311898cfa9c996544a24856daf8fda55c942ac732` |
| `event-status_update.json` | match | `6c0cbc8ba5c3b59a924d6c81af33e0837637954a452734a558e4fe00cb8f31df` |
| `event-status_update_event_final.json` | match | `89ac065fa0bd6319a0445495fa270c694bf81ab762befc227d9901446d51aca3` |
| `event-task.json` | match | `1b3a97b96b75ca4b931a43f679af8aef625c672cbb5d632cd94cd090d34b76e5` |
| `jsonrpc-task.json` | mismatch | `6e9f1ccf7ac2f43be5238ed1ea47de1a2c8ebc52c11d50889990e0a901818eb4` |
| `rest-task.json` | mismatch | `6e9f1ccf7ac2f43be5238ed1ea47de1a2c8ebc52c11d50889990e0a901818eb4` |
| `rpc-CANCEL_TASK_TEST_RESPONSE.json` | match | `68a9960a31f5e13a9bcc9c351bea00eeb0d07d65e74788c7f2fba19a32f46097` |
| `rpc-GET_TASK_TEST_RESPONSE.json` | mismatch | `78adda093d0aefcc6193939cc7c09fa0b40b8968267990b015784a37ac352831` |
| `rpc-SEND_MESSAGE_TEST_RESPONSE.json` | mismatch | `158d2638ad57373cc62accfb59d7286ee23fb918d04564fa777a710897fd9c86` |

A second, separate Java process ran `FrozenProtoSerializerProbe` on
`/tmp/a2a-t7-frozen.cp` (192 entries; SHA-256
`3a700279261a4dce5fc871c80ec09b0e959c6d3e71e63a2f9305de10bbf39498`). Its
direct commands were:

```sh
mkdir -p /tmp/a2a-t7-frozen-probe-classes
javac -cp "$(cat /tmp/a2a-t7-frozen.cp)" -d /tmp/a2a-t7-frozen-probe-classes .claude/architecture/a2a-1.1/phase-2/evidence/FrozenProtoSerializerProbe.java
java -cp "/tmp/a2a-t7-frozen-probe-classes:$(cat /tmp/a2a-t7-frozen.cp)" FrozenProtoSerializerProbe
```

All three commands exited 0. It used the frozen classpath independently; the
frozen and candidate classpaths were never combined. The frozen no-version protobuf serializer
produced the same actual output hashes as the candidate for the four
nonmatching cases: JSON-RPC Task, REST Task, GET response and SEND_MESSAGE
response. Those four remain mismatches against the unchanged expected files.
This parity is evidence about the serializer paths, not a positive fixture
comparison or a self-disposition of Sol's blocker.

The expected files remain unchanged. Their SHA-256 values, in the same order
as the nine rows above, are `e6dd825ae364d58a4734dd09694a9342c0b7fb894c76aa3fb9d71c8c7f7df5af`,
`19254a85acb5017d313315ba7657c524788bb4e2b3fce14288c535a67976dd54`,
`e423b776088293d753bca381cc513dd5ee1b43f5f152a175584e6d3c35ebb43d`,
`17feeca3a5cd7eeb8d336604e208eb5a706a042f0460e946ea9b7d02e0134296`,
`88fdff3f157a23a4b04962e19a36c3b98e116ac8f50fa732b6300e0ded95e232`,
`88fdff3f157a23a4b04962e19a36c3b98e116ac8f50fa732b6300e0ded95e232`,
`95596d08391e26a8184f07709e0aed8b53897478f3315089818465cf0426dae2`,
`68f1bba65d478bda96f78d5da4a9fd79e2a26b76cf2180b7953ed540ddbb2949`, and
`40858e7de25a189de932aea88e67e2a3d7ea587d395d11e29189ccc24c19cb85`.
The original default 1.0 failure and its historical cancellation-request
fingerprint remain separately recorded above; no 1.0 positive-pass total is
claimed. Do not recapture or weaken expectations. The concrete failure is the
four explicit candidate outputs differing from the frozen expected JSON even
though they agree with the frozen protobuf serializer. Proposed correction
scope is limited to review of the fixture-generation/serialization provenance
and the selected-version protobuf JSON output path. Do not change production
code or fixtures until the reviewer identifies which behavior the frozen
expectations require.

### Corrected serializer-path evidence — 2026-11-16

The original Phase 0 runtime was independently reconstructed from source
revision `2817ed78d02d9a6e609dafdf7f5129484edef017`. The separate classpath at
`/var/folders/6m/rzj3124j3qdd9rstb_f2s9th0000gn/T/a2a-coordinator-baseline-qk04wryd/phase0.cp`
has SHA-256 `b8f66b5e851ec46da6e7de466fad51d76a5b4f1cac48f7420265c5baed762224`;
the diagnosis reports 355 SDK/dependency file hashes verified and no candidate
SDK classes in this classpath. Its unchanged `WireProbe.java` run exited 0,
wrote 34 outputs, and matched 33 positive historical expectations. The sole
failure remains the original cancellation-request NPE fingerprint. I
independently compared its retained output directory with `expected/1.0`:
33 positive JSON/protobuf/binary matches and no mismatches; the error output
matches the preserved failure fingerprint. No fixture was written or changed.

The corrected direct Gson projection probe uses `JsonUtil.toJson`, the same
serializer used by `WireProbe.java` for `.json` expectations. It is model-level
Gson evidence, not actual HTTP response evidence. It exited 1 with **0/9**
matches; its outputs expose zero-valued newly added fields after projection
(`generation: 0`, and artifact `startGeneration: 0`/`endGeneration: 0`). This
explains the candidate's separate failed Gson fixture run, but does not show an
HTTP response regression. The concrete remaining question is whether generic
Gson object serialization is in the required T7 compatibility scope; no
production serialization change was made.

| Gson object output vs unchanged `.json` | Result | Actual SHA-256 |
| --- | --- | --- |
| `event-artifact_update.json` | mismatch | `259ee06f88141d6640117535b8c0b18868b2b4e37a7a11992b59b69f9116fb7e` |
| `event-status_update.json` | mismatch | `33c123582ead5d3817b9c6ac3a50e430f3a2b4080ce8a11ededb8ddf2b8e8b5e` |
| `event-status_update_event_final.json` | mismatch | `b00383ec81a2ea6dd36020c9e351a15861878fe11d92bcb0d9395450ee5d4d18` |
| `event-task.json` | mismatch | `b72807d24d91d87669d32752047f81135236c6fddccfe303e3b60d7311a54a30` |
| `jsonrpc-task.json` | mismatch | `734f7e865eddfcef505a6a85fc871831e8fb39a1d9e83ca84139acc22c875c8d` |
| `rest-task.json` | mismatch | `734f7e865eddfcef505a6a85fc871831e8fb39a1d9e83ca84139acc22c875c8d` |
| `rpc-CANCEL_TASK_TEST_RESPONSE.json` | mismatch | `cb67bfdf56e80d55fcfd3fc937cc1b9156b3f13c9810b6bc715d529e6fc74779` |
| `rpc-GET_TASK_TEST_RESPONSE.json` | mismatch | `fc9e553fe77c0240df584eecb515cd224e64d0b95ce2cd27f5273c71a796f9fd` |
| `rpc-SEND_MESSAGE_TEST_RESPONSE.json` | mismatch | `f1ad3acaf69d019a4f55adf27854d4d9487a2e903a3d97ba3563c7ad1d6e0dbf` |

The protobuf payload probe checks only the two direct Task payload fixtures.
It uses the exact `JsonFormat.printer().print(proto)` and `toByteArray()`
paths from `WireProbe.java`, comparing `.proto.json` and `.pb` independently:
**4/4 passed**. Both JSON outputs have SHA-256
`c7b19c0bd24f70e1d9f08d53807f3bbc8c24dd7829c7591b280c53db5d661a04`; both
binary outputs have SHA-256
`6359256b0587a59dd8d5075edd68d976b2501689d7cc2b9a0e2fca9ce3d6fe0c`.

Actual endpoint serializer bodies were then checked against the original
frozen runtime using the same production serializer calls and printer options.
The historical source confirms JSON-RPC used
`JSONRPCUtils.toJsonRPCResultResponse` and REST used
`ProtoJsonUtils.toJson(JsonFormat.printer().alwaysPrintFieldsWithNoPresence(),
builder)`. The candidate uses those same paths with explicit selected version
1.0. Candidate and frozen processes used separate classpaths; no classpath was
mixed. **Eight of eight semantic JSON comparisons passed**: four versioned
streaming event outputs and four protobuf HTTP response bodies (REST Task,
JSON-RPC cancel, get, and send-message). The event output files are byte-equal
with these candidate SHA-256 hashes:

| Output | Candidate and frozen SHA-256 |
| --- | --- |
| `event-artifact_update.json` | `17466b2f85688852d09b05b3b271a877797d3c5ab8f94d5545ded4833c0f3652` |
| `event-status_update.json` | `d94c722dd92a1c35c6b998c336de6ed742f48dd569ac63a735aa0a52fd9228b1` |
| `event-status_update_event_final.json` | `8709a45c7f5ddbcf600c8afc2ce6c8164bde7b1f00ceee81854025826ce14d09` |
| `event-task.json` | `f6663298c8047754c2e83bf0111fabc52d834a80bfee465a85c2e8109db4f703` |

The candidate/frozen protobuf response body hashes are `6e9f1ccf7ac2f43be5238ed1ea47de1a2c8ebc52c11d50889990e0a901818eb4` /
`90f2cc8076df936269bc98c443c0cbd8c028b24aeb80e575b973699582f32ea5` for REST
Task, and identical on both sides for cancel (`68a9960a31f5e13a9bcc9c351bea00eeb0d07d65e74788c7f2fba19a32f46097`),
get (`78adda093d0aefcc6193939cc7c09fa0b40b8968267990b015784a37ac352831`),
and send-message (`158d2638ad57373cc62accfb59d7286ee23fb918d04564fa777a710897fd9c86`).
REST's raw hashes differ because the selected-version filter rewrites JSON
whitespace; parsed JSON values are equal. These are serializer-body checks,
not end-to-end HTTP server tests.

The probes were compiled and run directly, without Maven, against candidate
classpath `/tmp/a2a-t7-selected-version.cp` (67 entries; SHA-256
`8c2896be6752c8e7f11d6824928f57c7a116432eeb0436ecf770cbf367e6e595`) and
the separate frozen Phase 0 classpath above. Each `javac` exited 0. The
Gson probe exited 1 (0/9); the protobuf payload, candidate event, frozen
event, candidate HTTP, and frozen HTTP probes each exited 0. Sources and
outputs are under `phase-2/evidence/` and `/tmp/a2a-t7-{gson-probe,
proto-fixture,candidate-event,frozen-event,candidate-http,frozen-http}-*`.

The compile/run commands and exit status were:

```sh
javac -cp "$(cat /tmp/a2a-t7-selected-version.cp)" -d /tmp/a2a-t7-gson-probe-classes .claude/architecture/a2a-1.1/phase-2/evidence/SelectedVersionGsonFixtureProbe.java # 0
java -cp "/tmp/a2a-t7-gson-probe-classes:$(cat /tmp/a2a-t7-selected-version.cp)" SelectedVersionGsonFixtureProbe # 1, 0/9
javac -cp "$(cat /tmp/a2a-t7-selected-version.cp)" -d /tmp/a2a-t7-proto-fixture-classes .claude/architecture/a2a-1.1/phase-2/evidence/SelectedVersionProtoFixtureProbe.java # 0
java -cp "/tmp/a2a-t7-proto-fixture-classes:$(cat /tmp/a2a-t7-selected-version.cp)" SelectedVersionProtoFixtureProbe # 0, 4/4
javac -cp "$(cat /tmp/a2a-t7-selected-version.cp)" -d /tmp/a2a-t7-candidate-http-classes .claude/architecture/a2a-1.1/phase-2/evidence/SelectedVersionProtoHttpProbe.java # 0
java -cp "/tmp/a2a-t7-candidate-http-classes:$(cat /tmp/a2a-t7-selected-version.cp)" SelectedVersionProtoHttpProbe /tmp/a2a-t7-candidate-http-output # 0
javac -cp "$(cat /tmp/a2a-t7-selected-version.cp)" -d /tmp/a2a-t7-candidate-event-classes .claude/architecture/a2a-1.1/phase-2/evidence/SelectedVersionEventHttpProbe.java # 0
java -cp "/tmp/a2a-t7-candidate-event-classes:$(cat /tmp/a2a-t7-selected-version.cp)" SelectedVersionEventHttpProbe /tmp/a2a-t7-candidate-event-output # 0
javac -cp "$(cat /var/folders/6m/rzj3124j3qdd9rstb_f2s9th0000gn/T/a2a-coordinator-baseline-qk04wryd/phase0.cp)" -d /tmp/a2a-t7-frozen-http-classes .claude/architecture/a2a-1.1/phase-2/evidence/FrozenProtoHttpProbe.java # 0
java -cp "/tmp/a2a-t7-frozen-http-classes:$(cat /var/folders/6m/rzj3124j3qdd9rstb_f2s9th0000gn/T/a2a-coordinator-baseline-qk04wryd/phase0.cp)" FrozenProtoHttpProbe /tmp/a2a-t7-frozen-http-output # 0
javac -cp "$(cat /var/folders/6m/rzj3124j3qdd9rstb_f2s9th0000gn/T/a2a-coordinator-baseline-qk04wryd/phase0.cp)" -d /tmp/a2a-t7-frozen-event-classes .claude/architecture/a2a-1.1/phase-2/evidence/FrozenEventHttpProbe.java # 0
java -cp "/tmp/a2a-t7-frozen-event-classes:$(cat /var/folders/6m/rzj3124j3qdd9rstb_f2s9th0000gn/T/a2a-coordinator-baseline-qk04wryd/phase0.cp)" FrozenEventHttpProbe /tmp/a2a-t7-frozen-event-output # 0
python3 .claude/architecture/a2a-1.1/phase-2/evidence/compare-json-outputs.py /tmp/a2a-t7-candidate-event-output /tmp/a2a-t7-frozen-event-output event-artifact_update.json event-status_update.json event-status_update_event_final.json event-task.json # 0
python3 .claude/architecture/a2a-1.1/phase-2/evidence/compare-json-outputs.py /tmp/a2a-t7-candidate-http-output /tmp/a2a-t7-frozen-http-output rest-task.json rpc-CANCEL_TASK_TEST_RESPONSE.json rpc-GET_TASK_TEST_RESPONSE.json rpc-SEND_MESSAGE_TEST_RESPONSE.json # 0
```

### Available full-suite record

The preserved `test.log` has SHA-256
`e31ad06e2049cdb7315c5da47bf3882ed092d0b0a5bea76da5d4a0dccba92e7f`. It
records Maven `BUILD SUCCESS`, 83/83 reactor modules, total time 11:21, Java
17.0.18, and finish time `2026-11-13T17:04:37Z`. Parsing its per-class console
summaries gives 320 class reports, 4,239 tests discovered, 4,116 executed,
123 skipped, zero failures/errors; the Failsafe OpenTelemetry integration
class ran 9/9. Maven Invoker reports successful SDK, extras, and reference BOM
usage tests. However, the log does **not** contain the actual Maven argv or a
candidate source/artifact hash. It finished before current HEAD
`34a120f73e551affca271850aff6d057baf413f6` was committed at 17:13:46Z and
before the current T6c worktree edits. Record this as a prior successful full
suite log, not as a build of the current T7 candidate. Do not rerun the full
build solely to replace this log; the current candidate build gate remains
subject to the separate review disposition.

### T3 selected-version and legacy API/SPI probes

The selected-version T3 selector was rerun after an initial local failure:

```sh
mvn -B -ntp -pl server-common,compat-0.3/server-conversion,transport/jsonrpc,transport/rest,transport/grpc -am -Dtest=VersionedOutputProjectorTest,ProjectingPublisherTest,Phase2ProjectionTest,PushNotificationSenderTest,PushNotificationPayloadFormatter_v0_3_Test,JSONRPCHandlerTest,RestHandlerTest,GrpcHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The first sandbox attempt exited 1 because Mockito/Byte Buddy could not attach
to the JVM for three `PushNotificationSenderTest` cases. The approved bounded
rerun of the same Maven selector exited 0 (`BUILD SUCCESS`, 25.296 s, Java
17.0.18). The target suites actually ran: `PushNotificationSenderTest` 29/29,
`ProjectingPublisherTest` 4/4, `VersionedOutputProjectorTest` 3/3,
`Phase2ProjectionTest` 2/2, `PushNotificationPayloadFormatter_v0_3_Test`
7/7, `JSONRPCHandlerTest` 77 (1 disabled skip), `RestHandlerTest` 95, and
`GrpcHandlerTest` 67. Total: 284 tests, zero failures/errors, one skip. The
push sender assertions compare selected 1.0 outbound bodies to unchanged
historical 1.0 fixtures; the 0.3 formatter suite compares against unchanged
0.3 fixtures. These are targeted T3 checks, not a full 1.0 corpus pass.

The processor classpath command exited 0:

```sh
mvn -B -ntp -f .claude/architecture/a2a-1.1/phase-2/api-probes/pom.xml dependency:build-classpath -Dmdep.outputFile=/tmp/a2a-phase2-probe-runtime.cp -DincludeScope=runtime
```

The T1 probe used separate 192-entry frozen and candidate SDK classpaths; the
frozen jars came from the tracked Phase 1 compatibility archive, while the
candidate classpath replaced the 11 SDK entries with current candidate jars
and retained the same external dependency entries. No frozen/candidate SDK
classpath mixing occurred. Classpath hashes are recorded below.

These temporary copies of the checked-in probe POM isolated source
compatibility outcomes:

```sh
mvn -B -ntp -f /tmp/a2a-t7-api-probes/full/pom.xml -Dphase2.sdk.classpathFile=/tmp/a2a-t7-frozen.cp -Dphase2.processor.classpathFile=/tmp/a2a-phase2-probe-runtime.cp verify
mvn -B -ntp -f /tmp/a2a-t7-api-probes/caller/pom.xml -Dphase2.sdk.classpathFile=/tmp/a2a-t7-candidate.cp -Dphase2.processor.classpathFile=/tmp/a2a-phase2-probe-runtime.cp verify
mvn -B -ntp -f /tmp/a2a-t7-api-probes/switch/pom.xml -Dphase2.sdk.classpathFile=/tmp/a2a-t7-candidate.cp -Dphase2.processor.classpathFile=/tmp/a2a-phase2-probe-runtime.cp verify
```

All three POM-wrapper invocations exited 1 with
`package org.jspecify.annotations does not exist`. The switch-only wrapper also
reported the expected two non-exhaustive switch diagnostics. This was a probe
wrapper failure, not a NullAway or SDK compatibility result.

To distinguish wrapper setup from compatibility results, the same Java 17
preview, Error Prone 2.42.0, and NullAway 0.12.10 compiler/processor arguments
were invoked directly with the isolated classpaths:

- Frozen classpath, `LegacyCaller.java` + `LegacySwitch.java`: exit 0.
- Candidate classpath, `LegacyCaller.java`: exit 0; the caller dereferences
  `MessageSendParams.message()` without a null check.
- Candidate classpath, `LegacySwitch.java`: exit 1 with exactly two
  non-exhaustive-switch diagnostics for the approved new sealed variant.
  This is the D1-approved source-compatibility break, not positive evidence.
- The retained Phase 1 `legacy-api-probes.jar` ran against candidate bytecode
  successfully, as did the candidate-compiled caller. Both linked through the
  legacy constructor/accessors and printed `Task record components=8`.
- `LegacyProviderCompatibilityTest` ran 4/4 in the changed-module regression
  gate. Its result is separate from the ordinary caller and binary probes.

D1's approval is limited to the record changes and the `TaskMessageUpdateEvent`
addition listed in [API migration](api-migration.md). The exhaustive switch
failure is predicted by that record and must not be counted as a compatibility
pass.

## Changed-module and same-instance regression gates

The first sandbox attempt of each Maven command exited 1 in the upstream
`http-client` prerequisite with 51 socket-bind `Operation not permitted`
errors, before the requested target modules ran. The exact Maven arguments
were then rerun with the approved bounded external execution (`timeout 1200`);
these later results are the gate results.

Changed-module selector:

```sh
mvn -B -ntp -pl spec-grpc,client/base,client/transport/jsonrpc,client/transport/rest,client/transport/grpc,transport/jsonrpc,transport/rest,transport/grpc,compat-0.3/server-conversion,extras/opentelemetry/client,extras/opentelemetry/client-propagation -am test
```

Exit 0, `BUILD SUCCESS`, 30 reactor modules, 1:28. Parsed Surefire XML:
190 suites / 2,399 tests / 0 failures / 0 errors / 6 skipped. The six skips
were five Vert.x-specific tests and one disabled JSON-RPC handler test.

Same-instance, authorization and 0.3 selector:

```sh
mvn -B -ntp -pl tests/multiversion/jsonrpc,tests/multiversion/rest,tests/multiversion/grpc -am verify
```

Exit 0, `BUILD SUCCESS`, 44 reactor modules, 7:35. Parsed Surefire XML:
283 suites / 3,928 tests / 0 failures / 0 errors / 122 skipped. The selected
targets actually ran: JSON-RPC multiversion 129 tests, 0 skips; REST
multiversion 138 tests, 18 skips; gRPC multiversion 124 tests, 22 skips.
REST's skips are its nine JSON-RPC-only cases in each version suite; gRPC has
those same 18 plus four disabled HTTP-auth cases (two per version). No Docker
skip was identified in these target reports. Broad reactor suite totals
overlap the changed-module and focused selectors; do not sum them.

## Candidate hashes and retained inputs

Candidate JARs were hashed from the post-gate `target` outputs. The complete
bounded clean install has not run, so these are the verified reactor artifacts
for the current candidate, not a clean-install result.

| Candidate artifact | SHA-256 |
| --- | --- |
| `spec-grpc` | `97f7a4c6475609f268c3e6ae554102c104c5e7ce1999201110c937590005daa1` |
| `client/base` | `ec60b1c9a2038853d67a496dc170952ac28d98e4900980a1b010a75f2a8202b8` |
| `client/transport/spi` | `4fcb6766ec3c538b278382bac3ed50f0a3ffa1cbc5fe59e4f66206855172c23d` |
| `client/transport/jsonrpc` | `7f0927afddb52d1988e97761fcbe7a2cc61cc09318e4f98d6674e75136dbd94e` |
| `client/transport/rest` | `762317544226abdd8f1f5faf98aaccbc920aa7c9bc8c128d89a2d6dd22b4fee0` |
| `client/transport/grpc` | `32f105ed2636f784f218d7bdbc65bf4c4db5201fb3492e3963f65f8f62ebfaf8` |
| `transport/jsonrpc` | `4fb9baaecd3604a626aa80c828b30b21436f38d0501cabe0c83d8647ff4ee79b` |
| `transport/rest` | `d6b6139d7bc210dc7b0a5e84fee6d36d27dac3d9a0fccf57c613ec5a20a5eabd` |
| `transport/grpc` | `166e5f786b934b5b6a6c6b8dea6a7af2f20f4899e9a168f749d3b7ad5d158097` |
| `compat-0.3/server-conversion` | `693b29da20eeab726942202bf08eef39224e31d89a68e467b72c9cc6f5141626` |
| `extras/opentelemetry/client` | `a37f8c2e5f00919b27cbe83c36d28471b9577ab60058f08914ffcbf23b8981a3` |
| `extras/opentelemetry/client-propagation` | `6ccd0f6efaae704459f7c24ad1534918deabea1a382c08644a01935ac78af08c` |

Other retained input hashes:

- Frozen SDK classpath `/tmp/a2a-t7-frozen.cp`:
  `3a700279261a4dce5fc871c80ec09b0e959c6d3e71e63a2f9305de10bbf39498`.
- Candidate SDK classpath `/tmp/a2a-t7-candidate.cp`:
  `380710a991fb0ae92604e9ad05b143f62463a474b86b55beb68e9a5ffb40293d`.
- Probe processor classpath `/tmp/a2a-phase2-probe-runtime.cp`:
  `ceff66d8fa53971d9fcf683862529e5e6274d56f7edb825abf9a46465400a336`.
- Historical 1.0 classpath `/tmp/a2a-phase2-candidate-1.0.cp`:
  `9debf6d1e17fbf90511c63246f9b5b8eaddd647347863a62d66601638b29ae17`.
- Historical 0.3 classpath `/tmp/a2a-phase2-candidate-0.3.cp`:
  `ca607817fce152f3c805d1c46feec3489c56fed13f5bb33883563d059464aa61`.
- Phase 0 fixture checksum manifest:
  `c6bada0c44ffc125f68cffbccdba495d4235e1244afc07e97cd13721f7c52e96`.
- Retained old-compiled API probe JAR:
  `8d03d9ce6004ff9dff7c1ca21657d4523acfaf98f9005aed1650b7d0bb15fe8a`.

## Correction re-review disposition and remaining gates

The separate Sol correction re-review is recorded in
[reviews/t7-review.md](reviews/t7-review.md#correction-re-review--2026-11-16).
It closed the invalid four-mismatch diagnosis. The candidate's 0/9 direct Gson
comparison is retained as canonical model-JSON evidence, not transport
compatibility evidence: T2 requires serialization of the new model fields and
D1 approved their additions. The corrected protobuf payload comparison passes
4/4 and the selected-version endpoint serializer bodies match the isolated
frozen runtime 8/8. These are not full HTTP server tests.

The review verified that public
`SseFormatter.formatResponseAsSSE(response, eventId)` is documented for server
integrations and calls the no-version `JSONRPCUtils` overload. That overload
selects `RESPONSE_PRINTER` with `alwaysPrintFieldsWithNoPresence()` and no
version-path filtering, so zero-valued new fields can appear. The reference
JSON-RPC route instead passes the selected protocol version. The actual
in-repository 1.0 route is not shown to leak fields, but behavior of existing
users of the public no-version helpers is unspecified. The recommended
bounded correction is to preserve historical 1.0 output for old no-version
overloads and require explicit-version overloads for 1.1. The alternative is
explicit owner approval of version-agnostic output plus API documentation that
1.0 callers must pass a version. This requires a separate implementation or
owner-disposition assignment; no production change is authorized by this
evidence correction.

The developer reported a later passing full suite, but no run receipt for that
run is present in the available worktree. The retained `test.log` has SHA-256
`e31ad06e2049cdb7315c5da47bf3882ed092d0b0a5bea76da5d4a0dccba92e7f`; it records
an earlier 83/83-module `BUILD SUCCESS`, 4,239 discovered / 4,116 executed /
123 skipped / zero failures or errors, Java 17.0.18, 11:21 elapsed, ending
`2026-11-13T17:04:37Z`. It predates reviewed HEAD
`34a120f73e551affca271850aff6d057baf413f6` and the T6c worktree edits, and
does not contain Maven argv or candidate source/artifact identity. It cannot
stand in for the reported later run or prove the current candidate build gate.
Candidate reactor JAR hashes listed above are available pre-clean-install
hashes; they do not establish that a clean install ran.

To disposition the reported later run without an automatic duplicate, obtain
its exact Maven argv, exit status and elapsed time, Java version, reviewed
candidate identity including dirty-source hash, reactor inventory,
Surefire/Failsafe discovery and outcomes, zero-test targets, skip identities,
Docker execution, and resulting artifact hashes. If that receipt identifies
this candidate and satisfies the plan, retain it as the bounded build result;
otherwise the current-candidate build gate remains open and must follow the
plan's review-before-build gate. The re-review is complete, but T7 remains
unaccepted pending the helper-contract disposition and qualifying build
evidence. See [next-session.md](next-session.md) for the bounded handoff.

A03/A04/A07/A14 remain only partially evidenced; controlled peers and
serializer-body probes are not full historical peers or endpoint tests, and
Phase 2 does not implement later mutation, replay, or observation guarantees.
Open Phase 0 specification gates remain open. Do not claim final protocol
conformance.

## T7 public-helper compatibility correction — 2026-11-16

### Behavior and caller audit

The two-argument `JSONRPCUtils.toJsonRPCResultResponse(requestId, proto)` now
delegates to the explicit 1.0 encoder. The three-argument overload treats a
null version as 1.0, applies the existing schema-path omission for 0.3/1.0,
and preserves all fields for 1.1. Legacy 0.3/1.0 encoding rejects the new
`TaskMessageUpdateEvent` and `StreamError` message types and active new
`StreamResponse` oneof variants (field numbers 5 and above) with
`UnsupportedOperationException`; they cannot be serialized as a misleading
legacy result. The existing error-response path is unchanged.

The two-argument `SseFormatter.formatResponseAsSSE(response, eventId)` now
delegates to the explicit 1.0 path. Its three-argument overload preserves the
selected version. The framework-agnostic Javadoc example now passes the
handler's selected version. The in-repository JSON-RPC unary route passes
`context.getVersionPolicy().protocolVersion()` to `JSONRPCUtils`; its SSE route
passes the same selected version to `SseFormatter`. No in-repository caller
intending 1.1 relies on either no-version overload.

### Historical serializer comparison

The expected JSON bodies were generated by the isolated frozen Phase 0 runtime
at source revision `2817ed78d02d9a6e609dafdf7f5129484edef017`, using its
`JSONRPCUtils` protobuf response printer. The separately captured frozen
classpath file `/var/folders/6m/rzj3124j3qdd9rstb_f2s9th0000gn/T/a2a-coordinator-baseline-qk04wryd/phase0.cp`
has SHA-256 `b8f66b5e851ec46da6e7de466fad51d76a5b4f1cac48f7420265c5baed762224`.
Candidate and frozen classes were run in separate processes. The frozen
direct-protobuf response body hash is
`755d96820f87282993d59f00107aa4f146a1f96d8cd40471358c3a79b612cce2`; the
frozen `ProtoUtils.ToProto.task` plus JSON-RPC response path used by SSE has
hash `b3b84dca75af14c99168a2247144c65e4633bfc0abddd2b232224c647d8127ae`.
The added legacy-overload regression tests assert those historical response
bodies without reading or changing frozen fixtures.

### Focused verification

Java was Temurin 17.0.18. RED runs established the regression tests against the
pre-correction implementation:

| Command | Exit | Result |
| --- | ---: | --- |
| `mvn -B -ntp -pl spec-grpc -am -Dtest=JSONRPCUtilsTest -Dsurefire.failIfNoSpecifiedTests=false test` | 1 | 61 tests; 2 expected failures: no-version response included `generation: "0"` and `timeline: []`, and legacy serialization accepted a new stream variant. Log: `/private/tmp/t7-helper-jsonrpc-red.log`. |
| `mvn -B -ntp -pl server-common -am -Dtest=SseFormatterTest -Dsurefire.failIfNoSpecifiedTests=false test` | 1 | 3 tests; 2 expected failures: no-version SSE included new fields and accepted a `messageUpdate` payload. Log: `/private/tmp/t7-helper-sse-red.log`. |

Post-correction focused runs passed:

| Command | Exit / elapsed | Target result |
| --- | ---: | --- |
| `mvn -B -ntp -pl spec-grpc -am -Dtest=JSONRPCUtilsTest -Dsurefire.failIfNoSpecifiedTests=false test` | 0 / 6.024 s | `JSONRPCUtilsTest`: 61 run, 0 failures, 0 errors, 0 skipped. Log: `/private/tmp/t7-helper-jsonrpc-final-focused.log`. |
| `mvn -B -ntp -pl server-common -am -Dtest=SseFormatterTest -Dsurefire.failIfNoSpecifiedTests=false test` | 0 / 10.876 s | `SseFormatterTest`: 3 run, 0 failures, 0 errors, 0 skipped. Log: `/private/tmp/t7-helper-sse-final-focused.log`. |

These tests cover frozen 1.0 body parity for both old overloads, omission of
zero-valued generation/timeline/artifact fields for explicit 1.0, retention of
those fields and the new message-update payload for explicit 1.1, rejection of
unsupported legacy stream variants, and unchanged SSE error formatting. The
JSON-RPC suite also retains its error-response round-trip test. Selector counts
are kept separate; reactor prerequisites and class totals are not added.

The unchanged reference route test class was attempted as an additional
versioned-caller check:
`mvn -B -ntp -pl reference/jsonrpc -am -Dtest=A2AServerRoutesTest -Dsurefire.failIfNoSpecifiedTests=false test`
exited 1 after 36 tests all errored during Mockito inline `MockMaker` setup
because Byte Buddy could not self-attach to the JVM; no route assertions ran.
This is not counted as passing evidence or as a production assertion failure.
The route call sites were inspected directly and pass the selected version.
Log: `/private/tmp/t7-helper-routes-green.log`.

At this correction's start, the previously referenced root `test.log` was not
present in the worktree. Its earlier 83-module result, counts and recorded
SHA-256 remain in the prior verification/review text; no replacement was
created during focused testing. Focused Sol review has now accepted this
correction; the final clean-install log will be saved as `test.log`.

Previously accepted T7 evidence remains unchanged: 0.3 fixture results,
protobuf payload 4/4, selected-version endpoint serializer parity 8/8, public
SDK/raw payload tests, API/SPI probes, and the changed-module/same-instance
regressions. This helper correction changes the public serializer boundary.
After the final clean install, refresh the helper-level historical comparisons
and the three selected-1.0 JSON-RPC response-body comparisons (cancel/get/send)
that pass through the changed three-argument method. Keep the REST Task and
four event comparisons, 0.3 fixture run, protobuf payload 4/4, and all other
accepted evidence tied to unchanged paths. Do not recapture fixtures or repeat
unaffected evidence layers. The focused independent review is complete; the
final Java 17 `mvn -B -ntp clean install` was pending when this checkpoint was
written. The final build and subsequent T7 closure are recorded below.

## Focused Sol review of the helper correction — 2026-11-16

The narrow review in
[reviews/t7-helper-correction-review.md](reviews/t7-helper-correction-review.md)
accepted the public-helper correction with no blocking production or regression
test finding. The reviewer ran no tests or builds. At that review checkpoint,
T7 remained open until the final Java 17 clean install and affected comparison
refresh, which are recorded below.

After that build, refresh only the three selected-1.0 JSON-RPC cancel/get/send
response bodies that use the changed three-argument serializer. The prior REST
Task and four event comparisons use unchanged serializer paths. The unchanged
reference-route selector's 36 Mockito/Byte Buddy setup errors are not positive
evidence; all assertions were prevented from running.

## Final candidate build and affected comparison refresh — 2026-11-16

The focused review accepted the compatibility correction, and the single
final candidate build and affected comparisons have now passed. This closes
T7's compatibility/evidence gate. It does not establish Phase 2 completion or
close the recorded Phase 0 specification gates.

### Candidate identity and full build

Candidate: branch `support/a2a-1.1.0`, HEAD
`34a120f73e551affca271850aff6d057baf413f6`, plus the eleven changed Java
source/test files in
[the source manifest](evidence/final-build-candidate-source-manifest.tsv).
The manifest SHA-256 is
`7b6bdb52d78ee91e73ffa83eb2761a8db58349cd86332abb862054c98daa946d`;
all eleven file hashes were rechecked after the build. No source changes were
made during the build or comparison refresh.

The exact Maven command was `mvn -B -ntp clean install`, on Temurin OpenJDK
17.0.18+8. It started at `2026-11-16T17:56:47.446578Z`, ended at
`2026-11-16T18:07:57.829828Z`, elapsed 670.382 seconds, and exited 0. Maven
reported `BUILD SUCCESS` and 11:08 total time. The root log is [test.log](../../../../test.log),
SHA-256 `0224e4af9528f1932dfecc432535ff8432d72a0fbdcad68ff0eb54611552a3db`.
All 83 reactor modules succeeded; none failed, skipped or were canceled.

Fresh report inventory after `clean install`:

- Surefire: 322 XML class reports; 4,252 discovered, 4,129 executed, 123
  skipped, zero failures/errors.
- Failsafe: 2 XML class reports; 9 discovered and executed, zero skips,
  failures or errors.
- Combined Surefire/Failsafe: 4,261 discovered, 4,138 executed, 123 skipped,
  zero failures/errors. These totals are derived from disjoint Surefire and
  Failsafe reports and do not add selector totals.
- The changed helper targets actually ran in the full build:
  `JSONRPCUtilsTest` 61/61 and `SseFormatterTest` 3/3. Both had zero
  failures/errors/skips.
- Twelve modules reported `No tests to run`: `boms/test-utils`,
  `examples/helloworld/client`, `examples/stream-lifecycle/client`,
  `extras/common`, `test-utils-docker`,
  `extras/queue-manager-replicated/tests-multi-instance/quarkus-common`,
  `extras/queue-manager-replicated/tests-multi-instance/quarkus-app-1`,
  `extras/queue-manager-replicated/tests-multi-instance/quarkus-app-2`,
  `extras/multitenancy`, `compat-0.3/tests/server-common`,
  `reference/multiversion-jsonrpc`, and
  `extras/multitenancy/tests/test-common`.
- All 123 skip identities are listed in
  [the skip inventory](evidence/final-build-skips-2026-11-16.md). They are
  not positive passes. They include existing protocol/transport exclusions,
  two `@Disabled` auth cases, known Vert.x SSE limitations, and the push
  notification token-auth TODO.
- Docker-backed checks did execute: MySQL authorization 6/6, PostgreSQL
  authorization 5/5, Kafka replication integration 4/4, and multi-instance
  replication 2/2, all with zero failures/errors/skips. The command did not
  set `skipDockerTests`; `test-utils-docker` itself is a helper module and
  correctly had no test classes. The report-backed test identities and
  results are in the full log and Surefire reports.
- Candidate package hashes after the clean build: `spec-grpc` JAR
  `7a1d6809ded2ed8e3b289cbe45893efcdb59d86956e30f9bd58d911d1ad2cea9`;
  `server-common` JAR
  `dc4d56c9055f1aa6565c6f406942bb141c3a7c83bd54a1afc8692dbea64ce35f`;
  `server-common` tests JAR
  `2d101071a93ce454e5a9033d0097cb51bd7953294ed2495488d9d4049b8bc441`.

### Historical response comparison refresh

The selected-1.0 probe source is unchanged. An initial compile attempt used the
`spec-grpc` Surefire classpath (36 entries; SHA-256
`9debf6d1e17fbf90511c63246f9b5b8eaddd647347863a62d66601638b29ae17`); `javac`
exited 1 because that classpath omitted `server.version` classes. No probe ran
and no comparison was made from that attempt. The corrected classpath came
from `server-common/target/surefire-reports/TEST-org.a2aproject.sdk.server.tasks.PushNotificationUrlValidatorTest.xml`,
with 67 entries and SHA-256
`02a18d25e5b4ce52596c49333f9ccc32d8cbead860946939086b92cb6827e0e9`.
Candidate and frozen runtime classpaths remained isolated.

Commands and results using the corrected classpath:

```sh
candidate_cp=$(cat /private/tmp/a2a-t7-postbuild-candidate.cp)
javac -cp "$candidate_cp" -d /private/tmp/a2a-t7-postbuild-http-classes .claude/architecture/a2a-1.1/phase-2/evidence/SelectedVersionProtoHttpProbe.java # 0
java -cp "/private/tmp/a2a-t7-postbuild-http-classes:$candidate_cp" SelectedVersionProtoHttpProbe /private/tmp/a2a-t7-postbuild-http-output # 0
python3 .claude/architecture/a2a-1.1/phase-2/evidence/compare-json-outputs.py /private/tmp/a2a-t7-postbuild-http-output /private/tmp/a2a-t7-frozen-http-output rpc-CANCEL_TASK_TEST_RESPONSE.json rpc-GET_TASK_TEST_RESPONSE.json rpc-SEND_MESSAGE_TEST_RESPONSE.json # 0, 3/3
```

All three selected-1.0 JSON-RPC response bodies match the isolated historical
Phase 0 serializer output. Candidate hashes equal the frozen hashes:

| Output | Candidate and frozen SHA-256 | Result |
| --- | --- | --- |
| `rpc-CANCEL_TASK_TEST_RESPONSE.json` | `68a9960a31f5e13a9bcc9c351bea00eeb0d07d65e74788c7f2fba19a32f46097` | match |
| `rpc-GET_TASK_TEST_RESPONSE.json` | `78adda093d0aefcc6193939cc7c09fa0b40b8968267990b015784a37ac352831` | match |
| `rpc-SEND_MESSAGE_TEST_RESPONSE.json` | `158d2638ad57373cc62accfb59d7286ee23fb918d04564fa777a710897fd9c86` | match |

The unchanged REST Task and four event comparisons are reused from the prior
accepted 8/8 frozen-runtime serializer comparison. The 0.3 fixtures, protobuf
payload 4/4, the original historical cancellation failure fingerprint, and
all public SDK/raw-payload/API/SPI/regression layers remain distinct and
unchanged. No frozen fixture or expected file was regenerated. The earlier
default 1.0 harness failure and the invalid Gson/protobuf diagnostic remain
preserved as historical evidence; neither is counted as a positive pass.

The review report was not edited. The focused correction disposition remains
in [t7-helper-correction-review.md](reviews/t7-helper-correction-review.md);
the prior broad T7 review remains at [t7-review.md](reviews/t7-review.md).
T7 is accepted for its compatibility/evidence scope. Phase 2 completion and
final protocol conformance are not claimed; the open Phase 0 specification
gates remain visible.
