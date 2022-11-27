/*
 * Copyright (c) 2022 Toast Inc.
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

package com.toasttab.protokt.codegen.util

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asTypeName
import com.toasttab.protokt.ext.Protokt

class FieldParser(
    private val ctx: GeneratorContext,
    private val desc: DescriptorProto,
    private val enclosingMessages: List<String>
) {
    fun toFields(): List<Field> {
        val generatedOneofIndices = mutableSetOf<Int>()
        val fields = mutableListOf<Field>()

        desc.fieldList.forEachIndexed { idx, t ->
            if (t.type != Type.TYPE_GROUP) {
                t.oneofIndex.takeIf { t.hasOneofIndex() }?.let { oneofIndex ->
                    if (oneofIndex !in generatedOneofIndices) {
                        generatedOneofIndices.add(oneofIndex)
                        fields.add(toOneof(idx, desc, desc.getOneofDecl(oneofIndex), t, fields))
                    }
                } ?: fields.add(toStandard(idx, t))
            }
        }

        return fields
    }

    private fun toOneof(
        idx: Int,
        desc: DescriptorProto,
        oneof: OneofDescriptorProto,
        field: FieldDescriptorProto,
        fields: List<Field>
    ): Field {
        val newName = newFieldName(oneof.name)

        if (field.proto3Optional) {
            return toStandard(idx, field)
        }

        val oneofFieldDescriptors =
            desc.fieldList.filter { it.hasOneofIndex() && it.oneofIndex == field.oneofIndex }

        val oneofStdFields =
            oneofFieldDescriptors.mapIndexed { fdpIdx, fdp ->
                toStandard(idx + fdpIdx, fdp, true)
            }

        val fieldTypeNames =
            oneofStdFields.associate {
                it.fieldName to generateOneofClassName(it.fieldName)
            }

        val name = generateOneofClassName(oneof.name)

        return Oneof(
            name = name,
            className = ClassName(ctx.kotlinPackage, enclosingMessages + desc.name + name),
            fieldTypeNames = fieldTypeNames,
            fieldName = newName,
            fields = oneofStdFields,
            options = OneofOptions(
                oneof.options,
                oneof.options.getExtension(Protokt.oneof)
            ),
            // index relative to all oneofs in this message
            index = idx - fields.filterIsInstance<StandardField>().count()
        )
    }

    private fun toStandard(
        idx: Int,
        fdp: FieldDescriptorProto,
        withinOneof: Boolean = false
    ): StandardField {
        val fieldType = toFieldType(fdp.type)
        val protoktOptions = fdp.options.getExtension(Protokt.property)
        val repeated = fdp.label == LABEL_REPEATED
        val mapEntry = mapEntry(fdp)
        val optional = optional(fdp)
        val packed = packed(fieldType, fdp)
        val tag =
            if (repeated && packed) {
                Tag.Packed(fdp.number)
            } else {
                Tag.Unpacked(fdp.number, fieldType.wireType)
            }

        if (protoktOptions.nonNull) {
            validateNonNullOption(fdp, fieldType, repeated, mapEntry, withinOneof, optional)
        }

        return StandardField(
            number = fdp.number,
            tag = tag,
            type = fieldType,
            repeated = repeated,
            optional = !withinOneof && optional,
            packed = packed,
            mapEntry = mapEntry,
            fieldName = newFieldName(fdp.name),
            options = FieldOptions(fdp.options, protoktOptions),
            protoTypeName = fdp.typeName,
            className = typeName(fdp.typeName, fieldType),
            index = idx
        )
    }

    private fun mapEntry(fdp: FieldDescriptorProto) =
        if (fdp.label == LABEL_REPEATED && fdp.type == Type.TYPE_MESSAGE) {
            findMapEntry(ctx.fdp, fdp.typeName)
                ?.takeIf { it.options.mapEntry }
                ?.let { resolveMapEntry(MessageParser(ctx, -1, it, enclosingMessages).toMessage()) }
        } else {
            null
        }

    private fun resolveMapEntry(m: Message) =
        MapEntry(
            (m.fields[0] as StandardField),
            (m.fields[1] as StandardField)
        )

    private fun findMapEntry(
        fdp: FileDescriptorProto,
        name: String,
        parent: DescriptorProto? = null
    ): DescriptorProto? {
        val (typeList, typeName) =
            if (parent == null) {
                Pair(
                    fdp.messageTypeList.filterNotNull(),
                    name.removePrefix(".${fdp.`package`}.")
                )
            } else {
                parent.nestedTypeList.filterNotNull() to name
            }

        typeName.indexOf('.').let { idx ->
            return if (idx == -1) {
                typeList.firstOrNull { it.name == typeName }
            } else {
                findMapEntry(
                    fdp,
                    typeName.substring(idx + 1),
                    typeList.firstOrNull { it.name == typeName.substring(0, idx) }
                )
            }
        }
    }

    private fun typeName(protoTypeName: String, fieldType: FieldType): ClassName {
        val fullyProtoQualified = protoTypeName.startsWith(".")

        return if (fullyProtoQualified) {
            requalifyProtoType(ctx, protoTypeName)
        } else {
            protoTypeName.let {
                if (it.isEmpty()) {
                    fieldType.protoktFieldType.asTypeName()
                } else {
                    ClassName.bestGuess(it)
                }
            }
        }
    }

    private fun optional(fdp: FieldDescriptorProto) =
        (fdp.label == LABEL_OPTIONAL && ctx.proto2) || fdp.proto3Optional

    private fun packed(type: FieldType, fdp: FieldDescriptorProto) =
        type.packable &&
            // marginal support for proto2
            (
                (ctx.proto2 && fdp.options.packed) ||
                    // packed if: proto3 and `packed` isn't set, or proto3
                    // and `packed` is true. If proto3, only explicitly
                    // setting `packed` to false disables packing, since
                    // the default value for an unset boolean is false.
                    (ctx.proto3 && (!fdp.options.hasPacked() || (fdp.options.hasPacked() && fdp.options.packed)))
                )
}

private fun toFieldType(type: Type) =
    when (type) {
        Type.TYPE_BOOL -> FieldType.BOOL
        Type.TYPE_BYTES -> FieldType.BYTES
        Type.TYPE_DOUBLE -> FieldType.DOUBLE
        Type.TYPE_ENUM -> FieldType.ENUM
        Type.TYPE_FIXED32 -> FieldType.FIXED32
        Type.TYPE_FIXED64 -> FieldType.FIXED64
        Type.TYPE_FLOAT -> FieldType.FLOAT
        Type.TYPE_INT32 -> FieldType.INT32
        Type.TYPE_INT64 -> FieldType.INT64
        Type.TYPE_MESSAGE -> FieldType.MESSAGE
        Type.TYPE_SFIXED32 -> FieldType.SFIXED32
        Type.TYPE_SFIXED64 -> FieldType.SFIXED64
        Type.TYPE_SINT32 -> FieldType.SINT32
        Type.TYPE_SINT64 -> FieldType.SINT64
        Type.TYPE_STRING -> FieldType.STRING
        Type.TYPE_UINT32 -> FieldType.UINT32
        Type.TYPE_UINT64 -> FieldType.UINT64
        else -> error("Unknown type: $type")
    }

private fun validateNonNullOption(
    fdp: FieldDescriptorProto,
    type: FieldType,
    repeated: Boolean,
    mapEntry: MapEntry?,
    withinOneof: Boolean,
    optional: Boolean
) {
    fun FieldType.typeName() =
        name.lowercase()

    fun name(field: StandardField) =
        if (field.type == FieldType.ENUM) {
            field.protoTypeName
        } else {
            field.type.typeName()
        }

    val typeName =
        when (type) {
            FieldType.ENUM, FieldType.MESSAGE -> fdp.typeName
            else -> type.typeName()
        }

    require(!optional) {
        "(protokt.property).non_null is not applicable to optional fields " +
            "and is inapplicable to optional $typeName"
    }
    require(!withinOneof) {
        "(protokt.property).non_null is only applicable to top level types " +
            "and is inapplicable to oneof field $typeName"
    }
    require(type == FieldType.MESSAGE && !repeated) {
        "(protokt.property).non_null is only applicable to message types " +
            "and is inapplicable to non-message " +
            when {
                mapEntry != null ->
                    "map<${name(mapEntry.key)}, ${name(mapEntry.value)}>"

                repeated ->
                    "repeated $typeName"

                else ->
                    type.typeName()
            }
    }
}

private fun snakeToCamel(str: String): String {
    var ret = str
    var lastIndex = -1
    while (true) {
        lastIndex =
            ret.indexOf('_', lastIndex + 1)
                .also {
                    if (it == -1) {
                        return ret
                    }
                }
        ret = ret.substring(0, lastIndex) +
            ret.substring(lastIndex + 1).capitalize()
    }
}

private fun newFieldName(preferred: String) =
    // Ideally we'd avoid decapitalization but people have a tendency to
    // capitalize oneof defintions which will cause a clash between the field
    // name and the oneof sealed class definition. Can be avoided if the name
    // of the sealed class is modified when the field name is capitalized
    snakeToCamel(preferred).decapitalize()

private fun generateOneofClassName(lowerSnake: String) =
    snakeToCamel(lowerSnake).capitalize()
