package com.toasttab.protokt.rt

@Target(AnnotationTarget.CLASS)
annotation class KtGeneratedMessage(
    /**
     * The full protocol buffer type name of this message used for packing into an Any.
     */
    val fullTypeName: String
)
