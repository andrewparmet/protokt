# Plan: Simplify Caching Constructor + Extend to Wrapper Types

**Status:** Proposed
**Depends on:** 04-lazy-caching-string-fields.md (Phases 1+2)

## Context

Phases 1+2 (runtime infrastructure + string field caching) are implemented. This plan covers three changes:

1. **Simplify the constructor pattern** — Replace the `_pkt_` dual-parameter approach (designed for Jackson compatibility) with a single `CachingReference` constructor parameter. Jackson constructor compatibility is not a concern; the constructor is private.

2. **Extend caching to wrapper types** — `bytes`-wrapped (e.g., UUID, InetAddress) and `string`-wrapped (e.g., LocalDate) fields get the same deferred-conversion treatment.

3. **Fix the nullability model** — Non-optional wrapper fields with `acceptsDefaultValue = false` (e.g., UUID, LocalDate) currently generate nullable properties. This is inconsistent with proto3 semantics. With caching, these become non-null — deserialization stores raw bytes and defers `wrap()` to access time. Validation is a separate concern (protovalidate).

4. **Deprecate `OptimizedSizeOfConverter`** — Subsumed by caching.

---

## Constructor Simplification

### Before (Phase 2 — dual `_pkt_` params):

```kotlin
class Person private constructor(
    name: String,                                              // positional (for Jackson)
    @GeneratedProperty(2) val id: Int,
    val unknownFields: UnknownFieldSet = UnknownFieldSet.empty(),
    _pkt_name: CachingReference<Bytes, String>? = null         // trailing
) {
    private val _name = _pkt_name ?: CachingReference(name, StringCachingConverter)
    @GeneratedProperty(1) val name: String get() = _name.value()
}
```

### After (single CachingReference param):

```kotlin
class Person private constructor(
    private val _name: CachingReference<Bytes, String>,
    @GeneratedProperty(2) val id: Int,
    val unknownFields: UnknownFieldSet = UnknownFieldSet.empty()
) {
    @GeneratedProperty(1) val name: String get() = _name.value()
}
```

- **Deserializer**: `Person(CachingReference(bytes ?: Bytes.empty(), StringCachingConverter), id, ...)`
- **Builder**: `Person(CachingReference(name, StringCachingConverter), id, ...)`
- No trailing params, no empty string dummies, no `_pkt_` prefix.

---

## Runtime Additions

### `BytesWrappedCachingConverter`

Adapts `Converter<Bytes, KotlinT>` into `CachingConverter<Bytes, KotlinT>`. Wire form is raw `Bytes`; Kotlin form is the wrapped type (e.g., UUID).

```kotlin
@OnlyForUseByGeneratedProtoCode
class BytesWrappedCachingConverter<KotlinT : Any>(
    private val converter: Converter<Bytes, KotlinT>
) : CachingConverter<Bytes, KotlinT> {
    override val wrapperClass = converter.wrapper
    override fun wrap(unwrapped: Bytes) = converter.wrap(unwrapped)
    override fun unwrap(wrapped: KotlinT) = converter.unwrap(wrapped)

    override fun writeTo(writer: Writer, value: Any) {
        if (value is Bytes) writer.write(value)
        else {
            @Suppress("UNCHECKED_CAST")
            writer.write(converter.unwrap(value as KotlinT))
        }
    }

    override fun sizeOf(value: Any): Int =
        if (value is Bytes) SizeCodecs.sizeOf(value)
        else {
            @Suppress("UNCHECKED_CAST")
            SizeCodecs.sizeOf(converter.unwrap(value as KotlinT))
        }

    override fun isDefault(value: Any): Boolean =
        if (value is Bytes) value.isEmpty()
        else {
            @Suppress("UNCHECKED_CAST")
            converter.unwrap(value as KotlinT).isEmpty()
        }
}
```

Pass-through benefit: `writeTo` writes raw `Bytes` directly (memcpy), `sizeOf` returns `bytes.value.size` (O(1)).

### `StringWrappedCachingConverter`

Adapts `Converter<String, KotlinT>` into `CachingConverter<Bytes, KotlinT>`. Wire form is raw UTF-8 `Bytes` (not `String`); bridges `Bytes -> String -> KotlinT` on access.

```kotlin
@OnlyForUseByGeneratedProtoCode
class StringWrappedCachingConverter<KotlinT : Any>(
    private val converter: Converter<String, KotlinT>
) : CachingConverter<Bytes, KotlinT> {
    override val wrapperClass = converter.wrapper
    override fun wrap(unwrapped: Bytes) = converter.wrap(unwrapped.value.decodeToString())
    override fun unwrap(wrapped: KotlinT) = Bytes(converter.unwrap(wrapped).encodeToByteArray())

    override fun writeTo(writer: Writer, value: Any) {
        if (value is Bytes) writer.write(value)
        else {
            @Suppress("UNCHECKED_CAST")
            writer.write(converter.unwrap(value as KotlinT))
        }
    }

    override fun sizeOf(value: Any): Int =
        if (value is Bytes) SizeCodecs.sizeOf(value)
        else {
            @Suppress("UNCHECKED_CAST")
            SizeCodecs.sizeOf(converter.unwrap(value as KotlinT))
        }

    override fun isDefault(value: Any): Boolean =
        if (value is Bytes) value.isEmpty()
        else {
            @Suppress("UNCHECKED_CAST")
            converter.unwrap(value as KotlinT).isEmpty()
        }
}
```

---

## Nullability Model Change

### Current behavior

For wrapper types with `acceptsDefaultValue = false` (UUID, InetAddress, LocalDate):
- Non-optional proto3 field generates a **nullable** Kotlin property: `val uuid: UUID?`
- Absent on wire: `null`
- Malformed data: throws during deserialization (eager `wrap()`)
- Users check `uuid != null` before access

### New behavior

With CachingReference, raw bytes are stored — conversion is deferred:
- Non-optional proto3 field generates a **non-null** Kotlin property: `val uuid: UUID`
- The backing `CachingReference` is always present (stores `Bytes.empty()` if field absent on wire)
- `value()` calls `wrap()` lazily — throws if data is invalid (absent or malformed)
- Validation is a separate concern (protovalidate)
- Optional proto3 fields remain nullable (`CachingReference?` is null when field absent)

This aligns nullability with proto semantics: non-optional = non-null, optional = nullable.

**Breaking change:** Users currently checking `uuid != null` will need to use protovalidate instead. The `generate_non_null_accessor` proto option becomes a no-op for cached fields since the field is already non-null.

---

## Codegen Changes

### `PropertyInfo` — Replace `cachingString: Boolean` with `cachingInfo: CachingFieldInfo?`

```kotlin
class CachingFieldInfo(
    val converterRef: CodeBlock,              // e.g., StringCachingConverter or _cachingUuid
    val validateUtf8: Boolean,                // true for string-type wire fields
    val cachingRefNullable: Boolean,          // true only for optional fields
    val kotlinType: TypeName,                 // user-facing type (String, UUID, LocalDate)
    val companionProperty: CompanionCachingProperty?  // null for StringCachingConverter
)

class CompanionCachingProperty(
    val name: String,               // e.g., "_cachingUuid"
    val adapterClass: ClassName,    // BytesWrappedCachingConverter or StringWrappedCachingConverter
    val converterClass: ClassName   // UuidBytesConverter
)
```

### `PropertyAnnotator.kt` — Unified detection

```kotlin
internal fun isCachingField(f: StandardField, ctx: Context): Boolean =
    isCachingString(f) || isCachingWrapper(f, ctx)

internal fun isCachingWrapper(f: StandardField, ctx: Context): Boolean {
    if (!f.wrapped || f.repeated || f.isMap) return false
    val wireType = Wrapper.wrapperWireType(f, ctx) ?: return false
    return wireType == Bytes::class || wireType == String::class
}
```

For each variant, populate `CachingFieldInfo`:
- **Plain string**: `converterRef = StringCachingConverter`, `validateUtf8 = true`, `cachingRefNullable = false`, no companion property
- **Bytes-wrapped**: `validateUtf8 = false`, adapter is `BytesWrappedCachingConverter`, `cachingRefNullable` only if `optional`
- **String-wrapped**: `validateUtf8 = true`, adapter is `StringWrappedCachingConverter`, `cachingRefNullable` only if `optional`

Note: `acceptsDefaultValue = false` no longer controls nullability for cached fields. The field is non-null unless it's proto3 `optional`.

### `MessageGenerator.kt` — Simplified `generateCachingProperty`

Non-nullable:
```kotlin
private val _name: CachingReference<Bytes, String>       // constructor val
@GeneratedProperty(1) val name: String get() = _name.value()  // delegate
```

Nullable (optional fields only):
```kotlin
private val _uuid: CachingReference<Bytes, UUID>?        // constructor val
@GeneratedProperty(1) val uuid: UUID? get() = _uuid?.value()  // delegate
```

### `DeserializerGenerator.kt`

- All caching fields deserialize as `Bytes?`, initialized to `null`
- String-type wire fields: `StringCachingConverter.readValidatedBytes(reader)` (UTF-8 validation)
- Bytes-type wire fields: `reader.readBytes()` (no validation needed)
- Constructor call wraps into CachingReference:
  - Non-nullable: `CachingReference(name ?: Bytes.empty(), converterRef)`
  - Nullable: `uuid?.let { CachingReference(it, converterRef) }`
- Companion properties for wrapper adapter instances:
  ```kotlin
  @JvmField val _cachingUuid = BytesWrappedCachingConverter(UuidBytesConverter)
  ```

### `DeserializerSupport.kt`

- `wrapDeserializedValueForConstructor`: emit `CachingReference(...)` wrapping
- Remove `cachingStringTrailingParam()` — no trailing params

### `BuilderGenerator.kt`

- Builder property type stays as the Kotlin type (`String`, `UUID?`, etc.)
- `build()` wraps caching fields: `CachingReference(name, converterRef)` or `uuid?.let { CachingReference(it, converterRef) }`

### `SerializerGenerator.kt`, `MessageSizeGenerator.kt`, `SerializeAndSizeSupport.kt`

- Generalize `isCachingString` checks to `cachingInfo != null`
- `nonDefault()` for caching fields:
  - Non-nullable: `_field.isNotDefault()`
  - Nullable: `_field != null && _field.isNotDefault()`
- Serialize via `_field.writeTo(writer)`, size via `_field.sizeOf()`

---

## Generated Code Examples

### Plain string (simplified from Phase 2):
```kotlin
class Person private constructor(
    private val _name: CachingReference<Bytes, String>,
    @GeneratedProperty(2) val id: Int,
    val unknownFields: UnknownFieldSet = UnknownFieldSet.empty()
) : AbstractMessage() {
    @GeneratedProperty(1) val name: String get() = _name.value()

    companion object Deserializer : AbstractDeserializer<Person>() {
        override fun deserialize(reader: Reader): Person {
            var name: Bytes? = null; var id = 0; ...
            when (reader.readTag()) {
                0u -> return Person(
                    CachingReference(name ?: Bytes.empty(), StringCachingConverter),
                    id, UnknownFieldSet.from(unknownFields)
                )
                10u -> name = StringCachingConverter.readValidatedBytes(reader)
            }
        }
    }
}
```

### Bytes-wrapped UUID (non-null, was previously nullable):
```kotlin
class Example private constructor(
    private val _uuid: CachingReference<Bytes, UUID>,
    @GeneratedProperty(2) val id: Int,
    val unknownFields: UnknownFieldSet = UnknownFieldSet.empty()
) : AbstractMessage() {
    @GeneratedProperty(1) val uuid: UUID get() = _uuid.value()

    companion object Deserializer : AbstractDeserializer<Example>() {
        @JvmField val _cachingUuid = BytesWrappedCachingConverter(UuidBytesConverter)

        override fun deserialize(reader: Reader): Example {
            var uuid: Bytes? = null; var id = 0; ...
            when (reader.readTag()) {
                0u -> return Example(
                    CachingReference(uuid ?: Bytes.empty(), _cachingUuid),
                    id, UnknownFieldSet.from(unknownFields)
                )
                10u -> uuid = reader.readBytes()
            }
        }
    }
}
```

---

## Scope

**Cached:**
- Plain string fields (non-repeated, non-map, non-oneof)
- `bytes` fields with `Converter<Bytes, KotlinT>` wrapper (e.g., UUID, InetAddress)
- `string` fields with `Converter<String, KotlinT>` wrapper (e.g., LocalDate)

**Not cached:**
- Message-wrapped types (e.g., `Timestamp -> Instant`): wire form is a parsed message, not raw bytes
- Primitive-wrapped types (e.g., `Int -> IntBox`): trivial conversion cost
- Repeated/map/oneof wrapped fields: collection semantics don't fit CachingReference
- `BytesSlice` fields: special handling

---

## `OptimizedSizeOfConverter` Deprecation

With caching, `OptimizedSizeOfConverter` is subsumed for cached fields. Non-cached fields (repeated, map, oneof wrapped) still benefit from it. Add `@Deprecated` annotation.

---

## Verification

1. `./gradlew clean check` — all tests pass (wrapper types, conformance, Jackson)
2. `./gradlew apiDump` — update API surface for new runtime classes
3. Inspect generated code to verify simplified CachingReference constructor pattern
