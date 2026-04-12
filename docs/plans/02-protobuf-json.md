# Plan: Protobuf Canonical JSON Encoding

**Status:** Proposed
**Priority:** Medium — high impact for adoption; every major protobuf library supports JSON
**Depends on:** None (can proceed independently of self-hosting)

## Problem

Protokt has no JSON serialization or deserialization support. Users who need JSON
(REST APIs, debugging, logging, interop with non-protobuf systems) must either use
`protokt-reflect` to round-trip through protobuf-java `DynamicMessage` + `JsonFormat`
(heavyweight, JVM-only), or write manual adapters.

The protobuf spec defines a
[canonical JSON encoding](https://protobuf.dev/programming-guides/proto3/#json)
that all conformant implementations should support.

---

## Proposed Architecture

### New module: `protokt-json`

A multiplatform module providing JSON serialization/deserialization. No new code
generation needed — uses existing descriptor information and reflection.

Dependencies:
- `protokt-runtime` (for `Message`, `Deserializer`, `Bytes`, etc.)
- `protokt-core` (for well-known types and descriptors)
- `kotlinx-serialization-json` (underlying JSON reader/writer only — not using its
  serialization framework)

### Core API

```kotlin
package protokt.v1.json

fun <T : Message> T.toJson(options: JsonOptions = JsonOptions.DEFAULT): String

fun <T : Message> Deserializer<T>.fromJson(
    json: String,
    options: JsonOptions = JsonOptions.DEFAULT
): T

class JsonOptions(
    val includeDefaultValues: Boolean = false,
    val useProtoFieldNames: Boolean = false,
    val prettyPrint: Boolean = false,
    val ignoreUnknownFields: Boolean = true,
    val typeRegistry: TypeRegistry = TypeRegistry.EMPTY
)
```

### Implementation: descriptor-driven reflection

The JSON codec walks the file descriptor's field list, reads each property value
from the message via Kotlin reflection, and writes the JSON representation. The
deserializer reads JSON tokens, maps field names to field numbers, and constructs
the message.

Field metadata needed per field: name (proto name + json_name/camelCase), field
number, wire type, repeated/map/oneof/optional flags, nested message deserializer,
enum value name mapping.

#### `json_name` handling

Currently `json_name` is stripped from encoded file descriptors
(`FileDescriptorResolver.kt:clearJsonInfo()`). Two options:

1. Stop stripping `json_name` (increases descriptor size slightly)
2. Recompute from proto field name using the standard algorithm
   (`foo_bar_baz` → `fooBarBaz`); preserve only custom `json_name` values

Option 2 is recommended — avoids penalizing users who don't use JSON. A codegen
flag `generate_json_names = true` can keep custom `json_name` when set.

### Well-known type handlers

Each WKT with special JSON encoding gets a dedicated handler:

| Type | JSON representation |
|------|-------------------|
| `Timestamp` | RFC 3339 string |
| `Duration` | Seconds with fractional: `"1.000340012s"` |
| `Struct` | JSON object directly |
| `Value` | The JSON value directly |
| `ListValue` | JSON array directly |
| `FieldMask` | Comma-separated camelCase paths |
| `Any` | Object with `@type` field (requires `TypeRegistry`) |
| `*Value` wrappers | The value directly, or `null` |
| `Empty` | `{}` |

### `Any` type handling

Requires a type registry to resolve `@type` URLs to message deserializers:

```kotlin
class TypeRegistry {
    fun findType(typeUrl: String): Deserializer<out Message>?

    class Builder {
        fun add(typeUrl: String, deserializer: Deserializer<out Message>): Builder
        fun build(): TypeRegistry
    }

    companion object {
        val EMPTY: TypeRegistry
    }
}
```

### Interaction with wrapper types (converters)

The JSON codec operates at the protobuf level, not the Kotlin level:
- Serialization: `LazyReference.wireValue()` → JSON-encode the protobuf type
- Deserialization: JSON-decode to protobuf type → construct via normal path

Users who want `UUID` to serialize as `"550e8400-..."` instead of base64 can use
a custom JSON layer on top; the protobuf JSON spec doesn't know about
application-level wrapper types.

### Message construction strategy

Start with round-trip through binary (serialize parsed JSON values to binary
protobuf, then deserialize using existing `Deserializer`) for correctness. Optimize
later to direct reflective construction or a generated `fromValues(Map<Int, Any?>)`
factory if performance matters.

---

## Phasing

### Phase 1: Core JSON codec
- Scalar type serialization/deserialization
- Message, repeated, map, oneof serialization
- Field naming (camelCase conversion, both-name parsing)
- Default value omission

### Phase 2: Well-known type handlers
- All WKT special JSON encodings
- `Any` type with type registry
- Wrapper types (null-as-default semantics)

### Phase 3: Conformance
- Wire up conformance test runner for JSON (`JSON_INPUT`/`JSON_OUTPUT`)
- Fix spec compliance issues
- Compare output with protobuf-java-util for a large corpus

### Phase 4: Performance and polish
- Streaming serialization (avoid full `JsonElement` tree)
- Performance benchmarks
- Public API stabilization

---

## Risks and Considerations

- **Reflection on common/JS/Native**: Descriptor-driven approach avoids heavy
  reflection. Field access can use generated accessor functions rather than
  `KProperty` lookups. May need expect/actual for property access.

- **Descriptor availability**: Requires `generateDescriptors = true` (the default).
  Users who disable descriptor generation can't use JSON — document and validate at
  runtime.

- **64-bit integer precision**: Spec requires `int64`/`uint64` as JSON strings.
  `kotlinx-serialization-json` handles this correctly.

- **Performance vs. generated code**: Reflection/descriptor-driven approach is slower
  than generated JSON code. Fine for most use cases. A future phase could add
  generated JSON serializers as opt-in codegen.

---

## Files to create/modify

| File | Description |
|------|-------------|
| `protokt-json/build.gradle.kts` | Module build configuration |
| `settings.gradle.kts` | Add `protokt-json` module |
| `protokt-json/src/commonMain/kotlin/protokt/v1/json/*.kt` | Core JSON codec |
| `protokt-json/src/commonMain/kotlin/protokt/v1/json/wellknown/*.kt` | WKT handlers |
| `testing/conformance/...` | JSON conformance support |
| `protokt-codegen/.../FileDescriptorResolver.kt` | Option to preserve `json_name` |
| `gradle/libs.versions.toml` | Add kotlinx-serialization-json |
