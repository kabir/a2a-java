# T6c gRPC implementation handoff

## Status

The four P2 findings in the independent review have been corrected and accepted
in the correction re-review, with one nonblocking recommendation to strengthen
the public lifecycle race evidence. No T7 work or Phase 2 completion claim is
made.

The gRPC 1.1 public transport now sends companion input requests, maps unary and
streaming responses through the versioned decoder, preserves per-task timeline
decode evidence, maps typed generation mismatch errors, and handles unknown,
malformed, locally rejected, cancelled, and completed stream outcomes. Its
capabilities report only ordinary operations for 1.0 and the implemented
guarded input, artifact input, and unknown-variant skip features for 1.1. The
SDK-owned channel admission and metadata paths are unchanged.

The transport rejects unsupported current-generation reads and 1.0 timeline
limits before I/O. Cursor observation and operational mutation/replay/live
semantics remain outside T6c. The public `Client.builder` peer test lives in
`client/base` because the gRPC transport module cannot depend on the base module;
transport-level decoder and wire tests remain in the gRPC module.

## Changed files

- `client/transport/grpc/src/main/java/org/a2aproject/sdk/client/transport/grpc/GrpcTransport.java`
- `client/transport/grpc/src/main/java/org/a2aproject/sdk/client/transport/grpc/GrpcTransportProvider.java`
- `client/transport/grpc/src/main/java/org/a2aproject/sdk/client/transport/grpc/EventStreamObserver.java`
- `client/transport/grpc/src/test/java/org/a2aproject/sdk/client/transport/grpc/Phase2PublicClientTest.java`
- `client/transport/grpc/src/test/java/org/a2aproject/sdk/client/transport/grpc/EventStreamObserverTest.java`
- `client/transport/grpc/src/test/java/org/a2aproject/sdk/client/transport/grpc/GrpcErrorMapperTest.java`
- `client/base/src/test/java/org/a2aproject/sdk/client/Phase2GrpcPublicClientTest.java`
- `.claude/architecture/a2a-1.1/task-list.md`
- `.claude/architecture/a2a-1.1/phase-2/t6c-grpc-handoff.md`

Pre-existing documentation edits, `AGENTS.md`, `implementation-tasks.md`, and
`test.log` were preserved. No shared production code, HTTP adapter, schema,
generated source, or historical fixture was changed.

## Checks and results

- `mvn -B -ntp -pl client/transport/grpc -am -DskipTests compile` — passed after
  fixing three NullAway findings in the new observer path.
- `mvn -B -ntp -pl client/transport/grpc -am -Dtest=Phase2PublicClientTest,EventStreamObserverTest -Dsurefire.failIfNoSpecifiedTests=false test`
  — passed; both target classes executed, 9 tests, 0 failures/errors/skips.
- `mvn -B -ntp -pl client/base -am -Dtest=Phase2GrpcPublicClientTest,Phase2ClientInputTest,ClientTransportPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test`
  — passed; all three targets executed, 18 tests, 0 failures/errors/skips.
- `mvn -B -ntp -pl client/transport/grpc -am -Dtest=Phase2PublicClientTest,EventStreamObserverTest,GrpcErrorMapperTest,GrpcChannelMetadataTest,GrpcGlobalProviderIsolationTest,GrpcOpaqueChannelAdmissionTest,GrpcOwnedCallAdmissionTest,GrpcOwnedChannelTest,GrpcOwnedLifecycleTest,GrpcProviderCleanupTest,GrpcTransportTest -Dsurefire.failIfNoSpecifiedTests=false test`
  — passed; all selected target classes executed, 73 tests, 0 failures/errors/skips.
  This covers the gRPC binding cases plus existing channel admission, metadata,
  ownership, cleanup, and lifecycle regressions.
- `git -c core.fsmonitor=false diff --check` — passed after the review
  corrections and handoff update.

The first expanded gRPC run, before the cancellation bridge fix, executed 63
tests and failed only `GrpcOwnedLifecycleTest.closeCancelsActiveStreamWithinBoundAndIsIdempotentForBothVersions`:
1.1 local cancellation completed the SPI observation without delivering the
legacy callback terminal. The bridge now delivers one `CancellationException`
for that cancelled end; the post-fix 73-test run passed.

A later isolated rerun of that lifecycle selector was blocked before Surefire by
`Operation not permitted` while Maven copied `spec/target/classes/META-INF/beans.xml`.
That selector did not execute in that attempt; the same lifecycle class executed
and passed in the post-fix 73-test gRPC run. Maven also selected Quarkus plugin
3.40.1.2 under Java 17 because the available 4.0.0 RC requires Java 21. Other
reactor modules had no matching selected tests under
`-Dsurefire.failIfNoSpecifiedTests=false`; all named target tests above did run.

## Review corrections follow-up

The review report at [t6c-review.md](reviews/t6c-review.md) is preserved as the
findings record and was not edited.

- Public unary `GrpcTransport.sendMessage` now decodes task responses through the
  selected 1.1 decoder; direct `GrpcTransport.getTask` does the same. Malformed
  known payloads become `A2AClientException`. Explicit 1.0 continues using the
  legacy mapping.
- Explicit 1.0 `listTasks` restores the historical returned-task-count
  `pageSize` and empty-token-to-null mapping. The peer case uses a final page and
  a wire page size that differs from the returned count.
- The opening-deadline cancellation action and the observer now share a
  cancellation flag. A deadline before observer installation is observed by
  `beforeStart`, which cancels the late call. The bounded test checks the
  deadline terminal, underlying cancellation, and that no frame is delivered.
- Public `Client.builder` controlled-peer tests now cover default 1.1 selection,
  explicit 1.0 local input rejection, unary task/direct-Message decoding,
  malformed unary and streaming task payloads, public list decoding,
  guarded/artifact input and typed mismatch errors, all-unknown close, two-client
  isolation, and a held-callback peer-completion/client-close race.

## Correction verification

- `mvn -B -ntp -pl client/base -am -Dtest=Phase2GrpcPublicClientTest,Phase2ClientInputTest,ClientTransportPolicyTest,Phase2PublicClientTest,EventStreamObserverTest -Dsurefire.failIfNoSpecifiedTests=false test`
  — `BUILD SUCCESS`. The base module ran `ClientTransportPolicyTest` (9),
  `Phase2GrpcPublicClientTest` (7), `Phase2PublicClientTest` (12), and
  `Phase2ClientInputTest` (8): 36 tests, no failures/errors/skips. Because `-am`
  also built adapter modules with matching names, JSON-RPC (19), gRPC (10), and
  REST (22) selected tests ran and passed.
- `mvn -B -ntp -pl client/transport/grpc -am -Dtest=Phase2PublicClientTest,EventStreamObserverTest,GrpcErrorMapperTest,GrpcChannelMetadataTest,GrpcGlobalProviderIsolationTest,GrpcOpaqueChannelAdmissionTest,GrpcOwnedCallAdmissionTest,GrpcOwnedChannelTest,GrpcOwnedLifecycleTest,GrpcProviderCleanupTest,GrpcTransportTest -Dsurefire.failIfNoSpecifiedTests=false test`
  — `BUILD SUCCESS`; all named gRPC regression targets ran, 74 tests, no
  failures/errors/skips.
- Earlier test-first reproductions failed as expected for the two reported
  behavior regressions: 1.1 unary task decoding rejected an unknown timeline
  entry, and explicit 1.0 final-page `pageSize` was 0 instead of the returned
  task count. Both now pass in the final runs above.
- The first deadline test double did not implement `StreamObserver.onCompleted`,
  then its default `disableAutoRequestWithInitial` threw; the test double was
  completed to match the gRPC API. Final `EventStreamObserverTest` passed all 5
  cases. These were test-harness issues, not remaining product failures.
- Maven ran on Java 17 and selected Quarkus plugin 3.40.1.2 because the available
  4.0.0 RC requires Java 21. Spotless checks were skipped by the existing build
  configuration; protobuf generation was also skipped as configured. No
  historical fixture suite or T7 gate was run.

## Unresolved items and next action

- Sol's [correction re-review](reviews/t6c-review.md#correction-re-review--2026-11-16)
  accepts T6c with a nonblocking recommendation: stage a terminal attempt while
  the public callback is held and add a pending update/error variation to the
  race test. The separate authorized evidence follow-up is recorded below and
  was accepted by the focused [race follow-up review](reviews/t6c-public-race-followup-review.md).
  That review's two low test-quality notes are corrected, verified, and closed
  in its [correction re-review](reviews/t6c-public-race-followup-review.md#correction-re-review--2026-11-16).
- T7 compatibility/evidence gates and the existing specification gates remain
  open; historical fixtures were not rerun or recaptured. T7 remains unassigned
  and was not started.
- No unresolved blocking T6c implementation issue is currently known. Wait for a
  separate T7 assignment; do not start it automatically.

## Public race evidence follow-up — 2026-11-16

The test-only follow-up is recorded in the [focused checkpoint](checkpoints/t6c-public-race-followup.md).
It keeps the first public callback held until the peer completion action and
public client close both return, after a second known task-message update has
been submitted to the controlled peer. A separate peer-error case submits the
same kind of update before an UNAVAILABLE terminal and verifies that the second
public callback runs before exactly one mapped terminal callback.

The focused Java 17 command executed only
`Phase2GrpcPublicClientTest`: 8 tests, zero failures, errors, or skips. The first
attempt was blocked by sandbox denial of the Netty local socket bind; the same
command passed after local socket access was granted. Exact logs and XML
provenance are in the checkpoint. This receipt records implementation evidence,
not an independent review of the correction. In the completion/close case, the second
frame is confirmed submitted by the peer's completed `onNext` call, but local
close can cancel it before it becomes a public callback; the peer-error variant
proves the queued known update is delivered after the held callback is released.

## Review-note corrections — 2026-11-16

The two low notes in the accepted race follow-up review are implemented: the
peer-error case asserts the mapped gRPC status is `UNAVAILABLE`, and each test
nests cleanup so server shutdown is attempted after client/executor cleanup
failures. The focused Java 17 command again executed only
`Phase2GrpcPublicClientTest`: 8 tests, zero failures, errors, or skips, with
`BUILD SUCCESS`. Raw log, fresh XML provenance, and final source hash are in the
[checkpoint](checkpoints/t6c-public-race-followup.md#review-note-corrections--2026-11-16).
The independent [correction re-review](reviews/t6c-public-race-followup-review.md#correction-re-review--2026-11-16)
closes both notes and confirms the race and pending-update assertions remain
intact. The test source still matches the recorded passing candidate; no new
test run was needed for this documentation update. The T6c follow-up is closed.
