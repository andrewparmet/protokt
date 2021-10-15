/*
 * Copyright (c) 2019 Toast Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.toasttab.protokt.codegen.annotators

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.toasttab.protokt.codegen.annotators.Annotator.Context
import com.toasttab.protokt.codegen.impl.Nullability.hasNonNullOption
import com.toasttab.protokt.codegen.impl.Wrapper.interceptFieldSizeof
import com.toasttab.protokt.codegen.impl.Wrapper.interceptSizeof
import com.toasttab.protokt.codegen.impl.Wrapper.interceptValueAccess
import com.toasttab.protokt.codegen.impl.Wrapper.mapKeyConverter
import com.toasttab.protokt.codegen.impl.Wrapper.mapValueConverter
import com.toasttab.protokt.codegen.protoc.Message
import com.toasttab.protokt.codegen.protoc.Oneof
import com.toasttab.protokt.codegen.protoc.StandardField
import com.toasttab.protokt.codegen.template.ConditionalParams
import com.toasttab.protokt.codegen.template.Message.Message.SizeofInfo

internal class MessageSizeAnnotator
private constructor(
    private val msg: Message,
    private val ctx: Context
) {
    private val resultVarName =
        run {
            var name = "result"
            while (msg.fields.any { it.fieldName == name }) {
                name += "_"
            }
            name
        }

    private fun annotateMessageSizeNew(): FunSpec {
        val fieldSizes =
            msg.fields.map {
                when (it) {
                    is StandardField ->
                        if (!it.hasNonNullOption) {
                            """
                                |if ${it.nonDefault(ctx)} {
                                |  $resultVarName += ${sizeOfString(it)}
                                |}
                            """.trimMargin()
                        } else {
                            "$resultVarName += ${sizeOfString(it)}"
                        }.replace(" ", "·")
                    is Oneof ->
                        if (it.hasNonNullOption) {
                            // TODO: verify indentation is correct for this case
                            "$resultVarName +=\n"
                        } else {
                            ""
                        } +
                            """
                            |when (${it.fieldName}) {
                            |${conditionals(it)}
                            |}
                        """.trimMargin()
                }
            }

        return FunSpec.builder("messageSize")
            .addModifiers(KModifier.PRIVATE)
            .returns(Int::class)
            .addCode(
                if (fieldSizes.isEmpty()) {
                    "return unknownFields.size()"
                } else {
                    """
                        |var $resultVarName = 0
                        |${fieldSizes.joinToString("\n")}
                        |$resultVarName += unknownFields.size()
                        |return $resultVarName
                    """.trimMargin()
                }
            )
            .build()
    }

    private fun conditionals(f: Oneof) =
        f.fields
            .sortedBy { it.number }.joinToString("\n") {
                """
                    |  is ${condition(f, it, msg.name)} ->
                    |    ${oneofSizeOfString(f, it)}
                """.trimMargin()
            }

    private fun condition(f: Oneof, ff: StandardField, type: String) =
        "${oneOfScope(f, type, ctx)}.${f.fieldTypeNames.getValue(ff.name)}"

    private fun oneofSizeOfString(o: Oneof, f: StandardField) =
        if (!o.hasNonNullOption) {
            "$resultVarName += "
        } else {
            ""
        } +
            sizeOfString(
                f,
                interceptSizeof(
                    f,
                    "${o.fieldName}.${f.fieldName}",
                    ctx
                )
            )

    private fun annotateMessageSizeOld(): List<SizeofInfo> {
        return msg.fields.map {
            when (it) {
                is StandardField ->
                    SizeofInfo(
                        true,
                        it.fieldName,
                        !it.hasNonNullOption,
                        listOf(
                            ConditionalParams(
                                it.nonDefault(ctx),
                                sizeOfString(it)
                            )
                        )
                    )
                is Oneof ->
                    SizeofInfo(
                        false,
                        it.fieldName,
                        !it.hasNonNullOption,
                        oneofSize(it, msg.name)
                    )
            }
        }
    }

    private fun sizeOfString(
        f: StandardField,
        oneOfFieldAccess: String? = null
    ): String {
        val name =
            oneOfFieldAccess
                ?: if (f.repeated) {
                    f.fieldName
                } else {
                    interceptSizeof(f, f.fieldName, ctx)
                }

        return when {
            f.map -> sizeOfMap(f, name)
            f.repeated && f.packed -> """
                |sizeof(Tag(${f.number})) + 
                |        $name
                |            .sumOf { sizeof(${f.box("it")}) }
                |            .let { it + sizeof(UInt32(it)) }
                """.trimMargin()
            f.repeated ->
                "(sizeof(Tag(${f.number})) * $name.size) + " +
                    "$name.sumOf { sizeof(${f.box(interceptValueAccess(f, ctx, "it"))}) }"
            else -> "sizeof(Tag(${f.number})) + ${interceptFieldSizeof(f, name, ctx)}"
        }
    }

    private fun sizeOfMap(f: StandardField, name: String): String {
        val key = mapKeyConverter(f, ctx)?.let { "$it.unwrap(k)" } ?: "k"
        val value = mapValueConverter(f, ctx)?.let { "$it.unwrap(v)" }?.let { f.maybeConstructBytes(it) } ?: "v"
        return """
            |sizeofMap($name, Tag(${f.number})) { k, v ->
            |    ${f.unqualifiedNestedTypeName(ctx)}.sizeof($key, $value)
            |}""".trimMargin()
    }

    private fun oneofSize(f: Oneof, type: String) =
        f.fields.map {
            ConditionalParams(
                "${oneOfScope(f, type, ctx)}.${f.fieldTypeNames.getValue(it.name)}",
                sizeOfString(
                    it,
                    interceptSizeof(
                        it,
                        "${f.fieldName}.${it.fieldName}",
                        ctx
                    )
                )
            )
        }

    companion object {
        fun annotateMessageSizeOld(msg: Message, ctx: Context) =
            MessageSizeAnnotator(msg, ctx).annotateMessageSizeOld()

        fun annotateMessageSizeNew(msg: Message, ctx: Context) =
            MessageSizeAnnotator(msg, ctx).annotateMessageSizeNew()
    }
}
