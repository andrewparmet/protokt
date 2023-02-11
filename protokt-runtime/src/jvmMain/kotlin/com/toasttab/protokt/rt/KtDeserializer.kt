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
import java.io.InputStream
import java.nio.ByteBuffer

actual interface KtDeserializer<T> {
    actual fun deserialize(bytes: Bytes): T

    actual fun deserialize(bytes: ByteArray): T

    actual fun deserialize(bytes: BytesSlice): T

    actual fun deserialize(deserializer: KtMessageDeserializer): T

    actual fun deserialize(bytes: com.toasttab.protokt.Bytes): T

    actual fun deserialize(bytes: com.toasttab.protokt.BytesSlice): T

    actual fun deserialize(deserializer: com.toasttab.protokt.KtMessageDeserializer): T

    fun deserialize(stream: InputStream): T =
        deserialize(deserializer(CodedInputStream.newInstance(stream)))

    fun deserialize(stream: CodedInputStream): T =
        deserialize(deserializer(stream))

    fun deserialize(buffer: ByteBuffer): T =
        deserialize(deserializer(CodedInputStream.newInstance(buffer)))

    @Deprecated("for ABI backwards compatibility only", level = DeprecationLevel.HIDDEN)
    object DefaultImpls {
        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, bytes: ByteArray) =
            deserializer.deserialize(deserializer(CodedInputStream.newInstance(bytes), bytes))

        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, bytes: Bytes) =
            deserializer.deserialize(bytes.value)

        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, bytes: BytesSlice) =
            deserializer.deserialize(
                deserializer(
                    CodedInputStream.newInstance(
                        bytes.array,
                        bytes.offset,
                        bytes.length
                    )
                )
            )

        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, buffer: ByteBuffer) =
            deserializer.deserialize(deserializer(CodedInputStream.newInstance(buffer)))

        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, stream: CodedInputStream) =
            deserializer.deserialize(deserializer(stream))

        @JvmStatic
        fun <T : KtMessage> deserialize(deserializer: KtDeserializer<T>, stream: InputStream) =
            deserializer.deserialize(deserializer(CodedInputStream.newInstance(stream)))
    }
}
