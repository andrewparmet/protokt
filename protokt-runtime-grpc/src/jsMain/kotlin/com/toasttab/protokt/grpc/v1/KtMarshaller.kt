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

package com.toasttab.protokt.grpc.v1

import com.toasttab.protokt.v1.KtDeserializer
import com.toasttab.protokt.v1.KtMessage

class KtMarshaller<T : KtMessage>(
    private val deserializer: KtDeserializer<T>
) : MethodDescriptor.Marshaller<T> {
    override fun parse(bytes: ByteArray) =
        deserializer.deserialize(bytes)

    override fun serialize(value: T): dynamic =
        value.serialize()
}