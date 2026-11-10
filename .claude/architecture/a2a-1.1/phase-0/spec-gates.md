# Phase 0 fresh upstream specification research

Fresh metadata recheck: **2026-11-10T13:30:15Z through 2026-11-10T13:30:16Z**. Source retrieval/analysis began approximately 13:18 UTC. Read-only research; no repository edits, builds, commits or GitHub mutations.

## Pins and status

- Target: **experimental A2A 1.1 RC**, tag `v1.1.0-rc`, commit **78e1d6a1f7f118c8e9bc830b04a8579c1119cb78**. The complete tags API result contains no final 1.1 tag. Releases API contains no 1.1 release; latest listed final release is v1.0.1. A final-spec reconciliation gate remains mandatory.
- Authoritative objects: [`specification/a2a.proto`](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/specification/a2a.proto). Protocol requirements: [`docs/specification.md`](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/docs/specification.md). The prose at L107 declares proto authoritative for objects/messages, although that sentence uses an obsolete `spec/` path; actual tagged path is `specification/`.
- [PR #2411](https://github.com/a2aproject/A2A/pull/2411) is **OPEN**, unmerged; base = target RC commit; head = **d318e2e074bcc967ce5f09b746304a72a9794f03**, unchanged from the previous research pin. API updatedAt is 2026-11-10T11:30:02Z. It is evidence of proposed clarification, **not the selected specification**.
- Full upstream files, metadata and current diff are retained in `evidence/upstream-sources.tar.gz`. [Exact commands](evidence/upstream-commands.md) and [SHA-256 fingerprints](evidence/upstream-SHA256SUMS) accompany the archive.

## Six gates

### 1. Final target — RC pinned; final reconciliation remains

No final 1.1 is published in tags/releases API evidence. Work can be scoped to RC/experimental but cannot claim final conformance. Explicit protocol header/interface version is `1.1`, not the tag spelling `v1.1.0-rc` (RC prose L864–899 defines Major.Minor protocol matching).

### 2. Discovery — unresolved normative wording, conservative policy identified

RC proto [L442–462](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/specification/a2a.proto#L442-L462) defines one `(url, protocol_binding, tenant, protocol_version)` interface and says to use the latest supported minor per major. RC prose [L893–903](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/docs/specification.md#L893-L903) requires exact requested Major.Minor semantics, rejects unsupported versions, and explicitly allows multiple same-transport interfaces with different versions, on same/different URLs.

Exact explicit 1.0 and 1.1 entries is the architecture's conservative SDK policy: select only the requested version, do not infer 1.0 from an advertised 1.1 entry, and do not auto-fallback. It is supported by the explicit-multiple-interface prose and exact-version processing requirement. The latest-minor proto comment has not been changed by #2411; fresh upstream evidence does **not** establish that latest-minor advertisement implicitly denotes every older minor. Do not describe the wording conflict itself as settled. A maintainer interpretation or revised target text is needed to close this gate if implementation requires strict normative discovery conformance.

### 3. Cursor — ordinary conflict remains; live baseline and terminal behavior separated

- Ordinary `SubscribeToTask`: RC prose [L297–313](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/docs/specification.md#L297-L313) unconditionally requires a first `Task`. The *same operation request type* proto [L906–916](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/specification/a2a.proto#L906-L916) says a set cursor replays only following events and does not open with a Task. These requirements cannot both hold for a retained cursor. Preserve this as a gate rather than silently picking one.
- Live opening `subscribe`: RC prose [L463–470](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/docs/specification.md#L463-L470) explicitly says retained cursor => no opening snapshot, replay strictly after cursor; absent cursor => initial snapshot; unable to replay => snapshot fallback instead of failure. Read the fallback as the explicit exception to the no-snapshot rule. RC does not establish a blanket hint policy permitting arbitrary snapshot-only behavior despite retained replay capability.
- Ordinary terminal task subscription is definitively rejected: `UnsupportedOperationError`, proto [L85–86](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/specification/a2a.proto#L85-L86), prose L307–311 and binding reiterations L2825/L3009. A cursor does not create a terminal-replay exception. An attached stream closes when terminal is reached. Recover a terminal snapshot with GetTask.
- Live `subscribe` uses the ordinary `SubscribeToTaskRequest` and links to that operation; applying ordinary terminal rejection is the strongest reading. The live subsection does not separately state terminal-reconnect/replay rules. Do not promise live replay of already-terminal tasks as an established exception; retain the live-terminal clarification with the cursor gate if exact behavior is required.
- #2411 proposed spec L313–315 and L467–469 replaces both cursor paths with an explicit hint: either replay or snapshot, including snapshot-only stores. It leaves ordinary terminal rejection unchanged. This proposed resolution is not in the target RC.

### 4. Bootstrap — increments are clear; generation-1 interaction trace missing

RC prose [L576–587](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/docs/specification.md#L576-L587) and proto L214–222 say creation initializes 1; each timeline append/status update/artifact mutation increments exactly once; each mutation maps to one event; two events must not share a task generation. Timeline entry generation is post-append (prose L621–636, proto L369–375). Message then artifact-list ordering is explicit (L498). Live opening snapshot already includes opening client message (L464).

No inspected pinned definition/example supplies the generation-1 creation trace, whether the initial client entry is part of generation-1 creation or a separate append at 2, or whether the initial required Task.status is merely creation baseline or a distinct status timeline/event mutation. The proposed ADR-002 also supplies no concrete bootstrap trace and has older conceptual text not matching all final schema decisions. #2411 does not change bootstrap.

The original research suggested creation at 1 followed by initial message at 2. That alternative remains evidence of ambiguity, but it is **not the selected SDK policy**: architecture L704–709 already recommends initial message at generation 1. The concrete selected trace is initial message/creation at 1, opening artifacts at 2 onward, then any separately emitted status at the next generation; required baseline status alone is not a separate event. Opening snapshot is not another mutation. See the [selected trace and normative residual](selected-policies.md#g4-bootstrap-explicit-recommended-trace). This is an experimental SDK policy, not a claim that upstream bootstrap wording is unambiguous.

### 5. HTTP live exchange — unresolved, including proposed PR

RC prose [L453–481](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/docs/specification.md#L453-L481) requires an invoking downlink plus short subsequent calls; every live stream is task-bound; separate calls return current-Task acknowledgement without replay; single-request rejection goes on downlink. RC proto [L44–54](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/specification/a2a.proto#L44-L54) maps POST `/message:live`; [L788–800](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/specification/a2a.proto#L788-L800) has only tenant and input payload. It contains **no exchange identifier or binding association rule**.

The complete JSON-RPC binding section (L2445–2720) and REST section (L2957–3225) do not define live opening versus subsequent-call identification or how a separate call selects its downlink. JSON-RPC request `id` correlates that call's own response, while SSE examples use the opening call's id; it does not identify another call's live exchange. HTTP connection identity is not a specified selector and does not survive HTTP multiplexing/reconnect as a portable rule.

#2411 proposed L482–484 strengthens rejection locality: no state/timeline/generation changes or broadcast, only the submitting caller; it says binding defines how. The diff changes **no binding section**, adds no selector, and does not establish whether a StreamError travels in the short call's response or an existing downlink. It also leaves acknowledgement encoding/media type/status and exact success-versus-downlink-rejection association incomplete for HTTP.

See [minimal HTTP trace](http-live-ambiguity.md) for the minimal counterexample. Do not add a session field/header, status RPC, operation receipt, sticky-connection rule, or new negotiated extension to manufacture a resolution. This blocks dependent HTTP live public API finalization and binding implementation.

### 6. Artifact identity/origin — key pinned, cross-origin rules incomplete in RC

RC proto [L325–348](https://github.com/a2aproject/A2A/blob/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78/specification/a2a.proto#L325-L348) says artifact ID unique within a task; role is optional originator, agent by default, client USER. Append targets same ID (L416–420; prose L499). Thus **per-task key is artifactId alone**. A composite `(artifactId, role)` key contradicts ID uniqueness and must not be inferred from an issue/PR summary.

RC describes client/agent origin roles but does not define full normalization of unspecified/spoofed role, immutability of original creator role under cross-party replacement, or whether cross-origin collision is rejected/permitted. Do not invent blanket rejection or an independent artifact namespace per role.

#2411 **diff/pinned files**, proposed spec [L500](https://github.com/a2aproject/A2A/blob/d318e2e074bcc967ce5f09b746304a72a9794f03/docs/specification.md#L500) and proto L328–352, expressly use artifactId alone; role means original creator and must be populated USER/AGENT; consumers treat UNSPECIFIED as AGENT; agent decides collaborative editing across origins, otherwise rejects InvalidParamsError or live StreamError. The original creator role is preserved rather than becoming last writer's role. The PR's API body still falsely describes composite identity and rejection of client AGENT role: **use source diff, not that stale body**. These improvements remain a proposal, and do not close the selected RC's origin/collision gate.

## Correlated outcomes by binding and input shape

Here COMMITTED means evidence of task acceptance, **not executor delivery or business completion**. A transport error/deadline after dispatch is not definitive rejection; a higher generation is not a receipt for a particular input; history/timeline may be limited. All binding success evidence below is an inference from the operation's specified successful response, not a new receipt object.

### Ordinary unary and ordinary server-streaming

| Binding | Positive evidence | Definitive request rejection evidence | Limits by input shape |
| --- | --- | --- | --- |
| JSON-RPC SendMessage | Successful same-request-id response with Task (prose L2497–2529; proto L940–949). | Same-request-id definitive validation/precondition rejection, e.g. -32010 generation mismatch; arbitrary internal/transport failure alone can be indeterminate. | Message-only successful Task is task-bearing acceptance. Message+artifacts and artifact-only permitted by L495–499; successful Task result is operation-level evidence, not individual artifact-event receipts or a guaranteed executor-delivery acknowledgement. New task direct Message commits no task inputs. |
| REST SendMessage | Successful response to that HTTP call containing Task, proto L21–30 and L940–949; latest Task result rules L507–517. | Definitive validation/precondition HTTP rejection with required A2A ErrorInfo (L3117ff), mismatch 409 (L3167–3195); arbitrary server errors may be indeterminate. | Same shape limits as JSON-RPC. |
| gRPC SendMessage | Successful unary call SendMessageResponse.task (proto L21, L940–949). | Definitive validation/precondition gRPC status + typed error details (L2891ff), mismatch ABORTED; arbitrary execution/transport errors may be indeterminate. | Same shape limits as JSON-RPC. |
| JSON-RPC / REST SendStreamingMessage | Opening Task on the response stream associated with that one call; task lifecycle begins Task (L206–210), JSON-RPC SSE carries call id (L2531–2546). | Binding error before valid stream/task; call-specific malformed/precondition failure. | Opening Task demonstrates task-bearing result for the request. RC does not explicitly say opening ordinary Task reflects all artifact inputs as #2411 says for live. Do not treat later unrelated Task/event as exact batch receipt. A single direct Message then close creates no task. |
| gRPC SendStreamingMessage | Opening Task on this server-streaming RPC. | That RPC's typed status/error. | Same ordinary stream limits. |

For a received success Task, the client can report a successful operation/task acceptance consistent with the operation contract. The Task snapshot's contents/generation alone do not let a later independent GetTask reconstruct the exact outcome of a previously lost request. Direct-Message behavior for *new task* is explicit no-persistence (L498). No statement here asserts a universal atomic-batch guarantee or solves uncertain existing-task direct-Message semantics by inventing receipts; the architecture's acceptance transaction is a separate SDK policy.

### Live opening and subsequent inputs

| Binding/input | Positive evidence available in RC | Rejection/correlation | Remaining gate |
| --- | --- | --- | --- |
| gRPC opening message-only | Initial Task on same duplex call already reflects opening message, L464. | StreamError on same call with referenceId matching messageId; terminal transport errors are distinct. | Bootstrap trace and optional missing referenceId; opening readiness is separate from later input acceptance. |
| gRPC subsequent message-only | TaskMessageUpdateEvent containing exact Message.messageId and authoritative generation (proto L379–392; prose L476, L630). Client should ensure unique IDs for its outstanding messages; no mandatory idempotency assumed. | StreamError.referenceId may name messageId on same duplex channel (proto L977–992), but is optional; absent ref with several pending inputs cannot be mapped definitively. | No universal positive receipt if message event is lost/skipped or stream closes before it. GetTask showing same unique entry can be evidence only while retained/unambiguous. No-event does not mean rejected. |
| gRPC opening message+artifacts | Initial Task explicitly reflects message in RC. Input ordering is message then artifacts (L498). | StreamError may identify message or one artifact, not a batch token. | RC does not explicitly require opening live snapshot to reflect the whole applied request; #2411 L466 adds that. One message event does not prove all artifact updates committed. |
| gRPC subsequent message+artifacts | Message event plus artifact updates could show each mutation when distinguishable. | Optional referenceId has message/artifact ID only. | No common request/batch ID, no specified all-or-none batch receipt; same-ID artifact updates from other writers may be observationally identical. Message event alone cannot establish whole-batch acceptance. |
| gRPC artifact-only, opening or subsequent | TaskArtifactUpdateEvent has artifactId, generation, append/lastChunk; current Task contains projection. | StreamError may name artifactId. | Repeated chunks/edits use same artifactId; no per-update ID. A concurrent writer can emit indistinguishable same-ID change. Artifact-only opening snapshot wording is incomplete in RC. Serializing this client's pending inputs does not eliminate other writers. Cannot promise universal exact per-input COMMITTED/REJECTED correlation. |
| REST / JSON-RPC live opening, all permitted shapes | Invoking call is downlink; RC opening message Task evidence as above. | Expected local StreamError is described on downlink. | Wire does not define how to distinguish a new downlink from an uplink for same task. Artifact opening evidence and bootstrap limits remain. |
| REST / JSON-RPC live subsequent message-only | Short-call current-Task acknowledgement is explicitly required (L475), and exact message-update event can identify accepted message. | Error supposed to reach downlink and reference message; JSON-RPC short-call id only correlates its own response. | Which downlink? Does successful short acknowledgement necessarily preclude later local rejection? No specified association/success contract. A current Task alone is not per-message provenance. |
| REST / JSON-RPC live subsequent message+artifacts / artifact-only | Same short-call acknowledgement plus observable task changes. | Same association gap plus optional artifact/message referenceId. | HTTP exchange gap **and** batch/artifact per-update correlation gap. |

`StreamError.referenceId` is optional, not a mandatory input token. ArtifactId identifies the artifact, not an individual append/update. A local SDK token cannot turn either into an on-wire receipt. Unknown StreamRequest payloads may have no usable message/artifact reference at all. Missing normative correlation must stay a gate, with finite pending-input deadlines yielding INDETERMINATE when appropriate. No input status RPC or executor-delivery acknowledgement exists in these definitions.

## Effect on implementation authorization

This fresh inspection pins experimental target and artifact key; confirms ordinary terminal rejection and the retained-cursor live baseline. It **does not close all Phase 0 gates**. Unresolved discovery wording, ordinary cursor conflict/live terminal detail, bootstrap trace, HTTP association, RC artifact origin/collision rules, and live artifact/batch receipt limitations require a maintainer decision or newer normative text before dependent public contracts are finalized. Independent baseline/inventory work can continue without inventing those rules.

## Dependency map and precise decision requests

| Gate/residual | Acceptance / phase | Server dependency | Client/public API dependency |
| --- | --- | --- | --- |
| Final reconciliation | A14; Phase 0 and final Phase 6 | Final 1.1 advertisement/conformance claim | Final support claim/docs; ordinary experimental work can proceed |
| Discovery | A01/A02/A03; Phase 1 | Effective version registrations/cards/dispatch | ClientBuilder/ClientConfig exact selection policy, provider descriptor |
| Ordinary cursor conflict, live-terminal interpretation | A09/A11; Phases 4/5 | Replay-vs-snapshot and terminal rejection | TaskObservation startup/readiness, cursor partial-view semantics; LiveSession resume |
| Bootstrap | A05/A06/A08/A09; Phase 3 then 4/5 | Canonical generation/timeline creation and emitted first events | Reducer initial snapshot/as-of baseline and InputHandle opening evidence |
| HTTP live association | A04/A11/A13; Phase 5 | Opening/uplink routing and local StreamError destination | REST/JSON-RPC LiveSession and InputHandle exact outcomes; do not finalize guessed wire selectors |
| Artifact origin/collision | A05/A07/A10; Phase 3 | Artifact identity, ownership, old-version projection | Companion artifact inputs and reducer origin preservation |
| Live batch/artifact exact correlation | A04/A10/A11; Phase 5 | Per-input outcome availability without new wire receipts | InputHandle COMMITTED/REJECTED versus INDETERMINATE; message event alone cannot prove whole batch |

Decision wording for the maintainer/user, explicitly **SDK policy choices pending upstream clarification**, not retroactive claims that RC says them:

1. **Discovery:** “Approve exact explicit 1.0/1.1 card entries and exact requested-version selection, treating the proto latest-minor comment as advertisement guidance rather than implicit older-minor matching?”
2. **Cursor:** “For the experimental RC implementation, which contradictory ordinary cursor rule governs: always-opening Task prose, or retained-cursor event replay from the request proto? May the proposed #2411 hint behavior be adopted as an explicitly documented deviation, or must that wait for upstream merge? Confirm ordinary and live subscribe both reject already-terminal tasks.”
3. **Bootstrap:** the architecture-selected SDK trace is initial message at generation 1, followed by artifacts and separately emitted status mutations. Retain authoritative upstream clarification before asserting this trace is normative; do not ask the user to choose the earlier message-at-2 research alternative again.
4. **HTTP:** “Provide or select an authoritative binding rule that distinguishes an opening downlink from a short uplink and associates each rejection/positive acknowledgement with its submitting caller when two exchanges share one principal/task. The supplied trace has no selector. Until resolved, keep dependent HTTP live work gated; do not invent a wire field/header/extension.”
5. **Artifact:** “Approve ID-only identity (already normative), with #2411's original-creator role normalization and agent-controlled cross-origin collaborative edits as explicit experimental SDK policy, or retain the RC origin/collision gate pending authoritative clarification?”
6. **Exact outcomes:** “For artifact-only/chunked/batched duplex inputs lacking unique request correlation, confirm finite INDETERMINATE outcomes unless actual unambiguous evidence is received; do not promise universal positive receipts, atomic-batch receipts, or infer rejection from silence. If universal exact results are required, which existing authoritative binding rule provides them?”

These decisions can unblock scoped experimental policies only to the degree authorized; they do not make an open PR the target or support final conformance. The HTTP question needs a concrete binding resolution before implementation, not merely approval of the recommended SDK handle names.


Read [decisions and dependent work](decisions.md) and the [required client-rule supplement](required-client-rules.md) before implementation. These record fresh independent review corrections and the additional same-generation recovery gate.


## Upstream clarification tracking

User-requested issue filing was checked against existing issues. [A2A #2415](https://github.com/a2aproject/A2A/issues/2415) already covers this HTTP association ambiguity and remains OPEN at the fresh check on 2026-11-10. Its comment links PR2411 without supplying a routing rule. Follow this issue; do not infer resolution from its existence or create a duplicate. Read-only snapshots: [issue](evidence/upstream-issue-2415.json), [comments](evidence/upstream-issue-2415-comments.json). No external GitHub state was changed.

Follow-up: [selected SDK policies](selected-policies.md) supplies concrete discovery, bootstrap, cursor/recovery and artifact-origin plans already supported by the recommended architecture. Its distinctions supersede the earlier broad requests for repeat policy approval; genuine normative residuals stay open.
