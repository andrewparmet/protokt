# Long-Term Plans

## Active plans

| # | Plan | Priority | Status |
|---|------|----------|--------|
| 01 | [Self-host compiler](01-self-host-compiler.md) | High | Proposed |
| 02 | [Protobuf JSON encoding](02-protobuf-json.md) | Medium | Proposed |
| 03 | [JS gRPC hardening](03-js-grpc-hardening.md) | Low | Proposed |
| 04 | [Kotlin-Native gRPC via kotlinx-rpc](04-kotlinx-rpc-grpc.md) | Medium | Proposed |
| 05 | [Rename Writer/Reader to Encoder/Decoder](05-rename-writer-reader.md) | Low | Proposed |

## Completed (implemented on `main`)

| Plan | Implementing PR(s) |
|------|--------------------|
| Persistent collections via kotlinx-collections-immutable | #455 |
| Lazy/caching wrapper types (`LazyReference`) | #457 |
| Lazy caching string fields | #468 |
| Simplified caching constructor + extended to wrapper types | #457, #468, #483 |
| Extended caching to wrapper types, removed `acceptsDefaultValue` | #457, #483 |
| Pure Kotlin wire format codec + runtime-selectable codec | #466, #467 |

## Other considerations for first-class status

These are observations rather than full plans. They may become plans as priorities
crystallize.

### Kotlin/Native target

With the pure Kotlin codec done and the runtime free of protobuf-java, adding
Kotlin/Native as a compile target is mechanically feasible. The main work is:
- Ensuring all expect/actual declarations have Native actuals
- UTF-8 validation on Native (platform-specific or pure Kotlin scanner)
- Testing and conformance on Native
- Adding Native to the multiplatform publication matrix

This would make protokt the only Kotlin multiplatform protobuf library targeting
JVM + JS + Native.

### Editions (proto edition 2023+)

The proto team is moving toward editions as the future of the protobuf language.
Protokt's codegen already declares `maximumEdition = EDITION_2023` but the
implementation is incomplete. Full editions support means correctly resolving
feature sets, understanding feature defaults per edition, and generating code that
respects edition-specific semantics.

### Performance benchmarks

The `benchmarks/` module exists with JMH infrastructure. Publishing benchmark
results showing competitive performance with protobuf-java (especially for the
pure Kotlin codec and LazyReference optimizations) would strengthen the project's
credibility.

### proto2 support completeness

Currently "marginal" (Main.kt: "we don't support all of proto2 but we have to say
we support it for protovalidate examples"). The self-hosting plan requires proto2
support for `descriptor.proto` and `plugin.proto` specifically, but broader proto2
support would benefit users with legacy `.proto` files.
