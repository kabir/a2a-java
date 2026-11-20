# P3-T04 full-reactor regression checkpoint — 2026-11-19

**Latest disposition — 2026-11-20:** The repair and subsequent follow-up
corrections are independently accepted by the [fourth follow-up review](../reviews/P3-T04-full-reactor-correction-review.md#fourth-follow-up-review--2026-11-20-1111-utc).
The accepted candidate passed 216 targeted tests with zero failures, errors or
skips and `maven.test.failure.ignore=false`. The saved full-reactor run below
predates the follow-up fixes; it is retained as historical evidence. The
developer explicitly requested amending the repair commit to include this
accepted candidate and its evidence. Earlier dispositions below are historical;
P3-T05 remains unassigned pending developer selection.

**Current test outcome — verified 2026-11-20:** The later saved full run completed
2026-11-19 17:00:37 UTC with zero failures/errors: **4,241 tests executed and 123
skipped across 83 successful reactor modules**. The original failing run below is
historical. The developer explicitly authorized committing the regression repair.
No separate independent correction-review record was found; original T03/T04
review acceptance is not extended by this test checkpoint. P3-T05 remains held.

Current source checkpoint: `2e2ee29a886905ef171a41135376e24dc6e93259`, branch
`support/a2a-1.1.0`, worktree `/Users/kabir/.codex-api/worktrees/3053/a2a-java`.
P3-T01 through P3-T04 retain their recorded task-scope independent acceptance.
The subsequent full-reactor regression gate has **not passed**. P3-T05 and its
prompt remain held at the developer's request. No runtime correction or new
independent review was performed by this coordinator investigation.

## Supplied run and evidence

The developer supplied the failure list from root `test.log`. The saved run
finished **2026-11-19 14:32:22 UTC**, duration **12:02 min**. Its final reactor
status says BUILD SUCCESS, but the saved Surefire properties include
`maven.test.failure.ignore=true`. That status does not establish passing tests.
The exact shell command was not supplied; do not reconstruct its other flags.

The log was read only after the developer identified it as the full-run evidence.
It remains untracked and was not written, deleted, recreated or committed by the
coordinator. At inspection it was 365069 bytes, mtime_ns 1795098742549260680;
no active writer was reported. Existing target-module XML agrees with the supplied
failures. No tests were rerun in this read-only diagnosis.

| Suite | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| server-common AtomicTaskStoreContractTest | 15 | 2 | 0 | 0 |
| server-common EventConsumerTest | 18 | 0 | 4 | 0 |
| transport/jsonrpc JSONRPCHandlerTest | 77 | 3 | 0 | 1 |
| transport/grpc GrpcHandlerTest | 67 | 2 | 0 | 0 |
| extras/queue-manager-replicated/core ReplicatedQueueManagerTest | 15 | 1 | 0 | 0 |
| compat-0.3/transport/jsonrpc JSONRPCHandler_v0_3_Test | 23 | 3 | 0 | 0 |

These six suites contain the supplied **11 assertion failures and 4 errors**;
this table is not a total for the whole reactor. Their existing evidence is in
each module's `target/surefire-reports/TEST-*.xml` and root `test.log`.

## Traced failure families

1. **Snapshot identity assumptions: two assertions.**
   `AtomicTaskStoreContractTest` lines 139 and 406 use `assertSame` across separately
   constructed TaskSnapshot values. T04's TaskSnapshot constructor now deep-copies
   Task payloads using ImmutableTaskCopy. The reported values have matching fields
   but distinct object identities. Preserve the immutable-provenance correction;
   evaluate value/revision/batch association instead of assuming reference identity.
   Do not weaken the matching-pair or revision non-reuse checks.

2. **Raw Task fixture admission: four EventConsumer errors.**
   The failing consumer fixtures use ChildQueue.enqueueEvent(Task), which routes
   through the producer/main-bus path. It now raises the deliberate provenance/
   completion-envelope admission error before consumption. Preserve Task-shaped
   consumer coverage and the production raw-admission guard. Existing T03
   ResultAggregator fixtures demonstrate consumer-local queue-item delivery.

3. **Real older-version full-Task output: eight binding failures.**
   The JSON-RPC, gRPC and compat-0.3 fixtures invoke real default-handler/executor
   paths. They emit initial/changed full Tasks or echo context.getTask().
   `AgentEmitter.emitEvent(Task)` adapts these to a proposal. The current
   `ExecutorSubmissionService.forTaskStore` rejects initial/changed full Tasks
   pending T05 and returns an unchanged Committed result with no events otherwise.
   The processor proposal fast path completes the local result without ordinary
   stream/push delivery. The observed missing Task events/notifications match
   that path. JSONRPCHandlerTest explicitly selects 1.0; the compat fixture
   explicitly selects 0.3. These cases cannot be dismissed as direct raw-queue
   fixture migration. Preserve their wire/notification coverage and the approved
   older-version contracts. A focused diagnosis must define the smallest safe
   compatibility repair or identify the specific T05 normalization slice needed;
   do not restore blind writes or weaken output assertions.

4. **Canonical replication stamp: one assertion.**
   The counting replication test enqueues the same submitted status twice and
   compares its final forwarded event to the original generation-0 object.
   The received second event has generation 1. Same-state canonical progress is
   a mutation and T03 publishes committed events. Preserve canonical stamping
   and establish the correct expected event/snapshot and mutation count; do not
   change production back to forwarding the original unstamped object.

## Recommended next developer assignment

Start one bounded Sol medium/normal regression diagnosis to resolve the
older-version compatibility repair and confirm the small fixture migrations.
This is not P3-T05 execution or a repeated whole-phase audit. Require a concrete
minimal repair plan and discriminating checks before assigning Luna corrections.
Preserve all original review findings and their dispositions. Any necessary
movement of a T05 slice into the repair must be explicit and justified; no public
contract change or unsafe compatibility bypass is authorized.

There is no active implementation/review assignment to resume and no inherited
runtime-assignment deadline. A separately started diagnosis records its own
start and two-hour backstop. No commit, push or PR is authorized by this checkpoint.


## Passing full-run verification and authorized commit — 2026-11-20

Coordinator commit preparation started **09:58:55 UTC**, with an **11:58:55 UTC**
two-hour backstop. Requested settings remain Sol, medium reasoning, normal speed;
exact serving metadata is unavailable. No production or test code was edited by
the coordinator. The developer reported all tests passing and explicitly requested
a commit; that authorizes the correction checkpoint independently of review status.

The saved full-reactor run ended **2026-11-19 17:00:37 UTC**, duration **11:11 min**,
with **83 SUCCESS** module entries and no failure/error markers. Fresh inspection
of the saved reports confirms:

| Report type | XML suites | Reported tests | Skipped | Executed | Failures | Errors |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Surefire | 327 | 4355 | 123 | 4232 | 0 | 0 |
| Failsafe | 1 | 9 | 0 | 9 | 0 | 0 |
| Total | 328 | 4364 | 123 | 4241 | 0 | 0 |

Surefire report modification times span **16:49:28–16:59:56 UTC**; the Failsafe
report is from **16:54:44 UTC**. Every changed Java source has a current compiled
class preceding its module's test evidence. The saved Surefire properties still
include `maven.test.failure.ignore=true`; unlike the earlier run, every saved
suite now has zero failures/errors. The exact shell command was not supplied and
is not reconstructed. No additional full or focused test run was needed for this
commit: the evidence was verified against the unchanged correction sources.

The existing `test.log` remains untracked, unopened for writing, unstaged and
unmodified by the coordinator. Its saved-run identity is:

- Size: **350163 bytes**; mtime_ns: `1795107637928644864`.
- SHA-256: `f611bf040c02e6af53838c8dda4298d69a916369cbf4236962d0a440fc6fde3d`.

The correction implements only the existing-task equivalent/status-only slice
identified by the Sol diagnosis plus the three fixture migrations. The remaining
T05 work stays held. The correction handoff asks for separate independent review;
no such report is present at this checkpoint, so passing tests and commit
permission are recorded without claiming that review. No next-task assignment,
commit push or PR is authorized. Issue association remains #1303.
