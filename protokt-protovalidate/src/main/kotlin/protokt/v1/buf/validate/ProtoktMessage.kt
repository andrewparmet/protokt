/*
 * Copyright (c) 2026 Toast, Inc.
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

package protokt.v1.buf.validate

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.Type
import com.google.protobuf.Descriptors.OneofDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.MapEntry
import com.google.protobuf.Message
import com.google.protobuf.Parser
import com.google.protobuf.UnknownFieldSet
import com.google.protobuf.UnsafeByteOperations
import com.google.protobuf.WireFormat
import protokt.v1.Bytes
import protokt.v1.Enum
import protokt.v1.google.protobuf.RuntimeContext
import protokt.v1.google.protobuf.getField
import protokt.v1.google.protobuf.hasField

/**
 * A `com.google.protobuf.Message` that adapts a protokt [protokt.v1.Message] for navigation by
 * cel-java's `DescriptorMessageProvider`. Implements the subset of `MessageOrBuilder` that
 * `selectField` / `hasField` actually call: `getDescriptorForType`, `getField`, `hasField`,
 * `getRepeatedFieldCount`, `getRepeatedField`, `hasOneof`, `getOneofFieldDescriptor`,
 * `getUnknownFields`, `getDefaultInstanceForType`. Everything else throws
 * `UnsupportedOperationException`.
 *
 * The wrapper is lazy: `getField` only converts the requested field, and nested message fields
 * return another `ProtoktMessage`. WKT-typed fields still go through
 * [RuntimeContext.convertValue] because cel-java's `ProtoAdapter` casts WKT field values to
 * their concrete Java protobuf classes (`com.google.protobuf.Duration`, etc).
 */
internal class ProtoktMessage(
    private val message: protokt.v1.Message?,
    private val descriptor: Descriptor,
    private val context: RuntimeContext,
) : Message {

    override fun getDescriptorForType(): Descriptor = descriptor

    override fun getDefaultInstanceForType(): Message =
        ProtoktMessage(null, descriptor, context)

    override fun hasField(field: FieldDescriptor): Boolean {
        if (message == null) {
            return false
        }
        return message.hasField(field)
    }

    override fun getField(field: FieldDescriptor): Any {
        if (message == null) {
            return defaultFor(field)
        }
        val raw = message.getField(field) ?: return defaultFor(field)
        return when {
            field.isMapField -> mapEntries(field, raw as Map<*, *>)
            field.isRepeated -> (raw as List<*>).map { adapt(field, it!!) }
            else -> adapt(field, raw)
        }
    }

    override fun getRepeatedFieldCount(field: FieldDescriptor): Int {
        @Suppress("UNCHECKED_CAST")
        return (getField(field) as List<Any>).size
    }

    override fun getRepeatedField(field: FieldDescriptor, index: Int): Any {
        @Suppress("UNCHECKED_CAST")
        return (getField(field) as List<Any>)[index]
    }

    override fun hasOneof(oneof: OneofDescriptor): Boolean =
        oneof.fields.any { hasField(it) }

    override fun getOneofFieldDescriptor(oneof: OneofDescriptor): FieldDescriptor? =
        oneof.fields.firstOrNull { hasField(it) }

    override fun getUnknownFields(): UnknownFieldSet = UnknownFieldSet.getDefaultInstance()

    override fun isInitialized(): Boolean = true

    override fun toString(): String =
        "ProtoktMessage(${descriptor.fullName}${if (message == null) ", default" else ""})"

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    // CEL never invokes any of the methods below on the navigation path. Throw if called.
    override fun getAllFields(): Map<FieldDescriptor, Any> = throw unsupported("getAllFields")
    override fun findInitializationErrors(): List<String> = throw unsupported("findInitializationErrors")
    override fun getInitializationErrorString(): String = throw unsupported("getInitializationErrorString")
    override fun newBuilderForType(): Message.Builder = throw unsupported("newBuilderForType")
    override fun toBuilder(): Message.Builder = throw unsupported("toBuilder")
    override fun getParserForType(): Parser<out Message> = throw unsupported("getParserForType")
    override fun toByteString(): ByteString = throw unsupported("toByteString")
    override fun toByteArray(): ByteArray = throw unsupported("toByteArray")
    override fun writeTo(output: com.google.protobuf.CodedOutputStream) = throw unsupported("writeTo(CodedOutputStream)")
    override fun writeTo(output: java.io.OutputStream) = throw unsupported("writeTo(OutputStream)")
    override fun writeDelimitedTo(output: java.io.OutputStream) = throw unsupported("writeDelimitedTo")
    override fun getSerializedSize(): Int = throw unsupported("getSerializedSize")

    private fun defaultFor(field: FieldDescriptor): Any =
        when {
            field.isMapField -> emptyList<MapEntry<*, *>>()
            field.isRepeated -> emptyList<Any>()
            field.type == Type.MESSAGE -> ProtoktMessage(null, field.messageType, context)
            else -> field.defaultValue
        }

    private fun adapt(field: FieldDescriptor, raw: Any): Any =
        when (raw) {
            is Enum -> field.enumType.findValueByNumberCreatingIfUnknown(raw.value)
            is UInt -> raw.toInt()
            is ULong -> raw.toLong()
            is Bytes -> UnsafeByteOperations.unsafeWrap(raw.asReadOnlyBuffer())
            is protokt.v1.Message ->
                if (WellKnownTypes.isOpen(field.messageType.fullName)) {
                    // CEL's ProtoAdapter casts WKT message fields to concrete classes (Any,
                    // Duration, Timestamp, etc.). Hand it the real proto message via protokt's
                    // existing converter.
                    context.convertValue(raw)
                } else {
                    ProtoktMessage(raw, field.messageType, context)
                }
            else -> raw
        }

    private fun mapEntries(field: FieldDescriptor, raw: Map<*, *>): List<MapEntry<Any?, Any?>> {
        val entryDescriptor = field.messageType
        val keyDesc = entryDescriptor.findFieldByNumber(1)
        val valDesc = entryDescriptor.findFieldByNumber(2)
        val defaultEntry =
            MapEntry.newDefaultInstance(
                entryDescriptor,
                WireFormat.FieldType.valueOf(keyDesc.type.name),
                defaultForMapField(keyDesc),
                WireFormat.FieldType.valueOf(valDesc.type.name),
                defaultForMapField(valDesc),
            ) as MapEntry<Any?, Any?>
        return raw.entries.map { (k, v) ->
            defaultEntry.toBuilder()
                .setKey(adapt(keyDesc, k!!))
                .setValue(adapt(valDesc, v!!))
                .build()
        }
    }

    private fun defaultForMapField(fd: FieldDescriptor): Any? =
        if (fd.type == Type.MESSAGE) {
            DynamicMessage.getDefaultInstance(fd.messageType)
        } else {
            fd.defaultValue
        }

    private fun unsupported(method: String): UnsupportedOperationException =
        UnsupportedOperationException(
            "ProtoktMessage.$method is not implemented; CEL navigation should not need it"
        )
}

internal object WellKnownTypes {
    /**
     * Open WKTs wrap arbitrary data: Any holds a packed message of any type, Value is a
     * JSON-like union, Struct/ListValue recurse through Value. cel-java casts these to their
     * concrete Java protobuf classes, so we can't substitute a [ProtoktMessage] wrapper.
     * Concrete WKTs (Duration, Timestamp, wrappers, Empty, FieldMask) also need their concrete
     * classes for cel-java's WKT dispatch, so we route them the same way.
     */
    private val WKT_NAMES = setOf(
        "google.protobuf.Any",
        "google.protobuf.Duration",
        "google.protobuf.Timestamp",
        "google.protobuf.FieldMask",
        "google.protobuf.Empty",
        "google.protobuf.Value",
        "google.protobuf.Struct",
        "google.protobuf.ListValue",
        "google.protobuf.BoolValue",
        "google.protobuf.BytesValue",
        "google.protobuf.DoubleValue",
        "google.protobuf.FloatValue",
        "google.protobuf.Int32Value",
        "google.protobuf.Int64Value",
        "google.protobuf.StringValue",
        "google.protobuf.UInt32Value",
        "google.protobuf.UInt64Value",
    )

    fun isOpen(typeName: String): Boolean = typeName in WKT_NAMES
}
