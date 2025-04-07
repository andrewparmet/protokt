package protokt.v1

import com.squareup.wire.ProtoReader
import com.squareup.wire.internal.ProtocolException
import kotlin.Throws
import okio.BufferedSource
import okio.ByteString
import okio.EOFException
import okio.IOException

/**
 * Reads and decodes protocol message fields.
 */
// extends ProtoReader so we can use ProtoAdatapter.decode methods and avoid owning zigzag decoding
internal class WireProtoReader(
    private val source: BufferedSource
): ProtoReader(source) {
    /** The current position in the input source, starting at 0 and increasing monotonically. */
    internal var pos: Long = 0

    /** The absolute position of the end of the current message. */
    internal var limit = Long.MAX_VALUE

    /** Limit once we complete the current length-delimited value. */
    private var pushedLimit: Long = -1

    /**
     * Reads a `bytes` field value from the stream. The length is read from the stream prior to the
     * actual data.
     */
    @Throws(IOException::class)
    override fun readBytes(): ByteString {
        val byteCount = beforeLengthDelimitedScalar()
        source.require(byteCount) // Throws EOFException if insufficient bytes are available.
        return source.readByteString(byteCount)
    }

    /** Reads a `string` field value from the stream. */
    @Throws(IOException::class)
    override fun readString(): String {
        val byteCount = beforeLengthDelimitedScalar()
        source.require(byteCount) // Throws EOFException if insufficient bytes are available.
        return source.readUtf8(byteCount)
    }

    /**
     * Reads a raw varint from the stream. If larger than 32 bits, discard the upper bits.
     */
    @Throws(IOException::class)
    override fun readVarint32(): Int {
        val result = internalReadVarint32()
        afterPackableScalar()
        return result
    }

    private fun internalReadVarint32(): Int {
        source.require(1) // Throws EOFException if insufficient bytes are available.
        pos++
        var tmp = source.readByte()
        if (tmp >= 0) {
            return tmp.toInt()
        }
        var result = tmp and 0x7f
        source.require(1) // Throws EOFException if insufficient bytes are available.
        pos++
        tmp = source.readByte()
        if (tmp >= 0) {
            result = result or (tmp shl 7)
        } else {
            result = result or (tmp and 0x7f shl 7)
            source.require(1) // Throws EOFException if insufficient bytes are available.
            pos++
            tmp = source.readByte()
            if (tmp >= 0) {
                result = result or (tmp shl 14)
            } else {
                result = result or (tmp and 0x7f shl 14)
                source.require(1) // Throws EOFException if insufficient bytes are available.
                pos++
                tmp = source.readByte()
                if (tmp >= 0) {
                    result = result or (tmp shl 21)
                } else {
                    result = result or (tmp and 0x7f shl 21)
                    source.require(1) // Throws EOFException if insufficient bytes are available.
                    pos++
                    tmp = source.readByte()
                    result = result or (tmp shl 28)
                    if (tmp < 0) {
                        // Discard upper 32 bits.
                        for (i in 0..4) {
                            source.require(1) // Throws EOFException if insufficient bytes are available.
                            pos++
                            if (source.readByte() >= 0) {
                                return result
                            }
                        }
                        throw ProtocolException("Malformed VARINT")
                    }
                }
            }
        }
        return result
    }

    /** Reads a raw varint up to 64 bits in length from the stream.  */
    @Throws(IOException::class)
    override fun readVarint64(): Long {
        var shift = 0
        var result: Long = 0
        while (shift < 64) {
            source.require(1) // Throws EOFException if insufficient bytes are available.
            pos++
            val b = source.readByte()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) {
                afterPackableScalar()
                return result
            }
            shift += 7
        }
        throw ProtocolException("WireInput encountered a malformed varint")
    }

    /** Reads a 32-bit little-endian integer from the stream.  */
    @Throws(IOException::class)
    override fun readFixed32(): Int {
        source.require(4) // Throws EOFException if insufficient bytes are available.
        pos += 4
        val result = source.readIntLe()
        afterPackableScalar()
        return result
    }

    /** Reads a 64-bit little-endian integer from the stream.  */
    @Throws(IOException::class)
    override fun readFixed64(): Long {
        source.require(8) // Throws EOFException if insufficient bytes are available.
        pos += 8
        val result = source.readLongLe()
        afterPackableScalar()
        return result
    }

    @Throws(IOException::class)
    private fun afterPackableScalar() {
        when {
            pos > limit -> throw IOException("Expected to end at $limit but was $pos")
            pos == limit -> {
                // We've completed a sequence of packed values. Pop the limit.
                limit = pushedLimit
                pushedLimit = -1
            }
        }
    }

    private fun beforeLengthDelimitedScalar(): Long {
        val length = internalReadVarint32()
        if (length < 0) throw ProtocolException("Negative length: $length")
        if (pushedLimit != -1L) throw IllegalStateException()
        // Push the current limit, and set a new limit to the length of this value.
        pushedLimit = limit
        limit = pos + length
        if (limit > pushedLimit) throw EOFException()

        val byteCount = limit - pos
        source.require(byteCount) // Throws EOFException if insufficient bytes are available.
        // We've completed a length-delimited scalar. Pop the limit.
        pos = limit
        limit = pushedLimit
        pushedLimit = -1
        return byteCount
    }

    override fun beginMessage(): Long {
        val token = pushedLimit
        pushedLimit = -1L
        return token
    }

    override fun endMessageAndGetUnknownFields(token: Long): ByteString {
        limit = token
        return ByteString.EMPTY
    }
}

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.and(other: Int): Int = toInt() and other

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.shl(other: Int): Int = toInt() shl other
