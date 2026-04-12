# Plan: Kotlin-Native gRPC via kotlinx-rpc

**Status:** Proposed
**Priority:** Medium — blocked on kotlinx-rpc gRPC graduating from dev preview
**Depends on:** None

## Problem

protokt's gRPC support is tightly coupled to grpc-java (JVM) and grpc-kotlin. The
JS story exists via grpc-js but Kotlin/Native has no gRPC support at all. The
grpc-kotlin shim approach (extract classes from a JAR, invoke
`GeneratorRunner.mainAsProtocPlugin`) works because grpc-kotlin's protoc plugin
emits complete, self-contained Kotlin source. There is no equivalent for Native.

Meanwhile, JetBrains' kotlinx-rpc project (https://github.com/Kotlin/kotlinx-rpc)
has shipped a dev-preview gRPC implementation that is multiplatform: JVM via
grpc-java typealiases, and **Kotlin/Native via C interop with gRPC Core**. Their
architecture splits code generation into two stages: a protoc plugin that emits
trivial `@Grpc`-annotated interfaces, and a Kotlin compiler plugin that synthesizes
all runtime wiring (stubs, service descriptors, method descriptors, marshaller
resolution) at FIR/IR time.

## Approach: Option A — Generate `@Grpc` Interfaces, Rely on Their Compiler Plugin

protokt's codegen emits the simple `@Grpc`-annotated interfaces that kotlinx-rpc's
compiler plugin expects. Users apply the kotlinx-rpc Gradle plugin alongside
protokt's. The compiler plugin handles stub generation, service descriptors, and
transport wiring. protokt provides a `GrpcMarshallerResolver` that bridges protokt's
serialization to kotlinx-rpc's marshaller system.

This is *not* a clean ripoff like the grpc-kotlin shim. The grpc-kotlin shim invokes
a protoc plugin as a library and gets back complete source. Here, the heavy lifting
happens in a Kotlin compiler plugin that must be applied to the user's build. The
coupling is deeper but the payoff is real: multiplatform gRPC (JVM + Native) with
protokt messages, maintained by JetBrains.

## Design

### Generated `@Grpc` interfaces

For a proto service:

```protobuf
service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
  rpc StreamHellos (HelloRequest) returns (stream HelloReply);
}
```

protokt's codegen emits:

```kotlin
@Grpc
interface Greeter {
    suspend fun SayHello(message: HelloRequest): HelloReply
    fun StreamHellos(message: HelloRequest): Flow<HelloReply>
}
```

Rules:
- Unary and client-streaming: `suspend fun`
- Server-streaming and bidi: non-suspend, returns `Flow`
- Client-streaming and bidi: parameter is `Flow<T>`
- Single message parameter per method (kotlinx-rpc enforces this via diagnostics)
- `@Grpc(protoPackage = "...")` when proto package differs from Kotlin package
- `@GrpcMethod(idempotent = true)` / `@GrpcMethod(safe = true)` per method options

This is trivial to generate — no need to extract or shim anything from kotlinx-rpc's
protoc-gen. The interface shape is documented and stable (it's the public API
contract between their protoc plugin and compiler plugin).

### Marshaller bridge

kotlinx-rpc's marshaller interface:

```kotlin
interface GrpcMarshaller<T> {
    fun encode(value: T, config: GrpcMarshallerConfig? = null): Source  // kotlinx.io
    fun decode(source: Source, config: GrpcMarshallerConfig? = null): T
}

fun interface GrpcMarshallerResolver {
    fun resolveOrNull(kType: KType): GrpcMarshaller<*>?
}
```

protokt's message interface:

```kotlin
interface Message {
    fun serialize(): ByteArray
    fun serializedSize(): Int
}

interface Deserializer<T : Message> {
    fun deserialize(bytes: ByteArray): T
}
```

The bridge adapter:

```kotlin
class ProtoktGrpcMarshaller<T : Message>(
    private val deserializer: Deserializer<T>
) : GrpcMarshaller<T> {
    override fun encode(value: T, config: GrpcMarshallerConfig?): Source =
        Buffer().apply { write(value.serialize()) }

    override fun decode(source: Source, config: GrpcMarshallerConfig?): T =
        deserializer.deserialize(source.readByteArray())
}
```

The resolver uses reflection (JVM) or a codegen-produced registry (Native) to find
each message's companion `Deserializer`:

```kotlin
class ProtoktMarshallerResolver(
    private val registry: Map<KType, ProtoktGrpcMarshaller<*>>
) : GrpcMarshallerResolver {
    override fun resolveOrNull(kType: KType) = registry[kType]
}
```

On Native, the registry must be populated at codegen time since there's no
reflection. protokt's codegen would emit a top-level registry object per proto file:

```kotlin
object GreeterMarshallerRegistry {
    val entries: Map<KType, ProtoktGrpcMarshaller<*>> = mapOf(
        typeOf<HelloRequest>() to ProtoktGrpcMarshaller(HelloRequest),
        typeOf<HelloReply>() to ProtoktGrpcMarshaller(HelloReply),
    )
}
```

Users combine registries when configuring the client:

```kotlin
val client = GrpcClient("localhost", 50051) {
    messageMarshallerResolver = ProtoktMarshallerResolver(
        GreeterMarshallerRegistry.entries + OtherMarshallerRegistry.entries
    )
}
```

An alternative to manual registry composition: protokt's codegen could also emit
`@WithGrpcMarshaller` annotations on each generated message class, pointing to the
`ProtoktGrpcMarshaller` instance. This lets kotlinx-rpc's compiler plugin resolve
marshallers automatically without a resolver, but couples the generated message
classes to kotlinx-rpc's annotation — acceptable only if the annotation is an
optional dependency.

### Module structure

```
protokt-runtime-grpc-krpc/           # new module
  src/commonMain/
    ProtoktGrpcMarshaller.kt         # GrpcMarshaller adapter
    ProtoktMarshallerResolver.kt     # GrpcMarshallerResolver impl
```

Codegen changes in `protokt-codegen`:
- New `GrpcKrpcServiceGenerator` that emits `@Grpc` interfaces
- New `GrpcKrpcMarshallerRegistryGenerator` that emits per-file registries
- Gated behind a `generateGrpcKrpc` flag in `ProtoktExtension`

Gradle plugin changes:
- Apply kotlinx-rpc's Gradle plugin when `generateGrpcKrpc` is enabled (or document
  that users must apply it themselves)
- Add `kotlinx-rpc-grpc-core` and `kotlinx-rpc-grpc-client`/`-server` as
  dependencies

### User-facing build configuration

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.toasttab.protokt")
    id("org.jetbrains.kotlinx.rpc.plugin")  // kotlinx-rpc compiler plugin
}

protokt {
    generate {
        grpcKrpc = true  // emit @Grpc interfaces + marshaller registries
    }
}

kotlin {
    jvm()
    macosArm64()
    linuxX64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("com.toasttab.protokt:protokt-runtime")
                implementation("com.toasttab.protokt:protokt-runtime-grpc-krpc")
                implementation("org.jetbrains.kotlinx:kotlinx-rpc-grpc-client")
            }
        }
    }
}
```

## Work Packages

### Package 0: Feasibility Spike

**Goal:** Validate the approach end-to-end with a hardcoded example.

**Approach:**
- Hand-write a `@Grpc` interface and `ProtoktGrpcMarshaller` for a simple service
- Apply kotlinx-rpc's compiler plugin and verify the generated stubs work
- Connect a protokt-serialized message through the marshaller bridge
- Test on JVM and Native (macOS)
- Confirm that kotlinx-rpc's compiler plugin processes externally-generated `@Grpc`
  interfaces correctly (i.e. that it doesn't require its own protoc plugin to have
  run first)

**Key risks to validate:**
- Does the compiler plugin require any metadata beyond the `@Grpc` annotation?
- Are there implicit assumptions about the protoc-gen stage having run (e.g.
  generated companion objects, proto descriptors)?
- What happens with `@WithGrpcMarshaller` — can it be added externally or does the
  compiler plugin expect to synthesize it?
- Does `kotlinx.io.Source`/`Buffer` interop cleanly with protokt's `ByteArray`-based
  serialization on Native?

### Package 1: Marshaller Bridge Module

**Goal:** Ship `protokt-runtime-grpc-krpc` with the marshaller adapter.

**Approach:**
- Implement `ProtoktGrpcMarshaller<T>` and `ProtoktMarshallerResolver`
- Multiplatform module targeting JVM + Native
- Unit tests verifying round-trip serialization through the bridge
- Decide on `@WithGrpcMarshaller` annotation strategy based on spike findings

### Package 2: Codegen — `@Grpc` Interface Generation

**Goal:** `protokt-codegen` emits `@Grpc`-annotated interfaces from proto service
definitions.

**Approach:**
- Add `GrpcKrpcServiceGenerator` alongside existing `ServiceGenerator`
- Map proto method types to Kotlin signatures:
  - Unary: `suspend fun Foo(message: Req): Resp`
  - Server streaming: `fun Foo(message: Req): Flow<Resp>`
  - Client streaming: `suspend fun Foo(message: Flow<Req>): Resp`
  - Bidi: `fun Foo(message: Flow<Req>): Flow<Resp>`
- Emit `@Grpc(protoPackage = "...")` and `@GrpcMethod` annotations as needed
- Gate behind `generateGrpcKrpc` parameter

### Package 3: Codegen — Marshaller Registry Generation

**Goal:** `protokt-codegen` emits per-file marshaller registries for Native support.

**Approach:**
- For each proto file, emit an object mapping `KType` to `ProtoktGrpcMarshaller`
  for all message types used as RPC request/response types
- Consider also emitting `@WithGrpcMarshaller` on message classes if spike confirms
  this works

### Package 4: Gradle Plugin Integration

**Goal:** Smooth build experience for users.

**Approach:**
- Add `grpcKrpc` flag to `ProtoktExtension`
- Document required kotlinx-rpc plugin application
- Add `protokt-runtime-grpc-krpc` dependency automatically when flag is set
- Integration test: multiplatform project (JVM + macOS) with a gRPC service

### Package 5: End-to-End Example

**Goal:** Working example project demonstrating the full stack.

**Approach:**
- HelloWorld-style example under `examples/grpc-krpc/`
- Multiplatform: JVM server + Native client (or both on JVM, both on Native)
- Demonstrates marshaller resolver setup, channel configuration, streaming

## Risks and Open Questions

### kotlinx-rpc stability

Their gRPC support is a dev preview (as of 0.11.0-grpc-185). The `@Grpc` interface
contract, compiler plugin behavior, and marshaller APIs are all `@ExperimentalRpcApi`.
Breaking changes are likely before 1.0. Protokt would need to track these.

**Mitigation:** protokt can ship this support as beta/experimental, matching
kotlinx-rpc's own stability level. The generated `@Grpc` interfaces are trivially
simple — if the annotation contract changes, updating the codegen is cheap. The
marshaller bridge is also small. Tracking upstream changes is low-effort.

### Compiler plugin as a hard dependency

Unlike the grpc-kotlin shim (which is invisible to users), this approach requires
users to apply the kotlinx-rpc Gradle plugin. This is a visible, version-pinned
dependency in their build.

**Mitigation:** This is the same pattern as applying the kotlinx-serialization
plugin. Users of multiplatform Kotlin are accustomed to compiler plugins.

### Marshaller resolution on Native

Without reflection, mapping `KType` to the correct deserializer requires either a
codegen-produced registry or `@WithGrpcMarshaller` annotations. The registry approach
requires manual composition; the annotation approach couples message classes to
kotlinx-rpc.

**Mitigation:** The spike (package 0) will determine which approach works. Both are
viable; the question is which is less friction.

### Interaction with existing gRPC support

protokt currently supports grpc-java (via grpc-kotlin shim) on JVM and grpc-js on
JS. The kotlinx-rpc path would be a third gRPC backend. Users would choose one:
- `grpcKotlinStubs = true` for JVM-only projects (current, stable)
- `grpcKrpc = true` for multiplatform projects needing Native (new)

These should not be enabled simultaneously for the same service — the codegen should
enforce mutual exclusivity or emit a clear error.

### `suspendClientCalls` visibility

kotlinx-rpc's coroutine call helpers are `internal`. If the compiler plugin wires
everything up, this doesn't matter — the generated stubs call through `RpcClient`
which delegates internally. But if we ever need to call the transport layer directly
(e.g. for custom call patterns), we'd need to request public API or vendor the code.

### gRPC Core C library on Native

kotlinx-rpc's Native transport uses prebuilt gRPC Core static libraries
(`native-deps/grpc-c-prebuilt/`). These are published as Maven artifacts. protokt
users targeting Native would transitively depend on these. This limits Native targets
to what kotlinx-rpc supports (currently macOS and Linux, not Windows or iOS).

## Timeline Considerations

- **Now:** Monitor kotlinx-rpc gRPC development, track API changes
- **When kotlinx-rpc gRPC reaches beta:** Run the feasibility spike (package 0)
- **After spike validates:** Implement packages 1–5, ship as beta/experimental

## References

- kotlinx-rpc gRPC docs: https://kotlin.github.io/kotlinx-rpc/grpc-generated-code.html
- kotlinx-rpc gRPC codegen docs: https://kotlin.github.io/kotlinx-rpc/grpc-codegen.html
- kotlinx-rpc repo: https://github.com/Kotlin/kotlinx-rpc
- protobuf-kotlin DSL design doc: https://github.com/lowasser/protobuf/blob/master/kotlin-design.md
- protobuf-kotlin design discussion: https://github.com/protocolbuffers/protobuf/issues/3742
- kotlinx-rpc gRPC dev preview tracker: https://github.com/Kotlin/kotlinx-rpc/issues/176
