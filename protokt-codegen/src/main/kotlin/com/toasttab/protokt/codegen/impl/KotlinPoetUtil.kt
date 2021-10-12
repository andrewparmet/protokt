/*
 * Copyright (c) 2021 Toast Inc.
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

package com.toasttab.protokt.codegen.impl

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import kotlin.reflect.KClass

fun String.bindSpaces() =
    replace(" ", "·")

fun String.bindMargin() =
    trimMargin().bindSpaces()

fun overrideProperty(name: String, type: KClass<*>) =
    PropertySpec.builder(name, type)
        .addModifiers(KModifier.OVERRIDE)
        .initializer(name)
        .build()
