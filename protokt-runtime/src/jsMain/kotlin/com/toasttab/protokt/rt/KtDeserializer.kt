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

import org.khronos.webgl.Uint8Array

actual interface KtDeserializer<T> {
    actual fun deserialize(bytes: Bytes): T

    actual fun deserialize(bytes: ByteArray): T

    actual fun deserialize(bytes: BytesSlice): T

    actual fun deserialize(deserializer: KtMessageDeserializer): T

    fun deserialize(bytes: Uint8Array): T =
        deserialize(deserializer(Reader.create(bytes)))

    actual fun deserialize(bytes: com.toasttab.protokt.Bytes): T

    actual fun deserialize(bytes: com.toasttab.protokt.BytesSlice): T

    actual fun deserialize(deserializer: com.toasttab.protokt.KtMessageDeserializer): T
}
