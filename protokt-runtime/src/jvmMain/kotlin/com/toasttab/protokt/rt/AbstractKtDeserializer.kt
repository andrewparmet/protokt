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

package com.toasttab.protokt.rt

import com.google.protobuf.CodedInputStream

actual abstract class AbstractKtDeserializer<T> actual constructor() : KtDeserializer<T> {
    actual override fun deserialize(bytes: Bytes) =
        deserialize(bytes.value)

    actual override fun deserialize(bytes: ByteArray) =
        deserialize(deserializer(CodedInputStream.newInstance(bytes), bytes))

    actual override fun deserialize(bytes: BytesSlice) =
        deserialize(
            deserializer(
                CodedInputStream.newInstance(
                    bytes.array,
                    bytes.offset,
                    bytes.length
                )
            )
        )

    actual override fun deserialize(bytes: com.toasttab.protokt.Bytes) =
        deserialize(bytes.value)

    actual override fun deserialize(bytes: com.toasttab.protokt.BytesSlice) =
        deserialize(
            deserializer(
                CodedInputStream.newInstance(
                    bytes.array,
                    bytes.offset,
                    bytes.length
                )
            )
        )

    actual override fun deserialize(deserializer: com.toasttab.protokt.KtMessageDeserializer) =
        deserialize(NewToOldAdapter(deserializer))

    private class NewToOldAdapter(
        private val deserializer: com.toasttab.protokt.KtMessageDeserializer
    ) : KtMessageDeserializer {
        override fun readBytes() =
            Bytes(deserializer.readBytes().value)

        override fun readBytesSlice() =
            deserializer.readBytesSlice().let {
                BytesSlice(it.array, it.offset, it.length)
            }

        override fun readDouble() =
            deserializer.readDouble()

        override fun readFixed32() =
            deserializer.readFixed32()

        override fun readFixed64() =
            deserializer.readFixed64()

        override fun readFloat() =
            deserializer.readFloat()

        override fun readInt64() =
            deserializer.readInt64()

        override fun readSFixed32() =
            deserializer.readSFixed32()

        override fun readSFixed64() =
            deserializer.readSFixed64()

        override fun readSInt32() =
            deserializer.readSInt32()

        override fun readSInt64() =
            deserializer.readSInt64()

        override fun readString() =
            deserializer.readString()

        override fun readUInt64() =
            deserializer.readUInt64()

        override fun readTag() =
            deserializer.readTag()

        override fun readUnknown() =
            deserializer.readUnknown().let {
                when (it.value) {
                    is com.toasttab.protokt.VarintVal -> UnknownField.varint(it.fieldNumber, it.value.value.value)
                    is com.toasttab.protokt.Fixed32Val -> UnknownField.fixed32(it.fieldNumber, it.value.value.value)
                    is com.toasttab.protokt.Fixed64Val -> UnknownField.fixed64(it.fieldNumber, it.value.value.value)
                    is com.toasttab.protokt.LengthDelimitedVal -> UnknownField.lengthDelimited(it.fieldNumber, it.value.value.value)
                    else -> error("unsupported unknown field type")
                }
            }

        override fun readRepeated(packed: Boolean, acc: KtMessageDeserializer.() -> Unit) {
            deserializer.readRepeated(packed) { acc(this@NewToOldAdapter) }
        }

        override fun <T> readMessage(m: KtDeserializer<T>) =
            deserializer.readMessage(
                object : com.toasttab.protokt.AbstractKtDeserializer<T>() {
                    override fun deserialize(deserializer: com.toasttab.protokt.KtMessageDeserializer) =
                        deserialize(this@NewToOldAdapter)

                    override fun deserialize(deserializer: KtMessageDeserializer) =
                        deserializer.readMessage(m)
                }
            )
    }
}
