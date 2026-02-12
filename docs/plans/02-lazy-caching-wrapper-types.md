# Plan: Lazy/Caching Wrapper Types

**Status:** Proposed
**Priority:** Medium
**Issue:** https://github.com/open-toast/protokt/issues/27

## Problem

Protokt currently converts between protobuf wire types and Kotlin wrapper types
eagerly — both during deserialization and serialization. This creates unnecessary work:

1. **Strings:** During deserialization, `readString()` decodes UTF-8 bytes into a
   `String`. During serialization, the string must be re-encoded to UTF-8 to calculate
   its byte size and then again to write it. That's three iterations over the data
   (decode, size, write) when a lazy approach would need at most two.

2. **Wrapper types (e.g., UUID, Instant):** During deserialization, the raw protobuf
   value is immediately converted to the wrapped Kotlin type via `Converter.wrap()`.
   During serialization, it's converted back via `Converter.unwrap()`. If a message is
   deserialized and then re-serialized without the wrapped value ever being accessed,
   both conversions are wasted.

3. **OptimizedSizeOfConverter:** This interface exists as a workaround — it lets
   converters calculate size without unwrapping. A lazy approach eliminates the need
   for this entirely.

### Current flow

```
Deserialize: wire bytes → readString()/readMessage() → Converter.wrap() → stored in message
Serialize:   stored value → Converter.unwrap() → sizeof() → write()
```

### Proposed flow

```
Deserialize: wire bytes → validate UTF-8 (strings only) → stored as raw form in CachingReference
Access:      CachingReference.wrapped → lazy Converter.wrap() → cached
Serialize:   CachingReference.unwrapped → use raw form directly (no conversion needed)
```

## Proposed Solution

Based on the design in issue #27, introduce a `CachingReference<WrappedT, UnwrappedT>`
that lazily converts between the wrapped (user-facing) and unwrapped (wire) forms.

### Runtime: CachingReference

```kotlin
class CachingReference<S : Any, T : Any>(
    @Volatile private var ref: Any,
    private val converter: Converter<S, T>
) {
    val wrapped: S
        get() = ref.let {
            if (converter.wrapper.isInstance(it)) {
                @Suppress("UNCHECKED_CAST")
                it as S
            } else {
                @Suppress("UNCHECKED_CAST")
                val converted = converter.wrap(it as T)
                ref = converted
                converted
            }
        }

    val unwrapped: T
        get() = ref.let {
            if (converter.wrapped.isInstance(it)) {
                @Suppress("UNCHECKED_CAST")
                it as T
            } else {
                @Suppress("UNCHECKED_CAST")
                val converted = converter.unwrap(it as S)
                ref = converted
                converted
            }
        }
}
```

Key design decisions:
- `@Volatile` on `ref` ensures thread-safe lazy conversion (no locking needed since
  both directions produce equivalent results — benign races are acceptable)
- `ref` holds either form; `isInstance` checks determine which form is currently cached
- Once converted, the new form replaces the old one in `ref`
- The original form is lost after conversion (acceptable since we can always re-derive)

### Multiplatform considerations

`@Volatile` is available on JVM and Native but is a no-op on JS. Since JS is
single-threaded, this is correct. The `isInstance` check uses `KClass.isInstance()`
which is available on all platforms.

However, `CachingReference` stores `Any` and uses runtime type checks, which may
need care on JS where type erasure behaves differently. The issue #27 draft uses
`java.lang.Class.isAssignableFrom()` which is JVM-only. The multiplatform version
should use `KClass.isInstance()` from kotlin-reflect or an expect/actual pattern.

### Code Generation Changes

**Generated message (with caching):**

```kotlin
class MyMessage private constructor(
    private val _name: CachingReference<String, ByteArray>,
    private val _uuid: CachingReference<java.util.UUID, ByteArray>,
    val id: Int,
    val unknownFields: UnknownFieldSet
) : AbstractMessage() {

    val name: String get() = _name.wrapped
    val uuid: java.util.UUID get() = _uuid.wrapped

    override fun serialize(serializer: KtMessageSerializer) {
        if (_name.unwrapped.isNotEmpty()) {
            serializer.writeTag(10).writeBytes(_name.unwrapped)
        }
        // ...
    }

    private fun sizeof(): Int {
        var res = 0
        if (_name.unwrapped.isNotEmpty()) {
            res += sizeofTag(1) + sizeofBytes(_name.unwrapped)
        }
        // ...
        return res
    }
}
```

**Deserialization:**

```kotlin
override fun deserialize(deserializer: KtMessageDeserializer): MyMessage {
    var name: ByteArray? = null
    var uuid: ByteArray? = null
    // ...
    while (true) {
        when (deserializer.readTag()) {
            0 -> return MyMessage(
                CachingReference(name ?: ByteArray(0), StringBytesConverter),
                CachingReference(uuid ?: ByteArray(0), UuidBytesConverter),
                // ...
            )
            10 -> name = deserializer.readByteArray()  // NOT readString()
            18 -> uuid = deserializer.readByteArray()
            // ...
        }
    }
}
```

Key change: `readByteArray()` instead of `readString()` for string fields. The
`String` object is only allocated when the `name` property is accessed.

### UTF-8 Validation Constraint

Proto3 requires string fields to contain valid UTF-8, and the conformance test suite
enforces this at parse time (e.g., `Required.Proto3.ProtobufInput.IllegalUtf8InStringField`
expects a parse error). This means we **cannot** fully defer string processing — we
must validate UTF-8 eagerly on the raw bytes at deserialization time.

The approach: read the field as a `ByteArray` via `readByteArray()`, then immediately
run a UTF-8 validation scan over the bytes. This scan does NOT allocate a `String` —
it only checks that every byte sequence is valid UTF-8. The actual `String` construction
(which involves copying the bytes into a `char[]` internally) is deferred until the
property is accessed.

```kotlin
10 -> {
    name = deserializer.readByteArray()
    validateUtf8(name!!)  // throws on invalid UTF-8, no String allocation
}
```

**What we still win:**
- **No `String` allocation at parse time.** The `validateUtf8()` scan is cheaper than
  `new String(bytes, UTF_8)` since it doesn't allocate a char array or a String object.
  For pass-through messages where the field is never accessed, this avoids the
  allocation entirely.
- **No re-encode at serialization time.** The raw bytes are written directly. This is
  the biggest win — today, serialization must call `string.toByteArray(UTF_8)` to
  re-encode, which allocates a new byte array and iterates the string. With caching,
  serialization uses the original bytes with zero conversion.
- **Single-iteration size calculation.** `sizeof()` uses `bytes.size` directly instead
  of re-encoding to count bytes.

**What we lose vs. fully lazy:**
- One extra byte-array iteration at parse time for the validation scan. This is
  unavoidable for proto3 conformance.

A `validateUtf8(ByteArray)` utility function should be added to the runtime. This is
straightforward to implement (walk the byte array checking UTF-8 continuation byte
rules) and is already well-understood — e.g., protobuf-java's `Utf8.isValidUtf8()`.

### Scope of Applicability

Caching applies to fields that have a converter (wrapper type) or are string fields.
It does NOT apply to:
- Primitive fields (int, bool, etc.) — no conversion needed
- Repeated fields — the collection itself isn't wrapped/unwrapped
- Map fields — same as repeated
- Enum fields — direct mapping, no conversion overhead
- Nested message fields — already stored as the message type

### Fields that benefit

| Field type | Current | With caching |
|-----------|---------|-------------|
| `string` | `readString()` at deser, re-encode at ser | `readByteArray()` + UTF-8 validate at deser, use bytes at ser |
| `bytes` with wrapper | `readByteArray()` + `wrap()` at deser, `unwrap()` at ser | `readByteArray()` at deser, lazy `wrap()` on access |
| `message` with wrapper | `readMessage()` + `wrap()` at deser, `unwrap()` at ser | `readMessage()` at deser, lazy `wrap()` on access |

### What Changes in the Codegen

| File | Change |
|------|--------|
| `MessageGenerator.kt` | Generate private `CachingReference` backing fields + public accessors |
| `DeserializerGenerator.kt` | Read raw bytes for string fields; construct `CachingReference` at end |
| `SerializerGenerator.kt` | Use `_field.unwrapped` instead of `field` for serialization |
| `SizeofGenerator.kt` | Use `_field.unwrapped` for size calculation |
| `BuilderGenerator.kt` | Builder still accepts wrapped types; `build()` constructs `CachingReference` |
| `Wrapper.kt` | May need new string-specific converter |
| Runtime | Add `CachingReference` class, string<->bytes converter |

### Impact on equals/hashCode/toString

These methods should use the **wrapped** form (the user-facing type) to maintain
semantic correctness:

```kotlin
override fun equals(other: Any?): Boolean =
    other is MyMessage &&
        other.name == name &&      // uses .wrapped
        other.uuid == uuid &&      // uses .wrapped
        other.unknownFields == unknownFields

override fun hashCode(): Int {
    var result = name.hashCode()   // uses .wrapped
    result = 31 * result + uuid.hashCode()
    return result
}
```

This means accessing a field for equality comparison will trigger lazy conversion.
This is acceptable — if you're comparing messages you need the semantic values.

### OptimizedSizeOfConverter Deprecation

With caching, `OptimizedSizeOfConverter` becomes unnecessary:
- Current: `OptimizedSizeOfConverter.sizeOf(wrapped)` avoids unwrapping just for size
- With caching: `CachingReference.unwrapped` gives the raw form, and size calculation
  uses it directly. The unwrapped form is cached for the subsequent `serialize()` call.

`OptimizedSizeOfConverter` can be deprecated and eventually removed.

### Risks and Considerations

- **Memory:** `CachingReference` adds an object allocation per cached field. For
  messages with many string fields, this adds GC pressure.
- **Thread safety:** The `@Volatile` + benign race approach is well-established
  (used by `lazy(LazyThreadSafetyMode.PUBLICATION)`) but worth documenting.
- **Multiplatform type checks:** `KClass.isInstance()` on JS may behave differently
  for primitive types. Needs testing.
- **UTF-8 validation cost:** Proto3 conformance requires eager UTF-8 validation at
  parse time. The validation scan adds one iteration over the byte array, but avoids
  the `String` allocation. Net win for pass-through messages; roughly neutral for
  messages where every string field is accessed.
- **Debug experience:** In a debugger, `CachingReference` fields show as the backing
  `_field` with an opaque `ref`. Custom `toString()` on the message mitigates this.
- **Builder API change:** The builder still accepts the wrapped type (`String`, `UUID`,
  etc.) so user code doesn't change. Internally, `build()` wraps in `CachingReference`
  starting from the wrapped form.

### Testing Plan

- Existing serialization/deserialization round-trip tests should pass
- Add tests verifying:
  - Lazy conversion: deserialize a message, serialize without accessing fields — no
    string decoding occurs
  - Caching: access a wrapped field twice, verify converter is called only once
  - Thread safety: concurrent access from multiple threads
  - Equality: messages with same content created via deserialization vs. builder are equal
- Benchmark: compare serialization throughput with and without caching for
  string-heavy messages

### Phasing

1. **Phase 1:** Implement `CachingReference` in runtime, add string<->bytes converter
2. **Phase 2:** Update codegen for string fields (biggest win, most common case)
3. **Phase 3:** Extend to all wrapper types
4. **Phase 4:** Deprecate `OptimizedSizeOfConverter`
