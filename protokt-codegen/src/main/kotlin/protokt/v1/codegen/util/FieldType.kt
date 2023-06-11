/*
 * Copyright (c) 2020 Toast, Inc.
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

package protokt.v1.codegen.util

import com.squareup.kotlinpoet.CodeBlock
import protokt.v1.KtEnum
import protokt.v1.KtMessage
import protokt.v1.KtMessageSerializer
import protokt.v1.sizeOfSInt32
import protokt.v1.sizeOfSInt64
import kotlin.reflect.KClass

sealed class FieldType {
    open val kotlinRepresentation: KClass<*>? = null
    open val inlineRepresentation: KClass<*>? = null
    open val ktRepresentation: KClass<*>? = null

    sealed class Nonscalar(
        override val kotlinRepresentation: KClass<*>? = null,
        override val ktRepresentation: KClass<*>? = null
    ) : FieldType()

    object Enum : Nonscalar(ktRepresentation = KtEnum::class)
    object Message : Nonscalar(ktRepresentation = KtMessage::class)
    object String : Nonscalar(kotlin.String::class)
    object Bytes : Nonscalar(protokt.v1.Bytes::class)

    sealed class Scalar(
        override val kotlinRepresentation: KClass<*>? = null
    ) : FieldType()

    object Bool : Scalar(Boolean::class)
    object Double : Scalar(kotlin.Double::class)
    object Float : Scalar(kotlin.Float::class)
    object Fixed32 : Scalar(UInt::class)
    object Fixed64 : Scalar(ULong::class)
    object Int32 : Scalar(Int::class)
    object Int64 : Scalar(Long::class)
    object SFixed32 : Scalar(Int::class)
    object SFixed64 : Scalar(Long::class)
    object SInt32 : Scalar(Int::class)
    object SInt64 : Scalar(Long::class)
    object UInt32 : Scalar(UInt::class)
    object UInt64 : Scalar(ULong::class)

    val protoktFieldType
        get() = when (this) {
            is Bytes -> protokt.v1.Bytes::class
            else ->
                requireNotNull(kotlinRepresentation) {
                    "no protokt field type for $this"
                }
        }

    val packable
        get() = this !in setOf(Bytes, Message, String)

    val writeFn
        get() = when (this) {
            Fixed32 -> KtMessageSerializer::writeFixed32.name
            SFixed32 -> KtMessageSerializer::writeSFixed32.name
            UInt32 -> KtMessageSerializer::writeUInt32.name
            SInt32 -> KtMessageSerializer::writeSInt32.name
            Fixed64 -> KtMessageSerializer::writeFixed64.name
            SFixed64 -> KtMessageSerializer::writeSFixed64.name
            UInt64 -> KtMessageSerializer::writeUInt64.name
            SInt64 -> KtMessageSerializer::writeSInt64.name
            else -> "write"
        }

    sealed interface SizeFn
    class Const(val size: Int) : SizeFn
    class Method(val name: kotlin.String) : SizeFn

    val sizeFn: SizeFn
        get() = when (this) {
            Bool -> Const(1)
            Double, Fixed64, SFixed64 -> Const(8)
            Float, Fixed32, SFixed32 -> Const(4)
            SInt32 -> Method(::sizeOfSInt32.name)
            SInt64 -> Method(::sizeOfSInt64.name)
            else -> Method("sizeOf")
        }

    val scalar
        get() = this is Scalar

    val wireType
        get() = when (this) {
            Bool,
            Enum,
            Int32,
            Int64,
            SInt32,
            SInt64,
            UInt32,
            UInt64 -> 0

            Double,
            Fixed64,
            SFixed64 -> 1

            Bytes,
            Message,
            String -> 2

            Float,
            Fixed32,
            SFixed32 -> 5
        }

    val defaultValue: CodeBlock
        get() = when (this) {
            Message -> CodeBlock.of("null")
            Enum -> error("enums do not have defaults; this is bug in the code generator")
            Bool -> CodeBlock.of("false")
            Fixed32, UInt32 -> CodeBlock.of("0u")
            Int32, SFixed32, SInt32 -> CodeBlock.of("0")
            Fixed64, UInt64 -> CodeBlock.of("0uL")
            Int64, SFixed64, SInt64 -> CodeBlock.of("0L")
            Float -> CodeBlock.of("0.0F")
            Double -> CodeBlock.of("0.0")
            Bytes -> CodeBlock.of("%T.empty()", protokt.v1.Bytes::class)
            String -> CodeBlock.of("\"\"")
        }
}