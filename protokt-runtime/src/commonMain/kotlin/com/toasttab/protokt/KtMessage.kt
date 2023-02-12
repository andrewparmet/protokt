package com.toasttab.protokt

import com.toasttab.protokt.rt.KtMessage

/**
 * Base type for all Kotlin generated types.
 */
expect interface KtMessage : KtMessage {
    fun serialize(serializer: KtMessageSerializer)
}
