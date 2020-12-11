package com.toasttab.protokt.ext

class CachingReference<S : Any, T : Any>(
    @Volatile private var ref: Any,
    private val converter: Converter<S, T>
) {
    val wrapped: S
        get() =
            ref.let {
                if (converter.wrapper == it::class) {
                    @Suppress("UNCHECKED_CAST")
                    it as S
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val converted = converter.wrap(it as T)
                    ref = converted
                    converted
                }
            }

    val unwrapped: T
        get() =
            ref.let {
                if (converter.wrapped == it::class) {
                    @Suppress("UNCHECKED_CAST")
                    it as T
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val converted = converter.unwrap(it as S)
                    ref = converted
                    converted
                }
            }
}
