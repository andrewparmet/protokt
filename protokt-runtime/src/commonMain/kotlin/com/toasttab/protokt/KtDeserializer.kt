package com.toasttab.protokt

import com.toasttab.protokt.rt.KtMessage

expect interface KtDeserializer<T : KtMessage> {
    fun deserialize(deserializer: KtMessageDeserializer): T

    fun deserialize(bytes: Bytes): T

    fun deserialize(bytes: ByteArray): T

    fun deserialize(bytes: BytesSlice): T
}
