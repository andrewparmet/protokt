/*
 * Copyright (c) 2025 Toast, Inc.
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

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import dev.cel.common.types.CelType
import dev.cel.common.types.StructTypeReference
import dev.cel.common.values.CelValue
import dev.cel.common.values.StructValue
import protokt.v1.Message
import protokt.v1.google.protobuf.RuntimeContext
import protokt.v1.google.protobuf.toDynamicMessage
import java.util.Optional

internal class ProtoktStructValue(
    private val message: Message,
    private val descriptor: Descriptor,
    private val runtimeContext: RuntimeContext
) : StructValue<String>() {
    private val dynamicMessage by lazy { message.toDynamicMessage(runtimeContext) }

    override fun value(): Any =
        dynamicMessage

    override fun isZeroValue(): Boolean =
        false

    override fun celType(): CelType =
        StructTypeReference.create(descriptor.fullName)

    override fun select(field: String): Any =
        find(field).orElseThrow {
            IllegalArgumentException("field not found: $field")
        }

    override fun find(field: String): Optional<*> {
        val fieldDescriptor = descriptor.findFieldByName(field)
            ?: return Optional.empty<Any>()
        return Optional.of(dynamicMessage.getField(fieldDescriptor))
    }
}
