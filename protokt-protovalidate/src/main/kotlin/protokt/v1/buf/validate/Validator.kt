/*
 * Copyright (c) 2024 Toast, Inc.
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

import build.buf.protovalidate.Config
import build.buf.protovalidate.ValidationResult
import build.buf.protovalidate.ValidatorFactory
import com.google.protobuf.Descriptors.Descriptor
import dev.cel.common.values.CelValueProvider
import protokt.v1.Beta
import protokt.v1.Message
import protokt.v1.google.protobuf.RuntimeContext
import protokt.v1.google.protobuf.toDynamicMessage
import java.util.Collections
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Beta
class Validator @JvmOverloads constructor(
    config: Config = Config.newBuilder().build(),
    private val lazyCelEvaluation: Boolean = true
) {
    private val delegate =
        ValidatorFactory.newBuilder()
            .withConfig(
                if (lazyCelEvaluation) {
                    Config.newBuilder()
                        .setFailFast(config.isFailFast)
                        .setCelValueProvider(NoOpCelValueProvider)
                        .build()
                } else {
                    config
                }
            )
            .build()

    private val descriptors = Collections.newSetFromMap(ConcurrentHashMap<Descriptor, Boolean>())

    @Volatile
    private var runtimeContext = RuntimeContext(emptyList())

    fun load(descriptor: Descriptor) {
        doLoad(descriptor)
        runtimeContext = RuntimeContext(descriptors)
    }

    private fun doLoad(descriptor: Descriptor) {
        descriptors.add(descriptor)
        descriptor.nestedTypes.forEach(::doLoad)
    }

    fun validate(message: Message): ValidationResult {
        val dynamicMessage = message.toDynamicMessage(runtimeContext)
        if (!lazyCelEvaluation) {
            return delegate.validate(dynamicMessage)
        }
        val descriptor = dynamicMessage.descriptorForType
        val celValue = ProtoktStructValue(message, descriptor, runtimeContext)
        return delegate.validate(celValue, descriptor)
    }
}

private object NoOpCelValueProvider : CelValueProvider {
    override fun newValue(structType: String, fields: Map<String, Any>): Optional<Any> =
        Optional.empty()
}
