package com.toasttab.protokt

import com.google.protobuf.CodedInputStream
import java.io.InputStream
import java.nio.ByteBuffer

actual interface KtDeserializer<T> {
    actual fun deserialize(bytes: Bytes): T

    actual fun deserialize(bytes: ByteArray): T

    actual fun deserialize(bytes: BytesSlice): T

    actual fun deserialize(deserializer: KtMessageDeserializer): T

    actual fun deserialize(bytes: com.toasttab.protokt.rt.Bytes): T

    actual fun deserialize(bytes: com.toasttab.protokt.rt.BytesSlice): T

    actual fun deserialize(deserializer: com.toasttab.protokt.rt.KtMessageDeserializer): T

    fun deserialize(stream: InputStream): T =
        deserialize(deserializer(CodedInputStream.newInstance(stream)))

    fun deserialize(stream: CodedInputStream): T =
        deserialize(deserializer(stream))

    fun deserialize(buffer: ByteBuffer): T =
        deserialize(deserializer(CodedInputStream.newInstance(buffer)))

    @Deprecated("for ABI backwards compatibility only", level = DeprecationLevel.HIDDEN)
    object DefaultImpls {
        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, bytes: ByteArray) =
            deserializer.deserialize(deserializer(CodedInputStream.newInstance(bytes), bytes))

        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, bytes: Bytes) =
            deserializer.deserialize(bytes.value)

        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, bytes: BytesSlice) =
            deserializer.deserialize(
                deserializer(
                    CodedInputStream.newInstance(
                        bytes.array,
                        bytes.offset,
                        bytes.length
                    )
                )
            )

        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, buffer: ByteBuffer) =
            deserializer.deserialize(deserializer(CodedInputStream.newInstance(buffer)))

        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, stream: CodedInputStream) =
            deserializer.deserialize(deserializer(stream))

        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, stream: InputStream) =
            deserializer.deserialize(deserializer(CodedInputStream.newInstance(stream)))
    }
}
