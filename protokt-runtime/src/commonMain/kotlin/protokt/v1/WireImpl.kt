package protokt.v1

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoWriter
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString.Companion.toByteString

internal class WireWriter(
    sink: BufferedSink
) : Writer {
    private val writer = ProtoWriter(sink)

    override fun writeFixed32(i: UInt) {
        writer.writeFixed32(i.toInt())
    }

    override fun writeSFixed32(i: Int) {
        writer.writeFixed32(i)
    }

    override fun writeUInt32(i: UInt) {
        writer.writeVarint32(i.toInt())
    }

    override fun writeSInt32(i: Int) {
        ProtoAdapter.SINT32.encode(writer, i)
    }

    override fun writeFixed64(l: ULong) {
        writer.writeFixed64(l.toLong())
    }

    override fun writeSFixed64(l: Long) {
        writer.writeFixed64(l)
    }

    override fun writeUInt64(l: ULong) {
        writer.writeVarint64(l.toLong())
    }

    override fun writeSInt64(l: Long) {
        ProtoAdapter.SINT64.encode(writer, l)
    }

    override fun write(i: Int) {
        writer.writeVarint32(i)
    }

    override fun write(l: Long) {
        writer.writeVarint64(l)
    }

    override fun write(f: Float) {
        writer.writeFixed32(f.toBits())
    }

    override fun write(d: Double) {
        writer.writeFixed64(d.toBits())
    }

    override fun write(s: String) {
        writer.writeVarint32(ProtoAdapter.STRING.encodedSize(s))
        writer.writeString(s)
    }

    override fun write(b: Boolean) {
        writer.writeVarint32(if (b) 1 else 0)
    }

    override fun write(b: ByteArray) {
        val bs = b.toByteString()
        writer.writeVarint32(ProtoAdapter.BYTES.encodedSize(bs))
        writer.writeBytes(bs)
    }

    override fun write(b: BytesSlice) {
        writer.writeBytes(b.toBytes().value.toByteString())
    }
}

internal class WireReader(
    private val source: BufferedSource,
    private val size: Int
) : Reader {
    private val reader = WireProtoReader(source)
    private var lastTag = 0u

    override fun readBytes() =
        Bytes(reader.readBytes().toByteArray())

    override fun readBytesSlice() =
        BytesSlice.from(reader.readBytes().toByteArray())

    override fun readDouble() =
        Double.fromBits(reader.readFixed64())

    override fun readFixed32() =
        reader.readFixed32().toUInt()

    override fun readFixed64() =
        reader.readFixed64().toULong()

    override fun readFloat() =
        Float.fromBits(reader.readFixed32())

    override fun readInt64() =
        reader.readVarint64()

    override fun readSFixed32() =
        reader.readFixed32()

    override fun readSFixed64() =
        reader.readFixed64()

    override fun readSInt32() =
        ProtoAdapter.SINT32.decode(reader)

    override fun readSInt64() =
        ProtoAdapter.SINT64.decode(reader)

    override fun readString() =
        reader.readString()

    override fun readUInt64() =
        reader.readVarint64().toULong()

    override fun readTag(): UInt {
        lastTag =
            if (reader.pos >= size) {
                0u
            } else {
                val tag = readInt32()
                check(tag ushr 3 != 0) { "Invalid tag" }
                tag.toUInt()
            }
        return lastTag
    }

    override fun readUnknown(): UnknownField {
        val fieldNumber = (lastTag.toInt() ushr 3).toUInt()

        return when (tagWireType(lastTag)) {
            0 -> UnknownField.varint(fieldNumber, readInt64())
            1 -> UnknownField.fixed64(fieldNumber, readFixed64())
            2 -> UnknownField.lengthDelimited(fieldNumber, reader.readBytes().toByteArray())
            3 -> throw UnsupportedOperationException("WIRETYPE_START_GROUP")
            4 -> throw UnsupportedOperationException("WIRETYPE_START_GROUP")
            5 -> UnknownField.fixed32(fieldNumber, readFixed32())
            else -> error("Unrecognized wire type")
        }
    }

    override fun readRepeated(packed: Boolean, acc: Reader.() -> Unit) {
        if (!packed || tagWireType(lastTag) != 2) {
            acc(this)
        } else {
            val length = readInt32()
            val endPosition = reader.pos + length
            while (reader.pos < endPosition) {
                acc(this)
            }
        }
    }

    override fun <T : Message> readMessage(m: Deserializer<T>): T =
        m.deserialize(this)
}

private fun tagWireType(tag: UInt) =
    tag.toInt() and ((1 shl 3) - 1)
