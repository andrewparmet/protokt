/*
 * Copyright (c) 2020 Toast Inc.
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

package com.toasttab.protokt.codegen.util

import arrow.core.None
import arrow.core.Option
import arrow.core.orElse

object WellKnownTypes {
    val StandardField.wrapWithWellKnownInterception
        get() =
            options.protokt.wrap.emptyToNone()
                .orElse {
                    if (protoTypeName.startsWith("$googleProto.")) {
                        classNameForWellKnownType(protoTypeName.removePrefix("$googleProto."))
                    } else {
                        None
                    }
                }

    private fun classNameForWellKnownType(type: String) =
        Option.fromNullable(
            when (type) {
                "DoubleValue" -> "java.lang.Double"
                "FloatValue" -> "java.lang.Float"
                "Int64Value" -> "java.lang.Long"
                "UInt64Value" -> "java.lang.Long"
                "Int32Value" -> "java.lang.Integer"
                "UInt32Value" -> "java.lang.Integer"
                "BoolValue" -> "java.lang.Boolean"
                "StringValue" -> "java.lang.String"
                "BytesValue" -> "$protoktRtPkg.Bytes"
                else -> null
            }
        )
}
