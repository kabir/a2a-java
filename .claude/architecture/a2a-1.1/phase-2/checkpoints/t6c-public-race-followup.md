# T6c public gRPC race evidence follow-up — 2026-11-16

## Assignment and boundary

Requested model: `gpt-6-luna`, medium reasoning, normal speed. Speed selection
was unavailable in the session controls; no observed model telemetry was
available to verify the requested identifier. Astra was not selected. Assignment
window: 2026-11-16 19:00:51–21:00:51 UTC. This is a test-only follow-up to the
nonblocking recommendation in [the preserved T6c review](../reviews/t6c-review.md#nonblocking-evidence-follow-up).
No production behavior, HTTP code, or prior review disposition changed.

## Test evidence change

`client/base/src/test/java/org/a2aproject/sdk/client/Phase2GrpcPublicClientTest.java`
now covers two real public `Client` cases with controlled Netty gRPC peers:

- The existing completion/client-close race holds the first public callback,
  submits a second known `TaskMessageUpdateEvent`, then starts peer completion
  and `Client.close()` together. It joins both completed actions before
  releasing the callback, verifies there was no terminal callback and the first
  callback had not exited, then waits for terminal delivery. The final drain
  checks exactly one public event, one terminal, and zero event callbacks that
  start after it; close suppresses the still-pending second frame.
- A new peer-error case submits a second known task-message update and then an
  UNAVAILABLE error while the first callback remains held. Both peer writes
  return before release; no terminal callback is present while held. After
  release, the second public event callback and terminal callback are joined.
  Final assertions require two events before one mapped `A2AClientException`
  (with gRPC status cause) and zero event callbacks after terminal.

The second peer frame in the completion/close race is confirmed submitted when
the controlled peer's `onNext` returns, then close may cancel it before public
delivery. This test cannot establish that the client decoded that frame before
the competing close. The peer-error variation verifies actual pending update
delivery and challenges the callback counter with a second event. The tests use
bounded latches/futures only; no timing sleeps. Executors and servers are shut
down and joined before final count assertions.

## Verification

Environment: Temurin Java 17.0.18, Maven 3.9.14.

Command:

```sh
mvn -B -ntp -pl client/base -am -Dtest=Phase2GrpcPublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The first attempt exited 1 because the sandbox denied Netty's local bind
(`Operation not permitted`, `0.0.0.0:0`); the raw log is
`/private/tmp/a2a-t6c-race-followup-2026-11-16.log`. With local socket access,
the exact command exited 0 (`BUILD SUCCESS`); the final raw log is
`/private/tmp/a2a-t6c-race-followup-2026-11-16-final.log`. An earlier successful
run before the final assertion-only changes is retained at
`/private/tmp/a2a-t6c-race-followup-2026-11-16-escalated.log`. XML parsing found
one executed matching Surefire suite,
`client/base/target/surefire-reports/TEST-org.a2aproject.sdk.client.Phase2GrpcPublicClientTest.xml`:
8 tests, 0 failures, 0 errors, 0 skipped. The XML lists the original six other
public client cases and both race cases. Other `-am` reactor modules had no
matching selected tests; a successful reactor summary was not used as test
discovery evidence.

The initial sandbox-denied attempt is environmental, not a test failure. The
focused independent review accepted the race evidence while recording two low
test-quality notes. Their correction receipt follows. This does not revise the
original T6c review, authorize T7, or establish Phase 2 completion.

## Review-note corrections — 2026-11-16

Requested model: `gpt-6-luna`, medium reasoning, normal speed. Speed selection
was unavailable; no observed model telemetry was exposed. Correction assignment
started 2026-11-16 19:15:48 UTC and retains the 21:00:51 UTC deadline. This
change addresses only the two low notes in
[the accepted follow-up review](../reviews/t6c-public-race-followup-review.md);
that review's text and disposition remain unchanged.

- The public peer-error case now asserts the mapped cause's exact gRPC status is
  `UNAVAILABLE`, in addition to its existing error-type assertions.
- Each lifecycle test releases the held callback, then nests cleanup so
  `server.shutdownNow().awaitTermination(...)` is attempted even if
  `client.close`, executor shutdown, or the bounded executor termination
  assertion fails. Existing timeout assertions and final count assertions remain.

Verification used Java 17.0.18 and Maven 3.9.14:

```sh
mvn -B -ntp -pl client/base -am -Dtest=Phase2GrpcPublicClientTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The command exited 0 (`BUILD SUCCESS`) at 2026-11-16 19:16:50 UTC. Surefire
executed only `Phase2GrpcPublicClientTest`: 8 tests, 0 failures, 0 errors, 0
skips. The fresh XML report is
`client/base/target/surefire-reports/TEST-org.a2aproject.sdk.client.Phase2GrpcPublicClientTest.xml`;
its mtime is later than the source mtime. Raw log:
`/private/tmp/a2a-t6c-review-notes-2026-11-16.log`. Final source SHA-256:
`9d4d67ee2517d26df547900721adcb1f7e83bbbc26a2593a038b53418cc59eb2`.

The two notes are implemented and verified. The independent
[correction re-review](../reviews/t6c-public-race-followup-review.md#correction-re-review--2026-11-16)
closes both notes and confirms the race and pending-update assertions remain
intact. Closure checked that the current source SHA-256 still matches the
passing candidate above and that saved XML reports 8 tests with no failures,
errors, or skips. Neither the static re-review nor this status update ran new
tests. The T6c follow-up is closed. The earlier sandbox-denied attempt and
successful follow-up runs remain recorded above and in their original logs.
