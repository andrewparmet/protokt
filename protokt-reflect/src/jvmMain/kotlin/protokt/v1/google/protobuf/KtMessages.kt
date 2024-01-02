/*
 * Copyright (c) 2023 Toast, Inc.
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

@file:JvmName("KtMessages")

package protokt.v1.google.protobuf

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.MapEntry
import com.google.protobuf.Message
import com.google.protobuf.UnknownFieldSet
import com.google.protobuf.UnknownFieldSet.Field
import com.google.protobuf.UnsafeByteOperations
import com.google.protobuf.WireFormat
import com.toasttab.protokt.v1.ProtoktProtos
import io.github.classgraph.ClassGraph
import protokt.v1.Bytes
import protokt.v1.Converter
import protokt.v1.KtEnum
import protokt.v1.KtGeneratedFileDescriptor
import protokt.v1.KtGeneratedMessage
import protokt.v1.KtMessage
import protokt.v1.google.protobuf.RuntimeContext.Companion.DEFAULT_CONVERTERS
import protokt.v1.reflect.ClassLookup
import kotlin.Any
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

fun KtMessage.toDynamicMessage(context: RuntimeContext): DynamicMessage =
    context.convertValue(this) as DynamicMessage

class RuntimeContext(
    descriptors: Iterable<Descriptors.Descriptor>,
    converters: Iterable<Converter<*, *>>,
) {
    internal val descriptorsByTypeName = descriptors.associateBy { it.fullName }
    private val convertersByWrappedType = converters.associateBy { it.wrapper to type(it.wrapped) }

    private fun type(klass: KClass<*>): KClass<*> =
        when {
            else -> error("unimplemented: $klass")
        }

    fun convertValue(value: Any?) =
        when (value) {
            is KtEnum -> value.value
            is UInt -> value.toInt()
            is ULong -> value.toLong()
            is KtMessage -> toDynamicMessage(value, this)
            is Bytes -> UnsafeByteOperations.unsafeWrap(value.asReadOnlyBuffer())

            // pray
            else -> value
        }

    internal fun unwrap(value: Any, field: FieldDescriptor): Any {
        val defaultConverter =
            if (field.type == FieldDescriptor.Type.MESSAGE) {
                DEFAULT_CONVERTERS[field.messageType.fullName]
            } else {
                null
            }

        // todo!
        val converter = defaultConverter ?: convertersByWrappedType.getValue(value::class to Any::class)

        @Suppress("UNCHECKED_CAST")
        return (converter as Converter<Any, Any>).unwrap(value)
    }

    companion object {
        internal val DEFAULT_CONVERTERS: Map<String, Converter<*, *>> =
            mapOf(
                "google.protobuf.DoubleValue" to DoubleValueConverter,
                "google.protobuf.FloatValue" to FloatValueConverter,
                "google.protobuf.Int64Value" to Int64ValueConverter,
                "google.protobuf.UInt64Value" to UInt64ValueConverter,
                "google.protobuf.Int32Value" to Int32ValueConverter,
                "google.protobuf.UInt32Value" to UInt32ValueConverter,
                "google.protobuf.BoolValue" to BoolValueConverter,
                "google.protobuf.StringValue" to StringValueConverter,
                "google.protobuf.BytesValue" to BytesValueConverter,
            )

        private val reflectiveContext by lazy {
            ClassLookup(emptyList())
            RuntimeContext(getDescriptors(), getConverters())
        }

        fun getContextReflectively() =
            reflectiveContext
    }
}

private fun getDescriptors() =
    ClassGraph()
        .enableAnnotationInfo()
        .scan()
        .use {
            it.getClassesWithAnnotation(KtGeneratedFileDescriptor::class.java)
                .map { info ->
                    @Suppress("UNCHECKED_CAST")
                    info.loadClass().kotlin as KClass<Any>
                }
        }
        .asSequence()
        .map { klassWithDescriptor ->
            klassWithDescriptor
                .declaredMemberProperties
                .single { it.returnType.classifier == FileDescriptor::class }
                .get(klassWithDescriptor.objectInstance!!) as FileDescriptor
        }
        .flatMap { it.toProtobufJavaDescriptor().messageTypes }
        .flatMap(::collectDescriptors)
        .asIterable()

private fun getConverters(): Iterable<Converter<*, *>> {
    val classLoader = Thread.currentThread().contextClassLoader

    return classLoader.getResources("META-INF/services/${Converter::class.qualifiedName}")
        .asSequence()
        .flatMap { url ->
            url.openStream()
                .bufferedReader()
                .useLines { lines ->
                    lines.map { it.substringBefore("#").trim() }
                        .filter { it.isNotEmpty() }
                        .map { classLoader.loadClass(it).kotlin.objectInstance as Converter<*, *> }
                        .toList()
                }
        }.asIterable()
}

private fun collectDescriptors(descriptor: Descriptors.Descriptor): Iterable<Descriptors.Descriptor> =
    listOf(descriptor) + descriptor.nestedTypes.flatMap(::collectDescriptors)

private fun FileDescriptor.toProtobufJavaDescriptor(): Descriptors.FileDescriptor =
    Descriptors.FileDescriptor.buildFrom(
        DescriptorProtos.FileDescriptorProto.parseFrom(proto.serialize()),
        dependencies.map { it.toProtobufJavaDescriptor() }.toTypedArray(),
        true,
    )

private fun toDynamicMessage(
    message: KtMessage,
    context: RuntimeContext,
): Message {
    val descriptor =
        context.descriptorsByTypeName
            .getValue(message::class.findAnnotation<KtGeneratedMessage>()!!.fullTypeName)

    return DynamicMessage.newBuilder(descriptor)
        .apply {
            descriptor.fields.forEach { field ->
                ProtoktReflect.getField(message, field)?.let { value ->
                    setField(
                        field,
                        when {
                            field.type == Descriptors.FieldDescriptor.Type.ENUM ->
                                if (field.isRepeated) {
                                    (value as List<*>).map { field.enumType.findValueByNumberCreatingIfUnknown(((it as KtEnum).value)) }
                                } else {
                                    field.enumType.findValueByNumberCreatingIfUnknown(((value as KtEnum).value))
                                }

                            field.isMapField ->
                                convertMap(value, field, context)

                            field.isRepeated ->
                                (value as List<*>).map(context::convertValue)

                            isWrapped(field) ->
                                context.convertValue(context.unwrap(value, field))

                            else -> context.convertValue(value)
                        },
                    )
                }
            }
        }
        .setUnknownFields(mapUnknownFields(message))
        .build()
}

private fun isWrapped(field: FieldDescriptor): Boolean {
    if (field.type == FieldDescriptor.Type.MESSAGE && field.messageType.fullName in DEFAULT_CONVERTERS) {
        return true
    }

    val options = field.toProto().options

    val propertyOptions =
        if (options.hasField(ProtoktProtos.property.descriptor)) {
            options.getField(ProtoktProtos.property.descriptor) as ProtoktProtos.FieldOptions
        } else if (options.unknownFields.hasField(ProtoktProtos.property.number)) {
            ProtoktProtos.FieldOptions.parseFrom(
                options.unknownFields.getField(ProtoktProtos.property.number)
                    .lengthDelimitedList
                    .last()
            )
        } else {
            return false
        }

    return propertyOptions.wrap.isNotEmpty() ||
        propertyOptions.keyWrap.isNotEmpty() ||
        propertyOptions.valueWrap.isNotEmpty()
}

private fun convertMap(
    value: Any,
    field: FieldDescriptor,
    context: RuntimeContext,
): List<MapEntry<*, *>> {
    val keyDesc = field.messageType.findFieldByNumber(1)
    val valDesc = field.messageType.findFieldByNumber(2)

    val keyDefault =
        if (keyDesc.type == Descriptors.FieldDescriptor.Type.MESSAGE) {
            null
        } else {
            keyDesc.defaultValue
        }

    val valDefault =
        if (valDesc.type == Descriptors.FieldDescriptor.Type.MESSAGE) {
            null
        } else {
            valDesc.defaultValue
        }

    val defaultEntry =
        MapEntry.newDefaultInstance(
            field.messageType,
            WireFormat.FieldType.valueOf(keyDesc.type.name),
            keyDefault,
            WireFormat.FieldType.valueOf(valDesc.type.name),
            valDefault
        ) as MapEntry<Any?, Any?>

    return (value as Map<*, *>).map { (k, v) ->
        defaultEntry.toBuilder()
            .setKey(context.convertValue(k))
            .setValue(context.convertValue(v))
            .build()
    }
}

private fun mapUnknownFields(message: KtMessage): UnknownFieldSet {
    val unknownFields = UnknownFieldSet.newBuilder()

    getUnknownFields(message).forEach { (number, field) ->
        unknownFields.mergeField(
            number.toInt(),
            Field.newBuilder()
                .apply {
                    field.varint.forEach { addVarint(it.value.toLong()) }
                    field.fixed32.forEach { addFixed32(it.value.toInt()) }
                    field.fixed64.forEach { addFixed64(it.value.toLong()) }
                    field.lengthDelimited.forEach { addLengthDelimited(UnsafeByteOperations.unsafeWrap(it.value.asReadOnlyBuffer())) }
                }
                .build()
        )
    }

    return unknownFields.build()
}
