package com.toasttab.protokt.rt

interface KtEnumDeserializer<V : KtEnum> {
    fun from(value: Int): V
}
