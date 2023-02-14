package com.toasttab.protokt

import com.google.protobuf.CodedInputStream
import java.io.InputStream
import java.nio.ByteBuffer

actual interface KtDeserializer<T : com.toasttab.protokt.rt.KtMessage> : com.toasttab.protokt.rt.KtDeserializer<T> {
    actual override fun deserialize(bytes: Bytes): T

    actual override fun deserialize(bytes: ByteArray): T

    actual override fun deserialize(bytes: BytesSlice): T

    actual override fun deserialize(deserializer: KtMessageDeserializer): T

    override fun deserialize(bytes: com.toasttab.protokt.rt.Bytes): T =
        deserialize(bytes.value)

    override fun deserialize(bytes: com.toasttab.protokt.rt.BytesSlice): T =
        deserialize(
            deserializer(
                CodedInputStream.newInstance(
                    bytes.array,
                    bytes.offset,
                    bytes.length
                )
            )
        )

    override fun deserialize(deserializer: com.toasttab.protokt.rt.KtMessageDeserializer): T =
        deserialize(OldToNewAdapter(deserializer))

    override fun deserialize(stream: InputStream): T =
        deserialize(deserializer(CodedInputStream.newInstance(stream)))

    override fun deserialize(stream: CodedInputStream): T =
        deserialize(deserializer(stream))

    override fun deserialize(buffer: ByteBuffer): T =
        deserialize(deserializer(CodedInputStream.newInstance(buffer)))
}
