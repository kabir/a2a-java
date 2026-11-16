# T7 public-helper compatibility correction review — 2026-11-16

**Disposition: accepted within the public-helper correction scope.** This is a
focused independent Sol review of the correction to the existing no-version
`JSONRPCUtils.toJsonRPCResultResponse` and
`SseFormatter.formatResponseAsSSE` overloads and their focused regression
tests. It does not accept T7 or establish Phase 2 completion.

I inspected the correction diff in `spec-grpc` and `server-common`, the new and
updated helper tests, the current call sites, the correction section of
[verification.md](../verification.md#t7-public-helper-compatibility-correction--2026-11-16),
and the retained focused test logs. The two old overloads select 1.0 output;
explicit 1.0 filters the added fields, including zero-valued fields; explicit
1.1 retains them. The serializer rejects new stream response variants for
legacy versions instead of emitting them as legacy payloads. The existing
error-response branch remains unchanged. The in-repository JSON-RPC unary and
SSE routes pass their selected protocol version explicitly. I found no
blocking production or test finding in this correction.

The historical byte expectations were obtained through the matching frozen
protobuf serializer path, separate from the candidate process. I inspected
the frozen Phase 0 classpath file: SHA-256
`b8f66b5e851ec46da6e7de466fad51d76a5b4f1cac48f7420265c5baed762224`,
36 entries, with no current candidate `target` entry. Its direct-protobuf and
model-to-protobuf JSON response hashes match the two recorded regression
expectations. The saved Java 17 focused results report
`JSONRPCUtilsTest` 61/61 and `SseFormatterTest` 3/3, with zero failures,
errors or skips.

The earlier 8/8 selected-version endpoint parity includes three JSON-RPC
response bodies that call the changed three-argument serializer: cancel, get,
and send-message. Refresh those three candidate-versus-frozen selected-1.0
bodies after the final clean install before closing T7. The REST Task body and
four event bodies use unchanged serializers.

The additional `A2AServerRoutesTest` selector exited 1: all 36 cases errored
during Mockito inline `MockMaker`/Byte Buddy self-attachment before their
assertions ran. It is not passing route evidence. No test or build was run for
this review. Runtime metadata identified the reviewer model as `gpt-6-sol`;
the requested role was focused Sol review at medium reasoning.
