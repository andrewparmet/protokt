package com.toasttab.protokt

import com.google.protobuf.CodedInputStream
import java.io.InputStream
import java.nio.ByteBuffer

actual interface KtDeserializer<T : KtMessage> {
    actual fun deserialize(bytes: Bytes): T

    actual fun deserialize(bytes: ByteArray): T

    actual fun deserialize(bytes: BytesSlice): T

    actual fun deserialize(deserializer: KtMessageDeserializer): T

    fun deserialize(bytes: com.toasttab.protokt.rt.Bytes): T =
        deserialize(bytes.value)

    fun deserialize(bytes: com.toasttab.protokt.rt.BytesSlice): T =
        deserialize(
            deserializer(
                CodedInputStream.newInstance(
                    bytes.array,
                    bytes.offset,
                    bytes.length
                )
            )
        )

    fun deserialize(deserializer: com.toasttab.protokt.rt.KtMessageDeserializer): T =
        deserialize(OldToNewAdapter(deserializer))

    fun deserialize(stream: InputStream): T =
        deserialize(deserializer(CodedInputStream.newInstance(stream)))

    fun deserialize(stream: CodedInputStream): T =
        deserialize(deserializer(stream))

    fun deserialize(buffer: ByteBuffer): T =
        deserialize(deserializer(CodedInputStream.newInstance(buffer)))
}
