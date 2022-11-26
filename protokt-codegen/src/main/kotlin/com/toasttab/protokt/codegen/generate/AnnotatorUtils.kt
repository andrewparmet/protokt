/*
 * Copyright (c) 2019 Toast Inc.
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

package com.toasttab.protokt.codegen.generate

import com.squareup.kotlinpoet.TypeName
import com.toasttab.protokt.codegen.generate.CodeGenerator.Context
import com.toasttab.protokt.codegen.generate.Wrapper.interceptMapKeyTypeName
import com.toasttab.protokt.codegen.generate.Wrapper.interceptMapValueTypeName
import com.toasttab.protokt.codegen.util.Oneof
import com.toasttab.protokt.codegen.util.StandardField

fun resolveMapEntryTypes(f: StandardField, ctx: Context) =
    f.mapEntry!!.let {
        MapTypeParams(
            interceptMapKeyTypeName(f, it.key.className, ctx)!!,
            interceptMapValueTypeName(f, it.value.className, ctx)!!
        )
    }

class MapTypeParams(
    val kType: TypeName,
    val vType: TypeName
)

fun Oneof.qualify(f: StandardField) =
    className.nestedClass(fieldTypeNames.getValue(f.fieldName))