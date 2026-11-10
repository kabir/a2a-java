# Provider cleanup continuation

Scope: GrpcTransportProvider.java and new GrpcProviderCleanupTest.java in support/a2a-1.1.0 worktree. No commit, push, PR, or Phase 2 work.

Read exact provider, transport constructor, SDK-owned factory lifecycle, configuration getters, relevant project instructions, Serena instructions, and TDD skill/reference.

Regression suite prepared (awaiting root-run RED):
- Positive control: real SDK-created channel appears in gRPC Channelz and disappears on close.
- Unsupported selected protocol version leaves no managed channel.
- Malformed selected tenant leaves no channel for either 1.0 or 1.1.
- Null card leaves no channel for either 1.0 or 1.1.
- A post-allocation configuration failure preserves the original exception and leaves no channel.

Tests inspect real gRPC Channelz with resolved loopback endpoints and release leaked resources in finally even on RED. They add no production testing hook or generic mock factory. Root exclusively runs Maven.

Implementation not started pending focused RED evidence. Planned safe cleanup captures the factory once, validates private SDK identity, validates immutable inputs before allocation, and closes only the fresh channel returned by that captured SDK factory on construction failure. Direct forInterface claim/reuse ownership is unchanged.

## RED evidence (root-run)

`resume-provider-cleanup-red`: 5 tests, 4 failures, 0 errors, 0 skipped, exit 1; duration 4.242s; 18:42:48.835555–18:42:53.077959. The positive Channelz control passed. All four intended resource-leak assertions failed: unsupported version, malformed tenant, missing card, and failure after allocation. Production code was edited only after root supplied this result.

## Implemented change

- Capture `getChannelFactory()` once, then check private SDK factory identity on that exact reference; a config subclass cannot substitute an arbitrary factory through a second getter call.
- Validate non-null card/descriptor, supported selected version, and tenant before allocation.
- Wrap transport construction after allocation; close only the fresh handle issued by the captured private SDK factory on failure, retain the original failure, and suppress cleanup errors.
- Preserve direct `forInterface` ownership semantics and existing opaque-factory rejection.

Compile ready. GREEN and broader transport regression results pending root execution.

## GREEN evidence (root-run)

Root reports `resume-owned-and-cleanup-green-compile-fixed` (18:45:00–18:45:09): all 5 cleanup tests passed. The run also passed all 12 existing owned-channel tests and 2 admission tests. Root is running the wider combined verification. This scoped cleanup work ran no Maven commands itself.
