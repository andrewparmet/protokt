package com.toasttab.protokt.codegen.impl

import com.toasttab.protokt.codegen.MessageType
import com.toasttab.protokt.codegen.TypeDesc
import com.toasttab.protokt.codegen.algebra.AST
import com.toasttab.protokt.codegen.model.PClass

object DuplicateNameInFileImportFilterer {
    fun anyMessageInAstsHasName(asts: List<AST<TypeDesc>>, pClass: PClass) =
        allMessageNames(asts).contains(pClass.simpleName)

    private fun allMessageNames(asts: List<AST<TypeDesc>>) =
        asts.flatMapToSet {
            when (val t = it.data.type.rawType) {
                is MessageType -> names(t)
                else -> emptySet()
            }
        }

    private fun names(m: MessageType): Set<String> =
        setOf(m.name) + m.nestedTypes.filterIsInstance<MessageType>().flatMap { names(it) }
}
