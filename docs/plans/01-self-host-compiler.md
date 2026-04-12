# Plan: Self-Host the Compiler (Remove protobuf-java from Codegen)

**Status:** Proposed
**Priority:** High — this is the single highest-leverage project for protokt's independence
**Depends on:** None (all runtime prerequisites are complete)

## Problem

The protokt code generator (`protokt-codegen`) depends on protobuf-java for two
purposes:

1. **Parsing the `CodeGeneratorRequest`**: `protoc` sends a serialized
   `CodeGeneratorRequest` on stdin. Today this is parsed via
   `CodeGeneratorRequest.parseFrom()` from protobuf-java, with a protobuf-java
   `ExtensionRegistry` to decode protokt's custom options.

2. **Accessing descriptor protos and custom options throughout codegen**: Every
   parser and generator file works with protobuf-java types —
   `DescriptorProtos.FileDescriptorProto`, `DescriptorProtos.FieldDescriptorProto`,
   `DescriptorProtos.DescriptorProto`, etc. Custom options are extracted via
   `options.getExtension(ProtoktProtos.property)`, which relies on protobuf-java's
   extension registry mechanism.

This creates several problems:

- **Circular dependency**: protokt generates Kotlin code from `.proto` files, but
  its own compiler depends on protobuf-java's generated Java code for the same
  `.proto` files (descriptor.proto, plugin.proto, protokt.proto).

- **Binary size**: protobuf-java is a large dependency (~1.7 MB) that exists solely
  for parsing at codegen time.

- **Version coupling**: Users are forced to align their protobuf-java version with
  whatever protokt's codegen needs, which can cause classpath conflicts.

Note: self-hosting means the codegen uses **protokt-generated Kotlin types** instead
of protobuf-java-generated Java types. The codegen itself still runs on the JVM —
"self-hosting" refers to the type system, not the target platform.

### Current state (as of April 2026)

The **runtime** is already free of protobuf-java in `commonMain` and `jvmMain`.
protobuf-java is only referenced by:
- `protokt-runtime-protobuf-java` (optional codec module)
- `protokt-runtime/jvmTest` (testing only)
- `protokt-codegen` (the target of this plan)

The runtime already has:
- Pure Kotlin codec (`ProtoktCodec`, `ProtoktWriter`, `ProtoktReader`) passing
  conformance tests
- Runtime-selectable codec via `protokt.codec` system property
- `LazyReference` for deferred wire/Kotlin conversion
- `StringConverter` with UTF-8 validation
- `CollectionFactory` with persistent collection support
- Generated protokt types for `descriptor.proto` in `protokt-core-lite`
  (`FileDescriptorProto`, `DescriptorProto`, `FieldDescriptorProto`, etc.)
- `FileDescriptor.buildFrom()` that parses descriptor bytes into the protokt type
  system

### protobuf-java usage in codegen (15 files)

| File | Usage |
|------|-------|
| `Main.kt` | `CodeGeneratorRequest.parseFrom()`, `ExtensionRegistry`, `CodeGeneratorResponse`, `Feature`, `Edition` |
| `GeneratorContext.kt` | `FileDescriptorProto`, `DescriptorProtos.Edition`, `ProtoktProtos.file` extension |
| `FieldParser.kt` | `DescriptorProto`, `FieldDescriptorProto`, `FeatureSet`, `OneofDescriptorProto`, `ProtoktProtos.property` extension |
| `MessageParser.kt` | `DescriptorProto`, `ProtoktProtos.class_` extension |
| `EnumParser.kt` | `ProtoktProtos.enum_`/`enumValue` extensions |
| `ServiceParser.kt` | `ProtoktProtos.service`/`method` extensions |
| `Types.kt` | All `DescriptorProtos.*Options` as fields on option wrapper types |
| `FileContentParser.kt` | `FileDescriptorProto` |
| `PackageResolution.kt` | `FileDescriptorProto` |
| `FileDescriptorResolver.kt` | `DescriptorProtos` for clearing json_name |
| `FileDescriptorEncoding.kt` | `FileDescriptorProto.toByteArray()` |
| `PropertyDocumentationAnnotator.kt` | Source code info types |
| `MessageDocumentationAnnotator.kt` | Source code info types |
| `EnumGenerator.kt` | `DescriptorProtos` for deprecation checks |
| `GrpcKotlinGeneratorSupport.kt` | `FileDescriptorProto` for gRPC stub generation |

### Guava usage in codegen (8 files)

`com.google.common.base.CaseFormat` is used in `FieldParser.kt`, `EnumParser.kt`,
`ServiceGenerator.kt`, and `PluginParams.kt`. This is trivially replaceable with a
small utility function.

---

## Proposed Solution

### Phase 1: Extension Registry for protokt types

The core problem is that protobuf extensions are encoded as regular fields in the
`extensions` range (e.g., field number 1253 for protokt options). When a message
like `FieldOptions` is deserialized, fields in the extension range end up in the
`unknownFields` set because the generated deserializer doesn't know about them.

protobuf-java solves this with `ExtensionRegistry` — a lookup table that maps
`(message type, field number)` to a field descriptor, allowing the parser to decode
extension fields into typed values.

We need the same concept for protokt's own types.

#### `ExtensionRegistry` interface

Add to `protokt-runtime`:

```kotlin
package protokt.v1

interface ExtensionRegistry {
    fun findExtension(
        containingType: String,
        fieldNumber: Int
    ): ExtensionInfo?

    companion object {
        val EMPTY: ExtensionRegistry = EmptyExtensionRegistry
    }
}

class ExtensionInfo(
    val fieldNumber: Int,
    val deserializer: Deserializer<out Message>
)

private object EmptyExtensionRegistry : ExtensionRegistry {
    override fun findExtension(containingType: String, fieldNumber: Int) = null
}
```

#### How extensions are encoded on the wire

Protokt's custom options are messages set as extensions on standard options. For
example, `ProtoktProtos.FieldOptions` is extension field 1253 on
`google.protobuf.FieldOptions`. On the wire:

```
tag = (1253 << 3) | 2  // field 1253, wire type 2 (length-delimited)
length = N
bytes = [serialized ProtoktProtos.FieldOptions message]
```

Today, when protokt deserializes `google.protobuf.FieldOptions`, field 1253 goes
into `unknownFields` as raw bytes. With an extension registry, the deserializer
can instead:

1. See field number 1253
2. Look up `("google.protobuf.FieldOptions", 1253)` in the registry
3. Find `ExtensionInfo(1253, ProtoktProtos.FieldOptions.Deserializer)`
4. Deserialize the bytes as `ProtoktProtos.FieldOptions`
5. Store the result in a typed extension map on the message

#### Extension storage: typed map on `*Options` messages

Add an `extensions: Map<Int, Message>` field to messages that declare extension
ranges. This requires changes to the code generator to detect `extensions`
declarations in `.proto` files and add the field. For the `*Options` types
specifically (only used at codegen time), the extension map makes access natural:

```kotlin
val protoktFieldOptions = fieldOptions.getExtension(1253, ProtoktProtos.FieldOptions)
```

#### Reader changes

The `Reader` interface (or its implementations) needs to accept an optional
`ExtensionRegistry`. When reading a message, if the current field number falls in
an extension range and the registry has a match, deserialize the extension value
as a typed message instead of an unknown field. The registry can be set on the
Reader instance to avoid breaking API changes.

### Phase 2: Generate protokt types for `plugin.proto` and `protokt.proto`

Currently, `CodeGeneratorRequest` and `CodeGeneratorResponse` are protobuf-java
types from `com.google.protobuf.compiler.PluginProtos`. And `ProtoktProtos` is a
protobuf-java generated class from `protokt.proto`.

To self-host:
1. Add `google/protobuf/compiler/plugin.proto` to protokt's own build
2. Generate protokt types for `CodeGeneratorRequest`, `CodeGeneratorResponse`
3. Generate protokt types for `ProtoktProtos` (the custom option messages)
4. Register protokt option extensions in the extension registry

#### Bootstrap: checked-in generated code

This creates a bootstrap problem: protokt needs to generate its own compiler input
types, but the compiler needs those types to run. The standard solution is
**checked-in generated code**: generate the bootstrap types once and check them in.
Subsequent builds use the checked-in code. A CI task (`./gradlew verifyBootstrap`)
verifies the checked-in code matches what the current compiler would generate.

This is the same approach used by protobuf-java, protobuf-go, prost, and most
other self-hosting protobuf implementations.

### Phase 3: Migrate codegen to use protokt types

Replace all protobuf-java type references with the protokt equivalents:

| protobuf-java type | protokt equivalent |
|--------------------|--------------------|
| `DescriptorProtos.FileDescriptorProto` | `protokt.v1.google.protobuf.FileDescriptorProto` |
| `DescriptorProtos.DescriptorProto` | `protokt.v1.google.protobuf.DescriptorProto` |
| `DescriptorProtos.FieldDescriptorProto` | `protokt.v1.google.protobuf.FieldDescriptorProto` |
| `DescriptorProtos.EnumDescriptorProto` | `protokt.v1.google.protobuf.EnumDescriptorProto` |
| `DescriptorProtos.OneofDescriptorProto` | `protokt.v1.google.protobuf.OneofDescriptorProto` |
| `DescriptorProtos.ServiceDescriptorProto` | `protokt.v1.google.protobuf.ServiceDescriptorProto` |
| `DescriptorProtos.MethodDescriptorProto` | `protokt.v1.google.protobuf.MethodDescriptorProto` |
| `DescriptorProtos.SourceCodeInfo` | `protokt.v1.google.protobuf.SourceCodeInfo` |
| `DescriptorProtos.*Options` | `protokt.v1.google.protobuf.*Options` |
| `DescriptorProtos.FeatureSet` | `protokt.v1.google.protobuf.FeatureSet` |
| `DescriptorProtos.Edition` | `protokt.v1.google.protobuf.Edition` |
| `PluginProtos.CodeGeneratorRequest` | `protokt.v1.google.protobuf.compiler.CodeGeneratorRequest` |
| `PluginProtos.CodeGeneratorResponse` | `protokt.v1.google.protobuf.compiler.CodeGeneratorResponse` |
| `ProtoktProtos.*` | self-generated protokt equivalents |

Key migration points:

- **`Main.kt`**: Parse `CodeGeneratorRequest` from stdin bytes using protokt's own
  deserializer with extension registry. Build `CodeGeneratorResponse` using protokt's
  own type and serialize to stdout.

- **`Types.kt`**: Replace all `DescriptorProtos.*Options` fields with protokt
  equivalents. Replace `ProtoktProtos.*Options` with self-generated equivalents.

- **`FieldParser.kt`**: Replace `FieldDescriptorProto.Type` and `.Label` enums.
  Replace `FeatureSet.FieldPresence`. Replace `getExtension()` calls with
  extension map lookups on the protokt option types. Replace `CaseFormat` with
  a utility function.

- **`FileDescriptorEncoding.kt`**: Replace `toByteArray()` with `serialize()`.

- **`FileDescriptorResolver.kt`**: Replace `toBuilder().clearSourceCodeInfo().build()`
  with protokt's `copy {}` pattern.

- **`GrpcKotlinGeneratorSupport.kt`**: This bridges to grpc-kotlin's generator
  which expects protobuf-java types. Options:
  - Keep gRPC generation as a separate protoc plugin with its own protobuf-java dep
  - Write an adapter layer that converts protokt types to protobuf-java types
    at the boundary
  - Fork/vendor the relevant grpc-kotlin generator code

### Phase 4: Remove protobuf-java dependency

Once all codegen code uses protokt types:
- Remove `protobuf-java` from `protokt-codegen/build.gradle.kts`
- Remove `ProtoktProtos` Java generated code
- Keep protobuf-java as an optional dependency only in `protokt-runtime-protobuf-java`
  (for users who want the protobuf-java codec) and `protokt-reflect` (which provides
  `toDynamicMessage()` interop)

---

## Risks and Considerations

- **Bootstrap complexity**: Checked-in generated code requires discipline to keep in
  sync. A `./gradlew verifyBootstrap` CI task is essential.

- **Proto2 support**: `descriptor.proto` and `plugin.proto` are proto2. Protokt has
  "marginal" proto2 support (Main.kt comment: "we don't support all of proto2 but
  we have to say we support it for protovalidate examples"). Self-hosting requires
  the proto2 subset used in these specific files: `optional` fields with `has*()`
  checks, default values for scalar fields, extension fields. Full proto2 support
  is not needed — just the subset used in descriptor.proto, plugin.proto, and
  protokt.proto.

- **gRPC stub generation**: The grpc-kotlin generator integration expects
  protobuf-java types. The recommended approach is to keep gRPC generation as a
  separate concern with an adapter layer, rather than trying to eliminate
  protobuf-java from gRPC stubs entirely.

- **Performance**: Parsing `CodeGeneratorRequest` happens once per protoc invocation.
  Performance differences are negligible.

- **Editions support**: `descriptor.proto` uses proto2, but edition 2023 support
  requires understanding `FeatureSet` and `FeatureSetDefaults`. These must work
  correctly in the self-hosted types.

---

## Files to create/modify

| File | Change |
|------|--------|
| `protokt-runtime/.../ExtensionRegistry.kt` | New: extension registry interface |
| `protokt-runtime/.../Reader.kt` | Extension registry awareness |
| `protokt-core/.../compiler/*.kt` | New: generated types for plugin.proto |
| `protokt-codegen/bootstrap/` | New: checked-in generated code for bootstrap |
| `protokt-codegen/.../Main.kt` | Use protokt types for request/response |
| `protokt-codegen/.../Types.kt` | Replace protobuf-java option types |
| `protokt-codegen/.../GeneratorContext.kt` | Replace protobuf-java types |
| `protokt-codegen/.../FieldParser.kt` | Replace protobuf-java types, extension access, CaseFormat |
| `protokt-codegen/.../MessageParser.kt` | Replace protobuf-java types |
| `protokt-codegen/.../EnumParser.kt` | Replace protobuf-java types, CaseFormat |
| `protokt-codegen/.../ServiceParser.kt` | Replace protobuf-java types |
| `protokt-codegen/.../ServiceGenerator.kt` | Replace CaseFormat |
| `protokt-codegen/.../PluginParams.kt` | Replace CaseFormat |
| `protokt-codegen/.../FileContentParser.kt` | Replace protobuf-java types |
| `protokt-codegen/.../PackageResolution.kt` | Replace protobuf-java types |
| `protokt-codegen/.../FileDescriptorEncoding.kt` | Replace protobuf-java types |
| `protokt-codegen/.../FileDescriptorResolver.kt` | Replace protobuf-java types |
| `protokt-codegen/.../PropertyDocumentationAnnotator.kt` | Replace protobuf-java types |
| `protokt-codegen/.../MessageDocumentationAnnotator.kt` | Replace protobuf-java types |
| `protokt-codegen/.../EnumGenerator.kt` | Replace protobuf-java types |
| `protokt-codegen/build.gradle.kts` | Remove protobuf-java dependency |

---

## Verification

1. `./gradlew clean check` — all tests pass with self-hosted types
2. Generate code for a representative set of `.proto` files and diff against
   protobuf-java-based output — must be identical
3. `./gradlew verifyBootstrap` — checked-in bootstrap code matches compiler output
4. Conformance tests pass
5. gRPC integration tests pass (with whatever adapter approach is chosen)
