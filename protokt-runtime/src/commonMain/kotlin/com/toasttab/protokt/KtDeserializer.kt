package com.toasttab.protokt

expect interface KtDeserializer<T : KtMessage> {
    fun deserialize(deserializer: KtMessageDeserializer): T

    fun deserialize(bytes: Bytes): T

    fun deserialize(bytes: ByteArray): T

    fun deserialize(bytes: BytesSlice): T
}
