# T6c public gRPC race follow-up review — 2026-11-16

## Scope and disposition

Compared the current `client/base/src/test/java/org/a2aproject/sdk/client/Phase2GrpcPublicClientTest.java`
with `/private/tmp/a2a-t6c-race-before/Phase2GrpcPublicClientTest.java`, the
nonblocking evidence recommendation in [the original T6c review](t6c-review.md#nonblocking-evidence-follow-up),
and [the implementation checkpoint](../checkpoints/t6c-public-race-followup.md).
The review is limited to this public gRPC race follow-up. The broader worktree
contains unrelated edits, which were not reviewed. No implementation or test
source was modified, and no test/build command was run.

**Disposition: the follow-up closes the original nonblocking race-evidence gap,
with two nonblocking test-quality notes below.** The successful path has
deterministic callback overlap, meaningful pending-update/error coverage, exact
public terminal/event counts, and bounded waits. No production ordering defect
is indicated.

## Review findings

### 1. Low: assert the peer error's specific status

In `publicCallbackPeerErrorWaitsForPendingUpdateAndHasOneTerminal`, the peer
sends `UNAVAILABLE`, while the final assertions only require an
`A2AClientException` whose cause is a `StatusRuntimeException` (lines 597–600).
This proves that an error reached the public callback and retained a gRPC cause,
but it would also pass if the mapped cause carried a different status. Consider
asserting `Status.fromThrowable(mappedError.getCause()).getCode()` is
`UNAVAILABLE` (and, if useful, the expected description). This is not a blocker
for the ordering evidence.

### 2. Low: ensure later cleanup runs after an earlier cleanup timeout

Both methods perform bounded executor-termination assertions in `finally`
before shutting down the Netty server (race method lines 502–507; peer-error
method lines 590–595). If `race.awaitTermination` or `peer.awaitTermination`
returns false, its assertion throws and skips `server.shutdownNow()`. A failure
in `client.close()` would likewise skip the remaining cleanup. The normal path
does join the race/peer executor and server before checking final counts, which
makes those assertions stable; this note concerns only cleanup failure paths.
Nested `finally` blocks or accumulated cleanup failures would allow every
resource to be stopped while still reporting the timeout.

## Evidence assessment

- **Change against the supplied baseline:** the completion/close test now
  submits a second update before racing, releases both terminal actions through
  a start latch, joins both actions before releasing the callback, and asserts
  that the callback is still incomplete with no terminal delivery. A 15-second
  callback wait has an explicit failure and an exit latch. The baseline released
  the callback immediately after opening the action latch and sent only one
  event.
- **Overlap and callback hold:** each terminal operation is invoked after the
  shared start latch opens and its future is joined while `callbackExited` is
  still pending. This proves both terminal paths complete at the public API
  boundary while the first callback has not exited. It does not prescribe which
  terminal wins, which is appropriate for the completion/close race.
- **Pending update and error:** the completion/close case submits a second peer
  frame before either action, but does not establish that the client decoded it
  before close; the checkpoint accurately records this limit. The separate
  error case serializes a second update followed by `UNAVAILABLE` on one peer
  executor, waits for both writes while the first callback is held, then
  requires the second public event and terminal error after release. Its
  `callbacksAfterTerminal` counter and two-event assertion exercise ordering.
- **Counts and successful cleanup:** each test waits for terminal delivery,
  closes the client, joins its peer executor and server, then asserts exactly
  one public terminal and the expected event count. The close race expects one
  event because close can suppress the submitted pending frame; the error case
  expects two. Both assert zero event callbacks observed after the terminal.
- **Timeout behavior:** startup, callback entry, action staging, future joins,
  terminal delivery, and executor/server termination all have finite waits. The
  `finally` blocks release the held callback and interrupt the action executor,
  so ordinary assertion/timeout failures do not leave the callback blocked.
  Cleanup sequencing after an executor-termination timeout is the limitation in
  finding 2.

Requested independent-review role/model per repository policy: Sol, medium
reasoning, normal speed. Runtime model metadata was not available to verify.

## Correction re-review — 2026-11-16

Compared the updated test again with the supplied pre-follow-up snapshot. Both
nonblocking notes above are closed in the current test:

- The peer-error case now asserts the mapped cause's exact gRPC status is
  `UNAVAILABLE` (`Phase2GrpcPublicClientTest.java:611-614`).
- Both cleanup blocks now nest `finally` clauses so a `client.close()` or
  executor-termination failure does not skip server shutdown (`:502-512` and
  `:596-607`). All waits remain bounded, and the held callback is still released
  before cleanup begins.

The deterministic overlap, pending update/error ordering, post-cleanup exact
counts, and documented limit on whether the close-race frame was decoded remain
as previously assessed. No test/build command was run in this re-review; no
implementation or test source was changed by the reviewer.
