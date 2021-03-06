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

package com.toasttab.protokt.codegen.impl

import com.toasttab.protokt.codegen.EnumType
import com.toasttab.protokt.codegen.TypeDesc
import com.toasttab.protokt.codegen.algebra.AST
import com.toasttab.protokt.codegen.impl.Deprecation.enclosingDeprecation
import com.toasttab.protokt.codegen.impl.Deprecation.hasDeprecation
import com.toasttab.protokt.codegen.impl.Deprecation.renderOptions
import com.toasttab.protokt.codegen.impl.EnumDocumentationAnnotator.Companion.annotateEnumDocumentation
import com.toasttab.protokt.codegen.impl.EnumDocumentationAnnotator.Companion.annotateEnumFieldDocumentation
import com.toasttab.protokt.codegen.impl.STAnnotator.Context

internal object EnumAnnotator {
    fun annotateEnum(
        ast: AST<TypeDesc>,
        e: EnumType,
        ctx: Context
    ): AST<TypeDesc> {
        ast.data.type.template.map { t ->
            STTemplate.addTo(t as STTemplate, EnumSt) { f ->
                when (f) {
                    is NameEnumVar -> e.name
                    is MapEnumVar ->
                        e.values.associate {
                            it.number to
                                EnumValueData(
                                    it.valueName,
                                    annotateEnumFieldDocumentation(e, it, ctx),
                                    if (it.options.default.deprecated) {
                                        renderOptions(
                                            it.options.protokt.deprecationMessage
                                        )
                                    } else {
                                        null
                                    }
                                )
                        }
                    is OptionsEnumVar ->
                        EnumOptions(
                            documentation = annotateEnumDocumentation(e, ctx),
                            deprecation =
                                if (e.options.default.deprecated) {
                                    renderOptions(
                                        e.options.protokt.deprecationMessage
                                    )
                                } else {
                                    null
                                },
                            suppressDeprecation =
                                (e.hasDeprecation && !enclosingDeprecation(ctx))
                        )
                }
            }
        }
        return ast
    }

    private data class EnumValueData(
        val valueName: String,
        val documentation: List<String>,
        val deprecation: Deprecation.RenderOptions?
    )

    private data class EnumOptions(
        val documentation: List<String>,
        val deprecation: Deprecation.RenderOptions?,
        val suppressDeprecation: Boolean
    )
}
