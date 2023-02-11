package com.toasttab.protokt

expect interface KtDeserializer<T> {
    fun deserialize(deserializer: KtMessageDeserializer): T

    fun deserialize(bytes: Bytes): T

    fun deserialize(bytes: ByteArray): T

    fun deserialize(bytes: BytesSlice): T

    fun deserialize(deserializer: com.toasttab.protokt.rt.KtMessageDeserializer): T

    fun deserialize(bytes: com.toasttab.protokt.rt.Bytes): T

    fun deserialize(bytes: com.toasttab.protokt.rt.BytesSlice): T
}
