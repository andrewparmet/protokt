package com.toasttab.protokt.rt

abstract class KtEnum {
    abstract val value: Int
    abstract val name: String

    final override fun equals(other: Any?) =
        other != null &&
            other::class == this::class &&
            (other as KtEnum).value == value

    final override fun hashCode() =
        value

    final override fun toString() =
        name
}
