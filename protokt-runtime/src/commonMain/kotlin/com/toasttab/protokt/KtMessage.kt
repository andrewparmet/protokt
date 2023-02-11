package com.toasttab.protokt

/**
 * Base type for all Kotlin generated types.
 */
expect interface KtMessage {
    val messageSize: Int

    fun serialize(serializer: KtMessageSerializer)

    fun serialize(): ByteArray
}
