package com.toasttab.protokt

/**
 * Base type for all Kotlin generated types.
 */
expect interface KtMessage : com.toasttab.protokt.rt.KtMessage {
    override val messageSize: Int

    override fun serialize(serializer: KtMessageSerializer)

    override fun serialize(): ByteArray
}
