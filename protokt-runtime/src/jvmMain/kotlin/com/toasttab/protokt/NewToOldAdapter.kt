package com.toasttab.protokt

import com.toasttab.protokt.rt.KtMessage

internal class NewToOldAdapter(
    private val deserializer: KtMessageDeserializer
) : com.toasttab.protokt.rt.KtMessageDeserializer {
    override fun readBytes() =
        com.toasttab.protokt.rt.Bytes(deserializer.readBytes().value)

    override fun readBytesSlice() =
        deserializer.readBytesSlice().let {
            com.toasttab.protokt.rt.BytesSlice(it.array, it.offset, it.length)
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
                is VarintVal -> com.toasttab.protokt.rt.UnknownField.varint(it.fieldNumber, it.value.value.value)
                is Fixed32Val -> com.toasttab.protokt.rt.UnknownField.fixed32(it.fieldNumber, it.value.value.value)
                is Fixed64Val -> com.toasttab.protokt.rt.UnknownField.fixed64(it.fieldNumber, it.value.value.value)
                is LengthDelimitedVal -> com.toasttab.protokt.rt.UnknownField.lengthDelimited(it.fieldNumber, it.value.value.value)
                else -> error("unsupported unknown field type")
            }
        }

    override fun readRepeated(packed: Boolean, acc: com.toasttab.protokt.rt.KtMessageDeserializer.() -> Unit) {
        deserializer.readRepeated(packed) { acc(this@NewToOldAdapter) }
    }

    override fun <T : KtMessage> readMessage(m: com.toasttab.protokt.rt.KtDeserializer<T>) =
        throw UnsupportedOperationException()
}