package com.toasttab.protokt.rt

import com.toasttab.protokt.KtMessageSerializer

expect interface KtMessage {
    val messageSize: Int

    fun serialize(serializer: KtMessageSerializer)

    fun serialize(): ByteArray
}
