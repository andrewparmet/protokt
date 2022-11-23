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

package com.toasttab.protokt.testing.options

import com.google.auto.service.AutoService
import com.toasttab.protokt.ext.Converter
import com.toasttab.protokt.ext.OptimizedSizeofConverter
import com.toasttab.protokt.rt.Bytes
import com.toasttab.protokt.rt.sizeof

data class CachingId(
    internal val value: Bytes
)

@AutoService(Converter::class)
object CachingIdConverter : OptimizedSizeofConverter<CachingId, Bytes> {
    override val wrapper = CachingId::class

    override val wrapped = Bytes::class

    override fun sizeof(wrapped: CachingId) =
        sizeof(wrapped.value)

    override fun unwrap(wrapped: CachingId) =
        wrapped.value

    override fun wrap(unwrapped: Bytes) =
        CachingId(unwrapped)
}
