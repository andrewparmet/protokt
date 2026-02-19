# Pure Kotlin Wire Format Codec + Runtime-Selectable Provider

## Context

protokt's runtime delegates wire format encoding/decoding to platform-specific libraries (protobuf-java on JVM, protobufjs on JS). This blocks adding a Kotlin/Native target and means protobuf-java is a hard runtime dependency on JVM. This change adds a pure Kotlin wire format implementation in `commonMain` and a `CodecProvider` mechanism (modeled on the existing `CollectionProvider`) so users can select the codec at runtime. On JVM, protobuf-java remains the default; on native (future), the Kotlin impl is the only option.

## New Files

### commonMain

1. **`protokt-runtime/src/commonMain/kotlin/protokt/v1/WireFormat.kt`** — Wire type constants + tag helpers (`WIRETYPE_VARINT=0`, `WIRETYPE_FIXED64=1`, etc., `getTagWireType`, `getTagFieldNumber`). Replaces platform imports of `com.google.protobuf.WireFormat` (JVM) and raw literals (JS).

2. **`protokt-runtime/src/commonMain/kotlin/protokt/v1/CodecProvider.kt`** — Interface:
   ```kotlin
   interface CodecProvider {
       fun writer(bytes: ByteArray): Writer
       fun reader(bytes: ByteArray): Reader
       fun reader(bytes: ByteArray, offset: Int, length: Int): Reader
   }
   ```

3. **`protokt-runtime/src/commonMain/kotlin/protokt/v1/Codec.kt`** — `internal expect val codecProvider: CodecProvider`

4. **`protokt-runtime/src/commonMain/kotlin/protokt/v1/KotlinWriter.kt`** (~120 lines) — Pure Kotlin `Writer` impl backed by a pre-allocated `ByteArray` with position cursor:
   - Varint encode: 7-bits-at-a-time loop with continuation bit
   - ZigZag encode for sint32/64 (same formula as `Sizes.kt`)
   - Fixed32/64: little-endian byte writes
   - Float/Double: `toRawBits()` then fixed write (stdlib common)
   - String: `encodeToByteArray()` + length-prefix (stdlib common UTF-8)
   - ByteArray/BytesSlice: length-prefix + `copyInto`

5. **`protokt-runtime/src/commonMain/kotlin/protokt/v1/KotlinReader.kt`** (~250 lines) — Pure Kotlin `Reader` impl backed by `ByteArray` with position/limit tracking:
   - Varint decode: byte-at-a-time loop, discard upper bits for 32-bit reads
   - ZigZag decode: `(n ushr 1) xor -(n and 1)`
   - Fixed32/64: little-endian assembly from bytes
   - Float/Double: `Float.fromBits()`/`Double.fromBits()` (stdlib common)
   - String: read length, `decodeToString()` (stdlib common UTF-8)
   - `readBytesSlice`: zero-copy when `sourceBytes` available (matches JvmReader behavior)
   - `readTag`: sets `_lastTag`, returns 0 at end
   - `readUnknown`: dispatches on wire type from `_lastTag` (uses `WireFormat`)
   - `readRepeated`: packed handling via mutable `limit` (replaces `pushLimit`/`popLimit`)
   - `readMessage`: save/restore limit around nested message

6. **`protokt-runtime/src/commonMain/kotlin/protokt/v1/KotlinCodecProvider.kt`** — Singleton `object` wiring `KotlinWriter`/`KotlinReader`.

### jvmMain

7. **`protokt-runtime/src/jvmMain/kotlin/protokt/v1/JvmCodecProvider.kt`** — Wraps existing `writer(CodedOutputStream)` and `reader(CodedInputStream, ByteArray?)` factory functions.

8. **`protokt-runtime/src/jvmMain/kotlin/protokt/v1/Codec.kt`** — `actual val codecProvider` using `by lazy` + `System.getProperty("protokt.codec.provider")` / `System.getenv("PROTOKT_CODEC_PROVIDER")` + reflection FQCN loading. Defaults to `JvmCodecProvider`.

### jsMain

9. **`protokt-runtime/src/jsMain/kotlin/protokt/v1/JsCodecProvider.kt`** — Wraps existing `reader(ProtobufJsReader)` function. Writer delegates to `KotlinCodecProvider` (protobufjs writer doesn't support pre-allocated byte arrays).

10. **`protokt-runtime/src/jsMain/kotlin/protokt/v1/Codec.kt`** — `actual val codecProvider` using `js()` env var check. Defaults to `JsCodecProvider`.

### Tests

11. **`protokt-runtime/src/commonTest/kotlin/protokt/v1/KotlinCodecTest.kt`** — Roundtrip tests for all field types: varint (0, 1, 127, 128, MAX_INT, negative, MAX_LONG), fixed32/64, zigzag, float/double, string (ASCII + multibyte UTF-8), byte arrays, tag encode/decode, nested messages via limit handling.

12. **`protokt-runtime/src/jvmTest/kotlin/protokt/v1/KotlinCodecCompatibilityTest.kt`** — Cross-codec tests: serialize with JvmCodecProvider, deserialize with KotlinCodecProvider (and vice versa). Verifies byte-level wire format compatibility.

## Modified Files

### jvmMain

- **`AbstractMessage.kt`** — Replace `CodedOutputStream.newInstance(buf)` with `codecProvider.writer(buf)`
- **`AbstractDeserializer.kt`** — Replace `CodedInputStream.newInstance(bytes)` with `codecProvider.reader(bytes)` / `codecProvider.reader(bytes, offset, length)`
- **`Message.kt`** — `serialize(OutputStream)`: if `codecProvider is JvmCodecProvider`, use existing CodedOutputStream path; else `outputStream.write(serialize())`
- **`Deserializer.kt`** — `deserialize(InputStream)`: if JvmCodecProvider, existing path; else `deserialize(stream.readBytes())`. `deserialize(ByteBuffer)`: if JvmCodecProvider, existing path; else copy to ByteArray. `deserialize(CodedInputStream)`: always delegates to existing `reader(stream)` (if user passes a CIS, protobuf-java is on classpath).

### jsMain

- **`AbstractMessage.kt`** — Route through `codecProvider`: if `KotlinCodecProvider`, use `codecProvider.writer(buf)` path; else existing ProtobufJsWriter path.
- **`AbstractDeserializer.kt`** — Route through `codecProvider`: if `KotlinCodecProvider`, use `codecProvider.reader(bytes)` path; else existing ProtobufJsReader path.

### Benchmarks

- **`benchmarks/protokt-benchmarks/src/main/kotlin/protokt/v1/benchmarks/ProtoktBenchmarks.kt`** — Add a second `@Param` for `codecProvider` alongside the existing `collectionProvider` param. In `@Setup`, set `System.setProperty("protokt.codec.provider", codecProvider)` in addition to the existing collection provider property. Parameter values: `"protokt.v1.JvmCodecProvider"` (default), `"protokt.v1.KotlinCodecProvider"`. This gives a cross-product of collection provider x codec provider benchmarks in the existing module — no new module needed.

### Conformance

- **`testing/conformance/driver/src/commonMain/kotlin/protokt/v1/conformance/Main.kt`** — Add stderr output for the active codec provider, paralleling the existing `protoktPersistentCollectionType=` line:
  ```kotlin
  Platform.printErr("protoktCodecProvider=${Platform.className(codecProvider)}")
  ```

- **`testing/conformance/runner/src/test/kotlin/protokt/v1/conformance/ConformanceTest.kt`** — Add `KotlinCodec` variants to the `ConformanceRunner` enum (paralleling the `JVM_PERSISTENT` / `JS_IR_PERSISTENT` variants):
  ```kotlin
  JVM_KOTLIN_CODEC("jvm", expectKotlinCodec = true) {
      override fun driver() = jvmConformanceDriver
      override fun env() = mapOf("JAVA_OPTS" to "-Dprotokt.codec.provider=protokt.v1.KotlinCodecProvider")
  }
  ```
  Add `verifyCodecProvider()` alongside the existing `verifyCollectionType()` to parse the stderr line and assert the correct provider is active.

## Implementation Order

1. `WireFormat.kt` (no deps)
2. `CodecProvider.kt` interface + `Codec.kt` expect (no deps beyond Writer/Reader)
3. `KotlinWriter.kt` (depends on Writer interface)
4. `KotlinReader.kt` (depends on Reader, WireFormat, Deserializer, Bytes, BytesSlice, UnknownField)
5. `KotlinCodecProvider.kt` (depends on KotlinWriter, KotlinReader)
6. `JvmCodecProvider.kt` + JVM `Codec.kt` actual
7. Modify JVM `AbstractMessage.kt`, `AbstractDeserializer.kt`, `Message.kt`, `Deserializer.kt`
8. `JsCodecProvider.kt` + JS `Codec.kt` actual
9. Modify JS `AbstractMessage.kt`, `AbstractDeserializer.kt`
10. Tests (commonTest + jvmTest)
11. Conformance: add stderr output in `Main.kt`, add `JVM_KOTLIN_CODEC` / `JS_IR_KOTLIN_CODEC` runners + `verifyCodecProvider()` in `ConformanceTest.kt`
12. Benchmarks: add `codecProvider` `@Param` to existing `ProtoktBenchmarks.kt`

## Verification

1. **Unit tests**: `./gradlew :protokt-runtime:allTests` — validates KotlinWriter/KotlinReader roundtrips and cross-codec compatibility
2. **Conformance (protobuf-java default)**: `./gradlew :testing:conformance:test` — existing conformance tests still pass with the provider indirection
3. **Conformance (Kotlin codec)**: The new `JVM_KOTLIN_CODEC` and `JS_IR_KOTLIN_CODEC` conformance runner variants validate wire format correctness against the official protobuf conformance suite using the pure Kotlin codec, with stderr verification confirming the correct provider is active
4. **Integration**: `./gradlew :testing:runtime-tests:test -Dprotokt.codec.provider=protokt.v1.KotlinCodecProvider` — validates all generated messages work with the Kotlin codec
5. **Benchmarks**: `./gradlew :benchmarks:protokt-benchmarks:run` — JMH JSON output now includes the codec provider x collection provider cross-product
