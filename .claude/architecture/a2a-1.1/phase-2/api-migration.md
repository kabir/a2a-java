# Phase 2 common API migration decision

Status: D1 was explicitly approved by the user on 2026-11-11. This approval is
limited to the listed record structure changes and `TaskMessageUpdateEvent` in
`UpdateEvent` and `StreamingEventKind`. Preserve every legacy constructor and
non-null accessor contract. It authorizes no other API or SPI break.

## Compatibility inventory

The baseline descriptors below are from the frozen Phase 1 SDK jars, whose
SHA-256 values are listed in `phase-1/compatibility/legacy-compile-manifest.json`.
The old record component counts and the planned additions are:

| Type | Old components | Phase 2 additions | Compatibility effect |
| --- | --- | --- | --- |
| `Task` | 6 | `long generation`, nullable timeline | Keep six-argument constructor; record shape/equality/hash/toString change. |
| `Artifact` | 6 | `long startGeneration`, `long endGeneration`, nullable role | Keep six-argument constructor; record shape/equality/hash/toString change. |
| `TaskStatusUpdateEvent` | 4 | `long generation` | Keep four-argument constructor; new event state participates in record methods. |
| `TaskArtifactUpdateEvent` | 6 | `long generation` | Keep six-argument constructor; new event state participates in record methods. |
| `MessageSendConfiguration` | 4 | nullable `ifGenerationMatch`, nullable `timelineLength` | Keep four-argument constructor and existing builder defaults. |
| `TaskQueryParams` | 3 | nullable `currentGeneration`, nullable `timelineLength` | Keep three-argument constructor. |
| `ListTasksParams` | 8 | nullable `timelineLength` | Keep eight-argument constructor. |
| `AgentCapabilities` | 4 | `boolean userArtifacts`, default false | Keep four-argument constructor. |
| `TaskPushNotificationConfig` | 6 | nullable `protocolVersion` | Keep six-argument constructor. |
| `MessageSendParams` | 4 | none | Keep unchanged; `message()` remains non-null. |

Record component additions change reflective component counts, canonical
constructor descriptors, equality, hash code, string form, and record-pattern
matching even when overloads preserve ordinary constructor linking. Those changes
are accepted only for the exact additions in the table. The old constructor and
accessor descriptors must be checked against the candidate before T7 closes.

`TaskMessageUpdateEvent` is the sole newly approved subtype. Adding it to both
sealed families can make previously exhaustive source switches fail compilation;
an already-compiled exhaustive pattern switch can fail when handed the new kind.
Migrate SDK consumers to include a default/unknown branch where they handle
`TaskUpdateEvent.getUpdateEvent()`. The standalone Java 17 preview switch probe
records source compatibility; Java 21 is unavailable in this environment, so no
Java 21 record-pattern result is claimed.

Artifact-only requests use `SendInputParams`; do not make
`MessageSendParams.message()` nullable. New request/query/observation fields are
additive and must be retained through copy-builders and versioned codecs. SPI
extensions remain default/additive. No SPI conflict or additional break has been
approved; stop and checkpoint for Sol if one is demonstrated.

## T5 additive transport companions

`VersionedClientTransport` gains default, unsupported methods for input send/stream,
observation, asynchronous get, and evidence-preserving synchronous get/list. The
existing `ClientTransport` and `AbstractClient` descriptors remain unchanged.
Unary task/message results use `TransportResult<T>` with immutable per-task
evidence; task lists require one matching evidence item per returned task, while
a direct `Message` carries none. The base `Client.getTask` and `listTasks`
methods use decoded companions only for selected 1.1 transports that advertise
`UNKNOWN_VARIANT_SKIP`, then unwrap at their existing public return boundary.
Those public signatures do not promise generation synchronization. Explicit 1.0
continues to use its ordinary legacy read methods.

The SPI feature set includes `GUARDED_INPUT`, `ARTIFACT_INPUT`, and
`UNKNOWN_VARIANT_SKIP`. JSON-RPC selected 1.1 advertises these features alongside
`ORDINARY` from both its provider and transport because its public unary and
streaming input paths preserve guards and artifact updates (subject to the
agent-card `userArtifacts` gate), decoded unary reads preserve per-task skipped
timeline notes, and SSE frames preserve skipped variant/timeline notes. Its 1.0
profile advertises `ORDINARY` only. The base public
`Client.getTask`/`listTasks` methods select the evidence-preserving companion for
1.1 and unwrap known Task content at their existing return boundary; callers
that need skipped-generation evidence use the wrapped SPI envelope. Other
bindings advertise only features they have completed. No cursor, long-poll,
delivery-version, observation-handle, or live capability is advertised.
`TransportObservation` uses local opening deadlines and frame/byte bounds, not
wire timeouts. Existing providers inherit defaults that fail before I/O, so the
additive SPI does not force old implementations to add methods.

## Probe evidence

`api-probes/` compiles ordinary caller and exhaustive sealed-switch examples
against the frozen old jars and later against candidate classpaths. The ordinary
caller is `@NullMarked` and dereferences `params.message().parts()` without a
null check. Keep old compiled class bytes for candidate linkage execution. The probe POM reads the isolated SDK source classpath from
phase2.sdk.classpathFile and a separate checker processor classpath from
phase2.processor.classpathFile. Generate the processor classpath with Maven
dependency plugin includeScope=runtime: NullAway 0.12.10 needs its runtime
dataflow-nullaway dependency when javac loads the checker. The POM uses the
active Java 17 profile (Error Prone 2.42.0 and NullAway 0.12.10), omitting the
src/test exclusion for these main-source probe files. See
[T1 probe evidence](evidence/t1-probe-results.md).

Results and class hashes belong under `phase-2/evidence/`. Old and candidate
classpaths are independent; do not overwrite frozen Phase 1 inputs or historical
wire expectations.
