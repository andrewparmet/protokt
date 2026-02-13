# Plan: Lazy/Caching String Fields (Phases 1+2)

## Context

Protokt currently converts string fields eagerly during both deserialization (`readString()` decodes UTF-8) and serialization (`write(String)` re-encodes UTF-8). For pass-through messages where string fields are never accessed, this is wasted work. The plan from `docs/plans/02-lazy-caching-wrapper-types.md` describes a `CachingReference` approach. This implements Phases 1 (runtime infrastructure) and 2 (codegen for string fields).

**Scope limitations:** Non-wrapped, non-repeated, non-map, non-oneof string fields only. Wrapped string fields (e.g., `LocalDateStringConverter`), repeated strings, map keys/values, and oneof string fields are unchanged. Wrapper types (Phase 3) and `OptimizedSizeOfConverter` deprecation (Phase 4) are future work.

---

## Phase 1: Runtime

### New file: `protokt-runtime/src/commonMain/kotlin/protokt/v1/CachingConverter.kt`

Interface for converters used with `CachingReference`. Singleton objects implement this — one per pair of types. Stored as a single reference in each `CachingReference` instance.

```kotlin
package protokt.v1

import kotlin.reflect.KClass

@OnlyForUseByGeneratedProtoCode
interface CachingConverter<WireT : Any, KotlinT : Any> {
    val wrapperClass: KClass<KotlinT>

    fun wrap(unwrapped: WireT): KotlinT
    fun unwrap(wrapped: KotlinT): WireT

    /** Write whichever form is currently cached, without forcing conversion. */
    fun writeTo(writer: Writer, value: Any)

    /** Compute serialized size from whichever form is currently cached. */
    fun sizeOf(value: Any): Int

    /** Check emptiness/default-ness from whichever form is currently cached. */
    fun isDefault(value: Any): Boolean
}
```

### New file: `protokt-runtime/src/commonMain/kotlin/protokt/v1/StringCachingConverter.kt`

Built-in converter for string fields. Both `String` and `Bytes` have native `Writer.write()` and `SizeCodecs.sizeOf()` support, so neither form needs conversion for serialization.

```kotlin
package protokt.v1

@OnlyForUseByGeneratedProtoCode
object StringCachingConverter : CachingConverter<Bytes, String> {
    override val wrapperClass = String::class

    override fun wrap(unwrapped: Bytes): String =
        unwrapped.value.decodeToString()

    override fun unwrap(wrapped: String): Bytes =
        Bytes(wrapped.encodeToByteArray())

    override fun writeTo(writer: Writer, value: Any) {
        if (value is Bytes) writer.write(value) else writer.write(value as String)
    }

    override fun sizeOf(value: Any): Int {
        return if (value is Bytes) SizeCodecs.sizeOf(value) else SizeCodecs.sizeOf(value as String)
    }

    override fun isDefault(value: Any): Boolean =
        if (value is Bytes) value.isEmpty() else (value as String).isEmpty()
}
```

**Why dynamic dispatch matters:** `CodedOutputStream.writeStringNoTag(String)` computes UTF-8 length and encodes directly into the output buffer — no intermediate `ByteArray` allocation. If we always converted `String→Bytes` before writing, we'd allocate an unnecessary `ByteArray`. Dynamic dispatch means:
- Pass-through (Bytes form): `write(Bytes)` → raw memcpy, `sizeOf(Bytes)` → O(1)
- Builder-created (String form): `write(String)` → direct encode (no alloc), `sizeOf(String)` → same as today

### New file: `protokt-runtime/src/commonMain/kotlin/protokt/v1/CachingReference.kt`

Generic caching reference, parameterized over wire and Kotlin types. Works for string fields now, wrapper types in Phase 3.

```kotlin
package protokt.v1

import kotlin.concurrent.Volatile

@OnlyForUseByGeneratedProtoCode
class CachingReference<WireT : Any, KotlinT : Any>(
    @Volatile private var ref: Any,
    private val converter: CachingConverter<WireT, KotlinT>
) {
    fun value(): KotlinT { /* lazy convert from wire form */ }
    fun wireValue(): WireT { /* lazy convert from Kotlin form */ }
    fun writeTo(writer: Writer) = converter.writeTo(writer, ref)
    fun sizeOf(): Int = converter.sizeOf(ref)
    fun isDefault(): Boolean = converter.isDefault(ref)
    fun isNotDefault(): Boolean = !isDefault()

    override fun equals(other: Any?): Boolean =
        other is CachingReference<*, *> && value() == other.value()
    override fun hashCode(): Int = value().hashCode()
    override fun toString(): String = value().toString()
}
```

**Key design points:**
- `@kotlin.concurrent.Volatile` for thread-safe benign races (same as `lazy(PUBLICATION)`)
- `converter` is a singleton object reference — no per-instance lambda allocation
- `writeTo(Writer)` and `sizeOf()` delegate to the converter, which dispatches on the current form without forcing conversion
- `KClass.isInstance()` distinguishes forms — works on all KMP targets
- `equals`/`hashCode`/`toString` use the Kotlin form (via `value()`)
- For string fields: `CachingReference<Bytes, String>` with `StringCachingConverter`
- For Phase 3 wrapper types: `CachingReference<Bytes, UUID>` with a `UuidCachingConverter`, etc.

### UTF-8 validation (expect/actual)

Proto3 conformance mandates eager UTF-8 validation at parse time. Rather than writing a custom byte-scanning validator, use platform facilities via expect/actual.

**`protokt-runtime/src/commonMain/kotlin/protokt/v1/Utf8.kt`** (expect):
```kotlin
package protokt.v1

internal expect fun validateUtf8(bytes: ByteArray)
```

**`protokt-runtime/src/jvmMain/kotlin/protokt/v1/Utf8.kt`** (actual):
```kotlin
package protokt.v1

import com.google.protobuf.Utf8 as ProtobufUtf8

internal actual fun validateUtf8(bytes: ByteArray) {
    if (!ProtobufUtf8.isValidUtf8(bytes)) {
        throw com.google.protobuf.InvalidProtocolBufferException("Invalid UTF-8")
    }
}
```

**`protokt-runtime/src/jsMain/kotlin/protokt/v1/Utf8.kt`** (actual):
```kotlin
package protokt.v1

internal actual fun validateUtf8(bytes: ByteArray) {
    // TextDecoder with fatal=true throws TypeError on invalid UTF-8.
    js("new TextDecoder('utf-8', {fatal: true}).decode(new Uint8Array(bytes))")
}
```

### No changes to `Reader`, `Writer`, `SizeCodecs`

Existing infrastructure already supports both forms:
- `Reader.readBytes()` returns `Bytes`
- `Writer.write(String)` encodes directly into output buffer (no intermediate alloc)
- `Writer.write(Bytes)` writes raw bytes (memcpy)
- `SizeCodecs.sizeOf(String)` computes UTF-8 byte length via code point scan
- `SizeCodecs.sizeOf(Bytes)` is O(1) via `bytes.value.size`

---

## Phase 2: Codegen

### Full before/after example

Consider a message with two string fields, an int, and a oneof containing a string:

```proto
message Example {
    string name = 1;
    int32 id = 2;
    string description = 3;
    oneof choice {
        string label = 4;
        int32 code = 5;
    }
}
```

**BEFORE (current generated code):**

```kotlin
@GeneratedMessage("testing.Example")
class Example private constructor(
    @GeneratedProperty(1)
    val name: String,
    @GeneratedProperty(2)
    val id: Int,
    @GeneratedProperty(3)
    val description: String,
    val choice: Choice?,
    val unknownFields: UnknownFieldSet = UnknownFieldSet.empty()
) : AbstractMessage() {

    private val `$messageSize`: Int by lazy {
        var result = 0
        if (name.isNotEmpty()) {
            result += sizeOf(10u) + sizeOf(name)
        }
        if (id != 0) {
            result += sizeOf(16u) + sizeOf(id)
        }
        if (description.isNotEmpty()) {
            result += sizeOf(26u) + sizeOf(description)
        }
        when (choice) {
            is Choice.Label ->
                result += sizeOf(34u) + sizeOf(choice.label)
            is Choice.Code ->
                result += sizeOf(40u) + sizeOf(choice.code)
            null -> Unit
        }
        result += unknownFields.size()
        result
    }

    override fun messageSize(): Int = `$messageSize`

    override fun serialize(writer: Writer) {
        if (name.isNotEmpty()) {
            writer.writeTag(10u).write(name)
        }
        if (id != 0) {
            writer.writeTag(16u).write(id)
        }
        if (description.isNotEmpty()) {
            writer.writeTag(26u).write(description)
        }
        when (choice) {
            is Choice.Label ->
                writer.writeTag(34u).write(choice.label)
            is Choice.Code ->
                writer.writeTag(40u).write(choice.code)
            null -> Unit
        }
        writer.writeUnknown(unknownFields)
    }

    // equals, hashCode, toString, copy, Choice, Builder, Deserializer ...
}
```

**AFTER (with `CachingReference<Bytes, String>`):**

Only `name` and `description` get caching — `id` (not a string), `choice.label` (in a oneof), and map/repeated fields are unchanged.

```kotlin
@GeneratedMessage("testing.Example")
class Example private constructor(
    private val _name: CachingReference<Bytes, String>,          // CHANGED
    @GeneratedProperty(2)
    val id: Int,
    private val _description: CachingReference<Bytes, String>,   // CHANGED
    val choice: Choice?,
    val unknownFields: UnknownFieldSet = UnknownFieldSet.empty()
) : AbstractMessage() {

    @GeneratedProperty(1)
    val name: String get() = _name.value()                       // CHANGED

    @GeneratedProperty(3)
    val description: String get() = _description.value()         // CHANGED

    private val `$messageSize`: Int by lazy {
        var result = 0
        if (_name.isNotDefault()) {                              // CHANGED
            result += sizeOf(10u) + _name.sizeOf()              // CHANGED
        }
        if (id != 0) {
            result += sizeOf(16u) + sizeOf(id)
        }
        if (_description.isNotDefault()) {                       // CHANGED
            result += sizeOf(26u) + _description.sizeOf()       // CHANGED
        }
        // oneof unchanged
        when (choice) {
            is Choice.Label ->
                result += sizeOf(34u) + sizeOf(choice.label)
            is Choice.Code ->
                result += sizeOf(40u) + sizeOf(choice.code)
            null -> Unit
        }
        result += unknownFields.size()
        result
    }

    override fun serialize(writer: Writer) {
        if (_name.isNotDefault()) {                              // CHANGED
            writer.writeTag(10u)                                 // CHANGED: tag separate
            _name.writeTo(writer)                                // CHANGED: dynamic dispatch
        }
        if (id != 0) {
            writer.writeTag(16u).write(id)
        }
        if (_description.isNotDefault()) {                       // CHANGED
            writer.writeTag(26u)                                 // CHANGED
            _description.writeTo(writer)                         // CHANGED
        }
        // oneof unchanged
        when (choice) {
            is Choice.Label ->
                writer.writeTag(34u).write(choice.label)
            is Choice.Code ->
                writer.writeTag(40u).write(choice.code)
            null -> Unit
        }
        writer.writeUnknown(unknownFields)
    }

    // equals/hashCode/toString use public `name`/`description` properties
    // which call .value() — triggers lazy decode if needed. No codegen change.

    fun copy(builder: Builder.() -> Unit): Example =
        Builder().apply {
            name = this@Example.name                             // reads .value()
            id = this@Example.id
            description = this@Example.description
            choice = this@Example.choice
            unknownFields = this@Example.unknownFields
            builder()
        }.build()

    @BuilderDsl
    class Builder {
        var name: String = ""                                    // unchanged public API
        var id: Int = 0
        var description: String = ""
        var choice: Choice? = null
        var unknownFields: UnknownFieldSet = UnknownFieldSet.empty()

        fun build(): Example = Example(
            CachingReference(name, StringCachingConverter),       // CHANGED
            id,
            CachingReference(description, StringCachingConverter), // CHANGED
            choice,
            unknownFields
        )
    }

    companion object Deserializer : AbstractDeserializer<Example>() {
        @JvmStatic
        override fun deserialize(reader: Reader): Example {
            var name: Bytes? = null                              // CHANGED
            var id = 0
            var description: Bytes? = null                       // CHANGED
            var choice: Choice? = null
            var unknownFields: UnknownFieldSet.Builder? = null

            while (true) {
                when (reader.readTag()) {
                    0u -> return Example(
                        name?.let { CachingReference(it, StringCachingConverter) }
                            ?: CachingReference("", StringCachingConverter),
                        id,
                        description?.let { CachingReference(it, StringCachingConverter) }
                            ?: CachingReference("", StringCachingConverter),
                        choice,
                        UnknownFieldSet.from(unknownFields)
                    )
                    10u -> name = reader.readBytes()              // CHANGED
                        .also { validateUtf8(it.value) }
                    16u -> id = reader.readInt32()
                    26u -> description = reader.readBytes()       // CHANGED
                        .also { validateUtf8(it.value) }
                    34u -> choice = Choice.Label(reader.readString()) // unchanged (oneof)
                    40u -> choice = Choice.Code(reader.readInt32())
                    else -> unknownFields =
                        (unknownFields ?: UnknownFieldSet.Builder()).also {
                            it.add(reader.readUnknown())
                        }
                }
            }
        }
    }
}
```

### Pass-through flow

If a message is deserialized and re-serialized without accessing `name` or `description`:
1. Deser: `readBytes()` → raw `Bytes` stored in `CachingReference`
2. UTF-8 validated (byte scan, no String allocation)
3. Serialization: `_name.writeTo(writer)` → `StringCachingConverter.writeTo()` → detects Bytes form → `writer.write(Bytes)` (raw memcpy)
4. `_name.sizeOf()` → `StringCachingConverter.sizeOf()` → detects Bytes form → `bytes.value.size` (O(1))

No `String` object is ever created. No UTF-8 encoding/decoding occurs after the initial validation scan.

### Builder-created flow

If a message is built via the Builder (String form):
1. Builder: `CachingReference("hello", StringCachingConverter)` stores a String
2. Serialization: `_name.writeTo(writer)` → detects String form → `writer.write(String)` → `CodedOutputStream.writeStringNoTag()` encodes directly into output buffer (no intermediate `ByteArray` allocation)
3. `_name.sizeOf()` → detects String form → `SizeCodecs.sizeOf(String)` (same as today)

### Codegen file changes

**`PropertyAnnotator.kt`** — Add `cachingString: Boolean` to `PropertyInfo`:
- Set `true` when `type == String && !repeated && !isMap && !wrapped` and field is not in a oneof
- `deserializeType` becomes `Bytes?` for caching string fields

**`MessageGenerator.kt`** — For `cachingString` properties, generate:
- Private constructor param: `_name: CachingReference<Bytes, String>` (no annotation)
- Public delegate property: `@GeneratedProperty(N) val name: String get() = _name.value()`
- `equals`/`hashCode`/`toString` use public property — no changes needed

**`DeserializerGenerator.kt`**:
- Declare `var name: Bytes? = null` instead of `var name = ""`
- Read via `reader.readBytes().also { validateUtf8(it.value) }` instead of `reader.readString()`
- Construct `name?.let { CachingReference(it, StringCachingConverter) } ?: CachingReference("", StringCachingConverter)`

**`SerializerGenerator.kt`**:
- For caching string fields, generate `_name.writeTo(writer)` instead of `writer.write(name)`
- Tag write is split: `writer.writeTag(10u)` then `_name.writeTo(writer)` (no chaining)

**`SerializeAndSizeSupport.kt`** — In `nonDefault()`:
- Early return for caching string: `CodeBlock.of("%N.isNotDefault()", "_$fieldName")`

**`MessageSizeGenerator.kt`**:
- For caching string fields, generate `_name.sizeOf()` instead of `sizeOf(name)`

**`BuilderGenerator.kt`**:
- Builder property stays `var name: String = ""` (public API unchanged)
- `build()` wraps: `CachingReference(name, StringCachingConverter)`

### Helper function (shared across codegen files)

```kotlin
internal fun isCachingString(f: StandardField): Boolean =
    f.type == FieldType.String && !f.repeated && !f.isMap && !f.wrapped
```

---

## Verification

1. Run `./gradlew clean check` — all existing serialization/deserialization round-trip tests should pass
2. Run conformance tests — validates UTF-8 enforcement at parse time
3. Inspect generated code (e.g., `build/generated/sources/proto/`) to verify the `CachingReference` pattern
4. Add runtime unit tests for `CachingReference` (lazy conversion, caching, thread safety, equality) and `StringCachingConverter` (dynamic dispatch, default detection)
