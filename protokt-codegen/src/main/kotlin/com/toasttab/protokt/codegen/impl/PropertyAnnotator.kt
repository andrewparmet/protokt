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

import com.toasttab.protokt.codegen.impl.Deprecation.renderOptions
import com.toasttab.protokt.codegen.impl.Implements.overrides
import com.toasttab.protokt.codegen.impl.Nullability.deserializeType
import com.toasttab.protokt.codegen.impl.Nullability.dslPropertyType
import com.toasttab.protokt.codegen.impl.Nullability.hasNonNullOption
import com.toasttab.protokt.codegen.impl.Nullability.nullable
import com.toasttab.protokt.codegen.impl.Nullability.renderNullableType
import com.toasttab.protokt.codegen.impl.PropertyDocumentationAnnotator.Companion.annotatePropertyDocumentation
import com.toasttab.protokt.codegen.impl.STAnnotator.Context
import com.toasttab.protokt.codegen.impl.Wrapper.converter
import com.toasttab.protokt.codegen.impl.Wrapper.foldFieldWrap
import com.toasttab.protokt.codegen.impl.Wrapper.interceptDefaultValue
import com.toasttab.protokt.codegen.impl.Wrapper.interceptTypeName
import com.toasttab.protokt.codegen.impl.Wrapper.wrapped
import com.toasttab.protokt.codegen.model.FieldType
import com.toasttab.protokt.codegen.protoc.Field
import com.toasttab.protokt.codegen.protoc.Message
import com.toasttab.protokt.codegen.protoc.Oneof
import com.toasttab.protokt.codegen.protoc.StandardField
import com.toasttab.protokt.codegen.template.Message.Message.PropertyInfo
import com.toasttab.protokt.codegen.template.Oneof as OneofTemplate
import com.toasttab.protokt.codegen.template.Renderers.DefaultValue
import com.toasttab.protokt.codegen.template.Renderers.Standard

internal class PropertyAnnotator
private constructor(
    private val msg: Message,
    private val ctx: Context
) {
    private fun annotateProperties(): List<PropertyInfo> {
        return msg.fields.map {
            val documentation = annotatePropertyDocumentation(it, ctx)

            when (it) {
                is StandardField -> {
                    annotateStandard(it).let { type ->
                        PropertyInfo(
                            name = it.fieldName,
                            propertyType = if (type == "ByteArray" && !it.wrapped) "Bytes" else type,
                            deserializeType =
                                deserializeType(
                                    it,
                                    it.foldFieldWrap(
                                        ctx,
                                        { type },
                                        { _, wrapped -> wrapped.simpleName!! }
                                    )
                                ),
                            dslPropertyType = dslPropertyType(it, type),
                            defaultValue = it.defaultValue(ctx),
                            fieldType = it.type.toString(),
                            wireRepresentationType =
                                it.foldFieldWrap(
                                    ctx,
                                    { null },
                                    { _, wrapped -> wrapped.simpleName }
                                ),
                            repeated = it.repeated,
                            map = it.map,
                            nullable = it.nullable || it.optional,
                            nonNullOption = it.hasNonNullOption,
                            overrides = it.overrides(ctx, msg),
                            wrapped = it.wrapped,
                            converterName =
                                it.foldFieldWrap(
                                    ctx,
                                    { null },
                                    { wrapper, wrapped ->
                                        converter(wrapper, wrapped, ctx.desc.context)::class.simpleName
                                    }
                                ),
                            documentation = documentation,
                            deprecation =
                                if (it.options.default.deprecated) {
                                    renderOptions(
                                        it.options.protokt.deprecationMessage
                                    )
                                } else {
                                    null
                                }
                        )
                    }
                }
                is Oneof ->
                    PropertyInfo(
                        name = it.fieldName,
                        propertyType = it.name,
                        deserializeType = it.renderNullableType(),
                        dslPropertyType = it.renderNullableType(),
                        defaultValue = it.defaultValue(ctx),
                        nullable = it.nullable,
                        nonNullOption = it.hasNonNullOption,
                        documentation = documentation
                    )
            }
        }
    }

    private fun annotateStandard(f: StandardField) =
        Standard.render(
            field = f,
            bytes = f.type == FieldType.BYTES,
            wrapped = f.wrapped,
            any =
                if (f.map) {
                    resolveMapEntryTypes(f, ctx)
                } else {
                    interceptTypeName(
                        f,
                        f.typePClass.qualifiedName,
                        ctx
                    )
                }
        )

    private fun Field.defaultValue(ctx: Context) =
        when (this) {
            is StandardField ->
                interceptDefaultValue(
                    this,
                    DefaultValue.render(
                        field = this,
                        type = type,
                        name =
                            if (type == FieldType.ENUM) {
                                typePClass.renderName(ctx.pkg)
                            } else {
                                ""
                            },
                        wrapped = wrapped
                    ),
                    ctx
                )
            is Oneof ->
                OneofTemplate.DefaultValue.render()
        }

    companion object {
        fun annotateProperties(msg: Message, ctx: Context) =
            PropertyAnnotator(msg, ctx).annotateProperties()
    }
}
