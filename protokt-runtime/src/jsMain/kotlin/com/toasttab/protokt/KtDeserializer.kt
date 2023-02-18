package com.toasttab.protokt

import org.khronos.webgl.Uint8Array

actual interface KtDeserializer<T : KtMessage> {
    actual fun deserialize(bytes: Bytes): T

    actual fun deserialize(bytes: ByteArray): T

    actual fun deserialize(bytes: BytesSlice): T

    actual fun deserialize(deserializer: KtMessageDeserializer): T

    fun deserialize(bytes: Uint8Array): T =
        deserialize(deserializer(Reader.create(bytes)))
}
