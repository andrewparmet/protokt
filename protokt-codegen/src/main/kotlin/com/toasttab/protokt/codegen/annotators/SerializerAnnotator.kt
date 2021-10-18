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

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.buildCodeBlock
import com.toasttab.protokt.codegen.annotators.Annotator.Context
import com.toasttab.protokt.codegen.impl.Nullability.hasNonNullOption
import com.toasttab.protokt.codegen.impl.Wrapper.interceptValueAccess
import com.toasttab.protokt.codegen.impl.runtimeFunction
import com.toasttab.protokt.codegen.protoc.Message
import com.toasttab.protokt.codegen.protoc.Oneof
import com.toasttab.protokt.codegen.protoc.StandardField
import com.toasttab.protokt.codegen.template.ConditionalParams
import com.toasttab.protokt.codegen.template.Message.Message.SerializerInfo
import com.toasttab.protokt.rt.KtMessageSerializer
import com.toasttab.protokt.rt.Tag
import com.toasttab.protokt.rt.UInt32

internal class SerializerAnnotator
private constructor(
    private val msg: Message,
    private val ctx: Context
) {
    private fun annotateSerializerNew(): FunSpec {
        val fieldSerializations =
            msg.fields.map {
                when (it) {
                    is StandardField ->
                        if (!it.hasNonNullOption) {
                            """
                                |if ${it.nonDefault(ctx)} {
                                |    ${serializeString(it)}
                                |}
                            """.trimMargin()
                        } else {
                            serializeString(it)
                        }
                    is Oneof ->
                        """
                            |when (${it.fieldName}) {
                            |${conditionals(it)}
                            |}
                        """.trimMargin()
                }
            }

        return FunSpec.builder("serialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("serializer", KtMessageSerializer::class)
            .addCode(
                if (fieldSerializations.isEmpty()) {
                    "serializer.writeUnknown(unknownFields)"
                } else {
                    """
                        |${fieldSerializations.joinToString("\n")}
                        |serializer.writeUnknown(unknownFields)
                    """.trimMargin()
                }
            )
            .build()
    }

    private fun conditionals(f: Oneof) =
        f.fields
            .sortedBy { it.number }.joinToString("\n") {
                """
                    |    is ${oneOfSer(f, it, msg.name).condition} ->
                    |        ${serializeString(it, Some(f.fieldName))}
                """.trimMargin()
            }

    private fun annotateSerializerOld(): List<SerializerInfo> {
        return msg.fields.map {
            when (it) {
                is StandardField ->
                    SerializerInfo(
                        true,
                        it.fieldName,
                        !it.hasNonNullOption,
                        listOf(
                            ConditionalParams(
                                it.nonDefault(ctx),
                                serializeString(it)
                            )
                        )
                    )
                is Oneof ->
                    SerializerInfo(
                        false,
                        it.fieldName,
                        !it.hasNonNullOption,
                        it.fields
                            .sortedBy { f -> f.number }
                            .map { f -> oneOfSer(it, f, msg.name) }
                    )
            }
        }
    }

    private fun serializeString(
        f: StandardField,
        t: Option<String> = None
    ): CodeBlock {
        val fieldAccess =
            t.fold(
                {
                    interceptValueAccess(
                        f,
                        ctx,
                        if (f.repeated) { "it" } else { f.fieldName }
                    )
                },
                {
                    interceptValueAccess(
                        f,
                        ctx,
                        "$it.${f.fieldName}"
                    )
                }
            )

        val map = LinkedHashMap<String, Any>()
        map += "tag" to Tag::class
        map += "uInt32" to UInt32::class
        map += "name" to f.fieldName
        map += "sizeof" to runtimeFunction("sizeof")
        return when {

            f.repeated && f.packed -> buildCodeBlock {
                map += "boxed" to f.box("it")
                addNamed(
                    "serializer.write(%tag:T(${f.tag.value}))" +
                        ".write(%uInt32:T(%name:L.sumOf{%sizeof:M(%boxed:L)}))\n",
                    map
                )
                addNamed("%name:L.forEach·{ serializer.write(%boxed:L) }", map)
            }
            f.map -> buildCodeBlock {
                map += "boxed" to f.boxMap(ctx)
                addNamed(
                    "%name:L.entries.forEach { " +
                        "serializer.write(%tag:T(${f.tag.value}))" +
                        ".write(%boxed:L) }",
                    map
                )
            }
            f.repeated -> buildCodeBlock {
                map += "boxed" to f.box(fieldAccess)
                addNamed(
                    "%name:L.forEach { " +
                        "serializer.write(%tag:T(${f.tag.value})).write(%boxed:L) }",
                    map
                )
            }

            else -> buildCodeBlock {
                map += "boxed" to f.box(fieldAccess)
                addNamed("serializer.write(%tag:T(${f.tag.value})).write(%boxed:L)", map)
            }
        }
    }

    private fun oneOfSer(f: Oneof, ff: StandardField, type: String) =
        ConditionalParams(
            CodeBlock.of(
                "%L.%L", oneOfScope(f, type, ctx), f.fieldTypeNames.getValue(ff.fieldName)
            ),
            serializeString(ff, Some(f.fieldName))
        )

    companion object {
        fun annotateSerializerOld(msg: Message, ctx: Context) =
            SerializerAnnotator(msg, ctx).annotateSerializerOld()

        fun annotateSerializerNew(msg: Message, ctx: Context) =
            SerializerAnnotator(msg, ctx).annotateSerializerNew()
    }
}
