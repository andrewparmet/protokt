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

import com.toasttab.protokt.codegen.annotators.Annotator.Context
import com.toasttab.protokt.codegen.annotators.PropertyDocumentationAnnotator.Companion.annotatePropertyDocumentation
import com.toasttab.protokt.codegen.impl.Deprecation.renderOptions
import com.toasttab.protokt.codegen.impl.Implements.overrides
import com.toasttab.protokt.codegen.impl.Nullability.deserializeType
import com.toasttab.protokt.codegen.impl.Nullability.dslPropertyType
import com.toasttab.protokt.codegen.impl.Nullability.hasNonNullOption
import com.toasttab.protokt.codegen.impl.Nullability.nullable
import com.toasttab.protokt.codegen.impl.Nullability.propertyType
import com.toasttab.protokt.codegen.impl.Nullability.renderNullableType
import com.toasttab.protokt.codegen.impl.Wrapper.interceptDefaultValue
import com.toasttab.protokt.codegen.impl.Wrapper.interceptTypeName
import com.toasttab.protokt.codegen.impl.Wrapper.wrapped
import com.toasttab.protokt.codegen.model.FieldType
import com.toasttab.protokt.codegen.protoc.Field
import com.toasttab.protokt.codegen.protoc.Message
import com.toasttab.protokt.codegen.protoc.Oneof
import com.toasttab.protokt.codegen.protoc.StandardField
import com.toasttab.protokt.codegen.template.Message.Message.PropertyInfo
import com.toasttab.protokt.codegen.template.Renderers.Standard

/**
 *
 */
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
                            pClass = it.typePClass,
                            propertyType = propertyType(it, type),
                            deserializeType = deserializeType(it, type),
                            dslPropertyType = dslPropertyType(it, type),
                            defaultValue = it.defaultValue(ctx),
                            fieldType = it.type.toString(),
                            repeated = it.repeated,
                            map = it.map,
                            nullable = it.nullable || it.optional,
                            nonNullOption = it.hasNonNullOption,
                            overrides = it.overrides(ctx, msg),
                            wrapped = it.wrapped,
                            documentation = documentation,
                            deprecation = deprecation(it)
                        )
                    }
                }
                is Oneof ->
                    PropertyInfo(
                        name = it.fieldName,
                        propertyType = propertyType(it),
                        deserializeType = it.renderNullableType(),
                        dslPropertyType = it.renderNullableType(),
                        defaultValue = it.defaultValue(ctx),
                        oneof = true,
                        nullable = it.nullable,
                        nonNullOption = it.hasNonNullOption,
                        documentation = documentation
                    )
            }
        }
    }

    private fun deprecation(f: StandardField) =
        if (f.options.default.deprecated) {
            renderOptions(
                f.options.protokt.deprecationMessage
            )
        } else {
            null
        }

    private fun annotateStandard(f: StandardField) =
        Standard.render(
            field = f,
            any =
            if (f.map) {
                resolveMapEntryTypes(f, ctx)
            } else {
                interceptTypeName(
                    f,
                    f.typePClass.renderName(ctx.pkg),
                    ctx
                )
            }
        ).let {
            if (it == "Any") {
                f.typePClass.qualifiedName
            } else {
                it
            }
        }

    private fun Field.defaultValue(ctx: Context) =
        when (this) {
            is StandardField ->
                interceptDefaultValue(
                    this,
                    when {
                        this.map -> "emptyMap()"
                        this.repeated -> "emptyList()"
                        type == FieldType.MESSAGE -> "null"
                        type == FieldType.ENUM -> "${name(this)}.from(0)"
                        this.nullable -> "null"
                        else -> this.type.defaultValue
                    },
                    ctx
                )
            is Oneof -> "null"
        }

    private fun name(f: StandardField) = when (f.type) {
        FieldType.ENUM -> f.typePClass.renderName(ctx.pkg)
        else -> ""
    }

    companion object {
        fun annotateProperties(msg: Message, ctx: Context) =
            PropertyAnnotator(msg, ctx).annotateProperties()
    }
}
