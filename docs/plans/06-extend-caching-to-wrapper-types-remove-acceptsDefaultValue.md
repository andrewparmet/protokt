# Plan: Extend Caching to Wrapper Types + Remove `acceptsDefaultValue`

## Context

This is a continuation of the caching infrastructure work. Prior work in this session:
- Renamed `CachingReference` → `LazyReference`, `StringCachingConverter` → `StringConverter`
- Merged two `Converter` interfaces into one unified interface in `protokt-runtime`
- Deleted `extensions:protokt-extensions-api` module
- Deleted `OptimizedSizeOfConverter`
- `LazyReference<WireT, KotlinT>` now handles wire dispatch (`writeTo`/`sizeOf`/`isDefault`) internally via `when` on wire type (`Bytes`, `String`, `Message`)
- Compilation passes

This plan extends caching from plain strings to bytes-wrapped and string-wrapped fields, changes the nullability model, and removes `acceptsDefaultValue`.

---

## Scope

**Cached (use `LazyReference`):**
- Plain `string` fields (already done) — non-repeated, non-map, non-oneof, non-optional
- `bytes` fields with `Converter<Bytes, KotlinT>` (e.g., UUID, InetAddress) — non-repeated, non-map, non-oneof, non-optional
- `string` fields with `Converter<String, KotlinT>` (e.g., LocalDate, StringBox) — non-repeated, non-map, non-oneof, non-optional

**Not cached:**
- Message-wrapped (e.g., `Timestamp → Instant`): wire form is a parsed message
- Primitive-wrapped (e.g., `Int → IntBox`): trivial conversion cost
- Repeated/map/oneof fields
- `optional` fields (simplifies this phase; can be added later)
- `BytesSlice` fields

---

## Nullability Model Change

**Before:** `acceptsDefaultValue = false` on a converter (e.g., UUID, LocalDate, InetAddress) made the field nullable (`UUID?`). Users checked `uuid != null`.

**After:** All cached fields are non-null. `LazyReference` always exists, storing `Bytes.empty()` if field was absent on wire. `wrap()` throws lazily on access if data is invalid. Nullability is purely a proto schema concern:
- proto3 `optional` → nullable
- message type → nullable
- everything else → non-null

**Breaking change:** `acceptsDefaultValue` is removed from `Converter`. Fields like `uuid: UUID?` become `uuid: UUID`.

---

## Step 1: Update `LazyReference` to bridge Bytes→String internally

**File:** `protokt-runtime/src/commonMain/kotlin/protokt/v1/LazyReference.kt`

No `BytesBridgeConverter` adapter class needed. Instead, `LazyReference` handles the case where raw `Bytes` are stored but the converter expects `String`. This happens for string-wrapped fields (e.g., `Converter<String, LocalDate>`).

### Changes to `value()`

When converting from wire form, check if stored as `Bytes` but converter expects `String`, and decode UTF-8 first:

```kotlin
fun value(): KotlinT {
    val current = ref
    return if (converter.wrapper.isInstance(current)) {
        @Suppress("UNCHECKED_CAST")
        current as KotlinT
    } else {
        @Suppress("UNCHECKED_CAST")
        val wireValue = if (current is Bytes && converter.wrapped != Bytes::class) {
            current.value.decodeToString() as WireT  // Bridge: raw Bytes → String
        } else {
            current as WireT
        }
        val converted = converter.wrap(wireValue)
        ref = converted
        converted
    }
}
```

### Changes to `wireValue()`

Same bridge for the reverse direction:

```kotlin
fun wireValue(): WireT {
    val current = ref
    return if (!converter.wrapper.isInstance(current)) {
        @Suppress("UNCHECKED_CAST")
        if (current is Bytes && converter.wrapped != Bytes::class) {
            current.value.decodeToString() as WireT  // Bridge: raw Bytes → String
        } else {
            current as WireT
        }
    } else {
        @Suppress("UNCHECKED_CAST")
        val converted = converter.unwrap(current as KotlinT)
        ref = converted
        converted
    }
}
```

### Changes to `writeTo()`, `sizeOf()`, `isDefault()`

Add a fast path that checks `ref` directly. If it's still raw `Bytes` (not yet converted to Kotlin type), operate on it without any conversion — this is the pass-through optimization:

```kotlin
fun writeTo(writer: Writer) {
    val current = ref
    // Fast path: still raw bytes, write directly (no conversion needed)
    if (current is Bytes && !converter.wrapper.isInstance(current)) {
        writer.write(current)
        return
    }
    when (val wire = wireValue()) {
        is Bytes -> writer.write(wire)
        is String -> writer.write(wire)
        is Message -> wire.serialize(writer)
        else -> error("Unsupported wire type: ${wire::class}")
    }
}

fun sizeOf(): Int {
    val current = ref
    if (current is Bytes && !converter.wrapper.isInstance(current)) {
        return SizeCodecs.sizeOf(current)
    }
    return when (val wire = wireValue()) {
        is Bytes -> SizeCodecs.sizeOf(wire)
        is String -> SizeCodecs.sizeOf(wire)
        is Message -> wire.messageSize()
        else -> error("Unsupported wire type: ${wire::class}")
    }
}

fun isDefault(): Boolean {
    val current = ref
    if (current is Bytes && !converter.wrapper.isInstance(current)) {
        return current.isEmpty()
    }
    return when (val wire = wireValue()) {
        is Bytes -> wire.isEmpty()
        is String -> wire.isEmpty()
        is Message -> false
        else -> error("Unsupported wire type: ${wire::class}")
    }
}
```

**Why this works:** The fast-path condition `current is Bytes && !converter.wrapper.isInstance(current)` is true when `ref` is still raw bytes (not yet converted to the Kotlin type). For `Converter<Bytes, UUID>`, `UUID::class.isInstance(bytes)` is false → fast path. For `Converter<String, LocalDate>`, `LocalDate::class.isInstance(bytes)` is false → fast path. For `Converter<Bytes, String>` (StringConverter), `String::class.isInstance(bytes)` is false → fast path. All correct.

---

## Step 2: Add `CachingFieldInfo` and detection logic

### `CachingFieldInfo` sealed class (in `PropertyAnnotator.kt`)

```kotlin
internal sealed class CachingFieldInfo {
    /** Plain string field. Uses StringConverter directly. */
    object PlainString : CachingFieldInfo()

    /** Bytes-wrapped field (e.g., Converter<Bytes, UUID>). Converter used directly. */
    data class BytesWrapped(val converterClassName: ClassName) : CachingFieldInfo()

    /** String-wrapped field (e.g., Converter<String, LocalDate>). LazyReference bridges Bytes→String internally. */
    data class StringWrapped(val converterClassName: ClassName) : CachingFieldInfo()
}
```

### Detection in `Wrapper.kt`

Add `cachingFieldInfo()` using existing `withWrapper()`:

```kotlin
internal fun StandardField.cachingFieldInfo(ctx: Context, mapEntry: Boolean): CachingFieldInfo? {
    if (repeated || isMap || mapEntry || optional) return null
    if (!wrapped) {
        return if (type == FieldType.String) CachingFieldInfo.PlainString else null
    }
    return withWrapper(ctx.info.context) { details ->
        when (details.converter.wrapped) {
            Bytes::class -> CachingFieldInfo.BytesWrapped(details.converter::class.asClassName())
            String::class -> CachingFieldInfo.StringWrapped(details.converter::class.asClassName())
            else -> null  // message-wrapped or primitive-wrapped: not cached
        }
    }
}
```

---

## Step 3: Update `PropertyAnnotator.kt`

Replace `cachingString: Boolean` with `cachingInfo: CachingFieldInfo?` on `PropertyInfo`.

Key changes in `annotate(field)`:
```kotlin
val cachingInfo = field.cachingFieldInfo(ctx, msg.mapEntry)
val wrapperRequiresNullability = if (cachingInfo != null) false else field.wrapperRequiresNullability(ctx)

PropertyInfo(
    // ...
    propertyType = propertyType(field, type, wrapperRequiresNullability),
    deserializeType = if (cachingInfo != null) Bytes::class.asTypeName().copy(nullable = true) else deserializeType(field, type),
    nullable = field.nullable || field.optional || wrapperRequiresNullability,
    cachingInfo = cachingInfo,
    // ...
)
```

Remove `isCachingString()` function. Remove `cachingString` from `PropertyInfo`, add `cachingInfo: CachingFieldInfo? = null`.

---

## Step 4: Update `MessageGenerator.kt`

Replace `property.cachingString` with `property.cachingInfo != null` in `properties()`.

Generalize `generateCachingStringProperty()` → `generateCachingProperty()`:
- `LazyReference` type parameterized with the converter's wire type and `property.propertyType`
  - `PlainString` and `BytesWrapped`: `LazyReference<Bytes, T>`
  - `StringWrapped`: `LazyReference<String, T>` (the converter's actual WireT is String; LazyReference bridges Bytes→String internally)
- Backing property: `private val _name: LazyReference<WireT, KotlinT>`
- Public getter: `val name: KotlinT get() = _name.value()`
- All cached fields are non-null (no optional caching in this phase)

---

## Step 5: Update `DeserializerGenerator.kt`

Generalize `cachingStringDeserialize()` → `cachingDeserialize(info)`:
```kotlin
when (info) {
    PlainString, StringWrapped -> CodeBlock.of("%T.readValidatedBytes(reader)", StringConverter::class)  // UTF-8 validation
    BytesWrapped -> CodeBlock.of("reader.readBytes()")  // no validation
}
```

No companion properties needed — converters are referenced as singletons directly.

Replace `isCachingString(field)` with lookup of `cachingInfo` from properties list.

---

## Step 6: Update `DeserializerSupport.kt`

Generalize `wrapDeserializedValueForConstructor()`:

**From deserializer** (has `Bytes?`):
```kotlin
// All cached: LazyReference(name ?: Bytes.empty(), converterRef)
CodeBlock.of("%T(%N ?: %T.empty(), %L)", LazyReference::class, p.name, Bytes::class, converterRef)
```

**From builder** (has Kotlin type or null):
```kotlin
// Non-optional cached: LazyReference(value ?: Bytes.empty(), converterRef)
// Builder value is UUID? — if null, use Bytes.empty() as wire default
CodeBlock.of("%T(%N ?: %T.empty(), %L)", LazyReference::class, p.name, Bytes::class, converterRef)
```

Where `converterRef` is:
- `PlainString` → `StringConverter`
- `BytesWrapped` → converter class directly (e.g., `UuidBytesConverter`)
- `StringWrapped` → converter class directly (e.g., `LocalDateStringConverter`)

Replace `p.cachingString` with `p.cachingInfo != null` in `deserializeVarInitialState()`.

---

## Step 7: Update serialization/sizing codegen

### `SerializerGenerator.kt`
Replace `isCachingString(f) && o == null` with caching check. Serialization property for cached fields is `_fieldName`.

### `MessageSizeGenerator.kt`
Same pattern — replace `isCachingString(f)` with caching check.

### `SerializeAndSizeSupport.kt`
Replace `isCachingString(this)` in `nonDefault()` with caching check.

---

## Step 8: Deprecate and remove `acceptsDefaultValue`

### `Converter.kt`
Deprecate `acceptsDefaultValue`:
```kotlin
@Deprecated("No longer used by protokt code generation.", level = DeprecationLevel.WARNING)
val acceptsDefaultValue get() = true
```

### `ClassLookup.kt`
- Remove `tryDeserializeDefaultValue()` validation
- Remove `cannotDeserializeDefaultValue` from `ConverterDetails`

### `Wrapper.kt`
- Remove `wrapperRequiresNullability()` and `wrapperRequiresNonNullOptionForNonNullity()`

### `PropertyAnnotator.kt`
- Remove `wrapperRequiresNullability` variable (already handled by cachingInfo)

### `Nullability.kt`
- Remove `wrapperRequiresNullability` parameter from `propertyType()`

### `SerializeAndSizeSupport.kt`
- Remove the `!nullable && wrapperRequiresNullability(ctx)` null guard in `nonDefault()` (dead code after caching all bytes/string-wrapped fields)

### Converter implementations
Remove `override val acceptsDefaultValue = false` from:
- `UuidBytesConverter.kt`
- `LocalDateStringConverter.kt`
- `InetAddressBytesConverter.kt`
- `ProtoktCodegenTest.kt` test converter

### `FieldParser.kt`
Relax `generate_non_null_accessor` validation — allow on any wrapped field (it's a no-op for cached fields since they're already non-null).

---

## Step 9: Update tests

### `WrapperTypesTest.kt`
- UUID, InetAddress, LocalDate properties are now **non-null** (was nullable)
- `generate_non_null_accessor` tests: property is already non-null, `requireXxx` accessor may not be generated
- Remove `!!` null assertions from test code that accesses wrapper fields
- Update nullability assertions (e.g., `propertyIsMarkedNullable("uuid")` → `isFalse()`)

### Other test files
- Search for `!!` or `as Any?` patterns on wrapper fields that changed nullability
- Ensure round-trip serialization tests still pass (wire format unchanged)

---

## Files to modify

| File | Change |
|------|--------|
| `protokt-runtime/.../LazyReference.kt` | Add Bytes→String bridge in `value()`/`wireValue()`, fast-path in `writeTo()`/`sizeOf()`/`isDefault()` |
| `protokt-runtime/.../Converter.kt` | Deprecate `acceptsDefaultValue` |
| `shared-src/.../ClassLookup.kt` | Remove `acceptsDefaultValue` validation and `cannotDeserializeDefaultValue` |
| `protokt-codegen/.../Wrapper.kt` | Add `cachingFieldInfo()`, remove `wrapperRequiresNullability` |
| `protokt-codegen/.../PropertyAnnotator.kt` | Replace `cachingString` with `cachingInfo: CachingFieldInfo?` |
| `protokt-codegen/.../MessageGenerator.kt` | Generalize `generateCachingStringProperty` → `generateCachingProperty` |
| `protokt-codegen/.../DeserializerGenerator.kt` | Generalize deserialization |
| `protokt-codegen/.../DeserializerSupport.kt` | Generalize constructor wrapping |
| `protokt-codegen/.../SerializerGenerator.kt` | Replace `isCachingString` with caching check |
| `protokt-codegen/.../SerializeAndSizeSupport.kt` | Replace `isCachingString`, remove wrapper nullability guard |
| `protokt-codegen/.../MessageSizeGenerator.kt` | Replace `isCachingString` with caching check |
| `protokt-codegen/.../Nullability.kt` | Remove `wrapperRequiresNullability` from `propertyType()` |
| `protokt-codegen/.../BuilderGenerator.kt` | Builder wrapping for cached wrapper fields |
| `protokt-codegen/util/FieldParser.kt` | Relax `generate_non_null_accessor` validation |
| `extensions/.../UuidBytesConverter.kt` | Remove `acceptsDefaultValue` override |
| `extensions/.../LocalDateStringConverter.kt` | Remove `acceptsDefaultValue` override |
| `extensions/.../InetAddressBytesConverter.kt` | Remove `acceptsDefaultValue` override |
| `protokt-codegen/.../ProtoktCodegenTest.kt` | Remove `acceptsDefaultValue` from test converter |
| `testing/.../WrapperTypesTest.kt` | Update nullability expectations |

---

## Verification

1. `./gradlew clean check` — all tests pass
2. `./gradlew apiDump` — update API surface
3. Inspect generated code for wrapper types (UUID, LocalDate, InetAddress) — verify `LazyReference` constructor pattern
4. Verify non-cached wrapper types (message-wrapped, primitive-wrapped) still work unchanged
