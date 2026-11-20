# P3-T04 full-reactor correction review — 2026-11-20

**Disposition — Changes required.** Reviewed `a0a5b6b9158f6df1352f8aa017be1be294e84a2d` on
`support/a2a-1.1.0` against the [diagnosis](../handoffs/P3-T04-full-reactor-diagnosis.md)
and the approved T03/T04 requirements for admitted proposals and canonical publication.
Fresh targeted verification passed **168/168**. Two independent production-path probes
reproduced defects outside the selected tests. The existing T03/T04 acceptances and
saved reactor result remain historical evidence. P3-T05 and its prompt remain held.

## Findings

### P1 — A proposal can commit outside the main bus and its ordered publisher

`server-common/src/main/java/org/a2aproject/sdk/server/tasks/ExecutorSubmissionService.java:298–316,351–378,535–564`

A registered submission's public `ProposalAdmission.process()` can claim an
UNDECIDED item and invoke the real atomic authority with
`TaskCommitSlotCallback.NONE`. The new callback-taking overload also accepts a
caller-supplied callback without evidence that the processor owns it. A temporary
probe used the public submission API with a custom queue that called `process()`
without forwarding to any MainQueue. It returned `Committed(changed=true)` and
advanced storage by one generation. There was no bus admission, publisher or
delivery. If a queue processes the capability before forwarding it, the later
bus processing also cannot claim the item.

Require a processor-owned processing capability for the mutating production
path and bind it to the admitted item and its owning queue. Cover the race where
the processor dequeues just before the producer records successful admission.
Preserve the injected authority's local-result tests and T04's forwarding
certainty behavior. Verify that processing outside the ordered bus leaves the
store unchanged and that real admission commits and publishes once. Applies
A05/A06 and the approved T04 admission boundary.

### P1 — Terminal full-Task proposals can close the originating stream before delivery

`server-common/src/main/java/org/a2aproject/sdk/server/events/EventQueue.java:880–884`,
`server-common/src/main/java/org/a2aproject/sdk/server/events/MainEventBusProcessor.java:386–420`,
`server-common/src/main/java/org/a2aproject/sdk/server/events/EventConsumer.java:178–188`

T04 excluded all proposal envelopes from pre-submitted final-event expectations
while the production authority could only reject them or return a no-op. The
repair now commits terminal status-only proposals and retains that exclusion.
The child therefore looks idle as soon as the executor returns. A temporary
1.0 `DefaultRequestHandler` probe paused the processor at a preceding event,
enqueued an existing-task COMPLETED proposal and let the executor return.
The stream completed normally with zero frames after three idle polls.
Releasing the processor committed COMPLETED and attempted delivery to zero
children. The same idle mechanism can close a streaming response awaiting an
already-admitted nonterminal compatibility result.

Track pending proposal delivery at least for the originating stream using the
existing bounded queue lifecycle. Resolve the exact expectation on delivery,
rejection or failure. Preserve conflict isolation and avoid clearing independent
pending expectations or marking rejected proposals final. Add a controlled
processor-delay test that observes the terminal Task in 1.0 and the canonical
terminal status in 1.1 before completion. Applies A06/A07 and T03 finality.

### P2 — Version-selected webhooks depend on the sender's concrete type

`server-common/src/main/java/org/a2aproject/sdk/server/events/MainEventBusProcessor.java:669–704`,
`extras/opentelemetry/server/src/main/java/org/a2aproject/sdk/extras/opentelemetry/server/OpenTelemetryPushNotificationSenderDecorator.java:81–101`

A sender that implements `PushNotificationSender` by forwarding to the SDK
base sender gets the ordinary canonical status for a changed proposal and
silently loses the active no-op notification. The forwarding contract carries
no intent to select the older Task shape. If the injected object exposes
`BasePushNotificationSender` methods directly, the new calls instead bypass
custom behavior implemented only in `sendNotification`. The repository's
OpenTelemetry decorator instruments that interface method. The concrete-type
branch therefore either loses compatibility delivery through an interface
wrapper or bypasses the existing interception point. This is a source finding.
The review did not replicate CDI proxy generation.

Carry version-selection intent through a backward-compatible sender dispatch
seam and exercise the real sender through a delegating wrapper. Keep the
version decision per config and retain canonical fallback for a genuinely
version-unaware custom sender. Cover one mixed 1.0/1.1 setup with a deferred
earlier webhook admission, the verified no-op, a changed proposal and a later
delta. The five new tests call the authority directly, so they establish storage
behavior but do not cover this delivery chain.

## Verification and scope

- Started **2026-11-20 10:18:15 UTC**, deadline **12:18:15 UTC**. Review role requested
  Sol, medium reasoning, normal speed. Serving settings were unavailable for
  independent verification. No model override or delegation was used.
- Fresh Maven run finished **10:19:15 UTC**, exit **0**. Surefire reported
  **159 server-common tests, 3 JSON-RPC 1.0, 2 gRPC 1.0, 3 compat JSON-RPC 0.3
  and 1 core replication**. Zero failures, errors or skips. The three
  fixture migrations preserve the diagnosed snapshot-value/revision, Task
  consumption, and canonical replication/count checks.
- Evidence and probe sources are under `/private/tmp/a2a-p3-t04-review.j5F2l4/`.
  `targeted.log` records the Maven run. `admission-probe.log` and
  `terminal-probe.log` reproduce the two P1s. The temporary terminal probe's
  first compilation hit package-private `stop()`; it then used the existing
  test helper. The final probes used the candidate's Surefire classpath and
  finite 60-second compile/run caps plus 30 seconds cleanup.
- The targeted run combined the [handoff selectors](../handoffs/P3-T04-full-reactor-diagnosis.md#bounded-luna-correction-assignment-for-developer-dispatch)
  in one invocation with `/usr/bin/timeout -k 30s 1200s`, `mvn -B -ntp`,
  `-pl server-common,transport/jsonrpc,transport/grpc,compat-0.3/transport/jsonrpc,extras/queue-manager-replicated/core -am`,
  `-Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false`
  and the handoff's Byte Buddy `-javaagent`. No full reactor was rerun.
- No code, tests, index, accepted reviews or `test.log` were edited. No commits
  or external actions. This separate review records only the correction delta.


## Follow-up review — 2026-11-20 10:37 UTC

**Disposition — Changes required on the uncommitted follow-up.** HEAD remains
`a0a5b6b9158f6df1352f8aa017be1be294e84a2d`. The follow-up prevents the original
no-token `ProposalAdmission.process()` call from committing, retains 1.0 and
1.1 origin streams during a recoverable bus pause, and carries webhook intent
through the sender SPI and the OpenTelemetry decorator. The webhook finding
is closed for the reviewed sender and version-unaware fallback. The following
issues remain in the proposal processing and stream lifetime changes.

### P1 — A failed duplicate enqueue cancels the already-admitted proposal

`server-common/src/main/java/org/a2aproject/sdk/server/events/EventQueue.java:720–728`,
`server-common/src/main/java/org/a2aproject/sdk/server/tasks/ExecutorSubmissionService.java:274–278`

The rejection handshake changes an already-ACCEPTED submission to REJECTED
using the token for a different enqueue attempt. The probe forwarded the
genuine item into a one-permit MainQueue with the processor parked and then
repeated the enqueue. The first item was visibly in the bus. The second
attempt's capacity timeout reported KNOWN_NOT_COMMITTED and made the first
item unprocessable. Starting the processor drained it without changing
storage. Bind acceptance and rollback to the same enqueue attempt and have a
duplicate attempt leave the registered admission, original completion and
pending origin delivery intact. Verify the first admission commits and
publishes exactly once after the second attempt fails, while real first-attempt
pre-dispatch failures remain known non-commit.

The public production factory also retains a direct entry point at
`ExecutorSubmissionService.java:545–550`. Calling the returned
`ProposalAuthority.process` still commits changed proposals through
`TaskCommitSlotCallback.NONE`. The compatibility tests use that route to
change storage. Gate production authority mutation independently of the public
compatibility overloads. Keep planner-only unit tests or move production commit
coverage through the ordered bus.

### P1 — An unresolved proposal defeats the bounded stream timeout

`server-common/src/main/java/org/a2aproject/sdk/server/events/EventQueue.java:1261–1273`,
`server-common/src/main/java/org/a2aproject/sdk/server/events/EventConsumer.java:164–187`

The new delivery set participates in the awaiting-final and atomic close checks,
but the timeout clears only the original final counters. A production-path
probe held the processor for seven seconds after the executor returned. After
the three-second final wait expired, subsequent polls continued logging that
the flag had been cleared while the origin stayed open. Releasing the processor
delivered the terminal Task and closed the stream. With a sustained stall, the
consumer loop can keep waiting indefinitely. Give the exact unresolved proposal
delivery a bounded lifetime. Expiry should release the origin's wait without
reclassifying a still-pending authority result or clearing independent newer
expectations. Retain the two recoverable-delay tests and add a stalled-processor
test that crosses the wait bound.

### Follow-up evidence

- Started **10:37:53 UTC**, deadline **12:37:53 UTC**. Requested review role
  Sol, medium reasoning, normal speed. Serving metadata unavailable.
- Fresh combined targeted run at **10:38:36 UTC**, exit 0. **207 tests** ran
  (190 server-common, eight binding, one replication, eight OpenTelemetry),
  with zero failures, errors or skips. The combined selector executed 28 named
  sender tests and included the new delegating-sender and version-unaware
  fallback cases. The handoff's separate run reports 31 sender tests.
- The combined run selected the handoff's named test classes and binding methods
  with `-am -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false`,
  the installed Byte Buddy agent, and a 1200-second limit plus 30 seconds cleanup.
  `/private/tmp/a2a-p3-t04-rereview.V45ZUC/targeted.log` records the counts.
  Both temporary probes exited 0 after reproducing the defects. Probe sources and
  console evidence are in the same directory as `ReplayProbe.java`, `replay.log`,
  `ExpiryProbe.java` and `expiry.log`.
- Tracked candidate diff SHA-256
  `a6c34947083c140f7a6bd3503692b31c9823d12cc96643437a075e4b7accca99`.
  No code, tests, task index, accepted reviews or `test.log` were edited.
  No commit, external action, full-reactor rerun or P3-T05 work occurred.

## Second follow-up review — 2026-11-20 10:49 UTC

**Disposition — Changes required.** Reviewed the uncommitted second follow-up at
`a0a5b6b9158f6df1352f8aa017be1be294e84a2d`. The exact-token admission claim
prevents the duplicate attempt from invalidating the first accepted item, and
the public production authority refuses direct mutation. The former indefinite
proposal-only wait is bounded. Fresh verification passed **214/214**. One
remaining interaction between proposal and ordinary final delivery was
reproduced.

### P1 — An older proposal consumes the timeout for a newer ordinary terminal event

`server-common/src/main/java/org/a2aproject/sdk/server/events/EventConsumer.java:152–183`,
`server-common/src/main/java/org/a2aproject/sdk/server/events/EventQueue.java:1188–1190,1277–1283`

The child reports proposal deliveries and ordinary final-event counters through
one `isAwaitingFinalEvent()` predicate. The consumer uses that combined
predicate to count toward the timeout that clears the ordinary final counters.
When an ordinary final is queued shortly before an older proposal expires, its
counter keeps the combined predicate true and inherits the older proposal's
elapsed wait. The timeout then clears the new ordinary final expectation.

A real 1.1 `DefaultRequestHandler` probe paused the processor, queued an
existing-task no-op proposal, then queued an ordinary COMPLETED status
approximately 2.6 seconds later. The origin completed normally with zero
frames after the ordinary terminal status had waited only **1,425 ms**. When
the processor was released, it committed the status with no origin left to
receive it. The new overlapping-proposal test protects one proposal from
another and misses this mixed case.

Keep separately aged proposal waits in the origin's close decision while
counting and clearing the ordinary final wait only on its own evidence.
Add a mixed-delay regression in which the bus resumes after the older
proposal expires and within the ordinary final event's delivery window.
Assert that the terminal status reaches the stream before completion.
Keep the recoverable-delay and stalled-proposal cases.

### Second follow-up evidence

- Review started **10:49:46 UTC**, deadline **12:49:46 UTC**. Requested Sol,
  medium reasoning, normal speed. Serving settings were unavailable.
- Fresh targeted run finished **10:50:27 UTC**, exit 0. **197 server-common,
  eight binding, one replication, and eight OpenTelemetry tests** passed with
  zero failures, errors or skips. Used the handoff's selectors in one reactor
  subset with `-am`, `PushNotificationSenderTest*` to include nested cases,
  `-Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false`,
  the installed Byte Buddy agent, and a 1200-second limit plus 30 seconds cleanup.
- `/private/tmp/a2a-p3-t04-rereview2.mGxNpj/targeted.log` records the counts.
  `MixedWaitProbe.java` and `mixed-wait.log` in the same directory preserve
  the reproduction. The probe compiled against the Surefire classpath and used
  60-second compile and run limits plus 30 seconds cleanup. The run exited 0
  after confirming the failure.
- Candidate tracked diff SHA-256
  `9005f6c941e3134f2bff366bce94d4022b969497863a8ac8bffbfd7d01ba2a51`.
  The untracked lifecycle test SHA-256 is
  `b5c08450172aa06b9853a0406b6bdf6ab65a74c4a4c10209da944a08ec8d7b32`.
  No code, tests, task index, accepted reviews or `test.log` were edited.
  No commit, external action, full-reactor rerun or P3-T05 work occurred.

## Third follow-up review — 2026-11-20 10:57 UTC

**Disposition — One compatibility correction remains.** Reviewed the uncommitted
third follow-up at `a0a5b6b9158f6df1352f8aa017be1be294e84a2d`. The previous P1
is closed for the SDK child queue. Its ordinary final counters and proposal
deadlines are now independent, and the production 1.1 mixed-delay test delivers
the terminal status before stream completion. Fresh verification passed
**215/215**. The earlier closed findings stand.

### P2 — Custom EventQueue implementations lose the ordinary final-event timeout

`server-common/src/main/java/org/a2aproject/sdk/server/events/EventQueue.java:427–438`,
`server-common/src/main/java/org/a2aproject/sdk/server/events/EventConsumer.java:133–166`

Before this change, a custom `EventQueue` could report an outstanding final
event through the public `isAwaitingFinalEvent()` hook, and the consumer would
invoke `clearAwaitingFinalEvent()` after the timeout. Its timeout now uses the
package-private `isAwaitingOrdinaryFinalEvent()`, whose base implementation
returns false. An out-of-package subclass cannot override that method. Its
public combined wait can still prevent the consumer from closing indefinitely,
while the timeout never advances.

A temporary out-of-package queue subclass implementing the established public
hooks ran against the candidate and against the committed consumer at
`a0a5b6b91`. With identical empty polls, the committed consumer called the clear
hook within 4.1 seconds and the candidate did not. Make the new base predicate
delegate to the existing public predicate, and retain the ChildQueue override
that reports only its ordinary final counters. Exercise the fallback with
an out-of-package or custom queue fixture while retaining the mixed-delay test.

### Third follow-up evidence

- Review started **10:57:04 UTC**, deadline **12:57:04 UTC**. Requested Sol,
  medium reasoning, normal speed. Serving settings were unavailable.
- Fresh targeted run finished **10:57:46 UTC**, exit 0. **198 server-common,
  eight binding, one replication, and eight OpenTelemetry tests** passed with
  zero failures, errors or skips. Used the second follow-up review's combined
  reactor selector and flags, including the 1200-second limit plus 30 seconds
  cleanup. The execution included all four proposal stream lifecycle tests.
- `/private/tmp/a2a-p3-t04-rereview3.vRHaCK/targeted.log` records the counts.
  That directory also contains `CustomQueueProbe.java`,
  `custom-candidate.log`, and `custom-baseline.log`. The control put only the
  compiled committed `EventConsumer` ahead of the candidate Surefire classpath.
  Probe compilation and execution each used a 60-second cap plus 30 seconds
  cleanup. Both runs exited 0 with the stated contrasting observations.
- Candidate tracked diff SHA-256
  `381fd97f7642f1f5328ff72b301f0483cf56e4af3a97c20a0eefe90790faed54`.
  Untracked lifecycle test SHA-256
  `cfd57e040b896740deef37d7d2cb3ddf35da765795de71b455e76953e62dfe41`.
  The review record was the only file edited. No full reactor, commit,
  external action or P3-T05 work occurred.

## Fourth follow-up review — 2026-11-20 11:11 UTC

**Disposition — Accepted for P3-T04 correction scope.** The remaining P2 is
closed on the uncommitted candidate at `a0a5b6b9158f6df1352f8aa017be1be294e84a2d`.
The base ordinary-final predicate delegates to the established public queue
hook. The SDK child queue overrides that predicate with only its ordinary
final counters, keeping proposal delivery on its independent deadline. The
out-of-package regression and the original independent custom queue probe
both verified that the public clear hook is called. All four SDK proposal
stream lifecycle cases passed, including the mixed-delay and stalled cases.
No remaining findings in this correction scope.

- Started **11:11:01 UTC**, deadline **13:11:01 UTC**. Requested Sol, medium
  reasoning, normal speed. Serving settings were unavailable.
- The combined targeted run finished **11:11:44 UTC**, exit 0. **216 tests**
  passed with zero failures, errors or skips (199 server-common, eight binding,
  one replication, eight OpenTelemetry). Used the third follow-up review's
  bounded Maven selector and flags with `CustomEventQueueCompatibilityTest`
  added. The prior custom queue probe ran against the new candidate on its
  Surefire classpath with a 60-second cap plus 30 seconds cleanup, exit 0.
  Evidence is in `/private/tmp/a2a-p3-t04-rereview4.T3jb8m/targeted.log`
  and `custom-probe.log`.
- Candidate tracked diff SHA-256
  `1e5a0bde1af4573bb9f7844f4853a372de1119939067277bcddf77a8b695a92a`.
  Untracked custom queue test SHA-256
  `bacc3b6e61ef71b42c05241385b40e3c1d21754a6f958cab427b15d53697d4b5`
  and lifecycle test SHA-256
  `cfd57e040b896740deef37d7d2cb3ddf35da765795de71b455e76953e62dfe41`.
  `git diff --check` passed. The review record was the only file edited.
  No full reactor, commit or P3-T05 work occurred.
