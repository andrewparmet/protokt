package com.toasttab.protokt.codegen.annotators

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.toasttab.protokt.codegen.template.Message.Message.PropertyInfo

fun deserializeType(p: PropertyInfo) =
    if (p.repeated || p.map) {
        p.deserializeType as ParameterizedTypeName
        ClassName(p.deserializeType.rawType.packageName, "Mutable" + p.deserializeType.rawType.simpleName)
            .parameterizedBy(p.deserializeType.typeArguments)
            .copy(nullable = true)
    } else {
        p.deserializeType
    }

fun deserializeValue(p: PropertyInfo) =
    if (p.repeated || p.wrapped || p.nullable || p.fieldType == "MESSAGE") {
        "null"
    } else {
        p.defaultValue
    }

fun deserializeVar(p: PropertyInfo) =
    p.name +
        if (p.fieldType == "MESSAGE" || p.repeated || p.oneof || p.nullable || p.wrapped) {
            ": " + deserializeType(p)
        } else {
            ""
        } +
        " = " + deserializeValue(p)

fun deserializeWrapper(p: PropertyInfo) =
    if (p.nonNullOption) {
        """
            requireNotNull(${p.name}) {
                StringBuilder("${p.name}")
                    .append(" specified nonnull with (protokt.${if (p.oneof) "oneof" else "property" }).non_null but was null")
            }
        """.trimIndent()
    } else {
        if (p.map) {
            "finishMap(${p.name})"
        } else if (p.repeated) {
            "finishList(${p.name})"
        } else {
            p.name +
                if (p.wrapped && !p.nullable) {
                    " ?: ${p.defaultValue}"
                } else {
                    ""
                }
        }
    }
