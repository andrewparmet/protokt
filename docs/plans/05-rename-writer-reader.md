# Plan: Rename Writer/Reader to Encoder/Decoder

**Status:** Proposed
**Priority:** Low
**Depends on:** None

## Problem

The runtime wire format types are named `Writer` and `Reader`, which sounds like
generic I/O rather than protobuf wire encoding. Other protobuf libraries use more
precise names:

- kotlinx-rpc: `WireEncoder` / `WireDecoder`
- protobuf-java: `CodedOutputStream` / `CodedInputStream`
- Wire: `ProtoWriter` / `ProtoReader`

protokt's own higher-level APIs already use `Deserializer` and `EnumDeserializer`
for typed deserialization, making the naming inconsistent: `Deserializer` is the
high-level API, but it delegates to `Reader` (low-level wire format) rather than
`Decoder`.

## Proposed Rename

| Current | New |
|---|---|
| `Writer` | `Encoder` |
| `Reader` | `Decoder` |

The `Deserializer` / `EnumDeserializer` names stay — they accurately describe the
high-level typed deserialization API (factory that produces `T` from wire bytes).
There's no corresponding `Serializer` because messages serialize themselves via
`Message.serialize()`. The asymmetry is intentional and matches protobuf-java.
`Encoder`/`Decoder` describe the low-level wire format operations that
`Deserializer` delegates to.

`ProtoktWriter` / `ProtoktReader` (the default implementations) become
`ProtoktEncoder` / `ProtoktDecoder`. Similarly `KotlinxIoSinkWriter` /
`KotlinxIoSourceReader` become `KotlinxIoSinkEncoder` / `KotlinxIoSourceDecoder`.

## Scope

This is a public API rename affecting:
- `protokt-runtime`: `Writer`, `Reader` interfaces
- `protokt-runtime-kotlinx-io`: `KotlinxIoSinkWriter`, `KotlinxIoSourceReader`,
  `StreamingCodec.reader()` / `serialize()`
- `protokt-codegen`: generated `serialize(writer: Writer)` methods, deserializer
  `deserialize(reader: Reader)` methods
- All generated code references `Writer` and `Reader`

## Approach

- Add `@Deprecated` typealiases for `Writer` → `Encoder` and `Reader` → `Decoder`
  for one release cycle
- Update codegen to emit the new names
- Update runtime implementations
- Remove typealiases in the following release
