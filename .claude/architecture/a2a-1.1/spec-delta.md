# Server-relevant changes from A2A 1.0.0 to 1.1 RC

This inventory accompanies the [server compatibility assessment](compatibility.md).
Revisions and source links are recorded in the [research index](README.md).

## Size and wire structure

| File | Added lines | Removed lines |
| --- | ---: | ---: |
| `docs/specification.md` | 490 | 159 |
| `specification/a2a.proto` | 234 | 29 |

The protobuf adds one RPC, four messages, and 18 fields on existing messages.
Existing field numbers and types are preserved. Comments and example corrections
account for part of the textual diff. The service remains
`lf.a2a.v1.A2AService`; there is no new 1.1 protobuf service namespace.

## New state and operations

| Area | Schema additions | Server consequence |
| --- | --- | --- |
| Live interaction | `SendLiveMessage`, `StreamRequest`, `StreamError` | A task-bound exchange accepts further input while delivering updates. gRPC is bidirectional; HTTP uses a downlink and additional uplink calls. A rejected input can leave the channel open. |
| Mutation ordering | `Task.generation`; generation on status/artifact events | Assign one authoritative sequence across client messages, status updates, and artifact changes, before delivery. |
| Concurrency | `SendMessageConfiguration.ifGenerationMatch`; `TaskGenerationMismatchError` | Atomically compare generation and accept input or reject without applying it. |
| Long-polling | `GetTaskRequest.currentGeneration` | Return immediately if newer; otherwise the server should wait, with timeout fallback. |
| Interaction record | `Task.timeline`, `TimelineEntry`, `timelineLength` on send/get/list | Maintain ordered client-message and agent-status entries while preserving legacy history. |
| Client-message delivery | `TaskMessageUpdateEvent`, `StreamResponse.messageUpdate` | Deliver only to subscribers requesting 1.1 or later. |
| Resume | `SubscribeToTaskRequest.lastSeenGeneration` | Introduces a replay cursor; live-operation prose specifies replay or snapshot fallback. |
| Client artifacts | `SendMessageRequest.artifactUpdates`, `AgentCapabilities.userArtifacts`, `Artifact.role` | Capability-gated input artifacts. Existing-task updates may omit `message`; new-task creation still requires one. |
| Artifact placement | `Artifact.startGeneration`, `Artifact.endGeneration` | Relate artifact production to the timeline without storing every artifact chunk as a timeline entry. |
| Webhooks | `TaskPushNotificationConfig.protocolVersion` | Store the delivery version explicitly; use the creating request version by default, with 1.0 fallback when unavailable. |

`history` and `historyLength` remain in 1.x and are deprecated in favour of
`timeline` and `timelineLength`. New timeline data cannot simply replace the
history needed by old interfaces. A timeline is also not a complete artifact
event log: it is insufficient by itself to reproduce every original chunk.

## Changes inherited from the 1.0.1 tag

The following mappings are already present in 1.0.1 and remain in 1.1:

| Error | 1.0.0 HTTP | 1.1 HTTP | gRPC change |
| --- | --- | --- | --- |
| `TaskNotCancelableError` | 409 | 400 | None |
| `ContentTypeNotSupportedError` | 415 | 400 | None |
| `InvalidAgentResponseError` | 502 | 500 | None |
| `PushNotificationNotSupportedError` | 400 | 400 | `UNIMPLEMENTED` to `FAILED_PRECONDITION` |
| `UnsupportedOperationError` | 400 | 400 | `UNIMPLEMENTED` to `FAILED_PRECONDITION` |
| `VersionNotSupportedError` | 400 | 400 | `UNIMPLEMENTED` to `FAILED_PRECONDITION` |

Existing JSON-RPC numbers are unchanged. The new generation-mismatch error is
`-32010` / gRPC `ABORTED` / HTTP 409.

Error-detail guidance is also revised: detail entries have typed `@type` values;
JSON-RPC no longer requires an `ErrorInfo` entry for every A2A error. HTTP still
requires `ErrorInfo` to distinguish errors sharing a status. The HTTP prose
retains an outer `error` wrapper and describes its code as the HTTP status;
do not infer a different JSON envelope merely from the reference to
`google.rpc.Status`.

Other inherited corrections include recommending `application/a2a+json` for
HTTP+JSON, clarifying tenant propagation, and fixing push-configuration examples.
These are separate from the new 1.1 interaction model.

## Other behavioral and documentation changes

- Interrupted states remain non-terminal; they should not by themselves close
  streaming responses. The current SDK already implements much of this behavior.
- Terminal tasks must not accept new messages or emit further updates. Existing
  replay/replication behavior needs distinguishing from a genuinely new mutation.
- Unknown stream response payloads and timeline entry kinds must be skipped by
  clients. Unknown live input must be rejected without killing the exchange.
- The REST subscription text changes POST to GET. This corrects an existing
  discrepancy: the 1.0.0 protobuf already specifies GET.
- Agent Cards acquire the distinct `application/a2a-agent-card+json` media type.
- The gRPC interface address is clarified as `hostname:port`.
- In-task authorization scope is clarified; an `AUTH_REQUIRED` transition alone
  grants no authorization for an operation or later messages.
- Custom bindings use URI identifiers; example and Agent Card discovery wording
  is corrected.

The additive protobuf structure supports sharing an implementation, but does not
make the two server interfaces behaviorally interchangeable. See the
[required version boundaries](compatibility.md#required-version-boundaries).
