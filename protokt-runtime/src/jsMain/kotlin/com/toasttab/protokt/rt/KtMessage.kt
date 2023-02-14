package com.toasttab.protokt.rt

import com.toasttab.protokt.KtMessageSerializer

actual interface KtMessage {
    actual val messageSize: Int

    actual fun serialize(serializer: KtMessageSerializer)

    actual fun serialize(): ByteArray
}
