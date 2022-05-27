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

import org.khronos.webgl.Int8Array

actual abstract class AbstractKtMessage actual constructor() : KtMessage {
    actual override fun serialize(): ByteArray =
        Writer.create().let {
            serialize(serializer(it))
            it.finish().run {
                Int8Array(buffer, byteOffset, length).unsafeCast<ByteArray>()
            }
        }
}
