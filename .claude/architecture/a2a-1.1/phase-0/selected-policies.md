# Phase 0 follow-up: normative facts versus selected SDK policies

Prepared 2026-11-10. Scope: specification/document research only; no production edits, builds, historical A03 probes, commits or external writes. Read the retained `phase-0/{decisions,spec-gates,required-client-rules}.md` and architecture recommended contracts. Reused the complete official-source snapshots checked at 13:30:15–13:30:16 UTC; no new upstream query is asserted. Target remains `a2aproject/A2A@78e1d6a1f7f118c8e9bc830b04a8579c1119cb78` (1.1 RC). Open PR2411 at `d318e2e074bcc967ce5f09b746304a72a9794f03` remains a proposal in that evidence.

Read the [source evidence](spec-gates.md), [gate register](decisions.md), [architecture](../architecture.md) and [required client rules](required-client-rules.md) together. This supplement refines the research recommendation; it does not override normative text.

## Corrections to the earlier research recommendation

The earlier `spec-gates.md` supplied an alternative generation-1 empty baseline followed by initial message at 2 as its candidate and asked the user to approve basic recommended policies again. That was too broad. Architecture L704–709 already recommends **initial accepted message as the generation-1 timeline entry**. The original alternative remains evidence of the RC ambiguity, but is **not the recommended SDK policy**. Likewise exact discovery, preserved artifact origin, default rejection of cross-origin writes, bounded uncertain input outcomes, and no automatic resend are already recommended contracts. The user selected that scope; do not reopen these as discretionary preference questions.

Selecting an SDK policy does not change upstream text. It can make a precise experimental plan reviewable while leaving its normative clarification/conformance condition explicit. There is no authorization to quietly adopt the entire open PR or invent a wire guarantee.

## G2 discovery

**Direct facts.** Proto L442–462 describes each interface with exact version, binding, endpoint and optional tenant; latest-minor comment is present. Prose L893–903 requires requested Major.Minor semantics, rejects unsupported versions and expressly permits same-transport different-version interfaces. Nothing says an advertised 1.1 implies 1.0 support.

**Already selected SDK policy.** Use explicit entries for every exposed 0.3/1.0/1.1 contract, scoped to binding/endpoint/tenant; immutable exact client selection; no automatic fallback or older-minor expansion. This is consistent with the explicit multi-interface prose and avoids inferring unsupported semantics. Phase 0 can finish the policy record without another user approval question. The latest-minor comment is a wording tension, not positive evidence that exact entries must be replaced by a version range.

**Residual.** Preserve upstream clarification of how latest-minor advertisement coexists with explicit entries before an unqualified discovery-conformance claim. It does not prevent writing the exact-entry plan or classifying the selected client API. No user decision is needed unless changing the selected policy (A01/A02, Phase 1).

## G4 bootstrap: explicit recommended trace

**Direct facts.** Prose L576–587 and proto L214–222 initialize creation at 1, increment once per mutation and prohibit duplicate event generations. Prose L498 orders message before artifact updates; L626 assigns post-append timeline generations. No concrete initial-creation example establishes whether the first client append is absorbed into creation. The first required Task.status exists, but no explicit trace says it must consume a second initial generation independent of a status event.

**Selected SDK trace, as architecture L688–709/L725–745 recommends:**

| Step | Canonical generation | Timeline/artifact effect | Publication/result |
| --- | --- | --- | --- |
| New ordinary input remains request-local until task-bearing materialization | No stored task/generation yet | No speculative task history/artifacts | A no-task direct Message discards pending task inputs; no task mutation occurs. |
| Materialize task with accepted opening message M | 1 | Initial client TimelineEntry(M, generation=1); preserve old history semantics. Required initial status is the creation baseline. | Message acceptance and creation are one bootstrap mutation, not creation1 then message2. |
| Apply opening artifact update A1 | 2 | A1 startGeneration=2 if first appearance; endGeneration=2 if completed; no artifact timeline entry | One canonical artifact mutation; server owns stamp. |
| Apply opening artifact update A2 | 3 | As above, following list order | One canonical artifact mutation. |
| Apply a separately emitted first status update S | 4 | Status TimelineEntry(S, generation=4), even if state equals baseline; progress updates are mutations | One status event. Baseline status alone is not silently fabricated as a separately emitted update. |
| Return opening authoritative Task snapshot | Still 4 | Contains committed M, artifacts and status | Snapshot is a summary, not mutation5; do not send its already-covered mutations as fresh events to that same subscriber. Later live events begin strictly after its watermark. |

With no opening artifacts, the separately emitted first status is generation 2. If the first executor proposal is a terminal Task with final artifacts, normalize artifacts before terminal status per architecture L828–833 so status closes the stream after final artifacts. Materialization and the opening batch are validated/committed atomically by selected SDK transaction policy; this atomicity is a stronger SDK implementation contract, not a universal wire receipt invented from RC prose.

The existing observer path receives each committed canonical mutation once; a newly attaching/opening stream receives its coordinated summary and later updates. Whether the first status is actually emitted depends on executor output; do not invent a historical status event merely to populate timeline. Migration of a pre-1.1 task is different: establish generation-1 stored baseline once and preserve old data without reconstructing missing original timeline (architecture L981–987).

**Residual.** Keep the upstream creation/first-append ambiguity explicit before labeling this trace normative/final-conforming. The user does not need to choose between message1/message2 again to get a concrete recommended Phase 0 plan. A05/A06/A08/A10 and Phase 3 tests must prove this selected trace when implementation is authorized; a written trace is not passing runtime evidence.

## G6 artifact identity and origin

**Direct facts.** Proto L325–348: artifactId unique within task; optional role denotes originator; client USER, agent default AGENT. Append/replacement match ID. The key is `(taskId, artifactId)`, not `(taskId, artifactId, role)`. RC does not fully spell out hostile/unspecified role normalization or cross-origin edit policy. PR2411 source (not stale composite-key body) adds creator-role preservation and agent-controlled collaborative editing.

**Already selected SDK policy, architecture L190–205:**

1. Preserve original creator origin across every accepted update; omission of role on legacy stored artifacts means AGENT. Treat incoming role as data to validate/normalize against actual authenticated ingress origin; it never grants agent privileges to client input. For new client-origin artifacts store/emit USER, for new agent-origin artifacts AGENT. This is SDK validation/normalization policy, not an asserted new RC MUST.
2. Default reject a client update to an existing AGENT-origin ID and an agent update to an existing USER-origin ID; validate the full accepted batch before mutation. The ID collision refers to the **same** artifact; do not create a second artifact using another role namespace. Default reject is a valid conservative agent policy and aligns with the proposed PR's agent choice to disallow collaboration, without adopting that PR as target.
3. For the concrete default profile, reject an explicit role inconsistent with the authenticated ingress origin; when omitted, assign the ingress origin. Use the existing parameter-validation boundary and document this SDK restriction in the focused plan. No new role/security wire field is needed. Exact error mapping is ordinary invalid parameters and live request-local rejection only where that live binding is resolved; do not invent an artifact-specific protocol error.
4. Old-version projection hides USER artifacts/updates and preserves AGENT artifacts. Never erase USER role and present that input as agent output. Canonical storage retains all authorized artifacts.

These policies are already recommended scope, so no additional preference approval is needed to record them. **Residual:** reconcile with authoritative origin/collision text before calling the policy normative; an opt-in cross-origin collaboration API would be an additional design decision, not necessary for the selected default. A07/A10 and Phases 2/3 remain implementation/verification obligations.

## G3 ordinary/live cursor and recovery

**Direct facts.** Ordinary prose L297–313 unconditionally starts with Task; cursor request proto L906–916 explicitly says set cursor => subsequent events without opening Task. Live L463–470 supplies retained/no-snapshot, absent/snapshot, unavailable/snapshot fallback. Ordinary terminal subscription is UnsupportedOperationError (proto L85–86); no cursor exception exists. Live subscribe links ordinary operation and no positive terminal replay exception exists. Open PR2411 changes cursor to optional hint, but is not target.

**Recommended experimental RC profile already aligned with architecture L1020–1031:**

- **Ordinary cursor absent:** coordinated initial current Task snapshot, then strictly later updates.
- **Ordinary retained cursor:** choose the specific shared request cursor rule and the architecture's retained-replay design: replay strictly after cursor, no opening snapshot. Label the broad ordinary-prose conflict as unresolved; this recommendation is an explicit experimental interpretation, not proof of settled normative precedence.
- **Live subscribe:** same retained/absent rules, directly supported by live subsection. Unavailable/expired cursor uses current snapshot then later updates. After restart, bounded replay may be unavailable and fallback applies. Do not turn RC into unrestricted snapshot-only hint behavior while a retained range is available.
- **Already terminal task:** reject subscription, ordinary and recommended live interpretation; use GetTask for final snapshot. An attached stream closes after delivery of the terminal update. Do not promise a terminal cursor replay exception.
- **Client view:** cursor alone is NO_BASELINE; snapshot establishes only an as-of represented projection and limited windows. Known sequence accounting is distinct from semantic synchronization; unknown timeline kind with known generation does not fabricate a sequence gap. Reconciliation never automatically resubmits input.
- **Long poll:** unset immediate; task generation greater than supplied immediate; equal/lower SHOULD wait with bounded timeout and MAY return current state (prose L591–595). No invented requirement to block forever.

**Recovery recommendation that preserves the retained client-rule supplement:**

1. Keep server's finite authority reconciliation/reorder/buffer limits. If authoritative head equals already-delivered watermark and there is no independently unresolved semantic gap, emit no redundant Task snapshot. Avoiding an unnecessary unchanged recovery emission needs no wire exception.
2. For a complete retained gap, replay canonical strictly later events. For unavailable gap, only send coordinated replacement Task if the binding permits it; snapshot is no mutation. If same-generation emission would trigger the literal RC equal-event rule or legality is uncertain, use architecture's bounded close/reconnect/GetTask alternative, not a fabricated stamp or snapshot exception.
3. If the client actually receives generation0 or equal consecutive event generations, apply required disablement for the remainder of the interaction. No automatic generation reliability reset merely because transport reconnects. The selected version stays unchanged; do not send a caller guard that cannot be upheld.
4. Known unchanged-head polling does not require repeated snapshots. However, an unknown semantic effect already at the same generation can genuinely need a replacement snapshot. Do **not** claim that skipping redundant server snapshots solves this case or defines interaction boundaries. Retain the same-generation/interaction clarification; finite failure/incomplete view is valid, universal automatic restoration is not promised.

**Residual.** Ordinary cursor text conflict; live already-terminal interpretation; legality of midstream resync Task and meaning of event/interaction under equal-generation rule. The recommended profile is concrete enough for a Phase 0 design/test plan. It does not close the dependent normative gate or prove A06/A09/A11/Phases 4/5. Upstream clarification is needed for an unqualified conformance/reliable-recovery claim. The recommended API names and state distinctions do not depend on inventing an answer; exact behavior tests must retain the gate.

## What decisions the user actually needs

No new user preference question is needed for exact selection, message-at-1 bootstrap, origin-preserving default cross-origin rejection, bounded uncertainty or no automatic retry: the architecture/recommended-contract scope already selects them. Record these concretely with their experimental/normative limits.

The material unresolved requirement remains an **authoritative binding resolution**, especially HTTP opening/uplink/downlink association and exact per-input evidence. Existing issue #2415 and open PR2411 have been recorded; simply asking the user to approve recommended SDK handles cannot resolve missing wire association. Similarly, the user cannot make contradictory upstream cursor text or ambiguous same-generation interaction boundaries disappear by approving a local policy. A conscious deviation from RC, new extension/receipt, expanded collaboration semantics, or weakened guaranteed outcome scope would require an explicit material decision; none is silently recommended here.

For Phase 0 the concrete next status is: **the G2/G3/G4/G6 research and recommended-policy record can be complete once incorporated/reviewed, with their exact normative residuals retained as dependent gates**. That is distinct from declaring those gates normatively closed or declaring Phase 0 fully complete. The original Phase 0 task expressly asks for unresolved binding gates to be recorded, so absence of upstream resolution need not make the research itself endless. However its baseline/historical peer checks, API/SPI inventory, precise plans, required reviews and exit conditions still stand; no A03 probe is run in this task and no Phase 0 pass or Axx criterion is claimed. Do not move to production implementation solely because this supplement exists.
