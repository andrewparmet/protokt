package com.toasttab.protokt

internal class OldToNewAdapter(
    private val deserializer: com.toasttab.protokt.rt.KtMessageDeserializer
) : KtMessageDeserializer {
    override fun readBytes() =
        Bytes(deserializer.readBytes().value)

    override fun readBytesSlice() =
        deserializer.readBytesSlice().let {
            BytesSlice(it.array, it.offset, it.length)
        }

    override fun readDouble() =
        deserializer.readDouble()

    override fun readFixed32() =
        deserializer.readFixed32()

    override fun readFixed64() =
        deserializer.readFixed64()

    override fun readFloat() =
        deserializer.readFloat()

    override fun readInt64() =
        deserializer.readInt64()

    override fun readSFixed32() =
        deserializer.readSFixed32()

    override fun readSFixed64() =
        deserializer.readSFixed64()

    override fun readSInt32() =
        deserializer.readSInt32()

    override fun readSInt64() =
        deserializer.readSInt64()

    override fun readString() =
        deserializer.readString()

    override fun readUInt64() =
        deserializer.readUInt64()

    override fun readTag() =
        deserializer.readTag()

    override fun readUnknown() =
        deserializer.readUnknown().let {
            when (it.value) {
                is com.toasttab.protokt.rt.VarintVal -> UnknownField.varint(it.fieldNumber, it.value.value.value)
                is com.toasttab.protokt.rt.Fixed32Val -> UnknownField.fixed32(it.fieldNumber, it.value.value.value)
                is com.toasttab.protokt.rt.Fixed64Val -> UnknownField.fixed64(it.fieldNumber, it.value.value.value)
                is com.toasttab.protokt.rt.LengthDelimitedVal -> UnknownField.lengthDelimited(it.fieldNumber, it.value.value.value)
                else -> error("unsupported unknown field type")
            }
        }

    override fun readRepeated(packed: Boolean, acc: KtMessageDeserializer.() -> Unit) {
        deserializer.readRepeated(packed) { acc(this@OldToNewAdapter) }
    }

    override fun <T : com.toasttab.protokt.rt.KtMessage> readMessage(m: KtDeserializer<T>) =
        throw UnsupportedOperationException()
}
