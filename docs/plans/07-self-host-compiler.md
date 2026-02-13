# Plan: Self-Host the Compiler (Remove protobuf-java from Codegen)

**Status:** Proposed
**Priority:** Medium
**Issue:** N/A

## Problem

The protokt code generator (`protokt-codegen`) depends on protobuf-java for two
distinct purposes:

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
  for parsing at codegen time. The runtime already has its own generated types for
  `FileDescriptorProto`, `DescriptorProto`, etc. in `protokt-core`.

- **Multiplatform story**: protobuf-java is JVM-only. If protokt ever wants to run
  codegen on non-JVM targets (e.g., a native `protoc` plugin binary), protobuf-java
  is a blocker.

- **Version coupling**: Users are forced to align their protobuf-java version with
  whatever protokt's codegen needs, which can cause classpath conflicts.

### Current protobuf-java usage (15 files in codegen)

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

### What already exists

protokt already generates its own types for the descriptor protos:

- `protokt-core` contains generated `FileDescriptorProto`, `DescriptorProto`,
  `FieldDescriptorProto`, `EnumDescriptorProto`, `ServiceDescriptorProto`,
  `MethodDescriptorProto`, `SourceCodeInfo`, etc.
- The tiny descriptors runtime (`Descriptors.kt` in `protokt-core`) wraps these
  with `FileDescriptor`, `Descriptor`, `EnumDescriptor`, `ServiceDescriptor`.
- `FileDescriptor.buildFrom()` already parses descriptor bytes into the protokt
  type system at runtime.

**What's missing**: the ability to parse **extensions** (custom options) from raw
protobuf bytes without protobuf-java's `ExtensionRegistry`.

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

/**
 * Registry of known extensions that allows deserializers to decode extension
 * fields instead of treating them as unknown fields.
 *
 * Extensions are identified by (containing message type, field number).
 * When a deserializer encounters a field number in the extensions range,
 * it consults the registry to find the extension's message type and
 * deserializes the value accordingly.
 */
interface ExtensionRegistry {
    /**
     * Look up an extension by the containing message's full protobuf type name
     * and the field number.
     *
     * Returns null if no extension is registered for this combination.
     */
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
`google.protobuf.FieldOptions`. On the wire, this is encoded as:

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

#### Extension storage on messages

Generated `*Options` messages (and potentially any message with extension ranges)
need a way to store parsed extensions. Two approaches:

**Option A: Extension map on the message** — Add an `extensions: Map<Int, Message>`
field to messages that declare extension ranges. This requires changes to the code
generator to detect `extensions` declarations in `.proto` files and add the field.

**Option B: Accessor on unknownFields** — Keep extensions in `unknownFields` as raw
bytes, but provide a typed accessor that lazily deserializes on access:

```kotlin
inline fun <reified T : Message> UnknownFieldSet.getExtension(
    fieldNumber: Int,
    deserializer: Deserializer<T>
): T? {
    val field = unknownFields[fieldNumber.toUInt()] ?: return null
    // field contains raw length-delimited bytes
    return deserializer.deserialize(field.varintOrBytes)  // need to add accessor
}
```

Option B is simpler and doesn't require changing generated message shapes, but
it re-parses on every access. Option A is cleaner but more invasive.

**Recommendation: Option A** for the `*Options` types specifically. These are
only used at codegen time, and the extension map makes access natural:

```kotlin
// Usage in codegen:
val protoktFieldOptions = fieldOptions.getExtension(1253, ProtoktProtos.FieldOptions)
```

#### Reader changes

The `Reader` interface (or its implementations) needs to accept an optional
`ExtensionRegistry`. When reading a message, if the current field number falls in
an extension range and the registry has a match, deserialize the extension value
as a typed message instead of an unknown field.

This could be done by:
- Adding an `extensionRegistry` parameter to `readMessage()` (breaking)
- Or having the registry be set on the Reader instance (non-breaking)
- Or having a separate `readMessageWithExtensions()` method

### Phase 2: Generate protokt types for `plugin.proto` and `protokt.proto`

Currently, `CodeGeneratorRequest` and `CodeGeneratorResponse` are protobuf-java
types from `com.google.protobuf.compiler.PluginProtos`. And `ProtoktProtos` is a
protobuf-java generated class from `protokt.proto`.

To self-host:
1. Add `google/protobuf/compiler/plugin.proto` to protokt's own build
2. Generate protokt types for `CodeGeneratorRequest`, `CodeGeneratorResponse`
3. Generate protokt types for `ProtoktProtos` (the custom option messages)
4. Register protokt option extensions in the extension registry

This creates a bootstrap problem: protokt needs to generate its own compiler input
types, but the compiler needs those types to run. Solutions:
- **Checked-in generated code**: Generate the bootstrap types once and check them in.
  Subsequent builds use the checked-in code. A CI step verifies the checked-in code
  matches what the current compiler would generate (similar to how protobuf-java
  handles this).
- **Two-stage build**: Build the compiler once with protobuf-java (stage 0), then
  use stage 0 to generate the self-hosted types, then rebuild the compiler with
  those types (stage 1).

**Recommendation: Checked-in generated code** for the bootstrap types. It's simpler,
and most protobuf implementations do this (protobuf-java, protobuf-go, prost, etc.).

### Phase 3: Migrate codegen to use protokt types

Replace all protobuf-java type references with the protokt equivalents:

| protobuf-java type | protokt equivalent |
|--------------------|--------------------|
| `com.google.protobuf.DescriptorProtos.FileDescriptorProto` | `protokt.v1.google.protobuf.FileDescriptorProto` |
| `com.google.protobuf.DescriptorProtos.DescriptorProto` | `protokt.v1.google.protobuf.DescriptorProto` |
| `com.google.protobuf.DescriptorProtos.FieldDescriptorProto` | `protokt.v1.google.protobuf.FieldDescriptorProto` |
| `com.google.protobuf.DescriptorProtos.EnumDescriptorProto` | `protokt.v1.google.protobuf.EnumDescriptorProto` |
| `com.google.protobuf.DescriptorProtos.OneofDescriptorProto` | `protokt.v1.google.protobuf.OneofDescriptorProto` |
| `com.google.protobuf.DescriptorProtos.ServiceDescriptorProto` | `protokt.v1.google.protobuf.ServiceDescriptorProto` |
| `com.google.protobuf.DescriptorProtos.MethodDescriptorProto` | `protokt.v1.google.protobuf.MethodDescriptorProto` |
| `com.google.protobuf.DescriptorProtos.SourceCodeInfo` | `protokt.v1.google.protobuf.SourceCodeInfo` |
| `com.google.protobuf.DescriptorProtos.*Options` | `protokt.v1.google.protobuf.*Options` |
| `com.google.protobuf.DescriptorProtos.FeatureSet` | `protokt.v1.google.protobuf.FeatureSet` |
| `com.google.protobuf.DescriptorProtos.Edition` | `protokt.v1.google.protobuf.Edition` |
| `com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest` | `protokt.v1.google.protobuf.compiler.CodeGeneratorRequest` |
| `com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse` | `protokt.v1.google.protobuf.compiler.CodeGeneratorResponse` |
| `com.toasttab.protokt.v1.ProtoktProtos.*` | `protokt.v1.ProtoktProtos.*` (self-generated) |

Key migration points:

- **`Main.kt`**: Parse `CodeGeneratorRequest` from stdin bytes using protokt's own
  deserializer with extension registry. Build `CodeGeneratorResponse` using protokt's
  own type and serialize to stdout.

- **`Types.kt`**: Replace all `DescriptorProtos.*Options` fields with protokt
  equivalents. Replace `ProtoktProtos.*Options` with self-generated equivalents.

- **`FieldParser.kt`**: Replace `FieldDescriptorProto.Type` and `.Label` enums.
  Replace `FeatureSet.FieldPresence`. Replace `getExtension()` calls with
  extension map lookups on the protokt option types.

- **`FileDescriptorEncoding.kt`**: Replace `toByteArray()` with `serialize()`.

- **`FileDescriptorResolver.kt`**: Replace `toBuilder().clearSourceCodeInfo().build()`
  with protokt's `copy {}` pattern. Replace `clearJsonName()` similarly.

- **`GrpcKotlinGeneratorSupport.kt`**: This is trickier — it bridges to
  grpc-kotlin's generator which expects protobuf-java types. This may need to
  remain as-is or have an adapter layer. Alternatively, gRPC stub generation
  could be kept as a separate plugin that retains the protobuf-java dependency.

### Phase 4: Remove protobuf-java dependency

Once all codegen code uses protokt types:
- Remove `protobuf-java` from `protokt-codegen/build.gradle.kts`
- Remove `ProtoktProtos` Java generated code
- Keep protobuf-java as an optional dependency only for `protokt-reflect` (which
  provides `toDynamicMessage()` interop for users who want it)

---

## Guava dependency

The codegen also uses `com.google.common.base.CaseFormat` (in `FieldParser.kt`)
for case conversion. This is a trivial dependency that can be replaced with a
small utility function.

---

## Risks and Considerations

- **Bootstrap complexity**: Self-hosting compilers always have a bootstrap story.
  Checked-in generated code is the simplest approach but requires discipline to
  keep in sync. A `./gradlew verifyBootstrap` task should be part of CI.

- **Feature parity**: protokt's generated types must correctly handle all features
  used by the codegen, including:
  - Extension fields (the main gap today)
  - `has*()` presence checks (proto2 optional fields in descriptor.proto)
  - Default values for proto2 fields
  - `oneof` fields in descriptor protos

- **Proto2 support**: `descriptor.proto` and `plugin.proto` are proto2. Protokt
  has "marginal" proto2 support. Self-hosting requires that proto2 features used
  in these specific files work correctly. This likely means:
  - `optional` fields with `has*()` checks
  - Default values for scalar fields
  - Extension fields (the main new feature)

  Protokt doesn't need to support ALL of proto2 — just the subset used in
  descriptor.proto, plugin.proto, and protokt.proto.

- **gRPC stub generation**: The grpc-kotlin generator integration expects
  protobuf-java types. Options:
  - Keep gRPC generation as a separate protoc plugin with its own protobuf-java dep
  - Write an adapter layer
  - Fork/vendor the relevant grpc-kotlin generator code

- **Performance**: Parsing `CodeGeneratorRequest` happens once per protoc
  invocation. Any performance difference between protobuf-java and protokt parsing
  is negligible in this context.

- **Editions support**: `descriptor.proto` uses proto2, but edition 2023 support
  in the codegen requires understanding `FeatureSet` and `FeatureSetDefaults`.
  These must work correctly in the self-hosted types.

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
| `protokt-codegen/.../FieldParser.kt` | Replace protobuf-java types, extension access |
| `protokt-codegen/.../MessageParser.kt` | Replace protobuf-java types |
| `protokt-codegen/.../EnumParser.kt` | Replace protobuf-java types |
| `protokt-codegen/.../ServiceParser.kt` | Replace protobuf-java types |
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
