# Plan: Protobuf Canonical JSON Encoding

**Status:** Proposed
**Priority:** Medium
**Issue:** N/A

## Problem

Protokt has no JSON serialization or deserialization support. The only wire format
is binary protobuf. Users who need JSON (for REST APIs, debugging, logging,
interop with non-protobuf systems) must either:

- Use `protokt-reflect` to convert to a protobuf-java `DynamicMessage`, then use
  protobuf-java-util's `JsonFormat` — heavyweight, JVM-only, requires protobuf-java
- Write manual Jackson/Gson/kotlinx.serialization adapters — tedious, error-prone,
  doesn't follow the protobuf JSON spec
- Use a different protobuf library entirely for JSON needs

The protobuf spec defines a
[canonical JSON encoding](https://protobuf.dev/programming-guides/proto3/#json)
that all conformant implementations should support. This encoding has specific
rules for field naming, type representation, null handling, and special formatting
for well-known types.

---

## Goals

1. Native JSON serialization and deserialization for all protokt message types
2. Full compliance with the protobuf canonical JSON spec
3. Multiplatform support (JVM, JS, Native) — JSON is especially important for
   JS/browser use cases
4. No dependency on protobuf-java or protobuf-java-util
5. Integration with the existing converter/wrapper type system

---

## Protobuf JSON Spec Summary

### Field naming
- Proto field names are converted to **lowerCamelCase** for JSON
- The `json_name` field option overrides the default name
- Parsers should accept both the lowerCamelCase name and the original proto field name

### Scalar types

| Proto type | JSON type | Notes |
|-----------|-----------|-------|
| `bool` | boolean | |
| `int32`, `sint32`, `sfixed32` | number | |
| `uint32`, `fixed32` | number | |
| `int64`, `sint64`, `sfixed64` | string | 64-bit integers are strings in JSON to avoid precision loss |
| `uint64`, `fixed64` | string | Same |
| `float`, `double` | number | `NaN`, `Infinity`, `-Infinity` as strings `"NaN"`, `"Infinity"`, `"-Infinity"` |
| `string` | string | |
| `bytes` | string | Base64-encoded (standard alphabet with padding) |
| `enum` | string | Enum value name. Unknown enum values as integer. |

### Message types
- JSON object
- `null` represents the default value for the field's type

### Repeated fields
- JSON array

### Map fields
- JSON object. Keys are always strings (proto int/bool keys are stringified).
- Sorted by key in canonical encoding.

### Oneof fields
- At most one field set. Represented as the single set field.

### Default value omission
- Fields with default values are **omitted** by default in JSON output
- A serialization option should allow including fields with default values

### Well-known type special encodings

| Type | JSON representation |
|------|-------------------|
| `google.protobuf.Timestamp` | RFC 3339 string: `"1972-01-01T10:00:20.021Z"` |
| `google.protobuf.Duration` | Seconds with fractional: `"1.000340012s"` |
| `google.protobuf.Struct` | JSON object directly |
| `google.protobuf.Value` | The JSON value directly (string, number, bool, null, object, array) |
| `google.protobuf.ListValue` | JSON array directly |
| `google.protobuf.FieldMask` | Comma-separated camelCase paths: `"foo,bar.baz"` |
| `google.protobuf.Any` | Object with `@type` field: `{"@type": "type.url", ...fields...}` |
| `google.protobuf.BoolValue` | `true`/`false` or `null` |
| `google.protobuf.*Value` (wrappers) | The value directly, or `null` |
| `google.protobuf.Empty` | `{}` |

---

## Proposed Architecture

### New module: `protokt-json`

A multiplatform module that provides JSON serialization/deserialization. No new
code generation needed — uses the existing descriptor information and reflection.

Dependencies:
- `protokt-runtime` (for `Message`, `Deserializer`, `Bytes`, etc.)
- `protokt-core` (for well-known types and descriptors)
- `kotlinx-serialization-json` (as the underlying JSON reader/writer)

**Why kotlinx-serialization-json and not a hand-rolled parser:**
- Multiplatform out of the box (JVM, JS, Native)
- Handles JSON parsing edge cases (Unicode escapes, number precision, streaming)
- Well-maintained and widely used in the Kotlin ecosystem
- We use it as a JSON token reader/writer, not its serialization framework —
  protokt controls the field mapping, type coercion, and spec compliance

### Core API

```kotlin
package protokt.v1.json

/**
 * Serializes a protokt message to its canonical JSON representation.
 */
fun <T : Message> T.toJson(options: JsonOptions = JsonOptions.DEFAULT): String

/**
 * Deserializes a protokt message from its JSON representation.
 */
fun <T : Message> Deserializer<T>.fromJson(json: String, options: JsonOptions = JsonOptions.DEFAULT): T

class JsonOptions(
    /** Include fields with default values in output. Default: false. */
    val includeDefaultValues: Boolean = false,
    /** Use proto field names instead of lowerCamelCase. Default: false. */
    val useProtoFieldNames: Boolean = false,
    /** Pretty-print with indentation. Default: false. */
    val prettyPrint: Boolean = false,
    /** Ignore unknown fields during parsing. Default: true. */
    val ignoreUnknownFields: Boolean = true,
) {
    companion object {
        val DEFAULT = JsonOptions()
    }
}
```

### Implementation approach: Descriptor-driven reflection

Rather than generating JSON-specific code for each message, use the existing
file descriptors and Kotlin reflection to serialize/deserialize generically.

At runtime, protokt already has:
- `FileDescriptor` → `Descriptor` tree with field metadata
- `Deserializer` companion objects on every message
- Field names and numbers from the descriptor protos
- The descriptor proto includes `json_name` for each field

The JSON serializer walks the descriptor's field list, reads each property value
from the message via Kotlin reflection (`KProperty`), and writes the JSON
representation. The deserializer reads JSON tokens, maps field names (camelCase
or proto name) to field numbers, and constructs the message.

#### Field metadata needed for JSON

The JSON codec needs, per field:
- Field name (proto name, json_name / camelCase name)
- Field number
- Wire type (to determine JSON representation)
- Whether the field is repeated, map, oneof, optional
- For enum fields: the enum value name mapping
- For message fields: the nested message's deserializer

Most of this is available from the `FieldDescriptorProto` in the file descriptor.
The main gap is that `json_name` is currently **stripped** from the encoded file
descriptor (see `FileDescriptorResolver.kt:clearJsonInfo()`). This stripping
saves binary size in the runtime descriptor but must be made optional or reversed
for JSON support.

**Options:**
1. Stop stripping `json_name` from descriptors (increases descriptor size slightly)
2. Recompute `json_name` from the proto field name at runtime using the standard
   algorithm (proto field `foo_bar_baz` → `fooBarBaz`)
3. Generate a separate JSON metadata structure

**Recommendation: Option 2** — the `json_name` computation algorithm is simple and
well-specified. Recomputing it avoids increasing descriptor size for users who
don't use JSON. For fields with a custom `json_name` set explicitly in the `.proto`
file, we'll need to preserve that. We can add a flag to the codegen:
`generate_json_names = true` which keeps `json_name` in the descriptor when set
to a non-default value.

### Well-known type handlers

Each WKT with special JSON encoding gets a dedicated handler:

```kotlin
internal interface WellKnownTypeJsonHandler<T : Message> {
    fun serialize(value: T, writer: JsonWriter, options: JsonOptions)
    fun deserialize(reader: JsonReader, options: JsonOptions): T
}
```

Handlers for:
- `TimestampJsonHandler` — RFC 3339 formatting/parsing
- `DurationJsonHandler` — seconds with fractional `"1.5s"` format
- `StructJsonHandler` — passthrough JSON object
- `ValueJsonHandler` — polymorphic JSON value
- `ListValueJsonHandler` — passthrough JSON array
- `FieldMaskJsonHandler` — comma-separated camelCase paths
- `AnyJsonHandler` — `@type` URL dispatch
- `*ValueJsonHandler` (wrappers) — unwrap to naked JSON value

These are registered in a map keyed by full protobuf type name. When the
serializer encounters a message field, it checks for a WKT handler first.

### `Any` type handling

`google.protobuf.Any` requires a **type registry** to resolve `@type` URLs to
message deserializers. The JSON codec needs a registry:

```kotlin
class TypeRegistry {
    private val types: Map<String, Deserializer<out Message>>

    fun findType(typeUrl: String): Deserializer<out Message>?

    class Builder {
        fun add(typeUrl: String, deserializer: Deserializer<out Message>): Builder
        fun addAllFrom(fileDescriptor: FileDescriptor): Builder
        fun build(): TypeRegistry
    }
}
```

Users register their message types if they use `Any`:

```kotlin
val registry = TypeRegistry.Builder()
    .add("type.googleapis.com/my.package.MyMessage", MyMessage)
    .build()

val options = JsonOptions(typeRegistry = registry)
message.toJson(options)
```

### Interaction with wrapper types (converters)

Protokt's wrapper type system (e.g., `bytes` → `UUID`, `string` → `LocalDate`)
is orthogonal to JSON encoding. The JSON codec operates at the **protobuf level**,
not the Kotlin level:

- Serialization: unwrap the Kotlin type to the protobuf type, then JSON-encode
  the protobuf type. `UUID` → `bytes` → base64 string.
- Deserialization: JSON-decode to the protobuf type, then wrap. Base64 string →
  `bytes` → `UUID`.

For the `LazyReference`-based caching fields, the JSON serializer should use
`wireValue()` to get the protobuf representation and encode that.

Users who want `UUID` to serialize as `"550e8400-..."` instead of base64 can
use a custom JSON serialization layer on top; the protobuf JSON spec doesn't
know about application-level wrapper types.

---

## Detailed Component Design

### JsonWriter (internal)

Wraps `kotlinx.serialization.json.JsonElement` building or streaming:

```kotlin
internal class JsonWriter {
    fun beginObject()
    fun endObject()
    fun beginArray()
    fun endArray()
    fun name(name: String)
    fun value(v: String)
    fun value(v: Boolean)
    fun value(v: Int)
    fun value(v: Long)
    fun value(v: UInt)
    fun value(v: ULong)
    fun value(v: Float)
    fun value(v: Double)
    fun nullValue()
}
```

### JsonReader (internal)

Wraps `kotlinx.serialization.json.JsonElement` traversal:

```kotlin
internal class JsonReader {
    fun beginObject()
    fun endObject()
    fun beginArray()
    fun endArray()
    fun nextName(): String
    fun nextString(): String
    fun nextBoolean(): Boolean
    fun nextInt(): Int
    fun nextLong(): Long
    fun nextDouble(): Double
    fun nextNull()
    fun peek(): JsonToken
    fun skipValue()
}
```

### MessageJsonSerializer (internal)

Generic message serializer driven by descriptors:

```kotlin
internal class MessageJsonSerializer(
    private val options: JsonOptions,
    private val typeRegistry: TypeRegistry
) {
    fun serialize(message: Message, descriptor: Descriptor, writer: JsonWriter) {
        writer.beginObject()
        for (field in descriptor.proto.field) {
            val value = readField(message, field)
            if (shouldInclude(value, field)) {
                writer.name(jsonFieldName(field))
                serializeValue(value, field, writer)
            }
        }
        writer.endObject()
    }

    private fun serializeValue(value: Any?, field: FieldDescriptorProto, writer: JsonWriter) {
        when {
            value == null -> writer.nullValue()
            field.isRepeated && field.isMap -> serializeMap(value, field, writer)
            field.isRepeated -> serializeRepeated(value, field, writer)
            field.type == TYPE_MESSAGE -> serializeMessage(value as Message, field, writer)
            field.type == TYPE_ENUM -> serializeEnum(value, field, writer)
            else -> serializeScalar(value, field, writer)
        }
    }

    // ... scalar, enum, message, repeated, map serialization
}
```

### MessageJsonDeserializer (internal)

Generic message deserializer driven by descriptors:

```kotlin
internal class MessageJsonDeserializer(
    private val options: JsonOptions,
    private val typeRegistry: TypeRegistry
) {
    fun <T : Message> deserialize(
        deserializer: Deserializer<T>,
        descriptor: Descriptor,
        reader: JsonReader
    ): T {
        // Build a field name → field descriptor lookup (both json_name and proto name)
        val fieldsByName = buildFieldLookup(descriptor)

        // Read JSON object into a map of field number → value
        reader.beginObject()
        val values = mutableMapOf<Int, Any?>()
        while (reader.peek() != END_OBJECT) {
            val name = reader.nextName()
            val field = fieldsByName[name]
            if (field == null) {
                if (options.ignoreUnknownFields) reader.skipValue()
                else error("Unknown field: $name")
                continue
            }
            values[field.number] = deserializeValue(field, reader)
        }
        reader.endObject()

        // Construct message from values using binary deserialization as the
        // construction mechanism (serialize to binary, then deserialize).
        // Alternative: use reflection to call the constructor directly.
        return constructMessage(deserializer, descriptor, values)
    }
}
```

#### Message construction strategy

Two options for constructing a message from parsed JSON values:

**Option A: Round-trip through binary.** Serialize the parsed values to a binary
protobuf byte array, then deserialize using the existing `Deserializer`. Simple,
correct, but adds overhead.

**Option B: Reflective constructor call.** Use Kotlin reflection to invoke the
message's primary constructor with the parsed values. More efficient but fragile —
constructor parameter order must match field order, and internal constructor
signatures are a codegen implementation detail.

**Option C: Generate a `fromValues(Map<Int, Any?>)` factory.** Add a generated
method that constructs the message from a field-number-to-value map. Clean API,
efficient, but requires code generation changes.

**Recommendation: Start with Option A** for correctness, then optimize to Option C
if performance matters. Option A is simplest and guaranteed correct because it
reuses the existing battle-tested deserialization path.

---

## Module Structure

```
protokt-json/
├── build.gradle.kts
├── src/
│   ├── commonMain/kotlin/protokt/v1/json/
│   │   ├── Json.kt                      # Public API (toJson, fromJson)
│   │   ├── JsonOptions.kt               # Configuration
│   │   ├── TypeRegistry.kt              # Any type resolution
│   │   ├── MessageJsonSerializer.kt     # Generic serializer
│   │   ├── MessageJsonDeserializer.kt   # Generic deserializer
│   │   ├── JsonFieldNaming.kt           # proto name → camelCase conversion
│   │   ├── ScalarJsonCodecs.kt          # Scalar type encoding/decoding
│   │   └── wellknown/
│   │       ├── WellKnownTypeHandler.kt  # Handler interface
│   │       ├── TimestampHandler.kt
│   │       ├── DurationHandler.kt
│   │       ├── StructHandler.kt
│   │       ├── ValueHandler.kt
│   │       ├── FieldMaskHandler.kt
│   │       ├── AnyHandler.kt
│   │       └── WrappersHandler.kt       # BoolValue, StringValue, etc.
│   ├── commonTest/kotlin/protokt/v1/json/
│   │   ├── ScalarJsonTest.kt
│   │   ├── MessageJsonTest.kt
│   │   ├── WellKnownTypeJsonTest.kt
│   │   └── ConformanceJsonTest.kt
│   └── jvmTest/kotlin/protokt/v1/json/
│       └── JsonFormatComparisonTest.kt  # Compare output with protobuf-java-util
```

### Dependencies

```kotlin
// protokt-json/build.gradle.kts
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":protokt-core"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(project(":testing:protokt-generation"))
            }
        }
        jvmTest {
            dependencies {
                // For comparison testing against reference implementation
                implementation(libs.protobuf.java.util)
            }
        }
    }
}
```

---

## Conformance Testing

The protobuf conformance test suite includes JSON test cases. Protokt already has
a conformance runner infrastructure in `testing/conformance/`. Extending it to
support JSON:

1. Handle `JSON_INPUT` and `JSON_OUTPUT` in the conformance runner
2. Use `toJson()` / `fromJson()` for serialization/deserialization
3. Report results for the JSON test cases

This provides automated verification of spec compliance.

---

## Phasing

### Phase 1: Core JSON codec
- Implement scalar type serialization/deserialization
- Implement message, repeated, map, oneof serialization
- Field naming (camelCase conversion, both-name parsing)
- Default value omission
- Basic test suite against hand-verified expected output

### Phase 2: Well-known type handlers
- Implement all WKT special JSON encodings
- `Any` type with type registry
- Wrapper types (null-as-default semantics)

### Phase 3: Conformance
- Wire up conformance test runner for JSON
- Fix any spec compliance issues found by conformance tests
- Compare output with protobuf-java-util for a large corpus of messages

### Phase 4: Performance and polish
- Streaming serialization (avoid building full `JsonElement` tree)
- Performance benchmarks
- Public API stabilization

---

## Risks and Considerations

- **Reflection on common/JS/Native**: Kotlin reflection (`KProperty`, etc.) works
  differently across platforms. The descriptor-driven approach avoids heavy
  reflection — field access can use generated accessor functions rather than
  `KProperty` lookups. May need an expect/actual layer for property access.

- **Descriptor availability**: JSON serialization requires file descriptors at
  runtime. Today, descriptors are generated when `generateDescriptors = true`
  (the default). Users who disable descriptor generation can't use JSON — this
  should be documented and validated at runtime.

- **64-bit integer precision**: The spec requires `int64`/`uint64` values to be
  JSON strings. `kotlinx-serialization-json` handles this correctly via
  `JsonPrimitive(string)` vs `JsonPrimitive(number)`.

- **Float special values**: `NaN`, `Infinity`, `-Infinity` must be JSON strings,
  not bare tokens. Most JSON libraries don't allow bare `NaN` in output, so this
  aligns naturally.

- **`json_name` availability**: Currently stripped from encoded descriptors.
  Phase 1 can use the standard camelCase algorithm for default names. Custom
  `json_name` support requires either preserving them in descriptors or a
  separate metadata structure.

- **Performance vs. generated code**: A reflection/descriptor-driven approach is
  simpler to implement but slower than generated JSON code. For most use cases
  (REST APIs, debugging) this is fine. If performance is critical, a future
  phase could add generated JSON serializers as an opt-in codegen feature.

- **`Any` type chicken-and-egg**: Serializing `Any` requires knowing the message
  type at runtime. The type registry pattern is standard (protobuf-java-util uses
  the same approach). Users must register types they expect to encounter.

---

## Files to create

| File | Description |
|------|-------------|
| `protokt-json/build.gradle.kts` | Module build configuration |
| `settings.gradle.kts` | Add `protokt-json` module |
| `protokt-json/src/commonMain/kotlin/protokt/v1/json/*.kt` | Core JSON codec |
| `protokt-json/src/commonMain/kotlin/protokt/v1/json/wellknown/*.kt` | WKT handlers |
| `testing/conformance/runner/src/.../ConformanceRunner.kt` | JSON conformance support |

## Files to modify

| File | Change |
|------|--------|
| `protokt-codegen/.../FileDescriptorResolver.kt` | Option to preserve `json_name` |
| `gradle/libs.versions.toml` | Add kotlinx-serialization-json |
